package com.avas.platform.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The seam where a home stops being planned by AVAS AI and starts being planned by rules.
 *
 * <p>Worth testing precisely because every failure here produces a complete, plausible house. The
 * recommendation a customer reads looks identical either way, so the only thing that distinguishes a
 * planned home from a fallback is the provenance these assertions check.</p>
 */
class AvasAiProgrammeClientTest {
    @Test
    void sendsTheBriefWithTheMeasuredPlotAndAdoptsTheProgrammeItIsAnswered() {
        var client = client(true, "service-secret");
        var server = MockRestServiceServer.createServer(http(client));
        server.expect(requestTo("http://avas-ai.test/api/v1/plan-programme"))
                .andExpect(header("X-Service-Key", "service-secret"))
                .andExpect(jsonPath("$.projectId").value("project-1"))
                .andExpect(jsonPath("$.contextVersion").value("requirements-v1"))
                .andExpect(jsonPath("$.family.children").value(2))
                .andExpect(jsonPath("$.budget.finishTier").value("LUXURY"))
                // The plot as the geometry engine measured it. A programme capped against the
                // bounding box is capped against ground that does not exist.
                .andExpect(jsonPath("$.plot.plannableAreaSqFt").exists())
                .andExpect(jsonPath("$.plot.buildableAreaSqFt").exists())
                .andRespond(withSuccess(response(4), MediaType.APPLICATION_JSON));

        var programme = client.plan("tenant-1", "project-1", "requirements-v1", details(), envelope());

        server.verify();
        assertThat(programme.bedrooms()).isEqualTo(4);
        assertThat(programme.attachedBathrooms()).isEqualTo(3);
        assertThat(programme.title()).isEqualTo("4-bedroom duplex");
        assertThat(programme.provider()).isEqualTo("OPENAI");
        assertThat(programme.model()).isEqualTo("gpt-5.4-mini-2026-08-01");
        assertThat(programme.fallbackUsed()).isFalse();
        assertThat(programme.modelPlanned()).isTrue();
    }

    @Test
    void aProgrammeThePlotCannotCarryIsRefusedRatherThanDrawn() {
        // The schema accepts six bedrooms and this plot does not. Adopting it anyway produces a
        // brief the layout engine reports as a programme gap on every candidate it draws, so the
        // customer sees three broken options and nothing says the model was the reason.
        var client = client(true, "service-secret");
        var server = MockRestServiceServer.createServer(http(client));
        server.expect(requestTo("http://avas-ai.test/api/v1/plan-programme"))
                .andRespond(withSuccess(response(6), MediaType.APPLICATION_JSON));

        var programme = client.plan("tenant-1", "project-1", "requirements-v1", small(), smallEnvelope());

        server.verify();
        assertThat(programme.fallbackUsed()).isTrue();
        assertThat(programme.provider()).isEqualTo("DETERMINISTIC");
        assertThat(programme.warnings())
                .anySatisfy(warning -> assertThat(warning).contains("outside the"));
    }

    @Test
    void aProgrammeForAnotherProjectSnapshotIsRefused() {
        var client = client(true, "service-secret");
        var server = MockRestServiceServer.createServer(http(client));
        server.expect(requestTo("http://avas-ai.test/api/v1/plan-programme"))
                .andRespond(withSuccess(response(4).replace("requirements-v1", "requirements-v9"),
                        MediaType.APPLICATION_JSON));

        var programme = client.plan("tenant-1", "project-1", "requirements-v1", details(), envelope());

        assertThat(programme.fallbackUsed()).isTrue();
        assertThat(programme.warnings())
                .anySatisfy(warning -> assertThat(warning).contains("different project snapshot"));
    }

    @Test
    void anUnreachableServiceStillPlansAHomeAndSaysWhyItPlannedItItself() {
        var client = client(true, "service-secret");
        var server = MockRestServiceServer.createServer(http(client));
        server.expect(requestTo("http://avas-ai.test/api/v1/plan-programme"))
                .andRespond(withServerError());

        var programme = client.plan("tenant-1", "project-1", "requirements-v1", details(), envelope());

        assertThat(programme.bedrooms()).isEqualTo(4);
        assertThat(programme.provider()).isEqualTo("DETERMINISTIC");
        assertThat(programme.fallbackUsed()).isTrue();
        assertThat(programme.modelPlanned()).isFalse();
        assertThat(programme.warnings()).anySatisfy(warning ->
                assertThat(warning).contains("AVAS AI was unavailable"));
    }

    @Test
    void anUnauthorisedCallIsAFallbackRatherThanASilentlyDifferentHouse() {
        var client = client(true, "wrong-secret");
        var server = MockRestServiceServer.createServer(http(client));
        server.expect(requestTo("http://avas-ai.test/api/v1/plan-programme"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        var programme = client.plan("tenant-1", "project-1", "requirements-v1", details(), envelope());

        assertThat(programme.fallbackUsed()).isTrue();
        assertThat(programme.provider()).isEqualTo("DETERMINISTIC");
    }

    @Test
    void aDisabledServiceIsNotReportedAsAFailedOne() {
        // Nothing was asked, so nothing failed. Reporting this as a fallback would make a deployment
        // that never enabled AI indistinguishable from one whose provider is broken.
        var client = client(false, "");

        var programme = client.plan("tenant-1", "project-1", "requirements-v1", details(), envelope());

        assertThat(programme.provider()).isEqualTo("DETERMINISTIC");
        assertThat(programme.fallbackUsed()).isFalse();
        assertThat(programme.warnings()).isEmpty();
    }

    @Test
    void aMissingServiceKeyIsNamedRatherThanLeftToFailOnTheWire() {
        var client = client(true, "");

        var programme = client.plan("tenant-1", "project-1", "requirements-v1", details(), envelope());

        assertThat(programme.fallbackUsed()).isTrue();
        assertThat(programme.warnings())
                .anySatisfy(warning -> assertThat(warning).contains("service key is not configured"));
    }

    // -------------------------------------------------------------------------------------------

    private AvasAiProgrammeClient client(boolean enabled, String serviceKey) {
        return new AvasAiProgrammeClient(new RestTemplateBuilder(), new ObjectMapper(),
                "http://avas-ai.test", serviceKey, enabled, 2);
    }

    private RestTemplate http(AvasAiProgrammeClient client) {
        return (RestTemplate) ReflectionTestUtils.getField(client, "http");
    }

    private String response(int bedrooms) {
        return """
                {
                  "requestId": "req-1",
                  "tenantId": "tenant-1",
                  "projectId": "project-1",
                  "contextVersion": "requirements-v1",
                  "provider": "OPENAI",
                  "model": "gpt-5.4-mini-2026-08-01",
                  "providerRequestId": "resp_avas_test_1",
                  "promptVersion": "household-programme-1.0.0",
                  "schemaVersion": "household-programme-1",
                  "fallbackUsed": false,
                  "warnings": [],
                  "programme": {
                    "bedrooms": %d,
                    "attachedBathrooms": 3,
                    "commonBathrooms": 1,
                    "parkingCars": 2,
                    "seniorBedroom": false,
                    "familyLounge": true,
                    "futureExpansion": false,
                    "title": "%d-bedroom duplex",
                    "reasons": ["A room for each child", "A guest room on the ground floor"]
                  }
                }
                """.formatted(bedrooms, bedrooms);
    }

    private BasicDetailsRequest details() {
        return brief(40, 60);
    }

    private BasicDetailsRequest small() {
        return brief(22, 40);
    }

    private BuildableEnvelope envelope() {
        return envelopeFor(details());
    }

    private BuildableEnvelope smallEnvelope() {
        return envelopeFor(small());
    }

    private BuildableEnvelope envelopeFor(BasicDetailsRequest details) {
        var boundary = details.boundary();
        return BuildableEnvelope.derive(boundary,
                SetbackRule.forUsage(boundary, details.floors(), details.parameters().plotUsage()),
                details.floors(), details.roadFacing(), details.parameters().parkingCars());
    }

    private BasicDetailsRequest brief(double width, double length) {
        var brief = new BasicDetailsRequest(width, length, Facing.NORTH, "Jaipur", 2, 14_000_000,
                Category.LUXURY, new FamilyDetails(2, 2, 0, false), List.of());
        var inferred = brief.parameters();
        return new BasicDetailsRequest(brief.plotWidth(), brief.plotLength(), brief.roadFacing(),
                brief.city(), brief.floors(), brief.budget(), brief.category(), brief.family(),
                brief.preferences(), new HomeParameters(inferred.homeType(),
                        inferred.staircaseType(), inferred.liftProvision(), inferred.balconyCount(),
                        inferred.terraceRequired(), inferred.courtyardRequired(),
                        inferred.accessibleGroundFloor(), inferred.parkingCars(),
                        inferred.solarReady(), inferred.rainwaterHarvesting(),
                        HomeParameters.STANDARD_SETBACK));
    }
}

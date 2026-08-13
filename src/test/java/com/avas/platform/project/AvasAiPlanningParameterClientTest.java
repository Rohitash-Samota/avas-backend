package com.avas.platform.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AvasAiPlanningParameterClientTest {
    @Test
    void sendsTypedHomeRequirementsAndAcceptsOnlyTheThreeStrategyContract() {
        var client = new AvasAiPlanningParameterClient(new RestTemplateBuilder(), new ObjectMapper(),
                "http://avas-ai.test", "service-secret", true, 2);
        var http = (RestTemplate) ReflectionTestUtils.getField(client, "http");
        var server = MockRestServiceServer.createServer(http);
        server.expect(requestTo("http://avas-ai.test/api/v1/plan-parameters"))
                .andExpect(header("X-Service-Key", "service-secret"))
                .andExpect(jsonPath("$.projectId").value("project-1"))
                .andExpect(jsonPath("$.contextVersion").value("requirement-v1"))
                .andExpect(jsonPath("$.requested.homeType").value("DUPLEX"))
                .andExpect(jsonPath("$.requested.liftProvision").value("PASSENGER"))
                .andExpect(jsonPath("$.requested.balconyCount").value(2))
                .andRespond(withSuccess(response(), MediaType.APPLICATION_JSON));

        var result = client.optimize("tenant-1", "project-1", "requirement-v1", details());

        server.verify();
        assertThat(result.provider()).isEqualTo("OPENAI");
        assertThat(result.model()).isEqualTo("gpt-5.4-mini-2026-08-01");
        assertThat(result.providerRequestId()).isEqualTo("resp_avas_test_1");
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.variants()).extracting(PlanningParameterVariant::strategy)
                .containsExactly("BUDGET_OPTIMIZED", "BALANCED", "LIFESTYLE_OPTIMIZED");
    }

    @Test
    void disabledServiceUsesAnExplicitNonFallbackDeterministicParameterSet() {
        var client = new AvasAiPlanningParameterClient(new RestTemplateBuilder(), new ObjectMapper(),
                "http://avas-ai.test", "", false, 2);

        var result = client.optimize("tenant-1", "project-1", "requirement-v1", details());

        assertThat(result.provider()).isEqualTo("DETERMINISTIC");
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void rejectsAResponseForAnotherImmutableProjectContext() {
        var client = new AvasAiPlanningParameterClient(new RestTemplateBuilder(), new ObjectMapper(),
                "http://avas-ai.test", "service-secret", true, 2);
        var http = (RestTemplate) ReflectionTestUtils.getField(client, "http");
        var server = MockRestServiceServer.createServer(http);
        server.expect(requestTo("http://avas-ai.test/api/v1/plan-parameters"))
                .andRespond(withSuccess(response().replace("requirement-v1", "stale-requirement"),
                        MediaType.APPLICATION_JSON));

        var result = client.optimize("tenant-1", "project-1", "requirement-v1", details());

        server.verify();
        assertThat(result.provider()).isEqualTo("DETERMINISTIC");
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.warnings()).singleElement()
                .asString().contains("different project snapshot");
    }

    private BasicDetailsRequest details() {
        return new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur, Rajasthan", 2, 7_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 1, true), List.of("Natural light"),
                new HomeParameters("DUPLEX", "U_SHAPED", "PASSENGER", 2,
                        true, true, true, 2, true, true));
    }

    private String response() {
        return """
                {"tenantId":"tenant-1","projectId":"project-1","contextVersion":"requirement-v1",
                  "requestId":"ai-request-1","providerRequestId":"resp_avas_test_1",
                  "provider":"OPENAI","model":"gpt-5.4-mini-2026-08-01",
                  "promptVersion":"home-parameters-1.0.0","schemaVersion":"home-parameters-1",
                  "fallbackUsed":false,"warnings":[],"variants":[
                    %s,%s,%s
                  ]
                }
                """.formatted(variant("BUDGET_OPTIMIZED", "Efficient Courtyard"),
                        variant("BALANCED", "Garden Threshold"),
                        variant("LIFESTYLE_OPTIMIZED", "Lightwell House"));
    }

    private String variant(String strategy, String title) {
        return """
                {"strategy":"%s","title":"%s","duplexZoning":"Shared living below; private rooms above",
                 "staircaseType":"U_SHAPED","liftProvision":"PASSENGER","balconyCount":2,
                 "terraceRequired":true,"courtyardRequired":true,"accessibleGroundFloor":true,
                 "parkingCars":2,"solarReady":true,"rainwaterHarvesting":true,"roomTargets":[
                   {"roomType":"LIVING_ROOM","floor":"GROUND","count":1,"targetAreaSqFt":240,"priority":"REQUIRED"},
                   {"roomType":"DINING","floor":"GROUND","count":1,"targetAreaSqFt":150,"priority":"REQUIRED"},
                   {"roomType":"KITCHEN","floor":"GROUND","count":1,"targetAreaSqFt":140,"priority":"REQUIRED"},
                   {"roomType":"STAIRCASE","floor":"GROUND","count":1,"targetAreaSqFt":90,"priority":"REQUIRED"},
                   {"roomType":"BATHROOM","floor":"GROUND","count":1,"targetAreaSqFt":45,"priority":"REQUIRED"}],
                 "weights":{"budget":0.2,"functionality":0.3,"daylight":0.2,"accessibility":0.15,"futureReadiness":0.15},
                 "explanations":["Bounded typed option","Deterministic geometry remains authoritative"]}
                """.formatted(strategy, title);
    }
}

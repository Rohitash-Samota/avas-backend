package com.avas.platform.project;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * What the platform is willing to show when an image model draws a picture of a concept.
 *
 * <p>Nothing here is adopted, so the refusals matter less than they do for a layout. What matters is
 * the separation: the reply carries measured sheets alongside the illustration, and a client that
 * confused the two would put a second, subtly different plan of the customer's own house in front of
 * them. That, and the fact that a picture arriving at all must never be able to stop the measured
 * plan appearing.</p>
 */
class AvasAiConceptRenderClientTest {
    private static final String URL = "http://ai.test/api/v1/plan-drawing";
    private static final String PNG = Base64.getEncoder().encodeToString("pretend-png".getBytes());
    private static final String SVG = Base64.getEncoder().encodeToString("<svg/>".getBytes());

    @Test
    void theIllustrationIsKeptAndTheMeasuredSheetsAreNot() {
        var server = new Server();
        server.expect().andRespond(withSuccess(reply(sheet("GROUND"), illustration()), MediaType.APPLICATION_JSON));

        var outcome = server.client.illustrate("tenant-1", "drawing-v3", project(), drawing(),
                ConceptRenderClient.Style.ELEVATION, null);

        assertThat(outcome.ready()).isTrue();
        assertThat(outcome.failure()).isNull();
        assertThat(outcome.render().mediaType()).isEqualTo("image/png");
        assertThat(outcome.render().image()).isEqualTo("pretend-png".getBytes());
        // The words travel with the picture: an illustration that arrived without its brief cannot
        // be judged against anything, and the UI would have nothing honest to caption it with.
        assertThat(outcome.render().prompt()).contains("Jaipur");
        assertThat(outcome.render().model()).isEqualTo("black-forest-labs/FLUX.1-schnell");
        server.verify();
    }

    @Test
    void aFloorRenderKeepsOnlyTheIllustrationForTheRequestedStorey() {
        var server = new Server();
        server.expect().andRespond(withSuccess(
                reply(illustration("GROUND"), illustration("FIRST")),
                MediaType.APPLICATION_JSON));

        var outcome = server.client.illustrate("tenant-1", "drawing-v3", project(), drawing(),
                ConceptRenderClient.Style.FLOOR_PLAN, null, "FIRST");

        assertThat(outcome.ready()).isTrue();
        assertThat(outcome.render().prompt()).contains("FIRST");
        server.verify();
    }

    @Test
    void aFloorlessIllustrationCannotBeFiledUnderARequestedFloor() {
        var server = new Server();
        server.expect().andRespond(withSuccess(reply(illustration()), MediaType.APPLICATION_JSON));

        var outcome = server.client.illustrate("tenant-1", "drawing-v3", project(), drawing(),
                ConceptRenderClient.Style.FLOOR_PLAN_3D, null, "GROUND");

        assertThat(outcome.ready()).isFalse();
        assertThat(outcome.failure()).contains("without the requested ground floor identity");
        server.verify();
    }


    @Test
    void aReplyCarryingOnlyMeasuredSheetsIsNotAPicture() {
        var server = new Server();
        // The ordinary shape of a deployment with no image model, and of one whose render failed and
        // fell back. Neither is an error: the customer sees the plan the platform drew itself.
        server.expect().andRespond(withSuccess(reply(sheet("GROUND"), sheet("FIRST")), MediaType.APPLICATION_JSON));

        var outcome = server.client.illustrate("tenant-1", "drawing-v3", project(), drawing(),
                ConceptRenderClient.Style.ELEVATION, null);

        assertThat(outcome.ready()).isFalse();
        // No image model configured is working as intended, and must not read as a failure.
        assertThat(outcome.failure()).isNull();
    }

    @Test
    void aRenderThatWasTriedAndFailedCarriesTheProvidersOwnReason() {
        var server = new Server();
        // The AI service drew its measured sheets and said why the picture is missing. Reporting
        // that as "no image model configured" is what sends somebody to configure a configured
        // service; an exhausted quota and a switched-off feature have nothing in common but the
        // absence of an image.
        server.expect().andRespond(withSuccess(replyWithFallback(
                "The illustrative render was unavailable: image endpoint returned 400: "
                        + "Billing hard limit has been reached."), MediaType.APPLICATION_JSON));

        var outcome = server.client.illustrate("tenant-1", "drawing-v3", project(), drawing(),
                ConceptRenderClient.Style.ELEVATION, null);

        assertThat(outcome.ready()).isFalse();
        assertThat(outcome.failure()).contains("Billing hard limit has been reached");
    }

    @Test
    void aCorridorIsDroppedRatherThanSentToAServiceThatWouldRejectTheWholeRequest() {
        var server = new Server();
        server.expect()
                .andExpect(header("X-Service-Key", "test-key"))
                .andExpect(jsonPath("$.style").value("PERSPECTIVE"))
                .andExpect(jsonPath("$.plot.city").value("Jaipur"))
                .andExpect(jsonPath("$.layout.floors[0].floor").value("GROUND"))
                .andExpect(jsonPath("$.layout.floors[0].rooms[0].roomType").value("LIVING_ROOM"))
                // The AI service's room vocabulary is a closed set with no CORRIDOR in it, so a
                // request carrying one is refused outright — and the customer would lose the
                // picture over a room that does not change what the house looks like.
                .andExpect(jsonPath("$.layout.floors[0].rooms[1].roomType").value("KITCHEN"))
                .andRespond(withSuccess(reply(illustration()), MediaType.APPLICATION_JSON));

        var geometry = new GeometryDocument("FEET", 40, 60, List.of(
                new RoomGeometry("G-R1", "LIVING_ROOM", 5, 5, 14, 20, 280, "GROUND"),
                new RoomGeometry("G-R2", "CORRIDOR", 19, 5, 4, 20, 80, "GROUND"),
                new RoomGeometry("G-R3", "KITCHEN", 23, 5, 11, 12, 132, "GROUND")),
                List.of(), List.of());

        server.client.illustrate("tenant-1", "drawing-v3", project(), drawing(geometry),
                ConceptRenderClient.Style.PERSPECTIVE, null);
        server.verify();
    }

    @Test
    void aServiceFailureCostsThePictureAndNothingElse() {
        var server = new Server();
        server.expect().andRespond(withServerError());

        var outcome = server.client.illustrate("tenant-1", "drawing-v3", project(), drawing(),
                ConceptRenderClient.Style.ELEVATION, null);

        assertThat(outcome.ready()).isFalse();
        // A service that cannot be reached is a thing to fix, not a deployment choice.
        assertThat(outcome.failure()).contains("could not be reached");
    }

    @Test
    void theRouteIsNotCalledAtAllWhenRendersAreDisabled() {
        var template = new RestTemplate();
        var mock = MockRestServiceServer.createServer(template);
        var disabled = new AvasAiConceptRenderClient(new StubBuilder(template), "http://ai.test",
                "test-key", false, 5);

        var outcome = disabled.illustrate("tenant-1", "drawing-v3", project(), drawing(),
                ConceptRenderClient.Style.ELEVATION, null);
        assertThat(outcome.ready()).isFalse();
        assertThat(outcome.failure()).as("disabled is a choice, not a fault").isNull();
        // Off means off: no request, so a disabled deployment cannot be spending GPU time.
        mock.verify();
    }

    @Test
    void thePayloadCarriesTheHomeTheCustomerActuallyChose() {
        var server = new Server();
        server.expect()
                // The gap this route used to have. It was handed coordinates and a plot and nothing
                // else, so a customer who chose a dog-legged stair, a future lift shaft and two
                // covered bays was shown a picture that knew about none of them — while the layout
                // it illustrates had placed all three. Several are invisible in the rectangles: a
                // future shaft and a working lift are the same five-foot square.
                .andExpect(jsonPath("$.requested.staircaseType").value("U_SHAPED"))
                .andExpect(jsonPath("$.requested.liftProvision").value("FUTURE_SHAFT"))
                .andExpect(jsonPath("$.requested.parkingCars").value(2))
                .andExpect(jsonPath("$.requested.plotUsage").value("STANDARD_SETBACK"))
                .andExpect(jsonPath("$.family.adults").value(2))
                .andExpect(jsonPath("$.family.seniorCitizens").value(1))
                .andExpect(jsonPath("$.budget.finishTier").value("PREMIUM"))
                .andExpect(jsonPath("$.preferences[0]").value("vastu compliant"))
                .andRespond(withSuccess(reply(illustration()), MediaType.APPLICATION_JSON));

        server.client.illustrate("tenant-1", "drawing-v3", specified(), drawing(),
                ConceptRenderClient.Style.FLOOR_PLAN, null, "GROUND");
        server.verify();
    }

    @Test
    void theLookTheCustomerPickedTravelsWithTheRequest() {
        var server = new Server();
        server.expect()
                .andExpect(jsonPath("$.options.palette").value("MONOCHROME"))
                .andExpect(jsonPath("$.options.furnishing").value("SCHEMATIC"))
                .andExpect(jsonPath("$.options.landscaping").value(false))
                .andExpect(jsonPath("$.options.showCars").value(false))
                .andExpect(jsonPath("$.options.labels").value("NAME_ONLY"))
                .andRespond(withSuccess(reply(illustration()), MediaType.APPLICATION_JSON));

        server.client.illustrate("tenant-1", "drawing-v3", project(), drawing(),
                ConceptRenderClient.Style.FLOOR_PLAN, null, "GROUND",
                new ConceptRenderClient.RenderOptions("schematic", "monochrome", false, false,
                        "name_only"));
        server.verify();
    }

    @Test
    void aProjectWithNoRecordedParametersStillGetsItsPicture() {
        var server = new Server();
        // The AI service forbids unknown fields, so a null parameter block has to be left out
        // rather than sent as null. Sent, it would 422 the whole call and the customer would be
        // told the render failed — over a field that only ever adds an adjective to a sentence.
        server.expect()
                .andExpect(jsonPath("$.requested").doesNotExist())
                .andRespond(withSuccess(reply(illustration()), MediaType.APPLICATION_JSON));

        var bare = new ProjectSummary("project-1", "AV-1", "My family home", StartMode.PLOT,
                "DRAFT", 3, null, Instant.EPOCH, Instant.EPOCH);
        var outcome = server.client.illustrate("tenant-1", "drawing-v3", bare, drawing(),
                ConceptRenderClient.Style.FLOOR_PLAN, null, "GROUND");
        // No details at all is "not configured for this project", not a failure to report.
        assertThat(outcome.ready()).isFalse();
    }

    private ProjectSummary specified() {
        return new ProjectSummary("project-1", "AV-1", "My family home", StartMode.PLOT, "DRAFT", 3,
                new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur", 2, 9_000_000L,
                        Category.PREMIUM, new FamilyDetails(2, 1, 1, false),
                        List.of("vastu compliant", "cross ventilation"),
                        new HomeParameters("DUPLEX", "U_SHAPED", "FUTURE_SHAFT", 2, true, true,
                                false, 2, false, false, "STANDARD_SETBACK")),
                Instant.EPOCH, Instant.EPOCH);
    }

    private ProjectSummary project() {
        return new ProjectSummary("project-1", "AV-1", "My family home", StartMode.PLOT, "DRAFT", 3,
                new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur", 2, 5_000_000L,
                        Category.STANDARD, new FamilyDetails(2, 1, 1, false), List.of()),
                Instant.EPOCH, Instant.EPOCH);
    }

    private DrawingCandidate drawing() {
        return drawing(new GeometryDocument("FEET", 40, 60, List.of(
                new RoomGeometry("G-R1", "LIVING_ROOM", 5, 5, 14, 20, 280, "GROUND"),
                new RoomGeometry("F-R1", "MASTER_BEDROOM", 5, 5, 13, 15, 195, "FIRST")),
                List.of(), List.of()));
    }

    private DrawingCandidate drawing(GeometryDocument geometry) {
        return new DrawingCandidate("drawing-1", "project-1", 3, "BALANCED", "Balanced concept",
                1200, 4_000_000L, 5_000_000L, 70, 70, 70, 80, geometry, List.of(), List.of(),
                List.of(), Map.of(), "READY", false, Instant.EPOCH);
    }

    private String sheet(String floor) {
        return """
                {"kind":"PLAN","floor":"%s","mediaType":"image/svg+xml","contentBase64":"%s",\
                "widthPx":900,"heightPx":1200,"pxPerFoot":24.0,"prompt":null,"seed":null,\
                "fileName":null}""".formatted(floor, SVG);
    }

    private String illustration() {
        return illustration(null);
    }

    private String illustration(String floor) {
        var floorValue = floor == null ? "null" : "\"" + floor + "\"";
        var subject = floor == null ? "whole house" : floor + " floor";
        return """
                {"kind":"ILLUSTRATION","floor":%s,"mediaType":"image/png","contentBase64":"%s",\
                "widthPx":1024,"heightPx":768,"pxPerFoot":null,\
                "prompt":"%s Indian family home in Jaipur","seed":7,"fileName":null}"""
                .formatted(floorValue, PNG, subject);
    }

    private String replyWithFallback(String warning) {
        return """
                {"requestId":"req-1","tenantId":"tenant-1","projectId":"project-1",\
                "contextVersion":"drawing-v3","provider":"DETERMINISTIC",\
                "model":"avas-drawing-svg-1.0.0","promptVersion":"plan-drawing-1.0.0",\
                "schemaVersion":"plan-drawing-1","fallbackUsed":true,"warnings":["%s"],\
                "style":"ELEVATION","images":[%s]}""".formatted(warning, sheet("GROUND"));
    }

    private String reply(String... images) {
        return """
                {"requestId":"req-1","tenantId":"tenant-1","projectId":"project-1",\
                "contextVersion":"drawing-v3","provider":"FLUX",\
                "model":"black-forest-labs/FLUX.1-schnell","promptVersion":"plan-drawing-1.0.0",\
                "schemaVersion":"plan-drawing-1","fallbackUsed":false,"warnings":[],\
                "style":"ELEVATION","images":[%s]}""".formatted(String.join(",", images));
    }

    private final class Server {
        private final RestTemplate template = new RestTemplate();
        private final MockRestServiceServer mock = MockRestServiceServer.createServer(template);
        private final AvasAiConceptRenderClient client = new AvasAiConceptRenderClient(
                new StubBuilder(template), "http://ai.test", "test-key", true, 5);

        org.springframework.test.web.client.ResponseActions expect() {
            return mock.expect(requestTo(URL)).andExpect(
                    org.springframework.test.web.client.match.MockRestRequestMatchers
                            .method(HttpMethod.POST));
        }

        void verify() {
            mock.verify();
        }
    }

    /** A builder that hands back the one template the mock server is bound to. */
    private static final class StubBuilder extends RestTemplateBuilder {
        private final RestTemplate template;

        private StubBuilder(RestTemplate template) {
            this.template = template;
        }

        @Override
        public RestTemplate build() {
            return template;
        }

        @Override
        public RestTemplateBuilder requestFactory(
                java.util.function.Supplier<org.springframework.http.client.ClientHttpRequestFactory> supplier) {
            return this;
        }
    }
}

package com.avas.platform.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * What the platform is willing to draw when something else decided where the rooms go.
 *
 * <p>A layout is adopted wholesale: it becomes the geometry that is validated, costed, taken off
 * for quantities and printed on the sheet a customer signs. So the interesting cases here are not
 * the happy path but the refusals — every one of them costs the customer nothing, because the local
 * planner draws the same programme a moment later, and every one of them is a plan the platform
 * would otherwise have put its name to.</p>
 */
class AvasAiLayoutClientTest {
    private static final String URL = "http://ai.test/api/v1/plan-layout";

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void aWellFormedLayoutIsAdoptedAsTheDrawing() {
        var server = new Server();
        server.expect().andRespond(withSuccess(reply(rooms(
                room("LIVING_ROOM", 5, 5, 14, 20),
                room("KITCHEN", 19, 5, 11, 12))), MediaTypeJson.JSON));

        var planned = server.client.plan("tenant-1", "project-1", "snapshot-1", details(),
                envelope(), details().parameters(), programme(), true, false, 0);

        assertThat(planned).isPresent();
        assertThat(planned.get().rooms()).extracting(RoomGeometry::type)
                .containsExactly("LIVING_ROOM", "KITCHEN");
        assertThat(planned.get().rooms().getFirst().id())
                .as("ids are stamped per storey").isEqualTo("G-R1");
        assertThat(planned.get().rooms().getFirst().area()).isEqualTo(280d);
        // Provenance travels with the rooms, because the PDF prints it. This reply came from the
        // service's own rules, so no model was involved and the drawing must not claim one was.
        assertThat(planned.get().provider()).isEqualTo("DETERMINISTIC");
        assertThat(planned.get().modelDrawn()).isFalse();
        server.verify();
    }

    @Test
    void aLayoutAModelDrewSaysSoInItsProvenance() {
        var server = new Server();
        // The provenance ends up in the PDF the customer signs, which said "No generative AI model"
        // on every drawing the platform had ever produced. A model placing the rooms has to change
        // that sentence, or the document is untrue about its own origin.
        server.expect().andRespond(withSuccess(
                reply(rooms(room("LIVING_ROOM", 5, 5, 14, 20)))
                        .replace("\"DETERMINISTIC\"", "\"ANTHROPIC\"")
                        .replace("avas-layout-hub-1.0.0", "claude-opus-5"),
                MediaTypeJson.JSON));

        var planned = server.client.plan("tenant-1", "project-1", "snapshot-1", details(),
                envelope(), details().parameters(), programme(), true, false, 0);

        assertThat(planned).isPresent();
        assertThat(planned.get().modelDrawn()).isTrue();
        assertThat(planned.get().model()).isEqualTo("claude-opus-5");
    }

    @Test
    void theProgrammeAndTheEnvelopeTravelWithTheRequest() {
        var server = new Server();
        server.expect()
                .andExpect(header("X-Service-Key", "test-key"))
                .andExpect(jsonPath("$.envelope.width").value(envelope().footprintWidth()))
                .andExpect(jsonPath("$.rooms[0].roomType").value("LIVING_ROOM"))
                .andExpect(jsonPath("$.rooms[0].priority").value("REQUIRED"))
                .andExpect(jsonPath("$.stairRequired").value(true))
                .andRespond(withSuccess(reply(rooms(room("LIVING_ROOM", 5, 5, 14, 20))),
                        MediaTypeJson.JSON));

        // The programme is what makes this an arrangement question rather than a planning one: the
        // service is told which rooms the home owes, so it cannot quietly plan a different house
        // from the one the customer's estimate was costed against.
        server.client.plan("tenant-1", "project-1", "snapshot-1", details(), envelope(),
                details().parameters(), programme(), true, false, 0);
        server.verify();
    }

    @Test
    void aLayoutContainingACorridorIsRefused() {
        var server = new Server();
        server.expect().andRespond(withSuccess(reply(rooms(
                room("LIVING_ROOM", 5, 5, 14, 20),
                room("CORRIDOR", 19, 5, 4, 20))), MediaTypeJson.JSON));

        // The one arrangement this route exists to avoid. A hub plan that came back with a passage
        // in it is the corridor planner's answer wearing the AI's provenance.
        assertThat(server.client.plan("tenant-1", "project-1", "snapshot-1", details(), envelope(),
                details().parameters(), programme(), true, false, 0)).isEmpty();
    }

    @Test
    void overlappingRoomsAreRefusedRatherThanDrawnOverEachOther() {
        var server = new Server();
        server.expect().andRespond(withSuccess(reply(rooms(
                room("LIVING_ROOM", 5, 5, 14, 20),
                room("KITCHEN", 15, 5, 11, 12))), MediaTypeJson.JSON));

        assertThat(server.client.plan("tenant-1", "project-1", "snapshot-1", details(), envelope(),
                details().parameters(), programme(), true, false, 0)).isEmpty();
    }

    @Test
    void aRoomOutsideTheBuildableEnvelopeIsRefused() {
        var server = new Server();
        // A room running past the setback ring cannot legally be built, and the validator
        // downstream would report it as a violation on a drawing the customer has already seen.
        var beyond = envelope().footprintX() + envelope().footprintWidth() - 4;
        server.expect().andRespond(withSuccess(reply(rooms(
                room("LIVING_ROOM", 5, 5, 14, 20),
                room("KITCHEN", beyond, 5, 13, 12))), MediaTypeJson.JSON));

        assertThat(server.client.plan("tenant-1", "project-1", "snapshot-1", details(), envelope(),
                details().parameters(), programme(), true, false, 0)).isEmpty();
    }

    @Test
    void aRoomBelowTheSizeItIsUsableAtIsRefused() {
        var server = new Server();
        // A living room six feet across has the right name and is not a living room.
        server.expect().andRespond(withSuccess(reply(rooms(
                room("LIVING_ROOM", 5, 5, 6, 20))), MediaTypeJson.JSON));

        assertThat(server.client.plan("tenant-1", "project-1", "snapshot-1", details(), envelope(),
                details().parameters(), programme(), true, false, 0)).isEmpty();
    }

    @Test
    void aRoomTypeThePlatformCannotDrawIsRefused() {
        var server = new Server();
        // Drawn at a fallback size it would be labelled with a word no renderer has furniture for,
        // and counted in the schedule as though the platform understood it.
        server.expect().andRespond(withSuccess(reply(rooms(
                room("DRAWING_ROOM", 5, 5, 14, 20))), MediaTypeJson.JSON));

        assertThat(server.client.plan("tenant-1", "project-1", "snapshot-1", details(), envelope(),
                details().parameters(), programme(), true, false, 0)).isEmpty();
    }

    @Test
    void aLayoutMissingAStoreyIsRefused() {
        var server = new Server();
        // A storey with no floor drawn on it reads downstream as a home one storey shorter than the
        // one the customer was quoted for.
        server.expect().andRespond(withSuccess(reply(rooms(room("LIVING_ROOM", 5, 5, 14, 20))),
                MediaTypeJson.JSON));

        assertThat(server.client.plan("tenant-1", "project-1", "snapshot-1", twoStorey(),
                envelope(), twoStorey().parameters(), programme(), true, false, 0)).isEmpty();
    }

    @Test
    void aLayoutPlannedForAnotherSnapshotIsRefused() {
        var server = new Server();
        server.expect().andRespond(withSuccess(
                reply(rooms(room("LIVING_ROOM", 5, 5, 14, 20)))
                        .replace("\"snapshot-1\"", "\"snapshot-9\""), MediaTypeJson.JSON));

        assertThat(server.client.plan("tenant-1", "project-1", "snapshot-1", details(), envelope(),
                details().parameters(), programme(), true, false, 0)).isEmpty();
    }

    @Test
    void anUnreachableServiceFallsBackWithoutThrowing() {
        var server = new Server();
        server.expect().andRespond(withServerError());

        assertThat(server.client.plan("tenant-1", "project-1", "snapshot-1", details(), envelope(),
                details().parameters(), programme(), true, false, 0)).isEmpty();
    }

    @Test
    void theRouteIsNotCalledAtAllWhenItIsDisabled() {
        var builder = new RestTemplateBuilder();
        var client = new AvasAiLayoutClient(builder, json, "http://ai.test", "test-key", false, 5);

        // No MockRestServiceServer is bound, so any call would fail the test by connecting.
        assertThat(client.plan("tenant-1", "project-1", "snapshot-1", details(), envelope(),
                details().parameters(), programme(), true, false, 0)).isEmpty();
    }

    // -------------------------------------------------------------------------------------------

    /** Binds a mock transport to a client built exactly as Spring would build it. */
    private final class Server {
        private final RestTemplate template = new RestTemplate();
        private final MockRestServiceServer mock = MockRestServiceServer.createServer(template);
        private final AvasAiLayoutClient client = new AvasAiLayoutClient(
                new StubBuilder(template), json, "http://ai.test", "test-key", true, 5);

        org.springframework.test.web.client.ResponseActions expect() {
            return mock.expect(requestTo(URL)).andExpect(method());
        }

        void verify() {
            mock.verify();
        }

        private org.springframework.test.web.client.RequestMatcher method() {
            return org.springframework.test.web.client.match.MockRestRequestMatchers
                    .method(HttpMethod.POST);
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

    private static final class MediaTypeJson {
        private static final org.springframework.http.MediaType JSON =
                org.springframework.http.MediaType.APPLICATION_JSON;
    }

    private String rooms(String... entries) {
        return String.join(",", entries);
    }

    private String room(String type, double x, double y, double width, double length) {
        return """
                {"roomType":"%s","floor":"GROUND","x":%s,"y":%s,"width":%s,"length":%s,\
                "opensOff":null}""".formatted(type, x, y, width, length);
    }

    private String reply(String roomsJson) {
        return """
                {"requestId":"req-1","tenantId":"tenant-1","projectId":"project-1",\
                "contextVersion":"snapshot-1","provider":"DETERMINISTIC",\
                "model":"avas-layout-hub-1.0.0","providerRequestId":null,\
                "promptVersion":"layout-1.0.0","schemaVersion":"plan-layout-1",\
                "fallbackUsed":false,"warnings":[],\
                "layout":{"floors":[{"floor":"GROUND","rooms":[%s],"hub":["LIVING_ROOM"]}],\
                "omitted":[],"notes":[]}}""".formatted(roomsJson);
    }

    /** The buildable footprint of a 40 x 60 plot, derived rather than assumed. */
    private BuildableEnvelope envelope() {
        return BuildableEnvelope.derive(details().boundary(),
                SetbackRule.forUsage(details().boundary(), 1, HomeParameters.STANDARD_SETBACK),
                1, Facing.NORTH, 0);
    }

    private BasicDetailsRequest details() {
        return new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur", 1, 9_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 0, false), List.of());
    }

    private BasicDetailsRequest twoStorey() {
        return new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur", 2, 9_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 0, false), List.of());
    }

    private List<FloorPlanner.ProgrammeRoom> programme() {
        return List.of(
                new FloorPlanner.ProgrammeRoom("LIVING_ROOM", "GROUND", 230, "REQUIRED"),
                new FloorPlanner.ProgrammeRoom("KITCHEN", "GROUND", 115, "REQUIRED"));
    }
}

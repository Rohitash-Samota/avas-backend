package com.avas.platform.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The AVAS AI layout route, with the platform's own planner standing in whenever it cannot answer.
 *
 * <p>Every failure path returns {@link Optional#empty()} and the caller plans locally, so the
 * customer always gets a drawing. That is also why this class is so careful about what it accepts:
 * a layout is adopted wholesale — it becomes the geometry that is validated, costed, taken off for
 * quantities and drawn on the sheet the customer signs off — so a reply that is merely well-formed
 * is not good enough. Rooms that overlap, leave the envelope, or are drawn at a size nobody could
 * use are refused here rather than discovered three services downstream.</p>
 *
 * <p>The AI service applies the same checks before it answers. Applying them again is not
 * redundancy: it is the boundary between two deployments that can be versioned independently, and
 * the only place the platform can be sure what it is about to draw.</p>
 */
@Component
class AvasAiLayoutClient implements LayoutClient {
    private static final Logger log = LoggerFactory.getLogger(AvasAiLayoutClient.class);

    /** Overlap two rooms may share before they are the same floor twice, in feet. */
    private static final double OVERLAP_TOLERANCE = 0.05d;
    /** Slack allowed against the envelope, absorbing the rounding both services do independently. */
    private static final double ENVELOPE_TOLERANCE = 0.15d;

    private final RestTemplate http;
    private final ObjectMapper json;
    private final String url;
    private final String serviceKey;
    private final boolean enabled;

    AvasAiLayoutClient(RestTemplateBuilder builder, ObjectMapper json,
            @Value("${avas.ai.base-url:http://127.0.0.1:8090}") String baseUrl,
            @Value("${avas.ai.service-key:}") String serviceKey,
            @Value("${avas.ai.layout-enabled:false}") boolean enabled,
            @Value("${avas.ai.layout-timeout-seconds:120}") long timeoutSeconds) {
        this.http = AvasAiHttp.client(builder, timeoutSeconds);
        this.json = json;
        this.url = baseUrl.replaceAll("/$", "") + "/api/v1/plan-layout";
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.enabled = enabled;
    }

    @Override
    public Optional<Layout> plan(String tenantId, String projectId,
            String contextVersion, BasicDetailsRequest details, BuildableEnvelope envelope,
            HomeParameters parameters, List<FloorPlanner.ProgrammeRoom> programme,
            boolean stairRequired, boolean liftRequired, int indoorParkingBays) {
        if (!enabled || serviceKey.isBlank() || programme == null || programme.isEmpty()) {
            return Optional.empty();
        }
        try {
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Key", serviceKey);
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            var body = payload(tenantId, projectId, contextVersion, details, envelope, parameters,
                    programme, stairRequired, liftRequired, indoorParkingBays);
            var response = http.postForObject(url, new HttpEntity<>(body, headers), AiResponse.class);
            if (response == null || response.layout() == null || response.layout().floors() == null) {
                return refuse(projectId, "the service returned an incomplete layout");
            }
            if (!tenantId.equals(response.tenantId()) || !projectId.equals(response.projectId())
                    || !contextVersion.equals(response.contextVersion())) {
                return refuse(projectId, "the service answered for a different project snapshot");
            }
            return adopt(projectId, response, details, envelope);
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("layout_fallback project={} reason={}", projectId, exception.getMessage());
            return Optional.empty();
        }
    }

    /**
     * The service's answer, checked against the ground it was planned on before it is adopted.
     *
     * <p>Refusing here costs the customer nothing — the local planner draws the same programme a
     * moment later — so the bar is what the platform is willing to put its name to rather than what
     * it can be talked into accepting.</p>
     */
    private Optional<Layout> adopt(String projectId, AiResponse response,
            BasicDetailsRequest details, BuildableEnvelope envelope) {
        var rooms = new ArrayList<RoomGeometry>();
        var expected = expectedFloors(details.floors());
        var seen = new ArrayList<String>();

        for (var floor : response.layout().floors()) {
            if (floor == null || floor.floor() == null || floor.rooms() == null) {
                return refuse(projectId, "a storey arrived without rooms");
            }
            var name = floor.floor().toUpperCase(Locale.ROOT);
            if (!expected.contains(name)) {
                return refuse(projectId, "it planned a " + name.toLowerCase(Locale.ROOT)
                        + " floor this home does not have");
            }
            seen.add(name);
            var index = 1;
            var placed = new ArrayList<RoomGeometry>();
            for (var room : floor.rooms()) {
                var geometry = geometry(room, name, index++);
                if (geometry == null) {
                    return refuse(projectId, "a room arrived without a usable rectangle");
                }
                if (RoomSpec.CORRIDOR.equals(geometry.type())) {
                    // The one arrangement this route exists to avoid. A hub plan that came back
                    // with a passage in it is the corridor planner's answer wearing the AI's
                    // provenance, and the customer would be none the wiser.
                    return refuse(projectId, "a hub layout may not contain a corridor");
                }
                var unusable = unusable(geometry);
                if (unusable != null) {
                    return refuse(projectId, unusable);
                }
                if (outside(geometry, envelope)) {
                    return refuse(projectId, roomLabel(geometry)
                            + " falls outside the buildable envelope");
                }
                var clash = overlapping(geometry, placed);
                if (clash != null) {
                    return refuse(projectId, roomLabel(geometry) + " overlaps " + roomLabel(clash)
                            + " on the " + name.toLowerCase(Locale.ROOT) + " floor");
                }
                placed.add(geometry);
            }
            if (placed.isEmpty()) {
                return refuse(projectId, "the " + name.toLowerCase(Locale.ROOT)
                        + " floor arrived empty");
            }
            rooms.addAll(placed);
        }

        if (!seen.containsAll(expected)) {
            // A storey missing from the reply is a storey with no floor drawn on it, which every
            // consumer downstream would read as a home one storey shorter than the one quoted.
            return refuse(projectId, "it did not plan every storey of this home");
        }
        log.info("layout_adopted project={} provider={} model={} rooms={}", projectId,
                response.provider(), response.model(), rooms.size());
        return Optional.of(new Layout(List.copyOf(rooms), response.provider(), response.model(),
                response.fallbackUsed()));
    }

    private RoomGeometry geometry(AiRoom room, String floor, int index) {
        if (room == null || room.roomType() == null || room.roomType().isBlank()) return null;
        var width = round2(room.width());
        var length = round2(room.length());
        if (!(width > 0.05) || !(length > 0.05)) return null;
        var type = room.roomType().toUpperCase(Locale.ROOT);
        return new RoomGeometry(prefix(floor) + "-R" + index, type, round2(room.x()),
                round2(room.y()), width, length, round2(width * length), floor);
    }

    /**
     * Why this room could not be built, or {@code null} when it can.
     *
     * <p>A name the platform holds no dimensions for is refused rather than drawn at a fallback
     * size: it would be labelled with a word no renderer has furniture for and counted in the
     * schedule as though the platform understood it.</p>
     */
    private String unusable(RoomGeometry room) {
        if (!RoomSpec.knows(room.type())) {
            return "it placed a " + roomLabel(room) + ", which this platform cannot draw";
        }
        var spec = RoomSpec.of(room.type());
        var shortest = Math.min(room.width(), room.length());
        if (shortest + 0.05 < spec.minShortSide()) {
            return roomLabel(room) + " is drawn " + String.format(Locale.ROOT, "%.1f", shortest)
                    + " ft across, below the " + fmt(spec.minShortSide()) + " ft it is usable at";
        }
        return null;
    }

    private boolean outside(RoomGeometry room, BuildableEnvelope envelope) {
        return room.x() < envelope.footprintX() - ENVELOPE_TOLERANCE
                || room.y() < envelope.footprintY() - ENVELOPE_TOLERANCE
                || room.x() + room.width()
                        > envelope.footprintX() + envelope.footprintWidth() + ENVELOPE_TOLERANCE
                || room.y() + room.length()
                        > envelope.footprintY() + envelope.footprintLength() + ENVELOPE_TOLERANCE;
    }

    private RoomGeometry overlapping(RoomGeometry room, List<RoomGeometry> placed) {
        for (var other : placed) {
            if (room.x() + room.width() - OVERLAP_TOLERANCE > other.x()
                    && other.x() + other.width() - OVERLAP_TOLERANCE > room.x()
                    && room.y() + room.length() - OVERLAP_TOLERANCE > other.y()
                    && other.y() + other.length() - OVERLAP_TOLERANCE > room.y()) {
                return other;
            }
        }
        return null;
    }

    private <T> Optional<T> refuse(String projectId, String reason) {
        log.warn("layout_rejected project={} reason={}", projectId, reason);
        return Optional.empty();
    }

    private Map<String, Object> payload(String tenantId, String projectId, String contextVersion,
            BasicDetailsRequest details, BuildableEnvelope envelope, HomeParameters parameters,
            List<FloorPlanner.ProgrammeRoom> programme, boolean stairRequired,
            boolean liftRequired, int indoorParkingBays) {
        var result = new LinkedHashMap<String, Object>();
        result.put("tenantId", tenantId);
        result.put("projectId", projectId);
        result.put("contextVersion", contextVersion);
        result.put("plot", plot(details, envelope));
        result.put("envelope", Map.of(
                "x", round2(envelope.footprintX()),
                "y", round2(envelope.footprintY()),
                "width", round2(envelope.footprintWidth()),
                "length", round2(envelope.footprintLength())));
        result.put("requested", json.convertValue(parameters, Map.class));
        result.put("rooms", programme.stream().map(room -> Map.of(
                "roomType", room.type(),
                "floor", room.floor(),
                "priority", room.priority(),
                "targetAreaSqFt", round2(room.targetArea()))).toList());
        result.put("indoorParkingBays", indoorParkingBays);
        result.put("stairRequired", stairRequired);
        result.put("liftRequired", liftRequired);
        return Map.copyOf(result);
    }

    /** The plot as the geometry engine measured it, so both services plan the same ground. */
    private Map<String, Object> plot(BasicDetailsRequest details, BuildableEnvelope envelope) {
        var boundary = details.boundary();
        var plot = new LinkedHashMap<String, Object>();
        plot.put("width", details.plotWidth());
        plot.put("length", details.plotLength());
        plot.put("unit", "FEET");
        plot.put("roadFacing", details.roadFacing().name());
        plot.put("city", details.city());
        plot.put("floors", details.floors());
        plot.put("areaSqFt", round(details.plotArea()));
        plot.put("corners", boundary.vertices().size());
        plot.put("irregular", boundary.irregular());
        plot.put("shape", boundary.irregular() ? "CUSTOM" : "RECTANGLE");
        plot.put("buildableAreaSqFt", round(envelope.buildableArea()));
        plot.put("plannableAreaSqFt", round(envelope.plannableArea()));
        return Map.copyOf(plot);
    }

    private static List<String> expectedFloors(int floors) {
        var names = List.of("GROUND", "FIRST", "SECOND");
        return names.subList(0, Math.max(1, Math.min(names.size(), floors)));
    }

    private static String prefix(String floor) {
        return switch (floor) {
            case "GROUND" -> "G";
            case "FIRST" -> "F1";
            default -> "F2";
        };
    }

    private static String roomLabel(RoomGeometry room) {
        var words = room.type().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static double round(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiResponse(String requestId, String tenantId, String projectId,
                              String contextVersion, String provider, String model,
                              String providerRequestId, String promptVersion, String schemaVersion,
                              boolean fallbackUsed, List<String> warnings, AiLayout layout) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiLayout(List<AiFloor> floors, List<String> omitted, List<String> notes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiFloor(String floor, List<AiRoom> rooms, List<String> hub) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiRoom(String roomType, String floor, double x, double y, double width,
                          double length, String opensOff) {}
}

package com.avas.platform.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The AVAS AI drawing route, asked only for the picture and never for the plan.
 *
 * <p>The service answers with both: the measured sheets it renders from the coordinates it is given,
 * and — where it is configured with an image model — one illustration of the same house. This client
 * keeps the illustration and discards the sheets, because the platform already draws its own plan
 * from geometry it validated, and two measured drawings of one home that disagree by a rounding is a
 * support ticket rather than a feature.</p>
 *
 * <p>Every failure path leaves the customer with the measured plan alone, which is what they would
 * have seen anyway. That is why this client is far less suspicious of its answer than
 * {@link AvasAiLayoutClient} is of its own: nothing here is adopted, so the worst a bad reply can do
 * is not be shown. It does say why it is not being shown — see {@link ConceptRenderClient.Outcome}
 * — because "no image model here" and "the render failed" have different fixes.</p>
 */
@Component
class AvasAiConceptRenderClient implements ConceptRenderClient {
    private static final Logger log = LoggerFactory.getLogger(AvasAiConceptRenderClient.class);

    /** Largest picture worth holding in memory and streaming to a browser. */
    private static final int MAX_IMAGE_BYTES = 12 * 1024 * 1024;

    /**
     * Spaces the AI service's room vocabulary does not contain.
     *
     * <p>Its {@code RoomType} is a closed set and rejects the request outright on anything outside
     * it, so these are dropped rather than sent. Both are deliberate omissions there: the AI service
     * plans a hub and has no corridor, and it merges parking into a courtyard itself. Neither
     * changes what the house looks like from the road, which is all this call is asking about.</p>
     */
    private static final Set<String> UNKNOWN_TO_AI = Set.of("CORRIDOR", "COURTYARD_PARKING");

    private static final List<String> FLOORS = List.of("GROUND", "FIRST", "SECOND");

    private final RestTemplate http;
    private final String url;
    private final String serviceKey;
    private final boolean enabled;

    AvasAiConceptRenderClient(RestTemplateBuilder builder,
            @Value("${avas.ai.base-url:http://127.0.0.1:8090}") String baseUrl,
            @Value("${avas.ai.service-key:}") String serviceKey,
            @Value("${avas.ai.render-enabled:false}") boolean enabled,
            @Value("${avas.ai.render-timeout-seconds:180}") long timeoutSeconds) {
        // Generous by the standards of the other clients, and it has to be: a diffusion model on a
        // cold pipeline spends its first call loading tens of gigabytes of weights, and every call
        // after that is still seconds rather than milliseconds.
        this.http = AvasAiHttp.client(builder, timeoutSeconds);
        this.url = baseUrl.replaceAll("/$", "") + "/api/v1/plan-drawing";
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.enabled = enabled;
    }

    @Override
    public Outcome illustrate(String tenantId, String contextVersion, ProjectSummary project,
            DrawingCandidate drawing, Style style, String brief, String floor,
            RenderOptions options) {
        if (!enabled || serviceKey.isBlank() || project == null || project.details() == null
                || drawing == null || drawing.geometry() == null) {
            return Outcome.notConfigured();
        }
        try {
            var floors = floors(drawing.geometry());
            if (floors.isEmpty()) {
                log.debug("render_skipped drawing={} reason=no recognisable rooms", drawing.id());
                return Outcome.failed("this drawing has no rooms the AI service recognises");
            }
            var requestedFloor = normalizeFloor(floor);
            if (floorwise(style) && requestedFloor == null) {
                return Outcome.failed("a floor is required for a floor-plan illustration");
            }
            if (requestedFloor != null && floors.stream()
                    .noneMatch(value -> requestedFloor.equals(value.get("floor")))) {
                return Outcome.failed("the requested floor is not present in this drawing");
            }

            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Key", serviceKey);
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            var body = payload(tenantId, project, contextVersion, drawing, floors, style, brief,
                    requestedFloor, options == null ? RenderOptions.defaults() : options);
            var response = http.postForObject(url, new HttpEntity<>(body, headers), AiResponse.class);
            return adopt(drawing.id(), response, style, requestedFloor);
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("render_unavailable drawing={} reason={}", drawing.id(), exception.getMessage());
            return Outcome.failed("the AVAS AI service could not be reached: " + exception.getMessage());
        }
    }

    /**
     * The illustration out of the reply, or empty when there is not one to show.
     *
     * <p>The measured sheets that arrive alongside it are dropped here rather than never asked for:
     * the route renders them on every path by design, and a client that treated their presence as
     * the answer would show the customer a second, subtly different plan of their own house.</p>
     */
    private Outcome adopt(String drawingId, AiResponse response, Style style,
            String requestedFloor) {
        if (response == null || response.images() == null) {
            return Outcome.failed("the AI service returned no images");
        }
        var sawIllustration = false;
        for (var image : response.images()) {
            if (image == null || !"ILLUSTRATION".equalsIgnoreCase(image.kind())) continue;
            sawIllustration = true;
            if (floorwise(style)) {
                var imageFloor = image.floor() == null
                        ? null : image.floor().trim().toUpperCase(Locale.ROOT);
                if (!requestedFloor.equals(imageFloor)) continue;
            }
            if (image.contentBase64() == null || image.contentBase64().isBlank()) continue;
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(image.contentBase64());
            } catch (IllegalArgumentException malformed) {
                log.warn("render_undecodable drawing={} reason={}", drawingId, malformed.getMessage());
                return Outcome.failed("the generated image could not be decoded");
            }
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                log.warn("render_rejected drawing={} bytes={}", drawingId, bytes.length);
                return Outcome.failed("the generated image was empty or too large to show");
            }
            log.info("render_ready drawing={} floor={} provider={} model={} bytes={}", drawingId,
                    requestedFloor, response.provider(), response.model(), bytes.length);
            return Outcome.of(new Render(bytes,
                    image.mediaType() == null ? MediaType.IMAGE_PNG_VALUE : image.mediaType(),
                    image.prompt(), response.provider(), response.model(),
                    response.fallbackUsed(), response.warnings()));
        }
        if (response.fallbackUsed()) {
            var reason = response.warnings() == null || response.warnings().isEmpty()
                    ? "the AI service did not say"
                    : String.join(" | ", response.warnings());
            log.warn("render_fallback drawing={} floor={} reason={}", drawingId, requestedFloor, reason);
            return Outcome.failed(reason);
        }
        if (floorwise(style) && sawIllustration) {
            return Outcome.failed("the AI service returned an illustration without the requested "
                    + requestedFloor.toLowerCase(Locale.ROOT) + " floor identity");
        }
        log.debug("render_absent drawing={} reason=no image model configured", drawingId);
        return Outcome.notConfigured();
    }

    private static boolean floorwise(Style style) {
        return style == Style.FLOOR_PLAN || style == Style.FLOOR_PLAN_3D;
    }

    private static String normalizeFloor(String floor) {
        if (floor == null || floor.isBlank()) return null;
        var normalized = floor.trim().toUpperCase(Locale.ROOT);
        if (!FLOORS.contains(normalized)) {
            throw new IllegalArgumentException("unknown floor " + floor);
        }
        return normalized;
    }

    /** The placed rooms regrouped by storey, in the shape the AI service's layout schema expects. */
    private List<Map<String, Object>> floors(GeometryDocument geometry) {
        var byFloor = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (var room : geometry.rooms()) {
            if (room == null || room.type() == null) continue;
            var type = room.type().toUpperCase(Locale.ROOT);
            if (UNKNOWN_TO_AI.contains(type)) continue;
            var floor = room.floor() == null ? "GROUND" : room.floor().toUpperCase(Locale.ROOT);
            if (!FLOORS.contains(floor)) continue;
            if (!(room.width() > 0) || !(room.length() > 0)) continue;
            byFloor.computeIfAbsent(floor, key -> new ArrayList<>()).add(new LinkedHashMap<>(Map.of(
                    "roomType", type,
                    "floor", floor,
                    "x", round2(room.x()),
                    "y", round2(room.y()),
                    "width", round2(room.width()),
                    "length", round2(room.length()))));
        }
        var out = new ArrayList<Map<String, Object>>();
        for (var name : FLOORS) {
            var rooms = byFloor.get(name);
            // The schema caps a storey at forty rooms; a plan that long is a plan with a problem,
            // and truncating it here only changes what the picture is generated from.
            if (rooms == null || rooms.isEmpty()) continue;
            out.add(new LinkedHashMap<>(Map.of(
                    "floor", name,
                    "rooms", rooms.size() > 40 ? rooms.subList(0, 40) : rooms)));
        }
        return out;
    }

    private Map<String, Object> payload(String tenantId, ProjectSummary project,
            String contextVersion, DrawingCandidate drawing, List<Map<String, Object>> floors,
            Style style, String brief, String floor, RenderOptions options) {
        var details = project.details();
        var body = new LinkedHashMap<String, Object>();
        body.put("tenantId", tenantId);
        body.put("projectId", project.id());
        body.put("contextVersion", contextVersion);
        body.put("plot", new LinkedHashMap<>(Map.of(
                "width", round2(details.plotWidth()),
                "length", round2(details.plotLength()),
                "roadFacing", details.roadFacing().name(),
                "city", details.city(),
                "floors", Math.max(1, Math.min(3, details.floors())))));
        body.put("envelope", envelope(drawing.geometry()));
        body.put("layout", new LinkedHashMap<>(Map.of("floors", floors)));
        body.put("style", style.name());
        // One storey per picture. Asked for the whole home on one canvas the model drew something
        // that was neither floor — a plan is a view of one level, and two of them competing for the
        // same frame is why the linework came back as mush.
        if (floor != null && !floor.isBlank()) {
            body.put("floors", List.of(floor.trim().toUpperCase(Locale.ROOT)));
        }
        if (brief != null && !brief.isBlank()) {
            body.put("brief", brief.strip().substring(0, Math.min(400, brief.strip().length())));
        }
        // What the customer actually chose. Until this was sent, the drawing route saw coordinates
        // and a plot and nothing else: a customer who picked a dog-legged stair, a future lift shaft
        // and two covered bays got a picture that knew about none of them, while the layout it
        // illustrates had placed all three. Several of these are invisible in the rectangles — a
        // future shaft and a working lift are the same five-foot square — so no amount of reading
        // the geometry recovers them.
        requested(details).ifPresent(value -> body.put("requested", value));
        if (details.family() != null) {
            var family = details.family();
            body.put("family", new LinkedHashMap<>(Map.of(
                    "adults", family.adults(),
                    "children", family.children(),
                    "seniorCitizens", family.seniorCitizens(),
                    "regularGuests", family.regularGuests())));
        }
        // The AI service floors its budget at ten lakh and rejects anything under it, so a project
        // recorded below that is sent without one rather than failing the whole render over a field
        // that only ever adds an adjective to a sentence.
        if (details.budget() >= 1_000_000L && details.category() != null) {
            body.put("budget", new LinkedHashMap<>(Map.of(
                    "total", details.budget(),
                    "currency", "INR",
                    "finishTier", details.category().name())));
        }
        if (details.preferences() != null && !details.preferences().isEmpty()) {
            body.put("preferences", details.preferences().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.strip().substring(0, Math.min(80, value.strip().length())))
                    .limit(10)
                    .toList());
        }
        body.put("options", new LinkedHashMap<>(Map.of(
                "furnishing", options.furnishing(),
                "palette", options.palette(),
                "landscaping", options.landscaping(),
                "showCars", options.showCars(),
                "labels", options.labels())));
        return body;
    }

    /**
     * The home parameters in the shape the AI service declares, or empty when none were recorded.
     *
     * <p>Spelled out field by field rather than serialised straight from {@link HomeParameters},
     * because the AI service's request model is {@code extra="forbid"}: one field it has not
     * declared rejects the whole call with a 422, and the reply the customer sees is the measured
     * plan with "the render failed" beside it. Naming them here means a field added on this side
     * cannot silently break every render on that one.</p>
     */
    private java.util.Optional<Map<String, Object>> requested(BasicDetailsRequest details) {
        var parameters = details.parameters();
        if (parameters == null) {
            return java.util.Optional.empty();
        }
        var out = new LinkedHashMap<String, Object>();
        out.put("homeType", parameters.homeType());
        out.put("staircaseType", parameters.staircaseType());
        out.put("liftProvision", parameters.liftProvision());
        out.put("balconyCount", parameters.balconyCount());
        out.put("terraceRequired", parameters.terraceRequired());
        out.put("courtyardRequired", parameters.courtyardRequired());
        out.put("accessibleGroundFloor", parameters.accessibleGroundFloor());
        out.put("parkingCars", parameters.parkingCars());
        out.put("solarReady", parameters.solarReady());
        out.put("rainwaterHarvesting", parameters.rainwaterHarvesting());
        out.put("plotUsage", parameters.plotUsage());
        return java.util.Optional.of(out);
    }

    /**
     * The rectangle every room was drawn inside, taken from the plan rather than re-derived.
     *
     * <p>The setback envelope would be the truer answer, but this call does not need it and asking
     * for it would mean re-running {@link BuildableEnvelope} against a boundary that may have moved
     * since the drawing was generated — a second opinion whose only effect could be to disagree with
     * the drawing it is describing. The bounding box of what was actually placed cannot.</p>
     */
    private Map<String, Object> envelope(GeometryDocument geometry) {
        var outline = geometry.buildableOutline();
        double minX, minY, maxX, maxY;
        if (outline != null && outline.size() >= 3) {
            minX = outline.stream().mapToDouble(PlotVertex::x).min().orElse(0);
            minY = outline.stream().mapToDouble(PlotVertex::y).min().orElse(0);
            maxX = outline.stream().mapToDouble(PlotVertex::x).max().orElse(0);
            maxY = outline.stream().mapToDouble(PlotVertex::y).max().orElse(0);
        } else {
            minX = geometry.rooms().stream().mapToDouble(RoomGeometry::x).min().orElse(0);
            minY = geometry.rooms().stream().mapToDouble(RoomGeometry::y).min().orElse(0);
            maxX = geometry.rooms().stream().mapToDouble(r -> r.x() + r.width()).max().orElse(0);
            maxY = geometry.rooms().stream().mapToDouble(r -> r.y() + r.length()).max().orElse(0);
        }
        return new LinkedHashMap<>(Map.of(
                "x", round2(minX),
                "y", round2(minY),
                "width", round2(Math.max(1, maxX - minX)),
                "length", round2(Math.max(1, maxY - minY))));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiImage(String kind, String floor, String mediaType, String contentBase64,
                   String prompt, Integer widthPx, Integer heightPx) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiResponse(String provider, String model, boolean fallbackUsed, List<String> warnings,
                      String style, List<AiImage> images) {}
}

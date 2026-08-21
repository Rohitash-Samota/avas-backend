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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class AvasAiPlanningParameterClient implements PlanningParameterClient {
    private static final Logger log = LoggerFactory.getLogger(AvasAiPlanningParameterClient.class);

    private final RestTemplate http;
    private final ObjectMapper json;
    private final String url;
    private final String serviceKey;
    private final boolean enabled;

    AvasAiPlanningParameterClient(RestTemplateBuilder builder, ObjectMapper json,
            @Value("${avas.ai.base-url:http://127.0.0.1:8090}") String baseUrl,
            @Value("${avas.ai.service-key:}") String serviceKey,
            @Value("${avas.ai.enabled:false}") boolean enabled,
            @Value("${avas.ai.timeout-seconds:90}") long timeoutSeconds) {
        this.http = AvasAiHttp.client(builder, timeoutSeconds);
        this.json = json;
        this.url = baseUrl.replaceAll("/$", "") + "/api/v1/plan-parameters";
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.enabled = enabled;
    }

    @Override
    public PlanningParameterSet optimize(String tenantId, String projectId, String contextVersion,
            BasicDetailsRequest details, BuildableEnvelope envelope) {
        if (!enabled) return local(details, envelope, null);
        if (serviceKey.isBlank()) return local(details, envelope, "AVAS AI service key is not configured");
        try {
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Key", serviceKey);
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            var response = http.postForObject(url, new HttpEntity<>(payload(tenantId, projectId,
                    contextVersion, details, envelope), headers), AiResponse.class);
            if (response == null || response.variants() == null || response.variants().size() != 3) {
                return local(details, envelope, "AVAS AI returned an incomplete parameter set");
            }
            if (!tenantId.equals(response.tenantId()) || !projectId.equals(response.projectId())
                    || !contextVersion.equals(response.contextVersion())) {
                return local(details, envelope, "AVAS AI returned parameters for a different project snapshot");
            }
            return new PlanningParameterSet(response.requestId(), response.providerRequestId(),
                    response.provider(), response.model(),
                    response.promptVersion(), response.schemaVersion(), response.fallbackUsed(),
                    safe(response.warnings()), List.copyOf(response.variants()));
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("planning_parameter_fallback project={} reason={}", projectId, exception.getMessage());
            return local(details, envelope, "AVAS AI was unavailable; deterministic parameters were used");
        }
    }

    private Map<String, Object> payload(String tenantId, String projectId, String contextVersion,
            BasicDetailsRequest details, BuildableEnvelope envelope) {
        var result = new LinkedHashMap<String, Object>();
        result.put("tenantId", tenantId);
        result.put("projectId", projectId);
        result.put("contextVersion", contextVersion);
        result.put("plot", plot(details, envelope));
        result.put("budget", Map.of("total", details.budget(), "currency", "INR", "finishTier",
                details.category().name()));
        result.put("family", Map.of("adults", details.family().adults(), "children",
                details.family().children(), "seniorCitizens", details.family().seniorCitizens(),
                "regularGuests", details.family().regularGuests()));
        result.put("preferences", details.preferences());
        result.put("requested", json.convertValue(details.parameters(), Map.class));
        return Map.copyOf(result);
    }

    /**
     * The plot as the geometry engine measured it, rather than as two numbers.
     *
     * <p>Width and length remain the outline's bounding box, which is all this payload used to
     * carry. Sizing a room programme from a bounding box sizes it for a plot that does not exist:
     * the six-corner outlines AVAS plans for enclose about a fifth less ground than the rectangle
     * around them, and a setback ring takes a third again. The areas here are the ones already
     * derived for this project, so the programme and the layout reason about the same ground.</p>
     */
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
        if (envelope != null) {
            plot.put("buildableAreaSqFt", round(envelope.buildableArea()));
            plot.put("plannableAreaSqFt", round(envelope.plannableArea()));
        }
        return Map.copyOf(plot);
    }

    private static double round(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private PlanningParameterSet local(BasicDetailsRequest details, BuildableEnvelope envelope,
            String warning) {
        return PlanningParameterSet.deterministic(details, envelope, warning);
    }

    private <T> List<T> safe(List<T> value) { return value == null ? List.of() : List.copyOf(value); }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiResponse(String requestId, String tenantId, String projectId, String contextVersion,
                              String provider, String model, String providerRequestId, String promptVersion,
                              String schemaVersion, boolean fallbackUsed, List<String> warnings,
                              List<PlanningParameterVariant> variants) {}
}

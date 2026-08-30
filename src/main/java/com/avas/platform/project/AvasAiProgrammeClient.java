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

/**
 * The AVAS AI programme route, with the platform's own rules standing in whenever it cannot answer.
 *
 * <p>Every failure path lands on {@link HouseholdProgramme#deterministic}, and every one of them
 * says why in the returned warnings. That matters more here than anywhere else in the pipeline: a
 * programme that silently fell back still produces a complete, plausible house, so nothing about the
 * recommendation a customer reads would otherwise reveal that the service was never reached.</p>
 */
@Component
class AvasAiProgrammeClient implements ProgrammeClient {
    private static final Logger log = LoggerFactory.getLogger(AvasAiProgrammeClient.class);

    private final RestTemplate http;
    private final ObjectMapper json;
    private final String url;
    private final String serviceKey;
    private final boolean enabled;

    AvasAiProgrammeClient(RestTemplateBuilder builder, ObjectMapper json,
            @Value("${avas.ai.base-url:http://127.0.0.1:8090}") String baseUrl,
            @Value("${avas.ai.service-key:}") String serviceKey,
            @Value("${avas.ai.enabled:false}") boolean enabled,
            @Value("${avas.ai.timeout-seconds:90}") long timeoutSeconds) {
        this.http = AvasAiHttp.client(builder, timeoutSeconds);
        this.json = json;
        this.url = baseUrl.replaceAll("/$", "") + "/api/v1/plan-programme";
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.enabled = enabled;
    }

    @Override
    public HouseholdProgramme plan(String tenantId, String projectId, String contextVersion,
            BasicDetailsRequest details, BuildableEnvelope envelope) {
        if (!enabled) return local(details, envelope, null);
        if (serviceKey.isBlank()) {
            return local(details, envelope, "AVAS AI service key is not configured");
        }
        try {
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Key", serviceKey);
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            var response = http.postForObject(url, new HttpEntity<>(payload(tenantId, projectId,
                    contextVersion, details, envelope), headers), AiResponse.class);
            if (response == null || response.programme() == null) {
                return local(details, envelope, "AVAS AI returned an incomplete programme");
            }
            if (!tenantId.equals(response.tenantId()) || !projectId.equals(response.projectId())
                    || !contextVersion.equals(response.contextVersion())) {
                return local(details, envelope,
                        "AVAS AI returned a programme for a different project snapshot");
            }
            return adopt(response, details, envelope);
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("programme_fallback project={} reason={}", projectId, exception.getMessage());
            return local(details, envelope,
                    "AVAS AI was unavailable; the deterministic programme was used");
        }
    }

    /**
     * The service's answer, checked against what this plot can carry before it is adopted.
     *
     * <p>AVAS AI applies these bounds itself, so a reply that breaches them means the two services
     * disagree about the envelope — which is exactly the case where trusting the remote answer
     * produces a brief the layout engine reports as a programme gap on every candidate it draws.</p>
     */
    private HouseholdProgramme adopt(AiResponse response, BasicDetailsRequest details,
            BuildableEnvelope envelope) {
        var proposed = response.programme();
        var need = details.family().bedroomsNeeded();
        var tier = SpecificationTier.of(details.category().name());
        var lounge = details.family().members() >= 4 && details.floors() > 1;
        var ceiling = Math.max(need, Math.min(HouseholdProgramme.MAXIMUM_BEDROOMS,
                HouseholdProgramme.capacity(details, envelope, tier, lounge)));
        if (proposed.bedrooms() < need || proposed.bedrooms() > ceiling) {
            return local(details, envelope, "AVAS AI proposed " + proposed.bedrooms()
                    + " bedrooms, which is outside the " + need + "-" + ceiling
                    + " this plot and household support; the deterministic programme was used");
        }
        return new HouseholdProgramme(proposed.bedrooms(), proposed.attachedBathrooms(),
                proposed.commonBathrooms(), proposed.parkingCars(), proposed.seniorBedroom(),
                proposed.familyLounge(), proposed.futureExpansion(), proposed.title(),
                safe(proposed.reasons()), response.provider(), response.model(),
                response.fallbackUsed(), safe(response.warnings()));
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

    /** The plot as the geometry engine measured it, so both services cap against the same ground. */
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

    private HouseholdProgramme local(BasicDetailsRequest details, BuildableEnvelope envelope,
            String warning) {
        return HouseholdProgramme.deterministic(details, envelope, warning);
    }

    private <T> List<T> safe(List<T> value) { return value == null ? List.of() : List.copyOf(value); }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiResponse(String requestId, String tenantId, String projectId,
                              String contextVersion, String provider, String model,
                              String providerRequestId, String promptVersion, String schemaVersion,
                              boolean fallbackUsed, List<String> warnings, AiProgramme programme) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiProgramme(int bedrooms, int attachedBathrooms, int commonBathrooms,
                               int parkingCars, boolean seniorBedroom, boolean familyLounge,
                               boolean futureExpansion, String title, List<String> reasons) {}
}

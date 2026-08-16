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
import java.util.Map;
import java.util.UUID;

/**
 * Calls the AVAS AI service for plot outline readings.
 *
 * <p>The service key never reaches the browser, and the model only ever returns a candidate
 * polygon: this client rebuilds it through {@link PlotBoundary}, so an outline that crosses itself,
 * encloses nothing or is implausibly large is dropped here rather than proposed to a customer.</p>
 */
@Component
class AvasAiPlotOutlineClient implements PlotOutlineClient {
    private static final Logger log = LoggerFactory.getLogger(AvasAiPlotOutlineClient.class);

    /** Contract version this client speaks; sent as the immutable context of every request. */
    private static final String CONTEXT_VERSION = "plot-outline-1";

    private final RestTemplate http;
    private final String url;
    private final String serviceKey;
    private final boolean enabled;

    AvasAiPlotOutlineClient(RestTemplateBuilder builder,
            @Value("${avas.ai.base-url:http://127.0.0.1:8090}") String baseUrl,
            @Value("${avas.ai.service-key:}") String serviceKey,
            @Value("${avas.ai.outline-enabled:false}") boolean enabled,
            @Value("${avas.ai.outline-timeout-seconds:45}") long timeoutSeconds) {
        this.http = AvasAiHttp.client(builder, timeoutSeconds);
        this.url = baseUrl.replaceAll("/$", "") + "/api/v1/plot-outline";
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.enabled = enabled;
    }

    @Override
    public boolean enabled() {
        return enabled && !serviceKey.isBlank();
    }

    @Override
    public PlotOutlineSuggestion propose(PlotOutlineQuery query) {
        if (!enabled()) {
            return PlotOutlineSuggestion.none(null);
        }
        try {
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Key", serviceKey);
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            var response = http.postForObject(url, new HttpEntity<>(payload(query), headers),
                    AiResponse.class);
            if (response == null) {
                return PlotOutlineSuggestion.none("AVAS AI returned no outline reading.");
            }
            return toSuggestion(response, query.roadFacing());
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("plot_outline_reading_unavailable source={} reason={}", query.source(),
                    exception.getMessage());
            return PlotOutlineSuggestion.none(
                    "AVAS could not reach the drawing reader; enter or draw the corners instead.");
        }
    }

    private Map<String, Object> payload(PlotOutlineQuery query) {
        var body = new LinkedHashMap<String, Object>();
        body.put("tenantId", query.tenantId() == null || query.tenantId().isBlank()
                ? "unknown-tenant" : query.tenantId());
        body.put("projectId", "wizard-draft");
        body.put("contextVersion", CONTEXT_VERSION);
        body.put("source", query.source().name());
        body.put("roadFacing", (query.roadFacing() == null ? Facing.NORTH : query.roadFacing()).name());
        if (query.description() != null && !query.description().isBlank()) {
            body.put("description", query.description());
        }
        if (query.knownWidthFeet() != null) body.put("knownWidthFeet", query.knownWidthFeet());
        if (query.knownLengthFeet() != null) body.put("knownLengthFeet", query.knownLengthFeet());
        if (query.source() == Source.DOCUMENT) {
            var document = new LinkedHashMap<String, Object>();
            document.put("fileName", query.fileName() == null ? "plot-drawing" : query.fileName());
            document.put("mediaType", query.mediaType());
            document.put("contentBase64", Base64.getEncoder().encodeToString(query.content()));
            document.put("pageText", query.pageText() == null ? "" : query.pageText());
            document.put("candidateDimensionsFeet", query.candidateDimensionsFeet());
            body.put("document", document);
        }
        return Map.copyOf(body);
    }

    /** Rebuilds the proposal through the platform's own geometry rules before trusting it. */
    private PlotOutlineSuggestion toSuggestion(AiResponse response, Facing roadFacing) {
        var notes = new ArrayList<String>();
        var proposal = response.proposal();
        if (proposal == null || proposal.vertices() == null || proposal.vertices().size() < 3) {
            notes.add("The drawing could not be measured automatically; draw or enter the corners.");
            return new PlotOutlineSuggestion(null, "CUSTOM", 0, List.of(), notes,
                    provider(response), model(response), response.fallbackUsed(),
                    safe(response.warnings()));
        }
        PlotBoundary boundary;
        try {
            boundary = new PlotBoundary(proposal.vertices().stream()
                    .map(vertex -> PlotVertex.of(vertex.x(), vertex.y())).toList(),
                    roadFacing == null ? Facing.NORTH : roadFacing);
        } catch (IllegalArgumentException exception) {
            log.warn("plot_outline_rejected reason={}", exception.getMessage());
            notes.add("The proposed outline failed AVAS geometry checks and was discarded: "
                    + exception.getMessage() + ".");
            return new PlotOutlineSuggestion(null, "CUSTOM", 0, List.of(), notes,
                    provider(response), model(response), response.fallbackUsed(),
                    safe(response.warnings()));
        }
        notes.addAll(safe(proposal.notes()));
        return new PlotOutlineSuggestion(boundary,
                proposal.shape() == null ? "CUSTOM" : proposal.shape(),
                Math.clamp(proposal.confidence(), 0, 100),
                safe(proposal.dimensionsFeet()), notes, provider(response), model(response),
                response.fallbackUsed(), safe(response.warnings()));
    }

    private String provider(AiResponse response) {
        return response.provider() == null ? "AVAS_AI" : response.provider();
    }

    private String model(AiResponse response) {
        return response.model() == null ? "not-recorded" : response.model();
    }

    private <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : List.copyOf(value);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiResponse(String requestId, String provider, String model, String providerRequestId,
                              String promptVersion, String schemaVersion, boolean fallbackUsed,
                              List<String> warnings, AiProposal proposal) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiProposal(String shape, List<AiVertex> vertices, List<Double> dimensionsFeet,
                              int confidence, List<String> notes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiVertex(double x, double y) {}
}

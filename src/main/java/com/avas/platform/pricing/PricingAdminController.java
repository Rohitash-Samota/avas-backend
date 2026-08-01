package com.avas.platform.pricing;

import com.avas.platform.auth.AvasPrincipal;
import com.avas.platform.security.ActiveRoleFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.avas.platform.pricing.PricingModels.*;

@RestController
@RequestMapping("/api/v1/admin")
public class PricingAdminController {
    private final PricingService pricing;

    PricingAdminController(PricingService pricing) { this.pricing = pricing; }

    @GetMapping("/configuration")
    PlatformConfigurationResponse configuration() { return pricing.getConfiguration(); }

    @PutMapping("/configuration")
    PlatformConfigurationResponse updateConfiguration(@AuthenticationPrincipal AvasPrincipal principal,
            HttpServletRequest servletRequest, @Valid @RequestBody PlatformConfigurationRequest request) {
        return pricing.updateConfiguration(principal.userId(), activeRole(servletRequest), request);
    }

    @GetMapping("/models")
    List<ModelReleaseResponse> models() { return pricing.modelReleases(); }

    @PostMapping("/models")
    ModelReleaseResponse registerModel(@AuthenticationPrincipal AvasPrincipal principal,
            HttpServletRequest servletRequest, @Valid @RequestBody ModelReleaseRequest request) {
        return pricing.registerModel(principal.userId(), activeRole(servletRequest), request);
    }

    @PutMapping("/models/{id}/activate")
    ModelReleaseResponse activateModel(@PathVariable UUID id,
            @AuthenticationPrincipal AvasPrincipal principal, HttpServletRequest servletRequest) {
        return pricing.activateModel(id, principal.userId(), activeRole(servletRequest));
    }

    @PutMapping("/models/{id}/validate")
    ModelReleaseResponse validateModel(@PathVariable UUID id,
            @AuthenticationPrincipal AvasPrincipal principal, HttpServletRequest servletRequest,
            @Valid @RequestBody ModelValidationRequest request) {
        return pricing.validateModel(id, principal.userId(), activeRole(servletRequest), request);
    }

    @GetMapping("/pricing/submissions")
    List<PriceSubmissionResponse> submissions() { return pricing.allSubmissions(); }

    @PutMapping("/pricing/submissions/{id}/decision")
    PriceSubmissionResponse decide(@PathVariable UUID id,
            @AuthenticationPrincipal AvasPrincipal principal, HttpServletRequest servletRequest,
            @Valid @RequestBody PriceSubmissionDecisionRequest request) {
        return pricing.decide(id, principal.userId(), activeRole(servletRequest), request);
    }

    private static String activeRole(HttpServletRequest request) {
        var value = request.getAttribute(ActiveRoleFilter.ACTIVE_ROLE);
        return value == null ? "ADMIN" : value.toString();
    }
}

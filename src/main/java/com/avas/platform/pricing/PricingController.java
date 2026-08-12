package com.avas.platform.pricing;

import com.avas.platform.auth.AvasPrincipal;
import com.avas.platform.security.ActiveRoleFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pricing")
public class PricingController {
    private final PricingService pricing;

    PricingController(PricingService pricing) {
        this.pricing = pricing;
    }

    @PostMapping("/submissions")
    PriceSubmissionResponse submit(@AuthenticationPrincipal AvasPrincipal principal,
            HttpServletRequest servletRequest, @Valid @RequestBody PriceSubmissionRequest request) {
        return pricing.submit(principal.userId(), principal.tenantId(), activeRole(servletRequest), request);
    }

    @GetMapping({"/submissions", "/submissions/mine"})
    List<PriceSubmissionResponse> mine(@AuthenticationPrincipal AvasPrincipal principal) {
        return pricing.mine(principal.userId());
    }

    @PostMapping("/budget-recommendations")
    BudgetRecommendationResponse recommend(@AuthenticationPrincipal AvasPrincipal principal,
            @Valid @RequestBody BudgetRecommendationRequest request) {
        return pricing.recommend(principal.userId(), principal.tenantId(), request);
    }

    @PostMapping("/budget-recommendations/{id}/feedback")
    BudgetRecommendationResponse feedback(@PathVariable UUID id,
            @AuthenticationPrincipal AvasPrincipal principal, HttpServletRequest servletRequest,
            @Valid @RequestBody BudgetFeedbackRequest request) {
        return pricing.feedback(id, principal.userId(), activeRole(servletRequest), request);
    }

    private static String activeRole(HttpServletRequest request) {
        var value = request.getAttribute(ActiveRoleFilter.ACTIVE_ROLE);
        return value == null ? "INDIVIDUAL" : value.toString();
    }
}

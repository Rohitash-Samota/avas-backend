package com.avas.platform.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlatformConfigurationResponse(
        String id,
        String defaultCurrency,
        String defaultCity,
        BigDecimal economyCostPerSqFt,
        BigDecimal standardCostPerSqFt,
        BigDecimal premiumCostPerSqFt,
        BigDecimal luxuryCostPerSqFt,
        BigDecimal defaultContingencyPercent,
        int minimumVerifiedSamples,
        int priceFreshnessDays,
        BigDecimal confidenceThreshold,
        int recommendationValidityDays,
        boolean contributionsEnabled,
        boolean aiExplanationEnabled,
        boolean learningEnabled,
        long version,
        UUID updatedBy,
        Instant updatedAt
) {
    static PlatformConfigurationResponse from(PlatformConfigurationEntity value) {
        return new PlatformConfigurationResponse(
                value.id(),
                value.defaultCurrency(),
                value.defaultCity(),
                value.economyCostPerSqFt(),
                value.standardCostPerSqFt(),
                value.premiumCostPerSqFt(),
                value.luxuryCostPerSqFt(),
                value.contingencyPercent(),
                value.minConfidenceSources(),
                value.priceFreshnessDays(),
                value.confidenceThreshold(),
                value.recommendationValidityDays(),
                value.contributionsEnabled(),
                value.aiExplanationEnabled(),
                value.learningEnabled(),
                value.configurationVersion(),
                value.updatedBy(),
                value.updatedAt());
    }
}

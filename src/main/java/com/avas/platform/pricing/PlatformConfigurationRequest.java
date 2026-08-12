package com.avas.platform.pricing;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PlatformConfigurationRequest(
        @NotBlank @Size(min = 3, max = 3) String defaultCurrency,
        @NotBlank @Size(max = 100) String defaultCity,
        @NotNull @Positive BigDecimal economyCostPerSqFt,
        @NotNull @Positive BigDecimal standardCostPerSqFt,
        @NotNull @Positive BigDecimal premiumCostPerSqFt,
        @NotNull @Positive BigDecimal luxuryCostPerSqFt,
        @NotNull @DecimalMin("0.0") @DecimalMax("50.0") @JsonAlias("contingencyPercent")
        BigDecimal defaultContingencyPercent,
        @NotNull @Positive @JsonAlias("minConfidenceSources") Integer minimumVerifiedSamples,
        @NotNull @Positive Integer priceFreshnessDays,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal confidenceThreshold,
        @NotNull @Positive Integer recommendationValidityDays,
        @NotNull Boolean contributionsEnabled,
        @NotNull Boolean aiExplanationEnabled,
        @NotNull Boolean learningEnabled
) {
}

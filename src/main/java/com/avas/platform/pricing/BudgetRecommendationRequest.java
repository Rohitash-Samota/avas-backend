package com.avas.platform.pricing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BudgetRecommendationRequest(
        @NotBlank @Size(max = 100) String city,
        @NotNull @Positive BigDecimal builtUpAreaSqFt,
        @NotNull PriceCategory category,
        @Positive BigDecimal totalBudget
) {
}

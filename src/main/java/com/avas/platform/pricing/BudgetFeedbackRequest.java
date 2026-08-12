package com.avas.platform.pricing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BudgetFeedbackRequest(
        @NotNull Boolean accepted,
        @Positive BigDecimal actualBudget,
        @Size(max = 1000) String note,
        Boolean consentToLearning
) {
}

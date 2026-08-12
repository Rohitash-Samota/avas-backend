package com.avas.platform.pricing;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ModelValidationRequest(
        @NotNull @DecimalMin("0.7") @DecimalMax("1.0") BigDecimal validationScore,
        @Size(max = 1000) String note
) {
}

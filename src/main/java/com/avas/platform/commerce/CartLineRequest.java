package com.avas.platform.commerce;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CartLineRequest(
        @NotBlank String productId,
        @Min(1) @Max(20) int quantity
) {
}

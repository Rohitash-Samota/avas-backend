package com.avas.platform.pricing;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ModelReleaseRequest(
        @NotBlank @Size(max = 120) String name,
        ModelType modelType,
        @NotBlank @Size(max = 60) String version,
        @NotBlank @Size(max = 100) String provider,
        @Size(max = 120) @JsonAlias("modelName") String runtime,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal validationScore,
        ModelStatus status,
        @Size(max = 2000) @JsonAlias("description") String notes
) {
}

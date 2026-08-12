package com.avas.platform.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ModelReleaseResponse(
        UUID id,
        String name,
        ModelType modelType,
        String version,
        String provider,
        String runtime,
        String modelName,
        BigDecimal validationScore,
        ModelStatus status,
        String notes,
        String description,
        UUID createdBy,
        Instant createdAt,
        UUID activatedBy,
        Instant activatedAt
) {
    static ModelReleaseResponse from(ModelReleaseEntity value) {
        return new ModelReleaseResponse(
                value.id(),
                value.name(),
                value.modelType(),
                value.releaseVersion(),
                value.provider(),
                value.runtime(),
                value.runtime(),
                value.validationScore(),
                value.status(),
                value.notes(),
                value.notes(),
                value.createdBy(),
                value.createdAt(),
                value.activatedBy(),
                value.activatedAt());
    }
}

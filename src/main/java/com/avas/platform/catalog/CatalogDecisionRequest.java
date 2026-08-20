package com.avas.platform.catalog;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CatalogDecisionRequest(
        @NotNull CatalogReviewStatus status,
        @Size(max = 1000) String note
) {
}

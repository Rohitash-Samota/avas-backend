package com.avas.platform.pricing;

import java.time.LocalDate;
import java.util.List;

/** Immutable request used by project costing; it contains no customer-controlled monetary values. */
public record CostRateQuery(
        String tenantId,
        String city,
        PriceCategory qualityTier,
        LocalDate asOf,
        List<CostRateRequirement> requirements
) {
    public CostRateQuery {
        if (city == null || city.isBlank()) throw new IllegalArgumentException("Pricing city is required");
        if (qualityTier == null) throw new IllegalArgumentException("Pricing quality tier is required");
        asOf = asOf == null ? LocalDate.now() : asOf;
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
    }
}

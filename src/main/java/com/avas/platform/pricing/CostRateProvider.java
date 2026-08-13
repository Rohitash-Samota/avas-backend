package com.avas.platform.pricing;

import java.math.BigDecimal;
import java.util.List;

/** Read-only module boundary from project costing into administrator-governed pricing. */
public interface CostRateProvider {
    CostRateSnapshot resolve(CostRateQuery query);

    /** Deterministic fallback used only by isolated unit tests that do not start Spring/JPA. */
    static CostRateProvider administrativeDefaults() {
        return query -> new CostRateSnapshot("INR", canonicalCity(query.city()), query.qualityTier(), query.asOf(),
                switch (query.qualityTier()) {
                    case ECONOMY -> new BigDecimal("1400.00");
                    case STANDARD -> new BigDecimal("1900.00");
                    case PREMIUM -> new BigDecimal("2600.00");
                    case LUXURY -> new BigDecimal("3600.00");
                }, "AVAS_ADMIN_BASE_PRICE", null, 0, new BigDecimal("7.50"), 30, 1, List.of());
    }

    static String canonicalCity(String value) {
        if (value == null) return "";
        var first = value.trim().split("[,;|]", 2)[0].trim();
        return first.replaceAll("\\s+", " ");
    }
}

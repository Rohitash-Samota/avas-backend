package com.avas.platform.project;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record Estimate(
        String id,
        String projectId,
        String drawingId,
        int version,
        long low,
        long recommended,
        long high,
        int builtUpArea,
        int durationMonthsLow,
        int durationMonthsHigh,
        int confidence,
        LocalDate validUntil,
        List<EstimateItem> items,
        List<String> assumptions,
        List<String> exclusions,
        Map<String, String> versions,
        boolean approved,
        Instant createdAt,
        String currency,
        long subtotal,
        long taxTotal,
        long contingency,
        String pricingSource,
        long pricingConfigurationVersion,
        int evidenceSampleCount,
        String pricingCity,
        String qualityTier
) {
    public Estimate {
        items = items == null ? List.of() : List.copyOf(items);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
        versions = versions == null ? Map.of() : Map.copyOf(versions);
        currency = currency == null || currency.isBlank() ? "INR" : currency;
        if (subtotal == 0 && recommended != 0) subtotal = recommended;
        pricingSource = pricingSource == null || pricingSource.isBlank() ? "LEGACY_ESTIMATE" : pricingSource;
    }

    /** Backward-compatible constructor for historical snapshots and focused unit tests. */
    public Estimate(String id, String projectId, String drawingId, int version, long low, long recommended,
            long high, int builtUpArea, int durationMonthsLow, int durationMonthsHigh, int confidence,
            LocalDate validUntil, List<EstimateItem> items, List<String> assumptions, List<String> exclusions,
            Map<String, String> versions, boolean approved, Instant createdAt) {
        this(id, projectId, drawingId, version, low, recommended, high, builtUpArea, durationMonthsLow,
                durationMonthsHigh, confidence, validUntil, items, assumptions, exclusions, versions, approved,
                createdAt, "INR", recommended, 0, 0, "LEGACY_ESTIMATE", 0, 0, null, null);
    }

    public Estimate withApproved(boolean value) {
        return new Estimate(id, projectId, drawingId, version, low, recommended, high, builtUpArea,
                durationMonthsLow, durationMonthsHigh, confidence, validUntil, items, assumptions, exclusions,
                versions, value, createdAt, currency, subtotal, taxTotal, contingency, pricingSource,
                pricingConfigurationVersion, evidenceSampleCount, pricingCity, qualityTier);
    }
}

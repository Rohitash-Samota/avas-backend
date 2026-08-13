package com.avas.platform.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Complete versioned pricing decision frozen into every generated planning estimate. */
public record CostRateSnapshot(
        String currency,
        String city,
        PriceCategory qualityTier,
        LocalDate asOf,
        BigDecimal baseCostPerSqFt,
        String basePriceSource,
        String baseEvidenceId,
        int baseEvidenceSampleCount,
        BigDecimal contingencyPercentage,
        int validityDays,
        long configurationVersion,
        List<CostRateEvidence> evidence
) {
    public CostRateSnapshot {
        currency = currency == null || currency.isBlank() ? "INR" : currency;
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public int totalEvidenceSampleCount() {
        return baseEvidenceSampleCount + evidence.stream().mapToInt(CostRateEvidence::sampleCount).sum();
    }
}

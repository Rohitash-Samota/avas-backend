package com.avas.platform.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Frozen public projection of the evidence used for one quantity/rate decision. */
public record CostRateEvidence(
        String requirementCode,
        String evidenceId,
        String itemName,
        PriceItemType itemType,
        String category,
        PriceCategory qualityTier,
        String city,
        String state,
        String unit,
        BigDecimal unitPrice,
        String productCode,
        String brandName,
        String specification,
        String supplierName,
        String source,
        LocalDate observedOn,
        LocalDate effectiveFrom,
        LocalDate expiresOn,
        BigDecimal taxPercentage,
        boolean materialIncluded,
        boolean labourIncluded,
        boolean transportIncluded,
        int sampleCount,
        ConfidenceLevel confidenceLevel
) {
}

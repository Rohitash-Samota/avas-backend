package com.avas.platform.project;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EstimateItem(
        String code,
        String category,
        String description,
        String unit,
        double quantity,
        BigDecimal rate,
        long amount,
        String evidenceId,
        int confidence,
        String itemType,
        String productCode,
        String brandName,
        String specification,
        String supplierName,
        String priceSource,
        LocalDate observedOn,
        LocalDate effectiveFrom,
        LocalDate expiresOn,
        BigDecimal taxPercentage,
        boolean materialIncluded,
        boolean labourIncluded,
        boolean transportIncluded,
        long lowAmount,
        long highAmount,
        int evidenceSampleCount
) {
    public EstimateItem {
        itemType = itemType == null || itemType.isBlank() ? "PACKAGE" : itemType;
        specification = specification == null || specification.isBlank() ? description : specification;
        priceSource = priceSource == null || priceSource.isBlank()
                ? (evidenceId == null || evidenceId.isBlank() ? "LEGACY_ESTIMATE" : "LEGACY_EVIDENCE")
                : priceSource;
        taxPercentage = taxPercentage == null ? BigDecimal.ZERO : taxPercentage;
        rate = rate == null ? BigDecimal.ZERO : rate.setScale(2, java.math.RoundingMode.HALF_UP);
        if (lowAmount == 0 && amount != 0) lowAmount = amount;
        if (highAmount == 0 && amount != 0) highAmount = amount;
    }

    /** Backward-compatible constructor for persisted and test estimates created before governed costing. */
    public EstimateItem(String code, String category, String description, String unit, double quantity,
            long rate, long amount, String evidenceId, int confidence) {
        this(code, category, description, unit, quantity, BigDecimal.valueOf(rate), amount, evidenceId, confidence,
                "PACKAGE", null, null, description, null,
                evidenceId == null ? "LEGACY_ESTIMATE" : "LEGACY_EVIDENCE",
                null, null, null, BigDecimal.ZERO, false, false, false, amount, amount,
                evidenceId == null ? 0 : 1);
    }
}

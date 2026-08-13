package com.avas.platform.project;

import com.avas.platform.pricing.PriceItemType;

import java.math.BigDecimal;

/** Deterministic planning quantity and its fallback share of the configured per-square-foot rate. */
public record QuantityTakeoffLine(
        String code,
        String category,
        String description,
        String unit,
        BigDecimal quantity,
        BigDecimal fallbackShare,
        PriceItemType itemType,
        String specification,
        boolean materialIncluded,
        boolean labourIncluded
) {
}

package com.avas.platform.pricing;

import java.math.BigDecimal;

/** One deterministic quantity for which the costing engine may request governed local evidence. */
public record CostRateRequirement(
        String code,
        PriceItemType itemType,
        String category,
        String unit,
        BigDecimal quantity
) {
    public CostRateRequirement {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Cost requirement code is required");
        if (itemType == null) throw new IllegalArgumentException("Cost requirement item type is required");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("Cost requirement category is required");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("Cost requirement unit is required");
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("Cost requirement quantity must be positive");
    }
}

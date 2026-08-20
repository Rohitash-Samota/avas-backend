package com.avas.platform.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A construction-material product as the platform publishes it.
 *
 * <p>The commerce {@code Product} record describes something AVAS itself sells and can therefore
 * be priced and checked out. This one describes something a third party sells, which is why it
 * carries provenance, a review status and a nullable price where the commerce record carries a
 * definite {@code unitPrice}. Keeping the two apart is what stops a scraped brick from appearing
 * in a checkout basket.</p>
 */
public record MaterialProduct(
        UUID id,
        String name,
        ConstructionCategory category,
        String categoryLabel,
        String subcategory,
        String sourceCategoryPath,
        String brand,
        String manufacturer,
        String modelCode,
        String sku,
        String description,
        String size,
        String materialComposition,
        Map<String, String> specifications,
        Map<String, String> attributes,
        BigDecimal price,
        BigDecimal discountPrice,
        String currency,
        String unit,
        BigDecimal minimumOrderQuantity,
        String minimumOrderUnit,
        StockStatus stockStatus,
        String sellerName,
        String sellerLocation,
        String sellerCity,
        String sellerState,
        BigDecimal rating,
        Integer reviewCount,
        List<String> imageUrls,
        String sourceSite,
        String productUrl,
        String sourceProductId,
        String collectorRunId,
        CatalogReviewStatus reviewStatus,
        Instant firstSeenAt,
        Instant lastSeenAt,
        int observationCount
) {
}

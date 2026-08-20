package com.avas.platform.catalog;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One collected product, as the collector read it.
 *
 * <p>Only the four fields a product cannot be identified without are required. Everything else is
 * optional because most listings publish only a fraction of it, and a collector that had to invent
 * a brand or a unit to get a row accepted would be filing worse data than one that admits the field
 * was absent.</p>
 *
 * <p>{@code price} is nullable for the same reason: a great many Indian construction listings say
 * "Ask Price", and a catalogue that only admits priced products would omit most of the market.</p>
 */
public record MaterialProductRequest(
        @NotBlank @Size(max = 64) String fingerprint,
        @NotBlank @Size(max = 60) String sourceSite,
        @NotBlank @Size(max = 1000) String productUrl,
        @NotBlank @Size(max = 300) String name,

        /** A value from {@link ConstructionCategory}. Derived server-side when absent or unknown. */
        @Size(max = 40) String category,
        @Size(max = 160) String subcategory,
        @Size(max = 500) String sourceCategoryPath,

        @Size(max = 120) String sourceProductId,
        @Size(max = 160) String brand,
        @Size(max = 200) String manufacturer,
        @Size(max = 120) String modelCode,
        @Size(max = 120) String sku,
        String description,

        @Size(max = 200) String size,
        @Size(max = 200) String materialComposition,
        Map<String, String> specifications,
        Map<String, String> attributes,

        @PositiveOrZero BigDecimal price,
        @PositiveOrZero BigDecimal discountPrice,
        @Size(max = 3) String currency,
        @Size(max = 40) String unit,
        @PositiveOrZero BigDecimal minimumOrderQuantity,
        @Size(max = 40) String minimumOrderUnit,
        @Size(max = 60) String availability,

        @Size(max = 200) String sellerName,
        @Size(max = 200) String sellerLocation,
        @Size(max = 120) String sellerCity,
        @Size(max = 120) String sellerState,

        @DecimalMin("0.0") @DecimalMax("5.0") BigDecimal rating,
        @PositiveOrZero Integer reviewCount,
        List<String> imageUrls,

        /** The collector's verbatim reading of the page, kept so normalisation can be replayed. */
        Map<String, Object> raw
) {
}

package com.avas.platform.pricing;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceSubmissionRequest(
        @NotBlank @Size(max = 160) String itemName,
        PriceItemType itemType,
        @NotBlank @Size(max = 40) String category,
        PriceCategory qualityTier,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100) String state,
        @NotBlank @Size(max = 40) String unit,
        @NotNull @Positive @JsonAlias({"price", "amount"}) BigDecimal unitPrice,
        @Positive BigDecimal quantity,
        LocalDate observedOn,
        LocalDate effectiveFrom,
        LocalDate expiresOn,
        @Size(max = 160) String supplierName,
        @Size(max = 160) String source,
        @Size(max = 1000) String notes,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal taxPercentage,
        Boolean materialIncluded,
        Boolean labourIncluded,
        Boolean transportIncluded,
        @Size(max = 120) String brandName,
        @Size(max = 100) String productCode,
        @Size(max = 1000) String specification
) {
}

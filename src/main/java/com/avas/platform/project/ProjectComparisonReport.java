package com.avas.platform.project;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Read-only, server-owned comparison contract shared by the project screen and the downloadable report.
 * Monetary values are minor currency units (whole INR today), never formatted display strings.
 */
public record ProjectComparisonReport(
        String projectId,
        String projectCode,
        String projectName,
        String city,
        int projectSnapshotVersion,
        int comparisonVersion,
        long projectBudget,
        String selectedOptionId,
        String bestOptionId,
        String reportOptionId,
        String recommendationBasis,
        List<Option> options
) {
    public ProjectComparisonReport {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public record Option(
            String drawingId,
            int drawingVersion,
            String name,
            String strategy,
            String status,
            boolean selected,
            boolean bestOption,
            boolean eligible,
            int rank,
            int weightedScore,
            int builtUpArea,
            int floorCount,
            int bedroomCount,
            int bathroomCount,
            long costLow,
            long recommendedCost,
            long costHigh,
            String currency,
            String budgetFit,
            String costBasis,
            int vastuScore,
            int naturalLightScore,
            int spaceEfficiencyScore,
            int confidence,
            int hardViolationCount,
            List<String> highlights,
            EstimateBreakdown estimate,
            Map<String, String> provenance
    ) {
        public Option {
            highlights = highlights == null ? List.of() : List.copyOf(highlights);
            provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
        }
    }

    public record EstimateBreakdown(
            boolean available,
            String estimateId,
            Integer estimateVersion,
            boolean approved,
            int confidence,
            LocalDate validUntil,
            String currency,
            long low,
            long subtotal,
            long taxTotal,
            long contingency,
            long recommended,
            long high,
            String pricingSource,
            long pricingConfigurationVersion,
            int evidenceSampleCount,
            String pricingCity,
            String qualityTier,
            List<CostLine> items,
            List<String> assumptions,
            List<String> exclusions,
            Map<String, String> versions
    ) {
        public EstimateBreakdown {
            items = items == null ? List.of() : List.copyOf(items);
            assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
            exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
            versions = versions == null ? Map.of() : Map.copyOf(versions);
        }
    }

    public record CostLine(
            String code,
            String category,
            String itemType,
            String productCode,
            String brandName,
            String description,
            String specification,
            String supplierName,
            String unit,
            double quantity,
            BigDecimal rate,
            long amount,
            long lowAmount,
            long highAmount,
            BigDecimal taxPercentage,
            boolean materialIncluded,
            boolean labourIncluded,
            boolean transportIncluded,
            String evidenceId,
            String priceSource,
            LocalDate observedOn,
            LocalDate effectiveFrom,
            LocalDate expiresOn,
            int evidenceSampleCount,
            int confidence
    ) {}
}

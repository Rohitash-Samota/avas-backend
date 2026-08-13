package com.avas.platform.project;

import com.avas.platform.pricing.ConfidenceLevel;
import com.avas.platform.pricing.CostRateEvidence;
import com.avas.platform.pricing.CostRateProvider;
import com.avas.platform.pricing.CostRateQuery;
import com.avas.platform.pricing.CostRateRequirement;
import com.avas.platform.pricing.PriceCategory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Deterministic monetary authority that freezes takeoff, governed rates and every adjustment. */
@Service
public class CostingService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private final QuantityTakeoffService takeoff;
    private final CostRateProvider rates;

    public CostingService(QuantityTakeoffService takeoff, CostRateProvider rates) {
        this.takeoff = takeoff;
        this.rates = rates;
    }

    static CostingService administrativeDefaults() {
        return new CostingService(new QuantityTakeoffService(), CostRateProvider.administrativeDefaults());
    }

    public Estimate cost(String estimateId, int version, String tenantId, ProjectSummary project,
            DrawingCandidate drawing, PriceCategory qualityTier, Map<String, String> applicationVersions) {
        var quantities = takeoff.takeoff(project, drawing);
        var query = new CostRateQuery(tenantId, project.details().city(), qualityTier, LocalDate.now(),
                quantities.stream().map(line -> new CostRateRequirement(line.code(), line.itemType(),
                        line.category(), line.unit(), line.quantity())).toList());
        var snapshot = rates.resolve(query);
        var evidenceByCode = snapshot.evidence().stream().collect(Collectors.toMap(
                CostRateEvidence::requirementCode, Function.identity(), (left, right) -> left,
                LinkedHashMap::new));
        var configuredBase = snapshot.baseCostPerSqFt().multiply(BigDecimal.valueOf(drawing.builtUpArea()));
        var items = new ArrayList<EstimateItem>();
        var coreUsesOnlyFallbackRates = evidenceByCode.keySet().stream().allMatch(this::isLiftLine);
        var configuredBaseAmount = money(configuredBase);
        long allocatedCoreAmount = 0;

        for (var line : quantities) {
            var evidence = evidenceByCode.get(line.code());
            var amount = evidence == null
                    ? coreUsesOnlyFallbackRates && "PRELIMINARIES".equals(line.code())
                            ? configuredBaseAmount - allocatedCoreAmount
                            : money(configuredBase.multiply(line.fallbackShare()))
                    : money(evidence.unitPrice().multiply(line.quantity()));
            if (!isLiftLine(line.code())) allocatedCoreAmount += amount;
            var rate = evidence == null
                    ? unitRate(BigDecimal.valueOf(amount).divide(line.quantity(), 8, RoundingMode.HALF_UP))
                    : unitRate(evidence.unitPrice());
            var bounds = bounds(amount);
            items.add(new EstimateItem(line.code(), title(line.category()), line.description(), line.unit(),
                    line.quantity().doubleValue(), rate, amount,
                    evidence == null ? snapshot.baseEvidenceId() : evidence.evidenceId(),
                    evidence == null ? baseConfidence(snapshot.baseEvidenceSampleCount())
                            : confidence(evidence.confidenceLevel()),
                    line.itemType().name(), evidence == null ? null : evidence.productCode(),
                    evidence == null ? null : evidence.brandName(),
                    evidence == null ? line.specification() : firstNonBlank(evidence.specification(), line.specification()),
                    evidence == null ? null : evidence.supplierName(),
                    evidence == null ? snapshot.basePriceSource() + "_ALLOCATED" : "APPROVED_LOCAL_EVIDENCE",
                    evidence == null ? null : evidence.observedOn(), evidence == null ? null : evidence.effectiveFrom(),
                    evidence == null ? null : evidence.expiresOn(),
                    evidence == null ? BigDecimal.ZERO : evidence.taxPercentage(),
                    evidence == null ? line.materialIncluded() : evidence.materialIncluded(),
                    evidence == null ? line.labourIncluded() : evidence.labourIncluded(),
                    evidence != null && evidence.transportIncluded(), bounds.low(), bounds.high(),
                    evidence == null ? snapshot.baseEvidenceSampleCount() : evidence.sampleCount()));
        }
        if (coreUsesOnlyFallbackRates && allocatedCoreAmount != configuredBaseAmount) {
            throw new IllegalStateException("Fallback takeoff does not reconcile to the configured base amount");
        }

        var beforeStrategy = items.stream().mapToLong(EstimateItem::amount).sum();
        var strategyPercentage = strategyPercentage(drawing.strategy());
        var strategyAmount = money(BigDecimal.valueOf(beforeStrategy).multiply(strategyPercentage)
                .divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
        var strategyBounds = bounds(strategyAmount);
        items.add(new EstimateItem("STRATEGY_ADJUSTMENT", "Strategy adjustment",
                strategyDescription(drawing.strategy(), strategyPercentage), "LS", 1,
                BigDecimal.valueOf(strategyAmount), strategyAmount,
                null, 100, "ADJUSTMENT", null, null,
                "Transparent layout-complexity adjustment applied after the geometry takeoff", null,
                "AVAS_STRATEGY_POLICY", null, snapshot.asOf(), null, BigDecimal.ZERO,
                false, false, false, strategyBounds.low(), strategyBounds.high(), 0));

        var subtotal = items.stream().mapToLong(EstimateItem::amount).sum();
        var taxTotal = items.stream().mapToLong(item -> money(BigDecimal.valueOf(item.amount())
                .multiply(item.taxPercentage()).divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP))).sum();
        if (taxTotal != 0) items.add(adjustment("TAX", "Tax", "Tax from the frozen evidence lines",
                taxTotal, "GOVERNED_EVIDENCE_TAX", snapshot.asOf()));
        var contingency = money(BigDecimal.valueOf(subtotal + taxTotal).multiply(snapshot.contingencyPercentage())
                .divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
        if (contingency != 0) items.add(adjustment("CONTINGENCY", "Contingency",
                snapshot.contingencyPercentage().stripTrailingZeros().toPlainString()
                        + "% administrator-configured planning contingency",
                contingency, "AVAS_ADMIN_CONFIGURATION", snapshot.asOf()));

        var recommended = items.stream().mapToLong(EstimateItem::amount).sum();
        var low = items.stream().mapToLong(EstimateItem::lowAmount).sum();
        var high = items.stream().mapToLong(EstimateItem::highAmount).sum();
        var evidenceSamples = snapshot.totalEvidenceSampleCount();
        var pricingSource = pricingSource(snapshot.basePriceSource(), evidenceByCode.isEmpty());
        var versions = new LinkedHashMap<>(applicationVersions == null ? Map.<String, String>of() : applicationVersions);
        versions.put("costingPolicy", "geometry-takeoff-2.0");
        versions.put("pricingConfigurationVersion", String.valueOf(snapshot.configurationVersion()));
        versions.put("pricingSource", pricingSource);
        versions.put("pricingCity", snapshot.city());

        var assumptions = new ArrayList<String>();
        assumptions.add("Quantities are deterministic planning takeoffs from saved drawing geometry; final schedules require professional measurement.");
        assumptions.add("Approved, current " + snapshot.city() + " evidence replaces an allowance only when item type, category, unit and quality tier match.");
        assumptions.add("Unmatched lines allocate the governed " + snapshot.basePriceSource() + " rate of INR "
                + snapshot.baseCostPerSqFt().stripTrailingZeros().toPlainString() + " per sq ft.");
        assumptions.add(strategyDescription(drawing.strategy(), strategyPercentage));
        var liftProvision = project.details().parameters().liftProvision();
        if ("FUTURE_SHAFT".equals(liftProvision)) {
            assumptions.add("Future lift shaft provision excludes all passenger lift equipment and installation.");
        } else if ("PASSENGER".equals(liftProvision)) {
            assumptions.add("Passenger lift equipment and structural shaft provision are separately itemised.");
        }

        var estimate = new Estimate(estimateId, project.id(), drawing.id(), version, low, recommended, high,
                drawing.builtUpArea(), 11, 13, overallConfidence(evidenceSamples),
                snapshot.asOf().plusDays(snapshot.validityDays()), List.copyOf(items), List.copyOf(assumptions),
                List.of("Land cost", "Finance and registration costs", "Authority fees not supported by approved evidence",
                        "Loose furniture and appliances"), Map.copyOf(versions), false, Instant.now(),
                snapshot.currency(), subtotal, taxTotal, contingency, pricingSource,
                snapshot.configurationVersion(), evidenceSamples, snapshot.city(), qualityTier.name());
        assertReconciled(estimate);
        return estimate;
    }

    private EstimateItem adjustment(String code, String category, String description, long amount, String source,
            LocalDate effectiveFrom) {
        var bounds = bounds(amount);
        return new EstimateItem(code, category, description, "LS", 1, BigDecimal.valueOf(amount), amount, null, 100,
                "ADJUSTMENT", null, null, description, null, source, null, effectiveFrom, null,
                BigDecimal.ZERO, false, false, false, bounds.low(), bounds.high(), 0);
    }

    private boolean isLiftLine(String code) {
        return "LIFT_SHAFT_PROVISION".equals(code) || "PASSENGER_LIFT".equals(code);
    }

    private void assertReconciled(Estimate estimate) {
        var itemTotal = estimate.items().stream().mapToLong(EstimateItem::amount).sum();
        if (itemTotal != estimate.recommended()
                || estimate.subtotal() + estimate.taxTotal() + estimate.contingency() != estimate.recommended()) {
            throw new IllegalStateException("Cost breakdown does not reconcile to the recommended total");
        }
        if (estimate.low() > estimate.recommended() || estimate.high() < estimate.recommended()) {
            throw new IllegalStateException("Cost uncertainty range does not contain the recommended total");
        }
    }

    private long money(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private BigDecimal unitRate(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private Bounds bounds(long amount) {
        var first = money(BigDecimal.valueOf(amount).multiply(new BigDecimal("0.92")));
        var second = money(BigDecimal.valueOf(amount).multiply(new BigDecimal("1.12")));
        return new Bounds(Math.min(first, second), Math.max(first, second));
    }

    private int confidence(ConfidenceLevel level) {
        return switch (level) { case LOW -> 35; case MEDIUM -> 65; case HIGH -> 90; };
    }

    private int baseConfidence(int samples) {
        return samples >= 4 ? 90 : samples >= 2 ? 65 : samples == 1 ? 35 : 70;
    }

    private int overallConfidence(int samples) {
        return samples >= 4 ? 90 : samples >= 2 ? 70 : samples == 1 ? 55 : 70;
    }

    private BigDecimal strategyPercentage(String strategy) {
        return switch (strategy == null ? "" : strategy) {
            case "BUDGET_OPTIMIZED" -> new BigDecimal("-3.00");
            case "LIFESTYLE_OPTIMIZED" -> new BigDecimal("5.00");
            default -> BigDecimal.ZERO;
        };
    }

    private String strategyDescription(String strategy, BigDecimal percentage) {
        var signed = percentage.signum() > 0 ? "+" + percentage.stripTrailingZeros().toPlainString()
                : percentage.stripTrailingZeros().toPlainString();
        return switch (strategy == null ? "" : strategy) {
            case "BUDGET_OPTIMIZED" -> signed + "% compact-grid efficiency credit for the budget-optimised option";
            case "LIFESTYLE_OPTIMIZED" -> signed + "% layout-complexity allowance for the lifestyle-optimised option";
            default -> "0% strategy adjustment for the balanced option";
        };
    }

    private String pricingSource(String baseSource, boolean noItemEvidence) {
        if (noItemEvidence) return baseSource;
        return "APPROVED_LOCAL_EVIDENCE".equals(baseSource)
                ? "APPROVED_LOCAL_EVIDENCE" : "MIXED_APPROVED_AND_ADMIN";
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String title(String value) {
        if (value == null || value.isBlank()) return "Other";
        var words = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ').split("\\s+");
        var result = new StringBuilder();
        for (var word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private record Bounds(long low, long high) {}
}

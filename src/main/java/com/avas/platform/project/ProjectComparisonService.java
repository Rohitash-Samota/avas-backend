package com.avas.platform.project;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Assembles a frozen-view comparison from already persisted project artifacts. */
@Service
public class ProjectComparisonService {
    private final ProjectService projects;

    public ProjectComparisonService(ProjectService projects) {
        this.projects = projects;
    }

    public ProjectComparisonReport comparison(String projectId) {
        return comparison(projectId, null);
    }

    public ProjectComparisonReport comparison(String projectId, String requestedDrawingId) {
        return comparison(projectId, requestedDrawingId, null);
    }

    public ProjectComparisonReport comparisonForEstimate(String estimateId) {
        var estimate = projects.estimate(estimateId);
        return comparison(estimate.projectId(), estimate.drawingId(), estimate);
    }

    private ProjectComparisonReport comparison(String projectId, String requestedDrawingId,
            Estimate pinnedEstimate) {
        var project = projects.get(projectId);
        var drawings = projects.drawings(projectId);
        if (drawings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Generate drawing options before creating a comparison report");
        }

        var requested = clean(requestedDrawingId) == null ? null : drawings.stream()
                .filter(drawing -> drawing.id().equals(requestedDrawingId.trim()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Drawing option was not found in this project"));
        var selected = drawings.stream().filter(DrawingCandidate::conceptApproved)
                .max(Comparator.comparingInt(DrawingCandidate::version)).orElse(null);
        var anchor = requested != null ? requested : selected;
        var comparisonVersion = anchor != null ? anchor.version()
                : drawings.stream().mapToInt(DrawingCandidate::version).max().orElseThrow();
        var candidates = drawings.stream().filter(drawing -> drawing.version() == comparisonVersion)
                .sorted(Comparator.comparingInt(this::strategyOrder).thenComparing(DrawingCandidate::id))
                .toList();
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The requested drawing version has no comparable options");
        }

        var estimates = new HashMap<>(latestEstimatesByDrawing(projects.estimates(projectId)));
        if (pinnedEstimate != null) estimates.put(pinnedEstimate.drawingId(), pinnedEstimate);
        var provisional = candidates.stream()
                .map(drawing -> option(project, drawing, estimates.get(drawing.id())))
                .toList();
        var ranked = provisional.stream()
                .sorted(Comparator.comparing(ProjectComparisonReport.Option::eligible).reversed()
                        .thenComparing(ProjectComparisonReport.Option::weightedScore, Comparator.reverseOrder())
                        .thenComparing(ProjectComparisonReport.Option::drawingId))
                .toList();
        var ranks = new HashMap<String, Integer>();
        for (var index = 0; index < ranked.size(); index++) ranks.put(ranked.get(index).drawingId(), index + 1);
        var bestId = ranked.getFirst().drawingId();
        var selectedId = selected != null && selected.version() == comparisonVersion ? selected.id() : null;
        var reportId = requested != null ? requested.id() : selectedId != null ? selectedId : bestId;
        var options = provisional.stream().map(option -> copyWithDecision(option,
                ranks.get(option.drawingId()), option.drawingId().equals(bestId))).toList();
        var allNeedReview = options.stream().noneMatch(ProjectComparisonReport.Option::eligible);
        var basis = allNeedReview
                ? "Highest-scoring available option; every option still requires professional constraint review."
                : "Highest eligible weighted score: 40% hard-rule compliance, 25% budget fit, "
                        + "20% space efficiency and 15% natural light.";
        var details = project.details();
        return new ProjectComparisonReport(project.id(), project.projectCode(), project.name(),
                details == null ? null : details.city(), project.currentSnapshotVersion(), comparisonVersion,
                details == null ? 0 : details.budget(), selectedId, bestId, reportId, basis, options);
    }

    private ProjectComparisonReport.Option option(ProjectSummary project, DrawingCandidate drawing,
            Estimate estimate) {
        var details = project.details();
        var low = estimate == null ? drawing.estimatedCostLow() : estimate.low();
        var recommended = estimate == null ? Math.round((low + drawing.estimatedCostHigh()) / 2.0)
                : estimate.recommended();
        var high = estimate == null ? drawing.estimatedCostHigh() : estimate.high();
        var budget = details == null ? 0 : details.budget();
        var fit = budgetFit(budget, low, recommended);
        var hardViolations = drawing.hardViolations() == null ? List.<String>of() : drawing.hardViolations();
        var eligible = hardViolations.isEmpty() && !"FAILED".equalsIgnoreCase(drawing.status());
        var score = weightedScore(eligible, fit, budget, low, drawing);
        var rooms = drawing.geometry() == null || drawing.geometry().rooms() == null
                ? List.<RoomGeometry>of() : drawing.geometry().rooms();
        var floors = (int) rooms.stream().map(room -> normalizedFloor(room.floor())).distinct().count();
        var bedrooms = (int) rooms.stream().filter(room -> room.type().contains("BEDROOM")).count();
        var bathrooms = (int) rooms.stream()
                .filter(room -> room.type().contains("BATH") || room.type().contains("TOILET")).count();
        var highlights = drawing.explanations() == null ? List.<String>of()
                : drawing.explanations().stream().limit(4).toList();
        var breakdown = estimate == null ? unavailableBreakdown(low, recommended, high)
                : breakdown(estimate);
        return new ProjectComparisonReport.Option(drawing.id(), drawing.version(), drawing.name(),
                drawing.strategy(), drawing.status(), drawing.conceptApproved(), false, eligible, 0, score,
                drawing.builtUpArea(), Math.max(1, floors), bedrooms, bathrooms, low, recommended, high,
                estimate == null ? "INR" : estimate.currency(), fit,
                estimate == null ? "DRAWING_PLANNING_RANGE" : estimate.pricingSource(), drawing.vastuScore(),
                drawing.naturalLightScore(), drawing.spaceEfficiencyScore(), drawing.confidence(),
                hardViolations.size(), highlights, breakdown,
                drawing.versions() == null ? Map.of() : drawing.versions());
    }

    private ProjectComparisonReport.Option copyWithDecision(ProjectComparisonReport.Option source,
            int rank, boolean best) {
        return new ProjectComparisonReport.Option(source.drawingId(), source.drawingVersion(), source.name(),
                source.strategy(), source.status(), source.selected(), best, source.eligible(), rank,
                source.weightedScore(), source.builtUpArea(), source.floorCount(), source.bedroomCount(),
                source.bathroomCount(), source.costLow(), source.recommendedCost(), source.costHigh(),
                source.currency(), source.budgetFit(), source.costBasis(), source.vastuScore(),
                source.naturalLightScore(), source.spaceEfficiencyScore(), source.confidence(),
                source.hardViolationCount(), source.highlights(), source.estimate(), source.provenance());
    }

    private ProjectComparisonReport.EstimateBreakdown breakdown(Estimate estimate) {
        var lines = estimate.items().stream().map(this::line).toList();
        return new ProjectComparisonReport.EstimateBreakdown(true, estimate.id(), estimate.version(),
                estimate.approved(), estimate.confidence(), estimate.validUntil(), estimate.currency(),
                estimate.low(), estimate.subtotal(), estimate.taxTotal(), estimate.contingency(),
                estimate.recommended(), estimate.high(), estimate.pricingSource(),
                estimate.pricingConfigurationVersion(), estimate.evidenceSampleCount(), estimate.pricingCity(),
                estimate.qualityTier(), lines, estimate.assumptions(), estimate.exclusions(), estimate.versions());
    }

    private ProjectComparisonReport.EstimateBreakdown unavailableBreakdown(long low, long recommended, long high) {
        return new ProjectComparisonReport.EstimateBreakdown(false, null, null, false, 0, null, "INR",
                low, recommended, 0, 0, recommended, high, "DRAWING_PLANNING_RANGE", 0, 0,
                null, null, List.of(), List.of("Generate a governed estimate to see material and brand detail."),
                List.of(), Map.of());
    }

    private ProjectComparisonReport.CostLine line(EstimateItem item) {
        return new ProjectComparisonReport.CostLine(item.code(), item.category(), item.itemType(),
                item.productCode(), item.brandName(), item.description(), item.specification(),
                item.supplierName(), item.unit(), item.quantity(), item.rate(), item.amount(), item.lowAmount(),
                item.highAmount(), item.taxPercentage(), item.materialIncluded(), item.labourIncluded(),
                item.transportIncluded(), item.evidenceId(), item.priceSource(), item.observedOn(),
                item.effectiveFrom(), item.expiresOn(), item.evidenceSampleCount(), item.confidence());
    }

    private Map<String, Estimate> latestEstimatesByDrawing(List<Estimate> estimates) {
        var result = new HashMap<String, Estimate>();
        for (var estimate : estimates) {
            var existing = result.get(estimate.drawingId());
            if (existing == null || newer(estimate, existing)) result.put(estimate.drawingId(), estimate);
        }
        return Map.copyOf(result);
    }

    private boolean newer(Estimate candidate, Estimate existing) {
        if (candidate.version() != existing.version()) return candidate.version() > existing.version();
        var candidateCreated = candidate.createdAt() == null ? Instant.EPOCH : candidate.createdAt();
        var existingCreated = existing.createdAt() == null ? Instant.EPOCH : existing.createdAt();
        return candidateCreated.isAfter(existingCreated);
    }

    private int weightedScore(boolean eligible, String fit, long budget, long low, DrawingCandidate drawing) {
        var budgetScore = switch (fit) {
            case "WITHIN_BUDGET" -> 100d;
            case "STRETCHED" -> 70d;
            case "ABOVE_BUDGET" -> low <= 0 ? 0d : Math.max(0, Math.min(60, budget * 60d / low));
            default -> 50d;
        };
        var ruleScore = eligible ? 100d : 0d;
        return (int) Math.round(ruleScore * .40 + budgetScore * .25
                + drawing.spaceEfficiencyScore() * .20 + drawing.naturalLightScore() * .15);
    }

    private String budgetFit(long budget, long low, long recommended) {
        if (budget <= 0) return "NOT_PROVIDED";
        if (recommended <= budget) return "WITHIN_BUDGET";
        if (low <= budget) return "STRETCHED";
        return "ABOVE_BUDGET";
    }

    private int strategyOrder(DrawingCandidate drawing) {
        return switch (drawing.strategy() == null ? "" : drawing.strategy().toUpperCase(Locale.ROOT)) {
            case "BUDGET_OPTIMIZED" -> 0;
            case "BALANCED" -> 1;
            case "LIFESTYLE_OPTIMIZED" -> 2;
            default -> 10;
        };
    }

    private String normalizedFloor(String floor) {
        return floor == null || floor.isBlank() ? "GROUND" : floor.trim().toUpperCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.avas.platform.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectComparisonServiceTest {
    private ProjectService projects;
    private ProjectComparisonService comparisons;

    @BeforeEach
    void setUp() {
        projects = new ProjectService(new GeometryEngine(), "RJ-JDA-2026.08", "AVAS-KB-2026.08",
                "layout-heuristic-1.5.0", "planning-estimate-2.0.0");
        comparisons = new ProjectComparisonService(projects);
    }

    @Test
    void comparesOneGenerationAndJoinsEachDrawingToItsFrozenCostSnapshot() {
        var project = preparedProject();
        var drawings = projects.drawings(project.id());
        var selected = projects.approveConcept(drawings.get(1).id(), "INDIVIDUAL");

        var report = comparisons.comparison(project.id());

        assertThat(report.projectId()).isEqualTo(project.id());
        assertThat(report.options()).hasSize(3);
        assertThat(report.comparisonVersion()).isEqualTo(selected.version());
        assertThat(report.selectedOptionId()).isEqualTo(selected.id());
        assertThat(report.reportOptionId()).isEqualTo(selected.id());
        assertThat(report.bestOptionId()).isIn(drawings.stream().map(DrawingCandidate::id).toList());
        assertThat(report.options()).extracting(ProjectComparisonReport.Option::rank)
                .containsExactlyInAnyOrder(1, 2, 3);
        assertThat(report.options()).extracting(ProjectComparisonReport.Option::strategy)
                .containsExactly("BUDGET_OPTIMIZED", "BALANCED", "LIFESTYLE_OPTIMIZED");
        assertThat(report.options()).allSatisfy(option -> {
            assertThat(option.estimate().available()).isTrue();
            assertThat(option.estimate().items()).isNotEmpty();
            assertThat(option.estimate().recommended())
                    .isEqualTo(option.estimate().items().stream()
                            .mapToLong(ProjectComparisonReport.CostLine::amount).sum());
            assertThat(option.costLow()).isLessThanOrEqualTo(option.recommendedCost());
            assertThat(option.costHigh()).isGreaterThanOrEqualTo(option.recommendedCost());
            assertThat(option.provenance()).containsKeys("generator", "strategyId");
        });
    }

    @Test
    void aRequestedHistoricalDrawingPinsTheComparisonVersionAndReportOption() {
        var project = preparedProject();
        var historical = projects.drawings(project.id()).getFirst();
        projects.generateDrawings(project.id(), "INDIVIDUAL");

        var report = comparisons.comparison(project.id(), historical.id());

        assertThat(report.comparisonVersion()).isEqualTo(historical.version());
        assertThat(report.reportOptionId()).isEqualTo(historical.id());
        assertThat(report.options()).hasSize(3)
                .allMatch(option -> option.drawingVersion() == historical.version());
    }

    @Test
    void estimateSpecificComparisonKeepsTheRequestedEstimateAndDrawingTogether() {
        var project = preparedProject();
        var estimate = projects.estimates(project.id()).get(2);

        var report = comparisons.comparisonForEstimate(estimate.id());
        var option = report.options().stream()
                .filter(value -> value.drawingId().equals(estimate.drawingId())).findFirst().orElseThrow();

        assertThat(report.reportOptionId()).isEqualTo(estimate.drawingId());
        assertThat(option.estimate().estimateId()).isEqualTo(estimate.id());
        assertThat(option.estimate().pricingSource()).isEqualTo(estimate.pricingSource());
    }

    private ProjectSummary preparedProject() {
        var project = projects.create(new CreateProjectRequest("Family duplex", StartMode.PLOT), "INDIVIDUAL");
        projects.updateBasicDetails(project.id(), details(), "INDIVIDUAL");
        var recommendation = projects.generateRecommendation(project.id(), "INDIVIDUAL");
        projects.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");
        projects.generateDrawings(project.id(), "INDIVIDUAL");
        return projects.get(project.id());
    }

    private BasicDetailsRequest details() {
        return new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur, Rajasthan", 2, 7_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 1, true),
                List.of("Vastu-friendly", "Family lounge", "Future expansion"));
    }
}

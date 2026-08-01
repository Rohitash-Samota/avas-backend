package com.avas.platform.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.avas.platform.project.ProjectModels.*;
import static org.assertj.core.api.Assertions.assertThat;

class ProjectServiceTest {
    private ProjectService service;

    @BeforeEach
    void setUp() {
        var geometry = new GeometryEngine();
        service = new ProjectService(geometry, "RJ-JDA-2026.08", "AVAS-KB-2026.08", "layout-heuristic-1.4.2", "planning-estimate-1.2.0");
    }

    @Test
    void recommendsSeniorFriendlyFourBedroomPremiumHome() {
        var project = service.create(new CreateProjectRequest("Family home", StartMode.PLOT), "INDIVIDUAL");
        service.updateBasicDetails(project.id(), details(), "INDIVIDUAL");
        var recommendation = service.generateRecommendation(project.id(), "INDIVIDUAL");

        assertThat(recommendation.bedrooms()).isEqualTo(4);
        assertThat(recommendation.category()).isEqualTo("PREMIUM");
        assertThat(recommendation.seniorCitizenBedroom()).isTrue();
        assertThat(recommendation.confidence()).isEqualTo(92);
        assertThat(recommendation.provenance()).containsEntry("rule", "RJ-JDA-2026.08");
    }

    @Test
    void producesThreeVersionedValidatedCandidatesAndEstimate() {
        var project = service.create(new CreateProjectRequest("Family home", StartMode.PLOT), "INDIVIDUAL");
        service.updateBasicDetails(project.id(), details(), "INDIVIDUAL");
        var recommendation = service.generateRecommendation(project.id(), "INDIVIDUAL");
        var snapshot = service.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");
        var job = service.generateDrawings(project.id(), "INDIVIDUAL");
        var drawings = service.drawings(project.id());

        assertThat(snapshot.snapshotId()).startsWith("req-");
        assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(drawings).hasSize(3).extracting(DrawingCandidate::strategy)
                .containsExactly("BUDGET_OPTIMIZED", "BALANCED", "LIFESTYLE_OPTIMIZED");
        assertThat(drawings).allSatisfy(drawing -> assertThat(drawing.hardViolations()).isEmpty());

        var estimate = service.generateEstimate(project.id(), drawings.get(1).id(), "INDIVIDUAL");
        assertThat(estimate.items()).hasSize(7);
        assertThat(estimate.low()).isLessThan(estimate.high());
        assertThat(service.validation(drawings.get(1).id()).status()).isEqualTo(EngineStatus.SUCCESS);
    }

    private BasicDetailsRequest details() {
        return new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur, Rajasthan", 2, 7_000_000, Category.NOT_SURE, new FamilyDetails(2, 2, 1, true), List.of("Vastu-friendly", "Family lounge", "Future expansion"));
    }
}

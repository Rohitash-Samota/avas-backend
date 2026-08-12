package com.avas.platform.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectServiceTest {
    private ProjectService service;

    @BeforeEach
    void setUp() {
        var geometry = new GeometryEngine();
        service = new ProjectService(geometry, "RJ-JDA-2026.08", "AVAS-KB-2026.08", "layout-heuristic-1.5.0", "planning-estimate-1.2.0");
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
        assertThat(drawings).allSatisfy(drawing -> {
            assertThat(drawing.hardViolations()).isNotEmpty()
                    .allMatch(violation -> violation.startsWith("Programme gap:"));
            assertThat(drawing.explanations()).anyMatch(explanation -> explanation.contains("Professional review required"));
        });

        var estimate = service.generateEstimate(project.id(), drawings.get(1).id(), "INDIVIDUAL");
        assertThat(estimate.items()).hasSize(7);
        assertThat(estimate.low()).isLessThan(estimate.high());
        var validation = service.validation(drawings.get(1).id());
        assertThat(validation.status()).isEqualTo(EngineStatus.EXPERT_REVIEW);
        var buildingRules = validation.gates().stream()
                .filter(gate -> gate.name().equals("Building rules")).findFirst().orElseThrow();
        assertThat(buildingRules.status()).isEqualTo("REVIEW_REQUIRED");
        assertThat(buildingRules.detail()).contains("unresolved hard/programme constraint")
                .contains("Programme gap:")
                .doesNotContain("No unresolved hard constraints");
        var estimateEvidence = validation.gates().stream()
                .filter(gate -> gate.name().equals("Estimate evidence")).findFirst().orElseThrow();
        assertThat(estimateEvidence.status()).isEqualTo("REVIEW_REQUIRED");
        assertThat(estimateEvidence.detail()).contains("outside the stored recommendation cost basis")
                .contains("recalibrated");
        assertThat(validation.professionalReview()).containsAll(drawings.get(1).hardViolations());
        assertThat(drawings.get(1).hardViolations())
                .contains("Programme gap: placed built-up area " + drawings.get(1).builtUpArea()
                        + " sq ft is outside recommended " + recommendation.builtUpAreaMinimum() + "-"
                        + recommendation.builtUpAreaMaximum() + " sq ft cost basis");
        assertThat(service.get(project.id()).status()).isEqualTo("REVIEW_REQUIRED");
    }

    @Test
    void keepsExactlyOneSelectedConceptWhenCustomerChangesSelection() {
        var project = service.create(new CreateProjectRequest("Family home", StartMode.PLOT), "INDIVIDUAL");
        service.updateBasicDetails(project.id(), details(), "INDIVIDUAL");
        var recommendation = service.generateRecommendation(project.id(), "INDIVIDUAL");
        service.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");
        service.generateDrawings(project.id(), "INDIVIDUAL");
        var drawings = service.drawings(project.id());

        service.approveConcept(drawings.get(0).id(), "INDIVIDUAL");
        var selected = service.approveConcept(drawings.get(2).id(), "INDIVIDUAL");

        assertThat(selected.conceptApproved()).isTrue();
        assertThat(service.drawings(project.id()))
                .filteredOn(DrawingCandidate::conceptApproved)
                .extracting(DrawingCandidate::id)
                .containsExactly(drawings.get(2).id());
    }

    private BasicDetailsRequest details() {
        return new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur, Rajasthan", 2, 7_000_000, Category.NOT_SURE, new FamilyDetails(2, 2, 1, true), List.of("Vastu-friendly", "Family lounge", "Future expansion"));
    }
}

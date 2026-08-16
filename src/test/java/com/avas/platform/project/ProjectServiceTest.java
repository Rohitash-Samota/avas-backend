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
    void sharesRoomsTwoToAFamilyMemberAndKeepsTheSeniorOnTheGroundFloor() {
        var project = service.create(new CreateProjectRequest("Family home", StartMode.PLOT), "INDIVIDUAL");
        service.updateBasicDetails(project.id(), details(), "INDIVIDUAL");
        var recommendation = service.generateRecommendation(project.id(), "INDIVIDUAL");

        assertThat(recommendation.bedrooms()).isEqualTo(3);
        assertThat(recommendation.category()).isEqualTo("PREMIUM");
        assertThat(recommendation.seniorCitizenBedroom()).isTrue();
        assertThat(recommendation.confidence()).isEqualTo(92);
        assertThat(recommendation.provenance()).containsEntry("rule", "RJ-JDA-2026.08");
    }

    @Test
    void usesAdultHeadcountAndKeepsRegularGuestsAsAPreferredFlexRoom() {
        var project = service.create(new CreateProjectRequest("Shared family home", StartMode.PLOT),
                "INDIVIDUAL");
        var details = new BasicDetailsRequest(45, 65, Facing.EAST, "Jaipur", 2, 8_000_000,
                Category.PREMIUM, new FamilyDetails(6, 0, 0, true), List.of("Natural light"));
        service.updateBasicDetails(project.id(), details, "INDIVIDUAL");

        var recommendation = service.generateRecommendation(project.id(), "INDIVIDUAL");

        assertThat(recommendation.bedrooms()).isEqualTo(3);
        assertThat(recommendation.reasons())
                .anyMatch(reason -> reason.contains("6 permanent family members share 3 core bedrooms at two per room"))
                .anyMatch(reason -> reason.contains("preferred flex/guest room"));
        assertThat(recommendation.provenance())
                .containsEntry("method", "deterministic-recommendation-1.2");
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

        var automaticallyFrozen = service.estimates(project.id());
        assertThat(automaticallyFrozen).hasSize(3);
        assertThat(automaticallyFrozen.get(0).recommended())
                .isLessThan(automaticallyFrozen.get(1).recommended());
        assertThat(automaticallyFrozen.get(1).recommended())
                .isLessThan(automaticallyFrozen.get(2).recommended());

        var estimate = service.generateEstimate(project.id(), drawings.get(1).id(), "INDIVIDUAL");
        assertThat(estimate.id()).isEqualTo(automaticallyFrozen.get(1).id());
        assertThat(service.estimates(project.id())).hasSize(3);
        assertThat(estimate.items()).hasSizeGreaterThan(16);
        assertThat(estimate.low()).isLessThan(estimate.high());
        assertThat(estimate.pricingCity()).isEqualTo("Jaipur");
        assertThat(estimate.items()).extracting(EstimateItem::code)
                .contains("LIFT_SHAFT_PROVISION")
                .doesNotContain("PASSENGER_LIFT");
        assertThat(estimate.items()).filteredOn(item -> item.evidenceId() != null)
                .allSatisfy(item -> assertThat(item.evidenceId()).doesNotStartWith("JPR-"));
        assertThat(estimate.items().stream().mapToLong(EstimateItem::amount).sum())
                .isEqualTo(estimate.recommended());
        assertThat(estimate.subtotal() + estimate.taxTotal() + estimate.contingency())
                .isEqualTo(estimate.recommended());
        var fallbackCore = estimate.items().stream()
                .filter(item -> !"ADJUSTMENT".equals(item.itemType()))
                .filter(item -> !item.code().equals("LIFT_SHAFT_PROVISION"))
                .filter(item -> !item.code().equals("PASSENGER_LIFT"))
                .mapToLong(EstimateItem::amount).sum();
        assertThat(fallbackCore).isEqualTo(2_600L * drawings.get(1).builtUpArea());
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
        assertThat(estimateEvidence.status()).isEqualTo("PASSED");
        assertThat(estimateEvidence.detail()).contains("accepted freshness window");
        assertThat(validation.professionalReview()).containsAll(drawings.get(1).hardViolations());
        assertThat(drawings.get(1).hardViolations())
                .noneMatch(value -> value.startsWith("Programme gap: placed built-up area"));
        assertThat(service.get(project.id()).status()).isEqualTo("REVIEW_REQUIRED");
    }

    @Test
    void regenerationSupersedesTheConceptSetInsteadOfGrowingIt() {
        var project = service.create(new CreateProjectRequest("Family home", StartMode.PLOT), "INDIVIDUAL");
        service.updateBasicDetails(project.id(), details(), "INDIVIDUAL");
        var recommendation = service.generateRecommendation(project.id(), "INDIVIDUAL");
        service.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");
        service.generateDrawings(project.id(), "INDIVIDUAL");
        var first = service.currentDrawings(project.id());

        service.regenerate(first.getFirst().id(), "INDIVIDUAL");
        var second = service.currentDrawings(project.id());

        assertThat(second).hasSize(3);
        assertThat(second).extracting(DrawingCandidate::version).containsOnly(first.getFirst().version() + 1);
        assertThat(second).extracting(DrawingCandidate::id)
                .doesNotContainAnyElementsOf(first.stream().map(DrawingCandidate::id).toList());
        // The superseded set stays stored and individually addressable, so an approval or estimate
        // recorded against it keeps resolving after the customer regenerates.
        assertThat(service.drawings(project.id())).hasSize(6);
        assertThat(service.drawing(first.getFirst().id()).id()).isEqualTo(first.getFirst().id());
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

    @Test
    void pricingFailureLeavesNoPartialGenerationArtifacts() {
        var failingCosting = new CostingService(new QuantityTakeoffService(), query -> {
            throw new IllegalStateException("Pricing temporarily unavailable");
        });
        var failing = new ProjectService(new GeometryEngine(), "RJ-JDA-2026.08", "AVAS-KB-2026.08",
                "layout-parameters-2.0.0", "governed-takeoff-2.0.0", failingCosting);
        var project = failing.create(new CreateProjectRequest("Failure-safe home", StartMode.PLOT),
                "INDIVIDUAL");
        failing.updateBasicDetails(project.id(), details(), "INDIVIDUAL");
        var recommendation = failing.generateRecommendation(project.id(), "INDIVIDUAL");
        failing.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> failing.generateDrawings(project.id(), "INDIVIDUAL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pricing temporarily unavailable");
        assertThat(failing.drawings(project.id())).isEmpty();
        assertThat(failing.estimates(project.id())).isEmpty();
        assertThat(failing.audit(project.id())).noneMatch(event ->
                event.action().equals("LAYOUT_CANDIDATES_GENERATED")
                        || event.action().equals("ESTIMATE_AUTO_GENERATED"));
    }

    private BasicDetailsRequest details() {
        return new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur, Rajasthan", 2, 7_000_000, Category.NOT_SURE, new FamilyDetails(2, 2, 1, true), List.of("Vastu-friendly", "Family lounge", "Future expansion"));
    }
}

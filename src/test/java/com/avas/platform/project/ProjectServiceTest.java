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

    /**
     * The product default: the home is planned across the whole plot.
     *
     * <p>Pinned here because the consequences run the length of the workflow. The envelope keeps no
     * open space, so the footprint is the plot and the built-up target rises with it; the same
     * budget then buys a larger house at a lower inferred finish tier, and every drawing has to say
     * the layout is not approvable as drawn.</p>
     */
    @Test
    void fullPlotUsageBuildsTheWholePlotAndSpreadsTheBudgetThinner() {
        var project = service.create(new CreateProjectRequest("Family home", StartMode.PLOT), "INDIVIDUAL");
        service.updateBasicDetails(project.id(), details(HomeParameters.FULL_PLOT), "INDIVIDUAL");
        var recommendation = service.generateRecommendation(project.id(), "INDIVIDUAL");

        var setback = service.create(new CreateProjectRequest("Same brief", StartMode.PLOT), "INDIVIDUAL");
        service.updateBasicDetails(setback.id(), details(HomeParameters.STANDARD_SETBACK), "INDIVIDUAL");
        var inset = service.generateRecommendation(setback.id(), "INDIVIDUAL");

        // The whole 40 x 60 plot, rather than the rectangle the assumed ring leaves inside it.
        assertThat(recommendation.builtUpAreaMinimum()).isGreaterThan(inset.builtUpAreaMinimum());
        // Same budget over more floor: the finish the server can stand behind drops a tier.
        assertThat(inset.category()).isEqualTo("PREMIUM");
        assertThat(recommendation.category()).isEqualTo("STANDARD");

        service.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");
        service.generateDrawings(project.id(), "INDIVIDUAL");
        assertThat(service.drawings(project.id())).isNotEmpty().allSatisfy(drawing -> {
            var geometry = drawing.geometry();
            assertThat(geometry.setbacks().front()).isZero();
            assertThat(geometry.setbacks().rear()).isZero();
            assertThat(geometry.setbacks().side()).isZero();
            assertThat(geometry.setbacks().source()).isEqualTo(SetbackRule.WAIVED);
            assertThat(geometry.buildableArea()).isEqualTo(geometry.plotArea());
            // Nothing is planned outside the building, so the cars are planned inside it.
            assertThat(geometry.siteElements()).isEmpty();
            assertThat(geometry.rooms()).anyMatch(room -> room.type().contains("PARKING"));
            assertThat(drawing.softRecommendations())
                    .anyMatch(note -> note.contains("open-space rule must be confirmed"))
                    .anyMatch(note -> note.contains("party walls"));
            assertThat(drawing.versions()).containsEntry("plotUsage", HomeParameters.FULL_PLOT);
        });
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
            // The programme the planner is given is the one the customer was quoted, so a layout
            // that places it has nothing to report. This used to come back flagged for review
            // against a bedroom count nobody had chosen: the parameter targets counted every child
            // as a bedroom of their own while the recommendation had them sharing, so all three
            // candidates were short of a bedroom that was never in the brief.
            assertThat(drawing.hardViolations()).isEmpty();
            assertThat(drawing.status()).isEqualTo("SUCCESS");
            assertThat(drawing.explanations())
                    .anyMatch(explanation -> explanation.contains(
                            recommendation.bedrooms() + " core bedrooms are recommended"));
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
        assertThat(validation.status()).isEqualTo(EngineStatus.SUCCESS);
        var buildingRules = validation.gates().stream()
                .filter(gate -> gate.name().equals("Building rules")).findFirst().orElseThrow();
        assertThat(buildingRules.status()).isEqualTo("PASSED");
        assertThat(buildingRules.detail()).contains("No unresolved hard constraints");
        var estimateEvidence = validation.gates().stream()
                .filter(gate -> gate.name().equals("Estimate evidence")).findFirst().orElseThrow();
        assertThat(estimateEvidence.status()).isEqualTo("PASSED");
        assertThat(estimateEvidence.detail()).contains("accepted freshness window");
        // Sign-off is never waived by a clean run; it is scope, not a finding.
        assertThat(validation.professionalReview()).contains("Licensed architect review");
        assertThat(service.get(project.id()).status()).isEqualTo("CONCEPTS_READY");
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

    /**
     * The shared brief, planned inside the assumed setback ring.
     *
     * <p>Pinned rather than left to the {@link HomeParameters#FULL_PLOT} product default because the
     * inferred finish tier here is budget over built-up area: full plot usage spreads the same
     * budget across a much larger house and lands on a different tier, which
     * {@link #fullPlotUsageBuildsTheWholePlotAndSpreadsTheBudgetThinner()} covers directly.</p>
     */
    private BasicDetailsRequest details() {
        return details(HomeParameters.STANDARD_SETBACK);
    }

    private BasicDetailsRequest details(String plotUsage) {
        var brief = new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur, Rajasthan", 2, 7_000_000, Category.NOT_SURE, new FamilyDetails(2, 2, 1, true), List.of("Vastu-friendly", "Family lounge", "Future expansion"));
        return new BasicDetailsRequest(brief.plotWidth(), brief.plotLength(), brief.roadFacing(),
                brief.city(), brief.floors(), brief.budget(), brief.category(), brief.family(),
                brief.preferences(), withPlotUsage(brief.parameters(), plotUsage));
    }

    /** Every inferred parameter as the brief derived it, with only the plot usage overridden. */
    private static HomeParameters withPlotUsage(HomeParameters source, String plotUsage) {
        return new HomeParameters(source.homeType(), source.staircaseType(), source.liftProvision(),
                source.balconyCount(), source.terraceRequired(), source.courtyardRequired(),
                source.accessibleGroundFloor(), source.parkingCars(), source.solarReady(),
                source.rainwaterHarvesting(), plotUsage);
    }
}

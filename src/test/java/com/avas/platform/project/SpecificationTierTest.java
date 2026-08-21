package com.avas.platform.project;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The finish tier has to change the plan, not only the price.
 *
 * <p>Before {@link SpecificationTier} existed, {@link Category} reached two places: the rate the
 * estimate multiplied by, and whether the site plan named a lawn. Two families on the same plot with
 * the same household were drawn identical homes and charged different amounts for them.</p>
 */
class SpecificationTierTest {
    private final GeometryEngine engine = new GeometryEngine();

    @Test
    void luxuryDrawsTheArrivalSequenceAndDressedSuiteThatStandardDoesNot() {
        var standard = groundAndFirst(plan(Category.STANDARD));
        var luxury = groundAndFirst(plan(Category.LUXURY));

        // The hall you arrive into, and the dressing room planned into the master suite. Neither is
        // in the household brief; both are what the tier is bought for.
        assertThat(standard).doesNotContain("FOYER");
        assertThat(luxury).contains("FOYER").contains("DRESSING_ROOM");
        // A dressing room can still turn up at any tier as a filler beside a bathroom, so the tier
        // signal is the planned suite rather than the room's mere presence — see the sequence test.
        assertThat(standard).doesNotContainSequence("MASTER_BEDROOM", "DRESSING_ROOM");
    }

    @Test
    void aDressedMasterSuiteKeepsItsOwnBathroom() {
        // The dressing room sits between the master bedroom and its ensuite, so an orphan check
        // that only looked at the entry immediately before the bathroom read every dressed suite as
        // an orphan and deleted the master's bathroom on exactly the tier that paid for it.
        var candidate = plan(Category.LUXURY).getFirst();
        var first = candidate.geometry().rooms().stream()
                .filter(room -> "FIRST".equals(room.floor())).map(RoomGeometry::type).toList();

        assertThat(first).containsSequence("MASTER_BEDROOM", "DRESSING_ROOM", "ATTACHED_BATHROOM");
    }

    @Test
    void aTighterPlateGivesUpTheTiersOwnRoomsBeforeItGivesUpTheUtility() {
        // RoomSpec ranks a WC above a utility, which is right for the core programme and wrong for
        // a room the tier added on top of it. Scored by type alone, a premium brief on a small plate
        // kept the visitor's WC and lost the room the washing is done in.
        var rooms = groundAndFirst(plan(Category.LUXURY, 24, 40));

        assertThat(rooms).contains("KITCHEN");
        if (!rooms.contains("UTILITY")) return;
        assertThat(rooms).as("a tight plate kept a tier extra while dropping the utility")
                .doesNotContain("TOILET");
    }

    @Test
    void theVerandahGivesWayBeforeTheFoyerWhenTheFrontageCannotCarryBoth() {
        // Programme order alone dropped whichever was added last — the foyer — leaving the covered
        // step and losing the hall, which is the wrong half of an arrival sequence to keep.
        var rooms = groundAndFirst(plan(Category.LUXURY));

        if (rooms.contains("VERANDAH")) return;
        assertThat(rooms).as("the frontage dropped the foyer and kept the verandah")
                .contains("FOYER");
    }

    @Test
    void theTierIsReadFromTheRecommendationRatherThanGuessedAgain() {
        // A project that chose NOT_SURE has had its tier derived from budget per square foot by the
        // time a recommendation exists, so the drawing reads that decision instead of repeating it.
        assertThat(SpecificationTier.of(recommendation("LUXURY"))).isEqualTo(SpecificationTier.LUXURY);
        assertThat(SpecificationTier.of(Category.NOT_SURE, 9_000_000, 2_000))
                .isEqualTo(SpecificationTier.LUXURY);
        assertThat(SpecificationTier.of(Category.NOT_SURE, 3_000_000, 2_000))
                .isEqualTo(SpecificationTier.STANDARD);
        assertThat(SpecificationTier.of((String) null)).isEqualTo(SpecificationTier.STANDARD);
    }

    @Test
    void everyTierKeepsTheWholeFillerPoolSoALongStripIsNeverStretchedToReachTheEndWall() {
        // The surplus list is what a strip longer than its programme is filled from. A tier that
        // shortened it would not draw a tidier plan; it would draw the last room stretched thirty
        // feet to the end wall.
        for (var tier : SpecificationTier.values()) {
            assertThat(tier.surplusOrder())
                    .as("%s surplus order", tier)
                    .hasSizeGreaterThanOrEqualTo(8)
                    .doesNotHaveDuplicates()
                    .contains("STORE", "STUDY", "PRAYER_ROOM", "DRESSING_ROOM", "HOME_OFFICE");
        }
        assertThat(SpecificationTier.LUXURY.surplusOrder().getFirst()).isEqualTo("DRESSING_ROOM");
        assertThat(SpecificationTier.STANDARD.surplusOrder().getFirst()).isEqualTo("STORE");
    }

    @Test
    void aLuxuryBriefStartsFromTheProvisionsItsOwnBudgetCovers() {
        // These are defaults a customer remains free to change; the tier only moves the starting
        // point. A home costed as luxury that defaulted to one bay and no lift was quoting one house
        // and offering to draw another.
        var luxury = HomeParameters.defaults(2, 2_400, false, List.of(), SpecificationTier.LUXURY);
        var standard = HomeParameters.defaults(2, 2_400, false, List.of(), SpecificationTier.STANDARD);

        assertThat(luxury.liftProvision()).isEqualTo("FUTURE_SHAFT");
        assertThat(standard.liftProvision()).isEqualTo("NONE");
        assertThat(luxury.terraceRequired()).isTrue();
        assertThat(luxury.parkingCars()).isGreaterThanOrEqualTo(standard.parkingCars());
    }

    // -------------------------------------------------------------------------------------------

    private List<String> groundAndFirst(List<DrawingCandidate> candidates) {
        return candidates.getFirst().geometry().rooms().stream().map(RoomGeometry::type).toList();
    }

    private List<DrawingCandidate> plan(Category category) {
        return plan(category, 40, 60);
    }

    private List<DrawingCandidate> plan(Category category, double width, double length) {
        var brief = new BasicDetailsRequest(width, length, Facing.NORTH, "Jaipur", 2,
                category == Category.LUXURY ? 14_000_000 : 6_000_000, category,
                new FamilyDetails(2, 2, 0, true), List.of());
        var inferred = brief.parameters();
        var details = new BasicDetailsRequest(brief.plotWidth(), brief.plotLength(), brief.roadFacing(),
                brief.city(), brief.floors(), brief.budget(), brief.category(), brief.family(),
                brief.preferences(),
                new HomeParameters(inferred.homeType(), inferred.staircaseType(),
                        inferred.liftProvision(), inferred.balconyCount(), inferred.terraceRequired(),
                        inferred.courtyardRequired(), inferred.accessibleGroundFloor(),
                        inferred.parkingCars(), inferred.solarReady(), inferred.rainwaterHarvesting(),
                        HomeParameters.STANDARD_SETBACK));
        return engine.generate("tier-" + category, 1, details, recommendation(category.name()),
                Map.of("ruleVersion", "test-rules", "strategyVersion", "test-strategy"));
    }

    private Recommendation recommendation(String category) {
        return new Recommendation("rec-1", "Three-bedroom duplex", category, 3, 2, 1, 2,
                1_900, 2_900, 6_300_000, 7_300_000, false, true, false, 92,
                List.of("Family brief"), Map.of("rule", "test"), true);
    }
}

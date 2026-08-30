package com.avas.platform.project;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The optimized programme has to be able to change which rooms a family gets.
 *
 * <p>It was read in exactly one place — the area lookup that resizes rooms the planner had already
 * chosen for itself. So an optimizer, model or deterministic, could say a home wants a prayer room
 * and the drawing would come back without one, having spent the request on making the kitchen
 * slightly larger. Plot area, budget and household reached the optimizer, changed its answer, and
 * changed no wall.</p>
 */
class ProgrammeRoomPlacementTest {
    private final GeometryEngine engine = new GeometryEngine();

    @Test
    void aRoomTheProgrammeAsksForIsDrawnEvenWhenTheCoreProgrammeNeverNamesIt() {
        // A verandah, because a premium brief's own programme does not include one and it is not on
        // the list the planner spends surplus floor on. So its presence can only have come from the
        // proposal, which is the whole point.
        var withoutIt = types(plan(List.of()));
        var withIt = types(plan(List.of(target("VERANDAH", "GROUND", "PREFERRED"))));

        assertThat(withoutIt).doesNotContain("VERANDAH");
        assertThat(withIt).contains("VERANDAH");
    }

    @Test
    void anArrivalSpaceTheProgrammeAddsIsMetFromTheRoadRatherThanBuriedBehindTheLiving() {
        // Where a room lands is as much the answer as whether it lands. A verandah drawn behind the
        // living room is not a verandah, so a proposal that adds one has to reach the frontage.
        var ground = plan(List.of(target("VERANDAH", "GROUND", "PREFERRED"))).getFirst()
                .geometry().rooms().stream().filter(room -> "GROUND".equals(room.floor())).toList();
        var verandah = ground.stream().filter(room -> "VERANDAH".equals(room.type())).findFirst()
                .orElseThrow();
        var living = ground.stream().filter(room -> "LIVING_ROOM".equals(room.type())).findFirst()
                .orElseThrow();

        // North-facing, so the road edge is the maximum y of the planning grid.
        assertThat(verandah.y()).isGreaterThanOrEqualTo(living.y() - .01);
    }

    @Test
    void aNameThisEngineCannotDrawIsIgnoredRatherThanPlacedAtFallbackSize() {
        // An unknown type resolves to RoomSpec's fallback dimensions, so placing it would draw a
        // room at a size nobody chose, label it with a name no renderer has furniture for, and count
        // it in the schedule as though the platform understood it.
        var rooms = types(plan(List.of(target("DRAWING_ROOM", "GROUND", "REQUIRED"))));

        assertThat(rooms).doesNotContain("DRAWING_ROOM");
        assertThat(rooms).contains("KITCHEN", "LIVING_ROOM");
    }

    @Test
    void theProgrammeCannotAddBedroomsBeyondTheBriefTheCustomerAccepted() {
        // The recommendation fixes the bedroom count, and the validator audits the drawing against
        // it. A layout that quietly exceeded it would be disagreeing with its own brief.
        var baseline = count(plan(List.of()), "BEDROOM");
        var pushed = count(plan(List.of(
                target("BEDROOM", "FIRST", "REQUIRED"),
                target("MASTER_BEDROOM", "FIRST", "REQUIRED"),
                target("ATTACHED_BATHROOM", "FIRST", "REQUIRED"))), "BEDROOM");

        assertThat(pushed).isEqualTo(baseline);
    }

    @Test
    void theProgrammeCannotRedrawTheCirculationOrTheCore() {
        // Stair and shaft stack across storeys and are the planner's own structure; a second one
        // placed off a programme entry would be a core that does not line up with itself.
        var candidate = plan(List.of(
                target("CORRIDOR", "GROUND", "REQUIRED"),
                target("STAIRCASE", "GROUND", "REQUIRED"),
                target("LIFT_SHAFT", "GROUND", "REQUIRED"))).getFirst();
        var ground = candidate.geometry().rooms().stream()
                .filter(room -> "GROUND".equals(room.floor())).map(RoomGeometry::type).toList();

        // The spine is a hub now, not a corridor: circulation is habitable or it is not drawn. A
        // programme entry asking for a passage is therefore refused outright rather than honoured
        // once — this used to assert exactly one corridor, which was the old arrangement's rule.
        assertThat(ground).doesNotContain(RoomSpec.CORRIDOR);
        assertThat(ground).filteredOn("STAIRCASE"::equals).hasSizeLessThanOrEqualTo(1);
        assertThat(ground).filteredOn("LIFT_SHAFT"::equals).hasSizeLessThanOrEqualTo(1);
    }

    @Test
    void aProposalListingEverySpaceCannotCrowdOutTheRoomsTheHouseholdNeeds() {
        var extras = List.of(
                target("PRAYER_ROOM", "GROUND", "REQUIRED"),
                target("HOME_OFFICE", "GROUND", "REQUIRED"),
                target("MULTIPURPOSE_ROOM", "GROUND", "REQUIRED"),
                target("FLEX_ROOM", "GROUND", "REQUIRED"),
                target("STUDY", "GROUND", "REQUIRED"),
                target("LAUNDRY", "GROUND", "REQUIRED"),
                target("STORE", "GROUND", "REQUIRED"),
                target("PORCH", "GROUND", "REQUIRED"));
        var candidate = plan(extras).getFirst();
        var ground = candidate.geometry().rooms().stream()
                .filter(room -> "GROUND".equals(room.floor())).map(RoomGeometry::type).toList();

        // The rooms the household cannot do without are still there, and the proposal's eight
        // suggestions did not all land. Which of them landed is the planner's business — the
        // guarantee is that a long list cannot displace the core programme.
        assertThat(ground).contains("KITCHEN", "LIVING_ROOM", "DINING");
        assertThat(candidate.geometry().rooms()).anyMatch(room -> room.type().endsWith("BEDROOM"));
        var proposed = extras.stream().map(PlanningParameterVariant.RoomTarget::roomType).toList();
        assertThat(ground.stream().filter(proposed::contains).distinct().toList())
                .as("every space the proposal listed landed on one storey")
                .hasSizeLessThan(proposed.size());
    }

    // -------------------------------------------------------------------------------------------

    private List<String> types(List<DrawingCandidate> candidates) {
        return candidates.getFirst().geometry().rooms().stream().map(RoomGeometry::type).toList();
    }

    private long count(List<DrawingCandidate> candidates, String suffix) {
        return types(candidates).stream().filter(type -> type.endsWith(suffix)).count();
    }

    private PlanningParameterVariant.RoomTarget target(String type, String floor, String priority) {
        return new PlanningParameterVariant.RoomTarget(type, floor, 1, 90, priority);
    }

    private List<DrawingCandidate> plan(List<PlanningParameterVariant.RoomTarget> extras) {
        var details = new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur", 2, 9_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 0, false), List.of());
        return engine.generate("programme", 1, details, recommendation(),
                Map.of("ruleVersion", "test-rules", "strategyVersion", "test-strategy"),
                parameterSet(extras));
    }

    private Recommendation recommendation() {
        return new Recommendation("rec-1", "Three-bedroom duplex", "PREMIUM", 3, 2, 1, 2,
                1_500, 4_200, 6_300_000, 7_300_000, false, true, false, 92,
                List.of("Family brief"), Map.of("rule", "test"), true);
    }

    /** The core programme every variant carries, plus whichever extras a case is exercising. */
    private PlanningParameterSet parameterSet(List<PlanningParameterVariant.RoomTarget> extras) {
        var targets = new java.util.ArrayList<>(List.of(
                target("LIVING_ROOM", "GROUND", "REQUIRED"),
                target("DINING", "GROUND", "REQUIRED"),
                target("KITCHEN", "GROUND", "REQUIRED"),
                target("BATHROOM", "GROUND", "REQUIRED"),
                target("BEDROOM", "FIRST", "REQUIRED")));
        targets.addAll(extras);
        var weights = Map.of("budget", .2, "functionality", .3, "daylight", .2,
                "accessibility", .15, "futureReadiness", .15);
        var variants = List.of("BUDGET_OPTIMIZED", "BALANCED", "LIFESTYLE_OPTIMIZED").stream()
                .map(strategy -> new PlanningParameterVariant(strategy, strategy + " option",
                        "Shared living below; private rooms above", "DOG_LEGGED", "NONE", 1,
                        false, false, false, 2, false, false, List.copyOf(targets), weights,
                        List.of("Sized to the plot", "Geometry remains deterministic")))
                .toList();
        return new PlanningParameterSet(null, "TEST", "test-rules", "home-parameters-1.2.0",
                "home-parameters-1", false, List.of(), variants);
    }
}

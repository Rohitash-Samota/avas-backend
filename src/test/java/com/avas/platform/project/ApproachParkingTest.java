package com.avas.platform.project;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the cars stand has to be one decision, not two that never spoke to each other.
 *
 * <p>The planner used to take a bay-sized bite out of the ground floor whenever parking was asked
 * for, while the site plan separately measured the front setback and drew bays on it if a car
 * happened to fit. Both fired on most plots, so a home got a garage indoors and a driveway outside;
 * and the programme audit, counting only indoor rectangles, reported a plan that parked two cars on
 * the approach as providing none.</p>
 */
class ApproachParkingTest {
    private final GeometryEngine engine = new GeometryEngine();

    @Test
    void aDeepApproachParksTheCarsOutsideAndKeepsTheGroundFloorForRooms() {
        // A 60 x 90 plot carries a 15 ft front setback, which is deep enough to drive into.
        var candidate = plan(60, 90).getFirst();
        var indoorBays = candidate.geometry().rooms().stream()
                .filter(room -> room.type().contains("PARKING")).count();
        var outdoorBays = candidate.geometry().siteElements().stream()
                .filter(element -> element.type().contains("PARKING")).count();

        assertThat(outdoorBays).as("the approach can take the cars").isGreaterThan(0);
        assertThat(indoorBays).as("so the building does not also carry them").isZero();
    }

    @Test
    void carsParkedOnTheApproachCountAsParkingThePlanProvides() {
        // A bay in the front setback is a car that is parked. Measuring only what is inside the
        // building reported every layout that deliberately kept the cars outside — the arrangement
        // that buys the ground floor its rooms back — as failing to provide its own parking.
        var candidate = plan(60, 90).getFirst();

        assertThat(candidate.hardViolations())
                .as("outdoor bays were not counted against the recommended parking")
                .noneMatch(violation -> violation.contains("parking bays represented"));
    }

    @Test
    void aShallowApproachMovesTheBuildingBackRatherThanTakingTheCarsIndoors() {
        // A 7.5 ft front setback is a foot short of the width of a car, and being a foot short used
        // to cost the ground floor a seventeen-foot garage — three hundred and forty square feet of
        // structure, taken from the living room's frontage. The building gives up the foot instead.
        var candidate = plan(40, 60).getFirst();

        assertThat(candidate.geometry().rooms())
                .as("nothing is parked inside the building")
                .noneMatch(room -> room.type().contains("PARKING"));
        assertThat(candidate.geometry().siteElements())
                .anyMatch(element -> element.type().equals("OUTDOOR_PARKING"));
        assertThat(candidate.softRecommendations())
                .as("the customer is told the building moved, and why")
                .anyMatch(note -> note.contains("set back a further")
                        && note.contains("stand on the approach"));
    }

    @Test
    void aPlotTooTightToBothBuildAndParkOnKeepsTheHomeAndBringsTheCarsIndoors() {
        // The hold-back is never taken at the cost of a plate too narrow to plan rooms against.
        var candidate = plan(22, 40).getFirst();

        assertThat(candidate.geometry().siteElements())
                .noneMatch(element -> element.type().equals("OUTDOOR_PARKING"));
        assertThat(candidate.softRecommendations())
                .noneMatch(note -> note.contains("set back a further"));
    }

    @Test
    void buildingAcrossTheWholePlotParksEveryCarIndoors() {
        // Full plot usage leaves no approach: the customer asked to build across the outline, so the
        // only ground beside the building is a sliver the envelope already gave to a room.
        var candidate = plan(40, 60, HomeParameters.FULL_PLOT).getFirst();

        assertThat(candidate.geometry().siteElements()).isEmpty();
        assertThat(candidate.geometry().rooms()).anyMatch(room -> room.type().contains("PARKING"));
    }

    // -------------------------------------------------------------------------------------------

    private List<DrawingCandidate> plan(double width, double length) {
        return plan(width, length, HomeParameters.STANDARD_SETBACK);
    }

    private List<DrawingCandidate> plan(double width, double length, String plotUsage) {
        var brief = new BasicDetailsRequest(width, length, Facing.NORTH, "Jaipur", 2, 9_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 0, false), List.of());
        var inferred = brief.parameters();
        var details = new BasicDetailsRequest(brief.plotWidth(), brief.plotLength(), brief.roadFacing(),
                brief.city(), brief.floors(), brief.budget(), brief.category(), brief.family(),
                brief.preferences(),
                new HomeParameters(inferred.homeType(), inferred.staircaseType(),
                        inferred.liftProvision(), inferred.balconyCount(), inferred.terraceRequired(),
                        inferred.courtyardRequired(), inferred.accessibleGroundFloor(), 2,
                        inferred.solarReady(), inferred.rainwaterHarvesting(), plotUsage));
        return engine.generate("parking-" + width + "x" + length + "-" + plotUsage, 1, details,
                recommendation(), Map.of("ruleVersion", "test-rules", "strategyVersion", "test-strategy"));
    }

    private Recommendation recommendation() {
        return new Recommendation("rec-1", "Three-bedroom duplex", "PREMIUM", 3, 2, 1, 2,
                1_500, 4_200, 6_300_000, 7_300_000, false, true, false, 92,
                List.of("Family brief"), Map.of("rule", "test"), true);
    }
}

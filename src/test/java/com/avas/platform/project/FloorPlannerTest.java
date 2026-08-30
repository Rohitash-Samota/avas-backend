package com.avas.platform.project;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the properties that make a generated plan a home rather than a partitioned rectangle.
 *
 * <p>Everything here is a statement about liveability, not about the engine's internals: a room you
 * can put furniture in, a passage you can reach it from, a bathroom that belongs to the bedroom it
 * serves. The earlier grid packer satisfied every structural rule in {@link GeometryEngine} and
 * failed all of these, which is exactly why they are asserted separately.</p>
 */
class FloorPlannerTest {
    private final GeometryEngine engine = new GeometryEngine();

    @Test
    void everyRoomIsBigEnoughToBeTheRoomItIsLabelled() {
        forEveryPlan((plot, floors, candidate) -> {
            for (var room : candidate.geometry().rooms()) {
                var spec = RoomSpec.of(room.type());
                var shortSide = Math.min(room.width(), room.length());
                var longSide = Math.max(room.width(), room.length());
                assertThat(shortSide)
                        .as("%s on a %s ft plot with %s floor(s) is only %.2f ft across",
                                room.type(), plot, floors, shortSide)
                        .isGreaterThanOrEqualTo(spec.minShortSide() - .05);
                assertThat(longSide)
                        .as("%s on a %s ft plot with %s floor(s) is only %.2f ft long",
                                room.type(), plot, floors, longSide)
                        .isGreaterThanOrEqualTo(spec.minShortSide() - .05);
            }
        });
    }

    @Test
    void noHabitableRoomIsDrawnAsACorridor() {
        // A room more than four times as long as it is wide cannot be furnished as that room,
        // whatever its area says. Circulation and shafts are the two spaces that legitimately are.
        var slender = List.of(RoomSpec.CORRIDOR, "STAIRCASE", "LIFT_SHAFT", "BALCONY", "TERRACE");
        forEveryPlan((plot, floors, candidate) -> {
            for (var room : candidate.geometry().rooms()) {
                if (slender.contains(room.type())) continue;
                var ratio = Math.max(room.width(), room.length()) / Math.min(room.width(), room.length());
                assertThat(ratio)
                        .as("%s on a %s ft plot with %s floor(s) is %.2f x %.2f ft",
                                room.type(), plot, floors, room.width(), room.length())
                        .isLessThanOrEqualTo(4.0);
            }
        });
    }

    /** The rooms a plan is allowed to circulate through, now that no plan draws a passage. */
    private static final List<String> HUB = List.of("LIVING_ROOM", "DINING", "FAMILY_LOUNGE",
            "MULTIPURPOSE_ROOM", "FOYER");

    @Test
    void noStoreyIsDrawnWithACorridor() {
        // The whole point of the hub: circulation is floor the family uses, or it is not drawn.
        // A passage running the depth of a storey is about forty square feet a floor spent on
        // walking, and the customer is charged for every one of them.
        forEveryPlan((plot, floors, candidate) -> assertThat(candidate.geometry().rooms())
                .as("a %s ft plot with %s floor(s) was planned with a corridor", plot, floors)
                .noneMatch(room -> RoomSpec.CORRIDOR.equals(room.type())));
    }

    @Test
    void everyStoreyHasHabitableCirculationAndNoRoomIsReachedThroughABedroom() {
        forEveryPlan((plot, floors, candidate) -> {
            var geometry = candidate.geometry();
            var roomsById = geometry.rooms().stream()
                    .collect(java.util.stream.Collectors.toMap(RoomGeometry::id, room -> room));
            for (var floor : List.of("GROUND", "FIRST", "SECOND").subList(0, floors)) {
                // Without a passage, the storey still has to have somewhere the household walks
                // through. A floor of rooms and nothing else is a floor entered through a bedroom.
                assertThat(geometry.rooms())
                        .as("%s floor of a %s ft plot has nothing to circulate through", floor, plot)
                        .anyMatch(room -> floor.equals(room.floor()) && HUB.contains(room.type()));
            }
            for (var door : geometry.doors()) {
                var connected = door.get("connectsRoomId");
                if (connected == null) continue;
                var from = roomsById.get(String.valueOf(door.get("roomId")));
                var to = roomsById.get(String.valueOf(connected));
                if (from == null || to == null) continue;
                // A door out of a bedroom leads to the hub or to something that belongs to that
                // bedroom: its bathroom, its dressing room, its store, its balcony. Never to a
                // kitchen, a stair or another bedroom.
                if (from.type().endsWith("BEDROOM")) {
                    var allowed = new java.util.ArrayList<>(HUB);
                    allowed.addAll(List.of("ATTACHED_BATHROOM", "DRESSING_ROOM", "STORE",
                            "BALCONY", "TERRACE"));
                    assertThat(to.type())
                            .as("%s on a %s ft plot is entered through %s", to.type(), plot, from.type())
                            .isIn(allowed.toArray());
                }
            }
        });
    }

    @Test
    void everyPrivateBathroomSharesAWallWithABedroomAndOpensOffIt() {
        forEveryPlan((plot, floors, candidate) -> {
            var geometry = candidate.geometry();
            var roomsById = geometry.rooms().stream()
                    .collect(java.util.stream.Collectors.toMap(RoomGeometry::id, room -> room));
            for (var bathroom : geometry.rooms()) {
                if (!"ATTACHED_BATHROOM".equals(bathroom.type())) continue;
                var openings = geometry.doors().stream()
                        .filter(door -> bathroom.id().equals(door.get("roomId"))
                                || bathroom.id().equals(door.get("connectsRoomId")))
                        .toList();
                assertThat(openings)
                        .as("%s on a %s ft plot has no door", bathroom.id(), plot)
                        .isNotEmpty();
                assertThat(openings).anySatisfy(door -> {
                    var other = bathroom.id().equals(door.get("roomId"))
                            ? roomsById.get(String.valueOf(door.get("connectsRoomId")))
                            : roomsById.get(String.valueOf(door.get("roomId")));
                    assertThat(other).isNotNull();
                    assertThat(other.type())
                            .as("%s on a %s ft plot opens off %s rather than a bedroom",
                                    bathroom.id(), plot, other.type())
                            .endsWith("BEDROOM");
                });
            }
        });
    }

    @Test
    void roomsTileTheFloorPlateWithoutGapsOrOverlaps() {
        forEveryPlan((plot, floors, candidate) -> {
            var geometry = candidate.geometry();
            for (var floor : List.of("GROUND", "FIRST", "SECOND").subList(0, floors)) {
                var rooms = geometry.rooms().stream().filter(room -> floor.equals(room.floor())).toList();
                var placed = rooms.stream().mapToDouble(RoomGeometry::area).sum();
                var minX = rooms.stream().mapToDouble(RoomGeometry::x).min().orElseThrow();
                var maxX = rooms.stream().mapToDouble(room -> room.x() + room.width()).max().orElseThrow();
                var minY = rooms.stream().mapToDouble(RoomGeometry::y).min().orElseThrow();
                var maxY = rooms.stream().mapToDouble(room -> room.y() + room.length()).max().orElseThrow();
                // No overlaps is already a hard rule, so equal areas means no gaps either.
                assertThat(placed)
                        .as("%s floor of a %s ft plot leaves unplanned area inside its own envelope",
                                floor, plot)
                        .isCloseTo((maxX - minX) * (maxY - minY), org.assertj.core.data.Offset.offset(1.5));
            }
        });
    }

    @Test
    void theStairAndLiftLandInTheSamePlaceOnEveryStorey() {
        var parameters = new HomeParameters("MULTI_STOREY", "DOG_LEGGED", "PASSENGER", 1, true,
                false, false, 2, false, false);
        var details = new BasicDetailsRequest(40, 60, Facing.EAST, "Jaipur", 3, 9_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 1, true), List.of("Natural light"), parameters);

        for (var candidate : engine.generate("stack", 1, details, recommendation(), versions())) {
            for (var type : List.of("STAIRCASE", "LIFT_SHAFT")) {
                var cores = candidate.geometry().rooms().stream()
                        .filter(room -> type.equals(room.type()))
                        .map(room -> List.of(room.x(), room.y(), room.width(), room.length()))
                        .distinct().toList();
                assertThat(cores).as("%s is not stacked on %s", type, candidate.strategy()).hasSize(1);
            }
        }
    }

    @Test
    void theThreeStrategiesProduceGenuinelyDifferentPlans() {
        var details = new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur", 2, 8_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 1, true), List.of("Natural light"));

        var candidates = engine.generate("variety", 1, details, recommendation(), versions());

        var shapes = candidates.stream()
                .map(candidate -> candidate.geometry().rooms().stream()
                        .map(room -> room.type() + "@" + room.x() + "," + room.y()
                                + ":" + room.width() + "x" + room.length())
                        .toList())
                .distinct().toList();
        assertThat(shapes).as("the three strategies drew the same plan").hasSize(3);
    }

    @Test
    void aSetbackNeverDeletesTheFarSideOfAnIrregularPlot() {
        // An L-shaped survey: the top edge of the short leg has a line that runs straight through
        // the tall one. Clipping against that line rather than the edge itself used to erase the
        // whole leg, leaving 325 of 3,312 sq ft buildable and a shed in the corner of an empty site.
        var boundary = new PlotBoundary(List.of(
                PlotVertex.of(0, 0), PlotVertex.of(60, 0), PlotVertex.of(60, 30),
                PlotVertex.of(36, 30), PlotVertex.of(36, 72), PlotVertex.of(0, 72)), Facing.NORTH);
        var rule = SetbackRule.assumedFor(boundary, 2);

        var envelope = BuildableEnvelope.derive(boundary, rule, 2);

        assertThat(envelope.buildableArea()).isGreaterThan(boundary.area() * .45);
        assertThat(envelope.footprintArea()).isGreaterThan(1_000);
        // Correct is not the same as generous: nothing may sit closer to a boundary than its setback.
        for (var corner : envelope.buildableOutline()) {
            for (var edge : boundary.edges()) {
                assertThat(PlotGeometry.distanceToSegment(corner, edge.from(), edge.to()))
                        .as("buildable corner %s is inside the %s setback", corner, edge.side())
                        .isGreaterThanOrEqualTo(edge.setbackFrom(rule) - .05);
            }
        }
    }

    /** Runs an assertion over a spread of plots, floor counts and road facings. */
    private void forEveryPlan(PlanAssertion assertion) {
        for (var plot : List.of(new double[] {30, 50}, new double[] {40, 60}, new double[] {55, 45},
                new double[] {24, 70})) {
            for (var floors : List.of(1, 2, 3)) {
                var facing = Facing.values()[(int) (plot[0] + floors) % Facing.values().length];
                var details = new BasicDetailsRequest(plot[0], plot[1], facing, "Jaipur", floors,
                        8_000_000, Category.PREMIUM, new FamilyDetails(2, 2, 1, true),
                        List.of("Natural light"));
                var label = (int) plot[0] + "x" + (int) plot[1] + " " + facing;
                List<DrawingCandidate> candidates;
                try {
                    candidates = engine.generate("plan-" + label + "-" + floors, 1, details,
                            recommendation(), versions());
                } catch (IllegalArgumentException refused) {
                    // Refusing a plot the setbacks leave unbuildable is a valid answer, and the one
                    // case where there is no geometry to make assertions about.
                    assertThat(refused).hasMessageContaining("expert review");
                    continue;
                }
                for (var candidate : candidates) {
                    assertion.check(label, floors, candidate);
                }
            }
        }
    }

    @FunctionalInterface
    private interface PlanAssertion {
        void check(String plot, int floors, DrawingCandidate candidate);
    }

    private Recommendation recommendation() {
        return new Recommendation("rec-1", "Four-bedroom home", "PREMIUM", 4, 3, 1, 2,
                2400, 2800, 6_300_000, 7_300_000, true, true, true, 92,
                List.of("Family brief"), Map.of("rule", "test"), true);
    }

    private Map<String, String> versions() {
        return Map.of("ruleVersion", "test-rules", "strategyVersion", "test-strategy");
    }
}

package com.avas.platform.project;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeometryEngineTest {
    private final GeometryEngine engine = new GeometryEngine();

    @Test
    void generatesCompleteStableGeometryForOneTwoAndThreeFloors() {
        for (var floors : List.of(1, 2, 3)) {
            var candidates = engine.generate("project-" + floors, 3, details(40, floors), recommendation(), versions());

            assertThat(candidates).hasSize(3);
            assertThat(candidates).extracting(DrawingCandidate::strategy)
                    .containsExactly("BUDGET_OPTIMIZED", "BALANCED", "LIFESTYLE_OPTIMIZED");
            assertThat(candidates).allSatisfy(candidate -> {
                var geometry = candidate.geometry();
                assertThat(candidate.hardViolations()).isNotEmpty()
                        .allMatch(value -> value.startsWith("Programme gap:"));
                assertThat(candidate.hardViolations())
                        .contains("Programme gap: " + (floors == 1 ? 0 : 1)
                                + " of 3 recommended attached bathrooms represented");
                assertThat(candidate.hardViolations())
                        .contains("Programme gap: placed built-up area " + candidate.builtUpArea()
                                + " sq ft is outside recommended 2400-2800 sq ft cost basis");
                if (floors == 1) {
                    assertThat(candidate.hardViolations())
                            .contains("Programme gap: recommended family lounge is not represented")
                            .contains("Programme gap: recommended future-expansion zone is not represented");
                }
                assertThat(engine.validate(40, 60, geometry.rooms())).isEmpty();
                assertThat(engine.validateDocument(floors, geometry.rooms(), geometry.doors(), geometry.windows()))
                        .isEmpty();
                assertThat(geometry.rooms()).hasSizeGreaterThanOrEqualTo(8 * floors);
                assertThat(geometry.doors()).hasSize(geometry.rooms().size() - floors + 1);
                assertThat(geometry.windows()).isNotEmpty().hasSizeLessThanOrEqualTo(geometry.rooms().size());
                assertThat(geometry.rooms()).extracting(RoomGeometry::id).doesNotHaveDuplicates();
                assertThat(geometry.doors()).extracting(opening -> opening.get("id")).doesNotHaveDuplicates();
                assertThat(geometry.windows()).extracting(opening -> opening.get("id")).doesNotHaveDuplicates();
                var roomIds = geometry.rooms().stream().map(RoomGeometry::id).toList();
                assertThat(geometry.doors().stream().map(opening -> String.valueOf(opening.get("roomId"))))
                        .allMatch(roomIds::contains);
                assertThat(geometry.rooms().stream().map(RoomGeometry::floor).distinct())
                        .containsExactlyElementsOf(List.of("GROUND", "FIRST", "SECOND").subList(0, floors));
                for (var floor : List.of("GROUND", "FIRST", "SECOND").subList(0, floors)) {
                    assertThat(geometry.rooms().stream().filter(room -> floor.equals(room.floor())))
                            .hasSizeGreaterThanOrEqualTo(8);
                }
                assertThat(geometry.rooms().stream().filter(room -> room.type().contains("BEDROOM")))
                        .hasSize(recommendation().bedrooms());
                assertThat(geometry.rooms()).anySatisfy(room -> {
                    assertThat(room.floor()).isEqualTo("GROUND");
                    assertThat(room.type()).isEqualTo("SENIOR_BEDROOM");
                });
                assertThat(geometry.rooms().stream().filter(room -> room.type().equals("STAIRCASE")))
                        .hasSize(floors);
                assertThat(geometry.rooms().stream().filter(room -> room.type().equals("STAIRCASE"))
                        .map(room -> List.of(room.x(), room.y(), room.width(), room.length())).distinct())
                        .hasSize(1);
                if (floors == 1) {
                    assertThat(geometry.rooms()).extracting(RoomGeometry::type)
                            .contains("PARKING", "LIVING_ROOM", "DINING", "KITCHEN", "BATHROOM");
                }
                assertThat(candidate.versions())
                        .containsEntry("generator", "AVAS deterministic layout engine")
                        .containsEntry("generationModel", "No generative AI model")
                        .containsEntry("geometrySchemaVersion", "multi-floor-1")
                        .containsEntry("requestedFloors", String.valueOf(floors))
                        .containsEntry("roadFacing", "NORTH")
                        .containsEntry("strategyId", candidate.strategy())
                        .containsKeys("optimizerSeed", "ruleVersion", "strategyVersion");
            });

            assertThat(candidates).allSatisfy(candidate -> assertThat(candidate.builtUpArea()).isEqualTo(
                    (int) Math.round(candidate.geometry().rooms().stream().mapToDouble(RoomGeometry::area).sum())));
            var balanced = candidates.get(1);
            if (floors > 1) {
                assertThat(widthOf(balanced, "GROUND", 1)).isNotEqualTo(widthOf(balanced, "FIRST", 1));
            }
            var repeated = engine.generate("project-" + floors, 3, details(40, floors),
                    recommendation(), versions()).getFirst();
            assertThat(repeated.geometry().rooms()).extracting(RoomGeometry::id)
                    .containsExactlyElementsOf(candidates.getFirst().geometry().rooms().stream()
                            .map(RoomGeometry::id).toList());
            assertThat(repeated.geometry().doors()).extracting(opening -> opening.get("id"))
                    .containsExactlyElementsOf(candidates.getFirst().geometry().doors().stream()
                            .map(opening -> opening.get("id")).toList());
        }
    }

    @Test
    void routesNarrowPlotsToExpertReview() {
        var candidates = engine.generate("narrow", 1, details(18, 2), recommendation(), versions());
        assertThat(candidates).allSatisfy(candidate -> assertThat(candidate.status()).isEqualTo("EXPERT_REVIEW"));
    }

    @Test
    void rejectsOrphanedWrongFloorAndOffPerimeterOpenings() {
        var drawing = engine.generate("project-openings", 1, details(40, 2), recommendation(), versions()).getFirst();
        var doors = new java.util.ArrayList<>(drawing.geometry().doors());

        var orphan = new java.util.LinkedHashMap<>(doors.get(0));
        orphan.put("id", "BROKEN-ORPHAN");
        orphan.put("roomId", "missing-room");
        doors.set(0, Map.copyOf(orphan));
        var offPerimeter = new java.util.LinkedHashMap<>(doors.get(1));
        offPerimeter.put("id", "BROKEN-PERIMETER");
        offPerimeter.put("floor", "SECOND");
        offPerimeter.put("x", -10);
        offPerimeter.put("width", 100);
        doors.set(1, Map.copyOf(offPerimeter));
        var duplicate = new java.util.LinkedHashMap<>(doors.get(2));
        duplicate.put("id", "BROKEN-DUPLICATE");
        doors.add(Map.copyOf(duplicate));
        var windows = new java.util.ArrayList<>(drawing.geometry().windows());
        var internalWindow = new java.util.LinkedHashMap<>(windows.getFirst());
        var windowRoom = drawing.geometry().rooms().stream()
                .filter(room -> room.id().equals(internalWindow.get("roomId"))).findFirst().orElseThrow();
        internalWindow.put("orientation", "SOUTH");
        internalWindow.put("x", windowRoom.x() + windowRoom.width() / 2);
        internalWindow.put("y", windowRoom.y() + windowRoom.length());
        windows.set(0, Map.copyOf(internalWindow));

        assertThat(engine.validateDocument(2, drawing.geometry().rooms(), doors, windows))
                .anyMatch(value -> value.contains("references missing room"))
                .anyMatch(value -> value.contains("not on the same floor"))
                .anyMatch(value -> value.contains("invalid width"))
                .anyMatch(value -> value.contains("not contained by the referenced room perimeter"))
                .anyMatch(value -> value.contains("Duplicate door on shared room edge"))
                .anyMatch(value -> value.contains("window") && value.contains("exterior building envelope"));

        var exteriorDoor = drawing.geometry().doors().stream()
                .filter(opening -> opening.get("connectsRoomId") == null)
                .findFirst().orElseThrow();
        var collidingWindow = new java.util.LinkedHashMap<>(drawing.geometry().windows().getFirst());
        collidingWindow.put("id", "BROKEN-DOOR-WINDOW-COLLISION");
        collidingWindow.put("roomId", exteriorDoor.get("roomId"));
        collidingWindow.put("floor", exteriorDoor.get("floor"));
        collidingWindow.put("orientation", exteriorDoor.get("orientation"));
        collidingWindow.put("x", exteriorDoor.get("x"));
        collidingWindow.put("y", exteriorDoor.get("y"));
        collidingWindow.put("width", 2.5);
        collidingWindow.remove("swing");
        var collidingWindows = new java.util.ArrayList<>(drawing.geometry().windows());
        collidingWindows.set(0, Map.copyOf(collidingWindow));
        assertThat(engine.validateDocument(2, drawing.geometry().rooms(), drawing.geometry().doors(),
                collidingWindows)).anyMatch(value -> value.contains("overlaps window")
                        && value.contains("same wall"));
    }

    @Test
    void keepsGeneratedDoorsAndWindowsSeparatedForEveryRoadFacing() {
        for (var facing : Facing.values()) {
            var drawing = engine.generate("project-facing-" + facing, 1, details(40, 2, facing),
                    recommendation(), versions()).getFirst();
            assertThat(engine.validateDocument(2, drawing.geometry().rooms(), drawing.geometry().doors(),
                    drawing.geometry().windows())).isEmpty();
        }
    }

    @Test
    void rejectsMissingDuplicateAndMisalignedStairCores() {
        var drawing = engine.generate("project-stairs", 1, details(40, 3), recommendation(), versions()).getFirst();
        var geometry = drawing.geometry();

        var missing = geometry.rooms().stream().map(room -> room.floor().equals("GROUND")
                        && room.type().equals("STAIRCASE") ? withType(room, "STORAGE") : room)
                .toList();
        assertThat(engine.validateDocument(3, missing, geometry.doors(), geometry.windows()))
                .anyMatch(value -> value.contains("GROUND floor requires exactly one STAIRCASE; found 0"));

        var duplicate = geometry.rooms().stream().map(room -> room.id().equals("F1-R1")
                        ? withType(room, "STAIRCASE") : room)
                .toList();
        assertThat(engine.validateDocument(3, duplicate, geometry.doors(), geometry.windows()))
                .anyMatch(value -> value.contains("FIRST floor requires exactly one STAIRCASE; found 2"));

        var moved = geometry.rooms().stream().map(room -> room.floor().equals("GROUND")
                        && room.type().equals("STAIRCASE")
                        ? new RoomGeometry(room.id(), room.type(), room.x() + 1, room.y(), room.width(),
                                room.length(), room.area(), room.floor())
                        : room)
                .toList();
        assertThat(engine.validateDocument(3, moved, geometry.doors(), geometry.windows()))
                .anyMatch(value -> value.contains("Stair cores are not vertically aligned"));
    }

    private RoomGeometry withType(RoomGeometry room, String type) {
        return new RoomGeometry(room.id(), type, room.x(), room.y(), room.width(), room.length(), room.area(),
                room.floor());
    }

    private double widthOf(DrawingCandidate drawing, String floor, int roomNumber) {
        return drawing.geometry().rooms().stream()
                .filter(room -> room.id().equals(("GROUND".equals(floor) ? "G" : "F1") + "-R" + roomNumber))
                .findFirst().orElseThrow().width();
    }

    private BasicDetailsRequest details(double width, int floors) {
        return details(width, floors, Facing.NORTH);
    }

    private BasicDetailsRequest details(double width, int floors, Facing facing) {
        return new BasicDetailsRequest(width, 60, facing, "Jaipur", floors, 7_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 1, true), List.of("Natural light"));
    }

    private Recommendation recommendation() {
        return new Recommendation("rec-1", "Four-bedroom home", "PREMIUM", 4, 3, 1, 1,
                2400, 2800, 6_300_000, 7_300_000, true, true, true, 92,
                List.of("Family brief"), Map.of("rule", "test"), true);
    }

    private Map<String, String> versions() {
        return Map.of("ruleVersion", "test-rules", "strategyVersion", "test-strategy");
    }
}

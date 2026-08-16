package com.avas.platform.project;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                var areaGap = "Programme gap: placed built-up area " + candidate.builtUpArea()
                        + " sq ft is outside recommended 2400-2800 sq ft cost basis";
                if (candidate.builtUpArea() < 2400 * .98 || candidate.builtUpArea() > 2800 * 1.02) {
                    assertThat(candidate.hardViolations()).contains(areaGap);
                } else {
                    assertThat(candidate.hardViolations()).doesNotContain(areaGap);
                }
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
                        .hasSize(floors == 1 ? 0 : floors);
                if (floors > 1) {
                    assertThat(geometry.rooms().stream().filter(room -> room.type().equals("STAIRCASE"))
                            .map(room -> List.of(room.x(), room.y(), room.width(), room.length())).distinct())
                            .hasSize(1);
                }
                if (floors == 1) {
                    assertThat(geometry.rooms()).extracting(RoomGeometry::type)
                            .contains("PARKING", "LIVING_ROOM", "DINING", "KITCHEN", "BATHROOM", "STORE")
                            .doesNotContain("STAIRCASE", "LIFT_SHAFT");
                }
                assertThat(candidate.versions())
                        .containsEntry("generator", "AVAS deterministic layout engine")
                        .containsEntry("generationModel", "No generative AI model")
                        .containsEntry("geometrySchemaVersion", GeometryEngine.GEOMETRY_SCHEMA_VERSION)
                        .containsEntry("requestedFloors", String.valueOf(floors))
                        .containsEntry("roadFacing", "NORTH")
                        .containsEntry("strategyId", candidate.strategy())
                        .containsKeys("optimizerSeed", "ruleVersion", "strategyVersion");
            });

            assertThat(candidates).allSatisfy(candidate -> assertThat(candidate.builtUpArea()).isEqualTo(
                    (int) Math.round(candidate.geometry().rooms().stream()
                            .filter(room -> !List.of("PARKING", "COURTYARD_PARKING", "COURTYARD",
                                    "OPEN_SPACE", "TERRACE").contains(room.type()))
                            .mapToDouble(RoomGeometry::area).sum())));
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
        // Move the window onto a wall this room shares with its neighbours rather than one on the
        // building envelope, which is the case the validator has to reject.
        var floorRooms = drawing.geometry().rooms().stream()
                .filter(room -> windowRoom.floor().equals(room.floor())).toList();
        record Wall(String orientation, double x, double y, boolean onEnvelope) {}
        var interiorWall = java.util.List.of(
                new Wall("NORTH", windowRoom.x() + windowRoom.width() / 2,
                        windowRoom.y() + windowRoom.length(),
                        onExtreme(floorRooms, windowRoom.y() + windowRoom.length(), "MAX_Y")),
                new Wall("SOUTH", windowRoom.x() + windowRoom.width() / 2, windowRoom.y(),
                        onExtreme(floorRooms, windowRoom.y(), "MIN_Y")),
                new Wall("EAST", windowRoom.x() + windowRoom.width(),
                        windowRoom.y() + windowRoom.length() / 2,
                        onExtreme(floorRooms, windowRoom.x() + windowRoom.width(), "MAX_X")),
                new Wall("WEST", windowRoom.x(), windowRoom.y() + windowRoom.length() / 2,
                        onExtreme(floorRooms, windowRoom.x(), "MIN_X")))
                .stream().filter(wall -> !wall.onEnvelope()).findFirst().orElseThrow();
        internalWindow.put("orientation", interiorWall.orientation());
        internalWindow.put("x", interiorWall.x());
        internalWindow.put("y", interiorWall.y());
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

    @Test
    void persistsTypedAiParameterProvenanceAndAlignsRequestedLiftShaft() {
        var base = details(40, 2, Facing.NORTH);
        var details = new BasicDetailsRequest(base.plotWidth(), base.plotLength(), base.roadFacing(),
                base.city(), base.floors(), base.budget(), base.category(), base.family(),
                base.preferences(), new HomeParameters("DUPLEX", "U_SHAPED", "PASSENGER", 2,
                        true, true, true, 2, true, true));
        var parameters = PlanningParameterSet.deterministic(details, null);

        var drawing = engine.generate("project-parameters", 1, details, recommendation(), versions(),
                parameters).get(1);

        assertThat(drawing.geometry().rooms()).filteredOn(room -> room.type().equals("LIFT_SHAFT"))
                .hasSize(2).extracting(RoomGeometry::floor).containsExactly("GROUND", "FIRST");
        assertThat(drawing.geometry().rooms()).filteredOn(room -> room.type().equals("BALCONY"))
                .hasSize(2);
        assertThat(drawing.geometry().rooms()).extracting(RoomGeometry::type)
                .contains("TERRACE", "COURTYARD_PARKING");
        assertThat(engine.validateDocument(2, drawing.geometry().rooms(), drawing.geometry().doors(),
                drawing.geometry().windows())).isEmpty();
        assertThat(drawing.versions())
                .containsEntry("parameterProvider", "DETERMINISTIC")
                .containsEntry("staircaseType", "U_SHAPED")
                .containsEntry("liftProvision", "PASSENGER")
                .containsEntry("parameterSchemaVersion", "home-parameters-1");
    }

    @Test
    void preservesExplicitNoLiftBalconyTerraceAndCourtyardAcrossVariants() {
        var base = details(40, 3, Facing.NORTH);
        var explicit = new BasicDetailsRequest(base.plotWidth(), base.plotLength(), base.roadFacing(),
                base.city(), base.floors(), base.budget(), base.category(), base.family(),
                base.preferences(), new HomeParameters("MULTI_STOREY", "DOG_LEGGED", "NONE", 0,
                        false, false, false, 1, false, false));
        var parameters = PlanningParameterSet.deterministic(explicit, null);

        assertThat(parameters.variants()).allSatisfy(variant -> {
            assertThat(variant.liftProvision()).isEqualTo("NONE");
            assertThat(variant.balconyCount()).isZero();
            assertThat(variant.terraceRequired()).isFalse();
            assertThat(variant.courtyardRequired()).isFalse();
            assertThat(variant.roomTargets()).noneMatch(target -> List.of(
                    "LIFT_SHAFT", "BALCONY", "TERRACE", "COURTYARD").contains(target.roomType()));
        });

        var drawing = engine.generate("project-explicit-none", 1, explicit, recommendation(), versions(),
                parameters).get(2);
        assertThat(drawing.geometry().rooms()).noneMatch(room -> List.of(
                "LIFT_SHAFT", "BALCONY", "TERRACE", "COURTYARD", "COURTYARD_PARKING")
                .contains(room.type()));
        assertThat(drawing.versions())
                .containsEntry("liftProvision", "NONE")
                .containsEntry("balconyCount", "0")
                .containsEntry("terraceRequired", "false")
                .containsEntry("courtyardRequired", "false");
    }

    @Test
    void singleStoreyParameterProgramDoesNotRequestVerticalCirculation() {
        var details = details(40, 1, Facing.SOUTH);

        var parameters = PlanningParameterSet.deterministic(details, null);

        assertThat(parameters.variants()).allSatisfy(variant -> assertThat(variant.roomTargets())
                .noneMatch(target -> target.roomType().equals("STAIRCASE")
                        || target.roomType().equals("LIFT_SHAFT")));
    }

    @Test
    void recordsLiveOpenAiParameterProvenanceWithoutClaimingAiGeneratedGeometry() {
        var details = details(40, 2, Facing.NORTH);
        var deterministic = PlanningParameterSet.deterministic(details, null);
        var openAi = new PlanningParameterSet("openai-request-1", "OPENAI", "gpt-5.4-mini",
                "home-parameters-1.0.0", "home-parameters-1", false, List.of(),
                deterministic.variants());

        var drawing = engine.generate("project-openai-parameters", 1, details, recommendation(), versions(),
                openAi).getFirst();

        assertThat(drawing.versions())
                .containsEntry("parameterProvider", "OPENAI")
                .containsEntry("parameterRequestId", "openai-request-1")
                .containsEntry("generationModel", "gpt-5.4-mini")
                .containsEntry("generationMode", "AI_PARAMETER_ASSISTED_DETERMINISTIC_GEOMETRY")
                .containsEntry("generator", "AVAS deterministic layout engine");
    }

    @Test
    void derivesParkingCapacityFromUsableDimensionsNotAreaAlone() {
        var normal = engine.generate("parking-normal", 1, details(40, 2), recommendation(), versions())
                .get(1);
        assertThat(normal.hardViolations()).noneMatch(value -> value.contains("parking bays represented"));

        // 24 x 200 ft stays buildable after setbacks but leaves a 14 ft footprint, so the parking
        // strip is too narrow for an 8 x 16 ft bay however much area it accumulates lengthwise.
        var narrowDetails = new BasicDetailsRequest(24, 200, Facing.NORTH, "Jaipur", 2, 7_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 1, true), List.of("Natural light"));
        var narrow = engine.generate("parking-narrow", 1, narrowDetails, recommendation(), versions()).get(1);
        assertThat(narrow.hardViolations()).anyMatch(value -> value.contains("parking bays represented"));
    }

    @Test
    void plotWithNoBuildableFootprintIsRejectedInsteadOfIgnoringSetbacks() {
        // A 10 ft frontage cannot absorb the assumed side setbacks. Packing rooms across the full
        // width anyway would silently produce an illegal layout, so generation must refuse.
        var unbuildable = new BasicDetailsRequest(10, 180, Facing.NORTH, "Jaipur", 2, 7_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 1, true), List.of("Natural light"));

        assertThatThrownBy(() -> engine.generate("unbuildable", 1, unbuildable, recommendation(), versions()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expert review");
    }

    @Test
    void everyCandidateKeepsBuiltUpRoomsInsideTheSetbackEnvelope() {
        for (var width : List.of(24d, 30d, 40d, 55d)) {
            for (var floors : List.of(1, 2, 3)) {
                var details = new BasicDetailsRequest(width, 60, Facing.NORTH, "Jaipur", floors,
                        7_000_000, Category.PREMIUM, new FamilyDetails(2, 2, 1, true), List.of("Garden"));
                var envelope = BuildableEnvelope.derive(details.boundary(),
                        SetbackRule.assumedFor(details.plotArea(), floors), floors);

                for (var candidate : engine.generate("envelope-" + width + "-" + floors, 1, details,
                        recommendation(), versions(), null, envelope)) {
                    assertThat(engine.validateEnvelope(envelope, candidate.geometry().rooms()))
                            .as("setback encroachment for %s ft x 60 ft, %s floor(s)", width, floors)
                            .isEmpty();
                    assertThat(engine.validate(width, 60, candidate.geometry().rooms()))
                            .as("overlap or boundary escape for %s ft x 60 ft, %s floor(s)", width, floors)
                            .isEmpty();
                }
            }
        }
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

    /** True when a wall line sits on the named extreme of the floor's building envelope. */
    private boolean onExtreme(List<RoomGeometry> rooms, double wallLine, String extreme) {
        var value = switch (extreme) {
            case "MAX_Y" -> rooms.stream().mapToDouble(room -> room.y() + room.length()).max().orElseThrow();
            case "MIN_Y" -> rooms.stream().mapToDouble(RoomGeometry::y).min().orElseThrow();
            case "MAX_X" -> rooms.stream().mapToDouble(room -> room.x() + room.width()).max().orElseThrow();
            default -> rooms.stream().mapToDouble(RoomGeometry::x).min().orElseThrow();
        };
        return Math.abs(wallLine - value) <= .02;
    }

    @Test
    void parksCarsOnTheApproachAndKeepsThatAreaOutOfTheBuiltUpTotal() {
        var parameters = new HomeParameters("DUPLEX", "DOG_LEGGED", "NONE", 1, false, false,
                false, 2, false, false);
        var details = new BasicDetailsRequest(50, 70, Facing.SOUTH, "Jaipur", 2, 9_000_000,
                Category.LUXURY, new FamilyDetails(2, 2, 1, true), List.of("Garden"), parameters);

        var candidate = engine.generate("project-site", 1, details, recommendation(), versions()).getFirst();
        var site = candidate.geometry().siteElements();

        var parking = site.stream().filter(element -> element.type().equals("OUTDOOR_PARKING")).findFirst();
        assertThat(parking).isPresent();
        assertThat(parking.get().label()).isEqualTo("2 car open parking");
        assertThat(parking.get().area()).isGreaterThan(200);
        assertThat(site).anyMatch(element -> element.type().equals("GARDEN"));

        // The whole point of parking outside: it is plot, not slab, so it must not reach the
        // built-up figure the customer is quoted on.
        var indoorParking = candidate.geometry().rooms().stream()
                .filter(room -> room.type().contains("PARKING"))
                .mapToDouble(RoomGeometry::area).sum();
        var roomArea = candidate.geometry().rooms().stream().mapToDouble(RoomGeometry::area).sum();
        assertThat(candidate.builtUpArea()).isLessThanOrEqualTo((int) Math.round(roomArea - indoorParking) + 1);
        assertThat(site).allSatisfy(element -> assertThat(element.area()).isGreaterThan(0));
    }

    @Test
    void leavesTheOpenGroundUnplannedWhenTheFinishDoesNotCarryIt() {
        var parameters = new HomeParameters("DUPLEX", "DOG_LEGGED", "NONE", 1, false, false,
                false, 0, false, false);
        var details = new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur", 2, 5_000_000,
                Category.STANDARD, new FamilyDetails(2, 2, 0, false), List.of("Natural light"), parameters);

        var candidate = engine.generate("project-standard", 1, details, recommendation(), versions()).getFirst();

        assertThat(candidate.geometry().siteElements())
                .noneMatch(element -> element.type().equals("GARDEN"))
                .noneMatch(element -> element.type().equals("OUTDOOR_PARKING"));
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

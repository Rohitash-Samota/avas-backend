package com.avas.platform.project;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

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
                // Geometry, envelope and document rules must all pass. Anything left is a programme
                // gap: something the brief asked for that this plot genuinely cannot carry.
                assertThat(candidate.hardViolations()).allMatch(value -> value.startsWith("Programme gap:"));
                // Two storeys of this plate carry the whole brief; one storey cannot, and says so
                // rather than drawing a bedroom nobody could sleep in.
                assertThat(candidate.hardViolations())
                        .filteredOn(value -> value.contains("attached bathrooms represented"))
                        .hasSize(floors == 1 ? 1 : 0);
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
                        .hasSizeBetween(floors == 1 ? 3 : recommendation().bedrooms(),
                                recommendation().bedrooms());
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
                            .contains("PARKING", "LIVING_ROOM", "KITCHEN", "BATHROOM", "CORRIDOR")
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
                .anyMatch(value -> value.contains("window")
                        && value.contains("neither the outside nor a light well"));

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
        var unbuildable = withPlotUsage(new BasicDetailsRequest(10, 180, Facing.NORTH, "Jaipur", 2,
                7_000_000, Category.PREMIUM, new FamilyDetails(2, 2, 1, true),
                List.of("Natural light")), HomeParameters.STANDARD_SETBACK);

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
                false, 2, false, false, HomeParameters.STANDARD_SETBACK);
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
                false, 0, false, false, HomeParameters.STANDARD_SETBACK);
        var details = new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur", 2, 5_000_000,
                Category.STANDARD, new FamilyDetails(2, 2, 0, false), List.of("Natural light"), parameters);

        var candidate = engine.generate("project-standard", 1, details, recommendation(), versions()).getFirst();

        assertThat(candidate.geometry().siteElements())
                .noneMatch(element -> element.type().equals("GARDEN"))
                .noneMatch(element -> element.type().equals("OUTDOOR_PARKING"));
    }

    @Test
    void fullPlotUsageWaivesTheSetbackRingAndPlansNoOpenGround() {
        var candidates = engine.generate("project-full", 1, details(40, 2, Facing.NORTH,
                HomeParameters.FULL_PLOT), recommendation(), versions());

        assertThat(candidates).isNotEmpty().allSatisfy(candidate -> {
            var geometry = candidate.geometry();
            assertThat(geometry.setbacks().source()).isEqualTo(SetbackRule.WAIVED);
            assertThat(geometry.setbacks().maximum()).isZero();
            // The buildable outline is the plot outline: there is no ring to inset by.
            assertThat(geometry.buildableArea()).isEqualTo(geometry.plotArea());
            assertThat(geometry.buildableOutline()).isEqualTo(geometry.plotOutline());
            assertThat(geometry.siteElements()).isEmpty();
            assertThat(candidate.versions()).containsEntry("plotUsage", HomeParameters.FULL_PLOT)
                    .containsEntry("setbacksWaived", "true");
            assertThat(candidate.softRecommendations())
                    .anyMatch(note -> note.contains("open-space rule must be confirmed"));
        });

        // The point of the choice: more of the plot reaches the family as rooms.
        var inset = engine.generate("project-inset", 1, details(40, 2, Facing.NORTH,
                HomeParameters.STANDARD_SETBACK), recommendation(), versions());
        assertThat(candidates.getFirst().builtUpArea())
                .isGreaterThan(inset.getFirst().builtUpArea());
    }

    /** An L-shaped 50 x 60 plot with a 20 x 20 bite out of the north-east corner. */
    private static PlotBoundary lShapedPlot() {
        return new PlotBoundary(List.of(
                PlotVertex.of(0, 0), PlotVertex.of(50, 0), PlotVertex.of(50, 40),
                PlotVertex.of(30, 40), PlotVertex.of(30, 60), PlotVertex.of(0, 60)), Facing.SOUTH);
    }

    private BasicDetailsRequest irregularDetails(PlotBoundary plot, String plotUsage) {
        var brief = new BasicDetailsRequest(plot.bounds().width(), plot.bounds().length(),
                plot.roadFacing(), "Jaipur", 2, 9_000_000, Category.PREMIUM,
                new FamilyDetails(2, 2, 1, true), List.of("Natural light"), null, plot);
        return withPlotUsage(brief, plotUsage);
    }

    @Test
    void fullPlotUsagePlansTheGroundTheInscribedRectangleCannotReach() {
        var plot = lShapedPlot();
        var inset = BuildableEnvelope.derive(plot, SetbackRule.none(), 2);

        // The packer alone reaches only the largest rectangle inside the L.
        assertThat(inset.footprintArea()).isLessThan(inset.plotArea() * .8);
        // Extension zones recover the leg, so the whole plot becomes plannable.
        assertThat(inset.extensionZones()).isNotEmpty();
        assertThat(inset.plannableArea()).isEqualTo(inset.plotArea());
        assertThat(inset.notes()).anyMatch(note -> note.contains("extension zone"));

        var candidate = engine.generate("l-shaped", 1, irregularDetails(plot, HomeParameters.FULL_PLOT),
                recommendation(), versions(), null, inset).getFirst();
        var ground = candidate.geometry().rooms().stream()
                .filter(room -> "GROUND".equals(room.floor())).toList();

        // Rooms stand in the leg, and every one of them is still a rectangle inside the outline.
        assertThat(ground).anyMatch(room -> room.y() >= 40);
        assertThat(candidate.hardViolations()).allMatch(value -> value.startsWith("Programme gap:"));
        assertThat(engine.validateEnvelope(inset, candidate.geometry().rooms())).isEmpty();
        // Every storey carries the leg; one that stopped short would overhang open air.
        assertThat(candidate.geometry().rooms()).filteredOn(room -> "FIRST".equals(room.floor()))
                .anyMatch(room -> room.y() >= 40);
    }

    @Test
    void groundTooNarrowForARoomBecomesDepthOnTheRoomsFacingIt() {
        // The leftover leg here is 5 ft wide: never a room, but real floor the customer asked for.
        var plot = new PlotBoundary(List.of(
                PlotVertex.of(0, 0), PlotVertex.of(40, 0), PlotVertex.of(40, 40),
                PlotVertex.of(35, 40), PlotVertex.of(35, 60), PlotVertex.of(0, 60)), Facing.SOUTH);
        var envelope = BuildableEnvelope.derive(plot, SetbackRule.none(), 2);
        assertThat(envelope.extensionZones())
                .anyMatch(zone -> Math.min(zone.width(), zone.length()) < 7);

        var candidate = engine.generate("narrow-leg", 1,
                irregularDetails(plot, HomeParameters.FULL_PLOT), recommendation(), versions(),
                null, envelope).getFirst();
        var ground = candidate.geometry().rooms().stream()
                .filter(room -> "GROUND".equals(room.floor())).toList();

        // Rooms reached the boundary the strip ran along, without leaving the plot to do it.
        assertThat(ground).anyMatch(room -> Math.abs(room.x() + room.width() - 40) < .05);
        assertThat(engine.validateEnvelope(envelope, candidate.geometry().rooms())).isEmpty();
        // A room whose wall the strip only partly covered stayed where it was, so nothing overlaps.
        assertThat(candidate.hardViolations()).allMatch(value -> value.startsWith("Programme gap:"));
    }

    @Test
    void slantedBoundariesKeepTheirMarginRatherThanSteppingRoomsAlongIt() {
        // Tapered plot: both long edges run diagonally, and no rectangle can follow them.
        var plot = new PlotBoundary(List.of(PlotVertex.of(0, 0), PlotVertex.of(40, 0),
                PlotVertex.of(32, 60), PlotVertex.of(8, 60)), Facing.SOUTH);
        var envelope = BuildableEnvelope.derive(plot, SetbackRule.none(), 2);

        // Narrow strips beside a diagonal would land at stepped depths, so they are not taken.
        assertThat(envelope.extensionZones())
                .allMatch(zone -> Math.min(zone.width(), zone.length()) >= 7);
    }

    /**
     * A tapered plot is the case rectangles alone cannot finish: the packed rectangle reaches only
     * three quarters of it, and the rest is two wedges down the sides.
     */
    @Test
    void roomsAgainstASlantedBoundaryFollowItSoTheWholePlotIsUsed() {
        var plot = new PlotBoundary(List.of(PlotVertex.of(0, 0), PlotVertex.of(40, 0),
                PlotVertex.of(32, 60), PlotVertex.of(8, 60)), Facing.SOUTH);
        var envelope = BuildableEnvelope.derive(plot, SetbackRule.none(), 2);
        assertThat(envelope.footprintArea()).isLessThan(envelope.plotArea() * .8);

        var candidate = engine.generate("tapered", 1,
                irregularDetails(plot, HomeParameters.FULL_PLOT), recommendation(), versions(),
                null, envelope).getFirst();
        var ground = candidate.geometry().rooms().stream()
                .filter(room -> "GROUND".equals(room.floor())).toList();

        // The storey now covers the plot, and nothing crosses the boundary to do it.
        assertThat(ground.stream().mapToDouble(RoomGeometry::area).sum())
                .isCloseTo(envelope.plotArea(), within(1d));
        assertThat(engine.validateEnvelope(envelope, candidate.geometry().rooms())).isEmpty();
        assertThat(candidate.hardViolations()).allMatch(value -> value.startsWith("Programme gap:"));

        var shaped = ground.stream().filter(RoomGeometry::shaped).toList();
        assertThat(shaped).isNotEmpty();
        assertThat(shaped).allSatisfy(room -> {
            // Counter-clockwise, which is what the massing view reads wall directions from.
            assertThat(PlotGeometry.signedArea(room.outline())).isPositive();
            // The bounding box still describes the room, and the area is the room's own, not the box.
            var box = PlotGeometry.bounds(room.outline());
            assertThat(room.x()).isCloseTo(box.minimumX(), within(.02));
            assertThat(room.y()).isCloseTo(box.minimumY(), within(.02));
            assertThat(room.area()).isLessThan(room.width() * room.length());
            assertThat(room.outline()).hasSizeGreaterThanOrEqualTo(3);
            // No corner repeats the one before it, so no wall has zero length.
            for (var index = 0; index < room.outline().size(); index++) {
                var corner = room.outline().get(index);
                var next = room.outline().get((index + 1) % room.outline().size());
                assertThat(Math.hypot(corner.x() - next.x(), corner.y() - next.y()))
                        .as("zero-length wall in %s", room.id()).isGreaterThan(.01);
            }
        });
    }

    /**
     * A house on its boundaries is a terraced house: the flank walls are shared with the plots
     * either side, so nothing opens through them.
     */
    @Test
    void buildingToTheBoundaryPutsWindowsOnlyOnTheRoadAndRearWalls() {
        var candidate = engine.generate("terraced", 1, details(40, 2, Facing.NORTH,
                HomeParameters.FULL_PLOT), recommendation(), versions()).getFirst();
        var geometry = candidate.geometry();
        var roomsById = geometry.rooms().stream()
                .collect(java.util.stream.Collectors.toMap(RoomGeometry::id, room -> room));

        assertThat(geometry.windows()).isNotEmpty();
        assertThat(geometry.windows()).allSatisfy(window -> {
            var orientation = String.valueOf(window.get("orientation"));
            var room = roomsById.get(String.valueOf(window.get("roomId")));
            var onFloor = geometry.rooms().stream()
                    .filter(other -> java.util.Objects.equals(other.floor(), room.floor())).toList();
            var envelope = envelopeOf(onFloor);
            var onFlank = ("EAST".equals(orientation)
                            && Math.abs(room.x() + room.width() - envelope[1]) <= .02)
                    || ("WEST".equals(orientation) && Math.abs(room.x() - envelope[0]) <= .02);
            // A flank opening is only ever a party wall here, since the building fills the plot.
            assertThat(onFlank)
                    .as("window %s opens through the party wall on the %s boundary",
                            window.get("id"), orientation)
                    .isFalse();
        });

        assertThat(candidate.softRecommendations())
                .anyMatch(note -> note.contains("party walls"));
        // Openings are still validated, so nothing was bought by loosening the rule.
        assertThat(candidate.hardViolations()).allMatch(value -> value.startsWith("Programme gap:"));
    }

    /** Minimum and maximum x of one storey, as {minX, maxX}. */
    private static double[] envelopeOf(List<RoomGeometry> rooms) {
        return new double[] {
                rooms.stream().mapToDouble(RoomGeometry::x).min().orElse(0),
                rooms.stream().mapToDouble(room -> room.x() + room.width()).max().orElse(0)};
    }

    @Test
    void squarePlotsKeepEveryRoomRectangular() {
        var candidate = engine.generate("square", 1, details(40, 2, Facing.NORTH,
                HomeParameters.FULL_PLOT), recommendation(), versions()).getFirst();

        // Nothing to follow, so no room carries an outline and every consumer reads the rectangle.
        assertThat(candidate.geometry().rooms()).noneMatch(RoomGeometry::shaped);
    }

    @Test
    void openSpaceUsageDrawsAGardenTheFinishTierWouldNotHaveBought() {
        var parameters = new HomeParameters("DUPLEX", "DOG_LEGGED", "NONE", 1, false, false,
                false, 1, false, false, HomeParameters.OPEN_SPACE);
        var details = new BasicDetailsRequest(50, 70, Facing.SOUTH, "Jaipur", 2, 5_000_000,
                Category.STANDARD, new FamilyDetails(2, 2, 0, false), List.of("Garden"), parameters);

        var candidate = engine.generate("project-open", 1, details, recommendation(), versions())
                .getFirst();

        assertThat(candidate.geometry().setbacks().assumed()).isTrue();
        assertThat(candidate.geometry().siteElements())
                .anyMatch(element -> element.type().equals("GARDEN"));
    }

    /**
     * A garden on an ordinary plot, where the only ground deep enough to plant is the front setback.
     *
     * <p>The approach band used to be withheld from the garden whether or not a car ended up on it.
     * A front setback shallower than a car is wide — which is most of them — therefore produced no
     * parking *and* no garden, and "setbacks with garden" drew exactly what plain standard setbacks
     * drew. The band is only spoken for when a bay is actually placed on it.</p>
     */
    @Test
    void openSpaceDrawsAGardenWhenTheApproachIsTooShallowToParkOn() {
        var parameters = new HomeParameters("BUNGALOW", "DOG_LEGGED", "NONE", 0, false, false,
                false, 1, false, false, HomeParameters.OPEN_SPACE);
        var details = new BasicDetailsRequest(30, 60, Facing.NORTH, "Jaipur", 1, 4_000_000,
                Category.STANDARD, new FamilyDetails(2, 1, 0, false), List.of("Garden"), parameters);

        var candidate = engine.generate("project-shallow-approach", 1, details, recommendation(),
                versions()).getFirst();

        var elements = candidate.geometry().siteElements();
        // The setback ring here is 7.5 ft at the front: too shallow for the 8.5 ft a car needs
        // across, so nothing parks outside and the band is free to be planted.
        assertThat(elements).noneMatch(element -> element.type().equals("OUTDOOR_PARKING"));
        assertThat(elements).anyMatch(element -> element.type().equals("GARDEN"));
    }

    /**
     * An extension room is named for a size it can actually be furnished at.
     *
     * <p>Taking the next type off the list regardless of the ground it landed on drew a 239 sq ft
     * study and a 144 sq ft store, against catalogue caps of 170 and 90.</p>
     */
    @Test
    void extensionRoomsAreNamedForTheSizeTheyAreDrawnAt() {
        var types = List.of("FAMILY_LOUNGE", "HOME_OFFICE", "MULTIPURPOSE_ROOM", "STORE");

        // 8 x 7 = 56 sq ft: only the store's band reaches down this far.
        assertThat(engine.extensionTypeFor(new PlotGeometry.Rect(0, 0, 8, 7), types, 0))
                .isEqualTo("STORE");
        // 16 x 15 = 240 sq ft: past the store and the home office, inside lounge and multipurpose.
        assertThat(engine.extensionTypeFor(new PlotGeometry.Rect(0, 0, 16, 15), types, 0))
                .isIn("FAMILY_LOUNGE", "MULTIPURPOSE_ROOM");
        // 12 x 8 = 96 sq ft, 8 ft across: too narrow for a lounge or a multipurpose room.
        assertThat(engine.extensionTypeFor(new PlotGeometry.Rect(0, 0, 12, 8), types, 0))
                .isEqualTo("HOME_OFFICE");
        // Larger than every band: the roomiest type is the honest name, never the next in the list.
        assertThat(engine.extensionTypeFor(new PlotGeometry.Rect(0, 0, 30, 20), types, 1))
                .isEqualTo("FAMILY_LOUNGE");

        // Whatever the piece, the type it is given must be one the catalogue would draw it as.
        for (var size : List.of(new double[] {8, 7}, new double[] {16, 15}, new double[] {12, 8})) {
            var piece = new PlotGeometry.Rect(0, 0, size[0], size[1]);
            var spec = RoomSpec.of(engine.extensionTypeFor(piece, types, 0));
            assertThat(piece.area()).isBetween(spec.minArea() - .5, spec.maxArea() + .5);
            assertThat(Math.min(piece.width(), piece.length())).isGreaterThanOrEqualTo(spec.minShortSide() - .01);
        }
    }

    private BasicDetailsRequest details(double width, int floors) {
        return details(width, floors, Facing.NORTH);
    }

    /**
     * The setback options use their whole envelope too, not just the largest rectangle inside it.
     *
     * <p>On an L-shaped plot the packed rectangle reached barely two thirds of the ground the
     * setback rule actually left buildable, and the rest was drawn as lawn. The open-space choices
     * are about the ring the rule holds back; the area inside it belongs to the customer whether or
     * not one rectangle happens to cover it.</p>
     */
    @Test
    void setbackEnvelopesPlanTheBuildableGroundOneRectangleCannotReach() {
        var plot = lShapedPlot();
        var envelope = BuildableEnvelope.derive(plot, SetbackRule.assumedFor(plot, 2), 2);

        assertThat(envelope.setbacks().waived()).isFalse();
        // Ground inside the setback line that the inscribed rectangle leaves standing empty.
        assertThat(envelope.footprintArea()).isLessThan(envelope.buildableArea() * .8);
        assertThat(envelope.extensionZones()).isNotEmpty();
        assertThat(envelope.plannableArea()).isCloseTo(envelope.buildableArea(), within(1d));
        assertThat(envelope.underUsesEnvelope()).isFalse();

        var candidate = engine.generate("l-shaped-setback", 1,
                irregularDetails(plot, HomeParameters.STANDARD_SETBACK), recommendation(), versions(),
                null, envelope).getFirst();
        var rooms = candidate.geometry().rooms();

        assertThat(engine.validate(50, 60, rooms)).isEmpty();
        // Nothing crossed the setback line to get there.
        assertThat(engine.validateEnvelope(envelope, rooms)).isEmpty();
        assertThat(rooms).filteredOn(room -> "GROUND".equals(room.floor()))
                .anyMatch(room -> room.x() >= envelope.footprintX() + envelope.footprintWidth() - .05
                        || room.y() >= envelope.footprintY() + envelope.footprintLength() - .05);
        // The ring is still open ground, and only the ring.
        assertThat(candidate.geometry().siteElements()).allSatisfy(element ->
                assertThat(rooms).filteredOn(room -> "GROUND".equals(room.floor()))
                        .noneMatch(room -> element.x() < room.x() + room.width() - .01
                                && element.x() + element.width() > room.x() + .01
                                && element.y() < room.y() + room.length() - .01
                                && element.y() + element.length() > room.y() + .01));
    }

    /** A tapered plot's setback line is slanted too, and rooms follow it rather than stopping square. */
    @Test
    void roomsFollowASlantedSetbackLineWithoutCrossingIt() {
        var plot = new PlotBoundary(List.of(PlotVertex.of(0, 0), PlotVertex.of(40, 0),
                PlotVertex.of(32, 60), PlotVertex.of(8, 60)), Facing.SOUTH);
        var envelope = BuildableEnvelope.derive(plot, SetbackRule.assumedFor(plot, 2), 2);
        assertThat(envelope.slanted()).isTrue();
        assertThat(envelope.footprintArea()).isLessThan(envelope.buildableArea() * .8);

        var candidate = engine.generate("tapered-setback", 1,
                irregularDetails(plot, HomeParameters.STANDARD_SETBACK), recommendation(), versions(),
                null, envelope).getFirst();
        var ground = candidate.geometry().rooms().stream()
                .filter(room -> "GROUND".equals(room.floor())).toList();

        assertThat(ground.stream().mapToDouble(RoomGeometry::area).sum())
                .isCloseTo(envelope.buildableArea(), within(1d));
        assertThat(engine.validateEnvelope(envelope, candidate.geometry().rooms())).isEmpty();
        assertThat(engine.validate(40, 60, candidate.geometry().rooms())).isEmpty();
    }

    /**
     * A plot whose slant leaves ground two rooms can both reach, which is where the boundary pass
     * used to draw the second one straight through the first.
     *
     * <p>The corner-cut plot in the screenshots: a forty by sixty with the south-east corner taken
     * off. The upper storey's balcony had open plot to its east and to its north, so it claimed both
     * and came back a wedge four times the size a balcony is planned at — over the two extension
     * rooms already standing there.</p>
     */
    @Test
    void roomsFollowingASlantedBoundaryNeverClaimTheSameGroundTwice() {
        var plot = new PlotBoundary(List.of(
                PlotVertex.of(0, 0), PlotVertex.of(40, 0), PlotVertex.of(40, 33),
                PlotVertex.of(22, 60), PlotVertex.of(0, 60)), Facing.NORTH);
        var envelope = BuildableEnvelope.derive(plot, SetbackRule.none(), 2);

        var candidates = engine.generate("corner-cut", 1,
                irregularDetails(plot, HomeParameters.FULL_PLOT), recommendation(), versions(),
                null, envelope);

        assertThat(candidates).isNotEmpty().allSatisfy(candidate -> {
            var rooms = candidate.geometry().rooms();
            assertThat(engine.validate(40, 60, rooms))
                    .withFailMessage("rooms overlap or leave the plot: %s", engine.validate(40, 60, rooms))
                    .isEmpty();
            assertThat(engine.validateEnvelope(envelope, rooms)).isEmpty();
            // The point of full plot usage: both storeys still reach the whole outline.
            for (var floor : List.of("GROUND", "FIRST")) {
                assertThat(rooms.stream().filter(room -> floor.equals(room.floor()))
                        .mapToDouble(RoomGeometry::area).sum())
                        .isCloseTo(envelope.plotArea(), within(1d));
            }
            // A balcony is a planned size, not whatever ground happens to be left over beside it.
            assertThat(rooms).filteredOn(room -> "BALCONY".equals(room.type()))
                    .allSatisfy(room -> assertThat(room.area()).isLessThan(120d));
        });
    }

    /**
     * The shared brief, planned inside the assumed setback ring.
     *
     * <p>Plot usage is pinned rather than left to default because these expectations — which
     * programme gaps a plate of this size produces, where the open ground goes — are calibrated
     * against the setback envelope. {@link HomeParameters#FULL_PLOT} is the product default and is
     * covered by its own tests.</p>
     */
    private BasicDetailsRequest details(double width, int floors, Facing facing) {
        return details(width, floors, facing, HomeParameters.STANDARD_SETBACK);
    }

    private BasicDetailsRequest details(double width, int floors, Facing facing, String plotUsage) {
        return withPlotUsage(new BasicDetailsRequest(width, 60, facing, "Jaipur", floors, 7_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 1, true), List.of("Natural light")),
                plotUsage);
    }

    /** The same brief with every inferred parameter intact and only the plot usage overridden. */
    private static BasicDetailsRequest withPlotUsage(BasicDetailsRequest brief, String plotUsage) {
        var inferred = brief.parameters();
        return new BasicDetailsRequest(brief.plotWidth(), brief.plotLength(), brief.roadFacing(),
                brief.city(), brief.floors(), brief.budget(), brief.category(), brief.family(),
                brief.preferences(),
                new HomeParameters(inferred.homeType(), inferred.staircaseType(),
                        inferred.liftProvision(), inferred.balconyCount(), inferred.terraceRequired(),
                        inferred.courtyardRequired(), inferred.accessibleGroundFloor(),
                        inferred.parkingCars(), inferred.solarReady(), inferred.rainwaterHarvesting(),
                        plotUsage));
    }

    @Test
    void theRoomProgrammeSizesTheRoomsRatherThanOnlyAuditingThem() {
        // Until the variant reached the planner, the whole output of the parameter optimizer was
        // read in one place: an audit that compared the finished drawing against it and printed the
        // differences. Plot area, budget and household changed the programme and changed no wall.
        var details = details(40, 2, Facing.NORTH);
        var generous = engine.generate("p-generous", 1, details, recommendation(), versions(),
                parameterSet(320, 190, 60));
        var modest = engine.generate("p-modest", 1, details, recommendation(), versions(),
                parameterSet(150, 95, 60));

        assertThat(livingArea(generous)).isGreaterThan(livingArea(modest) + 20);
    }

    @Test
    void aStripGivesTheLargerShareToWhicheverRoomTheProgrammeFavours() {
        // A storey's plate is fixed, so rooms sharing a strip tile it exactly and one can only grow
        // at another's expense. What the programme controls there is the split, not the total.
        var details = details(40, 2, Facing.NORTH);
        var kitchenLed = engine.generate("p-kitchen", 1, details, recommendation(), versions(),
                parameterSet(230, 200, 32));
        var utilityLed = engine.generate("p-utility", 1, details, recommendation(), versions(),
                parameterSet(230, 70, 95));

        assertThat(areaOf(kitchenLed, "KITCHEN") / areaOf(kitchenLed, "UTILITY"))
                .as("a programme favouring the kitchen must draw it larger relative to the utility")
                .isGreaterThan(areaOf(utilityLed, "KITCHEN") / areaOf(utilityLed, "UTILITY"));
    }

    @Test
    void aProgrammeAskingForAnUnusableRoomIsClampedToWhatTheTypeIsUsableAt() {
        // An optimizer may propose anything inside its own schema. What gets drawn is still bounded
        // by the dimensions the type works at, so a 40 sq ft bedroom never reaches a customer.
        var details = details(40, 2, Facing.NORTH);
        var absurd = engine.generate("p-absurd", 1, details, recommendation(), versions(),
                parameterSet(2_400, 20, 400));

        for (var room : absurd.getFirst().geometry().rooms()) {
            var spec = RoomSpec.of(room.type());
            assertThat(room.area())
                    .as("%s was drawn at %.1f sq ft", room.type(), room.area())
                    .isBetween(spec.minArea() - 1, spec.maxArea() + 5);
        }
    }

    @Test
    void aPlannerWithoutAProgrammeDrawsExactlyWhatItDrewBefore() {
        // The fallback path every existing caller uses: no variant, so room sizes come from the
        // type catalogue and the geometry is unchanged by any of this.
        var details = details(40, 2, Facing.NORTH);
        var withoutVariant = engine.generate("p-same", 1, details, recommendation(), versions());
        var withNullSet = engine.generate("p-same", 1, details, recommendation(), versions(), null);

        assertThat(withNullSet.getFirst().geometry().rooms())
                .isEqualTo(withoutVariant.getFirst().geometry().rooms());
    }

    private double livingArea(List<DrawingCandidate> candidates) {
        return areaOf(candidates, "LIVING_ROOM");
    }

    private double areaOf(List<DrawingCandidate> candidates, String type) {
        return candidates.getFirst().geometry().rooms().stream()
                .filter(room -> type.equals(room.type())).mapToDouble(RoomGeometry::area).sum();
    }

    /** A parameter set whose three variants all ask for the given ground-floor areas. */
    private PlanningParameterSet parameterSet(double living, double kitchen, double utility) {
        var targets = List.of(
                new PlanningParameterVariant.RoomTarget("LIVING_ROOM", "GROUND", 1, living, "REQUIRED"),
                new PlanningParameterVariant.RoomTarget("KITCHEN", "GROUND", 1, kitchen, "REQUIRED"),
                new PlanningParameterVariant.RoomTarget("UTILITY", "GROUND", 1, utility, "PREFERRED"),
                new PlanningParameterVariant.RoomTarget("DINING", "GROUND", 1, 140, "REQUIRED"),
                new PlanningParameterVariant.RoomTarget("BEDROOM", "FIRST", 1, 140, "REQUIRED"),
                new PlanningParameterVariant.RoomTarget("BATHROOM", "GROUND", 1, 40, "REQUIRED"));
        var weights = Map.of("budget", .2, "functionality", .3, "daylight", .2,
                "accessibility", .15, "futureReadiness", .15);
        var variants = List.of("BUDGET_OPTIMIZED", "BALANCED", "LIFESTYLE_OPTIMIZED").stream()
                .map(strategy -> new PlanningParameterVariant(strategy, strategy + " option",
                        "Shared living below; private rooms above", "DOG_LEGGED", "NONE", 1,
                        false, false, true, 1, false, false, targets, weights,
                        List.of("Sized to the plot", "Geometry remains deterministic")))
                .toList();
        return new PlanningParameterSet(null, "DETERMINISTIC", "test-rules", "home-parameters-1.1.0",
                "home-parameters-1", false, List.of(), variants);
    }

    @Test
    void candidateScoresAreMeasuredFromTheDrawingRatherThanFixedPerStrategy() {
        // Every project ever generated scored its options 92/91/90 with the same vastu, daylight
        // and efficiency numbers, because they were constants in the strategy table. A customer
        // comparing options was reading the strategy's reputation, not their own home.
        var narrow = engine.generate("s-narrow", 1, details(22, 1, Facing.NORTH),
                recommendation(), versions());
        var wide = engine.generate("s-wide", 1, details(55, 3, Facing.EAST),
                recommendation(), versions());

        var narrowScores = narrow.stream().map(DrawingCandidate::confidence).toList();
        var wideScores = wide.stream().map(DrawingCandidate::confidence).toList();
        assertThat(narrowScores).isNotEqualTo(wideScores);

        // And the components are genuinely measured, not copied between plots.
        assertThat(narrow.getFirst().spaceEfficiencyScore())
                .isNotEqualTo(wide.getFirst().spaceEfficiencyScore());
    }

    @Test
    void everyCandidateExplainsItselfWithSomethingMeasuredOnThisPlan() {
        var candidates = engine.generate("s-explain", 1, details(40, 2, Facing.NORTH),
                recommendation(), versions());

        for (var candidate : candidates) {
            assertThat(candidate.explanations())
                    .as("%s must say what it did with this plot", candidate.strategy())
                    .anyMatch(reason -> reason.contains("sq ft this plot can be planned on"));
            assertThat(candidate.explanations())
                    .anyMatch(reason -> reason.contains("passage, stair and shaft"));
        }
    }

    @Test
    void hardViolationsAreSubtractedRatherThanAveragedAway() {
        // A layout that crosses the buildable line is not a well-rounded design with a caveat.
        var rooms = engine.generate("s-clean", 1, details(40, 2, Facing.NORTH),
                recommendation(), versions()).getFirst();
        var score = CandidateScore.measure(rooms.geometry().rooms(), List.of(), null,
                Facing.NORTH, 2);

        assertThat(score.overall(null, 0)).isGreaterThan(score.overall(null, 3));
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

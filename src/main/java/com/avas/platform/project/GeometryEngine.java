package com.avas.platform.project;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Native deterministic geometry engine. Keeping this inside the platform API makes layouts,
 * validation and workflow versioning one transactional backend concern.
 */
@Component
class GeometryEngine {
    private static final List<Strategy> STRATEGIES = List.of(
            new Strategy("BUDGET_OPTIMIZED", "Efficient Courtyard", .43, .46,
                    88, 82, 95, List.of(
                    "Compact circulation lowers the built-up area.",
                    "Wet areas share a plumbing shaft.",
                    "A regular grid reduces construction complexity.")),
            new Strategy("BALANCED", "Garden Threshold", .38, .46,
                    94, 91, 91, List.of(
                    "The senior suite is on the ground floor.",
                    "Kitchen and utility are directly connected.",
                    "The stair can support future independent access.")),
            new Strategy("LIFESTYLE_OPTIMIZED", "Lightwell House", .34, .42,
                    91, 96, 86, List.of(
                    "A central lightwell improves daylight.",
                    "Shared spaces are enlarged for regular guests.",
                    "A separate office protects private space."))
    );

    List<DrawingCandidate> generate(String projectId, int version, BasicDetailsRequest details,
            Recommendation recommendation, Map<String, String> versions) {
        return generate(projectId, version, details, recommendation, versions, null);
    }

    List<DrawingCandidate> generate(String projectId, int version, BasicDetailsRequest details,
            Recommendation recommendation, Map<String, String> versions,
            PlanningParameterSet parameterSet) {
        if (details.plotWidth() < 10 || details.plotLength() < 10) {
            throw new IllegalArgumentException("Plot dimensions must each be at least 10 feet");
        }

        var candidates = new ArrayList<DrawingCandidate>();
        for (int index = 0; index < STRATEGIES.size(); index++) {
            var strategy = STRATEGIES.get(index);
            var variant = variantFor(parameterSet, strategy.key());
            var optionParameters = optionParameters(details.parameters(), variant);
            var rooms = packRooms(details.plotWidth(), details.plotLength(), details.floors(), strategy,
                    recommendation, optionParameters, variant);
            var doors = doorsFor(rooms, details.roadFacing());
            var windows = windowsFor(rooms, doors);
            // Rooms already contain every requested floor, so this is an aggregate area and must
            // not be multiplied by the floor count a second time. Outdoor programme zones are
            // deliberately excluded: pricing them again at the full built-up rate would double
            // count roof terraces and overstate open parking/courtyard construction.
            var constructedArea = rooms.stream().filter(this::countsAsBuiltUp)
                    .mapToDouble(RoomGeometry::area).sum();
            var builtUpArea = (int) Math.round(constructedArea);
            var violations = new ArrayList<>(validate(details.plotWidth(), details.plotLength(), rooms));
            violations.addAll(validateDocument(details.floors(), rooms, doors, windows));
            var programmeGaps = programmeGaps(recommendation, rooms, builtUpArea, optionParameters, variant);
            violations.addAll(programmeGaps);
            var reviewRequired = details.plotWidth() < 20 || !violations.isEmpty();
            var provenance = new LinkedHashMap<>(versions);
            provenance.put("generator", "AVAS deterministic layout engine");
            provenance.put("generationMode", "DETERMINISTIC");
            provenance.put("generationModel", "No generative AI model");
            provenance.put("modelVersion", "not-applicable");
            provenance.put("promptVersion", "not-used");
            provenance.put("strategyId", strategy.key());
            provenance.put("geometrySchemaVersion", "multi-floor-1");
            provenance.put("requestedFloors", String.valueOf(details.floors()));
            provenance.put("roadFacing", details.roadFacing().name());
            provenance.put("optimizerSeed", Integer.toUnsignedString(
                    Objects.hash(projectId, version, strategy.key())));
            if (parameterSet != null) {
                provenance.put("parameterProvider", safe(parameterSet.provider(), "DETERMINISTIC"));
                provenance.put("parameterModel", safe(parameterSet.model(), "avas-parameter-rules-1.0.0"));
                provenance.put("promptVersion", safe(parameterSet.promptVersion(), "home-parameters-1.0.0"));
                provenance.put("parameterSchemaVersion", safe(parameterSet.schemaVersion(), "home-parameters-1"));
                provenance.put("parameterFallback", String.valueOf(parameterSet.fallbackUsed()));
                if (parameterSet.requestId() != null) provenance.put("parameterRequestId", parameterSet.requestId());
                if (parameterSet.providerRequestId() != null) {
                    provenance.put("parameterProviderRequestId", parameterSet.providerRequestId());
                }
                if (!parameterSet.warnings().isEmpty()) {
                    provenance.put("parameterWarning", String.join(" | ", parameterSet.warnings()));
                }
                if ("OPENAI".equalsIgnoreCase(parameterSet.provider()) && !parameterSet.fallbackUsed()) {
                    provenance.put("generationMode", "AI_PARAMETER_ASSISTED_DETERMINISTIC_GEOMETRY");
                    provenance.put("generationModel", parameterSet.model());
                    provenance.put("modelVersion", parameterSet.model());
                }
            }
            provenance.put("homeType", optionParameters.homeType());
            provenance.put("staircaseType", optionParameters.staircaseType());
            provenance.put("liftProvision", optionParameters.liftProvision());
            provenance.put("balconyCount", String.valueOf(optionParameters.balconyCount()));
            provenance.put("terraceRequired", String.valueOf(optionParameters.terraceRequired()));
            provenance.put("courtyardRequired", String.valueOf(optionParameters.courtyardRequired()));
            provenance.put("accessibleGroundFloor", String.valueOf(optionParameters.accessibleGroundFloor()));
            provenance.put("parkingCars", String.valueOf(optionParameters.parkingCars()));
            provenance.put("solarReady", String.valueOf(optionParameters.solarReady()));
            provenance.put("rainwaterHarvesting", String.valueOf(optionParameters.rainwaterHarvesting()));
            if (variant != null) {
                provenance.put("parameterVariantTitle", variant.title());
                provenance.put("duplexZoning", variant.duplexZoning());
            }

            candidates.add(new DrawingCandidate(
                    "drawing-" + projectId + "-v" + version + "-" + (index + 1),
                    projectId,
                    version,
                    strategy.key(),
                    variant == null || variant.title() == null ? strategy.name() : variant.title(),
                    builtUpArea,
                    recommendation.estimatedCostLow(),
                    recommendation.estimatedCostHigh(),
                    strategy.vastuScore(),
                    strategy.naturalLightScore(),
                    strategy.spaceEfficiencyScore(),
                    92 - index,
                    new GeometryDocument("FEET", details.plotWidth(), details.plotLength(), rooms,
                            doors, windows),
                    List.copyOf(violations),
                    List.of(
                            "Verify setbacks against the applicable local authority release.",
                            "A licensed structural engineer must approve the final grid."),
                    programmeExplanations(strategy, variant, programmeGaps),
                    Map.copyOf(provenance),
                    reviewRequired ? "EXPERT_REVIEW" : "SUCCESS",
                    false,
                    Instant.now()));
        }
        return List.copyOf(candidates);
    }

    private boolean countsAsBuiltUp(RoomGeometry room) {
        return switch (room.type()) {
            case "PARKING", "COURTYARD_PARKING", "COURTYARD", "OPEN_SPACE", "TERRACE" -> false;
            default -> true;
        };
    }

    private List<String> programmeGaps(Recommendation recommendation, List<RoomGeometry> rooms, int builtUpArea,
            HomeParameters parameters, PlanningParameterVariant variant) {
        var gaps = new ArrayList<String>();
        var bedrooms = rooms.stream().filter(room -> room.type().contains("BEDROOM")).count();
        var attachedBathrooms = rooms.stream().filter(room -> room.type().contains("ATTACHED_BATHROOM")).count();
        var commonBathrooms = rooms.stream().filter(room -> "BATHROOM".equals(room.type())).count();
        var parkingBays = representedParkingBays(rooms);
        if (bedrooms != recommendation.bedrooms()) {
            gaps.add("Programme gap: " + bedrooms + " of " + recommendation.bedrooms()
                    + " recommended bedrooms represented");
        }
        if (attachedBathrooms < recommendation.attachedBathrooms()) {
            gaps.add("Programme gap: " + attachedBathrooms + " of " + recommendation.attachedBathrooms()
                    + " recommended attached bathrooms represented");
        }
        if (commonBathrooms < recommendation.commonBathrooms()) {
            gaps.add("Programme gap: " + commonBathrooms + " of " + recommendation.commonBathrooms()
                    + " recommended common bathrooms represented");
        }
        if (parkingBays < recommendation.parkingCars()) {
            gaps.add("Programme gap: " + parkingBays + " of " + recommendation.parkingCars()
                    + " recommended parking bays represented");
        }
        if (recommendation.seniorCitizenBedroom()
                && rooms.stream().noneMatch(room -> room.type().contains("SENIOR_BEDROOM"))) {
            gaps.add("Programme gap: recommended ground-floor senior bedroom is not represented");
        }
        if (recommendation.familyLounge()
                && rooms.stream().noneMatch(room -> room.type().contains("FAMILY_LOUNGE"))) {
            gaps.add("Programme gap: recommended family lounge is not represented");
        }
        if (recommendation.futureExpansion()
                && rooms.stream().noneMatch(room -> room.type().contains("TERRACE")
                        || room.type().contains("BALCONY") || room.type().contains("FUTURE_EXPANSION"))) {
            gaps.add("Programme gap: recommended future-expansion zone is not represented");
        }
        // Packing and whole-square-foot display introduce small boundary differences. Treat a
        // two-percent edge variance as rounding tolerance, not a cost-basis failure.
        if (builtUpArea < recommendation.builtUpAreaMinimum() * .98
                || builtUpArea > recommendation.builtUpAreaMaximum() * 1.02) {
            gaps.add("Programme gap: placed built-up area " + builtUpArea + " sq ft is outside recommended "
                    + recommendation.builtUpAreaMinimum() + "-" + recommendation.builtUpAreaMaximum()
                    + " sq ft cost basis");
        }
        var liftCount = rooms.stream().filter(room -> "LIFT_SHAFT".equals(room.type())).count();
        if (!"NONE".equals(parameters.liftProvision()) && liftCount == 0) {
            gaps.add("Programme gap: requested " + parameters.liftProvision().toLowerCase(Locale.ROOT)
                    .replace('_', ' ') + " lift provision is not represented");
        }
        var balconies = rooms.stream().filter(room -> "BALCONY".equals(room.type())).count();
        if (balconies < parameters.balconyCount()) {
            gaps.add("Programme gap: " + balconies + " of " + parameters.balconyCount()
                    + " requested balconies represented");
        }
        if (parameters.terraceRequired()
                && rooms.stream().noneMatch(room -> "TERRACE".equals(room.type()))) {
            gaps.add("Programme gap: requested terrace is not represented");
        }
        if (parameters.courtyardRequired()
                && rooms.stream().noneMatch(room -> room.type().contains("COURTYARD"))) {
            gaps.add("Programme gap: requested courtyard is not represented");
        }
        var representedParking = representedParkingBays(rooms);
        if (representedParking < parameters.parkingCars()) {
            gaps.add("Programme gap: " + representedParking + " of " + parameters.parkingCars()
                    + " requested parking bays represented");
        }
        gaps.addAll(roomTargetGaps(rooms, variant));
        return List.copyOf(gaps);
    }

    private long representedParkingBays(List<RoomGeometry> rooms) {
        // One parking programme rectangle may intentionally hold multiple bays. Capacity must be
        // dimension-aware so a long four-foot strip can never masquerade as usable parking area.
        return rooms.stream().filter(room -> room.type().contains("PARKING"))
                .mapToLong(room -> Math.min(3, Math.max(
                        (long) Math.floor(room.width() / 8d)
                                * (long) Math.floor(room.length() / 16d),
                        (long) Math.floor(room.width() / 16d)
                                * (long) Math.floor(room.length() / 8d))))
                .sum();
    }

    private List<String> roomTargetGaps(List<RoomGeometry> rooms, PlanningParameterVariant variant) {
        if (variant == null || variant.roomTargets() == null || variant.roomTargets().isEmpty()) return List.of();
        var required = new LinkedHashMap<String, Integer>();
        for (var target : variant.roomTargets()) {
            if (!"REQUIRED".equals(target.priority())) continue;
            required.merge(target.floor() + "|" + target.roomType(), target.count(), Integer::sum);
        }
        var gaps = new ArrayList<String>();
        required.forEach((key, count) -> {
            var separator = key.indexOf('|');
            var floor = key.substring(0, separator);
            var type = key.substring(separator + 1);
            var represented = rooms.stream().filter(room -> floor.equals(normalizedFloor(room.floor())))
                    .filter(room -> roomMatchesTarget(room.type(), type)).count();
            if (represented < count) {
                gaps.add("Programme gap: " + represented + " of " + count + " required "
                        + type.toLowerCase(Locale.ROOT).replace('_', ' ') + " spaces represented on "
                        + floor.toLowerCase(Locale.ROOT) + " floor");
            }
        });
        return List.copyOf(gaps);
    }

    private boolean roomMatchesTarget(String actual, String target) {
        if (actual.equals(target)) return true;
        return "BEDROOM".equals(target) && actual.endsWith("BEDROOM");
    }

    private List<String> programmeExplanations(Strategy strategy, List<String> gaps) {
        return programmeExplanations(strategy, null, gaps);
    }

    private List<String> programmeExplanations(Strategy strategy, PlanningParameterVariant variant,
            List<String> gaps) {
        var explanations = new ArrayList<>(strategy.explanations());
        if (variant != null && variant.explanations() != null) explanations.addAll(variant.explanations());
        gaps.forEach(gap -> explanations.add("Professional review required - " + gap));
        return List.copyOf(explanations);
    }

    private PlanningParameterVariant variantFor(PlanningParameterSet parameters, String strategy) {
        if (parameters == null || parameters.variants() == null) return null;
        return parameters.variants().stream().filter(value -> strategy.equals(value.strategy()))
                .findFirst().orElse(null);
    }

    private HomeParameters optionParameters(HomeParameters requested, PlanningParameterVariant variant) {
        if (variant == null) return requested;
        // Explicit circulation choices remain hard bounds. A generated option may add optional
        // lifestyle/future-ready provisions, but it cannot remove anything the customer requested.
        return new HomeParameters(requested.homeType(), requested.staircaseType(), requested.liftProvision(),
                Math.max(requested.balconyCount(), variant.balconyCount()),
                requested.terraceRequired() || variant.terraceRequired(),
                requested.courtyardRequired() || variant.courtyardRequired(),
                requested.accessibleGroundFloor() || variant.accessibleGroundFloor(),
                Math.max(requested.parkingCars(), variant.parkingCars()),
                requested.solarReady() || variant.solarReady(),
                requested.rainwaterHarvesting() || variant.rainwaterHarvesting());
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    List<String> validate(double plotWidth, double plotLength, List<RoomGeometry> rooms) {
        var violations = new ArrayList<String>();
        for (var room : rooms) {
            if (room.width() <= 0 || room.length() <= 0) {
                violations.add(room.id() + " has unusable geometry");
            }
            if (room.x() < 0 || room.y() < 0 || room.x() + room.width() > plotWidth + .01
                    || room.y() + room.length() > plotLength + .01) {
                violations.add(room.id() + " escapes the plot boundary");
            }
        }
        for (int left = 0; left < rooms.size(); left++) {
            for (int right = left + 1; right < rooms.size(); right++) {
                if (sameFloor(rooms.get(left), rooms.get(right)) && overlaps(rooms.get(left), rooms.get(right))) {
                    violations.add(rooms.get(left).id() + " overlaps " + rooms.get(right).id());
                }
            }
        }
        return List.copyOf(violations);
    }

    List<String> validateDocument(int requestedFloors, List<RoomGeometry> rooms,
            List<Map<String, Object>> doors, List<Map<String, Object>> windows) {
        var violations = new ArrayList<String>();
        var expectedFloors = new LinkedHashSet<String>();
        for (var index = 0; index < requestedFloors; index++) expectedFloors.add(floorName(index));
        var representedFloors = new LinkedHashSet<String>();
        var roomIds = new LinkedHashSet<String>();
        var roomsById = new LinkedHashMap<String, RoomGeometry>();
        for (var room : rooms) {
            var floor = normalizedFloor(room.floor());
            representedFloors.add(floor);
            if (!roomIds.add(room.id())) violations.add("Duplicate room id " + room.id());
            roomsById.putIfAbsent(room.id(), room);
        }
        if (!representedFloors.equals(expectedFloors)) {
            violations.add("Geometry floors " + representedFloors + " do not match requested floors " + expectedFloors);
        }
        validateStairCores(expectedFloors, rooms, violations);
        validateLiftCores(expectedFloors, rooms, violations);
        var envelopes = new LinkedHashMap<String, Envelope>();
        for (var floor : representedFloors) {
            envelopes.put(floor, envelopeFor(rooms.stream()
                    .filter(room -> floor.equals(normalizedFloor(room.floor()))).toList()));
        }
        validateDoors(doors, roomsById, envelopes, violations);
        validateWindows(windows, roomsById, envelopes, violations);
        validateOpeningSeparation(doors, windows, violations);
        validateConnectivity(rooms, doors, violations);
        return List.copyOf(violations);
    }

    private void validateStairCores(LinkedHashSet<String> expectedFloors, List<RoomGeometry> rooms,
            List<String> violations) {
        RoomGeometry reference = null;
        for (var floor : expectedFloors) {
            var stairs = rooms.stream()
                    .filter(room -> floor.equals(normalizedFloor(room.floor())))
                    .filter(room -> "STAIRCASE".equals(room.type()))
                    .toList();
            if (stairs.size() != 1) {
                violations.add(floor + " floor requires exactly one STAIRCASE; found " + stairs.size());
                continue;
            }
            if (reference == null) {
                reference = stairs.getFirst();
            } else if (!sameBounds(reference, stairs.getFirst())) {
                violations.add("Stair cores are not vertically aligned: " + reference.id()
                        + " differs from " + stairs.getFirst().id());
            }
        }
    }

    private void validateLiftCores(LinkedHashSet<String> expectedFloors, List<RoomGeometry> rooms,
            List<String> violations) {
        var lifts = rooms.stream().filter(room -> "LIFT_SHAFT".equals(room.type())).toList();
        if (lifts.isEmpty()) return;
        RoomGeometry reference = null;
        for (var floor : expectedFloors) {
            var floorLifts = lifts.stream()
                    .filter(room -> floor.equals(normalizedFloor(room.floor()))).toList();
            if (floorLifts.size() != 1) {
                violations.add(floor + " floor requires exactly one aligned LIFT_SHAFT; found "
                        + floorLifts.size());
                continue;
            }
            if (reference == null) reference = floorLifts.getFirst();
            else if (!sameBounds(reference, floorLifts.getFirst())) {
                violations.add("Lift shafts are not vertically aligned: " + reference.id()
                        + " differs from " + floorLifts.getFirst().id());
            }
        }
    }

    private boolean sameBounds(RoomGeometry first, RoomGeometry second) {
        return Math.abs(first.x() - second.x()) <= .02
                && Math.abs(first.y() - second.y()) <= .02
                && Math.abs(first.width() - second.width()) <= .02
                && Math.abs(first.length() - second.length()) <= .02;
    }

    private void validateDoors(List<Map<String, Object>> doors, Map<String, RoomGeometry> roomsById,
            Map<String, Envelope> envelopes, List<String> violations) {
        var ids = new LinkedHashSet<String>();
        var physicalOpenings = new LinkedHashSet<String>();
        var sharedRoomEdges = new LinkedHashSet<String>();
        var exteriorDoorCounts = new LinkedHashMap<String, Integer>();
        for (var opening : doors) {
            var id = String.valueOf(opening.get("id"));
            var roomId = String.valueOf(opening.get("roomId"));
            var floor = normalizedFloor(opening.get("floor") == null ? null : opening.get("floor").toString());
            if (!ids.add(id)) violations.add("Duplicate door id " + id);
            var room = roomsById.get(roomId);
            if (room == null) {
                violations.add("door " + id + " references missing room " + roomId);
                continue;
            }
            if (!normalizedFloor(room.floor()).equals(floor)) {
                violations.add("door " + id + " is not on the same floor as " + roomId);
            }
            var orientation = orientation(opening);
            if (orientation == null) {
                violations.add("door " + id + " has invalid orientation " + opening.get("orientation"));
                continue;
            }
            var x = number(opening.get("x"));
            var y = number(opening.get("y"));
            var width = number(opening.get("width"));
            validateOpeningOnRoom("door", id, room, orientation, x, y, width, violations);
            var physicalKey = physicalOpeningKey(floor, orientation, x, y);
            if (!physicalOpenings.add(physicalKey)) {
                violations.add("Duplicate physical door opening at " + physicalKey);
            }

            var connectedId = opening.get("connectsRoomId") == null ? null
                    : opening.get("connectsRoomId").toString();
            if (connectedId == null || connectedId.isBlank()) {
                exteriorDoorCounts.merge(floor, 1, Integer::sum);
                var envelope = envelopes.get(floor);
                if (envelope == null || !touchesEnvelope(room, envelope, orientation)) {
                    violations.add("door " + id + " is not on the exterior building envelope");
                }
                continue;
            }
            var connected = roomsById.get(connectedId);
            if (connected == null) {
                violations.add("door " + id + " references missing room " + connectedId);
                continue;
            }
            if (!normalizedFloor(connected.floor()).equals(floor)) {
                violations.add("door " + id + " is not on the same floor as " + connectedId);
            }
            var edge = sharedEdge(room, connected);
            if (edge == null || !edge.orientationFrom(room).equals(orientation)
                    || !doorFitsSharedEdge(edge, x, y, width)) {
                violations.add("door " + id + " is not contained by the shared room edge");
            }
            var pairKey = floor + "|" + (roomId.compareTo(connectedId) < 0
                    ? roomId + "|" + connectedId : connectedId + "|" + roomId);
            if (!sharedRoomEdges.add(pairKey)) {
                violations.add("Duplicate door on shared room edge " + pairKey);
            }
        }
        if (exteriorDoorCounts.getOrDefault("GROUND", 0) != 1) {
            violations.add("Ground floor requires exactly one exterior entrance door");
        }
        for (var floor : envelopes.keySet()) {
            if (!"GROUND".equals(floor) && exteriorDoorCounts.getOrDefault(floor, 0) != 0) {
                violations.add(floor + " floor must connect through the stair core, not an exterior entrance");
            }
        }
    }

    private void validateWindows(List<Map<String, Object>> windows, Map<String, RoomGeometry> roomsById,
            Map<String, Envelope> envelopes, List<String> violations) {
        var ids = new LinkedHashSet<String>();
        var physicalOpenings = new LinkedHashSet<String>();
        for (var opening : windows) {
            var id = String.valueOf(opening.get("id"));
            var roomId = String.valueOf(opening.get("roomId"));
            var floor = normalizedFloor(opening.get("floor") == null ? null : opening.get("floor").toString());
            if (!ids.add(id)) violations.add("Duplicate window id " + id);
            var room = roomsById.get(roomId);
            if (room == null) {
                violations.add("window " + id + " references missing room " + roomId);
                continue;
            }
            if (!normalizedFloor(room.floor()).equals(floor)) {
                violations.add("window " + id + " is not on the same floor as " + roomId);
            }
            var orientation = orientation(opening);
            if (orientation == null) {
                violations.add("window " + id + " has invalid orientation " + opening.get("orientation"));
                continue;
            }
            var x = number(opening.get("x"));
            var y = number(opening.get("y"));
            var width = number(opening.get("width"));
            validateOpeningOnRoom("window", id, room, orientation, x, y, width, violations);
            var envelope = envelopes.get(floor);
            if (envelope == null || !touchesEnvelope(room, envelope, orientation)) {
                violations.add("window " + id + " is not on the exterior building envelope");
            }
            var physicalKey = physicalOpeningKey(floor, orientation, x, y);
            if (!physicalOpenings.add(physicalKey)) {
                violations.add("Duplicate physical window opening at " + physicalKey);
            }
        }
    }

    private void validateOpeningSeparation(List<Map<String, Object>> doors, List<Map<String, Object>> windows,
            List<String> violations) {
        for (var door : doors) {
            var doorInterval = openingInterval(door);
            if (doorInterval == null) continue;
            for (var window : windows) {
                var windowInterval = openingInterval(window);
                if (windowInterval != null && openingIntervalsOverlap(doorInterval, windowInterval)) {
                    violations.add("Door " + doorInterval.id() + " overlaps window " + windowInterval.id()
                            + " on the same wall");
                }
            }
        }
    }

    private void validateOpeningOnRoom(String kind, String id, RoomGeometry room, String orientation,
            double x, double y, double width, List<String> violations) {
        var horizontal = "NORTH".equals(orientation) || "SOUTH".equals(orientation);
        var wallLength = horizontal ? room.width() : room.length();
        if (!Double.isFinite(width) || width <= 0 || width > wallLength + .01) {
            violations.add(kind + " " + id + " has invalid width");
        }
        var expectedAxis = switch (orientation) {
            case "NORTH" -> room.y();
            case "SOUTH" -> room.y() + room.length();
            case "EAST" -> room.x() + room.width();
            default -> room.x();
        };
        var actualAxis = horizontal ? y : x;
        var position = horizontal ? x : y;
        var minimum = horizontal ? room.x() : room.y();
        var maximum = minimum + wallLength;
        if (!Double.isFinite(actualAxis) || Math.abs(actualAxis - expectedAxis) > .02
                || !Double.isFinite(position) || position - width / 2 < minimum - .02
                || position + width / 2 > maximum + .02) {
            violations.add(kind + " " + id + " is not contained by the referenced room perimeter");
        }
    }

    private boolean doorFitsSharedEdge(SharedEdge edge, double x, double y, double width) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width)) return false;
        var axis = edge.vertical() ? x : y;
        var expectedAxis = edge.vertical() ? edge.x() : edge.y();
        var position = edge.vertical() ? y : x;
        return Math.abs(axis - expectedAxis) <= .02
                && position - width / 2 >= edge.from() - .02
                && position + width / 2 <= edge.to() + .02;
    }

    private String orientation(Map<String, Object> opening) {
        if (opening.get("orientation") == null) return null;
        var orientation = opening.get("orientation").toString().toUpperCase(Locale.ROOT);
        return List.of("NORTH", "SOUTH", "EAST", "WEST").contains(orientation) ? orientation : null;
    }

    private String physicalOpeningKey(String floor, String orientation, double x, double y) {
        var axis = "NORTH".equals(orientation) || "SOUTH".equals(orientation) ? "H" : "V";
        return floor + "|" + axis + "|" + round2(x) + "|" + round2(y);
    }

    private OpeningInterval openingInterval(Map<String, Object> opening) {
        var orientation = orientation(opening);
        var x = number(opening.get("x"));
        var y = number(opening.get("y"));
        var width = number(opening.get("width"));
        if (orientation == null || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(width) || width <= 0) return null;
        var horizontal = "NORTH".equals(orientation) || "SOUTH".equals(orientation);
        var center = horizontal ? x : y;
        return new OpeningInterval(
                normalizedFloor(opening.get("floor") == null ? null : opening.get("floor").toString()),
                horizontal ? "H" : "V", horizontal ? y : x, center - width / 2, center + width / 2,
                String.valueOf(opening.get("id")));
    }

    private boolean openingIntervalsOverlap(OpeningInterval first, OpeningInterval second) {
        return first.floor().equals(second.floor()) && first.axis().equals(second.axis())
                && Math.abs(first.wallLine() - second.wallLine()) <= .02
                && Math.max(first.from(), second.from()) < Math.min(first.to(), second.to()) - .02;
    }

    private void validateConnectivity(List<RoomGeometry> rooms, List<Map<String, Object>> doors,
            List<String> violations) {
        for (var floor : rooms.stream().map(room -> normalizedFloor(room.floor())).distinct().toList()) {
            var floorRooms = rooms.stream().filter(room -> floor.equals(normalizedFloor(room.floor()))).toList();
            var graph = new LinkedHashMap<String, LinkedHashSet<String>>();
            floorRooms.forEach(room -> graph.put(room.id(), new LinkedHashSet<>()));
            String entranceRoot = null;
            for (var door : doors) {
                if (!floor.equals(normalizedFloor(door.get("floor") == null ? null : door.get("floor").toString()))) {
                    continue;
                }
                var roomId = String.valueOf(door.get("roomId"));
                var connected = door.get("connectsRoomId") == null ? null : door.get("connectsRoomId").toString();
                if (connected == null || connected.isBlank()) {
                    entranceRoot = roomId;
                } else if (graph.containsKey(roomId) && graph.containsKey(connected)) {
                    graph.get(roomId).add(connected);
                    graph.get(connected).add(roomId);
                }
            }
            var root = "GROUND".equals(floor) ? entranceRoot
                    : floorRooms.stream().filter(room -> "STAIRCASE".equals(room.type()))
                            .map(RoomGeometry::id).findFirst().orElse(null);
            if (root == null || !graph.containsKey(root)) {
                violations.add(floor + " floor has no entrance/stair circulation root");
                continue;
            }
            var visited = new LinkedHashSet<String>();
            var queue = new ArrayList<String>();
            visited.add(root);
            queue.add(root);
            for (var cursor = 0; cursor < queue.size(); cursor++) {
                for (var adjacent : graph.get(queue.get(cursor))) {
                    if (visited.add(adjacent)) queue.add(adjacent);
                }
            }
            if (visited.size() != floorRooms.size()) {
                violations.add(floor + " floor circulation graph is disconnected; reached " + visited.size()
                        + " of " + floorRooms.size() + " rooms");
            }
        }
    }

    private double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return Double.NaN;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private List<RoomGeometry> packRooms(double width, double length, int floorCount, Strategy strategy,
            Recommendation recommendation, HomeParameters parameters, PlanningParameterVariant variant) {
        var rooms = new ArrayList<RoomGeometry>();
        var bedroomAllocation = bedroomAllocation(recommendation.bedrooms(), floorCount);
        var liftRequired = floorCount > 1 && !"NONE".equals(parameters.liftProvision());
        for (var floorIndex = 0; floorIndex < floorCount; floorIndex++) {
            rooms.addAll(packFloor(width, length, strategy, floorIndex, floorCount,
                    bedroomAllocation.get(floorIndex),
                    recommendation.seniorCitizenBedroom(), liftRequired, parameters, variant));
        }
        return List.copyOf(rooms);
    }

    private List<Integer> bedroomAllocation(int requestedBedrooms, int floorCount) {
        var target = Math.max(1, Math.min(6, requestedBedrooms));
        var result = new ArrayList<Integer>();
        var upperCapacity = Math.max(0, floorCount - 1) * 4;
        var groundBedrooms = Math.max(1, target - upperCapacity);
        result.add(groundBedrooms);
        var remaining = target - groundBedrooms;
        for (var floor = 1; floor < floorCount; floor++) {
            var remainingFloors = floorCount - floor;
            var count = Math.min(4, (int) Math.ceil(remaining / (double) remainingFloors));
            result.add(count);
            remaining -= count;
        }
        return List.copyOf(result);
    }

    private List<RoomGeometry> packFloor(double width, double length, Strategy strategy, int floorIndex,
            int floorCount, int bedroomCount, boolean seniorBedroomRequired, boolean liftRequired,
            HomeParameters parameters, PlanningParameterVariant variant) {
        var innerWidth = width * .82;
        var innerLength = length * .76;
        var startX = width * .09;
        var startY = length * .12;
        // Keep the structural envelope and stair stack aligned, but vary the internal grid so each
        // storey is genuine floor-specific geometry rather than a renamed ground-floor copy.
        var baseLeftWidth = innerWidth * strategy.columnSplit();
        var baseRightWidth = innerWidth - baseLeftWidth;
        var baseTopLength = innerLength * strategy.rowSplit();
        var baseBottomLength = innerLength - baseTopLength;
        if (floorIndex == 0) {
            return packGroundFloor(startX, startY, baseLeftWidth, baseRightWidth,
                    baseTopLength, baseBottomLength,
                    bedroomCount, seniorBedroomRequired, liftRequired, parameters, variant);
        }
        var columnAdjustment = floorIndex == 1 ? .04 : -.03;
        var rowAdjustment = floorIndex == 1 ? -.03 : .03;
        var topLeftWidth = innerWidth * (strategy.columnSplit() + columnAdjustment);
        var topRightWidth = innerWidth - topLeftWidth;
        var topLength = innerLength * (strategy.rowSplit() + rowAdjustment);
        var middleY = startY + topLength;
        var buildingBottom = startY + innerLength;
        var coreX = startX + baseLeftWidth;
        var coreY = startY + baseTopLength + baseBottomLength * .58;
        var coreWidth = baseRightWidth * .38;
        var coreLength = baseBottomLength * .42;
        var middleLength = coreY - middleY;
        var types = roomProgram(floorIndex, bedroomCount);

        var rooms = new ArrayList<RoomGeometry>();
        rooms.add(room(floorIndex, 1, types.get(0), startX, startY, topLeftWidth, topLength));
        rooms.add(room(floorIndex, 2, types.get(1), startX + topLeftWidth, startY, topRightWidth, topLength));
        rooms.add(room(floorIndex, 3, types.get(2), startX, middleY,
                baseLeftWidth, buildingBottom - middleY));
        rooms.add(room(floorIndex, 4, types.get(3), coreX, middleY,
                baseRightWidth * .52, middleLength));
        rooms.add(room(floorIndex, 5, types.get(4), coreX + baseRightWidth * .52,
                middleY, baseRightWidth * .48, middleLength));
        addVerticalCore(rooms, floorIndex, 6, coreX, coreY, coreWidth, coreLength, liftRequired);
        var nextIndex = liftRequired ? 8 : 7;
        rooms.add(room(floorIndex, nextIndex++, types.get(6), coreX + coreWidth,
                coreY, baseRightWidth * .24, coreLength));
        addOutdoorOrUtilityZones(rooms, floorIndex, floorCount, nextIndex,
                coreX + baseRightWidth * .62, coreY, baseRightWidth * .38, coreLength,
                types.get(7), parameters);
        return List.copyOf(rooms);
    }

    private List<RoomGeometry> packGroundFloor(double startX, double startY, double leftWidth,
            double rightWidth, double topLength, double bottomLength, int bedroomCount,
            boolean seniorBedroomRequired, boolean liftRequired, HomeParameters parameters,
            PlanningParameterVariant variant) {
        var rooms = new ArrayList<RoomGeometry>();
        var roomIndex = 1;
        var parkingType = parameters.courtyardRequired()
                ? parameters.parkingCars() > 0 ? "COURTYARD_PARKING" : "COURTYARD"
                : parameters.parkingCars() > 0 ? bedroomCount >= 6 ? "BEDROOM" : "PARKING" : "OPEN_SPACE";
        // The default 40 x 60 brief requests two cars. Give parking a genuine 8 x 16 ft module
        // per side-by-side bay when the envelope permits it, while retaining at least eight feet
        // for the adjacent living zone. Requests that cannot physically fit remain validation gaps.
        var totalWidth = leftWidth + rightWidth;
        var parkingWidth = leftWidth;
        if (parkingType.contains("PARKING") && topLength >= 16) {
            var desiredWidth = Math.max(leftWidth, parameters.parkingCars() * 8d);
            parkingWidth = round2(Math.min(desiredWidth, Math.max(leftWidth, totalWidth - 8d)));
        }
        rooms.add(room(0, roomIndex++, parkingType, startX, startY, parkingWidth, topLength));

        var rightX = startX + leftWidth;
        var topRightX = startX + parkingWidth;
        var topRightWidth = round2(totalWidth - parkingWidth);
        if (bedroomCount >= 3) {
            var livingWidth = topRightWidth * .55;
            rooms.add(room(0, roomIndex++, "LIVING_ROOM", topRightX, startY, livingWidth, topLength));
            rooms.add(room(0, roomIndex++, "BEDROOM", topRightX + livingWidth, startY,
                    topRightWidth - livingWidth, topLength));
        } else {
            rooms.add(room(0, roomIndex++, "LIVING_ROOM", topRightX, startY, topRightWidth, topLength));
        }

        var primaryType = seniorBedroomRequired ? "SENIOR_BEDROOM" : "MASTER_BEDROOM";
        if (bedroomCount >= 2) {
            var primaryY = round2(startY + topLength);
            var primaryLength = round2(bottomLength * .54);
            var secondaryY = round2(primaryY + primaryLength);
            var bottomEnd = round2(startY + topLength + bottomLength);
            rooms.add(room(0, roomIndex++, primaryType, startX, primaryY,
                    leftWidth, primaryLength));
            rooms.add(room(0, roomIndex++, "BEDROOM", startX, secondaryY,
                    leftWidth, round2(bottomEnd - secondaryY)));
        } else {
            rooms.add(room(0, roomIndex++, primaryType, startX, startY + topLength, leftWidth, bottomLength));
        }

        rooms.add(room(0, roomIndex++, bedroomCount >= 5 ? "BEDROOM" : "DINING", rightX,
                startY + topLength, rightWidth * .52, bottomLength * .58));
        rooms.add(room(0, roomIndex++, "KITCHEN", rightX + rightWidth * .52,
                startY + topLength, rightWidth * .48, bottomLength * .58));
        addVerticalCore(rooms, 0, roomIndex, rightX,
                startY + topLength + bottomLength * .58, rightWidth * .38, bottomLength * .42,
                liftRequired);
        roomIndex += liftRequired ? 2 : 1;
        rooms.add(room(0, roomIndex++, "BATHROOM", rightX + rightWidth * .38,
                startY + topLength + bottomLength * .58, rightWidth * .24, bottomLength * .42));
        rooms.add(room(0, roomIndex, bedroomCount >= 4 ? "BEDROOM" : "UTILITY",
                rightX + rightWidth * .62, startY + topLength + bottomLength * .58,
                rightWidth * .38, bottomLength * .42));
        return List.copyOf(rooms);
    }

    private void addVerticalCore(List<RoomGeometry> rooms, int floorIndex, int startIndex,
            double x, double y, double width, double length, boolean liftRequired) {
        if (!liftRequired) {
            rooms.add(room(floorIndex, startIndex, "STAIRCASE", x, y, width, length));
            return;
        }
        // The stair and lift share one aligned structural core on every floor. Keeping both
        // rectangles identical across storeys makes the shaft provision auditable and validates
        // the duplex circulation stack without inventing construction details.
        var stairWidth = round2(width * .68);
        rooms.add(room(floorIndex, startIndex, "STAIRCASE", x, y, stairWidth, length));
        rooms.add(room(floorIndex, startIndex + 1, "LIFT_SHAFT", x + stairWidth, y,
                round2(width - stairWidth), length));
    }

    private void addOutdoorOrUtilityZones(List<RoomGeometry> rooms, int floorIndex, int floorCount,
            int startIndex, double x, double y, double width, double length, String fallbackType,
            HomeParameters parameters) {
        var zones = new ArrayList<String>();
        var upperFloorCount = Math.max(1, floorCount - 1);
        var baseBalconies = parameters.balconyCount() / upperFloorCount;
        var extraBalconies = parameters.balconyCount() % upperFloorCount;
        var upperIndex = Math.max(0, floorIndex - 1);
        var balconiesHere = baseBalconies + (upperIndex < extraBalconies ? 1 : 0);
        for (var index = 0; index < balconiesHere; index++) zones.add("BALCONY");
        if (floorIndex == floorCount - 1 && parameters.terraceRequired()) zones.add("TERRACE");
        if (zones.isEmpty()) zones.add(fallbackType);
        var zoneWidth = width / zones.size();
        for (var index = 0; index < zones.size(); index++) {
            var fromX = x + zoneWidth * index;
            var actualWidth = index == zones.size() - 1 ? x + width - fromX : zoneWidth;
            rooms.add(room(floorIndex, startIndex + index, zones.get(index), fromX, y,
                    actualWidth, length));
        }
    }

    private List<String> roomProgram(int floorIndex, int bedroomCount) {
        var program = new ArrayList<>(switch (floorIndex) {
            case 1 -> List.of("FLEX_ROOM", "FAMILY_LOUNGE", "DRESSING_ROOM", "STUDY", "ATTACHED_BATHROOM",
                    "STAIRCASE", "BATHROOM", "BALCONY");
            default -> List.of("FLEX_ROOM", "MULTIPURPOSE_ROOM", "TERRACE", "HOME_OFFICE", "PRAYER_ROOM",
                    "STAIRCASE", "BATHROOM", "LAUNDRY");
        });
        var bedroomSlots = List.of(0, 2, 3, 4);
        for (var bedroom = 0; bedroom < bedroomCount && bedroom < bedroomSlots.size(); bedroom++) {
            var slot = bedroomSlots.get(bedroom);
            program.set(slot, floorIndex == 1 && bedroom == 0 ? "MASTER_BEDROOM" : "BEDROOM");
        }
        return List.copyOf(program);
    }

    private boolean overlaps(RoomGeometry left, RoomGeometry right) {
        var epsilon = .01;
        return left.x() < right.x() + right.width() - epsilon
                && left.x() + left.width() > right.x() + epsilon
                && left.y() < right.y() + right.length() - epsilon
                && left.y() + left.length() > right.y() + epsilon;
    }

    private boolean sameFloor(RoomGeometry left, RoomGeometry right) {
        return normalizedFloor(left.floor()).equals(normalizedFloor(right.floor()));
    }

    private RoomGeometry room(int floorIndex, int roomIndex, String type,
            double x, double y, double width, double length) {
        var floor = floorName(floorIndex);
        return new RoomGeometry(floorPrefix(floorIndex) + "-R" + roomIndex, type,
                round2(x), round2(y), round2(width), round2(length), round2(width * length), floor);
    }

    private String floorName(int floorIndex) {
        return switch (floorIndex) {
            case 0 -> "GROUND";
            case 1 -> "FIRST";
            default -> "SECOND";
        };
    }

    private String floorPrefix(int floorIndex) {
        return switch (floorIndex) {
            case 0 -> "G";
            case 1 -> "F1";
            default -> "F2";
        };
    }

    private String normalizedFloor(String floor) {
        return floor == null || floor.isBlank() ? "GROUND" : floor.toUpperCase(Locale.ROOT);
    }

    private List<Map<String, Object>> doorsFor(List<RoomGeometry> rooms, Facing roadFacing) {
        var doors = new ArrayList<Map<String, Object>>();
        for (var floor : rooms.stream().map(RoomGeometry::floor).distinct().toList()) {
            var floorRooms = rooms.stream().filter(room -> floor.equals(room.floor())).toList();
            var envelope = envelopeFor(floorRooms);
            var ground = "GROUND".equals(floor);
            var root = ground ? entranceRoom(floorRooms, envelope, roadFacing)
                    : floorRooms.stream().filter(room -> "STAIRCASE".equals(room.type())).findFirst()
                            .orElse(floorRooms.getFirst());
            var doorNumber = 0;
            if (ground) {
                doors.add(exteriorDoor(root, envelope, roadFacing, floorPrefixFor(floor) + "-D" + doorNumber++));
            }

            var edges = sharedEdges(floorRooms);
            var visited = new LinkedHashSet<String>();
            var queue = new ArrayList<RoomGeometry>();
            visited.add(root.id());
            queue.add(root);
            for (var cursor = 0; cursor < queue.size(); cursor++) {
                var current = queue.get(cursor);
                for (var edge : edges) {
                    var adjacent = edge.other(current);
                    if (adjacent == null || visited.contains(adjacent.id())) continue;
                    doors.add(internalDoor(edge, current, adjacent,
                            floorPrefixFor(floor) + "-D" + doorNumber++));
                    visited.add(adjacent.id());
                    queue.add(adjacent);
                }
            }
        }
        return List.copyOf(doors);
    }

    private Map<String, Object> exteriorDoor(RoomGeometry room, Envelope envelope, Facing preferred,
            String id) {
        var orientation = touchesEnvelope(room, envelope, preferred.name())
                ? preferred.name() : exteriorSides(room, envelope).getFirst();
        var horizontal = "NORTH".equals(orientation) || "SOUTH".equals(orientation);
        var wallLength = horizontal ? room.width() : room.length();
        var width = round2(Math.min(room.type().contains("PARKING") ? 8.0 : 3.6,
                Math.max(2.4, wallLength * .32)));
        var door = opening(id, room, orientation,
                horizontal ? room.x() + room.width() / 2
                        : "WEST".equals(orientation) ? room.x() : room.x() + room.width(),
                horizontal
                        ? "NORTH".equals(orientation) ? room.y() : room.y() + room.length()
                        : room.y() + room.length() / 2,
                width);
        door.put("exterior", true);
        return Map.copyOf(door);
    }

    private Map<String, Object> internalDoor(SharedEdge edge, RoomGeometry from, RoomGeometry to, String id) {
        var orientation = edge.orientationFrom(from);
        var door = opening(id, from, orientation, edge.x(), edge.y(),
                round2(Math.min(3.0, Math.max(2.4, edge.span() * .35))));
        door.put("connectsRoomId", to.id());
        door.put("exterior", false);
        return Map.copyOf(door);
    }

    private LinkedHashMap<String, Object> opening(String id, RoomGeometry room, String orientation,
            double x, double y, double width) {
        var opening = new LinkedHashMap<String, Object>();
        opening.put("id", id);
        opening.put("roomId", room.id());
        opening.put("floor", room.floor());
        opening.put("x", round2(x));
        opening.put("y", round2(y));
        opening.put("width", round2(width));
        opening.put("orientation", orientation);
        var doorMarker = id.lastIndexOf("-D");
        if (doorMarker >= 0) {
            opening.put("swing", Integer.parseInt(id.substring(doorMarker + 2)) % 2 == 0
                    ? "LEFT" : "RIGHT");
        }
        return opening;
    }

    private RoomGeometry entranceRoom(List<RoomGeometry> rooms, Envelope envelope, Facing facing) {
        return rooms.stream()
                .filter(room -> touchesEnvelope(room, envelope, facing.name()))
                .sorted((left, right) -> Integer.compare(entrancePriority(left), entrancePriority(right)))
                .findFirst()
                .orElseGet(() -> rooms.stream().filter(room -> !exteriorSides(room, envelope).isEmpty())
                        .findFirst().orElse(rooms.getFirst()));
    }

    private int entrancePriority(RoomGeometry room) {
        if (room.type().contains("PARKING")) return 0;
        if (room.type().contains("LIVING")) return 1;
        if (room.type().contains("BEDROOM")) return 2;
        if (room.type().contains("DINING") || room.type().contains("KITCHEN")) return 3;
        return 4;
    }

    private List<SharedEdge> sharedEdges(List<RoomGeometry> rooms) {
        var edges = new ArrayList<SharedEdge>();
        for (var leftIndex = 0; leftIndex < rooms.size(); leftIndex++) {
            for (var rightIndex = leftIndex + 1; rightIndex < rooms.size(); rightIndex++) {
                var edge = sharedEdge(rooms.get(leftIndex), rooms.get(rightIndex));
                if (edge != null && edge.span() >= 2.4) edges.add(edge);
            }
        }
        edges.sort((left, right) -> (left.first().id() + "|" + left.second().id())
                .compareTo(right.first().id() + "|" + right.second().id()));
        return List.copyOf(edges);
    }

    private SharedEdge sharedEdge(RoomGeometry first, RoomGeometry second) {
        var tolerance = .02;
        if (Math.abs(first.x() + first.width() - second.x()) <= tolerance
                || Math.abs(second.x() + second.width() - first.x()) <= tolerance) {
            var from = Math.max(first.y(), second.y());
            var to = Math.min(first.y() + first.length(), second.y() + second.length());
            if (to - from < 2.4) return null;
            var firstOnLeft = first.x() < second.x();
            var x = firstOnLeft ? first.x() + first.width() : first.x();
            return new SharedEdge(first, second, firstOnLeft ? "EAST" : "WEST",
                    round2(x), round2((from + to) / 2), round2(to - from), round2(from), round2(to), true);
        }
        if (Math.abs(first.y() + first.length() - second.y()) <= tolerance
                || Math.abs(second.y() + second.length() - first.y()) <= tolerance) {
            var from = Math.max(first.x(), second.x());
            var to = Math.min(first.x() + first.width(), second.x() + second.width());
            if (to - from < 2.4) return null;
            var firstAbove = first.y() < second.y();
            var y = firstAbove ? first.y() + first.length() : first.y();
            return new SharedEdge(first, second, firstAbove ? "SOUTH" : "NORTH",
                    round2((from + to) / 2), round2(y), round2(to - from), round2(from), round2(to), false);
        }
        return null;
    }

    private List<Map<String, Object>> windowsFor(List<RoomGeometry> rooms, List<Map<String, Object>> doors) {
        var windows = new ArrayList<Map<String, Object>>();
        for (var floor : rooms.stream().map(RoomGeometry::floor).distinct().toList()) {
            var floorRooms = rooms.stream().filter(room -> floor.equals(room.floor())).toList();
            var envelope = envelopeFor(floorRooms);
            var windowNumber = 1;
            for (var room : floorRooms) {
                if (room.type().contains("PARKING") || room.type().contains("STAIR")
                        || room.type().contains("LIFT") || room.type().contains("TERRACE")
                        || room.type().contains("BALCONY") || room.type().contains("COURTYARD")
                        || room.type().contains("OPEN_SPACE")) continue;
                var sides = exteriorSides(room, envelope);
                if (sides.isEmpty()) continue;
                var window = exteriorWindow(room, sides, doors,
                        floorPrefixFor(floor) + "-W" + windowNumber);
                if (window != null) {
                    windows.add(window);
                    windowNumber++;
                }
            }
        }
        return List.copyOf(windows);
    }

    private Map<String, Object> exteriorWindow(RoomGeometry room, List<String> sides,
            List<Map<String, Object>> doors, String id) {
        for (var orientation : sides) {
            var horizontal = "NORTH".equals(orientation) || "SOUTH".equals(orientation);
            var wallLength = horizontal ? room.width() : room.length();
            var width = round2(Math.min(4.0, Math.max(2.5, wallLength * .28)));
            var start = horizontal ? room.x() : room.y();
            for (var ratio : List.of(.20, .80, .35, .65, .50)) {
                var center = start + wallLength * ratio;
                if (center - width / 2 < start - .01 || center + width / 2 > start + wallLength + .01) {
                    continue;
                }
                var candidate = opening(id, room, orientation,
                        horizontal ? center : "WEST".equals(orientation) ? room.x() : room.x() + room.width(),
                        horizontal
                                ? "NORTH".equals(orientation) ? room.y() : room.y() + room.length()
                                : center,
                        width);
                candidate.remove("swing");
                var interval = openingInterval(candidate);
                var collides = interval != null && doors.stream().map(this::openingInterval)
                        .filter(Objects::nonNull)
                        .anyMatch(door -> openingIntervalsOverlap(door, interval));
                if (!collides) return Map.copyOf(candidate);
            }
        }
        return null;
    }

    private Envelope envelopeFor(List<RoomGeometry> rooms) {
        return new Envelope(
                rooms.stream().mapToDouble(RoomGeometry::x).min().orElse(0),
                rooms.stream().mapToDouble(room -> room.x() + room.width()).max().orElse(0),
                rooms.stream().mapToDouble(RoomGeometry::y).min().orElse(0),
                rooms.stream().mapToDouble(room -> room.y() + room.length()).max().orElse(0));
    }

    private List<String> exteriorSides(RoomGeometry room, Envelope envelope) {
        var sides = new ArrayList<String>();
        if (Math.abs(room.y() - envelope.minimumY()) <= .02) sides.add("NORTH");
        if (Math.abs(room.x() + room.width() - envelope.maximumX()) <= .02) sides.add("EAST");
        if (Math.abs(room.y() + room.length() - envelope.maximumY()) <= .02) sides.add("SOUTH");
        if (Math.abs(room.x() - envelope.minimumX()) <= .02) sides.add("WEST");
        return List.copyOf(sides);
    }

    private boolean touchesEnvelope(RoomGeometry room, Envelope envelope, String orientation) {
        return exteriorSides(room, envelope).contains(orientation);
    }

    private String floorPrefixFor(String floor) {
        return switch (normalizedFloor(floor)) {
            case "GROUND" -> "G";
            case "FIRST" -> "F1";
            default -> "F2";
        };
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Strategy(String key, String name, double columnSplit, double rowSplit,
            int vastuScore, int naturalLightScore, int spaceEfficiencyScore, List<String> explanations) {}
    private record Envelope(double minimumX, double maximumX, double minimumY, double maximumY) {}
    private record OpeningInterval(String floor, String axis, double wallLine, double from, double to, String id) {}
    private record SharedEdge(RoomGeometry first, RoomGeometry second, String orientationFromFirst,
            double x, double y, double span, double from, double to, boolean vertical) {
        RoomGeometry other(RoomGeometry room) {
            if (first.id().equals(room.id())) return second;
            if (second.id().equals(room.id())) return first;
            return null;
        }

        String orientationFrom(RoomGeometry room) {
            if (first.id().equals(room.id())) return orientationFromFirst;
            return switch (orientationFromFirst) {
                case "NORTH" -> "SOUTH";
                case "SOUTH" -> "NORTH";
                case "EAST" -> "WEST";
                default -> "EAST";
            };
        }
    }
}

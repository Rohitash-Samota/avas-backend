package com.avas.platform.project;

import java.util.List;
import java.util.Set;

public record PlanningParameterSet(
        String requestId,
        String providerRequestId,
        String provider,
        String model,
        String promptVersion,
        String schemaVersion,
        boolean fallbackUsed,
        List<String> warnings,
        List<PlanningParameterVariant> variants
) {
    public PlanningParameterSet(String requestId, String provider, String model, String promptVersion,
            String schemaVersion, boolean fallbackUsed, List<String> warnings,
            List<PlanningParameterVariant> variants) {
        this(requestId, null, provider, model, promptVersion, schemaVersion, fallbackUsed, warnings, variants);
    }

    public PlanningParameterSet {
        provider = required(provider, "provider");
        model = required(model, "model");
        promptVersion = required(promptVersion, "promptVersion");
        schemaVersion = required(schemaVersion, "schemaVersion");
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        variants = variants == null ? List.of() : List.copyOf(variants);
        var expected = Set.of("BUDGET_OPTIMIZED", "BALANCED", "LIFESTYLE_OPTIMIZED");
        var actual = variants.stream().map(PlanningParameterVariant::strategy)
                .collect(java.util.stream.Collectors.toSet());
        if (variants.size() != 3 || !actual.equals(expected)) {
            throw new IllegalArgumentException("Planning parameters require exactly one variant per AVAS strategy");
        }
    }

    public static PlanningParameterSet deterministic(BasicDetailsRequest details, String warning) {
        var value = details.parameters();
        var strategies = List.of("BUDGET_OPTIMIZED", "BALANCED", "LIFESTYLE_OPTIMIZED");
        var titles = List.of("Efficient Courtyard", "Garden Threshold", "Lightwell House");
        var variants = new java.util.ArrayList<PlanningParameterVariant>();
        for (var index = 0; index < strategies.size(); index++) {
            var weights = switch (index) {
                case 0 -> java.util.Map.of("budget", .40, "functionality", .25, "daylight", .12,
                        "accessibility", .13, "futureReadiness", .10);
                case 1 -> java.util.Map.of("budget", .24, "functionality", .30, "daylight", .17,
                        "accessibility", .17, "futureReadiness", .12);
                default -> java.util.Map.of("budget", .12, "functionality", .26, "daylight", .28,
                        "accessibility", .14, "futureReadiness", .20);
            };
            variants.add(new PlanningParameterVariant(strategies.get(index), titles.get(index),
                    details.floors() > 1
                            ? "Shared living on ground with private bedrooms above"
                            : "Accessible single-level living",
                    value.staircaseType(), value.liftProvision(), value.balconyCount(),
                    value.terraceRequired(), value.courtyardRequired(),
                    value.accessibleGroundFloor(),
                    value.parkingCars(), value.solarReady(), value.rainwaterHarvesting(),
                    deterministicRoomTargets(details, index),
                    weights,
                    List.of(programmeExplanation(details),
                            circulationExplanation(details),
                            "Room areas are concept targets; local rules and professional design remain authoritative.")));
        }
        return new PlanningParameterSet(null, null, "DETERMINISTIC", "avas-backend-parameter-rules-1.1.0",
                "home-parameters-1.1.0", "home-parameters-1", warning != null,
                warning == null ? List.of() : List.of(warning), List.copyOf(variants));
    }

    private static List<PlanningParameterVariant.RoomTarget> deterministicRoomTargets(
            BasicDetailsRequest details, int strategyIndex) {
        var targets = new java.util.ArrayList<PlanningParameterVariant.RoomTarget>();
        targets.add(target("LIVING_ROOM", "GROUND", 1,
                area(strategyIndex, 160, 200, 240), "REQUIRED"));
        targets.add(target("DINING", "GROUND", 1,
                area(strategyIndex, 100, 120, 150), "REQUIRED"));
        targets.add(target("KITCHEN", "GROUND", 1,
                area(strategyIndex, 80, 100, 125), "REQUIRED"));
        targets.add(target("BATHROOM", "GROUND", 1,
                details.family().seniorCitizens() > 0 || details.parameters().accessibleGroundFloor()
                        ? area(strategyIndex, 55, 60, 65) : area(strategyIndex, 40, 45, 55),
                "REQUIRED"));

        var floors = List.of("GROUND", "FIRST", "SECOND").subList(0, details.floors());
        if (details.floors() > 1) {
            for (var floor : floors) {
                targets.add(target("STAIRCASE", floor, 1,
                        area(strategyIndex, 75, 90, 105), "REQUIRED"));
                if (!"NONE".equals(details.parameters().liftProvision())) {
                    targets.add(target("LIFT_SHAFT", floor, 1,
                            "PASSENGER".equals(details.parameters().liftProvision())
                                    ? area(strategyIndex, 36, 36, 42)
                                    : area(strategyIndex, 30, 30, 36),
                            "REQUIRED"));
                }
            }
        }

        var bedrooms = bedroomRequirement(details);
        var bedroomFloors = bedroomFloors(floors, bedrooms);
        for (var index = 0; index < bedrooms; index++) {
            var floor = bedroomFloors.get(index);
            targets.add(target(index == 0 && details.family().seniorCitizens() > 0
                            ? "SENIOR_BEDROOM" : "BEDROOM", floor, 1,
                    index == 0 && details.family().seniorCitizens() > 0
                            ? area(strategyIndex, 130, 145, 160)
                            : area(strategyIndex, 110, 120, 140),
                    "REQUIRED"));
        }

        var attachedByFloor = new java.util.LinkedHashMap<String, Integer>();
        for (var index = 1; index < bedroomFloors.size(); index++) {
            attachedByFloor.merge(bedroomFloors.get(index), 1, Integer::sum);
        }
        attachedByFloor.forEach((floor, count) -> targets.add(target("ATTACHED_BATHROOM", floor,
                count, area(strategyIndex, 40, 45, 55), "PREFERRED")));

        if (details.family().regularGuests()) {
            targets.add(target("FLEX_GUEST_ROOM", floors.getLast(), 1,
                    area(strategyIndex, 105, 120, 140), "PREFERRED"));
        }
        if (details.floors() > 1 && strategyIndex > 0) {
            targets.add(target("FAMILY_LOUNGE", floors.getLast(), 1,
                    area(strategyIndex, 110, 140, 180), "PREFERRED"));
        }
        targets.add(target("UTILITY", "GROUND", 1,
                area(strategyIndex, 35, 50, 65), "PREFERRED"));

        var balconies = details.parameters().balconyCount();
        if (balconies > 0) {
            var balconyFloors = details.floors() > 1 ? floors.subList(1, floors.size()) : floors;
            for (var index = 0; index < balconyFloors.size(); index++) {
                var count = balconies / balconyFloors.size()
                        + (index < balconies % balconyFloors.size() ? 1 : 0);
                if (count > 0) {
                    targets.add(target("BALCONY", balconyFloors.get(index), count,
                            area(strategyIndex, 45, 60, 80), "PREFERRED"));
                }
            }
        }
        if (details.parameters().courtyardRequired()) {
            targets.add(target("COURTYARD", "GROUND", 1,
                    area(strategyIndex, 80, 120, 160), "PREFERRED"));
        }
        if (details.parameters().terraceRequired()) {
            targets.add(target("TERRACE", floors.getLast(), 1,
                    area(strategyIndex, 100, 120, 160), "OPTIONAL"));
        }
        return List.copyOf(targets);
    }

    private static int bedroomRequirement(BasicDetailsRequest details) {
        var adults = (details.family().adults() + 1) / 2;
        var seniors = (details.family().seniorCitizens() + 1) / 2;
        return Math.max(2, Math.min(6, adults + details.family().children() + seniors));
    }

    private static List<String> bedroomFloors(List<String> floors, int bedrooms) {
        if (floors.size() == 1) return java.util.Collections.nCopies(bedrooms, "GROUND");
        var result = new java.util.ArrayList<String>();
        result.add("GROUND");
        var upper = floors.subList(1, floors.size());
        for (var index = 0; index < bedrooms - 1; index++) result.add(upper.get(index % upper.size()));
        return List.copyOf(result);
    }

    private static double area(int strategyIndex, double budget, double balanced, double lifestyle) {
        return switch (strategyIndex) {
            case 0 -> budget;
            case 1 -> balanced;
            default -> lifestyle;
        };
    }

    private static String programmeExplanation(BasicDetailsRequest details) {
        var guests = details.family().regularGuests() ? " plus a preferred flex/guest room" : "";
        return bedroomRequirement(details) + " core bedrooms are recommended for "
                + details.family().members() + " permanent residents" + guests + ".";
    }

    private static String circulationExplanation(BasicDetailsRequest details) {
        var lift = switch (details.parameters().liftProvision()) {
            case "PASSENGER" -> "one passenger lift shaft";
            case "FUTURE_SHAFT" -> "one future lift shaft";
            default -> "no lift";
        };
        return "Selected circulation keeps " + lift + " and " + details.parameters().balconyCount()
                + " balcony space(s) unchanged across all three options.";
    }

    private static PlanningParameterVariant.RoomTarget target(String type, String floor, int count,
            double area, String priority) {
        return new PlanningParameterVariant.RoomTarget(type, floor, count, area, priority);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Planning parameter " + field + " is required");
        }
        return value.trim();
    }
}

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
            var lifestyle = index == 2;
            var balconies = index == 0 ? Math.max(0, value.balconyCount() - 1)
                    : lifestyle ? Math.min(6, value.balconyCount() + 1) : value.balconyCount();
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
                    value.staircaseType(), value.liftProvision(), balconies,
                    value.terraceRequired() || lifestyle, value.courtyardRequired() || lifestyle,
                    value.accessibleGroundFloor(),
                    value.parkingCars(), value.solarReady(), value.rainwaterHarvesting(),
                    deterministicRoomTargets(details, balconies, value.terraceRequired() || lifestyle),
                    weights,
                    List.of("Explicit project parameters are preserved for deterministic geometry.",
                            "Room targets are bounded inputs; AVAS geometry and hard-rule validation remain authoritative.")));
        }
        return new PlanningParameterSet(null, null, "DETERMINISTIC", "avas-backend-parameter-rules-1.0.0",
                "home-parameters-1.0.0", "home-parameters-1", warning != null,
                warning == null ? List.of() : List.of(warning), List.copyOf(variants));
    }

    private static List<PlanningParameterVariant.RoomTarget> deterministicRoomTargets(
            BasicDetailsRequest details, int balconies, boolean terrace) {
        var targets = new java.util.ArrayList<PlanningParameterVariant.RoomTarget>();
        targets.add(target("LIVING_ROOM", "GROUND", 1, 240, "REQUIRED"));
        targets.add(target("DINING", "GROUND", 1, 150, "REQUIRED"));
        targets.add(target("KITCHEN", "GROUND", 1, 140, "REQUIRED"));
        targets.add(target("STAIRCASE", "GROUND", 1, 90, "REQUIRED"));
        targets.add(target("BATHROOM", "GROUND", 1, 45, "REQUIRED"));
        var bedrooms = Math.max(2, Math.min(6, 1 + details.family().children()
                + (details.family().seniorCitizens() > 0 ? 1 : 0)));
        for (var index = 0; index < bedrooms; index++) {
            var floor = index == 0 || details.floors() == 1 ? "GROUND"
                    : index % Math.max(1, details.floors() - 1) == 0 ? "FIRST" : "SECOND";
            if (details.floors() == 2) floor = index == 0 ? "GROUND" : "FIRST";
            targets.add(target(index == 0 && details.family().seniorCitizens() > 0
                    ? "SENIOR_BEDROOM" : "BEDROOM", floor, 1, 150, "REQUIRED"));
        }
        if (details.floors() > 1) {
            targets.add(target("FAMILY_LOUNGE", details.floors() == 2 ? "FIRST" : "SECOND",
                    1, 170, "PREFERRED"));
        }
        if (balconies > 0 && details.floors() > 1) {
            targets.add(target("BALCONY", details.floors() == 2 ? "FIRST" : "SECOND",
                    balconies, 60, "PREFERRED"));
        }
        if (terrace) {
            targets.add(target("TERRACE", details.floors() == 1 ? "GROUND"
                    : details.floors() == 2 ? "FIRST" : "SECOND", 1, 120, "OPTIONAL"));
        }
        return List.copyOf(targets);
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

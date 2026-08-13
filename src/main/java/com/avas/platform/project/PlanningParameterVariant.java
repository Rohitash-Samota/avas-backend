package com.avas.platform.project;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record PlanningParameterVariant(
        String strategy,
        String title,
        String duplexZoning,
        String staircaseType,
        String liftProvision,
        int balconyCount,
        boolean terraceRequired,
        boolean courtyardRequired,
        boolean accessibleGroundFloor,
        int parkingCars,
        boolean solarReady,
        boolean rainwaterHarvesting,
        List<RoomTarget> roomTargets,
        Map<String, Double> weights,
        List<String> explanations
) {
    private static final Set<String> STRATEGIES = Set.of(
            "BUDGET_OPTIMIZED", "BALANCED", "LIFESTYLE_OPTIMIZED");
    private static final Set<String> STAIRCASES = Set.of(
            "DOG_LEGGED", "L_SHAPED", "U_SHAPED", "STRAIGHT");
    private static final Set<String> LIFTS = Set.of("NONE", "FUTURE_SHAFT", "PASSENGER");
    private static final Set<String> WEIGHTS = Set.of(
            "budget", "functionality", "daylight", "accessibility", "futureReadiness");

    public PlanningParameterVariant {
        strategy = member(strategy, STRATEGIES, "strategy");
        title = required(title, "title");
        duplexZoning = required(duplexZoning, "duplexZoning");
        staircaseType = member(staircaseType, STAIRCASES, "staircaseType");
        liftProvision = member(liftProvision, LIFTS, "liftProvision");
        if (balconyCount < 0 || balconyCount > 6) throw new IllegalArgumentException("balconyCount must be 0-6");
        if (parkingCars < 0 || parkingCars > 6) throw new IllegalArgumentException("parkingCars must be 0-6");
        roomTargets = roomTargets == null ? List.of() : List.copyOf(roomTargets);
        if (roomTargets.size() < 5 || roomTargets.size() > 40) {
            throw new IllegalArgumentException("Planning parameter variants require 5-40 room targets");
        }
        weights = weights == null ? Map.of() : Map.copyOf(weights);
        if (!weights.keySet().equals(WEIGHTS)
                || weights.values().stream().anyMatch(value -> value == null || value < 0 || value > 1)
                || Math.abs(weights.values().stream().mapToDouble(Double::doubleValue).sum() - 1) > .01) {
            throw new IllegalArgumentException("Planning parameter weights must contain the five AVAS weights and total 1.0");
        }
        explanations = explanations == null ? List.of() : List.copyOf(explanations);
        if (explanations.size() < 2 || explanations.size() > 6) {
            throw new IllegalArgumentException("Planning parameter variants require 2-6 explanations");
        }
    }

    public record RoomTarget(String roomType, String floor, int count, double targetAreaSqFt,
                             String priority) {
        public RoomTarget {
            roomType = required(roomType, "roomType");
            floor = member(floor, Set.of("GROUND", "FIRST", "SECOND"), "floor");
            priority = member(priority, Set.of("REQUIRED", "PREFERRED", "OPTIONAL"), "priority");
            if (count < 1 || count > 8) throw new IllegalArgumentException("room target count must be 1-8");
            if (targetAreaSqFt < 12 || targetAreaSqFt > 2_500) {
                throw new IllegalArgumentException("room target area must be 12-2500 sq ft");
            }
        }
    }

    private static String member(String value, Set<String> accepted, String field) {
        var normalized = required(value, field).toUpperCase(java.util.Locale.ROOT);
        if (!accepted.contains(normalized)) throw new IllegalArgumentException("Invalid " + field);
        return normalized;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}

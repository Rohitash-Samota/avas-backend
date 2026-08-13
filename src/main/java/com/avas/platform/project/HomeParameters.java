package com.avas.platform.project;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Explicit, user-owned planning parameters. These values are persisted with the requirement
 * snapshot and bound any AI proposal; they are never inferred from free-form text alone.
 */
public record HomeParameters(
        @Pattern(regexp = "BUNGALOW|DUPLEX|MULTI_STOREY") String homeType,
        @Pattern(regexp = "DOG_LEGGED|L_SHAPED|U_SHAPED|STRAIGHT") String staircaseType,
        @Pattern(regexp = "NONE|FUTURE_SHAFT|PASSENGER") String liftProvision,
        @Min(0) @Max(6) int balconyCount,
        boolean terraceRequired,
        boolean courtyardRequired,
        boolean accessibleGroundFloor,
        @Min(0) @Max(6) int parkingCars,
        boolean solarReady,
        boolean rainwaterHarvesting
) {
    public HomeParameters {
        homeType = blankDefault(homeType, "DUPLEX");
        staircaseType = blankDefault(staircaseType, "DOG_LEGGED");
        liftProvision = blankDefault(liftProvision, "NONE");
    }

    public static HomeParameters defaults(int floors, double plotArea, boolean seniorPresent,
            java.util.List<String> preferences) {
        var normalized = preferences == null ? java.util.List.<String>of() : preferences.stream()
                .map(value -> value.toLowerCase(java.util.Locale.ROOT)).toList();
        return new HomeParameters(
                floors == 1 ? "BUNGALOW" : floors == 2 ? "DUPLEX" : "MULTI_STOREY",
                "DOG_LEGGED",
                floors > 2 || seniorPresent ? "FUTURE_SHAFT" : "NONE",
                floors > 1 ? 1 : 0,
                normalized.stream().anyMatch(value -> value.contains("terrace")),
                normalized.stream().anyMatch(value -> value.contains("courtyard") || value.contains("garden")),
                seniorPresent,
                plotArea >= 1_800 ? 2 : 1,
                normalized.stream().anyMatch(value -> value.contains("solar")),
                normalized.stream().anyMatch(value -> value.contains("rainwater")));
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}

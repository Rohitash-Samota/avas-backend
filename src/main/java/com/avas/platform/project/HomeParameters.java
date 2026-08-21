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
        boolean rainwaterHarvesting,
        @Pattern(regexp = "FULL_PLOT|STANDARD_SETBACK|OPEN_SPACE") String plotUsage
) {
    /**
     * Build across the whole plot: no open-space ring, and the cars come indoors.
     *
     * <p>This is the AVAS default because it is what the plots we plan for are actually built to —
     * but it is a customer instruction, not a compliance position. An envelope generated this way
     * carries an explicit note that it will not satisfy the authority's open-space rule.</p>
     */
    public static final String FULL_PLOT = "FULL_PLOT";
    /** Assumed front, rear and side setbacks, with open ground shown only where the finish pays for it. */
    public static final String STANDARD_SETBACK = "STANDARD_SETBACK";
    /** Assumed setbacks, with the ground they leave deliberately planned as garden and parking. */
    public static final String OPEN_SPACE = "OPEN_SPACE";

    public HomeParameters {
        homeType = blankDefault(homeType, "DUPLEX");
        staircaseType = blankDefault(staircaseType, "DOG_LEGGED");
        liftProvision = blankDefault(liftProvision, "NONE");
        plotUsage = blankDefault(plotUsage, FULL_PLOT);
    }

    /**
     * Retains the pre-{@code plotUsage} arity so callers written before plot usage existed still
     * compile. They resolve to the current default, exactly as an absent JSON field does.
     */
    public HomeParameters(String homeType, String staircaseType, String liftProvision, int balconyCount,
            boolean terraceRequired, boolean courtyardRequired, boolean accessibleGroundFloor,
            int parkingCars, boolean solarReady, boolean rainwaterHarvesting) {
        this(homeType, staircaseType, liftProvision, balconyCount, terraceRequired, courtyardRequired,
                accessibleGroundFloor, parkingCars, solarReady, rainwaterHarvesting, FULL_PLOT);
    }

    public static HomeParameters defaults(int floors, double plotArea, boolean seniorPresent,
            java.util.List<String> preferences) {
        return defaults(floors, plotArea, seniorPresent, preferences, SpecificationTier.STANDARD);
    }

    /**
     * The parameters a brief implies, at the finish tier the brief was priced at.
     *
     * <p>These are the values a customer sees pre-filled and remains free to change; the tier only
     * moves the starting point. A home costed as luxury that defaulted to one car bay and no lift
     * was quoting one house and offering to draw another, and the customer had no way to know which
     * of the two the price belonged to.</p>
     */
    public static HomeParameters defaults(int floors, double plotArea, boolean seniorPresent,
            java.util.List<String> preferences, SpecificationTier tier) {
        var normalized = preferences == null ? java.util.List.<String>of() : preferences.stream()
                .map(value -> value.toLowerCase(java.util.Locale.ROOT)).toList();
        var specification = tier == null ? SpecificationTier.STANDARD : tier;
        var luxury = specification == SpecificationTier.LUXURY;
        return new HomeParameters(
                floors == 1 ? "BUNGALOW" : floors == 2 ? "DUPLEX" : "MULTI_STOREY",
                "DOG_LEGGED",
                // A lift is the one provision that cannot be added later without opening the slab,
                // so at the tier that pays for it the shaft is offered by default on every duplex.
                floors > 1 && (floors > 2 || seniorPresent || luxury) ? "FUTURE_SHAFT" : "NONE",
                floors > 1 ? (luxury ? floors - 1 : 1) : 0,
                normalized.stream().anyMatch(value -> value.contains("terrace")) || luxury,
                normalized.stream().anyMatch(value -> value.contains("courtyard") || value.contains("garden")),
                seniorPresent,
                specification.minimumParkingBays(plotArea >= 1_800 ? 2 : 1),
                normalized.stream().anyMatch(value -> value.contains("solar")),
                normalized.stream().anyMatch(value -> value.contains("rainwater")),
                FULL_PLOT);
    }

    /** True when the home is planned across the entire plot outline, with no setback ring. */
    public boolean usesFullPlot() {
        return FULL_PLOT.equals(plotUsage);
    }

    /** True when the ground left outside the building is planned as garden rather than left over. */
    public boolean plansOpenSpace() {
        return OPEN_SPACE.equals(plotUsage);
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}

package com.avas.platform.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BasicDetailsRequest(
        @Min(10) double plotWidth,
        @Min(10) double plotLength,
        @NotNull Facing roadFacing,
        @NotBlank String city,
        @Min(1) @Max(3) int floors,
        @Min(1_000_000) long budget,
        @NotNull Category category,
        @NotNull @Valid FamilyDetails family,
        @Size(max = 5) List<String> preferences,
        @Valid HomeParameters parameters,
        @Valid PlotBoundary plotBoundary
) {
    public BasicDetailsRequest {
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
        if (plotBoundary != null) {
            // The outline is authoritative once supplied. Width and length are kept as the bounding
            // box so every existing consumer keeps working, and the two can never disagree.
            var box = plotBoundary.bounds();
            plotWidth = round(box.width());
            plotLength = round(box.length());
            roadFacing = plotBoundary.roadFacing();
        }
        var area = plotBoundary != null ? plotBoundary.area() : plotWidth * plotLength;
        parameters = parameters == null
                ? HomeParameters.defaults(floors, area, family != null && family.seniorCitizens() > 0,
                        preferences,
                        // The tier the budget actually buys, so an unspecified brief starts from the
                        // provisions its own money covers rather than from the cheapest assumption.
                        SpecificationTier.of(category, budget, area * floors * 0.82d))
                : parameters;
    }

    public BasicDetailsRequest(double plotWidth, double plotLength, Facing roadFacing, String city,
            int floors, long budget, Category category, FamilyDetails family, List<String> preferences,
            HomeParameters parameters) {
        this(plotWidth, plotLength, roadFacing, city, floors, budget, category, family, preferences,
                parameters, null);
    }

    public BasicDetailsRequest(double plotWidth, double plotLength, Facing roadFacing, String city,
            int floors, long budget, Category category, FamilyDetails family, List<String> preferences) {
        this(plotWidth, plotLength, roadFacing, city, floors, budget, category, family, preferences, null, null);
    }

    /**
     * The plot outline to plan against.
     *
     * <p>Falls back to the rectangle implied by width and length so projects created before the
     * boundary editor existed keep generating identical geometry.</p>
     */
    public PlotBoundary boundary() {
        return plotBoundary != null ? plotBoundary : PlotBoundary.rectangle(plotWidth, plotLength, roadFacing);
    }

    /** True enclosed area of the outline, which is smaller than the bounding box on irregular plots. */
    public double plotArea() {
        return plotBoundary != null ? plotBoundary.area() : plotWidth * plotLength;
    }

    /** Bounding-box area; retained for the coverage bands that were calibrated against it. */
    public double boundingArea() {
        return plotWidth * plotLength;
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}

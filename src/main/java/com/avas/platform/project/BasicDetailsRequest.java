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
        @Valid HomeParameters parameters
) {
    public BasicDetailsRequest {
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
        parameters = parameters == null
                ? HomeParameters.defaults(floors, plotWidth * plotLength,
                        family != null && family.seniorCitizens() > 0, preferences)
                : parameters;
    }

    public BasicDetailsRequest(double plotWidth, double plotLength, Facing roadFacing, String city,
            int floors, long budget, Category category, FamilyDetails family, List<String> preferences) {
        this(plotWidth, plotLength, roadFacing, city, floors, budget, category, family, preferences, null);
    }

    public double plotArea() {
        return plotWidth * plotLength;
    }
}

package com.avas.platform.project;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FamilyDetails(
        @Min(0) @Max(10) int adults,
        @Min(0) @Max(10) int children,
        @Min(0) @Max(10) int seniorCitizens,
        boolean regularGuests
) {
    public int members() {
        return adults + children + seniorCitizens;
    }
}

package com.avas.platform.project;

import java.util.List;
import java.util.Map;

public record Recommendation(
        String id,
        String title,
        String category,
        int bedrooms,
        int attachedBathrooms,
        int commonBathrooms,
        int parkingCars,
        int builtUpAreaMinimum,
        int builtUpAreaMaximum,
        long estimatedCostLow,
        long estimatedCostHigh,
        boolean seniorCitizenBedroom,
        boolean familyLounge,
        boolean futureExpansion,
        int confidence,
        List<String> reasons,
        Map<String, String> provenance,
        boolean accepted
) {}

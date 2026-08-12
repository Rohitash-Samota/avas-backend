package com.avas.platform.project;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DrawingCandidate(
        String id,
        String projectId,
        int version,
        String strategy,
        String name,
        int builtUpArea,
        long estimatedCostLow,
        long estimatedCostHigh,
        int vastuScore,
        int naturalLightScore,
        int spaceEfficiencyScore,
        int confidence,
        GeometryDocument geometry,
        List<String> hardViolations,
        List<String> softRecommendations,
        List<String> explanations,
        Map<String, String> versions,
        String status,
        boolean conceptApproved,
        Instant createdAt
) {}

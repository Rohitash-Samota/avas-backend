package com.avas.platform.project;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RequirementSummary(
        String snapshotId,
        int version,
        String projectId,
        String handlingLevel,
        BasicDetailsRequest explicitInputs,
        Recommendation inferredRequirements,
        List<String> assumptions,
        List<String> questions,
        Map<String, String> versions,
        Instant approvedAt
) {}

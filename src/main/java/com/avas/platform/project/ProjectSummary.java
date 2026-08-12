package com.avas.platform.project;

import java.time.Instant;

public record ProjectSummary(
        String id,
        String projectCode,
        String name,
        StartMode startMode,
        String status,
        int currentSnapshotVersion,
        BasicDetailsRequest details,
        Instant createdAt,
        Instant updatedAt
) {}

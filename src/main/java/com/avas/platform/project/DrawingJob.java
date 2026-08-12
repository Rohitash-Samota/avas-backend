package com.avas.platform.project;

import java.time.Instant;
import java.util.List;

public record DrawingJob(
        String id,
        String projectId,
        JobStatus status,
        int progress,
        String stage,
        List<String> candidateIds,
        String inputSnapshotId,
        Instant createdAt,
        Instant completedAt
) {}

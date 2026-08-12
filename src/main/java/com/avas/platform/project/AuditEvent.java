package com.avas.platform.project;

import java.time.Instant;

public record AuditEvent(
        String id,
        String projectId,
        String action,
        String actorRole,
        String artifactId,
        String version,
        String detail,
        Instant at
) {}

package com.avas.platform.project;

import java.util.List;

public record ProjectStateSnapshot(
        ProjectSummary project,
        Recommendation recommendation,
        RequirementSummary requirement,
        List<DrawingCandidate> drawings,
        List<DrawingJob> jobs,
        List<Estimate> estimates,
        List<AuditEvent> audit
) {
    public ProjectStateSnapshot {
        drawings = drawings == null ? List.of() : List.copyOf(drawings);
        jobs = jobs == null ? List.of() : List.copyOf(jobs);
        estimates = estimates == null ? List.of() : List.copyOf(estimates);
        audit = audit == null ? List.of() : List.copyOf(audit);
    }
}

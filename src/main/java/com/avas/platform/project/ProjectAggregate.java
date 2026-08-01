package com.avas.platform.project;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.avas.platform.project.ProjectModels.*;

final class ProjectAggregate {
    final String id;
    final String code;
    final String name;
    final StartMode startMode;
    final Instant createdAt;
    Instant updatedAt;
    String status = "DRAFT";
    int snapshotVersion = 0;
    BasicDetailsRequest details;
    Recommendation recommendation;
    RequirementSummary requirementSummary;
    final List<DrawingCandidate> drawings = new ArrayList<>();
    final List<DrawingJob> jobs = new ArrayList<>();
    final List<Estimate> estimates = new ArrayList<>();
    final List<AuditEvent> audit = new ArrayList<>();

    ProjectAggregate(String id, String code, String name, StartMode startMode) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.startMode = startMode;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    ProjectAggregate(ProjectSummary summary) {
        this.id = summary.id(); this.code = summary.projectCode(); this.name = summary.name(); this.startMode = summary.startMode();
        this.createdAt = summary.createdAt(); this.updatedAt = summary.updatedAt(); this.status = summary.status();
        this.snapshotVersion = summary.currentSnapshotVersion(); this.details = summary.details();
    }

    ProjectAggregate(ProjectStateSnapshot state) {
        this(state.project());
        this.recommendation = state.recommendation(); this.requirementSummary = state.requirement();
        this.drawings.addAll(state.drawings()); this.jobs.addAll(state.jobs()); this.estimates.addAll(state.estimates()); this.audit.addAll(state.audit());
    }

    ProjectSummary summary() {
        return new ProjectSummary(id, code, name, startMode, status, snapshotVersion, details, createdAt, updatedAt);
    }
}

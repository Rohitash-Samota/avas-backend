package com.avas.platform.project;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
    /**
     * Legal envelope for the current details, resolved once and reused.
     *
     * <p>The recommendation, the geometry engine and the estimate must all reason about the same
     * buildable footprint, otherwise a plan is flagged against a target it was never given.
     * Rebuilt lazily after a reload because it is derived state, not stored state.</p>
     */
    private BuildableEnvelope envelope;
    Recommendation recommendation;
    RequirementSummary requirementSummary;
    /**
     * The parameter set the current concept set was generated from.
     *
     * <p>Held so that arranging one concept with AVAS AI later reuses the programme that concept was
     * costed against. Re-fetching it would ask the parameter model the same question a second time
     * and quite possibly get a different answer — and a concept whose card promises four bedrooms
     * must not be re-planned as a three-bedroom house because the arrangement ran an hour later.</p>
     *
     * <p>Null after a reload, because it is derived state rather than stored state. A lazy
     * arrangement then declines rather than guessing; the concept keeps the plan it already has.</p>
     */
    PlanningParameterSet parameterSet;
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

    /**
     * Buildable envelope for the current details.
     *
     * @throws IllegalArgumentException when the outline and setbacks leave nothing to build on
     */
    BuildableEnvelope envelope() {
        if (details == null) {
            throw new IllegalStateException("Basic details are required before the envelope can be resolved");
        }
        if (envelope == null) {
            var boundary = details.boundary();
            envelope = BuildableEnvelope.derive(boundary,
                    SetbackRule.forUsage(boundary, details.floors(), details.parameters().plotUsage()),
                    details.floors(), details.roadFacing(), details.parameters().parkingCars());
        }
        return envelope;
    }

    /** Drops the cached envelope after any change that can move the buildable footprint. */
    void invalidateEnvelope() {
        envelope = null;
    }
}

package com.avas.platform.project.persistence;

import com.avas.platform.project.ProjectModels.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class ProjectPersistenceService {
    private final ProjectRecordRepository projects;
    private final RequirementSnapshotRepository requirements;
    private final DrawingArtifactRepository drawings;
    private final EstimateArtifactRepository estimates;
    private final ProjectAuditRecordRepository audit;
    private final ProjectStateRepository states;
    private final ObjectMapper json;

    public ProjectPersistenceService(ProjectRecordRepository projects, RequirementSnapshotRepository requirements,
                                     DrawingArtifactRepository drawings, EstimateArtifactRepository estimates,
                                     ProjectAuditRecordRepository audit, ProjectStateRepository states, ObjectMapper json) {
        this.projects = projects; this.requirements = requirements; this.drawings = drawings;
        this.estimates = estimates; this.audit = audit; this.states = states; this.json = json;
    }

    @Transactional
    public void save(ProjectSummary value, String tenantId, UUID ownerUserId) {
        var detailsJson = value.details() == null ? null : json(value.details());
        var record = projects.findById(value.id()).orElseGet(() -> new ProjectRecordEntity(value, detailsJson, tenantId, ownerUserId));
        record.updateOwnership(tenantId, ownerUserId);
        record.update(value, detailsJson);
        projects.save(record);
    }

    @Transactional public void save(RequirementSummary value) {
        if (!requirements.existsById(value.snapshotId())) requirements.save(new RequirementSnapshotEntity(value, json(value)));
    }
    @Transactional public void save(DrawingCandidate value) {
        var payload = json(value);
        var record = drawings.findById(value.id()).orElseGet(() -> new DrawingArtifactEntity(value, payload));
        record.update(value, payload); drawings.save(record);
    }
    @Transactional public void save(Estimate value) {
        var payload = json(value);
        var record = estimates.findById(value.id()).orElseGet(() -> new EstimateArtifactEntity(value, payload));
        record.update(value, payload); estimates.save(record);
    }
    @Transactional public void save(AuditEvent value) {
        if (!audit.existsByEventId(value.id())) audit.save(new ProjectAuditRecordEntity(value));
    }

    /**
     * Persists one complete project workflow snapshot atomically. The queryable projections and the
     * rehydration document either commit together or roll back together, preventing a restart from
     * observing a partially saved project.
     */
    @Transactional public void save(ProjectStateSnapshot value, String tenantId, UUID ownerUserId) {
        var project = value.project();
        var detailsJson = project.details() == null ? null : json(project.details());
        var projectRecord = projects.findById(project.id())
                .orElseGet(() -> new ProjectRecordEntity(project, detailsJson, tenantId, ownerUserId));
        projectRecord.updateOwnership(tenantId, ownerUserId);
        projectRecord.update(project, detailsJson);
        projects.save(projectRecord);

        if (value.requirement() != null && !requirements.existsById(value.requirement().snapshotId())) {
            requirements.save(new RequirementSnapshotEntity(value.requirement(), json(value.requirement())));
        }
        value.drawings().forEach(drawing -> {
            var payload = json(drawing);
            var record = drawings.findById(drawing.id()).orElseGet(() -> new DrawingArtifactEntity(drawing, payload));
            record.update(drawing, payload);
            drawings.save(record);
        });
        value.estimates().forEach(estimate -> {
            var payload = json(estimate);
            var record = estimates.findById(estimate.id()).orElseGet(() -> new EstimateArtifactEntity(estimate, payload));
            record.update(estimate, payload);
            estimates.save(record);
        });
        value.audit().forEach(event -> {
            if (!audit.existsByEventId(event.id())) audit.save(new ProjectAuditRecordEntity(event));
        });

        var payload = json(value);
        var state = states.findById(project.id()).orElseGet(() -> new ProjectStateEntity(project.id(), payload));
        state.update(payload);
        states.save(state);
    }

    @Transactional(readOnly = true)
    public java.util.List<ProjectStateSnapshot> loadStates() {
        return states.findAll().stream().map(value -> read(value.payloadJson(), ProjectStateSnapshot.class)).toList();
    }

    /** Removes the exact legacy sample record created by pre-production releases. */
    @Transactional
    public void retireLegacySampleProject() {
        var legacyId = "demo-project";
        if (!projects.existsById(legacyId) && !states.existsById(legacyId)) return;
        estimates.deleteAllByProjectId(legacyId);
        drawings.deleteAllByProjectId(legacyId);
        requirements.deleteAllByProjectId(legacyId);
        audit.deleteAllByProjectId(legacyId);
        states.deleteById(legacyId);
        projects.deleteById(legacyId);
    }

    @Transactional(readOnly = true)
    public Optional<ProjectStateSnapshot> loadState(String projectId) {
        return states.findById(projectId).map(value -> read(value.payloadJson(), ProjectStateSnapshot.class));
    }

    public record ProjectAccess(String projectId, String tenantId, UUID ownerUserId) {}

    @Transactional(readOnly = true)
    public java.util.List<ProjectAccess> loadAccess() {
        return projects.findAll().stream()
                .map(value -> new ProjectAccess(value.id(), value.tenantId(), value.ownerUserId())).toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<ProjectSummary> list(String tenantId, UUID ownerUserId, boolean tenantWide) {
        var records = tenantWide ? projects.findAllByTenantIdOrderByUpdatedAtDesc(tenantId)
                : projects.findAllByTenantIdAndOwnerUserIdOrderByUpdatedAtDesc(tenantId, ownerUserId);
        return records.stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<ProjectSummary> loadProjectSummaries() {
        return projects.findAll().stream().map(this::summary).toList();
    }

    private ProjectSummary summary(ProjectRecordEntity value) {
        return new ProjectSummary(value.id(), value.projectCode(), value.name(),
                com.avas.platform.project.ProjectModels.StartMode.valueOf(value.startMode()), value.status(), value.snapshotVersion(),
                value.detailsJson() == null ? null : read(value.detailsJson(), BasicDetailsRequest.class), value.createdAt(), value.updatedAt());
    }

    private String json(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to persist project artifact", exception); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to restore project state", exception); }
    }
}

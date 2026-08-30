package com.avas.platform.project.persistence;

import com.avas.platform.project.AuditEvent;
import com.avas.platform.project.ConceptRenderClient;
import com.avas.platform.project.BasicDetailsRequest;
import com.avas.platform.project.DrawingCandidate;
import com.avas.platform.project.Estimate;
import com.avas.platform.project.ProjectStateSnapshot;
import com.avas.platform.project.ProjectSummary;
import com.avas.platform.project.RequirementSummary;
import com.avas.platform.project.StartMode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final ConceptRenderRepository renders;
    private final ObjectMapper json;

    public ProjectPersistenceService(ProjectRecordRepository projects, RequirementSnapshotRepository requirements,
                                     DrawingArtifactRepository drawings, EstimateArtifactRepository estimates,
                                     ProjectAuditRecordRepository audit, ProjectStateRepository states,
                                     ConceptRenderRepository renders, ObjectMapper json) {
        this.projects = projects; this.requirements = requirements; this.drawings = drawings;
        this.estimates = estimates; this.audit = audit; this.states = states;
        this.renders = renders; this.json = json;
    }

    /**
     * The picture already drawn for this concept, if there is one.
     *
     * <p>Read before the image model is asked, not after, because asking is the expensive and
     * irreversible half: a second call bills again and returns a different house, so a stored answer
     * is not an optimisation here but the only thing that makes the illustration stable.</p>
     */
    @Transactional(readOnly = true)
    public Optional<ConceptRenderClient.Render> findRender(String drawingId, String style, String brief) {
        return renders.findByDrawingIdAndStyleAndBriefKey(drawingId, style, briefKey(brief))
                .map(value -> new ConceptRenderClient.Render(value.image(), value.mediaType(), value.prompt(),
                        value.provider(), value.model(), false, readWarnings(value.warningsJson())));
    }

    /**
     * The last picture generated for a style/storey prefix, whatever brief or presentation options
     * produced it. Used only when assembling the PDF set: the document should carry what the customer
     * most recently drew, not silently omit it because they chose a palette or wrote a floor brief.
     */
    @Transactional(readOnly = true)
    public Optional<ConceptRenderClient.Render> findLatestRender(String drawingId, String stylePrefix) {
        return renders.findFirstByDrawingIdAndStyleStartingWithOrderByCreatedAtDesc(drawingId, stylePrefix)
                .map(value -> new ConceptRenderClient.Render(value.image(), value.mediaType(), value.prompt(),
                        value.provider(), value.model(), false, readWarnings(value.warningsJson())));
    }

    /** Keeps the picture against the concept, so the concept looks the same the next time it is opened. */
    @Transactional
    public void saveRender(String drawingId, String style, String brief, ConceptRenderClient.Render value) {
        if (value == null || value.image().length == 0) return;
        var key = briefKey(brief);
        var warnings = json(value.warnings());
        var record = renders.findByDrawingIdAndStyleAndBriefKey(drawingId, style, key)
                .orElseGet(() -> new ConceptRenderEntity(drawingId, style, key, value.mediaType(), value.image(),
                        value.prompt(), value.provider(), value.model(), warnings));
        record.update(value.mediaType(), value.image(), value.prompt(), value.provider(), value.model(), warnings);
        renders.save(record);
    }

    /** Forgets the stored picture so the next view draws a new one. Used by an explicit redraw. */
    @Transactional
    public void forgetRender(String drawingId, String style, String brief) {
        renders.findByDrawingIdAndStyleAndBriefKey(drawingId, style, briefKey(brief)).ifPresent(renders::delete);
    }

    /**
     * One value for "no brief", because a unique index cannot compare two nulls.
     *
     * <p>Left null, MySQL treats every brief-less render as distinct from every other, the lookup
     * never matches, and the store silently degenerates into a log of pictures nobody is shown.</p>
     */
    private String briefKey(String brief) {
        var trimmed = brief == null ? "" : brief.trim();
        return trimmed.length() <= 400 ? trimmed : trimmed.substring(0, 400);
    }

    private List<String> readWarnings(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return json.readValue(value, json.getTypeFactory().constructCollectionType(List.class, String.class)); }
        catch (JsonProcessingException exception) { return List.of(); }
    }

    @Transactional
    public void save(ProjectSummary value, String tenantId, UUID ownerUserId) {
        var detailsJson = value.details() == null ? null : json(value.details());
        var record = projects.findByPublicId(value.id()).orElseGet(() -> new ProjectRecordEntity(value, detailsJson, tenantId, ownerUserId));
        record.updateOwnership(tenantId, ownerUserId);
        record.update(value, detailsJson);
        projects.save(record);
    }

    @Transactional public void save(RequirementSummary value) {
        if (!requirements.existsBySnapshotId(value.snapshotId())) requirements.save(new RequirementSnapshotEntity(value, json(value)));
    }
    @Transactional public void save(DrawingCandidate value) {
        var payload = json(value);
        var record = drawings.findByDrawingId(value.id()).orElseGet(() -> new DrawingArtifactEntity(value, payload));
        record.update(value, payload); drawings.save(record);
    }
    @Transactional public void save(Estimate value) {
        var payload = json(value);
        var record = estimates.findByEstimateId(value.id()).orElseGet(() -> new EstimateArtifactEntity(value, payload));
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
        var projectRecord = projects.findByPublicId(project.id())
                .orElseGet(() -> new ProjectRecordEntity(project, detailsJson, tenantId, ownerUserId));
        projectRecord.updateOwnership(tenantId, ownerUserId);
        projectRecord.update(project, detailsJson);
        projects.save(projectRecord);

        if (value.requirement() != null && !requirements.existsBySnapshotId(value.requirement().snapshotId())) {
            requirements.save(new RequirementSnapshotEntity(value.requirement(), json(value.requirement())));
        }
        value.drawings().forEach(drawing -> {
            var payload = json(drawing);
            var record = drawings.findByDrawingId(drawing.id()).orElseGet(() -> new DrawingArtifactEntity(drawing, payload));
            record.update(drawing, payload);
            drawings.save(record);
        });
        value.estimates().forEach(estimate -> {
            var payload = json(estimate);
            var record = estimates.findByEstimateId(estimate.id()).orElseGet(() -> new EstimateArtifactEntity(estimate, payload));
            record.update(estimate, payload);
            estimates.save(record);
        });
        value.audit().forEach(event -> {
            if (!audit.existsByEventId(event.id())) audit.save(new ProjectAuditRecordEntity(event));
        });

        var payload = json(value);
        var state = states.findByProjectId(project.id()).orElseGet(() -> new ProjectStateEntity(project.id(), payload));
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
        if (!projects.existsByPublicId(legacyId) && !states.existsByProjectId(legacyId)) return;
        estimates.deleteAllByProjectId(legacyId);
        var drawingIds = drawings.findAll().stream()
                .filter(value -> legacyId.equals(value.projectId())).map(DrawingArtifactEntity::drawingId).toList();
        if (!drawingIds.isEmpty()) renders.deleteAllByDrawingIdIn(drawingIds);
        drawings.deleteAllByProjectId(legacyId);
        requirements.deleteAllByProjectId(legacyId);
        audit.deleteAllByProjectId(legacyId);
        states.deleteByProjectId(legacyId);
        projects.deleteByPublicId(legacyId);
    }

    @Transactional(readOnly = true)
    public Optional<ProjectStateSnapshot> loadState(String projectId) {
        return states.findByProjectId(projectId).map(value -> read(value.payloadJson(), ProjectStateSnapshot.class));
    }

    public record ProjectAccess(String projectId, String tenantId, UUID ownerUserId) {}

    @Transactional(readOnly = true)
    public java.util.List<ProjectAccess> loadAccess() {
        return projects.findAll().stream()
                .map(value -> new ProjectAccess(value.publicId(), value.tenantId(), value.ownerUserId())).toList();
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
        return new ProjectSummary(value.publicId(), value.projectCode(), value.name(),
                StartMode.valueOf(value.startMode()), value.status(), value.snapshotVersion(),
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

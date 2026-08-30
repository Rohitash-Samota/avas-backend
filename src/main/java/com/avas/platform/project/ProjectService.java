package com.avas.platform.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.avas.platform.project.persistence.ProjectPersistenceService;
import com.avas.platform.auth.AvasPrincipal;
import com.avas.platform.pricing.PriceCategory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProjectService {
    /**
     * Share of the ground plate the layout engine gives to parking, courtyard and open space.
     *
     * <p>Those zones are deliberately excluded from built-up area, so the packed built-up total is
     * {@code footprintArea * (floors - 0.18)}. Measured against the engine across plots from 30x40
     * to 60x100 ft at one to three storeys, where the observed ratio held within +/-5%.</p>
     */
    private static final double GROUND_FLOOR_OUTDOOR_SHARE = 0.18d;

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private final Map<String, ProjectAggregate> projects = new ConcurrentHashMap<>();
    private final Map<String, ProjectPersistenceService.ProjectAccess> access = new ConcurrentHashMap<>();
    private final Map<String, ProjectStateSnapshot> persistedSnapshots = new ConcurrentHashMap<>();
    private final GeometryEngine geometryEngine;
    private final Map<String, String> versions;
    private final ProjectPersistenceService persistence;
    private final CostingService costing;
    private final PlanningParameterClient planningParameters;
    private final ProgrammeClient programmes;

    @Autowired
    public ProjectService(
            GeometryEngine geometryEngine,
            @Value("${avas.versions.rules}") String rules,
            @Value("${avas.versions.knowledge}") String knowledge,
            @Value("${avas.versions.strategy}") String strategy,
            @Value("${avas.versions.estimate-policy}") String estimatePolicy,
            ProjectPersistenceService persistence,
            CostingService costing,
            PlanningParameterClient planningParameters,
            ProgrammeClient programmes
    ) {
        this.geometryEngine = geometryEngine;
        this.versions = Map.of("ruleVersion", rules, "knowledgeVersion", knowledge, "strategyVersion", strategy, "estimatePolicy", estimatePolicy);
        this.persistence = persistence;
        this.costing = costing;
        this.planningParameters = planningParameters;
        this.programmes = programmes;
    }

    public ProjectService(GeometryEngine geometryEngine, String rules, String knowledge, String strategy, String estimatePolicy) {
        this.geometryEngine = geometryEngine;
        this.versions = Map.of("ruleVersion", rules, "knowledgeVersion", knowledge, "strategyVersion", strategy, "estimatePolicy", estimatePolicy);
        this.persistence = null;
        this.costing = CostingService.administrativeDefaults();
        this.planningParameters = deterministicPlanningParameters();
        this.programmes = deterministicProgrammes();
    }

    ProjectService(GeometryEngine geometryEngine, String rules, String knowledge, String strategy,
            String estimatePolicy, CostingService costing) {
        this.geometryEngine = geometryEngine;
        this.versions = Map.of("ruleVersion", rules, "knowledgeVersion", knowledge,
                "strategyVersion", strategy, "estimatePolicy", estimatePolicy);
        this.persistence = null;
        this.costing = costing;
        this.planningParameters = deterministicPlanningParameters();
        this.programmes = deterministicProgrammes();
    }

    ProjectService(GeometryEngine geometryEngine, String rules, String knowledge, String strategy,
            String estimatePolicy, CostingService costing, PlanningParameterClient planningParameters) {
        this.geometryEngine = geometryEngine;
        this.versions = Map.of("ruleVersion", rules, "knowledgeVersion", knowledge,
                "strategyVersion", strategy, "estimatePolicy", estimatePolicy);
        this.persistence = null;
        this.costing = costing;
        this.planningParameters = planningParameters;
        this.programmes = deterministicProgrammes();
    }

    @PostConstruct
    void loadPersistedProjects() {
        if (persistence != null) {
            persistence.retireLegacySampleProject();
            persistence.loadStates().forEach(state -> {
                projects.put(state.project().id(), new ProjectAggregate(state));
                persistedSnapshots.put(state.project().id(), state);
            });
            persistence.loadAccess().forEach(value -> access.put(value.projectId(), value));
            persistence.loadProjectSummaries().stream()
                    .filter(summary -> !projects.containsKey(summary.id())).forEach(summary -> projects.put(summary.id(), new ProjectAggregate(summary)));
        }
    }

    public synchronized ProjectSummary create(CreateProjectRequest request, String role, UUID ownerUserId, String tenantId) {
        var id = UUID.randomUUID().toString();
        var code = "AVAS-" + (request.startMode() == StartMode.DRAWING ? "UPL" : "PRJ") + "-" + id.substring(0, 8).toUpperCase();
        var project = new ProjectAggregate(id, code, request.name(), request.startMode());
        projects.put(id, project);
        access.put(id, new ProjectPersistenceService.ProjectAccess(id, tenantId, ownerUserId));
        audit(project, "PROJECT_CREATED", role, id, "0", "Project created from " + request.startMode());
        persist(project);
        return project.summary();
    }

    public synchronized ProjectSummary create(CreateProjectRequest request, String role) {
        return create(request, role, null, "tenant-public");
    }

    public List<ProjectSummary> list(AvasPrincipal principal, String activeRole) {
        if (persistence == null) return projects.values().stream().map(ProjectAggregate::summary).toList();
        return persistence.list(principal.tenantId(), principal.userId(), tenantWide(activeRole));
    }

    public void requireAccess(String projectId, AvasPrincipal principal, String activeRole) {
        required(projectId);
        var ownership = access.get(projectId);
        var allowed = ownership != null && principal.tenantId().equals(ownership.tenantId())
                && (principal.userId().equals(ownership.ownerUserId()) || tenantWide(activeRole));
        if (!allowed) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
    }

    public String projectIdForDrawing(String drawingId) { return findDrawing(drawingId).project.id; }
    public String projectIdForEstimate(String estimateId) { return findEstimate(estimateId).project.id; }

    private boolean tenantWide(String activeRole) {
        return "ADMIN".equals(activeRole);
    }

    public ProjectSummary get(String projectId) { return required(projectId).summary(); }

    public synchronized ProjectSummary updateBasicDetails(String projectId, BasicDetailsRequest request, String role) {
        if (request.family().members() < 1) throw new IllegalArgumentException("At least one family member is required");
        var project = required(projectId);
        project.details = request;
        project.invalidateEnvelope();
        requireBuildableEnvelope(project);
        project.snapshotVersion++;
        project.recommendation = null;
        project.requirementSummary = null;
        project.status = "REQUIREMENTS_IN_PROGRESS";
        project.updatedAt = Instant.now();
        audit(project, "BASIC_DETAILS_UPDATED", role, projectId, String.valueOf(project.snapshotVersion), "Explicit plot, budget and family inputs updated");
        persist(project);
        return project.summary();
    }

    public Recommendation generateRecommendation(String projectId, String role) {
        // AVAS AI plans the programme, so the call is made before the monitor is taken: network I/O
        // inside it would let one slow provider block every unrelated project mutation. The brief is
        // frozen first and rechecked after, exactly as generateDrawings does with its own snapshot.
        final int frozenVersion;
        final BasicDetailsRequest frozenDetails;
        final BuildableEnvelope frozenEnvelope;
        final String tenantId;
        synchronized (this) {
            var project = requiredWithDetails(projectId);
            frozenVersion = project.snapshotVersion;
            frozenDetails = project.details;
            frozenEnvelope = project.envelope();
            tenantId = access.getOrDefault(project.id,
                    new ProjectPersistenceService.ProjectAccess(project.id, "tenant-public", null))
                    .tenantId();
        }
        var programme = programmes.plan(tenantId, projectId, "requirements-v" + frozenVersion,
                frozenDetails, frozenEnvelope);
        synchronized (this) {
            var project = requiredWithDetails(projectId);
            if (project.snapshotVersion != frozenVersion) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Project details changed while the programme was being planned; retry");
            }
            project.recommendation = recommendationFor(project, false, programme);
            project.status = "RECOMMENDATION_READY";
            project.updatedAt = Instant.now();
            audit(project, "RECOMMENDATION_GENERATED", role, project.recommendation.id(),
                    String.valueOf(project.snapshotVersion),
                    programme.modelPlanned()
                            ? "Programme planned by " + programme.provider()
                                    + " (" + programme.model() + ")"
                            : "Deterministic rules and approved knowledge applied");
            persist(project);
            return project.recommendation;
        }
    }

    public Recommendation recommendation(String projectId) {
        var value = required(projectId).recommendation;
        if (value == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation has not been generated");
        return value;
    }

    public synchronized RequirementSummary acceptRecommendation(String projectId, String recommendationId, String role) {
        var project = required(projectId);
        var current = recommendation(projectId);
        if (!current.id().equals(recommendationId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found");
        project.recommendation = copyRecommendation(current, true);
        project.requirementSummary = requirementFor(project);
        project.status = "REQUIREMENTS_APPROVED";
        project.updatedAt = Instant.now();
        audit(project, "REQUIREMENT_SNAPSHOT_APPROVED", role, project.requirementSummary.snapshotId(), String.valueOf(project.snapshotVersion), "Customer accepted the inferred requirement brief");
        persist(project);
        return project.requirementSummary;
    }

    public synchronized ProjectSummary updatePreferences(String projectId, PreferenceRequest request, String role) {
        var project = requiredWithDetails(projectId);
        var d = project.details;
        // The plot outline must survive a preference change; rebuilding without it would silently
        // revert an irregular plot to its bounding rectangle.
        project.details = new BasicDetailsRequest(d.plotWidth(), d.plotLength(), d.roadFacing(), d.city(), d.floors(),
                d.budget(), d.category(), d.family(), request.preferences(), d.parameters(), d.plotBoundary());
        project.snapshotVersion++;
        // Preferences change the programme — an extra bedroom, another bay — so it is planned again
        // rather than carried over. Deliberately the deterministic answer: this runs under the
        // monitor, and a preference edit must not wait on a network round trip. The next
        // recommendation generated for this project asks AVAS AI again.
        project.recommendation = recommendationFor(project, false,
                HouseholdProgramme.deterministic(project.details, project.envelope(), null));
        project.requirementSummary = null;
        project.updatedAt = Instant.now();
        audit(project, "PREFERENCES_UPDATED", role, projectId, String.valueOf(project.snapshotVersion), String.join(", ", request.preferences()));
        persist(project);
        return project.summary();
    }

    public RequirementSummary requirementSummary(String projectId) {
        var summary = required(projectId).requirementSummary;
        if (summary == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Approve a recommendation to create the requirement snapshot");
        return summary;
    }

    public DrawingJob generateDrawings(String projectId, String role) {
        final String snapshotId;
        final BasicDetailsRequest frozenDetails;
        final Recommendation frozenRecommendation;
        final String tenantId;
        // Resolved here rather than inside the parameter client so the room programme is sized
        // against the same envelope the layout is later packed into, and an unbuildable plot still
        // fails with its own actionable message before any network call is made.
        final BuildableEnvelope frozenEnvelope;
        final int nextVersion;
        synchronized (this) {
            var project = required(projectId);
            if (project.requirementSummary == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Approve the requirement summary before generating drawings");
            }
            frozenEnvelope = requireBuildableEnvelope(project);
            snapshotId = project.requirementSummary.snapshotId();
            frozenDetails = project.details;
            frozenRecommendation = project.recommendation;
            nextVersion = project.drawings.stream().mapToInt(DrawingCandidate::version)
                    .max().orElse(0) + 1;
            tenantId = access.getOrDefault(project.id,
                    new ProjectPersistenceService.ProjectAccess(project.id, "tenant-public", null)).tenantId();
        }
        // Network I/O must not hold the singleton ProjectService monitor: one slow provider must
        // never block unrelated project mutations. The frozen snapshot is rechecked before commit.
        var parameters = planningParameters.optimize(tenantId, projectId, snapshotId, frozenDetails,
                frozenEnvelope);
        var jobId = "job-" + UUID.randomUUID();
        var started = new DrawingJob(jobId, projectId, JobStatus.GENERATING_LAYOUTS, 45, "Generating layout strategies", List.of(), snapshotId, Instant.now(), null);
        // Generation is outside the monitor for the same reason the parameter call is: it now
        // reaches AVAS AI for the arrangement of each of the three options, so holding the lock
        // across it would stall every unrelated project for as long as the slowest of those calls.
        // Everything it reads was frozen above, and both the snapshot and the version it was
        // planned against are rechecked before anything is committed.
        // Generated without the remote planner. All three concepts used to be arranged by AVAS AI
        // here — three calls, ninety seconds each, for two plans nobody opens. The arrangement is
        // now asked for the concept somebody actually explores; see `arrangeWithAi`.
        var generated = geometryEngine.generate(projectId, nextVersion, frozenDetails,
                frozenRecommendation, versions, parameters, frozenEnvelope, tenantId, snapshotId,
                null, false);
        synchronized (this) {
        var project = required(projectId);
        if (project.requirementSummary == null
                || !snapshotId.equals(project.requirementSummary.snapshotId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Project requirements changed while planning parameters were generated; retry");
        }
        // A concurrent generation that landed while this one was planning would otherwise commit a
        // second set of candidates stamped with the same version, and the customer would be shown
        // six concepts where the workflow promises three.
        var landed = project.drawings.stream().mapToInt(DrawingCandidate::version).max().orElse(0) + 1;
        if (landed != nextVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Another drawing generation completed while this one was planning; retry");
        }
        var generatedEstimates = new ArrayList<Estimate>();
        for (var drawing : generated) {
            generatedEstimates.add(estimateFor(project, drawing, project.estimates.size()
                    + generatedEstimates.size() + 1));
        }
        // Commit the fully staged generation atomically only after geometry and all three governed
        // estimates succeed. A retry can never observe or version from partial artifacts.
        project.drawings.addAll(generated);
        project.estimates.addAll(generatedEstimates);
        // Kept so a later arrangement re-plans the same programme these three were costed against.
        project.parameterSet = parameters;
        project.status = generated.stream().anyMatch(d -> "EXPERT_REVIEW".equals(d.status())) ? "REVIEW_REQUIRED" : "CONCEPTS_READY";
        project.updatedAt = Instant.now();
        var completed = new DrawingJob(jobId, projectId, JobStatus.COMPLETED, 100, "Rendered and validated", generated.stream().map(DrawingCandidate::id).toList(), snapshotId, started.createdAt(), Instant.now());
        project.jobs.add(completed);
        for (var index = 0; index < generated.size(); index++) {
            var drawing = generated.get(index);
            var estimate = generatedEstimates.get(index);
            audit(project, "ESTIMATE_AUTO_GENERATED", role, estimate.id(), String.valueOf(estimate.version()),
                    "Frozen governed cost snapshot for " + drawing.strategy());
        }
        audit(project, "LAYOUT_CANDIDATES_GENERATED", role, jobId, String.valueOf(project.snapshotVersion),
                "Three validated candidate strategies generated from " + parameters.provider()
                        + " parameter set" + (parameters.fallbackUsed() ? " (fallback)" : ""));
        persist(project);
        return completed;
        }
    }

    public DrawingJob drawingJob(String projectId, String jobId) {
        return required(projectId).jobs.stream().filter(job -> job.id().equals(jobId)).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Drawing job not found"));
    }

    /** Every candidate ever generated, newest last. Callers that pin a historical version need this. */
    public List<DrawingCandidate> drawings(String projectId) { return List.copyOf(required(projectId).drawings); }

    /**
     * The concept set a customer is currently choosing between: the newest generation only.
     *
     * <p>Regeneration supersedes a set rather than adding to it. Every generation stamps the same
     * version onto all of its candidates, so the newest version is exactly the three strategies that
     * were last generated, and a project can never accumulate six or nine cards to compare.</p>
     *
     * <p>Superseded candidates stay stored and remain addressable through {@link #drawing(String)},
     * which is what keeps an approval, an estimate and an audit entry resolvable against the exact
     * drawing that was on screen when the decision was recorded.</p>
     */
    public List<DrawingCandidate> currentDrawings(String projectId) {
        var all = required(projectId).drawings;
        var current = all.stream().mapToInt(DrawingCandidate::version).max().orElse(0);
        return all.stream().filter(drawing -> drawing.version() == current).toList();
    }

    public DrawingCandidate drawing(String drawingId) { return findDrawing(drawingId).drawing; }

    /**
     * Arrange one concept with AVAS AI, in place, and keep the result.
     *
     * <p>The concept is already drawn when this is called — generation packs all three with the
     * deterministic planner, which is instant and free. This asks the model to arrange the rooms
     * <em>that concept already has</em>: same programme, same room count, so the built-up area and
     * the cost the card advertises still describe the home. Only where the rooms sit changes.</p>
     *
     * <p>Idempotent by intent. A concept already arranged by a model is returned untouched, so
     * reopening it costs nothing and — more importantly — shows the same plan. Re-running would give
     * a different arrangement of the same rooms, and a customer comparing three concepts cannot do
     * that if one of them rearranges itself every time they look at it.</p>
     *
     * <p>A refusal is not an error. The model's arrangement can still fail the checks, the service
     * can be down, and the parameter set is gone after a restart — in every case the concept keeps
     * the plan it already had, which is a plan worth having.</p>
     */
    public synchronized DrawingCandidate arrangeWithAi(String drawingId, String tenantId, String role) {
        var found = findDrawing(drawingId);
        var project = found.project;
        var existing = found.drawing;
        if (aiArranged(existing)) return existing;
        if (project.parameterSet == null || project.details == null || project.recommendation == null) {
            return existing;
        }
        var latest = project.drawings.stream().mapToInt(DrawingCandidate::version).max().orElse(0);
        if (existing.version() != latest) return existing;

        var contextVersion = project.requirementSummary == null
                ? "drawing-v" + existing.version() : project.requirementSummary.snapshotId();
        List<DrawingCandidate> replanned;
        try {
            replanned = geometryEngine.generate(project.id, existing.version(), project.details,
                    project.recommendation, versions, project.parameterSet, project.envelope(),
                    tenantId, contextVersion, existing.strategy(), true);
        } catch (RuntimeException exception) {
            log.warn("ai_arrangement_failed drawing={} reason={}", drawingId, exception.getMessage());
            return existing;
        }
        if (replanned.isEmpty()) return existing;
        var drawn = replanned.get(0);
        if (!aiArranged(drawn)) {
            // The service answered from its own rules, or its arrangement was refused. Keeping the
            // plan already on screen is better than swapping in a second deterministic plan that
            // differs from it for no reason the customer could be told.
            return existing;
        }
        // The selection flag belongs to the customer, not to the planner that drew the rooms.
        var merged = new DrawingCandidate(drawn.id(), drawn.projectId(), drawn.version(),
                drawn.strategy(), drawn.name(), drawn.builtUpArea(), drawn.estimatedCostLow(),
                drawn.estimatedCostHigh(), drawn.vastuScore(), drawn.naturalLightScore(),
                drawn.spaceEfficiencyScore(), drawn.confidence(), drawn.geometry(),
                drawn.hardViolations(), drawn.softRecommendations(), drawn.explanations(),
                drawn.versions(), drawn.status(), existing.conceptApproved(), existing.createdAt());
        project.drawings.set(found.index, merged);
        project.updatedAt = Instant.now();
        audit(project, "LAYOUT_ARRANGED_BY_AI", role, merged.id(), String.valueOf(merged.version()),
                "Rooms arranged by " + merged.versions().getOrDefault("generationModel", "a model"));
        persist(project);
        return merged;
    }

    /** True when a model, rather than the rules, decided where this concept's rooms sit. */
    private boolean aiArranged(DrawingCandidate drawing) {
        return "AI_ASSISTED".equals(drawing.versions().get("generationMode"));
    }

    public synchronized RevisionReceipt feedback(String drawingId, FeedbackRequest request, String role) {
        var found = findDrawing(drawingId);
        var interpreted = interpretFeedback(request.feedback());
        var revisionId = "revision-" + UUID.randomUUID();
        audit(found.project, "DRAWING_REVISION_REQUESTED", role, revisionId, String.valueOf(found.drawing.version() + 1), interpreted);
        persist(found.project);
        return new RevisionReceipt(drawingId, revisionId, found.drawing.version() + 1, interpreted, "QUEUED");
    }

    public DrawingJob regenerate(String drawingId, String role) { return generateDrawings(findDrawing(drawingId).project.id, role); }

    public synchronized DrawingCandidate approveConcept(String drawingId, String role) {
        var found = findDrawing(drawingId);
        // A project has one authoritative concept at a time. Clear any earlier selection before
        // persisting the new one so API responses and server-rendered PDFs cannot show two checked
        // concepts after a customer changes their mind.
        for (int index = 0; index < found.project.drawings.size(); index++) {
            var drawing = found.project.drawings.get(index);
            var selected = drawing.id().equals(drawingId);
            if (drawing.conceptApproved() != selected) {
                found.project.drawings.set(index, copyDrawing(drawing, selected));
            }
        }
        var approved = found.project.drawings.get(found.index);
        found.project.status = "CONCEPT_APPROVED";
        found.project.updatedAt = Instant.now();
        audit(found.project, "CONCEPT_DRAWING_APPROVED", role, drawingId, String.valueOf(approved.version()),
                "Selected concept recorded; professional approval remains required");
        persist(found.project);
        return approved;
    }

    public ValidationReport validation(String drawingId) {
        var drawing = drawing(drawingId);
        var hardViolations = drawing.hardViolations() == null ? List.<String>of() : drawing.hardViolations();
        var structuralViolations = hardViolations.stream()
                .filter(violation -> !violation.startsWith("Programme gap:"))
                .toList();
        var areaMismatch = hardViolations.stream()
                .anyMatch(violation -> violation.startsWith("Programme gap: placed built-up area"));
        var reviewRequired = "EXPERT_REVIEW".equals(drawing.status()) || !hardViolations.isEmpty();
        var buildingDetail = hardViolations.isEmpty()
                ? "No unresolved hard constraints in " + versions.get("ruleVersion")
                : hardViolations.size() + " unresolved hard/programme constraint(s): "
                        + String.join(" | ", hardViolations);
        var geometryDetail = structuralViolations.isEmpty()
                ? "No overlap, boundary escape or disconnected circulation"
                : structuralViolations.size() + " unresolved geometry constraint(s): "
                        + String.join(" | ", structuralViolations);
        var professionalReview = new ArrayList<String>();
        professionalReview.addAll(hardViolations);
        professionalReview.addAll(List.of("Licensed architect review", "Structural engineer design",
                "Local authority approval where applicable"));
        return new ValidationReport(drawingId, drawing.confidence(),
                reviewRequired ? EngineStatus.EXPERT_REVIEW : EngineStatus.SUCCESS, List.of(
                new ValidationGate("Schema & units", "PASSED", "Plot, budget, units and source provenance are complete", true),
                new ValidationGate("Building rules", hardViolations.isEmpty() ? "PASSED" : "REVIEW_REQUIRED",
                        buildingDetail, true),
                new ValidationGate("Geometry", structuralViolations.isEmpty() ? "PASSED" : "REVIEW_REQUIRED",
                        geometryDetail, true),
                new ValidationGate("Preliminary feasibility", reviewRequired ? "REVIEW_REQUIRED" : "PASSED_WITH_NOTES",
                        reviewRequired
                                ? "Professional feasibility review is required for unresolved programme, spans, setbacks and column grid"
                                : "Long-span and column-grid review remains professional scope",
                        true),
                new ValidationGate("Estimate evidence", areaMismatch ? "REVIEW_REQUIRED" : "PASSED",
                        areaMismatch
                                ? "Placed built-up area is outside the stored recommendation cost basis; pricing must be recalibrated before reliance"
                                : "Location evidence is within the accepted freshness window",
                        true),
                new ValidationGate("Professional review", "REQUIRED", "Conceptual output cannot become construction documentation without sign-off", true)
        ), List.copyOf(professionalReview), versions);
    }

    public synchronized Estimate generateEstimate(String projectId, String drawingId, String role) {
        var project = required(projectId);
        var drawing = drawing(drawingId);
        if (!drawing.projectId().equals(projectId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Drawing does not belong to this project");
        var existing = project.estimates.stream().filter(value -> value.drawingId().equals(drawingId))
                .max(java.util.Comparator.comparingInt(Estimate::version));
        if (existing.isPresent()) return existing.get();
        var estimate = estimateFor(project, drawing);
        project.estimates.add(estimate);
        project.updatedAt = Instant.now();
        audit(project, "ESTIMATE_GENERATED", role, estimate.id(), String.valueOf(estimate.version()), "Drawing quantities reconciled to local price evidence");
        persist(project);
        return estimate;
    }

    public List<Estimate> estimates(String projectId) { return List.copyOf(required(projectId).estimates); }
    public Estimate estimate(String estimateId) { return findEstimate(estimateId).estimate; }
    public List<EstimateItem> boq(String estimateId) { return estimate(estimateId).items(); }

    public synchronized Estimate approveEstimate(String estimateId, String role) {
        var found = findEstimate(estimateId);
        var e = found.estimate;
        var approved = e.withApproved(true);
        found.project.estimates.set(found.index, approved);
        audit(found.project, "PLANNING_ESTIMATE_APPROVED", role, e.id(), String.valueOf(e.version()), "Estimate accepted as a planning range, not a fixed commitment");
        persist(found.project);
        return approved;
    }

    public List<AuditEvent> audit(String projectId) { return List.copyOf(required(projectId).audit); }

    public WorkspaceSummary workspace(String role, String displayName, List<String> permissions, AvasPrincipal principal) {
        var visibleProjects = List.<ProjectSummary>of();
        if ("INDIVIDUAL".equals(role) || tenantWide(role)) visibleProjects = list(principal, role);
        var projectTasks = visibleProjects.stream().limit(6)
                .map(project -> new WorkspaceTask(project.id(), project.name(), project.projectCode(), project.status(), null))
                .toList();
        var metrics = switch (role) {
            case "BUILDER" -> List.of(
                    new WorkspaceMetric("Eligible projects", "0", "Released marketplace projects"),
                    new WorkspaceMetric("Quotes in review", "0", "Submitted quotations"),
                    new WorkspaceMetric("Awarded projects", "0", "Accepted awards"));
            case "INTERNAL_USER" -> List.of(
                    new WorkspaceMetric("Assigned services", "0", "Explicit professional assignments"),
                    new WorkspaceMetric("Review queue", "0", "Assigned reviews awaiting action"),
                    new WorkspaceMetric("Recorded decisions", "0", "Assignment-scoped decisions"));
            case "SITE_ENGINEER" -> List.of(
                    new WorkspaceMetric("Assigned sites", "0", "Explicit project assignments"),
                    new WorkspaceMetric("Open issues", "0", "Assigned-site issues"),
                    new WorkspaceMetric("Reports due", "0", "Daily reporting queue"));
            case "ADMIN" -> List.of(
                    new WorkspaceMetric("Tenant projects", String.valueOf(visibleProjects.size()), "Current production records"),
                    new WorkspaceMetric("Active workflows", String.valueOf(activeProjectCount(visibleProjects)), "Projects not archived"),
                    new WorkspaceMetric("Audit events", String.valueOf(auditCount(visibleProjects)), "Persisted project history"));
            default -> List.of(
                    new WorkspaceMetric("My projects", String.valueOf(visibleProjects.size()), "Owner-scoped records"),
                    new WorkspaceMetric("Active workflows", String.valueOf(activeProjectCount(visibleProjects)), "Planning in progress"),
                    new WorkspaceMetric("Concepts ready", String.valueOf(reviewQueueSize(visibleProjects)), "Projects ready for review"));
        };
        return new WorkspaceSummary(role, displayName, permissions.stream().sorted().toList(),
                RoleWorkflowCatalog.forRole(role, permissions), metrics, projectTasks);
    }

    private long activeProjectCount(List<ProjectSummary> summaries) {
        return summaries.stream().filter(project -> !"ARCHIVED".equals(project.status())).count();
    }

    private long reviewQueueSize(List<ProjectSummary> summaries) {
        return summaries.stream().filter(project -> project.status().contains("CONCEPT") || project.status().contains("DRAWING")).count();
    }

    private long auditCount(List<ProjectSummary> summaries) {
        return summaries.stream().mapToLong(project -> {
            var aggregate = projects.get(project.id());
            return aggregate == null ? 0 : aggregate.audit.size();
        }).sum();
    }

    /**
     * The brief a customer approves, built around the programme AVAS AI planned for this household.
     *
     * <p>The programme arrives already decided — how many bedrooms, how many of them are suites,
     * how many bays. Everything left here is arithmetic on top of it: the built-up band the plot
     * supports, the rate that band is costed at, and the provenance that says which route answered.
     * The bedroom count used to be computed here as well, from the headcount alone, which is how a
     * four-person household on a three-thousand square foot plot was planned two bedrooms and given
     * a study, a home office and a multipurpose room in place of the rest.</p>
     */
    private Recommendation recommendationFor(ProjectAggregate project, boolean accepted,
            HouseholdProgramme programme) {
        var d = project.details;
        // Target the same footprint the geometry engine will pack into, so a candidate is never
        // measured against an area the envelope could not have produced.
        var builtUp = (int) Math.round(
                project.envelope().plannableArea() * (d.floors() - GROUND_FLOOR_OUTDOOR_SHARE));
        var category = d.category() == Category.NOT_SURE ? (d.budget() / Math.max(1, builtUp) >= 3000 ? "LUXURY" : d.budget() / Math.max(1, builtUp) >= 2200 ? "PREMIUM" : "STANDARD") : d.category().name();
        var rate = switch (category) { case "LUXURY" -> 3300; case "PREMIUM" -> 2600; default -> 1950; };
        var expected = (long) builtUp * rate;
        return new Recommendation("rec-" + project.id + "-v" + project.snapshotVersion,
                programme.title(), category, programme.bedrooms(), programme.attachedBathrooms(),
                programme.commonBathrooms(), programme.parkingCars(),
                round10(builtUp * .92), round10(builtUp * 1.08),
                roundLakh(expected * .93), roundLakh(expected * 1.09),
                programme.seniorBedroom(), programme.familyLounge(), programme.futureExpansion(),
                92, programme.reasons(), provenanceFor(programme), accepted);
    }

    /**
     * Where this brief came from, in enough detail to tell a planned home from a fallback.
     *
     * <p>Every failure in the AI path degrades to a complete, plausible programme, so the only thing
     * that distinguishes a home a model planned from one the rules stood in for is written here. A
     * {@code programmeFallback} entry means the service was asked and could not answer, and carries
     * its own reason.</p>
     */
    private Map<String, String> provenanceFor(HouseholdProgramme programme) {
        var provenance = new java.util.LinkedHashMap<String, String>();
        provenance.put("rule", versions.get("ruleVersion"));
        provenance.put("knowledge", versions.get("knowledgeVersion"));
        provenance.put("method", programme.modelPlanned()
                ? "ai-programme-1.0" : "deterministic-recommendation-1.3");
        provenance.put("programmeProvider", programme.provider());
        provenance.put("programmeModel", programme.model());
        if (programme.fallbackUsed()) {
            provenance.put("programmeFallback", programme.warnings().isEmpty()
                    ? "The configured programme provider could not answer"
                    : String.join("; ", programme.warnings()));
        }
        return Map.copyOf(provenance);
    }

    private RequirementSummary requirementFor(ProjectAggregate project) {
        var assisted = project.details.floors() > 3 || project.details.plotWidth() < 20;
        return new RequirementSummary("req-" + project.id + "-v" + project.snapshotVersion, project.snapshotVersion, project.id, assisted ? "ASSISTED" : "AUTOMATIC", project.details, project.recommendation, List.of("Standard soil conditions until a geotechnical report is provided", "Indicative local setbacks pending professional verification", "Conceptual planning unit is feet"), assisted ? List.of("A professional must confirm the narrow-plot circulation strategy") : List.of(), versions, Instant.now());
    }

    private Estimate estimateFor(ProjectAggregate project, DrawingCandidate drawing) {
        return estimateFor(project, drawing, project.estimates.size() + 1);
    }

    private Estimate estimateFor(ProjectAggregate project, DrawingCandidate drawing, int version) {
        var tier = PriceCategory.valueOf(project.recommendation.category());
        var tenantId = access.getOrDefault(project.id,
                new ProjectPersistenceService.ProjectAccess(project.id, "tenant-public", null)).tenantId();
        return costing.cost("estimate-" + project.id + "-v" + version, version, tenantId, project.summary(), drawing,
                tier, versions);
    }

    private String interpretFeedback(String feedback) {
        var normalized = feedback.toLowerCase();
        if (normalized.contains("kitchen") && normalized.contains("larg")) return "Increase KITCHEN target area; reduce lower-priority space while preserving approved budget";
        if (normalized.contains("open space") || normalized.contains("garden")) return "Increase OPEN_SPACE priority and recalculate permissible built-up area";
        if (normalized.contains("budget") || normalized.contains("cost") || normalized.contains("lakh")) return "Apply customer cost ceiling and rank lower-cost geometry changes first";
        return "Record customer preference change for requirement interpretation and produce a new version";
    }

    private Recommendation copyRecommendation(Recommendation r, boolean accepted) { return new Recommendation(r.id(), r.title(), r.category(), r.bedrooms(), r.attachedBathrooms(), r.commonBathrooms(), r.parkingCars(), r.builtUpAreaMinimum(), r.builtUpAreaMaximum(), r.estimatedCostLow(), r.estimatedCostHigh(), r.seniorCitizenBedroom(), r.familyLounge(), r.futureExpansion(), r.confidence(), r.reasons(), r.provenance(), accepted); }
    private DrawingCandidate copyDrawing(DrawingCandidate d, boolean approved) { return new DrawingCandidate(d.id(), d.projectId(), d.version(), d.strategy(), d.name(), d.builtUpArea(), d.estimatedCostLow(), d.estimatedCostHigh(), d.vastuScore(), d.naturalLightScore(), d.spaceEfficiencyScore(), d.confidence(), d.geometry(), d.hardViolations(), d.softRecommendations(), d.explanations(), d.versions(), d.status(), approved, d.createdAt()); }

    /**
     * Resolves the legal envelope eagerly so an unbuildable plot fails at the point the customer
     * supplied it, with an actionable message, rather than deep inside layout generation.
     */
    private BuildableEnvelope requireBuildableEnvelope(ProjectAggregate project) {
        try {
            return project.envelope();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), exception);
        }
    }

    private ProjectAggregate required(String id) { var project = projects.get(id); if (project == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"); return project; }
    private ProjectAggregate requiredWithDetails(String id) { var p = required(id); if (p.details == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Complete basic project details first"); return p; }
    private FoundDrawing findDrawing(String drawingId) { for (var project : projects.values()) for (int i = 0; i < project.drawings.size(); i++) if (project.drawings.get(i).id().equals(drawingId)) return new FoundDrawing(project, project.drawings.get(i), i); throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Drawing not found"); }
    private FoundEstimate findEstimate(String estimateId) { for (var project : projects.values()) for (int i = 0; i < project.estimates.size(); i++) if (project.estimates.get(i).id().equals(estimateId)) return new FoundEstimate(project, project.estimates.get(i), i); throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estimate not found"); }
    private record FoundDrawing(ProjectAggregate project, DrawingCandidate drawing, int index) {}
    private record FoundEstimate(ProjectAggregate project, Estimate estimate, int index) {}
    private void audit(ProjectAggregate project, String action, String role, String artifactId, String version, String detail) {
        var event = new AuditEvent(UUID.randomUUID().toString(), project.id, action, role, artifactId, version, detail, Instant.now());
        project.audit.add(0, event);
    }
    private void persist(ProjectAggregate project) {
        if (persistence == null) return;
        var next = new ProjectStateSnapshot(project.summary(), project.recommendation, project.requirementSummary,
                List.copyOf(project.drawings), List.copyOf(project.jobs), List.copyOf(project.estimates), List.copyOf(project.audit));
        var ownership = access.getOrDefault(project.id,
                new ProjectPersistenceService.ProjectAccess(project.id, "tenant-public", null));
        try {
            persistence.save(next, ownership.tenantId(), ownership.ownerUserId());
            persistedSnapshots.put(project.id, next);
        } catch (RuntimeException exception) {
            var previous = persistedSnapshots.get(project.id);
            if (previous == null) {
                projects.remove(project.id);
                access.remove(project.id);
            } else {
                projects.put(project.id, new ProjectAggregate(previous));
            }
            throw exception;
        }
    }
    private int round10(double value) { return (int) Math.round(value / 10.0) * 10; }
    private long roundLakh(double value) { return Math.round(value / 100_000.0) * 100_000; }

    private static ProgrammeClient deterministicProgrammes() {
        return (tenantId, projectId, contextVersion, details, envelope) ->
                HouseholdProgramme.deterministic(details, envelope, null);
    }

    private static PlanningParameterClient deterministicPlanningParameters() {
        return (tenantId, projectId, contextVersion, details, envelope) ->
                PlanningParameterSet.deterministic(details, envelope, null);
    }
}

package com.avas.platform.project;

import com.avas.platform.project.persistence.ProjectPersistenceService;
import jakarta.servlet.http.HttpServletRequest;
import com.avas.platform.auth.AvasPrincipal;
import com.avas.platform.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

import static com.avas.platform.security.ActiveRoleFilter.ACTIVE_ROLE;

@Validated
@RestController
@RequestMapping("/api/v1")
public class ProjectController {
    private final ProjectService service;
    private final AuthService auth;
    private final FloorPlanPdfService pdf;
    private final PlotDocumentService plotDocuments;
    private final ConceptRenderClient renders;
    private final ProjectPersistenceService store;

    public ProjectController(ProjectService service, AuthService auth, FloorPlanPdfService pdf,
            PlotDocumentService plotDocuments, ConceptRenderClient renders, ProjectPersistenceService store) {
        this.service = service; this.auth = auth; this.pdf = pdf; this.plotDocuments = plotDocuments;
        this.renders = renders; this.store = store;
    }

    public record EstimateGenerateRequest(@NotBlank String drawingId) {}

    /**
     * Reads an uploaded plot drawing and proposes a boundary.
     *
     * <p>Deliberately not project-scoped: the create wizard reads a drawing before the project
     * exists. Nothing is stored or mutated here. The response is a proposal the customer confirms
     * or replaces through {@code PUT /basic-details}, so a misread drawing can never silently
     * become the geometry every downstream artifact is built from.</p>
     */
    @PostMapping(value = "/plot-documents/analyse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    PlotDocumentAnalysis analysePlotDocument(
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "roadFacing", required = false) Facing roadFacing,
            @AuthenticationPrincipal AvasPrincipal principal) {
        try {
            return plotDocuments.analyse(file.getOriginalFilename(), file.getContentType(),
                    file.getBytes(), roadFacing, principal == null ? null : principal.tenantId());
        } catch (java.io.IOException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The plot drawing upload could not be read", exception);
        }
    }

    /** A plot described in the customer's own words, for customers with no drawing to upload. */
    public record PlotOutlineDescriptionRequest(
            @NotBlank @jakarta.validation.constraints.Size(max = 600) String description,
            Facing roadFacing,
            @jakarta.validation.constraints.DecimalMin("10") Double widthFeet,
            @jakarta.validation.constraints.DecimalMin("10") Double lengthFeet) {}

    /** The proposal a customer confirms, corrects or discards. Never stored by this call. */
    public record PlotOutlineProposalResponse(List<PlotVertex> vertices, Facing roadFacing, String shape,
            int confidence, List<String> notes, String model, boolean fallbackUsed) {}

    /**
     * Draws the outline a description implies.
     *
     * <p>Like drawing analysis this is deliberately not project-scoped and stores nothing: it is a
     * starting point for the boundary editor, and the customer owns every corner afterwards.</p>
     */
    @PostMapping("/plot-outlines/describe")
    PlotOutlineProposalResponse describePlotOutline(@Valid @RequestBody PlotOutlineDescriptionRequest body,
            @AuthenticationPrincipal AvasPrincipal principal) {
        var suggestion = plotDocuments.describe(body.description(),
                body.roadFacing() == null ? Facing.NORTH : body.roadFacing(),
                body.widthFeet(), body.lengthFeet(), principal == null ? null : principal.tenantId());
        return new PlotOutlineProposalResponse(
                suggestion.hasBoundary() ? suggestion.boundary().vertices() : List.of(),
                suggestion.hasBoundary() ? suggestion.boundary().roadFacing() : body.roadFacing(),
                suggestion.shape(), suggestion.confidence(),
                java.util.stream.Stream.concat(suggestion.notes().stream(), suggestion.warnings().stream())
                        .toList(),
                suggestion.model(), suggestion.fallbackUsed());
    }

    @PostMapping("/projects")
    ResponseEntity<ProjectSummary> create(@Valid @RequestBody CreateProjectRequest body, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body, role(request), principal.userId(), principal.tenantId()));
    }

    @GetMapping("/projects")
    List<ProjectSummary> projects(HttpServletRequest request, @AuthenticationPrincipal AvasPrincipal principal) {
        return service.list(principal, role(request));
    }

    @GetMapping("/projects/{projectId}")
    ProjectSummary project(@PathVariable String projectId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        service.requireAccess(projectId, principal, role(request)); return service.get(projectId);
    }

    @PutMapping("/projects/{projectId}/basic-details")
    ProjectSummary basicDetails(@PathVariable String projectId, @Valid @RequestBody BasicDetailsRequest body,
            HttpServletRequest request, @AuthenticationPrincipal AvasPrincipal principal) {
        projectAccess(projectId, principal, request);
        return service.updateBasicDetails(projectId, body, role(request));
    }

    @PostMapping("/projects/{projectId}/recommendations/generate")
    Recommendation generateRecommendation(@PathVariable String projectId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        projectAccess(projectId, principal, request);
        return service.generateRecommendation(projectId, role(request));
    }

    @GetMapping("/projects/{projectId}/recommendations")
    List<Recommendation> recommendations(@PathVariable String projectId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        projectAccess(projectId, principal, request); return List.of(service.recommendation(projectId));
    }

    @PostMapping("/projects/{projectId}/recommendations/{recommendationId}/accept")
    RequirementSummary acceptRecommendation(@PathVariable String projectId, @PathVariable String recommendationId,
            HttpServletRequest request, @AuthenticationPrincipal AvasPrincipal principal) {
        projectAccess(projectId, principal, request);
        return service.acceptRecommendation(projectId, recommendationId, role(request));
    }

    @PutMapping("/projects/{projectId}/preferences")
    ProjectSummary preferences(@PathVariable String projectId, @Valid @RequestBody PreferenceRequest body,
            HttpServletRequest request, @AuthenticationPrincipal AvasPrincipal principal) {
        projectAccess(projectId, principal, request);
        return service.updatePreferences(projectId, body, role(request));
    }

    @GetMapping("/projects/{projectId}/requirement-summary")
    RequirementSummary requirementSummary(@PathVariable String projectId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        projectAccess(projectId, principal, request); return service.requirementSummary(projectId);
    }

    @PostMapping("/projects/{projectId}/drawings/generate")
    ResponseEntity<DrawingJob> generateDrawings(@PathVariable String projectId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        projectAccess(projectId, principal, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.generateDrawings(projectId, role(request)));
    }

    @GetMapping("/projects/{projectId}/drawing-jobs/{jobId}")
    DrawingJob drawingJob(@PathVariable String projectId, @PathVariable String jobId,
            HttpServletRequest request, @AuthenticationPrincipal AvasPrincipal principal) {
        projectAccess(projectId, principal, request); return service.drawingJob(projectId, jobId);
    }

    /**
     * The concepts on offer right now, which is the newest generation and never more than three.
     *
     * <p>Superseded versions stay readable through {@code GET /drawings/{id}}, so a link into an
     * earlier concept keeps resolving after a customer regenerates.</p>
     */
    @GetMapping("/projects/{projectId}/drawings")
    List<DrawingCandidate> drawings(@PathVariable String projectId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        projectAccess(projectId, principal, request); return service.currentDrawings(projectId);
    }

    @GetMapping("/drawings/{drawingId}")
    DrawingCandidate drawing(@PathVariable String drawingId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        drawingAccess(drawingId, principal, request); return service.drawing(drawingId);
    }

    @PostMapping("/drawings/{drawingId}/feedback")
    ResponseEntity<RevisionReceipt> feedback(@PathVariable String drawingId, @Valid @RequestBody FeedbackRequest body,
            HttpServletRequest request, @AuthenticationPrincipal AvasPrincipal principal) {
        drawingAccess(drawingId, principal, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.feedback(drawingId, body, role(request)));
    }

    @PostMapping("/drawings/{drawingId}/regenerate")
    ResponseEntity<DrawingJob> regenerate(@PathVariable String drawingId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        drawingAccess(drawingId, principal, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.regenerate(drawingId, role(request)));
    }

    @PostMapping("/drawings/{drawingId}/approve-concept")
    DrawingCandidate approveConcept(@PathVariable String drawingId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        drawingAccess(drawingId, principal, request); return service.approveConcept(drawingId, role(request));
    }

    @GetMapping("/drawings/{drawingId}/validation")
    ValidationReport validation(@PathVariable String drawingId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        drawingAccess(drawingId, principal, request); return service.validation(drawingId);
    }

    @GetMapping(value = {"/drawings/{drawingId}/pdf", "/drawings/{drawingId}/download",
            "/drawings/{drawingId}/download.pdf"}, produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> pdf(@PathVariable String drawingId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        drawingAccess(drawingId, principal, request);
        var drawing = service.drawing(drawingId);
        byte[] rendered;
        try {
            rendered = pdf.generate(service.get(drawing.projectId()), drawing,
                    generatedPlans(drawing));
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), exception);
        }
        var filename = "avas-" + safeFilename(drawing.name()) + "-v" + drawing.version() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(rendered.length)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header("X-AVAS-Drawing-Id", drawing.id())
                .body(rendered);
    }

    /**
     * A generated picture of what this concept might look like built, or {@code 204} when there is
     * none to show.
     *
     * <p>Separate from {@code /pdf} and from the geometry on purpose. The PDF carries the measured
     * plan the customer signs; this carries an illustration, and the two must never be reachable
     * through one call that a caller could confuse. The provenance travels in the body rather than
     * in headers so that nothing downstream can display the picture without the label that says
     * what it is.</p>
     *
     * <p>{@code 204} is the ordinary answer on a deployment with no image model configured. It is
     * not an error: the measured plan is what the customer was always going to be shown.</p>
     */
    /**
     * Arrange this concept with AVAS AI, once, and keep the arrangement.
     *
     * <p>Called when a customer opens a concept rather than when the three are generated. Two of
     * every three concepts are never opened, and arranging all of them cost three model calls and a
     * minute and a half of waiting to produce plans nobody looked at.</p>
     *
     * <p>Safe to call again: a concept already arranged by a model comes back untouched. That is
     * not only about cost — re-running would return a different arrangement of the same rooms, and
     * a concept that rearranges itself every time it is opened cannot be compared with the other
     * two. Every refusal path leaves the concept with the plan it already had.</p>
     */
    @PostMapping("/drawings/{drawingId}/ai-layout")
    ResponseEntity<DrawingCandidate> arrangeWithAi(@PathVariable String drawingId,
            HttpServletRequest request, @AuthenticationPrincipal AvasPrincipal principal) {
        drawingAccess(drawingId, principal, request);
        var arranged = service.arrangeWithAi(drawingId,
                principal == null ? null : principal.tenantId(), role(request));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-AVAS-Drawing-Id", arranged.id())
                .body(arranged);
    }

    /**
     * The store key for one picture.
     *
     * <p>The floor is part of it because the ground and first floor plans of one concept are two
     * different drawings. Keyed on style alone, the second storey would be served the first
     * storey's picture — and it would look plausible, which is the worst kind of wrong.</p>
     */
    private static String styleKey(ConceptRenderClient.Style style, String floor) {
        return styleKey(style, floor, null);
    }

    /**
     * Every generated plan this concept has, by storey, for the document a customer keeps.
     *
     * <p>All of them, rather than the ground floor alone. The PDF used to look up one key and print
     * one page, so a customer who generated both storeys and downloaded the set got a two-storey
     * home described by a single plan — and nothing in the document said the first floor was
     * missing rather than absent from the design.</p>
     *
     * <p>A storey with no generated plan is simply not in the map. It still gets its measured page:
     * the set is built around the geometry, and a generated picture is something a page can carry
     * rather than something a page requires.</p>
     */
    private java.util.Map<String, byte[]> generatedPlans(DrawingCandidate drawing) {
        if (store == null || drawing.geometry() == null) {
            return java.util.Map.of();
        }
        var out = new java.util.LinkedHashMap<String, byte[]>();
        for (var floor : List.of("GROUND", "FIRST", "SECOND")) {
            // Prefix lookup deliberately includes custom looks and per-floor briefs. The latest
            // 2D impression is the one the customer just created and expects in the downloaded set.
            store.findLatestRender(drawing.id(),
                            styleKey(ConceptRenderClient.Style.FLOOR_PLAN, floor))
                    .ifPresent(render -> out.put(floor, render.image()));
        }
        return out;
    }

    /**
     * The store key for one picture, including the look it was drawn in.
     *
     * <p>The options are folded in for the same reason the floor is: a plan drawn monochrome with no
     * landscaping and the same plan drawn in the brochure palette are two pictures, and serving one
     * where the other was asked for looks plausible — which is the worst kind of wrong.</p>
     *
     * <p>The default look is spelled the way it always was, with no options segment at all, so every
     * picture stored before this existed is still found by the key it was filed under. Dropping that
     * would not lose the pictures; it would silently redraw and re-bill every one of them.</p>
     */
    private static String styleKey(ConceptRenderClient.Style style, String floor,
            ConceptRenderClient.RenderOptions options) {
        var key = floor == null || floor.isBlank()
                ? style.name() : style.name() + ":" + floor.trim().toUpperCase(java.util.Locale.ROOT);
        return options == null || options.isDefault() ? key : key + "|" + options.storeKey();
    }

    private static boolean floorwise(ConceptRenderClient.Style style) {
        return style == ConceptRenderClient.Style.FLOOR_PLAN
                || style == ConceptRenderClient.Style.FLOOR_PLAN_3D;
    }

    @GetMapping("/drawings/{drawingId}/render")
    ResponseEntity<ConceptRender> render(@PathVariable String drawingId,
            @RequestParam(defaultValue = "ELEVATION") ConceptRenderClient.Style style,
            @RequestParam(required = false) String brief,
            // Which storey to draw. A plan is a view of one level, so this is how a two-storey home
            // is asked for as two pictures instead of as one unreadable canvas.
            @RequestParam(required = false) String floor,
            // How the picture should look. Presentation only: none of these can move a wall, which
            // is what makes them safe to take from a query string.
            @RequestParam(required = false) String furnishing,
            @RequestParam(required = false) String palette,
            @RequestParam(defaultValue = "true") boolean landscaping,
            @RequestParam(defaultValue = "true") boolean showCars,
            @RequestParam(required = false) String labels,
            @RequestParam(defaultValue = "false") boolean refresh,
            HttpServletRequest request, @AuthenticationPrincipal AvasPrincipal principal) {
        drawingAccess(drawingId, principal, request);
        var drawing = service.drawing(drawingId);
        if (floor == null || floor.isBlank()) {
            floor = null;
        } else {
            floor = floor.trim().toUpperCase(java.util.Locale.ROOT);
            if (!List.of("GROUND", "FIRST", "SECOND").contains(floor)) {
                throw new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown floor " + floor);
            }
        }
        if (floorwise(style) && floor == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A floor is required for " + style.name());
        }

        var options = new ConceptRenderClient.RenderOptions(
                furnishing, palette, landscaping, showCars, labels);
        // The picture this concept already has, unless the caller explicitly asked for a new one.
        //
        // Read before the model is asked because asking is the half that cannot be taken back: it
        // bills, and it returns a *different house* — an image model is not a pure function of its
        // prompt. Serving the stored copy is therefore not a cache in the usual sense. It is what
        // makes the illustration a property of the concept rather than of the moment it was opened,
        // and it is why reopening a concept no longer quietly spends another generation.
        if (!refresh && store != null) {
            var stored = store.findRender(drawing.id(), styleKey(style, floor, options), brief);
            if (stored.isPresent()) {
                return ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore().cachePrivate())
                        .header("X-AVAS-Drawing-Id", drawing.id())
                        .header("X-AVAS-Render-Origin", "stored")
                        .body(ConceptRender.of(ConceptRenderClient.Outcome.of(stored.get())));
            }
        }
        var project = service.get(drawing.projectId());
        var contextVersion = "drawing-v" + drawing.version();
        var outcome = renders.illustrate(principal == null ? null : principal.tenantId(),
                contextVersion, project, drawing, style, brief, floor, options);
        // Only a picture is kept. A failure is worth retrying, and storing one would turn a bad
        // minute at the image endpoint into a concept that never gets an illustration again.
        if (outcome.ready() && store != null) {
            store.saveRender(drawing.id(), styleKey(style, floor, options), brief, outcome.render());
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-AVAS-Drawing-Id", drawing.id())
                .header("X-AVAS-Render-Origin", outcome.ready() ? "generated" : "none")
                .body(ConceptRender.of(outcome));
    }

    /**
     * One generated picture, inseparable from the words it was generated from — or the reason there
     * is not one.
     *
     * <p>{@code kind} is fixed rather than carried from the service. There is exactly one thing this
     * endpoint can return a picture of, and spelling it out in the payload means a consumer that
     * renders the image has already been handed the label that says it is not a measurement.</p>
     *
     * <p>{@code status} exists because this used to answer {@code 204} for both "no image model is
     * configured" and "the render was attempted and failed". Those have different fixes, and a
     * screen that reported the first while the second was true sent people to configure something
     * that was already configured. UNAVAILABLE is the working-as-intended case; FAILED carries the
     * provider's own words in {@code warnings}.</p>
     */
    public record ConceptRender(String status, String kind, String mediaType, String imageBase64,
                                String prompt, String provider, String model,
                                List<String> warnings) {
        static ConceptRender of(ConceptRenderClient.Outcome outcome) {
            if (outcome.ready()) {
                var render = outcome.render();
                return new ConceptRender("READY", "ILLUSTRATION", render.mediaType(),
                        java.util.Base64.getEncoder().encodeToString(render.image()),
                        render.prompt(), render.provider(), render.model(), render.warnings());
            }
            if (outcome.failure() != null) {
                return new ConceptRender("FAILED", null, null, null, null, null, null,
                        List.of(outcome.failure()));
            }
            return new ConceptRender("UNAVAILABLE", null, null, null, null, null, null, List.of());
        }
    }

    @PostMapping("/projects/{projectId}/estimates/generate")
    ResponseEntity<Estimate> generateEstimate(@PathVariable String projectId, @Valid @RequestBody EstimateGenerateRequest body,
            HttpServletRequest request, @AuthenticationPrincipal AvasPrincipal principal) {
        projectAccess(projectId, principal, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.generateEstimate(projectId, body.drawingId(), role(request)));
    }

    @GetMapping("/projects/{projectId}/estimates")
    List<Estimate> estimates(@PathVariable String projectId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        projectAccess(projectId, principal, request); return service.estimates(projectId);
    }

    @GetMapping("/estimates/{estimateId}")
    Estimate estimate(@PathVariable String estimateId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        estimateAccess(estimateId, principal, request); return service.estimate(estimateId);
    }

    @GetMapping("/estimates/{estimateId}/boq")
    List<EstimateItem> boq(@PathVariable String estimateId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        estimateAccess(estimateId, principal, request); return service.boq(estimateId);
    }

    @PostMapping("/estimates/{estimateId}/approve")
    Estimate approveEstimate(@PathVariable String estimateId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        estimateAccess(estimateId, principal, request); return service.approveEstimate(estimateId, role(request));
    }

    @GetMapping("/projects/{projectId}/audit")
    List<AuditEvent> audit(@PathVariable String projectId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        projectAccess(projectId, principal, request); return service.audit(projectId);
    }

    @GetMapping("/workspace/summary")
    WorkspaceSummary workspace(HttpServletRequest request, @AuthenticationPrincipal AvasPrincipal principal) {
        var role = auth.assignedActiveRole(principal.userId(), role(request)).orElseThrow();
        return service.workspace(role.code(), role.displayName(), role.permissions(), principal);
    }

    private String role(HttpServletRequest request) { return String.valueOf(request.getAttribute(ACTIVE_ROLE)); }
    private void projectAccess(String projectId, AvasPrincipal principal, HttpServletRequest request) {
        service.requireAccess(projectId, principal, role(request));
    }
    private void drawingAccess(String drawingId, AvasPrincipal principal, HttpServletRequest request) {
        service.requireAccess(service.projectIdForDrawing(drawingId), principal, role(request));
    }
    private void estimateAccess(String estimateId, AvasPrincipal principal, HttpServletRequest request) {
        service.requireAccess(service.projectIdForEstimate(estimateId), principal, role(request));
    }
    private String safeFilename(String value) {
        var normalized = value == null ? "concept" : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "concept" : normalized;
    }
}

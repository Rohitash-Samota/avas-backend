package com.avas.platform.project;

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

import static com.avas.platform.project.ProjectModels.*;
import static com.avas.platform.security.ActiveRoleFilter.ACTIVE_ROLE;

@Validated
@RestController
@RequestMapping("/api/v1")
public class ProjectController {
    private final ProjectService service;
    private final AuthService auth;
    private final FloorPlanPdfService pdf;

    public ProjectController(ProjectService service, AuthService auth, FloorPlanPdfService pdf) {
        this.service = service; this.auth = auth; this.pdf = pdf;
    }

    public record EstimateGenerateRequest(@NotBlank String drawingId) {}

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

    @GetMapping("/projects/{projectId}/drawings")
    List<DrawingCandidate> drawings(@PathVariable String projectId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        projectAccess(projectId, principal, request); return service.drawings(projectId);
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
            rendered = pdf.generate(service.get(drawing.projectId()), drawing);
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

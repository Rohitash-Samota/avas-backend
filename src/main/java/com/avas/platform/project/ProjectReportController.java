package com.avas.platform.project;

import com.avas.platform.auth.AvasPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static com.avas.platform.security.ActiveRoleFilter.ACTIVE_ROLE;

/** Owner-scoped endpoints for reusable comparison data and private report artifacts. */
@RestController
@RequestMapping("/api/v1")
public class ProjectReportController {
    private final ProjectService projects;
    private final ProjectComparisonService comparisons;
    private final ProjectReportPdfService reports;

    public ProjectReportController(ProjectService projects, ProjectComparisonService comparisons,
            ProjectReportPdfService reports) {
        this.projects = projects;
        this.comparisons = comparisons;
        this.reports = reports;
    }

    @GetMapping("/projects/{projectId}/comparison")
    ProjectComparisonReport comparison(@PathVariable String projectId,
            @RequestParam(required = false) String drawingId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        projects.requireAccess(projectId, principal, role(request));
        return comparisons.comparison(projectId, drawingId);
    }

    @GetMapping(value = {"/projects/{projectId}/report", "/projects/{projectId}/report.pdf"},
            produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> projectReport(@PathVariable String projectId,
            @RequestParam(required = false) String drawingId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        projects.requireAccess(projectId, principal, role(request));
        var comparison = comparisons.comparison(projectId, drawingId);
        var reportDrawing = projects.drawing(comparison.reportOptionId());
        var rendered = render(projects.get(projectId), reportDrawing, comparison);
        return pdfResponse(rendered, "avas-" + safeFilename(projects.get(projectId).name())
                        + "-comparison-v" + comparison.comparisonVersion() + ".pdf",
                comparison, null);
    }

    @GetMapping(value = "/estimates/{estimateId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> estimateReport(@PathVariable String estimateId, HttpServletRequest request,
            @AuthenticationPrincipal AvasPrincipal principal) {
        var projectId = projects.projectIdForEstimate(estimateId);
        projects.requireAccess(projectId, principal, role(request));
        var estimate = projects.estimate(estimateId);
        var comparison = comparisons.comparisonForEstimate(estimateId);
        var drawing = projects.drawing(estimate.drawingId());
        var rendered = render(projects.get(projectId), drawing, comparison);
        return pdfResponse(rendered, "avas-" + safeFilename(drawing.name()) + "-estimate-v"
                        + estimate.version() + ".pdf", comparison, estimateId);
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] rendered, String filename,
            ProjectComparisonReport comparison, String estimateId) {
        var response = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(rendered.length)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header("X-AVAS-Project-Id", comparison.projectId())
                .header("X-AVAS-Report-Drawing-Id", comparison.reportOptionId())
                .header("X-AVAS-Comparison-Version", String.valueOf(comparison.comparisonVersion()));
        if (estimateId != null) response.header("X-AVAS-Estimate-Id", estimateId);
        return response.body(rendered);
    }

    private byte[] render(ProjectSummary project, DrawingCandidate drawing,
            ProjectComparisonReport comparison) {
        try {
            return reports.generate(project, drawing, comparison);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unable to create this project report: " + exception.getMessage(), exception);
        }
    }

    private String role(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(ACTIVE_ROLE));
    }

    private String safeFilename(String value) {
        var normalized = value == null ? "project" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "project" : normalized;
    }
}

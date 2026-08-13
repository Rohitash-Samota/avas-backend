package com.avas.platform.project;

import com.avas.platform.auth.AvasPrincipal;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.avas.platform.security.ActiveRoleFilter.ACTIVE_ROLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectReportControllerTest {
    private ProjectService projects;
    private ProjectReportController controller;
    private UUID owner;
    private String projectId;
    private String drawingId;
    private String estimateId;

    @BeforeEach
    void setUp() {
        projects = new ProjectService(new GeometryEngine(), "RJ-JDA-2026.08", "AVAS-KB-2026.08",
                "layout-heuristic-1.5.0", "planning-estimate-2.0.0");
        var comparisons = new ProjectComparisonService(projects);
        var reports = new ProjectReportPdfService(new FloorPlanPdfService(),
                new ComparisonPdfPageRenderer(), new CostBreakdownPdfPageRenderer());
        controller = new ProjectReportController(projects, comparisons, reports);
        owner = UUID.randomUUID();
        var project = projects.create(new CreateProjectRequest("Family home", StartMode.PLOT), "INDIVIDUAL",
                owner, "tenant-one");
        projectId = project.id();
        projects.updateBasicDetails(projectId, details(), "INDIVIDUAL");
        var recommendation = projects.generateRecommendation(projectId, "INDIVIDUAL");
        projects.acceptRecommendation(projectId, recommendation.id(), "INDIVIDUAL");
        projects.generateDrawings(projectId, "INDIVIDUAL");
        drawingId = projects.drawings(projectId).get(1).id();
        projects.approveConcept(drawingId, "INDIVIDUAL");
        estimateId = projects.estimates(projectId).stream()
                .filter(value -> value.drawingId().equals(drawingId)).findFirst().orElseThrow().id();
    }

    @Test
    void returnsReusableComparisonForTheOwnedProject() {
        var report = controller.comparison(projectId, null, request(), principal(owner, "tenant-one"));

        assertThat(report.options()).hasSize(3);
        assertThat(report.reportOptionId()).isEqualTo(drawingId);
        assertThat(report.options()).allSatisfy(option -> assertThat(option.estimate().available()).isTrue());
    }

    @Test
    void returnsProjectAndEstimateReportsAsInlinePrivateNoStorePdfs() throws Exception {
        var projectResponse = controller.projectReport(projectId, null, request(),
                principal(owner, "tenant-one"));
        var estimateResponse = controller.estimateReport(estimateId, request(),
                principal(owner, "tenant-one"));

        for (var response : List.of(projectResponse, estimateResponse)) {
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
            assertThat(response.getHeaders().getCacheControl()).contains("private").contains("no-store");
            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                    .startsWith("inline;").endsWith(".pdf\"");
            assertThat(new String(response.getBody(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
            try (var pdf = Loader.loadPDF(response.getBody())) {
                assertThat(pdf.getNumberOfPages()).isGreaterThan(4);
            }
        }
        assertThat(projectResponse.getHeaders().getFirst("X-AVAS-Project-Id")).isEqualTo(projectId);
        assertThat(projectResponse.getHeaders().getFirst("X-AVAS-Report-Drawing-Id")).isEqualTo(drawingId);
        assertThat(estimateResponse.getHeaders().getFirst("X-AVAS-Estimate-Id")).isEqualTo(estimateId);
    }

    @Test
    void hidesProjectAndEstimateArtifactsFromAnotherOwnerOrTenant() {
        var stranger = principal(UUID.randomUUID(), "tenant-one");
        var otherTenant = principal(owner, "tenant-two");

        assertHidden(() -> controller.comparison(projectId, null, request(), stranger));
        assertHidden(() -> controller.projectReport(projectId, null, request(), stranger));
        assertHidden(() -> controller.estimateReport(estimateId, request(), otherTenant));
    }

    @Test
    void mapsReportRenderingFailuresToUnprocessableEntity() {
        var failedRenderer = mock(ProjectReportPdfService.class);
        when(failedRenderer.generate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("broken PDF"));
        var failedController = new ProjectReportController(projects, new ProjectComparisonService(projects),
                failedRenderer);

        assertThatThrownBy(() -> failedController.projectReport(projectId, null, request(),
                principal(owner, "tenant-one")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    var response = (ResponseStatusException) exception;
                    assertThat(response.getStatusCode().value()).isEqualTo(422);
                    assertThat(response.getReason()).contains("broken PDF");
                });
    }

    private void assertHidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(404);
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setAttribute(ACTIVE_ROLE, "INDIVIDUAL");
        return request;
    }

    private AvasPrincipal principal(UUID id, String tenant) {
        return new AvasPrincipal(id, tenant, "owner@avas.test", Set.of("INDIVIDUAL"));
    }

    private BasicDetailsRequest details() {
        return new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur, Rajasthan", 2, 7_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 1, true), List.of("Natural light"));
    }
}

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

class ProjectPdfControllerTest {
    private ProjectService projects;
    private ProjectController controller;
    private UUID owner;
    private String drawingId;

    @BeforeEach
    void setUp() {
        projects = new ProjectService(new GeometryEngine(), "RJ-JDA-2026.08", "AVAS-KB-2026.08",
                "layout-heuristic-1.5.0", "planning-estimate-1.2.0");
        controller = new ProjectController(projects, null, new FloorPlanPdfService(), new PlotDocumentService());
        owner = UUID.randomUUID();
        var project = projects.create(new CreateProjectRequest("Family home", StartMode.PLOT), "INDIVIDUAL",
                owner, "tenant-one");
        projects.updateBasicDetails(project.id(), details(), "INDIVIDUAL");
        var recommendation = projects.generateRecommendation(project.id(), "INDIVIDUAL");
        projects.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");
        projects.generateDrawings(project.id(), "INDIVIDUAL");
        drawingId = projects.drawings(project.id()).get(0).id();
    }

    @Test
    void returnsOwnedCompleteFloorSetAsInlinePrivatePdf() throws Exception {
        var response = controller.pdf(drawingId, request(), principal(owner, "tenant-one"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("inline;").endsWith(".pdf\"");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store").contains("private");
        assertThat(response.getHeaders().getFirst("X-AVAS-Drawing-Id")).isEqualTo(drawingId);
        assertThat(response.getBody()).isNotNull();
        assertThat(new String(response.getBody(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (var document = Loader.loadPDF(response.getBody())) {
            // Layout sheet, site plan, and one sheet per storey.
            assertThat(document.getNumberOfPages()).isEqualTo(4);
            for (var page : document.getPages()) {
                assertThat(page.getResources().getXObjectNames()).isEmpty();
            }
        }
    }

    @Test
    void preservesOwnerPrivacyForPdfArtifacts() {
        assertThatThrownBy(() -> controller.pdf(drawingId, request(), principal(UUID.randomUUID(), "tenant-one")))
                .isInstanceOf(ResponseStatusException.class)
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

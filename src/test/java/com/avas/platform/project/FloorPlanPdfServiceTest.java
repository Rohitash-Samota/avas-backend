package com.avas.platform.project;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.avas.platform.project.ProjectModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FloorPlanPdfServiceTest {
    private ProjectService projects;
    private FloorPlanPdfService pdf;

    @BeforeEach
    void setUp() {
        projects = new ProjectService(new GeometryEngine(), "RJ-JDA-2026.08", "AVAS-KB-2026.08",
                "layout-heuristic-1.4.2", "planning-estimate-1.2.0");
        pdf = new FloorPlanPdfService();
    }

    @Test
    void rendersSelectedGeometryAndFrozenProvenanceAsOnePageVectorPdf() throws Exception {
        var project = projects.create(new CreateProjectRequest("Family home", StartMode.PLOT), "INDIVIDUAL");
        projects.updateBasicDetails(project.id(), details(), "INDIVIDUAL");
        var recommendation = projects.generateRecommendation(project.id(), "INDIVIDUAL");
        projects.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");
        projects.generateDrawings(project.id(), "INDIVIDUAL");
        var selected = projects.approveConcept(projects.drawings(project.id()).get(1).id(), "INDIVIDUAL");

        var bytes = pdf.generate(projects.get(project.id()), selected);

        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(bytes.length).isGreaterThan(4_000);
        try (var document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getPage(0).getResources().getXObjectNames()).isEmpty();
            var text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("GARDEN THRESHOLD")
                    .contains("SELECTED CONCEPT")
                    .contains("AUTHORITATIVE FLOOR PLAN MAP")
                    .contains("Living Room")
                    .contains("ALL DIMENSIONS IN FEET")
                    .contains("No generative AI model")
                    .contains("AVAS deterministic layout engine")
                    .contains("layout-heuristic-1.4.2")
                    .contains("qualified architect and structural engineer before construction")
                    .doesNotContain("Image Not Available");
        }
    }

    @Test
    void refusesToInventAPdfWhenStructuredGeometryIsMissing() {
        var project = projects.create(new CreateProjectRequest("Family home", StartMode.PLOT), "INDIVIDUAL");
        projects.updateBasicDetails(project.id(), details(), "INDIVIDUAL");
        var missingGeometry = new DrawingCandidate("drawing-missing", project.id(), 1, "BALANCED",
                "Missing geometry", 0, 1_000_000, 1_200_000, 0, 0, 0, 0,
                new GeometryDocument("FEET", 40, 60, List.of(), List.of(), List.of()), List.of(), List.of(),
                List.of(), java.util.Map.of(), "FAILED", false, java.time.Instant.now());

        assertThatThrownBy(() -> pdf.generate(projects.get(project.id()), missingGeometry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("geometry");
    }

    private BasicDetailsRequest details() {
        return new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur, Rajasthan", 2, 7_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 1, true),
                List.of("Vastu-friendly", "Family lounge", "Future expansion"));
    }
}

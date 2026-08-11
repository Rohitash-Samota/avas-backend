package com.avas.platform.project;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.avas.platform.project.ProjectModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FloorPlanPdfServiceTest {
    private ProjectService projects;
    private FloorPlanPdfService pdf;

    @BeforeEach
    void setUp() {
        projects = new ProjectService(new GeometryEngine(), "RJ-JDA-2026.08", "AVAS-KB-2026.08",
                "layout-heuristic-1.5.0", "planning-estimate-1.2.0");
        pdf = new FloorPlanPdfService();
    }

    @Test
    void rendersEverySelectedFloorAndFrozenProvenanceAsVectorPages() throws Exception {
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
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            for (var page : document.getPages()) {
                assertThat(page.getResources().getXObjectNames()).isEmpty();
            }
            var text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("GARDEN THRESHOLD")
                    .contains("SELECTED CONCEPT")
                    .contains("AUTHORITATIVE FLOOR PLAN MAP")
                    .contains("GROUND FLOOR PLAN")
                    .contains("FIRST FLOOR PLAN")
                    .contains("2-FLOOR COMPLETE SET")
                    .contains("Ground Floor geometry")
                    .contains("First Floor geometry")
                    .contains("SHEET 1 / 2")
                    .contains("SHEET 2 / 2")
                    .contains("Living Room")
                    .contains("Family Lounge")
                    .contains("ALL DIMENSIONS IN FEET")
                    .contains("NORTH-FACING ROAD / ACCESS")
                    .contains("PLAN HIGHLIGHTS")
                    .contains("EST. BUILD COST")
                    .contains("ORIENTATION")
                    .contains("SERVER VECTOR RENDER")
                    .contains("No generative AI model")
                    .contains("AVAS deterministic layout engine")
                    .contains("layout-heuristic-1.5.0")
                    .contains("qualified architect and structural engineer before construction")
                    .doesNotContain("Image Not Available")
                    .doesNotContain("upper floors require separate layouts");
        }
    }

    @Test
    void pageCountMatchesOneTwoAndThreeRequestedFloors() throws Exception {
        for (var floors : List.of(1, 2, 3)) {
            var project = projects.create(new CreateProjectRequest(floors + " floor home", StartMode.PLOT),
                    "INDIVIDUAL");
            projects.updateBasicDetails(project.id(), details(Facing.SOUTH, floors), "INDIVIDUAL");
            var recommendation = projects.generateRecommendation(project.id(), "INDIVIDUAL");
            projects.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");
            projects.generateDrawings(project.id(), "INDIVIDUAL");

            try (var document = Loader.loadPDF(pdf.generate(projects.get(project.id()),
                    projects.drawings(project.id()).getFirst()))) {
                assertThat(document.getNumberOfPages()).isEqualTo(floors);
                for (var page : document.getPages()) {
                    assertThat(page.getResources().getXObjectNames()).isEmpty();
                }
                var text = new PDFTextStripper().getText(document);
                assertThat(text).contains("SHEET " + floors + " / " + floors);
                if (floors == 3) assertThat(text).contains("SECOND FLOOR PLAN");
            }
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

    @Test
    void rendersLegacyGroundOnlyGeometryWithAnExplicitRegenerationWarning() throws Exception {
        var project = projects.create(new CreateProjectRequest("Legacy duplex", StartMode.PLOT), "INDIVIDUAL");
        projects.updateBasicDetails(project.id(), details(), "INDIVIDUAL");
        var recommendation = projects.generateRecommendation(project.id(), "INDIVIDUAL");
        projects.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");
        projects.generateDrawings(project.id(), "INDIVIDUAL");
        var complete = projects.drawings(project.id()).getFirst();
        var legacy = groundOnly(complete, false);

        try (var document = Loader.loadPDF(pdf.generate(projects.get(project.id()), legacy))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getPage(0).getResources().getXObjectNames()).isEmpty();
            assertThat(new PDFTextStripper().getText(document))
                    .contains("LEGACY INCOMPLETE FLOOR SET")
                    .contains("REGENERATE REQUIRED")
                    .contains("GROUND FLOOR PLAN");
        }
    }

    @Test
    void rejectsIncompleteGeometryThatClaimsTheMultiFloorSchema() {
        var project = projects.create(new CreateProjectRequest("Incomplete duplex", StartMode.PLOT), "INDIVIDUAL");
        projects.updateBasicDetails(project.id(), details(), "INDIVIDUAL");
        var recommendation = projects.generateRecommendation(project.id(), "INDIVIDUAL");
        projects.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");
        projects.generateDrawings(project.id(), "INDIVIDUAL");
        var incomplete = groundOnly(projects.drawings(project.id()).getFirst(), true);

        assertThatThrownBy(() -> pdf.generate(projects.get(project.id()), incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Multi-floor geometry is incomplete");
    }

    @Test
    void annotatesTheRoadEdgeUsingThePersistedProjectFacing() throws Exception {
        var project = projects.create(new CreateProjectRequest("West-facing home", StartMode.PLOT), "INDIVIDUAL");
        projects.updateBasicDetails(project.id(), details(Facing.WEST), "INDIVIDUAL");
        var recommendation = projects.generateRecommendation(project.id(), "INDIVIDUAL");
        projects.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");
        projects.generateDrawings(project.id(), "INDIVIDUAL");
        var drawing = projects.drawings(project.id()).getFirst();

        var bytes = pdf.generate(projects.get(project.id()), drawing);

        try (var document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            for (var page : document.getPages()) {
                assertThat(page.getResources().getXObjectNames()).isEmpty();
            }
            assertThat(new PDFTextStripper().getText(document))
                    .contains("WEST-FACING ROAD / ACCESS")
                    .contains("West facing");
        }
    }

    @Test
    void historicalDrawingKeepsItsFrozenFloorCountAndFacingAfterProjectDetailsChange() throws Exception {
        var project = projects.create(new CreateProjectRequest("Historical duplex", StartMode.PLOT), "INDIVIDUAL");
        projects.updateBasicDetails(project.id(), details(Facing.NORTH, 2), "INDIVIDUAL");
        var recommendation = projects.generateRecommendation(project.id(), "INDIVIDUAL");
        projects.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");
        projects.generateDrawings(project.id(), "INDIVIDUAL");
        var historical = projects.drawings(project.id()).getFirst();

        projects.updateBasicDetails(project.id(), details(50, 70, Facing.EAST, 3), "INDIVIDUAL");

        try (var document = Loader.loadPDF(pdf.generate(projects.get(project.id()), historical))) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            var text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("2-FLOOR COMPLETE SET")
                    .contains("NORTH-FACING ROAD / ACCESS")
                    .contains("North facing")
                    .contains("40.0 ft plot width")
                    .doesNotContain("EAST-FACING ROAD / ACCESS")
                    .doesNotContain("East facing")
                    .doesNotContain("50.0 ft plot width");
        }
    }

    @Test
    void legacyCompletenessRequiresTheExactCanonicalFloorSet() throws Exception {
        var project = projects.create(new CreateProjectRequest("Malformed legacy duplex", StartMode.PLOT),
                "INDIVIDUAL");
        projects.updateBasicDetails(project.id(), details(Facing.NORTH, 3), "INDIVIDUAL");
        var recommendation = projects.generateRecommendation(project.id(), "INDIVIDUAL");
        projects.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");
        projects.generateDrawings(project.id(), "INDIVIDUAL");
        var malformed = floorsOnly(projects.drawings(project.id()).getFirst(), List.of("FIRST", "SECOND"), 2);

        try (var document = Loader.loadPDF(pdf.generate(projects.get(project.id()), malformed))) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            assertThat(new PDFTextStripper().getText(document))
                    .contains("LEGACY INCOMPLETE FLOOR SET")
                    .contains("REGENERATE REQUIRED")
                    .doesNotContain("2-FLOOR COMPLETE SET");
        }
    }

    private BasicDetailsRequest details() {
        return details(Facing.NORTH, 2);
    }

    private BasicDetailsRequest details(Facing facing) {
        return details(facing, 2);
    }

    private BasicDetailsRequest details(Facing facing, int floors) {
        return details(40, 60, facing, floors);
    }

    private BasicDetailsRequest details(double width, double length, Facing facing, int floors) {
        return new BasicDetailsRequest(width, length, facing, "Jaipur, Rajasthan", floors, 7_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 1, true),
                List.of("Vastu-friendly", "Family lounge", "Future expansion"));
    }

    private DrawingCandidate groundOnly(DrawingCandidate source, boolean keepSchemaVersion) {
        var geometry = source.geometry();
        var rooms = geometry.rooms().stream().filter(room -> "GROUND".equals(room.floor())).toList();
        var doors = geometry.doors().stream().filter(opening -> "GROUND".equals(opening.get("floor")))
                .map(opening -> keepSchemaVersion ? opening : withoutOrientation(opening)).toList();
        var windows = geometry.windows().stream().filter(opening -> "GROUND".equals(opening.get("floor")))
                .map(opening -> keepSchemaVersion ? opening : withoutOrientation(opening)).toList();
        var versions = new LinkedHashMap<>(source.versions());
        if (!keepSchemaVersion) versions.remove("geometrySchemaVersion");
        return new DrawingCandidate(source.id(), source.projectId(), source.version(), source.strategy(), source.name(),
                source.builtUpArea(), source.estimatedCostLow(), source.estimatedCostHigh(), source.vastuScore(),
                source.naturalLightScore(), source.spaceEfficiencyScore(), source.confidence(),
                new GeometryDocument(geometry.unit(), geometry.plotWidth(), geometry.plotLength(), rooms, doors, windows),
                source.hardViolations(), source.softRecommendations(), source.explanations(), Map.copyOf(versions),
                source.status(), source.conceptApproved(), source.createdAt());
    }

    private DrawingCandidate floorsOnly(DrawingCandidate source, List<String> floors, int requestedFloors) {
        var geometry = source.geometry();
        var rooms = geometry.rooms().stream().filter(room -> floors.contains(room.floor())).toList();
        var doors = geometry.doors().stream().filter(opening -> floors.contains(opening.get("floor"))).toList();
        var windows = geometry.windows().stream().filter(opening -> floors.contains(opening.get("floor"))).toList();
        var versions = new LinkedHashMap<>(source.versions());
        versions.remove("geometrySchemaVersion");
        versions.put("requestedFloors", String.valueOf(requestedFloors));
        return new DrawingCandidate(source.id(), source.projectId(), source.version(), source.strategy(), source.name(),
                source.builtUpArea(), source.estimatedCostLow(), source.estimatedCostHigh(), source.vastuScore(),
                source.naturalLightScore(), source.spaceEfficiencyScore(), source.confidence(),
                new GeometryDocument(geometry.unit(), geometry.plotWidth(), geometry.plotLength(), rooms, doors, windows),
                source.hardViolations(), source.softRecommendations(), source.explanations(), Map.copyOf(versions),
                source.status(), source.conceptApproved(), source.createdAt());
    }

    private Map<String, Object> withoutOrientation(Map<String, Object> opening) {
        var legacy = new LinkedHashMap<>(opening);
        legacy.remove("orientation");
        return Map.copyOf(legacy);
    }
}

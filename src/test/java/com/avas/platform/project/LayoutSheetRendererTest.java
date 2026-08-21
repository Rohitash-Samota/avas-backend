package com.avas.platform.project;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sheet a customer reads, as opposed to the sheets a builder works from.
 *
 * <p>Everything printed on it is measured off the persisted geometry, so the assertions here are
 * mostly about that: a schedule that restated the brief instead of counting the drawing would claim
 * four bedrooms on a sheet showing three.</p>
 */
class LayoutSheetRendererTest {
    private ProjectService projects;
    private FloorPlanPdfService pdf;

    @BeforeEach
    void setUp() {
        projects = new ProjectService(new GeometryEngine(), "RJ-JDA-2026.08", "AVAS-KB-2026.08",
                "layout-heuristic-1.5.0", "planning-estimate-1.2.0");
        pdf = new FloorPlanPdfService();
    }

    @Test
    void leadsTheSetWithOneLandscapeSheetCarryingEveryStoreyAndTheSchedule() throws Exception {
        var drawing = drawing(Category.LUXURY, 2);

        try (var document = Loader.loadPDF(pdf.generate(project(), drawing))) {
            var sheet = document.getPage(0);
            assertThat(sheet.getMediaBox().getWidth())
                    .as("the layout sheet is landscape")
                    .isGreaterThan(sheet.getMediaBox().getHeight());
            // Vector only: the whole set is drawn, never a rasterised preview pasted onto a page.
            assertThat(sheet.getResources().getXObjectNames()).isEmpty();

            var stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            var text = stripper.getText(document);
            assertThat(text)
                    .contains("DUPLEX HOUSE LAYOUT")
                    .contains("40' (NORTH FACING) x 60' PLOT")
                    .contains("DOG-LEGGED STAIRCASE")
                    .contains("GROUND FLOOR PLAN")
                    .contains("FIRST FLOOR PLAN")
                    .contains("PLOT DETAILS")
                    .contains("SUMMARY")
                    .contains("STAIRCASE")
                    .contains("NORTH ROAD")
                    .contains("All dimensions are in feet and inches.")
                    .contains("Conceptual plan");
        }
    }

    @Test
    void theScheduleCountsTheDrawingRatherThanRestatingTheBrief() throws Exception {
        var drawing = drawing(Category.LUXURY, 2);
        var bedrooms = drawing.geometry().rooms().stream()
                .filter(room -> room.type().endsWith("BEDROOM")).count();

        assertThat(sheetText(drawing)).contains("Bedrooms").contains(String.valueOf(bedrooms));
    }

    @Test
    void aBungalowIsNotAdvertisedAsADuplex() throws Exception {
        assertThat(sheetText(drawing(Category.STANDARD, 1)))
                .contains("BUNGALOW HOUSE LAYOUT")
                .contains("1 FLOOR")
                .doesNotContain("FIRST FLOOR PLAN");
    }

    @Test
    void theSheetOnlyClaimsWhatThePlanSupports() throws Exception {
        // Every chip along the bottom is a promise about this drawing. A home planned with no lift
        // must not be sold a lift shaft, whatever the tier's brochure would say.
        var noLift = drawing(Category.STANDARD, 1);
        assertThat(noLift.geometry().rooms()).noneMatch(room -> "LIFT_SHAFT".equals(room.type()));

        assertThat(sheetText(noLift)).doesNotContain("FUTURE READY");
        assertThat(sheetText(drawing(Category.LUXURY, 2))).contains("FUTURE READY");
    }

    @Test
    void everyDimensionIsWrittenInFeetAndInches() {
        assertThat(LayoutSheetRenderer.feetInches(13)).isEqualTo("13'-0\"");
        assertThat(LayoutSheetRenderer.feetInches(13.5)).isEqualTo("13'-6\"");
        // Rounds up into the next foot rather than printing twelve inches.
        assertThat(LayoutSheetRenderer.feetInches(12.99)).isEqualTo("13'-0\"");
    }

    // -------------------------------------------------------------------------------------------

    private String projectId;

    private String sheetText(DrawingCandidate drawing) throws Exception {
        try (var document = Loader.loadPDF(pdf.generate(project(), drawing))) {
            var stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            return stripper.getText(document);
        }
    }

    private ProjectSummary project() {
        return projects.get(projectId);
    }

    private DrawingCandidate drawing(Category category, int floors) {
        var project = projects.create(new CreateProjectRequest("Sheet", StartMode.PLOT), "INDIVIDUAL");
        projectId = project.id();
        var brief = new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur", floors,
                category == Category.LUXURY ? 14_000_000 : 5_000_000, category,
                new FamilyDetails(2, 2, 0, false), List.of());
        var inferred = brief.parameters();
        projects.updateBasicDetails(project.id(), new BasicDetailsRequest(brief.plotWidth(),
                brief.plotLength(), brief.roadFacing(), brief.city(), brief.floors(), brief.budget(),
                brief.category(), brief.family(), brief.preferences(),
                new HomeParameters(inferred.homeType(), inferred.staircaseType(),
                        inferred.liftProvision(), inferred.balconyCount(), inferred.terraceRequired(),
                        inferred.courtyardRequired(), inferred.accessibleGroundFloor(),
                        inferred.parkingCars(), inferred.solarReady(), inferred.rainwaterHarvesting(),
                        HomeParameters.STANDARD_SETBACK)), "INDIVIDUAL");
        var recommendation = projects.generateRecommendation(project.id(), "INDIVIDUAL");
        projects.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");
        projects.generateDrawings(project.id(), "INDIVIDUAL");
        return projects.drawings(project.id()).getFirst();
    }
}

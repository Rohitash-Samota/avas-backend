package com.avas.platform.project;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Composes recommendation, comparison, authoritative floor-plan and governed costing pages. */
@Service
public class ProjectReportPdfService {
    private final FloorPlanPdfService floorPlans;
    private final RecommendationPdfPageRenderer recommendationPages;
    private final ComparisonPdfPageRenderer comparisonPages;
    private final CostBreakdownPdfPageRenderer costPages;

    public ProjectReportPdfService(FloorPlanPdfService floorPlans,
            RecommendationPdfPageRenderer recommendationPages,
            ComparisonPdfPageRenderer comparisonPages, CostBreakdownPdfPageRenderer costPages) {
        this.floorPlans = floorPlans;
        this.recommendationPages = recommendationPages;
        this.comparisonPages = comparisonPages;
        this.costPages = costPages;
    }

    public byte[] generate(ProjectSummary project, DrawingCandidate drawing,
            ProjectComparisonReport comparison) {
        if (project == null || drawing == null || comparison == null) {
            throw new IllegalArgumentException("Project, report drawing and comparison are required");
        }
        if (!project.id().equals(comparison.projectId()) || !drawing.projectId().equals(project.id())) {
            throw new IllegalArgumentException("Report artifacts do not belong to the same project");
        }
        var option = comparison.options().stream()
                .filter(value -> value.drawingId().equals(drawing.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Report drawing is outside the comparison set"));

        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            setMetadata(document.getDocumentInformation(), project, drawing, comparison);
            recommendationPages.render(document, project, drawing, option);
            comparisonPages.render(document, comparison);
            // The persisted drawing keeps its early geometry-planning range. A report must instead show
            // the same governed, frozen range used by its comparison and cost pages. This copy is render-only;
            // the authoritative drawing geometry, identity and provenance remain unchanged.
            var floorBytes = floorPlans.generate(project, withReportCostRange(drawing, option));
            try (var floorDocument = Loader.loadPDF(floorBytes)) {
                for (var page : floorDocument.getPages()) document.importPage(page);
            }
            costPages.render(document, comparison, option);
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render the project comparison report", exception);
        }
    }

    private DrawingCandidate withReportCostRange(DrawingCandidate drawing,
            ProjectComparisonReport.Option option) {
        return new DrawingCandidate(drawing.id(), drawing.projectId(), drawing.version(), drawing.strategy(),
                drawing.name(), drawing.builtUpArea(), option.costLow(), option.costHigh(), drawing.vastuScore(),
                drawing.naturalLightScore(), drawing.spaceEfficiencyScore(), drawing.confidence(),
                drawing.geometry(), drawing.hardViolations(), drawing.softRecommendations(),
                drawing.explanations(), drawing.versions(), drawing.status(), drawing.conceptApproved(),
                drawing.createdAt());
    }

    private void setMetadata(PDDocumentInformation info, ProjectSummary project, DrawingCandidate drawing,
            ProjectComparisonReport comparison) {
        info.setTitle(ReportPdfSupport.safe(project.name() + " - AVAS option and cost report"));
        info.setAuthor("AVAS Adaptive Home Planning");
        info.setSubject(ReportPdfSupport.safe("Private planning comparison for " + project.projectCode()
                + "; report option " + drawing.name()));
        info.setKeywords(ReportPdfSupport.safe("AVAS, option comparison, floor plan, cost breakdown, "
                + "BOQ, version " + comparison.comparisonVersion()));
        info.setCreator("AVAS server-side project report renderer");
        info.setProducer("Apache PDFBox");
    }
}

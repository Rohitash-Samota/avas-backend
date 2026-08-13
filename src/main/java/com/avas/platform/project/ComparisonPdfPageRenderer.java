package com.avas.platform.project;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Renders the decision-oriented, side-by-side option page. */
@Component
class ComparisonPdfPageRenderer {
    void render(PDDocument document, ProjectComparisonReport report) throws IOException {
        var page = ReportPdfSupport.page();
        document.addPage(page);
        try (var canvas = new PDPageContentStream(document, page)) {
            ReportPdfSupport.pageBackground(canvas);
            ReportPdfSupport.header(canvas, "OPTION COMPARISON  |  VERSION " + report.comparisonVersion());
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 25, ReportPdfSupport.INK,
                    ReportPdfSupport.fit(report.projectName().toUpperCase(), ReportPdfSupport.BOLD, 25, 430),
                    36, 748);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.6f, ReportPdfSupport.ACCENT,
                    "DESIGN AND COST DECISION SHEET", 36, 733);
            ReportPdfSupport.textRight(canvas, ReportPdfSupport.BOLD, 10, ReportPdfSupport.INK,
                    ReportPdfSupport.money(report.projectBudget(), "INR") + " BUDGET", 559, 743);
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6.2f, ReportPdfSupport.MUTED,
                    ReportPdfSupport.fit(ReportPdfSupport.present(report.city(), "Location pending") + "  |  "
                            + report.projectCode() + "  |  Snapshot " + report.projectSnapshotVersion(),
                            ReportPdfSupport.REGULAR, 6.2f, 523), 36, 718);

            var optionCount = Math.max(1, report.options().size());
            var gap = 10f;
            var width = (523f - gap * (optionCount - 1)) / optionCount;
            var x = 36f;
            for (var option : report.options()) {
                renderOption(canvas, option, x, 326, width, 370);
                x += width + gap;
            }

            ReportPdfSupport.card(canvas, 36, 216, 523, 92, true);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.2f, ReportPdfSupport.ACCENT,
                    "BEST AVAILABLE OPTION", 49, 288);
            var best = report.options().stream().filter(ProjectComparisonReport.Option::bestOption)
                    .findFirst().orElse(report.options().getFirst());
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 18, ReportPdfSupport.INK,
                    best.name(), 49, 263);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 7, ReportPdfSupport.INK,
                    "Rank " + best.rank() + "  |  Weighted score " + best.weightedScore() + "%  |  "
                            + ReportPdfSupport.title(best.budgetFit()), 49, 247);
            var explanationY = 232f;
            for (var line : ReportPdfSupport.wrap(report.recommendationBasis(), ReportPdfSupport.REGULAR,
                    6.3f, 485)) {
                ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6.3f, ReportPdfSupport.MUTED,
                        line, 49, explanationY);
                explanationY -= 9;
            }

            ReportPdfSupport.card(canvas, 36, 65, 253, 133, false);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.2f, ReportPdfSupport.ACCENT,
                    "HOW TO READ THIS REPORT", 49, 179);
            var readY = 160f;
            for (var value : new String[]{
                    "Floor sheets are imported from the same authoritative vector renderer used by AVAS.",
                    "Cost pages use the latest persisted estimate for the report option.",
                    "Brand and supplier names appear only when recorded in approved pricing evidence."
            }) {
                for (var line : ReportPdfSupport.wrap(value, ReportPdfSupport.REGULAR, 6.1f, 220)) {
                    ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6.1f, ReportPdfSupport.MUTED,
                            line, 57, readY);
                    readY -= 9;
                }
                readY -= 5;
            }

            ReportPdfSupport.card(canvas, 306, 65, 253, 133, false);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.2f, ReportPdfSupport.ACCENT,
                    "REPORT OPTION", 319, 179);
            var reportOption = report.options().stream()
                    .filter(option -> option.drawingId().equals(report.reportOptionId()))
                    .findFirst().orElse(best);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 15, ReportPdfSupport.INK,
                    reportOption.name(), 319, 155);
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6.2f, ReportPdfSupport.MUTED,
                    reportOption.selected() ? "Customer-selected concept"
                            : reportOption.bestOption() ? "Best available concept" : "Requested report concept",
                    319, 141);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 8, ReportPdfSupport.INK,
                    ReportPdfSupport.money(reportOption.recommendedCost(), reportOption.currency()), 319, 121);
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6, ReportPdfSupport.MUTED,
                    reportOption.floorCount() + " floors  |  " + ReportPdfSupport.grouped(reportOption.builtUpArea())
                            + " sq ft  |  " + reportOption.confidence() + "% confidence", 319, 106);
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.8f, ReportPdfSupport.MUTED,
                    ReportPdfSupport.fit("Cost basis: " + ReportPdfSupport.title(reportOption.costBasis()),
                            ReportPdfSupport.REGULAR, 5.8f, 218), 319, 89);

            ReportPdfSupport.footer(canvas, report.projectCode(),
                    "Comparison is conceptual guidance; professional review remains required.");
        }
    }

    private void renderOption(PDPageContentStream canvas, ProjectComparisonReport.Option option,
            float x, float y, float width, float height) throws IOException {
        ReportPdfSupport.card(canvas, x, y, width, height, option.bestOption());
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.6f, ReportPdfSupport.ACCENT,
                option.bestOption() ? "BEST OPTION" : option.selected() ? "SELECTED" : "OPTION " + option.rank(),
                x + 12, y + height - 19);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 12, ReportPdfSupport.INK,
                ReportPdfSupport.fit(option.name().toUpperCase(), ReportPdfSupport.BOLD, 12, width - 24),
                x + 12, y + height - 42);
        ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.4f, ReportPdfSupport.MUTED,
                ReportPdfSupport.fit(ReportPdfSupport.title(option.strategy()), ReportPdfSupport.REGULAR,
                        5.4f, width - 24), x + 12, y + height - 55);
        ReportPdfSupport.line(canvas, ReportPdfSupport.LINE, .5f, x + 12, y + height - 67,
                x + width - 12, y + height - 67);

        var cursor = y + height - 88;
        cursor = metric(canvas, "Decision score", option.weightedScore() + "%", x, cursor, width);
        cursor = metric(canvas, "Budget fit", ReportPdfSupport.title(option.budgetFit()), x, cursor, width);
        cursor = metric(canvas, "Built-up area", ReportPdfSupport.grouped(option.builtUpArea()) + " sq ft",
                x, cursor, width);
        cursor = metric(canvas, "Programme", option.bedroomCount() + " bed | " + option.bathroomCount()
                + " bath", x, cursor, width);
        cursor = metric(canvas, "Floors", String.valueOf(option.floorCount()), x, cursor, width);
        cursor = metric(canvas, "Vastu", option.vastuScore() + "%", x, cursor, width);
        cursor = metric(canvas, "Natural light", option.naturalLightScore() + "%", x, cursor, width);
        cursor = metric(canvas, "Efficiency", option.spaceEfficiencyScore() + "%", x, cursor, width);
        cursor = metric(canvas, "Hard issues", String.valueOf(option.hardViolationCount()), x, cursor, width);

        ReportPdfSupport.fill(canvas, ReportPdfSupport.PAPER, x + 8, y + 53, width - 16, 72);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.4f, ReportPdfSupport.ACCENT,
                "GOVERNED COST RANGE", x + 16, y + 108);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 8.5f, ReportPdfSupport.INK,
                ReportPdfSupport.fit(ReportPdfSupport.money(option.recommendedCost(), option.currency()),
                        ReportPdfSupport.BOLD, 8.5f, width - 32), x + 16, y + 88);
        ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.3f, ReportPdfSupport.MUTED,
                ReportPdfSupport.fit(ReportPdfSupport.money(option.costLow(), option.currency()) + " - "
                        + ReportPdfSupport.money(option.costHigh(), option.currency()), ReportPdfSupport.REGULAR,
                        5.3f, width - 32), x + 16, y + 73);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.2f,
                option.eligible() ? ReportPdfSupport.INK : ReportPdfSupport.ACCENT,
                option.eligible() ? "ELIGIBLE FOR COMPARISON" : "PROFESSIONAL REVIEW REQUIRED",
                x + 16, y + 39);
    }

    private float metric(PDPageContentStream canvas, String label, String value,
            float x, float y, float width) throws IOException {
        ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.5f, ReportPdfSupport.MUTED,
                label, x + 12, y);
        ReportPdfSupport.textRight(canvas, ReportPdfSupport.BOLD, 5.5f, ReportPdfSupport.INK,
                ReportPdfSupport.fit(value, ReportPdfSupport.BOLD, 5.5f, width * .5f), x + width - 12, y);
        ReportPdfSupport.line(canvas, ReportPdfSupport.LINE, .35f, x + 12, y - 5, x + width - 12, y - 5);
        return y - 17;
    }
}

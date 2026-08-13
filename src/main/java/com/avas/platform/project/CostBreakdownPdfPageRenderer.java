package com.avas.platform.project;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/** Renders governed totals and a paginated, evidence-backed material/brand schedule. */
@Component
class CostBreakdownPdfPageRenderer {
    private static final int ROWS_PER_PAGE = 12;

    void render(PDDocument document, ProjectComparisonReport report,
            ProjectComparisonReport.Option option) throws IOException {
        var estimate = option.estimate();
        renderSummary(document, report, option, estimate);
        if (!estimate.available() || estimate.items().isEmpty()) {
            renderUnavailable(document, report, option);
            return;
        }
        for (var offset = 0; offset < estimate.items().size(); offset += ROWS_PER_PAGE) {
            var end = Math.min(estimate.items().size(), offset + ROWS_PER_PAGE);
            renderItems(document, report, option, estimate.items().subList(offset, end),
                    offset / ROWS_PER_PAGE + 1,
                    (estimate.items().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        }
        renderEvidence(document, report, option, estimate);
    }

    private void renderSummary(PDDocument document, ProjectComparisonReport report,
            ProjectComparisonReport.Option option, ProjectComparisonReport.EstimateBreakdown estimate)
            throws IOException {
        var page = ReportPdfSupport.page();
        document.addPage(page);
        try (var canvas = new PDPageContentStream(document, page)) {
            ReportPdfSupport.pageBackground(canvas);
            ReportPdfSupport.header(canvas, "GOVERNED COST SUMMARY");
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 25, ReportPdfSupport.INK,
                    "COST & MATERIALS", 36, 748);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.6f, ReportPdfSupport.ACCENT,
                    ReportPdfSupport.fit(option.name().toUpperCase() + "  |  "
                            + ReportPdfSupport.title(option.strategy()), ReportPdfSupport.BOLD, 6.6f, 523),
                    36, 733);

            ReportPdfSupport.fill(canvas, ReportPdfSupport.INK, 36, 600, 523, 104);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.2f, new java.awt.Color(235, 193, 137),
                    "RECOMMENDED PLANNING COST", 54, 678);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 27, ReportPdfSupport.WHITE,
                    ReportPdfSupport.money(estimate.recommended(), estimate.currency()), 54, 641);
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6.4f, new java.awt.Color(211, 203, 191),
                    "Range " + ReportPdfSupport.money(estimate.low(), estimate.currency()) + " - "
                            + ReportPdfSupport.money(estimate.high(), estimate.currency()), 54, 619);
            ReportPdfSupport.textRight(canvas, ReportPdfSupport.BOLD, 11, ReportPdfSupport.WHITE,
                    estimate.confidence() + "% confidence", 541, 647);
            ReportPdfSupport.textRight(canvas, ReportPdfSupport.REGULAR, 6, new java.awt.Color(211, 203, 191),
                    estimate.available() ? estimate.items().size() + " cost lines | "
                            + estimate.evidenceSampleCount() + " evidence samples" : "Planning range only",
                    541, 627);

            var metricWidth = (523f - 18) / 4;
            summaryMetric(canvas, "SUBTOTAL", estimate.subtotal(), estimate.currency(), 36, 500, metricWidth);
            summaryMetric(canvas, "TAX", estimate.taxTotal(), estimate.currency(), 36 + metricWidth + 6,
                    500, metricWidth);
            summaryMetric(canvas, "CONTINGENCY", estimate.contingency(), estimate.currency(),
                    36 + (metricWidth + 6) * 2, 500, metricWidth);
            summaryMetric(canvas, "PROJECT BUDGET", report.projectBudget(), estimate.currency(),
                    36 + (metricWidth + 6) * 3, 500, metricWidth);

            ReportPdfSupport.card(canvas, 36, 318, 253, 162, false);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.2f, ReportPdfSupport.ACCENT,
                    "PRICING BASIS", 49, 458);
            var cursor = 433f;
            cursor = keyValue(canvas, "Source", ReportPdfSupport.title(estimate.pricingSource()), 49, cursor, 227);
            cursor = keyValue(canvas, "City", ReportPdfSupport.present(estimate.pricingCity(),
                    ReportPdfSupport.present(report.city(), "Not recorded")), 49, cursor, 227);
            cursor = keyValue(canvas, "Quality tier", ReportPdfSupport.title(estimate.qualityTier()),
                    49, cursor, 227);
            cursor = keyValue(canvas, "Configuration", estimate.pricingConfigurationVersion() == 0
                    ? "Legacy" : "v" + estimate.pricingConfigurationVersion(), 49, cursor, 227);
            cursor = keyValue(canvas, "Valid until", estimate.validUntil() == null
                    ? "Not recorded" : estimate.validUntil().toString(), 49, cursor, 227);
            keyValue(canvas, "Approval", estimate.approved() ? "Approved" : "Planning review", 49,
                    cursor, 227);

            ReportPdfSupport.card(canvas, 306, 318, 253, 162, false);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.2f, ReportPdfSupport.ACCENT,
                    "COST COVERAGE", 319, 458);
            var coverageY = 432f;
            var material = estimate.items().stream().filter(ProjectComparisonReport.CostLine::materialIncluded).count();
            var labour = estimate.items().stream().filter(ProjectComparisonReport.CostLine::labourIncluded).count();
            var transport = estimate.items().stream().filter(ProjectComparisonReport.CostLine::transportIncluded).count();
            for (var statement : List.of(
                    material + " line(s) include material",
                    labour + " line(s) include labour",
                    transport + " line(s) include transport",
                    estimate.items().stream().filter(line -> line.brandName() != null
                            && !line.brandName().isBlank()).count() + " line(s) name a recorded brand",
                    estimate.evidenceSampleCount() + " approved/current evidence sample(s)"
            )) {
                ReportPdfSupport.fill(canvas, ReportPdfSupport.ACCENT, 319, coverageY + 1, 3, 3);
                ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6.2f, ReportPdfSupport.MUTED,
                        statement, 329, coverageY);
                coverageY -= 24;
            }

            ReportPdfSupport.card(canvas, 36, 65, 523, 231, false);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.2f, ReportPdfSupport.ACCENT,
                    "TOP COST CATEGORIES", 49, 274);
            var categoryTotals = new java.util.LinkedHashMap<String, Long>();
            estimate.items().forEach(item -> categoryTotals.merge(
                    ReportPdfSupport.present(item.category(), "Other"), item.amount(), Long::sum));
            var categories = categoryTotals.entrySet().stream()
                    .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed()).limit(7).toList();
            var topY = 246f;
            for (var category : categories) {
                var fraction = estimate.recommended() <= 0 ? 0
                        : Math.min(1d, category.getValue() / (double) estimate.recommended());
                ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6.1f, ReportPdfSupport.MUTED,
                        ReportPdfSupport.fit(category.getKey(), ReportPdfSupport.REGULAR, 6.1f, 180), 49, topY);
                ReportPdfSupport.fill(canvas, ReportPdfSupport.PAPER, 239, topY - 1, 180, 7);
                ReportPdfSupport.fill(canvas, ReportPdfSupport.ACCENT, 239, topY - 1,
                        (float) (180 * fraction), 7);
                ReportPdfSupport.textRight(canvas, ReportPdfSupport.BOLD, 6.1f, ReportPdfSupport.INK,
                        ReportPdfSupport.money(category.getValue(), estimate.currency()), 544, topY);
                topY -= 27;
            }
            if (categories.isEmpty()) {
                ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 7, ReportPdfSupport.MUTED,
                        "No itemized cost lines are saved for this estimate.", 49, 236);
            }

            ReportPdfSupport.footer(canvas, report.projectCode(),
                    "Planning estimate, not a supplier quotation or construction contract.");
        }
    }

    private void renderItems(PDDocument document, ProjectComparisonReport report,
            ProjectComparisonReport.Option option, List<ProjectComparisonReport.CostLine> items,
            int pageNumber, int pageCount) throws IOException {
        var page = ReportPdfSupport.page();
        document.addPage(page);
        try (var canvas = new PDPageContentStream(document, page)) {
            ReportPdfSupport.pageBackground(canvas);
            ReportPdfSupport.header(canvas, "DETAILED COST BREAKDOWN  |  " + pageNumber + " OF " + pageCount);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 23, ReportPdfSupport.INK,
                    "MATERIALS, BRANDS & RATES", 36, 748);
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6.2f, ReportPdfSupport.MUTED,
                    ReportPdfSupport.fit(option.name() + "  |  Recorded evidence only; blank brands are not inferred.",
                            ReportPdfSupport.REGULAR, 6.2f, 523), 36, 731);

            ReportPdfSupport.fill(canvas, ReportPdfSupport.PAPER, 36, 685, 523, 27);
            column(canvas, "ITEM / CATEGORY", 44, 696);
            column(canvas, "BRAND / SPECIFICATION", 176, 696);
            column(canvas, "QTY x RATE", 355, 696);
            column(canvas, "AMOUNT", 489, 696);

            var y = 630f;
            for (var item : items) {
                renderItem(canvas, item, y, option.currency());
                y -= 51;
            }
            ReportPdfSupport.footer(canvas, report.projectCode(),
                    "Detailed cost schedule | Page " + pageNumber + " of " + pageCount);
        }
    }

    private void renderItem(PDPageContentStream canvas, ProjectComparisonReport.CostLine item,
            float y, String currency) throws IOException {
        ReportPdfSupport.line(canvas, ReportPdfSupport.LINE, .4f, 36, y - 15, 559, y - 15);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.1f, ReportPdfSupport.INK,
                ReportPdfSupport.fit(ReportPdfSupport.present(item.description(), item.code()),
                        ReportPdfSupport.BOLD, 6.1f, 124), 44, y + 23);
        ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.2f, ReportPdfSupport.MUTED,
                ReportPdfSupport.fit(ReportPdfSupport.present(item.category(), "Uncategorised") + " | "
                        + ReportPdfSupport.title(item.itemType()), ReportPdfSupport.REGULAR, 5.2f, 124),
                44, y + 10);
        ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 4.8f, ReportPdfSupport.MUTED,
                ReportPdfSupport.fit(ReportPdfSupport.present(item.code(), "No code") + " | "
                        + ReportPdfSupport.present(item.evidenceId(), "No evidence id"),
                        ReportPdfSupport.REGULAR, 4.8f, 124), 44, y - 2);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.8f, ReportPdfSupport.INK,
                ReportPdfSupport.fit(ReportPdfSupport.present(item.brandName(), "Brand not recorded"),
                        ReportPdfSupport.BOLD, 5.8f, 170), 176, y + 23);
        ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.1f, ReportPdfSupport.MUTED,
                ReportPdfSupport.fit(ReportPdfSupport.present(item.specification(), item.description()),
                        ReportPdfSupport.REGULAR, 5.1f, 170), 176, y + 10);
        ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.1f, ReportPdfSupport.MUTED,
                ReportPdfSupport.fit("Supplier: " + ReportPdfSupport.present(item.supplierName(), "not recorded"),
                        ReportPdfSupport.REGULAR, 5.1f, 170), 176, y - 2);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.8f, ReportPdfSupport.INK,
                String.format(java.util.Locale.ROOT, "%.2f %s", item.quantity(),
                        ReportPdfSupport.present(item.unit(), "unit")), 355, y + 23);
        ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.1f, ReportPdfSupport.MUTED,
                "x " + ReportPdfSupport.money(item.rate(), currency), 355, y + 10);
        ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 4.9f, ReportPdfSupport.MUTED,
                inclusionLabel(item), 355, y - 2);
        ReportPdfSupport.textRight(canvas, ReportPdfSupport.BOLD, 6.3f, ReportPdfSupport.INK,
                ReportPdfSupport.money(item.amount(), currency), 551, y + 23);
        ReportPdfSupport.textRight(canvas, ReportPdfSupport.REGULAR, 5f, ReportPdfSupport.MUTED,
                "Range " + ReportPdfSupport.money(item.lowAmount(), currency) + " - "
                        + ReportPdfSupport.money(item.highAmount(), currency), 551, y + 10);
        ReportPdfSupport.textRight(canvas, ReportPdfSupport.REGULAR, 4.8f, ReportPdfSupport.MUTED,
                item.confidence() + "% confidence", 551, y - 2);
    }

    private void renderEvidence(PDDocument document, ProjectComparisonReport report,
            ProjectComparisonReport.Option option, ProjectComparisonReport.EstimateBreakdown estimate)
            throws IOException {
        var pageCount = Math.max(1, (estimate.items().size() + 9) / 10);
        for (var pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            var from = (pageNumber - 1) * 10;
            var to = Math.min(estimate.items().size(), from + 10);
            renderEvidencePage(document, report, option, estimate,
                    estimate.items().subList(from, to), pageNumber, pageCount);
        }
    }

    private void renderEvidencePage(PDDocument document, ProjectComparisonReport report,
            ProjectComparisonReport.Option option, ProjectComparisonReport.EstimateBreakdown estimate,
            List<ProjectComparisonReport.CostLine> items, int pageNumber, int pageCount) throws IOException {
        var page = ReportPdfSupport.page();
        document.addPage(page);
        try (var canvas = new PDPageContentStream(document, page)) {
            ReportPdfSupport.pageBackground(canvas);
            ReportPdfSupport.header(canvas, "ASSUMPTIONS, EXCLUSIONS & EVIDENCE  |  "
                    + pageNumber + " OF " + pageCount);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 23, ReportPdfSupport.INK,
                    "COST PROVENANCE", 36, 748);
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6.2f, ReportPdfSupport.MUTED,
                    "Evidence identifiers and dates are reproduced from the persisted estimate snapshot.",
                    36, 731);
            listCard(canvas, "ASSUMPTIONS", estimate.assumptions(), 36, 481, 253, 225);
            listCard(canvas, "EXCLUSIONS", estimate.exclusions(), 306, 481, 253, 225);

            ReportPdfSupport.card(canvas, 36, 65, 523, 394, false);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.2f, ReportPdfSupport.ACCENT,
                    "PRICE EVIDENCE REGISTER", 49, 438);
            var y = 409f;
            for (var item : items) {
                ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.8f, ReportPdfSupport.INK,
                        ReportPdfSupport.fit(ReportPdfSupport.present(item.brandName(), item.description()),
                                ReportPdfSupport.BOLD, 5.8f, 160), 49, y);
                ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5f, ReportPdfSupport.MUTED,
                        ReportPdfSupport.fit("Evidence: " + ReportPdfSupport.present(item.evidenceId(),
                                "no evidence id") + " | " + ReportPdfSupport.title(item.priceSource()),
                                ReportPdfSupport.REGULAR, 5f, 255), 217, y);
                var date = item.effectiveFrom() != null ? item.effectiveFrom() : item.observedOn();
                ReportPdfSupport.textRight(canvas, ReportPdfSupport.REGULAR, 5f, ReportPdfSupport.MUTED,
                        (date == null ? "Date not recorded" : date.toString()) + " | "
                                + item.evidenceSampleCount() + " sample(s)", 546, y);
                ReportPdfSupport.line(canvas, ReportPdfSupport.LINE, .35f, 49, y - 9, 546, y - 9);
                y -= 32;
            }
            var provenance = option.provenance();
            var estimateVersions = estimate.versions();
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.2f, ReportPdfSupport.MUTED,
                    ReportPdfSupport.fit("Design engine: " + provenance.getOrDefault("generator", "Not recorded")
                            + " | Mode: " + provenance.getOrDefault("generationMode", "DETERMINISTIC"),
                            ReportPdfSupport.REGULAR, 5.2f, 497), 49, 94);
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.2f, ReportPdfSupport.MUTED,
                    ReportPdfSupport.fit("Parameters: " + provenance.getOrDefault("parameterProvider", "DETERMINISTIC")
                            + " / " + provenance.getOrDefault("parameterModel", "avas-parameter-rules")
                            + " | Fallback: " + provenance.getOrDefault("parameterFallback", "false")
                            + " | Request: " + provenance.getOrDefault("parameterProviderRequestId", "not applicable"),
                            ReportPdfSupport.REGULAR, 5.2f, 497), 49, 84);
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.2f, ReportPdfSupport.MUTED,
                    ReportPdfSupport.fit("Prompt/schema: "
                            + provenance.getOrDefault("promptVersion", "not used") + " / "
                            + provenance.getOrDefault("parameterSchemaVersion", "home-parameters-1")
                            + " | Strategy: " + provenance.getOrDefault("strategyVersion", "not recorded")
                            + " | Costing: " + estimateVersions.getOrDefault("costingPolicy", "not recorded"),
                            ReportPdfSupport.REGULAR, 5.2f, 497), 49, 74);
            ReportPdfSupport.footer(canvas, report.projectCode(),
                    "Evidence register | Page " + pageNumber + " of " + pageCount
                            + " | Validate before procurement.");
        }
    }

    private void renderUnavailable(PDDocument document, ProjectComparisonReport report,
            ProjectComparisonReport.Option option) throws IOException {
        var page = ReportPdfSupport.page();
        document.addPage(page);
        try (var canvas = new PDPageContentStream(document, page)) {
            ReportPdfSupport.pageBackground(canvas);
            ReportPdfSupport.header(canvas, "DETAILED COST BREAKDOWN");
            ReportPdfSupport.card(canvas, 76, 300, 443, 245, true);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 21, ReportPdfSupport.INK,
                    "GOVERNED ESTIMATE REQUIRED", 101, 498);
            var y = 463f;
            for (var line : ReportPdfSupport.wrap(
                    "This option has a drawing-level planning range, but no persisted itemized estimate. "
                            + "AVAS will not invent material brands, rates or evidence for this PDF.",
                    ReportPdfSupport.REGULAR, 8, 393)) {
                ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 8, ReportPdfSupport.MUTED,
                        line, 101, y);
                y -= 13;
            }
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 11, ReportPdfSupport.INK,
                    ReportPdfSupport.money(option.costLow(), option.currency()) + " - "
                            + ReportPdfSupport.money(option.costHigh(), option.currency()), 101, 377);
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6.5f, ReportPdfSupport.MUTED,
                    "Planning range from the frozen drawing artifact", 101, 358);
            ReportPdfSupport.footer(canvas, report.projectCode(), "No unverified cost details were generated.");
        }
    }

    private void summaryMetric(PDPageContentStream canvas, String title, long value, String currency,
            float x, float y, float width) throws IOException {
        ReportPdfSupport.card(canvas, x, y, width, 80, false);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.4f, ReportPdfSupport.ACCENT, title,
                x + 10, y + 57);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 7.3f, ReportPdfSupport.INK,
                ReportPdfSupport.fit(ReportPdfSupport.money(value, currency), ReportPdfSupport.BOLD, 7.3f,
                        width - 20), x + 10, y + 31);
    }

    private float keyValue(PDPageContentStream canvas, String key, String value, float x, float y,
            float width) throws IOException {
        ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.8f, ReportPdfSupport.MUTED, key, x, y);
        ReportPdfSupport.textRight(canvas, ReportPdfSupport.BOLD, 5.8f, ReportPdfSupport.INK,
                ReportPdfSupport.fit(ReportPdfSupport.present(value, "Not recorded"), ReportPdfSupport.BOLD,
                        5.8f, width * .58f), x + width, y);
        ReportPdfSupport.line(canvas, ReportPdfSupport.LINE, .35f, x, y - 5, x + width, y - 5);
        return y - 21;
    }

    private void column(PDPageContentStream canvas, String label, float x, float y) throws IOException {
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.2f, ReportPdfSupport.MUTED, label, x, y);
    }

    private String inclusionLabel(ProjectComparisonReport.CostLine item) {
        var values = new java.util.ArrayList<String>();
        if (item.materialIncluded()) values.add("material");
        if (item.labourIncluded()) values.add("labour");
        if (item.transportIncluded()) values.add("transport");
        return values.isEmpty() ? "Inclusions not recorded" : "Includes " + String.join(", ", values);
    }

    private void listCard(PDPageContentStream canvas, String title, List<String> values,
            float x, float y, float width, float height) throws IOException {
        ReportPdfSupport.card(canvas, x, y, width, height, false);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.2f, ReportPdfSupport.ACCENT,
                title, x + 13, y + height - 22);
        var cursor = y + height - 49;
        var safeValues = values.isEmpty() ? List.of("None recorded in this estimate snapshot.") : values;
        for (var value : safeValues.stream().limit(6).toList()) {
            ReportPdfSupport.fill(canvas, ReportPdfSupport.ACCENT, x + 13, cursor + 2, 3, 3);
            for (var line : ReportPdfSupport.wrap(value, ReportPdfSupport.REGULAR, 5.8f, width - 39)) {
                ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.8f, ReportPdfSupport.MUTED,
                        line, x + 24, cursor);
                cursor -= 9;
            }
            cursor -= 8;
        }
    }
}

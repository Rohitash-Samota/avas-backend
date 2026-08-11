package com.avas.platform.project;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.avas.platform.project.ProjectModels.DrawingCandidate;
import static com.avas.platform.project.ProjectModels.ProjectSummary;
import static com.avas.platform.project.ProjectModels.RoomGeometry;

/** Creates a self-contained vector PDF from the authoritative persisted drawing geometry. */
@Service
public class FloorPlanPdfService {
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final Color INK = new Color(47, 42, 36);
    private static final Color MUTED = new Color(112, 103, 91);
    private static final Color CORAL = new Color(176, 122, 79);
    private static final Color LINE = new Color(225, 217, 205);
    private static final Color PAPER = new Color(250, 248, 244);
    private static final Color MINT = new Color(231, 235, 222);
    private static final Color[] ROOM_COLORS = {
            new Color(242, 225, 200), new Color(232, 222, 208), new Color(233, 228, 219),
            new Color(229, 229, 224), new Color(220, 232, 210), new Color(239, 227, 212)
    };

    public byte[] generate(ProjectSummary project, DrawingCandidate drawing) {
        validate(project, drawing);
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            setMetadata(document.getDocumentInformation(), project, drawing);
            try (var canvas = new PDPageContentStream(document, page)) {
                render(canvas, project, drawing);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render the floor plan PDF", exception);
        }
    }

    private void validate(ProjectSummary project, DrawingCandidate drawing) {
        if (project == null || drawing == null) throw new IllegalArgumentException("Project and drawing are required");
        if (!project.id().equals(drawing.projectId())) throw new IllegalArgumentException("Drawing does not belong to this project");
        if (project.details() == null) throw new IllegalArgumentException("Project details are required to render a PDF");
        var geometry = drawing.geometry();
        if (geometry == null || geometry.rooms() == null || geometry.rooms().isEmpty()) {
            throw new IllegalArgumentException("Drawing geometry is required to render a PDF");
        }
        if (!"FEET".equalsIgnoreCase(geometry.unit()) || !finitePositive(geometry.plotWidth())
                || !finitePositive(geometry.plotLength())) {
            throw new IllegalArgumentException("Floor plan PDF requires valid geometry measured in feet");
        }
        for (var room : geometry.rooms()) {
            if (!finitePositive(room.width()) || !finitePositive(room.length()) || !Double.isFinite(room.x())
                    || !Double.isFinite(room.y()) || room.x() < 0 || room.y() < 0
                    || room.x() + room.width() > geometry.plotWidth() + .01
                    || room.y() + room.length() > geometry.plotLength() + .01) {
                throw new IllegalArgumentException("Drawing contains invalid room geometry: " + room.id());
            }
        }
    }

    private boolean finitePositive(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private void setMetadata(PDDocumentInformation info, ProjectSummary project, DrawingCandidate drawing) {
        info.setTitle(safe(drawing.name() + " - AVAS Conceptual Plan"));
        info.setAuthor("AVAS Adaptive Home Planning");
        info.setSubject(safe((drawing.conceptApproved() ? "Selected" : "Unselected")
                + " vector floor plan for " + project.projectCode()));
        info.setKeywords(safe("AVAS, conceptual plan, " + drawing.strategy() + ", "
                + versions(drawing).getOrDefault("strategyVersion", "version not recorded")));
        info.setCreator("AVAS server-side floor plan renderer");
        info.setProducer("Apache PDFBox");
    }

    private void render(PDPageContentStream canvas, ProjectSummary project, DrawingCandidate drawing) throws IOException {
        fill(canvas, Color.WHITE, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());

        text(canvas, BOLD, 16, INK, "AVAS", 36, 804);
        text(canvas, REGULAR, 5.5f, MUTED, "ADAPTIVE HOME PLANNING", 36, 794);
        textRight(canvas, REGULAR, 6, MUTED, "SERVER-GENERATED CONCEPT SHEET", 559, 802);
        line(canvas, LINE, .8f, 36, 784, 559, 784);

        text(canvas, BOLD, 25, INK, ellipsize(drawing.name().toUpperCase(Locale.ROOT), 34), 36, 750);
        text(canvas, BOLD, 7, CORAL,
                "AVAS CONCEPTUAL PLAN  |  " + grouped(drawing.builtUpArea()) + " SQ FT INDICATIVE BUILT-UP AREA", 36, 735);
        text(canvas, REGULAR, 7, MUTED,
                "Structured geometry rendered for planning, comparison and preliminary estimation.", 36, 722);
        selectionBadge(canvas, drawing.conceptApproved(), 438, 737, 121, 31);

        renderSidebar(canvas, project, drawing, 36, 303, 120, 395);
        renderPlan(canvas, drawing, 170, 303, 389, 395);
        renderSummaryCards(canvas, project, drawing);
        renderDisclaimer(canvas, project, drawing);
    }

    private void selectionBadge(PDPageContentStream canvas, boolean selected, float x, float y, float width, float height)
            throws IOException {
        fill(canvas, selected ? MINT : PAPER, x, y, width, height);
        stroke(canvas, selected ? CORAL : LINE, .8f, x, y, width, height);
        stroke(canvas, selected ? CORAL : MUTED, .8f, x + 9, y + 10, 11, 11);
        if (selected) {
            line(canvas, CORAL, 1.4f, x + 11, y + 15, x + 14, y + 12);
            line(canvas, CORAL, 1.4f, x + 14, y + 12, x + 19, y + 19);
        }
        text(canvas, BOLD, 6.2f, selected ? INK : MUTED,
                selected ? "SELECTED CONCEPT" : "NOT SELECTED", x + 27, y + 14);
    }

    private void renderSidebar(PDPageContentStream canvas, ProjectSummary project, DrawingCandidate drawing,
            float x, float y, float width, float height) throws IOException {
        fill(canvas, PAPER, x, y, width, height);
        stroke(canvas, LINE, .7f, x, y, width, height);
        var rooms = drawing.geometry().rooms();
        var bedrooms = rooms.stream().filter(room -> room.type().contains("BEDROOM")).count();
        var bathrooms = rooms.stream().filter(room -> room.type().contains("BATH") || room.type().contains("TOILET")).count();
        var cursor = y + height - 20;

        text(canvas, BOLD, 6.2f, CORAL, "PLAN HIGHLIGHTS", x + 10, cursor);
        cursor -= 16;
        cursor = bullet(canvas, bedrooms + " bedroom" + (bedrooms == 1 ? "" : "s"), x + 10, cursor);
        cursor = bullet(canvas, bathrooms + " bathroom" + (bathrooms == 1 ? "" : "s"), x + 10, cursor);
        cursor = bullet(canvas, drawing.geometry().rooms().size() + " placed spaces", x + 10, cursor);
        cursor = bullet(canvas, titleCase(project.details().roadFacing().name()) + " facing", x + 10, cursor);

        cursor -= 8;
        text(canvas, BOLD, 6.2f, CORAL, "SPACE EFFICIENCY", x + 10, cursor);
        cursor -= 30;
        text(canvas, BOLD, 22, INK, drawing.spaceEfficiencyScore() + "%", x + 10, cursor);
        text(canvas, REGULAR, 6, MUTED, "Optimized", x + 69, cursor + 4);

        cursor -= 25;
        text(canvas, BOLD, 6.2f, CORAL, "ESTIMATED BUILD COST", x + 10, cursor);
        cursor -= 18;
        text(canvas, BOLD, 11, INK, lakhRange(drawing), x + 10, cursor);
        cursor -= 11;
        text(canvas, REGULAR, 5.5f, MUTED, "Planning range, not a quotation", x + 10, cursor);

        cursor -= 24;
        text(canvas, BOLD, 6.2f, CORAL, "VALIDATION", x + 10, cursor);
        cursor -= 15;
        cursor = keyValue(canvas, "Status", titleCase(drawing.status()), x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Confidence", drawing.confidence() + "%", x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Hard errors", String.valueOf(sizeOf(drawing.hardViolations())), x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Review", "Required", x + 10, cursor, width - 20);

        cursor -= 12;
        text(canvas, BOLD, 6.2f, CORAL, "PLAN SCORES", x + 10, cursor);
        cursor -= 15;
        cursor = keyValue(canvas, "Vastu", drawing.vastuScore() + "%", x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Natural light", drawing.naturalLightScore() + "%", x + 10, cursor, width - 20);
        keyValue(canvas, "Efficiency", drawing.spaceEfficiencyScore() + "%", x + 10, cursor, width - 20);
    }

    private float bullet(PDPageContentStream canvas, String value, float x, float y) throws IOException {
        fill(canvas, CORAL, x, y + 1, 3, 3);
        text(canvas, REGULAR, 6.3f, INK, value, x + 8, y);
        return y - 13;
    }

    private float keyValue(PDPageContentStream canvas, String key, String value, float x, float y, float width)
            throws IOException {
        text(canvas, REGULAR, 5.8f, MUTED, key, x, y);
        var available = Math.max(25, width - textWidth(REGULAR, 5.8f, key) - 7);
        textRight(canvas, BOLD, 5.8f, INK, fitWidth(value, BOLD, 5.8f, available), x + width, y);
        line(canvas, LINE, .35f, x, y - 4, x + width, y - 4);
        return y - 14;
    }

    private void renderPlan(PDPageContentStream canvas, DrawingCandidate drawing,
            float panelX, float panelY, float panelWidth, float panelHeight) throws IOException {
        fill(canvas, Color.WHITE, panelX, panelY, panelWidth, panelHeight);
        stroke(canvas, LINE, .7f, panelX, panelY, panelWidth, panelHeight);
        var geometry = drawing.geometry();
        var topReserved = 23f;
        var bottomReserved = 23f;
        var availableWidth = panelWidth - 32;
        var availableHeight = panelHeight - topReserved - bottomReserved - 22;
        var scale = (float) Math.min(availableWidth / geometry.plotWidth(), availableHeight / geometry.plotLength());
        var plotWidth = (float) geometry.plotWidth() * scale;
        var plotHeight = (float) geometry.plotLength() * scale;
        var originX = panelX + (panelWidth - plotWidth) / 2;
        var originY = panelY + bottomReserved + (availableHeight - plotHeight) / 2;

        text(canvas, BOLD, 6.2f, CORAL, "AUTHORITATIVE FLOOR PLAN MAP", panelX + 12, panelY + panelHeight - 16);
        textRight(canvas, REGULAR, 5.8f, MUTED, "ALL DIMENSIONS IN FEET", panelX + panelWidth - 12,
                panelY + panelHeight - 16);
        fill(canvas, new Color(253, 252, 249), originX, originY, plotWidth, plotHeight);
        stroke(canvas, INK, 1f, originX, originY, plotWidth, plotHeight);

        for (int index = 0; index < geometry.rooms().size(); index++) {
            var room = geometry.rooms().get(index);
            var roomX = originX + (float) room.x() * scale;
            var roomY = originY + (float) (geometry.plotLength() - room.y() - room.length()) * scale;
            var roomWidth = (float) room.width() * scale;
            var roomHeight = (float) room.length() * scale;
            fill(canvas, ROOM_COLORS[index % ROOM_COLORS.length], roomX, roomY, roomWidth, roomHeight);
            stroke(canvas, INK, .65f, roomX, roomY, roomWidth, roomHeight);
            var label = titleCase(room.type());
            var fontSize = Math.max(4.2f, Math.min(7.2f, roomWidth / Math.max(6, label.length()) * 1.35f));
            if (roomWidth > 25 && roomHeight > 18) {
                textCentered(canvas, BOLD, fontSize, INK, ellipsize(label, 19), roomX + roomWidth / 2,
                        roomY + roomHeight / 2 + 2);
                textCentered(canvas, REGULAR, Math.max(4f, fontSize - 1.5f), MUTED,
                        oneDecimal(room.width()) + " ft x " + oneDecimal(room.length()) + " ft",
                        roomX + roomWidth / 2, roomY + roomHeight / 2 - 7);
            }
        }
        renderOpenings(canvas, geometry.doors(), geometry.windows(), originX, originY, scale,
                geometry.plotLength());
        renderPlanAnnotations(canvas, drawing, panelX, panelY, panelWidth, originX, originY, plotWidth, plotHeight);
    }

    private void renderOpenings(PDPageContentStream canvas, List<Map<String, Object>> doors,
            List<Map<String, Object>> windows, float originX, float originY, float scale, double plotLength)
            throws IOException {
        for (var door : doors == null ? List.<Map<String, Object>>of() : doors) {
            var x = number(door.get("x"));
            var y = number(door.get("y"));
            var width = Math.max(2.4, number(door.get("width")));
            if (!Double.isFinite(x) || !Double.isFinite(y)) continue;
            var startX = originX + (float) (x - width / 2) * scale;
            var endX = originX + (float) (x + width / 2) * scale;
            var pointY = originY + (float) (plotLength - y) * scale;
            canvas.setStrokingColor(CORAL);
            canvas.setLineWidth(.75f);
            canvas.moveTo(startX, pointY);
            canvas.curveTo(startX + (endX - startX) * .25f, pointY + (endX - startX) * .55f,
                    startX + (endX - startX) * .72f, pointY + (endX - startX) * .55f, endX, pointY);
            canvas.stroke();
        }
        for (var window : windows == null ? List.<Map<String, Object>>of() : windows) {
            var x = number(window.get("x"));
            var y = number(window.get("y"));
            var width = Math.max(2.5, number(window.get("width")));
            if (!Double.isFinite(x) || !Double.isFinite(y)) continue;
            var pointX = originX + (float) x * scale;
            var fromY = originY + (float) (plotLength - y - width / 2) * scale;
            var toY = originY + (float) (plotLength - y + width / 2) * scale;
            line(canvas, new Color(63, 109, 140), 1.3f, pointX, fromY, pointX, toY);
        }
    }

    private void renderPlanAnnotations(PDPageContentStream canvas, DrawingCandidate drawing,
            float panelX, float panelY, float panelWidth, float originX, float originY, float plotWidth,
            float plotHeight) throws IOException {
        textCentered(canvas, REGULAR, 5.5f, MUTED,
                oneDecimal(drawing.geometry().plotWidth()) + " ft plot width", originX + plotWidth / 2,
                originY + plotHeight + 8);
        text(canvas, REGULAR, 5.5f, MUTED,
                oneDecimal(drawing.geometry().plotLength()) + " ft plot length", panelX + 8, originY + plotHeight / 2);
        var northX = panelX + panelWidth - 23;
        var northY = panelY + 27;
        line(canvas, INK, 1f, northX, northY, northX, northY + 16);
        line(canvas, INK, 1f, northX, northY + 16, northX - 4, northY + 10);
        line(canvas, INK, 1f, northX, northY + 16, northX + 4, northY + 10);
        textCentered(canvas, BOLD, 6, INK, "N", northX, northY - 8);
        line(canvas, MUTED, .7f, originX, originY - 10, originX + Math.min(55, plotWidth * .35f), originY - 10);
        text(canvas, REGULAR, 5, MUTED, "ROAD / ACCESS EDGE", originX + 60, originY - 12);
    }

    private void renderSummaryCards(PDPageContentStream canvas, ProjectSummary project, DrawingCandidate drawing)
            throws IOException {
        var y = 151f;
        var height = 128f;
        renderSpecifications(canvas, project, drawing, 36, y, 162, height);
        renderAreaBreakdown(canvas, drawing, 205, y, 166, height);
        renderProvenance(canvas, drawing, 378, y, 181, height);
    }

    private void renderSpecifications(PDPageContentStream canvas, ProjectSummary project, DrawingCandidate drawing,
            float x, float y, float width, float height) throws IOException {
        card(canvas, "SPECIFICATIONS", x, y, width, height);
        var cursor = y + height - 31;
        cursor = keyValue(canvas, "Project", project.projectCode(), x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Plot area", grouped(Math.round(project.details().plotArea())) + " sq ft", x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Built-up", grouped(drawing.builtUpArea()) + " sq ft", x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Plot", oneDecimal(project.details().plotWidth()) + " x "
                + oneDecimal(project.details().plotLength()) + " ft", x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Floors requested", String.valueOf(project.details().floors()), x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Geometry floors", geometryFloors(drawing), x + 10, cursor, width - 20);
        keyValue(canvas, "Orientation", titleCase(project.details().roadFacing().name()), x + 10, cursor, width - 20);
    }

    private void renderAreaBreakdown(PDPageContentStream canvas, DrawingCandidate drawing,
            float x, float y, float width, float height) throws IOException {
        card(canvas, "AREA BREAKDOWN", x, y, width, height);
        var areas = new LinkedHashMap<String, Double>();
        areas.put("Living & dining", areaFor(drawing, "LIVING", "DINING"));
        areas.put("Bedrooms", areaFor(drawing, "BEDROOM"));
        areas.put("Kitchen & utility", areaFor(drawing, "KITCHEN", "UTILITY"));
        areas.put("Bath & circulation", areaFor(drawing, "BATH", "TOILET", "STAIR"));
        var total = drawing.geometry().rooms().stream().mapToDouble(RoomGeometry::area).sum();
        var carpet = drawing.geometry().rooms().stream().filter(room -> !room.type().contains("PARKING"))
                .mapToDouble(RoomGeometry::area).sum();
        var cursor = y + height - 31;
        for (var entry : areas.entrySet()) {
            cursor = keyValue(canvas, entry.getKey(), grouped(Math.round(entry.getValue())) + " sq ft",
                    x + 10, cursor, width - 20);
        }
        cursor = keyValue(canvas, "Placed footprint", grouped(Math.round(total)) + " sq ft", x + 10, cursor, width - 20);
        keyValue(canvas, "Indicative carpet", grouped(Math.round(carpet)) + " sq ft", x + 10, cursor, width - 20);
    }

    private double areaFor(DrawingCandidate drawing, String... fragments) {
        return drawing.geometry().rooms().stream().filter(room -> {
            for (var fragment : fragments) if (room.type().contains(fragment)) return true;
            return false;
        }).mapToDouble(RoomGeometry::area).sum();
    }

    private void renderProvenance(PDPageContentStream canvas, DrawingCandidate drawing,
            float x, float y, float width, float height) throws IOException {
        card(canvas, "GENERATION PROVENANCE", x, y, width, height);
        var versions = versions(drawing);
        var cursor = y + height - 31;
        cursor = keyValue(canvas, "Generator", versions.getOrDefault("generator", "Not recorded"), x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Generation model", versions.getOrDefault("generationModel", "Not recorded"), x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Algorithm", versions.getOrDefault("strategyVersion", "Not recorded"), x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Strategy", versions.getOrDefault("strategyId", drawing.strategy()), x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Rule release", versions.getOrDefault("ruleVersion", "Not recorded"), x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Knowledge", versions.getOrDefault("knowledgeVersion", "Not recorded"), x + 10, cursor, width - 20);
        keyValue(canvas, "Optimizer seed", versions.getOrDefault("optimizerSeed", "Not recorded"), x + 10, cursor, width - 20);
    }

    private void card(PDPageContentStream canvas, String title, float x, float y, float width, float height)
            throws IOException {
        fill(canvas, Color.WHITE, x, y, width, height);
        stroke(canvas, LINE, .7f, x, y, width, height);
        text(canvas, BOLD, 6.2f, CORAL, title, x + 10, y + height - 16);
        line(canvas, LINE, .5f, x + 10, y + height - 22, x + width - 10, y + height - 22);
    }

    private void renderDisclaimer(PDPageContentStream canvas, ProjectSummary project, DrawingCandidate drawing)
            throws IOException {
        fill(canvas, INK, 36, 62, 523, 70);
        text(canvas, BOLD, 7, new Color(235, 193, 137), "AVAS CONCEPTUAL PLAN", 49, 114);
        var warning = "This plan is generated for planning and estimation. It must be reviewed by a qualified "
                + "architect and structural engineer before construction.";
        var lines = wrap(warning, REGULAR, 6.8f, 480);
        var y = 99f;
        for (var value : lines) { text(canvas, REGULAR, 6.8f, Color.WHITE, value, 49, y); y -= 10; }
        var reviewNote = firstOrDefault(drawing.softRecommendations(), "Professional review is required.");
        text(canvas, REGULAR, 5.2f, new Color(205, 197, 185),
                "Review note: " + fitWidth(reviewNote, REGULAR, 5.2f, 470), 49, 78);
        text(canvas, REGULAR, 5.2f, new Color(205, 197, 185),
                "Warnings: " + sizeOf(drawing.softRecommendations()) + "  |  Hard errors: "
                        + sizeOf(drawing.hardViolations()) + "  |  Professional review: REQUIRED", 49, 68);

        text(canvas, BOLD, 7, INK, "AVAS", 36, 42);
        text(canvas, REGULAR, 5.3f, MUTED,
                safe(project.projectCode() + "  |  Drawing v" + drawing.version() + "  |  "
                        + titleCase(drawing.strategy())), 78, 42);
        textRight(canvas, REGULAR, 5.3f, MUTED, "Design smarter. Validate before building.", 559, 42);
    }

    private void fill(PDPageContentStream canvas, Color color, float x, float y, float width, float height)
            throws IOException {
        canvas.setNonStrokingColor(color);
        canvas.addRect(x, y, width, height);
        canvas.fill();
    }

    private void stroke(PDPageContentStream canvas, Color color, float lineWidth,
            float x, float y, float width, float height) throws IOException {
        canvas.setStrokingColor(color);
        canvas.setLineWidth(lineWidth);
        canvas.addRect(x, y, width, height);
        canvas.stroke();
    }

    private void line(PDPageContentStream canvas, Color color, float lineWidth,
            float x1, float y1, float x2, float y2) throws IOException {
        canvas.setStrokingColor(color);
        canvas.setLineWidth(lineWidth);
        canvas.moveTo(x1, y1);
        canvas.lineTo(x2, y2);
        canvas.stroke();
    }

    private void text(PDPageContentStream canvas, PDFont font, float size, Color color,
            String value, float x, float y) throws IOException {
        canvas.beginText();
        canvas.setFont(font, size);
        canvas.setNonStrokingColor(color);
        canvas.newLineAtOffset(x, y);
        canvas.showText(safe(value));
        canvas.endText();
    }

    private void textRight(PDPageContentStream canvas, PDFont font, float size, Color color,
            String value, float right, float y) throws IOException {
        var safe = safe(value);
        text(canvas, font, size, color, safe, right - textWidth(font, size, safe), y);
    }

    private void textCentered(PDPageContentStream canvas, PDFont font, float size, Color color,
            String value, float center, float y) throws IOException {
        var safe = safe(value);
        text(canvas, font, size, color, safe, center - textWidth(font, size, safe) / 2, y);
    }

    private float textWidth(PDFont font, float size, String value) throws IOException {
        return font.getStringWidth(safe(value)) / 1000f * size;
    }

    private List<String> wrap(String value, PDFont font, float size, float maxWidth) throws IOException {
        var result = new ArrayList<String>();
        var current = new StringBuilder();
        for (var word : safe(value).split("\\s+")) {
            var candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && textWidth(font, size, candidate) > maxWidth) {
                result.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    private String titleCase(String value) {
        if (value == null || value.isBlank()) return "Not recorded";
        var result = new StringBuilder();
        for (var part : value.toLowerCase(Locale.ROOT).split("[_\\s]+")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private String ellipsize(String value, int maximum) {
        var safe = safe(value);
        return safe.length() <= maximum ? safe : safe.substring(0, Math.max(1, maximum - 3)) + "...";
    }

    private String fitWidth(String value, PDFont font, float size, float maximum) throws IOException {
        var candidate = safe(value);
        if (textWidth(font, size, candidate) <= maximum) return candidate;
        while (candidate.length() > 3 && textWidth(font, size, candidate + "...") > maximum) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate + "...";
    }

    private String lakhRange(DrawingCandidate drawing) {
        return String.format(Locale.ROOT, "INR %.1fL - %.1fL",
                drawing.estimatedCostLow() / 100_000.0, drawing.estimatedCostHigh() / 100_000.0);
    }

    private String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String grouped(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return Double.NaN;
        try { return Double.parseDouble(value.toString()); }
        catch (NumberFormatException ignored) { return Double.NaN; }
    }

    private Map<String, String> versions(DrawingCandidate drawing) {
        return drawing.versions() == null ? Map.of() : drawing.versions();
    }

    private int sizeOf(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private String firstOrDefault(List<String> values, String fallback) {
        return values == null || values.isEmpty() || values.getFirst() == null ? fallback : values.getFirst();
    }

    private String geometryFloors(DrawingCandidate drawing) {
        return drawing.geometry().rooms().stream().map(RoomGeometry::floor).filter(value -> value != null && !value.isBlank())
                .distinct().map(this::titleCase).sorted().reduce((left, right) -> left + ", " + right)
                .orElse("Not recorded");
    }

    private String safe(String value) {
        if (value == null) return "";
        return value.replace('\u2013', '-').replace('\u2014', '-').replace('\u00d7', 'x')
                .replace('\u2018', '\'').replace('\u2019', '\'').replace('\u201c', '"').replace('\u201d', '"')
                .replace("\u20b9", "INR ").replaceAll("[^\\x20-\\x7E]", "?");
    }
}

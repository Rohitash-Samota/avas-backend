package com.avas.platform.project;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static final Color WALL = new Color(49, 45, 40);
    private static final Color FIXTURE = new Color(151, 137, 118);
    private static final Color WINDOW = new Color(63, 109, 140);
    private static final Color GARDEN = new Color(226, 237, 218);
    private static final Color PAVING = new Color(235, 231, 223);
    private static final Color SITE_EDGE = new Color(150, 160, 140);
    private static final Color SITE_INK = new Color(96, 106, 88);
    private static final Color[] ROOM_COLORS = {
            new Color(242, 225, 200), new Color(232, 222, 208), new Color(233, 228, 219),
            new Color(229, 229, 224), new Color(220, 232, 210), new Color(239, 227, 212)
    };

    public byte[] generate(ProjectSummary project, DrawingCandidate drawing) {
        validate(project, drawing);
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            setMetadata(document.getDocumentInformation(), project, drawing);
            var floors = floorKeys(drawing);
            // The site sheet leads the set: a reviewer checks the legal envelope before the plan
            // that sits inside it. Older drawings carry no outline, so the set stays floors-only.
            var siteSheet = drawing.geometry().hasSiteContext();
            var sheetCount = floors.size() + (siteSheet ? 1 : 0);
            if (siteSheet) {
                var page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (var canvas = new PDPageContentStream(document, page)) {
                    renderSitePlan(canvas, project, drawing, sheetCount);
                }
            }
            for (var index = 0; index < floors.size(); index++) {
                var page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (var canvas = new PDPageContentStream(document, page)) {
                    render(canvas, project, drawing, floors.get(index),
                            index + 1 + (siteSheet ? 1 : 0), sheetCount);
                }
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
        if (multiFloorSchema(drawing)) {
            var represented = floorKeys(drawing);
            var expected = expectedFloorKeys(requestedFloorCount(project, drawing));
            if (!represented.equals(expected)) {
                throw new IllegalArgumentException("Multi-floor geometry is incomplete: expected " + expected
                        + " but found " + represented);
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

    private void render(PDPageContentStream canvas, ProjectSummary project, DrawingCandidate drawing,
            String floor, int sheetNumber, int sheetCount) throws IOException {
        fill(canvas, Color.WHITE, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());

        text(canvas, BOLD, 16, INK, "AVAS", 36, 804);
        text(canvas, REGULAR, 5.5f, MUTED, "ADAPTIVE HOME PLANNING", 36, 794);
        textRight(canvas, REGULAR, 6, MUTED,
                "SERVER-GENERATED FLOOR SET  |  SHEET " + sheetNumber + " OF " + sheetCount, 559, 802);
        line(canvas, LINE, .8f, 36, 784, 559, 784);

        text(canvas, BOLD, 25, INK, ellipsize(drawing.name().toUpperCase(Locale.ROOT), 34), 36, 750);
        var requestedFloors = requestedFloorCount(project, drawing);
        text(canvas, BOLD, 6.6f, CORAL,
                "TOTAL BUILT-UP EST. " + grouped(drawing.builtUpArea()) + " SQ FT  |  "
                        + requestedFloors + "-FLOOR "
                        + (legacyIncomplete(project, drawing) ? "LEGACY SET" : "COMPLETE SET"), 36, 735);
        var floorRooms = roomsForFloor(drawing, floor);
        var floorDoors = openingsForFloor(drawing.geometry().doors(), floor);
        var floorWindows = openingsForFloor(drawing.geometry().windows(), floor);
        var floorDetail = floorTitle(floor) + " geometry  |  " + floorRooms.size() + " spaces  |  "
                + floorDoors.size() + " doors  |  " + floorWindows.size() + " windows";
        if (legacyIncomplete(project, drawing)) {
            floorDetail = "LEGACY INCOMPLETE FLOOR SET  |  " + sheetCount + " OF " + requestedFloors
                    + " REQUESTED FLOORS AVAILABLE  |  REGENERATE REQUIRED";
        }
        text(canvas, REGULAR, 7, legacyIncomplete(project, drawing) ? CORAL : MUTED,
                fitWidth(floorDetail, REGULAR, 7, 523), 36, 722);
        selectionBadge(canvas, drawing.conceptApproved(), 438, 737, 121, 31);

        renderPlan(canvas, project, drawing, floor, sheetNumber, sheetCount, 36, 180, 523, 527);
        renderCompactSummary(canvas, project, drawing, floor, sheetNumber, sheetCount, 36, 105, 523, 62);
        renderDisclaimer(canvas, project, drawing, floor, sheetNumber, sheetCount);
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
        cursor = bullet(canvas, titleCase(drawingFacing(project, drawing).name()) + " facing", x + 10, cursor);

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

    private void renderPlan(PDPageContentStream canvas, ProjectSummary project, DrawingCandidate drawing,
            String floor, int sheetNumber, int sheetCount,
            float panelX, float panelY, float panelWidth, float panelHeight) throws IOException {
        fill(canvas, Color.WHITE, panelX, panelY, panelWidth, panelHeight);
        stroke(canvas, LINE, .7f, panelX, panelY, panelWidth, panelHeight);
        var geometry = drawing.geometry();
        var rooms = roomsForFloor(drawing, floor);
        var topReserved = 44f;
        var bottomReserved = 38f;
        var availableWidth = panelWidth - 56;
        var availableHeight = panelHeight - topReserved - bottomReserved;
        var scale = (float) Math.min(availableWidth / geometry.plotWidth(), availableHeight / geometry.plotLength());
        var plotWidth = (float) geometry.plotWidth() * scale;
        var plotHeight = (float) geometry.plotLength() * scale;
        var originX = panelX + (panelWidth - plotWidth) / 2;
        var originY = panelY + bottomReserved + (availableHeight - plotHeight) / 2;
        // Planning coordinates grow east and north; PDF y also grows up, so the plate is drawn
        // without a flip and reads with north at the top, exactly like the site sheet. The plot
        // corner offset keeps outlines that are not anchored at the origin in register.
        var siteBounds = geometry.hasSiteContext() ? PlotGeometry.bounds(geometry.plotOutline()) : null;
        var planX = originX - (float) (siteBounds == null ? 0 : siteBounds.minimumX()) * scale;
        var planY = originY - (float) (siteBounds == null ? 0 : siteBounds.minimumY()) * scale;

        text(canvas, BOLD, 6.2f, CORAL, "AUTHORITATIVE FLOOR PLAN MAP", panelX + 12,
                panelY + panelHeight - 16);
        text(canvas, BOLD, 10, INK, floorTitle(floor).toUpperCase(Locale.ROOT) + " PLAN",
                panelX + 12, panelY + panelHeight - 29);
        textRight(canvas, BOLD, 5.8f, CORAL, "SHEET " + sheetNumber + " / " + sheetCount,
                panelX + panelWidth - 12, panelY + panelHeight - 29);
        textRight(canvas, REGULAR, 5.8f, MUTED, "ALL DIMENSIONS IN FEET", panelX + panelWidth - 12,
                panelY + panelHeight - 16);
        if (siteBounds == null) {
            fill(canvas, new Color(253, 252, 249), originX, originY, plotWidth, plotHeight);
            stroke(canvas, WALL, 1.1f, originX, originY, plotWidth, plotHeight);
        } else {
            // The plate is drawn on the surveyed outline, not on its bounding box, so a reader sees
            // the rooms against the plot that was actually measured and the open space that is left.
            java.util.function.BiFunction<Double, Double, float[]> project2d = (px, py) ->
                    new float[] {planX + (float) (double) px * scale, planY + (float) (double) py * scale};
            polygon(canvas, geometry.plotOutline(), project2d, new Color(253, 252, 249), WALL, 1.1f);
            polygon(canvas, geometry.buildableOutline(), project2d, new Color(247, 249, 244),
                    new Color(150, 173, 137), .7f);
        }

        // Only under the ground plate: parking and garden are on the ground, and repeating them
        // beneath an upper floor would draw a lawn a storey up.
        if (isGroundFloor(floor)) {
            renderSiteElements(canvas, geometry.siteElements(), planX, planY, scale);
        }
        for (int index = 0; index < rooms.size(); index++) {
            var room = rooms.get(index);
            var roomX = planX + (float) room.x() * scale;
            var roomY = planY + (float) room.y() * scale;
            var roomWidth = (float) room.width() * scale;
            var roomHeight = (float) room.length() * scale;
            fill(canvas, ROOM_COLORS[index % ROOM_COLORS.length], roomX, roomY, roomWidth, roomHeight);
        }
        // Partitions are stroked after every fill so a later room's fill cannot paint over the wall
        // it shares with an earlier one, which is what made adjacent rooms read as one space.
        for (var room : rooms) {
            stroke(canvas, WALL, partitionWallWeight(scale), planX + (float) room.x() * scale,
                    planY + (float) room.y() * scale, (float) room.width() * scale,
                    (float) room.length() * scale);
        }
        for (var room : rooms) {
            var roomX = planX + (float) room.x() * scale;
            var roomY = planY + (float) room.y() * scale;
            var roomWidth = (float) room.width() * scale;
            var roomHeight = (float) room.length() * scale;
            renderRoomFixture(canvas, room, roomX, roomY, roomWidth, roomHeight);
            renderRoomLabel(canvas, room, roomX, roomY, roomWidth, roomHeight);
        }
        renderBuildingEnvelope(canvas, rooms, planX, planY, scale);
        renderOpenings(canvas, openingsForFloor(geometry.doors(), floor),
                openingsForFloor(geometry.windows(), floor), planX, planY, scale);
        renderRoomDimensionChains(canvas, rooms, originX, originY, plotWidth, plotHeight, planX, planY, scale);
        renderPlanAnnotations(canvas, project, drawing, panelX, panelY, panelWidth, panelHeight,
                originX, originY, plotWidth, plotHeight, floor, rooms);
        renderScaleBar(canvas, panelX + 12, panelY + 24, scale);
    }

    private void renderRoomLabel(PDPageContentStream canvas, RoomGeometry room,
            float roomX, float roomY, float roomWidth, float roomHeight) throws IOException {
        if (roomWidth < 18 || roomHeight < 18) return;
        var label = titleCase(room.type());
        if (roomWidth < 28 && roomHeight >= 36) {
            var verticalSize = Math.min(5f, Math.max(3.8f, roomWidth * .2f));
            label = fitWidth(label, BOLD, verticalSize, roomHeight - 10);
            textRotated(canvas, BOLD, verticalSize, INK, label,
                    roomX + roomWidth / 2, roomY + roomHeight / 2, 90);
            return;
        }
        var fontSize = Math.min(7.4f, roomHeight * .12f);
        var availableWidth = Math.max(8, roomWidth - 8);
        while (fontSize > 4.2f && textWidth(BOLD, fontSize, label) > availableWidth) fontSize -= .2f;
        if (textWidth(BOLD, fontSize, label) > availableWidth) label = compactRoomLabel(room.type());
        while (fontSize > 3.4f && textWidth(BOLD, fontSize, label) > availableWidth) fontSize -= .2f;
        label = fitWidth(label, BOLD, fontSize, availableWidth);

        // Name, size, area: the three facts a room is annotated with on a real plan, in that order
        // of importance. Each line is dropped independently when the room is too small to carry it,
        // so a utility cupboard still gets its name rather than an overset block of text.
        var centreX = roomX + roomWidth / 2;
        var labelY = roomY + roomHeight * .39f;
        textCentered(canvas, BOLD, fontSize, INK, label, centreX, labelY);
        var detailSize = Math.max(3.4f, fontSize - 1.2f);
        var lineHeight = Math.max(6f, detailSize + 1.6f);
        var dimensions = feetInches(room.width()) + " x " + feetInches(room.length());
        var cursor = labelY - Math.max(7, fontSize + 1);
        if (roomHeight >= 34 && textWidth(REGULAR, detailSize, dimensions) <= roomWidth - 6) {
            textCentered(canvas, REGULAR, detailSize, MUTED, dimensions, centreX, cursor);
            cursor -= lineHeight;
        }
        var area = grouped(Math.round(room.area())) + " SQ FT";
        if (roomHeight >= 46 && textWidth(REGULAR, detailSize - .3f, area) <= roomWidth - 6) {
            textCentered(canvas, REGULAR, detailSize - .3f, MUTED, area, centreX, cursor);
        }
    }

    private String compactRoomLabel(String type) {
        return switch (type == null ? "" : type) {
            case "LIVING_ROOM" -> "Living";
            case "SENIOR_BEDROOM" -> "Bedroom";
            case "MASTER_BEDROOM" -> "Master Bed";
            case "FAMILY_LOUNGE" -> "Lounge";
            case "MULTIPURPOSE_ROOM" -> "Multi-use";
            case "ATTACHED_BATHROOM" -> "Ensuite";
            case "DRESSING_ROOM" -> "Dressing";
            case "HOME_OFFICE" -> "Office";
            case "PRAYER_ROOM" -> "Prayer";
            case "STAIRCASE" -> "Stairs";
            case "LIFT_SHAFT" -> "Lift";
            case "COURTYARD_PARKING" -> "Court / Park";
            case "OPEN_SPACE" -> "Open Space";
            case "BATHROOM" -> "Bath";
            default -> fitCharacters(titleCase(type), 9);
        };
    }

    private String fitCharacters(String value, int maximum) {
        var safe = safe(value);
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }

    private void renderRoomFixture(PDPageContentStream canvas, RoomGeometry room,
            float roomX, float roomY, float roomWidth, float roomHeight) throws IOException {
        if (roomWidth < 30 || roomHeight < 30) return;
        var type = room.type() == null ? "" : room.type();
        var centerX = roomX + roomWidth / 2;
        var fixtureY = roomY + roomHeight * .63f;
        canvas.setStrokingColor(FIXTURE);
        canvas.setLineWidth(.45f);

        if (type.contains("PARKING")) {
            var width = Math.min(34, roomWidth * .42f);
            var height = Math.min(54, roomHeight * .27f);
            var x = centerX - width / 2;
            var y = fixtureY;
            stroke(canvas, FIXTURE, .55f, x, y, width, height);
            line(canvas, FIXTURE, .4f, x + 5, y + height * .28f, x + width - 5, y + height * .28f);
            line(canvas, FIXTURE, .4f, x + 5, y + height * .72f, x + width - 5, y + height * .72f);
            line(canvas, FIXTURE, 1.4f, x - 2, y + 8, x - 2, y + 18);
            line(canvas, FIXTURE, 1.4f, x + width + 2, y + 8, x + width + 2, y + 18);
            line(canvas, FIXTURE, 1.4f, x - 2, y + height - 18, x - 2, y + height - 8);
            line(canvas, FIXTURE, 1.4f, x + width + 2, y + height - 18, x + width + 2, y + height - 8);
        } else if (type.contains("LIVING") || type.contains("LOUNGE") || type.contains("MULTIPURPOSE")) {
            var width = Math.min(76, roomWidth * .58f);
            var height = Math.min(18, roomHeight * .14f);
            var x = centerX - width / 2;
            stroke(canvas, FIXTURE, .55f, x, fixtureY, width, height);
            line(canvas, FIXTURE, .4f, x + width / 3, fixtureY, x + width / 3, fixtureY + height);
            line(canvas, FIXTURE, .4f, x + width * 2 / 3, fixtureY, x + width * 2 / 3, fixtureY + height);
            circle(canvas, FIXTURE, .45f, centerX, fixtureY - 9, Math.min(8, width * .12f));
        } else if (type.contains("BEDROOM")) {
            var width = Math.min(58, roomWidth * .55f);
            var height = Math.min(42, roomHeight * .28f);
            var x = centerX - width / 2;
            stroke(canvas, FIXTURE, .55f, x, fixtureY, width, height);
            line(canvas, FIXTURE, .45f, x, fixtureY + height - 10, x + width, fixtureY + height - 10);
            stroke(canvas, FIXTURE, .35f, x + 4, fixtureY + height - 8, width / 2 - 6, 6);
            stroke(canvas, FIXTURE, .35f, centerX + 2, fixtureY + height - 8, width / 2 - 6, 6);
        } else if (type.contains("DINING")) {
            var width = Math.min(46, roomWidth * .58f);
            var height = Math.min(20, roomHeight * .2f);
            var x = centerX - width / 2;
            stroke(canvas, FIXTURE, .55f, x, fixtureY, width, height);
            for (var chairX : new float[]{x + width * .2f, centerX, x + width * .8f}) {
                circle(canvas, FIXTURE, .4f, chairX, fixtureY - 4, 2.2f);
                circle(canvas, FIXTURE, .4f, chairX, fixtureY + height + 4, 2.2f);
            }
        } else if (type.contains("KITCHEN")) {
            var left = roomX + 6;
            var right = roomX + roomWidth - 6;
            var top = roomY + roomHeight - 7;
            line(canvas, FIXTURE, 2.2f, left, top, right, top);
            line(canvas, FIXTURE, 2.2f, right, top, right, roomY + roomHeight * .58f);
            stroke(canvas, FIXTURE, .45f, centerX - 7, top - 4, 14, 6);
        } else if (type.contains("STAIR")) {
            var left = roomX + 7;
            var right = roomX + roomWidth - 7;
            var start = roomY + roomHeight * .58f;
            var step = Math.max(3, Math.min(5, roomHeight * .045f));
            for (var index = 0; index < 6; index++) {
                var y = start + index * step;
                if (y > roomY + roomHeight - 5) break;
                line(canvas, FIXTURE, .45f, left, y, right, y);
            }
            line(canvas, FIXTURE, .7f, centerX, start, centerX, Math.min(roomY + roomHeight - 5, start + step * 5));
        } else if (type.contains("LIFT")) {
            var size = Math.min(18, Math.min(roomWidth, roomHeight) * .42f);
            var x = centerX - size / 2;
            var y = fixtureY;
            stroke(canvas, FIXTURE, .65f, x, y, size, size);
            line(canvas, FIXTURE, .45f, x + 3, y + 3, x + size - 3, y + size - 3);
            line(canvas, FIXTURE, .45f, x + size - 3, y + 3, x + 3, y + size - 3);
        } else if (type.contains("BATH") || type.contains("TOILET")) {
            var radius = Math.min(7, Math.min(roomWidth, roomHeight) * .15f);
            circle(canvas, FIXTURE, .5f, centerX, fixtureY + radius, radius);
            stroke(canvas, FIXTURE, .45f, centerX - radius, fixtureY + radius * 2, radius * 2, 5);
        } else if (type.contains("UTILITY") || type.contains("LAUNDRY")) {
            var size = Math.min(21, Math.min(roomWidth, roomHeight) * .3f);
            stroke(canvas, FIXTURE, .5f, centerX - size / 2, fixtureY, size, size);
            circle(canvas, FIXTURE, .45f, centerX, fixtureY + size / 2, size * .32f);
        } else if (type.contains("STUDY") || type.contains("OFFICE")) {
            var width = Math.min(42, roomWidth * .55f);
            line(canvas, FIXTURE, 2f, centerX - width / 2, fixtureY + 8, centerX + width / 2, fixtureY + 8);
            stroke(canvas, FIXTURE, .5f, centerX - 7, fixtureY - 2, 14, 10);
        } else if (type.contains("BALCONY") || type.contains("TERRACE")) {
            var radius = Math.min(7, Math.min(roomWidth, roomHeight) * .14f);
            circle(canvas, FIXTURE, .55f, centerX, fixtureY + radius, radius);
            line(canvas, FIXTURE, .55f, centerX, fixtureY, centerX, fixtureY - radius);
        } else if (type.contains("PRAYER")) {
            var width = Math.min(28, roomWidth * .45f);
            stroke(canvas, FIXTURE, .55f, centerX - width / 2, fixtureY, width, 12);
            line(canvas, FIXTURE, .45f, centerX, fixtureY + 12, centerX, fixtureY + 21);
        }
    }

    /**
     * The open ground: parking on the approach, garden on what is left.
     *
     * <p>Drawn before the rooms so the building always paints over it, and deliberately unlike a
     * room — a dashed edge and a wash rather than a walled, filled space. A reader has to be able to
     * tell at a glance which lines are slab they are paying to build and which are the plot around
     * it; a lawn drawn with a room's weight would read as an extra 600 sq ft of house.</p>
     */
    private void renderSiteElements(PDPageContentStream canvas, List<SiteElement> elements,
            float planX, float planY, float scale) throws IOException {
        for (var element : elements) {
            var x = planX + (float) element.x() * scale;
            var y = planY + (float) element.y() * scale;
            var width = (float) element.width() * scale;
            var height = (float) element.length() * scale;
            if (width < 4 || height < 4) continue;
            fill(canvas, "GARDEN".equals(element.type()) ? GARDEN : PAVING, x, y, width, height);
            canvas.setLineDashPattern(new float[] {2.4f, 1.8f}, 0);
            stroke(canvas, SITE_EDGE, .7f, x, y, width, height);
            canvas.setLineDashPattern(new float[] {}, 0);

            var label = safe(element.label());
            var size = Math.min(5.6f, Math.max(3.6f, height * .18f));
            while (size > 3.4f && textWidth(BOLD, size, label) > width - 5) size -= .2f;
            if (textWidth(BOLD, size, label) > width - 5 || height < 11) continue;
            var centreY = y + height / 2;
            textCentered(canvas, BOLD, size, SITE_INK, label, x + width / 2,
                    height >= 20 ? centreY + size * .3f : centreY - size * .35f);
            var area = grouped(Math.round(element.area())) + " SQ FT";
            if (height >= 20 && textWidth(REGULAR, size - .8f, area) <= width - 5) {
                textCentered(canvas, REGULAR, size - .8f, SITE_INK, area, x + width / 2,
                        centreY - size * .9f);
            }
        }
    }

    private void renderBuildingEnvelope(PDPageContentStream canvas, List<RoomGeometry> rooms,
            float originX, float originY, float scale) throws IOException {
        if (rooms == null || rooms.isEmpty()) return;
        var minimumX = rooms.stream().mapToDouble(RoomGeometry::x).min().orElse(0);
        var maximumX = rooms.stream().mapToDouble(room -> room.x() + room.width()).max().orElse(0);
        var minimumY = rooms.stream().mapToDouble(RoomGeometry::y).min().orElse(0);
        var maximumY = rooms.stream().mapToDouble(room -> room.y() + room.length()).max().orElse(0);
        var x = originX + (float) minimumX * scale;
        var y = originY + (float) minimumY * scale;
        var width = (float) (maximumX - minimumX) * scale;
        var height = (float) (maximumY - minimumY) * scale;
        stroke(canvas, WALL, exteriorWallWeight(scale), x, y, width, height);
    }

    /**
     * Wall weights taken from the wall itself rather than from a fixed hairline.
     *
     * <p>A plan reads as a construction drawing largely because its walls have mass: a 9 inch
     * external wall and a 4.5 inch partition are visibly different, and both are visibly thicker
     * than a furniture outline. Deriving the weight from the plate scale keeps that relationship
     * true on a site sheet and on a full-page plan alike, and the clamps stop a very small or very
     * large plate collapsing the distinction or flooding the plan with ink.</p>
     */
    private float exteriorWallWeight(float scale) {
        return Math.clamp(.75f * scale, 1.6f, 8f);
    }

    private float partitionWallWeight(float scale) {
        return Math.clamp(.375f * scale, 1f, 4.4f);
    }

    private void renderOpenings(PDPageContentStream canvas, List<Map<String, Object>> doors,
            List<Map<String, Object>> windows, float originX, float originY, float scale)
            throws IOException {
        for (var door : doors == null ? List.<Map<String, Object>>of() : doors) {
            var x = number(door.get("x"));
            var y = number(door.get("y"));
            var width = Math.max(2.4, number(door.get("width")));
            if (!Double.isFinite(x) || !Double.isFinite(y)) continue;
            var orientation = normalizedOrientation(door.get("orientation"), "SOUTH");
            var vertical = "EAST".equals(orientation) || "WEST".equals(orientation);
            if (vertical) {
                renderVerticalDoor(canvas, x, y, width, orientation, door.get("swing"),
                        originX, originY, scale);
            } else {
                renderHorizontalDoor(canvas, x, y, width, orientation, door.get("swing"),
                        originX, originY, scale);
            }
            renderDoorTag(canvas, doorTag(door, width), originX + (float) x * scale,
                    originY + (float) y * scale, vertical, scale);
        }
        for (var window : windows == null ? List.<Map<String, Object>>of() : windows) {
            var x = number(window.get("x"));
            var y = number(window.get("y"));
            var width = Math.max(2.5, number(window.get("width")));
            if (!Double.isFinite(x) || !Double.isFinite(y)) continue;
            var orientation = normalizedOrientation(window.get("orientation"), "WEST");
            if ("NORTH".equals(orientation) || "SOUTH".equals(orientation)) {
                var fromX = originX + (float) (x - width / 2) * scale;
                var toX = originX + (float) (x + width / 2) * scale;
                var pointY = originY + (float) y * scale;
                line(canvas, Color.WHITE, 3.1f, fromX, pointY, toX, pointY);
                line(canvas, WINDOW, .8f, fromX, pointY - 1.1f, toX, pointY - 1.1f);
                line(canvas, WINDOW, .8f, fromX, pointY + 1.1f, toX, pointY + 1.1f);
            } else {
                var pointX = originX + (float) x * scale;
                var fromY = originY + (float) (y - width / 2) * scale;
                var toY = originY + (float) (y + width / 2) * scale;
                line(canvas, Color.WHITE, 3.1f, pointX, fromY, pointX, toY);
                line(canvas, WINDOW, .8f, pointX - 1.1f, fromY, pointX - 1.1f, toY);
                line(canvas, WINDOW, .8f, pointX + 1.1f, fromY, pointX + 1.1f, toY);
            }
        }
    }

    /**
     * The door schedule mark a builder orders against.
     *
     * <p>Indian residential sets tag the main door {@code MD} and number the rest by leaf width, so
     * a 3 ft bedroom door and a 2 ft 6 in toilet door can be counted and priced separately from one
     * schedule. Derived from the opening itself rather than stored, because the geometry already
     * knows which door faces the street and how wide each leaf is.</p>
     */
    private String doorTag(Map<String, Object> door, double width) {
        if (Boolean.TRUE.equals(door.get("exterior"))) return "MD";
        return width >= 2.9 ? "D1" : "D2";
    }

    private void renderDoorTag(PDPageContentStream canvas, String tag, float x, float y,
            boolean vertical, float scale) throws IOException {
        // Below a certain plate scale the tags collide with the swings they belong to and stop being
        // readable; the schedule still carries them, so the plan simply omits them.
        if (scale < 5.5f) return;
        var size = 4.2f;
        var offset = 5.4f;
        var tagX = vertical ? x + offset : x;
        var tagY = vertical ? y : y - offset;
        var half = textWidth(BOLD, size, tag) / 2 + 1.1f;
        fill(canvas, Color.WHITE, tagX - half, tagY - 1.6f, half * 2, size + .8f);
        textCentered(canvas, BOLD, size, CORAL, tag, tagX, tagY);
    }

    private void renderHorizontalDoor(PDPageContentStream canvas, double x, double y, double width,
            String orientation, Object swing, float originX, float originY, float scale)
            throws IOException {
        var startX = originX + (float) (x - width / 2) * scale;
        var endX = originX + (float) (x + width / 2) * scale;
        var pointY = originY + (float) y * scale;
        line(canvas, Color.WHITE, 3.1f, startX, pointY, endX, pointY);
        var radius = endX - startX;
        var direction = "NORTH".equals(orientation) ? -1f : 1f;
        var rightHinge = "RIGHT".equalsIgnoreCase(String.valueOf(swing));
        var hingeX = rightHinge ? endX : startX;
        line(canvas, CORAL, .8f, hingeX, pointY, hingeX, pointY + direction * radius);
        canvas.setStrokingColor(CORAL);
        canvas.setLineWidth(.7f);
        if (rightHinge) {
            canvas.moveTo(startX, pointY);
            canvas.curveTo(startX, pointY + direction * radius * .55f,
                    endX - radius * .55f, pointY + direction * radius, endX, pointY + direction * radius);
        } else {
            canvas.moveTo(endX, pointY);
            canvas.curveTo(endX, pointY + direction * radius * .55f,
                    startX + radius * .55f, pointY + direction * radius, startX, pointY + direction * radius);
        }
        canvas.stroke();
    }

    private void renderVerticalDoor(PDPageContentStream canvas, double x, double y, double width,
            String orientation, Object swing, float originX, float originY, float scale)
            throws IOException {
        var pointX = originX + (float) x * scale;
        var fromY = originY + (float) (y - width / 2) * scale;
        var toY = originY + (float) (y + width / 2) * scale;
        line(canvas, Color.WHITE, 3.1f, pointX, fromY, pointX, toY);
        var radius = toY - fromY;
        var direction = "EAST".equals(orientation) ? -1f : 1f;
        var upperHinge = "RIGHT".equalsIgnoreCase(String.valueOf(swing));
        var hingeY = upperHinge ? toY : fromY;
        line(canvas, CORAL, .8f, pointX, hingeY, pointX + direction * radius, hingeY);
        canvas.setStrokingColor(CORAL);
        canvas.setLineWidth(.7f);
        if (upperHinge) {
            canvas.moveTo(pointX, fromY);
            canvas.curveTo(pointX + direction * radius * .55f, fromY,
                    pointX + direction * radius, toY - radius * .55f, pointX + direction * radius, toY);
        } else {
            canvas.moveTo(pointX, toY);
            canvas.curveTo(pointX + direction * radius * .55f, toY,
                    pointX + direction * radius, fromY + radius * .55f, pointX + direction * radius, fromY);
        }
        canvas.stroke();
    }

    private String normalizedOrientation(Object value, String fallback) {
        if (value == null) return fallback;
        var orientation = value.toString().toUpperCase(Locale.ROOT);
        return switch (orientation) {
            case "NORTH", "SOUTH", "EAST", "WEST" -> orientation;
            default -> fallback;
        };
    }

    /**
     * Sheet A1: the legal envelope, drawn before any layout that depends on it.
     *
     * <p>Shows the surveyed outline, the setback ring and the rectangle rooms are packed into, so a
     * reviewer can see at a glance how much of the plot the assumption consumed and how much
     * buildable area the packer left unused.</p>
     */
    private void renderSitePlan(PDPageContentStream canvas, ProjectSummary project,
            DrawingCandidate drawing, int sheetCount) throws IOException {
        fill(canvas, Color.WHITE, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
        var geometry = drawing.geometry();

        text(canvas, BOLD, 16, INK, "AVAS", 36, 804);
        text(canvas, REGULAR, 5.5f, MUTED, "ADAPTIVE HOME PLANNING", 36, 794);
        textRight(canvas, REGULAR, 6, MUTED, "SERVER-GENERATED FLOOR SET  |  SHEET 1 OF " + sheetCount,
                559, 802);
        line(canvas, LINE, .8f, 36, 784, 559, 784);
        text(canvas, BOLD, 25, INK, "SITE PLAN", 36, 750);
        text(canvas, BOLD, 6.6f, CORAL, "PLOT "
                + grouped(Math.round(geometry.plotArea())) + " SQ FT  |  BUILDABLE "
                + grouped(Math.round(geometry.buildableArea())) + " SQ FT  |  "
                + geometry.plotOutline().size() + " SURVEYED CORNERS", 36, 735);

        var panelX = 36f;
        var panelY = 180f;
        var panelWidth = 523f;
        var panelHeight = 527f;
        fill(canvas, Color.WHITE, panelX, panelY, panelWidth, panelHeight);
        stroke(canvas, LINE, .7f, panelX, panelY, panelWidth, panelHeight);

        var bounds = PlotGeometry.bounds(geometry.plotOutline());
        var scale = (float) Math.min((panelWidth - 90) / Math.max(bounds.width(), .001),
                (panelHeight - 120) / Math.max(bounds.length(), .001));
        var originX = panelX + (panelWidth - (float) bounds.width() * scale) / 2;
        var originY = panelY + 60 + (panelHeight - 120 - (float) bounds.length() * scale) / 2;
        // Planning y grows north; PDF y also grows up, so the outline maps without a flip.
        java.util.function.BiFunction<Double, Double, float[]> project2d = (x, y) -> new float[] {
                originX + (float) ((x - bounds.minimumX()) * scale),
                originY + (float) ((y - bounds.minimumY()) * scale)};

        text(canvas, BOLD, 6.2f, CORAL, "LEGAL ENVELOPE", panelX + 12, panelY + panelHeight - 16);
        text(canvas, BOLD, 10, INK, "PLOT, SETBACKS AND BUILDABLE AREA", panelX + 12,
                panelY + panelHeight - 29);
        textRight(canvas, REGULAR, 5.8f, MUTED, "ALL DIMENSIONS IN FEET", panelX + panelWidth - 12,
                panelY + panelHeight - 16);

        polygon(canvas, geometry.plotOutline(), project2d, new Color(247, 245, 240), WALL, 1.4f);
        polygon(canvas, geometry.buildableOutline(), project2d, MINT, new Color(125, 154, 110), 1f);

        var footprint = footprintOf(drawing);
        if (footprint != null) {
            var corner = project2d.apply(footprint[0], footprint[1]);
            fill(canvas, new Color(232, 226, 214), corner[0], corner[1],
                    (float) footprint[2] * scale, (float) footprint[3] * scale);
            stroke(canvas, CORAL, 1.2f, corner[0], corner[1],
                    (float) footprint[2] * scale, (float) footprint[3] * scale);
            textCentered(canvas, BOLD, 6, CORAL, "BUILDING FOOTPRINT",
                    corner[0] + (float) footprint[2] * scale / 2,
                    corner[1] + (float) footprint[3] * scale / 2);
        }

        var setbacks = geometry.setbacks();
        if (setbacks != null) {
            text(canvas, REGULAR, 5.4f, MUTED, "Setback front " + oneDecimal(setbacks.front())
                    + " ft  |  rear " + oneDecimal(setbacks.rear()) + " ft  |  side "
                    + oneDecimal(setbacks.side()) + " ft  |  source " + setbacks.source(),
                    panelX + 12, panelY + 44);
        }
        renderScaleBar(canvas, panelX + 12, panelY + 24, scale);

        var northX = panelX + panelWidth - 23;
        var northY = panelY + panelHeight - 63;
        line(canvas, INK, 1f, northX, northY, northX, northY + 16);
        line(canvas, INK, 1f, northX, northY + 16, northX - 4, northY + 10);
        line(canvas, INK, 1f, northX, northY + 16, northX + 4, northY + 10);
        textCentered(canvas, BOLD, 6, INK, "N", northX, northY - 8);

        renderCompactSummary(canvas, project, drawing, floorKeys(drawing).get(0), 1, sheetCount,
                36, 105, 523, 62);
        renderDisclaimer(canvas, project, drawing, floorKeys(drawing).get(0), 1, sheetCount);
    }

    /** Footprint as {x, y, width, length} recovered from the packed rooms, or null when empty. */
    private double[] footprintOf(DrawingCandidate drawing) {
        var rooms = drawing.geometry().rooms();
        if (rooms.isEmpty()) {
            return null;
        }
        var minX = rooms.stream().mapToDouble(RoomGeometry::x).min().orElse(0);
        var minY = rooms.stream().mapToDouble(RoomGeometry::y).min().orElse(0);
        var maxX = rooms.stream().mapToDouble(room -> room.x() + room.width()).max().orElse(0);
        var maxY = rooms.stream().mapToDouble(room -> room.y() + room.length()).max().orElse(0);
        return new double[] {minX, minY, maxX - minX, maxY - minY};
    }

    private void polygon(PDPageContentStream canvas, List<PlotVertex> ring,
            java.util.function.BiFunction<Double, Double, float[]> project2d,
            Color fill, Color edge, float weight) throws IOException {
        if (ring.size() < 3) {
            return;
        }
        canvas.setNonStrokingColor(fill);
        canvas.setStrokingColor(edge);
        canvas.setLineWidth(weight);
        var first = project2d.apply(ring.get(0).x(), ring.get(0).y());
        canvas.moveTo(first[0], first[1]);
        for (var index = 1; index < ring.size(); index++) {
            var point = project2d.apply(ring.get(index).x(), ring.get(index).y());
            canvas.lineTo(point[0], point[1]);
        }
        canvas.closePath();
        canvas.fillAndStroke();
    }

    /** Foot increments a reader can actually count off a printed bar. */
    private static final int[] NICE_STEPS = {1, 2, 5, 10, 20, 25, 50, 100};

    /**
     * Draws a graduated scale bar plus the stated ratio.
     *
     * <p>The plan is fitted to the panel, so its ratio is rarely a round number and a printer that
     * rescales the sheet invalidates it entirely. The graphic bar is the part that stays true under
     * rescaling, which is why the drawing carries both rather than a ratio alone.</p>
     *
     * @param scale points per foot used to draw the plan
     */
    private void renderScaleBar(PDPageContentStream canvas, float x, float y, float scale) throws IOException {
        var segments = 4;
        var step = niceStep(110f / (segments * Math.max(scale, .0001f)));
        var segmentWidth = step * scale;
        var barHeight = 4.2f;
        for (var index = 0; index < segments; index++) {
            var segmentX = x + index * segmentWidth;
            if (index % 2 == 0) {
                fill(canvas, INK, segmentX, y, segmentWidth, barHeight);
            } else {
                fill(canvas, Color.WHITE, segmentX, y, segmentWidth, barHeight);
                stroke(canvas, INK, .5f, segmentX, y, segmentWidth, barHeight);
            }
        }
        stroke(canvas, INK, .5f, x, y, segments * segmentWidth, barHeight);
        for (var index = 0; index <= segments; index += 2) {
            textCentered(canvas, REGULAR, 4.6f, MUTED, String.valueOf((int) (index * step)),
                    x + index * segmentWidth, y - 6);
        }
        textCentered(canvas, REGULAR, 4.6f, MUTED, "ft",
                x + segments * segmentWidth + 7, y - 6);
        // 1 pt is 1/72 in, so a foot drawn at `scale` pt represents 12 in of reality.
        var ratio = Math.round(864f / Math.max(scale, .0001f));
        text(canvas, BOLD, 4.9f, MUTED, "SCALE 1:" + ratio + " AT A4", x, y + barHeight + 4);
    }

    private float niceStep(float raw) {
        for (var candidate : NICE_STEPS) {
            if (candidate >= raw) {
                return candidate;
            }
        }
        return NICE_STEPS[NICE_STEPS.length - 1];
    }

    private void renderPlanAnnotations(PDPageContentStream canvas, ProjectSummary project, DrawingCandidate drawing,
            float panelX, float panelY, float panelWidth, float panelHeight, float originX, float originY,
            float plotWidth, float plotHeight, String floor, List<RoomGeometry> rooms) throws IOException {
        dimensionHorizontal(canvas, originX, originX + plotWidth, originY + plotHeight + 8,
                oneDecimal(drawing.geometry().plotWidth()) + " ft plot width");
        dimensionVertical(canvas, originX - 11, originY, originY + plotHeight,
                oneDecimal(drawing.geometry().plotLength()) + " ft plot length");
        var northX = panelX + panelWidth - 23;
        var northY = panelY + panelHeight - 63;
        line(canvas, INK, 1f, northX, northY, northX, northY + 16);
        line(canvas, INK, 1f, northX, northY + 16, northX - 4, northY + 10);
        line(canvas, INK, 1f, northX, northY + 16, northX + 4, northY + 10);
        textCentered(canvas, BOLD, 6, INK, "N", northX, northY - 8);

        var facing = drawingFacing(project, drawing);
        switch (facing) {
            case NORTH -> line(canvas, CORAL, 2.2f, originX, originY + plotHeight, originX + plotWidth,
                    originY + plotHeight);
            case SOUTH -> line(canvas, CORAL, 2.2f, originX, originY, originX + plotWidth, originY);
            case EAST -> line(canvas, CORAL, 2.2f, originX + plotWidth, originY, originX + plotWidth,
                    originY + plotHeight);
            case WEST -> line(canvas, CORAL, 2.2f, originX, originY, originX, originY + plotHeight);
        }
        text(canvas, BOLD, 5.6f, CORAL,
                facing.name() + "-FACING ROAD / ACCESS", panelX + 12, panelY + 11);
        var floorProgramArea = rooms.stream().mapToDouble(RoomGeometry::area).sum();
        textRight(canvas, REGULAR, 5.3f, MUTED,
                grouped(Math.round(floorProgramArea)) + " SQ FT " + floorTitle(floor).toUpperCase(Locale.ROOT)
                        + " PROGRAM AREA", panelX + panelWidth - 12,
                panelY + 11);
    }

    /**
     * The running dimension strings a builder actually sets a floor out from.
     *
     * <p>A single overall plot dimension says nothing about where a wall goes. These chains tick off
     * every distinct wall face across the floor, bottom and left, exactly as a setting-out drawing
     * does, and label each bay in feet and inches.</p>
     *
     * <p>Drawn in the gap between the building and the plot edge, and skipped entirely when the
     * setback leaves no room for it: a chain overlapping the plan would be worse than no chain.</p>
     */
    private void renderRoomDimensionChains(PDPageContentStream canvas, List<RoomGeometry> rooms,
            float originX, float originY, float plotWidth, float plotHeight, float planX, float planY,
            float scale) throws IOException {
        if (rooms.isEmpty()) return;
        var minimumGutter = 16f;

        var buildingLeft = planX + (float) rooms.stream().mapToDouble(RoomGeometry::x).min().orElse(0) * scale;
        var buildingBottom = planY + (float) rooms.stream().mapToDouble(RoomGeometry::y).min().orElse(0) * scale;
        if (buildingBottom - originY >= minimumGutter) {
            var edges = edgePositions(rooms, true, planX, scale);
            renderChain(canvas, edges, buildingBottom - 7, true, scale);
        }
        if (buildingLeft - originX >= minimumGutter) {
            var edges = edgePositions(rooms, false, planY, scale);
            renderChain(canvas, edges, buildingLeft - 7, false, scale);
        }
    }

    /** Distinct wall faces on one axis, in page points, deduplicated at drawing tolerance. */
    private List<Float> edgePositions(List<RoomGeometry> rooms, boolean horizontal, float planOrigin,
            float scale) {
        var positions = new java.util.TreeSet<Float>();
        for (var room : rooms) {
            var start = horizontal ? room.x() : room.y();
            var extent = horizontal ? room.width() : room.length();
            positions.add(planOrigin + (float) start * scale);
            positions.add(planOrigin + (float) (start + extent) * scale);
        }
        var deduplicated = new java.util.ArrayList<Float>();
        for (var position : positions) {
            // Half a point is finer than any line this drawing renders, so two faces closer than
            // that are one face as far as a reader is concerned.
            if (deduplicated.isEmpty() || position - deduplicated.getLast() > .5f) deduplicated.add(position);
        }
        return deduplicated;
    }

    private void renderChain(PDPageContentStream canvas, List<Float> edges, float offset,
            boolean horizontal, float scale) throws IOException {
        if (edges.size() < 2) return;
        var first = edges.getFirst();
        var last = edges.getLast();
        if (horizontal) line(canvas, MUTED, .4f, first, offset, last, offset);
        else line(canvas, MUTED, .4f, offset, first, offset, last);
        for (var edge : edges) {
            if (horizontal) line(canvas, MUTED, .4f, edge, offset - 2.5f, edge, offset + 2.5f);
            else line(canvas, MUTED, .4f, offset - 2.5f, edge, offset + 2.5f, edge);
        }
        for (int index = 0; index < edges.size() - 1; index++) {
            var from = edges.get(index);
            var to = edges.get(index + 1);
            var label = feetInches((to - from) / scale);
            var span = to - from;
            // A bay narrower than its own label would collide with its neighbours; the tick marks
            // still record the wall face, which is the part that matters for setting out.
            if (textWidth(REGULAR, 4.4f, label) > span - 1.5f) continue;
            if (horizontal) textCentered(canvas, REGULAR, 4.4f, MUTED, label, (from + to) / 2, offset - 5.6f);
            else textRotated(canvas, REGULAR, 4.4f, MUTED, label, offset - 1.4f, (from + to) / 2, 90);
        }
    }

    private void dimensionHorizontal(PDPageContentStream canvas, float fromX, float toX, float y, String label)
            throws IOException {
        line(canvas, MUTED, .45f, fromX, y, toX, y);
        line(canvas, MUTED, .45f, fromX, y - 3, fromX, y + 3);
        line(canvas, MUTED, .45f, toX, y - 3, toX, y + 3);
        fill(canvas, Color.WHITE, (fromX + toX) / 2 - 31, y - 3, 62, 7);
        textCentered(canvas, REGULAR, 5.2f, MUTED, label, (fromX + toX) / 2, y - 1);
    }

    private void dimensionVertical(PDPageContentStream canvas, float x, float fromY, float toY, String label)
            throws IOException {
        line(canvas, MUTED, .45f, x, fromY, x, toY);
        line(canvas, MUTED, .45f, x - 3, fromY, x + 3, fromY);
        line(canvas, MUTED, .45f, x - 3, toY, x + 3, toY);
        textRotated(canvas, REGULAR, 5.2f, MUTED, label, x - 4, (fromY + toY) / 2, 90);
    }

    private void renderCompactSummary(PDPageContentStream canvas, ProjectSummary project, DrawingCandidate drawing,
            String floor, int sheetNumber, int sheetCount,
            float x, float y, float width, float height) throws IOException {
        fill(canvas, Color.WHITE, x, y, width, height);
        stroke(canvas, LINE, .7f, x, y, width, height);
        var provenanceHeight = 20f;
        var firstWidth = 225f;
        var secondWidth = 132f;
        line(canvas, LINE, .55f, x, y + provenanceHeight, x + width, y + provenanceHeight);
        line(canvas, LINE, .55f, x + firstWidth, y + provenanceHeight, x + firstWidth, y + height);
        line(canvas, LINE, .55f, x + firstWidth + secondWidth, y + provenanceHeight,
                x + firstWidth + secondWidth, y + height);

        var rooms = roomsForFloor(drawing, floor);
        var bedrooms = rooms.stream().filter(room -> room.type().contains("BEDROOM")).count();
        var bathrooms = rooms.stream().filter(room -> room.type().contains("BATH")
                || room.type().contains("TOILET")).count();
        var doors = openingsForFloor(drawing.geometry().doors(), floor).size();
        var windows = openingsForFloor(drawing.geometry().windows(), floor).size();
        var versions = versions(drawing);
        var balconies = drawing.geometry().rooms().stream()
                .filter(room -> "BALCONY".equals(room.type())).count();
        summaryCell(canvas, "PLAN HIGHLIGHTS",
                bedrooms + " bedroom" + (bedrooms == 1 ? "" : "s") + " | " + bathrooms + " bathroom"
                        + (bathrooms == 1 ? "" : "s"),
                rooms.size() + " spaces | " + doors + " doors | " + windows + " windows",
                x, y + provenanceHeight,
                firstWidth, height - provenanceHeight);
        summaryCell(canvas, "EST. BUILD COST", lakhRange(drawing),
                titleCase(versions.getOrDefault("liftProvision", "NONE")) + " | " + balconies
                        + " balcon" + (balconies == 1 ? "y" : "ies"),
                x + firstWidth, y + provenanceHeight, secondWidth, height - provenanceHeight);
        summaryCell(canvas, "ORIENTATION", titleCase(drawingFacing(project, drawing).name()) + " facing",
                floorTitle(floor) + " | Sheet " + sheetNumber + " of " + sheetCount,
                x + firstWidth + secondWidth, y + provenanceHeight,
                width - firstWidth - secondWidth, height - provenanceHeight);

        fill(canvas, PAPER, x + 1, y + 1, width - 2, provenanceHeight - 1);
        text(canvas, BOLD, 4.8f, CORAL, "SERVER VECTOR RENDER", x + 9, y + 7);
        var provenance = versions.getOrDefault("parameterProvider", "DETERMINISTIC") + " parameters"
                + " | " + versions.getOrDefault("parameterModel", "avas-parameter-rules")
                + " | fallback " + versions.getOrDefault("parameterFallback", "false")
                + " | " + versions.getOrDefault("generator", "Not recorded");
        text(canvas, REGULAR, 4.8f, MUTED, provenance, x + 78, y + 7);
    }

    private void summaryCell(PDPageContentStream canvas, String heading, String value, String detail,
            float x, float y, float width, float height) throws IOException {
        text(canvas, BOLD, 5.2f, CORAL, heading, x + 10, y + height - 13);
        text(canvas, BOLD, 7.2f, INK, fitWidth(value, BOLD, 7.2f, width - 20), x + 10, y + height - 26);
        text(canvas, REGULAR, 5.2f, MUTED, fitWidth(detail, REGULAR, 5.2f, width - 20),
                x + 10, y + height - 36);
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
        cursor = keyValue(canvas, "Plot area", grouped(Math.round(
                drawing.geometry().plotWidth() * drawing.geometry().plotLength())) + " sq ft",
                x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Built-up", grouped(drawing.builtUpArea()) + " sq ft", x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Plot", oneDecimal(drawing.geometry().plotWidth()) + " x "
                + oneDecimal(drawing.geometry().plotLength()) + " ft", x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Floors requested", String.valueOf(requestedFloorCount(project, drawing)),
                x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Geometry floors", geometryFloors(drawing), x + 10, cursor, width - 20);
        keyValue(canvas, "Orientation", titleCase(drawingFacing(project, drawing).name()), x + 10, cursor, width - 20);
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
        cursor = keyValue(canvas, "Parameter provider", versions.getOrDefault("parameterProvider", "DETERMINISTIC"), x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Parameter model", versions.getOrDefault("parameterModel", "avas-parameter-rules"), x + 10, cursor, width - 20);
        cursor = keyValue(canvas, "Parameter fallback", versions.getOrDefault("parameterFallback", "false"), x + 10, cursor, width - 20);
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

    private void renderDisclaimer(PDPageContentStream canvas, ProjectSummary project, DrawingCandidate drawing,
            String floor, int sheetNumber, int sheetCount) throws IOException {
        fill(canvas, INK, 36, 47, 523, 45);
        text(canvas, BOLD, 6.3f, new Color(235, 193, 137), "AVAS CONCEPTUAL PLAN", 49, 78);
        var warning = "This plan is generated for planning and estimation. It must be reviewed by a qualified "
                + "architect and structural engineer before construction.";
        var lines = wrap(warning, REGULAR, 5.8f, 498);
        var y = 66f;
        for (var value : lines) { text(canvas, REGULAR, 5.8f, Color.WHITE, value, 49, y); y -= 8; }
        var reviewNote = legacyIncomplete(project, drawing)
                ? "Legacy incomplete floor set: regenerate this concept to create every requested floor."
                : firstOrDefault(drawing.softRecommendations(), "Professional review is required.");
        text(canvas, REGULAR, 4.7f, new Color(205, 197, 185),
                "Review: " + fitWidth(reviewNote, REGULAR, 4.7f, 360), 49, 54);
        textRight(canvas, BOLD, 4.7f, new Color(235, 193, 137),
                "WARNINGS " + sizeOf(drawing.softRecommendations()) + " | HARD ERRORS "
                        + sizeOf(drawing.hardViolations()) + " | REVIEW REQUIRED", 546, 54);

        text(canvas, BOLD, 7, INK, "AVAS", 36, 28);
        text(canvas, REGULAR, 5.3f, MUTED,
                safe(project.projectCode() + "  |  Drawing v" + drawing.version() + "  |  "
                        + titleCase(drawing.strategy()) + "  |  " + floorTitle(floor)), 78, 28);
        textRight(canvas, REGULAR, 5.3f, MUTED,
                "Sheet " + sheetNumber + " of " + sheetCount + " | Validate before building.", 559, 28);
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

    private void circle(PDPageContentStream canvas, Color color, float lineWidth,
            float centerX, float centerY, float radius) throws IOException {
        var control = radius * .55228475f;
        canvas.setStrokingColor(color);
        canvas.setLineWidth(lineWidth);
        canvas.moveTo(centerX + radius, centerY);
        canvas.curveTo(centerX + radius, centerY + control, centerX + control, centerY + radius,
                centerX, centerY + radius);
        canvas.curveTo(centerX - control, centerY + radius, centerX - radius, centerY + control,
                centerX - radius, centerY);
        canvas.curveTo(centerX - radius, centerY - control, centerX - control, centerY - radius,
                centerX, centerY - radius);
        canvas.curveTo(centerX + control, centerY - radius, centerX + radius, centerY - control,
                centerX + radius, centerY);
        canvas.closePath();
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

    private void textRotated(PDPageContentStream canvas, PDFont font, float size, Color color,
            String value, float x, float centerY, float degrees) throws IOException {
        var safe = safe(value);
        canvas.beginText();
        canvas.setFont(font, size);
        canvas.setNonStrokingColor(color);
        canvas.setTextMatrix(Matrix.getRotateInstance((float) Math.toRadians(degrees), x, centerY));
        canvas.newLineAtOffset(-textWidth(font, size, safe) / 2, 0);
        canvas.showText(safe);
        canvas.endText();
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

    /**
     * Feet and inches, the way a room is dimensioned on a construction drawing.
     *
     * <p>Decimal feet are a planning convenience; nobody sets out a wall at 12.54 ft. Rounding to
     * the nearest inch carries into the next foot at twelve, so 11.99 ft reads 12'-0" rather than
     * the 11'-12" a naive truncation produces.</p>
     */
    private String feetInches(double value) {
        var totalInches = (long) Math.round(Math.abs(value) * 12);
        return (totalInches / 12) + "'-" + (totalInches % 12) + "\"";
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

    private List<String> floorKeys(DrawingCandidate drawing) {
        return drawing.geometry().rooms().stream()
                .map(room -> normalizedFloor(room.floor()))
                .distinct()
                .sorted((left, right) -> Integer.compare(floorOrder(left), floorOrder(right)))
                .toList();
    }

    private List<String> expectedFloorKeys(int floorCount) {
        var floors = List.of("GROUND", "FIRST", "SECOND");
        return floors.subList(0, Math.max(0, Math.min(floorCount, floors.size())));
    }

    private List<RoomGeometry> roomsForFloor(DrawingCandidate drawing, String floor) {
        var normalized = normalizedFloor(floor);
        return drawing.geometry().rooms().stream()
                .filter(room -> normalizedFloor(room.floor()).equals(normalized))
                .toList();
    }

    private List<Map<String, Object>> openingsForFloor(List<Map<String, Object>> openings, String floor) {
        if (openings == null) return List.of();
        var normalized = normalizedFloor(floor);
        return openings.stream()
                .filter(opening -> normalizedFloor(opening.get("floor") == null
                        ? null : opening.get("floor").toString()).equals(normalized))
                .toList();
    }

    private String normalizedFloor(String floor) {
        if (floor == null || floor.isBlank()) return "GROUND";
        var normalized = floor.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "GROUND_FLOOR", "G", "0" -> "GROUND";
            case "FIRST_FLOOR", "F1", "1" -> "FIRST";
            case "SECOND_FLOOR", "F2", "2" -> "SECOND";
            default -> normalized;
        };
    }

    private int floorOrder(String floor) {
        return switch (normalizedFloor(floor)) {
            case "GROUND" -> 0;
            case "FIRST" -> 1;
            case "SECOND" -> 2;
            default -> 99;
        };
    }

    private String floorTitle(String floor) {
        return titleCase(normalizedFloor(floor)) + " Floor";
    }

    private boolean isGroundFloor(String floor) {
        return "GROUND".equals(normalizedFloor(floor));
    }

    private boolean legacyIncomplete(ProjectSummary project, DrawingCandidate drawing) {
        return !multiFloorSchema(drawing)
                && !floorKeys(drawing).equals(expectedFloorKeys(requestedFloorCount(project, drawing)));
    }

    /** True when the document carries a full room, door and window set for every requested floor. */
    private boolean multiFloorSchema(DrawingCandidate drawing) {
        var schema = versions(drawing).get("geometrySchemaVersion");
        return schema != null && GeometryEngine.MULTI_FLOOR_SCHEMAS.contains(schema);
    }

    private int requestedFloorCount(ProjectSummary project, DrawingCandidate drawing) {
        var frozen = versions(drawing).get("requestedFloors");
        if (frozen != null) {
            try {
                var parsed = Integer.parseInt(frozen);
                if (parsed >= 1 && parsed <= 3) return parsed;
            } catch (NumberFormatException ignored) {
                // Legacy drawings fall back to the project details available at render time.
            }
        }
        return project.details().floors();
    }

    private Facing drawingFacing(ProjectSummary project, DrawingCandidate drawing) {
        var frozen = versions(drawing).get("roadFacing");
        if (frozen != null) {
            try {
                return Facing.valueOf(frozen.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Legacy drawings fall back to the project details available at render time.
            }
        }
        return project.details().roadFacing();
    }

    private String safe(String value) {
        if (value == null) return "";
        return value.replace('\u2013', '-').replace('\u2014', '-').replace('\u00d7', 'x')
                .replace('\u2018', '\'').replace('\u2019', '\'').replace('\u201c', '"').replace('\u201d', '"')
                .replace("\u20b9", "INR ").replaceAll("[^\\x20-\\x7E]", "?");
    }
}

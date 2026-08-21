package com.avas.platform.project;

import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one sheet a customer actually looks at: every storey of the home side by side on one page.
 *
 * <p>The floor set this renderer sits in front of is a working document — one A4 per storey, at a
 * stated scale, with dimension chains and door marks a builder sets out from. It is the right
 * drawing for the person building the house and the wrong one for the family deciding whether to.
 * They cannot hold two pages side by side, so they cannot see that the stair lands where they left
 * it or that the master sits over the living room, and nothing on those pages tells them what the
 * home contains without counting rectangles.</p>
 *
 * <p>So this sheet answers the other question. Both plates at one scale on one landscape page, each
 * space filled by what it is for, furnished at the size the furniture really is, and read alongside
 * the plot's own dimensions, a schedule of what the home holds, and the staircase it is planned
 * around. Everything on it is measured from the persisted geometry — nothing here is decoration
 * that could disagree with the drawing it decorates.</p>
 *
 * <p>Conceptual throughout. Wall thicknesses are drawn as a graphic convention rather than a
 * construction detail, and every statutory dimension remains a licensed professional's work.</p>
 */
final class LayoutSheetRenderer {
    /** Landscape A3. Two plates at a readable scale need the width; A4 landscape does not have it. */
    static final PDRectangle SHEET = new PDRectangle(PDRectangle.A3.getHeight(), PDRectangle.A3.getWidth());

    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    // Paper and ink.
    private static final Color PAPER = new Color(249, 246, 240);
    private static final Color INK = new Color(38, 36, 33);
    private static final Color MUTED = new Color(122, 114, 102);
    private static final Color HAIRLINE = new Color(214, 206, 194);
    private static final Color WALL = new Color(38, 36, 34);

    // Zone fills. A reader should be able to tell a bedroom from a bathroom without reading a word.
    private static final Color ZONE_PRIVATE = new Color(238, 227, 208);
    private static final Color ZONE_PUBLIC = new Color(233, 226, 213);
    private static final Color ZONE_SERVICE = new Color(224, 219, 209);
    private static final Color ZONE_WET = new Color(190, 214, 228);
    private static final Color ZONE_CIRCULATION = new Color(228, 224, 216);
    private static final Color ZONE_OUTDOOR = new Color(213, 218, 219);
    private static final Color ZONE_DECK = new Color(150, 106, 72);
    private static final Color ZONE_PAVING = new Color(219, 216, 210);

    // Site.
    private static final Color GARDEN = new Color(200, 219, 176);
    private static final Color GARDEN_EDGE = new Color(150, 176, 124);
    private static final Color FOLIAGE = new Color(122, 158, 100);
    private static final Color FOLIAGE_DARK = new Color(94, 130, 78);

    // Contents.
    private static final Color FURNITURE = new Color(122, 96, 68);
    private static final Color FURNITURE_SOFT = new Color(203, 192, 175);
    private static final Color FIXTURE = new Color(96, 122, 138);
    private static final Color COUNTER = new Color(70, 68, 64);
    private static final Color CAR = new Color(246, 246, 246);
    private static final Color CAR_EDGE = new Color(120, 120, 122);
    private static final Color WINDOW = new Color(70, 116, 146);
    private static final Color ACCENT = new Color(170, 116, 74);

    private final ProjectSummary project;
    private final DrawingCandidate drawing;
    private final GeometryDocument geometry;
    private final HomeParameters parameters;
    private final SpecificationTier tier;
    private final List<String> floors;

    LayoutSheetRenderer(ProjectSummary project, DrawingCandidate drawing, List<String> floors) {
        this.project = project;
        this.drawing = drawing;
        this.geometry = drawing.geometry();
        this.parameters = project.details().parameters();
        this.tier = SpecificationTier.of(versions().getOrDefault("finishTier",
                project.details().category().name()));
        this.floors = List.copyOf(floors);
    }

    // -------------------------------------------------------------------------------------------
    // Sheet
    // -------------------------------------------------------------------------------------------

    void render(PDPageContentStream canvas) throws IOException {
        var width = SHEET.getWidth();
        var height = SHEET.getHeight();
        fill(canvas, PAPER, 0, 0, width, height);

        renderTitleBlock(canvas, width, height);
        renderPlates(canvas);
        renderPanels(canvas);
        renderFooter(canvas, width);
    }

    private void renderTitleBlock(PDPageContentStream canvas, float width, float height) throws IOException {
        textCentered(canvas, BOLD, 25, INK, homeTypeTitle(), width / 2, height - 44);
        textCentered(canvas, REGULAR, 8.6f, MUTED, subtitle(), width / 2, height - 62);
        line(canvas, HAIRLINE, .8f, 34, height - 76, width - 34, height - 76);
    }

    /** "DUPLEX HOUSE LAYOUT" — the home the customer chose, not a generic heading. */
    private String homeTypeTitle() {
        return switch (parameters.homeType()) {
            case "BUNGALOW" -> "BUNGALOW HOUSE LAYOUT";
            case "MULTI_STOREY" -> "MULTI-STOREY HOUSE LAYOUT";
            default -> "DUPLEX HOUSE LAYOUT";
        };
    }

    /** The plot, the storeys, the stair and the parking, exactly as the reference sheet reads. */
    private String subtitle() {
        var parts = new ArrayList<String>();
        parts.add(feet(geometry.plotWidth()) + " (" + project.details().roadFacing().name() + " FACING) × "
                + feet(geometry.plotLength()) + " PLOT");
        parts.add(floors.size() + (floors.size() == 1 ? " FLOOR" : " FLOORS"));
        parts.add(parameters.staircaseType().replace('_', '-') + " STAIRCASE");
        var bays = parkingBays();
        if (bays > 0) parts.add(bays + " CAR PARKING");
        return String.join("   |   ", parts).toUpperCase(Locale.ROOT);
    }

    private void renderFooter(PDPageContentStream canvas, float width) throws IOException {
        var boxHeight = 46f;
        var boxWidth = 700f;
        card(canvas, 34, 34, boxWidth, boxHeight);
        text(canvas, BOLD, 7.4f, INK, "BUILT FOR COMFORT. PLANNED FOR TOMORROW.", 48, 34 + boxHeight - 16);

        var claims = featureClaims();
        var cursor = 48f;
        var available = boxWidth - 28f;
        var slot = available / Math.max(1, claims.size());
        for (var claim : claims) {
            renderFeatureChip(canvas, claim, cursor, 34 + 11);
            cursor += slot;
        }

        textRight(canvas, REGULAR, 6.6f, MUTED, "All dimensions are in feet and inches.", width - 34, 34 + 26);
        textRight(canvas, REGULAR, 6.6f, MUTED,
                "Conceptual plan — subject to professional review before construction.", width - 34, 34 + 14);
    }

    private void renderFeatureChip(PDPageContentStream canvas, String claim, float x, float y)
            throws IOException {
        stroke(canvas, HAIRLINE, .7f, x, y - 2, 13, 13);
        line(canvas, ACCENT, 1f, x + 3, y + 4, x + 6, y + 1);
        line(canvas, ACCENT, 1f, x + 6, y + 1, x + 10, y + 8);
        var lines = claim.split("\\|");
        text(canvas, BOLD, 5.8f, INK, lines[0], x + 18, y + 5);
        if (lines.length > 1) text(canvas, BOLD, 5.8f, INK, lines[1], x + 18, y - 2);
    }

    /**
     * What this sheet is entitled to claim, checked against the drawing rather than the brochure.
     *
     * <p>Every claim is a promise about the plan, so each is confirmed against the geometry or the
     * customer's own parameters before it is printed. A sheet that advertises cross ventilation on a
     * plan with windows on one wall is worse than a sheet that advertises nothing.</p>
     */
    private List<String> featureClaims() {
        var claims = new ArrayList<String>();
        if (LifestylePreferences.of(project.details().preferences()).vastuLed()
                || drawing.vastuScore() >= 85) {
            claims.add("VASTU|FRIENDLY");
        }
        if (drawing.naturalLightScore() >= 70) claims.add("NATURAL LIGHT|& VENTILATION");
        if (crossVentilated()) claims.add("CROSS|VENTILATION");
        if (!"NONE".equals(parameters.liftProvision())) {
            claims.add("FUTURE READY|(LIFT SHAFT)");
        }
        if (tier.entranceFoyer() && hasType("FOYER")) claims.add("PLANNED|ARRIVAL");
        if (floors.size() > 1 && countType("KITCHEN") > 1) claims.add("RENTAL|FLOOR READY");
        if (parameters.solarReady()) claims.add("SOLAR|READY");
        if (parameters.rainwaterHarvesting()) claims.add("RAINWATER|HARVESTING");
        if (claims.isEmpty()) claims.add("PLANNED TO|YOUR BRIEF");
        return claims.size() > 5 ? claims.subList(0, 5) : claims;
    }

    /** True when the home has openings on opposing walls, which is what moves air through it. */
    private boolean crossVentilated() {
        var sides = new java.util.HashSet<String>();
        for (var window : geometry.windows()) {
            var orientation = text(window.get("orientation"));
            if (orientation != null) sides.add(orientation);
        }
        return (sides.contains("NORTH") && sides.contains("SOUTH"))
                || (sides.contains("EAST") && sides.contains("WEST"));
    }

    // -------------------------------------------------------------------------------------------
    // Plates
    // -------------------------------------------------------------------------------------------

    private static final float PLATE_LEFT = 40f;
    private static final float PLATE_RIGHT = 928f;
    private static final float PLATE_GAP = 20f;
    private static final float PLATE_TOP = 748f;
    private static final float PLATE_BOTTOM = 96f;
    /** Left margin inside a plate for the vertical dimension line. */
    private static final float DIMENSION_MARGIN = 30f;
    /** Height under each plate for its caption. */
    private static final float CAPTION_BAND = 34f;

    /**
     * Draws every storey at one shared scale, left to right, ground first.
     *
     * <p>The scale is shared deliberately. Two plates fitted individually to their own boxes look
     * tidier and lie: a first floor smaller than the ground floor would be drawn the same size, and
     * the one thing a customer most wants from seeing both at once — whether the rooms above sit
     * over the rooms below — becomes unreadable.</p>
     */
    private void renderPlates(PDPageContentStream canvas) throws IOException {
        var count = Math.max(1, floors.size());
        var plateWidth = (PLATE_RIGHT - PLATE_LEFT - PLATE_GAP * (count - 1)) / count;
        var frameWidth = plateWidth - DIMENSION_MARGIN - 6;
        var frameHeight = PLATE_TOP - PLATE_BOTTOM - CAPTION_BAND - 22;
        var scale = Math.min(frameWidth / (float) geometry.plotWidth(),
                frameHeight / (float) geometry.plotLength());

        for (var index = 0; index < count; index++) {
            var floor = floors.get(index);
            var plateX = PLATE_LEFT + index * (plateWidth + PLATE_GAP);
            var originX = plateX + DIMENSION_MARGIN
                    + (frameWidth - (float) geometry.plotWidth() * scale) / 2;
            var originY = PLATE_BOTTOM + CAPTION_BAND
                    + (frameHeight - (float) geometry.plotLength() * scale) / 2;
            renderPlate(canvas, floor, index == 0, originX, originY, scale, plateX, plateWidth);
        }
    }

    private void renderPlate(PDPageContentStream canvas, String floor, boolean ground,
            float originX, float originY, float scale, float plateX, float plateWidth)
            throws IOException {
        var rooms = roomsOn(floor);
        if (ground) {
            renderSite(canvas, originX, originY, scale);
        } else {
            renderPlotHint(canvas, originX, originY, scale);
        }
        for (var room : rooms) {
            fillRoom(canvas, zoneOf(room.type()), room, originX, originY, scale);
        }
        renderPartitions(canvas, rooms, originX, originY, scale);
        for (var room : rooms) {
            renderContents(canvas, room, originX, originY, scale);
        }
        renderEnvelope(canvas, rooms, originX, originY, scale);
        renderOpenings(canvas, floor, originX, originY, scale);
        for (var room : rooms) {
            renderRoomLabel(canvas, room, originX, originY, scale);
        }
        if (ground) {
            renderOverallDimensions(canvas, originX, originY, scale);
            renderRoadLabel(canvas, originX, originY, scale);
        }
        textCentered(canvas, BOLD, 9.4f, INK, floorCaption(floor),
                plateX + plateWidth / 2, PLATE_BOTTOM + 8);
    }

    private String floorCaption(String floor) {
        return switch (floor) {
            case "GROUND" -> "GROUND FLOOR PLAN";
            case "FIRST" -> "FIRST FLOOR PLAN";
            case "SECOND" -> "SECOND FLOOR PLAN";
            default -> floor + " FLOOR PLAN";
        };
    }

    /** The plot, its planted ground and the cars standing on it, under the ground-floor plate. */
    private void renderSite(PDPageContentStream canvas, float originX, float originY, float scale)
            throws IOException {
        if (geometry.hasSiteContext()) {
            polygon(canvas, geometry.plotOutline(), originX, originY, scale, ZONE_PAVING, null, 0);
        } else {
            fill(canvas, ZONE_PAVING, originX, originY, (float) geometry.plotWidth() * scale,
                    (float) geometry.plotLength() * scale);
        }
        for (var element : geometry.siteElements()) {
            var x = originX + (float) element.x() * scale;
            var y = originY + (float) element.y() * scale;
            var width = (float) element.width() * scale;
            var length = (float) element.length() * scale;
            if (element.type().contains("PARKING")) {
                fill(canvas, ZONE_PAVING, x, y, width, length);
                renderParkedCars(canvas, x, y, width, length, scale);
            } else {
                fill(canvas, GARDEN, x, y, width, length);
                renderPlanting(canvas, x, y, width, length);
            }
        }
        if (geometry.hasSiteContext()) {
            polygon(canvas, geometry.plotOutline(), originX, originY, scale, null, INK, 1.5f);
        }
    }

    /** A dashed plot line under an upper storey, so the two plates read against the same ground. */
    private void renderPlotHint(PDPageContentStream canvas, float originX, float originY, float scale)
            throws IOException {
        canvas.setLineDashPattern(new float[]{2.6f, 2.6f}, 0);
        if (geometry.hasSiteContext()) {
            polygon(canvas, geometry.plotOutline(), originX, originY, scale, null, HAIRLINE, .9f);
        } else {
            stroke(canvas, HAIRLINE, .9f, originX, originY, (float) geometry.plotWidth() * scale,
                    (float) geometry.plotLength() * scale);
        }
        canvas.setLineDashPattern(new float[]{}, 0);
    }

    /**
     * Every wall of the storey: partitions between rooms, then the outer wall on top of them.
     *
     * <p>Drawn as two passes rather than one stroke per room so a shared wall is not painted twice
     * at the same weight, which is what makes a plan look like a stack of boxes instead of a
     * building.</p>
     */
    private void renderPartitions(PDPageContentStream canvas, List<RoomGeometry> rooms,
            float originX, float originY, float scale) throws IOException {
        for (var room : rooms) {
            strokeRoom(canvas, WALL, Math.max(.7f, scale * .10f), room, originX, originY, scale);
        }
    }

    private void renderEnvelope(PDPageContentStream canvas, List<RoomGeometry> rooms,
            float originX, float originY, float scale) throws IOException {
        var bounds = boundsOf(rooms);
        if (bounds == null) return;
        stroke(canvas, WALL, Math.max(1.6f, scale * .26f),
                originX + (float) bounds[0] * scale, originY + (float) bounds[1] * scale,
                (float) (bounds[2] - bounds[0]) * scale, (float) (bounds[3] - bounds[1]) * scale);
    }

    private double[] boundsOf(List<RoomGeometry> rooms) {
        if (rooms.isEmpty()) return null;
        var minX = Double.MAX_VALUE;
        var minY = Double.MAX_VALUE;
        var maxX = -Double.MAX_VALUE;
        var maxY = -Double.MAX_VALUE;
        for (var room : rooms) {
            if (RoomSpec.isOutdoor(room.type())) continue;
            minX = Math.min(minX, room.x());
            minY = Math.min(minY, room.y());
            maxX = Math.max(maxX, room.x() + room.width());
            maxY = Math.max(maxY, room.y() + room.length());
        }
        return minX > maxX ? null : new double[]{minX, minY, maxX, maxY};
    }

    // -------------------------------------------------------------------------------------------
    // Openings
    // -------------------------------------------------------------------------------------------

    private void renderOpenings(PDPageContentStream canvas, String floor, float originX, float originY,
            float scale) throws IOException {
        for (var window : geometry.windows()) {
            if (!floor.equals(text(window.get("floor")))) continue;
            var x = number(window.get("x"));
            var y = number(window.get("y"));
            var width = Math.max(2.5, number(window.get("width")));
            if (!Double.isFinite(x) || !Double.isFinite(y)) continue;
            var orientation = orientation(window.get("orientation"), "WEST");
            var horizontal = "NORTH".equals(orientation) || "SOUTH".equals(orientation);
            var thickness = Math.max(1.8f, scale * .26f);
            if (horizontal) {
                var fromX = originX + (float) (x - width / 2) * scale;
                var toX = originX + (float) (x + width / 2) * scale;
                var pointY = originY + (float) y * scale;
                line(canvas, PAPER, thickness, fromX, pointY, toX, pointY);
                line(canvas, WINDOW, .75f, fromX, pointY - thickness / 3, toX, pointY - thickness / 3);
                line(canvas, WINDOW, .75f, fromX, pointY + thickness / 3, toX, pointY + thickness / 3);
            } else {
                var pointX = originX + (float) x * scale;
                var fromY = originY + (float) (y - width / 2) * scale;
                var toY = originY + (float) (y + width / 2) * scale;
                line(canvas, PAPER, thickness, pointX, fromY, pointX, toY);
                line(canvas, WINDOW, .75f, pointX - thickness / 3, fromY, pointX - thickness / 3, toY);
                line(canvas, WINDOW, .75f, pointX + thickness / 3, fromY, pointX + thickness / 3, toY);
            }
        }
        for (var door : geometry.doors()) {
            if (!floor.equals(text(door.get("floor")))) continue;
            var x = number(door.get("x"));
            var y = number(door.get("y"));
            var width = Math.max(2.4, number(door.get("width")));
            if (!Double.isFinite(x) || !Double.isFinite(y)) continue;
            var orientation = orientation(door.get("orientation"), "SOUTH");
            renderDoor(canvas, x, y, width, orientation, originX, originY, scale);
        }
    }

    /**
     * A door as a builder's plan draws one: the leaf standing open, and the arc it sweeps.
     *
     * <p>The swing is the part that carries information — it says which way the door opens and how
     * much floor it needs to do it — so it is drawn even at small scales where the leaf itself is a
     * couple of points long.</p>
     */
    private void renderDoor(PDPageContentStream canvas, double x, double y, double width,
            String orientation, float originX, float originY, float scale) throws IOException {
        var horizontal = "NORTH".equals(orientation) || "SOUTH".equals(orientation);
        var leaf = (float) width * scale;
        var centerX = originX + (float) x * scale;
        var centerY = originY + (float) y * scale;
        var thickness = Math.max(1.8f, scale * .26f);

        // Clear the wall so the opening reads as an opening rather than a mark on a solid line.
        if (horizontal) {
            line(canvas, PAPER, thickness, centerX - leaf / 2, centerY, centerX + leaf / 2, centerY);
        } else {
            line(canvas, PAPER, thickness, centerX, centerY - leaf / 2, centerX, centerY + leaf / 2);
        }
        if (leaf < 3.5f) return;

        // Hinge on the low side, leaf standing perpendicular, arc back to the far jamb.
        float hingeX;
        float hingeY;
        float leafX;
        float leafY;
        float farX;
        float farY;
        var swing = "SOUTH".equals(orientation) || "WEST".equals(orientation) ? -1f : 1f;
        if (horizontal) {
            hingeX = centerX - leaf / 2;
            hingeY = centerY;
            leafX = hingeX;
            leafY = centerY + swing * leaf;
            farX = centerX + leaf / 2;
            farY = centerY;
        } else {
            hingeX = centerX;
            hingeY = centerY - leaf / 2;
            leafX = centerX + swing * leaf;
            leafY = hingeY;
            farX = centerX;
            farY = centerY + leaf / 2;
        }
        line(canvas, WALL, .7f, hingeX, hingeY, leafX, leafY);
        quarterArc(canvas, hingeX, hingeY, leafX, leafY, farX, farY);
    }

    /** The quarter circle a door leaf sweeps, as a Bezier through the two open positions. */
    private void quarterArc(PDPageContentStream canvas, float hingeX, float hingeY, float fromX,
            float fromY, float toX, float toY) throws IOException {
        var k = .5523f;
        canvas.setStrokingColor(MUTED);
        canvas.setLineWidth(.5f);
        canvas.moveTo(fromX, fromY);
        canvas.curveTo(fromX + (toX - hingeX) * k, fromY + (toY - hingeY) * k,
                toX + (fromX - hingeX) * k, toY + (fromY - hingeY) * k, toX, toY);
        canvas.stroke();
    }

    // -------------------------------------------------------------------------------------------
    // Room labels
    // -------------------------------------------------------------------------------------------

    private void renderRoomLabel(PDPageContentStream canvas, RoomGeometry room, float originX,
            float originY, float scale) throws IOException {
        var width = (float) room.width() * scale;
        var height = (float) room.length() * scale;
        if (width < 22 || height < 13) return;
        var centerX = originX + (float) room.x() * scale + width / 2;
        var centerY = originY + (float) room.y() * scale + height / 2;

        var name = label(room.type());
        var size = Math.min(6.4f, Math.max(4.2f, width / (name.length() * .62f)));
        if (size < 4.2f) return;
        var dimension = feetInches(room.width()) + " X " + feetInches(room.length());
        var showDimension = height >= 24 && width >= textWidth(REGULAR, size - .8f, dimension) + 4;

        if (width < textWidth(BOLD, size, name) + 3) {
            name = shorten(name);
            if (width < textWidth(BOLD, size, name) + 3) return;
        }
        var baseline = showDimension ? centerY + 1.4f : centerY - size / 3;
        textCentered(canvas, BOLD, size, INK, name, centerX, baseline);
        if (showDimension) {
            textCentered(canvas, REGULAR, size - .9f, MUTED, dimension, centerX, baseline - size - .6f);
        }
    }

    /** The name a customer reads, which is not always the name the engine uses. */
    private String label(String type) {
        return switch (type) {
            case "ATTACHED_BATHROOM" -> "TOILET";
            case "BATHROOM" -> "BATH";
            case "LIFT_SHAFT" -> "LIFT SHAFT";
            case "COURTYARD_PARKING" -> "PARKING COURT";
            case "SENIOR_BEDROOM" -> "SENIOR BEDROOM";
            case "MASTER_BEDROOM" -> "MASTER BEDROOM";
            case "DRESSING_ROOM" -> "DRESS";
            case "FAMILY_LOUNGE" -> "FAMILY LOUNGE";
            case "MULTIPURPOSE_ROOM" -> "MULTIPURPOSE";
            case "OPEN_SPACE" -> "OPEN SPACE";
            default -> type.replace('_', ' ');
        };
    }

    private String shorten(String name) {
        var space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name;
    }

    // -------------------------------------------------------------------------------------------
    // Dimensions and orientation
    // -------------------------------------------------------------------------------------------

    private void renderOverallDimensions(PDPageContentStream canvas, float originX, float originY,
            float scale) throws IOException {
        var width = (float) geometry.plotWidth() * scale;
        var length = (float) geometry.plotLength() * scale;
        dimensionHorizontal(canvas, originX, originX + width, originY + length + 14,
                feetInches(geometry.plotWidth()));
        dimensionVertical(canvas, originX - 16, originY, originY + length,
                feetInches(geometry.plotLength()));
    }

    private void dimensionHorizontal(PDPageContentStream canvas, float fromX, float toX, float y,
            String label) throws IOException {
        line(canvas, MUTED, .6f, fromX, y, toX, y);
        arrowHead(canvas, fromX, y, 1);
        arrowHead(canvas, toX, y, -1);
        var half = textWidth(REGULAR, 6.4f, label) / 2 + 3;
        var midpoint = (fromX + toX) / 2;
        fill(canvas, PAPER, midpoint - half, y - 2.6f, half * 2, 8);
        textCentered(canvas, REGULAR, 6.4f, INK, label, midpoint, y - 2);
    }

    private void dimensionVertical(PDPageContentStream canvas, float x, float fromY, float toY,
            String label) throws IOException {
        line(canvas, MUTED, .6f, x, fromY, x, toY);
        arrowHeadVertical(canvas, x, fromY, 1);
        arrowHeadVertical(canvas, x, toY, -1);
        textRotated(canvas, REGULAR, 6.4f, INK, label, x - 2.4f, (fromY + toY) / 2
                - textWidth(REGULAR, 6.4f, label) / 2);
    }

    private void arrowHead(PDPageContentStream canvas, float x, float y, float direction)
            throws IOException {
        line(canvas, MUTED, .6f, x, y, x + direction * 4.5f, y + 1.8f);
        line(canvas, MUTED, .6f, x, y, x + direction * 4.5f, y - 1.8f);
        line(canvas, MUTED, .6f, x, y - 4, x, y + 4);
    }

    private void arrowHeadVertical(PDPageContentStream canvas, float x, float y, float direction)
            throws IOException {
        line(canvas, MUTED, .6f, x, y, x + 1.8f, y + direction * 4.5f);
        line(canvas, MUTED, .6f, x, y, x - 1.8f, y + direction * 4.5f);
        line(canvas, MUTED, .6f, x - 4, y, x + 4, y);
    }

    /** The road the home is entered from, drawn on the side it is actually on. */
    private void renderRoadLabel(PDPageContentStream canvas, float originX, float originY, float scale)
            throws IOException {
        var width = (float) geometry.plotWidth() * scale;
        var length = (float) geometry.plotLength() * scale;
        var facing = project.details().roadFacing();
        var label = facing.name() + " ROAD";
        var centerX = originX + width / 2;
        var y = switch (facing) {
            case NORTH -> originY + length + 26;
            case SOUTH -> originY - 20;
            default -> originY + length / 2;
        };
        if (facing == Facing.EAST || facing == Facing.WEST) {
            var x = facing == Facing.EAST ? originX + width + 14 : originX - 22;
            textRotated(canvas, BOLD, 6.8f, INK, label, x, y - textWidth(BOLD, 6.8f, label) / 2);
            return;
        }
        textCentered(canvas, BOLD, 6.8f, INK, label, centerX, y);
        var half = textWidth(BOLD, 6.8f, label) / 2;
        line(canvas, INK, .7f, centerX - half - 26, y + 2, centerX - half - 6, y + 2);
        line(canvas, INK, .7f, centerX + half + 6, y + 2, centerX + half + 26, y + 2);
        arrowHead(canvas, centerX - half - 26, y + 2, 1);
        arrowHead(canvas, centerX + half + 26, y + 2, -1);
    }

    // -------------------------------------------------------------------------------------------
    // Side panels
    // -------------------------------------------------------------------------------------------

    private static final float PANEL_X = 950f;
    private static final float PANEL_WIDTH = 206f;

    private void renderPanels(PDPageContentStream canvas) throws IOException {
        var cursor = PLATE_TOP;
        cursor = renderPlotDetails(canvas, cursor);
        cursor = renderSummary(canvas, cursor - 18);
        cursor = renderStaircase(canvas, cursor - 18);
        renderCompass(canvas, PANEL_X + PANEL_WIDTH / 2, cursor - 62);
    }

    private float renderPlotDetails(PDPageContentStream canvas, float top) throws IOException {
        var rows = new LinkedHashMap<String, String>();
        rows.put("Plot Size", feet(geometry.plotWidth()) + " X " + feet(geometry.plotLength()));
        rows.put("Plot Area", Math.round(geometry.plotArea()) + " sq ft");
        rows.put("Built-up", drawing.builtUpArea() + " sq ft");
        rows.put("Facing", titleCase(project.details().roadFacing().name()));
        rows.put("Floors", String.valueOf(floors.size()));
        rows.put("Specification", tier.displayName());
        return renderCard(canvas, "PLOT DETAILS", top, rows, false);
    }

    private float renderSummary(PDPageContentStream canvas, float top) throws IOException {
        return renderCard(canvas, "SUMMARY", top, summaryRows(), true);
    }

    /**
     * What this home contains, counted off the drawing.
     *
     * <p>Counted rather than restated from the brief. A schedule that repeats the requirement would
     * say four bedrooms on a sheet showing three, and the sheet is the thing the customer is holding
     * — so a row only appears when the rooms behind it are actually on the plates.</p>
     */
    private Map<String, String> summaryRows() {
        var rows = new LinkedHashMap<String, String>();
        put(rows, "Bedrooms", countMatching(type -> type.endsWith("BEDROOM")));
        put(rows, "Living / Dining", countType("LIVING_ROOM") + countType("DINING"));
        put(rows, "Family Lounge", countType("FAMILY_LOUNGE"));
        put(rows, "Kitchen", countType("KITCHEN"));
        put(rows, "Foyer", countType("FOYER"));
        put(rows, "Utility", countType("UTILITY") + countType("LAUNDRY"));
        put(rows, "Toilets", countMatching(type -> type.contains("BATHROOM") || "TOILET".equals(type)));
        put(rows, "Store", countType("STORE"));
        var bays = parkingBays();
        if (bays > 0) rows.put("Parking", bays + (bays == 1 ? " Car" : " Cars"));
        put(rows, "Balcony", countType("BALCONY"));
        put(rows, "Terrace", countType("TERRACE"));
        put(rows, "Courtyard", countMatching(type -> type.contains("COURTYARD")));
        if (!"NONE".equals(parameters.liftProvision())) {
            rows.put("Lift Provision", countType("LIFT_SHAFT") > 0
                    ? titleCase(parameters.liftProvision()) : "Planned");
        }
        return rows;
    }

    private void put(Map<String, String> rows, String label, long count) {
        if (count > 0) rows.put(label, String.valueOf(count));
    }

    private float renderCard(PDPageContentStream canvas, String heading, float top,
            Map<String, String> rows, boolean icons) throws IOException {
        var rowHeight = 15.5f;
        var height = 26 + rows.size() * rowHeight + 8;
        var bottom = top - height;
        card(canvas, PANEL_X, bottom, PANEL_WIDTH, height);
        text(canvas, BOLD, 8.4f, INK, heading, PANEL_X + 14, top - 18);
        line(canvas, HAIRLINE, .6f, PANEL_X + 14, top - 25, PANEL_X + PANEL_WIDTH - 14, top - 25);
        var y = top - 25 - rowHeight + 4;
        for (var row : rows.entrySet()) {
            var textX = PANEL_X + 14;
            if (icons) {
                renderSummaryGlyph(canvas, row.getKey(), textX, y - 1);
                textX += 15;
            }
            text(canvas, REGULAR, 7f, MUTED, row.getKey(), textX, y);
            text(canvas, BOLD, 7f, INK, ":", PANEL_X + PANEL_WIDTH - 62, y);
            text(canvas, BOLD, 7f, INK, row.getValue(), PANEL_X + PANEL_WIDTH - 54, y);
            y -= rowHeight;
        }
        return bottom;
    }

    /** A tiny mark per schedule row, so the panel scans as a list of things rather than of words. */
    private void renderSummaryGlyph(PDPageContentStream canvas, String label, float x, float y)
            throws IOException {
        canvas.setStrokingColor(ACCENT);
        canvas.setLineWidth(.65f);
        switch (label) {
            case "Bedrooms" -> {
                stroke(canvas, ACCENT, .65f, x, y, 10, 6.5f);
                line(canvas, ACCENT, .55f, x, y + 4.4f, x + 10, y + 4.4f);
            }
            case "Toilets" -> {
                circle(canvas, ACCENT, .65f, x + 3.4f, y + 3.2f, 2.4f);
                line(canvas, ACCENT, .55f, x + 7f, y, x + 7f, y + 6.5f);
            }
            case "Parking" -> {
                stroke(canvas, ACCENT, .65f, x + .5f, y + 1.4f, 9, 4);
                circle(canvas, ACCENT, .5f, x + 2.6f, y + 1.2f, 1.1f);
                circle(canvas, ACCENT, .5f, x + 7.4f, y + 1.2f, 1.1f);
            }
            case "Balcony", "Terrace", "Courtyard" -> {
                stroke(canvas, ACCENT, .65f, x, y, 10, 6.5f);
                line(canvas, ACCENT, .55f, x, y + 2.2f, x + 10, y + 2.2f);
            }
            case "Lift Provision" -> {
                stroke(canvas, ACCENT, .65f, x + 1.5f, y, 7, 6.5f);
                line(canvas, ACCENT, .55f, x + 5f, y, x + 5f, y + 6.5f);
            }
            default -> {
                stroke(canvas, ACCENT, .65f, x + 1f, y + .5f, 8, 5.5f);
                line(canvas, ACCENT, .55f, x + 1f, y + 3.2f, x + 9f, y + 3.2f);
            }
        }
    }

    /**
     * The staircase the whole plan is planned around, drawn as its own small detail.
     *
     * <p>A stair is the one element a customer asks about and the one a floor plan shows worst: at
     * plate scale it is a dozen parallel lines in a rectangle. Drawn once at its own size, with the
     * landing and the direction of travel, it becomes the thing they were asking about.</p>
     */
    private float renderStaircase(PDPageContentStream canvas, float top) throws IOException {
        var height = 132f;
        var bottom = top - height;
        card(canvas, PANEL_X, bottom, PANEL_WIDTH, height);
        text(canvas, BOLD, 8.4f, INK, "STAIRCASE", PANEL_X + 14, top - 18);
        line(canvas, HAIRLINE, .6f, PANEL_X + 14, top - 25, PANEL_X + PANEL_WIDTH - 14, top - 25);

        var boxWidth = 92f;
        var boxHeight = 62f;
        var x = PANEL_X + (PANEL_WIDTH - boxWidth) / 2;
        var y = bottom + 34;
        renderStairDetail(canvas, parameters.staircaseType(), x, y, boxWidth, boxHeight);
        textCentered(canvas, BOLD, 7.2f, INK,
                parameters.staircaseType().replace('_', '-') + " STAIRCASE",
                PANEL_X + PANEL_WIDTH / 2, bottom + 16);
        return bottom;
    }

    private void renderStairDetail(PDPageContentStream canvas, String type, float x, float y,
            float width, float height) throws IOException {
        fill(canvas, PAPER, x, y, width, height);
        stroke(canvas, WALL, 1f, x, y, width, height);
        var treads = 7;
        switch (type) {
            case "STRAIGHT" -> {
                var step = height / treads;
                for (var index = 1; index < treads; index++) {
                    line(canvas, MUTED, .55f, x, y + index * step, x + width, y + index * step);
                }
                arrowUp(canvas, x + width / 2, y + 6, y + height - 6);
            }
            case "L_SHAPED", "U_SHAPED" -> {
                var half = width / 2;
                var step = (height - 12) / treads;
                for (var index = 1; index < treads; index++) {
                    line(canvas, MUTED, .55f, x, y + index * step, x + half, y + index * step);
                }
                line(canvas, WALL, .8f, x, y + height - 12, x + width, y + height - 12);
                for (var index = 1; index < 4; index++) {
                    var stepX = x + half + index * (half / 4);
                    line(canvas, MUTED, .55f, stepX, y + height - 12, stepX, y + height);
                }
                arrowUp(canvas, x + half / 2, y + 5, y + height - 16);
            }
            default -> {
                // Dog-legged: two flights either side of a half landing, turning back on themselves.
                var half = width / 2;
                var step = height / treads;
                for (var index = 1; index < treads; index++) {
                    line(canvas, MUTED, .55f, x, y + index * step, x + half - 2, y + index * step);
                    line(canvas, MUTED, .55f, x + half + 2, y + index * step, x + width,
                            y + index * step);
                }
                line(canvas, WALL, .9f, x + half, y, x + half, y + height);
                arrowUp(canvas, x + half / 2, y + 5, y + height - 5);
                arrowDown(canvas, x + half + half / 2, y + 5, y + height - 5);
            }
        }
    }

    private void arrowUp(PDPageContentStream canvas, float x, float fromY, float toY) throws IOException {
        line(canvas, ACCENT, .8f, x, fromY, x, toY);
        line(canvas, ACCENT, .8f, x, toY, x - 2.4f, toY - 3.4f);
        line(canvas, ACCENT, .8f, x, toY, x + 2.4f, toY - 3.4f);
    }

    private void arrowDown(PDPageContentStream canvas, float x, float fromY, float toY) throws IOException {
        line(canvas, MUTED, .8f, x, toY, x, fromY);
        line(canvas, MUTED, .8f, x, fromY, x - 2.4f, fromY + 3.4f);
        line(canvas, MUTED, .8f, x, fromY, x + 2.4f, fromY + 3.4f);
    }

    /** North on the sheet is north on the plan: {@code +y} of the planning grid, always. */
    private void renderCompass(PDPageContentStream canvas, float centerX, float centerY)
            throws IOException {
        var radius = 21f;
        circle(canvas, HAIRLINE, .7f, centerX, centerY, radius);
        for (var index = 0; index < 4; index++) {
            var angle = Math.PI / 2 * index;
            var tipX = centerX + (float) (Math.cos(angle) * radius);
            var tipY = centerY + (float) (Math.sin(angle) * radius);
            var leftX = centerX + (float) (Math.cos(angle + Math.PI * .75) * radius * .34);
            var leftY = centerY + (float) (Math.sin(angle + Math.PI * .75) * radius * .34);
            var rightX = centerX + (float) (Math.cos(angle - Math.PI * .75) * radius * .34);
            var rightY = centerY + (float) (Math.sin(angle - Math.PI * .75) * radius * .34);
            canvas.setNonStrokingColor(index == 1 ? ACCENT : INK);
            canvas.moveTo(tipX, tipY);
            canvas.lineTo(leftX, leftY);
            canvas.lineTo(rightX, rightY);
            canvas.closePath();
            canvas.fill();
        }
        textCentered(canvas, BOLD, 7f, INK, "N", centerX, centerY + radius + 5);
        textCentered(canvas, BOLD, 6.2f, MUTED, "S", centerX, centerY - radius - 10);
        textCentered(canvas, BOLD, 6.2f, MUTED, "E", centerX + radius + 7, centerY - 2.4f);
        textCentered(canvas, BOLD, 6.2f, MUTED, "W", centerX - radius - 7, centerY - 2.4f);
    }

    // -------------------------------------------------------------------------------------------
    // Room contents
    // -------------------------------------------------------------------------------------------

    /**
     * What stands in the room, at the size it really is.
     *
     * <p>Furniture is drawn to plan scale rather than to a fixed number of points, which is the only
     * way it tells the truth: a double bed is six and a half feet by five whatever the plate is, so
     * a bedroom the bed only just fits into looks like one. That is the check a family actually
     * performs on a floor plan, and the reason these are not decorative glyphs.</p>
     */
    private void renderContents(PDPageContentStream canvas, RoomGeometry room, float originX,
            float originY, float scale) throws IOException {
        var x = originX + (float) room.x() * scale;
        var y = originY + (float) room.y() * scale;
        var width = (float) room.width() * scale;
        var height = (float) room.length() * scale;
        if (width < 12 || height < 12) return;
        var landscape = room.width() >= room.length();
        var type = room.type();

        if (type.endsWith("BEDROOM")) {
            renderBed(canvas, x, y, width, height, scale, "MASTER_BEDROOM".equals(type));
        } else if ("LIVING_ROOM".equals(type) || "FAMILY_LOUNGE".equals(type)
                || "MULTIPURPOSE_ROOM".equals(type)) {
            renderLounge(canvas, x, y, width, height, scale, landscape);
        } else if ("DINING".equals(type)) {
            renderDining(canvas, x, y, width, height, scale, landscape);
        } else if ("KITCHEN".equals(type)) {
            renderKitchen(canvas, x, y, width, height, scale);
        } else if (type.contains("BATHROOM") || "TOILET".equals(type)) {
            renderSanitary(canvas, x, y, width, height, scale, landscape);
        } else if ("STAIRCASE".equals(type)) {
            renderStairFlight(canvas, x, y, width, height, room);
        } else if ("LIFT_SHAFT".equals(type)) {
            renderLift(canvas, x, y, width, height);
        } else if (type.contains("PARKING")) {
            renderParkedCars(canvas, x, y, width, height, scale);
        } else if ("COURTYARD".equals(type)) {
            renderDeck(canvas, x, y, width, height, scale);
        } else if ("TERRACE".equals(type) || "BALCONY".equals(type) || "VERANDAH".equals(type)
                || "PORCH".equals(type)) {
            renderOutdoorSeating(canvas, x, y, width, height, scale);
        } else if ("STUDY".equals(type) || "HOME_OFFICE".equals(type)) {
            renderDesk(canvas, x, y, width, height, scale, landscape);
        } else if ("DRESSING_ROOM".equals(type) || "STORE".equals(type)) {
            renderShelving(canvas, x, y, width, height, scale, landscape);
        } else if ("UTILITY".equals(type) || "LAUNDRY".equals(type)) {
            renderAppliances(canvas, x, y, width, height, scale);
        } else if ("PRAYER_ROOM".equals(type)) {
            renderShrine(canvas, x, y, width, height, scale);
        } else if ("FOYER".equals(type)) {
            renderFoyer(canvas, x, y, width, height, scale);
        }
    }

    /** A double bed with its pillows, side tables and the wardrobe against the opposite wall. */
    private void renderBed(PDPageContentStream canvas, float x, float y, float width, float height,
            float scale, boolean master) throws IOException {
        var bedWidth = (master ? 6.2f : 5.2f) * scale;
        var bedLength = (master ? 6.8f : 6.4f) * scale;
        if (bedWidth > width - 6 || bedLength > height - 8) {
            bedWidth = Math.min(bedWidth, width - 6);
            bedLength = Math.min(bedLength, height - 8);
        }
        if (bedWidth < 8 || bedLength < 8) return;
        var bedX = x + (width - bedWidth) / 2;
        var bedY = y + height - bedLength - Math.min(4f, height * .06f);
        fill(canvas, FURNITURE_SOFT, bedX, bedY, bedWidth, bedLength);
        stroke(canvas, FURNITURE, .6f, bedX, bedY, bedWidth, bedLength);
        // Headboard and pillows at the head of the bed.
        fill(canvas, FURNITURE, bedX, bedY + bedLength - bedLength * .07f, bedWidth, bedLength * .07f);
        var pillowWidth = bedWidth * .42f;
        var pillowHeight = Math.min(bedLength * .17f, 6f);
        stroke(canvas, FURNITURE, .45f, bedX + bedWidth * .04f,
                bedY + bedLength - pillowHeight - bedLength * .09f, pillowWidth, pillowHeight);
        stroke(canvas, FURNITURE, .45f, bedX + bedWidth * .54f,
                bedY + bedLength - pillowHeight - bedLength * .09f, pillowWidth, pillowHeight);
        // Turned-back sheet, which is what makes the rectangle read as a bed at a glance.
        line(canvas, FURNITURE, .45f, bedX, bedY + bedLength * .34f, bedX + bedWidth,
                bedY + bedLength * .34f);
        // Side tables, only where the room has the clearance for them.
        var tableSize = Math.min(1.6f * scale, bedWidth * .22f);
        if (bedX - tableSize - 2 > x + 1) {
            stroke(canvas, FURNITURE, .5f, bedX - tableSize - 1.5f,
                    bedY + bedLength - tableSize, tableSize, tableSize);
        }
        if (bedX + bedWidth + tableSize + 2 < x + width - 1) {
            stroke(canvas, FURNITURE, .5f, bedX + bedWidth + 1.5f,
                    bedY + bedLength - tableSize, tableSize, tableSize);
        }
        // Wardrobe on the foot wall.
        var wardrobeDepth = Math.min(2f * scale, height * .1f);
        if (bedY - wardrobeDepth - 2 > y) {
            var wardrobeWidth = Math.min(width * .55f, 6f * scale);
            var wardrobeX = x + (width - wardrobeWidth) / 2;
            fill(canvas, FURNITURE_SOFT, wardrobeX, y + 1.5f, wardrobeWidth, wardrobeDepth);
            stroke(canvas, FURNITURE, .5f, wardrobeX, y + 1.5f, wardrobeWidth, wardrobeDepth);
            line(canvas, FURNITURE, .4f, wardrobeX + wardrobeWidth / 2, y + 1.5f,
                    wardrobeX + wardrobeWidth / 2, y + 1.5f + wardrobeDepth);
        }
    }

    /** A sofa run facing a coffee table, with a second seat where the room can take one. */
    private void renderLounge(PDPageContentStream canvas, float x, float y, float width, float height,
            float scale, boolean landscape) throws IOException {
        var sofaLength = Math.min(landscape ? width * .52f : width * .72f, 7f * scale);
        var sofaDepth = Math.min(2.9f * scale, height * .22f);
        if (sofaLength < 10 || sofaDepth < 4) return;
        var sofaX = x + (width - sofaLength) / 2;
        var sofaY = y + height - sofaDepth - Math.min(4f, height * .08f);
        fill(canvas, FURNITURE_SOFT, sofaX, sofaY, sofaLength, sofaDepth);
        stroke(canvas, FURNITURE, .55f, sofaX, sofaY, sofaLength, sofaDepth);
        line(canvas, FURNITURE, .45f, sofaX, sofaY + sofaDepth * .72f, sofaX + sofaLength,
                sofaY + sofaDepth * .72f);
        for (var index = 1; index < 3; index++) {
            var cushionX = sofaX + sofaLength * index / 3;
            line(canvas, FURNITURE, .4f, cushionX, sofaY, cushionX, sofaY + sofaDepth * .72f);
        }
        // Coffee table centred in the room, which is what fixes the seating as a sitting area.
        var tableWidth = Math.min(sofaLength * .48f, 3.6f * scale);
        var tableDepth = Math.min(1.9f * scale, height * .14f);
        var tableY = sofaY - tableDepth - Math.min(2.6f * scale, height * .1f);
        if (tableY > y + 2 && tableWidth > 5) {
            fill(canvas, FURNITURE, x + (width - tableWidth) / 2, tableY, tableWidth, tableDepth);
        }
        // Facing seat, only where the depth leaves room to walk between them.
        var facingY = tableY - tableDepth - Math.min(2.2f * scale, height * .09f);
        if (facingY > y + 2 && height > 9 * scale) {
            var seatLength = sofaLength * .55f;
            stroke(canvas, FURNITURE, .5f, x + (width - seatLength) / 2, facingY - sofaDepth * .7f,
                    seatLength, sofaDepth * .7f);
        }
    }

    /** A dining table with a chair to every side the table has room for. */
    private void renderDining(PDPageContentStream canvas, float x, float y, float width, float height,
            float scale, boolean landscape) throws IOException {
        var tableLength = Math.min(landscape ? width * .46f : width * .62f, 5.6f * scale);
        var tableDepth = Math.min(landscape ? height * .46f : height * .3f, 3.2f * scale);
        if (tableLength < 9 || tableDepth < 6) return;
        var tableX = x + (width - tableLength) / 2;
        var tableY = y + (height - tableDepth) / 2;
        fill(canvas, FURNITURE, tableX, tableY, tableLength, tableDepth);
        stroke(canvas, new Color(90, 70, 50), .5f, tableX, tableY, tableLength, tableDepth);

        var chair = Math.min(1.5f * scale, tableDepth * .5f);
        var seats = Math.max(2, Math.min(3, (int) (tableLength / (chair * 1.9f))));
        for (var index = 0; index < seats; index++) {
            var chairX = tableX + tableLength * (index + .5f) / seats - chair / 2;
            stroke(canvas, FURNITURE, .45f, chairX, tableY - chair - 1.4f, chair, chair);
            stroke(canvas, FURNITURE, .45f, chairX, tableY + tableDepth + 1.4f, chair, chair);
        }
        if (tableX - chair - 2 > x) {
            stroke(canvas, FURNITURE, .45f, tableX - chair - 1.4f,
                    tableY + (tableDepth - chair) / 2, chair, chair);
            stroke(canvas, FURNITURE, .45f, tableX + tableLength + 1.4f,
                    tableY + (tableDepth - chair) / 2, chair, chair);
        }
    }

    /** An L of counter with the sink under the window and the hob on the return. */
    private void renderKitchen(PDPageContentStream canvas, float x, float y, float width, float height,
            float scale) throws IOException {
        var depth = Math.min(2.1f * scale, Math.min(width, height) * .26f);
        if (depth < 3) return;
        // Counter along the top and down the right, which is the L nearly every kitchen is built as.
        fill(canvas, COUNTER, x + 1, y + height - depth - 1, width - 2, depth);
        fill(canvas, COUNTER, x + width - depth - 1, y + 1, depth, height - depth - 2);

        var sinkWidth = Math.min(width * .3f, 2.6f * scale);
        var sinkX = x + width * .2f;
        var sinkY = y + height - depth - 1 + depth * .18f;
        stroke(canvas, PAPER, .7f, sinkX, sinkY, sinkWidth, depth * .64f);
        line(canvas, PAPER, .6f, sinkX + sinkWidth / 2, sinkY, sinkX + sinkWidth / 2, sinkY + depth * .64f);

        // Hob, drawn as its four burners.
        var hobY = y + height * .42f;
        var hobX = x + width - depth - 1 + depth * .2f;
        var burner = Math.max(1.1f, depth * .16f);
        for (var row = 0; row < 2; row++) {
            for (var column = 0; column < 2; column++) {
                circle(canvas, PAPER, .55f, hobX + burner * (1 + column * 2.2f),
                        hobY + burner * (1 + row * 2.2f), burner);
            }
        }
    }

    /** WC, basin and shower — the three fittings that make a rectangle read as a bathroom. */
    private void renderSanitary(PDPageContentStream canvas, float x, float y, float width, float height,
            float scale, boolean landscape) throws IOException {
        var unit = Math.min(scale, Math.min(width, height) * .22f);
        if (unit < 1.6f) return;
        // WC against the far wall.
        var wcWidth = unit * 1.5f;
        var wcDepth = unit * 2.1f;
        var wcX = x + width - wcWidth - unit * .6f;
        var wcY = y + height - wcDepth - unit * .6f;
        fill(canvas, PAPER, wcX, wcY, wcWidth, wcDepth);
        stroke(canvas, FIXTURE, .55f, wcX, wcY + wcDepth * .22f, wcWidth, wcDepth * .78f);
        stroke(canvas, FIXTURE, .5f, wcX + wcWidth * .12f, wcY, wcWidth * .76f, wcDepth * .3f);
        // Basin.
        var basinWidth = unit * 1.7f;
        stroke(canvas, FIXTURE, .55f, x + unit * .6f, y + height - unit * 1.9f, basinWidth, unit * 1.3f);
        circle(canvas, FIXTURE, .45f, x + unit * .6f + basinWidth / 2, y + height - unit * 1.25f,
                unit * .42f);
        // Shower tray, in the corner with the diagonal that says it drains.
        if (height > 5 * unit || width > 5 * unit) {
            var trayX = x + unit * .6f;
            var trayY = y + unit * .6f;
            var traySize = Math.min(unit * 2.6f, Math.min(width, height) * .42f);
            stroke(canvas, FIXTURE, .5f, trayX, trayY, traySize, traySize);
            line(canvas, FIXTURE, .4f, trayX, trayY, trayX + traySize, trayY + traySize);
            line(canvas, FIXTURE, .4f, trayX + traySize, trayY, trayX, trayY + traySize);
        }
    }

    /** The flight, its landing and which way it climbs on this storey. */
    private void renderStairFlight(PDPageContentStream canvas, float x, float y, float width,
            float height, RoomGeometry room) throws IOException {
        var alongLength = room.length() >= room.width();
        var treads = Math.max(4, Math.min(11, (int) ((alongLength ? height : width) / 5.5f)));
        var flightSpan = (alongLength ? height : width) * .82f;
        var step = flightSpan / treads;
        for (var index = 1; index <= treads; index++) {
            if (alongLength) {
                var stepY = y + index * step;
                line(canvas, MUTED, .5f, x + 1.4f, stepY, x + width - 1.4f, stepY);
            } else {
                var stepX = x + index * step;
                line(canvas, MUTED, .5f, stepX, y + 1.4f, stepX, y + height - 1.4f);
            }
        }
        // Direction of travel: up from the entry end, which is the low end of the flight.
        if (alongLength) {
            arrowUp(canvas, x + width / 2, y + 2.5f, y + flightSpan - 1.5f);
        } else {
            line(canvas, ACCENT, .8f, x + 2.5f, y + height / 2, x + flightSpan, y + height / 2);
            line(canvas, ACCENT, .8f, x + flightSpan, y + height / 2, x + flightSpan - 3.4f,
                    y + height / 2 + 2.4f);
            line(canvas, ACCENT, .8f, x + flightSpan, y + height / 2, x + flightSpan - 3.4f,
                    y + height / 2 - 2.4f);
        }
        if (Math.min(width, height) > 18) {
            textCentered(canvas, BOLD, 4.6f, ACCENT, "UP", x + width / 2,
                    alongLength ? y + flightSpan + 3 : y + height - 6);
        }
    }

    private void renderLift(PDPageContentStream canvas, float x, float y, float width, float height)
            throws IOException {
        var inset = Math.min(3f, Math.min(width, height) * .16f);
        stroke(canvas, FIXTURE, .7f, x + inset, y + inset, width - inset * 2, height - inset * 2);
        line(canvas, FIXTURE, .5f, x + inset, y + inset, x + width - inset, y + height - inset);
        line(canvas, FIXTURE, .5f, x + width - inset, y + inset, x + inset, y + height - inset);
    }

    /** A car is 8'-6" across and 16'-0" long. Every bay drawn here is measured against that. */
    private static final float CAR_ACROSS = 8.5f;
    private static final float CAR_ALONG = 16f;

    /**
     * Cars on their bays, drawn as cars and counted in feet.
     *
     * <p>Counted from the real dimensions rather than from the proportions of the rectangle on the
     * page, because those are two different questions. A seventeen-foot-square court holds two cars
     * nose-in; scaled to the sheet it is a square, and asking the square how many cars fit answers
     * one — which is how a plan comes to show a single car adrift in a bay the schedule beside it
     * calls two.</p>
     */
    private void renderParkedCars(PDPageContentStream canvas, float x, float y, float width,
            float height, float scale) throws IOException {
        var widthFeet = width / scale;
        var heightFeet = height / scale;
        // Cars line up across the road they are entered from, not across whichever side of the bay
        // happens to be longer. A court seventeen feet wide and twenty deep off a north road holds
        // two cars abreast facing the street; measured by proportion alone it is "taller than it is
        // wide", and the bays came out lying on their sides, entered from a boundary wall.
        var facing = project.details().roadFacing();
        var rowAlongX = facing == Facing.NORTH || facing == Facing.SOUTH;
        var runFeet = rowAlongX ? widthFeet : heightFeet;
        var depthFeet = rowAlongX ? heightFeet : widthFeet;
        // Deep enough to drive in nose first, or the cars stand along the boundary instead.
        var noseIn = depthFeet >= CAR_ALONG - 1f;
        var perBay = noseIn ? CAR_ACROSS : CAR_ALONG;
        var bayDepth = noseIn ? CAR_ALONG : CAR_ACROSS;
        var bays = Math.max(1, Math.min(3, (int) Math.floor(runFeet / perBay)));
        var slot = (rowAlongX ? width : height) / bays;
        var carRun = Math.min(slot * .82f, perBay * .88f * scale);
        var carDepth = Math.min((rowAlongX ? height : width) * .86f, bayDepth * .92f * scale);
        if (carRun < 4 || carDepth < 4) return;

        for (var index = 0; index < bays; index++) {
            var offset = slot * index + (slot - carRun) / 2;
            if (rowAlongX) {
                renderCar(canvas, x + offset, y + (height - carDepth) / 2, carRun, carDepth, true);
            } else {
                renderCar(canvas, x + (width - carDepth) / 2, y + offset, carDepth, carRun, false);
            }
            if (index > 0) {
                if (rowAlongX) {
                    var lineX = x + slot * index;
                    line(canvas, MUTED, .5f, lineX, y + 2, lineX, y + height - 2);
                } else {
                    var lineY = y + slot * index;
                    line(canvas, MUTED, .5f, x + 2, lineY, x + width - 2, lineY);
                }
            }
        }
    }

    /**
     * One car in plan: body, cabin and wheels, drawn along the axis it is parked on.
     *
     * <p>Enough of a silhouette that a reader counts cars rather than crates. The proportions are
     * the car's own, so a bay too short for one shows it overhanging — which is the whole reason
     * for drawing the vehicle instead of shading the bay.</p>
     */
    private void renderCar(PDPageContentStream canvas, float x, float y, float width, float height,
            boolean lengthAlongY) throws IOException {
        var alongLength = lengthAlongY ? height : width;
        var across = lengthAlongY ? width : height;
        // Body, tucked in at nose and tail so the corners read as bodywork rather than as a box.
        var inset = across * .12f;
        if (lengthAlongY) {
            fill(canvas, CAR, x, y + inset, width, height - inset * 2);
            fill(canvas, CAR, x + inset, y, width - inset * 2, height);
            stroke(canvas, CAR_EDGE, .55f, x + inset * .5f, y + inset * .5f,
                    width - inset, height - inset);
            // Cabin and windscreen.
            fill(canvas, CAR_EDGE, x + across * .18f, y + alongLength * .34f,
                    across * .64f, alongLength * .3f);
            line(canvas, CAR_EDGE, .5f, x + across * .22f, y + alongLength * .74f,
                    x + across * .78f, y + alongLength * .74f);
            // Wheels.
            for (var side : new float[]{x - .4f, x + width - across * .1f + .4f}) {
                fill(canvas, CAR_EDGE, side, y + alongLength * .17f, across * .1f, alongLength * .16f);
                fill(canvas, CAR_EDGE, side, y + alongLength * .67f, across * .1f, alongLength * .16f);
            }
        } else {
            fill(canvas, CAR, x + inset, y, width - inset * 2, height);
            fill(canvas, CAR, x, y + inset, width, height - inset * 2);
            stroke(canvas, CAR_EDGE, .55f, x + inset * .5f, y + inset * .5f,
                    width - inset, height - inset);
            fill(canvas, CAR_EDGE, x + alongLength * .34f, y + across * .18f,
                    alongLength * .3f, across * .64f);
            line(canvas, CAR_EDGE, .5f, x + alongLength * .74f, y + across * .22f,
                    x + alongLength * .74f, y + across * .78f);
            for (var side : new float[]{y - .4f, y + height - across * .1f + .4f}) {
                fill(canvas, CAR_EDGE, x + alongLength * .17f, side, alongLength * .16f, across * .1f);
                fill(canvas, CAR_EDGE, x + alongLength * .67f, side, alongLength * .16f, across * .1f);
            }
        }
    }

    /** Lawn with planting along its edges, which is what a garden actually looks like in plan. */
    private void renderPlanting(PDPageContentStream canvas, float x, float y, float width, float height)
            throws IOException {
        canvas.setLineDashPattern(new float[]{2f, 2f}, 0);
        stroke(canvas, GARDEN_EDGE, .6f, x, y, width, height);
        canvas.setLineDashPattern(new float[]{}, 0);
        // Bounded rather than proportional. A tree stays a tree as the plate grows; scaling the
        // canopy with the band drew a nine-foot shrub as a twenty-foot crown and closed the garden
        // into a solid hedge along the whole frontage.
        var radius = Math.max(2.2f, Math.min(6.5f, Math.min(width, height) * .16f));
        var spacing = radius * 5.2f;
        if (width < spacing * .7f) return;
        var count = Math.max(1, Math.min(6, (int) (width / spacing)));
        var step = width / count;
        for (var index = 0; index < count; index++) {
            var treeX = x + step * (index + .5f);
            renderTree(canvas, treeX, y + height - radius - 2, radius);
            // A second row only where the band is genuinely deep enough to plant twice.
            if (height > radius * 7) renderTree(canvas, treeX + step * .5f, y + radius + 2, radius);
        }
    }

    /** A tree as a canopy of overlapping lobes, the way a landscape plan draws one. */
    private void renderTree(PDPageContentStream canvas, float centerX, float centerY, float radius)
            throws IOException {
        canvas.setNonStrokingColor(FOLIAGE);
        for (var index = 0; index < 5; index++) {
            var angle = Math.PI * 2 * index / 5;
            filledCircle(canvas, centerX + (float) Math.cos(angle) * radius * .42f,
                    centerY + (float) Math.sin(angle) * radius * .42f, radius * .58f);
        }
        canvas.setNonStrokingColor(FOLIAGE_DARK);
        filledCircle(canvas, centerX, centerY, radius * .26f);
    }

    /** Decking boards, which is how a courtyard reads as a surface you walk on. */
    private void renderDeck(PDPageContentStream canvas, float x, float y, float width, float height,
            float scale) throws IOException {
        var spacing = Math.max(3f, scale * .8f);
        var alongLength = height >= width;
        canvas.setStrokingColor(new Color(120, 84, 56));
        canvas.setLineWidth(.4f);
        if (alongLength) {
            for (var boardX = x + spacing; boardX < x + width - .5f; boardX += spacing) {
                canvas.moveTo(boardX, y + 1.5f);
                canvas.lineTo(boardX, y + height - 1.5f);
            }
        } else {
            for (var boardY = y + spacing; boardY < y + height - .5f; boardY += spacing) {
                canvas.moveTo(x + 1.5f, boardY);
                canvas.lineTo(x + width - 1.5f, boardY);
            }
        }
        canvas.stroke();
        var potRadius = Math.min(scale * .9f, Math.min(width, height) * .16f);
        if (potRadius >= 2) {
            renderTree(canvas, x + width - potRadius - 3, y + height - potRadius - 3, potRadius);
        }
    }

    /** A table and chairs on a terrace, planters on a balcony too narrow for them. */
    private void renderOutdoorSeating(PDPageContentStream canvas, float x, float y, float width,
            float height, float scale) throws IOException {
        var potRadius = Math.min(scale * .85f, Math.min(width, height) * .2f);
        if (potRadius >= 1.8f) {
            renderTree(canvas, x + potRadius + 2.5f, y + potRadius + 2.5f, potRadius);
            if (width > potRadius * 8) {
                renderTree(canvas, x + width - potRadius - 2.5f, y + potRadius + 2.5f, potRadius);
            }
        }
        if (Math.min(width, height) < 7 * scale * .55f) return;
        var tableSize = Math.min(3.4f * scale, Math.min(width, height) * .38f);
        if (tableSize < 7) return;
        var tableX = x + (width - tableSize) / 2;
        var tableY = y + (height - tableSize) / 2;
        fill(canvas, FURNITURE, tableX, tableY, tableSize, tableSize);
        var chair = tableSize * .34f;
        stroke(canvas, FURNITURE, .45f, tableX + (tableSize - chair) / 2, tableY - chair - 1.6f,
                chair, chair);
        stroke(canvas, FURNITURE, .45f, tableX + (tableSize - chair) / 2,
                tableY + tableSize + 1.6f, chair, chair);
        stroke(canvas, FURNITURE, .45f, tableX - chair - 1.6f, tableY + (tableSize - chair) / 2,
                chair, chair);
        stroke(canvas, FURNITURE, .45f, tableX + tableSize + 1.6f, tableY + (tableSize - chair) / 2,
                chair, chair);
    }

    private void renderDesk(PDPageContentStream canvas, float x, float y, float width, float height,
            float scale, boolean landscape) throws IOException {
        var deskLength = Math.min(landscape ? width * .58f : width * .74f, 5f * scale);
        var deskDepth = Math.min(2f * scale, height * .24f);
        if (deskLength < 8 || deskDepth < 3) return;
        var deskX = x + (width - deskLength) / 2;
        var deskY = y + height - deskDepth - Math.min(4f, height * .1f);
        fill(canvas, FURNITURE, deskX, deskY, deskLength, deskDepth);
        var chair = Math.min(1.6f * scale, deskDepth);
        stroke(canvas, FURNITURE, .5f, deskX + (deskLength - chair) / 2, deskY - chair - 2,
                chair, chair);
        // Shelving on the opposite wall.
        var shelfDepth = Math.min(1.4f * scale, height * .12f);
        if (deskY - chair - shelfDepth - 6 > y) {
            stroke(canvas, FURNITURE, .45f, x + 2, y + 2, width - 4, shelfDepth);
        }
    }

    private void renderShelving(PDPageContentStream canvas, float x, float y, float width, float height,
            float scale, boolean landscape) throws IOException {
        var depth = Math.min(1.6f * scale, Math.min(width, height) * .3f);
        if (depth < 2.4f) return;
        if (landscape) {
            fill(canvas, FURNITURE_SOFT, x + 1.5f, y + height - depth - 1.5f, width - 3, depth);
            stroke(canvas, FURNITURE, .5f, x + 1.5f, y + height - depth - 1.5f, width - 3, depth);
            for (var index = 1; index < 4; index++) {
                var shelfX = x + 1.5f + (width - 3) * index / 4;
                line(canvas, FURNITURE, .4f, shelfX, y + height - depth - 1.5f, shelfX,
                        y + height - 1.5f);
            }
        } else {
            fill(canvas, FURNITURE_SOFT, x + 1.5f, y + 1.5f, depth, height - 3);
            stroke(canvas, FURNITURE, .5f, x + 1.5f, y + 1.5f, depth, height - 3);
            for (var index = 1; index < 4; index++) {
                var shelfY = y + 1.5f + (height - 3) * index / 4;
                line(canvas, FURNITURE, .4f, x + 1.5f, shelfY, x + 1.5f + depth, shelfY);
            }
        }
    }

    private void renderAppliances(PDPageContentStream canvas, float x, float y, float width,
            float height, float scale) throws IOException {
        var size = Math.min(2f * scale, Math.min(width, height) * .42f);
        if (size < 3.5f) return;
        stroke(canvas, FIXTURE, .55f, x + 2, y + height - size - 2, size, size);
        circle(canvas, FIXTURE, .45f, x + 2 + size / 2, y + height - size / 2 - 2, size * .3f);
        if (width > size * 2.6f) {
            stroke(canvas, FIXTURE, .55f, x + size + 4, y + height - size - 2, size, size);
            line(canvas, FIXTURE, .4f, x + size + 4, y + height - size * .35f - 2,
                    x + size * 2 + 4, y + height - size * .35f - 2);
        }
    }

    private void renderShrine(PDPageContentStream canvas, float x, float y, float width, float height,
            float scale) throws IOException {
        var shrineWidth = Math.min(width * .6f, 2.6f * scale);
        var shrineDepth = Math.min(height * .28f, 1.5f * scale);
        if (shrineWidth < 4 || shrineDepth < 2.4f) return;
        var shrineX = x + (width - shrineWidth) / 2;
        var shrineY = y + height - shrineDepth - 2;
        fill(canvas, FURNITURE, shrineX, shrineY, shrineWidth, shrineDepth);
        line(canvas, ACCENT, .7f, shrineX + shrineWidth / 2, shrineY,
                shrineX + shrineWidth / 2, shrineY - Math.min(6f, height * .2f));
    }

    /** A console and a mat: the two things that make a hall read as an entrance. */
    private void renderFoyer(PDPageContentStream canvas, float x, float y, float width, float height,
            float scale) throws IOException {
        var consoleWidth = Math.min(width * .62f, 3.4f * scale);
        var consoleDepth = Math.min(1.3f * scale, height * .14f);
        if (consoleWidth < 5 || consoleDepth < 2) return;
        fill(canvas, FURNITURE, x + (width - consoleWidth) / 2, y + height - consoleDepth - 2.5f,
                consoleWidth, consoleDepth);
        var matWidth = Math.min(width * .5f, 3f * scale);
        var matHeight = Math.min(height * .22f, 2f * scale);
        if (matWidth > 4 && matHeight > 3) {
            canvas.setLineDashPattern(new float[]{1.6f, 1.6f}, 0);
            stroke(canvas, MUTED, .5f, x + (width - matWidth) / 2, y + 3, matWidth, matHeight);
            canvas.setLineDashPattern(new float[]{}, 0);
        }
    }

    // -------------------------------------------------------------------------------------------
    // Geometry helpers
    // -------------------------------------------------------------------------------------------

    private List<RoomGeometry> roomsOn(String floor) {
        var rooms = new ArrayList<RoomGeometry>();
        for (var room : geometry.rooms()) {
            if (floor.equalsIgnoreCase(room.floor())) rooms.add(room);
        }
        return rooms;
    }

    private Color zoneOf(String type) {
        if (type == null) return ZONE_PUBLIC;
        if (type.contains("BATHROOM") || "TOILET".equals(type)) return ZONE_WET;
        if (type.endsWith("BEDROOM")) return ZONE_PRIVATE;
        if (type.contains("PARKING")) return ZONE_PAVING;
        if ("COURTYARD".equals(type)) return ZONE_DECK;
        if ("TERRACE".equals(type) || "BALCONY".equals(type) || "OPEN_SPACE".equals(type)) {
            return ZONE_OUTDOOR;
        }
        if ("VERANDAH".equals(type) || "PORCH".equals(type)) return ZONE_PAVING;
        if (RoomSpec.CORRIDOR.equals(type) || "STAIRCASE".equals(type) || "LIFT_SHAFT".equals(type)) {
            return ZONE_CIRCULATION;
        }
        return switch (type) {
            case "KITCHEN", "UTILITY", "LAUNDRY", "STORE", "DRESSING_ROOM" -> ZONE_SERVICE;
            default -> ZONE_PUBLIC;
        };
    }

    private long countType(String type) {
        return countMatching(type::equals);
    }

    private long countMatching(java.util.function.Predicate<String> predicate) {
        return geometry.rooms().stream().map(RoomGeometry::type).filter(java.util.Objects::nonNull)
                .filter(predicate).count();
    }

    private boolean hasType(String type) {
        return countType(type) > 0;
    }

    /**
     * Bays this plan gives the family, indoors and on the approach.
     *
     * <p>Counted the same way the programme audit counts them, so the schedule on this sheet and the
     * validation report behind it can never disagree about how many cars the home parks.</p>
     */
    private int parkingBays() {
        var bays = 0L;
        for (var room : geometry.rooms()) {
            if (room.type() != null && room.type().contains("PARKING")) {
                bays += bayCapacity(room.width(), room.length());
            }
        }
        for (var element : geometry.siteElements()) {
            if (element.type() != null && element.type().contains("PARKING")) {
                bays += bayCapacity(element.width(), element.length());
            }
        }
        return (int) bays;
    }

    private long bayCapacity(double width, double length) {
        return Math.min(3, Math.max(
                (long) Math.floor(width / 8d) * (long) Math.floor(length / 16d),
                (long) Math.floor(width / 16d) * (long) Math.floor(length / 8d)));
    }

    private Map<String, String> versions() {
        return drawing.versions() == null ? Map.of() : drawing.versions();
    }

    // -------------------------------------------------------------------------------------------
    // Formatting
    // -------------------------------------------------------------------------------------------

    /** {@code 13'-6"} — the way an Indian residential plan writes a dimension. */
    static String feetInches(double value) {
        var totalInches = (int) Math.round(value * 12);
        var wholeFeet = totalInches / 12;
        var inches = totalInches % 12;
        return wholeFeet + "'-" + inches + "\"";
    }

    /** {@code 40'} — the plot dimension, which is quoted in whole feet. */
    private static String feet(double value) {
        return Math.round(value) + "'";
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) return "";
        var words = value.replace('_', ' ').toLowerCase(Locale.ROOT).split(" ");
        var result = new StringBuilder();
        for (var word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String orientation(Object value, String fallback) {
        var resolved = text(value);
        if (resolved == null) return fallback;
        var upper = resolved.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "NORTH", "SOUTH", "EAST", "WEST" -> upper;
            default -> fallback;
        };
    }

    private static double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException ignored) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    // -------------------------------------------------------------------------------------------
    // Drawing primitives
    // -------------------------------------------------------------------------------------------

    private void card(PDPageContentStream canvas, float x, float y, float width, float height)
            throws IOException {
        fill(canvas, Color.WHITE, x, y, width, height);
        stroke(canvas, HAIRLINE, .8f, x, y, width, height);
    }

    private void fill(PDPageContentStream canvas, Color color, float x, float y, float width,
            float height) throws IOException {
        canvas.setNonStrokingColor(color);
        canvas.addRect(x, y, width, height);
        canvas.fill();
    }

    private void stroke(PDPageContentStream canvas, Color color, float lineWidth, float x, float y,
            float width, float height) throws IOException {
        canvas.setStrokingColor(color);
        canvas.setLineWidth(lineWidth);
        canvas.addRect(x, y, width, height);
        canvas.stroke();
    }

    private void line(PDPageContentStream canvas, Color color, float lineWidth, float x1, float y1,
            float x2, float y2) throws IOException {
        canvas.setStrokingColor(color);
        canvas.setLineWidth(lineWidth);
        canvas.moveTo(x1, y1);
        canvas.lineTo(x2, y2);
        canvas.stroke();
    }

    private void circle(PDPageContentStream canvas, Color color, float lineWidth, float centerX,
            float centerY, float radius) throws IOException {
        canvas.setStrokingColor(color);
        canvas.setLineWidth(lineWidth);
        circlePath(canvas, centerX, centerY, radius);
        canvas.stroke();
    }

    private void filledCircle(PDPageContentStream canvas, float centerX, float centerY, float radius)
            throws IOException {
        circlePath(canvas, centerX, centerY, radius);
        canvas.fill();
    }

    private void circlePath(PDPageContentStream canvas, float centerX, float centerY, float radius)
            throws IOException {
        var control = radius * .55228475f;
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
    }

    private void polygon(PDPageContentStream canvas, List<PlotVertex> ring, float originX,
            float originY, float scale, Color fill, Color strokeColor, float lineWidth)
            throws IOException {
        if (ring == null || ring.size() < 3) return;
        for (var index = 0; index < ring.size(); index++) {
            var vertex = ring.get(index);
            var x = originX + vertex.x().floatValue() * scale;
            var y = originY + vertex.y().floatValue() * scale;
            if (index == 0) {
                canvas.moveTo(x, y);
            } else {
                canvas.lineTo(x, y);
            }
        }
        canvas.closePath();
        if (fill != null) {
            canvas.setNonStrokingColor(fill);
            canvas.fill();
        } else {
            canvas.setStrokingColor(strokeColor);
            canvas.setLineWidth(lineWidth);
            canvas.stroke();
        }
    }

    private void fillRoom(PDPageContentStream canvas, Color color, RoomGeometry room, float originX,
            float originY, float scale) throws IOException {
        if (!room.shaped()) {
            fill(canvas, color, originX + (float) room.x() * scale, originY + (float) room.y() * scale,
                    (float) room.width() * scale, (float) room.length() * scale);
            return;
        }
        polygon(canvas, room.corners(), originX, originY, scale, color, null, 0);
    }

    private void strokeRoom(PDPageContentStream canvas, Color color, float lineWidth, RoomGeometry room,
            float originX, float originY, float scale) throws IOException {
        if (!room.shaped()) {
            stroke(canvas, color, lineWidth, originX + (float) room.x() * scale,
                    originY + (float) room.y() * scale, (float) room.width() * scale,
                    (float) room.length() * scale);
            return;
        }
        polygon(canvas, room.corners(), originX, originY, scale, null, color, lineWidth);
    }

    private void text(PDPageContentStream canvas, PDFont font, float size, Color color, String value,
            float x, float y) throws IOException {
        canvas.beginText();
        canvas.setFont(font, size);
        canvas.setNonStrokingColor(color);
        canvas.newLineAtOffset(x, y);
        canvas.showText(safe(value));
        canvas.endText();
    }

    private void textCentered(PDPageContentStream canvas, PDFont font, float size, Color color,
            String value, float centerX, float y) throws IOException {
        text(canvas, font, size, color, value, centerX - textWidth(font, size, value) / 2, y);
    }

    private void textRight(PDPageContentStream canvas, PDFont font, float size, Color color,
            String value, float right, float y) throws IOException {
        text(canvas, font, size, color, value, right - textWidth(font, size, value), y);
    }

    private void textRotated(PDPageContentStream canvas, PDFont font, float size, Color color,
            String value, float x, float y) throws IOException {
        canvas.beginText();
        canvas.setFont(font, size);
        canvas.setNonStrokingColor(color);
        canvas.setTextMatrix(org.apache.pdfbox.util.Matrix.getRotateInstance(Math.PI / 2, x, y));
        canvas.showText(safe(value));
        canvas.endText();
    }

    private float textWidth(PDFont font, float size, String value) throws IOException {
        return font.getStringWidth(safe(value)) / 1000 * size;
    }

    /** Standard-14 fonts encode WinAnsi only, so anything outside it becomes a plain equivalent. */
    private static String safe(String value) {
        if (value == null) return "";
        var builder = new StringBuilder(value.length());
        for (var character : value.toCharArray()) {
            if (character == '×') {
                builder.append('x');
            } else if (character == '—' || character == '–') {
                builder.append('-');
            } else if (character >= 32 && character <= 126) {
                builder.append(character);
            } else if (character >= 160 && character <= 255) {
                builder.append(character);
            } else {
                builder.append(' ');
            }
        }
        return builder.toString();
    }
}

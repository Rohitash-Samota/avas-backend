package com.avas.platform.project;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Renders the selected household brief, circulation guidance and persisted room schedule. */
@Component
class RecommendationPdfPageRenderer {
    private static final int SCHEDULE_ROWS_PER_PAGE = 13;

    void render(PDDocument document, ProjectSummary project, DrawingCandidate drawing,
            ProjectComparisonReport.Option option) throws IOException {
        var facts = RecommendationFacts.from(project, drawing);
        renderOverview(document, project, drawing, option, facts);
        renderRoomSchedule(document, project, drawing, facts);
    }

    private void renderOverview(PDDocument document, ProjectSummary project, DrawingCandidate drawing,
            ProjectComparisonReport.Option option, RecommendationFacts facts) throws IOException {
        var page = ReportPdfSupport.page();
        document.addPage(page);
        try (var canvas = new PDPageContentStream(document, page)) {
            ReportPdfSupport.pageBackground(canvas);
            ReportPdfSupport.header(canvas, "PERSONALISED RECOMMENDATION  |  SELECTED OPTION");
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 24, ReportPdfSupport.INK,
                    "PERSONALISED DESIGN RECOMMENDATION", 36, 748);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.4f, ReportPdfSupport.ACCENT,
                    "HOUSEHOLD, AREA AND SELECTED-PREFERENCE RESPONSE", 36, 732);
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6.2f, ReportPdfSupport.MUTED,
                    ReportPdfSupport.fit(project.name() + "  |  "
                                    + ReportPdfSupport.present(facts.city(), "Location pending")
                                    + "  |  " + option.name(),
                            ReportPdfSupport.REGULAR, 6.2f, 523), 36, 717);

            var gap = 8f;
            var metricWidth = (523f - gap * 3) / 4;
            metric(canvas, "HOUSEHOLD", facts.members() + " people",
                    facts.familySummary(), 36, 630, metricWidth);
            metric(canvas, "RECOMMENDED PROGRAMME", facts.recommendedBedrooms() + " bedrooms",
                    facts.recommendedAttachedBathrooms() + " attached + "
                            + facts.recommendedCommonBathrooms() + " common bath",
                    36 + metricWidth + gap, 630, metricWidth);
            metric(canvas, "CURRENT DRAWING", facts.bedrooms() + " bed | " + facts.bathrooms() + " bath",
                    facts.rooms().size() + " persisted spaces", 36 + (metricWidth + gap) * 2, 630, metricWidth);
            metric(canvas, "AREA & LEVELS", ReportPdfSupport.grouped(drawing.builtUpArea()) + " sq ft",
                    facts.floorCount() + " floors | "
                            + ReportPdfSupport.grouped(Math.round(facts.plotArea())) + " sq ft plot",
                    36 + (metricWidth + gap) * 3, 630, metricWidth);

            ReportPdfSupport.card(canvas, 36, 468, 253, 143, false);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.2f, ReportPdfSupport.ACCENT,
                    "FAMILY & AREA BASIS", 49, 589);
            bulletList(canvas, facts.recommendationReasons(), 49, 568, 224, 4, 6.05f, 9f, 8f);

            ReportPdfSupport.card(canvas, 306, 468, 253, 143, false);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.2f, ReportPdfSupport.ACCENT,
                    "SELECTED DESIGN DIRECTION", 319, 589);
            var direction = List.of(
                    "Home: " + ReportPdfSupport.title(facts.homeType())
                            + " | " + ReportPdfSupport.title(facts.finishTier())
                            + " specification.",
                    "Road-facing edge: " + ReportPdfSupport.title(facts.roadFacing())
                            + "; verify sun, wind, views, noise and privacy on site.",
                    "Stack kitchens and bathrooms; keep stair, lift and structural lines continuous.",
                    "Selected vector option: " + option.name() + " ("
                            + ReportPdfSupport.title(drawing.strategy()) + ").");
            bulletList(canvas, direction, 319, 568, 224, 4, 6.05f, 9f, 8f);

            renderLiftGuidance(canvas, facts, 36, 282, 253, 164);
            renderBalconyGuidance(canvas, facts, 306, 282, 253, 164);

            ReportPdfSupport.card(canvas, 36, 64, 523, 196, true);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.2f, ReportPdfSupport.ACCENT,
                    "COORDINATION PRIORITIES BEFORE APPROVAL OR CONSTRUCTION", 49, 238);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6f, ReportPdfSupport.INK,
                    "PLAN / STRUCTURE", 49, 216);
            bulletList(canvas, List.of(
                    "Verify the legal plot, setbacks, coverage, FAR/FSI, height and parking rules locally.",
                    "Keep the stair/lift core and support lines aligned on every represented floor.",
                    "Check every room with furniture, door swings, storage and usable circulation."),
                    49, 198, 224, 3, 5.85f, 8.5f, 7f);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6f, ReportPdfSupport.INK,
                    "MEP / SAFETY", 319, 216);
            bulletList(canvas, List.of(
                    "Keep wet rooms close to service shafts; reserve accessible maintenance routes.",
                    "Coordinate lift power, plumbing, drainage, electrical and roof equipment early.",
                    "Have fire egress, accessibility, structure and all code dimensions professionally checked."),
                    319, 198, 224, 3, 5.85f, 8.5f, 7f);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.7f, ReportPdfSupport.ACCENT,
                    "CONCEPT ONLY - ROOM SIZES AND SYSTEM GUIDANCE ARE NOT STATUTORY OR CONSTRUCTION DESIGN.",
                    49, 84);

            ReportPdfSupport.footer(canvas, project.projectCode(),
                    "Personalised brief from saved selections and current vector geometry.");
        }
    }

    private void renderLiftGuidance(PDPageContentStream canvas, RecommendationFacts facts,
            float x, float y, float width, float height) throws IOException {
        ReportPdfSupport.card(canvas, x, y, width, height, false);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.2f, ReportPdfSupport.ACCENT,
                "LIFT + ACCESSIBILITY", x + 13, y + height - 22);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 9f, ReportPdfSupport.INK,
                ReportPdfSupport.fit("AVAS: " + liftRecommendationLabel(facts.recommendedLiftProvision()),
                        ReportPdfSupport.BOLD, 9f, width - 26),
                x + 13, y + height - 47);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.7f, ReportPdfSupport.MUTED,
                ReportPdfSupport.fit("Selected: " + ReportPdfSupport.title(facts.requestedLiftProvision())
                                + " | Drawing: " + facts.liftStacks() + " lift core"
                                + (facts.liftStacks() == 1 ? "" : "s"),
                        ReportPdfSupport.BOLD, 5.7f, width - 26), x + 13, y + height - 64);
        var guidance = liftGuidance(facts);
        var cursor = y + height - 83;
        for (var line : ReportPdfSupport.wrap(guidance, ReportPdfSupport.REGULAR, 6.05f, width - 26)) {
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6.05f, ReportPdfSupport.MUTED,
                    line, x + 13, cursor);
            cursor -= 9;
        }
        cursor -= 6;
        var accessibility = facts.seniors() > 0 || facts.accessibleGroundFloor()
                ? "Accessibility priority: retain a step-free arrival and usable ground-floor bed/bath route."
                : "Keep the arrival, stairs and circulation clear; confirm accessibility requirements locally.";
        for (var line : ReportPdfSupport.wrap(accessibility, ReportPdfSupport.REGULAR, 5.8f, width - 26)) {
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.8f, ReportPdfSupport.MUTED,
                    line, x + 13, cursor);
            cursor -= 8.5f;
        }
    }

    private void renderBalconyGuidance(PDPageContentStream canvas, RecommendationFacts facts,
            float x, float y, float width, float height) throws IOException {
        ReportPdfSupport.card(canvas, x, y, width, height, false);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.2f, ReportPdfSupport.ACCENT,
                "BALCONY + OUTDOOR", x + 13, y + height - 22);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 9f, ReportPdfSupport.INK,
                "AVAS: " + facts.recommendedBalconies() + " "
                        + (facts.recommendedBalconies() == 1 ? "BALCONY" : "BALCONIES"),
                x + 13, y + height - 47);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.7f, ReportPdfSupport.MUTED,
                "Selected: " + facts.requestedBalconies() + " | Drawing: " + facts.balconies(),
                x + 13, y + height - 64);
        var cursor = y + height - 83;
        for (var line : ReportPdfSupport.wrap(balconyRecommendationBasis(facts),
                ReportPdfSupport.REGULAR, 6.05f, width - 26)) {
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6.05f, ReportPdfSupport.MUTED,
                    line, x + 13, cursor);
            cursor -= 9;
        }
        cursor -= 4;
        var guidance = facts.recommendedBalconies() == 0
                ? "Preserve indoor area; add outdoor space only after facade, privacy and approval checks."
                : "Use daylight edges; aim for about 1.5 m useful depth where permitted, with shade, privacy, "
                        + "waterproofing, drain and overflow.";
        for (var line : ReportPdfSupport.wrap(guidance, ReportPdfSupport.REGULAR, 6.05f, width - 26)) {
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6.05f, ReportPdfSupport.MUTED,
                    line, x + 13, cursor);
            cursor -= 9;
        }
        cursor -= 4;
        var outdoor = new ArrayList<String>();
        if (facts.terraceRequested()) outdoor.add("terrace selected");
        if (facts.courtyardRequested()) outdoor.add("courtyard selected");
        if (facts.rainwaterHarvesting()) outdoor.add("rainwater harvesting selected");
        var outdoorNote = outdoor.isEmpty()
                ? "No additional outdoor systems were selected."
                : ReportPdfSupport.title(String.join("; ", outdoor))
                        + ". Protect drainage, structure and maintenance access.";
        for (var line : ReportPdfSupport.wrap(outdoorNote, ReportPdfSupport.REGULAR, 5.8f, width - 26)) {
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.8f, ReportPdfSupport.MUTED,
                    line, x + 13, cursor);
            cursor -= 8.5f;
        }
    }

    private String liftGuidance(RecommendationFacts facts) {
        var basis = facts.planningFloors() <= 1
                ? "A single-floor programme does not need a residential lift provision."
                : "FUTURE_SHAFT".equals(facts.recommendedLiftProvision())
                        ? "A future shaft is the minimum AVAS recommendation because the brief has "
                                + (facts.planningFloors() >= 3 ? "three floors"
                                        : facts.seniors() > 0 ? "senior residents" : "an accessible-floor priority")
                                + "."
                        : "The current two-floor brief has no household-triggered lift minimum; a future shaft "
                                + "remains an adaptability option.";
        if (facts.liftStacks() == 0 && !"NONE".equals(facts.requestedLiftProvision())) {
            return basis + " The selected lift provision is not represented; coordinate or regenerate before issue.";
        }
        if (facts.liftStacks() > 0) {
            return basis + " Keep the core aligned; the vendor must verify shaft, pit, headroom, power and rescue.";
        }
        return basis;
    }

    private String liftRecommendationLabel(String recommendation) {
        return "FUTURE_SHAFT".equals(recommendation) ? "FUTURE SHAFT MINIMUM" : "NO LIFT MINIMUM";
    }

    private String balconyRecommendationBasis(RecommendationFacts facts) {
        if (facts.planningFloors() <= 1) {
            return "Area rule: 0 for a single-floor home; preserve the core indoor programme.";
        }
        var basis = new ArrayList<String>();
        basis.add("1 base for a multi-floor home");
        if (facts.recommendedBedrooms() >= 4 && facts.plotArea() >= 1_500) {
            basis.add("+1 for " + facts.recommendedBedrooms() + " bedrooms on >=1,500 sq ft");
        }
        if (facts.recommendedBedrooms() >= 6 && facts.planningFloors() >= 3 && facts.plotArea() >= 2_400) {
            basis.add("+1 for the large programme/plot threshold");
        }
        return "Area/household rule: " + String.join("; ", basis) + ".";
    }

    private void renderRoomSchedule(PDDocument document, ProjectSummary project, DrawingCandidate drawing,
            RecommendationFacts facts) throws IOException {
        var pageCount = Math.max(1, (facts.rooms().size() + SCHEDULE_ROWS_PER_PAGE - 1)
                / SCHEDULE_ROWS_PER_PAGE);
        for (var pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            var from = (pageNumber - 1) * SCHEDULE_ROWS_PER_PAGE;
            var to = Math.min(facts.rooms().size(), from + SCHEDULE_ROWS_PER_PAGE);
            renderRoomSchedulePage(document, project, drawing, facts,
                    facts.rooms().subList(from, to), pageNumber, pageCount);
        }
    }

    private void renderRoomSchedulePage(PDDocument document, ProjectSummary project, DrawingCandidate drawing,
            RecommendationFacts facts, List<RoomGeometry> rooms, int pageNumber, int pageCount)
            throws IOException {
        var page = ReportPdfSupport.page();
        document.addPage(page);
        try (var canvas = new PDPageContentStream(document, page)) {
            ReportPdfSupport.pageBackground(canvas);
            ReportPdfSupport.header(canvas, "ACTUAL ROOM SCHEDULE  |  " + pageNumber + " OF " + pageCount);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 24, ReportPdfSupport.INK,
                    "ACTUAL ROOM SCHEDULE", 36, 748);
            ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 6.4f, ReportPdfSupport.ACCENT,
                    "PERSISTED VECTOR GEOMETRY - DIMENSIONS AND PLANNED CONTENTS", 36, 732);
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 6.2f, ReportPdfSupport.MUTED,
                    ReportPdfSupport.fit(drawing.name() + "  |  " + facts.rooms().size() + " spaces across "
                                    + facts.floorCount() + " floors  |  Dimensions in "
                                    + ReportPdfSupport.title(drawing.geometry().unit()),
                            ReportPdfSupport.REGULAR, 6.2f, 523), 36, 716);

            ReportPdfSupport.fill(canvas, ReportPdfSupport.PAPER, 36, 676, 523, 27);
            column(canvas, "FLOOR", 44, 687);
            column(canvas, "SPACE / GEOMETRY ID", 102, 687);
            column(canvas, "CLEAR SIZE", 229, 687);
            column(canvas, "AREA", 310, 687);
            column(canvas, "PLANNED CONTENTS / CHECK", 369, 687);

            var rowTop = 676f;
            for (var room : rooms) {
                renderRoomRow(canvas, drawing, room, facts.parkingCars(), rowTop, 46);
                rowTop -= 46;
            }
            if (rooms.isEmpty()) {
                ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 8, ReportPdfSupport.MUTED,
                        "No persisted room geometry is available for this schedule.", 49, 640);
            }
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.4f, ReportPdfSupport.MUTED,
                    "Dimensions reproduce the current concept geometry; verify statutory minima, furniture fit, "
                            + "structure and services before approval.", 44, 56);
            ReportPdfSupport.footer(canvas, project.projectCode(),
                    "Room schedule | Page " + pageNumber + " of " + pageCount + " | Concept only.");
        }
    }

    private void renderRoomRow(PDPageContentStream canvas, DrawingCandidate drawing,
            RoomGeometry room, int parkingCars, float rowTop, float rowHeight) throws IOException {
        ReportPdfSupport.line(canvas, ReportPdfSupport.LINE, .4f, 36, rowTop - rowHeight, 559,
                rowTop - rowHeight);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.8f, ReportPdfSupport.INK,
                ReportPdfSupport.fit(floorLabel(room.floor()), ReportPdfSupport.BOLD, 5.8f, 50),
                44, rowTop - 18);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.9f, ReportPdfSupport.INK,
                ReportPdfSupport.fit(ReportPdfSupport.title(room.type()), ReportPdfSupport.BOLD, 5.9f, 119),
                102, rowTop - 15);
        ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 4.8f, ReportPdfSupport.MUTED,
                ReportPdfSupport.fit(ReportPdfSupport.present(room.id(), "No geometry id"),
                        ReportPdfSupport.REGULAR, 4.8f, 119), 102, rowTop - 29);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.8f, ReportPdfSupport.INK,
                dimension(room, drawing.geometry().unit()), 229, rowTop - 18);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.8f, ReportPdfSupport.INK,
                area(room, drawing.geometry().unit()), 310, rowTop - 18);
        var cursor = rowTop - 13;
        for (var line : ReportPdfSupport.wrap(contentsFor(room, parkingCars),
                ReportPdfSupport.REGULAR, 5.2f, 182).stream().limit(4).toList()) {
            ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.2f, ReportPdfSupport.MUTED,
                    line, 369, cursor);
            cursor -= 7.5f;
        }
    }

    private void metric(PDPageContentStream canvas, String label, String value, String detail,
            float x, float y, float width) throws IOException {
        ReportPdfSupport.card(canvas, x, y, width, 76, false);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.2f, ReportPdfSupport.ACCENT,
                label, x + 9, y + 56);
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 9.2f, ReportPdfSupport.INK,
                ReportPdfSupport.fit(value, ReportPdfSupport.BOLD, 9.2f, width - 18), x + 9, y + 34);
        ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, 5.1f, ReportPdfSupport.MUTED,
                ReportPdfSupport.fit(detail, ReportPdfSupport.REGULAR, 5.1f, width - 18), x + 9, y + 16);
    }

    private float bulletList(PDPageContentStream canvas, List<String> values,
            float x, float y, float width, int limit, float fontSize, float lineHeight, float gap)
            throws IOException {
        var cursor = y;
        for (var value : values.stream().filter(item -> item != null && !item.isBlank()).limit(limit).toList()) {
            ReportPdfSupport.fill(canvas, ReportPdfSupport.ACCENT, x, cursor + 1, 3, 3);
            for (var line : ReportPdfSupport.wrap(value, ReportPdfSupport.REGULAR, fontSize, width - 12)) {
                ReportPdfSupport.text(canvas, ReportPdfSupport.REGULAR, fontSize, ReportPdfSupport.MUTED,
                        line, x + 11, cursor);
                cursor -= lineHeight;
            }
            cursor -= gap;
        }
        return cursor;
    }

    private void column(PDPageContentStream canvas, String label, float x, float y) throws IOException {
        ReportPdfSupport.text(canvas, ReportPdfSupport.BOLD, 5.1f, ReportPdfSupport.MUTED, label, x, y);
    }

    private String dimension(RoomGeometry room, String unit) {
        var suffix = "FEET".equalsIgnoreCase(unit) ? "ft" : "m";
        return String.format(Locale.ROOT, "%.1f x %.1f %s", room.width(), room.length(), suffix);
    }

    private String area(RoomGeometry room, String unit) {
        var suffix = "FEET".equalsIgnoreCase(unit) ? "sq ft" : "sq m";
        return String.format(Locale.ROOT, "%.1f %s", room.area(), suffix);
    }

    private String floorLabel(String floor) {
        var normalized = floor == null || floor.isBlank() ? "GROUND" : floor;
        return ReportPdfSupport.title(normalized) + " floor";
    }

    private String contentsFor(RoomGeometry room, int parkingCars) {
        var type = normalizedType(room);
        if (type.contains("COURTYARD_PARKING")) {
            return parkingCars + " car bay(s), landscape edge and safe pedestrian route";
        }
        if (type.equals("PARKING")) {
            return parkingCars + " car bay(s), door clearance, turning and drainage";
        }
        if (type.contains("SENIOR_BEDROOM")) return "Accessible bed, wardrobe and clear transfer path";
        if (type.contains("MASTER_BEDROOM")) return "Double/queen bed, wardrobe wall and side tables";
        if (type.contains("BEDROOM")) return "Bed, wardrobe and study/storage";
        if (type.contains("LIVING")) return "Sofa grouping, media wall and clear circulation";
        if (type.contains("DINING")) return "Dining table, chairs and crockery storage";
        if (type.contains("KITCHEN")) return "Counter, hob, sink, refrigerator and storage";
        if (type.contains("ATTACHED_BATH")) return "WC, basin, shower and wet-shaft connection";
        if (type.contains("BATH") || type.contains("TOILET")) {
            return "WC, basin, shower and ventilation/shaft route";
        }
        if (type.contains("STAIR")) return "Flights, landings, handrails and protected headroom";
        if (type.contains("LIFT")) return "Lift shaft; vendor sizes car, pit, headroom, power and rescue access";
        if (type.contains("BALCONY")) return "Outdoor seating/planters, safe railing and floor drain";
        if (type.contains("TERRACE")) return "Outdoor use, waterproofing, falls, drain and overflow";
        if (type.contains("COURTYARD")) return "Landscape, daylight, ventilation and drainage";
        if (type.contains("UTILITY")) return "Laundry/sink counter, appliance and service storage";
        if (type.contains("LAUNDRY")) return "Washer, sink, drying zone and utility storage";
        if (type.contains("FAMILY_LOUNGE")) return "Family seating, media/study zone and storage";
        if (type.contains("DRESSING")) return "Wardrobes, dresser and clear changing space";
        if (type.contains("STUDY") || type.contains("HOME_OFFICE")) {
            return "Desk, task chair, shelves and power/data";
        }
        if (type.contains("PRAYER")) return "Prayer platform/altar, storage and safe lighting";
        if (type.contains("FLEX")) return "Convertible guest bed, movable seating and storage";
        if (type.contains("MULTIPURPOSE")) return "Flexible seating/activity layout and storage";
        if (type.contains("OPEN_SPACE")) return "Landscape/arrival zone and site drainage";
        return "Furniture, access clearances, lighting and storage to the approved brief";
    }

    private static String normalizedType(RoomGeometry room) {
        return room.type() == null ? "" : room.type().trim().toUpperCase(Locale.ROOT);
    }

    private record RecommendationFacts(
            List<RoomGeometry> rooms,
            int floorCount,
            int bedrooms,
            int bathrooms,
            int balconies,
            int liftStacks,
            int recommendedBedrooms,
            int recommendedAttachedBathrooms,
            int recommendedCommonBathrooms,
            int recommendedBalconies,
            String recommendedLiftProvision,
            int planningFloors,
            int members,
            int seniors,
            boolean accessibleGroundFloor,
            String familySummary,
            double plotArea,
            String roadFacing,
            String city,
            String homeType,
            String finishTier,
            String requestedLiftProvision,
            int requestedBalconies,
            boolean terraceRequested,
            boolean courtyardRequested,
            boolean rainwaterHarvesting,
            int parkingCars,
            List<String> recommendationReasons
    ) {
        static RecommendationFacts from(ProjectSummary project, DrawingCandidate drawing) {
            var details = project.details();
            var rawRooms = drawing.geometry() == null || drawing.geometry().rooms() == null
                    ? List.<RoomGeometry>of() : drawing.geometry().rooms();
            var rooms = rawRooms.stream()
                    .sorted(Comparator.comparingInt((RoomGeometry room) -> floorRank(room.floor()))
                            .thenComparingDouble(RoomGeometry::y)
                            .thenComparingDouble(RoomGeometry::x)
                            .thenComparing(room -> ReportPdfSupport.present(room.id(), "")))
                    .toList();
            var floors = (int) rooms.stream().map(room -> normalizedFloor(room.floor())).distinct().count();
            var bedrooms = (int) rooms.stream().filter(room -> normalizedType(room).contains("BEDROOM")).count();
            var bathrooms = (int) rooms.stream().filter(room -> normalizedType(room).contains("BATH")
                    || normalizedType(room).contains("TOILET")).count();
            var balconies = (int) rooms.stream().filter(room -> normalizedType(room).contains("BALCONY")).count();
            var liftStacks = (int) rooms.stream().filter(room -> normalizedType(room).contains("LIFT"))
                    .map(RecommendationFacts::boundsKey).distinct().count();
            var attached = (int) rooms.stream().filter(room -> normalizedType(room).contains("ATTACHED_BATH"))
                    .count();
            var provenance = drawing.versions() == null ? Map.<String, String>of() : drawing.versions();
            var adults = integer(provenance, "householdAdults", details.family().adults());
            var children = integer(provenance, "householdChildren", details.family().children());
            var seniors = integer(provenance, "householdSeniors", details.family().seniorCitizens());
            var members = integer(provenance, "householdMembers", adults + children + seniors);
            var planningFloors = Math.max(1, integer(provenance, "requestedFloors", details.floors()));
            var plotWidth = validDimension(drawing.geometry() == null ? 0 : drawing.geometry().plotWidth())
                    ? drawing.geometry().plotWidth() : details.plotWidth();
            var plotLength = validDimension(drawing.geometry() == null ? 0 : drawing.geometry().plotLength())
                    ? drawing.geometry().plotLength() : details.plotLength();
            var plotArea = plotWidth * plotLength;
            var roadFacing = provenance.getOrDefault("roadFacing", details.roadFacing().name());
            var city = provenance.getOrDefault("projectCity", details.city());
            var homeType = provenance.getOrDefault("requestedHomeType", details.parameters().homeType());
            var finishTier = provenance.getOrDefault("finishTier", details.category().name());
            var requestedLift = normalizedProvision(provenance.getOrDefault("requestedLiftProvision",
                    details.parameters().liftProvision()));
            var requestedBalconies = integer(provenance, "requestedBalconyCount",
                    details.parameters().balconyCount());
            var terraceRequested = bool(provenance, "requestedTerrace",
                    details.parameters().terraceRequired());
            var courtyardRequested = bool(provenance, "requestedCourtyard",
                    details.parameters().courtyardRequired());
            var accessibleGroundFloor = bool(provenance, "requestedAccessibleGroundFloor",
                    details.parameters().accessibleGroundFloor());
            var rainwater = bool(provenance, "requestedRainwaterHarvesting",
                    details.parameters().rainwaterHarvesting());
            var parkingCars = integer(provenance, "recommendedParkingCars",
                    details.parameters().parkingCars());
            var recommendedBedrooms = integer(provenance, "recommendedBedrooms", bedrooms);
            var recommendedBalconies = recommendedBalconies(planningFloors, recommendedBedrooms, plotArea);
            var recommendedLift = recommendedLiftProvision(planningFloors, seniors, accessibleGroundFloor);
            var reasons = splitReasons(provenance.get("recommendationReasons"));
            if (reasons.isEmpty()) {
                reasons = List.of(
                        members + " permanent household members define the core room brief.",
                        String.format(Locale.ROOT, "%.0f x %.0f ft %s-facing plot; %.0f sq ft total plot area.",
                                plotWidth, plotLength, roadFacing.toLowerCase(Locale.ROOT), plotArea),
                        "The current room schedule is read from the selected persisted geometry, not a generic template.");
            }
            var family = adults + " adults | " + children + " children | " + seniors + " seniors";
            return new RecommendationFacts(rooms, Math.max(1, floors), bedrooms, bathrooms, balconies,
                    liftStacks,
                    recommendedBedrooms,
                    integer(provenance, "recommendedAttachedBathrooms", attached),
                    integer(provenance, "recommendedCommonBathrooms", Math.max(0, bathrooms - attached)),
                    recommendedBalconies, recommendedLift, planningFloors, members, seniors,
                    accessibleGroundFloor, family, plotArea, roadFacing, city, homeType, finishTier, requestedLift,
                    requestedBalconies, terraceRequested, courtyardRequested, rainwater,
                    parkingCars, reasons);
        }

        private static int recommendedBalconies(int floors, int bedrooms, double plotArea) {
            if (floors <= 1) return 0;
            var result = 1;
            if (bedrooms >= 4 && plotArea >= 1_500) result++;
            if (bedrooms >= 6 && floors >= 3 && plotArea >= 2_400) result++;
            return result;
        }

        private static String recommendedLiftProvision(int floors, int seniors,
                boolean accessibleGroundFloor) {
            if (floors <= 1) return "NONE";
            return floors >= 3 || seniors > 0 || accessibleGroundFloor ? "FUTURE_SHAFT" : "NONE";
        }

        private static int integer(Map<String, String> values, String key, int fallback) {
            try {
                return Integer.parseInt(values.getOrDefault(key, String.valueOf(fallback)));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private static boolean bool(Map<String, String> values, String key, boolean fallback) {
            var value = values.get(key);
            return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
        }

        private static boolean validDimension(double value) {
            return Double.isFinite(value) && value > 0;
        }

        private static String normalizedProvision(String value) {
            return value == null || value.isBlank() ? "NONE" : value.trim().toUpperCase(Locale.ROOT);
        }

        private static List<String> splitReasons(String value) {
            if (value == null || value.isBlank()) return List.of();
            return List.of(value.split("\\s*\\|\\s*")).stream()
                    .filter(item -> !item.isBlank()).toList();
        }

        private static String boundsKey(RoomGeometry room) {
            return String.format(Locale.ROOT, "%.2f|%.2f|%.2f|%.2f",
                    room.x(), room.y(), room.width(), room.length());
        }

        private static int floorRank(String floor) {
            return switch (normalizedFloor(floor)) {
                case "GROUND" -> 0;
                case "FIRST" -> 1;
                case "SECOND" -> 2;
                default -> 100;
            };
        }

        private static String normalizedFloor(String floor) {
            return floor == null || floor.isBlank() ? "GROUND" : floor.trim().toUpperCase(Locale.ROOT);
        }
    }
}

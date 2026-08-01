package com.avas.platform.project;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.avas.platform.project.ProjectModels.*;

/**
 * Native deterministic geometry engine. Keeping this inside the platform API makes layouts,
 * validation and workflow versioning one transactional backend concern.
 */
@Component
class GeometryEngine {
    private static final List<Strategy> STRATEGIES = List.of(
            new Strategy("BUDGET_OPTIMIZED", "Efficient Courtyard", .43, .46, .92,
                    88, 82, 95, List.of(
                    "Compact circulation lowers the built-up area.",
                    "Wet areas share a plumbing shaft.",
                    "A regular grid reduces construction complexity.")),
            new Strategy("BALANCED", "Garden Threshold", .38, .46, 1.0,
                    94, 91, 91, List.of(
                    "The senior suite is on the ground floor.",
                    "Kitchen and utility are directly connected.",
                    "The stair can support future independent access.")),
            new Strategy("LIFESTYLE_OPTIMIZED", "Lightwell House", .34, .42, 1.07,
                    91, 96, 86, List.of(
                    "A central lightwell improves daylight.",
                    "Shared spaces are enlarged for regular guests.",
                    "A separate office protects private space."))
    );

    List<DrawingCandidate> generate(String projectId, int version, BasicDetailsRequest details,
            Recommendation recommendation, Map<String, String> versions) {
        if (details.plotWidth() < 10 || details.plotLength() < 10) {
            throw new IllegalArgumentException("Plot dimensions must each be at least 10 feet");
        }

        var candidates = new ArrayList<DrawingCandidate>();
        for (int index = 0; index < STRATEGIES.size(); index++) {
            var strategy = STRATEGIES.get(index);
            var rooms = packRooms(details.plotWidth(), details.plotLength(), strategy);
            var violations = validate(details.plotWidth(), details.plotLength(), rooms);
            var reviewRequired = details.plotWidth() < 20 || !violations.isEmpty();
            var footprint = rooms.stream().mapToDouble(RoomGeometry::area).sum();
            var builtUpArea = (int) Math.round(footprint * details.floors() * strategy.areaFactor());

            candidates.add(new DrawingCandidate(
                    "drawing-" + projectId + "-v" + version + "-" + (index + 1),
                    projectId,
                    version,
                    strategy.key(),
                    strategy.name(),
                    builtUpArea,
                    recommendation.estimatedCostLow(),
                    recommendation.estimatedCostHigh(),
                    strategy.vastuScore(),
                    strategy.naturalLightScore(),
                    strategy.spaceEfficiencyScore(),
                    92 - index,
                    new GeometryDocument("FEET", details.plotWidth(), details.plotLength(), rooms, List.of(), List.of()),
                    violations,
                    List.of(
                            "Verify setbacks against the applicable local authority release.",
                            "A licensed structural engineer must approve the final grid."),
                    strategy.explanations(),
                    new LinkedHashMap<>(versions),
                    reviewRequired ? "EXPERT_REVIEW" : "SUCCESS",
                    false,
                    Instant.now()));
        }
        return List.copyOf(candidates);
    }

    List<String> validate(double plotWidth, double plotLength, List<RoomGeometry> rooms) {
        var violations = new ArrayList<String>();
        for (var room : rooms) {
            if (room.width() <= 0 || room.length() <= 0) {
                violations.add(room.id() + " has unusable geometry");
            }
            if (room.x() < 0 || room.y() < 0 || room.x() + room.width() > plotWidth + .01
                    || room.y() + room.length() > plotLength + .01) {
                violations.add(room.id() + " escapes the plot boundary");
            }
        }
        for (int left = 0; left < rooms.size(); left++) {
            for (int right = left + 1; right < rooms.size(); right++) {
                if (overlaps(rooms.get(left), rooms.get(right))) {
                    violations.add(rooms.get(left).id() + " overlaps " + rooms.get(right).id());
                }
            }
        }
        return List.copyOf(violations);
    }

    private List<RoomGeometry> packRooms(double width, double length, Strategy strategy) {
        var innerWidth = width * .82;
        var innerLength = length * .76;
        var startX = width * .09;
        var startY = length * .12;
        var leftWidth = innerWidth * strategy.columnSplit();
        var rightWidth = innerWidth - leftWidth;
        var topLength = innerLength * strategy.rowSplit();
        var bottomLength = innerLength - topLength;

        return List.of(
                room("R1", "PARKING", startX, startY, leftWidth, topLength),
                room("R2", "LIVING_ROOM", startX + leftWidth, startY, rightWidth, topLength),
                room("R3", "SENIOR_BEDROOM", startX, startY + topLength, leftWidth, bottomLength),
                room("R4", "DINING", startX + leftWidth, startY + topLength, rightWidth * .52, bottomLength * .58),
                room("R5", "KITCHEN", startX + leftWidth + rightWidth * .52, startY + topLength, rightWidth * .48, bottomLength * .58),
                room("R6", "STAIRCASE", startX + leftWidth, startY + topLength + bottomLength * .58, rightWidth * .38, bottomLength * .42),
                room("R7", "BATHROOM", startX + leftWidth + rightWidth * .38, startY + topLength + bottomLength * .58, rightWidth * .24, bottomLength * .42),
                room("R8", "UTILITY", startX + leftWidth + rightWidth * .62, startY + topLength + bottomLength * .58, rightWidth * .38, bottomLength * .42));
    }

    private boolean overlaps(RoomGeometry left, RoomGeometry right) {
        var epsilon = .01;
        return left.x() < right.x() + right.width() - epsilon
                && left.x() + left.width() > right.x() + epsilon
                && left.y() < right.y() + right.length() - epsilon
                && left.y() + left.length() > right.y() + epsilon;
    }

    private RoomGeometry room(String id, String type, double x, double y, double width, double length) {
        return new RoomGeometry(id, type, round2(x), round2(y), round2(width), round2(length),
                round2(width * length), "GROUND");
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Strategy(String key, String name, double columnSplit, double rowSplit, double areaFactor,
            int vastuScore, int naturalLightScore, int spaceEfficiencyScore, List<String> explanations) {}
}

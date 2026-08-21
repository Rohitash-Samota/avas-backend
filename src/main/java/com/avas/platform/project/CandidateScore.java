package com.avas.platform.project;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a generated layout is actually like, measured from the layout itself.
 *
 * <p>These numbers used to be constants. Each strategy carried a fixed vastu, daylight and
 * efficiency score in the engine's strategy table, so every project ever generated ranked its three
 * options 92, 91, 90 in the same order, whatever the plot, the household or the drawing turned out
 * to be. A customer choosing between options was reading the strategy's reputation rather than
 * anything about the home in front of them.</p>
 *
 * <p>Every component here is derived from the placed rooms, the doors between them and the windows
 * in their walls. Nothing is asserted. A component that cannot be measured on a particular plan —
 * no bedrooms on a floor, no windows at all under full plot usage — reports the neutral 50 rather
 * than a flattering default, so an unmeasurable plan never outranks a measured one by omission.</p>
 *
 * <p>Scores are 0-100 and are deliberately soft: they rank options against each other. They never
 * excuse a hard violation, which is why {@link #overall(Map, int)} subtracts for those separately
 * and no weighting can restore the loss.</p>
 */
record CandidateScore(
        int spaceEfficiency,
        int naturalLight,
        int vastu,
        int adjacency,
        int privacy,
        int roomUsability,
        int circulation,
        List<String> reasons
) {
    /** Rooms a family lives in, which are what daylight and privacy are judged against. */
    private static final Set<String> HABITABLE = Set.of(
            "LIVING_ROOM", "DINING", "KITCHEN", "FAMILY_LOUNGE", "BEDROOM", "MASTER_BEDROOM",
            "SENIOR_BEDROOM", "FLEX_ROOM", "FLEX_GUEST_ROOM", "MULTIPURPOSE_ROOM", "STUDY",
            "HOME_OFFICE", "PRAYER_ROOM");

    /** Spaces that are circulation rather than accommodation. */
    private static final Set<String> CIRCULATION = Set.of("CORRIDOR", "STAIRCASE", "LIFT_SHAFT");

    /**
     * Pairs a plan is better for having next to each other, and how much it matters.
     *
     * <p>Straight out of how these homes are used: meals travel from the kitchen to the dining
     * table, a bathroom attached to a bedroom has to touch the bedroom it is attached to, and
     * washing goes from the kitchen to the utility. Scored on a shared wall, because that is what
     * "adjacent" means to somebody carrying a dish.</p>
     */
    private static final List<Adjacency> WANTED = List.of(
            new Adjacency("KITCHEN", "DINING", 3),
            new Adjacency("LIVING_ROOM", "DINING", 3),
            new Adjacency("KITCHEN", "UTILITY", 2),
            new Adjacency("MASTER_BEDROOM", "ATTACHED_BATHROOM", 3),
            new Adjacency("SENIOR_BEDROOM", "BATHROOM", 2),
            new Adjacency("LIVING_ROOM", "CORRIDOR", 1));

    private record Adjacency(String first, String second, int weight) {}

    /** Two rooms count as adjacent when their walls actually meet over a usable length. */
    private static final double SHARED_WALL_MINIMUM = 2.5d;

    static CandidateScore measure(List<RoomGeometry> rooms, List<Map<String, Object>> windows,
            BuildableEnvelope envelope, Facing roadFacing, int floors) {
        var reasons = new ArrayList<String>();
        var efficiency = spaceEfficiency(rooms, envelope, floors, reasons);
        var light = naturalLight(rooms, windows, reasons);
        var vastu = vastu(rooms, envelope, roadFacing, reasons);
        var adjacency = adjacency(rooms, reasons);
        var privacy = privacy(rooms, reasons);
        var usability = roomUsability(rooms, reasons);
        var circulation = circulation(rooms, reasons);
        return new CandidateScore(efficiency, light, vastu, adjacency, privacy, usability,
                circulation, List.copyOf(reasons));
    }

    /**
     * The weighted overall score, with hard violations taken off the top.
     *
     * <p>The weights are the option's own — the five the parameter optimizer returns and which
     * nothing previously read. A customer who asked for daylight gets an option ranked on daylight.
     * Violations are subtracted after weighting so no combination of good soft scores can bury one:
     * a layout with a room outside the buildable line is not a well-rounded design with a caveat.</p>
     */
    int overall(Map<String, Double> weights, int hardViolations) {
        var budget = weight(weights, "budget", .2);
        var functionality = weight(weights, "functionality", .3);
        var daylight = weight(weights, "daylight", .2);
        var accessibility = weight(weights, "accessibility", .15);
        var future = weight(weights, "futureReadiness", .15);
        var total = budget + functionality + daylight + accessibility + future;
        if (total <= 0) return 0;

        // Each weight is spent on the components it is actually about.
        var weighted = budget * spaceEfficiency
                + functionality * ((adjacency + roomUsability + circulation) / 3d)
                + daylight * ((naturalLight + vastu) / 2d)
                + accessibility * ((privacy + circulation) / 2d)
                + future * ((spaceEfficiency + roomUsability) / 2d);
        var soft = weighted / total;
        return (int) Math.round(Math.max(0, Math.min(100, soft - hardViolations * 12d)));
    }

    private static double weight(Map<String, Double> weights, String key, double fallback) {
        if (weights == null) return fallback;
        var value = weights.get(key);
        return value == null || value < 0 ? fallback : value;
    }

    /** How much of the ground a storey could be planned on the layout actually occupies. */
    private static int spaceEfficiency(List<RoomGeometry> rooms, BuildableEnvelope envelope,
            int floors, List<String> reasons) {
        if (envelope == null || envelope.plannableArea() <= 0) return 50;
        var enclosed = rooms.stream()
                .filter(room -> !RoomSpec.isOutdoor(room.type()))
                .mapToDouble(RoomGeometry::area).sum();
        var available = envelope.plannableArea() * Math.max(1, floors);
        var used = enclosed / available;
        reasons.add(String.format(Locale.ROOT,
                "Uses %.0f%% of the %,.0f sq ft this plot can be planned on across %d floor(s).",
                Math.min(1, used) * 100, available, Math.max(1, floors)));
        // Full occupancy is the target; overshoot is a packing artefact rather than extra value.
        return band(used >= 1 ? 100 : used * 100);
    }

    /** The share of rooms a family lives in that have a window. */
    private static int naturalLight(List<RoomGeometry> rooms, List<Map<String, Object>> windows,
            List<String> reasons) {
        var habitable = rooms.stream().filter(room -> HABITABLE.contains(room.type())).toList();
        if (habitable.isEmpty()) return 50;
        var lit = new LinkedHashSet<String>();
        if (windows != null) {
            for (var window : windows) lit.add(String.valueOf(window.get("roomId")));
        }
        var withLight = habitable.stream().filter(room -> lit.contains(room.id())).count();
        var share = withLight / (double) habitable.size();
        var dark = habitable.size() - withLight;
        reasons.add(dark == 0
                ? "Every habitable room has a window."
                : dark + " habitable room" + (dark == 1 ? "" : "s") + " have no window and need a "
                        + "courtyard or lightwell before this is built.");
        return band(share * 100);
    }

    /**
     * How well the plan sits with the directional conventions these customers plan by.
     *
     * <p>Measured against the room's own position in the plot rather than asserted: the kitchen
     * belongs south-east, the master bedroom south-west, a prayer room north-east. This is a
     * planning convention widely followed in this market, not a building rule, and it is scored
     * rather than enforced — a plan that ignores it is still a valid plan.</p>
     */
    private static int vastu(List<RoomGeometry> rooms, BuildableEnvelope envelope, Facing roadFacing,
            List<String> reasons) {
        if (envelope == null || envelope.buildableOutline().size() < 3) return 50;
        var xs = envelope.buildableOutline().stream().mapToDouble(PlotVertex::x);
        var ys = envelope.buildableOutline().stream().mapToDouble(PlotVertex::y);
        var minX = xs.min().orElse(0);
        var maxX = envelope.buildableOutline().stream().mapToDouble(PlotVertex::x).max().orElse(1);
        var minY = ys.min().orElse(0);
        var maxY = envelope.buildableOutline().stream().mapToDouble(PlotVertex::y).max().orElse(1);
        var width = Math.max(.01, maxX - minX);
        var length = Math.max(.01, maxY - minY);

        // North is maximum y and east is maximum x, matching the plot boundary's own convention.
        record Preference(String type, double east, double north, int weight) {}
        var preferences = List.of(
                new Preference("KITCHEN", 1, 0, 3),
                new Preference("MASTER_BEDROOM", 0, 0, 2),
                new Preference("PRAYER_ROOM", 1, 1, 2),
                new Preference("LIVING_ROOM", 1, 1, 1),
                new Preference("STAIRCASE", 0, 1, 1));

        var scored = 0d;
        var possible = 0d;
        var honoured = new ArrayList<String>();
        for (var preference : preferences) {
            var room = rooms.stream()
                    .filter(candidate -> "GROUND".equalsIgnoreCase(candidate.floor()))
                    .filter(candidate -> preference.type().equals(candidate.type()))
                    .findFirst().orElse(null);
            if (room == null) continue;
            possible += preference.weight();
            var east = (room.x() + room.width() / 2 - minX) / width;
            var north = (room.y() + room.length() / 2 - minY) / length;
            // 1 when the room sits exactly in its preferred corner, 0 in the opposite one.
            var fit = 1 - (Math.abs(east - preference.east()) + Math.abs(north - preference.north())) / 2;
            scored += preference.weight() * Math.max(0, fit);
            if (fit > .6) honoured.add(room.type().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        if (possible <= 0) return 50;
        if (!honoured.isEmpty()) {
            reasons.add("Directional convention is followed for the " + String.join(", ", honoured)
                    + " on a " + roadFacing.name().toLowerCase(Locale.ROOT) + "-facing plot.");
        }
        return band(scored / possible * 100);
    }

    /** How many of the relationships a home is used through are actually built as shared walls. */
    private static int adjacency(List<RoomGeometry> rooms, List<String> reasons) {
        var scored = 0d;
        var possible = 0d;
        var met = new ArrayList<String>();
        var missed = new ArrayList<String>();
        for (var wanted : WANTED) {
            var first = rooms.stream().filter(room -> wanted.first().equals(room.type())).toList();
            var second = rooms.stream().filter(room -> wanted.second().equals(room.type())).toList();
            if (first.isEmpty() || second.isEmpty()) continue;
            possible += wanted.weight();
            var touching = first.stream().anyMatch(left -> second.stream()
                    .anyMatch(right -> sharesWall(left, right)));
            var label = label(wanted.first()) + " and " + label(wanted.second());
            if (touching) {
                scored += wanted.weight();
                met.add(label);
            } else {
                missed.add(label);
            }
        }
        if (possible <= 0) return 50;
        if (!met.isEmpty()) reasons.add("Directly connected: " + String.join("; ", met) + ".");
        if (!missed.isEmpty()) reasons.add("Not directly connected: " + String.join("; ", missed) + ".");
        return band(scored / possible * 100);
    }

    /**
     * Whether the private rooms are actually private.
     *
     * <p>Two things are measured. A bedroom that shares a wall with the living room or dining room
     * carries the noise of the whole house, and a bedroom that has to be crossed to reach another
     * room is not a bedroom anybody can close the door of. Both are the difference between a plan
     * that validates and a plan a family can live in.</p>
     */
    private static int privacy(List<RoomGeometry> rooms, List<String> reasons) {
        var bedrooms = rooms.stream().filter(room -> RoomSpec.isBedroom(room.type())).toList();
        if (bedrooms.isEmpty()) return 50;
        var publicRooms = rooms.stream()
                .filter(room -> "LIVING_ROOM".equals(room.type()) || "DINING".equals(room.type()))
                .toList();

        var exposed = 0;
        for (var bedroom : bedrooms) {
            if (publicRooms.stream().anyMatch(open -> sharesWall(bedroom, open))) exposed++;
        }
        // A bedroom sandwiched between two other bedrooms on the same strip is a walk-through room.
        var throughRooms = 0;
        for (var bedroom : bedrooms) {
            var neighbours = bedrooms.stream().filter(other -> !other.id().equals(bedroom.id()))
                    .filter(other -> sharesWall(bedroom, other)).count();
            if (neighbours >= 2) throughRooms++;
        }
        var penalty = (exposed + throughRooms) / (double) bedrooms.size();
        reasons.add(exposed == 0
                ? "No bedroom shares a wall with the living or dining room."
                : exposed + " bedroom" + (exposed == 1 ? "" : "s") + " back onto the shared living space.");
        return band((1 - Math.min(1, penalty)) * 100);
    }

    /** The share of rooms drawn at a size their own type is usable at. */
    private static int roomUsability(List<RoomGeometry> rooms, List<String> reasons) {
        if (rooms.isEmpty()) return 50;
        var offenders = new ArrayList<String>();
        var good = 0;
        for (var room : rooms) {
            var spec = RoomSpec.of(room.type());
            var shortSide = Math.min(room.width(), room.length());
            var longSide = Math.max(room.width(), room.length());
            var fits = shortSide + .05 >= spec.minShortSide() && longSide + .05 >= spec.minLongSide()
                    && room.area() + .5 >= spec.minArea() && room.area() <= spec.maxArea() + .5;
            if (fits) {
                good++;
            } else if (offenders.size() < 3) {
                offenders.add(String.format(Locale.ROOT, "%s at %.1f x %.1f ft",
                        label(room.type()), room.width(), room.length()));
            }
        }
        if (!offenders.isEmpty()) {
            reasons.add("Outside the usual size for their type: " + String.join(", ", offenders) + ".");
        }
        return band(good / (double) rooms.size() * 100);
    }

    /** How much of the floor goes on getting between rooms rather than being in them. */
    private static int circulation(List<RoomGeometry> rooms, List<String> reasons) {
        var enclosed = rooms.stream().filter(room -> !RoomSpec.isOutdoor(room.type()))
                .mapToDouble(RoomGeometry::area).sum();
        if (enclosed <= 0) return 50;
        var passage = rooms.stream().filter(room -> CIRCULATION.contains(room.type()))
                .mapToDouble(RoomGeometry::area).sum();
        var share = passage / enclosed;
        reasons.add(String.format(Locale.ROOT,
                "%.1f%% of the enclosed area is passage, stair and shaft.", share * 100));
        // Around a tenth is normal for a home with a stair; nothing is gained below about 6%,
        // because a plan with no circulation at all is one where rooms open into each other.
        if (share <= .06) return 88;
        if (share >= .28) return 0;
        return band((1 - (share - .06) / .22) * 100);
    }

    /** True when two rooms on the same storey have walls that meet over a usable length. */
    private static boolean sharesWall(RoomGeometry left, RoomGeometry right) {
        if (left.floor() == null || !left.floor().equals(right.floor())) return false;
        var gap = .35d;
        var verticalTouch = Math.abs(left.x() + left.width() - right.x()) < gap
                || Math.abs(right.x() + right.width() - left.x()) < gap;
        var horizontalTouch = Math.abs(left.y() + left.length() - right.y()) < gap
                || Math.abs(right.y() + right.length() - left.y()) < gap;
        if (verticalTouch) {
            return overlap(left.y(), left.y() + left.length(), right.y(), right.y() + right.length())
                    >= SHARED_WALL_MINIMUM;
        }
        if (horizontalTouch) {
            return overlap(left.x(), left.x() + left.width(), right.x(), right.x() + right.width())
                    >= SHARED_WALL_MINIMUM;
        }
        return false;
    }

    private static double overlap(double fromA, double toA, double fromB, double toB) {
        return Math.max(0, Math.min(toA, toB) - Math.max(fromA, fromB));
    }

    private static String label(String type) {
        return type == null ? "room" : type.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static int band(double value) {
        return (int) Math.round(Math.max(0, Math.min(100, value)));
    }
}

package com.avas.platform.project;

import java.util.Locale;
import java.util.Map;

/**
 * The dimensions a room type is actually usable at, in planning feet.
 *
 * <p>A layout engine that only divides area produces rooms nobody can live in: a bedroom twenty-five
 * feet deep, a bathroom the size of a bedroom, a lift shaft two feet wide. Every space therefore
 * carries the shortest side it works at, the longest side beyond which it stops reading as that room,
 * and the area band a builder would recognise for it.</p>
 *
 * <p>These are planning conventions for Indian low-rise housing, not statutory minima. The National
 * Building Code and the local development authority control the values that must be met, and a
 * licensed professional confirms them; AVAS only refuses to draw a space it knows is unusable.</p>
 *
 * @param minShortSide shortest usable dimension, whichever way the room ends up oriented
 * @param minLongSide  shortest usable dimension of the *other* side, so a square-ish room still works
 * @param preferredArea area the planner aims for before any surplus or shortfall is shared out
 * @param minArea      area below which the space stops being worth drawing
 * @param maxArea      area above which surplus should go somewhere else instead
 * @param priority     1 is structural and can never be dropped; 5 is dropped first
 */
record RoomSpec(
        double minShortSide,
        double minLongSide,
        double preferredArea,
        double minArea,
        double maxArea,
        int priority
) {
    /** Spaces that are floor area a family cannot be charged for as enclosed construction. */
    static final java.util.Set<String> OUTDOOR_TYPES =
            java.util.Set.of("PARKING", "COURTYARD_PARKING", "COURTYARD", "OPEN_SPACE", "TERRACE");

    /** Circulation. Named separately because it is the one space every other room must touch. */
    static final String CORRIDOR = "CORRIDOR";

    private static final RoomSpec FALLBACK = new RoomSpec(7d, 8d, 90d, 56d, 200d, 4);

    private static final Map<String, RoomSpec> CATALOGUE = Map.ofEntries(
            // Public zone.
            Map.entry("LIVING_ROOM", new RoomSpec(10d, 13d, 230d, 130d, 420d, 1)),
            Map.entry("DINING", new RoomSpec(9d, 10d, 140d, 90d, 260d, 2)),
            Map.entry("FAMILY_LOUNGE", new RoomSpec(10d, 11d, 170d, 110d, 320d, 3)),
            Map.entry("PORCH", new RoomSpec(6d, 8d, 90d, 48d, 220d, 4)),
            // Service zone.
            Map.entry("KITCHEN", new RoomSpec(7.5d, 9d, 115d, 68d, 210d, 1)),
            Map.entry("UTILITY", new RoomSpec(5d, 6d, 48d, 30d, 96d, 4)),
            Map.entry("LAUNDRY", new RoomSpec(5d, 6d, 45d, 30d, 90d, 5)),
            Map.entry("STORE", new RoomSpec(4.5d, 5.5d, 40d, 25d, 90d, 5)),
            // Private zone. A bedroom under about 100 sq ft cannot hold a double bed and a wardrobe.
            Map.entry("MASTER_BEDROOM", new RoomSpec(10d, 12d, 185d, 120d, 320d, 1)),
            Map.entry("SENIOR_BEDROOM", new RoomSpec(10d, 11d, 155d, 110d, 260d, 1)),
            // 8.5 ft clear is the narrowest a single bed, a walkway and a wardrobe line up in, and is
            // what dense plotted housing is actually built to; below it the room stops being one.
            Map.entry("BEDROOM", new RoomSpec(8.5d, 10d, 140d, 90d, 240d, 1)),
            Map.entry("FLEX_ROOM", new RoomSpec(9d, 10d, 130d, 90d, 230d, 4)),
            Map.entry("MULTIPURPOSE_ROOM", new RoomSpec(9d, 10d, 135d, 90d, 260d, 4)),
            Map.entry("STUDY", new RoomSpec(7.5d, 9d, 95d, 60d, 170d, 4)),
            Map.entry("HOME_OFFICE", new RoomSpec(7.5d, 9d, 95d, 60d, 170d, 4)),
            Map.entry("DRESSING_ROOM", new RoomSpec(5d, 6d, 50d, 32d, 90d, 5)),
            Map.entry("PRAYER_ROOM", new RoomSpec(4.5d, 5d, 32d, 22d, 64d, 5)),
            // Wet areas. A 4.5 ft clear width is the narrowest a WC, basin and shower line up in.
            Map.entry("ATTACHED_BATHROOM", new RoomSpec(4.5d, 6.5d, 42d, 30d, 80d, 2)),
            Map.entry("BATHROOM", new RoomSpec(4.5d, 6d, 38d, 27d, 66d, 2)),
            Map.entry("TOILET", new RoomSpec(3.5d, 5d, 20d, 16d, 36d, 3)),
            // Vertical circulation. Sized per staircase type by the planner, so these are floors.
            Map.entry("STAIRCASE", new RoomSpec(3.5d, 9.5d, 84d, 45d, 160d, 1)),
            Map.entry("LIFT_SHAFT", new RoomSpec(5d, 5.5d, 30d, 27.5d, 48d, 1)),
            Map.entry(CORRIDOR, new RoomSpec(3.25d, 6d, 60d, 24d, 340d, 1)),
            // Outdoor. A balcony under four feet deep holds a person and nothing else.
            Map.entry("BALCONY", new RoomSpec(4d, 6d, 50d, 28d, 120d, 3)),
            Map.entry("TERRACE", new RoomSpec(6d, 8d, 130d, 60d, 480d, 3)),
            Map.entry("COURTYARD", new RoomSpec(6d, 8d, 110d, 56d, 320d, 3)),
            Map.entry("COURTYARD_PARKING", new RoomSpec(8.5d, 16d, 160d, 136d, 420d, 2)),
            // One car needs 8.5 x 16 ft clear; anything less is a strip, not a bay.
            Map.entry("PARKING", new RoomSpec(8.5d, 16d, 160d, 136d, 420d, 2)));

    /** Spaces a plan legitimately draws long and thin: circulation, shafts and edge spaces. */
    private static final java.util.Set<RoomSpec> SLENDER = java.util.Set.of(
            CATALOGUE.get(CORRIDOR), CATALOGUE.get("STAIRCASE"), CATALOGUE.get("LIFT_SHAFT"),
            CATALOGUE.get("BALCONY"), CATALOGUE.get("TERRACE"));

    static RoomSpec of(String type) {
        return CATALOGUE.getOrDefault(type == null ? "" : type.toUpperCase(Locale.ROOT), FALLBACK);
    }

    static boolean isOutdoor(String type) {
        return OUTDOOR_TYPES.contains(type);
    }

    /** How many times its own depth this space may run before it stops reading as that space. */
    private double proportionLimit() {
        return SLENDER.contains(this) ? 12d : 3.6d;
    }

    static boolean isBedroom(String type) {
        return type != null && type.endsWith("BEDROOM");
    }

    /**
     * The shortest run this space needs when its other side is already fixed at {@code across} feet.
     *
     * <p>A room is usable either way round, so a strip wide enough for the long side only needs the
     * short side of depth, and a narrow strip needs the long side. Returning {@code NaN} says the
     * strip is too narrow for this space at any depth.</p>
     */
    double minRun(double across) {
        if (across + .01 < minShortSide) return Double.NaN;
        return across + .01 >= minLongSide ? minShortSide : minLongSide;
    }

    /**
     * The longest run worth giving this space in a strip of {@code across} feet.
     *
     * <p>Bounded by proportion as well as by area. A room four times longer than it is wide cannot be
     * furnished as that room however much floor it has: a nine-by-forty parking bay is a driveway and
     * a nine-by-forty bedroom is a corridor with a bed in it. Circulation and shafts are the spaces
     * that legitimately are long and thin, so they carry a far looser bound.</p>
     */
    double maxRun(double across) {
        var proportion = across * proportionLimit();
        return Math.max(minRun(across), Math.min(maxArea / Math.max(.01, across), proportion));
    }

    /** The run this space would like in a strip of {@code across} feet. */
    double preferredRun(double across) {
        var run = preferredArea / Math.max(.01, across);
        return Math.min(maxRun(across), Math.max(minRun(across), run));
    }
}

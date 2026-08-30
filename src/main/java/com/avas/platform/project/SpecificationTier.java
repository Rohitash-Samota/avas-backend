package com.avas.platform.project;

import java.util.List;
import java.util.Locale;

/**
 * What a finish tier actually buys in the plan, rather than only what it costs per square foot.
 *
 * <p>The tier a customer chooses reached exactly two places: the rate the estimate multiplied by,
 * and whether a lawn was named on the site plan. Everything a builder means by "luxury" — that you
 * arrive through a covered sit-out into a foyer rather than straight into the living room, that
 * guests have a WC they reach without walking past a bedroom, that the master has a dressing room
 * off its bathroom, that the utility is a room and not a corner of the kitchen — changed nothing.
 * Two families on the same plot with the same household and the same floor count were given
 * identical drawings and charged different amounts for them.</p>
 *
 * <p>So the tier is asked twice in the pipeline. {@link PlanningParameterSet} asks it which spaces
 * belong in the programme, so the targets a customer is shown name them. {@link FloorPlanner} asks
 * it the same question when it lays the strip out, and asks it again for the order surplus area is
 * spent in — which is the difference between a large plot buying a dressing room and a home office,
 * and the same plot buying a third store cupboard because that was first on a fixed list.</p>
 *
 * <p>These are conventions for Indian low-rise housing, not statutory content. Nothing here decides
 * whether a space is permitted or how it must be built; it decides what a plan at this tier is
 * expected to contain, and a licensed professional remains the authority on the rest.</p>
 */
enum SpecificationTier {
    /**
     * Every space the household needs and nothing it does not: the budget is in the shell.
     *
     * <p>You enter the living room from the door. The utility is a strip off the kitchen. There is
     * one common bathroom per floor and the bedrooms that earn an ensuite have one.</p>
     */
    STANDARD(1.00d, 1, false, false, false, false, false, false,
            List.of("STORE", "STUDY", "MULTIPURPOSE_ROOM", "PRAYER_ROOM", "FLEX_ROOM")),

    /**
     * The arrival sequence and the guest WC that separate a planned house from a sized one.
     *
     * <p>A foyer means the front door does not open onto the sofa, and a WC off the living room
     * means a visitor never walks through the private half of the plan. Both are small rooms and
     * both are what a customer is actually buying when they move up a tier.</p>
     */
    PREMIUM(1.08d, 2, true, false, true, true, false, false,
            List.of("STUDY", "STORE", "DRESSING_ROOM", "PRAYER_ROOM", "MULTIPURPOSE_ROOM", "HOME_OFFICE")),

    /**
     * The full arrival sequence, a dressed master suite and service rooms that are rooms.
     *
     * <p>Covered verandah into a foyer, guest WC off the living room, dressing room between the
     * master bedroom and its bathroom, a store and a laundry that are not the utility doing three
     * jobs, and a dedicated work room. Surplus goes to the suite and the study before it goes
     * anywhere else, because that is where a family at this tier feels it.</p>
     */
    LUXURY(1.18d, 2, true, true, true, true, true, true,
            List.of("DRESSING_ROOM", "HOME_OFFICE", "PRAYER_ROOM", "STUDY", "MULTIPURPOSE_ROOM", "STORE"));

    private final double generosity;
    private final int minimumParkingBays;
    private final boolean entranceFoyer;
    private final boolean coveredVerandah;
    private final boolean guestToilet;
    private final boolean dedicatedStore;
    private final boolean masterDressingRoom;
    private final boolean separateLaundry;
    private final List<String> surplusOrder;

    SpecificationTier(double generosity, int minimumParkingBays, boolean entranceFoyer,
            boolean coveredVerandah, boolean guestToilet, boolean dedicatedStore,
            boolean masterDressingRoom, boolean separateLaundry, List<String> surplusOrder) {
        this.generosity = generosity;
        this.minimumParkingBays = minimumParkingBays;
        this.entranceFoyer = entranceFoyer;
        this.coveredVerandah = coveredVerandah;
        this.guestToilet = guestToilet;
        this.dedicatedStore = dedicatedStore;
        this.masterDressingRoom = masterDressingRoom;
        this.separateLaundry = separateLaundry;
        this.surplusOrder = List.copyOf(surplusOrder);
    }

    /**
     * The tier a recommendation was calculated at.
     *
     * <p>{@link Recommendation#category()} is already the resolved string — a project that chose
     * {@link Category#NOT_SURE} has had its tier derived from budget per square foot by the time a
     * recommendation exists — so this is a read of a decision, never a second copy of it.</p>
     */
    static SpecificationTier of(Recommendation recommendation) {
        return recommendation == null ? STANDARD : of(recommendation.category());
    }

    /** The tier named by a stored string, defaulting to {@link #STANDARD} for anything unknown. */
    static SpecificationTier of(String category) {
        if (category == null || category.isBlank()) return STANDARD;
        return switch (category.trim().toUpperCase(Locale.ROOT)) {
            case "LUXURY" -> LUXURY;
            case "PREMIUM" -> PREMIUM;
            default -> STANDARD;
        };
    }

    /**
     * The tier a brief implies before any recommendation exists.
     *
     * <p>Mirrors the rule {@code ProjectService} applies when a customer selected
     * {@link Category#NOT_SURE}: the budget per square foot decides, because a family who has not
     * chosen a finish has still chosen an amount of money.</p>
     */
    static SpecificationTier of(Category category, long budget, double builtUpAreaSqFt) {
        if (category != null && category != Category.NOT_SURE) return of(category.name());
        var rate = budget / Math.max(1d, builtUpAreaSqFt);
        return rate >= 3_000 ? LUXURY : rate >= 2_200 ? PREMIUM : STANDARD;
    }

    /** How much more generously this tier sizes a room than the type's own preferred area. */
    double generosity() {
        return generosity;
    }

    /** Bays a plan at this tier should carry even when the household asked for fewer. */
    int minimumParkingBays(int requested) {
        return Math.max(requested, minimumParkingBays);
    }

    /**
     * True when each child is planned a room rather than a room for each pair of them.
     *
     * <p>Sharing is the right assumption for a household buying the shell and the wrong one above
     * it: a family paying premium or luxury rates for three thousand square feet is not buying two
     * children a bunk bed. Derived rather than declared because it follows the tier exactly, and the
     * constructor above is already long enough to hide a transposed flag.</p>
     */
    boolean plansRoomPerChild() {
        return this != STANDARD;
    }

    /**
     * True when the plan carries one bedroom beyond the household.
     *
     * <p>The ground-floor guest room a luxury duplex is built with — kept for visiting parents, and
     * for the room a family grows into. Distinct from the flex room a brief naming regular guests
     * already earns, which is not a bedroom and does not change the count.</p>
     */
    boolean plansSpareBedroom() {
        return this == LUXURY;
    }

    /** True when the front door opens into a hall rather than straight into the living room. */
    boolean entranceFoyer() {
        return entranceFoyer;
    }

    /** True when a covered sit-out is planned in front of that hall. */
    boolean coveredVerandah() {
        return coveredVerandah;
    }

    /** True when a visitor has a WC off the public half of the ground floor. */
    boolean guestToilet() {
        return guestToilet;
    }

    /** True when storage is a room of its own rather than whatever the strip had left. */
    boolean dedicatedStore() {
        return dedicatedStore;
    }

    /** True when the master bathroom is reached through a dressing room. */
    boolean masterDressingRoom() {
        return masterDressingRoom;
    }

    /** True when washing has a room rather than sharing the utility. */
    boolean separateLaundry() {
        return separateLaundry;
    }

    /**
     * Every space surplus floor area may buy, in the order this tier would buy them.
     *
     * <p>The tier's own preference comes first and the rest of the pool follows it. The pool has to
     * stay complete: a strip longer than its programme wants is filled by taking candidates off
     * this list until the rooms can cover it, so a short list does not produce a tidier plan — it
     * produces the last room on the strip stretched to thirty feet to reach the end wall.</p>
     */
    List<String> surplusOrder() {
        var order = new java.util.ArrayList<>(surplusOrder);
        for (var candidate : SURPLUS_POOL) {
            if (!order.contains(candidate)) order.add(candidate);
        }
        return List.copyOf(order);
    }

    /** Every space that can absorb spare floor without pretending to be part of the brief. */
    private static final List<String> SURPLUS_POOL = List.of("STORE", "STUDY", "PRAYER_ROOM",
            "MULTIPURPOSE_ROOM", "LAUNDRY", "DRESSING_ROOM", "FLEX_ROOM", "HOME_OFFICE");

    /**
     * The spaces this tier adds to the ground floor, in the order they are met from the road.
     *
     * <p>Order is the whole point: a verandah the plan puts behind the living room is not a
     * verandah. These are handed to the front band in this sequence, so the drawing reads the way
     * the house is entered.</p>
     */
    List<String> groundEntranceSpaces() {
        var spaces = new java.util.ArrayList<String>(2);
        if (coveredVerandah) spaces.add("VERANDAH");
        if (entranceFoyer) spaces.add("FOYER");
        return List.copyOf(spaces);
    }

    /**
     * The order the arrival sequence gives way in when the frontage cannot carry all of it.
     *
     * <p>The verandah goes first. A hall you can put a bag down in is worth more than a covered
     * step you cannot sit on, and a frontage that keeps the sit-out and loses the hall has kept the
     * decoration and dropped the room.</p>
     */
    static List<String> entranceSacrificeOrder() {
        return List.of("VERANDAH", "FOYER");
    }

    /** The service spaces this tier expects on the ground floor beyond the kitchen and utility. */
    List<String> groundServiceSpaces() {
        var spaces = new java.util.ArrayList<String>(3);
        if (guestToilet) spaces.add("TOILET");
        if (dedicatedStore) spaces.add("STORE");
        if (separateLaundry) spaces.add("LAUNDRY");
        return List.copyOf(spaces);
    }

    /**
     * What the sheet may claim this plan is, so the drawing never advertises what it did not draw.
     *
     * <p>Every entry is a promise the tier makes; the renderer keeps only the ones the placed rooms
     * and the customer's own parameters actually support.</p>
     */
    List<String> featureClaims() {
        var claims = new java.util.ArrayList<String>();
        claims.add("BUILT FOR COMFORT");
        if (entranceFoyer) claims.add("PLANNED ARRIVAL");
        if (guestToilet) claims.add("GUEST WC");
        if (masterDressingRoom) claims.add("DRESSED MASTER SUITE");
        return List.copyOf(claims);
    }

    /** The label a customer reads for this tier. */
    String displayName() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }
}

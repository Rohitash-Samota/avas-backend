package com.avas.platform.project;

import java.util.List;
import java.util.Locale;

/**
 * What the customer asked for in their own words, read once and read the same way everywhere.
 *
 * <p>The wizard offers these as chips and the platform stored every one of them, but exactly one —
 * future expansion — was ever read, by a bare {@code contains("future")} in the middle of building
 * the recommendation. A customer who asked for more bedrooms, a larger living room, extra parking, a
 * rental floor or a vastu-led plan got precisely the same home as one who asked for none of it, and
 * nothing told them so.</p>
 *
 * <p>Matching is deliberately loose. These are free-text strings that arrive from the chip list
 * today and could be typed tomorrow, so each reading looks for the words a customer would actually
 * use rather than an exact chip label.</p>
 */
record LifestylePreferences(
        boolean moreBedrooms,
        boolean largerLiving,
        boolean moreParking,
        boolean rentalFloor,
        boolean vastuLed,
        boolean futureExpansion
) {
    private static final LifestylePreferences NONE =
            new LifestylePreferences(false, false, false, false, false, false);

    static LifestylePreferences of(List<String> preferences) {
        if (preferences == null || preferences.isEmpty()) return NONE;
        var normalized = preferences.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        return new LifestylePreferences(
                any(normalized, "more bedroom", "extra bedroom", "additional bedroom"),
                any(normalized, "larger living", "bigger living", "large living", "spacious living"),
                any(normalized, "more parking", "extra parking", "additional parking", "two car",
                        "second car"),
                any(normalized, "rental", "tenant", "let out", "rent out"),
                any(normalized, "vastu", "vaastu"),
                any(normalized, "future", "expansion", "extend later"));
    }

    private static boolean any(List<String> preferences, String... needles) {
        for (var preference : preferences) {
            for (var needle : needles) {
                if (preference.contains(needle)) return true;
            }
        }
        return false;
    }

    /**
     * Bedrooms this household needs once their stated priorities are taken into account.
     *
     * <p>A spare room is something a family asks for rather than something the headcount decides for
     * them, which is exactly why the household rule alone could never produce one.</p>
     */
    int bedroomsFor(FamilyDetails family) {
        var base = family.bedroomsNeeded();
        return moreBedrooms ? Math.min(6, base + 1) : base;
    }

    /** Parking bays to plan for, honouring a customer who explicitly asked for more. */
    int parkingFor(HomeParameters parameters) {
        return moreParking ? Math.min(6, parameters.parkingCars() + 1) : parameters.parkingCars();
    }

    /** What these choices are worth saying back to the customer on the recommendation. */
    List<String> reasons() {
        var reasons = new java.util.ArrayList<String>();
        if (moreBedrooms) reasons.add("An extra bedroom beyond the household count was requested and is planned");
        if (largerLiving) reasons.add("Shared living space is enlarged ahead of optional rooms");
        if (moreParking) reasons.add("An additional parking bay was requested and is planned");
        if (rentalFloor) {
            reasons.add("A rental floor was requested: the top floor is planned with its own kitchen "
                    + "and bathroom, and a separate entrance and meter must be confirmed with an architect");
        }
        if (vastuLed) reasons.add("Directional convention is weighted more heavily when ranking the options");
        if (futureExpansion) reasons.add("A future-expansion zone is preserved");
        return List.copyOf(reasons);
    }
}

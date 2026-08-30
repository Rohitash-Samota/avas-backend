package com.avas.platform.project;

import java.util.List;

/**
 * The home a household is planned, before anything has been placed on the plot.
 *
 * <p>How many bedrooms, how many of them have a bathroom of their own, how many bays are parked,
 * whether a ground-floor bedroom and a family lounge are owed. It is the substance of the
 * recommendation a customer approves and the programme {@link FloorPlanner} then packs, which is why
 * it is one record rather than six fields threaded through the service.</p>
 *
 * <p>Decided by AVAS AI, which can see the plot's own measured areas, with the rules below as the
 * answer when that service is unreachable. The two are deliberately the same arithmetic: a customer
 * whose recommendation was generated during an outage must not be planned a different house from one
 * generated a minute later, and a fallback that quietly plans a smaller home is the failure mode
 * this platform is most prone to hiding.</p>
 *
 * @param provider which route answered — {@code OPENAI}, {@code ANTHROPIC} or {@code DETERMINISTIC}
 * @param model the model or rule version that produced it, for the recommendation's provenance
 * @param fallbackUsed true when the configured provider was asked and could not answer
 */
public record HouseholdProgramme(
        int bedrooms,
        int attachedBathrooms,
        int commonBathrooms,
        int parkingCars,
        boolean seniorBedroom,
        boolean familyLounge,
        boolean futureExpansion,
        String title,
        List<String> reasons,
        String provider,
        String model,
        boolean fallbackUsed,
        List<String> warnings
) {
    /** The layout engine's ceiling on bedrooms for one low-rise household. Not a planning rule. */
    static final int MAXIMUM_BEDROOMS = 6;

    /**
     * Share of the ground floor plate that stays unenclosed: covered parking, a courtyard, a sit-out.
     *
     * <p>Mirrors {@link FloorPlanner#OUTDOOR_SHARE}. A programme sized against the whole plate is
     * sized against area the drawing was always going to spend outdoors.</p>
     */
    static final double GROUND_OUTDOOR_SHARE = 0.18d;

    /**
     * Enclosed area one more bedroom costs a plan.
     *
     * <p>The room at the area its type is planned to, the bathroom that comes with it in all but the
     * smallest homes, and the passage needed to reach it without walking through another room.
     * Counting a bedroom at its own area alone overstates how many a plate can hold by about a
     * third.</p>
     */
    private static final double BEDROOM_MODULE = 205d;

    public HouseholdProgramme {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        bedrooms = Math.max(1, Math.min(MAXIMUM_BEDROOMS, bedrooms));
        attachedBathrooms = Math.max(0, Math.min(bedrooms, attachedBathrooms));
        commonBathrooms = Math.max(attachedBathrooms == 0 ? 1 : 0, Math.min(4, commonBathrooms));
        parkingCars = Math.max(0, Math.min(6, parkingCars));
    }

    /** True when a model actually decided this programme, rather than the rules standing in. */
    public boolean modelPlanned() {
        return !fallbackUsed && !"DETERMINISTIC".equalsIgnoreCase(provider);
    }

    // ---------------------------------------------------------------------------------------
    // The deterministic answer
    // ---------------------------------------------------------------------------------------

    /**
     * The programme these rules plan, for when AVAS AI cannot be reached.
     *
     * <p>Kept in step with {@code avas_ai.programme} on purpose. Both answer the same question the
     * same way: what this household wants, capped by what the plot can carry, floored by what it
     * needs.</p>
     */
    public static HouseholdProgramme deterministic(BasicDetailsRequest details,
            BuildableEnvelope envelope, String warning) {
        var family = details.family();
        var wanted = LifestylePreferences.of(details.preferences());
        var tier = SpecificationTier.of(resolvedCategory(details, envelope));
        var lounge = family.members() >= 4 && details.floors() > 1;
        var bedrooms = bedroomsFor(details, envelope, tier, lounge, wanted);
        var attached = attachedBathroomsFor(bedrooms);
        return new HouseholdProgramme(bedrooms, attached, 1,
                tier.minimumParkingBays(wanted.parkingFor(details.parameters())),
                family.seniorCitizens() > 0, lounge, wanted.futureExpansion(),
                bedrooms + "-bedroom " + (details.floors() > 1 ? "duplex" : "family home"),
                reasonsFor(details, envelope, tier, bedrooms, lounge, wanted),
                "DETERMINISTIC", "avas-backend-programme-rules-1.0.0", warning != null,
                warning == null ? List.of() : List.of(warning));
    }

    /**
     * The finish tier the programme is planned at.
     *
     * <p>{@code NOT_SURE} is resolved against what the budget buys per square foot, the same way the
     * recommendation resolves it, so a customer who has not chosen a tier is still planned one.</p>
     */
    private static String resolvedCategory(BasicDetailsRequest details, BuildableEnvelope envelope) {
        if (details.category() != Category.NOT_SURE) return details.category().name();
        var enclosed = enclosedArea(details, envelope);
        var rate = details.budget() / Math.max(1d, enclosed);
        return rate >= 3000 ? "LUXURY" : rate >= 2200 ? "PREMIUM" : "STANDARD";
    }

    /** Enclosed floor area this plot can carry across every storey, once open ground is taken. */
    static double enclosedArea(BasicDetailsRequest details, BuildableEnvelope envelope) {
        var perFloor = envelope != null && envelope.plannableArea() > 0
                ? envelope.plannableArea()
                : details.plotArea() * .55d;
        return perFloor * (details.floors() - GROUND_OUTDOOR_SHARE);
    }

    /** Bedrooms the plot can hold once the rooms every home needs have been paid for. */
    static int capacity(BasicDetailsRequest details, BuildableEnvelope envelope,
            SpecificationTier tier, boolean lounge) {
        var core = RoomSpec.of("LIVING_ROOM").preferredArea() + RoomSpec.of("DINING").preferredArea()
                + RoomSpec.of("KITCHEN").preferredArea() + RoomSpec.of("UTILITY").preferredArea();
        if (details.floors() > 1) core += RoomSpec.of("STAIRCASE").preferredArea() * details.floors();
        if (tier.entranceFoyer()) core += RoomSpec.of("FOYER").preferredArea();
        if (lounge) core += RoomSpec.of("FAMILY_LOUNGE").preferredArea();
        if (details.family().seniorCitizens() > 0 || details.floors() == 3) {
            core += RoomSpec.of("LIFT_SHAFT").preferredArea() * details.floors();
        }
        return Math.max(1, (int) ((enclosedArea(details, envelope) - core) / BEDROOM_MODULE));
    }

    /** What this household is planned: what it wants, capped by the plot, floored by what it needs. */
    static int bedroomsFor(BasicDetailsRequest details, BuildableEnvelope envelope,
            SpecificationTier tier, boolean lounge, LifestylePreferences wanted) {
        var want = want(details.family(), tier) + (wanted.moreBedrooms() ? 1 : 0);
        var need = details.family().bedroomsNeeded();
        return Math.max(need, Math.min(Math.min(want, capacity(details, envelope, tier, lounge)),
                MAXIMUM_BEDROOMS));
    }

    /**
     * Bedrooms this household would be planned on ground that can carry them.
     *
     * <p>Couples share, because that is what couples do. Children stop sharing above the standard
     * tier: a family paying premium or luxury rates for three thousand square feet is not buying two
     * children a bunk bed. Luxury carries one bedroom beyond the household on top of that — the
     * ground-floor guest room that tier is built with.</p>
     *
     * <p>Regular guests are deliberately not counted. They are already served by a preferred flex
     * room the plan can drop without dropping a bedroom, and counting them here would plan one
     * visitor two rooms.</p>
     */
    private static int want(FamilyDetails family, SpecificationTier tier) {
        var rooms = shared(family.adults()) + shared(family.seniorCitizens());
        rooms += tier.plansRoomPerChild() ? family.children() : shared(family.children());
        if (tier.plansSpareBedroom()) rooms++;
        return Math.max(1, rooms);
    }

    private static int shared(int people) {
        return (people + 1) / 2;
    }

    /**
     * Bedrooms given a bathroom of their own.
     *
     * <p>All but one, so there is always a bedroom served by the common bathroom rather than a home
     * whose passage bathroom serves nobody.</p>
     */
    static int attachedBathroomsFor(int bedrooms) {
        if (bedrooms > 3) return Math.max(1, bedrooms - 1);
        return Math.max(1, bedrooms - (bedrooms > 1 ? 1 : 0));
    }

    /** Why this household is being planned this home, in terms the customer can check. */
    private static List<String> reasonsFor(BasicDetailsRequest details, BuildableEnvelope envelope,
            SpecificationTier tier, int bedrooms, boolean lounge, LifestylePreferences wanted) {
        var family = details.family();
        var need = family.bedroomsNeeded();
        var want = want(family, tier);
        var reasons = new java.util.ArrayList<String>();
        if (family.children() > 0 && tier.plansRoomPerChild()) {
            reasons.add(family.members() + " permanent residents: a room for each of "
                    + family.children() + (family.children() == 1 ? " child" : " children")
                    + ", and one shared by the adults");
        } else {
            reasons.add(family.members() + " permanent residents share " + need
                    + " core bedrooms at two per room");
        }
        reasons.add(details.plotWidth() + " × " + details.plotLength() + " ft "
                + details.roadFacing().name().toLowerCase(java.util.Locale.ROOT)
                + "-facing plot across " + details.floors()
                + (details.floors() > 1 ? " floors" : " floor"));
        if (bedrooms < want) {
            reasons.add(want + " bedrooms would suit this household; the plot carries "
                    + capacity(details, envelope, tier, lounge) + ", so " + bedrooms + " are planned");
        } else if (bedrooms > need) {
            reasons.add("About " + Math.round(enclosedArea(details, envelope))
                    + " sq ft can be enclosed here, which carries " + bedrooms
                    + " bedrooms rather than the " + need + " the headcount alone would share");
        }
        if (family.regularGuests()) {
            reasons.add("A flex room is planned for the regular guests the brief named, without "
                    + "adding a bedroom");
        }
        if (family.seniorCitizens() > 0) {
            reasons.add("A ground-floor bedroom is planned so the stair is never the only way to bed");
        }
        if (lounge) reasons.add("A family lounge is planned upstairs, private to the household");
        reasons.addAll(wanted.reasons());
        reasons.add(tier.name() + " specification calibrated to the approved budget");
        reasons.add("Hard rules are checked before lifestyle ranking");
        return List.copyOf(reasons);
    }
}

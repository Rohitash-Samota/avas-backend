package com.avas.platform.project;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FamilyDetails(
        @Min(0) @Max(10) int adults,
        @Min(0) @Max(10) int children,
        @Min(0) @Max(10) int seniorCitizens,
        boolean regularGuests
) {
    /** Most bedrooms the current low-rise geometry engine will plan for one household. */
    private static final int MAXIMUM_BEDROOMS = 6;

    public int members() {
        return adults + children + seniorCitizens;
    }

    /**
     * Core bedrooms this household needs, at two people to a room.
     *
     * <p>Couples share, children share and seniors share. That is what these households actually
     * do, and it is what keeps a brief inside the area the plot and the budget can carry: giving
     * every child a room of their own inflated a four-child family to six bedrooms and pushed the
     * built-up target past what the drawing could place, which is how a brief ends up with more
     * bathrooms than rooms to put them beside.</p>
     *
     * <p>Two people therefore need one bedroom, not two. A spare room is something a family asks
     * for in their priorities rather than something the household count decides for them, and a
     * regular guest is served by a preferred flex room that never inflates the permanent count.</p>
     *
     * <p>The ceiling is the geometry engine's current limit rather than a planning rule.</p>
     */
    public int bedroomsNeeded() {
        return Math.max(1, Math.min(MAXIMUM_BEDROOMS,
                sharedRooms(adults) + sharedRooms(children) + sharedRooms(seniorCitizens)));
    }

    /** Rooms a group of this size fills at two to a room, with the odd person rounded up. */
    private static int sharedRooms(int people) {
        return (people + 1) / 2;
    }
}

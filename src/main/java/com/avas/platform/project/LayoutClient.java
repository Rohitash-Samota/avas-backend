package com.avas.platform.project;

import java.util.List;
import java.util.Optional;

/**
 * Asks AVAS AI where the rooms of an already-decided programme should go.
 *
 * <p>The last planning decision before the drawing is drawn, and the only one the platform still
 * made entirely alone. {@link ProgrammeClient} decides <em>what</em> the home contains and this
 * decides <em>how it is arranged</em> — deliberately two questions, because a service that answered
 * both could quietly plan a different house from the one the customer's estimate was costed
 * against. The programme travels with the request; only the arrangement is in question.</p>
 *
 * <p>The arrangement asked for is a <em>hub</em>, not a corridor. {@link FloorPlanner} plans a
 * double-loaded corridor — two strips of rooms either side of a three-and-three-quarter-foot
 * passage — which guarantees every room a door without walking through another room, and spends
 * forty running feet of a family's floor area on somewhere to walk. A hub plan keeps the guarantee
 * and gives the area back: circulation is the living-dining run on the ground floor and the family
 * lounge above it, and every other room takes its door off that. It is how the homes this market
 * actually builds are drawn.</p>
 *
 * <p>An empty answer is not a failure. It means the arrangement was not decided remotely — the
 * service is disabled, unreachable, or returned something that would not build — and the caller
 * plans the same programme locally instead. The customer always gets a drawing.</p>
 */
public interface LayoutClient {
    /**
     * A layout that was decided remotely, and enough provenance to say so truthfully.
     *
     * <p>The provenance is not decoration. Every candidate's generator, mode and model are frozen
     * onto the drawing artifact and printed in the PDF the customer signs, which until now said
     * "AVAS deterministic layout engine" and "No generative AI model" on every drawing the platform
     * had ever produced — true while the platform placed every room itself, and a false statement in
     * a customer-facing document the moment a model placed them instead.</p>
     *
     * @param provider {@code DETERMINISTIC} when the AI service answered from its own rules, or the
     *                 model vendor when a model drew it. Both were planned remotely; only one of
     *                 them involved a model, and the drawing has to be able to tell them apart.
     */
    record Layout(List<RoomGeometry> rooms, String provider, String model, boolean fallbackUsed) {
        /** True when a generative model, rather than a rule set, placed these rooms. */
        boolean modelDrawn() {
            return provider != null && !"DETERMINISTIC".equalsIgnoreCase(provider);
        }
    }

    /**
     * Where this programme's rooms go, or empty to plan them locally.
     *
     * @param programme every space this home is owed, from {@link FloorPlanner#roomProgramme()}, so
     *                  both planners are arranging the same brief
     * @param envelope  the buildable footprint every storey is planned inside
     */
    Optional<Layout> plan(String tenantId, String projectId, String contextVersion,
                                      BasicDetailsRequest details, BuildableEnvelope envelope,
                                      HomeParameters parameters,
                                      List<FloorPlanner.ProgrammeRoom> programme,
                                      boolean stairRequired, boolean liftRequired,
                                      int indoorParkingBays);
}

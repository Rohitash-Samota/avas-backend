package com.avas.platform.project;

import java.util.List;

/**
 * Asks AVAS AI for a picture of a concept the platform has already drawn.
 *
 * <p>This is the one AI route whose answer is <em>not</em> adopted as geometry, and the distinction
 * is the reason it has a client of its own rather than another method on {@link LayoutClient}. A
 * layout is validated, costed, taken off for quantities and printed on the sheet the customer signs;
 * an illustration is looked at. Nothing here is measured, nothing is priced from it, and no corner
 * of it reaches {@link GeometryDocument}.</p>
 *
 * <p>That is a deliberate boundary, not a limitation of the current implementation. The image comes
 * from a diffusion model, which asked for an architectural drawing does not decline — it returns
 * convincing linework over invented dimensions, in the same hand a real drawing is drawn in. The
 * platform already draws the measured plan itself, from geometry it validated; this exists so a
 * customer can see what the house might look like, and it is labelled as such everywhere it is
 * shown.</p>
 *
 * <p>An empty answer is not a failure. It means the render was not available — the service is
 * disabled, unreachable, running without an image model, or out of memory — and the caller shows
 * the measured plan alone, which is what it would have shown anyway. It does say <em>which</em> of
 * those happened: see {@link Outcome}.</p>
 */
public interface ConceptRenderClient {
    /**
     * A generated picture of one concept, and enough provenance to say where it came from.
     *
     * @param prompt       the words the picture was generated from, shown to the customer so an
     *                     illustration can be judged against its own brief rather than taken as a
     *                     statement about their house
     * @param fallbackUsed true when the AI service could not render and answered with its measured
     *                     sheets alone, in which case there is no illustration to show
     */
    record Render(byte[] image, String mediaType, String prompt, String provider, String model,
                  boolean fallbackUsed, List<String> warnings) {
        public Render {
            image = image == null ? new byte[0] : image.clone();
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public byte[] image() {
            return image.clone();
        }
    }

    /**
     * What came back: a picture, nothing because none was asked for, or nothing because it failed.
     *
     * <p>Three states rather than two, because the last two are not the same thing and collapsing
     * them is how an exhausted quota comes to read as a feature nobody switched on. A deployment
     * with no image model is working as intended; one whose render failed needs somebody to look at
     * it, and the person looking is entitled to the provider's own reason.</p>
     */
    record Outcome(Render render, String failure) {
        static Outcome of(Render render) {
            return new Outcome(render, null);
        }

        /** No image model is configured here, which is the ordinary shape of most deployments. */
        static Outcome notConfigured() {
            return new Outcome(null, null);
        }

        /** A render was attempted and did not arrive. {@code reason} is the provider's own words. */
        static Outcome failed(String reason) {
            return new Outcome(null, reason == null || reason.isBlank() ? "no reason given" : reason);
        }

        public boolean ready() {
            return render != null;
        }
    }

    /**
     * Which picture is wanted.
     *
     * <p>{@code FLOOR_PLAN} asks the image model for a top-down plan illustration. It is a picture
     * of a plan, not a plan: the AI service's own prompt forbids dimension strings precisely so it
     * cannot come back looking like something to build from, and it carries the same
     * not-a-measured-drawing label every other generated view carries. {@code FLOOR_PLAN_3D} is the
     * matching single-storey isometric cutaway, also edited from that floor's measured plate.</p>
     */
    enum Style { ELEVATION, PERSPECTIVE, FLOOR_PLAN, FLOOR_PLAN_3D }

    /**
     * How the customer asked this picture to look, as opposed to what it contains.
     *
     * <p>Presentation only, and safe to accept from a form for exactly that reason: every field here
     * changes ink and none of them changes geometry. A plan rendered {@code MONOCHROME} with no
     * landscaping and one rendered {@code EARTH_WOOD} with a garden are the same house at the same
     * dimensions, drawn from the same validated rectangles.</p>
     *
     * <p>They are part of the picture's identity all the same — two option sets are two different
     * pictures rather than one stale one — which is why {@code storeKey} folds them in.</p>
     */
    record RenderOptions(String furnishing, String palette, boolean landscaping, boolean showCars,
                         String labels) {
        /** What a caller that expressed no preference gets: the brochure look, fully dressed. */
        public static RenderOptions defaults() {
            return new RenderOptions("FURNISHED", "WARM_NEUTRAL", true, true, "NAME_AND_SIZE");
        }

        public RenderOptions {
            furnishing = blankTo(furnishing, "FURNISHED");
            palette = blankTo(palette, "WARM_NEUTRAL");
            labels = blankTo(labels, "NAME_AND_SIZE");
        }

        private static String blankTo(String value, String fallback) {
            return value == null || value.isBlank()
                    ? fallback : value.trim().toUpperCase(java.util.Locale.ROOT);
        }

        /**
         * A short stable spelling of these choices, for the key a stored picture is filed under.
         *
         * <p>Stable is the whole requirement: it is compared against keys written days ago, so it
         * cannot depend on field order, on a hash seed, or on anything that varies per process.</p>
         */
        public String storeKey() {
            return String.join("-", furnishing, palette, landscaping ? "L" : "l",
                    showCars ? "C" : "c", labels);
        }

        /** True when nothing was chosen, so the ordinary key stays the short one it always was. */
        public boolean isDefault() {
            return equals(defaults());
        }
    }

    /**
     * A picture of this concept, or empty to show the measured plan alone.
     *
     * @param brief   the customer's own words about the house, carried into the render and nowhere
     *                else — it cannot move a wall, and there is no path from it into project geometry
     * @param options how the picture should look; see {@link RenderOptions}
     */
    Outcome illustrate(String tenantId, String contextVersion, ProjectSummary project,
                       DrawingCandidate drawing, Style style, String brief, String floor,
                       RenderOptions options);

    /** The brochure look, for a caller with no opinion about how the picture is drawn. */
    default Outcome illustrate(String tenantId, String contextVersion, ProjectSummary project,
                               DrawingCandidate drawing, Style style, String brief, String floor) {
        return illustrate(tenantId, contextVersion, project, drawing, style, brief, floor,
                RenderOptions.defaults());
    }

    /** Every storey at once. Kept for callers that want the house rather than one of its plans. */
    default Outcome illustrate(String tenantId, String contextVersion, ProjectSummary project,
                               DrawingCandidate drawing, Style style, String brief) {
        return illustrate(tenantId, contextVersion, project, drawing, style, brief, null);
    }
}

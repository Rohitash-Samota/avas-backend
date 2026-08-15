package com.avas.platform.project;

import jakarta.validation.constraints.Min;

/**
 * Indicative open-space distances applied inward from the plot boundary, in feet.
 *
 * <p>These are planning assumptions only. The applicable development authority controls the real
 * values for a specific plot and road, so every generated envelope records the rule source and
 * remains subject to professional verification.</p>
 */
public record SetbackRule(
        @Min(0) double front,
        @Min(0) double rear,
        @Min(0) double side,
        String source
) {
    /** Marks an envelope derived from AVAS planning defaults rather than a verified authority rule. */
    public static final String ASSUMED = "AVAS_PLANNING_ASSUMPTION";
    /** Narrowest strip the assumed setbacks may leave behind, in feet. */
    private static final double MINIMUM_CORE = 10d;
    /** Share of the plot width the assumed side setbacks may consume between them. */
    private static final double MAXIMUM_WIDTH_SHARE = 0.5d;
    /** Share of the plot depth the assumed front and rear setbacks may consume between them. */
    private static final double MAXIMUM_DEPTH_SHARE = 0.6d;

    public SetbackRule {
        if (front < 0 || rear < 0 || side < 0) {
            throw new IllegalArgumentException("Setback distances cannot be negative");
        }
        source = source == null || source.isBlank() ? ASSUMED : source.trim();
    }

    /**
     * Derives conservative low-rise residential setbacks from plot size and floor count.
     *
     * <p>Larger plots and taller blocks carry proportionally deeper open space. Values stay inside
     * the range commonly seen in Indian municipal bye-laws for plotted residential development, but
     * they are deliberately assumptions rather than a claim about any specific jurisdiction.</p>
     */
    public static SetbackRule assumedFor(double plotArea, int floors) {
        var front = plotArea < 1_000 ? 5d : plotArea < 2_500 ? 7.5d : plotArea < 5_000 ? 10d : 15d;
        var rear = plotArea < 1_000 ? 3d : plotArea < 2_500 ? 5d : plotArea < 5_000 ? 7.5d : 10d;
        var side = plotArea < 1_000 ? 0d : plotArea < 2_500 ? 3d : plotArea < 5_000 ? 5d : 7.5d;
        if (floors >= 3) {
            front += 2.5d;
            rear += 2.5d;
            side = Math.max(side, 3d);
        }
        return new SetbackRule(front, rear, side, ASSUMED);
    }

    /**
     * Assumed setbacks for a specific outline, capped against the plot's own dimensions.
     *
     * <p>The area-only bands are calibrated for normally proportioned plots. On an unusually narrow
     * or shallow site they would consume nearly the whole plot, which says more about the
     * assumption than about the site. The caps bound that overreach; they only ever bite on extreme
     * proportions and never widen the buildable area of a normally shaped plot.</p>
     *
     * <p>Capping does not make a plot buildable. When the remaining core is still under
     * {@link #MINIMUM_CORE} the envelope stage refuses the plot and asks for expert review, because
     * planning a home across a frontage that narrow is a professional judgement, not a default.</p>
     */
    public static SetbackRule assumedFor(PlotBoundary boundary, int floors) {
        var base = assumedFor(boundary.area(), floors);
        var box = boundary.bounds();
        var side = Math.min(base.side(), box.width() * MAXIMUM_WIDTH_SHARE / 2d);
        var depthBudget = box.length() * MAXIMUM_DEPTH_SHARE;
        var front = base.front();
        var rear = base.rear();
        if (front + rear > depthBudget) {
            var factor = depthBudget / (front + rear);
            front *= factor;
            rear *= factor;
        }
        var capped = side < base.side() || front < base.front() || rear < base.rear();
        return new SetbackRule(round(front), round(rear), round(side),
                capped ? ASSUMED_CAPPED : ASSUMED);
    }

    /** Marks an assumption that had to be capped against an unusually proportioned plot. */
    public static final String ASSUMED_CAPPED = "AVAS_PLANNING_ASSUMPTION_CAPPED";

    public static SetbackRule none() {
        return new SetbackRule(0, 0, 0, "NO_SETBACK_APPLIED");
    }

    /** Smallest buildable strip either axis should retain before expert review is warranted. */
    public static double minimumCore() {
        return MINIMUM_CORE;
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    /** True when this rule came from AVAS defaults and still needs authority verification. */
    public boolean assumed() {
        return ASSUMED.equals(source) || ASSUMED_CAPPED.equals(source);
    }

    /** True when the assumption had to be reduced to fit an unusually proportioned plot. */
    public boolean capped() {
        return ASSUMED_CAPPED.equals(source);
    }

    public double maximum() {
        return Math.max(front, Math.max(rear, side));
    }
}

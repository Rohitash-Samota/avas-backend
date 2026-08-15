package com.avas.platform.project;

import java.util.ArrayList;
import java.util.List;

/**
 * The legal-envelope stage of the design workflow: plot outline in, buildable footprint out.
 *
 * <p>This is deliberately the first geometry produced for a project. Rooms are only ever packed
 * inside the rectangle recorded here, so no layout can occupy required open space. Setbacks remain
 * planning assumptions until an authority rule is verified for the specific plot and road.</p>
 */
public record BuildableEnvelope(
        PlotBoundary plot,
        SetbackRule setbacks,
        List<PlotVertex> buildableOutline,
        double plotArea,
        double buildableArea,
        double footprintX,
        double footprintY,
        double footprintWidth,
        double footprintLength,
        List<String> notes
) {
    /** Shortest side AVAS will attempt before declaring a plot unbuildable, in feet. */
    private static final double[] MINIMUM_DIMENSION_LADDER = {14d, 12d, 10d, 8d};

    public BuildableEnvelope {
        buildableOutline = buildableOutline == null ? List.of() : List.copyOf(buildableOutline);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /**
     * Derives the buildable footprint for any simple plot outline.
     *
     * @throws IllegalArgumentException when setbacks leave no rectangle a home could occupy
     */
    public static BuildableEnvelope derive(PlotBoundary plot, SetbackRule setbacks, int floors) {
        var notes = new ArrayList<String>();
        var plotArea = plot.area();
        var outline = PlotGeometry.insetBySetbacks(plot, setbacks);
        if (outline.isEmpty()) {
            throw new IllegalArgumentException(
                    "The assumed setbacks leave no buildable area on this plot; a professional must confirm "
                            + "the applicable open-space rule before a layout can be generated");
        }
        var buildableArea = Math.abs(PlotGeometry.signedArea(outline));

        PlotGeometry.Rect footprint = null;
        for (var minimum : MINIMUM_DIMENSION_LADDER) {
            footprint = PlotGeometry.largestInscribedRectangle(outline, minimum);
            if (footprint != null) {
                if (minimum < MINIMUM_DIMENSION_LADDER[0]) {
                    notes.add("The buildable envelope is narrow; the largest usable footprint is only "
                            + round(footprint.width()) + " x " + round(footprint.length())
                            + " ft and needs professional review");
                }
                break;
            }
        }
        if (footprint == null) {
            throw new IllegalArgumentException(
                    "No rectangle of at least 8 ft per side fits inside the setback envelope; this plot needs "
                            + "an expert review rather than automatic generation");
        }

        if (plot.irregular()) {
            notes.add("The plot outline is not rectangular; rooms are packed inside the largest rectangle that "
                    + "fits within the setback envelope, leaving "
                    + round(buildableArea - footprint.area()) + " sq ft of buildable area unused");
        }
        if (setbacks.assumed()) {
            notes.add("Setbacks are AVAS planning assumptions (front " + round(setbacks.front())
                    + " ft, rear " + round(setbacks.rear()) + " ft, side " + round(setbacks.side())
                    + " ft) and must be replaced with the authority rule for this plot");
        }
        if (setbacks.capped()) {
            notes.add("This plot is unusually proportioned, so the assumed open space was reduced to fit it; "
                    + "the authority rule may well demand more than the layout currently leaves");
        }
        if (Math.min(footprint.width(), footprint.length()) < SetbackRule.minimumCore()) {
            notes.add("The buildable core is under " + round(SetbackRule.minimumCore())
                    + " ft in one direction, which is below the width AVAS plans rooms against with confidence");
        }
        if (floors >= 3) {
            notes.add("Three storeys trigger deeper assumed open space and a professional height check");
        }

        return new BuildableEnvelope(plot, setbacks, outline, plotArea, buildableArea,
                footprint.x(), footprint.y(), footprint.width(), footprint.length(), notes);
    }

    /** Convenience path for the legacy rectangular width/length inputs. */
    public static BuildableEnvelope forRectangle(double width, double length, Facing roadFacing, int floors) {
        var plot = PlotBoundary.rectangle(width, length, roadFacing);
        return derive(plot, SetbackRule.assumedFor(plot, floors), floors);
    }

    /** Footprint area as a share of the plot; the ground-coverage figure a reviewer checks. */
    public double coverageRatio() {
        return plotArea <= 0 ? 0 : footprintArea() / plotArea;
    }

    public double footprintArea() {
        return footprintWidth * footprintLength;
    }

    /** Open space left outside the packed footprint, in square feet. */
    public double openSpaceArea() {
        return Math.max(0, plotArea - footprintArea());
    }

    /** True when the packed rectangle leaves a meaningful part of the envelope unused. */
    public boolean underUsesEnvelope() {
        return buildableArea > 0 && footprintArea() < buildableArea * 0.82d;
    }

    private static double round(double value) {
        return Math.round(value * 10d) / 10d;
    }
}

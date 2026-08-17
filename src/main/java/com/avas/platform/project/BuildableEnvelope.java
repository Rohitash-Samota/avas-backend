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
        List<PlotGeometry.Rect> extensionZones,
        List<String> notes
) {
    /** Shortest side AVAS will attempt before declaring a plot unbuildable, in feet. */
    private static final double[] MINIMUM_DIMENSION_LADDER = {14d, 12d, 10d, 8d};
    /** Shortest side an extension zone needs before it can hold a room of its own, in feet. */
    private static final double MINIMUM_EXTENSION_SIDE = 7d;
    /** Floor area an extension zone needs before it is worth planning rooms into. */
    private static final double MINIMUM_EXTENSION_AREA = 60d;
    /**
     * Shortest side of leftover ground worth recovering on a plot with square corners, in feet.
     *
     * <p>Well below the width a room needs: a strip this narrow cannot be a room, but it can be
     * given to the rooms along it as depth. Only offered where every boundary runs square, because
     * against a slanted edge the same strips would land at different depths and step the wall along
     * the diagonal — a jagged outline nobody builds, in place of a margin nobody minds.</p>
     */
    private static final double MINIMUM_STRIP_SIDE = 3d;
    /** Floor area below which leftover ground is margin rather than something to plan. */
    private static final double MINIMUM_STRIP_AREA = 12d;

    public BuildableEnvelope {
        buildableOutline = buildableOutline == null ? List.of() : List.copyOf(buildableOutline);
        extensionZones = extensionZones == null ? List.of() : List.copyOf(extensionZones);
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

        // Full plot usage is the customer asking for the whole plot, so the packer comes back for
        // the ground the inscribed rectangle could not reach and plans it as extension zones. Every
        // other choice keeps the single rectangle it always had.
        var square = rectilinear(outline);
        var zones = setbacks.waived()
                ? PlotGeometry.residualRectangles(outline, List.of(footprint),
                        square ? MINIMUM_STRIP_SIDE : MINIMUM_EXTENSION_SIDE,
                        square ? MINIMUM_STRIP_AREA : MINIMUM_EXTENSION_AREA)
                : List.<PlotGeometry.Rect>of();
        var zoneArea = zones.stream().mapToDouble(PlotGeometry.Rect::area).sum();

        if (plot.irregular()) {
            var unused = buildableArea - footprint.area() - zoneArea;
            if (!setbacks.waived()) {
                notes.add("The plot outline is not rectangular; rooms are packed inside the largest rectangle "
                        + "that fits within the setback envelope, leaving "
                        + round(buildableArea - footprint.area()) + " sq ft of buildable area unused");
            } else if (!square) {
                notes.add("The plot outline is not rectangular and has a slanted boundary; the rooms standing "
                        + "against it are drawn to the boundary's own line so the layout follows the plot "
                        + "rather than stopping at the largest rectangle inside it");
            } else if (!zones.isEmpty()) {
                notes.add("The plot outline is not rectangular; rooms are packed into the largest rectangle that "
                        + "fits inside it plus " + zones.size() + " extension zone"
                        + (zones.size() == 1 ? "" : "s") + " totalling " + round(zoneArea)
                        + " sq ft, leaving " + round(Math.max(0, unused))
                        + " sq ft as margin against the boundary");
            }
        }
        if (setbacks.waived()) {
            // Said plainly, and without overstating it. Building wall to wall is how most streets in
            // this market are built and is permitted on many plots; what is not safe is assuming so.
            notes.add("Full plot usage was selected, so this layout occupies the entire "
                    + round(plotArea) + " sq ft plot and leaves no setback or open space, in the way "
                    + "a row of city houses is built to its boundaries. Whether that is permitted "
                    + "depends on the plot: many authorities allow zero side margins on small plots "
                    + "and require open space on larger ones, so the applicable open-space rule must "
                    + "be confirmed for this plot and road before the drawing is submitted.");
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
                footprint.x(), footprint.y(), footprint.width(), footprint.length(), zones, notes);
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

    /**
     * Ground one storey can actually be planned on: the packed rectangle plus every extension zone.
     *
     * <p>This is the figure the brief and the estimate have to reason about. Targeting the inscribed
     * rectangle alone would report the extension zones as the plan overrunning its own cost basis,
     * when they are the area the customer asked to use.</p>
     */
    public double plannableArea() {
        // Full plot usage reaches the whole outline: square ground is recovered as extension zones,
        // and the rooms against a slanted edge follow it. Either way the target is the plot itself.
        if (setbacks.waived()) {
            return plotArea;
        }
        return footprintArea() + extensionZones.stream().mapToDouble(PlotGeometry.Rect::area).sum();
    }

    /** Open space left outside the packed footprint, in square feet. */
    public double openSpaceArea() {
        return Math.max(0, plotArea - footprintArea());
    }

    /** True when the packed rectangle leaves a meaningful part of the envelope unused. */
    public boolean underUsesEnvelope() {
        return buildableArea > 0 && footprintArea() < buildableArea * 0.82d;
    }

    /** True when some boundary runs at an angle, so no rectangle can be flush against it. */
    public boolean slanted() {
        return !rectilinear(buildableOutline);
    }

    /** True when every boundary edge runs square, so rooms can meet it without stepping. */
    private static boolean rectilinear(List<PlotVertex> outline) {
        for (var index = 0; index < outline.size(); index++) {
            var from = outline.get(index);
            var to = outline.get((index + 1) % outline.size());
            if (Math.abs(from.x() - to.x()) > .05 && Math.abs(from.y() - to.y()) > .05) {
                return false;
            }
        }
        return true;
    }

    private static double round(double value) {
        return Math.round(value * 10d) / 10d;
    }
}

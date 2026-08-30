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
    /**
     * Most of its own depth the building will give up so the cars can stand in front of it.
     *
     * <p>A foot off a forty-seven foot plate to save a three-hundred square foot garage is a clear
     * gain. A third of the plate to save the same garage is not, however the arithmetic on one
     * storey looks, because the depth is lost on every storey and the garage was only ever on one.</p>
     */
    private static final double MAXIMUM_PARKING_HOLD_BACK_SHARE = 0.15d;

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
        return derive(plot, setbacks, floors, null, 0);
    }

    /**
     * Derives the buildable footprint, holding the building back far enough to park outdoors.
     *
     * @param facing     the road this plot is entered from, or {@code null} when it is not yet known
     * @param parkingCars bays the home is planned around; zero leaves the footprint untouched
     */
    public static BuildableEnvelope derive(PlotBoundary plot, SetbackRule setbacks, int floors,
            Facing facing, int parkingCars) {
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

        footprint = holdBackForParking(footprint, plot, setbacks, facing, parkingCars, notes);

        // One rectangle is what the packer can plan, not what the customer is entitled to build. On
        // an irregular plot the largest one inside the envelope reaches barely two thirds of it, so
        // the packer comes back for the ground standing empty beside it and plans that too.
        //
        // How hard it scrapes is what the plot-usage choice decides. Full plot usage takes even the
        // strips too narrow to be a room and gives them to the rooms alongside as depth; every other
        // choice recovers only ground big enough to stand a room in and leaves the rest as margin.
        // What none of them do any more is discard buildable area: the setback ring is what the rule
        // holds back, and the area inside it is the customer's whether or not one rectangle covers it.
        var square = rectilinear(outline);
        var scrapeStrips = setbacks.waived() && square;
        var zones = PlotGeometry.residualRectangles(outline, List.of(footprint),
                scrapeStrips ? MINIMUM_STRIP_SIDE : MINIMUM_EXTENSION_SIDE,
                scrapeStrips ? MINIMUM_STRIP_AREA : MINIMUM_EXTENSION_AREA);
        var zoneArea = zones.stream().mapToDouble(PlotGeometry.Rect::area).sum();

        if (plot.irregular()) {
            var unused = buildableArea - footprint.area() - zoneArea;
            if (!setbacks.waived()) {
                notes.add("The plot outline is not rectangular; rooms are packed into the largest rectangle "
                        + "that fits within the setback envelope"
                        + (zones.isEmpty() ? "" : " plus " + zones.size() + " extension zone"
                                + (zones.size() == 1 ? "" : "s") + " totalling " + round(zoneArea) + " sq ft")
                        + ", leaving " + round(Math.max(0, unused))
                        + " sq ft of the buildable area as margin against the setback line");
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
     * Pulls the building back from the road until the cars can stand in front of it.
     *
     * <p>The footprint is otherwise flush with the setback line, so the only open ground a car could
     * reach is the front setback itself — and the assumed front setback on a plot this size is seven
     * and a half feet, which is a foot short of the width of a car. Being a foot short is not a
     * neutral outcome: {@link ApproachParking} finds no approach, the bays fall to the building, and
     * the ground floor spends seventeen feet of its frontage on a garage. That is three hundred and
     * forty square feet of structure, and the room it displaces is the living room.</p>
     *
     * <p>So the building gives up the foot. What it buys back is the whole garage, and what the
     * drawing gains is a house with cars parked in front of it rather than inside it.</p>
     *
     * <p>Deliberately not done by deepening the setback. A setback is what the authority requires
     * and is recorded as an assumption pending verification; how far behind that line the building
     * chooses to sit is a design decision, and conflating the two would put a design choice in the
     * field a professional is asked to check.</p>
     */
    private static PlotGeometry.Rect holdBackForParking(PlotGeometry.Rect footprint, PlotBoundary plot,
            SetbackRule setbacks, Facing facing, int parkingCars, List<String> notes) {
        // Full plot usage is an instruction to build across the outline; there is no ground to give.
        if (facing == null || parkingCars <= 0 || setbacks.waived()) return footprint;
        var box = plot.bounds();
        var horizontal = facing == Facing.NORTH || facing == Facing.SOUTH;
        var depth = switch (facing) {
            case NORTH -> box.maximumY() - (footprint.y() + footprint.length());
            case SOUTH -> footprint.y() - box.minimumY();
            case EAST -> box.maximumX() - (footprint.x() + footprint.width());
            case WEST -> footprint.x() - box.minimumX();
        };
        var run = horizontal ? footprint.width() : footprint.length();

        // Standing along the boundary needs the width of a car and the length of one per bay;
        // driving in nose first needs the length of a car and only its width per bay. Whichever
        // costs the building less depth is the one to hold back for, because both are arrangements
        // this market builds and the cheaper one leaves more house.
        var alongside = fits(run, parkingCars * ApproachParking.CAR_LENGTH)
                ? ApproachParking.CAR_WIDTH : Double.NaN;
        var noseIn = fits(run, parkingCars * ApproachParking.CAR_WIDTH)
                ? ApproachParking.CAR_LENGTH : Double.NaN;
        var required = Double.isNaN(alongside) ? noseIn
                : Double.isNaN(noseIn) ? alongside : Math.min(alongside, noseIn);
        if (Double.isNaN(required) || depth + .01 >= required) return footprint;

        var holdBack = required - depth;
        var plateDepth = horizontal ? footprint.length() : footprint.width();
        var remaining = plateDepth - holdBack;
        // Never at the cost of a plate too narrow to plan against. A plot that cannot both hold a
        // home and park in front of it keeps the home, and the bays fall back to the building.
        if (remaining < SetbackRule.minimumCore()) return footprint;
        // And never when the ground given up costs more than the garage it saves. On a small plot a
        // shallow front setback is eleven feet short of a nose-in bay, and paying eleven feet of a
        // thirty-two foot plate to avoid one indoor bay is a worse home, not a better one: it is a
        // third of every storey spent to save a room on one of them.
        if (holdBack * run > parkingCars * RoomSpec.of("PARKING").preferredArea()) return footprint;
        if (holdBack > plateDepth * MAXIMUM_PARKING_HOLD_BACK_SHARE) return footprint;

        notes.add("The building is set back a further " + round(holdBack)
                + " ft from the road so " + parkingCars + " car" + (parkingCars == 1 ? "" : "s")
                + " can stand on the approach rather than inside the ground floor");
        return switch (facing) {
            case NORTH -> new PlotGeometry.Rect(footprint.x(), footprint.y(),
                    footprint.width(), footprint.length() - holdBack);
            case SOUTH -> new PlotGeometry.Rect(footprint.x(), footprint.y() + holdBack,
                    footprint.width(), footprint.length() - holdBack);
            case EAST -> new PlotGeometry.Rect(footprint.x(), footprint.y(),
                    footprint.width() - holdBack, footprint.length());
            case WEST -> new PlotGeometry.Rect(footprint.x() + holdBack, footprint.y(),
                    footprint.width() - holdBack, footprint.length());
        };
    }

    /** True when a run of open ground is long enough to stand the bays it has to hold. */
    private static boolean fits(double run, double needed) {
        return run + .01 >= needed;
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
        // The same reasoning one line in. A slanted setback line is followed rather than stopped
        // square of, so the envelope's own area is what a storey can be planned on; anywhere else
        // it is the packed rectangle plus the ground the extension zones recovered beside it.
        if (slanted()) {
            return buildableArea;
        }
        return footprintArea() + extensionZones.stream().mapToDouble(PlotGeometry.Rect::area).sum();
    }

    /**
     * Plot area outside the packed footprint, in square feet.
     *
     * <p>Not the open ground the plan leaves: the extension zones are counted here and are built on.
     * This is the complement of {@link #footprintArea()} against the plot, and nothing more.</p>
     */
    public double openSpaceArea() {
        return Math.max(0, plotArea - footprintArea());
    }

    /** True when the layout still leaves a meaningful part of the envelope unused. */
    public boolean underUsesEnvelope() {
        // Measured against everything a storey can be planned on, not the packed rectangle alone.
        // Reading the rectangle here told a customer an architect could recover area the extension
        // zones and the boundary-following rooms had already recovered for them.
        return buildableArea > 0 && plannableArea() < buildableArea * 0.82d;
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

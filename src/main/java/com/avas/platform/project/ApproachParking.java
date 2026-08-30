package com.avas.platform.project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where the cars stand, decided once for both the layout and the site plan.
 *
 * <p>This was two decisions that never spoke to each other. The planner asked whether the customer
 * wanted parking and, if so, took a bay-sized bite out of the ground floor's public band; the site
 * plan separately measured the front setback and drew bays on it if a car happened to fit. On most
 * plots both fired, so a home was given a garage indoors <em>and</em> a driveway outside; on the
 * rest neither knew what the other had done, and the programme audit — counting only indoor
 * rectangles — reported a plan that parked two cars on the approach as providing none.</p>
 *
 * <p>Deciding it here makes the trade-off explicit, and it is the trade-off that matters most to
 * what a family gets for their money. A bay on the approach costs a paved slab. The same bay inside
 * the building costs a storey of structure and takes its frontage from the living room — seventeen
 * feet of it for two cars, on a plot whose whole frontage is thirty-four. So the approach is used
 * first, always, and the building only carries the cars the approach cannot.</p>
 *
 * @param bays   cars that stand on the approach
 * @param noseIn true when they drive in front-first, false when they stand along the boundary
 * @param area   the piece of open ground they stand on, or {@code null} when none was found
 */
record ApproachParking(int bays, boolean noseIn, PlotGeometry.Rect area) {
    /** Length of a car, and the depth an approach needs before one can be driven in nose first. */
    static final double CAR_LENGTH = 16d;
    /** Width of a car, and the depth an approach needs before one can stand along the boundary. */
    static final double CAR_WIDTH = 8.5d;
    /** Shortest side of open ground worth treating as a usable piece of the plot, in feet. */
    private static final double MINIMUM_OPEN_SIDE = 3d;
    /** Floor area below which open ground is a margin rather than somewhere to put anything. */
    private static final double MINIMUM_OPEN_AREA = 30d;
    private static final double GAP = .05d;

    static final ApproachParking NONE = new ApproachParking(0, false, null);

    /**
     * How many of the requested cars the approach can take, and how they stand on it.
     *
     * <p>Full plot usage parks none outside: the customer asked to build across the outline, so the
     * only ground beside the building is the sliver the envelope already gave to a room.</p>
     */
    static ApproachParking decide(BuildableEnvelope envelope, Facing facing, HomeParameters parameters) {
        if (envelope == null || parameters == null || parameters.parkingCars() <= 0
                || parameters.usesFullPlot()) {
            return NONE;
        }
        var approach = approachGround(envelope, facing);
        if (approach == null) return NONE;

        var horizontal = facing == Facing.NORTH || facing == Facing.SOUTH;
        var depth = horizontal ? approach.length() : approach.width();
        var run = horizontal ? approach.width() : approach.length();
        if (depth + .01 < CAR_WIDTH) return NONE;

        // A front setback is usually shallower than a car is long, which is why so many homes in
        // this market park along the boundary rather than nose-in. Both are real arrangements, so
        // the depth that is actually there chooses between them instead of ruling parking out.
        var noseIn = depth + .01 >= CAR_LENGTH;
        var perCar = noseIn ? CAR_WIDTH : CAR_LENGTH;
        var bays = Math.min(parameters.parkingCars(), (int) Math.floor(run / perCar));
        return bays <= 0 ? NONE : new ApproachParking(bays, noseIn, approach);
    }

    /** Cars the building itself has to carry once the approach has taken what it can. */
    int indoorBays(HomeParameters parameters) {
        return Math.max(0, parameters.parkingCars() - bays);
    }

    /** The bay rectangle, laid hard against the house so the rest of the approach stays driveway. */
    PlotGeometry.Rect bayRectangle(BuildableEnvelope envelope, Facing facing) {
        if (bays <= 0 || area == null) return null;
        var horizontal = facing == Facing.NORTH || facing == Facing.SOUTH;
        var depth = noseIn ? CAR_LENGTH : CAR_WIDTH;
        var run = bays * (noseIn ? CAR_WIDTH : CAR_LENGTH);
        if (horizontal) {
            var y = facing == Facing.SOUTH ? area.y() + area.length() - depth : area.y();
            return new PlotGeometry.Rect(area.x() + (area.width() - run) / 2, y, run, depth);
        }
        var x = facing == Facing.WEST ? area.x() + area.width() - depth : area.x();
        return new PlotGeometry.Rect(x, area.y() + (area.length() - run) / 2, depth, run);
    }

    /**
     * The largest piece of open ground on the road side, which is the only ground a car can reach.
     */
    private static PlotGeometry.Rect approachGround(BuildableEnvelope envelope, Facing facing) {
        var built = new ArrayList<PlotGeometry.Rect>();
        built.add(new PlotGeometry.Rect(envelope.footprintX(), envelope.footprintY(),
                envelope.footprintWidth(), envelope.footprintLength()));
        built.addAll(envelope.extensionZones());
        return PlotGeometry.residualRectangles(envelope.plot().vertices(), built,
                        MINIMUM_OPEN_SIDE, MINIMUM_OPEN_AREA).stream()
                .filter(piece -> onRoadSide(envelope, piece, facing))
                .max(Comparator.comparingDouble(PlotGeometry.Rect::area))
                .orElse(null);
    }

    /** True when this piece of open ground lies between the building and the road. */
    private static boolean onRoadSide(BuildableEnvelope envelope, PlotGeometry.Rect piece, Facing facing) {
        return switch (facing) {
            case NORTH -> piece.y() >= envelope.footprintY() + envelope.footprintLength() - GAP;
            case SOUTH -> piece.y() + piece.length() <= envelope.footprintY() + GAP;
            case EAST -> piece.x() >= envelope.footprintX() + envelope.footprintWidth() - GAP;
            case WEST -> piece.x() + piece.width() <= envelope.footprintX() + GAP;
        };
    }

    /**
     * Open ground nothing is standing on, for the site plan to name.
     *
     * @param alsoOccupied ground already spoken for beyond the building — the bays, once they are
     *                     placed. Passing them in is what lets a lawn be drawn <em>beside</em> a
     *                     driveway: measured against the building alone the front band comes back as
     *                     one rectangle, and planting it paints grass over the parked cars.
     */
    static List<PlotGeometry.Rect> openGround(BuildableEnvelope envelope,
            PlotGeometry.Rect... alsoOccupied) {
        var built = new ArrayList<PlotGeometry.Rect>();
        built.add(new PlotGeometry.Rect(envelope.footprintX(), envelope.footprintY(),
                envelope.footprintWidth(), envelope.footprintLength()));
        built.addAll(envelope.extensionZones());
        for (var occupied : alsoOccupied) {
            if (occupied != null) built.add(occupied);
        }
        return PlotGeometry.residualRectangles(envelope.plot().vertices(), built,
                MINIMUM_OPEN_SIDE, MINIMUM_OPEN_AREA);
    }
}

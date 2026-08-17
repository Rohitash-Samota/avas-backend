package com.avas.platform.project;

import java.util.List;

/**
 * One room on one storey.
 *
 * <p>{@code x}, {@code y}, {@code width} and {@code length} are always the bounding box, and
 * {@code area} is always the true enclosed area. For the rectangular rooms that make up almost
 * every plan the two say the same thing, and {@code outline} is absent.</p>
 *
 * <p>{@code outline} carries the corners of a room that is not a rectangle. Only rooms standing
 * against a slanted plot boundary need one: they follow the boundary rather than stopping square of
 * it, which is the difference between using the whole plot and leaving a wedge of it unbuilt. Every
 * consumer that only needs to place or measure the room keeps reading the bounding box; the ones
 * that draw it read the outline when it is there.</p>
 */
public record RoomGeometry(
        String id,
        String type,
        double x,
        double y,
        double width,
        double length,
        double area,
        String floor,
        List<PlotVertex> outline
) {
    public RoomGeometry {
        outline = outline == null ? List.of() : List.copyOf(outline);
    }

    /** The rectangular case, which is what the packer produces for all but the boundary rooms. */
    public RoomGeometry(String id, String type, double x, double y, double width, double length,
            double area, String floor) {
        this(id, type, x, y, width, length, area, floor, List.of());
    }

    /** True when this room has corners of its own rather than being its bounding box. */
    public boolean shaped() {
        return outline.size() >= 3;
    }

    /** The room's corners, falling back to the bounding rectangle for an ordinary room. */
    public List<PlotVertex> corners() {
        return shaped() ? outline : List.of(
                PlotVertex.of(x, y), PlotVertex.of(x + width, y),
                PlotVertex.of(x + width, y + length), PlotVertex.of(x, y + length));
    }
}

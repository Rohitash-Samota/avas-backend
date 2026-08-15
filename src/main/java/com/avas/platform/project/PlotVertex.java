package com.avas.platform.project;

import jakarta.validation.constraints.NotNull;

/**
 * A single plot boundary corner in project planning feet.
 *
 * <p>The planning grid is right-handed with {@code +x} pointing east and {@code +y} pointing
 * north, so a compass bearing can be derived from any edge normal without extra state.</p>
 */
public record PlotVertex(@NotNull Double x, @NotNull Double y) {

    public PlotVertex {
        if (x == null || y == null) {
            throw new IllegalArgumentException("Plot vertex requires both x and y");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Plot vertex coordinates must be finite");
        }
    }

    public static PlotVertex of(double x, double y) {
        return new PlotVertex(x, y);
    }

    public double distanceTo(PlotVertex other) {
        return Math.hypot(other.x - x, other.y - y);
    }

    PlotVertex rounded() {
        return new PlotVertex(round(x), round(y));
    }

    private static double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }
}

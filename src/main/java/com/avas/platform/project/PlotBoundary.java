package com.avas.platform.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * A closed, non self-intersecting plot outline in planning feet.
 *
 * <p>Any simple polygon is accepted, so rectangular, L-shaped, corner-cut, trapezoidal and fully
 * irregular survey outlines all use one representation. Vertices are stored counter-clockwise so
 * every edge has a well defined outward normal, which is what setback classification and door
 * placement rely on.</p>
 */
public record PlotBoundary(
        @NotNull @Size(min = 3, max = 64) List<@Valid PlotVertex> vertices,
        @NotNull Facing roadFacing
) {
    /** Rejects hairline vertices that would make edge normals meaningless. */
    private static final double MINIMUM_EDGE = 0.05d;

    public PlotBoundary {
        if (vertices == null || vertices.size() < 3) {
            throw new IllegalArgumentException("A plot boundary needs at least three corners");
        }
        if (vertices.size() > 64) {
            throw new IllegalArgumentException("A plot boundary supports at most 64 corners");
        }
        if (roadFacing == null) {
            throw new IllegalArgumentException("A plot boundary needs a road-facing direction");
        }
        var cleaned = PlotGeometry.dropRepeatedVertices(vertices, MINIMUM_EDGE);
        if (cleaned.size() < 3) {
            throw new IllegalArgumentException("A plot boundary needs at least three distinct corners");
        }
        // Crossing is checked first: a bow-tie outline can have a near-zero signed area because the
        // lobes cancel, and "crosses itself" is the diagnosis the customer can actually act on.
        if (PlotGeometry.selfIntersects(cleaned)) {
            throw new IllegalArgumentException("The plot boundary crosses itself; corners must form a simple outline");
        }
        if (Math.abs(PlotGeometry.signedArea(cleaned)) < 1d) {
            throw new IllegalArgumentException("The plot boundary encloses no usable area");
        }
        vertices = PlotGeometry.counterClockwise(cleaned);
    }

    /** Builds the axis-aligned rectangle that the earlier width/length-only flow implied. */
    public static PlotBoundary rectangle(double width, double length, Facing roadFacing) {
        if (width <= 0 || length <= 0) {
            throw new IllegalArgumentException("Plot width and length must be positive");
        }
        return new PlotBoundary(List.of(
                PlotVertex.of(0, 0),
                PlotVertex.of(width, 0),
                PlotVertex.of(width, length),
                PlotVertex.of(0, length)), roadFacing);
    }

    /** Enclosed area by the shoelace formula, in square feet. */
    public double area() {
        return Math.abs(PlotGeometry.signedArea(vertices));
    }

    public double perimeter() {
        var total = 0d;
        for (var index = 0; index < vertices.size(); index++) {
            total += vertices.get(index).distanceTo(vertices.get((index + 1) % vertices.size()));
        }
        return total;
    }

    public PlotGeometry.Bounds bounds() {
        return PlotGeometry.bounds(vertices);
    }

    /** Longest edge whose outward normal points toward the road; the notional frontage. */
    public double frontage() {
        var longest = 0d;
        for (var edge : edges()) {
            if (edge.side() == EdgeSide.FRONT) {
                longest = Math.max(longest, edge.length());
            }
        }
        return longest > 0 ? longest : bounds().width();
    }

    /** True when the outline is more complex than a plain rectangle and needs review attention. */
    public boolean irregular() {
        if (vertices.size() != 4) {
            return true;
        }
        var box = bounds();
        return Math.abs(box.width() * box.length() - area()) > Math.max(1d, area() * 0.01d);
    }

    /** Edges in boundary order, each already classified against the road direction. */
    public List<Edge> edges() {
        var road = PlotGeometry.compassVector(roadFacing);
        var result = new ArrayList<Edge>(vertices.size());
        for (var index = 0; index < vertices.size(); index++) {
            var from = vertices.get(index);
            var to = vertices.get((index + 1) % vertices.size());
            result.add(new Edge(from, to, EdgeSide.classify(PlotGeometry.outwardNormal(from, to), road)));
        }
        return List.copyOf(result);
    }

    /** Which boundary an edge represents once the road direction is known. */
    public enum EdgeSide {
        FRONT,
        REAR,
        SIDE;

        static EdgeSide classify(double[] outwardNormal, double[] roadVector) {
            var alignment = outwardNormal[0] * roadVector[0] + outwardNormal[1] * roadVector[1];
            // cos(45 deg): an edge counts as front or rear only when it genuinely faces that way.
            if (alignment >= 0.7071d) {
                return FRONT;
            }
            return alignment <= -0.7071d ? REAR : SIDE;
        }
    }

    /** A boundary segment together with the open-space category it carries. */
    public record Edge(PlotVertex from, PlotVertex to, EdgeSide side) {
        public double length() {
            return from.distanceTo(to);
        }

        public double setbackFrom(SetbackRule rule) {
            return switch (side) {
                case FRONT -> rule.front();
                case REAR -> rule.rear();
                case SIDE -> rule.side();
            };
        }
    }
}

package com.avas.platform.project;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic planar helpers for arbitrary plot outlines.
 *
 * <p>Every routine is pure and side-effect free so the same boundary always yields the same
 * envelope, which is what the reproducible-generation contract requires. Nothing here is a
 * substitute for a surveyed drawing or an authority-issued setback.</p>
 */
final class PlotGeometry {
    /** Tolerance for treating two coordinates as the same point, in feet. */
    static final double EPSILON = 1e-6d;
    /** Upper bound on raster cells per axis, keeping the inscribed-rectangle scan bounded. */
    private static final int MAX_GRID = 360;
    /** Finest raster cell, in feet. Coarser than this on very large plots. */
    private static final double MIN_CELL = 0.25d;

    private PlotGeometry() {
    }

    /** Twice-signed area halved: positive when the ring is counter-clockwise. */
    static double signedArea(List<PlotVertex> ring) {
        var total = 0d;
        for (var index = 0; index < ring.size(); index++) {
            var current = ring.get(index);
            var next = ring.get((index + 1) % ring.size());
            total += current.x() * next.y() - next.x() * current.y();
        }
        return total / 2d;
    }

    static List<PlotVertex> counterClockwise(List<PlotVertex> ring) {
        if (signedArea(ring) >= 0) {
            return List.copyOf(ring);
        }
        var reversed = new ArrayList<>(ring);
        java.util.Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    /** Removes duplicate and collinear-adjacent corners that would produce zero-length edges. */
    static List<PlotVertex> dropRepeatedVertices(List<PlotVertex> ring, double minimumEdge) {
        var cleaned = new ArrayList<PlotVertex>(ring.size());
        for (var vertex : ring) {
            if (cleaned.isEmpty() || cleaned.get(cleaned.size() - 1).distanceTo(vertex) > minimumEdge) {
                cleaned.add(vertex);
            }
        }
        while (cleaned.size() > 2 && cleaned.get(0).distanceTo(cleaned.get(cleaned.size() - 1)) <= minimumEdge) {
            cleaned.remove(cleaned.size() - 1);
        }
        return List.copyOf(cleaned);
    }

    /** True when any pair of non-adjacent edges cross, which would make the outline invalid. */
    static boolean selfIntersects(List<PlotVertex> ring) {
        var count = ring.size();
        for (var i = 0; i < count; i++) {
            var a1 = ring.get(i);
            var a2 = ring.get((i + 1) % count);
            for (var j = i + 1; j < count; j++) {
                // Skip the shared-corner neighbours; only genuinely disjoint edges may not cross.
                if (j == i || (j + 1) % count == i || (i + 1) % count == j) {
                    continue;
                }
                if (segmentsIntersect(a1, a2, ring.get(j), ring.get((j + 1) % count))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean segmentsIntersect(PlotVertex p1, PlotVertex p2, PlotVertex p3, PlotVertex p4) {
        var d1 = cross(p3, p4, p1);
        var d2 = cross(p3, p4, p2);
        var d3 = cross(p1, p2, p3);
        var d4 = cross(p1, p2, p4);
        if (((d1 > EPSILON && d2 < -EPSILON) || (d1 < -EPSILON && d2 > EPSILON))
                && ((d3 > EPSILON && d4 < -EPSILON) || (d3 < -EPSILON && d4 > EPSILON))) {
            return true;
        }
        return (Math.abs(d1) <= EPSILON && onSegment(p3, p4, p1))
                || (Math.abs(d2) <= EPSILON && onSegment(p3, p4, p2))
                || (Math.abs(d3) <= EPSILON && onSegment(p1, p2, p3))
                || (Math.abs(d4) <= EPSILON && onSegment(p1, p2, p4));
    }

    private static double cross(PlotVertex origin, PlotVertex to, PlotVertex point) {
        return (to.x() - origin.x()) * (point.y() - origin.y())
                - (to.y() - origin.y()) * (point.x() - origin.x());
    }

    private static boolean onSegment(PlotVertex a, PlotVertex b, PlotVertex point) {
        return point.x() >= Math.min(a.x(), b.x()) - EPSILON && point.x() <= Math.max(a.x(), b.x()) + EPSILON
                && point.y() >= Math.min(a.y(), b.y()) - EPSILON && point.y() <= Math.max(a.y(), b.y()) + EPSILON;
    }

    static Bounds bounds(List<PlotVertex> ring) {
        var minX = Double.MAX_VALUE;
        var maxX = -Double.MAX_VALUE;
        var minY = Double.MAX_VALUE;
        var maxY = -Double.MAX_VALUE;
        for (var vertex : ring) {
            minX = Math.min(minX, vertex.x());
            maxX = Math.max(maxX, vertex.x());
            minY = Math.min(minY, vertex.y());
            maxY = Math.max(maxY, vertex.y());
        }
        return new Bounds(minX, maxX, minY, maxY);
    }

    /** Outward unit normal of a directed edge on a counter-clockwise ring. */
    static double[] outwardNormal(PlotVertex from, PlotVertex to) {
        var dx = to.x() - from.x();
        var dy = to.y() - from.y();
        var length = Math.hypot(dx, dy);
        if (length <= EPSILON) {
            return new double[] {0, 0};
        }
        return new double[] {dy / length, -dx / length};
    }

    /** Unit vector toward a compass direction on the planning grid. */
    static double[] compassVector(Facing facing) {
        return switch (facing) {
            case NORTH -> new double[] {0, 1};
            case SOUTH -> new double[] {0, -1};
            case EAST -> new double[] {1, 0};
            case WEST -> new double[] {-1, 0};
        };
    }

    static boolean containsPoint(List<PlotVertex> ring, double x, double y) {
        var inside = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            var xi = ring.get(i).x();
            var yi = ring.get(i).y();
            var xj = ring.get(j).x();
            var yj = ring.get(j).y();
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * Applies each edge's setback as an inward half-plane clip.
     *
     * <p>For convex outlines this is the exact offset polygon. For re-entrant outlines the
     * successive-clip result is contained within the true offset, so the buildable area is never
     * overstated. Under-claiming open space would be the unsafe direction, so the conservative
     * result is the intended behaviour rather than an approximation to fix later.</p>
     */
    static List<PlotVertex> insetBySetbacks(PlotBoundary boundary, SetbackRule rule) {
        var current = boundary.vertices();
        for (var edge : boundary.edges()) {
            var distance = edge.setbackFrom(rule);
            if (distance <= EPSILON) {
                continue;
            }
            var outward = outwardNormal(edge.from(), edge.to());
            if (outward[0] == 0 && outward[1] == 0) {
                continue;
            }
            // Inward normal is the negated outward normal; the clip keeps everything at least
            // `distance` inside the edge line.
            var nx = -outward[0];
            var ny = -outward[1];
            var offset = nx * edge.from().x() + ny * edge.from().y() + distance;
            current = clipHalfPlane(current, nx, ny, offset);
            if (current.size() < 3) {
                return List.of();
            }
        }
        var cleaned = dropRepeatedVertices(current, 0.01d);
        return cleaned.size() < 3 || Math.abs(signedArea(cleaned)) < 1d ? List.of() : counterClockwise(cleaned);
    }

    /** Sutherland-Hodgman clip retaining the region where {@code nx*x + ny*y >= offset}. */
    static List<PlotVertex> clipHalfPlane(List<PlotVertex> ring, double nx, double ny, double offset) {
        if (ring.isEmpty()) {
            return List.of();
        }
        var output = new ArrayList<PlotVertex>(ring.size() + 4);
        for (var index = 0; index < ring.size(); index++) {
            var current = ring.get(index);
            var next = ring.get((index + 1) % ring.size());
            var currentDistance = nx * current.x() + ny * current.y() - offset;
            var nextDistance = nx * next.x() + ny * next.y() - offset;
            var currentInside = currentDistance >= -EPSILON;
            var nextInside = nextDistance >= -EPSILON;
            if (currentInside) {
                output.add(current);
            }
            if (currentInside != nextInside) {
                var t = currentDistance / (currentDistance - nextDistance);
                output.add(PlotVertex.of(
                        current.x() + t * (next.x() - current.x()),
                        current.y() + t * (next.y() - current.y())).rounded());
            }
        }
        return output;
    }

    /**
     * Largest axis-aligned rectangle that fits wholly inside a polygon.
     *
     * <p>The polygon is rasterised and scanned with the standard largest-rectangle-in-histogram
     * sweep. A cell counts only when all four of its corners are inside, so the returned rectangle
     * never escapes the buildable envelope. This is what lets the proven rectangular room packer
     * serve an arbitrary plot: the packer receives a guaranteed-legal rectangle.</p>
     *
     * @param minimumDimension shortest acceptable side in feet; rejects unusable slivers
     */
    static Rect largestInscribedRectangle(List<PlotVertex> ring, double minimumDimension) {
        if (ring.size() < 3) {
            return null;
        }
        var box = bounds(ring);
        var span = Math.max(box.width(), box.length());
        if (span <= 0) {
            return null;
        }
        var cell = Math.max(MIN_CELL, span / MAX_GRID);
        var columns = (int) Math.floor(box.width() / cell);
        var rows = (int) Math.floor(box.length() / cell);
        if (columns < 1 || rows < 1) {
            return null;
        }
        var filled = new boolean[rows][columns];
        for (var r = 0; r < rows; r++) {
            for (var c = 0; c < columns; c++) {
                var x0 = box.minimumX() + c * cell;
                var y0 = box.minimumY() + r * cell;
                filled[r][c] = containsPoint(ring, x0 + EPSILON, y0 + EPSILON)
                        && containsPoint(ring, x0 + cell - EPSILON, y0 + EPSILON)
                        && containsPoint(ring, x0 + EPSILON, y0 + cell - EPSILON)
                        && containsPoint(ring, x0 + cell - EPSILON, y0 + cell - EPSILON);
            }
        }
        var minimumCells = Math.max(1, (int) Math.ceil(minimumDimension / cell));
        var heights = new int[columns];
        Rect best = null;
        var bestArea = 0d;
        for (var r = 0; r < rows; r++) {
            for (var c = 0; c < columns; c++) {
                heights[c] = filled[r][c] ? heights[c] + 1 : 0;
            }
            var candidate = widestBar(heights, r, cell, box, minimumCells);
            if (candidate != null && candidate.area() > bestArea) {
                bestArea = candidate.area();
                best = candidate;
            }
        }
        return best;
    }

    /** Largest histogram rectangle on one raster row, translated back into feet. */
    private static Rect widestBar(int[] heights, int row, double cell, Bounds box, int minimumCells) {
        var stack = new java.util.ArrayDeque<Integer>();
        Rect best = null;
        var bestArea = 0d;
        for (var index = 0; index <= heights.length; index++) {
            var height = index == heights.length ? 0 : heights[index];
            while (!stack.isEmpty() && heights[stack.peek()] >= height) {
                var barHeight = heights[stack.pop()];
                var left = stack.isEmpty() ? 0 : stack.peek() + 1;
                var widthCells = index - left;
                if (barHeight >= minimumCells && widthCells >= minimumCells) {
                    var rect = new Rect(
                            box.minimumX() + left * cell,
                            box.minimumY() + (row + 1 - barHeight) * cell,
                            widthCells * cell,
                            barHeight * cell);
                    if (rect.area() > bestArea) {
                        bestArea = rect.area();
                        best = rect;
                    }
                }
            }
            stack.push(index);
        }
        return best;
    }

    /** Axis-aligned extents of a ring. */
    record Bounds(double minimumX, double maximumX, double minimumY, double maximumY) {
        double width() {
            return maximumX - minimumX;
        }

        double length() {
            return maximumY - minimumY;
        }
    }

    /** An axis-aligned rectangle in planning feet. */
    record Rect(double x, double y, double width, double length) {
        double area() {
            return width * length;
        }
    }
}

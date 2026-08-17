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

    /** Most extension zones one plot is worth planning; beyond this the remainder is margin. */
    private static final int MAX_RESIDUAL_PIECES = 4;
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
     * True when a point is inside the ring or standing on it, within {@code tolerance} feet.
     *
     * <p>A room that follows the boundary has its wall on the boundary, so its corners sit exactly
     * on the line. {@link #containsPoint} answers strictly and would call every one of them an
     * escape; what matters is that the room does not cross, and a wall on the line does not.</p>
     */
    static boolean insideOrOn(List<PlotVertex> ring, double x, double y, double tolerance) {
        if (containsPoint(ring, x, y)) {
            return true;
        }
        for (var index = 0; index < ring.size(); index++) {
            var from = ring.get(index);
            var to = ring.get((index + 1) % ring.size());
            if (distanceToSegment(from, to, x, y) <= tolerance) {
                return true;
            }
        }
        return false;
    }

    /** Shortest distance from a point to a segment, in feet. */
    private static double distanceToSegment(PlotVertex from, PlotVertex to, double x, double y) {
        var dx = to.x() - from.x();
        var dy = to.y() - from.y();
        var lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= EPSILON) {
            return Math.hypot(x - from.x(), y - from.y());
        }
        var t = Math.max(0, Math.min(1, ((x - from.x()) * dx + (y - from.y()) * dy) / lengthSquared));
        return Math.hypot(x - (from.x() + t * dx), y - (from.y() + t * dy));
    }

    /**
     * The buildable outline: the plot eroded inward by each edge's own setback.
     *
     * <p>Setbacks are a distance from the boundary <em>segment</em>, not from the infinite line it
     * lies on. Treating each edge as a half-plane clip is exact only while the outline stays convex;
     * on a re-entrant one it is catastrophic rather than merely conservative. An L-shaped plot has an
     * edge whose line runs straight through the far leg, so clipping against it deletes that leg
     * entirely — a 3,312 sq ft survey came back with 325 sq ft buildable, and every drawing planned
     * on it was a shed in one corner of an empty site.</p>
     *
     * <p>So the corner offset is computed properly, by moving each edge inward onto its own offset
     * line and re-intersecting the corners. The result is then checked against the property that
     * actually matters — no point of it lies closer to any boundary segment than that segment's
     * setback — and only accepted if it holds. Anything that fails falls back to the half-plane clip,
     * which understates the buildable area but can never overstate it.</p>
     */
    static List<PlotVertex> insetBySetbacks(PlotBoundary boundary, SetbackRule rule) {
        var offset = offsetInward(boundary, rule);
        return offset.isEmpty() ? clipInward(boundary, rule) : offset;
    }

    /**
     * Moves every edge onto its offset line and rebuilds the corners where they now meet.
     *
     * <p>Edges that the offset has turned back on themselves have been consumed by the setback and
     * are dropped before the corners are recomputed, which is what keeps a narrow neck from folding
     * the outline inside out.</p>
     *
     * @return the eroded outline, or an empty list when the result cannot be trusted
     */
    private static List<PlotVertex> offsetInward(PlotBoundary boundary, SetbackRule rule) {
        var edges = new ArrayList<>(boundary.edges());
        var lines = new ArrayList<double[]>(edges.size());
        for (var edge : edges) {
            var outward = outwardNormal(edge.from(), edge.to());
            if (outward[0] == 0 && outward[1] == 0) {
                return List.of();
            }
            // Inward normal, and the line every point of the buildable outline must sit on or behind.
            var nx = -outward[0];
            var ny = -outward[1];
            lines.add(new double[] {nx, ny, nx * edge.from().x() + ny * edge.from().y()
                    + edge.setbackFrom(rule)});
        }

        for (var pass = 0; pass <= boundary.vertices().size(); pass++) {
            if (lines.size() < 3) {
                return List.of();
            }
            var corners = corners(lines);
            if (corners.isEmpty()) {
                return List.of();
            }
            var collapsed = -1;
            for (var index = 0; index < corners.size(); index++) {
                var from = corners.get(index);
                var to = corners.get((index + 1) % corners.size());
                var original = edges.get(index);
                var originalX = original.to().x() - original.from().x();
                var originalY = original.to().y() - original.from().y();
                // A run that now points back the way it came has been eaten by the setback.
                if ((to.x() - from.x()) * originalX + (to.y() - from.y()) * originalY < -EPSILON) {
                    collapsed = index;
                    break;
                }
            }
            if (collapsed < 0) {
                return acceptable(boundary, rule, corners) ? corners : List.of();
            }
            lines.remove(collapsed);
            edges.remove(collapsed);
        }
        return List.of();
    }

    /** Corner points where each pair of consecutive offset lines meet. */
    private static List<PlotVertex> corners(List<double[]> lines) {
        var corners = new ArrayList<PlotVertex>(lines.size());
        for (var index = 0; index < lines.size(); index++) {
            var previous = lines.get((index - 1 + lines.size()) % lines.size());
            var current = lines.get(index);
            var determinant = previous[0] * current[1] - previous[1] * current[0];
            if (Math.abs(determinant) < 1e-9d) {
                // Collinear neighbours never meet; the corner simply slides along the shared line.
                corners.add(PlotVertex.of(current[0] * current[2], current[1] * current[2]).rounded());
                continue;
            }
            var x = (previous[2] * current[1] - current[2] * previous[1]) / determinant;
            var y = (previous[0] * current[2] - current[0] * previous[2]) / determinant;
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                return List.of();
            }
            corners.add(PlotVertex.of(x, y).rounded());
        }
        var cleaned = dropRepeatedVertices(corners, 0.01d);
        return cleaned.size() < 3 ? List.of() : cleaned;
    }

    /**
     * Confirms an eroded outline really does keep every setback.
     *
     * <p>Checked rather than assumed, because a naive offset is only correct while the polygon stays
     * simple. This is the guard that lets the exact path be used at all: if it cannot be proved to
     * respect every boundary segment it is discarded, not shipped.</p>
     */
    private static boolean acceptable(PlotBoundary boundary, SetbackRule rule, List<PlotVertex> corners) {
        if (corners.size() < 3 || selfIntersects(corners)) {
            return false;
        }
        var area = Math.abs(signedArea(corners));
        if (area < 1d || area > boundary.area() + 1d) {
            return false;
        }
        for (var corner : corners) {
            for (var edge : boundary.edges()) {
                var required = edge.setbackFrom(rule);
                if (required <= EPSILON) {
                    continue;
                }
                if (distanceToSegment(corner, edge.from(), edge.to()) < required - 0.05d) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Shortest distance from a point to a line segment, in feet. */
    static double distanceToSegment(PlotVertex point, PlotVertex from, PlotVertex to) {
        var dx = to.x() - from.x();
        var dy = to.y() - from.y();
        var lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= EPSILON) {
            return point.distanceTo(from);
        }
        var t = ((point.x() - from.x()) * dx + (point.y() - from.y()) * dy) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(point.x() - (from.x() + t * dx), point.y() - (from.y() + t * dy));
    }

    /** Conservative fallback: successive inward half-plane clips, one per boundary edge. */
    private static List<PlotVertex> clipInward(PlotBoundary boundary, SetbackRule rule) {
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

    /**
     * The part of a polygon lying inside an axis-aligned rectangle.
     *
     * <p>Four half-plane clips against a convex window, which is the case Sutherland-Hodgman is
     * exact for. Used to ask what the plot actually offers across one room's frontage: the answer is
     * the room's share of the boundary, slant and all, rather than a rectangle stopping short of
     * it.</p>
     *
     * @return the clipped ring, or an empty list when nothing of the polygon lies inside
     */
    static List<PlotVertex> clipToRect(List<PlotVertex> ring, double minimumX, double minimumY,
            double maximumX, double maximumY) {
        var current = ring;
        current = clipHalfPlane(current, 1, 0, minimumX);
        current = clipHalfPlane(current, -1, 0, -maximumX);
        current = clipHalfPlane(current, 0, 1, minimumY);
        current = clipHalfPlane(current, 0, -1, -maximumY);
        current = withoutRepeatedCorners(current);
        return current.size() < 3 ? List.of() : current;
    }

    /**
     * Drops corners that repeat the one before them.
     *
     * <p>Clipping a corner that already sits on the clip line emits it from both edges that meet
     * there. The duplicate encloses no area, but it is a zero-length wall to anything that walks the
     * ring looking for surfaces to draw.</p>
     */
    private static List<PlotVertex> withoutRepeatedCorners(List<PlotVertex> ring) {
        if (ring.size() < 2) {
            return ring;
        }
        var kept = new ArrayList<PlotVertex>(ring.size());
        for (var index = 0; index < ring.size(); index++) {
            var corner = ring.get(index);
            var previous = kept.isEmpty() ? ring.get(ring.size() - 1) : kept.get(kept.size() - 1);
            if (Math.abs(corner.x() - previous.x()) > EPSILON
                    || Math.abs(corner.y() - previous.y()) > EPSILON) {
                kept.add(corner);
            }
        }
        return kept;
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
        var raster = Raster.of(ring);
        return raster == null ? null : raster.largest(minimumDimension);
    }

    /**
     * The buildable rectangles left over once {@code taken} has been claimed.
     *
     * <p>This is what turns a single inscribed rectangle into a plan that follows the plot. The
     * packer is only able to plan inside a rectangle, so an irregular plot is served by planning the
     * largest one and then coming back for what is still standing empty beside it — each pass
     * yielding another rectangle the packer can also use.</p>
     *
     * <p>Pieces are returned largest first, and never overlap {@code taken}: a cell is surrendered
     * the moment it meets something already claimed. That leaves a hairline of unclaimed ground
     * along the shared edge, so each piece is snapped back onto whichever claimed edge it came
     * within one cell of. Slanted boundaries keep their margin — no arrangement of axis-aligned
     * rectangles can close a diagonal, and stepping one across it would draw a wall nobody builds.</p>
     *
     * @param minimumDimension shortest acceptable side in feet
     * @param minimumArea      floor below which a piece is margin rather than usable ground
     */
    static List<Rect> residualRectangles(List<PlotVertex> ring, List<Rect> taken,
            double minimumDimension, double minimumArea) {
        var raster = Raster.of(ring);
        if (raster == null) {
            return List.of();
        }
        var claimed = new ArrayList<>(taken);
        claimed.forEach(raster::clear);
        var pieces = new ArrayList<Rect>();
        for (var pass = 0; pass < MAX_RESIDUAL_PIECES; pass++) {
            var next = raster.largest(minimumDimension);
            if (next == null || next.area() < minimumArea) {
                break;
            }
            raster.clear(next);
            var snapped = snapToClaimed(next, claimed, raster.cell);
            pieces.add(snapped);
            claimed.add(snapped);
        }
        return List.copyOf(pieces);
    }

    /**
     * Closes the raster hairline between a residual piece and the ground already claimed.
     *
     * <p>Only edges that are already within one cell of a claimed edge move, so a piece is never
     * stretched onto ground the rasteriser found unbuildable.</p>
     */
    private static Rect snapToClaimed(Rect piece, List<Rect> claimed, double cell) {
        var minimumX = piece.x();
        var minimumY = piece.y();
        var maximumX = piece.x() + piece.width();
        var maximumY = piece.y() + piece.length();
        for (var other : claimed) {
            var otherRight = other.x() + other.width();
            var otherTop = other.y() + other.length();
            var overlapsRows = minimumY < otherTop - EPSILON && maximumY > other.y() + EPSILON;
            var overlapsColumns = minimumX < otherRight - EPSILON && maximumX > other.x() + EPSILON;
            if (overlapsRows) {
                if (Math.abs(minimumX - otherRight) <= cell + EPSILON) minimumX = otherRight;
                if (Math.abs(maximumX - other.x()) <= cell + EPSILON) maximumX = other.x();
            }
            if (overlapsColumns) {
                if (Math.abs(minimumY - otherTop) <= cell + EPSILON) minimumY = otherTop;
                if (Math.abs(maximumY - other.y()) <= cell + EPSILON) maximumY = other.y();
            }
        }
        return new Rect(round2(minimumX), round2(minimumY),
                round2(maximumX - minimumX), round2(maximumY - minimumY));
    }

    private static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    /**
     * The plot as a grid of buildable cells, which is the one representation both the inscribed
     * rectangle and the residual sweep need.
     *
     * <p>A cell counts only when all four corners are inside the ring, so nothing derived from it
     * can escape the envelope.</p>
     */
    private static final class Raster {
        private final Bounds box;
        private final double cell;
        private final int rows;
        private final int columns;
        private final boolean[][] filled;

        private Raster(Bounds box, double cell, int rows, int columns, boolean[][] filled) {
            this.box = box;
            this.cell = cell;
            this.rows = rows;
            this.columns = columns;
            this.filled = filled;
        }

        static Raster of(List<PlotVertex> ring) {
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
            return new Raster(box, cell, rows, columns, filled);
        }

        /** Surrenders every cell that meets {@code rect} at all, so nothing is ever claimed twice. */
        void clear(Rect rect) {
            for (var r = 0; r < rows; r++) {
                for (var c = 0; c < columns; c++) {
                    if (!filled[r][c]) continue;
                    var x0 = box.minimumX() + c * cell;
                    var y0 = box.minimumY() + r * cell;
                    if (x0 < rect.x() + rect.width() - EPSILON && x0 + cell > rect.x() + EPSILON
                            && y0 < rect.y() + rect.length() - EPSILON
                            && y0 + cell > rect.y() + EPSILON) {
                        filled[r][c] = false;
                    }
                }
            }
        }

        Rect largest(double minimumDimension) {
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

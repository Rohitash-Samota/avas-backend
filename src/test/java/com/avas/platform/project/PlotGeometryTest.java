package com.avas.platform.project;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlotGeometryTest {

    @Test
    void rectangularBoundaryReportsShoelaceArea() {
        var plot = PlotBoundary.rectangle(40, 60, Facing.NORTH);

        assertThat(plot.area()).isEqualTo(2_400d);
        assertThat(plot.perimeter()).isEqualTo(200d);
        assertThat(plot.irregular()).isFalse();
    }

    @Test
    void clockwiseInputIsNormalisedToCounterClockwise() {
        var clockwise = new PlotBoundary(List.of(
                PlotVertex.of(0, 0),
                PlotVertex.of(0, 60),
                PlotVertex.of(40, 60),
                PlotVertex.of(40, 0)), Facing.NORTH);

        assertThat(PlotGeometry.signedArea(clockwise.vertices())).isPositive();
    }

    @Test
    void selfIntersectingOutlineIsRejected() {
        assertThatThrownBy(() -> new PlotBoundary(List.of(
                PlotVertex.of(0, 0),
                PlotVertex.of(40, 60),
                PlotVertex.of(40, 0),
                PlotVertex.of(0, 60)), Facing.NORTH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("crosses itself");
    }

    @Test
    void degenerateOutlineIsRejected() {
        assertThatThrownBy(() -> new PlotBoundary(List.of(
                PlotVertex.of(0, 0),
                PlotVertex.of(10, 0),
                PlotVertex.of(20, 0)), Facing.NORTH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roadFacingSelectsTheFrontEdge() {
        var north = PlotBoundary.rectangle(40, 60, Facing.NORTH);
        var front = north.edges().stream()
                .filter(edge -> edge.side() == PlotBoundary.EdgeSide.FRONT).toList();

        assertThat(front).hasSize(1);
        // The north edge runs along y = 60 for a plot whose origin is the south-west corner.
        assertThat(front.get(0).from().y()).isEqualTo(60d);
        assertThat(front.get(0).to().y()).isEqualTo(60d);

        var east = PlotBoundary.rectangle(40, 60, Facing.EAST);
        var eastFront = east.edges().stream()
                .filter(edge -> edge.side() == PlotBoundary.EdgeSide.FRONT).toList();
        assertThat(eastFront).hasSize(1);
        assertThat(eastFront.get(0).from().x()).isEqualTo(40d);
    }

    @Test
    void setbacksShrinkTheRectangularEnvelopeOnEachClassifiedEdge() {
        var plot = PlotBoundary.rectangle(50, 80, Facing.NORTH);
        var rule = new SetbackRule(10, 7.5, 5, SetbackRule.ASSUMED);

        var envelope = BuildableEnvelope.derive(plot, rule, 2);

        assertThat(envelope.plotArea()).isEqualTo(4_000d);
        assertThat(envelope.buildableArea()).isCloseTo(2_500d, org.assertj.core.data.Offset.offset(1d));
        assertThat(envelope.footprintWidth()).isCloseTo(40d, org.assertj.core.data.Offset.offset(0.5d));
        assertThat(envelope.footprintLength()).isCloseTo(62.5d, org.assertj.core.data.Offset.offset(0.5d));
        assertThat(envelope.footprintX()).isCloseTo(5d, org.assertj.core.data.Offset.offset(0.5d));
        assertThat(envelope.footprintY()).isCloseTo(7.5d, org.assertj.core.data.Offset.offset(0.5d));
    }

    @Test
    void everyPackedFootprintCornerStaysInsideTheBuildableOutline() {
        // A re-entrant L-shaped plot: the conservative clip must never hand the packer a rectangle
        // that escapes the envelope, because rooms are placed blind inside it.
        var plot = new PlotBoundary(List.of(
                PlotVertex.of(0, 0),
                PlotVertex.of(60, 0),
                PlotVertex.of(60, 30),
                PlotVertex.of(35, 30),
                PlotVertex.of(35, 70),
                PlotVertex.of(0, 70)), Facing.NORTH);

        var envelope = BuildableEnvelope.derive(plot, SetbackRule.assumedFor(plot.area(), 2), 2);
        var outline = envelope.buildableOutline();

        assertThat(plot.irregular()).isTrue();
        assertThat(envelope.footprintArea()).isPositive();
        for (var x : List.of(envelope.footprintX(), envelope.footprintX() + envelope.footprintWidth())) {
            for (var y : List.of(envelope.footprintY(), envelope.footprintY() + envelope.footprintLength())) {
                assertThat(PlotGeometry.containsPoint(outline, clampInward(x, envelope, true),
                        clampInward(y, envelope, false)))
                        .as("footprint corner (%s, %s) inside buildable outline", x, y)
                        .isTrue();
            }
        }
    }

    @Test
    void irregularPlotRecordsAnUnusedEnvelopeNote() {
        var plot = new PlotBoundary(List.of(
                PlotVertex.of(0, 0),
                PlotVertex.of(70, 0),
                PlotVertex.of(70, 40),
                PlotVertex.of(40, 80),
                PlotVertex.of(0, 80)), Facing.SOUTH);

        var envelope = BuildableEnvelope.derive(plot, SetbackRule.assumedFor(plot.area(), 2), 2);

        assertThat(envelope.notes()).anyMatch(note -> note.contains("not rectangular"));
        assertThat(envelope.notes()).anyMatch(note -> note.contains("planning assumptions"));
        assertThat(envelope.footprintArea()).isLessThanOrEqualTo(envelope.buildableArea() + 1d);
    }

    @Test
    void oversizedSetbacksAreRejectedRatherThanSilentlyProducingASliver() {
        var plot = PlotBoundary.rectangle(20, 25, Facing.NORTH);

        assertThatThrownBy(() -> BuildableEnvelope.derive(plot, new SetbackRule(9, 9, 9, "TEST"), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expert review");
    }

    @Test
    void coverageAndOpenSpaceReconcileToThePlotArea() {
        var envelope = BuildableEnvelope.forRectangle(40, 60, Facing.EAST, 2);

        assertThat(envelope.footprintArea() + envelope.openSpaceArea())
                .isCloseTo(envelope.plotArea(), org.assertj.core.data.Offset.offset(0.01d));
        assertThat(envelope.coverageRatio()).isBetween(0d, 1d);
    }

    /** Nudges a footprint corner a hair inward so the strict point-in-polygon test is meaningful. */
    private double clampInward(double value, BuildableEnvelope envelope, boolean horizontal) {
        var low = horizontal ? envelope.footprintX() : envelope.footprintY();
        var high = low + (horizontal ? envelope.footprintWidth() : envelope.footprintLength());
        return Math.min(high - 0.01d, Math.max(low + 0.01d, value));
    }
}

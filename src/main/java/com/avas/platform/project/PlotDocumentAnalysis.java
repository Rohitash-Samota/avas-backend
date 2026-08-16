package com.avas.platform.project;

import java.util.List;

/**
 * What AVAS could read from an uploaded plot drawing.
 *
 * <p>Extraction is advisory. Nothing here becomes project geometry until the customer confirms it,
 * because a misread boundary silently invalidates every downstream drawing and estimate.</p>
 */
public record PlotDocumentAnalysis(
        String fileName,
        String mediaType,
        long byteSize,
        String checksum,
        int pageCount,
        /** Dimension-like values found in the drawing text, largest first, in feet. */
        List<Double> candidateDimensionsFeet,
        /** Boundary AVAS is willing to propose, or null when the drawing could not be read. */
        PlotBoundary proposedBoundary,
        /** 0-100. Anything below {@link #CONFIRMATION_THRESHOLD} must be confirmed by a human. */
        int confidence,
        List<String> notes,
        /** How the outline was arrived at, so the customer is never told a guess was a measurement. */
        Source source,
        /** The reading model, or the deterministic parser identity. Never blank. */
        String model,
        /** The preset the outline matches, so the wizard keeps its editing affordances. */
        String shape
) {
    /** Below this confidence the customer must confirm or correct the boundary before use. */
    public static final int CONFIRMATION_THRESHOLD = 70;

    /** Where a proposed outline came from. */
    public enum Source {
        /** Nothing could be read; the customer draws the outline. */
        NONE,
        /** Dimension text parsed out of a vector PDF by the platform. */
        DIMENSION_TEXT,
        /** The AVAS AI drawing reader, which can also read scans and photographs. */
        AVAS_AI
    }

    public PlotDocumentAnalysis {
        candidateDimensionsFeet = candidateDimensionsFeet == null ? List.of() : List.copyOf(candidateDimensionsFeet);
        notes = notes == null ? List.of() : List.copyOf(notes);
        source = source == null ? Source.NONE : source;
        model = model == null || model.isBlank() ? "avas-dimension-text-1" : model;
        shape = shape == null || shape.isBlank() ? "CUSTOM" : shape;
    }

    /** Compatibility view for callers created before reading provenance was recorded. */
    public PlotDocumentAnalysis(String fileName, String mediaType, long byteSize, String checksum,
            int pageCount, List<Double> candidateDimensionsFeet, PlotBoundary proposedBoundary,
            int confidence, List<String> notes) {
        this(fileName, mediaType, byteSize, checksum, pageCount, candidateDimensionsFeet,
                proposedBoundary, confidence, notes,
                proposedBoundary == null ? Source.NONE : Source.DIMENSION_TEXT,
                "avas-dimension-text-1", "CUSTOM");
    }

    /** True whenever the customer must review the reading before it can drive a layout. */
    public boolean requiresConfirmation() {
        return proposedBoundary == null || confidence < CONFIRMATION_THRESHOLD;
    }
}

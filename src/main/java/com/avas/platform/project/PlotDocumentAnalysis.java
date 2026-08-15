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
        List<String> notes
) {
    /** Below this confidence the customer must confirm or correct the boundary before use. */
    public static final int CONFIRMATION_THRESHOLD = 70;

    public PlotDocumentAnalysis {
        candidateDimensionsFeet = candidateDimensionsFeet == null ? List.of() : List.copyOf(candidateDimensionsFeet);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /** True whenever the customer must review the reading before it can drive a layout. */
    public boolean requiresConfirmation() {
        return proposedBoundary == null || confidence < CONFIRMATION_THRESHOLD;
    }
}

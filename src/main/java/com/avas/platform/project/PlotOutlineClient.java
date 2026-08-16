package com.avas.platform.project;

import java.util.List;

/**
 * Reads a plot outline out of an uploaded drawing or a customer's description.
 *
 * <p>Every result is a proposal. The boundary is re-validated by {@link PlotBoundary} before it
 * leaves this package and the customer confirms it before it becomes project geometry, so a
 * misread drawing can never silently drive a layout, an estimate or an approval.</p>
 */
interface PlotOutlineClient {
    /** True when outline reading is configured; callers keep their deterministic path otherwise. */
    boolean enabled();

    PlotOutlineSuggestion propose(PlotOutlineQuery query);

    /** A reader that is switched off, so callers fall back to their own text reading. */
    static PlotOutlineClient disabled() {
        return new PlotOutlineClient() {
            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public PlotOutlineSuggestion propose(PlotOutlineQuery query) {
                return PlotOutlineSuggestion.none(null);
            }
        };
    }

    /** Where the outline is being read from. */
    enum Source {
        DOCUMENT,
        DESCRIPTION
    }

    /**
     * @param content raw upload bytes; null for a description
     * @param pageText text the platform already extracted, which keeps a vector PDF cheap to read
     */
    record PlotOutlineQuery(
            Source source,
            Facing roadFacing,
            String tenantId,
            String description,
            String fileName,
            String mediaType,
            byte[] content,
            String pageText,
            List<Double> candidateDimensionsFeet,
            Double knownWidthFeet,
            Double knownLengthFeet
    ) {
        static PlotOutlineQuery forDocument(String tenantId, String fileName, String mediaType,
                byte[] content, String pageText, List<Double> dimensions, Facing roadFacing) {
            return new PlotOutlineQuery(Source.DOCUMENT, roadFacing, tenantId, "", fileName, mediaType,
                    content, pageText, dimensions, null, null);
        }

        static PlotOutlineQuery forDescription(String tenantId, String description, Facing roadFacing,
                Double knownWidthFeet, Double knownLengthFeet) {
            return new PlotOutlineQuery(Source.DESCRIPTION, roadFacing, tenantId, description, null, null,
                    null, "", List.of(), knownWidthFeet, knownLengthFeet);
        }
    }

    /**
     * @param boundary null when nothing credible could be read, which is a normal outcome
     * @param shape the preset the outline matches, so the wizard can keep its editing affordances
     */
    record PlotOutlineSuggestion(
            PlotBoundary boundary,
            String shape,
            int confidence,
            List<Double> dimensionsFeet,
            List<String> notes,
            String provider,
            String model,
            boolean fallbackUsed,
            List<String> warnings
    ) {
        public PlotOutlineSuggestion {
            dimensionsFeet = dimensionsFeet == null ? List.of() : List.copyOf(dimensionsFeet);
            notes = notes == null ? List.of() : List.copyOf(notes);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        static PlotOutlineSuggestion none(String note) {
            return new PlotOutlineSuggestion(null, "CUSTOM", 0, List.of(),
                    note == null ? List.of() : List.of(note), "NONE", "not-configured", false, List.of());
        }

        boolean hasBoundary() {
            return boundary != null;
        }
    }
}

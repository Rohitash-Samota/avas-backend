package com.avas.platform.project;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlotDocumentServiceTest {
    private final PlotDocumentService service = new PlotDocumentService();

    @Test
    void readsFeetAndInchesFromAVectorPlotDrawing() throws Exception {
        var pdf = pdfContaining("PLOT BOUNDARY", "NORTH SIDE 40'-6\"", "EAST SIDE 60'-0\"");

        var analysis = service.analyse("survey.pdf", "application/pdf", pdf, Facing.NORTH);

        assertThat(analysis.candidateDimensionsFeet()).containsExactly(60d, 40.5d);
        assertThat(analysis.proposedBoundary()).isNotNull();
        assertThat(analysis.proposedBoundary().area()).isCloseTo(2_430d, org.assertj.core.data.Offset.offset(1d));
        assertThat(analysis.checksum()).hasSize(64);
        assertThat(analysis.byteSize()).isEqualTo(pdf.length);
    }

    @Test
    void convertsMetricDimensionsToPlanningFeet() throws Exception {
        var pdf = pdfContaining("PLOT", "12.19 m", "18.29 m");

        var analysis = service.analyse("metric.pdf", "application/pdf", pdf, Facing.EAST);

        // 18.29 m and 12.19 m are the metric statements of a 60 x 40 ft plot.
        assertThat(analysis.candidateDimensionsFeet()).hasSize(2);
        assertThat(analysis.candidateDimensionsFeet().get(0)).isCloseTo(60d, org.assertj.core.data.Offset.offset(0.1d));
        assertThat(analysis.candidateDimensionsFeet().get(1)).isCloseTo(40d, org.assertj.core.data.Offset.offset(0.1d));
        assertThat(analysis.proposedBoundary().roadFacing()).isEqualTo(Facing.EAST);
    }

    @Test
    void alwaysRequiresConfirmationBecauseTheRoadEdgeCannotBeInferred() throws Exception {
        var pdf = pdfContaining("40'-0\"", "60'-0\"");

        var analysis = service.analyse("plot.pdf", "application/pdf", pdf, Facing.NORTH);

        assertThat(analysis.confidence()).isLessThan(PlotDocumentAnalysis.CONFIRMATION_THRESHOLD);
        assertThat(analysis.requiresConfirmation()).isTrue();
        assertThat(analysis.notes()).anyMatch(note -> note.contains("Confirm or redraw"));
    }

    @Test
    void scannedDrawingWithoutTextIsReportedRatherThanGuessed() throws Exception {
        var pdf = pdfContaining();

        var analysis = service.analyse("scan.pdf", "application/pdf", pdf, Facing.NORTH);

        assertThat(analysis.proposedBoundary()).isNull();
        assertThat(analysis.confidence()).isZero();
        assertThat(analysis.requiresConfirmation()).isTrue();
        assertThat(analysis.notes()).anyMatch(note -> note.contains("scanned drawing"));
    }

    @Test
    void rasterUploadIsAcceptedButNotMeasured() {
        var png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

        var analysis = service.analyse("plot.png", "image/png", png, Facing.NORTH);

        assertThat(analysis.proposedBoundary()).isNull();
        assertThat(analysis.requiresConfirmation()).isTrue();
        assertThat(analysis.notes()).anyMatch(note -> note.contains("cannot measure them automatically"));
    }

    @Test
    void ignoresValuesThatCannotBePlotEdges() {
        // Room labels, scale bars and sheet numbers all look like dimensions in raw text.
        var found = service.extractDimensions("SCALE 1'-0\" ROOM 9'-6\" PLOT 45'-0\" SITE 2000'-0\"");

        assertThat(found).containsExactly(45d);
    }

    @Test
    void rejectsUnsupportedAndOversizedUploads() {
        assertThatThrownBy(() -> service.analyse("plot.dwg", "application/acad", new byte[] {1}, Facing.NORTH))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PDF, PNG, JPEG or WebP");

        assertThatThrownBy(() -> service.analyse("huge.pdf", "application/pdf",
                new byte[(int) PlotDocumentService.MAXIMUM_BYTES + 1], Facing.NORTH))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("10 MB");

        assertThatThrownBy(() -> service.analyse("empty.pdf", "application/pdf", new byte[0], Facing.NORTH))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void unreadableBytesClaimingToBeAPdfAreRejectedCleanly() {
        assertThatThrownBy(() -> service.analyse("broken.pdf", "application/pdf",
                "not really a pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8), Facing.NORTH))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("could not be read");
    }

    private byte[] pdfContaining(String... lines) throws Exception {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            if (lines.length > 0) {
                try (var canvas = new PDPageContentStream(document, page)) {
                    canvas.beginText();
                    canvas.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    canvas.newLineAtOffset(60, 700);
                    for (var line : List.of(lines)) {
                        canvas.showText(line);
                        canvas.newLineAtOffset(0, -20);
                    }
                    canvas.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}

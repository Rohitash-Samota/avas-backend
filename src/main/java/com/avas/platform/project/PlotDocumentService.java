package com.avas.platform.project;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads an uploaded plot drawing well enough to propose a boundary for confirmation.
 *
 * <p>Deliberately not a full CAD or computer-vision pipeline. Vector PDFs carry dimension text that
 * can be parsed reliably; raster scans cannot be, and are reported as such rather than guessed at.
 * The customer confirms or corrects every value before it reaches the geometry engine.</p>
 */
@Service
public class PlotDocumentService {
    private static final Logger log = LoggerFactory.getLogger(PlotDocumentService.class);

    /** Upload ceiling. Survey PDFs are small; anything larger is not a plot drawing. */
    static final long MAXIMUM_BYTES = 10L * 1024 * 1024;
    private static final List<String> ACCEPTED = List.of(
            "application/pdf", "image/png", "image/jpeg", "image/webp");

    /** Feet-and-inches, decimal feet, or metres, each with the unit spelled out or symbolised. */
    private static final Pattern FEET_INCHES = Pattern.compile(
            "(\\d{1,4}(?:\\.\\d{1,2})?)\\s*(?:'|\\u2032|ft\\b|feet\\b)(?:\\s*[-\\s]?\\s*(\\d{1,2}(?:\\.\\d{1,2})?)\\s*(?:\"|\\u2033|in\\b))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern METRES = Pattern.compile(
            "(\\d{1,4}(?:\\.\\d{1,3})?)\\s*(?:m\\b|mtr\\b|metre|meter)", Pattern.CASE_INSENSITIVE);

    /** Plot edges outside this band are almost certainly not plot edges. */
    private static final double MINIMUM_EDGE_FEET = 10d;
    private static final double MAXIMUM_EDGE_FEET = 1_000d;
    private static final double FEET_PER_METRE = 3.280839895d;

    public PlotDocumentAnalysis analyse(String fileName, String mediaType, byte[] content, Facing roadFacing) {
        var normalizedType = mediaType == null ? "" : mediaType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        if (content == null || content.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded plot drawing is empty");
        }
        if (content.length > MAXIMUM_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Plot drawings must be 10 MB or smaller");
        }
        if (!ACCEPTED.contains(normalizedType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Upload the plot drawing as PDF, PNG, JPEG or WebP");
        }

        var checksum = checksum(content);
        var safeName = safeName(fileName);
        if (!"application/pdf".equals(normalizedType)) {
            return new PlotDocumentAnalysis(safeName, normalizedType, content.length, checksum, 1,
                    List.of(), null, 0, List.of(
                    "AVAS stores raster plot images but cannot measure them automatically.",
                    "Enter or draw the boundary corners so the buildable envelope can be derived."));
        }
        return analysePdf(safeName, normalizedType, content, checksum, roadFacing);
    }

    private PlotDocumentAnalysis analysePdf(String fileName, String mediaType, byte[] content,
            String checksum, Facing roadFacing) {
        var notes = new ArrayList<String>();
        try (var document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "The plot drawing is password protected; upload an unprotected copy");
            }
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            var text = stripper.getText(document);
            var dimensions = extractDimensions(text);

            if (dimensions.isEmpty()) {
                notes.add("No dimension text was found; this is likely a scanned drawing rather than a vector PDF.");
                notes.add("Enter or draw the boundary corners so the buildable envelope can be derived.");
                return new PlotDocumentAnalysis(fileName, mediaType, content.length, checksum,
                        document.getNumberOfPages(), List.of(), null, 0, notes);
            }

            // Two credible edges are enough to propose the rectangle a plot drawing usually implies.
            // Anything more complex stays a proposal the customer edits into the real outline.
            if (dimensions.size() < 2) {
                notes.add("Only one dimension could be read, so the second plot side must be supplied.");
                return new PlotDocumentAnalysis(fileName, mediaType, content.length, checksum,
                        document.getNumberOfPages(), dimensions, null, 30, notes);
            }
            var width = dimensions.get(1);
            var length = dimensions.get(0);
            var boundary = PlotBoundary.rectangle(width, length,
                    roadFacing == null ? Facing.NORTH : roadFacing);
            notes.add("Proposed a " + trim(width) + " x " + trim(length)
                    + " ft rectangle from the two largest dimensions found in the drawing text.");
            notes.add("Confirm or redraw the outline; AVAS cannot verify which edge faces the road.");
            if (dimensions.size() > 2) {
                notes.add("The drawing also mentions " + (dimensions.size() - 2)
                        + " other dimension(s); an irregular plot must be drawn corner by corner.");
            }
            var confidence = dimensions.size() == 2 ? 60 : 45;
            return new PlotDocumentAnalysis(fileName, mediaType, content.length, checksum,
                    document.getNumberOfPages(), dimensions, boundary, confidence, notes);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            log.warn("Plot drawing could not be parsed: {}", exception.toString());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The plot drawing could not be read; upload a different export or enter the boundary manually");
        }
    }

    /** Plausible plot edges in feet, largest first and de-duplicated. */
    List<Double> extractDimensions(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var found = new ArrayList<Double>();
        var feetMatcher = FEET_INCHES.matcher(text);
        while (feetMatcher.find()) {
            var feet = Double.parseDouble(feetMatcher.group(1));
            if (feetMatcher.group(2) != null) {
                feet += Double.parseDouble(feetMatcher.group(2)) / 12d;
            }
            accept(found, feet);
        }
        var metreMatcher = METRES.matcher(text);
        while (metreMatcher.find()) {
            accept(found, Double.parseDouble(metreMatcher.group(1)) * FEET_PER_METRE);
        }
        found.sort(java.util.Comparator.reverseOrder());
        return List.copyOf(found);
    }

    private void accept(List<Double> found, double feet) {
        if (feet < MINIMUM_EDGE_FEET || feet > MAXIMUM_EDGE_FEET) {
            return;
        }
        var rounded = Math.round(feet * 100d) / 100d;
        // Repeated labels for the same edge are normal on a survey drawing.
        if (found.stream().noneMatch(value -> Math.abs(value - rounded) < 0.05d)) {
            found.add(rounded);
        }
    }

    private String checksum(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required to record upload provenance", exception);
        }
    }

    private String safeName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "plot-drawing";
        }
        var trimmed = fileName.substring(fileName.lastIndexOf('/') + 1);
        trimmed = trimmed.substring(trimmed.lastIndexOf('\\') + 1);
        var cleaned = trimmed.replaceAll("[^A-Za-z0-9._-]", "-");
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}

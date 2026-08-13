package com.avas.platform.project;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small vector-drawing toolkit shared by independently testable report page renderers. */
final class ReportPdfSupport {
    static final PDRectangle PAGE = PDRectangle.A4;
    static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    static final Color INK = new Color(47, 42, 36);
    static final Color MUTED = new Color(112, 103, 91);
    static final Color ACCENT = new Color(176, 122, 79);
    static final Color LINE = new Color(225, 217, 205);
    static final Color PAPER = new Color(250, 248, 244);
    static final Color MINT = new Color(231, 235, 222);
    static final Color WHITE = Color.WHITE;

    private ReportPdfSupport() {}

    static PDPage page() {
        return new PDPage(PAGE);
    }

    static void pageBackground(PDPageContentStream canvas) throws IOException {
        fill(canvas, WHITE, 0, 0, PAGE.getWidth(), PAGE.getHeight());
    }

    static void header(PDPageContentStream canvas, String label) throws IOException {
        text(canvas, BOLD, 16, INK, "AVAS", 36, 804);
        text(canvas, REGULAR, 5.5f, MUTED, "ADAPTIVE HOME PLANNING", 36, 794);
        textRight(canvas, REGULAR, 6, MUTED, label, 559, 802);
        line(canvas, LINE, .8f, 36, 784, 559, 784);
    }

    static void footer(PDPageContentStream canvas, String projectCode, String detail) throws IOException {
        line(canvas, LINE, .7f, 36, 35, 559, 35);
        text(canvas, BOLD, 6, INK, "AVAS", 36, 23);
        text(canvas, REGULAR, 5.2f, MUTED, safe(projectCode), 72, 23);
        textRight(canvas, REGULAR, 5.2f, MUTED, safe(detail), 559, 23);
    }

    static void card(PDPageContentStream canvas, float x, float y, float width, float height,
            boolean emphasized) throws IOException {
        fill(canvas, emphasized ? MINT : WHITE, x, y, width, height);
        stroke(canvas, emphasized ? ACCENT : LINE, emphasized ? 1.1f : .7f, x, y, width, height);
    }

    static void fill(PDPageContentStream canvas, Color color, float x, float y, float width, float height)
            throws IOException {
        canvas.setNonStrokingColor(color);
        canvas.addRect(x, y, width, height);
        canvas.fill();
    }

    static void stroke(PDPageContentStream canvas, Color color, float width,
            float x, float y, float boxWidth, float boxHeight) throws IOException {
        canvas.setStrokingColor(color);
        canvas.setLineWidth(width);
        canvas.addRect(x, y, boxWidth, boxHeight);
        canvas.stroke();
    }

    static void line(PDPageContentStream canvas, Color color, float width,
            float fromX, float fromY, float toX, float toY) throws IOException {
        canvas.setStrokingColor(color);
        canvas.setLineWidth(width);
        canvas.moveTo(fromX, fromY);
        canvas.lineTo(toX, toY);
        canvas.stroke();
    }

    static void text(PDPageContentStream canvas, PDFont font, float size, Color color,
            String value, float x, float y) throws IOException {
        canvas.beginText();
        canvas.setFont(font, size);
        canvas.setNonStrokingColor(color);
        canvas.newLineAtOffset(x, y);
        canvas.showText(safe(value));
        canvas.endText();
    }

    static void textRight(PDPageContentStream canvas, PDFont font, float size, Color color,
            String value, float rightX, float y) throws IOException {
        var safeValue = safe(value);
        text(canvas, font, size, color, safeValue, rightX - textWidth(font, size, safeValue), y);
    }

    static List<String> wrap(String value, PDFont font, float size, float maxWidth) throws IOException {
        if (value == null || value.isBlank()) return List.of();
        var result = new ArrayList<String>();
        var line = new StringBuilder();
        for (var word : safe(value).trim().split("\\s+")) {
            var candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && textWidth(font, size, candidate) > maxWidth) {
                result.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty()) result.add(line.toString());
        return List.copyOf(result);
    }

    static String fit(String value, PDFont font, float size, float width) throws IOException {
        var safeValue = safe(value);
        if (textWidth(font, size, safeValue) <= width) return safeValue;
        var suffix = "...";
        while (!safeValue.isEmpty() && textWidth(font, size, safeValue + suffix) > width) {
            safeValue = safeValue.substring(0, safeValue.length() - 1);
        }
        return safeValue.stripTrailing() + suffix;
    }

    static String money(long value, String currency) {
        var prefix = currency == null || currency.isBlank() ? "INR" : currency.toUpperCase(Locale.ROOT);
        return prefix + " " + String.format(Locale.US, "%,d", value);
    }

    static String money(java.math.BigDecimal value, String currency) {
        var prefix = currency == null || currency.isBlank() ? "INR" : currency.toUpperCase(Locale.ROOT);
        var safeValue = value == null ? java.math.BigDecimal.ZERO : value;
        return prefix + " " + new java.text.DecimalFormat("#,##0.00",
                java.text.DecimalFormatSymbols.getInstance(Locale.US)).format(safeValue);
    }

    static String grouped(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    static String title(String value) {
        if (value == null || value.isBlank()) return "Not recorded";
        var result = new StringBuilder();
        for (var word : value.toLowerCase(Locale.ROOT).replace('_', ' ').split("\\s+")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    static String present(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static String safe(String value) {
        if (value == null) return "";
        return value.replace('\u2013', '-').replace('\u2014', '-').replace('\u00d7', 'x')
                .replace('\u2018', '\'').replace('\u2019', '\'').replace('\u201c', '"').replace('\u201d', '"')
                .replace("\u20b9", "INR ").replaceAll("[^\\x20-\\x7E]", "?");
    }

    private static float textWidth(PDFont font, float size, String value) throws IOException {
        return font.getStringWidth(safe(value)) / 1000f * size;
    }
}

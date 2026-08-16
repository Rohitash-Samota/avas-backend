package com.avas.platform.project;

import java.util.List;
import java.util.Map;

/**
 * Structured geometry for one drawing candidate.
 *
 * <p>Carries the site context alongside the rooms so a site plan, a scaled floor plan and an area
 * schedule can all be rendered from this one document without recomputing the envelope.</p>
 */
public record GeometryDocument(
        String unit,
        double plotWidth,
        double plotLength,
        List<RoomGeometry> rooms,
        List<Map<String, Object>> doors,
        List<Map<String, Object>> windows,
        List<PlotVertex> plotOutline,
        List<PlotVertex> buildableOutline,
        SetbackRule setbacks,
        double plotArea,
        double buildableArea,
        List<SiteElement> siteElements
) {
    public GeometryDocument {
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
        doors = doors == null ? List.of() : List.copyOf(doors);
        windows = windows == null ? List.of() : List.copyOf(windows);
        plotOutline = plotOutline == null ? List.of() : List.copyOf(plotOutline);
        buildableOutline = buildableOutline == null ? List.of() : List.copyOf(buildableOutline);
        siteElements = siteElements == null ? List.of() : List.copyOf(siteElements);
    }

    /** Compatibility view for documents written before open site features were modelled. */
    public GeometryDocument(String unit, double plotWidth, double plotLength, List<RoomGeometry> rooms,
            List<Map<String, Object>> doors, List<Map<String, Object>> windows,
            List<PlotVertex> plotOutline, List<PlotVertex> buildableOutline, SetbackRule setbacks,
            double plotArea, double buildableArea) {
        this(unit, plotWidth, plotLength, rooms, doors, windows, plotOutline, buildableOutline,
                setbacks, plotArea, buildableArea, List.of());
    }

    /** Compatibility view for callers created before the plot outline was modelled. */
    public GeometryDocument(String unit, double plotWidth, double plotLength, List<RoomGeometry> rooms,
            List<Map<String, Object>> doors, List<Map<String, Object>> windows) {
        this(unit, plotWidth, plotLength, rooms, doors, windows, List.of(), List.of(), null,
                plotWidth * plotLength, 0d, List.of());
    }

    /** True when a site plan can be drawn, rather than only the floor plates. */
    public boolean hasSiteContext() {
        return plotOutline.size() >= 3;
    }
}

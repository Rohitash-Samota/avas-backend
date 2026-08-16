package com.avas.platform.project;

/**
 * Something planned on the plot but outside the building: parking, a driveway, garden, a sit-out.
 *
 * <p>Deliberately not a {@link RoomGeometry}. A room is enclosed floor area that a customer pays to
 * build and that every downstream number is derived from — built-up area, the quantity takeoff, the
 * cost per square foot. Open parking on the approach and lawn along a side setback are none of those
 * things, and modelling them as rooms would inflate the built-up area a customer is quoted on and
 * bill them for a garden as though it were a slab.</p>
 *
 * <p>They still belong in the geometry document, because they are the difference between a floor
 * plate and a plot a family can picture living on, and because open parking is what makes a
 * ground-floor garage unnecessary.</p>
 */
public record SiteElement(
        String id,
        String type,
        double x,
        double y,
        double width,
        double length,
        double area,
        String label
) {
    public SiteElement {
        area = Math.round(width * length * 100d) / 100d;
    }

    static SiteElement of(String id, String type, String label, double x, double y,
            double width, double length) {
        return new SiteElement(id, type, round(x), round(y), round(width), round(length), 0d, label);
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}

package com.avas.platform.catalog;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The construction taxonomy every collected product is filed under.
 *
 * <p>Suppliers each invent their own category tree — one site sells "Building Material &gt; Cement
 * &amp; Concrete", the next "Grey Cement", the next "RMC". Storing those verbatim gives a catalogue
 * that cannot be searched across sources, so the site's own wording is kept on the product as
 * {@code sourceCategoryPath} and this enum is what the catalogue is queried by.</p>
 *
 * <p>{@link #classify} exists because the collector and the platform must not disagree about what a
 * product is. A collector may send the category it derived; when it sends nothing, or sends a value
 * this version of the platform does not know, the server derives one here rather than storing a
 * blank that no search will ever match.</p>
 */
public enum ConstructionCategory {
    CEMENT_CONCRETE("Cement, concrete and mortar",
            "cement", "concrete", "mortar", "ready mix", "readymix", "rmc", "opc", "ppc", "psc",
            "grout", "screed", "plaster mix", "dry mix"),
    MASONRY("Bricks, blocks and stone masonry",
            "brick", "block", "aac", "clc", "fly ash", "flyash", "hollow block", "paver",
            "kerb", "boundary stone", "masonry"),
    AGGREGATE("Sand, aggregate and raw material",
            "sand", "aggregate", "gravel", "grit", "ballast", "crusher", "stone dust", "murram",
            "m sand", "river sand", "boulder", "kapchi"),
    STEEL_METAL("Steel, TMT and metal products",
            "steel", "tmt", "rebar", "reinforcement bar", "binding wire", "girder", "angle",
            "channel", "beam", "purlin", "ms pipe", "gi pipe", "structural", "iron", "metal sheet",
            "wire mesh", "weld mesh"),
    TILES_STONE("Tiles, stone and flooring",
            "tile", "vitrified", "ceramic", "marble", "granite", "flooring", "wall cladding",
            "cladding", "mosaic", "kota", "sandstone", "quartz", "skirting", "grout tile"),
    PAINT_CHEMICALS("Paint, waterproofing and construction chemicals",
            "paint", "primer", "emulsion", "enamel", "distemper", "putty", "waterproof",
            "adhesive", "sealant", "epoxy", "admixture", "construction chemical", "bond coat",
            "curing compound", "silicone", "varnish", "thinner"),
    ELECTRICAL("Electrical, wiring and lighting",
            "electrical", "wire", "cable", "switch", "socket", "mcb", "rccb", "distribution board",
            "conduit", "light", "lamp", "led", "luminaire", "fan", "inverter", "meter", "busbar"),
    PLUMBING_SANITARY("Plumbing, fittings and sanitaryware",
            "plumbing", "cpvc", "upvc", "pvc pipe", "sanitary", "washbasin", "wash basin",
            "water closet", "wc", "faucet", "tap", "valve", "water tank", "cistern", "shower",
            "drainage", "trap", "bib cock", "urinal", "sink"),
    DOORS_WINDOWS("Doors, windows, glass and hardware",
            "door", "window", "glass", "glazing", "aluminium", "aluminum", "hinge", "lock",
            "handle", "hardware", "frame", "shutter", "railing", "grill", "mosquito mesh",
            "louver", "chaukhat"),
    WOOD_PANELS("Wood, plywood, laminate and boards",
            "wood", "timber", "plywood", "ply", "laminate", "veneer", "mdf", "particle board",
            "block board", "hdmr", "wpc", "pvc board", "teak", "sal wood"),
    ROOFING_INSULATION("Roofing, ceiling, insulation and gypsum",
            "roof", "roofing", "ceiling", "false ceiling", "insulation", "gypsum", "pop",
            "plaster of paris", "shingle", "polycarbonate sheet", "corrugated", "thermocol",
            "xps", "rockwool", "acoustic panel", "asbestos"),
    TOOLS_EQUIPMENT("Tools, machinery and safety equipment",
            "tool", "drill", "cutter", "grinder", "mixer machine", "vibrator", "scaffolding",
            "shuttering", "formwork", "safety", "helmet", "harness", "gloves", "ladder",
            "trowel", "wheelbarrow", "compactor", "jcb", "crane", "hoist", "machinery"),
    /** Construction-related but not one of the named families. Kept so nothing useful is discarded. */
    GENERAL_CONSTRUCTION("Other construction material");

    private final String label;
    private final List<String> keywords;

    ConstructionCategory(String label, String... keywords) {
        this.label = label;
        this.keywords = List.of(keywords);
    }

    public String label() {
        return label;
    }

    public List<String> keywords() {
        return keywords;
    }

    /**
     * Reads a category the collector already decided on, without guessing.
     *
     * <p>Returns {@code null} for an unknown value so the caller can fall back to {@link #classify},
     * rather than silently filing a misread product under a real category.</p>
     */
    public static ConstructionCategory parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var normalised = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return Arrays.stream(values())
                .filter(candidate -> candidate.name().equals(normalised))
                .findFirst()
                .orElse(null);
    }

    /**
     * Derives a category from whatever text the listing gave us.
     *
     * <p>Text is weighed in the order supplied, so a caller passes the site's own category path
     * before the product name: "Cement" in a breadcrumb is a stronger signal than "cement" appearing
     * in the description of a mixer machine. The longest keyword wins within one piece of text for
     * the same reason — "wash basin" must not be read as "basin" in a plumbing-versus-tiles tie.</p>
     */
    public static ConstructionCategory classify(String... texts) {
        for (var text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            var haystack = text.toLowerCase(Locale.ROOT);
            ConstructionCategory best = null;
            var bestLength = 0;
            for (var candidate : values()) {
                for (var keyword : candidate.keywords) {
                    if (keyword.length() > bestLength && haystack.contains(keyword)) {
                        best = candidate;
                        bestLength = keyword.length();
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return GENERAL_CONSTRUCTION;
    }
}

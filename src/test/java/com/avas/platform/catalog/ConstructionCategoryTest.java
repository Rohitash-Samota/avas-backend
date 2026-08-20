package com.avas.platform.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConstructionCategoryTest {

    @Test
    void aSiteBreadcrumbOutweighsWordsFoundInTheProductName() {
        // "Cement mixer machine" contains "cement", but the site filed it under tools and that is
        // the stronger signal. Reading the name first would put machinery in the cement aisle.
        assertThat(ConstructionCategory.classify("Tools & Machinery > Concrete Mixers",
                null, "Cement Mixer Machine 10/7", null))
                .isEqualTo(ConstructionCategory.TOOLS_EQUIPMENT);
    }

    @Test
    void theLongestMatchingKeywordWinsWithinOnePieceOfText() {
        // "wash basin" is plumbing; "basin" alone is not a keyword, and "ceramic" would otherwise
        // pull a ceramic wash basin into tiles.
        assertThat(ConstructionCategory.classify("Ceramic Wash Basin, wall mounted"))
                .isEqualTo(ConstructionCategory.PLUMBING_SANITARY);
    }

    @Test
    void eachNamedFamilyIsReachableFromOrdinaryListingWording() {
        assertThat(ConstructionCategory.classify("OPC 53 Grade Cement")).isEqualTo(ConstructionCategory.CEMENT_CONCRETE);
        assertThat(ConstructionCategory.classify("AAC Block 600x200x100")).isEqualTo(ConstructionCategory.MASONRY);
        assertThat(ConstructionCategory.classify("River Sand per CFT")).isEqualTo(ConstructionCategory.AGGREGATE);
        assertThat(ConstructionCategory.classify("TMT Bar Fe500D 12mm")).isEqualTo(ConstructionCategory.STEEL_METAL);
        assertThat(ConstructionCategory.classify("Vitrified Floor Tile 600x600")).isEqualTo(ConstructionCategory.TILES_STONE);
        assertThat(ConstructionCategory.classify("Interior Emulsion Paint 20L")).isEqualTo(ConstructionCategory.PAINT_CHEMICALS);
        assertThat(ConstructionCategory.classify("2.5 sq mm copper wire")).isEqualTo(ConstructionCategory.ELECTRICAL);
        assertThat(ConstructionCategory.classify("CPVC Pipe 25mm")).isEqualTo(ConstructionCategory.PLUMBING_SANITARY);
        assertThat(ConstructionCategory.classify("Flush Door with Frame")).isEqualTo(ConstructionCategory.DOORS_WINDOWS);
        assertThat(ConstructionCategory.classify("Marine Plywood 19mm")).isEqualTo(ConstructionCategory.WOOD_PANELS);
        assertThat(ConstructionCategory.classify("Gypsum False Ceiling Board")).isEqualTo(ConstructionCategory.ROOFING_INSULATION);
        assertThat(ConstructionCategory.classify("Safety Helmet with chin strap")).isEqualTo(ConstructionCategory.TOOLS_EQUIPMENT);
    }

    @Test
    void somethingUnrecognisedIsKeptRatherThanDiscarded() {
        // A product the lexicon has no word for is still a product the crawl decided to collect.
        // Filing it as general keeps it findable instead of losing it to a blank category.
        assertThat(ConstructionCategory.classify("Assorted site consumables"))
                .isEqualTo(ConstructionCategory.GENERAL_CONSTRUCTION);
        assertThat(ConstructionCategory.classify((String) null))
                .isEqualTo(ConstructionCategory.GENERAL_CONSTRUCTION);
    }

    @Test
    void anUnknownDeclaredCategoryIsRejectedRatherThanGuessedAt() {
        // parse() is how a collector's own decision is read. Returning null lets the caller fall
        // back to classify() explicitly, instead of a typo landing in a real category.
        assertThat(ConstructionCategory.parse("CEMENT_CONCRETE")).isEqualTo(ConstructionCategory.CEMENT_CONCRETE);
        assertThat(ConstructionCategory.parse("cement-concrete")).isEqualTo(ConstructionCategory.CEMENT_CONCRETE);
        assertThat(ConstructionCategory.parse("CEMNT")).isNull();
        assertThat(ConstructionCategory.parse("  ")).isNull();
    }
}

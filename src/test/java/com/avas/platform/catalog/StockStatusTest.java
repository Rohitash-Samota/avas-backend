package com.avas.platform.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockStatusTest {

    @Test
    void aMissingAvailabilityReadsAsUnknownRatherThanInStock() {
        // Most construction listings never state stock. Defaulting to IN_STOCK would make the
        // catalogue claim something the source never said.
        assertThat(StockStatus.parse(null)).isEqualTo(StockStatus.UNKNOWN);
        assertThat(StockStatus.parse("")).isEqualTo(StockStatus.UNKNOWN);
        assertThat(StockStatus.parse("Call for details")).isEqualTo(StockStatus.UNKNOWN);
    }

    @Test
    void schemaOrgAndPlainEnglishWordingBothRead() {
        assertThat(StockStatus.parse("https://schema.org/InStock")).isEqualTo(StockStatus.IN_STOCK);
        assertThat(StockStatus.parse("OutOfStock")).isEqualTo(StockStatus.OUT_OF_STOCK);
        assertThat(StockStatus.parse("Currently sold out")).isEqualTo(StockStatus.OUT_OF_STOCK);
        assertThat(StockStatus.parse("Made to order")).isEqualTo(StockStatus.MADE_TO_ORDER);
    }

    @Test
    void outOfStockIsNotReadAsInStockBecauseItContainsStock() {
        // "Out of stock" also matches on the shorter "stock" phrasings, so order of testing matters.
        assertThat(StockStatus.parse("Out of stock, available in 2 weeks"))
                .isEqualTo(StockStatus.OUT_OF_STOCK);
    }
}

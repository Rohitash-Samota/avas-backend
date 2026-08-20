package com.avas.platform.catalog;

import java.util.Locale;

/**
 * Whether the listing claimed the product was buyable when it was read.
 *
 * <p>{@link #UNKNOWN} is the honest default and by far the most common value: most Indian
 * construction listings quote on enquiry and never state stock at all. It exists so a missing field
 * reads as missing rather than as {@link #IN_STOCK}.</p>
 */
public enum StockStatus {
    IN_STOCK,
    OUT_OF_STOCK,
    MADE_TO_ORDER,
    DISCONTINUED,
    UNKNOWN;

    public static StockStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        var text = value.trim().toLowerCase(Locale.ROOT);
        if (text.contains("out of stock") || text.contains("outofstock") || text.contains("sold out")) {
            return OUT_OF_STOCK;
        }
        if (text.contains("discontinued")) {
            return DISCONTINUED;
        }
        if (text.contains("made to order") || text.contains("madetoorder") || text.contains("pre order")
                || text.contains("preorder") || text.contains("backorder")) {
            return MADE_TO_ORDER;
        }
        if (text.contains("in stock") || text.contains("instock") || text.contains("available")) {
            return IN_STOCK;
        }
        return UNKNOWN;
    }
}

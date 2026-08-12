package com.avas.platform.commerce;

public record Product(
        String id,
        String name,
        String description,
        String category,
        long unitPrice,
        String icon
) {
}

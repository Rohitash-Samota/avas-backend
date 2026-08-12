package com.avas.platform.commerce;

public record OrderLine(
        String productId,
        String name,
        int quantity,
        long unitPrice,
        long lineTotal
) {
}

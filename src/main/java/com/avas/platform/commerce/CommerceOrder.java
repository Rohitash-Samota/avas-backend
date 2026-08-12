package com.avas.platform.commerce;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommerceOrder(
        UUID id,
        UUID userId,
        String projectId,
        OrderStatus status,
        String orderType,
        String currency,
        long total,
        List<OrderLine> lines,
        String buyerName,
        String buyerEmail,
        String buyerPhone,
        Instant createdAt,
        Instant updatedAt
) {
}

package com.avas.platform.commerce;

import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID orderId,
        long amount,
        String status,
        String reason,
        String gatewayRefundId
) {
}

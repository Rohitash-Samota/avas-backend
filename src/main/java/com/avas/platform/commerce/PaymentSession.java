package com.avas.platform.commerce;

import java.time.Instant;
import java.util.UUID;

public record PaymentSession(
        UUID id,
        UUID orderId,
        PaymentStatus status,
        String provider,
        String mode,
        String gatewayOrderId,
        String gatewayPaymentId,
        String publicKey,
        long amount,
        String currency,
        boolean checkoutReady,
        Instant createdAt,
        Instant updatedAt
) {
}

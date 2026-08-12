package com.avas.platform.commerce;

import java.time.Instant;
import java.util.UUID;

public record WalletTransactionResponse(
        UUID id,
        String type,
        long amount,
        long balanceAfter,
        String referenceType,
        String referenceId,
        String description,
        Instant createdAt
) {
}

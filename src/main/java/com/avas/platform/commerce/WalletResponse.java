package com.avas.platform.commerce;

import java.time.Instant;
import java.util.UUID;

public record WalletResponse(UUID id, long balance, String currency, String status, Instant updatedAt) {
}

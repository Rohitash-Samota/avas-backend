package com.avas.platform.commerce;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record WalletTopUpRequest(@Min(100) @Max(10_000_000) long amount) {
}

package com.avas.platform.commerce;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundRequest(@NotBlank @Size(max = 500) String reason) {
}

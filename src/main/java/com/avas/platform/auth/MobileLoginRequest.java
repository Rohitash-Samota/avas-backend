package com.avas.platform.auth;

import jakarta.validation.constraints.NotBlank;

public record MobileLoginRequest(@NotBlank String mobileNumber, @NotBlank String password) {
}

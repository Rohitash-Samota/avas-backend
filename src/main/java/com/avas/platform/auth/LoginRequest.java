package com.avas.platform.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(String identifier, String email, String mobileNumber, @NotBlank String password) {
    @AssertTrue(message = "Provide an email, mobile number, username or full name")
    public boolean isIdentifierPresent() {
        return notBlank(identifier) || notBlank(email) || notBlank(mobileNumber);
    }

    String resolvedIdentifier() {
        if (notBlank(identifier)) return identifier.trim();
        if (notBlank(email)) return email.trim();
        return mobileNumber == null ? "" : mobileNumber.trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

package com.avas.platform.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @Pattern(regexp = "^$|^[a-zA-Z0-9][a-zA-Z0-9._-]{2,59}$",
                message = "username must contain 3-60 letters, numbers, dots, underscores or hyphens") String username,
        @Email @Size(max = 190) String email,
        @Pattern(regexp = "^$|^[+0-9][0-9 -]{7,20}$") String mobileNumber,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Pattern(regexp = "^(INDIVIDUAL|BUILDER)$",
                message = "accountType must be INDIVIDUAL or BUILDER") String accountType
) {
}

package com.avas.platform.auth;

import jakarta.validation.constraints.NotBlank;

public record RoleAssignmentRequest(@NotBlank String role) {
}

package com.avas.platform.auth;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record RoleUpdateRequest(Boolean active, Set<@NotBlank String> permissions) {
}

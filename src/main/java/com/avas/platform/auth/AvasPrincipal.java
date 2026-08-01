package com.avas.platform.auth;

import java.util.Set;
import java.util.UUID;

public record AvasPrincipal(UUID userId, String tenantId, String email, Set<String> roles) {
    public boolean hasRole(String role) { return roles.contains(role); }
}

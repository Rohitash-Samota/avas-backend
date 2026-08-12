package com.avas.platform.auth;

import java.util.List;

public record RoleResponse(String code, String displayName, boolean active, List<String> permissions) {
    static RoleResponse from(RoleEntity role) {
        return new RoleResponse(role.getCode(), role.getDisplayName(), role.isActive(),
                role.getPermissions().stream().sorted().toList());
    }
}

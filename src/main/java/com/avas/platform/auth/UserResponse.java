package com.avas.platform.auth;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String tenantId,
        String firstName,
        String lastName,
        String username,
        String email,
        String mobileNumber,
        String authProvider,
        List<String> roles,
        List<RoleResponse> roleAccess
) {
    static UserResponse from(UserEntity user) {
        return new UserResponse(user.getId(), user.getTenantId(), user.getFirstName(), user.getLastName(),
                user.getUsername(), user.getEmail(), user.getMobileNumber(), user.getProvider().name(),
                user.getRoles().stream().map(RoleEntity::getCode).sorted().toList(),
                user.getRoles().stream().map(RoleResponse::from)
                        .sorted(Comparator.comparing(RoleResponse::code)).toList());
    }
}

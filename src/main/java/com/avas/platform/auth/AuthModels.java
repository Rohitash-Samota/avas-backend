package com.avas.platform.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AuthModels {
    private AuthModels() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @Pattern(regexp = "^$|^[a-zA-Z0-9][a-zA-Z0-9._-]{2,59}$", message = "username must contain 3-60 letters, numbers, dots, underscores or hyphens") String username,
            @Email @Size(max = 190) String email,
            @Pattern(regexp = "^$|^[+0-9][0-9 -]{7,20}$") String mobileNumber,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Pattern(regexp = "^(INDIVIDUAL|BUILDER)$", message = "accountType must be INDIVIDUAL or BUILDER")
            String accountType
    ) {}

    public record LoginRequest(String identifier, String email, String mobileNumber,
            @NotBlank String password) {
        @AssertTrue(message = "Provide an email, mobile number, username or full name")
        public boolean isIdentifierPresent() {
            return notBlank(identifier) || notBlank(email) || notBlank(mobileNumber);
        }

        String resolvedIdentifier() {
            if (notBlank(identifier)) return identifier.trim();
            if (notBlank(email)) return email.trim();
            return mobileNumber == null ? "" : mobileNumber.trim();
        }

        private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
    }
    public record MobileLoginRequest(@NotBlank String mobileNumber, @NotBlank String password) {}
    public record RoleAssignmentRequest(@NotBlank String role) {}
    public record RoleUpdateRequest(Boolean active, Set<@NotBlank String> permissions) {}
    public record AdminCreateUserRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @Pattern(regexp = "^$|^[a-zA-Z0-9][a-zA-Z0-9._-]{2,59}$", message = "username must contain 3-60 letters, numbers, dots, underscores or hyphens") String username,
            @Email @Size(max = 190) String email,
            @Pattern(regexp = "^$|^[+0-9][0-9 -]{7,20}$") String mobileNumber,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Pattern(regexp = "^(INDIVIDUAL|BUILDER|INTERNAL_USER|SITE_ENGINEER|ADMIN)$") String role,
            @Size(max = 60) String tenantId
    ) {}
    public record RoleResponse(String code, String displayName, boolean active, List<String> permissions) {
        static RoleResponse from(RoleEntity role) {
            return new RoleResponse(role.getCode(), role.getDisplayName(), role.isActive(),
                    role.getPermissions().stream().sorted().toList());
        }
    }

    public record UserResponse(UUID id, String tenantId, String firstName, String lastName, String username, String email,
            String mobileNumber, String authProvider, List<String> roles, List<RoleResponse> roleAccess) {
        static UserResponse from(UserEntity user) {
            return new UserResponse(user.getId(), user.getTenantId(), user.getFirstName(), user.getLastName(),
                    user.getUsername(), user.getEmail(), user.getMobileNumber(), user.getProvider().name(),
                    user.getRoles().stream().map(RoleEntity::getCode).sorted().toList(),
                    user.getRoles().stream().map(RoleResponse::from).sorted(java.util.Comparator.comparing(RoleResponse::code)).toList());
        }
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {}
    public record ProviderStatus(boolean local, boolean google, String googleAuthorizationUrl) {}
    record IssuedTokens(AuthResponse response, String refreshToken) {}
}

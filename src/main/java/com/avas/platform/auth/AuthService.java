package com.avas.platform.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final IdentityUserStore users;
    private final IdentityRoleStore roles;
    private final IdentityRefreshTokenStore refreshTokens;
    private final IdentityMigration identityMigration;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final Duration refreshLifetime;

    AuthService(IdentityUserStore users, IdentityRoleStore roles, IdentityRefreshTokenStore refreshTokens,
            IdentityMigration identityMigration, PasswordEncoder passwords, JwtService jwt,
            @Value("${avas.auth.refresh-token-days:30}") long refreshDays) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.identityMigration = identityMigration;
        this.passwords = passwords;
        this.jwt = jwt;
        this.refreshLifetime = Duration.ofDays(refreshDays);
    }

    @PostConstruct
    void migrateIdentityStore() {
        identityMigration.migrate();
    }

    public IssuedTokens register(RegisterRequest request) {
        var email = UserEntity.normalizeEmail(request.email());
        var mobile = UserEntity.normalizeMobile(request.mobileNumber());
        requireContact(email, mobile);
        var username = availableUsername(request.username(), request.firstName(), request.lastName());
        ensureIdentifiersAvailable(username, email, mobile);
        var accountType = "BUILDER".equalsIgnoreCase(request.accountType()) ? "BUILDER" : "INDIVIDUAL";
        var user = new UserEntity("tenant-public", request.firstName().trim(), request.lastName().trim(),
                username, email, mobile, passwords.encode(request.password()),
                UserEntity.AuthProvider.LOCAL, requiredRole(accountType));
        addBuilderCustomerAccess(user, accountType);
        user = users.save(user);
        return issue(user);
    }

    public UserResponse createUser(AdminCreateUserRequest request) {
        var email = UserEntity.normalizeEmail(request.email());
        var mobile = UserEntity.normalizeMobile(request.mobileNumber());
        requireContact(email, mobile);
        var username = availableUsername(request.username(), request.firstName(), request.lastName());
        ensureIdentifiersAvailable(username, email, mobile);
        var tenantId = request.tenantId() == null || request.tenantId().isBlank()
                ? "tenant-public" : request.tenantId().trim();
        var roleCode = request.role().toUpperCase();
        var user = new UserEntity(tenantId, request.firstName().trim(), request.lastName().trim(),
                username, email, mobile, passwords.encode(request.password()),
                UserEntity.AuthProvider.LOCAL, requiredRole(roleCode));
        addBuilderCustomerAccess(user, roleCode);
        user = users.save(user);
        return UserResponse.from(user);
    }

    public IssuedTokens login(LoginRequest request) {
        var user = findByIdentifier(request.resolvedIdentifier()).orElseThrow(this::invalidCredentials);
        validatePassword(user, request.password());
        return issue(user);
    }

    public IssuedTokens loginMobile(MobileLoginRequest request) {
        return login(new LoginRequest(null, null, request.mobileNumber(), request.password()));
    }

    public IssuedTokens rotate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) throw invalidRefresh();
        var current = refreshTokens.findByTokenHash(hash(rawRefreshToken)).orElseThrow(this::invalidRefresh);
        if (!current.active()) throw invalidRefresh();
        current.revoke();
        refreshTokens.save(current);
        return issue(requiredUser(current.getUserId()));
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        refreshTokens.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> { token.revoke(); refreshTokens.save(token); });
    }

    public UserResponse user(UUID id) { return UserResponse.from(requiredUser(id)); }

    public List<UserResponse> users() { return users.findAll().stream().map(UserResponse::from).toList(); }

    public List<RoleResponse> roleCatalog() {
        return roles.findAll().stream().map(RoleResponse::from)
                .sorted(java.util.Comparator.comparing(RoleResponse::code)).toList();
    }

    public Set<String> permissionCatalog() { return IdentityDefaults.PERMISSION_CATALOG; }

    public Optional<RoleResponse> assignedActiveRole(UUID userId, String roleCode) {
        return users.findById(userId).filter(UserEntity::isEnabled).stream()
                .flatMap(user -> user.getRoles().stream())
                .filter(RoleEntity::isActive)
                .filter(role -> role.getCode().equalsIgnoreCase(roleCode))
                .findFirst().map(RoleResponse::from);
    }

    public Optional<RoleResponse> firstAssignedActiveRole(UUID userId) {
        return users.findById(userId).filter(UserEntity::isEnabled).stream()
                .flatMap(user -> user.getRoles().stream())
                .filter(RoleEntity::isActive)
                .sorted(java.util.Comparator.comparing(RoleEntity::getCode))
                .findFirst().map(RoleResponse::from);
    }

    public UserResponse assignRole(UUID userId, String roleCode) {
        var user = requiredUser(userId);
        var role = requiredRole(roleCode.toUpperCase());
        if (!role.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "This role is disabled");
        user.addRole(role);
        addBuilderCustomerAccess(user, role.getCode());
        return UserResponse.from(users.save(user));
    }

    public UserResponse removeRole(UUID userId, String roleCode) {
        var user = requiredUser(userId);
        var role = requiredRole(roleCode.toUpperCase());
        if (!user.getRoles().contains(role)) return UserResponse.from(user);
        if ("INDIVIDUAL".equals(role.getCode()) && user.getRoles().stream().anyMatch(value -> "BUILDER".equals(value.getCode()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Builder profiles must retain Individual customer access");
        }
        if (user.getRoles().size() == 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account must retain at least one role");
        }
        user.removeRole(role);
        return UserResponse.from(users.save(user));
    }

    public RoleResponse configureRole(String roleCode, RoleUpdateRequest request) {
        var role = requiredRole(roleCode.toUpperCase());
        var permissions = request.permissions() == null ? role.getPermissions() : request.permissions().stream()
                .map(value -> value.trim().toUpperCase()).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!IdentityDefaults.PERMISSION_CATALOG.containsAll(permissions)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role contains an unknown permission");
        }
        var active = request.active() == null ? role.isActive() : request.active();
        if ("INDIVIDUAL".equals(role.getCode())
                && (!active || !permissions.containsAll(IdentityDefaults.REQUIRED_INDIVIDUAL_PERMISSIONS))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Individual customer must remain active with all required customer journey permissions");
        }
        if ("ADMIN".equals(role.getCode()) && (!active || !permissions.containsAll(Set.of("USER_MANAGE", "ROLE_MANAGE")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Administrator must remain active with USER_MANAGE and ROLE_MANAGE permissions");
        }
        role.configure(active, permissions);
        return RoleResponse.from(roles.save(role));
    }

    public IssuedTokens oauthUser(String email, String givenName, String familyName) {
        var user = users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(new UserEntity("tenant-public",
                givenName == null || givenName.isBlank() ? "AVAS" : givenName,
                familyName == null ? "User" : familyName,
                availableUsername(null, givenName == null ? "AVAS" : givenName, familyName == null ? "User" : familyName),
                email, null, null,
                UserEntity.AuthProvider.GOOGLE, requiredRole("INDIVIDUAL"))));
        return issue(user);
    }

    private void addBuilderCustomerAccess(UserEntity user, String roleCode) {
        if ("BUILDER".equals(roleCode)) user.addRole(requiredRole("INDIVIDUAL"));
    }

    private Optional<UserEntity> findByIdentifier(String rawIdentifier) {
        var identifier = rawIdentifier.trim();
        Optional<UserEntity> result = Optional.empty();
        if (identifier.contains("@")) result = users.findByEmailIgnoreCase(identifier);
        if (result.isEmpty()) result = users.findByUsernameIgnoreCase(UserEntity.normalizeUsername(identifier));
        var mobile = UserEntity.normalizeMobile(identifier);
        if (result.isEmpty() && mobile != null && mobile.length() >= 8) result = users.findByMobileNumber(mobile);
        if (result.isEmpty()) {
            var matches = users.findByFullNameIgnoreCase(identifier.replaceAll("\\s+", " "));
            if (matches.size() == 1) result = Optional.of(matches.get(0));
        }
        return result;
    }

    private String availableUsername(String requested, String firstName, String lastName) {
        if (requested != null && !requested.isBlank()) return UserEntity.normalizeUsername(requested);
        return uniqueUsername(slug(firstName + "." + lastName));
    }

    private String uniqueUsername(String rawBase) {
        var base = rawBase.length() > 50 ? rawBase.substring(0, 50) : rawBase;
        var candidate = base;
        var suffix = 2;
        while (users.existsByUsernameIgnoreCase(candidate)) candidate = base + "." + suffix++;
        return candidate;
    }

    private String slug(String value) {
        var slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", ".")
                .replaceAll("^[._-]+|[._-]+$", "");
        if (slug.length() < 3) slug = "user." + slug;
        return slug;
    }

    private void requireContact(String email, String mobile) {
        if (email == null && mobile == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide either an email address or mobile number");
        }
        if (mobile != null && (mobile.length() < 8 || mobile.length() > 15)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobile number must contain 8 to 15 digits");
        }
    }

    private void ensureIdentifiersAvailable(String username, String email, String mobile) {
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This username is already in use");
        }
        if (email != null && users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this email");
        }
        if (mobile != null && users.existsByMobileNumber(mobile)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this mobile number");
        }
    }

    private IssuedTokens issue(UserEntity user) {
        var rawRefresh = randomToken();
        refreshTokens.save(new RefreshTokenEntity(user.getId(), hash(rawRefresh), Instant.now().plus(refreshLifetime)));
        var response = new AuthResponse(jwt.issue(user), "Bearer", jwt.expiresInSeconds(), UserResponse.from(user));
        return new IssuedTokens(response, rawRefresh);
    }

    private UserEntity requiredUser(UUID id) { return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")); }
    private RoleEntity requiredRole(String code) { return roles.findByCode(code).orElseThrow(() -> new IllegalStateException("Role is not configured: " + code)); }
    private void validatePassword(UserEntity user, String password) {
        if (!user.isEnabled() || user.getPasswordHash() == null || !passwords.matches(password, user.getPasswordHash())) throw invalidCredentials();
    }
    private ResponseStatusException invalidCredentials() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"); }
    private ResponseStatusException invalidRefresh() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired"); }
    private String randomToken() { var bytes = new byte[48]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}

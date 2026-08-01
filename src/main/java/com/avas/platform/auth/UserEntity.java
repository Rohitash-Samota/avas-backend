package com.avas.platform.auth;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_tenant", columnList = "tenantId"),
        @Index(name = "idx_users_mobile", columnList = "mobileNumber")
})
public class UserEntity {
    public enum AuthProvider {
        LOCAL, GOOGLE
    }

    @Id
    private UUID id;

    @Column(nullable = false, length = 60)
    private String tenantId;

    @Column(nullable = false, length = 80)
    private String firstName;

    @Column(nullable = false, length = 80)
    private String lastName;

    @Column(unique = true, length = 60)
    private String username;

    @Column(unique = true, length = 190)
    private String email;

    @Column(unique = true, length = 30)
    private String mobileNumber;

    @Column(length = 120)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, length = 30)
    private String provisioningSource = "APPLICATION";

    @Column(length = 80)
    private String migrationVersion;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleEntity> roles = new LinkedHashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
    }

    public UserEntity(String tenantId, String firstName, String lastName, String username, String email,
            String mobileNumber,
            String passwordHash, AuthProvider provider, RoleEntity initialRole) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = normalizeUsername(username);
        this.email = normalizeEmail(email);
        this.mobileNumber = normalizeMobile(mobileNumber);
        this.passwordHash = passwordHash;
        this.provider = provider;
        this.enabled = true;
        this.roles.add(initialRole);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    static UserEntity rehydrate(UUID id, String tenantId, String firstName, String lastName, String username,
            String email, String mobileNumber, String passwordHash, AuthProvider provider, boolean enabled,
            Set<RoleEntity> roles, Instant createdAt, Instant updatedAt, String provisioningSource,
            String migrationVersion) {
        var user = new UserEntity();
        user.id = id;
        user.tenantId = tenantId;
        user.firstName = firstName;
        user.lastName = lastName;
        user.username = username;
        user.email = email;
        user.mobileNumber = mobileNumber;
        user.passwordHash = passwordHash;
        user.provider = provider;
        user.enabled = enabled;
        user.roles = new LinkedHashSet<>(roles);
        user.createdAt = createdAt;
        user.updatedAt = updatedAt;
        user.provisioningSource = provisioningSource == null ? "APPLICATION" : provisioningSource;
        user.migrationVersion = migrationVersion;
        return user;
    }

    void markProvisionedByMigration(String version) {
        this.provisioningSource = "MIGRATION";
        this.migrationVersion = version;
        this.updatedAt = Instant.now();
    }

    public void addRole(RoleEntity role) {
        roles.add(role);
        updatedAt = Instant.now();
    }

    public void removeRole(RoleEntity role) {
        roles.remove(role);
        updatedAt = Instant.now();
    }

    public void resetLocalPassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.provider = AuthProvider.LOCAL;
        this.enabled = true;
        this.updatedAt = Instant.now();
    }

    public void updateLoginIdentity(String username, String mobileNumber) {
        this.username = normalizeUsername(username);
        this.mobileNumber = normalizeMobile(mobileNumber);
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getProvisioningSource() {
        return provisioningSource;
    }

    public String getMigrationVersion() {
        return migrationVersion;
    }

    public Set<RoleEntity> getRoles() {
        return Set.copyOf(roles);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    static String normalizeMobile(String value) {
        if (value == null || value.isBlank())
            return null;
        var normalized = value.replaceAll("[^0-9]", "");
        return normalized.isBlank() ? null : normalized;
    }

    static String normalizeEmail(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeUsername(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}

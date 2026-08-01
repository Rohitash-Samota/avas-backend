package com.avas.platform.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "avas.identity.store", havingValue = "sql", matchIfMissing = true)
class SqlIdentityMigration implements IdentityMigration {
    private static final String ROLE_MIGRATION = "V001__role_catalog";
    private static final String RETIRE_DEMO_MIGRATION = "V004__retire_demo_identities";
    private static final String BOOTSTRAP_ADMIN_MIGRATION = "V005__bootstrap_production_administrator";

    private final IdentityUserStore users;
    private final IdentityRoleStore roles;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    private final boolean bootstrapEnabled;
    private final String bootstrapFirstName;
    private final String bootstrapLastName;
    private final String bootstrapUsername;
    private final String bootstrapEmail;
    private final String bootstrapMobile;
    private final String bootstrapPassword;
    private final String bootstrapTenantId;

    SqlIdentityMigration(IdentityUserStore users, IdentityRoleStore roles, PasswordEncoder passwords,
            JdbcTemplate jdbc,
            @Value("${avas.bootstrap-admin.enabled:false}") boolean bootstrapEnabled,
            @Value("${avas.bootstrap-admin.first-name:Platform}") String bootstrapFirstName,
            @Value("${avas.bootstrap-admin.last-name:Administrator}") String bootstrapLastName,
            @Value("${avas.bootstrap-admin.username:platform.admin}") String bootstrapUsername,
            @Value("${avas.bootstrap-admin.email:}") String bootstrapEmail,
            @Value("${avas.bootstrap-admin.mobile-number:}") String bootstrapMobile,
            @Value("${avas.bootstrap-admin.password:}") String bootstrapPassword,
            @Value("${avas.bootstrap-admin.tenant-id:tenant-production}") String bootstrapTenantId) {
        this.users = users;
        this.roles = roles;
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.bootstrapEnabled = bootstrapEnabled;
        this.bootstrapFirstName = bootstrapFirstName;
        this.bootstrapLastName = bootstrapLastName;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapMobile = bootstrapMobile;
        this.bootstrapPassword = bootstrapPassword;
        this.bootstrapTenantId = bootstrapTenantId;
    }

    @Override
    @Transactional
    public void migrate() {
        ensureLedger();
        apply(ROLE_MIGRATION, "Create the AVAS role and permission catalogue", this::migrateRoles);
        migrateRoles();
        apply(RETIRE_DEMO_MIGRATION, "Remove legacy demonstration identities", this::retireLegacyDemoIdentities);
        if (bootstrapEnabled) {
            validateBootstrap();
            apply(BOOTSTRAP_ADMIN_MIGRATION, "Create the initial production administrator", this::provisionBootstrapAdministrator);
        }
    }

    private void migrateRoles() {
        IdentityDefaults.ROLE_DEFINITIONS.forEach((code, permissions) -> {
            var role = roles.findByCode(code).orElseGet(() -> new RoleEntity(code,
                    IdentityDefaults.displayName(code), permissions, IdentityDefaults.ROLE_DEFINITION_VERSION));
            role.upgradeDefinition(IdentityDefaults.displayName(code), permissions,
                    IdentityDefaults.ROLE_DEFINITION_VERSION);
            roles.save(role);
        });
    }

    private void retireLegacyDemoIdentities() {
        var selection = "SELECT id FROM users WHERE tenant_id = 'tenant-demo' AND provisioning_source = 'MIGRATION' " +
                "AND migration_version IN ('V002__first_administrator', 'V003__demo_role_identities')";
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (" + selection + ")");
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (" + selection + ")");
        jdbc.update("DELETE FROM users WHERE tenant_id = 'tenant-demo' AND provisioning_source = 'MIGRATION' " +
                "AND migration_version IN ('V002__first_administrator', 'V003__demo_role_identities')");
    }

    private void provisionBootstrapAdministrator() {
        var role = roles.findByCode("ADMIN").orElseThrow();
        var email = UserEntity.normalizeEmail(bootstrapEmail);
        var mobile = UserEntity.normalizeMobile(bootstrapMobile);
        var username = UserEntity.normalizeUsername(bootstrapUsername);
        if (users.existsByUsernameIgnoreCase(username)
                || (email != null && users.existsByEmailIgnoreCase(email))
                || (mobile != null && users.existsByMobileNumber(mobile))) {
            throw new IllegalStateException("Bootstrap administrator identity already exists; disable bootstrap and sign in with the existing account");
        }
        var encodedPassword = passwords.encode(bootstrapPassword);
        var user = new UserEntity(bootstrapTenantId.trim(), bootstrapFirstName.trim(), bootstrapLastName.trim(),
                username, email, mobile, encodedPassword, UserEntity.AuthProvider.LOCAL, role);
        user.markProvisionedByMigration(BOOTSTRAP_ADMIN_MIGRATION);
        users.save(user);
    }

    private void validateBootstrap() {
        if (bootstrapFirstName.isBlank() || bootstrapLastName.isBlank() || bootstrapUsername.isBlank() || bootstrapTenantId.isBlank()) {
            throw new IllegalStateException("Production administrator bootstrap requires name, username and tenant ID");
        }
        if (UserEntity.normalizeEmail(bootstrapEmail) == null && UserEntity.normalizeMobile(bootstrapMobile) == null) {
            throw new IllegalStateException("Production administrator bootstrap requires an email address or mobile number");
        }
        if (bootstrapPassword.length() < 12 || bootstrapPassword.length() > 72
                || !bootstrapPassword.matches(".*[A-Z].*") || !bootstrapPassword.matches(".*[a-z].*")
                || !bootstrapPassword.matches(".*[0-9].*") || !bootstrapPassword.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalStateException("AVAS_BOOTSTRAP_ADMIN_PASSWORD must contain 12-72 characters with upper, lower, number and symbol");
        }
    }

    private void ensureLedger() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS identity_schema_migrations (
                    version VARCHAR(80) PRIMARY KEY,
                    description VARCHAR(255) NOT NULL,
                    identity_store VARCHAR(20) NOT NULL,
                    executed_at TIMESTAMP NOT NULL
                )
                """);
    }

    private void apply(String version, String description, Runnable migration) {
        var applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM identity_schema_migrations WHERE version = ?", Integer.class, version);
        if (applied != null && applied > 0) return;
        migration.run();
        jdbc.update("INSERT INTO identity_schema_migrations (version, description, identity_store, executed_at) VALUES (?, ?, ?, ?)",
                version, description, "MYSQL", Timestamp.from(Instant.now()));
    }
}

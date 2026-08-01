package com.avas.platform.auth;

import java.time.Instant;
import java.util.Date;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.mongodb.client.MongoCollection;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.in;
import com.mongodb.client.model.IndexOptions;
import static com.mongodb.client.model.Indexes.ascending;

@Component
@ConditionalOnProperty(name = "avas.identity.store", havingValue = "mongo")
class MongoIdentityMigration implements IdentityMigration {
    private static final String MIGRATIONS = "identity_schema_migrations";
    private static final String ROLE_MIGRATION = "V001__identity_role_catalog";
    private static final String RETIRE_DEMO_MIGRATION = "V004__retire_demo_identities";
    private static final String BOOTSTRAP_ADMIN_MIGRATION = "V005__bootstrap_production_administrator";

    private final MongoTemplate mongo;
    private final IdentityUserStore users;
    private final IdentityRoleStore roles;
    private final PasswordEncoder passwords;
    private final boolean bootstrapEnabled;
    private final String bootstrapFirstName;
    private final String bootstrapLastName;
    private final String bootstrapUsername;
    private final String bootstrapEmail;
    private final String bootstrapMobile;
    private final String bootstrapPassword;
    private final String bootstrapTenantId;

    MongoIdentityMigration(MongoTemplate mongo, IdentityUserStore users, IdentityRoleStore roles,
            PasswordEncoder passwords,
            @Value("${avas.bootstrap-admin.enabled:false}") boolean bootstrapEnabled,
            @Value("${avas.bootstrap-admin.first-name:Platform}") String bootstrapFirstName,
            @Value("${avas.bootstrap-admin.last-name:Administrator}") String bootstrapLastName,
            @Value("${avas.bootstrap-admin.username:platform.admin}") String bootstrapUsername,
            @Value("${avas.bootstrap-admin.email:}") String bootstrapEmail,
            @Value("${avas.bootstrap-admin.mobile-number:}") String bootstrapMobile,
            @Value("${avas.bootstrap-admin.password:}") String bootstrapPassword,
            @Value("${avas.bootstrap-admin.tenant-id:tenant-production}") String bootstrapTenantId) {
        this.mongo = mongo;
        this.users = users;
        this.roles = roles;
        this.passwords = passwords;
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
    public void migrate() {
        createCollectionsAndIndexes();
        apply(ROLE_MIGRATION, "Create the complete AVAS role and permission catalog", this::migrateRoles);
        migrateRoles();
        apply(RETIRE_DEMO_MIGRATION, "Remove legacy demonstration identities", this::retireLegacyDemoIdentities);
        if (bootstrapEnabled) {
            validateBootstrap();
            apply(BOOTSTRAP_ADMIN_MIGRATION, "Create the initial production administrator", this::provisionBootstrapAdministrator);
        }
    }

    private void createCollectionsAndIndexes() {
        MongoCollection<Document> roleCollection = mongo.getCollection(MongoIdentityRoleStore.COLLECTION);
        MongoCollection<Document> userCollection = mongo.getCollection(MongoIdentityUserStore.COLLECTION);
        MongoCollection<Document> tokenCollection = mongo.getCollection(MongoIdentityRefreshTokenStore.COLLECTION);
        roleCollection.createIndex(ascending("code"), new IndexOptions().unique(true).name("uk_roles_code"));
        userCollection.createIndex(ascending("username"), new IndexOptions().unique(true).name("uk_users_username"));
        userCollection.createIndex(ascending("email"), new IndexOptions().unique(true).name("uk_users_email")
                .partialFilterExpression(new Document("email", new Document("$type", "string"))));
        userCollection.createIndex(ascending("mobileNumber"), new IndexOptions().unique(true).name("uk_users_mobile")
                .partialFilterExpression(new Document("mobileNumber", new Document("$type", "string"))));
        tokenCollection.createIndex(ascending("tokenHash"),
                new IndexOptions().unique(true).name("uk_refresh_token_hash"));
        tokenCollection.createIndex(ascending("expiresAt"), new IndexOptions().name("idx_refresh_token_expiry"));
    }

    private void migrateRoles() {
        IdentityDefaults.ROLE_DEFINITIONS.forEach((code, permissions) -> {
            var role = roles.findByCode(code)
                    .orElseGet(() -> new RoleEntity(code, IdentityDefaults.displayName(code), permissions,
                            IdentityDefaults.ROLE_DEFINITION_VERSION));
            role.upgradeDefinition(IdentityDefaults.displayName(code), permissions,
                    IdentityDefaults.ROLE_DEFINITION_VERSION);
            roles.save(role);
        });
    }

    private void retireLegacyDemoIdentities() {
        var userCollection = mongo.getCollection(MongoIdentityUserStore.COLLECTION);
        var filter = and(eq("tenantId", "tenant-demo"), eq("provisioningSource", "MIGRATION"),
                in("migrationVersion", "V002__first_administrator", "V003__demo_role_identities"));
        var ids = new java.util.ArrayList<String>();
        userCollection.find(filter).projection(new Document("_id", 1)).forEach(document -> ids.add(document.getString("_id")));
        if (!ids.isEmpty()) mongo.getCollection(MongoIdentityRefreshTokenStore.COLLECTION).deleteMany(in("userId", ids));
        userCollection.deleteMany(filter);
    }

    private void provisionBootstrapAdministrator() {
        var role = requiredRole("ADMIN");
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

    private RoleEntity requiredRole(String code) {
        return roles.findByCode(code).orElseThrow(() -> new IllegalStateException("Role migration is missing " + code));
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

    private void apply(String version, String description, Runnable migration) {
        MongoCollection<Document> migrations = mongo.getCollection(MIGRATIONS);
        if (migrations.countDocuments(eq("_id", version)) > 0)
            return;
        migration.run();
        migrations.insertOne(new Document("_id", version)
                .append("version", version.substring(1, 4))
                .append("description", description)
                .append("identityStore", "MONGO")
                .append("database", mongo.getDb().getName())
                .append("executedAt", Date.from(Instant.now())));
    }
}

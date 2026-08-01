package com.avas.platform.auth;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;

@Component
@ConditionalOnProperty(name = "avas.identity.store", havingValue = "mongo")
class MongoIdentityUserStore implements IdentityUserStore {
    static final String COLLECTION = "users";
    private final MongoCollection<Document> users;
    private final MongoCollection<Document> roles;

    MongoIdentityUserStore(MongoTemplate mongo) {
        this.users = mongo.getCollection(COLLECTION);
        this.roles = mongo.getCollection(MongoIdentityRoleStore.COLLECTION);
    }

    public UserEntity save(UserEntity user) {
        users.replaceOne(eq("_id", user.getId().toString()), MongoIdentityDocuments.fromUser(user),
                new ReplaceOptions().upsert(true));
        return user;
    }

    public Optional<UserEntity> findById(UUID id) { return one(eq("_id", id.toString())); }
    public List<UserEntity> findAll() {
        return StreamSupport.stream(users.find().sort(Sorts.ascending("username")).spliterator(), false)
                .map(this::toUser).toList();
    }
    public Optional<UserEntity> findByEmailIgnoreCase(String email) { return one(ci("email", email)); }
    public Optional<UserEntity> findByUsernameIgnoreCase(String username) { return one(ci("username", username)); }
    public Optional<UserEntity> findByMobileNumber(String mobileNumber) { return one(eq("mobileNumber", mobileNumber)); }
    public List<UserEntity> findByFullNameIgnoreCase(String fullName) {
        return findAll().stream().filter(user -> (user.getFirstName() + " " + user.getLastName()).equalsIgnoreCase(fullName)).toList();
    }
    public boolean existsByEmailIgnoreCase(String email) { return users.countDocuments(ci("email", email)) > 0; }
    public boolean existsByUsernameIgnoreCase(String username) { return users.countDocuments(ci("username", username)) > 0; }
    public boolean existsByMobileNumber(String mobileNumber) { return users.countDocuments(eq("mobileNumber", mobileNumber)) > 0; }

    private Optional<UserEntity> one(org.bson.conversions.Bson filter) {
        return Optional.ofNullable(users.find(filter).first()).map(this::toUser);
    }

    private UserEntity toUser(Document document) {
        Function<String, RoleEntity> roleLoader = code -> Optional.ofNullable(roles.find(eq("_id", code)).first())
                .map(MongoIdentityDocuments::toRole)
                .orElseThrow(() -> new IllegalStateException("Mongo identity role is missing: " + code));
        return MongoIdentityDocuments.toUser(document, roleLoader);
    }

    private static org.bson.conversions.Bson ci(String field, String value) {
        return regex(field, Pattern.compile("^" + Pattern.quote(value) + "$", Pattern.CASE_INSENSITIVE));
    }
}

@Component
@ConditionalOnProperty(name = "avas.identity.store", havingValue = "mongo")
class MongoIdentityRoleStore implements IdentityRoleStore {
    static final String COLLECTION = "roles";
    private final MongoCollection<Document> roles;

    MongoIdentityRoleStore(MongoTemplate mongo) { this.roles = mongo.getCollection(COLLECTION); }
    public RoleEntity save(RoleEntity role) {
        roles.replaceOne(eq("_id", role.getCode()), MongoIdentityDocuments.fromRole(role),
                new ReplaceOptions().upsert(true));
        return role;
    }
    public Optional<RoleEntity> findByCode(String code) {
        return Optional.ofNullable(roles.find(eq("_id", code.toUpperCase())).first()).map(MongoIdentityDocuments::toRole);
    }
    public List<RoleEntity> findAll() {
        return StreamSupport.stream(roles.find().sort(Sorts.ascending("code")).spliterator(), false)
                .map(MongoIdentityDocuments::toRole).toList();
    }
}

@Component
@ConditionalOnProperty(name = "avas.identity.store", havingValue = "mongo")
class MongoIdentityRefreshTokenStore implements IdentityRefreshTokenStore {
    static final String COLLECTION = "refresh_tokens";
    private final MongoCollection<Document> tokens;

    MongoIdentityRefreshTokenStore(MongoTemplate mongo) { this.tokens = mongo.getCollection(COLLECTION); }
    public RefreshTokenEntity save(RefreshTokenEntity token) {
        tokens.replaceOne(eq("_id", token.getId().toString()), MongoIdentityDocuments.fromToken(token),
                new ReplaceOptions().upsert(true));
        return token;
    }
    public Optional<RefreshTokenEntity> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(tokens.find(eq("tokenHash", tokenHash)).first()).map(MongoIdentityDocuments::toToken);
    }
}

final class MongoIdentityDocuments {
    private MongoIdentityDocuments() {}

    static Document fromRole(RoleEntity role) {
        return new Document("_id", role.getCode())
                .append("code", role.getCode())
                .append("displayName", role.getDisplayName())
                .append("active", role.isActive())
                .append("definitionVersion", IdentityDefaults.ROLE_DEFINITION_VERSION)
                .append("permissions", role.getPermissions().stream().sorted().toList())
                .append("updatedAt", Date.from(Instant.now()));
    }

    static RoleEntity toRole(Document document) {
        var role = new RoleEntity(document.getString("code"), document.getString("displayName"),
                new LinkedHashSet<>(document.getList("permissions", String.class)),
                document.getInteger("definitionVersion", IdentityDefaults.ROLE_DEFINITION_VERSION));
        role.configure(Boolean.TRUE.equals(document.getBoolean("active")), role.getPermissions());
        return role;
    }

    static Document fromUser(UserEntity user) {
        var document = new Document("_id", user.getId().toString())
                .append("tenantId", user.getTenantId())
                .append("firstName", user.getFirstName())
                .append("lastName", user.getLastName())
                .append("username", user.getUsername())
                .append("provider", user.getProvider().name())
                .append("enabled", user.isEnabled())
                .append("passwordHash", user.getPasswordHash())
                .append("roles", user.getRoles().stream().map(RoleEntity::getCode).sorted().toList())
                .append("provisioningSource", user.getProvisioningSource())
                .append("migrationVersion", user.getMigrationVersion())
                .append("createdAt", Date.from(user.getCreatedAt()))
                .append("updatedAt", Date.from(user.getUpdatedAt()));
        if (user.getEmail() != null) document.append("email", user.getEmail());
        if (user.getMobileNumber() != null) document.append("mobileNumber", user.getMobileNumber());
        return document;
    }

    static UserEntity toUser(Document document, Function<String, RoleEntity> roleLoader) {
        var roleCodes = document.getList("roles", String.class);
        Set<RoleEntity> userRoles = (roleCodes == null ? List.<String>of() : roleCodes).stream()
                .map(roleLoader).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return UserEntity.rehydrate(UUID.fromString(document.getString("_id")), document.getString("tenantId"),
                document.getString("firstName"), document.getString("lastName"), document.getString("username"),
                document.getString("email"), document.getString("mobileNumber"), document.getString("passwordHash"),
                UserEntity.AuthProvider.valueOf(document.getString("provider")), Boolean.TRUE.equals(document.getBoolean("enabled")),
                userRoles, instant(document, "createdAt"), instant(document, "updatedAt"),
                document.getString("provisioningSource"), document.getString("migrationVersion"));
    }

    static Document fromToken(RefreshTokenEntity token) {
        var document = new Document("_id", token.getId().toString())
                .append("userId", token.getUserId().toString())
                .append("tokenHash", token.getTokenHash())
                .append("expiresAt", Date.from(token.getExpiresAt()))
                .append("createdAt", Date.from(token.getCreatedAt()));
        if (token.getRevokedAt() != null) document.append("revokedAt", Date.from(token.getRevokedAt()));
        return document;
    }

    static RefreshTokenEntity toToken(Document document) {
        return RefreshTokenEntity.rehydrate(UUID.fromString(document.getString("_id")),
                UUID.fromString(document.getString("userId")), document.getString("tokenHash"),
                instant(document, "expiresAt"), nullableInstant(document, "revokedAt"), instant(document, "createdAt"));
    }

    private static Instant instant(Document document, String field) { return document.getDate(field).toInstant(); }
    private static Instant nullableInstant(Document document, String field) {
        var value = document.getDate(field);
        return value == null ? null : value.toInstant();
    }
}

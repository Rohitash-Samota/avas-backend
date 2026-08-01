package com.avas.platform.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface IdentityUserStore {
    UserEntity save(UserEntity user);
    Optional<UserEntity> findById(UUID id);
    List<UserEntity> findAll();
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    Optional<UserEntity> findByUsernameIgnoreCase(String username);
    Optional<UserEntity> findByMobileNumber(String mobileNumber);
    List<UserEntity> findByFullNameIgnoreCase(String fullName);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByMobileNumber(String mobileNumber);
}

interface IdentityRoleStore {
    RoleEntity save(RoleEntity role);
    Optional<RoleEntity> findByCode(String code);
    List<RoleEntity> findAll();
}

interface IdentityRefreshTokenStore {
    RefreshTokenEntity save(RefreshTokenEntity token);
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
}

interface IdentityMigration {
    void migrate();
}

package com.avas.platform.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "avas.identity.store", havingValue = "sql", matchIfMissing = true)
class JpaIdentityUserStore implements IdentityUserStore {
    private final JpaUserRepository repository;

    JpaIdentityUserStore(JpaUserRepository repository) { this.repository = repository; }
    public UserEntity save(UserEntity user) { return repository.save(user); }
    public Optional<UserEntity> findById(UUID id) { return repository.findById(id); }
    public List<UserEntity> findAll() { return repository.findAll(); }
    public Optional<UserEntity> findByEmailIgnoreCase(String email) { return repository.findByEmailIgnoreCase(email); }
    public Optional<UserEntity> findByUsernameIgnoreCase(String username) { return repository.findByUsernameIgnoreCase(username); }
    public Optional<UserEntity> findByMobileNumber(String mobileNumber) { return repository.findByMobileNumber(mobileNumber); }
    public List<UserEntity> findByFullNameIgnoreCase(String fullName) { return repository.findByFullNameIgnoreCase(fullName); }
    public boolean existsByEmailIgnoreCase(String email) { return repository.existsByEmailIgnoreCase(email); }
    public boolean existsByUsernameIgnoreCase(String username) { return repository.existsByUsernameIgnoreCase(username); }
    public boolean existsByMobileNumber(String mobileNumber) { return repository.existsByMobileNumber(mobileNumber); }
}

@Component
@ConditionalOnProperty(name = "avas.identity.store", havingValue = "sql", matchIfMissing = true)
class JpaIdentityRoleStore implements IdentityRoleStore {
    private final JpaRoleRepository repository;

    JpaIdentityRoleStore(JpaRoleRepository repository) { this.repository = repository; }
    public RoleEntity save(RoleEntity role) { return repository.save(role); }
    public Optional<RoleEntity> findByCode(String code) { return repository.findByCode(code); }
    public List<RoleEntity> findAll() { return repository.findAll(); }
}

@Component
@ConditionalOnProperty(name = "avas.identity.store", havingValue = "sql", matchIfMissing = true)
class JpaIdentityRefreshTokenStore implements IdentityRefreshTokenStore {
    private final JpaRefreshTokenRepository repository;

    JpaIdentityRefreshTokenStore(JpaRefreshTokenRepository repository) { this.repository = repository; }
    public RefreshTokenEntity save(RefreshTokenEntity token) { return repository.save(token); }
    public Optional<RefreshTokenEntity> findByTokenHash(String tokenHash) { return repository.findByTokenHash(tokenHash); }
}

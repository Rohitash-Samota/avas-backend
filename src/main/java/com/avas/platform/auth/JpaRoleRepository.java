package com.avas.platform.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface JpaRoleRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByCode(String code);
}

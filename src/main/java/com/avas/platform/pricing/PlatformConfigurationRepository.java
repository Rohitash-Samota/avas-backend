package com.avas.platform.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface PlatformConfigurationRepository extends JpaRepository<PlatformConfigurationEntity, Long> {
    boolean existsByConfigurationKey(String configurationKey);

    Optional<PlatformConfigurationEntity> findByConfigurationKey(String configurationKey);
}

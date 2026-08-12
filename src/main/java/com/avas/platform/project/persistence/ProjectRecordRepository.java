package com.avas.platform.project.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ProjectRecordRepository extends JpaRepository<ProjectRecordEntity, Long> {
    Optional<ProjectRecordEntity> findByPublicId(String publicId);

    boolean existsByPublicId(String publicId);

    void deleteByPublicId(String publicId);

    List<ProjectRecordEntity> findAllByTenantIdAndOwnerUserIdOrderByUpdatedAtDesc(String tenantId, UUID ownerUserId);

    List<ProjectRecordEntity> findAllByTenantIdOrderByUpdatedAtDesc(String tenantId);
}

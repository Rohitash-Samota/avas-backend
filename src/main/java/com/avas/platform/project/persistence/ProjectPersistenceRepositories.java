package com.avas.platform.project.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface ProjectRecordRepository extends JpaRepository<ProjectRecordEntity, String> {
    List<ProjectRecordEntity> findAllByTenantIdAndOwnerUserIdOrderByUpdatedAtDesc(String tenantId, UUID ownerUserId);
    List<ProjectRecordEntity> findAllByTenantIdOrderByUpdatedAtDesc(String tenantId);
}
interface RequirementSnapshotRepository extends JpaRepository<RequirementSnapshotEntity, String> { void deleteAllByProjectId(String projectId); }
interface DrawingArtifactRepository extends JpaRepository<DrawingArtifactEntity, String> { void deleteAllByProjectId(String projectId); }
interface EstimateArtifactRepository extends JpaRepository<EstimateArtifactEntity, String> { void deleteAllByProjectId(String projectId); }
interface ProjectAuditRecordRepository extends JpaRepository<ProjectAuditRecordEntity, UUID> {
    boolean existsByEventId(String eventId);
    void deleteAllByProjectId(String projectId);
}
interface ProjectStateRepository extends JpaRepository<ProjectStateEntity, String> {}

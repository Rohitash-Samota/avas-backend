package com.avas.platform.project.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface RequirementSnapshotRepository extends JpaRepository<RequirementSnapshotEntity, Long> {
    Optional<RequirementSnapshotEntity> findBySnapshotId(String snapshotId);

    boolean existsBySnapshotId(String snapshotId);

    void deleteAllByProjectId(String projectId);
}

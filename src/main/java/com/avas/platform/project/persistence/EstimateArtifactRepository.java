package com.avas.platform.project.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface EstimateArtifactRepository extends JpaRepository<EstimateArtifactEntity, Long> {
    Optional<EstimateArtifactEntity> findByEstimateId(String estimateId);

    void deleteAllByProjectId(String projectId);
}

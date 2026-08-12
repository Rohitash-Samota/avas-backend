package com.avas.platform.project.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface DrawingArtifactRepository extends JpaRepository<DrawingArtifactEntity, Long> {
    Optional<DrawingArtifactEntity> findByDrawingId(String drawingId);

    void deleteAllByProjectId(String projectId);
}

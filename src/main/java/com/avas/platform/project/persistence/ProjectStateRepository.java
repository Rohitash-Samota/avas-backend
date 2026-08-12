package com.avas.platform.project.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ProjectStateRepository extends JpaRepository<ProjectStateEntity, Long> {
    Optional<ProjectStateEntity> findByProjectId(String projectId);

    boolean existsByProjectId(String projectId);

    void deleteByProjectId(String projectId);
}

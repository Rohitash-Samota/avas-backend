package com.avas.platform.project.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProjectAuditRecordRepository extends JpaRepository<ProjectAuditRecordEntity, Long> {
    boolean existsByEventId(String eventId);

    void deleteAllByProjectId(String projectId);
}

package com.avas.platform.project.persistence;

import com.avas.platform.project.ProjectModels.AuditEvent;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "project_audit_logs", indexes = @Index(name = "idx_project_audit_project", columnList = "projectId"))
class ProjectAuditRecordEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 80) private String eventId;
    @Column(nullable = false, length = 80) private String projectId;
    @Column(nullable = false, length = 80) private String action;
    @Column(nullable = false, length = 40) private String actorRole;
    @Column(nullable = false, length = 120) private String artifactId;
    @Column(nullable = false, length = 30) private String artifactVersion;
    @Column(nullable = false, length = 1000) private String detail;
    @Column(nullable = false, updatable = false) private Instant occurredAt;
    protected ProjectAuditRecordEntity() {}
    ProjectAuditRecordEntity(AuditEvent value) {
        id = UUID.randomUUID(); eventId = value.id(); projectId = value.projectId(); action = value.action(); actorRole = value.actorRole();
        artifactId = value.artifactId(); artifactVersion = value.version(); detail = value.detail(); occurredAt = value.at();
    }
}

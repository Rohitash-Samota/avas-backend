package com.avas.platform.project.persistence;

import com.avas.platform.project.ProjectModels.ProjectSummary;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects", indexes = {
        @Index(name = "idx_projects_tenant", columnList = "tenantId"),
        @Index(name = "idx_projects_owner", columnList = "tenantId,ownerUserId"),
        @Index(name = "idx_projects_code", columnList = "projectCode", unique = true)
})
class ProjectRecordEntity {
    @Id @Column(length = 80) private String id;
    @Column(nullable = false, length = 60) private String tenantId;
    @Column(columnDefinition = "BINARY(16)") private UUID ownerUserId;
    @Column(nullable = false, unique = true, length = 40) private String projectCode;
    @Column(nullable = false, length = 180) private String name;
    @Column(nullable = false, length = 30) private String startMode;
    @Column(nullable = false, length = 50) private String status;
    @Column(nullable = false) private int snapshotVersion;
    @Lob @Column(columnDefinition = "LONGTEXT") private String detailsJson;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected ProjectRecordEntity() {}
    ProjectRecordEntity(ProjectSummary project, String detailsJson, String tenantId, UUID ownerUserId) {
        id = project.id(); this.tenantId = tenantId; this.ownerUserId = ownerUserId;
        projectCode = project.projectCode(); name = project.name();
        startMode = project.startMode().name(); createdAt = project.createdAt(); update(project, detailsJson);
    }
    void update(ProjectSummary project, String detailsJson) {
        status = project.status(); snapshotVersion = project.currentSnapshotVersion(); this.detailsJson = detailsJson; updatedAt = project.updatedAt();
    }
    String id() { return id; } String projectCode() { return projectCode; } String name() { return name; }
    String tenantId() { return tenantId; } UUID ownerUserId() { return ownerUserId; }
    void updateOwnership(String tenantId, UUID ownerUserId) {
        this.tenantId = tenantId;
        if (ownerUserId != null) this.ownerUserId = ownerUserId;
    }
    String startMode() { return startMode; } String status() { return status; } int snapshotVersion() { return snapshotVersion; }
    String detailsJson() { return detailsJson; } Instant createdAt() { return createdAt; } Instant updatedAt() { return updatedAt; }
}

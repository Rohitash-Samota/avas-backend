package com.avas.platform.project.persistence;

import com.avas.platform.project.ProjectModels.RequirementSummary;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "requirement_snapshots", indexes = @Index(name = "idx_requirements_project_version", columnList = "projectId,version", unique = true))
class RequirementSnapshotEntity {
    @Id @Column(length = 120) private String id;
    @Column(nullable = false, length = 80) private String projectId;
    @Column(nullable = false) private int version;
    @Column(nullable = false, length = 30) private String handlingLevel;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String payloadJson;
    @Column(nullable = false) private Instant approvedAt;
    protected RequirementSnapshotEntity() {}
    RequirementSnapshotEntity(RequirementSummary value, String payloadJson) {
        id = value.snapshotId(); projectId = value.projectId(); version = value.version(); handlingLevel = value.handlingLevel();
        this.payloadJson = payloadJson; approvedAt = value.approvedAt();
    }
}

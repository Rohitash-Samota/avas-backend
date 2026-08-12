package com.avas.platform.project.persistence;

import com.avas.platform.common.persistence.AbstractLongIdEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "project_state_snapshots")
class ProjectStateEntity extends AbstractLongIdEntity {
    @Column(name = "project_id", nullable = false, unique = true, length = 80) private String projectId;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String payloadJson;
    @Column(nullable = false) private Instant updatedAt;
    protected ProjectStateEntity() {}
    ProjectStateEntity(String projectId, String payloadJson) { this.projectId = projectId; update(payloadJson); }
    void update(String payloadJson) { this.payloadJson = payloadJson; this.updatedAt = Instant.now(); }
    String payloadJson() { return payloadJson; }
}

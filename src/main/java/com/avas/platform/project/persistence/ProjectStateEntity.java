package com.avas.platform.project.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "project_state_snapshots")
class ProjectStateEntity {
    @Id @Column(length = 80) private String projectId;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String payloadJson;
    @Column(nullable = false) private Instant updatedAt;
    protected ProjectStateEntity() {}
    ProjectStateEntity(String projectId, String payloadJson) { this.projectId = projectId; update(payloadJson); }
    void update(String payloadJson) { this.payloadJson = payloadJson; this.updatedAt = Instant.now(); }
    String payloadJson() { return payloadJson; }
}

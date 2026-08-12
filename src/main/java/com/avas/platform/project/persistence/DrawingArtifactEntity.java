package com.avas.platform.project.persistence;

import com.avas.platform.project.DrawingCandidate;
import com.avas.platform.common.persistence.AbstractLongIdEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "drawing_artifacts", indexes = @Index(name = "idx_drawings_project_version", columnList = "projectId,version"))
class DrawingArtifactEntity extends AbstractLongIdEntity {
    @Column(name = "drawing_id", nullable = false, unique = true, length = 120) private String drawingId;
    @Column(nullable = false, length = 80) private String projectId;
    @Column(nullable = false) private int version;
    @Column(nullable = false, length = 60) private String strategy;
    @Column(nullable = false, length = 40) private String status;
    @Column(nullable = false) private boolean conceptApproved;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String payloadJson;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    protected DrawingArtifactEntity() {}
    DrawingArtifactEntity(DrawingCandidate value, String payloadJson) {
        drawingId = value.id(); projectId = value.projectId(); version = value.version(); strategy = value.strategy();
        createdAt = value.createdAt(); update(value, payloadJson);
    }
    void update(DrawingCandidate value, String payloadJson) { status = value.status(); conceptApproved = value.conceptApproved(); this.payloadJson = payloadJson; }
}

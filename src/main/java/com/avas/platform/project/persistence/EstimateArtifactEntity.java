package com.avas.platform.project.persistence;

import com.avas.platform.project.ProjectModels.Estimate;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "estimate_artifacts", indexes = @Index(name = "idx_estimates_project_version", columnList = "projectId,version"))
class EstimateArtifactEntity {
    @Id @Column(length = 120) private String id;
    @Column(nullable = false, length = 80) private String projectId;
    @Column(nullable = false, length = 120) private String drawingId;
    @Column(nullable = false) private int version;
    @Column(nullable = false) private long recommendedAmount;
    @Column(nullable = false) private boolean approved;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String payloadJson;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    protected EstimateArtifactEntity() {}
    EstimateArtifactEntity(Estimate value, String payloadJson) {
        id = value.id(); projectId = value.projectId(); drawingId = value.drawingId(); version = value.version(); createdAt = value.createdAt();
        update(value, payloadJson);
    }
    void update(Estimate value, String payloadJson) { recommendedAmount = value.recommended(); approved = value.approved(); this.payloadJson = payloadJson; }
}

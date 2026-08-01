package com.avas.platform.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "governance_audit", indexes = @Index(name = "idx_governance_audit_aggregate", columnList = "aggregateType,aggregateId,createdAt"))
class GovernanceAuditEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 80) private String eventType;
    @Column(nullable = false, length = 80) private String aggregateType;
    @Column(nullable = false, length = 80) private String aggregateId;
    @Column(nullable = false) private UUID actorId;
    @Column(nullable = false, length = 40) private String actorRole;
    @Column(nullable = false, length = 1000) private String summary;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    protected GovernanceAuditEntity() {}

    GovernanceAuditEntity(String eventType, String aggregateType, String aggregateId,
            UUID actorId, String actorRole, String summary) {
        id = UUID.randomUUID();
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.summary = summary;
        createdAt = Instant.now();
    }
}

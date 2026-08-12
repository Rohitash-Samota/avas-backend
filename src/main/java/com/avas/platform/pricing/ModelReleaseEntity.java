package com.avas.platform.pricing;

import com.avas.platform.common.persistence.AbstractLongIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "model_releases",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_model_release_name_version",
                columnNames = {"name", "releaseVersion"}),
        indexes = @Index(name = "idx_model_release_active", columnList = "modelType,status"))
class ModelReleaseEntity extends AbstractLongIdEntity {
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ModelType modelType;

    @Column(nullable = false, length = 60)
    private String releaseVersion;

    @Column(nullable = false, length = 100)
    private String provider;

    @Column(length = 120)
    private String runtime;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal validationScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModelStatus status;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private UUID activatedBy;
    private Instant activatedAt;

    protected ModelReleaseEntity() {
    }

    ModelReleaseEntity(ModelReleaseRequest request, UUID actor) {
        publicId = UUID.randomUUID();
        name = request.name().trim();
        modelType = request.modelType() == null ? ModelType.RECOMMENDATION : request.modelType();
        releaseVersion = request.version().trim();
        provider = request.provider().trim();
        runtime = clean(request.runtime());
        validationScore = request.validationScore() == null ? BigDecimal.ZERO.setScale(4)
                : request.validationScore().setScale(4, java.math.RoundingMode.HALF_UP);
        status = ModelStatus.DRAFT;
        notes = clean(request.notes());
        createdBy = actor;
        createdAt = Instant.now();
    }

    void activate(UUID actor) {
        if (status != ModelStatus.VALIDATED || validationScore.compareTo(new BigDecimal("0.7000")) < 0) {
            throw new IllegalStateException("Only a validated model with score 0.70 or higher can be activated");
        }
        status = ModelStatus.ACTIVE;
        activatedBy = actor;
        activatedAt = Instant.now();
    }

    void validate(BigDecimal score, String note) {
        if (status != ModelStatus.DRAFT) {
            throw new IllegalStateException("Only a draft model can be validated");
        }
        validationScore = score.setScale(4, java.math.RoundingMode.HALF_UP);
        status = ModelStatus.VALIDATED;
        if (note != null && !note.isBlank()) {
            notes = (notes == null ? "" : notes + "\n") + "Validation: " + note.trim();
        }
    }

    void retire() {
        if (status == ModelStatus.ACTIVE) {
            status = ModelStatus.RETIRED;
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    UUID id() {
        return publicId;
    }

    String name() {
        return name;
    }

    ModelType modelType() {
        return modelType;
    }

    String releaseVersion() {
        return releaseVersion;
    }

    String provider() {
        return provider;
    }

    String runtime() {
        return runtime;
    }

    BigDecimal validationScore() {
        return validationScore;
    }

    ModelStatus status() {
        return status;
    }

    String notes() {
        return notes;
    }

    UUID createdBy() {
        return createdBy;
    }

    Instant createdAt() {
        return createdAt;
    }

    UUID activatedBy() {
        return activatedBy;
    }

    Instant activatedAt() {
        return activatedAt;
    }
}

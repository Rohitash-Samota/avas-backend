package com.avas.platform.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ModelReleaseRepository extends JpaRepository<ModelReleaseEntity, Long> {
    Optional<ModelReleaseEntity> findByPublicId(UUID publicId);

    boolean existsByNameIgnoreCaseAndReleaseVersionIgnoreCase(String name, String releaseVersion);

    List<ModelReleaseEntity> findAllByOrderByCreatedAtDesc();

    List<ModelReleaseEntity> findByModelTypeAndStatus(ModelType modelType, ModelStatus status);

    Optional<ModelReleaseEntity> findFirstByModelTypeAndStatusOrderByActivatedAtDesc(
            ModelType modelType,
            ModelStatus status);
}

package com.avas.platform.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.avas.platform.pricing.PricingModels.*;

interface PriceSubmissionRepository extends JpaRepository<PriceSubmissionEntity, UUID> {
    List<PriceSubmissionEntity> findBySubmittedByOrderByCreatedAtDesc(UUID submittedBy);
    List<PriceSubmissionEntity> findAllByOrderByCreatedAtDesc();
    List<PriceSubmissionEntity> findByStatusOrderByCreatedAtDesc(PriceSubmissionStatus status);
}

interface PlatformConfigurationRepository extends JpaRepository<PlatformConfigurationEntity, String> {}

interface ModelReleaseRepository extends JpaRepository<ModelReleaseEntity, UUID> {
    boolean existsByNameIgnoreCaseAndReleaseVersionIgnoreCase(String name, String releaseVersion);
    List<ModelReleaseEntity> findAllByOrderByCreatedAtDesc();
    List<ModelReleaseEntity> findByModelTypeAndStatus(ModelType modelType, ModelStatus status);
    Optional<ModelReleaseEntity> findFirstByModelTypeAndStatusOrderByActivatedAtDesc(ModelType modelType, ModelStatus status);
}

interface BudgetRecommendationRepository extends JpaRepository<BudgetRecommendationEntity, UUID> {}
interface GovernanceAuditRepository extends JpaRepository<GovernanceAuditEntity, UUID> {}

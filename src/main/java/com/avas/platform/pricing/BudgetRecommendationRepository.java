package com.avas.platform.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface BudgetRecommendationRepository extends JpaRepository<BudgetRecommendationEntity, Long> {
    Optional<BudgetRecommendationEntity> findByPublicId(UUID publicId);
}

package com.avas.platform.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PriceSubmissionRepository extends JpaRepository<PriceSubmissionEntity, Long> {
    Optional<PriceSubmissionEntity> findByPublicId(UUID publicId);

    List<PriceSubmissionEntity> findBySubmittedByOrderByCreatedAtDesc(UUID submittedBy);

    List<PriceSubmissionEntity> findAllByOrderByCreatedAtDesc();

    List<PriceSubmissionEntity> findByStatusOrderByCreatedAtDesc(PriceSubmissionStatus status);
}

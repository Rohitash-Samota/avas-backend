package com.avas.platform.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MaterialProductRepository extends JpaRepository<MaterialProductEntity, Long>,
        JpaSpecificationExecutor<MaterialProductEntity> {

    Optional<MaterialProductEntity> findByFingerprint(String fingerprint);

    /**
     * Loads a whole batch's existing rows in one query.
     *
     * <p>A crawl posts hundreds of products at a time and most of them already exist. Looking each
     * one up individually turns one ingest into hundreds of round trips, which is what makes a
     * re-crawl of a large site unworkable rather than merely slow.</p>
     */
    List<MaterialProductEntity> findAllByFingerprintIn(Collection<String> fingerprints);

    Optional<MaterialProductEntity> findByPublicId(UUID publicId);

    long countBySourceSite(String sourceSite);

    long countByReviewStatus(CatalogReviewStatus reviewStatus);
}

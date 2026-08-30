package com.avas.platform.project.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface ConceptRenderRepository extends JpaRepository<ConceptRenderEntity, Long> {
    Optional<ConceptRenderEntity> findByDrawingIdAndStyleAndBriefKey(String drawingId, String style, String briefKey);

    Optional<ConceptRenderEntity> findFirstByDrawingIdAndStyleStartingWithOrderByCreatedAtDesc(
            String drawingId, String stylePrefix);

    List<ConceptRenderEntity> findAllByDrawingId(String drawingId);

    void deleteAllByDrawingIdIn(List<String> drawingIds);
}

package com.avas.platform.pricing;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Document(PricingIntelligenceDocument.COLLECTION)
record PricingIntelligenceDocument(
        @Id String id,
        @Indexed String snapshotType,
        @Indexed UUID recommendationId,
        @Indexed String city,
        String category,
        String areaBand,
        BigDecimal evidenceUnitPrice,
        BigDecimal lowBudget,
        BigDecimal recommendedBudget,
        BigDecimal highBudget,
        BigDecimal actualBudget,
        String confidence,
        int evidenceCount,
        long configurationVersion,
        String modelRelease,
        Boolean accepted,
        boolean consentedLearning,
        List<String> explanations,
        Instant createdAt
) {
    static final String COLLECTION = "pricing_intelligence_snapshots";

    static PricingIntelligenceDocument recommendation(BudgetRecommendationEntity value) {
        return new PricingIntelligenceDocument(null, "RECOMMENDATION", value.id(), value.city(),
                value.category().name(), areaBand(value.builtUpAreaSqFt()), value.evidenceUnitPrice(), value.lowBudget(),
                value.recommendedBudget(), value.highBudget(), null, value.confidence().name(), value.evidenceCount(),
                value.configurationVersion(), value.modelRelease(), null, false, value.explanations(), Instant.now());
    }

    static PricingIntelligenceDocument feedback(BudgetRecommendationEntity value) {
        return new PricingIntelligenceDocument(null, "CONSENTED_FEEDBACK", value.id(), value.city(),
                value.category().name(), areaBand(value.builtUpAreaSqFt()), value.evidenceUnitPrice(), value.lowBudget(),
                value.recommendedBudget(), value.highBudget(), value.actualBudget(), value.confidence().name(),
                value.evidenceCount(), value.configurationVersion(), value.modelRelease(), value.accepted(), true,
                value.explanations(), Instant.now());
    }

    private static String areaBand(BigDecimal area) {
        var value = area.intValue();
        var lower = Math.max(0, value / 250 * 250);
        return lower + "-" + (lower + 249) + " sq ft";
    }
}

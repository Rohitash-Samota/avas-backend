package com.avas.platform.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BudgetRecommendationResponse(
        UUID id,
        String city,
        BigDecimal builtUpAreaSqFt,
        PriceCategory category,
        BigDecimal evidenceUnitPrice,
        BigDecimal lowBudget,
        BigDecimal recommendedBudget,
        BigDecimal highBudget,
        BigDecimal totalBudget,
        BudgetFit budgetFit,
        PriceCategory suggestedCategory,
        ConfidenceLevel confidenceLevel,
        int confidence,
        int evidenceCount,
        int sampleCount,
        String priceSource,
        List<String> assumptions,
        List<String> explanations,
        String modelRelease,
        String modelVersion,
        long configurationVersion,
        Instant createdAt,
        Boolean accepted,
        BigDecimal actualBudget,
        String feedbackNote,
        Boolean consentToLearning
) {
    static BudgetRecommendationResponse from(BudgetRecommendationEntity value) {
        return new BudgetRecommendationResponse(
                value.id(),
                value.city(),
                value.builtUpAreaSqFt(),
                value.category(),
                value.evidenceUnitPrice(),
                value.lowBudget(),
                value.recommendedBudget(),
                value.highBudget(),
                value.totalBudget(),
                value.budgetFit(),
                value.suggestedCategory(),
                value.confidence(),
                confidenceScore(value),
                value.evidenceCount(),
                value.evidenceCount(),
                value.priceSource(),
                value.assumptions(),
                value.explanations(),
                value.modelRelease(),
                value.modelRelease(),
                value.configurationVersion(),
                value.createdAt(),
                value.accepted(),
                value.actualBudget(),
                value.feedbackNote(),
                value.consentToLearning());
    }

    private static int confidenceScore(BudgetRecommendationEntity value) {
        return switch (value.confidence()) {
            case LOW -> 35;
            case MEDIUM -> 65;
            case HIGH -> 90;
        };
    }
}

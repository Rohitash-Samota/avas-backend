package com.avas.platform.pricing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "avas.mongo.enabled", havingValue = "true", matchIfMissing = true)
class MongoPricingIntelligenceStore implements PricingIntelligenceStore {
    private final PricingIntelligenceRepository repository;

    MongoPricingIntelligenceStore(PricingIntelligenceRepository repository) { this.repository = repository; }

    @Override
    public void recordRecommendation(BudgetRecommendationEntity recommendation) {
        repository.save(PricingIntelligenceDocument.recommendation(recommendation));
    }

    @Override
    public void recordConsentedFeedback(BudgetRecommendationEntity recommendation) {
        repository.save(PricingIntelligenceDocument.feedback(recommendation));
    }
}

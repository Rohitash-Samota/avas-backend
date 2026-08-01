package com.avas.platform.pricing;

interface PricingIntelligenceStore {
    void recordRecommendation(BudgetRecommendationEntity recommendation);
    void recordConsentedFeedback(BudgetRecommendationEntity recommendation);
}

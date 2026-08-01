package com.avas.platform.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.avas.platform.pricing.PricingModels.PlatformConfigurationRequest;

@Entity
@Table(name = "platform_configuration")
class PlatformConfigurationEntity {
    static final String GLOBAL_ID = "GLOBAL";

    @Id @Column(length = 30) private String id;
    @Column(nullable = false, length = 3) private String defaultCurrency;
    @Column(nullable = false, length = 100) private String defaultCity;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal economyCostPerSqFt;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal standardCostPerSqFt;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal premiumCostPerSqFt;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal luxuryCostPerSqFt;
    @Column(nullable = false, precision = 6, scale = 2) private BigDecimal contingencyPercent;
    @Column(nullable = false) private int minConfidenceSources;
    @Column(nullable = false) private int priceFreshnessDays;
    @Column(nullable = false, precision = 5, scale = 2) private BigDecimal confidenceThreshold;
    @Column(nullable = false) private int recommendationValidityDays;
    @Column(nullable = false) private boolean contributionsEnabled;
    @Column(nullable = false) private boolean aiExplanationEnabled;
    @Column(nullable = false) private boolean learningEnabled;
    @Column(nullable = false) private long configurationVersion;
    private UUID updatedBy;
    @Column(nullable = false) private Instant updatedAt;

    protected PlatformConfigurationEntity() {}

    static PlatformConfigurationEntity defaults() {
        var value = new PlatformConfigurationEntity();
        value.id = GLOBAL_ID;
        value.defaultCurrency = "INR";
        value.defaultCity = "Jaipur";
        value.economyCostPerSqFt = new BigDecimal("1400.00");
        value.standardCostPerSqFt = new BigDecimal("1900.00");
        value.premiumCostPerSqFt = new BigDecimal("2600.00");
        value.luxuryCostPerSqFt = new BigDecimal("3600.00");
        value.contingencyPercent = new BigDecimal("7.50");
        value.minConfidenceSources = 4;
        value.priceFreshnessDays = 180;
        value.confidenceThreshold = new BigDecimal("70.00");
        value.recommendationValidityDays = 30;
        value.contributionsEnabled = true;
        value.aiExplanationEnabled = true;
        value.learningEnabled = true;
        value.configurationVersion = 1;
        value.updatedAt = Instant.now();
        return value;
    }

    void update(PlatformConfigurationRequest request, UUID actor) {
        if (request.economyCostPerSqFt().compareTo(request.standardCostPerSqFt()) > 0
                || request.standardCostPerSqFt().compareTo(request.premiumCostPerSqFt()) > 0
                || request.premiumCostPerSqFt().compareTo(request.luxuryCostPerSqFt()) > 0) {
            throw new IllegalArgumentException("Cost levels must be ordered: economy, standard, premium, then luxury");
        }
        defaultCurrency = request.defaultCurrency().trim().toUpperCase(java.util.Locale.ROOT);
        defaultCity = request.defaultCity().trim();
        economyCostPerSqFt = money(request.economyCostPerSqFt());
        standardCostPerSqFt = money(request.standardCostPerSqFt());
        premiumCostPerSqFt = money(request.premiumCostPerSqFt());
        luxuryCostPerSqFt = money(request.luxuryCostPerSqFt());
        contingencyPercent = request.defaultContingencyPercent().setScale(2, java.math.RoundingMode.HALF_UP);
        minConfidenceSources = request.minimumVerifiedSamples();
        priceFreshnessDays = request.priceFreshnessDays();
        confidenceThreshold = request.confidenceThreshold().setScale(2, java.math.RoundingMode.HALF_UP);
        recommendationValidityDays = request.recommendationValidityDays();
        contributionsEnabled = request.contributionsEnabled();
        aiExplanationEnabled = request.aiExplanationEnabled();
        learningEnabled = request.learningEnabled();
        configurationVersion++;
        updatedBy = actor;
        updatedAt = Instant.now();
    }

    BigDecimal rate(PricingModels.PriceCategory category) {
        return switch (category) {
            case ECONOMY -> economyCostPerSqFt;
            case STANDARD -> standardCostPerSqFt;
            case PREMIUM -> premiumCostPerSqFt;
            case LUXURY -> luxuryCostPerSqFt;
        };
    }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, java.math.RoundingMode.HALF_UP); }
    String id() { return id; }
    String defaultCurrency() { return defaultCurrency; }
    String defaultCity() { return defaultCity; }
    BigDecimal economyCostPerSqFt() { return economyCostPerSqFt; }
    BigDecimal standardCostPerSqFt() { return standardCostPerSqFt; }
    BigDecimal premiumCostPerSqFt() { return premiumCostPerSqFt; }
    BigDecimal luxuryCostPerSqFt() { return luxuryCostPerSqFt; }
    BigDecimal contingencyPercent() { return contingencyPercent; }
    int minConfidenceSources() { return minConfidenceSources; }
    int priceFreshnessDays() { return priceFreshnessDays; }
    BigDecimal confidenceThreshold() { return confidenceThreshold; }
    int recommendationValidityDays() { return recommendationValidityDays; }
    boolean contributionsEnabled() { return contributionsEnabled; }
    boolean aiExplanationEnabled() { return aiExplanationEnabled; }
    boolean learningEnabled() { return learningEnabled; }
    long configurationVersion() { return configurationVersion; }
    UUID updatedBy() { return updatedBy; }
    Instant updatedAt() { return updatedAt; }
}

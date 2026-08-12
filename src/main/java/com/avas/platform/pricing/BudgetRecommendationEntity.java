package com.avas.platform.pricing;

import com.avas.platform.common.persistence.AbstractLongIdEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "budget_recommendations",
        indexes = @Index(
                name = "idx_budget_recommendation_user",
                columnList = "requestedBy,createdAt"))
class BudgetRecommendationEntity extends AbstractLongIdEntity {
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(nullable = false, length = 60)
    private String tenantId;

    @Column(nullable = false)
    private UUID requestedBy;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal builtUpAreaSqFt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PriceCategory category;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal evidenceUnitPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal lowBudget;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal recommendedBudget;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal highBudget;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalBudget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BudgetFit budgetFit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PriceCategory suggestedCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConfidenceLevel confidence;

    @Column(nullable = false)
    private int evidenceCount;

    @Column(nullable = false, length = 80)
    private String priceSource;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "budget_recommendation_assumptions", joinColumns = @JoinColumn(name = "recommendation_id"))
    @Column(name = "assumption", nullable = false, length = 500)
    private List<String> assumptions = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "budget_recommendation_explanations", joinColumns = @JoinColumn(name = "recommendation_id"))
    @Column(name = "explanation", nullable = false, length = 500)
    private List<String> explanations = new ArrayList<>();

    @Column(nullable = false, length = 160)
    private String modelRelease;

    @Column(nullable = false)
    private long configurationVersion;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Boolean accepted;

    @Column(precision = 19, scale = 2)
    private BigDecimal actualBudget;

    @Column(length = 1000)
    private String feedbackNote;

    private Boolean consentToLearning;
    private Instant feedbackAt;

    protected BudgetRecommendationEntity() {
    }

    BudgetRecommendationEntity(String tenantId, UUID requestedBy, BudgetRecommendationRequest request,
            BigDecimal evidenceUnitPrice, BigDecimal lowBudget, BigDecimal recommendedBudget, BigDecimal highBudget,
            BudgetFit budgetFit, PriceCategory suggestedCategory, ConfidenceLevel confidence, int evidenceCount,
            String priceSource, List<String> assumptions, List<String> explanations, String modelRelease,
            long configurationVersion) {
        publicId = UUID.randomUUID();
        this.tenantId = tenantId;
        this.requestedBy = requestedBy;
        city = request.city().trim();
        builtUpAreaSqFt = request.builtUpAreaSqFt().setScale(2, java.math.RoundingMode.HALF_UP);
        category = request.category();
        this.evidenceUnitPrice = money(evidenceUnitPrice);
        this.lowBudget = money(lowBudget);
        this.recommendedBudget = money(recommendedBudget);
        this.highBudget = money(highBudget);
        totalBudget = request.totalBudget() == null ? null : money(request.totalBudget());
        this.budgetFit = budgetFit;
        this.suggestedCategory = suggestedCategory;
        this.confidence = confidence;
        this.evidenceCount = evidenceCount;
        this.priceSource = priceSource;
        this.assumptions.addAll(assumptions);
        this.explanations.addAll(explanations);
        this.modelRelease = modelRelease;
        this.configurationVersion = configurationVersion;
        createdAt = Instant.now();
    }

    void feedback(BudgetFeedbackRequest request) {
        accepted = request.accepted();
        actualBudget = request.actualBudget() == null ? null : money(request.actualBudget());
        feedbackNote = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        consentToLearning = Boolean.TRUE.equals(request.consentToLearning());
        feedbackAt = Instant.now();
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    UUID id() {
        return publicId;
    }

    String tenantId() {
        return tenantId;
    }

    UUID requestedBy() {
        return requestedBy;
    }

    String city() {
        return city;
    }

    BigDecimal builtUpAreaSqFt() {
        return builtUpAreaSqFt;
    }

    PriceCategory category() {
        return category;
    }

    BigDecimal evidenceUnitPrice() {
        return evidenceUnitPrice;
    }

    BigDecimal lowBudget() {
        return lowBudget;
    }

    BigDecimal recommendedBudget() {
        return recommendedBudget;
    }

    BigDecimal highBudget() {
        return highBudget;
    }

    BigDecimal totalBudget() {
        return totalBudget;
    }

    BudgetFit budgetFit() {
        return budgetFit;
    }

    PriceCategory suggestedCategory() {
        return suggestedCategory;
    }

    ConfidenceLevel confidence() {
        return confidence;
    }

    int evidenceCount() {
        return evidenceCount;
    }

    String priceSource() {
        return priceSource;
    }

    List<String> assumptions() {
        return List.copyOf(assumptions);
    }

    List<String> explanations() {
        return List.copyOf(explanations);
    }

    String modelRelease() {
        return modelRelease;
    }

    long configurationVersion() {
        return configurationVersion;
    }

    Instant createdAt() {
        return createdAt;
    }

    Boolean accepted() {
        return accepted;
    }

    BigDecimal actualBudget() {
        return actualBudget;
    }

    String feedbackNote() {
        return feedbackNote;
    }

    Boolean consentToLearning() {
        return consentToLearning;
    }

    Instant feedbackAt() {
        return feedbackAt;
    }
}

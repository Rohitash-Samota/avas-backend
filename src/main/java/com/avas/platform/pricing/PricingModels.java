package com.avas.platform.pricing;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PricingModels {
    private PricingModels() {}

    public enum PriceCategory { ECONOMY, STANDARD, PREMIUM, LUXURY }
    public enum PriceItemType { COST_PER_SQFT, MATERIAL, LABOUR, SERVICE, PACKAGE }
    public enum PriceSubmissionStatus { PENDING, APPROVED, REJECTED }
    public enum ConfidenceLevel { LOW, MEDIUM, HIGH }
    public enum BudgetFit { WITHIN_BUDGET, STRETCHED, ABOVE_BUDGET, NOT_PROVIDED }
    public enum ModelType { REQUIREMENT, RECOMMENDATION, PRICE_RANKING, ESTIMATE }
    public enum ModelStatus { DRAFT, VALIDATED, ACTIVE, RETIRED }

    public record PriceSubmissionRequest(
            @NotBlank @Size(max = 160) String itemName,
            PriceItemType itemType,
            @NotBlank @Size(max = 40) String category,
            PriceCategory qualityTier,
            @NotBlank @Size(max = 100) String city,
            @Size(max = 100) String state,
            @NotBlank @Size(max = 40) String unit,
            @NotNull @Positive @JsonAlias({"price", "amount"}) BigDecimal unitPrice,
            @Positive BigDecimal quantity,
            LocalDate observedOn,
            LocalDate effectiveFrom,
            LocalDate expiresOn,
            @Size(max = 160) String supplierName,
            @Size(max = 160) String source,
            @Size(max = 1000) String notes,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal taxPercentage,
            Boolean materialIncluded,
            Boolean labourIncluded,
            Boolean transportIncluded
    ) {}

    public record PriceSubmissionResponse(
            UUID id, String tenantId, UUID submittedBy, String submittedRole, String itemName,
            PriceItemType itemType, String category, PriceCategory qualityTier, String city, String state, String unit,
            BigDecimal unitPrice, BigDecimal quantity, LocalDate observedOn, LocalDate effectiveFrom,
            LocalDate expiresOn, String supplierName, String source, String notes, BigDecimal taxPercentage,
            boolean materialIncluded, boolean labourIncluded, boolean transportIncluded,
            PriceSubmissionStatus status, int version, UUID reviewedBy, Instant reviewedAt,
            String reviewNote, String decisionNote, Instant createdAt, Instant submittedAt
    ) {
        static PriceSubmissionResponse from(PriceSubmissionEntity value) {
            return new PriceSubmissionResponse(value.id(), value.tenantId(), value.submittedBy(),
                    value.submittedRole(), value.itemName(), value.itemType(), value.category(), value.qualityTier(), value.city(),
                    value.state(), value.unit(), value.unitPrice(), value.quantity(), value.observedOn(),
                    value.effectiveFrom(), value.expiresOn(), value.supplierName(), value.source(), value.notes(),
                    value.taxPercentage(), value.materialIncluded(), value.labourIncluded(), value.transportIncluded(),
                    value.status(), value.recordVersion(), value.reviewedBy(), value.reviewedAt(), value.reviewNote(),
                    value.reviewNote(), value.createdAt(), value.createdAt());
        }
    }

    public record PriceSubmissionDecisionRequest(
            @NotNull @JsonAlias("status") PriceSubmissionStatus decision,
            @Size(max = 1000) String note
    ) {}

    public record BudgetRecommendationRequest(
            @NotBlank @Size(max = 100) String city,
            @NotNull @Positive BigDecimal builtUpAreaSqFt,
            @NotNull PriceCategory category,
            @Positive BigDecimal totalBudget
    ) {}

    public record BudgetRecommendationResponse(
            UUID id, String city, BigDecimal builtUpAreaSqFt, PriceCategory category,
            BigDecimal evidenceUnitPrice, BigDecimal lowBudget, BigDecimal recommendedBudget,
            BigDecimal highBudget, BigDecimal totalBudget, BudgetFit budgetFit,
            PriceCategory suggestedCategory, ConfidenceLevel confidenceLevel, int confidence, int evidenceCount, int sampleCount,
            String priceSource, List<String> assumptions, List<String> explanations,
            String modelRelease, String modelVersion, long configurationVersion, Instant createdAt,
            Boolean accepted, BigDecimal actualBudget, String feedbackNote, Boolean consentToLearning
    ) {
        static BudgetRecommendationResponse from(BudgetRecommendationEntity value) {
            return new BudgetRecommendationResponse(value.id(), value.city(), value.builtUpAreaSqFt(),
                    value.category(), value.evidenceUnitPrice(), value.lowBudget(), value.recommendedBudget(),
                    value.highBudget(), value.totalBudget(), value.budgetFit(), value.suggestedCategory(),
                    value.confidence(), confidenceScore(value), value.evidenceCount(), value.evidenceCount(), value.priceSource(), value.assumptions(),
                    value.explanations(), value.modelRelease(), value.modelRelease(), value.configurationVersion(), value.createdAt(),
                    value.accepted(), value.actualBudget(), value.feedbackNote(), value.consentToLearning());
        }

        private static int confidenceScore(BudgetRecommendationEntity value) {
            return switch (value.confidence()) { case LOW -> 35; case MEDIUM -> 65; case HIGH -> 90; };
        }
    }

    public record BudgetFeedbackRequest(
            @NotNull Boolean accepted,
            @Positive BigDecimal actualBudget,
            @Size(max = 1000) String note,
            Boolean consentToLearning
    ) {}

    public record PlatformConfigurationRequest(
            @NotBlank @Size(min = 3, max = 3) String defaultCurrency,
            @NotBlank @Size(max = 100) String defaultCity,
            @NotNull @Positive BigDecimal economyCostPerSqFt,
            @NotNull @Positive BigDecimal standardCostPerSqFt,
            @NotNull @Positive BigDecimal premiumCostPerSqFt,
            @NotNull @Positive BigDecimal luxuryCostPerSqFt,
            @NotNull @DecimalMin("0.0") @DecimalMax("50.0") @JsonAlias("contingencyPercent") BigDecimal defaultContingencyPercent,
            @NotNull @Positive @JsonAlias("minConfidenceSources") Integer minimumVerifiedSamples,
            @NotNull @Positive Integer priceFreshnessDays,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal confidenceThreshold,
            @NotNull @Positive Integer recommendationValidityDays,
            @NotNull Boolean contributionsEnabled,
            @NotNull Boolean aiExplanationEnabled,
            @NotNull Boolean learningEnabled
    ) {}

    public record PlatformConfigurationResponse(
            String id, String defaultCurrency, String defaultCity, BigDecimal economyCostPerSqFt,
            BigDecimal standardCostPerSqFt, BigDecimal premiumCostPerSqFt, BigDecimal luxuryCostPerSqFt,
            BigDecimal defaultContingencyPercent, int minimumVerifiedSamples, int priceFreshnessDays,
            BigDecimal confidenceThreshold, int recommendationValidityDays, boolean contributionsEnabled,
            boolean aiExplanationEnabled, boolean learningEnabled, long version, UUID updatedBy, Instant updatedAt
    ) {
        static PlatformConfigurationResponse from(PlatformConfigurationEntity value) {
            return new PlatformConfigurationResponse(value.id(), value.defaultCurrency(), value.defaultCity(),
                    value.economyCostPerSqFt(), value.standardCostPerSqFt(), value.premiumCostPerSqFt(),
                    value.luxuryCostPerSqFt(), value.contingencyPercent(), value.minConfidenceSources(),
                    value.priceFreshnessDays(), value.confidenceThreshold(), value.recommendationValidityDays(),
                    value.contributionsEnabled(), value.aiExplanationEnabled(), value.learningEnabled(),
                    value.configurationVersion(), value.updatedBy(), value.updatedAt());
        }
    }

    public record ModelReleaseRequest(
            @NotBlank @Size(max = 120) String name,
            ModelType modelType,
            @NotBlank @Size(max = 60) String version,
            @NotBlank @Size(max = 100) String provider,
            @Size(max = 120) @JsonAlias("modelName") String runtime,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal validationScore,
            ModelStatus status,
            @Size(max = 2000) @JsonAlias("description") String notes
    ) {}

    public record ModelValidationRequest(
            @NotNull @DecimalMin("0.7") @DecimalMax("1.0") BigDecimal validationScore,
            @Size(max = 1000) String note
    ) {}

    public record ModelReleaseResponse(
            UUID id, String name, ModelType modelType, String version, String provider, String runtime,
            String modelName, BigDecimal validationScore, ModelStatus status, String notes, String description, UUID createdBy,
            Instant createdAt, UUID activatedBy, Instant activatedAt
    ) {
        static ModelReleaseResponse from(ModelReleaseEntity value) {
            return new ModelReleaseResponse(value.id(), value.name(), value.modelType(), value.releaseVersion(),
                    value.provider(), value.runtime(), value.runtime(), value.validationScore(), value.status(), value.notes(), value.notes(),
                    value.createdBy(), value.createdAt(), value.activatedBy(), value.activatedAt());
        }
    }
}

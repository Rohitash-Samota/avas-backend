package com.avas.platform.pricing;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.avas.platform.pricing.PricingModels.*;

@Service
public class PricingService {
    private static final Logger log = LoggerFactory.getLogger(PricingService.class);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final PriceSubmissionRepository submissions;
    private final PlatformConfigurationRepository configurations;
    private final ModelReleaseRepository models;
    private final BudgetRecommendationRepository recommendations;
    private final GovernanceAuditRepository audit;
    private final ObjectProvider<PricingIntelligenceStore> intelligence;

    PricingService(PriceSubmissionRepository submissions, PlatformConfigurationRepository configurations,
            ModelReleaseRepository models, BudgetRecommendationRepository recommendations,
            GovernanceAuditRepository audit, ObjectProvider<PricingIntelligenceStore> intelligence) {
        this.submissions = submissions;
        this.configurations = configurations;
        this.models = models;
        this.recommendations = recommendations;
        this.audit = audit;
        this.intelligence = intelligence;
    }

    @PostConstruct
    void seedConfiguration() {
        if (!configurations.existsById(PlatformConfigurationEntity.GLOBAL_ID)) {
            configurations.save(PlatformConfigurationEntity.defaults());
        }
    }

    @Transactional
    public PriceSubmissionResponse submit(UUID userId, String tenantId, String activeRole,
            PriceSubmissionRequest request) {
        validateDates(request);
        if (!configuration().contributionsEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Price contributions are currently disabled by the administrator");
        }
        var value = submissions.save(new PriceSubmissionEntity(tenantId, userId, activeRole, request));
        record("PRICE_SUBMITTED", "PRICE_SUBMISSION", value.id().toString(), userId, activeRole,
                "Submitted " + value.itemName() + " pricing evidence for " + value.city());
        return PriceSubmissionResponse.from(value);
    }

    @Transactional(readOnly = true)
    public List<PriceSubmissionResponse> mine(UUID userId) {
        return submissions.findBySubmittedByOrderByCreatedAtDesc(userId).stream()
                .map(PriceSubmissionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PriceSubmissionResponse> allSubmissions() {
        return submissions.findAllByOrderByCreatedAtDesc().stream().map(PriceSubmissionResponse::from).toList();
    }

    @Transactional
    public PriceSubmissionResponse decide(UUID id, UUID actor, String activeRole,
            PriceSubmissionDecisionRequest request) {
        if (request.decision() == PriceSubmissionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED");
        }
        var value = requiredSubmission(id);
        if (value.status() != PriceSubmissionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A price submission decision is immutable; submit a new version instead");
        }
        value.decide(request.decision(), actor, request.note());
        value = submissions.save(value);
        record("PRICE_" + request.decision().name(), "PRICE_SUBMISSION", id.toString(), actor, activeRole,
                request.decision().name() + " price evidence; history retained as version " + value.recordVersion());
        return PriceSubmissionResponse.from(value);
    }

    @Transactional
    public BudgetRecommendationResponse recommend(UUID userId, String tenantId,
            BudgetRecommendationRequest request) {
        var configuration = configuration();
        var today = LocalDate.now();
        var evidence = submissions.findByStatusOrderByCreatedAtDesc(PriceSubmissionStatus.APPROVED).stream()
                .filter(value -> value.itemType() == PriceItemType.COST_PER_SQFT)
                .filter(value -> value.qualityTier() == request.category())
                .filter(value -> value.city().equalsIgnoreCase(request.city().trim()))
                .filter(value -> value.currentlyEffective(today, configuration.priceFreshnessDays()))
                .sorted(Comparator.comparing(PriceSubmissionEntity::unitPrice))
                .toList();

        var rate = evidence.isEmpty() ? configuration.rate(request.category()) : median(evidence);
        var source = evidence.isEmpty() ? "AVAS_ADMIN_BASE_PRICE" : "APPROVED_LOCAL_EVIDENCE";
        var confidence = confidence(evidence.size(), configuration.minConfidenceSources());
        var factor = BigDecimal.ONE.add(configuration.contingencyPercent().divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
        var base = rate.multiply(request.builtUpAreaSqFt());
        var low = base.multiply(new BigDecimal("0.92"));
        var recommended = base.multiply(factor);
        var high = base.multiply(new BigDecimal("1.12")).multiply(factor);
        var fit = fit(request.totalBudget(), low, recommended);
        var suggested = suggestedCategory(request, configuration, factor);
        var activeModel = models.findFirstByModelTypeAndStatusOrderByActivatedAtDesc(
                        ModelType.RECOMMENDATION, ModelStatus.ACTIVE)
                .map(value -> value.name() + ":" + value.releaseVersion())
                .orElse("rules-budget-v1");

        var assumptions = new ArrayList<String>();
        assumptions.add("Planning estimate uses built-up area and excludes land, finance and registration costs.");
        assumptions.add("A " + configuration.contingencyPercent().stripTrailingZeros().toPlainString()
                + "% contingency is included in the recommended amount.");
        if (evidence.isEmpty()) assumptions.add("No current approved local evidence was available; the administrator base price was used.");
        var explanations = new ArrayList<String>();
        explanations.add("Rate: INR " + money(rate).toPlainString() + " per sq ft from " + source + ".");
        explanations.add("Confidence is " + confidence + " from " + evidence.size() + " approved local source(s).");
        if (request.totalBudget() != null) {
            explanations.add("The supplied budget is classified as " + fit.name().replace('_', ' ').toLowerCase() + ".");
            if (suggested != request.category()) explanations.add("Consider the " + suggested.name().toLowerCase() + " category for a closer budget fit.");
        }

        var value = recommendations.save(new BudgetRecommendationEntity(tenantId, userId, request, rate, low,
                recommended, high, fit, suggested, confidence, evidence.size(), source, assumptions, explanations,
                activeModel, configuration.configurationVersion()));
        record("BUDGET_RECOMMENDED", "BUDGET_RECOMMENDATION", value.id().toString(), userId, "INDIVIDUAL",
                "Created explainable budget recommendation using " + source);
        writeRecommendation(value);
        return BudgetRecommendationResponse.from(value);
    }

    @Transactional
    public BudgetRecommendationResponse feedback(UUID id, UUID userId, String activeRole,
            BudgetFeedbackRequest request) {
        var value = recommendations.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget recommendation not found"));
        if (!value.requestedBy().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This recommendation belongs to another account");
        }
        value.feedback(request);
        value = recommendations.save(value);
        var learningAllowed = configuration().learningEnabled() && Boolean.TRUE.equals(value.consentToLearning());
        record("BUDGET_FEEDBACK_RECORDED", "BUDGET_RECOMMENDATION", id.toString(), userId, activeRole,
                learningAllowed ? "Saved consented de-identified learning feedback" : "Saved feedback without learning use");
        if (learningAllowed) writeFeedback(value);
        return BudgetRecommendationResponse.from(value);
    }

    @Transactional(readOnly = true)
    public PlatformConfigurationResponse getConfiguration() {
        return PlatformConfigurationResponse.from(configuration());
    }

    @Transactional
    public PlatformConfigurationResponse updateConfiguration(UUID actor, String activeRole,
            PlatformConfigurationRequest request) {
        var value = configuration();
        value.update(request, actor);
        value = configurations.save(value);
        record("PLATFORM_CONFIGURATION_CHANGED", "PLATFORM_CONFIGURATION", value.id(), actor, activeRole,
                "Published pricing configuration version " + value.configurationVersion());
        return PlatformConfigurationResponse.from(value);
    }

    @Transactional(readOnly = true)
    public List<ModelReleaseResponse> modelReleases() {
        return models.findAllByOrderByCreatedAtDesc().stream().map(ModelReleaseResponse::from).toList();
    }

    @Transactional
    public ModelReleaseResponse registerModel(UUID actor, String activeRole, ModelReleaseRequest request) {
        if (models.existsByNameIgnoreCaseAndReleaseVersionIgnoreCase(request.name().trim(), request.version().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This model release already exists");
        }
        try {
            var value = models.save(new ModelReleaseEntity(request, actor));
            record("MODEL_RELEASE_REGISTERED", "MODEL_RELEASE", value.id().toString(), actor, activeRole,
                    "Registered model " + value.name() + ":" + value.releaseVersion() + " for offline validation");
            return ModelReleaseResponse.from(value);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This model release already exists");
        }
    }

    @Transactional
    public ModelReleaseResponse activateModel(UUID id, UUID actor, String activeRole) {
        var value = models.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Model release not found"));
        models.findByModelTypeAndStatus(value.modelType(), ModelStatus.ACTIVE).stream()
                .filter(active -> !active.id().equals(id)).forEach(ModelReleaseEntity::retire);
        try {
            value.activate(actor);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        value = models.save(value);
        record("MODEL_OR_RULE_RELEASE_CHANGED", "MODEL_RELEASE", id.toString(), actor, activeRole,
                "Activated " + value.name() + ":" + value.releaseVersion() + " with rollback history retained");
        return ModelReleaseResponse.from(value);
    }

    @Transactional
    public ModelReleaseResponse validateModel(UUID id, UUID actor, String activeRole, ModelValidationRequest request) {
        var value = models.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Model release not found"));
        try {
            value.validate(request.validationScore(), request.note());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        value = models.save(value);
        record("MODEL_RELEASE_VALIDATED", "MODEL_RELEASE", id.toString(), actor, activeRole,
                "Recorded offline validation score " + value.validationScore());
        return ModelReleaseResponse.from(value);
    }

    private PlatformConfigurationEntity configuration() {
        return configurations.findById(PlatformConfigurationEntity.GLOBAL_ID).orElseGet(() ->
                configurations.save(PlatformConfigurationEntity.defaults()));
    }

    private PriceSubmissionEntity requiredSubmission(UUID id) {
        return submissions.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Price submission not found"));
    }

    private void validateDates(PriceSubmissionRequest request) {
        var observed = request.observedOn() == null ? LocalDate.now() : request.observedOn();
        var effective = request.effectiveFrom() == null ? observed : request.effectiveFrom();
        if (observed.isAfter(LocalDate.now().plusDays(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Observed date cannot be in the future");
        }
        if (request.expiresOn() != null && request.expiresOn().isBefore(effective)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry date must be after the effective date");
        }
    }

    private static BigDecimal median(List<PriceSubmissionEntity> values) {
        var middle = values.size() / 2;
        if (values.size() % 2 == 1) return values.get(middle).unitPrice();
        return values.get(middle - 1).unitPrice().add(values.get(middle).unitPrice())
                .divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
    }

    private static ConfidenceLevel confidence(int count, int target) {
        if (count >= target) return ConfidenceLevel.HIGH;
        if (count >= 2) return ConfidenceLevel.MEDIUM;
        return ConfidenceLevel.LOW;
    }

    private static BudgetFit fit(BigDecimal total, BigDecimal low, BigDecimal recommended) {
        if (total == null) return BudgetFit.NOT_PROVIDED;
        if (total.compareTo(recommended) >= 0) return BudgetFit.WITHIN_BUDGET;
        if (total.compareTo(low) >= 0) return BudgetFit.STRETCHED;
        return BudgetFit.ABOVE_BUDGET;
    }

    private static PriceCategory suggestedCategory(BudgetRecommendationRequest request,
            PlatformConfigurationEntity configuration, BigDecimal factor) {
        if (request.totalBudget() == null) return request.category();
        var requested = configuration.rate(request.category()).multiply(request.builtUpAreaSqFt()).multiply(factor);
        if (request.totalBudget().compareTo(requested) >= 0) return request.category();
        if (request.category() == PriceCategory.LUXURY) {
            var premium = configuration.rate(PriceCategory.PREMIUM).multiply(request.builtUpAreaSqFt()).multiply(factor);
            if (request.totalBudget().compareTo(premium) >= 0) return PriceCategory.PREMIUM;
        }
        if (request.category() == PriceCategory.PREMIUM) {
            var standard = configuration.rate(PriceCategory.STANDARD).multiply(request.builtUpAreaSqFt()).multiply(factor);
            if (request.totalBudget().compareTo(standard) >= 0) return PriceCategory.STANDARD;
        }
        return PriceCategory.ECONOMY;
    }

    private void record(String event, String aggregate, String aggregateId, UUID actor,
            String role, String summary) {
        audit.save(new GovernanceAuditEntity(event, aggregate, aggregateId, actor,
                role == null ? "UNKNOWN" : role, summary));
    }

    private void writeRecommendation(BudgetRecommendationEntity value) {
        var store = intelligence.getIfAvailable();
        if (store == null) return;
        try { store.recordRecommendation(value); }
        catch (RuntimeException exception) { log.warn("Mongo pricing snapshot could not be written: {}", exception.getMessage()); }
    }

    private void writeFeedback(BudgetRecommendationEntity value) {
        var store = intelligence.getIfAvailable();
        if (store == null) return;
        try { store.recordConsentedFeedback(value); }
        catch (RuntimeException exception) { log.warn("Mongo pricing feedback could not be written: {}", exception.getMessage()); }
    }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}

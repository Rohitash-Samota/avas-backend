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

@Service
public class PricingService implements CostRateProvider {
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
        if (!configurations.existsByConfigurationKey(PlatformConfigurationEntity.GLOBAL_KEY)) {
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
        var value = submissions.save(new PriceSubmissionEntity(tenantId, userId,
                activeRole, authorisedChannel(request.sourceChannel(), activeRole), request));
        record("PRICE_SUBMITTED", "PRICE_SUBMISSION", value.id().toString(), userId, activeRole,
                "Submitted " + value.itemName() + " pricing evidence for " + value.city()
                        + " via " + value.sourceChannel());
        return PriceSubmissionResponse.from(value);
    }

    /**
     * Decides which provenance channel a caller is allowed to claim.
     *
     * <p>The channel drives how much weight the evidence carries, so it cannot be self-asserted by
     * whoever posts the request. An ordinary submission is USER regardless of what the body says;
     * only an administrator may record collector output or an official schedule, which is how the
     * scraper's PENDING rows get their channel without letting an arbitrary caller mint OFFICIAL
     * evidence.</p>
     */
    private PriceSourceChannel authorisedChannel(PriceSourceChannel requested, String activeRole) {
        if (requested == null) {
            return "BUILDER".equals(activeRole) ? PriceSourceChannel.BUILDER : PriceSourceChannel.USER;
        }
        if ("ADMIN".equals(activeRole)) {
            return requested;
        }
        if ("BUILDER".equals(activeRole) && requested == PriceSourceChannel.BUILDER) {
            return PriceSourceChannel.BUILDER;
        }
        if (requested == PriceSourceChannel.USER) {
            return PriceSourceChannel.USER;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only an administrator may record " + requested + " price evidence");
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
                .filter(value -> CostRateProvider.canonicalCity(value.city()).equalsIgnoreCase(
                        CostRateProvider.canonicalCity(request.city())))
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

    /**
     * Resolves a complete read-only pricing snapshot for project costing. Approved evidence is
     * selected deterministically as of the supplied date; mutable repositories are never exposed to
     * the project module.
     */
    @Override
    @Transactional(readOnly = true)
    public CostRateSnapshot resolve(CostRateQuery query) {
        var configuration = configuration();
        var city = CostRateProvider.canonicalCity(query.city());
        var approved = submissions.findByStatusOrderByCreatedAtDesc(PriceSubmissionStatus.APPROVED).stream()
                .filter(value -> value.qualityTier() == query.qualityTier())
                .filter(value -> CostRateProvider.canonicalCity(value.city()).equalsIgnoreCase(city))
                .filter(value -> value.currentlyEffective(query.asOf(), configuration.priceFreshnessDays()))
                .toList();

        var baseEvidence = approved.stream()
                .filter(value -> value.itemType() == PriceItemType.COST_PER_SQFT)
                .sorted(Comparator.comparing(PriceSubmissionEntity::unitPrice))
                .toList();
        var baseRate = baseEvidence.isEmpty() ? configuration.rate(query.qualityTier()) : median(baseEvidence);
        var baseSource = baseEvidence.isEmpty() ? "AVAS_ADMIN_BASE_PRICE" : "APPROVED_LOCAL_EVIDENCE";
        var resolved = new ArrayList<CostRateEvidence>();
        for (var requirement : query.requirements()) {
            var candidates = approved.stream()
                    .filter(value -> value.itemType() == requirement.itemType())
                    .filter(value -> canonicalKey(value.category()).equals(canonicalKey(requirement.category())))
                    .filter(value -> canonicalKey(value.unit()).equals(canonicalKey(requirement.unit())))
                    .toList();
            if (candidates.isEmpty()) continue;
            // Only the most trustworthy channel present is priced from. Market observation informs
            // a rate when nothing better exists, but it never dilutes accountable evidence by being
            // averaged in alongside it.
            var bestChannel = candidates.stream()
                    .map(PriceSubmissionEntity::sourceChannel)
                    .max(Comparator.comparingInt(PriceSourceChannel::trust))
                    .orElse(PriceSourceChannel.USER);
            var matches = candidates.stream()
                    .filter(value -> value.sourceChannel() == bestChannel)
                    .sorted(Comparator.comparing(PriceSubmissionEntity::unitPrice))
                    .toList();
            // Freeze one real observation so the evidence ID, brand/product and rate always refer
            // to the same approved record. Sample count still communicates the supporting pool.
            var representative = matches.get((matches.size() - 1) / 2);
            resolved.add(new CostRateEvidence(requirement.code(), representative.id().toString(),
                    representative.itemName(), representative.itemType(), representative.category(),
                    representative.qualityTier(), CostRateProvider.canonicalCity(representative.city()),
                    representative.state(), representative.unit(), representative.unitPrice(), representative.productCode(),
                    representative.brandName(), representative.specification(), representative.supplierName(),
                    representative.source(), representative.observedOn(), representative.effectiveFrom(),
                    representative.expiresOn(), representative.taxPercentage(), representative.materialIncluded(),
                    representative.labourIncluded(), representative.transportIncluded(), matches.size(),
                    confidence(matches.size(), configuration.minConfidenceSources())));
        }
        var representativeBase = baseEvidence.isEmpty() ? null : baseEvidence.get(baseEvidence.size() / 2);
        return new CostRateSnapshot(configuration.defaultCurrency(), city, query.qualityTier(), query.asOf(), baseRate,
                baseSource, representativeBase == null ? null : representativeBase.id().toString(),
                baseEvidence.size(), configuration.contingencyPercent(), configuration.recommendationValidityDays(),
                configuration.configurationVersion(), resolved);
    }

    @Transactional
    public BudgetRecommendationResponse feedback(UUID id, UUID userId, String activeRole,
            BudgetFeedbackRequest request) {
        var value = recommendations.findByPublicId(id).orElseThrow(() ->
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
        var value = models.findByPublicId(id).orElseThrow(() ->
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
        var value = models.findByPublicId(id).orElseThrow(() ->
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
        return configurations.findByConfigurationKey(PlatformConfigurationEntity.GLOBAL_KEY).orElseGet(() ->
                configurations.save(PlatformConfigurationEntity.defaults()));
    }

    private PriceSubmissionEntity requiredSubmission(UUID id) {
        return submissions.findByPublicId(id).orElseThrow(() ->
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

    private static String canonicalKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_").replaceAll("(^_|_$)", "");
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

package com.avas.platform.pricing;

import com.avas.platform.common.persistence.AbstractLongIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "price_submissions", indexes = {
        @Index(name = "idx_price_submission_user", columnList = "submittedBy,createdAt"),
        @Index(name = "idx_price_submission_lookup", columnList = "status,city,category,itemType")
})
class PriceSubmissionEntity extends AbstractLongIdEntity {
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(nullable = false, length = 60)
    private String tenantId;

    @Column(nullable = false)
    private UUID submittedBy;

    @Column(nullable = false, length = 40)
    private String submittedRole;

    @Column(nullable = false, length = 160)
    private String itemName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PriceItemType itemType;

    @Column(nullable = false, length = 40)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PriceCategory qualityTier;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(nullable = false, length = 40)
    private String unit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false)
    private LocalDate observedOn;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate expiresOn;

    @Column(length = 160)
    private String supplierName;

    @Column(length = 120)
    private String brandName;

    @Column(length = 100)
    private String productCode;

    @Column(length = 1000)
    private String specification;

    @Column(length = 160)
    private String source;

    @Column(length = 1000)
    private String notes;

    @Column(precision = 6, scale = 2)
    private BigDecimal taxPercentage;

    @Column(nullable = false)
    private boolean materialIncluded;

    @Column(nullable = false)
    private boolean labourIncluded;

    @Column(nullable = false)
    private boolean transportIncluded;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PriceSubmissionStatus status;

    /** How this observation reached AVAS; drives how far it may influence a resolved rate. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 20)
    private PriceSourceChannel sourceChannel;

    /** Page the observation was read from, when it was collected automatically. */
    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    /** Groups every row produced by one collector execution so a bad run can be retired together. */
    @Column(name = "collector_run_id", length = 80)
    private String collectorRunId;

    @Column(nullable = false)
    private int recordVersion;

    private UUID reviewedBy;
    private Instant reviewedAt;

    @Column(length = 1000)
    private String reviewNote;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected PriceSubmissionEntity() {
    }

    PriceSubmissionEntity(String tenantId, UUID submittedBy, String submittedRole,
            PriceSourceChannel channel, PriceSubmissionRequest request) {
        publicId = UUID.randomUUID();
        this.tenantId = tenantId;
        this.submittedBy = submittedBy;
        this.submittedRole = submittedRole;
        itemName = request.itemName().trim();
        category = request.category().trim().toUpperCase(java.util.Locale.ROOT);
        itemType = request.itemType() == null ? inferItemType(category, request.unit()) : request.itemType();
        qualityTier = request.qualityTier() == null ? PriceCategory.STANDARD : request.qualityTier();
        city = request.city().trim();
        state = clean(request.state());
        unit = request.unit().trim();
        unitPrice = money(request.unitPrice());
        quantity = request.quantity();
        observedOn = request.observedOn() == null ? LocalDate.now() : request.observedOn();
        effectiveFrom = request.effectiveFrom() == null ? observedOn : request.effectiveFrom();
        expiresOn = request.expiresOn();
        supplierName = clean(request.supplierName());
        brandName = clean(request.brandName());
        productCode = clean(request.productCode());
        specification = clean(request.specification());
        source = clean(request.source());
        notes = clean(request.notes());
        taxPercentage = request.taxPercentage() == null ? BigDecimal.ZERO : request.taxPercentage();
        materialIncluded = Boolean.TRUE.equals(request.materialIncluded());
        labourIncluded = Boolean.TRUE.equals(request.labourIncluded());
        transportIncluded = Boolean.TRUE.equals(request.transportIncluded());
        status = PriceSubmissionStatus.PENDING;
        sourceChannel = PriceSourceChannel.orDefault(channel);
        sourceUrl = clean(request.sourceUrl());
        collectorRunId = clean(request.collectorRunId());
        recordVersion = 1;
        createdAt = Instant.now();
    }

    void decide(PriceSubmissionStatus decision, UUID reviewer, String note) {
        status = decision;
        reviewedBy = reviewer;
        reviewedAt = Instant.now();
        reviewNote = clean(note);
    }

    boolean currentlyEffective(LocalDate today, int freshnessDays) {
        return status == PriceSubmissionStatus.APPROVED
                && !effectiveFrom.isAfter(today)
                && (expiresOn == null || !expiresOn.isBefore(today))
                && !observedOn.isBefore(today.minusDays(freshnessDays));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static PriceItemType inferItemType(String category, String unit) {
        if ("COST_PER_SQFT".equals(category)) {
            return PriceItemType.COST_PER_SQFT;
        }
        return switch (category) {
            case "LABOUR" -> PriceItemType.LABOUR;
            case "SERVICE", "EQUIPMENT" -> PriceItemType.SERVICE;
            case "WORK", "PACKAGE" -> PriceItemType.PACKAGE;
            default -> PriceItemType.MATERIAL;
        };
    }

    UUID id() {
        return publicId;
    }

    String tenantId() {
        return tenantId;
    }

    UUID submittedBy() {
        return submittedBy;
    }

    String submittedRole() {
        return submittedRole;
    }

    String itemName() {
        return itemName;
    }

    PriceItemType itemType() {
        return itemType;
    }

    String category() {
        return category;
    }

    PriceCategory qualityTier() {
        return qualityTier;
    }

    String city() {
        return city;
    }

    String state() {
        return state;
    }

    String unit() {
        return unit;
    }

    BigDecimal unitPrice() {
        return unitPrice;
    }

    BigDecimal quantity() {
        return quantity;
    }

    LocalDate observedOn() {
        return observedOn;
    }

    LocalDate effectiveFrom() {
        return effectiveFrom;
    }

    LocalDate expiresOn() {
        return expiresOn;
    }

    String supplierName() {
        return supplierName;
    }

    String brandName() {
        return brandName;
    }

    String productCode() {
        return productCode;
    }

    String specification() {
        return specification;
    }

    String source() {
        return source;
    }

    String notes() {
        return notes;
    }

    BigDecimal taxPercentage() {
        return taxPercentage;
    }

    boolean materialIncluded() {
        return materialIncluded;
    }

    boolean labourIncluded() {
        return labourIncluded;
    }

    boolean transportIncluded() {
        return transportIncluded;
    }

    PriceSubmissionStatus status() {
        return status;
    }

    PriceSourceChannel sourceChannel() {
        return sourceChannel == null ? PriceSourceChannel.USER : sourceChannel;
    }

    String sourceUrl() {
        return sourceUrl;
    }

    String collectorRunId() {
        return collectorRunId;
    }

    int recordVersion() {
        return recordVersion;
    }

    UUID reviewedBy() {
        return reviewedBy;
    }

    Instant reviewedAt() {
        return reviewedAt;
    }

    String reviewNote() {
        return reviewNote;
    }

    Instant createdAt() {
        return createdAt;
    }
}

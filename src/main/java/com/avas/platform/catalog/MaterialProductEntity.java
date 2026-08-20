package com.avas.platform.catalog;

import com.avas.platform.common.persistence.AbstractLongIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One construction-material product as a public listing described it.
 *
 * <p>Two representations are kept deliberately. The typed columns are the <em>normalised</em>
 * product — the vocabulary AVAS searches and reports on. {@code rawPayload} is the collector's
 * verbatim reading of the page, stored because a normaliser is only ever as good as its current
 * rules: when a site changes its markup or a mapping turns out wrong, the raw rows can be replayed
 * instead of the whole catalogue having to be re-crawled.</p>
 *
 * <p>{@code fingerprint} is what makes a re-run idempotent. A crawl reaches the same product by
 * several routes — a category page, a subcategory page, a paginated listing — and running weekly
 * must update the row it made last week rather than adding a second one.</p>
 */
@Entity
@Table(name = "catalog_material_products", indexes = {
        @Index(name = "idx_material_product_fingerprint", columnList = "fingerprint", unique = true),
        @Index(name = "idx_material_product_search", columnList = "reviewStatus,category,sourceSite"),
        @Index(name = "idx_material_product_brand", columnList = "brand"),
        @Index(name = "idx_material_product_run", columnList = "collectorRunId")
})
public class MaterialProductEntity extends AbstractLongIdEntity {
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    /**
     * Stable identity for one product across runs: normally a hash of source site and product URL,
     * falling back to site plus SKU when the site's URLs carry session or tracking parameters.
     */
    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(nullable = false, length = 60)
    private String tenantId;

    // --- Provenance -------------------------------------------------------------------------

    @Column(nullable = false, length = 60)
    private String sourceSite;

    @Column(nullable = false, length = 1000)
    private String productUrl;

    /** The site's own product id, when it publishes one. Not unique across sites. */
    @Column(length = 120)
    private String sourceProductId;

    @Column(length = 80)
    private String collectorRunId;

    // --- Identity ---------------------------------------------------------------------------

    @Column(nullable = false, length = 300)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ConstructionCategory category;

    @Column(length = 160)
    private String subcategory;

    /** The site's own category wording, kept so a normalisation mistake stays traceable. */
    @Column(length = 500)
    private String sourceCategoryPath;

    @Column(length = 160)
    private String brand;

    @Column(length = 200)
    private String manufacturer;

    @Column(length = 120)
    private String modelCode;

    @Column(length = 120)
    private String sku;

    @Column(columnDefinition = "TEXT")
    private String description;

    // --- Physical description ---------------------------------------------------------------

    @Column(length = 200)
    private String size;

    @Column(length = 200)
    private String materialComposition;

    /** Label/value pairs the listing published as a specification table, as a JSON object. */
    @Column(columnDefinition = "TEXT")
    private String specifications;

    /** Everything else the page exposed that has no column of its own, as a JSON object. */
    @Column(columnDefinition = "TEXT")
    private String attributes;

    // --- Commercial terms -------------------------------------------------------------------

    @Column(precision = 19, scale = 4)
    private BigDecimal price;

    @Column(precision = 19, scale = 4)
    private BigDecimal discountPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 40)
    private String unit;

    @Column(precision = 19, scale = 3)
    private BigDecimal minimumOrderQuantity;

    @Column(length = 40)
    private String minimumOrderUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockStatus stockStatus;

    // --- Seller -----------------------------------------------------------------------------

    @Column(length = 200)
    private String sellerName;

    @Column(length = 200)
    private String sellerLocation;

    @Column(length = 120)
    private String sellerCity;

    @Column(length = 120)
    private String sellerState;

    // --- Reputation and media ---------------------------------------------------------------

    @Column(precision = 3, scale = 2)
    private BigDecimal rating;

    private Integer reviewCount;

    /** Absolute image URLs as a JSON array. */
    @Column(columnDefinition = "TEXT")
    private String imageUrls;

    // --- Governance and lifecycle -----------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CatalogReviewStatus reviewStatus;

    @Column(length = 1000)
    private String reviewNote;

    private UUID reviewedBy;

    private Instant reviewedAt;

    @Column(columnDefinition = "TEXT")
    private String rawPayload;

    @Column(nullable = false)
    private Instant firstSeenAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** How many runs have found this product. A listing seen once is weaker evidence than one seen weekly. */
    @Column(nullable = false)
    private int observationCount;

    @Column(nullable = false)
    private boolean active;

    protected MaterialProductEntity() {
    }

    MaterialProductEntity(String fingerprint, String tenantId, String sourceSite, String productUrl,
                          String name, ConstructionCategory category, Instant seenAt) {
        this.publicId = UUID.randomUUID();
        this.fingerprint = fingerprint;
        this.tenantId = tenantId;
        this.sourceSite = sourceSite;
        this.productUrl = productUrl;
        this.name = name;
        this.category = category;
        this.currency = "INR";
        this.stockStatus = StockStatus.UNKNOWN;
        this.reviewStatus = CatalogReviewStatus.PENDING;
        this.firstSeenAt = seenAt;
        this.lastSeenAt = seenAt;
        this.updatedAt = seenAt;
        this.observationCount = 0;
        this.active = true;
    }

    /**
     * Records that a run saw this product again.
     *
     * <p>{@code firstSeenAt} and the review decision are untouched on purpose: a re-crawl is new
     * information about the listing, not grounds to re-open a decision an administrator already
     * made about it.</p>
     */
    void seenAgain(Instant seenAt) {
        this.lastSeenAt = seenAt;
        this.updatedAt = seenAt;
        this.observationCount += 1;
    }

    void review(CatalogReviewStatus status, UUID reviewer, String note, Instant at) {
        this.reviewStatus = status;
        this.reviewedBy = reviewer;
        this.reviewNote = note;
        this.reviewedAt = at;
        this.updatedAt = at;
    }

    void deactivate(Instant at) {
        this.active = false;
        this.updatedAt = at;
    }

    public UUID getId() { return publicId; }
    public String getFingerprint() { return fingerprint; }
    public String getTenantId() { return tenantId; }
    public String getSourceSite() { return sourceSite; }
    public String getProductUrl() { return productUrl; }
    public String getSourceProductId() { return sourceProductId; }
    public String getCollectorRunId() { return collectorRunId; }
    public String getName() { return name; }
    public ConstructionCategory getCategory() { return category; }
    public String getSubcategory() { return subcategory; }
    public String getSourceCategoryPath() { return sourceCategoryPath; }
    public String getBrand() { return brand; }
    public String getManufacturer() { return manufacturer; }
    public String getModelCode() { return modelCode; }
    public String getSku() { return sku; }
    public String getDescription() { return description; }
    public String getSize() { return size; }
    public String getMaterialComposition() { return materialComposition; }
    public String getSpecifications() { return specifications; }
    public String getAttributes() { return attributes; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getDiscountPrice() { return discountPrice; }
    public String getCurrency() { return currency; }
    public String getUnit() { return unit; }
    public BigDecimal getMinimumOrderQuantity() { return minimumOrderQuantity; }
    public String getMinimumOrderUnit() { return minimumOrderUnit; }
    public StockStatus getStockStatus() { return stockStatus; }
    public String getSellerName() { return sellerName; }
    public String getSellerLocation() { return sellerLocation; }
    public String getSellerCity() { return sellerCity; }
    public String getSellerState() { return sellerState; }
    public BigDecimal getRating() { return rating; }
    public Integer getReviewCount() { return reviewCount; }
    public String getImageUrls() { return imageUrls; }
    public CatalogReviewStatus getReviewStatus() { return reviewStatus; }
    public String getReviewNote() { return reviewNote; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getRawPayload() { return rawPayload; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getObservationCount() { return observationCount; }
    public boolean isActive() { return active; }

    void setProductUrl(String productUrl) { this.productUrl = productUrl; }
    void setSourceProductId(String sourceProductId) { this.sourceProductId = sourceProductId; }
    void setCollectorRunId(String collectorRunId) { this.collectorRunId = collectorRunId; }
    void setName(String name) { this.name = name; }
    void setCategory(ConstructionCategory category) { this.category = category; }
    void setSubcategory(String subcategory) { this.subcategory = subcategory; }
    void setSourceCategoryPath(String sourceCategoryPath) { this.sourceCategoryPath = sourceCategoryPath; }
    void setBrand(String brand) { this.brand = brand; }
    void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    void setModelCode(String modelCode) { this.modelCode = modelCode; }
    void setSku(String sku) { this.sku = sku; }
    void setDescription(String description) { this.description = description; }
    void setSize(String size) { this.size = size; }
    void setMaterialComposition(String materialComposition) { this.materialComposition = materialComposition; }
    void setSpecifications(String specifications) { this.specifications = specifications; }
    void setAttributes(String attributes) { this.attributes = attributes; }
    void setPrice(BigDecimal price) { this.price = price; }
    void setDiscountPrice(BigDecimal discountPrice) { this.discountPrice = discountPrice; }
    void setCurrency(String currency) { this.currency = currency; }
    void setUnit(String unit) { this.unit = unit; }
    void setMinimumOrderQuantity(BigDecimal quantity) { this.minimumOrderQuantity = quantity; }
    void setMinimumOrderUnit(String minimumOrderUnit) { this.minimumOrderUnit = minimumOrderUnit; }
    void setStockStatus(StockStatus stockStatus) { this.stockStatus = stockStatus; }
    void setSellerName(String sellerName) { this.sellerName = sellerName; }
    void setSellerLocation(String sellerLocation) { this.sellerLocation = sellerLocation; }
    void setSellerCity(String sellerCity) { this.sellerCity = sellerCity; }
    void setSellerState(String sellerState) { this.sellerState = sellerState; }
    void setRating(BigDecimal rating) { this.rating = rating; }
    void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }
    void setImageUrls(String imageUrls) { this.imageUrls = imageUrls; }
    void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
}

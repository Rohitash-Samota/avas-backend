package com.avas.platform.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ingests and serves the collected construction-material catalogue.
 *
 * <p>Ingest is an upsert keyed on the collector's fingerprint, not an insert. A crawl reaches the
 * same product from several category pages and is expected to run on a schedule, so "seen again"
 * has to be the ordinary case: it refreshes the listing's fields, advances {@code lastSeenAt} and
 * leaves both {@code firstSeenAt} and any administrator decision alone.</p>
 *
 * <p>Nothing here approves anything. Collected rows are Tier 3 market observation under the AVAS
 * specification and land {@link CatalogReviewStatus#PENDING}; only {@link #decide} moves them, and
 * only a caller holding {@code CATALOG_MANAGE} can reach it.</p>
 */
@Service
public class CatalogService {
    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    /** Beyond this a description is a scraped page rather than a product description. */
    private static final int MAX_DESCRIPTION = 8_000;
    private static final int MAX_JSON = 16_000;
    private static final int MAX_IMAGES = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final MaterialProductRepository products;
    private final ObjectMapper json;

    CatalogService(MaterialProductRepository products, ObjectMapper json) {
        this.products = products;
        this.json = json;
    }

    @Transactional
    public CatalogIngestResponse ingest(CatalogIngestRequest request, String tenantId) {
        var now = Instant.now();
        var runId = trim(request.collectorRunId(), 80);
        var rejections = new ArrayList<CatalogIngestResponse.RejectedProduct>();

        // One product can legitimately arrive twice inside a batch when a crawl reached it from two
        // category pages. Collapsing here keeps the row count honest and avoids two writes racing
        // for the same fingerprint inside one transaction.
        var batch = new LinkedHashMap<String, MaterialProductRequest>();
        for (var candidate : request.products()) {
            var fingerprint = trim(candidate.fingerprint(), 64);
            if (fingerprint == null) {
                rejections.add(new CatalogIngestResponse.RejectedProduct(
                        null, candidate.productUrl(), "A product needs a fingerprint to be de-duplicated"));
                continue;
            }
            batch.put(fingerprint, candidate);
        }

        var existing = products.findAllByFingerprintIn(batch.keySet()).stream()
                .collect(Collectors.toMap(MaterialProductEntity::getFingerprint, entity -> entity,
                        (first, second) -> first));

        var created = 0;
        var updated = 0;
        for (var entry : batch.entrySet()) {
            var fingerprint = entry.getKey();
            var candidate = entry.getValue();
            try {
                var entity = existing.get(fingerprint);
                if (entity == null) {
                    entity = new MaterialProductEntity(fingerprint, tenantId,
                            trim(candidate.sourceSite(), 60), trim(candidate.productUrl(), 1000),
                            trim(candidate.name(), 300), resolveCategory(candidate), now);
                    created++;
                } else {
                    updated++;
                }
                apply(entity, candidate, runId, now);
                entity.seenAgain(now);
                products.save(entity);
            } catch (RuntimeException error) {
                // One unreadable product must not lose the other four hundred in the batch.
                log.warn("rejecting collected product {}: {}", candidate.productUrl(), error.toString());
                rejections.add(new CatalogIngestResponse.RejectedProduct(
                        fingerprint, candidate.productUrl(), error.getMessage()));
            }
        }
        log.info("catalogue ingest run={} created={} updated={} rejected={}",
                runId, created, updated, rejections.size());
        return new CatalogIngestResponse(created, updated, rejections.size(), List.copyOf(rejections));
    }

    @Transactional(readOnly = true)
    public Page<MaterialProduct> search(CatalogReviewStatus status, String category, String sourceSite,
                                        String brand, String text, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "lastSeenAt"));
        // A category the caller invented is a typo, not "everything": silently widening the search
        // would answer a question they did not ask.
        ConstructionCategory resolved = null;
        if (category != null && !category.isBlank()) {
            resolved = ConstructionCategory.parse(category);
            if (resolved == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category: " + category);
            }
        }
        var filter = MaterialProductSearch.of(status, resolved, blankToNull(sourceSite),
                blankToNull(brand), blankToNull(text));
        return products.findAll(filter, pageable).map(this::toProduct);
    }

    @Transactional(readOnly = true)
    public MaterialProduct product(UUID id) {
        return products.findByPublicId(id)
                .map(this::toProduct)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown product"));
    }

    @Transactional
    public MaterialProduct decide(UUID id, CatalogDecisionRequest request, UUID reviewer) {
        var entity = products.findByPublicId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown product"));
        if (request.status() == CatalogReviewStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A review decision must approve or reject; it cannot return a product to the queue");
        }
        entity.review(request.status(), reviewer, trim(request.note(), 1000), Instant.now());
        return toProduct(products.save(entity));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> statistics() {
        var counts = new LinkedHashMap<String, Long>();
        counts.put("total", products.count());
        for (var status : CatalogReviewStatus.values()) {
            counts.put(status.name().toLowerCase(java.util.Locale.ROOT), products.countByReviewStatus(status));
        }
        return counts;
    }

    // --- normalisation ---------------------------------------------------------------------

    private ConstructionCategory resolveCategory(MaterialProductRequest candidate) {
        var declared = ConstructionCategory.parse(candidate.category());
        if (declared != null) {
            return declared;
        }
        // The site's own breadcrumb first, then the product's own words: a breadcrumb reading
        // "Cement" is a stronger signal than "cement" inside the description of a mixer machine.
        return ConstructionCategory.classify(
                candidate.sourceCategoryPath(), candidate.subcategory(), candidate.name(),
                candidate.description());
    }

    private void apply(MaterialProductEntity entity, MaterialProductRequest candidate, String runId, Instant now) {
        entity.setName(require(trim(candidate.name(), 300), "name"));
        entity.setProductUrl(require(trim(candidate.productUrl(), 1000), "productUrl"));
        entity.setCategory(resolveCategory(candidate));
        entity.setSubcategory(trim(candidate.subcategory(), 160));
        entity.setSourceCategoryPath(trim(candidate.sourceCategoryPath(), 500));
        entity.setSourceProductId(trim(candidate.sourceProductId(), 120));
        entity.setBrand(trim(candidate.brand(), 160));
        entity.setManufacturer(trim(candidate.manufacturer(), 200));
        entity.setModelCode(trim(candidate.modelCode(), 120));
        entity.setSku(trim(candidate.sku(), 120));
        entity.setDescription(trim(candidate.description(), MAX_DESCRIPTION));
        entity.setSize(trim(candidate.size(), 200));
        entity.setMaterialComposition(trim(candidate.materialComposition(), 200));
        entity.setSpecifications(writeJson(candidate.specifications()));
        entity.setAttributes(writeJson(candidate.attributes()));

        entity.setPrice(candidate.price());
        entity.setDiscountPrice(discountOrNull(candidate));
        entity.setCurrency(currency(candidate.currency()));
        entity.setUnit(trim(candidate.unit(), 40));
        entity.setMinimumOrderQuantity(candidate.minimumOrderQuantity());
        entity.setMinimumOrderUnit(trim(candidate.minimumOrderUnit(), 40));
        entity.setStockStatus(StockStatus.parse(candidate.availability()));

        entity.setSellerName(trim(candidate.sellerName(), 200));
        entity.setSellerLocation(trim(candidate.sellerLocation(), 200));
        entity.setSellerCity(trim(candidate.sellerCity(), 120));
        entity.setSellerState(trim(candidate.sellerState(), 120));

        entity.setRating(candidate.rating());
        entity.setReviewCount(candidate.reviewCount());
        entity.setImageUrls(writeJson(images(candidate.imageUrls())));
        entity.setRawPayload(writeJson(candidate.raw()));
        if (runId != null) {
            entity.setCollectorRunId(runId);
        }
    }

    /**
     * Keeps a discount only when it is actually one.
     *
     * <p>Sites routinely render the same number twice, or put the higher of the pair in the strike
     * -through slot. Storing that as a discount produces a catalogue where a filter on "discounted"
     * returns everything, so a discount that is not below the price is dropped rather than kept.</p>
     */
    private static BigDecimal discountOrNull(MaterialProductRequest candidate) {
        var discount = candidate.discountPrice();
        if (discount == null) {
            return null;
        }
        var price = candidate.price();
        if (price == null || discount.compareTo(price) >= 0) {
            return null;
        }
        return discount;
    }

    private static String currency(String value) {
        if (value == null || value.isBlank()) {
            return "INR";
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT).substring(0, Math.min(3, value.trim().length()));
    }

    private static List<String> images(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        var unique = new ArrayList<String>();
        var seen = new HashSet<String>();
        for (var url : urls) {
            if (url == null || url.isBlank() || url.length() > 1000) {
                continue;
            }
            var value = url.trim();
            if (seen.add(value)) {
                unique.add(value);
            }
            if (unique.size() >= MAX_IMAGES) {
                break;
            }
        }
        return unique.isEmpty() ? null : unique;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        try {
            var text = json.writeValueAsString(value);
            if (text.length() > MAX_JSON) {
                // Truncating JSON would store something that cannot be parsed back. Dropping the
                // field keeps the row usable and says so in the log.
                log.warn("dropping an oversized collected attribute block ({} chars)", text.length());
                return null;
            }
            return text;
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalArgumentException("Unserialisable product attributes: " + error.getOriginalMessage());
        }
    }

    private Map<String, String> readMap(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(text, STRING_MAP);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            log.warn("unreadable stored attribute block: {}", error.getOriginalMessage());
            return Map.of();
        }
    }

    private List<String> readList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(text, STRING_LIST);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            log.warn("unreadable stored image list: {}", error.getOriginalMessage());
            return List.of();
        }
    }

    MaterialProduct toProduct(MaterialProductEntity entity) {
        return new MaterialProduct(
                entity.getId(),
                entity.getName(),
                entity.getCategory(),
                entity.getCategory().label(),
                entity.getSubcategory(),
                entity.getSourceCategoryPath(),
                entity.getBrand(),
                entity.getManufacturer(),
                entity.getModelCode(),
                entity.getSku(),
                entity.getDescription(),
                entity.getSize(),
                entity.getMaterialComposition(),
                readMap(entity.getSpecifications()),
                readMap(entity.getAttributes()),
                entity.getPrice(),
                entity.getDiscountPrice(),
                entity.getCurrency(),
                entity.getUnit(),
                entity.getMinimumOrderQuantity(),
                entity.getMinimumOrderUnit(),
                entity.getStockStatus(),
                entity.getSellerName(),
                entity.getSellerLocation(),
                entity.getSellerCity(),
                entity.getSellerState(),
                entity.getRating(),
                entity.getReviewCount(),
                readList(entity.getImageUrls()),
                entity.getSourceSite(),
                entity.getProductUrl(),
                entity.getSourceProductId(),
                entity.getCollectorRunId(),
                entity.getReviewStatus(),
                entity.getFirstSeenAt(),
                entity.getLastSeenAt(),
                entity.getObservationCount());
    }

    private static String require(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("A collected product needs a " + field);
        }
        return value;
    }

    private static String trim(String value, int maximum) {
        if (value == null) {
            return null;
        }
        var collapsed = value.strip().replaceAll("\\s+", " ");
        if (collapsed.isEmpty()) {
            return null;
        }
        return collapsed.length() <= maximum ? collapsed : collapsed.substring(0, maximum);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

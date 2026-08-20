package com.avas.platform.catalog;

import java.util.List;

/**
 * What one ingest batch did, per product.
 *
 * <p>The counts are separated because they answer different operational questions: {@code created}
 * says whether the crawl is still finding new ground, {@code updated} says the site is being
 * re-read correctly, and {@code rejected} with its reasons is what a collector logs so a failing
 * adapter is visible without reading the server's logs.</p>
 */
public record CatalogIngestResponse(
        int created,
        int updated,
        int rejected,
        List<RejectedProduct> rejections
) {
    public record RejectedProduct(String fingerprint, String productUrl, String reason) {
    }
}

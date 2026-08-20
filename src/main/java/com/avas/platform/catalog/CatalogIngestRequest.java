package com.avas.platform.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A batch of collected products from one crawl.
 *
 * <p>Batched rather than one product per request because a crawl of a single category yields
 * hundreds of products, and the de-duplication that makes a re-crawl idempotent is far cheaper
 * when a whole batch's fingerprints can be resolved in one query.</p>
 *
 * <p>The cap is deliberate: an unbounded batch is an unbounded transaction, and a collector that
 * has to chunk its output is one whose failures are partial rather than total.</p>
 */
public record CatalogIngestRequest(
        @NotEmpty @Size(max = 500) @Valid List<MaterialProductRequest> products,
        @Size(max = 80) String collectorRunId
) {
}

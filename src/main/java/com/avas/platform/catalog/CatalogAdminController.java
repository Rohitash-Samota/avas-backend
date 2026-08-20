package com.avas.platform.catalog;

import com.avas.platform.auth.AvasPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Collector ingest and the administrator's review of what it collected.
 *
 * <p>Both sit behind {@code CATALOG_MANAGE}. The collector authenticates as an administrator for
 * the same reason it does on the pricing side: writing third-party data into AVAS is an
 * administrative act, and the rows it writes still wait for a human decision.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/catalog")
public class CatalogAdminController {
    private final CatalogService catalog;

    CatalogAdminController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @PostMapping("/products")
    ResponseEntity<CatalogIngestResponse> ingest(@AuthenticationPrincipal AvasPrincipal principal,
                                                 @Valid @RequestBody CatalogIngestRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(catalog.ingest(request, principal.tenantId()));
    }

    @GetMapping("/products")
    Page<MaterialProduct> products(@RequestParam(required = false) CatalogReviewStatus status,
                                   @RequestParam(required = false) String category,
                                   @RequestParam(required = false) String sourceSite,
                                   @RequestParam(required = false) String brand,
                                   @RequestParam(required = false, name = "q") String text,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        return catalog.search(status == null ? CatalogReviewStatus.PENDING : status,
                category, sourceSite, brand, text, page, size);
    }

    @PostMapping("/products/{id}/decision")
    MaterialProduct decide(@PathVariable UUID id, @Valid @RequestBody CatalogDecisionRequest request,
                           @AuthenticationPrincipal AvasPrincipal principal) {
        return catalog.decide(id, request, principal.userId());
    }

    @GetMapping("/statistics")
    Map<String, Long> statistics() {
        return catalog.statistics();
    }
}

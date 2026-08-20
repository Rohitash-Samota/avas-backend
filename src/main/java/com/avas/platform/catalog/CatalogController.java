package com.avas.platform.catalog;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read access to the construction-material catalogue.
 *
 * <p>Only {@link CatalogReviewStatus#APPROVED} products are reachable here. The pending queue is a
 * working area of unverified third-party data and belongs behind the admin controller, not on a
 * route the storefront can call.</p>
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    Page<MaterialProduct> products(@RequestParam(required = false) String category,
                                   @RequestParam(required = false) String sourceSite,
                                   @RequestParam(required = false) String brand,
                                   @RequestParam(required = false, name = "q") String text,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        return catalog.search(CatalogReviewStatus.APPROVED, category, sourceSite, brand, text, page, size);
    }

    @GetMapping("/products/{id}")
    MaterialProduct product(@PathVariable UUID id) {
        return catalog.product(id);
    }

    /** The taxonomy a client filters by, so a UI does not have to hard-code the enum. */
    @GetMapping("/categories")
    List<Map<String, String>> categories() {
        return Arrays.stream(ConstructionCategory.values())
                .map(category -> Map.of("code", category.name(), "label", category.label()))
                .toList();
    }
}

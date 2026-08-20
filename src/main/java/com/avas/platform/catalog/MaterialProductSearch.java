package com.avas.platform.catalog;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Locale;

/**
 * The catalogue's filter, built as criteria rather than as a JPQL string.
 *
 * <p>A single query with {@code :param is null or column = :param} for every filter reads well and
 * does not work: Hibernate cannot infer a type for a bare parameter compared against null, and the
 * repository fails to start rather than at the first query. Adding a predicate only when the caller
 * supplied a value avoids the question, and produces a narrower SQL statement per request as a
 * side effect.</p>
 */
final class MaterialProductSearch {

    private MaterialProductSearch() {
    }

    static Specification<MaterialProductEntity> of(CatalogReviewStatus status,
                                                   ConstructionCategory category,
                                                   String sourceSite, String brand, String text) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.isTrue(root.get("active")));
            if (status != null) {
                predicates.add(builder.equal(root.get("reviewStatus"), status));
            }
            if (category != null) {
                predicates.add(builder.equal(root.get("category"), category));
            }
            if (sourceSite != null) {
                predicates.add(builder.equal(builder.lower(root.get("sourceSite")),
                        sourceSite.toLowerCase(Locale.ROOT)));
            }
            if (brand != null) {
                predicates.add(builder.equal(builder.lower(root.get("brand")),
                        brand.toLowerCase(Locale.ROOT)));
            }
            if (text != null) {
                // Free text spans name and description because a buyer searching "53 grade" is as
                // likely to be quoting the description as the title.
                var pattern = "%" + text.toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(builder.coalesce(root.get("description"), "")), pattern)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}

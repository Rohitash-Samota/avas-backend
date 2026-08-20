package com.avas.platform.catalog;

/**
 * Whether a collected product has been accepted into the searchable catalogue.
 *
 * <p>Collected listings are Tier 3 market observation in the AVAS specification. A product row
 * carries no price authority on its own, but it is still third-party data of unverified accuracy,
 * so it lands {@link #PENDING} and an administrator decides whether it is published.</p>
 */
public enum CatalogReviewStatus {
    PENDING,
    APPROVED,
    REJECTED
}

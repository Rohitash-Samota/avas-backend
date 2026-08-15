package com.avas.platform.pricing;

/**
 * How a piece of price evidence entered AVAS.
 *
 * <p>The channel is recorded separately from the free-text {@code source} because it drives a
 * governance decision, not a display label: automated market observation carries less weight than a
 * human submission and is never allowed to outrank it when a rate is resolved.</p>
 *
 * <p>Ordering matters. {@link #trust()} reflects the source hierarchy in the AVAS specification,
 * where government schedules are Tier 1 and scraped marketplace listings are Tier 3 market
 * observation that "should not directly determine customer pricing".</p>
 */
public enum PriceSourceChannel {
    /** Government schedule of rates, PWD/BSR or other published official rate. */
    OFFICIAL(100),
    /** Entered or approved by an AVAS administrator. */
    ADMIN(80),
    /** Submitted by a verified builder against real work. */
    BUILDER(60),
    /** Submitted through the product by a signed-in user. */
    USER(50),
    /** Collected automatically from a public marketplace or search result. */
    SCRAPER(20);

    private final int trust;

    PriceSourceChannel(int trust) {
        this.trust = trust;
    }

    /** Higher wins when two approved observations compete for the same requirement. */
    public int trust() {
        return trust;
    }

    /** True when evidence from this channel needs a human decision before it can price anything. */
    public boolean requiresHumanApproval() {
        return this == SCRAPER;
    }

    public static PriceSourceChannel orDefault(PriceSourceChannel value) {
        return value == null ? USER : value;
    }
}

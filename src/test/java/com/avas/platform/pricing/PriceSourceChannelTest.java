package com.avas.platform.pricing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriceSourceChannelTest {

    @Test
    void officialAndAdminEvidenceOutranksAutomatedCollection() {
        assertThat(PriceSourceChannel.OFFICIAL.trust())
                .isGreaterThan(PriceSourceChannel.ADMIN.trust());
        assertThat(PriceSourceChannel.ADMIN.trust())
                .isGreaterThan(PriceSourceChannel.BUILDER.trust());
        assertThat(PriceSourceChannel.BUILDER.trust())
                .isGreaterThan(PriceSourceChannel.USER.trust());
        // The specification puts scraped marketplace listings at Tier 3 market observation, which
        // must never determine customer pricing on its own.
        assertThat(PriceSourceChannel.USER.trust())
                .isGreaterThan(PriceSourceChannel.SCRAPER.trust());
    }

    @Test
    void onlyCollectedEvidenceIsGatedBehindAHumanDecision() {
        assertThat(PriceSourceChannel.SCRAPER.requiresHumanApproval()).isTrue();
        for (var channel : PriceSourceChannel.values()) {
            if (channel != PriceSourceChannel.SCRAPER) {
                assertThat(channel.requiresHumanApproval())
                        .as("%s should not be gated as collector output", channel)
                        .isFalse();
            }
        }
    }

    @Test
    void unspecifiedChannelFallsBackToAnAccountableHumanSubmission() {
        assertThat(PriceSourceChannel.orDefault(null)).isEqualTo(PriceSourceChannel.USER);
        assertThat(PriceSourceChannel.orDefault(PriceSourceChannel.SCRAPER))
                .isEqualTo(PriceSourceChannel.SCRAPER);
    }
}

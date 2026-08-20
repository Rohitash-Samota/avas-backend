package com.avas.platform.project;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The household's own bedroom rule, which the brief, the parameter targets and the wizard all read.
 *
 * <p>Three copies of it had drifted apart: two of them gave every child a room of their own while
 * the third had children sharing, and all three floored the count at two. A couple was therefore
 * quoted a spare bedroom nobody asked for, and a family with children was promised more bedrooms in
 * the wizard than the drawing came back with.</p>
 */
class FamilyDetailsTest {
    @Test
    void twoPeopleNeedOneBedroom() {
        assertThat(new FamilyDetails(2, 0, 0, false).bedroomsNeeded()).isEqualTo(1);
        assertThat(new FamilyDetails(1, 0, 0, false).bedroomsNeeded()).isEqualTo(1);
        // Nobody recorded yet is still a home, so the count never falls below one.
        assertThat(new FamilyDetails(0, 0, 0, false).bedroomsNeeded()).isEqualTo(1);
    }

    @Test
    void childrenAndSeniorsShareTwoToARoomLikeTheAdultsDo() {
        assertThat(new FamilyDetails(2, 2, 0, false).bedroomsNeeded()).isEqualTo(2);
        assertThat(new FamilyDetails(2, 3, 0, false).bedroomsNeeded()).isEqualTo(3);
        assertThat(new FamilyDetails(2, 2, 1, false).bedroomsNeeded()).isEqualTo(3);
        assertThat(new FamilyDetails(2, 2, 2, false).bedroomsNeeded()).isEqualTo(3);
    }

    @Test
    void regularGuestsDoNotInflateThePermanentCount() {
        assertThat(new FamilyDetails(2, 2, 0, true).bedroomsNeeded())
                .isEqualTo(new FamilyDetails(2, 2, 0, false).bedroomsNeeded());
    }

    @Test
    void theCountStopsAtWhatTheGeometryEngineCanPlan() {
        assertThat(new FamilyDetails(10, 10, 10, false).bedroomsNeeded()).isEqualTo(6);
    }
}

package com.avas.platform.project;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The home a household is planned, when AVAS AI cannot be reached.
 *
 * <p>These rules and {@code avas_ai.programme} answer the same question the same way on purpose: a
 * customer whose recommendation was generated during an outage must not be planned a different house
 * from one generated a minute later. The assertions here are the ones that would catch the two
 * drifting apart.</p>
 */
class HouseholdProgrammeTest {
    @Test
    void aLuxuryDuplexGivesEachChildARoomRatherThanFillingTheSpaceWithAStudy() {
        var programme = plan(Category.LUXURY, new FamilyDetails(2, 2, 0, false));

        // One shared by the adults, one for each child, and the ground-floor guest room the tier
        // carries. The headcount rule alone planned two, and spent what was left on a study, a home
        // office and a multipurpose room.
        assertThat(programme.bedrooms()).isEqualTo(4);
        assertThat(programme.title()).isEqualTo("4-bedroom duplex");
        assertThat(programme.attachedBathrooms()).isEqualTo(3);
        assertThat(programme.commonBathrooms()).isEqualTo(1);
        assertThat(programme.familyLounge()).isTrue();
        assertThat(programme.provider()).isEqualTo("DETERMINISTIC");
        assertThat(programme.modelPlanned()).isFalse();
    }

    @Test
    void childrenStillShareBelowThePremiumTier() {
        assertThat(plan(Category.STANDARD, new FamilyDetails(2, 2, 0, false)).bedrooms()).isEqualTo(2);
        assertThat(plan(Category.PREMIUM, new FamilyDetails(2, 2, 0, false)).bedrooms()).isEqualTo(3);
    }

    @Test
    void theSameHouseholdIsPlannedFewerBedroomsOnGroundThatCannotCarryThem() {
        var small = plan(Category.LUXURY, new FamilyDetails(2, 2, 0, false), 22, 40);

        // Never below what the household needs, however little ground there is.
        assertThat(small.bedrooms()).isEqualTo(new FamilyDetails(2, 2, 0, false).bedroomsNeeded());
        assertThat(small.reasons()).anyMatch(reason -> reason.contains("the plot carries"));
    }

    @Test
    void regularGuestsAreServedByAFlexRoomRatherThanAnExtraBedroom() {
        var withGuests = plan(Category.LUXURY, new FamilyDetails(2, 2, 0, true));

        assertThat(withGuests.bedrooms())
                .isEqualTo(plan(Category.LUXURY, new FamilyDetails(2, 2, 0, false)).bedrooms());
        assertThat(withGuests.reasons()).anyMatch(reason -> reason.contains("flex room is planned"));
    }

    @Test
    void aSeniorEarnsAGroundFloorBedroomAndIsSaidBackToTheCustomer() {
        var programme = plan(Category.PREMIUM, new FamilyDetails(2, 0, 2, false));

        assertThat(programme.seniorBedroom()).isTrue();
        assertThat(programme.reasons()).anyMatch(reason -> reason.contains("ground-floor bedroom"));
    }

    @Test
    void aStatedPreferenceForMoreBedroomsIsHonoured() {
        var asked = plan(Category.LUXURY, new FamilyDetails(2, 2, 0, false),
                List.of("More bedrooms please"));

        assertThat(asked.bedrooms()).isEqualTo(5);
    }

    @Test
    void everyProgrammeIsOneTheLayoutEngineCouldPlace() {
        for (var category : List.of(Category.STANDARD, Category.PREMIUM, Category.LUXURY)) {
            for (var children = 0; children <= 4; children++) {
                var programme = plan(category, new FamilyDetails(2, children, 0, false));

                assertThat(programme.bedrooms()).isBetween(1, HouseholdProgramme.MAXIMUM_BEDROOMS);
                assertThat(programme.attachedBathrooms()).isLessThanOrEqualTo(programme.bedrooms());
                assertThat(programme.attachedBathrooms() + programme.commonBathrooms())
                        .isGreaterThanOrEqualTo(1);
            }
        }
    }

    @Test
    void aFallbackSaysSoRatherThanPassingItselfOffAsAPlannedHome() {
        var programme = HouseholdProgramme.deterministic(brief(Category.LUXURY,
                new FamilyDetails(2, 2, 0, false), 40, 60, List.of()), null,
                "AVAS AI was unavailable");

        assertThat(programme.fallbackUsed()).isTrue();
        assertThat(programme.modelPlanned()).isFalse();
        assertThat(programme.warnings()).containsExactly("AVAS AI was unavailable");
    }

    // -------------------------------------------------------------------------------------------

    private HouseholdProgramme plan(Category category, FamilyDetails family) {
        return plan(category, family, 40, 60);
    }

    private HouseholdProgramme plan(Category category, FamilyDetails family, List<String> preferences) {
        var details = brief(category, family, 40, 60, preferences);
        return HouseholdProgramme.deterministic(details, envelope(details), null);
    }

    private HouseholdProgramme plan(Category category, FamilyDetails family, double width, double length) {
        var details = brief(category, family, width, length, List.of());
        return HouseholdProgramme.deterministic(details, envelope(details), null);
    }

    private BuildableEnvelope envelope(BasicDetailsRequest details) {
        var boundary = details.boundary();
        return BuildableEnvelope.derive(boundary,
                SetbackRule.forUsage(boundary, details.floors(), details.parameters().plotUsage()),
                details.floors(), details.roadFacing(), details.parameters().parkingCars());
    }

    private BasicDetailsRequest brief(Category category, FamilyDetails family, double width,
            double length, List<String> preferences) {
        var brief = new BasicDetailsRequest(width, length, Facing.NORTH, "Jaipur", 2,
                category == Category.LUXURY ? 14_000_000 : 7_000_000, category, family, preferences);
        var inferred = brief.parameters();
        return new BasicDetailsRequest(brief.plotWidth(), brief.plotLength(), brief.roadFacing(),
                brief.city(), brief.floors(), brief.budget(), brief.category(), brief.family(),
                brief.preferences(), new HomeParameters(inferred.homeType(),
                        inferred.staircaseType(), inferred.liftProvision(), inferred.balconyCount(),
                        inferred.terraceRequired(), inferred.courtyardRequired(),
                        inferred.accessibleGroundFloor(), inferred.parkingCars(),
                        inferred.solarReady(), inferred.rainwaterHarvesting(),
                        HomeParameters.STANDARD_SETBACK));
    }
}

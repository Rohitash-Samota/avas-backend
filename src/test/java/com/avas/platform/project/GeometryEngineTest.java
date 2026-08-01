package com.avas.platform.project;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.avas.platform.project.ProjectModels.*;
import static org.assertj.core.api.Assertions.assertThat;

class GeometryEngineTest {
    private final GeometryEngine engine = new GeometryEngine();

    @Test
    void generatesThreeVersionedCandidatesWithoutGeometryViolations() {
        var candidates = engine.generate("project-1", 3, details(40), recommendation(), versions());

        assertThat(candidates).hasSize(3);
        assertThat(candidates).extracting(DrawingCandidate::id)
                .containsExactly("drawing-project-1-v3-1", "drawing-project-1-v3-2", "drawing-project-1-v3-3");
        assertThat(candidates).extracting(DrawingCandidate::strategy)
                .containsExactly("BUDGET_OPTIMIZED", "BALANCED", "LIFESTYLE_OPTIMIZED");
        assertThat(candidates).allSatisfy(candidate -> {
            assertThat(candidate.hardViolations()).isEmpty();
            assertThat(engine.validate(40, 60, candidate.geometry().rooms())).isEmpty();
        });
    }

    @Test
    void routesNarrowPlotsToExpertReview() {
        var candidates = engine.generate("narrow", 1, details(18), recommendation(), versions());
        assertThat(candidates).allSatisfy(candidate -> assertThat(candidate.status()).isEqualTo("EXPERT_REVIEW"));
    }

    private BasicDetailsRequest details(double width) {
        return new BasicDetailsRequest(width, 60, Facing.NORTH, "Jaipur", 2, 7_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 1, true), List.of("Natural light"));
    }

    private Recommendation recommendation() {
        return new Recommendation("rec-1", "Four-bedroom home", "PREMIUM", 4, 3, 1, 1,
                2400, 2800, 6_300_000, 7_300_000, true, true, true, 92,
                List.of("Family brief"), Map.of("rule", "test"), true);
    }

    private Map<String, String> versions() {
        return Map.of("ruleVersion", "test-rules", "strategyVersion", "test-strategy");
    }
}

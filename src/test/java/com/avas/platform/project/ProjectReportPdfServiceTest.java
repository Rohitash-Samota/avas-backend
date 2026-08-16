package com.avas.platform.project;

import com.avas.platform.pricing.ConfidenceLevel;
import com.avas.platform.pricing.CostRateEvidence;
import com.avas.platform.pricing.CostRateProvider;
import com.avas.platform.pricing.CostRateSnapshot;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectReportPdfServiceTest {
    private ProjectService projects;
    private ProjectComparisonService comparisons;
    private ProjectReportPdfService reports;

    @BeforeEach
    void setUp() {
        projects = new ProjectService(new GeometryEngine(), "RJ-JDA-2026.08", "AVAS-KB-2026.08",
                "layout-parameters-2.0.0", "governed-takeoff-2.0.0",
                new CostingService(new QuantityTakeoffService(), demoApprovedRates()));
        comparisons = new ProjectComparisonService(projects);
        reports = new ProjectReportPdfService(new FloorPlanPdfService(),
                new RecommendationPdfPageRenderer(), new ComparisonPdfPageRenderer(),
                new CostBreakdownPdfPageRenderer());
    }

    @Test
    void combinesSideBySideOptionsAuthoritativeFloorPagesAndDetailedCostEvidence() throws Exception {
        var project = preparedProject();
        var selected = projects.approveConcept(projects.drawings(project.id()).get(1).id(), "INDIVIDUAL");
        var comparison = comparisons.comparison(project.id());
        var option = comparison.options().stream()
                .filter(value -> value.drawingId().equals(selected.id()))
                .findFirst().orElseThrow();
        var preliminaryFloorRange = lakhRange(selected.estimatedCostLow(), selected.estimatedCostHigh());
        var governedFloorRange = lakhRange(option.costLow(), option.costHigh());
        var masterBedroom = selected.geometry().rooms().stream()
                .filter(room -> "MASTER_BEDROOM".equals(room.type())).findFirst().orElseThrow();
        var masterDimension = String.format(Locale.ROOT, "%.1f x %.1f ft",
                masterBedroom.width(), masterBedroom.length());

        assertThat(governedFloorRange).isNotEqualTo(preliminaryFloorRange);

        var bytes = reports.generate(projects.get(project.id()), selected, comparison);
        var sampleOutput = System.getProperty("avas.sample.report");
        if (sampleOutput != null && !sampleOutput.isBlank()) {
            Files.write(Path.of(sampleOutput), bytes);
        }

        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(bytes.length).isGreaterThan(10_000);
        try (var document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(9);
            for (var page : document.getPages()) {
                assertThat(page.getResources().getXObjectNames()).isEmpty();
            }
            var text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("PERSONALISED DESIGN RECOMMENDATION")
                    .contains("FAMILY & AREA BASIS")
                    .contains("5 people")
                    .contains("3 bedrooms")
                    .contains("2 attached + 1 common bath")
                    .contains("LIFT + ACCESSIBILITY")
                    .contains("AVAS: FUTURE SHAFT MINIMUM")
                    .contains("Selected: Passenger | Drawing: 1 lift core")
                    .contains("BALCONY + OUTDOOR")
                    .contains("AVAS: 1 BALCONY")
                    .contains("Selected: 2 | Drawing: 2")
                    .contains("Area/household rule: 1 base for a multi-floor home")
                    .contains("ACTUAL ROOM SCHEDULE")
                    .contains("PERSISTED VECTOR GEOMETRY - DIMENSIONS AND PLANNED CONTENTS")
                    .contains("Master Bedroom")
                    .contains(masterDimension)
                    .contains("Double/queen bed, wardrobe wall and side tables")
                    .contains("WC, basin, shower")
                    .contains("OPTION COMPARISON")
                    .contains("BEST AVAILABLE OPTION")
                    .contains("EFFICIENT COURTYARD")
                    .contains("Garden Threshold")
                    .contains("LIGHTWELL HOUSE")
                    .contains("AUTHORITATIVE FLOOR PLAN MAP")
                    .contains("GROUND FLOOR PLAN")
                    .contains("FIRST FLOOR PLAN")
                    .contains("LIFT")
                    .contains("Courtyard")
                    .contains(governedFloorRange)
                    .contains("COST & MATERIALS")
                    .contains("MATERIALS, BRANDS & RATES")
                    .contains("PRICE EVIDENCE REGISTER")
                    .contains("UltraTech")
                    .contains("Tata Tiscon")
                    .contains("Kajaria")
                    .contains("DEMO-EVIDENCE-CEMENT-001")
                    .contains("Parameters: DETERMINISTIC")
                    .contains("Prompt/schema: home-parameters-1.1.0 / home-parameters-1")
                    .contains("Planning estimate, not a supplier quotation")
                    .contains("Brand not recorded")
                    .doesNotContain(preliminaryFloorRange)
                    .doesNotContain("Image Not Available");
            assertThat(text.indexOf("PERSONALISED DESIGN RECOMMENDATION"))
                    .isLessThan(text.indexOf("OPTION COMPARISON"));
            assertThat(text.indexOf("ACTUAL ROOM SCHEDULE"))
                    .isLessThan(text.indexOf("OPTION COMPARISON"));
            assertThat(text.replaceAll("\\s+", "")).contains("Balcony").contains("Terrace");
        }
    }

    private String lakhRange(long low, long high) {
        return String.format(Locale.ROOT, "INR %.1fL - %.1fL", low / 100_000.0, high / 100_000.0);
    }

    private CostRateProvider demoApprovedRates() {
        return query -> {
            var evidence = new ArrayList<CostRateEvidence>();
            for (var requirement : query.requirements()) {
                var product = switch (requirement.code()) {
                    case "CEMENT" -> new String[]{"DEMO-EVIDENCE-CEMENT-001", "UltraTech", "OPC-53",
                            "53 grade OPC, 50 kg bag", "415.00"};
                    case "REINFORCEMENT_STEEL" -> new String[]{"DEMO-EVIDENCE-STEEL-001", "Tata Tiscon",
                            "TMT-550SD", "Fe 550SD reinforcement steel", "72.00"};
                    case "FLOORING" -> new String[]{"DEMO-EVIDENCE-FLOOR-001", "Kajaria", "GVT-600X1200",
                            "Glazed vitrified tile allowance", "195.00"};
                    default -> null;
                };
                if (product == null) continue;
                evidence.add(new CostRateEvidence(requirement.code(), product[0], requirement.category(),
                        requirement.itemType(), requirement.category(), query.qualityTier(),
                        CostRateProvider.canonicalCity(query.city()), "Rajasthan", requirement.unit(),
                        new BigDecimal(product[4]), product[2], product[1], product[3],
                        "Demo admin-approved Jaipur supplier", "DEMO_ADMIN_APPROVED_RATE",
                        query.asOf().minusDays(7), query.asOf().minusDays(7), query.asOf().plusDays(90),
                        new BigDecimal("18.00"), true, false, true, 1, ConfidenceLevel.HIGH));
            }
            return new CostRateSnapshot("INR", CostRateProvider.canonicalCity(query.city()),
                    query.qualityTier(), query.asOf(), new BigDecimal("2600.00"),
                    "AVAS_ADMIN_BASE_PRICE", null, 0, new BigDecimal("7.50"), 30, 2, evidence);
        };
    }

    private ProjectSummary preparedProject() {
        var project = projects.create(new CreateProjectRequest("Demo family duplex", StartMode.PLOT), "INDIVIDUAL");
        projects.updateBasicDetails(project.id(), new BasicDetailsRequest(40, 60, Facing.NORTH,
                "Jaipur, Rajasthan", 2, 10_000_000, Category.PREMIUM,
                new FamilyDetails(2, 2, 1, true), List.of("Natural light", "Garden connection",
                        "Roof terrace", "Solar ready", "Rainwater harvesting"),
                new HomeParameters("DUPLEX", "DOG_LEGGED", "PASSENGER", 2,
                        true, true, true, 1, true, true)), "INDIVIDUAL");
        var recommendation = projects.generateRecommendation(project.id(), "INDIVIDUAL");
        projects.acceptRecommendation(project.id(), recommendation.id(), "INDIVIDUAL");
        projects.generateDrawings(project.id(), "INDIVIDUAL");
        return projects.get(project.id());
    }
}

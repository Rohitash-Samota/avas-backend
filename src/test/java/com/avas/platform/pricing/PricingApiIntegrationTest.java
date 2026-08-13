package com.avas.platform.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pricing;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.mongodb.repositories.type=none",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration,org.springframework.boot.actuate.autoconfigure.data.mongo.MongoHealthContributorAutoConfiguration",
        "management.health.mongo.enabled=false",
        "avas.mongo.enabled=false",
        "avas.identity.store=sql",
        "avas.bootstrap-admin.enabled=true",
        "avas.bootstrap-admin.username=platform.admin",
        "avas.bootstrap-admin.email=platform-admin@avas.test",
        "avas.bootstrap-admin.password=StrongAdmin@2026",
        "avas.bootstrap-admin.tenant-id=tenant-test"
})
@AutoConfigureMockMvc
class PricingApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CostRateProvider rates;

    @Test
    void governsPriceEvidenceAndProducesAnExplainableBudgetWithConsentedFeedback() throws Exception {
        var builder = register("BUILDER");
        var submissionResult = mvc.perform(post("/api/v1/pricing/submissions")
                        .header("Authorization", bearer(builder))
                        .header("X-Active-Role", "BUILDER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "itemName", "Jaipur standard turnkey construction",
                                "category", "COST_PER_SQFT", "qualityTier", "STANDARD",
                                "city", "Jaipur", "unit", "SQ_FT", "unitPrice", 2100,
                                "observedOn", LocalDate.now().toString(), "source", "QUOTATION"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.itemType").value("COST_PER_SQFT"))
                .andReturn();
        var submissionId = json.readTree(submissionResult.getResponse().getContentAsString()).path("id").asText();

        var admin = login("platform.admin", "StrongAdmin@2026");
        mvc.perform(put("/api/v1/admin/pricing/submissions/{id}/decision", submissionId)
                        .header("Authorization", bearer(admin)).header("X-Active-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"note\":\"Recent supplier evidence verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(put("/api/v1/admin/pricing/submissions/{id}/decision", submissionId)
                        .header("Authorization", bearer(admin)).header("X-Active-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isConflict());

        var customer = register("INDIVIDUAL");
        var recommendationResult = mvc.perform(post("/api/v1/pricing/budget-recommendations")
                        .header("Authorization", bearer(customer)).header("X-Active-Role", "INDIVIDUAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"Jaipur\",\"builtUpAreaSqFt\":1800,\"category\":\"STANDARD\",\"totalBudget\":4200000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceUnitPrice").value(2100.0))
                .andExpect(jsonPath("$.sampleCount").value(1))
                .andExpect(jsonPath("$.confidenceLevel").value("LOW"))
                .andExpect(jsonPath("$.priceSource").value("APPROVED_LOCAL_EVIDENCE"))
                .andReturn();
        var recommendationId = json.readTree(recommendationResult.getResponse().getContentAsString()).path("id").asText();

        mvc.perform(post("/api/v1/pricing/budget-recommendations/{id}/feedback", recommendationId)
                        .header("Authorization", bearer(customer)).header("X-Active-Role", "INDIVIDUAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true,\"actualBudget\":4250000,\"note\":\"Selected after review\",\"consentToLearning\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.consentToLearning").value(true));
    }

    @Test
    void versionsConfigurationAndRequiresValidationBeforeModelActivation() throws Exception {
        var admin = login("platform.admin", "StrongAdmin@2026");
        mvc.perform(get("/api/v1/admin/configuration")
                        .header("Authorization", bearer(admin)).header("X-Active-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("GLOBAL"))
                .andExpect(jsonPath("$.defaultCurrency").value("INR"))
                .andExpect(jsonPath("$.learningEnabled").value(true));

        mvc.perform(put("/api/v1/admin/configuration")
                        .header("Authorization", bearer(admin)).header("X-Active-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.ofEntries(
                                Map.entry("defaultCurrency", "INR"), Map.entry("defaultCity", "Jaipur"),
                                Map.entry("economyCostPerSqFt", 1500), Map.entry("standardCostPerSqFt", 2000),
                                Map.entry("premiumCostPerSqFt", 2800), Map.entry("luxuryCostPerSqFt", 3900),
                                Map.entry("defaultContingencyPercent", 8), Map.entry("minimumVerifiedSamples", 3),
                                Map.entry("priceFreshnessDays", 120), Map.entry("confidenceThreshold", 75),
                                Map.entry("recommendationValidityDays", 21), Map.entry("contributionsEnabled", true),
                                Map.entry("aiExplanationEnabled", true), Map.entry("learningEnabled", true)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.standardCostPerSqFt").value(2000.0));

        var modelResult = mvc.perform(post("/api/v1/admin/models")
                        .header("Authorization", bearer(admin)).header("X-Active-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Budget explainer\",\"modelType\":\"RECOMMENDATION\",\"provider\":\"GROQ\",\"modelName\":\"llama-3.1-8b-instant\",\"version\":\"2026.08.1\",\"validationScore\":0.86,\"description\":\"Offline candidate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        var modelId = json.readTree(modelResult.getResponse().getContentAsString()).path("id").asText();

        mvc.perform(put("/api/v1/admin/models/{id}/activate", modelId)
                        .header("Authorization", bearer(admin)).header("X-Active-Role", "ADMIN"))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/v1/admin/models/{id}/validate", modelId)
                        .header("Authorization", bearer(admin)).header("X-Active-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validationScore\":0.86,\"note\":\"Regression and safety checks passed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALIDATED"));

        mvc.perform(put("/api/v1/admin/models/{id}/activate", modelId)
                        .header("Authorization", bearer(admin)).header("X-Active-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void exposesApprovedBrandedMaterialAsAStablePublicCostRateSnapshot() throws Exception {
        var builder = register("BUILDER");
        var result = mvc.perform(post("/api/v1/pricing/submissions")
                        .header("Authorization", bearer(builder)).header("X-Active-Role", "BUILDER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.ofEntries(
                                Map.entry("itemName", "Premium OPC cement"),
                                Map.entry("itemType", "MATERIAL"), Map.entry("category", "CEMENT"),
                                Map.entry("qualityTier", "PREMIUM"),
                                Map.entry("city", "Jaipur, Rajasthan"), Map.entry("unit", "BAG"),
                                Map.entry("unitPrice", 415), Map.entry("observedOn", LocalDate.now().toString()),
                                Map.entry("supplierName", "Approved Jaipur Supplier"),
                                Map.entry("source", "AUTHORIZED_DEALER_QUOTE"),
                                Map.entry("brandName", "UltraTech"), Map.entry("productCode", "OPC-53"),
                                Map.entry("specification", "53 grade OPC, 50 kg bag"),
                                Map.entry("materialIncluded", true)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brandName").value("UltraTech"))
                .andExpect(jsonPath("$.productCode").value("OPC-53"))
                .andExpect(jsonPath("$.specification").value("53 grade OPC, 50 kg bag"))
                .andReturn();
        var publicId = json.readTree(result.getResponse().getContentAsString()).path("id").asText();

        var admin = login("platform.admin", "StrongAdmin@2026");
        mvc.perform(put("/api/v1/admin/pricing/submissions/{id}/decision", publicId)
                        .header("Authorization", bearer(admin)).header("X-Active-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"note\":\"Brand and product verified\"}"))
                .andExpect(status().isOk());

        var snapshot = rates.resolve(new CostRateQuery("tenant-test", "Jaipur", PriceCategory.PREMIUM,
                LocalDate.now(), List.of(new CostRateRequirement("CEMENT", PriceItemType.MATERIAL,
                        "CEMENT", "BAG", new BigDecimal("100.000")))));

        assertThat(snapshot.city()).isEqualTo("Jaipur");
        assertThat(snapshot.evidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.evidenceId()).isEqualTo(publicId);
            assertThat(evidence.brandName()).isEqualTo("UltraTech");
            assertThat(evidence.productCode()).isEqualTo("OPC-53");
            assertThat(evidence.specification()).isEqualTo("53 grade OPC, 50 kg bag");
            assertThat(evidence.unitPrice()).isEqualByComparingTo("415.00");
        });
    }

    private JsonNode register(String accountType) throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "firstName", "Pricing", "lastName", "User", "username", "pricing." + suffix,
                                "email", "pricing-" + suffix + "@avas.test", "password", "StrongUser@2026",
                                "accountType", accountType))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode login(String identifier, String password) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("identifier", identifier, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(JsonNode login) { return "Bearer " + login.path("accessToken").asText(); }
}

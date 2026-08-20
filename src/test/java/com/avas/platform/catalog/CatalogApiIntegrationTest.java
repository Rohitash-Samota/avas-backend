package com.avas.platform.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.mongodb.repositories.type=none",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration,org.springframework.boot.actuate.autoconfigure.data.mongo.MongoHealthContributorAutoConfiguration",
        "management.health.mongo.enabled=false",
        "avas.mongo.enabled=false",
        "avas.identity.store=sql",
        "avas.bootstrap-admin.enabled=true",
        "avas.bootstrap-admin.username=catalog.admin",
        "avas.bootstrap-admin.email=catalog-admin@avas.test",
        "avas.bootstrap-admin.password=StrongAdmin@2026",
        "avas.bootstrap-admin.tenant-id=tenant-test"
})
@AutoConfigureMockMvc
class CatalogApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void collectedProductsAreIngestedDeduplicatedAndPublishedOnlyAfterAHumanDecision() throws Exception {
        var admin = login("catalog.admin", "StrongAdmin@2026");

        // A crawl posts a batch. The same product arriving twice inside one batch - reached from
        // two category pages - is one row, not two.
        mvc.perform(post("/api/v1/admin/catalog/products")
                        .header("Authorization", bearer(admin)).header("X-Active-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "collectorRunId", "catalog-run-1",
                                "products", List.of(cement("395.00"), cement("395.00"), tmtBar())))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.rejected").value(0));

        // Nothing is published yet: collected data is Tier 3 market observation until a human
        // accepts it.
        mvc.perform(get("/api/v1/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        var pending = mvc.perform(get("/api/v1/admin/catalog/products")
                        .header("Authorization", bearer(admin)).header("X-Active-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();
        var products = json.readTree(pending.getResponse().getContentAsString()).path("content");
        var cementId = findByName(products, "UltraTech OPC 53 Cement");

        // Re-running the crawl updates the row rather than adding a second copy of everything.
        mvc.perform(post("/api/v1/admin/catalog/products")
                        .header("Authorization", bearer(admin)).header("X-Active-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "collectorRunId", "catalog-run-2",
                                "products", List.of(cement("410.00"))))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(1));

        mvc.perform(post("/api/v1/admin/catalog/products/{id}/decision", cementId)
                        .header("Authorization", bearer(admin)).header("X-Active-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"note\":\"Listing verified against the supplier\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"));

        // Approved, and only now visible - with the second run's price and the fields the listing
        // published, including the JSON blocks read back out.
        mvc.perform(get("/api/v1/catalog/products").param("category", "CEMENT_CONCRETE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("UltraTech OPC 53 Cement"))
                .andExpect(jsonPath("$.content[0].price").value(410.0000))
                .andExpect(jsonPath("$.content[0].brand").value("UltraTech"))
                .andExpect(jsonPath("$.content[0].specifications.Grade").value("53"))
                .andExpect(jsonPath("$.content[0].imageUrls[0]").value("https://store.test/img/cement.jpg"))
                .andExpect(jsonPath("$.content[0].stockStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$.content[0].observationCount").value(2));

        // The steel product was never approved, so a category search does not return it.
        mvc.perform(get("/api/v1/catalog/products").param("category", "STEEL_METAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // A category the caller invented is a typo, not "everything".
        mvc.perform(get("/api/v1/catalog/products").param("category", "CEMNT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCategoryTheCollectorDidNotSendIsDerivedFromTheSitesOwnWording() throws Exception {
        var admin = login("catalog.admin", "StrongAdmin@2026");
        var product = Map.of(
                "fingerprint", "f".repeat(60) + "0002",
                "sourceSite", "teststore",
                "productUrl", "https://store.test/p/derived",
                "name", "Marine Plywood 19mm BWP",
                "sourceCategoryPath", "Building Material > Plywood & Boards");

        mvc.perform(post("/api/v1/admin/catalog/products")
                        .header("Authorization", bearer(admin)).header("X-Active-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("products", List.of(product)))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.created").value(1));

        mvc.perform(get("/api/v1/admin/catalog/products").param("q", "Marine Plywood")
                        .header("Authorization", bearer(admin)).header("X-Active-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].category").value("WOOD_PANELS"))
                .andExpect(jsonPath("$.content[0].stockStatus").value("UNKNOWN"));
    }

    @Test
    void ingestIsClosedToCallersWithoutCatalogueAuthority() throws Exception {
        var individual = register("INDIVIDUAL");
        mvc.perform(post("/api/v1/admin/catalog/products")
                        .header("Authorization", bearer(individual)).header("X-Active-Role", "INDIVIDUAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("products", List.of(cement("395.00"))))))
                .andExpect(status().isForbidden());

        // Reading the approved catalogue needs no account at all, like the commerce catalogue.
        mvc.perform(get("/api/v1/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CEMENT_CONCRETE"));
    }

    private static Map<String, Object> cement(String price) {
        return Map.ofEntries(
                Map.entry("fingerprint", "a".repeat(64)),
                Map.entry("sourceSite", "teststore"),
                Map.entry("productUrl", "https://store.test/p/opc-53"),
                Map.entry("name", "UltraTech OPC 53 Cement"),
                Map.entry("category", "CEMENT_CONCRETE"),
                Map.entry("subcategory", "OPC cement"),
                Map.entry("sourceCategoryPath", "Building Material > Cement"),
                Map.entry("brand", "UltraTech"),
                Map.entry("sku", "UT-OPC53-50"),
                Map.entry("price", price),
                Map.entry("currency", "INR"),
                Map.entry("unit", "Bag"),
                Map.entry("availability", "https://schema.org/InStock"),
                Map.entry("specifications", Map.of("Grade", "53", "Weight", "50 kg")),
                Map.entry("imageUrls", List.of("https://store.test/img/cement.jpg")),
                Map.entry("raw", Map.of("jsonld", Map.of("name", "UltraTech OPC 53 Cement"))));
    }

    private static Map<String, Object> tmtBar() {
        return Map.of(
                "fingerprint", "b".repeat(64),
                "sourceSite", "teststore",
                "productUrl", "https://store.test/p/tmt-12",
                "name", "TMT Bar Fe500D 12mm",
                "category", "STEEL_METAL",
                "price", "68.50",
                "unit", "Kg");
    }

    private String findByName(JsonNode products, String name) {
        for (var product : products) {
            if (name.equals(product.path("name").asText())) {
                return product.path("id").asText();
            }
        }
        throw new AssertionError("No collected product named " + name);
    }

    private JsonNode register(String accountType) throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "firstName", "Catalog", "lastName", "User", "username", "catalog." + suffix,
                                "email", "catalog-" + suffix + "@avas.test", "password", "StrongUser@2026",
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

package com.avas.platform.migration;

import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Indexes.ascending;
import static com.mongodb.client.model.Indexes.compoundIndex;

/** Creates and versions the flexible document collections specified for MongoDB. */
@Component
@ConditionalOnProperty(name = "avas.mongo.enabled", havingValue = "true", matchIfMissing = true)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class MongoDomainSchemaMigration implements ApplicationRunner {
    private static final String LEDGER = "domain_schema_migrations";
    private static final String VERSION = "V001__specification_document_collections";
    private static final List<String> PROJECT_COLLECTIONS = List.of(
            "project_context_snapshots",
            "requirement_interpretation_snapshots",
            "ai_conversations",
            "ai_generation_requests",
            "ai_generation_responses",
            "geometry_documents",
            "floor_plan_documents",
            "drawing_validation_reports",
            "drawing_analyses",
            "estimate_snapshots",
            "daily_site_reports",
            "inspection_reports",
            "recommendation_explanations",
            "customer_feedback",
            "generation_diagnostics",
            "space_programmes",
            "candidate_layouts",
            "project_learning_records");
    private static final List<String> TENANT_COLLECTIONS = List.of("model_output_logs");

    private final MongoTemplate mongo;

    MongoDomainSchemaMigration(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        var ledger = mongo.getCollection(LEDGER);
        if (ledger.countDocuments(eq("_id", VERSION)) > 0) return;
        PROJECT_COLLECTIONS.forEach(name -> ensureCollection(name, true));
        TENANT_COLLECTIONS.forEach(name -> ensureCollection(name, false));
        ledger.insertOne(new Document("_id", VERSION)
                .append("version", "001")
                .append("description", "Create specification-owned versioned domain and intelligence collections")
                .append("specificationVersion", "AVAS-SPEC-1.0-2026-08-01")
                .append("database", mongo.getDb().getName())
                .append("executedAt", Date.from(Instant.now())));
    }

    private void ensureCollection(String name, boolean projectScoped) {
        if (!mongo.collectionExists(name)) {
            mongo.getDb().createCollection(name, new CreateCollectionOptions()
                    .validationOptions(new ValidationOptions()
                            .validator(documentSchema(projectScoped))
                            .validationLevel(ValidationLevel.MODERATE)
                            .validationAction(ValidationAction.ERROR)));
        }
        var collection = mongo.getCollection(name);
        collection.createIndex(ascending("tenantId"), new IndexOptions().name("idx_tenant"));
        collection.createIndex(ascending("createdAt"), new IndexOptions().name("idx_created_at"));
        collection.createIndex(ascending("schemaVersion"), new IndexOptions().name("idx_schema_version"));
        if (projectScoped) {
            collection.createIndex(compoundIndex(ascending("tenantId"), ascending("projectId"), ascending("createdAt")),
                    new IndexOptions().name("idx_tenant_project_created"));
        }
    }

    private Document documentSchema(boolean projectScoped) {
        var required = projectScoped
                ? List.of("schemaVersion", "tenantId", "projectId", "createdAt")
                : List.of("schemaVersion", "tenantId", "createdAt");
        var properties = new Document("schemaVersion", new Document("bsonType", "string"))
                .append("tenantId", new Document("bsonType", "string"))
                .append("createdAt", new Document("bsonType", "date"));
        if (projectScoped) properties.append("projectId", new Document("bsonType", "string"));
        return new Document("$jsonSchema", new Document("bsonType", "object")
                .append("required", required)
                .append("properties", properties));
    }
}

package com.avas.platform.knowledge;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Document(collection = "knowledge_sources")
class KnowledgeSourceDocument {
    @Id private String id;
    @Indexed(unique = true) private String sourceKey;
    private String title;
    private String kind;
    private String jurisdiction;
    private String version;
    private String status;
    private String contentHash;
    private Map<String, String> metadata = new LinkedHashMap<>();
    private Instant createdAt;
    private Instant updatedAt;

    protected KnowledgeSourceDocument() {}

    KnowledgeSourceDocument(String sourceKey, String title, String kind, String jurisdiction, String version,
            String contentHash, Map<String, String> metadata) {
        this.id = UUID.randomUUID().toString();
        this.sourceKey = sourceKey;
        this.createdAt = Instant.now();
        update(title, kind, jurisdiction, version, contentHash, metadata);
    }

    void update(String title, String kind, String jurisdiction, String version, String contentHash,
            Map<String, String> metadata) {
        this.title = title.trim();
        this.kind = kind.trim().toUpperCase();
        this.jurisdiction = jurisdiction.trim();
        this.version = version.trim();
        this.status = "ACTIVE";
        this.contentHash = contentHash.trim();
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        this.updatedAt = Instant.now();
    }

    String id() { return id; }
    String sourceKey() { return sourceKey; }
    String title() { return title; }
    String kind() { return kind; }
    String jurisdiction() { return jurisdiction; }
    String version() { return version; }
    String status() { return status; }
    String contentHash() { return contentHash; }
    Map<String, String> metadata() { return Map.copyOf(metadata); }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
}

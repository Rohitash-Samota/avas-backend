package com.avas.platform.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public final class KnowledgeModels {
    private KnowledgeModels() {}

    public record KnowledgeSourceRequest(
            @NotBlank @Size(max = 120) String sourceKey,
            @NotBlank @Size(max = 180) String title,
            @NotBlank @Size(max = 80) String kind,
            @NotBlank @Size(max = 100) String jurisdiction,
            @NotBlank @Size(max = 60) String version,
            @NotBlank @Size(max = 80) String contentHash,
            Map<String, String> metadata
    ) {}

    public record KnowledgeSourceResponse(String id, String sourceKey, String title, String kind,
            String jurisdiction, String version, String status, String contentHash,
            Map<String, String> metadata, Instant createdAt, Instant updatedAt) {
        static KnowledgeSourceResponse from(KnowledgeSourceDocument document) {
            return new KnowledgeSourceResponse(document.id(), document.sourceKey(), document.title(),
                    document.kind(), document.jurisdiction(), document.version(), document.status(),
                    document.contentHash(), document.metadata(), document.createdAt(), document.updatedAt());
        }
    }

    public record KnowledgeStatus(String database, String collection, boolean connected,
            long documentCount, String activeVersion) {}
}

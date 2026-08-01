package com.avas.platform.knowledge;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static com.avas.platform.knowledge.KnowledgeModels.*;

@Service
@ConditionalOnProperty(name = "avas.mongo.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeService {
    static final String COLLECTION = "knowledge_sources";
    private final KnowledgeSourceRepository repository;
    private final String database;
    private final String activeVersion;

    KnowledgeService(KnowledgeSourceRepository repository,
            @Value("${spring.data.mongodb.database:}") String configuredDatabase,
            @Value("${spring.data.mongodb.uri}") String mongoUri,
            @Value("${avas.versions.knowledge}") String activeVersion) {
        this.repository = repository;
        this.database = configuredDatabase == null || configuredDatabase.isBlank()
                ? databaseFrom(mongoUri) : configuredDatabase;
        this.activeVersion = activeVersion;
    }

    @PostConstruct
    void seedBaseline() {
        var request = new KnowledgeSourceRequest("avas-knowledge-baseline", "AVAS planning knowledge baseline",
                "RULE_AND_EVIDENCE", "IN-RJ-Jaipur", activeVersion, hash(activeVersion),
                Map.of("owner", "AVAS", "review", "professional-required", "systemOfRecord", "MongoDB"));
        upsert(request);
    }

    public KnowledgeStatus status() {
        return new KnowledgeStatus(database, COLLECTION, true, repository.count(), activeVersion);
    }

    public List<KnowledgeSourceResponse> sources() {
        return repository.findAll().stream().map(KnowledgeSourceResponse::from).toList();
    }

    public KnowledgeSourceResponse upsert(KnowledgeSourceRequest request) {
        var document = repository.findBySourceKey(request.sourceKey()).orElseGet(() ->
                new KnowledgeSourceDocument(request.sourceKey().trim(), request.title(), request.kind(),
                        request.jurisdiction(), request.version(), request.contentHash(), request.metadata()));
        document.update(request.title(), request.kind(), request.jurisdiction(), request.version(),
                request.contentHash(), request.metadata());
        return KnowledgeSourceResponse.from(repository.save(document));
    }

    private static String databaseFrom(String uri) {
        var query = uri.indexOf('?');
        var withoutQuery = uri.substring(0, query >= 0 ? query : uri.length());
        var slash = withoutQuery.lastIndexOf('/');
        return slash >= 0 && slash < withoutQuery.length() - 1 ? withoutQuery.substring(slash + 1) : "avas-new";
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash the knowledge baseline", exception);
        }
    }
}

package com.avas.platform.knowledge;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

interface KnowledgeSourceRepository extends MongoRepository<KnowledgeSourceDocument, String> {
    Optional<KnowledgeSourceDocument> findBySourceKey(String sourceKey);
}

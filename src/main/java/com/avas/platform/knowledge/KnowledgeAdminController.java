package com.avas.platform.knowledge;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import static com.avas.platform.knowledge.KnowledgeModels.*;

@RestController
@RequestMapping("/api/v1/admin/knowledge")
@ConditionalOnProperty(name = "avas.mongo.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeAdminController {
    private final KnowledgeService service;

    KnowledgeAdminController(KnowledgeService service) { this.service = service; }

    @PostMapping("/sources")
    KnowledgeSourceResponse upsert(@Valid @RequestBody KnowledgeSourceRequest request) {
        return service.upsert(request);
    }
}

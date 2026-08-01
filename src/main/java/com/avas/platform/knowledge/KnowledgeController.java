package com.avas.platform.knowledge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.avas.platform.knowledge.KnowledgeModels.*;

@RestController
@RequestMapping("/api/v1/knowledge")
@ConditionalOnProperty(name = "avas.mongo.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeController {
    private final KnowledgeService service;

    KnowledgeController(KnowledgeService service) { this.service = service; }

    @GetMapping("/status")
    KnowledgeStatus status() { return service.status(); }

    @GetMapping("/sources")
    List<KnowledgeSourceResponse> sources() { return service.sources(); }
}

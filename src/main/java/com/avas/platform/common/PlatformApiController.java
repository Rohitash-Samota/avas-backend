package com.avas.platform.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Public API discovery document. Business resources remain authenticated. */
@RestController
@RequestMapping("/api/v1")
public class PlatformApiController {
    @GetMapping({"", "/status"})
    ApiIndex index() {
        return new ApiIndex(
                "UP",
                "v1",
                "MODULAR_MONOLITH",
                "AVAS-Detailed-Product-and-Technical-Build-Specification",
                new Authentication("POST /api/v1/auth/login",
                        "Authorization: Bearer <accessToken>",
                        "X-Active-Role: <one of the user's roles>"),
                Map.of(
                        "mysql", "Transactional system of record: identities, projects, rules, drawings, estimates, commerce and audit",
                        "mongodb", "Versioned flexible documents: requirements, geometry, validations, AI outputs, site reports and feedback",
                        "objectStorage", "Large generated and uploaded files"),
                List.of("/api/v1/auth", "/api/v1/projects", "/api/v1/drawings", "/api/v1/estimates",
                        "/api/v1/pricing", "/api/v1/commerce", "/api/v1/admin"));
    }

    record Authentication(String login, String tokenHeader, String roleHeader) {}

    record ApiIndex(String status, String apiVersion, String architecture, String specification,
            Authentication authentication, Map<String, String> databaseOwnership, List<String> resourceGroups) {}
}

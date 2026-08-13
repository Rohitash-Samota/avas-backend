package com.avas.platform.auth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class IdentityDefaults {
    static final int ROLE_DEFINITION_VERSION = 6;
    static final Map<String, Set<String>> ROLE_DEFINITIONS = roleDefinitions();
    static final Set<String> REQUIRED_INDIVIDUAL_PERMISSIONS = ROLE_DEFINITIONS.get("INDIVIDUAL");
    static final Set<String> PERMISSION_CATALOG = ROLE_DEFINITIONS.values().stream()
            .flatMap(Set::stream).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private IdentityDefaults() {}

    static String displayName(String code) {
        return switch (code) {
            case "INDIVIDUAL" -> "Individual customer";
            case "BUILDER" -> "Builder";
            case "INTERNAL_USER" -> "Architect / internal reviewer";
            case "SITE_ENGINEER" -> "Site engineer";
            case "ADMIN" -> "Administrator";
            default -> code.replace('_', ' ');
        };
    }

    private static Map<String, Set<String>> roleDefinitions() {
        var definitions = new LinkedHashMap<String, Set<String>>();
        definitions.put("INDIVIDUAL", Set.of(
                "PROJECT_CREATE", "PROJECT_READ", "PROJECT_UPDATE", "DRAWING_UPLOAD", "DRAWING_GENERATE",
                "DRAWING_FEEDBACK", "DRAWING_APPROVE_CONCEPT", "ESTIMATE_READ", "ESTIMATE_APPROVE",
                "QUOTATION_COMPARE", "BUILDER_SELECT", "SITE_ENGINEER_REQUEST", "PROJECT_PROGRESS_READ",
                "CHANGE_REQUEST_APPROVE", "PAYMENT_READ", "DOCUMENT_READ", "MESSAGE_USE", "COMMERCE_BUY", "WALLET_READ",
                "BUDGET_RECOMMEND"));
        definitions.put("BUILDER", Set.of(
                "BUILDER_PROFILE_MANAGE", "BUILDER_VERIFICATION_SUBMIT", "SERVICE_LOCATION_MANAGE", "PRICE_MANAGE", "PRICE_SUBMIT",
                "PROJECT_ELIGIBLE_READ", "DRAWING_APPROVED_READ", "BOQ_READ", "QUOTE_CREATE", "QUOTE_REVISE",
                "MILESTONE_MANAGE", "TIMELINE_MANAGE", "PROJECT_AWARD_ACCEPT", "TEAM_MANAGE", "PROGRESS_UPDATE",
                "INVOICE_CREATE", "PAYMENT_READ"));
        definitions.put("INTERNAL_USER", Set.of(
                "ASSIGNED_SERVICE_READ", "PRICE_SUBMIT", "DRAWING_REVIEW", "ESTIMATE_REVIEW",
                "UPLOADED_DRAWING_REVIEW", "CUSTOMER_REQUEST_HANDLE", "PROJECT_PROGRESS_UPDATE", "SITE_REPORT_READ",
                "INSPECTION_CREATE", "ISSUE_CREATE"));
        definitions.put("SITE_ENGINEER", Set.of(
                "ASSIGNED_PROJECT_READ", "SITE_REPORT_CREATE", "WORKER_LOG_CREATE", "MATERIAL_LOG_CREATE",
                "MEDIA_UPLOAD", "INSPECTION_CREATE", "ISSUE_CREATE", "MILESTONE_VERIFY", "CUSTOMER_UPDATE", "PRICE_SUBMIT"));
        definitions.put("ADMIN", Set.of(
                "USER_MANAGE", "ROLE_MANAGE", "PROJECT_MANAGE", "RULE_MANAGE", "DRAWING_TEMPLATE_MANAGE",
                "RECOMMENDATION_RULE_MANAGE", "ROOM_STANDARD_MANAGE", "VASTU_RULE_MANAGE", "CATALOG_MANAGE",
                "PRICE_MANAGE", "VERIFICATION_MANAGE", "APPROVAL_MANAGE", "ASSIGNMENT_MANAGE", "PAYMENT_MANAGE",
                "COUPON_MANAGE", "REPORT_READ", "MODEL_MANAGE", "KNOWLEDGE_MANAGE", "AUDIT_READ", "JOB_MANAGE",
                "PLATFORM_CONFIG_MANAGE", "BUDGET_RECOMMEND"));
        return Collections.unmodifiableMap(definitions);
    }
}

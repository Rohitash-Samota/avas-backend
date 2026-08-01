package com.avas.platform.project;

import java.util.List;
import java.util.Set;

import static com.avas.platform.project.ProjectModels.WorkspaceStep;

/**
 * Product workflow metadata from specification sections 25-30 and 40. Keeping it on the
 * server makes the active role, permissions and visible journey one authoritative contract.
 */
final class RoleWorkflowCatalog {
    private RoleWorkflowCatalog() {}

    static List<WorkspaceStep> forRole(String role, List<String> permissions) {
        var granted = Set.copyOf(permissions);
        return definitions(role).stream().map(step -> new WorkspaceStep(
                step.id(), step.title(), step.detail(), step.permission(), step.href(),
                granted.contains(step.permission()) ? "AVAILABLE" : "RESTRICTED"
        )).toList();
    }

    private static List<WorkspaceStep> definitions(String role) {
        return switch (role) {
            case "BUILDER" -> List.of(
                    step("builder-profile", "Complete builder profile", "Maintain business identity, verification evidence and service locations.", "BUILDER_PROFILE_MANAGE", "/account"),
                    step("builder-rates", "Submit rates", "Add construction, material, labour and package rates for approval.", "PRICE_SUBMIT", "/pricing"),
                    step("builder-opportunities", "Review eligible projects", "Access only projects released to the builder marketplace.", "PROJECT_ELIGIBLE_READ", "/workspace?role=BUILDER"),
                    step("builder-quote", "Create quotation", "Price the approved drawing and BOQ with milestones and timeline.", "QUOTE_CREATE", "/workspace?role=BUILDER"),
                    step("builder-delivery", "Deliver awarded work", "Manage the team, progress evidence, invoices and payment status.", "PROGRESS_UPDATE", "/workspace?role=BUILDER")
            );
            case "INTERNAL_USER" -> List.of(
                    step("professional-assignments", "Open assigned services", "Work only on projects and services assigned to this professional profile.", "ASSIGNED_SERVICE_READ", "/workspace?role=INTERNAL_USER"),
                    step("professional-drawing", "Review conceptual drawings", "Record professional corrections and the applicable review decision.", "DRAWING_REVIEW", "/workspace?role=INTERNAL_USER"),
                    step("professional-estimate", "Validate estimate", "Check quantities, assumptions, evidence confidence and exclusions.", "ESTIMATE_REVIEW", "/workspace?role=INTERNAL_USER"),
                    step("professional-inspection", "Record inspection", "Create traceable inspection observations and identified risks.", "INSPECTION_CREATE", "/workspace?role=INTERNAL_USER"),
                    step("professional-pricing", "Submit service pricing", "Submit location-aware professional rates for administrator approval.", "PRICE_SUBMIT", "/pricing")
            );
            case "SITE_ENGINEER" -> List.of(
                    step("site-assignments", "Open assigned sites", "Access only projects explicitly assigned for execution monitoring.", "ASSIGNED_PROJECT_READ", "/workspace?role=SITE_ENGINEER"),
                    step("site-daily-report", "File daily report", "Record attendance, completed work, photographs and delays.", "SITE_REPORT_CREATE", "/workspace?role=SITE_ENGINEER"),
                    step("site-materials", "Record materials", "Track received and consumed materials against the active project.", "MATERIAL_LOG_CREATE", "/workspace?role=SITE_ENGINEER"),
                    step("site-inspection", "Inspect quality and safety", "Create observations, issues and corrective follow-ups.", "INSPECTION_CREATE", "/workspace?role=SITE_ENGINEER"),
                    step("site-milestone", "Verify milestone", "Authenticate milestone evidence before customer approval or payment.", "MILESTONE_VERIFY", "/workspace?role=SITE_ENGINEER")
            );
            case "ADMIN" -> List.of(
                    step("admin-users", "Govern users and roles", "Provision privileged users and maintain least-privilege role definitions.", "USER_MANAGE", "/admin/users"),
                    step("admin-verification", "Review verifications", "Approve builder and internal-professional access evidence.", "VERIFICATION_MANAGE", "/admin/users"),
                    step("admin-pricing", "Govern prices", "Approve evidence, configuration and model releases without rewriting history.", "PRICE_MANAGE", "/admin/pricing"),
                    step("admin-knowledge", "Govern knowledge", "Admit versioned rules, templates and approved knowledge sources.", "KNOWLEDGE_MANAGE", "/workspace?role=ADMIN"),
                    step("admin-audit", "Review audit history", "Inspect authenticated governance, professional and financial actions.", "AUDIT_READ", "/workspace?role=ADMIN")
            );
            default -> List.of(
                    step("customer-project", "Create a project", "Start from plot details, an uploaded drawing or planning guidance.", "PROJECT_CREATE", "/projects/new"),
                    step("customer-requirements", "Confirm requirements", "Review inferred values and approve the versioned requirement snapshot.", "PROJECT_UPDATE", "/dashboard"),
                    step("customer-concepts", "Compare conceptual plans", "Review explainable alternatives, validation and trade-offs.", "PROJECT_READ", "/dashboard"),
                    step("customer-approval", "Approve a concept", "Approve only the conceptual direction before professional review.", "DRAWING_APPROVE_CONCEPT", "/dashboard"),
                    step("customer-estimate", "Review planning estimate", "Inspect the range, assumptions, exclusions and evidence confidence.", "ESTIMATE_READ", "/dashboard"),
                    step("customer-builder", "Compare builders", "Compare released quotations and select a verified builder.", "QUOTATION_COMPARE", "/dashboard")
            );
        };
    }

    private static WorkspaceStep step(String id, String title, String detail, String permission, String href) {
        return new WorkspaceStep(id, title, detail, permission, href, "AVAILABLE");
    }
}

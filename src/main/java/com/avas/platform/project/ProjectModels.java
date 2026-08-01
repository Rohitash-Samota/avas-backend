package com.avas.platform.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ProjectModels {
    private ProjectModels() {}

    public enum StartMode { PLOT, DRAWING, PLANNING }
    public enum Facing { NORTH, SOUTH, EAST, WEST }
    public enum Category { STANDARD, PREMIUM, LUXURY, NOT_SURE }
    public enum EngineStatus { SUCCESS, ASSISTED, EXPERT_REVIEW, UNSUPPORTED, FAILED_RETRYABLE }
    public enum JobStatus { QUEUED, REQUIREMENTS_PROCESSING, GENERATING_SPACE_PROGRAMME, GENERATING_LAYOUTS, OPTIMIZING_LAYOUTS, VALIDATING, RENDERING, GENERATING_ESTIMATE, COMPLETED, REVIEW_REQUIRED, FAILED }

    public record CreateProjectRequest(@NotBlank String name, @NotNull StartMode startMode) {}

    public record FamilyDetails(
            @Min(0) @Max(10) int adults,
            @Min(0) @Max(10) int children,
            @Min(0) @Max(10) int seniorCitizens,
            boolean regularGuests
    ) {
        public int members() { return adults + children + seniorCitizens; }
    }

    public record BasicDetailsRequest(
            @Min(10) double plotWidth,
            @Min(10) double plotLength,
            @NotNull Facing roadFacing,
            @NotBlank String city,
            @Min(1) @Max(3) int floors,
            @Min(1_000_000) long budget,
            @NotNull Category category,
            @NotNull @Valid FamilyDetails family,
            @Size(max = 5) List<String> preferences
    ) {
        public BasicDetailsRequest {
            preferences = preferences == null ? List.of() : List.copyOf(preferences);
        }
        public double plotArea() { return plotWidth * plotLength; }
    }

    public record PreferenceRequest(@NotNull @Size(min = 1, max = 5) List<@NotBlank String> preferences) {}
    public record FeedbackRequest(@NotBlank @Size(max = 500) String feedback) {}

    public record ProjectSummary(
            String id,
            String projectCode,
            String name,
            StartMode startMode,
            String status,
            int currentSnapshotVersion,
            BasicDetailsRequest details,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record Recommendation(
            String id,
            String title,
            String category,
            int bedrooms,
            int attachedBathrooms,
            int commonBathrooms,
            int parkingCars,
            int builtUpAreaMinimum,
            int builtUpAreaMaximum,
            long estimatedCostLow,
            long estimatedCostHigh,
            boolean seniorCitizenBedroom,
            boolean familyLounge,
            boolean futureExpansion,
            int confidence,
            List<String> reasons,
            Map<String, String> provenance,
            boolean accepted
    ) {}

    public record RequirementSummary(
            String snapshotId,
            int version,
            String projectId,
            String handlingLevel,
            BasicDetailsRequest explicitInputs,
            Recommendation inferredRequirements,
            List<String> assumptions,
            List<String> questions,
            Map<String, String> versions,
            Instant approvedAt
    ) {}

    public record RoomGeometry(String id, String type, double x, double y, double width, double length, double area, String floor) {}
    public record GeometryDocument(String unit, double plotWidth, double plotLength, List<RoomGeometry> rooms, List<Map<String, Object>> doors, List<Map<String, Object>> windows) {}

    public record DrawingCandidate(
            String id,
            String projectId,
            int version,
            String strategy,
            String name,
            int builtUpArea,
            long estimatedCostLow,
            long estimatedCostHigh,
            int vastuScore,
            int naturalLightScore,
            int spaceEfficiencyScore,
            int confidence,
            GeometryDocument geometry,
            List<String> hardViolations,
            List<String> softRecommendations,
            List<String> explanations,
            Map<String, String> versions,
            String status,
            boolean conceptApproved,
            Instant createdAt
    ) {}

    public record DrawingJob(
            String id,
            String projectId,
            JobStatus status,
            int progress,
            String stage,
            List<String> candidateIds,
            String inputSnapshotId,
            Instant createdAt,
            Instant completedAt
    ) {}

    public record ValidationGate(String name, String status, String detail, boolean hardGate) {}
    public record ValidationReport(String drawingId, int score, EngineStatus status, List<ValidationGate> gates, List<String> professionalReview, Map<String, String> versions) {}

    public record EstimateItem(String code, String category, String description, String unit, double quantity, long rate, long amount, String evidenceId, int confidence) {}
    public record Estimate(
            String id,
            String projectId,
            String drawingId,
            int version,
            long low,
            long recommended,
            long high,
            int builtUpArea,
            int durationMonthsLow,
            int durationMonthsHigh,
            int confidence,
            LocalDate validUntil,
            List<EstimateItem> items,
            List<String> assumptions,
            List<String> exclusions,
            Map<String, String> versions,
            boolean approved,
            Instant createdAt
    ) {}

    public record AuditEvent(String id, String projectId, String action, String actorRole, String artifactId, String version, String detail, Instant at) {}
    public record WorkspaceSummary(
            String role,
            String displayName,
            List<String> permissions,
            List<WorkspaceStep> workflow,
            List<WorkspaceMetric> metrics,
            List<WorkspaceTask> tasks
    ) {}
    public record WorkspaceStep(String id, String title, String detail, String permission, String href, String status) {}
    public record WorkspaceMetric(String label, String value, String note) {}
    public record WorkspaceTask(String id, String title, String detail, String status, String due) {}
    public record RevisionReceipt(String drawingId, String revisionId, int nextVersion, String interpretedChange, String status) {}
    public record ProjectStateSnapshot(
            ProjectSummary project,
            Recommendation recommendation,
            RequirementSummary requirement,
            List<DrawingCandidate> drawings,
            List<DrawingJob> jobs,
            List<Estimate> estimates,
            List<AuditEvent> audit
    ) {
        public ProjectStateSnapshot {
            drawings = drawings == null ? List.of() : List.copyOf(drawings);
            jobs = jobs == null ? List.of() : List.copyOf(jobs);
            estimates = estimates == null ? List.of() : List.copyOf(estimates);
            audit = audit == null ? List.of() : List.copyOf(audit);
        }
    }
}

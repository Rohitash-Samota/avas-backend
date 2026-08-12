package com.avas.platform.project;

import java.util.List;

public record WorkspaceSummary(
        String role,
        String displayName,
        List<String> permissions,
        List<WorkspaceStep> workflow,
        List<WorkspaceMetric> metrics,
        List<WorkspaceTask> tasks
) {}

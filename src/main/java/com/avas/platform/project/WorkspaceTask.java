package com.avas.platform.project;

public record WorkspaceTask(
        String id,
        String title,
        String detail,
        String status,
        String due
) {}

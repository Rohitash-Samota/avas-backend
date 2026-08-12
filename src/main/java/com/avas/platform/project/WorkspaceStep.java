package com.avas.platform.project;

public record WorkspaceStep(
        String id,
        String title,
        String detail,
        String permission,
        String href,
        String status
) {}

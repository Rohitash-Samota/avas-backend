package com.avas.platform.project;

public record RevisionReceipt(
        String drawingId,
        String revisionId,
        int nextVersion,
        String interpretedChange,
        String status
) {}

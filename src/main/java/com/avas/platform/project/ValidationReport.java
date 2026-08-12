package com.avas.platform.project;

import java.util.List;
import java.util.Map;

public record ValidationReport(
        String drawingId,
        int score,
        EngineStatus status,
        List<ValidationGate> gates,
        List<String> professionalReview,
        Map<String, String> versions
) {}

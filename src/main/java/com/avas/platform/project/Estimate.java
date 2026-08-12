package com.avas.platform.project;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

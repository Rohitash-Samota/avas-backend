package com.avas.platform.project;

public record EstimateItem(
        String code,
        String category,
        String description,
        String unit,
        double quantity,
        long rate,
        long amount,
        String evidenceId,
        int confidence
) {}

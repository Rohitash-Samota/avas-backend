package com.avas.platform.project;

public record ValidationGate(
        String name,
        String status,
        String detail,
        boolean hardGate
) {}

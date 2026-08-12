package com.avas.platform.project;

import java.util.List;
import java.util.Map;

public record GeometryDocument(
        String unit,
        double plotWidth,
        double plotLength,
        List<RoomGeometry> rooms,
        List<Map<String, Object>> doors,
        List<Map<String, Object>> windows
) {}

package com.avas.platform.project;

public record RoomGeometry(
        String id,
        String type,
        double x,
        double y,
        double width,
        double length,
        double area,
        String floor
) {}

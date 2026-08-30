package com.avas.platform.project;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class HubDebugTest {
    private final GeometryEngine engine = new GeometryEngine();

    @Test
    void dump() {
        var details = new BasicDetailsRequest(40, 60, Facing.NORTH, "Jaipur", 2, 7_000_000,
                Category.PREMIUM, new FamilyDetails(2, 2, 1, true), List.of("Natural light"));
        var rec = new Recommendation("rec-1", "Four-bedroom home", "PREMIUM", 4, 3, 1, 1,
                2400, 2800, 6_300_000, 7_300_000, true, true, true, 92,
                List.of("Family brief"), Map.of("rule", "test"), true);
        var candidates = engine.generate("p-absurd", 1, details, rec,
                Map.of("ruleVersion", "r", "strategyVersion", "s"));
        for (var room : candidates.getFirst().geometry().rooms()) {
            var spec = RoomSpec.of(room.type());
            System.out.printf("DBG %-7s %-19s x=%6.2f y=%6.2f  %6.2f x %6.2f = %7.2f (max %.0f)%s%n",
                    room.floor(), room.type(), room.x(), room.y(), room.width(), room.length(),
                    room.area(), spec.maxArea(),
                    room.area() > spec.maxArea() + 5 ? "  <<< OVER" : "");
        }
    }
}

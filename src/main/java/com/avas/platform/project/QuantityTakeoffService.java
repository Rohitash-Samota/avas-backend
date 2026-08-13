package com.avas.platform.project;

import com.avas.platform.pricing.PriceItemType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Produces reproducible planning quantities from the authoritative saved geometry. */
@Component
public class QuantityTakeoffService {
    public List<QuantityTakeoffLine> takeoff(ProjectSummary project, DrawingCandidate drawing) {
        if (project == null || project.details() == null || drawing == null || drawing.geometry() == null) {
            throw new IllegalArgumentException("Project details and drawing geometry are required for costing");
        }
        var builtUp = BigDecimal.valueOf(drawing.builtUpArea());
        var rooms = drawing.geometry().rooms() == null ? List.<RoomGeometry>of() : drawing.geometry().rooms();
        var roomCount = Math.max(1, rooms.size());
        var wetRooms = Math.max(1, rooms.stream().filter(room -> room.type().contains("BATH")
                || room.type().contains("TOILET") || room.type().contains("KITCHEN")).count());
        var kitchenBathPackages = Math.max(1, rooms.stream().filter(room -> room.type().contains("BATH")
                || room.type().contains("TOILET") || room.type().contains("KITCHEN")).count());
        var openings = Math.max(1, size(drawing.geometry().doors()) + size(drawing.geometry().windows()));
        var wetArea = rooms.stream().filter(room -> room.type().contains("BATH") || room.type().contains("TOILET")
                        || room.type().contains("KITCHEN") || room.type().contains("UTILITY"))
                .mapToDouble(RoomGeometry::area).sum();
        var floorFootprint = builtUp.divide(BigDecimal.valueOf(Math.max(1, project.details().floors())),
                3, RoundingMode.HALF_UP);
        var externalArea = BigDecimal.valueOf(project.details().plotArea()).subtract(floorFootprint)
                .max(BigDecimal.ONE);

        var lines = new ArrayList<QuantityTakeoffLine>();
        lines.add(line("CEMENT", "CEMENT", "Cement", "BAG", builtUp.multiply(new BigDecimal("0.40")),
                ".075", PriceItemType.MATERIAL, "Structural-grade cement allowance", true, false));
        lines.add(line("REINFORCEMENT_STEEL", "STEEL", "Reinforcement steel", "KG",
                builtUp.multiply(new BigDecimal("4.00")), ".105", PriceItemType.MATERIAL,
                "TMT reinforcement allowance; grade and bar schedule require structural design", true, false));
        lines.add(line("MASONRY", "MASONRY", "Masonry units", "EACH",
                builtUp.multiply(new BigDecimal("8.00")), ".060", PriceItemType.MATERIAL,
                "Block or brick allowance pending wall schedule", true, false));
        lines.add(line("SAND_AGGREGATE", "AGGREGATE", "Sand and aggregates", "CU_FT",
                builtUp.multiply(new BigDecimal("2.50")), ".055", PriceItemType.MATERIAL,
                "Concrete, mortar and plaster aggregate allowance", true, false));
        lines.add(line("CIVIL_LABOUR", "CIVIL_LABOUR", "Civil construction labour", "SQ_FT", builtUp,
                ".065", PriceItemType.LABOUR, "Foundation, RCC, masonry and plaster labour allowance", false, true));
        lines.add(line("FLOORING", "FLOORING", "Flooring materials", "SQ_FT",
                builtUp.multiply(new BigDecimal("0.82")), ".080", PriceItemType.MATERIAL,
                "Internal floor finish allowance", true, false));
        lines.add(line("PAINT", "PAINT", "Paint and wall finishes", "SQ_FT",
                builtUp.multiply(new BigDecimal("3.50")), ".040", PriceItemType.MATERIAL,
                "Internal and external paintable surface allowance", true, false));
        lines.add(line("FINISH_LABOUR", "FINISH_LABOUR", "Flooring and finish labour", "SQ_FT", builtUp,
                ".030", PriceItemType.LABOUR, "Installation, preparation and finishing labour", false, true));
        lines.add(line("ELECTRICAL", "ELECTRICAL", "Electrical installation", "POINT",
                BigDecimal.valueOf(Math.max(roomCount * 4L, project.details().floors() * 12L)), ".080",
                PriceItemType.PACKAGE, "Concealed wiring, distribution and standard point allowance", true, true));
        lines.add(line("PLUMBING", "PLUMBING", "Plumbing installation", "POINT",
                BigDecimal.valueOf(Math.max(wetRooms * 5L, project.details().floors() * 10L)), ".070",
                PriceItemType.PACKAGE, "Water supply, drainage and standard plumbing point allowance", true, true));
        lines.add(line("KITCHEN_BATHROOM", "KITCHEN_BATHROOM", "Kitchen and bathroom packages", "PACKAGE",
                BigDecimal.valueOf(kitchenBathPackages), ".115", PriceItemType.PACKAGE,
                "Cabinetry, counters, sanitaryware and standard fixtures allowance", true, true));
        lines.add(line("DOORS_WINDOWS", "DOORS_WINDOWS", "Doors and windows", "EACH",
                BigDecimal.valueOf(openings), ".090", PriceItemType.PACKAGE,
                "Door, window, frame, glazing and hardware allowance", true, true));
        lines.add(line("PROFESSIONAL_SERVICES", "PROFESSIONAL_SERVICES", "Professional services", "LS",
                BigDecimal.ONE, ".045", PriceItemType.SERVICE,
                "Concept-stage architect, engineering and quantity-review allowance", false, true));
        lines.add(line("WATERPROOFING", "WATERPROOFING", "Waterproofing", "SQ_FT",
                BigDecimal.valueOf(Math.max(1, wetArea)), ".025", PriceItemType.PACKAGE,
                "Wet-area and exposed-slab waterproofing allowance", true, true));
        lines.add(line("EXTERNAL_SITE_WORKS", "EXTERNAL_SITE_WORKS", "External and site works", "SQ_FT",
                externalArea, ".040", PriceItemType.PACKAGE,
                "Basic drainage, paving and plot-development allowance", true, true));
        lines.add(line("PRELIMINARIES", "PRELIMINARIES", "Construction preliminaries", "MONTH",
                BigDecimal.valueOf(12), ".025", PriceItemType.SERVICE,
                "Temporary works, supervision, safety and mobilisation allowance", false, true));

        assertCoreFallbackShare(lines);

        var liftProvision = project.details().parameters().liftProvision();
        if ("FUTURE_SHAFT".equals(liftProvision) || "PASSENGER".equals(liftProvision)) {
            lines.add(line("LIFT_SHAFT_PROVISION", "LIFT_SHAFT_PROVISION", "Lift shaft provision", "EACH",
                    BigDecimal.ONE, ".015", PriceItemType.PACKAGE,
                    "Structural shaft provision only; lift car, controls and operating equipment excluded",
                    true, true));
        }
        if ("PASSENGER".equals(liftProvision)) {
            lines.add(line("PASSENGER_LIFT", "PASSENGER_LIFT", "Passenger lift equipment", "EACH",
                    BigDecimal.ONE, ".045", PriceItemType.PACKAGE,
                    "Passenger lift car, drive, controls and standard installation allowance", true, true));
        }
        return List.copyOf(lines);
    }

    private void assertCoreFallbackShare(List<QuantityTakeoffLine> lines) {
        var total = lines.stream().map(QuantityTakeoffLine::fallbackShare)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalStateException("Core fallback takeoff shares must total exactly 1.000; found " + total);
        }
    }

    private QuantityTakeoffLine line(String code, String category, String description, String unit,
            BigDecimal quantity, String share, PriceItemType type, String specification,
            boolean materialIncluded, boolean labourIncluded) {
        return new QuantityTakeoffLine(code, category, description, unit,
                quantity.setScale(3, RoundingMode.HALF_UP), new BigDecimal(share), type, specification,
                materialIncluded, labourIncluded);
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }
}

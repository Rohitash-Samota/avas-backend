package com.avas.platform.project;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Native deterministic geometry engine. Keeping this inside the platform API makes layouts,
 * validation and workflow versioning one transactional backend concern.
 */
@Component
class GeometryEngine {
    /**
     * Current geometry document schema.
     *
     * <p>{@code multi-floor-3} replaced the fixed grid packer with the programme-driven
     * {@link FloorPlanner}: every storey now carries a circulation corridor, rooms are held to the
     * dimensions their type is usable at, and a private bathroom sits against the bedroom it serves.
     * Earlier documents have no corridor and rooms sized only by area, so they cannot be read as a
     * buildable arrangement and are reported as needing a regenerated version.</p>
     *
     * <p>{@code multi-floor-2} corrected the opening compass convention: north is the maximum y of
     * the planning grid, matching {@link PlotBoundary} and the setback envelope. Documents still
     * stamped {@code multi-floor-1} carry openings mirrored north to south.</p>
     */
    static final String GEOMETRY_SCHEMA_VERSION = "multi-floor-3";
    /** Schemas that carry a complete room, door and window set for every requested floor. */
    static final java.util.Set<String> MULTI_FLOOR_SCHEMAS =
            java.util.Set.of("multi-floor-1", "multi-floor-2", GEOMETRY_SCHEMA_VERSION);

    private static final List<Strategy> STRATEGIES = List.of(
            new Strategy("BUDGET_OPTIMIZED", "Efficient Courtyard", .43, .46,
                    88, 82, 95, List.of(
                    "Compact circulation lowers the built-up area.",
                    "Wet areas share a plumbing shaft.",
                    "A regular grid reduces construction complexity.")),
            new Strategy("BALANCED", "Garden Threshold", .38, .46,
                    94, 91, 91, List.of(
                    "The senior suite is on the ground floor.",
                    "Kitchen and utility are directly connected.",
                    "The stair can support future independent access.")),
            new Strategy("LIFESTYLE_OPTIMIZED", "Lightwell House", .34, .42,
                    91, 96, 86, List.of(
                    "A central lightwell improves daylight.",
                    "Shared spaces are enlarged for regular guests.",
                    "A separate office protects private space."))
    );

    List<DrawingCandidate> generate(String projectId, int version, BasicDetailsRequest details,
            Recommendation recommendation, Map<String, String> versions) {
        return generate(projectId, version, details, recommendation, versions, null);
    }

    List<DrawingCandidate> generate(String projectId, int version, BasicDetailsRequest details,
            Recommendation recommendation, Map<String, String> versions,
            PlanningParameterSet parameterSet) {
        var boundary = details.boundary();
        return generate(projectId, version, details, recommendation, versions, parameterSet,
                BuildableEnvelope.derive(boundary,
                        SetbackRule.forUsage(boundary, details.floors(), details.parameters().plotUsage()),
                        details.floors()));
    }

    /**
     * Generates candidates against an already-resolved legal envelope.
     *
     * <p>The envelope is computed once per project and reused so the recommendation, the geometry
     * and the estimate all reason about the same buildable footprint.</p>
     */
    List<DrawingCandidate> generate(String projectId, int version, BasicDetailsRequest details,
            Recommendation recommendation, Map<String, String> versions,
            PlanningParameterSet parameterSet, BuildableEnvelope envelope) {
        if (details.plotWidth() < 10 || details.plotLength() < 10) {
            throw new IllegalArgumentException("Plot dimensions must each be at least 10 feet");
        }
        if (envelope == null) {
            throw new IllegalArgumentException("A buildable envelope is required before layout generation");
        }

        var candidates = new ArrayList<DrawingCandidate>();
        for (int index = 0; index < STRATEGIES.size(); index++) {
            var strategy = STRATEGIES.get(index);
            var variant = variantFor(parameterSet, strategy.key());
            var optionParameters = optionParameters(details.parameters(), variant);
            var planner = plannerFor(envelope, details.roadFacing(), details.floors(), strategy,
                    recommendation, optionParameters, variant);
            // Extension rooms join before the openings are placed, so the door tree reaches them
            // through the walls they genuinely share rather than through an afterthought.
            var rooms = extensionRooms(envelope, planner.planBuilding(), details.floors());
            // A slanted boundary is the one edge rectangles cannot reach, so the rooms standing
            // against it take the boundary's own line. Square plots are already flush against it.
            // The line followed is the buildable outline, so this recovers the wedge inside the
            // setback ring without ever crossing it — which is why it is not reserved for the
            // waived case: a tapered plot with setbacks was leaving a quarter of its legal area
            // unbuilt for want of a wall that is not at right angles.
            if (envelope.slanted()) {
                rooms = followBoundary(rooms, envelope.buildableOutline());
            }
            var doors = doorsFor(rooms, details.roadFacing());
            var windows = windowsFor(rooms, doors, openSides(envelope, details.roadFacing()));
            // Rooms already contain every requested floor, so this is an aggregate area and must
            // not be multiplied by the floor count a second time. Outdoor programme zones are
            // deliberately excluded: pricing them again at the full built-up rate would double
            // count roof terraces and overstate open parking/courtyard construction.
            var constructedArea = rooms.stream().filter(this::countsAsBuiltUp)
                    .mapToDouble(RoomGeometry::area).sum();
            var builtUpArea = (int) Math.round(constructedArea);
            var violations = new ArrayList<>(validate(details.plotWidth(), details.plotLength(), rooms));
            violations.addAll(validateEnvelope(envelope, rooms));
            violations.addAll(validateDocument(details.floors(), rooms, doors, windows));
            // Planned before the audit rather than at the point the document is assembled, because
            // open parking on the approach is a bay the family actually gets. Counting only indoor
            // rectangles reported every plot that parks outside — which is most of them, and every
            // plot the tier planned a driveway for — as short of its own recommended bay count.
            var siteElements = siteElements(envelope, details.roadFacing(), optionParameters,
                    details.category());
            var programmeGaps = programmeGaps(recommendation, rooms, siteElements, builtUpArea,
                    optionParameters, variant);
            violations.addAll(programmeGaps);
            // Measured from the layout that was just placed, rather than read off the strategy.
            // These used to be constants, so all three options scored the same on every project
            // ever generated and the ranking said nothing about the homes being compared.
            var score = CandidateScore.measure(rooms, windows, envelope, details.roadFacing(),
                    details.floors());
            var reviewRequired = details.plotWidth() < 20 || !violations.isEmpty();
            var provenance = new LinkedHashMap<>(versions);
            provenance.put("generator", "AVAS deterministic layout engine");
            provenance.put("generationMode", "DETERMINISTIC");
            provenance.put("generationModel", "No generative AI model");
            provenance.put("modelVersion", "not-applicable");
            provenance.put("promptVersion", "not-used");
            provenance.put("strategyId", strategy.key());
            provenance.put("geometrySchemaVersion", GEOMETRY_SCHEMA_VERSION);
            provenance.put("requestedFloors", String.valueOf(details.floors()));
            provenance.put("roadFacing", details.roadFacing().name());
            provenance.put("householdAdults", String.valueOf(details.family().adults()));
            provenance.put("householdChildren", String.valueOf(details.family().children()));
            provenance.put("householdSeniors", String.valueOf(details.family().seniorCitizens()));
            provenance.put("householdMembers", String.valueOf(details.family().members()));
            provenance.put("regularGuests", String.valueOf(details.family().regularGuests()));
            provenance.put("recommendedBedrooms", String.valueOf(recommendation.bedrooms()));
            provenance.put("recommendedAttachedBathrooms",
                    String.valueOf(recommendation.attachedBathrooms()));
            provenance.put("recommendedCommonBathrooms", String.valueOf(recommendation.commonBathrooms()));
            provenance.put("recommendedParkingCars", String.valueOf(recommendation.parkingCars()));
            provenance.put("recommendedBuiltUpMinimum",
                    String.valueOf(recommendation.builtUpAreaMinimum()));
            provenance.put("recommendedBuiltUpMaximum",
                    String.valueOf(recommendation.builtUpAreaMaximum()));
            provenance.put("recommendationMethod",
                    recommendation.provenance().getOrDefault("method", "deterministic-recommendation"));
            provenance.put("recommendationReasons", String.join(" | ", recommendation.reasons()));
            provenance.put("preferences", String.join(" | ", details.preferences()));
            provenance.put("requestedLiftProvision", details.parameters().liftProvision());
            provenance.put("requestedBalconyCount", String.valueOf(details.parameters().balconyCount()));
            provenance.put("requestedTerrace", String.valueOf(details.parameters().terraceRequired()));
            provenance.put("requestedCourtyard", String.valueOf(details.parameters().courtyardRequired()));
            provenance.put("requestedAccessibleGroundFloor",
                    String.valueOf(details.parameters().accessibleGroundFloor()));
            provenance.put("requestedParkingCars", String.valueOf(details.parameters().parkingCars()));
            provenance.put("requestedSolarReady", String.valueOf(details.parameters().solarReady()));
            provenance.put("requestedRainwaterHarvesting",
                    String.valueOf(details.parameters().rainwaterHarvesting()));
            provenance.put("requestedHomeType", details.parameters().homeType());
            provenance.put("requestedStaircaseType", details.parameters().staircaseType());
            provenance.put("requestedPlotUsage", details.parameters().plotUsage());
            provenance.put("finishTier", details.category().name());
            provenance.put("projectCity", details.city());
            provenance.put("plotOutlineCorners", String.valueOf(envelope.plot().vertices().size()));
            provenance.put("plotOutlineIrregular", String.valueOf(envelope.plot().irregular()));
            provenance.put("plotArea", String.valueOf(Math.round(envelope.plotArea())));
            provenance.put("buildableArea", String.valueOf(Math.round(envelope.buildableArea())));
            provenance.put("footprintArea", String.valueOf(Math.round(envelope.footprintArea())));
            provenance.put("groundCoverage", String.format(Locale.ROOT, "%.3f", envelope.coverageRatio()));
            provenance.put("setbackFront", String.valueOf(envelope.setbacks().front()));
            provenance.put("setbackRear", String.valueOf(envelope.setbacks().rear()));
            provenance.put("setbackSide", String.valueOf(envelope.setbacks().side()));
            provenance.put("setbackSource", envelope.setbacks().source());
            provenance.put("optimizerSeed", Integer.toUnsignedString(
                    Objects.hash(projectId, version, strategy.key())));
            if (parameterSet != null) {
                provenance.put("parameterProvider", safe(parameterSet.provider(), "DETERMINISTIC"));
                provenance.put("parameterModel", safe(parameterSet.model(), "avas-parameter-rules-1.1.0"));
                provenance.put("promptVersion", safe(parameterSet.promptVersion(), "home-parameters-1.1.0"));
                provenance.put("parameterSchemaVersion", safe(parameterSet.schemaVersion(), "home-parameters-1"));
                provenance.put("parameterFallback", String.valueOf(parameterSet.fallbackUsed()));
                if (parameterSet.requestId() != null) provenance.put("parameterRequestId", parameterSet.requestId());
                if (parameterSet.providerRequestId() != null) {
                    provenance.put("parameterProviderRequestId", parameterSet.providerRequestId());
                }
                if (!parameterSet.warnings().isEmpty()) {
                    provenance.put("parameterWarning", String.join(" | ", parameterSet.warnings()));
                }
                if ("OPENAI".equalsIgnoreCase(parameterSet.provider()) && !parameterSet.fallbackUsed()) {
                    provenance.put("generationMode", "AI_PARAMETER_ASSISTED_DETERMINISTIC_GEOMETRY");
                    provenance.put("generationModel", parameterSet.model());
                    provenance.put("modelVersion", parameterSet.model());
                }
            }
            provenance.put("homeType", optionParameters.homeType());
            provenance.put("staircaseType", optionParameters.staircaseType());
            provenance.put("liftProvision", optionParameters.liftProvision());
            provenance.put("balconyCount", String.valueOf(optionParameters.balconyCount()));
            provenance.put("terraceRequired", String.valueOf(optionParameters.terraceRequired()));
            provenance.put("courtyardRequired", String.valueOf(optionParameters.courtyardRequired()));
            provenance.put("accessibleGroundFloor", String.valueOf(optionParameters.accessibleGroundFloor()));
            provenance.put("parkingCars", String.valueOf(optionParameters.parkingCars()));
            provenance.put("solarReady", String.valueOf(optionParameters.solarReady()));
            provenance.put("rainwaterHarvesting", String.valueOf(optionParameters.rainwaterHarvesting()));
            provenance.put("plotUsage", optionParameters.plotUsage());
            provenance.put("setbacksWaived", String.valueOf(envelope.setbacks().waived()));
            if (variant != null) {
                provenance.put("parameterVariantTitle", variant.title());
                provenance.put("duplexZoning", variant.duplexZoning());
            }

            candidates.add(new DrawingCandidate(
                    "drawing-" + projectId + "-v" + version + "-" + (index + 1),
                    projectId,
                    version,
                    strategy.key(),
                    variant == null || variant.title() == null ? strategy.name() : variant.title(),
                    builtUpArea,
                    recommendation.estimatedCostLow(),
                    recommendation.estimatedCostHigh(),
                    score.vastu(),
                    score.naturalLight(),
                    score.spaceEfficiency(),
                    score.overall(variant == null ? null : variant.weights(), violations.size()),
                    new GeometryDocument("FEET", details.plotWidth(), details.plotLength(), rooms,
                            doors, windows, envelope.plot().vertices(), envelope.buildableOutline(),
                            envelope.setbacks(), envelope.plotArea(), envelope.buildableArea(),
                            siteElements),
                    List.copyOf(violations),
                    softRecommendations(envelope, planner.notes(),
                            daylightNotes(envelope, rooms, windows, details.roadFacing())),
                    programmeExplanations(strategy, variant, programmeGaps, score),
                    Map.copyOf(provenance),
                    reviewRequired ? "EXPERT_REVIEW" : "SUCCESS",
                    false,
                    Instant.now()));
        }
        return List.copyOf(candidates);
    }

    /**
     * Non-blocking guidance shown beside a candidate, led by anything specific to this plot.
     *
     * <p>The two standing professional-scope reminders always close the list so they can never be
     * pushed out by plot-specific notes.</p>
     */
    /**
     * What building to the boundary costs the plan in light, said in rooms rather than in principle.
     *
     * <p>Two of the four walls are now party walls, so the rooms behind them have no window. That is
     * the bargain of a house that uses its whole plot, and the customer is entitled to see the size
     * of it on the drawing rather than discover it on site.</p>
     */
    private List<String> daylightNotes(BuildableEnvelope envelope, List<RoomGeometry> rooms,
            List<Map<String, Object>> windows, Facing roadFacing) {
        if (!envelope.setbacks().waived()) {
            return List.of();
        }
        var lit = windows.stream().map(window -> String.valueOf(window.get("roomId")))
                .collect(java.util.stream.Collectors.toSet());
        var dark = rooms.stream()
                .filter(room -> HABITABLE_TYPES.contains(room.type()))
                .filter(room -> !lit.contains(room.id()))
                .toList();
        var notes = new ArrayList<String>();
        var flanks = roadFacing == Facing.NORTH || roadFacing == Facing.SOUTH ? "east and west"
                : "north and south";
        notes.add("Building to the plot line makes the " + flanks + " walls party walls: they are "
                + "shared with the neighbouring plots and carry no windows, so this layout takes its "
                + "light from the road and rear faces the way a terraced city house does. The "
                + "neighbours' consent and a party-wall agreement are needed before building.");
        if (!dark.isEmpty()) {
            notes.add(dark.size() + " habitable room" + (dark.size() == 1 ? "" : "s")
                    + " sit behind those party walls with no window ("
                    + dark.stream().map(room -> roomLabel(room.type())).distinct()
                            .collect(java.util.stream.Collectors.joining(", "))
                    + "); a courtyard or lightwell cut into the plan is how a house this deep is "
                    + "normally given daylight, and an architect should place one before this is built.");
        }
        return List.copyOf(notes);
    }

    /** Rooms a family lives in, which are the ones daylight is judged against. */
    private static final java.util.Set<String> HABITABLE_TYPES = java.util.Set.of(
            "LIVING_ROOM", "DINING", "KITCHEN", "FAMILY_LOUNGE", "BEDROOM", "MASTER_BEDROOM",
            "SENIOR_BEDROOM", "FLEX_ROOM", "FLEX_GUEST_ROOM", "MULTIPURPOSE_ROOM", "STUDY",
            "HOME_OFFICE", "PRAYER_ROOM");

    private String roomLabel(String type) {
        return type == null ? "room" : type.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private List<String> softRecommendations(BuildableEnvelope envelope, List<String> plannerNotes,
            List<String> daylightNotes) {
        var notes = new ArrayList<String>(envelope.notes());
        notes.addAll(daylightNotes);
        // Anything the planner had to compromise on is said here rather than left for the customer
        // to spot in the drawing: a squeezed stair core, a room the plot could not carry.
        plannerNotes.stream().distinct().forEach(notes::add);
        if (envelope.underUsesEnvelope()) {
            notes.add("An architect can recover additional area by following the plot outline instead of the "
                    + "largest inscribed rectangle AVAS packs into.");
        }
        notes.add("Verify setbacks against the applicable local authority release.");
        notes.add("A licensed structural engineer must approve the final grid.");
        return List.copyOf(notes);
    }

    /** Depth a car needs to stand clear of the building, in feet. */
    /** Below this a strip of open ground is a margin, not somewhere a family would put anything. */
    private static final double USABLE_OPEN_DEPTH = 6d;

    /**
     * Plans the open ground: where the cars stand, and what the rest of the plot becomes.
     *
     * <p>Parking goes outside the building whenever the approach can take it. A car parked in the
     * setback costs a slab, not a storey, and every square foot it saves indoors is one the family
     * gets as a room instead — which is why the reference plans customers bring us show a porch and a
     * driveway rather than a garage eating the ground floor.</p>
     *
     * <p>The rest is only offered on the finishes that pay for it. Naming a lawn on a plot whose
     * budget is spent on the shell would be drawing a promise the estimate cannot keep — unless the
     * customer chose {@link HomeParameters#OPEN_SPACE}, which is them saying the open ground is the
     * point and should be planned rather than left over.</p>
     *
     * <p>Full plot usage plans no open ground at all. The building occupies the outline, so a strip
     * drawn beside it would be a sliver the envelope already gave to a room: the cars belong indoors
     * on that choice, and the planner places them there.</p>
     */
    private List<SiteElement> siteElements(BuildableEnvelope envelope, Facing facing,
            HomeParameters parameters, Category category) {
        if (parameters.usesFullPlot()) {
            return List.of();
        }
        var open = openGround(envelope);
        if (open.isEmpty()) {
            return List.of();
        }
        var elements = new ArrayList<SiteElement>();
        // The same decision the planner sized its ground floor against, so the bays drawn on the
        // approach and the bays the building did not have to carry are one answer, not two.
        var approachParking = ApproachParking.decide(envelope, facing, parameters);
        var bayRectangle = approachParking.bayRectangle(envelope, facing);
        var approach = approachParking.area();
        var approachParked = bayRectangle != null;
        if (bayRectangle != null) {
            elements.add(SiteElement.of("site-parking", "OUTDOOR_PARKING",
                    approachParking.bays() + " car open parking", bayRectangle.x(), bayRectangle.y(),
                    bayRectangle.width(), bayRectangle.length()));
        }

        // Garden is a finish-tier promise, so it is only drawn where the specification carries it —
        // or where the customer asked for the open ground to be planned, which buys it at any tier.
        if (parameters.plansOpenSpace() || category == Category.LUXURY || category == Category.PREMIUM) {
            // The approach is only withheld from the garden when a car is actually standing on it.
            // Excluding it either way meant that on an ordinary plot — where the front setback is
            // the one band deep enough to plant and too shallow to park in — "setbacks with garden"
            // drew no garden at all, and was indistinguishable from plain standard setbacks.
            var parkedOn = approachParked ? approach : null;
            open.stream()
                    .filter(piece -> piece != parkedOn)
                    .filter(piece -> Math.min(piece.width(), piece.length()) >= USABLE_OPEN_DEPTH)
                    .max(Comparator.comparingDouble(PlotGeometry.Rect::area))
                    .ifPresent(piece -> elements.add(SiteElement.of("site-garden", "GARDEN", "Garden",
                            piece.x(), piece.y(), piece.width(), piece.length())));
        }
        return List.copyOf(elements);
    }

    /**
     * The ground on this plot no room is standing on.
     *
     * <p>Taken from the plot the same way the extension zones were taken from the envelope, rather
     * than as the four bands around the packed rectangle. Those bands were open ground only while
     * the packed rectangle was the whole building; once the planner also took the leg of an L-shaped
     * plot, the band beside it still read as empty and a lawn was drawn straight over the rooms
     * standing in it.</p>
     */
    private List<PlotGeometry.Rect> openGround(BuildableEnvelope envelope) {
        return ApproachParking.openGround(envelope);
    }


    private boolean countsAsBuiltUp(RoomGeometry room) {
        return switch (room.type()) {
            case "PARKING", "COURTYARD_PARKING", "COURTYARD", "OPEN_SPACE", "TERRACE" -> false;
            default -> true;
        };
    }

    private List<String> programmeGaps(Recommendation recommendation, List<RoomGeometry> rooms,
            List<SiteElement> siteElements, int builtUpArea, HomeParameters parameters,
            PlanningParameterVariant variant) {
        var gaps = new ArrayList<String>();
        var bedrooms = rooms.stream().filter(room -> room.type().contains("BEDROOM")).count();
        var attachedBathrooms = rooms.stream().filter(room -> room.type().contains("ATTACHED_BATHROOM")).count();
        var commonBathrooms = rooms.stream().filter(room -> "BATHROOM".equals(room.type())).count();
        var parkingBays = representedParkingBays(rooms, siteElements);
        if (bedrooms != recommendation.bedrooms()) {
            gaps.add("Programme gap: " + bedrooms + " of " + recommendation.bedrooms()
                    + " recommended bedrooms represented");
        }
        if (attachedBathrooms < recommendation.attachedBathrooms()) {
            gaps.add("Programme gap: " + attachedBathrooms + " of " + recommendation.attachedBathrooms()
                    + " recommended attached bathrooms represented");
        }
        if (commonBathrooms < recommendation.commonBathrooms()) {
            gaps.add("Programme gap: " + commonBathrooms + " of " + recommendation.commonBathrooms()
                    + " recommended common bathrooms represented");
        }
        if (parkingBays < recommendation.parkingCars()) {
            gaps.add("Programme gap: " + parkingBays + " of " + recommendation.parkingCars()
                    + " recommended parking bays represented");
        }
        if (recommendation.seniorCitizenBedroom()
                && rooms.stream().noneMatch(room -> room.type().contains("SENIOR_BEDROOM"))) {
            gaps.add("Programme gap: recommended ground-floor senior bedroom is not represented");
        }
        if (recommendation.familyLounge()
                && rooms.stream().noneMatch(room -> room.type().contains("FAMILY_LOUNGE"))) {
            gaps.add("Programme gap: recommended family lounge is not represented");
        }
        if (recommendation.futureExpansion()
                && rooms.stream().noneMatch(room -> room.type().contains("TERRACE")
                        || room.type().contains("BALCONY") || room.type().contains("FUTURE_EXPANSION"))) {
            gaps.add("Programme gap: recommended future-expansion zone is not represented");
        }
        // Packing and whole-square-foot display introduce small boundary differences. Treat a
        // two-percent edge variance as rounding tolerance, not a cost-basis failure.
        if (builtUpArea < recommendation.builtUpAreaMinimum() * .98
                || builtUpArea > recommendation.builtUpAreaMaximum() * 1.02) {
            gaps.add("Programme gap: placed built-up area " + builtUpArea + " sq ft is outside recommended "
                    + recommendation.builtUpAreaMinimum() + "-" + recommendation.builtUpAreaMaximum()
                    + " sq ft cost basis");
        }
        var liftCount = rooms.stream().filter(room -> "LIFT_SHAFT".equals(room.type())).count();
        if (!"NONE".equals(parameters.liftProvision()) && liftCount == 0) {
            gaps.add("Programme gap: requested " + parameters.liftProvision().toLowerCase(Locale.ROOT)
                    .replace('_', ' ') + " lift provision is not represented");
        }
        var balconies = rooms.stream().filter(room -> "BALCONY".equals(room.type())).count();
        if (balconies < parameters.balconyCount()) {
            gaps.add("Programme gap: " + balconies + " of " + parameters.balconyCount()
                    + " requested balconies represented");
        }
        if (parameters.terraceRequired()
                && rooms.stream().noneMatch(room -> "TERRACE".equals(room.type()))) {
            gaps.add("Programme gap: requested terrace is not represented");
        }
        if (parameters.courtyardRequired()
                && rooms.stream().noneMatch(room -> room.type().contains("COURTYARD"))) {
            gaps.add("Programme gap: requested courtyard is not represented");
        }
        var representedParking = parkingBays;
        if (representedParking < parameters.parkingCars()) {
            gaps.add("Programme gap: " + representedParking + " of " + parameters.parkingCars()
                    + " requested parking bays represented");
        }
        gaps.addAll(roomTargetGaps(rooms, variant));
        return List.copyOf(gaps);
    }

    /**
     * Bays this plan actually gives the family, indoors and on the approach alike.
     *
     * <p>A car standing in the front setback is parked. Measuring only the rectangles inside the
     * building said otherwise, so a layout that deliberately kept the cars outside — which is the
     * arrangement that buys the ground floor its rooms back — was reported as failing to provide
     * the parking it had just planned.</p>
     */
    private long representedParkingBays(List<RoomGeometry> rooms, List<SiteElement> siteElements) {
        var indoors = rooms.stream().filter(room -> room.type().contains("PARKING"))
                .mapToLong(room -> bayCapacity(room.width(), room.length())).sum();
        var outdoors = siteElements == null ? 0L : siteElements.stream()
                .filter(element -> element.type().contains("PARKING"))
                .mapToLong(element -> bayCapacity(element.width(), element.length())).sum();
        return indoors + outdoors;
    }

    /**
     * Cars a rectangle of this size can stand, whichever way they are arranged.
     *
     * <p>Dimension-aware rather than area-aware, so a long four-foot strip can never masquerade as
     * usable parking. Capped per rectangle because beyond three abreast the bays need an aisle the
     * conceptual plan is not modelling.</p>
     */
    private long bayCapacity(double width, double length) {
        return Math.min(3, Math.max(
                (long) Math.floor(width / 8d) * (long) Math.floor(length / 16d),
                (long) Math.floor(width / 16d) * (long) Math.floor(length / 8d)));
    }

    private List<String> roomTargetGaps(List<RoomGeometry> rooms, PlanningParameterVariant variant) {
        if (variant == null || variant.roomTargets() == null || variant.roomTargets().isEmpty()) return List.of();
        var required = new LinkedHashMap<String, Integer>();
        for (var target : variant.roomTargets()) {
            if (!"REQUIRED".equals(target.priority())) continue;
            required.merge(target.floor() + "|" + target.roomType(), target.count(), Integer::sum);
        }
        var gaps = new ArrayList<String>();
        required.forEach((key, count) -> {
            var separator = key.indexOf('|');
            var floor = key.substring(0, separator);
            var type = key.substring(separator + 1);
            var represented = rooms.stream().filter(room -> floor.equals(normalizedFloor(room.floor())))
                    .filter(room -> roomMatchesTarget(room.type(), type)).count();
            if (represented < count) {
                gaps.add("Programme gap: " + represented + " of " + count + " required "
                        + type.toLowerCase(Locale.ROOT).replace('_', ' ') + " spaces represented on "
                        + floor.toLowerCase(Locale.ROOT) + " floor");
            }
        });
        return List.copyOf(gaps);
    }

    private boolean roomMatchesTarget(String actual, String target) {
        if (actual.equals(target)) return true;
        return "BEDROOM".equals(target) && actual.endsWith("BEDROOM");
    }

    private List<String> programmeExplanations(Strategy strategy, List<String> gaps) {
        return programmeExplanations(strategy, null, gaps);
    }

    private List<String> programmeExplanations(Strategy strategy, PlanningParameterVariant variant,
            List<String> gaps) {
        return programmeExplanations(strategy, variant, gaps, null);
    }

    /**
     * Why this option came out the way it did, led by what was measured on the drawing itself.
     *
     * <p>The strategy's own sentences describe an intention and are true of every project it is
     * applied to. The measured reasons describe this plan: how much of the plot it used, which
     * rooms ended up connected, how many have a window. A customer choosing between three options
     * needs the second kind to tell them apart.</p>
     */
    private List<String> programmeExplanations(Strategy strategy, PlanningParameterVariant variant,
            List<String> gaps, CandidateScore score) {
        var explanations = new ArrayList<String>();
        if (score != null) explanations.addAll(score.reasons());
        explanations.addAll(strategy.explanations());
        if (variant != null && variant.explanations() != null) explanations.addAll(variant.explanations());
        gaps.forEach(gap -> explanations.add("Professional review required - " + gap));
        return List.copyOf(explanations);
    }

    private PlanningParameterVariant variantFor(PlanningParameterSet parameters, String strategy) {
        if (parameters == null || parameters.variants() == null) return null;
        return parameters.variants().stream().filter(value -> strategy.equals(value.strategy()))
                .findFirst().orElse(null);
    }

    private HomeParameters optionParameters(HomeParameters requested, PlanningParameterVariant variant) {
        if (variant == null) return requested;
        // These fields are explicit customer selections. Parameter variants may recommend room
        // sizes and priorities, but must never silently add a balcony, terrace, courtyard or lift.
        return requested;
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    List<String> validate(double plotWidth, double plotLength, List<RoomGeometry> rooms) {
        var violations = new ArrayList<String>();
        for (var room : rooms) {
            if (room.width() <= 0 || room.length() <= 0) {
                violations.add(room.id() + " has unusable geometry");
            }
            if (room.x() < 0 || room.y() < 0 || room.x() + room.width() > plotWidth + .01
                    || room.y() + room.length() > plotLength + .01) {
                violations.add(room.id() + " escapes the plot boundary");
            }
        }
        for (int left = 0; left < rooms.size(); left++) {
            for (int right = left + 1; right < rooms.size(); right++) {
                if (sameFloor(rooms.get(left), rooms.get(right)) && overlaps(rooms.get(left), rooms.get(right))) {
                    violations.add(rooms.get(left).id() + " overlaps " + rooms.get(right).id());
                }
            }
        }
        return List.copyOf(violations);
    }

    /**
     * Confirms no room encroaches on required open space.
     *
     * <p>The packer is handed a rectangle that already sits inside the setback envelope, so this is
     * a regression guard rather than an expected failure path. It is checked on every candidate
     * because a layout that quietly crosses a setback line is the one defect a customer cannot see
     * in the rendered plan.</p>
     */
    List<String> validateEnvelope(BuildableEnvelope envelope, List<RoomGeometry> rooms) {
        if (envelope == null || envelope.buildableOutline().size() < 3) {
            return List.of();
        }
        var violations = new ArrayList<String>();
        var outline = envelope.buildableOutline();
        for (var room : rooms) {
            if (!countsAsBuiltUp(room)) {
                // Courtyards, terraces and open parking are allowed to sit in open space.
                continue;
            }
            if (!rectangleInside(outline, room)) {
                violations.add(room.id() + " crosses the required setback line");
            }
        }
        return List.copyOf(violations);
    }

    private boolean rectangleInside(List<PlotVertex> outline, RoomGeometry room) {
        var inset = 0.02d;
        if (room.shaped()) {
            // A room that follows the boundary stands its wall on it, so its corners lie on the
            // line rather than inside it. What has to hold is that no part of the room crosses:
            // every corner is on or inside, and so is the middle of every wall between them, which
            // is where a room bulging out between two legal corners would show.
            var corners = room.corners();
            for (var index = 0; index < corners.size(); index++) {
                var corner = corners.get(index);
                var next = corners.get((index + 1) % corners.size());
                if (!PlotGeometry.insideOrOn(outline, corner.x(), corner.y(), inset)
                        || !PlotGeometry.insideOrOn(outline, (corner.x() + next.x()) / 2,
                                (corner.y() + next.y()) / 2, inset)) {
                    return false;
                }
            }
            return true;
        }
        var left = room.x() + inset;
        var right = room.x() + room.width() - inset;
        var bottom = room.y() + inset;
        var top = room.y() + room.length() - inset;
        if (right <= left || top <= bottom) {
            return true;
        }
        return PlotGeometry.containsPoint(outline, left, bottom)
                && PlotGeometry.containsPoint(outline, right, bottom)
                && PlotGeometry.containsPoint(outline, left, top)
                && PlotGeometry.containsPoint(outline, right, top);
    }

    List<String> validateDocument(int requestedFloors, List<RoomGeometry> rooms,
            List<Map<String, Object>> doors, List<Map<String, Object>> windows) {
        var violations = new ArrayList<String>();
        var expectedFloors = new LinkedHashSet<String>();
        for (var index = 0; index < requestedFloors; index++) expectedFloors.add(floorName(index));
        var representedFloors = new LinkedHashSet<String>();
        var roomIds = new LinkedHashSet<String>();
        var roomsById = new LinkedHashMap<String, RoomGeometry>();
        for (var room : rooms) {
            var floor = normalizedFloor(room.floor());
            representedFloors.add(floor);
            if (!roomIds.add(room.id())) violations.add("Duplicate room id " + room.id());
            roomsById.putIfAbsent(room.id(), room);
        }
        if (!representedFloors.equals(expectedFloors)) {
            violations.add("Geometry floors " + representedFloors + " do not match requested floors " + expectedFloors);
        }
        validateStairCores(expectedFloors, rooms, violations);
        validateLiftCores(expectedFloors, rooms, violations);
        var envelopes = new LinkedHashMap<String, Envelope>();
        for (var floor : representedFloors) {
            envelopes.put(floor, envelopeFor(rooms.stream()
                    .filter(room -> floor.equals(normalizedFloor(room.floor()))).toList()));
        }
        validateDoors(doors, roomsById, envelopes, violations);
        validateWindows(windows, roomsById, envelopes, rooms, violations);
        validateOpeningSeparation(doors, windows, violations);
        validateConnectivity(rooms, doors, violations);
        return List.copyOf(violations);
    }

    private void validateStairCores(LinkedHashSet<String> expectedFloors, List<RoomGeometry> rooms,
            List<String> violations) {
        if (expectedFloors.size() == 1) {
            var stairs = rooms.stream().filter(room -> "STAIRCASE".equals(room.type())).count();
            if (stairs > 0) violations.add("A single-storey home must not contain an internal stair core");
            return;
        }
        RoomGeometry reference = null;
        for (var floor : expectedFloors) {
            var stairs = rooms.stream()
                    .filter(room -> floor.equals(normalizedFloor(room.floor())))
                    .filter(room -> "STAIRCASE".equals(room.type()))
                    .toList();
            if (stairs.size() != 1) {
                violations.add(floor + " floor requires exactly one STAIRCASE; found " + stairs.size());
                continue;
            }
            if (reference == null) {
                reference = stairs.getFirst();
            } else if (!sameBounds(reference, stairs.getFirst())) {
                violations.add("Stair cores are not vertically aligned: " + reference.id()
                        + " differs from " + stairs.getFirst().id());
            }
        }
    }

    private void validateLiftCores(LinkedHashSet<String> expectedFloors, List<RoomGeometry> rooms,
            List<String> violations) {
        var lifts = rooms.stream().filter(room -> "LIFT_SHAFT".equals(room.type())).toList();
        if (lifts.isEmpty()) return;
        RoomGeometry reference = null;
        for (var floor : expectedFloors) {
            var floorLifts = lifts.stream()
                    .filter(room -> floor.equals(normalizedFloor(room.floor()))).toList();
            if (floorLifts.size() != 1) {
                violations.add(floor + " floor requires exactly one aligned LIFT_SHAFT; found "
                        + floorLifts.size());
                continue;
            }
            if (reference == null) reference = floorLifts.getFirst();
            else if (!sameBounds(reference, floorLifts.getFirst())) {
                violations.add("Lift shafts are not vertically aligned: " + reference.id()
                        + " differs from " + floorLifts.getFirst().id());
            }
        }
    }

    private boolean sameBounds(RoomGeometry first, RoomGeometry second) {
        return Math.abs(first.x() - second.x()) <= .02
                && Math.abs(first.y() - second.y()) <= .02
                && Math.abs(first.width() - second.width()) <= .02
                && Math.abs(first.length() - second.length()) <= .02;
    }

    private void validateDoors(List<Map<String, Object>> doors, Map<String, RoomGeometry> roomsById,
            Map<String, Envelope> envelopes, List<String> violations) {
        var ids = new LinkedHashSet<String>();
        var physicalOpenings = new LinkedHashSet<String>();
        var sharedRoomEdges = new LinkedHashSet<String>();
        var exteriorDoorCounts = new LinkedHashMap<String, Integer>();
        for (var opening : doors) {
            var id = String.valueOf(opening.get("id"));
            var roomId = String.valueOf(opening.get("roomId"));
            var floor = normalizedFloor(opening.get("floor") == null ? null : opening.get("floor").toString());
            if (!ids.add(id)) violations.add("Duplicate door id " + id);
            var room = roomsById.get(roomId);
            if (room == null) {
                violations.add("door " + id + " references missing room " + roomId);
                continue;
            }
            if (!normalizedFloor(room.floor()).equals(floor)) {
                violations.add("door " + id + " is not on the same floor as " + roomId);
            }
            var orientation = orientation(opening);
            if (orientation == null) {
                violations.add("door " + id + " has invalid orientation " + opening.get("orientation"));
                continue;
            }
            var x = number(opening.get("x"));
            var y = number(opening.get("y"));
            var width = number(opening.get("width"));
            validateOpeningOnRoom("door", id, room, orientation, x, y, width, violations);
            var physicalKey = physicalOpeningKey(floor, orientation, x, y);
            if (!physicalOpenings.add(physicalKey)) {
                violations.add("Duplicate physical door opening at " + physicalKey);
            }

            var connectedId = opening.get("connectsRoomId") == null ? null
                    : opening.get("connectsRoomId").toString();
            if (connectedId == null || connectedId.isBlank()) {
                exteriorDoorCounts.merge(floor, 1, Integer::sum);
                var envelope = envelopes.get(floor);
                if (envelope == null || !touchesEnvelope(room, envelope, orientation)) {
                    violations.add("door " + id + " is not on the exterior building envelope");
                }
                continue;
            }
            var connected = roomsById.get(connectedId);
            if (connected == null) {
                violations.add("door " + id + " references missing room " + connectedId);
                continue;
            }
            if (!normalizedFloor(connected.floor()).equals(floor)) {
                violations.add("door " + id + " is not on the same floor as " + connectedId);
            }
            var edge = sharedEdge(room, connected);
            if (edge == null || !edge.orientationFrom(room).equals(orientation)
                    || !doorFitsSharedEdge(edge, x, y, width)) {
                violations.add("door " + id + " is not contained by the shared room edge");
            }
            var pairKey = floor + "|" + (roomId.compareTo(connectedId) < 0
                    ? roomId + "|" + connectedId : connectedId + "|" + roomId);
            if (!sharedRoomEdges.add(pairKey)) {
                violations.add("Duplicate door on shared room edge " + pairKey);
            }
        }
        if (exteriorDoorCounts.getOrDefault("GROUND", 0) != 1) {
            violations.add("Ground floor requires exactly one exterior entrance door");
        }
        for (var floor : envelopes.keySet()) {
            if (!"GROUND".equals(floor) && exteriorDoorCounts.getOrDefault(floor, 0) != 0) {
                violations.add(floor + " floor must connect through the stair core, not an exterior entrance");
            }
        }
    }

    private void validateWindows(List<Map<String, Object>> windows, Map<String, RoomGeometry> roomsById,
            Map<String, Envelope> envelopes, List<RoomGeometry> rooms, List<String> violations) {
        var ids = new LinkedHashSet<String>();
        var physicalOpenings = new LinkedHashSet<String>();
        for (var opening : windows) {
            var id = String.valueOf(opening.get("id"));
            var roomId = String.valueOf(opening.get("roomId"));
            var floor = normalizedFloor(opening.get("floor") == null ? null : opening.get("floor").toString());
            if (!ids.add(id)) violations.add("Duplicate window id " + id);
            var room = roomsById.get(roomId);
            if (room == null) {
                violations.add("window " + id + " references missing room " + roomId);
                continue;
            }
            if (!normalizedFloor(room.floor()).equals(floor)) {
                violations.add("window " + id + " is not on the same floor as " + roomId);
            }
            var orientation = orientation(opening);
            if (orientation == null) {
                violations.add("window " + id + " has invalid orientation " + opening.get("orientation"));
                continue;
            }
            var x = number(opening.get("x"));
            var y = number(opening.get("y"));
            var width = number(opening.get("width"));
            validateOpeningOnRoom("window", id, room, orientation, x, y, width, violations);
            var envelope = envelopes.get(floor);
            // A window belongs on a wall with daylight behind it. That is the outside of the
            // building, or — for a house built to its boundaries, where most walls are party walls —
            // a courtyard or terrace the plan opened inside itself for exactly this reason.
            var floorRooms = rooms.stream()
                    .filter(other -> floor.equals(normalizedFloor(other.floor()))).toList();
            if ((envelope == null || !touchesEnvelope(room, envelope, orientation))
                    && !lightWellSides(room, floorRooms).contains(orientation)) {
                violations.add("window " + id + " opens onto neither the outside nor a light well");
            }
            var physicalKey = physicalOpeningKey(floor, orientation, x, y);
            if (!physicalOpenings.add(physicalKey)) {
                violations.add("Duplicate physical window opening at " + physicalKey);
            }
        }
    }

    private void validateOpeningSeparation(List<Map<String, Object>> doors, List<Map<String, Object>> windows,
            List<String> violations) {
        for (var door : doors) {
            var doorInterval = openingInterval(door);
            if (doorInterval == null) continue;
            for (var window : windows) {
                var windowInterval = openingInterval(window);
                if (windowInterval != null && openingIntervalsOverlap(doorInterval, windowInterval)) {
                    violations.add("Door " + doorInterval.id() + " overlaps window " + windowInterval.id()
                            + " on the same wall");
                }
            }
        }
    }

    private void validateOpeningOnRoom(String kind, String id, RoomGeometry room, String orientation,
            double x, double y, double width, List<String> violations) {
        var horizontal = "NORTH".equals(orientation) || "SOUTH".equals(orientation);
        var wallLength = horizontal ? room.width() : room.length();
        if (!Double.isFinite(width) || width <= 0 || width > wallLength + .01) {
            violations.add(kind + " " + id + " has invalid width");
        }
        var expectedAxis = wallLine(room, orientation);
        var actualAxis = horizontal ? y : x;
        var position = horizontal ? x : y;
        var minimum = horizontal ? room.x() : room.y();
        var maximum = minimum + wallLength;
        if (!Double.isFinite(actualAxis) || Math.abs(actualAxis - expectedAxis) > .02
                || !Double.isFinite(position) || position - width / 2 < minimum - .02
                || position + width / 2 > maximum + .02) {
            violations.add(kind + " " + id + " is not contained by the referenced room perimeter");
        }
    }

    private boolean doorFitsSharedEdge(SharedEdge edge, double x, double y, double width) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width)) return false;
        var axis = edge.vertical() ? x : y;
        var expectedAxis = edge.vertical() ? edge.x() : edge.y();
        var position = edge.vertical() ? y : x;
        return Math.abs(axis - expectedAxis) <= .02
                && position - width / 2 >= edge.from() - .02
                && position + width / 2 <= edge.to() + .02;
    }

    private String orientation(Map<String, Object> opening) {
        if (opening.get("orientation") == null) return null;
        var orientation = opening.get("orientation").toString().toUpperCase(Locale.ROOT);
        return List.of("NORTH", "SOUTH", "EAST", "WEST").contains(orientation) ? orientation : null;
    }

    private String physicalOpeningKey(String floor, String orientation, double x, double y) {
        var axis = "NORTH".equals(orientation) || "SOUTH".equals(orientation) ? "H" : "V";
        return floor + "|" + axis + "|" + round2(x) + "|" + round2(y);
    }

    private OpeningInterval openingInterval(Map<String, Object> opening) {
        var orientation = orientation(opening);
        var x = number(opening.get("x"));
        var y = number(opening.get("y"));
        var width = number(opening.get("width"));
        if (orientation == null || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(width) || width <= 0) return null;
        var horizontal = "NORTH".equals(orientation) || "SOUTH".equals(orientation);
        var center = horizontal ? x : y;
        return new OpeningInterval(
                normalizedFloor(opening.get("floor") == null ? null : opening.get("floor").toString()),
                horizontal ? "H" : "V", horizontal ? y : x, center - width / 2, center + width / 2,
                String.valueOf(opening.get("id")));
    }

    private boolean openingIntervalsOverlap(OpeningInterval first, OpeningInterval second) {
        return first.floor().equals(second.floor()) && first.axis().equals(second.axis())
                && Math.abs(first.wallLine() - second.wallLine()) <= .02
                && Math.max(first.from(), second.from()) < Math.min(first.to(), second.to()) - .02;
    }

    private void validateConnectivity(List<RoomGeometry> rooms, List<Map<String, Object>> doors,
            List<String> violations) {
        for (var floor : rooms.stream().map(room -> normalizedFloor(room.floor())).distinct().toList()) {
            var floorRooms = rooms.stream().filter(room -> floor.equals(normalizedFloor(room.floor()))).toList();
            var graph = new LinkedHashMap<String, LinkedHashSet<String>>();
            floorRooms.forEach(room -> graph.put(room.id(), new LinkedHashSet<>()));
            String entranceRoot = null;
            for (var door : doors) {
                if (!floor.equals(normalizedFloor(door.get("floor") == null ? null : door.get("floor").toString()))) {
                    continue;
                }
                var roomId = String.valueOf(door.get("roomId"));
                var connected = door.get("connectsRoomId") == null ? null : door.get("connectsRoomId").toString();
                if (connected == null || connected.isBlank()) {
                    entranceRoot = roomId;
                } else if (graph.containsKey(roomId) && graph.containsKey(connected)) {
                    graph.get(roomId).add(connected);
                    graph.get(connected).add(roomId);
                }
            }
            var root = "GROUND".equals(floor) ? entranceRoot
                    : floorRooms.stream().filter(room -> "STAIRCASE".equals(room.type()))
                            .map(RoomGeometry::id).findFirst().orElse(null);
            if (root == null || !graph.containsKey(root)) {
                violations.add(floor + " floor has no entrance/stair circulation root");
                continue;
            }
            var visited = new LinkedHashSet<String>();
            var queue = new ArrayList<String>();
            visited.add(root);
            queue.add(root);
            for (var cursor = 0; cursor < queue.size(); cursor++) {
                for (var adjacent : graph.get(queue.get(cursor))) {
                    if (visited.add(adjacent)) queue.add(adjacent);
                }
            }
            if (visited.size() != floorRooms.size()) {
                violations.add(floor + " floor circulation graph is disconnected; reached " + visited.size()
                        + " of " + floorRooms.size() + " rooms");
            }
        }
    }

    private double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return Double.NaN;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    /**
     * Plans every storey through the programme-driven {@link FloorPlanner}.
     *
     * <p>The strategy contributes only the two proportions it is entitled to — how the plate splits
     * either side of the circulation spine, and how deep the public band on the road runs. Room
     * sizing itself belongs to the planner, because a strategy that could stretch a bedroom past the
     * dimensions it is usable at would be choosing a look over a home.</p>
     */
    private FloorPlanner plannerFor(BuildableEnvelope envelope, Facing roadFacing, int floorCount,
            Strategy strategy, Recommendation recommendation, HomeParameters parameters,
            PlanningParameterVariant variant) {
        return new FloorPlanner(envelope, roadFacing, floorCount, strategy.columnSplit(),
                strategy.rowSplit(), recommendation, parameters, variant);
    }

    /** Floor area an extension room aims for, matched to the ordinary rooms beside it. */
    private static final double EXTENSION_ROOM_AREA = 150d;
    /** Shortest side an extension room may be drawn at, in feet. */
    private static final double EXTENSION_MINIMUM_SIDE = 7d;
    /** Most rooms one extension zone is worth splitting into. */
    private static final int MAX_EXTENSION_ROOMS = 4;
    /**
     * What the ground floor gains from land beside the packed rectangle, most useful first.
     *
     * <p>Deliberately none of the counted programme. The recommendation fixes the bedroom and
     * bathroom count from the household, so an extension bedroom would not read as the plot serving
     * the family better — it would read as the layout disagreeing with its own brief.</p>
     */
    private static final List<String> GROUND_EXTENSION_TYPES =
            List.of("FAMILY_LOUNGE", "HOME_OFFICE", "MULTIPURPOSE_ROOM", "STORE");
    /** The same for an upper storey, where the useful additions are study and shared space. */
    private static final List<String> UPPER_EXTENSION_TYPES =
            List.of("FAMILY_LOUNGE", "STUDY", "MULTIPURPOSE_ROOM", "STORE");

    /**
     * Rooms for the ground the packed rectangle could not reach.
     *
     * <p>The packer plans rectangles, so an irregular plot has always been served by planning the
     * largest one and calling the remainder unusable. Under full plot usage that remainder is
     * exactly what the customer asked to build on, so each extension zone is subdivided into rooms
     * and handed to the same door and window passes as the rest of the storey — which is what
     * connects them to the house rather than leaving them stranded beside it.</p>
     *
     * <p>Zones repeat on every storey. They are plot geometry, not a ground-floor decision, and a
     * storey that stopped short of them would leave the floor above overhanging open air.</p>
     */
    private List<RoomGeometry> extensionRooms(BuildableEnvelope envelope,
            List<RoomGeometry> planned, int floorCount) {
        if (envelope.extensionZones().isEmpty()) {
            return planned;
        }
        var rooms = new ArrayList<>(planned);
        for (var floorIndex = 0; floorIndex < floorCount; floorIndex++) {
            var floor = floorName(floorIndex);
            var index = 1 + (int) planned.stream()
                    .filter(room -> normalizedFloor(room.floor()).equals(floor)).count();
            var types = floorIndex == 0 ? GROUND_EXTENSION_TYPES : UPPER_EXTENSION_TYPES;
            var taken = 0;
            for (var zone : envelope.extensionZones()) {
                var pieces = subdivideZone(zone);
                if (pieces.isEmpty()) {
                    // Too narrow to be a room, so it becomes depth on the rooms already facing it.
                    var onFloor = rooms.stream()
                            .filter(room -> normalizedFloor(room.floor()).equals(floor)).toList();
                    var grown = absorbStrip(onFloor, zone);
                    rooms.removeAll(onFloor);
                    rooms.addAll(grown);
                    continue;
                }
                for (var piece : pieces) {
                    var type = extensionTypeFor(piece, types, taken);
                    taken++;
                    rooms.add(room(floorIndex, index++, type,
                            piece.x(), piece.y(), piece.width(), piece.length()));
                }
            }
        }
        return List.copyOf(rooms);
    }

    /**
     * Hands leftover ground too narrow to be a room to the rooms standing along it.
     *
     * <p>A strip four feet wide is not a room, but it is still floor the customer asked to build on.
     * It is split at the edges of the rooms that face it and each share is given to the room it
     * touches, so a strip becomes depth on the rooms beside it rather than a slot of nothing. The
     * split is what keeps every room rectangular: a room only ever grows across the full width of
     * its own edge, so its new outline is still a rectangle.</p>
     *
     * <p>Any part of the strip no room faces stays unbuilt. That is the margin against a slanted
     * boundary, and it belongs to the plot rather than to a room that cannot reach it.</p>
     */
    private List<RoomGeometry> absorbStrip(List<RoomGeometry> rooms, PlotGeometry.Rect strip) {
        var grown = new ArrayList<RoomGeometry>(rooms.size());
        for (var room : rooms) {
            grown.add(growInto(room, strip));
        }
        return grown;
    }

    /** The room extended over whatever share of the strip lies against one of its four walls. */
    private RoomGeometry growInto(RoomGeometry room, PlotGeometry.Rect strip) {
        var left = room.x();
        var bottom = room.y();
        var right = room.x() + room.width();
        var top = room.y() + room.length();
        var stripRight = strip.x() + strip.width();
        var stripTop = strip.y() + strip.length();
        // The strip has to cover the room's whole wall, not merely touch it. A room reaching past the
        // end of the strip could only take it by turning an L, and growing the rectangle regardless
        // would carry it over ground the strip never covered — off the plot, where the leg ends.
        var withinRows = bottom >= strip.y() - GAP && top <= stripTop + GAP;
        var withinColumns = left >= strip.x() - GAP && right <= stripRight + GAP;
        var newLeft = withinRows && Math.abs(left - stripRight) <= GAP ? strip.x() : left;
        var newRight = withinRows && Math.abs(right - strip.x()) <= GAP ? stripRight : right;
        var newBottom = withinColumns && Math.abs(bottom - stripTop) <= GAP ? strip.y() : bottom;
        var newTop = withinColumns && Math.abs(top - strip.y()) <= GAP ? stripTop : top;
        // A room may only take the strip on one axis. Growing on two would claim the corner twice,
        // and the second room along the strip would be drawn straight through it.
        if (newLeft != left || newRight != right) {
            newBottom = bottom;
            newTop = top;
        }
        if (newLeft == left && newRight == right && newBottom == bottom && newTop == top) {
            return room;
        }
        // The share is clipped to the room's own span, so what it gains is exactly the rectangle in
        // front of it and never a neighbour's.
        var width = round2(newRight - newLeft);
        var length = round2(newTop - newBottom);
        return new RoomGeometry(room.id(), room.type(), round2(newLeft), round2(newBottom),
                width, length, round2(width * length), room.floor());
    }

    /** Tolerance for two edges being the same line, in feet. */
    private static final double GAP = .05d;

    /**
     * Lets the rooms on the edge of the plan follow a slanted boundary instead of stopping square
     * of it.
     *
     * <p>Rectangles cannot be flush against a diagonal, so a tapered plot always kept a wedge of
     * unbuilt ground down each side — on a forty by sixty taper that is a quarter of the plot. The
     * room standing there is given the boundary's own line for its outer wall: its share of the plot
     * is exactly the plot clipped to its frontage, slant included, which is the shape an architect
     * would draw and the only one that reaches the corner.</p>
     *
     * <p>Rooms with nothing but plot beyond them are the ones that move; everything behind them
     * stays the rectangle it was. Ground is claimed one room at a time and a claim is withdrawn from
     * anything already standing on it, because on a cut corner the same wedge lies beyond two rooms
     * at once and checking only who stands there now let both of them take all of it — which drew
     * the second room straight through the first.</p>
     */
    private List<RoomGeometry> followBoundary(List<RoomGeometry> rooms, List<PlotVertex> plot) {
        var bounds = PlotGeometry.bounds(plot);
        // What every room stands on right now. A room that has already taken its share of the margin
        // is entered here as the shape it took, so the next room reaching for the same ground finds
        // it occupied instead of being drawn straight through it.
        var settled = new LinkedHashMap<String, RoomGeometry>();
        rooms.forEach(room -> settled.put(room.id(), room));
        for (var room : claimOrder(rooms)) {
            var others = settled.values().stream()
                    .filter(other -> !other.id().equals(room.id()) && sameFloor(other, room))
                    .toList();
            var left = blocked(room, others, Side.WEST) ? room.x() : bounds.minimumX();
            var right = blocked(room, others, Side.EAST) ? room.x() + room.width() : bounds.maximumX();
            var bottom = blocked(room, others, Side.SOUTH) ? room.y() : bounds.minimumY();
            var top = blocked(room, others, Side.NORTH) ? room.y() + room.length() : bounds.maximumY();
            // Nothing stands between this room and the boundary on two sides at once, so both edges
            // reach past the corner and two rooms sharing that corner would each claim all of it.
            // The claim is withdrawn from whichever edge costs it least until it stands clear.
            var claim = withdrawFrom(room, others, new double[] {left, bottom, right, top});
            if (claim[0] == room.x() && claim[1] == room.y()
                    && claim[2] == room.x() + room.width() && claim[3] == room.y() + room.length()) {
                continue;
            }
            var clipped = PlotGeometry.clipToRect(plot, claim[0], claim[1], claim[2], claim[3]);
            // Counter-clockwise, always. Anything walking the ring for wall surfaces — the massing
            // view most of all — takes the outward face from the direction each edge runs, so a ring
            // that came back wound the other way would turn every wall of the room inside out.
            var ring = !clipped.isEmpty() && PlotGeometry.signedArea(clipped) < 0
                    ? new ArrayList<>(clipped).reversed()
                    : clipped;
            var area = ring.isEmpty() ? 0 : Math.abs(PlotGeometry.signedArea(ring));
            // Only worth reshaping when it actually recovers ground, and never when the clip came
            // back as something that no longer contains the room it started as.
            if (ring.isEmpty() || area <= room.area() + .5 || !containsRoom(ring, room)) {
                continue;
            }
            var box = PlotGeometry.bounds(ring);
            settled.put(room.id(), new RoomGeometry(room.id(), room.type(), round2(box.minimumX()),
                    round2(box.minimumY()), round2(box.width()), round2(box.length()),
                    round2(area), room.floor(), ring));
        }
        // Rebuilt in the order the planner produced, because the door pass reads rooms in plan order.
        return rooms.stream().map(room -> settled.get(room.id())).toList();
    }

    /**
     * The order rooms are offered the margin in: enclosed rooms before outdoor programme, and the
     * larger room first within each.
     *
     * <p>Whoever is offered a piece of ground first takes it, so the order is what decides who gets
     * the corner of a tapered plot. A bedroom standing against the slant should have it before the
     * balcony beside it does; a balcony stretched to four times the size it was planned at is no
     * longer a balcony, and the family loses the room the ground could have been.</p>
     */
    private List<RoomGeometry> claimOrder(List<RoomGeometry> rooms) {
        return rooms.stream().sorted(java.util.Comparator
                        .comparing((RoomGeometry room) -> RoomSpec.isOutdoor(room.type()))
                        .thenComparing(java.util.Comparator.comparingDouble(RoomGeometry::area).reversed())
                        .thenComparing(RoomGeometry::id))
                .toList();
    }

    /**
     * Pulls a claim back off every room already standing on the ground it reaches over.
     *
     * <p>Only ever gives up ground the room did not start with: the room's own rectangle is kept
     * whole, so withdrawing can shrink what a room gains but never what it already had.</p>
     */
    private double[] withdrawFrom(RoomGeometry room, List<RoomGeometry> others, double[] claim) {
        // Each pass surrenders at most one edge per neighbour, and there are only four edges to
        // surrender, so the claim cannot keep shrinking indefinitely.
        for (var pass = 0; pass < 4; pass++) {
            var clear = true;
            for (var other : others) {
                if (!reachesOver(claim, other)) continue;
                clear = false;
                claim = withdrawEdge(room, other, claim);
            }
            if (clear) return claim;
        }
        return new double[] {room.x(), room.y(), room.x() + room.width(), room.y() + room.length()};
    }

    /** The claim with the one edge given up that clears this neighbour and costs the least ground. */
    private double[] withdrawEdge(RoomGeometry room, RoomGeometry other, double[] claim) {
        var candidates = List.of(
                new double[] {Math.max(claim[0], other.x() + other.width()), claim[1], claim[2], claim[3]},
                new double[] {claim[0], Math.max(claim[1], other.y() + other.length()), claim[2], claim[3]},
                new double[] {claim[0], claim[1], Math.min(claim[2], other.x()), claim[3]},
                new double[] {claim[0], claim[1], claim[2], Math.min(claim[3], other.y())});
        double[] best = null;
        var bestArea = -1d;
        for (var candidate : candidates) {
            if (!holdsRoom(candidate, room) || reachesOver(candidate, other)) continue;
            var area = (candidate[2] - candidate[0]) * (candidate[3] - candidate[1]);
            if (area > bestArea) {
                bestArea = area;
                best = candidate;
            }
        }
        // No edge clears this neighbour while keeping the room whole, which means the two started
        // out overlapping. Nothing this pass does can fix that, so the room simply stays as it was.
        return best == null
                ? new double[] {room.x(), room.y(), room.x() + room.width(), room.y() + room.length()}
                : best;
    }

    /** True when the claim would be drawn over ground this room is standing on. */
    private boolean reachesOver(double[] claim, RoomGeometry other) {
        return claim[0] < other.x() + other.width() && claim[2] > other.x()
                && claim[1] < other.y() + other.length() && claim[3] > other.y();
    }

    /** True when the claim still covers the whole rectangle the room was planned as. */
    private boolean holdsRoom(double[] claim, RoomGeometry room) {
        return claim[0] <= room.x() + GAP && claim[1] <= room.y() + GAP
                && claim[2] >= room.x() + room.width() - GAP
                && claim[3] >= room.y() + room.length() - GAP;
    }

    private enum Side { NORTH, SOUTH, EAST, WEST }

    /** True when another room on the same storey stands between this one and the boundary. */
    private boolean blocked(RoomGeometry room, List<RoomGeometry> others, Side side) {
        for (var other : others) {
            var sharesRows = other.y() < room.y() + room.length() - GAP
                    && other.y() + other.length() > room.y() + GAP;
            var sharesColumns = other.x() < room.x() + room.width() - GAP
                    && other.x() + other.width() > room.x() + GAP;
            var stands = switch (side) {
                case WEST -> sharesRows && other.x() < room.x() + GAP;
                case EAST -> sharesRows && other.x() + other.width() > room.x() + room.width() - GAP;
                case SOUTH -> sharesColumns && other.y() < room.y() + GAP;
                case NORTH -> sharesColumns && other.y() + other.length() > room.y() + room.length() - GAP;
            };
            if (stands) return true;
        }
        return false;
    }

    /** Guards against a clip that wandered off and returned a region the room is not even in. */
    private boolean containsRoom(List<PlotVertex> ring, RoomGeometry room) {
        var inset = .02d;
        return PlotGeometry.containsPoint(ring, room.x() + inset, room.y() + inset)
                && PlotGeometry.containsPoint(ring, room.x() + room.width() - inset, room.y() + inset)
                && PlotGeometry.containsPoint(ring, room.x() + inset, room.y() + room.length() - inset)
                && PlotGeometry.containsPoint(ring, room.x() + room.width() - inset,
                        room.y() + room.length() - inset);
    }

    /** Splits one zone into rooms, or into nothing when it is too narrow to hold one. */
    /**
     * The room an extension piece is actually the size of.
     *
     * <p>Taking the next type off the list regardless of the piece it lands on is what produced a
     * 239 sq ft study and a 144 sq ft store — both rooms the catalogue caps at 170 and 90, drawn at
     * half as much again because the ground happened to be there. A room is named for what it can
     * be furnished as, so the piece chooses the type rather than the other way round.</p>
     *
     * <p>Preference still runs down the list, so a plot with several zones gets a lounge before a
     * store rather than four of the same room; {@code taken} only rotates the starting point among
     * the types that fit. When the piece is larger than every candidate the roomiest one is used —
     * {@link #subdivideZone} has already split it as far as it usefully splits — and when it is
     * smaller than all of them the tightest one is, which is the store the leftover really is.</p>
     */
    String extensionTypeFor(PlotGeometry.Rect piece, List<String> types, int taken) {
        var across = Math.min(piece.width(), piece.length());
        var along = Math.max(piece.width(), piece.length());
        var area = piece.area();
        var fitting = new ArrayList<String>();
        for (var type : types) {
            var spec = RoomSpec.of(type);
            if (across + .01 >= spec.minShortSide() && along + .01 >= spec.minLongSide()
                    && area + .5 >= spec.minArea() && area <= spec.maxArea() + .5) {
                fitting.add(type);
            }
        }
        if (!fitting.isEmpty()) {
            return fitting.get(taken % fitting.size());
        }
        // Nothing fits the piece exactly. Too large for every band means the roomiest type is the
        // honest name for it; too small means the tightest one is.
        var roomiest = types.stream().max(Comparator.comparingDouble(type -> RoomSpec.of(type).maxArea()));
        var tightest = types.stream().min(Comparator.comparingDouble(type -> RoomSpec.of(type).minArea()));
        var oversized = roomiest.map(type -> area > RoomSpec.of(type).maxArea()).orElse(false);
        return (oversized ? roomiest : tightest).orElse("MULTIPURPOSE_ROOM");
    }

    private List<PlotGeometry.Rect> subdivideZone(PlotGeometry.Rect zone) {
        if (Math.min(zone.width(), zone.length()) < EXTENSION_MINIMUM_SIDE) {
            return List.of();
        }
        var pieces = new ArrayList<PlotGeometry.Rect>();
        bisectZone(zone, pieces);
        return List.copyOf(pieces);
    }

    /**
     * Halves a zone across its longer side until each piece is about the size of a room.
     *
     * <p>Halving rather than slicing into equal strips: slicing a chunky zone one way produces a
     * rank of identical galleries, where halving alternates its own axis and arrives at rooms with
     * the proportions rooms are actually furnished at. A twenty by thirty leg becomes four fifteen
     * by ten rooms rather than four seven-and-a-half by twenty corridors.</p>
     */
    private void bisectZone(PlotGeometry.Rect zone, List<PlotGeometry.Rect> into) {
        var horizontal = zone.width() >= zone.length();
        var along = horizontal ? zone.width() : zone.length();
        if (into.size() >= MAX_EXTENSION_ROOMS - 1
                || zone.area() <= EXTENSION_ROOM_AREA * 1.5
                || along / 2 < EXTENSION_MINIMUM_SIDE) {
            into.add(zone);
            return;
        }
        var half = round2(along / 2);
        if (horizontal) {
            bisectZone(new PlotGeometry.Rect(zone.x(), zone.y(), half, zone.length()), into);
            bisectZone(new PlotGeometry.Rect(round2(zone.x() + half), zone.y(),
                    round2(zone.width() - half), zone.length()), into);
        } else {
            bisectZone(new PlotGeometry.Rect(zone.x(), zone.y(), zone.width(), half), into);
            bisectZone(new PlotGeometry.Rect(zone.x(), round2(zone.y() + half), zone.width(),
                    round2(zone.length() - half)), into);
        }
    }


    private boolean overlaps(RoomGeometry left, RoomGeometry right) {
        var epsilon = .01;
        return left.x() < right.x() + right.width() - epsilon
                && left.x() + left.width() > right.x() + epsilon
                && left.y() < right.y() + right.length() - epsilon
                && left.y() + left.length() > right.y() + epsilon;
    }

    private boolean sameFloor(RoomGeometry left, RoomGeometry right) {
        return normalizedFloor(left.floor()).equals(normalizedFloor(right.floor()));
    }

    private RoomGeometry room(int floorIndex, int roomIndex, String type,
            double x, double y, double width, double length) {
        var floor = floorName(floorIndex);
        // Both corners are snapped to the same 2-decimal grid, and the extents are derived from the
        // snapped corners. Rounding x and width independently lets a shared wall drift by up to a
        // hundredth in each direction, which is the same order as the overlap tolerance, so two
        // rooms meeting on an exact line could be reported as overlapping. Snapping the far corner
        // instead makes an abutting pair share one identical coordinate.
        var left = round2(x);
        var bottom = round2(y);
        var right = round2(x + width);
        var top = round2(y + length);
        var snappedWidth = right - left;
        var snappedLength = top - bottom;
        return new RoomGeometry(floorPrefix(floorIndex) + "-R" + roomIndex, type,
                left, bottom, snappedWidth, snappedLength, round2(snappedWidth * snappedLength), floor);
    }

    private String floorName(int floorIndex) {
        return switch (floorIndex) {
            case 0 -> "GROUND";
            case 1 -> "FIRST";
            default -> "SECOND";
        };
    }

    private String floorPrefix(int floorIndex) {
        return switch (floorIndex) {
            case 0 -> "G";
            case 1 -> "F1";
            default -> "F2";
        };
    }

    private String normalizedFloor(String floor) {
        return floor == null || floor.isBlank() ? "GROUND" : floor.toUpperCase(Locale.ROOT);
    }

    /**
     * Places one door per room, choosing which wall it goes on the way a plan would.
     *
     * <p>A spanning tree over the shared walls is what keeps every room reachable and stops two
     * doors appearing on one wall. Which tree, though, is the difference between a home and a maze:
     * breadth-first order used to hang a bathroom door off whichever room happened to be reached
     * first, so a family walked through a bedroom to reach the toilet. The tree is therefore grown
     * cheapest-edge first against {@link #doorCost}, which prefers the passage for habitable rooms
     * and the parent bedroom for a bathroom that belongs to it.</p>
     */
    private List<Map<String, Object>> doorsFor(List<RoomGeometry> rooms, Facing roadFacing) {
        var doors = new ArrayList<Map<String, Object>>();
        for (var floor : rooms.stream().map(RoomGeometry::floor).distinct().toList()) {
            var floorRooms = rooms.stream().filter(room -> floor.equals(room.floor())).toList();
            var envelope = envelopeFor(floorRooms);
            var ground = "GROUND".equals(floor);
            var root = ground ? entranceRoom(floorRooms, envelope, roadFacing)
                    : floorRooms.stream().filter(room -> "STAIRCASE".equals(room.type())).findFirst()
                            .orElse(floorRooms.getFirst());
            var doorNumber = 0;
            if (ground) {
                doors.add(exteriorDoor(root, envelope, roadFacing, floorPrefixFor(floor) + "-D" + doorNumber++));
            }

            var edges = sharedEdges(floorRooms);
            var connected = new LinkedHashSet<String>();
            connected.add(root.id());
            while (connected.size() < floorRooms.size()) {
                SharedEdge best = null;
                RoomGeometry bestFrom = null;
                RoomGeometry bestTo = null;
                var bestCost = Double.MAX_VALUE;
                for (var edge : edges) {
                    var firstIn = connected.contains(edge.first().id());
                    var secondIn = connected.contains(edge.second().id());
                    if (firstIn == secondIn) continue;
                    var from = firstIn ? edge.first() : edge.second();
                    var to = firstIn ? edge.second() : edge.first();
                    // A wider shared wall is the more natural place for a door, so it breaks ties
                    // without ever outweighing the room-type preference itself.
                    var cost = doorCost(from, to) - Math.min(edge.span(), 12) * .01;
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = edge;
                        bestFrom = from;
                        bestTo = to;
                    }
                }
                if (best == null) break;
                doors.add(internalDoor(best, bestFrom, bestTo,
                        floorPrefixFor(floor) + "-D" + doorNumber++));
                connected.add(bestTo.id());
            }
        }
        return List.copyOf(doors);
    }

    /**
     * How willing a plan should be to reach {@code to} by cutting a door out of {@code from}.
     *
     * <p>Lower is better. The ordering encodes the circulation rules a drawing is read against: a
     * private bathroom opens off the bedroom it serves, habitable rooms open off the passage, and
     * nothing is entered through a bedroom or a parking bay if any other wall will do.</p>
     */
    /** The rooms that legitimately open off a bedroom, because they belong to it. */
    private static final java.util.Set<String> BEDROOM_SUITE =
            java.util.Set.of("ATTACHED_BATHROOM", "DRESSING_ROOM", "STORE", "BALCONY", "TERRACE");

    private double doorCost(RoomGeometry from, RoomGeometry to) {
        var fromType = from.type();
        var toType = to.type();
        if ("ATTACHED_BATHROOM".equals(toType)) {
            if (fromType.endsWith("BEDROOM")) return 0;
            return RoomSpec.CORRIDOR.equals(fromType) ? 40 : 30;
        }
        if ("ATTACHED_BATHROOM".equals(fromType)) return 60;
        if (RoomSpec.CORRIDOR.equals(toType) || RoomSpec.CORRIDOR.equals(fromType)) return 5;
        if (fromType.endsWith("BEDROOM")) {
            // Through a bedroom you reach that bedroom's own rooms. Anything else — a guest WC, a
            // store, the stair — is a route through somebody's private room, and the tree should
            // take any other way round before it takes this one. Costed rather than forbidden
            // because a room with no other neighbour still has to be reachable somehow, and a plan
            // that leaves it sealed is worse than one that reports an awkward door.
            return BEDROOM_SUITE.contains(toType) ? 35 : 400;
        }
        if (RoomSpec.isOutdoor(fromType)) return 25;
        if ("LIVING_ROOM".equals(fromType) || "DINING".equals(fromType)
                || "FAMILY_LOUNGE".equals(fromType)) return 10;
        return 20;
    }

    private Map<String, Object> exteriorDoor(RoomGeometry room, Envelope envelope, Facing preferred,
            String id) {
        var orientation = touchesEnvelope(room, envelope, preferred.name())
                ? preferred.name() : exteriorSides(room, envelope).getFirst();
        var horizontal = "NORTH".equals(orientation) || "SOUTH".equals(orientation);
        var wallLength = horizontal ? room.width() : room.length();
        var width = round2(Math.min(room.type().contains("PARKING") ? 8.0 : 3.6,
                Math.max(2.4, wallLength * .32)));
        var door = opening(id, room, orientation,
                horizontal ? room.x() + room.width() / 2 : wallLine(room, orientation),
                horizontal ? wallLine(room, orientation) : room.y() + room.length() / 2,
                width);
        door.put("exterior", true);
        return Map.copyOf(door);
    }

    private Map<String, Object> internalDoor(SharedEdge edge, RoomGeometry from, RoomGeometry to, String id) {
        var orientation = edge.orientationFrom(from);
        var door = opening(id, from, orientation, edge.x(), edge.y(),
                round2(Math.min(3.0, Math.max(2.4, edge.span() * .35))));
        door.put("connectsRoomId", to.id());
        door.put("exterior", false);
        return Map.copyOf(door);
    }

    private LinkedHashMap<String, Object> opening(String id, RoomGeometry room, String orientation,
            double x, double y, double width) {
        var opening = new LinkedHashMap<String, Object>();
        opening.put("id", id);
        opening.put("roomId", room.id());
        opening.put("floor", room.floor());
        opening.put("x", round2(x));
        opening.put("y", round2(y));
        opening.put("width", round2(width));
        opening.put("orientation", orientation);
        var doorMarker = id.lastIndexOf("-D");
        if (doorMarker >= 0) {
            opening.put("swing", Integer.parseInt(id.substring(doorMarker + 2)) % 2 == 0
                    ? "LEFT" : "RIGHT");
        }
        return opening;
    }

    private RoomGeometry entranceRoom(List<RoomGeometry> rooms, Envelope envelope, Facing facing) {
        return rooms.stream()
                .filter(room -> touchesEnvelope(room, envelope, facing.name()))
                .sorted((left, right) -> Integer.compare(entrancePriority(left), entrancePriority(right)))
                .findFirst()
                .orElseGet(() -> rooms.stream().filter(room -> !exteriorSides(room, envelope).isEmpty())
                        .findFirst().orElse(rooms.getFirst()));
    }

    /**
     * Which room the front door should open into.
     *
     * <p>The living room comes first: a home is entered through its hall, and the porch or parking
     * bay beside it is the approach rather than the threshold. Parking is still accepted when the
     * plan puts nothing else on the road, which is what a plot too narrow for both leaves.</p>
     */
    private int entrancePriority(RoomGeometry room) {
        if (room.type().contains("LIVING")) return 0;
        if (room.type().contains("PARKING") || "PORCH".equals(room.type())) return 1;
        if (room.type().contains("LOUNGE") || room.type().contains("DINING")) return 2;
        if (RoomSpec.CORRIDOR.equals(room.type())) return 3;
        if (room.type().contains("KITCHEN")) return 4;
        if (room.type().contains("BEDROOM")) return 5;
        return 6;
    }

    private List<SharedEdge> sharedEdges(List<RoomGeometry> rooms) {
        var edges = new ArrayList<SharedEdge>();
        for (var leftIndex = 0; leftIndex < rooms.size(); leftIndex++) {
            for (var rightIndex = leftIndex + 1; rightIndex < rooms.size(); rightIndex++) {
                var edge = sharedEdge(rooms.get(leftIndex), rooms.get(rightIndex));
                if (edge != null && edge.span() >= 2.4) edges.add(edge);
            }
        }
        edges.sort((left, right) -> (left.first().id() + "|" + left.second().id())
                .compareTo(right.first().id() + "|" + right.second().id()));
        return List.copyOf(edges);
    }

    private SharedEdge sharedEdge(RoomGeometry first, RoomGeometry second) {
        var tolerance = .02;
        if (Math.abs(first.x() + first.width() - second.x()) <= tolerance
                || Math.abs(second.x() + second.width() - first.x()) <= tolerance) {
            var from = Math.max(first.y(), second.y());
            var to = Math.min(first.y() + first.length(), second.y() + second.length());
            if (to - from < 2.4) return null;
            var firstOnLeft = first.x() < second.x();
            var x = firstOnLeft ? first.x() + first.width() : first.x();
            return new SharedEdge(first, second, firstOnLeft ? "EAST" : "WEST",
                    round2(x), round2((from + to) / 2), round2(to - from), round2(from), round2(to), true);
        }
        if (Math.abs(first.y() + first.length() - second.y()) <= tolerance
                || Math.abs(second.y() + second.length() - first.y()) <= tolerance) {
            var from = Math.max(first.x(), second.x());
            var to = Math.min(first.x() + first.width(), second.x() + second.width());
            if (to - from < 2.4) return null;
            // +y is north, so the room with the smaller y sits south of the other one.
            var firstIsSouthern = first.y() < second.y();
            var y = firstIsSouthern ? first.y() + first.length() : first.y();
            return new SharedEdge(first, second, firstIsSouthern ? "NORTH" : "SOUTH",
                    round2((from + to) / 2), round2(y), round2(to - from), round2(from), round2(to), false);
        }
        return null;
    }

    /**
     * Spaces a room can take light and air from across a shared wall.
     *
     * <p>These are the open spaces the plan carves inside itself, which is where a house built to
     * its boundaries has to get its daylight from.</p>
     */
    private static final java.util.Set<String> LIGHT_WELLS =
            java.util.Set.of("COURTYARD", "COURTYARD_PARKING", "OPEN_SPACE", "TERRACE", "BALCONY");

    private List<Map<String, Object>> windowsFor(List<RoomGeometry> rooms,
            List<Map<String, Object>> doors, java.util.Set<String> openSides) {
        var windows = new ArrayList<Map<String, Object>>();
        for (var floor : rooms.stream().map(RoomGeometry::floor).distinct().toList()) {
            var floorRooms = rooms.stream().filter(room -> floor.equals(room.floor())).toList();
            var envelope = envelopeFor(floorRooms);
            var windowNumber = 1;
            for (var room : floorRooms) {
                if (room.type().contains("PARKING") || room.type().contains("STAIR")
                        || room.type().contains("LIFT") || room.type().contains("TERRACE")
                        || room.type().contains("BALCONY") || room.type().contains("COURTYARD")
                        || room.type().contains("OPEN_SPACE")) continue;
                // A wall on the plot boundary has the neighbour's building against it, so the light
                // has to come off the street or out of the plan's own courtyard instead.
                var sides = new ArrayList<>(exteriorSides(room, envelope).stream()
                        .filter(openSides::contains).toList());
                lightWellSides(room, floorRooms).forEach(side -> {
                    if (!sides.contains(side)) sides.add(side);
                });
                if (sides.isEmpty()) continue;
                var window = exteriorWindow(room, sides, doors,
                        floorPrefixFor(floor) + "-W" + windowNumber);
                if (window != null) {
                    windows.add(window);
                    windowNumber++;
                }
            }
        }
        return List.copyOf(windows);
    }

    /** Walls of this room that face a courtyard, terrace or balcony on the same storey. */
    private List<String> lightWellSides(RoomGeometry room, List<RoomGeometry> floorRooms) {
        var sides = new ArrayList<String>();
        for (var other : floorRooms) {
            if (!LIGHT_WELLS.contains(other.type())) continue;
            var edge = sharedEdge(room, other);
            if (edge != null && !sides.contains(edge.orientationFrom(room))) {
                sides.add(edge.orientationFrom(room));
            }
        }
        return List.copyOf(sides);
    }

    /**
     * Which compass sides of the building can carry an opening.
     *
     * <p>With open space all round, every wall is an outside wall.</p>
     *
     * <p>Building to the boundary is the other case, and it is how a street of city houses is
     * built: the two flank walls stand on the plot line and the neighbours' houses come up against
     * them, so they are party walls and carry nothing. The front and the back are the faces that
     * stay in the open — the road on one side and whatever the plot backs onto on the other — and
     * they are where a terraced house has always taken its light and air from.</p>
     */
    private java.util.Set<String> openSides(BuildableEnvelope envelope, Facing roadFacing) {
        if (!envelope.setbacks().waived()) {
            return java.util.Set.of("NORTH", "SOUTH", "EAST", "WEST");
        }
        var rear = switch (roadFacing) {
            case NORTH -> Facing.SOUTH;
            case SOUTH -> Facing.NORTH;
            case EAST -> Facing.WEST;
            case WEST -> Facing.EAST;
        };
        return java.util.Set.of(roadFacing.name(), rear.name());
    }

    private Map<String, Object> exteriorWindow(RoomGeometry room, List<String> sides,
            List<Map<String, Object>> doors, String id) {
        for (var orientation : sides) {
            var horizontal = "NORTH".equals(orientation) || "SOUTH".equals(orientation);
            var wallLength = horizontal ? room.width() : room.length();
            var width = round2(Math.min(4.0, Math.max(2.5, wallLength * .28)));
            var start = horizontal ? room.x() : room.y();
            for (var ratio : List.of(.20, .80, .35, .65, .50)) {
                var center = start + wallLength * ratio;
                if (center - width / 2 < start - .01 || center + width / 2 > start + wallLength + .01) {
                    continue;
                }
                var candidate = opening(id, room, orientation,
                        horizontal ? center : wallLine(room, orientation),
                        horizontal ? wallLine(room, orientation) : center,
                        width);
                candidate.remove("swing");
                var interval = openingInterval(candidate);
                var collides = interval != null && doors.stream().map(this::openingInterval)
                        .filter(Objects::nonNull)
                        .anyMatch(door -> openingIntervalsOverlap(door, interval));
                if (!collides) return Map.copyOf(candidate);
            }
        }
        return null;
    }

    private Envelope envelopeFor(List<RoomGeometry> rooms) {
        return new Envelope(
                rooms.stream().mapToDouble(RoomGeometry::x).min().orElse(0),
                rooms.stream().mapToDouble(room -> room.x() + room.width()).max().orElse(0),
                rooms.stream().mapToDouble(RoomGeometry::y).min().orElse(0),
                rooms.stream().mapToDouble(room -> room.y() + room.length()).max().orElse(0));
    }

    private List<String> exteriorSides(RoomGeometry room, Envelope envelope) {
        var sides = new ArrayList<String>();
        if (Math.abs(room.y() + room.length() - envelope.maximumY()) <= .02) sides.add("NORTH");
        if (Math.abs(room.x() + room.width() - envelope.maximumX()) <= .02) sides.add("EAST");
        if (Math.abs(room.y() - envelope.minimumY()) <= .02) sides.add("SOUTH");
        if (Math.abs(room.x() - envelope.minimumX()) <= .02) sides.add("WEST");
        return List.copyOf(sides);
    }

    /**
     * The wall line of a room on one compass side.
     *
     * <p>The planning grid grows north with {@code +y}, exactly as {@link PlotBoundary} documents,
     * so the north wall is the room's maximum y. Every opening coordinate, every validation check
     * and every renderer derives its side from here, which is what keeps the packed layout, the
     * setback envelope and the surveyed outline describing the same building.</p>
     */
    private double wallLine(RoomGeometry room, String orientation) {
        return switch (orientation) {
            case "NORTH" -> room.y() + room.length();
            case "SOUTH" -> room.y();
            case "EAST" -> room.x() + room.width();
            default -> room.x();
        };
    }

    private boolean touchesEnvelope(RoomGeometry room, Envelope envelope, String orientation) {
        return exteriorSides(room, envelope).contains(orientation);
    }

    private String floorPrefixFor(String floor) {
        return switch (normalizedFloor(floor)) {
            case "GROUND" -> "G";
            case "FIRST" -> "F1";
            default -> "F2";
        };
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }


    private record Strategy(String key, String name, double columnSplit, double rowSplit,
            int vastuScore, int naturalLightScore, int spaceEfficiencyScore, List<String> explanations) {}
    private record Envelope(double minimumX, double maximumX, double minimumY, double maximumY) {}
    private record OpeningInterval(String floor, String axis, double wallLine, double from, double to, String id) {}
    private record SharedEdge(RoomGeometry first, RoomGeometry second, String orientationFromFirst,
            double x, double y, double span, double from, double to, boolean vertical) {
        RoomGeometry other(RoomGeometry room) {
            if (first.id().equals(room.id())) return second;
            if (second.id().equals(room.id())) return first;
            return null;
        }

        String orientationFrom(RoomGeometry room) {
            if (first.id().equals(room.id())) return orientationFromFirst;
            return switch (orientationFromFirst) {
                case "NORTH" -> "SOUTH";
                case "SOUTH" -> "NORTH";
                case "EAST" -> "WEST";
                default -> "EAST";
            };
        }
    }
}

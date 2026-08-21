package com.avas.platform.project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plans a home the way a floor plan is actually drawn, rather than by cutting the plot into cells.
 *
 * <p>The earlier packer divided the buildable rectangle into a fixed three-by-three grid and named
 * the cells. That produces geometry that validates and nobody could build: bedrooms twenty-five feet
 * deep, a hundred-and-fifty square foot bathroom, a lift shaft two feet wide, and no circulation at
 * all — every room opened directly into the next, so reaching a bathroom meant walking through a
 * bedroom.</p>
 *
 * <p>This planner works from a room programme instead. Two decisions are made once for the whole
 * building, because walls have to stack and a stair has to land in the same place on every storey:
 * the transverse split into two room strips either side of a circulation spine, and the rectangle the
 * stair and lift occupy at the rear of one of them. Everything else is planned per floor, so each
 * storey is genuine floor-specific geometry.</p>
 *
 * <p>Within a strip every room is a run along the spine, sized from the area its type wants and
 * clamped to the dimensions that type is usable at. Surplus area goes to spaces that can absorb it
 * before it is allowed to inflate a bedroom; a plate too small to hold the programme drops optional
 * rooms rather than drawing unusable ones. Rooms tile their strip exactly, so the built-up area a
 * customer is quoted on is the area actually drawn.</p>
 *
 * <p>Nothing here is construction-ready. Wall thicknesses, structural spans, sanitary layouts and
 * every statutory dimension remain a licensed professional's work; the planner only refuses to place
 * a space at a size that is known to be unusable.</p>
 */
final class FloorPlanner {
    /** Clear width of the circulation spine. Below about three feet two people cannot pass. */
    private static final double CORRIDOR_WIDTH = 3.75d;
    /** Narrowest strip worth planning rooms in; under this the plate is planned as a single run. */
    private static final double MINIMUM_STRIP = 8.5d;
    /**
     * Share of the floor plates that stays unenclosed: covered parking, a courtyard, a terrace.
     *
     * <p>Mirrors the share the recommendation reserves when it sets the built-up target, so a plan
     * that spends this budget lands inside the cost basis it is measured against.</p>
     */
    static final double OUTDOOR_SHARE = 0.18d;
    /** Ceiling on how much of the plate depth the public band on the road may take. */
    private static final double MAXIMUM_FRONT_SHARE = 0.42d;
    /** Depth a car needs to stand clear, plus the wall it stands against. */
    private static final double PARKING_DEPTH = 16.5d;

    private final Facing facing;
    private final int floorCount;
    private final Recommendation recommendation;
    private final HomeParameters parameters;
    /**
     * The optimized room programme for this option, or {@code null} to plan from type defaults.
     *
     * <p>Until this was passed in, the parameter variants — the entire output of the planning
     * optimizer, AI or deterministic — reached the engine and were read in exactly one place, a
     * post-hoc audit that compared the drawing against them and printed the differences. They sized
     * nothing and placed nothing, so plot area, budget and household reached the optimizer, changed
     * its answer, and then changed no wall in the drawing.</p>
     */
    private final PlanningParameterVariant variant;
    private final double frontShare;
    private final boolean liftRequired;
    private final boolean stairRequired;

    /** Plate geometry, resolved once so every storey stacks on the same walls. */
    private final double plateX;
    private final double plateY;
    private final double plateWidth;
    private final double plateLength;
    private final double depth;
    private final double across;
    /**
     * True when the passage runs parallel to the road rather than away from it.
     *
     * <p>A plate much wider than it is deep has no room for a public band and a spine behind it: the
     * front band alone would eat the whole depth. Such a plot is built as a bar of rooms along the
     * frontage with the passage behind them, and planning it the other way round produced rooms
     * forty-four feet long and three feet deep.</p>
     */
    private final boolean spineAlongFrontage;
    /** Length of the run rooms are laid along, and the width the strips divide between them. */
    private final double runLength;
    private final double bandTotal;
    private final double corridorWidth;
    private final double leftWidth;
    private final double rightWidth;
    private final double corridorFrom;
    private final double coreRun;
    private final boolean coreOnLeft;
    private final double stairAcross;
    private final boolean liftPlaced;
    private final boolean singleRun;
    private final double outdoorBudget;
    private final List<String> notes = new ArrayList<>();

    FloorPlanner(BuildableEnvelope envelope, Facing facing, int floorCount, double stripSplit,
            double frontShare, Recommendation recommendation, HomeParameters parameters) {
        this(envelope, facing, floorCount, stripSplit, frontShare, recommendation, parameters, null);
    }

    FloorPlanner(BuildableEnvelope envelope, Facing facing, int floorCount, double stripSplit,
            double frontShare, Recommendation recommendation, HomeParameters parameters,
            PlanningParameterVariant variant) {
        this.facing = facing;
        this.floorCount = floorCount;
        this.recommendation = recommendation;
        this.parameters = parameters;
        this.variant = variant;
        this.frontShare = frontShare;
        this.stairRequired = floorCount > 1;
        this.liftRequired = floorCount > 1 && !"NONE".equals(parameters.liftProvision());

        this.plateX = envelope.footprintX();
        this.plateY = envelope.footprintY();
        this.plateWidth = envelope.footprintWidth();
        this.plateLength = envelope.footprintLength();
        var horizontalRoad = facing == Facing.NORTH || facing == Facing.SOUTH;
        this.depth = horizontalRoad ? plateLength : plateWidth;
        this.across = horizontalRoad ? plateWidth : plateLength;
        this.outdoorBudget = OUTDOOR_SHARE * plateWidth * plateLength * floorCount;

        // Rooms are always laid along the plate's longer axis and the strips divide the shorter one,
        // so the passage runs the length of the building however the plot is proportioned.
        this.spineAlongFrontage = across > depth * 1.25d;
        this.runLength = spineAlongFrontage ? across : depth;
        this.bandTotal = spineAlongFrontage ? depth : across;
        this.corridorWidth = round2(clamp(bandTotal * .18d, RoomSpec.of(RoomSpec.CORRIDOR).minShortSide(),
                CORRIDOR_WIDTH));

        var usableBand = bandTotal - corridorWidth;
        this.singleRun = usableBand < MINIMUM_STRIP * 2;
        if (singleRun) {
            // Too narrow for rooms either side of a spine: plan one run with the passage against a
            // wall. That is what a genuinely narrow plot gets built as.
            this.leftWidth = round2(Math.max(MINIMUM_STRIP * .6, usableBand));
            this.corridorFrom = this.leftWidth;
            this.rightWidth = 0d;
            notes.add("The buildable width is too narrow for rooms either side of a passage, so the "
                    + "layout is planned as a single run against one wall and needs professional review");
        } else {
            var left = clamp(usableBand * clamp(stripSplit, .38d, .62d),
                    MINIMUM_STRIP, usableBand - MINIMUM_STRIP);
            this.leftWidth = round2(left);
            this.corridorFrom = this.leftWidth;
            this.rightWidth = round2(bandTotal - this.leftWidth - corridorWidth);
        }

        // The core goes in the service strip when that strip can carry a stair, so the sleeping strip
        // keeps its width for bedrooms.
        var stair = staircaseFootprint(parameters.staircaseType());
        this.coreOnLeft = chooseCoreStrip(stair);
        var coreWidth = this.coreOnLeft ? this.leftWidth : this.rightWidth;
        var liftAcross = liftAcross(coreWidth);
        this.liftPlaced = liftRequired && carriesStairAndLift(coreWidth, stair);
        this.stairAcross = round2(liftPlaced ? coreWidth - liftAcross : coreWidth);
        // Sized against the whole run rather than what the public band leaves, so the two cannot
        // depend on each other and a shallow plot cannot end up with a negative spine.
        this.coreRun = round2(clamp(stair[1], Math.max(6d, RoomSpec.of("LIFT_SHAFT").minLongSide()),
                Math.max(6.5d, runLength * .30)));

        if (this.stairAcross + .01 < stair[0] || this.coreRun + .01 < stair[1]) {
            notes.add("The stair core is tighter than a " + parameters.staircaseType()
                    .toLowerCase(Locale.ROOT).replace('_', ' ') + " flight normally needs, so the rise, "
                    + "going and headroom must be confirmed by a structural designer");
        }
        if (liftRequired && !liftPlaced) {
            notes.add("The buildable width cannot carry a lift shaft beside the stair; the lift "
                    + "provision needs a professional to reposition the core");
        }
    }

    /**
     * Decides which strip carries the stair and lift.
     *
     * <p>The service strip is preferred so the sleeping strip keeps its full width for bedrooms, and
     * on a plate planned along its frontage the rear strip is preferred so the road side stays free
     * for the rooms that want the light. Either preference gives way to whether the strip can
     * physically hold the core, because a lift the plot has room for and the planner declined to
     * place is a promise broken for the sake of a tidier diagram.</p>
     */
    private boolean chooseCoreStrip(double[] stair) {
        if (rightWidth == 0) return true;
        var leftFits = liftRequired ? carriesStairAndLift(leftWidth, stair) : leftWidth + .01 >= stair[0];
        var rightFits = liftRequired ? carriesStairAndLift(rightWidth, stair) : rightWidth + .01 >= stair[0];
        if (leftFits != rightFits) return leftFits;
        // Both work, or neither does: fall back to the preference.
        if (spineAlongFrontage) return false;
        var narrowOnLeft = leftWidth <= rightWidth;
        return narrowOnLeft;
    }

    private double liftAcross(double stripWidth) {
        return liftRequired
                ? clamp(stripWidth * .34, RoomSpec.of("LIFT_SHAFT").minShortSide(), 6d) : 0d;
    }

    private boolean carriesStairAndLift(double stripWidth, double[] stair) {
        return stripWidth - liftAcross(stripWidth) >= stair[0] * .5;
    }

    List<String> notes() {
        return List.copyOf(notes);
    }

    /** Every room of every storey, planned against one shared set of stacked walls. */
    List<RoomGeometry> planBuilding() {
        var rooms = new ArrayList<RoomGeometry>();
        var bedrooms = bedroomAllocation();
        var attached = attachedBathroomAllocation(bedrooms);
        for (var floorIndex = 0; floorIndex < floorCount; floorIndex++) {
            rooms.addAll(planFloor(floorIndex, bedrooms.get(floorIndex), attached.get(floorIndex)));
        }
        return List.copyOf(rooms);
    }

    // ---------------------------------------------------------------------------------------
    // Programme
    // ---------------------------------------------------------------------------------------

    /** Bedrooms per storey. The ground floor keeps one so a senior or guest never faces the stair. */
    private List<Integer> bedroomAllocation() {
        var target = Math.max(1, Math.min(6, recommendation.bedrooms()));
        var result = new ArrayList<Integer>();
        if (floorCount == 1) {
            result.add(target);
            return List.copyOf(result);
        }
        var ground = Math.max(1, target - (floorCount - 1) * 3);
        result.add(ground);
        var remaining = target - ground;
        for (var floor = 1; floor < floorCount; floor++) {
            var remainingFloors = floorCount - floor;
            var count = Math.min(3, (int) Math.ceil(remaining / (double) remainingFloors));
            result.add(count);
            remaining -= count;
        }
        return List.copyOf(result);
    }

    /** Attached bathrooms per storey, spent on the sleeping floors first. */
    private List<Integer> attachedBathroomAllocation(List<Integer> bedrooms) {
        var budget = Math.max(0, recommendation.attachedBathrooms());
        var result = new ArrayList<Integer>();
        for (var index = 0; index < bedrooms.size(); index++) result.add(0);
        var order = new ArrayList<Integer>();
        // Upper floors first: the master suite is there, and a ground-floor guest or senior bedroom
        // is the one that most often shares the common bathroom in a real house.
        for (var index = bedrooms.size() - 1; index >= 0; index--) order.add(index);
        var spent = 0;
        var progressing = true;
        while (spent < budget && progressing) {
            progressing = false;
            for (var index : order) {
                if (spent >= budget) break;
                if (result.get(index) < bedrooms.get(index)) {
                    result.set(index, result.get(index) + 1);
                    spent++;
                    progressing = true;
                }
            }
        }
        return List.copyOf(result);
    }

    /** Outdoor area the ground floor should carry: covered parking, a courtyard, or an open sit-out. */
    private double groundOutdoorArea() {
        var terraceShare = topFloorTerraceArea();
        var ground = Math.max(0, outdoorBudget - terraceShare);
        if (parameters.parkingCars() > 0) {
            var bays = Math.min(parameters.parkingCars(), 3) * RoomSpec.of("PARKING").preferredArea();
            return clamp(ground, Math.min(bays, RoomSpec.of("PARKING").minArea()), bays * 1.35);
        }
        return clamp(ground, 0d, RoomSpec.of("COURTYARD").maxArea());
    }

    private double topFloorTerraceArea() {
        if (floorCount < 2 || !parameters.terraceRequired()) return 0d;
        return clamp(outdoorBudget * .45, RoomSpec.of("TERRACE").minArea(),
                RoomSpec.of("TERRACE").maxArea());
    }

    /**
     * Depth of the public band on the road, for one storey.
     *
     * <p>Free to differ floor to floor: the stair sits a fixed run in from the rear wall, so it
     * stacks whatever the front of each storey does.</p>
     */
    private double frontDepth(int floorIndex, double outdoorArea) {
        var leadType = floorIndex == 0 ? "LIVING_ROOM" : "FAMILY_LOUNGE";
        var living = RoomSpec.of(leadType);
        // The band's depth follows the space that leads it, so the programme has to be read here
        // too: sizing the public band from the type catalogue while the rooms inside it were sized
        // from the programme meant the largest room in the house came out the same width whatever
        // the plot and the budget could afford.
        var wanted = (wantedArea(floorIndex, leadType) + (floorIndex == 0 ? outdoorArea : 0)) / bandTotal;
        var minimum = living.minShortSide();
        if (floorIndex == 0 && parameters.parkingCars() > 0 && outdoorArea > 0) {
            minimum = Math.max(minimum, PARKING_DEPTH);
        }
        // The spine has to keep the core and at least one usable room behind the public band. A plot
        // too shallow for that gets a shallower front and reports the bays it could not fit, rather
        // than a band that eats the rooms behind it.
        var spineFloor = coreRun + RoomSpec.of("BEDROOM").minShortSide();
        var ceiling = Math.min(Math.min(runLength * MAXIMUM_FRONT_SHARE, floorIndex == 0 ? 21d : 14d),
                Math.max(runLength * .25, runLength - spineFloor));
        // The strategy nudges the public band within the band it is allowed, rather than setting it.
        var nudged = wanted * clamp(frontShare * 2.2d, .88d, 1.14d);
        return round2(clamp(nudged, Math.min(minimum, ceiling), Math.max(ceiling, living.minShortSide() * .6)));
    }

    /**
     * The rooms one storey should contain, in the order they sit along the spine.
     *
     * <p>Public spaces face the road, service spaces sit on one side of the passage and sleeping
     * spaces on the other, and every bathroom that belongs to a bedroom is placed immediately behind
     * it so the two share a wall. That ordering is the reason the plan reads as a home rather than as
     * a list of rectangles.</p>
     */
    private FloorProgramme programme(int floorIndex, int bedroomCount, int attachedCount) {
        var ground = floorIndex == 0;
        var topFloor = floorIndex == floorCount - 1;
        var front = new ArrayList<Request>();
        var sleeping = new ArrayList<Request>();
        var service = new ArrayList<Request>();

        if (ground) {
            var outdoor = groundOutdoorArea();
            if (outdoor > 1) front.add(new Request(groundOutdoorType(), outdoor));
            front.add(want(floorIndex, "LIVING_ROOM"));
            // Dining leads the sleeping strip so it opens straight off the living room, the way a
            // living-dining runs in a real plan, and the kitchen faces it across the passage.
            sleeping.add(want(floorIndex, "DINING"));
            // A bungalow has no upper floor to put the family room on, so it belongs here.
            if (floorCount == 1 && recommendation.familyLounge()) {
                sleeping.add(want(floorIndex, "FAMILY_LOUNGE"));
            }
            service.add(want(floorIndex, "KITCHEN"));
            service.add(want(floorIndex, "UTILITY"));
            service.add(want(floorIndex, "BATHROOM"));
        } else {
            // A terrace belongs over the ground-floor porch, on the road frontage, not buried
            // between rooms where it would be an open shaft nobody can reach the edge of.
            if (topFloor && parameters.terraceRequired() && topFloorTerraceArea() > 1) {
                front.add(new Request("TERRACE", topFloorTerraceArea()));
            }
            for (var index = 0; index < balconiesOnFloor(floorIndex); index++) {
                front.add(want(floorIndex, "BALCONY"));
            }
            front.add(want(floorIndex,
                    recommendation.familyLounge() ? "FAMILY_LOUNGE" : "MULTIPURPOSE_ROOM"));
            service.add(want(floorIndex, "BATHROOM"));
            service.add(want(floorIndex, floorIndex == 1 ? "STUDY" : "HOME_OFFICE"));
            service.add(want(floorIndex, "STORE"));
        }

        // Bedrooms, each followed by its own bathroom so the pair shares a wall.
        var attachedLeft = attachedCount;
        for (var index = 0; index < bedroomCount; index++) {
            sleeping.add(want(floorIndex, bedroomType(floorIndex, index)));
            if (attachedLeft > 0) {
                sleeping.add(want(floorIndex, "ATTACHED_BATHROOM"));
                attachedLeft--;
            }
        }
        if (sleeping.isEmpty()) sleeping.add(want(floorIndex, "FLEX_ROOM"));
        return new FloorProgramme(front, sleeping, service);
    }

    /**
     * A request for one space, sized from the optimized programme where it names that space.
     *
     * <p>Looked up against the floor it is being placed on, because the same type is legitimately a
     * different size upstairs: the ground-floor bathroom of an accessible home is larger than the
     * one beside a first-floor bedroom, and the programme says so.</p>
     *
     * <p>Whatever the programme asks for is clamped into the band {@link RoomSpec} holds for the
     * type. That is the line between an optimizer and a drawing: a proposal may prefer a 500 sq ft
     * kitchen or a 40 sq ft bedroom, and neither is a room this engine will draw. Falling back to
     * the type's own preferred area when the programme is silent keeps a planner built without a
     * variant — every existing caller — producing exactly the geometry it produced before.</p>
     */
    private Request want(int floorIndex, String type) {
        return new Request(type, wantedArea(floorIndex, type));
    }

    /**
     * Area this space should be planned at on this floor: the programme's, or the type's own.
     *
     * <p>Always clamped into the {@link RoomSpec} band, so however a proposal is arrived at the
     * drawing only ever contains sizes the space is usable at.</p>
     */
    private double wantedArea(int floorIndex, String type) {
        var spec = RoomSpec.of(type);
        var target = targetArea(floorIndex, type);
        return Double.isNaN(target) ? spec.preferredArea()
                : clamp(target, spec.minArea(), spec.maxArea());
    }

    /** Area the programme asks for this type on this floor, or {@code NaN} when it does not name it. */
    private double targetArea(int floorIndex, String type) {
        if (variant == null || variant.roomTargets() == null) return Double.NaN;
        var floor = floorName(floorIndex);
        Double sameFloor = null;
        Double anyFloor = null;
        for (var target : variant.roomTargets()) {
            if (!type.equalsIgnoreCase(target.roomType())) continue;
            if (floor.equalsIgnoreCase(target.floor())) {
                sameFloor = target.targetAreaSqFt();
                break;
            }
            if (anyFloor == null) anyFloor = target.targetAreaSqFt();
        }
        var resolved = sameFloor != null ? sameFloor : anyFloor;
        return resolved == null ? Double.NaN : resolved;
    }

    private String groundOutdoorType() {
        if (parameters.parkingCars() > 0) {
            return parameters.courtyardRequired() ? "COURTYARD_PARKING" : "PARKING";
        }
        return parameters.courtyardRequired() ? "COURTYARD" : "OPEN_SPACE";
    }

    private String bedroomType(int floorIndex, int index) {
        if (floorIndex == 0) {
            if (recommendation.seniorCitizenBedroom() && index == 0) return "SENIOR_BEDROOM";
            return floorCount == 1 && index == 0 ? "MASTER_BEDROOM" : "BEDROOM";
        }
        return floorIndex == 1 && index == 0 ? "MASTER_BEDROOM" : "BEDROOM";
    }

    private int balconiesOnFloor(int floorIndex) {
        var upperFloors = Math.max(1, floorCount - 1);
        var base = parameters.balconyCount() / upperFloors;
        var extra = parameters.balconyCount() % upperFloors;
        var upperIndex = Math.max(0, floorIndex - 1);
        return base + (upperIndex < extra ? 1 : 0);
    }

    // ---------------------------------------------------------------------------------------
    // Placement
    // ---------------------------------------------------------------------------------------

    private List<RoomGeometry> planFloor(int floorIndex, int bedroomCount, int attachedCount) {
        var programme = programme(floorIndex, bedroomCount, attachedCount);
        // The public band only exists when the spine runs away from the road. On a wide shallow
        // plate the public rooms simply lead the strip that faces the road instead.
        var frontRun = spineAlongFrontage ? 0d : frontDepth(floorIndex, groundOutdoor(floorIndex));
        var spineRun = round2(runLength - frontRun);
        var placed = new ArrayList<Placement>();

        if (frontRun > .05) {
            placed.addAll(placePublicBand(programme.front(), frontRun));
        }
        if (spineRun > .05) {
            placed.add(new Placement(RoomSpec.CORRIDOR, frontRun, corridorFrom, spineRun, corridorWidth));
        }

        // The two room strips, each one run of rooms along the spine. The strip at band offset zero
        // is the one against the road, so when there is no public band it takes the public rooms —
        // and the sleeping rooms with them, so bedrooms get the frontage rather than the back wall.
        var sleepingOnLeft = spineAlongFrontage || !coreOnLeft || singleRun;
        var leftRequests = new ArrayList<>(sleepingOnLeft ? programme.sleeping() : programme.service());
        var rightRequests = new ArrayList<>(sleepingOnLeft ? programme.service() : programme.sleeping());
        if (spineAlongFrontage) {
            leftRequests.addAll(0, programme.front());
        }
        if (singleRun) {
            leftRequests.addAll(rightRequests);
            rightRequests.clear();
        } else {
            var sleepingList = sleepingOnLeft ? leftRequests : rightRequests;
            var serviceList = sleepingOnLeft ? rightRequests : leftRequests;
            var coreOnSleeping = sleepingOnLeft == coreOnLeft;
            var core = stairRequired || liftPlaced ? coreRun : 0;
            spillSleepingRooms(sleepingList, serviceList,
                    sleepingOnLeft ? leftWidth : rightWidth, sleepingOnLeft ? rightWidth : leftWidth,
                    spineRun - (coreOnSleeping ? core : 0), spineRun - (coreOnSleeping ? 0 : core));
        }
        placed.addAll(placeStrip(leftRequests, frontRun, spineRun, 0, leftWidth, coreOnLeft));
        if (!singleRun) {
            placed.addAll(placeStrip(rightRequests, frontRun, spineRun,
                    round2(corridorFrom + corridorWidth), rightWidth, !coreOnLeft));
        }

        var rooms = new ArrayList<RoomGeometry>();
        var index = 1;
        for (var placement : placed) {
            // Last guard before the geometry leaves the planner: a rectangle with no extent is not
            // a room, and every consumer downstream is entitled to assume it never sees one.
            if (placement.runRun() <= .05 || placement.bandRun() <= .05) {
                notes.add(roomLabel(placement.type()) + " could not be given a usable size on the "
                        + floorName(floorIndex).toLowerCase(Locale.ROOT) + " floor and was left out");
                continue;
            }
            var box = toPlot(placement);
            rooms.add(room(floorIndex, index++, placement.type(), box[0], box[1], box[2], box[3]));
        }
        return rooms;
    }

    private double groundOutdoor(int floorIndex) {
        return floorIndex == 0 ? groundOutdoorArea() : 0d;
    }

    /**
     * Moves bedrooms across the passage when the sleeping strip alone cannot hold them.
     *
     * <p>A plate wide enough for two good strips holds more bedrooms across the passage than along
     * one side of it, which is how a wide plot is really planned. Without this the surplus bedrooms
     * are simply dropped and a forty-by-sixty duplex comes back a bedroom short of its own brief
     * while half the service strip stands empty.</p>
     *
     * <p>A bedroom always travels with the bathroom that belongs to it, and the master never moves:
     * it is the room the sleeping strip exists for.</p>
     */
    private void spillSleepingRooms(List<Request> sleeping, List<Request> service, double sleepingWidth,
            double serviceWidth, double sleepingAvailable, double serviceAvailable) {
        for (var guard = 0; guard < 6; guard++) {
            if (slotMinimum(sleeping, sleepingWidth, true) <= sleepingAvailable + .01) return;
            var last = -1;
            for (var index = sleeping.size() - 1; index >= 0; index--) {
                var type = sleeping.get(index).type();
                if (RoomSpec.isBedroom(type) && !"MASTER_BEDROOM".equals(type)) {
                    last = index;
                    break;
                }
            }
            if (last < 0) return;
            var moving = new ArrayList<Request>();
            moving.add(sleeping.get(last));
            if (last + 1 < sleeping.size() && "ATTACHED_BATHROOM".equals(sleeping.get(last + 1).type())) {
                moving.add(sleeping.get(last + 1));
            }
            var trial = new ArrayList<>(service);
            trial.addAll(moving);
            if (slotMinimum(trial, serviceWidth, true) > serviceAvailable + .01) {
                // The other strip cannot take it either; leave the decision to the fitting pass,
                // which drops the least important space and says so.
                return;
            }
            for (var index = moving.size() - 1; index >= 0; index--) sleeping.remove(last + index);
            service.addAll(moving);
        }
    }

    /**
     * Divides the public band across the frontage, giving each space a usable share.
     *
     * <p>The band's main habitable room — the living room, or the lounge upstairs — is positioned to
     * sit across the head of the passage and widened until it covers it. Everything downstream
     * depends on that overlap: it is the wall the door between the public band and the circulation
     * spine is cut into, so without it a family would reach the passage through the parking bay or
     * the terrace.</p>
     *
     * <p>The band is filled the same way the room strips are. A wide frontage carrying only the two
     * or three rooms the programme names has to stretch one of them across the surplus, which is how
     * a forty-foot lounge ten feet deep gets drawn; giving the band another usable space instead is
     * both a better plan and the reason the filler list exists.</p>
     */
    private List<Placement> placePublicBand(List<Request> requests, double bandRun) {
        if (requests.isEmpty()) return List.of();
        var fitted = fitToRun(requests, bandRun, bandTotal, true);
        var slots = fitted.stream().map(request -> new Slot(List.of(request), bandRun)).toList();
        var widths = shareSlots(slots, bandTotal);
        // The anchor is always the last space added to the band by the programme.
        var anchor = fitted.size() - 1;
        var order = anchoredOrder(fitted, widths, anchor);

        var placements = new ArrayList<Placement>();
        var cursor = 0d;
        for (var position = 0; position < order.size(); position++) {
            var index = order.get(position);
            var end = position == order.size() - 1 ? bandTotal : round2(cursor + widths[index]);
            placements.add(new Placement(fitted.get(index).type(), 0, cursor, bandRun,
                    round2(end - cursor)));
            cursor = end;
        }
        return placements;
    }

    /**
     * Orders the public band so its anchor room lands over the passage, widening it if it falls short.
     *
     * <p>Only the running order is open, so the band still tiles the frontage exactly however the
     * rooms are arranged.</p>
     */
    private List<Integer> anchoredOrder(List<Request> requests, double[] widths, int anchor) {
        var others = new ArrayList<Integer>();
        for (var index = 0; index < requests.size(); index++) if (index != anchor) others.add(index);
        var corridorEnd = corridorFrom + corridorWidth;
        var wantedOverlap = Math.min(corridorWidth, 2.6d);

        // Slack is what the neighbours could give up without dropping below their own usable width,
        // so each running order is scored on the overlap it could reach rather than the one it
        // happens to start with.
        var slack = 0d;
        for (var index : others) {
            slack += Math.max(0, widths[index] - RoomSpec.of(requests.get(index).type()).minShortSide());
        }
        var bestInsert = 0;
        var bestOverlap = -1d;
        var leading = 0d;
        for (var insert = 0; insert <= others.size(); insert++) {
            var reach = leading <= corridorFrom ? widths[anchor] + slack : widths[anchor];
            var overlap = Math.min(leading + reach, corridorEnd) - Math.max(leading, corridorFrom);
            if (overlap > bestOverlap + .01) {
                bestOverlap = overlap;
                bestInsert = insert;
            }
            if (insert < others.size()) leading += widths[others.get(insert)];
        }
        var anchorStart = 0d;
        for (var position = 0; position < bestInsert; position++) {
            anchorStart += widths[others.get(position)];
        }
        bestOverlap = Math.min(anchorStart + widths[anchor], corridorEnd) - Math.max(anchorStart, corridorFrom);

        if (bestOverlap < wantedOverlap && !others.isEmpty()) {
            // Reaching back to the near edge of the passage is what the anchor has to be wide enough
            // for; once it starts inside the passage, width alone can no longer buy the overlap.
            var needed = anchorStart <= corridorFrom
                    ? corridorFrom + wantedOverlap - anchorStart : wantedOverlap;
            var growth = Math.max(0, needed - widths[anchor]);
            // The neighbours give the space up widest first, never below their own usable width.
            var donors = new ArrayList<>(others);
            donors.sort((left, right) -> Double.compare(widths[right], widths[left]));
            for (var donor : donors) {
                if (growth <= 0) break;
                var floor = RoomSpec.of(requests.get(donor).type()).minShortSide();
                var given = Math.min(growth, Math.max(0, widths[donor] - floor));
                widths[donor] -= given;
                widths[anchor] += given;
                growth -= given;
            }
        }

        var order = new ArrayList<Integer>();
        for (var position = 0; position < others.size(); position++) {
            if (position == bestInsert) order.add(anchor);
            order.add(others.get(position));
        }
        if (bestInsert >= others.size()) order.add(anchor);
        return order;
    }

    /**
     * Divides one strip along the spine, reserving the rear for the stair and lift when the core
     * belongs to this strip so it lands identically on every storey.
     */
    private List<Placement> placeStrip(List<Request> requests, double runFrom, double runTotal,
            double bandFrom, double stripWidth, boolean withCore) {
        var placements = new ArrayList<Placement>();
        if (stripWidth <= .05 || runTotal <= .05) return placements;
        var carriesCore = withCore && (stairRequired || liftPlaced);
        // On a plate too shallow to hold rooms behind the core, the core takes the strip and the
        // programme reports what it could not place. A negative run would be a drawn contradiction.
        var coreDepth = carriesCore ? Math.min(coreRun, runTotal) : 0d;
        var available = round2(runTotal - coreDepth);
        if (carriesCore && available < RoomSpec.of("STORE").minShortSide()) {
            available = 0d;
        }
        var runEnd = round2(runFrom + available);
        if (available <= .05) {
            placements.addAll(corePlacements(runEnd, round2(runFrom + runTotal), bandFrom,
                    stripWidth, carriesCore));
            return placements;
        }

        var fitted = fitToRun(requests, stripWidth, available, true);
        var slots = pairCompactSpaces(fitted, stripWidth);
        var spans = shareSlots(slots, available);
        var cursor = runFrom;
        for (var index = 0; index < slots.size(); index++) {
            var end = index == slots.size() - 1 ? runEnd : round2(cursor + spans[index]);
            placements.addAll(slots.get(index).split(cursor, bandFrom, round2(end - cursor)));
            cursor = end;
        }
        placements.addAll(corePlacements(runEnd, round2(runFrom + runTotal), bandFrom,
                stripWidth, carriesCore));
        return placements;
    }

    /** The stair, and the lift beside it when the strip is wide enough to carry both. */
    private List<Placement> corePlacements(double coreFrom, double coreEnd, double bandFrom,
            double stripWidth, boolean carriesCore) {
        var run = round2(coreEnd - coreFrom);
        if (!carriesCore || run <= .05) return List.of();
        if (stairRequired && liftPlaced && stripWidth - stairAcross > .05) {
            return List.of(
                    new Placement("STAIRCASE", coreFrom, bandFrom, run, stairAcross),
                    new Placement("LIFT_SHAFT", coreFrom, round2(bandFrom + stairAcross), run,
                            round2(stripWidth - stairAcross)));
        }
        return List.of(new Placement(stairRequired ? "STAIRCASE" : "STORE", coreFrom, bandFrom,
                run, stripWidth));
    }

    /**
     * Reconciles a wanted programme with the run it has to fit inside.
     *
     * <p>Rooms that cannot reach a usable size are dropped in reverse priority order rather than
     * drawn too small, and a run with area to spare is given spaces that can absorb it before the
     * surplus is allowed to inflate a bedroom into a hall.</p>
     */
    private List<Request> fitToRun(List<Request> requests, double across, double available,
            boolean allowFiller) {
        var kept = new ArrayList<Request>();
        for (var request : requests) {
            if (Double.isNaN(RoomSpec.of(request.type()).minRun(across))) {
                notes.add(roomLabel(request.type()) + " needs a wider run than this plot leaves and "
                        + "was not placed");
                continue;
            }
            kept.add(request);
        }
        if (kept.isEmpty()) kept.add(Request.of("STORE"));

        // Too little room: drop the least important spaces until the minimums fit. A bedroom leaves
        // with the bathroom that belongs to it, or the plan keeps an ensuite serving nothing.
        while (kept.size() > 1 && slotMinimum(kept, across, allowFiller) > available + .01) {
            var victim = -1;
            var worst = Long.MIN_VALUE;
            for (var index = 0; index < kept.size(); index++) {
                var type = kept.get(index).type();
                if ("ATTACHED_BATHROOM".equals(type)) continue;
                // Optional spaces go first; among equals a plain bedroom goes before the master or
                // the senior's room, and the later of two identical rooms goes before the earlier.
                var score = RoomSpec.of(type).priority() * 1_000L
                        + ("BEDROOM".equals(type) ? 500L : 0L) + index;
                if (score > worst) {
                    worst = score;
                    victim = index;
                }
            }
            if (victim < 0) break;
            notes.add(roomLabel(kept.get(victim).type()) + " was left out; the buildable area cannot "
                    + "carry it at a usable size");
            var wasBedroom = RoomSpec.isBedroom(kept.get(victim).type());
            kept.remove(victim);
            if (wasBedroom && victim < kept.size() && "ATTACHED_BATHROOM".equals(kept.get(victim).type())) {
                kept.remove(victim);
            }
            dropOrphanEnsuites(kept);
        }
        dropOrphanEnsuites(kept);

        // Room to spare: give it to spaces that use it well instead of oversizing what is there.
        // Each addition has to keep the run feasible, or the surplus is traded for a squeezed plan.
        if (allowFiller) {
            for (var filler : List.of("STORE", "STUDY", "PRAYER_ROOM", "MULTIPURPOSE_ROOM", "LAUNDRY",
                    "DRESSING_ROOM", "FLEX_ROOM", "HOME_OFFICE")) {
                if (slotMaximum(kept, across, true) >= available - .01) break;
                if (kept.stream().anyMatch(request -> request.type().equals(filler))) continue;
                if (Double.isNaN(RoomSpec.of(filler).minRun(across))) continue;
                var trial = new ArrayList<>(kept);
                trial.add(Request.of(filler));
                if (slotMinimum(trial, across, true) > available + .01) continue;
                kept = trial;
            }
        }
        return kept;
    }

    /**
     * Removes any private bathroom left standing without the bedroom it served.
     *
     * <p>An ensuite is protected from the dropping pass so it can never outlive its bedroom by
     * accident, which means it has to be cleared deliberately when the bedroom does go. Left in, a
     * floor that lost its bedrooms came back as a row of bathrooms opening off the passage.</p>
     */
    private void dropOrphanEnsuites(List<Request> requests) {
        for (var index = requests.size() - 1; index >= 0; index--) {
            if (!"ATTACHED_BATHROOM".equals(requests.get(index).type())) continue;
            if (index > 0 && RoomSpec.isBedroom(requests.get(index - 1).type())) continue;
            requests.remove(index);
        }
    }

    private double slotMinimum(List<Request> requests, double across, boolean pair) {
        var slots = pair ? pairCompactSpaces(requests, across)
                : requests.stream().map(request -> new Slot(List.of(request), across)).toList();
        return slots.stream().mapToDouble(Slot::minimumRun).sum();
    }

    private double slotMaximum(List<Request> requests, double across, boolean pair) {
        var slots = pair ? pairCompactSpaces(requests, across)
                : requests.stream().map(request -> new Slot(List.of(request), across)).toList();
        return slots.stream().mapToDouble(Slot::maximumRun).sum();
    }

    /**
     * Groups the small spaces so two of them share one run of the strip side by side.
     *
     * <p>Without this a bathroom given a thirteen-foot-wide strip comes out thirteen feet long and
     * five wide — the area is right and the room is a corridor with a WC at the end. Pairing a
     * bathroom with the store or dressing room that would sit beside it anyway produces the six-by-
     * seven both rooms actually want, and is what lets a floor hold its whole programme.</p>
     */
    private List<Slot> pairCompactSpaces(List<Request> requests, double stripWidth) {
        var slots = new ArrayList<Slot>();
        var index = 0;
        while (index < requests.size()) {
            var request = requests.get(index);
            var spec = RoomSpec.of(request.type());
            if (!isCompact(spec)) {
                slots.add(new Slot(List.of(request), stripWidth));
                index++;
                continue;
            }
            var next = index + 1 < requests.size() ? requests.get(index + 1) : null;
            var partner = next != null && isCompact(RoomSpec.of(next.type())) ? next
                    : new Request(companionFor(request.type(), requests, slots), 0);
            var partnerSpec = RoomSpec.of(partner.type());
            var fits = stripWidth >= spec.minShortSide() + partnerSpec.minShortSide() + 1.5d;
            if (!fits) {
                slots.add(new Slot(List.of(request), stripWidth));
                index++;
                continue;
            }
            var companion = partner.preferredArea() > 0 ? partner : Request.of(partner.type());
            slots.add(new Slot(List.of(request, companion), stripWidth));
            index += partner == next ? 2 : 1;
        }
        return List.copyOf(slots);
    }

    /** True for spaces that would read as a passage if they took a whole strip to themselves. */
    private static boolean isCompact(RoomSpec spec) {
        return spec.maxArea() <= 96d;
    }

    /**
     * The room that naturally sits beside a small one when the strip is wide enough for both.
     *
     * <p>Falls through to the next sensible neighbour when the obvious one is already on this floor,
     * so a storey never ends up with three stores in a row for want of a second idea.</p>
     */
    private static String companionFor(String type, List<Request> requests, List<Slot> placedSoFar) {
        var preferred = switch (type) {
            case "ATTACHED_BATHROOM" -> List.of("DRESSING_ROOM", "STORE", "TOILET");
            case "UTILITY" -> List.of("LAUNDRY", "STORE", "PRAYER_ROOM");
            default -> List.of("STORE", "PRAYER_ROOM", "LAUNDRY", "DRESSING_ROOM");
        };
        for (var candidate : preferred) {
            var alreadyRequested = requests.stream().anyMatch(request -> request.type().equals(candidate));
            var alreadyPlaced = placedSoFar.stream().flatMap(slot -> slot.requests().stream())
                    .anyMatch(request -> request.type().equals(candidate));
            if (!alreadyRequested && !alreadyPlaced) return candidate;
        }
        return preferred.getLast();
    }

    /**
     * Shares one run of {@code total} feet between the slots that have to fill it.
     *
     * <p>Each slot starts at the run its wanted area implies, is clamped to the dimensions its rooms
     * are usable at, and the shortfall or surplus is passed to whichever slots still have room to
     * take it. When the programme genuinely cannot fit, minimums are squeezed proportionally rather
     * than one room being made unusable to protect the rest.</p>
     */
    private double[] shareSlots(List<Slot> slots, double total) {
        var count = slots.size();
        var minimum = new double[count];
        var maximum = new double[count];
        var value = new double[count];
        for (var index = 0; index < count; index++) {
            var slot = slots.get(index);
            minimum[index] = slot.minimumRun();
            maximum[index] = Math.max(minimum[index], slot.maximumRun());
            value[index] = clamp(slot.preferredRun(), minimum[index], maximum[index]);
        }
        var minimumTotal = sum(minimum);
        if (minimumTotal > total) {
            var factor = total / minimumTotal;
            for (var index = 0; index < count; index++) {
                minimum[index] *= factor;
                maximum[index] = Math.max(maximum[index] * factor, minimum[index]);
                value[index] = minimum[index];
            }
        }
        var maximumTotal = sum(maximum);
        if (maximumTotal < total) {
            // More run than the programme wants. The surplus is shared in proportion to how much
            // each space already holds, so a generous plot gives a larger living room rather than a
            // store forty feet deep. A flat scale-up would multiply the smallest rooms the most.
            var surplus = total - maximumTotal;
            var weight = sum(value);
            for (var index = 0; index < count; index++) {
                maximum[index] += surplus * (weight <= 1e-9 ? 1d / count : value[index] / weight);
            }
        }
        for (var index = 0; index < count; index++) {
            value[index] = clamp(value[index], minimum[index], maximum[index]);
        }
        for (var pass = 0; pass < 40; pass++) {
            var residual = total - sum(value);
            if (Math.abs(residual) < 1e-6) break;
            var headroom = 0d;
            for (var index = 0; index < count; index++) {
                headroom += residual > 0 ? maximum[index] - value[index] : value[index] - minimum[index];
            }
            if (headroom < 1e-9) break;
            for (var index = 0; index < count; index++) {
                var room = residual > 0 ? maximum[index] - value[index] : value[index] - minimum[index];
                value[index] = clamp(value[index] + residual * (room / headroom),
                        minimum[index], maximum[index]);
            }
        }
        // Any residue left by clamping goes to the space best able to absorb it.
        var residue = total - sum(value);
        if (Math.abs(residue) > 1e-6) {
            var target = 0;
            for (var index = 1; index < count; index++) if (value[index] > value[target]) target = index;
            value[target] += residue;
        }
        return value;
    }

    // ---------------------------------------------------------------------------------------
    // Geometry helpers
    // ---------------------------------------------------------------------------------------

    /**
     * Maps a road-relative rectangle onto the planning grid.
     *
     * <p>Depth is measured inward from the road edge and the transverse axis runs left to right as
     * the plot is approached, so one programme serves all four road facings without the layout being
     * written out four times. The four cases are true quarter turns of one another, which is what
     * keeps a south-facing plan the mirror of a north-facing one rather than a different design.</p>
     */
    /**
     * Maps a placement out of the run/band frame the planner works in.
     *
     * <p>When the spine runs along the frontage the two axes are exchanged: the run is measured
     * across the frontage and the strips divide the depth. That single swap is what lets one
     * programme serve both a deep narrow plot and a wide shallow one.</p>
     */
    private double[] toPlot(Placement placement) {
        return spineAlongFrontage
                ? toPlot(placement.bandFrom(), placement.runFrom(), placement.bandRun(), placement.runRun())
                : toPlot(placement.runFrom(), placement.bandFrom(), placement.runRun(), placement.bandRun());
    }

    private double[] toPlot(double depthFrom, double acrossFrom, double depthRun, double acrossRun) {
        return switch (facing) {
            case NORTH -> new double[] {round2(plateX + acrossFrom),
                    round2(plateY + plateLength - depthFrom - depthRun), round2(acrossRun), round2(depthRun)};
            case SOUTH -> new double[] {round2(plateX + plateWidth - acrossFrom - acrossRun),
                    round2(plateY + depthFrom), round2(acrossRun), round2(depthRun)};
            case EAST -> new double[] {round2(plateX + plateWidth - depthFrom - depthRun),
                    round2(plateY + plateLength - acrossFrom - acrossRun), round2(depthRun), round2(acrossRun)};
            case WEST -> new double[] {round2(plateX + depthFrom), round2(plateY + acrossFrom),
                    round2(depthRun), round2(acrossRun)};
        };
    }

    /** Clear width and run a staircase type needs, in feet. */
    private static double[] staircaseFootprint(String type) {
        return switch (type == null ? "DOG_LEGGED" : type) {
            case "STRAIGHT" -> new double[] {4d, 14d};
            case "L_SHAPED" -> new double[] {7.5d, 10.5d};
            case "U_SHAPED" -> new double[] {9d, 9.5d};
            default -> new double[] {8d, 10.5d};
        };
    }

    private RoomGeometry room(int floorIndex, int roomIndex, String type, double x, double y,
            double width, double length) {
        var left = round2(x);
        var bottom = round2(y);
        var right = round2(x + width);
        var top = round2(y + length);
        var snappedWidth = round2(right - left);
        var snappedLength = round2(top - bottom);
        return new RoomGeometry(floorPrefix(floorIndex) + "-R" + roomIndex, type, left, bottom,
                snappedWidth, snappedLength, round2(snappedWidth * snappedLength), floorName(floorIndex));
    }

    private static String floorName(int floorIndex) {
        return switch (floorIndex) {
            case 0 -> "GROUND";
            case 1 -> "FIRST";
            default -> "SECOND";
        };
    }

    private static String floorPrefix(int floorIndex) {
        return switch (floorIndex) {
            case 0 -> "G";
            case 1 -> "F1";
            default -> "F2";
        };
    }

    private static String roomLabel(String type) {
        return type.charAt(0) + type.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static double sum(double[] values) {
        var total = 0d;
        for (var value : values) total += value;
        return total;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    /** One space the programme asks for, with the area it wants rather than only its type default. */
    private record Request(String type, double preferredArea) {
        static Request of(String type) {
            return new Request(type, RoomSpec.of(type).preferredArea());
        }
    }

    /** A rectangle in road-relative coordinates, before it is mapped onto the planning grid. */
    private record Placement(String type, double runFrom, double bandFrom, double runRun,
                             double bandRun) {}

    /**
     * One run of a strip, holding either a single room across the full width or two side by side.
     *
     * <p>The widths are settled when the slot is created so the run every caller reasons about is
     * already the run those particular widths need.</p>
     */
    private record Slot(List<Request> requests, double stripWidth) {
        /** Transverse width of each room in the slot, always totalling the strip exactly. */
        private double[] widths() {
            var count = requests.size();
            var widths = new double[count];
            if (count == 1) {
                widths[0] = stripWidth;
                return widths;
            }
            var wanted = 0d;
            for (var request : requests) wanted += Math.max(1, request.preferredArea());
            var used = 0d;
            for (var index = 0; index < count; index++) {
                var spec = RoomSpec.of(requests.get(index).type());
                var share = stripWidth * Math.max(1, requests.get(index).preferredArea()) / wanted;
                widths[index] = Math.max(spec.minShortSide(), share);
                used += widths[index];
            }
            // Honour the minimums first, then give the residue to the room that wanted more area.
            var residue = stripWidth - used;
            var target = 0;
            for (var index = 1; index < count; index++) {
                if (requests.get(index).preferredArea() > requests.get(target).preferredArea()) target = index;
            }
            widths[target] = Math.max(RoomSpec.of(requests.get(target).type()).minShortSide(),
                    widths[target] + residue);
            var total = 0d;
            for (var width : widths) total += width;
            widths[count - 1] += stripWidth - total;
            return widths;
        }

        double minimumRun() {
            var widths = widths();
            var minimum = 0d;
            for (var index = 0; index < requests.size(); index++) {
                var run = RoomSpec.of(requests.get(index).type()).minRun(widths[index]);
                minimum = Math.max(minimum, Double.isNaN(run) ? widths[index] : run);
            }
            return minimum;
        }

        double maximumRun() {
            var widths = widths();
            var maximum = 0d;
            for (var index = 0; index < requests.size(); index++) {
                maximum = Math.max(maximum, RoomSpec.of(requests.get(index).type()).maxRun(widths[index]));
            }
            return maximum;
        }

        double preferredRun() {
            var wanted = 0d;
            for (var request : requests) wanted += request.preferredArea();
            return clamp(wanted / Math.max(.01, stripWidth), minimumRun(), maximumRun());
        }

        /** Lays the slot out, keeping the first room on the passage side of the strip. */
        List<Placement> split(double runFrom, double bandFrom, double runRun) {
            var widths = widths();
            var placements = new ArrayList<Placement>();
            var cursor = bandFrom;
            for (var index = 0; index < requests.size(); index++) {
                var end = index == requests.size() - 1 ? round2(bandFrom + stripWidth)
                        : round2(cursor + widths[index]);
                placements.add(new Placement(requests.get(index).type(), runFrom, cursor, runRun,
                        round2(end - cursor)));
                cursor = end;
            }
            return placements;
        }
    }

    /** The rooms one storey holds, grouped by where they sit relative to the circulation spine. */
    private record FloorProgramme(List<Request> front, List<Request> sleeping, List<Request> service) {}

    /** The structural decisions taken once for the whole building, for provenance and tests. */
    Map<String, Double> plateFacts() {
        var facts = new LinkedHashMap<String, Double>();
        facts.put("frontDepth", frontDepth(0, groundOutdoorArea()));
        facts.put("corridorWidth", corridorWidth);
        facts.put("leftWidth", leftWidth);
        facts.put("rightWidth", rightWidth);
        facts.put("coreRun", coreRun);
        return Map.copyOf(facts);
    }
}

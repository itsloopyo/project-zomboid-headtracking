package com.cameraunlock.zomboid;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Tests of which rooms the eye's picture draws the contents of: see RoomSight. Each is held
 * to RoomRays, which follows a line through each of a fine grid of points of the picture: a
 * room a line reaches has to be one the walk finds.
 *
 * The plans are squares of rooms on levels. Two squares of different rooms, or a room and
 * the open air, have a wall between them unless a door, a window or a gap is put in it; a
 * room has a floor and a ceiling, and a square of open air at level 0 has the ground.
 */
final class RoomSightTests {
    private RoomSightTests() {}

    static void run() {
        Checks.run("aShutDoorHidesTheRoomBehindItAndAnOpenOneShowsIt", RoomSightTests::aShutDoorHidesTheRoomBehindItAndAnOpenOneShowsIt);
        Checks.run("aRoomBehindTheEyeOrOffThePictureIsNotSeen", RoomSightTests::aRoomBehindTheEyeOrOffThePictureIsNotSeen);
        Checks.run("aWindowShowsTheRoomFromTheStreetAndADrawnCurtainHidesIt", RoomSightTests::aWindowShowsTheRoomFromTheStreetAndADrawnCurtainHidesIt);
        Checks.run("aRoomIsSeenThroughTwoDoorsOnlyWhereTheyLineUp", RoomSightTests::aRoomIsSeenThroughTwoDoorsOnlyWhereTheyLineUp);
        Checks.run("aHeadLeaningIntoADoorwaySeesTheRoomBeyond", RoomSightTests::aHeadLeaningIntoADoorwaySeesTheRoomBeyond);
        Checks.run("theFloorAboveIsSeenUpTheStairwellAndNotThroughTheCeiling", RoomSightTests::theFloorAboveIsSeenUpTheStairwellAndNotThroughTheCeiling);
        Checks.run("aHouseAcrossTheStreetIsSeenThroughTheEyesOwnWindow", RoomSightTests::aHouseAcrossTheStreetIsSeenThroughTheEyesOwnWindow);
        Checks.run("anUpperRoomIsSeenThroughItsWindowFromTheStreet", RoomSightTests::anUpperRoomIsSeenThroughItsWindowFromTheStreet);
        Checks.run("theStoreysSeenIntoFromTheStreetAreCountedFromTheLand", RoomSightTests::theStoreysSeenIntoFromTheStreetAreCountedFromTheLand);
        Checks.run("aHouseSeenThroughAClosetWindowAmongManyWindowsIsSeen", RoomSightTests::aHouseSeenThroughAClosetWindowAmongManyWindowsIsSeen);
        Checks.run("aHouseBetweenTheEyeAndAWindowHidesItUnlessTheLineGoesThroughTheHouse", RoomSightTests::aHouseBetweenTheEyeAndAWindowHidesItUnlessTheLineGoesThroughTheHouse);
        Checks.run("aTallerHouseBehindALowOneShowsItsUpperRooms", RoomSightTests::aTallerHouseBehindALowOneShowsItsUpperRooms);
        Checks.run("anEyeClimbingAStairOverTheFloorAboveSeesThatFloor", RoomSightTests::anEyeClimbingAStairOverTheFloorAboveSeesThatFloor);
        Checks.run("aHouseAcrossTheStreetSeenOnlyThroughAMiddleWindowIsSeen", RoomSightTests::aHouseAcrossTheStreetSeenOnlyThroughAMiddleWindowIsSeen);
        Checks.run("aHeadThatMovesALittleKeepsWhatTheBlocksHideAndMissesNothing", RoomSightTests::aHeadThatMovesALittleKeepsWhatTheBlocksHideAndMissesNothing);
        Checks.run("whatIsBuiltIsWhatAnyTurnOfTheHeadOrANearDoorOpenedWouldShow", RoomSightTests::whatIsBuiltIsWhatAnyTurnOfTheHeadOrANearDoorOpenedWouldShow);
        Checks.run("aRoomAStepAwayThroughTwoOpeningsIsBuiltBeforeItShows", RoomSightTests::aRoomAStepAwayThroughTwoOpeningsIsBuiltBeforeItShows);
        Checks.run("whatAnEyeAStepFromThePlaceSeesInAHouseIsBuilt", RoomSightTests::whatAnEyeAStepFromThePlaceSeesInAHouseIsBuilt);
        Checks.run("roomsKeptForBuildingGoAfterAWhileUnfound", RoomSightTests::roomsKeptForBuildingGoAfterAWhileUnfound);
        Checks.run("openingsAreFoundInWallsAndNotWhereThereIsNone", RoomSightTests::openingsAreFoundInWallsAndNotWhereThereIsNone);
        Checks.run("noRoomALineReachesIsLeftOutInARandomTown", RoomSightTests::noRoomALineReachesIsLeftOutInARandomTown);
        Checks.run("aSetOfRoomsHoldsWhatIsAddedUntilItIsCleared", RoomSightTests::aSetOfRoomsHoldsWhatIsAddedUntilItIsCleared);
        Checks.run("aPictureListsOnlyTheRoomsItSeesInto", RoomSightTests::aPictureListsOnlyTheRoomsItSeesInto);
        Checks.run("aWallsEdgeAndAWindowsBlindAreListedByTheRoomsEitherSide", RoomSightTests::aWallsEdgeAndAWindowsBlindAreListedByTheRoomsEitherSide);
        Checks.run("aMapLetGoOfHoldsNothingOfTheWorldItWasReadFrom", RoomSightTests::aMapLetGoOfHoldsNothingOfTheWorldItWasReadFrom);
        Checks.run("aChunkReadBeforeItsLandIsReadAgainOnceItIsWorkedOut", RoomSightTests::aChunkReadBeforeItsLandIsReadAgainOnceItIsWorkedOut);
        Checks.run("aChunkNotToBeReadKeepsWhatItHadAndOneNeverReadIsUnread", RoomSightTests::aChunkNotToBeReadKeepsWhatItHadAndOneNeverReadIsUnread);
        Checks.run("aChunkUnreadAddsNothingToTheFramesOpenings", RoomSightTests::aChunkUnreadAddsNothingToTheFramesOpenings);
        Checks.run("aChunkMarkedWhileItMayNotBeReadIsUnread", RoomSightTests::aChunkMarkedWhileItMayNotBeReadIsUnread);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** A plan of rooms on levels, with the openings in their walls. */
    static final class Plan implements RoomSight.Ground {
        private final Map<Long, Long> rooms = new HashMap<>(), buildings = new HashMap<>();
        private long building = 100;
        /** How far the land under every square is over 0, in tiles. */
        private float lift;
        private final Map<Long, Integer> edges = new HashMap<>();
        private final java.util.Set<Long> holes = new java.util.HashSet<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ;

        private static long key(int x, int y, int z) {
            return ((long) (x + 4096) << 40) | ((long) (y + 4096) << 16) | (z + 64);
        }

        private static long edgeKey(int x, int y, int z, boolean north) {
            return key(x, y, z) * 2 + (north ? 1 : 0);
        }

        /** Has the rooms filled from here on be in another building. */
        Plan building(long id) {
            building = id;
            return this;
        }

        /** Fills squares fromX to toX across and fromY to toY down, on level z, with room id. */
        Plan room(long id, int z, int fromX, int fromY, int toX, int toY) {
            buildings.put(id, building);
            for (int x = fromX; x <= toX; x++) {
                for (int y = fromY; y <= toY; y++) {
                    rooms.put(key(x, y, z), id);
                }
            }
            minX = Math.min(minX, fromX - 1);
            minY = Math.min(minY, fromY - 1);
            maxX = Math.max(maxX, toX + 1);
            maxY = Math.max(maxY, toY + 1);
            maxZ = Math.max(maxZ, z);
            return this;
        }

        /** Puts something in the north edge of a square (towards y - 1), or its west edge. */
        Plan opening(int x, int y, int z, boolean north, int state) {
            edges.put(edgeKey(x, y, z, north), state);
            return this;
        }

        /** Has the land under every square stand this far over 0, in tiles: on a hill, or in a hollow under 0. */
        Plan lift(float tiles) {
            lift = tiles;
            return this;
        }

        /** Takes the floor out of a square, as the well of a stair does. */
        Plan hole(int x, int y, int z) {
            holes.add(key(x, y, z));
            return this;
        }

        @Override
        public long region(int x, int y, int z) {
            Long id = rooms.get(key(x, y, z));
            return id == null ? RoomSight.OUTSIDE : id;
        }

        @Override
        public int edge(int x, int y, int z, boolean north) {
            Integer put = edges.get(edgeKey(x, y, z, north));
            if (put != null) {
                return put;
            }
            return region(x, y, z) == (north ? region(x, y - 1, z) : region(x - 1, y, z)) ? RoomSight.SEE : RoomSight.SOLID;
        }

        @Override
        public boolean floored(int x, int y, int z) {
            if (holes.contains(key(x, y, z))) {
                return false;
            }
            return z == 0 || region(x, y, z) != RoomSight.OUTSIDE || region(x, y, z - 1) != RoomSight.OUTSIDE;
        }

        @Override
        public float floorUp(float x, float y, int z) {
            return z * ViewCamera.LEVEL_HEIGHT + lift;
        }

        @Override
        public long building(int x, int y, int z) {
            long room = region(x, y, z);
            return room == RoomSight.OUTSIDE ? RoomSight.OUTSIDE : buildings.get(room);
        }

        RoomSight.Blocks blocks() {
            RoomSight.Blocks blocks = new RoomSight.Blocks();
            for (int y = minY; y <= maxY + 1; y++) {
                blocks.scanRow(this, minX, maxX + 2, y);
            }
            return blocks;
        }

        RoomSight.Openings openings() {
            RoomSight.Openings openings = new RoomSight.Openings();
            for (int z = 0; z <= maxZ; z++) {
                for (int x = minX; x <= maxX + 1; x++) {
                    for (int y = minY; y <= maxY + 1; y++) {
                        openings.scan(this, x, y, z);
                    }
                }
            }
            return openings;
        }
    }

    private static final float FOV = (float) Math.tan(Math.toRadians(55) / 2), ASPECT = 16f / 9f;

    /** The rooms the walk finds for an eye standing at (x, y) on level z, facing yaw degrees and pitch degrees, and holds them to the lines. */
    private static RoomSight.Seen seen(Plan plan, float x, float y, int z, float yaw, float pitch) {
        return seenFrom(plan, x, y, z * ViewCamera.LEVEL_HEIGHT + 1.6f, z, yaw, pitch);
    }

    private static RoomSight.Seen seenFrom(Plan plan, float x, float y, float up, int z, float yaw, float pitch) {
        ViewCamera.Pose eye = new ViewCamera.Pose();
        ViewCamera.clean(eye, x, y, up, (float) Math.toRadians(yaw), (float) Math.toRadians(pitch));
        RoomSight.Seen walked = new RoomSight.Seen(), lines = new RoomSight.Seen();
        int squareX = (int) Math.floor(x), squareY = (int) Math.floor(y);
        new RoomSight().walk(plan, plan.openings(), plan.blocks(), eye, FOV * ASPECT, FOV, plan.region(squareX, squareY, z), plan.building(squareX, squareY, z), walked);
        RoomRays.trace(plan, eye, FOV * ASPECT, FOV, 160, 90, 80f, z, lines);
        for (long id = 0; id < 64; id++) {
            if (lines.contains(id)) {
                check(walked.contains(id), "a line from (" + x + ", " + y + ") facing " + yaw + " reaches room " + id + " and the walk finds it too");
            }
        }
        return walked;
    }

    private static RoomSight.Seen allRound(Plan plan, float x, float y, int z, float grow, float shutWithin) {
        RoomSight.Seen round = new RoomSight.Seen();
        int squareX = (int) Math.floor(x), squareY = (int) Math.floor(y);
        new RoomSight().walkAllRound(plan, plan.openings(), plan.blocks(), x, y, z * ViewCamera.LEVEL_HEIGHT + 1.6f, plan.region(squareX, squareY, z), plan.building(squareX, squareY, z),
                grow, shutWithin, round);
        return round;
    }

    private static final long HALL = 1, BATH = 2, KITCHEN = 3, OVER = 4, ACROSS = 5, BETWEEN = 6, BEYOND = 7;

    /** A hall, squares 1 to 4 each way, with a bathroom east of it, 5 to 6 across and 1 to 2 down, through a door at the west edge of 5, 2. */
    private static Plan house(int door) {
        return new Plan().room(HALL, 0, 1, 1, 4, 4).room(BATH, 0, 5, 1, 6, 2).opening(5, 2, 0, false, door);
    }

    private static void aShutDoorHidesTheRoomBehindItAndAnOpenOneShowsIt() {
        check(!seen(house(RoomSight.SHUT), 2.5f, 2.5f, 0, 0f, 0f).contains(BATH), "the bathroom is not seen through its shut door");
        check(seen(house(RoomSight.SEE), 2.5f, 2.5f, 0, 0f, 0f).contains(BATH), "and is through the door open");
        check(seen(house(RoomSight.SHUT), 2.5f, 2.5f, 0, 0f, 0f).contains(HALL), "the eye's own room is always seen");
        check(!seen(house(RoomSight.SOLID), 2.5f, 2.5f, 0, 0f, 0f).contains(BATH), "and a wall with no door hides it");
    }

    private static void aRoomBehindTheEyeOrOffThePictureIsNotSeen() {
        check(!seen(house(RoomSight.SEE), 2.5f, 2.5f, 0, 180f, 0f).contains(BATH), "the bathroom behind the eye is not seen through its open door");
        check(!seen(house(RoomSight.SEE), 2.5f, 3.5f, 0, 90f, 0f).contains(BATH), "nor one off to the side of the picture");
    }

    private static void aWindowShowsTheRoomFromTheStreetAndADrawnCurtainHidesIt() {
        Plan glass = house(RoomSight.SHUT).opening(6, 1, 0, true, RoomSight.SEE);
        check(seen(glass, 6.5f, -4.5f, 0, 90f, 0f).contains(BATH), "the bathroom is seen from the street through its window");
        check(!seen(glass, 6.5f, -4.5f, 0, 90f, 0f).contains(HALL), "and the hall, behind a wall and a shut door, is not");
        Plan curtained = house(RoomSight.SHUT).opening(6, 1, 0, true, RoomSight.SHUT);
        check(!seen(curtained, 6.5f, -4.5f, 0, 90f, 0f).contains(BATH), "a drawn curtain hides it");
        check(!seen(glass, 6.5f, 8.5f, 0, -90f, 0f).contains(BATH), "and from the side the window is not on, the wall hides it");
    }

    private static void aRoomIsSeenThroughTwoDoorsOnlyWhereTheyLineUp() {
        // A row of three rooms, with doors between the hall and the bathroom and between the bathroom and a kitchen east of it.
        Plan row = house(RoomSight.SEE).room(KITCHEN, 0, 7, 1, 9, 2).opening(7, 2, 0, false, RoomSight.SEE);
        check(seen(row, 1.5f, 2.5f, 0, 0f, 0f).contains(KITCHEN), "the kitchen is seen through both doors in a line");
        Plan aside = house(RoomSight.SEE).room(KITCHEN, 0, 7, 1, 9, 2).opening(7, 1, 0, false, RoomSight.SEE);
        check(!seen(aside, 1.5f, 1.5f, 0, 10f, 0f).contains(KITCHEN), "and not where the second door is out of the first one's line");
    }

    private static void aHeadLeaningIntoADoorwaySeesTheRoomBeyond() {
        // The eye a hair from the doorway's plane, looking along the wall: the opening is all but edge on, and the room is still seen.
        Plan open = house(RoomSight.SEE);
        check(seen(open, 4.95f, 2.5f, 0, 10f, 0f).contains(BATH), "an eye at the doorway sees into the room beyond it");
        check(seen(open, 5.05f, 2.5f, 0, 170f, 0f).contains(HALL), "and from just past it, the room it left");
    }

    private static void theFloorAboveIsSeenUpTheStairwellAndNotThroughTheCeiling() {
        Plan stairs = new Plan().room(HALL, 0, 1, 1, 4, 4).room(OVER, 1, 1, 1, 4, 4);
        check(!seen(stairs, 2.5f, 2.5f, 0, 0f, 60f).contains(OVER), "the room above is not seen through the ceiling");
        stairs.hole(3, 2, 1).hole(3, 3, 1);
        check(seen(stairs, 2.5f, 2.5f, 0, 0f, 60f).contains(OVER), "and is up the well of the stair");
        check(seen(stairs, 3.5f, 2.5f, 1, 180f, -60f).contains(HALL), "and from above, the room below is seen down it");
    }

    private static void aHouseAcrossTheStreetIsSeenThroughTheEyesOwnWindow() {
        Plan street = house(RoomSight.SHUT).opening(1, 3, 0, true, RoomSight.SOLID).opening(3, 5, 0, true, RoomSight.SEE)
                .room(ACROSS, 0, 1, 10, 6, 14).opening(3, 10, 0, true, RoomSight.SEE);
        check(seen(street, 3.5f, 3.5f, 0, 90f, 0f).contains(ACROSS), "the room across the street is seen through the hall's window and its own");
        check(!seen(street, 3.5f, 3.5f, 0, 0f, 0f).contains(ACROSS), "and not with the eye turned away");
    }

    private static void anUpperRoomIsSeenThroughItsWindowFromTheStreet() {
        Plan two = new Plan().room(HALL, 0, 1, 1, 4, 4).room(OVER, 1, 1, 1, 4, 4).opening(2, 5, 1, true, RoomSight.SEE);
        check(seen(two, 2.5f, 12.5f, 0, -90f, 20f).contains(OVER), "a room upstairs is seen through its window from the street below");
        check(!seen(two, 2.5f, 12.5f, 0, -90f, 20f).contains(HALL), "and the room under it, with no window, is not");
    }

    /**
     * From the street a room is seen into through a window up to the storey over the eye's, and
     * that storey is counted from the land the eye stands on: in a hollow under 0 the first
     * floor's window is the storey over the eye's, and on a hill a third floor's is not.
     */
    private static void theStoreysSeenIntoFromTheStreetAreCountedFromTheLand() {
        for (float lift : new float[] {-2f, 0f, 2.6f}) {
            Plan three = new Plan().lift(lift).room(HALL, 0, 1, 1, 4, 4).room(OVER, 1, 1, 1, 4, 4).room(ACROSS, 2, 1, 1, 4, 4).opening(2, 5, 1, true, RoomSight.SEE)
                    .opening(3, 5, 2, true, RoomSight.SEE);
            RoomSight.Seen seen = seenFrom(three, 2.5f, 12.5f, lift + 1.6f, 0, -90f, 25f);
            check(seen.contains(OVER), "with the land at " + lift + ", the first floor is seen through its window from the street");
            check(!seen.contains(ACROSS), "and the floor over it is not, its window showing its blind: " + lift);
        }
    }

    /**
     * A hall with a row of windows and doorways into closets with windows of their own, and
     * houses across the street. The street is reached through more windows than the walk keeps
     * a rectangle for, so some it keeps are widened over the wall between windows; a house seen
     * only through a closet's window, which lies in the picture in such a widening, is seen.
     */
    private static void aHouseSeenThroughAClosetWindowAmongManyWindowsIsSeen() {
        Plan plan = new Plan().room(HALL, 0, 0, 10, 30, 14);
        for (int x : new int[] {0, 2, 4, 5, 7, 8, 12, 13, 14, 15, 18, 19, 24, 26, 28, 29, 30}) {
            plan.opening(x, 10, 0, true, RoomSight.SEE);
        }
        long id = 10;
        for (int x : new int[] {19, 3, 17}) {
            plan.room(id++, 0, x, 8, x, 9).opening(x, 10, 0, true, RoomSight.SEE).opening(x, 8, 0, true, RoomSight.SEE);
        }
        plan.building(200);
        int[][] houses = {{22, -10, 24}, {25, -6, 26}, {27, -15, 27}, {6, -9, 8}, {6, -14, 7}, {5, -13, 5}, {10, -7, 11}, {8, -13, 9}};
        long seenOnlyThroughACloset = id + 3;
        for (int[] house : houses) {
            plan.room(id++, 0, house[0], house[1] - 2, house[0] + 2, house[1]).opening(house[2], house[1] + 1, 0, true, RoomSight.SEE);
        }
        check(seenFrom(plan, 2.3355f, 13.352f, 1.6f, 0, -58.36f, -6.51f).contains(seenOnlyThroughACloset), "the house seen through the closet's window is seen");
    }

    /** Three houses in a row up the y axis, the eye in the street south of them looking north at the middle one. */
    private static void aHouseBetweenTheEyeAndAWindowHidesItUnlessTheLineGoesThroughTheHouse() {
        Plan row = new Plan().building(10).room(BETWEEN, 0, 0, 10, 8, 14).building(20).room(BEYOND, 0, 0, 2, 8, 6).opening(4, 7, 0, true, RoomSight.SEE);
        // The far house's window faces the near house's back wall, and the eye is south of the near house.
        check(!seen(row, 4.5f, 20.5f, 0, -90f, 0f).contains(BEYOND), "a window behind a house, seen from the street the other side of it, is hidden by the house");
        row.opening(4, 15, 0, true, RoomSight.SEE).opening(4, 10, 0, true, RoomSight.SEE);
        RoomSight.Seen through = seen(row, 4.5f, 20.5f, 0, -90f, 0f);
        check(through.contains(BETWEEN) && through.contains(BEYOND), "and seen through the near house's front and back windows, it is not");
    }

    /**
     * A hall with a stair up its east side, under a landing that runs on along the floor above:
     * a character on the stair still stands on the hall's level while its eye is over the floor
     * above, looking along the landing. The walk starts on the level the eye is in.
     */
    private static void anEyeClimbingAStairOverTheFloorAboveSeesThatFloor() {
        Plan plan = new Plan().room(HALL, 0, 0, 0, 2, 6).room(OVER, 1, 0, 0, 2, 6).hole(2, 3, 1).hole(2, 4, 1).hole(2, 5, 1);
        RoomSight.Openings openings = plan.openings();
        RoomSight.Blocks blocks = plan.blocks();
        int views = 0;
        for (float y = 3.1f; y < 6f; y += 0.3f) {
            for (float up = 1.6f; up < 3.4f; up += 0.2f) {
                ViewCamera.Pose eye = new ViewCamera.Pose();
                for (float pitch = -40f; pitch <= 20f; pitch += 30f) {
                    ViewCamera.clean(eye, 2.5f, y, up, (float) Math.toRadians(-90), (float) Math.toRadians(pitch));
                    RoomSight.Seen walked = new RoomSight.Seen(), lines = new RoomSight.Seen();
                    int level = RoomSight.levelOf(plan, 2, (int) Math.floor(y), 0, up);
                    new RoomSight().walk(plan, openings, blocks, eye, FOV * ASPECT, FOV, plan.region(2, (int) Math.floor(y), level), plan.building(2, (int) Math.floor(y), level), walked);
                    RoomRays.trace(plan, eye, FOV * ASPECT, FOV, 160, 90, 40f, level, lines);
                    check(!lines.contains(OVER) || walked.contains(OVER), "an eye on the stair at (2.5, " + y + ") " + up + " up, pitched " + pitch + ", sees the floor above the lines reach, from level " + level);
                    check(!lines.contains(HALL) || walked.contains(HALL), "and the hall below them, from level " + level);
                    views++;
                }
            }
        }
        check(RoomSight.levelOf(plan, 2, 4, 0, 1.6f) == 0 && RoomSight.levelOf(plan, 2, 4, 0, ViewCamera.LEVEL_HEIGHT + 0.4f) == 1, "the eye is on the level above once it is over that floor");
        check(RoomSight.levelOf(plan, 1, 4, 0, ViewCamera.LEVEL_HEIGHT + 0.4f) == 0, "but not where the floor above is whole");
        check(views > 50, "views were looked from: " + views);
    }

    /**
     * A living room with a window either side of a door into a box room, whose own window looks
     * across the street at a house's window: the street is reached through the two windows on
     * the left and right of the picture first, and through the box room's, between them, last.
     */
    private static void aHouseAcrossTheStreetSeenOnlyThroughAMiddleWindowIsSeen() {
        Plan plan = new Plan().building(1).room(HALL, 0, 0, 5, 9, 9).room(BATH, 0, 3, 1, 6, 4)
                .opening(4, 5, 0, true, RoomSight.SEE).opening(4, 1, 0, true, RoomSight.SEE).opening(1, 5, 0, true, RoomSight.SEE).opening(8, 5, 0, true, RoomSight.SEE)
                .building(2).room(ACROSS, 0, 3, -12, 6, -9).opening(4, -8, 0, true, RoomSight.SEE);
        for (float x = 4.2f; x < 4.9f; x += 0.3f) {
            check(seen(plan, x, 9.5f, 0, -90f, 0f).contains(ACROSS), "the house across the street is seen through the box room's window from (" + x + ", 9.5)");
        }
    }

    private static void aTallerHouseBehindALowOneShowsItsUpperRooms() {
        Plan row = new Plan().building(10).room(BETWEEN, 0, 0, 10, 8, 14).building(20).room(BEYOND, 0, 0, 2, 8, 6).room(OVER, 1, 0, 2, 8, 6).opening(4, 7, 1, true, RoomSight.SEE);
        check(seen(row, 4.5f, 24.5f, 0, -90f, 10f).contains(OVER), "a window upstairs behind a house of one floor is seen over its roof");
    }

    /**
     * A street of houses with their windows open to it, walked by one walker from eyes a little
     * apart, which holds them all to one horizon: each is held to the lines from where it is.
     */
    private static void aHeadThatMovesALittleKeepsWhatTheBlocksHideAndMissesNothing() {
        Plan street = new Plan();
        long id = 0;
        for (int house = 0; house < 8; house++) {
            int ox = (house % 4) * 10, oy = house < 4 ? 0 : 16;
            street.building(200 + house).room(id++, 0, ox, oy, ox + 6, oy + 5).room(id++, 1, ox, oy, ox + 6, oy + 5);
            for (int z = 0; z < 2; z++) {
                street.opening(ox + 2, house < 4 ? oy + 6 : oy, z, true, RoomSight.SEE).opening(ox + 4, house < 4 ? oy : oy + 6, z, true, RoomSight.SEE);
            }
        }
        RoomSight.Openings openings = street.openings();
        RoomSight.Blocks blocks = street.blocks();
        RoomSight walker = new RoomSight();
        Random random = new Random(3);
        float x = 2.5f, y = 9.5f;
        int walked = 0;
        for (int step = 0; step < 400; step++) {
            x += (random.nextFloat() - 0.3f) * 0.12f;
            y = Math.max(8f, Math.min(11f, y + (random.nextFloat() - 0.5f) * 0.12f));
            float up = 1.6f + (random.nextFloat() - 0.5f) * 0.04f;
            ViewCamera.Pose eye = new ViewCamera.Pose();
            ViewCamera.clean(eye, x, y, up, (float) Math.toRadians(random.nextFloat() * 360f), (float) Math.toRadians(-10f + random.nextFloat() * 40f));
            RoomSight.Seen seen = new RoomSight.Seen(), lines = new RoomSight.Seen();
            walker.walk(street, openings, blocks, eye, FOV * ASPECT, FOV, RoomSight.OUTSIDE, RoomSight.OUTSIDE, seen);
            RoomRays.trace(street, eye, FOV * ASPECT, FOV, 160, 90, 80f, 0, lines);
            for (long room = 0; room < id; room++) {
                if (lines.contains(room)) {
                    check(seen.contains(room), "a line from (" + x + ", " + y + ") reaches room " + room + " and the walk from one horizon finds it");
                }
            }
            walked++;
        }
        check(walked == 400 && walker.horizonBuilds() < 200, "and the horizon was built again only as the eye moved off it: " + walker.horizonBuilds() + " times in " + walked);
    }

    private static void whatIsBuiltIsWhatAnyTurnOfTheHeadOrANearDoorOpenedWouldShow() {
        check(allRound(house(RoomSight.SEE), 2.5f, 2.5f, 0, 0f, 0f).contains(BATH), "a room behind the eye, through an open door, is built");
        check(!allRound(house(RoomSight.SHUT), 2.5f, 2.5f, 0, 0f, 0f).contains(BATH), "one behind a shut door is not");
        check(allRound(house(RoomSight.SHUT), 2.5f, 2.5f, 0, 0f, 10f).contains(BATH), "unless the door is near enough to be opened before the room could be built");
        Plan row = house(RoomSight.SEE).room(KITCHEN, 0, 7, 1, 9, 2).opening(7, 1, 0, false, RoomSight.SEE);
        check(!allRound(row, 1.5f, 1.5f, 0, 0f, 0f).contains(KITCHEN), "a room out of every line from the eye is not built");
        check(allRound(row, 1.5f, 1.5f, 0, 1.5f, 0f).contains(KITCHEN), "and is once the eye could be a step from where it is");
    }

    /**
     * A hall with a doorway east into a long room, whose door at the far side of the doorway
     * goes back west into a kitchen south of the hall: from the hall a step before the doorway
     * no line reaches the kitchen, and a step through it the kitchen's door is in sight.
     */
    private static void aRoomAStepAwayThroughTwoOpeningsIsBuiltBeforeItShows() {
        Plan plan = new Plan().room(HALL, 0, 1, 1, 4, 4).room(BATH, 0, 5, 1, 8, 7).room(KITCHEN, 0, 1, 5, 4, 7)
                .opening(5, 2, 0, false, RoomSight.SEE).opening(5, 6, 0, false, RoomSight.SEE);
        check(seen(plan, 5.3f, 2.5f, 0, 90f, 0f).contains(KITCHEN), "a step through the doorway, the kitchen is seen through its own door");
        check(!allRound(plan, 4f, 2.5f, 0, 0f, 0f).contains(KITCHEN), "from the hall a step before the doorway it is not");
        check(allRound(plan, 4f, 2.5f, 0, 1.5f, 0f).contains(KITCHEN), "and is built there, a step and a bit before it shows");
    }

    /**
     * Houses of rooms with doors open, shut and none at random, and places inside them: every
     * room a line reaches from an eye a step from the place, in any way it looks, is among what
     * the walk round the place builds. A house alone, so no other building stands in the air.
     */
    private static void whatAnEyeAStepFromThePlaceSeesInAHouseIsBuilt() {
        Random random = new Random(11);
        float grow = 1.5f;
        int[] states = {RoomSight.SOLID, RoomSight.SHUT, RoomSight.SEE, RoomSight.SEE};
        int eyes = 0, misses = 0;
        for (int house = 0; house < 30; house++) {
            Plan plan = new Plan();
            long id = 0;
            for (int z = 0; z < 2; z++) {
                // Nine rooms a floor, three by three, of random sizes.
                int a = 2 + random.nextInt(3), b = a + 2 + random.nextInt(3), c = 2 + random.nextInt(3), d = c + 2 + random.nextInt(3);
                int[] xs = {0, a, b, 12}, ys = {0, c, d, 12};
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        plan.room(id++, z, xs[i], ys[j], xs[i + 1] - 1, ys[j + 1] - 1);
                    }
                }
                for (int k = 0; k < 24; k++) {
                    plan.opening(random.nextInt(13), random.nextInt(13), z, random.nextBoolean(), states[random.nextInt(states.length)]);
                }
            }
            if (random.nextBoolean()) {
                plan.hole(1 + random.nextInt(10), 1 + random.nextInt(10), 1);
            }
            RoomSight.Openings openings = plan.openings();
            RoomSight.Blocks blocks = plan.blocks();
            for (int place = 0; place < 20; place++) {
                int z = random.nextInt(2);
                float x = 0.2f + random.nextFloat() * 11.6f, y = 0.2f + random.nextFloat() * 11.6f, up = z * ViewCamera.LEVEL_HEIGHT + 1.6f;
                int squareX = (int) Math.floor(x), squareY = (int) Math.floor(y);
                RoomSight.Seen round = new RoomSight.Seen();
                new RoomSight().walkAllRound(plan, openings, blocks, x, y, up, plan.region(squareX, squareY, z), plan.building(squareX, squareY, z), grow, 0f, round);
                for (int e = 0; e < 24; e++) {
                    double way = random.nextDouble() * Math.PI * 2, far = grow * Math.sqrt(random.nextDouble());
                    float eyeX = x + (float) (far * Math.cos(way)), eyeY = y + (float) (far * Math.sin(way));
                    if (!walkable(plan, x, y, eyeX, eyeY, z)) {
                        continue;
                    }
                    ViewCamera.Pose eye = new ViewCamera.Pose();
                    ViewCamera.clean(eye, eyeX, eyeY, up, (float) (random.nextDouble() * Math.PI * 2), (float) Math.toRadians(-30 + random.nextDouble() * 60));
                    RoomSight.Seen lines = new RoomSight.Seen();
                    RoomRays.trace(plan, eye, FOV * ASPECT, FOV, 96, 54, 40f, z, lines);
                    eyes++;
                    for (long room = 0; room < id; room++) {
                        if (lines.contains(room) && !round.contains(room)) {
                            misses++;
                        }
                    }
                }
            }
        }
        check(eyes > 8000, "eyes were looked from: " + eyes);
        check(misses == 0, "no room an eye a step from the place sees is left unbuilt: " + misses + " left out");
    }

    /** Whether the eye can go straight from (x, y) to (toX, toY) on a level and stay inside the house, crossing only edges that let sight through. */
    private static boolean walkable(Plan plan, float x, float y, float toX, float toY, int z) {
        int atX = (int) Math.floor(x), atY = (int) Math.floor(y);
        if (plan.region((int) Math.floor(toX), (int) Math.floor(toY), z) == RoomSight.OUTSIDE) {
            return false;
        }
        for (int i = 1; i <= 64; i++) {
            float t = i / 64f;
            int nextX = (int) Math.floor(x + (toX - x) * t), nextY = (int) Math.floor(y + (toY - y) * t);
            if (nextX != atX) {
                if (plan.edge(Math.max(nextX, atX), atY, z, false) != RoomSight.SEE) {
                    return false;
                }
                atX = nextX;
            }
            if (nextY != atY) {
                if (plan.edge(atX, Math.max(nextY, atY), z, true) != RoomSight.SEE) {
                    return false;
                }
                atY = nextY;
            }
        }
        return true;
    }

    private static void roomsKeptForBuildingGoAfterAWhileUnfound() {
        RoomSight.Kept kept = new RoomSight.Kept();
        RoomSight.Seen found = new RoomSight.Seen(), entered = new RoomSight.Seen(), left = new RoomSight.Seen();
        found.add(HALL);
        found.add(BATH);
        kept.found(found, 0L, entered);
        check(kept.contains(HALL) && kept.contains(BATH) && entered.size() == 2, "rooms found are kept, and said to have come in");
        found.clear();
        found.add(HALL);
        entered.clear();
        kept.found(found, RoomSight.Kept.KEEP_NANOS - 1, entered);
        kept.expire(RoomSight.Kept.KEEP_NANOS - 1, left);
        check(entered.size() == 0 && left.size() == 0 && kept.contains(BATH), "a room not found again is kept for a while");
        kept.expire(RoomSight.Kept.KEEP_NANOS, left);
        check(left.contains(BATH) && !kept.contains(BATH) && kept.contains(HALL), "and goes once it has not been found for that long, and is said to have gone");
        for (long room = 100; room < 1100; room++) {
            found.add(room);
        }
        kept.found(found, RoomSight.Kept.KEEP_NANOS, entered);
        kept.expire(2 * RoomSight.Kept.KEEP_NANOS - 1, left);
        check(kept.size() == 1001 && kept.contains(1099L), "however many rooms are kept, each keeps the time it was found: " + kept.size());
    }

    private static void openingsAreFoundInWallsAndNotWhereThereIsNone() {
        RoomSight.Openings found = house(RoomSight.SHUT).openings();
        int doors = 0, ups = 0, others = 0;
        for (int i = 0; i < found.count; i++) {
            if (found.kind[i] == RoomSight.WEST && found.x[i] == 5 && found.y[i] == 2) {
                doors++;
            } else if (found.kind[i] == RoomSight.UP) {
                ups++;
            } else {
                others++;
            }
        }
        check(doors == 1, "the shut door is an opening, to be asked again whether it is open: " + doors);
        check(others == 0, "a wall is none: " + others);
        check(ups == 0, "and a roof over a room is none: " + ups);
        RoomSight.Openings well = new Plan().room(HALL, 0, 1, 1, 2, 2).room(OVER, 1, 1, 1, 2, 2).hole(1, 1, 1).openings();
        check(well.count == 1 && well.kind[0] == RoomSight.UP && well.here[0] == HALL && well.there[0] == OVER, "the well of a stair is an opening up to the room above");
    }

    /** Streets of houses with rooms, doors and windows open and shut at random, and eyes anywhere looking any way. */
    private static void noRoomALineReachesIsLeftOutInARandomTown() {
        Random random = new Random(42);
        int views = 0;
        long seenSum = 0, roomsSum = 0;
        for (int town = 0; town < 12; town++) {
            Plan plan = new Plan();
            long id = 0;
            int[] states = {RoomSight.SOLID, RoomSight.SHUT, RoomSight.SEE};
            for (int house = 0; house < 6; house++) {
                int ox = (house % 3) * 12, oy = (house / 3) * 12;
                for (int z = 0; z < 2; z++) {
                    // Four rooms a floor, two by two, of random sizes.
                    int split = 2 + random.nextInt(5), down = 2 + random.nextInt(5);
                    plan.room(id++, z, ox, oy, ox + split - 1, oy + down - 1).room(id++, z, ox + split, oy, ox + 7, oy + down - 1)
                            .room(id++, z, ox, oy + down, ox + split - 1, oy + 7).room(id++, z, ox + split, oy + down, ox + 7, oy + 7);
                    for (int k = 0; k < 10; k++) {
                        boolean north = random.nextBoolean();
                        int x = ox + random.nextInt(9), y = oy + random.nextInt(9);
                        plan.opening(x, y, z, north, states[random.nextInt(3)]);
                    }
                }
                if (random.nextBoolean()) {
                    plan.hole(ox + 1, oy + 1, 1);
                }
            }
            for (int view = 0; view < 25; view++) {
                int z = random.nextInt(2);
                float x = -6f + random.nextFloat() * 42f, y = -6f + random.nextFloat() * 30f;
                float yaw = random.nextFloat() * 360f, pitch = -30f + random.nextFloat() * 60f;
                RoomSight.Seen walked = seen(plan, x, y, z, yaw, pitch);
                // What the walk round the eye finds is built: every room a line of the picture reaches has to be among them.
                RoomSight.Seen round = allRound(plan, x, y, z, 0f, 0f), lines = new RoomSight.Seen();
                ViewCamera.Pose eye = new ViewCamera.Pose();
                ViewCamera.clean(eye, x, y, z * ViewCamera.LEVEL_HEIGHT + 1.6f, (float) Math.toRadians(yaw), (float) Math.toRadians(pitch));
                RoomRays.trace(plan, eye, FOV * ASPECT, FOV, 160, 90, 80f, z, lines);
                for (long room = 0; room < id; room++) {
                    if (lines.contains(room)) {
                        check(round.contains(room), "a room a line of the picture reaches is built: room " + room + " from (" + x + ", " + y + ")");
                    }
                }
                views++;
                seenSum += walked.size();
                roomsSum += id;
            }
        }
        check(views == 300, "every view was walked");
        check(seenSum * 2 < roomsSum, "and the walk leaves out most rooms of a town from a place in it: " + seenSum + " of " + roomsSum);
    }

    private static void aSetOfRoomsHoldsWhatIsAddedUntilItIsCleared() {
        RoomSight.Seen seen = new RoomSight.Seen();
        for (long id = 0; id < 1000; id += 3) {
            check(seen.add(id * 7919L), "a room is added once");
        }
        check(!seen.add(0L) && seen.size() == 334, "and not twice");
        boolean all = true;
        for (long id = 0; id < 1000; id++) {
            all &= seen.contains(id * 7919L) == (id % 3 == 0);
        }
        check(all, "it holds what was added and nothing else");
        seen.clear();
        check(seen.size() == 0 && !seen.contains(0L) && !seen.contains(3L * 7919L), "and nothing once cleared");
    }

    /** RoomMap keeps each chunk it read; a map let go of, as the picture stops, keeps no chunk and no map of the save it was read from. */
    private static void aMapLetGoOfHoldsNothingOfTheWorldItWasReadFrom() {
        RoomMap map = new RoomMap();
        zombie.iso.IsoCell cell = new zombie.iso.IsoCell();
        zombie.iso.IsoChunk chunk = new zombie.iso.IsoChunk();
        chunk.wx = 3;
        chunk.wy = 4;
        chunk.loaded = true;
        map.begin(cell, Terrain.LEVEL);
        int read = map.read(chunk, null, null, true, 0L, true);
        check(map.read(chunk, null, null, true, 1L, true) == read, "a chunk read is kept while it is the same chunk");
        java.lang.ref.WeakReference<Object> chunkKept = new java.lang.ref.WeakReference<>(chunk), cellKept = new java.lang.ref.WeakReference<>(cell);
        map.forget();
        chunk = null;
        cell = null;
        for (int tries = 0; tries < 20 && (chunkKept.get() != null || cellKept.get() != null); tries++) {
            System.gc();
        }
        check(chunkKept.get() == null && cellKept.get() == null, "once the map is let go of, nothing keeps the chunk or the map it was read from");
        zombie.iso.IsoChunk again = new zombie.iso.IsoChunk();
        again.wx = 3;
        again.wy = 4;
        map.begin(new zombie.iso.IsoCell(), Terrain.LEVEL);
        check(map.read(again, null, null, true, 2L, true) > read, "and a chunk of the next world in the same place is read afresh");
    }

    /** An opening's heights are the floors' heights where it stands, and a chunk read while the land under it was not known kept them at 0, a hill's height off the ground. */
    private static void aChunkReadBeforeItsLandIsReadAgainOnceItIsWorkedOut() {
        RoomMap map = new RoomMap();
        zombie.iso.IsoCell cell = new zombie.iso.IsoCell();
        zombie.iso.IsoChunk chunk = new zombie.iso.IsoChunk();
        chunk.wx = 913;
        chunk.wy = 1441;
        chunk.loaded = true;
        map.begin(cell, Terrain.LEVEL);
        check(Terrain.LEVEL.versionIfKept(chunk.wx, chunk.wy) == -1, "the test's chunk has no land worked out yet");
        int read = map.read(chunk, null, null, true, 0L, true);
        check(map.read(chunk, null, null, true, 1L, true) == read, "a chunk read is kept while its land is as it was");
        Terrain.LEVEL.corners(cell, chunk.wx, chunk.wy);
        check(map.read(chunk, null, null, true, 2L, true) > read, "and read again once the land under it is worked out");
    }

    /** A frame that has spent its time on reading leaves the rest: a chunk read before keeps that reading, and one never read is said to be unread. */
    private static void aChunkNotToBeReadKeepsWhatItHadAndOneNeverReadIsUnread() {
        RoomMap map = new RoomMap();
        zombie.iso.IsoCell cell = new zombie.iso.IsoCell();
        zombie.iso.IsoChunk chunk = new zombie.iso.IsoChunk();
        chunk.wx = 5;
        chunk.wy = 6;
        chunk.loaded = true;
        map.begin(cell, Terrain.LEVEL);
        check(map.read(chunk, null, null, false, 0L, false) == RoomMap.UNREAD, "a chunk never read and not to be read is unread");
        int read = map.read(chunk, null, null, false, 0L, true);
        check(read != RoomMap.UNREAD, "read, it is not");
        long later = 2_000_000_000L;
        check(map.read(chunk, null, null, false, later, false) == read, "due to be read again and not to be, it keeps what it had");
        check(map.read(chunk, null, null, false, later, true) > read, "and is read again once it may be");
        zombie.iso.IsoChunk again = new zombie.iso.IsoChunk();
        again.wx = 5;
        again.wy = 6;
        again.loaded = true;
        check(map.read(again, null, null, false, later, false) == RoomMap.UNREAD, "another chunk loaded in its place has no reading of its own");
    }

    private static zombie.iso.IsoChunk loadedChunk(int wx, int wy) {
        zombie.iso.IsoChunk chunk = new zombie.iso.IsoChunk();
        chunk.wx = wx;
        chunk.wy = wy;
        chunk.loaded = true;
        return chunk;
    }

    private static void aChunkUnreadAddsNothingToTheFramesOpenings() {
        RoomMap map = new RoomMap();
        map.begin(new zombie.iso.IsoCell(), Terrain.LEVEL);
        zombie.iso.IsoChunk chunk = loadedChunk(7, 8);
        check(map.read(chunk, null, null, false, 0L, false) == RoomMap.UNREAD, "a chunk never read and not to be read is unread");
        RoomSight.Openings openings = new RoomSight.Openings();
        RoomSight.Blocks blocks = new RoomSight.Blocks();
        map.addTo(chunk, openings, blocks);
        check(openings.count == 0 && blocks.count == 0, "it adds no openings and no blocks to the frame's");
        map.addOpeningsTo(chunk, openings, 0, 0);
        check(openings.count == 0, "nor to what is heard");
        map.read(chunk, null, null, false, 0L, true);
        map.addTo(chunk, openings, blocks);
        check(openings.count == 0 && blocks.count == 0, "read, it adds what it has, which on an empty chunk is nothing");
    }

    private static void aChunkMarkedWhileItMayNotBeReadIsUnread() {
        RoomMap map = new RoomMap();
        map.begin(new zombie.iso.IsoCell(), Terrain.LEVEL);
        zombie.iso.IsoChunk chunk = loadedChunk(9, 10);
        int read = map.read(chunk, null, null, true, 0L, true);
        check(map.read(chunk, null, null, true, 0L, false) == read, "unmarked, a chunk not to be read keeps its reading");
        ChunkMarks.marked(chunk, 64L);
        check(map.read(chunk, null, null, true, 0L, false) == RoomMap.UNREAD, "marked as built on since, and not to be read, it is unread");
        check(map.read(chunk, null, null, true, 0L, true) > read, "and is read again once it may be");
    }

    private static void aPictureListsOnlyTheRoomsItSeesInto() {
        SpriteGeometry.MeshSet solid = new SpriteGeometry.MeshSet(), laid = new SpriteGeometry.MeshSet();
        solid.get(4).add(0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);
        solid.room = BATH;
        solid.get(4).add(1f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);
        solid.room = HALL;
        solid.get(4).add(2f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);
        solid.room = RoomSight.OUTSIDE;
        WorldScene.ChunkMeshes build = new WorldScene.ChunkMeshes(0);
        build.fill(solid, laid);
        check(build.runs == 3 && build.runRoom[0] == RoomSight.OUTSIDE && build.runRoom[1] == BATH && build.runRoom[2] == HALL,
                "the contents of each room are runs of their own, though they wear one texture");
        RoomSight.Seen seen = new RoomSight.Seen();
        seen.add(HALL);
        check(ChunkBuffers.listed(build.runRoom[0], seen) && !ChunkBuffers.listed(build.runRoom[1], seen) && ChunkBuffers.listed(build.runRoom[2], seen),
                "a picture lists what is in no room and the rooms it sees into, and leaves out the rest");
        check(ChunkBuffers.listed(build.runRoom[1], null), "and one drawn with every room lists them all");
        build.scrap();
    }

    private static void aWallsEdgeAndAWindowsBlindAreListedByTheRoomsEitherSide() {
        SpriteGeometry.MeshSet solid = new SpriteGeometry.MeshSet(), laid = new SpriteGeometry.MeshSet();
        solid.room = RoomSight.pair(BATH, HALL);
        solid.get(4).add(0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);
        solid.room = RoomSight.blind(BATH);
        solid.get(4).add(1f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);
        solid.room = RoomSight.OUTSIDE;
        WorldScene.ChunkMeshes build = new WorldScene.ChunkMeshes(0);
        build.fill(solid, laid);
        check(build.runs == 2 && Math.min(build.runRoom[0], build.runRoomToo[0]) == Math.min(BATH, HALL)
                && Math.max(build.runRoom[0], build.runRoomToo[0]) == Math.max(BATH, HALL) && build.runUnless[0] == RoomSight.OUTSIDE,
                "what is seen from either of two rooms is a run of both");
        check(build.runRoom[1] == RoomSight.OUTSIDE && build.runRoomToo[1] == RoomSight.OUTSIDE && build.runUnless[1] == BATH,
                "and a window's blind is the open air's, unless its room is seen into");
        check(RoomSight.pair(HALL, BATH) == RoomSight.pair(BATH, HALL) && RoomSight.pair(HALL, HALL) == HALL, "a pair is the same either way round, and a room paired with itself is the room");
        ChunkBuffers.Listing listing = new ChunkBuffers.Listing();
        java.util.ArrayList<WorldScene.ChunkMeshes> chunks = new java.util.ArrayList<>(java.util.List.of(build));
        RoomSight.Seen seen = new RoomSight.Seen();
        seen.add(HALL);
        listing.of(chunks, null, false, seen, Integer.MAX_VALUE, SpriteGeometry.NO_BUILDING);
        check(listing.ranges() == 1 && listing.runsOf(0) == 2, "seen into the hall, the edge and the bath's blind are listed");
        seen.add(BATH);
        listing.of(chunks, null, false, seen, Integer.MAX_VALUE, SpriteGeometry.NO_BUILDING);
        check(listing.ranges() == 1 && listing.runsOf(0) == 1, "seen into the bath too, its blind is not");
        listing.of(chunks, null, false, null, Integer.MAX_VALUE, SpriteGeometry.NO_BUILDING);
        check(listing.ranges() == 1 && listing.runsOf(0) == 1, "and a picture of every room, a mirror's or a shadow's, has the room and never the blind");
        build.scrap();
    }
}

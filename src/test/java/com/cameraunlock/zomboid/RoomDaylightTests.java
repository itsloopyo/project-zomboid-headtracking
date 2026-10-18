package com.cameraunlock.zomboid;

import java.util.HashMap;
import java.util.Map;

/** Tests of the daylight a room lets in through its openings: see RoomDaylight. */
final class RoomDaylightTests {
    private RoomDaylightTests() {}

    static void run() {
        Checks.run("aSquareBesideAWindowHasMoreDaylightThanOneAcrossTheRoom", RoomDaylightTests::aSquareBesideAWindowHasMoreDaylightThanOneAcrossTheRoom);
        Checks.run("aRoomWithNoOpeningToTheDayHasNone", RoomDaylightTests::aRoomWithNoOpeningToTheDayHasNone);
        Checks.run("anOpeningOnEachSideOfTheRoomLetsTheDayIn", RoomDaylightTests::anOpeningOnEachSideOfTheRoomLetsTheDayIn);
        Checks.run("aRoomIsLitThroughAnOpenDoorFromADaylitRoom", RoomDaylightTests::aRoomIsLitThroughAnOpenDoorFromADaylitRoom);
        Checks.run("aTallRoomIsLitByTheWindowsOfTheLevelOverIt", RoomDaylightTests::aTallRoomIsLitByTheWindowsOfTheLevelOverIt);
        Checks.run("theEdgeOfTheLoadedMapIsNoOpening", RoomDaylightTests::theEdgeOfTheLoadedMapIsNoOpening);
        Checks.run("aBoardedWindowLetsInLessThanABareOne", RoomDaylightTests::aBoardedWindowLetsInLessThanABareOne);
        Checks.run("thereIsNoDaylightAtNight", RoomDaylightTests::thereIsNoDaylightAtNight);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static final long ROOM = 1, NEXT = 2, OVER = 3;
    private static final int FAR = 100_000;
    private static final RoomDaylight.Loaded ALL = new RoomDaylight.Loaded(-FAR, -FAR, FAR, FAR);
    /** Each room's box: ROOM 1 to 8 across and 1 to 6 down, NEXT east of it 9 to 12, OVER the level over ROOM. */
    private static final Map<Long, int[]> BOXES = Map.of(ROOM, new int[] {1, 1, 8, 6}, NEXT, new int[] {9, 1, 12, 6}, OVER, new int[] {1, 1, 8, 6});

    private static RoomSightTests.Plan house() {
        return new RoomSightTests.Plan().room(ROOM, 0, 1, 1, 8, 6).room(NEXT, 0, 9, 1, 12, 6);
    }

    /** A plan as the shares read it: an edge sight passes lets all the day through, but where told otherwise. */
    private static final class Lit implements RoomDaylight.Edges {
        final RoomSightTests.Plan plan;
        final Map<String, Float> through = new HashMap<>();

        Lit(RoomSightTests.Plan plan) {
            this.plan = plan;
        }

        Lit passing(int x, int y, int z, boolean north, float share) {
            through.put(x + "," + y + "," + z + "," + north, share);
            return this;
        }

        @Override
        public float through(int x, int y, int z, boolean north) {
            Float told = through.get(x + "," + y + "," + z + "," + north);
            return told != null ? told : plan.edge(x, y, z, north) == RoomSight.SEE ? 1f : 0f;
        }

        @Override
        public long region(int x, int y, int z) {
            return plan.region(x, y, z);
        }

        @Override
        public int edge(int x, int y, int z, boolean north) {
            return plan.edge(x, y, z, north);
        }

        @Override
        public boolean floored(int x, int y, int z) {
            return plan.floored(x, y, z);
        }

        @Override
        public float floorUp(float x, float y, int z) {
            return plan.floorUp(x, y, z);
        }

        @Override
        public long building(int x, int y, int z) {
            return plan.building(x, y, z);
        }
    }

    private static RoomDaylight.Shares shares(RoomDaylight.Edges map, int x, int y, RoomDaylight.Loaded loaded) {
        return RoomDaylight.of(map, (room, box) -> System.arraycopy(BOXES.get(room), 0, box, 0, 4), map.region(x, y, 0), 0, loaded, new RoomDaylight.Scratch());
    }

    private static RoomDaylight.Shares shares(RoomSightTests.Plan plan, int x, int y, RoomDaylight.Loaded loaded) {
        return shares(new Lit(plan), x, y, loaded);
    }

    private static RoomDaylight.Shares shares(RoomSightTests.Plan plan) {
        return shares(plan, 4, 3, ALL);
    }

    private static void aSquareBesideAWindowHasMoreDaylightThanOneAcrossTheRoom() {
        // A window in the north wall over square 4, 1.
        RoomDaylight.Shares lit = shares(house().opening(4, 1, 0, true, RoomSight.SEE));
        float near = lit.at(4, 1), across = lit.at(4, 6), corner = lit.at(8, 6);
        check(near > 0.4f && near <= RoomDaylight.MOST, "the square under the window has most of the day: " + near);
        check(across > 0f && across < near / 2f, "across the room it has less, and some: " + across);
        check(corner < across, "and least in the far corner: " + corner);
    }

    private static void aRoomWithNoOpeningToTheDayHasNone() {
        check(shares(house()).at(4, 3) == 0f, "a room walled all round has none");
        check(shares(house().opening(4, 1, 0, true, RoomSight.SHUT)).at(4, 1) == 0f, "nor one whose window has its curtain drawn or whose door is shut");
        // A doorway in the east wall, the west edge of 9, 3, leads to the next room, which has no window.
        check(shares(house().opening(9, 3, 0, false, RoomSight.SEE)).at(8, 3) == 0f, "nor one whose only opening is to a room with none");
    }

    private static void anOpeningOnEachSideOfTheRoomLetsTheDayIn() {
        // The south edge of a square is the north edge of the one below it, the east edge the west edge of the one beside.
        RoomSightTests.Plan open = new RoomSightTests.Plan().room(ROOM, 0, 1, 1, 8, 6)
                .opening(4, 1, 0, true, RoomSight.SEE).opening(1, 3, 0, false, RoomSight.SEE).opening(4, 7, 0, true, RoomSight.SEE).opening(9, 3, 0, false, RoomSight.SEE);
        RoomDaylight.Shares lit = shares(open);
        check(lit.at(4, 1) > 0.4f, "by the north window");
        check(lit.at(1, 3) > 0.4f, "by the west window");
        check(lit.at(4, 6) > 0.4f, "by the south window");
        check(lit.at(8, 3) > 0.4f, "by the east window");
    }

    private static void aRoomIsLitThroughAnOpenDoorFromADaylitRoom() {
        // NEXT has a window in its north wall at 10, 1; ROOM opens into it at the west edge of 9, 2.
        RoomSightTests.Plan plan = house().opening(10, 1, 0, true, RoomSight.SEE).opening(9, 2, 0, false, RoomSight.SEE);
        RoomDaylight.Shares lit = shares(plan);
        RoomDaylight.Shares window = shares(plan, 10, 2, ALL);
        float door = lit.at(8, 2);
        check(door > 0.05f && door < window.at(9, 2), "the square inside the door has some of the next room's day, less than it has: " + door);
        check(lit.at(1, 6) < door, "and the far corner less again");
        RoomSightTests.Plan shut = house().opening(10, 1, 0, true, RoomSight.SEE).opening(9, 2, 0, false, RoomSight.SHUT);
        check(shares(shut).at(8, 2) == 0f, "with the door shut, none");
    }

    private static void aTallRoomIsLitByTheWindowsOfTheLevelOverIt() {
        // The level over ROOM is a room of the same building with no floor, and a window in its north wall at 4, 1.
        RoomSightTests.Plan tall = new RoomSightTests.Plan().room(ROOM, 0, 1, 1, 8, 6).room(OVER, 1, 1, 1, 8, 6).opening(4, 1, 1, true, RoomSight.SEE);
        for (int x = 1; x <= 8; x++) {
            for (int y = 1; y <= 6; y++) {
                tall.hole(x, y, 1);
            }
        }
        RoomDaylight.Shares lit = shares(tall);
        check(lit.at(4, 1) > 0.1f && lit.at(4, 1) < RoomDaylight.MOST * 0.6f, "the floor under the high window is lit, less than beside a window of its own: " + lit.at(4, 1));
        RoomSightTests.Plan floored = new RoomSightTests.Plan().room(ROOM, 0, 1, 1, 8, 6).room(OVER, 1, 1, 1, 8, 6).opening(4, 1, 1, true, RoomSight.SEE);
        check(shares(floored).at(4, 1) == 0f, "a window over a floor lights the room over it, not this one");
    }

    private static void aBoardedWindowLetsInLessThanABareOne() {
        RoomSightTests.Plan plan = house().opening(4, 1, 0, true, RoomSight.SEE);
        float bare = shares(plan, 4, 3, ALL).at(4, 1);
        float boarded = shares(new Lit(plan).passing(4, 1, 0, true, 0.1f), 4, 3, ALL).at(4, 1);
        check(boarded > 0f && boarded < bare / 3f, "a window boarded to slits lets in a little: " + boarded + " of " + bare);
        check(shares(new Lit(plan).passing(4, 1, 0, true, 0f), 4, 3, ALL).at(4, 1) == 0f, "one under a sheet of metal none");
    }

    private static void thereIsNoDaylightAtNight() {
        check(Evening.day(SkyState.FULL_NIGHT) == 0f && Evening.day(-0.9f) == 0f, "at full night there is none of the day");
        check(Evening.day(0.3f) == 1f && Evening.day(0.9f) == 1f, "with the sun up, all of it");
        float dusk = Evening.day((SkyState.FULL_NIGHT + 0.3f) / 2f);
        check(dusk > 0.2f && dusk < 0.8f, "and between, some: " + dusk);
    }

    private static void theEdgeOfTheLoadedMapIsNoOpening() {
        // The map is loaded from x 3 on: the room's squares west of that are not there, and read as in no room.
        RoomDaylight.Shares cut = shares(house(), 4, 3, new RoomDaylight.Loaded(3, -FAR, FAR, FAR));
        check(cut.at(3, 3) == 0f && cut.at(5, 3) == 0f, "a room cut by the edge of the loaded map has no daylight from it");
    }
}

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
        Checks.run("sharesWorkedOutAgainFromTheSameOpeningsAreKept", RoomDaylightTests::sharesWorkedOutAgainFromTheSameOpeningsAreKept);
        Checks.run("aShareChangeMarksTheLightOfTheRoomsChunksAndTellsNone", RoomDaylightTests::aShareChangeMarksTheLightOfTheRoomsChunksAndTellsNone);
        Checks.run("aLowSunTurnsARoomsDaylightGold", RoomDaylightTests::aLowSunTurnsARoomsDaylightGold);
        Checks.run("aScratchKeptFromRoomToRoomReadsTheMapAfresh", RoomDaylightTests::aScratchKeptFromRoomToRoomReadsTheMapAfresh);
    }

    private static String source(String file) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cameraunlock/zomboid", file)).replaceAll("\\s+", "");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
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

    private static RoomDaylight.Shares again(RoomDaylight.Edges map, RoomDaylight.Shares was) {
        return RoomDaylight.of(map, (room, box) -> System.arraycopy(BOXES.get(room), 0, box, 0, 4), ROOM, 0, ALL, new RoomDaylight.Scratch(), was);
    }

    private static void sharesWorkedOutAgainFromTheSameOpeningsAreKept() {
        RoomSightTests.Plan plan = house().opening(4, 1, 0, true, RoomSight.SEE).opening(10, 1, 0, true, RoomSight.SEE).opening(9, 2, 0, false, RoomSight.SEE);
        RoomDaylight.Shares was = shares(plan);
        check(again(new Lit(plan), was) == was, "the same openings keep the shares worked out from them");
        Lit boarded = new Lit(plan).passing(4, 1, 0, true, 0.3f);
        RoomDaylight.Shares changed = again(boarded, was), fresh = shares(boarded, 4, 3, ALL);
        check(changed != was && changed.sameAs(fresh), "a window letting in less makes them again, as they are made from nothing");
        RoomSightTests.Plan farther = house().opening(4, 1, 0, true, RoomSight.SEE).opening(10, 1, 0, true, RoomSight.SEE).opening(9, 2, 0, false, RoomSight.SHUT);
        check(again(new Lit(farther), was) != was, "and so does the door to the next room shut");
    }

    private static void aShareChangeMarksTheLightOfTheRoomsChunksAndTellsNone() {
        RoomSightTests.Plan plan = house().opening(4, 1, 0, true, RoomSight.SEE);
        RoomDaylight daylight = new RoomDaylight((room, box) -> System.arraycopy(BOXES.get(room), 0, box, 0, 4));
        zombie.iso.IsoGridSquare square = new zombie.iso.IsoCell().square(4, 3, 0);
        square.setRoomID(ROOM);
        // Each asking comes after the shares of the one before are due, so they are worked out again.
        long due = 10_000_000_000L;
        float first = daylight.share(new Lit(plan), square, -FAR, -FAR, 2 * FAR, 1L);
        int lit = lightMarksRoundTheRoom(), told = ChunkMarks.told(0, 0) + ChunkMarks.told(1, 0);
        float again = daylight.share(new Lit(plan), square, -FAR, -FAR, 2 * FAR, due);
        check(again == first && lightMarksRoundTheRoom() == lit, "shares worked out again the same mark nothing");
        float boarded = daylight.share(new Lit(plan).passing(4, 1, 0, true, 0.3f), square, -FAR, -FAR, 2 * FAR, 2 * due);
        check(boarded < first, "a window letting in less gives the room less: " + first + " then " + boarded);
        check(lightMarksRoundTheRoom() == lit + 2, "and marks the light of the room's two chunks: " + (lightMarksRoundTheRoom() - lit));
        check(ChunkMarks.told(0, 0) + ChunkMarks.told(1, 0) == told, "and tells neither, as nothing but the light of their builds is otherwise");
    }

    /** The light marks round the chunk at 0, 0, which ROOM's box (1 to 8 across, 1 to 6 down) shares with the chunk at 1, 0. */
    private static int lightMarksRoundTheRoom() {
        return ChunkMarks.around(0, 0) - ChunkMarks.thingsAround(0, 0);
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

    private static void aScratchKeptFromRoomToRoomReadsTheMapAfresh() {
        Lit lit = new Lit(house().opening(4, 1, 0, true, RoomSight.SEE).opening(12, 3, 0, false, RoomSight.SEE).opening(9, 2, 0, false, RoomSight.SEE));
        java.util.Set<String> gone = new java.util.HashSet<>();
        RoomDaylight.Edges map = new RoomDaylight.Edges() {
            @Override
            public float through(int x, int y, int z, boolean north) {
                return lit.through(x, y, z, north);
            }

            @Override
            public long region(int x, int y, int z) {
                return gone.contains(x + "," + y + "," + z) ? RoomSight.OUTSIDE : lit.region(x, y, z);
            }

            @Override
            public int edge(int x, int y, int z, boolean north) {
                return lit.edge(x, y, z, north);
            }

            @Override
            public boolean floored(int x, int y, int z) {
                return lit.floored(x, y, z);
            }

            @Override
            public float floorUp(float x, float y, int z) {
                return lit.floorUp(x, y, z);
            }

            @Override
            public long building(int x, int y, int z) {
                return lit.building(x, y, z);
            }
        };
        RoomDaylight.Rooms boxes = (room, box) -> System.arraycopy(BOXES.get(room), 0, box, 0, 4);
        RoomDaylight.Scratch kept = new RoomDaylight.Scratch();
        java.util.Random random = new java.util.Random(5);
        for (int round = 0; round < 200; round++) {
            // Squares of both rooms and round them come and go from the map, and the door between them opens by steps.
            gone.add((random.nextInt(14)) + "," + random.nextInt(8) + "," + random.nextInt(2));
            if (random.nextInt(3) == 0) {
                gone.clear();
            }
            lit.passing(9, 3, 0, false, random.nextInt(5) * 0.25f);
            for (long room : new long[] {ROOM, NEXT, ROOM}) {
                RoomDaylight.Shares reused = RoomDaylight.of(map, boxes, room, 0, ALL, kept);
                RoomDaylight.Shares fresh = RoomDaylight.of(map, boxes, room, 0, ALL, new RoomDaylight.Scratch());
                check(reused.sameAs(fresh), "round " + round + ", room " + room + ": shares worked out with the scratch of the last room are those worked out afresh");
            }
        }
    }

    private static void aLowSunTurnsARoomsDaylightGold() {
        float noon = SkyState.lowSun(0.8f, 0f, 0f), low = SkyState.lowSun(0.08f, 0f, 0f), night = SkyState.lowSun(-0.3f, 0f, 0f);
        check(noon < 0.01f && night == 0f && low > 0.8f, "the sun's gold is the low sun's, neither noon's nor the night's: " + noon + " " + low + " " + night);
        check(SkyState.lowSun(0.5f, 0f, 0f) < 0.15f * low,
                "it is gone once the sun is half way up: " + SkyState.lowSun(0.5f, 0f, 0f));
        float clouded = SkyState.lowSun(0.08f, 0.7f, 0f);
        check(clouded < 0.45f * low && clouded > 0f, "the heaviest cloud with no rain lets little of it through: " + clouded);
        check(SkyState.lowSun(0.08f, 0f, 1f) == 0f && SkyState.lowSun(0.08f, 0f, 0.5f) < 0.55f * low, "nor does fog, which greys the street: " + SkyState.lowSun(0.08f, 0f, 0.5f));
        check(Evening.lowSunGreen(0f) == 1f && Evening.lowSunBlue(0f) == 1f, "with no low sun a room's daylight is as the game has it");
        float green = Evening.lowSunGreen(low), blue = Evening.lowSunBlue(low);
        check(green < 0.8f && blue < 0.6f && blue < green, "a low sun takes blue out of a room's daylight, and less green: " + green + " " + blue);
        check(Math.abs(Math.log(Evening.lowSunGreen(1f)) - 0.5 * Math.log(Evening.lowSunBlue(1f))) < 1e-6,
                "and its green is halfway between red's and blue's in stops, which the indoor balance reads as warm and leaves: " + Evening.LOW_SUN_GREEN);
        float hazy = Evening.lowSunBlue(0.5f * low);
        check(hazy > blue && hazy < 1f, "a sun half as gold takes half as much: " + hazy);

        SkyState sky = new SkyState();
        sky.daylightKept = 0.5f;
        sky.roomKept = 0.45f;
        sky.roomGold = 1f;
        float[] kept = new float[4];
        Pipeline.roomKept(sky, kept);
        check(kept[1] == Evening.lowSunGreen(1f) && kept[2] == Evening.lowSunBlue(1f) && Math.abs(kept[0] - Math.pow(0.5 * 0.45, 2.2)) < 1e-6,
                "the world shader is told the gold in uRoomKept.yz beside what a room keeps of the daylight: " + kept[0] + " " + kept[1] + " " + kept[2]);
        sky.roomGold = 0f;
        Pipeline.roomKept(sky, kept);
        check(kept[1] == 1f && kept[2] == 1f, "and none with no gold");
        String scene = source("WorldScene.java");
        check(scene.contains("sky.roomGold=Quality.plain(view.index)?0f:sky.lowSun;"), "Low has no gold: it lights a room as the game does");
        for (String reader : new String[] {"Pipeline.java", "Characters.java", "HeldLight.java"}) {
            check(!java.util.regex.Pattern.compile("\\.lowSun(?![A-Za-z])").matcher(source(reader)).find(), reader + " takes the gold from roomGold, which is none in Low, not from lowSun");
        }

        String world = PipelineShaders.WORLD_FRAGMENT_MAIN.replaceAll("\\s+", "");
        int at = world.indexOf("vec3inRoom=");
        String inRoom = at < 0 ? "" : world.substring(at, world.indexOf(';', at));
        check(world.indexOf("uRoomKept.yz") == world.lastIndexOf("uRoomKept.yz") && inRoom.contains("daylight*(1.0-vec3(1.0,uRoomKept.yz))"),
                "the low sun colours only a room's light, not the open ground's, and of it only what is daylight, not a lamp's: " + inRoom);

        float[] stood = new float[3];
        Evening.stood(0.2f, 0.8f, 0.25f, 1f, stood, 0);
        check(stood[0] == 0.2f && Math.abs(stood[1] - 0.2f * Math.pow(Evening.LOW_SUN_GREEN, 1 / 2.2)) < 1e-5f && Math.abs(stood[2] - 0.2f * Math.pow(Evening.LOW_SUN_BLUE, 1 / 2.2)) < 1e-5f,
                "a person in a room lit by the day alone is as gold as the room: " + stood[0] + " " + stood[1] + " " + stood[2]);
        Evening.stood(0.95f, 0.18f, 0.45f, 1f, stood, 0);
        check(stood[1] > 0.99f * 0.95f && stood[2] > 0.99f * 0.95f, "in a room lit by its lamp at dusk they keep the lamp's light: " + stood[1] + " " + stood[2]);
        Evening.stood(0.5f, 0.5f, 0.45f, 1f, stood, 0);
        float lampAndDay = 1f - stood[2] / 0.5f;
        check(lampAndDay > 0.05f && lampAndDay < 1f - (float) Math.pow(Evening.LOW_SUN_BLUE, 1 / 2.2) - 0.05f,
                "and where both light them, the day's share of it alone goes gold: " + stood[2]);
        Evening.stood(0.2f, 0.8f, 0.25f, 0f, stood, 0);
        check(stood[1] == 0.2f && stood[2] == 0.2f, "with no gold the light is as it was said");
        float[] held = new float[HeldLight.LIGHTS];
        HeldLight.light(new float[] {1f, 0.6f, 0.6f, 0.6f, 0f}, 0.8f, 0.8f, 0.8f, 1f, 0.45f, 1f, held);
        check(held[HeldLight.STOOD + 2] < 0.8f * held[HeldLight.STOOD] && held[HeldLight.STOOD] == held[HeldLight.LIGHT],
                "what the viewer holds in a sunlit room is gold as the room is: " + held[HeldLight.STOOD] + " " + held[HeldLight.STOOD + 2]);
        HeldLight.light(new float[] {1f, 0.6f, 0.6f, 0.6f, 1f}, 0.8f, 0.8f, 0.8f, 1f, 0.45f, 1f, held);
        check(held[HeldLight.STOOD + 2] == held[HeldLight.STOOD], "and out of doors as the sun lights it, with no gold of the room's");
        String figures = PipelineShaders.FIGURE_LIGHT_MAIN.replaceAll("\\s+", "");
        check(figures.contains("around=pow(stoodIn,2.2)*(1.0-dayShare*(1.0-vec3(1.0,uFigureGold.zw)))"),
                "the people's light gives a person indoors the gold on the day's share of their light");
    }
}

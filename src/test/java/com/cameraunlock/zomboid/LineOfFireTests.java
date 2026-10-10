package com.cameraunlock.zomboid;

import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.LosUtil.TestResults;

/**
 * Where the first person aim stops, which is the point the game's ballistics are handed: see
 * WorldQuery.LineOfFire. The game keeps a firearm's target only when its own line of sight test finds
 * no wall and no shut door between the shooter and the target, so the aim stops where that test would
 * and nowhere else. Measured in the game, the test the aim used before (whether a character could
 * walk across) said "blocked" across a doorway, an open door and a closed window, so the aim stopped
 * on the opening and the game, handed a point there, found no target behind it.
 */
final class LineOfFireTests {
    private LineOfFireTests() {}

    static void run() {
        Checks.run("theAimAsksTheGamesLineOfFireNotWhetherSomeoneCouldWalkThrough", LineOfFireTests::theAimAsksTheGamesLineOfFireNotWhetherSomeoneCouldWalkThrough);
        Checks.run("aShotStopsWhereTheGamesOwnLineOfFireStops", LineOfFireTests::aShotStopsWhereTheGamesOwnLineOfFireStops);
        Checks.run("aShotGoesOnThroughEveryOpeningAlongAHall", LineOfFireTests::aShotGoesOnThroughEveryOpeningAlongAHall);
        Checks.run("aShotAtASlantGoesThroughADoorwayAndNotTheWallBesideIt", LineOfFireTests::aShotAtASlantGoesThroughADoorwayAndNotTheWallBesideIt);
        Checks.run("aShutGlassDoorPastAWindowLeavesTheShotGoingAsTheGameKeepsItsResult", LineOfFireTests::aShutGlassDoorPastAWindowLeavesTheShotGoingAsTheGameKeepsItsResult);
        Checks.run("besideAirAShotStopsOnlyAtAWallTheEyeCannotSeePast", LineOfFireTests::besideAirAShotStopsOnlyAtAWallTheEyeCannotSeePast);
        Checks.run("aShotBesideAirDoesNotGoByTheWalkersWall", LineOfFireTests::aShotBesideAirDoesNotGoByTheWalkersWall);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** The game classes the shot's edge test calls, by name: what the aim goes by is the game's answer, so it is read off the compiled class. */
    private static Set<String> calledBy(String method) throws Exception {
        byte[] bytes;
        try (InputStream in = WorldQuery.class.getResourceAsStream("WorldQuery.class")) {
            bytes = in.readAllBytes();
        }
        Set<String> called = new HashSet<>();
        for (MethodModel each : ClassFile.of().parse(bytes).methods()) {
            if (!each.methodName().equalsString(method)) {
                continue;
            }
            each.code().ifPresent(code -> code.forEach(element -> {
                if (element instanceof InvokeInstruction invoke) {
                    called.add(invoke.owner().asInternalName() + "." + invoke.name().stringValue());
                }
            }));
        }
        return called;
    }

    private static void theAimAsksTheGamesLineOfFireNotWhetherSomeoneCouldWalkThrough() throws Exception {
        Set<String> called = calledBy("solidToAShot");
        check(called.contains("zombie/iso/IsoGridSquare.testVisionAdjacent"), "the shot's edge test asks the game's own line of sight test, as its firearms do: " + called);
        check(!called.contains("zombie/iso/IsoGridSquare.isBlockedTo") && !called.contains("zombie/iso/IsoGridSquare.isWindowTo"),
                "and not whether a character could walk across, which a doorway, an open door and shut glass all fail");
    }

    /** Whether the game's test of one edge, the first along a line, stops a shot. */
    private static boolean stops(TestResults seen) {
        return new WorldQuery.LineOfFire().stopsAt(seen);
    }

    private static void aShotStopsWhereTheGamesOwnLineOfFireStops() {
        check(!stops(TestResults.Clear), "open floor and a doorway with no door in it let a shot through");
        check(!stops(TestResults.ClearThroughOpenDoor), "so does an open door");
        check(!stops(TestResults.ClearThroughWindow),
                "and a window, open, broken or shut: the game breaks shut glass and the shot goes on");
        check(stops(TestResults.Blocked), "a wall, a drawn curtain or a barricade stops it");
        check(stops(TestResults.ClearThroughClosedDoor), "and so does a shut door, which the game sees past and does not shoot through");
    }

    /** A hall along x, one square wide, with what the game's test answers at each edge east of square x. */
    private static GridTrace.Edges hall(Map<Integer, TestResults> edges) {
        WorldQuery.LineOfFire line = new WorldQuery.LineOfFire();
        line.start();
        return (fromX, fromY, toX, toY) -> fromY != toY || line.stopsAt(edges.getOrDefault(Math.min(fromX, toX), TestResults.Clear));
    }

    private static void aShotGoesOnThroughEveryOpeningAlongAHall() {
        GridTrace trace = new GridTrace();
        Map<Integer, TestResults> edges = new HashMap<>();
        edges.put(1, TestResults.Clear);
        edges.put(3, TestResults.ClearThroughOpenDoor);
        edges.put(5, TestResults.ClearThroughWindow);
        edges.put(7, TestResults.Blocked);
        float stop = trace.firstBlockedEdge(hall(edges), 0.5f, 0.5f, 1f, 0f, 20f);
        check(Math.abs(stop - 7.5f) < 1e-4f, "past a doorway, an open door and a shut window the shot stops at the wall (" + stop + ")");
        edges.put(3, TestResults.ClearThroughClosedDoor);
        stop = trace.firstBlockedEdge(hall(edges), 0.5f, 0.5f, 1f, 0f, 20f);
        check(Math.abs(stop - 3.5f) < 1e-4f, "a shut door met before any window stops it there (" + stop + ")");
        edges.put(3, TestResults.ClearThroughOpenDoor);
        edges.put(5, TestResults.Blocked);
        stop = trace.firstBlockedEdge(hall(edges), 0.5f, 0.5f, 1f, 0f, 20f);
        check(Math.abs(stop - 5.5f) < 1e-4f, "a curtain drawn over the window stops it there (" + stop + ")");
        edges.clear();
        check(trace.firstBlockedEdge(hall(edges), 0.5f, 0.5f, 1f, 0f, 20f) == Float.POSITIVE_INFINITY, "an open hall stops nothing");
    }

    private static void aShotAtASlantGoesThroughADoorwayAndNotTheWallBesideIt() {
        // A wall along x = 5 between squares 4 and 5, with a doorway in it at y = 2.
        GridTrace.Edges wall = (fromX, fromY, toX, toY) -> stops(
                fromY == toY && Math.min(fromX, toX) == 4 && fromY != 2 ? TestResults.Blocked : TestResults.Clear);
        GridTrace trace = new GridTrace();
        // From (2.5, 3.5) to (7.5, 0.9): it crosses x = 5 at y = 2.2, in the doorway, about 27 degrees off square.
        float alongX = 5f, alongY = -2.6f, length = (float) Math.hypot(alongX, alongY);
        check(trace.firstBlockedEdge(wall, 2.5f, 3.5f, alongX / length, alongY / length, length) == Float.POSITIVE_INFINITY,
                "a line through the doorway at a slant reaches the far side");
        // From (2.5, 3.5) to (7.5, 3.5): it meets the wall at y = 3.
        check(Math.abs(trace.firstBlockedEdge(wall, 2.5f, 3.5f, 1f, 0f, 5f) - 2.5f) < 1e-4f, "and one beside it meets the wall");
    }

    private static void aShutGlassDoorPastAWindowLeavesTheShotGoingAsTheGameKeepsItsResult() {
        GridTrace trace = new GridTrace();
        Map<Integer, TestResults> edges = new HashMap<>();
        edges.put(2, TestResults.ClearThroughWindow);
        edges.put(5, TestResults.ClearThroughClosedDoor);
        check(trace.firstBlockedEdge(hall(edges), 0.5f, 0.5f, 1f, 0f, 20f) == Float.POSITIVE_INFINITY,
                "out of a window and on through a shut door with glass in it, the game keeps the window and the shot goes on");
        edges.put(2, TestResults.ClearThroughOpenDoor);
        check(Math.abs(trace.firstBlockedEdge(hall(edges), 0.5f, 0.5f, 1f, 0f, 20f) - 5.5f) < 1e-4f,
                "through an open door the same shut glass door stops it");
        edges.put(2, TestResults.ClearThroughOpenDoor);
        edges.put(4, TestResults.ClearThroughWindow);
        check(trace.firstBlockedEdge(hall(edges), 0.5f, 0.5f, 1f, 0f, 20f) == Float.POSITIVE_INFINITY,
                "and through an open door and then a window it goes on again, as the window is what the game keeps");
        edges.clear();
        edges.put(2, TestResults.ClearThroughWindow);
        edges.put(4, TestResults.Blocked);
        check(Math.abs(trace.firstBlockedEdge(hall(edges), 0.5f, 0.5f, 1f, 0f, 20f) - 4.5f) < 1e-4f, "a wall past a window still stops it");
    }

    /** A square's flags, as the map has them. */
    private static Predicate<IsoFlagType> square(IsoFlagType... flags) {
        Set<IsoFlagType> has = Set.of(flags);
        return has::contains;
    }

    private static void besideAirAShotStopsOnlyAtAWallTheEyeCannotSeePast() {
        // From a square on an upper floor north out over the street, where the map has no square.
        Predicate<IsoFlagType> shutWindowNorth = square(IsoFlagType.collideN, IsoFlagType.windowN, IsoFlagType.transparentN);
        check(!WorldQuery.stopsAShotBesideAir(shutWindowNorth, null, 5, 5, 5, 4), "out of an upper window's shut glass into the air the shot goes on");
        check(!WorldQuery.stopsAShotBesideAir(square(IsoFlagType.collideN, IsoFlagType.doorN), null, 5, 5, 5, 4), "and out of a doorway with its door");
        check(!WorldQuery.stopsAShotBesideAir(square(), null, 5, 5, 5, 4), "and off a balcony with no wall");
        check(WorldQuery.stopsAShotBesideAir(square(IsoFlagType.collideN), null, 5, 5, 5, 4), "a plain wall stops it");
        check(WorldQuery.stopsAShotBesideAir(square(IsoFlagType.collideW), null, 5, 5, 4, 5), "and one on the west edge, west");
        check(!WorldQuery.stopsAShotBesideAir(square(IsoFlagType.collideN), null, 5, 5, 5, 6),
                "a wall on the square's north edge is not met going south, onto the missing square's side");
        // Out of the air into a square through its west edge: an upper window across the street.
        check(!WorldQuery.stopsAShotBesideAir(null, square(IsoFlagType.collideW, IsoFlagType.windowW, IsoFlagType.transparentW), 4, 5, 5, 5),
                "in from the air through a window's glass the shot goes on");
        check(WorldQuery.stopsAShotBesideAir(null, square(IsoFlagType.collideW), 4, 5, 5, 5), "and a wall there stops it");
        check(!WorldQuery.stopsAShotBesideAir(null, null, 4, 5, 5, 5), "air to air stops nothing");
    }

    private static void aShotBesideAirDoesNotGoByTheWalkersWall() throws Exception {
        Set<String> called = calledBy("solidToAShot");
        check(!called.contains("com/cameraunlock/zomboid/WorldQuery.wallBesideAir"),
                "beside air the shot's test is its own, not the eye's, which stops on shut glass and doorways: " + called);
    }
}

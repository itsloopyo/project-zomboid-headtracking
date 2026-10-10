package com.cameraunlock.zomboid;

import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import zombie.iso.LosUtil.TestResults;

/**
 * Where the first person aim stops, which is the point the game's ballistics are handed: see
 * WorldQuery.stopsAShot. The game keeps a firearm's target only when its own line of sight test finds
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

    private static void aShotStopsWhereTheGamesOwnLineOfFireStops() {
        check(!WorldQuery.stopsAShot(TestResults.Clear), "open floor and a doorway with no door in it let a shot through");
        check(!WorldQuery.stopsAShot(TestResults.ClearThroughOpenDoor), "so does an open door");
        check(!WorldQuery.stopsAShot(TestResults.ClearThroughWindow),
                "and a window, open, broken or shut: the game breaks shut glass and the shot goes on");
        check(WorldQuery.stopsAShot(TestResults.Blocked), "a wall, a drawn curtain or a barricade stops it");
        check(WorldQuery.stopsAShot(TestResults.ClearThroughClosedDoor), "and so does a shut door, which the game sees past and does not shoot through");
    }

    /** A hall along x, one square wide, with what the game's test answers at each edge east of square x. */
    private static GridTrace.Edges hall(Map<Integer, TestResults> edges) {
        return (fromX, fromY, toX, toY) -> {
            if (fromY != toY) {
                return true;
            }
            TestResults seen = edges.getOrDefault(Math.min(fromX, toX), TestResults.Clear);
            return WorldQuery.stopsAShot(seen);
        };
    }

    private static void aShotGoesOnThroughEveryOpeningAlongAHall() {
        GridTrace trace = new GridTrace();
        Map<Integer, TestResults> edges = new HashMap<>();
        edges.put(1, TestResults.Clear);
        edges.put(3, TestResults.ClearThroughOpenDoor);
        edges.put(5, TestResults.ClearThroughWindow);
        edges.put(7, TestResults.ClearThroughClosedDoor);
        float stop = trace.firstBlockedEdge(hall(edges), 0.5f, 0.5f, 1f, 0f, 20f);
        check(Math.abs(stop - 7.5f) < 1e-4f, "past a doorway, an open door and a shut window the shot stops at the shut door (" + stop + ")");
        edges.put(5, TestResults.Blocked);
        stop = trace.firstBlockedEdge(hall(edges), 0.5f, 0.5f, 1f, 0f, 20f);
        check(Math.abs(stop - 5.5f) < 1e-4f, "a curtain drawn over the window stops it there (" + stop + ")");
        edges.clear();
        check(trace.firstBlockedEdge(hall(edges), 0.5f, 0.5f, 1f, 0f, 20f) == Float.POSITIVE_INFINITY, "an open hall stops nothing");
    }

    private static void aShotAtASlantGoesThroughADoorwayAndNotTheWallBesideIt() {
        // A wall along x = 5 between squares 4 and 5, with a doorway in it at y = 2.
        GridTrace.Edges wall = (fromX, fromY, toX, toY) -> WorldQuery.stopsAShot(
                fromY == toY && Math.min(fromX, toX) == 4 && fromY != 2 ? TestResults.Blocked : TestResults.Clear);
        GridTrace trace = new GridTrace();
        // From (2.5, 3.5) to (7.5, 0.9): it crosses x = 5 at y = 2.2, in the doorway, about 27 degrees off square.
        float alongX = 5f, alongY = -2.6f, length = (float) Math.hypot(alongX, alongY);
        check(trace.firstBlockedEdge(wall, 2.5f, 3.5f, alongX / length, alongY / length, length) == Float.POSITIVE_INFINITY,
                "a line through the doorway at a slant reaches the far side");
        // From (2.5, 3.5) to (7.5, 3.5): it meets the wall at y = 3.
        check(Math.abs(trace.firstBlockedEdge(wall, 2.5f, 3.5f, 1f, 0f, 5f) - 2.5f) < 1e-4f, "and one beside it meets the wall");
    }
}

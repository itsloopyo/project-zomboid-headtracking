package com.cameraunlock.zomboid;

/** Where a shot crosses the edges between squares, which is where it meets a window: see ShotGlass. */
final class ShotGlassTests {
    private ShotGlassTests() {}

    static void run() {
        Checks.run("aShotCrossesAWestEdgeWhereItsLineMeetsThatX", ShotGlassTests::aShotCrossesAWestEdgeWhereItsLineMeetsThatX);
        Checks.run("aShotCrossesANorthEdgeWhereItsLineMeetsThatY", ShotGlassTests::aShotCrossesANorthEdgeWhereItsLineMeetsThatY);
        Checks.run("theCrossingIsInTheMeasureOfTheWayGiven", ShotGlassTests::theCrossingIsInTheMeasureOfTheWayGiven);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void aShotCrossesAWestEdgeWhereItsLineMeetsThatX() {
        check(near(ShotGlass.crossedAt(2.5f, 0.5f, 1f, 0f, 4, 0, 5, 0), 2.5f), "going east from x 2.5, the edge into square 5 is 2.5 on");
        check(near(ShotGlass.crossedAt(2.5f, 0.5f, -1f, 0f, 1, 0, 0, 0), 1.5f), "going west, the edge out of square 1 is at x 1, 1.5 on");
    }

    private static void aShotCrossesANorthEdgeWhereItsLineMeetsThatY() {
        check(near(ShotGlass.crossedAt(0.5f, 0.2f, 0f, 1f, 0, 2, 0, 3), 2.8f), "going south from y 0.2, the edge into square 3 is 2.8 on");
        check(near(ShotGlass.crossedAt(0.5f, 3.75f, 0f, -1f, 0, 3, 0, 2), 0.75f), "going north, the edge out of square 3 is at y 3, 0.75 on");
    }

    private static void theCrossingIsInTheMeasureOfTheWayGiven() {
        // The ground part of a unit way that also climbs: half of it is along the ground, so the distance doubles.
        check(near(ShotGlass.crossedAt(0.5f, 0.5f, 0.5f, 0f, 0, 0, 1, 0), 1f), "a way with half its length on the ground goes twice as far to the edge");
        float diagonal = (float) Math.sqrt(0.5);
        check(near(ShotGlass.crossedAt(0.5f, 0.5f, diagonal, diagonal, 0, 0, 0, 1), 0.5f / diagonal), "and so does a diagonal, by its share across");
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-5f;
    }
}

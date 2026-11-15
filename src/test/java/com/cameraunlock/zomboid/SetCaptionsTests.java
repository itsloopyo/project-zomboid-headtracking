package com.cameraunlock.zomboid;

/** Tests of how high over a set its lines are written: SetCaptions.top against the heights the sets are drawn at. */
final class SetCaptionsTests {
    private SetCaptionsTests() {}

    static void run() {
        Checks.run("aSetsLinesFollowWhereTheGameStandsTheSet", SetCaptionsTests::aSetsLinesFollowWhereTheGameStandsTheSet);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    /** Pixels of the game's surfaces as metres. */
    private static float metres(float pixels) {
        return pixels / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
    }

    private static void aSetsLinesFollowWhereTheGameStandsTheSet() {
        // A set painted for a table 15 high, lifted 3 onto the low table it stands on (measured in the game, 10777,9768).
        float onTable = SetCaptions.top(true, 15, 3f, true);
        check(near(onTable, metres(18f) + SetCaptions.TELEVISION_HIGH), "a television on a table is topped at the table and its own height: " + onTable);
        // On a chest of drawers the game lifts the same set 19.
        check(SetCaptions.top(true, 15, 19f, true) > onTable, "on a higher piece the same set's top is higher");
        // The game takes a table-top piece with nothing under it down by its own Surface, to the floor.
        float radioOnFloor = SetCaptions.top(true, 34, -34f, false);
        check(near(radioOnFloor, SetCaptions.RADIO_HIGH), "a radio on the floor is topped at its own height over the floor: " + radioOnFloor);
        // The antique console is no table-top piece and stands on the floor.
        check(near(SetCaptions.top(false, 30, 0f, true), SetCaptions.CONSOLE_TOP), "the console's lines go over its cabinet");
        // A radio stacked on that table's set: the game lifts it to stand where the set's top is, 60 here.
        float stacked = SetCaptions.top(true, 34, 60f - 34f, false);
        check(near(stacked, metres(60f) + SetCaptions.RADIO_HIGH), "a radio stacked on a set is topped where the game draws it: " + stacked);
        check(stacked > metres(18f), "a radio on a set stands over the set's foot, not on the table under it");
    }
}

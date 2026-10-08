package com.cameraunlock.zomboid;

/** Tests of where the steadying pass is told the people and vehicles of the picture stood a frame ago. */
final class MoversTests {
    private MoversTests() {}

    static void run() {
        Checks.run("aMoverStoodWhereItWasNotedTheFrameBefore", MoversTests::aMoverStoodWhereItWasNotedTheFrameBefore);
        Checks.run("aMoverFirstSeenStoodWhereItStands", MoversTests::aMoverFirstSeenStoodWhereItStands);
        Checks.run("aMoverThatMissedAFrameIsFirstSeenAgain", MoversTests::aMoverThatMissedAFrameIsFirstSeenAgain);
        Checks.run("theNearestSixteenAreKept", MoversTests::theNearestSixteenAreKept);
        Checks.run("nobodyIsNotedForAPictureThatIsNotSteadied", MoversTests::nobodyIsNotedForAPictureThatIsNotSteadied);
        Checks.run("nobodyIsHeldOnToOnceForgotten", MoversTests::nobodyIsHeldOnToOnceForgotten);
    }

    private static void nobodyIsHeldOnToOnceForgotten() {
        Movers movers = new Movers();
        Object walker = new Object();
        movers.begin(true);
        movers.note(walker, 10f, 20f, 0.5f, 1f, 4f);
        movers.end();
        movers.forget();
        movers.begin(true);
        movers.note(walker, 11f, 20f, 0.5f, 1f, 4f);
        check(movers.found == 1 && movers.before[0] == 11f, "a mover noted again after the view was let go of is first seen: where it stood before is not kept");
    }

    private static void aMoverStoodWhereItWasNotedTheFrameBefore() {
        Movers movers = new Movers();
        Object walker = new Object(), other = new Object();
        movers.begin(true);
        movers.note(walker, 10f, 20f, 0.5f, 1f, 4f);
        movers.note(other, 30f, 40f, 0f, 2f, 9f);
        movers.end();
        movers.begin(true);
        movers.note(other, 31f, 40f, 0f, 2.5f, 9f);
        movers.note(walker, 10.25f, 20.5f, 0.75f, 1.5f, 4f);
        movers.end();
        check(movers.found == 2, "both movers are in the frame: " + movers.found);
        check(row(movers.now, 0, 31f, 40f, 0f, 2.5f) && row(movers.before, 0, 30f, 40f, 0f, 2f), "the first noted stood where it was a frame ago");
        check(row(movers.now, 1, 10.25f, 20.5f, 0.75f, 1.5f) && row(movers.before, 1, 10f, 20f, 0.5f, 1f),
                "the second stood where it was a frame ago, in whatever order the two were noted");
    }

    private static void aMoverFirstSeenStoodWhereItStands() {
        Movers movers = new Movers();
        movers.begin(true);
        movers.note(new Object(), 5f, 6f, 7f, 8f, 1f);
        movers.end();
        check(row(movers.before, 0, 5f, 6f, 7f, 8f), "a mover seen for the first time has not moved");
    }

    private static void aMoverThatMissedAFrameIsFirstSeenAgain() {
        Movers movers = new Movers();
        Object walker = new Object();
        movers.begin(true);
        movers.note(walker, 1f, 1f, 0f, 0f, 1f);
        movers.end();
        movers.begin(true);
        movers.end();
        movers.begin(true);
        movers.note(walker, 9f, 9f, 0f, 0f, 1f);
        movers.end();
        check(row(movers.before, 0, 9f, 9f, 0f, 0f), "a mover out of the picture for a frame did not cross it in one");
    }

    private static void theNearestSixteenAreKept() {
        Movers movers = new Movers();
        Object[] crowd = new Object[Movers.MOST + 8];
        for (int frame = 0; frame < 2; frame++) {
            movers.begin(true);
            // The furthest first: the nearest come after the list is full.
            for (int i = crowd.length - 1; i >= 0; i--) {
                if (frame == 0) {
                    crowd[i] = new Object();
                }
                movers.note(crowd[i], i + frame * 0.5f, 0f, 0f, 0f, (float) i * i);
            }
            movers.end();
        }
        check(movers.found == Movers.MOST, "no more than the list holds: " + movers.found);
        boolean[] kept = new boolean[crowd.length];
        for (int i = 0; i < movers.found; i++) {
            int who = Math.round(movers.before[i * 4]);
            kept[who] = true;
            check(movers.now[i * 4] == who + 0.5f, "each kept mover has its own place a frame ago: " + movers.now[i * 4] + " after " + movers.before[i * 4]);
        }
        for (int i = 0; i < crowd.length; i++) {
            check(kept[i] == i < Movers.MOST, "mover " + i + (i < Movers.MOST ? " is among the nearest and is kept" : " is past the nearest and is not"));
        }
    }

    private static void nobodyIsNotedForAPictureThatIsNotSteadied() {
        Movers movers = new Movers();
        movers.begin(false);
        movers.note(new Object(), 1f, 2f, 3f, 4f, 1f);
        movers.end();
        check(movers.found == 0, "with the frames not mixed nobody is noted");
    }

    private static boolean row(float[] rows, int at, float x, float y, float up, float angle) {
        return rows[at * 4] == x && rows[at * 4 + 1] == y && rows[at * 4 + 2] == up && rows[at * 4 + 3] == angle;
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}

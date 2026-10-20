package com.cameraunlock.zomboid;

/** Tests of Sidestep: a character walked sideways or backwards facing the view keeps the pace of the game's own walk. */
final class SidestepTests {
    private static final long FRAME = 16_666_667L;

    private SidestepTests() {}

    static void run() {
        beforeAWalkAheadTheSharesMeasuredRaiseTheStance();
        aWalkAheadIsMeasuredOnceUnderWayAndTheStanceKeepsItsPace();
        theStartOfAWalkAndAGapAreNotItsPace();
        walkingAndCreepingAreMeasuredApart();
        theRaiseNeverOverreachesTheStance();
        theRaiseChangesSmoothlyRoundFromBackToSideways();
    }

    private static void beforeAWalkAheadTheSharesMeasuredRaiseTheStance() {
        Sidestep sidestep = new Sidestep();
        float diagonal = (float) -Math.sqrt(0.5);
        float[][] ways = {{-1f, Sidestep.BACK}, {diagonal, Sidestep.BACK_ASIDE}, {0f, Sidestep.ASIDE}};
        for (float[] way : ways) {
            float pace = way[1] * 0.1f;
            check(Math.abs(pace * sidestep.raised(pace, way[0], false) - 0.1f) < 1e-5f
                            && Math.abs(pace * sidestep.raised(pace, way[0], true) - 0.1f * Sidestep.SNEAK) < 1e-5f,
                    "with no walk ahead measured, the stance at " + way[0] + " makes the walk's and the creep's pace from the shares");
        }
        check(sidestep.raised(0f, -1f, false) == 1f, "a stance that moves nothing is left still");
    }

    private static void aWalkAheadIsMeasuredOnceUnderWayAndTheStanceKeepsItsPace() {
        Sidestep sidestep = new Sidestep();
        long now = walk(sidestep, 0.08f, false, 0L, 3_000_000_000L);
        check(Math.abs(sidestep.measured(false) - 0.08f) < 1e-4f, "three seconds of walking ahead measure its pace: " + sidestep.measured(false));
        float stance = 0.08f * Sidestep.BACK * 0.9f;
        check(Math.abs(stance * sidestep.raised(stance, -1f, false) - 0.08f) < 1e-4f,
                "backwards with a weapon whose stance steps a little shorter, the character keeps the measured walk's pace");
        walk(sidestep, 0.05f, false, now + FRAME, 3_000_000_000L);
        check(Math.abs(sidestep.measured(false) - 0.05f) < 1e-3f, "loaded down, the measured pace follows the slower walk: " + sidestep.measured(false));
    }

    private static void theStartOfAWalkAndAGapAreNotItsPace() {
        Sidestep sidestep = new Sidestep();
        long now = walk(sidestep, 0.01f, false, 0L, Sidestep.UNDER_WAY_NANOS - FRAME);
        check(Float.isNaN(sidestep.measured(false)), "the first half second of a walk ahead, its start, measures nothing");
        now = walk(sidestep, 0.01f, false, now + Sidestep.GAP_NANOS + FRAME, Sidestep.UNDER_WAY_NANOS - FRAME);
        check(Float.isNaN(sidestep.measured(false)), "after a gap the walk starts again, and its start measures nothing either");
    }

    private static void walkingAndCreepingAreMeasuredApart() {
        Sidestep sidestep = new Sidestep();
        long now = walk(sidestep, 0.08f, false, 0L, 2_000_000_000L);
        walk(sidestep, 0.07f, true, now + FRAME, 2_000_000_000L);
        check(Math.abs(sidestep.measured(false) - 0.08f) < 1e-4f && Math.abs(sidestep.measured(true) - 0.07f) < 1e-4f,
                "a walk and a creep ahead each measure their own pace");
    }

    private static void theRaiseNeverOverreachesTheStance() {
        Sidestep sidestep = new Sidestep();
        walk(sidestep, 0.08f, false, 0L, 2_000_000_000L);
        float starting = 0.002f;
        check(Math.abs(sidestep.raised(starting, -1f, false) - Sidestep.OVERREACH / Sidestep.BACK) < 1e-4f,
                "the stance starting from a standstill is raised no more than OVERREACH past the shares, so it still eases in");
        float blending = 0.12f;
        check(Math.abs(blending * sidestep.raised(blending, -1f, false) - 0.08f) < 1e-4f,
                "a walk ahead still blending out as the stance begins is held to the measured pace, not carried past it");
        float forwardish = 0.5f;
        check(Math.abs(0.07f * sidestep.raised(0.07f, forwardish, false) - 0.08f) < 1e-4f,
                "a controller pushed partly ahead is held to the measured pace too");
    }

    private static void theRaiseChangesSmoothlyRoundFromBackToSideways() {
        Sidestep sidestep = new Sidestep();
        float was = sidestep.raised(1f, -1f, false);
        for (int step = 1; step <= 90; step++) {
            float now = sidestep.raised(1f, (float) -Math.cos(Math.toRadians(step)), false);
            check(now >= was - 1e-4f && now - was < 0.1f, "the raise grows a little at each degree from straight back: " + step + " gives " + now);
            was = now;
        }
    }

    /** Walks ahead at a pace for a time, a frame at a time from a moment, and gives the moment of the last frame. */
    private static long walk(Sidestep sidestep, float pace, boolean sneaking, long from, long nanos) {
        long now = from;
        for (; now <= from + nanos; now += FRAME) {
            sidestep.walkedAhead(pace, sneaking, now);
        }
        return now - FRAME;
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}

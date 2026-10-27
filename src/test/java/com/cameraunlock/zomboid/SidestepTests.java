package com.cameraunlock.zomboid;

/** Tests of Sidestep: a character walked sideways or backwards facing the view keeps the pace of the game's own walk. */
final class SidestepTests {
    private static final long FRAME = 16_666_667L;

    private SidestepTests() {}

    static void run() {
        Checks.run("beforeAWalkAheadTheSharesMeasuredRaiseTheStance", SidestepTests::beforeAWalkAheadTheSharesMeasuredRaiseTheStance);
        Checks.run("aWalkAheadIsMeasuredOnceUnderWayAndTheStanceKeepsItsPace", SidestepTests::aWalkAheadIsMeasuredOnceUnderWayAndTheStanceKeepsItsPace);
        Checks.run("theStartOfAWalkAndAGapAreNotItsPace", SidestepTests::theStartOfAWalkAndAGapAreNotItsPace);
        Checks.run("walkingAndCreepingAreMeasuredApart", SidestepTests::walkingAndCreepingAreMeasuredApart);
        Checks.run("theRaiseNeverOverreachesTheStance", SidestepTests::theRaiseNeverOverreachesTheStance);
        Checks.run("theRaiseChangesSmoothlyRoundFromBackToSideways", SidestepTests::theRaiseChangesSmoothlyRoundFromBackToSideways);
        Checks.run("aJogOrSprintAsideKeepsThePaceOfTheGaitAhead", SidestepTests::aJogOrSprintAsideKeepsThePaceOfTheGaitAhead);
        Checks.run("aSprintTheGameDropsForAFrameIsStillMeasured", SidestepTests::aSprintTheGameDropsForAFrameIsStillMeasured);
        Checks.run("aSwingingTimeMultiplierDoesNotCarryThePacePastTheGround", SidestepTests::aSwingingTimeMultiplierDoesNotCarryThePacePastTheGround);
    }

    private static void beforeAWalkAheadTheSharesMeasuredRaiseTheStance() {
        Sidestep sidestep = new Sidestep();
        float diagonal = (float) -Math.sqrt(0.5);
        float[][] ways = {{-1f, Sidestep.BACK}, {diagonal, Sidestep.BACK_ASIDE}, {0f, Sidestep.ASIDE}};
        for (float[] way : ways) {
            float pace = way[1] * 0.1f;
            check(Math.abs(pace * raise(sidestep, pace, way[0], Sidestep.WALKING) - 0.1f) < 1e-5f
                            && Math.abs(pace * raise(sidestep, pace, way[0], Sidestep.CREEPING) - 0.1f * Sidestep.SNEAK) < 1e-5f,
                    "with no walk ahead measured, the stance at " + way[0] + " makes the walk's and the creep's pace from the shares");
        }
        check(raise(sidestep, 0f, -1f, Sidestep.WALKING) == 1f, "a stance that moves nothing is left still");
    }

    private static void aWalkAheadIsMeasuredOnceUnderWayAndTheStanceKeepsItsPace() {
        Sidestep sidestep = new Sidestep();
        long now = walk(sidestep, 0.08f, Sidestep.WALKING, 0L, 3_000_000_000L);
        check(Math.abs(sidestep.measured(Sidestep.WALKING) - 0.08f) < 1e-4f, "three seconds of walking ahead measure its pace: " + sidestep.measured(Sidestep.WALKING));
        float stance = 0.08f * Sidestep.BACK * 0.9f;
        check(Math.abs(stance * raise(sidestep, stance, -1f, Sidestep.WALKING) - 0.08f) < 1e-4f,
                "backwards with a weapon whose stance steps a little shorter, the character keeps the measured walk's pace");
        walk(sidestep, 0.05f, Sidestep.WALKING, now + FRAME, 3_000_000_000L);
        check(Math.abs(sidestep.measured(Sidestep.WALKING) - 0.05f) < 1e-3f, "loaded down, the measured pace follows the slower walk: " + sidestep.measured(Sidestep.WALKING));
    }

    private static void theStartOfAWalkAndAGapAreNotItsPace() {
        Sidestep sidestep = new Sidestep();
        long now = walk(sidestep, 0.01f, Sidestep.WALKING, 0L, Sidestep.UNDER_WAY_NANOS - FRAME);
        check(Float.isNaN(sidestep.measured(Sidestep.WALKING)), "the first half second of a walk ahead, its start, measures nothing");
        now = walk(sidestep, 0.01f, Sidestep.WALKING, now + Sidestep.GAP_NANOS + FRAME, Sidestep.UNDER_WAY_NANOS - FRAME);
        check(Float.isNaN(sidestep.measured(Sidestep.WALKING)), "after a gap the walk starts again, and its start measures nothing either");
    }

    private static void walkingAndCreepingAreMeasuredApart() {
        Sidestep sidestep = new Sidestep();
        long now = walk(sidestep, 0.08f, Sidestep.WALKING, 0L, 2_000_000_000L);
        walk(sidestep, 0.07f, Sidestep.CREEPING, now + FRAME, 2_000_000_000L);
        check(Math.abs(sidestep.measured(Sidestep.WALKING) - 0.08f) < 1e-4f && Math.abs(sidestep.measured(Sidestep.CREEPING) - 0.07f) < 1e-4f,
                "a walk and a creep ahead each measure their own pace");
    }

    private static void theRaiseNeverOverreachesTheStance() {
        Sidestep sidestep = new Sidestep();
        walk(sidestep, 0.08f, Sidestep.WALKING, 0L, 2_000_000_000L);
        float starting = 0.002f;
        check(Math.abs(raise(sidestep, starting, -1f, Sidestep.WALKING) - Sidestep.OVERREACH / Sidestep.BACK) < 1e-4f,
                "the stance starting from a standstill is raised no more than OVERREACH past the shares, so it still eases in");
        float blending = 0.12f;
        check(Math.abs(blending * raise(sidestep, blending, -1f, Sidestep.WALKING) - 0.08f) < 1e-4f,
                "a walk ahead still blending out as the stance begins is held to the measured pace, not carried past it");
        float forwardish = 0.5f;
        check(Math.abs(0.07f * raise(sidestep, 0.07f, forwardish, Sidestep.WALKING) - 0.08f) < 1e-4f,
                "a controller pushed partly ahead is held to the measured pace too");
    }

    private static void theRaiseChangesSmoothlyRoundFromBackToSideways() {
        Sidestep sidestep = new Sidestep();
        float was = raise(sidestep, 1f, -1f, Sidestep.WALKING);
        for (int step = 1; step <= 90; step++) {
            float now = raise(sidestep, 1f, (float) -Math.cos(Math.toRadians(step)), Sidestep.WALKING);
            check(now >= was - 1e-4f && now - was < 0.1f, "the raise grows a little at each degree from straight back: " + step + " gives " + now);
            was = now;
        }
    }

    private static void aJogOrSprintAsideKeepsThePaceOfTheGaitAhead() {
        Sidestep sidestep = new Sidestep();
        float stance = 0.08f * Sidestep.ASIDE;
        check(Math.abs(stance * raise(sidestep, stance, 0f, Sidestep.SPRINTING) - 0.08f * Sidestep.SPRINT_OF_WALK) < 1e-4f
                        && Math.abs(stance * raise(sidestep, stance, -1f, Sidestep.JOGGING) - 0.08f * Sidestep.ASIDE / Sidestep.BACK * Sidestep.JOG_OF_WALK) < 1e-4f,
                "with nothing measured a sprint or jog aside goes faster than the walk the shares make, by the game's share for the gait");
        long now = walk(sidestep, 0.08f, Sidestep.WALKING, 0L, 2_000_000_000L);
        now = walk(sidestep, 0.16f, Sidestep.JOGGING, now + FRAME, 2_000_000_000L);
        check(Math.abs(stance * raise(sidestep, stance, 0f, Sidestep.SPRINTING) - 0.08f * Sidestep.SPRINT_OF_WALK) < 1e-4f,
                "a sprint not yet measured goes at the measured walk times the game's share for a sprint");
        walk(sidestep, 0.24f, Sidestep.SPRINTING, now + FRAME, 2_000_000_000L);
        check(Math.abs(stance * raise(sidestep, stance, 0f, Sidestep.JOGGING) - 0.16f) < 1e-4f
                        && Math.abs(stance * raise(sidestep, stance, 0f, Sidestep.SPRINTING) - 0.24f) < 1e-4f
                        && Math.abs(stance * raise(sidestep, stance, -1f, Sidestep.SPRINTING) - 0.24f) < 1e-4f,
                "jogged or sprinted aside or back, the character keeps the measured pace of that gait ahead");
        float starting = 0.002f;
        check(Math.abs(raise(sidestep, starting, 0f, Sidestep.SPRINTING) - Sidestep.OVERREACH / Sidestep.ASIDE * 3f) < 1e-3f,
                "a sprint aside from a standstill eases in by the same share as the walk does");
        check(Math.abs(sidestep.measured(Sidestep.WALKING) - 0.08f) < 1e-4f, "jogging and sprinting leave the walk's pace as it was");
    }

    private static void aSprintTheGameDropsForAFrameIsStillMeasured() {
        Sidestep sidestep = new Sidestep();
        long now = 0L;
        for (int frame = 0; frame < 180; frame++, now += FRAME) {
            sidestep.walkedAhead(frame % 10 == 9 ? 0.16f : 0.24f, 1f, frame % 10 == 9 ? Sidestep.JOGGING : Sidestep.SPRINTING, now);
        }
        check(Math.abs(sidestep.measured(Sidestep.SPRINTING) - 0.24f) < 1e-3f,
                "a sprint ahead the game reads as a jog one frame in ten is still measured: " + sidestep.measured(Sidestep.SPRINTING));
    }

    private static void aSwingingTimeMultiplierDoesNotCarryThePacePastTheGround() {
        Sidestep sidestep = new Sidestep();
        long now = 0L;
        float ground = 0f, time = 0f;
        for (int frame = 0; frame < 300; frame++, now += FRAME) {
            // The same ground each frame while the multiplier swings, as the game's sprint does.
            float multiplier = frame % 4 == 0 ? 0.1f : 0.5f;
            sidestep.walkedAhead(0.05f, multiplier, Sidestep.SPRINTING, now);
        }
        check(Math.abs(sidestep.measured(Sidestep.SPRINTING) - 0.05f / 0.4f) < 0.01f,
                "the sprint ahead is its ground over the game's time, 0.125, not the mean of each frame's: " + sidestep.measured(Sidestep.SPRINTING));
        float wanted = sidestep.measured(Sidestep.SPRINTING);
        long from = now + Sidestep.GAP_NANOS * 2;
        for (int frame = 0; frame < 120; frame++) {
            float multiplier = frame % 4 == 0 ? 0.1f : 0.5f;
            float moved = 0.008f;
            float out = moved * sidestep.raised(moved, multiplier, -1f, Sidestep.SPRINTING, from + frame * FRAME);
            if (frame >= 60) {
                ground += out;
                time += multiplier;
            }
        }
        check(Math.abs(ground / time - wanted) < 1e-3f, "sprinted back, the stance covers the sprint ahead's ground in the same game time: " + ground / time);
    }

    private static long raisedAt;

    /** One frame of the stance on its own, at a time multiplier of 1. */
    private static float raise(Sidestep sidestep, float pace, float ahead, int gait) {
        raisedAt += 1_000_000_000L;
        return sidestep.raised(pace, 1f, ahead, gait, raisedAt);
    }

    /** Walks ahead at a pace for a time, a frame at a time from a moment, and gives the moment of the last frame. */
    private static long walk(Sidestep sidestep, float pace, int gait, long from, long nanos) {
        long now = from;
        for (; now <= from + nanos; now += FRAME) {
            sidestep.walkedAhead(pace, 1f, gait, now);
        }
        return now - FRAME;
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}

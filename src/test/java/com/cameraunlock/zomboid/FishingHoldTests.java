package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** A fishing rod out over water in first person: held, cast and reeled as FishingHold has it. */
final class FishingHoldTests {
    private FishingHoldTests() {}

    static void run() {
        Checks.run("aCastDrawsTheRodBackAndWhipsItOutWithoutAJump", FishingHoldTests::aCastDrawsTheRodBackAndWhipsItOutWithoutAJump);
        Checks.run("theReelTurnsOnlyWhileTheLineIsReeledIn", FishingHoldTests::theReelTurnsOnlyWhileTheLineIsReeledIn);
        Checks.run("theRodIsHeldTipUpInEveryStage", FishingHoldTests::theRodIsHeldTipUpInEveryStage);
    }

    private static void aCastDrawsTheRodBackAndWhipsItOutWithoutAJump() {
        float[] at = new float[ViewModelMath.HOLD], before = new float[ViewModelMath.HOLD];
        FishingHold.pose(FishingHold.READY, 0f, before);
        float[] ready = before.clone(), waiting = new float[ViewModelMath.HOLD];
        FishingHold.pose(FishingHold.WAITING, 0f, waiting);
        float most = -Float.MAX_VALUE, least = Float.MAX_VALUE, mostAt = 0f, leastAt = 0f, steepest = 0f;
        float step = 1f / 240f;
        for (float t = 0f; t <= 2f; t += step) {
            FishingHold.pose(FishingHold.CAST, t, at);
            if (t == 0f) {
                for (int i = 0; i < ViewModelMath.HOLD; i++) {
                    check(Math.abs(at[i] - ready[i]) < 1e-6f, "a cast starts from where the rod is held ready: " + i);
                }
            }
            if (at[0] > most) {
                most = at[0];
                mostAt = t;
            }
            if (t > mostAt && at[0] < least) {
                least = at[0];
                leastAt = t;
            }
            steepest = Math.max(steepest, Math.abs(at[0] - before[0]) / step);
            System.arraycopy(at, 0, before, 0, ViewModelMath.HOLD);
        }
        check(Math.toDegrees(most) > 80f && Math.abs(mostAt - FishingHold.DRAWS_BACK) < 0.01f, "the rod is drawn back over the shoulder: " + Math.toDegrees(most) + " at " + mostAt);
        check(Math.toDegrees(least) < 10f && Math.abs(leastAt - FishingHold.DRAWS_BACK - FishingHold.WHIPS_OUT) < 0.01f, "and whipped out low over the water: " + Math.toDegrees(least) + " at " + leastAt);
        check(Math.toDegrees(steepest) < 1500f, "turning smoothly, at most " + Math.toDegrees(steepest) + " degrees a second");
        for (int i = 0; i < ViewModelMath.HOLD; i++) {
            check(Math.abs(at[i] - waiting[i]) < 1e-6f, "and ends where it waits for a bite: " + i);
        }
    }

    private static void theReelTurnsOnlyWhileTheLineIsReeledIn() {
        float crank = 1f;
        for (int stage = FishingHold.NOT; stage <= FishingHold.LANDING; stage++) {
            if (stage != FishingHold.REELING) {
                check(FishingHold.crank(crank, stage, 0.25f) == crank, "the reel stands still in stage " + stage);
            }
        }
        float turned = 0f, at = 0f;
        for (int frame = 0; frame < 60; frame++) {
            float next = FishingHold.crank(at, FishingHold.REELING, 1f / 60f);
            turned += (float) Math.IEEEremainder(next - at, 2 * Math.PI);
            at = next;
        }
        check(Math.abs(turned / (2 * Math.PI) - FishingHold.TURNS_A_SECOND) < 1e-3f, "reeling, it goes round " + FishingHold.TURNS_A_SECOND + " times a second: " + turned / (2 * Math.PI));
    }

    private static void theRodIsHeldTipUpInEveryStage() {
        float[] at = new float[ViewModelMath.HOLD];
        for (int stage = FishingHold.READY; stage <= FishingHold.LANDING; stage++) {
            for (float t = 0f; t < 3f; t += 0.05f) {
                FishingHold.pose(stage, t, at);
                check(at[0] >= 0f && at[0] < Math.toRadians(105), "stage " + stage + " at " + t + " has the rod's tip up: " + Math.toDegrees(at[0]));
                check(at[3] > 0f && at[4] < 0f, "and the rod in the right hand, low on the right: " + at[3] + ", " + at[4]);
            }
        }
    }
}

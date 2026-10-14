package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** When a vehicle driven over the hills leaves the ground, and how it comes back down. */
final class AirtimeTests {
    private AirtimeTests() {}

    static void run() {
        Checks.run("onLevelGroundAVehicleNeverLeavesIt", AirtimeTests::onLevelGroundAVehicleNeverLeavesIt);
        Checks.run("overACrestSlowlyItKeepsToTheLand", AirtimeTests::overACrestSlowlyItKeepsToTheLand);
        Checks.run("overACrestFastItFliesAndFallsAtGravity", AirtimeTests::overACrestFastItFliesAndFallsAtGravity);
        Checks.run("aJumpPutsItOnTheGround", AirtimeTests::aJumpPutsItOnTheGround);
    }

    private static final float STEP = 1f / 60f;

    /** A round hill crest at x 0, heightTop high, falling away as a parabola of the given curvature. */
    private static float crest(float x, float curvature) {
        return -0.5f * curvature * x * x;
    }

    private static void onLevelGroundAVehicleNeverLeavesIt() {
        Airtime air = new Airtime();
        air.step(1f, STEP, true);
        for (int i = 0; i < 600; i++) {
            air.step(1f, STEP, false);
            check(!air.airborne && air.height == 1f, "on level ground the vehicle stays on it");
        }
    }

    /** Drives over a crest of the given curvature at speed, metres a second, and says the most time it spent off the ground at once. */
    private static float overCrest(float speed, float curvature, Airtime air) {
        float x = -40f, most = 0f;
        air.step(crest(x, curvature), STEP, true);
        while (x < 40f) {
            x += speed * STEP;
            air.step(crest(x, curvature), STEP, false);
            check(air.height >= crest(x, curvature) - 1e-4f, "a vehicle is never below the land: " + air.height + " at " + x);
            most = Math.max(most, air.aloft);
        }
        return most;
    }

    private static void overACrestSlowlyItKeepsToTheLand() {
        // Over a crest of curvature k a vehicle stays on the land below sqrt(g / k): 22 m/s at 0.02.
        float aloft = overCrest(15f, 0.02f, new Airtime());
        check(aloft == 0f, "below the speed the crest throws it at, the vehicle keeps to the land: " + aloft);
    }

    private static void overACrestFastItFliesAndFallsAtGravity() {
        Airtime air = new Airtime();
        float aloft = overCrest(40f, 0.02f, air);
        check(aloft > 0.5f, "well over that speed it leaves the ground for a while: " + aloft);
        // Thrown level off the top it falls as anything does: below the take off by g t^2 / 2.
        Airtime level = new Airtime();
        level.step(0f, STEP, true);
        level.step(0f, STEP, false);
        level.step(-10f, STEP, false);
        check(level.airborne, "where the land drops away at once it is in the air");
        // It left the level ground at 0 one step ago.
        float t = STEP;
        for (int i = 0; i < 30; i++) {
            level.step(-10f, STEP, false);
            t += STEP;
        }
        float fallen = -level.height, expected = 0.5f * Airtime.GRAVITY * t * t;
        check(Math.abs(fallen - expected) < 0.05f * expected + 0.02f, "and it falls at gravity: " + fallen + " against " + expected);
        while (level.airborne) {
            level.step(-10f, STEP, false);
        }
        check(level.height == -10f && level.rising == 0f, "it lands on the land and stops falling");
    }

    private static void aJumpPutsItOnTheGround() {
        Airtime air = new Airtime();
        air.step(0f, STEP, true);
        air.step(0f, STEP, false);
        air.step(-10f, STEP, false);
        check(air.airborne, "in the air");
        air.step(3f, 2f, true);
        check(!air.airborne && air.height == 3f && air.rising == 0f, "a jump puts it on the land at once, with no fall");
    }
}

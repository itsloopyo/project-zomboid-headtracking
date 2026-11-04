package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** How much of the roll a turn gives a vehicle is taken off it, and when none is. */
final class TurnRollTests {
    private TurnRollTests() {}

    static void run() {
        Checks.run("aTurnIsRolledAgainstByAllButWhatIsKept", TurnRollTests::aTurnIsRolledAgainstByAllButWhatIsKept);
        Checks.run("goingStraightOrTippedFarNothingIsGiven", TurnRollTests::goingStraightOrTippedFarNothingIsGiven);
        Checks.run("aKnockIsNotCountedAndTheCatchAfterItIs", TurnRollTests::aKnockIsNotCountedAndTheCatchAfterItIs);
        Checks.run("afterAGapItStartsAgain", TurnRollTests::afterAGapItStartsAgain);
        Checks.run("aCrashIntoAWallIsNotCounted", TurnRollTests::aCrashIntoAWallIsNotCounted);
    }

    private static void aCrashIntoAWallIsNotCounted() {
        // Sliding sideways at 6 m/s into a wall, stopped in a tenth of a second: 60 m/s/s against its slide.
        TurnRoll roll = new TurnRoll();
        float vy = -6f;
        roll.step(1f, 0f, 1f, 0f, vy, STEP, INFLUENCE, HIGH);
        for (int i = 0; i < 6; i++) {
            vy = Math.min(0f, vy + 60f * STEP);
            float given = roll.step(1f, 0f, 1f, 0f, vy, STEP, INFLUENCE, HIGH);
            check(given == 0f, "a car struck harder than its wheels grip is left to the game: " + given);
        }
    }

    private static final float STEP = 1f / 60f;
    /** A Base.CarNormal: rollInfluence 1, its wheels meeting the ground 0.33 under its place. */
    private static final float INFLUENCE = 1f, HIGH = 0.333f;

    /** Drives round a bend at 20 m/s, thrown towards (forwardY, -forwardX) at thrown m/s/s, for a second, and gives the last roll. */
    private static float turned(float thrown, float upUp, TurnRoll roll) {
        float heading = 0f, given = 0f;
        roll.step(1f, 0f, upUp, 20f, 0f, STEP, INFLUENCE, HIGH);
        for (int i = 0; i < 60; i++) {
            heading -= thrown / 20f * STEP;
            float fx = (float) Math.cos(heading), fy = (float) Math.sin(heading);
            given = roll.step(fx, fy, upUp, 20f * fx, 20f * fy, STEP, INFLUENCE, HIGH);
        }
        return given;
    }

    private static void aTurnIsRolledAgainstByAllButWhatIsKept() {
        float given = turned(14f, 1f, new TurnRoll());
        float wanted = -(1f - TurnRoll.KEPT) * INFLUENCE * HIGH * 14f;
        check(Math.abs(given - wanted) < 0.05f, "a turn at 14 m/s/s is rolled against by all but KEPT of what the wheels' grip gives: " + given + " for " + wanted);
        check(Math.abs(turned(-14f, 1f, new TurnRoll()) + given) < 1e-3f, "the other way round, the same the other way");
        check(Math.signum(given) == -Math.signum(throwRolls(14f)), "against the way a throw that tips it is rolled");
        check(new TurnRoll().step(1f, 0f, 1f, 20f, 0f, STEP, INFLUENCE, HIGH) == 0f, "nothing on the first step, with nothing to measure the throw from");
    }

    /** What Rollover gives the same turn, thrown well past what tips it. */
    private static float throwRolls(float thrown) {
        Rollover rollover = new Rollover();
        float heading = 0f, given = 0f;
        rollover.step(1f, 0f, 1f, 20f, 0f, STEP, 1f);
        for (int i = 0; i < 60; i++) {
            heading -= thrown / 20f * STEP;
            float fx = (float) Math.cos(heading), fy = (float) Math.sin(heading);
            given = rollover.step(fx, fy, 1f, 20f * fx, 20f * fy, STEP, 1f);
        }
        return given;
    }

    private static void goingStraightOrTippedFarNothingIsGiven() {
        check(turned(0f, 1f, new TurnRoll()) == 0f, "going straight nothing is given");
        check(turned(14f, TurnRoll.UPRIGHT - 0.01f, new TurnRoll()) == 0f, "on its side or going over by its weight nothing is given");
    }

    private static void aKnockIsNotCountedAndTheCatchAfterItIs() {
        // Facing +x, its side (0, -1): knocked that way at 15 m/s/s, short of GRIP_MOST, then its wheels catch and stop the slide.
        TurnRoll roll = new TurnRoll();
        float vy = 0f, caught = 0f, last = Float.MAX_VALUE;
        roll.step(1f, 0f, 1f, 20f, vy, STEP, INFLUENCE, HIGH);
        for (int i = 0; i < 12; i++) {
            vy -= 15f * STEP;
            float knocked = Math.abs(roll.step(1f, 0f, 1f, 20f, vy, STEP, INFLUENCE, HIGH));
            // Until it slides faster than ThrownAcross.SLIDING a push cannot be told from its wheels' grip.
            if (-vy > ThrownAcross.SLIDING) {
                check(knocked < last || knocked == 0f, "pushed aside by something else while it slides, nothing more is given: " + knocked + " after " + last);
            }
            last = knocked;
        }
        while (vy < -0.1f) {
            vy += 12f * STEP;
            caught = Math.max(caught, roll.step(1f, 0f, 1f, 20f, vy, STEP, INFLUENCE, HIGH));
        }
        check(caught > 0f, "as its wheels catch the slide it is rolled against the trip over them: " + caught);
    }

    private static void afterAGapItStartsAgain() {
        TurnRoll roll = new TurnRoll();
        roll.step(1f, 0f, 1f, 20f, 0f, STEP, INFLUENCE, HIGH);
        roll.restart();
        check(roll.step(1f, 0f, 1f, 0f, -20f, STEP, INFLUENCE, HIGH) == 0f && roll.across() == 0f,
                "a speed met after a gap is no throw across");
    }
}

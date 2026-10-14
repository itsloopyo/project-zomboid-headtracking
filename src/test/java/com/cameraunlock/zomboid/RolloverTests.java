package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** When a vehicle thrown across the way it faces is rolled over, and which way. */
final class RolloverTests {
    private RolloverTests() {}

    static void run() {
        Checks.run("aSaloonTipsJustUnderWhatItsWheelsHold", RolloverTests::aSaloonTipsJustUnderWhatItsWheelsHold);
        Checks.run("goingStraightOrTurningGentlyRollsNothing", RolloverTests::goingStraightOrTurningGentlyRollsNothing);
        Checks.run("thrownHardItRollsAwayFromTheThrowAndMoreForHarder", RolloverTests::thrownHardItRollsAwayFromTheThrowAndMoreForHarder);
        Checks.run("tippedFarItIsLeftToItsWeight", RolloverTests::tippedFarItIsLeftToItsWeight);
        Checks.run("aKnockIsNotCountedAndTheTripAfterItIs", RolloverTests::aKnockIsNotCountedAndTheTripAfterItIs);
    }

    private static final float STEP = 1f / 60f;
    /** A Base.CarNormal: wheels 0.66 out, their bottoms 0.82 below its middle. */
    private static final float SALOON = Rollover.tipsAt(0.66f, 0.55f + 0.273f);

    /**
     * Drives round a bend at 20 m/s, thrown towards its side (forwardY, -forwardX) at thrown m/s/s
     * by its wheels, facing the way it goes, for a second, and gives the last roll.
     */
    private static float thrownSideways(float thrown, float upUp, Rollover rollover) {
        float heading = 0f, roll = 0f;
        rollover.step(1f, 0f, upUp, 20f, 0f, STEP, SALOON);
        for (int i = 0; i < 60; i++) {
            // Turning that way the heading goes clockwise, the velocity with it.
            heading -= thrown / 20f * STEP;
            float fx = (float) Math.cos(heading), fy = (float) Math.sin(heading);
            roll = rollover.step(fx, fy, upUp, 20f * fx, 20f * fy, STEP, SALOON);
        }
        return roll;
    }

    private static void aSaloonTipsJustUnderWhatItsWheelsHold() {
        check(SALOON > 11f && SALOON < 14f, "a saloon tips just under the 14 m/s/s the game's physics held one to in a turn: " + SALOON);
        check(Rollover.tipsAt(0.66f, 1.2f) < SALOON && Rollover.tipsAt(0.66f, 0.6f) > SALOON, "a taller vehicle tips sooner, a lower one later");
    }

    private static void goingStraightOrTurningGentlyRollsNothing() {
        check(thrownSideways(0f, 1f, new Rollover()) == 0f, "going straight nothing rolls it");
        check(thrownSideways(SALOON - 1f, 1f, new Rollover()) == 0f, "thrown across short of what tips it nothing rolls it");
        check(new Rollover().step(1f, 0f, 1f, 20f, 0f, STEP, SALOON) == 0f, "nothing on the first step, with nothing to measure the throw from");
    }

    private static void thrownHardItRollsAwayFromTheThrowAndMoreForHarder() {
        float some = thrownSideways(SALOON + 2f, 1f, new Rollover()), more = thrownSideways(SALOON + 6f, 1f, new Rollover());
        check(some > 0f && more > some, "thrown towards its side it is rolled with a positive torque, harder for harder: " + some + ", " + more);
        check(Math.abs(some - 2f * Rollover.ROLL_PER) < 0.05f, "by ROLL_PER for each m/s/s beyond what tips it: " + some);
        float other = thrownSideways(-(SALOON + 2f), 1f, new Rollover());
        check(Math.abs(other + some) < 1e-3f, "thrown the other way, the same the other way: " + other);
        float flung = thrownSideways(500f, 1f, new Rollover());
        check(Math.abs(flung - (Rollover.MOST - SALOON) * Rollover.ROLL_PER) < 0.05f, "a fling is counted only up to MOST: " + flung);
    }

    private static void tippedFarItIsLeftToItsWeight() {
        check(thrownSideways(SALOON + 6f, Rollover.TIPPED - 0.01f, new Rollover()) == 0f, "tipped past TIPPED nothing more is given");
    }

    private static void aKnockIsNotCountedAndTheTripAfterItIs() {
        // Facing +x, its side (0, -1): knocked that way at 30 m/s/s for a fifth of a second, then its wheels catch and stop the slide.
        Rollover rollover = new Rollover();
        float vy = 0f, knocked = 0f, tripped = 0f;
        rollover.step(1f, 0f, 1f, 20f, vy, STEP, SALOON);
        for (int i = 0; i < 12; i++) {
            vy -= 30f * STEP;
            knocked = rollover.step(1f, 0f, 1f, 20f, vy, STEP, SALOON);
            check(knocked == 0f, "pushed aside by something else, its wheels throw it by nothing: " + knocked);
        }
        while (vy < -0.1f) {
            vy += 30f * STEP;
            tripped = Math.min(tripped, rollover.step(1f, 0f, 1f, 20f, vy, STEP, SALOON));
        }
        check(tripped < 0f, "as its wheels catch the slide it is rolled on over them, top towards where it slid: " + tripped);
    }
}

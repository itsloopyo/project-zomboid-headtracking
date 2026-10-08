package com.cameraunlock.zomboid;

final class StairTests {
    private StairTests() {}

    static void run() {
        Checks.run("aFlightIsTwelveStepsThatEndLevelWithTheFloorAbove", StairTests::aFlightIsTwelveStepsThatEndLevelWithTheFloorAbove);
        Checks.run("theUndersideStaysUnderEveryTreadAndMeetsTheFloor", StairTests::theUndersideStaysUnderEveryTreadAndMeetsTheFloor);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-5f;
    }

    private static void aFlightIsTwelveStepsThatEndLevelWithTheFloorAbove() {
        float last = 0f;
        int steps = 0;
        for (int third = 0; third < 3; third++) {
            for (int step = 0; step < Stairs.STEPS; step++) {
                float tread = Stairs.tread(third, step);
                check(near(tread - last, Stairs.RISE), "every riser is the same height, third " + third + " step " + step);
                last = tread;
                steps++;
            }
        }
        check(steps == 12 && near(last, 1f), "twelve steps climb one level, got " + steps + " to " + last);
        check(near(Stairs.tread(0, Stairs.STEPS - 1), 1f / 3f) && near(Stairs.tread(1, Stairs.STEPS - 1), 2f / 3f),
                "each square of a flight ends a third of a level above the last");
    }

    private static void theUndersideStaysUnderEveryTreadAndMeetsTheFloor() {
        check(Stairs.underside(0f) == 0f && Stairs.underside(Stairs.GOING) == 0f, "the underside is on the floor as far as the second riser");
        for (int third = 0; third < 3; third++) {
            for (int step = 0; step < Stairs.STEPS; step++) {
                // Under the back of a tread the underside is nearest it: a string's depth below the foot of the next riser.
                float back = third + (step + 1) * Stairs.GOING, under = Stairs.underside(back);
                check(near(under, Stairs.tread(third, step) - Stairs.STRING), "the underside at " + back + " is a string under the tread, at " + under);
            }
        }
        check(near(Stairs.underside(3f), 1f - Stairs.STRING), "at the top it is a string's depth under the floor above");
    }
}

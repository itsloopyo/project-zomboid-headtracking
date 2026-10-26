package com.cameraunlock.zomboid;

final class StairTests {
    private StairTests() {}

    static void run() {
        Checks.run("aFlightIsTwelveStepsThatEndLevelWithTheFloorAbove", StairTests::aFlightIsTwelveStepsThatEndLevelWithTheFloorAbove);
        Checks.run("theUndersideStaysUnderEveryTreadAndMeetsTheFloor", StairTests::theUndersideStaysUnderEveryTreadAndMeetsTheFloor);
        Checks.run("whatIsLaidOverAFlightIsOnItsTreadsAndNeverUnderOne", StairTests::whatIsLaidOverAFlightIsOnItsTreadsAndNeverUnderOne);
        Checks.run("onlyBareTimberFlightsAreLaidInBoards", StairTests::onlyBareTimberFlightsAreLaidInBoards);
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

    private static void whatIsLaidOverAFlightIsOnItsTreadsAndNeverUnderOne() {
        for (int third = 0; third < 3; third++) {
            for (int step = 0; step < Stairs.STEPS; step++) {
                float from = third + step * Stairs.GOING;
                for (float into : new float[] {0f, 0.5f, 0.999f}) {
                    float along = from + into * Stairs.GOING, over = Stairs.over(along);
                    check(near(over, Stairs.tread(third, step)), "over the tread of third " + third + " step " + step + " at " + along + " is that tread, got " + over);
                }
            }
        }
        check(near(Stairs.over(-0.1f), Stairs.RISE) && near(Stairs.over(3f), 1f), "off either end it is the first or the last tread");
        for (float along = 0f; along <= 3f; along += 0.01f) {
            check(Stairs.over(along) >= Math.min(along, 3f) / 3f - 1e-5f && Stairs.over(along) <= 1f + 1e-5f,
                    "at " + along + " it is no lower than the ramp the game walks a character up, nor above the floor at the top");
        }
    }

    private static void onlyBareTimberFlightsAreLaidInBoards() {
        for (String timber : new String[] {"fixtures_stairs_01_16", "fixtures_stairs_01_21", "fixtures_stairs_01_29", "fixtures_stairs_01_34", "fixtures_stairs_01_42", "fixtures_stairs_01_66", "fixtures_stairs_01_74"}) {
            check(Stairs.timber(timber), timber + " is a timber flight");
        }
        for (String other : new String[] {"fixtures_stairs_01_0", "fixtures_stairs_01_3", "fixtures_stairs_01_22", "fixtures_stairs_01_48", "fixtures_stairs_01_58",
                "fixtures_stairs_01_80", "fixtures_stairs_02_16", "fixtures_stairs_01_", "fixtures_stairs_01_x", "walls_garage_01_16", null}) {
            check(!Stairs.timber(other), other + " is no timber flight");
        }
        // A tread is laid as one board, so a square holds as many boards as steps: a count the
        // picture lays as boards, west flights' boards running along y and north flights' along x.
        for (boolean west : new boolean[] {true, false}) {
            float made = WorldScene.boardsOf(Stairs.STEPS, west);
            check(made >= WorldScene.FIRST_BOARDS && made <= WorldScene.LAST_BOARDS, "a flight's treads are boards, made " + made);
            check(made == WorldScene.FIRST_BOARDS + 2 * (Stairs.STEPS - Flats.FEWEST_BOARDS) + (west ? 1 : 0), "a flight climbing " + (west ? "west" : "north") + " runs its boards across the climb");
        }
    }
}

package com.cameraunlock.zomboid;

import java.util.Map;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;

final class StairTests {
    private StairTests() {}

    static void run() {
        Checks.run("aFlightIsTwelveStepsThatEndLevelWithTheFloorAbove", StairTests::aFlightIsTwelveStepsThatEndLevelWithTheFloorAbove);
        Checks.run("theUndersideStaysUnderEveryTreadAndMeetsTheFloor", StairTests::theUndersideStaysUnderEveryTreadAndMeetsTheFloor);
        Checks.run("whatIsLaidOverAFlightIsOnItsTreadsAndNeverUnderOne", StairTests::whatIsLaidOverAFlightIsOnItsTreadsAndNeverUnderOne);
        Checks.run("onlyBareTimberFlightsAreLaidInBoards", StairTests::onlyBareTimberFlightsAreLaidInBoards);
        Checks.run("aBodyOnTheFloorBesideAFlightLiesFlatOnTheFloor", StairTests::aBodyOnTheFloorBesideAFlightLiesFlatOnTheFloor);
        Checks.run("aBodyOnAFlightLiesAlongItsTreadsAndPastItsSides", StairTests::aBodyOnAFlightLiesAlongItsTreadsAndPastItsSides);
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

    private static IsoSprite stair(IsoObjectType type) {
        return new IsoSprite("fixtures_stairs_01_0", new PropertyContainer(Map.of()), type, null);
    }

    /**
     * A floored room, 0..9 by 0..9 on level 0, with a flight climbing west along row 1 (its foot
     * on the east side of (6, 1), its top at the west side of (4, 1)) and one climbing north up
     * column 8 (foot on the south side of (8, 6), top at the north side of (8, 4)).
     */
    private static IsoCell room() {
        IsoCell cell = new IsoCell();
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                cell.square(x, y, 0);
            }
        }
        cell.getGridSquare(6, 1, 0).add(stair(IsoObjectType.stairsBW));
        cell.getGridSquare(5, 1, 0).add(stair(IsoObjectType.stairsMW));
        cell.getGridSquare(4, 1, 0).add(stair(IsoObjectType.stairsTW));
        cell.getGridSquare(8, 6, 0).add(stair(IsoObjectType.stairsBN));
        cell.getGridSquare(8, 5, 0).add(stair(IsoObjectType.stairsMN));
        cell.getGridSquare(8, 4, 0).add(stair(IsoObjectType.stairsTN));
        return cell;
    }

    /**
     * A body's picture is laid as a grid over a square or two round where it lies, much wider
     * than the body. Lying on the floor beside a flight, the points of it over the flight's
     * squares took the height of the treads there, up to a level, and the picture between
     * them and the floor stood up as a wall of stretched texels to the flight's underside.
     */
    private static void aBodyOnTheFloorBesideAFlightLiesFlatOnTheFloor() {
        IsoCell cell = room();
        for (int[] at : new int[][] {{5, 2}, {7, 1}, {3, 1}, {7, 5}, {9, 5}, {8, 7}}) {
            IsoGridSquare lying = cell.getGridSquare(at[0], at[1], 0);
            for (float x = at[0] - 1.5f; x <= at[0] + 2.5f; x += 0.125f) {
                for (float y = at[1] - 1.5f; y <= at[1] + 2.5f; y += 0.125f) {
                    float up = WorldScene.restUp(lying, x, y);
                    check(up == 0f, "a body on the floor at (" + at[0] + ", " + at[1] + ") lies on the floor at (" + x + ", " + y + "), got " + up + " of a level");
                }
            }
        }
    }

    private static void aBodyOnAFlightLiesAlongItsTreadsAndPastItsSides() {
        IsoCell cell = room();
        IsoGridSquare lying = cell.getGridSquare(5, 1, 0);
        for (float x = 3.5f; x <= 7.5f; x += 0.125f) {
            float along = 7f - x, expected = along <= 0f ? 0f : Stairs.over(along);
            for (float y : new float[] {0.2f, 1.5f, 2.8f}) {
                float up = WorldScene.restUp(lying, x, y);
                check(near(up, expected), "a body on the middle of the west flight lies on its tread " + expected + " at (" + x + ", " + y + "), its sides included, got " + up);
            }
        }
        check(WorldScene.restUp(lying, 7.5f, 1.5f) == 0f && near(WorldScene.restUp(lying, 3.5f, 1.5f), 1f), "past its foot on the floor, past its top on the floor above");
    }
}

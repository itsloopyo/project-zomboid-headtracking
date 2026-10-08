package com.cameraunlock.zomboid;

/** Tests of the marks the viewer leaves in lying snow. */
final class SnowPrintTests {
    private static final float[] prints = new float[SnowPrints.PRINTS * 4], ruts = new float[SnowPrints.RUTS * 4], rutsUp = new float[SnowPrints.RUTS];
    private static final float[] rest = new float[6];

    private SnowPrintTests() {}

    static void run() {
        Checks.run("aWalkLeavesAFootprintAStrideLeftAndRightByTurns", SnowPrintTests::aWalkLeavesAFootprintAStrideLeftAndRightByTurns);
        Checks.run("standingStillLeavesNothing", SnowPrintTests::standingStillLeavesNothing);
        Checks.run("aJumpAcrossTheMapIsNotAWalk", SnowPrintTests::aJumpAcrossTheMapIsNotAWalk);
        Checks.run("onlyTheNewestFootprintsAreKept", SnowPrintTests::onlyTheNewestFootprintsAreKept);
        Checks.run("aDriveLeavesItsPathInStretches", SnowPrintTests::aDriveLeavesItsPathInStretches);
        Checks.run("gettingOutOfAVehicleDoesNotJoinItsRutToTheWalk", SnowPrintTests::gettingOutOfAVehicleDoesNotJoinItsRutToTheWalk);
        Checks.run("placesAreCountedFromThePicturesCorner", SnowPrintTests::placesAreCountedFromThePicturesCorner);
        Checks.run("onlyTheNewestStretchesOfRutAreKept", SnowPrintTests::onlyTheNewestStretchesOfRutAreKept);
        Checks.run("forgottenTheyAreGone", SnowPrintTests::forgottenTheyAreGone);
        Checks.run("twoWalkersEachLeaveTheirOwnWay", SnowPrintTests::twoWalkersEachLeaveTheirOwnWay);
    }

    /** Walks so many steps of that length from a place along x. */
    private static void walk(float fromX, float y, int steps, float step) {
        for (int i = 0; i <= steps; i++) {
            SnowPrints.walked(0, fromX + i * step, y, 0f);
        }
    }

    private static void read(float eyeX, float eyeY) {
        SnowPrints.placed(Terrain.LEVEL, eyeX, eyeY, prints, ruts, rutsUp, rest);
    }

    private static void aWalkLeavesAFootprintAStrideLeftAndRightByTurns() {
        SnowPrints.forget();
        walk(30f, 20f, 32, 0.0625f);
        read(0f, 0f);
        check(rest[0] == 4f, "two tiles walked are four strides: " + rest[0]);
        check(near(prints[0], 30f + SnowPrints.STRIDE) && near(prints[4], 30f + 2f * SnowPrints.STRIDE), "a footprint at every stride: " + prints[0] + ", " + prints[4]);
        check(near(Math.abs(prints[1] - 20f), SnowPrints.STANCE) && near(prints[1] - 20f, 20f - prints[5]), "to one side of the way walked and then the other: " + prints[1] + ", " + prints[5]);
        check(near(prints[2], 0f), "pointing the way walked: " + prints[2]);
        SnowPrints.forget();
        for (int i = 0; i <= 16; i++) {
            SnowPrints.walked(0, 30f, 20f - i * 0.0625f, 0f);
        }
        read(0f, 0f);
        check(near(prints[2], (float) -Math.PI / 2f), "and up the map when the walk is up the map: " + prints[2]);
    }

    private static void standingStillLeavesNothing() {
        SnowPrints.forget();
        for (int i = 0; i < 500; i++) {
            SnowPrints.walked(0, 30f + 0.01f * (i % 3), 20f, 0f);
        }
        read(0f, 0f);
        check(rest[0] == 0f, "shifting on the spot is not a stride: " + rest[0]);
        check(rest[2] > rest[4], "and with no mark the box holds nothing");
    }

    private static void aJumpAcrossTheMapIsNotAWalk() {
        SnowPrints.forget();
        SnowPrints.walked(0, 30f, 20f, 0f);
        SnowPrints.walked(0, 30f + SnowPrints.JUMP + 1f, 20f, 0f);
        read(0f, 0f);
        check(rest[0] == 0f, "a jump leaves no footprint: " + rest[0]);
        SnowPrints.walked(0, 30f + SnowPrints.JUMP + 1f + SnowPrints.STRIDE, 20f, 0f);
        read(0f, 0f);
        check(rest[0] == 1f, "and the walk goes on from where it landed: " + rest[0]);
    }

    private static void onlyTheNewestFootprintsAreKept() {
        SnowPrints.forget();
        walk(30f, 20f, SnowPrints.PRINTS + 10, SnowPrints.STRIDE);
        read(0f, 0f);
        check(rest[0] == SnowPrints.PRINTS, "no more footprints than there is room for: " + rest[0]);
        float least = Float.MAX_VALUE;
        for (int i = 0; i < SnowPrints.PRINTS; i++) {
            least = Math.min(least, prints[i * 4]);
        }
        check(near(least, 30f + 11f * SnowPrints.STRIDE), "and the ones that went are the oldest: " + least);
    }

    private static void aDriveLeavesItsPathInStretches() {
        SnowPrints.forget();
        for (int i = 0; i <= 25; i++) {
            SnowPrints.drove(0, 30f + i * 0.125f, 20f, 1.5f);
        }
        read(0f, 0f);
        check(rest[1] == 5f && rest[0] == 0f, "a little over three tiles driven are five stretches of rut and no footprint: " + rest[1]);
        check(near(ruts[0], 30f) && near(ruts[2], 30f + SnowPrints.RUT) && near(ruts[4], ruts[2]), "each from where the last ended: " + ruts[0] + " to " + ruts[2]);
        check(rutsUp[0] == 1.5f && rutsUp[4] == 1.5f, "on the ground the vehicle stood on: " + rutsUp[0]);
        check(rest[3] < 20f - 0.5f && rest[5] > 20f + 0.5f, "and the box has room for a wheel to either side of the path");
        SnowPrints.drove(0, 30f + 26f * 0.125f + SnowPrints.RUT, 20f, 4f);
        read(0f, 0f);
        check(rutsUp[5] == 4f && rutsUp[0] == 1.5f, "a stretch on higher ground is on its own height, and the ones before it on theirs: " + rutsUp[5]);
    }

    private static void gettingOutOfAVehicleDoesNotJoinItsRutToTheWalk() {
        SnowPrints.forget();
        SnowPrints.drove(0, 30f, 20f, 0f);
        SnowPrints.drove(0, 31f, 20f, 0f);
        SnowPrints.walked(0, 31f, 21f, 0f);
        SnowPrints.walked(0, 31f, 21.2f, 0f);
        read(0f, 0f);
        check(rest[0] == 0f && rest[1] == 1f, "stepping out is not a stride from the driver's seat: " + rest[0] + " footprints, " + rest[1] + " stretches");
        SnowPrints.drove(0, 31f, 22f, 0f);
        read(0f, 0f);
        check(rest[1] == 1f, "nor is getting in a stretch of rut: " + rest[1]);
    }

    private static void placesAreCountedFromThePicturesCorner() {
        SnowPrints.forget();
        walk(10790f, 9770f, 1, SnowPrints.STRIDE);
        read(10795.25f, 9772.5f);
        // vPlace is the place less the eye plus what is left of the eye over sixty tiles: 10790.5 - 10740 and 9770 - 9720.
        check(near(prints[0], 50.5f) && near(Math.abs(prints[1] - 50f), SnowPrints.STANCE), "a footprint is where vPlace has the ground under it: " + prints[0] + ", " + prints[1]);
        read(10801f, 9772.5f);
        check(near(prints[0], -9.5f), "and moves sixty tiles with the corner: " + prints[0]);
    }

    private static void onlyTheNewestStretchesOfRutAreKept() {
        SnowPrints.forget();
        for (int i = 0; i <= SnowPrints.RUTS + 3; i++) {
            SnowPrints.drove(0, 30f + i * SnowPrints.RUT, 20f, i);
        }
        read(0f, 0f);
        check(rest[1] == SnowPrints.RUTS, "no more stretches than there is room for: " + rest[1]);
        float least = Float.MAX_VALUE, lowest = Float.MAX_VALUE;
        for (int i = 0; i < SnowPrints.RUTS; i++) {
            least = Math.min(least, ruts[i * 4]);
            lowest = Math.min(lowest, rutsUp[i]);
        }
        check(near(least, 30f + 3f * SnowPrints.RUT) && lowest == 4f, "and the ones that went are the oldest, their heights with them: " + least + ", " + lowest);
    }

    private static void forgottenTheyAreGone() {
        SnowPrints.forget();
        walk(30f, 20f, 4, SnowPrints.STRIDE);
        SnowPrints.forget();
        read(0f, 0f);
        check(rest[0] == 0f && rest[2] > rest[4], "forgotten, the footprints are gone: " + rest[0]);
        SnowPrints.walked(0, 32f + SnowPrints.STRIDE, 20f, 0f);
        read(0f, 0f);
        check(rest[0] == 0f, "and the walk does not go on from where it was: " + rest[0]);
    }

    private static void twoWalkersEachLeaveTheirOwnWay() {
        SnowPrints.forget();
        // Side by side a street apart, a step each by turns, as two players at one screen are told of.
        for (int i = 0; i <= 8; i++) {
            SnowPrints.walked(0, 30f + i * SnowPrints.STRIDE, 20f, 0f);
            SnowPrints.walked(1, 30f + i * SnowPrints.STRIDE, 23f, 0f);
        }
        read(0f, 0f);
        check(rest[0] == 16f, "each walker leaves a footprint at each of their own strides: " + rest[0]);
        for (int i = 0; i < 16; i++) {
            float fromOwnWay = Math.min(Math.abs(prints[i * 4 + 1] - 20f), Math.abs(prints[i * 4 + 1] - 23f));
            check(near(fromOwnWay, SnowPrints.STANCE) && near(prints[i * 4 + 2], 0f), "beside the way that walker went and pointing along it: " + prints[i * 4 + 1]);
        }
        check(near(prints[1] - 20f, prints[5] - 23f) && near(prints[9] - 20f, prints[13] - 23f) && near(prints[1] - 20f, 20f - prints[9]),
                "each steps left and right by their own turns, not by the other's");
        SnowPrints.lifted(0);
        SnowPrints.walked(0, 80f, 20f, 0f);
        SnowPrints.walked(1, 30f + 9f * SnowPrints.STRIDE, 23f, 0f);
        read(0f, 0f);
        check(rest[0] == 17f, "one walker lifted off the snow leaves the other's walk going on: " + rest[0]);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-3f;
    }

    private static void check(boolean condition, String message) {
        Checks.check(condition, message);
    }
}

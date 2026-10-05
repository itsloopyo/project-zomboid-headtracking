package com.cameraunlock.zomboid;

/** Tests of how the mod's own rifle works its bolt. */
final class RifleTests {
    private RifleTests() {}

    static void run() {
        boltIsShutBeforeAndAfter();
        handleIsUpBeforeTheBoltIsDrawnAndUntilItIsHome();
        boltComesRightBackOnce();
    }

    private static void boltIsShutBeforeAndAfter() {
        float[] bolt = new float[2];
        OwnRifle.worked(0f, bolt);
        check(bolt[0] == 0f && bolt[1] == 0f, "the bolt is shut as its working begins");
        OwnRifle.worked(5f, bolt);
        check(Math.abs(bolt[0]) < 1e-6f && Math.abs(bolt[1]) < 1e-6f, "and shut again when it is over");
    }

    private static void handleIsUpBeforeTheBoltIsDrawnAndUntilItIsHome() {
        float[] bolt = new float[2];
        for (float seconds = 0f; seconds < 1f; seconds += 0.002f) {
            OwnRifle.worked(seconds, bolt);
            check(bolt[0] >= -1e-6f && bolt[0] <= 1f + 1e-6f && bolt[1] >= -1e-6f && bolt[1] <= 1f + 1e-6f, "neither the turn nor the draw leaves its range at " + seconds);
            check(bolt[1] < 1e-4f || bolt[0] > 1f - 1e-4f, "the bolt is " + bolt[1] + " back with its handle only " + bolt[0] + " up at " + seconds);
        }
    }

    private static void boltComesRightBackOnce() {
        float[] bolt = new float[2];
        float most = 0f;
        int rises = 0;
        float before = 0f;
        boolean rising = false;
        for (float seconds = 0f; seconds < 1f; seconds += 0.002f) {
            OwnRifle.worked(seconds, bolt);
            most = Math.max(most, bolt[1]);
            if (bolt[1] > before + 1e-6f && !rising) {
                rises++;
                rising = true;
            } else if (bolt[1] < before - 1e-6f) {
                rising = false;
            }
            before = bolt[1];
        }
        check(most == 1f, "the bolt comes right back, not " + most);
        check(rises == 1, "and once, not " + rises + " times");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}

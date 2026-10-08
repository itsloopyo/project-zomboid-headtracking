package com.cameraunlock.zomboid;

import java.util.Arrays;

/** Tests of how a square's light is found without the viewer's torch in it. */
final class TorchLessTests {
    private static final long ROOM = 7L, NEXT_ROOM = 8L;

    private TorchLessTests() {}

    static void run() {
        Checks.run("aSumUnderOneGivesTheLightBack", TorchLessTests::aSumUnderOneGivesTheLightBack);
        Checks.run("aSumStoppedAtOneIsOnlyALeast", TorchLessTests::aSumStoppedAtOneIsOnlyALeast);
        Checks.run("aDarkRoomStaysAsDarkAsItsCorners", TorchLessTests::aDarkRoomStaysAsDarkAsItsCorners);
        Checks.run("aLampsLightRunsOnUnderTheFan", TorchLessTests::aLampsLightRunsOnUnderTheFan);
        Checks.run("nothingIsCarriedThroughAWall", TorchLessTests::nothingIsCarriedThroughAWall);
        Checks.run("aSquareCutOffKeepsItsLeast", TorchLessTests::aSquareCutOffKeepsItsLeast);
        Checks.run("nothingIsGivenLessThanItsLeast", TorchLessTests::nothingIsGivenLessThanItsLeast);
        Checks.run("aSquareKnownInPartKeepsTheColourRoundIt", TorchLessTests::aSquareKnownInPartKeepsTheColourRoundIt);
        Checks.run("aSquareTheTorchComesOntoKeepsTheLightItHad", TorchLessTests::aSquareTheTorchComesOntoKeepsTheLightItHad);
    }

    private static void aSquareTheTorchComesOntoKeepsTheLightItHad() {
        float[] before = {0.2f, 0.3f, 0.4f};
        float[] light = new float[3], least = new float[3];
        boolean[] known = new boolean[3];
        // The torch stops red at 1, and leaves green a step off what it was and blue as it was.
        TorchLess.without(1f, 0.9f, light, known, least, 0);
        TorchLess.without(0.8f + 1f / 255f, 0.5f, light, known, least, 1);
        TorchLess.without(0.9f, 0.5f, light, known, least, 2);
        check(TorchLess.keep(light, known, least, 0, before, 0), "a square with a light known before is known still");
        check(light[0] == 0.2f && known[0], "a part the torch has blotted out keeps what it was last known to be: " + light[0]);
        check(light[1] == 0.3f, "a part a step off what it was is what it was, to the bit: " + light[1]);
        check(light[2] == 0.4f, "and so is one that has not moved: " + light[2]);

        // The room's own lamp goes on under the torch: the least the light can be is now more than it was.
        TorchLess.without(1f, 0.3f, light, known, least, 0);
        TorchLess.without(0.95f, 0.3f, light, known, least, 1);
        TorchLess.without(0.4f, 0f, light, known, least, 2);
        TorchLess.keep(light, known, least, 0, before, 0);
        check(near(light[0], 0.7f), "a blotted part is never less than the least it can be: " + light[0]);
        check(near(light[1], 0.65f), "a known part that has really changed is what it is now: " + light[1]);

        TorchLess.without(1f, 0.9f, light, known, least, 0);
        TorchLess.without(0.5f, 0f, light, known, least, 1);
        TorchLess.without(0.5f, 0f, light, known, least, 2);
        check(!TorchLess.keep(light, known, least, 0, null, 0) && !known[0] && near(light[0], 0.1f), "with nothing known before, a blotted square is left for the squares round it");
    }

    private static void aSumUnderOneGivesTheLightBack() {
        float[] light = new float[3], least = new float[3];
        boolean[] known = new boolean[3];
        // Read in the game: a square at 0.361 with the torch adding 0.235 came out at 0.596.
        TorchLess.without(0.596f, 0.235f, light, known, least, 0);
        check(known[0] && near(light[0], 0.361f), "a sum under 1 gives the light without the torch exactly: " + light[0]);
        TorchLess.without(1f, 0f, light, known, least, 1);
        check(known[1] && light[1] == 1f, "a square the torch is not on keeps its light, at 1 as anywhere");
    }

    private static void aSumStoppedAtOneIsOnlyALeast() {
        float[] light = new float[3], least = new float[3];
        boolean[] known = new boolean[3];
        TorchLess.without(1f, 0.6f, light, known, least, 0);
        check(!known[0] && near(least[0], 0.4f), "a sum stopped at 1 says only that the light was at least 1 less the torch's share: " + least[0]);
        TorchLess.without(1f, 1f, light, known, least, 1);
        check(!known[1] && least[1] == 0f, "and nothing at all where the torch alone gives 1");
    }

    /** A level of span by span squares of one room, all at one light and all known. */
    private static float[] level(int span, float all, boolean[] known, float[] least, long[] room) {
        float[] light = new float[span * span * 3];
        Arrays.fill(light, all);
        Arrays.fill(known, true);
        Arrays.fill(least, 0f);
        Arrays.fill(room, ROOM);
        return light;
    }

    private static void blot(int span, int i, int j, float[] light, boolean[] known) {
        for (int part = 0; part < 3; part++) {
            known[(j * span + i) * 3 + part] = false;
            light[(j * span + i) * 3 + part] = 0f;
        }
    }

    private static void aDarkRoomStaysAsDarkAsItsCorners() {
        int span = 7;
        boolean[] known = new boolean[span * span * 3];
        float[] least = new float[span * span * 3];
        long[] room = new long[span * span];
        float[] light = level(span, 0.161f, known, least, room);
        for (int j = 1; j < span - 1; j++) {
            for (int i = 1; i < span - 1; i++) {
                blot(span, i, j, light, known);
            }
        }
        TorchLess.settle(span, light, known, least, room);
        for (int at = 0; at < light.length; at++) {
            check(near(light[at], 0.161f), "a dark room the torch has blotted out is as dark all through as its edge: " + light[at]);
        }
    }

    private static void aLampsLightRunsOnUnderTheFan() {
        int span = 6;
        boolean[] known = new boolean[span * span * 3];
        float[] least = new float[span * span * 3];
        long[] room = new long[span * span];
        float[] light = level(span, 0f, known, least, room);
        for (int j = 0; j < span; j++) {
            for (int i = 0; i < span; i++) {
                Arrays.fill(light, (j * span + i) * 3, (j * span + i) * 3 + 3, i == 0 ? 0.2f : 0.6f);
                if (i > 0 && i < span - 1) {
                    blot(span, i, j, light, known);
                }
            }
        }
        TorchLess.settle(span, light, known, least, room);
        for (int i = 1; i < span; i++) {
            float before = light[(2 * span + i - 1) * 3], here = light[(2 * span + i) * 3];
            check(here >= before - 1e-5f && here >= 0.2f - 1e-5f && here <= 0.6f + 1e-5f,
                    "between a dim side and a lamplit side the light rises from one to the other: " + before + " then " + here);
        }
    }

    private static void nothingIsCarriedThroughAWall() {
        int span = 4;
        boolean[] known = new boolean[span * span * 3];
        float[] least = new float[span * span * 3];
        long[] room = new long[span * span];
        float[] light = level(span, 0.161f, known, least, room);
        // The right half is another room, lamplit, and known. The left's column beside it is blotted out.
        for (int j = 0; j < span; j++) {
            for (int i = 2; i < span; i++) {
                room[j * span + i] = NEXT_ROOM;
                Arrays.fill(light, (j * span + i) * 3, (j * span + i) * 3 + 3, 0.7f);
            }
            blot(span, 1, j, light, known);
        }
        TorchLess.settle(span, light, known, least, room);
        for (int j = 0; j < span; j++) {
            check(near(light[(j * span + 1) * 3], 0.161f), "a square takes its light from its own room, not from the lamplit one through the wall: " + light[(j * span + 1) * 3]);
        }
    }

    private static void aSquareCutOffKeepsItsLeast() {
        int span = 3;
        boolean[] known = new boolean[span * span * 3];
        float[] least = new float[span * span * 3];
        long[] room = new long[span * span];
        float[] light = level(span, 0.5f, known, least, room);
        Arrays.fill(room, TorchLess.NOT_THERE);
        room[4] = ROOM;
        blot(span, 1, 1, light, known);
        Arrays.fill(least, 12, 15, 0.3f);
        Arrays.fill(light, 12, 15, 0.3f);
        TorchLess.settle(span, light, known, least, room);
        check(near(light[12], 0.3f), "a square with no known square of its room to reach keeps the least it can be: " + light[12]);
    }

    private static void nothingIsGivenLessThanItsLeast() {
        int span = 3;
        boolean[] known = new boolean[span * span * 3];
        float[] least = new float[span * span * 3];
        long[] room = new long[span * span];
        float[] light = level(span, 0.1f, known, least, room);
        blot(span, 1, 1, light, known);
        Arrays.fill(least, 12, 15, 0.45f);
        TorchLess.settle(span, light, known, least, room);
        check(near(light[12], 0.45f), "a square whose light is known to be at least 0.45 is not given the 0.1 round it: " + light[12]);
    }

    private static void aSquareKnownInPartKeepsTheColourRoundIt() {
        int span = 3;
        boolean[] known = new boolean[span * span * 3];
        float[] least = new float[span * span * 3];
        long[] room = new long[span * span];
        float[] light = level(span, 0f, known, least, room);
        // A lamp's warm light all round, and in the middle a square half as bright whose blue alone is exact.
        for (int at = 0; at < span * span; at++) {
            light[at * 3] = 0.4f;
            light[at * 3 + 1] = 0.3f;
            light[at * 3 + 2] = 0.2f;
        }
        known[12] = false;
        known[13] = false;
        light[12] = 0f;
        light[13] = 0f;
        light[14] = 0.1f;
        TorchLess.settle(span, light, known, least, room);
        check(near(light[12], 0.2f) && near(light[13], 0.15f) && near(light[14], 0.1f),
                "a square whose blue alone is known takes the colour round it at the brightness its blue has: " + light[12] + " " + light[13] + " " + light[14]);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 2e-3f;
    }
}

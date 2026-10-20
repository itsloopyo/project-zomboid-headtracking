package com.cameraunlock.zomboid;

/** Tests of how a square's light is found without the viewer's torch in it. */
final class TorchLessTests {
    /** A street lamp as the engine lists it: colour and radius. */
    private static final float[] STREET_LAMP = {1f, 160f / 255f, 40f / 255f};
    private static final float[] ROOM_LIGHT = {229f / 255f, 216f / 255f, 209f / 255f};
    private static final float[] NIGHT = {60f / 255f, 38f / 255f, 30f / 255f};
    private static final float[] NO_TORCH = {0f, 0f, 0f};

    private TorchLessTests() {}

    static void run() {
        Checks.run("aSumUnderOneGivesTheLightBack", TorchLessTests::aSumUnderOneGivesTheLightBack);
        Checks.run("aSumStoppedAtOneIsOnlyALeast", TorchLessTests::aSumStoppedAtOneIsOnlyALeast);
        Checks.run("aLightIsNeverLessThanItsDaylight", TorchLessTests::aLightIsNeverLessThanItsDaylight);
        Checks.run("aStreetLampsLightIsWorkedOutAsTheEngineMakesIt", TorchLessTests::aStreetLampsLightIsWorkedOutAsTheEngineMakesIt);
        Checks.run("theBrightestLightWinsEachPart", TorchLessTests::theBrightestLightWinsEachPart);
        Checks.run("aLightIsNoBrighterThanTheKnownPartsAllow", TorchLessTests::aLightIsNoBrighterThanTheKnownPartsAllow);
        Checks.run("otherTorchesStayInTheLight", TorchLessTests::otherTorchesStayInTheLight);
        Checks.run("aStepEitherWayKeepsTheLightItHad", TorchLessTests::aStepEitherWayKeepsTheLightItHad);
        Checks.run("aShotsLampLeftBehindIsLetGo", TorchLessTests::aShotsLampLeftBehindIsLetGo);
    }

    private static void aShotsLampLeftBehindIsLetGo() {
        check(TorchLess.reaches(10943, 9860, 18, 10943, 9860), "a shot's lamp on the viewer's square is taken out");
        check(TorchLess.reaches(10943, 9860, 18, 10925, 9878), "and so is one as far off as its light reaches");
        check(!TorchLess.reaches(10943, 9860, 18, 10924, 9860), "one a square further is let go");
        check(!TorchLess.reaches(10943, 9860, 18, 12772, 1565), "a lamp the viewer was moved across the map from is let go");
    }

    private static void aSumUnderOneGivesTheLightBack() {
        float[] light = new float[3], least = new float[3];
        boolean[] known = new boolean[3];
        // Read in the game: a square at 0.361 with the torch adding 0.235 came out at 0.596.
        TorchLess.without(0.596f, 0.235f, 0f, light, known, least, 0);
        check(known[0] && near(light[0], 0.361f), "a sum under 1 gives the light without the torch exactly: " + light[0]);
        TorchLess.without(1f, 0f, 0f, light, known, least, 1);
        check(known[1] && light[1] == 1f, "a square the torch is not on keeps its light, at 1 as anywhere");
    }

    private static void aSumStoppedAtOneIsOnlyALeast() {
        float[] light = new float[3], least = new float[3];
        boolean[] known = new boolean[3];
        TorchLess.without(1f, 0.6f, 0f, light, known, least, 0);
        check(!known[0] && near(least[0], 0.4f), "a sum stopped at 1 says only that the light was at least 1 less the torch's share: " + least[0]);
        TorchLess.without(1f, 1f, 0f, light, known, least, 1);
        check(!known[1] && least[1] == 0f, "and nothing at all where the torch alone gives 1");
    }

    private static void aLightIsNeverLessThanItsDaylight() {
        float[] light = new float[3], least = new float[3];
        boolean[] known = new boolean[3];
        TorchLess.without(1f, 1f, 0.3f, light, known, least, 0);
        check(near(least[0], 0.3f), "the engine joins the lamps to the daylight by the greater, so the light is at least the daylight: " + least[0]);
    }

    /** Works out a square of light without the torch, red, green and blue all stopped at 1. */
    private static float[] stopped(int x, int y, float[] day, float[] others, int[] places, float[]... colours) {
        float[] light = new float[3], least = {day[0], day[1], day[2]};
        float[] colour = new float[colours.length * 3];
        for (int lamp = 0; lamp < colours.length; lamp++) {
            System.arraycopy(colours[lamp], 0, colour, lamp * 3, 3);
        }
        float[] all = new float[colours.length];
        java.util.Arrays.fill(all, 1f);
        TorchLess.fromLights(light, new boolean[3], least, day, others, 0, 1f, x, y, 0, colours.length, places, colour, 0, all);
        return light;
    }

    private static void aStreetLampsLightIsWorkedOutAsTheEngineMakesIt() {
        // Read in the game at 00:30: a street lamp of radius 20 a level up at (10718, 9789), and the squares
        // round it with no torch, red 204 a square off, 173 three across and one down, 149 three and four.
        int[] lamp = {10718, 9789, 1, 20};
        check(near(stopped(10717, 9789, NIGHT, NO_TORCH, lamp, STREET_LAMP)[0], 204f / 255f), "a square beside the lamp's foot is lit as the engine lit it");
        check(near(stopped(10721, 9790, NIGHT, NO_TORCH, lamp, STREET_LAMP)[0], 173f / 255f), "a way across a corner is the square root of 2 a step");
        float[] far = stopped(10721, 9793, NIGHT, NO_TORCH, lamp, STREET_LAMP);
        check(near(far[0], 149f / 255f) && near(far[1], 94f / 255f) && near(far[2], 30f / 255f),
                "each part is the lamp's colour cut to the byte, and the night's blue where the lamp's is less: " + far[0] + " " + far[1] + " " + far[2]);
        float[] out = stopped(10690, 9789, NIGHT, NO_TORCH, lamp, STREET_LAMP);
        check(near(out[0], NIGHT[0]) && near(out[1], NIGHT[1]), "past the lamp's radius the square has its daylight");
    }

    private static void theBrightestLightWinsEachPart() {
        // Read in the game at 00:30 at (10724, 9785), between a street lamp and a building's lit rooms, with the
        // five lights it lists: 0.467 0.310 0.302, the street lamp's red and the nearest room light's green and blue.
        int[] places = {10726, 9781, 0, 7, 10728, 9784, 0, 7, 10727, 9787, 0, 5, 10728, 9786, 0, 7, 10718, 9789, 1, 20};
        float[] mixed = stopped(10724, 9785, NIGHT, NO_TORCH, places, ROOM_LIGHT, ROOM_LIGHT, new float[] {1f, 200f / 255f, 160f / 255f}, ROOM_LIGHT, STREET_LAMP);
        check(near(mixed[0], 119f / 255f) && near(mixed[1], 79f / 255f) && near(mixed[2], 77f / 255f),
                "each part is the brightest of the lights, not their sum, and not one light's colour: " + mixed[0] + " " + mixed[1] + " " + mixed[2]);
    }

    private static void aLightIsNoBrighterThanTheKnownPartsAllow() {
        // A room light whose way runs round a wall: its blue is known and is half of what the straight way gives.
        float[] light = {0f, 0f, 60f / 255f}, least = {0f, 0f, 0f};
        boolean[] known = {false, false, true};
        int[] place = {11, 10, 0, 7};
        float allowed = TorchLess.allows(light, known, 0, 10, 10, 0, place, ROOM_LIGHT, 0);
        check(Float.isNaN(TorchLess.allows(light, new boolean[3], 0, 10, 10, 0, place, ROOM_LIGHT, 0)), "a square with no part known says nothing of how much of a light reaches it");
        TorchLess.fromLights(light, known, least, new float[3], NO_TORCH, 0, 1f, 10, 10, 0, 1, place, ROOM_LIGHT, 0, new float[] {allowed});
        float straight = (int) (209f * (1f - 1f / 7f));
        check(near(light[2], 60f / 255f), "a known part is left as it is");
        check(near(light[0] / light[2], 229f / 209f) && light[0] * 255f < straight,
                "the parts it is not known in are dimmed with it, in the light's own colour: " + light[0] + " " + light[1]);
    }

    private static void otherTorchesStayInTheLight() {
        float[] light = new float[3], least = new float[3];
        boolean[] known = new boolean[3];
        float[] beside = {0.2f, 0.2f, 0.2f};
        TorchLess.fromLights(light, known, least, NIGHT, beside, 0, 1f, 0, 0, 0, 0, new int[0], new float[0], 0, new float[0]);
        check(near(light[0], NIGHT[0] + 0.2f), "a car's lamps or another player's torch on the square are part of its light without the viewer's: " + light[0]);
    }

    private static void aStepEitherWayKeepsTheLightItHad() {
        float[] before = {0.2f, 0.3f, 0.4f};
        float[] light = {0.2f + 1f / 255f, 0.3f - 2f / 255f, 0.45f};
        TorchLess.steady(light, 0, before, 0);
        check(light[0] == 0.2f && light[1] == 0.3f, "a part a step or two off what it was is what it was, to the bit");
        check(light[2] == 0.45f, "a part that has really changed is what it is now");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1.5f / 255f;
    }
}

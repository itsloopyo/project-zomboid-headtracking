package com.cameraunlock.zomboid;

/** Tests of how much light the moon gives by the Night Darkness setting, its phase and the sky: see SkyState.lightColour. */
final class MoonlightTests {
    private MoonlightTests() {}

    private static final int PITCH_BLACK = 1, NORMAL = 3, BRIGHT = 4;

    static void run() {
        Checks.run("aNewMoonInAClosedSkyAtNormalIsTheOldMoonlight", MoonlightTests::aNewMoonInAClosedSkyAtNormalIsTheOldMoonlight);
        Checks.run("aFullMoonInAClearSkyLightsMoreAndCloudAndFogTakeItBack", MoonlightTests::aFullMoonInAClearSkyLightsMoreAndCloudAndFogTakeItBack);
        Checks.run("theMoonFollowsTheNightDarknessSetting", MoonlightTests::theMoonFollowsTheNightDarknessSetting);
        Checks.run("theMoonDoesNotReachTheSun", MoonlightTests::theMoonDoesNotReachTheSun);
        Checks.run("aStepOfThePhaseIsEasedIn", MoonlightTests::aStepOfThePhaseIsEasedIn);
    }

    private static float[] colour(float sunUp, float white, float night, float moonGives) {
        float[] into = new float[3];
        SkyState.lightColour(sunUp, white, night, moonGives, into);
        return into;
    }

    private static void aNewMoonInAClosedSkyAtNormalIsTheOldMoonlight() {
        float[] light = colour(-0.8f, 0f, 0.7f, SkyState.moonGives(NORMAL, 0f, 1f));
        Checks.check(light[0] == 0.10f * 0.7f && light[1] == 0.13f * 0.7f && light[2] == 0.20f * 0.7f,
                "a new moon in a closed sky at Normal is (0.10, 0.13, 0.20) times night: " + light[0] + ", " + light[1] + ", " + light[2]);
        Checks.check(SkyState.moonGives(NORMAL, 1f, 1f) == 1f, "a full moon behind a closed sky gives no more than that");
    }

    private static void aFullMoonInAClearSkyLightsMoreAndCloudAndFogTakeItBack() {
        float full = SkyState.moonGives(NORMAL, 1f, 0f), half = SkyState.moonGives(NORMAL, 0.5f, 0f);
        Checks.check(full > half && half > 1f, "a full moon in a clear sky gives more than a half moon, and that more than none: " + half + ", " + full);
        Checks.check(SkyState.moonGives(NORMAL, 1f, SkyState.covered(0.6f, 0f, 0f)) < SkyState.moonGives(NORMAL, 1f, SkyState.covered(0.2f, 0f, 0f)), "more cloud, less moonlight");
        Checks.check(SkyState.moonGives(NORMAL, 1f, SkyState.covered(0f, 0f, 1f)) == 1f, "full fog takes all of a full moon's light back");
        Checks.check(SkyState.moonGives(NORMAL, 1f, SkyState.covered(0f, 1f, 0f)) == 1f, "so does a sky that rain or snow falls from");
    }

    private static void theMoonFollowsTheNightDarknessSetting() {
        float[] black = colour(-0.8f, 0f, 1f, SkyState.moonGives(PITCH_BLACK, 0f, 0f));
        Checks.check(black[0] == 0f && black[1] == 0f && black[2] == 0f, "Pitch Black with no moon has no moonlight");
        Checks.check(SkyState.moonGives(BRIGHT, 0f, 0f) > SkyState.moonGives(NORMAL, 0f, 0f), "Bright has more than Normal");
        Checks.check(SkyState.moonGives(PITCH_BLACK, 1f, 0f) < SkyState.moonGives(NORMAL, 0f, 0f), "a full moon at Pitch Black stays darker than no moon at Normal");
    }

    private static void theMoonDoesNotReachTheSun() {
        float[] none = colour(0.5f, 1f, 0f, 0f), lots = colour(0.5f, 1f, 0f, 5f);
        Checks.check(none[0] == lots[0] && none[1] == lots[1] && none[2] == lots[2], "by day the moon's share changes nothing");
    }

    private static void aStepOfThePhaseIsEasedIn() {
        Checks.check(SkyState.eased(-1f, 0.75f, 100f, false) == 0.75f, "the first picture takes the phase as it is");
        float shown = SkyState.eased(0.5f, 0.75f, 1f / 60f, false);
        Checks.check(shown > 0.5f && shown < 0.51f, "a quarter's step comes in over many frames, not in one: " + shown);
        Checks.check(SkyState.eased(0.5f, 0.75f, 10f, false) < 0.75f, "a long frame does not take it whole either");
        Checks.check(SkyState.eased(0.5f, 0.75f, 1f / 60f, true) == 0.5f, "it holds while the game is paused");
        float s = 0.5f;
        for (int frame = 0; frame < 60 * 20; frame++) {
            s = SkyState.eased(s, 0.75f, 1f / 60f, false);
        }
        Checks.check(Math.abs(s - 0.75f) < 0.001f, "twenty seconds on it has arrived: " + s);
    }
}

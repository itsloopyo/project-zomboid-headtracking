package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of the light a drop of rain is drawn in, which it is kept where it is lighter than what is behind it. */
final class RainTests {
    private RainTests() {}

    static void run() {
        Checks.run("aDropIsNoBrighterThanTheAirWhateverTheSkyDoes", RainTests::aDropIsNoBrighterThanTheAirWhateverTheSkyDoes);
        Checks.run("aDropDimsWithTheDaylight", RainTests::aDropDimsWithTheDaylight);
        Checks.run("lightningLightsTheDrops", RainTests::lightningLightsTheDrops);
    }

    private static SkyState sky(float open, float kept, float horizon, float glow) {
        SkyState sky = new SkyState();
        sky.openRed = sky.openGreen = sky.openBlue = open;
        sky.daylightKept = kept;
        sky.horizonRed = sky.horizonGreen = sky.horizonBlue = horizon;
        sky.glowRed = glow;
        sky.glowGreen = 0.42f * glow;
        return sky;
    }

    private static float[] drop(SkyState sky) {
        float[] light = new float[3];
        sky.dropLight(light);
        return light;
    }

    /** From noon through sunset to night the open ground's light falls while the horizon glows; a drop follows the ground. */
    private static void aDropIsNoBrighterThanTheAirWhateverTheSkyDoes() {
        float[][] hours = {{0.85f, 1f, 0.7f, 0f}, {0.55f, 0.9f, 0.9f, 0.6f}, {0.3f, 0.7f, 1f, 1f}, {0.15f, 0.5f, 0.6f, 0.8f}, {0.05f, 0.4f, 0.1f, 0f}};
        for (float[] hour : hours) {
            float[] light = drop(sky(hour[0], hour[1], hour[2], hour[3]));
            float air = (float) Math.pow(hour[0] * hour[1], 2.2);
            for (float channel : light) {
                check(channel <= SkyState.RAIN_OVER_AIR * air + 1e-6f, "a drop is no brighter than the air it falls through: " + channel + " against " + air);
            }
            float[] glowing = drop(sky(hour[0], hour[1], 3f, 3f));
            check(glowing[0] == light[0] && glowing[2] == light[2], "and a brighter horizon does not make it brighter");
        }
    }

    private static void aDropDimsWithTheDaylight() {
        float day = drop(sky(0.85f, 1f, 0.7f, 0f))[1], dusk = drop(sky(0.3f, 0.7f, 1f, 1f))[1], night = drop(sky(0.05f, 0.4f, 0.1f, 0f))[1];
        check(day > dusk && dusk > night && night < 0.001f, "a drop is as bright as the day, and next to nothing at night: " + day + ", " + dusk + ", " + night);
    }

    private static void lightningLightsTheDrops() {
        SkyState sky = sky(0.05f, 0.4f, 0.1f, 0f);
        float dark = drop(sky)[2];
        sky.flashBlue = 0.5f;
        check(drop(sky)[2] == dark + SkyState.RAIN_FLASHED * 0.5f, "a flash lights every drop");
    }
}

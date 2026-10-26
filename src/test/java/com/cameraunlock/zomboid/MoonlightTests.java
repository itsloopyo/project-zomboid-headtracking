package com.cameraunlock.zomboid;

/** Tests of how much light the moon gives by its phase and the cloud: see SkyState.moonLift. */
final class MoonlightTests {
    private MoonlightTests() {}

    static void run() {
        Checks.run("aFullMoonInAClearSkyLightsMoreAndCloudTakesItBack", MoonlightTests::aFullMoonInAClearSkyLightsMoreAndCloudTakesItBack);
    }

    private static void aFullMoonInAClearSkyLightsMoreAndCloudTakesItBack() {
        Checks.check(SkyState.moonLift(0f, 0f) == 1f, "a new moon gives the night's least light");
        Checks.check(SkyState.moonLift(1f, 1f) == 1f, "a full moon behind a closed sky gives no more than that");
        Checks.check(SkyState.moonLift(1f, 0f) == 1f + SkyState.FULL_MOON_LIFT, "a full moon in a clear sky gives the most");
        Checks.check(SkyState.moonLift(0.5f, 0f) < SkyState.moonLift(1f, 0f) && SkyState.moonLift(0.5f, 0f) > 1f, "a half moon gives between");
        Checks.check(SkyState.moonLift(1f, 0.6f) < SkyState.moonLift(1f, 0.2f), "more cloud, less moonlight");
    }
}

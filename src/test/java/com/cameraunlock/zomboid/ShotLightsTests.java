package com.cameraunlock.zomboid;

final class ShotLightsTests {
    private ShotLightsTests() {}

    static void run() {
        Checks.run("aShotOnALampsCornerBreaksThatLampAndNoOther", ShotLightsTests::aShotOnALampsCornerBreaksThatLampAndNoOther);
        Checks.run("aShotBreaksALampOnlyNearTheMiddleOfItsSquare", ShotLightsTests::aShotBreaksALampOnlyNearTheMiddleOfItsSquare);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void aShotOnALampsCornerBreaksThatLampAndNoOther() {
        float halfWide = 0.9f, halfLong = 2.2f, at = ShotLights.LAMP_ACROSS * halfWide;
        check(ShotLights.onLamp(at, halfLong, halfWide, halfLong, 1f, 1f), "a shot on the front lamp's place is on it");
        check(!ShotLights.onLamp(at, halfLong, halfWide, halfLong, -1f, 1f), "and not on the front lamp across from it");
        check(!ShotLights.onLamp(at, halfLong, halfWide, halfLong, 1f, -1f), "nor the rear lamp behind it");
        check(!ShotLights.onLamp(0f, halfLong, halfWide, halfLong, 1f, 1f), "a shot on the middle of the nose breaks no lamp");
        check(ShotLights.onLamp(-at, -halfLong - 0.1f, halfWide, halfLong, -1f, -1f), "a shot just off a rear lamp's place on the body is on it");
    }

    private static void aShotBreaksALampOnlyNearTheMiddleOfItsSquare() {
        check(ShotLights.passesNear(0.5f, 0.5f, 1f, 0f, 5, 0), "a shot along the middle of a row of squares passes near each middle");
        check(!ShotLights.passesNear(0.5f, 0.05f, 1f, 0f, 5, 0), "one along the edge of the row does not");
        check(ShotLights.passesNear(0.5f, 0.5f, 1f, 1f, 3, 3), "and one on the diagonal passes near each middle it crosses");
    }
}

package com.cameraunlock.zomboid;

final class ShotLightsTests {
    private ShotLightsTests() {}

    static void run() {
        Checks.run("aShotOnALampsCornerBreaksThatLampAndNoOther", ShotLightsTests::aShotOnALampsCornerBreaksThatLampAndNoOther);
        Checks.run("aShotBreaksALampOnlyNearTheMiddleOfItsSquare", ShotLightsTests::aShotBreaksALampOnlyNearTheMiddleOfItsSquare);
        Checks.run("aStreetLampIsKnownByItsPlaceOnTheSheetLitOrNot", ShotLightsTests::aStreetLampIsKnownByItsPlaceOnTheSheetLitOrNot);
        Checks.run("aRoadLampsLightIsOutOnItsArm", ShotLightsTests::aRoadLampsLightIsOutOnItsArm);
        Checks.run("aShotBreaksARoadLampAtItsHeadAndNotOverItsPost", ShotLightsTests::aShotBreaksARoadLampAtItsHeadAndNotOverItsPost);
        Checks.run("aShotThatStopsShortOfALampLeavesIt", ShotLightsTests::aShotThatStopsShortOfALampLeavesIt);
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

    private static void aStreetLampIsKnownByItsPlaceOnTheSheetLitOrNot() {
        check(ShotLights.streetLampOf("lighting_outdoor_01_9", null) == 9, "a road lamp is known by its place on the sheet");
        check(ShotLights.streetLampOf("lighting_outdoor_01_on_9", null) == 9, "and the same lamp lit by its place on the lit sheet");
        check(ShotLights.streetLampOf("lighting_outdoor_01_9", "Wall Lamp") == -1, "a named thing on the sheet is no street lamp");
        check(ShotLights.streetLampOf("lighting_indoor_01_3", null) == -1, "nor is a lamp of another sheet");
        check(ShotLights.lightsOf(4) == null, "a bare post has no light");
    }

    private static void aRoadLampsLightIsOutOnItsArm() {
        float[] north = ShotLights.lightsOf(8), east = ShotLights.lightsOf(9), south = ShotLights.lightsOf(10), west = ShotLights.lightsOf(11);
        check(north[1] < -0.5f && Math.abs(north[0]) < 0.01f, "the first road lamp's light is out north, past its own square");
        check(east[0] > 0.5f && Math.abs(east[1]) < 0.01f, "the second's east");
        check(south[1] > 0.5f && Math.abs(south[0]) < 0.01f, "the third's south");
        check(west[0] < -0.5f && Math.abs(west[1]) < 0.01f, "the fourth's west");
        check(ShotLights.lightsOf(0)[2] > ViewCamera.LEVEL_HEIGHT, "the light of a lamp in one piece is up in the level over its foot");
        check(ShotLights.lightsOf(6).length == 6 && ShotLights.lightsOf(19).length == 6, "a lamp with two heads has two lights");
    }

    private static void aShotBreaksARoadLampAtItsHeadAndNotOverItsPost() {
        // The east road lamp's head on the level over a post at square (10, 10), the ground flat, shot at from the south.
        float[] light = ShotLights.lightsOf(9);
        float headX = 10.5f + light[0], headY = 10.5f + light[1], headUp = ViewCamera.LEVEL_HEIGHT + light[2];
        float eyeX = headX, eyeY = 16.5f, eyeUp = 1.6f;
        check(passes(eyeX, eyeY, eyeUp, headX, headY, headUp, headX, headY, headUp), "a shot at the head breaks it");
        check(passes(eyeX, eyeY, eyeUp, headX + 0.2f, headY, headUp + 0.15f, headX, headY, headUp), "so does one at the edge of the head");
        check(!passes(eyeX, eyeY, eyeUp, 10.5f, 10.5f, headUp, headX, headY, headUp), "one over the post, beside the head, does not");
        check(!passes(eyeX, eyeY, eyeUp, headX, headY, 1f, headX, headY, headUp), "nor one at the foot of the post");
    }

    private static void aShotThatStopsShortOfALampLeavesIt() {
        check(!ShotLights.passesBy(0f, 0f, 2f, 1f, 0f, 0f, 3f, 5f, 0f, 2f), "a shot stopped 2 tiles short of a lamp leaves it");
        check(ShotLights.passesBy(0f, 0f, 2f, 1f, 0f, 0f, 4.8f, 5f, 0f, 2f), "one stopped on the outside of the lamp breaks it");
        check(!ShotLights.passesBy(0f, 0f, 2f, -1f, 0f, 0f, 10f, 5f, 0f, 2f), "one the other way leaves it");
    }

    /** Whether a shot from the eye aimed at (toX, toY, toUp) and stopping well past it passes a light at (x, y, up). */
    private static boolean passes(float eyeX, float eyeY, float eyeUp, float toX, float toY, float toUp, float x, float y, float up) {
        float wayX = toX - eyeX, wayY = toY - eyeY, wayUp = toUp - eyeUp;
        float length = (float) Math.sqrt(wayX * wayX + wayY * wayY + wayUp * wayUp);
        return ShotLights.passesBy(eyeX, eyeY, eyeUp, wayX / length, wayY / length, wayUp / length, length + 5f, x, y, up);
    }
}

package com.cameraunlock.zomboid;

/** Tests of the glass of the windows that throw the room back. */
final class WindowGlassTests {
    private static final float TAN = (float) Math.tan(Math.toRadians(32.5)), ASPECT = 16f / 9f, NEAR = 0.05f;

    private WindowGlassTests() {}

    static void run() {
        Checks.run("aWindowsGlassIsItsTileOfTheWallFacingTheEye", WindowGlassTests::aWindowsGlassIsItsTileOfTheWallFacingTheEye);
        Checks.run("aWindowIsSeenFromItsRoomAndNotFromBehind", WindowGlassTests::aWindowIsSeenFromItsRoomAndNotFromBehind);
        Checks.run("theRoomBeginsPastTheWallAndIsLaidInTheOpening", WindowGlassTests::theRoomBeginsPastTheWallAndIsLaidInTheOpening);
        Checks.run("windowsAndMirrorsAreNeverOneGlass", WindowGlassTests::windowsAndMirrorsAreNeverOneGlass);
        Checks.run("aGlazedDoorsGlassIsItsLightsAndNoMore", WindowGlassTests::aGlazedDoorsGlassIsItsLightsAndNoMore);
        Checks.run("noWindowThrowsBackToAnEyeInTheOpenAir", WindowGlassTests::noWindowThrowsBackToAnEyeInTheOpenAir);
    }

    private static float[] window(boolean north, int edge, int along, float side) {
        float[] glass = new float[LookingGlass.FLOATS];
        WindowGlass.note(north, edge, along, 0f, side, glass, 0);
        return glass;
    }

    private static ViewCamera.Pose eye(float x, float y, float headingDegrees) {
        ViewCamera.Pose camera = new ViewCamera.Pose();
        ViewCamera.clean(camera, x, y, 1.6f, (float) Math.toRadians(headingDegrees), 0f);
        return camera;
    }

    /** The heading that looks from (fromX, fromY) to (x, y). */
    private static float toward(float fromX, float fromY, float x, float y) {
        ViewCamera.Pose probe = new ViewCamera.Pose();
        for (int degrees = 0; degrees < 360; degrees++) {
            ViewCamera.clean(probe, fromX, fromY, 1.6f, (float) Math.toRadians(degrees), 0f);
            float dx = x - fromX, dy = y - fromY, length = (float) Math.hypot(dx, dy);
            if ((probe.forwardX * dx + probe.forwardY * dy) / length > 0.9999f) {
                return degrees;
            }
        }
        throw new IllegalStateException("no heading looks from (" + fromX + ", " + fromY + ") to (" + x + ", " + y + ")");
    }

    private static void aWindowsGlassIsItsTileOfTheWallFacingTheEye() {
        float[] north = window(true, 30, 20, -1f);
        check(north[LookingGlass.CORNER] == 20f && north[LookingGlass.CORNER + 1] == 30f && north[LookingGlass.CORNER + 2] == 0f, "a north window's glass starts at its square's corner on the floor");
        check(north[LookingGlass.ACROSS] == 1f && north[LookingGlass.ACROSS + 1] == 0f, "and runs a tile along x");
        check(north[LookingGlass.UP + 2] == ViewCamera.LEVEL_HEIGHT, "up to the floor above");
        check(north[LookingGlass.FACING] == 0f && north[LookingGlass.FACING + 1] == -1f, "facing the eye's side of its edge");
        float[] west = window(false, 12, 7, 1f);
        check(west[LookingGlass.CORNER] == 12f && west[LookingGlass.CORNER + 1] == 7f, "a west window's glass starts at its square's corner");
        check(west[LookingGlass.ACROSS + 1] == 1f && west[LookingGlass.ACROSS] == 0f, "and runs a tile along y");
        check(west[LookingGlass.FACING] == 1f && west[LookingGlass.FACING + 1] == 0f, "facing the eye's side");
    }

    private static void aWindowIsSeenFromItsRoomAndNotFromBehind() {
        float[] glass = window(true, 30, 20, -1f);
        LookingGlass looking = new LookingGlass();
        float[] rect = new float[4];
        check(looking.seen(glass, 0, eye(20.5f, 27f, toward(20.5f, 27f, 20.5f, 30f)), TAN, ASPECT, NEAR, rect), "the window is seen from its room, looking at it");
        check(!looking.seen(glass, 0, eye(20.5f, 33f, toward(20.5f, 33f, 20.5f, 30f)), TAN, ASPECT, NEAR, rect), "and not from the street behind the side it was noted facing");
    }

    private static void noWindowThrowsBackToAnEyeInTheOpenAir() {
        check(WindowGlass.throwsBack(false, false), "a window throws the room back to an eye in the room");
        check(!WindowGlass.throwsBack(false, true), "and nothing to an eye in the room whose side of the edge is the street");
        check(!WindowGlass.throwsBack(true, true), "nor to an eye in the street before it");
        check(!WindowGlass.throwsBack(true, false), "nor to an eye in the street beyond the house, though the window's room is on its side of the edge");
    }

    private static void theRoomBeginsPastTheWallAndIsLaidInTheOpening() {
        check(WindowGlass.CLEAR > WorldScene.WALL_HALF, "what the glass shows begins past the room's face of the wall, which from behind would hide it all");
        check(WindowGlass.LAID > 0f && WindowGlass.LAID < WorldScene.WALL_HALF, "and is laid before the panes and behind the wall's face, which hides it but in the opening");
    }

    private static void windowsAndMirrorsAreNeverOneGlass() {
        ViewCamera.Pose camera = eye(20.5f, 27f, toward(20.5f, 27f, 20.5f, 30f));
        float[] windows = new float[LookingGlass.FLOATS * 2];
        WindowGlass.note(true, 30, 20, 0f, -1f, windows, 0);
        WindowGlass.note(true, 30, 21, 0f, -1f, windows, LookingGlass.FLOATS);
        // A small mirror in the same plane, beside the windows.
        float[] mirror = new float[LookingGlass.FLOATS];
        mirror[LookingGlass.CORNER] = 19.6f;
        mirror[LookingGlass.CORNER + 1] = 30f;
        mirror[LookingGlass.CORNER + 2] = 1.4f;
        mirror[LookingGlass.ACROSS] = 0.3f;
        mirror[LookingGlass.UP + 2] = 0.3f;
        mirror[LookingGlass.FACING + 1] = -1f;
        LookingGlass looking = new LookingGlass();
        looking.begin();
        looking.consider(mirror, 0, false, camera, TAN, ASPECT, NEAR);
        looking.consider(windows, 0, true, camera, TAN, ASPECT, NEAR);
        looking.consider(windows, 1, true, camera, TAN, ASPECT, NEAR);
        check(looking.end(), "the eye sees glass");
        check(looking.window && looking.count == 2, "the windows take more of the picture, and only they are kept: " + looking.count);
        check(looking.clear == WindowGlass.CLEAR && looking.laid == WindowGlass.LAID, "with a window's clearances");
        looking.begin();
        looking.consider(mirror, 0, false, camera, TAN, ASPECT, NEAR);
        check(looking.end() && !looking.window && looking.count == 1, "a mirror alone is a mirror");
        // A mirror a little smaller than one window, which throws back far more.
        mirror[LookingGlass.ACROSS] = 0.8f;
        mirror[LookingGlass.UP + 2] = 1f;
        mirror[LookingGlass.CORNER + 2] = 0.8f;
        looking.begin();
        looking.consider(windows, 0, true, camera, TAN, ASPECT, NEAR);
        looking.consider(mirror, 0, false, camera, TAN, ASPECT, NEAR);
        check(looking.end() && !looking.window, "a mirror is chosen over a window of the same size, which shows far less");
        check(looking.clear == LookingGlass.CLEAR && looking.laid == LookingGlass.CLEAR, "with a mirror's clearances");
    }

    private static void aGlazedDoorsGlassIsItsLightsAndNoMore() {
        Windows.Shape leaf = new Windows.Shape();
        // Two lights side by side over panels, the left one broken.
        leaf.panes = new float[] {0.3f, 0.45f, 0.5f, 0.85f, 0.4f, 0.5f, 0.65f, 0.55f, 0.9f, 1f};
        float[] north = window(true, 30, 20, -1f);
        WindowGlass.lights(leaf, true, north, 0);
        check(close(north[LookingGlass.CORNER], 20.3f) && north[LookingGlass.CORNER + 1] == 30f && close(north[LookingGlass.CORNER + 2], 0.5f * ViewCamera.LEVEL_HEIGHT),
                "a north door's glass starts where its first light does, at the foot of the lowest");
        check(close(north[LookingGlass.ACROSS], 0.35f) && north[LookingGlass.ACROSS + 1] == 0f && close(north[LookingGlass.UP + 2], 0.4f * ViewCamera.LEVEL_HEIGHT),
                "and runs along x to the end of the last light, up to the head of the highest");
        float[] west = window(false, 12, 7, 1f);
        WindowGlass.lights(leaf, false, west, 0);
        check(west[LookingGlass.CORNER] == 12f && close(west[LookingGlass.CORNER + 1], 7.3f) && close(west[LookingGlass.ACROSS + 1], 0.35f) && west[LookingGlass.ACROSS] == 0f,
                "a west door's glass runs along y over its lights");
        check(west[LookingGlass.FACING] == 1f, "facing the eye's side as a window's does");
        check(WindowGlass.LAID < OpeningBuilder.DOOR_HALF, "the picture is laid behind the leaf's faces, which hide it between the lights");
    }

    private static boolean close(float a, float b) {
        return Math.abs(a - b) < 1e-5f;
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}

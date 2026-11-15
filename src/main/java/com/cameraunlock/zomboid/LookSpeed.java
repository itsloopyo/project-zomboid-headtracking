package com.cameraunlock.zomboid;

/**
 * How fast the mouse and a controller's aiming stick turn the first and third person view
 * unzoomed: the mod's own speed times MouseLookSpeed or StickLookSpeed in CameraUnlock.ini. The
 * head's turn is the tracker's and never goes by either. Set by the thread that watches the file
 * or by the options screen, and read by the logic thread, which turns the view (FirstPerson).
 * Zoomed, the view turns less, to move the picture as far.
 */
final class LookSpeed {
    /** The rows' default and range in the config table (Config). */
    static final float DEFAULT = 1f, LEAST = 0.1f, MOST = 3f;

    private static final float RADIANS_PER_PIXEL = 0.0025f;
    /** A stick pushed right over, in radians a second: across, and up or down. */
    private static final float STICK_ACROSS = (float) Math.toRadians(220), STICK_UP = (float) Math.toRadians(140);

    private static volatile float mouse = DEFAULT, stick = DEFAULT;

    private LookSpeed() {}

    /** As cameraunlock-core read it, which holds it to the row's range. */
    static void setMouse(float speed) {
        mouse = speed;
    }

    static void setStick(float speed) {
        stick = speed;
    }

    static float mouse() {
        return mouse;
    }

    static float stick() {
        return stick;
    }

    static float radiansPerPixel() {
        return RADIANS_PER_PIXEL * mouse;
    }

    static float stickAcross() {
        return STICK_ACROSS * stick;
    }

    static float stickUp() {
        return STICK_UP * stick;
    }
}

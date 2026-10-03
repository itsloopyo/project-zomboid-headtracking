package com.cameraunlock.zomboid;

/**
 * The first-person view's field of view. The game has none of its own: its
 * view is a flat projection with a zoom, so this number is the mod's, and the
 * frame's projection, the reticle and the near plane all take it from here.
 *
 * It is the angle from the bottom of the picture to the top, so a wider screen
 * shows more to the sides and the same amount up and down.
 *
 * Read and written on the game's logic thread. A frame carries the value it
 * was built with to the render thread.
 */
final class FieldOfView {
    static final String PROPERTY = "cameraunlock.zomboid.fov";
    static final float DEFAULT_DEGREES = 65f;
    static final float MIN_DEGREES = 40f;
    static final float MAX_DEGREES = 110f;

    private static float degrees = parse(System.getProperty(PROPERTY));

    private FieldOfView() {}

    /** @param text degrees from bottom to top, or null for the default */
    static float parse(String text) {
        if (text == null) {
            return DEFAULT_DEGREES;
        }
        float value;
        try {
            value = Float.parseFloat(text.strip());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(PROPERTY + " is \"" + text + "\", which is not a number of degrees");
        }
        return checked(value);
    }

    private static float checked(float value) {
        if (!(value >= MIN_DEGREES && value <= MAX_DEGREES)) {
            throw new IllegalArgumentException(PROPERTY + " is " + value + " degrees, outside " + MIN_DEGREES + " to "
                    + MAX_DEGREES + " (measured from the bottom of the picture to the top)");
        }
        return value;
    }

    static float degrees() {
        return degrees;
    }

    /** Takes effect on the next frame built. */
    static void set(float newDegrees) {
        degrees = checked(newDegrees);
    }

    /** The tangent of half the angle: how far the top of the picture is from its centre, one unit ahead of the eye. */
    static float tanHalfVertical() {
        return (float) Math.tan(Math.toRadians(degrees) / 2.0);
    }
}

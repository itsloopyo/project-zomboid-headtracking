package com.cameraunlock.zomboid;

/**
 * The first-person view's field of view. The game has none of its own: its
 * view is a flat projection with a zoom, so this number is the mod's, set by
 * FieldOfView in CameraUnlock.ini, and the frame's projection, the reticle and
 * the near plane all take it from here.
 *
 * It is the angle from the bottom of the picture to the top, so a wider screen
 * shows more to the sides and the same amount up and down.
 *
 * Aiming a firearm narrows it. The angle the player set stays as it is, and a
 * zoom, the tangent drawn with over the tangent set, eases between 1 and the
 * aimed ratio.
 *
 * The zoom is read and written on the game's logic thread, and the angle is
 * set by the thread that watches CameraUnlock.ini. A frame carries the values
 * it was built with to the render thread.
 */
final class FieldOfView {
    /** The row's default in the config table (Config). */
    static final float DEFAULT_DEGREES = 65f;

    /** The tangent of half the angle with a firearm aimed, over the tangent the player set: 50 degrees at the default 65. */
    static final float AIMED_ZOOM = 0.73f;
    /** How long the zoom takes to cover all but a third of what is left to go. */
    static final float ZOOM_SECONDS = 0.07f;
    /** Nearer its target than this the zoom is put on it, so that unzoomed is exactly 1. */
    private static final float ZOOM_SETTLED = 1e-4f;

    private static volatile float degrees = DEFAULT_DEGREES;
    private static float zoom = 1f;

    private FieldOfView() {}

    static float degrees() {
        return degrees;
    }

    /**
     * Takes effect on the next frame built.
     *
     * @param newDegrees as cameraunlock-core read it, which holds it to the row's range
     */
    static void set(float newDegrees) {
        degrees = newDegrees;
    }

    /** Moves the zoom towards aimed or unaimed over a frame of the given length. */
    static void ease(boolean aimed, float seconds) {
        float target = aimed ? AIMED_ZOOM : 1f;
        zoom = target + (zoom - target) * (float) Math.exp(-seconds / ZOOM_SECONDS);
        if (Math.abs(zoom - target) < ZOOM_SETTLED) {
            zoom = target;
        }
    }

    /** Unzoomed at once, for when no picture is being drawn to ease in. */
    static void rest() {
        zoom = 1f;
    }

    /** The tangent the picture is drawn with over the tangent the player set: 1 unzoomed. */
    static float zoom() {
        return zoom;
    }

    /** The angle the picture is drawn with this frame, in degrees. */
    static float drawnDegrees() {
        return (float) Math.toDegrees(2.0 * Math.atan(tanHalfVertical()));
    }

    /** The tangent of half the angle the player set. */
    static float setTanHalfVertical() {
        return (float) Math.tan(Math.toRadians(degrees) / 2.0);
    }

    /**
     * The tangent of half the angle the picture is drawn with: how far the top of the picture
     * is from its centre, one unit ahead of the eye.
     */
    static float tanHalfVertical() {
        return setTanHalfVertical() * zoom;
    }
}

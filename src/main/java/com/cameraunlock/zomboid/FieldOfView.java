package com.cameraunlock.zomboid;

/**
 * The first-person view's field of view. The game has none of its own: its
 * view is a flat projection with a zoom, so this number is the mod's, set by
 * FieldOfView in CameraUnlock.ini, and the frame's projection, the reticle and
 * the near plane all take it from here. The camera behind the character has its
 * own, ThirdPersonFieldOfView, narrower by default: it already stands back from
 * the scene, and wide it shrinks the character and bends the edges as it swings.
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
    static final float DEFAULT_DEGREES = 70f;
    /** ThirdPersonFieldOfView's default. */
    static final float DEFAULT_BEHIND_DEGREES = 60f;

    /** The tangent of half the angle with a firearm aimed, over the tangent the player set: 54 degrees at the default 70. */
    static final float AIMED_ZOOM = 0.73f;
    /** How long the zoom takes to cover all but a third of what is left to go. */
    static final float ZOOM_SECONDS = 0.07f;
    /** Nearer its target than this the zoom is put on it, so that unzoomed is exactly 1. */
    private static final float ZOOM_SETTLED = 1e-4f;

    private static volatile float degrees = DEFAULT_DEGREES;
    private static volatile float behindDegrees = DEFAULT_BEHIND_DEGREES;
    private float zoom = 1f;


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

    static float behindDegrees() {
        return behindDegrees;
    }

    /** As set, for the camera behind the character. */
    static void setBehind(float newDegrees) {
        behindDegrees = newDegrees;
    }

    /**
     * What the tangents of a camera a share of the way out from the eye to its place behind the
     * character are scaled by: 1 at the eye, the third person tangent over the first person one
     * behind. The angle eases with the camera, so it never jumps as the camera comes and goes.
     */
    static float behindScale(float share) {
        float behind = (float) Math.tan(Math.toRadians(behindDegrees) / 2.0);
        return 1f + (behind / setTanHalfVertical() - 1f) * share;
    }

    /** Moves the zoom towards aimed or unaimed over a frame of the given length. */
    void ease(boolean aimed, float seconds) {
        float target = aimed ? AIMED_ZOOM : 1f;
        zoom = target + (zoom - target) * (float) Math.exp(-seconds / ZOOM_SECONDS);
        if (Math.abs(zoom - target) < ZOOM_SETTLED) {
            zoom = target;
        }
    }

    /** Unzoomed at once, for when no picture is being drawn to ease in. */
    void rest() {
        zoom = 1f;
    }

    /** The tangent the picture is drawn with over the tangent the player set: 1 unzoomed. */
    float zoom() {
        return zoom;
    }

    /** The angle the picture is drawn with this frame, in degrees. */
    float drawnDegrees() {
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
    float tanHalfVertical() {
        return setTanHalfVertical() * zoom;
    }
}

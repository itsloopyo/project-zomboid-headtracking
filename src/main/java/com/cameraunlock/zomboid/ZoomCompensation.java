package com.cameraunlock.zomboid;

/**
 * Keeps a head movement the same size on screen when the field of view
 * narrows. A narrow field of view magnifies everything in the picture, the
 * head's turn included, so the pose is scaled to move the picture as far as it
 * would at the field of view the player set.
 *
 * Yaw, pitch and a lean across the view move the picture across the frame and
 * are scaled. Roll turns the picture about its centre by the same angle at any
 * field of view, and a lean along the view only brings things nearer, so
 * neither is.
 */
final class ZoomCompensation {
    private ZoomCompensation() {}

    /**
     * The factor to scale by: 1 when nothing is zoomed, smaller as the view narrows.
     * Both are the tangent of half the angle, measured the same way up the picture.
     */
    static float factor(float tanHalfNow, float tanHalfBase) {
        return tanHalfNow / tanHalfBase;
    }

    /**
     * An angle in radians, rescaled to move the picture as far as it did
     * unzoomed. A point's place on screen goes as the tangent of its angle
     * over the tangent of half the field of view, so it is the tangent that is
     * scaled. Taken from the sine and cosine, because the arctangent of a tangent
     * comes back a half turn out for a head turned past a right angle.
     */
    static float scaleAngle(float radians, float factor) {
        return (float) Math.atan2(Math.sin(radians) * factor, Math.cos(radians));
    }

    /**
     * Scales the part of a lean that lies across the view and leaves the part
     * along it.
     *
     * @param lean three components, rewritten in place
     * @param axisX the way the view looks, unit length, in the lean's own axes
     */
    static void scaleLean(float[] lean, float axisX, float axisY, float axisUp, float factor) {
        float along = lean[0] * axisX + lean[1] * axisY + lean[2] * axisUp;
        lean[0] = axisX * along + (lean[0] - axisX * along) * factor;
        lean[1] = axisY * along + (lean[1] - axisY * along) * factor;
        lean[2] = axisUp * along + (lean[2] - axisUp * along) * factor;
    }
}

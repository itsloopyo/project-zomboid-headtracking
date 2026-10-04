package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * Splits a lean between what moves the view alone and what moves the weapon and the shot with
 * it, as the sights come up and go down.
 *
 * The part of the lean along the aim keeps the eye on the sight line, so the view always has
 * it: leaning in brings the sights nearer. The part across the aim goes over to the weapon as
 * the sights come up, unless the mode is free look, where the view keeps it all. The two
 * shares add up to the lean that was handed in, but for what the forward stop holds back, so
 * the eye is in the same place whichever has it.
 *
 * The lean goes in already held off the walls: the weapon's share carries the shot, and a
 * share that had not been would shoot through one.
 *
 * The same model as cameraunlock-core's LeanHandover, which exists in C++ and C# only.
 */
final class LeanHandover {
    /** Moves the view and nothing else. */
    final float[] camera = new float[3];
    /** Moves the weapon and where the shot starts with the eye. */
    final float[] rig = new float[3];

    private final AdsFade fade = new AdsFade();
    /** Follows the sights alone, free look or not, for the forward stop. */
    private final AdsFade sightsFade = new AdsFade();
    private final float[] aim = new float[3];
    private float forwardStop = Float.POSITIVE_INFINITY;

    /** How far along the aim the lean may take the eye while the sights are up, short of the rear sight. */
    void setForwardStop(float stop) {
        forwardStop = stop;
    }

    /**
     * Once a frame the lean is applied.
     *
     * @param lean         the whole lean, three components
     * @param aimX         the way the aim points, unit length, in the lean's own axes
     * @param aiming       whether the sights are up this frame, polled and not latched
     * @param freeLook     whether the view keeps the lean through the aim
     * @param rigAvailable false where the weapon cannot be moved: its share then eases out instead
     */
    void update(float[] lean, float aimX, float aimY, float aimUp, boolean aiming, boolean freeLook, boolean rigAvailable,
            long nowMillis) {
        float cameraShare = fade.update(aiming && !freeLook, nowMillis);
        float sightsUp = 1f - sightsFade.update(aiming, nowMillis);
        float along = lean[0] * aimX + lean[1] * aimY + lean[2] * aimUp;
        float kept = along > forwardStop ? along + (forwardStop - along) * sightsUp : along;
        float rigShare = rigAvailable ? 1f - cameraShare : 0f;
        aim[0] = aimX;
        aim[1] = aimY;
        aim[2] = aimUp;
        for (int i = 0; i < 3; i++) {
            float lateral = lean[i] - aim[i] * along;
            camera[i] = aim[i] * kept + lateral * cameraShare;
            rig[i] = lateral * rigShare;
        }
    }

    /** Every way the lean stops comes here instead of to update. The next lean starts at the hip. */
    void stop() {
        Arrays.fill(camera, 0f);
        Arrays.fill(rig, 0f);
        fade.reset();
        sightsFade.reset();
    }
}

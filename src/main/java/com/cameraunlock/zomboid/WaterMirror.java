package com.cameraunlock.zomboid;

/**
 * What water shows of the world above it, worked out so that a point on the water shows the
 * same thing wherever the head points.
 *
 * The world is drawn a second time from the eye's image under the water. A point Q above the
 * water, drawn from that image, falls on the same place in the picture as Q's own image under
 * the water would fall seen from the eye, and that is the place where the water between the eye
 * and that image is drawn. So the water at a pixel shows what the second picture has at that
 * same pixel, and nothing of it depends on what else the first picture happens to hold.
 *
 * Water lies on the country's slow rise and fall (Terrain.waterLevel), which over the width of
 * a river is a plane to within a few hundredths of a tile: the plane that touches it under
 * the eye is the mirror.
 */
final class WaterMirror {
    /**
     * How much wider than the eye's picture the second one is drawn, each way. A ripple throws
     * the eye's line to one side of where level water would, and at the edge of the picture
     * that is outside it: the margin is what is there to be shown.
     */
    static final float MARGIN = 1.2f;

    private WaterMirror() {}

    /**
     * How rough the wind makes water, as a multiple of what a light breeze does: push is how
     * hard the wind pushes on what it blows (Wind.push), a tenth in a calm and two in a gale.
     * Water is never glass, and a gale does not stand it on end.
     */
    static float ruffle(float push) {
        return Math.clamp(0.55f + 1.6f * push, 0.55f, 2.4f);
    }

    /**
     * The plane that touches the water's surface under (x, y): the unit vector square to it,
     * pointing up, and how far along that vector the plane is from the map's corner.
     */
    static void plane(Terrain terrain, float x, float y, float[] out) {
        float slopeX = (terrain.waterLevel(x + 1f, y) - terrain.waterLevel(x - 1f, y)) / 2f;
        float slopeY = (terrain.waterLevel(x, y + 1f) - terrain.waterLevel(x, y - 1f)) / 2f;
        float length = (float) Math.sqrt(slopeX * slopeX + slopeY * slopeY + 1f);
        out[0] = -slopeX / length;
        out[1] = -slopeY / length;
        out[2] = 1f / length;
        out[3] = out[0] * x + out[1] * y + out[2] * terrain.waterLevel(x, y);
    }

    /** How far above the plane a point is, in tiles. */
    static float above(float[] plane, float x, float y, float up) {
        return plane[0] * x + plane[1] * y + plane[2] * up - plane[3];
    }

    /**
     * The eye's image in the plane: as far under it as the eye is over it, with its three axes
     * turned over with it. Its axes are a mirror image of the eye's, so the picture drawn from
     * it is not turned over a second time when it is shown.
     */
    static void mirror(ViewCamera.Pose eye, float[] plane, ViewCamera.Pose out) {
        float over = 2f * above(plane, eye.eyeX, eye.eyeY, eye.eyeUp);
        out.eyeX = eye.eyeX - over * plane[0];
        out.eyeY = eye.eyeY - over * plane[1];
        out.eyeUp = eye.eyeUp - over * plane[2];
        float right = 2f * (plane[0] * eye.rightX + plane[1] * eye.rightY + plane[2] * eye.rightUp);
        out.rightX = eye.rightX - right * plane[0];
        out.rightY = eye.rightY - right * plane[1];
        out.rightUp = eye.rightUp - right * plane[2];
        float up = 2f * (plane[0] * eye.upX + plane[1] * eye.upY + plane[2] * eye.upUp);
        out.upX = eye.upX - up * plane[0];
        out.upY = eye.upY - up * plane[1];
        out.upUp = eye.upUp - up * plane[2];
        float forward = 2f * (plane[0] * eye.forwardX + plane[1] * eye.forwardY + plane[2] * eye.forwardUp);
        out.forwardX = eye.forwardX - forward * plane[0];
        out.forwardY = eye.forwardY - forward * plane[1];
        out.forwardUp = eye.forwardUp - forward * plane[2];
    }
}

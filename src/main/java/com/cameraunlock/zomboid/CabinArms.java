package com.cameraunlock.zomboid;

/**
 * The driver's arms on the steering wheel of the mod's own interior: the mod's arm model with each
 * hand closed round the rim, at ten and two with the wheel straight, going round with it as it is
 * turned, from shoulders that stay with the seat when the head leans.
 *
 * Places are worked out in the interior's own frame (CabinModel) and given to the arm solver in the
 * view's axes, which is what the arms are drawn in.
 *
 * All of it is the render thread's.
 */
final class CabinArms {
    /** How far round the rim from its top each hand holds it, in radians: ten and two. */
    static final float HAND_PLACE = (float) Math.toRadians(60);
    /** The shoulders and where each forearm seems to come from, in the arm model's metres from the eye at rest: ahead, left, up. */
    private static final float[] SHOULDER = {-0.07f, 0.19f, -0.22f}, FROM = {-0.02f, 0.40f, -0.58f};

    final float[][] skins = new float[2][24 * 16], said = new float[2][ArmPose.SAYS];
    private final float[] turn = new float[9];

    /**
     * Poses both arms on the wheel. False where either has no way to reach it, and then neither is drawn.
     *
     * @param scale the arm model's metres to tiles
     */
    boolean place(ArmModel model, float scale, CabinDraw.Frame f, ViewCamera.Pose camera, float steer) {
        float[] w = f.fitted.wheel;
        float ax = w[3], ay = w[4], az = w[5];
        // The wheel's own up, square to its column, and its left.
        float ux = -az * ax, uy = -az * ay, uz = 1f - az * az;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        float lx = uy * az - uz * ay, ly = uz * ax - ux * az, lz = ux * ay - uy * ax;
        CabinDraw.about(ax, ay, az, steer, turn);
        float[] weights = new float[model.poses.length];
        weights[model.pose("handle")] = 1f;
        float radius = w[6];
        for (int side = 0; side < 2; side++) {
            // The left hand, side 1, holds the rim on the driver's left.
            float sign = side == 1 ? 1f : -1f;
            float a = sign * HAND_PLACE;
            float s = (float) Math.sin(a), c = (float) Math.cos(a);
            float[] grip = {radius * (s * lx + c * ux), radius * (s * ly + c * uy), radius * (s * lz + c * uz)};
            // Along the rim towards its top, which the thumb lies along.
            float[] thumb = {-sign * (c * lx - s * ux), -sign * (c * ly - s * uy), -sign * (c * lz - s * uz)};
            turned(grip);
            turned(thumb);
            float[] centre = view(f, camera, w[0] + grip[0], w[1] + grip[1], w[2] + grip[2], true);
            float[] across = view(f, camera, thumb[0], thumb[1], thumb[2], false);
            float[] shoulder = view(f, camera, SHOULDER[0] * scale, sign * SHOULDER[1] * scale, SHOULDER[2] * scale, true);
            float[] from = view(f, camera, FROM[0] * scale, sign * FROM[1] * scale, FROM[2] * scale, true);
            float[] out = {across[1], -across[0], 0f};
            if (!ArmPose.solve(model, scale, weights, centre, across, out, shoulder, from, side == 1, true, skins[side], said[side])) {
                return false;
            }
        }
        return true;
    }

    private void turned(float[] v) {
        float x = v[0], y = v[1], z = v[2];
        v[0] = turn[0] * x + turn[3] * y + turn[6] * z;
        v[1] = turn[1] * x + turn[4] * y + turn[7] * z;
        v[2] = turn[2] * x + turn[5] * y + turn[8] * z;
    }

    /** A place (or a way, without moving it) in the interior's frame, in the view's axes from the eye: right, up, back. */
    private static float[] view(CabinDraw.Frame f, ViewCamera.Pose camera, float x, float y, float z, boolean place) {
        float[] m = f.model;
        float rx = m[0] * x + m[4] * y + m[8] * z + (place ? m[12] : 0f);
        float ry = m[1] * x + m[5] * y + m[9] * z + (place ? m[13] : 0f);
        float rz = m[2] * x + m[6] * y + m[10] * z + (place ? m[14] : 0f);
        return new float[] {rx * camera.rightX + ry * camera.rightY + rz * camera.rightUp, rx * camera.upX + ry * camera.upY + rz * camera.upUp,
            -(rx * camera.forwardX + ry * camera.forwardY + rz * camera.forwardUp)};
    }
}

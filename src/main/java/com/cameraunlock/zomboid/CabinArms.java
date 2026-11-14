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
    /**
     * The shoulders and where each forearm seems to come from, in the arm model's metres from the
     * eye at rest: ahead, left, up. Where the forearm comes from is wide of the shoulder: an
     * elbow in the plane through the eye, the shoulder and the wrist is seen from the eye as a
     * straight arm however far it bends, and one out to the side of that plane shows its bend.
     */
    private static final float[] SHOULDER = {0f, 0.20f, -0.28f}, FROM = {-0.02f, 0.60f, -0.55f};
    /**
     * How much of the rim's way towards where the forearm comes from is taken out of the line
     * across the fist. Square to the rim, a fist at ten or two can only have its forearm come from
     * straight behind, the elbow level with the wrist and the arm held out straight; a driver's
     * rim runs across the palm on a slant, the wrist turned in, and the elbow hangs below and
     * outside the wrist. Measured with the arm model on the mod's own wheel with its column leaning
     * 10 to 60 degrees from level: at 0.9 the elbow is 0.033 to 0.076 tiles below the wrist, 0.079
     * to 0.104 outside it and 0.044 to 0.055 off the plane through the eye, the shoulder and the
     * wrist, bent 61 to 87 degrees.
     */
    private static final float GRIP_SLANT = 0.9f;

    final float[][] skins = new float[2][24 * 16], said = new float[2][ArmPose.SAYS];
    private final float[] turn = new float[9];

    /**
     * Poses both arms on the wheel. False where either has no way to reach it, and then neither is drawn.
     *
     * @param scale the arm model's metres to tiles
     * @param m     from the interior's frame to the world less the eye (CabinDraw.Frame.model)
     * @param w     the wheel in the interior's frame: its middle, the way its column runs away from the driver, and how far out from its middle a hand holds its rim
     */
    boolean place(ArmModel model, float scale, float[] m, float[] w, ViewCamera.Pose camera, float steer) {
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
            float[] back = {FROM[0] * scale - w[0] - grip[0], sign * FROM[1] * scale - w[1] - grip[1], FROM[2] * scale - w[2] - grip[2]};
            float along = (thumb[0] * back[0] + thumb[1] * back[1] + thumb[2] * back[2]) / (back[0] * back[0] + back[1] * back[1] + back[2] * back[2]);
            for (int axis = 0; axis < 3; axis++) {
                thumb[axis] -= GRIP_SLANT * along * back[axis];
            }
            float[] centre = view(m, camera, w[0] + grip[0], w[1] + grip[1], w[2] + grip[2], true);
            float[] across = view(m, camera, thumb[0], thumb[1], thumb[2], false);
            float[] shoulder = view(m, camera, SHOULDER[0] * scale, sign * SHOULDER[1] * scale, SHOULDER[2] * scale, true);
            float[] from = view(m, camera, FROM[0] * scale, sign * FROM[1] * scale, FROM[2] * scale, true);
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
    private static float[] view(float[] m, ViewCamera.Pose camera, float x, float y, float z, boolean place) {
        float rx = m[0] * x + m[4] * y + m[8] * z + (place ? m[12] : 0f);
        float ry = m[1] * x + m[5] * y + m[9] * z + (place ? m[13] : 0f);
        float rz = m[2] * x + m[6] * y + m[10] * z + (place ? m[14] : 0f);
        return new float[] {rx * camera.rightX + ry * camera.rightY + rz * camera.rightUp, rx * camera.upX + ry * camera.upY + rz * camera.upUp,
            -(rx * camera.forwardX + ry * camera.forwardY + rz * camera.forwardUp)};
    }
}

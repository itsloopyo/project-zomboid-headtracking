package com.cameraunlock.zomboid;

/**
 * The eye handed to the character's head while the game's own animation has the body. Knocked
 * over, lying on the ground, getting up from it and dying, the view is the head's, from where
 * the head is and the way it faces, with the player's own head turn on top, and the mouse turns
 * nothing. Grabbed, bitten or shoving a zombie off, the character stays on its feet and the
 * view stays the mouse's: the game's animation has the body, and none of the view. It is
 * handed over and back on a short ease, so neither way is a cut. Once the game has made the
 * dead character a body on the ground the head's last view is held: the game poses that body
 * no more.
 *
 * Logic thread.
 */
final class BodyLed {
    /** How quickly the head takes the eye, and gives it back: time constants, seconds. */
    static final float IN_SECONDS = 0.06f, OUT_SECONDS = 0.12f;
    /** A share this near either end is taken as that end. */
    private static final float WHOLE = 0.002f;
    /**
     * The way the face looks and the way the crown points, each in Bip01_Head's own axes. Measured
     * in the game's idle stance: the bone's +z is the body's forward tipped 13.6 degrees down and
     * its +x is up tipped as far forward. Those are turned back by that much, so a head as it
     * idles looks level, as the mouse's view does when it takes the eye back.
     */
    private static final float IDLE_DOWN = (float) Math.toRadians(13.6);
    static final float[] FORWARD_IN_HEAD = {(float) Math.sin(IDLE_DOWN), 0f, (float) Math.cos(IDLE_DOWN)},
            UP_IN_HEAD = {(float) Math.cos(IDLE_DOWN), 0f, (float) -Math.sin(IDLE_DOWN)};

    /** 0 while the view is the mouse's, 1 while it is the head's. */
    private float share;
    private boolean leads, whole, holding;
    /** The head's view this frame, or as last seen once held. */
    final ViewCamera.Pose head = new ViewCamera.Pose();
    /** The view as it was drawn on the last frame before the hold, kept for as long as it lasts. */
    final ViewCamera.Pose heldClean = new ViewCamera.Pose(), heldTracked = new ViewCamera.Pose();

    /**
     * Carries the hand-over on by a frame.
     *
     * @param leads   whether the game's animation has the body
     * @param whole   whether it has the view as well: not for a hit or a bite the character stays on its feet through
     * @param holding whether the game has made the body it poses no more
     */
    void frame(boolean leads, boolean whole, boolean holding, float seconds) {
        this.leads = leads;
        this.whole = whole && leads;
        this.holding = holding && this.whole;
        float to = this.whole ? 1f : 0f;
        share = ViewModelMath.ease(share, to, seconds, to > share ? IN_SECONDS : OUT_SECONDS);
        if (Math.abs(share - to) < WHOLE) {
            share = to;
        }
    }

    /** Back to the mouse's view at once: a new character, or the view left. */
    void reset() {
        share = 0f;
        leads = false;
        whole = false;
        holding = false;
    }

    /** Whether the game's animation has the body this frame. */
    boolean leads() {
        return leads;
    }

    /** Whether the whole view is the head's, and the mouse turns nothing. */
    boolean whole() {
        return whole;
    }

    /** Whether the head's last view is held: the body is posed no more. */
    boolean holding() {
        return holding;
    }

    /** How much of the view is the head's, 0 to 1. */
    float share() {
        return share;
    }

    /**
     * The head's view from where it is in the world.
     *
     * @param turn      Bip01_Head's turn in the model, its columns its own x, y and z axes, column after column
     * @param bodyAngle the angle the game draws the body at, AnimationPlayer.getRenderedAngle
     * @param footX     where the body's feet are along the map, tiles
     * @param footY     the same, across the map
     * @param footUp    the height they stand at, tiles
     * @param jointX    where the head's joint is from the body's feet, along the map, tiles
     * @param jointY    the same, across the map
     * @param jointUp   the same, upwards
     */
    static void look(float[] turn, float bodyAngle, float footX, float footY, float footUp, float jointX, float jointY, float jointUp,
            ViewCamera.Pose out) {
        float fx = column(turn, FORWARD_IN_HEAD, 0), fy = column(turn, FORWARD_IN_HEAD, 1), fz = column(turn, FORWARD_IN_HEAD, 2);
        float ux = column(turn, UP_IN_HEAD, 0), uy = column(turn, UP_IN_HEAD, 1), uz = column(turn, UP_IN_HEAD, 2);
        // Model y is up, and model z is reflected when placed in the game's world: as ViewCamera.eyeOffset places the joint.
        float c = (float) Math.cos(bodyAngle + Math.PI), s = (float) Math.sin(bodyAngle + Math.PI);
        float forwardX = c * fx + s * fz, forwardY = s * fx - c * fz, forwardUp = fy;
        float upX = c * ux + s * uz, upY = s * ux - c * uz, upUp = uy;
        out.forwardX = forwardX;
        out.forwardY = forwardY;
        out.forwardUp = forwardUp;
        out.upX = upX;
        out.upY = upY;
        out.upUp = upUp;
        orthonormal(out);
        out.eyeX = footX + jointX + out.forwardX * ViewCamera.EYES_AHEAD + out.upX * ViewCamera.EYES_ABOVE_JOINT;
        out.eyeY = footY + jointY + out.forwardY * ViewCamera.EYES_AHEAD + out.upY * ViewCamera.EYES_ABOVE_JOINT;
        out.eyeUp = footUp + jointUp + out.forwardUp * ViewCamera.EYES_AHEAD + out.upUp * ViewCamera.EYES_ABOVE_JOINT;
    }

    /** One part of a direction given in a bone's own axes, in the axes its turn is given in. */
    private static float column(float[] turn, float[] in, int row) {
        return turn[row] * in[0] + turn[3 + row] * in[1] + turn[6 + row] * in[2];
    }

    /** Makes a pose's forward of length one, its up square to that, and its right square to both: right = up x forward, as ViewCamera.clean has them. */
    static void orthonormal(ViewCamera.Pose pose) {
        float f = (float) Math.sqrt(pose.forwardX * pose.forwardX + pose.forwardY * pose.forwardY + pose.forwardUp * pose.forwardUp);
        pose.forwardX /= f;
        pose.forwardY /= f;
        pose.forwardUp /= f;
        float along = pose.upX * pose.forwardX + pose.upY * pose.forwardY + pose.upUp * pose.forwardUp;
        float ux = pose.upX - along * pose.forwardX, uy = pose.upY - along * pose.forwardY, uu = pose.upUp - along * pose.forwardUp;
        float u = (float) Math.sqrt(ux * ux + uy * uy + uu * uu);
        pose.upX = ux / u;
        pose.upY = uy / u;
        pose.upUp = uu / u;
        pose.rightX = pose.upY * pose.forwardUp - pose.upUp * pose.forwardY;
        pose.rightY = pose.upUp * pose.forwardX - pose.upX * pose.forwardUp;
        pose.rightUp = pose.upX * pose.forwardY - pose.upY * pose.forwardX;
    }

    private final float[] fromTurn = new float[4], toTurn = new float[4];

    /** A view share of the way from one to another: the eye along the line between, the turn the shortest way round. */
    void between(ViewCamera.Pose from, ViewCamera.Pose to, float share, ViewCamera.Pose out) {
        turnOf(from, fromTurn);
        turnOf(to, toTurn);
        float dot = fromTurn[0] * toTurn[0] + fromTurn[1] * toTurn[1] + fromTurn[2] * toTurn[2] + fromTurn[3] * toTurn[3];
        float sign = dot < 0f ? -1f : 1f;
        dot = Math.abs(dot);
        float a, b;
        if (dot > 0.9995f) {
            a = 1f - share;
            b = share;
        } else {
            float angle = (float) Math.acos(dot), sin = (float) Math.sin(angle);
            a = (float) Math.sin((1f - share) * angle) / sin;
            b = (float) Math.sin(share * angle) / sin;
        }
        float x = a * fromTurn[0] + b * sign * toTurn[0], y = a * fromTurn[1] + b * sign * toTurn[1],
                z = a * fromTurn[2] + b * sign * toTurn[2], w = a * fromTurn[3] + b * sign * toTurn[3];
        float n = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        x /= n;
        y /= n;
        z /= n;
        w /= n;
        // The matrix whose columns are right, up and forward.
        out.rightX = 1f - 2f * (y * y + z * z);
        out.rightY = 2f * (x * y + w * z);
        out.rightUp = 2f * (x * z - w * y);
        out.upX = 2f * (x * y - w * z);
        out.upY = 1f - 2f * (x * x + z * z);
        out.upUp = 2f * (y * z + w * x);
        out.forwardX = 2f * (x * z + w * y);
        out.forwardY = 2f * (y * z - w * x);
        out.forwardUp = 1f - 2f * (x * x + y * y);
        out.eyeX = from.eyeX + (to.eyeX - from.eyeX) * share;
        out.eyeY = from.eyeY + (to.eyeY - from.eyeY) * share;
        out.eyeUp = from.eyeUp + (to.eyeUp - from.eyeUp) * share;
    }

    /** A view's turn as a quaternion (x, y, z, w), of the matrix whose columns are its right, up and forward. */
    private static void turnOf(ViewCamera.Pose pose, float[] out) {
        float m00 = pose.rightX, m01 = pose.upX, m02 = pose.forwardX;
        float m10 = pose.rightY, m11 = pose.upY, m12 = pose.forwardY;
        float m20 = pose.rightUp, m21 = pose.upUp, m22 = pose.forwardUp;
        float trace = m00 + m11 + m22;
        if (trace > 0f) {
            float s = (float) Math.sqrt(trace + 1f) * 2f;
            out[3] = 0.25f * s;
            out[0] = (m21 - m12) / s;
            out[1] = (m02 - m20) / s;
            out[2] = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            float s = (float) Math.sqrt(1f + m00 - m11 - m22) * 2f;
            out[3] = (m21 - m12) / s;
            out[0] = 0.25f * s;
            out[1] = (m01 + m10) / s;
            out[2] = (m02 + m20) / s;
        } else if (m11 > m22) {
            float s = (float) Math.sqrt(1f + m11 - m00 - m22) * 2f;
            out[3] = (m02 - m20) / s;
            out[0] = (m01 + m10) / s;
            out[1] = 0.25f * s;
            out[2] = (m12 + m21) / s;
        } else {
            float s = (float) Math.sqrt(1f + m22 - m00 - m11) * 2f;
            out[3] = (m10 - m01) / s;
            out[0] = (m02 + m20) / s;
            out[1] = (m12 + m21) / s;
            out[2] = 0.25f * s;
        }
    }

    /**
     * The turn from the level view facing yaw to a view, as rows of three in world axes: what
     * HeadTracking.view takes for what the viewer's frame has tipped by, so a lean goes the way
     * the head lies.
     */
    static void tipped(float yaw, ViewCamera.Pose to, float[] out) {
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        // The level view: right (-sy, cy, 0), up (0, 0, 1), forward (cy, sy, 0). Each of its axes goes to the view's.
        out[0] = to.rightX * -sy + to.forwardX * cy;
        out[1] = to.rightX * cy + to.forwardX * sy;
        out[2] = to.upX;
        out[3] = to.rightY * -sy + to.forwardY * cy;
        out[4] = to.rightY * cy + to.forwardY * sy;
        out[5] = to.upY;
        out[6] = to.rightUp * -sy + to.forwardUp * cy;
        out[7] = to.rightUp * cy + to.forwardUp * sy;
        out[8] = to.upUp;
    }
}

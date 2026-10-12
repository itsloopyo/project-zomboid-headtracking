package com.cameraunlock.zomboid;

/**
 * Where the hands are while the viewer, with no weapon in them, shoves or stamps: for a shove both
 * come up from under the picture, palms first, are pushed out into what is shoved and are brought
 * back; for a stamp they come up to the sides and brace as the foot goes down. A hand with
 * something else in it, a torch or a bag, goes the same way as a fist round what it holds: the
 * free hand does the shoving, and what is held goes out and back with the other.
 *
 * Each hand is given in the aim's axes (x to the right, y up, z towards the viewer) and in metres
 * from the eye, as nine numbers: the place just before its palm, the way out of its palm, and the
 * way its fingers run. The left hand is the right one in a mirror.
 */
final class BareHands {
    private BareHands() {}

    /** The ways something is hit with nothing in hand. */
    static final int SHOVED = 0, STAMPED = 1;
    /** The hand's own pose for each way, by its name in ArmModel, and a hand's with something in it. */
    static final String[] POSES = {"open", "handle"};
    static final String FULL_POSE = "handle";
    /**
     * A right hand with something in it faces in across the body with its fingers ahead, as a fist
     * is on a bat's handle: what it holds runs up through the fist.
     */
    private static final float[] FULL_PALM = {-1f, 0f, 0f}, FULL_FINGERS = {0f, -0.2f, -1f};
    /** How many numbers say where a hand is. */
    static final int SAYS = 9;

    private static final int REST = 0, WOUND = 1, LANDS = 2, THROUGH = 3, SPRUNG = 4;

    /** For each way: at rest under the picture, drawn back, landing, gone on past it, and sprung back off what it landed on. */
    private static final float[][][] KEYS = {
        {
            {0.22f, -0.62f, -0.16f, -1f, 0f, -0.2f, 0f, -0.3f, -1f},
            {0.18f, -0.24f, -0.26f, -0.25f, -0.5f, -1f, 0f, 1f, -0.5f},
            {0.16f, -0.14f, -0.46f, 0f, -0.6f, -1f, 0.1f, 1f, -0.6f},
            {0.15f, -0.15f, -0.50f, 0f, -0.6f, -1f, 0.1f, 1f, -0.6f},
            {0.17f, -0.17f, -0.38f, -0.1f, -0.5f, -1f, 0.05f, 1f, -0.5f},
        },
        {
            {0.22f, -0.62f, -0.16f, -1f, 0f, -0.2f, 0f, -0.3f, -1f},
            {0.25f, -0.19f, -0.34f, -0.3f, -1f, -0.2f, -0.15f, 0.1f, -1f},
            {0.27f, -0.14f, -0.37f, -0.3f, -1f, -0.1f, -0.15f, 0f, -1f},
            {0.27f, -0.14f, -0.37f, -0.3f, -1f, -0.1f, -0.15f, 0f, -1f},
            {0.26f, -0.16f, -0.36f, -0.3f, -1f, -0.15f, -0.15f, 0.05f, -1f},
        },
    };

    /**
     * Where the right hand is at a moment of a shove or a stamp.
     *
     * @param way     SHOVED or STAMPED
     * @param reached how far it is to landing: 0 as it starts, 1 as it lands
     * @param past    how far past landing: 0 until it lands, 1 when the hands are down again
     * @param struck  whether it landed on something
     * @param out     the answer, SAYS numbers
     */
    static void pose(int way, float reached, float past, boolean struck, float[] out) {
        float[][] keys = KEYS[way];
        if (!(past > 0f)) {
            float r = Math.clamp(reached, 0f, 1f);
            if (r < ViewModelMath.WINDS) {
                between(keys[REST], keys[WOUND], ViewModelMath.smooth(r / ViewModelMath.WINDS), out);
            } else {
                float t = (r - ViewModelMath.WINDS) / (1f - ViewModelMath.WINDS);
                between(keys[WOUND], keys[LANDS], t * t, out);
            }
            return;
        }
        float p = Math.min(past, 1f), turns = struck ? ViewModelMath.SPRINGS : ViewModelMath.FOLLOWS;
        float[] end = keys[struck ? SPRUNG : THROUGH];
        if (p < turns) {
            float left = 1f - p / turns;
            between(keys[LANDS], end, 1f - left * left, out);
        } else {
            between(end, keys[REST], ViewModelMath.smooth((p - turns) / (1f - turns)), out);
        }
    }

    /**
     * Where one hand is at a moment of a shove or a stamp, as `pose` has the right, with something
     * in it or not.
     *
     * @param full whether the hand has something in it
     * @param left whether it is the left hand
     */
    static void hand(int way, float reached, float past, boolean struck, boolean full, boolean left, float[] out) {
        pose(way, reached, past, struck, out);
        if (full) {
            System.arraycopy(FULL_PALM, 0, out, 3, 3);
            System.arraycopy(FULL_FINGERS, 0, out, 6, 3);
        }
        if (left) {
            out[0] = -out[0];
            out[3] = -out[3];
            out[6] = -out[6];
        }
    }

    /** The name in ArmModel of the pose of a hand in a shove or a stamp, with something in it or not. */
    static String poseOf(int way, boolean full) {
        return full ? FULL_POSE : POSES[way];
    }

    private static void between(float[] from, float[] to, float share, float[] out) {
        for (int i = 0; i < SAYS; i++) {
            out[i] = from[i] + (to[i] - from[i]) * share;
        }
    }
}

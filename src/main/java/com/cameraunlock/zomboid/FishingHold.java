package com.cameraunlock.zomboid;

/**
 * How a fishing rod is held in first person while the game's fishing has it out over water, in
 * the hold ViewModelMath.attack takes: held out ahead with its tip up, swung back over the
 * shoulder and out to cast, its tip lifted while the reel is turned, lowered while line is let
 * out. The left hand is on the reel, and turns its handle while the line is reeled in.
 */
final class FishingHold {
    private FishingHold() {}

    /** What the game's fishing is doing, by its states: none, ready to cast, casting, waiting, reeling in, letting line out, landing the catch. */
    static final int NOT = 0, READY = 1, CAST = 2, WAITING = 3, REELING = 4, LETTING_OUT = 5, LANDING = 6;

    private static final float[] READY_HOLD = key(32, -12, -6, 0.15f, -0.14f, 0.40f),
            WAITING_HOLD = key(24, -10, -4, 0.15f, -0.15f, 0.42f),
            REELING_HOLD = key(42, -8, -4, 0.14f, -0.13f, 0.37f),
            LETTING_OUT_HOLD = key(12, -10, -4, 0.15f, -0.16f, 0.44f),
            LANDING_HOLD = key(58, -18, -10, 0.13f, -0.11f, 0.33f),
            DRAWN_BACK = key(100, 6, 6, 0.17f, -0.05f, 0.25f),
            CAST_OUT = key(4, -10, -4, 0.13f, -0.17f, 0.48f);
    /**
     * A cast: the rod is drawn back over the shoulder in the first of these, whipped out ahead
     * in the next, and brought up to wait in the last, in seconds. The game puts the bobber in
     * the water about 1.4 s after the cast starts.
     */
    static final float DRAWS_BACK = 0.35f, WHIPS_OUT = 0.18f, COMES_UP = 0.6f;
    /** The rod's tip nods this far, in radians, this many times a second, while the reel is turned. */
    static final float NODS = (float) Math.toRadians(2.5), NODS_A_SECOND = 2.5f;
    /** The reel's handle goes round this many times a second while the line is reeled in. */
    static final float TURNS_A_SECOND = 2f;

    private static float[] key(float pitch, float yaw, float roll, float x, float y, float ahead) {
        return new float[] {(float) Math.toRadians(pitch), (float) Math.toRadians(yaw), (float) Math.toRadians(roll), x, y, ahead, 0f};
    }

    /**
     * Where the rod is held, `since` seconds into a stage.
     *
     * @param stage one of the stages but NOT
     * @param out   the hold, as ViewModelMath.attack gives one
     */
    static void pose(int stage, float since, float[] out) {
        switch (stage) {
            case CAST -> {
                if (since < DRAWS_BACK) {
                    between(READY_HOLD, DRAWN_BACK, ViewModelMath.smooth(since / DRAWS_BACK), out);
                } else if (since < DRAWS_BACK + WHIPS_OUT) {
                    float t = (since - DRAWS_BACK) / WHIPS_OUT;
                    between(DRAWN_BACK, CAST_OUT, t * t * (3f - 2f * t), out);
                } else {
                    between(CAST_OUT, WAITING_HOLD, ViewModelMath.smooth(Math.min((since - DRAWS_BACK - WHIPS_OUT) / COMES_UP, 1f)), out);
                }
            }
            case WAITING -> System.arraycopy(WAITING_HOLD, 0, out, 0, ViewModelMath.HOLD);
            case REELING -> {
                System.arraycopy(REELING_HOLD, 0, out, 0, ViewModelMath.HOLD);
                out[0] += NODS * (float) Math.sin(2 * Math.PI * NODS_A_SECOND * since);
            }
            case LETTING_OUT -> System.arraycopy(LETTING_OUT_HOLD, 0, out, 0, ViewModelMath.HOLD);
            case LANDING -> System.arraycopy(LANDING_HOLD, 0, out, 0, ViewModelMath.HOLD);
            default -> System.arraycopy(READY_HOLD, 0, out, 0, ViewModelMath.HOLD);
        }
    }

    /** How far round the reel's handle has gone, in radians, after `seconds` more of a stage. */
    static float crank(float crank, int stage, float seconds) {
        return stage == REELING ? (float) ((crank + 2 * Math.PI * TURNS_A_SECOND * seconds) % (2 * Math.PI)) : crank;
    }

    private static void between(float[] from, float[] to, float share, float[] out) {
        for (int i = 0; i < ViewModelMath.HOLD; i++) {
            out[i] = from[i] + (to[i] - from[i]) * share;
        }
    }
}

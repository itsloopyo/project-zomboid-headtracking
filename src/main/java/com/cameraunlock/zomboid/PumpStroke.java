package com.cameraunlock.zomboid;

/**
 * How far back a pump gun's fore-end is, from how far through its animation of racking the
 * game is, frame by frame.
 *
 * The game has two animations of it, by whether the sights are up when it starts. A shot is
 * fired with them up, and the rack after it starts as the shot is fired: the hand waits out
 * the kick, draws the fore-end straight back, stops a moment with the bolt open and drives it
 * home before the game lets go of its animation at 0.80 of the way through. At its usual pace
 * (0.77 s) the pull takes 0.19 s, the shove 0.18 s, and the first quarter, while the hand
 * waits, is when the kick of the shot has mostly died away. Its rack from the hip, as after
 * loading, has no kick to wait out, and the stroke keeps to where the game's own hand makes
 * it: the pull over its first 30 percent and the shove from 37.5 to 75 percent.
 *
 * Should the game let go of its animation part way through, the stroke runs on at the pace
 * it had to the end, so the fore-end never jumps home.
 *
 * Logic thread.
 */
final class PumpStroke {
    /** Shares of the game's rack at which the pull starts and ends, and the shove starts and ends, with the sights up and from the hip. */
    static final float[] AIMED = {0.25f, 0.5f, 0.55f, 0.78f}, HIP = {0.02f, 0.3f, 0.375f, 0.75f};
    private static final int BACK_FROM = 0, BACK_TO = 1, FORWARD_FROM = 2, FORWARD_TO = 3;
    /** How far back the fore-end is when the bolt throws the spent shell out of the port: at the back of the pull. */
    static final float THROWN_AT = 0.95f;
    private static final float NONE = -1f;

    private float at = NONE, pace;
    private float[] phases = AIMED;
    /** How far back the fore-end is, 0 home to 1 right back. */
    float back;

    /** How far back the fore-end is a share of the way through the game's rack, of AIMED or HIP. */
    static float at(float rack, float[] phases) {
        return ViewModelMath.smooth((rack - phases[BACK_FROM]) / (phases[BACK_TO] - phases[BACK_FROM]))
                - ViewModelMath.smooth((rack - phases[FORWARD_FROM]) / (phases[FORWARD_TO] - phases[FORWARD_FROM]));
    }

    /** Whether the fore-end, going from one place to another, passed the place at which the shell is thrown out on its way back. */
    static boolean throwsShell(float before, float now) {
        return before < THROWN_AT && now >= THROWN_AT;
    }

    /**
     * Moves on by a frame.
     *
     * @param rack    how far through its animation of racking the game is, or less than nought while it plays none
     * @param aimed   whether that is its sights-up rack
     * @param seconds the length of the frame
     */
    void step(float rack, boolean aimed, float seconds) {
        if (rack >= 0f) {
            phases = aimed ? AIMED : HIP;
            if (at >= 0f && rack > at && seconds > 0f) {
                pace = (rack - at) / seconds;
            }
            at = rack;
        } else if (at >= 0f && at < phases[FORWARD_TO] && pace > 0f) {
            at = Math.min(at + pace * seconds, phases[FORWARD_TO]);
        } else {
            at = NONE;
            pace = 0f;
        }
        back = at < 0f ? 0f : at(at, phases);
    }

    void rest() {
        at = NONE;
        pace = 0f;
        back = 0f;
    }
}

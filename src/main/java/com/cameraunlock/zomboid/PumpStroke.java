package com.cameraunlock.zomboid;

/**
 * How far back a pump gun's fore-end is, from how far through its animation of racking the
 * game is, frame by frame.
 *
 * The game racks after every shot, and its animation of it starts as the shot is fired. The
 * hand waits out the kick, draws the fore-end straight back, stops a moment with the bolt
 * open, drives it home and holds still for what is left. With the game's sights-up rack at
 * its usual pace (0.77 s) the pull takes 0.19 s, the shove 0.23 s, and the first quarter
 * of it, while the hand waits, is when the kick of the shot has mostly died away.
 *
 * Should the game let go of its animation part way through, the stroke runs on at the pace
 * it had to the end, so the fore-end never jumps home.
 *
 * Logic thread.
 */
final class PumpStroke {
    /** Shares of the game's rack at which the pull starts and ends, and the shove starts and ends. */
    static final float BACK_FROM = 0.25f, BACK_TO = 0.5f, FORWARD_FROM = 0.55f, FORWARD_TO = 0.85f;
    /** How far back the fore-end is when the bolt throws the spent shell out of the port: at the back of the pull. */
    static final float THROWN_AT = 0.95f;
    private static final float NONE = -1f;

    private float at = NONE, pace;
    /** How far back the fore-end is, 0 home to 1 right back. */
    float back;

    /** How far back the fore-end is a share of the way through the game's rack. */
    static float at(float rack) {
        return ViewModelMath.smooth((rack - BACK_FROM) / (BACK_TO - BACK_FROM)) - ViewModelMath.smooth((rack - FORWARD_FROM) / (FORWARD_TO - FORWARD_FROM));
    }

    /** Whether the fore-end, going from one place to another, passed the place at which the shell is thrown out on its way back. */
    static boolean throwsShell(float before, float now) {
        return before < THROWN_AT && now >= THROWN_AT;
    }

    /**
     * Moves on by a frame.
     *
     * @param rack    how far through its animation of racking the game is, or less than nought while it plays none
     * @param seconds the length of the frame
     */
    void step(float rack, float seconds) {
        if (rack >= 0f) {
            if (at >= 0f && rack > at && seconds > 0f) {
                pace = (rack - at) / seconds;
            }
            at = rack;
        } else if (at >= 0f && at < FORWARD_TO && pace > 0f) {
            at = Math.min(at + pace * seconds, FORWARD_TO);
        } else {
            at = NONE;
            pace = 0f;
        }
        back = at < 0f ? 0f : at(at);
    }

    void rest() {
        at = NONE;
        pace = 0f;
        back = 0f;
    }
}

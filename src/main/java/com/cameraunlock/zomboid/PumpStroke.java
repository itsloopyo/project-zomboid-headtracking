package com.cameraunlock.zomboid;

/**
 * How far back a pump gun's fore-end is, from how far through its animation of racking the
 * game is, frame by frame.
 *
 * The game has two animations of it, by whether the sights are up when it starts. A shot is
 * fired with them up, and the rack after it starts as the shot is fired: the hand waits out
 * the kick, draws the fore-end straight back, stops a moment with the bolt open and drives it
 * home before the game lets go of its animation at 0.80 of the way through. At its usual pace
 * (0.77 s) the pull takes 0.19 s, the shove 0.16 s, and the first quarter, while the hand
 * waits, is when the kick of the shot has mostly died away. Its rack from the hip, as after
 * loading, has no kick to wait out, and the stroke keeps to where the game's own hand makes
 * it: the pull over its first 30 percent and the shove from 37.5 to 75 percent.
 *
 * Should the game let go of its animation in the shove, the stroke runs on to the end at the
 * pace that rack has at the game's usual speed, so the fore-end never jumps home.
 *
 * Logic thread.
 */
final class PumpStroke {
    /**
     * A rack of the game's: the shares of it at which the pull starts and ends, and the shove
     * starts and ends, and how long it takes at the game's usual speed, in seconds.
     */
    record Rack(float backFrom, float backTo, float forwardFrom, float forwardTo, float takes) {
        /** How far back the fore-end is a share of the way through it. */
        float at(float through) {
            return ViewModelMath.smooth((through - backFrom) / (backTo - backFrom)) - ViewModelMath.smooth((through - forwardFrom) / (forwardTo - forwardFrom));
        }
    }

    /** The game's racks with the sights up (Bob_AttackShotgun_AimRack) and from the hip (Bob_Reload_Shotgun_Rack, at a reload speed of 1). */
    static final Rack AIMED = new Rack(0.25f, 0.5f, 0.55f, 0.76f, 0.767f), HIP = new Rack(0.02f, 0.3f, 0.375f, 0.75f, 1.4f);
    /** How far back the fore-end is when the bolt throws the spent shell out of the port: at the back of the pull. */
    static final float THROWN_AT = 0.95f;
    /** What `step` is given while the game plays no rack. */
    static final float NO_RACK = -1f;

    private float at = NO_RACK;
    private Rack rack = AIMED;
    /** How far back the fore-end is, 0 home to 1 right back. */
    float back;

    /** Whether the fore-end, going from one place to another, passed the place at which the shell is thrown out on its way back. */
    static boolean throwsShell(float before, float now) {
        return before < THROWN_AT && now >= THROWN_AT;
    }

    /**
     * Moves on by a frame.
     *
     * @param through how far through its animation of racking the game is, or NO_RACK while it plays none
     * @param aimed   whether that is its sights-up rack
     * @param seconds the length of the frame
     */
    void step(float through, boolean aimed, float seconds) {
        if (through >= 0f) {
            rack = aimed ? AIMED : HIP;
            at = through;
        } else if (at >= 0f && at < rack.forwardTo()) {
            at = Math.min(at + seconds / rack.takes(), rack.forwardTo());
        } else {
            at = NO_RACK;
        }
        back = at < 0f ? 0f : rack.at(at);
    }

    void rest() {
        at = NO_RACK;
        back = 0f;
    }
}

package com.cameraunlock.zomboid;

/**
 * How far through an attack the picture is, from how far through its own animation of it the
 * game is, frame by frame.
 *
 * The game lands an attack by how far through its animation was the frame before, so the
 * picture is that one frame behind what the animation reads: it lands on the frame the game
 * does, with what the game hit known. The game lets go of its animation before it is over,
 * and the picture then runs on at the pace it had: cut off part way, what is swung would
 * jump back to where it is held.
 */
final class AttackClock {
    /**
     * How far through the game's animation of an attack what was swung is held again. The game
     * takes no new attack until its animation is over, so the picture's is over a little before.
     */
    static final float HELD_AGAIN = 0.85f;
    private static final float NONE = -1f;
    /** The least of an animation that is before the landing and after it: both are divided by. */
    private static final float LANDS_SOONEST = 0.05f;

    private float at = NONE, seen = NONE, lands, pace;
    /** The length of the frame that read `seen`: the animation moved from `at` to `seen` over it. */
    private float seenOver;
    /** How long the picture stands where the attack landed, and how long it then takes to catch up, as shares of the animation. See HitStop. */
    private float stops, catches;
    /** Whether the frame last stepped is the one an attack began on, and the one it landed on. */
    boolean began, landed;

    /**
     * A frame of the game's logic.
     *
     * @param playing  whether the game is playing an attack's animation
     * @param fraction how far through it the animation is, 0 to 1
     * @param landsAt  how far through it the attack lands
     * @param seconds  the length of the frame
     */
    void step(boolean playing, float fraction, float landsAt, float seconds) {
        began = false;
        landed = false;
        if (playing && (seen == NONE || fraction < seen)) {
            began = true;
            pace = 0f;
            stops = 0f;
            at = 0f;
            lands = Math.clamp(landsAt, LANDS_SOONEST, HELD_AGAIN - LANDS_SOONEST);
            seen = fraction;
            seenOver = seconds;
        } else if (playing) {
            // An animation that has not moved on has fired nothing new: the game is paused.
            if (fraction > seen) {
                boolean before = at >= lands;
                if (seenOver > 1e-4f) {
                    pace = (seen - at) / seenOver;
                }
                at = seen;
                seen = fraction;
                seenOver = seconds;
                landed = !before && at >= lands;
            }
        } else if (at != NONE) {
            // Let go of before it landed, the game's attack never lands: nothing is hit.
            seen = NONE;
            at += pace * seconds;
            if (!(pace > 0f) || at >= HELD_AGAIN) {
                at = NONE;
            }
        }
    }

    /**
     * The attack landed on something: the picture stands where it struck for this long, in
     * seconds of the animation at the pace it has, then catches up with it. Both together take
     * no more than HitStop.ROOM of the way to holding it again.
     */
    void stop(float seconds) {
        stops = seconds * pace;
        catches = HitStop.CATCHES_UP * pace;
        float room = HitStop.ROOM * (HELD_AGAIN - lands);
        if (stops + catches > room) {
            float fits = room / (stops + catches);
            stops *= fits;
            catches *= fits;
        }
    }

    /** No attack is under way. */
    void reset() {
        at = NONE;
        seen = NONE;
        stops = 0f;
        began = false;
        landed = false;
    }

    /** How far the attack is to landing: 0 with none, 1 as it lands. */
    float reached() {
        return at == NONE ? 0f : Math.min(at / lands, 1f);
    }

    /** How far past landing it is: 0 until it lands and while it stands where it struck, 1 when what was swung is held again. */
    float past() {
        return at == NONE ? 0f : Math.clamp(HitStop.shown(at - lands, stops, catches) / (HELD_AGAIN - lands), 0f, 1f);
    }

    /** Whether an attack is under way, or one let go of is still running on. */
    boolean under() {
        return at != NONE;
    }
}

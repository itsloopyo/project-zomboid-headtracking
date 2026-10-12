package com.cameraunlock.zomboid;

/**
 * A blow that lands on something is stopped by it for a moment in the picture: what was swung
 * stands where it struck, then comes on again, slowly at first and then faster than the game's
 * animation, until it is back with it, well before it is held again.
 *
 * Only the picture stops. The game's animation, the hit it makes, and when the next attack may
 * be made are the game's, untouched. Everything here is measured in the animation's own time,
 * as a share of it: while the game is paused nothing moves on, and the stop is as long after as
 * it was before.
 */
final class HitStop {
    private HitStop() {}

    /** How long a blow from the lightest and the heaviest things swung stands where it struck, in seconds. */
    static final float LIGHTEST_STOPS = 0.04f, HEAVIEST_STOPS = 0.11f;
    /** What the lightest and heaviest things swung weigh, in the game's weight: a knife's and a sledgehammer's. */
    static final float LIGHTEST = 0.5f, HEAVIEST = 5f;
    /** How long the picture takes to come back to the animation once it moves again, in seconds. */
    static final float CATCHES_UP = 0.12f;
    /** At most this much of the way from landing to holding it again goes in the stop and the catching up. */
    static final float ROOM = 0.6f;

    /** How long a blow stands where it struck, in seconds, by what struck it weighs. */
    static float seconds(float weight) {
        float heavy = Math.clamp((weight - LIGHTEST) / (HEAVIEST - LIGHTEST), 0f, 1f);
        return LIGHTEST_STOPS + (HEAVIEST_STOPS - LIGHTEST_STOPS) * heavy;
    }

    /**
     * How far past landing the picture is, as a share of the animation.
     *
     * @param beyond  how far past landing the game's animation is, as a share of it
     * @param stops   how long the picture stands, as a share of the animation: 0 for a blow that did not stop
     * @param catches how long it then takes to come back to the animation, as a share of it
     */
    static float shown(float beyond, float stops, float catches) {
        if (!(stops > 0f)) {
            return beyond;
        }
        return beyond * ViewModelMath.smooth((beyond - stops) / catches);
    }
}

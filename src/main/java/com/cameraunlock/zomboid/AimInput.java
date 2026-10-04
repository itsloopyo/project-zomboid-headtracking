package com.cameraunlock.zomboid;

/**
 * Whether the game is told its aim input is held. The game only counts the
 * attack button while the aim input was already held on the frame before, so a
 * click of the attack button alone does nothing. Here the attack button holds
 * the aim input down with it, and for a short tail after it is let go: a click
 * shorter than the two logic frames the game needs still has the aim held when
 * the attack is taken.
 */
final class AimInput {
    static final long TAIL_NANOS = 250_000_000L;

    private boolean tailStarted;
    private long tailEnds;

    /**
     * @param stock      what the game's own aim input reads
     * @param attackDown whether the attack button is down
     * @param now        System.nanoTime()
     */
    boolean held(boolean stock, boolean attackDown, long now) {
        if (attackDown) {
            tailStarted = true;
            tailEnds = now + TAIL_NANOS;
            return true;
        }
        // nanoTime is only good for differences: it can be negative and it wraps.
        return stock || tailStarted && now - tailEnds < 0;
    }

    /** Forgets a tail that is running, so it does not hold the aim once the filter is back on. */
    void reset() {
        tailStarted = false;
    }
}

package com.cameraunlock.zomboid;

/**
 * What the game is told of its aim input and its attack button. The game only counts the
 * attack button while the aim input was already held on the frame before, so a click of
 * the attack button alone does nothing. Here the attack button holds the aim input down
 * with it, and for a short tail after it is let go.
 *
 * A firearm's shot is found a target from what the game worked out on the frames before
 * it, while the aim was held: it takes up what stands under the reticle on one frame, has
 * its shape on the next, and only then finds it along the shot. A shot taken on the frame
 * the aim went up finds nothing and passes through whatever it was pointed at. So with a
 * firearm the game is not told of the attack button until it has had the aim for
 * FRAMES_TO_BEAR frames, and a click that was let go before then is still passed on, held
 * down for the FRAMES_PRESSED frames the game needs to take it.
 */
final class AimInput {
    static final long TAIL_NANOS = 250_000_000L;
    static final int FRAMES_TO_BEAR = 3, FRAMES_PRESSED = 2;

    private boolean tailStarted;
    private long tailEnds;
    private boolean aimed, wasDown, owed;
    private int aimedFrames, pressedFrames;

    /** Once a frame of the game's logic. */
    void frame() {
        aimedFrames = aimed ? aimedFrames + 1 : 0;
        if (pressedFrames > 0) {
            pressedFrames++;
        }
        // A game that never asked after its attack button in all that time is not taking attacks.
        if (aimedFrames > FRAMES_TO_BEAR + FRAMES_PRESSED + 1) {
            owed = false;
            pressedFrames = 0;
        }
    }

    /**
     * Whether the game is told its aim input is held.
     *
     * @param stock      what the game's own aim input reads
     * @param attackDown whether the attack button is down
     * @param now        System.nanoTime()
     */
    boolean held(boolean stock, boolean attackDown, long now) {
        owed |= attackDown && !wasDown;
        wasDown = attackDown;
        if (attackDown || owed) {
            tailStarted = true;
            tailEnds = now + TAIL_NANOS;
            aimed = true;
        } else {
            // nanoTime is only good for differences: it can be negative and it wraps.
            aimed = stock || tailStarted && now - tailEnds < 0;
        }
        return aimed;
    }

    /**
     * Whether the game is told its attack button is down.
     *
     * @param attackDown whether the attack button is down
     * @param firearm    whether what is in hand is a firearm
     */
    boolean attack(boolean attackDown, boolean firearm) {
        if (!firearm) {
            owed = false;
            pressedFrames = 0;
            return attackDown;
        }
        if (aimedFrames < FRAMES_TO_BEAR) {
            return false;
        }
        if (owed) {
            if (pressedFrames == 0) {
                pressedFrames = 1;
            }
            if (pressedFrames <= FRAMES_PRESSED) {
                return true;
            }
            owed = false;
            pressedFrames = 0;
        }
        return attackDown;
    }

    /** Forgets a click and a tail, so neither holds the aim once the filter is back on. */
    void reset() {
        tailStarted = false;
        aimed = false;
        wasDown = false;
        owed = false;
        aimedFrames = 0;
        pressedFrames = 0;
    }
}

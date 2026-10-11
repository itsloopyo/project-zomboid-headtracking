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
 * the aim went up finds nothing and passes through whatever it was pointed at. Those steps
 * are the game's physics world's, which it steps a hundred times a second whatever the frame
 * rate, and a shot taken after only two of them missed one time in ten. So with a firearm
 * the game is not told of the attack button until it has had the aim for FRAMES_TO_BEAR
 * frames and UPDATES_TO_BEAR updates of its physics world, and a click that was let go before
 * then is still passed on, held down for the FRAMES_PRESSED frames the game needs to take it.
 *
 * What is swung needs no target found first, only the aim held on the frame before: the
 * game is told of the attack button from FRAMES_TO_SWING frames after the aim went up, and
 * a click let go before then is passed on the same way. A click shorter than two of the
 * game's frames, which at thirty frames a second is any quick one, would else do nothing.
 */
final class AimInput {
    static final long TAIL_NANOS = 250_000_000L;
    static final int FRAMES_TO_BEAR = 3, UPDATES_TO_BEAR = 3, FRAMES_TO_SWING = 1, FRAMES_PRESSED = 2;
    /** Past this a click is dropped even with the physics world short of its updates: it is not updated while the game is paused. */
    static final int FRAMES_OWED = 30;

    private boolean tailStarted;
    private long tailEnds;
    private boolean aimed, wasDown, owed;
    private int aimedFrames, aimedUpdates, borneFrames, pressedFrames, physicsUpdates;

    /**
     * Once a frame of the game's logic.
     *
     * @param physicsUpdates how many times the game's physics world has been updated, a count that only goes up
     */
    void frame(int physicsUpdates) {
        aimedFrames = aimed ? aimedFrames + 1 : 0;
        aimedUpdates = aimed ? aimedUpdates + physicsUpdates - this.physicsUpdates : 0;
        this.physicsUpdates = physicsUpdates;
        borneFrames = borne() ? borneFrames + 1 : 0;
        if (pressedFrames > 0) {
            pressedFrames++;
        }
        // A game that never asked after its attack button in all that time is not taking attacks.
        if (borneFrames > FRAMES_PRESSED + 1 || aimedFrames > FRAMES_OWED) {
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
        if (firearm ? !borne() : aimedFrames < FRAMES_TO_SWING) {
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

    /** Whether the game has had the aim long enough to find what a firearm's shot is on. */
    private boolean borne() {
        return aimedFrames >= FRAMES_TO_BEAR && aimedUpdates >= UPDATES_TO_BEAR;
    }

    /** Forgets a click and a tail, so neither holds the aim once the filter is back on. */
    void reset() {
        tailStarted = false;
        aimed = false;
        wasDown = false;
        owed = false;
        aimedFrames = 0;
        aimedUpdates = 0;
        borneFrames = 0;
        pressedFrames = 0;
    }
}

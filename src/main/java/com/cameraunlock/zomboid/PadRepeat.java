package com.cameraunlock.zomboid;

/**
 * When a direction held on a controller's d-pad steps the quick loot list: once as it goes down,
 * then again after FIRST_NANOS and every EVERY_NANOS while it stays down, as the game repeats its
 * own d-pad presses. Asked as often as anything reads the direction; it steps once at each time.
 *
 * Logic thread only.
 */
final class PadRepeat {
    static final long FIRST_NANOS = 400_000_000L, EVERY_NANOS = 120_000_000L;

    private boolean down;
    private long next;

    /** Whether the direction steps the list now, with it down or not. */
    boolean step(boolean held, long now) {
        if (!held) {
            down = false;
            return false;
        }
        if (!down) {
            down = true;
            next = now + FIRST_NANOS;
            return true;
        }
        if (now - next >= 0) {
            next = now + EVERY_NANOS;
            return true;
        }
        return false;
    }

    /** Whether the direction is down, as it was last asked. */
    boolean down() {
        return down;
    }
}

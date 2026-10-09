package com.cameraunlock.zomboid;

/**
 * A button pressed twice in quick succession. The second press counts only when it comes
 * within WINDOW_NANOS of the first, so a press held to work the game's own menu on the button
 * and let go is not the first of two.
 */
final class DoubleTap {
    static final long WINDOW_NANOS = 400_000_000L;

    private boolean wasDown, armed;
    private long firstAt;

    /**
     * Once a frame with whether the button is down.
     *
     * @param now System.nanoTime()
     * @return whether this frame's press is the second of two
     */
    boolean press(boolean down, long now) {
        boolean pressed = down && !wasDown;
        wasDown = down;
        if (!pressed) {
            return false;
        }
        // nanoTime is only good for differences: it can be negative and it wraps.
        if (armed && now - firstAt <= WINDOW_NANOS) {
            armed = false;
            return true;
        }
        armed = true;
        firstAt = now;
        return false;
    }
}

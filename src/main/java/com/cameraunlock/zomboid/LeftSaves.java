package com.cameraunlock.zomboid;

/**
 * Saves left, counted on the logic thread, for what the render thread keeps of a save: the
 * render thread asks at its next use whether one has been left since it last asked, and lets
 * go of what it keeps if so.
 */
final class LeftSaves {
    private volatile int left;
    private int seen;

    /** Logic thread: a save is left. */
    void left() {
        left++;
    }

    /** Render thread: whether a save has been left since this was last asked. */
    boolean sinceAsked() {
        int now = left;
        boolean any = now != seen;
        seen = now;
        return any;
    }
}

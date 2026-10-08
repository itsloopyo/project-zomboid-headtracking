package com.cameraunlock.zomboid;

/**
 * Whether each frame of the first person picture is mixed with the one before it: the
 * FrameBlending row of CameraUnlock.ini. Mixed, what changes from frame to frame is steadied
 * and the whole picture is a little softer. Off, each frame is drawn on its own.
 *
 * The row is read again while the game runs. The thread that watches the file sets it and the
 * render thread reads it as it draws a frame.
 */
final class FrameBlending {
    private static volatile boolean on;

    private FrameBlending() {}

    static void set(boolean asked) {
        if (asked == on) {
            return;
        }
        on = asked;
        Log.info("frame blending: " + (asked ? "on" : "off"));
    }

    static boolean on() {
        return on;
    }
}

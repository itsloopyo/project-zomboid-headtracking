package com.cameraunlock.zomboid;

import java.util.Locale;
import java.util.Map;

/**
 * Says where the game stood while it stood still. A frame that takes seconds leaves nothing
 * in the log but its length: by the time it ends, whatever held it has let go. A thread of
 * its own watches for the frames to stop coming and writes down, while they are stopped, what
 * the game's logic thread and render thread are in the middle of.
 */
final class Stall {
    private Stall() {}

    /** Frames this far apart are a stall. */
    private static final long STALLED_NANOS = 1_000_000_000L;
    private static final long LOOKS_EVERY_MILLISECONDS = 200L;
    /** The threads whose place is written down: the game's logic thread, its render thread and its lighting thread. */
    private static final String[] WATCHED = {"MainThread", "main", "Lighting Thread"};
    private static final int FRAMES_WRITTEN = 40;

    private static volatile long beatNanos;
    private static Thread watcher;
    private static boolean said;

    /** Called by the logic thread each time it builds a frame of a player's first person view. With every view off no frame is looked for. */
    static void beat() {
        beatNanos = System.nanoTime();
        if (watcher == null) {
            watcher = new Thread(Stall::watch, "CameraUnlock stall watch");
            watcher.setDaemon(true);
            watcher.start();
        }
    }

    private static void watch() {
        while (true) {
            try {
                Thread.sleep(LOOKS_EVERY_MILLISECONDS);
            } catch (InterruptedException stopped) {
                return;
            }
            long since = System.nanoTime() - beatNanos;
            if (since < STALLED_NANOS || !anyOn()) {
                said = false;
            } else if (!said) {
                // Once a stall: the frames also stop for good when a save is left, and that is said once too.
                said = true;
                Log.info(String.format(Locale.ROOT, "no frame for %.1f s. Where the game's threads stand:%s", since / 1e9, standing()));
            }
        }
    }

    private static boolean anyOn() {
        for (int player = 0; player < View.COUNT; player++) {
            View view = View.of(player);
            if (view != null && view.firstPerson.isOn()) {
                return true;
            }
        }
        return false;
    }

    private static String standing() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<Thread, StackTraceElement[]> each : Thread.getAllStackTraces().entrySet()) {
            Thread thread = each.getKey();
            boolean watched = false;
            for (String name : WATCHED) {
                watched |= name.equals(thread.getName());
            }
            if (!watched) {
                continue;
            }
            out.append(System.lineSeparator()).append("  ").append(thread.getName()).append(", ").append(thread.getState());
            StackTraceElement[] frames = each.getValue();
            for (int i = 0; i < Math.min(frames.length, FRAMES_WRITTEN); i++) {
                out.append(System.lineSeparator()).append("    at ").append(frames[i]);
            }
        }
        return out.toString();
    }
}

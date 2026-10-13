package com.cameraunlock.zomboid;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.util.Locale;

/**
 * Says where the game stood while it stood still. A frame that takes seconds leaves nothing
 * in the log but its length: by the time it ends, whatever held it has let go, and a freeze
 * that never ends leaves nothing at all. A thread of its own watches for the frames of a save
 * to stop coming, in first person and in the game's own view alike, and writes down, while
 * they are stopped, what the game's threads and the mod's are in the middle of and which
 * lock each waits on.
 */
final class Stall {
    private Stall() {}

    /** Frames this far apart are a stall. */
    private static final long STALLED_NANOS = 1_000_000_000L;
    /** While a stall lasts, where the threads stand is written again this often: a freeze shows whether anything moves. */
    private static final long WRITTEN_AGAIN_NANOS = 10_000_000_000L;
    private static final long LOOKS_EVERY_MILLISECONDS = 200L;
    /** The game's logic thread, its render thread and its lighting thread. The mod's own are every thread named CameraUnlock. */
    private static final String[] WATCHED = {"MainThread", "main", "Lighting Thread"};
    private static final int FRAMES_WRITTEN = 40;

    private static volatile long beatNanos;
    /** Set by each frame of a save, cleared as the save is left: the frames stop then, and it is no stall. */
    private static volatile boolean inSave;
    private static Thread watcher;
    private static long writtenAt;

    /** Called by the logic thread once a frame that draws the world of a save, whichever view. */
    static void beat() {
        beatNanos = System.nanoTime();
        inSave = true;
        if (watcher == null) {
            watcher = new Thread(Stall::watch, "CameraUnlock stall watch");
            watcher.setDaemon(true);
            watcher.start();
        }
    }

    /** Called as the game begins to leave a save, which stops its frames while it saves. */
    static void leaving() {
        inSave = false;
    }

    private static void watch() {
        while (true) {
            try {
                Thread.sleep(LOOKS_EVERY_MILLISECONDS);
            } catch (InterruptedException stopped) {
                return;
            }
            long now = System.nanoTime(), since = now - beatNanos;
            if (since < STALLED_NANOS || !inSave || everyoneSleeps()) {
                writtenAt = 0L;
            } else if (writtenAt == 0L || now - writtenAt >= WRITTEN_AGAIN_NANOS) {
                writtenAt = now;
                Log.info(String.format(Locale.ROOT, "no frame for %.1f s. Where the game's threads stand:%s", since / 1e9, standing()));
            }
        }
    }

    /** No frame is drawn while every player sleeps, by the game's own choice. */
    private static boolean everyoneSleeps() {
        for (int i = 0; i < zombie.characters.IsoPlayer.numPlayers; i++) {
            zombie.characters.IsoPlayer each = zombie.characters.IsoPlayer.players[i];
            if (each != null && !each.isAsleep()) {
                return false;
            }
        }
        return true;
    }

    private static String standing() {
        String line = System.lineSeparator();
        StringBuilder out = new StringBuilder();
        for (ThreadInfo thread : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
            String name = thread.getThreadName();
            boolean watched = name.startsWith("CameraUnlock");
            for (String each : WATCHED) {
                watched |= each.equals(name);
            }
            if (!watched) {
                continue;
            }
            out.append(line).append("  ").append(name).append(", ").append(thread.getThreadState());
            if (thread.getLockName() != null) {
                out.append(", waiting on ").append(thread.getLockName());
                if (thread.getLockOwnerName() != null) {
                    out.append(" held by ").append(thread.getLockOwnerName());
                }
            }
            StackTraceElement[] frames = thread.getStackTrace();
            for (int i = 0; i < Math.min(frames.length, FRAMES_WRITTEN); i++) {
                out.append(line).append("    at ").append(frames[i]);
            }
            for (MonitorInfo held : thread.getLockedMonitors()) {
                out.append(line).append("    holds ").append(held).append(" taken at ").append(held.getLockedStackFrame());
            }
            for (LockInfo held : thread.getLockedSynchronizers()) {
                out.append(line).append("    holds ").append(held);
            }
        }
        return out.toString();
    }
}

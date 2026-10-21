package com.cameraunlock.zomboid;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Locale;

/**
 * Says where the game stood while it stood still. A frame that takes seconds leaves nothing
 * in the log but its length: by the time it ends, whatever held it has let go, and a freeze
 * that never ends leaves nothing at all. A thread of its own watches for the frames of a save
 * to stop coming, in first person and in the game's own view alike, and writes down once, while
 * they are stopped, what the game's threads and the mod's are in the middle of and which
 * lock each waits on, and how long the stop lasted once frames come again.
 */
final class Stall {
    private Stall() {}

    /** Frames this far apart are a stall. */
    private static final long STALLED_NANOS = 1_000_000_000L;
    private static final long LOOKS_EVERY_MILLISECONDS = 200L;
    /** The game's logic thread, its render thread and its lighting thread. The mod's own are every thread named CameraUnlock. */
    private static final String[] WATCHED = {"MainThread", "main", "Lighting Thread"};
    private static final int FRAMES_WRITTEN = 40;

    private static volatile long beatNanos;
    /** Set by each frame of a save, cleared as the save is left: the frames stop then, and it is no stall. */
    private static volatile boolean inSave;
    private static Thread watcher;
    /** When the frames stopped, if where the threads stand has been written for this stall, else 0. */
    private static long stalledSince;

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
            long now = System.nanoTime(), beat = beatNanos, since = now - beat;
            if (since < STALLED_NANOS || !inSave || everyoneSleeps()) {
                if (stalledSince != 0L) {
                    if (since < STALLED_NANOS) {
                        Log.info(String.format(Locale.ROOT, "frames came again after %.1f s", (beat - stalledSince) / 1e9));
                    }
                    stalledSince = 0L;
                }
            } else if (stalledSince == 0L) {
                stalledSince = now - since;
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
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        // Named first with no stack, so only the watched threads' stacks and locks are taken.
        long[] asked = Arrays.stream(threads.getThreadInfo(threads.getAllThreadIds(), 0))
                .filter(each -> each != null && watched(each.getThreadName())).mapToLong(ThreadInfo::getThreadId).toArray();
        for (ThreadInfo thread : threads.getThreadInfo(asked, true, true, FRAMES_WRITTEN)) {
            if (thread == null) {
                // It ended between being listed and being asked after.
                continue;
            }
            String name = thread.getThreadName();
            out.append(line).append("  ").append(name).append(", ").append(thread.getThreadState());
            if (thread.getLockName() != null) {
                out.append(", waiting on ").append(thread.getLockName());
                if (thread.getLockOwnerName() != null) {
                    out.append(" held by ").append(thread.getLockOwnerName());
                }
            }
            for (StackTraceElement frame : thread.getStackTrace()) {
                out.append(line).append("    at ").append(frame);
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

    private static boolean watched(String name) {
        if (name.startsWith("CameraUnlock")) {
            return true;
        }
        for (String each : WATCHED) {
            if (each.equals(name)) {
                return true;
            }
        }
        return false;
    }
}

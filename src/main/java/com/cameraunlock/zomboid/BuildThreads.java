package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The threads chunks are built on off the logic thread. A build reads the game's map, which the
 * game changes on the logic thread, so it runs only while the logic thread waits on it inside the
 * mod's frame, where the game moves nothing on the map.
 */
final class BuildThreads {
    private BuildThreads() {}

    /** One of the mod's build threads. */
    private static final class BuildThread extends Thread {
        BuildThread(Runnable run, String name) {
            super(run, name);
            setDaemon(true);
        }
    }

    /**
     * The one lock over what the builds share and change as they go (the roofs, the wall finishes, the heights, room
     * daylight, the sets' numbers, the game's light): a build thread holds it for each asking. Taken in that order
     * nowhere else, so there is no other lock to wait on under it, and what the logic thread does for a build thread
     * as it waits (onLogicThread) never takes it.
     */
    static final Object SHARED = new Object();

    /**
     * What builds made on a build thread did that is the logic thread's to do (a chunk told of, a chunk's light
     * marked, the heights joined at a chunk's edges), kept in the order they did it until the logic thread does it
     * (land) or it is let go of (forget).
     */
    static final class Landing {
        private final ArrayList<Runnable> left = new ArrayList<>();

        /** Does all that was kept, in order, and lets it go. Logic thread. */
        void land() {
            for (int i = 0; i < left.size(); i++) {
                left.get(i).run();
            }
            left.clear();
        }

        void forget() {
            left.clear();
        }
    }

    /** Where what the builds a build thread makes leave to the logic thread is kept, from landInto until it is set again. */
    private static final ThreadLocal<Landing> landings = new ThreadLocal<>();

    /** Has what the builds made on this build thread from now on leave to the logic thread kept in landing: null for nowhere, which leaving anything throws on. */
    static void landInto(Landing landing) {
        landings.set(landing);
    }

    /** Does what a build leaves to the logic thread: at once on the logic thread, and on a build thread kept where landInto says. */
    static void later(Runnable effect) {
        if (!here()) {
            effect.run();
            return;
        }
        Landing landing = landings.get();
        if (landing == null) {
            throw new IllegalStateException("a build on " + Thread.currentThread().getName() + " left work for the logic thread with nowhere to keep it");
        }
        landing.left.add(effect);
    }

    /** ChunkMarks.tell from a build, as later. */
    static void tell(int wx, int wy) {
        later(() -> ChunkMarks.tell(wx, wy, wx, wy));
    }

    /** The same for every chunk from (minWx, minWy) to (maxWx, maxWy). */
    static void tell(int minWx, int minWy, int maxWx, int maxWy) {
        later(() -> ChunkMarks.tell(minWx, minWy, maxWx, maxWy));
    }

    /** Whether the thread asking is one of the mod's build threads. */
    static boolean here() {
        return Thread.currentThread() instanceof BuildThread;
    }

    /** A pool of one build thread, made on first use. */
    static ExecutorService one(String name) {
        return Executors.newSingleThreadExecutor(run -> new BuildThread(run, name));
    }

    /** Runs the build on the pool and waits for it: what it throws, thrown again here. */
    static void runAndWait(ExecutorService pool, Runnable build) {
        Future<?> running = pool.submit(build);
        try {
            running.get();
        } catch (ExecutionException e) {
            throw new IllegalStateException("a build on a build thread threw", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("the logic thread was interrupted waiting on a build thread", e);
        }
    }
}

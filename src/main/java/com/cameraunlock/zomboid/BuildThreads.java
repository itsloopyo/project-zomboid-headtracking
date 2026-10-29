package com.cameraunlock.zomboid;

import java.util.Arrays;
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
     * What builds made on a build thread told chunks (ChunkMarks.tell, which is the logic thread's alone), kept in
     * the order they told it until the logic thread tells it (tell) or it is let go of (forget).
     */
    static final class Tells {
        private int[] told = new int[64];
        private int count;

        private void add(int minWx, int minWy, int maxWx, int maxWy) {
            if (count + 4 > told.length) {
                told = Arrays.copyOf(told, told.length * 2);
            }
            told[count++] = minWx;
            told[count++] = minWy;
            told[count++] = maxWx;
            told[count++] = maxWy;
        }

        /** Tells ChunkMarks all that was kept, in order, and lets it go. Logic thread. */
        void tell() {
            for (int at = 0; at < count; at += 4) {
                ChunkMarks.tell(told[at], told[at + 1], told[at + 2], told[at + 3]);
            }
            count = 0;
        }

        void forget() {
            count = 0;
        }
    }

    /** Where what the builds a build thread makes tell chunks is kept, from tellInto until it is set again. */
    private static final ThreadLocal<Tells> tellings = new ThreadLocal<>();

    /** Has what the builds made on this build thread from now on tell chunks kept in tells: null for nowhere, which a telling throws on. */
    static void tellInto(Tells tells) {
        tellings.set(tells);
    }

    /** ChunkMarks.tell from a build: at once on the logic thread, and on a build thread kept where tellInto says. */
    static void tell(int wx, int wy) {
        tell(wx, wy, wx, wy);
    }

    /** The same for every chunk from (minWx, minWy) to (maxWx, maxWy). */
    static void tell(int minWx, int minWy, int maxWx, int maxWy) {
        if (!here()) {
            ChunkMarks.tell(minWx, minWy, maxWx, maxWy);
            return;
        }
        Tells tells = tellings.get();
        if (tells == null) {
            throw new IllegalStateException("a build on " + Thread.currentThread().getName() + " told chunks with nowhere to keep it");
        }
        tells.add(minWx, minWy, maxWx, maxWy);
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

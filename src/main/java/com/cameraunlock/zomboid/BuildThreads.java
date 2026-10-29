package com.cameraunlock.zomboid;

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

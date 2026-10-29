package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The threads chunks are built on off the logic thread. A build reads the game's map, which the
 * game changes on the logic thread, so it runs only while the logic thread waits on it inside the
 * mod's frame, where the game moves nothing on the map. What a build has to have done on the
 * logic thread, the logic thread does as it waits (onLogicThread).
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
     * daylight, the sets' numbers, the game's light): a build thread holds it for each asking, and the logic thread
     * holds it for what it does for a build thread as it waits (onLogicThread), which may read the game's light too.
     * No other lock is waited on under it.
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

    /** What the build threads have asked the logic thread to do, and for each piece of work run, its end (DONE). */
    private static final LinkedBlockingQueue<FutureTask<?>> asked = new LinkedBlockingQueue<>();
    private static final FutureTask<?> DONE = new FutureTask<>(() -> null);

    /** A pool of one build thread, made on first use. */
    static ExecutorService one(String name) {
        return pool(1, name);
    }

    /** A pool of count build threads, each made on first use. */
    static ExecutorService pool(int count, String name) {
        int[] made = {0};
        return Executors.newFixedThreadPool(count, run -> new BuildThread(run, count == 1 ? name : name + " " + ++made[0]));
    }

    /** Runs the build on the pool and waits for it, doing what it asks of the logic thread: what it throws, thrown again here. */
    static void runAndWait(ExecutorService pool, Runnable build) {
        waitOn(new Future<?>[] {pool.submit(ended(build))});
    }

    /**
     * Runs work on count threads of the pool at once, each handed its number from 0, and waits for them all, doing
     * what they ask of the logic thread. What any of them throws is thrown again here once all have ended.
     */
    static void runOnAll(ExecutorService pool, int count, IntConsumer work) {
        Future<?>[] running = new Future<?>[count];
        for (int thread = 0; thread < count; thread++) {
            int number = thread;
            running[thread] = pool.submit(ended(() -> work.accept(number)));
        }
        waitOn(running);
    }

    private static Runnable ended(Runnable work) {
        return () -> {
            try {
                work.run();
            } finally {
                asked.add(DONE);
            }
        };
    }

    private static void waitOn(Future<?>[] running) {
        try {
            for (int left = running.length; left > 0;) {
                FutureTask<?> ask = asked.take();
                if (ask == DONE) {
                    left--;
                } else {
                    // Under SHARED, so that the game's light, which what is asked may read, is read by one thread at a time.
                    synchronized (SHARED) {
                        ask.run();
                    }
                }
            }
            for (Future<?> each : running) {
                each.get();
            }
        } catch (ExecutionException e) {
            throw new IllegalStateException("a build on a build thread threw", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("the logic thread was interrupted waiting on a build thread", e);
        }
    }

    /**
     * What has to be done on the logic thread, done there: at once on the logic thread itself, and from a build
     * thread by the logic thread as it waits on the build, under SHARED, while this one waits for the answer. A
     * build thread holding SHARED cannot ask, as the logic thread would wait on it for the lock.
     */
    static <T> T onLogicThread(Supplier<T> work) {
        if (!here()) {
            return work.get();
        }
        if (Thread.holdsLock(SHARED)) {
            throw new IllegalStateException(Thread.currentThread().getName() + " asked the logic thread for something holding the builds' lock, which it would wait on");
        }
        FutureTask<T> task = new FutureTask<>(work::get);
        asked.add(task);
        try {
            return task.get();
        } catch (ExecutionException e) {
            throw new IllegalStateException("what a build thread asked of the logic thread threw", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("a build thread was interrupted waiting on the logic thread", e);
        }
    }
}

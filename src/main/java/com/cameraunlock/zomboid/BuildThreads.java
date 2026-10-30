package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.List;
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
 * game changes on the logic thread, so it runs only while the logic thread is inside the mod's
 * frame, where the game moves nothing on the map: building the frame or waiting on the builds.
 * What a build has to have done on the logic thread, the logic thread does as it waits (onLogicThread).
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

    /** What the build threads have asked the logic thread to do, and for each piece of work run, its end (an End). */
    private static final LinkedBlockingQueue<FutureTask<?>> asked = new LinkedBlockingQueue<>();

    /**
     * The end of a piece of work, one for each wait: a wait begun while another is open (a dev check's build during a
     * Handing) takes the other's ends off the queue too, and must neither count them nor lose them.
     */
    private static final class End extends FutureTask<Object> {
        End() {
            super(() -> null);
        }
    }

    /**
     * Set once the logic thread stops waiting on build threads that have not ended (it was interrupted): they may still
     * be making what they were handed and asking of the logic thread, so nothing is waited on or handed again.
     */
    private static IllegalStateException stranded;

    private static void notStranded() {
        if (stranded != null) {
            throw new IllegalStateException("build threads were left running when the logic thread stopped waiting on them, so none can be waited on or handed work again", stranded);
        }
    }

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
        End end = new End();
        waitOn(new Future<?>[] {pool.submit(ended(build, end))}, end);
    }

    /**
     * Whether build threads may be making builds while the logic thread goes on with its frame (Handing): the logic
     * thread then reads the game's light only under SHARED, as they do. Set and read on the logic thread, and read by
     * the dev check of who reads the light.
     */
    private static volatile boolean handing;

    static boolean handing() {
        return handing;
    }

    /**
     * Work handed to build threads as the logic thread comes to it, while the logic thread goes on: the first piece
     * starts count threads of the pool, each handed its number from 0, which take the pieces in the order they were
     * handed (next) until the logic thread says there is no more and waits on them (finish). What they ask of the
     * logic thread is done there as it waits.
     */
    static final class Handing<T> {
        private static final Object NO_MORE = new Object();
        private final LinkedBlockingQueue<Object> waiting = new LinkedBlockingQueue<>();
        private Future<?>[] running;
        private End end;

        /** Logic thread: hands a piece on, starting the threads with the first. */
        void hand(T work, ExecutorService pool, int count, IntConsumer thread) {
            notStranded();
            if (running == null) {
                handing = true;
                running = new Future<?>[count];
                end = new End();
                for (int each = 0; each < count; each++) {
                    int number = each;
                    running[each] = pool.submit(ended(() -> thread.accept(number), end));
                }
            }
            waiting.add(work);
        }

        /** On a build thread: the next piece handed, once there is one, or null once there is no more. */
        @SuppressWarnings("unchecked")
        T next() {
            try {
                Object next = waiting.take();
                return next == NO_MORE ? null : (T) next;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(Thread.currentThread().getName() + " was interrupted waiting for a build to be handed", e);
            }
        }

        /**
         * Logic thread: says there is no more and waits for every piece to be made, doing what the threads ask of it.
         * What any of them threw is thrown here once all have ended. Nothing to wait for if nothing was handed.
         */
        void finish() {
            if (running == null) {
                return;
            }
            for (int each = 0; each < running.length; each++) {
                waiting.add(NO_MORE);
            }
            try {
                waitOn(running, end);
            } finally {
                // Left as it is if the threads were stranded: they may still be taking from waiting.
                if (stranded == null) {
                    running = null;
                    end = null;
                    waiting.clear();
                    handing = false;
                }
            }
        }
    }

    private static Runnable ended(Runnable work, End end) {
        return () -> {
            try {
                work.run();
            } finally {
                asked.add(end);
            }
        };
    }

    private static void waitOn(Future<?>[] running, End end) {
        notStranded();
        List<FutureTask<?>> othersEnds = new ArrayList<>();
        try {
            for (int left = running.length; left > 0;) {
                FutureTask<?> ask = asked.take();
                if (ask == end) {
                    left--;
                } else if (ask instanceof End) {
                    othersEnds.add(ask);
                } else {
                    // Under SHARED, so that the game's light, which what is asked may read, is read by one thread at a time.
                    synchronized (SHARED) {
                        ask.run();
                    }
                }
            }
            asked.addAll(othersEnds);
            othersEnds.clear();
            for (Future<?> each : running) {
                each.get();
            }
        } catch (ExecutionException e) {
            throw new IllegalStateException("a build on a build thread threw", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stranded = new IllegalStateException("the logic thread was interrupted waiting on a build thread", e);
            throw stranded;
        }
    }

    /**
     * What has to be done on the logic thread, done there: at once on the logic thread itself, under SHARED, and from a build
     * thread by the logic thread as it waits on the build, under SHARED, while this one waits for the answer. A
     * build thread holding SHARED cannot ask, as the logic thread would wait on it for the lock.
     */
    static <T> T onLogicThread(Supplier<T> work) {
        if (!here()) {
            // Under SHARED as when it is asked for, as build threads may be making builds while the logic thread goes on (Handing).
            synchronized (SHARED) {
                return work.get();
            }
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

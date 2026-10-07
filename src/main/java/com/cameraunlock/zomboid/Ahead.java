package com.cameraunlock.zomboid;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Work begun on other threads before its result is asked for: reading a picture out of the
 * jar takes a tenth of a second or so, and done one after another on the frame that first
 * draws the picture, the reading held that frame for seconds.
 */
final class Ahead {
    private static final Map<String, Task<?>> BEGUN = new ConcurrentHashMap<>();
    /** Two cores are left to the game's own two busy threads. The threads go when there is no more to do. */
    private static final ThreadPoolExecutor WORKERS = workers();

    private Ahead() {}

    private static ThreadPoolExecutor workers() {
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 2));
        ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), work -> {
            Thread thread = new Thread(work, "CameraUnlock ahead");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    /**
     * Work whose result may be memory outside the heap. Taken, it is the taker's to free.
     * Forgotten, nobody will take it, and it is freed here: by forget if the work had ended,
     * and by the thread doing the work if it ends afterwards.
     */
    private static final class Task<T> extends FutureTask<T> {
        private volatile T made;

        Task(Callable<T> work) {
            super(work);
        }

        @Override
        protected void set(T result) {
            made = result;
            super.set(result);
            if (isCancelled()) {
                free();
            }
        }

        void free() {
            if (made instanceof OffHeap.Block block) {
                block.free();
            }
        }
    }

    /** Begins work, whose result {@link #take} hands over under the same name. */
    static <T> void begin(String name, Callable<T> work) {
        Task<T> task = new Task<>(work);
        BEGUN.put(name, task);
        WORKERS.execute(task);
    }

    /**
     * Lets go of work begun under name whose result nobody will take: it is not started if no
     * thread has started on it, and a result that is memory outside the heap is given back.
     */
    static void forget(String name) {
        Task<?> task = BEGUN.remove(name);
        // Not cancelled is ended: with a result, which is then freed, or with a fault, which left none.
        if (task != null && !task.cancel(false)) {
            task.free();
        }
    }

    /** Whether the work begun under name is done and not yet taken. */
    static boolean done(String name) {
        FutureTask<?> task = BEGUN.get(name);
        return task != null && task.isDone();
    }

    /**
     * The result of work: of what was begun under name where something was, done here if no
     * thread has started on it and waited for if one has, and otherwise of work done here.
     */
    @SuppressWarnings("unchecked")
    static <T> T take(String name, Callable<T> work) throws IOException {
        Task<T> task = (Task<T>) BEGUN.remove(name);
        try {
            if (task == null) {
                return work.call();
            }
            task.run();
            return task.get();
        } catch (ExecutionException failed) {
            throw thrown(failed.getCause());
        } catch (Exception failed) {
            throw thrown(failed);
        }
    }

    private static RuntimeException thrown(Throwable cause) throws IOException {
        if (cause instanceof IOException unread) {
            throw unread;
        }
        if (cause instanceof RuntimeException fault) {
            throw fault;
        }
        throw new IllegalStateException(cause);
    }
}

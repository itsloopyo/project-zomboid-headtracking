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
    private static final Map<String, FutureTask<?>> BEGUN = new ConcurrentHashMap<>();
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

    /** Begins work, whose result {@link #take} hands over under the same name. */
    static void begin(String name, Callable<?> work) {
        FutureTask<?> task = new FutureTask<>(work);
        BEGUN.put(name, task);
        WORKERS.execute(task);
    }

    /** Lets go of work begun under name whose result nobody will take: it is not started if no thread has started on it. */
    static void forget(String name) {
        FutureTask<?> task = BEGUN.remove(name);
        if (task != null) {
            task.cancel(false);
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
        FutureTask<T> task = (FutureTask<T>) BEGUN.remove(name);
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

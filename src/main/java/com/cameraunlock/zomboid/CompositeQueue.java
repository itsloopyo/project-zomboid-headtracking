package com.cameraunlock.zomboid;

import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The composites asked for in the frame the logic thread is drawing up, for the render thread
 * to make while it waits for that frame: see WaitingComposites, which hands this the game's
 * own. Apart from the game's classes so the tests can ask.
 *
 * The render thread waits for a frame holding the game's lock on its frames, and lets go of it
 * only as it sleeps, so whatever it makes in the wait it makes holding that lock. It stops
 * after the one in hand as soon as another thread wants the lock: to wake it, for more or for
 * anything else, or to hand the frame over. Nothing asked for in a frame is kept past its
 * hand-over, or past the game letting go of the frame's draws. What is left then is made where
 * the game makes it, in the frame's draw.
 */
final class CompositeQueue<T> {
    private final ConcurrentLinkedQueue<T> asked;
    private final Predicate<T> made;
    private final Consumer<T> make;
    private final Runnable wake;
    /** Held while the render thread makes them, so the logic thread empties the queue only between two. */
    private final Object making = new Object();
    /** The logic thread is handing the frame over, or letting go of its draws. */
    private volatile boolean handing;
    /** How many threads are waking the render thread from its wait, for which each needs the lock on the frames. */
    private final AtomicInteger waking = new AtomicInteger();
    /**
     * The render thread is in the callback of its wait, which it runs holding the lock, and
     * looks at the queue before it sleeps again: no ask has to wake it.
     */
    private volatile boolean calling;
    private volatile long handingSince, wakingSince;

    /** Dev only, for the rig. Each is added to by one thread and read and emptied by the logic thread. */
    private final AtomicLong tollMade = new AtomicLong(), tollMadeNanos = new AtomicLong(), tollUnready = new AtomicLong(), tollLeft = new AtomicLong(),
            tollLetGo = new AtomicLong(), tollWakes = new AtomicLong(), tollHeld = new AtomicLong(), tollHeldNanos = new AtomicLong(), tollHeldMost = new AtomicLong();

    /**
     * @param made whether one is made
     * @param make makes one, on the render thread, or leaves it unmade while what it is made
     *     from is not ready
     * @param wake wakes the render thread from its wait: calls notifying, takes the lock on
     *     the frames, and calls woken before it lets go of it
     */
    CompositeQueue(Predicate<T> made, Consumer<T> make, Runnable wake) {
        this(new ConcurrentLinkedQueue<>(), made, make, wake);
    }

    /**
     * Keeps what is asked in the given store, which is empty. Between toMake's first look
     * finding none and its second, the only code that runs and is not this class's is the
     * store's isEmpty, so a test steps into that moment through a store of its own.
     */
    CompositeQueue(ConcurrentLinkedQueue<T> asked, Predicate<T> made, Consumer<T> make, Runnable wake) {
        this.asked = asked;
        this.made = made;
        this.make = make;
        this.wake = wake;
    }

    /** Logic thread: one asked for in the frame being drawn up. */
    void ask(T composite) {
        asked.offer(composite);
        if (calling) {
            return;
        }
        tollWakes.incrementAndGet();
        wake.run();
    }

    /** Any thread, as it comes to wake the render thread and has yet to take the lock. */
    void notifying() {
        wakingSince = System.nanoTime();
        waking.incrementAndGet();
    }

    /**
     * The thread that woke the render thread, still holding the lock. Counted off there, the
     * render thread sees it counted off as it takes the lock.
     */
    void woken() {
        waking.decrementAndGet();
    }

    /** Logic thread, as it begins to hand the frame over and has yet to take the lock. */
    void handing() {
        handingSince = System.nanoTime();
        handing = true;
    }

    /** Logic thread, holding the lock, with the frame handed over: what was not made is the draw's. */
    void handed() {
        tollLeft.addAndGet(unmade());
        asked.clear();
        handing = false;
    }

    /**
     * Logic thread, as the game lets go of a frame's draws and of the composites only they
     * held: the frame it last drew, as it begins to draw up the next, where the queue is
     * already empty, and under the Lua debugger the frame being drawn up, where it is not.
     */
    void lettingGo() {
        if (asked.isEmpty()) {
            return;
        }
        handingSince = System.nanoTime();
        handing = true;
        synchronized (making) {
            tollLetGo.addAndGet(unmade());
            asked.clear();
        }
        handing = false;
    }

    /** How many in the queue are not made. Logic thread, with the render thread not making any. */
    private int unmade() {
        int unmade = 0;
        for (T composite : asked) {
            if (!made.test(composite)) {
                unmade++;
            }
        }
        return unmade;
    }

    /** Render thread, as the callback of its wait for a frame begins, holding the lock. */
    void calling() {
        calling = true;
    }

    /**
     * Render thread, as that callback ends, holding the lock: whether to make some now. With
     * none to make it lets the logic thread know the next ask has to wake it, and then looks
     * once more for one asked before the logic thread knew.
     *
     * @param waiting whether the render thread goes on waiting for the frame
     */
    boolean toMake(boolean waiting) {
        if (waiting && wanted()) {
            return true;
        }
        calling = false;
        if (!waiting || !wanted()) {
            return false;
        }
        calling = true;
        return true;
    }

    private boolean wanted() {
        return !handing && waking.get() == 0 && !asked.isEmpty();
    }

    /** Render thread, holding the lock: makes what is asked, oldest first, until none is left or another thread wants the lock. */
    void make() {
        synchronized (making) {
            for (T composite; !handing && waking.get() == 0 && (composite = asked.poll()) != null; ) {
                if (made.test(composite)) {
                    continue;
                }
                long began = System.nanoTime();
                make.accept(composite);
                tollMadeNanos.addAndGet(System.nanoTime() - began);
                (made.test(composite) ? tollMade : tollUnready).incrementAndGet();
            }
        }
        // How long the thread that came for the lock has waited for the one that was in hand.
        boolean handingOver = handing;
        if (handingOver || waking.get() != 0) {
            long held = System.nanoTime() - (handingOver ? handingSince : wakingSince);
            tollHeld.incrementAndGet();
            tollHeldNanos.addAndGet(held);
            tollHeldMost.accumulateAndGet(held, Math::max);
        }
    }

    /** Dev only, for the rig: what the waits have come to since this was last asked. Logic thread. */
    String tolls() {
        return String.format(Locale.ROOT, "made in the wait %d in %.1f ms, tried there before their pictures were ready %d, left to the draw %d, let go of with their frame %d, "
                        + "wakes %d, a thread held for the one in hand %d times, %.2f ms in all, at most %.2f ms",
                tollMade.getAndSet(0), tollMadeNanos.getAndSet(0) / 1e6, tollUnready.getAndSet(0), tollLeft.getAndSet(0), tollLetGo.getAndSet(0), tollWakes.getAndSet(0),
                tollHeld.getAndSet(0), tollHeldNanos.getAndSet(0) / 1e6, tollHeldMost.getAndSet(0) / 1e6);
    }
}

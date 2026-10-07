package com.cameraunlock.zomboid;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.IdentityHashMap;

/**
 * The memory a chunk's vertices cross from the logic thread to the render thread in.
 *
 * The logic thread takes a buffer, fills it with a build's vertices and leaves it with the
 * build. The render thread hands the vertices to the graphics card the first time a frame
 * shows the build and gives the buffer back, so the vertices of a chunk that is on the
 * graphics card are nowhere in this process. The buffers go round: the same few carry every
 * build, and a frame that builds no more than the last one makes no garbage.
 *
 * They are outside the Java heap, where the graphics driver reads them without a copy, and
 * one that is let go is freed there and then: see OffHeap.
 */
final class VertexLoans {
    /** No buffer is shorter than two to this power floats: fewer lengths means more buffers fit the next asker. */
    private static final int SMALLEST = 12;
    /** A buffer is at most two to this power floats: a direct buffer's length in bytes is an int. */
    private static final int LARGEST = 28;
    private static final int LENGTHS = LARGEST - SMALLEST + 1;
    /**
     * How long a buffer lies idle before it is let go. A view standing still builds its chunks
     * again in bursts two seconds apart, and between two bursts every buffer is idle: judged
     * over less than several bursts, the whole pool was let go after each and made again for
     * the next, forty megabytes at a time.
     */
    static final long IDLE_FOR_NANOS = 20_000_000_000L;
    private static final FloatBuffer[][] idle = new FloatBuffer[LENGTHS][4];
    private static final int[] idleCount = new int[LENGTHS];
    /** The fewest of each length that have been idle at once since buffers were last let go: those were never wanted. */
    private static final int[] leastIdle = new int[LENGTHS];
    /** The memory behind every buffer there is, lent or idle. */
    private static final IdentityHashMap<FloatBuffer, OffHeap.Block> blocks = new IdentityHashMap<>();
    private static long idleBytes;
    private static long lentBytes;
    private static long trimmedNanos;
    private static boolean trimmedOnce;

    private VertexLoans() {}

    /** An empty buffer with room for this many floats, which is the taker's until it is given back. */
    static synchronized FloatBuffer take(int floats) {
        if (floats <= 0 || floats > 1 << LARGEST) {
            throw new IllegalArgumentException("a buffer of " + floats + " floats");
        }
        int power = Math.max(SMALLEST, 32 - Integer.numberOfLeadingZeros(floats - 1));
        int length = power - SMALLEST;
        long bytes = (long) Float.BYTES << power;
        lentBytes += bytes;
        if (idleCount[length] > 0) {
            FloatBuffer kept = idle[length][--idleCount[length]];
            idle[length][idleCount[length]] = null;
            leastIdle[length] = Math.min(leastIdle[length], idleCount[length]);
            idleBytes -= bytes;
            kept.clear();
            return kept;
        }
        leastIdle[length] = 0;
        OffHeap.Block block = OffHeap.take((int) bytes);
        FloatBuffer made = block.bytes.asFloatBuffer();
        blocks.put(made, block);
        return made;
    }

    /** Gives back a buffer take handed out. Whoever gives it must not touch it again. */
    static synchronized void give(FloatBuffer buffer) {
        if (!blocks.containsKey(buffer)) {
            throw new IllegalArgumentException("a buffer of " + buffer.capacity() + " floats is not one that was taken");
        }
        int power = 31 - Integer.numberOfLeadingZeros(buffer.capacity());
        int length = power - SMALLEST;
        lentBytes -= (long) Float.BYTES << power;
        if (idleCount[length] == idle[length].length) {
            idle[length] = Arrays.copyOf(idle[length], idleCount[length] * 2);
        }
        idle[length][idleCount[length]++] = buffer;
        idleBytes += (long) Float.BYTES << power;
    }

    /**
     * Lets go of the buffers that have lain idle for all of IDLE_FOR_NANOS, and does nothing
     * when called sooner than that after it last let any go. A journey to a new place builds
     * every chunk in sight in one frame, and what carried them is more than any later frame
     * needs. What the frames since did need at once is kept, so a steady pace of building
     * finds its buffers here every time.
     */
    static synchronized void trim(long nowNanos) {
        if (trimmedOnce && nowNanos - trimmedNanos < IDLE_FOR_NANOS) {
            return;
        }
        trimmedOnce = true;
        trimmedNanos = nowNanos;
        for (int length = 0; length < LENGTHS; length++) {
            for (int surplus = leastIdle[length]; surplus > 0; surplus--) {
                FloatBuffer gone = idle[length][--idleCount[length]];
                idle[length][idleCount[length]] = null;
                blocks.remove(gone).free();
                idleBytes -= (long) Float.BYTES << length + SMALLEST;
            }
            leastIdle[length] = idleCount[length];
        }
    }

    /** How many bytes are out with a build, and how many are kept for the next. */
    static synchronized long lentBytes() {
        return lentBytes;
    }

    static synchronized long idleBytes() {
        return idleBytes;
    }
}

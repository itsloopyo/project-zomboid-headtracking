package com.cameraunlock.zomboid;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.fboRenderChunk.FBORenderLevels;

/**
 * What the game says has changed, chunk by chunk.
 *
 * The game draws each level of each chunk once into a texture of its own and draws that
 * texture until something on the level changes, so whatever in it changes what is in that
 * texture says so: an object put down, taken away or given another sprite, a door, a body,
 * blood, through two methods of IsoChunk, and the light of a square, which the game finds
 * changed whenever anything asks a square for its light and the lighting engine has a new
 * one, through one of FBORenderLevels. The three are patched to call marked and lit. A count
 * is kept for each chunk, and a build that remembers the counts round its chunk knows
 * without reading the chunk that the game has changed nothing on it since.
 *
 * The game marks a chunk's texture for more than that through FBORenderLevels, with the same
 * flags: every chunk that comes onto its own screen, as if all its objects were new. So only
 * the light is taken from there.
 *
 * The game marks from its logic thread and, on a server's client, from wherever it applies
 * what the server sent, so the counts are atomic and nothing here takes a lock.
 */
public final class ChunkMarks {
    /** As many places a side as WorldScene's table of builds: the loaded map is at most 19 chunks a side, so no two of its chunks share one. */
    static final int SIDE = 64;
    /**
     * The game's FBORenderChunk.DIRTY_ flags for what no build goes by: a texture newly made
     * (512), and what the game cuts away or fades so that its own overhead view can see the
     * player (2048, 8192, 16384).
     */
    private static final long NOT_BUILT_FROM = 512L | 2048L | 8192L | 16384L;
    private static final long LIGHTING = 32L;
    /** An object put down, taken away or changed (64, 128, 256), and what marks those for a thing that is no part of a building: blood, a body, an item on the ground (1, 2, 4, 8, 16). */
    private static final long OBJECT = 64L | 128L | 256L, LOOSE = 1L | 2L | 4L | 8L | 16L;
    private static final AtomicIntegerArray things = new AtomicIntegerArray(SIDE * SIDE), lights = new AtomicIntegerArray(SIDE * SIDE),
            structures = new AtomicIntegerArray(SIDE * SIDE);
    /**
     * Raised by the mod itself, on the logic thread alone, for a chunk whose builds were told
     * something that is now otherwise and that no reading of the chunk would show: how a roof
     * was fitted, what a building's walls are finished in.
     */
    private static final int[] told = new int[SIDE * SIDE];
    /** Dev only: how many marks came with each of the game's flags, by the flag's bit. */
    static final boolean COUNTED = Boolean.getBoolean("cameraunlock.zomboid.auditMarks");
    private static final AtomicLongArray byFlag = new AtomicLongArray(17);
    private static final int UNLOADED = 16;
    private static final java.util.Set<String> threads = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> callers = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Whether the game is telling: both patches are in its code and it keeps a texture for each
     * chunk, which is what it marks for. Set by WorldScene on the logic thread, and read there.
     */
    static boolean live;

    private ChunkMarks() {}

    /** Called by the game where it marks a level of a chunk's texture as to be drawn again: see Agent.chunkMarkPatches. */
    public static void lit(FBORenderLevels levels, long flags) {
        if ((flags & LIGHTING) != 0L) {
            marked(levels.getChunk(), LIGHTING);
        }
    }

    /** Called by the game where something on a chunk has changed, on one level or on all of them. */
    public static void marked(IsoChunk chunk, long flags) {
        if ((flags & ~NOT_BUILT_FROM) == 0L) {
            return;
        }
        // A chunk being filled as it loads is marked for every object put on it. It has no build yet, and the chunks beside it learn of it from the map.
        if (!chunk.loaded) {
            if (COUNTED) {
                byFlag.getAndIncrement(UNLOADED);
            }
            return;
        }
        int slot = slot(chunk.wx, chunk.wy);
        if ((flags & ~(NOT_BUILT_FROM | LIGHTING)) != 0L) {
            things.getAndIncrement(slot);
        }
        if ((flags & OBJECT) != 0L && (flags & LOOSE) == 0L) {
            structures.getAndIncrement(slot);
        }
        if ((flags & LIGHTING) != 0L) {
            lights.getAndIncrement(slot);
        }
        if (COUNTED) {
            for (int bit = 0; bit < 16; bit++) {
                if ((flags >> bit & 1L) != 0L) {
                    byFlag.getAndIncrement(bit);
                }
            }
            if (threads.add(Thread.currentThread().getName())) {
                Log.info("chunk marks: the game marks from the thread " + Thread.currentThread().getName() + ", flags " + flags);
            }
            if ((flags & ~(NOT_BUILT_FROM | LIGHTING)) != 0L && (byFlag.get(6) + byFlag.get(10)) % 64 == 0) {
                StackTraceElement[] stack = new Throwable().getStackTrace();
                StringBuilder from = new StringBuilder().append(flags);
                for (int i = 2; i < Math.min(stack.length, 7); i++) {
                    from.append(" < ").append(stack[i].getClassName().replace("zombie.", "")).append('.').append(stack[i].getMethodName());
                }
                callers.computeIfAbsent(from.toString(), unused -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
            }
        }
    }

    /**
     * Called by the game where it gives an object another sprite without marking the chunk:
     * a window smashed, opened or shut. The game draws such an object afresh on every frame
     * and has nothing to mark for it. See Agent.windowMarkPatches.
     */
    public static void changed(IsoObject object) {
        IsoGridSquare square = object.getSquare();
        IsoChunk chunk = square == null ? null : square.getChunk();
        if (chunk != null) {
            things.getAndIncrement(slot(chunk.wx, chunk.wy));
            structures.getAndIncrement(slot(chunk.wx, chunk.wy));
        }
    }

    static int slot(int wx, int wy) {
        return (wx & (SIDE - 1)) * SIDE + (wy & (SIDE - 1));
    }

    /** Logic thread: the build of the chunk at (wx, wy) is to be made again. */
    static void tell(int wx, int wy) {
        told[slot(wx, wy)]++;
    }

    /** The same for every chunk from (minWx, minWy) to (maxWx, maxWy). */
    static void tell(int minWx, int minWy, int maxWx, int maxWy) {
        for (int wx = minWx; wx <= maxWx; wx++) {
            for (int wy = minWy; wy <= maxWy; wy++) {
                told[slot(wx, wy)]++;
            }
        }
    }

    /**
     * The marks for objects that may be part of a building on the chunks from (minWx, minWy)
     * to (maxWx, maxWy), as one number that is another once any of the chunks is marked so again.
     */
    static int structuresIn(int minWx, int minWy, int maxWx, int maxWy) {
        int sum = 0;
        for (int wx = minWx; wx <= maxWx; wx++) {
            for (int wy = minWy; wy <= maxWy; wy++) {
                sum += structures.get(slot(wx, wy));
            }
        }
        return sum;
    }

    /** Every mark on a chunk and the eight round it, light among them, as one number that is another once any is marked again. */
    static int around(int wx, int wy) {
        int sum = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int slot = slot(wx + dx, wy + dy);
                sum += things.get(slot) + lights.get(slot);
            }
        }
        return sum;
    }

    /** How many times the chunk has been told. */
    static int told(int wx, int wy) {
        return told[slot(wx, wy)];
    }

    /** Dev only: the marks by flag since the last asking. */
    static String counted() {
        StringBuilder all = new StringBuilder();
        for (int bit = 0; bit < 16; bit++) {
            long count = byFlag.getAndSet(bit, 0L);
            if (count != 0L) {
                all.append(' ').append(1 << bit).append(':').append(count);
            }
        }
        long unloaded = byFlag.getAndSet(UNLOADED, 0L);
        all.append(" of chunks not yet loaded ").append(unloaded);
        callers.entrySet().stream().sorted((a, b) -> b.getValue().get() - a.getValue().get()).limit(6)
                .forEach(each -> all.append(" | ").append(each.getValue().get()).append("x ").append(each.getKey()));
        callers.clear();
        return all.toString();
    }
}

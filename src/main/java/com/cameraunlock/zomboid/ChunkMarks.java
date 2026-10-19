package com.cameraunlock.zomboid;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
 *
 * The game says which level a light it marks is on, and a count is kept for each level too:
 * a chunk of a tall building is thirty levels, and the light of one of them changing is no
 * reason to read the other twenty-nine.
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
    /** The levels a light may be marked on by its level: the game's map has none lower or higher. */
    private static final int LEVELS = 64, LOWEST_LEVEL = -32;
    /** The marks for light by the level they were on, and those that came with no level or one outside the levels counted. */
    private static final AtomicIntegerArray litLevels = new AtomicIntegerArray(SIDE * SIDE * LEVELS), litLoose = new AtomicIntegerArray(SIDE * SIDE);
    /**
     * The marks for an object by the level they were on, and those that came with no level or
     * one outside the levels counted. A vine growing on the ground had every level of a tower
     * the chunk is part of read again for the finish of its walls.
     */
    private static final AtomicIntegerArray builtLevels = new AtomicIntegerArray(SIDE * SIDE * LEVELS), builtLoose = new AtomicIntegerArray(SIDE * SIDE);
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
    private static final Set<String> threads = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, AtomicInteger> callers = new ConcurrentHashMap<>();
    /**
     * Whether the game is telling: the three patches are in its code and it keeps a texture for each
     * chunk, which is what it marks for. Set by WorldScene on the logic thread, and read there.
     */
    static boolean live;

    private ChunkMarks() {}

    /** Called by the game where it marks a level of a chunk's texture as to be drawn again: see Agent.chunkMarkPatches. */
    public static void lit(FBORenderLevels levels, int level, long flags) {
        if ((flags & LIGHTING) != 0L) {
            marked(levels.getChunk(), LIGHTING, level, LOWEST_LEVEL - 1);
        }
    }

    /** Called by the game where something on a chunk has changed on all of its levels. */
    public static void marked(IsoChunk chunk, long flags) {
        marked(chunk, flags, LOWEST_LEVEL - 1, LOWEST_LEVEL - 1);
    }

    /** Called by the game where something on a level of a chunk has changed. Its light is counted as marked with no level: the game marks that by level apart (lit). */
    public static void marked(IsoChunk chunk, int level, long flags) {
        marked(chunk, flags, LOWEST_LEVEL - 1, level);
    }

    private static void marked(IsoChunk chunk, long flags, int level, int builtLevel) {
        if ((flags & ~NOT_BUILT_FROM) == 0L || hushed == Thread.currentThread()) {
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
            built(slot, builtLevel);
        }
        if ((flags & LIGHTING) != 0L) {
            lit(slot, level);
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
                callers.computeIfAbsent(from.toString(), unused -> new AtomicInteger()).incrementAndGet();
            }
        }
    }

    /** The thread a mark the game makes for nothing a build is made from is being made on, while it is made: see hush. */
    private static volatile Thread hushed;

    /**
     * In place of the game's marking an object's chunk for a change only its own view draws: a
     * thing shaken as it is struck (IsoObject.setRenderEffect, removeRenderEffect). The game
     * still marks its own texture of the chunk. A door struck by a crowd shakes for as long
     * as it is struck, and every mark had the building it stands in read again.
     */
    public static void hush(IsoObject object, long flags) {
        hushed = Thread.currentThread();
        try {
            object.invalidateRenderChunkLevel(flags);
        } finally {
            hushed = null;
        }
    }

    /** Counts a mark for a thing on the chunk of a square that is no part of a building. */
    static void thing(IsoGridSquare square) {
        IsoChunk chunk = square == null ? null : square.getChunk();
        if (chunk != null) {
            things.getAndIncrement(slot(chunk.wx, chunk.wy));
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
            built(slot(chunk.wx, chunk.wy), square.getZ());
        }
    }

    /** Counts a mark for the light of the chunk in a place of the table, on a level or, given one outside the levels counted, on none. */
    static void lit(int slot, int level) {
        if (level >= LOWEST_LEVEL && level < LOWEST_LEVEL + LEVELS) {
            litLevels.getAndIncrement(slot * LEVELS + level - LOWEST_LEVEL);
        } else {
            litLoose.getAndIncrement(slot);
        }
        lights.getAndIncrement(slot);
    }

    private static void built(int slot, int level) {
        if (level >= LOWEST_LEVEL && level < LOWEST_LEVEL + LEVELS) {
            builtLevels.getAndIncrement(slot * LEVELS + level - LOWEST_LEVEL);
        } else {
            builtLoose.getAndIncrement(slot);
        }
        structures.getAndIncrement(slot);
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

    /** The same for the levels from fromZ to toZ alone, with those marked with no level. */
    static int structuresIn(int minWx, int minWy, int maxWx, int maxWy, int fromZ, int toZ) {
        int from = Math.max(fromZ, LOWEST_LEVEL) - LOWEST_LEVEL, to = Math.min(toZ, LOWEST_LEVEL + LEVELS - 1) - LOWEST_LEVEL;
        int sum = 0;
        for (int wx = minWx; wx <= maxWx; wx++) {
            for (int wy = minWy; wy <= maxWy; wy++) {
                int slot = slot(wx, wy);
                sum += builtLoose.get(slot);
                for (int level = from; level <= to; level++) {
                    sum += builtLevels.get(slot * LEVELS + level);
                }
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

    /** The marks for light on one level of a chunk and of the eight round it, as one number that is another once any is marked so again. */
    static int litAround(int wx, int wy, int level) {
        int sum = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                sum += litLevels.get(slot(wx + dx, wy + dy) * LEVELS + level - LOWEST_LEVEL);
            }
        }
        return sum;
    }

    /** The marks for light that named no level, on a chunk and the eight round it. */
    static int litLooseAround(int wx, int wy) {
        return sumAround(litLoose, wx, wy);
    }

    /** The marks on a chunk and the eight round it for anything but light, as one number that is another once any is marked so again. */
    static int thingsAround(int wx, int wy) {
        return sumAround(things, wx, wy);
    }

    private static int sumAround(AtomicIntegerArray counts, int wx, int wy) {
        int sum = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                sum += counts.get(slot(wx + dx, wy + dy));
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
        // Each count is taken once before the sort: another thread may raise one while it runs, and a sort whose order changes under it throws.
        callers.entrySet().stream().map(each -> Map.entry(each.getKey(), each.getValue().get()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(6)
                .forEach(each -> all.append(" | ").append(each.getValue()).append("x ").append(each.getKey()));
        callers.clear();
        return all.toString();
    }
}

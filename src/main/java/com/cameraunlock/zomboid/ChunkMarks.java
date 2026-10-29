package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;
import zombie.iso.IsoChunk;
import zombie.iso.IsoDirections;
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
    /** The levels an object may be marked on by its level: the game's map has none lower or higher. */
    private static final int LEVELS = 64, LOWEST_LEVEL = -32;
    /**
     * The marks for an object by the level they were on, and those that came with no level or
     * one outside the levels counted. A vine growing on the ground had every level of a tower
     * the chunk is part of read again for the finish of its walls.
     */
    private static final AtomicIntegerArray builtLevels = new AtomicIntegerArray(SIDE * SIDE * LEVELS), builtLoose = new AtomicIntegerArray(SIDE * SIDE);
    /**
     * Raised by the mod itself, on the logic thread alone, for a chunk whose builds were told
     * something that is now otherwise and that no reading of the chunk would show: how a roof
     * was fitted, what a building's walls are finished in. Kept by the chunk and not by its
     * place in the table: a chunk off the map is told too, as a roof left behind is, and one
     * 64 chunks on from a loaded chunk shares that chunk's place. Each telling has a number
     * larger than any before it, and a chunk reads the number of the last telling of it.
     */
    private static final LongTable<Told> told = new LongTable<>();
    /**
     * By place in the table, what a chunk there reads once it is let go of, or if it never was
     * told: no less than the last telling of any chunk let go of in that place. So the number a
     * chunk reads never falls and rises whenever it is told, which is all a build remembering it
     * asks, whichever chunks are let go of.
     */
    private static final int[] letGoAt = new int[SIDE * SIDE];
    private static int tellings;
    /** By view, the chunks it keeps builds of as it last said: west, north and side, a side of 0 for none. */
    private static final int[] keptBy = new int[View.COUNT * 3];
    private static final ArrayList<Told> held = new ArrayList<>();

    private record Told(int wx, int wy, int telling) {}
    /** Dev only: how many marks came with each of the game's flags, by the flag's bit. */
    static final boolean COUNTED = Boolean.getBoolean("cameraunlock.zomboid.auditMarks");
    private static final AtomicLongArray byFlag = new AtomicLongArray(17);
    private static final int UNLOADED = 16;
    private static final Set<String> threads = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, AtomicInteger> callers = new ConcurrentHashMap<>();
    /**
     * Whether the game is telling: the four patches are in its code and it keeps a texture for each
     * chunk, which is what it marks for. Set by WorldScene on the logic thread, and read there.
     */
    static boolean live;

    private ChunkMarks() {}

    /**
     * Called by the game where it marks a level of a chunk's texture as to be drawn again: see
     * Agent.chunkMarkPatches. The light is counted for the chunk, whatever level it was marked on.
     */
    public static void lit(FBORenderLevels levels, int level, long flags) {
        if ((flags & LIGHTING) != 0L) {
            marked(levels.getChunk(), LIGHTING, LOWEST_LEVEL - 1);
        }
    }

    /**
     * Called by the game once it has loaded a chunk onto the map. What it put on the chunk as it
     * filled it was not counted (marked), and the game reuses its IsoChunk objects, so neither the
     * marks nor the object tell the chunk loaded again in a place from the one let go of there, which
     * may have changed while it was away. Counted as a mark with no level for what may be part of a
     * building, so that what is kept by those (structuresIn) is read again, and as one for what is on
     * the chunk, so that the builds of it and of the chunks round it are (around): one loaded again
     * with the same chunks round it kept its build until the sweep. See Agent.chunkMarkPatches.
     */
    public static void loaded(IsoChunk chunk) {
        loadingOn = null;
        loading = null;
        int slot = slot(chunk.wx, chunk.wy);
        things.getAndIncrement(slot);
        built(slot, LOWEST_LEVEL - 1);
    }

    /** The thread finishing the load of a chunk onto the map, while it does, and the chunk: see made. */
    private static volatile Thread loadingOn;
    private static IsoChunk loading;

    /** Called by the game as it begins to finish a chunk it is loading onto the map, before what made marks for it: see loaded. */
    public static void loading(IsoChunk chunk) {
        loading = chunk;
        loadingOn = Thread.currentThread();
    }

    /** Called by the game where something on a chunk has changed on all of its levels. */
    public static void marked(IsoChunk chunk, long flags) {
        marked(chunk, flags, LOWEST_LEVEL - 1);
    }

    /** Called by the game where something on a level of a chunk has changed. */
    public static void marked(IsoChunk chunk, int level, long flags) {
        marked(chunk, flags, level);
    }

    private static void marked(IsoChunk chunk, long flags, int level) {
        if ((flags & ~NOT_BUILT_FROM) == 0L || hushed == Thread.currentThread()) {
            return;
        }
        // A chunk being filled as it loads is marked for every object put on it. The load is counted once it is done (loaded), for the chunk and the chunks round it.
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
            built(slot, level);
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
     * Logic thread: the light the mod works out for a square of the chunk at (wx, wy) is
     * otherwise, though the game has marked nothing there (TorchLess, RoomDaylight). Counted as
     * the game's light marks are, so the pages of the builds round the chunk are worked out again.
     */
    static void markLight(int wx, int wy) {
        lights.getAndIncrement(slot(wx, wy));
    }

    /** The same for every chunk from (minWx, minWy) to (maxWx, maxWy). */
    static void markLight(int minWx, int minWy, int maxWx, int maxWy) {
        for (int wx = minWx; wx <= maxWx; wx++) {
            for (int wy = minWy; wy <= maxWy; wy++) {
                lights.getAndIncrement(slot(wx, wy));
            }
        }
    }

    /**
     * Called by the game where it gives an object another sprite without marking the chunk:
     * a window smashed, opened or shut. The game draws such an object afresh on every frame
     * and has nothing to mark for it. See Agent.windowMarkPatches, and unmarkedChangePatches
     * for the rest.
     */
    public static void changed(IsoObject object) {
        changedOn(object.getSquare());
    }

    /** The same for what is on a square: the graffiti a story sprays on a wall. */
    public static void changedOn(IsoGridSquare square) {
        IsoChunk chunk = square == null ? null : square.getChunk();
        if (chunk != null) {
            things.getAndIncrement(slot(chunk.wx, chunk.wy));
            built(slot(chunk.wx, chunk.wy), square.getZ());
        }
    }

    /** Called by the game as it stamps dirt on a square's floor: it changes the floors of the four squares beside it too. */
    public static void stamped(IsoGridSquare square) {
        changedOn(square);
        changedOn(square.getAdjacentSquare(IsoDirections.N));
        changedOn(square.getAdjacentSquare(IsoDirections.S));
        changedOn(square.getAdjacentSquare(IsoDirections.E));
        changedOn(square.getAdjacentSquare(IsoDirections.W));
    }

    /**
     * Called by the game as it puts a square on the map where there was none (IsoCell.ConnectNewSquare),
     * which it marks nothing for. The build cursor makes one where its level has none, and a chunk that
     * loads puts squares on the edge of each loaded chunk beside it, by every square of its own edge that
     * holds something (IsoChunk.loadInMainThread). A square with nothing on it is not read as no square:
     * RoomMap reads one beside a tall hall's upper room as the hall's (TallHalls.floorOf), and a roof
     * stops looking along its ridge at a building's level where the map has none (Roofs.beyondRidge). One
     * put on a chunk still being filled is counted with its load (loaded). One put within a square of a
     * chunk being loaded, as those the load puts on the edges of the chunks beside it are, is marked only
     * for what may be built on it: every chunk build that reads it is round the chunk loaded and has that
     * load's own mark, while a mark for what is on it would have the chunks round its own chunk, two from
     * the load, summed again for a square none of them reads.
     */
    public static void made(IsoGridSquare square) {
        IsoChunk chunk = square.getChunk();
        if (chunk == null || !chunk.loaded) {
            return;
        }
        boolean besideTheLoad = false;
        if (loadingOn == Thread.currentThread()) {
            int fromX = loading.wx * WorldScene.CHUNK_TILES, fromY = loading.wy * WorldScene.CHUNK_TILES;
            besideTheLoad = square.getX() >= fromX - 1 && square.getX() <= fromX + WorldScene.CHUNK_TILES
                    && square.getY() >= fromY - 1 && square.getY() <= fromY + WorldScene.CHUNK_TILES;
        }
        if (besideTheLoad) {
            built(slot(chunk.wx, chunk.wy), square.getZ());
        } else {
            changedOn(square);
        }
    }

    /** Called by the game as it lights a hearth or a barbecue, or puts it out, with whether it was lit and is to be: it marks neither. */
    public static void burning(IsoObject object, boolean was, boolean now) {
        if (was != now) {
            thing(object.getSquare());
        }
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
        tell(wx, wy, wx, wy);
    }

    /** The same for every chunk from (minWx, minWy) to (maxWx, maxWy). */
    static void tell(int minWx, int minWy, int maxWx, int maxWy) {
        int telling = ++tellings;
        for (int wx = minWx; wx <= maxWx; wx++) {
            for (int wy = minWy; wy <= maxWy; wy++) {
                told.put(chunkKey(wx, wy), new Told(wx, wy, telling));
            }
        }
    }

    /**
     * Logic thread: the view keeps builds of the chunks from (west, north), side chunks a side,
     * and of no others. Lets go of the tellings of every chunk no view keeps. Where that raises
     * what its place reads, a chunk some view keeps in the same place that read the old number
     * is first given that number of its own, so that its builds are not made again for it.
     */
    static void keepTellings(int view, int west, int north, int side) {
        if (side > SIDE) {
            throw new IllegalStateException("a view keeps builds of " + side + " chunks a side, and the table of tellings has " + SIDE + ": two it keeps would share a place");
        }
        keptBy[view * 3] = west;
        keptBy[view * 3 + 1] = north;
        keptBy[view * 3 + 2] = side;
        told.addValuesTo(held);
        told.clear();
        for (Told each : held) {
            if (kept(each.wx, each.wy)) {
                told.put(chunkKey(each.wx, each.wy), each);
            }
        }
        for (Told each : held) {
            int slot = slot(each.wx, each.wy);
            if (!kept(each.wx, each.wy) && each.telling - letGoAt[slot] > 0) {
                holdWhatIsRead(slot);
                letGoAt[slot] = each.telling;
            }
        }
        held.clear();
    }

    private static boolean kept(int wx, int wy) {
        for (int at = 0; at < keptBy.length; at += 3) {
            int dx = wx - keptBy[at], dy = wy - keptBy[at + 1], side = keptBy[at + 2];
            if (dx >= 0 && dx < side && dy >= 0 && dy < side) {
                return true;
            }
        }
        return false;
    }

    /** Gives the chunk each view keeps in the place, if it has none, a telling of its own with the number it reads now. */
    private static void holdWhatIsRead(int slot) {
        for (int at = 0; at < keptBy.length; at += 3) {
            int west = keptBy[at], north = keptBy[at + 1], side = keptBy[at + 2];
            int dx = slot / SIDE - west & SIDE - 1, dy = slot % SIDE - north & SIDE - 1;
            if (dx < side && dy < side) {
                int wx = west + dx, wy = north + dy;
                long key = chunkKey(wx, wy);
                if (told.get(key) == null) {
                    told.put(key, new Told(wx, wy, letGoAt[slot]));
                }
            }
        }
    }

    private static long chunkKey(int wx, int wy) {
        return (long) wx << 32 | wy & 0xffffffffL;
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

    /** The marks on a box of chunks that may be part of a building and were marked with no level. */
    static int structuresLoose(int minWx, int minWy, int maxWx, int maxWy) {
        int sum = 0;
        for (int wx = minWx; wx <= maxWx; wx++) {
            for (int wy = minWy; wy <= maxWy; wy++) {
                sum += builtLoose.get(slot(wx, wy));
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

    /** The number of the last telling of the chunk, or what its place reads: see letGoAt. */
    static int told(int wx, int wy) {
        Told last = told.get(chunkKey(wx, wy));
        return last == null ? letGoAt[slot(wx, wy)] : last.telling;
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

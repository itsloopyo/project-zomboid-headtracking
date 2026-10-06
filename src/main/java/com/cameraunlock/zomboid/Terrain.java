package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import zombie.iso.BuildingDef;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.areas.IsoBuilding;
import zombie.iso.sprite.IsoSprite;

/**
 * The lie of the land in the first-person picture. The game's map is flat: a river is level
 * with its banks and a town is a table. Seen from above that is how a map reads. Seen from eye
 * height it is a board, so the picture gives the land a height the game does not have, by the
 * rules land keeps:
 *
 * <ul>
 * <li>The country rises and falls over hundreds of paces, and hills stand on it. A road climbs
 * a hill at a grade a car can take, because the hills are that gentle.</li>
 * <li>Water lies low. Hills only ever rise from the base of the country, and they die away
 * towards water over the width of a valley's side, so a pond or a river is in the bottom of
 * its valley, and its banks fall to it.</li>
 * <li>A building stands on a level pad at the height the land has in the middle of it, and the
 * ground round it is graded to meet the pad.</li>
 * <li>A street is level from one side to the other. It rises and falls along its length with
 * the country and the hills, at the height the land has down the middle of it, and the ground
 * beside it is graded to meet it. It takes nothing from what stands beside it: the grade from
 * a building's pad is done by the time it reaches the street.</li>
 * <li>Open ground has rises and hollows and is uneven underfoot. Made ground, a road or a
 * yard, is cut level through it.</li>
 * </ul>
 *
 * Only the picture. The game's own positions, paths and collisions stay on the flat map, and
 * everything drawn, on every floor, the eye included, is lifted by the height of the ground
 * under it.
 *
 * Heights are kept at the corners of the squares, a chunk at a time. The logic thread works
 * them out and both threads read them.
 */
final class Terrain {
    static final int CHUNK_TILES = 8;
    static final int CORNERS = CHUNK_TILES + 1;
    private static final int TABLE_SIDE = 64;
    /** How far from made ground, a building or water its hold on the height of the land reaches, in tiles. */
    static final int REACH = 6;
    /** How far below the land round it water lies, and how far from it the bank starts to fall, in tiles. */
    private static final float WATER_BELOW = 0.34f;
    private static final float BANK_WIDTH = 2.6f;
    /**
     * A street is laid at the height the land has, taken over the street within this many tiles
     * either way. No less than a street is wide, so that both its sides take it over the same squares.
     */
    static final int LEVELLED = 8;
    static final int SPAN = CHUNK_TILES + 2 * LEVELLED;

    static final byte NATURAL = 0, MADE = 1, WATER = 2, UNKNOWN = 3, STREET = 4;
    /** How far a street lies below the ground beside it: the height of a kerb, in tiles. */
    static final float KERB = 0.11f;

    private static final class Grid {
        int wx, wy;
        boolean settled;
        /** The frame it was worked out in. */
        int stamp;
        final float[] heights = new float[CORNERS * CORNERS];
        /** The height of water at each corner, which where a quay meets it is not the land's. */
        final float[] water = new float[CORNERS * CORNERS];
        /** Where the kerbs of the chunk's streets run, as Kerbs keeps it. Null where it has no street. */
        float[] kerb;
        /** What the squares the kerbs were worked out from are, and the kerbs, each as one number. */
        long kerbFrom, kerbSum;
    }

    private static final class Kinds {
        int wx, wy;
        final byte[] kinds = new byte[CHUNK_TILES * CHUNK_TILES];
        /** The middle of the building each square is in. NaN where there is none. */
        final float[] midX = new float[CHUNK_TILES * CHUNK_TILES], midY = new float[CHUNK_TILES * CHUNK_TILES];
        boolean water;
    }

    private static final AtomicReferenceArray<Grid> grids = new AtomicReferenceArray<>(TABLE_SIDE * TABLE_SIDE);
    private static final Kinds[] known = new Kinds[TABLE_SIDE * TABLE_SIDE];
    private static final Kinds passing = new Kinds();
    static final byte[] around = new byte[SPAN * SPAN];
    static final float[] padsAround = new float[SPAN * SPAN];
    /** The height of the land in the middle of each square of street of around. */
    private static final float[] streetAround = new float[SPAN * SPAN];
    private static final float[] midXAround = new float[SPAN * SPAN], midYAround = new float[SPAN * SPAN];
    /** The height of the pad of each building whose pad can no longer change, by the middle of the building. */
    private static final HashMap<Long, Float> pads = new HashMap<>();
    private static final byte[] kerbKinds = new byte[Kerbs.SPAN * Kerbs.SPAN];
    private static final boolean[] kerbHard = new boolean[Kerbs.SPAN * Kerbs.SPAN];
    /** How far from water the hills take to rise to their height, in chunks and in tiles: the width of a valley's side. */
    private static final int VALLEY_CHUNKS = 4;
    private static final float VALLEY = VALLEY_CHUNKS * CHUNK_TILES;
    /** How far round a chunk water is looked for, in chunks: a valley's width from anywhere within LEVELLED of it. */
    private static final int WATER_CHUNKS = VALLEY_CHUNKS + (LEVELLED + CHUNK_TILES - 1) / CHUNK_TILES;
    private static final int[] waterChunkX = new int[(2 * WATER_CHUNKS + 1) * (2 * WATER_CHUNKS + 1)];
    private static final int[] waterChunkY = new int[waterChunkX.length];
    private static int waterChunks;

    /**
     * A chunk within a valley's width of ground that is not loaded has heights that may yet
     * change. They are worked out again this many frames on, not at every asking: a build asks
     * up to three times, and most of the chunks in sight are that near the map's edge.
     */
    private static final int RETRIED = 30;
    private static int frame;

    /** Logic thread, once a frame the picture is built. */
    static void frame(int number) {
        frame = number;
    }

    private Terrain() {}

    private static int slot(int wx, int wy) {
        return (wx & (TABLE_SIDE - 1)) * TABLE_SIDE + (wy & (TABLE_SIDE - 1));
    }

    /** The height of the ground at (x, y) above the flat map, in tiles. 0 where it is not known. */
    static float height(float x, float y) {
        int tileX = (int) Math.floor(x), tileY = (int) Math.floor(y);
        int wx = Math.floorDiv(tileX, CHUNK_TILES), wy = Math.floorDiv(tileY, CHUNK_TILES);
        Grid grid = grids.get(slot(wx, wy));
        if (grid == null || grid.wx != wx || grid.wy != wy) {
            return 0f;
        }
        int i = tileX - wx * CHUNK_TILES, j = tileY - wy * CHUNK_TILES;
        float a = x - tileX, b = y - tileY;
        float[] h = grid.heights;
        int at = i * CORNERS + j;
        return (h[at] * (1f - a) + h[at + CORNERS] * a) * (1f - b) + (h[at + 1] * (1f - a) + h[at + CORNERS + 1] * a) * b
                - (grid.kerb != null && Kerbs.at(grid.kerb, x - wx * CHUNK_TILES, y - wy * CHUNK_TILES) > 0f ? KERB : 0f);
    }

    /** Where the kerbs of a chunk run, as Kerbs keeps it, and null where the chunk has no street. After corners. */
    static float[] kerb(int wx, int wy) {
        return grids.get(slot(wx, wy)).kerb;
    }

    /** The kerbs of a chunk as one number, which is another when they run otherwise. After corners. */
    static long kerbSum(int wx, int wy) {
        return grids.get(slot(wx, wy)).kerbSum;
    }

    /** The height of water at the corners of a chunk, laid out as corners gives the land's. After corners. */
    static float[] water(int wx, int wy) {
        return grids.get(slot(wx, wy)).water;
    }

    /**
     * The corner heights of a chunk, CORNERS by CORNERS with x first, worked out if they are
     * not yet or if the ground round the chunk was not all loaded when they last were. Logic
     * thread.
     */
    static float[] corners(IsoCell cell, int wx, int wy) {
        int slot = slot(wx, wy);
        Grid grid = grids.get(slot);
        if (grid != null && grid.wx == wx && grid.wy == wy && (grid.settled || frame - grid.stamp < RETRIED)) {
            return grid.heights;
        }
        Grid fresh = new Grid();
        fresh.wx = wx;
        fresh.wy = wy;
        fresh.stamp = frame;
        // In this order: gather looks for the water round each building, and the corners want the water round the chunk.
        fresh.settled = gather(cell, wx, wy) & findWater(cell, wx, wy);
        streets(wx * CHUNK_TILES - LEVELLED, wy * CHUNK_TILES - LEVELLED);
        for (int i = 0; i < CORNERS; i++) {
            for (int j = 0; j < CORNERS; j++) {
                float x = wx * CHUNK_TILES + i, y = wy * CHUNK_TILES + j;
                fresh.heights[i * CORNERS + j] = corner(x, y, i + LEVELLED, j + LEVELLED);
                fresh.water[i * CORNERS + j] = base(x, y) - WATER_BELOW;
            }
        }
        kerbs(cell, fresh, grid != null && grid.wx == wx && grid.wy == wy ? grid : null);
        grids.set(slot, fresh);
        return fresh.heights;
    }

    /**
     * Works out where the kerbs of a chunk run. After gather. The line of a kerb is shaped by
     * the squares a long way along it, so it is only worked out anew when one of those has
     * changed since it last was.
     */
    private static void kerbs(IsoCell cell, Grid fresh, Grid last) {
        boolean street = false;
        for (int i = LEVELLED - 1; i <= LEVELLED + CHUNK_TILES && !street; i++) {
            for (int j = LEVELLED - 1; j <= LEVELLED + CHUNK_TILES && !street; j++) {
                street = around[i * SPAN + j] == STREET;
            }
        }
        if (!street) {
            return;
        }
        int reach = Kerbs.MARGIN / CHUNK_TILES;
        long from = 0x9E3779B97F4A7C15L;
        for (int cx = -reach; cx <= reach; cx++) {
            for (int cy = -reach; cy <= reach; cy++) {
                Kinds kinds = known[slot(fresh.wx + cx, fresh.wy + cy)];
                if (kinds == null || kinds.wx != fresh.wx + cx || kinds.wy != fresh.wy + cy) {
                    kinds = cell.getGridSquare((fresh.wx + cx) * CHUNK_TILES + CHUNK_TILES / 2, (fresh.wy + cy) * CHUNK_TILES + CHUNK_TILES / 2, 0) == null
                            ? null : kinds(cell, fresh.wx + cx, fresh.wy + cy);
                }
                for (int i = 0; i < CHUNK_TILES; i++) {
                    for (int j = 0; j < CHUNK_TILES; j++) {
                        int at = ((cx + reach) * CHUNK_TILES + i) * Kerbs.SPAN + (cy + reach) * CHUNK_TILES + j;
                        kerbKinds[at] = kinds == null ? UNKNOWN : kinds.kinds[i * CHUNK_TILES + j];
                        kerbHard[at] = kinds != null && !Float.isNaN(kinds.midX[i * CHUNK_TILES + j]);
                        from = (from ^ kerbKinds[at] + (kerbHard[at] ? 8 : 0)) * 0x100000001B3L;
                    }
                }
            }
        }
        fresh.kerbFrom = from;
        if (last != null && last.kerbFrom == from) {
            fresh.kerb = last.kerb;
            fresh.kerbSum = last.kerbSum;
            return;
        }
        fresh.kerb = Kerbs.field(kerbKinds, kerbHard, fresh.wx * CHUNK_TILES, fresh.wy * CHUNK_TILES);
        if (fresh.kerb != null) {
            long sum = 0x9E3779B97F4A7C15L;
            for (float value : fresh.kerb) {
                sum = (sum ^ Float.floatToRawIntBits(value)) * 0x100000001B3L;
            }
            fresh.kerbSum = sum;
        }
    }

    /** Fills streetAround, for an around whose first square is (x, y) of the map. After findWater. */
    static void streets(int x, int y) {
        for (int i = 0; i < SPAN; i++) {
            for (int j = 0; j < SPAN; j++) {
                if (around[i * SPAN + j] == STREET) {
                    streetAround[i * SPAN + j] = base(x + i + 0.5f, y + j + 0.5f);
                }
            }
        }
    }

    /** The height of the corner at (x, y) of the map, which is the corner (cornerI, cornerJ) of around. After streets. */
    static float corner(float x, float y, int cornerI, int cornerJ) {
        float toMade = REACH, toWater = REACH, toPad = REACH, toBuilt = REACH, toStreet = REACH;
        float touchingPads = 0f, nearPads = 0f, nearWeight = 0f;
        int touching = 0;
        for (int di = -REACH; di < REACH; di++) {
            for (int dj = -REACH; dj < REACH; dj++) {
                int at = (cornerI + di) * SPAN + cornerJ + dj;
                byte kind = around[at];
                if (kind == NATURAL) {
                    continue;
                }
                float awayI = di >= 0 ? di : -di - 1, awayJ = dj >= 0 ? dj : -dj - 1;
                float away = (float) Math.sqrt(awayI * awayI + awayJ * awayJ);
                toMade = Math.min(toMade, away);
                if (kind == WATER) {
                    toWater = Math.min(toWater, away);
                } else {
                    toBuilt = Math.min(toBuilt, away);
                }
                if (kind == STREET) {
                    toStreet = Math.min(toStreet, away);
                }
                float pad = padsAround[at];
                if (!Float.isNaN(pad) && away < REACH) {
                    toPad = Math.min(toPad, away);
                    if (away == 0f) {
                        touchingPads += pad;
                        touching++;
                    }
                    float weight = 1f - away / REACH;
                    weight *= weight * weight * weight;
                    nearPads += pad * weight;
                    nearWeight += weight;
                }
            }
        }
        if (touching > 0) {
            return touchingPads / touching;
        }
        float land = base(x, y);
        if (toStreet < REACH) {
            // In the same order from whichever chunk asks, so that two chunks give the corner they share one height.
            float streets = 0f;
            int count = 0;
            for (int di = -LEVELLED; di < LEVELLED; di++) {
                for (int dj = -LEVELLED; dj < LEVELLED; dj++) {
                    int at = (cornerI + di) * SPAN + cornerJ + dj;
                    if (around[at] == STREET) {
                        streets += streetAround[at];
                        count++;
                    }
                }
            }
            land += (streets / count - land) * (1f - smooth(toStreet / REACH));
        }
        land += (knolls(x, y) + roll(x, y)) * smooth(toMade / REACH)
                // A bank falls to the water. A quay or a boardwalk does not: it runs level to its
                // edge and the water lies below it.
                - WATER_BELOW * (1f - smooth(toWater / BANK_WIDTH)) * smooth(toBuilt / 2f);
        if (nearWeight == 0f) {
            return land;
        }
        float pad = nearPads / nearWeight;
        return pad + (land - pad) * smooth(toPad / Math.min(REACH, toPad + toStreet));
    }

    /**
     * Fills around and padsAround with what each square within LEVELLED of the chunk is. False if
     * some of it is not loaded, which counts as made ground, or if a pad may yet change.
     */
    private static boolean gather(IsoCell cell, int wx, int wy) {
        boolean all = true;
        int reach = (LEVELLED + CHUNK_TILES - 1) / CHUNK_TILES;
        for (int cx = -reach; cx <= reach; cx++) {
            for (int cy = -reach; cy <= reach; cy++) {
                Kinds kinds = kinds(cell, wx + cx, wy + cy);
                for (int i = 0; i < CHUNK_TILES; i++) {
                    int row = cx * CHUNK_TILES + i + LEVELLED;
                    if (row < 0 || row >= SPAN) {
                        continue;
                    }
                    for (int j = 0; j < CHUNK_TILES; j++) {
                        int column = cy * CHUNK_TILES + j + LEVELLED;
                        if (column < 0 || column >= SPAN) {
                            continue;
                        }
                        byte kind = kinds.kinds[i * CHUNK_TILES + j];
                        all &= kind != UNKNOWN;
                        around[row * SPAN + column] = kind;
                        midXAround[row * SPAN + column] = kinds.midX[i * CHUNK_TILES + j];
                        midYAround[row * SPAN + column] = kinds.midY[i * CHUNK_TILES + j];
                    }
                }
            }
        }
        // Only now: finding a pad looks at other chunks, and what a chunk not all loaded is made of is kept in one place.
        float lastX = Float.NaN, lastY = Float.NaN, lastPad = Float.NaN;
        for (int at = 0; at < midXAround.length; at++) {
            float x = midXAround[at], y = midYAround[at];
            if (Float.isNaN(x)) {
                padsAround[at] = Float.NaN;
                continue;
            }
            if (x != lastX || y != lastY) {
                long key = (long) Float.floatToRawIntBits(x) << 32 | Float.floatToRawIntBits(y) & 0xFFFFFFFFL;
                Float kept = pads.get(key);
                if (kept == null) {
                    boolean lasting = findWater(cell, Math.floorDiv((int) Math.floor(x), CHUNK_TILES), Math.floorDiv((int) Math.floor(y), CHUNK_TILES));
                    kept = base(x, y);
                    if (lasting) {
                        pads.put(key, kept);
                    }
                    all &= lasting;
                }
                lastX = x;
                lastY = y;
                lastPad = kept;
            }
            padsAround[at] = lastPad;
        }
        return all;
    }

    /**
     * Notes which chunks within WATER_CHUNKS of the chunk hold water. False if some of them are
     * not loaded, and so not known.
     */
    private static boolean findWater(IsoCell cell, int wx, int wy) {
        boolean all = true;
        waterChunks = 0;
        for (int cx = -WATER_CHUNKS; cx <= WATER_CHUNKS; cx++) {
            for (int cy = -WATER_CHUNKS; cy <= WATER_CHUNKS; cy++) {
                Kinds kept = known[slot(wx + cx, wy + cy)];
                if (kept == null || kept.wx != wx + cx || kept.wy != wy + cy) {
                    // A chunk with its middle square missing is not loaded, and looking over
                    // the rest of it would find nothing to keep.
                    kept = cell.getGridSquare((wx + cx) * CHUNK_TILES + CHUNK_TILES / 2, (wy + cy) * CHUNK_TILES + CHUNK_TILES / 2, 0) == null
                            ? null : kinds(cell, wx + cx, wy + cy);
                    if (kept == null || kept == passing) {
                        all = false;
                        continue;
                    }
                }
                if (kept.water) {
                    waterChunkX[waterChunks] = (wx + cx) * CHUNK_TILES;
                    waterChunkY[waterChunks++] = (wy + cy) * CHUNK_TILES;
                }
            }
        }
        return all;
    }

    /** How far (x, y) is from the nearest chunk findWater found water in, in tiles, and VALLEY if from none. */
    private static float toWaterChunk(float x, float y) {
        float nearest = VALLEY;
        for (int i = 0; i < waterChunks; i++) {
            float awayX = Math.max(0f, Math.max(waterChunkX[i] - x, x - waterChunkX[i] - CHUNK_TILES));
            float awayY = Math.max(0f, Math.max(waterChunkY[i] - y, y - waterChunkY[i] - CHUNK_TILES));
            nearest = Math.min(nearest, (float) Math.sqrt(awayX * awayX + awayY * awayY));
        }
        return nearest;
    }

    /** What each square of a chunk is. Kept once every square of the chunk has been seen loaded. */
    private static Kinds kinds(IsoCell cell, int wx, int wy) {
        int slot = slot(wx, wy);
        Kinds kept = known[slot];
        if (kept != null && kept.wx == wx && kept.wy == wy) {
            return kept;
        }
        boolean whole = true, water = false;
        for (int i = 0; i < CHUNK_TILES; i++) {
            for (int j = 0; j < CHUNK_TILES; j++) {
                IsoGridSquare square = cell.getGridSquare(wx * CHUNK_TILES + i, wy * CHUNK_TILES + j, 0);
                byte kind = kind(square);
                whole &= kind != UNKNOWN;
                passing.kinds[i * CHUNK_TILES + j] = kind;
                water |= kind == WATER;
                IsoBuilding building = square == null ? null : square.getBuilding();
                BuildingDef def = building == null ? null : building.getDef();
                passing.midX[i * CHUNK_TILES + j] = def == null ? Float.NaN : def.getX() + def.getW() / 2f;
                passing.midY[i * CHUNK_TILES + j] = def == null ? Float.NaN : def.getY() + def.getH() / 2f;
            }
        }
        if (!whole) {
            return passing;
        }
        Kinds fresh = new Kinds();
        fresh.wx = wx;
        fresh.wy = wy;
        fresh.water = water;
        System.arraycopy(passing.kinds, 0, fresh.kinds, 0, fresh.kinds.length);
        System.arraycopy(passing.midX, 0, fresh.midX, 0, fresh.midX.length);
        System.arraycopy(passing.midY, 0, fresh.midY, 0, fresh.midY.length);
        known[slot] = fresh;
        return fresh;
    }

    static byte kind(IsoGridSquare square) {
        return square == null ? UNKNOWN : kind(square, square.getFloor());
    }

    /** The same of a square whose floor the caller has already asked the game for: the asking is a walk through the square's objects. */
    static byte kind(IsoGridSquare square, IsoObject floor) {
        IsoSprite sprite = floor == null ? null : floor.getSprite();
        if (sprite == null) {
            return MADE;
        }
        if (sprite.getProperties().has(IsoFlagType.water)) {
            return WATER;
        }
        String name = sprite.getName();
        if (name == null || !square.isOutside()) {
            return MADE;
        }
        return name.startsWith("blends_natural") ? NATURAL : name.startsWith("blends_street") ? STREET : MADE;
    }

    /**
     * Whether the country is left as level as the game's map is. Seen from overhead the picture
     * has to lie where the game's own does, or the pointer is not on what it seems to be on:
     * the game knows nothing of hills. Kerbs and banks, which are a hand high, stay.
     */
    static volatile boolean level;

    /** Sets whether the country is level, and lets go of every height worked out the other way. Logic thread. */
    static void level(boolean flat) {
        level = flat;
        for (int slot = 0; slot < grids.length(); slot++) {
            grids.set(slot, null);
        }
        Arrays.fill(known, null);
        pads.clear();
    }

    /** What is drawn at (x, y), on any floor, stands this much above where the flat map has it. */
    static float lift(float x, float y, float z) {
        return height(x, y);
    }

    private static float smooth(float t) {
        t = Math.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** The height of the land at (x, y) before what is uneven on open ground and what is built is on it. After findWater. */
    private static float base(float x, float y) {
        return country(x, y) + hills(x, y) * smooth(toWaterChunk(x, y) / VALLEY);
    }

    /** The rise and fall of the country over the better part of a mile. Never steeper than a rise of one in forty. */
    private static float country(float x, float y) {
        if (level) {
            return 0f;
        }
        return 2.2f * (float) (Math.sin(x * 0.0061 + 0.7) * Math.sin(y * 0.0053 + 2.2))
                + 1.4f * (float) Math.sin((x * 0.8 - y * 0.6) * 0.0087 + 4.0);
    }

    /**
     * The hills that stand on the country: broad, a few tiles high, and never below it. A road
     * over one climbs less than one in fifteen.
     */
    private static float hills(float x, float y) {
        if (level) {
            return 0f;
        }
        float broad = (float) (Math.sin(x * 0.015 + 1.1) * Math.sin(y * 0.018 + 0.3) + 0.6 * Math.sin((x + y) * 0.01 + 5.0));
        float ridge = (float) (Math.sin(x * 0.033 - y * 0.022 + 2.0) * Math.sin(y * 0.03 + x * 0.009 + 0.9));
        float up = Math.max(0f, 0.5f + 0.42f * broad + 0.16f * ridge);
        return 2.4f * up * up;
    }

    /**
     * The rises and hollows of open country, a stone's throw across and a man's height or so
     * from top to bottom. Made ground is cut level through them: a road runs between its banks.
     */
    private static float knolls(float x, float y) {
        if (level) {
            return 0f;
        }
        return 1.1f * (float) (Math.sin(x * 0.067 + 0.3) * Math.sin(y * 0.079 + 1.7))
                + 0.7f * (float) (Math.sin((x + y * 0.6) * 0.045 + 3.1) * Math.sin((y - x * 0.4) * 0.057 + 0.8));
    }

    /** What is uneven underfoot on open ground, the same wherever and whenever it is asked. */
    private static float roll(float x, float y) {
        if (level) {
            return 0f;
        }
        return 0.2f * (float) (Math.sin(x * 0.071 + 1.3) * Math.sin(y * 0.063 + 0.4))
                + 0.07f * (float) (Math.sin(x * 0.29 + y * 0.11) * Math.sin(y * 0.27 - x * 0.13));
    }
}

package com.cameraunlock.zomboid;

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
 * <li>Open ground is uneven underfoot. Made ground, a road or a yard, is smooth.</li>
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
    private static final int REACH = 6;
    /** How far below the land round it water lies, and how far from it the bank starts to fall, in tiles. */
    private static final float WATER_BELOW = 0.34f;
    private static final float BANK_WIDTH = 2.6f;
    private static final int SPAN = CHUNK_TILES + 2 * REACH;

    static final byte NATURAL = 0, MADE = 1, WATER = 2, UNKNOWN = 3;

    private static final class Grid {
        int wx, wy;
        boolean settled;
        final float[] heights = new float[CORNERS * CORNERS];
    }

    private static final class Kinds {
        int wx, wy;
        final byte[] kinds = new byte[CHUNK_TILES * CHUNK_TILES];
        /** The height of the pad of the building each square is in. NaN where there is none. */
        final float[] pads = new float[CHUNK_TILES * CHUNK_TILES];
        boolean water;
    }

    private static final AtomicReferenceArray<Grid> grids = new AtomicReferenceArray<>(TABLE_SIDE * TABLE_SIDE);
    private static final Kinds[] known = new Kinds[TABLE_SIDE * TABLE_SIDE];
    private static final Kinds passing = new Kinds();
    private static final byte[] around = new byte[SPAN * SPAN];
    private static final float[] padsAround = new float[SPAN * SPAN];
    /** How far from water the hills take to rise to their height, in chunks and in tiles: the width of a valley's side. */
    private static final int VALLEY_CHUNKS = 4;
    private static final float VALLEY = VALLEY_CHUNKS * CHUNK_TILES;
    private static final int[] waterChunkX = new int[(2 * VALLEY_CHUNKS + 1) * (2 * VALLEY_CHUNKS + 1)];
    private static final int[] waterChunkY = new int[waterChunkX.length];
    private static int waterChunks;

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
        return (h[at] * (1f - a) + h[at + CORNERS] * a) * (1f - b) + (h[at + 1] * (1f - a) + h[at + CORNERS + 1] * a) * b;
    }

    /**
     * The corner heights of a chunk, CORNERS by CORNERS with x first, worked out if they are
     * not yet or if the ground round the chunk was not all loaded when they last were. Logic
     * thread.
     */
    static float[] corners(IsoCell cell, int wx, int wy) {
        int slot = slot(wx, wy);
        Grid grid = grids.get(slot);
        if (grid != null && grid.wx == wx && grid.wy == wy && grid.settled) {
            return grid.heights;
        }
        Grid fresh = new Grid();
        fresh.wx = wx;
        fresh.wy = wy;
        fresh.settled = gather(cell, wx, wy) & findWater(cell, wx, wy);
        for (int i = 0; i < CORNERS; i++) {
            for (int j = 0; j < CORNERS; j++) {
                fresh.heights[i * CORNERS + j] = corner(wx * CHUNK_TILES + i, wy * CHUNK_TILES + j, i + REACH, j + REACH);
            }
        }
        grids.set(slot, fresh);
        return fresh.heights;
    }

    /** The height of the corner at (x, y) of the map, which is the corner (cornerI, cornerJ) of around. */
    private static float corner(float x, float y, int cornerI, int cornerJ) {
        float toMade = REACH, toWater = REACH, toPad = REACH;
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
        float land = country(x, y) + hills(x, y) * smooth(toWaterChunk(x, y) / VALLEY)
                + roll(x, y) * smooth(toMade / REACH) - WATER_BELOW * (1f - smooth(toWater / BANK_WIDTH));
        if (nearWeight == 0f) {
            return land;
        }
        float pad = nearPads / nearWeight;
        return pad + (land - pad) * smooth(toPad / REACH);
    }

    /**
     * Fills around and padsAround with what each square within REACH of the chunk is. False if
     * some of it is not loaded: that ground counts as made.
     */
    private static boolean gather(IsoCell cell, int wx, int wy) {
        boolean all = true;
        int reach = (REACH + CHUNK_TILES - 1) / CHUNK_TILES;
        for (int cx = -reach; cx <= reach; cx++) {
            for (int cy = -reach; cy <= reach; cy++) {
                Kinds kinds = kinds(cell, wx + cx, wy + cy);
                for (int i = 0; i < CHUNK_TILES; i++) {
                    int row = cx * CHUNK_TILES + i + REACH;
                    if (row < 0 || row >= SPAN) {
                        continue;
                    }
                    for (int j = 0; j < CHUNK_TILES; j++) {
                        int column = cy * CHUNK_TILES + j + REACH;
                        if (column < 0 || column >= SPAN) {
                            continue;
                        }
                        byte kind = kinds.kinds[i * CHUNK_TILES + j];
                        all &= kind != UNKNOWN;
                        around[row * SPAN + column] = kind;
                        padsAround[row * SPAN + column] = kinds.pads[i * CHUNK_TILES + j];
                    }
                }
            }
        }
        return all;
    }

    /**
     * Notes which chunks within VALLEY of the chunk hold water. False if some of them are not
     * loaded, and so not known.
     */
    private static boolean findWater(IsoCell cell, int wx, int wy) {
        boolean all = true;
        waterChunks = 0;
        for (int cx = -VALLEY_CHUNKS; cx <= VALLEY_CHUNKS; cx++) {
            for (int cy = -VALLEY_CHUNKS; cy <= VALLEY_CHUNKS; cy++) {
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
                passing.pads[i * CHUNK_TILES + j] = def == null ? Float.NaN : pad(def);
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
        System.arraycopy(passing.pads, 0, fresh.pads, 0, fresh.pads.length);
        known[slot] = fresh;
        return fresh;
    }

    /** The height a building's floor is at: the land's in the middle of it, without what is uneven underfoot. */
    private static float pad(BuildingDef def) {
        float x = def.getX() + def.getW() / 2f, y = def.getY() + def.getH() / 2f;
        return country(x, y) + hills(x, y);
    }

    static byte kind(IsoGridSquare square) {
        if (square == null) {
            return UNKNOWN;
        }
        IsoObject floor = square.getFloor();
        IsoSprite sprite = floor == null ? null : floor.getSprite();
        if (sprite == null) {
            return MADE;
        }
        if (sprite.getProperties().has(IsoFlagType.water)) {
            return WATER;
        }
        String name = sprite.getName();
        return square.isOutside() && name != null && name.startsWith("blends_natural") ? NATURAL : MADE;
    }

    /** What is drawn at (x, y), on any floor, stands this much above where the flat map has it. */
    static float lift(float x, float y, float z) {
        return height(x, y);
    }

    private static float smooth(float t) {
        t = Math.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** The rise and fall of the country over the better part of a mile. Never steeper than a rise of one in forty. */
    private static float country(float x, float y) {
        return 2.2f * (float) (Math.sin(x * 0.0061 + 0.7) * Math.sin(y * 0.0053 + 2.2))
                + 1.4f * (float) Math.sin((x * 0.8 - y * 0.6) * 0.0087 + 4.0);
    }

    /**
     * The hills that stand on the country: broad, a few tiles high, and never below it. A road
     * over one climbs less than one in fifteen.
     */
    private static float hills(float x, float y) {
        float broad = (float) (Math.sin(x * 0.015 + 1.1) * Math.sin(y * 0.018 + 0.3) + 0.6 * Math.sin((x + y) * 0.01 + 5.0));
        float ridge = (float) (Math.sin(x * 0.033 - y * 0.022 + 2.0) * Math.sin(y * 0.03 + x * 0.009 + 0.9));
        float up = Math.max(0f, 0.5f + 0.42f * broad + 0.16f * ridge);
        return 2.4f * up * up;
    }

    /** What is uneven underfoot on open ground, the same wherever and whenever it is asked. */
    private static float roll(float x, float y) {
        return 0.2f * (float) (Math.sin(x * 0.071 + 1.3) * Math.sin(y * 0.063 + 0.4))
                + 0.07f * (float) (Math.sin(x * 0.29 + y * 0.11) * Math.sin(y * 0.27 - x * 0.13));
    }
}

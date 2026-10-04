package com.cameraunlock.zomboid;

import java.util.concurrent.atomic.AtomicReferenceArray;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;

/**
 * The lie of the land in the first-person picture. The game's map is flat: a river is level
 * with its banks and a field is a table. Seen from above that is how a map reads. Seen from
 * eye height it is a board, so the picture gives the ground a height the game does not have:
 * open ground rolls, and the banks of water fall to a surface that lies below them.
 *
 * Only the picture. The game's own positions, paths and collisions stay on the flat map, and
 * everything drawn at ground level, the eye included, is lifted by the same amount.
 *
 * Whatever people made stays level. The roll fades out over FLAT_WITHIN tiles before a road, a
 * floor or water, so a house stands on flat ground and a road has no step at its edge.
 *
 * Heights are kept at the corners of the squares, a chunk at a time. The logic thread works
 * them out and both threads read them.
 */
final class Terrain {
    static final int CHUNK_TILES = 8;
    static final int CORNERS = CHUNK_TILES + 1;
    private static final int TABLE_SIDE = 64;
    /** How far from made ground or water the roll is at its full height, in tiles. */
    private static final int FLAT_WITHIN = 6;
    /** How far below the flat map water lies, and how far from it the bank starts to fall, in tiles. */
    private static final float WATER_BELOW = 0.34f;
    private static final float BANK_WIDTH = 2.6f;
    private static final int SPAN = CHUNK_TILES + 2 * FLAT_WITHIN;

    static final byte NATURAL = 0, MADE = 1, WATER = 2, UNKNOWN = 3;

    private static final class Grid {
        int wx, wy;
        boolean settled;
        final float[] heights = new float[CORNERS * CORNERS];
    }

    private static final class Kinds {
        int wx, wy;
        final byte[] kinds = new byte[CHUNK_TILES * CHUNK_TILES];
    }

    private static final AtomicReferenceArray<Grid> grids = new AtomicReferenceArray<>(TABLE_SIDE * TABLE_SIDE);
    private static final Kinds[] known = new Kinds[TABLE_SIDE * TABLE_SIDE];
    private static final Kinds passing = new Kinds();
    private static final byte[] around = new byte[SPAN * SPAN];

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

    /** What is drawn at (x, y) on level z stands this much above where the flat map has it: only the ground level is lifted. */
    static float lift(float x, float y, float z) {
        return z >= 0f && z < 1f ? height(x, y) : 0f;
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
        fresh.settled = gather(cell, wx, wy);
        for (int i = 0; i < CORNERS; i++) {
            for (int j = 0; j < CORNERS; j++) {
                float toMade = FLAT_WITHIN, toWater = BANK_WIDTH;
                int cornerI = i + FLAT_WITHIN, cornerJ = j + FLAT_WITHIN;
                for (int di = -FLAT_WITHIN; di < FLAT_WITHIN; di++) {
                    for (int dj = -FLAT_WITHIN; dj < FLAT_WITHIN; dj++) {
                        byte kind = around[(cornerI + di) * SPAN + cornerJ + dj];
                        if (kind == NATURAL) {
                            continue;
                        }
                        float awayI = di >= 0 ? di : -di - 1, awayJ = dj >= 0 ? dj : -dj - 1;
                        float away = (float) Math.sqrt(awayI * awayI + awayJ * awayJ);
                        toMade = Math.min(toMade, away);
                        if (kind == WATER) {
                            toWater = Math.min(toWater, away);
                        }
                    }
                }
                float x = wx * CHUNK_TILES + i, y = wy * CHUNK_TILES + j;
                fresh.heights[i * CORNERS + j] = roll(x, y) * smooth(toMade / FLAT_WITHIN) - WATER_BELOW * (1f - smooth(toWater / BANK_WIDTH));
            }
        }
        grids.set(slot, fresh);
        return fresh.heights;
    }

    /**
     * Fills around with what kind of ground each square within FLAT_WITHIN of the chunk is.
     * False if some of it is not loaded: that ground counts as made, and flat.
     */
    private static boolean gather(IsoCell cell, int wx, int wy) {
        boolean all = true;
        int reach = (FLAT_WITHIN + CHUNK_TILES - 1) / CHUNK_TILES;
        for (int cx = -reach; cx <= reach; cx++) {
            for (int cy = -reach; cy <= reach; cy++) {
                byte[] kinds = kinds(cell, wx + cx, wy + cy);
                for (int i = 0; i < CHUNK_TILES; i++) {
                    int row = cx * CHUNK_TILES + i + FLAT_WITHIN;
                    if (row < 0 || row >= SPAN) {
                        continue;
                    }
                    for (int j = 0; j < CHUNK_TILES; j++) {
                        int column = cy * CHUNK_TILES + j + FLAT_WITHIN;
                        if (column < 0 || column >= SPAN) {
                            continue;
                        }
                        byte kind = kinds[i * CHUNK_TILES + j];
                        all &= kind != UNKNOWN;
                        around[row * SPAN + column] = kind;
                    }
                }
            }
        }
        return all;
    }

    /** What each square of a chunk is. Kept once every square of the chunk has been seen loaded. */
    private static byte[] kinds(IsoCell cell, int wx, int wy) {
        int slot = slot(wx, wy);
        Kinds kept = known[slot];
        if (kept != null && kept.wx == wx && kept.wy == wy) {
            return kept.kinds;
        }
        boolean whole = true;
        for (int i = 0; i < CHUNK_TILES; i++) {
            for (int j = 0; j < CHUNK_TILES; j++) {
                byte kind = kind(cell.getGridSquare(wx * CHUNK_TILES + i, wy * CHUNK_TILES + j, 0));
                whole &= kind != UNKNOWN;
                passing.kinds[i * CHUNK_TILES + j] = kind;
            }
        }
        if (!whole) {
            return passing.kinds;
        }
        Kinds fresh = new Kinds();
        fresh.wx = wx;
        fresh.wy = wy;
        System.arraycopy(passing.kinds, 0, fresh.kinds, 0, fresh.kinds.length);
        known[slot] = fresh;
        return fresh.kinds;
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

    private static float smooth(float t) {
        t = Math.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Long swells with a shorter unevenness over them, the same wherever and whenever it is asked. */
    private static float roll(float x, float y) {
        return 0.34f * (float) (Math.sin(x * 0.071 + 1.3) * Math.sin(y * 0.063 + 0.4))
                + 0.22f * (float) Math.sin((x + y) * 0.043 + 2.1)
                + 0.09f * (float) (Math.sin(x * 0.29 + y * 0.11) * Math.sin(y * 0.27 - x * 0.13));
    }
}

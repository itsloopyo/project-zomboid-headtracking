package com.cameraunlock.zomboid;

import java.util.Arrays;
import zombie.core.textures.ColorInfo;
import zombie.iso.IsoGridSquare;

/**
 * Each square's light as the game gives it (IsoGridSquare.ILighting.lightInfo), asked of the
 * game once for each pass of its lighting engine. The engine relights nothing between two of
 * its passes (LightingJNI.getUpdateCounter, which TorchLess goes by too), and a chunk's reading
 * asks for the ring of squares round it as well as its own, so most squares were asked for
 * over and over in a pass: each asking goes to the native engine. The first asking in a pass
 * is the game's, as before, and marks the chunk where the light has changed (ChunkMarks).
 *
 * Logic thread only. Kept by chunk and level in the places of ChunkMarks' table, so the
 * chunks of the loaded map never share one, and a place taken by another chunk starts empty.
 * No square is held: a light is known by its square's place and the pass. What is kept for a
 * chunk the map no longer holds is let go (forgetOutside), so what is kept is never more than
 * the loaded map's levels that have been read.
 */
final class SquareLights {
    private static final int LEVELS = 64, LOWEST_LEVEL = -32;
    private static final int TILES = WorldScene.CHUNK_TILES * WorldScene.CHUNK_TILES;
    private static final int PLACES = ChunkMarks.SIDE * ChunkMarks.SIDE;

    private static final class Block {
        int wx, wy;
        final boolean[] known = new boolean[TILES];
        final int[] pass = new int[TILES];
        final float[] light = new float[TILES * 4];

        /** Takes the block for another chunk, with nothing known of it. */
        void take(int wx, int wy) {
            this.wx = wx;
            this.wy = wy;
            Arrays.fill(known, false);
        }
    }

    /** By level, each made at its first asking: by place in ChunkMarks' table. */
    private final Block[][] levels = new Block[LEVELS][];
    /** What a square asked for again in the same pass is handed back in: only until the next asking. */
    private final ColorInfo kept = new ColorInfo();
    /** How many times a square's light has been asked of the game, for the logic thread's tolls. */
    long asked;

    /** The square's light for the player as the game has it in the engine's pass given. */
    ColorInfo of(IsoGridSquare square, int playerIndex, int pass) {
        int x = square.getX(), y = square.getY();
        int wx = Math.floorDiv(x, WorldScene.CHUNK_TILES), wy = Math.floorDiv(y, WorldScene.CHUNK_TILES);
        Block[] places = levels[square.getZ() - LOWEST_LEVEL];
        if (places == null) {
            places = new Block[PLACES];
            levels[square.getZ() - LOWEST_LEVEL] = places;
        }
        int slot = ChunkMarks.slot(wx, wy);
        Block block = places[slot];
        if (block == null) {
            block = new Block();
            block.take(wx, wy);
            places[slot] = block;
        } else if (block.wx != wx || block.wy != wy) {
            block.take(wx, wy);
        }
        int at = (x & WorldScene.CHUNK_TILES - 1) * WorldScene.CHUNK_TILES + (y & WorldScene.CHUNK_TILES - 1);
        if (block.known[at] && block.pass[at] == pass) {
            kept.r = block.light[at * 4];
            kept.g = block.light[at * 4 + 1];
            kept.b = block.light[at * 4 + 2];
            kept.a = block.light[at * 4 + 3];
            return kept;
        }
        ColorInfo light = square.lighting[playerIndex].lightInfo();
        asked++;
        block.known[at] = true;
        block.pass[at] = pass;
        block.light[at * 4] = light.r;
        block.light[at * 4 + 1] = light.g;
        block.light[at * 4 + 2] = light.b;
        block.light[at * 4 + 3] = light.a;
        return light;
    }

    /** Lets go of what is kept for every chunk outside the loaded map, whose west and north chunk and width in chunks are given. */
    void forgetOutside(int mapWx, int mapWy, int mapSide) {
        for (Block[] places : levels) {
            if (places == null) {
                continue;
            }
            for (int slot = 0; slot < places.length; slot++) {
                Block block = places[slot];
                if (block != null && (block.wx < mapWx || block.wx >= mapWx + mapSide || block.wy < mapWy || block.wy >= mapWy + mapSide)) {
                    places[slot] = null;
                }
            }
        }
    }

    /** Lets go of everything: the map it is of is gone. */
    void forget() {
        Arrays.fill(levels, null);
    }
}

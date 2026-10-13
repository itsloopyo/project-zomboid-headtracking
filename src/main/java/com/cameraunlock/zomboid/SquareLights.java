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
 * squares of the loaded map never share one; a square is known by itself as well as by its
 * place, and one the map has let go of is never taken for another.
 */
final class SquareLights {
    private static final int LEVELS = 64, LOWEST_LEVEL = -32;
    private static final int TILES = WorldScene.CHUNK_TILES * WorldScene.CHUNK_TILES;

    private static final class Block {
        final IsoGridSquare[] square = new IsoGridSquare[TILES];
        final int[] pass = new int[TILES];
        final float[] light = new float[TILES * 4];
    }

    private final Block[] blocks = new Block[ChunkMarks.SIDE * ChunkMarks.SIDE * LEVELS];
    /** What a square asked for again in the same pass is handed back in: only until the next asking. */
    private final ColorInfo kept = new ColorInfo();

    /** The square's light for the player as the game has it in the engine's pass given. */
    ColorInfo of(IsoGridSquare square, int playerIndex, int pass) {
        int x = square.getX(), y = square.getY();
        int index = ChunkMarks.slot(Math.floorDiv(x, WorldScene.CHUNK_TILES), Math.floorDiv(y, WorldScene.CHUNK_TILES)) * LEVELS + square.getZ() - LOWEST_LEVEL;
        Block block = blocks[index];
        if (block == null) {
            block = new Block();
            blocks[index] = block;
        }
        int at = (x & WorldScene.CHUNK_TILES - 1) * WorldScene.CHUNK_TILES + (y & WorldScene.CHUNK_TILES - 1);
        if (block.square[at] == square && block.pass[at] == pass) {
            kept.r = block.light[at * 4];
            kept.g = block.light[at * 4 + 1];
            kept.b = block.light[at * 4 + 2];
            kept.a = block.light[at * 4 + 3];
            return kept;
        }
        ColorInfo light = square.lighting[playerIndex].lightInfo();
        block.square[at] = square;
        block.pass[at] = pass;
        block.light[at * 4] = light.r;
        block.light[at * 4 + 1] = light.g;
        block.light[at * 4 + 2] = light.b;
        block.light[at * 4 + 3] = light.a;
        return light;
    }

    /** Lets go of every square: the map they are of is gone. */
    void forget() {
        Arrays.fill(blocks, null);
    }
}

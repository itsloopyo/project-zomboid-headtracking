package com.cameraunlock.zomboid;

import zombie.iso.IsoChunkMap;

/**
 * Where a chunk map keeps its centre. The game centres each player's map on the chunk the player
 * stands in (or the place ahead of their vehicle) in IsoChunkMap.ProcessChunkPos, every tick, and a
 * new centre unloads a column or row of chunks at one edge and loads one at the other. A character
 * turning on the spot moves a few millimetres a tick on its animation, so one standing on a chunk
 * line moved the map back and forth 42 times in an 80 s turn. So the centre stays where it is until
 * the place is {@link #LAG_TILES} past the chunk it is on, and then goes where the game would put
 * it: which column is loaded at the far edge lags by up to a tile (owner's ruling of 2026-10-29,
 * alone and on servers). What else asks which chunks are loaded reads the centre this decides,
 * IsoChunkMap.worldX and worldY. Each player's map has its own, so in split screen the lag is per map.
 */
public final class ChunkCentre {
    /** How far past the chunk it is on the place goes before the centre follows it, in tiles. */
    static final float LAG_TILES = 1f;

    private ChunkCentre() {}

    /**
     * The centre on one axis: {@code centre} where the map has it, {@code stock} where the game
     * would put it, the chunk {@code place} is in. A place less than LAG_TILES past either edge of
     * the centre's chunk keeps the centre; any other goes to the game's, a teleport at once.
     */
    static int held(int centre, int stock, float place) {
        if (stock == centre) {
            return centre;
        }
        float west = centre * (float) WorldScene.CHUNK_TILES;
        if (place > west - LAG_TILES && place < west + WorldScene.CHUNK_TILES + LAG_TILES) {
            return centre;
        }
        return stock;
    }

    /** The centre ProcessChunkPos takes on x, from the one it worked out and the place it worked it out from. Called from the game's code. */
    public static int heldX(int stock, IsoChunkMap map, float x) {
        return held(map.worldX, stock, x);
    }

    /** The same on y. Called from the game's code. */
    public static int heldY(int stock, IsoChunkMap map, float y) {
        return held(map.worldY, stock, y);
    }
}

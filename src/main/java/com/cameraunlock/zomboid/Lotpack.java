package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * The squares of one chunk at a time of a cell's lotpack, read as the game reads a chunk when
 * it loads it (IsoLot.load): level by level, x before y within a level, each square its tiles
 * or one of a run of empty squares. Any thread.
 */
final class Lotpack {
    private static final byte[] MAGIC = {'L', 'O', 'T', 'P'};
    private static final int CELL_CHUNKS = FarCells.CELL / Terrain.CHUNK_TILES;
    /** The levels the game reads of a lotpack, whatever its header says. */
    static final int LOWEST = -32, HIGHEST = 31;

    private final ByteBuffer in;
    /** Where the table of the chunks' places starts: after the magic and version of a pack that has them. */
    private final int table;
    /** How many of the squares after the one last read are empty. */
    private int empty;

    Lotpack(ByteBuffer pack) {
        in = pack.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        in.get(0, magic);
        table = Arrays.equals(magic, MAGIC) ? 12 : 4;
    }

    /** Goes to the first square of the chunk (cx, cy) within the cell. */
    void chunk(int cx, int cy) {
        in.position(in.getInt(table + (cx * CELL_CHUNKS + cy) * 8));
        empty = 0;
    }

    /** How many tiles the next square holds, each then read with tile: 0 for a square with none. */
    int square() {
        if (empty > 0) {
            empty--;
            return 0;
        }
        int held = in.getInt();
        if (held == -1) {
            empty = in.getInt();
            if (empty > 0) {
                empty--;
            }
            return 0;
        }
        if (held <= 1) {
            return 0;
        }
        in.getInt();
        return held - 1;
    }

    /** The next tile of the square, as which of the header's tiles it is, of headerTiles. */
    int tile(int headerTiles) {
        int tile = in.getInt();
        if (tile < 0 || tile >= headerTiles) {
            throw new IllegalStateException("the lotpack names tile " + tile + " of its header's " + headerTiles);
        }
        return tile;
    }
}

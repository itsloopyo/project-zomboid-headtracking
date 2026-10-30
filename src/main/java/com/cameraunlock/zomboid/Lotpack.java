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

    /**
     * A lotpack the game would not load as it stands, or a chunk of one. The game loads such a
     * chunk empty (IsoLot.load catches what its read throws), so a reader skips it and says so.
     */
    static final class Malformed extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        Malformed(String message) {
            super(message);
        }
    }

    private final ByteBuffer in;
    /** Where the table of the chunks' places starts, as IsoLot.load seeks it. */
    private final int table;
    /** How many of the squares after the one last read are empty. */
    private int empty;
    private int cx, cy;

    Lotpack(ByteBuffer pack) {
        in = pack.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int version = 0;
        if (in.limit() >= 8) {
            byte[] magic = new byte[4];
            in.get(0, magic);
            if (Arrays.equals(magic, MAGIC)) {
                version = in.getInt(4);
                if (version < 0 || version > 1) {
                    throw new Malformed("the lotpack is of version " + version + ", which the game does not read");
                }
            }
        }
        // IsoLot.load: 4 past the version for version 1, and 4 from the start for version 0, whether or not the magic is there.
        table = (version >= 1 ? 8 : 0) + 4;
    }

    /** Goes to the first square of the chunk (cx, cy) within the cell. */
    void chunk(int cx, int cy) {
        this.cx = cx;
        this.cy = cy;
        int entry = table + (cx * CELL_CHUNKS + cy) * 8;
        if (entry > in.limit() - 4) {
            throw new Malformed("the lotpack of " + in.limit() + " bytes ends before chunk " + cx + ", " + cy + "'s place in its table");
        }
        int place = in.getInt(entry);
        if (place < 0 || place > in.limit()) {
            throw new Malformed("the lotpack puts chunk " + cx + ", " + cy + " at byte " + place + ", outside its " + in.limit() + " bytes");
        }
        in.position(place);
        empty = 0;
    }

    private int next() {
        if (in.remaining() < 4) {
            throw new Malformed("the lotpack ends inside chunk " + cx + ", " + cy);
        }
        return in.getInt();
    }

    /** How many tiles the next square holds, each then read with tile: 0 for a square with none. */
    int square() {
        if (empty > 0) {
            empty--;
            return 0;
        }
        int held = next();
        if (held == -1) {
            empty = next();
            if (empty > 0) {
                empty--;
            }
            return 0;
        }
        if (held <= 1) {
            return 0;
        }
        next();
        return held - 1;
    }

    /** The next tile of the square, as which of the header's tiles it is, of headerTiles. */
    int tile(int headerTiles) {
        int tile = next();
        if (tile < 0 || tile >= headerTiles) {
            throw new Malformed("the lotpack names tile " + tile + " of its header's " + headerTiles);
        }
        return tile;
    }
}

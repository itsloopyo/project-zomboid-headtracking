package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

/**
 * Where the game's map stands its trees in one cell, read from the cell's lotpack as the game
 * reads it when it loads a chunk (IsoLot.load). Nothing here asks the game anything, so it runs
 * on any thread. See FarTrees.
 */
final class FarTreeCells {
    private static final int CHUNK = Terrain.CHUNK_TILES;
    private static final int CHUNKS = FarCells.CELL / CHUNK;
    private static final byte[] MAGIC = {'L', 'O', 'T', 'P'};
    /** The levels the game reads of a lotpack, whatever its header says. */
    private static final int LOWEST = -32, HIGHEST = 31;

    private FarTreeCells() {}

    /**
     * Whether a tile the map places is a tree the game takes over as one of its own trees when the
     * square is first loaded: NatureTrees.replaceExistingObject takes these and no others.
     */
    static boolean isTree(String tile) {
        return tile.startsWith("vegetation_trees") || tile.startsWith("jumbo_tree_01");
    }

    /** For each of a header's tiles, whether it is a tree. */
    static boolean[] trees(List<String> tilesUsed) {
        boolean[] tree = new boolean[tilesUsed.size()];
        for (int i = 0; i < tree.length; i++) {
            tree[i] = isTree(tilesUsed.get(i));
        }
        return tree;
    }

    /**
     * The squares of a cell with a tree on the ground, each as x + y * CELL within the cell, in
     * the order the lotpack holds them: chunk by chunk, x before y within a chunk.
     *
     * @param pack      the whole lotpack
     * @param tree      for each tile of the cell's header, whether it is a tree
     * @param minLevel  the header's lowest level
     * @param maxLevel  the header's highest level
     */
    static int[] squares(ByteBuffer pack, boolean[] tree, int minLevel, int maxLevel) {
        ByteBuffer in = pack.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        in.get(0, magic);
        int table = Arrays.equals(magic, MAGIC) ? 12 : 4;
        int from = Math.max(minLevel, LOWEST), to = Math.min(maxLevel, HIGHEST);
        if (from > 0 || to < 0) {
            return new int[0];
        }
        int[] found = new int[256];
        int count = 0;
        for (int wx = 0; wx < CHUNKS; wx++) {
            for (int wy = 0; wy < CHUNKS; wy++) {
                in.position(in.getInt(table + (wx * CHUNKS + wy) * 8));
                int empty = 0;
                for (int z = from; z <= 0; z++) {
                    for (int x = 0; x < CHUNK; x++) {
                        for (int y = 0; y < CHUNK; y++) {
                            if (empty > 0) {
                                empty--;
                                continue;
                            }
                            int held = in.getInt();
                            if (held == -1) {
                                empty = in.getInt();
                                if (empty > 0) {
                                    empty--;
                                }
                                continue;
                            }
                            if (held <= 1) {
                                continue;
                            }
                            in.getInt();
                            boolean treed = false;
                            for (int i = 1; i < held; i++) {
                                int tile = in.getInt();
                                if (tile < 0 || tile >= tree.length) {
                                    throw new IllegalStateException("the lotpack names tile " + tile + " of its header's " + tree.length);
                                }
                                treed |= tree[tile];
                            }
                            if (treed && z == 0) {
                                if (count == found.length) {
                                    found = Arrays.copyOf(found, count * 2);
                                }
                                found[count++] = wx * CHUNK + x + (wy * CHUNK + y) * FarCells.CELL;
                            }
                        }
                    }
                }
            }
        }
        return Arrays.copyOf(found, count);
    }
}

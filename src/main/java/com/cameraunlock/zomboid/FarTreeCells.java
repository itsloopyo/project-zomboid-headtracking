package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
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
        return squares(pack, tree, minLevel, maxLevel, null);
    }

    /**
     * As squares, with a chunk that cannot be read skipped as the game loads it, empty, and what
     * was wrong with it added to skipped. With skipped null, it is thrown.
     */
    static int[] squares(ByteBuffer pack, boolean[] tree, int minLevel, int maxLevel, List<Lotpack.Malformed> skipped) {
        Lotpack in = new Lotpack(pack);
        int from = Math.max(minLevel, Lotpack.LOWEST), to = Math.min(maxLevel, Lotpack.HIGHEST);
        if (from > 0 || to < 0) {
            return new int[0];
        }
        int[] found = new int[256];
        int count = 0;
        for (int wx = 0; wx < CHUNKS; wx++) {
            for (int wy = 0; wy < CHUNKS; wy++) {
                if (found.length - count < CHUNK * CHUNK) {
                    found = Arrays.copyOf(found, found.length * 2);
                }
                try {
                    count = chunk(in, wx, wy, from, tree, found, count);
                } catch (Lotpack.Malformed unreadable) {
                    if (skipped == null) {
                        throw unreadable;
                    }
                    // The squares found before the fault stay in found past count, and are written over.
                    skipped.add(unreadable);
                }
            }
        }
        return Arrays.copyOf(found, count);
    }

    /** Adds the treed squares of the chunk (wx, wy) to found from count, which has room for the chunk's, and answers the count after them. */
    private static int chunk(Lotpack in, int wx, int wy, int from, boolean[] tree, int[] found, int count) {
        in.chunk(wx, wy);
        for (int z = from; z <= 0; z++) {
            for (int x = 0; x < CHUNK; x++) {
                for (int y = 0; y < CHUNK; y++) {
                    int tiles = in.square();
                    boolean treed = false;
                    for (int i = 0; i < tiles; i++) {
                        treed |= tree[in.tile(tree.length)];
                    }
                    if (treed && z == 0) {
                        found[count++] = wx * CHUNK + x + (wy * CHUNK + y) * FarCells.CELL;
                    }
                }
            }
        }
        return count;
    }
}

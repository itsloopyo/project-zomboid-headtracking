package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * Which trees stand in one cell beyond the loaded map, from the map's tree squares and the
 * trees the game's world generation lists for each square's biome. The game puts big trees in
 * the place of the map's own, each with room round it, and in some places leaves the map's
 * trees as they are; this follows that by the same lists and probabilities and a choice of its
 * own, so it is the right kind, size and number of trees for each place, not the game's trees one
 * for one. Nothing here asks the game anything. See FarTrees.
 */
final class FarTreePlacing {
    /** A tree a biome lists, by its kind, how likely it is, and how many squares a side it takes; or no tree, for a kind below 0. */
    record Choice(int kind, float probability, int side) {}

    /** The trees of a cell: square (x + y * CELL within it), kind and side, in the order they were placed. */
    record Trees(int[] square, int[] kind, int[] side, int count) {}

    /** The sides a tree that does not fit is tried at in turn, each smaller. */
    private static final int[] SIDES = {5, 3, 2, 1};

    private FarTreePlacing() {}

    /** A number from 0 to 1 that is the same for the same square and salt, and unrelated between squares. */
    static float hash(int x, int y, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ (y + 0x632BE59BD9B4E019L) * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1 << 24);
    }

    /**
     * @param cellX    the cell, in cells
     * @param squares  the cell's tree squares, as FarTreeCells.squares gives them
     * @param biomeOf  for each square of the cell, x + y * CELL, the biome it is in
     * @param biomes   for each biome, the trees it lists; an empty list keeps the map's tree, of a kind from keptKinds
     * @param keptKinds the kinds a kept tree may be, any one as likely as another
     */
    static Trees place(int cellX, int cellY, int[] squares, int[] biomeOf, Choice[][] biomes, int[] keptKinds) {
        int cell = FarCells.CELL;
        int baseX = cellX * cell, baseY = cellY * cell;
        long[] order = new long[squares.length];
        for (int i = 0; i < squares.length; i++) {
            int at = squares[i];
            order[i] = (long) (hash(baseX + at % cell, baseY + at / cell, 1) * (1 << 30)) << 32 | i;
        }
        Arrays.sort(order);
        boolean[] taken = new boolean[cell * cell];
        int[] square = new int[squares.length], kind = new int[squares.length], side = new int[squares.length];
        int count = 0;
        for (long each : order) {
            int at = squares[(int) each];
            if (taken[at]) {
                continue;
            }
            int x = at % cell, y = at / cell;
            Choice[] choices = biomes[biomeOf[at]];
            if (choices.length == 0) {
                if (keptKinds.length == 0) {
                    continue;
                }
                taken[at] = true;
                square[count] = at;
                kind[count] = keptKinds[(int) (hash(baseX + x, baseY + y, 2) * keptKinds.length)];
                side[count++] = 1;
                continue;
            }
            float total = 0f;
            for (Choice choice : choices) {
                total += choice.probability;
            }
            float drawn = hash(baseX + x, baseY + y, 3) * total;
            Choice chosen = choices[choices.length - 1];
            for (Choice choice : choices) {
                if (drawn < choice.probability) {
                    chosen = choice;
                    break;
                }
                drawn -= choice.probability;
            }
            if (chosen.kind < 0) {
                continue;
            }
            for (int tried : SIDES) {
                if (tried > chosen.side || !clear(taken, x, y, tried)) {
                    continue;
                }
                take(taken, x, y, tried);
                square[count] = at;
                kind[count] = chosen.kind;
                side[count++] = tried;
                break;
            }
        }
        return new Trees(square, kind, side, count);
    }

    /** The squares a tree of that side takes round its own, its own in the middle, those past the cell's edge left out. */
    private static boolean clear(boolean[] taken, int x, int y, int side) {
        int from = -(side / 2), to = from + side;
        for (int j = from; j < to; j++) {
            for (int i = from; i < to; i++) {
                int sx = x + i, sy = y + j;
                if (sx >= 0 && sy >= 0 && sx < FarCells.CELL && sy < FarCells.CELL && taken[sx + sy * FarCells.CELL]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void take(boolean[] taken, int x, int y, int side) {
        int from = -(side / 2), to = from + side;
        for (int j = from; j < to; j++) {
            for (int i = from; i < to; i++) {
                int sx = x + i, sy = y + j;
                if (sx >= 0 && sy >= 0 && sx < FarCells.CELL && sy < FarCells.CELL) {
                    taken[sx + sy * FarCells.CELL] = true;
                }
            }
        }
    }
}

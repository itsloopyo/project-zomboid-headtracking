package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class FarTreeTests {
    private FarTreeTests() {}

    static void run() {
        Checks.run("onlyTheMapsTreesAreTrees", FarTreeTests::onlyTheMapsTreesAreTrees);
        Checks.run("aTreeIsFoundOnItsSquare", FarTreeTests::aTreeIsFoundOnItsSquare);
        Checks.run("aRunOfEmptySquaresIsSkipped", FarTreeTests::aRunOfEmptySquaresIsSkipped);
        Checks.run("aCellarIsReadPastToTheGround", FarTreeTests::aCellarIsReadPastToTheGround);
        Checks.run("aTreeUpstairsIsLeftOut", FarTreeTests::aTreeUpstairsIsLeftOut);
        Checks.run("aLotpackWithoutItsWordIsRead", FarTreeTests::aLotpackWithoutItsWordIsRead);
        Checks.run("aTileTheHeaderHasNotIsRefused", FarTreeTests::aTileTheHeaderHasNotIsRefused);
    }

    private static final List<String> TILES = List.of("blends_natural_01_16", "vegetation_trees_01_8", "e_newgrass_1_33", "jumbo_tree_01_0");
    private static final boolean[] TREE = FarTreeCells.trees(TILES);

    /** A square's contents as the lotpack has them: a room and its tiles, or nothing for an empty square. */
    private record Square(int wx, int wy, int z, int x, int y, int... tiles) {}

    /**
     * A lotpack of one cell from the levels given, each chunk's squares written out in the game's
     * order, empty squares as runs.
     */
    private static ByteBuffer pack(boolean magic, int minLevel, int maxLevel, Square... squares) {
        ByteBuffer out = ByteBuffer.allocate(4 << 20).order(ByteOrder.LITTLE_ENDIAN);
        if (magic) {
            out.put(new byte[] {'L', 'O', 'T', 'P'}).putInt(1);
        }
        out.putInt(1024);
        int table = out.position();
        out.position(table + 1024 * 8);
        for (int wx = 0; wx < 32; wx++) {
            for (int wy = 0; wy < 32; wy++) {
                out.putLong(table + (wx * 32 + wy) * 8, out.position());
                int empty = 0;
                for (int z = minLevel; z <= maxLevel; z++) {
                    for (int x = 0; x < 8; x++) {
                        for (int y = 0; y < 8; y++) {
                            Square held = null;
                            for (Square square : squares) {
                                if (square.wx == wx && square.wy == wy && square.z == z && square.x == x && square.y == y) {
                                    held = square;
                                }
                            }
                            if (held == null) {
                                empty++;
                                continue;
                            }
                            if (empty > 0) {
                                out.putInt(-1).putInt(empty);
                                empty = 0;
                            }
                            out.putInt(held.tiles.length + 1).putInt(-1);
                            for (int tile : held.tiles) {
                                out.putInt(tile);
                            }
                        }
                    }
                }
                if (empty > 0) {
                    out.putInt(-1).putInt(empty);
                }
            }
        }
        out.flip();
        return out;
    }

    private static int at(int x, int y) {
        return x + y * FarCells.CELL;
    }

    private static void onlyTheMapsTreesAreTrees() {
        check(Arrays.equals(TREE, new boolean[] {false, true, false, true}), "the trees of the map are vegetation_trees and jumbo_tree_01, and grass is none");
    }

    private static void aTreeIsFoundOnItsSquare() {
        int[] found = FarTreeCells.squares(pack(true, 0, 0, new Square(0, 0, 0, 3, 5, 0, 1), new Square(2, 1, 0, 4, 4, 0, 2)), TREE, 0, 0);
        check(Arrays.equals(found, new int[] {at(3, 5)}), "the one tree is on its square and the grass is not: " + Arrays.toString(found));
    }

    private static void aRunOfEmptySquaresIsSkipped() {
        int[] found = FarTreeCells.squares(pack(true, 0, 0, new Square(31, 2, 0, 7, 7, 0, 3), new Square(31, 2, 0, 0, 1, 0)), TREE, 0, 0);
        check(Arrays.equals(found, new int[] {at(31 * 8 + 7, 2 * 8 + 7)}), "a tree after a run of empty squares is on its own square: " + Arrays.toString(found));
    }

    private static void aCellarIsReadPastToTheGround() {
        int[] found = FarTreeCells.squares(pack(true, -1, 0, new Square(1, 1, -1, 2, 2, 0, 1), new Square(1, 1, 0, 6, 1, 0, 1)), TREE, -1, 0);
        check(Arrays.equals(found, new int[] {at(14, 9)}), "the tree on the ground is found and the one in the cellar is not: " + Arrays.toString(found));
    }

    private static void aTreeUpstairsIsLeftOut() {
        int[] found = FarTreeCells.squares(pack(true, 0, 1, new Square(0, 3, 1, 1, 1, 1), new Square(0, 3, 0, 2, 2, 1)), TREE, 0, 1);
        check(Arrays.equals(found, new int[] {at(2, 26)}), "only the ground's tree is found: " + Arrays.toString(found));
    }

    private static void aLotpackWithoutItsWordIsRead() {
        int[] found = FarTreeCells.squares(pack(false, 0, 0, new Square(5, 6, 0, 1, 2, 1)), TREE, 0, 0);
        check(Arrays.equals(found, new int[] {at(41, 50)}), "a lotpack of the first version, without LOTP, is read the same: " + Arrays.toString(found));
    }

    private static void aTileTheHeaderHasNotIsRefused() {
        boolean refused = false;
        try {
            FarTreeCells.squares(pack(true, 0, 0, new Square(0, 0, 0, 0, 0, 0, TILES.size())), TREE, 0, 0);
        } catch (IllegalStateException expected) {
            refused = true;
        }
        check(refused, "a tile past the header's list is refused, not read as something else");
        List<String> none = new ArrayList<>();
        check(FarTreeCells.trees(none).length == 0, "a header with no tiles has no trees");
    }
}

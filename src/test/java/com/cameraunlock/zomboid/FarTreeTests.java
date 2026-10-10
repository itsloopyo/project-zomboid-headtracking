package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
        Checks.run("theSameCellPlacesTheSameTrees", FarTreeTests::theSameCellPlacesTheSameTrees);
        Checks.run("aChoiceOfNoTreePlacesNone", FarTreeTests::aChoiceOfNoTreePlacesNone);
        Checks.run("aBiomeWithNoTreesKeepsTheMaps", FarTreeTests::aBiomeWithNoTreesKeepsTheMaps);
        Checks.run("noTwoTreesShareASquare", FarTreeTests::noTwoTreesShareASquare);
        Checks.run("aBigTreeWithoutRoomIsSmaller", FarTreeTests::aBigTreeWithoutRoomIsSmaller);
        Checks.run("theHashIsFromNoughtToOne", FarTreeTests::theHashIsFromNoughtToOne);
        Checks.run("aTilesNameGivesItsSpeciesAndSize", FarTreeTests::aTilesNameGivesItsSpeciesAndSize);
        Checks.run("aBiomesAnyTreeIsEachOfItsSpecies", FarTreeTests::aBiomesAnyTreeIsEachOfItsSpecies);
        Checks.run("grassAndBouldersAreNoTrees", FarTreeTests::grassAndBouldersAreNoTrees);
        Checks.run("theErosionsTreesAreKept", FarTreeTests::theErosionsTreesAreKept);
        Checks.run("theTreeMeshIsACrownOverABole", FarTreeTests::theTreeMeshIsACrownOverABole);
    }

    private static void theTreeMeshIsACrownOverABole() {
        float[] near = FarTrees.mesh(5, 7, 4);
        check(near.length == (7 * (2 + 2 * 4) + 4 * 2) * 9, "five rings of seven sides and a bole of four are 78 triangles: " + near.length / 9);
        float[] far = FarTrees.mesh(2, 4, 0);
        check(far.length == 4 * (2 + 2) * 9, "two rings of four and no bole are 16 triangles: " + far.length / 9);
        boolean inRange = true, foot = false, top = false;
        for (int at = 0; at < near.length; at += 3) {
            inRange &= near[at] >= 0f && near[at] <= 1f;
            foot |= near[at + 2] == FarTrees.CROWN_FOOT && near[at] == 0f;
            top |= near[at + 2] == FarTrees.CROWN_TOP && near[at] == 1f;
        }
        check(inRange && foot && top, "every vertex is from the foot of its part to the top, and the crown closes at both ends");
    }

    private static final List<String> EROSION = List.of("e_americanholly_1", "e_canadianhemlock_1", "e_virginiapine_1");
    private static final Map<String, List<FarTreeKinds.Listed>> GAMES = Map.of(
            "organic_forest", List.of(new FarTreeKinds.Listed(List.of("e_dogwoodJUMBO_1_0", "e_dogwoodJUMBO_1_1"), 0.1f, 2),
                    new FarTreeKinds.Listed(List.of("$subbiome"), 0.1f, 3), new FarTreeKinds.Listed(List.of("$any"), 0.15f, 5),
                    new FarTreeKinds.Listed(List.of("e_redmapleJUMBO_1_0"), 0.1f, 2)),
            "ph_forest", List.of(new FarTreeKinds.Listed(List.of("$any"), 0.2f, 5), new FarTreeKinds.Listed(List.of("e_virginiapineJUMBO_1_0"), 0.1f, 2),
                    new FarTreeKinds.Listed(List.of("f_bushes_1_64"), 0.05f, 1), new FarTreeKinds.Listed(List.of("boulders_40"), 0.15f, 1),
                    new FarTreeKinds.Listed(List.of("e_newgrass_1_0"), 0.05f, 1)),
            "townhouse", List.of());

    private static void aTilesNameGivesItsSpeciesAndSize() {
        check("dogwood".equals(FarTreeKinds.speciesOf("e_dogwoodJUMBOXL_1_0")) && FarTreeKinds.sizeOf("e_dogwoodJUMBOXL_1_0") == FarTreeKinds.JUMBO_XL, "a big tree's tile");
        check("canadianhemlock".equals(FarTreeKinds.speciesOf("e_canadianhemlock_1_3")) && FarTreeKinds.sizeOf("e_canadianhemlock_1_3") == FarTreeKinds.SMALL, "an erosion tree's tile");
        check(FarTreeKinds.sizeOf("e_redmapleJUMBOXXL_1_0") == FarTreeKinds.JUMBO_XXL && FarTreeKinds.sizeOf("e_redmapleJUMBO_1_0") == FarTreeKinds.JUMBO, "the two other sizes");
        check(FarTreeKinds.speciesOf("vegetation_trees_01_8") == null && FarTreeKinds.speciesOf(null) == null, "a map tile and nothing name no species");
        check(FarTreeKinds.sizeOfSide(5) == FarTreeKinds.JUMBO_XXL && FarTreeKinds.sizeOfSide(3) == FarTreeKinds.JUMBO_XL && FarTreeKinds.sizeOfSide(2) == FarTreeKinds.JUMBO
                && FarTreeKinds.sizeOfSide(1) == FarTreeKinds.SMALL, "sizes by the squares a side");
        FarTreeKinds kinds = FarTreeKinds.of(GAMES, EROSION);
        int dogwood = kinds.species.indexOf("dogwood");
        check(kinds.name(FarTreeKinds.kind(dogwood, FarTreeKinds.JUMBO_XL)).equals("e_dogwoodJUMBOXL"), "a kind is named as its tiles begin");
    }

    private static void aBiomesAnyTreeIsEachOfItsSpecies() {
        FarTreeKinds kinds = FarTreeKinds.of(GAMES, EROSION);
        FarTreePlacing.Choice[] forest = kinds.biomes.get("organic_forest");
        int dogwood = kinds.species.indexOf("dogwood"), redmaple = kinds.species.indexOf("redmaple");
        int bigDogwood = 0, bigRedmaple = 0;
        float total = 0f;
        for (FarTreePlacing.Choice choice : forest) {
            total += choice.probability();
            if (choice.side() == 5) {
                bigDogwood += choice.kind() == dogwood ? 1 : 0;
                bigRedmaple += choice.kind() == redmaple ? 1 : 0;
                check(Math.abs(choice.probability() - 0.075f) < 1e-6f, "the any tree's chance is shared between the two species");
            }
        }
        check(bigDogwood == 1 && bigRedmaple == 1, "the any tree is dogwood or red maple, the species the biome names");
        check(Math.abs(total - 0.45f) < 1e-5f, "sharing the any and subbiome trees keeps the biome's total chance: " + total);
    }

    private static void grassAndBouldersAreNoTrees() {
        FarTreeKinds kinds = FarTreeKinds.of(GAMES, EROSION);
        int none = 0, pine = 0;
        for (FarTreePlacing.Choice choice : kinds.biomes.get("ph_forest")) {
            none += choice.kind() < 0 ? 1 : 0;
            pine += choice.kind() == kinds.species.indexOf("virginiapine") ? 1 : 0;
        }
        check(none == 3, "the bush, the boulder and the grass are no tree: " + none);
        check(pine == 2, "the pine and the any tree are pines, the one species the biome names: " + pine);
        check(!kinds.species.contains("newgrass"), "grass is no species");
    }

    private static void theErosionsTreesAreKept() {
        FarTreeKinds kinds = FarTreeKinds.of(GAMES, EROSION);
        check(kinds.biomes.get("townhouse").length == 0, "a biome that lists no trees chooses none");
        check(kinds.kept.length == 3 && kinds.species.get(kinds.kept[1]).equals("canadianhemlock"), "the map's own trees are the erosion's species");
    }

    private static final int[] ONE_BIOME = new int[FarCells.CELL * FarCells.CELL];

    /** Every other square of a block of the cell, as a wood of the map's small trees stands. */
    private static int[] wood(int from, int to) {
        int[] squares = new int[(to - from) * (to - from)];
        int count = 0;
        for (int y = from; y < to; y++) {
            for (int x = from; x < to; x++) {
                if ((x + y) % 2 == 0) {
                    squares[count++] = x + y * FarCells.CELL;
                }
            }
        }
        return Arrays.copyOf(squares, count);
    }

    private static final FarTreePlacing.Choice[] FOREST = {new FarTreePlacing.Choice(0, 0.3f, 2), new FarTreePlacing.Choice(1, 0.3f, 3), new FarTreePlacing.Choice(2, 0.3f, 5),
        new FarTreePlacing.Choice(-1, 0.1f, 1)};

    private static void theSameCellPlacesTheSameTrees() {
        FarTreePlacing.Trees first = FarTreePlacing.place(41, 33, wood(10, 60), ONE_BIOME, new FarTreePlacing.Choice[][] {FOREST}, new int[0]);
        FarTreePlacing.Trees again = FarTreePlacing.place(41, 33, wood(10, 60), ONE_BIOME, new FarTreePlacing.Choice[][] {FOREST}, new int[0]);
        check(first.count() > 0 && first.count() == again.count() && Arrays.equals(first.square(), again.square()) && Arrays.equals(first.kind(), again.kind())
                && Arrays.equals(first.side(), again.side()), "a cell placed twice has the same trees: " + first.count() + " and " + again.count());
        FarTreePlacing.Trees elsewhere = FarTreePlacing.place(42, 33, wood(10, 60), ONE_BIOME, new FarTreePlacing.Choice[][] {FOREST}, new int[0]);
        check(!Arrays.equals(first.square(), elsewhere.square()), "another cell with the same squares has other trees");
    }

    private static void aChoiceOfNoTreePlacesNone() {
        FarTreePlacing.Trees none = FarTreePlacing.place(0, 0, wood(0, 40), ONE_BIOME, new FarTreePlacing.Choice[][] {{new FarTreePlacing.Choice(-1, 1f, 1)}}, new int[] {7});
        check(none.count() == 0, "a biome whose one choice is no tree has none: " + none.count());
    }

    private static void aBiomeWithNoTreesKeepsTheMaps() {
        int[] squares = wood(0, 20);
        FarTreePlacing.Trees kept = FarTreePlacing.place(3, 4, squares, ONE_BIOME, new FarTreePlacing.Choice[][] {{}}, new int[] {4, 9});
        check(kept.count() == squares.length, "every one of the map's trees is kept: " + kept.count() + " of " + squares.length);
        boolean sides = true, kinds = true;
        for (int i = 0; i < kept.count(); i++) {
            sides &= kept.side()[i] == 1;
            kinds &= kept.kind()[i] == 4 || kept.kind()[i] == 9;
        }
        check(sides && kinds, "a kept tree takes its own square and is one of the kinds given");
    }

    private static void noTwoTreesShareASquare() {
        FarTreePlacing.Trees trees = FarTreePlacing.place(7, 7, wood(0, 120), ONE_BIOME, new FarTreePlacing.Choice[][] {FOREST}, new int[0]);
        boolean[] taken = new boolean[FarCells.CELL * FarCells.CELL];
        boolean clear = true;
        for (int i = 0; i < trees.count(); i++) {
            int x = trees.square()[i] % FarCells.CELL, y = trees.square()[i] / FarCells.CELL, side = trees.side()[i];
            for (int j = -(side / 2); j < side - side / 2; j++) {
                for (int k = -(side / 2); k < side - side / 2; k++) {
                    int sx = x + k, sy = y + j;
                    if (sx >= 0 && sy >= 0 && sx < FarCells.CELL && sy < FarCells.CELL) {
                        clear &= !taken[sx + sy * FarCells.CELL];
                        taken[sx + sy * FarCells.CELL] = true;
                    }
                }
            }
        }
        check(clear && trees.count() > 100, "the room round every tree is its own: " + trees.count() + " trees");
    }

    private static void aBigTreeWithoutRoomIsSmaller() {
        int[] squares = {10 + 10 * FarCells.CELL, 13 + 10 * FarCells.CELL};
        FarTreePlacing.Trees trees = FarTreePlacing.place(0, 0, squares, ONE_BIOME, new FarTreePlacing.Choice[][] {{new FarTreePlacing.Choice(5, 1f, 5)}}, new int[0]);
        check(trees.count() == 2, "both trees stand: " + trees.count());
        int[] sides = Arrays.copyOf(trees.side(), 2);
        Arrays.sort(sides);
        check(sides[1] == 5 && sides[0] < 5 && sides[0] >= 1, "the second, three squares from the first, is smaller: " + Arrays.toString(sides));
    }

    private static void theHashIsFromNoughtToOne() {
        float least = 1f, most = 0f, sum = 0f;
        for (int i = 0; i < 100000; i++) {
            float h = FarTreePlacing.hash(i * 7 - 300, i * 13, i % 5);
            least = Math.min(least, h);
            most = Math.max(most, h);
            sum += h;
        }
        check(least >= 0f && most < 1f && Math.abs(sum / 100000f - 0.5f) < 0.01f, "the hash is spread from 0 to 1: " + least + " to " + most + ", mean " + sum / 100000f);
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

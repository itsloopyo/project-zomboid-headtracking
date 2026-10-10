package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The kinds of tree the far trees are: a species and a size. The game names its trees' tiles
 * e_<species>_1_<stage> for a tree of the erosion's own size and e_<species>JUMBO_1_<n>,
 * e_<species>JUMBOXL_1_<n> and e_<species>JUMBOXXL_1_<n> for the big ones its world generation
 * stands, which take 2, 3 and 5 squares a side. See FarTrees.
 */
final class FarTreeKinds {
    /** Sizes, by how many squares a side the tree takes. */
    static final int SMALL = 0, JUMBO = 1, JUMBO_XL = 2, JUMBO_XXL = 3, SIZES = 4;
    private static final String[] SIZE_SUFFIX = {"", "JUMBO", "JUMBOXL", "JUMBOXXL"};
    /** In a biome's list of trees, one of the biome's own species at the size given. */
    static final String ANY = "$any", SUBBIOME = "$subbiome";

    /** The species, by the name the game's tiles give them, the index each has in a kind. */
    final List<String> species;
    /** For each biome by name, the trees it lists, as FarTreePlacing chooses among them. */
    final Map<String, FarTreePlacing.Choice[]> biomes;
    /** The species a tree the map stands in a biome that lists no trees may be. */
    final int[] kept;

    private FarTreeKinds(List<String> species, Map<String, FarTreePlacing.Choice[]> biomes, int[] kept) {
        this.species = species;
        this.biomes = biomes;
        this.kept = kept;
    }

    /** The size of a tree that takes that many squares a side: 5 and more is the largest. */
    static int sizeOfSide(int side) {
        return side >= 5 ? JUMBO_XXL : side >= 3 ? JUMBO_XL : side == 2 ? JUMBO : SMALL;
    }

    static int kind(int species, int size) {
        return species * SIZES + size;
    }

    /** The species a tree tile names, or null for a tile that is no tree of the game's: e_dogwoodJUMBOXL_1_0 is dogwood. */
    static String speciesOf(String tile) {
        if (tile == null || !tile.startsWith("e_")) {
            return null;
        }
        int end = tile.indexOf("JUMBO");
        if (end < 0) {
            end = tile.indexOf('_', 2);
        }
        return end <= 2 ? null : tile.substring(2, end);
    }

    /** The size a tree tile names: e_dogwoodJUMBOXL_1_0 is JUMBO_XL. */
    static int sizeOf(String tile) {
        if (tile.contains("JUMBOXXL")) {
            return JUMBO_XXL;
        }
        if (tile.contains("JUMBOXL")) {
            return JUMBO_XL;
        }
        return tile.contains("JUMBO") ? JUMBO : SMALL;
    }

    /** One tree a biome lists: the tiles it may be, how likely it is, and how many squares a side it takes. */
    record Listed(List<String> tiles, float probability, int side) {}

    /**
     * The kinds from what the game lists: for each biome, its trees, and the tilesets of the
     * erosion's trees (NatureTrees), e_americanholly_1 and the like. A biome's ANY or SUBBIOME
     * tree is each of the species the same biome lists by name, as likely as one another. What
     * a biome lists that is no tree (a bush, a boulder, grass) is chosen as no tree: a tile is a
     * tree's when it is a big tree's or one of the erosion's species.
     */
    static FarTreeKinds of(Map<String, List<Listed>> listed, List<String> erosionTilesets) {
        LinkedHashMap<String, Integer> index = new LinkedHashMap<>();
        int[] kept = new int[erosionTilesets.size()];
        int keptCount = 0;
        for (String tileset : erosionTilesets) {
            String species = speciesOf(tileset + "_0");
            if (species != null) {
                kept[keptCount++] = index.computeIfAbsent(species, k -> index.size());
            }
        }
        HashMap<String, FarTreePlacing.Choice[]> biomes = new HashMap<>();
        for (Map.Entry<String, List<Listed>> biome : listed.entrySet()) {
            ArrayList<Integer> named = new ArrayList<>();
            for (Listed tree : biome.getValue()) {
                for (String tile : tree.tiles) {
                    String species = treeSpecies(tile, index);
                    if (species != null) {
                        int at = index.computeIfAbsent(species, k -> index.size());
                        if (!named.contains(at)) {
                            named.add(at);
                        }
                    }
                }
            }
            ArrayList<FarTreePlacing.Choice> choices = new ArrayList<>();
            for (Listed tree : biome.getValue()) {
                String first = tree.tiles.isEmpty() ? "" : tree.tiles.get(0);
                if (first.equals(ANY) || first.equals(SUBBIOME)) {
                    for (int species : named) {
                        choices.add(new FarTreePlacing.Choice(species, tree.probability / named.size(), tree.side));
                    }
                } else {
                    String species = treeSpecies(first, index);
                    choices.add(new FarTreePlacing.Choice(species == null ? -1 : index.get(species), tree.probability, tree.side));
                }
            }
            biomes.put(biome.getKey(), choices.toArray(new FarTreePlacing.Choice[0]));
        }
        int[] keptSpecies = new int[keptCount];
        System.arraycopy(kept, 0, keptSpecies, 0, keptCount);
        return new FarTreeKinds(List.copyOf(index.keySet()), Map.copyOf(biomes), keptSpecies);
    }

    /** The species of a tile that is a tree's: a big tree's, or one of the species already known from the erosion's tilesets. */
    private static String treeSpecies(String tile, Map<String, Integer> known) {
        String species = speciesOf(tile);
        return species != null && (sizeOf(tile) != SMALL || known.containsKey(species)) ? species : null;
    }

    /** The name of a kind, e_dogwoodJUMBOXL, as the game's tiles of it begin. */
    String name(int kind) {
        return "e_" + species.get(kind / SIZES) + SIZE_SUFFIX[kind % SIZES];
    }
}

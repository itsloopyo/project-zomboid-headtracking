package com.cameraunlock.zomboid;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import zombie.erosion.ErosionRegions;
import zombie.erosion.categories.ErosionCategory;
import zombie.iso.IsoLot;
import zombie.iso.IsoWorld;
import zombie.iso.LotHeader;
import zombie.iso.worldgen.WorldGenChunk;
import zombie.iso.worldgen.biomes.Feature;
import zombie.iso.worldgen.biomes.FeatureType;
import zombie.iso.worldgen.biomes.IBiome;
import zombie.iso.worldgen.biomes.TileGroup;
import zombie.iso.worldgen.maps.BiomeMap;
import zombie.iso.worldgen.maps.BiomeMapEntry;

/**
 * The trees beyond the loaded map, a cell at a time, made with the far land (FarLand).
 *
 * Where a tree stands is the map's: the squares its lotpack puts a tree on. What tree it is
 * is the game's world generation's: the trees each square's biome lists (WorldGenChunk), with
 * the room each takes round it. The game's own choice among them is its world generation's, and
 * this one is FarTreePlacing's, so the far trees are the right kinds, sizes and numbers for each
 * place and not the game's trees one for one: the loaded map's own trees take over from them
 * where it begins.
 *
 * The game's world generation keeps caches it fills as it is asked, on the thread that loads
 * the map. Nothing here asks it: a biome map of the far trees' own is asked on the far land's
 * thread, and the biomes' lists of trees are read once on the logic thread, as the game holds
 * them once a save is loaded.
 */
final class FarTrees {
    /** Bytes a tree takes on the graphics card: where it stands, its foot's height, and its kind with a number of its own after the point. */
    static final int TREE_BYTES = 16;

    /** What the game's map holds for one cell: its lotpack and the tiles its header names. */
    record Lot(Path pack, boolean[] tree, int minLevel, int maxLevel) {}

    private FarTrees() {}

    /**
     * Logic thread: the kinds the game's lists make, or null where this save has no world
     * generation to ask (a map without its biomes).
     */
    static FarTreeKinds read() throws ReflectiveOperationException {
        WorldGenChunk generation = IsoWorld.instance.getWgChunk();
        if (generation == null) {
            return null;
        }
        Field basesField = WorldGenChunk.class.getDeclaredField("biomesMap");
        basesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ? extends IBiome> bases = (Map<String, ? extends IBiome>) basesField.get(generation);
        HashMap<String, List<FarTreeKinds.Listed>> listed = new HashMap<>();
        for (Map.Entry<String, ? extends IBiome> base : bases.entrySet()) {
            ArrayList<FarTreeKinds.Listed> trees = new ArrayList<>();
            // A biome that lists no features at all has no map of them.
            Map<FeatureType, List<Feature>> all = base.getValue().getFeatures();
            List<Feature> features = all == null ? null : all.get(FeatureType.TREE);
            if (features != null) {
                for (Feature feature : features) {
                    ArrayList<String> tiles = new ArrayList<>();
                    int side = 1;
                    for (TileGroup group : feature.tileGroups()) {
                        tiles.addAll(group.tiles());
                        side = Math.max(side, Math.max(group.sx(), group.sy()));
                    }
                    trees.add(new FarTreeKinds.Listed(List.copyOf(tiles), feature.probability().getValue(), side));
                }
            }
            listed.put(base.getKey(), List.copyOf(trees));
        }
        ErosionCategory erosion = ErosionRegions.getCategory(ErosionRegions.REGION_NATURE, ErosionRegions.CATEGORY_TREES);
        Field treesField = erosion.getClass().getDeclaredField("trees");
        treesField.setAccessible(true);
        Object[] inits = (Object[]) treesField.get(erosion);
        ArrayList<String> tilesets = new ArrayList<>();
        for (Object init : inits) {
            Field tileset = init.getClass().getDeclaredField("tilesetName");
            tileset.setAccessible(true);
            tilesets.add((String) tileset.get(init));
        }
        return FarTreeKinds.of(listed, tilesets);
    }

    /** Logic thread: what the game's map holds for the cell, or null for a cell it has no lotpack for. */
    static Lot lot(int cellX, int cellY) {
        LotHeader header = IsoLot.getHeader(cellX, cellY);
        String pack = IsoLot.InfoFileNames.get("world_" + cellX + "_" + cellY + ".lotpack");
        if (header == null || pack == null) {
            return null;
        }
        return new Lot(Path.of(pack), FarTreeCells.trees(header.tilesUsed), header.minLevel, header.maxLevel);
    }

    /** The far land's thread only: the biome map the far trees ask, so that the game's own is only ever asked by the game. */
    private static BiomeMap biomes;

    /** The far land's thread, as the map is let go of. */
    static void forget() {
        if (biomes != null) {
            biomes.Dispose();
            biomes = null;
        }
    }

    /**
     * The far land's thread: the cell's trees as the graphics card takes them, TREE_BYTES each,
     * from the start of what is returned to its limit.
     *
     * @param around as FarCells.toWater takes it, for the height of the land under each tree
     */
    static ByteBuffer make(FarTreeKinds kinds, Lot lot, int cellX, int cellY, int[][] around) throws IOException {
        if (biomes == null) {
            biomes = new BiomeMap();
        }
        int[] squares = FarTreeCells.squares(ByteBuffer.wrap(Files.readAllBytes(lot.pack())), lot.tree(), lot.minLevel(), lot.maxLevel());
        if (squares.length == 0) {
            return ByteBuffer.allocate(0);
        }
        ArrayList<FarTreePlacing.Choice[]> lists = new ArrayList<>();
        HashMap<String, Integer> listOf = new HashMap<>();
        int[] biomeOf = new int[FarCells.CELL * FarCells.CELL];
        int chunks = FarCells.CELL / Terrain.CHUNK_TILES;
        int[][] zones = new int[chunks * chunks][];
        for (int at : squares) {
            int x = at % FarCells.CELL, y = at / FarCells.CELL;
            int chunk = x / Terrain.CHUNK_TILES + y / Terrain.CHUNK_TILES * chunks;
            if (zones[chunk] == null) {
                zones[chunk] = biomes.getZones(cellX * chunks + x / Terrain.CHUNK_TILES, cellY * chunks + y / Terrain.CHUNK_TILES, BiomeMap.Type.BIOME);
            }
            String name = null;
            if (zones[chunk] != null) {
                BiomeMapEntry entry = biomes.getEntry(zones[chunk][x % Terrain.CHUNK_TILES + y % Terrain.CHUNK_TILES * Terrain.CHUNK_TILES]);
                name = entry == null ? null : entry.biome();
            }
            FarTreePlacing.Choice[] choices = name == null ? null : kinds.biomes.get(name);
            String key = choices == null ? "" : name;
            Integer list = listOf.get(key);
            if (list == null) {
                list = lists.size();
                listOf.put(key, list);
                lists.add(choices == null ? new FarTreePlacing.Choice[0] : choices);
            }
            biomeOf[at] = list;
        }
        FarTreePlacing.Trees trees = FarTreePlacing.place(cellX, cellY, squares, biomeOf, lists.toArray(new FarTreePlacing.Choice[0][]), kinds.kept);
        ByteBuffer to = ByteBuffer.allocate(trees.count() * TREE_BYTES).order(ByteOrder.nativeOrder());
        int baseX = cellX * FarCells.CELL, baseY = cellY * FarCells.CELL;
        for (int i = 0; i < trees.count(); i++) {
            int at = trees.square()[i], side = trees.side()[i];
            int x = at % FarCells.CELL, y = at / FarCells.CELL;
            // A tree that takes an even number of squares a side stands on the corner its own square shares with the next.
            float middle = side % 2 == 0 ? 0f : 0.5f;
            float standX = baseX + x + middle, standY = baseY + y + middle;
            int kind = FarTreeKinds.kind(trees.kind()[i], FarTreeKinds.sizeOfSide(side));
            to.putFloat(standX).putFloat(standY).putFloat(ground(cellX, cellY, around, standX - baseX, standY - baseY))
                    .putFloat(kind + FarTreePlacing.hash(baseX + x, baseY + y, 4) * 0.999f);
        }
        return to.flip();
    }

    /** The parts of a vertex of the tree mesh, as PipelineShaders.FAR_TREE_VERTEX reads them. */
    static final float BOLE = 0f, CROWN = 1f, CROWN_FOOT = 2f, CROWN_TOP = 3f;

    /**
     * The mesh every far tree is drawn from, as triangles of three floats a vertex: how far up
     * its part the vertex is, from 0 to 1, its angle round the foot, and its part. A crown of
     * rings between a point at its foot and one at its top, sides a ring, and a bole of
     * boleSides under it, or none.
     */
    static float[] mesh(int rings, int sides, int boleSides) {
        ArrayList<float[]> triangles = new ArrayList<>();
        for (int side = 0; side < sides; side++) {
            float a = (float) (2 * Math.PI * side / sides), b = (float) (2 * Math.PI * (side + 1) / sides);
            float first = 1f / (rings + 1), last = (float) rings / (rings + 1);
            triangles.add(new float[] {0f, a, CROWN_FOOT, first, b, CROWN, first, a, CROWN});
            for (int ring = 1; ring < rings; ring++) {
                float low = (float) ring / (rings + 1), high = (float) (ring + 1) / (rings + 1);
                triangles.add(new float[] {low, a, CROWN, low, b, CROWN, high, b, CROWN});
                triangles.add(new float[] {low, a, CROWN, high, b, CROWN, high, a, CROWN});
            }
            triangles.add(new float[] {last, a, CROWN, last, b, CROWN, 1f, a, CROWN_TOP});
        }
        for (int side = 0; side < boleSides; side++) {
            float a = (float) (2 * Math.PI * side / boleSides), b = (float) (2 * Math.PI * (side + 1) / boleSides);
            triangles.add(new float[] {0f, a, BOLE, 0f, b, BOLE, 1f, b, BOLE});
            triangles.add(new float[] {0f, a, BOLE, 1f, b, BOLE, 1f, a, BOLE});
        }
        float[] mesh = new float[triangles.size() * 9];
        for (int i = 0; i < triangles.size(); i++) {
            System.arraycopy(triangles.get(i), 0, mesh, i * 9, 9);
        }
        return mesh;
    }

    /** The height of the far land's surface at a place in the cell: the land is drawn from heights at the corners of its chunks (FarCells.land), and a tree stands on it as drawn. */
    private static float ground(int cellX, int cellY, int[][] around, float x, float y) {
        int step = FarCells.STEP;
        int i = Math.min((int) (x / step), FarCells.SIDE - 1), j = Math.min((int) (y / step), FarCells.SIDE - 1);
        float fx = x / step - i, fy = y / step - j;
        float h00 = corner(cellX, cellY, around, i, j), h10 = corner(cellX, cellY, around, i + 1, j);
        float h01 = corner(cellX, cellY, around, i, j + 1), h11 = corner(cellX, cellY, around, i + 1, j + 1);
        return (h00 * (1f - fx) + h10 * fx) * (1f - fy) + (h01 * (1f - fx) + h11 * fx) * fy;
    }

    private static float corner(int cellX, int cellY, int[][] around, int i, int j) {
        int x = i * FarCells.STEP, y = j * FarCells.STEP;
        return Terrain.HILLS.farUnder(cellX * FarCells.CELL + x, cellY * FarCells.CELL + y, FarCells.toWater(around, x, y));
    }
}

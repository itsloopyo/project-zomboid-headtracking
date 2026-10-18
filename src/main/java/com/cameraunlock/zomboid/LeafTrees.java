package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import zombie.GameTime;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSpriteInstance;
import zombie.util.list.PZArrayList;

/**
 * The game's side of LeafFall for one view, on the logic thread: reads the trees within
 * LeafFall.REACH of the eye a few rows of squares a frame, moves the leaves on, and hands the
 * frame's sky what it is to draw.
 */
final class LeafTrees implements LeafFall.Ground {
    /** Rows of squares read a frame: the square round the eye is read through in a dozen frames. */
    private static final int ROWS = 6;
    private static final int SIDE = 2 * (int) LeafFall.REACH + 1;
    /**
     * Each tree tile's crown as the built world first measured it (Flora.measure): its lowest and
     * highest leaves over the tree's foot and how far out they reach, in tiles. A tile names the
     * species, the size and the erosion's stage, so a young tree's crown is a young tree's, and
     * its picture is the same each time a tree of it is built: one measure of it is kept.
     */
    private static final ConcurrentHashMap<String, float[]> CROWNS = new ConcurrentHashMap<>();

    final LeafFall fall = new LeafFall();
    private final WorldScene scene;
    private int row, fromX, fromY;
    private long lastNanos;
    private IsoCell cell;
    private Terrain terrain;
    private int round;
    private float scale;

    LeafTrees(WorldScene scene) {
        this.scene = scene;
    }

    /** Whether the game hangs leaves on a tree: one its erosion has bare has none, and its crown is drawn bare. */
    static boolean inLeaf(IsoObject tree) {
        ArrayList<IsoSpriteInstance> leaves = tree.getAttachedAnimSprite();
        return leaves != null && !leaves.isEmpty() || tree.getOverlaySprite() != null;
    }

    /** Whether a crown of the tree tile is still to be noted (see crownSeen). Any thread. */
    static boolean crownWanted(String tile) {
        return !CROWNS.containsKey(tile);
    }

    /** Notes a crown of the tree tile as the built world measured it, with Flora.measure's sizes, unless one is noted already. Any thread. */
    static void crownSeen(String tile, float[] sizes) {
        CROWNS.putIfAbsent(tile, new float[] {sizes[Flora.LOWEST], sizes[Flora.HIGHEST], sizes[Flora.WIDEST]});
    }

    /**
     * Reads on, moves the leaves on to now and writes them into the sky, or none where the mod's
     * plants are not drawn. round and scale are what the builds read roofs at (Roofs.at).
     */
    void gather(SkyState sky, IsoCell cell, Terrain terrain, int round, float scale, float eyeX, float eyeY, long now) {
        float elapsed = lastNanos == 0L || GameTime.isGamePaused() ? 0f : (now - lastNanos) / 1e9f;
        lastNanos = now;
        if (!Own.plants(scene.view.index) || cell == null) {
            fall.clear();
            row = 0;
            sky.leafCount = 0;
            return;
        }
        this.cell = cell;
        this.terrain = terrain;
        this.round = round;
        this.scale = scale;
        long season = Seasons.now();
        // No tree sheds outside autumn (LeafFall.shedding), so the trees are not read then; leaves still falling go on.
        if (Seasons.seasonOf(season) == Seasons.AUTUMN) {
            read(eyeX, eyeY);
        } else {
            fall.forgetTrees();
            row = 0;
        }
        fall.step(elapsed, sky.airX, sky.airY, sky.blowing, Seasons.seasonOf(season), Seasons.progressionOf(season), eyeX, eyeY, this);
        sky.leafCount = fall.write(sky.leaves, eyeX, eyeY);
    }

    private void read(float eyeX, float eyeY) {
        if (row == 0) {
            fromX = (int) Math.floor(eyeX) - (int) LeafFall.REACH;
            fromY = (int) Math.floor(eyeY) - (int) LeafFall.REACH;
            fall.beginTrees(eyeX, eyeY);
        }
        for (int end = Math.min(row + ROWS, SIDE); row < end; row++) {
            int y = fromY + row;
            for (int x = fromX; x < fromX + SIDE; x++) {
                IsoGridSquare square = cell.getGridSquare(x, y, 0);
                if (square == null || !square.HasTree()) {
                    continue;
                }
                IsoObject tree = treeOn(square);
                String name = tree == null || tree.getSprite() == null ? null : tree.getSprite().getName();
                if (name == null) {
                    continue;
                }
                float[] crown = CROWNS.get(name);
                fall.offer(x + 0.5f, y + 0.5f, terrain.lift(x + 0.5f, y + 0.5f, 0), name, Seasons.treeHash(x, y), inLeaf(tree),
                        crown == null ? 0f : crown[0], crown == null ? 0f : crown[1], crown == null ? 0f : crown[2]);
            }
        }
        if (row == SIDE) {
            fall.endTrees();
            row = 0;
        }
    }

    private static IsoObject treeOn(IsoGridSquare square) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i).getType() == IsoObjectType.tree) {
                return objects.get(i);
            }
        }
        return null;
    }

    @Override
    public float ground(float x, float y) {
        return terrain.lift(x, y, 0);
    }

    @Override
    public float surface(float x, float y, float up) {
        float land = terrain.lift(x, y, 0), roof = roofUnder(x, y, up);
        return Float.isNaN(roof) ? land : roof * ViewCamera.LEVEL_HEIGHT + land;
    }

    @Override
    public float layers(float x, float y, float up) {
        // A roof tile lies at most a flat roof's layers over its floor, and nothing is laid on it.
        return Float.isNaN(roofUnder(x, y, up)) ? scene.layersOnTheLand((int) Math.floor(x), (int) Math.floor(y)) : RoofBuilder.FLAT_ROOF_LAYERS;
    }

    /** The height in levels over the land of the roof a leaf at height up over (x, y) comes down onto, or NaN where it comes down onto the land. */
    private float roofUnder(float x, float y, float up) {
        int sx = (int) Math.floor(x), sy = (int) Math.floor(y);
        for (int z = LeafFall.storey(up, terrain.lift(sx + 0.5f, sy + 0.5f, 0)); z >= 1; z--) {
            // A leaf in the storey of a roof's tiles, over its square, is over the roof or in it: either way the roof is what it comes down onto.
            float roof = roofAt(sx, sy, z, x - sx, y - sy);
            if (!Float.isNaN(roof)) {
                return roof;
            }
        }
        return Float.NaN;
    }

    @Override
    public boolean walled(int x, int y, float up) {
        IsoGridSquare square = cell.getGridSquare(x, y, LeafFall.storey(up, terrain.lift(x + 0.5f, y + 0.5f, 0)));
        return square != null && square.getRoomID() != -1L;
    }

    /**
     * The height in levels of the roof tile laid on level z of the square at (x, y), at (a, b)
     * within the square, on the plane the roof's fit gave it as RoofBuilder lays it, or NaN where
     * none is: no tile, a trim on the roof, or half a tile at a ridge, which the ridge stands for.
     */
    private float roofAt(int x, int y, int z, float a, float b) {
        Roofs.Laid laid = Roofs.at(cell, x, y, z, round, scale);
        if (laid == null) {
            laid = Roofs.madeAt(x, y, z, round);
        }
        if (laid == null) {
            return Float.NaN;
        }
        RoofFit.Tile tile = laid.tile;
        if (tile.half != 0 || tile.fitting) {
            return Float.NaN;
        }
        if (tile.shaped) {
            System.arraycopy(tile.corners, 0, corners, 0, 4);
        } else if (tile.axis == RoofFit.LEVEL && tile.fitAxis == RoofFit.LEVEL) {
            if (tile.peak > 0f) {
                float across = tile.peakAxis == RoofFit.ALONG_X ? a : b;
                return tile.fitLow + tile.peak * (1f - Math.abs(2f * across - 1f));
            }
            return (tile.low + tile.high) / 2f;
        } else {
            RoofFit.cornersOf(tile.fitAxis, tile.fitLow, tile.fitHigh, corners);
        }
        // North-west, north-east, south-east and south-west.
        float north = corners[0] + (corners[1] - corners[0]) * a, south = corners[3] + (corners[2] - corners[3]) * a;
        return north + (south - north) * b;
    }

    private final float[] corners = new float[4];
}

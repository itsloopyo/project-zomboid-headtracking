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
     * Each tree tile's crown as the built world last measured it (Flora.measure): its lowest and
     * highest leaves over the tree's foot and how far out they reach, in tiles. A tile names the
     * species, the size and the erosion's stage, so a young tree's crown is a young tree's.
     */
    private static final ConcurrentHashMap<String, float[]> CROWNS = new ConcurrentHashMap<>();

    final LeafFall fall = new LeafFall();
    private int row, fromX, fromY;
    private long lastNanos;
    private IsoCell cell;
    private Terrain terrain;

    /** Whether the game hangs leaves on a tree: one its erosion has bare has none, and its crown is drawn bare. WorldScene reads a tree in leaf by this too. */
    static boolean inLeaf(IsoObject tree) {
        ArrayList<IsoSpriteInstance> leaves = tree.getAttachedAnimSprite();
        return leaves != null && !leaves.isEmpty() || tree.getOverlaySprite() != null;
    }

    /** Whether a crown of the tree tile is still to be noted (see crownSeen). Any thread. */
    static boolean crownWanted(String tile) {
        return !CROWNS.containsKey(tile);
    }

    /** Notes a crown of the tree tile as the built world measured it, with Flora.measure's sizes. Any thread. */
    static void crownSeen(String tile, float[] sizes) {
        CROWNS.putIfAbsent(tile, new float[] {sizes[Flora.LOWEST], sizes[Flora.HIGHEST], sizes[Flora.WIDEST]});
    }

    /** Reads on, moves the leaves on to now and writes them into the sky, or none where the mod's plants are not drawn. */
    void gather(SkyState sky, IsoCell cell, Terrain terrain, float eyeX, float eyeY, long now) {
        float elapsed = lastNanos == 0L || GameTime.isGamePaused() ? 0f : (now - lastNanos) / 1e9f;
        lastNanos = now;
        if (!Own.plants() || cell == null) {
            fall.clear();
            row = 0;
            sky.leafCount = 0;
            return;
        }
        this.cell = cell;
        this.terrain = terrain;
        read(eyeX, eyeY);
        long season = Seasons.now();
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
    public float height(float x, float y) {
        return terrain.lift(x, y, 0);
    }

    @Override
    public boolean walled(int x, int y, float up) {
        IsoGridSquare square = cell.getGridSquare(x, y, Math.max((int) Math.floor(up / ViewCamera.LEVEL_HEIGHT), 0));
        return square != null && square.getRoomID() != -1L;
    }
}

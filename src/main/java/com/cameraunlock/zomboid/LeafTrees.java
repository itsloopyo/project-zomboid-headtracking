package com.cameraunlock.zomboid;

import zombie.GameTime;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;

/**
 * The game's side of LeafFall for one view, on the logic thread: reads the broad-leaved trees
 * within LeafFall.REACH of the eye a few rows of squares a frame, moves the leaves on, and
 * hands the frame's sky what it is to draw.
 */
final class LeafTrees implements LeafFall.Ground {
    /** Rows of squares read a frame: the square round the eye is read through in a few dozen frames. */
    private static final int ROWS = 6;
    private static final int SIDE = 2 * (int) LeafFall.REACH + 1;

    final LeafFall fall = new LeafFall();
    private final float[] xs = new float[LeafFall.MOST_TREES], ys = new float[LeafFall.MOST_TREES], grounds = new float[LeafFall.MOST_TREES];
    private final int[] hashes = new int[LeafFall.MOST_TREES];
    private final byte[] hues = new byte[LeafFall.MOST_TREES];
    private final boolean[] jumbos = new boolean[LeafFall.MOST_TREES];
    private int found, row, fromX, fromY;
    private boolean full;
    private long lastNanos;
    private IsoCell cell;
    private Terrain terrain;

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
            found = 0;
            full = false;
        }
        for (int end = Math.min(row + ROWS, SIDE); row < end; row++) {
            int y = fromY + row;
            for (int x = fromX; x < fromX + SIDE && !full; x++) {
                IsoGridSquare square = cell.getGridSquare(x, y, 0);
                if (square == null || !square.HasTree()) {
                    continue;
                }
                String name = WorldScene.Frame.treeName(square);
                if (!LeafFall.sheds(name)) {
                    continue;
                }
                int hash = Seasons.treeHash(x, y);
                xs[found] = x + 0.5f;
                ys[found] = y + 0.5f;
                grounds[found] = terrain.lift(x + 0.5f, y + 0.5f, 0);
                hashes[found] = hash;
                hues[found] = (byte) Flora.autumnHue(name, hash);
                jumbos[found] = name.contains("JUMBO");
                full = ++found == LeafFall.MOST_TREES;
            }
        }
        if (row == SIDE) {
            fall.trees(found, xs, ys, grounds, hashes, hues, jumbos);
            row = 0;
        }
    }

    @Override
    public float height(float x, float y) {
        return terrain.lift(x, y, 0);
    }

    @Override
    public boolean walled(int x, int y) {
        IsoGridSquare square = cell.getGridSquare(x, y, 0);
        return square != null && square.getRoomID() != -1L;
    }
}

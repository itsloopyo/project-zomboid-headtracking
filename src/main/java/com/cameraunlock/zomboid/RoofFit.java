package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Lays the tiles of a roof on the planes the roof is made of.
 *
 * Each tile's heights are read off its own sprite, and each sprite is a little out: one has a
 * lip painted under its low edge, another a board up its side. Laid as read, a slope is a
 * flight of small steps. A slope is one plane, so the tiles that make one up are found, a
 * plane is put through what all of them say, and every tile takes its heights from the plane.
 * Two slopes that rise to the same ridge then meet along it.
 */
final class RoofFit {
    /** A tile that is level, one whose height changes along x, one whose height changes along y. */
    static final int LEVEL = 0, ALONG_X = 1, ALONG_Y = 2;

    /** Two tiles whose heights along the edge they share are nearer than this, in levels, are of one slope. */
    static final float JOIN = 0.2f;
    /** A slope whose tiles are all within this of its plane takes the plane. One with a tile further off keeps what was read. */
    static final float ACCEPT = 0.2f;
    /** What the game's roofs rise by over a tile, in levels, and how near a slope has to come to it to be taken as that. */
    static final float PITCH = 1f / 3f, PITCH_SLACK = 0.07f;
    /** A plane of that pitch nearer than this to meeting the tile edges at whole thirds of a level is taken to meet them there. */
    static final float THIRD_SLACK = 0.1f;

    /** One tile of roof: where it is, what its sprite says of it, and what the fit makes of it. */
    static final class Tile {
        final int x, y, z;
        /** LEVEL, ALONG_X or ALONG_Y as the sprite says, and whether it says so clearly. */
        final int axis;
        final boolean sure;
        /** The height at the tile's edge nearer the map's origin along its axis and at the far one, in levels from the ground. */
        final float low, high;
        /** The slope the tile is laid on once fitted, and its heights there. */
        int fitAxis;
        float fitLow, fitHigh;
        /** Whether the fit moved the tile onto a plane shared with other tiles. */
        boolean fitted;
        private int group = -1;

        Tile(int x, int y, int z, int axis, boolean sure, float low, float high) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.axis = axis;
            this.sure = sure;
            this.low = low;
            this.high = high;
            fitAxis = axis;
            fitLow = low;
            fitHigh = high;
        }

        private int along() {
            return axis == ALONG_X ? x : y;
        }

        private int across() {
            return axis == ALONG_X ? y : x;
        }
    }

    private RoofFit() {}

    static long key(int x, int y, int z) {
        return ((long) x & 0xFFFFFFFL) << 36 | ((long) y & 0xFFFFFFFL) << 8 | (z & 0xFFL);
    }

    /** Fits every tile of the list: see the class. */
    static void fit(List<Tile> tiles) {
        HashMap<Long, Tile> at = new HashMap<>();
        for (Tile tile : tiles) {
            at.put(key(tile.x, tile.y, tile.z), tile);
        }
        ArrayList<Tile> slope = new ArrayList<>();
        int groups = 0;
        for (Tile first : tiles) {
            if (first.group >= 0 || first.axis == LEVEL || !first.sure) {
                continue;
            }
            slope.clear();
            first.group = groups;
            slope.add(first);
            for (int next = 0; next < slope.size(); next++) {
                Tile tile = slope.get(next);
                for (int side = 0; side < 4; side++) {
                    int dx = side == 0 ? -1 : side == 1 ? 1 : 0, dy = side == 2 ? -1 : side == 3 ? 1 : 0;
                    // A slope that climbs out of the top of one level goes on at the foot of the next.
                    for (int dz = -1; dz <= 1; dz++) {
                        Tile beside = at.get(key(tile.x + dx, tile.y + dy, tile.z + dz));
                        if (beside != null && beside.group < 0 && beside.sure && joins(tile, beside)) {
                            beside.group = groups;
                            slope.add(beside);
                        }
                    }
                }
            }
            if (slope.size() > 1) {
                layOnPlane(slope);
            }
            groups++;
        }
        for (Tile tile : tiles) {
            if (!tile.fitted && tile.axis != LEVEL && !tile.sure) {
                adopt(tile, at);
            }
        }
    }

    /** Whether two tiles next to each other are of one slope: they slope the same way and agree along the edge they share. */
    private static boolean joins(Tile a, Tile b) {
        if (a.axis != b.axis || Math.signum(a.high - a.low) != Math.signum(b.high - b.low)) {
            return false;
        }
        if (a.across() == b.across()) {
            Tile nearer = a.along() < b.along() ? a : b, further = nearer == a ? b : a;
            return Math.abs(nearer.high - further.low) <= JOIN;
        }
        return Math.abs(a.low - b.low) <= JOIN && Math.abs(a.high - b.high) <= JOIN;
    }

    /** Puts the plane of least squares through the heights of a slope's tiles, and the tiles on it if they are all near it. */
    private static void layOnPlane(List<Tile> slope) {
        double sumT = 0, sumH = 0, sumTT = 0, sumTH = 0;
        int samples = 0;
        for (Tile tile : slope) {
            for (int end = 0; end <= 1; end++) {
                double t = tile.along() + end, h = end == 0 ? tile.low : tile.high;
                sumT += t;
                sumH += h;
                sumTT += t * t;
                sumTH += t * h;
                samples++;
            }
        }
        double spread = samples * sumTT - sumT * sumT;
        float rise = (float) ((samples * sumTH - sumT * sumH) / spread);
        if (Math.abs(Math.abs(rise) - PITCH) <= PITCH_SLACK) {
            rise = Math.signum(rise) * PITCH;
        }
        float atOrigin = (float) ((sumH - rise * sumT) / samples);
        if (Math.abs(rise) == PITCH) {
            float third = Math.round(atOrigin / PITCH) * PITCH;
            if (Math.abs(third - atOrigin) <= THIRD_SLACK) {
                atOrigin = third;
            }
        }
        for (Tile tile : slope) {
            float low = atOrigin + rise * tile.along(), high = low + rise;
            if (Math.abs(low - tile.low) > ACCEPT || Math.abs(high - tile.high) > ACCEPT) {
                return;
            }
        }
        for (Tile tile : slope) {
            // Each edge from its own place on the plane, so that two tiles work out the edge they share to the same number.
            tile.fitLow = atOrigin + rise * tile.along();
            tile.fitHigh = atOrigin + rise * (tile.along() + 1);
            tile.fitted = true;
        }
    }

    /**
     * A tile whose sprite does not say clearly how it lies takes the plane of the tile beside
     * it in the same row of a fitted slope. Where slopes of both ways lie beside it, it is
     * where two of them meet, a hip or a valley, and is left as read.
     */
    private static void adopt(Tile tile, HashMap<Long, Tile> at) {
        Tile from = null;
        for (int side = 0; side < 4; side++) {
            int dx = side == 0 ? -1 : side == 1 ? 1 : 0, dy = side == 2 ? -1 : side == 3 ? 1 : 0;
            Tile beside = at.get(key(tile.x + dx, tile.y + dy, tile.z));
            // In the same row of a slope: beside it across the way the slope runs.
            if (beside == null || !beside.fitted || !beside.sure || (beside.axis == ALONG_X) != (dx == 0)) {
                continue;
            }
            if (from != null && from.axis != beside.axis) {
                return;
            }
            from = beside;
        }
        if (from != null) {
            tile.fitAxis = from.axis;
            tile.fitLow = from.fitLow;
            tile.fitHigh = from.fitHigh;
            tile.fitted = true;
        }
    }
}

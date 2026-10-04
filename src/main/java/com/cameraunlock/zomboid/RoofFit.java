package com.cameraunlock.zomboid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.IntPredicate;

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
    /**
     * A tile where two slopes meet, as its sprite shows it: three of its corners at one height
     * and the fourth, the north-west one or the south-east one, at another. One corner high
     * is the end of a hip, one corner low the foot of a valley.
     */
    static final int NORTH_WEST_ODD = 3, SOUTH_EAST_ODD = 4;
    /** How a tile of two planes is folded: not at all, from its north-west corner to its south-east one, or the other way. */
    static final int UNCREASED = 0, NW_SE = 1, NE_SW = 2;
    /**
     * The two triangles a folded tile is made of, as corners of the square in the order
     * north-west, north-east, south-east, south-west. The last corner of each is the one off the fold.
     */
    static final int[][] TRIANGLES = {{0, 2, 1, 0, 2, 3}, {0, 2, 1, 0, 2, 3}, {1, 3, 2, 1, 3, 0}};
    /** Two heights nearer than this, in levels, are one height. */
    static final float SAME = 0.02f;

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
        /** Whether the sprite is a trim on the roof, narrower than a tile, and no part of a slope. */
        boolean fitting;
        /**
         * For a tile that is more than one plane, its heights at the square's corners, north-west,
         * north-east, south-east and south-west, in levels from the ground, and how it is folded.
         */
        final float[] corners = new float[4];
        int crease;
        boolean shaped;
        /** Which of a shaped tile's two triangles its own sprite lies on: bit 0 the first of TRIANGLES, bit 1 the second. */
        int own;
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

        /** Which way the fitted slope climbs along its axis: 1 towards the far edge, -1 towards the one nearer the map's origin. */
        private int up() {
            return fitLow > fitHigh ? -1 : 1;
        }

        /** Whether the tile is one whole plane of a slope, placed by its sprite or by the fit. */
        private boolean slopes() {
            return (fitAxis == ALONG_X || fitAxis == ALONG_Y) && !shaped && !fitting && (fitted || sure);
        }
    }

    /** The heights at a square's four corners of a plane with the given heights at its near and far edge along the axis. */
    static void cornersOf(int axis, float near, float far, float[] corners) {
        corners[0] = near;
        corners[1] = axis == ALONG_X ? far : near;
        corners[2] = axis == LEVEL ? near : far;
        corners[3] = axis == ALONG_Y ? far : near;
    }

    /**
     * The longest run of rows that have something on them, as its first and last row. Rows
     * with nothing on them end a run once there are more than gap of them together.
     */
    static int[] longestRun(int rows, int gap, IntPredicate has) {
        int bestFirst = 0, bestLast = -1, from = -1, empty = 0;
        for (int row = 0; row <= rows; row++) {
            if (row < rows && has.test(row)) {
                from = from < 0 ? row : from;
                empty = 0;
            } else if (from >= 0 && (++empty > gap || row == rows)) {
                int to = row - empty;
                if (to - from > bestLast - bestFirst) {
                    bestFirst = from;
                    bestLast = to;
                }
                from = -1;
                empty = 0;
            }
        }
        return new int[] {bestFirst, bestLast};
    }

    /** How a square with these corner heights is folded, when its surface is at the given height over its middle. */
    static int creaseOf(float[] corners, float middle) {
        float northWestToSouthEast = (corners[0] + corners[2]) / 2f, northEastToSouthWest = (corners[1] + corners[3]) / 2f;
        if (Math.abs(northWestToSouthEast - northEastToSouthWest) < SAME / 2f) {
            return UNCREASED;
        }
        return Math.abs(northWestToSouthEast - middle) <= Math.abs(northEastToSouthWest - middle) ? NW_SE : NE_SW;
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
            if (first.group >= 0 || first.axis != ALONG_X && first.axis != ALONG_Y || !first.sure || first.fitting) {
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
                        if (beside != null && beside.group < 0 && beside.sure && !beside.fitting && joins(tile, beside)) {
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
            if (tile.axis == NORTH_WEST_ODD || tile.axis == SOUTH_EAST_ODD) {
                fold(tile, at);
            } else if (!tile.fitted && tile.axis != LEVEL && !tile.sure) {
                adopt(tile, at);
            }
        }
    }

    /**
     * A tile where two slopes meet is both of them, folded along the line they meet on. Its
     * two heights are those of the slope it ends, where a tile of that slope lies beside it
     * and agrees with what its own sprite says.
     */
    private static void fold(Tile tile, HashMap<Long, Tile> at) {
        float northWest = tile.low, southEast = tile.high;
        for (int side = 0; side < 4; side++) {
            int dx = side == 0 ? -1 : side == 1 ? 1 : 0, dy = side == 2 ? -1 : side == 3 ? 1 : 0;
            Tile beside = at.get(key(tile.x + dx, tile.y + dy, tile.z));
            if (beside != null && beside.slopes() && (beside.fitAxis == ALONG_X) == (dx == 0)
                    && Math.abs(beside.fitLow - tile.low) <= ACCEPT && Math.abs(beside.fitHigh - tile.high) <= ACCEPT) {
                northWest = beside.fitLow;
                southEast = beside.fitHigh;
                tile.fitted = true;
                break;
            }
        }
        boolean northWestOdd = tile.axis == NORTH_WEST_ODD;
        tile.corners[0] = northWest;
        tile.corners[1] = northWestOdd ? southEast : northWest;
        tile.corners[2] = southEast;
        tile.corners[3] = northWestOdd ? southEast : northWest;
        tile.crease = NW_SE;
        tile.shaped = true;
        tile.own = 3;
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

    /** What stands round a roof, for the slopes the map leaves out. */
    interface Site {
        /**
         * Whether the building stands under the square as high as a roof whose eave is on that
         * level: it is there, and has no roof of its own on a lower level.
         */
        boolean stands(int x, int y, int z);

        /** Whether a floor lies on the square at that level. */
        boolean floored(int x, int y, int z);

        /** Whether the square at that level is a room or has a floor, or something roofs it from above. */
        boolean taken(int x, int y, int z);
    }

    /** The furthest a roof's level top is crossed to reach the place of its far slope, in tiles. */
    static final int WIDEST_TOP = 64;
    /** A tile that climbs less than this, in levels, has no slope to carry over a ridge. */
    private static final float LEAST_RISE = 0.05f;
    /** The furthest from a square, in tiles, that the building's edge is looked for where two made slopes meet. */
    private static final int FURTHEST_EAVE = 16;

    /**
     * One made slope over one square: its heights at the square's edge nearer the map's origin
     * along the slope's axis and at the far edge, and the slope it is the far side of.
     */
    private static final class Side {
        final int x, y;
        final float near, far;
        /** Whether the slope climbs from the building's edge. One that could not be falls from its ridge. */
        final boolean fromEave;
        /** The height of the eave, the climb over a tile and the height the slope stops at. */
        final float eave, pitch, top;

        Side(int x, int y, float near, float far, boolean fromEave, float eave, float pitch, float top) {
            this.x = x;
            this.y = y;
            this.near = near;
            this.far = far;
            this.fromEave = fromEave;
            this.eave = eave;
            this.pitch = pitch;
            this.top = top;
        }

        Side at(int x, int y) {
            return new Side(x, y, near, far, fromEave, eave, pitch, top);
        }

        int level() {
            return (int) Math.floor(Math.min(near, far) + LEAST_RISE);
        }

        /** The level the slope's eave is on. */
        int eaveLevel() {
            return (int) Math.floor(eave + LEAST_RISE);
        }
    }

    /**
     * The slopes of a fitted roof that the map does not hold. The game shows every roof from
     * the south-east, and its maps have tiles only for the slopes that view sees, those that
     * fall to the south and to the east: the far side of a ridge has no tile at all, and seen
     * from the north or the west the house stands open. A roof is the same both sides of its
     * ridge. So from the top tile of each row of each slope the roof is carried on: over the
     * ridge, or across the level top of a roof that has one, to the far wall, and from the
     * eave there the far slope climbs at the same pitch to the height the near one reached.
     *
     * A slope goes on along its level lines until the building ends or another slope cuts it
     * off. Where two made slopes come to the same square they meet in a hip, at a corner of
     * the building that points out, or in a valley, at one that points in: the roof there is
     * as high as its nearest eave is far, measured along whichever of the two axes is further.
     * Where a made slope runs into a slope of the map round a corner that points out, the
     * map's tile is folded: its sprite keeps the half its own slope is the lower on.
     */
    static List<Tile> backs(List<Tile> tiles, Site site) {
        HashMap<Long, Tile> at = new HashMap<>();
        HashMap<Long, Tile> roofed = new HashMap<>();
        for (Tile tile : tiles) {
            at.put(key(tile.x, tile.y, tile.z), tile);
            roofed.merge(key(tile.x, tile.y, 0), tile, (was, now) -> was.slopes() ? was : now);
        }
        HashMap<Long, Boolean> built = new HashMap<>();
        ArrayList<Tile> made = new ArrayList<>();
        mend(tiles, roofed, built, site, made);
        LinkedHashMap<Long, Side> alongX = new LinkedHashMap<>(), alongY = new LinkedHashMap<>();
        for (Tile top : tiles) {
            float rise = Math.abs(top.fitHigh - top.fitLow);
            if (!top.slopes() || rise < LEAST_RISE) {
                continue;
            }
            boolean isAlongX = top.fitAxis == ALONG_X;
            int up = top.up();
            int dx = isAlongX ? up : 0, dy = isAlongX ? 0 : up;
            float ridge = Math.max(top.fitLow, top.fitHigh);
            if (slopesOn(at, top, dx, dy, up) || cutShort(at, top, dx, dy, ridge)) {
                continue;
            }
            int climbed = 1;
            Tile lowest = top;
            for (Tile lower = top; (lower = below(at, lower, dx, dy, up)) != null; ) {
                lowest = lower;
                climbed++;
            }
            float eave = Math.min(lowest.fitLow, lowest.fitHigh);
            int whole = Math.round(ridge);
            boolean level = Math.abs(ridge - whole) < LEAST_RISE;
            int x = top.x + dx, y = top.y + dy;
            boolean bothSides = false;
            for (int crossed = 0; crossed < WIDEST_TOP && !bothSides; crossed++) {
                Tile on = roofed.get(key(x, y, 0));
                if (on == null && !(level && site.floored(x, y, whole))) {
                    break;
                }
                // The far slope is on the map: this roof has both its sides.
                bothSides = on != null && on.slopes() && on.fitAxis == top.fitAxis && on.up() == -up;
                x += dx;
                y += dy;
            }
            if (bothSides) {
                continue;
            }
            int floor = (int) Math.floor(eave + LEAST_RISE);
            int open = 0;
            while (open < WIDEST_TOP && stands(built, site, x + open * dx, y + open * dy, floor) && !roofed.containsKey(key(x + open * dx, y + open * dy, 0))
                    && !site.taken(x + open * dx, y + open * dy, floor)) {
                open++;
            }
            // A far side as deep as the near one, or deeper, stands on the far wall as the near
            // one does on its own. A shallower one, or one that ends against a higher part of
            // the building, has only the ridge to be placed by.
            boolean fromEave = open >= climbed && !stands(built, site, x + open * dx, y + open * dy, floor);
            HashMap<Long, Side> strips = isAlongX ? alongX : alongY;
            for (int step = 0; step < (fromEave ? open : Math.min(open, climbed)); step++) {
                float outer, inner;
                if (fromEave) {
                    int fromWall = open - 1 - step;
                    outer = Math.min(ridge, eave + fromWall * rise);
                    inner = Math.min(ridge, eave + (fromWall + 1) * rise);
                } else {
                    inner = ridge - step * rise;
                    outer = inner - rise;
                }
                Side side = new Side(x + step * dx, y + step * dy, up < 0 ? outer : inner, up < 0 ? inner : outer, fromEave, eave, rise, ridge);
                strips.merge(key(side.x, side.y, 0), side, (was, now) -> was.near + was.far <= now.near + now.far ? was : now);
            }
        }
        extend(alongX, true, roofed, built, site);
        extend(alongY, false, roofed, built, site);

        float[] corners = new float[4], other = new float[4];
        for (Side side : alongX.values()) {
            Side across = alongY.get(key(side.x, side.y, 0));
            float middle;
            if (across == null) {
                cornersOf(ALONG_X, side.near, side.far, corners);
                middle = (side.near + side.far) / 2f;
            } else if (side.fromEave && across.fromEave) {
                middle = fromEaves(side, across, built, site, corners);
            } else {
                cornersOf(ALONG_X, side.near, side.far, corners);
                cornersOf(ALONG_Y, across.near, across.far, other);
                for (int corner = 0; corner < 4; corner++) {
                    corners[corner] = Math.min(corners[corner], other[corner]);
                }
                middle = Math.min(side.near + side.far, across.near + across.far) / 2f;
            }
            lay(made, side.x, side.y, corners, middle, site);
        }
        for (Side side : alongY.values()) {
            if (!alongX.containsKey(key(side.x, side.y, 0))) {
                cornersOf(ALONG_Y, side.near, side.far, corners);
                lay(made, side.x, side.y, corners, (side.near + side.far) / 2f, site);
            }
        }
        return made;
    }

    /**
     * A square in the middle of a slope that the map gives no tile, the one a chimney stands
     * on, is of the slope: it has the same plane on both sides of it along the slope's level
     * line. It is made, and counts from here on as one of the roof's own tiles.
     */
    private static void mend(List<Tile> tiles, HashMap<Long, Tile> roofed, HashMap<Long, Boolean> built, Site site, List<Tile> made) {
        for (Tile tile : tiles) {
            if (!tile.slopes()) {
                continue;
            }
            int dx = tile.fitAxis == ALONG_X ? 0 : 1, dy = 1 - dx;
            int x = tile.x + dx, y = tile.y + dy;
            Tile beyond = roofed.get(key(x + dx, y + dy, 0));
            if (roofed.containsKey(key(x, y, 0)) || beyond == null || !beyond.slopes() || beyond.fitAxis != tile.fitAxis
                    || Math.abs(beyond.fitLow - tile.fitLow) > SAME || Math.abs(beyond.fitHigh - tile.fitHigh) > SAME
                    || !stands(built, site, x, y, tile.z) || site.taken(x, y, tile.z)) {
                continue;
            }
            Tile mended = new Tile(x, y, tile.z, tile.fitAxis, true, tile.fitLow, tile.fitHigh);
            mended.fitted = true;
            cornersOf(mended.fitAxis, mended.fitLow, mended.fitHigh, mended.corners);
            roofed.put(key(x, y, 0), mended);
            made.add(mended);
        }
    }

    private static boolean stands(HashMap<Long, Boolean> built, Site site, int x, int y, int z) {
        return built.computeIfAbsent(key(x, y, z), unused -> site.stands(x, y, z));
    }

    /** Makes the tile of a made roof over one square, unless the building has something else there. */
    private static void lay(List<Tile> made, int x, int y, float[] corners, float middle, Site site) {
        float lowest = Math.min(Math.min(corners[0], corners[1]), Math.min(corners[2], corners[3]));
        int z = (int) Math.floor(lowest + LEAST_RISE);
        if (site.taken(x, y, z)) {
            return;
        }
        int crease = creaseOf(corners, middle);
        boolean levelAlongX = Math.abs(corners[0] - corners[1]) < SAME && Math.abs(corners[3] - corners[2]) < SAME;
        boolean levelAlongY = Math.abs(corners[0] - corners[3]) < SAME && Math.abs(corners[1] - corners[2]) < SAME;
        Tile tile;
        if (crease == UNCREASED && levelAlongY) {
            tile = new Tile(x, y, z, levelAlongX ? LEVEL : ALONG_X, true, corners[0], corners[1]);
        } else if (crease == UNCREASED && levelAlongX) {
            tile = new Tile(x, y, z, ALONG_Y, true, corners[0], corners[3]);
        } else {
            tile = new Tile(x, y, z, LEVEL, true, lowest, lowest);
            tile.shaped = true;
            // A square whose corners are not in one plane and that the fold's rule left whole is folded the way that keeps it lowest.
            tile.crease = crease != UNCREASED ? crease : NW_SE;
        }
        System.arraycopy(corners, 0, tile.corners, 0, 4);
        tile.fitted = true;
        made.add(tile);
    }

    /**
     * The heights at a square's corners, into corners, of a roof that climbs from every edge of
     * its building at the pitch of the two slopes, and stops at the lower of their tops.
     * Returns its height over the square's middle.
     */
    private static float fromEaves(Side alongX, Side alongY, HashMap<Long, Boolean> built, Site site, float[] corners) {
        float top = Math.min(alongX.top, alongY.top);
        int reach = Math.min(FURTHEST_EAVE, 1 + (int) Math.ceil(Math.max((top - alongX.eave) / alongX.pitch, (top - alongY.eave) / alongY.pitch)));
        int level = Math.min(alongX.eaveLevel(), alongY.eaveLevel());
        float middle = top;
        corners[0] = corners[1] = corners[2] = corners[3] = top;
        for (int x = alongX.x - reach; x <= alongX.x + reach; x++) {
            for (int y = alongX.y - reach; y <= alongX.y + reach; y++) {
                if (stands(built, site, x, y, level)) {
                    continue;
                }
                for (int point = 0; point < 5; point++) {
                    float px = alongX.x + (point == 4 ? 0.5f : point == 1 || point == 2 ? 1f : 0f);
                    float py = alongX.y + (point == 4 ? 0.5f : point >= 2 ? 1f : 0f);
                    float awayX = Math.max(0f, Math.max(x - px, px - (x + 1))), awayY = Math.max(0f, Math.max(y - py, py - (y + 1)));
                    float height = Math.max(alongX.eave + alongX.pitch * awayX, alongY.eave + alongY.pitch * awayY);
                    if (point == 4) {
                        middle = Math.min(middle, height);
                    } else {
                        corners[point] = Math.min(corners[point], height);
                    }
                }
            }
        }
        return middle;
    }

    /**
     * Carries each made slope on along its level lines, square by square, over ground the
     * building stands on that nothing roofs yet. Where it comes to a tile of the map that
     * slopes the other way and falls the way the made slope is being carried, the two meet in
     * a hip and the tile is folded.
     */
    private static void extend(LinkedHashMap<Long, Side> strips, boolean alongX, HashMap<Long, Tile> roofed, HashMap<Long, Boolean> built, Site site) {
        ArrayDeque<Side> queue = new ArrayDeque<>(strips.values());
        while (!queue.isEmpty()) {
            Side from = queue.poll();
            for (int step = -1; step <= 1; step += 2) {
                int x = from.x + (alongX ? 0 : step), y = from.y + (alongX ? step : 0);
                while (!strips.containsKey(key(x, y, 0))) {
                    Tile on = roofed.get(key(x, y, 0));
                    if (on == null) {
                        if (stands(built, site, x, y, from.eaveLevel()) && !site.taken(x, y, from.eaveLevel()) && !site.taken(x, y, from.level())) {
                            Side next = from.at(x, y);
                            strips.put(key(x, y, 0), next);
                            queue.add(next);
                        }
                        break;
                    }
                    if (!hip(on, from, alongX, step)) {
                        break;
                    }
                    x += alongX ? 0 : step;
                    y += alongX ? step : 0;
                }
            }
        }
    }

    /** Folds a tile of the map where a made slope, carried onto its square, is the lower of the two over part of it. False if it is not. */
    private static boolean hip(Tile on, Side side, boolean alongX, int step) {
        if (!on.slopes() || on.fitAxis != (alongX ? ALONG_Y : ALONG_X) || -on.up() != step) {
            return false;
        }
        float[] made = new float[4], own = new float[4];
        cornersOf(alongX ? ALONG_X : ALONG_Y, side.near, side.far, made);
        cornersOf(on.fitAxis, on.fitLow, on.fitHigh, own);
        boolean madeLower = false, ownLower = false;
        for (int corner = 0; corner < 4; corner++) {
            madeLower |= made[corner] < own[corner] - SAME;
            ownLower |= own[corner] < made[corner] - SAME;
            on.corners[corner] = Math.min(made[corner], own[corner]);
        }
        if (!madeLower || !ownLower) {
            return false;
        }
        on.crease = creaseOf(on.corners, Math.min(side.near + side.far, on.fitLow + on.fitHigh) / 2f);
        if (on.crease == UNCREASED) {
            return false;
        }
        on.shaped = true;
        on.own = 0;
        for (int triangle = 0; triangle < 2; triangle++) {
            int off = TRIANGLES[on.crease][triangle * 3 + 2];
            if (own[off] <= made[off]) {
                on.own |= 1 << triangle;
            }
        }
        return true;
    }

    /** Whether a slope goes on climbing past the tile: a tile sloping the same way stands at its high edge. */
    private static boolean slopesOn(HashMap<Long, Tile> at, Tile tile, int dx, int dy, int up) {
        for (int dz = 0; dz <= 1; dz++) {
            Tile next = at.get(key(tile.x + dx, tile.y + dy, tile.z + dz));
            if (next != null && next.fitAxis == tile.fitAxis && next.up() == up) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the row of a slope that ends in this tile was cut short by a hip, and did not
     * reach the ridge: the same slope goes on up from the tile's high edge in the row beside it.
     */
    private static boolean cutShort(HashMap<Long, Tile> at, Tile top, int dx, int dy, float ridge) {
        for (int side = -1; side <= 1; side += 2) {
            for (int dz = 0; dz <= 1; dz++) {
                Tile next = at.get(key(top.x + dx + (dx == 0 ? side : 0), top.y + dy + (dy == 0 ? side : 0), top.z + dz));
                if (next != null && next.slopes() && next.fitAxis == top.fitAxis && next.up() == top.up()
                        && Math.abs(Math.min(next.fitLow, next.fitHigh) - ridge) <= JOIN) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The tile of the same slope at the tile's low edge, or null. */
    private static Tile below(HashMap<Long, Tile> at, Tile tile, int dx, int dy, int up) {
        for (int dz = 0; dz >= -1; dz--) {
            Tile next = at.get(key(tile.x - dx, tile.y - dy, tile.z + dz));
            if (next != null && next.slopes() && next.fitAxis == tile.fitAxis && next.up() == up) {
                return next;
            }
        }
        return null;
    }

    /** A strip along a ridge or down a hip: where it runs, in tiles and in levels from the ground, and how fast the roof falls away either side. */
    static final class Cap {
        final float x0, y0, high0, x1, y1, high1;
        /** How far the roof falls, in levels, a tile out from the line. */
        final float fall;

        Cap(float x0, float y0, float high0, float x1, float y1, float high1, float fall) {
            this.x0 = x0;
            this.y0 = y0;
            this.high0 = high0;
            this.x1 = x1;
            this.y1 = y1;
            this.high1 = high1;
            this.fall = fall;
        }
    }

    private static final float DIAGONAL = (float) Math.sqrt(0.5);
    /** A fold the roof falls away from by less than this, in levels a tile, is too slight to cap: the glass of a skylight is folded so. */
    private static final float LEAST_FALL = 0.1f;

    /**
     * The lines a roof is capped along: every ridge, where two slopes that climb towards each
     * other meet at their tops or at a row of level tiles between them, and every hip, the fold
     * of a tile that falls away on both sides of it. A valley, where the roof rises on both
     * sides, has no cap. tiles are the fitted tiles of the map and made those of backs.
     */
    static List<Cap> caps(List<Tile> tiles, List<Tile> made) {
        HashMap<Long, Tile> at = new HashMap<>();
        ArrayList<Tile> all = new ArrayList<>(tiles);
        all.addAll(made);
        for (Tile tile : all) {
            at.merge(key(tile.x, tile.y, tile.z), tile, (was, now) -> was.slopes() || was.shaped ? was : now);
        }
        ArrayList<Cap> caps = new ArrayList<>();
        for (Tile tile : all) {
            if (tile.shaped) {
                int from = TRIANGLES[tile.crease][0], to = TRIANGLES[tile.crease][1];
                float along = (tile.corners[from] + tile.corners[to]) / 2f;
                float off = (tile.corners[TRIANGLES[tile.crease][2]] + tile.corners[TRIANGLES[tile.crease][5]]) / 2f;
                if ((along - off) / DIAGONAL >= LEAST_FALL) {
                    caps.add(new Cap(tile.x + cornerX(from), tile.y + cornerY(from), tile.corners[from],
                            tile.x + cornerX(to), tile.y + cornerY(to), tile.corners[to], (along - off) / DIAGONAL));
                }
                continue;
            }
            if (tile.fitting || at.get(key(tile.x, tile.y, tile.z)) != tile) {
                continue;
            }
            if (tile.slopes() && tile.up() > 0) {
                boolean alongX = tile.fitAxis == ALONG_X;
                Tile facing = facing(at, tile.x + (alongX ? 1 : 0), tile.y + (alongX ? 0 : 1), tile.z, tile.fitAxis, -1, tile.fitHigh);
                if (facing != null) {
                    float high = Math.max(tile.fitHigh, facing.fitLow);
                    caps.add(alongX ? new Cap(tile.x + 1f, tile.y, high, tile.x + 1f, tile.y + 1f, high, tile.fitHigh - tile.fitLow)
                            : new Cap(tile.x, tile.y + 1f, high, tile.x + 1f, tile.y + 1f, high, tile.fitHigh - tile.fitLow));
                }
            } else if (tile.fitAxis == LEVEL) {
                for (int axis = ALONG_X; axis <= ALONG_Y; axis++) {
                    boolean alongX = axis == ALONG_X;
                    float level = (tile.fitLow + tile.fitHigh) / 2f;
                    Tile before = facing(at, tile.x - (alongX ? 1 : 0), tile.y - (alongX ? 0 : 1), tile.z, axis, 1, level);
                    Tile after = facing(at, tile.x + (alongX ? 1 : 0), tile.y + (alongX ? 0 : 1), tile.z, axis, -1, level);
                    if (before != null && after != null) {
                        float high = Math.max(level, Math.max(before.fitHigh, after.fitLow));
                        // The roof is level under this one from side to side.
                        caps.add(alongX ? new Cap(tile.x + 0.5f, tile.y, high, tile.x + 0.5f, tile.y + 1f, high, 0f)
                                : new Cap(tile.x, tile.y + 0.5f, high, tile.x + 1f, tile.y + 0.5f, high, 0f));
                    }
                }
            }
        }
        return caps;
    }

    static float cornerX(int corner) {
        return corner == 1 || corner == 2 ? 1f : 0f;
    }

    static float cornerY(int corner) {
        return corner >= 2 ? 1f : 0f;
    }

    /** The whole tile of a slope along the axis on the square, on its level or the one above or below, that climbs the given way to about the given height, or null. */
    private static Tile facing(HashMap<Long, Tile> at, int x, int y, int z, int axis, int up, float ridge) {
        for (int dz = -1; dz <= 1; dz++) {
            Tile tile = at.get(key(x, y, z + dz));
            if (tile != null && tile.slopes() && tile.fitAxis == axis && tile.up() == up && Math.abs(Math.max(tile.fitLow, tile.fitHigh) - ridge) <= JOIN) {
                return tile;
            }
        }
        return null;
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
            if (beside == null || !beside.fitted || !beside.slopes() || (beside.axis == ALONG_X) != (dx == 0)) {
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

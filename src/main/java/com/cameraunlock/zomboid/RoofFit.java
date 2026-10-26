package com.cameraunlock.zomboid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
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
    /**
     * Two whole tiles one after the other along their slope whose climbs over a tile differ by
     * more than this, in levels, are of two pitches: a slope and the shallower kick it runs out
     * into at its eave. Laid on one plane the slope was flattened to the kick's pitch, and had no
     * far side made for it (a porch roof at 3661..3663, 5715..5725, roofs_05_4 over 97 and 96).
     * A gambrel's upper slope climbs half the standard pitch, a difference of just this: taken as
     * one plane with the lower slope, the barn's near side was laid straight from eave to ridge at
     * a quarter of a level a tile (7621..7642 x 14354..14359).
     */
    static final float KINK = PITCH / 2f - SAME;
    static final int EAST_HALF = 1, SOUTH_HALF = 2;
    /** How many squares towards the map's origin, and thirds of a level down, a half tile is looked for its place: see peaks. */
    private static final int FURTHEST_SHIFT = 2;
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
        /**
         * For a piece half a tile wide, the top of a slope whose ridge runs along the middle of a
         * square: EAST_HALF or SOUTH_HALF, the half of its square it lies on, and its height at
         * its low edge, the square's, in levels from the ground. 0 for any other tile. See peaks.
         */
        int half;
        float halfEdge;
        /**
         * For a level tile under a ridge that runs along its middle: how far the ridge stands
         * over the tile's two edges, in levels, and the axis the roof climbs to it along. 0 for a
         * tile that is level.
         */
        float peak;
        int peakAxis;
        /**
         * How far a ridge along the middle of the tile runs on past the tile's edge nearer the
         * map's origin and past its far edge, in tiles: half a tile where the ridge runs into the
         * middle of a ridge across it, a wing's into the main roof's, and 0 elsewhere. See peaks.
         */
        float peakBefore, peakAfter;
        /** Whether the tile is a slope carried on over the square of another, to meet it in a valley: see valleys. */
        boolean carried;
        /**
         * For a tile the map cuts to a triangle, the corner of its square that is cut away, as
         * cornerX and cornerY number them: the north-west or the south-east. The tile is the half
         * of its plane on the other side of the line from the south-west corner to the north-east.
         * -1 for a whole tile. See Roofs.cut.
         */
        int cutCorner = -1;
        /** Whether the tile is a level sheet the map marks as no tile of a roof: see Roofs.flatTop and behindRidges. */
        boolean sheet;
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

    /** A line of sight that climbs less than this over a roof's plane, in levels a tile along the map's diagonal, runs along the roof. */
    private static final float SIGHT_LEAST = 0.1f;

    /**
     * Where something the game paints standing on the floor of a roof's level, at (a, b) on its square, stands on the
     * roof: into out as a and b on the square and its height over that floor, in levels. corners are the roof's heights
     * over the floor at the square's corners, north-west, north-east, south-east and south-west, and the roof is taken
     * as the plane through them. The game's view draws a point (d, d) further along the map and d/3 of a level higher
     * on the same spot, so the thing is slid along that line of sight to where it meets the roof, and no further than
     * the edge of its square: the corners are the roof over this square alone, and past a ridge the line of sight would
     * meet the plane squares on. Stopped there, it stands on the roof at that edge. On a slope the line runs along,
     * which the game's view sees edge on or not at all, it is raised to the roof over (a, b).
     */
    static void standOn(float[] corners, float a, float b, float[] out) {
        float alongX = (corners[1] + corners[2] - corners[0] - corners[3]) / 2f, alongY = (corners[2] + corners[3] - corners[0] - corners[1]) / 2f;
        float middle = (corners[0] + corners[1] + corners[2] + corners[3]) / 4f;
        float at = middle + alongX * (a - 0.5f) + alongY * (b - 0.5f);
        float sight = PITCH - alongX - alongY;
        if (sight < SIGHT_LEAST) {
            out[0] = a;
            out[1] = b;
            out[2] = at;
            return;
        }
        float slid = Math.clamp(at / sight, -Math.min(a, b), 1f - Math.max(a, b));
        out[0] = a + slid;
        out[1] = b + slid;
        out[2] = at + (alongX + alongY) * slid;
    }

    /**
     * The tile a roof sprite on a square is laid on, given the tile the fit left there and
     * whether the sprite is that tile's own: its own sprite on it, and a second sprite on a
     * valley's fold where the tile's own sprite lies on some of it, which is a piece of the same
     * picture of where the two slopes meet (the top of a valley), laid on the triangles the
     * tile's own sprite is on. Any other second sprite on none, null: it lies as it reads. On a
     * hip's fold a second sprite is no piece of the hip (a flat roof's sheet the hip stands
     * on), and laid on the fold it lay in the hip's own sprite's plane, or over the triangle
     * the mod's own sheet closes.
     */
    static Tile laidOn(Tile fitted, boolean itsOwn) {
        return fitted != null && (itsOwn || valley(fitted) && fitted.own != 0) ? fitted : null;
    }

    /** Whether a tile is folded into a valley: its fold lower than its two corners off the fold. */
    static boolean valley(Tile tile) {
        if (!tile.shaped || tile.crease == UNCREASED) {
            return false;
        }
        int[] order = TRIANGLES[tile.crease];
        return tile.corners[order[0]] + tile.corners[order[1]] < tile.corners[order[2]] + tile.corners[order[5]] - SAME;
    }

    /**
     * Whether a tile is a whole plane of a slope that falls along the axis, towards the far
     * edge, to the floor of its level there: the lowest tile of a slope whose eave is that edge.
     */
    static boolean fallsToItsFloor(Tile tile, int axis) {
        return tile.slopes() && tile.fitAxis == axis && tile.fitHigh < tile.fitLow && Math.abs(tile.fitHigh - tile.z) < Roofs.FLAT;
    }

    /**
     * Whether a folded tile's far edge along the axis, its east edge along x or its south one
     * along y, lies at the floor of its level with the roof rising from it: an eave of the fold
     * (RoofBuilder.addFoldEaves). The corner of a hipped roof the map cuts to a triangle is
     * folded so, its outer two edges both at the eave.
     */
    static boolean foldFallsToItsFloor(Tile tile, int axis) {
        if (!tile.shaped) {
            return false;
        }
        // Corners north-west, north-east, south-east, south-west: the far edge has the south-east and one of the others.
        boolean alongX = axis == ALONG_X;
        return Math.abs(tile.corners[alongX ? 1 : 3] - tile.z) < Roofs.FLAT && Math.abs(tile.corners[2] - tile.z) < Roofs.FLAT
                && Math.max(tile.corners[0], tile.corners[alongX ? 3 : 1]) > tile.z + Roofs.FLAT;
    }

    /** As fallsToItsFloor, for a slope that falls the other way, to the edge nearer the map's origin. */
    static boolean risesFromItsFloor(Tile tile, int axis) {
        return tile.slopes() && tile.fitAxis == axis && tile.fitLow < tile.fitHigh && Math.abs(tile.fitLow - tile.z) < Roofs.FLAT;
    }

    /** Whether a tile of a roof, no trim, lies level at the floor of its level: the corner of a hipped roof at its eave, as the map draws it. */
    static boolean levelOnItsFloor(Tile tile) {
        return !tile.fitting && !tile.shaped && tile.half == 0 && tile.fitAxis == LEVEL && Math.abs(tile.fitLow - tile.z) < Roofs.FLAT
                && Math.abs(tile.fitHigh - tile.z) < Roofs.FLAT;
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
        levelPastTheCrown(tiles, at);
        for (Tile tile : tiles) {
            if (tile.cutCorner >= 0) {
                foldCut(tile, at);
            } else if (tile.axis == NORTH_WEST_ODD || tile.axis == SOUTH_EAST_ODD) {
                fold(tile, at);
            } else if (!tile.fitted && tile.axis != LEVEL && !tile.sure) {
                adopt(tile, at);
            } else if (tile.axis == LEVEL && !tile.fitting && tile.half == 0) {
                goOnDown(tile, at);
            }
        }
    }

    /**
     * A row of a slope that climbs from a crown's height on into the crown is laid level there: the map put the crown a row
     * lower than the slope's top. Left climbing, the row stood a third of a level over the crown along its whole edge, and
     * the far side made from it climbed past the crown the same, so both ends of the roof kicked up (12798..12807, 3240..3247).
     * The rest of the row along its level line goes level with it, past squares where no crown is beside it.
     */
    private static void levelPastTheCrown(List<Tile> tiles, HashMap<Long, Tile> at) {
        ArrayList<Tile> level = new ArrayList<>();
        for (Tile tile : tiles) {
            if (!tile.slopes() || Math.abs(Math.abs(tile.fitHigh - tile.fitLow) - PITCH) > PITCH_SLACK) {
                continue;
            }
            boolean isAlongX = tile.fitAxis == ALONG_X;
            int up = tile.up();
            Tile crown = at.get(key(tile.x + (isAlongX ? up : 0), tile.y + (isAlongX ? 0 : up), tile.z));
            if (crown != null && crown.sheet && Math.abs(crown.fitLow - Math.min(tile.fitLow, tile.fitHigh)) < SAME) {
                level.add(tile);
            }
        }
        for (int next = 0; next < level.size(); next++) {
            Tile tile = level.get(next);
            for (int side = -1; side <= 1; side += 2) {
                Tile beside = at.get(key(tile.x + (tile.fitAxis == ALONG_Y ? side : 0), tile.y + (tile.fitAxis == ALONG_X ? side : 0), tile.z));
                if (beside != null && beside.slopes() && beside.fitAxis == tile.fitAxis && Math.abs(beside.fitLow - tile.fitLow) < SAME
                        && Math.abs(beside.fitHigh - tile.fitHigh) < SAME && !level.contains(beside)) {
                    level.add(beside);
                }
            }
        }
        for (Tile tile : level) {
            float foot = Math.min(tile.fitLow, tile.fitHigh);
            tile.fitAxis = LEVEL;
            tile.fitLow = foot;
            tile.fitHigh = foot;
        }
    }

    /**
     * The lowest row of a slope that faces away from the game's view is a sliver of sprite, and
     * reads as a level tile at the height of the eave. Laid level it is a ledge along the foot
     * of the roof with a step up to the slope behind it. Where a level tile lies at the low
     * edge of a slope, a tile's climb below that edge, it is the slope going on down.
     */
    private static void goOnDown(Tile tile, HashMap<Long, Tile> at) {
        for (int side = 0; side < 4; side++) {
            int dx = side == 0 ? -1 : side == 1 ? 1 : 0, dy = side == 2 ? -1 : side == 3 ? 1 : 0;
            Tile above = at.get(key(tile.x + dx, tile.y + dy, tile.z));
            if (above == null || !above.slopes() || above.fitAxis != (dx != 0 ? ALONG_X : ALONG_Y) || above.up() != dx + dy) {
                continue;
            }
            float edge = Math.min(above.fitLow, above.fitHigh), rise = Math.abs(above.fitHigh - above.fitLow);
            if (rise < LEAST_RISE || Math.abs(tile.low - (edge - rise)) > PITCH_SLACK) {
                continue;
            }
            tile.fitAxis = above.fitAxis;
            tile.fitLow = dx + dy > 0 ? edge - rise : edge;
            tile.fitHigh = dx + dy > 0 ? edge : edge - rise;
            tile.fitted = true;
            return;
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

    /**
     * A tile cut to a triangle is half of a hip: the slope that would meet it along the cut
     * faces away from the game's view, and the map leaves that half of the square bare. Where
     * the roof goes on past the corner cut away, on either square beside it, the other half is
     * that slope: the tile's own plane turned over the cut, so that the corner cut away is as
     * high as the one across from it, and the tile is folded along the cut with the mod's own
     * sheet on the half its sprite has nothing of. Where no roof is beside it there, the slope
     * ends at the cut with nothing behind it and the tile is left a triangle: see
     * WorldScene.underCut.
     */
    private static void foldCut(Tile tile, HashMap<Long, Tile> at) {
        int step = tile.cutCorner == 0 ? -1 : 1;
        if (at.get(key(tile.x + step, tile.y, tile.z)) == null && at.get(key(tile.x, tile.y + step, tile.z)) == null) {
            return;
        }
        cornersOf(tile.fitAxis, tile.fitLow, tile.fitHigh, tile.corners);
        tile.corners[tile.cutCorner] = tile.corners[2 - tile.cutCorner];
        tile.crease = NE_SW;
        tile.shaped = true;
        // The first of the fold's two triangles has the south-east corner, the second the north-west.
        tile.own = tile.cutCorner == 0 ? 1 : 2;
    }

    /** Whether two tiles next to each other are of one slope: they slope the same way and agree along the edge they share. */
    private static boolean joins(Tile a, Tile b) {
        if (a.axis != b.axis || Math.signum(a.high - a.low) != Math.signum(b.high - b.low)) {
            return false;
        }
        if (a.across() == b.across()) {
            Tile nearer = a.along() < b.along() ? a : b, further = nearer == a ? b : a;
            return Math.abs(nearer.high - further.low) <= JOIN && (a.half != 0 || b.half != 0 || Math.abs((a.high - a.low) - (b.high - b.low)) <= KINK);
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

        /**
         * Whether the square at that level is a room or has a floor, or something roofs it from
         * above. roof is the least height, in levels, a roof made over the square stands at
         * there: a floor that is no room, on a level above z and no higher than that, is the
         * ceiling the map lays over the loft inside that roof, under its ridge, and does not count.
         */
        boolean taken(int x, int y, int z, float roof);

        /**
         * Whether the square's floor at that level is a sheet of a roof's laid as a floor (Roofs.hiddenSheet) over another
         * room than the square beside it, (fromX, fromY), has under it: the flat roof of a lower part of the building.
         */
        default boolean annex(int fromX, int fromY, int x, int y, int z) {
            return false;
        }

        /** Whether the square at that level is a room of a storey: one with floors, not the space under a roof. */
        default boolean storey(int x, int y, int z) {
            return false;
        }
    }

    /** The furthest a roof's level top is crossed to reach the place of its far slope, in tiles. */
    static final int WIDEST_TOP = 64;
    /** A tile that climbs less than this, in levels, has no slope to carry over a ridge. */
    private static final float LEAST_RISE = 0.05f;
    /** The furthest from a square, in tiles, that the building's edge is looked for where two made slopes meet. */
    private static final int FURTHEST_EAVE = 16;

    /** The far side of one row of a slope, as backs works it out: where it starts, which way it runs, how deep it is and how it is placed. */
    private static final class Row {
        final int x, y, dx, dy, climbed;
        int open;
        boolean fromEave, valley;
        final boolean crowned;
        final float eave, rise, ridge;

        Row(int x, int y, int dx, int dy, int open, int climbed, boolean fromEave, boolean crowned, float eave, float rise, float ridge) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.open = open;
            this.climbed = climbed;
            this.fromEave = fromEave;
            this.crowned = crowned;
            this.eave = eave;
            this.rise = rise;
            this.ridge = ridge;
        }
    }

    /**
     * One made slope over one square: its heights at the square's edge nearer the map's origin
     * along the slope's axis and at the far edge, and the slope it is the far side of.
     */
    private static final class Side {
        final int x, y;
        final float near, far;
        /** Whether the slope climbs from the building's edge. One that could not be falls from its ridge. */
        final boolean fromEave;
        /** Whether it falls from its ridge into the far side of a roof across it, and meets that in a valley. */
        final boolean valley;
        /** The height of the eave, the climb over a tile and the height the slope stops at. */
        final float eave, pitch, top;
        /** Which way along its axis the slope climbs, as Tile.up, the same over the level top it stops at. */
        final int up;

        Side(int x, int y, float near, float far, boolean fromEave, boolean valley, float eave, float pitch, float top, int up) {
            this.x = x;
            this.y = y;
            this.near = near;
            this.far = far;
            this.fromEave = fromEave;
            this.valley = valley;
            this.eave = eave;
            this.pitch = pitch;
            this.top = top;
            this.up = up;
        }

        Side at(int x, int y) {
            return new Side(x, y, near, far, fromEave, valley, eave, pitch, top, up);
        }

        /** The level the slope's eave is on. */
        int eaveLevel() {
            return (int) Math.floor(eave + LEAST_RISE);
        }
    }

    /**
     * The level sheets of a roof (Tile.sheet) that lie behind a ridge and are no part of what
     * shows: the map paints a sheet a third of a level up over the far side of a roof as well
     * as over the crown of a hip cut short, and the far side is the one the game's view never
     * sees. Sheets joined at one height are one piece. A crown is what its slopes climb to, so
     * the slopes beside it meet it at its height; a piece whose edge runs more along slopes that
     * meet it higher up than along slopes that meet it at its height lies behind their ridge (a
     * long gable's ridge along one side, the ends of the house meeting it at its height). Left
     * in, the far slope made over it stopped at the sheets' height, a step below the ridge. A
     * piece with no slope beside it is a flat roof.
     */
    static List<Tile> behindRidges(List<Tile> tiles) {
        HashMap<Long, Tile> at = new HashMap<>();
        for (Tile tile : tiles) {
            at.put(key(tile.x, tile.y, tile.z), tile);
        }
        ArrayList<Tile> behind = new ArrayList<>();
        HashMap<Tile, Boolean> seen = new HashMap<>();
        for (Tile first : tiles) {
            if (!first.sheet || seen.containsKey(first)) {
                continue;
            }
            ArrayList<Tile> piece = new ArrayList<>();
            ArrayDeque<Tile> queue = new ArrayDeque<>();
            queue.add(first);
            seen.put(first, true);
            int climbedTo = 0, under = 0;
            while (!queue.isEmpty()) {
                Tile sheet = queue.poll();
                piece.add(sheet);
                for (int side = 0; side < 4; side++) {
                    int dx = side == 0 ? -1 : side == 1 ? 1 : 0, dy = side == 2 ? -1 : side == 3 ? 1 : 0;
                    Tile next = at.get(key(sheet.x + dx, sheet.y + dy, sheet.z));
                    if (next != null && next.sheet && Math.abs(next.low - first.low) < SAME) {
                        if (!seen.containsKey(next)) {
                            seen.put(next, true);
                            queue.add(next);
                        }
                        continue;
                    }
                    for (int dz = -1; dz <= 1; dz++) {
                        Tile beside = at.get(key(sheet.x + dx, sheet.y + dy, sheet.z + dz));
                        if (beside == null || beside.sheet || beside.shaped) {
                            continue;
                        }
                        // The slope's height along the edge it shares with the sheet: its low edge is the one nearer the map's origin.
                        float edge = beside.axis == ALONG_X && dx != 0 ? (dx > 0 ? beside.low : beside.high)
                                : beside.axis == ALONG_Y && dy != 0 ? (dy > 0 ? beside.low : beside.high) : Float.NaN;
                        climbedTo += Math.abs(edge - first.low) <= PITCH / 2f ? 1 : 0;
                        under += edge > first.low + PITCH / 2f ? 1 : 0;
                    }
                }
            }
            if (under > climbedTo) {
                behind.addAll(piece);
            }
        }
        return behind;
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
        // The squares of a crown that a far slope comes down over before it meets the crown's height.
        HashSet<Long> over = new HashSet<>();
        ArrayList<Row> rows = new ArrayList<>();
        HashMap<Long, Row> rowAt = new HashMap<>();
        for (Tile top : tiles) {
            float rise = Math.abs(top.fitHigh - top.fitLow);
            // A slope at another pitch is a canopy or a lean-to over a porch, and has no far side: carried
            // across to the far wall it laid a level roof over the whole house at the canopy's height.
            if (!top.slopes() || rise < LEAST_RISE || Math.abs(rise - PITCH) > PITCH_SLACK) {
                continue;
            }
            boolean isAlongX = top.fitAxis == ALONG_X;
            int up = top.up();
            int dx = isAlongX ? up : 0, dy = isAlongX ? 0 : up;
            float ridge = Math.max(top.fitLow, top.fitHigh);
            if (slopesOn(at, top, dx, dy, up) || cutShort(at, top, dx, dy, ridge) || endOfAHip(roofed, top, dx, dy, ridge)) {
                continue;
            }
            int climbed = 1;
            Tile lowest = top;
            for (Tile lower = top; (lower = below(at, lower, dx, dy, up)) != null; ) {
                lowest = lower;
                climbed++;
            }
            float eave = Math.min(lowest.fitLow, lowest.fitHigh);
            // A row whose foot runs under a wing is cut short there, by the valley's tiles: the
            // slope goes on down under the wing to the eave the rest of its plane comes down to.
            // A row that ends at its own eave keeps it, though more of its plane comes down
            // further: a terrace's deeper neighbour under one ridge, a roof that runs on down
            // over a lean-to beside it. Taken down a storey there, the far slope looked for
            // its place on the storey under the roof, found rooms and was not made.
            // The plane is looked for among every tile of the map, fitted or not: the hip end of a wing
            // whose lower row is the valley's tile is no plane of the fit's, and its row read its eave a
            // row up, so the far slope stood a third of a level over the wall (3919, 13816..13820).
            float planeEave = cutByAWing(at, lowest, dx, dy) ? eaveOfPlane(tiles, top) : eave;
            if (planeEave < eave - SAME) {
                eave = planeEave;
                climbed = Math.round((ridge - eave) / rise);
            }
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
                // Any other slope there is a wing's roof and no level top: crossed, the far side stood at
                // the ridge's height on the wall beyond the wing (1469..1472, 9850).
                if (on != null && on.slopes()) {
                    break;
                }
                x += dx;
                y += dy;
            }
            if (bothSides) {
                continue;
            }
            int floor = (int) Math.floor(eave + LEAST_RISE);
            // The far side stands on the top of the storey it covers, never in it. A near side that runs on down in one plane over a
            // lower part of the building has its eave a storey under the far side's rooms: a house's south slope comes down over its
            // porch on level 1 (871..874, 12521..12526), and its far side fell from the ridge to the floor of level 2 there, a sunken
            // flat beside the rows to the east, which stand on the north wall (869..878, 12515..12520).
            while (floor + 1 < ridge - SAME && site.storey(x, y, floor)) {
                floor++;
                eave = floor;
                climbed = Math.round((ridge - eave) / rise);
            }
            int open = 0;
            // The far slope over a square stands no lower than the ridge less a tile's climb for every square from the ridge to the square's far edge.
            // It comes down over a lower crown behind the ridge until it meets the crown's height: the map has no tile
            // for the far slope there, and stopped at the crown's edge it stood a third of a level over it, a ledge open
            // underneath (a cross gable before a hip cut short, 10402..10411 x 12658..12669).
            ArrayList<Long> overCrown = new ArrayList<>();
            boolean metCrown = false, annex = false;
            while (open < WIDEST_TOP && stands(built, site, x + open * dx, y + open * dy, floor)) {
                // A sheet over another room is no foot of this slope but the flat roof of a lower part of the building
                // beside it, whose wall the slope comes down to. Carried on over it, the far slope's eave stood on the
                // annex's far wall and its middle went into the annex's roof (3913..3914, 13821).
                if (site.annex(x + (open - 1) * dx, y + (open - 1) * dy, x + open * dx, y + open * dy, floor)) {
                    annex = true;
                    break;
                }
                long square = key(x + open * dx, y + open * dy, 0);
                float foot = ridge - (open + 1) * rise;
                Tile on = roofed.get(square);
                metCrown |= on != null && on.sheet;
                if (on != null ? !(on.sheet && on.fitLow <= foot + SAME) : site.taken(x + open * dx, y + open * dy, floor, foot)) {
                    break;
                }
                if (on != null) {
                    overCrown.add(square);
                }
                open++;
            }
            // A far side as deep as the near one, or deeper, stands on the far wall as the near
            // one does on its own. A shallower one, or one that ends against a higher part of
            // the building, has only the ridge to be placed by.
            boolean fromEave = open >= climbed && (annex || !stands(built, site, x + open * dx, y + open * dy, floor));
            over.addAll(overCrown);
            Row row = new Row(x, y, dx, dy, open, climbed, fromEave, metCrown, eave, rise, ridge);
            rows.add(row);
            rowAt.putIfAbsent(rowKey(x, y, dx, dy), row);
        }
        // The row just past the end of a crown is of one far side with the row beside it that comes
        // down onto the crown: it falls from the ridge as deep. Carried on to the far wall, at the
        // gable end of a cross gable before a hip cut short, it ran on at the ridge's height over the
        // crown's own far slope (10402, 12658..12665). Rows further along keep their own far side.
        ArrayList<Row> cut = new ArrayList<>();
        ArrayList<Integer> cutTo = new ArrayList<>();
        for (Row row : rows) {
            if (!row.fromEave || row.crowned) {
                continue;
            }
            for (int side = -1; side <= 1; side += 2) {
                Row beside = rowAt.get(rowKey(row.x + (row.dx == 0 ? side : 0), row.y + (row.dy == 0 ? side : 0), row.dx, row.dy));
                if (beside != null && beside.crowned && !beside.fromEave && Math.abs(beside.ridge - row.ridge) < SAME) {
                    cut.add(row);
                    cutTo.add(Math.min(row.open, beside.open));
                    break;
                }
            }
        }
        for (int i = 0; i < cut.size(); i++) {
            cut.get(i).fromEave = false;
            cut.get(i).open = cutTo.get(i);
        }
        // A far side that runs on past the depth of its near one into the far side of a roof
        // across it has come to where a wing meets the main roof, and both far sides are made:
        // it falls from its ridge as deep as the near side and meets the other in a valley.
        // Carried on to the far wall, the wing's far side climbed from the main roof's end wall
        // and the main roof's from the wing's, each a level top over the other (the church at
        // 3532..3549, 11199..11211).
        HashSet<Long> spanX = new HashSet<>(), spanY = new HashSet<>();
        // Past the depth of its near side, a far side that stands on its wall runs on level along its ridge: a wing lower than
        // that ridge runs into it there too. Left out, the main roof's north side four rows deep left its last row to a low
        // wing's far side, which was carried on over it as a flat at the wing's ridge (6277..6280, 5410..5413).
        HashMap<Long, Float> ridgeX = new HashMap<>(), ridgeY = new HashMap<>();
        for (Row row : rows) {
            for (int step = 0; step < (row.fromEave ? row.open : Math.min(row.open, row.climbed)); step++) {
                long square = key(row.x + step * row.dx, row.y + step * row.dy, 0);
                if (step < row.climbed) {
                    (row.dx != 0 ? spanX : spanY).add(square);
                }
                (row.dx != 0 ? ridgeX : ridgeY).merge(square, row.ridge, Math::max);
            }
        }
        for (Row row : rows) {
            if (!row.fromEave || row.open <= row.climbed) {
                continue;
            }
            long past = key(row.x + row.climbed * row.dx, row.y + row.climbed * row.dy, 0);
            Float across = (row.dx != 0 ? ridgeY : ridgeX).get(past);
            if ((row.dx != 0 ? spanY : spanX).contains(past) || across != null && across > row.ridge + row.rise - SAME) {
                row.open = row.climbed;
                row.fromEave = false;
                row.valley = true;
            }
        }
        for (Row row : rows) {
            boolean fromEave = row.fromEave, isAlongX = row.dx != 0;
            int open = row.open, climbed = row.climbed, dx = row.dx, dy = row.dy, x = row.x, y = row.y, up = dx + dy;
            float eave = row.eave, rise = row.rise, ridge = row.ridge;
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
                Side side = new Side(x + step * dx, y + step * dy, up < 0 ? outer : inner, up < 0 ? inner : outer, fromEave, row.valley, eave, rise, ridge, -up);
                strips.merge(key(side.x, side.y, 0), side, (was, now) -> was.near + was.far <= now.near + now.far ? was : now);
            }
        }
        gambrels(tiles, at, roofed, built, site, alongX, alongY);
        // A crown's edge that no slope climbs to, map's or made, stands on the wall there: the hip
        // climbs from the wall to it, as the map's own slopes do on the sides it draws. Left out, the
        // crown of a hip cut short ended in a ledge over the wall on its north and west sides.
        for (Tile crown : tiles) {
            if (!crown.sheet) {
                continue;
            }
            int deep = Math.round((crown.fitLow - crown.z) / PITCH);
            if (deep < 1 || Math.abs(crown.z + deep * PITCH - crown.fitLow) > SAME) {
                continue;
            }
            for (int side = 0; side < 4; side++) {
                int dx = side == 0 ? -1 : side == 1 ? 1 : 0, dy = side == 2 ? -1 : side == 3 ? 1 : 0;
                HashMap<Long, Side> strips = dx != 0 ? alongX : alongY;
                for (int out = 1; out <= deep; out++) {
                    int x = crown.x + out * dx, y = crown.y + out * dy;
                    float inner = crown.fitLow - (out - 1) * PITCH, outer = inner - PITCH;
                    // A far side falling from a ridge across the way is the roof there, as in extend.
                    Side other = (dx != 0 ? alongY : alongX).get(key(x, y, 0));
                    if (roofed.containsKey(key(x, y, 0)) || strips.containsKey(key(x, y, 0)) || other != null && !other.fromEave || !stands(built, site, x, y, crown.z) || site.taken(x, y, crown.z, outer)) {
                        break;
                    }
                    boolean outward = dx + dy > 0;
                    strips.put(key(x, y, 0), new Side(x, y, outward ? inner : outer, outward ? outer : inner, true, false, crown.z, PITCH, crown.fitLow, outward ? -1 : 1));
                }
            }
        }
        HashMap<Long, Side> laidX = new HashMap<>(alongX), laidY = new HashMap<>(alongY);
        extend(alongX, true, roofed, built, site, laidY, over);
        extend(alongY, false, roofed, built, site, laidX, over);

        float[] corners = new float[4], other = new float[4];
        for (Side side : alongX.values()) {
            Side across = alongY.get(key(side.x, side.y, 0));
            float middle;
            if (across == null) {
                cornersOf(ALONG_X, side.near, side.far, corners);
                middle = notched(side, true, built, site, alongX, corners, (side.near + side.far) / 2f);
            } else if (side.fromEave && across.fromEave) {
                middle = fromEaves(side, across, built, site, corners);
            } else if (side.valley || across.valley) {
                cornersOf(ALONG_X, side.near, side.far, corners);
                cornersOf(ALONG_Y, across.near, across.far, other);
                for (int corner = 0; corner < 4; corner++) {
                    corners[corner] = Math.max(corners[corner], other[corner]);
                }
                middle = Math.max(side.near + side.far, across.near + across.far) / 2f;
            } else {
                cornersOf(ALONG_X, side.near, side.far, corners);
                cornersOf(ALONG_Y, across.near, across.far, other);
                for (int corner = 0; corner < 4; corner++) {
                    corners[corner] = Math.min(corners[corner], other[corner]);
                }
                middle = Math.min(side.near + side.far, across.near + across.far) / 2f;
            }
            lay(made, side.x, side.y, corners, middle, across == null ? side.eaveLevel() : Math.min(side.eaveLevel(), across.eaveLevel()), site, over);
        }
        for (Side side : alongY.values()) {
            if (!alongX.containsKey(key(side.x, side.y, 0))) {
                cornersOf(ALONG_Y, side.near, side.far, corners);
                lay(made, side.x, side.y, corners, notched(side, false, built, site, alongY, corners, (side.near + side.far) / 2f), side.eaveLevel(), site, over);
            }
        }
        return made;
    }

    /**
     * The far lower slopes of a gambrel roof. Its near side climbs from the eave at the standard
     * pitch to a crease and on at a shallower one to the ridge, and the map holds the far upper
     * slope as well, coming down from the ridge to the crease, but nothing under it: the near
     * lower slope's top row runs on into the upper slope and the upper slope is at no pitch a far
     * side is made for, so no row was carried over, and the barn stood open from the crease down
     * to the ceiling under it (7621..7642 x 14348..14359, 2384..2396 x 6779..6803). From the foot of
     * each such tile the far lower slope comes down at the near one's pitch, as many rows as it.
     */
    private static void gambrels(List<Tile> tiles, HashMap<Long, Tile> at, HashMap<Long, Tile> roofed, HashMap<Long, Boolean> built, Site site,
            HashMap<Long, Side> alongX, HashMap<Long, Side> alongY) {
        for (Tile upper : tiles) {
            float rise = Math.abs(upper.fitHigh - upper.fitLow);
            if (!upper.slopes() || rise < LEAST_RISE) {
                continue;
            }
            boolean isAlongX = upper.fitAxis == ALONG_X;
            int up = upper.up();
            int dx = isAlongX ? up : 0, dy = isAlongX ? 0 : up;
            if (below(at, upper, dx, dy, up) != null) {
                continue;
            }
            float crease = Math.min(upper.fitLow, upper.fitHigh);
            // Across the ridge, the near upper slope, its foot at the same height, and the lower slope it stands on.
            Tile foot = null;
            for (int across = 1; across <= WIDEST_TOP && foot == null; across++) {
                Tile on = roofed.get(key(upper.x + across * dx, upper.y + across * dy, 0));
                if (on == null || !on.slopes() || on.fitAxis != upper.fitAxis) {
                    break;
                }
                if (on.up() == -up) {
                    foot = on;
                }
            }
            if (foot == null) {
                continue;
            }
            Tile lower;
            while ((lower = below(at, foot, -dx, -dy, -up)) != null && Math.abs(Math.abs(lower.fitHigh - lower.fitLow) - rise) <= PITCH_SLACK) {
                foot = lower;
            }
            if (lower == null || Math.abs(Math.min(foot.fitLow, foot.fitHigh) - crease) > JOIN) {
                continue;
            }
            float pitch = Math.abs(lower.fitHigh - lower.fitLow);
            if (Math.abs(pitch - PITCH) > PITCH_SLACK || Math.abs(pitch - rise) <= PITCH_SLACK) {
                continue;
            }
            int climbed = 1;
            for (Tile next = lower; (next = below(at, next, -dx, -dy, -up)) != null; ) {
                climbed++;
            }
            float eave = crease - climbed * pitch;
            int floor = (int) Math.floor(eave + LEAST_RISE);
            HashMap<Long, Side> strips = isAlongX ? alongX : alongY;
            int laid = 0;
            while (laid < climbed) {
                int x = upper.x - (laid + 1) * dx, y = upper.y - (laid + 1) * dy;
                if (roofed.containsKey(key(x, y, 0)) || !stands(built, site, x, y, floor) || site.taken(x, y, floor, crease - (laid + 1) * pitch)) {
                    break;
                }
                laid++;
            }
            boolean fromEave = laid == climbed && !stands(built, site, upper.x - (climbed + 1) * dx, upper.y - (climbed + 1) * dy, floor);
            for (int step = 1; step <= laid; step++) {
                float inner = crease - (step - 1) * pitch, outer = inner - pitch;
                strips.putIfAbsent(key(upper.x - step * dx, upper.y - step * dy, 0),
                        new Side(upper.x - step * dx, upper.y - step * dy, up > 0 ? outer : inner, up > 0 ? inner : outer, fromEave, false, eave, pitch, crease, up));
            }
        }
    }

    /** The square a far side starts on, and which way it runs. */
    private static long rowKey(int x, int y, int dx, int dy) {
        return key(x, y, (dx + 1) * 3 + dy + 1);
    }

    /**
     * The eave of the plane a slope tile lies on: the lowest edge of any whole slope tile of the
     * map that climbs the same way at the same pitch and stands, carried on to the tile, within
     * half a pitch of it. A plane beside it, a row along, stands a whole pitch off.
     */
    private static float eaveOfPlane(List<Tile> tiles, Tile top) {
        float rise = top.fitHigh - top.fitLow, eave = Math.min(top.fitLow, top.fitHigh);
        for (Tile tile : tiles) {
            if (!tile.slopes() || tile.fitAxis != top.fitAxis || Math.abs(tile.fitHigh - tile.fitLow - rise) > SAME) {
                continue;
            }
            int axis = tile.fitAxis == ALONG_X ? tile.x : tile.y, topAxis = top.fitAxis == ALONG_X ? top.x : top.y;
            if (Math.abs(tile.fitLow + rise * (topAxis - axis) - top.fitLow) <= PITCH / 2f) {
                eave = Math.min(eave, Math.min(tile.fitLow, tile.fitHigh));
            }
        }
        return eave;
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
                    || !stands(built, site, x, y, tile.z) || site.taken(x, y, tile.z, Math.min(tile.fitLow, tile.fitHigh))) {
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
        // Asked up to a thousand times a square where two slopes meet, nearly always of a square already known: no lambda is made for those.
        Long key = key(x, y, z);
        Boolean known = built.get(key);
        if (known == null) {
            known = site.stands(x, y, z);
            built.put(key, known);
        }
        return known;
    }

    /**
     * Makes the tile of a made roof over one square, unless the building has something else there
     * from the level of the roof's eave up. A square in over holds a crown the slope comes down onto.
     */
    private static void lay(List<Tile> made, int x, int y, float[] corners, float middle, int eaveLevel, Site site, Set<Long> over) {
        float lowest = Math.min(Math.min(corners[0], corners[1]), Math.min(corners[2], corners[3]));
        int z = (int) Math.floor(lowest + LEAST_RISE);
        if (!over.contains(key(x, y, 0)) && site.taken(x, y, eaveLevel, lowest)) {
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
                // The flat roof of a lower part of the building inside its outside walls is under the eave too (see
                // backs): left out, the slope came down to the outside wall beyond it and stood a third of a level over
                // the lower part's wall (3915..3918, 13817).
                int towardX = Integer.signum(alongX.x - x), towardY = Integer.signum(alongX.y - y);
                if (stands(built, site, x, y, level) && !(towardX != 0 && site.annex(x + towardX, y, x, y, level))
                        && !(towardY != 0 && site.annex(x, y + towardY, x, y, level))) {
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
     * Brings a made slope that climbs from the building's edge down into a notch in that edge,
     * into corners, and returns its height over the square's middle. Where the wall under the
     * eave steps in, the roof climbs from the step's wall as well, and the two meet in a valley.
     * Laid row by row, each row from its own wall, the rows each side of the step stood a third
     * of a level apart, a step in the roof with an upright face that showed as a black triangle
     * and corners turned up like a pagoda's (8673..8674, 8418..8419). Only a square the building
     * goes on from along the slope is a notch: one beyond a gable end is not, and the far wall
     * past the ridge is never looked at. Nor is a square whose wall to the building meets the
     * slope at its top: that wall stands under the ridge, and nothing climbs from it. Beyond a
     * farmhouse's south gable a wing went on from the ridge's square (2866..2867, 10404), and
     * the far slope was brought down to it as a hip. Nor is the wall of a square under another
     * ridge, the made far side of a lower wing past the gable end: the main roof's north side
     * was hipped down to the wing's north wall, and the main gable stood bare over the hip
     * (13616..13623, 3673..3678).
     */
    private static float notched(Side side, boolean isAlongX, HashMap<Long, Boolean> built, Site site, HashMap<Long, Side> strips, float[] corners, float middle) {
        if (!side.fromEave) {
            return middle;
        }
        int level = side.eaveLevel(), ax = isAlongX ? 1 : 0, ay = 1 - ax;
        int reach = Math.min(FURTHEST_EAVE, 1 + (int) Math.ceil((side.top - side.eave) / side.pitch));
        for (int x = side.x - reach; x <= side.x + reach; x++) {
            for (int y = side.y - reach; y <= side.y + reach; y++) {
                int along = isAlongX ? x - side.x : y - side.y;
                if (along * side.up > 0 || stands(built, site, x, y, level)) {
                    continue;
                }
                boolean underEave = false;
                for (int toward = -1; toward <= 1 && !underEave; toward += 2) {
                    Side wing = strips.get(key(x + toward * ax, y + toward * ay, 0));
                    if (stands(built, site, x + toward * ax, y + toward * ay, level) && (wing == null || Math.abs(wing.top - side.top) < SAME)) {
                        int wall = along + (toward > 0 ? 1 : 0);
                        // A level square is the slope's top carried across, and says nothing of where its ridge is.
                        underEave = Math.abs(side.far - side.near) < SAME || side.near + wall * (side.far - side.near) < side.top - SAME;
                    }
                }
                if (!underEave) {
                    continue;
                }
                for (int point = 0; point < 5; point++) {
                    float px = side.x + (point == 4 ? 0.5f : point == 1 || point == 2 ? 1f : 0f);
                    float py = side.y + (point == 4 ? 0.5f : point >= 2 ? 1f : 0f);
                    float away = Math.max(Math.max(0f, Math.max(x - px, px - (x + 1))), Math.max(0f, Math.max(y - py, py - (y + 1))));
                    float height = side.eave + side.pitch * away;
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
    private static void extend(LinkedHashMap<Long, Side> strips, boolean alongX, HashMap<Long, Tile> roofed, HashMap<Long, Boolean> built, Site site,
            HashMap<Long, Side> across, Set<Long> over) {
        ArrayDeque<Side> queue = new ArrayDeque<>(strips.values());
        while (!queue.isEmpty()) {
            Side from = queue.poll();
            for (int step = -1; step <= 1; step += 2) {
                int x = from.x + (alongX ? 0 : step), y = from.y + (alongX ? step : 0);
                while (!strips.containsKey(key(x, y, 0))) {
                    Tile on = roofed.get(key(x, y, 0));
                    if (on == null) {
                        // A far side falling from a ridge across the way is the roof there: the slope ends against it in a valley.
                        Side other = across.get(key(x, y, 0));
                        // Nor is it carried on over the flat roof of a lower part beside it (see backs): it stood over the north
                        // block's flat roof as a gutter against the main roof's eave (3916..3917, 13816).
                        if ((other == null || other.fromEave) && stands(built, site, x, y, from.eaveLevel()) && !site.taken(x, y, from.eaveLevel(), Math.min(from.near, from.far))
                                && !site.annex(x - (alongX ? 0 : step), y - (alongX ? step : 0), x, y, from.eaveLevel())) {
                            Side next = from.at(x, y);
                            strips.put(key(x, y, 0), next);
                            queue.add(next);
                        }
                        break;
                    }
                    // A crown square between two squares of one far side that comes down over the crown (see
                    // backs), where the row itself was cut short by the cross gable's valley.
                    if (on.sheet && !from.fromEave && strips.containsKey(key(x + (alongX ? 0 : step), y + (alongX ? step : 0), 0)) && Math.abs(from.near - from.far) > SAME && Math.min(from.near, from.far) >= on.fitLow - SAME && !across.containsKey(key(x, y, 0))) {
                        Side next = from.at(x, y);
                        strips.put(key(x, y, 0), next);
                        over.add(key(x, y, 0));
                        queue.add(next);
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
        float[] made = new float[4], own = new float[4], lower = new float[4];
        cornersOf(alongX ? ALONG_X : ALONG_Y, side.near, side.far, made);
        cornersOf(on.fitAxis, on.fitLow, on.fitHigh, own);
        boolean madeLower = false, ownLower = false;
        for (int corner = 0; corner < 4; corner++) {
            madeLower |= made[corner] < own[corner] - SAME;
            ownLower |= own[corner] < made[corner] - SAME;
            lower[corner] = Math.min(made[corner], own[corner]);
        }
        if (!madeLower || !ownLower) {
            return false;
        }
        int crease = creaseOf(lower, Math.min(side.near + side.far, on.fitLow + on.fitHigh) / 2f);
        if (crease == UNCREASED) {
            return false;
        }
        // Only a tile that is folded takes the new corners: one left as it was keeps its own.
        System.arraycopy(lower, 0, on.corners, 0, 4);
        on.crease = crease;
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

    /**
     * Whether the row that ends in this tile is the last before a hip at the end of a crown: across
     * its top lies no crown, but the crown's corner lies a square to the side at the tile's top. The
     * roof across it is the hip's face, which climbs from the end wall. Carried over that face, the
     * row's far side met it in a valley two rows deep and stood across it as a black triangle over
     * a gable of siding (8675, 8422..8424).
     */
    private static boolean endOfAHip(HashMap<Long, Tile> roofed, Tile top, int dx, int dy, float ridge) {
        if (roofed.containsKey(key(top.x + dx, top.y + dy, 0))) {
            return false;
        }
        for (int side = -1; side <= 1; side += 2) {
            Tile crown = roofed.get(key(top.x + dx + (dx == 0 ? side : 0), top.y + dy + (dy == 0 ? side : 0), 0));
            if (crown != null && crown.sheet && Math.abs(crown.fitLow - ridge) < SAME) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the walk down a row from its top stopped at a wing: past the low edge of its
     * lowest tile lies a tile the map folded where two slopes meet, the valley's, or a tile of
     * a slope across this one. A dormer's ridge trim at the row's foot, over the floor of its
     * level, is a wing too: the gutter along an eave is a level down. Read as the eave, it set
     * the far side made from the row a third of a level up its wall, a box on the north side of
     * a house whose south slope has two dormers (3963 and 3968, 9853..9854).
     */
    private static boolean cutByAWing(HashMap<Long, Tile> at, Tile lowest, int dx, int dy) {
        float foot = Math.min(lowest.fitLow, lowest.fitHigh);
        Tile trim = at.get(key(lowest.x - dx, lowest.y - dy, lowest.z));
        if (trim != null && trim.fitting && foot > lowest.z + Roofs.FLAT && Math.abs(Math.max(trim.fitLow, trim.fitHigh) - foot) < SAME) {
            return true;
        }
        for (int dz = 0; dz >= -1; dz--) {
            Tile next = at.get(key(lowest.x - dx, lowest.y - dy, lowest.z + dz));
            if (next != null && (next.shaped || next.slopes() && next.fitAxis != lowest.fitAxis)) {
                return true;
            }
        }
        return false;
    }

    /** The tile of the same slope at the tile's low edge, or null: one that meets it, and no porch roof under its eave. */
    private static Tile below(HashMap<Long, Tile> at, Tile tile, int dx, int dy, int up) {
        for (int dz = 0; dz >= -1; dz--) {
            Tile next = at.get(key(tile.x - dx, tile.y - dy, tile.z + dz));
            if (next != null && next.slopes() && next.fitAxis == tile.fitAxis && next.up() == up
                    && Math.abs(Math.max(next.fitLow, next.fitHigh) - Math.min(tile.fitLow, tile.fitHigh)) <= JOIN) {
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
                    boolean peaked = tile.peak > 0f && tile.peakAxis == axis;
                    // A ridge along the middle of the square is one whatever lies beside it: where a
                    // wing's ridge runs into the main roof's, a slope on one side is under the other.
                    if (peaked || before != null && after != null) {
                        float high = (peaked ? level : Math.max(level, Math.max(before.fitHigh, after.fitLow))) + (peaked ? tile.peak : 0f);
                        // The roof is level under this one from side to side, unless it peaks there.
                        float fall = peaked ? 2f * tile.peak : 0f;
                        float from = peaked ? -tile.peakBefore : 0f, to = 1f + (peaked ? tile.peakAfter : 0f);
                        caps.add(alongX ? new Cap(tile.x + 0.5f, tile.y + from, high, tile.x + 0.5f, tile.y + to, high, fall)
                                : new Cap(tile.x + from, tile.y + 0.5f, high, tile.x + to, tile.y + 0.5f, high, fall));
                    }
                }
            }
        }
        return caps;
    }

    /**
     * The ridges that run along the middle of a row of squares. A roof an odd number of squares
     * across has its ridge over the middle of one, and the map tops each slope there with a
     * piece half a tile wide. Only the east and south halves are on the map, as with whole
     * slopes. And the game's view cannot tell a thing from the same thing a square nearer the
     * map's origin each way and a third of a level lower, so where the piece would have to be
     * more than a level over its square's floor the map holds it a level up and a square
     * further off each way, which in that view is the same place. Stood where the map has
     * it, such a piece is a strip of roof in the air over the ridge.
     *
     * So a half tile is put where it joins the slope below it: on its own square, or one or
     * two squares towards the origin and as many thirds of a level down. The square it comes
     * to is the ridge's: it is level from edge to edge at the height the two slopes reach,
     * as backs made it or as made here, and peaks along its middle half a tile's climb higher.
     */
    static void peaks(List<Tile> tiles, List<Tile> made) {
        HashMap<Long, Tile> slopes = new HashMap<>(), ridges = new HashMap<>(), halves = new HashMap<>();
        for (Tile tile : tiles) {
            if (tile.slopes()) {
                slopes.put(key(tile.x, tile.y, 0), tile);
            }
            if (tile.half != 0) {
                halves.put(key(tile.x, tile.y, tile.z), tile);
            }
        }
        for (Tile tile : made) {
            ridges.put(key(tile.x, tile.y, 0), tile);
        }
        HashMap<Tile, Integer> shifts = new HashMap<>();
        for (Tile half : tiles) {
            if (half.half == 0) {
                continue;
            }
            boolean east = half.half == EAST_HALF;
            for (int shift = 0; shift <= FURTHEST_SHIFT; shift++) {
                int x = half.x - shift, y = half.y - shift;
                float edge = half.halfEdge - shift * PITCH;
                Tile below = slopes.get(key(x + (east ? 1 : 0), y + (east ? 0 : 1), 0));
                if (below == null || below.fitAxis != (east ? ALONG_X : ALONG_Y) || below.up() != -1 || Math.abs(below.fitLow - edge) > SAME + PITCH_SLACK) {
                    continue;
                }
                shifts.put(half, shift);
                peak(x, y, below.fitLow, east, slopes, ridges, made);
                break;
            }
        }
        // A half whose square has the end of another slope below it, where a wing's ridge runs
        // into the main roof's, or the foot of a valley, has its place from the run of halves
        // it is one of: the same ridge, at the same height.
        for (Tile half : tiles) {
            if (half.half == 0 || shifts.containsKey(half)) {
                continue;
            }
            boolean east = half.half == EAST_HALF;
            for (int way = -1; way <= 1 && !shifts.containsKey(half); way += 2) {
                for (int along = 1; ; along++) {
                    Tile next = halves.get(key(half.x + (east ? 0 : way * along), half.y + (east ? way * along : 0), half.z));
                    if (next == null || next.half != half.half || Math.abs(next.halfEdge - half.halfEdge) > SAME) {
                        break;
                    }
                    Integer shift = shifts.get(next);
                    if (shift != null) {
                        shifts.put(half, shift);
                        peak(half.x - shift, half.y - shift, half.halfEdge - shift * PITCH, east, slopes, ridges, made);
                        break;
                    }
                }
            }
        }
        // Where a wing's ridge comes to the square of the main roof's, it runs on to the middle
        // of that square, where the two meet: stopped at the square's edge it ends half a tile
        // short, an open gable end standing on the main roof's slope.
        for (Tile ridge : ridges.values()) {
            if (ridge.peak <= 0f) {
                continue;
            }
            boolean alongY = ridge.peakAxis == ALONG_X;
            ridge.peakBefore = meets(ridges, ridge, ridge.x - (alongY ? 0 : 1), ridge.y - (alongY ? 1 : 0)) ? 0.5f : 0f;
            ridge.peakAfter = meets(ridges, ridge, ridge.x + (alongY ? 0 : 1), ridge.y + (alongY ? 1 : 0)) ? 0.5f : 0f;
        }
    }

    /** Whether the square holds a ridge across the given one's, as high. */
    private static boolean meets(HashMap<Long, Tile> ridges, Tile ridge, int x, int y) {
        Tile across = ridges.get(key(x, y, 0));
        return across != null && across.peak > 0f && across.peakAxis != ridge.peakAxis && Math.abs(across.fitLow + across.peak - ridge.fitLow - ridge.peak) <= SAME;
    }

    /**
     * Makes the square under a ridge along its middle level at the height the slopes reach and
     * peaked, or peaks the level tile backs made there. A square that holds a slope of the map
     * is left as it is, unless the slope lies wholly under the ridge: the main roof going on
     * under a wing's ridge.
     */
    private static void peak(int x, int y, float level, boolean east, HashMap<Long, Tile> slopes, HashMap<Long, Tile> ridges, List<Tile> made) {
        Tile ridge = ridges.get(key(x, y, 0));
        Tile under = slopes.get(key(x, y, 0));
        if (ridge == null && (under == null || Math.max(under.fitLow, under.fitHigh) < level - SAME)) {
            ridge = new Tile(x, y, (int) Math.floor(level + LEAST_RISE), LEVEL, true, level, level);
            ridge.fitted = true;
            cornersOf(LEVEL, level, level, ridge.corners);
            made.add(ridge);
            ridges.put(key(x, y, 0), ridge);
        }
        if (ridge != null && !ridge.shaped && ridge.fitAxis == LEVEL && Math.abs(ridge.fitLow - level) <= SAME + PITCH_SLACK) {
            ridge.peak = PITCH / 2f;
            ridge.peakAxis = east ? ALONG_X : ALONG_Y;
        }
    }

    /**
     * The valleys where one roof runs into the side of another: a wing whose ridge meets the
     * slope of the main roof, a dormer. The map gives each square one slope, the wing's as far
     * as its last square and the main roof's from there, so the two meet in a stair of whole
     * tiles with a step open at every one. They are two planes, and where two planes meet is
     * a straight line.
     *
     * So each slope is carried on along its level lines over the squares of a slope that runs
     * across it and climbs the way it is being carried, for as long as it stands above that
     * slope anywhere on the square and that slope goes on to climb as high as it. Under the
     * other slope it is out of sight inside the roof; above it, it is the roof of the wing,
     * and the line where it goes under is the valley. A ridge that runs along the middle of
     * its squares is carried on the same way. A slope that falls the way it would be carried
     * is a hip, which backs folds, and nothing is carried there.
     *
     * tiles are the fitted tiles of the map and made those of backs and peaks. Returns the
     * carried tiles, each marked so.
     */
    static List<Tile> valleys(List<Tile> tiles, List<Tile> made) {
        HashMap<Long, ArrayList<Tile>> at = new HashMap<>();
        ArrayList<Tile> all = new ArrayList<>(tiles);
        all.addAll(made);
        for (Tile tile : all) {
            if (!tile.fitting && tile.half == 0) {
                at.computeIfAbsent(key(tile.x, tile.y, 0), unused -> new ArrayList<>()).add(tile);
            }
        }
        ArrayList<Tile> carried = new ArrayList<>();
        float[] own = new float[4], other = new float[4];
        for (Tile tile : all) {
            boolean peaked = tile.peak > 0f && !tile.shaped && tile.fitAxis == LEVEL;
            if (!peaked && !tile.slopes() || tile.fitting || tile.half != 0) {
                continue;
            }
            // The axis the heights of the tile change along, and so the one its level lines run across.
            int axis = peaked ? tile.peakAxis : tile.fitAxis;
            cornersOf(peaked ? LEVEL : tile.fitAxis, tile.fitLow, peaked ? tile.fitLow : tile.fitHigh, own);
            float lowest = peaked ? tile.fitLow : Math.min(tile.fitLow, tile.fitHigh), highest = peaked ? tile.fitLow + tile.peak : Math.max(tile.fitLow, tile.fitHigh);
            for (int step = -1; step <= 1; step += 2) {
                int dx = axis == ALONG_X ? 0 : step, dy = axis == ALONG_X ? step : 0;
                for (int x = tile.x + dx, y = tile.y + dy; ; x += dx, y += dy) {
                    ArrayList<Tile> here = at.get(key(x, y, 0));
                    Tile across = null, flat = null;
                    boolean ends = here == null;
                    for (int i = 0; !ends && i < here.size(); i++) {
                        Tile on = here.get(i);
                        // The next tile of the slope itself, or a tile the map has folded there, has the square already.
                        ends = on.shaped || sameRoof(tile, peaked, axis, on);
                        if (on.slopes() && on.fitAxis != axis && (across == null || across.up() != step)) {
                            across = on;
                        } else if (on.axis == LEVEL && on.peak == 0f && !on.fitted) {
                            flat = on;
                        }
                    }
                    if (!ends && across == null && flat != null && !peaked) {
                        foldValley(flat, own, tile, at.get(key(x + dx, y + dy, 0)), axis, step);
                    }
                    if (ends || across == null) {
                        break;
                    }
                    cornersOf(across.fitAxis, across.fitLow, across.fitHigh, other);
                    boolean above = false, under = true;
                    for (int corner = 0; corner < 4; corner++) {
                        above |= (peaked ? highest : own[corner]) > other[corner] + SAME;
                        under &= (peaked ? highest : own[corner]) >= other[corner] - SAME;
                    }
                    // A slope across that falls the way this one is carried is a hip, unless it lies under
                    // this one all over its square: then it is inside the roof, a stray slope the map has
                    // in a valley (1472, 9851..9852) or the far side backs made for it, and the valley
                    // goes on past it to the slope beyond. Stopped there, it left a pit open at 1471, 9852.
                    boolean falls = across.up() != step;
                    if (!above || falls && !under || climbsTo(at, falls ? x + dx : x, falls ? y + dy : y, dx, dy, across.fitAxis, step) < lowest - SAME) {
                        break;
                    }
                    if (!falls && !under && !peaked && strayHead(at, across, x, y, dx, dy, own, other)) {
                        // The map's roofs_03_4 at 1472, 9851: laid as read, its head stood out of the valley
                        // as a painted triangle. It is folded onto this slope, all of it the mod's own sheet.
                        System.arraycopy(own, 0, across.corners, 0, 4);
                        across.crease = NW_SE;
                        across.shaped = true;
                        across.own = 0;
                        continue;
                    }
                    Tile over = new Tile(x, y, (int) Math.floor(lowest + LEAST_RISE), tile.fitAxis, true, tile.fitLow, tile.fitHigh);
                    over.fitted = true;
                    over.carried = true;
                    over.peak = peaked ? tile.peak : 0f;
                    over.peakAxis = tile.peakAxis;
                    System.arraycopy(own, 0, over.corners, 0, 4);
                    carried.add(over);
                }
            }
        }
        return carried;
    }

    /**
     * Whether a slope across, climbing the way a slope is carried over it, is a stray in the valley:
     * its foot lies under the carried slope, and nothing on the square beyond its head meets it there.
     * A slope of the roof goes on unbroken from its head, up the slope it climbs on to or onto a level
     * top; a stray the map puts in a valley stands clear of the roof there.
     */
    private static boolean strayHead(HashMap<Long, ArrayList<Tile>> at, Tile across, int x, int y, int dx, int dy, float[] carried, float[] own) {
        float foot = Math.min(across.fitLow, across.fitHigh), head = Math.max(across.fitLow, across.fitHigh);
        for (int corner = 0; corner < 4; corner++) {
            if (own[corner] < foot + SAME && carried[corner] < own[corner] - SAME) {
                return false;
            }
        }
        // The corners of the next square's edge that the head lies along.
        int first = dx < 0 ? 1 : dx > 0 ? 0 : dy < 0 ? 2 : 0, second = dx < 0 ? 2 : dx > 0 ? 3 : dy < 0 ? 3 : 1;
        float[] edge = new float[4];
        ArrayList<Tile> next = at.get(key(x + dx, y + dy, 0));
        for (int i = 0; next != null && i < next.size(); i++) {
            Tile on = next.get(i);
            if (on.shaped) {
                System.arraycopy(on.corners, 0, edge, 0, 4);
            } else {
                cornersOf(on.fitAxis, on.fitLow, on.fitHigh, edge);
            }
            if (Math.abs(Math.max(edge[first], edge[second]) - head) <= JOIN) {
                return false;
            }
        }
        return true;
    }

    /** The highest the slope that runs across the square climbs to, going on from it the way given over the squares it covers. */
    private static float climbsTo(HashMap<Long, ArrayList<Tile>> at, int x, int y, int dx, int dy, int axis, int up) {
        float highest = Float.NEGATIVE_INFINITY;
        for (boolean on = true; on; x += dx, y += dy) {
            on = false;
            ArrayList<Tile> here = at.get(key(x, y, 0));
            for (int i = 0; here != null && i < here.size(); i++) {
                Tile tile = here.get(i);
                if (tile.slopes() && tile.fitAxis == axis && tile.up() == up) {
                    highest = Math.max(highest, Math.max(tile.fitLow, tile.fitHigh));
                    on = true;
                }
            }
        }
        return highest;
    }

    /** A tile the map has for the foot of a valley reads as level this near the height of the valley's higher corners, in levels. */
    private static final float VALLEY_READ = 0.1f;

    /**
     * The tile at the foot of a valley on the side the game's view does not see. The map has
     * one there, and its sprite is the valley seen from behind: it reads as a level tile at
     * the height of the valley's three higher corners, and laid level it is a block standing
     * in the valley with a step down at two of its sides. It is the two slopes it lies
     * between: the one being carried onto its square, whose heights there are carried, and
     * the one that comes down to it from the square beyond, one of which beyond holds. Where
     * both are there and the tile reads as high as they say, it is folded along the line they
     * meet on.
     */
    private static void foldValley(Tile flat, float[] carried, Tile from, ArrayList<Tile> beyond, int axis, int step) {
        if (beyond == null) {
            return;
        }
        float[] other = new float[4], higher = new float[4];
        for (Tile down : beyond) {
            if (!down.slopes() || down.fitAxis == axis || down.up() != step) {
                continue;
            }
            float low = Math.min(down.fitLow, down.fitHigh), rise = Math.abs(down.fitHigh - down.fitLow);
            cornersOf(down.fitAxis, step < 0 ? low : low - rise, step < 0 ? low - rise : low, other);
            float highest = 0f;
            for (int corner = 0; corner < 4; corner++) {
                higher[corner] = Math.max(carried[corner], other[corner]);
                highest = Math.max(highest, higher[corner]);
            }
            int crease = creaseOf(higher, Math.max((from.fitLow + from.fitHigh) / 2f, low - rise / 2f));
            if (crease == UNCREASED || Math.abs(flat.fitLow - highest) > VALLEY_READ) {
                continue;
            }
            System.arraycopy(higher, 0, flat.corners, 0, 4);
            flat.crease = crease;
            flat.shaped = true;
            flat.own = 0;
            return;
        }
    }

    /** Whether a tile on the next square along a level line of another is the same slope, or the same ridge, going on. */
    private static boolean sameRoof(Tile tile, boolean peaked, int axis, Tile on) {
        if (Math.abs(on.fitLow - tile.fitLow) > JOIN) {
            return false;
        }
        if (peaked) {
            return on.peak > 0f && on.peakAxis == axis;
        }
        return on.slopes() && on.fitAxis == axis && on.up() == tile.up();
    }

    /** The caps along the ridges that valleys carried on, each as far as its ridge goes. */
    static List<Cap> capsOf(List<Tile> carried) {
        ArrayList<Cap> caps = new ArrayList<>();
        for (Tile tile : carried) {
            if (tile.peak > 0f) {
                float high = tile.fitLow + tile.peak;
                caps.add(tile.peakAxis == ALONG_X ? new Cap(tile.x + 0.5f, tile.y, high, tile.x + 0.5f, tile.y + 1f, high, 2f * tile.peak)
                        : new Cap(tile.x, tile.y + 0.5f, high, tile.x + 1f, tile.y + 0.5f, high, 2f * tile.peak));
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
     * where two of them meet: on the line of a valley, where the map's valley tiles are the
     * squares before and after it along the diagonal, it is folded as they are, the higher of
     * the two slopes at each corner, and on the line of a hip the lower. Elsewhere it is left
     * as read.
     */
    private static void adopt(Tile tile, HashMap<Long, Tile> at) {
        Tile alongX = null, alongY = null;
        for (int side = 0; side < 4; side++) {
            int dx = side == 0 ? -1 : side == 1 ? 1 : 0, dy = side == 2 ? -1 : side == 3 ? 1 : 0;
            Tile beside = at.get(key(tile.x + dx, tile.y + dy, tile.z));
            // In the same row of a slope: beside it across the way the slope runs.
            if (beside == null || !beside.fitted || !beside.slopes() || (beside.fitAxis == ALONG_X) != (dx == 0)) {
                continue;
            }
            if (beside.fitAxis == ALONG_X) {
                alongX = beside;
            } else {
                alongY = beside;
            }
        }
        if (alongX != null && alongY != null) {
            between(tile, alongX, alongY, at);
            return;
        }
        Tile from = alongX != null ? alongX : alongY;
        if (from != null) {
            tile.fitAxis = from.fitAxis;
            tile.fitLow = from.fitLow;
            tile.fitHigh = from.fitHigh;
            tile.fitted = true;
        }
    }

    /** Folds a tile between a slope along x and one along y in the same rows as it, where the map's tiles either side along the diagonal say which fold it is. */
    private static void between(Tile tile, Tile alongX, Tile alongY, HashMap<Long, Tile> at) {
        int kind = -1;
        for (int step = -1; step <= 1; step += 2) {
            Tile diagonal = at.get(key(tile.x + step, tile.y + step, tile.z));
            if (diagonal != null && (diagonal.axis == SOUTH_EAST_ODD || diagonal.axis == NORTH_WEST_ODD)) {
                if (kind >= 0 && kind != diagonal.axis) {
                    return;
                }
                kind = diagonal.axis;
            }
        }
        if (kind < 0) {
            return;
        }
        float[] x = new float[4], y = new float[4];
        cornersOf(ALONG_X, alongX.fitLow, alongX.fitHigh, x);
        cornersOf(ALONG_Y, alongY.fitLow, alongY.fitHigh, y);
        for (int corner = 0; corner < 4; corner++) {
            tile.corners[corner] = kind == SOUTH_EAST_ODD ? Math.max(x[corner], y[corner]) : Math.min(x[corner], y[corner]);
        }
        tile.crease = NW_SE;
        tile.shaped = true;
        tile.own = 3;
        tile.fitted = true;
    }
}

package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * Which rooms the eye can see into, worked out from the map's rooms and the openings between
 * them, so that what stands in a room is drawn only while some line of sight reaches into it.
 *
 * A room is seen through its openings: a doorway, an open door, a window, a door with glass in
 * it, the well of a stair. Each opening is a rectangle on a square's edge, or across a square
 * between two levels, and from the eye it covers a rectangle of the picture. The eye's own room
 * (or the open air, out of doors) is seen through the whole picture; a room behind one of its
 * openings through the part of the picture that opening covers; a room behind an opening of
 * that room through what the two have in common; and so on. The open air is one region: a
 * window of a house across the street is reached from the eye's room through any window of the
 * eye's room that covers it, and from the street directly.
 *
 * In the open air a line can also be stopped by a building it does not go into: a house between
 * the eye and that window. A line that goes into a building goes in through an opening, which
 * the walk follows, so a line straight through the open air to a window passes through no
 * room, and the rooms of every building but the eye's own stand solid in its way (Blocks,
 * Horizon). A line that has been through a building's rooms and out into the air again is held
 * to no block, so what is seen through a house, in at one window and out at another, is found.
 * The blocks are the rooms from the ground up, so what overhangs the street stops nothing, and
 * a room is sometimes counted in sight when it is not; one in sight is never counted out.
 *
 * A wall, a shut door and a drawn curtain end a line. A rectangle of the picture is a little
 * wider than the opening covers, by MARGIN, and an eye closer to an opening's plane than
 * THROUGH looks through the whole of it, so that a head leaning through a doorway, or a picture
 * drawn a pixel off, never has a room it sees counted out.
 */
final class RoomSight {
    /** The region of the open air: every square that is in no room. */
    static final long OUTSIDE = -1L;
    /** The open air as the walk reaches it after a line has been through some other building's rooms: no block stops such a line. */
    private static final long THROUGH_A_BUILDING = -2L;
    /** What is built on a room's square and is no room's contents, nor seen from a place known: drawn whenever its chunk is. */
    static final long ANYWHERE = -3L;
    /** Paired with a room (blind), what is seen in place of the room while the eye does not see into it. */
    static final long BLIND = -4L;
    /** The two regions of each key pair gives, the lesser first. */
    private static final java.util.concurrent.ConcurrentHashMap<Long, long[]> PAIRS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The key of what is seen from either of two regions: a wall's caps and the reveals and
     * frame of an opening in it, seen from the rooms or the open air either side. A region
     * itself where the two are one. Under ANYWHERE, so no room and not OUTSIDE.
     */
    static long pair(long a, long b) {
        if (a == b) {
            return a;
        }
        long low = Math.min(a, b), high = Math.max(a, b);
        long mixed = (low * 0x9E3779B97F4A7C15L ^ high) * 0xC2B2AE3D27D4EB4FL;
        long key = ANYWHERE - 1L - (mixed & 0x3FFFFFFFFFFFFFFFL);
        long[] was = PAIRS.computeIfAbsent(key, k -> new long[] {low, high});
        if (was[0] != low || was[1] != high) {
            throw new IllegalStateException("regions " + low + " and " + high + " share a pair key with " + was[0] + " and " + was[1]);
        }
        return key;
    }

    /** The key of what the open air sees through a window of a room in place of the room, while the eye does not see into it. */
    static long blind(long room) {
        return pair(BLIND, room);
    }

    /** The two regions of a key pair gave, or null for a region's own key. */
    static long[] pairOf(long key) {
        return key < ANYWHERE ? PAIRS.get(key) : null;
    }

    /** What an edge between two squares does to a line of sight: stops it and always will, stops it until it is opened, lets it through. */
    static final int SOLID = 0, SHUT = 1, SEE = 2;
    /** How far past an opening's own rectangle in the picture it is counted as covering, in the picture's half widths. */
    static final float MARGIN = 0.02f;
    /** An eye this near an opening's plane, in tiles, over the opening or near it, sees through the whole of the opening. */
    static final float THROUGH = 0.35f;
    /** How near the eye a corner of an opening is kept, in tiles along the view: nearer, it is cut off where it crosses. */
    private static final float NEAR = 0.01f;
    /** How far below a level's floor and above its ceiling an opening is taken to reach, in tiles. */
    static final float SLACK = 0.05f;
    /** How far an eye that moves about its place may go up or down, in tiles: a lean or a crouch, where it goes much further along the ground. */
    private static final float RISE = 0.5f;
    /** How many storeys over the eye's the open air sees into a room through. */
    static final int ABOVE_FROM_OUTSIDE = 1;

    /** The map as the walk reads it. */
    interface Ground {
        /** The room a square is in, or OUTSIDE for one in none or with no square. */
        long region(int x, int y, int z);

        /** What the north edge of a square does to sight (towards y - 1), or with north false its west edge (towards x - 1). */
        int edge(int x, int y, int z, boolean north);

        /** Whether a square has a floor or a roof that sight between it and the level below does not pass. */
        boolean floored(int x, int y, int z);

        /** How high a point's floor is on a level, in tiles above level 0. */
        float floorUp(float x, float y, int z);

        /** The building whose room a square is in, or OUTSIDE for a square in no room. */
        long building(int x, int y, int z);
    }

    /** The kinds of opening: on a square's north edge, on its west edge, and across the square up to the level above. */
    static final int NORTH = 0, WEST = 1, UP = 2;

    /**
     * Openings between two regions, each kept with the square it is on: a square's north and
     * west edges and the gap to the square above it. One region is on each side: for NORTH the
     * square's and the one at y - 1, for WEST the one at x - 1, for UP the one above.
     */
    static final class Openings {
        int count;
        int[] x = new int[256], y = new int[256], z = new int[256], kind = new int[256];
        /** The region on each side, and the building each side's room is in, OUTSIDE for the open air. */
        long[] here = new long[256], there = new long[256], hereBuilding = new long[256], thereBuilding = new long[256];
        /**
         * Four heights an opening's corners stand at, kept as it is found so that a walk asks the
         * map nothing but whether it is open: on an edge, the floor at its first end and at its
         * other end, then the ceiling at each; across a square, the floor above at its corners
         * from (x, y) round to (x, y + 1).
         */
        float[] heights = new float[256 * 4];
        /** Raised each time the openings are cleared: a walk from the same eye over the same filling reuses what the last worked out. */
        int filling;

        void clear() {
            count = 0;
            filling++;
        }

        void add(int atX, int atY, int atZ, int ofKind, long hereRegion, long thereRegion, long hereIn, long thereIn, float h0, float h1, float h2, float h3) {
            if (count == x.length) {
                int grown = count * 2;
                x = Arrays.copyOf(x, grown);
                y = Arrays.copyOf(y, grown);
                z = Arrays.copyOf(z, grown);
                kind = Arrays.copyOf(kind, grown);
                here = Arrays.copyOf(here, grown);
                there = Arrays.copyOf(there, grown);
                hereBuilding = Arrays.copyOf(hereBuilding, grown);
                thereBuilding = Arrays.copyOf(thereBuilding, grown);
                heights = Arrays.copyOf(heights, grown * 4);
            }
            heights[count * 4] = h0;
            heights[count * 4 + 1] = h1;
            heights[count * 4 + 2] = h2;
            heights[count * 4 + 3] = h3;
            x[count] = atX;
            y[count] = atY;
            z[count] = atZ;
            kind[count] = ofKind;
            here[count] = hereRegion;
            there[count] = thereRegion;
            hereBuilding[count] = hereIn;
            thereBuilding[count] = thereIn;
            count++;
        }

        void addAll(Openings other) {
            addAll(other, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }

        /** Adds the openings of another on the levels from fromZ to toZ, both included. */
        void addAll(Openings other, int fromZ, int toZ) {
            for (int i = 0; i < other.count; i++) {
                if (other.z[i] < fromZ || other.z[i] > toZ) {
                    continue;
                }
                add(other.x[i], other.y[i], other.z[i], other.kind[i], other.here[i], other.there[i], other.hereBuilding[i], other.thereBuilding[i],
                        other.heights[i * 4], other.heights[i * 4 + 1], other.heights[i * 4 + 2], other.heights[i * 4 + 3]);
            }
        }

        /**
         * Adds the openings a square has to the regions beside it and above it: every edge
         * between two regions that is not a wall that always stops sight, and the gap to the
         * level above where the regions differ and there is no floor. Openings that are shut now
         * are kept too: whether they are open is asked again by each walk.
         */
        void scan(Ground ground, int atX, int atY, int atZ) {
            long region = ground.region(atX, atY, atZ);
            long north = ground.region(atX, atY - 1, atZ), west = ground.region(atX - 1, atY, atZ), above = ground.region(atX, atY, atZ + 1);
            if (north != region && ground.edge(atX, atY, atZ, true) != SOLID) {
                addEdge(ground, atX, atY, atZ, NORTH, region, north, ground.building(atX, atY - 1, atZ));
            }
            if (west != region && ground.edge(atX, atY, atZ, false) != SOLID) {
                addEdge(ground, atX, atY, atZ, WEST, region, west, ground.building(atX - 1, atY, atZ));
            }
            if (above != region && !ground.floored(atX, atY, atZ + 1)) {
                add(atX, atY, atZ, UP, region, above, ground.building(atX, atY, atZ), ground.building(atX, atY, atZ + 1), ground.floorUp(atX, atY, atZ + 1),
                        ground.floorUp(atX + 1, atY, atZ + 1), ground.floorUp(atX + 1, atY + 1, atZ + 1), ground.floorUp(atX, atY + 1, atZ + 1));
            }
        }

        /** Adds an edge with its floor and its ceiling at each end: the ceiling no lower than a level over the floor, however the level above lies. */
        private void addEdge(Ground ground, int atX, int atY, int atZ, int ofKind, long region, long beyond, long beyondIn) {
            int toX = ofKind == NORTH ? atX + 1 : atX, toY = ofKind == NORTH ? atY : atY + 1;
            float lowA = ground.floorUp(atX, atY, atZ), lowB = ground.floorUp(toX, toY, atZ);
            add(atX, atY, atZ, ofKind, region, beyond, ground.building(atX, atY, atZ), beyondIn, lowA, lowB,
                    Math.max(lowA + ViewCamera.LEVEL_HEIGHT, ground.floorUp(atX, atY, atZ + 1)), Math.max(lowB + ViewCamera.LEVEL_HEIGHT, ground.floorUp(toX, toY, atZ + 1)));
        }
    }

    /**
     * What stands solid in the open air: the rooms of each building from the ground up, as
     * boxes over runs of squares along x whose rooms reach from level 0 to the same height in
     * the same building.
     */
    static final class Blocks {
        int count;
        /** For each block: least x, least y, greatest x, greatest y, and the height of its top. */
        float[] box = new float[5 * 64];
        long[] building = new long[64];
        int filling;

        void clear() {
            count = 0;
            filling++;
        }

        void add(float minX, float minY, float maxX, float maxY, float top, long in) {
            if (count == building.length) {
                building = Arrays.copyOf(building, count * 2);
                box = Arrays.copyOf(box, count * 10);
            }
            box[count * 5] = minX;
            box[count * 5 + 1] = minY;
            box[count * 5 + 2] = maxX;
            box[count * 5 + 3] = maxY;
            box[count * 5 + 4] = top;
            building[count] = in;
            count++;
        }

        void addAll(Blocks other) {
            for (int i = 0; i < other.count; i++) {
                add(other.box[i * 5], other.box[i * 5 + 1], other.box[i * 5 + 2], other.box[i * 5 + 3], other.box[i * 5 + 4], other.building[i]);
            }
        }

        /** Adds the blocks of a row of squares, from fromX up to but not including toX. */
        void scanRow(Ground ground, int fromX, int toX, int y) {
            int runFrom = fromX, runLevels = 0;
            long runIn = OUTSIDE;
            for (int x = fromX; x <= toX; x++) {
                int levels = 0;
                long in = OUTSIDE;
                if (x < toX) {
                    in = ground.building(x, y, 0);
                    while (in != OUTSIDE && ground.building(x, y, levels) == in) {
                        levels++;
                    }
                }
                if (levels != runLevels || in != runIn) {
                    if (runLevels > 0) {
                        // The top where it is lowest along the run: a line over it is held to no more than it clears.
                        float top = Math.min(Math.min(ground.floorUp(runFrom, y, runLevels), ground.floorUp(runFrom, y + 1, runLevels)),
                                Math.min(ground.floorUp(x, y, runLevels), ground.floorUp(x, y + 1, runLevels)));
                        add(runFrom, y, x, y + 1, top, runIn);
                    }
                    runFrom = x;
                    runLevels = levels;
                    runIn = in;
                }
            }
        }
    }

    /**
     * What the blocks hide from an eye, all the way round it. For each of BINS ways along the
     * ground, every block that fills the whole of that way is kept with how far its furthest
     * corner is and how steeply a line has to rise to clear it: the rise over the run from the
     * eye to the top of the block, taken where the block is furthest when its top is above the
     * eye and nearest when below, which is the least it can be anywhere in the way. Sorted by
     * distance, each with the steepest of those at that distance or nearer.
     *
     * It is worked out for any eye within SLACK_TILES along the ground and SLACK_UP up or down of
     * the place it is built for: each block is taken as no wider, no taller and no nearer than it
     * is from the furthest of those eyes, and hides asks of an opening as wide and as tall as it
     * is from the nearest. So a head that moves a little is held to the same horizon, which is
     * only built again once it has moved further than that. Up and down the slack is small: a
     * window under the eaves of a house behind a house of one floor is hidden by a hand's breadth.
     */
    static final class Horizon {
        static final int BINS = 512;
        static final float SLACK_TILES = 0.25f, SLACK_UP = 0.05f;
        private static final double PER_BIN = 2 * Math.PI / BINS;
        private final int[] from = new int[BINS + 1], fill = new int[BINS];
        private float[] reach = new float[1024], rise = new float[1024];
        /**
         * Each way's entries as one number to sort by, the distance's bits over the rise's: a
         * distance is never below 0, and such floats sort as their bits do. A way past a house a
         * step off holds a few hundred entries.
         */
        private long[] sorting = new long[1024];
        /** For each block in build, the ways it fills, first and last, and its reach and rise in them: the same in both passes. */
        private long[] wayFirst = new long[64], wayLast = new long[64];
        private float[] wayReach = new float[64], wayRise = new float[64];
        private float eyeX, eyeY, eyeUp;
        /** What it was built from, and whether it is built. */
        private Blocks builtFrom;
        private int builtFilling;
        private long builtBesides;
        private boolean built;
        /** Dev only: how many ways along the ground the blocks fill between them, and how many times it was built. */
        int entries, builds;

        /** Whether what was built holds for an eye at a place, among the same blocks and with the same building left out. */
        boolean holds(float atX, float atY, float atUp, Blocks blocks, long besides) {
            float dx = atX - eyeX, dy = atY - eyeY, du = atUp - eyeUp;
            return built && blocks == builtFrom && blocks.filling == builtFilling && besides == builtBesides && dx * dx + dy * dy <= SLACK_TILES * SLACK_TILES
                    && Math.abs(du) <= SLACK_UP;
        }

        void build(float atX, float atY, float atUp, Blocks blocks, long besides) {
            eyeX = atX;
            eyeY = atY;
            eyeUp = atUp;
            builtFrom = blocks;
            builtFilling = blocks.filling;
            builtBesides = besides;
            built = true;
            builds++;
            if (wayFirst.length < blocks.count) {
                wayFirst = new long[Integer.highestOneBit(blocks.count) * 2];
                wayLast = new long[wayFirst.length];
                wayReach = new float[wayFirst.length];
                wayRise = new float[wayFirst.length];
            }
            for (int i = 0; i < blocks.count; i++) {
                wayFirst[i] = 1L;
                wayLast[i] = 0L;
                if (blocks.building[i] == besides) {
                    continue;
                }
                float[] b = blocks.box;
                int at = i * 5;
                float minX = b[at] - eyeX, minY = b[at + 1] - eyeY, maxX = b[at + 2] - eyeX, maxY = b[at + 3] - eyeY;
                if (minX <= 2f * SLACK_TILES && maxX >= -2f * SLACK_TILES && minY <= 2f * SLACK_TILES && maxY >= -2f * SLACK_TILES) {
                    // An eye stands over it or beside it, on a roof or a balcony: it fills no way round the eye for certain.
                    continue;
                }
                double middle = Math.atan2((minY + maxY) / 2.0, (minX + maxX) / 2.0);
                double low = Double.MAX_VALUE, high = -Double.MAX_VALUE;
                float far = 0f;
                for (int corner = 0; corner < 4; corner++) {
                    float cx = (corner & 1) == 0 ? minX : maxX, cy = (corner & 2) == 0 ? minY : maxY;
                    double off = wrap(Math.atan2(cy, cx) - middle);
                    low = Math.min(low, off);
                    high = Math.max(high, off);
                    far = Math.max(far, (float) Math.sqrt(cx * cx + cy * cy));
                }
                float nearX = Math.max(minX, Math.min(0f, maxX)), nearY = Math.max(minY, Math.min(0f, maxY));
                float near = (float) Math.sqrt(nearX * nearX + nearY * nearY);
                // From any eye within the slack: no taller, no further off when its top is above the eye, no nearer when below.
                float top = b[at + 4] - eyeUp - SLACK_UP;
                wayRise[i] = top / (top > 0f ? far + SLACK_TILES : near - SLACK_TILES);
                wayReach[i] = far + SLACK_TILES;
                // And no wider: only the ways it fills from one side to the other from every such eye.
                double narrower = Math.asin(Math.min(1.0, SLACK_TILES / (near - SLACK_TILES)));
                wayFirst[i] = (long) Math.ceil((middle + low + narrower + Math.PI) / PER_BIN);
                wayLast[i] = (long) Math.floor((middle + high - narrower + Math.PI) / PER_BIN) - 1;
            }
            Arrays.fill(fill, 0);
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < blocks.count; i++) {
                    for (long way = wayFirst[i]; way <= wayLast[i]; way++) {
                        int bin = (int) Math.floorMod(way, (long) BINS);
                        if (pass == 0) {
                            fill[bin]++;
                        } else {
                            int slot = from[bin] + fill[bin]++;
                            reach[slot] = wayReach[i];
                            rise[slot] = wayRise[i];
                        }
                    }
                }
                if (pass == 0) {
                    from[0] = 0;
                    for (int bin = 0; bin < BINS; bin++) {
                        from[bin + 1] = from[bin] + fill[bin];
                    }
                    entries = from[BINS];
                    if (reach.length < entries) {
                        reach = new float[Integer.highestOneBit(entries) * 2];
                        rise = new float[reach.length];
                        sorting = new long[reach.length];
                    }
                    Arrays.fill(fill, 0);
                }
            }
            for (int bin = 0; bin < BINS; bin++) {
                int start = from[bin], end = from[bin + 1];
                for (int i = start; i < end; i++) {
                    sorting[i] = (long) Float.floatToIntBits(reach[i]) << 32 | Float.floatToIntBits(rise[i]) & 0xFFFFFFFFL;
                }
                Arrays.sort(sorting, start, end);
                for (int i = start; i < end; i++) {
                    reach[i] = Float.intBitsToFloat((int) (sorting[i] >>> 32));
                    rise[i] = Float.intBitsToFloat((int) sorting[i]);
                }
                for (int i = start + 1; i < end; i++) {
                    rise[i] = Math.max(rise[i], rise[i - 1]);
                }
            }
        }

        /**
         * Whether the blocks hide all of a flat opening, given by its corners on the map, the
         * height of its top and how many corners it has, from every way round the eye it is in.
         */
        boolean hides(float[] cornersX, float[] cornersY, int corners, float top) {
            top += SLACK_UP;
            double middle = 0.0;
            float near = Float.MAX_VALUE, far = 0f;
            for (int corner = 0; corner < corners; corner++) {
                float dx = cornersX[corner] - eyeX, dy = cornersY[corner] - eyeY;
                if (corner == 0) {
                    middle = Math.atan2(dy, dx);
                }
                far = Math.max(far, (float) Math.sqrt(dx * dx + dy * dy));
            }
            near = nearestTo(cornersX, cornersY, corners);
            if (near < 1e-3f) {
                return false;
            }
            double low = Double.MAX_VALUE, high = -Double.MAX_VALUE;
            for (int corner = 0; corner < corners; corner++) {
                double off = wrap(Math.atan2(cornersY[corner] - eyeY, cornersX[corner] - eyeX) - middle);
                low = Math.min(low, off);
                high = Math.max(high, off);
            }
            near = Math.max(near - SLACK_TILES, 1e-3f);
            far += SLACK_TILES;
            float above = top - eyeUp;
            float steepest = above / (above > 0f ? near : far);
            double wider = Math.asin(Math.min(1.0, SLACK_TILES / near));
            long first = (long) Math.floor((middle + low - wider + Math.PI) / PER_BIN), last = (long) Math.floor((middle + high + wider + Math.PI) / PER_BIN);
            for (long way = first; way <= last; way++) {
                int bin = (int) Math.floorMod(way, (long) BINS);
                // The blocks wholly nearer than the opening: the last of them holds the steepest.
                int start = from[bin], end = from[bin + 1], lowIndex = start, highIndex = end;
                while (lowIndex < highIndex) {
                    int mid = (lowIndex + highIndex) >>> 1;
                    if (reach[mid] < near) {
                        lowIndex = mid + 1;
                    } else {
                        highIndex = mid;
                    }
                }
                if (lowIndex == start || rise[lowIndex - 1] < steepest) {
                    return false;
                }
            }
            return true;
        }

        /** How near the eye the polygon of corners comes along the ground: a side of it may come nearer than any corner. */
        private float nearestTo(float[] cornersX, float[] cornersY, int corners) {
            float nearest = Float.MAX_VALUE;
            for (int corner = 0; corner < corners; corner++) {
                int next = (corner + 1) % corners;
                float ax = cornersX[corner] - eyeX, ay = cornersY[corner] - eyeY, bx = cornersX[next] - eyeX, by = cornersY[next] - eyeY;
                float ex = bx - ax, ey = by - ay, length = ex * ex + ey * ey;
                float t = length < 1e-9f ? 0f : Math.max(0f, Math.min(1f, -(ax * ex + ay * ey) / length));
                float px = ax + t * ex, py = ay + t * ey;
                nearest = Math.min(nearest, (float) Math.sqrt(px * px + py * py));
            }
            return nearest;
        }

        private static double wrap(double angle) {
            return angle - 2 * Math.PI * Math.floor((angle + Math.PI) / (2 * Math.PI));
        }
    }

    /** A set of rooms by their ids, each with a number kept with it, emptied in one step. */
    static final class Seen {
        private long[] ids = new long[256], values = new long[256];
        private int[] stamps = new int[256];
        private int stamp = 1, size;

        void clear() {
            stamp++;
            size = 0;
            airWhole = true;
            airCount = 0;
            if (stamp == Integer.MAX_VALUE) {
                Arrays.fill(stamps, 0);
                stamp = 1;
            }
        }

        boolean contains(long id) {
            return slotOf(id) >= 0;
        }

        private int slotOf(long id) {
            int mask = ids.length - 1;
            for (int slot = hash(id) & mask; stamps[slot] == stamp; slot = (slot + 1) & mask) {
                if (ids[slot] == id) {
                    return slot;
                }
            }
            return -1;
        }

        /** Adds a room, and says whether it was not there before. */
        boolean add(long id) {
            return put(id, 0L, false);
        }

        /** Adds a room if it is not there, keeps the number with it, and says whether it was not there before. */
        boolean put(long id, long value) {
            return put(id, value, true);
        }

        private boolean put(long id, long value, boolean keep) {
            if ((size + 1) * 2 > ids.length) {
                grow();
            }
            int mask = ids.length - 1;
            int slot = hash(id) & mask;
            for (; stamps[slot] == stamp; slot = (slot + 1) & mask) {
                if (ids[slot] == id) {
                    if (keep) {
                        values[slot] = value;
                    }
                    return false;
                }
            }
            stamps[slot] = stamp;
            ids[slot] = id;
            values[slot] = value;
            size++;
            return true;
        }

        int size() {
            return size;
        }

        /** Whether any room is in both this set and another. */
        boolean overlaps(Seen other) {
            for (int i = 0; i < ids.length; i++) {
                if (stamps[i] == stamp && other.contains(ids[i])) {
                    return true;
                }
            }
            return false;
        }

        /** How many slots the table has: every room of the set is in one of them, read by at. */
        int slots() {
            return ids.length;
        }

        /** The room in a slot, or OUTSIDE for an empty slot. */
        long at(int slot) {
            return stamps[slot] == stamp ? ids[slot] : OUTSIDE;
        }

        /** The number kept with the room in a slot. */
        long valueAt(int slot) {
            return values[slot];
        }

        private void grow() {
            long[] oldIds = ids, oldValues = values;
            int[] oldStamps = stamps;
            int oldStamp = stamp;
            ids = new long[oldIds.length * 2];
            values = new long[oldIds.length * 2];
            stamps = new int[oldIds.length * 2];
            stamp = 1;
            size = 0;
            for (int i = 0; i < oldIds.length; i++) {
                if (oldStamps[i] == oldStamp) {
                    put(oldIds[i], oldValues[i], true);
                }
            }
        }

        /**
         * Where the open air is in the picture the set was walked for: the rectangles it was
         * reached through, and the eye they were worked out from. Whole where the eye is out
         * of doors, and where the set was not walked for a picture.
         */
        private boolean airWhole = true;
        private float[] air = new float[2 * RegionTable.KEPT * 4];
        private int airCount;
        private float eyeX, eyeY, eyeUp, rightX, rightY, rightUp, upX, upY, upUp, forwardX, forwardY, forwardUp, tanAcross, tanUp;

        /** Takes the open air's rectangles and the eye from the walk that has just filled the set. */
        void airFrom(RoomSight walk, boolean outdoors) {
            airWhole = outdoors;
            airCount = walk.reached.copy(OUTSIDE, air, 0);
            airCount += walk.reachedThrough.copy(THROUGH_A_BUILDING, air, airCount);
            eyeX = walk.eyeX;
            eyeY = walk.eyeY;
            eyeUp = walk.eyeUp;
            rightX = walk.rightX;
            rightY = walk.rightY;
            rightUp = walk.rightUp;
            upX = walk.upX;
            upY = walk.upY;
            upUp = walk.upUp;
            forwardX = walk.forwardX;
            forwardY = walk.forwardY;
            forwardUp = walk.forwardUp;
            tanAcross = walk.tanAcross;
            tanUp = walk.tanUp;
        }

        /**
         * Whether any of a box in the open air may be in the picture: it covers some of a
         * rectangle the open air was reached through. A box the eye is in, or that reaches
         * behind it, is taken to.
         */
        boolean airSees(float minX, float minY, float minUp, float maxX, float maxY, float maxUp) {
            if (airWhole) {
                return true;
            }
            if (airCount == 0) {
                return false;
            }
            float lowX = Float.POSITIVE_INFINITY, lowY = Float.POSITIVE_INFINITY, highX = Float.NEGATIVE_INFINITY, highY = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < 8; i++) {
                float dx = ((i & 1) == 0 ? minX : maxX) - eyeX, dy = ((i & 2) == 0 ? minY : maxY) - eyeY, du = ((i & 4) == 0 ? minUp : maxUp) - eyeUp;
                float along = dx * forwardX + dy * forwardY + du * forwardUp;
                if (along < NEAR) {
                    return true;
                }
                float across = (dx * rightX + dy * rightY + du * rightUp) / (along * tanAcross), up = (dx * upX + dy * upY + du * upUp) / (along * tanUp);
                lowX = Math.min(lowX, across);
                highX = Math.max(highX, across);
                lowY = Math.min(lowY, up);
                highY = Math.max(highY, up);
            }
            for (int k = 0; k < airCount; k++) {
                if (highX > air[k * 4] && lowX < air[k * 4 + 2] && highY > air[k * 4 + 1] && lowY < air[k * 4 + 3]) {
                    return true;
                }
            }
            return false;
        }

        static int hash(long id) {
            long mixed = id * 0x9E3779B97F4A7C15L;
            return (int) (mixed ^ mixed >>> 32);
        }
    }

    /**
     * Rooms kept for a while after they were last found: the rooms whose contents are built.
     * A room comes in the moment a walk finds it and goes once no walk has for KEEP_NANOS, so
     * that a room at the edge of sight is not built, let go and built again as the eye moves.
     */
    static final class Kept {
        static final long KEEP_NANOS = 4_000_000_000L;
        private final long keepNanos;
        private Seen rooms = new Seen(), spare = new Seen();

        Kept() {
            this(KEEP_NANOS);
        }

        Kept(long keepNanos) {
            this.keepNanos = keepNanos;
        }
        /** Raised each time a room is kept that was not, or let go: what is worked out from the rooms kept holds while it stays the same. */
        int changes;

        boolean contains(long room) {
            return rooms.contains(room);
        }

        int size() {
            return rooms.size();
        }

        /** Keeps every room found now, as found at the time given, and adds to entered those that were not kept. */
        void found(Seen now, long nanos, Seen entered) {
            for (int slot = 0; slot < now.slots(); slot++) {
                long room = now.at(slot);
                if (room != OUTSIDE && rooms.put(room, nanos)) {
                    entered.add(room);
                    changes++;
                }
            }
        }

        /** Lets go of every room not found for the time it keeps them, and adds them to left. */
        void expire(long nanos, Seen left) {
            spare.clear();
            for (int slot = 0; slot < rooms.slots(); slot++) {
                long room = rooms.at(slot);
                if (room == OUTSIDE) {
                    continue;
                }
                if (nanos - rooms.valueAt(slot) >= keepNanos) {
                    left.add(room);
                    changes++;
                } else {
                    spare.put(room, rooms.valueAt(slot));
                }
            }
            Seen was = rooms;
            rooms = spare;
            spare = was;
        }

        void clear() {
            rooms.clear();
            changes++;
        }

        /** Adds every room kept to a set. */
        void copyTo(Seen into) {
            for (int slot = 0; slot < rooms.slots(); slot++) {
                long room = rooms.at(slot);
                if (room != OUTSIDE) {
                    into.add(room);
                }
            }
        }
    }

    /** The eye, which way it looks, and the half widths of its picture as tangents. */
    private float eyeX, eyeY, eyeUp, rightX, rightY, rightUp, upX, upY, upUp, forwardX, forwardY, forwardUp, tanAcross, tanUp;
    /** For each opening of the walk, its rectangle in the picture once worked out: least x, least y, greatest x, greatest y; NaN while not worked out. */
    private float[] covers = new float[256 * 4];
    /** For each opening, whether its rectangle was worked out and whether it covers any of the picture. */
    private byte[] worked = new byte[256];
    /**
     * For each opening, the horizon build its hiddenByBlocks answer was worked out against with
     * no growth (the picture's own walk, every frame), and the answer: it stands for as long as
     * the horizon does. Looked for afresh every frame, a street of windows in a tall building
     * was most of the logic thread's frame while it was faced. 0 is no answer.
     */
    private int[] hiddenFor = new int[256];
    private boolean[] hiddenWas = new boolean[256];
    /** The same for a walk round an eye, whose openings are grown: the eye it was worked out for, and the answer, which every picture of the round shares. */
    private int[] hiddenGrownFor = new int[256];
    private boolean[] hiddenGrownWas = new boolean[256];
    /**
     * For each opening, the eye its state was read for and the state: an opening reached through
     * several regions, or in several of the pictures of one walk round an eye, is read once. A
     * door opened in the frames a round is walked over is read by the next round, which comes
     * a quarter of a second later at most, and by the picture straight away.
     */
    private int[] stateFor = new int[256];
    private byte[] stateWas = new byte[256];
    private int walks, eyes, eyeNow;
    private static final byte UNWORKED = 0, EMPTY = 1, COVERS = 2;
    /**
     * For each region the walk reaches, the rectangle of the picture it has been reached
     * through so far: by lines that have not yet been out of some other building into the air,
     * and by lines that have.
     */
    private final RegionTable reached = new RegionTable(), reachedThrough = new RegionTable();
    /** Each opening twice, once from each side: the lists of the openings each region has. */
    private int[] first = new int[256], next = new int[512];
    private long[] keys = new long[256];
    private float[] stackRect = new float[64 * 4];
    private long[] stackRegion = new long[64];
    private boolean[] stackThrough = new boolean[64];
    private final float[] corners = new float[3 * 8], clipped = new float[3 * 20];
    private final float[] alongX = new float[4], alongY = new float[4];
    /**
     * The picture's horizon, and the walks' round the eye: those are walked from where the eye
     * was as they were set out, which by a walking pace is soon further than the slack from where
     * it is now, so each keeps its own, and what was worked out against the picture's stands
     * while the rounds are walked.
     */
    private final Horizon horizon = new Horizon(), roundHorizon = new Horizon();
    private int indexedFilling;
    private Openings indexed;
    /** How far each opening is taken to reach past its edges along the ground and up, and within what distance a shut one counts as open: see walkAllRound. */
    private float grow, rise, shutWithin;
    private Blocks blocks;
    private long eyeBuilding;
    /** Dev only: how many times the horizon has been built. */
    int horizonBuilds() {
        return horizon.builds;
    }

    /** Dev only: what the last walk did. */
    int openingsTested, openingsCovering, steps, hiddenByBlocks;

    /**
     * Works out the rooms an eye sees into, and adds them to seen.
     *
     * @param blocks      what stands solid in the open air round the eye
     * @param tanAcross   the tangent of half the picture's width as the eye sees it
     * @param tanUp       the tangent of half its height
     * @param start       the region the eye is in
     * @param eyeBuilding the building the eye is in, or OUTSIDE: its rooms stand in the way of nothing, since every line starts in them
     */
    void walk(Ground ground, Openings openings, Blocks blocks, ViewCamera.Pose eye, float tanAcross, float tanUp, long start, long eyeBuilding, Seen seen) {
        prepare(openings, blocks, eye.eyeX, eye.eyeY, eye.eyeUp, eyeBuilding, 0f, 0f, ++eyes);
        face(ground, openings, eye, tanAcross, tanUp, start, seen);
        seen.airFrom(this, start == OUTSIDE);
    }

    /** The six pictures of a cube round the eye, which between them see every way. */
    private static final float[][] CUBE = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private final ViewCamera.Pose cubeFace = new ViewCamera.Pose();

    /**
     * Works out the rooms an eye at a place could see into whichever way it looked, from
     * anywhere within grow tiles of the place, and through any shut door or drawn curtain
     * within shutWithin tiles of it as if it were open, and adds them to seen: the rooms whose
     * contents are built, so that a turn of the head, a step, or a door opened near the eye
     * finds them built. The other arguments are walk's.
     */
    void walkAllRound(Ground ground, Openings openings, Blocks blocks, float atX, float atY, float atUp, long start, long eyeBuilding, float grow, float shutWithin, Seen seen) {
        Round round = new Round();
        begin(round, atX, atY, atUp, start, eyeBuilding, grow, shutWithin, seen);
        boolean last;
        do {
            last = walkFace(ground, openings, blocks, round);
        } while (!last);
    }

    /**
     * A walk round an eye walked a picture at a time, so that the six pictures, which together
     * take a frame's worth on a street, are spread over six frames: see walkAllRound.
     */
    static final class Round {
        private float atX, atY, atUp, grow, shutWithin;
        private long start, eyeBuilding;
        private int eye, faces = CUBE.length;
        private Seen seen;

        boolean walking() {
            return faces < CUBE.length;
        }

        void drop() {
            faces = CUBE.length;
            seen = null;
        }
    }

    /** Sets out a walk round an eye, as walkAllRound's, that walkFace walks. */
    void begin(Round round, float atX, float atY, float atUp, long start, long eyeBuilding, float grow, float shutWithin, Seen seen) {
        round.atX = atX;
        round.atY = atY;
        round.atUp = atUp;
        round.start = start;
        round.eyeBuilding = eyeBuilding;
        round.grow = grow;
        round.shutWithin = shutWithin;
        round.seen = seen;
        round.eye = ++eyes;
        round.faces = 0;
    }

    /** Walks the next of a round's pictures, and says whether that was its last. */
    boolean walkFace(Ground ground, Openings openings, Blocks blocks, Round round) {
        if (!round.walking()) {
            throw new IllegalStateException("a walk round the eye was walked past its last picture");
        }
        prepare(openings, blocks, round.atX, round.atY, round.atUp, round.eyeBuilding, round.grow, round.shutWithin, round.eye);
        float[] way = CUBE[round.faces++];
        cubeFace.eyeAt(round.atX, round.atY, round.atUp);
        cubeFace.forwardX = way[0];
        cubeFace.forwardY = way[1];
        cubeFace.forwardUp = way[2];
        // Any up that is square to the way looked: a cube's face is square, and its picture is the same turned.
        float upX = way[2] != 0f ? 1f : 0f, upY = 0f, upUp = way[2] != 0f ? 0f : 1f;
        cubeFace.upX = upX;
        cubeFace.upY = upY;
        cubeFace.upUp = upUp;
        cubeFace.rightX = upY * way[2] - upUp * way[1];
        cubeFace.rightY = upUp * way[0] - upX * way[2];
        cubeFace.rightUp = upX * way[1] - upY * way[0];
        Seen seen = round.seen;
        if (!round.walking()) {
            round.seen = null;
        }
        face(ground, openings, cubeFace, 1f, 1f, round.start, seen);
        return !round.walking();
    }

    /** Lists the openings by region and takes what every picture walked from this eye shares. */
    private void prepare(Openings openings, Blocks blocks, float atX, float atY, float atUp, long eyeBuilding, float grow, float shutWithin, int eye) {
        // The blocks seen from the same eye hide the same, and the openings listed once are listed: a walk round the eye after the picture's reuses both.
        if (openings != indexed || openings.filling != indexedFilling) {
            index(openings);
            indexed = openings;
            indexedFilling = openings.filling;
        }
        eyeNow = eye;
        this.blocks = blocks;
        this.eyeBuilding = eyeBuilding;
        this.grow = grow;
        rise = Math.min(grow, RISE);
        this.shutWithin = shutWithin;
        eyeX = atX;
        eyeY = atY;
        eyeUp = atUp;
        openingsTested = openingsCovering = steps = hiddenByBlocks = 0;
    }

    /** One picture's walk, from the eye prepare was given. */
    private void face(Ground ground, Openings openings, ViewCamera.Pose eye, float tanAcross, float tanUp, long start, Seen seen) {
        rightX = eye.rightX;
        rightY = eye.rightY;
        rightUp = eye.rightUp;
        upX = eye.upX;
        upY = eye.upY;
        upUp = eye.upUp;
        forwardX = eye.forwardX;
        forwardY = eye.forwardY;
        forwardUp = eye.forwardUp;
        this.tanAcross = tanAcross;
        this.tanUp = tanUp;
        Arrays.fill(worked, 0, openings.count, UNWORKED);
        walks++;
        liveUsed = 0;
        if (liveFor.length < tableLength * 2) {
            liveFor = new int[tableLength * 2];
            liveFrom = new int[tableLength * 2];
            liveCount = new int[tableLength * 2];
        }
        reached.clear();
        reachedThrough.clear();
        if (start != OUTSIDE) {
            seen.add(start);
        }
        // The eye's storey counted from the land under it: on a hill level 0's floor is well over 0.
        int highestFromOutside = (int) Math.floor((eyeUp - ground.floorUp(eyeX, eyeY, 0)) / ViewCamera.LEVEL_HEIGHT) + ABOVE_FROM_OUTSIDE;
        int depth = 0;
        float whole = 1f + MARGIN;
        depth = push(depth, start, false, -whole, -whole, whole, whole);
        reached.unite(start, -whole, -whole, whole, whole);
        while (depth > 0) {
            depth--;
            steps++;
            long region = stackRegion[depth];
            boolean through = stackThrough[depth];
            float minX = stackRect[depth * 4], minY = stackRect[depth * 4 + 1], maxX = stackRect[depth * 4 + 2], maxY = stackRect[depth * 4 + 3];
            int slot = slotOf(region == THROUGH_A_BUILDING ? OUTSIDE : region);
            // A region is walked again for each rectangle it is reached through that no earlier one
            // holds. The first time, every opening it has is asked whether it faces the eye, covers
            // any of the picture, is open and stands clear of the blocks, which no rectangle changes,
            // and those that pass are kept: a later walk of it asks only them where they meet the
            // rectangle. A street walked again through each window of a house would otherwise read
            // its thousand openings each time.
            int kept = region == THROUGH_A_BUILDING ? 1 : 0;
            boolean listed = slot >= 0 && liveFor[slot * 2 + kept] == walks;
            int liveAt = listed ? liveFrom[slot * 2 + kept] : liveUsed, liveEnd = listed ? liveAt + liveCount[slot * 2 + kept] : Integer.MAX_VALUE;
            for (int entry = slot < 0 ? -1 : listed ? (liveAt < liveEnd ? live[liveAt] : -1) : first[slot]; entry >= 0;
                    entry = listed ? (++liveAt < liveEnd ? live[liveAt] : -1) : next[entry]) {
                int opening = entry >> 1;
                boolean fromHere = (entry & 1) == 0;
                long beyond = fromHere ? openings.there[opening] : openings.here[opening];
                if (beyond == OUTSIDE && (through || region != OUTSIDE && (fromHere ? openings.hereBuilding[opening] : openings.thereBuilding[opening]) != eyeBuilding)) {
                    beyond = THROUGH_A_BUILDING;
                }
                if (!listed) {
                    if (!faces(openings, opening, fromHere)) {
                        continue;
                    }
                    // From the open air, a room is seen into through a window no higher than the storey over the eye's.
                    if ((region == OUTSIDE || region == THROUGH_A_BUILDING) && beyond != OUTSIDE && beyond != THROUGH_A_BUILDING
                            && openings.z[opening] > highestFromOutside) {
                        continue;
                    }
                    openingsTested++;
                    if (worked[opening] == UNWORKED) {
                        worked[opening] = cover(openings, opening) ? COVERS : EMPTY;
                    }
                    if (worked[opening] == EMPTY) {
                        continue;
                    }
                    // Whether it is open is asked last but one: it reads the map.
                    int state;
                    if (stateFor[opening] == eyeNow) {
                        state = stateWas[opening];
                    } else {
                        state = state(ground, openings, opening);
                        stateFor[opening] = eyeNow;
                        stateWas[opening] = (byte) state;
                    }
                    if (state != SEE && !(state == SHUT && nearerThan(openings, opening, shutWithin))) {
                        continue;
                    }
                    if (region == OUTSIDE && hiddenByBlocks(openings, opening)) {
                        hiddenByBlocks++;
                        continue;
                    }
                    if (liveUsed == live.length) {
                        live = Arrays.copyOf(live, liveUsed * 2);
                    }
                    live[liveUsed++] = entry;
                }
                float lowX = Math.max(minX, covers[opening * 4]), lowY = Math.max(minY, covers[opening * 4 + 1]);
                float highX = Math.min(maxX, covers[opening * 4 + 2]), highY = Math.min(maxY, covers[opening * 4 + 3]);
                if (lowX >= highX || lowY >= highY) {
                    continue;
                }
                openingsCovering++;
                if (beyond != OUTSIDE && beyond != THROUGH_A_BUILDING) {
                    seen.add(beyond);
                }
                boolean throughBeyond = through || beyond == THROUGH_A_BUILDING;
                RegionTable table = throughBeyond ? reachedThrough : reached;
                if (table.unite(beyond, lowX, lowY, highX, highY)) {
                    depth = push(depth, beyond, throughBeyond, table.walkMinX, table.walkMinY, table.walkMaxX, table.walkMaxY);
                }
            }
            if (slot >= 0 && !listed) {
                liveFor[slot * 2 + kept] = walks;
                liveFrom[slot * 2 + kept] = liveAt;
                liveCount[slot * 2 + kept] = liveUsed - liveAt;
            }
        }
    }

    /**
     * For each region's slot, twice (as itself, and as the open air reached through a building),
     * the walk its openings that pass every test but the rectangle were listed in, where in live
     * the list starts and how long it is. And the lists, one after another, liveUsed long.
     */
    private int[] liveFor = new int[512], liveFrom = new int[512], liveCount = new int[512];
    private int[] live = new int[1024];
    private int liveUsed;

    /**
     * Dev only, after a picture's walk: what the walk made of the opening on (x, y, z) of the
     * kind given, gone through from the region given: whether it faces it, the rectangle it
     * covers, its state, whether the blocks hide it, and the rectangles the regions on its
     * two sides were reached through.
     */
    String explain(Ground ground, Openings openings, int x, int y, int z, int kind, long from) {
        for (int i = 0; i < openings.count; i++) {
            if (openings.x[i] != x || openings.y[i] != y || openings.z[i] != z || openings.kind[i] != kind) {
                continue;
            }
            boolean fromHere = openings.here[i] == from;
            long to = fromHere ? openings.there[i] : openings.here[i];
            String covers = worked[i] == COVERS ? String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f %.2f", covers(i, 0), covers(i, 1), covers(i, 2), covers(i, 3))
                    : worked[i] == EMPTY ? "none" : "not worked";
            return String.format(java.util.Locale.ROOT, "[opening %d: %d to %d, buildings %d / %d, faces %b, covers %s, state %d, hidden by blocks %b; %d reached through %s, through a building %s; %d reached through %s]",
                    i, from, to, openings.hereBuilding[i], openings.thereBuilding[i], faces(openings, i, fromHere), covers, state(ground, openings, i),
                    from == OUTSIDE && hiddenByBlocks(openings, i), from, reached.describe(from), reachedThrough.describe(from), to, reached.describe(to));
        }
        return "[no opening " + x + "," + y + "," + z + " of kind " + kind + "]";
    }

    private float covers(int opening, int k) {
        return covers[opening * 4 + k];
    }

    private boolean nearerThan(Openings openings, int opening, float reach) {
        float dx = openings.x[opening] + 0.5f - eyeX, dy = openings.y[opening] + 0.5f - eyeY;
        return dx * dx + dy * dy < reach * reach;
    }

    /** Whether the blocks between the eye and an opening, reached straight through the open air, hide all of it. */
    private boolean hiddenByBlocks(Openings openings, int opening) {
        Horizon from = grow != 0f ? roundHorizon : horizon;
        if (!from.holds(eyeX, eyeY, eyeUp, blocks, eyeBuilding)) {
            from.build(eyeX, eyeY, eyeUp, blocks, eyeBuilding);
        }
        if (grow != 0f) {
            if (hiddenGrownFor[opening] != eyeNow) {
                hiddenGrownFor[opening] = eyeNow;
                hiddenGrownWas[opening] = hiddenByBlocksNow(openings, opening, from);
            }
            return hiddenGrownWas[opening];
        }
        if (hiddenFor[opening] != horizon.builds) {
            hiddenFor[opening] = horizon.builds;
            hiddenWas[opening] = hiddenByBlocksNow(openings, opening, horizon);
        }
        return hiddenWas[opening];
    }

    private boolean hiddenByBlocksNow(Openings openings, int opening, Horizon against) {
        int x = openings.x[opening], y = openings.y[opening], kind = openings.kind[opening];
        if (kind == UP) {
            alongX[0] = x - grow;
            alongY[0] = y - grow;
            alongX[1] = x + 1 + grow;
            alongY[1] = y - grow;
            alongX[2] = x + 1 + grow;
            alongY[2] = y + 1 + grow;
            alongX[3] = x - grow;
            alongY[3] = y + 1 + grow;
            float[] h = openings.heights;
            int at = opening * 4;
            float top = Math.max(Math.max(h[at], h[at + 1]), Math.max(h[at + 2], h[at + 3]));
            return against.hides(alongX, alongY, 4, top + rise);
        }
        float toX = kind == NORTH ? x + 1 : x, toY = kind == NORTH ? y : y + 1;
        float top = Math.max(openings.heights[opening * 4 + 2], openings.heights[opening * 4 + 3]) + SLACK + rise;
        if (grow == 0f) {
            alongX[0] = x;
            alongY[0] = y;
            alongX[1] = toX;
            alongY[1] = toY;
            return against.hides(alongX, alongY, 2, top);
        }
        // The ground the opening's box covers, as coverGrown has it.
        alongX[0] = x - grow;
        alongY[0] = y - grow;
        alongX[1] = toX + grow;
        alongY[1] = y - grow;
        alongX[2] = toX + grow;
        alongY[2] = toY + grow;
        alongX[3] = x - grow;
        alongY[3] = toY + grow;
        return against.hides(alongX, alongY, 4, top);
    }

    /**
     * The level whose region an eye at height eyeUp over the square (x, y) is in, from the level
     * the character stands on: the one above where the eye is over that level's floor and the
     * square has no floor there (the well of a stair), the one below where it is under its own.
     */
    static int levelOf(Ground ground, int x, int y, int level, float eyeUp) {
        while (eyeUp > ground.floorUp(x + 0.5f, y + 0.5f, level + 1) && !ground.floored(x, y, level + 1)) {
            level++;
        }
        while (level > 0 && eyeUp < ground.floorUp(x + 0.5f, y + 0.5f, level) && !ground.floored(x, y, level)) {
            level--;
        }
        return level;
    }

    /** What an opening does to sight now. */
    static int state(Ground ground, Openings openings, int opening) {
        int kind = openings.kind[opening];
        if (kind == UP) {
            return ground.floored(openings.x[opening], openings.y[opening], openings.z[opening] + 1) ? SOLID : SEE;
        }
        return ground.edge(openings.x[opening], openings.y[opening], openings.z[opening], kind == NORTH);
    }

    private int push(int depth, long region, boolean through, float minX, float minY, float maxX, float maxY) {
        if (depth == stackRegion.length) {
            stackRegion = Arrays.copyOf(stackRegion, depth * 2);
            stackThrough = Arrays.copyOf(stackThrough, depth * 2);
            stackRect = Arrays.copyOf(stackRect, depth * 8);
        }
        stackRegion[depth] = region;
        stackThrough[depth] = through;
        stackRect[depth * 4] = minX;
        stackRect[depth * 4 + 1] = minY;
        stackRect[depth * 4 + 2] = maxX;
        stackRect[depth * 4 + 3] = maxY;
        return depth + 1;
    }

    /**
     * Whether the eye is on the side of an opening's plane that a line going through it from
     * the region it is left from starts on, or near enough the plane to look through it.
     */
    private boolean faces(Openings openings, int opening, boolean fromHere) {
        int kind = openings.kind[opening];
        float[] h = openings.heights;
        int at = opening * 4;
        // How far the eye is on the side of the opening the square it is kept with is on.
        float onHereSide = kind == NORTH ? eyeY - openings.y[opening] : kind == WEST ? eyeX - openings.x[opening]
                : (h[at] + h[at + 1] + h[at + 2] + h[at + 3]) / 4f - eyeUp;
        // An eye that may stand anywhere within grow of the place may stand that much further over the plane.
        float slack = THROUGH + (kind == UP ? rise : grow);
        return fromHere ? onHereSide >= -slack : onHereSide <= slack;
    }

    /** Works out the rectangle of the picture an opening covers, and says whether it covers any. */
    private boolean cover(Openings openings, int opening) {
        int x = openings.x[opening], y = openings.y[opening], kind = openings.kind[opening];
        float[] h = openings.heights;
        int at = opening * 4;
        // Most openings are wholly out of the picture: a ball round one tells it before its corners are worked out.
        float middleX = kind == WEST ? x : x + 0.5f, middleY = kind == NORTH ? y : y + 0.5f, middleUp = (h[at] + h[at + 1] + h[at + 2] + h[at + 3]) / 4f;
        float ball = 0.5f * (float) Math.sqrt(2f + (h[at + 3] - h[at]) * (h[at + 3] - h[at]) + (h[at + 2] - h[at + 1]) * (h[at + 2] - h[at + 1])) + grow + rise + SLACK + THROUGH;
        float dx = middleX - eyeX, dy = middleY - eyeY, du = middleUp - eyeUp;
        float along = dx * forwardX + dy * forwardY + du * forwardUp;
        if (along < -ball) {
            return false;
        }
        float across = Math.abs(dx * rightX + dy * rightY + du * rightUp), up = Math.abs(dx * upX + dy * upY + du * upUp);
        float wide = tanAcross * (1f + MARGIN), tall = tanUp * (1f + MARGIN);
        if (across - wide * along > ball * (float) Math.sqrt(1f + wide * wide) || up - tall * along > ball * (float) Math.sqrt(1f + tall * tall)) {
            return false;
        }
        if (grow > 0f) {
            return coverGrown(openings, opening);
        }
        if (kind == UP) {
            float h00 = h[at], h10 = h[at + 1], h11 = h[at + 2], h01 = h[at + 3];
            float low = Math.min(Math.min(h00, h10), Math.min(h11, h01)), high = Math.max(Math.max(h00, h10), Math.max(h11, h01));
            if (Math.abs(eyeUp - (low + high) / 2f) < THROUGH + (high - low) / 2f && eyeX > x - THROUGH && eyeX < x + 1 + THROUGH && eyeY > y - THROUGH && eyeY < y + 1 + THROUGH) {
                return whole(opening);
            }
            corner(0, x - grow, y - grow, h00);
            corner(1, x + 1 + grow, y - grow, h10);
            corner(2, x + 1 + grow, y + 1 + grow, h11);
            corner(3, x - grow, y + 1 + grow, h01);
            return project(opening, 4);
        }
        float toX = kind == NORTH ? x + 1 : x, toY = kind == NORTH ? y : y + 1;
        float low = Math.min(h[at], h[at + 1]) - SLACK - rise, high = Math.max(h[at + 2], h[at + 3]) + SLACK + rise;
        // An eye at the plane, over the opening or near it, looks through all of it.
        float off = kind == NORTH ? Math.abs(eyeY - y) : Math.abs(eyeX - x);
        float alongEye = kind == NORTH ? eyeX : eyeY, alongFrom = kind == NORTH ? x : y;
        if (off < THROUGH && alongEye > alongFrom - THROUGH && alongEye < alongFrom + 1 + THROUGH && eyeUp > low - THROUGH && eyeUp < high + THROUGH) {
            return whole(opening);
        }
        float fromX = kind == NORTH ? x - grow : x, fromY = kind == NORTH ? y : y - grow, endX = kind == NORTH ? toX + grow : toX, endY = kind == NORTH ? toY : toY + grow;
        corner(0, fromX, fromY, low);
        corner(1, endX, endY, low);
        corner(2, endX, endY, high);
        corner(3, fromX, fromY, high);
        return project(opening, 4);
    }

    /**
     * The rectangle of the picture an opening covers from any eye within grow of the place
     * along the ground and rise up: a line from such an eye through openings, moved back by
     * however far that eye stands from the place, goes from the place through the openings each
     * moved as far, so through the box each fills moved every way by as much. Grown only along
     * its own edge, an opening a step beyond a near one fell out of the near one's rectangle as
     * the eye walked up to it, and the room beyond came into the picture before it was built.
     */
    private boolean coverGrown(Openings openings, int opening) {
        int x = openings.x[opening], y = openings.y[opening], kind = openings.kind[opening];
        float[] h = openings.heights;
        int at = opening * 4;
        float lowX = x - grow, lowY = y - grow, highX, highY, low, high;
        if (kind == UP) {
            highX = x + 1 + grow;
            highY = y + 1 + grow;
            low = Math.min(Math.min(h[at], h[at + 1]), Math.min(h[at + 2], h[at + 3])) - rise;
            high = Math.max(Math.max(h[at], h[at + 1]), Math.max(h[at + 2], h[at + 3])) + rise;
        } else {
            highX = kind == NORTH ? x + 1 + grow : x + grow;
            highY = kind == NORTH ? y + grow : y + 1 + grow;
            low = Math.min(h[at], h[at + 1]) - SLACK - rise;
            high = Math.max(h[at + 2], h[at + 3]) + SLACK + rise;
        }
        if (eyeX > lowX - THROUGH && eyeX < highX + THROUGH && eyeY > lowY - THROUGH && eyeY < highY + THROUGH && eyeUp > low - THROUGH && eyeUp < high + THROUGH) {
            return whole(opening);
        }
        for (int i = 0; i < 8; i++) {
            corner(i, (i & 1) == 0 ? lowX : highX, (i & 2) == 0 ? lowY : highY, (i & 4) == 0 ? low : high);
        }
        int kept = 0;
        for (int i = 0; i < 8; i++) {
            if (corners[i * 3 + 2] >= NEAR) {
                System.arraycopy(corners, i * 3, clipped, kept++ * 3, 3);
            }
            // Each edge of the box once: from a corner to the one that differs from it in one bit set.
            for (int bit = 1; bit < 8; bit <<= 1) {
                int j = i | bit;
                if (j == i) {
                    continue;
                }
                float ai = corners[i * 3 + 2], aj = corners[j * 3 + 2];
                if (ai >= NEAR != aj >= NEAR) {
                    float t = (NEAR - ai) / (aj - ai);
                    clipped[kept * 3] = corners[i * 3] + t * (corners[j * 3] - corners[i * 3]);
                    clipped[kept * 3 + 1] = corners[i * 3 + 1] + t * (corners[j * 3 + 1] - corners[i * 3 + 1]);
                    clipped[kept * 3 + 2] = NEAR;
                    kept++;
                }
            }
        }
        return rectangle(opening, kept);
    }

    private boolean whole(int opening) {
        float whole = 1f + MARGIN;
        covers[opening * 4] = -whole;
        covers[opening * 4 + 1] = -whole;
        covers[opening * 4 + 2] = whole;
        covers[opening * 4 + 3] = whole;
        return true;
    }

    /** Puts a corner of the opening into the eye's terms: across, up, along the view. */
    private void corner(int at, float x, float y, float up) {
        float dx = x - eyeX, dy = y - eyeY, du = up - eyeUp;
        corners[at * 3] = dx * rightX + dy * rightY + du * rightUp;
        corners[at * 3 + 1] = dx * upX + dy * upY + du * upUp;
        corners[at * 3 + 2] = dx * forwardX + dy * forwardY + du * forwardUp;
    }

    /** Cuts the polygon of corners where it comes nearer the eye than NEAR, and keeps the rectangle of the picture what is left covers. */
    private boolean project(int opening, int count) {
        int kept = 0;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            float ai = corners[i * 3 + 2], aj = corners[j * 3 + 2];
            boolean inI = ai >= NEAR, inJ = aj >= NEAR;
            if (inI) {
                System.arraycopy(corners, i * 3, clipped, kept++ * 3, 3);
            }
            if (inI != inJ) {
                float t = (NEAR - ai) / (aj - ai);
                clipped[kept * 3] = corners[i * 3] + t * (corners[j * 3] - corners[i * 3]);
                clipped[kept * 3 + 1] = corners[i * 3 + 1] + t * (corners[j * 3 + 1] - corners[i * 3 + 1]);
                clipped[kept * 3 + 2] = NEAR;
                kept++;
            }
        }
        return rectangle(opening, kept);
    }

    /** Keeps the rectangle of the picture the first kept points of clipped cover, and says whether it covers any of it. */
    private boolean rectangle(int opening, int kept) {
        if (kept == 0) {
            return false;
        }
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < kept; i++) {
            float along = clipped[i * 3 + 2];
            float across = clipped[i * 3] / (along * tanAcross), up = clipped[i * 3 + 1] / (along * tanUp);
            minX = Math.min(minX, across);
            maxX = Math.max(maxX, across);
            minY = Math.min(minY, up);
            maxY = Math.max(maxY, up);
        }
        covers[opening * 4] = minX - MARGIN;
        covers[opening * 4 + 1] = minY - MARGIN;
        covers[opening * 4 + 2] = maxX + MARGIN;
        covers[opening * 4 + 3] = maxY + MARGIN;
        float whole = 1f + MARGIN;
        return maxX + MARGIN > -whole && minX - MARGIN < whole && maxY + MARGIN > -whole && minY - MARGIN < whole;
    }

    /** Lists each opening under both its regions, and forgets every rectangle worked out before. */
    private void index(Openings openings) {
        int count = openings.count;
        Arrays.fill(hiddenFor, 0);
        Arrays.fill(hiddenGrownFor, 0);
        Arrays.fill(stateFor, 0);
        if (worked.length < count) {
            int grown = Integer.highestOneBit(count) * 2;
            worked = new byte[grown];
            hiddenFor = new int[grown];
            hiddenWas = new boolean[grown];
            hiddenGrownFor = new int[grown];
            hiddenGrownWas = new boolean[grown];
            stateFor = new int[grown];
            stateWas = new byte[grown];
            covers = new float[grown * 4];
            next = new int[grown * 2];
        }
        int tableSize = Integer.highestOneBit(Math.max(count, 64)) * 4;
        if (keys.length < tableSize) {
            keys = new long[tableSize];
            first = new int[tableSize];
        }
        tableLength = tableSize;
        Arrays.fill(first, 0, tableSize, Integer.MIN_VALUE);
        for (int opening = 0; opening < count; opening++) {
            link(openings.here[opening], opening << 1);
            link(openings.there[opening], opening << 1 | 1);
        }
    }

    private int tableLength;

    private void link(long region, int entry) {
        int mask = tableLength - 1;
        int slot = Seen.hash(region) & mask;
        while (first[slot] != Integer.MIN_VALUE && keys[slot] != region) {
            slot = (slot + 1) & mask;
        }
        if (first[slot] == Integer.MIN_VALUE) {
            keys[slot] = region;
            first[slot] = -1;
        }
        next[entry] = first[slot];
        first[slot] = entry;
    }

    private int slotOf(long region) {
        int mask = tableLength - 1;
        for (int slot = Seen.hash(region) & mask; first[slot] != Integer.MIN_VALUE; slot = (slot + 1) & mask) {
            if (keys[slot] == region) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * For each region reached, up to KEPT rectangles of the picture it has been walked through:
     * one that lies inside one of them adds nothing and is not walked again. Held as rectangles
     * and not as one box round them all: a region reached through two windows either side of a
     * third has the third's rectangle inside that box, and what only the third shows (a house
     * across the street through it) was left out. A new one is walked as it is; past KEPT it is
     * also joined to the kept one it widens least, so a region reached through more than KEPT
     * openings can again pass over what lies between them inside that joined one. Walking a
     * region again asks only the openings its first walk kept (see face).
     */
    private static final class RegionTable {
        static final int KEPT = 8;
        private long[] keys = new long[64];
        /** For each slot, how many rectangles its region keeps, and the rectangles, KEPT to a slot. */
        private int[] counts = new int[64];
        private float[] rects = new float[64 * KEPT * 4];
        private int[] stamps = new int[64];
        private int stamp = 1, size;

        void clear() {
            stamp++;
            size = 0;
        }

        private int slotOf(long region) {
            int mask = keys.length - 1;
            for (int slot = Seen.hash(region) & mask; stamps[slot] == stamp; slot = (slot + 1) & mask) {
                if (keys[slot] == region) {
                    return slot;
                }
            }
            return -1;
        }

        /** Copies the rectangles a region has been walked through into into from rectangle at on, and says how many. */
        int copy(long region, float[] into, int at) {
            int slot = slotOf(region);
            if (slot < 0) {
                return 0;
            }
            System.arraycopy(rects, slot * KEPT * 4, into, at * 4, counts[slot] * 4);
            return counts[slot];
        }

        /** Dev only: the box round the rectangles a region has been walked through and how many there are, or "nothing". */
        String describe(long region) {
            int slot = slotOf(region);
            if (slot < 0) {
                return "nothing";
            }
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int k = 0; k < counts[slot]; k++) {
                int at = (slot * KEPT + k) * 4;
                minX = Math.min(minX, rects[at]);
                minY = Math.min(minY, rects[at + 1]);
                maxX = Math.max(maxX, rects[at + 2]);
                maxY = Math.max(maxY, rects[at + 3]);
            }
            return String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f %.2f in %d", minX, minY, maxX, maxY, counts[slot]);
        }

        /** The rectangle the region is to be walked again through, after unite says it is. */
        float walkMinX, walkMinY, walkMaxX, walkMaxY;

        /**
         * Takes a rectangle a region is reached through and says whether the region is to be
         * walked again: false where it lies inside one walked already. Every rectangle kept has
         * been walked through whole, so a region that already keeps KEPT has the one the new
         * rectangle widens least widened over it and walked through again whole: walked through
         * only the new one, what the widening took in was held walked, and a window between two
         * walked through, seen through some other way, was left out.
         */
        boolean unite(long region, float minX, float minY, float maxX, float maxY) {
            int slot = slotOf(region);
            if (slot < 0) {
                if ((size + 1) * 2 > keys.length) {
                    grow();
                }
                int mask = keys.length - 1;
                slot = Seen.hash(region) & mask;
                while (stamps[slot] == stamp) {
                    slot = (slot + 1) & mask;
                }
                stamps[slot] = stamp;
                keys[slot] = region;
                counts[slot] = 0;
                size++;
            }
            int base = slot * KEPT * 4, count = counts[slot];
            float[] r = rects;
            for (int k = 0; k < count; k++) {
                int at = base + k * 4;
                if (minX >= r[at] && minY >= r[at + 1] && maxX <= r[at + 2] && maxY <= r[at + 3]) {
                    return false;
                }
            }
            int at;
            if (count < KEPT) {
                at = base + count * 4;
                counts[slot] = count + 1;
            } else {
                at = base;
                float least = Float.MAX_VALUE;
                for (int k = 0; k < KEPT; k++) {
                    int each = base + k * 4;
                    float widened = (Math.max(maxX, r[each + 2]) - Math.min(minX, r[each])) * (Math.max(maxY, r[each + 3]) - Math.min(minY, r[each + 1]))
                            - (r[each + 2] - r[each]) * (r[each + 3] - r[each + 1]);
                    if (widened < least) {
                        least = widened;
                        at = each;
                    }
                }
                minX = Math.min(minX, r[at]);
                minY = Math.min(minY, r[at + 1]);
                maxX = Math.max(maxX, r[at + 2]);
                maxY = Math.max(maxY, r[at + 3]);
            }
            r[at] = walkMinX = minX;
            r[at + 1] = walkMinY = minY;
            r[at + 2] = walkMaxX = maxX;
            r[at + 3] = walkMaxY = maxY;
            return true;
        }

        private void grow() {
            long[] oldKeys = keys;
            int[] oldCounts = counts, oldStamps = stamps;
            float[] oldRects = rects;
            int oldStamp = stamp;
            keys = new long[oldKeys.length * 2];
            counts = new int[oldKeys.length * 2];
            rects = new float[oldKeys.length * 2 * KEPT * 4];
            stamps = new int[oldKeys.length * 2];
            stamp = 1;
            int mask = keys.length - 1;
            for (int i = 0; i < oldKeys.length; i++) {
                if (oldStamps[i] == oldStamp) {
                    int slot = Seen.hash(oldKeys[i]) & mask;
                    while (stamps[slot] == stamp) {
                        slot = (slot + 1) & mask;
                    }
                    stamps[slot] = stamp;
                    keys[slot] = oldKeys[i];
                    counts[slot] = oldCounts[i];
                    System.arraycopy(oldRects, i * KEPT * 4, rects, slot * KEPT * 4, KEPT * 4);
                }
            }
        }
    }
}

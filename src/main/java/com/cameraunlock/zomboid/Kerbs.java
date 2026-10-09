package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * Where a kerb runs. The map is squares, and a street that turns a corner or bends is drawn
 * on it as a stair of them. A kerb laid along the edges of those squares is that stair, cast
 * in concrete. Here the edge of the street is taken for what the stair stands for: a line
 * that runs straight where the squares run straight, slants where they step evenly, and
 * goes round a corner as a curve.
 *
 * The line passes through the middle of every edge between a street's square and the land's,
 * so a straight kerb lies along the squares' edges, and through the middle of each tread of a
 * stair, which for an even stair is one straight line. Those points are not joined: they
 * pull a smooth curve towards them, as the pegs of a spline do, so a square corner is
 * rounded, by a third of a square and a little more at its point.
 *
 * What is kept is not the line but, at points an eighth of a square apart, how far each is
 * from it: above nothing on the street's side, below on the land's. Whoever needs the kerb
 * reads where that crosses nothing. The same squares give the same numbers whichever chunk
 * asks, so the kerb of one chunk meets the next one's exactly.
 */
final class Kerbs {
    /** How many points of the field there are to a square's side. */
    static final int FINE = 8;
    static final int POINTS = Terrain.CHUNK_TILES * FINE + 1;
    /** How many squares round a chunk are looked at: further than anything that bends the line inside it. */
    static final int MARGIN = 24;
    static final int SPAN = Terrain.CHUNK_TILES + 2 * MARGIN;
    /** The longest tread a stair may have and still be a slant. A longer one is a straight kerb with a jog at its end. */
    private static final int LONGEST_TREAD = 8;
    /** The field is the true distance out to this far from the line, in squares, and no more than it beyond. */
    static final float NEAR = 1f;
    /**
     * How far off the line a point is still on it, squared, in squares. The line is a spline
     * worked out from pegs, and a straight run of it comes out a few millionths of a millionth
     * off its squares' edges, more the further the chunk is from the map's corner. Every
     * reader tells the street's side from the land's by the sign of the field and counts an
     * exact 0 as land: a point on a straight kerb left at 1e-12 was the street's, and the
     * square beside it was taken for one the kerb crosses.
     */
    private static final double ON_THE_LINE = 1e-18;

    private static final int CORNERS = SPAN + 1;
    private static final int[] DX = {1, 0, -1, 0}, DY = {0, 1, 0, -1};
    private static final int NONE = -1;

    /** The edges of the kerb, each from a corner of the squares in one of four directions, the street on its left. */
    private static final boolean[] edge = new boolean[CORNERS * CORNERS * 4];
    private static final int[] next = new int[edge.length];
    private static final boolean[] follows = new boolean[edge.length];
    private static final boolean[] walked = new boolean[edge.length];
    private static int[] chain = new int[256];
    private static int[] runStart = new int[64], runLength = new int[64];
    private static double[] pegs = new double[512];
    private static final float[] nearest = new float[POINTS * POINTS];
    private static final boolean[] streetSide = new boolean[POINTS * POINTS];

    private Kerbs() {}

    /**
     * The field of a chunk: POINTS by POINTS with x first, the chunk's own corner first. kinds
     * and hard are SPAN by SPAN, x first, the chunk in their middle: what each square is, as
     * Terrain names it, and whether the line must keep out of it, as it must out of a house.
     * (originX, originY) is the chunk's corner on the map. Null where the chunk has no street
     * in it. One thread.
     */
    static float[] field(byte[] kinds, boolean[] hard, int originX, int originY) {
        boolean any = false;
        for (int i = MARGIN - 1; i <= MARGIN + Terrain.CHUNK_TILES && !any; i++) {
            for (int j = MARGIN - 1; j <= MARGIN + Terrain.CHUNK_TILES && !any; j++) {
                any = kinds[i * SPAN + j] == Terrain.STREET;
            }
        }
        if (!any) {
            return null;
        }
        Arrays.fill(nearest, Float.POSITIVE_INFINITY);
        findEdges(kinds);
        Arrays.fill(walked, false);
        // A kerb that ends, at water or where the map does, starts at an edge nothing leads to.
        for (int at = 0; at < edge.length; at++) {
            if (edge[at] && !follows[at]) {
                walk(at, false, originX - MARGIN, originY - MARGIN);
            }
        }
        for (int at = 0; at < edge.length; at++) {
            if (edge[at] && !walked[at]) {
                walk(at, true, originX - MARGIN, originY - MARGIN);
            }
        }
        float[] field = new float[POINTS * POINTS];
        boolean street = false;
        for (int p = 0; p < POINTS; p++) {
            for (int q = 0; q < POINTS; q++) {
                int at = p * POINTS + q;
                if (nearest[at] < NEAR * NEAR) {
                    field[at] = (streetSide[at] ? 1f : -1f) * (float) Math.sqrt(nearest[at]);
                } else {
                    // Far from any kerb every square round the point is the one thing.
                    int i = MARGIN + p / FINE, j = MARGIN + q / FINE;
                    boolean low = kinds[i * SPAN + j] == Terrain.STREET || p % FINE == 0 && kinds[(i - 1) * SPAN + j] == Terrain.STREET
                            || q % FINE == 0 && kinds[i * SPAN + j - 1] == Terrain.STREET
                            || p % FINE == 0 && q % FINE == 0 && kinds[(i - 1) * SPAN + j - 1] == Terrain.STREET;
                    field[at] = low ? NEAR : -NEAR;
                }
            }
        }
        // The squares round the chunk as well: one of them holds the points on the chunk's edge as its own do.
        for (int i = -1; i <= Terrain.CHUNK_TILES; i++) {
            for (int j = -1; j <= Terrain.CHUNK_TILES; j++) {
                if (!hard[(i + MARGIN) * SPAN + j + MARGIN]) {
                    continue;
                }
                for (int p = Math.max(i * FINE, 0); p <= Math.min((i + 1) * FINE, POINTS - 1); p++) {
                    for (int q = Math.max(j * FINE, 0); q <= Math.min((j + 1) * FINE, POINTS - 1); q++) {
                        field[p * POINTS + q] = Math.min(field[p * POINTS + q], 0f);
                    }
                }
            }
        }
        for (int at = 0; at < field.length && !street; at++) {
            street = field[at] > 0f;
        }
        return street ? field : null;
    }

    /**
     * Whether a kerb runs along some stretch of the north edge of the chunk's square (x, y), or
     * of its west edge: two neighbouring points of the field on the edge both lie nearer the
     * line than a wall's skin is to its own line. Its face is then as good as in the plane of
     * the edge there: round the corner of a lawn at a drive's mouth the line comes back onto
     * the edge by a few hundredths of a square. Where the line only crosses the edge, no two
     * neighbours are that near: they are an eighth of a square apart.
     */
    static boolean alongEdge(float[] field, int x, int y, boolean north) {
        boolean last = false;
        for (int i = 0; i <= FINE; i++) {
            int p = x * FINE + (north ? i : 0), q = y * FINE + (north ? 0 : i);
            boolean on = Math.abs(field[p * POINTS + q]) <= WorldScene.WALL_HALF;
            if (on && last) {
                return true;
            }
            last = on;
        }
        return false;
    }

    /** The field at a point of the chunk, (x, y) in squares from its corner. */
    static float at(float[] field, float x, float y) {
        float p = Math.clamp(x * FINE, 0f, POINTS - 1f), q = Math.clamp(y * FINE, 0f, POINTS - 1f);
        int p0 = Math.min((int) p, POINTS - 2), q0 = Math.min((int) q, POINTS - 2);
        float a = p - p0, b = q - q0;
        int at = p0 * POINTS + q0;
        return (field[at] * (1f - a) + field[at + POINTS] * a) * (1f - b) + (field[at + 1] * (1f - a) + field[at + POINTS + 1] * a) * b;
    }

    private static boolean street(byte[] kinds, int i, int j) {
        return kinds[i * SPAN + j] == Terrain.STREET;
    }

    /** Whether a square is land a street has a kerb against. Water has none, and neither has what is not loaded or not looked at. */
    private static boolean land(byte[] kinds, int i, int j) {
        if (i < 0 || j < 0 || i >= SPAN || j >= SPAN) {
            return false;
        }
        byte kind = kinds[i * SPAN + j];
        return kind == Terrain.NATURAL || kind == Terrain.MADE;
    }

    private static void findEdges(byte[] kinds) {
        Arrays.fill(edge, false);
        for (int i = 0; i < SPAN; i++) {
            for (int j = 0; j < SPAN; j++) {
                if (!street(kinds, i, j)) {
                    continue;
                }
                // Round the square with it on the left.
                edge[(i * CORNERS + j) * 4] = land(kinds, i, j - 1);
                edge[((i + 1) * CORNERS + j) * 4 + 1] = land(kinds, i + 1, j);
                edge[((i + 1) * CORNERS + j + 1) * 4 + 2] = land(kinds, i, j + 1);
                edge[(i * CORNERS + j + 1) * 4 + 3] = land(kinds, i - 1, j);
            }
        }
        Arrays.fill(follows, false);
        for (int at = 0; at < edge.length; at++) {
            next[at] = NONE;
            if (!edge[at]) {
                continue;
            }
            int way = at & 3, corner = (at >> 2) + DX[way] * CORNERS + DY[way];
            // Where two streets' squares touch at a corner the kerb keeps to the square it is on.
            for (int turn : TURNS) {
                int onward = corner * 4 + (way + turn & 3);
                if (edge[onward]) {
                    next[at] = onward;
                    follows[onward] = true;
                    break;
                }
            }
        }
    }

    private static final int[] TURNS = {1, 0, 3};

    /** Follows one kerb from an edge of it, round to that edge again or to its end, and lays its line into the field. */
    private static void walk(int from, boolean round, int cornerX, int cornerY) {
        int edges = 0;
        for (int at = from; at != NONE && !walked[at]; at = next[at]) {
            walked[at] = true;
            if (edges == chain.length) {
                chain = Arrays.copyOf(chain, edges * 2);
            }
            chain[edges++] = at;
        }
        // The runs of it: each as long as it goes one way.
        int first = 0;
        if (round) {
            while ((chain[first] & 3) == (chain[(first + edges - 1) % edges] & 3)) {
                first++;
            }
        }
        int runs = 0;
        for (int k = 0; k < edges; k++) {
            int way = chain[(first + k) % edges] & 3;
            if (k > 0 && way == (chain[(first + k - 1) % edges] & 3)) {
                runLength[runs - 1]++;
                continue;
            }
            if (runs == runStart.length) {
                runStart = Arrays.copyOf(runStart, runs * 2);
                runLength = Arrays.copyOf(runLength, runs * 2);
            }
            runStart[runs] = (first + k) % edges;
            runLength[runs++] = 1;
        }
        int count = 0;
        if (pegs.length < 2 * (edges + 6)) {
            pegs = new double[4 * (edges + 6)];
        }
        if (!round) {
            // A kerb that ends goes on past its end as it was going, so that its last length is straight.
            count = 3;
        }
        for (int run = 0; run < runs; run++) {
            int start = chain[runStart[run]], way = start & 3, length = runLength[run];
            double x = cornerX + (start >> 2) / CORNERS, y = cornerY + (start >> 2) % CORNERS;
            if (tread(run, runs, round)) {
                pegs[2 * count] = x + DX[way] * length / 2.0;
                pegs[2 * count++ + 1] = y + DY[way] * length / 2.0;
                continue;
            }
            for (int k = 0; k < length; k++) {
                pegs[2 * count] = x + DX[way] * (k + 0.5);
                pegs[2 * count++ + 1] = y + DY[way] * (k + 0.5);
            }
        }
        if (!round) {
            int before = chain[0] & 3, after = chain[edges - 1] & 3;
            for (int k = 1; k <= 3; k++) {
                pegs[2 * (3 - k)] = pegs[6] - DX[before] * k;
                pegs[2 * (3 - k) + 1] = pegs[7] - DY[before] * k;
                pegs[2 * count] = pegs[2 * count - 2] + DX[after];
                pegs[2 * count + 1] = pegs[2 * count - 1] + DY[after];
                count++;
            }
        }
        for (int k = round ? 0 : 1; k < (round ? count : count - 2); k++) {
            lay((k + count - 1) % count, k, (k + 1) % count, (k + 2) % count, cornerX + MARGIN, cornerY + MARGIN);
        }
    }

    /**
     * Whether a run is the tread of a stair: it turns one way into it and the other way out,
     * and the risers at both its ends are short. A stair that stands for a slanting line has
     * all its risers one square, or all its treads, and the line goes through the middle of
     * each. One whose treads and risers are both two squares is the same stair on a coarser
     * map. Past that the middles are too far from the corners between them, and what lies
     * there is no stair but a kerb with a jog in it.
     */
    private static boolean tread(int run, int runs, boolean round) {
        int length = runLength[run];
        if (length < 2 || length > LONGEST_TREAD || !round && (run == 0 || run == runs - 1)) {
            return false;
        }
        int before = (run + runs - 1) % runs, after = (run + 1) % runs;
        int way = chain[runStart[run]] & 3;
        boolean leftIn = (chain[runStart[before]] + 1 & 3) == way, leftOut = (way + 1 & 3) == (chain[runStart[after]] & 3);
        int riser = Math.max(runLength[before], runLength[after]);
        return leftIn != leftOut && (riser == 1 || riser == 2 && length == 2);
    }

    /** Lays the length of the curve that four pegs in a row shape between the middle two, and notes how far each point of the field is from it. */
    private static void lay(int a, int b, int c, int d, int originX, int originY) {
        double ax = pegs[2 * a], ay = pegs[2 * a + 1], bx = pegs[2 * b], by = pegs[2 * b + 1];
        double cx = pegs[2 * c], cy = pegs[2 * c + 1], dx = pegs[2 * d], dy = pegs[2 * d + 1];
        double reach = Terrain.CHUNK_TILES + NEAR + 1.0;
        if (Math.max(Math.max(ax, bx), Math.max(cx, dx)) < originX - NEAR - 1.0 || Math.min(Math.min(ax, bx), Math.min(cx, dx)) > originX + reach
                || Math.max(Math.max(ay, by), Math.max(cy, dy)) < originY - NEAR - 1.0 || Math.min(Math.min(ay, by), Math.min(cy, dy)) > originY + reach) {
            return;
        }
        int pieces = Math.clamp((int) Math.ceil(Math.hypot(cx - bx, cy - by) * 6.0), 3, 24);
        double fromX = 0.0, fromY = 0.0;
        for (int piece = 0; piece <= pieces; piece++) {
            double t = (double) piece / pieces, u = 1.0 - t;
            double wa = u * u * u / 6.0, wb = (3.0 * t * t * t - 6.0 * t * t + 4.0) / 6.0, wc = (-3.0 * t * t * t + 3.0 * t * t + 3.0 * t + 1.0) / 6.0, wd = t * t * t / 6.0;
            double toX = wa * ax + wb * bx + wc * cx + wd * dx, toY = wa * ay + wb * by + wc * cy + wd * dy;
            if (piece > 0) {
                note(fromX - originX, fromY - originY, toX - originX, toY - originY);
            }
            fromX = toX;
            fromY = toY;
        }
    }

    /** One straight piece of a kerb's line, in squares from the chunk's corner, the street on its left. */
    private static void note(double fromX, double fromY, double toX, double toY) {
        int lowP = Math.max(0, (int) Math.ceil((Math.min(fromX, toX) - NEAR) * FINE)), highP = Math.min(POINTS - 1, (int) Math.floor((Math.max(fromX, toX) + NEAR) * FINE));
        int lowQ = Math.max(0, (int) Math.ceil((Math.min(fromY, toY) - NEAR) * FINE)), highQ = Math.min(POINTS - 1, (int) Math.floor((Math.max(fromY, toY) + NEAR) * FINE));
        double alongX = toX - fromX, alongY = toY - fromY, squared = alongX * alongX + alongY * alongY;
        for (int p = lowP; p <= highP; p++) {
            for (int q = lowQ; q <= highQ; q++) {
                double x = (double) p / FINE - fromX, y = (double) q / FINE - fromY;
                double t = squared == 0.0 ? 0.0 : Math.clamp((x * alongX + y * alongY) / squared, 0.0, 1.0);
                double offX = x - t * alongX, offY = y - t * alongY;
                double off = offX * offX + offY * offY;
                float away = off < ON_THE_LINE ? 0f : (float) off;
                if (away < nearest[p * POINTS + q]) {
                    nearest[p * POINTS + q] = away;
                    streetSide[p * POINTS + q] = away > 0f && alongX * y - alongY * x > 0.0;
                }
            }
        }
    }
}

package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * Where the lanes of a road run. A road the map paints a yellow centre line down is driven
 * on either side of that line, a lane between each white line and the next, out to the kerb,
 * and its tarmac is worn along each lane: a dark band of oil down the middle, where engines
 * drip, and two paler tracks either side of it, where the wheels go. A car park has no centre
 * line, and so no lanes.
 *
 * The lines are the game's own sprites, which paint a line a little in from one edge of
 * their square, and a road runs along the map's x or y: the lanes are worked out across the
 * road from the squares alone, so a square is given its lane by the same numbers whichever
 * chunk asks.
 */
final class Lanes {
    /**
     * Floats kept for each square of a chunk: which way the road is crossed, the centre line's
     * place that way, how far from it the square's lane begins and how wide it is, where the road
     * begins and ends that way, and how much of the lane is drawn at each corner of the square.
     */
    static final int FLOATS = 10;
    static final int AXIS = 0, CENTRE = 1, INNER = 2, WIDTH = 3, LOW = 4, HIGH = 5, CORNER = 6;
    /** Which way a road is crossed: along x for a road that runs along y, and along y for one that runs along x. */
    static final float NONE = 0f, ACROSS_X = 1f, ACROSS_Y = 2f;
    /** How far either way across a road its kerbs and lines are looked for, in squares. */
    private static final int WIDEST = 12;
    /**
     * How many rows of squares either way along a road its lines are looked for in, so that a
     * row the paint misses still has its lanes. The map's dashed lines are mostly two squares
     * painted and two left, and leave out at most eight.
     */
    private static final int ALONG = 4;
    /** The narrowest a lane is, in tiles: two white lines nearer than that are one, and a strip narrower is worn as no lane. */
    static final float NARROWEST = 1.5f;
    /**
     * The widest the outermost lane is, in tiles: tarmac further from the last line than that,
     * with no kerb to end it, is a lot or a yard beside the road, and the road ends at that line.
     */
    static final float WIDEST_LANE = 6f;
    /** How far apart the lines of one centre may lie: the two of a double line are 0.16 apart. */
    private static final float ONE_CENTRE = 0.5f;
    /** How many white lines one side of a road can hold within its window. */
    private static final int SIDE_LINES = (2 * WIDEST + 1) * (2 * ALONG + 1) * 2;

    /**
     * What Kinds keeps of the lines on a square, a bit a line: yellow ones in the low four
     * bits and white ones in the high four. Of each four, the first two are lines that run
     * along y, near the square's low x edge and near its high one, and the last two lines
     * that run along x, near its low y edge and its high one.
     */
    static final int YELLOW = 0, WHITE = 4;
    /** How far into its square each of the four lines lies. */
    private static final float[] INTO = {0.0703f, 0.9141f, 0.0859f, 0.9297f};
    private static final String SHEET = "street_trafficlines_01_";

    private Lanes() {}

    /** The bit of a straight line of the road's sheet, or 0 for anything else. */
    static int mark(String name) {
        if (name == null || !name.startsWith(SHEET) || name.length() > SHEET.length() + 2) {
            return 0;
        }
        int number = 0;
        for (int at = SHEET.length(); at < name.length(); at++) {
            char digit = name.charAt(at);
            if (digit < '0' || digit > '9') {
                return 0;
            }
            number = number * 10 + digit - '0';
        }
        // 0..7 faded white, 8..15 white, 16..23 faded yellow, 24..31 yellow; the odd ones are corners.
        if (number > 31 || number % 2 != 0) {
            return 0;
        }
        int line = number % 8 / 2;
        // The sheet's four are a line along y at the low x edge, along x at the low y edge, along y at the high x edge, along x at the high y edge.
        int bit = line == 0 ? 0 : line == 1 ? 2 : line == 2 ? 1 : 3;
        return 1 << (bit + (number >= 16 ? YELLOW : WHITE));
    }

    /**
     * The lanes of a chunk, FLOATS a square, x first, or null where no square of it is in a
     * lane. kinds, footway and marks are Kerbs.SPAN by Kerbs.SPAN, x first, the chunk in their
     * middle: what each square is as Terrain names it, whether it is footway, and its lines as
     * mark gives them. (originX, originY) is the chunk's corner on the map. One thread.
     */
    static float[] of(byte[] kinds, boolean[] footway, byte[] marks, int originX, int originY) {
        int tiles = Terrain.CHUNK_TILES, ring = tiles + 2;
        float[] around = new float[ring * ring * CORNER];
        double[] sides = new double[2 * SIDE_LINES];
        boolean any = false;
        for (int i = 0; i < ring; i++) {
            for (int j = 0; j < ring; j++) {
                int at = (i * ring + j) * CORNER;
                describe(kinds, footway, marks, Kerbs.MARGIN - 1 + i, Kerbs.MARGIN - 1 + j, originX - Kerbs.MARGIN, originY - Kerbs.MARGIN, around, at, sides);
                any |= around[at] != NONE && i > 0 && j > 0 && i <= tiles && j <= tiles;
            }
        }
        if (!any) {
            return null;
        }
        float[] lanes = new float[tiles * tiles * FLOATS];
        for (int i = 0; i < tiles; i++) {
            for (int j = 0; j < tiles; j++) {
                int own = ((i + 1) * ring + j + 1) * CORNER, at = (i * tiles + j) * FLOATS;
                System.arraycopy(around, own, lanes, at, CORNER);
                if (around[own] == NONE) {
                    continue;
                }
                // A corner is in the lane as far as every square of tarmac round it is of this same road, or lies past
                // its edge: where the road begins or ends, at a junction or where it widens, the lane fades out over
                // the square.
                for (int corner = 0; corner < 4; corner++) {
                    int ci = i + 1 + (corner == 1 || corner == 2 ? 1 : 0), cj = j + 1 + (corner >= 2 ? 1 : 0);
                    boolean whole = true;
                    for (int di = -1; di <= 0; di++) {
                        for (int dj = -1; dj <= 0; dj++) {
                            int other = ((ci + di) * ring + cj + dj) * CORNER;
                            float across = (around[own] == ACROSS_X ? originX - 1 + ci + di : originY - 1 + cj + dj) + 0.5f;
                            whole &= !tarmac(kinds, footway, Kerbs.MARGIN - 1 + ci + di, Kerbs.MARGIN - 1 + cj + dj)
                                    || around[other] == around[own] && around[other + CENTRE] == around[own + CENTRE]
                                    && around[other + LOW] == around[own + LOW] && around[other + HIGH] == around[own + HIGH]
                                    || around[other] == NONE && (across < around[own + LOW] || across > around[own + HIGH]);
                        }
                    }
                    lanes[at + CORNER + corner] = whole ? 1f : 0f;
                }
            }
        }
        return lanes;
    }

    /** How much of a lane a point (a, b) of a square is in, from its corners: see of. */
    static float drawn(float[] lanes, int at, float a, float b) {
        float c0 = lanes[at + CORNER], c1 = lanes[at + CORNER + 1], c2 = lanes[at + CORNER + 2], c3 = lanes[at + CORNER + 3];
        return (c0 * (1f - a) + c1 * a) * (1f - b) + (c3 * (1f - a) + c2 * a) * b;
    }

    private static boolean tarmac(byte[] kinds, boolean[] footway, int i, int j) {
        return i >= 0 && j >= 0 && i < Kerbs.SPAN && j < Kerbs.SPAN && kinds[i * Kerbs.SPAN + j] == Terrain.STREET && !footway[i * Kerbs.SPAN + j];
    }

    /** Writes the way across and the lane of square (i, j) of the window, all 0 for none, into into from at. */
    private static void describe(byte[] kinds, boolean[] footway, byte[] marks, int i, int j, int windowX, int windowY, float[] into, int at, double[] sides) {
        Arrays.fill(into, at, at + CORNER, 0f);
        if (!tarmac(kinds, footway, i, j)) {
            return;
        }
        boolean crossedX = across(kinds, footway, marks, i, j, true, windowX, windowY, into, at, sides);
        float[] lane = crossedX ? Arrays.copyOfRange(into, at, at + CORNER) : null;
        boolean crossedY = across(kinds, footway, marks, i, j, false, windowX, windowY, into, at, sides);
        // Lanes both ways are a junction, where traffic crosses every way.
        if (crossedX == crossedY) {
            Arrays.fill(into, at, at + CORNER, 0f);
        } else if (crossedX) {
            System.arraycopy(lane, 0, into, at, CORNER);
            into[at] = ACROSS_X;
        } else {
            into[at] = ACROSS_Y;
        }
    }

    /**
     * Whether square (i, j) is in a lane of a road crossed along x (or along y), and if so
     * writes the centre line's place on the map that way, where the square's lane begins and how
     * wide it is, and where the road begins and ends that way, into into from at + CENTRE.
     */
    private static boolean across(byte[] kinds, boolean[] footway, byte[] marks, int i, int j, boolean alongX, int windowX, int windowY, float[] into, int at, double[] sides) {
        int low = 0, high = 0;
        while (low < WIDEST && step(kinds, footway, i, j, -(low + 1), alongX)) {
            low++;
        }
        while (high < WIDEST && step(kinds, footway, i, j, high + 1, alongX)) {
            high++;
        }
        int lowLine = alongX ? 0 : 2, highLine = alongX ? 1 : 3;
        // Worked out on the map's own numbers, so that every square of one road gives one centre to the last bit.
        double base = alongX ? windowX + i : windowY + j;
        double least = Double.MAX_VALUE, most = -Double.MAX_VALUE;
        for (int k = -low; k <= high; k++) {
            for (int row = -ALONG; row <= ALONG; row++) {
                int lines = lines(marks, alongX ? i + k : i + row, alongX ? j + row : j + k) >> YELLOW & 0xF;
                for (int line = lowLine; line <= highLine; line++) {
                    if ((lines & 1 << line) != 0) {
                        least = Math.min(least, base + k + INTO[line]);
                        most = Math.max(most, base + k + INTO[line]);
                    }
                }
            }
        }
        if (least > most || most - least > ONE_CENTRE) {
            return false;
        }
        double centre = (least + most) / 2.0;
        if (base + 0.5 == centre) {
            return false;
        }
        // Each side's white lines, as how far they are from the centre: the low side's from sides[0], the high side's from sides[SIDE_LINES].
        int lowCount = 0, highCount = 0;
        for (int k = -low; k <= high; k++) {
            for (int row = -ALONG; row <= ALONG; row++) {
                int lines = lines(marks, alongX ? i + k : i + row, alongX ? j + row : j + k) >> WHITE & 0xF;
                for (int line = lowLine; line <= highLine; line++) {
                    double place = base + k + INTO[line];
                    if ((lines & 1 << line) == 0) {
                        continue;
                    }
                    if (place < centre) {
                        sides[lowCount++] = centre - place;
                    } else {
                        sides[SIDE_LINES + highCount++] = place - centre;
                    }
                }
            }
        }
        lowCount = bounds(sides, 0, lowCount);
        highCount = bounds(sides, SIDE_LINES, highCount);
        double lowKerb = low < WIDEST ? centre - (base - low) : Double.POSITIVE_INFINITY;
        double highKerb = high < WIDEST ? base + high + 1.0 - centre : Double.POSITIVE_INFINITY;
        double lowLast = lowCount == 0 ? 0.0 : sides[lowCount - 1], highLast = highCount == 0 ? 0.0 : sides[SIDE_LINES + highCount - 1];
        // A side ends at its kerb, unless tarmac goes on past its last line further than a lane is wide: that is a lot
        // beside the road, and the road ends at that line, or with no line on that side as far out as the other side
        // does. Kerbs that far out on both sides are a wide road's.
        boolean lowOpen = lowKerb - lowLast > WIDEST_LANE, highOpen = highKerb - highLast > WIDEST_LANE;
        double lowOuter = lowOpen && lowCount > 0 ? lowLast : lowKerb, highOuter = highOpen && highCount > 0 ? highLast : highKerb;
        boolean lowMirrored = lowOpen && lowCount == 0, highMirrored = highOpen && highCount == 0;
        if (lowMirrored && highMirrored) {
            if (Double.isInfinite(lowKerb) || Double.isInfinite(highKerb)) {
                return false;
            }
        } else if (lowMirrored) {
            lowOuter = highOuter;
        } else if (highMirrored) {
            highOuter = lowOuter;
        }
        // Too narrow a first lane is no lane: one carriageway of a divided road, with its yellow line along its edge.
        if ((lowCount > 0 ? sides[0] : lowOuter) < NARROWEST || (highCount > 0 ? sides[SIDE_LINES] : highOuter) < NARROWEST) {
            return false;
        }
        boolean onHigh = base + 0.5 > centre;
        double from = Math.abs(base + 0.5 - centre), outer = onHigh ? highOuter : lowOuter;
        if (from > outer) {
            return false;
        }
        int first = onHigh ? SIDE_LINES : 0, count = onHigh ? highCount : lowCount;
        double inner = 0.0, next = outer;
        for (int b = first; b < first + count; b++) {
            if (sides[b] > from) {
                next = sides[b];
                break;
            }
            inner = sides[b];
        }
        into[at + CENTRE] = (float) centre;
        into[at + INNER] = (float) inner;
        into[at + WIDTH] = (float) (next - inner);
        into[at + LOW] = (float) (centre - lowOuter);
        into[at + HIGH] = (float) (centre + highOuter);
        return true;
    }

    /**
     * Sorts the count distances of white lines from start of sides, and keeps those that end a
     * lane: a line nearer than NARROWEST to the one before it, or to the centre, is part of that
     * one. Returns how many are kept, from start.
     */
    private static int bounds(double[] sides, int start, int count) {
        Arrays.sort(sides, start, start + count);
        int kept = 0;
        double before = 0.0;
        for (int b = start; b < start + count; b++) {
            if (sides[b] - before >= NARROWEST) {
                sides[start + kept++] = sides[b];
                before = sides[b];
            }
        }
        return kept;
    }

    /** The lines of square (i, j) of the window, none off its edge. */
    private static int lines(byte[] marks, int i, int j) {
        return i < 0 || j < 0 || i >= Kerbs.SPAN || j >= Kerbs.SPAN ? 0 : marks[i * Kerbs.SPAN + j] & 0xFF;
    }

    /** Whether the square that far across the road from (i, j) is still its tarmac. */
    private static boolean step(byte[] kinds, boolean[] footway, int i, int j, int by, boolean alongX) {
        return alongX ? tarmac(kinds, footway, i + by, j) : tarmac(kinds, footway, i, j + by);
    }
}

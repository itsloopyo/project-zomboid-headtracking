package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * Which made thing stands over each square of the map, so that Terrain can give the whole of
 * it one level footing. The land rolls, a road rolls with it and what grows stands on it.
 * What is built does not: a building, the row it shares walls with, the stair up its side,
 * the roof that overhangs its walls, the awning and the balcony and the canopy on posts are
 * each one body, plumb and level, and the ground is cut level under all of it.
 *
 * A square is under something built when a building's room is on it, a stair stands on it,
 * or anything is built over it on a level above. Built squares that touch, by a side or by a
 * corner, are one thing. One with a building in it stands at that building's footing, and
 * where several buildings are joined, at the footing of one of them, the same whichever
 * square asks. One with no building in it, a canopy or a flight to nowhere, stands at the
 * height the land has under the middle of it.
 *
 * Logic thread.
 */
final class Footings {
    /** What is over a square: nothing built, something built that is no room of a building, a building, or a square the map has not loaded. */
    static final byte OPEN = 0, BUILT = 1, HOUSE = 2, UNLOADED = 3;

    /** The map, as far as a footing goes by it. */
    interface Site {
        byte over(int x, int y);

        /** The middle of the building over a HOUSE square, as key packs it. */
        long house(int x, int y);
    }

    /**
     * No more squares than this are one thing. Something larger, a mall of several buildings
     * or a viaduct, is not looked over whole: each building in it keeps its own footing, what
     * is joined to a building takes the nearest one's, and what has no building follows the
     * land as a road does.
     */
    static final int MOST = 8192;

    /** The sheet of the game's poles and overhead wires, which are hung on squares of air and are no part of anything built there. */
    static final String OVERHEAD_LINES = "appliances_com";

    /** No footing: the land's own height. */
    static final long NONE = key(Float.NaN, Float.NaN);

    /** The thing found over the square last asked about: its middle as key packs it, or NONE. */
    long found;
    /** Whether it has no building in it, and so stands at the land's height and not at a building's. */
    boolean free;
    /** Whether all of it was on loaded map: one that was not may be another thing when more is loaded. */
    boolean settled;
    /** Whether all of it was looked over: then every square of it, the first count pairs of squares, has the same answer. */
    boolean whole;
    int count;
    int[] squares = new int[512];

    /**
     * What each square asked about in the search in hand is, by the square as square packs
     * it: the search asks about each of a square's eight neighbours, and telling what is over
     * a square is a walk up through its levels. A place holds for the search whose number it
     * has, so the table is never cleared. A thing of MOST squares has eight times as many
     * round it at the very most, and the table is never full.
     */
    private static final int PLACES = 1 << 17;
    private final long[] asked = new long[PLACES];
    private final int[] askedIn = new int[PLACES];
    private final byte[] answered = new byte[PLACES];
    private int search;
    private static final byte QUEUED = 8;
    /** The buildings found in things too large to look over, which answer for their own squares without another search. */
    private final java.util.HashSet<Long> large = new java.util.HashSet<>();

    /** For a map loaded anew. */
    void forget() {
        large.clear();
    }

    static long key(float x, float y) {
        return (long) Float.floatToRawIntBits(x) << 32 | Float.floatToRawIntBits(y) & 0xFFFFFFFFL;
    }

    static float keyX(long key) {
        return Float.intBitsToFloat((int) (key >> 32));
    }

    static float keyY(long key) {
        return Float.intBitsToFloat((int) key);
    }

    /** Finds the thing that stands over (x, y) and leaves it in the fields. */
    void find(Site site, int x, int y) {
        count = 0;
        free = false;
        settled = true;
        whole = true;
        found = NONE;
        search++;
        byte start = answered[place(site, x, y)];
        if (start == UNLOADED) {
            settled = false;
            return;
        }
        if (start == OPEN) {
            return;
        }
        if (start == HOUSE && large.contains(site.house(x, y))) {
            whole = false;
            found = site.house(x, y);
            return;
        }
        answered[place(site, x, y)] |= QUEUED;
        push(x, y);
        long least = NONE, nearest = NONE;
        boolean housed = false;
        int lowX = x, highX = x, lowY = y, highY = y;
        for (int next = 0; next < count && whole; next++) {
            int atX = squares[next * 2], atY = squares[next * 2 + 1];
            if ((answered[place(site, atX, atY)] & ~QUEUED) == HOUSE) {
                long house = site.house(atX, atY);
                if (!housed) {
                    nearest = house;
                }
                least = housed ? Math.min(least, house) : house;
                housed = true;
            }
            lowX = Math.min(lowX, atX);
            highX = Math.max(highX, atX);
            lowY = Math.min(lowY, atY);
            highY = Math.max(highY, atY);
            for (int side = 0; side < 8 && whole; side++) {
                int toX = atX + STEP_X[side], toY = atY + STEP_Y[side];
                int at = place(site, toX, toY);
                byte over = answered[at];
                if (over == UNLOADED) {
                    settled = false;
                }
                if (over != BUILT && over != HOUSE) {
                    continue;
                }
                whole = count < MOST;
                if (whole) {
                    answered[at] |= QUEUED;
                    push(toX, toY);
                }
            }
        }
        if (!whole) {
            found = start == HOUSE ? site.house(x, y) : nearest;
            if (start == HOUSE && settled) {
                large.add(found);
            }
            return;
        }
        free = !housed;
        found = housed ? least : key((lowX + highX + 1) / 2f, (lowY + highY + 1) / 2f);
    }

    private static final int[] STEP_X = {1, -1, 0, 0, 1, 1, -1, -1}, STEP_Y = {0, 0, 1, -1, 1, -1, 1, -1};

    /** Where in the table the square is, asking the site about it if this search has not yet. */
    private int place(Site site, int x, int y) {
        long square = (long) x << 32 | y & 0xFFFFFFFFL;
        int at = (int) (square * 0x9E3779B97F4A7C15L >>> 47) & PLACES - 1;
        while (askedIn[at] == search) {
            if (asked[at] == square) {
                return at;
            }
            at = at + 1 & PLACES - 1;
        }
        asked[at] = square;
        askedIn[at] = search;
        answered[at] = site.over(x, y);
        return at;
    }

    private void push(int x, int y) {
        if (count * 2 == squares.length) {
            squares = Arrays.copyOf(squares, squares.length * 2);
        }
        squares[count * 2] = x;
        squares[count * 2 + 1] = y;
        count++;
    }
}

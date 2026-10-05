package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * What the two sides of a wall show.
 *
 * The game has one sprite for a wall, painted for the side its own view sees: the south face
 * of a wall on a square's north edge, the east face of one on its west edge. So the walls on
 * the north and west of a house carry the wallpaper of the room behind them, and those on its
 * south and east, which stand on the squares just outside it, carry its siding. The skin of a
 * wall on the side its sprite was painted for shows the sprite. The other skin keeps the
 * sprite's outline, its windows and doors, and is painted over in the colour of the surface
 * that side has: out of doors the finish of the whole building, which is the sprite most of
 * its south and east walls show, and indoors the sprite most of that room's own walls show.
 */
final class WallSides {
    /** How a skin is painted over: plain paint, or boards lapped one over the next. */
    static final int FLAT = 1, SIDING = 2;

    private static final int LEVELS = 15;
    private static final int BANDS = 32;
    private static final int PAINTED = 128;
    private static final float RED = 0.299f, GREEN = 0.587f, BLUE = 0.114f;

    private WallSides() {}

    /** What each of a set of walls shows, as numbers, and which number most of them agree on. */
    static final class Votes {
        /** Each a place in a list, so none is negative. */
        private int[] cast = new int[16];
        private int count;

        void add(int vote) {
            if (count == cast.length) {
                cast = Arrays.copyOf(cast, count * 2);
            }
            cast[count++] = vote;
        }

        boolean isEmpty() {
            return count == 0;
        }

        /** The vote cast most often, and of two cast equally often the one cast first. -1 when none was cast. */
        int commonest() {
            int highest = -1;
            for (int i = 0; i < count; i++) {
                highest = Math.max(highest, cast[i]);
            }
            int[] times = new int[highest + 1];
            for (int i = 0; i < count; i++) {
                times[cast[i]]++;
            }
            int best = -1, most = 0;
            for (int i = 0; i < count; i++) {
                if (times[cast[i]] > most) {
                    most = times[cast[i]];
                    best = cast[i];
                }
            }
            return best;
        }
    }

    /**
     * A colour to paint a skin over in, each part from 0 to 1, and how it is painted, FLAT or
     * SIDING, as one number that is never 0.
     *
     * Every different number is one more texture change and one more draw each frame, since
     * the colour is told to the picture once for all the triangles that share it. Rounded to
     * sixteen steps a channel, the walls of a street come to a few dozen colours and not to
     * one for every sprite. A house's far skins all take their colour from the same sprite, so
     * the rounding is the same along a whole wall and never shows as a step.
     */
    static int key(float red, float green, float blue, int how) {
        return level(red) << 12 | level(green) << 8 | level(blue) << 4 | how;
    }

    private static int level(float value) {
        return Math.round(Math.clamp(value, 0f, 1f) * LEVELS);
    }

    /**
     * What the picture is told for a range of triangles: into out the colour, then FLAT or
     * SIDING. All 0 for a key of 0, which is no colour.
     */
    static void tint(int key, float[] out) {
        if (key == 0) {
            out[0] = out[1] = out[2] = out[3] = 0f;
            return;
        }
        out[0] = (key >> 12 & 15) / (float) LEVELS;
        out[1] = (key >> 8 & 15) / (float) LEVELS;
        out[2] = (key >> 4 & 15) / (float) LEVELS;
        out[3] = key & 15;
    }

    /**
     * The colour most of a sprite is painted in: into out its red, green and blue and its
     * brightness, each from 0 to 1. The sprite is the rectangle from (left, top) to before
     * (right, bottom) of a picture width pixels wide, held as bytes of red, green, blue and
     * cover. False for a rectangle with nothing painted in it.
     *
     * Not the mean of all of it: a wall with a window in it is part frame and part glass, and
     * boards have dark lines between them. The painted pixels are sorted by brightness into
     * bands, and the colour is the mean of the fullest band and the one either side of it.
     */
    static boolean usualColour(ByteBuffer pixels, int width, int left, int top, int right, int bottom, float[] out) {
        int[] bands = new int[BANDS];
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int at = (y * width + x) * 4;
                if ((pixels.get(at + 3) & 0xff) >= PAINTED) {
                    bands[band(pixels, at)]++;
                }
            }
        }
        int fullest = 0;
        for (int band = 1; band < BANDS; band++) {
            if (bands[band] > bands[fullest]) {
                fullest = band;
            }
        }
        if (bands[fullest] == 0) {
            return false;
        }
        long red = 0, green = 0, blue = 0;
        int count = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int at = (y * width + x) * 4;
                if ((pixels.get(at + 3) & 0xff) >= PAINTED && Math.abs(band(pixels, at) - fullest) <= 1) {
                    red += pixels.get(at) & 0xff;
                    green += pixels.get(at + 1) & 0xff;
                    blue += pixels.get(at + 2) & 0xff;
                    count++;
                }
            }
        }
        out[0] = red / (255f * count);
        out[1] = green / (255f * count);
        out[2] = blue / (255f * count);
        out[3] = RED * out[0] + GREEN * out[1] + BLUE * out[2];
        return true;
    }

    private static int band(ByteBuffer pixels, int at) {
        float bright = RED * (pixels.get(at) & 0xff) + GREEN * (pixels.get(at + 1) & 0xff) + BLUE * (pixels.get(at + 2) & 0xff);
        return Math.min((int) (bright * BANDS / 256f), BANDS - 1);
    }
}

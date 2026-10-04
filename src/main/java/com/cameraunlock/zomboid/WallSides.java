package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;

/**
 * What the two sides of a wall show.
 *
 * The game has one sprite for a wall, painted for the side its own view sees: the south face
 * of a wall on a square's north edge, the east face of one on its west edge. So the walls on
 * the north and west of a house carry the wallpaper of the room behind them, and those on its
 * south and east, which stand on the squares just outside it, carry its siding. The skin of a
 * wall on the side its sprite was painted for shows the sprite. The other skin keeps the
 * sprite's outline and relief and is painted over in the colour of the surface that side has,
 * which is found by walking to the wall that does show it.
 */
final class WallSides {
    /** A square the walk came to is loaded, is under a roof, has a wall on the edge the walk asks about. */
    static final int LOADED = 1, INDOORS = 2, WALL = 4;
    /** The walk found no wall showing the other side's surface. */
    static final int NOTHING = -1;
    /** The walk ran into map that is not loaded, and may find a wall once it is. */
    static final int UNKNOWN = -2;
    /** The furthest a walk goes, in squares: across the widest building it is worth being right about. */
    static final int FURTHEST = 30;

    /** The squares a walk passes, by how many steps along it they are. */
    interface Squares {
        /** LOADED, INDOORS and WALL, added together, for the square that many steps along. */
        int at(int step);
    }

    private static final int LEVELS = 15;
    private static final int BANDS = 32;
    private static final int PAINTED = 128;
    private static final float RED = 0.299f, GREEN = 0.587f, BLUE = 0.114f;

    private WallSides() {}

    /**
     * From an indoor square away from its wall, through the building, to the first square out
     * of doors: step 1 is the square next to the wall's own. The wall on that square's near
     * edge is the building's far outside wall, and its sprite is the building's siding.
     * Returns the step of that square, or NOTHING or UNKNOWN.
     */
    static int toOutsideWall(Squares squares) {
        for (int step = 1; step <= FURTHEST; step++) {
            int square = squares.at(step);
            if ((square & LOADED) == 0) {
                return UNKNOWN;
            }
            if ((square & INDOORS) == 0) {
                return (square & WALL) != 0 ? step : NOTHING;
            }
        }
        return NOTHING;
    }

    /**
     * From the indoor square beyond a wall on through its room to the first square with a wall
     * on its far edge: step 0 is the square beyond the wall. That wall's sprite is the room's
     * wallpaper. Returns the step of that square, or NOTHING or UNKNOWN.
     */
    static int toRoomWall(Squares squares) {
        for (int step = 0; step < FURTHEST; step++) {
            int square = squares.at(step);
            if ((square & LOADED) == 0) {
                return UNKNOWN;
            }
            if ((square & INDOORS) == 0) {
                return NOTHING;
            }
            if ((square & WALL) != 0) {
                return step;
            }
        }
        return NOTHING;
    }

    /**
     * A colour to paint a skin over in and the usual brightness of the sprite under it, each
     * from 0 to 1, as one number that is never 0.
     *
     * Every different number is one more texture change and one more draw each frame, since
     * the colour is told to the picture once for all the triangles that share it. Rounded to
     * sixteen steps a channel, the walls of a street come to a few dozen colours and not to
     * one for every pair of sprites. A house's far skins all take their colour from the same
     * sprite, so the rounding is the same along a whole wall and never shows as a step.
     */
    static int key(float red, float green, float blue, float usual) {
        return level(red) << 12 | level(green) << 8 | level(blue) << 4 | Math.max(level(usual), 1);
    }

    private static int level(float value) {
        return Math.round(Math.clamp(value, 0f, 1f) * LEVELS);
    }

    /**
     * What the picture is told for a range of triangles: into out the colour, then 1 over the
     * sprite's usual brightness. All 0 for a key of 0, which is no colour.
     */
    static void tint(int key, float[] out) {
        if (key == 0) {
            out[0] = out[1] = out[2] = out[3] = 0f;
            return;
        }
        out[0] = (key >> 12 & 15) / (float) LEVELS;
        out[1] = (key >> 8 & 15) / (float) LEVELS;
        out[2] = (key >> 4 & 15) / (float) LEVELS;
        out[3] = LEVELS / (float) (key & 15);
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

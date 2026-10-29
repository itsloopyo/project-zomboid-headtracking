package com.cameraunlock.zomboid;

/**
 * The wires strung between the big pylons. The game paints them a square at a time, each
 * sprite one straight level wire across its square: along x, along y, or along one of the
 * two diagonals. Stood up as a card a run of them was a row of dashes turned to the eye.
 *
 * Two of the sprites have a red marker ball on the wire as well, and the ball is a model.
 *
 * Where each wire crosses its square and how high it is were read off the sprites. A wire
 * along a diagonal is painted the whole length of the square's diagonal, so a run of them
 * meets end to end on squares that touch at their corners.
 */
final class PylonWires {
    private PylonWires() {}

    private static final String SHEET = "electricity_pylon_";
    /** Per sprite from 160: where on the square the wire starts and ends, along x and along y in tiles, and how high it is, in levels. */
    private static final float[][] SPANS = {
        {0f, 1f, 1f, 0f, 0.326f},
        {0f, 1f, 1f, 0f, 0.211f},
        {0.477f, 0f, 0.477f, 1f, 0.588f},
        {0.383f, 0f, 0.383f, 1f, 0f},
        {0f, 0.539f, 1f, 0.539f, 0.599f},
        {0f, 0.492f, 1f, 0.492f, 0.006f},
        {0.12f, -0.12f, 1.12f, 0.88f, 0.03f},
        {-0.16f, 0.16f, 0.84f, 1.16f, 0.03f},
    };

    /** The two wires that carry a red marker ball, 170 and 171 on the sheet: the same as 162 and 163. */
    private static final int MARKED = 170, MARKED_AS = 162;
    /** How far along its wire each of those balls is, from the wire's start, as a share of the wire. */
    private static final float[] BALLS = {0.563f, 0.531f};

    /** The wire a sprite paints, as {x0, y0, x1, y1, high}, or null for a sprite that is none. */
    static float[] of(String name) {
        int place = place(name);
        if (place == MARKED || place == MARKED + 1) {
            return SPANS[place - MARKED + MARKED_AS - 160];
        }
        return place >= 160 && place < 160 + SPANS.length ? SPANS[place - 160] : null;
    }

    /** How far along its wire the sprite paints a marker ball, as a share of the wire, or NaN for a wire with none. */
    static float ball(String name) {
        int place = place(name);
        return place == MARKED || place == MARKED + 1 ? BALLS[place - MARKED] : Float.NaN;
    }

    /**
     * Which sprite of the sheet a name is, or -1. A sprite the game makes from a picture of
     * its own has no name: a trap whose item gives no sprite to be placed as, as a noise
     * trap's does not (IsoTrap.initSprite), or a thing a mod puts down from a texture's path.
     */
    private static int place(String name) {
        return name == null ? -1 : Sheets.place(name, SHEET);
    }
}

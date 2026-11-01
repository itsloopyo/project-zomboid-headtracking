package com.cameraunlock.zomboid;

/**
 * The arms of the traffic signals that reach out over a crossing from a lamp post, the game's
 * lighting_outdoor_01_58 to 87. A post stands on one square with its lamp on the level above, and
 * its arm is five more sprites on that level, one a square, the second and the fourth with a
 * signal hung on it. Stood up as cards turned to the eye they were loose black pieces in the air.
 *
 * Each piece builds the length of the arm over its own square, and its signal, from where the
 * post stands: the arm leaves the post low, climbs over the first two squares and runs level to
 * its tip. The heights and places were read off the sprites, and every one of the four arms on
 * the sheet reads the same within a hundredth of a level.
 */
final class MastArms {
    private MastArms() {}

    private static final String SHEET = "lighting_outdoor_01_";

    /** Where on its square the post the arm leaves stands, as lighting_outdoor_01_55 paints its axis. */
    static final float POST_A = 0.5f, POST_B = 0.55f;
    /** How high the arm's middle line is over the level its pieces are on, in levels, every KNOT tiles out from the post's axis. Past the last it runs level. */
    private static final float[] HIGH = {0.565f, 0.64f, 0.715f, 0.79f, 0.835f, 0.842f};
    static final float KNOT = 0.5f;
    /** How thick the arm is: half its side, in tiles. */
    static final float ARM_HALF = 0.04f;
    /** How far from the post's axis each of the two signals hangs, in tiles: on the second and the fourth piece out. */
    private static final float[] SIGNAL_OUT = {2.15f, 4.1f};
    /** A signal's housing: half its side, in tiles, how far in front of the arm its middle is, toward its face, and its foot and top over the level, in levels. */
    static final float HOUSING_HALF = 0.09f, HOUSING_PROUD = 0.09f, HOUSING_FOOT = 0.655f, HOUSING_TOP = 0.975f;
    /** Its three lenses, top to bottom: half their side and how far each stands out of the housing's face, in tiles. */
    static final float LENS_HALF = 0.08f, LENS_PROUD = 0.02f;

    /**
     * The piece of an arm a sprite is, or null for a sprite that is none: the way the arm runs
     * out from its post, along x or along y, and how many squares out from the post the piece is.
     */
    record Piece(int alongX, int alongY, int out) {
        /** Where on the arm this piece's length starts and ends, in tiles from the post's axis. The first runs from the axis itself. */
        float from() {
            return out == 1 ? 0f : out - 0.5f;
        }

        float to() {
            return out + 0.5f;
        }

        /** How far from the post's axis this piece's signal hangs, or NaN for a piece with none. */
        float signal() {
            return out == 2 ? SIGNAL_OUT[0] : out == 4 ? SIGNAL_OUT[1] : Float.NaN;
        }

        /** The way the signal's face looks, along x and along y: a quarter turn from the arm, toward the crossing. */
        int facesX() {
            return alongY;
        }

        int facesY() {
            return -alongX;
        }
    }

    static Piece of(String name) {
        int place = name == null ? -1 : Sheets.place(name, SHEET);
        if (place >= 58 && place <= 62) {
            return new Piece(-1, 0, 63 - place);
        }
        if (place >= 65 && place <= 69) {
            return new Piece(0, -1, place - 64);
        }
        if (place >= 73 && place <= 77) {
            return new Piece(1, 0, place - 72);
        }
        if (place >= 82 && place <= 86) {
            return new Piece(0, 1, 87 - place);
        }
        return null;
    }

    /** How high the arm's middle line is, in levels over its pieces' level, out from the post's axis. */
    static float high(float out) {
        float knot = out / KNOT;
        int below = (int) Math.floor(knot);
        if (below >= HIGH.length - 1) {
            return HIGH[HIGH.length - 1];
        }
        float share = knot - below;
        return HIGH[below] + (HIGH[below + 1] - HIGH[below]) * share;
    }
}

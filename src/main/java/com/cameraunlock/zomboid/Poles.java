package com.cameraunlock.zomboid;

/**
 * The poles that carry overhead wires.
 *
 * The game stands one as a sprite on each level of its square: a length of bare shaft on the
 * ground and on the level above, and on the third the top of the shaft with its cross-arm,
 * drawn for one of the four ways an arm can lie on the grid. They are sprites 88 to 95 of the
 * sheet the wires are on. A length read by itself is a post of its own thickness and colour
 * a hand off the one under it, and an arm kept as the top of its sprite is a card that turns
 * to the eye and never lies under the wires it carries. So a pole is built whole from its
 * foot: one shaft, and on it the arm its top sprite shows, where that sprite's wires cross it.
 */
final class Poles {
    /** The sheet poles and wires are on. */
    private static final String SHEET = "appliances_com_01_";
    private static final int FIRST = 88, LAST = 95, FIRST_ARM = 90, LAST_ARM = 93;

    /** How high a bare length of shaft stands over the floor of its level, and the top of a shaft that has an arm, in levels. */
    static final float LENGTH = 1f, TOP = 0.46f;
    /** The top of a shaft is this much of the thickness of its foot. */
    static final float TAPER = 0.8f;
    /** The arm: how high its middle is over the floor of its level, in levels, and half its thickness, in tiles. It is bolted to the side of the shaft the game's view is on. */
    static final float ARM_UP = 0.385f, ARM_HALF = 0.055f;
    /** The two braces under an arm: how far along each half of it one meets it, as a share, how high on the shaft it starts, in levels, and half its thickness, in tiles. */
    static final float BRACE_AT = 0.45f, BRACE_UP = 0.24f, BRACE_HALF = 0.022f;
    /** An insulator: its radius and the radius of the pin it stands on, in tiles, and how much of the way from the arm up to the wire is pin. */
    static final float INSULATOR = 0.045f, PIN = 0.014f, PIN_SHARE = 0.32f;
    /** What an insulator is made of: glazed stoneware. */
    static final float INSULATOR_RED = 0.8f, INSULATOR_GREEN = 0.81f, INSULATOR_BLUE = 0.78f;

    /**
     * An arm as its sprite shows it. (alongX, alongY) is the way it lies, of length one, picked
     * of its two ways so that the game's view is on the side (-alongY, alongX) of it. half is
     * half its length. outer is how far either side of the shaft's axis its outer two wires
     * cross it and middle where the third does, all in tiles along the arm: where Wires puts
     * the lines of the wire sprites that run from such a pole.
     */
    record Arm(float alongX, float alongY, float half, float outer, float middle) {}

    private static final float DIAGONAL = (float) Math.sqrt(0.5);
    private static final Arm[] ARMS = {
        new Arm(DIAGONAL, -DIAGONAL, 0.65f, 0.56f, 0f),
        new Arm(-DIAGONAL, -DIAGONAL, 0.65f, 0.57f, 0.21f),
        new Arm(0f, -1f, 0.93f, 0.8f, 0.16f),
        new Arm(1f, 0f, 0.93f, 0.8f, -0.16f),
    };

    /** The end of a wire this near an insulator, in tiles, is made fast to it. */
    static final float MADE_FAST = 0.35f;

    private Poles() {}

    /**
     * Where the insulator of one of an arm's three wires stands, into two places of out: which
     * is -1 or 1 for an outer wire and 0 for the third, and the shaft's top is thin in radius on
     * (axisX, axisY). A wire that passes over the shaft lies on an insulator on its top, for
     * which this is true, and any other on one on a pin in the arm.
     */
    static boolean insulator(Arm arm, int which, float axisX, float axisY, float thin, float[] out) {
        float along = which == 0 ? arm.middle : which * arm.outer;
        if (Math.abs(along) < thin + INSULATOR) {
            out[0] = axisX;
            out[1] = axisY;
            return true;
        }
        float off = thin + ARM_HALF;
        out[0] = axisX - arm.alongY * off + arm.alongX * along;
        out[1] = axisY + arm.alongX * off + arm.alongY * along;
        return false;
    }

    /** Which sprite of the sheet a name is, or -1 for one of another sheet. */
    static int index(String name) {
        return name == null ? -1 : Sheets.place(name, SHEET);
    }

    /** Whether a sprite is a length of a pole, with an arm or without. */
    static boolean length(String name) {
        int index = index(name);
        return index >= FIRST && index <= LAST;
    }

    /** The arm a sprite shows on its length of pole, or null for a bare length and for anything else. */
    static Arm arm(String name) {
        int index = index(name);
        return index >= FIRST_ARM && index <= LAST_ARM ? ARMS[index - FIRST_ARM] : null;
    }
}

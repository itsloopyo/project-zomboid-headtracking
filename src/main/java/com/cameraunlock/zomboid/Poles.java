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

    /** The map as a pole's lengths are found on it: the sprite of the length of pole drawn on a square of a level, or null. */
    interface Drawn {
        String on(int x, int y, int z);
    }

    /**
     * Whether the length of pole drawn on a square stands a level lower and Spans.LEVEL_SQUARES
     * back along x and y, where the game's view draws it on the same place: on the top of a
     * pole there, with nothing of its own under it, or on a length of its own square that is
     * itself drawn so. The map's artists put some poles' tops so. No level is lower than lowest.
     */
    static boolean drawnUp(Drawn map, int x, int y, int z, int lowest) {
        int back = Spans.LEVEL_SQUARES;
        if (z - 2 < lowest || map.on(x, y, z) == null || map.on(x - back, y - back, z - 1) != null) {
            return false;
        }
        return map.on(x, y, z - 1) == null ? map.on(x - back, y - back, z - 2) != null : drawnUp(map, x, y, z - 1, lowest);
    }

    /**
     * Whether the bare length of pole drawn on a square stands one square on along x and y, under
     * the rest of its pole: the length over it is drawn there, a level up, and nothing over it
     * or beside it, or the length over it on its own square is set back so. The game's view
     * draws the two overlapping, as one pole, and the wires run from the upper, so the pole
     * stands where the upper is.
     */
    static boolean setBack(Drawn map, int x, int y, int z) {
        String here = map.on(x, y, z);
        if (here == null || arm(here) != null || map.on(x + 1, y + 1, z) != null) {
            return false;
        }
        return map.on(x, y, z + 1) == null ? map.on(x + 1, y + 1, z + 1) != null : setBack(map, x, y, z + 1);
    }

    /**
     * Where the length of pole that stands on a square of a level is drawn: SAME on the square
     * itself, UP for a level up and Spans.LEVEL_SQUARES on along x and y, BACK for one square
     * back along x and y, or NONE.
     */
    static int drawnFrom(Drawn map, int x, int y, int z, int lowest) {
        int on = Spans.LEVEL_SQUARES;
        if (drawnUp(map, x + on, y + on, z + 1, lowest)) {
            return UP;
        }
        if (setBack(map, x - 1, y - 1, z)) {
            return BACK;
        }
        return map.on(x, y, z) == null || drawnUp(map, x, y, z, lowest) || setBack(map, x, y, z) ? NONE : SAME;
    }

    static final int NONE = -1, SAME = 0, UP = 1, BACK = 2;

    /** Which squares of the map have a floor. */
    interface Floored {
        boolean at(int x, int y, int z);
    }

    /**
     * The level a pole whose lowest length is on a square of a level stands on: the length's
     * own, or where the length stands in the air, as some of the map's poles are drawn whole a
     * level up, the first level under it with a floor, and the ground's at the lowest.
     */
    static int footLevel(Floored floored, int x, int y, int z) {
        while (z > 0 && !floored.at(x, y, z)) {
            z--;
        }
        return z;
    }

    /** The map as far as it is loaded: whether the chunk a square is in is. */
    interface Loaded {
        boolean at(int x, int y);
    }

    /**
     * Whether what drawnUp, setBack and standsOn say of a square of a level may change once
     * more of the map is loaded, the square's own chunk being loaded: the lowest of the lengths
     * on the square may be drawn up from a pole Spans.LEVEL_SQUARES back, or stand on a length
     * set back from one square back; a bare length under no arm on its square may be set
     * back under a length one square on; and an empty square may have a length over it set
     * back from one square back, or the top of a length under it drawn up Spans.LEVEL_SQUARES on,
     * each on map not loaded.
     */
    static boolean unsettled(Drawn map, Loaded loaded, int x, int y, int z, int lowest) {
        int other = Spans.LEVEL_SQUARES;
        String here = map.on(x, y, z);
        boolean under = z - 1 >= lowest && map.on(x, y, z - 1) != null;
        if (here == null) {
            boolean over = false;
            for (int up = 1; up <= POLE_LEVELS; up++) {
                over |= map.on(x, y, z + up) != null;
            }
            return under && !loaded.at(x + other, y + other) || over && !loaded.at(x - 1, y - 1);
        }
        int bottom = z, top = z;
        while (bottom - 1 >= lowest && map.on(x, y, bottom - 1) != null) {
            bottom--;
        }
        while (map.on(x, y, top + 1) != null) {
            top++;
        }
        return bottom - 2 >= lowest && !loaded.at(x - other, y - other) || !under && z - 1 >= lowest && !loaded.at(x - 1, y - 1)
                || arm(map.on(x, y, top)) == null && !loaded.at(x + 1, y + 1);
    }

    /** The most levels a pole of the map stands through. */
    private static final int POLE_LEVELS = 3;

    /**
     * Whether what a pole's build reads from a length of it on a square of a level may change once more of the map is
     * loaded: the length itself, the square over it, and over the top the build climbs to from it, where a top drawn
     * up onto map not loaded would make the pole a length taller.
     */
    static boolean unsettledPole(Drawn map, Loaded loaded, int x, int y, int z, int lowest) {
        int top = z;
        while (standsOn(map, x, y, top + 1, lowest) != null) {
            top++;
        }
        return unsettled(map, loaded, x, y, z, lowest) || unsettled(map, loaded, x, y, z + 1, lowest) || unsettled(map, loaded, x, y, top + 1, lowest);
    }

    /** The sprite of the length of pole that stands on a square of a level, wherever it is drawn, or null. */
    static String standsOn(Drawn map, int x, int y, int z, int lowest) {
        int on = Spans.LEVEL_SQUARES;
        return switch (drawnFrom(map, x, y, z, lowest)) {
            case UP -> map.on(x + on, y + on, z + 1);
            case BACK -> map.on(x - 1, y - 1, z);
            case SAME -> map.on(x, y, z);
            default -> null;
        };
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

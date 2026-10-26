package com.cameraunlock.zomboid;

/**
 * The shape of a flight of stairs.
 *
 * The map has a flight as three squares, each a third of a level higher than the one before,
 * and the game walks a character up it as up a ramp. Every stair sprite the game has paints
 * the same flight on those squares: twelve steps, four to a square, the first riser at the
 * foot of the lowest square and the last tread level with the floor above. Laid as a ramp
 * with the sprite on it, the steps were a picture of steps on a board. Here is where the
 * treads and risers are, so that each is built where its sprite paints it.
 *
 * Along a flight is measured in squares from its foot, 0 to 3, and heights in levels above
 * the floor the flight stands on.
 */
final class Stairs {
    /** Steps to a square. */
    static final int STEPS = 4;
    /** How far a tread runs along the flight, in squares. */
    static final float GOING = 1f / STEPS;
    /** How high a riser is, in levels: three squares of steps climb one level. */
    static final float RISE = 1f / (3 * STEPS);
    /**
     * How far under the line through the backs of the treads the underside of a flight runs,
     * in levels. The sprites paint the side of a flight as a string about a riser deep.
     */
    static final float STRING = RISE;

    private Stairs() {}

    /** The height of a tread: step 0 is the lowest of its square, and third 0 the lowest square of the flight. */
    static float tread(int third, int step) {
        return (third * STEPS + step + 1) * RISE;
    }

    /**
     * The height of the tread at a place along the flight, or of the tread just past it where it
     * stands on a riser: nothing laid at this height is under a step.
     */
    static float over(float along) {
        int step = Math.clamp((int) Math.floor(along * STEPS), 0, 3 * STEPS - 1);
        return (step + 1) * RISE;
    }

    /** The same, and on the floor short of the foot of the flight. */
    static float on(float along) {
        return along <= 0f ? 0f : over(along);
    }

    private static final String SHEET = "fixtures_stairs_01_";
    private static final int[] TIMBER = {16, 17, 18, 19, 20, 21, 24, 25, 26, 27, 28, 29, 32, 33, 34, 40, 41, 42, 64, 65, 66, 72, 73, 74};

    /**
     * Whether a stair sprite paints a closed flight of bare timber treads. Read off the
     * sprites, not their MaterialType: the game gives Wood_Solid to the pale stone flights
     * with dark nosings (48 to 50 and 56 to 58) as well.
     */
    static boolean timber(String sprite) {
        if (sprite == null) {
            return false;
        }
        int index = Sheets.place(sprite, SHEET);
        for (int timber : TIMBER) {
            if (timber == index) {
                return true;
            }
        }
        return false;
    }

    /** The height of the underside of the flight. It meets the floor under the first riser but one. */
    static float underside(float along) {
        return Math.max(0f, along / 3f - STRING);
    }
}

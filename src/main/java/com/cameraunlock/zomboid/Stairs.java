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

    /** The height of the underside of the flight. It meets the floor under the first riser but one. */
    static float underside(float along) {
        return Math.max(0f, along / 3f - STRING);
    }
}

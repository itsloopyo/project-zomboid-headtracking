package com.cameraunlock.zomboid;

/**
 * The wind as what grows feels it. The game's wind is a direction and a strength that it
 * changes when it likes, by any amount. Grass that leant straight along the one by the other
 * jumped whenever either did, and its gusts, which were laid out along the wind's direction
 * over a map thousands of tiles across, were all somewhere else after the smallest turn of it.
 *
 * So the push eases after the game's wind over a few seconds, and the gusts are a pattern of
 * their own that is carried along: where it has got to is kept here and only ever moves on,
 * by how fast the wind is blowing and the way it is blowing at that moment. Nothing the game
 * does to its wind can move a blade further in a frame than the wind itself would.
 */
final class Wind {
    /** How long the push takes to come most of the way to a new wind, in seconds. */
    static final float SETTLES = 3f;
    /**
     * The gusts repeat over this many tiles, and how far they have travelled is kept within
     * this many, so that it never grows past what a float holds finely. The world shader
     * lays its coarsest gusts over 64 tiles and carries its ripples at a quarter of the
     * travel: a whole number of both fits in this.
     */
    static final float TRAVEL_WRAP = 256f;
    /** The clock every blade shivers by starts again after this many seconds, in which each has shivered a whole number of times. */
    static final float FLUTTER_WRAP = 8f;
    /** How fast the gusts cross the ground in tiles a second: in the lightest air, and more for each unit of the push. */
    private static final float TRAVEL_STILL = 0.5f, TRAVEL_PER_PUSH = 1.6f;

    /** The way what grows is pushed, scaled by how hard. */
    float pushX, pushY;
    /** How far the gusts have been carried, in tiles. */
    float travelX, travelY;
    /** Seconds on the clock the blades shiver by. */
    float flutter;
    private boolean blowing;

    /** Moves the wind on by elapsed seconds, in which the game's wind is (towardX, towardY). */
    void blow(float towardX, float towardY, float elapsed) {
        if (!blowing) {
            pushX = towardX;
            pushY = towardY;
            blowing = true;
        }
        float take = 1f - (float) Math.exp(-elapsed / SETTLES);
        pushX += (towardX - pushX) * take;
        pushY += (towardY - pushY) * take;
        float push = (float) Math.sqrt(pushX * pushX + pushY * pushY);
        if (push > 1e-4f) {
            float speed = TRAVEL_STILL + TRAVEL_PER_PUSH * push;
            travelX = wrapped(travelX + pushX / push * speed * elapsed, TRAVEL_WRAP);
            travelY = wrapped(travelY + pushY / push * speed * elapsed, TRAVEL_WRAP);
        }
        flutter = wrapped(flutter + elapsed, FLUTTER_WRAP);
    }

    private static float wrapped(float value, float within) {
        float left = value - within * (float) Math.floor(value / within);
        // A hair under nothing comes out as the whole of within, which is nothing again.
        return left < within ? left : 0f;
    }
}

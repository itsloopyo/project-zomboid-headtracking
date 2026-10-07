package com.cameraunlock.zomboid;

/**
 * The wind as what grows feels it. The game's wind is a direction and a strength that it
 * changes when it likes, by any amount. Grass that leant straight along the one by the other
 * would jump whenever either did, and gusts laid out along the wind's direction over a map
 * thousands of tiles across would all be somewhere else after the smallest turn of it.
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
    static final float FLUTTER_WRAP = 160f;
    /** How fast the gusts cross the ground in tiles a second: in the lightest air, and more for each unit of the push. */
    private static final float TRAVEL_STILL = 0.05f, TRAVEL_PER_PUSH = 0.16f;

    /**
     * How hard what grows is pushed by a wind of the game's intensity, which is 0 in still
     * air and 1 at 120 kilometres an hour. The picture's plants lean as far as they ever do
     * at a push of 1.5. In the light air of most days, 10 kilometres an hour or so, grass
     * only stirs: a blade's tip moves by a few hundredths of its height. A fresh breeze of
     * 40 has it leaning and running in waves, and from 80 up it is laid over.
     */
    static float push(float intensity) {
        return 0.02f + 2f * (float) Math.pow(Math.clamp(intensity, 0f, 1f), 1.2);
    }

    /**
     * How fast the air itself moves in a wind of the game's intensity, in tiles a second:
     * 120 kilometres an hour at 1, which is the game's own scale. Rain and snow are carried
     * along at this, so rain that falls at a dozen tiles a second comes down nearly straight
     * in light air and at half a right angle in a gale, and snow, which falls at one or two,
     * goes by almost level in any wind at all.
     */
    static float speed(float intensity) {
        return 33.3f * Math.clamp(intensity, 0f, 1f);
    }

    /**
     * Rain and snow are drawn in a box of air round the eye this many tiles across, and what
     * falls out of one side comes in at the other: how far the air has carried them is kept
     * within it. The rain and snow shaders have half of it as BOX.
     */
    static final float CARRIED_WRAP = 28f;
    /**
     * The clocks rain and snow fall by start again after this many seconds. A drop falls a
     * whole number of sixteenths of ten tiles a second and a flake a whole number of
     * hundredths of fifteen, the box is twelve tiles high, and in these times each has
     * fallen through it a whole number of times: nothing moves when a clock starts again.
     */
    static final float RAIN_CLOCK = 19.2f, SNOW_CLOCK = 80f;

    /** The way what grows is pushed, scaled by how hard. */
    float pushX, pushY;
    /** How fast the air moves, in tiles a second, eased as the push is. */
    float airX, airY;
    /** How far the air has carried what falls through it, in tiles, and the seconds on the clocks rain and snow fall by. */
    float carriedX, carriedY, rainClock, snowClock;
    /** How far the gusts have been carried, in tiles. */
    float travelX, travelY;
    /** Seconds on the clock the blades shiver by. */
    float flutter;
    private boolean blowing;

    /** Moves the wind on by elapsed seconds, in which the game's wind pushes by (towardX, towardY) and its air moves that way at airSpeed. */
    void blow(float towardX, float towardY, float airSpeed, float elapsed) {
        float toward = (float) Math.sqrt(towardX * towardX + towardY * towardY);
        float airToX = toward > 1e-6f ? towardX / toward * airSpeed : 0f, airToY = toward > 1e-6f ? towardY / toward * airSpeed : 0f;
        if (!blowing) {
            pushX = towardX;
            pushY = towardY;
            airX = airToX;
            airY = airToY;
            blowing = true;
        }
        float take = 1f - (float) Math.exp(-elapsed / SETTLES);
        pushX += (towardX - pushX) * take;
        pushY += (towardY - pushY) * take;
        airX += (airToX - airX) * take;
        airY += (airToY - airY) * take;
        carriedX = wrapped(carriedX + airX * elapsed, CARRIED_WRAP);
        carriedY = wrapped(carriedY + airY * elapsed, CARRIED_WRAP);
        rainClock = wrapped(rainClock + elapsed, RAIN_CLOCK);
        snowClock = wrapped(snowClock + elapsed, SNOW_CLOCK);
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

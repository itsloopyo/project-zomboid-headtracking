package com.cameraunlock.zomboid;

import java.util.Locale;

/**
 * How the change of speed of the vehicle the viewer sits in pulls at the rain on its glass, as
 * GlassRain draws it: water left behind by a vehicle that gathers speed leans to the back of
 * a side window as it runs down, and to the outside of a bend on the screens. A screen leans
 * back over the cabin, so the same pull holds a run on the front screen back while the vehicle
 * gathers speed and hurries it under braking, and the other way about on the back screen.
 *
 * Kept and read by the picture's thread alone.
 */
final class GlassPull {
    /**
     * The change of speed in a second at which a run leans a tile for each tile it falls. The
     * game's speed of a vehicle is taken to be in metres a second, which makes this the pull
     * of the earth. Not measured in the game.
     */
    static final float LEANS_WHOLE = 9.8f;
    /** A run leans no further than this, however hard the vehicle is driven. */
    static final float MOST_LEAN = 0.7f;
    /** How far back a screen leans for each tile it rises: see paneSeen in the cabin's shader. */
    static final float SCREEN_LEANS = 0.75f;
    /** A run on a screen goes no faster than this many times its own pace. It can be held still. */
    static final float MOST_HURRIED = 3f;
    /** The pull follows the change of speed over about this long, in seconds: the game's speed moves in steps. */
    static final float SETTLES = 0.4f;
    /** A frame is counted as no longer than this, in seconds. */
    static final float MOST_A_FRAME = 0.1f;
    /** The picture's clock begins again this often, in seconds: see SkyState.seconds. */
    static final float HOUR = 3600f;

    /** How far a run leans for each tile it falls: to the front of the vehicle on a side window, and to its side on a screen, the side Ride.aheadY, -Ride.aheadX turned about is counted to. */
    float leanAhead, leanAside;
    /** The clocks the runs on the front and the back screen go by, in seconds, beginning again every hour. */
    float front, back;

    private boolean known;
    private float lastSeconds, lastX, lastY, along, across, frontAhead, backAhead;
    private int said;


    /**
     * Once a frame.
     *
     * @param seated  whether the viewer is in a vehicle
     * @param seconds the picture's clock
     * @param goingX  the vehicle's speed on the map
     * @param aheadX  the way the vehicle faces on the map
     */
    void frame(boolean seated, float seconds, float goingX, float goingY, float aheadX, float aheadY) {
        float passed = Math.min(((seconds - lastSeconds) % HOUR + HOUR) % HOUR, MOST_A_FRAME);
        lastSeconds = seconds;
        if (!seated) {
            known = false;
            along = 0f;
            across = 0f;
            frontAhead = 0f;
            backAhead = 0f;
        } else if (!known) {
            known = true;
            lastX = goingX;
            lastY = goingY;
        } else if (passed > 0f) {
            float changeX = (goingX - lastX) / passed, changeY = (goingY - lastY) / passed;
            lastX = goingX;
            lastY = goingY;
            float follows = 1f - (float) Math.exp(-passed / SETTLES);
            along += (changeX * aheadX + changeY * aheadY - along) * follows;
            across += (changeY * aheadX - changeX * aheadY - across) * follows;
        }
        // The water is left behind: it goes the other way from the change of speed.
        leanAhead = Math.clamp(-along / LEANS_WHOLE, -MOST_LEAN, MOST_LEAN);
        leanAside = Math.clamp(-across / LEANS_WHOLE, -MOST_LEAN, MOST_LEAN);
        float held = SCREEN_LEANS * along / LEANS_WHOLE;
        frontAhead = (frontAhead + passed * (Math.clamp(1f - held, 0f, MOST_HURRIED) - 1f)) % HOUR;
        backAhead = (backAhead + passed * (Math.clamp(1f + held, 0f, MOST_HURRIED) - 1f)) % HOUR;
        front = ((seconds + frontAhead) % HOUR + HOUR) % HOUR;
        back = ((seconds + backAhead) % HOUR + HOUR) % HOUR;
    }

    /** With trace on: what the vehicle's speed was last read as and what the glass makes of it, now and then. */
    void say() {
        if ((said++ & 127) == 0) {
            Log.info(String.format(Locale.ROOT, "glass pull: going (%.2f, %.2f), speed changing %.2f ahead and %.2f aside a second, runs lean %.2f and %.2f",
                    lastX, lastY, along, across, leanAhead, leanAside));
        }
    }
}

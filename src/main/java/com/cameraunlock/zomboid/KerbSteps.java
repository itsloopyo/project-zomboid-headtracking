package com.cameraunlock.zomboid;

import java.util.concurrent.ConcurrentHashMap;

/**
 * How high the picture stands someone walking. The land is smooth but for its kerbs, a step of
 * Terrain.KERB down into the street, and the game moves a walker across one in a frame: stood
 * on the land as it is there, the walker went up or down the whole step at once. So the kerb's
 * share is carried on a spring (Step) as legs carry a body up a step, and the rest of the
 * land, hills and all, is taken as it is, with no lag.
 *
 * The viewer is stepped with the eye, on the logic thread, and everyone else on the render
 * thread as they are drawn. Each is read on any thread.
 */
final class KerbSteps {
    /** Someone not drawn for this long is forgotten, and stands where the land has them when next drawn. */
    private static final long FORGOTTEN_NANOS = 1_000_000_000L;

    private final ConcurrentHashMap<Object, Step> by = new ConcurrentHashMap<>();

    /** The height a person at (x, y) on level z stands at, their step carried on to now. Always on the same thread for one person. */
    float step(Terrain terrain, Object who, float x, float y, float z, long now) {
        float land = terrain.land(x, y);
        return z * ViewCamera.LEVEL_HEIGHT + land - by.computeIfAbsent(who, w -> new Step()).step(land - terrain.lift(x, y, z), now);
    }

    /** The same as last stepped, and the land as it is for someone not stepped lately. Any thread. */
    float up(Terrain terrain, Object who, float x, float y, float z) {
        Step step = by.get(who);
        float land = terrain.land(x, y);
        boolean fresh = step != null && System.nanoTime() - step.at <= Step.LOST_NANOS;
        return z * ViewCamera.LEVEL_HEIGHT + land - (fresh ? step.down : land - terrain.lift(x, y, z));
    }

    /** Puts someone straight on the ground the next time they are stepped: off their feet, or seen from elsewhere. */
    void lose(Object who) {
        by.remove(who);
    }

    /** Lets go of whoever has not been drawn for a while. Render thread. */
    void sweep(long now) {
        by.values().removeIf(step -> now - step.at > FORGOTTEN_NANOS);
    }

    void forget() {
        by.clear();
    }

    /** How far below the land one walker's feet are, carried towards how far below it the ground under them is. */
    private static final class Step {
        /** The spring's time constant, critically damped: a step of the kerb is nine tenths taken in about 0.23 seconds. */
        static final float SPRUNG_FOR = 0.06f;
        /** A gap longer than this between steps puts the feet straight on the ground. */
        static final long LOST_NANOS = 500_000_000L;

        volatile float down;
        private float speed;
        volatile long at;
        private boolean stepped;

        /** @param drop how far below the land the ground under the walker is now: Terrain.KERB in a street, else 0 */
        float step(float drop, long now) {
            if (!stepped || now - at > LOST_NANOS) {
                down = drop;
                speed = 0f;
            } else {
                // The spring's exact solution over the time since the last step, so it is the same at any frame rate.
                float seconds = (now - at) / 1e9f, w = 1f / SPRUNG_FOR, off = down - drop;
                float push = (speed + w * off) * seconds, fade = (float) Math.exp(-w * seconds);
                down = drop + (off + push) * fade;
                speed = (speed - w * push) * fade;
            }
            at = now;
            stepped = true;
            return down;
        }
    }
}

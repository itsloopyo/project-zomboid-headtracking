package com.cameraunlock.zomboid;

/**
 * How hard a vehicle's wheels throw it across the way it faces, read from how its speed on the
 * map changes from one update to the next, less what the mod's own pushes changed it by, and
 * averaged over a short time: see Rollover and TurnRoll. Positive is a throw towards
 * (forwardY, -forwardX) on the map.
 */
final class ThrownAcross {
    /** The most it is thrown across that is counted: a crash the physics flings a vehicle in is not a turn. */
    static final float MOST = 60f;
    /**
     * Going sideways faster than this, metres a second, a vehicle is sliding: a throw across the
     * same way as it slides is something else pushing it (another vehicle, a wall it scrapes),
     * not its wheels, and is not counted. Its wheels only ever push against its slide.
     */
    static final float SLIDING = 0.5f;

    private final float averagedOver, dropsBeyond;
    private float lastX, lastY, across;
    private boolean known;

    /**
     * Averaged over this many seconds, as the physics' speed steps from one update to the next;
     * a step thrown across harder than dropsBeyond m/s/s is taken as no throw by the wheels.
     */
    ThrownAcross(float averagedOver, float dropsBeyond) {
        this.averagedOver = averagedOver;
        this.dropsBeyond = dropsBeyond;
    }

    /** Knows nothing again of how it goes, as on its first step. */
    void restart() {
        known = false;
        across = 0f;
    }

    /** The throw across as the last step that counted left it, m/s/s. */
    float across() {
        return across;
    }

    /**
     * Moves on by seconds of physics stepped, the vehicle facing (forwardX, forwardY) on the map
     * and going at (goingX, goingY), its speed changed by (pushedX, pushedY) by pushes that are
     * not its wheels' since the last step. False where nothing is known yet of how it is thrown:
     * its first step, or a vehicle facing straight up or down.
     */
    boolean step(float forwardX, float forwardY, float goingX, float goingY, float pushedX, float pushedY, float seconds) {
        if (!known) {
            known = true;
            lastX = goingX;
            lastY = goingY;
            return false;
        }
        float flat = (float) Math.hypot(forwardX, forwardY);
        float thrownX = (goingX - lastX - pushedX) / seconds, thrownY = (goingY - lastY - pushedY) / seconds;
        lastX = goingX;
        lastY = goingY;
        if (flat < 1e-3f) {
            return false;
        }
        float side = (thrownX * forwardY - thrownY * forwardX) / flat;
        float sliding = (goingX * forwardY - goingY * forwardX) / flat;
        if (side * sliding > 0f && Math.abs(sliding) > SLIDING || Math.abs(side) > dropsBeyond) {
            side = 0f;
        }
        across += (Math.max(-MOST, Math.min(MOST, side)) - across) * Math.min(1f, seconds / averagedOver);
        return true;
    }
}

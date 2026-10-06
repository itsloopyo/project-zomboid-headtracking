package com.cameraunlock.zomboid;

/**
 * Which way the movement keys go once the first-person view has landed.
 *
 * In the game's own view a key moves the character along the screen, and in first person
 * along the view. The view lands facing the way the character faces, so a key held through
 * the landing would turn the walk by the angle between the two: walking right across the
 * screen became walking to the right of a view that already faced that way. The keys held at
 * the landing go on meaning the walk they were making, turned with the view from there on,
 * for as long as they stay held. Other keys pressed within a short time of the landing mean
 * that walk too, which is the time the player has to move their fingers to the keys that make
 * the same walk in the new view.
 *
 * Logic thread only.
 */
final class Stride {
    /** How long after the landing a change of keys still means the walk the landing found. */
    static final long ADJUST_NANOS = 500_000_000L;

    private boolean kept;
    private float keptX, keptY;
    private float landedYaw;
    private long landedNanos;

    /** The view has landed with these keys held, as the game reads them, and facing this way. */
    void land(float keysX, float keysY, float yaw, long now) {
        kept = keysX != 0f || keysY != 0f;
        keptX = keysX;
        keptY = keysY;
        landedYaw = yaw;
        landedNanos = now;
    }

    void reset() {
        kept = false;
    }

    /**
     * The angle to turn the direction the game reads from the keys by, for keys that are down.
     *
     * @param stockForwardYaw the way the forward key goes in the game's own view
     */
    float turn(float keysX, float keysY, float yaw, float stockForwardYaw, long now) {
        if (kept && (keysX == keptX && keysY == keptY || now - landedNanos < ADJUST_NANOS)) {
            return yaw - landedYaw;
        }
        kept = false;
        return yaw - stockForwardYaw;
    }
}

package com.cameraunlock.zomboid;

/**
 * Which way the movement keys go from the moment first person is asked for.
 *
 * In the game's own view a key moves the character along the screen, and in first person
 * along the view. The view lands facing the way the character faced when the key for first
 * person was pressed, and the keys are the view's from that moment: left to the game until
 * the camera landed, a key pressed while it flew in would be a direction on the screen and
 * turn the character to it, and the view would land looking that way and not the way the
 * character had faced. A key already held at that moment would turn the walk the other way about, by
 * the angle between the screen's forward and the character's. The keys held then go on
 * meaning the walk they were making, turned with the view from there on, for as long as they
 * stay held. Other keys pressed while the camera flies in, or within a short time of its
 * landing, mean that walk too, which is the time the player has to move their fingers to the
 * keys that make the same walk in the new view.
 *
 * Logic thread only.
 */
final class Stride {
    /** How long after the landing a change of keys still means the walk the switch found. */
    static final long ADJUST_NANOS = 500_000_000L;

    /** The cosine of how far from straight ahead the movement keys may ask and still count as ahead: forward with a sideways key is. */
    private static final float AHEAD_AT_LEAST = 0.6f;

    /**
     * Whether the movement keys ask for a way that is not ahead of the view: sideways or back.
     * As the game hands the keys over they are a direction on the screen, where forward is up.
     */
    static boolean aside(float keysX, float keysY) {
        float length = (float) Math.hypot(keysX, keysY);
        return length > 0f && -keysY / length < AHEAD_AT_LEAST;
    }

    private boolean kept;
    private boolean flying;
    private float keptX, keptY;
    private float begunYaw;
    private long landedNanos;

    /** First person has been asked for with these keys held, as the game reads them, and the character facing this way. */
    void begin(float keysX, float keysY, float yaw) {
        kept = keysX != 0f || keysY != 0f;
        flying = true;
        keptX = keysX;
        keptY = keysY;
        begunYaw = yaw;
    }

    /** The camera has landed at the eye. */
    void landed(long now) {
        flying = false;
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
        if (kept && (keysX == keptX && keysY == keptY || flying || now - landedNanos < ADJUST_NANOS)) {
            return yaw - begunYaw;
        }
        kept = false;
        return yaw - stockForwardYaw;
    }
}

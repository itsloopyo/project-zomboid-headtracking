package com.cameraunlock.zomboid;

/**
 * Whether a vehicle driven over the first person hills has left the ground, and how high it
 * is. On the ground it goes where its springs carry it over the land; where the land falls
 * away faster than the vehicle can fall, over a crest or off a kerb at speed, it flies on what
 * it was rising or falling at and falls back to the land at the pull of gravity.
 *
 * Heights are in tiles, which the game takes as metres.
 */
final class Airtime {
    /** Metres a second each second that falling pulls with. */
    static final float GRAVITY = 9.81f;
    /** How long a vehicle is off the ground before it is flying, seconds: shorter, its wheels only went light over a bump. */
    static final float FLYING_AFTER = 0.1f;

    /** Whether the vehicle is off the ground. */
    boolean airborne;
    /** How high the vehicle's foot is, and how fast that rises. */
    float height, rising;
    /** How long it has been off the ground this time, seconds. */
    float aloft;
    /** How fast it was falling the moment it came down, on the step it landed: 0 on any other. */
    float landedAt;

    /** Whether it has been off the ground long enough to have no grip: see FLYING_AFTER. */
    boolean flying() {
        return airborne && aloft >= FLYING_AFTER;
    }

    /**
     * Moves the vehicle on by seconds, where its springs would have its foot at sprung. A jump
     * (another vehicle, a long gap, a teleport) puts it on the ground there.
     */
    void step(float sprung, float seconds, boolean jumped) {
        landedAt = 0f;
        if (jumped || seconds <= 0f) {
            airborne = false;
            height = sprung;
            rising = 0f;
            aloft = 0f;
            return;
        }
        if (airborne) {
            rising -= GRAVITY * seconds;
            height += rising * seconds;
            aloft += seconds;
            if (height <= sprung) {
                landedAt = -rising;
                airborne = false;
                height = sprung;
                rising = 0f;
                aloft = 0f;
            }
            return;
        }
        // The same step as in the air: rising is the climb over the last step, and a step of
        // the land is taken as one of its own climb and fall alike.
        float falls = rising - GRAVITY * seconds, thrown = height + falls * seconds;
        if (thrown > sprung) {
            airborne = true;
            rising = falls;
            height = thrown;
            aloft = seconds;
            return;
        }
        rising = (sprung - height) / seconds;
        height = sprung;
    }
}

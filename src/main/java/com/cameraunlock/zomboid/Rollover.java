package com.cameraunlock.zomboid;

/**
 * A vehicle thrown hard enough across the way it faces rolls over, played alone. The game's
 * physics holds a vehicle's wheels to the road far harder than a car's are held: a Base.CarNormal
 * at 85 km/h on full lock was thrown across at 1.4 g for two seconds and leaned 3 degrees, and a
 * knock on its side at that speed laid it past its side and back onto its wheels. This gives a
 * roll about the vehicle's own length for as much as it is thrown across beyond what tips it, so
 * a tall van taken round a bend too fast goes over, and a car knocked aside rolls over its
 * wheels as they catch. Only what its wheels throw it by is counted. See CarPhysics.
 */
final class Rollover {
    /**
     * How much harder a vehicle has to be thrown across than its width and the height of its
     * middle would tip it at (half its track over that height, times g): a Base.CarNormal's 0.66
     * over 0.82 comes to 12.6 m/s/s, just under the 14 its wheels hold in a turn.
     */
    static final float HELD_BEYOND = 1.6f;
    /**
     * The roll given for each metre a second each second it is thrown across beyond what tips it,
     * in the game's physics' torque per kilogram. Measured on a Base.CarNormal standing still: 10
     * for a quarter of a second lifted it 27 degrees onto two wheels and it fell back, 20 put it
     * on its roof.
     */
    static final float ROLL_PER = 2f;
    /** Tipped this far, its own up this low, the physics carries the roll on by the vehicle's weight, or brings it back: nothing more is given. */
    static final float TIPPED = 0.5f;
    /** How long the throw across is averaged over, seconds. */
    static final float AVERAGED_OVER = 0.1f;

    /** How hard a vehicle whose wheels are halfTrack out from its middle, which is height above the ground, is thrown across before it tips, m/s/s. */
    static float tipsAt(float halfTrack, float height) {
        return Airtime.GRAVITY * halfTrack / height * HELD_BEYOND;
    }

    private final ThrownAcross thrown = new ThrownAcross(AVERAGED_OVER, Float.MAX_VALUE);

    /** Knows nothing again of how the vehicle goes: after a gap in its updates. */
    void restart() {
        thrown.restart();
    }

    /**
     * Moves the vehicle on by seconds, facing (forwardX, forwardY) on the map, its own up's
     * height in the world upUp, going at (goingX, goingY), tipping when thrown across harder than
     * tipsAt. Returns the roll to give it this step about its own forward axis, per kilogram, as
     * the game's physics takes a torque: positive turns its top away from (forwardY, -forwardX)
     * on the map, the way it was measured.
     */
    float step(float forwardX, float forwardY, float upUp, float goingX, float goingY, float seconds, float tipsAt) {
        // Thrown towards (forwardY, -forwardX) by its wheels, they are pulled that way and its top
        // goes the other: so a vehicle knocked aside rolls as it is tripped by its wheels after the knock.
        if (!thrown.step(forwardX, forwardY, goingX, goingY, seconds)) {
            return 0f;
        }
        float across = thrown.across();
        if (upUp < TIPPED || Math.abs(across) <= tipsAt) {
            return 0f;
        }
        return Math.signum(across) * (Math.abs(across) - tipsAt) * ROLL_PER;
    }
}

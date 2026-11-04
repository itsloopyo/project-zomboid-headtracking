package com.cameraunlock.zomboid;

/**
 * A vehicle turned hard, or slid round with the brakes on, leans less than the game's physics
 * leans it, played alone. The game's physics pushes a vehicle across by its wheels' grip on the
 * road, and rolls it as if that push were given that share of the way down from its place to
 * the road which the vehicle's script calls its rollInfluence: 0.5 to 1 for the game's own
 * vehicles, 1 being the road itself. This gives a roll against that, so only KEPT of it is left,
 * from how hard its wheels throw it across (ThrownAcross). A throw harder than its wheels can
 * give, as a crash gives, is left to the game. Its place is how far over the ground its wheels
 * meet: see CarPhysics.gripsBelow.
 */
final class TurnRoll {
    /** The share of the roll the wheels' grip gives that is left. */
    static final float KEPT = 0.2f;
    /** Tipped this far, its own up this low, it is on its side or going over by its weight: nothing is given. */
    static final float UPRIGHT = 0.7f;
    /** How long the throw across is averaged over, seconds: short, since a slide that catches throws it hardest for a moment. */
    static final float AVERAGED_OVER = 0.03f;

    /**
     * Thrown across harder than this, m/s/s, it is not its wheels: the game's physics held a
     * Base.CarNormal to 14 at full lock (see Rollover), and a car sliding into a wall or struck
     * from the side is thrown far harder for a moment.
     */
    static final float GRIP_MOST = 20f;

    private final ThrownAcross thrown = new ThrownAcross(AVERAGED_OVER, GRIP_MOST);

    /** Knows nothing again of how the vehicle goes: after a gap in its updates. */
    void restart() {
        thrown.restart();
    }

    /** How hard its wheels throw it across, as the last step left it, m/s/s. */
    float across() {
        return thrown.across();
    }

    /**
     * Moves the vehicle on by seconds of physics stepped, facing (forwardX, forwardY) on the map,
     * its own up's height in the world upUp, going at (goingX, goingY), its speed changed by
     * (pushedX, pushedY) by pushes that are not its wheels' (ThrownAcross); its script's
     * rollInfluence, and how far under its place its wheels meet the ground in metres. Returns the roll to give it
     * this step about its own forward axis, per kilogram, signed as Rollover's: positive turns its
     * top away from (forwardY, -forwardX) on the map.
     */
    float step(float forwardX, float forwardY, float upUp, float goingX, float goingY, float pushedX, float pushedY, float seconds, float rollInfluence,
            float below) {
        if (!thrown.step(forwardX, forwardY, goingX, goingY, pushedX, pushedY, seconds) || upUp < UPRIGHT) {
            return 0f;
        }
        // Thrown towards (forwardY, -forwardX), its wheels roll its top the other way: the positive way.
        return -(1f - KEPT) * rollInfluence * below * thrown.across();
    }
}

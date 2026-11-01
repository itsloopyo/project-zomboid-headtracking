package com.cameraunlock.zomboid;

/**
 * The springs a vehicle rides on over the first person hills, one at each wheel. Each corner of
 * the body is held over the ground under its own wheel by a spring and a damper between the two,
 * so it is only a change in how the ground climbs that moves a corner against its wheel: the
 * front of a car driven onto a hill lifts and settles before the back gets there, a dip drops
 * the front and then the back, and ground that rises under one side rolls the body over to the
 * other. On a steady climb every corner sits where it sits on the flat.
 *
 * Heights are in tiles, which the game takes as metres. Logic thread.
 */
final class Suspension {
    /** How fast a corner bounces on its spring, radians a second (1.3 a second), and its damping against what would stop it dead: a road car's. */
    static final float BOUNCE = 8.2f, DAMPING = 0.3f;
    /** How far a corner moves from where it rests over its wheel before it meets its stop, either way. */
    static final float TRAVEL = 0.12f;
    /**
     * How long the ground's climb under a wheel is smoothed over, seconds: the climb is read off
     * where the game has the vehicle from one step to the next, and a step that carried it a little
     * further than the last would otherwise shake every corner.
     */
    private static final float CLIMB_SMOOTHED_FOR = 0.03f;

    private final Kick.Spring[] corners = new Kick.Spring[4];
    /** The height of the ground under each wheel at the last step, and how fast it was climbing then. */
    private final float[] under = new float[4], climbing = new float[4];
    /**
     * How many steps it has been carried since it was set down on the ground. Each corner's spring
     * holds how far, and how fast, the corner is from resting over its wheel; the ground's climb is
     * known from the first step, and a change in it from the second.
     */
    private int carried;
    private final float[] sprung = new float[4];

    Suspension() {
        for (int i = 0; i < corners.length; i++) {
            corners[i] = new Kick.Spring(BOUNCE, DAMPING, TRAVEL);
        }
    }

    /**
     * Carries the body seconds on over ground, the lie of the land under its four wheels now, and
     * lays body on its four corners. Set down, after a jump or in the air, every corner rests over
     * its wheel.
     */
    void carry(VehicleSlope ground, float seconds, boolean setDown, VehicleSlope body) {
        if (setDown) {
            carried = 0;
        }
        for (int i = 0; i < corners.length; i++) {
            Kick.Spring corner = corners[i];
            float now = ground.corners[i];
            if (carried == 0) {
                corner.rest();
                climbing[i] = 0f;
            } else if (seconds > 0f) {
                float climb = (now - under[i]) / seconds;
                // The body has the climb it had a step ago; the ground under it has changed its own since.
                if (carried > 1) {
                    climb = climbing[i] + (climb - climbing[i]) * (1f - (float) Math.exp(-seconds / CLIMB_SMOOTHED_FOR));
                    corner.speed -= climb - climbing[i];
                }
                climbing[i] = climb;
                corner.step(seconds);
            }
            under[i] = now;
            sprung[i] = now + corner.at;
        }
        if (seconds > 0f || carried == 0) {
            carried++;
        }
        body.on(sprung[0], sprung[1], sprung[2], sprung[3], ground.facingX, ground.facingY, ground.length, ground.width);
    }

    /** Comes down on the land falling at speed, metres a second: every corner is pushed down onto its spring. */
    void land(float speed) {
        for (Kick.Spring corner : corners) {
            corner.speed -= speed;
        }
    }
}

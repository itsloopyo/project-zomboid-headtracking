package com.cameraunlock.zomboid;

/**
 * The physics steps one vehicle was given since its last update, and how much pushes that are
 * not its wheels' changed its speed in them: see CarPhysics. Logic thread only.
 */
final class Stepped {
    /** How long one step of the game's physics is, seconds: WorldSimulation steps Bullet at this fixed rate. */
    static final float PHYSICS_STEP = 0.01f;

    private int steps;
    private float pushedX, pushedY, seconds, tookX, tookY;

    /** One physics step, in which something not its wheels pushes the vehicle at (x, y) m/s/s on the map. */
    void step(float x, float y) {
        steps++;
        pushedX += x * PHYSICS_STEP;
        pushedY += y * PHYSICS_STEP;
    }

    /** Takes what was counted since the last take and counts again from nothing: false where no step was taken. */
    boolean take() {
        seconds = steps * PHYSICS_STEP;
        tookX = pushedX;
        tookY = pushedY;
        steps = 0;
        pushedX = 0f;
        pushedY = 0f;
        return seconds > 0f;
    }

    /** The physics time the last take held, seconds. */
    float seconds() {
        return seconds;
    }

    /** How much the pushes changed its speed on the map in the last take, metres a second. */
    float pushedX() {
        return tookX;
    }

    float pushedY() {
        return tookY;
    }
}

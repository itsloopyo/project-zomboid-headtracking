package com.cameraunlock.zomboid;

/**
 * The driver's place the picture builds in a vehicle, in tiles along the vehicle, across it
 * and up.
 */
final class Cabins {
    private Cabins() {}

    /** The place is laid out at one and a half times the size a vehicle is drawn at. */
    static final float SCALE = 2f / 3f;

    /**
     * The wheel's middle from the eye, its radius, the way its column runs, away from the
     * driver, and where the mirror on the screen hangs.
     */
    private static final float[] PLACE = {0.531f, 0f, -0.447f, 0.231f, 0.943f, 0f, -0.332f, 0.46f, 0.34f, 0.07f};

    /** Where the eye of a driver is from the middle of the wheel: behind it and above. */
    static final float EYE_BEHIND = PLACE[0] * SCALE, EYE_ABOVE = -PLACE[2] * SCALE;

    /**
     * The driver's place in the vehicle with that script, or null where the vehicle's own
     * steering wheel has not been found: the place is built on the game's wheel, and without
     * one there is nowhere to build it.
     */
    static float[] wheel(String script) {
        return script == null || !VehicleMeshes.wheels.containsKey(script) ? null : PLACE;
    }
}

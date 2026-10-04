package com.cameraunlock.zomboid;

import java.util.Map;

/**
 * What the picture knows of the inside of each kind of vehicle, by the name of its script.
 * Places are measured from the driver's eye as the picture sets it: along the vehicle, across
 * it and up, in tiles.
 */
final class Cabins {
    private Cabins() {}

    /**
     * Each steering wheel: its middle, its radius, the way its column runs, away from the
     * driver, and then where the mirror on the screen hangs. Read off the depth of the game's own model as drawn from the seat. A vehicle
     * that is not here keeps the wheel the game gives it.
     */
    private static final Map<String, float[]> WHEELS = Map.of(
            "Base.CarNormal", new float[] {0.531f, 0f, -0.447f, 0.231f, 0.943f, 0f, -0.332f, 0.46f, 0.34f, 0.07f});

    /** The wheel of the vehicle with that script, or null where it has not been measured. */
    static float[] wheel(String script) {
        return script == null ? null : WHEELS.get(script);
    }
}

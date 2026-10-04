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

    /**
     * What each family of vehicles has at the driver's place. A row is: the wheel's size
     * against the standard, its rim's thickness, its spokes (2 a bar, 3 a T, 4 two low pairs,
     * 5 a cross, 6 three thin metal ones) and their thickness; the hub's half-width, half-height
     * and roundness; the instruments (0 a strip, 1 two dials, 2 a large dial between two small,
     * 3 a row, 5 one dial and a small one), their radius and number; the hood's half-width and
     * half-height, none for a bare plate; the colours of the dash and of the seats; the colour
     * the instruments light up in; the gear lever (1 on the column, 2 on the floor); the wood
     * on the dash (none, half the height of a band of it, or 1 for the whole face); its air
     * vents (1 round, 2 slatted rectangles); what the seats are covered in (0 plain, 1 ribs
     * down them, 2 pleats across them, 3 plaid, 4 two fine stripes); what stands between the
     * seats (1 a console with the lever's boot, 2 the boot alone, 3 a van's engine cover,
     * 4 the cover and a post up the middle of the screen); and what is set in the dash
     * (1 a radio, 2 a glove box, 3 both).
     * A script belongs to the family whose name it starts with, the longest such name winning:
     * the liveries of one body share its script's stem.
     */
    private static final String[] FAMILIES = {
        "Base.CarNormal", "Base.CarLights", "Base.CarTaxi", "Base.CarLuxury", "Base.CarStationWagon", "Base.ModernCar02", "Base.ModernCar", "Base.OffRoad",
        "Base.PickUpTruck", "Base.PickUpVan", "Base.RaceCar", "Base.SmallCar02", "Base.SmallCar", "Base.SportsCar", "Base.StepVan", "Base.SUV", "Base.Van",
    };
    private static final float[] SALOON = {1.05f, 0.017f, 2, 0.024f, 0.085f, 0.04f, 0.02f, 0, 0.05f, 1, 0.21f, 0.05f,
        0.23f, 0.27f, 0.33f, 0.30f, 0.35f, 0.42f, 0.6f, 1f, 0.75f, 1, 0.022f, 2, 1, 0, 3};
    private static final float[][] KITS = {
        SALOON,
        {1.05f, 0.017f, 2, 0.024f, 0.085f, 0.04f, 0.02f, 0, 0.05f, 1, 0.21f, 0.05f, 0.12f, 0.12f, 0.125f, 0.13f, 0.13f, 0.14f, 0.6f, 1f, 0.75f, 1, 0, 2, 0, 0, 3},
        {1.05f, 0.017f, 2, 0.024f, 0.085f, 0.04f, 0.02f, 0, 0.05f, 1, 0.21f, 0.05f, 0.42f, 0.34f, 0.24f, 0.44f, 0.36f, 0.26f, 0.6f, 1f, 0.75f, 1, 0, 2, 0, 0, 3},
        {1.08f, 0.022f, 4, 0.02f, 0.10f, 0.07f, 0.03f, 2, 0.06f, 3, 0.21f, 0.075f, 0.12f, 0.11f, 0.10f, 0.55f, 0.40f, 0.24f, 1f, 0.7f, 0.3f, 2, 0.03f, 1, 2, 1, 3},
        {1.05f, 0.017f, 2, 0.024f, 0.085f, 0.04f, 0.02f, 0, 0.05f, 1, 0.21f, 0.05f, 0.40f, 0.30f, 0.20f, 0.45f, 0.36f, 0.25f, 0.6f, 1f, 0.75f, 1, 1, 2, 3, 0, 3},
        {1.0f, 0.022f, 4, 0.02f, 0.10f, 0.05f, 0.02f, 2, 0.058f, 3, 0.22f, 0.072f, 0.14f, 0.17f, 0.24f, 0.18f, 0.23f, 0.34f, 0.5f, 0.9f, 1f, 1, 0, 2, 1, 0, 3},
        {0.97f, 0.024f, 5, 0.018f, 0.09f, 0.075f, 0.02f, 1, 0.052f, 2, 0.185f, 0.066f, 0.30f, 0.30f, 0.31f, 0.36f, 0.36f, 0.38f, 0.6f, 1f, 0.7f, 2, 0, 2, 0, 1, 3},
        {1.0f, 0.016f, 6, 0.011f, 0.035f, 0.035f, 0.035f, 3, 0.034f, 5, 0.22f, 0f, 0.25f, 0.27f, 0.19f, 0.12f, 0.12f, 0.12f, 1f, 1f, 0.95f, 2, 0, 0, 0, 2, 0},
        {1.10f, 0.018f, 2, 0.022f, 0.07f, 0.045f, 0.02f, 1, 0.055f, 2, 0.2f, 0.07f, 0.36f, 0.26f, 0.17f, 0.42f, 0.31f, 0.20f, 0.6f, 1f, 0.75f, 1, 0, 2, 2, 0, 3},
        {1.12f, 0.016f, 3, 0.014f, 0.04f, 0.04f, 0.04f, 3, 0.031f, 6, 0.24f, 0.05f, 0.10f, 0.10f, 0.10f, 0.10f, 0.12f, 0.20f, 1f, 0.75f, 0.4f, 2, 0, 0, 0, 2, 3},
        {0.92f, 0.022f, 6, 0.012f, 0.035f, 0.035f, 0.035f, 3, 0.036f, 5, 0.22f, 0f, 0.55f, 0.55f, 0.57f, 0.08f, 0.08f, 0.08f, 1f, 1f, 0.95f, 2, 0, 0, 0, 2, 0},
        {0.96f, 0.019f, 4, 0.02f, 0.06f, 0.06f, 0.012f, 1, 0.05f, 2, 0.18f, 0.07f, 0.10f, 0.10f, 0.10f, 0.38f, 0.38f, 0.40f, 1f, 0.6f, 0.2f, 2, 0, 2, 4, 1, 3},
        {0.95f, 0.015f, 2, 0.02f, 0.05f, 0.04f, 0.012f, 5, 0.058f, 2, 0.15f, 0.075f, 0.45f, 0.37f, 0.26f, 0.45f, 0.36f, 0.25f, 0.6f, 1f, 0.75f, 2, 0, 2, 0, 2, 1},
        {0.93f, 0.026f, 2, 0.032f, 0.08f, 0.06f, 0.03f, 0, 0.05f, 1, 0.19f, 0.06f, 0.10f, 0.10f, 0.10f, 0.45f, 0.10f, 0.10f, 0.3f, 0.9f, 1f, 2, 0, 2, 2, 1, 3},
        {1.30f, 0.016f, 6, 0.011f, 0.035f, 0.035f, 0.035f, 5, 0.055f, 2, 0.14f, 0f, 0.62f, 0.60f, 0.54f, 0.08f, 0.08f, 0.08f, 0.6f, 1f, 0.75f, 2, 0, 0, 0, 4, 0},
        {1.02f, 0.022f, 4, 0.02f, 0.095f, 0.05f, 0.02f, 1, 0.055f, 2, 0.2f, 0.072f, 0.33f, 0.29f, 0.25f, 0.45f, 0.40f, 0.33f, 0.5f, 0.95f, 0.9f, 1, 0, 2, 0, 0, 3},
        {1.12f, 0.018f, 2, 0.022f, 0.08f, 0.04f, 0.02f, 1, 0.055f, 2, 0.2f, 0.07f, 0.25f, 0.24f, 0.22f, 0.45f, 0.38f, 0.28f, 0.6f, 1f, 0.75f, 1, 0, 2, 0, 3, 3},
    };

    /** The driver's place of the family that script belongs to. A script of no family here has the saloon's. */
    static float[] kit(String script) {
        int best = -1;
        for (int i = 0; i < FAMILIES.length; i++) {
            if (script.startsWith(FAMILIES[i]) && (best < 0 || FAMILIES[i].length() > FAMILIES[best].length())) {
                best = i;
            }
        }
        return best < 0 ? SALOON : KITS[best];
    }

    /** A steering wheel turns about eight times as far as the road wheels do, and a turn and a quarter at most either way. */
    private static final float STEERING_RATIO = 8f, MOST_TURN = 2.5f * (float) Math.PI;

    /** How far the steering wheel is turned, in radians, with the road wheels turned that far. */
    static float wheelTurn(float steering) {
        return Math.clamp(steering * STEERING_RATIO, -MOST_TURN, MOST_TURN);
    }

    /**
     * Where a body's cabin is from the middle of its wheel, as VehicleMeshes.Parts has it, for
     * a body not yet measured: the standard car's.
     */
    static final float[] STANDARD_MEASURES = {0.37f, 0.07f, 0.07f, 0.41f, 0.323f, 0.12f, 0.21f, 0.53f, 0.09f};
}

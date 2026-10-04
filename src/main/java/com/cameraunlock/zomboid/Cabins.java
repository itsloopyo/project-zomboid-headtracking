package com.cameraunlock.zomboid;

/**
 * What the picture knows of the inside of each kind of vehicle, by the name of its script.
 * Places are measured along the vehicle, across it and up, in tiles.
 */
final class Cabins {
    private Cabins() {}

    /**
     * The driver's place the picture builds: the wheel's middle from the eye, its radius, the
     * way its column runs, away from the driver, and where the mirror on the screen hangs.
     */
    private static final float[] PLACE = {0.531f, 0f, -0.447f, 0.231f, 0.943f, 0f, -0.332f, 0.46f, 0.34f, 0.07f};

    /**
     * For each family of vehicles, how far the driver's eye is moved from where the seat alone
     * would put it, so that the game's own steering wheel is where PLACE has it: straight
     * ahead and at arm's length. Read off the depth of each model as drawn from the seat. A
     * script belongs to the family whose name it starts with, the longest such name winning,
     * since the liveries of one body share its script's stem.
     */
    private static final String[] FAMILIES = {
        "Base.CarNormal", "Base.CarLights", "Base.CarTaxi", "Base.CarLuxury", "Base.CarStationWagon", "Base.ModernCar02", "Base.ModernCar",
        "Base.PickUpTruck", "Base.PickUpVan", "Base.SUV", "Base.SmallCar02", "Base.SportsCar",
    };
    private static final float[][] EYES = {
        {0f, 0f, 0f}, {0f, 0f, 0f}, {0f, 0f, 0f}, {-0.032f, 0.132f, -0.035f}, {-0.104f, 0.003f, 0.049f}, {-0.072f, 0.025f, 0.039f}, {0.082f, 0.024f, 0.007f},
        {-0.129f, -0.044f, 0.282f}, {-0.078f, -0.043f, 0.238f}, {-0.037f, 0.044f, 0.215f}, {0.050f, -0.036f, 0.041f}, {-0.150f, -0.044f, -0.153f},
    };

    /** Scripts that start with a family's name and are another body altogether. */
    private static final String[] NOT = {"Base.CarStationWagon2"};

    private static int family(String script) {
        if (script == null) {
            return -1;
        }
        for (String other : NOT) {
            if (script.startsWith(other)) {
                return -1;
            }
        }
        int best = -1;
        for (int i = 0; i < FAMILIES.length; i++) {
            if (script.startsWith(FAMILIES[i]) && (best < 0 || FAMILIES[i].length() > FAMILIES[best].length())) {
                best = i;
            }
        }
        return best;
    }

    /** The driver's place in the vehicle with that script, or null where its cabin has not been measured. */
    static float[] wheel(String script) {
        return family(script) < 0 ? null : PLACE;
    }

    /** How far to move the driver's eye in that vehicle: along it, across it and up. Null where it has not been measured. */
    static float[] eye(String script) {
        int family = family(script);
        return family < 0 ? null : EYES[family];
    }
}

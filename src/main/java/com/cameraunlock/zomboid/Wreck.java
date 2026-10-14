package com.cameraunlock.zomboid;

/**
 * A vehicle left on its roof, played alone, catches fire after a few seconds and burns out, as
 * in an open-world driving game: the fire takes hold under it, whoever is still inside is
 * thrown clear, and a little later it goes up and what is left is the game's own burnt shell.
 * See CarPhysics for where this meets the game. Once alight it burns out whichever way up it
 * ends.
 */
final class Wreck {
    /** On its roof: its own up points this far down, or further. Past its side, more than about 120 degrees over. */
    static final float ON_ITS_ROOF = -0.5f;
    /** How long a vehicle lies on its roof before it catches fire, seconds. */
    static final float CATCHES_AFTER = 4f;
    /** How long it burns before it goes up, seconds. */
    static final float GOES_UP_AFTER = 6f;

    enum Stage { WHOLE, ALIGHT, BURNT }

    /** What happens to the vehicle on this step: nothing, it catches fire, or it goes up. */
    enum Change { NONE, CATCHES, GOES_UP }

    /**
     * The game's burnt shells (media/scripts/generated/vehicles/burntAndSmashedVehicles) by the
     * start of the names of the vehicles they are left of, the longest start first. The game
     * keeps no such table: it only ever spawns its shells as they are.
     */
    private static final String[][] SHELLS = {
            {"CarLightsPolice", "NormalCarBurntPolice"}, {"CarLights", "NormalCarBurntPolice"}, {"CarLuxury", "LuxuryCarBurnt"},
            {"CarTaxi", "TaxiBurnt"}, {"CarStationWagon", "CarNormalBurnt"}, {"CarNormal", "CarNormalBurnt"},
            {"ModernCarLights", "NormalCarBurntPolice"}, {"ModernCar02", "ModernCar02Burnt"}, {"ModernCar", "ModernCarBurnt"},
            {"OffRoad", "OffRoadBurnt"}, {"PickUpTruckLights", "PickupSpecialBurnt"}, {"PickUpTruck", "PickupBurnt"},
            {"PickUpVanLights", "PickUpVanLightsBurnt"}, {"PickUpVan", "PickUpVanBurnt"}, {"RaceCar", "RaceCarBurnt"},
            {"SmallCar02", "SmallCar02Burnt"}, {"SmallCar", "SmallCarBurnt"}, {"SportsCar", "SportsCarBurnt"}, {"SUV", "SUVBurnt"},
            {"VanAmbulance", "AmbulanceBurnt"}, {"VanRadio", "VanRadioBurnt"}, {"VanSeats", "VanSeatsBurnt"}, {"StepVan", "VanBurnt"},
            {"Van", "VanBurnt"},
    };

    /** The game's burnt shell a vehicle of the game's own, by its full script name, is left as, or null for one the table does not know. */
    static String shellOf(String scriptName) {
        String module = scriptName.substring(0, scriptName.indexOf('.') + 1), name = scriptName.substring(module.length());
        if (!module.equals("Base.")) {
            return null;
        }
        for (String[] shell : SHELLS) {
            if (name.startsWith(shell[0])) {
                return "Base." + shell[1];
            }
        }
        return null;
    }

    Stage stage = Stage.WHOLE;
    /** How long it has lain on its roof, or burnt, this stage. */
    float lasted;

    /**
     * Moves the vehicle on by seconds, its own up's height in the world being up (1 standing,
     * -1 on its roof).
     */
    Change step(float up, float seconds) {
        switch (stage) {
            case WHOLE -> {
                lasted = up <= ON_ITS_ROOF ? lasted + seconds : 0f;
                if (lasted >= CATCHES_AFTER) {
                    stage = Stage.ALIGHT;
                    lasted = 0f;
                    return Change.CATCHES;
                }
            }
            case ALIGHT -> {
                lasted += seconds;
                if (lasted >= GOES_UP_AFTER) {
                    stage = Stage.BURNT;
                    lasted = 0f;
                    return Change.GOES_UP;
                }
            }
            case BURNT -> { }
        }
        return Change.NONE;
    }
}

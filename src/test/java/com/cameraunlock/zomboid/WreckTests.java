package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** When a vehicle on its roof catches fire and goes up, and what it is left as. */
final class WreckTests {
    private WreckTests() {}

    static void run() {
        Checks.run("aVehicleOnItsRoofCatchesFireAfterAFewSecondsAndThenGoesUp", WreckTests::aVehicleOnItsRoofCatchesFireAfterAFewSecondsAndThenGoesUp);
        Checks.run("onItsSideOrRightedInTimeItDoesNot", WreckTests::onItsSideOrRightedInTimeItDoesNot);
        Checks.run("eachOfTheGamesVehiclesIsLeftAsAShellOfItsKind", WreckTests::eachOfTheGamesVehiclesIsLeftAsAShellOfItsKind);
    }

    private static final float STEP = 0.25f;

    private static void aVehicleOnItsRoofCatchesFireAfterAFewSecondsAndThenGoesUp() {
        Wreck wreck = new Wreck();
        float t = 0f;
        Wreck.Change change = Wreck.Change.NONE;
        while (change == Wreck.Change.NONE) {
            change = wreck.step(-1f, STEP);
            t += STEP;
        }
        check(change == Wreck.Change.CATCHES && Math.abs(t - Wreck.CATCHES_AFTER) < 1e-4f, "on its roof it catches fire after " + Wreck.CATCHES_AFTER + " s: " + t);
        t = 0f;
        change = Wreck.Change.NONE;
        while (change == Wreck.Change.NONE) {
            // Righted while alight, it burns out all the same.
            change = wreck.step(1f, STEP);
            t += STEP;
        }
        check(change == Wreck.Change.GOES_UP && Math.abs(t - Wreck.GOES_UP_AFTER) < 1e-4f, "and goes up after burning " + Wreck.GOES_UP_AFTER + " s, whichever way up: " + t);
        for (int i = 0; i < 100; i++) {
            check(wreck.step(-1f, STEP) == Wreck.Change.NONE, "a burnt shell does nothing more");
        }
    }

    private static void onItsSideOrRightedInTimeItDoesNot() {
        Wreck side = new Wreck();
        for (int i = 0; i < 400; i++) {
            check(side.step(0f, STEP) == Wreck.Change.NONE, "on its side a vehicle never catches fire");
        }
        Wreck righted = new Wreck();
        for (int round = 0; round < 20; round++) {
            for (float t = 0f; t < Wreck.CATCHES_AFTER - 2 * STEP; t += STEP) {
                check(righted.step(-1f, STEP) == Wreck.Change.NONE, "not before its time on its roof");
            }
            check(righted.step(1f, STEP) == Wreck.Change.NONE && righted.lasted == 0f, "righted, it starts again from nothing");
        }
    }

    private static void eachOfTheGamesVehiclesIsLeftAsAShellOfItsKind() {
        check("Base.CarNormalBurnt".equals(Wreck.shellOf("Base.CarNormal")), "a saloon is a burnt saloon");
        check("Base.NormalCarBurntPolice".equals(Wreck.shellOf("Base.CarLightsPolice")), "a police car a burnt police car");
        check("Base.ModernCar02Burnt".equals(Wreck.shellOf("Base.ModernCar02")) && "Base.ModernCarBurnt".equals(Wreck.shellOf("Base.ModernCar_Martin")),
                "the longest start of a name is taken");
        check("Base.PickUpVanLightsBurnt".equals(Wreck.shellOf("Base.PickUpVanLightsFire")) && "Base.PickupBurnt".equals(Wreck.shellOf("Base.PickUpTruckMccoy")),
                "a pickup van and a pickup truck each their own");
        check("Base.AmbulanceBurnt".equals(Wreck.shellOf("Base.VanAmbulance")) && "Base.VanBurnt".equals(Wreck.shellOf("Base.StepVan_Florist")),
                "an ambulance its own, and a step van a van");
        check(Wreck.shellOf("Base.Trailer") == null && Wreck.shellOf("SomeMod.CarNormal") == null, "a trailer, and another module's vehicle, are not in the table");
    }
}

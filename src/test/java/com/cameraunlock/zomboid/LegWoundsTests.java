package com.cameraunlock.zomboid;

/** Tests of the record of zombies' shot legs. */
final class LegWoundsTests {
    private LegWoundsTests() {}

    static void run() {
        Checks.run("aLegIsRuinedOnceItsShotsReachTheHealthLeft", LegWoundsTests::aLegIsRuinedOnceItsShotsReachTheHealthLeft);
        Checks.run("eachLegCountsItsOwnShots", LegWoundsTests::eachLegCountsItsOwnShots);
        Checks.run("eachZombieCountsItsOwnShots", LegWoundsTests::eachZombieCountsItsOwnShots);
        Checks.run("aZombieIsForgottenOnceRuinedOrMadeAgain", LegWoundsTests::aZombieIsForgottenOnceRuinedOrMadeAgain);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void aLegIsRuinedOnceItsShotsReachTheHealthLeft() {
        LegWounds wounds = new LegWounds();
        Object zombie = new Object();
        check(!wounds.ruins(zombie, LegWounds.LEFT, 0.8f, 1.8f), "one pistol shot does not ruin a leg of a zombie with 1.8 health left");
        check(!wounds.ruins(zombie, LegWounds.LEFT, 0.8f, 1.75f), "nor two");
        check(wounds.ruins(zombie, LegWounds.LEFT, 0.8f, 1.7f), "a third does");
        check(new LegWounds().ruins(new Object(), LegWounds.RIGHT, 1.8f, 1.8f), "a shot exactly the health left ruins it");
    }

    private static void eachLegCountsItsOwnShots() {
        LegWounds wounds = new LegWounds();
        Object zombie = new Object();
        check(!wounds.ruins(zombie, LegWounds.LEFT, 1.2f, 1.8f), "a shot in the left leg");
        check(!wounds.ruins(zombie, LegWounds.RIGHT, 1.2f, 1.8f), "and one in the right do not add up");
        check(wounds.ruins(zombie, LegWounds.RIGHT, 1.2f, 1.8f), "a second in the right ruins it");
    }

    private static void eachZombieCountsItsOwnShots() {
        LegWounds wounds = new LegWounds();
        Object first = new Object(), second = new Object();
        check(!wounds.ruins(first, LegWounds.LEFT, 1.2f, 1.8f), "a shot in one zombie's leg");
        check(!wounds.ruins(second, LegWounds.LEFT, 1.2f, 1.8f), "adds nothing to another's");
    }

    private static void aZombieIsForgottenOnceRuinedOrMadeAgain() {
        LegWounds wounds = new LegWounds();
        Object ruined = new Object(), gone = new Object();
        wounds.ruins(ruined, LegWounds.LEFT, 2f, 1.8f);
        check(wounds.remembered() == 0, "a zombie whose leg is ruined is forgotten");
        wounds.ruins(gone, LegWounds.LEFT, 1.2f, 1.8f);
        check(wounds.remembered() == 1, "one with a shot leg is remembered");
        wounds.forget(gone);
        check(wounds.remembered() == 0, "and forgotten when the game makes a new zombie of its object");
        check(!wounds.ruins(gone, LegWounds.LEFT, 1.2f, 1.8f), "the object made again starts with sound legs");
    }
}

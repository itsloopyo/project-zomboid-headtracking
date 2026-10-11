package com.cameraunlock.zomboid;

/** Tests of the record of zombies' shot limbs. */
final class WoundsTests {
    private WoundsTests() {}

    static void run() {
        Checks.run("aLimbIsWoundedAtHalfTheHealthLeftAndRuinedAtAll", WoundsTests::aLimbIsWoundedAtHalfTheHealthLeftAndRuinedAtAll);
        Checks.run("oneShotCanRuinASoundLimb", WoundsTests::oneShotCanRuinASoundLimb);
        Checks.run("aLimbOnlyGetsWorse", WoundsTests::aLimbOnlyGetsWorse);
        Checks.run("eachLimbCountsItsOwnShots", WoundsTests::eachLimbCountsItsOwnShots);
        Checks.run("eachZombieCountsItsOwnShots", WoundsTests::eachZombieCountsItsOwnShots);
        Checks.run("aZombieIsForgottenWhenMadeAgain", WoundsTests::aZombieIsForgottenWhenMadeAgain);
        Checks.run("theLurchFollowsTheWoundedFoot", WoundsTests::theLurchFollowsTheWoundedFoot);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void aLimbIsWoundedAtHalfTheHealthLeftAndRuinedAtAll() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        check(wounds.shot(zombie, Wounds.LEFT_LEG, 0.8f, 1.8f) == Wounds.SOUND, "one pistol shot leaves a leg of a zombie with 1.8 health left sound");
        check(wounds.stage(zombie, Wounds.LEFT_LEG) == Wounds.SOUND, "and says so");
        check(wounds.shot(zombie, Wounds.LEFT_LEG, 0.8f, 1.75f) == Wounds.WOUNDED, "a second wounds it");
        check(wounds.stage(zombie, Wounds.LEFT_LEG) == Wounds.WOUNDED, "and it stays wounded");
        check(wounds.shot(zombie, Wounds.LEFT_LEG, 0.8f, 1.7f) == Wounds.RUINED, "a third ruins it");
        check(wounds.stage(zombie, Wounds.LEFT_LEG) == Wounds.RUINED, "and it stays ruined");
    }

    private static void oneShotCanRuinASoundLimb() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        check(wounds.shot(zombie, Wounds.RIGHT_ARM, 1.8f, 1.8f) == Wounds.RUINED, "a shot of all the health left ruins a sound arm at once");
    }

    private static void aLimbOnlyGetsWorse() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        wounds.shot(zombie, Wounds.LEFT_ARM, 1f, 1.8f);
        check(wounds.shot(zombie, Wounds.LEFT_ARM, 0.01f, 10f) == Wounds.SOUND, "more health later does not heal it, and the shot changes nothing");
        check(wounds.stage(zombie, Wounds.LEFT_ARM) == Wounds.WOUNDED, "it is still wounded");
    }

    private static void eachLimbCountsItsOwnShots() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        for (int limb = 0; limb < Wounds.LIMBS; limb++) {
            check(wounds.shot(zombie, limb, 0.8f, 1.8f) == Wounds.SOUND, "a shot in limb " + limb + " adds nothing to the others");
        }
        check(wounds.shot(zombie, Wounds.RIGHT_LEG, 0.8f, 1.8f) == Wounds.WOUNDED, "a second in one limb wounds it");
        check(wounds.stage(zombie, Wounds.LEFT_LEG) == Wounds.SOUND, "and only it");
    }

    private static void eachZombieCountsItsOwnShots() {
        Wounds wounds = new Wounds();
        Object first = new Object(), second = new Object();
        wounds.shot(first, Wounds.LEFT_LEG, 1.2f, 1.8f);
        check(wounds.stage(first, Wounds.LEFT_LEG) == Wounds.WOUNDED, "a shot wounds one zombie's leg");
        check(wounds.stage(second, Wounds.LEFT_LEG) == Wounds.SOUND, "and not another's");
    }

    private static void aZombieIsForgottenWhenMadeAgain() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        check(!wounds.anyone(), "no zombie is on record at first");
        wounds.shot(zombie, Wounds.LEFT_LEG, 2f, 1.8f);
        check(wounds.remembered() == 1 && wounds.anyone(), "a zombie with a shot limb is on record, ruined or not");
        wounds.forget(zombie);
        check(wounds.remembered() == 0 && !wounds.anyone(), "and forgotten when the game makes a new zombie of its object");
        check(wounds.stage(zombie, Wounds.LEFT_LEG) == Wounds.SOUND, "the object made again starts sound");
    }

    private static void theLurchFollowsTheWoundedFoot() {
        float thigh = 0.4f, planted = WoundPose.PLANTED * thigh;
        check(WoundPose.stance(0f, 0f, thigh) == 0.5f, "both feet down: half the lurch");
        check(WoundPose.stance(0f, planted, thigh) == 1f, "the wounded foot down and the other up: all of it");
        check(WoundPose.stance(planted, 0f, thigh) == 0f, "the wounded foot up: none");
        check(WoundPose.stance(0f, 3f * planted, thigh) == 1f && WoundPose.stance(3f * planted, 0f, thigh) == 0f, "and no more or less than that");
    }
}

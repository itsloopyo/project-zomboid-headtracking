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
        Checks.run("aRuinedLimbShotAgainMayComeOff", WoundsTests::aRuinedLimbShotAgainMayComeOff);
        Checks.run("aHeavyShotMayTakeALimbOff", WoundsTests::aHeavyShotMayTakeALimbOff);
        Checks.run("thinnerPartsComeOffMoreReadily", WoundsTests::thinnerPartsComeOffMoreReadily);
        Checks.run("aBlastIsRolledForOnceALimb", WoundsTests::aBlastIsRolledForOnceALimb);
        Checks.run("aCutOnlyMovesUp", WoundsTests::aCutOnlyMovesUp);
        Checks.run("theCutsOfAZombieReadBackPartByPart", WoundsTests::theCutsOfAZombieReadBackPartByPart);
        Checks.run("theLurchFollowsTheWoundedFoot", WoundsTests::theLurchFollowsTheWoundedFoot);
    }

    private static int shots;

    /** A shot through a forearm or calf whose roll never takes it off. */
    private static int shot(Wounds wounds, Object zombie, int limb, float damage, float health) {
        return wounds.shot(zombie, limb, damage, health, health + damage, Wounds.LOWER, ++shots, 1f);
    }

    /** A shot of its own, on the given part of the limb, rolled as given. */
    private static int shot(Wounds wounds, Object zombie, int limb, float damage, float health, float before, int at, float roll) {
        return wounds.shot(zombie, limb, damage, health, before, at, ++shots, roll);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void aLimbIsWoundedAtHalfTheHealthLeftAndRuinedAtAll() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        check(shot(wounds, zombie, Wounds.LEFT_LEG, 0.8f, 1.8f) == Wounds.SOUND, "one pistol shot leaves a leg of a zombie with 1.8 health left sound");
        check(wounds.stage(zombie, Wounds.LEFT_LEG) == Wounds.SOUND, "and says so");
        check(shot(wounds, zombie, Wounds.LEFT_LEG, 0.8f, 1.75f) == Wounds.WOUNDED, "a second wounds it");
        check(wounds.stage(zombie, Wounds.LEFT_LEG) == Wounds.WOUNDED, "and it stays wounded");
        check(shot(wounds, zombie, Wounds.LEFT_LEG, 0.8f, 1.7f) == Wounds.RUINED, "a third ruins it");
        check(wounds.stage(zombie, Wounds.LEFT_LEG) == Wounds.RUINED, "and it stays ruined");
    }

    private static void oneShotCanRuinASoundLimb() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        check(shot(wounds, zombie, Wounds.RIGHT_ARM, 1.8f, 1.8f) == Wounds.RUINED, "a shot of all the health left ruins a sound arm at once");
    }

    private static void aLimbOnlyGetsWorse() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        shot(wounds, zombie, Wounds.LEFT_ARM, 1f, 1.8f);
        check(shot(wounds, zombie, Wounds.LEFT_ARM, 0.01f, 10f) == Wounds.SOUND, "more health later does not heal it, and the shot changes nothing");
        check(wounds.stage(zombie, Wounds.LEFT_ARM) == Wounds.WOUNDED, "it is still wounded");
    }

    private static void eachLimbCountsItsOwnShots() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        for (int limb = 0; limb < Wounds.LIMBS; limb++) {
            check(shot(wounds, zombie, limb, 0.8f, 1.8f) == Wounds.SOUND, "a shot in limb " + limb + " adds nothing to the others");
        }
        check(shot(wounds, zombie, Wounds.RIGHT_LEG, 0.8f, 1.8f) == Wounds.WOUNDED, "a second in one limb wounds it");
        check(wounds.stage(zombie, Wounds.LEFT_LEG) == Wounds.SOUND, "and only it");
    }

    private static void eachZombieCountsItsOwnShots() {
        Wounds wounds = new Wounds();
        Object first = new Object(), second = new Object();
        shot(wounds, first, Wounds.LEFT_LEG, 1.2f, 1.8f);
        check(wounds.stage(first, Wounds.LEFT_LEG) == Wounds.WOUNDED, "a shot wounds one zombie's leg");
        check(wounds.stage(second, Wounds.LEFT_LEG) == Wounds.SOUND, "and not another's");
    }

    private static void aZombieIsForgottenWhenMadeAgain() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        check(!wounds.anyone(), "no zombie is on record at first");
        shot(wounds, zombie, Wounds.LEFT_LEG, 2f, 1.8f);
        check(wounds.remembered() == 1 && wounds.anyone(), "a zombie with a shot limb is on record, ruined or not");
        wounds.forget(zombie);
        check(wounds.remembered() == 0 && !wounds.anyone(), "and forgotten when the game makes a new zombie of its object");
        check(wounds.stage(zombie, Wounds.LEFT_LEG) == Wounds.SOUND, "the object made again starts sound");
    }

    private static void aRuinedLimbShotAgainMayComeOff() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        check(!wounds.anyoneCut(), "nobody is cut at first");
        check(shot(wounds, zombie, Wounds.LEFT_ARM, 2f, 1.8f) == Wounds.RUINED, "one shot ruins the arm");
        check(wounds.cut(zombie, Wounds.LEFT_ARM) == Wounds.WHOLE, "and leaves it on");
        check(shot(wounds, zombie, Wounds.LEFT_ARM, 0.1f, 1.7f, 1.8f, Wounds.LOWER, Wounds.RUINED_CUTS[Wounds.LOWER] + 0.01f) == Wounds.SOUND,
                "the next shot in it leaves it on, rolled over the chance");
        check(shot(wounds, zombie, Wounds.LEFT_ARM, 0.1f, 1.6f, 1.7f, Wounds.LOWER, Wounds.RUINED_CUTS[Wounds.LOWER] - 0.01f) == Wounds.SEVERED,
                "and one rolled under it, however light, takes it off");
        check(wounds.cut(zombie, Wounds.LEFT_ARM) == Wounds.LOWER && wounds.anyoneCut(), "at the elbow, for a shot on the forearm");
        check(wounds.cut(zombie, Wounds.RIGHT_ARM) == Wounds.WHOLE, "and only that arm");
    }

    private static void aHeavyShotMayTakeALimbOff() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        float share = Wounds.SEVERING_SHARE[Wounds.UPPER];
        check(shot(wounds, zombie, Wounds.RIGHT_LEG, share * 1.8f - 0.1f, 1.7f, 1.8f, Wounds.UPPER, 0f) == Wounds.RUINED,
                "a shot under " + share + " times the health it had only ruins, however it is rolled");
        Object other = new Object();
        check(shot(wounds, other, Wounds.RIGHT_LEG, share * 1.8f, 1.7f, 1.8f, Wounds.UPPER, Wounds.HEAVY_CUTS[Wounds.UPPER] + 0.01f) == Wounds.RUINED,
                "one of that much rolled over the chance ruins");
        Object third = new Object();
        check(shot(wounds, third, Wounds.RIGHT_LEG, share * 1.8f, 1.7f, 1.8f, Wounds.UPPER, Wounds.HEAVY_CUTS[Wounds.UPPER] - 0.01f) == Wounds.SEVERED
                && wounds.cut(third, Wounds.RIGHT_LEG) == Wounds.UPPER, "and rolled under it takes the leg off, at the hip for a shot on the thigh");
        Object fourth = new Object();
        check(shot(wounds, fourth, Wounds.LEFT_ARM, 10f, 1f, 1.5f, Wounds.END, 0f) == Wounds.SEVERED && wounds.cut(fourth, Wounds.LEFT_ARM) == Wounds.END,
                "a shot on the hand takes it off at the wrist");
    }

    private static void thinnerPartsComeOffMoreReadily() {
        for (int at = Wounds.END; at < Wounds.UPPER; at++) {
            check(Wounds.HEAVY_CUTS[at] > Wounds.HEAVY_CUTS[at + 1] && Wounds.RUINED_CUTS[at] > Wounds.RUINED_CUTS[at + 1]
                    && Wounds.SEVERING_SHARE[at] < Wounds.SEVERING_SHARE[at + 1], "part " + at + " comes off more readily than the one above it");
        }
        check(Wounds.HEAVY_CUTS[Wounds.UPPER] <= 0.1f && Wounds.RUINED_CUTS[Wounds.UPPER] <= 0.05f, "an upper arm or a thigh seldom comes off");
    }

    private static void aBlastIsRolledForOnceALimb() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        int blast = 1000;
        check(wounds.shot(zombie, Wounds.LEFT_LEG, 60f, 0f, 2f, Wounds.LOWER, blast, 0.99f) == Wounds.RUINED, "a blast's first pellet in a calf rolls and leaves it on");
        check(wounds.shot(zombie, Wounds.LEFT_LEG, 60f, 0f, 0f, Wounds.LOWER, blast, 0f) == Wounds.SOUND && wounds.cut(zombie, Wounds.LEFT_LEG) == Wounds.WHOLE,
                "the next pellet of the same blast is not rolled for again");
        check(wounds.shot(zombie, Wounds.LEFT_LEG, 60f, 0f, 0f, Wounds.LOWER, blast + 1, 0f) == Wounds.SEVERED, "the next blast is");
    }

    private static void aCutOnlyMovesUp() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        shot(wounds, zombie, Wounds.LEFT_LEG, 4f, 1f, 1.5f, Wounds.LOWER, 0f);
        check(wounds.cut(zombie, Wounds.LEFT_LEG) == Wounds.LOWER, "cut at the knee");
        check(shot(wounds, zombie, Wounds.LEFT_LEG, 4f, 1f, 1.5f, Wounds.END, 0f) == Wounds.SOUND, "a shot below that changes nothing");
        check(wounds.cut(zombie, Wounds.LEFT_LEG) == Wounds.LOWER, "still at the knee");
        check(shot(wounds, zombie, Wounds.LEFT_LEG, 6f, 1f, 1.5f, Wounds.UPPER, 0f) == Wounds.SEVERED && wounds.cut(zombie, Wounds.LEFT_LEG) == Wounds.UPPER,
                "a leg cut at the knee and shot in the thigh is cut again at the hip");
    }

    private static void theCutsOfAZombieReadBackPartByPart() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        check(wounds.cuts(zombie) == 0, "nothing cut is nought");
        shot(wounds, zombie, Wounds.RIGHT_ARM, 4f, 1f, 1.5f, Wounds.END, 0f);
        shot(wounds, zombie, Wounds.LEFT_LEG, 6f, 1f, 1.5f, Wounds.UPPER, 0f);
        shot(wounds, zombie, Wounds.RIGHT_LEG, 4f, 1f, 1.5f, Wounds.LOWER, 0f);
        wounds.burst(zombie);
        int cuts = wounds.cuts(zombie);
        check(Wounds.cutIn(cuts, Wounds.LEFT_ARM) == Wounds.WHOLE && Wounds.cutIn(cuts, Wounds.RIGHT_ARM) == Wounds.END
                && Wounds.cutIn(cuts, Wounds.LEFT_LEG) == Wounds.UPPER && Wounds.cutIn(cuts, Wounds.RIGHT_LEG) == Wounds.LOWER
                && Wounds.cutIn(cuts, Wounds.HEAD) == Wounds.UPPER, "each part reads back as it was cut, the head burst: " + cuts);
        check(wounds.anyoneCut(), "and the zombie is cut");
    }

    private static void theLurchFollowsTheWoundedFoot() {
        float thigh = 0.4f, planted = WoundPose.PLANTED * thigh;
        check(WoundPose.stance(0f, 0f, thigh) == 0.5f, "both feet down: half the lurch");
        check(WoundPose.stance(0f, planted, thigh) == 1f, "the wounded foot down and the other up: all of it");
        check(WoundPose.stance(planted, 0f, thigh) == 0f, "the wounded foot up: none");
        check(WoundPose.stance(0f, 3f * planted, thigh) == 1f && WoundPose.stance(3f * planted, 0f, thigh) == 0f, "and no more or less than that");
    }
}

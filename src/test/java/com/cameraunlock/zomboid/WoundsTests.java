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
        Checks.run("aLimbComesOffOnceItsShotsAddUp", WoundsTests::aLimbComesOffOnceItsShotsAddUp);
        Checks.run("aHeavyShotTakesALimbOffAtOnce", WoundsTests::aHeavyShotTakesALimbOffAtOnce);
        Checks.run("thinnerPartsComeOffMoreReadily", WoundsTests::thinnerPartsComeOffMoreReadily);
        Checks.run("aCutOnlyMovesUp", WoundsTests::aCutOnlyMovesUp);
        Checks.run("theCutsOfAZombieReadBackPartByPart", WoundsTests::theCutsOfAZombieReadBackPartByPart);
        Checks.run("theLurchFollowsTheWoundedFoot", WoundsTests::theLurchFollowsTheWoundedFoot);
        Checks.run("aKillingShotTakesALimbOffOnlyPastAWholeZombiesShareAndARoll", WoundsTests::aKillingShotTakesALimbOffOnlyPastAWholeZombiesShareAndARoll);
        Checks.run("aZombieKilledByABlastLosesEveryLimbItCutsOnOneRoll", WoundsTests::aZombieKilledByABlastLosesEveryLimbItCutsOnOneRoll);
    }

    /** A shot through a forearm or calf. */
    private static int shot(Wounds wounds, Object zombie, int limb, float damage, float health) {
        return wounds.shot(zombie, limb, damage, health, Wounds.LOWER, false);
    }

    /** A shot on the given part of the limb. */
    private static int shot(Wounds wounds, Object zombie, int limb, float damage, float health, int at) {
        return wounds.shot(zombie, limb, damage, health, at, false);
    }

    private static void aKillingShotTakesALimbOffOnlyPastAWholeZombiesShareAndARoll() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        check(wounds.shot(zombie, Wounds.LEFT_ARM, 1.4f, 0f, Wounds.END, true) != Wounds.SEVERED, "a pistol round that kills a zombie at its last hit point leaves its hand on, roll or no");
        Object other = new Object();
        float heavy = Wounds.WHOLE_HEALTH * Wounds.SEVERING_SHARE[Wounds.LOWER];
        check(wounds.shot(other, Wounds.RIGHT_LEG, heavy, 0f, Wounds.LOWER, false) != Wounds.SEVERED, "a killing shot heavy enough to take a whole zombie's calf off leaves it on when the roll fails");
        Object lucky = new Object();
        check(wounds.shot(lucky, Wounds.RIGHT_LEG, heavy, 0f, Wounds.LOWER, true) == Wounds.SEVERED, "and takes it off when the roll passes");
        Object living = new Object();
        check(wounds.shot(living, Wounds.RIGHT_LEG, heavy, 0.5f, Wounds.LOWER, false) == Wounds.SEVERED, "a shot that leaves the zombie alive goes by its health left, with no roll");
    }

    private static void aZombieKilledByABlastLosesEveryLimbItCutsOnOneRoll() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        int cut = 0;
        // A shotgun's pellets each strike a limb, the first killing it and the rest after: the death is rolled once.
        for (int pellet = 0; pellet < 8; pellet++) {
            cut += wounds.shot(zombie, pellet % Wounds.LIMBS, 80f, 0f, Wounds.LOWER, true) == Wounds.SEVERED ? 1 : 0;
        }
        check(cut == Wounds.LIMBS, "a blast that kills a zombie takes off every limb its pellets would off a whole zombie: " + cut);
        Object spared = new Object();
        int none = wounds.shot(spared, Wounds.LEFT_LEG, 80f, 0f, Wounds.LOWER, false) == Wounds.SEVERED ? 1 : 0;
        for (int pellet = 1; pellet < 8; pellet++) {
            none += wounds.shot(spared, pellet % Wounds.LIMBS, 80f, 0f, Wounds.LOWER, true) == Wounds.SEVERED ? 1 : 0;
        }
        check(none == 0, "and none when its death's roll failed, whatever the later pellets roll: " + none);
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

    private static void aLimbComesOffOnceItsShotsAddUp() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        check(!wounds.anyoneCut(), "nobody is cut at first");
        check(shot(wounds, zombie, Wounds.LEFT_LEG, 2f, 1.8f) == Wounds.RUINED, "one rifle round ruins the leg");
        check(shot(wounds, zombie, Wounds.LEFT_LEG, 2f, 1.75f) == Wounds.SOUND && shot(wounds, zombie, Wounds.LEFT_LEG, 2f, 1.7f) == Wounds.SOUND,
                "two more leave it on");
        check(wounds.cut(zombie, Wounds.LEFT_LEG) == Wounds.WHOLE && !wounds.anyoneCut(), "still whole");
        check(shot(wounds, zombie, Wounds.LEFT_LEG, 2f, 1.65f) == Wounds.SEVERED, "the fourth, past " + Wounds.SEVERING_SHARE[Wounds.LOWER] + " times the health left, takes it off");
        check(wounds.cut(zombie, Wounds.LEFT_LEG) == Wounds.LOWER && wounds.anyoneCut(), "at the knee, for a shot on the calf");
        check(wounds.cut(zombie, Wounds.RIGHT_LEG) == Wounds.WHOLE, "and only that leg");
    }

    private static void aHeavyShotTakesALimbOffAtOnce() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        float share = Wounds.SEVERING_SHARE[Wounds.UPPER];
        check(shot(wounds, zombie, Wounds.RIGHT_LEG, share * 1.8f - 0.1f, 1.8f, Wounds.UPPER) == Wounds.RUINED,
                "a shot under " + share + " times the health left only ruins");
        Object other = new Object();
        check(shot(wounds, other, Wounds.RIGHT_LEG, share * 1.8f, 1.8f, Wounds.UPPER) == Wounds.SEVERED
                && wounds.cut(other, Wounds.RIGHT_LEG) == Wounds.UPPER, "one of that much takes the leg off, at the hip for a shot on the thigh");
        Object third = new Object();
        check(shot(wounds, third, Wounds.LEFT_ARM, 10f, 1f, Wounds.END) == Wounds.SEVERED && wounds.cut(third, Wounds.LEFT_ARM) == Wounds.END,
                "a shot on the hand takes it off at the wrist");
        check(shot(wounds, new Object(), Wounds.LEFT_LEG, 1.6f * 4f, 1.9f, Wounds.LOWER) == Wounds.RUINED,
                "an M16's heaviest crit only ruins a calf of a zombie at its full 1.9");
    }

    private static void thinnerPartsComeOffMoreReadily() {
        for (int at = Wounds.END; at < Wounds.UPPER; at++) {
            check(Wounds.SEVERING_SHARE[at] < Wounds.SEVERING_SHARE[at + 1], "part " + at + " comes off for less than the one above it");
        }
    }

    private static void aCutOnlyMovesUp() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        shot(wounds, zombie, Wounds.LEFT_LEG, 4f, 1f, Wounds.LOWER);
        check(wounds.cut(zombie, Wounds.LEFT_LEG) == Wounds.LOWER, "cut at the knee");
        check(shot(wounds, zombie, Wounds.LEFT_LEG, 4f, 1f, Wounds.END) == Wounds.SOUND, "a shot below that changes nothing");
        check(wounds.cut(zombie, Wounds.LEFT_LEG) == Wounds.LOWER, "still at the knee");
        check(shot(wounds, zombie, Wounds.LEFT_LEG, 6f, 1f, Wounds.UPPER) == Wounds.SEVERED && wounds.cut(zombie, Wounds.LEFT_LEG) == Wounds.UPPER,
                "a leg cut at the knee and shot in the thigh is cut again at the hip");
    }

    private static void theCutsOfAZombieReadBackPartByPart() {
        Wounds wounds = new Wounds();
        Object zombie = new Object();
        check(wounds.cuts(zombie) == 0, "nothing cut is nought");
        shot(wounds, zombie, Wounds.RIGHT_ARM, 4f, 1f, Wounds.END);
        shot(wounds, zombie, Wounds.LEFT_LEG, 6f, 1f, Wounds.UPPER);
        shot(wounds, zombie, Wounds.RIGHT_LEG, 4f, 1f, Wounds.LOWER);
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

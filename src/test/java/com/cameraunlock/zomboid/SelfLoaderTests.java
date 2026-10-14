package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of how the self-loading rifles' bolts move, on SelfLoading.Slide with a rifle's timings. */
final class SelfLoaderTests {
    private SelfLoaderTests() {}

    private static final float FRAME = 1f / 60f;

    static void run() {
        Checks.run("theBoltIsHomeBeforeAnAutomaticsNextShot", SelfLoaderTests::theBoltIsHomeBeforeAnAutomaticsNextShot);
        Checks.run("everyShotOfABurstThrowsACase", SelfLoaderTests::everyShotOfABurstThrowsACase);
        Checks.run("theLastRoundLeavesTheBoltBackUntilOneIsChambered", SelfLoaderTests::theLastRoundLeavesTheBoltBackUntilOneIsChambered);
        Checks.run("twoShotsInOneFrameThrowTwoCases", SelfLoaderTests::twoShotsInOneFrameThrowTwoCases);
        Checks.run("shotsTakenWhileNotWorkedAreNotWorkedLater", SelfLoaderTests::shotsTakenWhileNotWorkedAreNotWorkedLater);
        Checks.run("aRackLetGoOverAnEmptyMagazineLeavesTheBoltBack", SelfLoaderTests::aRackLetGoOverAnEmptyMagazineLeavesTheBoltBack);
        Checks.run("anotherRifleInHandHasItsOwnBolt", SelfLoaderTests::anotherRifleInHandHasItsOwnBolt);
    }

    private static SelfLoading.Slide bolt() {
        return new SelfLoading.Slide(OwnSelfLoader.BACK_IN, 0f, OwnSelfLoader.HOME_IN);
    }

    private static void theBoltIsHomeBeforeAnAutomaticsNextShot() {
        float home = OwnSelfLoader.BACK_IN + OwnSelfLoader.HOME_IN;
        check(home < 0.08f, "a shot's cycle is over before the next round of a burst, 0.08 s on: " + home);
        check(Math.abs(SelfLoading.cycledAt(OwnSelfLoader.BACK_IN, OwnSelfLoader.BACK_IN, 0f, OwnSelfLoader.HOME_IN) - 1f) < 1e-6f, "right back at BACK_IN");
        check(SelfLoading.cycledAt(home, OwnSelfLoader.BACK_IN, 0f, OwnSelfLoader.HOME_IN) == 0f, "and home again after");
    }

    /** Thirty shots 0.08 s apart, frames of 1/60 s: as many cases as shots, and the bolt home between them. */
    private static void everyShotOfABurstThrowsACase() {
        SelfLoading.Slide bolt = bolt();
        bolt.step(1, true, 0, true, 30, true, ArmActs.HOLDING, 0f, FRAME);
        int shots = 0, thrown = 0;
        float time = 0f, next = 0f, least = 1f, lastShot = 0f;
        for (int frame = 0; frame < 200; frame++) {
            time += FRAME;
            if (shots < 30 && time >= next) {
                shots++;
                next += 0.08f;
                lastShot = time;
            }
            bolt.step(1, true, shots, true, 30 - shots, true, ArmActs.HOLDING, 0f, FRAME);
            thrown += bolt.thrown;
            // A frame of 1/60 s a full cycle after a shot, before the next.
            if (shots > 0 && time - lastShot >= OwnSelfLoader.BACK_IN + OwnSelfLoader.HOME_IN + 1e-4f && bolt.thrown == 0) {
                least = Math.min(least, 1f - bolt.back);
            }
        }
        check(thrown == shots, "a case for every shot: " + thrown + " of " + shots);
        check(least > 0.99f, "and the bolt home a full cycle after each shot, before the next: " + least);
    }

    private static void twoShotsInOneFrameThrowTwoCases() {
        SelfLoading.Slide bolt = bolt();
        bolt.step(1, true, 0, true, 30, true, ArmActs.HOLDING, 0f, FRAME);
        int thrown = 0;
        bolt.step(1, true, 2, true, 28, true, ArmActs.HOLDING, 0f, FRAME);
        thrown += bolt.thrown;
        for (int frame = 0; frame < 10; frame++) {
            bolt.step(1, true, 2, true, 28, true, ArmActs.HOLDING, 0f, FRAME);
            thrown += bolt.thrown;
        }
        check(thrown == 2, "two shots between frames throw two cases: " + thrown);
        // A third before the second's case has left: that case and the third's.
        bolt.step(1, true, 3, true, 27, true, ArmActs.HOLDING, 0f, 0.001f);
        thrown = bolt.thrown;
        bolt.step(1, true, 4, true, 26, true, ArmActs.HOLDING, 0f, 0.001f);
        thrown += bolt.thrown;
        for (int frame = 0; frame < 10; frame++) {
            bolt.step(1, true, 4, true, 26, true, ArmActs.HOLDING, 0f, FRAME);
            thrown += bolt.thrown;
        }
        check(thrown == 2, "and a shot that comes before the last one's case has left throws that case too: " + thrown);
    }

    private static void shotsTakenWhileNotWorkedAreNotWorkedLater() {
        SelfLoading.Slide bolt = bolt();
        bolt.step(1, true, 0, true, 30, true, ArmActs.HOLDING, 0f, FRAME);
        bolt.step(1, false, 5, true, 25, true, ArmActs.HOLDING, 0f, FRAME);
        check(bolt.thrown == 0 && bolt.back == 0f, "seen again after shots it was not there for, it works none of them: " + bolt.thrown);
    }

    private static void aRackLetGoOverAnEmptyMagazineLeavesTheBoltBack() {
        for (int rounds : new int[] {0, 30}) {
            SelfLoading.Slide bolt = bolt();
            bolt.step(1, true, 0, false, rounds, true, ArmActs.HOLDING, 0f, FRAME);
            float through = 0f;
            for (int frame = 0; frame < 60; frame++) {
                through += 1f / 60f;
                bolt.step(1, true, 0, false, rounds, true, ArmActs.RACKING, through, FRAME);
            }
            if (rounds == 0) {
                check(bolt.back == 1f && bolt.locked(), "a rack let go over an empty magazine leaves the bolt back: " + bolt.back);
            } else {
                bolt.step(1, true, 0, true, rounds - 1, true, ArmActs.HOLDING, 0f, FRAME);
                check(bolt.back == 0f && !bolt.locked(), "and over a loaded one it goes home with a round: " + bolt.back);
            }
        }
    }

    private static void anotherRifleInHandHasItsOwnBolt() {
        SelfLoading.Slide bolt = bolt();
        bolt.step(1, true, 0, true, 0, true, ArmActs.HOLDING, 0f, FRAME);
        for (int frame = 0; frame < 30; frame++) {
            bolt.step(1, true, 1, false, 0, true, ArmActs.HOLDING, 0f, FRAME);
        }
        check(bolt.back == 1f, "emptied, its bolt is back");
        bolt.step(2, true, 1, true, 20, true, ArmActs.HOLDING, 0f, FRAME);
        check(bolt.back == 0f && bolt.thrown == 0, "another rifle taken in hand loaded has its bolt home: " + bolt.back);
    }

    private static void theLastRoundLeavesTheBoltBackUntilOneIsChambered() {
        SelfLoading.Slide bolt = bolt();
        bolt.step(1, true, 0, true, 0, true, ArmActs.HOLDING, 0f, FRAME);
        for (int frame = 0; frame < 30; frame++) {
            bolt.step(1, true, 1, false, 0, true, ArmActs.HOLDING, 0f, FRAME);
        }
        check(bolt.back == 1f && bolt.locked(), "the shot that empties it leaves the bolt back: " + bolt.back);
        for (int frame = 0; frame < 30; frame++) {
            bolt.step(1, true, 1, false, 30, true, ArmActs.HOLDING, 0f, FRAME);
        }
        check(bolt.back == 1f, "a full magazine pushed home leaves it back: " + bolt.back);
        bolt.step(1, true, 1, true, 29, true, ArmActs.HOLDING, 0f, FRAME);
        check(bolt.back == 0f && !bolt.locked(), "a round chambered and it is home: " + bolt.back);
    }
}

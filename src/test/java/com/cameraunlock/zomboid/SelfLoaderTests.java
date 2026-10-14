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
            thrown += bolt.thrown ? 1 : 0;
            // A frame of 1/60 s a full cycle after a shot, before the next.
            if (shots > 0 && time - lastShot >= OwnSelfLoader.BACK_IN + OwnSelfLoader.HOME_IN + 1e-4f && !bolt.thrown) {
                least = Math.min(least, 1f - bolt.back);
            }
        }
        check(thrown == shots, "a case for every shot: " + thrown + " of " + shots);
        check(least > 0.99f, "and the bolt home a full cycle after each shot, before the next: " + least);
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

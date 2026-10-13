package com.cameraunlock.zomboid;

/** Tests of how the mod's own pistols work their slides. */
final class PistolTests {
    private PistolTests() {}

    private static final float FRAME = 1f / 60f;
    private static final int GUN = 101;

    static void run() {
        Checks.run("slideGoesRightBackOnceAShotAndIsHomeAfter", PistolTests::slideGoesRightBackOnceAShotAndIsHomeAfter);
        Checks.run("aShotWithRoundsLeftThrowsOneCaseAndLetsTheSlideHome", PistolTests::aShotWithRoundsLeftThrowsOneCaseAndLetsTheSlideHome);
        Checks.run("theLastRoundLeavesTheSlideBackThroughAChangeOfMagazineUntilRacked", PistolTests::theLastRoundLeavesTheSlideBackThroughAChangeOfMagazineUntilRacked);
        Checks.run("anEmptyPistolTakenInHandHasItsSlideBack", PistolTests::anEmptyPistolTakenInHandHasItsSlideBack);
        Checks.run("rackingDrawsTheSlideBackAndLetsItGo", PistolTests::rackingDrawsTheSlideBackAndLetsItGo);
    }

    private static void slideGoesRightBackOnceAShotAndIsHomeAfter() {
        check(OwnPistol.slideAt(0f) == 0f, "the slide is home at the shot");
        check(Math.abs(OwnPistol.slideAt(OwnPistol.CYCLES_FOR)) < 1e-6f, "and home again once the shot is over");
        float most = 0f, before = 0f;
        int rises = 0;
        boolean rising = false;
        for (float seconds = 0f; seconds < 0.5f; seconds += 0.001f) {
            float back = OwnPistol.slideAt(seconds);
            check(back >= -1e-6f && back <= 1f + 1e-6f, "the slide stays within its travel at " + seconds + ": " + back);
            most = Math.max(most, back);
            if (back > before + 1e-6f && !rising) {
                rises++;
                rising = true;
            } else if (back < before - 1e-6f) {
                rising = false;
            }
            before = back;
        }
        check(most == 1f, "it comes right back, not " + most);
        check(rises == 1, "and once, not " + rises + " times");
    }

    private static void aShotWithRoundsLeftThrowsOneCaseAndLetsTheSlideHome() {
        OwnPistol.Slide slide = new OwnPistol.Slide();
        slide.step(GUN, 3, true, 14, true, ArmActs.HOLDING, 0f, FRAME);
        check(slide.back == 0f, "the slide is home with the pistol held ready");
        int thrown = 0;
        float most = 0f;
        // The shot: the game chambers the next round from the magazine.
        for (int frame = 0; frame < 60; frame++) {
            slide.step(GUN, 4, true, 13, true, ArmActs.HOLDING, 0f, FRAME);
            thrown += slide.thrown ? 1 : 0;
            most = Math.max(most, slide.back);
        }
        check(most > 0.9f, "the shot drives the slide back, " + most + " of its travel");
        check(thrown == 1, "one case is thrown out, not " + thrown);
        check(slide.back == 0f, "and a second after it the slide is home");
    }

    private static void theLastRoundLeavesTheSlideBackThroughAChangeOfMagazineUntilRacked() {
        OwnPistol.Slide slide = new OwnPistol.Slide();
        // One round in the chamber and none in the magazine.
        slide.step(GUN, 7, true, 0, true, ArmActs.HOLDING, 0f, FRAME);
        for (int frame = 0; frame < 120; frame++) {
            slide.step(GUN, 8, false, 0, true, ArmActs.HOLDING, 0f, FRAME);
        }
        check(slide.back == 1f, "the last round leaves the slide back, not " + slide.back);
        // The empty magazine out, and a full one in.
        for (int frame = 0; frame < 60; frame++) {
            slide.step(GUN, 8, false, 0, frame < 30, ArmActs.UNLOADING, frame / 60f, FRAME);
        }
        check(slide.back == 1f, "it stays back with the magazine out, not " + slide.back);
        for (int frame = 0; frame < 60; frame++) {
            slide.step(GUN, 8, false, frame < 50 ? 0 : 15, frame >= 50, ArmActs.LOADING, frame / 60f, FRAME);
        }
        check(slide.back == 1f, "and with a full one in and nothing chambered, not " + slide.back);
        float least = 1f;
        for (int frame = 0; frame <= 60; frame++) {
            float through = frame / 60f;
            // The game chambers a round as the hand lets the slide go.
            slide.step(GUN, 8, through >= ArmHolds.SLIDE_LET_GO, through >= ArmHolds.SLIDE_LET_GO ? 14 : 15, true, ArmActs.RACKING, through, FRAME);
            if (through < ArmHolds.SLIDE_LET_GO) {
                check(slide.back == 1f, "racked, it is held back until the hand lets it go: " + slide.back + " at " + through);
            }
            least = Math.min(least, slide.back);
        }
        check(least < 1e-3f && slide.back < 1e-3f, "let go, it runs home: " + slide.back);
        for (int frame = 0; frame < 30; frame++) {
            slide.step(GUN, 8, true, 14, true, ArmActs.HOLDING, 0f, FRAME);
        }
        check(slide.back == 0f, "and stays there, not " + slide.back);
    }

    private static void anEmptyPistolTakenInHandHasItsSlideBack() {
        OwnPistol.Slide slide = new OwnPistol.Slide();
        slide.step(GUN, 2, false, 0, true, ArmActs.HOLDING, 0f, FRAME);
        check(slide.back == 1f, "an empty magazine in and nothing chambered: back, not " + slide.back);
        slide.step(102, 2, true, 7, true, ArmActs.HOLDING, 0f, FRAME);
        check(slide.back == 0f, "another, loaded, taken in hand: home, not " + slide.back);
        slide.step(GUN, 2, false, 0, false, ArmActs.HOLDING, 0f, FRAME);
        check(slide.back == 0f, "an empty pistol with no magazine in it: home, not " + slide.back);
    }

    private static void rackingDrawsTheSlideBackAndLetsItGo() {
        OwnPistol.Slide slide = new OwnPistol.Slide();
        float most = 0f;
        for (int frame = 0; frame <= 60; frame++) {
            slide.step(GUN, 0, true, 15, true, ArmActs.RACKING, frame / 60f, FRAME);
            most = Math.max(most, slide.back);
        }
        check(most > 0.99f, "racking draws the slide right back, " + most);
        check(slide.back < 1e-3f, "and lets it home, " + slide.back);
        check(!slide.thrown, "with no case thrown");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}

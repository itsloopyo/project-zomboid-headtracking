package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;

/** Tests of how the mod's own pistols work their slides, the size they are drawn at, and how far the eye leans in to them. */
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
        Checks.run("rackingOverAnEmptyMagazineLeavesTheSlideBack", PistolTests::rackingOverAnEmptyMagazineLeavesTheSlideBack);
        Checks.run("theLastRoundWithNoMagazineInLetsTheSlideHome", PistolTests::theLastRoundWithNoMagazineInLetsTheSlideHome);
        Checks.run("shotsTakenWhileThePistolWasNotWorkedAreNotWorkedAfter", PistolTests::shotsTakenWhileThePistolWasNotWorkedAreNotWorkedAfter);
        Checks.run("theMagazineIsInItsWellUnlessTheHandHasIt", PistolTests::theMagazineIsInItsWellUnlessTheHandHasIt);
        Checks.run("anOwnHandgunIsDrawnAtTheLongGunsSize", PistolTests::anOwnHandgunIsDrawnAtTheLongGunsSize);
        Checks.run("eachOwnHandgunStopsTheEyeShortOfItsOwnRearSight", PistolTests::eachOwnHandgunStopsTheEyeShortOfItsOwnRearSight);
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
        slide.step(GUN, true, 3, true, 14, true, ArmActs.HOLDING, 0f, FRAME);
        check(slide.back == 0f, "the slide is home with the pistol held ready");
        int thrown = 0;
        float most = 0f;
        // The shot: the game chambers the next round from the magazine.
        for (int frame = 0; frame < 60; frame++) {
            slide.step(GUN, true, 4, true, 13, true, ArmActs.HOLDING, 0f, FRAME);
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
        slide.step(GUN, true, 7, true, 0, true, ArmActs.HOLDING, 0f, FRAME);
        for (int frame = 0; frame < 120; frame++) {
            slide.step(GUN, true, 8, false, 0, true, ArmActs.HOLDING, 0f, FRAME);
        }
        check(slide.back == 1f, "the last round leaves the slide back, not " + slide.back);
        // The empty magazine out, and a full one in.
        for (int frame = 0; frame < 60; frame++) {
            slide.step(GUN, true, 8, false, 0, frame < 30, ArmActs.UNLOADING, frame / 60f, FRAME);
        }
        check(slide.back == 1f, "it stays back with the magazine out, not " + slide.back);
        for (int frame = 0; frame < 60; frame++) {
            slide.step(GUN, true, 8, false, frame < 50 ? 0 : 15, frame >= 50, ArmActs.LOADING, frame / 60f, FRAME);
        }
        check(slide.back == 1f, "and with a full one in and nothing chambered, not " + slide.back);
        float least = 1f;
        for (int frame = 0; frame <= 60; frame++) {
            float through = frame / 60f;
            // The game chambers a round as the hand lets the slide go.
            slide.step(GUN, true, 8, through >= ArmHolds.SLIDE_LET_GO, through >= ArmHolds.SLIDE_LET_GO ? 14 : 15, true, ArmActs.RACKING, through, FRAME);
            if (through < ArmHolds.SLIDE_LET_GO) {
                check(slide.back == 1f, "racked, it is held back until the hand lets it go: " + slide.back + " at " + through);
            }
            least = Math.min(least, slide.back);
        }
        check(least < 1e-3f && slide.back < 1e-3f, "let go, it runs home: " + slide.back);
        for (int frame = 0; frame < 30; frame++) {
            slide.step(GUN, true, 8, true, 14, true, ArmActs.HOLDING, 0f, FRAME);
        }
        check(slide.back == 0f, "and stays there, not " + slide.back);
    }

    private static void anEmptyPistolTakenInHandHasItsSlideBack() {
        OwnPistol.Slide slide = new OwnPistol.Slide();
        slide.step(GUN, true, 2, false, 0, true, ArmActs.HOLDING, 0f, FRAME);
        check(slide.back == 1f, "an empty magazine in and nothing chambered: back, not " + slide.back);
        slide.step(102, true, 2, true, 7, true, ArmActs.HOLDING, 0f, FRAME);
        check(slide.back == 0f, "another, loaded, taken in hand: home, not " + slide.back);
        slide.step(GUN, true, 2, false, 0, false, ArmActs.HOLDING, 0f, FRAME);
        check(slide.back == 0f, "an empty pistol with no magazine in it: home, not " + slide.back);
    }

    private static void rackingDrawsTheSlideBackAndLetsItGo() {
        OwnPistol.Slide slide = new OwnPistol.Slide();
        float most = 0f;
        for (int frame = 0; frame <= 60; frame++) {
            slide.step(GUN, true, 0, true, 15, true, ArmActs.RACKING, frame / 60f, FRAME);
            most = Math.max(most, slide.back);
        }
        check(most > 0.99f, "racking draws the slide right back, " + most);
        check(slide.back < 1e-3f, "and lets it home, " + slide.back);
        check(!slide.thrown, "with no case thrown");
    }

    private static void rackingOverAnEmptyMagazineLeavesTheSlideBack() {
        OwnPistol.Slide slide = new OwnPistol.Slide();
        float most = 0f;
        for (int frame = 0; frame <= 60; frame++) {
            float through = frame / 60f;
            slide.step(GUN, true, 0, false, 0, true, ArmActs.RACKING, through, FRAME);
            if (through >= ArmHolds.SLIDE_LET_GO) {
                most = Math.max(most, 1f - slide.back);
            }
        }
        check(most == 0f && slide.back == 1f, "racked over an empty magazine, the slide stays back once let go: " + slide.back);
        for (int frame = 0; frame <= 60; frame++) {
            slide.step(GUN, true, 0, false, 0, true, ArmActs.HOLDING, 0f, FRAME);
        }
        check(slide.back == 1f, "and after: " + slide.back);
    }

    private static void theLastRoundWithNoMagazineInLetsTheSlideHome() {
        OwnPistol.Slide slide = new OwnPistol.Slide();
        slide.step(GUN, true, 1, true, 0, false, ArmActs.HOLDING, 0f, FRAME);
        float most = 0f;
        for (int frame = 0; frame < 60; frame++) {
            slide.step(GUN, true, 2, false, 0, false, ArmActs.HOLDING, 0f, FRAME);
            most = Math.max(most, slide.back);
        }
        check(most > 0.9f && slide.back == 0f, "with no magazine to hold it, the slide goes back and home: most " + most + ", now " + slide.back);
    }

    private static void shotsTakenWhileThePistolWasNotWorkedAreNotWorkedAfter() {
        OwnPistol.Slide slide = new OwnPistol.Slide();
        slide.step(GUN, true, 5, true, 10, true, ArmActs.HOLDING, 0f, FRAME);
        // Two shots with another weapon, or with this one not drawn: the count moved on while this slide was not.
        boolean thrown = false;
        float most = 0f;
        slide.step(GUN, false, 7, true, 10, true, ArmActs.HOLDING, 0f, FRAME);
        for (int frame = 0; frame < 60; frame++) {
            thrown |= slide.thrown;
            most = Math.max(most, slide.back);
            slide.step(GUN, true, 7, true, 10, true, ArmActs.HOLDING, 0f, FRAME);
        }
        check(!thrown && most == 0f, "seen again after a gap, the slide does not work a shot it did not see: thrown " + thrown + ", most " + most);
        slide.step(GUN, false, 9, false, 0, true, ArmActs.HOLDING, 0f, FRAME);
        check(slide.back == 1f && !slide.thrown, "seen again emptied, it is held back as the game has it, throwing nothing: " + slide.back);
    }

    private static void theMagazineIsInItsWellUnlessTheHandHasIt() {
        check(OwnPistol.magazineIn(true, ArmActs.HOLDING, -1f), "held, with one in: in its well");
        check(!OwnPistol.magazineIn(false, ArmActs.HOLDING, -1f), "with none in: not");
        check(OwnPistol.magazineIn(true, ArmActs.UNLOADING, -1f), "being unloaded, until the game has it out: in");
        check(!OwnPistol.magazineIn(false, ArmActs.LOADING, -1f), "being loaded, until the game has it in: in the hand");
        check(!OwnPistol.magazineIn(true, ArmActs.HOLDING, ArmActs.HOME_IN / 2f), "pushed home: the hand's, as ArmHolds.carries has it");
        check(OwnPistol.magazineIn(true, ArmActs.HOLDING, ArmActs.HOME_IN + 0.01f), "and the well's once home");
        check(OwnPistol.magazineIn(true, ArmActs.LOADING, 0f), "a magazine the game has in while loading is the well's");
    }

    private static void anOwnHandgunIsDrawnAtTheLongGunsSize() {
        for (float[] gun : new float[][] {{0.204f, 0.1470f}, {0.203f, 0.1450f}, {0.253f, 0.1483f}, {0.179f, 0.1474f}}) {
            float fits = ViewModel.ownHandgunFits(gun[0], gun[1]);
            // A metre of the model is gameMuzzle * fits / muzzleFar of the game's units, drawn at the character's scale.
            float perMetre = gun[1] * fits / gun[0];
            check(Math.abs(perMetre - Characters.PER_METRE) < 1e-6f, "a metre of a handgun of " + gun[0] + " m over a game model of " + gun[1] + " is " + perMetre
                    + " of the game's units, the long guns' " + Characters.PER_METRE);
        }
    }

    private static void eachOwnHandgunStopsTheEyeShortOfItsOwnRearSight() throws IOException {
        float tiles = Characters.PER_METRE * Characters.CHARACTER_SCALE;
        // With the eye and the rear sight where the game's handguns have them, the stop is theirs.
        check(Math.abs(ViewModel.handgunStop(ViewModel.AIMED_AHEAD / tiles, 0f, tiles) - ViewModel.FORWARD_STOP) < 1e-6f, "the stock eye and sight give FORWARD_STOP");
        for (OwnModel model : OwnModel.ALL) {
            if (!model.handgun) {
                continue;
            }
            ByteBuffer read;
            try (InputStream packed = new GZIPInputStream(PistolTests.class.getResourceAsStream(model.mesh))) {
                read = ByteBuffer.wrap(packed.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            }
            OwnModel.Layout layout = OwnModel.layout(read, model.mesh);
            float rear = OwnModel.rearSight(layout.places(read), layout.muzzle(), model.top());
            check(rear > -0.06f && rear < 0.06f, model.what + "'s rear sight is within a hand of its grip along the bore: " + rear);
            float stop = ViewModel.handgunStop(layout.eyeBehind(), rear, tiles);
            check(stop > 0f && stop < (layout.eyeBehind() + rear) * tiles, model.what + " stops the eye short of its rear sight: " + stop + " of "
                    + (layout.eyeBehind() + rear) * tiles + " tiles");
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}

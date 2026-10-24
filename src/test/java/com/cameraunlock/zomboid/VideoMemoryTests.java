package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** What the graphics card's memory lets each graphics mode draw, and the skins hold. */
final class VideoMemoryTests {
    private VideoMemoryTests() {}

    static void run() {
        Checks.run("cardsStartAtTheirSizesStep", VideoMemoryTests::cardsStartAtTheirSizesStep);
        Checks.run("eachStepNeedsNoMoreThanTheOneBefore", VideoMemoryTests::eachStepNeedsNoMoreThanTheOneBefore);
        Checks.run("oneShortageIsOneStep", VideoMemoryTests::oneShortageIsOneStep);
        Checks.run("aBigCardDrawsEveryModeAsItIs", VideoMemoryTests::aBigCardDrawsEveryModeAsItIs);
        Checks.run("eachStepGivesUpWhatItSays", VideoMemoryTests::eachStepGivesUpWhatItSays);
        Checks.run("noStepDrawsMoreThanTheOneBefore", VideoMemoryTests::noStepDrawsMoreThanTheOneBefore);
        Checks.run("theSkinsHoldLessOnSmallerCards", VideoMemoryTests::theSkinsHoldLessOnSmallerCards);
    }

    private static final Quality.Picture[] MODES = {Quality.HIGH_PICTURE, Quality.MEDIUM_PICTURE, Quality.LOW_PICTURE};

    private static void cardsStartAtTheirSizesStep() {
        check(VideoMemory.stepFor(24576) == 0, "a 24 GB card gives up nothing");
        check(VideoMemory.stepFor(16384) == 0, "a 16 GB card gives up nothing");
        check(VideoMemory.stepFor(8192) == 0, "an 8 GB card gives up nothing");
        check(VideoMemory.stepFor(6141) == 0, "a 6 GB card that says a few MB less gives up nothing: " + VideoMemory.stepFor(6141));
        check(VideoMemory.stepFor(5120) == 3, "a 5 GB card starts at step 3: " + VideoMemory.stepFor(5120));
        check(VideoMemory.stepFor(4093) == VideoMemory.LAST_STEP, "a 4 GB card starts at the last step: " + VideoMemory.stepFor(4093));
        check(VideoMemory.stepFor(2048) == VideoMemory.LAST_STEP, "a 2 GB card starts at the last step");
        int[] need = VideoMemory.NEED_MB;
        int beside = VideoMemory.GAME_MB + VideoMemory.OTHERS_MB;
        for (int at = 0; at < VideoMemory.LAST_STEP; at++) {
            int first = at;
            while (first > 0 && need[first - 1] == need[at]) {
                first--;
            }
            check(VideoMemory.stepFor(need[at] + beside) == first, "a card of step " + at + "'s need beside the game and others starts at step " + first);
            check(VideoMemory.stepFor(need[at] + beside - 1) > at, "a card a MB smaller starts past step " + at);
        }
    }

    private static void eachStepNeedsNoMoreThanTheOneBefore() {
        for (int at = 1; at <= VideoMemory.LAST_STEP; at++) {
            check(VideoMemory.NEED_MB[at] <= VideoMemory.NEED_MB[at - 1], "step " + at + " needs no more than step " + (at - 1));
        }
    }

    private static void oneShortageIsOneStep() {
        long settles = VideoMemory.SETTLES_NANOS;
        check(VideoMemory.room(0, Long.MAX_VALUE) == VideoMemory.Room.STEPPED, "the first refusal takes a step");
        check(VideoMemory.room(1, 0L) == VideoMemory.Room.SETTLING, "a refusal on the frame of the step takes none");
        check(VideoMemory.room(1, settles - 1) == VideoMemory.Room.SETTLING, "a refusal before the step has settled takes none");
        check(VideoMemory.room(1, settles) == VideoMemory.Room.STEPPED, "a refusal once the step has settled takes the next");
        check(VideoMemory.room(VideoMemory.LAST_STEP, settles - 1) == VideoMemory.Room.SETTLING, "a refusal just after the last step is put down to it");
        check(VideoMemory.room(VideoMemory.LAST_STEP, settles) == VideoMemory.Room.NONE_LEFT, "a refusal once the last step has settled has nothing left to give up");
    }

    private static void aBigCardDrawsEveryModeAsItIs() {
        for (Quality.Picture mode : MODES) {
            check(VideoMemory.fit(mode, 0).equals(mode), "step 0 draws " + Quality.described(mode) + " as it is");
        }
        Quality.Picture custom = new Quality.Picture(false, Quality.TREES_TO_SIGHT, Quality.ALL_GRASS, 1.5f, true, 120, 120, 400, 2, true, true, true, true,
                true, true, 80);
        check(VideoMemory.fit(custom, 0).equals(custom), "step 0 draws a Custom picture dearer than High as it is");
    }

    private static void eachStepGivesUpWhatItSays() {
        Quality.Picture high = Quality.HIGH_PICTURE;
        Quality.Picture one = VideoMemory.fit(high, 1);
        check(!one.sharpShadows() && !one.frameBlending(), "step 1 has no sharp shadows and no frame blending");
        check(one.fullSizeTextures() && one.renderScale() == high.renderScale() && one.viewDistance() == high.viewDistance() && one.shadingAndLight()
                && one.reflections() && one.surfaceDepth(), "step 1 keeps the rest of High");
        Quality.Picture two = VideoMemory.fit(high, 2);
        check(!two.fullSizeTextures() && !two.sharpShadows(), "step 2 has half size pictures as well");
        check(two.renderScale() == high.renderScale() && two.viewDistance() == high.viewDistance(), "step 2 keeps High's pixels and sight");
        Quality.Picture three = VideoMemory.fit(high, 3);
        Quality.Picture medium = Quality.MEDIUM_PICTURE;
        check(three.renderScale() == medium.renderScale() && three.viewDistance() == medium.viewDistance() && three.treeQuality() == medium.treeQuality()
                && three.grassDensity() == medium.grassDensity() && !three.surfaceDepth() && !three.frameBlending() && !three.stockLook(),
                "step 3 draws High as Medium, without frame blending");
        for (Quality.Picture mode : MODES) {
            Quality.Picture last = VideoMemory.fit(mode, VideoMemory.LAST_STEP);
            check(last.stockLook(), "the last step draws none of the mod's own content in " + Quality.described(mode));
            check(last.renderScale() <= Quality.LOW_PICTURE.renderScale() && last.viewDistance() <= Quality.LOW_PICTURE.viewDistance(),
                    "the last step draws no more than Low");
        }
        check(VideoMemory.fit(Quality.LOW_PICTURE, 3).equals(Quality.LOW_PICTURE), "Low is cheaper than step 3 asks and is drawn as it is");
    }

    private static void noStepDrawsMoreThanTheOneBefore() {
        for (int at = 1; at <= VideoMemory.LAST_STEP; at++) {
            Quality.Picture before = VideoMemory.ceiling(at - 1), now = VideoMemory.ceiling(at);
            check(now.stockLook() || !before.stockLook(), "step " + at + " keeps the stock look");
            check(now.treeQuality() <= before.treeQuality() && now.grassDensity() <= before.grassDensity() && now.renderScale() <= before.renderScale()
                    && now.viewDistance() <= before.viewDistance() && now.drawDistance() <= before.drawDistance()
                    && now.farTreeDistance() <= before.farTreeDistance() && now.rainOnGlass() <= before.rainOnGlass()
                    && now.lightDistance() <= before.lightDistance(), "step " + at + " draws no further, finer or more than step " + (at - 1));
            check((!now.sharpShadows() || before.sharpShadows()) && (!now.shadingAndLight() || before.shadingAndLight())
                    && (!now.reflections() || before.reflections()) && (!now.fullSizeTextures() || before.fullSizeTextures())
                    && (!now.surfaceDepth() || before.surfaceDepth()) && (!now.wholeFigureShadows() || before.wholeFigureShadows())
                    && (!now.frameBlending() || before.frameBlending()), "step " + at + " turns nothing on that step " + (at - 1) + " had off");
            check(!now.equals(before), "step " + at + " gives something up");
            check(!VideoMemory.given(at).equals(VideoMemory.given(at - 1)), "step " + at + " says what it gives up");
        }
    }

    private static void theSkinsHoldLessOnSmallerCards() {
        check(VideoMemory.skinBytes(0) == 512L << 20, "a big card's composed skins hold 512 MB, as before");
        check(VideoMemory.jarSkinBytes(0) == 512L << 20, "a big card's skins from the jar hold 512 MB, as before");
        check(VideoMemory.tailoredSkinBytes(0) == 384L << 20, "a big card's tailored skins hold 384 MB, as before");
        for (int at = 1; at <= VideoMemory.LAST_STEP; at++) {
            check(VideoMemory.skinBytes(at) <= VideoMemory.skinBytes(at - 1) && VideoMemory.jarSkinBytes(at) <= VideoMemory.jarSkinBytes(at - 1)
                    && VideoMemory.tailoredSkinBytes(at) <= VideoMemory.tailoredSkinBytes(at - 1), "the skins hold no more at step " + at);
            check(VideoMemory.skinBytes(at) > 0 && VideoMemory.jarSkinBytes(at) > 0 && VideoMemory.tailoredSkinBytes(at) > 0, "the skins have room at step " + at);
        }
    }
}

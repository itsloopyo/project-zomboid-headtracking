package com.cameraunlock.zomboid;

/** Tests of Develop: the eye's reading, the frame's dark and bright, the range they set, the tints by the light and the cast taken out indoors. */
final class DevelopTests {
    private DevelopTests() {}

    private static final int CELLS = 2048;
    private static final long SECOND = 1_000_000_000L;

    static void run() {
        theEyesReadingIsTheWeightedMeanOfTheLog();
        theDarkAndTheBrightAreTheFramesOwn();
        theFilmIsUndone();
        aFlatFrameIsTakenDownToBlackAndStretched();
        aFrameThatFillsTheRangeAlreadyIsLeftNearlyAsItIs();
        nightAndFogAreLetBeMore();
        noStrengthLeavesThePictureAsItWas();
        itMovesTowardsWhatTheFrameAsksOverTime();
        theLightTintsByDaySunAndNight();
        aGreenCastIndoorsIsTakenOutAndAWarmOneMostlyKept();
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** A frame whose cells' lights run evenly from low to high, log2, every weight 1. */
    private static float[] even(float low, float high) {
        float[] lights = new float[CELLS];
        for (int i = 0; i < CELLS; i++) {
            lights[i] = low + (high - low) * (i + 0.5f) / CELLS;
        }
        return lights;
    }

    private static float[] filled(float value) {
        float[] each = new float[CELLS];
        java.util.Arrays.fill(each, value);
        return each;
    }

    private static void theEyesReadingIsTheWeightedMeanOfTheLog() {
        Develop d = new Develop();
        float[] lights = filled(-4f), weights = filled(1f);
        lights[0] = 4f;
        weights[0] = CELLS - 1f;
        float read = d.read(lights, weights, CELLS);
        check(Math.abs(read) < 1e-4f, "the eye reads the mean of the log of the light by the weights: half the weight at -4 and half at 4 reads 0");
    }

    private static void theDarkAndTheBrightAreTheFramesOwn() {
        Develop d = new Develop();
        d.read(even(-10f, -2f), filled(1f), CELLS);
        check(Math.abs(d.dark() - (-10f + 8f * Develop.DARK_SHARE)) < 0.1f, "the dark is the light under which the darkest share lies: " + d.dark());
        check(Math.abs(d.bright() - (-10f + 8f * Develop.BRIGHT_SHARE)) < 0.1f, "the bright likewise from the top: " + d.bright());
        check(Math.abs(d.middle() - (-6f)) < 0.1f, "and the middle is the middle: " + d.middle());
    }

    private static void theFilmIsUndone() {
        for (float shown : new float[] {0.01f, 0.2f, 0.5f, 0.85f}) {
            check(Math.abs(Develop.film(Develop.filmInverse(shown)) - shown) < 1e-4f, "the film's inverse gives back " + shown);
        }
    }

    /** Settles d for ten seconds of frames at this exposure. */
    private static void settleLong(Develop d, float exposure, float strength, float night, float fog, float indoors) {
        long now = SECOND;
        for (int i = 0; i < 600; i++) {
            now += SECOND / 60;
            d.settle(exposure, strength, night, fog, indoors, now);
        }
    }

    private static void aFlatFrameIsTakenDownToBlackAndStretched() {
        // A noon in haze: every part of the frame between 0.06 and 0.35 exposed, nothing near black or white.
        Develop d = new Develop();
        float exposure = 4f;
        d.read(even(log2(0.06f / exposure), log2(0.35f / exposure)), filled(1f), CELLS);
        settleLong(d, exposure, 1f, 0f, 0f, 0f);
        float darkLight = exposure * (float) Math.pow(2.0, d.dark());
        check(d.takenAt(exposure) > 0.5f * darkLight, "most of a hazy frame's dark is taken away: " + d.takenAt(exposure) + " of " + darkLight);
        check(d.takenAt(exposure) <= Develop.MOST_TAKEN * darkLight + 1e-5f, "never more than MOST_TAKEN of it");
        check(d.gain() > 1.15f && d.gain() <= Develop.MOST_GAIN + 1e-4f, "and its range is stretched, within MOST_GAIN: " + d.gain());
    }

    private static void aFrameThatFillsTheRangeAlreadyIsLeftNearlyAsItIs() {
        Develop d = new Develop();
        float exposure = 1f;
        float black = (float) Math.pow(Develop.DARK_SHOWN, 2.2), white = Develop.filmInverse((float) Math.pow(Develop.BRIGHT_SHOWN, 2.2));
        // Spread so that its dark and bright shares lie where they are to be shown.
        float low = log2(black), high = log2(white);
        float span = (high - low) / (Develop.BRIGHT_SHARE - Develop.DARK_SHARE);
        d.read(even(low - Develop.DARK_SHARE * span, low - Develop.DARK_SHARE * span + span), filled(1f), CELLS);
        settleLong(d, exposure, 1f, 0f, 0f, 0f);
        check(d.takenAt(exposure) < 0.1f * black, "a frame with its own black takes none away: " + d.takenAt(exposure));
        check(Math.abs(d.gain() - 1f) < 0.05f, "and is not stretched: " + d.gain());
    }

    private static void nightAndFogAreLetBeMore() {
        float exposure = 4f;
        float[] lights = even(log2(0.002f / exposure), log2(0.06f / exposure));
        Develop night = new Develop();
        night.read(lights, filled(1f), CELLS);
        settleLong(night, exposure, 1f, 1f, 0f, 0f);
        check(night.gain() <= Develop.MOST_GAIN_AT_NIGHT + 1e-4f, "a night is stretched no more than MOST_GAIN_AT_NIGHT: " + night.gain());
        // A night street with a lamp far brighter than white in it.
        Develop lamp = new Develop();
        float[] lit = even(log2(0.002f / exposure), log2(0.06f / exposure));
        for (int i = CELLS * 97 / 100; i < CELLS; i++) {
            lit[i] = log2(20f / exposure);
        }
        lamp.read(lit, filled(1f), CELLS);
        settleLong(lamp, exposure, 1f, 1f, 0f, 0f);
        check(lamp.gain() >= 1f - 1e-4f, "and a lamp in it is not held in: " + lamp.gain());
        Develop sun = new Develop();
        sun.read(lit, filled(1f), CELLS);
        settleLong(sun, exposure, 1f, 0f, 0f, 0f);
        check(sun.gain() < 0.9f, "where by day a light that bright is: " + sun.gain());
        Develop day = new Develop(), fog = new Develop();
        float[] hazy = even(log2(0.06f / exposure), log2(0.35f / exposure));
        day.read(hazy, filled(1f), CELLS);
        fog.read(hazy, filled(1f), CELLS);
        settleLong(day, exposure, 1f, 0f, 0f, 0f);
        settleLong(fog, exposure, 1f, 0f, 1f, 0f);
        check(fog.takenAt(exposure) < 0.5f * day.takenAt(exposure), "thick fog keeps more of its paleness than a clear day: " + fog.takenAt(exposure) + " against " + day.takenAt(exposure));
    }

    private static void noStrengthLeavesThePictureAsItWas() {
        Develop d = new Develop();
        d.read(even(-8f, -5f), filled(1f), CELLS);
        settleLong(d, 4f, 0f, 0f, 0f, 0f);
        check(d.takenAt(4f) < 1e-6f && Math.abs(d.gain() - 1f) < 1e-5f, "a style with no levels takes nothing away and stretches nothing");
        Develop unread = new Develop();
        settleLong(unread, 4f, 1f, 0f, 0f, 1f);
        check(unread.takenAt(4f) == 0f && unread.gain() == 1f && unread.balanceRed() == 1f && unread.balanceBlue() == 1f,
                "nor does a frame before the meter's first reading");
    }

    private static void itMovesTowardsWhatTheFrameAsksOverTime() {
        Develop d = new Develop();
        float exposure = 4f;
        d.read(even(log2(0.06f / exposure), log2(0.35f / exposure)), filled(1f), CELLS);
        d.settle(exposure, 1f, 0f, 0f, 0f, SECOND);
        d.settle(exposure, 1f, 0f, 0f, 0f, SECOND + SECOND / 60);
        float early = d.gain();
        settleLong(d, exposure, 1f, 0f, 0f, 0f);
        check(early > 1f && early < d.gain() - 0.01f, "the gain eases towards what the frame asks for, not jumps: " + early + " then " + d.gain());
    }

    private static void theLightTintsByDaySunAndNight() {
        float[] shade = new float[3], lit = new float[3];
        Develop.tints(0.8f, 0f, 0f, shade, lit);
        check(shade[2] > shade[0] && lit[0] > lit[2], "a clear noon: cool shade, warm sun");
        Develop.tints(0.8f, 1f, 0f, shade, lit);
        check(shade[0] == 1f && shade[2] == 1f && lit[0] == 1f && lit[2] == 1f, "under a full cloud cover neither is tinted");
        Develop.tints(0.05f, 0f, 0f, shade, lit);
        check(Math.abs(shade[2] - 1f) < 0.005f && Math.abs(lit[0] - 1f) < 0.005f, "a low sun colours the picture itself and is let be");
        Develop.tints(-0.5f, 0f, 1f, shade, lit);
        check(shade[2] > 1.05f && shade[0] < 0.95f && lit[0] > lit[2], "at night the dark is moonlight blue and what a lamp lights warm");
        check(Develop.overcastCurve(1f, 0f) > 0f && Develop.overcastCurve(0f, 0f) == 0f && Develop.overcastCurve(1f, 1f) == 0f,
                "an overcast day takes more of an S; a clear one and a night do not");
    }

    private static void aGreenCastIndoorsIsTakenOutAndAWarmOneMostlyKept() {
        float[] lights = even(-8f, -3f);
        Develop green = castOf(lights, -0.3f, -0.3f, 1f);
        check(green.balanceRed() > 1.15f && Math.abs(green.balanceRed() - green.balanceBlue()) < 1e-4f,
                "a room lit green has its red and blue lifted alike: " + green.balanceRed() + " " + green.balanceBlue());
        Develop warm = castOf(lights, 0.3f, -0.3f, 1f);
        check(warm.balanceRed() < 1f && warm.balanceRed() > 0.9f && warm.balanceBlue() > 1f && warm.balanceBlue() < 1.1f,
                "a room lit warm keeps most of its warmth: " + warm.balanceRed() + " " + warm.balanceBlue());
        Develop out = castOf(lights, -0.3f, -0.3f, 0f);
        check(out.balanceRed() == 1f && out.balanceBlue() == 1f, "out of doors nothing is taken out");
        // Only what the light falls on is the light's colour: a dark green floor does not make the
        // room's light green, nor does a lamp's own colour.
        float[] reds = filled(0f), blues = filled(0f);
        for (int i = 0; i < CELLS / 2; i++) {
            reds[i] = -1f;
            blues[i] = -1f;
        }
        for (int i = CELLS * 97 / 100; i < CELLS; i++) {
            reds[i] = 1f;
            blues[i] = -1f;
        }
        Develop floor = new Develop();
        floor.read(lights, filled(1f), CELLS);
        floor.readCast(lights, reds, blues, CELLS);
        settleLong(floor, 4f, 1f, 0f, 0f, 1f);
        check(Math.abs(floor.balanceRed() - 1f) < 1e-3f, "a cast in the dark half of the frame is not the light's: " + floor.balanceRed());
    }

    private static Develop castOf(float[] lights, float red, float blue, float indoors) {
        Develop d = new Develop();
        d.read(lights, filled(1f), CELLS);
        d.readCast(lights, filled(red), filled(blue), CELLS);
        settleLong(d, 4f, 1f, 0f, 0f, indoors);
        return d;
    }

    private static float log2(float x) {
        return (float) (Math.log(x) / Math.log(2.0));
    }
}

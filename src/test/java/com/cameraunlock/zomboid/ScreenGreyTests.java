package com.cameraunlock.zomboid;

/** Tests of how much the game's screen shader greys a view under the first person picture: see ScreenGrey. */
final class ScreenGreyTests {
    private ScreenGreyTests() {}

    static void run() {
        Checks.run("lowKeepsTheGamesGrey", ScreenGreyTests::lowKeepsTheGamesGrey);
        Checks.run("thePictureKeepsTheSeasonsGreyAndNoMore", ScreenGreyTests::thePictureKeepsTheSeasonsGreyAndNoMore);
        Checks.run("theGreyGoesAsThePictureAndItsStyleComeIn", ScreenGreyTests::theGreyGoesAsThePictureAndItsStyleComeIn);
    }

    private static void lowKeepsTheGamesGrey() {
        Checks.check(ScreenGrey.under(0.4f, 0.9f, 0.85f, true, 1f, 1f) == 0.4f, "Low's picture is greyed as the game greys its own view");
    }

    private static void thePictureKeepsTheSeasonsGreyAndNoMore() {
        float summer = ScreenGrey.under(0.4f, 0.9f, 0.85f, false, 1f, 1f);
        Checks.check(Math.abs(summer - 0.1f * 0.4f * 0.85f) < 1e-6f, "on a long day with fog grey on top, only the season's is kept: " + summer);
        float winter = ScreenGrey.under(0.2f, 0.1f, 1f, false, 1f, 1f);
        Checks.check(winter == 0.2f, "and never more than the game's own: " + winter);
        float shortDay = ScreenGrey.under(0.4f, 0.1f, 1f, false, 1f, 1f);
        Checks.check(Math.abs(shortDay - 0.36f) < 1e-6f, "on a short day the season's grey stays: " + shortDay);
    }

    private static void theGreyGoesAsThePictureAndItsStyleComeIn() {
        float season = 0.1f * 0.4f;
        Checks.check(ScreenGrey.under(0.4f, 0.9f, 1f, false, 0f, 1f) == 0.4f, "with the game's own view on the screen, the game's grey");
        Checks.check(ScreenGrey.under(0.4f, 0.9f, 1f, false, 1f, 0f) == 0.4f, "with the picture showing the game's view from overhead, the game's grey");
        float half = ScreenGrey.under(0.4f, 0.9f, 1f, false, 1f, 0.5f);
        Checks.check(Math.abs(half - (0.4f + season) / 2f) < 1e-6f, "half way down to the eye, half way between: " + half);
        float mixing = ScreenGrey.under(0.4f, 0.9f, 1f, false, 0.5f, 1f);
        Checks.check(Math.abs(mixing - (0.4f + season) / 2f) < 1e-6f, "with the picture half over the game's, half way between: " + mixing);
    }
}

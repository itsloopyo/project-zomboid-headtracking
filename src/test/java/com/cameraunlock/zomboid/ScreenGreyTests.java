package com.cameraunlock.zomboid;

/** Tests of how much the game's screen shader greys a view under the first person picture: see ScreenGrey. */
final class ScreenGreyTests {
    private ScreenGreyTests() {}

    static void run() {
        Checks.run("lowKeepsTheGamesGrey", ScreenGreyTests::lowKeepsTheGamesGrey);
        Checks.run("thePictureKeepsNoGrey", ScreenGreyTests::thePictureKeepsNoGrey);
        Checks.run("theGreyGoesAsThePictureAndItsStyleComeIn", ScreenGreyTests::theGreyGoesAsThePictureAndItsStyleComeIn);
    }

    private static void lowKeepsTheGamesGrey() {
        Checks.check(ScreenGrey.under(0.4f, true, 1f, 1f) == 0.4f, "Low's picture is greyed as the game greys its own view");
    }

    private static void thePictureKeepsNoGrey() {
        Checks.check(ScreenGrey.under(0.36f, false, 1f, 1f) == 0f, "a clear winter day's grey is not kept");
        Checks.check(ScreenGrey.under(0.4f, false, 1f, 1f) == 0f, "nor fog's and the weather's");
    }

    private static void theGreyGoesAsThePictureAndItsStyleComeIn() {
        Checks.check(ScreenGrey.under(0.4f, false, 0f, 1f) == 0.4f, "with the game's own view on the screen, the game's grey");
        Checks.check(ScreenGrey.under(0.4f, false, 1f, 0f) == 0.4f, "with the picture showing the game's view from overhead, the game's grey");
        float half = ScreenGrey.under(0.4f, false, 1f, 0.5f);
        Checks.check(Math.abs(half - 0.2f) < 1e-6f, "half way down to the eye, half the game's grey: " + half);
        float mixing = ScreenGrey.under(0.4f, false, 0.5f, 1f);
        Checks.check(Math.abs(mixing - 0.2f) < 1e-6f, "with the picture half over the game's, half the game's grey: " + mixing);
    }
}

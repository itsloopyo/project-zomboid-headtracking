package com.cameraunlock.zomboid;

/** Tests of the controller's double tap that switches first person. */
final class DoubleTapTests {
    private static final long MS = 1_000_000L;

    private DoubleTapTests() {}

    static void run() {
        Checks.run("twoQuickPressesAreADoubleTap", DoubleTapTests::twoQuickPressesAreADoubleTap);
        Checks.run("aSecondPressTooLateIsAFirst", DoubleTapTests::aSecondPressTooLateIsAFirst);
        Checks.run("aButtonHeldDownIsOnePress", DoubleTapTests::aButtonHeldDownIsOnePress);
        Checks.run("aThirdPressStartsAgain", DoubleTapTests::aThirdPressStartsAgain);
        Checks.run("theWindowHoldsAcrossTheClocksWrap", DoubleTapTests::theWindowHoldsAcrossTheClocksWrap);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** Presses at the times given, down for one frame and up the next, and says whether the last was a double tap. */
    private static boolean taps(DoubleTap tap, long... at) {
        boolean last = false;
        for (long now : at) {
            last = tap.press(true, now);
            check(!tap.press(false, now + 16 * MS), "letting go is never a press");
        }
        return last;
    }

    private static void twoQuickPressesAreADoubleTap() {
        DoubleTap tap = new DoubleTap();
        check(!taps(tap, 0), "one press is not a double tap");
        check(taps(tap, 200 * MS), "a second within the window is");
        check(taps(new DoubleTap(), 0, DoubleTap.WINDOW_NANOS), "and one at the window's very end");
    }

    private static void aSecondPressTooLateIsAFirst() {
        DoubleTap tap = new DoubleTap();
        check(!taps(tap, 0, DoubleTap.WINDOW_NANOS + 1), "a second press after the window is not a double tap");
        check(taps(tap, DoubleTap.WINDOW_NANOS + 1 + 100 * MS), "it is the first of the next two");
    }

    private static void aButtonHeldDownIsOnePress() {
        DoubleTap tap = new DoubleTap();
        boolean any = false;
        long held = DoubleTap.WINDOW_NANOS + 100 * MS;
        for (long now = 0; now < held; now += 16 * MS) {
            any |= tap.press(true, now);
        }
        check(!any, "a button held through the window is one press");
        tap.press(false, held);
        check(!tap.press(true, held + 50 * MS), "and pressed again straight after a hold longer than the window it is not a double tap");
    }

    private static void aThirdPressStartsAgain() {
        DoubleTap tap = new DoubleTap();
        check(taps(tap, 0, 100 * MS), "two presses are a double tap");
        check(!taps(tap, 200 * MS), "a third straight after is a first press again");
        check(taps(tap, 300 * MS), "and a fourth makes the next double tap");
    }

    private static void theWindowHoldsAcrossTheClocksWrap() {
        DoubleTap tap = new DoubleTap();
        check(taps(tap, Long.MAX_VALUE - 100 * MS, Long.MAX_VALUE + 100 * MS), "presses either side of nanoTime's wrap are a double tap");
    }
}

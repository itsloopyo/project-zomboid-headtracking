package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of who wears a finer skin: see FineSkinRange. */
final class FineSkinRangeTests {
    private FineSkinRangeTests() {}

    static void run() {
        Checks.run("aPersonWithinTheRangeWearsOne", FineSkinRangeTests::aPersonWithinTheRangeWearsOne);
        Checks.run("aPersonWearingOneKeepsItALittleFurtherOut", FineSkinRangeTests::aPersonWearingOneKeepsItALittleFurtherOut);
        Checks.run("aPersonAtTheEdgeDoesNotChangeEveryFrame", FineSkinRangeTests::aPersonAtTheEdgeDoesNotChangeEveryFrame);
        Checks.run("aPersonNotDrawnForAFrameIsForgotten", FineSkinRangeTests::aPersonNotDrawnForAFrameIsForgotten);
    }

    private static void aPersonWithinTheRangeWearsOne() {
        FineSkinRange range = new FineSkinRange();
        check(range.wears(new Object(), FineSkinRange.WITHIN - 0.1f, 1), "a person just within the range wears a finer skin");
        check(!range.wears(new Object(), FineSkinRange.WITHIN + 0.1f, 1), "a person just past it, who wore none, does not");
    }

    private static void aPersonWearingOneKeepsItALittleFurtherOut() {
        FineSkinRange range = new FineSkinRange();
        Object walker = new Object();
        range.wears(walker, 2f, 1);
        check(range.wears(walker, (FineSkinRange.WITHIN + FineSkinRange.KEPT_WITHIN) / 2f, 2), "walking out past the range, a finer skin is kept");
        check(!range.wears(walker, FineSkinRange.KEPT_WITHIN + 0.1f, 3), "and given up past the second edge");
        check(!range.wears(walker, (FineSkinRange.WITHIN + FineSkinRange.KEPT_WITHIN) / 2f, 4), "and not put on again until within the first");
    }

    private static void aPersonAtTheEdgeDoesNotChangeEveryFrame() {
        FineSkinRange range = new FineSkinRange();
        Object walker = new Object();
        range.wears(walker, 1f, 1);
        int changes = 0;
        boolean was = true;
        for (int frame = 2; frame < 100; frame++) {
            float away = FineSkinRange.WITHIN + (frame % 2 == 0 ? 0.2f : -0.2f);
            boolean wears = range.wears(walker, away, frame);
            changes += wears != was ? 1 : 0;
            was = wears;
        }
        check(changes == 0, "a person swaying either side of the range keeps the skin they wear: " + changes + " changes");
    }

    private static void aPersonNotDrawnForAFrameIsForgotten() {
        FineSkinRange range = new FineSkinRange();
        Object walker = new Object(), other = new Object();
        range.wears(walker, 1f, 1);
        range.wears(other, 1f, 3);
        check(range.remembered() == 1, "someone not drawn the frame before is let go: " + range.remembered() + " remembered");
        check(!range.wears(walker, FineSkinRange.WITHIN + 0.5f, 3), "and past the range puts on no finer skin");
    }
}

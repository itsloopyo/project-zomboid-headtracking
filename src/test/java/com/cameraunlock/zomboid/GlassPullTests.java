package com.cameraunlock.zomboid;

/** Tests of how a vehicle's change of speed pulls at the rain on its glass. */
final class GlassPullTests {
    private static final float FRAME = 1f / 60f;

    private GlassPullTests() {}

    static void run() {
        steadySpeedPullsAtNothing();
        gatheringSpeedLeansRunsBackAndHoldsTheFrontScreens();
        brakingHurriesTheFrontScreensRuns();
        aBendLeansRunsToItsOutside();
        leanAndPaceHaveTheirLimits();
        clocksBeginAgainWithThePictures();
    }

    /** Drives for so many seconds from the picture's clock at start, facing aheadX, aheadY, the speed changing by that much a second. Returns the clock it ends at. */
    private static float drive(float start, float seconds, float aheadX, float aheadY, float changeX, float changeY) {
        GlassPull.frame(false, start, 0f, 0f, aheadX, aheadY);
        float clock = start, goingX = 3f * aheadX, goingY = 3f * aheadY;
        for (int i = 0; i < Math.round(seconds / FRAME); i++) {
            clock = (clock + FRAME) % GlassPull.HOUR;
            goingX += changeX * FRAME;
            goingY += changeY * FRAME;
            GlassPull.frame(true, clock, goingX, goingY, aheadX, aheadY);
        }
        return clock;
    }

    private static void steadySpeedPullsAtNothing() {
        float clock = drive(100f, 3f, 0.6f, 0.8f, 0f, 0f);
        check(GlassPull.leanAhead == 0f && GlassPull.leanAside == 0f, "at a steady speed nothing leans");
        check(near(GlassPull.front, clock) && near(GlassPull.back, clock), "at a steady speed the screens' runs go by the picture's clock");
    }

    private static void gatheringSpeedLeansRunsBackAndHoldsTheFrontScreens() {
        float clock = drive(100f, 3f, 0.6f, 0.8f, 0.6f * 4.9f, 0.8f * 4.9f);
        check(near(GlassPull.leanAhead, -0.5f), "gathering speed at half the earth's pull, a run on a side window leans half as far back as it falls: " + GlassPull.leanAhead);
        check(near(GlassPull.leanAside, 0f), "and not to either side: " + GlassPull.leanAside);
        check(GlassPull.front < clock - 0.5f, "the front screen's runs are held back: " + (GlassPull.front - clock));
        check(GlassPull.back > clock + 0.5f, "the back screen's runs are hurried: " + (GlassPull.back - clock));
    }

    private static void brakingHurriesTheFrontScreensRuns() {
        float clock = drive(100f, 3f, 0f, -1f, 0f, 4.9f);
        check(near(GlassPull.leanAhead, 0.5f), "braking, a run on a side window leans forward: " + GlassPull.leanAhead);
        check(GlassPull.front > clock + 0.5f && GlassPull.back < clock - 0.5f, "and the front screen's runs are hurried, the back screen's held back");
    }

    private static void aBendLeansRunsToItsOutside() {
        // Facing along x, the side the panes are counted to is along y. Speed gained that way is a bend to that side.
        drive(100f, 3f, 1f, 0f, 0f, 4.9f);
        check(near(GlassPull.leanAside, -0.5f), "in a bend a run on a screen leans to the outside of it: " + GlassPull.leanAside);
        check(near(GlassPull.leanAhead, 0f), "and a run on a side window stays upright: " + GlassPull.leanAhead);
    }

    private static void leanAndPaceHaveTheirLimits() {
        float clock = drive(100f, 2f, 1f, 0f, -60f, 60f);
        check(GlassPull.leanAhead == GlassPull.MOST_LEAN && GlassPull.leanAside == -GlassPull.MOST_LEAN, "a run leans no further than the most");
        check(GlassPull.front - clock < 2f * (GlassPull.MOST_HURRIED - 1f) + 0.01f, "a hurried run has its most");
        check(GlassPull.back - clock > -2f - 0.01f, "and a run held back does not climb");
    }

    private static void clocksBeginAgainWithThePictures() {
        float clock = drive(GlassPull.HOUR - 1f, 2f, 1f, 0f, 4.9f, 0f);
        check(clock < 2f, "the picture's clock began again: " + clock);
        check(GlassPull.front >= 0f && GlassPull.front < GlassPull.HOUR && GlassPull.back >= 0f && GlassPull.back < GlassPull.HOUR,
                "the screens' clocks stay within the hour: " + GlassPull.front + ", " + GlassPull.back);
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.02f;
    }
}

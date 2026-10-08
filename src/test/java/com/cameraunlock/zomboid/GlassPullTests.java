package com.cameraunlock.zomboid;

/** Tests of how a vehicle's change of speed pulls at the rain on its glass. */
final class GlassPullTests {
    private static final GlassPull glassPull = new GlassPull();

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
        glassPull.frame(false, start, 0f, 0f, aheadX, aheadY);
        float clock = start, goingX = 3f * aheadX, goingY = 3f * aheadY;
        for (int i = 0; i < Math.round(seconds / FRAME); i++) {
            clock = (clock + FRAME) % GlassPull.HOUR;
            goingX += changeX * FRAME;
            goingY += changeY * FRAME;
            glassPull.frame(true, clock, goingX, goingY, aheadX, aheadY);
        }
        return clock;
    }

    private static void steadySpeedPullsAtNothing() {
        float clock = drive(100f, 3f, 0.6f, 0.8f, 0f, 0f);
        check(glassPull.leanAhead == 0f && glassPull.leanAside == 0f, "at a steady speed nothing leans");
        check(near(glassPull.front, clock) && near(glassPull.back, clock), "at a steady speed the screens' runs go by the picture's clock");
    }

    private static void gatheringSpeedLeansRunsBackAndHoldsTheFrontScreens() {
        float clock = drive(100f, 3f, 0.6f, 0.8f, 0.6f * 4.9f, 0.8f * 4.9f);
        check(near(glassPull.leanAhead, -0.5f), "gathering speed at half the earth's pull, a run on a side window leans half as far back as it falls: " + glassPull.leanAhead);
        check(near(glassPull.leanAside, 0f), "and not to either side: " + glassPull.leanAside);
        check(glassPull.front < clock - 0.5f, "the front screen's runs are held back: " + (glassPull.front - clock));
        check(glassPull.back > clock + 0.5f, "the back screen's runs are hurried: " + (glassPull.back - clock));
    }

    private static void brakingHurriesTheFrontScreensRuns() {
        float clock = drive(100f, 3f, 0f, -1f, 0f, 4.9f);
        check(near(glassPull.leanAhead, 0.5f), "braking, a run on a side window leans forward: " + glassPull.leanAhead);
        check(glassPull.front > clock + 0.5f && glassPull.back < clock - 0.5f, "and the front screen's runs are hurried, the back screen's held back");
    }

    private static void aBendLeansRunsToItsOutside() {
        // Facing along x, the side the panes are counted to is along y. Speed gained that way is a bend to that side.
        drive(100f, 3f, 1f, 0f, 0f, 4.9f);
        check(near(glassPull.leanAside, -0.5f), "in a bend a run on a screen leans to the outside of it: " + glassPull.leanAside);
        check(near(glassPull.leanAhead, 0f), "and a run on a side window stays upright: " + glassPull.leanAhead);
    }

    private static void leanAndPaceHaveTheirLimits() {
        float clock = drive(100f, 2f, 1f, 0f, -60f, 60f);
        check(glassPull.leanAhead == GlassPull.MOST_LEAN && glassPull.leanAside == -GlassPull.MOST_LEAN, "a run leans no further than the most");
        check(glassPull.front - clock < 2f * (GlassPull.MOST_HURRIED - 1f) + 0.01f, "a hurried run has its most");
        check(glassPull.back - clock > -2f - 0.01f, "and a run held back does not climb");
    }

    private static void clocksBeginAgainWithThePictures() {
        float clock = drive(GlassPull.HOUR - 1f, 2f, 1f, 0f, 4.9f, 0f);
        check(clock < 2f, "the picture's clock began again: " + clock);
        check(glassPull.front >= 0f && glassPull.front < GlassPull.HOUR && glassPull.back >= 0f && glassPull.back < GlassPull.HOUR,
                "the screens' clocks stay within the hour: " + glassPull.front + ", " + glassPull.back);
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

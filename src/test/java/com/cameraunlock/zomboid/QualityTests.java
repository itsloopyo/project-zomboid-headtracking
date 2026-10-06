package com.cameraunlock.zomboid;

final class QualityTests {
    private QualityTests() {}

    private static final long SECOND = 1_000_000_000L;

    static void run() {
        aLevelAskedForIsKept();
        automaticStepsDownWhileThePictureIsSlow();
        automaticStaysWhereThePictureKeepsUp();
        Quality.set(Quality.HIGH);
        System.out.println("all tests passed");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    /** Counts frames at a steady rate for a time, and returns the time it ended at. */
    private static long frames(long from, float rate, float seconds) {
        long step = (long) (SECOND / rate);
        long now = from;
        while (now - from < (long) (seconds * SECOND)) {
            now += step;
            Quality.frame(now);
        }
        return now;
    }

    private static void aLevelAskedForIsKept() {
        Quality.set(Quality.HIGH);
        check(Quality.scale() == 1f && Quality.sight() == 80 && Quality.shadowSize() == 4096 && Quality.shadedAndLit(),
                "high is the whole picture");
        frames(SECOND, 10f, 20f);
        check(Quality.scale() == 1f, "and stays the whole picture however slowly it is drawn");
        Quality.set(Quality.LOW);
        check(Quality.scale() < 0.7f && Quality.sight() == 48 && Quality.shadowSize() == 2048 && !Quality.shadedAndLit() && !Quality.relief(),
                "low is fewer pixels, shorter sight, smaller maps and no shading of corners");
    }

    private static void automaticStepsDownWhileThePictureIsSlow() {
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        check(Quality.sight() == 80, "automatic begins with the whole picture");
        long now = frames(SECOND, 30f, 2.5f);
        check(Quality.sight() == 80, "and judges nothing in under three seconds");
        now = frames(now, 30f, 1f);
        check(Quality.sight() == 64, "three seconds at thirty frames a second take it down a level");
        now = frames(now, 30f, 4f);
        check(Quality.sight() == 64, "the next count begins once the new level has settled");
        now = frames(now, 30f, 1.5f);
        check(Quality.sight() == 48, "and still slow, it goes down again");
        frames(now, 5f, 30f);
        check(Quality.sight() == 48, "below which there is nothing");
    }

    private static void automaticStaysWhereThePictureKeepsUp() {
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        long now = frames(SECOND, 60f, 30f);
        check(Quality.sight() == 80, "at sixty frames a second the picture stays whole");
        Quality.rest();
        now = frames(now + 60 * SECOND, 60f, 10f);
        check(Quality.sight() == 80, "and a minute with the view off is not a minute of slow frames");
        now = frames(now, 30f, 3.2f);
        check(Quality.sight() == 64, "slow frames later on are still acted on");
        frames(now, 120f, 60f);
        check(Quality.sight() == 64, "and it does not go back up in the session");
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        now = frames(now + 60 * SECOND, 100f, 2f);
        now = frames(now + 6 * SECOND, 100f, 2f);
        check(Quality.sight() == 80, "one frame of six seconds among fast ones is a hitch and takes nothing down");
    }
}

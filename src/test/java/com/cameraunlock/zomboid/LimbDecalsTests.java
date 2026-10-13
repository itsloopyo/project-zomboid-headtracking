package com.cameraunlock.zomboid;

/** Tests of what lies where among the pieces laid on the ground as pictures. */
final class LimbDecalsTests {
    private LimbDecalsTests() {}

    static void run() {
        Checks.run("aLaidPieceLiesOnTheSquareItsMiddleIsOn", LimbDecalsTests::aLaidPieceLiesOnTheSquareItsMiddleIsOn);
        Checks.run("theNextPieceTakesThePictureLaidLongestAgo", LimbDecalsTests::theNextPieceTakesThePictureLaidLongestAgo);
    }

    private static void aLaidPieceLiesOnTheSquareItsMiddleIsOn() {
        LimbDecals.forget();
        Checks.check(LimbDecals.sumAt(10800, 9400, 0) == 0L, "nothing lies on a square nothing was laid on");
        LimbDecals.place(LimbDecals.take(), 10800.7f, 9400.2f, 0, 0.4f);
        Checks.check(LimbDecals.at(10800, 9400, 0).size() == 1, "a piece lies on the square its middle is on");
        Checks.check(LimbDecals.at(10800, 9400, 1).isEmpty() && LimbDecals.at(10801, 9400, 0).isEmpty(), "and on no other level or square");
        long one = LimbDecals.sumAt(10800, 9400, 0);
        LimbDecals.place(LimbDecals.take(), 10800.1f, 9400.9f, 0, 0.2f);
        Checks.check(one != 0L && LimbDecals.sumAt(10800, 9400, 0) != one, "a second piece on the square changes its signature");
        LimbDecals.forget();
    }

    private static void theNextPieceTakesThePictureLaidLongestAgo() {
        LimbDecals.forget();
        for (int i = 0; i < LimbDecals.SLOTS; i++) {
            LimbDecals.place(LimbDecals.take(), 10000.5f + i, 9000.5f, 0, 0.3f);
        }
        Checks.check(LimbDecals.at(10000, 9000, 0).size() == 1, "every picture is laid while there are pictures to spare");
        int slot = LimbDecals.take();
        Checks.check(slot == 0 && LimbDecals.at(10000, 9000, 0).isEmpty(), "the next piece takes the first picture, and the piece in it goes: slot " + slot);
        Checks.check(LimbDecals.at(10001, 9000, 0).size() == 1, "the rest stay");
        LimbDecals.forget();
    }
}

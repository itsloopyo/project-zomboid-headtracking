package com.cameraunlock.zomboid;

/** Tests of TooDark: which squares the game counts too dark to see by, and how far the picture takes them down. */
final class TooDarkTests {
    private TooDarkTests() {}

    static void run() {
        theGamesLineLeavesASquareAsThePictureHasIt();
        underTheLineTheDarkComesInOverTheRamp();
        theWornItemsVisionModifierMovesTheLine();
        theRatioTakesThePicturesLightToTheGames();
        aRoomWithNothingInSightRoundItIsNotKnown();
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static void theGamesLineLeavesASquareAsThePictureHasIt() {
        check(TooDark.darkness(TooDark.LINE, 1f) == 0f, "a square on the game's line is not too dark");
        check(TooDark.darkness(0.8f, 1f) == 0f, "nor is one over it");
    }

    private static void underTheLineTheDarkComesInOverTheRamp() {
        check(near(TooDark.darkness(TooDark.LINE - TooDark.RAMP / 2f, 1f), 0.5f), "half the ramp under the line is half dark");
        check(near(TooDark.darkness(TooDark.LINE - TooDark.RAMP, 1f), 1f), "the ramp's foot is wholly dark");
        check(TooDark.darkness(0f, 1f) == 1f, "and so is a square with no light at all");
    }

    private static void theWornItemsVisionModifierMovesTheLine() {
        // tooDarkToRead: (light / modifier + light) / 2 under 0.43. At a modifier of 0.5 a light of 0.3 reads 0.45.
        check(TooDark.darkness(0.3f, 0.5f) == 0f, "what the worn items let the player see by is not too dark");
        check(TooDark.darkness(0.3f, 1f) > 0f, "the same light with nothing worn is");
    }

    private static void theRatioTakesThePicturesLightToTheGames() {
        check(near(TooDark.ratio(0.2f, 0.4f), (float) Math.pow(0.5, 2.2)), "a square the picture lights at twice the game's light is taken down by its linear share");
        check(TooDark.ratio(0.2f, 0f) == 1f, "a square the picture gives no light is left alone");
        check(TooDark.ratio(1f, 0.01f) == TooDark.MOST_RATIO, "and one it lights far darker than the game is lifted no further than MOST_RATIO");
    }

    private static void aRoomWithNothingInSightRoundItIsNotKnown() {
        int[] figures = {Sight.UNREAD, Sight.CLOSED, Sight.UNREAD};
        Sight.fill(figures, new int[3], new int[3], 3, 1, Sight.UNREAD);
        check(figures[1] == Sight.UNREAD, "a room with nothing in sight round it is left unknown, not counted as having no light");
        int[] lit = {90, Sight.CLOSED, Sight.UNREAD};
        Sight.fill(lit, new int[3], new int[3], 3, 1, Sight.UNREAD);
        check(lit[1] == 90, "one beside a square in sight takes its light");
    }
}

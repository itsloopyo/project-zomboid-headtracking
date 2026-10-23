package com.cameraunlock.zomboid;

/** Tests of which sprites are a church's drape, and of the folds a run of its squares hangs in. */
final class WallDrapeTests {
    private WallDrapeTests() {}

    static void run() {
        Checks.run("drapeLengthsAreToldByTheirPlaceOnTheChurchSheet", WallDrapeTests::drapeLengthsAreToldByTheirPlaceOnTheChurchSheet);
        Checks.run("foldsRunOnFromOneSquareIntoTheNext", WallDrapeTests::foldsRunOnFromOneSquareIntoTheNext);
        Checks.run("foldsDifferAndStayWithinTheirDepth", WallDrapeTests::foldsDifferAndStayWithinTheirDepth);
    }

    private static void drapeLengthsAreToldByTheirPlaceOnTheChurchSheet() {
        String sheet = "location_community_church_small_01_";
        Checks.check(WallDrapes.kind(sheet + 86) == WallDrapes.FOOT, "the length with the fringe on a west wall is a drape's foot");
        Checks.check(WallDrapes.kind(sheet + 76) == WallDrapes.FOOT, "and on a north wall");
        Checks.check(WallDrapes.kind(sheet + 84) == WallDrapes.HEAD, "the plain length a level up is its head");
        Checks.check(WallDrapes.kind(sheet + 85) == WallDrapes.BANNER, "a length with a cross is a banner over it");
        Checks.check(WallDrapes.kind(sheet + 77) == WallDrapes.BANNER, "and on a north wall");
        Checks.check(WallDrapes.kind(sheet + 87) == WallDrapes.NONE, "a column's base is no drape");
        Checks.check(WallDrapes.kind(sheet + 860) == WallDrapes.NONE, "nor a sprite whose number only starts the same");
        Checks.check(WallDrapes.kind("location_community_church_small_02_86") == WallDrapes.NONE, "nor one of another sheet");
        Checks.check(WallDrapes.kind(null) == WallDrapes.NONE, "nor a sprite with no name");
    }

    /**
     * Along a run of squares as the builder steps them, strip by strip: the last point of a
     * square and the first of the next are one point, and no strip steps further out or in
     * than a fold's turn allows.
     */
    private static void foldsRunOnFromOneSquareIntoTheNext() {
        int strips = WallDrapes.FOLDS * WallDrapes.FOLD_STRIPS;
        float most = WallDrapes.DEEP * (1f + WallDrapes.SPREAD);
        // A fold's sine turns by at most 2 pi / FOLD_STRIPS of its depth between two points a strip apart.
        float step = most * (float) (2.0 * Math.PI / WallDrapes.FOLD_STRIPS) * 1.01f;
        for (boolean north : new boolean[] {false, true}) {
            long line = WallDrapes.line(north, 12755, 0);
            for (float up : new float[] {0f, 0.135f, 0.5f, 1f}) {
                for (int square = 1669; square < 1682; square++) {
                    float end = WallDrapes.out(line, square, 1f, up), start = WallDrapes.out(line, square + 1, 0f, up);
                    Checks.check(Math.abs(end - start) < 1e-6f, "square " + square + " ends where the next starts, at " + up + ": " + end + " " + start);
                    for (int strip = 0; strip < strips; strip++) {
                        float a = WallDrapes.out(line, square, (float) strip / strips, up), b = WallDrapes.out(line, square, (float) (strip + 1) / strips, up);
                        Checks.check(Math.abs(b - a) <= step, "no step between strips " + strip + " and " + (strip + 1) + " of " + square + ": " + a + " " + b);
                    }
                }
            }
        }
    }

    private static void foldsDifferAndStayWithinTheirDepth() {
        long line = WallDrapes.line(false, 12755, 0);
        float least = Float.MAX_VALUE, deepest = 0f, most = WallDrapes.DEEP * (1f + WallDrapes.SPREAD);
        for (int square = 1669; square <= 1682; square++) {
            for (int fold = 0; fold < WallDrapes.FOLDS; fold++) {
                // A quarter of the way through a fold is its crest.
                float crest = WallDrapes.out(line, square, (fold + 0.25f) / WallDrapes.FOLDS, 1f);
                least = Math.min(least, crest);
                deepest = Math.max(deepest, crest);
                Checks.check(Math.abs(WallDrapes.out(line, square, (fold + 0.25f) / WallDrapes.FOLDS, 0f)) <= most + 1e-6f, "a fold's foot is no deeper than the most");
            }
        }
        Checks.check(least > 0f && deepest <= WallDrapes.DEEP + 1e-6f, "every crest stands out, none past the depth: " + least + " " + deepest);
        Checks.check(deepest - least > 0.2f * WallDrapes.DEEP, "the folds of a run do not all hang alike: " + least + " " + deepest);
        Checks.check(Math.abs(WallDrapes.out(line, 1670, 0.1f, 0f)) > Math.abs(WallDrapes.out(line, 1670, 0.1f, 1f)), "a fold is deeper at the drape's foot than at its head");
        Checks.check(WallDrapes.line(false, 12755, 0) != WallDrapes.line(true, 12755, 0) && WallDrapes.line(false, 12755, 0) != WallDrapes.line(false, 12755, 1),
                "a north and a west wall on one line, and two levels, are lines of their own");
    }
}

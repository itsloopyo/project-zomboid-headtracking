package com.cameraunlock.zomboid;

/** Tests of how much of a ground spreading over its neighbour covers it (FloorBuilder.coverFrom). */
final class SpreadGroundTests {
    private SpreadGroundTests() {}

    static void run() {
        Checks.run("aStraightEdgeIsLaidByItsCornersAsBefore", SpreadGroundTests::aStraightEdgeIsLaidByItsCornersAsBefore);
        Checks.run("aPathOneSquareWideBetweenGrassKeepsItsMiddle", SpreadGroundTests::aPathOneSquareWideBetweenGrassKeepsItsMiddle);
        Checks.run("aPathTurningACornerKeepsTheTurn", SpreadGroundTests::aPathTurningACornerKeepsTheTurn);
        Checks.run("aPathOnTheSlantGoesOnThroughItsCorners", SpreadGroundTests::aPathOnTheSlantGoesOnThroughItsCorners);
        Checks.run("aPathSteppingOnTheSlantIsNotCutAtItsSteps", SpreadGroundTests::aPathSteppingOnTheSlantIsNotCutAtItsSteps);
        Checks.run("squaresAlongAPathSteppingOnTheSlantAgreeOnTheEdgesTheyShare", SpreadGroundTests::squaresAlongAPathSteppingOnTheSlantAgreeOnTheEdgesTheyShare);
        Checks.run("theEndOfAPathIsRounded", SpreadGroundTests::theEndOfAPathIsRounded);
        Checks.run("twoPathSquaresAgreeOnTheEdgeTheyShare", SpreadGroundTests::twoPathSquaresAgreeOnTheEdgeTheyShare);
        Checks.run("theCornerOfALawnIsRounded", SpreadGroundTests::theCornerOfALawnIsRounded);
        Checks.run("aLoneSquareOfGrassIsRounded", SpreadGroundTests::aLoneSquareOfGrassIsRounded);
        Checks.run("aPathSteppingBesideAnotherGrassIsNotCutAtItsSteps", SpreadGroundTests::aPathSteppingBesideAnotherGrassIsNotCutAtItsSteps);
    }

    /**
     * Lays the map's square at x, y. The map is rows of 'g' for the spreading ground, '.' for the
     * square's own and 'x' for other ground; beyond its edge is the own ground.
     */
    private static boolean cover(String[] map, int x, int y, float[] corners, float[] grid) {
        int[] around = new int[25];
        for (int i = 0; i < 25; i++) {
            int ax = x + i % 5 - 2, ay = y + i / 5 - 2;
            char what = ax < 0 || ay < 0 || ay >= map.length || ax >= map[ay].length() ? '.' : map[ay].charAt(ax);
            around[i] = what == 'g' ? FloorBuilder.SPREADING : what == '.' ? FloorBuilder.OWN : FloorBuilder.OTHER;
        }
        return FloorBuilder.coverFrom(around, corners, grid);
    }

    /** Lays the middle square of a map five by five. */
    private static boolean cover(String[] map, float[] corners, float[] grid) {
        return cover(map, 2, 2, corners, grid);
    }

    private static final String[] PATH = {"gg.gg", "gg.gg", "gg.gg", "gg.gg", "gg.gg"};
    /** A path on the slant that steps: in from the west, a step south, out to the east. */
    private static final String[] STEP = {"ggggg", "ggggg", "...gg", "gg...", "ggggg"};

    /** How much shows at (a, b) of the square, as SpriteGeometry reads the grid. */
    private static float at(float[] grid, float a, float b) {
        int i = a < 0.5f ? 0 : 1, j = b < 0.5f ? 0 : 1;
        float u = a * 2f - i, v = b * 2f - j;
        int k = j * 3 + i;
        return grid[k] * (1f - u) * (1f - v) + grid[k + 1] * u * (1f - v) + grid[k + 4] * u * v + grid[k + 3] * (1f - u) * v;
    }

    private static void aStraightEdgeIsLaidByItsCornersAsBefore() {
        float[] corners = new float[4], grid = new float[9];
        boolean byGrid = cover(new String[] {"gg...", "gg...", "gg...", "gg...", "gg..."}, corners, grid);
        check(!byGrid, "grass along the west side alone is laid as one face");
        check(corners[0] == 1f && corners[3] == 1f && corners[1] == 0f && corners[2] == 0f, "covered at the two west corners");
        check(grid[4] == 0.5f && grid[3] == 1f && grid[5] == 0f, "the grid says what the corners say");
    }

    private static void aPathOneSquareWideBetweenGrassKeepsItsMiddle() {
        float[] corners = new float[4], grid = new float[9];
        check(cover(PATH, corners, grid), "laid by its grid");
        check(at(grid, 0.5f, 0.5f) < 0.3f && at(grid, 0.5f, 0f) < 0.3f && at(grid, 0.5f, 1f) < 0.3f, "the path runs down the middle, through both ends");
        check(at(grid, 0f, 0.5f) == 1f && at(grid, 1f, 0.5f) == 1f, "grass at both sides");
        check(at(grid, 0.2f, 0.5f) > 0.5f, "the grass reaches a little way in");
    }

    private static void aPathTurningACornerKeepsTheTurn() {
        float[] corners = new float[4], grid = new float[9];
        // Grass to the north and west and inside the bend to the south-east: the path comes in from the east and goes out south.
        check(cover(new String[] {"ggggg", "ggggg", "gg...", "gg.gg", "gg.gg"}, corners, grid), "laid by its grid");
        check(at(grid, 0.5f, 0.5f) < 0.3f && at(grid, 1f, 0.5f) < 0.3f && at(grid, 0.5f, 1f) < 0.3f, "the path goes from the east edge through the middle to the south edge");
    }

    private static void aPathOnTheSlantGoesOnThroughItsCorners() {
        float[] corners = new float[4], grid = new float[9];
        // Path squares to the north-east and south-west, grass on all four sides.
        check(cover(new String[] {"gggg.", "ggg.g", "gg.gg", "g.ggg", ".gggg"}, corners, grid), "laid by its grid");
        check(corners[1] == 0f && corners[3] == 0f, "the path's own corners are not covered");
        check(corners[0] == 1f && corners[2] == 1f, "the other two are");
        check(at(grid, 0.5f, 0.5f) < 0.3f, "and the middle is path");
    }

    private static void aPathSteppingOnTheSlantIsNotCutAtItsSteps() {
        float[] corners = new float[4], grid = new float[9];
        // The step's square, where the path comes in from the west and goes on south: grass to the north and east, and in the turn to the south-west.
        check(cover(STEP, corners, grid), "laid by its grid");
        check(corners[3] == 0f, "the corner the path turns round, grass only across its diagonal, is the path's");
        check(at(grid, 0f, 0.5f) < 0.3f && at(grid, 0.5f, 0.5f) < 0.3f && at(grid, 0.5f, 1f) < 0.3f && at(grid, 0.2f, 0.8f) < 0.3f,
                "and the path runs from the west edge round it to the south edge");
        check(corners[0] == 1f && corners[1] == 1f, "grass along the north still covers its corners");
    }

    /** Every pair of the map's own squares side by side laid alike along the edge between them. The map is rows of 'g' for the spreading ground and '.' for the own. */
    private static void squaresAgree(String[] map, String what) {
        int high = map.length, wide = map[0].length();
        float[][] grids = new float[high * wide][];
        for (int y = 0; y < high; y++) {
            for (int x = 0; x < wide; x++) {
                if (map[y].charAt(x) != '.') {
                    continue;
                }
                grids[y * wide + x] = new float[9];
                cover(map, x, y, new float[4], grids[y * wide + x]);
            }
        }
        for (int y = 0; y < high; y++) {
            for (int x = 0; x < wide; x++) {
                float[] here = grids[y * wide + x];
                float[] east = x + 1 < wide ? grids[y * wide + x + 1] : null, south = y + 1 < high ? grids[(y + 1) * wide + x] : null;
                for (float t = 0f; here != null && t <= 1f; t += 0.125f) {
                    check(east == null || Math.abs(at(here, 1f, t) - at(east, 0f, t)) < 1e-6f, what + ": " + x + ", " + y + " and the square east of it at " + t);
                    check(south == null || Math.abs(at(here, t, 1f) - at(south, t, 0f)) < 1e-6f, what + ": " + x + ", " + y + " and the square south of it at " + t);
                }
            }
        }
    }

    private static void squaresAlongAPathSteppingOnTheSlantAgreeOnTheEdgesTheyShare() {
        squaresAgree(new String[] {"gggggg", "g..ggg", "gg..gg", "ggg..g", "gggggg"}, "a path stepping on the slant");
        squaresAgree(new String[] {"ggggg", "g...g", "g.ggg", "ggggg"}, "a path turning a corner");
        squaresAgree(new String[] {".....", ".....", "..g..", ".....", "....."}, "a square of grass in a yard");
        squaresAgree(new String[] {"gg...", "gg...", ".....", "....."}, "the corner of a lawn");
        squaresAgree(new String[] {"g.g.gg..", "..gg.g.g", "g...g..g", ".gg..g.g", "g..gg..."}, "grass in patches");
        // And the path is whole: each step's own squares keep their middles and the corner between them.
        float[] corners = new float[4], grid = new float[9];
        cover(STEP, corners, grid);
        check(at(grid, 0.5f, 0.5f) < 0.3f && at(grid, 0f, 0.5f) < 0.3f && at(grid, 0.5f, 1f) < 0.3f, "a step's square has the path from its west edge to its south edge");
    }

    private static void theEndOfAPathIsRounded() {
        float[] corners = new float[4], grid = new float[9];
        check(cover(new String[] {"gg.gg", "gg.gg", "gg.gg", "ggggg", "ggggg"}, corners, grid), "laid by its grid");
        check(at(grid, 0.5f, 0.5f) < 0.3f && at(grid, 0.5f, 0f) < 0.3f, "the path comes in from the north as far as the middle");
        check(at(grid, 0.5f, 1f) == 1f, "and grass closes it at the south");
    }

    private static void twoPathSquaresAgreeOnTheEdgeTheyShare() {
        float[] north = new float[9], south = new float[9], corners = new float[4];
        // A path running north to south between grass, the north square of it and the one south of it.
        cover(PATH, 2, 1, corners, north);
        cover(PATH, 2, 2, corners, south);
        for (float a = 0f; a <= 1f; a += 0.125f) {
            check(Math.abs(at(north, a, 1f) - at(south, a, 0f)) < 1e-6f, "the same at " + a + " along the edge");
        }
    }

    private static void theCornerOfALawnIsRounded() {
        String[] lawn = {"gg...", "gg...", ".....", "....."};
        float[] corners = new float[4], east = new float[9], south = new float[9], beyond = new float[9];
        cover(lawn, 2, 1, corners, east);
        cover(lawn, 1, 2, corners, south);
        cover(lawn, 2, 2, corners, beyond);
        for (float t = 0f; t <= 1f; t += 0.125f) {
            check(at(east, 0f, t) == 1f, "the square east of the lawn's corner has grass all along its west edge, at " + t);
            check(at(south, t, 0f) == 1f, "the square south of it all along its north edge, at " + t);
        }
        check(corners[0] == 1f, "and the square across the corner has grass at the corner it touches");
    }

    private static void aLoneSquareOfGrassIsRounded() {
        String[] yard = {".....", ".....", "..g..", ".....", "....."};
        float[] corners = new float[4], grid = new float[9];
        cover(yard, 2, 1, corners, grid);
        for (float t = 0f; t <= 1f; t += 0.125f) {
            check(at(grid, t, 1f) == 1f, "the square north of it has grass all along its south edge, at " + t);
        }
        cover(yard, 1, 1, corners, grid);
        check(corners[2] == 1f, "and the square across its corner has grass at that corner");
    }

    private static void aPathSteppingBesideAnotherGrassIsNotCutAtItsSteps() {
        float[] corners = new float[4], grid = new float[9];
        // As STEP, but past the turn the far side of the path is another grass, which does not spread over it.
        cover(new String[] {"ggggg", "ggxxg", "...xx", "gg...", "ggggg"}, corners, grid);
        check(corners[3] == 0f, "the corner the path turns round is still the path's");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}

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
        Checks.run("theEndOfAPathIsRounded", SpreadGroundTests::theEndOfAPathIsRounded);
        Checks.run("twoPathSquaresAgreeOnTheEdgeTheyShare", SpreadGroundTests::twoPathSquaresAgreeOnTheEdgeTheyShare);
    }

    private static final int W = FloorBuilder.OWN, G = FloorBuilder.SPREADING;

    /** How much shows at (a, b) of the square, as SpriteGeometry reads the grid. */
    private static float at(float[] grid, float a, float b) {
        int i = a < 0.5f ? 0 : 1, j = b < 0.5f ? 0 : 1;
        float u = a * 2f - i, v = b * 2f - j;
        int k = j * 3 + i;
        return grid[k] * (1f - u) * (1f - v) + grid[k + 1] * u * (1f - v) + grid[k + 4] * u * v + grid[k + 3] * (1f - u) * v;
    }

    private static void aStraightEdgeIsLaidByItsCornersAsBefore() {
        float[] corners = new float[4], grid = new float[9];
        boolean byGrid = FloorBuilder.coverFrom(new int[] {G, W, W, G, W, W, G, W, W}, corners, grid);
        check(!byGrid, "grass along the west side alone is laid as one face");
        check(corners[0] == 1f && corners[3] == 1f && corners[1] == 0f && corners[2] == 0f, "covered at the two west corners");
        check(grid[4] == 0.5f && grid[3] == 1f && grid[5] == 0f, "the grid says what the corners say");
    }

    private static void aPathOneSquareWideBetweenGrassKeepsItsMiddle() {
        float[] corners = new float[4], grid = new float[9];
        check(FloorBuilder.coverFrom(new int[] {G, W, G, G, W, G, G, W, G}, corners, grid), "laid by its grid");
        check(at(grid, 0.5f, 0.5f) < 0.3f && at(grid, 0.5f, 0f) < 0.3f && at(grid, 0.5f, 1f) < 0.3f, "the path runs down the middle, through both ends");
        check(at(grid, 0f, 0.5f) == 1f && at(grid, 1f, 0.5f) == 1f, "grass at both sides");
        check(at(grid, 0.2f, 0.5f) > 0.5f, "the grass reaches a little way in");
    }

    private static void aPathTurningACornerKeepsTheTurn() {
        float[] corners = new float[4], grid = new float[9];
        // Grass to the north and west and inside the bend to the south-east: the path comes in from the east and goes out south.
        check(FloorBuilder.coverFrom(new int[] {G, G, G, G, W, W, G, W, G}, corners, grid), "laid by its grid");
        check(at(grid, 0.5f, 0.5f) < 0.3f && at(grid, 1f, 0.5f) < 0.3f && at(grid, 0.5f, 1f) < 0.3f, "the path goes from the east edge through the middle to the south edge");
    }

    private static void aPathOnTheSlantGoesOnThroughItsCorners() {
        float[] corners = new float[4], grid = new float[9];
        // Path squares to the north-east and south-west, grass on all four sides.
        check(FloorBuilder.coverFrom(new int[] {G, G, W, G, W, G, W, G, G}, corners, grid), "laid by its grid");
        check(corners[1] == 0f && corners[3] == 0f, "the path's own corners are not covered");
        check(corners[0] == 1f && corners[2] == 1f, "the other two are");
        check(at(grid, 0.5f, 0.5f) < 0.3f, "and the middle is path");
    }

    private static void aPathSteppingOnTheSlantIsNotCutAtItsSteps() {
        float[] corners = new float[4], grid = new float[9];
        // A step of a path on the slant, which comes in from the west and goes on south: grass to the north and east, and in the turn to the south-west.
        check(FloorBuilder.coverFrom(new int[] {G, G, G, W, W, G, G, W, W}, corners, grid), "laid by its grid");
        check(corners[3] == 0f, "the corner the path turns round, grass only across its diagonal, is the path's");
        check(at(grid, 0f, 0.5f) < 0.3f && at(grid, 0.5f, 0.5f) < 0.3f && at(grid, 0.5f, 1f) < 0.3f && at(grid, 0.2f, 0.8f) < 0.3f,
                "and the path runs from the west edge round it to the south edge");
        check(corners[0] == 1f && corners[1] == 1f, "grass along the north still covers its corners");
    }

    private static void theEndOfAPathIsRounded() {
        float[] corners = new float[4], grid = new float[9];
        check(FloorBuilder.coverFrom(new int[] {G, W, G, G, W, G, G, G, G}, corners, grid), "laid by its grid");
        check(at(grid, 0.5f, 0.5f) < 0.3f && at(grid, 0.5f, 0f) < 0.3f, "the path comes in from the north as far as the middle");
        check(at(grid, 0.5f, 1f) == 1f, "and grass closes it at the south");
    }

    private static void twoPathSquaresAgreeOnTheEdgeTheyShare() {
        float[] north = new float[9], south = new float[9], corners = new float[4];
        // A path running north to south between grass, the north square of it and the one south of it.
        FloorBuilder.coverFrom(new int[] {G, W, G, G, W, G, G, W, G}, corners, north);
        FloorBuilder.coverFrom(new int[] {G, W, G, G, W, G, G, W, G}, corners, south);
        for (float a = 0f; a <= 1f; a += 0.125f) {
            check(Math.abs(at(north, a, 1f) - at(south, a, 0f)) < 1e-6f, "the same at " + a + " along the edge");
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}

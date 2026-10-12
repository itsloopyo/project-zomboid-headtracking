package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.function.BiFunction;

/**
 * Tests of what is read off a tiled wall's sprite stood square on its wall: see WallTiles. Each
 * wall is drawn as the game draws it at its own scale, 64 dots long, and again twice as large,
 * with every dot of it two each way: each case runs at both.
 */
final class WallTileTests {
    private static final int ACROSS = 64, HIGH = 192;
    private static final float[] BLUE = {0.42f, 0.62f, 0.71f}, CREAM = {0.90f, 0.90f, 0.78f}, PEACH = {0.80f, 0.74f, 0.66f}, NAVY = {0.20f, 0.25f, 0.30f};
    /** How many dots each dot of a wall below is drawn as, each way. */
    private static int scale = 1;

    private WallTileTests() {}

    static void run() {
        for (int each = 1; each <= 2; each++) {
            scale = each;
            String at = each == 1 ? "" : " at twice the scale";
            Checks.run("blueTilesAreTheirJointsAndCourses" + at, WallTileTests::blueTilesAreTheirJointsAndCourses);
            Checks.run("paintOverABandOfTilesIsThreeBands" + at, WallTileTests::paintOverABandOfTilesIsThreeBands);
            Checks.run("plainPaintIsNoTiles" + at, WallTileTests::plainPaintIsNoTiles);
            Checks.run("stripedPaperIsNoTiles" + at, WallTileTests::stripedPaperIsNoTiles);
            Checks.run("courseWithAFigureKeepsItsFigure" + at, WallTileTests::courseWithAFigureKeepsItsFigure);
            Checks.run("figureWhereTilesMeetIsOnBothCourses" + at, WallTileTests::figureWhereTilesMeetIsOnBothCourses);
            Checks.run("shadingAndASpeckAreNoFigure" + at, WallTileTests::shadingAndASpeckAreNoFigure);
            Checks.run("darkTilesKeepTheirCourses" + at, WallTileTests::darkTilesKeepTheirCourses);
            Checks.run("wallWithAWindowIsNotWhole" + at, WallTileTests::wallWithAWindowIsNotWhole);
            Checks.run("doorInTilesUnderPaintIsReadAsNotWhole" + at, WallTileTests::doorInTilesUnderPaintIsReadAsNotWhole);
            Checks.run("jointsKeepTheirStepPastAJambThatIsNoJoint" + at, WallTileTests::jointsKeepTheirStepPastAJambThatIsNoJoint);
        }
        scale = 1;
        Checks.run("aTileIsPaintedInItsOwnColourFinely", WallTileTests::aTileIsPaintedInItsOwnColourFinely);
    }

    /**
     * A wall stood square, drawn at the scale in hand: colour gives the colour of each dot of it
     * at the game's own scale, or null for no wall there. As the game's walls, the last four
     * dots of each row are unpainted.
     */
    private static WallTiles.Wall read(BiFunction<Integer, Integer, float[]> colour) {
        int across = ACROSS * scale, high = HIGH * scale;
        float[] rgb = new float[across * high * 3];
        boolean[] solid = new boolean[across * high];
        for (int row = 0; row < high; row++) {
            for (int column = 0; column < across - 4 * scale; column++) {
                float[] dot = colour.apply(column / scale, row / scale);
                if (dot != null) {
                    int at = row * across + column;
                    solid[at] = true;
                    System.arraycopy(dot, 0, rgb, at * 3, 3);
                }
            }
        }
        return WallTiles.read(rgb, solid, across, high);
    }

    /** Whether a joint is where the game's dot at column is, as near as a dot at the scale in hand. */
    private static boolean jointAt(float joint, int column) {
        return Math.abs(joint - (column + 0.5f) / ACROSS) <= 0.75f / ACROSS;
    }

    private static float[] shaded(float[] colour, float by) {
        return new float[] {colour[0] * by, colour[1] * by, colour[2] * by};
    }

    /** Tiles as the game paints them: a dark joint dot every step from phase across, a dark row and a pale one every courseStep rows from courseFrom. */
    private static float[] tiles(float[] colour, int column, int row, int phase, int step, int courseFrom, int courseStep) {
        if (Math.floorMod(column - phase, step) == 0) {
            return shaded(colour, 0.82f);
        }
        int inCourse = Math.floorMod(row - courseFrom, courseStep);
        return inCourse == 0 ? shaded(colour, 0.9f) : inCourse == 1 ? shaded(colour, 1.1f) : colour;
    }

    private static void blueTilesAreTheirJointsAndCourses() {
        WallTiles.Wall wall = read((column, row) -> tiles(BLUE, column, row, 11, 16, 27, 22));
        check(wall != null && wall.whole && wall.bands.length == 1, "a wall of blue tiles is one band, whole");
        WallTiles.Band band = wall.bands[0];
        check(band.tiled && band.joints.length == 4, "with four joints along it, the last where the sprite stops short: " + band.joints.length);
        check(jointAt(band.joints[0], 11) && jointAt(band.joints[3], 59) && Math.abs(band.step - 16f / ACROSS) < 0.001f, "a step of 16 dots from the eleventh: " + band.phase + " " + band.step);
        check(band.courses.length >= 7, "and a joint between every course: " + band.courses.length);
        for (int c = 1; c < band.courses.length; c++) {
            check(Math.abs(band.courses[c] - band.courses[c - 1] - 22f / HIGH) < 0.012f, "courses evenly apart, from the foot up");
        }
        check(band.jointRed < BLUE[0] && band.jointBlue < BLUE[2], "the joints are darker than the tiles");
        check(band.foot == 0f && band.head == 1f, "and the band is the whole wall");
    }

    private static void paintOverABandOfTilesIsThreeBands() {
        WallTiles.Wall wall = read((column, row) -> row < 157 ? CREAM : row < 161 ? shaded(PEACH, 0.7f) : tiles(PEACH, column, row, 15, 22, 400, 1000));
        check(wall != null && wall.bands.length == 3, "tiles to a third of the way up, a strip and paint over them: " + (wall == null ? "none" : wall.bands.length));
        check(wall.bands[0].tiled && wall.bands[0].joints.length == 3, "the lowest is tiles, joints 22 dots apart");
        check(!wall.bands[1].tiled && wall.bands[1].head - wall.bands[1].foot < 0.03f, "then a thin strip of trim");
        check(!wall.bands[2].tiled && wall.bands[2].head == 1f, "and paint to the top");
        check(Math.abs(wall.bands[0].head - (1f - 161f / HIGH)) < 0.006f, "the tiles stopping at their line: " + wall.bands[0].head);
        check(Math.abs(wall.bands[2].red - CREAM[0]) < 0.01f, "the paint in its own colour");
    }

    private static void plainPaintIsNoTiles() {
        check(read((column, row) -> CREAM) == null, "a wall painted one colour has no tiles");
    }

    private static void stripedPaperIsNoTiles() {
        check(read((column, row) -> column / 8 % 2 == 0 ? CREAM : PEACH) == null, "nor one papered in stripes, whose edges are darker than one side only");
    }

    private static void courseWithAFigureKeepsItsFigure() {
        WallTiles.Wall wall = read((column, row) -> {
            boolean diamond = row >= 130 && row < 148 && Math.abs(Math.floorMod(column - 11, 16) - 8) + Math.abs(row - 139) < 4;
            return diamond ? new float[] {0.2f, 0.5f, 0.6f} : tiles(CREAM, column, row, 11, 16, 6, 38);
        });
        check(wall != null && wall.bands.length == 1 && wall.bands[0].tiled, "a wall of tiles with a row of diamonds on them is tiles");
        WallTiles.Band band = wall.bands[0];
        int figures = 0, at = -1;
        for (int c = 0; c < band.figured.length; c++) {
            figures += band.figured[c] ? 1 : 0;
            at = band.figured[c] ? c : at;
        }
        check(figures == 1, "and one course of it shows its figure: " + figures);
        if (at < 0) {
            return;
        }
        // Row 139 is in the course from row 122 to 158, counted from the foot.
        float middle = 1f - 139f / HIGH;
        float low = at == 0 ? 0f : band.courses[at - 1], high = at == band.courses.length ? 1f : band.courses[at];
        check(low < middle && middle < high, "the one the diamonds are on");
    }

    private static void figureWhereTilesMeetIsOnBothCourses() {
        // A diamond where four tiles meet at every other joint of every course line, as the game paints them: half of each on the
        // course under its line and half on the course over it.
        WallTiles.Wall wall = read((column, row) -> {
            int across = Math.floorMod(column - 11, 32), up = Math.floorMod(row - 28, 22);
            boolean diamond = Math.min(across, 32 - across) + Math.min(up, 22 - up) < 5;
            return diamond ? new float[] {0.2f, 0.5f, 0.6f} : tiles(CREAM, column, row, 11, 16, 27, 22);
        });
        check(wall != null && wall.bands[0].tiled, "a wall of tiles with a diamond where tiles meet is tiles");
        boolean all = true;
        for (boolean figured : wall.bands[0].figured) {
            all &= figured;
        }
        check(all, "and every course of it shows the sprite's dots, so a figure across a joint is not cut in half");
    }

    private static void shadingAndASpeckAreNoFigure() {
        // The game shades a wall darker under the ceiling, and a few dots of one tile are a speck: neither is a figure.
        WallTiles.Wall wall = read((column, row) -> {
            float[] tile = tiles(BLUE, column, row, 11, 16, 27, 22);
            boolean speck = column >= 34 && column < 36 && row >= 100 && row < 102;
            return row < 4 ? shaded(tile, 0.55f) : speck ? new float[] {0.9f, 0.2f, 0.2f} : tile;
        });
        boolean none = wall != null;
        for (boolean figured : wall == null ? new boolean[0] : wall.bands[0].figured) {
            none &= !figured;
        }
        check(none, "no course of plain tiles shaded under the ceiling, with a speck on one tile, has a figure");
    }

    private static void darkTilesKeepTheirCourses() {
        WallTiles.Wall wall = read((column, row) -> tiles(NAVY, column, row, 11, 16, 27, 22));
        check(wall != null && wall.bands.length == 1 && wall.bands[0].courses.length >= 7, "dark tiles keep their course joints, a tenth darker as on white ones: "
                + (wall == null ? "none" : wall.bands.length + " bands"));
    }

    private static void wallWithAWindowIsNotWhole() {
        WallTiles.Wall wall = read((column, row) -> column >= 12 && column < 36 && row >= 45 && row < 120 ? null : tiles(BLUE, column, row, 11, 16, 27, 22));
        check(wall != null && !wall.whole, "a wall of tiles with a window in it says it is not whole, and takes a whole one's reading");
    }

    private static void doorInTilesUnderPaintIsReadAsNotWhole() {
        // A door to the floor through tiles a sixth of the way up under paint: nothing of the tiles is left to read beside it.
        WallTiles.Wall wall = read((column, row) -> column >= 12 && column < 44 && row >= 40 ? null
                : row < 157 ? CREAM : row < 161 ? shaded(PEACH, 0.7f) : tiles(PEACH, column, row, 15, 22, 400, 1000));
        check(wall != null && !wall.whole, "a door through tiles under paint is read, and says it is not whole, so it can take a whole wall's tiles");
        boolean paint = false;
        for (WallTiles.Band band : wall == null ? new WallTiles.Band[0] : wall.bands) {
            paint |= !band.tiled && Math.abs(band.red - CREAM[0]) < 0.02f;
        }
        check(paint, "and what it says of itself, the paint over the door, is there to check a whole wall's against");
    }

    private static void jointsKeepTheirStepPastAJambThatIsNoJoint() {
        // A dark line seven dots from the joint before it, as a window's jamb is, is no joint.
        WallTiles.Wall wall = read((column, row) -> column == 4 ? shaded(BLUE, 0.6f) : tiles(BLUE, column, row, 11, 16, 27, 22));
        check(wall != null && wall.bands[0].joints.length == 4 && jointAt(wall.bands[0].joints[0], 11), "the joints are still every 16 dots from the eleventh");
    }

    private static void aTileIsPaintedInItsOwnColourFinely() {
        float[] told = new float[4];
        WallSides.tint(WallSides.laid(0.48f, 0.56f, 0.59f, 0f, 0f, 0f, WallSides.FINE), told);
        check(told[3] == WallSides.FINE, "the picture is told a tile is painted finely: " + told[3]);
        check(Math.abs(told[0] - 0.48f) <= 0.5f / 31f && Math.abs(told[1] - 0.56f) <= 0.5f / 31f && Math.abs(told[2] - 0.59f) <= 0.5f / 31f,
                "and its colour to within half a step of 31");
    }
}

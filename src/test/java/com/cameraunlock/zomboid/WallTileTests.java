package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.function.BiFunction;
import zombie.core.textures.Texture;
import zombie.iso.SpriteDetails.IsoObjectType;

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
            Checks.run("panelOverAWallIsTilesOnlyWhereItPaints" + at, WallTileTests::panelOverAWallIsTilesOnlyWhereItPaints);
            Checks.run("paintOverAWallIsNoTiles" + at, WallTileTests::paintOverAWallIsNoTiles);
            Checks.run("aHarlequinOverAPanelShowsItsDots" + at, WallTileTests::aHarlequinOverAPanelShowsItsDots);
            Checks.run("aPanelsCappingIsAStripOfIt" + at, WallTileTests::aPanelsCappingIsAStripOfIt);
            Checks.run("smallTilesOverLargeOfOneColourAreTwoGrids" + at, WallTileTests::smallTilesOverLargeOfOneColourAreTwoGrids);
            Checks.run("aBorderOfRosettesIsNoCourseJoint" + at, WallTileTests::aBorderOfRosettesIsNoCourseJoint);
            Checks.run("faintQuiltingOverTilesShowsItsDots" + at, WallTileTests::faintQuiltingOverTilesShowsItsDots);
            Checks.run("aBandIsTheColourOfMostOfItsRows" + at, WallTileTests::aBandIsTheColourOfMostOfItsRows);
            Checks.run("everyTileOfAPanelIsAFace" + at, WallTileTests::everyTileOfAPanelIsAFace);
        }
        scale = 1;
        Checks.run("aTileIsPaintedInItsOwnColourFinely", WallTileTests::aTileIsPaintedInItsOwnColourFinely);
        Checks.run("evenCoursesAreOneFaceWithWholeNumbersAtTheirLines", WallTileTests::evenCoursesAreOneFaceWithWholeNumbersAtTheirLines);
        Checks.run("aBandsEdgeIsNeverOnAWholeNumber", WallTileTests::aBandsEdgeIsNeverOnAWholeNumber);
        Checks.run("aCourseWithAFigureIsAFaceOfItsOwn", WallTileTests::aCourseWithAFigureIsAFaceOfItsOwn);
        Checks.run("unevenCoursesAreTwoFaces", WallTileTests::unevenCoursesAreTwoFaces);
        Checks.run("aDoorWallTakesTheWholeWallBesideItInItsRoom", WallTileTests::aDoorWallTakesTheWholeWallBesideItInItsRoom);
        Checks.run("aDoorWallLooksNoFurtherThanItsRoomAndItsWall", WallTileTests::aDoorWallLooksNoFurtherThanItsRoomAndItsWall);
        Checks.run("aDoorWallPassesAWholeWallThatDisagreesWithIt", WallTileTests::aDoorWallPassesAWholeWallThatDisagreesWithIt);
        Checks.run("aWholeWallNotReadYetLeavesTheBuildToBeMadeAgain", WallTileTests::aWholeWallNotReadYetLeavesTheBuildToBeMadeAgain);
        Checks.run("aSpriteReadAsAWallIsReadAgainLaidOverOne", WallTileTests::aSpriteReadAsAWallIsReadAgainLaidOverOne);
        Checks.run("theFarSideOfAWallTakesTheTilesOfTheRoomBeyondOnEitherEdge", WallTileTests::theFarSideOfAWallTakesTheTilesOfTheRoomBeyondOnEitherEdge);
        Checks.run("theFarSideWaitsForTheRoomsTilesToBeRead", WallTileTests::theFarSideWaitsForTheRoomsTilesToBeRead);
        Checks.run("theFarSideTakesNoTilesFromAWallWithAnOpening", WallTileTests::theFarSideTakesNoTilesFromAWallWithAnOpening);
        Checks.run("aPosterOnATiledWallIsThePoster", WallTileTests::aPosterOnATiledWallIsThePoster);
        Checks.run("whatIsLaidOverAWallIsNotOnTheRoomBeyond", WallTileTests::whatIsLaidOverAWallIsNotOnTheRoomBeyond);
    }

    /**
     * A wall stood square, drawn at the scale in hand: colour gives the colour of each dot of it
     * at the game's own scale, or null for no wall there. As the game's walls, the last four
     * dots of each row are unpainted.
     */
    private static WallTiles.Wall read(BiFunction<Integer, Integer, float[]> colour) {
        return read(colour, false);
    }

    /** As read, for a wall or for tiles laid over one. */
    private static WallTiles.Wall read(BiFunction<Integer, Integer, float[]> colour, boolean overlay) {
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
        return WallTiles.read(rgb, solid, across, high, overlay);
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

    private static void panelOverAWallIsTilesOnlyWhereItPaints() {
        // White tiles from row 110 down, with a notch a course and a half high cut out of the top from column 30 on.
        float[] white = {0.92f, 0.92f, 0.92f};
        WallTiles.Wall wall = read((column, row) -> row < 110 || row < 132 && column >= 30 ? null : tiles(white, column, row, 11, 16, 120, 22), true);
        check(wall != null && wall.whole, "a panel of tiles laid over a wall is tiles, and has no opening of its own");
        WallTiles.Band band = null;
        for (WallTiles.Band each : wall == null ? new WallTiles.Band[0] : wall.bands) {
            band = each.tiled ? each : band;
        }
        check(band != null && Math.abs(band.head - (1f - 110f / HIGH)) < 0.01f, "up to the panel's top and no further: " + (band == null ? "none" : band.head));
        if (band == null) {
            return;
        }
        int top = band.courses.length;
        check(band.has(top, 1) && !band.has(top, 2) && !band.has(top, 3), "its top course has the tiles left of the notch and none in it");
        int cut = band.cell(top - 1, 2);
        check(band.has(top - 1, 2) && Math.abs(band.cells[cut + 3] - (1f - 132f / HIGH)) < 0.006f, "the tile the notch's foot crosses is cut to it: "
                + (band.has(top - 1, 2) ? band.cells[cut + 3] : -1f));
        check(band.has(top - 1, 2) && band.cells[cut] == band.joints[1], "and starts at its joint, as a whole tile would");
        check(band.has(0, band.across() - 1) && band.cells[band.cell(0, band.across() - 1) + 1] == 1f, "a tile at the wall's end runs to it, past where the sprite stops short");
    }

    private static void paintOverAWallIsNoTiles() {
        check(read((column, row) -> row < 110 ? null : CREAM, true) == null, "paint laid over a wall is no tiles");
    }

    private static final float[] WHITE = {0.92f, 0.92f, 0.92f}, GREY = {0.66f, 0.65f, 0.62f}, ROSE = {0.70f, 0.30f, 0.30f}, PINK = {0.86f, 0.70f, 0.72f};

    /** The band of a reading that rows from first to last of the wall, the top row first, lie in; null if none or several. */
    private static WallTiles.Band bandOver(WallTiles.Wall wall, int first, int last) {
        WallTiles.Band found = null;
        int count = 0;
        for (WallTiles.Band band : wall == null ? new WallTiles.Band[0] : wall.bands) {
            if (band.foot < 1f - (float) first / HIGH && band.head > 1f - (float) last / HIGH) {
                found = band;
                count++;
            }
        }
        return count == 1 ? found : null;
    }

    /** Tiles of a panel laid over a wall, white, joints every 16 dots from the eleventh and courses every 22 rows from row 120. */
    private static float[] panel(int column, int row) {
        return tiles(WHITE, column, row, 11, 16, 120, 22);
    }

    private static void aHarlequinOverAPanelShowsItsDots() {
        // Diamonds of two colours from row 50 to 118, 16 dots across and 24 rows up (as walls_interior_detailing_bathroom_03 draws
        // them, grey on white: the rows by a diamond's middle are mostly one colour, so they read as thin bands of alternate colour),
        // over a panel of tiles.
        WallTiles.Wall wall = read((column, row) -> {
            if (row < 50) {
                return null;
            }
            if (row >= 118) {
                return panel(column, row);
            }
            float u = column / 16f, v = (row - 50) / 24f;
            return Math.floorMod((int) Math.floor(u + v) + (int) Math.floor(u - v), 2) == 0 ? GREY : WHITE;
        }, true);
        WallTiles.Band pattern = bandOver(wall, 56, 112);
        check(pattern != null && !pattern.tiled && pattern.figured[0], "the diamonds are one band showing the sprite's dots, no stripes of joints: "
                + (wall == null ? "none" : WallTiles.said(wall)));
        WallTiles.Band under = bandOver(wall, 130, 180);
        check(under != null && under.tiled, "and the panel under them is tiles");
    }

    private static void aPanelsCappingIsAStripOfIt() {
        // A panel of tiles from row 110 down, capped by four rows of darker trim: thin enough to read as a line, not a band.
        WallTiles.Wall wall = read((column, row) -> row < 106 ? null : row < 110 ? GREY : panel(column, row), true);
        float top = 0f;
        for (WallTiles.Band band : wall == null ? new WallTiles.Band[0] : wall.bands) {
            top = Math.max(top, band.head);
        }
        check(Math.abs(top - (1f - 106f / HIGH)) < 0.006f, "the panel is built up to its capping's top, the capping a strip of it: " + top);
    }

    private static void smallTilesOverLargeOfOneColourAreTwoGrids() {
        // White tiles every 8 dots from row 40 to 106, and every 16 under them, courses of 11 rows over and 22 under.
        WallTiles.Wall wall = read((column, row) -> row < 40 ? null
                : row < 106 ? tiles(WHITE, column, row, 3, 8, 40, 11) : tiles(WHITE, column, row, 11, 16, 106, 22), true);
        WallTiles.Band small = bandOver(wall, 50, 100), large = bandOver(wall, 120, 180);
        check(small != null && small.tiled && Math.abs(small.step - 8f / ACROSS) < 0.001f, "the small tiles are joints 8 dots apart: "
                + (wall == null ? "none" : WallTiles.said(wall)));
        check(large != null && large.tiled && Math.abs(large.step - 16f / ACROSS) < 0.001f, "and the large ones under them 16, not 8");
    }

    private static void aBorderOfRosettesIsNoCourseJoint() {
        // A border 23 rows high through the middle of a panel of tiles, each row mostly rose or pale pink in turn with the white
        // between, as walls_interior_detailing_bathroom_01 draws its rosettes: every row of it reads as a line of its own colour.
        WallTiles.Wall wall = read((column, row) -> {
            if (row < 40) {
                return null;
            }
            if (row >= 100 && row < 123) {
                return Math.floorMod(column + 3 * row, 10) < 3 ? WHITE : row % 2 == 0 ? ROSE : PINK;
            }
            return panel(column, row);
        }, true);
        boolean shown = false;
        for (WallTiles.Band band : wall == null ? new WallTiles.Band[0] : wall.bands) {
            for (int course = 0; course <= band.courses.length; course++) {
                float foot = course == 0 ? band.foot : band.courses[course - 1], head = course == band.courses.length ? band.head : band.courses[course];
                boolean over = foot < 1f - 105f / HIGH && head > 1f - 118f / HIGH;
                shown |= over && band.figured[band.tiled ? course : 0];
            }
        }
        check(shown, "the border shows the sprite's dots, not a course joint as thick as it: " + (wall == null ? "none" : WallTiles.said(wall)));
    }

    private static void faintQuiltingOverTilesShowsItsDots() {
        // A figure a twelfth darker than the white, from row 40 to 106, over tiles: dots scattered with no joints in them, as the
        // quilting of walls_interior_detailing_bathroom_03 reads (its lines run across every column alike).
        WallTiles.Wall wall = read((column, row) -> {
            if (row < 40) {
                return null;
            }
            if (row >= 106) {
                return tiles(WHITE, column, row, 11, 16, 106, 22);
            }
            return Math.floorMod((column * 73856093) ^ (row * 19349663), 9) == 0 ? shaded(WHITE, 0.91f) : WHITE;
        }, true);
        WallTiles.Band quilted = bandOver(wall, 50, 100);
        check(quilted != null && !quilted.tiled && quilted.figured[0], "the figure shows the sprite's dots, though it is a shade off the white: "
                + (wall == null ? "none" : WallTiles.said(wall)));
    }

    private static void aBandIsTheColourOfMostOfItsRows() {
        // Under a figure, the row of a band of tiles next to it is a shade warmer.
        WallTiles.Wall wall = read((column, row) -> row < 40 ? null : row < 100 ? GREY : row == 100 ? new float[] {0.95f, 0.90f, 0.87f} : panel(column, row), true);
        WallTiles.Band band = bandOver(wall, 130, 180);
        check(band != null && Math.abs(band.red - WHITE[0]) < 0.01f && Math.abs(band.blue - WHITE[2]) < 0.01f,
                "the tiles are white, not the warmer row they start at: " + (band == null ? "none" : band.red + " " + band.green + " " + band.blue));
    }

    private static void everyTileOfAPanelIsAFace() {
        // As panelOverAWallIsTilesOnlyWhereItPaints, with a row of diamonds on one course: every tile there is, figured or not, is under a face.
        float[] white = {0.92f, 0.92f, 0.92f};
        WallTiles.Wall wall = read((column, row) -> {
            if (row < 110 || row < 132 && column >= 30) {
                return null;
            }
            boolean diamond = row >= 145 && row < 163 && Math.abs(Math.floorMod(column - 11, 16) - 8) + Math.abs(row - 153) < 4;
            return diamond ? new float[] {0.2f, 0.5f, 0.6f} : tiles(white, column, row, 11, 16, 120, 22);
        }, true);
        check(wall != null, "a panel of tiles is read");
        if (wall == null) {
            return;
        }
        java.util.ArrayList<WallTiles.Face> faces = WallTiles.overlayFaces(wall, 0.004f, 0.004f / ViewCamera.LEVEL_HEIGHT);
        boolean covered = true, pictured = false;
        for (WallTiles.Band band : wall.bands) {
            for (int course = 0; course <= band.courses.length; course++) {
                for (int tile = 0; tile < band.across(); tile++) {
                    if (!band.has(course, tile)) {
                        continue;
                    }
                    int at = band.cell(course, tile);
                    float along = (band.cells[at] + band.cells[at + 1]) / 2f, up = (band.cells[at + 2] + band.cells[at + 3]) / 2f;
                    boolean under = false;
                    for (WallTiles.Face face : faces) {
                        under |= face.band == band && face.start <= along && along <= face.stop && face.low <= up && up <= face.high;
                        pictured |= face.kind == WallTiles.Face.PICTURE;
                    }
                    covered &= under;
                }
            }
        }
        check(covered, "every tile the panel paints is under a face");
        check(pictured, "and the course with diamonds is faces of the sprite's dots");
    }

    private static void aTileIsPaintedInItsOwnColourFinely() {
        float[] told = new float[4];
        WallSides.tint(WallSides.laid(0.48f, 0.56f, 0.59f, 0f, 0f, 0f, WallSides.FINE), told);
        check(told[3] == WallSides.FINE, "the picture is told a tile is painted finely: " + told[3]);
        check(Math.abs(told[0] - 0.48f) <= 0.5f / 31f && Math.abs(told[1] - 0.56f) <= 0.5f / 31f && Math.abs(told[2] - 0.59f) <= 0.5f / 31f,
                "and its colour to within half a step of 31");
    }

    /** A band of tiles from foot to head with course lines at those heights. */
    private static WallTiles.Band band(float foot, float head, float... courses) {
        WallTiles.Band band = new WallTiles.Band();
        band.tiled = true;
        band.foot = foot;
        band.head = head;
        band.courses = courses;
        band.figured = new boolean[courses.length + 1];
        return band;
    }

    /** The texture coordinate up a stretch's face at height h. */
    private static float v(WallTiles.Stretch stretch, float h) {
        return stretch.footV + (h - stretch.low) * stretch.perLevel;
    }

    private static boolean whole(float v) {
        return Math.abs(v - Math.round(v)) < 1e-3f;
    }

    private static boolean clearOfWhole(float v) {
        return Math.abs(v - Math.round(v)) > 0.15f;
    }

    private static void evenCoursesAreOneFaceWithWholeNumbersAtTheirLines() {
        WallTiles.Band band = band(0f, 1f, 0.15f, 0.265f, 0.38f, 0.495f, 0.61f, 0.725f, 0.84f);
        java.util.ArrayList<WallTiles.Stretch> laid = WallTiles.stretches(band, 0.002f);
        check(laid.size() == 1 && laid.get(0).first == 0 && laid.get(0).last == 7, "a wall of evenly laid tiles is one face: " + laid.size());
        boolean lines = true;
        for (float line : band.courses) {
            lines &= whole(v(laid.get(0), line));
        }
        check(lines, "with its joints laid where its texture coordinate up it is a whole number, at every course line");
        check(clearOfWhole(v(laid.get(0), 0f)) && clearOfWhole(v(laid.get(0), 1f)), "and none at the floor or the ceiling");
    }

    private static void aBandsEdgeIsNeverOnAWholeNumber() {
        // Courses a tenth apart from 0.11: the floor would fall at -1.1, near enough a line to draw one along the skirting.
        WallTiles.Band band = band(0f, 0.565f, 0.11f, 0.21f, 0.31f, 0.41f, 0.51f);
        java.util.ArrayList<WallTiles.Stretch> laid = WallTiles.stretches(band, 0.002f);
        boolean clear = true;
        for (WallTiles.Stretch stretch : laid) {
            clear &= stretch.first > 0 || clearOfWhole(v(stretch, band.foot));
            clear &= stretch.last < band.courses.length || clearOfWhole(v(stretch, band.head));
            for (int line = Math.max(0, stretch.first - 1); line < Math.min(band.courses.length, stretch.last + 1); line++) {
                clear &= whole(v(stretch, band.courses[line]));
            }
        }
        check(laid.size() == 2 && clear, "a band whose floor a step from its lines would put on a line gives the course there a face of its own: " + laid.size());
    }

    private static void aCourseWithAFigureIsAFaceOfItsOwn() {
        WallTiles.Band band = band(0f, 1f, 0.25f, 0.45f, 0.65f, 0.85f);
        band.figured[2] = true;
        java.util.ArrayList<WallTiles.Stretch> laid = WallTiles.stretches(band, 0.002f);
        check(laid.size() == 3 && !laid.get(0).figured && laid.get(1).figured && !laid.get(2).figured, "plain courses, the course with the figure, plain courses: " + laid.size());
        if (laid.size() != 3) {
            return;
        }
        check(laid.get(0).high == 0.45f + 0.002f && laid.get(2).low == 0.65f - 0.002f, "the plain faces run on half a joint past the lines they share with it, so those are drawn whole");
        check(laid.get(1).low == 0.45f + 0.002f && laid.get(1).high == 0.65f - 0.002f, "and it starts half a joint past them");
        check(whole(v(laid.get(0), 0.45f)) && whole(v(laid.get(2), 0.65f)), "the lines are whole numbers on the plain faces");
        check(WallTiles.stretches(band, 0.002f) == laid, "a band is laid once, and the same at every asking");
        java.util.ArrayList<WallTiles.Stretch> wider = WallTiles.stretches(band, 0.003f);
        check(wider != laid && wider.get(1).low == 0.45f + 0.003f, "and laid again for joints of another width");
    }

    private static void unevenCoursesAreTwoFaces() {
        // Lines a fifth apart, then a tenth: one step does not put a whole number on all of them.
        WallTiles.Band band = band(0f, 1f, 0.2f, 0.4f, 0.6f, 0.7f, 0.8f, 0.9f);
        java.util.ArrayList<WallTiles.Stretch> laid = WallTiles.stretches(band, 0.002f);
        boolean lines = true;
        for (WallTiles.Stretch stretch : laid) {
            for (int line = Math.max(0, stretch.first - 1); line < Math.min(band.courses.length, stretch.last + 1); line++) {
                lines &= whole(v(stretch, band.courses[line]));
            }
        }
        check(laid.size() >= 2 && lines, "courses laid at two steps are two faces, each with whole numbers at its own lines: " + laid.size());
    }

    private static final String SHEET = "walls_interior_bathroom_01_";

    /**
     * A test map with north walls of the bathroom sheet on squares 0 to 4 of row 0, all in room
     * 1, each sprite with a texture of its own (id 100 + x) read as no tiles, but on the squares
     * named bare, which have no wall.
     */
    private static TestMap bathroom(int... bare) {
        TestMap map = new TestMap();
        map.build.tiledSheet = SHEET;
        for (int x = 0; x <= 4; x++) {
            map.cell.square(x, 0, 0).setRoomID(1L);
            boolean none = false;
            for (int each : bare) {
                none |= each == x;
            }
            if (!none) {
                Texture texture = new Texture(100 + x, 4, 4);
                texture.paint(0, 0, 4, 4);
                map.cell.square(x, 0, 0).add(map.spriteWith(SHEET + (x == 1 ? 15 : 13), IsoObjectType.wall, texture, "WallN", ""));
                WallTiles.knownForTests(texture, true, false, null);
            }
        }
        return map;
    }

    private static Texture textureAt(TestMap map, int x) {
        return map.cell.square(x, 0, 0).getObjects().get(0).getSprite().getTextureForCurrentFrame(null);
    }

    /** A whole wall's reading, of peach tiles to a sixth of the way up under cream paint, or the paint alone (with an opening) as a door wall reads. */
    private static WallTiles.Wall reading(boolean whole, float[] paint) {
        WallTiles.Wall wall = new WallTiles.Wall();
        wall.whole = whole;
        WallTiles.Band tiles = band(0f, 0.16f);
        tiles.red = PEACH[0];
        tiles.green = PEACH[1];
        tiles.blue = PEACH[2];
        WallTiles.Band above = new WallTiles.Band();
        above.foot = 0.16f;
        above.head = 1f;
        above.red = paint[0];
        above.green = paint[1];
        above.blue = paint[2];
        wall.bands = whole ? new WallTiles.Band[] {tiles, above} : new WallTiles.Band[] {above};
        if (!whole) {
            above.foot = 0.8f;
        }
        return wall;
    }

    private static void aDoorWallTakesTheWholeWallBesideItInItsRoom() {
        TestMap map = bathroom();
        WallTiles.Wall whole = reading(true, CREAM);
        WallTiles.knownForTests(textureAt(map, 2), true, false, whole);
        check(map.build.wallBuilder.tilesOf(textureAt(map, 1), 1, 0, 0, 1f, 0f, true) == whole, "a door through tiles under paint takes the whole wall beside it");
        WallTiles.knownForTests(textureAt(map, 1), true, false, reading(false, CREAM));
        check(map.build.wallBuilder.tilesOf(textureAt(map, 1), 1, 0, 0, 1f, 0f, true) == whole, "and does so where it read its paint over the door, which agrees");
    }

    private static void aDoorWallLooksNoFurtherThanItsRoomAndItsWall() {
        TestMap map = bathroom();
        WallTiles.knownForTests(textureAt(map, 3), true, false, reading(true, CREAM));
        map.cell.square(2, 0, 0).setRoomID(2L);
        check(map.build.wallBuilder.tilesOf(textureAt(map, 1), 1, 0, 0, 1f, 0f, true) == null, "not past a square of another room");
        map = bathroom(2);
        WallTiles.knownForTests(textureAt(map, 3), true, false, reading(true, CREAM));
        check(map.build.wallBuilder.tilesOf(textureAt(map, 1), 1, 0, 0, 1f, 0f, true) == null, "nor past a square with no wall of its sheet on that edge");
    }

    private static void aDoorWallPassesAWholeWallThatDisagreesWithIt() {
        TestMap map = bathroom();
        WallTiles.knownForTests(textureAt(map, 1), true, false, reading(false, CREAM));
        WallTiles.knownForTests(textureAt(map, 2), true, false, reading(true, BLUE));
        WallTiles.Wall agrees = reading(true, CREAM);
        WallTiles.knownForTests(textureAt(map, 3), true, false, agrees);
        check(map.build.wallBuilder.tilesOf(textureAt(map, 1), 1, 0, 0, 1f, 0f, true) == agrees, "a whole wall in another colour of the sheet is passed for the next that agrees");
    }

    private static void aWholeWallNotReadYetLeavesTheBuildToBeMadeAgain() {
        TestMap map = bathroom(2);
        Texture unread = new Texture(200, 4, 4);
        unread.paint(0, 0, 4, 4);
        map.cell.square(2, 0, 0).add(map.spriteWith(SHEET + 13, IsoObjectType.wall, unread, "WallN", ""));
        map.build.building.signed = true;
        check(map.build.wallBuilder.tilesOf(textureAt(map, 1), 1, 0, 0, 1f, 0f, true) == null && !map.build.building.signed,
                "a whole wall beside it not read yet leaves the build to be made again once it is");
    }

    private static void aSpriteReadAsAWallIsReadAgainLaidOverOne() {
        // One sprite of a sheet of tiles is a wall on one square and laid over another wall on the next: each is read the way it is drawn.
        float[] white = {0.92f, 0.92f, 0.92f};
        Texture texture = new Texture(300, 4, 4);
        texture.paint(0, 0, 4, 4);
        WallTiles.Wall asWall = read((column, row) -> tiles(white, column, row, 11, 16, 120, 22));
        check(asWall != null, "the sprite reads as a wall of tiles");
        WallTiles.knownForTests(texture, true, false, asWall);
        WallTiles.Wall over = WallTiles.of(texture, new SpriteGeometry.Frame(), true, true);
        check(over != asWall, "laid over a wall it is not taken for the wall it was read as");
        check(over == null && WallTiles.unread(texture, true, true), "and is still to be read that way");
        WallTiles.Wall panel = read((column, row) -> row < 110 ? null : tiles(white, column, row, 11, 16, 120, 22), true);
        WallTiles.knownForTests(texture, true, true, panel);
        check(WallTiles.of(texture, new SpriteGeometry.Frame(), true, false) == asWall && WallTiles.of(texture, new SpriteGeometry.Frame(), true, true) == panel,
                "once read so, each way has its own reading");
        check(!WallTiles.overlayFaces(panel, 0.004f, 0.004f / ViewCamera.LEVEL_HEIGHT).isEmpty(), "and the tiles over a wall are laid as faces");
    }

    /** A sprite the room beyond a wall shows, painted. */
    private static Texture shown(int id) {
        Texture texture = new Texture(id, 4, 4);
        texture.paint(0, 0, 4, 4);
        return texture;
    }

    private static void theFarSideOfAWallTakesTheTilesOfTheRoomBeyondOnEitherEdge() {
        TestMap map = bathroom();
        Texture west = shown(310);
        WallTiles.Wall whole = reading(true, CREAM);
        // The room's sprite is a west wall's: read on the north edge it is mostly bare, an opening, and on its own edge it is tiles.
        WallTiles.knownForTests(west, true, false, reading(false, CREAM));
        WallTiles.knownForTests(west, false, false, whole);
        check(map.build.wallBuilder.farTilesOf(west, true) == whole, "the far side of a north wall takes the tiles of the room beyond, read off its west wall");
        Texture north = shown(311);
        WallTiles.knownForTests(north, true, false, whole);
        check(map.build.wallBuilder.farTilesOf(north, true) == whole, "and of its north wall, read on its own edge");
    }

    private static void theFarSideWaitsForTheRoomsTilesToBeRead() {
        TestMap map = bathroom();
        map.build.building.signed = true;
        check(map.build.wallBuilder.farTilesOf(shown(312), true) == null && !map.build.building.signed,
                "a room's sprite not read yet leaves the far side plain and the build to be made again once it is");
    }

    private static void theFarSideTakesNoTilesFromAWallWithAnOpening() {
        TestMap map = bathroom();
        Texture door = shown(313);
        WallTiles.knownForTests(door, true, false, reading(false, CREAM));
        WallTiles.knownForTests(door, false, false, reading(false, CREAM));
        map.build.building.signed = true;
        check(map.build.wallBuilder.farTilesOf(door, true) == null && map.build.building.signed,
                "a room's sprite read with an opening in it on both edges gives the far side no tiles, and is not waited for");
    }

    /** A sprite laid over the north wall of square 1 of a bathroom, with a picture as big as a wall's. */
    private static Texture over(TestMap map, int id) {
        Texture texture = new Texture(id, 128, 256);
        texture.paint(0, 0, 128, 256);
        TestMap.<java.util.Map<Texture, Boolean>>read(null, Stains.class, "known").put(texture, false);
        WorldScene.frameOf(map.build.frame, map.cell.getGridSquare(1, 0, 0).getObjects().get(0), texture);
        map.build.overLayer = 1;
        return texture;
    }

    /** How many triangles the frame's build has added showing that texture as it is painted, in every room. */
    private static int laidWith(Texture texture) {
        int triangles = 0;
        for (SpriteGeometry.Mesh mesh : TestMap.<java.util.List<SpriteGeometry.Mesh>>read(WorldScene.solid, "all")) {
            triangles += mesh.texture == texture.getID() && mesh.tint == 0 ? mesh.triangles() : 0;
        }
        return triangles;
    }

    /** Runs a test with every row of [Content] on and the pictures made, as in the game. */
    private static void withTextures(Checks.Test test) throws Throwable {
        boolean made = TestMap.read(null, Own.class, "picturesMade");
        Own.picturesMade(true);
        try {
            TestMap.withEverything(test);
        } finally {
            Own.picturesMade(made);
        }
    }

    private static void aPosterOnATiledWallIsThePoster() throws Throwable {
        withTextures(() -> {
            TestMap map = bathroom();
            Texture poster = over(map, 320);
            // Were it read as tiles laid over the wall, it would be built as them and not as itself.
            WallTiles.knownForTests(poster, true, true, read((column, row) -> row < 110 ? null : tiles(WHITE, column, row, 11, 16, 120, 22), true));
            map.build.overSheet = null;
            map.build.wallBuilder.addEdge(WorldScene.solid.get(poster.getID()), poster, false, false, 1, 0, 0, 0f, 1f, 0f);
            check(laidWith(poster) > 0, "a poster hung on a tiled wall is laid as its picture, not read as the wall's tiles");
            Texture panel = over(map, 322);
            WallTiles.knownForTests(panel, true, true, read((column, row) -> row < 110 ? null : tiles(WHITE, column, row, 11, 16, 120, 22), true));
            map.build.overSheet = SHEET;
            map.build.wallBuilder.addEdge(WorldScene.solid.get(panel.getID()), panel, false, false, 1, 0, 0, 0f, 1f, 0f);
            check(laidWith(panel) == 0, "and a panel of tiles hung so, of a sheet of them, is built as its tiles");
        });
    }

    private static void whatIsLaidOverAWallIsNotOnTheRoomBeyond() throws Throwable {
        withTextures(() -> {
            TestMap map = bathroom();
            map.cell.square(1, -1, 0).setRoomID(2L);
            Texture grime = over(map, 321);
            map.build.overSheet = null;
            map.build.wallBuilder.addEdge(WorldScene.solid.get(grime.getID()), grime, true, false, 1, 0, 0, 0f, 1f, 0f);
            int near = 0, far = 0;
            for (SpriteGeometry.Mesh mesh : TestMap.<java.util.List<SpriteGeometry.Mesh>>read(WorldScene.solid, "all")) {
                if (mesh.texture == grime.getID()) {
                    near += mesh.room == 1L ? mesh.triangles() : 0;
                    far += mesh.room == 2L ? mesh.triangles() : 0;
                }
            }
            check(near > 0, "what is laid over a wall is on its face inside the square");
            check(far == 0, "and not on its face in the room beyond");
        });
    }
}

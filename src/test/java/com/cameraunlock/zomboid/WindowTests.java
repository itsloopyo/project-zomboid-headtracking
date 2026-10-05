package com.cameraunlock.zomboid;

import java.util.Arrays;

/** Tests of what is measured off a window's sprite laid flat on its wall. */
final class WindowTests {
    private WindowTests() {}

    static void run() {
        sashWindowIsAFrameARailAndTwoPanes();
        barsOneDotWideMakeALightEach();
        raggedLineBesideABarIsHalfADot();
        openWindowHasNoGlassWhereTheSashWas();
        brokenPaneKeepsTheShareOfGlassItsSpriteShows();
        frameWithNoStileAtOneEndRunsOn();
        archIsMeasuredOverItsSquarePart();
        curtainDrawnAcrossIsOneLengthOfCloth();
        curtainPulledBackIsTwo();
        sheetRolledUpHangsNowhere();
        wallpaperIsItsStripes();
        plainWallWithADoorInItIsOneStripe();
        chequeredFloorIsItsTiles();
        boardsAreNoTiles();
    }

    /** # frame, . glass, a space nothing. Every row as long as the first. */
    private static Windows.Shape measured(String... rows) {
        int across = rows[0].length();
        byte[] dots = new byte[across * rows.length];
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < across; column++) {
                char dot = rows[row].charAt(column);
                dots[row * across + column] = (byte) (dot == '#' ? 2 : dot == '.' ? 1 : 0);
            }
        }
        return Windows.measure(dots, across, rows.length);
    }

    private static void sashWindowIsAFrameARailAndTwoPanes() {
        Windows.Shape shape = measured(
                "            ",
                "  ########  ",
                "  ########  ",
                "  ##....##  ",
                "  ##....##  ",
                "  ##....##  ",
                "  ########  ",
                "  ##....##  ",
                "  ##....##  ",
                "  ########  ",
                "            ",
                "            ");
        check(shape != null, "a frame with a rail across it is a window");
        check(near(shape.start, 2f / 12f) && near(shape.end, 10f / 12f) && near(shape.head, 11f / 12f) && near(shape.foot, 2f / 12f),
                "whose frame is where its outermost dots are: " + shape.start + " to " + shape.end + ", " + shape.foot + " to " + shape.head);
        check(near(shape.startWide, 2f / 12f) && near(shape.endWide, 2f / 12f) && near(shape.headHigh, 2f / 12f) && near(shape.footHigh, 1f / 12f),
                "with sides as thick as they are painted: " + shape.startWide + ", " + shape.endWide + ", " + shape.headHigh + ", " + shape.footHigh);
        check(shape.bars.length == 4 && near(shape.bars[0], 4f / 12f) && near(shape.bars[1], 8f / 12f) && near(shape.bars[2], 5f / 12f) && near(shape.bars[3], 6f / 12f),
                "one rail from stile to stile, a dot high");
        check(shape.panes.length == 10 && near(shape.panes[2], 6f / 12f) && near(shape.panes[3], 9f / 12f) && shape.panes[4] == 1f
                && near(shape.panes[7], 3f / 12f) && near(shape.panes[8], 5f / 12f) && shape.panes[9] == 1f, "and a whole pane above it and one below");
    }

    private static void barsOneDotWideMakeALightEach() {
        Windows.Shape shape = measured(
                "###########",
                "###########",
                "##...#...##",
                "##...#...##",
                "###########",
                "##...#...##",
                "##...#...##",
                "###########",
                "###########");
        check(shape.bars.length == 12, "a rail and an upright in each half: " + shape.bars.length / 4);
        check(shape.panes.length == 20, "four lights: " + shape.panes.length / 5);
        check(near(shape.bars[0], 5f / 11f) && near(shape.bars[1], 6f / 11f) && near(shape.bars[2], 5f / 9f) && near(shape.bars[3], 7f / 9f),
                "the upper upright stands from the rail to the frame's head");
    }

    private static void raggedLineBesideABarIsHalfADot() {
        Windows.Shape shape = measured(
                "# # # # # #",
                "###########",
                "##.......##",
                "##.......##",
                "###########",
                "##.#.#.#.##",
                "##.......##",
                "##.......##",
                "###########",
                "###########");
        check(near(shape.headHigh, 1.5f / 10f), "the frame's ragged top line is half a dot of it: " + shape.headHigh * 10f);
        check(shape.bars.length == 4 && near(shape.bars[2], 1f - 5.5f / 10f) && near(shape.bars[3], 1f - 4f / 10f), "and a rail with a ragged line under it is a dot and a half high");
    }

    private static void openWindowHasNoGlassWhereTheSashWas() {
        Windows.Shape shape = measured(
                "##########",
                "##......##",
                "##......##",
                "##########",
                "##      ##",
                "##      ##",
                "##      ##",
                "##########");
        check(shape.panes.length == 5 && shape.panes[2] > 0.5f, "an open window has glass in its upper half and none below: " + shape.panes.length / 5);
    }

    private static void brokenPaneKeepsTheShareOfGlassItsSpriteShows() {
        Windows.Shape shape = measured(
                "##########",
                "##.. ...##",
                "##.    .##",
                "##      ##",
                "##.    .##",
                "##########");
        check(shape.panes.length == 5 && near(shape.panes[4], 9f / 24f), "a broken pane has as much glass as its sprite: " + shape.panes[4]);
    }

    private static void frameWithNoStileAtOneEndRunsOn() {
        Windows.Shape shape = measured(
                "########",
                "##......",
                "##......",
                "##......",
                "########",
                "########");
        check(shape != null && near(shape.startWide, 2f / 8f) && shape.endWide == 0f, "a frame with no stile at its end is still a window");
        check(shape.panes.length == 5 && near(shape.panes[1], 1f), "whose pane runs to the end of its square");
    }

    private static void archIsMeasuredOverItsSquarePart() {
        Windows.Shape shape = measured(
                "      ####      ",
                "     ##..##     ",
                "    ##....##    ",
                "    #......#    ",
                "####........####",
                "##............##",
                "##............##",
                "##............##",
                "##............##",
                "##............##",
                "##............##",
                "################",
                "################");
        check(shape != null && shape.arch != null && shape.arch.length == 48, "an arched window is measured, a place of its arch for each dot across");
        check(near(shape.head, 9f / 13f) && near(shape.foot, 0f), "its square part ends where the arch springs: " + shape.head);
        check(near(shape.arch[7 * 3], 1f) && near(shape.arch[7 * 3 + 1], 12f / 13f) && shape.arch[7 * 3 + 2] == 1f,
                "at its crown the frame is a dot thick with glass under it: " + shape.arch[21] + ", " + shape.arch[22]);
        check(shape.arch[0] <= shape.head + 1e-4f, "and at its ends the arch has come down to where it springs");
        check(shape.panes.length == 5, "one light below");
    }

    private static Drapes.Shape hung(String... rows) {
        int across = rows[0].length();
        byte[] dots = new byte[across * rows.length];
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < across; column++) {
                dots[row * across + column] = (byte) (rows[row].charAt(column) == '#' ? 2 : 0);
            }
        }
        return Drapes.measure(dots, across, rows.length);
    }

    private static void curtainDrawnAcrossIsOneLengthOfCloth() {
        Drapes.Shape shape = hung(
                "        ",
                " ###### ",
                " ## ### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                " ###### ",
                "        ");
        check(shape.hangs.length == 2 && near(shape.hangs[0], 1f / 8f) && near(shape.hangs[1], 7f / 8f), "a curtain drawn across is one length of cloth");
        check(near(shape.head, 24f / 25f) && near(shape.foot, 1f / 25f), "from its rod to its hem: " + shape.foot + " to " + shape.head);
    }

    private static void curtainPulledBackIsTwo() {
        Drapes.Shape shape = hung(
                "########",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##",
                "##    ##");
        check(shape.hangs.length == 4 && near(shape.hangs[1], 2f / 8f) && near(shape.hangs[2], 6f / 8f), "a curtain pulled back hangs at the two ends of its rod");
    }

    private static void sheetRolledUpHangsNowhere() {
        Drapes.Shape shape = hung(
                " ########## ",
                " ########## ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ",
                "            ");
        check(shape != null && shape.hangs.length == 0, "a sheet rolled up over a window is all roll");
    }

    private static void wallpaperIsItsStripes() {
        // Two colours, eight columns each, with one mixed column where they meet.
        float[] columns = new float[32 * 3];
        boolean[] told = new boolean[32];
        for (int column = 0; column < 32; column++) {
            int within = column % 16;
            float shade = within < 7 ? 0.9f : within == 7 ? 0.8f : within < 15 ? 0.7f : 0.8f;
            columns[column * 3] = shade;
            columns[column * 3 + 1] = shade;
            columns[column * 3 + 2] = 0.6f;
            told[column] = true;
        }
        Papers.Paper paper = Papers.striped(columns, told);
        check(paper.ends.length == 4, "two colours twice over, the mixed column at the end being no stripe: " + paper.ends.length);
        check(near(paper.ends[0], 7.5f / 32f) && near(paper.ends[1], 15.5f / 32f) && near(paper.ends[3], 1f), "a mixed column is shared by the stripes it lies between: " + paper.ends[0]);
        check(near(paper.colours[0], 0.9f) && near(paper.colours[3], 0.7f) && near(paper.colours[2], 0.6f), "each stripe in its own colour");
        check(near(paper.red, 0.8f), "and the whole in the mean of them: " + paper.red);
    }

    private static void plainWallWithADoorInItIsOneStripe() {
        float[] columns = new float[32 * 3];
        boolean[] told = new boolean[32];
        for (int column = 0; column < 32; column++) {
            told[column] = column < 6 || column > 26;
            columns[column * 3] = columns[column * 3 + 1] = columns[column * 3 + 2] = told[column] ? 0.5f : 0f;
        }
        Papers.Paper paper = Papers.striped(columns, told);
        check(paper.ends.length == 1 && near(paper.ends[0], 1f) && near(paper.colours[0], 0.5f), "a wall of one colour is one stripe, across its doorway too");
        Arrays.fill(told, 8, 32, false);
        Arrays.fill(told, 0, 2, false);
        check(Papers.striped(columns, told) == null, "and a sliver of wall says too little");
    }

    /** A square of floor side dots a side: the colour at each dot from its place, x then y, each 0 to 1. */
    private static float[] floor(int side, java.util.function.BiFunction<Float, Float, float[]> colour) {
        float[] dots = new float[side * side * 3];
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                System.arraycopy(colour.apply((i + 0.5f) / side, (j + 0.5f) / side), 0, dots, (i * side + j) * 3, 3);
            }
        }
        return dots;
    }

    private static void chequeredFloorIsItsTiles() {
        // Four tiles a side, black and white turn about, with a grey joint a dot wide round each.
        Tiles.Shape tiles = Tiles.laid(floor(64, (x, y) -> {
            boolean joint = x * 64f % 16f < 1f || y * 64f % 16f < 1f;
            boolean white = ((int) (x * 4f) + (int) (y * 4f)) % 2 == 0;
            return joint ? new float[] {0.5f, 0.5f, 0.5f} : white ? new float[] {0.9f, 0.9f, 0.85f} : new float[] {0.1f, 0.1f, 0.12f};
        }), 64);
        check(tiles != null && tiles.count == 4, "a chequered floor is four tiles a side: " + (tiles == null ? "none" : String.valueOf(tiles.count)));
        check(near(tiles.colours[0], 0.9f) && near(tiles.colours[3], 0.1f) && near(tiles.colours[(1 * 4 + 1) * 3 + 2], 0.85f), "each in its own colour");
        check(near(tiles.red, (0.9f * 112.5f + 0.1f * 112.5f + 0.5f * 31f) / 256f), "and the floor as a whole in the mean of all of it: " + tiles.red);
        Tiles.Shape plain = Tiles.laid(floor(64, (x, y) -> new float[] {0.6f, 0.55f, 0.5f}), 64);
        check(plain != null && plain.count == 1, "a floor of one colour is one tile");
    }

    private static void boardsAreNoTiles() {
        // Boards a third of a tile wide, each a shade of its own, with dark lines along and butt joints across.
        check(Tiles.laid(floor(64, (x, y) -> {
            float board = (float) Math.floor(y * 3.3f);
            float shade = 0.45f + 0.12f * (float) Math.sin(board * 2.4f) + 0.05f * (float) Math.sin(x * 40f + board);
            return y * 3.3f % 1f < 0.08f ? new float[] {0.15f, 0.1f, 0.05f} : new float[] {shade, shade * 0.7f, shade * 0.4f};
        }), 64) == null, "a floor of boards is left to its sprite");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }
}

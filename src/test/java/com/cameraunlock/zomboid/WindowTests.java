package com.cameraunlock.zomboid;

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
        archIsNoWindowInASquareFrame();
        curtainDrawnAcrossIsOneLengthOfCloth();
        curtainPulledBackIsTwo();
        sheetRolledUpHangsNowhere();
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

    private static void archIsNoWindowInASquareFrame() {
        check(measured(
                "    ####    ",
                "  ###..###  ",
                " ##......## ",
                "##........##",
                "##........##",
                "##........##",
                "############",
                "############") == null, "an arch is left to its sprite");
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

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }
}

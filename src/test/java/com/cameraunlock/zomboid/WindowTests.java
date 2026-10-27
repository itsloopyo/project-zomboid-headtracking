package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.function.BiFunction;

/** Tests of what is measured off a window's sprite laid flat on its wall. */
final class WindowTests {
    private WindowTests() {}

    static void run() {
        Checks.run("sashWindowIsAFrameARailAndTwoPanes", WindowTests::sashWindowIsAFrameARailAndTwoPanes);
        Checks.run("barsOneDotWideMakeALightEach", WindowTests::barsOneDotWideMakeALightEach);
        Checks.run("raggedLineBesideABarIsHalfADot", WindowTests::raggedLineBesideABarIsHalfADot);
        Checks.run("openWindowHasNoGlassWhereTheSashWas", WindowTests::openWindowHasNoGlassWhereTheSashWas);
        Checks.run("brokenPaneKeepsTheShareOfGlassItsSpriteShows", WindowTests::brokenPaneKeepsTheShareOfGlassItsSpriteShows);
        Checks.run("frameWithNoStileAtOneEndRunsOn", WindowTests::frameWithNoStileAtOneEndRunsOn);
        Checks.run("frostedGlassLowDownIsNoFoot", WindowTests::frostedGlassLowDownIsNoFoot);
        Checks.run("windowPaintedOffItsSquareIsMovedBackOntoIt", WindowTests::windowPaintedOffItsSquareIsMovedBackOntoIt);
        Checks.run("halfAWindowThatRunsOnReachesItsSquaresEnd", WindowTests::halfAWindowThatRunsOnReachesItsSquaresEnd);
        Checks.run("halfAWindowBegunInTheSquareBeforeIsCutAtItsStart", WindowTests::halfAWindowBegunInTheSquareBeforeIsCutAtItsStart);
        Checks.run("windowWithinItsSquareStaysWhereItIs", WindowTests::windowWithinItsSquareStaysWhereItIs);
        Checks.run("windowThatRunsOnIsCarriedToTheEndOfItsWallsOpening", WindowTests::windowThatRunsOnIsCarriedToTheEndOfItsWallsOpening);
        Checks.run("stileIsWidenedToTheEndOfItsWallsOpening", WindowTests::stileIsWidenedToTheEndOfItsWallsOpening);
        Checks.run("windowFarShortOfItsOpeningOrWithNoneIsLeft", WindowTests::windowFarShortOfItsOpeningOrWithNoneIsLeft);
        Checks.run("archIsMeasuredOverItsSquarePart", WindowTests::archIsMeasuredOverItsSquarePart);
        Checks.run("curtainDrawnAcrossIsOneLengthOfCloth", WindowTests::curtainDrawnAcrossIsOneLengthOfCloth);
        Checks.run("curtainPulledBackIsTwo", WindowTests::curtainPulledBackIsTwo);
        Checks.run("sheetRolledUpHangsNowhere", WindowTests::sheetRolledUpHangsNowhere);
        Checks.run("wallpaperIsItsStripes", WindowTests::wallpaperIsItsStripes);
        Checks.run("plainWallWithADoorInItIsOneStripe", WindowTests::plainWallWithADoorInItIsOneStripe);
        Checks.run("paperIsReadWhereTheWallRunsAllAlong", WindowTests::paperIsReadWhereTheWallRunsAllAlong);
        Checks.run("chequeredFloorIsItsTiles", WindowTests::chequeredFloorIsItsTiles);
        Checks.run("bricksInBasketweaveArePavers", WindowTests::bricksInBasketweaveArePavers);
        Checks.run("honeycombIsHexagonalPavers", WindowTests::honeycombIsHexagonalPavers);
        Checks.run("boardsAreNoTiles", WindowTests::boardsAreNoTiles);
        Checks.run("chipsInAGroundAreSpeckled", WindowTests::chipsInAGroundAreSpeckled);
        Checks.run("diagonalFloorIsItsDiamonds", WindowTests::diagonalFloorIsItsDiamonds);
        Checks.run("parquetIsItsBlocksOfSlats", WindowTests::parquetIsItsBlocksOfSlats);
        Checks.run("shadedTilesAreAGrid", WindowTests::shadedTilesAreAGrid);
        Checks.run("diamondsCutToTheSquareCoverIt", WindowTests::diamondsCutToTheSquareCoverIt);
        Checks.run("diagonalJointsAreTheSpritesOwn", WindowTests::diagonalJointsAreTheSpritesOwn);
        Checks.run("floorLaidFlatMeetsItselfAtTheSquaresEdge", WindowTests::floorLaidFlatMeetsItselfAtTheSquaresEdge);
        Checks.run("halfASquareIsNoFlatFloor", WindowTests::halfASquareIsNoFlatFloor);
        Checks.run("wallStoodSquareRunsOnIntoTheNextLength", WindowTests::wallStoodSquareRunsOnIntoTheNextLength);
        Checks.run("wallStoodSquareLosesTheDarkLineAlongItsTop", WindowTests::wallStoodSquareLosesTheDarkLineAlongItsTop);
        Checks.run("doorwayToTheEndOfItsSquareHasNoWallInItsRim", WindowTests::doorwayToTheEndOfItsSquareHasNoWallInItsRim);
        Checks.run("cornerPostPictureCoversItsFacesSlack", WindowTests::cornerPostPictureCoversItsFacesSlack);
        Checks.run("doorWithOneSmallLightIsALeafWithItsLight", WindowTests::doorWithOneSmallLightIsALeafWithItsLight);
        Checks.run("closerOnADoorsHeadIsNoPartOfItsLeaf", WindowTests::closerOnADoorsHeadIsNoPartOfItsLeaf);
        Checks.run("narrowArchKeepsItsCrown", WindowTests::narrowArchKeepsItsCrown);
    }

    /** A flush door with a small light high in it, as the commercial doors are: a leaf with stiles as wide as the light's sides, and the light glass. */
    private static void doorWithOneSmallLightIsALeafWithItsLight() {
        String[] rows = new String[40];
        for (int row = 0; row < rows.length; row++) {
            rows[row] = row == 0 || row == 39 ? "              " : row >= 4 && row < 11 ? "  ###....###  " : "  ##########  ";
        }
        int across = rows[0].length();
        Windows.Shape leaf = measuredLeaf(rows);
        check(leaf != null, "a door with a light a sixth of its height is a leaf");
        if (leaf == null) {
            return;
        }
        check(leaf.panes.length == 5, "it has its one light");
        check(Math.abs(leaf.startWide - 3f / across) < 1e-4f && Math.abs(leaf.endWide - 3f / across) < 1e-4f,
                "its stiles are as wide as the light's sides: " + leaf.startWide * across + " and " + leaf.endWide * across);
    }

    /**
     * A glass door with a closer standing on its head at one end, as fixtures_doors_02_41 paints
     * it: the leaf is measured under the closer, with its two lights, and not refused as no
     * leaf with four straight sides.
     */
    private static void closerOnADoorsHeadIsNoPartOfItsLeaf() {
        String[] rows = new String[44];
        for (int row = 0; row < rows.length; row++) {
            rows[row] = row == 0 ? "                              "
                    : row == 1 ? "                        #.    "
                    : row < 4 ? "                       ###    "
                    : row == 4 ? "   # # # # # # # # # # ####.  "
                    : row < 9 || row >= 21 && row < 25 || row >= 39 && row < 43 ? "  #########################   "
                    : row < 43 ? "  #####...............#####   "
                    : "                              ";
        }
        Windows.Shape leaf = measuredLeaf(rows);
        check(leaf != null, "a door with a closer on its head is a leaf");
        if (leaf == null) {
            return;
        }
        check(leaf.panes.length == 10, "with its two lights: " + leaf.panes.length / 5);
        check(Math.abs(leaf.head - (1f - 4f / rows.length)) < 1e-4f, "its head is the frame's, under the closer: " + leaf.head * rows.length);
    }

    /**
     * The narrowest arch measure takes keeps its crown. An arch is told by its head's corners,
     * bare for CORNER dots, so its crown stands CORNER dots or more from either end and is never
     * the lump at one end of a head that a door's closer is.
     */
    private static void narrowArchKeepsItsCrown() {
        Windows.Shape shape = measured(
                "    ###    ",
                "    #.#    ",
                "    #.#    ",
                "    #.#    ",
                "##.......##",
                "##.......##",
                "##.......##",
                "##.......##",
                "##.......##",
                "##.......##",
                "###########",
                "###########");
        check(shape != null && shape.arch != null, "an arched window eleven dots across is measured with its arch");
        check(shape != null && shape.arch != null && near(shape.arch[5 * 3], 1f), "up to its crown: " + (shape == null || shape.arch == null ? "none" : shape.arch[15]));
    }

    /** As measured, with a frame that is frame all over a shape too: see Windows.measure. */
    private static Windows.Shape measuredLeaf(String... rows) {
        int across = rows[0].length();
        byte[] dots = new byte[across * rows.length];
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < across; column++) {
                char dot = rows[row].charAt(column);
                dots[row * across + column] = dot == '#' ? Windows.FRAME : dot == '.' ? Windows.GLASS : Windows.EMPTY;
            }
        }
        return Windows.measure(dots, across, rows.length, true);
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

    /** A bus shelter's side: a rail along the top and frosted glass to the ground, painted past FRAME_COVER in its lowest third. */
    private static void frostedGlassLowDownIsNoFoot() {
        Windows.Shape shape = measured(
                "##########",
                "..........",
                "..........",
                "..........",
                "..........",
                "..........",
                "..........",
                "##########",
                "##########",
                "##########");
        check(shape != null && near(shape.footHigh, 3f / 10f) && shape.panes.length == 5 && near(shape.panes[2], 3f / 10f), "read as a frame, the frosting is a foot");
        Windows.frostedFoot(shape);
        check(shape.footHigh == 0f && near(shape.panes[2], 0f) && near(shape.panes[3], 9f / 10f), "and taken back, the pane runs from the rail to the ground");
        check(near(shape.frostedTo, 3f / 10f), "frosted as high as the foot was");
        Windows.Shape cut = OpeningBuilder.withinCut(shape, 0.1f, 0.9f);
        check(cut != shape && cut.frostedTo == shape.frostedTo, "and cut back to its opening, as frosted");
        Windows.Shape sill = measured(
                "##########",
                "##......##",
                "##......##",
                "##......##",
                "##......##",
                "##......##",
                "##......##",
                "##......##",
                "##......##",
                "##########");
        Windows.frostedFoot(sill);
        check(near(sill.footHigh, 1f / 10f) && near(sill.panes[2], 1f / 10f) && sill.frostedTo == 0f, "a window's foot of a dot or two stays its foot, and its glass clear");
        Windows.Shape tower = measured(
                "##########",
                "##......##",
                "##......##",
                "##......##",
                "##......##",
                "##......##",
                "##########",
                "##########",
                "##########",
                "##########");
        Windows.frostedFoot(tower);
        check(near(tower.footHigh, 4f / 10f) && near(tower.panes[2], 4f / 10f), "and frosting in a frame with a stile at each end stays its foot");
    }

    /** Rows read two dots past each end of a square sixteen dots long, as Windows.read reads them, then put on the square. */
    private static Windows.Shape onSquare(String... rows) {
        check(rows[0].length() == 20, "a row is the square's sixteen dots and two past each end");
        return Windows.onSquare(measured(rows), 16, 2);
    }

    /** The game paints this window two dots before its square and a dot short of its end, where the wall's opening runs on: moved back, it closes the opening. */
    private static void windowPaintedOffItsSquareIsMovedBackOntoIt() {
        Windows.Shape shape = onSquare(
                "#################   ",
                "##.............##   ",
                "##.............##   ",
                "##.............##   ",
                "##.............##   ",
                "#################   ",
                "#################   ");
        check(near(shape.start, 0f) && near(shape.end, 1f), "the frame runs from end to end of its square: " + shape.start + " to " + shape.end);
        check(near(shape.startWide, 1f / 16f) && near(shape.endWide, 2f / 16f), "its first stile cut where the square begins, its last whole: " + shape.startWide + ", " + shape.endWide);
        check(shape.panes.length == 5 && near(shape.panes[0], 1f / 16f) && near(shape.panes[1], 14f / 16f), "and its pane moved with it, between the stiles: " + shape.panes[0] + " to " + shape.panes[1]);
    }

    /** The first half of a window two squares long stops a dot short of its square's end, which the second half's sprite paints over in the game. */
    private static void halfAWindowThatRunsOnReachesItsSquaresEnd() {
        Windows.Shape shape = onSquare(
                "    #############   ",
                "    ##...........   ",
                "    ##...........   ",
                "    ##...........   ",
                "    ##...........   ",
                "    #############   ",
                "    #############   ");
        check(near(shape.start, 2f / 16f) && near(shape.startWide, 2f / 16f), "its stile stays where it is painted: " + shape.start + ", " + shape.startWide);
        check(near(shape.end, 1f) && shape.endWide == 0f, "and the end with no stile is the square's end: " + shape.end);
        check(shape.panes.length == 5 && near(shape.panes[1], 1f), "with its pane running on to it: " + shape.panes[1]);
    }

    /** The second half is painted from a dot before its square: the part over the first half's square is the first half's. */
    private static void halfAWindowBegunInTheSquareBeforeIsCutAtItsStart() {
        Windows.Shape shape = onSquare(
                " #############      ",
                " ...........##      ",
                " ...........##      ",
                " ...........##      ",
                " ...........##      ",
                " #############      ",
                " #############      ");
        check(near(shape.start, 0f) && shape.startWide == 0f, "it starts where its square does: " + shape.start + ", " + shape.startWide);
        check(shape.panes.length == 5 && near(shape.panes[0], 0f) && near(shape.panes[1], 10f / 16f), "and so does its pane: " + shape.panes[0] + " to " + shape.panes[1]);
        check(shape.bars.length == 0, "nothing is left past the square's start");
    }

    private static void windowWithinItsSquareStaysWhereItIs() {
        Windows.Shape shape = onSquare(
                "     ##########     ",
                "     ##......##     ",
                "     ##......##     ",
                "     ##......##     ",
                "     ##########     ",
                "     ##########     ");
        check(near(shape.start, 3f / 16f) && near(shape.end, 13f / 16f) && near(shape.startWide, 2f / 16f) && near(shape.endWide, 2f / 16f),
                "a window with both stiles in its square is where it is painted: " + shape.start + " to " + shape.end);
        check(near(shape.panes[0], 5f / 16f) && near(shape.panes[1], 11f / 16f), "its pane too");
    }

    /** A window from start to end with a stile at each end as wide as given (0 for none), one rail across and a pane over and under it. */
    private static Windows.Shape window(float start, float end, float startWide, float endWide) {
        Windows.Shape shape = new Windows.Shape();
        shape.start = start;
        shape.end = end;
        shape.startWide = startWide;
        shape.endWide = endWide;
        shape.foot = 0.3f;
        shape.head = 0.75f;
        shape.footHigh = 0.03f;
        shape.headHigh = 0.02f;
        float in = start + startWide, out = end - endWide;
        shape.bars = new float[] {in, out, 0.5f, 0.53f};
        shape.panes = new float[] {in, out, 0.53f, 0.73f, 1f, in, out, 0.33f, 0.5f, 1f};
        return shape;
    }

    /** The first half of the motel's long window stops 0.0625 short of its square's end, where its wall's opening runs on to the next square. */
    private static void windowThatRunsOnIsCarriedToTheEndOfItsWallsOpening() {
        Windows.Shape half = window(0.15625f, 0.9375f, 0.09375f, 0f);
        Windows.Shape fit = OpeningBuilder.fitted(half, 0.1875f, 1f);
        check(near(fit.end, 1f) && fit.endWide == 0f, "the end with no stile reaches the opening's end: " + fit.end);
        check(near(fit.start, 0.15625f) && near(fit.startWide, 0.09375f), "the stile that laps over the wall at the other end stays: " + fit.start + ", " + fit.startWide);
        check(near(fit.bars[1], 1f) && near(fit.panes[1], 1f) && near(fit.panes[6], 1f), "the rail and both panes run on to it");
        check(near(fit.bars[0], 0.25f) && near(fit.panes[0], 0.25f), "and still begin at the stile");
        check(near(half.end, 0.9375f) && near(half.panes[1], 0.9375f), "the measured shape, every square's that shows the sprite, is left as it was");
    }

    /** A window of one square whose sprite stops 0.078 short of the end of an opening that runs to the square's end: the stile closes it. */
    private static void stileIsWidenedToTheEndOfItsWallsOpening() {
        Windows.Shape shape = window(0.140625f, 0.921875f, 0.09375f, 0.0625f);
        Windows.Shape fit = OpeningBuilder.fitted(shape, 0.203125f, 1f);
        check(near(fit.end, 1f) && near(fit.endWide, 0.0625f + 0.078125f), "the stile reaches the opening's end, its inside where it was: " + fit.end + ", " + fit.endWide);
        check(near(fit.panes[1], 0.859375f) && near(fit.bars[1], 0.859375f), "the panes and the rail stop at the stile as before");
    }

    private static void windowFarShortOfItsOpeningOrWithNoneIsLeft() {
        Windows.Shape shape = window(0.2f, 0.7f, 0.05f, 0.05f);
        check(OpeningBuilder.fitted(shape, 0f, 1f) == shape, "a window further than FIT_MOST from its opening's ends is not stretched to them");
        check(OpeningBuilder.fitted(shape, -1f, 0f) == shape, "nor one whose wall had no opening measured");
        check(OpeningBuilder.fitted(shape, 0.25f, 0.65f) == shape, "nor one that already covers its opening");
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

    private static void paperIsReadWhereTheWallRunsAllAlong() {
        // A doorway from column 3 to 28 below row 30, its reveal painted down columns 1 and 2, the sprite's last column empty.
        int across = 32, high = 96;
        byte[] dots = new byte[across * high];
        for (int row = 0; row < high; row++) {
            for (int column = 0; column < across - 1; column++) {
                dots[row * across + column] = row >= 30 && column >= 3 && column <= 28 ? Windows.EMPTY : Windows.FRAME;
            }
        }
        boolean[] rows = Papers.wallRows(dots, across, high, 6);
        check(!rows[5] && rows[6] && rows[29], "the rows above the doorway's head are read");
        check(!rows[30] && !rows[95], "and none beside the doorway, down which the reveal is painted");
        Arrays.fill(dots, 0, 26 * across, Windows.EMPTY);
        rows = Papers.wallRows(dots, across, high, 6);
        check(rows[6] && rows[30] && rows[95], "a sprite with too little wall all along is read from every row");
    }

    /** A square of floor side dots a side: the colour at each dot from its place, x then y, each 0 to 1. */
    private static float[] floor(int side, BiFunction<Float, Float, float[]> colour) {
        float[] dots = new float[side * side * 3];
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                System.arraycopy(colour.apply((i + 0.5f) / side, (j + 0.5f) / side), 0, dots, (i * side + j) * 3, 3);
            }
        }
        return dots;
    }

    private static void bricksInBasketweaveArePavers() {
        // Four cells a side, sixteen dots each, the first joint a dark dot five dots in. Each two
        // by two of cells is a pair of bricks, laid along x and along y turn about, every brick
        // its own shade of red, speckled a little, with a lighter dot beside each joint.
        Tiles.Shape tiles = Tiles.laid(floor(64, (x, y) -> {
            int i = (int) (x * 64f), j = (int) (y * 64f);
            if (basketJoint(i, j)) {
                return new float[] {0.2f, 0.15f, 0.12f};
            }
            int ci = Math.floorMod(i - 5, 64) / 16, cj = Math.floorMod(j - 5, 64) / 16;
            int brick = (ci / 2 + cj / 2) % 2 == 0 ? (ci / 2) * 8 + cj : ci * 8 + cj / 2 + 4;
            boolean lit = basketJoint(i - 1, j) || basketJoint(i, j - 1);
            float speckle = ((i * 7 + j * 13) % 5 - 2) * 0.004f, shade = 0.45f + (brick % 5) * 0.03f + speckle + (lit ? 0.06f : 0f);
            return new float[] {shade + 0.1f, shade * 0.6f, shade * 0.5f};
        }), 64);
        check(tiles != null && tiles.pavers != null && tiles.cellsA == 4 && tiles.cellsB == 4, "bricks in basketweave are pavers on four cells a side: "
                + (tiles == null ? "none" : tiles.pavers == null ? "read as count " + tiles.count : tiles.cellsA + " by " + tiles.cellsB));
        check(Math.abs(tiles.firstA * 64f - 5.5f) <= 1f && Math.abs(tiles.firstB * 64f - 5.5f) <= 1f, "the first joints within a dot of where they are laid: " + tiles.firstA * 64f + ", " + tiles.firstB * 64f);
        int bricks = tiles.pavers.length / 7;
        boolean allBricks = bricks == 8;
        for (int at = 0; at < tiles.pavers.length; at += 7) {
            allBricks &= tiles.pavers[at + 2] * tiles.pavers[at + 3] == 2f;
        }
        check(allBricks, "eight bricks, each two cells: " + bricks);
        check(tiles.jointRed < 0.3f && tiles.pavers[4] > 0.5f, "the joints dark and the bricks red: " + tiles.jointRed + ", " + tiles.pavers[4]);
        // The same speckle with no joints, and a lawn's blotches, are no pavers.
        check(Tiles.pavers(floor(64, (x, y) -> {
            int i = (int) (x * 64f), j = (int) (y * 64f);
            float shade = 0.4f + ((i * 7 + j * 13) % 5 - 2) * 0.004f;
            return new float[] {shade, shade, shade};
        }), 64) == null, "a plain speckled floor is no pavers");
        check(Tiles.pavers(floor(64, (x, y) -> {
            int i = (int) (x * 64f), j = (int) (y * 64f);
            float shade = 0.35f + ((i * 31 + j * 17) % 11) * 0.02f;
            return new float[] {shade * 0.6f, shade, shade * 0.4f};
        }), 64) == null, "and nor is a blotched lawn");
    }

    /** Whether dot i, j of the basketweave is a joint: a joint is a dot wide, every sixteenth dot from the fifth, where it is the side of a brick. */
    private static boolean basketJoint(int i, int j) {
        int ci = Math.floorMod(i - 5, 64) / 16, cj = Math.floorMod(j - 5, 64) / 16;
        boolean alongX = (ci / 2 + cj / 2) % 2 == 0;
        return Math.floorMod(i - 5, 16) == 0 && (!alongX || ci % 2 == 0) || Math.floorMod(j - 5, 16) == 0 && (alongX || cj % 2 == 0);
    }

    private static void chipsInAGroundAreSpeckled() {
        // Chips two to four dots across in pink, brown and white, scattered over a beige ground, cover about a third of it.
        float[][] chips = {{0.8f, 0.62f, 0.6f}, {0.5f, 0.38f, 0.3f}, {0.9f, 0.88f, 0.84f}};
        float[] terrazzo = floor(64, (x, y) -> new float[] {0.7f, 0.66f, 0.58f});
        Random random = new Random(80);
        for (int chip = 0; chip < 160; chip++) {
            int i0 = random.nextInt(64), j0 = random.nextInt(64), across = 2 + random.nextInt(3);
            float[] colour = chips[random.nextInt(chips.length)];
            for (int i = i0; i < i0 + across; i++) {
                for (int j = j0; j < j0 + across; j++) {
                    System.arraycopy(colour, 0, terrazzo, ((i % 64) * 64 + j % 64) * 3, 3);
                }
            }
        }
        check(Tiles.laid(terrazzo, 64) == null && Tiles.speckled(terrazzo, 64), "chips in a ground are no tiles, and speckled");
        // Boards eight dots wide along y, each its own shade, with a dark joint between, and the joints across them out of line from board to board.
        float[] boards = floor(64, (x, y) -> {
            int i = (int) (x * 64f), j = (int) (y * 64f), board = i / 8;
            float shade = i % 8 == 0 || (j + board * 23) % 40 == 0 ? 0.2f : 0.45f + (board * 37 % 7) * 0.03f + ((i * 7 + j * 13) % 5 - 2) * 0.01f;
            return new float[] {shade, shade * 0.8f, shade * 0.6f};
        });
        check(!Tiles.speckled(boards, 64), "boards are not speckled");
        // Stripes four dots wide running on the diagonal, as bands of a herringbone do, light and dark turn about.
        check(!Tiles.speckled(floor(64, (x, y) -> {
            float shade = ((int) (x * 64f) + (int) (y * 64f)) / 4 % 2 == 0 ? 0.4f : 0.6f;
            return new float[] {shade, shade, shade};
        }), 64), "nor are stripes on the diagonal");
        check(!Tiles.speckled(floor(64, (x, y) -> new float[] {0.5f, 0.5f, 0.5f}), 64), "nor is a floor of one colour");
    }

    private static void honeycombIsHexagonalPavers() {
        // Six rows of six hexagons to the square, their middles a lattice from five dots in each
        // way, steps of 10 2/3 dots along y and of 10 2/3 along x and 5 1/3 back along y: each
        // paver its own shade of red, speckled a little, with a dark joint a dot wide round it.
        Tiles.Shape tiles = Tiles.laid(floor(64, (x, y) -> {
            float a = x * 64f, b = y * 64f;
            float[] hex = honeycomb(a, b);
            if (hex[0] < 0.5f) {
                return new float[] {0.25f, 0.18f, 0.15f};
            }
            float speckle = (((int) a * 7 + (int) b * 13) % 5 - 2) * 0.004f, shade = 0.42f + Math.floorMod(Math.round(hex[1]) * 3 + Math.round(hex[2]), 5) * 0.03f + speckle;
            return new float[] {shade + 0.1f, shade * 0.7f, shade * 0.6f};
        }), 64);
        check(tiles != null && tiles.hexes != null && tiles.hexes.length == 36 * 5, "a honeycomb is thirty-six hexagonal pavers: "
                + (tiles == null ? "none" : tiles.hexes == null ? "read as count " + tiles.count + ", pavers " + (tiles.pavers != null) : tiles.hexes.length / 5 + ""));
        float closest = Float.MAX_VALUE;
        for (int at = 0; at < tiles.hexes.length; at += 5) {
            float offA = Math.abs(tiles.hexes[at] * 64f - 5f), offB = Math.abs(tiles.hexes[at + 1] * 64f - 5f);
            closest = Math.min(closest, (float) Math.hypot(Math.min(offA, 64f - offA), Math.min(offB, 64f - offB)));
        }
        check(closest <= 1f, "a middle within a dot of where it is laid: " + closest);
        // Every middle a whole number of steps from the first, or neighbours' edges part in a crack.
        float offLattice = 0f;
        for (int at = 5; at < tiles.hexes.length; at += 5) {
            float da = (tiles.hexes[at] - tiles.hexes[0]) * 64f, db = (tiles.hexes[at + 1] - tiles.hexes[1]) * 64f;
            float n1 = da * 3f / 32f, n2 = (db + n1 * 16f / 3f) * 3f / 32f;
            offLattice = Math.max(offLattice, Math.max(Math.abs(n1 - Math.round(n1)), Math.abs(n2 - Math.round(n2))));
        }
        check(offLattice < 1e-3f, "every middle on one lattice: " + offLattice + " of a step off");
        float widest = 0f;
        for (int k = 0; k < 12; k += 2) {
            widest = Math.max(widest, (float) Math.hypot(tiles.hexCorners[k], tiles.hexCorners[k + 1]) * 64f);
        }
        check(widest > 6f && widest < 8f, "corners as far from the middle as the lattice puts them: " + widest);
        check(tiles.jointRed < 0.35f && tiles.hexes[2] > 0.5f, "the joints dark and the pavers red: " + tiles.jointRed + ", " + tiles.hexes[2]);
        // Bricks in basketweave, the same speckle with no joints, and a lawn's blotches are no hexagons.
        check(Tiles.hexes(floor(64, (x, y) -> {
            int i = (int) (x * 64f), j = (int) (y * 64f);
            float shade = basketJoint(i, j) ? 0.2f : 0.5f + ((i * 7 + j * 13) % 5 - 2) * 0.004f;
            return new float[] {shade, shade * 0.6f, shade * 0.5f};
        }), 64) == null, "bricks in basketweave are no hexagons");
        check(Tiles.hexes(floor(64, (x, y) -> {
            int i = (int) (x * 64f), j = (int) (y * 64f);
            float shade = 0.4f + ((i * 7 + j * 13) % 5 - 2) * 0.004f;
            return new float[] {shade, shade, shade};
        }), 64) == null, "a plain speckled floor is no hexagons");
        check(Tiles.hexes(floor(64, (x, y) -> {
            int i = (int) (x * 64f), j = (int) (y * 64f);
            float shade = 0.35f + ((i * 31 + j * 17) % 11) * 0.02f;
            return new float[] {shade * 0.6f, shade, shade * 0.4f};
        }), 64) == null, "and nor is a blotched lawn");
    }

    /**
     * Of the dot at (a, b) of the honeycomb: how far it is from the edge of its hexagon, in dots,
     * and where its hexagon's middle lies on the square.
     */
    private static float[] honeycomb(float a, float b) {
        float first = Float.MAX_VALUE, second = Float.MAX_VALUE, middleA = 0f, middleB = 0f;
        for (int n1 = -2; n1 <= 8; n1++) {
            for (int n2 = -2; n2 <= 10; n2++) {
                float midA = 5f + n1 * 32f / 3f, midB = 5f - n1 * 16f / 3f + n2 * 32f / 3f;
                for (float wrapA = -64f; wrapA <= 64f; wrapA += 64f) {
                    for (float wrapB = -64f; wrapB <= 64f; wrapB += 64f) {
                        float far = (float) Math.hypot(a - midA - wrapA, b - midB - wrapB);
                        if (far < first - 1e-3f) {
                            second = first;
                            first = far;
                            middleA = Math.floorMod(Math.round(midA + wrapA), 64);
                            middleB = Math.floorMod(Math.round(midB + wrapB), 64);
                        } else if (far < second && far > first + 1e-3f) {
                            second = far;
                        }
                    }
                }
            }
        }
        return new float[] {(second * second - first * first) / (2f * 32f / 3f), middleA, middleB};
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

    /** A floor of tiles on the diagonal, count joints a tile each way from phase, a grey joint a dot and a half wide, the diamonds in one colour or turn about in two. */
    private static float[] diagonalFloor(int count, float phase, float[] even, float[] odd) {
        return floor(64, (a, b) -> {
            float s = count * (a + b) - phase, t = count * (a - b) - phase;
            float toJoint = Math.min(Math.min(s - (float) Math.floor(s), (float) Math.ceil(s) - s), Math.min(t - (float) Math.floor(t), (float) Math.ceil(t) - t));
            if (toJoint * 64f / count / 1.4142f < 0.75f) {
                return new float[] {0.45f, 0.45f, 0.45f};
            }
            return Math.floorMod((int) Math.floor(s) + (int) Math.floor(t), 2) == 0 ? even : odd;
        });
    }

    private static void diagonalFloorIsItsDiamonds() {
        float[] green = {0.29f, 0.34f, 0.26f}, white = {0.9f, 0.9f, 0.85f}, grey = {0.44f, 0.43f, 0.48f};
        Tiles.Shape plain = Tiles.laid(diagonalFloor(2, 0f, green, green), 64);
        check(plain != null && plain.diagonal == 2 && plain.count == 0 && plain.blocks == 0, "green tiles on the diagonal, two joints a tile, are read so: "
                + (plain == null ? "none" : plain.diagonal + " joints, " + plain.count + " tiles"));
        check(Math.abs(plain.phase) < 0.07f || Math.abs(plain.phase - 1f) < 0.07f, "with the joints through the corners of the square: " + plain.phase);
        check(!plain.checkered && Math.abs(plain.colours[0] - 0.29f) < 0.01f && Math.abs(plain.colours[1] - 0.34f) < 0.01f, "all in the one green");
        check(Math.abs(plain.jointRed - 0.45f) < 0.03f, "and the joints in their own grey: " + plain.jointRed);
        check(plain.uneven > 0.0006f, "a carpet goes by how uneven the square is taken whole, joints and all: " + plain.uneven);
        Tiles.Shape chequer = Tiles.laid(diagonalFloor(2, 0.5f, grey, white), 64);
        check(chequer != null && chequer.diagonal == 2 && chequer.checkered, "grey and white turn about on the diagonal is a chequer");
        check(Math.abs(chequer.phase - 0.5f) < 0.07f, "its joints half a tile along from the corners: " + chequer.phase);
        check(Math.abs(chequer.colours[0] - 0.44f) < 0.01f && Math.abs(chequer.colours[3] - 0.9f) < 0.01f && Math.abs(chequer.colours[5] - 0.85f) < 0.01f,
                "the even diamonds grey and the odd white");
        float[] area = new float[2];
        for (int odd = 0; odd < 2; odd++) {
            float[] cut = odd == 0 ? chequer.evenTriangles : chequer.oddTriangles;
            for (int at = 0; at < cut.length; at += 6) {
                area[odd] += Math.abs((cut[at + 2] - cut[at]) * (cut[at + 5] - cut[at + 1]) - (cut[at + 4] - cut[at]) * (cut[at + 3] - cut[at + 1])) / 2f;
            }
        }
        check(Math.abs(area[0] - 0.5f) < 1e-4f && Math.abs(area[1] - 0.5f) < 1e-4f, "and each colour is laid over half the square: " + Arrays.toString(area));
        Tiles.Shape fine = Tiles.laid(diagonalFloor(4, 0f, white, white), 64);
        check(fine != null && fine.diagonal == 4, "four joints a tile are four: " + (fine == null ? "none" : String.valueOf(fine.diagonal)));
    }

    /**
     * A floor of tiles on the diagonal at side dots a side, two joints a tile, the joints along
     * a + b from phase and along a - b from phaseAcross, grout wide dots across in its colour
     * between them, and the diamonds in one colour or turn about in two. Grout of 0 wide is two
     * colours that simply meet, each a shade lighter along the one edge and darker along the
     * other, as a bevel is painted.
     */
    private static float[] diagonalFloor(int side, float phase, float phaseAcross, float wide, float[] grout, float[] even, float[] odd) {
        return floor(side, (a, b) -> {
            float s = 2f * (a + b) - phase, t = 2f * (a - b) - phaseAcross;
            float inS = s - (float) Math.floor(s), inT = t - (float) Math.floor(t);
            // A joint to the next is a quarter of the square's diagonal: side / 2 / 1.4142 dots.
            float dots = side / 2f / 1.4142f;
            float toJoint = Math.min(Math.min(inS, 1f - inS), Math.min(inT, 1f - inT)) * dots;
            if (toJoint < wide / 2f) {
                return grout.clone();
            }
            float[] colour = (Math.floorMod((int) Math.floor(s) + (int) Math.floor(t), 2) == 0 ? even : odd).clone();
            if (wide == 0f && toJoint < 1f) {
                float shade = Math.min(inS, inT) * dots < 1f ? 1.06f : 0.94f;
                for (int part = 0; part < 3; part++) {
                    colour[part] *= shade;
                }
            }
            return colour;
        });
    }

    /**
     * Tiles on the diagonal as the game paints them: the joints of sprite 39 lighter than its
     * dark tiles, those of 36 lighter than its brown ones, sprite 9's joints at a place of their
     * own each way, and the chequers 0 and 1 two colours that meet with no grout between. What
     * the picture is handed is the sprite's own joint, at both sizes the game reads sprites at.
     */
    private static void diagonalJointsAreTheSpritesOwn() {
        float[] dark = {0.17f, 0.19f, 0.2f}, lightGrout = {0.35f, 0.36f, 0.37f}, brown = {0.63f, 0.42f, 0.31f}, paleGrout = {0.64f, 0.53f, 0.48f};
        float[] black = {0.15f, 0.14f, 0.21f}, white = {0.86f, 0.86f, 0.78f}, mustard = {0.8f, 0.67f, 0.3f}, darker = {0.6f, 0.5f, 0.22f};
        float[] joint = new float[3], tile = new float[4];
        for (int side : new int[] {64, 32}) {
            for (float[][] floor : new float[][][] {{dark, lightGrout}, {brown, paleGrout}}) {
                Tiles.Shape tiles = Tiles.laid(diagonalFloor(side, 0.906f, 0.969f, side / 32f, floor[1], floor[0], floor[0]), side);
                check(tiles != null && tiles.diagonal == 2 && !tiles.checkered && tiles.grouted, side + " dots: tiles on the diagonal with grout lighter than them are read so");
                int key = FloorBuilder.diagonalKey(tiles, 0);
                WallSides.joint(key, joint);
                WallSides.tint(key, tile);
                check(Math.abs(tile[0] - floor[0][0]) < 0.03f && Math.abs(joint[0] - floor[1][0]) < 0.05f && Math.abs(joint[2] - floor[1][2]) < 0.05f,
                        side + " dots: the picture is handed the tiles' colour and the grout's, lighter as it is: " + Arrays.toString(tile) + " joints " + Arrays.toString(joint));
            }
            Tiles.Shape nine = Tiles.laid(diagonalFloor(side, 0.906f, 0.953f, side / 32f, darker, mustard, mustard), side);
            check(nine != null && Math.abs(nine.phase - 0.906f) < 1.5f / side && Math.abs(nine.phaseAcross - 0.953f) < 1.5f / side,
                    side + " dots: the joints along a + b and along a - b each at their own place: " + (nine == null ? "none" : nine.phase + " and " + nine.phaseAcross));
            for (float[] other : new float[][] {{0.44f, 0.43f, 0.48f}, white}) {
                Tiles.Shape chequer = Tiles.laid(diagonalFloor(side, 0.906f, 0.938f, 0f, black, black, other), side);
                check(chequer != null && chequer.checkered && !chequer.grouted, side + " dots: two colours that meet on the diagonal with no grout are read so");
                for (int odd = 0; odd < 2; odd++) {
                    int key = FloorBuilder.diagonalKey(chequer, odd);
                    WallSides.joint(key, joint);
                    WallSides.tint(key, tile);
                    check(Math.abs(joint[0] - tile[0]) < 0.07f && Math.abs(joint[1] - tile[1]) < 0.07f && Math.abs(joint[2] - tile[2]) < 0.07f,
                            side + " dots: and laid with no line between them: " + Arrays.toString(tile) + " joints " + Arrays.toString(joint));
                }
            }
        }
    }

    private static void parquetIsItsBlocksOfSlats() {
        for (int slats = Tiles.FEWEST_SLATS; slats <= Tiles.MOST_SLATS; slats++) {
            for (boolean evenAlongY : new boolean[] {true, false}) {
                int count = slats;
                Tiles.Shape parquet = Tiles.laid(floor(64, (a, b) -> {
                    int x = (int) (a * 4f), y = (int) (b * 4f);
                    float inA = a * 4f - x, inB = b * 4f - y;
                    if (inA > 0.93f || inB > 0.93f) {
                        return new float[] {0.3f, 0.17f, 0.12f};
                    }
                    boolean alongY = (x + y) % 2 == 0 == evenAlongY;
                    float across = (alongY ? inA : inB) / 0.93f * count;
                    float shade = 0.5f + 0.06f * (float) Math.sin(Math.PI * (across - Math.floor(across))) - (across % 1f < 0.12f ? 0.08f : 0f);
                    return new float[] {shade, shade * 0.6f, shade * 0.4f};
                }), 64);
                check(parquet != null && parquet.blocks == 4 && parquet.slats == slats && parquet.evenAlongY == evenAlongY,
                        "parquet of four blocks a side of " + slats + " slats, the even ones along " + (evenAlongY ? "y" : "x") + ", is read so: "
                        + (parquet == null ? "none" : parquet.blocks + " blocks, " + parquet.slats + " slats, " + parquet.evenAlongY + ", " + parquet.diagonal + " diagonal, " + parquet.count + " tiles"));
            }
        }
        check(WorldScene.parquetOf(Tiles.FEWEST_BLOCKS, Tiles.FEWEST_SLATS, false) == WorldScene.FIRST_PARQUET
                && WorldScene.parquetOf(Tiles.MOST_BLOCKS, Tiles.MOST_SLATS, true) == WorldScene.LAST_PARQUET
                && WorldScene.parquetOf(4, 2, true) != WorldScene.parquetOf(4, 3, false) && WorldScene.parquetOf(3, 4, true) + 1f == WorldScene.parquetOf(4, 2, false),
                "each count of blocks and slats and lie of parquet has a material number of its own");
        check(32f * (WorldScene.LAST_PARQUET + 1f) <= WorldScene.LIT_STEP, "every parquet material is under the light a face's openness counts above it: "
                + WorldScene.LAST_PARQUET + " thirty-twos against " + WorldScene.LIT_STEP);
        check(WorldScene.FIRST_PARQUET > WorldScene.LAST_BOARDS && PipelineShaders.WORLD_VERTEX.contains("vMaterial > " + (int) (WorldScene.FIRST_PARQUET - 1f) + ".5 && vMaterial < " + (int) WorldScene.LAST_PARQUET + ".5")
                && PipelineShaders.WORLD_VERTEX.contains("vMaterial - " + (int) WorldScene.FIRST_PARQUET + ".0")
                && PipelineShaders.WORLD_VERTEX.contains(Tiles.FEWEST_BLOCKS + ".0 + floor(parquetAs / " + 2 * (Tiles.MOST_SLATS - Tiles.FEWEST_SLATS + 1) + ".0)")
                && PipelineShaders.WORLD_VERTEX.contains("(" + Tiles.FEWEST_SLATS + ".0 + mod(floor(parquetAs / 2.0), " + (Tiles.MOST_SLATS - Tiles.FEWEST_SLATS + 1) + ".0))"),
                "and the picture reads the same numbers back");
    }

    private static void shadedTilesAreAGrid() {
        // Four tiles a side, each lighter towards two of its edges and darker towards the other two, with a dark joint.
        Tiles.Shape tiles = Tiles.laid(floor(64, (a, b) -> {
            float inA = a * 4f % 1f, inB = b * 4f % 1f;
            if (inA > 0.92f || inB > 0.92f) {
                return new float[] {0.4f, 0.33f, 0.27f};
            }
            float shade = 0.7f + (inA < 0.18f || inB < 0.18f ? 0.12f : 0f) - (inA > 0.75f || inB > 0.75f ? 0.12f : 0f);
            return new float[] {shade, shade * 0.86f, shade * 0.74f};
        }), 64);
        check(tiles != null && tiles.count == 4, "tiles shaded towards their edges are four a side: " + (tiles == null ? "none" : String.valueOf(tiles.count)));
        check(Math.abs(tiles.colours[0] - 0.7f) < 0.01f, "each in the colour of its middle: " + tiles.colours[0]);
    }

    private static void diamondsCutToTheSquareCoverIt() {
        for (int count = 1; count <= 4; count++) {
            for (float phase : new float[] {0f, 0.3125f, 0.875f}) {
                float total = 0f;
                boolean inside = true, right = true;
                for (int odd = 0; odd < 2; odd++) {
                    float[] cut = Tiles.diamondTriangles(count, phase, 1f - phase, odd == 1);
                    for (int at = 0; at < cut.length; at += 6) {
                        float twice = (cut[at + 2] - cut[at]) * (cut[at + 5] - cut[at + 1]) - (cut[at + 4] - cut[at]) * (cut[at + 3] - cut[at + 1]);
                        total += Math.abs(twice) / 2f;
                        right &= twice > 0f;
                        float a = (cut[at] + cut[at + 2] + cut[at + 4]) / 3f, b = (cut[at + 1] + cut[at + 3] + cut[at + 5]) / 3f;
                        for (int corner = 0; corner < 6; corner++) {
                            inside &= cut[at + corner] >= -1e-5f && cut[at + corner] <= 1f + 1e-5f;
                        }
                        // Each triangle lies in a diamond of its own colour.
                        right &= Math.floorMod((int) Math.floor(count * (a + b) - phase) + (int) Math.floor(count * (a - b) - (1f - phase)), 2) == odd;
                    }
                }
                check(Math.abs(total - 1f) < 1e-4f && inside, "the diamonds of both colours cut to the square cover it once, " + count + " joints from " + phase + ": " + total);
                check(right, "each piece in its own colour's diamond, its corners going round as the square's do");
            }
        }
    }

    /**
     * A sheet holding one floor sprite as the game paints it, a diamond 128 dots across and 64
     * down with its top corner at dot (64, 0): every dot whose middle is in the diamond, and the
     * staircase dots that touch it, in the colour the floor has at the place the dot's middle shows.
     */
    private static ByteBuffer diamond(BiFunction<Float, Float, float[]> colour, float kept) {
        ByteBuffer sheet = ByteBuffer.allocate(128 * 64 * 4);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 128; x++) {
                float u = x + 0.5f - 64f, v = y + 0.5f;
                float a = (v / 32f + u / 64f) / 2f, b = (v / 32f - u / 64f) / 2f;
                if (a < -0.02f || b < -0.02f || a > 1.02f || b > 1.02f || a > kept) {
                    continue;
                }
                float[] c = colour.apply(a, b);
                int at = (y * 128 + x) * 4;
                sheet.put(at, (byte) Math.round(c[0] * 255f)).put(at + 1, (byte) Math.round(c[1] * 255f)).put(at + 2, (byte) Math.round(c[2] * 255f)).put(at + 3, (byte) 255);
            }
        }
        return sheet;
    }

    /** Joints running corner to corner across the square, two to a square each way, as a floor of tiles laid on the diagonal has them. */
    private static float[] diagonalTiles(float a, float b) {
        float one = (float) Math.cos(2.0 * Math.PI * 2.0 * (a + b)), other = (float) Math.cos(2.0 * Math.PI * 2.0 * (a - b));
        float shade = 0.5f + 0.2f * one + 0.2f * other;
        return new float[] {shade, shade * 0.8f, shade * 0.6f};
    }

    private static void floorLaidFlatMeetsItselfAtTheSquaresEdge() {
        byte[] dots = Flats.laid(diamond(WindowTests::diagonalTiles, 2f), 128, 0, 0, 128, 64, 64f, 192f, 2f, 0f, 192f, 128f, 64f, 0f, 0f, 0f, false);
        check(dots != null, "a diamond painted to its edges is a square of floor");
        int side = Flats.SIDE;
        float worst = 0f;
        for (int j = 0; j < side; j++) {
            for (int i = 0; i < side; i++) {
                float[] c = diagonalTiles((i + 0.5f) / side, (j + 0.5f) / side);
                check((dots[(j * side + i) * 4 + 3] & 0xff) == 255, "every dot of the flat picture is painted");
                worst = Math.max(worst, Math.abs((dots[(j * side + i) * 4] & 0xff) / 255f - c[0]));
            }
        }
        check(worst < 0.06f, "each dot of the flat picture is the floor at its own place, edges and corners too: off by " + worst);
        // Across the square's edge the picture goes on as it does inside the square: the last dot and the first are a dot apart.
        float seam = 0f, inside = 0f;
        for (int k = 0; k < side; k++) {
            seam = Math.max(seam, Math.abs((dots[(k * side) * 4] & 0xff) - (dots[(k * side + side - 1) * 4] & 0xff)));
            seam = Math.max(seam, Math.abs((dots[k * 4] & 0xff) - (dots[((side - 1) * side + k) * 4] & 0xff)));
            inside = Math.max(inside, Math.abs((dots[(k * side + 40) * 4] & 0xff) - (dots[(k * side + 41) * 4] & 0xff)));
        }
        check(seam <= inside + 6f, "the joints run on through the edge of the square: a step of " + seam + " there against " + inside + " inside it");
        // The rim holds what lies past each edge: the far side of the square.
        byte[] whole = Flats.rimmed(dots, true);
        int rim = Flats.RIM, all = Flats.WHOLE;
        check(whole[(rim * all + rim) * 4] == dots[0] && whole[(rim * all + rim - 1) * 4] == dots[(side - 1) * 4]
                && whole[((rim - 1) * all + rim) * 4] == dots[(side - 1) * side * 4] && whole[((all - 1) * all + all - 1) * 4] == dots[((rim - 1) * side + rim - 1) * 4],
                "the rim round the flat picture is the far side of the square");
        byte[] ended = Flats.rimmed(dots, false);
        check(ended[(rim * all + rim - 1) * 4] == dots[0] && ended[((all - 1) * all + all - 1) * 4] == dots[((side - 1) * side + side - 1) * 4],
                "and a roof's rim, which the next tile up is no part of, is its own last dots");
        // The same floor tipped up as a tile of roof, a sixth of a level over the square: laid flat on that slope it is the same picture.
        // The frame dot of a point of that slope is 64 * (a - b) + 64 across and 32 * a + 192 down: the climb takes back what b adds.
        ByteBuffer tipped = ByteBuffer.allocate(128 * 32 * 4);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 128; x++) {
                float a = (y + 0.5f) / 32f, b = a - (x + 0.5f - 64f) / 64f;
                if (b < -0.02f || b > 1.02f) {
                    continue;
                }
                float[] c = diagonalTiles(a, b);
                int at = (y * 128 + x) * 4;
                tipped.put(at, (byte) Math.round(c[0] * 255f)).put(at + 1, (byte) Math.round(c[1] * 255f)).put(at + 2, (byte) Math.round(c[2] * 255f)).put(at + 3, (byte) 255);
            }
        }
        byte[] slope = Flats.laid(tipped, 128, 0, 0, 128, 32, 64f, 192f, 2f, 0f, 192f, 128f, 32f, 0f, 0f, 1f / 6f, true);
        check(slope != null, "a tile of roof painted to its edges is laid flat on its slope");
        float off = 0f;
        for (int j = 8; j < side - 8; j++) {
            for (int i = 8; i < side - 8; i++) {
                off = Math.max(off, Math.abs((slope[(j * side + i) * 4] & 0xff) / 255f - diagonalTiles((i + 0.5f) / side, (j + 0.5f) / side)[0]));
            }
        }
        check(off < 0.08f, "and each dot of it is the roof at its own place on the slope: off by " + off);
        // The same tile cut along its diagonal by a hip: the half it paints is its picture and the other half is empty.
        for (int y = 0; y < 32; y++) {
            for (int x = 64; x < 128; x++) {
                tipped.put((y * 128 + x) * 4 + 3, (byte) 0);
            }
        }
        byte[] hip = Flats.laid(tipped, 128, 0, 0, 128, 32, 64f, 192f, 2f, 0f, 192f, 128f, 32f, 0f, 0f, 1f / 6f, true);
        check(hip != null && hip[(96 * side + 32) * 4 + 3] != 0 && hip[(32 * side + 96) * 4 + 3] == 0, "a tile of roof cut by a hip has a picture of the half it paints");
        check(Flats.laid(tipped, 128, 0, 0, 128, 32, 64f, 192f, 2f, 0f, 192f, 128f, 32f, 0f, 0f, 1f / 6f, false) == null, "and a floor painted by half is still no floor");
        // Boards: five to a tile along y and eight along x, each with a dark joint down one edge.
        byte[] boardsY = new byte[side * side * 4], boardsX = new byte[side * side * 4];
        for (int j = 0; j < side; j++) {
            for (int i = 0; i < side; i++) {
                for (int channel = 0; channel < 3; channel++) {
                    boardsY[(j * side + i) * 4 + channel] = (byte) (i * 5 % side < 8 ? 60 : 180);
                    boardsX[(j * side + i) * 4 + channel] = (byte) (j * 8 % side < 12 ? 60 : 180);
                }
            }
        }
        Flats.Boards alongY = Flats.boarded(boardsY), alongX = Flats.boarded(boardsX);
        check(alongY != null && alongY.alongY() && alongY.count() == 5, "five boards to a tile along y are read as that: " + alongY);
        check(alongX != null && !alongX.alongY() && alongX.count() == 8, "and eight along x as that: " + alongX);
        check(alongY != null && Math.abs(alongY.red() - (0.0625f * 60f + 0.9375f * 180f) / 255f) < 0.01f, "with the square's mean colour");
        check(Flats.boarded(dots) == null, "and tiles are no boards");
        // Grain with no joints: streaks along y that wander and never come round evenly.
        byte[] grain = new byte[side * side * 4];
        Random streaks = new Random(7);
        int[] streak = new int[side];
        for (int i = 0; i < side; i++) {
            streak[i] = 120 + streaks.nextInt(40);
        }
        for (int j = 0; j < side; j++) {
            for (int i = 0; i < side; i++) {
                for (int channel = 0; channel < 3; channel++) {
                    grain[(j * side + i) * 4 + channel] = (byte) (streak[i] + streaks.nextInt(4));
                }
            }
        }
        check(Flats.boarded(grain) == null, "and wood grain with no joints is no boards");
        check(WorldScene.boardsOf(Flats.FEWEST_BOARDS, false) == WorldScene.FIRST_BOARDS && WorldScene.boardsOf(Flats.MOST_BOARDS, true) == WorldScene.LAST_BOARDS
                && WorldScene.boardsOf(5, true) == 21f, "each count and lie of boards has a material number of its own");
        // The picture's vertex program reads them back: the numbers it takes for boards are the ones boardsOf gives, and its first count is the fewest.
        check(PipelineShaders.WORLD_VERTEX.contains("vMaterial > " + (int) (WorldScene.FIRST_BOARDS - 1f) + ".5 && vMaterial < " + (int) WorldScene.LAST_BOARDS + ".5")
                && PipelineShaders.WORLD_VERTEX.contains(Flats.FEWEST_BOARDS + ".0 + floor((vMaterial - " + (int) WorldScene.FIRST_BOARDS + ".0) / 2.0)")
                && PipelineShaders.WORLD_VERTEX.contains("mod(vMaterial - " + (int) WorldScene.FIRST_BOARDS + ".0, 2.0)"), "and the picture reads the same numbers back");
        // How much of its square a sprite paints decides what it is: under three tenths nothing, up to 97 in 100 a cut tile of roof and no floor, above that a whole square.
        for (float kept : new float[] {0.27f, 0.33f, 0.95f, 0.99f}) {
            byte[] roof = Flats.laid(diamond(WindowTests::diagonalTiles, kept), 128, 0, 0, 128, 64, 64f, 192f, 2f, 0f, 192f, 128f, 64f, 0f, 0f, 0f, true);
            byte[] ground = Flats.laid(diamond(WindowTests::diagonalTiles, kept), 128, 0, 0, 128, 64, 64f, 192f, 2f, 0f, 192f, 128f, 64f, 0f, 0f, 0f, false);
            int far = (64 * side + side - 4) * 4 + 3;
            check((roof != null) == kept > 0.3f, "a sprite that paints " + kept + " of its plane is a tile of roof or is not");
            check((ground != null) == kept > 0.97f, "a sprite that paints " + kept + " of its square is a floor or is not");
            check(roof == null || (roof[far] != 0) == kept > 0.97f, "and only a whole tile is painted to its far edge: " + kept);
        }
    }

    private static void halfASquareIsNoFlatFloor() {
        check(Flats.laid(diamond(WindowTests::diagonalTiles, 0.5f), 128, 0, 0, 128, 64, 64f, 192f, 2f, 0f, 192f, 128f, 64f, 0f, 0f, 0f, false) == null,
                "a sprite that paints half its square is left to its sprite");
    }

    /** How bright a wall of courses is at a place along it and a height up it, each 0 to 1: eight courses, each a shade of its own, with a joint a third of the way along. */
    private static float courses(float along, float up) {
        float course = (float) Math.floor(up * 8f);
        return 0.35f + 0.05f * course + (Math.abs(along - 0.33f) < 0.02f ? -0.2f : 0f);
    }

    private static void wallStoodSquareLosesTheDarkLineAlongItsTop() {
        // Walls along the north edge with a line three dots deep along the top, as the game paints them: black over a trailer's
        // pale panels (location_trailer_01_25), olive over walls_interior_house_04's paper, the trailer's again on a low wall half
        // a level high, and a top only a little darker than the wall under it, which is the wall's own and stays.
        int[][] kinds = {{0, 235}, {97, 166}, {0, 235}, {110, 120}};
        for (int kind = 0; kind < kinds.length; kind++) {
            int line = kinds[kind][0], body = kinds[kind][1];
            float tops = kind == 2 ? 0.5f : 1f;
            ByteBuffer sheet = ByteBuffer.allocate(128 * 256 * 4);
            for (int y = 0; y < 256; y++) {
                for (int x = 64; x < 128; x++) {
                    float along = (x + 0.5f - 64f) / 64f, top = 192f + 32f * along - 192f * tops;
                    if (y + 0.5f < top || y + 0.5f > 192f + 32f * along) {
                        continue;
                    }
                    int shade = y + 0.5f - top < 3f ? line : body, at = (y * 128 + x) * 4;
                    sheet.put(at, (byte) shade).put(at + 1, (byte) shade).put(at + 2, (byte) shade).put(at + 3, (byte) 255);
                }
            }
            byte[] dots = Flats.stood(sheet, 128, 0, 0, 128, 256, true, 64f, 192f, 2f, 0f, 0f, 128f, 256f);
            int wide = Flats.WALL_LONG, darkest = 255, first = Math.round(Flats.WALL_HIGH * (1f - tops));
            for (int i = 4; i < wide - 4; i++) {
                for (int j = first; j < first + 8; j++) {
                    darkest = Math.min(darkest, dots[(j * wide + i) * 4] & 0xff);
                }
            }
            if (kind == 3) {
                check(darkest < 115, "a top a little darker than the wall is the wall's and stays: darkest " + darkest);
            } else {
                check(darkest == body, (kind == 2 ? "a low wall" : "a wall") + " stood square has no dark line along its top: darkest " + darkest + " under " + line);
            }
        }
    }

    private static void wallStoodSquareRunsOnIntoTheNextLength() {
        // A wall along the north edge as the game paints it, in a whole frame of 128 by 256 with the square's corner at (64, 192):
        // on the slant, stopping three dots short of its far end, with a window in it.
        ByteBuffer sheet = ByteBuffer.allocate(128 * 256 * 4);
        for (int y = 0; y < 256; y++) {
            for (int x = 64; x < 125; x++) {
                float along = (x + 0.5f - 64f) / 64f, up = (192f + 32f * along - (y + 0.5f)) / 192f;
                boolean window = along > 0.3f && along < 0.6f && up > 0.3f && up < 0.7f;
                if (up < 0f || up > 1f || window) {
                    continue;
                }
                byte shade = (byte) Math.round(courses(along, up) * 255f);
                int at = (y * 128 + x) * 4;
                sheet.put(at, shade).put(at + 1, shade).put(at + 2, shade).put(at + 3, (byte) 255);
            }
        }
        byte[] dots = Flats.stood(sheet, 128, 0, 0, 128, 256, true, 64f, 192f, 2f, 0f, 0f, 128f, 256f);
        check(dots != null, "a wall painted along the north edge is a wall");
        int wide = Flats.WALL_LONG, high = Flats.WALL_HIGH;
        float worst = 0f;
        for (int j = 4; j < high - 4; j++) {
            for (int i = 0; i < wide; i++) {
                float along = (i + 0.5f) / wide, up = 1f - (j + 0.5f) / high;
                boolean window = along > 0.3f && along < 0.6f && up > 0.3f && up < 0.7f;
                boolean edge = Math.abs(along - 0.3f) < 0.02f || Math.abs(along - 0.6f) < 0.02f || Math.abs(up - 0.3f) < 0.01f || Math.abs(up - 0.7f) < 0.01f;
                int at = (j * wide + i) * 4;
                if (edge) {
                    continue;
                }
                check(((dots[at + 3] & 0xff) > 127) != window, "the wall is painted to both its ends and the window is left open: dot " + i + ", " + j);
                // Off a course's own edge and off the joint, where two shades meet in one dot.
                float inCourse = up * 8f - (float) Math.floor(up * 8f);
                if (!window && inCourse > 0.15f && inCourse < 0.85f && Math.abs(along - 0.33f) > 0.04f && i < wide - 3) {
                    worst = Math.max(worst, Math.abs((dots[at] & 0xff) / 255f - courses(along, up)));
                }
            }
        }
        check(worst < 0.02f, "each course is level and in its own shade from end to end: off by " + worst);
        int low = (high - 20) * wide;
        check(dots[(low + wide - 1) * 4] == dots[(low + wide - 4) * 4] && dots[(low + wide - 1) * 4 + 3] != 0, "the dots the sprite's end lacks are the last it has");
        byte[] whole = Flats.wallRimmed(dots);
        int rim = Flats.WALL_RIM, all = Flats.WALL_WHOLE;
        check(whole[(20 * all + rim) * 4] == dots[20 * wide * 4] && whole[(20 * all + rim - 1) * 4] == dots[(20 * wide + wide - 1) * 4]
                && whole[(20 * all + all - 1) * 4] == dots[(20 * wide + rim - 1) * 4], "and the rim at each end is the wall's other end, which is the next length's");
        check(Flats.stood(sheet, 128, 0, 0, 128, 256, false, 64f, 192f, 2f, 0f, 0f, 128f, 256f) == null, "a sprite with nothing along the west edge is no wall there");
        // A wall along the west edge runs the other way across its sprite. This one stops three dots short of its far end and
        // eight short of its top, and its last tenth is a paler band, so what fills the gaps can be told from the rest.
        ByteBuffer west = ByteBuffer.allocate(128 * 256 * 4);
        for (int y = 0; y < 256; y++) {
            for (int x = 3; x < 64; x++) {
                float along = (64f - (x + 0.5f)) / 64f, up = (192f + 32f * along - (y + 0.5f)) / 192f;
                if (up < 0f || up > 1f - 8f / 192f) {
                    continue;
                }
                byte shade = (byte) Math.round((courses(along, up) + (along > 0.9f ? 0.1f : 0f)) * 255f);
                int at = (y * 128 + x) * 4;
                west.put(at, shade).put(at + 1, shade).put(at + 2, shade).put(at + 3, (byte) 255);
            }
        }
        byte[] stood = Flats.stood(west, 128, 0, 0, 128, 256, false, 64f, 192f, 2f, 0f, 0f, 128f, 256f);
        check(stood != null, "a wall painted along the west edge is a wall");
        float westOff = 0f;
        for (int j = 12; j < high - 4; j++) {
            for (int i = 0; i < wide - 4; i++) {
                float along = (i + 0.5f) / wide, up = 1f - (j + 0.5f) / high, inCourse = up * 8f - (float) Math.floor(up * 8f);
                if (inCourse > 0.15f && inCourse < 0.85f && Math.abs(along - 0.33f) > 0.04f && Math.abs(along - 0.9f) > 0.03f) {
                    westOff = Math.max(westOff, Math.abs((stood[(j * wide + i) * 4] & 0xff) / 255f - courses(along, up) - (along > 0.9f ? 0.1f : 0f)));
                }
            }
        }
        check(westOff < 0.02f, "a west wall's start is the square's corner and its joint is where the sprite has it: off by " + westOff);
        int mid = (100 * wide + wide - 1) * 4;
        float midUp = 1f - 100.5f / high;
        check((stood[mid + 3] & 0xff) == 255 && Math.abs((stood[mid] & 0xff) / 255f - courses(0.95f, midUp) - 0.1f) < 0.02f, "the end the sprite stops short of is filled solid with the wall's last dots");
        int topDot = 40 * 4;
        check((stood[topDot + 3] & 0xff) == 255 && Math.abs((stood[topDot] & 0xff) / 255f - courses(0.6f, 1f - 10f / 192f)) < 0.02f, "and the top it stops short of with its top course");
        // The same wall with glass in its window is a glazed wall, and is left to its sprite.
        ByteBuffer glazed = ByteBuffer.allocate(128 * 256 * 4);
        glazed.put(sheet.duplicate().rewind()).rewind();
        for (int y = 0; y < 256; y++) {
            for (int x = 64; x < 125; x++) {
                float along = (x + 0.5f - 64f) / 64f, up = (192f + 32f * along - (y + 0.5f)) / 192f;
                if (along > 0.3f && along < 0.6f && up > 0.3f && up < 0.7f) {
                    glazed.put((y * 128 + x) * 4 + 3, (byte) 110);
                }
            }
        }
        check(Flats.stood(glazed, 128, 0, 0, 128, 256, true, 64f, 192f, 2f, 0f, 0f, 128f, 256f) == null, "a wall with a pane of glass in it has no picture");
        // A fence of pickets with gaps of all sorts at its ends, row by row, is left as painted.
        ByteBuffer pickets = ByteBuffer.allocate(128 * 256 * 4);
        for (int y = 0; y < 256; y++) {
            for (int x = 64; x < 128; x++) {
                float along = (x + 0.5f - 64f) / 64f, up = (192f + 32f * along - (y + 0.5f)) / 192f;
                int row = (int) Math.floor(up * 192f);
                if (up < 0f || up > 0.5f || (x - 64 + row / 3) % 8 < 4) {
                    continue;
                }
                pickets.put((y * 128 + x) * 4 + 3, (byte) 255);
            }
        }
        byte[] fence = Flats.stood(pickets, 128, 0, 0, 128, 256, true, 64f, 192f, 2f, 0f, 0f, 128f, 256f);
        int open = 0;
        for (int j = high / 2 + 4; j < high - 4; j++) {
            open += (fence[(j * wide) * 4 + 3] & 0xff) < 128 ? 1 : 0;
        }
        check(open > high / 8, "the gaps at a fence's end stay open: " + open + " rows of them");
        check(fence[(10 * wide + 30) * 4 + 3] == 0, "and nothing is painted over a low fence");
    }

    private static void cornerPostPictureCoversItsFacesSlack() {
        // A corner post as the game paints one: six columns trimmed out of a frame of 128, from 60, at the square's corner,
        // 64. Its four columns left of the corner are its west face and its two right of it its north face. A face cut to
        // the sprite runs two pixels on past it at tile scale 1, four here, and the picture is painted in the sprite's last
        // column that far and two pixels more, so the face's edge is not where its paint stops. Each column is a shade of its
        // own, so where a dot was taken from can be told.
        int spriteWide = 6, spriteHigh = 196, wide = Flats.WALL_LONG;
        ByteBuffer post = ByteBuffer.allocate(spriteWide * spriteHigh * 4);
        for (int y = 0; y < spriteHigh; y++) {
            for (int x = 0; x < spriteWide; x++) {
                int at = (y * spriteWide + x) * 4;
                post.put(at, (byte) (40 + 30 * x)).put(at + 1, (byte) 90).put(at + 2, (byte) 90).put(at + 3, (byte) 255);
            }
        }
        byte[] west = Flats.stood(post, spriteWide, 0, 0, spriteWide, spriteHigh, false, 64f, 192f, 2f, 60f, 0f, spriteWide, spriteHigh);
        byte[] north = Flats.stood(post, spriteWide, 0, 0, spriteWide, spriteHigh, true, 64f, 192f, 2f, 60f, 0f, spriteWide, spriteHigh);
        check(west != null && north != null, "a corner post is a wall both ways");
        // A tile is 64 dots along and 64 pixels across at tile scale 2: the west face is 4 dots of sprite, then 8 painted past it.
        int row = 100 * wide;
        for (int i = 0; i < 12; i++) {
            int at = (row + i) * 4;
            check((west[at + 3] & 0xff) == 255, "the west face and its slack are painted: dot " + i);
            if (i >= 4) {
                check((west[at] & 0xff) == 40, "the slack past the post's side is its last column: dot " + i + " is " + (west[at] & 0xff));
            }
        }
        check(west[(row + 13) * 4 + 3] == 0, "further on the post's west face is open");
        for (int i = 0; i < 10; i++) {
            int at = (row + i) * 4;
            check((north[at + 3] & 0xff) == 255, "the north face and its slack are painted: dot " + i);
            if (i >= 2) {
                check((north[at] & 0xff) == 190, "the slack past the post's other side is its last column there: dot " + i + " is " + (north[at] & 0xff));
            }
        }
        check(north[(row + 11) * 4 + 3] == 0, "further on the post's north face is open");
    }

    private static void doorwayToTheEndOfItsSquareHasNoWallInItsRim() {
        // One leaf's frame of a pair of doors: the doorway runs from the wall's start to three quarters along, and under
        // it the wall is whole. Each painted dot is as pale as it is far along, so the end its rim takes can be told.
        int wide = Flats.WALL_LONG, high = Flats.WALL_HIGH, rim = Flats.WALL_RIM, all = Flats.WALL_WHOLE;
        byte[] dots = new byte[wide * high * 4];
        for (int j = 0; j < high; j++) {
            for (int i = 0; i < wide; i++) {
                if (j < high / 2 && i < wide * 3 / 4) {
                    continue;
                }
                int at = (j * wide + i) * 4;
                dots[at] = dots[at + 1] = dots[at + 2] = (byte) (i * 4);
                dots[at + 3] = (byte) 255;
            }
        }
        // A row under it that the sprite stops a dot short of at its start is wall all the same.
        int shortRow = high - 10;
        dots[shortRow * wide * 4 + 3] = 0;
        byte[] whole = Flats.wallRimmed(dots);
        int door = 20, under = high - 20;
        for (int k = 0; k < rim; k++) {
            check(whole[(door * all + k) * 4 + 3] == 0, "beside a doorway that runs to the wall's start the rim is empty: dot " + k);
            int far = (door * all + rim + wide + k) * 4;
            check(whole[far + 3] == (byte) 255 && whole[far] == (byte) ((wide - 1) * 4), "and past the jamb at its end the rim is the jamb's last dot: dot " + k);
            int before = (under * all + k) * 4, after = (under * all + rim + wide + k) * 4;
            check(whole[before] == (byte) ((wide - rim + k) * 4) && whole[after] == (byte) (k * 4) && whole[before + 3] == (byte) 255 && whole[after + 3] == (byte) 255,
                    "under the doorway, whole at both ends, the rim is the next length's wall: dot " + k);
            int stopped = (shortRow * all + k) * 4;
            check(whole[stopped + 3] == (byte) 255 && whole[stopped] == (byte) ((wide - rim + k) * 4), "and so is the rim past a dot the sprite stops short by: dot " + k);
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }
}

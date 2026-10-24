package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.Arrays;

/** Tests of the skirting boards, rails and cornices built along inside walls: see Trims. */
final class TrimTests {
    private static final int LONG = Flats.WALL_LONG, HIGH = Flats.WALL_HIGH;
    private static final Trims.Light WHITE = (x, y, into) -> {
        into[0] = 1f;
        into[1] = 1f;
        into[2] = 1f;
    };

    private TrimTests() {}

    static void run() {
        Checks.run("aSkirtingAndACorniceAreReadAsTwoBands", TrimTests::aSkirtingAndACorniceAreReadAsTwoBands);
        Checks.run("anEvenlyPaintedPanelIsABoardAndABrickOrBlockOneIsLeftToItsSprite", TrimTests::anEvenlyPaintedPanelIsABoardAndABrickOrBlockOneIsLeftToItsSprite);
        Checks.run("aDadoPanelIsARailOverABoardBelowItsCornice", TrimTests::aDadoPanelIsARailOverABoardBelowItsCornice);
        Checks.run("aDadoPanelReadAtOneTimesIsBuiltAsAtTwo", TrimTests::aDadoPanelReadAtOneTimesIsBuiltAsAtTwo);
        Checks.run("aDadoDoorwayPieceAsTheGameReadsItIsBuiltLikeItsSiblings", TrimTests::aDadoDoorwayPieceAsTheGameReadsItIsBuiltLikeItsSiblings);
        Checks.run("aJointOfBlocksWhereARailWouldBeIsNoRail", TrimTests::aJointOfBlocksWhereARailWouldBeIsNoRail);
        Checks.run("aDarkLineAtThePanelsTopWithNoRailOverItIsNoRail", TrimTests::aDarkLineAtThePanelsTopWithNoRailOverItIsNoRail);
        Checks.run("aRailStandsProudOfTheBoardItCaps", TrimTests::aRailStandsProudOfTheBoardItCaps);
        Checks.run("blocksWithFaintJointsAreLeftToTheirSprite", TrimTests::blocksWithFaintJointsAreLeftToTheirSprite);
        Checks.run("blocksAreToldByTheirUprightJointsWhereTheLightUnderACourseFades", TrimTests::blocksAreToldByTheirUprightJointsWhereTheLightUnderACourseFades);
        Checks.run("everyCorniceIsOneMouldingWhateverItsBand", TrimTests::everyCorniceIsOneMouldingWhateverItsBand);
        Checks.run("aBandsHeightsAreRoundedSoTwoEdgesMeet", TrimTests::aBandsHeightsAreRoundedSoTwoEdgesMeet);
        Checks.run("aSkirtingStopsWhereADoorwayIs", TrimTests::aSkirtingStopsWhereADoorwayIs);
        Checks.run("aBandsColourIsItsPaintNotItsOutline", TrimTests::aBandsColourIsItsPaintNotItsOutline);
        Checks.run("aSkirtingStandsOnTheRoomsSideOfItsWallWithNoUndersideOnTheFloor", TrimTests::aSkirtingStandsOnTheRoomsSideOfItsWallWithNoUndersideOnTheFloor);
        Checks.run("everyFaceOfAMouldingIsCutWithItsWallInTheOverheadView", TrimTests::everyFaceOfAMouldingIsCutWithItsWallInTheOverheadView);
        Checks.run("everyFaceOfAMouldingLooksAwayFromItsWall", TrimTests::everyFaceOfAMouldingLooksAwayFromItsWall);
        Checks.run("aMouldingIsLitByTheRoomAndPaintedByItsMesh", TrimTests::aMouldingIsLitByTheRoomAndPaintedByItsMesh);
        Checks.run("aTiledWainscotIsLaidAsTilesAndNotAsMouldings", TrimTests::aTiledWainscotIsLaidAsTilesAndNotAsMouldings);
        Checks.run("twoMouldingsMeetInAMitreInTheCornerAndNeitherCrossesTheOther", TrimTests::twoMouldingsMeetInAMitreInTheCornerAndNeitherCrossesTheOther);
        Checks.run("onlyTheSameMouldingTurnsACorner", TrimTests::onlyTheSameMouldingTurnsACorner);
        Checks.run("somethingHungOverAMouldingStandsClearOfIt", TrimTests::somethingHungOverAMouldingStandsClearOfIt);
    }

    /** A wall stood square with rows first to last painted from column from to column to, in grey. */
    private static void paint(byte[] dots, int first, int last, int from, int to, int grey) {
        for (int row = first; row <= last; row++) {
            for (int column = from; column <= to; column++) {
                int at = (row * LONG + column) * 4;
                dots[at] = dots[at + 1] = dots[at + 2] = (byte) grey;
                dots[at + 3] = (byte) 255;
            }
        }
    }

    private static void aSkirtingAndACorniceAreReadAsTwoBands() {
        byte[] dots = new byte[LONG * HIGH * 4];
        paint(dots, HIGH - 12, HIGH - 1, 0, LONG - 1, 230);
        paint(dots, 0, 9, 0, LONG - 1, 230);
        Trims.Band[] bands = Trims.bands(dots, LONG, HIGH, 1f);
        check(bands.length == 2, "two bands: " + bands.length);
        Trims.Band cornice = bands[0], skirting = bands[1];
        check(cornice.top() == 1f && Math.abs(cornice.foot() - 30f / 32f) < 1e-4f, "the cornice runs up to the ceiling: " + cornice);
        check(skirting.foot() == 0f && Math.abs(skirting.top() - 2f / 32f) < 1e-4f, "the skirting stands on the floor, its top rounded to a 32nd of a level: " + skirting);
        check(skirting.from() == 0f && skirting.to() == 1f, "and runs the wall's length: " + skirting);
    }

    private static void anEvenlyPaintedPanelIsABoardAndABrickOrBlockOneIsLeftToItsSprite() {
        byte[] dots = new byte[LONG * HIGH * 4];
        paint(dots, HIGH - 56, HIGH - 1, 0, LONG - 1, 220);
        Trims.Band[] bands = Trims.bands(dots, LONG, HIGH, 1f);
        check(bands.length == 1 && bands[0].foot() == 0f && Math.abs(bands[0].top() - 9f / 32f) < 1e-4f, "a painted panel is one board from the floor up: " + bands[0]);
        // Courses of brick: a dark joint every sixth row and every sixteenth dot along.
        for (int row = HIGH - 56; row < HIGH; row += 6) {
            paint(dots, row, row, 0, LONG - 1, 70);
        }
        for (int column = 0; column < LONG; column += 16) {
            paint(dots, HIGH - 56, HIGH - 1, column, column, 70);
        }
        check(Trims.bands(dots, LONG, HIGH, 1f).length == 0, "a brick panel is a surface, left to its sprite");
        // Cast blocks: as even as paint, with a joint every fifteenth row, a dark dot over a light one.
        dots = new byte[LONG * HIGH * 4];
        paint(dots, HIGH - 56, HIGH - 1, 0, LONG - 1, 100);
        paint(dots, HIGH - 1, HIGH - 1, 0, LONG - 1, 60);
        check(Trims.bands(dots, LONG, HIGH, 1f).length == 1, "a painted panel with a shadow along its foot is still a board");
        for (int row = HIGH - 50; row < HIGH - 4; row += 15) {
            paint(dots, row, row, 0, LONG - 1, 90);
            paint(dots, row + 1, row + 1, 0, LONG - 1, 110);
        }
        check(Trims.bands(dots, LONG, HIGH, 1f).length == 0, "a panel of blocks is a surface, left to its sprite");
    }

    /** A wall stood square with a band painted from the floor up, row by row in the greys given from its top down. */
    private static byte[] panel(int[] greys) {
        byte[] dots = new byte[LONG * HIGH * 4];
        for (int i = 0; i < greys.length; i++) {
            int row = HIGH - greys.length + i;
            paint(dots, row, row, 0, LONG - 1, greys[i]);
        }
        return dots;
    }

    /** The bands of a dado panel read top down: its cornice, its rail and the board under the rail, each along the lengths of the wall given as from, to pairs of columns. */
    private static void checkDado(Trims.Band[] bands, String what, int... lengths) {
        int each = lengths.length / 2;
        check(bands.length == 1 + 2 * each, what + ": a cornice, then a rail and a board along each length: " + Arrays.toString(bands));
        if (bands.length != 1 + 2 * each) {
            return;
        }
        check(Trims.cornice(bands[0]), what + ": the cornice is kept: " + bands[0]);
        for (int i = 0; i < each; i++) {
            Trims.Band rail = bands[1 + i], board = bands[1 + each + i];
            float from = lengths[2 * i] == 0 ? 0f : (float) lengths[2 * i] / LONG, to = lengths[2 * i + 1] == LONG - 1 ? 1f : (lengths[2 * i + 1] + 1f) / LONG;
            check(Trims.rail(rail) && Math.abs(rail.top() - 9f / 32f) < 1e-4f && Math.abs(rail.foot() - 13f / 64f) < 1e-4f,
                    what + ": the rail runs from its shadow up to the panel's top: " + rail);
            check(!Trims.rail(board) && board.foot() == 0f && board.top() == rail.foot(), what + ": the board runs from the floor up to the rail: " + board);
            check(rail.from() == from && rail.to() == to && board.from() == from && board.to() == to, what + ": both along the same length of wall: " + rail + " " + board);
        }
    }

    private static void aDadoPanelIsARailOverABoardBelowItsCornice() {
        // walls_interior_detailing_01_2 as read: a rail shaded round with its shadow under it,
        // the panel painted evenly, a bead and its shadow along the floor.
        int[] greys = new int[53];
        Arrays.fill(greys, 214);
        int[] rail = {165, 199, 230, 221, 213, 214, 214, 214, 215, 206, 192, 202, 211, 187, 158, 197, 221, 213, 213};
        System.arraycopy(rail, 0, greys, 0, rail.length);
        int[] foot = {210, 206, 178, 151};
        System.arraycopy(foot, 0, greys, greys.length - foot.length, foot.length);
        byte[] dots = panel(greys);
        paint(dots, 0, 17, 0, LONG - 1, 220);
        Trims.Band[] bands = Trims.bands(dots, LONG, HIGH, 1f);
        checkDado(bands, "at 2x", 0, LONG - 1);
        if (bands.length == 3) {
            check(Math.abs(bands[1].red() - 214f / 255f) < 0.03f, "the rail is painted as its rows are, not as its shadow: " + bands[1]);
        }
    }

    private static void aDadoPanelReadAtOneTimesIsBuiltAsAtTwo() {
        // walls_interior_detailing_01_2 west as read from the 1x sheet: softer, so the faces either
        // side of the rail's shadow are as even as a course of blocks either side of its joint.
        int[] greys = {157, 172, 179, 177, 174, 173, 173, 172, 169, 163, 161, 164, 159, 146, 148, 165, 174, 172, 170, 171, 172, 172, 172, 172, 172, 172, 172,
                172, 172, 172, 172, 172, 172, 172, 172, 172, 172, 172, 172, 172, 172, 172, 172, 172, 172, 172, 172, 172, 171, 168, 156, 138, 133};
        byte[] dots = panel(greys);
        int[] cornice = {175, 175, 175, 175, 177, 178, 175, 169, 161, 154, 159, 167, 171, 170, 165, 161, 160, 163, 164, 155, 140, 127};
        for (int row = 0; row < cornice.length; row++) {
            paint(dots, row, row, 0, LONG - 1, cornice[row]);
        }
        checkDado(Trims.bands(dots, LONG, HIGH, 1f), "at 1x", 0, LONG - 1);
    }

    private static void aDadoDoorwayPieceAsTheGameReadsItIsBuiltLikeItsSiblings() {
        // walls_interior_detailing_01_42 west as the game reads it, in brightness by row: the
        // panel four dots of paint at one end of the wall and eight at the other, a doorway
        // between, and the last three dots the sprite's edge, taken on past its side, half as
        // bright again as the board and darker in its shadows.
        int[] cornice = {42, 42, 42, 46, 53, 51, 49, 48, 42, 46, 49, 49, 49, 48, 44, 44, 45, 47, 46, 40, 34};
        int[] corniceEdge = {73, 73, 73, 75, 77, 77, 77, 77, 23, 59, 77, 77, 74, 73, 73, 73, 74, 74, 74, 22, 26};
        byte[] dots = new byte[LONG * HIGH * 4];
        for (int row = 0; row < cornice.length; row++) {
            paint(dots, row, row, 0, LONG - 4, cornice[row]);
            paint(dots, row, row, LONG - 3, LONG - 1, corniceEdge[row]);
        }
        paint(dots, 21, 21, 0, 30, 29);
        paint(dots, 138, 138, 57, 63, 18);
        int[] greys = {44, 53, 52, 50, 49, 49, 49, 50, 48, 44, 48, 50, 43, 35, 43, 51, 50, 50, 50, 49, 49, 49, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50,
                50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 49, 50, 49, 49, 42, 32, 38};
        int[] edge = {63, 75, 74, 74, 74, 74, 74, 74, 74, 75, 75, 75, 23, 5, 60, 78, 78, 76, 75, 75, 75, 75, 74, 74, 74, 74, 74, 74, 74, 74, 74, 74, 74, 74, 74,
                74, 74, 74, 74, 74, 74, 74, 74, 74, 74, 75, 75, 75, 75, 74, 22, 5, 57};
        for (int i = 0; i < greys.length; i++) {
            int row = HIGH - greys.length + i;
            paint(dots, row, row, 0, 3, greys[i]);
            paint(dots, row, row, 56, LONG - 4, greys[i]);
            paint(dots, row, row, LONG - 3, LONG - 1, edge[i]);
        }
        checkDado(Trims.bands(dots, LONG, HIGH, 1f), "01_42 west", 0, 3, 56, LONG - 1);
    }

    private static void aJointOfBlocksWhereARailWouldBeIsNoRail() {
        // walls_interior_detailing_01_100 north as read from the 1x sheet, at the end of a wall:
        // stone blocks, their joints where a rail's shadow would be. The lowest of them taken for
        // a rail's shadow, the blocks under it read as a board.
        int[] greys = {128, 121, 114, 114, 113, 101, 88, 110, 131, 132, 132, 132, 131, 132, 132, 111, 90, 101, 113, 114, 114, 101, 87, 101, 114, 104, 93,
                103, 113, 121, 127, 128, 127, 109, 91, 109, 128, 128, 127, 122, 115, 112, 109, 112, 115, 124, 132, 123, 114, 115, 116, 103, 90, 103, 115, 124,
                131, 118};
        byte[] dots = new byte[LONG * HIGH * 4];
        for (int i = 0; i < greys.length; i++) {
            int row = HIGH - greys.length + i;
            paint(dots, row, row, 0, 9, greys[i]);
        }
        check(Trims.bands(dots, LONG, HIGH, 1f).length == 0, "a panel of stone blocks is a surface, left to its sprite: " + Arrays.toString(Trims.bands(dots, LONG, HIGH, 1f)));
    }

    private static void blocksWithFaintJointsAreLeftToTheirSprite() {
        // walls_interior_detailing_01_82 as read: green blocks a course of 26 rows high, their
        // joints 2 to 4 hundredths darker than the faces, about as faint as the shading of a rail.
        int[] greys = new int[57];
        Arrays.fill(greys, 163);
        greys[4] = 160;
        greys[5] = 157;
        greys[7] = 165;
        greys[31] = 159;
        greys[32] = 159;
        greys[34] = 166;
        greys[35] = 165;
        check(Trims.bands(panel(greys), LONG, HIGH, 1f).length == 0, "a panel of green blocks is a surface, left to its sprite");
    }

    private static void blocksAreToldByTheirUprightJointsWhereTheLightUnderACourseFades() {
        // walls_interior_detailing_01_116 as the game reads it: grey blocks, one joint too near
        // the panel's top to have a face over it, the other with a light edge under it fading
        // over three rows, so the face under it is not even.
        int[] greys = new int[58];
        Arrays.fill(greys, 78);
        System.arraycopy(new int[] {75, 74, 85, 81, 79}, 0, greys, 6, 5);
        System.arraycopy(new int[] {74, 82, 87, 81}, 0, greys, 34, 4);
        byte[] dots = panel(greys);
        check(Trims.bands(dots, LONG, HIGH, 1f).length == 1, "without its upright joints it reads as a board");
        int first = HIGH - greys.length;
        for (int column : new int[] {10, 32, 54}) {
            paint(dots, first + 11, first + 33, column, column, 72);
        }
        for (int column : new int[] {21, 43}) {
            paint(dots, first + 38, HIGH - 1, column, column, 72);
        }
        check(Trims.bands(dots, LONG, HIGH, 1f).length == 0, "with them it is a panel of blocks, left to its sprite");
        // A plaster panel beside a doorway: the reveal is dark the panel's whole height, and is no joint.
        dots = new byte[LONG * HIGH * 4];
        paint(dots, first, HIGH - 1, 0, LONG - 1, 200);
        paint(dots, first, HIGH - 1, 6, 6, 180);
        check(Trims.bands(dots, LONG, HIGH, 1f).length == 1, "a panel with a doorway's reveal is still a board");
        // A panel stopping short of the wall's end, shaded dark beside its end for part of its height.
        dots = new byte[LONG * HIGH * 4];
        paint(dots, first, HIGH - 1, 0, LONG - 3, 200);
        paint(dots, first + 10, first + 40, LONG - 5, LONG - 5, 180);
        check(Trims.bands(dots, LONG, HIGH, 1f).length == 1, "a panel shaded beside its end is still a board");
    }

    private static void everyCorniceIsOneMouldingWhateverItsBand() {
        float[] wide = Trims.section(new Trims.Band(0.88f, 1f, 0f, 1f, 0.9f, 0.9f, 0.9f)), narrow = Trims.section(new Trims.Band(0.9f, 1f, 0f, 1f, 0.9f, 0.9f, 0.9f));
        check(Arrays.equals(wide, narrow), "two cornices read from bands of two depths are one moulding, so they meet where their squares do");
        float lowest = Float.MAX_VALUE;
        for (int i = 1; i < wide.length; i += 2) {
            lowest = Math.min(lowest, wide[i]);
        }
        check(Math.abs(ViewCamera.LEVEL_HEIGHT - lowest - Trims.CORNICE_FALL) < 1e-4f, "it falls CORNICE_FALL down the wall: " + (ViewCamera.LEVEL_HEIGHT - lowest));
    }

    private static void aBandsHeightsAreRoundedSoTwoEdgesMeet() {
        byte[] north = new byte[LONG * HIGH * 4], west = new byte[LONG * HIGH * 4];
        paint(north, HIGH - 12, HIGH - 1, 0, LONG - 1, 230);
        paint(west, HIGH - 13, HIGH - 1, 0, LONG - 1, 230);
        check(Trims.bands(north, LONG, HIGH, 1f)[0].top() == Trims.bands(west, LONG, HIGH, 1f)[0].top(), "a skirting a dot taller on one edge meets the other at one height");
    }

    private static void aSkirtingStopsWhereADoorwayIs() {
        byte[] dots = new byte[LONG * HIGH * 4];
        paint(dots, HIGH - 12, HIGH - 1, 0, 19, 230);
        paint(dots, HIGH - 12, HIGH - 1, 44, LONG - 1, 230);
        Trims.Band[] bands = Trims.bands(dots, LONG, HIGH, 1f);
        check(bands.length == 2, "a band either side of the doorway: " + bands.length);
        check(bands[0].from() == 0f && Math.abs(bands[0].to() - 20f / LONG) < 1e-4f, "the first stops at the doorway: " + bands[0]);
        check(Math.abs(bands[1].from() - 44f / LONG) < 1e-4f && bands[1].to() == 1f, "the second starts past it: " + bands[1]);
    }

    private static void aBandsColourIsItsPaintNotItsOutline() {
        byte[] dots = new byte[LONG * HIGH * 4];
        paint(dots, HIGH - 12, HIGH - 1, 0, LONG - 1, 230);
        paint(dots, HIGH - 2, HIGH - 1, 0, LONG - 1, 40);
        Trims.Band band = Trims.bands(dots, LONG, HIGH, 1f)[0];
        check(Math.abs(band.red() - 230f / 255f) < 1e-3f, "the dark line along its foot is not its colour: " + band.red());
    }

    private static final Trims.Band SKIRTING = new Trims.Band(0f, 0.06f, 0f, 1f, 0.9f, 0.9f, 0.9f);
    private static final Trims.Band CORNICE = new Trims.Band(0.95f, 1f, 0f, 1f, 0.9f, 0.9f, 0.9f);
    private static final Trims.Band RAIL = new Trims.Band(13f / 64f, 9f / 32f, 0f, 1f, 0.8f, 0.8f, 0.8f);
    private static final Trims.Band UNDER_RAIL = new Trims.Band(0f, 13f / 64f, 0f, 1f, 0.8f, 0.8f, 0.8f);

    private static void aDarkLineAtThePanelsTopWithNoRailOverItIsNoRail() {
        int[] greys = new int[56];
        Arrays.fill(greys, 200);
        greys[0] = 150;
        greys[7] = 160;
        Trims.Band[] bands = Trims.bands(panel(greys), LONG, HIGH, 1f);
        check(bands.length == 1 && !Trims.rail(bands[0]) && bands[0].foot() == 0f, "a painted panel with a dark line near its top is one board: " + Arrays.toString(bands));
    }

    private static void aRailStandsProudOfTheBoardItCaps() {
        check(Trims.depth(RAIL) == Trims.RAIL && Trims.RAIL > Trims.depth(UNDER_RAIL) && Trims.depth(UNDER_RAIL) == Trims.BOARD,
                "a rail stands out of the wall further than the board under it: " + Trims.depth(RAIL) + " over " + Trims.depth(UNDER_RAIL));
        float[] section = Trims.section(RAIL);
        float foot = RAIL.foot() * ViewCamera.LEVEL_HEIGHT, top = RAIL.top() * ViewCamera.LEVEL_HEIGHT;
        check(section[1] == top && section[section.length - 1] == foot, "it runs from the top of its band down to the top of the board: " + Arrays.toString(section));
        check(section[section.length - 2] == Trims.BOARD, "and comes down onto the board's face, closing over it: " + Arrays.toString(section));
        check(Trims.turnsTheCorner(RAIL, RAIL) && !Trims.turnsTheCorner(RAIL, UNDER_RAIL), "a rail meets a rail in a corner, and not the board");
        Trims.Band[] bands = {CORNICE, RAIL, UNDER_RAIL};
        check(Trims.clearOf(bands, 0.2f, 0.8f, 0.1f, 0.25f) == Trims.RAIL, "something hung over the rail stands clear of it");
    }

    private static void aSkirtingStandsOnTheRoomsSideOfItsWallWithNoUndersideOnTheFloor() {
        // The north wall of square (4, 9), the room to the south of it, then the west wall, the room to the east.
        for (int edge = 0; edge < 2; edge++) {
            SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
            float da = edge == 0 ? 1f : 0f, db = 1f - da;
            Trims.add(mesh, SKIRTING, 4f, 9f, da, db, db, da, 0f, 0f, false, WHITE);
            check(mesh.triangles() > 0, "a skirting is built");
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                float x = mesh.vertices[at], y = mesh.vertices[at + 1], up = mesh.vertices[at + 2];
                float out = edge == 0 ? y - 9f : x - 4f, along = edge == 0 ? x - 4f : y - 9f;
                check(out >= WorldScene.WALL_HALF - 1e-5f && out <= WorldScene.WALL_HALF + Trims.BOARD + 1e-5f,
                        "it stands out of the skin on the room's side, by a board's thickness: " + out);
                check(along >= -1e-5f && along <= 1f + 1e-5f, "along its own length: " + along);
                check(up >= -1e-5f && up <= SKIRTING.top() * ViewCamera.LEVEL_HEIGHT + 1e-5f, "from the floor to its top: " + up);
                check(!(up < 1e-5f && mesh.vertices[at + 11] < -0.5f), "no face of it lies face down on the floor");
            }
        }
    }

    private static void aMouldingIsLitByTheRoomAndPaintedByItsMesh() {
        Trims.Band wood = new Trims.Band(0f, 0.06f, 0f, 1f, 0.5f, 0.3f, 0.15f);
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        Trims.add(mesh, wood, 4f, 9f, 1f, 0f, 0f, 1f, 0f, 0f, false, (x, y, into) -> {
            into[0] = 0.8f;
            into[1] = 0.6f;
            into[2] = 0.4f;
        });
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            check(mesh.vertices[at + 5] == 0.8f && mesh.vertices[at + 6] == 0.6f && mesh.vertices[at + 7] == 0.4f, "each vertex is the light alone");
        }
        check(Trims.paint(wood) != Trims.paint(SKIRTING), "a wooden and a white moulding are two meshes");
    }

    private static void everyFaceOfAMouldingIsCutWithItsWallInTheOverheadView() {
        // PipelineShaders.cutAway for a face not hung: one facing more up or down than along the
        // floor goes with the north wall where it is within 0.06 of the north line; one facing more
        // along the floor goes with the north wall where it is within 0.3 of the line and faces
        // more along y than x. Ends of a band whole along its square and of one cut short by a door.
        for (Trims.Band band : new Trims.Band[] {SKIRTING, CORNICE, new Trims.Band(0.4f, 0.43f, 0f, 1f, 0.5f, 0.3f, 0.2f), RAIL,
                new Trims.Band(0f, 0.12f, 0.3f, 0.7f, 0.9f, 0.9f, 0.9f)}) {
            SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
            Trims.add(mesh, band, 4f, 9f, 1f, 0f, 0f, 1f, 0f, 0f, false, WHITE);
            Trims.add(mesh, band, 4f, 9f, 1f, 0f, 0f, 1f, 0f, 0f, band.from() == 0f, WHITE);
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                float x = mesh.vertices[at], y = mesh.vertices[at + 1];
                float nx = mesh.vertices[at + 9], ny = mesh.vertices[at + 10], nz = mesh.vertices[at + 11];
                float offY = Math.abs(y - Math.round(y)), offX = Math.abs(x - Math.round(x));
                boolean lying = Math.abs(nz) > Math.max(Math.abs(nx), Math.abs(ny));
                boolean north = lying ? offY < 0.06f : offY < 0.3f && Math.abs(ny) > Math.abs(nx);
                check(north, "every face of a moulding goes with its own wall: normal " + nx + " " + ny + " " + nz + " at " + x + " " + y
                        + " (off the west line " + offX + ")");
            }
        }
    }

    private static void everyFaceOfAMouldingLooksAwayFromItsWall() {
        for (Trims.Band band : new Trims.Band[] {SKIRTING, CORNICE, new Trims.Band(0.4f, 0.43f, 0f, 1f, 0.5f, 0.3f, 0.2f), RAIL}) {
            SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
            Trims.add(mesh, band, 4f, 9f, 1f, 0f, 0f, 1f, 0f, 0f, false, WHITE);
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                float nx = mesh.vertices[at + 9], ny = mesh.vertices[at + 10];
                check(ny >= -1e-5f || Math.abs(nx) > 0.5f, "a face of " + band + " faces the room, an end or up or down, never the wall: " + ny);
            }
        }
    }

    private static void aTiledWainscotIsLaidAsTilesAndNotAsMouldings() {
        check(Trims.moulded("walls_interior_detailing_01_17"), "a skirting and cornice sheet is built as mouldings");
        check(Trims.is("walls_interior_detailing_bathroom_01_39") && !Trims.moulded("walls_interior_detailing_bathroom_01_39")
                && WallTiles.tiledSheet("walls_interior_detailing_bathroom_01_39"), "a bathroom's tiled wainscot is trim, laid as tiles");
    }

    private static void twoMouldingsMeetInAMitreInTheCornerAndNeitherCrossesTheOther() {
        // The north and the west wall of square (4, 9), each with its moulding to the corner.
        for (Trims.Band band : new Trims.Band[] {SKIRTING, CORNICE, new Trims.Band(0f, 0.28f, 0f, 1f, 0.9f, 0.9f, 0.9f), RAIL}) {
            SpriteGeometry.Mesh north = new SpriteGeometry.Mesh(), west = new SpriteGeometry.Mesh();
            Trims.add(north, band, 4f, 9f, 1f, 0f, 0f, 1f, 0f, 0f, true, WHITE);
            Trims.add(west, band, 4f, 9f, 0f, 1f, 1f, 0f, 0f, 0f, true, WHITE);
            java.util.Set<String> northDiagonal = new java.util.HashSet<>(), westDiagonal = new java.util.HashSet<>();
            for (int at = 0; at < north.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                float along = north.vertices[at] - 4f, across = north.vertices[at + 1] - 9f;
                check(along >= across - 1e-5f, band + ": no point of the north moulding is in the west one's half of the corner: " + along + " " + across);
                if (Math.abs(along - across) < 1e-5f) {
                    northDiagonal.add(String.format("%.4f %.4f %.4f", along, across, north.vertices[at + 2]));
                }
            }
            for (int at = 0; at < west.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                float along = west.vertices[at + 1] - 9f, across = west.vertices[at] - 4f;
                check(along >= across - 1e-5f, band + ": no point of the west moulding is in the north one's half of the corner: " + along + " " + across);
                if (Math.abs(along - across) < 1e-5f) {
                    westDiagonal.add(String.format("%.4f %.4f %.4f", across, along, west.vertices[at + 2]));
                }
            }
            check(!northDiagonal.isEmpty() && northDiagonal.equals(westDiagonal), band + ": the two ends are one section on the diagonal: " + northDiagonal + " and " + westDiagonal);
        }
    }

    private static void onlyTheSameMouldingTurnsACorner() {
        check(Trims.turnsTheCorner(SKIRTING, SKIRTING) && Trims.turnsTheCorner(CORNICE, new Trims.Band(0.9f, 1f, 0f, 1f, 0.5f, 0.5f, 0.5f)),
                "a skirting meets a skirting, a cornice a cornice of any band");
        check(Trims.turnsTheCorner(SKIRTING, new Trims.Band(0f, 0.06f + 1f / 32f, 0f, 1f, 0.9f, 0.9f, 0.9f)), "two readings a rounding apart are one moulding");
        check(!Trims.turnsTheCorner(SKIRTING, new Trims.Band(0f, 0.06f + 2f / 32f, 0f, 1f, 0.9f, 0.9f, 0.9f)), "a taller board is another");
        check(!Trims.turnsTheCorner(SKIRTING, CORNICE), "a skirting is no cornice");
        check(!Trims.turnsTheCorner(SKIRTING, new Trims.Band(0f, 0.06f, 0.3f, 1f, 0.9f, 0.9f, 0.9f)), "one that stops short of the corner, at a doorway, meets nothing there");
    }

    private static void somethingHungOverAMouldingStandsClearOfIt() {
        Trims.Band[] bands = {SKIRTING, CORNICE};
        check(Trims.clearOf(bands, 0.2f, 0.8f, 0.3f, 0.7f) == 0f, "a picture between the skirting and the cornice is on the bare wall");
        check(Trims.clearOf(bands, 0.2f, 0.8f, 0.02f, 0.3f) == Trims.depth(SKIRTING) && Trims.depth(SKIRTING) >= Trims.BOARD,
                "one over the skirting stands clear by the board: " + Trims.clearOf(bands, 0.2f, 0.8f, 0.02f, 0.3f));
        // The cornice's band starts 0.95 up, and its moulding falls further, CORNICE_FALL under the ceiling.
        float under = 1f - Trims.CORNICE_FALL / ViewCamera.LEVEL_HEIGHT;
        check(Trims.clearOf(bands, 0.2f, 0.8f, 0.6f, under + 0.01f) == Trims.depth(CORNICE) && Trims.depth(CORNICE) > Trims.depth(SKIRTING),
                "one up into the cornice's cove stands clear by it: " + Trims.clearOf(bands, 0.2f, 0.8f, 0.6f, under + 0.01f));
        check(Trims.clearOf(bands, 0.2f, 0.8f, 0.6f, under - 0.01f) == 0f, "one just under the cove is on the bare wall");
        check(Trims.clearOf(new Trims.Band[] {new Trims.Band(0f, 0.06f, 0.5f, 1f, 0.9f, 0.9f, 0.9f)}, 0f, 0.4f, 0.02f, 0.3f) == 0f,
                "and none where the skirting stops short of it along the wall");
    }
}

package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;

/** Tests of the battens read off a wall of upright boards. */
final class BattenTests {
    private static final int WIDE = 64, HIGH = 230, ROWS = HIGH - WIDE / 2;
    private static final int BOARD = 0x5a2a1c, JOINT = 0x48221a, BATTEN = 0x6a3424, EDGE = 0x3a1810, FAINT = 0x5e2c1e, LIT = 0x7a3c2a;
    private static final int UPPER = 36, LOWER = 132, DEEP = 12;

    private BattenTests() {}

    static void run() {
        Checks.run("battensAreReadAtTheirHeights", BattenTests::battensAreReadAtTheirHeights);
        Checks.run("battensEitherSideOfAWindowAreRead", BattenTests::battensEitherSideOfAWindowAreRead);
        Checks.run("aFaintBattenBetweenItsLinesIsRead", BattenTests::aFaintBattenBetweenItsLinesIsRead);
        Checks.run("aBattenWithNoLineOverItIsRead", BattenTests::aBattenWithNoLineOverItIsRead);
        Checks.run("aGablesBattensAreNotRead", BattenTests::aGablesBattensAreNotRead);
        Checks.run("uprightBoardsAloneHaveNoBattens", BattenTests::uprightBoardsAloneHaveNoBattens);
        Checks.run("lappedBoardsHaveNoBattens", BattenTests::lappedBoardsHaveNoBattens);
        Checks.run("aWallTooShortToReadHasNoBattens", BattenTests::aWallTooShortToReadHasNoBattens);
    }

    /** What is painted at a place on a wall set straight: how far along it in dots, and how many rows down. */
    private interface Wall {
        int at(int along, int row);
    }

    /** Upright boards eight dots wide, a dark joint between each two. */
    private static int boards(int along) {
        return along % 8 == 0 ? JOINT : BOARD;
    }

    /** A batten from row top to row top + deep: the dark line of its edge, its lit top, its face, and its shadow under it on the boards. */
    private static int batten(int along, int row, int top, int deep) {
        if (row == top) {
            return EDGE;
        }
        if (row == top + 1) {
            return LIT;
        }
        if (row > top && row < top + deep) {
            return BATTEN;
        }
        if (row == top + deep || row == top + deep + 1) {
            return EDGE;
        }
        return boards(along);
    }

    /** A batten from row top to row top + deep, its face face: see batten. */
    private static int batten(int along, int row, int top, int deep, int face) {
        int colour = batten(along, row, top, deep);
        return colour == BATTEN ? face : colour;
    }

    /** Both battens, each face face. */
    private static int twoBattens(int along, int row, int face) {
        return row >= LOWER && row <= LOWER + DEEP + 1 ? batten(along, row, LOWER, DEEP, face) : batten(along, row, UPPER, DEEP, face);
    }

    private static void battensAreReadAtTheirHeights() {
        checkBoth(measure((along, row) -> twoBattens(along, row, BATTEN)));
    }

    /** A window across the wall between them: nothing painted there. */
    private static void battensEitherSideOfAWindowAreRead() {
        checkBoth(measure((along, row) -> row > UPPER + DEEP + 6 && row < LOWER - 6 ? 0 : twoBattens(along, row, BATTEN)));
    }

    /** As a wall facing north paints them: the face barely lighter than the boards, the two lines as dark. */
    private static void aFaintBattenBetweenItsLinesIsRead() {
        checkBoth(measure((along, row) -> twoBattens(along, row, FAINT)));
    }

    /** As a wall facing west paints the lower one: its lit edge straight off the boards, no dark line over it. */
    private static void aBattenWithNoLineOverItIsRead() {
        checkBoth(measure((along, row) -> row == LOWER ? boards(along) : twoBattens(along, row, BATTEN)));
    }

    /** A gable's piece: the boards and battens of a wall, its top cut away on the slant, and the sprite trimmed to it. */
    private static void aGablesBattensAreNotRead() {
        int cut = 70;
        ByteBuffer whole = sprite((along, row) -> row < cut + along ? 0 : twoBattens(along, row, BATTEN), HIGH);
        ByteBuffer trimmed = whole.slice(cut * WIDE * 4, (HIGH - cut) * WIDE * 4);
        Checks.check(Battens.measure(trimmed, WIDE, 0, 0, WIDE, HIGH - cut, HIGH).length == 0, "a gable's battens are not read");
    }

    private static void checkBoth(float[] read) {
        Checks.check(read.length == 4, "two battens, got " + read.length / 2);
        if (read.length != 4) {
            return;
        }
        float lowFoot = 1f - (LOWER + DEEP) / (ROWS - 1f), lowTop = 1f - LOWER / (ROWS - 1f);
        float highFoot = 1f - (UPPER + DEEP) / (ROWS - 1f), highTop = 1f - UPPER / (ROWS - 1f);
        Checks.check(Math.abs(read[0] - lowFoot) < 0.03f && Math.abs(read[1] - lowTop) < 0.03f,
                "the lower batten from " + lowFoot + " to " + lowTop + ", got " + read[0] + " to " + read[1]);
        Checks.check(Math.abs(read[2] - highFoot) < 0.03f && Math.abs(read[3] - highTop) < 0.03f,
                "the upper batten from " + highFoot + " to " + highTop + ", got " + read[2] + " to " + read[3]);
    }

    private static void uprightBoardsAloneHaveNoBattens() {
        Checks.check(measure((along, row) -> boards(along)).length == 0, "upright boards are no battens");
    }

    /** Boards lapped along the wall, a shadow under each, every twelve rows: lines all the way up. */
    private static void lappedBoardsHaveNoBattens() {
        Checks.check(measure((along, row) -> row % 12 == 0 ? EDGE : row % 12 < 4 ? BATTEN : BOARD).length == 0, "lapped boards are no battens");
    }

    private static void aWallTooShortToReadHasNoBattens() {
        Checks.check(Battens.measure(sprite((along, row) -> boards(along), 40), WIDE, 0, 0, WIDE, 40, 40).length == 0, "a sliver of wall is not read");
    }

    private static float[] measure(Wall wall) {
        return Battens.measure(sprite(wall, HIGH), WIDE, 0, 0, WIDE, HIGH, HIGH);
    }

    /** A wall's sprite: the wall as the game's view shows it from the side, a dot down for every two along. */
    private static ByteBuffer sprite(Wall wall, int high) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(WIDE * high * 4);
        for (int y = 0; y < high; y++) {
            for (int x = 0; x < WIDE; x++) {
                int row = y - (x + 1) / 2;
                int colour = row < 0 || row >= high - WIDE / 2 ? 0 : wall.at(x, row);
                pixels.put((byte) (colour >> 16)).put((byte) (colour >> 8)).put((byte) colour).put((byte) (colour == 0 ? 0 : 255));
            }
        }
        pixels.flip();
        return pixels;
    }
}

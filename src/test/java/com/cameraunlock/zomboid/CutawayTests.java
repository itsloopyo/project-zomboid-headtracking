package com.cameraunlock.zomboid;

/** Tests of the block of cut walls the world shader is handed: see Cutaways. */
final class CutawayTests {
    private CutawayTests() {}

    static void run() {
        everySquareOfABlockReadsBackWhatWasPutInIt();
        aFloatHoldsItsTwelveSquaresExactly();
        onlyTheTwoWallMarksAreKept();
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    /** The marks a test puts on a square: every one of none, north, west and both, in no order that follows the block's rows. */
    private static int marks(int column, int row) {
        return (column * 7 + row * 13 + column * row) & 3;
    }

    private static void everySquareOfABlockReadsBackWhatWasPutInIt() {
        float[] rows = new float[Cutaways.FLOATS];
        Cutaways.clear(rows);
        for (int row = 0; row < Cutaways.SIDE; row++) {
            for (int column = 0; column < Cutaways.SIDE; column++) {
                if (marks(column, row) != 0) {
                    Cutaways.put(rows, column, row, marks(column, row));
                }
            }
        }
        for (int row = 0; row < Cutaways.SIDE; row++) {
            for (int column = 0; column < Cutaways.SIDE; column++) {
                check(Cutaways.at(rows, column, row) == marks(column, row), "the marks of square " + column + ", " + row + " read back as they were put");
            }
        }
    }

    private static void aFloatHoldsItsTwelveSquaresExactly() {
        check(Cutaways.DIGITS * 2 <= 24, "a float's 24 bits hold two for each of its squares");
        check(Cutaways.SIDE % Cutaways.DIGITS == 0, "a row is a whole number of floats");
        float[] rows = new float[Cutaways.FLOATS];
        Cutaways.clear(rows);
        for (int column = 0; column < Cutaways.DIGITS; column++) {
            Cutaways.put(rows, column, 0, Cutaways.NORTH | Cutaways.WEST);
        }
        check(rows[0] == (float) ((1 << 2 * Cutaways.DIGITS) - 1), "a float with both marks on every square is every one of its bits");
        check(rows[1] == 0f, "and the float beside it is untouched");
    }

    private static void onlyTheTwoWallMarksAreKept() {
        float[] rows = new float[Cutaways.FLOATS];
        Cutaways.clear(rows);
        Cutaways.put(rows, 3, 2, 0xff);
        check(Cutaways.at(rows, 3, 2) == (Cutaways.NORTH | Cutaways.WEST), "a mark the game keeps besides the two is left out");
        check(Cutaways.at(rows, 4, 2) == 0 && Cutaways.at(rows, 2, 2) == 0, "and does not run over into the squares beside it");
    }
}

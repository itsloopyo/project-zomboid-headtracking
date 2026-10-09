package com.cameraunlock.zomboid;

/** Tests of the block of cut walls the world shader is handed: see Cutaways. */
final class CutawayTests {
    private CutawayTests() {}

    static void run() {
        Checks.run("everySquareOfABlockReadsBackWhatWasPutInIt", CutawayTests::everySquareOfABlockReadsBackWhatWasPutInIt);
        Checks.run("aFloatHoldsItsTwelveSquaresExactly", CutawayTests::aFloatHoldsItsTwelveSquaresExactly);
        Checks.run("onlyTheTwoWallMarksAreKept", CutawayTests::onlyTheTwoWallMarksAreKept);
        Checks.run("theTopOfAWallIsCutWithItsWallWhicheverWayItLeans", CutawayTests::theTopOfAWallIsCutWithItsWallWhicheverWayItLeans);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
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

    /**
     * Whether the world shader's cutAway takes a point of a face away, as that shader works it
     * out, line for line, with the game's marks given as marked(x, y). The test below holds the
     * shader to these lines.
     */
    private static boolean cut(float x, float y, float facingX, float facingY, float facingZ, boolean hung, java.util.function.BiFunction<Integer, Integer, Integer> marked) {
        float edgeX = (float) Math.floor(x + 0.5f), edgeY = (float) Math.floor(y + 0.5f), offX = Math.abs(x - edgeX), offY = Math.abs(y - edgeY);
        boolean northCut = (marked.apply((int) Math.floor(x - 0.06f), (int) edgeY) & Cutaways.NORTH) != 0 || (marked.apply((int) Math.floor(x + 0.06f), (int) edgeY) & Cutaways.NORTH) != 0;
        boolean westCut = (marked.apply((int) edgeX, (int) Math.floor(y - 0.06f)) & Cutaways.WEST) != 0 || (marked.apply((int) edgeX, (int) Math.floor(y + 0.06f)) & Cutaways.WEST) != 0;
        if (!hung && Math.abs(facingZ) > Math.max(Math.abs(facingX), Math.abs(facingY))) {
            return offY < 0.06f && northCut || offX < 0.06f && westCut;
        }
        float reach = hung ? 0.45f : 0.3f;
        boolean north = offY < reach && (hung ? offY < offX : Math.abs(facingY) > Math.abs(facingX));
        boolean west = offX < reach && (hung ? offX <= offY : Math.abs(facingX) >= Math.abs(facingY));
        return north ? northCut : west && westCut;
    }

    private static final String[] CUT_LINES = {
        "if (!hung && abs(facing.z) > max(abs(facing.x), abs(facing.y))) {",
        "return off.y < 0.06 && northCut || off.x < 0.06 && westCut;",
        "float reach = hung ? 0.45 : 0.3;",
        "bool north = off.y < reach && (hung ? off.y < off.x : abs(facing.y) > abs(facing.x));",
        "bool west = off.x < reach && (hung ? off.x <= off.y : abs(facing.x) >= abs(facing.y));",
        "return north ? northCut : west && westCut;"};

    private static void theTopOfAWallIsCutWithItsWallWhicheverWayItLeans() {
        String shader = PipelineShaders.WORLD_FRAGMENT_PARTS;
        for (String line : CUT_LINES) {
            check(shader.contains(line), "the world shader's cutAway is the rule this test holds: " + line);
        }
        // A low north wall along y = 5240 from x 6480 to 6488, cut, and a low west fence along x = 6475 from y 5240 to 5248, cut.
        java.util.function.BiFunction<Integer, Integer, Integer> marked = (x, y) -> (y == 5240 && x >= 6480 && x < 6488 ? Cutaways.NORTH : 0)
                | (x == 6475 && y >= 5240 && y < 5248 ? Cutaways.WEST : 0);
        check(cut(6482.5f, 5240.04f, 0f, 1f, 0f, false, marked), "a cut north wall's own side is cut");
        check(cut(6474.96f, 5242.5f, 1f, 0f, 0f, false, marked), "a cut west fence's own side is cut");
        for (float along = 6480.02f; along < 6488f; along += 0.125f) {
            check(cut(along, 5240.03f, 0f, 0f, 1f, false, marked), "the level top of a cut north wall is cut all along it, joints included, at x " + along);
            check(cut(along, 5239.97f, 0.11f, 0f, 1f, false, marked), "and its top falling to the east, at x " + along);
        }
        for (float along = 5240.02f; along < 5248f; along += 0.125f) {
            check(cut(6475.03f, along, 0f, 0f, 1f, false, marked), "the level top of a cut west fence is cut all along it, at y " + along);
            check(cut(6474.97f, along, 0f, 0.11f, 1f, false, marked), "and its top falling to the south, at y " + along);
        }
        check(!cut(6482.5f, 5240.2f, 0f, 0f, 1f, false, marked), "a counter's top a fifth of a tile from a cut wall is no part of the wall");
        check(!cut(6482.5f, 5240.5f, 0f, 0f, 1f, false, marked), "a floor in the middle of a square is no wall's");
        check(!cut(6490.5f, 5240.03f, 0f, 0f, 1f, false, marked), "the top of a north wall the game has not cut stands");
    }

    private static void onlyTheTwoWallMarksAreKept() {
        float[] rows = new float[Cutaways.FLOATS];
        Cutaways.clear(rows);
        Cutaways.put(rows, 3, 2, 0xff);
        check(Cutaways.at(rows, 3, 2) == (Cutaways.NORTH | Cutaways.WEST), "a mark the game keeps besides the two is left out");
        check(Cutaways.at(rows, 4, 2) == 0 && Cutaways.at(rows, 2, 2) == 0, "and does not run over into the squares beside it");
    }
}

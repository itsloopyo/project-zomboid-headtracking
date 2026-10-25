package com.cameraunlock.zomboid;

import java.util.function.BiFunction;

/** Tests of the block of cut walls the world shader is handed: see Cutaways. */
final class CutawayTests {
    private CutawayTests() {}

    static void run() {
        Checks.run("everySquareOfABlockReadsBackWhatWasPutInIt", CutawayTests::everySquareOfABlockReadsBackWhatWasPutInIt);
        Checks.run("aFloatHoldsItsEightSquaresExactly", CutawayTests::aFloatHoldsItsEightSquaresExactly);
        Checks.run("onlyTheTwoWallMarksAreKept", CutawayTests::onlyTheTwoWallMarksAreKept);
        Checks.run("whatHangsStaysWhereTheGameKeepsIt", CutawayTests::whatHangsStaysWhereTheGameKeepsIt);
        Checks.run("theTopOfAWallIsCutWithItsWallWhicheverWayItLeans", CutawayTests::theTopOfAWallIsCutWithItsWallWhicheverWayItLeans);
        Checks.run("whatHangsOnACutWallIsCutWithItToItsEnds", CutawayTests::whatHangsOnACutWallIsCutWithItToItsEnds);
        Checks.run("aBoardOnAWallOutdoorsStandsForAViewerOutdoors", CutawayTests::aBoardOnAWallOutdoorsStandsForAViewerOutdoors);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** The marks a test puts on a square: every one of none, north, west and both, kept or not, in no order that follows the block's rows. */
    private static int marks(int column, int row) {
        return (column * 7 + row * 13 + column * row) & 7;
    }

    private static void everySquareOfABlockReadsBackWhatWasPutInIt() {
        float[] rows = new float[Cutaways.FLOATS];
        Cutaways.clear(rows);
        for (int row = 0; row < Cutaways.SIDE; row++) {
            for (int column = 0; column < Cutaways.SIDE; column++) {
                if (marks(column, row) != 0) {
                    Cutaways.put(rows, column, row, marks(column, row), (marks(column, row) & Cutaways.KEPT) != 0);
                }
            }
        }
        for (int row = 0; row < Cutaways.SIDE; row++) {
            for (int column = 0; column < Cutaways.SIDE; column++) {
                check(Cutaways.at(rows, column, row) == marks(column, row), "the marks of square " + column + ", " + row + " read back as they were put");
            }
        }
    }

    private static void aFloatHoldsItsEightSquaresExactly() {
        check(Cutaways.DIGITS * 3 <= 24, "a float's 24 bits hold three for each of its squares");
        check(Cutaways.SIDE * Cutaways.SIDE % Cutaways.DIGITS == 0, "the block is a whole number of floats");
        check(Cutaways.FLOATS % 4 == 0 && Cutaways.FLOATS * Cutaways.DIGITS >= Cutaways.SIDE * Cutaways.SIDE, "the floats fill whole vec4s and hold the block");
        float[] rows = new float[Cutaways.FLOATS];
        Cutaways.clear(rows);
        for (int column = 0; column < Cutaways.DIGITS; column++) {
            Cutaways.put(rows, column, 0, Cutaways.NORTH | Cutaways.WEST, true);
        }
        check(rows[0] == (float) ((1 << 3 * Cutaways.DIGITS) - 1), "a float with every mark on every square is every one of its bits");
        check(rows[1] == 0f, "and the float beside it is untouched");
    }

    /**
     * Whether the world shader's cutAway takes a point of a face away, as that shader works it
     * out, line for line, with the game's marks given as marked(x, y). The test below holds the
     * shader to these lines.
     */
    private static boolean cut(float x, float y, float facingX, float facingY, float facingZ, float hung, BiFunction<Integer, Integer, Integer> marked) {
        float edgeX = (float) Math.floor(x + 0.5f), edgeY = (float) Math.floor(y + 0.5f), offX = Math.abs(x - edgeX), offY = Math.abs(y - edgeY);
        float past = hung > 4.5f ? 0.25f : 0.06f;
        boolean northCut = wallCut(marked.apply((int) Math.floor(x - past), (int) edgeY), Cutaways.NORTH, hung) || wallCut(marked.apply((int) Math.floor(x + past), (int) edgeY), Cutaways.NORTH, hung);
        boolean westCut = wallCut(marked.apply((int) edgeX, (int) Math.floor(y - past)), Cutaways.WEST, hung) || wallCut(marked.apply((int) edgeX, (int) Math.floor(y + past)), Cutaways.WEST, hung);
        if (hung < 0.5f && Math.abs(facingZ) > Math.max(Math.abs(facingX), Math.abs(facingY))) {
            return offY < 0.06f && northCut || offX < 0.06f && westCut;
        }
        float reach = hung > 0.5f ? 0.45f : 0.3f, side = hung - 2f * (float) Math.floor((hung - 0.5f) / 2f);
        boolean north = offY < reach && (hung > 0.5f ? side < 1.5f : Math.abs(facingY) > Math.abs(facingX));
        boolean west = offX < reach && (hung > 0.5f ? side > 1.5f : Math.abs(facingX) >= Math.abs(facingY));
        return north ? northCut : west && westCut;
    }

    /** The shader's wallCut, line for line. */
    private static boolean wallCut(int marks, int wall, float hung) {
        return (marks & wall) != 0 && (hung < 0.5f || hung > 2.5f || marks < Cutaways.KEPT);
    }

    private static final String[] CUT_LINES = {
        "return mod(floor(marks / wall), 2.0) > 0.5 && (hung < 0.5 || hung > 2.5 || marks < 3.5);",
        "float past = hung > 4.5 ? 0.25 : 0.06;",
        "bool northCut = wallCut(cutMarks(vec2(floor(at.x - past), edge.y)), 1.0, hung) || wallCut(cutMarks(vec2(floor(at.x + past), edge.y)), 1.0, hung);",
        "bool westCut = wallCut(cutMarks(vec2(edge.x, floor(at.y - past))), 2.0, hung) || wallCut(cutMarks(vec2(edge.x, floor(at.y + past))), 2.0, hung);",
        "if (hung < 0.5 && abs(facing.z) > max(abs(facing.x), abs(facing.y))) {",
        "return off.y < 0.06 && northCut || off.x < 0.06 && westCut;",
        "float reach = hung > 0.5 ? 0.45 : 0.3, side = hung - 2.0 * floor((hung - 0.5) / 2.0);",
        "bool north = off.y < reach && (hung > 0.5 ? side < 1.5 : abs(facing.y) > abs(facing.x));",
        "bool west = off.x < reach && (hung > 0.5 ? side > 1.5 : abs(facing.x) >= abs(facing.y));",
        "return north ? northCut : west && westCut;"};

    private static void theTopOfAWallIsCutWithItsWallWhicheverWayItLeans() {
        String shader = Cutaways.GLSL;
        for (String line : CUT_LINES) {
            check(shader.contains(line), "the world shader's cutAway is the rule this test holds: " + line);
        }
        // A low north wall along y = 5240 from x 6480 to 6488, cut, and a low west fence along x = 6475 from y 5240 to 5248, cut.
        BiFunction<Integer, Integer, Integer> marked = (x, y) -> (y == 5240 && x >= 6480 && x < 6488 ? Cutaways.NORTH : 0)
                | (x == 6475 && y >= 5240 && y < 5248 ? Cutaways.WEST : 0);
        check(cut(6482.5f, 5240.04f, 0f, 1f, 0f, 0f, marked), "a cut north wall's own side is cut");
        check(cut(6474.96f, 5242.5f, 1f, 0f, 0f, 0f, marked), "a cut west fence's own side is cut");
        for (float along = 6480.02f; along < 6488f; along += 0.125f) {
            check(cut(along, 5240.03f, 0f, 0f, 1f, 0f, marked), "the level top of a cut north wall is cut all along it, joints included, at x " + along);
            check(cut(along, 5239.97f, 0.11f, 0f, 1f, 0f, marked), "and its top falling to the east, at x " + along);
        }
        for (float along = 5240.02f; along < 5248f; along += 0.125f) {
            check(cut(6475.03f, along, 0f, 0f, 1f, 0f, marked), "the level top of a cut west fence is cut all along it, at y " + along);
            check(cut(6474.97f, along, 0f, 0.11f, 1f, 0f, marked), "and its top falling to the south, at y " + along);
        }
        check(!cut(6482.5f, 5240.2f, 0f, 0f, 1f, 0f, marked), "a counter's top a fifth of a tile from a cut wall is no part of the wall");
        check(!cut(6482.5f, 5240.5f, 0f, 0f, 1f, 0f, marked), "a floor in the middle of a square is no wall's");
        check(!cut(6490.5f, 5240.03f, 0f, 0f, 1f, 0f, marked), "the top of a north wall the game has not cut stands");
    }

    private static void whatHangsOnACutWallIsCutWithItToItsEnds() {
        String shader = PipelineShaders.WORLD_FRAGMENT_PARTS + PipelineShaders.WORLD_FRAGMENT_MAIN;
        check(shader.contains("cutAway(vWorld, vNormal, ofModel ? vHung : 0.0)"), "a model is cut by the wall it hangs on");
        check(PipelineShaders.WORLD_VERTEX.contains("(abs(back.y) > abs(back.x) ? 1.0 : 2.0)"), "the wall a model hangs on is the one at its back");
        // A shelf on a cut west wall along x = 11613, its brackets 0.12 from the ends of its square, whose north wall stands.
        BiFunction<Integer, Integer, Integer> marked = (x, y) -> x == 11613 && y >= 9842 && y < 9850 ? Cutaways.WEST : 0;
        for (float along = 9843.02f; along < 9844f; along += 0.05f) {
            for (float out = 0.02f; out < 0.3f; out += 0.04f) {
                check(cut(11613f + out, along, 0f, 0f, 1f, 2f, marked), "a shelf on a cut west wall is cut at " + out + " out, " + along);
            }
        }
        check(!cut(11613.2f, 9843.1f, 0f, 0f, 1f, 1f, marked), "what hangs on a north wall that stands is not cut by the cut west wall beside it");
        check(!cut(11613.7f, 9843.5f, 0f, 0f, 1f, 2f, marked), "nor is what hangs from a ceiling well clear of the wall");
    }

    private static void onlyTheTwoWallMarksAreKept() {
        float[] rows = new float[Cutaways.FLOATS];
        Cutaways.clear(rows);
        Cutaways.put(rows, 3, 2, 0xff, false);
        check(Cutaways.at(rows, 3, 2) == (Cutaways.NORTH | Cutaways.WEST), "a mark the game keeps besides the two is left out");
        check(Cutaways.at(rows, 4, 2) == 0 && Cutaways.at(rows, 2, 2) == 0, "and does not run over into the squares beside it");
    }

    private static void whatHangsStaysWhereTheGameKeepsIt() {
        check(!Cutaways.kept(Cutaways.WEST, false, false, true), "a thing on a cut west wall of another room than the viewer's goes");
        check(Cutaways.kept(Cutaways.WEST, true, false, true), "in the viewer's own room it stays");
        check(Cutaways.kept(Cutaways.WEST, false, true, false), "and where only the north wall's things may be cut, a cut west wall's stay");
        check(!Cutaways.kept(Cutaways.NORTH | Cutaways.WEST, false, true, false), "but a cut north wall's go");
    }

    private static void aBoardOnAWallOutdoorsStandsForAViewerOutdoors() {
        // The kiosk at 7004, 6039..6040: a west wall the game cuts, out of doors, as the viewer is.
        int marks = Cutaways.WEST | (Cutaways.kept(Cutaways.WEST, true, false, true) ? Cutaways.KEPT : 0);
        BiFunction<Integer, Integer, Integer> marked = (x, y) -> x == 7004 && (y == 6039 || y == 6040) ? marks : 0;
        check(cut(7004.03f, 6039.5f, 1f, 0f, 0f, 0f, marked), "the wall is cut to its low course");
        check(!cut(7004.08f, 6039.5f, 1f, 0f, 0f, 2f, marked), "the board hung on it stands");
        check(!cut(7004.3f, 6040.2f, 0f, 0f, 1f, 2f, marked), "and so does the hood over it");
        check(cut(7004.12f, 6039.5f, 0.3f, 0.2f, 0.9f, 4f, marked), "but a log laid as the wall's face goes with the wall, whichever way its face turns");
        check(cut(7004.1f, 6041.2f, 0.3f, 0.2f, 0.9f, 6f, marked), "and a log end run on past the wall's end goes with the wall it runs on from");
        check(!cut(7004.1f, 6041.2f, 0.3f, 0.2f, 0.9f, 4f, marked), "where a log of a wall's face that far over the next square goes by that square");
    }
}

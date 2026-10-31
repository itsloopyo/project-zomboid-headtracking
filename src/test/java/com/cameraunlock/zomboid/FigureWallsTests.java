package com.cameraunlock.zomboid;

import java.util.Set;

/** Tests of where a figure is cut at the solid edges by it: FigureWalls' runs, the planes the draw takes and the matrix from the eye. */
final class FigureWallsTests {
    private FigureWallsTests() {}

    static void run() {
        Checks.run("aZombiePressedToAWallIsCutOnlyPastIt", FigureWallsTests::aZombiePressedToAWallIsCutOnlyPastIt);
        Checks.run("aRunOfWallCutsHalfASquarePastTheFigureSquareAndNoFurther", FigureWallsTests::aRunOfWallCutsHalfASquarePastTheFigureSquareAndNoFurther);
        Checks.run("anOpenDoorwayIsLeftWholeAndTheWallBesideItCut", FigureWallsTests::anOpenDoorwayIsLeftWholeAndTheWallBesideItCut);
        Checks.run("aCornerCutsBothWays", FigureWallsTests::aCornerCutsBothWays);
        Checks.run("noSolidEdgeNoCut", FigureWallsTests::noSolidEdgeNoCut);
        Checks.run("theMatrixFromTheEyeGivesPlacesFromWhereTheFigureStands", FigureWallsTests::theMatrixFromTheEyeGivesPlacesFromWhereTheFigureStands);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** Edges given as "x,y,N" or "x,y,W" for the north or west edge of the square at (x, y). */
    private static FigureWalls.EdgeSolid edges(String... solid) {
        Set<String> set = Set.of(solid);
        return (x, y, north) -> set.contains(x + "," + y + (north ? ",N" : ",W"));
    }

    /** Whether a place in the world is cut away from a figure standing at (x, y) by these edges. */
    private static boolean cut(FigureWalls.EdgeSolid edges, float x, float y, float atX, float atY) {
        FigureWalls.Cuts cuts = new FigureWalls.Cuts();
        cuts.count = FigureWalls.cuts(x, y, edges, cuts.cuts);
        float[] planes = new float[FigureWalls.MOST * FigureWalls.PLANE_FLOATS];
        FigureWalls.planes(cuts, x, y, planes);
        return FigureWalls.cut(planes, cuts.count, atX - x, atY - y);
    }

    /** A wall on the north edge of square (100, 51): the south edge of (100, 50), where the zombie stands 0.2 from it. */
    private static void aZombiePressedToAWallIsCutOnlyPastIt() {
        FigureWalls.EdgeSolid wall = edges("100,51,N");
        float x = 100.5f, y = 50.8f;
        check(cut(wall, x, y, 100.5f, 51.1f), "a shoulder past the wall is cut");
        check(cut(wall, x, y, 100.05f, 51.4f), "and a hand reaching further past it");
        check(!cut(wall, x, y, 100.5f, 50.95f), "what is on the zombie's side of the wall is kept");
        check(!cut(wall, x, y, 101.2f, 51.2f), "and what reaches past the wall's end, round it");
        check(!cut(wall, 100.5f, 51.3f, 100.5f, 51.1f), "a figure on the other side keeps what is on its side");
        check(cut(wall, 100.5f, 51.3f, 100.5f, 50.9f), "and has cut what reaches back through");
    }

    private static void aRunOfWallCutsHalfASquarePastTheFigureSquareAndNoFurther() {
        FigureWalls.EdgeSolid run = edges("99,51,N", "100,51,N", "101,51,N", "102,51,N");
        float x = 100.5f, y = 50.8f;
        check(cut(run, x, y, 101.4f, 51.2f), "the next square's wall cuts what reaches past this square's end");
        check(cut(run, x, y, 99.6f, 51.2f), "either way");
        check(!cut(run, x, y, 101.6f, 51.2f), "but no further than half a square: " + FigureWalls.REACH);
        FigureWalls.Cuts cuts = new FigureWalls.Cuts();
        check(FigureWalls.cuts(x, y, run, cuts.cuts) == 1, "and the run is one cut");
    }

    private static void anOpenDoorwayIsLeftWholeAndTheWallBesideItCut() {
        FigureWalls.EdgeSolid beside = edges("99,51,N", "101,51,N");
        float x = 100.5f, y = 50.9f;
        check(!cut(beside, x, y, 100.5f, 51.3f), "an arm through the open doorway is kept");
        check(cut(beside, x, y, 101.2f, 51.2f), "and one through the wall beside it is cut");
        check(cut(beside, x, y, 99.8f, 51.2f), "on either side");
        FigureWalls.Cuts cuts = new FigureWalls.Cuts();
        check(FigureWalls.cuts(x, y, beside, cuts.cuts) == 2, "two runs, one each side of the doorway");
    }

    private static void aCornerCutsBothWays() {
        FigureWalls.EdgeSolid corner = edges("100,50,N", "100,50,W");
        float x = 100.2f, y = 50.2f;
        check(cut(corner, x, y, 100.5f, 49.9f), "past the north wall");
        check(cut(corner, x, y, 99.9f, 50.5f), "past the west wall");
        check(!cut(corner, x, y, 100.5f, 50.5f), "and nothing in the room");
    }

    private static void noSolidEdgeNoCut() {
        FigureWalls.Cuts cuts = new FigureWalls.Cuts();
        check(FigureWalls.cuts(100.5f, 50.5f, edges(), cuts.cuts) == 0, "a figure in the open has no cut");
        check(FigureWalls.cuts(100.5f, 50.5f, edges("103,50,N", "100,53,W"), cuts.cuts) == 0, "nor one whose walls are squares off");
    }

    /** A view turned and moved, through which a place far out on the map is seen counted from the eye's place; fromEye takes it back to the figure's. */
    private static void theMatrixFromTheEyeGivesPlacesFromWhereTheFigureStands() {
        double yaw = 0.7, pitch = -0.3;
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw), cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        // Rows: right, up, back, as a view's are; by columns.
        float[] right = {cy, sy, 0f}, up = {-sy * sp, cy * sp, cp}, back = {sy * cp, -cy * cp, sp};
        float[] view = new float[16];
        for (int i = 0; i < 3; i++) {
            view[i * 4] = right[i];
            view[i * 4 + 1] = up[i];
            view[i * 4 + 2] = back[i];
        }
        view[12] = 0.3f;
        view[13] = -0.2f;
        view[14] = 0.1f;
        view[15] = 1f;
        float fromX = 10806.25f, fromY = 9781.5f, fromUp = 1.6f, x = 10806.5f, y = 9778.8f, stands = 0.02f;
        float[] place = {10806.9f, 9779.3f, 1.2f};
        double[] seen = new double[3];
        for (int row = 0; row < 3; row++) {
            seen[row] = view[row] * (place[0] - fromX) + view[4 + row] * (place[1] - fromY) + view[8 + row] * (place[2] - fromUp) + view[12 + row];
        }
        float[] back2 = new float[16];
        FigureWalls.fromEye(view, fromX, fromY, fromUp, x, y, stands, back2);
        float[] got = new float[3];
        for (int row = 0; row < 3; row++) {
            got[row] = (float) (back2[row] * seen[0] + back2[4 + row] * seen[1] + back2[8 + row] * seen[2] + back2[12 + row]);
        }
        float[] want = {place[0] - x, place[1] - y, place[2] - stands};
        for (int i = 0; i < 3; i++) {
            check(Math.abs(got[i] - want[i]) < 1e-4f, "axis " + i + ": " + got[i] + " against " + want[i]);
        }
        check(back2[3] == 0f && back2[7] == 0f && back2[11] == 0f && back2[15] == 1f, "and the matrix only turns and moves");
    }
}

package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * What goes with the holes shot through a thing (Breakage): what is left with nothing to hold it
 * falls out, a board shot most of the way through breaks across, and what is still held stays.
 */
final class BreakageTests {
    private BreakageTests() {}

    static void run() {
        Checks.run("aPieceRingedByHolesFallsOutOfASheet", BreakageTests::aPieceRingedByHolesFallsOutOfASheet);
        Checks.run("oneHoleInASheetTakesNothingFarFromIt", BreakageTests::oneHoleInASheetTakesNothingFarFromIt);
        Checks.run("aBoardShotMostOfTheWayThroughBreaksAcross", BreakageTests::aBoardShotMostOfTheWayThroughBreaksAcross);
        Checks.run("aLengthOfBoardBrokenAtBothEndsFallsOut", BreakageTests::aLengthOfBoardBrokenAtBothEndsFallsOut);
        Checks.run("whatGoesIsTheSameEveryTime", BreakageTests::whatGoesIsTheSameEveryTime);
        Checks.run("pastTheMostHolesOneTheOthersCoverGoesFirst", BreakageTests::pastTheMostHolesOneTheOthersCoverGoesFirst);
        Checks.run("whatWentIsCutOutOfAFenceThatHasBeenShotToPieces", BreakageTests::whatWentIsCutOutOfAFenceThatHasBeenShotToPieces);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static final Breakage.Panel SHEET = new Breakage.Panel(Breakage.SHEET, 0f, 1f, 0f, 1f, 10816);
    /** A fence 0.6 of a level high whose edge starts at a whole number of boards along the map: its fourth board runs from 0.45 to 0.6. */
    private static final Breakage.Panel FENCE = new Breakage.Panel(Breakage.BOARDS, 0f, 1f, 0f, 0.6f, 0);

    private static float levels(float tiles) {
        return tiles / ViewCamera.LEVEL_HEIGHT;
    }

    private static void aPieceRingedByHolesFallsOutOfASheet() {
        float[] entries = new float[0];
        float middleUp = levels(1.2f);
        for (int i = 0; i < 12; i++) {
            double angle = 2 * Math.PI * i / 12;
            entries = BlastHoles.with(entries, true, 0.5f + 0.25f * (float) Math.cos(angle), middleUp + levels(0.25f * (float) Math.sin(angle)), 0.08f, 100 + i, 0);
        }
        Breakage.Pieces pieces = Breakage.work(entries, true, SHEET);
        check(pieces.inside(0.5f, middleUp), "the piece inside the ring of holes falls out");
        check(pieces.inside(0.42f, middleUp + levels(0.05f)), "all of it");
        check(!pieces.inside(0.5f, levels(0.3f)) && !pieces.inside(0.08f, middleUp) && !pieces.inside(0.92f, levels(2f)),
                "what is outside the ring stays");
        check(Breakage.work(entries, false, SHEET).rectangles.length == 0, "the holes of one edge leave the other edge whole");
    }

    private static void oneHoleInASheetTakesNothingFarFromIt() {
        float[] entries = BlastHoles.with(new float[0], true, 0.5f, levels(1.2f), 0.1f, 7, 0);
        Breakage.Pieces pieces = Breakage.work(entries, true, SHEET);
        float near = 0.1f * 1.25f * 1.3f + Breakage.BRIDGE * Breakage.CELL;
        for (float[] r : pieces.rectangles) {
            check(r[0] >= 0.5f - near && r[2] <= 0.5f + near && r[1] * ViewCamera.LEVEL_HEIGHT >= 1.2f - near && r[5] * ViewCamera.LEVEL_HEIGHT <= 1.2f + near,
                    "only crumbs at the hole's ragged rim go with one hole, at " + Arrays.toString(r));
        }
    }

    private static void aBoardShotMostOfTheWayThroughBreaksAcross() {
        float up = levels(0.7f);
        float[] entries = BlastHoles.with(new float[0], true, 0.525f, up, 0.07f, 11, 0);
        Breakage.Pieces pieces = Breakage.work(entries, true, FENCE);
        boolean across = true;
        for (float along = 0.451f; along < 0.6f; along += 0.005f) {
            across &= pieces.inside(along, up) || BlastHoles.inAHole(entries, true, along, up);
        }
        check(across && pieces.inside(0.452f, up), "the board the hole took most of breaks right across beside it");
        check(!pieces.inside(0.40f, up) && !pieces.inside(0.65f, up), "the boards either side, which the hole did not reach, stand");
        check(!pieces.inside(0.525f, levels(0.2f)) && !pieces.inside(0.525f, levels(1.3f)), "and the broken board stands on below and above the break, held at its ends");
    }

    private static void aLengthOfBoardBrokenAtBothEndsFallsOut() {
        float low = levels(0.45f), high = levels(1.0f);
        float[] entries = BlastHoles.with(new float[0], true, 0.525f, low, 0.07f, 21, 0);
        entries = BlastHoles.with(entries, true, 0.525f, high, 0.07f, 22, 0);
        Breakage.Pieces pieces = Breakage.work(entries, true, FENCE);
        float between = (low + high) / 2f;
        check(pieces.inside(0.525f, between) && pieces.inside(0.46f, between), "the length between the two breaks falls out");
        check(!pieces.inside(0.375f, between) && !pieces.inside(0.675f, between), "the boards beside it stand");
    }

    private static void whatGoesIsTheSameEveryTime() {
        float[] entries = BlastHoles.with(new float[0], true, 0.525f, levels(0.45f), 0.06f, 21, 0);
        entries = BlastHoles.with(entries, true, 0.33f, levels(1.0f), 0.07f, 23, 0);
        float[][] once = Breakage.work(entries, true, FENCE).rectangles, again = Breakage.work(entries.clone(), true, FENCE).rectangles;
        check(Arrays.deepEquals(once, again), "the same holes break a fence the same way on every load and on every thread");
        check(Breakage.of(entries, true, FENCE) == Breakage.of(entries, true, FENCE), "and it is worked out once for a list of holes");
    }

    private static void pastTheMostHolesOneTheOthersCoverGoesFirst() {
        float[] entries = BlastHoles.with(new float[0], true, 5f, 0.4f, 0.05f, 1, 0);
        entries = BlastHoles.with(entries, true, 0.5f, 0.4f, 0.03f, 2, 0);
        entries = BlastHoles.with(entries, true, 0.5f, 0.4f, 0.14f, 3, 0);
        for (int i = 3; i < BlastHoles.MOST_HOLES; i++) {
            entries = BlastHoles.with(entries, true, 10f + i, 0.4f, 0.05f, 100 + i, 0);
        }
        entries = BlastHoles.with(entries, true, 50f, 0.4f, 0.05f, 999, 0);
        boolean oldest = false, covered = false;
        for (int at = 0; at < entries.length; at += BlastHoles.FLOATS) {
            oldest |= entries[at + BlastHoles.SEED] == 1f;
            covered |= entries[at + BlastHoles.SEED] == 2f;
        }
        check(entries.length == BlastHoles.MOST_HOLES * BlastHoles.FLOATS, "an object keeps the most holes");
        check(oldest && !covered, "the hole inside a bigger one went, and the oldest stayed");
    }

    /**
     * Fences shot to pieces are cut, both skins, without a throw: the ring and the spiral the rig
     * shot at 10793 and 10795, 9779 (the first threw where two broken pieces met on one line), and
     * blasts laid at random over a fence of boards and a sheet.
     */
    private static void whatWentIsCutOutOfAFenceThatHasBeenShotToPieces() {
        float[] ring = new float[0];
        for (int i = 0; i < 12; i++) {
            double angle = 2 * Math.PI * i / 12;
            ring = BlastHoles.with(ring, true, 0.5f + 0.25f * (float) Math.cos(angle), 0.4f + 0.25f * (float) Math.sin(angle) / ViewCamera.LEVEL_HEIGHT, 0.08f, 100 + i, 0);
        }
        float[] spiral = new float[0];
        double turn = 0;
        for (int k = 0; 0.035 * turn <= 0.32; k++) {
            double r = 0.035 * turn;
            spiral = BlastHoles.with(spiral, true, 0.5f + (float) (r * Math.cos(turn)), 0.4f + (float) (r * Math.sin(turn)) / ViewCamera.LEVEL_HEIGHT, 0.09f, 300 + k, 0);
            turn += 0.11 / Math.max(r, 0.11);
        }
        check(cutThrough(ring, new Breakage.Panel(Breakage.BOARDS, 0f, 1f, 0f, 1f, 10793)) == null, "the ring of holes is cut");
        check(cutThrough(spiral, new Breakage.Panel(Breakage.BOARDS, 0f, 1f, 0f, 1f, 10795)) == null, "the spiral of holes is cut");
        java.util.Random random = new java.util.Random(77);
        int threw = 0;
        String first = null;
        for (int run = 0; run < 400; run++) {
            float[] entries = new float[0];
            int count = 3 + random.nextInt(20);
            for (int h = 0; h < count; h++) {
                entries = BlastHoles.with(entries, true, random.nextFloat(), random.nextFloat() * 0.8f, 0.04f + random.nextFloat() * 0.1f, random.nextInt(1 << 20), 0);
            }
            Breakage.Panel panel = run % 2 == 0 ? new Breakage.Panel(Breakage.BOARDS, 0f, 1f, 0f, 0.6f + random.nextFloat() * 0.4f, random.nextInt(20000))
                    : new Breakage.Panel(Breakage.SHEET, 0f, 1f, 0f, 1f, random.nextInt(20000));
            String failed = cutThrough(entries, panel);
            if (failed != null) {
                threw++;
                first = first == null ? "run " + run + ": " + failed : first;
            }
        }
        check(threw == 0, threw + " of 400 shot up fences threw, the first " + first);
    }

    /** Cuts the holes and what went with them out of the panel built as two skins of a north edge, as WallBuilder lays a fence: what threw, or null. */
    private static String cutThrough(float[] entries, Breakage.Panel panel) {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        float top = panel.upTo() * ViewCamera.LEVEL_HEIGHT;
        for (float across : new float[] {-0.02f, 0.02f}) {
            float[][] corners = {{0f, 0f}, {1f, 0f}, {1f, top}, {0f, 0f}, {1f, top}, {0f, top}};
            for (float[] c : corners) {
                float[] v = new float[SpriteGeometry.VERTEX_FLOATS];
                v[0] = c[0];
                v[1] = across;
                v[2] = c[1];
                v[5] = 1f;
                v[8] = 3f;
                mesh.addVertex(v);
            }
        }
        MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.08f);
        BlastHoles.into(cut, entries, panel, true, 0f, 1f, 0f, ViewCamera.LEVEL_HEIGHT, 1);
        try {
            cut.cut(mesh, 0);
            cut.finish();
            return null;
        } catch (IllegalStateException e) {
            return e.getMessage() + (e.getCause() == null ? "" : " / " + e.getCause().getMessage());
        }
    }
}

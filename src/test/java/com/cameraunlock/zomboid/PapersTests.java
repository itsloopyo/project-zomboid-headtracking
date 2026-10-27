package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** A west wall's paper in its north sheetmate's paint: Papers.toned. */
final class PapersTests {
    private PapersTests() {}

    static void run() {
        Checks.run("aWestPaperTakesItsNorthMatesPaint", PapersTests::aWestPaperTakesItsNorthMatesPaint);
        Checks.run("aWestPaperKeepsItsStripes", PapersTests::aWestPaperKeepsItsStripes);
    }

    private static Papers.Paper paper(boolean north, float[] ends, float... colours) {
        Papers.Paper paper = new Papers.Paper();
        paper.north = north;
        paper.ends = ends;
        paper.colours = colours;
        float from = 0f;
        for (int stripe = 0; stripe < ends.length; stripe++) {
            float share = ends[stripe] - from;
            from = ends[stripe];
            paper.red += colours[stripe * 3] * share;
            paper.green += colours[stripe * 3 + 1] * share;
            paper.blue += colours[stripe * 3 + 2] * share;
        }
        return paper;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    /** walls_interior_house_03_16 and 03_17, one pale green paper the game paints 154 on its west sprite and 198 on its north. */
    private static void aWestPaperTakesItsNorthMatesPaint() {
        Papers.Paper west = paper(false, new float[] {1f}, 154f / 255f, 157f / 255f, 150f / 255f);
        Papers.Paper north = paper(true, new float[] {1f}, 198f / 255f, 201f / 255f, 192f / 255f);
        Papers.Paper toned = Papers.toned(west, north);
        check(near(toned.colours[0], 198f / 255f) && near(toned.colours[1], 201f / 255f) && near(toned.colours[2], 192f / 255f),
                "a one colour west paper is painted as its north mate: " + toned.colours[0] * 255f + ", " + toned.colours[1] * 255f + ", " + toned.colours[2] * 255f);
        check(near(toned.red, north.red) && near(toned.green, north.green) && near(toned.blue, north.blue), "and its whole is the north mate's");
        check(!toned.north && near(west.colours[0], 154f / 255f), "it is still a west paper, and the paper read off the sprite is left as it was");
    }

    private static void aWestPaperKeepsItsStripes() {
        Papers.Paper west = paper(false, new float[] {0.5f, 1f}, 0.4f, 0.4f, 0.4f, 0.6f, 0.6f, 0.6f);
        Papers.Paper north = paper(true, new float[] {0.25f, 1f}, 0.7f, 0.7f, 0.7f, 0.5f, 0.5f, 0.5f);
        Papers.Paper toned = Papers.toned(west, north);
        float by = north.red / west.red;
        check(toned.ends.length == 2 && near(toned.ends[0], 0.5f), "a striped west paper keeps where its own stripes end");
        check(near(toned.colours[0], 0.4f * by) && near(toned.colours[3], 0.6f * by), "each stripe lit by as much as the whole: " + toned.colours[0] + ", " + toned.colours[3]);
        Papers.Paper bright = Papers.toned(paper(false, new float[] {1f}, 0.9f, 0.5f, 0.5f), paper(true, new float[] {1f}, 1f, 1f, 1f));
        check(bright.colours[0] <= 1f && bright.colours[1] <= 1f, "and no stripe is painted brighter than white");
    }
}

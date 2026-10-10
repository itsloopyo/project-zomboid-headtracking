package com.cameraunlock.zomboid;

/** Tests of how the pieces of a tent or a market stall's canopy are laid: see Tents. */
final class TentTests {
    private TentTests() {}

    static void run() {
        Checks.run("everyTentPieceTheMapPlacesIsLaid", TentTests::everyTentPieceTheMapPlacesIsLaid);
        Checks.run("aCanopysSquaresMeetAtTheirEdgesFromEaveToRidgeToEave", TentTests::aCanopysSquaresMeetAtTheirEdgesFromEaveToRidgeToEave);
        Checks.run("aGableReachesTheRidgeAndAStripCarriesTheSlopeOn", TentTests::aGableReachesTheRidgeAndAStripCarriesTheSlopeOn);
        Checks.run("aRopeRunsFromUnderTheEavesToTheGroundOutside", TentTests::aRopeRunsFromUnderTheEavesToTheGroundOutside);
        Checks.run("anotherSpriteIsNoTent", TentTests::anotherSpriteIsNoTent);
        Checks.run("theCanvasIsTheCeilingUnderEverySquareOfACanopy", TentTests::theCanvasIsTheCeilingUnderEverySquareOfACanopy);
        Checks.run("aGableReachesTheCanvasOverItAndNoFurther", TentTests::aGableReachesTheCanvasOverItAndNoFurther);
        Checks.run("aGablesLiningClosesItInsideUpToTheCanvas", TentTests::aGablesLiningClosesItInsideUpToTheCanvas);
    }

    private static final String SHEET = "location_military_tent_01_";

    /** Every piece of canopy, gable, strip and rope the shipped map places, by its place on the sheet (scan of 2026-10-09). */
    private static final int[] PLACED = {
        16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45,
        48, 49, 50, 51, 52, 53,
        80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107,
        108, 109, 112, 113, 114, 115, 116, 117,
    };

    private static void everyTentPieceTheMapPlacesIsLaid() {
        for (int place : PLACED) {
            String name = SHEET + place;
            check(Tents.of(name) != null || Tents.ropeOf(name) != null, name + " is laid");
        }
    }

    /** The rows of squares one canopy is made of, across the way its ridge runs, as the map places them. */
    private static final int[][] ROWS = {
        {16, 22, 23, 21}, {16, 17, 18, 19, 20, 21}, {29, 28, 25, 24}, {29, 28, 27, 26, 25, 24}, {29, 31, 30, 24},
        {80, 86, 87, 85}, {80, 81, 82, 83, 84, 85}, {93, 92, 89, 88}, {93, 92, 91, 90, 89, 88},
    };

    private static void aCanopysSquaresMeetAtTheirEdgesFromEaveToRidgeToEave() {
        for (int[] row : ROWS) {
            String said = java.util.Arrays.toString(row);
            Tents.Piece first = Tents.of(SHEET + row[0]);
            check(first.kind() == Tents.CANOPY, said + " is canopy");
            check(Math.abs(first.from()) < 1e-6f, said + " starts at the eaves");
            float highest = 0f;
            for (int i = 0; i < row.length; i++) {
                Tents.Piece piece = Tents.of(SHEET + row[i]);
                check(piece.kind() == Tents.CANOPY && piece.alongX() == first.alongX(), said + " runs one way");
                check(Math.abs(Math.abs(piece.to() - piece.from()) - Tents.STEP) < 1e-6f, said + " climbs a step a square");
                if (i > 0) {
                    check(Math.abs(Tents.of(SHEET + row[i - 1]).to() - piece.from()) < 1e-6f, said + " meets at the edge after square " + (i - 1));
                }
                highest = Math.max(highest, piece.to());
            }
            check(Math.abs(Tents.of(SHEET + row[row.length - 1]).to()) < 1e-6f, said + " ends at the eaves");
            check(Math.abs(highest - row.length / 2 * Tents.STEP) < 1e-6f, said + " has its ridge in the middle");
        }
    }

    private static void aGableReachesTheRidgeAndAStripCarriesTheSlopeOn() {
        check(Tents.of(SHEET + 48).kind() == Tents.GABLE && Tents.of(SHEET + 48).alongX(), "48 is a gable on a north edge");
        check(Tents.of(SHEET + 40).kind() == Tents.GABLE && !Tents.of(SHEET + 40).alongX(), "40 is a gable on a west edge");
        check(Tents.of(SHEET + 32).kind() == Tents.FLAP && !Tents.of(SHEET + 32).alongX(), "32 is a strip on a west edge");
        check(Tents.of(SHEET + 39).kind() == Tents.FLAP && Tents.of(SHEET + 39).alongX(), "39 is a strip on a north edge");
        check(Tents.FLAP_OUT > 0f && Tents.FLAP_OUT < 0.5f, "the strip goes a little past the eaves");
    }

    private static void aRopeRunsFromUnderTheEavesToTheGroundOutside() {
        for (int place : new int[] {33, 35, 36, 38, 97, 99, 100, 102}) {
            Tents.Piece rope = Tents.ropeOf(SHEET + place);
            check(rope != null && rope.kind() == Tents.ROPE, place + " is a rope");
            check(rope.topH() > 0.8f && rope.topH() < 1f, place + " is made fast just under the eaves");
            // The eaves are on the square's west or north edge, and the rope's peg is out from there.
            check(rope.alongX() ? rope.footB() > 0.5f : rope.footA() > 0.5f, place + " is pegged out from the tent");
        }
        check(Tents.ropeOf(SHEET + 32) == null, "a strip alone has no rope");
        check(Tents.of(SHEET + 35) == null, "a rope alone has no cloth");
    }

    private static void anotherSpriteIsNoTent() {
        for (String name : new String[] {SHEET + 0, SHEET + 3, SHEET + 56, SHEET + 64, SHEET + 128, SHEET + "x", SHEET, "roofs_shallow_01_24", "location_military_tent_02_16", null}) {
            check(Tents.of(name) == null && Tents.ropeOf(name) == null, name + " is no tent's piece");
        }
    }

    /**
     * A square holding canopy has no flat ceiling under it, the mod's plaster or the map's
     * ceilings_01 laid as a floor: the stall at the Riverside market (6472, 5210) and the camp's
     * tent (12472, 4217), whose level 1 holds ceilings_01_0 beside the canvas. A gable, a strip
     * and the ceiling sprite alone are no canvas, and a house's ceiling stays.
     */
    private static void theCanvasIsTheCeilingUnderEverySquareOfACanopy() {
        for (int place : new int[] {16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 80, 86, 87, 85, 93, 88}) {
            check(Tents.isCanvas(sprite(SHEET + place)), place + " is canvas");
        }
        for (String name : new String[] {SHEET + 48, SHEET + 40, SHEET + 32, SHEET + 35, SHEET + 0, "ceilings_01_0", "roofs_shallow_01_24"}) {
            check(!Tents.isCanvas(sprite(name)), name + " is no canvas");
        }
    }

    /**
     * Each gable is as high as the canopy over its edge, square by square, so the wall under
     * the end of a tent meets the canvas with no sky between: the gables of each row of canopy
     * as the map places them, a north edge's under 16..21 and a west edge's under 29..24.
     */
    private static void aGableReachesTheCanvasOverItAndNoFurther() {
        int[][] under = {
            {16, 48}, {22, 49}, {23, 52}, {21, 53}, {17, 49}, {18, 50}, {19, 51}, {20, 52},
            {29, 45}, {28, 44}, {31, 44}, {25, 41}, {30, 41}, {24, 40}, {27, 43}, {26, 42},
            {80, 112}, {86, 113}, {87, 116}, {85, 117}, {93, 109}, {88, 104},
        };
        for (int[] pair : under) {
            Tents.Piece canvas = Tents.of(SHEET + pair[0]), gable = Tents.of(SHEET + pair[1]);
            check(gable.kind() == Tents.GABLE && gable.alongX() == canvas.alongX(), pair[1] + " is a gable along " + pair[0] + "'s slope");
            check(Math.abs(gable.from() - canvas.from()) < 1e-6f && Math.abs(gable.to() - canvas.to()) < 1e-6f,
                    pair[1] + " meets the canvas of " + pair[0] + " at both ends: " + gable.from() + ".." + gable.to());
        }
        check(Tents.DRAWN_IN > 0f, "the canvas's texels are taken from inside the edges its sprites paint short of");
    }

    /**
     * Inside a tent the end wall meets the canvas with no sky between: each gable's lining runs
     * the whole edge, stands just inside the tent (on the gable's square where the canvas is
     * on it, across the edge where it is not) and reaches just over the canopy's line, so it
     * meets the canvas's underside inside the canvas's face. Not further: a lining that stood
     * out over the canvas would show from outside.
     */
    private static void aGablesLiningClosesItInsideUpToTheCanvas() {
        float[] corners = new float[12];
        for (int place : new int[] {48, 49, 50, 51, 52, 53, 40, 41, 42, 43, 44, 45, 112, 117, 104, 109}) {
            Tents.Piece gable = Tents.of(SHEET + place);
            for (boolean canvasHere : new boolean[] {true, false}) {
                Tents.lining(gable, canvasHere, corners);
                String said = place + (canvasHere ? " with its canvas on its square" : " at the far end of its canvas");
                boolean north = gable.alongX();
                float across = north ? corners[1] : corners[0], length = north ? corners[3] - corners[0] : corners[4] - corners[1];
                check(Math.abs(length - 1f) < 1e-6f, said + ": lined the whole edge");
                check(canvasHere ? across > 0f && across < 0.05f : across < 0f && across > -0.05f, said + ": lined just inside the tent, " + across);
                check(corners[2] == 0f && corners[5] == 0f, said + ": lined from its foot");
                float overFrom = corners[11] - gable.from(), overTo = corners[8] - gable.to();
                check(overFrom > 0f && overFrom < 0.01f && overTo > 0f && overTo < 0.01f, said + ": lined up to just over the canvas, " + overFrom + " " + overTo);
            }
        }
    }

    private static zombie.iso.sprite.IsoSprite sprite(String name) {
        return new zombie.iso.sprite.IsoSprite(name, new zombie.core.properties.PropertyContainer(new java.util.HashMap<>()));
    }

    private static void check(boolean ok, String what) {
        Checks.check(ok, what);
    }
}

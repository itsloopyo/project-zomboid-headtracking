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
        check(Tents.GABLE_HIGH >= 3 * Tents.STEP - 1e-6f, "a gable reaches the ridge of the widest tent");
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

    private static void check(boolean ok, String what) {
        Checks.check(ok, what);
    }
}

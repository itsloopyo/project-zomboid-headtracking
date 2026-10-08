package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.HashMap;

/** Tests of what counts as one made thing, and of the level ground Terrain cuts under it. */
final class FootingTests {
    private FootingTests() {}

    static void run() {
        Checks.run("aBuildingItsStairAndItsOverhangAreOneThing", FootingTests::aBuildingItsStairAndItsOverhangAreOneThing);
        Checks.run("buildingsThatShareAWallOrACornerStandOnOneFooting", FootingTests::buildingsThatShareAWallOrACornerStandOnOneFooting);
        Checks.run("aThingWithNoBuildingStandsAtTheLandUnderItsMiddle", FootingTests::aThingWithNoBuildingStandsAtTheLandUnderItsMiddle);
        Checks.run("whatIsNotLoadedLeavesAThingUnsettled", FootingTests::whatIsNotLoadedLeavesAThingUnsettled);
        Checks.run("aThingTooLargeToLookOverKeepsEachBuildingWhole", FootingTests::aThingTooLargeToLookOverKeepsEachBuildingWhole);
        Checks.run("openGroundAndWhatIsApartFromABuildingAreNotIt", FootingTests::openGroundAndWhatIsApartFromABuildingAreNotIt);
        Checks.run("theGroundUnderAStairOnAStreetIsLevelAtTheBuildingsFooting", FootingTests::theGroundUnderAStairOnAStreetIsLevelAtTheBuildingsFooting);
        Checks.run("aStreetPastAStairIsStillLevelFromKerbToKerb", FootingTests::aStreetPastAStairIsStillLevelFromKerbToKerb);
        Checks.run("theGroundUnderAnEaveOverALawnIsLevelAtTheBuildingsFooting", FootingTests::theGroundUnderAnEaveOverALawnIsLevelAtTheBuildingsFooting);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** A map drawn in rows of text: '.' open, 's' built, a letter A to Z a building, '?' not loaded. Outside it is open. */
    private static final class Plan implements Footings.Site {
        final String[] rows;
        final HashMap<Character, Long> houses = new HashMap<>();

        Plan(String... rows) {
            this.rows = rows;
            for (int y = 0; y < rows.length; y++) {
                for (int x = 0; x < rows[y].length(); x++) {
                    char c = rows[y].charAt(x);
                    if (c >= 'A' && c <= 'Z') {
                        // Any point will do for a building's middle, as long as it is the building's own.
                        houses.putIfAbsent(c, Footings.key(100f + c, 200f + c));
                    }
                }
            }
        }

        private char at(int x, int y) {
            return y < 0 || y >= rows.length || x < 0 || x >= rows[y].length() ? '.' : rows[y].charAt(x);
        }

        @Override
        public byte over(int x, int y) {
            char c = at(x, y);
            return c == '.' ? Footings.OPEN : c == 's' ? Footings.BUILT : c == '?' ? Footings.UNLOADED : Footings.HOUSE;
        }

        @Override
        public long house(int x, int y) {
            return houses.get(at(x, y));
        }
    }

    private static final Footings footings = new Footings();

    private static long found(Plan plan, int x, int y) {
        footings.find(plan, x, y);
        return footings.found;
    }

    private static void aBuildingItsStairAndItsOverhangAreOneThing() {
        // A building with a roof that overhangs its south and east walls, and a flight up its north side.
        Plan plan = new Plan(
                "..sss....",
                ".AAAAAs..",
                ".AAAAAs..",
                ".ssssss..",
                ".........");
        long house = plan.houses.get('A');
        for (int y = 0; y < plan.rows.length; y++) {
            for (int x = 0; x < plan.rows[y].length(); x++) {
                long here = found(plan, x, y);
                check(here == (plan.at(x, y) == '.' ? Footings.NONE : house), "the stair, the overhang and the building are one thing, at " + x + ", " + y);
                check(footings.settled && footings.whole, "a thing all on loaded map is settled");
                check(plan.at(x, y) == '.' || !footings.free, "what is joined to a building stands at the building's footing");
            }
        }
        footings.find(plan, 3, 0);
        check(footings.count == 3 + 6 + 6 + 6, "one search finds every square of the thing, got " + footings.count);
    }

    private static void buildingsThatShareAWallOrACornerStandOnOneFooting() {
        Plan row = new Plan(
                "AAABBBCC",
                "AAABBBCC",
                "........",
                "DD......",
                "DD......",
                "..EE....");
        long first = found(row, 0, 0);
        check(first == found(row, 4, 1) && first == found(row, 7, 0), "a row of buildings that share walls is one thing with one footing");
        check(first == row.houses.get('A') || first == row.houses.get('B') || first == row.houses.get('C'), "the row's footing is one of its own buildings'");
        long pair = found(row, 0, 3);
        check(pair == found(row, 3, 5), "buildings that touch at a corner are one thing");
        check(pair != first, "a building across open ground is another thing");
    }

    private static void aThingWithNoBuildingStandsAtTheLandUnderItsMiddle() {
        Plan canopy = new Plan(
                ".......",
                "..ssss.",
                "..ssss.",
                ".......");
        long here = found(canopy, 2, 1);
        check(footings.free, "a canopy with no building under it stands at the land's height");
        check(Footings.keyX(here) == 4f && Footings.keyY(here) == 2f, "its middle is the middle of the ground it covers, got " + Footings.keyX(here) + ", " + Footings.keyY(here));
        check(here == found(canopy, 5, 2), "every square of it has the same middle");
    }

    private static void whatIsNotLoadedLeavesAThingUnsettled() {
        Plan edge = new Plan(
                "AAA?",
                "AAA?");
        found(edge, 0, 0);
        check(!footings.settled, "a building at the edge of the loaded map may yet turn out to be joined to another");
        found(edge, 3, 0);
        check(!footings.settled && footings.found == Footings.NONE, "a square that is not loaded has no footing yet");
        Plan inland = new Plan(
                "AAA.?",
                "AAA.?");
        found(inland, 0, 0);
        check(footings.settled, "open ground between a building and the map's edge settles it");
    }

    private static void aThingTooLargeToLookOverKeepsEachBuildingWhole() {
        // Two halls joined by a covered way, more squares together than one search takes.
        int side = 80;
        check(2 * side * side > Footings.MOST && side * side < Footings.MOST, "the test's halls are too large together and not apart");
        String[] rows = new String[side];
        for (int y = 0; y < side; y++) {
            char[] row = new char[2 * side + 4];
            Arrays.fill(row, 0, side, 'A');
            Arrays.fill(row, side, side + 4, y == side / 2 ? 's' : '.');
            Arrays.fill(row, side + 4, 2 * side + 4, 'B');
            rows[y] = new String(row);
        }
        Plan halls = new Plan(rows);
        long west = halls.houses.get('A'), east = halls.houses.get('B');
        for (int[] at : new int[][] {{0, 0}, {side - 1, side - 1}, {side / 2, side / 2}, {side - 1, side / 2}}) {
            check(found(halls, at[0], at[1]) == west && !footings.whole, "every square of one hall has that hall's footing, from " + at[0] + ", " + at[1]);
        }
        check(found(halls, side + 4, 0) == east && found(halls, 2 * side + 3, side - 1) == east, "and of the other hall, the other's");
        long way = found(halls, side + 1, side / 2);
        check(way == west || way == east, "the covered way between them stands with one of the halls");

        String[] long1 = new String[3];
        char[] viaduct = new char[Footings.MOST + 10];
        Arrays.fill(viaduct, 's');
        Arrays.fill(long1, new String(viaduct));
        check(found(new Plan(long1), 5, 1) == Footings.NONE, "a viaduct with no building in it follows the land as its road does");
    }

    private static void openGroundAndWhatIsApartFromABuildingAreNotIt() {
        Plan yard = new Plan(
                "AAA....",
                "AAA..s.",
                "......."
        );
        check(found(yard, 4, 1) == Footings.NONE && footings.count == 0, "open ground has no footing");
        long shed = found(yard, 5, 1);
        check(footings.free && shed != yard.houses.get('A'), "what stands two squares from a building is not joined to it");
    }

    /** Lays the test's ground: a building south of row street, a street of six squares north of it, open ground beyond, and a flight on the street against the wall. */
    private static void layStairOnStreet(float pad, int row, int from, int to) {
        int span = Terrain.SPAN;
        Arrays.fill(Terrain.padsAround, Float.NaN);
        for (int i = 0; i < span; i++) {
            for (int j = 0; j < span; j++) {
                Terrain.around[i * span + j] = j >= row ? Terrain.MADE : j >= row - 6 ? Terrain.STREET : Terrain.NATURAL;
                if (j >= row || j == row - 1 && i >= from && i < to) {
                    Terrain.padsAround[i * span + j] = pad;
                }
            }
        }
    }

    private static void theGroundUnderAStairOnAStreetIsLevelAtTheBuildingsFooting() {
        byte[] around = Terrain.around.clone();
        float[] pads = Terrain.padsAround.clone();
        try {
            Terrain terrain = Terrain.HILLS;
            int span = Terrain.SPAN, row = span / 2 + 2, from = span / 2 - 1, to = span / 2 + 2;
            // Where the land falls steeply enough for the street to lie well off the building's footing.
            int x = 6466 - from, y = 5249 - row;
            float pad = 3.25f;
            layStairOnStreet(pad, row, from, to);
            terrain.streets(x, y);
            for (int i = from; i <= to; i++) {
                for (int j = row - 1; j <= row; j++) {
                    check(terrain.corner(x + i, y + j, i, j) == pad, "every corner of a square a flight stands on is at the footing of the building it climbs, corner " + i + ", " + j);
                }
            }
            float street = terrain.corner(x + from + 1, y + row - 4, from + 1, row - 4);
            check(Math.abs(street - pad) > 0.2f, "the test's street lies off the footing, or it shows nothing: " + street);
        } finally {
            System.arraycopy(around, 0, Terrain.around, 0, around.length);
            System.arraycopy(pads, 0, Terrain.padsAround, 0, pads.length);
        }
    }

    private static void theGroundUnderAnEaveOverALawnIsLevelAtTheBuildingsFooting() {
        byte[] around = Terrain.around.clone();
        float[] pads = Terrain.padsAround.clone();
        try {
            Terrain terrain = Terrain.HILLS;
            int span = Terrain.SPAN, row = span / 2 + 2;
            float pad = -1.5f;
            Arrays.fill(Terrain.padsAround, Float.NaN);
            for (int i = 0; i < span; i++) {
                for (int j = 0; j < span; j++) {
                    Terrain.around[i * span + j] = j >= row ? Terrain.MADE : Terrain.NATURAL;
                    if (j >= row - 1) {
                        Terrain.padsAround[i * span + j] = pad;
                    }
                }
            }
            terrain.streets(10700, 9700);
            for (int i = span / 2 - 2; i <= span / 2 + 2; i++) {
                check(terrain.corner(10700 + i, 9700 + row - 1, i, row - 1) == pad, "the lawn under an eave is at the building's footing at the eave's outer edge, corner " + i);
            }
            float out = terrain.corner(10700 + span / 2, 9700 + row - 3, span / 2, row - 3);
            check(out != pad, "the lawn two squares out from the eave is the land's own again, graded to the footing");
        } finally {
            System.arraycopy(around, 0, Terrain.around, 0, around.length);
            System.arraycopy(pads, 0, Terrain.padsAround, 0, pads.length);
        }
    }

    private static void aStreetPastAStairIsStillLevelFromKerbToKerb() {
        byte[] around = Terrain.around.clone();
        float[] pads = Terrain.padsAround.clone();
        try {
            Terrain terrain = Terrain.HILLS;
            // The flight at one end of the ground laid, and the street looked at well out of its reach.
            int span = Terrain.SPAN, row = span / 2 + 2, from = 0, to = 3, along = to + Terrain.REACH + 5;
            int x = 6466 - from, y = 5249 - row;
            layStairOnStreet(3.25f, row, from, to);
            terrain.streets(x, y);
            float near = terrain.corner(x + along, y + row - 5, along, row - 5), far = terrain.corner(x + along, y + row - 2, along, row - 2);
            check(Math.abs(near - far) < 1e-4f, "a street is level from side to side away from what stands on it: " + near + " and " + far);
        } finally {
            System.arraycopy(around, 0, Terrain.around, 0, around.length);
            System.arraycopy(pads, 0, Terrain.padsAround, 0, pads.length);
        }
    }
}

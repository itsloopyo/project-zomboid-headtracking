package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/** Tests of what counts as one made thing, and of the level ground Terrain cuts under it. */
final class FootingTests {
    private FootingTests() {}

    static void run() {
        Checks.run("aBuildingItsStairAndItsOverhangAreOneThing", FootingTests::aBuildingItsStairAndItsOverhangAreOneThing);
        Checks.run("buildingsThatShareAWallOrACornerStandOnOneFooting", FootingTests::buildingsThatShareAWallOrACornerStandOnOneFooting);
        Checks.run("aThingWithNoBuildingStandsAtTheLandUnderItsMiddle", FootingTests::aThingWithNoBuildingStandsAtTheLandUnderItsMiddle);
        Checks.run("anAnswerIsLookedForAgainOnlyOnceASquareItWentByChanges", FootingTests::anAnswerIsLookedForAgainOnlyOnceASquareItWentByChanges);
        Checks.run("aThingTooLargeToLookOverKeepsEachBuildingWhole", FootingTests::aThingTooLargeToLookOverKeepsEachBuildingWhole);
        Checks.run("openGroundAndWhatIsApartFromABuildingAreNotIt", FootingTests::openGroundAndWhatIsApartFromABuildingAreNotIt);
        Checks.run("whatOnlyHangsIsNothingUnlessItTouchesSomethingBuilt", FootingTests::whatOnlyHangsIsNothingUnlessItTouchesSomethingBuilt);
        Checks.run("aRowDownAHillStandsAtTheFootingOfItsMiddle", FootingTests::aRowDownAHillStandsAtTheFootingOfItsMiddle);
        Checks.run("oneSearchAnswersForEverySquareOfAThing", FootingTests::oneSearchAnswersForEverySquareOfAThing);
        Checks.run("aChunkAskedAboutBetweenOthersIsKeptPastTheLastKept", FootingTests::aChunkAskedAboutBetweenOthersIsKeptPastTheLastKept);
        Checks.run("theGroundUnderAStairOnAStreetIsLevelAtTheBuildingsFooting", FootingTests::theGroundUnderAStairOnAStreetIsLevelAtTheBuildingsFooting);
        Checks.run("aStreetPastAStairIsStillLevelFromKerbToKerb", FootingTests::aStreetPastAStairIsStillLevelFromKerbToKerb);
        Checks.run("theGroundUnderAnEaveOverALawnIsLevelAtTheBuildingsFooting", FootingTests::theGroundUnderAnEaveOverALawnIsLevelAtTheBuildingsFooting);
        Checks.run("aPitchsTwoGoalsAreOneThingBoxedAsWideAsAPitch", FootingTests::aPitchsTwoGoalsAreOneThingBoxedAsWideAsAPitch);
        Checks.run("aCourtIsAThingOfItsOwnApartFromTheBuildingBesideIt", FootingTests::aCourtIsAThingOfItsOwnApartFromTheBuildingBesideIt);
        Checks.run("groundsWhoseBoxesOverlapInAChainAreLaidAtTheLargestOnesFooting", FootingTests::groundsWhoseBoxesOverlapInAChainAreLaidAtTheLargestOnesFooting);
        Checks.run("goalsReachOnlyAcrossTheirCrossbarAndNotThroughAWall", FootingTests::goalsReachOnlyAcrossTheirCrossbarAndNotThroughAWall);
        Checks.run("basesOfADiamondTurnedOnTheMapAreOneThing", FootingTests::basesOfADiamondTurnedOnTheMapAreOneThing);
        Checks.run("goalsAsFarApartAsAGoalReachesAreLaidLevel", FootingTests::goalsAsFarApartAsAGoalReachesAreLaidLevel);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /**
     * A map drawn in rows of text: '.' open, 's' built, 'h' hung over, a letter A to Z a building, '?' not loaded, and level
     * ground: 'c' a court's surface, 'g' a goal whose crossbar runs along x, 'y' one along y, 'b' a base. Outside it is open.
     */
    private static final class Plan implements Footings.Site {
        final String[] rows;
        final HashMap<Character, Long> houses = new HashMap<>();

        Plan(String... rows) {
            this.rows = rows;
            HashMap<Character, int[]> boxes = new HashMap<>();
            for (int y = 0; y < rows.length; y++) {
                for (int x = 0; x < rows[y].length(); x++) {
                    char c = rows[y].charAt(x);
                    if (c >= 'A' && c <= 'Z') {
                        int[] box = boxes.computeIfAbsent(c, key -> new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE});
                        box[0] = Math.min(box[0], x);
                        box[1] = Math.min(box[1], y);
                        box[2] = Math.max(box[2], x);
                        box[3] = Math.max(box[3], y);
                    }
                }
            }
            boxes.forEach((c, box) -> houses.put(c, Footings.key((box[0] + box[2] + 1) / 2f, (box[1] + box[3] + 1) / 2f)));
        }

        private char at(int x, int y) {
            return y < 0 || y >= rows.length || x < 0 || x >= rows[y].length() ? '.' : rows[y].charAt(x);
        }

        /** A plan with a goal of three squares along x at (x, y) and another at (x, y + apart), on open ground. */
        static Plan pitch(int x, int y, int apart) {
            String[] rows = new String[y + apart + 2];
            for (int row = 0; row < rows.length; row++) {
                rows[row] = row == y || row == y + apart ? ".".repeat(x) + "ggg." : ".".repeat(x + 4);
            }
            return new Plan(rows);
        }

        @Override
        public byte over(int x, int y) {
            char c = at(x, y);
            return c == '.' ? Footings.OPEN : c == 's' ? Footings.BUILT : c == 'h' ? Footings.HUNG : c == '?' ? Footings.UNLOADED
                    : levelKind(x, y) != Grounds.NONE ? Footings.LEVEL : Footings.HOUSE;
        }

        @Override
        public byte levelKind(int x, int y) {
            return switch (at(x, y)) {
                case 'c' -> Grounds.SURFACE;
                case 'g' -> Grounds.GOAL_ALONG_X;
                case 'y' -> Grounds.GOAL_ALONG_Y;
                case 'b' -> Grounds.BASE;
                default -> Grounds.NONE;
            };
        }

        @Override
        public long house(int x, int y) {
            return houses.get(at(x, y));
        }

        @Override
        public boolean paved(int x, int y) {
            return false;
        }
    }

    private static final Footings footings = new Footings();

    private static long found(Plan plan, int x, int y) {
        // Each plan is a map of its own at the same squares as the last.
        footings.forget();
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
                check(footings.whole, "a thing all on loaded map is looked over whole");
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
        check(first == row.houses.get('B'), "the row's footing is that of the building in the middle of it");
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

    /**
     * An answer holds for as long as no square its search went by has changed: asked again, it
     * is not looked for again, and once a square it went by has changed, it is.
     */
    private static void anAnswerIsLookedForAgainOnlyOnceASquareItWentByChanges() {
        Plan edge = new Plan(
                "AA????",
                "AA????");
        Changing site = new Changing(edge);
        Footings fresh = new Footings();
        long house = edge.houses.get('A');
        fresh.find(site, 0, 0);
        check(fresh.found == house && fresh.whole, "a building at the edge of the loaded map is looked over whole");
        check(fresh.readLowX == -1 && fresh.readHighX == 2 && fresh.readLowY == -1 && fresh.readHighY == 2,
                "its search went by the building and the squares round it, those not loaded among them: " + fresh.readLowX + ", " + fresh.readLowY + " to " + fresh.readHighX + ", " + fresh.readHighY);
        int asked = site.asked;
        fresh.find(site, 1, 1);
        check(site.asked == asked && fresh.found == house, "another square of the building is answered with no search");
        site.change(new Plan(
                "AA??CC",
                "AA??CC"), 4, 0, 5, 1);
        fresh.find(site, 0, 0);
        check(site.asked == asked && fresh.found == house, "a building loaded past what the search went by leaves its answer as it was");
        Plan joined = new Plan(
                "AACCCC",
                "AACCCC");
        site.change(joined, 2, 0, 3, 1);
        fresh.find(site, 0, 0);
        check(site.asked > asked && fresh.found == joined.houses.get('C'), "once ground it went by is loaded and joins it to the other, it is looked for again and stands with the other");
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
        Footings fresh = new Footings();
        int[] asked = {0};
        Footings.Site counted = new Footings.Site() {
            @Override
            public byte over(int x, int y) {
                asked[0]++;
                return halls.over(x, y);
            }

            @Override
            public long house(int x, int y) {
                return halls.house(x, y);
            }

            @Override
            public boolean paved(int x, int y) {
                return halls.paved(x, y);
            }
        };
        fresh.find(counted, side + 1, side / 2);
        check(!fresh.whole && fresh.found == west, "the covered way between them stands with the hall nearest along it, before any hall was asked about");
        fresh.find(counted, side + 2, side / 2);
        check(!fresh.whole && fresh.found == east, "and its other end with the other hall");
        int first = asked[0];
        check(first > Footings.MOST, "the test's first searches looked a hall over, or they show nothing: " + first);
        for (int[] at : new int[][] {{0, 0}, {side - 1, side - 1}, {side / 2, side / 2}, {side - 1, side / 2}}) {
            fresh.find(counted, at[0], at[1]);
            check(fresh.found == west && !fresh.whole, "every square of one hall has that hall's footing, from " + at[0] + ", " + at[1]);
        }
        fresh.find(counted, side + 4, 0);
        check(fresh.found == east, "and of the other hall, the other's");
        fresh.find(counted, 2 * side + 3, side - 1);
        check(fresh.found == east, "to its far corner");
        check(asked[0] - first < 2 * Footings.MOST * 9, "a thing found too large is not looked over again for each square of it: " + (asked[0] - first) + " squares asked about");

        String[] long1 = new String[3];
        char[] viaduct = new char[Footings.MOST + 10];
        Arrays.fill(viaduct, 's');
        Arrays.fill(long1, new String(viaduct));
        check(found(new Plan(long1), 5, 1) == Footings.NONE, "a viaduct with no building in it follows the land as its road does");
    }

    /**
     * A site that counts the squares it is asked about, and whose squares change a box at a
     * time: asItWas tells of a box whether a change since overlapped it, as Terrain's site
     * tells it from its log of the chunks that came out otherwise.
     */
    static final class Changing implements Footings.Site {
        private Footings.Site now;
        private final ArrayList<int[]> changes = new ArrayList<>();
        int asked;

        Changing(Footings.Site now) {
            this.now = now;
        }

        /** From now on the site is to, which is otherwise than it was only from (lowX, lowY) to (highX, highY). */
        void change(Footings.Site to, int lowX, int lowY, int highX, int highY) {
            now = to;
            changes.add(new int[] {lowX, lowY, highX, highY});
        }

        @Override
        public byte over(int x, int y) {
            asked++;
            return now.over(x, y);
        }

        @Override
        public long house(int x, int y) {
            return now.house(x, y);
        }

        @Override
        public boolean paved(int x, int y) {
            return now.paved(x, y);
        }

        @Override
        public byte levelKind(int x, int y) {
            return now.levelKind(x, y);
        }

        @Override
        public long changes() {
            return changes.size();
        }

        @Override
        public boolean asItWas(int lowX, int lowY, int highX, int highY, long since) {
            for (int at = (int) since; at < changes.size(); at++) {
                int[] box = changes.get(at);
                if (box[0] <= highX && box[2] >= lowX && box[1] <= highY && box[3] >= lowY) {
                    return false;
                }
            }
            return true;
        }
    }

    private static void oneSearchAnswersForEverySquareOfAThing() {
        // A block of 40 by 40 with a flight down its side, and another at the edge of the loaded map.
        String[] rows = new String[42];
        for (int y = 0; y < rows.length; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < 48; x++) {
                row.append(y < 40 && x < 40 ? 'A' : y < 40 && x == 40 ? 's' : y < 10 && x >= 44 && x < 47 ? 'B' : y < 10 && x == 47 ? '?' : '.');
            }
            rows[y] = row.toString();
        }
        Changing site = new Changing(new Plan(rows));
        Footings fresh = new Footings();
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < 48; x++) {
                fresh.find(site, x, y);
            }
        }
        int squares = 48 * rows.length, thing = 40 * 41;
        // Each square asked once for itself, and the block's search asks once about each square of it and round it.
        check(site.asked < squares + 2 * (thing + 4 * 42) + 200, "every square of a map asked for its footing asks about each square a few times, not once a square of the thing: " + site.asked);
        int before = site.asked;
        site.change(site.now, 100, 0, 107, 7);
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < 48; x++) {
                fresh.find(site, x, y);
            }
        }
        check(site.asked - before < squares + 200, "with a chunk changed away from the block it is not looked over again: " + (site.asked - before));
    }

    /** A building of one square at the first corner of every chunk, and open ground round it. */
    private static final class Corners implements Footings.Site {
        int asked;

        @Override
        public byte over(int x, int y) {
            asked++;
            return Math.floorMod(x, Terrain.CHUNK_TILES) == 0 && Math.floorMod(y, Terrain.CHUNK_TILES) == 0 ? Footings.HOUSE : Footings.OPEN;
        }

        @Override
        public long house(int x, int y) {
            return Footings.key(x + 0.5f, y + 0.5f);
        }

        @Override
        public boolean paved(int x, int y) {
            return false;
        }
    }

    private static void aChunkAskedAboutBetweenOthersIsKeptPastTheLastKept() {
        Corners site = new Corners();
        Footings fresh = new Footings();
        fresh.find(site, 0, 0);
        int before = site.asked;
        for (int chunk = 1; chunk <= Footings.KEPT_CHUNKS + 500; chunk++) {
            fresh.find(site, chunk * Terrain.CHUNK_TILES, 0);
            int asked = site.asked;
            fresh.find(site, 0, 0);
            check(site.asked == asked, "the chunk asked about after each other one is the newest kept and is not looked over again, after " + chunk + " others");
        }
        check(site.asked > before, "the other chunks were looked over");
        int asked = site.asked;
        fresh.find(site, (Footings.KEPT_CHUNKS + 500) * Terrain.CHUNK_TILES, 0);
        check(site.asked == asked, "the last chunk kept is kept");
        fresh.find(site, Terrain.CHUNK_TILES, 0);
        check(site.asked > asked, "the first of the others, past the last kept, was let go and is looked over again");
        fresh.find(site, 0, 0);
        fresh.forget();
        asked = site.asked;
        fresh.find(site, 0, 0);
        check(site.asked > asked, "a map loaded anew has the chunk last asked about looked over again");
    }

    private static void whatOnlyHangsIsNothingUnlessItTouchesSomethingBuilt() {
        Plan street = new Plan(
                "AAA......",
                "AAAh..h..",
                "......h.s",
                "........h");
        check(found(street, 3, 1) == street.houses.get('A') && !footings.free, "a sign that hangs out from a building stands with the building");
        check(found(street, 6, 1) == Footings.NONE && !footings.free && found(street, 6, 2) == Footings.NONE, "the head of a lamp on its pole is no made thing and goes by the land");
        long canopy = found(street, 8, 3);
        check(footings.free && canopy == found(street, 8, 2), "what hangs from a canopy stands with the canopy");
    }

    private static void aRowDownAHillStandsAtTheFootingOfItsMiddle() {
        Plan row = new Plan("AABBCCDDEE.FF");
        for (int x = 0; x < 10; x++) {
            check(found(row, x, 0) == row.houses.get('C'), "from square " + x + " the row stands at the footing of the building in its middle");
        }
        Plan pair = new Plan("AABB");
        check(found(pair, 0, 0) == found(pair, 3, 0), "two buildings as near the middle as each other give one answer whichever is asked");
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
        Arrays.fill(Terrain.footwayAround, false);
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
        boolean[] footway = Terrain.footwayAround.clone();
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
            // The street runs along the wall, so Paving lays all of it to the footing: it is the land beyond it that shows what the footing is off.
            float land = terrain.corner(x + from + 1, y + row - 6 - 2 * Terrain.REACH, from + 1, row - 6 - 2 * Terrain.REACH);
            check(Math.abs(land - pad) > 0.2f, "the test's land lies off the footing, or it shows nothing: " + land);
        } finally {
            System.arraycopy(around, 0, Terrain.around, 0, around.length);
            System.arraycopy(pads, 0, Terrain.padsAround, 0, pads.length);
            System.arraycopy(footway, 0, Terrain.footwayAround, 0, footway.length);
        }
    }

    private static void theGroundUnderAnEaveOverALawnIsLevelAtTheBuildingsFooting() {
        byte[] around = Terrain.around.clone();
        float[] pads = Terrain.padsAround.clone();
        boolean[] footway = Terrain.footwayAround.clone();
        try {
            Terrain terrain = Terrain.HILLS;
            int span = Terrain.SPAN, row = span / 2 + 2;
            float pad = -1.5f;
            Arrays.fill(Terrain.padsAround, Float.NaN);
            Arrays.fill(Terrain.footwayAround, false);
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
            System.arraycopy(footway, 0, Terrain.footwayAround, 0, footway.length);
        }
    }

    private static void aStreetPastAStairIsStillLevelFromKerbToKerb() {
        byte[] around = Terrain.around.clone();
        float[] pads = Terrain.padsAround.clone();
        boolean[] footway = Terrain.footwayAround.clone();
        try {
            Terrain terrain = Terrain.HILLS;
            // The flight at one end of the ground laid, and the street looked at well out of its reach.
            int span = Terrain.SPAN, row = span / 2 + 2, from = 0, to = 3, along = to + Terrain.REACH + 5;
            int x = 6466 - from, y = 5249 - row;
            layStairOnStreet(3.25f, row, from, to);
            terrain.streets(x, y);
            float near = terrain.corner(x + along, y + row - 5, along, row - 5), far = terrain.corner(x + along, y + row - 2, along, row - 2);
            check(Math.abs(near - far) < 1e-4f, "a street is level from side to side along from the flight that stands on it: " + near + " and " + far);
        } finally {
            System.arraycopy(around, 0, Terrain.around, 0, around.length);
            System.arraycopy(pads, 0, Terrain.padsAround, 0, pads.length);
            System.arraycopy(footway, 0, Terrain.footwayAround, 0, footway.length);
        }
    }

    private static void aPitchsTwoGoalsAreOneThingBoxedAsWideAsAPitch() {
        // Two goals 30 squares apart down the pitch.
        Plan pitch = Plan.pitch(10, 2, 30);
        long goal = found(pitch, 10, 2);
        check(goal != Footings.NONE && footings.free && footings.whole, "a goal is a thing that stands at the height of the ground under it");
        check(goal == found(pitch, 12, 32), "the goal at the other end of the pitch is the same thing");
        check(Footings.keyX(goal) == 11.5f && Footings.keyY(goal) == 17.5f, "it stands at the middle of the pitch, got " + Footings.keyX(goal) + ", " + Footings.keyY(goal));
        check(footings.boxed, "a pitch is laid level");
        int length = footings.boxHighY - footings.boxLowY, width = footings.boxHighX - footings.boxLowX;
        check(footings.boxLowY == 2 - Grounds.MARGIN && footings.boxHighY == 33 + Grounds.MARGIN, "the box runs from goal to goal and the run-off past them, got " + footings.boxLowY + ".." + footings.boxHighY);
        check(width == Math.round(length * Grounds.WIDTH_FOR_LENGTH), "the box is as wide as a pitch of its length, got " + width + " for " + length);
        check(Math.abs(footings.boxLowX + footings.boxHighX - (2 * 10 + 3)) <= 1, "and centred on the goals, got " + footings.boxLowX + ".." + footings.boxHighX);
        check(found(pitch, 11, 17) == Footings.NONE, "the grass of the pitch is no part of the thing: the box lays it level");
    }

    private static void aCourtIsAThingOfItsOwnApartFromTheBuildingBesideIt() {
        Plan court = new Plan(
                "AAAA.......",
                "AAAAcccccc.",
                "AAAAcccccc.",
                "AAAAcccccc.",
                "...........");
        long building = found(court, 0, 0);
        long surface = found(court, 4, 1);
        check(surface != building && surface != Footings.NONE, "a court beside a building is a thing of its own");
        check(footings.free && footings.boxed, "it stands at the height of the ground under it, laid level");
        check(footings.boxLowX == 4 - Grounds.MARGIN && footings.boxHighX == 10 + Grounds.MARGIN && footings.boxLowY == 1 - Grounds.MARGIN && footings.boxHighY == 4 + Grounds.MARGIN,
                "a court's box is the court and its apron, no wider: " + footings.boxLowX + ", " + footings.boxLowY + " to " + footings.boxHighX + ", " + footings.boxHighY);
        check(surface == found(court, 9, 3), "every square of the surface is the one thing");
        check(found(court, 3, 2) == building && !footings.boxed, "the building is not joined to it");
    }

    private static void groundsWhoseBoxesOverlapInAChainAreLaidAtTheLargestOnesFooting() {
        // A small court, a larger one whose box overlaps it, the largest overlapping only the second, and one well apart.
        String[] rows = new String[12];
        for (int y = 0; y < rows.length; y++) {
            rows[y] = (y < 2 ? "cc." : "...") + (y < 6 ? "cccccc." : ".......") + "cccccccccccc" + ".".repeat(20) + (y < 3 ? "ccc" : "...");
        }
        Plan chain = new Plan(rows);
        long largest = found(chain, 10, 0);
        int lowX = footings.boxLowX, lowY = footings.boxLowY, highX = footings.boxHighX, highY = footings.boxHighY;
        check(footings.ruler == largest, "the largest of a chain is its own ruler");
        found(chain, 0, 0);
        check(footings.boxHighX < lowX, "the small court's box is apart from the largest's: " + footings.boxHighX + " and " + lowX);
        check(footings.ruler == largest && footings.rulerLowX == lowX && footings.rulerLowY == lowY && footings.rulerHighX == highX && footings.rulerHighY == highY,
                "and it is laid at the largest's footing all the same, through the court between them");
        check(footings.count == 4, "the courts looked over for the ruler are not counted with the thing's own squares, got " + footings.count);
        found(chain, 8, 5);
        check(footings.ruler == largest, "as is the court between");
        long apart = found(chain, 42, 0);
        check(footings.ruler == apart, "a court apart from them keeps its own");
        Plan twins = new Plan(
                "cccc.cccc",
                "cccc.cccc");
        long left = found(twins, 0, 0);
        found(twins, 8, 1);
        check(footings.ruler == left, "of two as large, the one whose middle comes first, from either");
        found(twins, 0, 1);
        check(footings.ruler == left, "and from the other");
        // Looking round the court on the right, the largest is met before the court between that joins them.
        String[] bridged = new String[14];
        for (int y = 0; y < bridged.length; y++) {
            bridged[y] = "c".repeat(14) + "." + (y < 4 ? "cccc" : "....") + "." + (y < 6 ? "cccccc" : "......");
        }
        Plan bridge = new Plan(bridged);
        long big = found(bridge, 0, 0);
        int bigLowX = footings.boxLowX, bigHighY = footings.boxHighY;
        found(bridge, 25, 5);
        check(footings.ruler == big, "a court met before the court that joins it to the chain is of the chain");
        footings.find(bridge, 13, 13);
        check(footings.count == 0 && footings.found == big && footings.boxLowX == bigLowX && footings.boxHighY == bigHighY && footings.ruler == big,
                "and is answered from the same search, with its own middle and box");
        footings.find(bridge, 16, 2);
        long between = footings.found;
        check(footings.ruler == big && footings.count == 0, "as is the court between");
        check(found(bridge, 16, 2) == between && footings.ruler == big, "as a search of its own finds it");
    }

    private static void goalsReachOnlyAcrossTheirCrossbarAndNotThroughAWall() {
        Plan side = new Plan(
                "ggg.........................ggg");
        long left = found(side, 0, 0);
        check(left != found(side, 28, 0), "goals side by side along the line of their crossbars are two things");
        Plan walled = new Plan(
                "ggg",
                "...",
                "sss",
                "...",
                "ggg");
        check(found(walled, 0, 0) != found(walled, 0, 4), "a goal does not reach through a wall for another");
        Plan otherWay = new Plan(
                "y.....y",
                "y.....y",
                "y.....y");
        check(found(otherWay, 0, 0) == found(otherWay, 6, 2), "a goal along y reaches along x for the goal it faces");
    }

    private static void basesOfADiamondTurnedOnTheMapAreOneThing() {
        Plan diamond = new Plan(
                "......b.......",
                "..............",
                "..............",
                "b.............",
                "..........b...",
                "..............",
                "..............",
                "....b.........");
        long first = found(diamond, 6, 0);
        check(first == found(diamond, 0, 3) && first == found(diamond, 10, 4) && first == found(diamond, 4, 7), "the bases of a diamond are one thing however it lies on the map");
        check(footings.boxed && footings.boxLowX == -Grounds.MARGIN && footings.boxHighX == 11 + Grounds.MARGIN, "laid level over all of them");
    }

    private static void goalsAsFarApartAsAGoalReachesAreLaidLevel() {
        // Half way down the side of the longest pitch a goal reaches, the land eased round it is still near enough a goal for every chunk round it to see.
        check(found(Plan.pitch(10, 2, Grounds.reach(Grounds.GOAL_ALONG_X)), 10, 2) != Footings.NONE && footings.whole && footings.boxed, "goals as far apart as a goal reaches are one thing laid level");
        check(footings.boxHighY - footings.boxLowY == Grounds.reach(Grounds.GOAL_ALONG_X) + 1 + 2 * Grounds.MARGIN, "from end to end");
        check(found(Plan.pitch(10, 2, Grounds.reach(Grounds.GOAL_ALONG_X) + 1), 10, 2) != found(Plan.pitch(10, 2, Grounds.reach(Grounds.GOAL_ALONG_X) + 1), 12, 83), "goals farther apart are two");
    }
}

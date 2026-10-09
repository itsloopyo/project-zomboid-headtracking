package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.HashMap;

/** Tests that made ground is laid: level against what is built, a ramp where it must change height, and never a hump. */
final class PavingTests {
    private static final int SPAN = Terrain.SPAN, MID = SPAN / 2;
    /** No two corners a square apart on a ramp between a footing and land a tile off it are further apart than this: the ramp's steepest. */
    private static final float STEEPEST = 1.5f / Paving.EASED_OVER + 0.005f;

    private PavingTests() {}

    static void run() {
        byte[] around = Terrain.around.clone();
        float[] pads = Terrain.padsAround.clone();
        boolean[] footway = Terrain.footwayAround.clone();
        try {
            Checks.run("pavingAgainstAWallIsLevelAtItsFootingFromTheWallToTheFarKerb", PavingTests::pavingAgainstAWallIsLevelAtItsFootingFromTheWallToTheFarKerb);
            Checks.run("aStreetPastTheEndOfAWallComesDownAsARamp", PavingTests::aStreetPastTheEndOfAWallComesDownAsARamp);
            Checks.run("aStreetBehindALawnTakesNothingFromAHouse", PavingTests::aStreetBehindALawnTakesNothingFromAHouse);
            Checks.run("aDriveIsLevelAtTheGarageAndTheRoadStaysWhereTheLandHasIt", PavingTests::aDriveIsLevelAtTheGarageAndTheRoadStaysWhereTheLandHasIt);
            Checks.run("aRoadBetweenTwoFootingsIsLevelAndTheFootwaysTakeTheDifference", PavingTests::aRoadBetweenTwoFootingsIsLevelAndTheFootwaysTakeTheDifference);
            Checks.run("aYardBetweenTwoFootingsPassesFromOneToTheOther", PavingTests::aYardBetweenTwoFootingsPassesFromOneToTheOther);
            Checks.run("aLanePastAHeldYardBehindAVergeKeepsItsOwnHeight", PavingTests::aLanePastAHeldYardBehindAVergeKeepsItsOwnHeight);
            Checks.run("aPathBehindAStripOfLawnIsGradedAsTheLawnIs", PavingTests::aPathBehindAStripOfLawnIsGradedAsTheLawnIs);
            Checks.run("aSignOnPostsHoldsNoStreetAndACanopyDoes", PavingTests::aSignOnPostsHoldsNoStreetAndACanopyDoes);
            Checks.run("twoChunksGiveTheCornerTheyShareOneHeight", PavingTests::twoChunksGiveTheCornerTheyShareOneHeight);
            Checks.run("whatIsGatheredReachesAsFarAsPavingLooks", PavingTests::whatIsGatheredReachesAsFarAsPavingLooks);
        } finally {
            System.arraycopy(around, 0, Terrain.around, 0, around.length);
            System.arraycopy(pads, 0, Terrain.padsAround, 0, pads.length);
            System.arraycopy(footway, 0, Terrain.footwayAround, 0, footway.length);
        }
        Checks.run("builtThingsAcrossAPassageAreOneThing", PavingTests::builtThingsAcrossAPassageAreOneThing);
        Checks.run("aBuildingWithPavingAgainstItStandsWhereThePavingMeetsIt", PavingTests::aBuildingWithPavingAgainstItStandsWhereThePavingMeetsIt);
        Checks.run("aBoothOnAPavementIsSetDownOnIt", PavingTests::aBoothOnAPavementIsSetDownOnIt);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** What a square of a test's ground is: its kind, the footing it stands on or NaN, and whether it is footway. */
    private interface Ground {
        byte kind(int i, int j);

        float pad(int i, int j);

        default boolean footway(int i, int j) {
            return kind(i, j) == Terrain.MADE;
        }
    }

    private static void lay(Ground ground) {
        for (int i = 0; i < SPAN; i++) {
            for (int j = 0; j < SPAN; j++) {
                Terrain.around[i * SPAN + j] = ground.kind(i, j);
                Terrain.padsAround[i * SPAN + j] = ground.pad(i, j);
                Terrain.footwayAround[i * SPAN + j] = ground.footway(i, j);
            }
        }
    }

    /** The corner (i, j) of the ground laid, on land as level as the map: every height is then what the footings alone make it. */
    private static float corner(int i, int j) {
        return Terrain.LEVEL.corner(i, j, i, j);
    }

    /** How far the four corners of the square (i, j) are from lying in one plane. */
    private static float twist(int i, int j) {
        return Math.abs(corner(i, j) + corner(i + 1, j + 1) - corner(i + 1, j) - corner(i, j + 1));
    }

    private static void pavingAgainstAWallIsLevelAtItsFootingFromTheWallToTheFarKerb() {
        // A building a tile above the land, two squares of footway, a road six squares wide, open ground.
        int wall = 40, kerb = 42, verge = 48;
        lay(new Ground() {
            @Override
            public byte kind(int i, int j) {
                return j < kerb ? Terrain.MADE : j < verge ? Terrain.STREET : Terrain.NATURAL;
            }

            @Override
            public float pad(int i, int j) {
                return j < wall ? 1f : Float.NaN;
            }
        });
        Terrain.LEVEL.streets(0, 0);
        for (int i = MID - 4; i <= MID + 4; i++) {
            for (int j = wall; j <= verge; j++) {
                check(Math.abs(corner(i, j) - 1f) < 1e-5f, "paving against a wall is at the wall's footing from the wall to the far kerb, corner " + i + ", " + j + ": " + corner(i, j));
            }
            for (int j = wall; j < verge; j++) {
                check(twist(i, j) < 1e-5f, "and no square of it is twisted");
            }
        }
        float last = 1f;
        for (int j = verge + 1; j <= verge + Terrain.REACH + 1; j++) {
            float here = corner(MID, j);
            check(here <= last + 1e-6f && here >= -1e-6f, "the verge beyond the far kerb falls to the land and does not hump, corner " + j + ": " + here);
            last = here;
        }
        check(Math.abs(last) < 1e-5f, "and has the land's own height by the time it is out of reach: " + last);
    }

    private static void aStreetPastTheEndOfAWallComesDownAsARamp() {
        // The same street, with the building only on the squares before the middle: beyond its end the street runs on between verges.
        int wall = 40, kerb = 42, verge = 48, end = MID;
        lay(new Ground() {
            @Override
            public byte kind(int i, int j) {
                return j < wall ? i < end ? Terrain.MADE : Terrain.NATURAL : j < kerb ? Terrain.MADE : j < verge ? Terrain.STREET : Terrain.NATURAL;
            }

            @Override
            public float pad(int i, int j) {
                return j < wall && i < end ? 1f : Float.NaN;
            }

            @Override
            public boolean footway(int i, int j) {
                return j >= wall && j < kerb;
            }
        });
        Terrain.LEVEL.streets(0, 0);
        float last = 1f;
        boolean reached = false;
        for (int i = end - 2 * Terrain.LEVELLED; i <= end + Paving.EASED_OVER + Terrain.LEVELLED + 2; i++) {
            float here = corner(i, kerb + 2);
            check(i > end - Terrain.LEVELLED || Math.abs(here - 1f) < 1e-5f, "a road is at the footing along the wall, corner " + i + ": " + here);
            check(here <= last + 1e-6f, "towards the end of the wall and past it, it only ever comes down, corner " + i);
            check(last - here <= STEEPEST, "and no steeper than a ramp, corner " + i + ": " + (last - here));
            for (int j = kerb; j <= verge; j++) {
                check(corner(i, j) == here, "and is as high at one kerb as at the other all the way down, corner " + i + ", " + j + ": " + corner(i, j) + " against " + here);
            }
            float footway = corner(i, wall + 1);
            check(footway >= here - 1e-6f && footway <= 1f + 1e-6f, "the footway beside it lies between the road and the wall's footing, corner " + i + ": " + footway);
            if (i > end + 1) {
                // Past the wall the ground behind the footway is open: the footway is no ridge between it and the road. A corner
                // within reach of the wall's end is still graded a little towards its footing, as all ground there is: 0.022 at most.
                float behind = corner(i, wall - 1), kerbside = corner(i, kerb);
                check(footway <= Math.max(behind, kerbside) + 0.03f, "past the end of the wall the footway stands no higher than the road or the verge behind it, corner " + i + ": "
                        + footway + " between " + behind + " and " + kerbside);
            }
            reached |= here == 0f;
            last = here;
        }
        check(reached, "the ramp is down at the land's height within the distance it eases over");
    }

    private static void aStreetBehindALawnTakesNothingFromAHouse() {
        // A house a tile above the land, four squares of lawn, a street five squares wide.
        int wall = 36, lawn = 40, verge = 45;
        lay(new Ground() {
            @Override
            public byte kind(int i, int j) {
                return j < wall && Math.abs(i - MID) < 6 ? Terrain.MADE : j >= lawn && j < verge ? Terrain.STREET : Terrain.NATURAL;
            }

            @Override
            public float pad(int i, int j) {
                return j < wall && Math.abs(i - MID) < 6 ? 1f : Float.NaN;
            }
        });
        Terrain.LEVEL.streets(0, 0);
        for (int i = MID - 12; i <= MID + 12; i++) {
            for (int j = lawn; j <= verge; j++) {
                check(corner(i, j) == 0f, "a street past a house on its lawn is at the land's height from kerb to kerb, corner " + i + ", " + j + ": " + corner(i, j));
            }
        }
        float between = corner(MID, wall + 2);
        check(between > 0.05f && between < 0.95f, "the lawn between them is graded from one to the other: " + between);
    }

    private static void aDriveIsLevelAtTheGarageAndTheRoadStaysWhereTheLandHasIt() {
        // A house a tile above the land, a drive four squares wide and eight long across its lawn, a road six squares wide.
        int wall = 36, road = 44, verge = 50, driveFrom = MID - 2, driveTo = MID + 2;
        lay(new Ground() {
            @Override
            public byte kind(int i, int j) {
                boolean house = j < wall && Math.abs(i - MID) < 8, drive = j >= wall && j < road && i >= driveFrom && i < driveTo;
                return house ? Terrain.MADE : drive || j >= road && j < verge ? Terrain.STREET : Terrain.NATURAL;
            }

            @Override
            public float pad(int i, int j) {
                return j < wall && Math.abs(i - MID) < 8 ? 1f : Float.NaN;
            }
        });
        Terrain.LEVEL.streets(0, 0);
        float last = 1f;
        for (int j = wall; j <= road; j++) {
            float here = corner(MID, j);
            check(j > wall || here == 1f, "a drive is at the garage's footing at its door: " + here);
            check(here <= last + 1e-6f, "and only ever comes down from it, corner " + j);
            last = here;
        }
        check(corner(MID, wall + 1) > 0.5f, "it does not drop the whole way in its first square: " + corner(MID, wall + 1));
        for (int i = MID - 14; i <= MID + 14; i++) {
            for (int j = road; j <= verge; j++) {
                check(corner(i, j) == 0f, "the road a drive opens onto is at the land's height from kerb to kerb, corner " + i + ", " + j + ": " + corner(i, j));
            }
        }
    }

    private static void aRoadBetweenTwoFootingsIsLevelAndTheFootwaysTakeTheDifference() {
        // Two buildings across a street, one a tile higher than the other: two squares of footway, a road of eight, two of footway.
        int near = 40, far = 52;
        lay(new Ground() {
            @Override
            public byte kind(int i, int j) {
                return j < near + 2 || j >= far - 2 ? Terrain.MADE : Terrain.STREET;
            }

            @Override
            public float pad(int i, int j) {
                return j < near ? 1f : j >= far ? 2f : Float.NaN;
            }
        });
        Terrain.LEVEL.streets(0, 0);
        float road = corner(MID, near + 2);
        check(Math.abs(road - 1.5f) < 0.01f, "a road between two footings is held half way between them: " + road);
        for (int j = near + 2; j <= far - 2; j++) {
            check(corner(MID, j) == road, "and is as high at one kerb as at the other, corner " + j + ": " + corner(MID, j) + " against " + road);
        }
        float last = 1f;
        for (int j = near; j <= far; j++) {
            float here = corner(MID, j);
            check(here >= last - 1e-6f, "from the lower wall to the higher the ground only ever climbs, corner " + j + ": " + here);
            last = here;
        }
        check(Math.abs(corner(MID, near + 1) - 1.25f) < 0.06f && Math.abs(corner(MID, far - 1) - 1.75f) < 0.06f,
                "each footway is ruled from its wall to the road's edge: " + corner(MID, near + 1) + " and " + corner(MID, far - 1));
    }

    private static void aYardBetweenTwoFootingsPassesFromOneToTheOther() {
        // The same two buildings with nothing but a yard between them: no road to hold level, so the yard is one slope from wall to wall.
        int near = 40, far = 52;
        lay(new Ground() {
            @Override
            public byte kind(int i, int j) {
                return Terrain.MADE;
            }

            @Override
            public float pad(int i, int j) {
                return j < near ? 1f : j >= far ? 2f : Float.NaN;
            }
        });
        Terrain.LEVEL.streets(0, 0);
        float last = 1f;
        for (int j = near; j <= far; j++) {
            float here = corner(MID, j);
            check(here >= last - 1e-6f && here <= 2f + 1e-6f, "a yard between two footings only ever climbs from the lower to the higher, corner " + j + ": " + here);
            check(here - last < 0.3f, "and by no step, corner " + j + ": " + (here - last));
            last = here;
        }
        check(corner(MID, near + 1) < 1.05f && corner(MID, far - 1) > 1.95f, "it is at each wall's footing along that wall: " + corner(MID, near + 1) + " and " + corner(MID, far - 1));
    }

    private static void aLanePastAHeldYardBehindAVergeKeepsItsOwnHeight() {
        // A building half a tile above the land with a broad yard before it, a square of verge, and a lane four squares wide that runs past.
        int wall = 30, yard = 40, lane = 41, verge = 45;
        lay(new Ground() {
            @Override
            public byte kind(int i, int j) {
                return j < yard ? Terrain.MADE : j >= lane && j < verge ? Terrain.STREET : Terrain.NATURAL;
            }

            @Override
            public float pad(int i, int j) {
                return j < wall ? 0.5f : Float.NaN;
            }
        });
        Terrain.LEVEL.streets(0, 0);
        check(Math.abs(corner(MID, yard) - 0.5f) < 1e-5f, "the yard is at the building's footing to its edge: " + corner(MID, yard));
        for (int i = MID - 20; i <= MID + 20; i++) {
            for (int j = lane; j <= verge; j++) {
                check(corner(i, j) == 0f, "a lane past a held yard behind a verge is at the land's height, both kerbs alike, corner " + i + ", " + j + ": " + corner(i, j));
            }
        }
    }

    private static void aSignOnPostsHoldsNoStreetAndACanopyDoes() {
        // A broad yard of made ground, and on it a row of six squares under a sign, or three rows of them under a canopy, half a tile off the land.
        for (int deep = 1; deep <= 3; deep += 2) {
            int rows = deep;
            lay(new Ground() {
                @Override
                public byte kind(int i, int j) {
                    return Math.abs(i - MID) < 20 && Math.abs(j - MID) < 20 ? Terrain.MADE : Terrain.NATURAL;
                }

                @Override
                public float pad(int i, int j) {
                    return i >= MID - 3 && i < MID + 3 && j >= MID && j < MID + rows ? 0.5f : Float.NaN;
                }
            });
            Terrain.LEVEL.streets(0, 0);
            float out = corner(MID, MID - Paving.APRON - 3);
            if (rows == 1) {
                check(out == 0f, "a sign on its posts holds no yard to its own height: " + out);
            } else {
                check(out > 0.4f && out <= 0.5f, "a canopy holds the yard straight out from it to its own footing: " + out);
            }
        }
    }

    private static void twoChunksGiveTheCornerTheyShareOneHeight() {
        // A street with footways past the end of one building and on between two others, on rolling land: every part of the rule has a say at the corner asked for.
        int x = 10700, y = 9700;
        Ground ground = new Ground() {
            @Override
            public byte kind(int i, int j) {
                return j >= 20 && j < 32 ? i % 5 == 0 && j > 28 ? Terrain.MADE : Terrain.STREET : pad(i, j) == pad(i, j) ? Terrain.MADE : Terrain.NATURAL;
            }

            @Override
            public float pad(int i, int j) {
                return j < 20 && i < 30 ? 2.5f : j < 20 && i > 44 ? 3.25f : j >= 32 && i > 20 && i < 60 ? 1.75f : Float.NaN;
            }

            @Override
            public boolean footway(int i, int j) {
                return j >= 20 && j < 32 && (j < 22 || j >= 30 || kind(i, j) == Terrain.MADE);
            }
        };
        for (int cornerI = 26; cornerI <= 50; cornerI += 4) {
            for (int cornerJ = 18; cornerJ <= 34; cornerJ += 4) {
                int[] heights = new int[2];
                for (int chunk = 0; chunk < 2; chunk++) {
                    // The corner is the last of one chunk's and the first of the next one's: AROUND + CHUNK_TILES squares into what one gathers, AROUND into the other's.
                    int at = Terrain.AROUND + (chunk == 0 ? Terrain.CHUNK_TILES : 0);
                    int shiftI = cornerI - at, shiftJ = cornerJ - at;
                    lay(new Ground() {
                        @Override
                        public byte kind(int i, int j) {
                            return ground.kind(i + shiftI, j + shiftJ);
                        }

                        @Override
                        public float pad(int i, int j) {
                            return ground.pad(i + shiftI, j + shiftJ);
                        }

                        @Override
                        public boolean footway(int i, int j) {
                            return ground.footway(i + shiftI, j + shiftJ);
                        }
                    });
                    // As a chunk asks: for its own corners and no others, which is what keeps Paving to the squares round them.
                    Terrain.HILLS.streets(x + shiftI, y + shiftJ, Terrain.AROUND, Terrain.AROUND + Terrain.CHUNK_TILES, true);
                    heights[chunk] = Float.floatToRawIntBits(Terrain.HILLS.corner(x + cornerI, y + cornerJ, at, at));
                }
                check(heights[0] == heights[1], "the corner two chunks share is one height to the last bit, corner " + cornerI + ", " + cornerJ
                        + ": " + Float.intBitsToFloat(heights[0]) + " and " + Float.intBitsToFloat(heights[1]));
            }
        }
    }

    private static void whatIsGatheredReachesAsFarAsPavingLooks() {
        // A corner goes by road within LEVELLED of it, that by what lies level within EASED_OVER of it, that by a wall within
        // LEVEL_FOR of it, and whether ground between is broad by the squares within twice BROAD of it.
        int reach = Terrain.LEVELLED + Paving.EASED_OVER + Paving.LEVEL_FOR + 2 * Paving.BROAD;
        check(reach <= Terrain.AROUND, "Terrain gathers as far round a chunk as a corner of it goes by: " + reach + " squares against " + Terrain.AROUND);
        check(Paving.MOUTH + 2 <= Terrain.AROUND && Paving.FOOTWAY + 2 <= Terrain.AROUND && Paving.APRON + 2 <= Terrain.AROUND, "and as far as a strip, a footway and a mouth are followed");
        check(Terrain.AROUND % Terrain.CHUNK_TILES == 0, "in whole chunks");
    }

    private static void aPathBehindAStripOfLawnIsGradedAsTheLawnIs() {
        // A house a tile above the land, a square of lawn, a path a square wide along the wall, lawn beyond: nothing built touches the path.
        int wall = 36, path = 37;
        lay(new Ground() {
            @Override
            public byte kind(int i, int j) {
                return j < wall || j == path ? Terrain.MADE : Terrain.NATURAL;
            }

            @Override
            public float pad(int i, int j) {
                return j < wall ? 1f : Float.NaN;
            }
        });
        Terrain.LEVEL.streets(0, 0);
        float last = 1f;
        for (int j = wall; j <= wall + Terrain.REACH + 1; j++) {
            float here = corner(MID, j);
            check(here <= last + 1e-6f, "the ground only ever falls from the house's footing to the land, path and all, corner " + j + ": " + here + " after " + last);
            last = here;
        }
        check(corner(MID, path) > 0.5f && corner(MID, path + 1) > 0.3f, "and the path lies on the grade, not in a trench at the land's height: " + corner(MID, path) + " and " + corner(MID, path + 1));
    }

    /** A map drawn in rows of text: '.' open ground, '=' made ground, 's' built, 'h' hung over made ground, '?' not loaded, a letter A to Z a building. Outside it is open. */
    private static final class Plan implements Footings.Site {
        final String[] rows;
        final HashMap<Character, Long> houses = new HashMap<>();

        Plan(String... rows) {
            this.rows = rows;
            for (int y = 0; y < rows.length; y++) {
                for (int x = 0; x < rows[y].length(); x++) {
                    char c = rows[y].charAt(x);
                    if (c >= 'A' && c <= 'Z') {
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
            return c == '.' ? Footings.OPEN : c == '=' ? Footings.PAVED : c == 's' ? Footings.BUILT : c == 'h' ? Footings.HUNG : c == '?' ? Footings.UNLOADED : Footings.HOUSE;
        }

        @Override
        public long house(int x, int y) {
            return houses.get(at(x, y));
        }

        @Override
        public boolean paved(int x, int y) {
            return at(x, y) == 'h';
        }
    }

    private static final Footings footings = new Footings();

    private static long found(Plan plan, int x, int y) {
        footings.forget();
        footings.find(plan, x, y);
        return footings.found;
    }

    private static void builtThingsAcrossAPassageAreOneThing() {
        char[] gap = new char[Footings.PASSAGE];
        Arrays.fill(gap, '=');
        String passage = new String(gap);
        Plan row = new Plan(
                "AAA" + passage + "BBB",
                "AAA" + passage + "BBB",
                "AAA" + passage + "BBB");
        check(found(row, 0, 0) == found(row, 3 + Footings.PASSAGE, 2), "two shops with a passage of made ground between them stand on one footing");
        check(footings.settled && footings.whole, "and are one thing, looked over whole");
        Plan street = new Plan(
                "AAA" + passage + "=BBB",
                "AAA" + passage + "=BBB",
                "AAA" + passage + "=BBB");
        check(found(street, 0, 0) != found(street, 4 + Footings.PASSAGE, 2), "across a street wider than a passage they are two");
        Plan lawn = new Plan(
                "AAA..BBB",
                "AAA..BBB",
                "AAA..BBB");
        check(found(lawn, 0, 0) != found(lawn, 5, 0), "and across a lawn they are two, however narrow it is");
        Plan fringe = new Plan(
                "AAAh==BBB",
                "AAAh==BBB",
                "AAAh==BBB");
        check(found(fringe, 0, 1) == found(fringe, 6, 1), "a sign out over the passage from one of them does not part them");
        check(found(fringe, 3, 1) == found(fringe, 0, 1), "and stands with them");
        Plan edge = new Plan(
                "AAA==?",
                "AAA==?",
                "AAA==?");
        found(edge, 0, 0);
        check(!footings.settled, "a passage that runs on into ground that is not loaded may yet have something built across it");
    }

    private static void aBuildingWithPavingAgainstItStandsWhereThePavingMeetsIt() {
        Plan shop = new Plan(
                "AAAAA",
                "AAAAA",
                "AAAAA",
                "=====",
                "=====");
        long here = found(shop, 0, 0);
        check(Footings.keyX(here) == 2.5f && Footings.keyY(here) == 3.5f, "a shop stands at the height the land has in the middle of the pavement along its front: " + Footings.keyX(here) + ", " + Footings.keyY(here));
        check(footings.free, "at the height the pavement has there with nothing standing, and not at the land's under its own middle");
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 3; y++) {
                check(found(shop, x, y) == here, "whichever square of it asks, " + x + ", " + y);
            }
        }
        Plan across = new Plan(
                "AAAAA",
                "AAAAA",
                "AAAAA",
                "=====",
                "=====",
                "=====",
                "=====",
                "=====",
                "=====",
                "BBBBB",
                "BBBBB",
                "BBBBB");
        long north = found(across, 2, 0), south = found(across, 2, 10);
        check(north != south, "the shop across a street is a thing of its own");
        check(Footings.keyX(north) == Footings.keyX(south) && Footings.keyY(south) - Footings.keyY(north) == 5f,
                "and stands where the same street meets it: the two are the street's width apart and not the depth of both");
        Plan fronted = new Plan(
                "AAAAA",
                "AAAAA",
                "AAAAA",
                "hhhhh",
                "=====");
        here = found(fronted, 2, 1);
        check(Footings.keyX(here) == 2.5f && Footings.keyY(here) == 3.5f, "the pavement under a shop's sign is against the shop: " + Footings.keyX(here) + ", " + Footings.keyY(here));
        Plan house = new Plan(
                "AAAAA",
                "AAAAA",
                "AAAAA",
                ".....");
        check(found(house, 0, 0) == house.houses.get('A'), "a house on its lawn stands at its own footing");
    }

    private static void aBoothOnAPavementIsSetDownOnIt() {
        Plan booth = new Plan(
                "=====",
                "=AA==",
                "=====");
        long here = found(booth, 1, 1);
        check(footings.free, "a booth on a pavement stands at the height the paving has where it is");
        check(Footings.keyX(here) == 2f && Footings.keyY(here) == 1.5f, "round its middle: " + Footings.keyX(here) + ", " + Footings.keyY(here));
        Plan shed = new Plan(
                ".....",
                ".AA..",
                ".....");
        check(found(shed, 1, 1) == shed.houses.get('A') && !footings.free, "a shed on a lawn stands at its own footing, as any building does");
        Plan shelter = new Plan(
                "======",
                "=ssss=",
                "=ssss=",
                "======");
        here = found(shelter, 2, 1);
        check(footings.free && Footings.keyX(here) == 3f && Footings.keyY(here) == 2f, "a canopy on a forecourt stands at the height the forecourt has under its middle: " + Footings.keyX(here) + ", " + Footings.keyY(here));
    }
}

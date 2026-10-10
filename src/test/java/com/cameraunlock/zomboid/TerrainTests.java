package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/** Tests of the height Terrain gives a street and the ground beside it, the hills of the country, and how high the player has them stand. */
final class TerrainTests {
    private static final int SPAN = Terrain.SPAN;
    /** The heights the hills at height 1 gave the land of shown() before HillHeight, with no farmland or woods zoned, as their bits. */
    private static final String AS_IT_WAS = "40139075,400d4fa9,3ff616de,3fdda745,3fdda745,3fdda745,3fe3b897,3fdd20fa,3fd1aa48,3fbcc0e5,3f74639a,3fa0f93a,3fb24599,3fa9ffe7,"
            + "3fa5c257,3fa75e2c,3fa97a4d,3fa7e9f9,3fa44090,3fa38002,3fa7a797,3facbe6e,3face94a,3fa6e388,3f9f628f,3f9bca71,3f9c55aa,3f9c6a00,3f989b1a,3f933c4c,3f9214f0,"
            + "3f97d368,3fa0dc25,3fa73561,3fa911a2,\n403940a1,3f8a65ea,3e9dbe2d,bf4c249a,bee71071,bf3567f0,be002be0,3df3eb0a,be26740e,3fcd89dc,4004a744,3fec1037,3e1ed93c,"
            + "3d6bbc4f,be9c8bd1,";
    private static final float[] HEIGHTS = {0f, 0.35f, 1f, HillHeight.MOST};
    /** Woods from (0, 0) to (6000, 6000) with a town in the middle of them, and farmland drawn as a triangle off to the east. */
    private static final List<Country.Shape> COUNTRY = List.of(
            Country.Shape.rectangle("DeepForest", 0, 0, 6000, 6000),
            Country.Shape.rectangle("TownZone", 2400, 2400, 1200, 1200),
            new Country.Shape("FarmLand", new int[] {8000, 0, 12000, 0, 10000, 4000}));

    /** The lie of the land the test in hand asks. */
    private static Terrain terrain;

    private TerrainTests() {}

    static void run() {
        byte[] around = Terrain.around.clone();
        float[] pads = Terrain.padsAround.clone();
        boolean[] footway = Terrain.footwayAround.clone();
        try {
            Arrays.fill(Terrain.footwayAround, false);
            terrain = Terrain.HILLS;
            streetAlongAHillsideIsLevelFromSideToSide();
            swellsAreSeenFromAStreetAndNoSteeperThanOneInTwenty();
            terrain = Terrain.LEVEL;
            streetWithItsPavementAgainstABuildingIsAtTheBuildingsFooting();
            theHillsAtHeightOneAreTheLandAsItWas();
            theHillsAtNoHeightAreTheLevelLand();
            kerbsBanksAndStreetsStandAsTheyDoAtEveryHeight();
            theFarLandAndTheBuiltLandAgreeAtEveryHeight();
            hillsRiseInFarmlandAndWoodsAndNotInTown();
            everyRiseOfTheLandIsTheHillsHeightTimesItsOwn();
            noHillRisesFromWater();
            theHillsAreAtTheirDefaultWherePlayersCanFight();
            aChangeOfHeightReworksTheLandRoundTheEyeAtOnce();
        } finally {
            System.arraycopy(around, 0, Terrain.around, 0, around.length);
            System.arraycopy(pads, 0, Terrain.padsAround, 0, pads.length);
            System.arraycopy(footway, 0, Terrain.footwayAround, 0, footway.length);
            Terrain.HILLS.heighten(1f);
            Country.set(List.of());
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** Fills the map of the test: made ground up to the street, the street, then open ground, the same in every row. */
    private static void lay(int street, int verge, byte before) {
        Arrays.fill(Terrain.padsAround, Float.NaN);
        for (int i = 0; i < SPAN; i++) {
            for (int j = 0; j < SPAN; j++) {
                Terrain.around[i * SPAN + j] = j < street ? before : j < verge ? Terrain.STREET : Terrain.NATURAL;
            }
        }
    }

    private static void streetAlongAHillsideIsLevelFromSideToSide() {
        // Where the land falls a quarter of a tile across the width of a street that runs east.
        int x = 10724, y = 9725, street = 9, verge = 15, row = SPAN / 2;
        lay(street, verge, Terrain.NATURAL);
        terrain.streets(x, y);
        float kerb = terrain.corner(x + row, y + street, row, street);
        for (int j = street; j <= verge; j++) {
            check(Math.abs(terrain.corner(x + row, y + j, row, j) - kerb) < 1e-5f, "a street along a hillside is as high at one kerb as at the other");
        }
        float along = terrain.corner(x + row + 1, y + street, row + 1, street);
        check(along != kerb && Math.abs(along - kerb) < 0.15f, "a street rises and falls along its length, no steeper than the land");
        float beside = terrain.corner(x + row, y + street - 1, row, street - 1);
        check(Math.abs(beside - kerb) < 0.25f, "the ground beside a street meets it without a step");
    }

    private static void swellsAreSeenFromAStreetAndNoSteeperThanOneInTwenty() {
        float steepest = 0f, lowest = 0f, highest = 0f;
        for (int x = 10000; x < 10600; x++) {
            for (int y = 9000; y < 9600; y++) {
                float here = terrain.swells(x, y);
                steepest = Math.max(steepest, Math.max(Math.abs(terrain.swells(x + 1, y) - here), Math.abs(terrain.swells(x, y + 1) - here)));
                lowest = Math.min(lowest, here);
                highest = Math.max(highest, here);
            }
        }
        check(steepest <= 0.05f, "the land swells no steeper than one in twenty");
        check(steepest > 0.035f && highest > 0.8f && lowest < -0.8f, "the land swells and dips most of a tile either way");
        float crests = 0;
        for (int x = 10000; x < 11000; x++) {
            float before = terrain.swells(x - 1, 9300), here = terrain.swells(x, 9300), after = terrain.swells(x + 1, 9300);
            crests += here > before && here >= after ? 1 : 0;
        }
        check(crests >= 6 && crests <= 10, "a street crosses a crest every hundred paces or so");
    }

    private static void streetWithItsPavementAgainstABuildingIsAtTheBuildingsFooting() {
        // A building, two squares of pavement, a street four squares wide, open ground.
        int pavement = 8, street = 10, verge = 14, row = SPAN / 2;
        lay(street, verge, Terrain.MADE);
        for (int i = 0; i < SPAN; i++) {
            Arrays.fill(Terrain.padsAround, i * SPAN, i * SPAN + pavement, 1f);
        }
        terrain.streets(0, 0);
        check(terrain.corner(0f, 0f, row, pavement) == 1f, "a building's own corner is at the height of its pad");
        // The pavement runs up to the wall, so the street is laid to the building's footing (Paving) and not to the land.
        for (int j = pavement; j <= verge; j++) {
            check(Math.abs(terrain.corner(0f, 0f, row, j) - 1f) < 1e-5f, "a street past a building with its pavement against the wall is at the building's footing from the wall to the far kerb");
        }
        for (int j = street + 1; j <= verge; j++) {
            check(terrain.corner(0f, 0f, row, j) == terrain.corner(0f, 0f, row, street), "and its road is as high at one kerb as at the other");
        }
    }

    /** Open ground, a street, open ground with a line of water across it, about (10724, 9725), as the heights recorded in AS_IT_WAS were laid. */
    private static void layShown() {
        Arrays.fill(Terrain.padsAround, Float.NaN);
        for (int i = 0; i < SPAN; i++) {
            for (int j = 0; j < SPAN; j++) {
                Terrain.around[i * SPAN + j] = j < 9 ? Terrain.NATURAL : j < 15 ? Terrain.STREET : j == 30 ? Terrain.WATER : Terrain.NATURAL;
            }
        }
    }

    /** The corners of a row across layShown's land, every third, then the far land, at a valley's width from water and at 9 tiles, and the water's level, at five places. */
    private static String shown(Terrain on) {
        StringBuilder out = new StringBuilder();
        layShown();
        int x = 10724, y = 9725, row = SPAN / 2;
        on.streets(x, y);
        for (int j = 0; j < SPAN; j += 3) {
            out.append(Integer.toHexString(Float.floatToRawIntBits(on.corner(x + row, y + j, row, j)))).append(',');
        }
        out.append('\n');
        float[][] places = {{10724, 9725}, {6500, 5300}, {12000, 1500}, {8000, 11000}, {3000, 9000}};
        for (float[] place : places) {
            out.append(Integer.toHexString(Float.floatToRawIntBits(on.far(place[0], place[1], Terrain.VALLEY)))).append(',');
            out.append(Integer.toHexString(Float.floatToRawIntBits(on.far(place[0], place[1], 9f)))).append(',');
            out.append(Integer.toHexString(Float.floatToRawIntBits(on.waterLevel(place[0], place[1])))).append(',');
        }
        return out.toString();
    }

    private static void theHillsAtHeightOneAreTheLandAsItWas() {
        Country.set(List.of());
        Terrain.HILLS.heighten(1f);
        check(shown(Terrain.HILLS).equals(AS_IT_WAS), "with the hills at 1 and no farmland or woods, the land is the land as it was to the bit");
    }

    private static void theHillsAtNoHeightAreTheLevelLand() {
        Country.set(COUNTRY);
        Terrain.HILLS.heighten(0f);
        boolean same = true;
        String hills = shown(Terrain.HILLS), level = shown(Terrain.LEVEL);
        String[] a = hills.split("[,\n]+"), b = level.split("[,\n]+");
        for (int i = 0; i < a.length; i++) {
            // Compared as numbers: no land, 0, is no land whichever its sign.
            same &= Float.intBitsToFloat(Integer.parseUnsignedInt(a[i], 16)) == Float.intBitsToFloat(Integer.parseUnsignedInt(b[i], 16));
        }
        check(a.length == b.length && same, "with the hills at 0 the land, the far land and the water are the level land's:\n" + hills + "\n" + level);
        for (int x = 0; x < 6000; x += 97) {
            check(Terrain.HILLS.far(x, 1000f + x / 3f, Terrain.VALLEY) == 0f && Terrain.HILLS.swells(x, 1000f) == 0f,
                    "and the woods have no hill and no swell at " + x);
        }
    }

    private static void kerbsBanksAndStreetsStandAsTheyDoAtEveryHeight() {
        Country.set(COUNTRY);
        int x = 1200, y = 1300, row = SPAN / 2;
        for (float gain : HEIGHTS) {
            Terrain.HILLS.heighten(gain);
            // Open ground falling to water that fills the map from the twentieth row on.
            Arrays.fill(Terrain.padsAround, Float.NaN);
            for (int i = 0; i < SPAN; i++) {
                for (int j = 0; j < SPAN; j++) {
                    Terrain.around[i * SPAN + j] = j < 20 ? Terrain.NATURAL : Terrain.WATER;
                }
            }
            Terrain.HILLS.streets(x, y);
            float edge = Terrain.HILLS.corner(x + row, y + 20, row, 20), water = Terrain.HILLS.base(x + row, y + 20) - Terrain.WATER_BELOW;
            check(Math.abs(edge - water) < 1e-5f, "at " + gain + " the bank comes down to the water's level at the water's edge: " + edge + " and " + water);
            // A street along a hillside, at the land's height down its middle and level across.
            lay(9, 15, Terrain.NATURAL);
            Terrain.HILLS.streets(x, y);
            float kerb = Terrain.HILLS.corner(x + row, y + 9, row, 9);
            boolean level = true;
            for (int j = 9; j <= 15; j++) {
                level &= Math.abs(Terrain.HILLS.corner(x + row, y + j, row, j) - kerb) < 1e-5f;
            }
            check(level, "at " + gain + " a street is as high at one kerb as at the other");
        }
    }

    private static void theFarLandAndTheBuiltLandAgreeAtEveryHeight() {
        Country.set(COUNTRY);
        for (float gain : HEIGHTS) {
            Terrain.HILLS.heighten(gain);
            boolean agree = true, water = true;
            for (int x = 100; x < 12000; x += 211) {
                float y = 500f + x * 0.37f;
                // With no water about and nothing built round it, the land the built world stands on is the far land's.
                agree &= Terrain.HILLS.far(x, y, Terrain.VALLEY) == Terrain.HILLS.base(x, y);
                water &= Math.abs(Terrain.HILLS.waterLevel(x, y) - (Terrain.HILLS.far(x, y, 0f) - Terrain.WATER_BELOW)) < 1e-5f;
            }
            check(agree, "at " + gain + " the far land lies where the built world's land does");
            check(water, "at " + gain + " the water the built world draws lies where the far land's water does, below its land");
        }
    }

    private static void hillsRiseInFarmlandAndWoodsAndNotInTown() {
        Country.set(COUNTRY);
        Terrain.HILLS.heighten(1f);
        check(Country.at(1000f, 1000f) > 0.999f && Country.at(3000f, 3000f) == 0f && Country.at(10000f, 1300f) > 0.999f && Country.at(14000f, 1000f) == 0f
                        && Country.at(-500f, 1000f) == 0f,
                "deep in the woods and in farmland a place is all country, in the middle of a town and where the map zones nothing none: "
                        + Country.at(1000f, 1000f) + " " + Country.at(3000f, 3000f) + " " + Country.at(10000f, 1300f) + " " + Country.at(14000f, 1000f));
        float[] woods = land(200, 2000), town = land(2800, 3200);
        Country.set(List.of());
        float[] bareWoods = land(200, 2000), bareTown = land(2800, 3200);
        Country.set(COUNTRY);
        int hilly = 0;
        float highest = 0f, inTown = 0f;
        for (int at = 0; at < woods.length; at++) {
            float up = woods[at] - bareWoods[at];
            hilly += up > 1f ? 1 : 0;
            highest = Math.max(highest, up);
        }
        for (int at = 0; at < town.length; at++) {
            inTown = Math.max(inTown, Math.abs(town[at] - bareTown[at]));
        }
        float share = hilly / (float) woods.length;
        check(share > 0.3f && highest > 6f, "in the woods a third of the land and more stands a tile and more higher than the same land unzoned, and the highest six tiles and more: "
                + share + ", " + highest);
        check(inTown == 0f, "in the middle of the town the land is as it is unzoned: " + inTown);
        float steepest = 0f;
        for (int x = 200; x < 2000; x += 2) {
            for (int y = 200; y < 2000; y += 2) {
                float here = Terrain.HILLS.base(x, y);
                steepest = Math.max(steepest, (float) Math.hypot(Terrain.HILLS.base(x + 1, y) - here, Terrain.HILLS.base(x, y + 1) - here));
            }
        }
        check(steepest < 0.17f, "and the land of the woods is nowhere steeper than one in six: " + steepest);
    }

    /** The built world's land, before what is uneven on open ground, every fourth tile from (from, from) to (to, to). */
    private static float[] land(int from, int to) {
        int side = (to - from) / 4;
        float[] heights = new float[side * side];
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                heights[i * side + j] = Terrain.HILLS.base(from + i * 4, from + j * 4);
            }
        }
        return heights;
    }

    /** Open ground and a street, with nothing a hand high: every height is gain times its height at 1. */
    private static void everyRiseOfTheLandIsTheHillsHeightTimesItsOwn() {
        Country.set(COUNTRY);
        int x = 1300, y = 1700, row = SPAN / 2;
        lay(9, 15, Terrain.NATURAL);
        Terrain.HILLS.heighten(1f);
        Terrain.HILLS.streets(x, y);
        float[] atOne = new float[SPAN];
        for (int j = 0; j < SPAN; j++) {
            atOne[j] = Terrain.HILLS.corner(x + row, y + j, row, j);
        }
        for (float gain : HEIGHTS) {
            Terrain.HILLS.heighten(gain);
            Terrain.HILLS.streets(x, y);
            float worst = 0f;
            for (int j = 0; j < SPAN; j++) {
                worst = Math.max(worst, Math.abs(Terrain.HILLS.corner(x + row, y + j, row, j) - gain * atOne[j]));
            }
            check(worst < 1e-5f, "at " + gain + " open ground, its knolls and what is uneven underfoot, and the street through it stand " + gain + " times as high: " + worst);
        }
    }

    private static void noHillRisesFromWater() {
        Country.set(COUNTRY);
        for (float gain : HEIGHTS) {
            Terrain.HILLS.heighten(gain);
            boolean low = true;
            for (int x = 200; x < 2000; x += 37) {
                for (int y = 200; y < 2000; y += 41) {
                    // At water the land is the country's rise and fall alone, the hills of the woods as much as any.
                    low &= Math.abs(Terrain.HILLS.far(x, y, 0f) - (Terrain.HILLS.waterLevel(x, y) + Terrain.WATER_BELOW)) < 1e-5f;
                }
            }
            check(low, "at " + gain + " no hill of the woods rises from water");
        }
    }

    private static void theHillsAreAtTheirDefaultWherePlayersCanFight() {
        float asked = HillHeight.asked();
        HillHeight.set(0.3f);
        check(HillHeight.drawn(false) == 0.3f, "where players cannot fight the hills stand as the player set them");
        check(HillHeight.drawn(true) == HillHeight.DEFAULT, "where they can, at the height everyone has who has not moved it");
        HillHeight.set(asked);
    }

    /**
     * A change of the height of the hills has the heights of the eye's chunk and of the eight
     * round it worked out again at once, at the new height, and no others: the rest wait to be
     * asked for, nearest first. Until a chunk's are, the water there is drawn from the land as it
     * was, so the plane the water mirrors in is worked out at the height its chunk was.
     */
    private static void aChangeOfHeightReworksTheLandRoundTheEyeAtOnce() {
        Terrain.HILLS.heighten(1f);
        List<String> worked = new ArrayList<>();
        float eyeX = 1300.5f, eyeY = -12.25f;
        Terrain.Rework<List<String>> record = (list, wx, wy) -> list.add(wx + "," + wy + " at " + Terrain.HILLS.gain());
        check(Terrain.HILLS.heighten(1.5f, 0, eyeX, eyeY, worked, record), "the first view built after the change changes the gain");
        List<String> round = new ArrayList<>();
        for (int wx = 161; wx <= 163; wx++) {
            for (int wy = -3; wy <= -1; wy++) {
                round.add(wx + "," + wy + " at 1.5");
            }
        }
        check(worked.size() == 9 && worked.containsAll(round), "the eye's chunk and the eight round it, and no other, are worked out at once at the new height: " + worked);
        worked.clear();
        check(!Terrain.HILLS.heighten(1.5f, 1, eyeX + 800f, eyeY, worked, record), "the second view, built after it, finds the gain changed already");
        round.replaceAll(chunk -> (Integer.parseInt(chunk.substring(0, chunk.indexOf(','))) + 100) + chunk.substring(chunk.indexOf(',')));
        check(worked.size() == 9 && worked.containsAll(round), "and has the chunks round its own eye worked out at once: " + worked);
        worked.clear();
        Terrain.HILLS.heighten(1.5f, 0, eyeX, eyeY, worked, record);
        Terrain.HILLS.heighten(1.5f, 1, eyeX + 800f, eyeY, worked, record);
        check(worked.isEmpty(), "and neither view's are worked out again until the gain changes again: " + worked);
        Terrain.HILLS.heighten(1f, 0, eyeX, eyeY, worked, record);
        Terrain.HILLS.heighten(1.5f, 0, eyeX, eyeY, worked, record);
        worked.clear();
        Terrain.HILLS.heighten(1.5f, 1, eyeX + 800f, eyeY, worked, record);
        check(worked.size() == 9 && worked.containsAll(round),
                "the gain taken to 1 and back to 1.5 while the second view was not built has its eye's chunks worked out at once when it is: " + worked);
        check(Terrain.HILLS.gainAt(eyeX + 400f, eyeY) == 1.5f, "a chunk never worked out will be at the new height");
        check(Terrain.waterLevel(1.5f, eyeX, eyeY) == 1.5f * (Terrain.waterLevel(1f, eyeX, eyeY) + Terrain.WATER_BELOW) - Terrain.WATER_BELOW,
                "water lies at the height of the hills times the country's rise and fall");
        Terrain.HILLS.heighten(1f);
    }
}

package com.cameraunlock.zomboid;

import java.util.Arrays;

/** Tests of the height Terrain gives a street and the ground beside it. */
final class TerrainTests {
    private static final int SPAN = Terrain.SPAN;

    /** The lie of the land the test in hand asks. */
    private static Terrain terrain;

    private TerrainTests() {}

    static void run() {
        byte[] around = Terrain.around.clone();
        float[] pads = Terrain.padsAround.clone();
        try {
            terrain = Terrain.HILLS;
            streetAlongAHillsideIsLevelFromSideToSide();
            swellsAreSeenFromAStreetAndNoSteeperThanOneInTwenty();
            terrain = Terrain.LEVEL;
            streetPastABuildingIsAsHighOnOneSideAsTheOther();
        } finally {
            System.arraycopy(around, 0, Terrain.around, 0, around.length);
            System.arraycopy(pads, 0, Terrain.padsAround, 0, pads.length);
        }
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
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

    private static void streetPastABuildingIsAsHighOnOneSideAsTheOther() {
        // A building, two squares of pavement, a street four squares wide, open ground.
        int pavement = 8, street = 10, verge = 14, row = SPAN / 2;
        lay(street, verge, Terrain.MADE);
        for (int i = 0; i < SPAN; i++) {
            Arrays.fill(Terrain.padsAround, i * SPAN, i * SPAN + pavement, 1f);
        }
        terrain.streets(0, 0);
        check(terrain.corner(0f, 0f, row, pavement) == 1f, "a building's own corner is at the height of its pad");
        for (int j = street; j <= verge; j++) {
            check(terrain.corner(0f, 0f, row, j) == 0f, "a street past a building a tile above the land is at the land's height from kerb to kerb");
        }
        float between = terrain.corner(0f, 0f, row, pavement + 1);
        check(between > 0.2f && between < 0.8f, "the pavement between a building and a street is graded from one to the other");
    }
}

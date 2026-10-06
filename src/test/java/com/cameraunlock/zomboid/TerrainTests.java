package com.cameraunlock.zomboid;

import java.util.Arrays;

/** Tests of the height Terrain gives a street and the ground beside it. */
final class TerrainTests {
    private static final int SPAN = Terrain.SPAN;

    private TerrainTests() {}

    static void run() {
        streetAlongAHillsideIsLevelFromSideToSide();
        boolean was = Terrain.level;
        Terrain.level = true;
        try {
            streetPastABuildingIsAsHighOnOneSideAsTheOther();
        } finally {
            Terrain.level = was;
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
        Terrain.streets(x, y);
        float kerb = Terrain.corner(x + row, y + street, row, street);
        for (int j = street; j <= verge; j++) {
            check(Math.abs(Terrain.corner(x + row, y + j, row, j) - kerb) < 1e-5f, "a street along a hillside is as high at one kerb as at the other");
        }
        float along = Terrain.corner(x + row + 1, y + street, row + 1, street);
        check(along != kerb && Math.abs(along - kerb) < 0.07f, "a street rises and falls along its length, no steeper than the hills");
        float beside = Terrain.corner(x + row, y + street - 1, row, street - 1);
        check(Math.abs(beside - kerb) < 0.25f, "the ground beside a street meets it without a step");
    }

    private static void streetPastABuildingIsAsHighOnOneSideAsTheOther() {
        // A building, two squares of pavement, a street four squares wide, open ground.
        int pavement = 8, street = 10, verge = 14, row = SPAN / 2;
        lay(street, verge, Terrain.MADE);
        for (int i = 0; i < SPAN; i++) {
            Arrays.fill(Terrain.padsAround, i * SPAN, i * SPAN + pavement, 1f);
        }
        Terrain.streets(0, 0);
        check(Terrain.corner(0f, 0f, row, pavement) == 1f, "a building's own corner is at the height of its pad");
        for (int j = street; j <= verge; j++) {
            check(Terrain.corner(0f, 0f, row, j) == 0f, "a street past a building a tile above the land is at the land's height from kerb to kerb");
        }
        float between = Terrain.corner(0f, 0f, row, pavement + 1);
        check(between > 0.2f && between < 0.8f, "the pavement between a building and a street is graded from one to the other");
    }
}

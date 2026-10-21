package com.cameraunlock.zomboid;

final class SkinCutsTests {
    private SkinCutsTests() {}

    static void run() {
        Checks.run("theSkirtIsThePieceHungOnTheMiddleLine", SkinCutsTests::theSkirtIsThePieceHungOnTheMiddleLine);
        Checks.run("theSkirtIsCutAndTheBodyKept", SkinCutsTests::theSkirtIsCutAndTheBodyKept);
        Checks.run("aSkinIsToldByItsName", SkinCutsTests::aSkinIsToldByItsName);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** A leg off the middle line, and beside it a sheet with a vertex on the line, low down, sharing the leg's place at its top. */
    private static final float[] PLACES = {
        0.10f, 0.50f, 0f, 0.20f, 0.50f, 0f, 0.10f, 0.10f, 0f,
        0.10f, 0.50f, 0f, 0.00f, 0.10f, 0f, 0.10f, 0.10f, 0f,
    };
    private static final int[] CORNERS = {0, 1, 2, 3, 4, 5};
    /** The leg on the left half of the picture, the sheet on the right half. */
    private static final float[] UVS = {
        0.0f, 0.0f, 0.5f, 0.0f, 0.0f, 1.0f,
        0.5f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f,
    };

    private static void theSkirtIsThePieceHungOnTheMiddleLine() {
        boolean[] skirt = SkinCuts.skirt(PLACES, CORNERS);
        check(!skirt[0] && skirt[1], "the sheet is the skirt and the leg is not, though they share a place");
    }

    private static void theSkirtIsCutAndTheBodyKept() {
        int size = 64;
        byte[] cut = SkinCuts.cut(UVS, CORNERS, SkinCuts.skirt(PLACES, CORNERS), size, size);
        check(cut[10 * size + 60] == 0, "a dot inside the sheet is cut");
        check(cut[10 * size + 5] == (byte) 255, "a dot inside the leg shows");
        check(cut[60 * size + 20] == (byte) 255, "a dot on neither shows");
        // The sheet's slack reaches a dot past its long edge, from (32, 0) to (64, 64), over its outside.
        check(cut[size + 32] == 0, "the dot just outside the sheet's edge is cut too");
        check(cut[3 * size + 32] == (byte) 255, "a dot further outside it shows");
    }

    private static void aSkinIsToldByItsName() {
        check(SkinCuts.bodyOf("/skins/body/m_zedbody03_level2.jpg") == SkinCuts.MALE && SkinCuts.bodyOf("/skins/body/malebody04a.jpg") == SkinCuts.MALE,
                "a man's skins, zombie and living, are the male body's");
        check(SkinCuts.bodyOf("/skins/body/f_zedbody01.jpg") == SkinCuts.FEMALE && SkinCuts.bodyOf("/skins/body/femalebody05.jpg") == SkinCuts.FEMALE,
                "a woman's skins are the female body's");
        check(SkinCuts.bodyOf("/skins/body/cow_black.jpg") == SkinCuts.NO_BODY && SkinCuts.bodyOf("/skins/shirtdecals/pizza.jpg") == SkinCuts.NO_BODY,
                "an animal's skin and a print are no person's");
    }
}

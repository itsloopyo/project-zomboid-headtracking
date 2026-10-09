package com.cameraunlock.zomboid;

/** Tests of which sprites are the halves of a swag of lights, of the cord the two halves hang as, and of where its bulbs hang. */
final class LightsTests {
    private LightsTests() {}

    static void run() {
        halfIsToldByItsKindItsFacingAndItsPlaceInThePiece();
        whatIsNoStringOfLightsIsNone();
        twoHalvesAreOneSwagBetweenSupportsTwoSquaresApart();
        halfWithNoOtherHalfStillHangsFromItsSupportToTheMiddle();
        cordOfTheTwoHalvesIsOneCurve();
        everyBulbHangsOnOneHalfAndUnderTheCord();
        rainbowStringHasABulbOfEachColourAndTheRestAreClear();
        stringIsMadeFastAtTheCornerPostItsSwagEndsAt();
    }

    private static int large(boolean south, boolean second) {
        return Lights.sliceOf("Lights", "Large", south ? "E" : "S", !second ? "0,0" : south ? "0,1" : "1,0");
    }

    private static void halfIsToldByItsKindItsFacingAndItsPlaceInThePiece() {
        String[] kinds = {"Large", "Small", "Rainbow"};
        for (int kind = Lights.LARGE; kind <= Lights.RAINBOW; kind++) {
            int east = Lights.sliceOf("Lights", kinds[kind], "S", "0,0"), eastOn = Lights.sliceOf("Lights", kinds[kind], "S", "1,0");
            int south = Lights.sliceOf("Lights", kinds[kind], "E", "0,0"), southOn = Lights.sliceOf("Lights", kinds[kind], "E", "0,1");
            check(Lights.kind(east) == kind && Lights.kind(eastOn) == kind && Lights.kind(south) == kind && Lights.kind(southOn) == kind, kinds[kind] + " is its own kind");
            check(!Lights.runsSouth(east) && !Lights.runsSouth(eastOn) && Lights.runsSouth(south) && Lights.runsSouth(southOn),
                    kinds[kind] + ": one that faces south runs east, one that faces east runs south");
            check(!Lights.second(east) && Lights.second(eastOn) && !Lights.second(south) && Lights.second(southOn), kinds[kind] + ": the half further along its piece is the second");
        }
    }

    private static void whatIsNoStringOfLightsIsNone() {
        check(Lights.sliceOf("Bunting", "Large", "S", "0,0") == Lights.NONE, "bunting");
        check(Lights.sliceOf("Lights", "Flood", "S", null) == Lights.NONE, "a floodlight, which the game also calls Lights");
        check(Lights.sliceOf("Lights", "Lamp on", null, null) == Lights.NONE, "a lamp, which the game also calls Lights");
        check(Lights.sliceOf("Lights", "Large", "N", "0,0") == Lights.NONE, "a string that faces a way none does");
        check(Lights.sliceOf("Lights", "Large", "S", "0,1") == Lights.NONE, "a half where a string that runs east has none");
        check(Lights.sliceOf(null, null, null, null) == Lights.NONE, "a sprite with no name");
    }

    private static void twoHalvesAreOneSwagBetweenSupportsTwoSquaresApart() {
        float[] first = new float[Spans.PIECE], second = new float[Spans.PIECE];
        Lights.span(large(true, false), 10, 20, first);
        Lights.span(large(true, true), 10, 21, second);
        for (int number = Spans.FROM; number < Spans.PIECE; number++) {
            check(first[number] == second[number], "both halves of a swag that runs south have the same span: number " + number);
        }
        check(near(first[Spans.FROM], 10f + Lights.OFF) && near(first[Spans.FROM + 1], 20f) && near(first[Spans.TO], 10f + Lights.OFF) && near(first[Spans.TO + 1], 22f),
                "a swag that runs south hangs inside the west edge from the corner of its first square to two squares south");
        check(near(first[3], 21f) && near(second[1], 21f) && near(first[2], second[0]), "the two halves meet in the middle");
        check(near(Spans.span(first, 0), Lights.SPAN), "the supports are two squares apart");
        check(Spans.swing(first, 0) == Spans.swing(second, 0), "the two halves swing as one");
        Lights.span(large(false, false), 10, 20, first);
        Lights.span(large(false, true), 11, 20, second);
        check(near(first[Spans.FROM], 10f) && near(first[Spans.FROM + 1], 20f + Lights.OFF) && near(second[Spans.TO], 12f) && near(second[Spans.TO + 1], 20f + Lights.OFF),
                "a swag that runs east hangs inside the north edge from the corner of its first square to two squares east");
        check(first[Spans.FROM + Spans.END_KIND] == Spans.FAST && first[Spans.TO + Spans.END_KIND] == Spans.FAST && !Spans.wanting(first), "both ends are made fast");
    }

    private static void halfWithNoOtherHalfStillHangsFromItsSupportToTheMiddle() {
        float[] alone = new float[Spans.PIECE];
        Lights.span(large(false, true), 31, 7, alone);
        check(near(alone[0], 31f) && near(alone[2], 32f) && near(alone[Spans.FROM], 30f) && near(alone[Spans.TO], 32f),
                "a second half is the far half of a swag that starts a square before it, whatever is on that square");
        check(near(Spans.along(alone, 0, alone[0], alone[1]), 0.5f) && near(Spans.along(alone, 0, alone[2], alone[3]), 1f), "it hangs from the middle up to its support");
    }

    private static void cordOfTheTwoHalvesIsOneCurve() {
        float[] first = new float[Spans.PIECE], second = new float[Spans.PIECE];
        Lights.span(large(false, false), 0, 0, first);
        Lights.span(large(false, true), 1, 0, second);
        float sag = Lights.SAG * ViewCamera.LEVEL_HEIGHT, before = -1f;
        for (int length = 0; length <= 2 * Lights.LENGTHS; length++) {
            float[] half = length <= Lights.LENGTHS ? first : second;
            float share = (length <= Lights.LENGTHS ? length : length - Lights.LENGTHS) / (float) Lights.LENGTHS;
            float hangs = Spans.hangs(Spans.along(half, 0, half[0] + (half[2] - half[0]) * share, half[1]), sag);
            check(length <= Lights.LENGTHS ? hangs > before : hangs < before, "the cord goes down to the middle and up again: length " + length);
            before = hangs;
        }
        check(near(before, 0f), "it is at its support's height where it ends");
        check(near(Spans.hangs(Spans.along(first, 0, first[2], first[3]), sag), sag) && near(Spans.hangs(Spans.along(second, 0, second[0], second[1]), sag), sag),
                "both halves are at the swag's lowest where they meet");
        check((Lights.HELD - Lights.SAG) * ViewCamera.LEVEL_HEIGHT > 1.9f, "the lowest of the cord is over a head");
    }

    private static void everyBulbHangsOnOneHalfAndUnderTheCord() {
        for (int kind = Lights.LARGE; kind <= Lights.RAINBOW; kind++) {
            int first = kind * 4, second = kind * 4 + 1, onFirst = 0;
            float before = 0f;
            for (int bulb = 0; bulb < Lights.bulbs(kind); bulb++) {
                float place = Lights.place(kind, bulb);
                check(place > before && place < Lights.SPAN, "bulbs hang in order between the supports: kind " + kind + " bulb " + bulb);
                check(place - before > Lights.size(kind) || bulb == 0, "no bulb hangs inside the one before: kind " + kind + " bulb " + bulb);
                check(Lights.on(first, bulb) != Lights.on(second, bulb), "a bulb is on one half: kind " + kind + " bulb " + bulb);
                onFirst += Lights.on(first, bulb) ? 1 : 0;
                before = place;
            }
            check(onFirst > 0 && onFirst < Lights.bulbs(kind), "both halves have bulbs: kind " + kind);
            check(Lights.size(kind) * 2f < Lights.OFF, "a bulb is clear of the wall its cord hangs along: kind " + kind);
        }
        check(Lights.HOLDER[0] == 0f && Lights.GLASS[Lights.GLASS.length - 1] == 0f, "a bulb starts at its cord and is closed underneath");
        check(Lights.GLASS[0] < Lights.HOLDER[Lights.HOLDER.length - 2], "the glass comes out of the holder");
        for (int ring = 2; ring < Lights.GLASS.length; ring += 2) {
            check(Lights.GLASS[ring] > Lights.GLASS[ring - 2] && Lights.GLASS[ring + 1] <= 1f, "the glass is turned from the top down and is no wider than its size");
        }
    }

    private static void rainbowStringHasABulbOfEachColourAndTheRestAreClear() {
        for (int bulb = 1; bulb < Lights.bulbs(Lights.RAINBOW); bulb++) {
            boolean other = false;
            for (int part = 0; part < 3; part++) {
                other |= Math.abs(Lights.colour(Lights.RAINBOW, bulb, true, part) - Lights.colour(Lights.RAINBOW, bulb - 1, true, part)) > 0.05f;
                check(Lights.colour(Lights.RAINBOW, bulb, true, part) > Lights.colour(Lights.RAINBOW, bulb, false, part), "a bulb is brighter burning: bulb " + bulb);
            }
            check(other, "a rainbow bulb is another colour than the one before it: bulb " + bulb);
        }
        for (int kind = Lights.LARGE; kind <= Lights.SMALL; kind++) {
            for (int part = 0; part < 3; part++) {
                check(Lights.colour(kind, 0, false, part) == Lights.colour(kind, Lights.bulbs(kind) - 1, false, part), "clear bulbs are all one colour");
            }
            check(Lights.colour(kind, 0, false, 0) == Lights.colour(kind, 0, false, 2), "clear glass with the lamp out has no colour");
            check(Lights.colour(kind, 0, true, 0) > Lights.colour(kind, 0, true, 2), "a burning lamp is warm");
        }
    }

    private static void stringIsMadeFastAtTheCornerPostItsSwagEndsAt() {
        check(Lights.fastAt(large(true, false), 0, 0) && Lights.fastAt(large(false, false), 0, 0), "a first half starts at its own square's corner");
        check(Lights.fastAt(large(true, true), 0, -1) && !Lights.fastAt(large(true, true), 0, 0) && !Lights.fastAt(large(true, true), -1, 0),
                "a second half that runs south ends at the corner of the square south of it");
        check(Lights.fastAt(large(false, true), -1, 0) && !Lights.fastAt(large(false, true), 0, 0) && !Lights.fastAt(large(false, true), 0, -1),
                "a second half that runs east ends at the corner of the square east of it");
        check(!Lights.fastAt(large(true, false), 0, -1) && !Lights.fastAt(large(false, false), -1, 0), "a first half is not made fast where it meets its second");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }
}

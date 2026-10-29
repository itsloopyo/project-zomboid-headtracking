package com.cameraunlock.zomboid;

/** Tests of which sprites are the halves of a swag of lights, of the cord the two halves hang as, and of where its bulbs hang. */
final class LightsTests {
    private LightsTests() {}

    static void run() {
        Checks.run("halfIsToldByItsKindItsFacingAndItsPlaceInThePiece", LightsTests::halfIsToldByItsKindItsFacingAndItsPlaceInThePiece);
        Checks.run("halfIsReadOffTheSpritesOwnProperties", LightsTests::halfIsReadOffTheSpritesOwnProperties);
        Checks.run("whatIsNoStringOfLightsIsNone", LightsTests::whatIsNoStringOfLightsIsNone);
        Checks.run("twoHalvesAreOneSwagBetweenSupportsTwoSquaresApart", LightsTests::twoHalvesAreOneSwagBetweenSupportsTwoSquaresApart);
        Checks.run("halfWithNoOtherHalfStillHangsFromItsSupportToTheMiddle", LightsTests::halfWithNoOtherHalfStillHangsFromItsSupportToTheMiddle);
        Checks.run("cordOfTheTwoHalvesIsOneCurve", LightsTests::cordOfTheTwoHalvesIsOneCurve);
        Checks.run("everyBulbHangsOnOneHalfClearOfTheNext", LightsTests::everyBulbHangsOnOneHalfClearOfTheNext);
        Checks.run("bulbHangsFromItsCordAndUnderIt", LightsTests::bulbHangsFromItsCordAndUnderIt);
        Checks.run("bulbIsTurnedRoundThePlumbLineAndSwingsWithItsCord", LightsTests::bulbIsTurnedRoundThePlumbLineAndSwingsWithItsCord);
        Checks.run("farBulbIsTheSameBulbInFarFewerTrianglesAndFurtherOffThereIsNone", LightsTests::farBulbIsTheSameBulbInFarFewerTrianglesAndFurtherOffThereIsNone);
        Checks.run("postHoldsTheHalvesWhoseCordEndsAtItsCorner", LightsTests::postHoldsTheHalvesWhoseCordEndsAtItsCorner);
        Checks.run("rainbowStringHasABulbOfEachColourAndTheRestAreClear", LightsTests::rainbowStringHasABulbOfEachColourAndTheRestAreClear);
        Checks.run("stringIsMadeFastAtTheCornerPostItsSwagEndsAt", LightsTests::stringIsMadeFastAtTheCornerPostItsSwagEndsAt);
        Checks.run("bulbsAreWholeNearPlainerFurtherOffAndGoneOnlyFromAStringThatIsOut", LightsTests::bulbsAreWholeNearPlainerFurtherOffAndGoneOnlyFromAStringThatIsOut);
        Checks.run("wholeBulbsHolderIsClosedOnTop", LightsTests::wholeBulbsHolderIsClosedOnTop);
    }

    private static int large(boolean south, boolean second) {
        return Lights.sliceOf("Lights", "Large", south ? "E" : "S", !second ? "0,0" : south ? "0,1" : "1,0");
    }

    /** As the build asks it of a sprite, with the properties of the tiles' own rows. */
    private static void halfIsReadOffTheSpritesOwnProperties() {
        String row = "|CustomName|Lights|Facing|S|ForceSingleItem||HasLightOnSprite||IsHigh||IsMoveAble||LightRadius|3|PickUpWeight|10|exterior||lightswitch||GroupName|";
        check(Lights.slice(TileRows.sprite("walls_decoration_01_96" + row + "Large|SpriteGridPos|0,0")) == large(false, false), "the first half of a large string is told off its sprite");
        check(Lights.slice(TileRows.sprite("walls_decoration_01_97" + row + "Large|SpriteGridPos|1,0")) == large(false, true), "and the second");
        int small = Lights.slice(TileRows.sprite("walls_decoration_01_88" + row + "Small|SpriteGridPos|0,0"));
        int rainbow = Lights.slice(TileRows.sprite("walls_decoration_01_76" + row + "Rainbow|SpriteGridPos|0,0"));
        check(small != Lights.NONE && Lights.kind(small) == Lights.SMALL && rainbow != Lights.NONE && Lights.kind(rainbow) == Lights.RAINBOW, "small and rainbow strings are told by their group");
        check(Lights.slice(TileRows.sprite("lighting_indoor_01_58|CustomName|Lights|GroupName|Lamp on|lightswitch|")) == Lights.NONE, "a lamp of the same name is no string");
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

    private static final float SAG = Lights.SAG * ViewCamera.LEVEL_HEIGHT;

    /** The ends of the straight lengths a swag's cord is hung as, the first half's and then the second's, as Lights.cordEnd gives them: 2 * LENGTHS + 2 of them. */
    private static float[][] cord(boolean south, int x, int y) {
        float[] piece = new float[Spans.PIECE];
        float[][] ends = new float[2 * Lights.LENGTHS + 2][Lights.NUMBERS];
        for (int half = 0; half < 2; half++) {
            Lights.span(large(south, half == 1), x + (south ? 0 : half), y + (south ? half : 0), piece);
            for (int end = 0; end <= Lights.LENGTHS; end++) {
                Lights.cordEnd(piece, end, SAG, ends[half * (Lights.LENGTHS + 1) + end]);
            }
        }
        return ends;
    }

    private static void cordOfTheTwoHalvesIsOneCurve() {
        int lengths = Lights.LENGTHS;
        for (int way = 0; way < 2; way++) {
            boolean south = way == 1;
            float[][] ends = cord(south, 10, 20);
            for (int number = 0; number < Lights.NUMBERS; number++) {
                check(near(ends[lengths][number], ends[lengths + 1][number]), "the second half starts where the first stops: number " + number);
            }
            for (int end = 0; end < ends.length; end++) {
                int step = end <= lengths ? end : end - 1;
                float share = step / (2f * lengths), along = (south ? 20f : 10f) + Lights.SPAN * share, across = (south ? 10f : 20f) + Lights.OFF;
                check(near(ends[end][south ? Lights.Y : Lights.X], along) && near(ends[end][south ? Lights.X : Lights.Y], across),
                        "the ends are evenly spaced on the line between the supports: end " + end);
                check(near(ends[end][Lights.ALONG], share), "an end is as far along the swag as it is from the first support: end " + end);
                check(near(ends[end][Lights.HANGS], ends[ends.length - 1 - end][Lights.HANGS]), "the cord hangs alike either side of its middle: end " + end);
                check(end == 0 || end == lengths + 1 || (end <= lengths ? ends[end][Lights.HANGS] > ends[end - 1][Lights.HANGS] : ends[end][Lights.HANGS] < ends[end - 1][Lights.HANGS]),
                        "the cord goes down to the middle and up again: end " + end);
            }
            check(ends[0][Lights.HANGS] == 0f && ends[ends.length - 1][Lights.HANGS] == 0f, "it is at the height of its supports where it is made fast");
            check(near(ends[lengths][Lights.HANGS], SAG), "it hangs its whole sag where the halves meet");
        }
        check((Lights.HELD - Lights.SAG) * ViewCamera.LEVEL_HEIGHT > 1.9f, "the lowest of the cord is over a head");
    }

    private static float widest(float[] profile) {
        float widest = 0f;
        for (int ring = 1; ring < profile.length; ring += 2) {
            widest = Math.max(widest, profile[ring]);
        }
        return widest;
    }

    private static void everyBulbHangsOnOneHalfClearOfTheNext() {
        for (int kind = Lights.LARGE; kind <= Lights.RAINBOW; kind++) {
            int first = kind * 4, second = kind * 4 + 1, onFirst = 0;
            float before = 0f, across = 2f * Lights.size(kind) * widest(Lights.GLASS);
            for (int bulb = 0; bulb < Lights.bulbs(kind); bulb++) {
                float place = Lights.place(kind, bulb);
                check(place > before && place < Lights.SPAN, "bulbs hang in order between the supports: kind " + kind + " bulb " + bulb);
                check(place - before > across || bulb == 0, "a bulb hangs clear of the one before by more than a bulb is across: kind " + kind + " bulb " + bulb);
                check(Lights.on(first, bulb) != Lights.on(second, bulb), "a bulb is on one half: kind " + kind + " bulb " + bulb);
                onFirst += Lights.on(first, bulb) ? 1 : 0;
                before = place;
            }
            check(onFirst > 0 && onFirst < Lights.bulbs(kind), "both halves have bulbs: kind " + kind);
            check(Lights.size(kind) * 2f < Lights.OFF, "a bulb is clear of the wall its cord hangs along: kind " + kind);
        }
        check(Lights.GLASS[0] < Lights.HOLDER[Lights.HOLDER.length - 2], "the glass comes out of the holder");
        for (int ring = 2; ring < Lights.GLASS.length; ring += 2) {
            check(Lights.GLASS[ring] > Lights.GLASS[ring - 2] && Lights.GLASS[ring + 1] <= 1f, "the glass is turned from the top down and is no wider than its size");
        }
    }

    /**
     * Where each bulb is hung, as Lights.bulb gives it, against the cord as Lights.cordEnd hangs
     * it: the straight length over the bulb is found among the ends, and the top of the bulb
     * has to be inside the cord there, which is WIRE_THICK through.
     */
    private static void bulbHangsFromItsCordAndUnderIt() {
        float[] piece = new float[Spans.PIECE], at = new float[Lights.NUMBERS];
        for (int kind = Lights.LARGE; kind <= Lights.RAINBOW; kind++) {
            for (int way = 0; way < 2; way++) {
                boolean south = way == 1;
                float[][] ends = cord(south, 10, 20);
                for (int half = 0; half < 2; half++) {
                    int slice = kind * 4 + (south ? 2 : 0) + half;
                    Lights.span(slice, south ? 10 : 10 + half, south ? 20 + half : 20, piece);
                    for (int bulb = 0; bulb < Lights.bulbs(kind); bulb++) {
                        if (!Lights.on(slice, bulb)) {
                            continue;
                        }
                        String which = "kind " + kind + (south ? " south" : " east") + " half " + half + " bulb " + bulb;
                        Lights.bulb(piece, kind, bulb, SAG, at);
                        int from = half * (Lights.LENGTHS + 1);
                        while (from < half * (Lights.LENGTHS + 1) + Lights.LENGTHS - 1 && ends[from + 1][Lights.ALONG] < at[Lights.ALONG]) {
                            from++;
                        }
                        float[] high = ends[from], low = ends[from + 1];
                        check(at[Lights.ALONG] >= high[Lights.ALONG] - 1e-5f && at[Lights.ALONG] <= low[Lights.ALONG] + 1e-5f,
                                "a bulb on a half hangs under the cord of that half: " + which);
                        float share = (at[Lights.ALONG] - high[Lights.ALONG]) / (low[Lights.ALONG] - high[Lights.ALONG]);
                        check(near(at[Lights.X], high[Lights.X] + (low[Lights.X] - high[Lights.X]) * share) && near(at[Lights.Y], high[Lights.Y] + (low[Lights.Y] - high[Lights.Y]) * share),
                                "a bulb is on the line its cord runs along: " + which);
                        float below = at[Lights.HANGS] - (high[Lights.HANGS] + (low[Lights.HANGS] - high[Lights.HANGS]) * share);
                        check(below >= -1e-5f && below < WorldScene.WIRE_THICK / 2f, "the top of a bulb is inside its cord, not over it nor hanging clear below: " + which + ", " + below);
                    }
                }
            }
        }
    }

    private static void bulbIsTurnedRoundThePlumbLineAndSwingsWithItsCord() {
        float x = 3.25f, y = 7.5f, up = 2.2f, hangs = 0.11f, acrossX = 0.6f, acrossY = -1.3f;
        for (int kind = Lights.LARGE; kind <= Lights.RAINBOW; kind++) {
            // Holder and glass, near the eye and far from it.
            for (int shape = 0; shape < 4; shape++) {
                boolean far = shape >= 2, glass = shape % 2 == 1;
                float[] profile = glass ? Lights.glass(far) : Lights.holder(far);
                int sides = Lights.sides(kind, far);
                check(glass ? profile[profile.length - 1] == 0f : profile[0] == 0f, "a holder starts at its cord and the glass is closed underneath: shape " + shape);
                SpriteGeometry.Mesh mesh = LightSiteTests.constant(new SpriteGeometry.Mesh());
                float size = Lights.size(kind), deepest = profile[profile.length - 2] * size;
                Lights.round(mesh, x, y, up, hangs, acrossX, acrossY, profile, size, sides, 0.2f, 0.4f, 0.6f);
                int closing = (profile[1] == 0f ? 1 : 0) + (profile[profile.length - 1] == 0f ? 1 : 0);
                check(mesh.triangles() == sides * (2 * (profile.length / 2 - 1) - closing),
                        "two triangles a side a ring, and one where the ring closes on the plumb line: kind " + kind + ", " + mesh.triangles());
                for (int at = 0; at < mesh.floats; at += 3 * SpriteGeometry.VERTEX_FLOATS) {
                    float[] v = mesh.vertices;
                    int second = at + SpriteGeometry.VERTEX_FLOATS, third = second + SpriteGeometry.VERTEX_FLOATS;
                    float ax = v[second] - v[at], ay = v[second + 1] - v[at + 1], az = v[second + 2] - v[at + 2];
                    float bx = v[third] - v[at], by = v[third + 1] - v[at + 1], bz = v[third + 2] - v[at + 2];
                    double twice = Math.sqrt(Math.pow(ay * bz - az * by, 2) + Math.pow(az * bx - ax * bz, 2) + Math.pow(ax * by - ay * bx, 2));
                    check(twice > 1e-7, "no triangle has two corners in one place: kind " + kind + ", triangle " + at / (3 * SpriteGeometry.VERTEX_FLOATS));
                }
                float lowest = up, widest = 0f;
                for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                    float[] v = mesh.vertices;
                    float out = (float) Math.hypot(v[at] - x, v[at + 1] - y), deep = up - v[at + 2];
                    check(deep >= 0f && deep <= deepest + 1e-5f, "every vertex is under the cord, no deeper than the profile: kind " + kind + ", " + deep);
                    check(out <= size * widest(profile) + 1e-5f, "no vertex is further from the plumb line than the profile is wide: kind " + kind + ", " + out);
                    float length = (float) Math.sqrt(v[at + 9] * v[at + 9] + v[at + 10] * v[at + 10] + v[at + 11] * v[at + 11]);
                    check(Math.abs(length - 1f - (hangs + deep + Lights.HUNG_LEAST)) < 1e-5f,
                            "a vertex hangs below the line by what its cord does there and its own depth under the cord: kind " + kind + ", " + (length - 1f));
                    check(v[at + 9] * (v[at] - x) + v[at + 10] * (v[at + 1] - y) >= -1e-6f, "a vertex faces away from the plumb line: kind " + kind);
                    check(v[at + 3] == acrossX && v[at + 4] == acrossY, "a vertex carries the way across its cord, which it swings by");
                    lowest = Math.min(lowest, v[at + 2]);
                    widest = Math.max(widest, out);
                }
                check(near(up - lowest, deepest) && near(widest, size * widest(profile)), "the profile is turned whole: kind " + kind);
            }
        }
    }

    private static int triangles(int kind, boolean far) {
        SpriteGeometry.Mesh mesh = LightSiteTests.constant(new SpriteGeometry.Mesh());
        Lights.round(mesh, 0f, 0f, 0f, 0f, 1f, 0f, Lights.holder(far), Lights.size(kind), Lights.sides(kind, far), 1f, 1f, 1f);
        Lights.round(mesh, 0f, 0f, 0f, 0f, 1f, 0f, Lights.glass(far), Lights.size(kind), Lights.sides(kind, far), 1f, 1f, 1f);
        return mesh.triangles();
    }

    private static void farBulbIsTheSameBulbInFarFewerTrianglesAndFurtherOffThereIsNone() {
        float[] near = Lights.glass(false), far = Lights.glass(true), nearHolder = Lights.holder(false), farHolder = Lights.holder(true);
        check(far[far.length - 2] == near[near.length - 2] && far[0] == near[0], "a far bulb hangs as deep under its cord as a near one, from as far under it");
        check(Math.abs(widest(far) - widest(near)) < 0.02f && Math.abs(widest(farHolder) - widest(nearHolder)) < 0.03f, "and is as thick, glass and holder");
        check(farHolder[farHolder.length - 2] == nearHolder[nearHolder.length - 2], "with a holder as long");
        for (int kind = Lights.LARGE; kind <= Lights.RAINBOW; kind++) {
            check(Lights.sides(kind, true) >= 3 && triangles(kind, true) * 3 <= triangles(kind, false),
                    "a far bulb is a third of the triangles or fewer: kind " + kind + ", " + triangles(kind, true) + " of " + triangles(kind, false));
        }
        // A chunk is built for the distance of its band, which is all WorldScene.addLights asks: each of the three ways to hang a string needs a band that gets it.
        int whole = 0, simple = 0, bare = 0;
        for (float away : WorldScene.BAND_AWAY) {
            whole += away < Lights.FINE_WITHIN ? 1 : 0;
            simple += away >= Lights.FINE_WITHIN && away < Lights.BULBS_WITHIN ? 1 : 0;
            bare += away >= Lights.BULBS_WITHIN ? 1 : 0;
        }
        check(whole > 0 && simple > 0 && bare > 0, "some bands have whole bulbs, some far ones and some none: " + whole + ", " + simple + ", " + bare);
    }

    /**
     * A corner post holds a half whose own cord ends at a support of its swag on the corner
     * the post stands on. Where the cord and supports of every half are is asked of
     * Lights.span, for the halves on every square round the post, and set against what fastAt
     * says and against the squares a post looks on.
     */
    private static void postHoldsTheHalvesWhoseCordEndsAtItsCorner() {
        float[] piece = new float[Spans.PIECE];
        int postX = 40, postY = 60;
        boolean[] looked = new boolean[Lights.HELD_FROM_X.length];
        for (int slice = 0; slice < 4; slice++) {
            boolean south = Lights.runsSouth(slice), second = Lights.second(slice);
            for (int alongX = -3; alongX <= 3; alongX++) {
                for (int alongY = -3; alongY <= 3; alongY++) {
                    Lights.span(slice, postX + alongX, postY + alongY, piece);
                    int end = second ? 2 : 0, support = second ? Spans.TO : Spans.FROM;
                    check(near(piece[end], piece[support]) && near(piece[end + 1], piece[support + 1]), "the cord of a half ends at a support of its swag");
                    boolean atCorner = near(piece[end], postX + (south ? Lights.OFF : 0f)) && near(piece[end + 1], postY + (south ? 0f : Lights.OFF));
                    String which = "slice " + slice + " on the square " + alongX + ", " + alongY + " from the one the post is on";
                    check(Lights.fastAt(slice, alongX, alongY) == atCorner, "a half is made fast at the corner a post stands on when its cord ends there: " + which);
                    int among = -1;
                    for (int beside = 0; beside < looked.length; beside++) {
                        if (Lights.HELD_FROM_X[beside] == alongX && Lights.HELD_FROM_Y[beside] == alongY) {
                            among = beside;
                        }
                    }
                    check(!atCorner || among >= 0, "a post looks on every square a half it holds may be on: " + which);
                    if (atCorner) {
                        looked[among] = true;
                    }
                }
            }
        }
        for (int beside = 0; beside < looked.length; beside++) {
            check(looked[beside], "a post looks on no square that no half it holds can be on: " + Lights.HELD_FROM_X[beside] + ", " + Lights.HELD_FROM_Y[beside]);
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

    private static void bulbsAreWholeNearPlainerFurtherOffAndGoneOnlyFromAStringThatIsOut() {
        check(Lights.made(true, true, false) == Lights.WHOLE && Lights.made(true, true, true) == Lights.WHOLE, "near the eye a bulb is whole, burning or out");
        check(Lights.made(false, true, false) == Lights.FAR && Lights.made(false, true, true) == Lights.FAR, "further off a bulb is plainer, burning or out");
        check(Lights.made(false, false, false) == Lights.BARE, "furthest off a string that is out hangs bare");
        check(Lights.made(false, false, true) == Lights.FAR, "furthest off a string that burns keeps its bulbs");
    }

    private static void wholeBulbsHolderIsClosedOnTop() {
        check(Lights.holder(false)[0] == 0f && Lights.holder(false)[1] == 0f, "a whole bulb's holder is closed on top");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }
}

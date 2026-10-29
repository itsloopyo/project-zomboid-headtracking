package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.Arrays;
import java.util.Random;

/** Tests of the light sites a build's vertices take their light from, and of the twin that checks each vertex against its site: see LightSites. */
final class LightSiteTests {
    private static final int RECORD = LightSites.RECORD;

    private LightSiteTests() {}

    static void run() {
        Checks.run("theSuiteChecksEveryVertexAgainstItsTwin", LightSiteTests::theSuiteChecksEveryVertexAgainstItsTwin);
        Checks.run("aSiteIsKeptOnceWithTheLightItWasPutWith", LightSiteTests::aSiteIsKeptOnceWithTheLightItWasPutWith);
        Checks.run("aSitePutAgainInOtherLightThrows", LightSiteTests::aSitePutAgainInOtherLightThrows);
        Checks.run("aVertexWhoseLightIsNotSaidThrows", LightSiteTests::aVertexWhoseLightIsNotSaidThrows);
        Checks.run("aVertexLitOtherwiseThanItsSiteThrows", LightSiteTests::aVertexLitOtherwiseThanItsSiteThrows);
        Checks.run("aFrameLitByHandLightsNothing", LightSiteTests::aFrameLitByHandLightsNothing);
        Checks.run("everyBlendOfAFaceMatchesItsTwinToTheBit", LightSiteTests::everyBlendOfAFaceMatchesItsTwinToTheBit);
        Checks.run("glassBehindALampTakesTheSiteOfItsLightWithTheLamp", LightSiteTests::glassBehindALampTakesTheSiteOfItsLightWithTheLamp);
        Checks.run("aTintIsASiteOfItsOwnOverItsBase", LightSiteTests::aTintIsASiteOfItsOwnOverItsBase);
        Checks.run("aCrownMadeApartTakesTheSiteItWasWrittenDownWith", LightSiteTests::aCrownMadeApartTakesTheSiteItWasWrittenDownWith);
        Checks.run("aVertexOfASiteCarriesItsRecordAndItsSharesAndSaysHowInItsAlpha", LightSiteTests::aVertexOfASiteCarriesItsRecordAndItsSharesAndSaysHowInItsAlpha);
        Checks.run("everyAlphaAVertexIsGivenIsReadBackAsItWasGiven", LightSiteTests::everyAlphaAVertexIsGivenIsReadBackAsItWasGiven);
        Checks.run("aBuildsPageIsTheRecordsOfItsTable", LightSiteTests::aBuildsPageIsTheRecordsOfItsTable);
        Checks.run("theWorldShaderReadsARecordWhereLightPagesLaysItOut", LightSiteTests::theWorldShaderReadsARecordWhereLightPagesLaysItOut);
        Checks.run("aSiteWorkedOutAgainOtherwiseThanItWasPutThrows", LightSiteTests::aSiteWorkedOutAgainOtherwiseThanItWasPutThrows);
        Checks.run("aRefreshedPageTakesTheRecordsThatDiffer", LightSiteTests::aRefreshedPageTakesTheRecordsThatDiffer);
        Checks.run("aPageFindsTheRecordOfASiteByItsKey", LightSiteTests::aPageFindsTheRecordOfASiteByItsKey);
        Checks.run("crownsAreLitAgainFromThePageOfTheirBuild", LightSiteTests::crownsAreLitAgainFromThePageOfTheirBuild);
    }

    /**
     * Puts the frame on a site of WorldScene.sites named by its light, as a builder puts one
     * named by its square: frames lit alike share a site. A test that lights a frame by hand
     * sites it after the last change to its light.
     */
    static SpriteGeometry.Frame sited(SpriteGeometry.Frame frame) {
        float[] record = new float[RECORD];
        LightSites.take(frame, record, 0, 0f, false);
        // Named by its light and not by a square, the site is none a frame could work out again (LightSites.Table.evaluator).
        LightSites.Evaluator evaluator = WorldScene.sites.evaluator;
        WorldScene.sites.evaluator = null;
        try {
            WorldScene.sites.put(LightSites.SQUARE, 1L << 40 | Arrays.hashCode(record) & 0xFFFFFFFFL, frame, 0f, false);
        } finally {
            WorldScene.sites.evaluator = evaluator;
        }
        return frame;
    }

    /** The mesh, with what is added to it next carrying the light it is given as it is: a test's own vertices. */
    static SpriteGeometry.Mesh constant(SpriteGeometry.Mesh mesh) {
        mesh.lightStatic();
        return mesh;
    }

    /**
     * The mesh's vertices with the light the picture works out for each (LightSites.decode) in
     * the places a vertex of no site carries it: its colour, its alpha with nothing over
     * LightSites.CODED_STEP, what it has of a light the game has not given and of the day, and 0
     * for its record. Read from the records of its set's table, or for a mesh of no set the
     * world's, which a test's frames are put on (sited).
     */
    static float[] lit(SpriteGeometry.Mesh mesh) {
        return lit(mesh, mesh.set != null ? mesh.set.sites : WorldScene.sites);
    }

    static float[] lit(SpriteGeometry.Mesh mesh, LightSites.Table table) {
        float[] lit = Arrays.copyOf(mesh.vertices, mesh.floats), light = new float[6];
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            LightSites.decode(mesh.vertices, at, table.records, light);
            lit[at + 5] = light[0];
            lit[at + 6] = light[1];
            lit[at + 7] = light[2];
            lit[at + 8] = light[5];
            lit[at + SpriteGeometry.RECORD_AT] = 0f;
            lit[at + SpriteGeometry.UNLIT_AT] = light[3];
            lit[at + SpriteGeometry.DAY_AT] = light[4];
        }
        return lit;
    }

    private static boolean throwsIllegalState(Runnable run) {
        try {
            run.run();
            return false;
        } catch (IllegalStateException expected) {
            return true;
        }
    }

    private static void theSuiteChecksEveryVertexAgainstItsTwin() {
        check(LightSites.TWIN, "the suite runs with the twin on, as scripts/test.ps1 says");
    }

    private static void aSiteIsKeptOnceWithTheLightItWasPutWith() {
        LightSites.Table table = new LightSites.Table();
        table.level = 2;
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.light(0.3f, 0.4f, 0.5f);
        frame.open = 1f;
        int site = table.put(LightSites.SQUARE, 17L, frame, 0.25f, false);
        check(site == 0 && frame.site == 0 && frame.sites == table, "the first site put is the table's first, and the frame takes it");
        check(table.put(LightSites.SQUARE, 17L, frame, 0.25f, false) == site && table.count == 1, "put again in the same light, it is the same site");
        check(table.find(LightSites.SQUARE, 17L) == site && table.find(LightSites.SQUARE, 18L) == LightSites.NO_SITE, "it is found by its key, and a key not put is not");
        int at = site * RECORD;
        check(table.records[at + LightSites.RED] == 0.3f && table.records[at + LightSites.BLUE] == 0.5f && table.records[at + LightSites.FLAT_DAY] == 0.25f
                && table.records[at + LightSites.OPEN] == 1f && table.records[at + LightSites.CORNER_UNLIT] == SpriteGeometry.AS_FACE
                && table.records[at + LightSites.LAMPLIT] == 0f, "its record is the light it was put with");
        check(LightSites.kindOf(table.keys[site]) == LightSites.SQUARE && (table.keys[site] >>> 48 & 0xFF) == 2 + 128, "its key names its kind and its level");
        table.level = 3;
        check(table.put(LightSites.SQUARE, 17L, frame, 0.25f, false) == 1, "the same names on another level are another site");
        frame.light(0.6f, 0.4f, 0.5f);
        check(table.put(LightSites.SQUARE, 18L, frame, 0f, false) == 2 && table.records[2 * RECORD + LightSites.RED] == 0.6f, "and other names another, with their own light");
        table.clear();
        check(table.count == 0 && table.find(LightSites.SQUARE, 18L) == LightSites.NO_SITE, "a table emptied keeps none");
    }

    private static void aSitePutAgainInOtherLightThrows() {
        LightSites.Table table = new LightSites.Table();
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.light(0.3f, 0.4f, 0.5f);
        table.put(LightSites.SQUARE, 5L, frame, 0f, false);
        frame.cornerRed[2] = 0.31f;
        check(throwsIllegalState(() -> table.put(LightSites.SQUARE, 5L, frame, 0f, false)), "a site put again with a corner lit otherwise throws");
        frame.cornerRed[2] = 0.3f;
        check(throwsIllegalState(() -> table.put(LightSites.SQUARE, 5L, frame, 0.1f, false)), "and with another day for its flat vertices");
        check(throwsIllegalState(() -> table.put(LightSites.SQUARE, 5L, frame, 0f, true)), "and with a lamp where there was none");
        check(table.put(LightSites.SQUARE, 5L, frame, 0f, false) == 0, "and in the light it was put with, it is the site it was");
    }

    private static void aVertexWhoseLightIsNotSaidThrows() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        check(throwsIllegalState(() -> mesh.add(0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)), "a vertex added with its light not said throws");
        mesh.lightStatic();
        mesh.add(0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);
        mesh.add(0f, 0f, 0f, 0f, 0f, 0.5f, 0.5f, 0.5f);
        check(mesh.floats == 2 * SpriteGeometry.VERTEX_FLOATS, "one said to carry its light as it is is added, and as many more as follow");
        mesh.litAsFace();
        check(throwsIllegalState(() -> mesh.add(0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)), "a mesh put back as its faces say has the light of the next said again");
        mesh.lightStatic();
        mesh.clear();
        check(throwsIllegalState(() -> mesh.add(0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)), "and so does one emptied");
        SpriteGeometry.MeshSet set = new SpriteGeometry.MeshSet();
        SpriteGeometry.Mesh asked = set.get(4);
        asked.lightStatic();
        asked.add(0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);
        check(throwsIllegalState(() -> set.get(4).add(0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)), "and one asked for again, whatever its last asker said");
    }

    private static void aVertexLitOtherwiseThanItsSiteThrows() {
        LightSites.Table table = new LightSites.Table();
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.light(0.3f, 0.4f, 0.5f);
        frame.open = 1f + WorldScene.LIT_STEP * WorldScene.ROOM_LIT;
        table.put(LightSites.SQUARE, 9L, frame, 0.5f, false);
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        mesh.open = frame.open;
        mesh.day = 0.5f;
        mesh.lightFlat(frame, 0.5f, 1f, 1f);
        mesh.add(0f, 0f, 0f, 0f, 0f, 0.15f, 0.4f, 0.5f);
        check(mesh.floats == SpriteGeometry.VERTEX_FLOATS, "a vertex lit as its site says, times what the builder lays on, is added");
        check(throwsIllegalState(() -> mesh.add(0f, 0f, 0f, 0f, 0f, 0.15f, 0.4f, Math.nextUp(0.5f))), "one the least bit off its site's light throws");
        mesh.day = 0.25f;
        check(throwsIllegalState(() -> mesh.add(0f, 0f, 0f, 0f, 0f, 0.15f, 0.4f, 0.5f)), "and one with another day");
        mesh.day = 0.5f;
        mesh.open = frame.open + WorldScene.LIT_STEP;
        check(throwsIllegalState(() -> mesh.add(0f, 0f, 0f, 0f, 0f, 0.15f, 0.4f, 0.5f)), "and one with another kind of light");
        mesh.open = frame.open + 32f * WorldScene.GLASS;
        mesh.add(0f, 0f, 0f, 0f, 0f, 0.15f, 0.4f, 0.5f);
        check(mesh.floats == 2 * SpriteGeometry.VERTEX_FLOATS, "what it is made of is the builder's");
    }

    private static void aFrameLitByHandLightsNothing() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.light(0.3f, 0.4f, 0.5f);
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        check(frame.site == LightSites.NO_SITE && throwsIllegalState(() -> mesh.lightFlat(frame)), "a frame lit by hand is of no site, and lights nothing");
        LightSites.Table table = new LightSites.Table();
        table.put(LightSites.SQUARE, 3L, frame, 0f, false);
        mesh.lightFlat(frame);
        frame.open = 1f;
        check(throwsIllegalState(() -> mesh.lightFlat(frame)), "nor does one lit otherwise once it took its site");
        frame.openUnlit();
        mesh.lightFlat(frame);
        mesh.add(0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);
        check(frame.site == LightSites.STATIC && mesh.floats == SpriteGeometry.VERTEX_FLOATS,
                "the open ground's light where the game has given none is a constant, which its vertices carry as it is");
    }

    /** Floors and walls, whole and in part, over squares lit unevenly at random: each vertex is checked against its twin as it is added. */
    private static void everyBlendOfAFaceMatchesItsTwinToTheBit() {
        Random random = new Random(29);
        SpriteGeometry geometry = new SpriteGeometry();
        for (int round = 0; round < 200; round++) {
            SpriteGeometry.Frame frame = wholeFrame();
            for (int corner = 0; corner < 4; corner++) {
                frame.cornerRed[corner] = 1.5f * random.nextFloat();
                frame.cornerGreen[corner] = random.nextFloat();
                frame.cornerBlue[corner] = 0.7f * random.nextFloat();
                frame.cornerUnlit[corner] = random.nextFloat();
                frame.cornerDay[corner] = random.nextFloat();
            }
            frame.unlitKind = random.nextInt(4);
            frame.open = random.nextInt(2) + WorldScene.LIT_STEP * random.nextInt(4);
            sited(frame);
            SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
            mesh.open = frame.open;
            float a = 0.5f * random.nextFloat(), b = 0.5f * random.nextFloat(), wide = 0.1f + 0.4f * random.nextFloat();
            int laid = geometry.addFace(mesh, frame, 10, 20, 0, a, b, 0f, wide, 0f, 0f, 0f, wide, 0f)
                    + geometry.addFace(mesh, frame, 10, 20, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
                    + geometry.addFace(mesh, frame, 10, 20, 0, 0f, a, 0f, 0f, wide, 0f, 0f, 0f, 0.5f);
            check(laid > 0, "the faces are laid, each vertex as its twin says");
        }
    }

    private static SpriteGeometry.Frame wholeFrame() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.width = 128f;
        frame.height = 256f;
        frame.uEnd = 1f;
        frame.vEnd = 1f;
        return frame;
    }

    private static void glassBehindALampTakesTheSiteOfItsLightWithTheLamp() {
        LightSites.Table table = new LightSites.Table();
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.light(0.3f, 0.4f, 0.5f);
        int square = table.put(LightSites.SQUARE, 33L, frame, 0f, false);
        long north = LightSites.place(40, 59, 2);
        int lamp = table.lamp(frame, LightSites.LAMP_NORTH, north, true);
        check(lamp != square && frame.site == lamp && table.keys[lamp] == (table.keys[square] | LightSites.LAMP_NORTH << 46),
                "a lamp behind the glass is a site of its own, named by the square it is read from");
        check(table.bases[lamp] == square && table.places[lamp] == north && LightSites.placeX(north) == 40 && LightSites.placeY(north) == 59 && LightSites.placeZ(north) == 2,
                "worked out again from the square's site, with the lamp of the square it is read from");
        check(Arrays.equals(table.records, lamp * RECORD, lamp * RECORD + LightSites.LAMPLIT, table.records, square * RECORD, square * RECORD + LightSites.LAMPLIT)
                && table.records[lamp * RECORD + LightSites.LAMPLIT] == 1f, "lit as the square's site, with the lamp burning");
        frame.site = square;
        check(table.lamp(frame, LightSites.LAMP_NORTH, north, true) == lamp, "and the same site when laid again");
        frame.site = square;
        check(throwsIllegalState(() -> table.lamp(frame, LightSites.LAMP_NORTH, north, false)), "a lamp read again otherwise throws");
        frame.site = square;
        check(throwsIllegalState(() -> table.lamp(frame, LightSites.LAMP_NORTH, LightSites.place(40, 58, 2), true)), "and so does one read again from another square");
        frame.site = square;
        int own = table.lamp(frame, LightSites.LAMP_OWN, LightSites.place(40, 60, 2), false);
        check(own != lamp && table.records[own * RECORD + LightSites.LAMPLIT] == 0f, "the square's own lamp is another site, here with none burning");
        SpriteGeometry.Mesh glass = new SpriteGeometry.Mesh();
        frame.site = lamp;
        glass.lightFlat(frame);
        glass.open = 32f * WorldScene.GLASS;
        check(throwsIllegalState(() -> glass.add(0f, 0f, 0f, 0f, 0f, 0.3f, 0.4f, 0.5f)), "glass behind a burning lamp is lit glass: plain glass there throws");
        glass.open = 32f * WallBuilder.LIT_GLASS;
        glass.add(0f, 0f, 0f, 0f, 0f, 0.3f, 0.4f, 0.5f);
        check(glass.floats == SpriteGeometry.VERTEX_FLOATS, "and lit glass is added");
        check(glass.vertices[8] == 32f * WorldScene.GLASS + LightSites.CODED_STEP * (2f * LightSites.FLAT + 8f + 16f) && lit(glass, table)[8] == 32f * WallBuilder.LIT_GLASS,
                "carrying plain glass, and that a lamp behind it lights it as its site says: the picture reads lit glass from the site with the lamp burning");
        SpriteGeometry.Frame ceiling = new SpriteGeometry.Frame();
        ceiling.light(0.2f, 0.2f, 0.2f);
        table.put(LightSites.CEILING, 4L, ceiling, 0f, false);
        check(throwsIllegalState(() -> table.lamp(ceiling, LightSites.LAMP_OWN, north, true)), "a lamp read from a site that names no square throws");
    }

    private static void aTintIsASiteOfItsOwnOverItsBase() {
        LightSites.Table table = new LightSites.Table();
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.light(0.4f, 0.4f, 0.4f);
        int base = table.put(LightSites.SQUARE, 2L, frame, 0f, false);
        frame.light(0.2f, 0.3f, 0.4f);
        int tinted = table.tinted(base, 0.5f, 0.75f, 1f, frame);
        check(tinted != base && frame.site == tinted && table.bases[tinted] == base && table.tints[tinted * 3] == 0.5f && table.tints[tinted * 3 + 1] == 0.75f
                && table.tints[tinted * 3 + 2] == 1f && table.records[tinted * RECORD + LightSites.GREEN] == 0.3f,
                "a tint is a site of its own, its base and the tint kept with it, and the light it gives its record");
        check(table.tinted(base, 0.5f, 0.75f, 1f, frame) != tinted, "and each tint laid is another");
        check(throwsIllegalState(() -> table.tinted(LightSites.NO_SITE, 1f, 1f, 1f, frame)), "a tint laid on the light of no site throws");
    }

    private static void aCrownMadeApartTakesTheSiteItWasWrittenDownWith() {
        LightSites.Table build = new LightSites.Table(), crowns = new LightSites.Table();
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.light(0.3f, 0.4f, 0.5f);
        frame.cornerDay[1] = 0.5f;
        frame.open = 1f;
        int site = build.put(LightSites.SQUARE, 21L, frame, 0f, false);
        SpriteGeometry.Frame made = new SpriteGeometry.Frame();
        int there = crowns.putRecord(build.keys[site], build.records, site * RECORD, made);
        check(made.sites == crowns && made.site == there && crowns.keys[there] == build.keys[site],
                "a crown made apart takes the site of the key it was written down with, in the table it is made into");
        check(LightSites.matches(made, build.records, site * RECORD) && made.red == 0.3f && made.cornerDay[1] == 0.5f && made.open == 1f, "lit as the record says");
        float[] other = Arrays.copyOfRange(build.records, site * RECORD, site * RECORD + RECORD);
        other[LightSites.CORNER_RED + 3] = 0.9f;
        check(throwsIllegalState(() -> crowns.putRecord(build.keys[site], other, 0, made)), "a key written down again with another record throws");
    }

    /**
     * A vertex of a site carries what the builder laid on the light, its record's place in its
     * table and its shares (a, b) of the square, and says in its alpha whether it is aloft, how it
     * takes its light and that the kind of its light is its record's. A vertex of no site carries
     * its light, and what it has of a light the game has not given and of the day, as it is given.
     */
    private static void aVertexOfASiteCarriesItsRecordAndItsSharesAndSaysHowInItsAlpha() {
        SpriteGeometry.MeshSet set = new SpriteGeometry.MeshSet();
        SpriteGeometry.Frame first = new SpriteGeometry.Frame(), frame = new SpriteGeometry.Frame();
        first.light(0.1f, 0.1f, 0.1f);
        set.sites.put(LightSites.SQUARE, 1L, first, 0f, false);
        frame.light(0.4f, 0.5f, 0.6f);
        frame.cornerRed[2] = 0.8f;
        frame.cornerDay[1] = 0.5f;
        frame.open = 1f + WorldScene.LIT_STEP * WorldScene.ROOM_LIT;
        int site = set.sites.put(LightSites.SQUARE, 2L, frame, 0.25f, false);
        set.storey(SpriteGeometry.NO_BUILDING, 1);
        SpriteGeometry.Mesh mesh = set.get(3);
        mesh.open = frame.open + 32f * 9f;
        mesh.litAt(frame, 0.25f, 0.75f, 0.5f, 1f, 1f);
        float[] light = new float[5];
        LightSites.twin(set.sites.records, site * RECORD, LightSites.CORNERS, 0.25f, 0.75f, 0.5f, 1f, 1f, light);
        mesh.add(1f, 2f, 3f, 0f, 0f, light[0], light[1], light[2]);
        float[] v = mesh.vertices;
        check(site == 1 && v[5] == 0.5f && v[6] == 1f && v[7] == 1f && v[SpriteGeometry.RECORD_AT] == site && v[SpriteGeometry.A_AT] == 0.25f && v[SpriteGeometry.B_AT] == 0.75f,
                "a vertex of a site carries what is laid on its light, its record and its shares of the square");
        check(v[8] == 1f + 32f * 9f + LightSites.CODED_STEP * (1f + 2f * LightSites.CORNERS + 8f),
                "and its alpha is its own under its kind of light, with over that that it is aloft, is lit by the corners and takes the kind of light of its record");
        float[] lit = lit(mesh);
        check(lit[5] == light[0] && lit[6] == light[1] && lit[7] == light[2] && lit[8] == mesh.open && lit[SpriteGeometry.UNLIT_AT] == light[3]
                && lit[SpriteGeometry.DAY_AT] == light[4], "worked out as the picture works it out, it is lit as it was given");
        mesh.lightStatic();
        mesh.unlit = 0.3f;
        mesh.day = 0.6f;
        mesh.add(1f, 2f, 3f, 0f, 0f, 0.7f, 0.8f, 0.9f);
        float[] w = mesh.vertices;
        int at = SpriteGeometry.VERTEX_FLOATS;
        check(w[at + 5] == 0.7f && w[at + 6] == 0.8f && w[at + 7] == 0.9f && w[at + 8] == mesh.open + LightSites.CODED_STEP && w[at + SpriteGeometry.RECORD_AT] == 0f
                && w[at + SpriteGeometry.UNLIT_AT] == 0.3f && w[at + SpriteGeometry.DAY_AT] == 0.6f, "a vertex of no site carries its light as it is given, and says only that it is aloft");
    }

    /**
     * Every alpha a builder gives a vertex, of every kind of light, material and aloft, coded
     * for every way a vertex takes its light, is worked out again as it was given: with its
     * record's kind where it takes that, and on a site with a lamp behind it, its glass and panes
     * lit as the lamp says. Each coded alpha is a whole number under two to the twentieth, which a
     * float holds exactly.
     */
    private static void everyAlphaAVertexIsGivenIsReadBackAsItWasGiven() {
        // A site of no lamp, of kind 3; one with a lamp burning behind it, of kind 1; and one with a lamp that is out, of kind 2.
        float[] records = new float[3 * RECORD];
        records[LightSites.OPEN] = 1f + WorldScene.LIT_STEP * 3f;
        records[RECORD + LightSites.OPEN] = WorldScene.LIT_STEP;
        records[RECORD + LightSites.LAMPLIT] = 1f;
        records[2 * RECORD + LightSites.OPEN] = 1f + WorldScene.LIT_STEP * 2f;
        float[] vertex = new float[SpriteGeometry.VERTEX_FLOATS], out = new float[6];
        int read = 0, wrong = 0;
        String first = "";
        for (int kind = 0; kind < 4; kind++) {
            for (int material = 0; material < 128; material++) {
                for (int low : new int[] {0, 1, 7, 8, 23, 24, 25, 31}) {
                    float alpha = low + 32f * material + WorldScene.LIT_STEP * kind;
                    for (int aloft = 0; aloft < 2; aloft++) {
                        vertex[8] = alpha + LightSites.CODED_STEP * aloft;
                        LightSites.decode(vertex, 0, records, out);
                        if (out[5] != alpha) {
                            first = wrong++ == 0 ? "the alpha " + alpha + " of no site, aloft " + aloft + ", read as " + out[5] : first;
                        }
                        read++;
                        for (int record = 0; record < 3; record++) {
                            boolean lampSite = record > 0, burning = record == 1;
                            // Glass or a pane lit otherwise than its lamp says is a vertex the twin refuses: see glassBehindALampTakesTheSiteOfItsLightWithTheLamp.
                            if (lampSite && (burning ? material == WorldScene.GLASS || material == WorldScene.PANE : material == WallBuilder.LIT_GLASS || material == WorldScene.LIT_PANE)) {
                                continue;
                            }
                            for (int mode = LightSites.FLAT; mode <= LightSites.LERP; mode++) {
                                for (boolean kindFromSite : new boolean[] {true, false}) {
                                    float given = kindFromSite ? alpha + WorldScene.LIT_STEP * (LightSites.kindOf(records[record * RECORD + LightSites.OPEN]) - kind) : alpha;
                                    vertex[8] = LightSites.coded(given, aloft, mode, kindFromSite, lampSite);
                                    vertex[SpriteGeometry.RECORD_AT] = record;
                                    LightSites.decode(vertex, 0, records, out);
                                    float code = (float) Math.floor((vertex[8] + 0.5f) / LightSites.CODED_STEP);
                                    if (out[5] != given || vertex[8] != Math.rint(vertex[8]) || vertex[8] >= 1 << 20 || code != aloft + 2 * mode + (kindFromSite ? 8 : 0)
                                            + 16 * (!lampSite ? 0 : material == WorldScene.GLASS || material == WallBuilder.LIT_GLASS ? 1 : material == WorldScene.PANE || material == WorldScene.LIT_PANE ? 2 : 0)) {
                                        first = wrong++ == 0 ? "the alpha " + given + " of record " + record + ", mode " + mode + ", aloft " + aloft + (kindFromSite ? ", of its record's kind," : "")
                                                + " coded " + vertex[8] + " and read as " + out[5] : first;
                                    }
                                    read++;
                                }
                            }
                        }
                    }
                }
            }
        }
        check(wrong == 0, wrong + " of " + read + " alphas are not read back as they were given: " + first);
    }

    /** A build's page is its table's records in the table's order, which its vertices count them in, whether they are solid or laid. */
    private static void aBuildsPageIsTheRecordsOfItsTable() {
        LightSites.Table table = new LightSites.Table();
        SpriteGeometry.Layers layers = new SpriteGeometry.Layers();
        SpriteGeometry.MeshSet solid = new SpriteGeometry.MeshSet(layers, table), laid = new SpriteGeometry.MeshSet(layers, table);
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.light(0.2f, 0.3f, 0.4f);
        table.put(LightSites.SQUARE, 1L, frame, 0f, false);
        frame.light(0.5f, 0.6f, 0.7f);
        table.put(LightSites.SQUARE, 2L, frame, 0f, false);
        SpriteGeometry.Mesh mesh = laid.get(1);
        mesh.lightFlat(frame);
        mesh.add(0f, 0f, 0f, 0f, 0f, 0.5f, 0.6f, 0.7f);
        WorldScene.ChunkMeshes build = new WorldScene.ChunkMeshes(0);
        build.fill(solid, laid);
        check(build.pageRecords == 2 && Arrays.equals(build.page, 0, 2 * RECORD, table.records, 0, 2 * RECORD), "a build's page is its table's records, in the table's order");
        SpriteGeometry.MeshSet own = new SpriteGeometry.MeshSet(layers, new LightSites.Table());
        own.sites.put(LightSites.SQUARE, 3L, frame, 0f, false);
        check(throwsIllegalState(() -> build.fill(solid, own)), "a build whose laid vertices count their sites in a table of their own throws: a build has one page");
        build.fill(new SpriteGeometry.MeshSet(), new SpriteGeometry.MeshSet());
        check(build.floats == 0 && build.pageRecords == 0, "and a build of nothing has none");
        build.scrap();
    }

    /**
     * The world's vertex program finds texel k of record r where LightPages puts it, TEXELS to a
     * record and A_ROW records to a row of WIDE; reads each part of a record from the texel
     * LightSites lays it in; blends the corners in twin's order of operations, so the same floats
     * come out; and reads the code, the kind of light and the lamp's materials as LightSites codes
     * them.
     */
    private static void theWorldShaderReadsARecordWhereLightPagesLaysItOut() {
        String vertex = PipelineShaders.WORLD_VERTEX.replaceAll("\\s+", "");
        check(LightPages.TEXELS * 4 == RECORD && LightPages.A_ROW * LightPages.TEXELS == LightPages.WIDE, "a record is whole texels, and a row whole records");
        check(vertex.contains("floatrow=floor((r+0.5)/" + LightPages.A_ROW + ".0);") && vertex.contains("floatcolumn=(r-" + LightPages.A_ROW + ".0*row)*" + LightPages.TEXELS + ".0+k;")
                && vertex.contains("vec2((column+0.5)/" + LightPages.WIDE + ".0,(row+0.5)/uPageRows)"), "a record's texel is found as LightPages lays them out");
        check(LightSites.RED == 0 && LightSites.GREEN == 1 && LightSites.BLUE == 2 && LightSites.FLAT_DAY == 3 && vertex.contains("vec4own=lightRecord(r,0.0);")
                && vertex.contains("vColor.rgb=own.rgb*gl_Color.rgb;") && vertex.contains("day=own.w;"), "a flat vertex's light and day are its record's first texel");
        check(vertex.contains("vec4red=lightRecord(r," + LightSites.CORNER_RED / 4 + ".0),green=lightRecord(r," + LightSites.CORNER_GREEN / 4 + ".0),blue=lightRecord(r,"
                        + LightSites.CORNER_BLUE / 4 + ".0);")
                && vertex.contains("vec4share=lightRecord(r," + LightSites.CORNER_UNLIT / 4 + ".0),daily=lightRecord(r," + LightSites.CORNER_DAY / 4 + ".0);")
                && vertex.contains("vec4tail=lightRecord(r," + LightSites.OPEN / 4 + ".0);") && LightSites.OPEN % 4 == 0 && LightSites.UNLIT_KIND == LightSites.OPEN + 1
                && LightSites.LAMPLIT == LightSites.OPEN + 2, "the corners, their unlit shares and their day, and the openness, unlit kind and lamp, are the texels LightSites lays them in");
        check(vertex.contains("floatw0=(1.0-a)*(1.0-b),w1=a*(1.0-b),w2=a*b,w3=(1.0-a)*b;")
                && vertex.contains("vec3blended=lightMode>2.5?(c0*(1.0-a)+c1*a)*(1.0-b)+(c3*(1.0-a)+c2*a)*b:c0*w0+c1*w1+c2*w2+c3*w3;")
                && vertex.contains("unlit=share.x<0.0?-1.0:tail.y*" + (int) SpriteGeometry.UNLIT_KIND_STEP + ".0+share.x*w0+share.y*w1+share.z*w2+share.w*w3;")
                && vertex.contains("day=daily.x*w0+daily.y*w1+daily.z*w2+daily.w*w3;") && SpriteGeometry.AS_FACE == -1f && vertex.contains("unlit=-1.0;"),
                "the corners are blended in twin's order of operations");
        check(vertex.contains("floatlightCode=floor((gl_Color.a+0.5)/" + (int) LightSites.CODED_STEP + ".0);")
                && vertex.contains("vColor.a+=" + (int) WorldScene.LIT_STEP + ".0*floor((tail.x+0.5)/" + (int) WorldScene.LIT_STEP + ".0);")
                && vertex.contains("vColor.a+=variant<1.5?" + (int) (32f * (WallBuilder.LIT_GLASS - WorldScene.GLASS)) + ".0:" + (int) (32f * (WorldScene.LIT_PANE - WorldScene.PANE)) + ".0;"),
                "and the code, the kind of light and the lamp's materials are read as LightSites codes them");
    }

    /** With the twin on, a site put for the first time is worked out again from its key (LightSites.Table.evaluator): otherwise than it was put, or as no site, it throws. */
    private static void aSiteWorkedOutAgainOtherwiseThanItWasPutThrows() {
        LightSites.Table table = new LightSites.Table();
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.light(0.3f, 0.4f, 0.5f);
        long[] asked = new long[1];
        table.evaluator = (key, base, place, tints, tintAt, records, into, at) -> {
            asked[0] = key;
            LightSites.take(frame, into, at, 0f, false);
            return true;
        };
        int site = table.put(LightSites.SQUARE, 6L, frame, 0f, false);
        check(asked[0] == table.keys[site], "a site put for the first time is worked out again from its key");
        asked[0] = 0L;
        table.put(LightSites.SQUARE, 6L, frame, 0f, false);
        check(asked[0] == 0L, "and not when it is put again");
        table.evaluator = (key, base, place, tints, tintAt, records, into, at) -> {
            into[at + LightSites.GREEN] = 0.41f;
            return true;
        };
        check(throwsIllegalState(() -> table.put(LightSites.SQUARE, 7L, frame, 0f, false)), "one worked out otherwise throws");
        table.evaluator = (key, base, place, tints, tintAt, records, into, at) -> false;
        check(throwsIllegalState(() -> table.put(LightSites.SQUARE, 8L, frame, 0f, false)), "and so does one worked out to be no site");
    }

    /**
     * A build's page refreshed (publish) takes the records that differ from it to the bit, and
     * has the render thread send them: it raises lit, and the first and last record changed run
     * over every refreshing until the render thread takes them. One that changes nothing leaves it as it was.
     */
    private static void aRefreshedPageTakesTheRecordsThatDiffer() {
        LightSites.Table table = new LightSites.Table();
        SpriteGeometry.Layers layers = new SpriteGeometry.Layers();
        SpriteGeometry.MeshSet solid = new SpriteGeometry.MeshSet(layers, table), laid = new SpriteGeometry.MeshSet(layers, table);
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        for (int i = 0; i < 4; i++) {
            frame.light(0.1f * i, 0.2f, 0.3f);
            table.put(LightSites.SQUARE, i, frame, 0f, false);
        }
        WorldScene.ChunkMeshes build = new WorldScene.ChunkMeshes(0);
        build.fill(solid, laid);
        int lit = build.lit;
        check(build.pageRecords == 4 && build.litTo < build.litFrom, "a page filled is its build's, with nothing refreshed to send");
        float[] records = Arrays.copyOf(build.page, 4 * RECORD);
        check(build.publish(records) == 0 && build.lit == lit && build.litTo < build.litFrom, "refreshed to the same records, it is as it was");
        records[RECORD + LightSites.GREEN] = 0.25f;
        records[2 * RECORD + LightSites.LAMPLIT] = 1f;
        check(build.publish(records) == 2 && build.lit == lit + 1 && build.litFrom == 1 && build.litTo == 2
                && Arrays.equals(build.page, 0, 4 * RECORD, records, 0, 4 * RECORD), "refreshed to two others, it takes them and has the two sent");
        records[LightSites.RED] = -0f;
        check(build.publish(records) == 1 && build.lit == lit + 2 && build.litFrom == 0 && build.litTo == 2 && Float.floatToRawIntBits(build.page[LightSites.RED]) == Integer.MIN_VALUE,
                "a record that differs only in its bits differs, and what is sent runs from the first changed to the last");
        build.fill(solid, laid);
        check(build.litTo < build.litFrom && build.page[RECORD + LightSites.GREEN] == 0.2f, "a page filled again is its build's, with nothing refreshed to send");
        build.scrap();
    }

    /** A build's page finds the record of a site's key (pageSite), and none for a key it has not, until it is filled again. */
    private static void aPageFindsTheRecordOfASiteByItsKey() {
        LightSites.Table table = new LightSites.Table();
        SpriteGeometry.Layers layers = new SpriteGeometry.Layers();
        SpriteGeometry.MeshSet solid = new SpriteGeometry.MeshSet(layers, table), laid = new SpriteGeometry.MeshSet(layers, table);
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.light(0.2f, 0.3f, 0.4f);
        for (int i = 0; i < 40; i++) {
            table.put(LightSites.ALONG, i * 7L, frame, 0f, false);
        }
        WorldScene.ChunkMeshes build = new WorldScene.ChunkMeshes(0);
        build.fill(solid, laid);
        boolean found = true;
        for (int i = 0; i < 40; i++) {
            found &= build.pageSite(table.keys[i]) == i;
        }
        check(found && build.pageSite(LightSites.key(LightSites.ALONG, 0, 1L)) == -1, "each site's key finds its record, and a key put on no site finds none");
        table.clear();
        table.put(LightSites.SQUARE, 3L, frame, 0f, false);
        build.fill(solid, laid);
        check(build.pageSite(table.keys[0]) == 0 && build.pageSite(LightSites.key(LightSites.ALONG, 0, 7L)) == -1, "filled again, it finds the sites it has now and no others");
        build.scrap();
    }

    private static final float[] LEAF_PLACES = {0.25f, 0.5f, 0.75f, 0.5f};

    /** A crown written down in the frame's light, as a build writes one down. */
    private static Crowns crownIn(SpriteGeometry.Frame frame) {
        Crowns crowns = new Crowns();
        crowns.add(5, frame, LEAF_PLACES, 1.5f, 2.5f, 0f, 0.5f, 0.9f, 6f, 0f, 0f, Flora.BARK_OAK, Flora.BARK_OAK, Flora.MAPLE, 3, Flora.OPEN, 1f, 0, 0f, TreeForm.NONE, null,
                Flora.TWIGGED_AT_1080, false, false, false);
        return crowns;
    }

    /**
     * Crowns are lit again from the page of their build as a refresh leaves it (Crowns.relight):
     * a crown of a site takes its key's record there, and one of a site the build has not throws.
     */
    private static void crownsAreLitAgainFromThePageOfTheirBuild() {
        LightSites.Table table = new LightSites.Table();
        SpriteGeometry.Layers layers = new SpriteGeometry.Layers();
        SpriteGeometry.MeshSet solid = new SpriteGeometry.MeshSet(layers, table), laid = new SpriteGeometry.MeshSet(layers, table);
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.light(0.3f, 0.4f, 0.5f);
        int site = table.put(LightSites.SQUARE, 12L, frame, 0f, false);
        Crowns crowns = crownIn(frame);
        WorldScene.ChunkMeshes build = new WorldScene.ChunkMeshes(0);
        build.fill(solid, laid);
        float[] records = Arrays.copyOf(build.page, build.pageRecords * RECORD);
        records[site * RECORD + LightSites.CORNER_GREEN + 2] = 0.9f;
        build.publish(records);
        crowns.relight(build);
        SpriteGeometry.Frame relit = new SpriteGeometry.Frame();
        LightSites.give(records, site * RECORD, relit);
        new LightSites.Table().put(LightSites.SQUARE, 12L, relit, 0f, false);
        check(crowns.same(crownIn(relit)) && !crowns.same(crownIn(frame)), "a crown lit again from its build's page is the crown its build would write down in that light");
        table.clear();
        table.put(LightSites.SQUARE, 13L, frame, 0f, false);
        build.fill(solid, laid);
        check(throwsIllegalState(() -> crowns.relight(build)), "and one of a site its build has not throws");
        build.scrap();
    }
}

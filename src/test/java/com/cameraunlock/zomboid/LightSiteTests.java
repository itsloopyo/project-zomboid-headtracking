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
    }

    /**
     * Puts the frame on a site of WorldScene.sites named by its light, as a builder puts one
     * named by its square: frames lit alike share a site. A test that lights a frame by hand
     * sites it after the last change to its light.
     */
    static SpriteGeometry.Frame sited(SpriteGeometry.Frame frame) {
        float[] record = new float[RECORD];
        LightSites.take(frame, record, 0, 0f, false);
        WorldScene.sites.put(LightSites.SQUARE, 1L << 40 | Arrays.hashCode(record) & 0xFFFFFFFFL, frame, 0f, false);
        return frame;
    }

    /** The mesh, with what is added to it next carrying the light it is given as it is: a test's own vertices. */
    static SpriteGeometry.Mesh constant(SpriteGeometry.Mesh mesh) {
        mesh.lightStatic();
        return mesh;
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
        int lamp = table.lamp(frame, LightSites.LAMP_NORTH, true);
        check(lamp != square && frame.site == lamp && table.keys[lamp] == (table.keys[square] | LightSites.LAMP_NORTH << 46),
                "a lamp behind the glass is a site of its own, named by the square it is read from");
        check(Arrays.equals(table.records, lamp * RECORD, lamp * RECORD + LightSites.LAMPLIT, table.records, square * RECORD, square * RECORD + LightSites.LAMPLIT)
                && table.records[lamp * RECORD + LightSites.LAMPLIT] == 1f, "lit as the square's site, with the lamp burning");
        frame.site = square;
        check(table.lamp(frame, LightSites.LAMP_NORTH, true) == lamp, "and the same site when laid again");
        frame.site = square;
        check(throwsIllegalState(() -> table.lamp(frame, LightSites.LAMP_NORTH, false)), "a lamp read again otherwise throws");
        frame.site = square;
        int own = table.lamp(frame, LightSites.LAMP_OWN, false);
        check(own != lamp && table.records[own * RECORD + LightSites.LAMPLIT] == 0f, "the square's own lamp is another site, here with none burning");
        SpriteGeometry.Mesh glass = new SpriteGeometry.Mesh();
        frame.site = lamp;
        glass.lightFlat(frame);
        glass.open = 32f * WorldScene.GLASS;
        check(throwsIllegalState(() -> glass.add(0f, 0f, 0f, 0f, 0f, 0.3f, 0.4f, 0.5f)), "glass behind a burning lamp is lit glass: plain glass there throws");
        glass.open = 32f * WallBuilder.LIT_GLASS;
        glass.add(0f, 0f, 0f, 0f, 0f, 0.3f, 0.4f, 0.5f);
        check(glass.floats == SpriteGeometry.VERTEX_FLOATS, "and lit glass is added");
        SpriteGeometry.Frame ceiling = new SpriteGeometry.Frame();
        ceiling.light(0.2f, 0.2f, 0.2f);
        table.put(LightSites.CEILING, 4L, ceiling, 0f, false);
        check(throwsIllegalState(() -> table.lamp(ceiling, LightSites.LAMP_OWN, true)), "a lamp read from a site that names no square throws");
    }

    private static void aTintIsASiteOfItsOwnOverItsBase() {
        LightSites.Table table = new LightSites.Table();
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.light(0.4f, 0.4f, 0.4f);
        int base = table.put(LightSites.SQUARE, 2L, frame, 0f, false);
        frame.light(0.2f, 0.3f, 0.4f);
        int tinted = table.tinted(base, 0.5f, 0.75f, 1f, frame);
        check(tinted != base && frame.site == tinted && table.tints[tinted * 4] == base && table.tints[tinted * 4 + 1] == 0.5f && table.tints[tinted * 4 + 2] == 0.75f
                && table.records[tinted * RECORD + LightSites.GREEN] == 0.3f, "a tint is a site of its own, its base and the tint kept with it, and the light it gives its record");
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
}

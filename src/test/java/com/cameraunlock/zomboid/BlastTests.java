package com.cameraunlock.zomboid;

import java.util.ArrayList;

/**
 * Blast holes: what a blast's pellets on a door or a fence come to (Blasts), how the holes are
 * kept (BlastHoles), and how they are cut through what the picture built (MeshCut).
 */
final class BlastTests {
    private BlastTests() {}

    static void run() {
        Checks.run("aHoleTakesItsOwnAreaOutOfAFace", BlastTests::aHoleTakesItsOwnAreaOutOfAFace);
        Checks.run("nothingIsLeftInsideAHole", BlastTests::nothingIsLeftInsideAHole);
        Checks.run("whatIsLeftOfAFaceHasNoCracks", BlastTests::whatIsLeftOfAFaceHasNoCracks);
        Checks.run("aFaceAwayFromTheHoleIsLeftAsItIs",BlastTests::aFaceAwayFromTheHoleIsLeftAsItIs);
        Checks.run("aFaceAlongThePlaneIsCutWhereItsLineRunsThroughTheHole", BlastTests::aFaceAlongThePlaneIsCutWhereItsLineRunsThroughTheHole);
        Checks.run("aFaceOutOfReachIsNotCut", BlastTests::aFaceOutOfReachIsNotCut);
        Checks.run("aHoleThroughTwoFacesIsLinedBetweenThem", BlastTests::aHoleThroughTwoFacesIsLinedBetweenThem);
        Checks.run("aHoleOffTheLeafsEdgeIsLinedOnlyOverTheLeaf", BlastTests::aHoleOffTheLeafsEdgeIsLinedOnlyOverTheLeaf);
        Checks.run("holesOverOneAnotherAreLinedOnlyRoundTheWoodLeft", BlastTests::holesOverOneAnotherAreLinedOnlyRoundTheWoodLeft);
        Checks.run("aHoleIsLinedThroughABattenOnlyWhereTheBattenIs", BlastTests::aHoleIsLinedThroughABattenOnlyWhereTheBattenIs);
        Checks.run("whatACutVertexCarriesIsBlendedAndItsCodesKept", BlastTests::whatACutVertexCarriesIsBlendedAndItsCodesKept);
        Checks.run("theRunsAThingDrewMoveWithItsTriangles", BlastTests::theRunsAThingDrewMoveWithItsTriangles);
        Checks.run("aMarkLiesOnTheFaceNearestWhereItWasStruckFrom", BlastTests::aMarkLiesOnTheFaceNearestWhereItWasStruckFrom);
        Checks.run("pelletsCloseTogetherBlowAHoleAndSpreadOnesDoNot", BlastTests::pelletsCloseTogetherBlowAHoleAndSpreadOnesDoNot);
        Checks.run("anObjectKeepsItsNewestHolesAndMarks", BlastTests::anObjectKeepsItsNewestHolesAndMarks);
        Checks.run("aShotGoesThroughOnlyWellInsideAHoleOnItsEdge", BlastTests::aShotGoesThroughOnlyWellInsideAHoleOnItsEdge);
        Checks.run("aRoundsHoleIsSmallWhereItWentInAndWideWhereItCameOut", BlastTests::aRoundsHoleIsSmallWhereItWentInAndWideWhereItCameOut);
        Checks.run("anObjectKeepsItsNewestRoundsHolesApartFromTheRest", BlastTests::anObjectKeepsItsNewestRoundsHolesApartFromTheRest);
        Checks.run("holesOverlappingOnRaggedFacesAlwaysCut", BlastTests::holesOverlappingOnRaggedFacesAlwaysCut);
        Checks.run("aSliverOutOnTheMapIsCutWhereItsFloatsAreCoarse", BlastTests::aSliverOutOnTheMapIsCutWhereItsFloatsAreCoarse);
        Checks.run("aCornerOfAnOutlineGrazingAFaceEdgeTakesOnlyWhatIsInTheHole", BlastTests::aCornerOfAnOutlineGrazingAFaceEdgeTakesOnlyWhatIsInTheHole);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static final int V = SpriteGeometry.VERTEX_FLOATS;

    /** A vertex at (x, y, up) carrying red 1 and a code of 3 in its light's mode, the rest 0. */
    private static float[] vertex(float x, float y, float up) {
        float[] v = new float[V];
        v[0] = x;
        v[1] = y;
        v[2] = up;
        v[5] = 1f;
        v[8] = 3f;
        return v;
    }

    /** A face of a north edge at y = across, from s0 to s1 along and t0 to t1 up, as two triangles. */
    private static void face(SpriteGeometry.Mesh mesh, float s0, float s1, float t0, float t1, float across) {
        mesh.addVertex(vertex(s0, across, t0));
        mesh.addVertex(vertex(s1, across, t0));
        mesh.addVertex(vertex(s1, across, t1));
        mesh.addVertex(vertex(s0, across, t0));
        mesh.addVertex(vertex(s1, across, t1));
        mesh.addVertex(vertex(s0, across, t1));
    }

    /** The area of the mesh's triangles from `from` on, seen across a north edge's plane. */
    private static float areaAcross(SpriteGeometry.Mesh mesh, int from) {
        float sum = 0f;
        for (int at = from; at < mesh.floats; at += 3 * V) {
            float[] f = mesh.vertices;
            float sA = f[at], tA = f[at + 2], sB = f[at + V], tB = f[at + V + 2], sC = f[at + 2 * V], tC = f[at + 2 * V + 2];
            sum += Math.abs((sB - sA) * (tC - tA) - (sC - sA) * (tB - tA)) / 2f;
        }
        return sum;
    }

    private static float polygonArea(float[] o) {
        float sum = 0f;
        int points = o.length / 2;
        for (int i = 0; i < points; i++) {
            int j = (i + 1) % points;
            sum += o[2 * i] * o[2 * j + 1] - o[2 * j] * o[2 * i + 1];
        }
        return sum / 2f;
    }

    private static final float[] OUTLINE = MeshCut.outline(0.5f, 1f, 0.1f, 0.12f, 0.3f, 18, 1234);

    private static void aHoleTakesItsOwnAreaOutOfAFace() {
        check(polygonArea(OUTLINE) > 0f, "an outline is wound anticlockwise");
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        face(mesh, 0f, 1f, 0f, 2f, 0f);
        MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.05f);
        cut.hole(OUTLINE, 0.5f, 1f);
        cut.cut(mesh, 0);
        float left = areaAcross(mesh, 0);
        check(Math.abs(left - (2f - polygonArea(OUTLINE))) < 1e-4f, "a face of 2 square tiles keeps 2 less the hole's " + polygonArea(OUTLINE) + ": kept " + left);
    }

    /**
     * An outline whose corner reaches a hair over a face's edge, between two of its points on the
     * other side: the edge crosses the corner's two sides at the same place. Bent in, the corner
     * leaves a sliver of the face outside the hole there; bent out, it pokes a sliver of hole in.
     */
    private static void aCornerOfAnOutlineGrazingAFaceEdgeTakesOnlyWhatIsInTheHole() {
        float hair = 1e-9f;
        float[] bentIn = {0.25f, -0.125f, 0.375f, hair, 0.5f, -0.125f, 0.5f, 0.5f, 0.25f, 0.5f};
        float[] bentOut = {0.25f, -0.5f, 0.5f, -0.5f, 0.5f, -0.125f, 0.375f, hair, 0.25f, -0.125f};
        check(polygonArea(bentIn) > 0f && polygonArea(bentOut) > 0f, "both outlines are wound anticlockwise");
        SpriteGeometry.Mesh in = new SpriteGeometry.Mesh();
        face(in, 0f, 1f, 0f, 2f, 0f);
        MeshCut cutIn = new MeshCut(0f, 0f, 1f, 0f, 0.05f);
        cutIn.hole(bentIn, 0.375f, 0.25f);
        cutIn.cut(in, 0);
        check(Math.abs(areaAcross(in, 0) - (2f - 0.125f)) < 1e-4f, "a corner bent in on the face's foot leaves the face less the hole's 0.125 inside it: kept " + areaAcross(in, 0));
        SpriteGeometry.Mesh out = new SpriteGeometry.Mesh();
        face(out, 0f, 1f, 0f, 2f, 0f);
        MeshCut cutOut = new MeshCut(0f, 0f, 1f, 0f, 0.05f);
        cutOut.hole(bentOut, 0.375f, -0.3f);
        cutOut.cut(out, 0);
        check(Math.abs(areaAcross(out, 0) - 2f) < 1e-4f, "a corner bent out over the face's foot takes next to nothing of it: kept " + areaAcross(out, 0));
    }

    private static void nothingIsLeftInsideAHole() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        face(mesh, 0f, 1f, 0f, 2f, 0f);
        MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.05f);
        cut.hole(OUTLINE, 0.5f, 1f);
        cut.cut(mesh, 0);
        float[] f = mesh.vertices;
        int inside = 0;
        for (int at = 0; at < mesh.floats; at += 3 * V) {
            float s = (f[at] + f[at + V] + f[at + 2 * V]) / 3f, t = (f[at + 2] + f[at + V + 2] + f[at + 2 * V + 2]) / 3f;
            if (MeshCut.inside(OUTLINE, s, t)) {
                inside++;
            }
        }
        check(inside == 0, "no triangle left has its middle in the hole: " + inside + " do");
    }

    /**
     * Holes through a face drawn as a grid of quads, and through one large triangle a hole sits
     * wholly inside: every edge of what is left is either on the face's border or the hole's, or
     * is the edge of another triangle the other way round, to the bit. An edge with no such twin
     * off both borders is a crack the world behind shows through.
     */
    private static void whatIsLeftOfAFaceHasNoCracks() {
        float[][] places = {{0.5f, 1f}, {0.37f, 0.61f}, {0.25f, 1.25f}, {0.81f, 0.5f}, {0.6f, 1.4f}};
        for (int seed = 0; seed < places.length; seed++) {
            SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 8; j++) {
                    face(mesh, i * 0.25f, (i + 1) * 0.25f, j * 0.25f, (j + 1) * 0.25f, 0f);
                }
            }
            MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.05f);
            float[] one = MeshCut.outline(places[seed][0], places[seed][1], 0.1f, 0.12f, 0.3f, 18, seed * 77 + 5);
            float[] two = MeshCut.outline(places[seed][0] + 0.09f, places[seed][1] - 0.05f, 0.06f, 0.07f, 0.3f, 18, seed * 77 + 6);
            cut.hole(one, places[seed][0], places[seed][1]);
            cut.hole(two, places[seed][0] + 0.09f, places[seed][1] - 0.05f);
            cut.cut(mesh, 0);
            int cracks = cracks(mesh, 0f, 1f, 0f, 2f, one, two);
            check(cracks == 0, "a grid cut by two holes at " + places[seed][0] + ", " + places[seed][1] + " has no cracks: " + cracks + " edges have no twin");
        }
        SpriteGeometry.Mesh big = new SpriteGeometry.Mesh();
        face(big, 0f, 4f, 0f, 4f, 0f);
        MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.05f);
        float[] inner = MeshCut.outline(3f, 1f, 0.1f, 0.12f, 0.3f, 18, 99);
        cut.hole(inner, 3f, 1f);
        cut.cut(big, 0);
        check(Math.abs(areaAcross(big, 0) - (16f - polygonArea(inner))) < 1e-3f, "a hole wholly inside one triangle takes its own area: kept " + areaAcross(big, 0));
        check(cracks(big, 0f, 4f, 0f, 4f, inner) == 0, "and leaves no cracks");
    }

    /** The edges of the mesh's triangles that have no twin the other way round and are on neither the face's border nor an outline. */
    private static int cracks(SpriteGeometry.Mesh mesh, float s0, float s1, float t0, float t1, float[]... outlines) {
        java.util.HashSet<String> edges = new java.util.HashSet<>();
        float[] f = mesh.vertices;
        for (int at = 0; at < mesh.floats; at += 3 * V) {
            for (int k = 0; k < 3; k++) {
                edges.add(edgeKey(f, at + k * V, at + (k + 1) % 3 * V));
            }
        }
        int cracks = 0;
        for (int at = 0; at < mesh.floats; at += 3 * V) {
            for (int k = 0; k < 3; k++) {
                int a = at + k * V, b = at + (k + 1) % 3 * V;
                if (edges.contains(edgeKey(f, b, a))) {
                    continue;
                }
                float s = (f[a] + f[b]) / 2f, t = (f[a + 2] + f[b + 2]) / 2f;
                boolean border = Math.abs(s - s0) < 1e-5f || Math.abs(s - s1) < 1e-5f || Math.abs(t - t0) < 1e-5f || Math.abs(t - t1) < 1e-5f;
                boolean rim = false;
                for (float[] o : outlines) {
                    rim |= nearOutline(o, s, t);
                }
                if (!border && !rim) {
                    cracks++;
                }
            }
        }
        return cracks;
    }

    private static String edgeKey(float[] f, int a, int b) {
        return Float.floatToRawIntBits(f[a]) + "," + Float.floatToRawIntBits(f[a + 1]) + "," + Float.floatToRawIntBits(f[a + 2]) + ">"
                + Float.floatToRawIntBits(f[b]) + "," + Float.floatToRawIntBits(f[b + 1]) + "," + Float.floatToRawIntBits(f[b + 2]);
    }

    private static boolean nearOutline(float[] o, float s, float t) {
        int points = o.length / 2;
        for (int i = 0; i < points; i++) {
            int j = (i + 1) % points;
            float pS = o[2 * i], pT = o[2 * i + 1], dS = o[2 * j] - pS, dT = o[2 * j + 1] - pT;
            float share = Math.max(0f, Math.min(1f, ((s - pS) * dS + (t - pT) * dT) / (dS * dS + dT * dT)));
            float offS = s - (pS + dS * share), offT = t - (pT + dT * share);
            if (offS * offS + offT * offT < 1e-9f) {
                return true;
            }
        }
        return false;
    }

    private static void aFaceAwayFromTheHoleIsLeftAsItIs() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        face(mesh, 2f, 3f, 0f, 2f, 0f);
        float[] before = java.util.Arrays.copyOf(mesh.vertices, mesh.floats);
        MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.05f);
        cut.hole(OUTLINE, 0.5f, 1f);
        cut.cut(mesh, 0);
        check(java.util.Arrays.equals(before, java.util.Arrays.copyOf(mesh.vertices, mesh.floats)), "a face a tile off the hole keeps its triangles as they were");
    }

    private static void aFaceAlongThePlaneIsCutWhereItsLineRunsThroughTheHole() {
        // A board's side at s = 0.5, from across -0.02 to 0.02 and up 0 to 2: the hole's middle is on its line.
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        mesh.addVertex(vertex(0.5f, -0.02f, 0f));
        mesh.addVertex(vertex(0.5f, 0.02f, 0f));
        mesh.addVertex(vertex(0.5f, 0.02f, 2f));
        mesh.addVertex(vertex(0.5f, -0.02f, 0f));
        mesh.addVertex(vertex(0.5f, 0.02f, 2f));
        mesh.addVertex(vertex(0.5f, -0.02f, 2f));
        MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.05f);
        cut.hole(OUTLINE, 0.5f, 1f);
        cut.cut(mesh, 0);
        float low = Float.POSITIVE_INFINITY, high = Float.NEGATIVE_INFINITY, kept = 0f;
        float[] f = mesh.vertices;
        for (int at = 0; at < mesh.floats; at += 3 * V) {
            float uA = f[at + 2], uB = f[at + V + 2], uC = f[at + 2 * V + 2], aA = f[at + 1], aB = f[at + V + 1], aC = f[at + 2 * V + 1];
            kept += Math.abs((aB - aA) * (uC - uA) - (aC - aA) * (uB - uA)) / 2f;
            float middle = (uA + uB + uC) / 3f;
            if (middle > 0.9f && middle < 1.1f) {
                low = Math.min(low, middle);
                high = Math.max(high, middle);
            }
        }
        check(low == Float.POSITIVE_INFINITY, "nothing of the side is left across the hole's middle");
        check(kept < 0.04f * 2f - 0.04f * 0.15f && kept > 0.04f * 2f - 0.04f * 0.4f, "the side loses about the hole's height of itself: kept " + kept + " of 0.08");
    }

    private static void aFaceOutOfReachIsNotCut() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        face(mesh, 0f, 1f, 0f, 2f, 0.2f);
        MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.05f);
        cut.hole(OUTLINE, 0.5f, 1f);
        cut.cut(mesh, 0);
        check(mesh.floats == 6 * V, "a face 0.2 tiles off the plane, with the cut reaching 0.05, keeps its two triangles");
    }

    private static void aHoleThroughTwoFacesIsLinedBetweenThem() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        face(mesh, 0f, 1f, 0f, 2f, -0.02f);
        face(mesh, 0f, 1f, 0f, 2f, 0.02f);
        MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.05f);
        cut.hole(OUTLINE, 0.5f, 1f);
        cut.cut(mesh, 0);
        int cutFloats = mesh.floats;
        cut.finish();
        check(mesh.floats - cutFloats == 18 * 2 * 3 * V, "the hole's 18 edges are lined with two triangles each");
        float[] f = mesh.vertices;
        boolean between = true, shaded = true;
        for (int at = cutFloats; at < mesh.floats; at += V) {
            between &= Math.abs(f[at + 1]) <= 0.02f + 1e-6f;
            shaded &= Math.abs(f[at + 5] - MeshCut.LINING_SHADE) < 1e-6f;
        }
        check(between, "the lining runs from one face to the other");
        check(shaded, "and is the face's light, darker");
    }

    /**
     * The lining's strips from `from` on, two triangles each, as finish adds them: for each, where
     * it starts and ends along the outline at the face it runs from, and how far across it runs
     * from and to.
     */
    private static ArrayList<float[]> strips(SpriteGeometry.Mesh mesh, int from) {
        ArrayList<float[]> strips = new ArrayList<>();
        float[] f = mesh.vertices;
        for (int at = from; at < mesh.floats; at += 6 * V) {
            float low = Float.POSITIVE_INFINITY, high = Float.NEGATIVE_INFINITY;
            for (int k = 0; k < 6; k++) {
                low = Math.min(low, f[at + k * V + 1]);
                high = Math.max(high, f[at + k * V + 1]);
            }
            strips.add(new float[] {f[at], f[at + 2], f[at + V], f[at + V + 2], low, high});
        }
        return strips;
    }

    /** How long the strips run along the outline, at the face they run from. */
    private static float linedLength(ArrayList<float[]> strips) {
        float sum = 0f;
        for (float[] strip : strips) {
            sum += (float) Math.hypot(strip[2] - strip[0], strip[3] - strip[1]);
        }
        return sum;
    }

    /** How long the outline is where `kept` holds, measured a thousandth of an edge at a time. */
    private static float lengthWhere(float[] o, java.util.function.BiPredicate<Float, Float> kept) {
        float sum = 0f;
        int points = o.length / 2, steps = 1000;
        for (int i = 0; i < points; i++) {
            int j = (i + 1) % points;
            float dS = (o[2 * j] - o[2 * i]) / steps, dT = (o[2 * j + 1] - o[2 * i + 1]) / steps;
            for (int k = 0; k < steps; k++) {
                if (kept.test(o[2 * i] + dS * (k + 0.5f), o[2 * i + 1] + dT * (k + 0.5f))) {
                    sum += (float) Math.hypot(dS, dT);
                }
            }
        }
        return sum;
    }

    /** A hole whose middle is on a leaf's edge, as a blast at a door's edge leaves: no lining stands past the leaf, where there is no wood. */
    private static void aHoleOffTheLeafsEdgeIsLinedOnlyOverTheLeaf() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        face(mesh, 0f, 0.5f, 0f, 2f, -0.02f);
        face(mesh, 0f, 0.5f, 0f, 2f, 0.02f);
        MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.05f);
        cut.hole(OUTLINE, 0.5f, 1f);
        cut.cut(mesh, 0);
        int cutFloats = mesh.floats;
        cut.finish();
        float furthestAlong = Float.NEGATIVE_INFINITY;
        for (int at = cutFloats; at < mesh.floats; at += V) {
            furthestAlong = Math.max(furthestAlong, mesh.vertices[at]);
        }
        check(furthestAlong <= 0.5f + 1e-6f, "no lining stands past the leaf's edge at 0.5: it reaches " + furthestAlong);
        float lined = linedLength(strips(mesh, cutFloats)), over = lengthWhere(OUTLINE, (s, t) -> s < 0.5f);
        check(Math.abs(lined - over) < 1e-3f, "the outline is lined all the way round where it is over the leaf: " + lined + " of " + over);
    }

    /** A second blast's hole over the first's: each is lined where it cuts the wood left, and neither's lining crosses the other's opening. */
    private static void holesOverOneAnotherAreLinedOnlyRoundTheWoodLeft() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        face(mesh, 0f, 1f, 0f, 2f, -0.02f);
        face(mesh, 0f, 1f, 0f, 2f, 0.02f);
        float[] two = MeshCut.outline(0.62f, 1.05f, 0.08f, 0.1f, 0.3f, 18, 4321);
        MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.05f);
        cut.hole(OUTLINE, 0.5f, 1f);
        cut.hole(two, 0.62f, 1.05f);
        cut.cut(mesh, 0);
        int cutFloats = mesh.floats;
        cut.finish();
        ArrayList<float[]> strips = strips(mesh, cutFloats);
        int across = 0;
        for (float[] strip : strips) {
            float s = (strip[0] + strip[2]) / 2f, t = (strip[1] + strip[3]) / 2f;
            if (MeshCut.inside(nearOutline(OUTLINE, s, t) ? two : OUTLINE, s, t)) {
                across++;
            }
        }
        check(across == 0, "no strip of either hole's lining lies in the other's opening: " + across + " of " + strips.size() + " do");
        float left = lengthWhere(OUTLINE, (s, t) -> !MeshCut.inside(two, s, t)) + lengthWhere(two, (s, t) -> !MeshCut.inside(OUTLINE, s, t));
        check(Math.abs(linedLength(strips) - left) < 1e-3f, "the two are lined all the way round what is left: " + linedLength(strips) + " of " + left);
    }

    /** A hole through boards and the batten across them: the lining is as deep as the batten only where the batten is, and as the boards elsewhere. */
    private static void aHoleIsLinedThroughABattenOnlyWhereTheBattenIs() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        face(mesh, 0f, 1f, 0f, 2f, -0.04f);
        face(mesh, 0f, 1f, 0f, 2f, 0.04f);
        face(mesh, 0f, 1f, 0.9f, 1f, 0.062f);
        MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.08f);
        cut.hole(OUTLINE, 0.5f, 1f);
        cut.cut(mesh, 0);
        int cutFloats = mesh.floats;
        cut.finish();
        ArrayList<float[]> strips = strips(mesh, cutFloats);
        int wrong = 0;
        for (float[] strip : strips) {
            float t = (strip[1] + strip[3]) / 2f;
            float deep = t > 0.9f && t < 1f ? 0.062f : 0.04f;
            if (Math.abs(strip[4] + 0.04f) > 1e-6f || Math.abs(strip[5] - deep) > 1e-6f) {
                wrong++;
            }
        }
        check(wrong == 0, "every strip runs from the boards' face at -0.04 to the batten's face where it crosses the batten, and to the boards' other face elsewhere: "
                + wrong + " of " + strips.size() + " do not");
        check(Math.abs(linedLength(strips) - lengthWhere(OUTLINE, (s, t) -> true)) < 1e-3f, "and the hole is lined all the way round");
    }

    private static void whatACutVertexCarriesIsBlendedAndItsCodesKept() {
        float[] tri = new float[3 * V];
        float[] a = vertex(0f, 0f, 0f), b = vertex(1f, 0f, 0f), c = vertex(0f, 0f, 1f);
        a[3] = 0f;
        b[3] = 1f;
        c[3] = 0f;
        a[SpriteGeometry.RECORD_AT] = 7f;
        b[SpriteGeometry.RECORD_AT] = 9f;
        c[SpriteGeometry.RECORD_AT] = 9f;
        System.arraycopy(a, 0, tri, 0, V);
        System.arraycopy(b, 0, tri, V, V);
        System.arraycopy(c, 0, tri, 2 * V, V);
        float[] out = new float[V];
        MeshCut.blendAt(tri, 0, 0.6f, 0.3f, 0.1f, out, 0);
        check(Math.abs(out[3] - 0.3f) < 1e-6f, "a texture coordinate is blended");
        check(out[8] == 3f, "what is the same at all three corners is kept as it is");
        check(out[SpriteGeometry.RECORD_AT] == 7f, "a code is the nearest corner's, never a blend");
    }

    private static void theRunsAThingDrewMoveWithItsTriangles() {
        SpriteGeometry.MeshSet set = new SpriteGeometry.MeshSet();
        face(set.get(1), 5f, 6f, 0f, 2f, 0f);
        set.own(42L);
        int[][] ends = MeshCut.ends(set);
        SpriteGeometry.Mesh mesh = set.get(1);
        face(mesh, 0f, 1f, 0f, 2f, 0f);
        set.own(43L);
        face(mesh, 0f, 1f, 0f, 2f, 0.01f);
        MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.05f);
        cut.hole(OUTLINE, 0.5f, 1f);
        cut.since(ends, set);
        set.own(Outlines.NONE);
        check(mesh.spans == 2, "the two things' runs are kept: " + mesh.spans);
        check(mesh.spanFrom[0] == 6 * V, "the first starts where it did, past what was drawn before the cut");
        check(mesh.spanTo[0] == mesh.spanFrom[1], "the second starts where the first, cut, ends");
        check(mesh.spanTo[1] <= mesh.floats && mesh.spanTo[1] > mesh.spanFrom[1], "and ends inside the mesh");
        check(Math.abs(areaAcross(mesh, 6 * V) - 2f * (2f - polygonArea(OUTLINE))) < 1e-3f, "both faces the thing drew are cut, and what was drawn before is not");
    }

    private static void aMarkLiesOnTheFaceNearestWhereItWasStruckFrom() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        face(mesh, 0f, 1f, 0f, 2f, -0.02f);
        face(mesh, 0f, 1f, 0f, 2f, 0.02f);
        MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.05f);
        cut.mark(0.3f, 0.5f, MeshCut.outline(0.3f, 0.5f, 0.014f, 0.014f, 0.35f, 7, 5), -1);
        cut.cut(mesh, 0);
        int before = mesh.floats;
        cut.finish();
        check(mesh.floats - before == 7 * 3 * V, "a mark of 7 points is a fan of 7 triangles");
        boolean onNear = true;
        for (int at = before; at < mesh.floats; at += V) {
            onNear &= Math.abs(mesh.vertices[at + 1] - (-0.02f - MeshCut.MARK_OFF)) < 1e-6f;
        }
        check(onNear, "a mark struck from the -y side lies just off the face at y = -0.02");
    }

    private static Blasts.Struck at(float along, float up) {
        return new Blasts.Struck(null, null, true, along, up, 1);
    }

    private static void pelletsCloseTogetherBlowAHoleAndSpreadOnesDoNot() {
        ArrayList<Blasts.Struck> close = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            close.add(at(0.5f + 0.02f * i, 0.4f + 0.004f * i));
        }
        Blasts.Cluster hole = Blasts.cluster(close);
        check(hole != null && hole.members().size() == 5, "five pellets within a few centimetres blow one hole");
        check(hole != null && hole.radius() >= Blasts.SMALLEST && hole.radius() <= Blasts.LARGEST, "its radius is between the least and the most");
        ArrayList<Blasts.Struck> spread = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            spread.add(at(0.1f + 0.2f * i, 0.4f));
        }
        check(Blasts.cluster(spread) == null, "five pellets 20 centimetres apart blow none");
        ArrayList<Blasts.Struck> some = new ArrayList<>();
        some.add(at(0.5f, 0.4f));
        some.add(at(0.52f, 0.41f));
        some.add(at(0.49f, 0.39f));
        some.add(at(0.1f, 0.1f));
        some.add(at(0.95f, 0.7f));
        Blasts.Cluster three = Blasts.cluster(some);
        check(three != null && three.members().size() == 3, "three close together blow a hole and the two strays do not join it");
        ArrayList<Blasts.Struck> two = new ArrayList<>();
        two.add(at(0.5f, 0.4f));
        two.add(at(0.51f, 0.4f));
        check(Blasts.cluster(two) == null, "two pellets are not enough");
    }

    private static void anObjectKeepsItsNewestHolesAndMarks() {
        float[] entries = new float[0];
        for (int i = 0; i < BlastHoles.MOST_HOLES + 2; i++) {
            entries = BlastHoles.with(entries, true, i, 0.5f, 0.1f, i, 0);
        }
        entries = BlastHoles.with(entries, false, 0.3f, 0.2f, BlastHoles.MARK_RADIUS, 99, -1);
        check(entries.length == (BlastHoles.MOST_HOLES + 1) * BlastHoles.FLOATS, "the most holes and one mark are kept");
        check(entries[BlastHoles.ALONG] == 2f, "the oldest holes went first");
        float[] back = BlastHoles.parse(BlastHoles.write(entries));
        check(java.util.Arrays.equals(entries, back), "what is saved reads back the same");
        check(BlastHoles.parse("").length == 0, "nothing saved is nothing");
        check(refused("0.5,0.5,NaN,7,0,1") && refused("0.5,Infinity,0.1,7,0,1") && refused("0.5,0.5,0.1,7,0"), "a list with a number that is not finite, or one short, is refused");
        check(refused("0.5,0.5,1e30,7,0,1") && refused("0.5,0.5,0,7,0,1") && refused("0.5,0.5,0.1,7,3,1") && refused("0.5,0.5,0.1,7,0.5,1")
                && refused("0.5,0.5,0.1,7,0,2"), "and so is one with a hole no shot makes: a radius past a tile or none, a side or an edge that is no side or edge");
        String many = String.join(",", java.util.Collections.nCopies(BlastHoles.MOST_HOLES + BlastHoles.MOST_MARKS + BlastHoles.MOST_ROUNDS + 1, "0.5,0.5,0.1,7,0,1"));
        check(refused(many), "and one with more holes than an object keeps");
    }

    private static boolean refused(String saved) {
        try {
            BlastHoles.parse(saved);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    /**
     * A round from the +y side through a wall of two skins of a 2 by 4 grid of quads each: the
     * skin it went in by loses the way in, the far one the wider way out, neither with a crack,
     * and the lining runs from the one outline to the other.
     */
    private static void aRoundsHoleIsSmallWhereItWentInAndWideWhereItCameOut() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        for (float across : new float[] {-0.04f, 0.04f}) {
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 4; j++) {
                    face(mesh, i * 0.5f, (i + 1) * 0.5f, j * 0.5f, (j + 1) * 0.5f, across);
                }
            }
        }
        float[] entries = BlastHoles.with(new float[0], true, 0.47f, 1.1f / ViewCamera.LEVEL_HEIGHT, BlastHoles.ROUND_RADIUS * 3f, 4242, BlastHoles.ROUND);
        MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.08f);
        BlastHoles.into(cut, entries, true, 0f, 1f, 0f, ViewCamera.LEVEL_HEIGHT, 1);
        cut.cut(mesh, 0);
        int cutFloats = mesh.floats;
        cut.finish();
        float r = BlastHoles.ROUND_RADIUS * 3f;
        float[] in = MeshCut.outline(0.47f, 1.1f, r, r, BlastHoles.ROUND_RAGGED, BlastHoles.ROUND_POINTS, 4242);
        float[] out = MeshCut.outline(0.47f, 1.1f, r * BlastHoles.EXIT_WIDER, r * BlastHoles.EXIT_WIDER * BlastHoles.EXIT_TALL, BlastHoles.EXIT_RAGGED,
                BlastHoles.ROUND_POINTS, 4242);
        check(polygonArea(out) > 3f * polygonArea(in), "the way out is wider than the way in: " + polygonArea(out) + " against " + polygonArea(in));
        SpriteGeometry.Mesh near = new SpriteGeometry.Mesh(), far = new SpriteGeometry.Mesh();
        float[] f = mesh.vertices;
        for (int at = 0; at < cutFloats; at += 3 * V) {
            SpriteGeometry.Mesh to = f[at + 1] > 0f ? near : far;
            for (int k = 0; k < 3; k++) {
                to.addVertex(java.util.Arrays.copyOfRange(f, at + k * V, at + (k + 1) * V));
            }
        }
        check(Math.abs(areaAcross(near, 0) - (2f - polygonArea(in))) < 1e-4f, "the skin on the round's side loses the way in: kept " + areaAcross(near, 0));
        check(Math.abs(areaAcross(far, 0) - (2f - polygonArea(out))) < 1e-4f, "the far skin loses the way out: kept " + areaAcross(far, 0));
        check(cracks(near, 0f, 1f, 0f, 2f, in) == 0 && cracks(far, 0f, 1f, 0f, 2f, out) == 0, "and neither has a crack");
        check(mesh.floats - cutFloats == BlastHoles.ROUND_POINTS * 2 * 3 * V, "the hole's edges are lined with two triangles each");
        boolean ends = true;
        for (int at = cutFloats; at < mesh.floats; at += V) {
            float s = f[at], t = f[at + 2];
            float[] o = f[at + 1] > 0f ? in : out;
            boolean onOutline = false;
            for (int i = 0; i < o.length; i += 2) {
                onOutline |= Math.abs(o[i] - s) < 1e-5f && Math.abs(o[i + 1] - t) < 1e-5f;
            }
            ends &= onOutline && Math.abs(Math.abs(f[at + 1]) - 0.04f) < 1e-5f;
        }
        check(ends, "the lining runs from the way in on the near skin to the way out on the far one");
    }

    /**
     * Blasts, rounds and marks heaped on a fence of irregular faces on a slope, as a shotgun emptied
     * into one does, cut without an error: among them the ones where an outline grazed a face's
     * corner or edge and its crossings came out of order, which once threw in a build and turned
     * first person off. Exact runs lay the seven blasts one fence took in the game, run by run with
     * other seeds; the rest lay holes of every kind about them.
     */
    private static void holesOverlappingOnRaggedFacesAlwaysCut() {
        float[][] blasts = {{0.877f, 0.503f, 0.05f}, {0.383f, 0.514f, 0.05f}, {0.117f, 0.517f, 0.05f}, {0.164f, 0.523f, 0.05f},
                {0.290f, 0.424f, 0.072f}, {0.443f, 0.423f, 0.061f}, {0.307f, 0.518f, 0.054f}};
        int[] exactOnes = {1543, 17853, 36538}, mixedOnes = {95, 100, 186, 509, 741, 774, 16704};
        int threw = 0;
        String first = null;
        for (int pass = 0; pass < 4; pass++) {
            boolean exact = pass % 2 == 0;
            float out = pass < 2 ? 0f : 12257f;
            int[] known = exact ? exactOnes : mixedOnes;
            for (int i = 0; i < 1500 + known.length; i++) {
                int run = i < 1500 ? i : known[i - 1500];
                try {
                    cutRaggedFence(new java.util.Random(run), blasts, exact, out);
                } catch (RuntimeException e) {
                    threw++;
                    first = first == null ? (exact ? "exact" : "mixed") + " run " + run + " at " + out + ": " + e.getMessage() : first;
                }
            }
        }
        check(threw == 0, threw + " runs threw, the first " + first);
    }

    /** One run of holesOverlappingOnRaggedFacesAlwaysCut. */
    private static void cutRaggedFence(java.util.Random r, float[][] blasts, boolean exact, float out) {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        float level = ViewCamera.LEVEL_HEIGHT, foot = 0.7f;
        for (float across : new float[] {-0.04f, 0.04f}) {
            int columns = 1 + r.nextInt(6);
            float[] xs = new float[columns + 1];
            for (int i = 1; i < columns; i++) {
                xs[i] = (float) i / columns + (r.nextFloat() - 0.5f) * 0.1f / columns;
            }
            xs[columns] = 1f;
            for (int i = 0; i < columns; i++) {
                float slope = (r.nextFloat() - 0.5f) * 0.4f;
                float b0 = foot + slope * xs[i], b1 = foot + slope * xs[i + 1], top = foot + level * (0.7f + r.nextFloat() * 0.3f);
                mesh.addVertex(vertex(out + xs[i], across, b0));
                mesh.addVertex(vertex(out + xs[i + 1], across, b1));
                mesh.addVertex(vertex(out + xs[i + 1], across, top));
                mesh.addVertex(vertex(out + xs[i], across, b0));
                mesh.addVertex(vertex(out + xs[i + 1], across, top));
                mesh.addVertex(vertex(out + xs[i], across, top));
            }
        }
        float[] entries = new float[0];
        if (exact) {
            for (float[] b : blasts) {
                entries = BlastHoles.with(entries, true, b[0], b[1], b[2], r.nextInt(1 << 20), 0);
            }
        }
        int count = exact ? 0 : 3 + r.nextInt(8);
        for (int h = 0; h < count; h++) {
            float[] b = blasts[r.nextInt(blasts.length)];
            float along = b[0] + (r.nextFloat() - 0.5f) * 0.3f, up = b[1] + (r.nextFloat() - 0.5f) * 0.15f;
            int kind = r.nextInt(3);
            if (kind == 0) {
                entries = BlastHoles.with(entries, true, along, up, b[2] * (0.8f + r.nextFloat() * 0.6f), r.nextInt(1 << 20), 0);
            } else if (kind == 1) {
                entries = BlastHoles.with(entries, true, along, up, BlastHoles.ROUND_RADIUS * (1f + r.nextFloat() * 2f), r.nextInt(1 << 20),
                        (r.nextBoolean() ? 1 : -1) * BlastHoles.ROUND);
            } else {
                entries = BlastHoles.with(entries, true, along, up, BlastHoles.MARK_RADIUS, r.nextInt(1 << 20), r.nextBoolean() ? 1 : -1);
            }
        }
        MeshCut cut = new MeshCut(out, 0f, 1f, 0f, 0.08f);
        BlastHoles.into(cut, entries, true, 0f, 1f, foot, level, 1);
        cut.cut(mesh, 0);
        cut.finish();
    }

    /**
     * A thin face of a wall at 12257, 6834 that a blast's hole crosses near its far end, as the
     * game built it: its corners and the outline are the ones logged when it threw. Out there a
     * coordinate's floats are a 2048th of a tile apart, and the two crossings of the sliver, taken
     * off their vertices, came out the wrong way round and left an outline with no ear.
     */
    private static void aSliverOutOnTheMapIsCutWhereItsFloatsAreCoarse() {
        float[] outline = {0.51219875f, 0.95093054f, 0.5149327f, 0.97389555f, 0.50263053f, 1.0070144f, 0.48270097f, 1.0291989f,
                0.45091352f, 1.0093796f, 0.42838237f, 1.0398346f, 0.40504196f, 1.014316f, 0.3964227f, 0.98264956f, 0.38945898f, 0.96599f,
                0.37554482f, 0.9357951f, 0.39876086f, 0.92835987f, 0.38326222f, 0.89352685f, 0.40988925f, 0.87234074f, 0.4270366f, 0.8776123f,
                0.4524555f, 0.85794884f, 0.4705865f, 0.88180804f, 0.4870303f, 0.89890176f, 0.4843071f, 0.9219395f};
        float x = 12257.01f, y = 6834f;
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        mesh.addVertex(vertex(x, y, -0.046875f));
        mesh.addVertex(vertex(x, y + 0.54003906f, 1.1519477f));
        mesh.addVertex(vertex(x, y + 0.5488281f, 1.1719604f));
        MeshCut cut = new MeshCut(12257f, y, 0f, 1f, 0.08f);
        cut.hole(outline, 0.45f, 0.95f);
        String threw = null;
        try {
            cut.cut(mesh, 0);
        } catch (IllegalStateException e) {
            threw = e.getMessage();
        }
        check(threw == null, "the sliver is cut: " + threw);
    }

    private static void anObjectKeepsItsNewestRoundsHolesApartFromTheRest() {
        float[] entries = BlastHoles.with(new float[0], true, 0.1f, 0.5f, 0.1f, 1, 0);
        entries = BlastHoles.with(entries, true, 0.2f, 0.5f, BlastHoles.MARK_RADIUS, 2, 1);
        for (int i = 0; i < BlastHoles.MOST_ROUNDS + 3; i++) {
            entries = BlastHoles.with(entries, false, i, 0.5f, BlastHoles.ROUND_RADIUS, i, -BlastHoles.ROUND);
        }
        check(entries.length == (BlastHoles.MOST_ROUNDS + 2) * BlastHoles.FLOATS, "the most rounds' holes are kept, beside the blast's hole and the mark");
        check(entries[BlastHoles.SIDE] == 0f && entries[BlastHoles.FLOATS + BlastHoles.SIDE] == 1f, "which stay");
        check(entries[2 * BlastHoles.FLOATS + BlastHoles.ALONG] == 3f, "and the oldest rounds' holes went first");
        check(BlastHoles.inAHole(entries, false, 10f, 0.5f), "a later shot goes through a round's hole");
        check(!BlastHoles.inAHole(entries, false, 10f + BlastHoles.ROUND_RADIUS, 0.5f), "but not past where it went in");
    }

    private static void aShotGoesThroughOnlyWellInsideAHoleOnItsEdge() {
        float[] entries = BlastHoles.with(new float[0], true, 0.5f, 0.4f, 0.1f, 1, 0);
        check(BlastHoles.inAHole(entries, true, 0.5f, 0.4f), "a shot through the middle goes through");
        check(!BlastHoles.inAHole(entries, false, 0.5f, 0.4f), "but not on the other edge of the square");
        check(!BlastHoles.inAHole(entries, true, 0.5f + 0.095f, 0.4f), "nor grazing its rim");
        float[] marked = BlastHoles.with(new float[0], true, 0.5f, 0.4f, 0.1f, 1, 1);
        check(!BlastHoles.inAHole(marked, true, 0.5f, 0.4f), "a mark is no hole");
    }
}

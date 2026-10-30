package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.IdentityHashMap;

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
        Checks.run("whatACutVertexCarriesIsBlendedAndItsCodesKept", BlastTests::whatACutVertexCarriesIsBlendedAndItsCodesKept);
        Checks.run("theRunsAThingDrewMoveWithItsTriangles", BlastTests::theRunsAThingDrewMoveWithItsTriangles);
        Checks.run("aMarkLiesOnTheFaceNearestWhereItWasStruckFrom", BlastTests::aMarkLiesOnTheFaceNearestWhereItWasStruckFrom);
        Checks.run("pelletsCloseTogetherBlowAHoleAndSpreadOnesDoNot", BlastTests::pelletsCloseTogetherBlowAHoleAndSpreadOnesDoNot);
        Checks.run("anObjectKeepsItsNewestHolesAndMarks", BlastTests::anObjectKeepsItsNewestHolesAndMarks);
        Checks.run("aShotGoesThroughOnlyWellInsideAHoleOnItsEdge", BlastTests::aShotGoesThroughOnlyWellInsideAHoleOnItsEdge);
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
        IdentityHashMap<SpriteGeometry.Mesh, Integer> ends = MeshCut.ends(set);
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

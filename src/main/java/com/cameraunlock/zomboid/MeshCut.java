package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.IdentityHashMap;

/**
 * Holes cut through what one thing drew into meshes, and marks stamped on its faces.
 *
 * The thing stands on a plane, a door's leaf or a length of fence on its square's edge, given by
 * an origin and the way along it on the map: the way across is the other axis of the map, and up
 * is up. A point of the plane is (s, t): s along it from the origin and t up, in tiles. A hole is a
 * ragged outline in the plane, wound anticlockwise round a middle it is a star about, and it goes
 * through whatever the thing drew within reach of the plane. A face that looks across the plane
 * (a leaf's face, a board's) is cut in the plane: wedge by wedge from the middle, each wedge's
 * share of the hole being the triangle of the middle and the edge, so that what a wedge keeps of
 * a face is the convex part beyond the edge, and the wedges between them cover the plane. A face
 * that looks along it (a leaf's edge, a board's side) is cut where its line runs through the
 * outline. Then the sides of the hole are lined from the furthest face cut on one side of the
 * plane to the furthest on the other, in the light of a face that was cut, darker, as the wood
 * inside a hole is.
 *
 * A mark is a small ragged spot laid just off the face nearest the side it was struck from, in
 * that face's light, darker: where one pellet struck a door, which keeps no mark the picture lays
 * over the world, since it swings.
 *
 * Every vertex made carries what the triangle it was cut from carried there: what is the same at
 * all three corners as it is, a code (the light's mode, how it lies, the tree, the light's record)
 * as the nearest corner has it, and the rest blended.
 */
final class MeshCut {
    private static final int V = SpriteGeometry.VERTEX_FLOATS;
    /** The floats of a vertex that are codes, never blended: see SpriteGeometry.VERTEX_FLOATS. */
    private static final boolean[] CODE = new boolean[V];

    static {
        CODE[8] = true;
        CODE[SpriteGeometry.WAY_AT] = true;
        CODE[SpriteGeometry.ROOT_AT] = true;
        CODE[SpriteGeometry.ROOT_AT + 1] = true;
        CODE[SpriteGeometry.RECORD_AT] = true;
    }

    /** How much darker the wood lining a hole is than the face it was cut from, and a mark than the face it is on. */
    static final float LINING_SHADE = 0.55f, MARK_SHADE = 0.22f;
    /** How far off its face a mark lies, in tiles. */
    static final float MARK_OFF = 0.003f;
    /** A triangle with less area than this seen across the plane, in square tiles, looks along it. */
    private static final float EDGE_ON = 1e-7f;

    private final float originX, originY, alongX, alongY, reach;

    private final ArrayList<Hole> holes = new ArrayList<>();
    private final ArrayList<Mark> marks = new ArrayList<>();

    /** One hole: its outline as (s, t) pairs, its middle, and what cutting found of the faces it went through. */
    private static final class Hole {
        final float[] outline;
        final float middleS, middleT;
        final float lowS, highS, lowT, highT;
        float nearest = Float.POSITIVE_INFINITY, furthest = Float.NEGATIVE_INFINITY;
        SpriteGeometry.Mesh donorMesh;
        final float[] donor = new float[V];

        Hole(float[] outline, float middleS, float middleT) {
            this.outline = outline;
            this.middleS = middleS;
            this.middleT = middleT;
            float ls = Float.POSITIVE_INFINITY, hs = Float.NEGATIVE_INFINITY, lt = Float.POSITIVE_INFINITY, ht = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < outline.length; i += 2) {
                ls = Math.min(ls, outline[i]);
                hs = Math.max(hs, outline[i]);
                lt = Math.min(lt, outline[i + 1]);
                ht = Math.max(ht, outline[i + 1]);
            }
            lowS = ls;
            highS = hs;
            lowT = lt;
            highT = ht;
        }

        boolean cutSomething() {
            return donorMesh != null && furthest > nearest;
        }
    }

    /** One mark: where, how big, its outline, the side it was struck from, and the face found for it. */
    private static final class Mark {
        final float s, t;
        final float[] outline;
        final int side;
        SpriteGeometry.Mesh mesh;
        float across = Float.NEGATIVE_INFINITY;
        final float[] vertex = new float[V];

        Mark(float s, float t, float[] outline, int side) {
            this.s = s;
            this.t = t;
            this.outline = outline;
            this.side = side;
        }
    }

    /**
     * A cut on the plane through (originX, originY) that runs along (alongX, alongY), one of the
     * map's axes, reaching reach tiles either side of the plane.
     */
    MeshCut(float originX, float originY, float alongX, float alongY, float reach) {
        if (Math.abs(alongX) + Math.abs(alongY) != 1f || alongX != 0f && alongY != 0f) {
            throw new IllegalArgumentException("a cut runs along one of the map's axes, not (" + alongX + ", " + alongY + ")");
        }
        this.originX = originX;
        this.originY = originY;
        this.alongX = Math.abs(alongX);
        this.alongY = Math.abs(alongY);
        this.reach = reach;
    }

    /** A hole: its outline as (s, t) pairs wound anticlockwise, a star about (middleS, middleT). */
    void hole(float[] outline, float middleS, float middleT) {
        holes.add(new Hole(outline, middleS, middleT));
    }

    /** A mark at (s, t) with the outline given, struck from the side of the plane side (1 or -1) is on. */
    void mark(float s, float t, float[] outline, int side) {
        marks.add(new Mark(s, t, outline, side));
    }

    boolean any() {
        return !holes.isEmpty() || !marks.isEmpty();
    }

    /** Where each mesh the sets have handed out ends now: what a thing adds to it from here on is the thing's. */
    static IdentityHashMap<SpriteGeometry.Mesh, Integer> ends(SpriteGeometry.MeshSet... sets) {
        IdentityHashMap<SpriteGeometry.Mesh, Integer> ends = new IdentityHashMap<>();
        for (SpriteGeometry.MeshSet set : sets) {
            for (SpriteGeometry.Mesh mesh : set.used) {
                ends.put(mesh, mesh.floats);
            }
        }
        return ends;
    }

    /** Cuts what the thing added to the sets' meshes since ends were taken, then lines the holes and lays the marks. */
    void since(IdentityHashMap<SpriteGeometry.Mesh, Integer> ends, SpriteGeometry.MeshSet... sets) {
        if (!any()) {
            return;
        }
        for (SpriteGeometry.MeshSet set : sets) {
            for (int i = 0; i < set.used.size(); i++) {
                SpriteGeometry.Mesh mesh = set.used.get(i);
                Integer end = ends.get(mesh);
                cut(mesh, end == null ? 0 : end);
            }
        }
        finish();
    }

    /**
     * An outline of points round (middleS, middleT), wound anticlockwise: radius across and
     * up, each point pushed in or out by up to ragged of the radius, by the seed.
     */
    static float[] outline(float middleS, float middleT, float radiusS, float radiusT, float ragged, int points, int seed) {
        float[] out = new float[2 * points];
        float turn = (float) (2 * Math.PI / points);
        for (int i = 0; i < points; i++) {
            float angle = turn * (i + 0.35f * noise(seed, 2 * i));
            float scale = 1f + ragged * noise(seed, 2 * i + 1);
            out[2 * i] = middleS + (float) Math.cos(angle) * radiusS * scale;
            out[2 * i + 1] = middleT + (float) Math.sin(angle) * radiusT * scale;
        }
        return out;
    }

    /** A number in [-1, 1] from a seed and an index, the same every time. */
    static float noise(int seed, int index) {
        int h = seed * 0x9E3779B1 + index * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / (float) 0x7FFFFF - 1f;
    }

    private float s(float[] f, int at) {
        return (f[at] - originX) * alongX + (f[at + 1] - originY) * alongY;
    }

    private float across(float[] f, int at) {
        return (f[at] - originX) * alongY + (f[at + 1] - originY) * alongX;
    }

    // What the triangles are cut into: grown as it needs.
    private float[] out = new float[V * 3 * 64];
    private int outFloats;
    private float[] work = new float[V * 3 * 64], next = new float[V * 3 * 64];
    private int workFloats, nextFloats;

    /**
     * Cuts the holes through the mesh's floats from `from` on, which are all the thing's, and finds
     * the faces the marks lie on. The mesh's floats from there are put back cut: see
     * SpriteGeometry.Mesh.replaceFrom.
     */
    void cut(SpriteGeometry.Mesh mesh, int from) {
        int count = mesh.floats - from;
        if (count <= 0) {
            return;
        }
        if (count % (3 * V) != 0) {
            throw new IllegalStateException("a mesh's floats from " + from + " are " + count + ", not whole triangles");
        }
        int triangles = count / (3 * V);
        int[] firstOf = new int[triangles + 1];
        outFloats = 0;
        for (int triangle = 0; triangle < triangles; triangle++) {
            firstOf[triangle] = outFloats;
            workFloats = 0;
            work = room(work, 3 * V);
            System.arraycopy(mesh.vertices, from + triangle * 3 * V, work, 0, 3 * V);
            workFloats = 3 * V;
            for (Hole hole : holes) {
                nextFloats = 0;
                for (int at = 0; at < workFloats; at += 3 * V) {
                    cutOne(mesh, work, at, hole);
                }
                float[] swap = work;
                work = next;
                next = swap;
                workFloats = nextFloats;
            }
            out = room(out, outFloats + workFloats);
            System.arraycopy(work, 0, out, outFloats, workFloats);
            outFloats += workFloats;
        }
        firstOf[triangles] = outFloats;
        for (Mark mark : marks) {
            for (int at = 0; at < outFloats; at += 3 * V) {
                findFace(mesh, out, at, mark);
            }
        }
        mesh.replaceFrom(from, out, outFloats, firstOf);
    }

    /** Adds the linings of the holes that went through something, and the marks that found a face: once every mesh is cut. */
    void finish() {
        for (Hole hole : holes) {
            if (hole.cutSomething()) {
                line(hole);
            }
        }
        for (Mark mark : marks) {
            if (mark.mesh != null) {
                stamp(mark);
            }
        }
    }

    private static float[] room(float[] array, int need) {
        if (need <= array.length) {
            return array;
        }
        return java.util.Arrays.copyOf(array, Math.max(need, array.length * 2));
    }

    private void keep(float[] from, int at) {
        next = room(next, nextFloats + 3 * V);
        System.arraycopy(from, at, next, nextFloats, 3 * V);
        nextFloats += 3 * V;
    }

    // A polygon being clipped: up to 16 corners, each its place in the plane the clip is in and its weights of the triangle's corners.
    private final float[] px = new float[16], py = new float[16], w0 = new float[16], w1 = new float[16], w2 = new float[16];
    private final float[] qx = new float[16], qy = new float[16], v0 = new float[16], v1 = new float[16], v2 = new float[16];
    private int corners;

    /** Cuts one hole out of the triangle at `at` in tri, putting what is left in next. */
    private void cutOne(SpriteGeometry.Mesh mesh, float[] tri, int at, Hole hole) {
        int a = at, b = at + V, c = at + 2 * V;
        float aA = across(tri, a), aB = across(tri, b), aC = across(tri, c);
        if (Math.max(aA, Math.max(aB, aC)) < -reach || Math.min(aA, Math.min(aB, aC)) > reach) {
            keep(tri, at);
            return;
        }
        float sA = s(tri, a), sB = s(tri, b), sC = s(tri, c);
        float tA = tri[a + 2], tB = tri[b + 2], tC = tri[c + 2];
        if (Math.max(sA, Math.max(sB, sC)) <= hole.lowS || Math.min(sA, Math.min(sB, sC)) >= hole.highS
                || Math.max(tA, Math.max(tB, tC)) <= hole.lowT || Math.min(tA, Math.min(tB, tC)) >= hole.highT) {
            keep(tri, at);
            return;
        }
        float area = (sB - sA) * (tC - tA) - (sC - sA) * (tB - tA);
        if (Math.abs(area) > EDGE_ON) {
            int before = nextFloats;
            cutFace(tri, at, hole, sA, tA, sB, tB, sC, tC);
            if (nextFloats != before + 3 * V || !sameTriangle(tri, at, next, before)) {
                if (hole.donorMesh == null) {
                    hole.donorMesh = mesh;
                    System.arraycopy(tri, at, hole.donor, 0, V);
                }
                hole.nearest = Math.min(hole.nearest, Math.min(aA, Math.min(aB, aC)));
                hole.furthest = Math.max(hole.furthest, Math.max(aA, Math.max(aB, aC)));
            }
            return;
        }
        cutEdgeOn(tri, at, hole, sA, tA, sB, tB, sC, tC, aA, aB, aC);
    }

    private static boolean sameTriangle(float[] one, int at, float[] other, int otherAt) {
        for (int i = 0; i < 3 * V; i++) {
            if (Float.floatToRawIntBits(one[at + i]) != Float.floatToRawIntBits(other[otherAt + i])) {
                return false;
            }
        }
        return true;
    }

    /** A face across the plane: what of it lies outside the outline, wedge by wedge. */
    private void cutFace(float[] tri, int at, Hole hole, float sA, float tA, float sB, float tB, float sC, float tC) {
        float[] o = hole.outline;
        int points = o.length / 2;
        boolean outsideAll = true;
        if (allOutside(hole, sA, tA, sB, tB, sC, tC)) {
            keep(tri, at);
            return;
        }
        for (int i = 0; i < points; i++) {
            int j = (i + 1) % points;
            float pS = o[2 * i], pT = o[2 * i + 1], qS = o[2 * j], qT = o[2 * j + 1];
            start(sA, tA, sB, tB, sC, tC);
            // Anticlockwise of the ray to p, clockwise of the ray to q, and beyond the edge pq.
            clip(-(pT - hole.middleT), pS - hole.middleS, (pT - hole.middleT) * hole.middleS - (pS - hole.middleS) * hole.middleT);
            clip(qT - hole.middleT, -(qS - hole.middleS), -(qT - hole.middleT) * hole.middleS + (qS - hole.middleS) * hole.middleT);
            clip(qT - pT, -(qS - pS), -(qT - pT) * pS + (qS - pS) * pT);
            emit(tri, at);
            outsideAll = false;
        }
        if (outsideAll) {
            keep(tri, at);
        }
    }

    /** Whether the triangle is wholly clear of the outline: none of its corners in it, none of its edges across it, and the outline not in it. */
    private static boolean allOutside(Hole hole, float sA, float tA, float sB, float tB, float sC, float tC) {
        float[] o = hole.outline;
        int points = o.length / 2;
        if (inside(o, sA, tA) || inside(o, sB, tB) || inside(o, sC, tC)) {
            return false;
        }
        for (int i = 0; i < points; i++) {
            int j = (i + 1) % points;
            float pS = o[2 * i], pT = o[2 * i + 1], qS = o[2 * j], qT = o[2 * j + 1];
            if (crosses(pS, pT, qS, qT, sA, tA, sB, tB) || crosses(pS, pT, qS, qT, sB, tB, sC, tC) || crosses(pS, pT, qS, qT, sC, tC, sA, tA)) {
                return false;
            }
        }
        return !inTriangle(o[0], o[1], sA, tA, sB, tB, sC, tC);
    }

    /** Whether (s, t) is inside the outline, by the crossings of a ray along s. */
    static boolean inside(float[] o, float s, float t) {
        boolean in = false;
        int points = o.length / 2;
        for (int i = 0, j = points - 1; i < points; j = i++) {
            float iS = o[2 * i], iT = o[2 * i + 1], jS = o[2 * j], jT = o[2 * j + 1];
            if ((iT > t) != (jT > t) && s < (jS - iS) * (t - iT) / (jT - iT) + iS) {
                in = !in;
            }
        }
        return in;
    }

    private static boolean crosses(float aS, float aT, float bS, float bT, float cS, float cT, float dS, float dT) {
        float d1 = side(cS, cT, dS, dT, aS, aT), d2 = side(cS, cT, dS, dT, bS, bT), d3 = side(aS, aT, bS, bT, cS, cT), d4 = side(aS, aT, bS, bT, dS, dT);
        return (d1 > 0) != (d2 > 0) && (d3 > 0) != (d4 > 0);
    }

    private static float side(float aS, float aT, float bS, float bT, float pS, float pT) {
        return (bS - aS) * (pT - aT) - (bT - aT) * (pS - aS);
    }

    private static boolean inTriangle(float s, float t, float sA, float tA, float sB, float tB, float sC, float tC) {
        float d1 = side(sA, tA, sB, tB, s, t), d2 = side(sB, tB, sC, tC, s, t), d3 = side(sC, tC, sA, tA, s, t);
        boolean negative = d1 < 0 || d2 < 0 || d3 < 0, positive = d1 > 0 || d2 > 0 || d3 > 0;
        return !(negative && positive);
    }

    /** A face along the plane, its line in the plane through the outline: what of it lies where the line is outside. */
    private void cutEdgeOn(float[] tri, int at, Hole hole, float sA, float tA, float sB, float tB, float sC, float tC, float aA, float aB, float aC) {
        float dS = sB - sA, dT = tB - tA, eS = sC - sA, eT = tC - tA;
        if (eS * eS + eT * eT > dS * dS + dT * dT) {
            dS = eS;
            dT = eT;
        }
        float length = (float) Math.sqrt(dS * dS + dT * dT);
        if (length < 1e-6f) {
            keep(tri, at);
            return;
        }
        float uS = dS / length, uT = dT / length;
        float wA = 0f, wB = (sB - sA) * uS + (tB - tA) * uT, wC = (sC - sA) * uS + (tC - tA) * uT;
        float area = (wB - wA) * (aC - aA) - (wC - wA) * (aB - aA);
        if (Math.abs(area) < EDGE_ON) {
            keep(tri, at);
            return;
        }
        // Where the line crosses the outline's edges, in w along it.
        float[] o = hole.outline;
        int points = o.length / 2;
        float[] crossings = new float[points + 2];
        int found = 0;
        for (int i = 0; i < points; i++) {
            int j = (i + 1) % points;
            float pS = o[2 * i] - sA, pT = o[2 * i + 1] - tA, qS = o[2 * j] - sA, qT = o[2 * j + 1] - tA;
            // Signed distances of p and q off the line.
            float dp = pS * -uT + pT * uS, dq = qS * -uT + qT * uS;
            if ((dp > 0) == (dq > 0)) {
                continue;
            }
            float share = dp / (dp - dq);
            crossings[found++] = (pS + (qS - pS) * share) * uS + (pT + (qT - pT) * share) * uT;
        }
        if (found < 2) {
            keep(tri, at);
            return;
        }
        java.util.Arrays.sort(crossings, 0, found);
        float low = Math.min(wA, Math.min(wB, wC)), high = Math.max(wA, Math.max(wB, wC));
        if (crossings[found - 1] <= low || crossings[0] >= high) {
            keep(tri, at);
            return;
        }
        // Inside runs between crossings 0-1, 2-3, ...: keep what is outside them.
        float keptFrom = Float.NEGATIVE_INFINITY;
        for (int k = 0; k + 1 < found; k += 2) {
            keepBetween(tri, at, wA, aA, wB, aB, wC, aC, keptFrom, crossings[k]);
            keptFrom = crossings[k + 1];
        }
        keepBetween(tri, at, wA, aA, wB, aB, wC, aC, keptFrom, Float.POSITIVE_INFINITY);
    }

    private void keepBetween(float[] tri, int at, float wA, float aA, float wB, float aB, float wC, float aC, float from, float to) {
        start(wA, aA, wB, aB, wC, aC);
        if (from != Float.NEGATIVE_INFINITY) {
            clip(1f, 0f, -from);
        }
        if (to != Float.POSITIVE_INFINITY) {
            clip(-1f, 0f, to);
        }
        emit(tri, at);
    }

    private void start(float xA, float yA, float xB, float yB, float xC, float yC) {
        px[0] = xA;
        py[0] = yA;
        px[1] = xB;
        py[1] = yB;
        px[2] = xC;
        py[2] = yC;
        w0[0] = 1f;
        w1[0] = 0f;
        w2[0] = 0f;
        w0[1] = 0f;
        w1[1] = 1f;
        w2[1] = 0f;
        w0[2] = 0f;
        w1[2] = 0f;
        w2[2] = 1f;
        corners = 3;
    }

    /** Keeps what of the polygon has nx x + ny y + c at least 0. */
    private void clip(float nx, float ny, float c) {
        int kept = 0;
        for (int i = 0; i < corners; i++) {
            int j = (i + 1) % corners;
            float di = nx * px[i] + ny * py[i] + c, dj = nx * px[j] + ny * py[j] + c;
            if (di >= 0f) {
                put(kept++, px[i], py[i], w0[i], w1[i], w2[i]);
            }
            if ((di >= 0f) != (dj >= 0f)) {
                float share = di / (di - dj);
                put(kept++, px[i] + (px[j] - px[i]) * share, py[i] + (py[j] - py[i]) * share, w0[i] + (w0[j] - w0[i]) * share,
                        w1[i] + (w1[j] - w1[i]) * share, w2[i] + (w2[j] - w2[i]) * share);
            }
        }
        System.arraycopy(qx, 0, px, 0, kept);
        System.arraycopy(qy, 0, py, 0, kept);
        System.arraycopy(v0, 0, w0, 0, kept);
        System.arraycopy(v1, 0, w1, 0, kept);
        System.arraycopy(v2, 0, w2, 0, kept);
        corners = kept;
    }

    private void put(int i, float x, float y, float a, float b, float c) {
        qx[i] = x;
        qy[i] = y;
        v0[i] = a;
        v1[i] = b;
        v2[i] = c;
    }

    /** Puts the polygon in next as a fan of triangles, with what the corners of the triangle at `at` carry blended at each. */
    private void emit(float[] tri, int at) {
        if (corners < 3) {
            return;
        }
        if (Math.abs(area()) < EDGE_ON * 1e-2f) {
            return;
        }
        next = room(next, nextFloats + (corners - 2) * 3 * V);
        for (int k = 1; k + 1 < corners; k++) {
            blend(tri, at, 0, next, nextFloats);
            blend(tri, at, k, next, nextFloats + V);
            blend(tri, at, k + 1, next, nextFloats + 2 * V);
            nextFloats += 3 * V;
        }
    }

    private float area() {
        float sum = 0f;
        for (int i = 0; i < corners; i++) {
            int j = (i + 1) % corners;
            sum += px[i] * py[j] - px[j] * py[i];
        }
        return sum / 2f;
    }

    /** The vertex at the polygon's corner k, blended from the triangle at `at`, into to at toAt. */
    private void blend(float[] tri, int at, int k, float[] to, int toAt) {
        blendAt(tri, at, w0[k], w1[k], w2[k], to, toAt);
    }

    static void blendAt(float[] tri, int at, float a, float b, float c, float[] to, int toAt) {
        int nearest = a >= b && a >= c ? 0 : b >= c ? 1 : 2;
        for (int i = 0; i < V; i++) {
            float x = tri[at + i], y = tri[at + V + i], z = tri[at + 2 * V + i];
            if (Float.floatToRawIntBits(x) == Float.floatToRawIntBits(y) && Float.floatToRawIntBits(y) == Float.floatToRawIntBits(z)) {
                to[toAt + i] = x;
            } else if (CODE[i]) {
                to[toAt + i] = tri[at + nearest * V + i];
            } else {
                to[toAt + i] = a * x + b * y + c * z;
            }
        }
    }

    /** Notes the face of the triangle at `at`, if it is across the plane and over the mark, where it is the nearest yet to the side the mark was struck from. */
    private void findFace(SpriteGeometry.Mesh mesh, float[] tri, int at, Mark mark) {
        int a = at, b = at + V, c = at + 2 * V;
        float sA = s(tri, a), sB = s(tri, b), sC = s(tri, c);
        float tA = tri[a + 2], tB = tri[b + 2], tC = tri[c + 2];
        float area = (sB - sA) * (tC - tA) - (sC - sA) * (tB - tA);
        if (Math.abs(area) <= EDGE_ON) {
            return;
        }
        float l1 = ((mark.s - sA) * (tC - tA) - (sC - sA) * (mark.t - tA)) / area;
        float l2 = ((sB - sA) * (mark.t - tA) - (mark.s - sA) * (tB - tA)) / area;
        float l0 = 1f - l1 - l2;
        if (l0 < 0f || l1 < 0f || l2 < 0f) {
            return;
        }
        float across = l0 * across(tri, a) + l1 * across(tri, b) + l2 * across(tri, c);
        if (Math.abs(across) > reach || across * mark.side <= mark.across * mark.side && mark.mesh != null) {
            return;
        }
        mark.mesh = mesh;
        mark.across = across;
        blendAt(tri, at, l0, l1, l2, mark.vertex, 0);
    }

    private final float[] corner = new float[V];

    /** Lines a hole's sides, from its nearest face to its furthest, an edge of the outline at a time. */
    private void line(Hole hole) {
        float[] o = hole.outline;
        int points = o.length / 2;
        SpriteGeometry.Mesh mesh = hole.donorMesh;
        for (int i = 0; i < points; i++) {
            int j = (i + 1) % points;
            float pS = o[2 * i], pT = o[2 * i + 1], qS = o[2 * j], qT = o[2 * j + 1];
            // The lining looks in, towards the middle.
            float nS = -(qT - pT), nT = qS - pS;
            if (nS * (hole.middleS - pS) + nT * (hole.middleT - pT) < 0f) {
                nS = -nS;
                nT = -nT;
            }
            float length = (float) Math.sqrt(nS * nS + nT * nT);
            if (length < 1e-6f) {
                continue;
            }
            nS /= length;
            nT /= length;
            int at = mesh.floats;
            lining(mesh, hole.donor, pS, pT, hole.nearest, nS, nT);
            lining(mesh, hole.donor, qS, qT, hole.nearest, nS, nT);
            lining(mesh, hole.donor, qS, qT, hole.furthest, nS, nT);
            mesh.again(at);
            mesh.again(at + 2 * V);
            lining(mesh, hole.donor, pS, pT, hole.furthest, nS, nT);
        }
    }

    private void lining(SpriteGeometry.Mesh mesh, float[] donor, float s, float t, float across, float nS, float nT) {
        System.arraycopy(donor, 0, corner, 0, V);
        place(corner, s, t, across);
        corner[9] = nS * alongX;
        corner[10] = nS * alongY;
        corner[11] = nT;
        corner[5] *= LINING_SHADE;
        corner[6] *= LINING_SHADE;
        corner[7] *= LINING_SHADE;
        corner[SpriteGeometry.ALONG_AT] = 0f;
        corner[SpriteGeometry.ALONG_AT + 1] = 0f;
        corner[SpriteGeometry.WAY_AT] = 0f;
        mesh.addVertex(corner);
    }

    private void place(float[] vertex, float s, float t, float across) {
        vertex[0] = originX + s * alongX + across * alongY;
        vertex[1] = originY + s * alongY + across * alongX;
        vertex[2] = t;
    }

    /** Lays a mark just off the face found for it, a fan round its middle. */
    private void stamp(Mark mark) {
        float[] o = mark.outline;
        int points = o.length / 2;
        float across = mark.across + mark.side * MARK_OFF;
        SpriteGeometry.Mesh mesh = mark.mesh;
        float normalAcross = mark.side;
        for (int i = 0; i < points; i++) {
            int j = (i + 1) % points;
            markCorner(mesh, mark, mark.s, mark.t, across, normalAcross);
            markCorner(mesh, mark, o[2 * i], o[2 * i + 1], across, normalAcross);
            markCorner(mesh, mark, o[2 * j], o[2 * j + 1], across, normalAcross);
        }
    }

    private void markCorner(SpriteGeometry.Mesh mesh, Mark mark, float s, float t, float across, float normalAcross) {
        System.arraycopy(mark.vertex, 0, corner, 0, V);
        place(corner, s, t, across);
        corner[9] = normalAcross * alongY;
        corner[10] = normalAcross * alongX;
        corner[11] = 0f;
        corner[5] *= MARK_SHADE;
        corner[6] *= MARK_SHADE;
        corner[7] *= MARK_SHADE;
        mesh.addVertex(corner);
    }
}

package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;

/**
 * Dev only: finds the surfaces of the first-person world that fight for depth. Two triangles
 * fight where they cover the same part of one plane, or of two planes nearer each other than
 * the depth buffer tells apart from where they are seen: the picture then shows one or the
 * other from frame to frame as the eye moves.
 *
 * Every build keeps a copy of its triangles as it is filled (copy), since its vertices go to
 * the graphics card and are given back. Every few seconds of a frame whose builds have
 * changed, the triangles of the builds the frame shows within the reach of the eye, and the
 * furniture stood on them, are put in buckets by their plane and set against each other, with
 * each moved off its plane as PipelineShaders.FACED moves it as it draws. The cards that turn
 * to the eye are set against the cards standing on the same spot, which turn in one plane with
 * them wherever they are seen from. The log has a line
 * for each kind of pair, by what the two are made of and how they lie, with the area they
 * share, the nearest the eye can be for them to fight, and a few places with what the map has
 * on the square.
 *
 * Switched on with -Dcameraunlock.zomboid.depthFights=reach in tiles, in a development build.
 */
final class DepthFights {
    static final boolean ON = DevBuild.ON && System.getProperty("cameraunlock.zomboid.depthFights") != null;
    private static final float REACH = ON ? Float.parseFloat(System.getProperty("cameraunlock.zomboid.depthFights")) : 0f;
    private static final long EVERY_NANOS = 5_000_000_000L;

    /**
     * How far the depth buffer tells apart two surfaces at a tile, which grows by the square of
     * the distance: 24 bits from a near plane of 0.056 tiles (Pipeline: WorldScene.NEAR_CORNER_DISTANCE
     * at the field of view the game starts with), twice over, since a fight shows before the two
     * share one step of depth.
     */
    static final double DEPTH_STEP_PER_TILE_SQUARED = 2.0 / (0.056 * (1 << 24));
    /** The distances a pair is tried at, in tiles: the furthest is about where the world's chunks stop being drawn. */
    static final float[] DISTANCES = {0.3f, 0.6f, 1f, 1.5f, 2f, 3f, 4f, 6f, 8f, 12f, 16f, 24f, 32f, 48f, 64f, 96f, 128f};
    /** Two planes this far apart along their normal are always in a bucket together or in the next. */
    private static final double SLICE = 0.06;
    private static final float CELL = 2f;
    /** The cosine of a degree: planes whose normals are further apart than that do not fight over an area. */
    private static final double SAME_WAY = Math.cos(Math.toRadians(1.0));
    /** Less than a square centimetre in common is an edge, not an area. */
    static final float LEAST_AREA = 1e-4f;
    static final int CARD_FLOATS = 10;
    /** Cards that stand nearer each other than this, in tiles, turn to the eye in one plane from wherever they are seen. */
    static final double SAME_SPOT = 0.002;

    private DepthFights() {}

    /** One build's triangles as they were filled: its runs' textures, and for each triangle its corners and how the picture moves it. */
    static final class Copy {
        int wx, wy;
        boolean grown;
        int triangles;
        /** Nine floats a triangle: its corners. */
        float[] corners = new float[0];
        /** Six a triangle: what the picture moves it by (SpriteGeometry.WAY_AT, ALONG_AT and the layers of cards it stands nearer), what it is made of, and the face's way along the ground. */
        float[] moves = new float[0];
        int[] run = new int[0];
        int cards;
        /** CARD_FLOATS a card that turns to the camera: where it stands, then each corner's place along the card and height, how many layers of cards it stands towards the camera, and what it is made of. */
        float[] cardCorners = new float[0];
        int[] cardRun = new int[0];
        /** For each run, its texture as a number to tell runs apart by in the log: never bound. */
        int[] textures;
        boolean[] runLaid;
    }

    static Copy copy(int wx, int wy, boolean grown, SpriteGeometry.MeshSet solid, SpriteGeometry.MeshSet laid) {
        Copy copy = new Copy();
        copy.wx = wx;
        copy.wy = wy;
        copy.grown = grown;
        int triangles = 0, runs = 0, cards = 0;
        for (SpriteGeometry.MeshSet set : new SpriteGeometry.MeshSet[] {solid, laid}) {
            for (int i = 0; i < set.used.size(); i++) {
                SpriteGeometry.Mesh mesh = set.used.get(i);
                if (mesh.floats > 0) {
                    triangles += mesh.floats / (3 * SpriteGeometry.VERTEX_FLOATS);
                    runs++;
                    for (int at = 0; at < mesh.floats; at += 3 * SpriteGeometry.VERTEX_FLOATS) {
                        if (mesh.vertices[at + SpriteGeometry.WAY_AT] == SpriteGeometry.FACES) {
                            cards++;
                        }
                    }
                }
            }
        }
        copy.corners = new float[triangles * 9];
        copy.moves = new float[triangles * 6];
        copy.run = new int[triangles];
        copy.cardCorners = new float[cards * CARD_FLOATS];
        copy.cardRun = new int[cards];
        copy.textures = new int[runs];
        copy.runLaid = new boolean[runs];
        int t = 0, run = 0, card = 0;
        final int v = SpriteGeometry.VERTEX_FLOATS;
        for (SpriteGeometry.MeshSet set : new SpriteGeometry.MeshSet[] {solid, laid}) {
            for (int i = 0; i < set.used.size(); i++) {
                SpriteGeometry.Mesh mesh = set.used.get(i);
                if (mesh.floats == 0) {
                    continue;
                }
                copy.textures[run] = mesh.texture;
                copy.runLaid[run] = set == laid;
                float[] from = mesh.vertices;
                for (int at = 0; at < mesh.floats; at += 3 * v) {
                    float way = from[at + SpriteGeometry.WAY_AT];
                    // A card turned to the camera has no plane until it is seen from somewhere: it is set against the cards on
                    // the same spot (cardPairs). Wood is a ring standing where it is.
                    if (way == SpriteGeometry.FACES) {
                        int to = card * CARD_FLOATS;
                        copy.cardCorners[to] = from[at];
                        copy.cardCorners[to + 1] = from[at + 1];
                        for (int c = 0; c < 3; c++) {
                            copy.cardCorners[to + 2 + c * 2] = from[at + c * v + SpriteGeometry.ALONG_AT];
                            copy.cardCorners[to + 3 + c * 2] = from[at + c * v + 2];
                        }
                        copy.cardCorners[to + 8] = from[at + SpriteGeometry.ALONG_AT + 1];
                        copy.cardCorners[to + 9] = from[at + 8];
                        copy.cardRun[card] = run;
                        card++;
                        continue;
                    }
                    if (way == SpriteGeometry.WOOD) {
                        continue;
                    }
                    for (int c = 0; c < 3; c++) {
                        copy.corners[t * 9 + c * 3] = from[at + c * v];
                        copy.corners[t * 9 + c * 3 + 1] = from[at + c * v + 1];
                        copy.corners[t * 9 + c * 3 + 2] = from[at + c * v + 2];
                    }
                    copy.moves[t * 6] = way;
                    copy.moves[t * 6 + 1] = from[at + SpriteGeometry.ALONG_AT];
                    copy.moves[t * 6 + 2] = from[at + SpriteGeometry.ALONG_AT + 1];
                    copy.moves[t * 6 + 3] = from[at + 8];
                    copy.moves[t * 6 + 4] = from[at + 9];
                    copy.moves[t * 6 + 5] = from[at + 10];
                    copy.run[t] = run;
                    t++;
                }
                run++;
            }
        }
        copy.triangles = t;
        copy.cards = card;
        return copy;
    }

    /** A triangle set against the others: its corners, its plane measured from the eye, and how far the picture moves it off that plane. */
    static final class Tri {
        final double[] p = new double[9];
        double nx, ny, nz, d;
        /** Layers it lies off along the normal (SpriteGeometry.LAYERS_X and the rest), and layers of cards it stands towards the camera along it. */
        double layers, cards;
        String label;
        int source;
        /** The cell its bounds start in, along its plane's two axes: see pairs. */
        long cellU, cellV;
    }

    /** What the pairs of one kind add up to. */
    static final class Kind {
        double area;
        int pairs;
        float nearest = Float.MAX_VALUE;
        double areaNow;
        final StringBuilder places = new StringBuilder();
        final ArrayList<double[]> at = new ArrayList<>();
    }

    private static long lastNanos;
    private static long lastShape;
    /** The fullest bucket of the last audit, for the log: what makes an audit slow. */
    private static String largest = "none";
    private static final HashMap<Integer, Models.Model> MODELS = new HashMap<>();

    /** Run by the logic thread after a frame's builds: audits the builds it shows, at most every few seconds and only once they have changed. */
    static void audit(List<WorldScene.ChunkMeshes> shown, float eyeX, float eyeY, float eyeUp, IsoCell cell) {
        long now = System.nanoTime();
        if (now - lastNanos < EVERY_NANOS) {
            return;
        }
        long shape = 17;
        for (WorldScene.ChunkMeshes build : shown) {
            shape = shape * 31 + System.identityHashCode(build) * 7L + build.version;
        }
        shape = shape * 31 + Math.round(eyeX) * 131 + Math.round(eyeY) * 17 + Math.round(eyeUp);
        if (shape == lastShape) {
            return;
        }
        lastNanos = now;
        lastShape = shape;
        ArrayList<Tri> tris = new ArrayList<>();
        ArrayList<Card> cards = new ArrayList<>();
        int source = 0;
        for (WorldScene.ChunkMeshes build : shown) {
            source++;
            Copy copy = build.depthCopy;
            if (copy == null || build.maxX < eyeX - REACH || build.minX > eyeX + REACH || build.maxY < eyeY - REACH || build.minY > eyeY + REACH) {
                continue;
            }
            gather(copy, source, eyeX, eyeY, eyeUp, REACH, tris);
            gatherCards(copy, source, eyeX, eyeY, REACH, cards);
            placed(build.placed, source, eyeX, eyeY, eyeUp, REACH, tris);
        }
        Map<String, Kind> found = pairs(tris, eyeX, eyeY, eyeUp);
        cardPairs(cards, found);
        double total = 0;
        for (Kind kind : found.values()) {
            total += kind.area;
        }
        Log.info(String.format(Locale.ROOT, "depth fights: %d triangles and %d cards within %.0f tiles of (%.1f, %.1f, %.2f), %d kinds of pair, %.3f square tiles in all, took %.0f ms, fullest bucket %s",
                tris.size(), cards.size(), REACH, eyeX, eyeY, eyeUp, found.size(), total, (System.nanoTime() - now) / 1e6, largest));
        found.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue().area, a.getValue().area)).forEach(e -> {
            Kind kind = e.getValue();
            StringBuilder where = new StringBuilder();
            for (double[] place : kind.at) {
                where.append(String.format(Locale.ROOT, " (%.2f, %.2f, %.3f gap %.4f%s)", place[0], place[1], place[2], place[3], squareNames(cell, place)));
            }
            Log.info(String.format(Locale.ROOT, "depth fights: %.4f sq tiles (%.4f from here) in %d pairs, from %.1f tiles: %s at%s", kind.area, kind.areaNow, kind.pairs,
                    kind.nearest, e.getKey(), where));
        });
    }

    private static String squareNames(IsoCell cell, double[] place) {
        if (cell == null) {
            return "";
        }
        int x = (int) Math.floor(place[0]), y = (int) Math.floor(place[1]);
        StringBuilder names = new StringBuilder();
        // The level a height is on or tops, with the land's lift under a building of up to half a level.
        int level = (int) Math.floor((place[2] + 0.5) / ViewCamera.LEVEL_HEIGHT);
        for (int z = level - 1; z <= level; z++) {
            IsoGridSquare square = cell.getGridSquare(x, y, z);
            if (square == null) {
                continue;
            }
            names.append(" z").append(z).append(':');
            for (int i = 0; i < square.getObjects().size(); i++) {
                IsoObject object = square.getObjects().get(i);
                names.append(' ').append(object.getSprite() == null ? "?" : object.getSprite().getName());
            }
        }
        return names.length() == 0 ? "" : " [" + names.toString().trim() + "]";
    }

    /** The copy's triangles whose middle is within reach of the eye. */
    static void gather(Copy copy, int source, float eyeX, float eyeY, float eyeUp, float reach, List<Tri> into) {
        for (int t = 0; t < copy.triangles; t++) {
            float[] c = copy.corners;
            int at = t * 9;
            double mx = (c[at] + c[at + 3] + c[at + 6]) / 3.0, my = (c[at + 1] + c[at + 4] + c[at + 7]) / 3.0;
            if (Math.abs(mx - eyeX) > reach || Math.abs(my - eyeY) > reach) {
                continue;
            }
            Tri tri = new Tri();
            for (int i = 0; i < 9; i++) {
                tri.p[i] = c[at + i] - (i % 3 == 0 ? eyeX : i % 3 == 1 ? eyeY : eyeUp);
            }
            if (!plane(tri)) {
                continue;
            }
            float way = copy.moves[t * 6], along = copy.moves[t * 6 + 1], nearer = copy.moves[t * 6 + 2], open = copy.moves[t * 6 + 3];
            float faceX = copy.moves[t * 6 + 4], faceY = copy.moves[t * 6 + 5];
            if (way >= SpriteGeometry.LAYERS_X) {
                double axis = way == SpriteGeometry.LAYERS_X ? tri.nx : way == SpriteGeometry.LAYERS_Y ? tri.ny : tri.nz;
                tri.layers = axis * along;
            }
            double toward = nearer * Math.sqrt(tri.nx * tri.nx + tri.ny * tri.ny);
            if (way == SpriteGeometry.CLEARS) {
                double length = Math.sqrt(faceX * faceX + faceY * faceY);
                toward += along * (length > 0 ? Math.abs(tri.nx * faceX + tri.ny * faceY) / length : 0);
            }
            tri.cards = toward;
            int material = (int) Math.floor((open + 0.5f) / 32f);
            int kind = Math.round(open - 32f * material);
            boolean laid = copy.runLaid[copy.run[t]];
            tri.label = (laid ? "laid " : "") + (copy.grown ? "grown " : "") + materialName(material) + "/" + kind + " tex " + copy.textures[copy.run[t]]
                    + (way == 0f ? "" : " " + wayName(way) + " " + trim(along)) + (nearer == 0f ? "" : " nearer " + trim(nearer));
            tri.source = source;
            into.add(tri);
        }
    }

    /** The furniture stood on a build whose middle is within reach of the eye, triangle by triangle where the picture stands them. */
    static void placed(Models.Instances placed, int source, float eyeX, float eyeY, float eyeUp, float reach, List<Tri> into) {
        if (placed.count == 0) {
            return;
        }
        if (MODELS.isEmpty()) {
            for (Models.Model model : Models.loadedModels()) {
                MODELS.put(model.from, model);
            }
        }
        for (int i = 0; i < placed.count; i++) {
            float[] f = placed.floats;
            int at = i * Models.Instances.FLOATS;
            float placeX = f[at + Models.Instances.PLACE], placeY = f[at + Models.Instances.PLACE + 1];
            float scale = f[at + Models.Instances.PLACE + 2], scaleUp = f[at + Models.Instances.PLACE + 3];
            float footX = f[at + Models.Instances.FOOT], footY = f[at + Models.Instances.FOOT + 1], footUp = f[at + Models.Instances.FOOT + 2];
            if (Math.abs(placeX + footX - eyeX) > reach || Math.abs(placeY + footY - eyeY) > reach) {
                continue;
            }
            float t0 = f[at + Models.Instances.TURN], t1 = f[at + Models.Instances.TURN + 1], t2 = f[at + Models.Instances.TURN + 2], t3 = f[at + Models.Instances.TURN + 3];
            Models.Model model = MODELS.get(placed.ints[i * Models.Instances.INTS + Models.Instances.FIRST]);
            if (model == null) {
                throw new IllegalStateException("no model starts at corner " + placed.ints[i * Models.Instances.INTS + Models.Instances.FIRST]);
            }
            for (int corner = 0; corner < model.corners.length; corner += 3) {
                Tri tri = new Tri();
                for (int k = 0; k < 3; k++) {
                    int vertex = model.corners[corner + k] * Models.VERTEX_FLOATS;
                    float mx = model.vertices[vertex], my = model.vertices[vertex + 1], mz = model.vertices[vertex + 2];
                    tri.p[k * 3] = placeX + footX + (t0 * mx + t1 * my) * scale - (double) eyeX;
                    tri.p[k * 3 + 1] = placeY + footY + (t2 * mx + t3 * my) * scale - (double) eyeY;
                    tri.p[k * 3 + 2] = footUp + mz * scaleUp - (double) eyeUp;
                }
                if (!plane(tri)) {
                    continue;
                }
                tri.label = "model " + model.name + "/" + model.surface[model.corners[corner]];
                // One piece's own triangles meet as its maker made them: set only against others.
                tri.source = -(source * 100000 + i);
                into.add(tri);
            }
        }
    }

    /** A triangle of a card that turns to the camera: the spot it stands on, and the triangle laid flat as along the card and up. */
    static final class Card {
        double x, y, up;
        final Tri flat = new Tri();
        double cards;
        String label;
        int source;
    }

    /** The copy's cards that stand within reach of the eye. */
    static void gatherCards(Copy copy, int source, float eyeX, float eyeY, float reach, List<Card> into) {
        for (int t = 0; t < copy.cards; t++) {
            float[] c = copy.cardCorners;
            int at = t * CARD_FLOATS;
            if (Math.abs(c[at] - eyeX) > reach || Math.abs(c[at + 1] - eyeY) > reach) {
                continue;
            }
            Card card = new Card();
            card.x = c[at];
            card.y = c[at + 1];
            for (int k = 0; k < 3; k++) {
                card.flat.p[k * 3] = c[at + 2 + k * 2];
                card.flat.p[k * 3 + 2] = c[at + 3 + k * 2];
                card.up += c[at + 3 + k * 2] / 3.0;
            }
            if (!plane(card.flat)) {
                continue;
            }
            card.cards = c[at + 8];
            float open = c[at + 9];
            int material = (int) Math.floor((open + 0.5f) / 32f);
            int kind = Math.round(open - 32f * material);
            card.label = "card " + (copy.grown ? "grown " : "") + materialName(material) + "/" + kind + " tex " + copy.textures[copy.cardRun[t]]
                    + (card.cards == 0 ? "" : " nearer " + trim((float) card.cards));
            card.source = source;
            into.add(card);
        }
    }

    /**
     * Every pair of cards on one spot that fights, by kind. A card turns to the eye through the
     * spot it stands on, so two cards on one spot are in one plane from wherever they are seen,
     * apart only by the layers of cards each stands towards the camera.
     */
    static void cardPairs(List<Card> cards, Map<String, Kind> found) {
        HashMap<Long, ArrayList<Card>> spots = new HashMap<>();
        for (Card card : cards) {
            spots.computeIfAbsent(spot(Math.round(card.x / SAME_SPOT), Math.round(card.y / SAME_SPOT)), k -> new ArrayList<>()).add(card);
        }
        // Each spot against itself and the four of its neighbours that come after it, so a pair across a spot's edge is tried once.
        final long[][] after = {{0, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
        for (ArrayList<Card> here : spots.values()) {
            long sx = Math.round(here.get(0).x / SAME_SPOT), sy = Math.round(here.get(0).y / SAME_SPOT);
            for (int i = 0; i < here.size(); i++) {
                Card a = here.get(i);
                for (long[] step : after) {
                    ArrayList<Card> others = step[0] == 0 && step[1] == 0 ? here : spots.get(spot(sx + step[0], sy + step[1]));
                    if (others == null) {
                        continue;
                    }
                    for (int j = others == here ? i + 1 : 0; j < others.size(); j++) {
                        testCards(a, others.get(j), found);
                    }
                }
            }
        }
    }

    private static long spot(long sx, long sy) {
        return sx << 32 ^ (sy & 0xffffffffL);
    }

    private static void testCards(Card a, Card b, Map<String, Kind> found) {
        double apart = Math.hypot(a.x - b.x, a.y - b.y);
        if (apart >= SAME_SPOT) {
            return;
        }
        float nearest = nearestFight(apart, 0, a.cards - b.cards);
        if (nearest == Float.MAX_VALUE) {
            return;
        }
        double area = overlap(a.flat, b.flat);
        if (area < LEAST_AREA) {
            return;
        }
        boolean twice = sameCorners(a.flat, b.flat);
        String what = "card: " + (a.label.compareTo(b.label) <= 0 ? a.label + " WITH " + b.label : b.label + " WITH " + a.label)
                + (twice ? " (the same triangle twice)" : "") + (a.source == b.source ? "" : " (two builds)");
        Kind kind = found.computeIfAbsent(what, x -> new Kind());
        kind.area += area;
        kind.pairs++;
        kind.nearest = Math.min(kind.nearest, nearest);
        kind.areaNow += area;
        if (kind.at.size() < 4) {
            kind.at.add(new double[] {a.x, a.y, a.up, apart});
        }
    }

    /** Works out the triangle's plane, one way round whichever way it is wound. False for a sliver. */
    static boolean plane(Tri tri) {
        double[] p = tri.p;
        double ux = p[3] - p[0], uy = p[4] - p[1], uz = p[5] - p[2], vx = p[6] - p[0], vy = p[7] - p[1], vz = p[8] - p[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 2 * LEAST_AREA * 0.1) {
            return false;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        if (nz < -1e-6 || Math.abs(nz) <= 1e-6 && (ny < -1e-6 || Math.abs(ny) <= 1e-6 && nx < 0)) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        tri.nx = nx;
        tri.ny = ny;
        tri.nz = nz;
        tri.d = nx * p[0] + ny * p[1] + nz * p[2];
        return true;
    }

    /**
     * Every pair of the triangles that fights from somewhere within DISTANCES, by kind. Places
     * are measured from the eye, as Tri holds them; eyeX, eyeY and eyeUp put them back on the
     * map for the log.
     */
    static Map<String, Kind> pairs(List<Tri> tris, float eyeX, float eyeY, float eyeUp) {
        HashMap<Long, ArrayList<Tri>> buckets = new HashMap<>();
        for (Tri tri : tris) {
            long normal = normalKey(tri);
            double[] axes = axes(normal);
            long slice = (long) Math.floor(tri.d / SLICE);
            double lowU = Double.MAX_VALUE, highU = -Double.MAX_VALUE, lowV = Double.MAX_VALUE, highV = -Double.MAX_VALUE;
            for (int c = 0; c < 3; c++) {
                double u = tri.p[c * 3] * axes[0] + tri.p[c * 3 + 1] * axes[1] + tri.p[c * 3 + 2] * axes[2];
                double v = tri.p[c * 3] * axes[3] + tri.p[c * 3 + 1] * axes[4] + tri.p[c * 3 + 2] * axes[5];
                lowU = Math.min(lowU, u);
                highU = Math.max(highU, u);
                lowV = Math.min(lowV, v);
                highV = Math.max(highV, v);
            }
            tri.cellU = (long) Math.floor(lowU / CELL);
            tri.cellV = (long) Math.floor(lowV / CELL);
            for (long cu = tri.cellU; cu <= (long) Math.floor(highU / CELL); cu++) {
                for (long cv = (long) Math.floor(lowV / CELL); cv <= (long) Math.floor(highV / CELL); cv++) {
                    buckets.computeIfAbsent(key(normal, slice, cu, cv), k -> new ArrayList<>()).add(tri);
                }
            }
        }
        ArrayList<Tri> fullest = new ArrayList<>();
        for (ArrayList<Tri> bucket : buckets.values()) {
            if (bucket.size() > fullest.size()) {
                fullest = bucket;
            }
        }
        largest = fullest.isEmpty() ? "none" : fullest.size() + " of " + fullest.get(0).label + " at " + String.format(Locale.ROOT, "%.1f, %.1f, %.2f",
                fullest.get(0).p[0] + eyeX, fullest.get(0).p[1] + eyeY, fullest.get(0).p[2] + eyeUp) + " in " + buckets.size() + " buckets";
        HashMap<String, Kind> found = new HashMap<>();
        for (Map.Entry<Long, ArrayList<Tri>> entry : buckets.entrySet()) {
            ArrayList<Tri> same = entry.getValue();
            long k = entry.getKey();
            long cu = (k >>> 12) & 0xfffL, cv = k & 0xfffL;
            ArrayList<Tri> next = buckets.get(nextSlice(k));
            for (int i = 0; i < same.size(); i++) {
                Tri a = same.get(i);
                for (int pass = 0; pass < 2; pass++) {
                    ArrayList<Tri> others = pass == 0 ? same : next;
                    if (others == null) {
                        continue;
                    }
                    for (int j = pass == 0 ? i + 1 : 0; j < others.size(); j++) {
                        Tri b = others.get(j);
                        if (a == b || a.source == b.source && a.source < 0) {
                            continue;
                        }
                        // Two triangles share every cell from where both their bounds have started on, so a pair is tried in that one only.
                        if ((Math.max(a.cellU, b.cellU) & 0xfffL) != cu || (Math.max(a.cellV, b.cellV) & 0xfffL) != cv) {
                            continue;
                        }
                        test(a, b, found);
                    }
                }
            }
        }
        for (Kind kind : found.values()) {
            for (double[] place : kind.at) {
                place[0] += eyeX;
                place[1] += eyeY;
                place[2] += eyeUp;
            }
        }
        return found;
    }

    private static void test(Tri a, Tri b, Map<String, Kind> found) {
        double dot = a.nx * b.nx + a.ny * b.ny + a.nz * b.nz;
        if (dot < SAME_WAY) {
            return;
        }
        float nearest = nearestFight(a.d - b.d, a.layers - b.layers, a.cards - b.cards);
        if (nearest == Float.MAX_VALUE) {
            return;
        }
        double area = overlap(a, b);
        if (area < LEAST_AREA) {
            return;
        }
        boolean twice = sameCorners(a, b);
        String facing = Math.abs(a.nz) > 0.9 ? "level" : Math.abs(a.nz) < 0.1 ? "upright" : "sloped";
        String one = a.label, two = b.label;
        String what = facing + ": " + (one.compareTo(two) <= 0 ? one + " WITH " + two : two + " WITH " + one) + (twice ? " (the same triangle twice)" : "")
                + (a.source == b.source ? "" : " (two builds)");
        Kind kind = found.computeIfAbsent(what, x -> new Kind());
        kind.area += area;
        kind.pairs++;
        kind.nearest = Math.min(kind.nearest, nearest);
        double mx = (a.p[0] + a.p[3] + a.p[6]) / 3, my = (a.p[1] + a.p[4] + a.p[7]) / 3, mz = (a.p[2] + a.p[5] + a.p[8]) / 3;
        double distance = Math.sqrt(mx * mx + my * my + mz * mz);
        if (distance >= nearest) {
            kind.areaNow += area;
        }
        if (kind.at.size() < 4) {
            kind.at.add(new double[] {mx, my, mz, Math.abs(a.d - b.d)});
        }
    }

    /**
     * The nearest of DISTANCES from which two parallel planes fight, or Float.MAX_VALUE for none:
     * apart, along their normal, by gap besides layers more layers than the second lies by and
     * cards more layers of cards towards the camera, whichever side of them it is on.
     */
    static float nearestFight(double gap, double layers, double cards) {
        for (float distance : DISTANCES) {
            double layer = WorldScene.STAND_OFF + WorldScene.STAND_OFF_PER_TILE_SQUARED * distance * distance;
            double card = WorldScene.CARD_LAYER + 4 * layer;
            double fixed = gap + layers * layer;
            double apart = Math.min(Math.abs(fixed + cards * card), Math.abs(fixed - cards * card));
            if (apart < DEPTH_STEP_PER_TILE_SQUARED * distance * distance) {
                return distance;
            }
        }
        return Float.MAX_VALUE;
    }

    private static boolean sameCorners(Tri a, Tri b) {
        for (int i = 0; i < 3; i++) {
            boolean match = false;
            for (int j = 0; j < 3 && !match; j++) {
                match = Math.abs(a.p[i * 3] - b.p[j * 3]) < 1e-4 && Math.abs(a.p[i * 3 + 1] - b.p[j * 3 + 1]) < 1e-4 && Math.abs(a.p[i * 3 + 2] - b.p[j * 3 + 2]) < 1e-4;
            }
            if (!match) {
                return false;
            }
        }
        return true;
    }

    private static long normalKey(Tri tri) {
        return (Math.round(tri.nx * 20) + 32) << 12 | (Math.round(tri.ny * 20) + 32) << 6 | (Math.round(tri.nz * 20) + 32);
    }

    private static double[] axes(long normal) {
        double nx = ((normal >> 12) & 63) - 32, ny = ((normal >> 6) & 63) - 32, nz = (normal & 63) - 32;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
        double ux, uy, uz;
        if (Math.abs(nz) > 0.7) {
            ux = 1;
            uy = 0;
            uz = -nx / nz;
        } else {
            ux = -ny;
            uy = nx;
            uz = 0;
        }
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        return new double[] {ux, uy, uz, ny * uz - nz * uy, nz * ux - nx * uz, nx * uy - ny * ux};
    }

    /** A bucket: the way a plane faces, which slice of such planes it is in, and a cell along it. */
    private static long key(long normal, long slice, long cu, long cv) {
        return normal << 46 | (slice & 0x3fffffL) << 24 | (cu & 0xfffL) << 12 | (cv & 0xfffL);
    }

    /** The bucket over the same cell in the next slice of planes. */
    private static long nextSlice(long key) {
        long slice = ((key >>> 24) + 1) & 0x3fffffL;
        return key & ~(0x3fffffL << 24) | slice << 24;
    }

    /** How much of their plane two triangles in it share, in square tiles. */
    static double overlap(Tri a, Tri b) {
        double[] axes = axes(normalKey(a));
        double[] subject = flat(a.p, axes), clip = flat(b.p, axes);
        if (area(clip, 3) < 0) {
            double s = clip[2], t = clip[3];
            clip[2] = clip[4];
            clip[3] = clip[5];
            clip[4] = s;
            clip[5] = t;
        }
        double[] poly = Arrays.copyOf(subject, 32);
        int count = 3;
        double[] next = new double[32];
        for (int e = 0; e < 3 && count > 0; e++) {
            double x0 = clip[e * 2], y0 = clip[e * 2 + 1], x1 = clip[(e + 1) % 3 * 2], y1 = clip[(e + 1) % 3 * 2 + 1];
            int made = 0;
            for (int i = 0; i < count && made < 15; i++) {
                double px = poly[i * 2], py = poly[i * 2 + 1], qx = poly[(i + 1) % count * 2], qy = poly[(i + 1) % count * 2 + 1];
                double sp = (x1 - x0) * (py - y0) - (y1 - y0) * (px - x0), sq = (x1 - x0) * (qy - y0) - (y1 - y0) * (qx - x0);
                if (sp >= 0) {
                    next[made * 2] = px;
                    next[made * 2 + 1] = py;
                    made++;
                }
                if (sp >= 0 != sq >= 0) {
                    double t = sp / (sp - sq);
                    next[made * 2] = px + (qx - px) * t;
                    next[made * 2 + 1] = py + (qy - py) * t;
                    made++;
                }
            }
            double[] swap = poly;
            poly = next;
            next = swap;
            count = made;
        }
        return count < 3 ? 0 : Math.abs(area(poly, count));
    }

    private static double[] flat(double[] p, double[] axes) {
        double[] out = new double[6];
        for (int c = 0; c < 3; c++) {
            out[c * 2] = p[c * 3] * axes[0] + p[c * 3 + 1] * axes[1] + p[c * 3 + 2] * axes[2];
            out[c * 2 + 1] = p[c * 3] * axes[3] + p[c * 3 + 1] * axes[4] + p[c * 3 + 2] * axes[5];
        }
        return out;
    }

    private static double area(double[] poly, int count) {
        double sum = 0;
        for (int i = 0; i < count; i++) {
            sum += poly[i * 2] * poly[(i + 1) % count * 2 + 1] - poly[(i + 1) % count * 2] * poly[i * 2 + 1];
        }
        return sum / 2;
    }

    private static String trim(float value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String wayName(float way) {
        return way == SpriteGeometry.CLEARS ? "clears" : way == SpriteGeometry.LAYERS_X ? "layers-x" : way == SpriteGeometry.LAYERS_Y ? "layers-y"
                : way == SpriteGeometry.LAYERS_UP ? "layers-up" : way == SpriteGeometry.SHEET ? "sheet" : "way " + way;
    }

    private static String materialName(int material) {
        return switch (material) {
            case 0 -> "none";
            case 1 -> "SHINGLES";
            case 2 -> "PAINT";
            case 3 -> "WOOD";
            case 4 -> "EARTH";
            case 5 -> "LEAVES";
            case 6 -> "BARK";
            case 7 -> "PLANKS";
            case 8 -> "CARPET";
            case 9 -> "CONCRETE";
            case 10 -> "QUAY";
            case 11 -> "TILE";
            case 12 -> "WIRE";
            case 13 -> "GLASS";
            case 14 -> "LAMP";
            case 15 -> "MESH";
            default -> "m" + material;
        };
    }
}

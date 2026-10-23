package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * The left arm, placed on each of the mod's own long guns as ViewModel and ArmHolds place it,
 * goes no further into the gun than a hand resting on it presses in: at the hip, raised, with
 * the sights up, in a shot's kick, racking, and while a round goes in. With the sights up the
 * forearm went 10 to 18 mm into a magazine or a sawn-off's grip, and the hand that loads went
 * 11 to 19 mm into the receiver.
 */
final class HandClipTests {
    private HandClipTests() {}

    /** How far into a gun any of the left arm may go, in metres. */
    private static final float DEEPEST = 0.005f;
    private static final float TPM = 0.74355f;

    /** aim, kick pitch in degrees, kick back, what is being done, for how long, whether a clip goes in, and how far a round is. */
    private record Still(String name, float aim, float kickPitch, float kickBack, int doing, float done, boolean clip, float round) {}

    private static final Still[] STILLS = {
        new Still("at the hip", 0f, 0f, 0f, ArmActs.HOLDING, 0f, true, 0.5f),
        new Still("half raised", 0.5f, 0f, 0f, ArmActs.HOLDING, 0f, true, 0.5f),
        new Still("with the sights up", 1f, 0f, 0f, ArmActs.HOLDING, 0f, true, 0.5f),
        new Still("in a shot's kick with the sights up", 1f, 4f, 0.03f, ArmActs.HOLDING, 0f, true, 0.5f),
        new Still("racking", 0f, 0f, 0f, ArmActs.RACKING, 0.5f, true, 0.5f),
        new Still("bringing a round to the port", 0f, 0f, 0f, ArmActs.LOADING, 1.2f, false, 0.3f),
        new Still("at the port", 0f, 0f, 0f, ArmActs.LOADING, 1.2f, false, 0.42f),
        new Still("pushing a round in", 0f, 0f, 0f, ArmActs.LOADING, 1.2f, false, 0.6f),
        new Still("taking the hand back from the port", 0f, 0f, 0f, ArmActs.LOADING, 1.2f, false, 0.75f),
    };

    static void run() throws IOException {
        ArmModel arms;
        try (InputStream packed = HandClipTests.class.getResourceAsStream("/models/arms.mesh.gz")) {
            arms = new ArmModel(packed);
        }
        for (OwnModel own : OwnModel.ALL) {
            if (!own.handgun) {
                gun(arms, own);
            }
        }
    }

    private static boolean selfLoader(OwnModel own) {
        return own.mesh.matches(".*(assault|battle|js14|trapper).*");
    }

    /** The parts of each gun that are drawn while it is held. */
    private static int[] drawn(OwnModel own) {
        String m = own.mesh;
        if (selfLoader(own)) return new int[] {0, 1, 2, 4};
        if (m.contains("lever")) return new int[] {0, 1, 2, 3};
        if (m.contains("rifle") || m.contains("varmint") || m.contains("tactical")) return new int[] {0, 2};
        if (m.contains("shotgun") || m.contains("sawnoff") || m.contains("js3t")) return new int[] {0, 1, 2, 4};
        if (m.contains("dbarrel")) return new int[] {0, 1, 2, 5};
        return new int[] {0, 1, 2};
    }

    private static int action(OwnModel own) {
        String m = own.mesh;
        if (own.pump) return ArmActs.PUMP;
        if (m.contains("lever")) return ArmActs.LEVER;
        if (m.contains("dbarrelsawn")) return ArmActs.DOUBLE_SAWN;
        if (m.contains("dbarrel")) return ArmActs.DOUBLE;
        if (m.contains("varmint") || m.contains("toy")) return ArmActs.BOLT_SINGLE;
        return ArmActs.BOLT;
    }

    private static void gun(ArmModel arms, OwnModel own) throws IOException {
        ByteBuffer read;
        try (InputStream packed = new GZIPInputStream(HandClipTests.class.getResourceAsStream(own.mesh))) {
            read = ByteBuffer.wrap(packed.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
        }
        OwnModel.Layout layout = OwnModel.layout(read, own.mesh);
        float[] places = layout.places(read);
        int[] corners = layout.corners(read);
        StringBuilder why = new StringBuilder();
        WeaponShape shape = WeaponShape.read(places, corners, new float[] {0f, 1f, 0f}, own.top(), layout.muzzle(), 1f, why);
        Checks.check(shape != null, own.what + " has a shape: " + why);
        if (shape == null) {
            return;
        }
        // As it is held, and as it is while rounds go in: a self-loader's magazine is then in the hand, a double's barrels
        // are dropped open about the hinge with the rounds going in at the lower chamber's rim, and a lever gun's go in at its gate.
        int[] counts = layout.counts(), drawn = drawn(own);
        float[] moves = layout.moves(), loadPlaces = places.clone(), port = null;
        ArrayList<Integer> held = new ArrayList<>(), loaded = new ArrayList<>();
        int first = 0;
        for (int part = 0; part < counts.length; part++) {
            final int p = part;
            if (Arrays.stream(drawn).anyMatch(d -> d == p)) {
                for (int i = first; i < first + counts[part]; i++) {
                    held.add(corners[i]);
                    if (!(selfLoader(own) && part == 4)) {
                        loaded.add(corners[i]);
                    }
                }
            }
            first += counts[part];
        }
        if (own.mesh.contains("dbarrel")) {
            float[] turn = new float[16];
            OwnGun.turnAbout(turn, 0f, moves[OwnDouble.HINGE_Y], moves[OwnDouble.HINGE_Z], 1f, 0f, 0f, -moves[OwnDouble.OPENS]);
            BitSet moved = new BitSet();
            for (int i = counts[0]; i < counts[0] + counts[1]; i++) {
                int v = corners[i];
                if (moved.get(v)) continue;
                moved.set(v);
                float x = places[v * 3], y = places[v * 3 + 1], z = places[v * 3 + 2];
                for (int row = 0; row < 3; row++) {
                    loadPlaces[v * 3 + row] = turn[row] * x + turn[4 + row] * y + turn[8 + row] * z + turn[12 + row];
                }
            }
            port = new float[3];
            OwnDouble.rim(moves, 0, 1f, 0f, new float[16], port);
        } else if (own.mesh.contains("lever")) {
            port = new float[] {moves[6], moves[7], moves[8]};
        }
        Hull holding = new Hull(places, held.stream().mapToInt(Integer::intValue).toArray());
        Hull loading = new Hull(loadPlaces, loaded.stream().mapToInt(Integer::intValue).toArray());
        for (Still still : STILLS) {
            boolean roundIn = still.doing == ArmActs.LOADING && !still.clip;
            float deepest = deepest(arms, own, shape, layout, roundIn ? loading : holding, still, roundIn ? port : null);
            Checks.check(deepest <= DEEPEST,
                    String.format(Locale.ROOT, "the left arm goes no more than %.0f mm into %s %s: %.1f mm", DEEPEST * 1000f, own.what, still.name, deepest * 1000f));
        }
    }

    /** How deep the deepest of the left arm's vertices is inside the gun, in metres. */
    private static float deepest(ArmModel arms, OwnModel own, WeaponShape shape, OwnModel.Layout layout, Hull hull, Still still, float[] port) {
        float aimedAhead = own.eyeBehind * TPM, aimedY = -own.sightLine * TPM;
        float hipAhead = ViewModel.hipAhead(aimedAhead);
        float raised = ViewModelMath.smooth(still.aim);
        float atX = ViewModel.HIP_LONG_X * (1f - raised), atY = ViewModel.HIP_LONG_Y + (aimedY - ViewModel.HIP_LONG_Y) * raised;
        float ahead = hipAhead + (aimedAhead - hipAhead) * raised;
        float yaw = -(float) Math.atan(atX * 0.2f), pitch = -(float) Math.atan((atY - aimedY) * 0.2f);
        float roll = (float) Math.toRadians(8) * (1f - raised);
        float[] out = new float[16], steady = new float[16], grip = new float[3];
        ViewModelMath.hold(new float[3], new float[] {0f, 1f, 0f}, own.top(), layout.muzzle(), TPM, atX, atY, ahead, out);
        ViewModelMath.place(out, 0f, 0f, 0f, grip);
        System.arraycopy(out, 0, steady, 0, 16);
        ViewModelMath.turn(steady, pitch, yaw, roll, grip[0], grip[1], -ahead, 0f, 0f, 0f);
        ViewModelMath.turn(out, pitch + (float) Math.toRadians(still.kickPitch), yaw, roll, grip[0], grip[1], -ahead, 0f, 0f, still.kickBack);
        float[] pointing = new float[3], over = new float[3];
        ViewModelMath.turned(out, new float[] {0f, 1f, 0f}, pointing);
        ViewModelMath.turned(out, own.top(), over);
        ArmPose.unit(pointing[0], pointing[1], pointing[2], pointing);
        ArmPose.unit(over[0], over[1], over[2], over);
        ArmHolds.Placed placed = new ArmHolds.Placed(shape, out, steady, 0f, layout.heldY(), own.pump ? ArmHolds.BOLT_TRAVEL : own.travel);
        if (port != null) {
            placed.port(port);
        }
        if (layout.moves().length >= 3 && selfLoader(own)) {
            placed.charged(new float[] {layout.moves()[0], layout.moves()[1], layout.moves()[2]});
        }
        ArmHolds.Weapon weapon = new ArmHolds.Weapon(true, false, placed.at(shape.grip), pointing, over, placed);
        ArmHolds.Doing doing = new ArmHolds.Doing(still.aim, true, action(own), -1f, still.doing, still.done, still.round, 1.2f, -1f, still.clip, -1f,
                still.clip ? -1f : 2f, 2.4f, 0f);
        float[][] skins = new float[2][arms.bones.length * 16], said = new float[2][ArmPose.SAYS];
        float[] same = {1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};
        int both = ArmHolds.place(arms, ViewModel.OWN_TILES_PER_METRE, weapon, doing, same, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled());
        Checks.check(both == 2, own.what + " " + still.name + " has both arms");
        float[] skin = skins[1];
        ByteBuffer v = arms.vertices;
        int first = arms.partFirst[ArmModel.ARM], count = arms.partCount[ArmModel.ARM];
        BitSet seen = new BitSet();
        float deepest = 0f;
        for (int i = first; i < first + count; i++) {
            int vertex = arms.indices.getInt(i * 4);
            if (seen.get(vertex)) continue;
            seen.set(vertex);
            int at = vertex * ArmModel.VERTEX_BYTES;
            float x = v.getFloat(at), y = v.getFloat(at + 4), z = v.getFloat(at + 8);
            float[] p = new float[3];
            for (int k = 0; k < 4; k++) {
                int bone = v.get(at + ArmModel.BONES_AT + k) & 0xFF;
                float w = (v.get(at + ArmModel.WEIGHTS_AT + k) & 0xFF) / 255f;
                for (int row = 0; row < 3; row++) {
                    p[row] += w * (skin[bone * 16 + row] * x + skin[bone * 16 + 4 + row] * y + skin[bone * 16 + 8 + row] * z + skin[bone * 16 + 12 + row]);
                }
            }
            // Back into the mesh's axes: `out` is a scale times a turn, perhaps mirrored, so its transpose over the scale squared undoes it.
            float[] d = {p[0] - out[12], p[1] - out[13], p[2] - out[14]}, m = new float[3];
            for (int c = 0; c < 3; c++) {
                m[c] = (out[c * 4] * d[0] + out[c * 4 + 1] * d[1] + out[c * 4 + 2] * d[2]) / (TPM * TPM);
            }
            deepest = Math.max(deepest, hull.depth(m[0], m[1], m[2]));
        }
        return deepest;
    }

    /**
     * A gun's mesh filled slice by slice across its barrel: in each slice square to the barrel,
     * everything a flood from the slice's border cannot reach is the gun, hollows such as the
     * space between barrel and handguard included, and each cell of it keeps how far it is from
     * the nearest cell outside, within its slice.
     */
    private static final class Hull {
        static final float CELL = 0.001f, SLICE = 0.002f, FROM = -0.12f, TO = 0.62f;
        final float x0, z0;
        final int nx, nz, ny;
        /** Depth inside the gun in cells times ten (chamfer 10/14), 0 outside. */
        final short[] depth;

        Hull(float[] places, int[] tris) {
            float xa = 1e9f, xb = -1e9f, za = 1e9f, zb = -1e9f;
            for (int t : tris) {
                xa = Math.min(xa, places[t * 3]);
                xb = Math.max(xb, places[t * 3]);
                za = Math.min(za, places[t * 3 + 2]);
                zb = Math.max(zb, places[t * 3 + 2]);
            }
            x0 = xa - 4 * CELL;
            z0 = za - 4 * CELL;
            nx = (int) ((xb - xa) / CELL) + 9;
            nz = (int) ((zb - za) / CELL) + 9;
            ny = (int) ((TO - FROM) / SLICE) + 1;
            depth = new short[nx * nz * ny];
            boolean[] wall = new boolean[nx * nz];
            int[] queue = new int[nx * nz];
            ArrayList<ArrayList<Integer>> buckets = new ArrayList<>();
            for (int s = 0; s < ny; s++) buckets.add(new ArrayList<>());
            for (int t = 0; t < tris.length; t += 3) {
                float ya = Math.min(places[tris[t] * 3 + 1], Math.min(places[tris[t + 1] * 3 + 1], places[tris[t + 2] * 3 + 1]));
                float yb = Math.max(places[tris[t] * 3 + 1], Math.max(places[tris[t + 1] * 3 + 1], places[tris[t + 2] * 3 + 1]));
                int sa = Math.max(0, (int) Math.ceil((ya - FROM) / SLICE)), sb = Math.min(ny - 1, (int) Math.floor((yb - FROM) / SLICE));
                for (int s = sa; s <= sb; s++) buckets.get(s).add(t);
            }
            short[] d = new short[nx * nz];
            for (int s = 0; s < ny; s++) {
                Arrays.fill(wall, false);
                float y = FROM + s * SLICE;
                for (int t : buckets.get(s)) {
                    float[] cut = new float[4];
                    int found = 0;
                    for (int e = 0; e < 3 && found < 2; e++) {
                        int a = tris[t + e] * 3, b = tris[t + (e + 1) % 3] * 3;
                        float ya = places[a + 1], yb = places[b + 1];
                        if ((ya - y) * (yb - y) > 0f || ya == yb) continue;
                        float k = (y - ya) / (yb - ya);
                        cut[found * 2] = places[a] + (places[b] - places[a]) * k;
                        cut[found * 2 + 1] = places[a + 2] + (places[b + 2] - places[a + 2]) * k;
                        found++;
                    }
                    if (found < 2) continue;
                    float length = (float) Math.hypot(cut[2] - cut[0], cut[3] - cut[1]);
                    int steps = Math.max(1, (int) (length / (CELL * 0.4f)));
                    for (int i = 0; i <= steps; i++) {
                        float x = cut[0] + (cut[2] - cut[0]) * i / steps, z = cut[1] + (cut[3] - cut[1]) * i / steps;
                        wall[(int) ((z - z0) / CELL) * nx + (int) ((x - x0) / CELL)] = true;
                    }
                }
                Arrays.fill(d, Short.MAX_VALUE);
                int head = 0, tail = 0;
                for (int cz = 0; cz < nz; cz++) {
                    for (int cx = 0; cx < nx; cx++) {
                        if ((cx == 0 || cz == 0 || cx == nx - 1 || cz == nz - 1) && !wall[cz * nx + cx]) {
                            d[cz * nx + cx] = 0;
                            queue[tail++] = cz * nx + cx;
                        }
                    }
                }
                while (head < tail) {
                    int c = queue[head++], cx = c % nx, cz = c / nx;
                    int[] next = {cx > 0 ? c - 1 : -1, cx < nx - 1 ? c + 1 : -1, cz > 0 ? c - nx : -1, cz < nz - 1 ? c + nx : -1};
                    for (int at : next) {
                        if (at < 0 || d[at] == 0 || wall[at]) continue;
                        d[at] = 0;
                        queue[tail++] = at;
                    }
                }
                for (int cz = 0; cz < nz; cz++) {
                    for (int cx = 0; cx < nx; cx++) {
                        int at = cz * nx + cx;
                        if (d[at] == 0) continue;
                        int best = d[at];
                        if (cx > 0) best = Math.min(best, d[at - 1] + 10);
                        if (cz > 0) best = Math.min(best, d[at - nx] + 10);
                        if (cx > 0 && cz > 0) best = Math.min(best, d[at - nx - 1] + 14);
                        if (cx < nx - 1 && cz > 0) best = Math.min(best, d[at - nx + 1] + 14);
                        d[at] = (short) best;
                    }
                }
                for (int cz = nz - 1; cz >= 0; cz--) {
                    for (int cx = nx - 1; cx >= 0; cx--) {
                        int at = cz * nx + cx;
                        if (d[at] == 0) continue;
                        int best = d[at];
                        if (cx < nx - 1) best = Math.min(best, d[at + 1] + 10);
                        if (cz < nz - 1) best = Math.min(best, d[at + nx] + 10);
                        if (cx < nx - 1 && cz < nz - 1) best = Math.min(best, d[at + nx + 1] + 14);
                        if (cx > 0 && cz < nz - 1) best = Math.min(best, d[at + nx - 1] + 14);
                        d[at] = (short) best;
                    }
                }
                System.arraycopy(d, 0, depth, s * nx * nz, nx * nz);
            }
        }

        /** How far inside the gun a place of the mesh's is, in metres: 0 outside it or off the hull. */
        float depth(float x, float y, float z) {
            int s = Math.round((y - FROM) / SLICE), cx = (int) ((x - x0) / CELL), cz = (int) ((z - z0) / CELL);
            if (s < 0 || s >= ny || cx < 0 || cz < 0 || cx >= nx || cz >= nz) return 0f;
            int d = depth[(s * nz + cz) * nx + cx];
            return d == 0 ? 0f : (d / 10f - 0.5f) * CELL;
        }
    }
}

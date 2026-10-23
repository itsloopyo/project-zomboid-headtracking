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
 * the sights up, in a shot's kick, racking, while a round goes in, and through a magazine's
 * change, the magazine in the hand included. With the sights up the forearm went 10 to 18 mm
 * into a magazine or a sawn-off's grip, the hand that loads went 11 to 19 mm into the receiver,
 * and the fist round a magazine 10 to 14 mm into it. On each own handgun both arms are held to
 * the same through every hold it has: the fists round the grip went 13 to 20 mm into it.
 *
 * How deep a place is is how far it is from the nearest place outside the gun in any direction:
 * read only across the barrel, a finger a little into the front of a pistol's grip read as deep
 * as the grip is half wide.
 */
final class HandClipTests {
    private HandClipTests() {}

    /** How far into a gun any of the left arm may go, in metres. */
    private static final float DEEPEST = 0.005f;
    private static final float TPM = 0.74355f;

    /**
     * aim, kick pitch in degrees, kick back, what is being done, for how long, whether a clip goes in, how far a round is,
     * and for a magazine's change, which only a gun that takes one goes through: how long ago one was seated, how long
     * since one came out, and how long one has been fetched for.
     */
    private record Still(String name, float aim, float kickPitch, float kickBack, int doing, float done, boolean clip, float round,
            boolean change, float seated, float out, float fetching) {
        Still(String name, float aim, float kickPitch, float kickBack, int doing, float done, boolean clip, float round) {
            this(name, aim, kickPitch, kickBack, doing, done, clip, round, false, -1f, -1f, clip ? -1f : 2f);
        }

        static Still change(String name, int doing, float done, boolean clip, float seated, float out, float fetching) {
            return new Still(name, 0f, 0f, 0f, doing, done, clip, 0.5f, true, seated, out, fetching);
        }
    }

    /** How long a magazine is fetched for, in seconds. */
    private static final float FETCH_TAKES = 2.4f;

    /** Which handguns a hold is had by. */
    private static final int EVERY = 0, PISTOLS = 1, REVOLVERS = 2;

    /** A handgun's hold: which handguns have it, and the still. */
    private record HandgunStill(int by, Still still) {}

    /** How long the game is taken to be over what is being done, in seconds: Doing's pace. */
    private static final float PACE = 1.2f;

    private static final HandgunStill[] HANDGUN_STILLS = {
        new HandgunStill(EVERY, new Still("at the hip", 0f, 0f, 0f, ArmActs.HOLDING, 0f, true, 0.5f)),
        new HandgunStill(EVERY, new Still("half raised", 0.5f, 0f, 0f, ArmActs.HOLDING, 0f, true, 0.5f)),
        new HandgunStill(EVERY, new Still("with the sights up", 1f, 0f, 0f, ArmActs.HOLDING, 0f, true, 0.5f)),
        new HandgunStill(EVERY, new Still("in a shot's kick with the sights up", 1f, 4f, 0.03f, ArmActs.HOLDING, 0f, true, 0.5f)),
        new HandgunStill(PISTOLS, new Still("reaching for the slide", 0f, 0f, 0f, ArmActs.RACKING, PACE * 0.15f, true, 0.5f)),
        new HandgunStill(PISTOLS, new Still("drawing the slide back", 0f, 0f, 0f, ArmActs.RACKING, PACE * 0.3f, true, 0.5f)),
        new HandgunStill(PISTOLS, new Still("letting the slide go", 0f, 0f, 0f, ArmActs.RACKING, PACE * 0.55f, true, 0.5f)),
        new HandgunStill(PISTOLS, Still.change("taking hold of the magazine in the grip", ArmActs.LOADING, 0.5f, true, -1f, -1f, -1f)),
        new HandgunStill(PISTOLS, Still.change("drawing the magazine out", ArmActs.LOADING, 0.5f, false, -1f, ArmActs.DRAWN_CLEAR_IN / 2f, -1f)),
        new HandgunStill(PISTOLS, Still.change("with the magazine drawn clear", ArmActs.LOADING, 0.5f, false, -1f, ArmActs.DRAWN_CLEAR_IN, -1f)),
        new HandgunStill(PISTOLS, Still.change("taking the magazine away", ArmActs.LOADING, 0.5f, false, -1f, (ArmActs.DRAWN_CLEAR_IN + ArmActs.TAKEN_IN) / 2f, -1f)),
        new HandgunStill(PISTOLS, Still.change("bringing the next magazine up", ArmActs.LOADING, 0.5f, false, -1f, -1f, FETCH_TAKES * 0.45f)),
        new HandgunStill(PISTOLS, Still.change("with the next magazine under the grip", ArmActs.LOADING, 0.5f, false, -1f, -1f, FETCH_TAKES)),
        new HandgunStill(PISTOLS, Still.change("pushing the magazine home", ArmActs.HOLDING, 0f, true, 0f, -1f, -1f)),
        new HandgunStill(PISTOLS, Still.change("with the magazine half way home", ArmActs.HOLDING, 0f, true, ArmActs.HOME_IN / 2f, -1f, -1f)),
        new HandgunStill(PISTOLS, Still.change("letting the magazine go", ArmActs.HOLDING, 0f, true, ArmActs.HOME_IN + 0.2f, -1f, -1f)),
        new HandgunStill(REVOLVERS, new Still("bringing a round to the cylinder", 0f, 0f, 0f, ArmActs.LOADING, 1.2f, false, 0.3f)),
        new HandgunStill(REVOLVERS, new Still("at the chamber", 0f, 0f, 0f, ArmActs.LOADING, 1.2f, false, 0.42f)),
        new HandgunStill(REVOLVERS, new Still("pushing a round in", 0f, 0f, 0f, ArmActs.LOADING, 1.2f, false, 0.6f)),
        new HandgunStill(REVOLVERS, new Still("taking the hand back from the cylinder", 0f, 0f, 0f, ArmActs.LOADING, 1.2f, false, 0.75f)),
    };

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
        Still.change("taking hold of the magazine in the well", ArmActs.LOADING, 0.5f, true, -1f, -1f, -1f),
        Still.change("drawing the magazine out", ArmActs.LOADING, 0.5f, false, -1f, ArmActs.DRAWN_CLEAR_IN / 2f, -1f),
        Still.change("with the magazine drawn clear", ArmActs.LOADING, 0.5f, false, -1f, ArmActs.DRAWN_CLEAR_IN, -1f),
        Still.change("taking the magazine away", ArmActs.LOADING, 0.5f, false, -1f, (ArmActs.DRAWN_CLEAR_IN + ArmActs.TAKEN_IN) / 2f, -1f),
        Still.change("bringing the next magazine up", ArmActs.LOADING, 0.5f, false, -1f, -1f, FETCH_TAKES * 0.45f),
        Still.change("with the next magazine under the well", ArmActs.LOADING, 0.5f, false, -1f, -1f, FETCH_TAKES),
        Still.change("pushing the magazine home", ArmActs.HOLDING, 0f, true, 0f, -1f, -1f),
        Still.change("with the magazine half way home", ArmActs.HOLDING, 0f, true, ArmActs.HOME_IN / 2f, -1f, -1f),
        Still.change("letting the magazine go", ArmActs.HOLDING, 0f, true, ArmActs.HOME_IN + 0.2f, -1f, -1f),
    };

    static void run() throws IOException {
        ArmModel arms;
        try (InputStream packed = HandClipTests.class.getResourceAsStream("/models/arms.mesh.gz")) {
            arms = new ArmModel(packed);
        }
        for (OwnModel own : OwnModel.ALL) {
            if (own.handgun) {
                handgun(arms, own);
            } else {
                gun(arms, own);
            }
        }
    }

    /**
     * Both arms on a handgun: on its grip at the hip and with the sights up, a pistol's slide racked, drawn back as far as
     * the hand draws it, and its magazine changed, and a revolver's rounds put in with its cylinder swung out on its crane.
     */
    private static void handgun(ArmModel arms, OwnModel own) throws IOException {
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
        int[] counts = layout.counts();
        boolean revolver = counts.length == OwnRevolver.PARTS;
        int[] starts = new int[counts.length + 1];
        for (int part = 0; part < counts.length; part++) {
            starts[part + 1] = starts[part] + counts[part];
        }
        float[] moves = layout.moves();
        if (revolver) {
            // Its frame, trigger, hammer, cylinder, crane and ejector, as held and with the last three swung out on the crane, where
            // the next round goes into the chamber at the top.
            Hull holding = new Hull(places, Arrays.copyOfRange(corners, 0, starts[6]));
            float[] swung = new float[16], open = places.clone();
            OwnGun.turnAbout(swung, moves[OwnRevolver.CRANE_X], 0f, moves[OwnRevolver.CRANE_Z], 0f, 1f, 0f, moves[OwnRevolver.SWING]);
            BitSet moved = new BitSet();
            for (int i = starts[3]; i < starts[6]; i++) {
                int v = corners[i];
                if (moved.get(v)) continue;
                moved.set(v);
                float x = places[v * 3], y = places[v * 3 + 1], z = places[v * 3 + 2];
                for (int row = 0; row < 3; row++) {
                    open[v * 3 + row] = swung[row] * x + swung[4 + row] * y + swung[8 + row] * z + swung[12 + row];
                }
            }
            Hull opened = new Hull(open, Arrays.copyOfRange(corners, 0, starts[6]));
            float[] port = new float[3];
            ViewModelMath.place(swung, moves[OwnRevolver.CHAMBER_X], moves[OwnRevolver.CHAMBER_Y], moves[OwnRevolver.CHAMBER_Z], port);
            for (HandgunStill held : HANDGUN_STILLS) {
                if (held.by == PISTOLS) continue;
                boolean loading = held.still.doing == ArmActs.LOADING;
                check(own, held.still, deepest(arms, own, shape, layout, loading ? opened : holding, null, held.still, loading ? port : null, ArmActs.REVOLVER));
            }
            return;
        }
        // Its frame, slide and magazine: the magazine out of its well while the game has it out.
        int[] withMagazine = new int[counts[0] + counts[2] + counts[4]];
        System.arraycopy(corners, 0, withMagazine, 0, counts[0]);
        System.arraycopy(corners, starts[2], withMagazine, counts[0], counts[2]);
        System.arraycopy(corners, starts[4], withMagazine, counts[0] + counts[2], counts[4]);
        int[] without = Arrays.copyOf(withMagazine, counts[0] + counts[2]);
        Hull holding = new Hull(places, withMagazine), emptied = new Hull(places, without);
        for (HandgunStill held : HANDGUN_STILLS) {
            if (held.by == REVOLVERS) continue;
            Still still = held.still;
            Hull hull = still.change && !SelfLoading.magazineIn(still.clip, still.doing, still.seated) ? emptied : holding;
            if (still.doing == ArmActs.RACKING) {
                // The slide drawn back as far as the hand on it has drawn it.
                float back = ArmHolds.slideRacked(still.done / PACE) * layout.travel();
                float[] racked = places.clone();
                for (int i = starts[2]; i < starts[3]; i++) {
                    racked[corners[i] * 3 + 1] = places[corners[i] * 3 + 1] - back;
                }
                hull = new Hull(racked, withMagazine);
            }
            check(own, still, deepest(arms, own, shape, layout, hull, null, still, null, ArmActs.HANDGUN));
        }
    }

    private static void check(OwnModel own, Still still, float deepest) {
        Checks.check(deepest <= DEEPEST,
                String.format(Locale.ROOT, "the arms go no more than %.0f mm into %s %s: %.1f mm", DEEPEST * 1000f, own.what, still.name, deepest * 1000f));
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
        // A self-loader's magazine where it is in its well, to be read where the hand carries it.
        Hull magazine = null;
        if (selfLoader(own)) {
            int at = counts[0] + counts[1] + counts[2] + counts[3];
            magazine = new Hull(places, Arrays.copyOfRange(corners, at, at + counts[4]));
        }
        for (Still still : STILLS) {
            if (still.change && action(own) != ArmActs.BOLT) {
                continue;
            }
            boolean roundIn = still.doing == ArmActs.LOADING && !still.clip;
            boolean inWell = still.clip && SelfLoading.magazineIn(true, still.doing, still.seated);
            Hull hull = still.change ? (inWell ? holding : loading) : roundIn ? loading : holding;
            float deepest = deepest(arms, own, shape, layout, hull, magazine, still, roundIn && !still.change ? port : null, action(own));
            Checks.check(deepest <= DEEPEST,
                    String.format(Locale.ROOT, "the left arm goes no more than %.0f mm into %s %s: %.1f mm", DEEPEST * 1000f, own.what, still.name, deepest * 1000f));
        }
    }

    /** How deep the deepest of the vertices of the left arm, and on a handgun of either arm, is inside the gun, in metres. */
    private static float deepest(ArmModel arms, OwnModel own, WeaponShape shape, OwnModel.Layout layout, Hull hull, Hull magazine, Still still, float[] port,
            int action) {
        // An OwnModel's own eyeBehind, sightLine and travel are filled only once it is made in the game: the layout has them here.
        float aimedAhead = layout.eyeBehind() * TPM, aimedY = -layout.sightLine() * TPM;
        float raised = ViewModelMath.smooth(still.aim);
        float atX, atY, ahead;
        if (own.handgun) {
            float off = ViewModelMath.arc(raised);
            atX = ViewModel.HIP_X * (1f - raised);
            atY = ViewModel.HIP_Y + (aimedY - ViewModel.HIP_Y) * raised - ViewModel.RAISE_DIPS * off;
            ahead = ViewModel.HIP_AHEAD + (aimedAhead - ViewModel.HIP_AHEAD) * raised + ViewModel.RAISE_REACHES * off;
        } else {
            float hipAhead = ViewModel.hipAhead(aimedAhead);
            atX = ViewModel.HIP_LONG_X * (1f - raised);
            atY = ViewModel.HIP_LONG_Y + (aimedY - ViewModel.HIP_LONG_Y) * raised;
            ahead = hipAhead + (aimedAhead - hipAhead) * raised;
        }
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
        ArmHolds.Placed placed = new ArmHolds.Placed(shape, out, steady, 0f, layout.heldY(), own.pump ? ArmHolds.BOLT_TRAVEL : layout.travel());
        if (port != null) {
            placed.port(port);
        }
        if (layout.moves().length >= 3 && selfLoader(own)) {
            placed.charged(new float[] {layout.moves()[0], layout.moves()[1], layout.moves()[2]});
        }
        ArmHolds.Weapon weapon = new ArmHolds.Weapon(true, own.handgun, placed.at(shape.grip), pointing, over, placed);
        ArmHolds.Doing doing = new ArmHolds.Doing(still.aim, true, action, -1f, still.doing, still.done, still.round, PACE, still.seated, still.clip,
                still.out, still.fetching, FETCH_TAKES, 0f);
        float[][] skins = new float[2][arms.bones.length * 16], said = new float[2][ArmPose.SAYS];
        float[] same = {1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};
        ArmHolds.Carried carried = new ArmHolds.Carried();
        int both = ArmHolds.place(arms, ViewModel.OWN_TILES_PER_METRE, weapon, doing, same, skins, said, carried, new ArmHolds.Settled());
        Checks.check(both == 2, own.what + " " + still.name + " has both arms");
        // The magazine in the hand, placed as the gun draws it there: from its place in the well to the view.
        float[] inHand = null;
        if (magazine != null && carried.what == ArmHolds.MAGAZINE) {
            inHand = new float[16];
            OwnSelfLoader.inHand(carried.at, shape, (float) Math.sqrt(out[4] * out[4] + out[5] * out[5] + out[6] * out[6]), new float[3], inHand);
        }
        float deepest = 0f;
        for (int side = own.handgun ? 0 : 1; side < both; side++) {
            deepest = Math.max(deepest, deepest(arms, skins[side], out, hull, magazine, inHand));
        }
        return deepest;
    }

    /** How deep the deepest of an arm's vertices, skinned, is inside the gun, or in the magazine in its hand, in metres. */
    private static float deepest(ArmModel arms, float[] skin, float[] out, Hull hull, Hull magazine, float[] inHand) {
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
            if (inHand != null) {
                float metre2 = inHand[0] * inHand[0] + inHand[1] * inHand[1] + inHand[2] * inHand[2];
                float[] h = {p[0] - inHand[12], p[1] - inHand[13], p[2] - inHand[14]};
                for (int c = 0; c < 3; c++) {
                    m[c] = (inHand[c * 4] * h[0] + inHand[c * 4 + 1] * h[1] + inHand[c * 4 + 2] * h[2]) / metre2;
                }
                deepest = Math.max(deepest, magazine.depth(m[0], m[1], m[2]));
            }
        }
        return deepest;
    }

    /**
     * A gun's mesh filled slice by slice across its barrel: in each slice square to the barrel,
     * everything a flood from the slice's border cannot reach is the gun, hollows such as the
     * space between barrel and handguard included, and each cell of it keeps how far it is from
     * the nearest cell outside, in the slice or in the slices either side.
     */
    private static final class Hull {
        static final float CELL = 0.001f, SLICE = 0.002f, FROM = -0.12f, TO = 0.62f;
        final float x0, z0;
        final int nx, nz, ny;
        /** Depth inside the gun in tenths of a cell (chamfer: 10 a cell across, 20 a slice along, 14, 22 and 24 aslant), 0 outside. */
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
            // From the inside of every slice, how far each cell is from the outside in any direction: forward over the cells
            // before it, then back over those after.
            for (int at = 0; at < depth.length; at++) {
                depth[at] = depth[at] == 0 ? 0 : Short.MAX_VALUE;
            }
            int[][] before = new int[13][];
            int k = 0;
            for (int ds = -1; ds <= 0; ds++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (ds == 0 && (dz > 0 || dz == 0 && dx >= 0)) continue;
                        int across = Math.abs(dx) + Math.abs(dz);
                        int cost = ds == 0 ? (across == 1 ? 10 : 14) : across == 0 ? 20 : across == 1 ? 22 : 24;
                        before[k++] = new int[] {dx, dz, ds, cost};
                    }
                }
            }
            for (int way = 1; way >= -1; way -= 2) {
                for (int i = 0; i < depth.length; i++) {
                    int at = way > 0 ? i : depth.length - 1 - i;
                    if (depth[at] == 0) continue;
                    int cx = at % nx, cz = at / nx % nz, s = at / (nx * nz), best = depth[at];
                    for (int[] n : before) {
                        int x = cx + n[0] * way, z = cz + n[1] * way, sl = s + n[2] * way;
                        int from = x < 0 || z < 0 || sl < 0 || x >= nx || z >= nz || sl >= ny ? 0 : depth[(sl * nz + z) * nx + x];
                        best = Math.min(best, from + n[3]);
                    }
                    depth[at] = (short) best;
                }
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

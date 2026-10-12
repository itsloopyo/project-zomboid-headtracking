package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The crowns of one chunk's trees and bushes, as what each is made from and nothing of the
 * game's: numbers, and the places its sprite paints leaves at, which the mod worked out itself.
 *
 * A crown is a third of the work of building a chunk in a wood, and all of it is arithmetic
 * on these. So a chunk's build only writes down what its crowns are made from, and they are
 * made apart from it: not at all while a new build of the chunk writes down what the last
 * one did, and otherwise on a thread of their own, with the crowns the chunk had standing
 * until the new ones are ready.
 */
final class Crowns {
    /** A crown's foot, where its sprite shows the foot, how far off it is made for, its bole, what addCrown reads of its sprite's frame, and its room. */
    private static final int X = 0, Y = 1, BASE = 2, FOOT_U = 3, FOOT_V = 4, AWAY = 5, BOLE_RADIUS = 6, BOLE_TALL = 7, SCALE = 8, LEFT = 9, TOP = 10,
            WIDTH = 11, HEIGHT = 12, U_START = 13, V_START = 14, U_END = 15, V_END = 16, RED = 17, GREEN = 18, BLUE = 19, OPEN = 20, ROOM = 21, FLOATS = 25;
    private static final int TEXTURE = 0, BARK = 1, BOLE_BARK = 2, SPRIG = 3, SEED = 4, INTS = 5;
    /** How far from the eye a plant has a crown of sprigs, and within what it has them at their smallest and most. */
    static final float REACH = 40f, NEAR = 12f;
    /** How coarse the crown of a plant at REACH is, which one nearer comes to by degrees: see Flora.addCrown. */
    private static final float FAR_DETAIL = 0.3f;
    /** Within how far of the eye a crown is one stood under, with twigs and more leaves inside it, and from how far it comes to that by degrees; and how far off its limbs are made. */
    private static final float CLOSE = 4f, CLOSING = 9f, LIMB_REACH = 16f;

    /** How fine a crown that far off is made: 1 the finest. */
    static float detail(float away) {
        return 1f + (FAR_DETAIL - 1f) * Math.clamp((away - NEAR) / (REACH - NEAR), 0f, 1f);
    }

    /** How much a crown that far off is one stood under: 1 wholly. */
    static float close(float away) {
        return Math.clamp((CLOSING - away) / (CLOSING - CLOSE), 0f, 1f);
    }

    /** Whether a crown that far off grows limbs from its tree's bole. */
    static boolean limbed(float away, int sprig) {
        return sprig == Flora.BARE || away < LIMB_REACH;
    }

    private float[] floats = new float[FLOATS * 8];
    private int[] ints = new int[INTS * 8];
    private float[][] places = new float[8][];
    private int count;

    void clear() {
        // The places are the mod's own and long lived, one array a texture: nothing is held by leaving them named.
        count = 0;
    }

    boolean isEmpty() {
        return count == 0;
    }

    /**
     * Writes down a crown to be made in the mesh of the texture, as from that far off. Its
     * bole is its tree's, with the bole's bark, and no height for a plant with none: such a
     * crown, and any too far off for limbs, is made in its own bark. room is Flora.addCrown's.
     */
    void add(int texture, SpriteGeometry.Frame frame, float[] leafPlaces, float x, float y, float base, float footU, float footV, float away,
            float boleRadius, float boleTall, int boleBark, int bark, int sprig, int seed, float[] room) {
        if (count == places.length) {
            floats = Arrays.copyOf(floats, floats.length * 2);
            ints = Arrays.copyOf(ints, ints.length * 2);
            places = Arrays.copyOf(places, places.length * 2);
        }
        int at = count * FLOATS;
        floats[at + X] = x;
        floats[at + Y] = y;
        floats[at + BASE] = base;
        floats[at + FOOT_U] = footU;
        floats[at + FOOT_V] = footV;
        floats[at + AWAY] = away;
        floats[at + BOLE_RADIUS] = boleRadius;
        floats[at + BOLE_TALL] = boleTall;
        floats[at + SCALE] = frame.scale;
        floats[at + LEFT] = frame.left;
        floats[at + TOP] = frame.top;
        floats[at + WIDTH] = frame.width;
        floats[at + HEIGHT] = frame.height;
        floats[at + U_START] = frame.uStart;
        floats[at + V_START] = frame.vStart;
        floats[at + U_END] = frame.uEnd;
        floats[at + V_END] = frame.vEnd;
        floats[at + RED] = frame.red;
        floats[at + GREEN] = frame.green;
        floats[at + BLUE] = frame.blue;
        floats[at + OPEN] = frame.open;
        System.arraycopy(room, 0, floats, at + ROOM, 4);
        at = count * INTS;
        ints[at + TEXTURE] = texture;
        ints[at + BARK] = bark;
        ints[at + BOLE_BARK] = boleBark;
        ints[at + SPRIG] = sprig;
        ints[at + SEED] = seed;
        places[count++] = leafPlaces;
    }

    /**
     * Writes the same crowns down in another, as from another distance: a chunk that has
     * passed into another band and holds nothing else the band changes has only its crowns
     * made again, from what its last build read.
     */
    void copyTo(Crowns into, float away) {
        if (into.places.length < count) {
            into.floats = new float[floats.length];
            into.ints = new int[ints.length];
            into.places = new float[places.length][];
        }
        System.arraycopy(floats, 0, into.floats, 0, count * FLOATS);
        System.arraycopy(ints, 0, into.ints, 0, count * INTS);
        System.arraycopy(places, 0, into.places, 0, count);
        into.count = count;
        for (int i = 0; i < count; i++) {
            into.floats[i * FLOATS + AWAY] = away;
        }
    }

    /** Whether the two would be made into the same triangles: the same crowns in the same order, to the bit. */
    boolean same(Crowns other) {
        if (count != other.count) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            if (places[i] != other.places[i]) {
                return false;
            }
        }
        for (int i = 0; i < count * INTS; i++) {
            if (ints[i] != other.ints[i]) {
                return false;
            }
        }
        for (int i = 0; i < count * FLOATS; i++) {
            if (Float.floatToRawIntBits(floats[i]) != Float.floatToRawIntBits(other.floats[i])) {
                return false;
            }
        }
        return true;
    }

    /** Makes the crowns into the set, leaves in the mesh of each one's texture and wood in the plain one. The frame is scratch. */
    void mesh(SpriteGeometry.MeshSet into, int plain, SpriteGeometry.Frame frame) {
        float[] room = new float[4];
        for (int i = 0; i < count; i++) {
            int at = i * FLOATS, of = i * INTS;
            frame.scale = floats[at + SCALE];
            frame.left = floats[at + LEFT];
            frame.top = floats[at + TOP];
            frame.width = floats[at + WIDTH];
            frame.height = floats[at + HEIGHT];
            frame.uStart = floats[at + U_START];
            frame.vStart = floats[at + V_START];
            frame.uEnd = floats[at + U_END];
            frame.vEnd = floats[at + V_END];
            frame.red = floats[at + RED];
            frame.green = floats[at + GREEN];
            frame.blue = floats[at + BLUE];
            frame.open = floats[at + OPEN];
            float away = floats[at + AWAY];
            System.arraycopy(floats, at + ROOM, room, 0, 4);
            boolean limbed = floats[at + BOLE_TALL] > 0f && limbed(away, ints[of + SPRIG]);
            if (!Flora.addCrown(into.get(ints[of + TEXTURE]), into.get(plain), frame, places[i], floats[at + X], floats[at + Y], floats[at + BASE], floats[at + FOOT_U],
                    floats[at + FOOT_V], detail(away), close(away), limbed ? floats[at + BOLE_RADIUS] : 0f, limbed ? floats[at + BOLE_TALL] : 0f,
                    ints[of + (limbed ? BOLE_BARK : BARK)], ints[of + SPRIG], ints[of + SEED], room)) {
                throw new IllegalStateException("a crown that was measured as one is none");
            }
        }
    }

    /**
     * The crowns of one chunk on their way through the other thread: what they are made
     * from, the build they are made into, where the view that wants them takes them back in,
     * and after the making whether it failed. The logic thread sets all of it but the last
     * before it hands the work over, and reads it again only when the work comes back in done.
     */
    static final class Work implements Runnable {
        final Crowns from;
        final WorldScene.ChunkMeshes into;
        final int slot, plain;
        final boolean summed;
        private final LinkedBlockingQueue<Work> done;
        /** Set by the logic thread when the chunk has been let go of or built again otherwise: the crowns are then not made. */
        volatile boolean unwanted;
        /** When the work was handed over, on System.nanoTime's clock. */
        final long begun = System.nanoTime();
        long sum;
        /** What the making threw, for the logic thread to throw on when it takes the work back in. */
        Throwable failed;

        Work(Crowns from, WorldScene.ChunkMeshes into, int slot, int plain, boolean summed, LinkedBlockingQueue<Work> done) {
            this.from = from;
            this.into = into;
            this.slot = slot;
            this.plain = plain;
            this.summed = summed;
            this.done = done;
        }

        @Override
        public void run() {
            try {
                if (!unwanted) {
                    made.clear();
                    from.mesh(made, plain, frame);
                    if (summed) {
                        sum = WorldScene.floatsOf(made);
                    }
                    into.fill(made, none);
                    made.clear();
                    if (++sinceTrimmed >= TRIMMED_EVERY) {
                        sinceTrimmed = 0;
                        made.trim();
                    }
                }
            } catch (Throwable fault) {
                failed = fault;
            }
            done.add(this);
        }
    }

    /** The one thread's own: the set it makes crowns in, an empty one, a frame, and how many chunks it has made since it last let go of room it does not use. */
    private static final SpriteGeometry.MeshSet made = new SpriteGeometry.MeshSet(), none = new SpriteGeometry.MeshSet();
    private static final SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
    private static final int TRIMMED_EVERY = 512;
    private static int sinceTrimmed;

    /**
     * One thread: the crowns of a drive through a wood are a tenth of the logic thread's
     * time, and the game keeps four threads busy on such a drive as it is. It stands a step
     * under the game's threads, so on a PC with no core to spare it takes what they leave,
     * and goes when there has been nothing to do for a while.
     */
    private static final ThreadPoolExecutor WORKER = worker();

    private static ThreadPoolExecutor worker() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), work -> {
            Thread thread = new Thread(work, "CameraUnlock crowns");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    static void begin(Work work) {
        WORKER.execute(work);
    }

    /**
     * Has the thread give back the room its own set keeps, when the builds are let go of: the
     * set is the thread's, so the thread does it, after whatever it was handed before. Twice,
     * since a trim keeps what was asked for since the one before.
     */
    static void letGo() {
        WORKER.execute(() -> {
            made.trim();
            made.trim();
            sinceTrimmed = 0;
        });
    }

    /** The next piece of a view's work to come back, waited for: for when everything it handed over has to be back before the logic thread goes on. */
    static Work awaited(LinkedBlockingQueue<Work> done) {
        try {
            return done.take();
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(stopped);
        }
    }
}

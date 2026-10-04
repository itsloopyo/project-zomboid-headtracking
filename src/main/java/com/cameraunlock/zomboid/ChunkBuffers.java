package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;

/**
 * The world's triangles on the graphics card.
 *
 * Most chunk builds a frame shows are the ones the frame before showed, so a build's vertices
 * are handed to the graphics card once, the first time a frame draws it, and stay there until
 * no frame shows the build any more. All of them live in one buffer, each build in a block of
 * its own, so that every triangle wearing one texture is still one draw however many chunks
 * it is spread over: the draw is given the list of places in the buffer where those triangles
 * are, chunk after chunk in the order the frame lists its chunks.
 *
 * Everything here runs on the render thread but forget.
 *
 * What makes the hand-over between the threads safe is WorldScene's own rule: a build is filled
 * again only when no frame in flight shows it. So while a frame is being drawn, nothing it
 * shows is being written. The logic thread raises a build's version when it has filled it,
 * and the build's block is taken as its picture only while it holds that version.
 */
final class ChunkBuffers {
    private static final int GL_TRIANGLES = 0x0004;
    private static final int GL_FLOAT = 0x1406;
    private static final int GL_ARRAY_BUFFER = 0x8892;
    private static final int GL_DYNAMIC_DRAW = 0x88E8;
    private static final int STRIDE = SpriteGeometry.VERTEX_FLOATS * Float.BYTES;
    /** The buffer is made in whole numbers of this many floats: 16 MB. */
    private static final int GRAIN = 1 << 22;

    /** Builds no frame shows any more, whose blocks the render thread has yet to free. */
    private static final ConcurrentLinkedQueue<WorldScene.ChunkMeshes> forgotten = new ConcurrentLinkedQueue<>();
    private static final BufferSpace space = new BufferSpace();
    private static int buffer;
    /** How long the buffer is, in floats. */
    private static int capacity;
    /** Which making of the buffer this is. A block is of the buffer only while the build holding it names this one: making the buffer anew empties it. Never 0, which a build names when it holds no block. */
    private static int epoch = 1;
    private static FloatBuffer staging = ByteBuffer.allocateDirect(1 << 20).order(ByteOrder.nativeOrder()).asFloatBuffer();
    /** For each range, one after the other, the first vertex and the number of vertices of each run of the buffer it draws. */
    private static IntBuffer firsts = ints(1 << 12), counts = ints(1 << 12);
    private static int[] rangeTexture = new int[256], rangeTint = new int[256], rangeFrom = new int[256], rangeRuns = new int[256];
    private static int ranges, solidRanges;
    private static final Runs solidRuns = new Runs(), laidRuns = new Runs();
    private static int sentBuilds;
    private static long sentFloats;
    /** What the last frame came to, for the logic thread's log: megabytes in use and in all, builds sent and megabytes sent. */
    private static volatile float usedMegabytes, allMegabytes, sentMegabytes;
    private static volatile int sent;

    private ChunkBuffers() {}

    private static IntBuffer ints(int count) {
        return ByteBuffer.allocateDirect(count * Integer.BYTES).order(ByteOrder.nativeOrder()).asIntBuffer();
    }

    /**
     * Says that no frame shows a build any more, so that its block can be freed. Called on the
     * logic thread, before the build is filled again or let go for good.
     */
    static void forget(WorldScene.ChunkMeshes build) {
        if (build.heldEpoch != 0) {
            forgotten.add(build);
        }
    }

    /** How many ranges the frame draws, and how many of them, the first ones, are solid. */
    static int ranges() {
        return ranges;
    }

    static int solidRanges() {
        return solidRanges;
    }

    /** The OpenGL name of the texture a range's triangles wear, 0 for none. */
    static int texture(int range) {
        return rangeTexture[range];
    }

    static int tint(int range) {
        return rangeTint[range];
    }

    static String describe() {
        return String.format(Locale.ROOT, "card holds %.0f of %.0f MB, sent %d builds %.1f MB", usedMegabytes, allMegabytes, sent, sentMegabytes);
    }

    /**
     * Makes ready to draw a frame's chunks: hands the graphics card the builds it does not hold
     * as they now are, and lists the triangles by texture and tint, what is solid first and
     * then what is laid over it. Leaves no buffer bound.
     */
    static void load(ArrayList<WorldScene.ChunkMeshes> chunks) {
        if (buffer == 0) {
            buffer = GL15.glGenBuffers();
        }
        for (WorldScene.ChunkMeshes gone = forgotten.poll(); gone != null; gone = forgotten.poll()) {
            drop(gone);
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, buffer);
        sentBuilds = 0;
        sentFloats = 0;
        // Every build that has been filled again gives its block back before any takes one, so
        // that a chunk's new build can go where its last one was.
        for (int i = 0; i < chunks.size(); i++) {
            WorldScene.ChunkMeshes build = chunks.get(i);
            if (build.heldEpoch == epoch && build.heldVersion != build.version) {
                drop(build);
            }
        }
        boolean fits = true;
        for (int i = 0; i < chunks.size() && fits; i++) {
            if (chunks.get(i).heldEpoch != epoch) {
                fits = send(chunks.get(i));
            }
        }
        if (!fits) {
            remake(chunks);
            for (int i = 0; i < chunks.size(); i++) {
                if (!send(chunks.get(i))) {
                    throw new IllegalStateException("a buffer of " + capacity + " floats made for this frame does not hold it");
                }
            }
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        usedMegabytes = ((long) capacity - space.free()) * Float.BYTES / 1048576f;
        allMegabytes = (long) capacity * Float.BYTES / 1048576f;
        sentMegabytes = sentFloats * Float.BYTES / 1048576f;
        sent = sentBuilds;

        solidRuns.clear();
        laidRuns.clear();
        for (int i = 0; i < chunks.size(); i++) {
            solidRuns.count(chunks.get(i).solid.all);
            laidRuns.count(chunks.get(i).laid.all);
        }
        int most = solidRuns.used + laidRuns.used;
        if (most > rangeTexture.length) {
            rangeTexture = new int[most * 2];
            rangeTint = new int[most * 2];
            rangeFrom = new int[most * 2];
            rangeRuns = new int[most * 2];
        }
        ranges = 0;
        int runs = solidRuns.place(0);
        solidRanges = ranges;
        runs = laidRuns.place(runs);
        if (runs > firsts.capacity()) {
            firsts = ints(Integer.highestOneBit(runs) * 2);
            counts = ints(Integer.highestOneBit(runs) * 2);
        }
        // A draw leaves the two lists' limits at the end of its range.
        firsts.clear();
        counts.clear();
        for (int i = 0; i < chunks.size(); i++) {
            WorldScene.ChunkMeshes build = chunks.get(i);
            // The order a build's meshes were sent in: see send.
            int at = solidRuns.list(build.solid.all, build.heldAt);
            laidRuns.list(build.laid.all, at);
        }
    }

    private static void drop(WorldScene.ChunkMeshes build) {
        if (build.heldEpoch == epoch && build.heldFloats > 0) {
            space.give(build.heldAt, build.heldFloats);
        }
        build.heldEpoch = 0;
    }

    private static int floats(WorldScene.ChunkMeshes build) {
        int floats = 0;
        for (int i = 0; i < build.solid.all.size(); i++) {
            floats += build.solid.all.get(i).floats;
        }
        for (int i = 0; i < build.laid.all.size(); i++) {
            floats += build.laid.all.get(i).floats;
        }
        return floats;
    }

    /** Hands the graphics card a build's vertices, its solid meshes and then its laid ones, each in the order the build lists them. False when the buffer has no room. */
    private static boolean send(WorldScene.ChunkMeshes build) {
        // Read before the vertices are: the logic thread raised it after writing them.
        int version = build.version;
        int floats = floats(build);
        int at = 0;
        if (floats > 0) {
            at = space.take(floats);
            if (at < 0) {
                return false;
            }
            if (floats > staging.capacity()) {
                staging = ByteBuffer.allocateDirect(Integer.highestOneBit(floats) * 2 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
            }
            staging.clear();
            for (int i = 0; i < build.solid.all.size(); i++) {
                staging.put(build.solid.all.get(i).vertices, 0, build.solid.all.get(i).floats);
            }
            for (int i = 0; i < build.laid.all.size(); i++) {
                staging.put(build.laid.all.get(i).vertices, 0, build.laid.all.get(i).floats);
            }
            staging.flip();
            GL15.glBufferSubData(GL_ARRAY_BUFFER, (long) at * Float.BYTES, staging);
            sentBuilds++;
            sentFloats += floats;
        }
        build.heldAt = at;
        build.heldFloats = floats;
        build.heldVersion = version;
        build.heldEpoch = epoch;
        return true;
    }

    /**
     * Makes the buffer anew, empty, with room for the frame and half as much again. Run when a
     * build finds no gap long enough: the buffer is too short, or what is free in it is in
     * pieces, which starting over puts right as well.
     */
    private static void remake(ArrayList<WorldScene.ChunkMeshes> chunks) {
        long shown = 0;
        for (int i = 0; i < chunks.size(); i++) {
            shown += floats(chunks.get(i));
        }
        long wanted = (shown + shown / 2 + GRAIN - 1) / GRAIN * GRAIN;
        if (wanted > Integer.MAX_VALUE / GRAIN * GRAIN) {
            throw new IllegalStateException("the frame's chunks hold " + shown + " floats, more than one buffer can");
        }
        boolean longer = wanted > capacity;
        if (longer) {
            capacity = (int) wanted;
        }
        GL15.glBufferData(GL_ARRAY_BUFFER, (long) capacity * Float.BYTES, GL_DYNAMIC_DRAW);
        epoch++;
        space.reset(capacity);
        sentBuilds = 0;
        sentFloats = 0;
        Log.info(String.format(Locale.ROOT, "first person keeps the world in %d MB on the graphics card (%s), of which this frame's %d chunks take %d",
                (long) capacity * Float.BYTES >> 20, longer ? "made longer" : "put in order", chunks.size(), shown * Float.BYTES >> 20));
    }

    /** Points OpenGL's vertex, texture coordinate, colour and normal arrays at the buffer. Leaves no buffer bound. */
    static void point() {
        GL15.glBindBuffer(GL_ARRAY_BUFFER, buffer);
        GL11.glVertexPointer(3, GL_FLOAT, STRIDE, 0L);
        GL11.glTexCoordPointer(2, GL_FLOAT, STRIDE, 3L * Float.BYTES);
        GL11.glColorPointer(4, GL_FLOAT, STRIDE, 5L * Float.BYTES);
        GL11.glNormalPointer(GL_FLOAT, STRIDE, 9L * Float.BYTES);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /** Draws every triangle of a range, in the order the frame's chunks and each chunk's meshes are listed. */
    static void draw(int range) {
        int from = rangeFrom[range], to = from + rangeRuns[range];
        firsts.clear().position(from).limit(to);
        counts.clear().position(from).limit(to);
        GL14.glMultiDrawArrays(GL_TRIANGLES, firsts, counts);
    }

    /** The runs of the buffer each texture and tint draws: how many, where its list of them starts, and where in the list the next goes. */
    private static final class Runs {
        private int[] texture = new int[512], tint = new int[512], runs = new int[512], cursor = new int[512];
        private boolean[] taken = new boolean[512];
        int used;

        void clear() {
            java.util.Arrays.fill(taken, false);
            used = 0;
        }

        private int slot(int name, int tinted) {
            int mask = texture.length - 1;
            int slot = ((name ^ tinted << 12) * 0x9E3779B1) >>> 16 & mask;
            while (taken[slot] && (texture[slot] != name || tint[slot] != tinted)) {
                slot = (slot + 1) & mask;
            }
            return slot;
        }

        void count(ArrayList<SpriteGeometry.Mesh> meshes) {
            for (int i = 0; i < meshes.size(); i++) {
                SpriteGeometry.Mesh mesh = meshes.get(i);
                if (mesh.floats == 0) {
                    continue;
                }
                if (used * 2 >= texture.length) {
                    grow();
                }
                int slot = slot(mesh.texture, mesh.tint);
                if (!taken[slot]) {
                    taken[slot] = true;
                    texture[slot] = mesh.texture;
                    tint[slot] = mesh.tint;
                    runs[slot] = 0;
                    used++;
                }
                runs[slot]++;
            }
        }

        private void grow() {
            int[] oldTexture = texture, oldTint = tint, oldRuns = runs;
            boolean[] oldTaken = taken;
            texture = new int[oldTexture.length * 2];
            tint = new int[oldTexture.length * 2];
            runs = new int[oldTexture.length * 2];
            cursor = new int[oldTexture.length * 2];
            taken = new boolean[oldTexture.length * 2];
            for (int i = 0; i < oldTexture.length; i++) {
                if (oldTaken[i]) {
                    int slot = slot(oldTexture[i], oldTint[i]);
                    taken[slot] = true;
                    texture[slot] = oldTexture[i];
                    tint[slot] = oldTint[i];
                    runs[slot] = oldRuns[i];
                }
            }
        }

        /** Gives each texture its part of the lists from the run at start, notes it as a range to draw, and returns the run after the last. */
        int place(int start) {
            for (int slot = 0; slot < texture.length; slot++) {
                if (!taken[slot]) {
                    continue;
                }
                cursor[slot] = start;
                rangeTexture[ranges] = texture[slot];
                rangeTint[ranges] = tint[slot];
                rangeFrom[ranges] = start;
                rangeRuns[ranges] = runs[slot];
                ranges++;
                start += runs[slot];
            }
            return start;
        }

        /** Lists where each mesh's vertices are, the first of them being at this float of the buffer, and returns the float after the last. */
        int list(ArrayList<SpriteGeometry.Mesh> meshes, int at) {
            for (int i = 0; i < meshes.size(); i++) {
                SpriteGeometry.Mesh mesh = meshes.get(i);
                if (mesh.floats == 0) {
                    continue;
                }
                int slot = slot(mesh.texture, mesh.tint);
                firsts.put(cursor[slot], at / SpriteGeometry.VERTEX_FLOATS);
                counts.put(cursor[slot], mesh.floats / SpriteGeometry.VERTEX_FLOATS);
                cursor[slot]++;
                at += mesh.floats;
            }
            return at;
        }
    }
}

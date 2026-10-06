package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

/**
 * The world's triangles on the graphics card.
 *
 * Most chunk builds a frame shows are the ones the frame before showed, so a build's vertices
 * are handed to the graphics card once, the first time a frame draws it, and stay there until
 * no frame shows the build any more. From then on the graphics card's is the only copy: the
 * memory the vertices came in goes back to carry the next build. All of them live in one
 * buffer, each build in a block of its own, so that every triangle wearing one texture is
 * still one draw however many chunks it is spread over: the draw is given the list of places
 * in the buffer where those triangles are, chunk after chunk in the order the frame lists its
 * chunks.
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
    private static final int GL_TEXTURE0 = 0x84C0;
    private static final int GL_ARRAY_BUFFER = 0x8892;
    private static final int GL_COPY_READ_BUFFER = 0x8F36;
    private static final int GL_COPY_WRITE_BUFFER = 0x8F37;
    private static final int GL_DYNAMIC_DRAW = 0x88E8;
    private static final int STRIDE = SpriteGeometry.VERTEX_FLOATS * Float.BYTES;
    /** The buffer is made in whole numbers of this many floats: 16 MB. */
    private static final int GRAIN = 1 << 22;

    /**
     * Builds no frame shows any more, whose blocks the render thread has yet to free: the list
     * the logic thread adds to, and the one the render thread last emptied, which it swaps for
     * the first to take what has been added. Both threads hold the lists' lock to touch either.
     */
    private static final Object forgottenLock = new Object();
    private static ArrayList<WorldScene.ChunkMeshes> forgotten = new ArrayList<>(), freed = new ArrayList<>();
    private static final BufferSpace space = new BufferSpace();
    private static int buffer;
    /** How long the buffer is, in floats. */
    private static int capacity;
    /** Every build with a block, in no order: each knows its place here. They are what is carried over when the buffer is made anew. */
    private static final ArrayList<WorldScene.ChunkMeshes> holders = new ArrayList<>();
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
        if (build.held) {
            synchronized (forgottenLock) {
                forgotten.add(build);
            }
        }
    }

    static String describe() {
        return String.format(Locale.ROOT, "card holds %.0f of %.0f MB, sent %d builds %.1f MB, %.0f MB on the way there and %.0f MB kept for the next",
                usedMegabytes, allMegabytes, sent, sentMegabytes, VertexLoans.lentBytes() / 1048576f, VertexLoans.idleBytes() / 1048576f);
    }

    /**
     * Makes ready to draw a frame's chunks: hands the graphics card the builds it does not hold
     * as they now are. Leaves no buffer bound.
     */
    static void load(ArrayList<WorldScene.ChunkMeshes> chunks) {
        if (buffer == 0) {
            buffer = GL15.glGenBuffers();
        }
        ArrayList<WorldScene.ChunkMeshes> gone;
        synchronized (forgottenLock) {
            gone = forgotten;
            forgotten = freed;
            freed = gone;
        }
        for (int i = 0; i < gone.size(); i++) {
            drop(gone.get(i));
        }
        gone.clear();
        GL15.glBindBuffer(GL_ARRAY_BUFFER, buffer);
        sentBuilds = 0;
        sentFloats = 0;
        // Every build that has been filled again gives its block back before any takes one, so
        // that a chunk's new build can go where its last one was.
        for (int i = 0; i < chunks.size(); i++) {
            WorldScene.ChunkMeshes build = chunks.get(i);
            if (build.held && build.heldVersion != build.version) {
                drop(build);
            }
        }
        for (int i = 0; i < chunks.size(); i++) {
            WorldScene.ChunkMeshes build = chunks.get(i);
            if (!build.held && !send(build)) {
                remake(chunks, i);
                if (!send(build)) {
                    throw new IllegalStateException("a buffer of " + capacity + " floats made for this frame does not hold it");
                }
            }
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        usedMegabytes = ((long) capacity - space.free()) * Float.BYTES / 1048576f;
        allMegabytes = (long) capacity * Float.BYTES / 1048576f;
        sentMegabytes = sentFloats * Float.BYTES / 1048576f;
        sent = sentBuilds;
    }

    private static void drop(WorldScene.ChunkMeshes build) {
        if (!build.held) {
            return;
        }
        if (build.heldFloats > 0) {
            space.give(build.heldAt, build.heldFloats);
        }
        WorldScene.ChunkMeshes last = holders.remove(holders.size() - 1);
        if (last != build) {
            holders.set(build.heldIndex, last);
            last.heldIndex = build.heldIndex;
        }
        build.held = false;
    }

    /**
     * Hands the graphics card a build's vertices and gives back the memory they came in. False
     * when the buffer has no room, and the build is then as it was.
     */
    private static boolean send(WorldScene.ChunkMeshes build) {
        // Read before the runs and the vertices are: the logic thread raised it after writing them.
        int version = build.version;
        int floats = build.floats;
        int at = 0;
        if (floats > 0) {
            at = space.take(floats);
            if (at < 0) {
                return false;
            }
            FloatBuffer vertices = build.vertices;
            if (vertices == null || vertices.remaining() != floats) {
                throw new IllegalStateException("a build of " + floats + " floats that the graphics card does not hold came with "
                        + (vertices == null ? "no vertices" : vertices.remaining() + " floats of them"));
            }
            GL15.glBufferSubData(GL_ARRAY_BUFFER, (long) at * Float.BYTES, vertices);
            build.vertices = null;
            VertexLoans.give(vertices);
            sentBuilds++;
            sentFloats += floats;
        }
        build.heldAt = at;
        build.heldFloats = floats;
        build.heldVersion = version;
        build.heldIndex = holders.size();
        holders.add(build);
        build.held = true;
        return true;
    }

    /**
     * Makes the buffer anew with room for what it holds and for the builds of the frame, from
     * this one on, that it does not yet hold, and half as much again, and carries every block
     * over, one after another with no gaps. Run when a build finds no gap long enough: the
     * buffer is too short, or what is free in it is in pieces, which this puts right as well.
     * The blocks are copied on the graphics card. Nothing else has them.
     */
    private static void remake(ArrayList<WorldScene.ChunkMeshes> chunks, int from) {
        long kept = 0;
        for (int i = 0; i < holders.size(); i++) {
            kept += holders.get(i).heldFloats;
        }
        long coming = 0;
        for (int i = from; i < chunks.size(); i++) {
            if (!chunks.get(i).held) {
                coming += chunks.get(i).floats;
            }
        }
        long wanted = (kept + coming + (kept + coming) / 2 + GRAIN - 1) / GRAIN * GRAIN;
        if (wanted > Integer.MAX_VALUE / GRAIN * GRAIN) {
            throw new IllegalStateException("the world in sight is " + (kept + coming) + " floats, more than one buffer can hold");
        }
        boolean longer = wanted > capacity;
        if (longer) {
            capacity = (int) wanted;
        }
        int old = buffer;
        buffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ARRAY_BUFFER, buffer);
        GL15.glBufferData(GL_ARRAY_BUFFER, (long) capacity * Float.BYTES, GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL_COPY_READ_BUFFER, old);
        GL15.glBindBuffer(GL_COPY_WRITE_BUFFER, buffer);
        int next = 0;
        for (int i = 0; i < holders.size(); i++) {
            WorldScene.ChunkMeshes build = holders.get(i);
            if (build.heldFloats > 0) {
                GL31.glCopyBufferSubData(GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, (long) build.heldAt * Float.BYTES, (long) next * Float.BYTES,
                        (long) build.heldFloats * Float.BYTES);
                build.heldAt = next;
                next += build.heldFloats;
            }
        }
        GL15.glBindBuffer(GL_COPY_READ_BUFFER, 0);
        GL15.glBindBuffer(GL_COPY_WRITE_BUFFER, 0);
        GL15.glDeleteBuffers(old);
        space.reset(capacity);
        if (next > 0 && space.take(next) != 0) {
            throw new IllegalStateException("the blocks carried over were not put at the start of the new buffer");
        }
        Log.info(String.format(Locale.ROOT, "first person keeps the world in %d MB on the graphics card (%s), of which %d chunks take %d and this frame brings %d more",
                (long) capacity * Float.BYTES >> 20, longer ? "made longer" : "put in order", holders.size(), kept * Float.BYTES >> 20, coming * Float.BYTES >> 20));
    }

    /** Points OpenGL's vertex, texture coordinate, colour and normal arrays at the buffer, and the second texture coordinate array at what a vertex carries for a card. Leaves no buffer bound. */
    static void point() {
        GL15.glBindBuffer(GL_ARRAY_BUFFER, buffer);
        GL11.glVertexPointer(3, GL_FLOAT, STRIDE, 0L);
        GL11.glTexCoordPointer(2, GL_FLOAT, STRIDE, 3L * Float.BYTES);
        GL11.glColorPointer(4, GL_FLOAT, STRIDE, 5L * Float.BYTES);
        GL11.glNormalPointer(GL_FLOAT, STRIDE, 9L * Float.BYTES);
        GL13.glClientActiveTexture(GL_TEXTURE0 + 1);
        GL11.glTexCoordPointer(3, GL_FLOAT, STRIDE, 12L * Float.BYTES);
        GL13.glClientActiveTexture(GL_TEXTURE0);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Some of a frame's chunks as draws: their triangles listed by texture and tint, what is
     * solid first and then what is laid over it. One texture in one tint is a range, drawn in
     * one call.
     */
    static final class Listing {
        /** For each range, one after the other, the first vertex and the number of vertices of each run of the buffer it draws. */
        private IntBuffer firsts = ints(1 << 12), counts = ints(1 << 12);
        private int[] rangeTexture = new int[256], rangeTint = new int[256], rangeFrom = new int[256], rangeRuns = new int[256];
        private int ranges, solidRanges;
        /** What is laid is drawn in the order listed, each over the last: what grows on the ground first, and what lies on it, a path spread over a lawn's edge, over that. */
        private final Runs solidRuns = new Runs(), grownRuns = new Runs(), laidRuns = new Runs();

        /**
         * Lists the chunks of a frame that ChunkBuffers.load has made ready, leaving out those
         * marked in left when it is not null.
         *
         * @param laidToo whether what is laid over the floors is listed, or only what is solid
         */
        void of(ArrayList<WorldScene.ChunkMeshes> chunks, boolean[] left, boolean laidToo) {
            solidRuns.clear();
            grownRuns.clear();
            laidRuns.clear();
            for (int i = 0; i < chunks.size(); i++) {
                if (left != null && left[i]) {
                    continue;
                }
                WorldScene.ChunkMeshes build = chunks.get(i);
                solidRuns.count(build, 0, build.solidRuns);
                if (laidToo) {
                    (build.grown ? grownRuns : laidRuns).count(build, build.solidRuns, build.runs);
                }
            }
            int most = solidRuns.used + grownRuns.used + laidRuns.used;
            if (most > rangeTexture.length) {
                rangeTexture = new int[most * 2];
                rangeTint = new int[most * 2];
                rangeFrom = new int[most * 2];
                rangeRuns = new int[most * 2];
            }
            ranges = 0;
            int runs = place(solidRuns, 0);
            solidRanges = ranges;
            runs = place(grownRuns, runs);
            runs = place(laidRuns, runs);
            if (runs > firsts.capacity()) {
                firsts = ints(Integer.highestOneBit(runs) * 2);
                counts = ints(Integer.highestOneBit(runs) * 2);
            }
            // A draw leaves the two lists' limits at the end of its range.
            firsts.clear();
            counts.clear();
            for (int i = 0; i < chunks.size(); i++) {
                if (left != null && left[i]) {
                    continue;
                }
                WorldScene.ChunkMeshes build = chunks.get(i);
                // The order a build's runs were sent in.
                int at = build.heldAt;
                for (int run = 0; run < build.runs; run++) {
                    Runs of = run < build.solidRuns ? solidRuns : build.grown ? grownRuns : laidRuns;
                    if (of == solidRuns || laidToo) {
                        int slot = of.slot(build.runTexture[run], build.runTint[run]);
                        firsts.put(of.cursor[slot], at / SpriteGeometry.VERTEX_FLOATS);
                        counts.put(of.cursor[slot], build.runFloats[run] / SpriteGeometry.VERTEX_FLOATS);
                        of.cursor[slot]++;
                    }
                    at += build.runFloats[run];
                }
            }
        }

        /** Gives each texture its part of the lists from the run at start, notes it as a range to draw, and returns the run after the last. */
        private int place(Runs runs, int start) {
            for (int slot = 0; slot < runs.texture.length; slot++) {
                if (!runs.taken[slot]) {
                    continue;
                }
                runs.cursor[slot] = start;
                rangeTexture[ranges] = runs.texture[slot];
                rangeTint[ranges] = runs.tint[slot];
                rangeFrom[ranges] = start;
                rangeRuns[ranges] = runs.runs[slot];
                ranges++;
                start += runs.runs[slot];
            }
            return start;
        }

        /** How many ranges there are, and how many of them, the first ones, are solid. */
        int ranges() {
            return ranges;
        }

        int solidRanges() {
            return solidRanges;
        }

        /** The OpenGL name of the texture a range's triangles wear, 0 for none. */
        int texture(int range) {
            return rangeTexture[range];
        }

        int tint(int range) {
            return rangeTint[range];
        }

        /** Draws every triangle of a range, in the order the chunks and each chunk's runs are listed. */
        void draw(int range) {
            int from = rangeFrom[range], to = from + rangeRuns[range];
            firsts.clear().position(from).limit(to);
            counts.clear().position(from).limit(to);
            GL14.glMultiDrawArrays(GL_TRIANGLES, firsts, counts);
        }
    }

    /** The runs of the buffer each texture and tint draws: how many, where its list of them starts, and where in the list the next goes. */
    private static final class Runs {
        private int[] texture = new int[512], tint = new int[512], runs = new int[512], cursor = new int[512];
        private boolean[] taken = new boolean[512];
        int used;

        void clear() {
            Arrays.fill(taken, false);
            used = 0;
        }

        int slot(int name, int tinted) {
            int mask = texture.length - 1;
            int slot = ((name ^ tinted << 12) * 0x9E3779B1) >>> 16 & mask;
            while (taken[slot] && (texture[slot] != name || tint[slot] != tinted)) {
                slot = (slot + 1) & mask;
            }
            return slot;
        }

        void count(WorldScene.ChunkMeshes build, int from, int to) {
            for (int run = from; run < to; run++) {
                if (used * 2 >= texture.length) {
                    grow();
                }
                int slot = slot(build.runTexture[run], build.runTint[run]);
                if (!taken[slot]) {
                    taken[slot] = true;
                    texture[slot] = build.runTexture[run];
                    tint[slot] = build.runTint[run];
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
    }
}

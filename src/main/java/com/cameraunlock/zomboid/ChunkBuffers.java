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
 * buffer, the builds of every player's view together, each build in a block of its own, so
 * that every triangle wearing one texture is
 * still one draw however many chunks it is spread over: the draw is given the list of places
 * in the buffer where those triangles are, chunk after chunk in the order the frame lists its
 * chunks.
 *
 * Everything that touches the buffer runs on the render thread. forget, describe and the
 * sums that hold no buffer state (kept, onScreen, nearestFirst and the like) are called on
 * the logic thread.
 *
 * What makes the hand-over between the threads safe is WorldScene's own rule: a build is filled
 * again only when no frame in flight shows it. So while a frame is being drawn, nothing it
 * shows is being written. The logic thread raises a build's version when it has filled it,
 * and the build's block is taken as its picture only while it holds that version.
 */
final class ChunkBuffers {
    /**
     * The OpenGL name of the texture a run or a range of them is drawn with, from the key its
     * mesh was made under: a key below zero is a square of rug's number, whose picture is chosen
     * as it is drawn (Rugs.texture), any other the texture's own name. Whatever binds a run's
     * texture binds what this gives: the rug tests hold every reader of a run's key to it.
     */
    static int bound(int key) {
        return key < 0 ? Rugs.texture(key) : key;
    }

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
    private static final int GL_OUT_OF_MEMORY = 0x0505;

    /**
     * Builds no frame shows any more, whose blocks the render thread has yet to free: the list
     * the logic thread adds to, and the one the render thread last emptied, which it swaps for
     * the first to take what has been added. Both threads hold the lists' lock to touch either.
     */
    private static final Object forgottenLock = new Object();
    private static ArrayList<WorldScene.ChunkMeshes> forgotten = new ArrayList<>(), freed = new ArrayList<>();
    private static final BufferSpace space = new BufferSpace();
    /** A buffer less than one part in this many in use for SLACK_FOR_NANOS is made shorter. */
    private static final int SLACK = 3;
    private static final long SLACK_FOR_NANOS = 10_000_000_000L;
    private static long slackSince, loadedNanos;
    private static int buffer;
    /** How long the buffer is, in floats. */
    private static int capacity;
    /** Every build with a block, in no order: each knows its place here. They are what is carried over when the buffer is made anew. */
    private static final ArrayList<WorldScene.ChunkMeshes> holders = new ArrayList<>();
    /** How many of them are each view's. */
    private static final int[] heldOf = new int[View.COUNT];
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
        return String.format(Locale.ROOT, "card holds %.0f of %.0f MB, sent %d builds %.1f MB, %.0f MB on the way there and %.0f MB kept for the next, %s",
                usedMegabytes, allMegabytes, sent, sentMegabytes, VertexLoans.lentBytes() / 1048576f, VertexLoans.idleBytes() / 1048576f, LightPages.describe());
    }

    /**
     * Makes ready to draw a frame's chunks: hands the graphics card the builds it does not hold
     * as they now are. Leaves no buffer bound.
     */
    static void load(ArrayList<WorldScene.ChunkMeshes> chunks, ArrayList<WorldScene.ChunkMeshes> ahead) {
        if (buffer == 0) {
            buffer = GL15.glGenBuffers();
        }
        dropForgotten();
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
        // A buffer made for a wide view is mostly empty once the view is a narrow one. Time in which no
        // frame was drawn says nothing of how full it is kept: the wait begins again after a gap.
        long now = System.nanoTime();
        boolean gap = now - loadedNanos > SLACK_FOR_NANOS / 10;
        loadedNanos = now;
        if (gap || !slack(capacity, (long) capacity - space.free())) {
            slackSince = now;
        } else if (now - slackSince > SLACK_FOR_NANOS) {
            remake(chunks, chunks.size());
            slackSince = now;
        }
        loadAhead(ahead);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        usedMegabytes = ((long) capacity - space.free()) * Float.BYTES / 1048576f;
        allMegabytes = (long) capacity * Float.BYTES / 1048576f;
        sentMegabytes = sentFloats * Float.BYTES / 1048576f;
        sent = sentBuilds;
    }

    /**
     * Hands the graphics card builds that no frame draws yet, where the buffer has room for
     * them as it is, so that the frame that first draws them has nothing to wait for and
     * their vertices are not kept in this process meanwhile. One the buffer has no room for
     * is left as it was, and goes over with the frame that draws it. Called by load, with its buffer bound.
     */
    private static void loadAhead(ArrayList<WorldScene.ChunkMeshes> builds) {
        for (int i = 0; i < builds.size(); i++) {
            WorldScene.ChunkMeshes build = builds.get(i);
            if (build.held && build.heldVersion != build.version) {
                drop(build);
            }
            if (!build.held) {
                send(build);
            }
        }
    }

    /** How long a buffer is made for this many floats in use: half as much again, in whole grains. */
    static long wanted(long used) {
        return (used + used / 2 + GRAIN - 1) / GRAIN * GRAIN;
    }

    /** Whether a buffer this long with this much in use is mostly empty: one just made for what it holds never is. */
    static boolean slack(long capacity, long used) {
        return capacity > GRAIN && used * SLACK < capacity;
    }

    /**
     * Whether a build of the chunk at (wx, wy) is kept with the eye where it is: on the loaded
     * map or a step off it, and within what the graphics mode shows and a little more.
     */
    static boolean kept(int wx, int wy, int mapWx, int mapWy, int mapSide, float eyeX, float eyeY, float sight) {
        if (wx < mapWx - 1 || wx > mapWx + mapSide || wy < mapWy - 1 || wy > mapWy + mapSide) {
            return false;
        }
        int tiles = WorldScene.CHUNK_TILES;
        float toX = wx * tiles + tiles / 2f - eyeX, toY = wy * tiles + tiles / 2f - eyeY;
        float within = drawnWithin(sight) + WorldScene.KEPT_BEYOND;
        return toX * toX + toY * toY <= within * within;
    }

    /** How far from the eye, in tiles, the middle of a chunk may be and the chunk still be built and drawn. */
    static float drawnWithin(float sight) {
        return sight + WorldScene.CHUNK_REACH;
    }

    private static int[] walk = new int[0];
    private static int walkSide;

    /**
     * Every step from one place on a square map of this many chunks a side to another, as
     * pairs of chunks across and chunks down, the shortest first. Logic thread.
     */
    static int[] nearestFirst(int side) {
        if (walkSide != side) {
            int reach = side - 1, across = 2 * reach + 1;
            long[] keys = new long[across * across];
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    keys[(dx + reach) * across + dy + reach] = (long) (dx * dx + dy * dy) << 32 | (long) (dx + reach) << 16 | dy + reach;
                }
            }
            Arrays.sort(keys);
            int[] steps = new int[keys.length * 2];
            for (int i = 0; i < keys.length; i++) {
                steps[i * 2] = (int) (keys[i] >> 16 & 0xffff) - reach;
                steps[i * 2 + 1] = (int) (keys[i] & 0xffff) - reach;
            }
            walk = steps;
            walkSide = side;
        }
        return walk;
    }

    /**
     * How far outside the screen a chunk may be and still cast a shadow into it, in tiles, with
     * the light this high (the sine of its height above the horizon): the length of the
     * shadow of what stands this many tiles tall on it, no less than 24, for what leans into
     * the picture from a floor or two up, and no more than 48, beyond which the shadow maps
     * hold little.
     */
    static float shadowReach(float lightUp, float tall) {
        float up = Math.clamp(lightUp, 0.05f, 1f);
        return Math.clamp(tall * (float) Math.sqrt(1f - up * up) / up, 24f, 48f);
    }

    /**
     * Whether any of a chunk can be on the screen in the game's own view, or within beyond
     * tiles of it. (toX, toY) is from the middle of the screen, on the player's floor, to the
     * chunk's middle. The view looks along the unit vector (alongX, alongY) on the ground and
     * shows halfAcross tiles to each side and halfAlong ahead and behind. A floor above the
     * player's is drawn levelShift tiles further along, so a chunk with floors from lowLevel
     * to highLevel, counted from the player's, is on the screen wherever one of them is.
     */
    static boolean onScreen(float toX, float toY, int lowLevel, int highLevel, float alongX, float alongY, float halfAcross, float halfAlong,
            float levelShift, float beyond) {
        float reach = beyond + WorldScene.CHUNK_REACH;
        float across = toY * alongX - toX * alongY, along = toX * alongX + toY * alongY;
        return Math.abs(across) <= halfAcross + reach && along + highLevel * levelShift >= -halfAlong - reach
                && along + lowLevel * levelShift <= halfAlong + reach;
    }

    /** Whether the chunk at (wx, wy) has a square in the box of squares from (minX, minY) up to but not (maxX, maxY), or right beside it. */
    static boolean chunkTouches(int wx, int wy, int minX, int minY, int maxX, int maxY) {
        int tiles = WorldScene.CHUNK_TILES;
        return wx * tiles < maxX + 1 && (wx + 1) * tiles > minX - 1 && wy * tiles < maxY + 1 && (wy + 1) * tiles > minY - 1;
    }

    private static void dropForgotten() {
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
    }

    /**
     * Frees the blocks of a view whose first person has been off a while, the logic thread
     * having let every build of it go (WorldScene.letGo), and gives the buffer back to the
     * graphics card once no build of any view has a block in it.
     */
    static void letGo(int view) {
        dropForgotten();
        if (buffer == 0) {
            return;
        }
        if (heldOf[view] != 0) {
            // Every build of the view was let go, so none should hold a block: one that does was lost by whatever let it go.
            Log.info("first person keeps the world's room on the graphics card: " + heldOf[view] + " builds of player " + (view + 1) + "'s view still hold a part of it");
            return;
        }
        if (!holders.isEmpty()) {
            return;
        }
        GL15.glDeleteBuffers(buffer);
        buffer = 0;
        capacity = 0;
        space.reset(0);
        LightPages.letGo();
        usedMegabytes = 0f;
        allMegabytes = 0f;
        Log.info("first person has given the world's room on the graphics card back");
    }

    private static void drop(WorldScene.ChunkMeshes build) {
        if (!build.held) {
            return;
        }
        if (build.heldFloats > 0) {
            space.give(build.heldAt, build.heldFloats);
        }
        if (build.heldPageRecords > 0) {
            LightPages.give(build.heldPageAt, build.heldPageRecords);
        }
        WorldScene.ChunkMeshes last = holders.remove(holders.size() - 1);
        if (last != build) {
            holders.set(build.heldIndex, last);
            last.heldIndex = build.heldIndex;
        }
        heldOf[build.of]--;
        build.held = false;
    }

    /**
     * Hands the graphics card a build's vertices and its light page, and gives back the memory
     * the vertices came in. False when the buffer has no room, and the build is then as it was.
     */
    private static boolean send(WorldScene.ChunkMeshes build) {
        // Read before the runs, the vertices and the page are: the logic thread raised it after writing them.
        int version = build.version;
        int floats = build.floats;
        int records = build.pageRecords;
        int at = 0, pageAt = 0;
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
            if (records > 0) {
                try {
                    pageAt = LightPages.send(build.page, records);
                } catch (RuntimeException refused) {
                    space.give(at, floats);
                    throw refused;
                }
                // A vertex counts its record from the start of its build's page.
                if (pageAt != 0) {
                    for (int record = SpriteGeometry.RECORD_AT; record < floats; record += SpriteGeometry.VERTEX_FLOATS) {
                        vertices.put(record, vertices.get(record) + pageAt);
                    }
                }
            }
            GL15.glBufferSubData(GL_ARRAY_BUFFER, (long) at * Float.BYTES, vertices);
            build.vertices = null;
            VertexLoans.give(vertices);
            sentBuilds++;
            sentFloats += floats;
        }
        build.heldAt = at;
        build.heldFloats = floats;
        build.heldPageAt = pageAt;
        build.heldPageRecords = floats > 0 ? records : 0;
        build.heldVersion = version;
        build.heldIndex = holders.size();
        holders.add(build);
        heldOf[build.of]++;
        build.held = true;
        return true;
    }

    /**
     * Makes the buffer anew with room for what it holds and for the builds of the frame, from
     * this one on, that it does not yet hold, and half as much again, and carries every block
     * over, one after another with no gaps. Run when a build finds no gap long enough: the
     * buffer is too short, or what is free in it is in pieces, which this puts right as well.
     * Run too when most of the buffer has long been empty, which makes it shorter.
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
        long wanted = wanted(kept + coming);
        if (wanted > Integer.MAX_VALUE / GRAIN * GRAIN) {
            throw new IllegalStateException("the world in sight is " + (kept + coming) + " floats, more than one buffer can hold");
        }
        String how = wanted > capacity ? "made longer" : wanted < capacity ? "made shorter" : "put in order";
        int old = buffer;
        Pipeline.leftBefore("the world's buffer");
        int made = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ARRAY_BUFFER, made);
        GL15.glBufferData(GL_ARRAY_BUFFER, DevVideoMemory.worldBufferBytes(wanted * Float.BYTES), GL_DYNAMIC_DRAW);
        int error = GL11.glGetError();
        long exact = (kept + coming + GRAIN - 1) / GRAIN * GRAIN;
        boolean needed = from < chunks.size();
        if (error == GL_OUT_OF_MEMORY && needed) {
            // Half as much again is room to grow into, and the frame can be drawn without it.
            Log.info(String.format(Locale.ROOT, "first person's world buffer of %d MB had no room on the graphics card: asked for again at %d MB, what this frame needs",
                    wanted * Float.BYTES >> 20, exact * Float.BYTES >> 20));
            wanted = exact;
            GL15.glBufferData(GL_ARRAY_BUFFER, wanted * Float.BYTES, GL_DYNAMIC_DRAW);
            error = GL11.glGetError();
        }
        if (error != 0) {
            refuse(made, old, error, wanted, needed);
            return;
        }
        int[] was = new int[holders.size()];
        GL15.glBindBuffer(GL_COPY_READ_BUFFER, old);
        GL15.glBindBuffer(GL_COPY_WRITE_BUFFER, made);
        int next = 0;
        for (int i = 0; i < holders.size(); i++) {
            WorldScene.ChunkMeshes build = holders.get(i);
            was[i] = build.heldAt;
            if (build.heldFloats > 0) {
                GL31.glCopyBufferSubData(GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, (long) build.heldAt * Float.BYTES, (long) next * Float.BYTES,
                        (long) build.heldFloats * Float.BYTES);
                build.heldAt = next;
                next += build.heldFloats;
            }
        }
        GL15.glBindBuffer(GL_COPY_READ_BUFFER, 0);
        GL15.glBindBuffer(GL_COPY_WRITE_BUFFER, 0);
        error = GL11.glGetError();
        if (error != 0) {
            // A driver that makes room only when it is first written to says so here: the old buffer still holds every block where it was.
            for (int i = 0; i < holders.size(); i++) {
                holders.get(i).heldAt = was[i];
            }
            refuse(made, old, error, wanted, needed);
            return;
        }
        capacity = (int) wanted;
        buffer = made;
        GL15.glDeleteBuffers(old);
        space.reset(capacity);
        if (next > 0 && space.take(next) != 0) {
            throw new IllegalStateException("the blocks carried over were not put at the start of the new buffer");
        }
        Log.info(String.format(Locale.ROOT, "first person keeps the world in %d MB on the graphics card (%s), of which %d chunks take %d and this frame brings %d more",
                (long) capacity * Float.BYTES >> 20, how, holders.size(), kept * Float.BYTES >> 20, coming * Float.BYTES >> 20));
    }

    /**
     * Gives back the buffer made in old's place, with old bound as it was, and throws where the
     * frame needed it: out of memory steps the graphics modes down, any other error is a fault.
     * A shorter buffer the driver had no room for leaves the longer one as it is, which holds the frame.
     */
    private static void refuse(int made, int old, int error, long floats, boolean needed) {
        GL15.glDeleteBuffers(made);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, old);
        String what = String.format(Locale.ROOT, "the world's buffer of %d MB (OpenGL error 0x%x)", floats * Float.BYTES >> 20, error);
        if (error != GL_OUT_OF_MEMORY) {
            throw new IllegalStateException(what);
        }
        if (!needed) {
            Log.info("first person keeps the world's buffer at " + ((long) capacity * Float.BYTES >> 20) + " MB: the graphics card had no room for " + what);
            return;
        }
        Pipeline.noted(error);
        throw new VideoMemory.Refused(what);
    }

    /** Points OpenGL's vertex, texture coordinate, colour and normal arrays at the buffer, the second texture coordinate array at what a vertex carries for a card, the third at its tree's square, and the fourth at its light: its record and shares, or what it has of a light the game has not given and of the day. Leaves no buffer bound. */
    static void point() {
        GL15.glBindBuffer(GL_ARRAY_BUFFER, buffer);
        GL11.glVertexPointer(3, GL_FLOAT, STRIDE, 0L);
        GL11.glTexCoordPointer(2, GL_FLOAT, STRIDE, 3L * Float.BYTES);
        GL11.glColorPointer(4, GL_FLOAT, STRIDE, 5L * Float.BYTES);
        GL11.glNormalPointer(GL_FLOAT, STRIDE, 9L * Float.BYTES);
        GL13.glClientActiveTexture(GL_TEXTURE0 + 1);
        GL11.glTexCoordPointer(3, GL_FLOAT, STRIDE, 12L * Float.BYTES);
        GL13.glClientActiveTexture(GL_TEXTURE0 + 2);
        GL11.glTexCoordPointer(2, GL_FLOAT, STRIDE, (long) SpriteGeometry.ROOT_AT * Float.BYTES);
        GL13.glClientActiveTexture(GL_TEXTURE0 + 3);
        GL11.glTexCoordPointer(3, GL_FLOAT, STRIDE, (long) SpriteGeometry.RECORD_AT * Float.BYTES);
        GL13.glClientActiveTexture(GL_TEXTURE0);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /** Dev: has LightCheck check the light of each build's block, with the world's arrays switched on. Render thread. */
    static void checkLight() {
        point();
        for (WorldScene.ChunkMeshes build : holders) {
            if (build.heldFloats > 0) {
                LightCheck.block(buffer, build.heldAt, build.heldFloats);
            }
        }
    }

    /**
     * Some of a frame's chunks as draws: their triangles listed by texture and tint, what is
     * solid first and then what is laid over it. One texture in one tint is a range, drawn in
     * one call.
     */
    static final class Listing {
        /** For each range, one after the other, the first vertex and the number of vertices of each run of the buffer it draws. */
        private IntBuffer firsts = ints(1 << 12), counts = ints(1 << 12);
        // The lists are made here and copied to the buffers whole: a direct buffer checks its
        // memory is still there on every put, and each listing puts two for every run listed.
        private int[] firstList = new int[1 << 12], countList = new int[1 << 12];
        private int[] rangeTexture = new int[256], rangeTint = new int[256], rangeFrom = new int[256], rangeRuns = new int[256];
        private int ranges, solidRanges;
        /** What is laid is drawn in the order listed, each over the last: what grows on the ground first, and what lies on it, a path spread over a lawn's edge, over that. */
        private final Runs solidRuns = new Runs(), grownRuns = new Runs(), laidRuns = new Runs();

        /**
         * Lists the chunks of a frame that ChunkBuffers.load has made ready, leaving out those
         * marked in left when it is not null.
         *
         * @param laidToo whether what is laid over the floors is listed, or only what is solid
         * @param rooms   the rooms whose contents are listed, the rest being left out, or null to list every room's: see RoomSight
         * @param storeys the lowest level of the building given whose storeys are left out, or Integer.MAX_VALUE for none: see Pipeline.storeysFrom
         */
        void of(ArrayList<WorldScene.ChunkMeshes> chunks, boolean[] left, boolean laidToo, RoomSight.Seen rooms, int storeys, long building) {
            askedOf = null;
            airOf = null;
            solidRuns.clear();
            grownRuns.clear();
            laidRuns.clear();
            for (int i = 0; i < chunks.size(); i++) {
                if (left != null && left[i]) {
                    continue;
                }
                WorldScene.ChunkMeshes build = chunks.get(i);
                solidRuns.count(build, 0, build.solidRuns, rooms, storeys, building);
                if (laidToo) {
                    (build.grown ? grownRuns : laidRuns).count(build, build.solidRuns, build.runs, rooms, storeys, building);
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
                firstList = new int[firsts.capacity()];
                countList = new int[counts.capacity()];
            }
            for (int i = 0; i < chunks.size(); i++) {
                if (left != null && left[i]) {
                    continue;
                }
                WorldScene.ChunkMeshes build = chunks.get(i);
                // The order a build's runs were sent in.
                int at = build.heldAt;
                for (int run = 0; run < build.runs; run++) {
                    Runs of = run < build.solidRuns ? solidRuns : build.grown ? grownRuns : laidRuns;
                    int slot = of == solidRuns || laidToo ? of.next() : -1;
                    if (slot >= 0) {
                        firstList[of.cursor[slot]] = at / SpriteGeometry.VERTEX_FLOATS;
                        countList[of.cursor[slot]] = build.runFloats[run] / SpriteGeometry.VERTEX_FLOATS;
                        of.cursor[slot]++;
                    }
                    at += build.runFloats[run];
                }
            }
            if (!solidRuns.allRead() || !grownRuns.allRead() || !laidRuns.allRead()) {
                throw new IllegalStateException("a listing placed other runs than it counted");
            }
            // A draw leaves the two lists' limits at the end of its range.
            firsts.clear();
            firsts.put(firstList, 0, runs);
            counts.clear();
            counts.put(countList, 0, runs);
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
            return bound(rangeTexture[range]);
        }

        int tint(int range) {
            return rangeTint[range];
        }

        /** How many runs a range draws, and the first vertex of each, in the order drawn. */
        int runsOf(int range) {
            return rangeRuns[range];
        }

        int first(int range, int run) {
            return firsts.get(rangeFrom[range] + run);
        }

        /** Draws every triangle of a range, in the order the chunks and each chunk's runs are listed. */
        void draw(int range) {
            int from = rangeFrom[range], to = from + rangeRuns[range];
            firsts.clear().position(from).limit(to);
            counts.clear().position(from).limit(to);
            GL14.glMultiDrawArrays(GL_TRIANGLES, firsts, counts);
        }
    }

    /**
     * Whether a run of the contents of a room, of what is seen from either of two regions
     * (RoomSight.pair), or of the open air is listed for a picture that sees into the rooms
     * given, or into every room. The open air is taken to be seen.
     */
    static boolean listed(long room, RoomSight.Seen rooms) {
        if (rooms == null || room == RoomSight.OUTSIDE || room == RoomSight.ANYWHERE) {
            return true;
        }
        long[] two = RoomSight.pairOf(room);
        return two == null ? rooms.contains(room) : listed(two[0], rooms) || listed(two[1], rooms);
    }

    /** Whether a piece standing in a ball and seen from a region, not a pair, is listed: the open air round it is seen where the ball is in the picture's part of it. */
    static boolean listed(long room, RoomSight.Seen rooms, float x, float y, float up, float reach) {
        if (rooms == null || room == RoomSight.ANYWHERE) {
            return true;
        }
        if (room == RoomSight.OUTSIDE) {
            return rooms.airSees(x - reach, y - reach, up - reach, x + reach, y + reach, up + reach);
        }
        return rooms.contains(room);
    }

    /** Whether a run of a build is listed: it is no storey left out, and the place it is seen from is in the picture. */
    private static boolean listed(WorldScene.ChunkMeshes build, int run, RoomSight.Seen rooms, int storeys, long building) {
        if (build.runUnless[run] != RoomSight.OUTSIDE) {
            // A window's blind stands in for its room in the eye's own picture alone: a mirror, a shadow or the overhead view has the room itself.
            return rooms != null && (build.runLevel[run] < storeys || build.runBuilding[run] != building) && !listedOnce(build.runUnless[run], rooms)
                    && airShows(build, run, rooms);
        }
        return (build.runLevel[run] < storeys || build.runBuilding[run] != building) && (rooms == null || seenFrom(build.runRoom[run], build, run, rooms)
                || build.runRoomToo[run] != build.runRoom[run] && seenFrom(build.runRoomToo[run], build, run, rooms));
    }

    /** Whether a region a run is seen from is in the picture: a room the eye sees into, or the open air where the picture shows it. */
    private static boolean seenFrom(long region, WorldScene.ChunkMeshes build, int run, RoomSight.Seen rooms) {
        if (region == RoomSight.OUTSIDE) {
            return airShows(build, run, rooms);
        }
        if (region == RoomSight.ANYWHERE) {
            return true;
        }
        return listedOnce(region, rooms);
    }

    /**
     * Whether the open air round a run may be in the picture: the box of its chunk, cut down to
     * its storey for a building's, covers some of the picture the open air is seen in. Asked
     * once a storey and once for the rest in each chunk.
     */
    private static boolean airShows(WorldScene.ChunkMeshes build, int run, RoomSight.Seen rooms) {
        if (build != airOf) {
            airOf = build;
            airAsked = airAnswers = 0L;
        }
        long building = build.runBuilding[run];
        int level = build.runLevel[run];
        int bit = building == SpriteGeometry.NO_BUILDING || level < 0 || level > 62 ? 63 : level;
        long mask = 1L << bit;
        if ((airAsked & mask) == 0L) {
            float low = build.minUp, high = build.maxUp;
            if (bit != 63) {
                // A storey's pieces stand on the land under it, which lies within a level or two of its own height.
                low = Math.max(low, (level - 2) * ViewCamera.LEVEL_HEIGHT);
                high = Math.min(high, (level + 3) * ViewCamera.LEVEL_HEIGHT);
            }
            float s = AIR_SLACK + build.turned;
            boolean shows = rooms.airSees(build.minX - s, build.minY - s, low - s, build.maxX + s, build.maxY + s, high + s);
            airAsked |= mask;
            if (shows) {
                airAnswers |= mask;
            }
        }
        return (airAnswers & mask) != 0L;
    }

    /** How far past a chunk's box what is built in it may be drawn, in tiles: a sway, a card turned to the eye. */
    private static final float AIR_SLACK = 1f;
    /** Render thread: the chunk airShows was last asked about, which of its storeys it has answered for, and how. */
    private static WorldScene.ChunkMeshes airOf;
    private static long airAsked, airAnswers;

    /**
     * The same, with the last room asked about remembered while one listing is made, which
     * forgets it as it begins (askedOf): a build's runs come room by room, and every run of every
     * chunk drawn was looked up in the rooms seen, a fifth of the render thread's own time while
     * walking.
     */
    private static boolean listedOnce(long room, RoomSight.Seen rooms) {
        if (rooms == null || room == RoomSight.OUTSIDE) {
            return true;
        }
        if (rooms != askedOf || room != askedRoom) {
            askedOf = rooms;
            askedRoom = room;
            askedListed = rooms.contains(room);
        }
        return askedListed;
    }

    /** Render thread: the rooms and the room listedOnce was last asked about, and its answer. */
    private static RoomSight.Seen askedOf;
    private static long askedRoom;
    private static boolean askedListed;

    /** The runs of the buffer each texture and tint draws: how many, where its list of them starts, and where in the list the next goes. */
    private static final class Runs {
        private int[] texture = new int[512], tint = new int[512], runs = new int[512], cursor = new int[512];
        private boolean[] taken = new boolean[512];
        int used;
        /**
         * The slot of each run count was given, in the order given, or -1 for one not listed:
         * the listing goes over the same runs again in the same order to place them (next), and
         * asked again, whether a run is listed and where its texture is were a third of the
         * render thread's own time.
         */
        private int[] counted = new int[1 << 12];
        private int countedCount, readAt;

        void clear() {
            Arrays.fill(taken, false);
            used = 0;
            countedCount = readAt = 0;
        }

        boolean allRead() {
            return readAt == countedCount;
        }

        /** The slot count found for the next run, as it found them. */
        int next() {
            return counted[readAt++];
        }

        int slot(int name, int tinted) {
            int mask = texture.length - 1;
            int slot = ((name ^ tinted << 12) * 0x9E3779B1) >>> 16 & mask;
            while (taken[slot] && (texture[slot] != name || tint[slot] != tinted)) {
                slot = (slot + 1) & mask;
            }
            return slot;
        }

        void count(WorldScene.ChunkMeshes build, int from, int to, RoomSight.Seen rooms, int storeys, long building) {
            if (counted.length < countedCount + to - from) {
                counted = Arrays.copyOf(counted, Math.max(counted.length * 2, countedCount + to - from));
            }
            for (int run = from; run < to; run++) {
                if (!listed(build, run, rooms, storeys, building)) {
                    counted[countedCount++] = -1;
                    continue;
                }
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
                counted[countedCount++] = slot;
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
            int[] moved = new int[oldTexture.length];
            for (int i = 0; i < oldTexture.length; i++) {
                if (oldTaken[i]) {
                    int slot = slot(oldTexture[i], oldTint[i]);
                    taken[slot] = true;
                    texture[slot] = oldTexture[i];
                    tint[slot] = oldTint[i];
                    runs[slot] = oldRuns[i];
                    moved[i] = slot;
                }
            }
            for (int i = 0; i < countedCount; i++) {
                if (counted[i] >= 0) {
                    counted[i] = moved[counted[i]];
                }
            }
        }
    }
}

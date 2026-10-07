package com.cameraunlock.zomboid;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/** Tests of the room kept for each chunk's triangles in the one buffer the graphics card holds them in. */
final class BufferTests {
    private BufferTests() {}

    static void run() {
        blocksAreTakenFromTheFirstGapThatHoldsThem();
        blocksGivenBackJoinTheirNeighbours();
        aBlockThatWasNotTakenIsRefused();
        blocksComingAndGoingNeverOverlap();
        aSetListsWhatWasAskedForInTheOrderItWasAsked();
        aTrimmedSetLetsGoOfWhatIsNoLongerAskedFor();
        aBuildIsFilledWithItsRunsOneAfterAnother();
        aBuildFilledAgainGivesBackWhatNoFrameTook();
        loansGoRoundAndTheIdleOnesAreCapped();
        aBuildKnowsTheBoxItsVerticesAreIn();
        aBoxIsLeftOutOfAMapOnlyWhenNoneOfItIsInIt();
        aBufferJustMadeIsNeverMostlyEmpty();
        everyChunkThatCanBeDrawnIsKept();
        aPictureHalvedIsTheMeanOfItsDotsInFours();
        memoryWorkedOutAheadIsFreedWhenTheWorkIsForgottenAndOnlyThen();
        chunksAreWalkedNearestFirstAndEachOnce();
        onlyChunksUnderOrBesideABoxTouchIt();
        noChunkOnTheScreenIsLeftOut();
        aLowLightReachesFurtherForItsShadows();
    }

    /** The game's own view: looking north-west along the ground, a floor up drawn 4.24 tiles further along. */
    private static void noChunkOnTheScreenIsLeftOut() {
        float alongX = -0.70710678f, alongY = -0.70710678f, shift = 4.2426f;
        java.util.Random random = new java.util.Random(7);
        int kept = 0, seen = 0;
        for (int trial = 0; trial < 200_000; trial++) {
            float halfAcross = 5f + random.nextFloat() * 70f, halfAlong = 5f + random.nextFloat() * 40f, beyond = 24f + random.nextFloat() * 24f;
            float toX = (random.nextFloat() - 0.5f) * 400f, toY = (random.nextFloat() - 0.5f) * 400f;
            int low = random.nextInt(5) - 2, high = low + random.nextInt(8);
            boolean on = false;
            for (int level = low; level <= high && !on; level++) {
                for (int corner = 0; corner < 4 && !on; corner++) {
                    float x = toX + (corner % 2 == 0 ? -4f : 4f), y = toY + (corner < 2 ? -4f : 4f);
                    float across = y * alongX - x * alongY, along = x * alongX + y * alongY + level * shift;
                    on = Math.abs(across) <= halfAcross + beyond && Math.abs(along) <= halfAlong + beyond;
                }
            }
            boolean keeps = ChunkBuffers.onScreen(toX, toY, low, high, alongX, alongY, halfAcross, halfAlong, shift, beyond);
            check(keeps || !on, "a chunk with a corner on the screen, or within what casts onto it, at any of its floors is built");
            kept += keeps ? 1 : 0;
            seen += on ? 1 : 0;
        }
        check(seen > 1000 && kept < seen * 3 / 2, "and not many that have none are: " + kept + " kept for " + seen + " seen");
    }

    private static void aLowLightReachesFurtherForItsShadows() {
        check(ChunkBuffers.shadowReach(1f, 7f) == 24f && ChunkBuffers.shadowReach(0.5f, 7f) == 24f, "under a high sun what is just off the screen is enough");
        float low = ChunkBuffers.shadowReach(0.2f, 7f);
        check(low > 30f && low < 40f, "a house under a low sun casts some 34 tiles: " + low);
        check(ChunkBuffers.shadowReach(0.3f, 10f) > ChunkBuffers.shadowReach(0.3f, 7f), "a taller building casts further");
        check(ChunkBuffers.shadowReach(0.01f, 7f) == 48f && ChunkBuffers.shadowReach(-0.2f, 20f) == 48f, "and no light reaches further than the shadow maps hold");
    }

    private static void onlyChunksUnderOrBesideABoxTouchIt() {
        // A building on squares 100 to 111 by 205 to 207: chunks are 8 squares a side.
        int minX = 100, minY = 205, maxX = 112, maxY = 208;
        for (int wx = 10; wx <= 15; wx++) {
            for (int wy = 23; wy <= 28; wy++) {
                boolean touches = ChunkBuffers.chunkTouches(wx, wy, minX, minY, maxX, maxY);
                boolean under = wx * 8 <= 112 && wx * 8 + 7 >= 99 && wy * 8 <= 208 && wy * 8 + 7 >= 204;
                check(touches == under, "a chunk touches a box when one of its squares is in it or a square from it: chunk " + wx + ", " + wy);
            }
        }
        check(ChunkBuffers.chunkTouches(12, 25, minX, minY, maxX, maxY) && !ChunkBuffers.chunkTouches(15, 25, minX, minY, maxX, maxY)
                && !ChunkBuffers.chunkTouches(12, 28, minX, minY, maxX, maxY), "the chunks under it do, and those two chunks off do not");
    }

    private static void chunksAreWalkedNearestFirstAndEachOnce() {
        int side = 19;
        int[] walk = ChunkBuffers.nearestFirst(side);
        check(walk.length == 37 * 37 * 2 && walk[0] == 0 && walk[1] == 0, "the walk begins where the eye is and has every step there is on the map");
        for (int fromX : new int[] {0, 9, 18}) {
            for (int fromY : new int[] {0, 4, 18}) {
                boolean[] reached = new boolean[side * side];
                int last = 0, count = 0;
                for (int step = 0; step < walk.length; step += 2) {
                    int x = fromX + walk[step], y = fromY + walk[step + 1];
                    int away = walk[step] * walk[step] + walk[step + 1] * walk[step + 1];
                    check(away >= last, "no step is shorter than the one before it");
                    last = away;
                    if (x >= 0 && y >= 0 && x < side && y < side) {
                        check(!reached[x * side + y], "no chunk is come to twice");
                        reached[x * side + y] = true;
                        count++;
                    }
                }
                check(count == side * side, "and from anywhere on the map every chunk is come to");
            }
        }
        check(ChunkBuffers.nearestFirst(side) == walk, "the order is worked out once for a map of one size");
    }

    private static void aPictureHalvedIsTheMeanOfItsDotsInFours() {
        int wide = 5, high = 4;
        OffHeap.Block whole = OffHeap.take(wide * high * 4);
        for (int y = 0; y < high; y++) {
            for (int x = 0; x < wide; x++) {
                for (int part = 0; part < 4; part++) {
                    whole.bytes.put((y * wide + x) * 4 + part, (byte) (x * 40 + y * 10 + part * 3 + (x == 1 && y == 0 ? 130 : 0)));
                }
            }
        }
        OffHeap.Block half = Pictures.halved(whole.bytes, wide, high);
        check(half.bytes.capacity() == 2 * 2 * 4, "half as wide and half as high, an odd last column left out");
        for (int part = 0; part < 4; part++) {
            int sum = part * 3 + (40 + part * 3 + 130) + (10 + part * 3) + (50 + part * 3);
            check((half.bytes.get(part) & 0xff) == (sum + 2) / 4, "each part of a dot is the mean of the four it stands for, bytes above 127 counted as they are");
            check((half.bytes.get((1 * 2 + 1) * 4 + part) & 0xff) == 125 + part * 3, "and the last dot is the mean of the last four whole columns and rows");
        }
        whole.free();
        half.free();
    }

    /** A block freed is told so by its second free being refused: OffHeap.Block.free throws on it. */
    private static boolean freed(OffHeap.Block block) {
        try {
            block.free();
            return false;
        } catch (IllegalStateException twice) {
            return true;
        }
    }

    private static void memoryWorkedOutAheadIsFreedWhenTheWorkIsForgottenAndOnlyThen() {
        try {
            OffHeap.Block[] made = new OffHeap.Block[3];
            Ahead.begin("test done then forgotten", () -> made[0] = OffHeap.take(64));
            while (!Ahead.done("test done then forgotten")) {
                Thread.sleep(1);
            }
            Ahead.forget("test done then forgotten");
            check(freed(made[0]), "work that had ended when it was forgotten has its memory freed by the forgetting");

            java.util.concurrent.CountDownLatch begun = new java.util.concurrent.CountDownLatch(1), forgotten = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch ended = new java.util.concurrent.CountDownLatch(1);
            Ahead.begin("test forgotten while under way", () -> {
                begun.countDown();
                forgotten.await();
                made[1] = OffHeap.take(64);
                // The thread that does the work frees it after this returns: the test waits a moment for that.
                ended.countDown();
                return made[1];
            });
            begun.await();
            Ahead.forget("test forgotten while under way");
            forgotten.countDown();
            ended.await();
            boolean gone = false;
            for (int tries = 0; tries < 2000 && !gone; tries++) {
                Thread.sleep(1);
                try {
                    // Read, not freed: a free here would race the worker's own.
                    java.lang.reflect.Field flag = OffHeap.Block.class.getDeclaredField("freed");
                    flag.setAccessible(true);
                    gone = flag.getBoolean(made[1]);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
            check(gone, "work forgotten while a thread was on it has its memory freed by that thread when it ends");

            Ahead.begin("test taken", () -> OffHeap.take(64));
            made[2] = Ahead.take("test taken", () -> OffHeap.take(64));
            Ahead.forget("test taken");
            check(!freed(made[2]), "work that was taken is the taker's to free, and forgetting its name afterwards frees nothing");
        } catch (InterruptedException | java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void aBufferJustMadeIsNeverMostlyEmpty() {
        check(ChunkBuffers.wanted(0) == 0, "nothing in use wants no buffer");
        long grain = ChunkBuffers.wanted(1);
        for (long used = 1; used < 40 * grain; used += grain / 7) {
            long made = ChunkBuffers.wanted(used);
            check(made >= used + used / 2 && made % grain == 0, "a buffer is made half as long again as what it holds, in whole grains");
            check(!ChunkBuffers.slack(made, used), "and is not mostly empty the moment it is made, or it would be made again at once");
            check(made <= grain || ChunkBuffers.slack(made, used / 3) || used < 3, "with a third of that in use it is, unless it is one grain");
        }
        check(!ChunkBuffers.slack(grain, 0), "a buffer of one grain is as short as one gets");
    }

    private static void everyChunkThatCanBeDrawnIsKept() {
        int mapWx = 1300, mapWy = 1200, side = 19;
        float eyeX = (mapWx + 9) * 8 + 3.5f, eyeY = (mapWy + 9) * 8 + 4.5f;
        for (float sight : new float[] {48f, 64f, 80f}) {
            int kept = 0, drawn = 0;
            for (int wx = mapWx - 3; wx <= mapWx + side + 3; wx++) {
                for (int wy = mapWy - 3; wy <= mapWy + side + 3; wy++) {
                    double away = Math.hypot(wx * 8 + 4 - eyeX, wy * 8 + 4 - eyeY);
                    boolean onMap = wx >= mapWx && wx < mapWx + side && wy >= mapWy && wy < mapWy + side;
                    boolean keeps = ChunkBuffers.kept(wx, wy, mapWx, mapWy, side, eyeX, eyeY, sight);
                    if (onMap && away <= ChunkBuffers.drawnWithin(sight)) {
                        drawn++;
                        check(keeps, "a chunk near enough to be drawn is kept");
                    }
                    check(!keeps || away <= ChunkBuffers.drawnWithin(sight) + 8.5, "and none more than a chunk beyond that");
                    check(!keeps || wx >= mapWx - 1 && wx <= mapWx + side && wy >= mapWy - 1 && wy <= mapWy + side, "nor one further off the map than a step");
                    kept += keeps ? 1 : 0;
                }
            }
            check(sight > 50f || kept < side * side * 2 / 3, "the Low mode keeps well under the whole map: " + kept + " of " + side * side);
            check(drawn > 0 && kept >= drawn, "and what is kept is at least what is drawn");
        }
    }

    private static void aBuildKnowsTheBoxItsVerticesAreIn() {
        SpriteGeometry.MeshSet solid = new SpriteGeometry.MeshSet(), laid = new SpriteGeometry.MeshSet();
        solid.get(1).add(3f, -2f, 0.5f, 0f, 0f, 1f, 1f, 1f);
        solid.get(2).add(7f, 4f, -1f, 0f, 0f, 1f, 1f, 1f);
        laid.get(1).add(5f, 9f, 6f, 0f, 0f, 1f, 1f, 1f);
        WorldScene.ChunkMeshes build = new WorldScene.ChunkMeshes();
        build.fill(solid, laid);
        check(build.minX == 3f && build.maxX == 7f && build.minY == -2f && build.maxY == 9f && build.minUp == -1f && build.maxUp == 6f,
                "the box runs from the least to the greatest of every run's vertices");
        build.scrap();
    }

    /** Whether a point is in what a matrix puts in a picture: inside all six sides of clip space. */
    private static boolean pictured(float[] m, double x, double y, double up) {
        double cx = m[0] * x + m[4] * y + m[8] * up + m[12], cy = m[1] * x + m[5] * y + m[9] * up + m[13];
        double cz = m[2] * x + m[6] * y + m[10] * up + m[14], cw = m[3] * x + m[7] * y + m[11] * up + m[15];
        return Math.abs(cx) <= cw && Math.abs(cy) <= cw && Math.abs(cz) <= cw;
    }

    private static void aBoxIsLeftOutOfAMapOnlyWhenNoneOfItIsInIt() {
        // Straight down on a square 20 either side of (100, 200), from 40 above height 1.5 to 40 below it.
        float[] down = new float[16];
        down[0] = 1f / 20f;
        down[12] = -100f / 20f;
        down[5] = 1f / 20f;
        down[13] = -200f / 20f;
        down[10] = -1f / 40f;
        down[14] = 1.5f / 40f;
        down[15] = 1f;
        MapBounds map = new MapBounds();
        map.of(down);
        check(!map.outside(100f, 200f, 0f, 4f, 4f, 1f), "a box in the middle of the map is in it");
        check(map.outside(121f, 200f, 0f, 0.5f, 0.5f, 1f), "a box past the map's edge is not");
        check(!map.outside(121f, 200f, 0f, 1.5f, 0.5f, 1f), "one that reaches back over the edge is");
        check(!map.outside(120.5f, 200f, 0f, 0.48f, 0.5f, 1f), "nor is one left out for being a hair outside");
        check(map.outside(100f, 200f, 50f, 4f, 4f, 1f) && map.outside(100f, 200f, -50f, 4f, 4f, 1f), "a box above or below all the map sees is not in it");

        // A view with perspective, as the headlights' map has, looking along +x from (10, 10, 1).
        ViewCamera.Pose lamp = new ViewCamera.Pose();
        ViewCamera.clean(lamp, 10f, 10f, 1f, 0f, 0f);
        float[] seen = ViewCamera.view(lamp), cast = ViewCamera.projection(0.84f, 1f, 0.35f, 32f), beam = new float[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                for (int k = 0; k < 4; k++) {
                    beam[column * 4 + row] += cast[k * 4 + row] * seen[column * 4 + k];
                }
            }
        }
        map.of(beam);
        check(!map.outside(20f, 10f, 1f, 1f, 1f, 1f) && map.outside(0f, 10f, 1f, 1f, 1f, 1f) && map.outside(60f, 10f, 1f, 1f, 1f, 1f),
                "what is ahead of the lamps is in their map, what is behind them or past their reach is not");

        // Whatever is left out has no point in the picture: its corners, its middle and points all through it.
        Random random = new Random(11);
        int leftOut = 0;
        for (float[] matrix : new float[][] {down, beam}) {
            map.of(matrix);
            boolean straightDown = matrix == down;
            for (int box = 0; box < 20000; box++) {
                float x = (straightDown ? 100f : 20f) + (random.nextFloat() - 0.5f) * 90f, y = (straightDown ? 200f : 10f) + (random.nextFloat() - 0.5f) * 90f;
                float up = (random.nextFloat() - 0.5f) * 120f;
                float halfX = random.nextFloat() * 6f, halfY = random.nextFloat() * 6f, halfUp = random.nextFloat() * 6f;
                if (!map.outside(x, y, up, halfX, halfY, halfUp)) {
                    continue;
                }
                leftOut++;
                for (int point = 0; point < 40; point++) {
                    double a = point < 8 ? (point & 1) * 2 - 1 : random.nextDouble() * 2 - 1, b = point < 8 ? (point >> 1 & 1) * 2 - 1 : random.nextDouble() * 2 - 1;
                    double c = point < 8 ? (point >> 2 & 1) * 2 - 1 : random.nextDouble() * 2 - 1;
                    check(!pictured(matrix, x + a * halfX, y + b * halfY, up + c * halfUp), "a box left out of a map has no point in it");
                }
            }
        }
        check(leftOut > 10000, "and most boxes scattered that widely are left out");
    }

    private static SpriteGeometry.Mesh triangles(SpriteGeometry.Mesh mesh, int count, float mark) {
        for (int i = 0; i < count * 3; i++) {
            mesh.add(mark, i, 0f, 0f, 0f, 1f, 1f, 1f);
        }
        return mesh;
    }

    private static void aSetListsWhatWasAskedForInTheOrderItWasAsked() {
        SpriteGeometry.MeshSet set = new SpriteGeometry.MeshSet();
        SpriteGeometry.Mesh first = set.get(5), second = set.get(9, 0x77), third = set.get(2);
        set.get(9, 0x77);
        check(set.used.size() == 3 && set.used.get(0) == first && set.used.get(1) == second && set.used.get(2) == third, "each mesh once, in the order first asked for");
        triangles(first, 4, 1f);
        set.clear();
        check(set.used.isEmpty() && first.floats == 0, "an emptied set has nothing in use");
        check(set.get(2) == third && set.get(5) == first && set.used.get(0) == third && set.used.get(1) == first,
                "the same meshes serve the next filling, listed in its own order");
    }

    private static void aTrimmedSetLetsGoOfWhatIsNoLongerAskedFor() {
        SpriteGeometry.MeshSet set = new SpriteGeometry.MeshSet();
        for (int texture = 1; texture <= 300; texture++) {
            set.get(texture);
        }
        SpriteGeometry.Mesh big = triangles(set.get(7), 8000, 1f);
        int grown = big.vertices.length;
        set.clear();
        set.trim();
        check(set.kept() == 300 && big.vertices.length == grown, "what the last fillings used is kept as it is");
        for (int filling = 0; filling < 3; filling++) {
            triangles(set.get(7), 10, 1f);
            set.get(8);
            set.clear();
        }
        set.trim();
        check(set.kept() == 2 && set.get(7) == big && big.vertices.length < grown / 4, "meshes not asked for are let go, and room many times what was used");
        check(big.vertices.length >= 10 * 3 * SpriteGeometry.VERTEX_FLOATS, "with room left for what was used");
        set.clear();
        for (int texture = 1; texture <= 300; texture++) {
            check(set.get(texture).floats == 0, "a mesh asked for again is found or made empty");
        }
        check(set.kept() == 300, "one mesh a texture after the table was made anew");
        boolean refused = false;
        try {
            set.trim();
        } catch (IllegalStateException expected) {
            refused = true;
        }
        check(refused, "a set in use is not trimmed");
    }

    private static void aBuildIsFilledWithItsRunsOneAfterAnother() {
        SpriteGeometry.MeshSet solid = new SpriteGeometry.MeshSet(), laid = new SpriteGeometry.MeshSet();
        triangles(solid.get(4), 2, 1f);
        solid.get(6);
        triangles(solid.get(3, 0x21), 5, 2f);
        triangles(laid.get(4), 1, 3f);
        WorldScene.ChunkMeshes build = new WorldScene.ChunkMeshes();
        int before = build.version;
        build.fill(solid, laid);
        int vertex = SpriteGeometry.VERTEX_FLOATS;
        check(build.version == before + 1, "a filling raises the version");
        check(build.runs == 3 && build.solidRuns == 2, "an empty mesh is no run");
        check(build.runTexture[0] == 4 && build.runTexture[1] == 3 && build.runTint[1] == 0x21 && build.runTexture[2] == 4, "the runs are the meshes in the order asked for, solid first");
        check(build.runFloats[0] == 6 * vertex && build.runFloats[1] == 15 * vertex && build.runFloats[2] == 3 * vertex, "each as long as its mesh");
        check(build.floats == 24 * vertex && build.triangles() == 8, "and the build as long as all of them");
        FloatBuffer vertices = build.vertices;
        check(vertices.position() == 0 && vertices.remaining() == build.floats, "the vertices are ready to be read from the start");
        check(vertices.get(0) == 1f && vertices.get(6 * vertex) == 2f && vertices.get(21 * vertex) == 3f && vertices.get(21 * vertex + 1) == 0f
                && vertices.get(23 * vertex + 1) == 2f, "each run's vertices where the runs before it end");
        check(solid.used.size() == 3 && solid.get(4).floats == 6 * vertex, "the sets are left as they were");
        build.scrap();
        check(build.vertices == null, "a scrapped build has given its vertices back");

        solid.clear();
        laid.clear();
        build.fill(solid, laid);
        check(build.runs == 0 && build.floats == 0 && build.vertices == null, "a build of nothing has no vertices");
    }

    private static void aBuildFilledAgainGivesBackWhatNoFrameTook() {
        SpriteGeometry.MeshSet solid = new SpriteGeometry.MeshSet(), laid = new SpriteGeometry.MeshSet();
        triangles(solid.get(1), 400, 1f);
        long lent = VertexLoans.lentBytes();
        WorldScene.ChunkMeshes build = new WorldScene.ChunkMeshes();
        build.fill(solid, laid);
        long one = VertexLoans.lentBytes() - lent;
        check(one >= build.floats * 4L && one < build.floats * 8L, "a build borrows room for its vertices and less than twice that");
        build.fill(solid, laid);
        check(VertexLoans.lentBytes() - lent == one, "filled again with no frame between, it holds one loan and not two");
        build.scrap();
        check(VertexLoans.lentBytes() == lent, "and none once scrapped");
    }

    private static void loansGoRoundAndTheIdleOnesAreCapped() {
        FloatBuffer first = VertexLoans.take(5000);
        check(first.capacity() == 8192 && first.position() == 0 && first.limit() == 8192 && first.isDirect(), "a loan is a power of two long, empty and outside the heap");
        first.put(1f).flip();
        VertexLoans.give(first);
        FloatBuffer again = VertexLoans.take(8192);
        check(again == first && again.position() == 0 && again.limit() == 8192, "what was given back is lent again, empty");
        VertexLoans.give(again);
        FloatBuffer shortest = VertexLoans.take(100);
        check(shortest.capacity() == 4096, "no loan is shorter than the shortest");
        VertexLoans.give(shortest);
        boolean twice = false;
        try {
            VertexLoans.give(shortest);
        } catch (IllegalArgumentException expected) {
            twice = true;
        }
        check(twice, "a buffer given back twice is refused: lent to two takers, it would be freed under one of them");

        // A burst of nine at once, then a steady two at a time: once the burst's are seen to
        // lie idle, all are let go but the two the steady pace keeps taking.
        long clock = System.nanoTime(), wait = VertexLoans.IDLE_FOR_NANOS;
        VertexLoans.trim(clock += wait);
        VertexLoans.trim(clock += wait);
        check(VertexLoans.idleBytes() == 0, "what lies idle from one trim to the next is let go");
        int each = 1 << 16;
        long bytes = each * 4L;
        ArrayList<FloatBuffer> burst = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            burst.add(VertexLoans.take(each));
        }
        for (FloatBuffer loan : burst) {
            VertexLoans.give(loan);
        }
        VertexLoans.trim(clock += wait);
        check(VertexLoans.idleBytes() == 9 * bytes, "what came back since the last trim is kept for now");
        // A view standing still builds in bursts with seconds between them, and asks to trim far oftener than buffers are let go.
        for (int burstNumber = 0; burstNumber < 5; burstNumber++) {
            ArrayList<FloatBuffer> again9 = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                again9.add(VertexLoans.take(each));
            }
            for (FloatBuffer loan : again9) {
                check(same(burst, loan), "a burst takes the buffers the burst before it gave back");
                VertexLoans.give(loan);
            }
            VertexLoans.trim(clock += wait / 10);
            VertexLoans.trim(clock += wait / 10);
            check(VertexLoans.idleBytes() == 9 * bytes, "and between two bursts nothing is let go");
        }
        for (int frame = 0; frame < 5; frame++) {
            FloatBuffer one = VertexLoans.take(each), other = VertexLoans.take(each - 100);
            check(same(burst, one) && same(burst, other), "a steady pace takes what the burst left");
            VertexLoans.give(one);
            VertexLoans.give(other);
        }
        VertexLoans.trim(clock += wait);
        check(VertexLoans.idleBytes() == 2 * bytes, "the seven no frame took are let go, the two in use kept");
        int gone = 0;
        for (FloatBuffer loan : burst) {
            try {
                VertexLoans.give(loan);
            } catch (IllegalArgumentException expected) {
                gone++;
            }
        }
        check(gone == 9, "none of the nine can be given back again: seven are freed and two are not out on loan");
        FloatBuffer one = VertexLoans.take(each), other = VertexLoans.take(each);
        check(same(burst, one) && same(burst, other), "and they still go round");
        VertexLoans.give(one);
        VertexLoans.give(other);
        boolean refused = false;
        try {
            VertexLoans.give(FloatBuffer.allocate(1000));
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a buffer that was never lent is refused");
        long lentNow = VertexLoans.lentBytes();
        FloatBuffer out = VertexLoans.take(each);
        VertexLoans.letGoIdle();
        check(VertexLoans.idleBytes() == 0 && VertexLoans.lentBytes() == lentNow + bytes, "with the view off every idle buffer is let go and one on loan is not");
        out.put(0, 3f);
        VertexLoans.give(out);
        check(same(burst, out) || out.get(0) == 3f, "and the one on loan is still whole");
        VertexLoans.letGoIdle();
    }

    /** Whether the very buffer is in the list: two buffers are equal when their floats are, and a buffer let go has none to read. */
    private static boolean same(ArrayList<FloatBuffer> list, FloatBuffer buffer) {
        for (FloatBuffer each : list) {
            if (each == buffer) {
                return true;
            }
        }
        return false;
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static void blocksAreTakenFromTheFirstGapThatHoldsThem() {
        BufferSpace space = new BufferSpace();
        check(space.take(12) == -1, "a buffer never made has no room");
        space.reset(100);
        check(space.take(40) == 0 && space.take(30) == 40 && space.take(30) == 70, "blocks follow one another");
        check(space.free() == 0 && space.take(1) == -1, "and a full buffer has no room");
        space.give(40, 30);
        check(space.take(31) == -1, "a gap too short is passed over");
        check(space.take(10) == 40 && space.take(20) == 50, "and a block goes at the start of the first that is not");
    }

    private static void blocksGivenBackJoinTheirNeighbours() {
        BufferSpace space = new BufferSpace();
        space.reset(90);
        space.take(30);
        space.take(30);
        space.take(30);
        space.give(0, 30);
        space.give(60, 30);
        check(space.take(60) == -1, "two gaps apart are not one");
        space.give(30, 30);
        check(space.free() == 90 && space.take(90) == 0, "the block between them makes the three one");
        space.give(0, 90);
        space.take(30);
        space.take(30);
        space.give(30, 30);
        check(space.take(60) == 30, "a block joins the gap after it");
        space.give(30, 60);
        space.give(0, 30);
        check(space.take(90) == 0, "and the gap before it");
    }

    private static void aBlockThatWasNotTakenIsRefused() {
        BufferSpace space = new BufferSpace();
        space.reset(60);
        space.take(60);
        space.give(12, 24);
        boolean refused = false;
        try {
            space.give(24, 24);
        } catch (IllegalStateException expected) {
            refused = true;
        }
        check(refused, "a block that lies over a gap was never taken");
    }

    private static void blocksComingAndGoingNeverOverlap() {
        int capacity = 1 << 16;
        BufferSpace space = new BufferSpace();
        space.reset(capacity);
        boolean[] used = new boolean[capacity];
        ArrayList<int[]> held = new ArrayList<>();
        Random random = new Random(7);
        int inUse = 0;
        for (int step = 0; step < 20000; step++) {
            if (!held.isEmpty() && random.nextInt(100) < 48) {
                int[] block = held.remove(random.nextInt(held.size()));
                space.give(block[0], block[1]);
                Arrays.fill(used, block[0], block[0] + block[1], false);
                inUse -= block[1];
            } else {
                int size = 12 * (1 + random.nextInt(300));
                int at = space.take(size);
                if (at < 0) {
                    continue;
                }
                check(at + size <= capacity, "a block lies inside the buffer");
                for (int i = at; i < at + size; i++) {
                    check(!used[i], "a block is given to one holder at a time");
                    used[i] = true;
                }
                held.add(new int[] {at, size});
                inUse += size;
            }
            check(space.free() == capacity - inUse, "what is free is what is not held");
        }
        for (int[] block : held) {
            space.give(block[0], block[1]);
        }
        check(space.take(capacity) == 0, "with everything given back the buffer is one gap again");
    }
}

package com.cameraunlock.zomboid;

/** Tests of the crown of sprigs a tree or a bush is given in place of its sprite's painted leaves. */
final class CrownTests {
    private static final int WIDE = 128, HIGH = 256;
    private static final float FOOT_U = 64f, FOOT_V = 240f;

    private CrownTests() {}

    static void run() {
        leavesAreFoundOnlyWhereTheSpritePaintsThem();
        crownStandsWhereTheSpriteShowsItAndEverySprigSaysWhereItsColourIs();
        crownFurtherOffIsFewerSprigs();
        spriteWithNextToNothingPaintedHasNoCrown();
        sprigIsChosenByTheSpeciesInTheName();
        crownsWrittenDownAreMadeAsTheBuildWouldHaveMadeThem();
        crownsMadeFromTheSameAreKnownForTheSame();
        crownsOfAChunkInAnotherBandAreMadeFromWhatWasWrittenDown();
        crownsMadeOnTheirThreadAreTheSameCrowns();
        spriteMeasuredAsNoCrownMakesNone();
    }

    /** A round crown painted in the upper half of the sprite, over a bare stick of trunk that is no part of it. */
    private static boolean leaf(int u, int v) {
        float du = u - FOOT_U, dv = v - 80f;
        return du * du + dv * dv < 50f * 50f;
    }

    private static SpriteGeometry.Frame frame() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 1f;
        frame.left = 0f;
        frame.top = 0f;
        frame.width = WIDE;
        frame.height = HIGH;
        frame.uStart = 0.25f;
        frame.uEnd = 0.5f;
        frame.vStart = 0.5f;
        frame.vEnd = 1f;
        frame.open = 1f;
        frame.light(0.8f, 0.8f, 0.8f);
        return frame;
    }

    private static void leavesAreFoundOnlyWhereTheSpritePaintsThem() {
        float[] places = Flora.leafPlaces(CrownTests::leaf, frame());
        check(places.length >= 2 * 40, "a crown that fills a quarter of its sprite is found at dozens of places, not " + places.length / 2);
        for (int i = 0; i < places.length; i += 2) {
            check(leaf((int) places[i], (int) places[i + 1]), "every place found is one the sprite paints");
        }
    }

    private static void crownStandsWhereTheSpriteShowsItAndEverySprigSaysWhereItsColourIs() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces(CrownTests::leaf, frame);
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        check(Flora.addCrown(mesh, new SpriteGeometry.Mesh(), frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.MAPLE, 7), "a painted crown makes a crown");
        int vertices = mesh.floats / SpriteGeometry.VERTEX_FLOATS;
        int sprigs = sprigVertices(mesh), inside = 0;
        check(sprigs >= 6 * 20 && sprigs % 6 == 0, "the crown is whole sprigs of two triangles, a good many of them: " + sprigs / 6);
        float across = SpriteGeometry.TILES_PER_PIXEL_ACROSS, up = ViewCamera.LEVEL_HEIGHT / SpriteGeometry.PIXELS_PER_LEVEL;
        // The painted disc is 50 pixels round a middle 160 above the foot, and a sprig reaches at most its own length past where it grows.
        float reach = 50f * across + 1f, low = 110f * up - 1f, high = 210f * up + 1f;
        for (int vertex = 0; vertex < vertices; vertex++) {
            int at = vertex * SpriteGeometry.VERTEX_FLOATS;
            float dx = mesh.vertices[at] - 10.5f, dy = mesh.vertices[at + 1] - 20.5f, z = mesh.vertices[at + 2];
            check(Math.sqrt(dx * dx + dy * dy) <= reach, "no sprig stands further from the trunk than the sprite's crown is wide");
            check(z >= low && z <= high, "every sprig is at the height the sprite paints leaves at, not " + z);
            float u = mesh.vertices[at + 3], v = mesh.vertices[at + 4];
            float partU = u - (float) Math.floor(u), partV = v - (float) Math.floor(v);
            float material = (float) Math.floor((mesh.vertices[at + 8] + 0.5f) / 32f);
            boolean core = material == Flora.CORE;
            if (core) {
                // The inside of the crown: within the sprigs, in shade, and saying which kind of leaf it is made of.
                inside++;
                check(Math.sqrt(dx * dx + dy * dy) <= 50f * across * 0.8f, "the inside of a crown keeps inside its sprigs");
                check(Math.abs(partU - (Flora.MAPLE + 0.5f) / Flora.CORE_KINDS) < 1e-3f, "the inside says which sprig its leaves are");
                check(mesh.vertices[at + 5] < 0.8f * 0.7f, "the inside of a crown is in the shade of its outside");
            } else {
                check(Math.abs(partU - 0.25f) < 1e-3f || Math.abs(partU - 0.75f) < 1e-3f, "a corner is a quarter in from the whole number across");
                check(Math.abs(partV - 0.25f) < 1e-3f || Math.abs(partV - 0.75f) < 1e-3f, "and down");
                check(material == Flora.CROWN + Flora.MAPLE, "every vertex is marked as a sprig of the asked kind");
            }
            // The whole number is a place in the sprite's own part of its texture, which the crown is coloured from.
            float spriteU = (float) Math.floor(u) / Flora.CROWN_STEPS, spriteV = (float) Math.floor(v) / Flora.CROWN_STEPS;
            check(spriteU >= 0.25f - 1e-3f && spriteU <= 0.5f + 1e-3f && spriteV >= 0.5f - 1e-3f && spriteV <= 1f + 1e-3f,
                    "the colour is taken from inside the sprite's frame");
        }
        check(inside >= 3 * 40 && inside % 3 == 0, "a crown has an inside, of whole triangles all the way round it: " + inside / 3);
    }

    /** How many of a crown's vertices are its sprigs', and not its inside's. */
    private static int sprigVertices(SpriteGeometry.Mesh mesh) {
        int count = 0;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            if ((float) Math.floor((mesh.vertices[at + 8] + 0.5f) / 32f) != Flora.CORE) {
                count++;
            }
        }
        return count;
    }

    private static void crownFurtherOffIsFewerSprigs() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces(CrownTests::leaf, frame);
        SpriteGeometry.Mesh near = new SpriteGeometry.Mesh(), far = new SpriteGeometry.Mesh();
        Flora.addCrown(near, new SpriteGeometry.Mesh(), frame, places, 0.5f, 0.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.LINDEN, 7);
        Flora.addCrown(far, new SpriteGeometry.Mesh(), frame, places, 0.5f, 0.5f, 0f, FOOT_U, FOOT_V, 0.25f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.LINDEN, 7);
        int farSprigs = sprigVertices(far), nearSprigs = sprigVertices(near);
        check(farSprigs > 0 && farSprigs * 2 < nearSprigs, "a crown at a quarter of the detail has under half the sprigs: " + farSprigs + " of " + nearSprigs);
    }

    private static void spriteWithNextToNothingPaintedHasNoCrown() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces((u, v) -> u == 3 && v == 3, frame);
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        check(!Flora.addCrown(mesh, new SpriteGeometry.Mesh(), frame, places, 0.5f, 0.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.LINDEN, 7) && mesh.floats == 0,
                "a sprite with one dot painted makes no crown and adds nothing");
    }

    private static void sprigIsChosenByTheSpeciesInTheName() {
        check(Flora.sprigOf("e_virginiapine_1_3", false) == Flora.PINE, "a pine bears pine sprigs");
        check(Flora.sprigOf("e_redmaple_1_0", false) == Flora.MAPLE, "a maple bears maple sprigs");
        check(Flora.sprigOf("e_americanholly_1_2", false) == Flora.HOLLY, "a holly bears holly sprigs");
        check(Flora.sprigOf("e_canadianhemlock_1_2", false) == Flora.HEMLOCK, "a hemlock bears hemlock sprigs");
        check(Flora.sprigOf("e_riverbirch_1_2", false) == Flora.BIRCH, "a birch bears birch sprigs");
        check(Flora.sprigOf("e_americanlinden_1_2", false) == Flora.LINDEN, "a linden bears linden sprigs");
        check(Flora.sprigOf("f_bushes_1_4", true) == Flora.SHRUB, "a bush bears a shrub's, whatever it is called");
    }

    private static final int LEAVES = 41, PLAIN = 0;
    /** One sprite's leaves, found once as the mod finds them once a texture: two crowns are the same only on the same places. */
    private static final float[] PLACES = Flora.leafPlaces(CrownTests::leaf, frame());

    /** Two trees and a bush of one chunk as from that far off, the second tree in another light and with a bole to grow limbs from. */
    private static Crowns chunkOfThree(float secondRed, float away) {
        SpriteGeometry.Frame frame = frame();
        float[] places = PLACES;
        Crowns crowns = new Crowns();
        crowns.add(LEAVES, frame, places, 10.5f, 20.5f, 0.1f, FOOT_U, FOOT_V, away, 0f, 0f, Flora.BARK_OAK, Flora.BARK_OAK, Flora.MAPLE, 7);
        frame.light(secondRed, 0.5f, 0.4f);
        crowns.add(LEAVES + 1, frame, places, 12.5f, 21.5f, 0f, FOOT_U, FOOT_V, away, 0.12f, 0.9f, Flora.BARK_OAK + 1, Flora.BARK_OAK, Flora.LINDEN, 8);
        frame.open = 0f;
        crowns.add(LEAVES, frame, places, 14.5f, 22.5f, 0f, FOOT_U, FOOT_V, away, 0f, 0f, Flora.BARK_OAK, Flora.BARK_OAK, Flora.SHRUB, 9);
        return crowns;
    }

    private static Crowns chunkOfThree(float secondRed) {
        return chunkOfThree(secondRed, 6f);
    }

    /** The same three as a build made them before crowns were written down: each from its own numbers. */
    private static void threeInTheBuild(SpriteGeometry.MeshSet inBuild, float away) {
        SpriteGeometry.Frame frame = frame();
        float[] places = PLACES;
        float detail = Crowns.detail(away), close = Crowns.close(away);
        boolean limbed = Crowns.limbed(away);
        Flora.addCrown(inBuild.get(LEAVES), inBuild.get(PLAIN), frame, places, 10.5f, 20.5f, 0.1f, FOOT_U, FOOT_V, detail, close, 0f, 0f, Flora.BARK_OAK, Flora.MAPLE, 7);
        frame.light(0.6f, 0.5f, 0.4f);
        Flora.addCrown(inBuild.get(LEAVES + 1), inBuild.get(PLAIN), frame, places, 12.5f, 21.5f, 0f, FOOT_U, FOOT_V, detail, close, limbed ? 0.12f : 0f, limbed ? 0.9f : 0f,
                limbed ? Flora.BARK_OAK + 1 : Flora.BARK_OAK, Flora.LINDEN, 8);
        frame.open = 0f;
        Flora.addCrown(inBuild.get(LEAVES), inBuild.get(PLAIN), frame, places, 14.5f, 22.5f, 0f, FOOT_U, FOOT_V, detail, close, 0f, 0f, Flora.BARK_OAK, Flora.SHRUB, 9);
    }

    private static void same(SpriteGeometry.MeshSet apart, SpriteGeometry.MeshSet inBuild) {
        check(apart.used.size() == 3 && inBuild.used.size() == 3, "the leaves of two textures and the wood are three meshes");
        for (int i = 0; i < 3; i++) {
            SpriteGeometry.Mesh one = apart.used.get(i), other = inBuild.used.get(i);
            check(one.texture == other.texture && one.floats == other.floats && (one.floats > 0 || one.texture == PLAIN), "each mesh is of the same texture and as long");
            for (int at = 0; at < one.floats; at++) {
                check(Float.floatToRawIntBits(one.vertices[at]) == Float.floatToRawIntBits(other.vertices[at]), "and the same to the last bit of every float");
            }
        }
    }

    private static void crownsWrittenDownAreMadeAsTheBuildWouldHaveMadeThem() {
        SpriteGeometry.MeshSet apart = new SpriteGeometry.MeshSet(), inBuild = new SpriteGeometry.MeshSet();
        // Near, with limbs and stood under; in between; and far off, coarse and in its own bark.
        for (float away : new float[] {6f, 20f, 30f}) {
            apart.clear();
            inBuild.clear();
            chunkOfThree(0.6f, away).mesh(apart, PLAIN, new SpriteGeometry.Frame());
            threeInTheBuild(inBuild, away);
            same(apart, inBuild);
        }
    }

    private static void crownsOfAChunkInAnotherBandAreMadeFromWhatWasWrittenDown() {
        Crowns near = chunkOfThree(0.6f, 6f), moved = new Crowns();
        near.copyTo(moved, 30f);
        check(!moved.same(near) && moved.same(chunkOfThree(0.6f, 30f)), "written down again as from further off, they are what a build from there writes down");
        SpriteGeometry.MeshSet apart = new SpriteGeometry.MeshSet(), inBuild = new SpriteGeometry.MeshSet();
        moved.mesh(apart, PLAIN, new SpriteGeometry.Frame());
        threeInTheBuild(inBuild, 30f);
        same(apart, inBuild);
        // Into one that held more.
        Crowns one = new Crowns();
        one.add(LEAVES, frame(), PLACES, 1.5f, 2.5f, 0f, FOOT_U, FOOT_V, 6f, 0f, 0f, Flora.BARK_OAK, Flora.BARK_OAK, Flora.MAPLE, 3);
        one.copyTo(moved, 6f);
        check(moved.same(one) && !moved.same(near), "and take the place of whatever was written there before");
        // And more than a new one has room for: nine, where it starts with room for eight.
        Crowns many = new Crowns(), room = new Crowns();
        for (int i = 0; i < 9; i++) {
            many.add(LEAVES, frame(), PLACES, i + 0.5f, 2.5f, 0f, FOOT_U, FOOT_V, 6f, 0f, 0f, Flora.BARK_OAK, Flora.BARK_OAK, Flora.MAPLE, i);
        }
        many.copyTo(room, 6f);
        check(room.same(many), "nine crowns written into one that has held none are the same nine");
        many.copyTo(room, 20f);
        check(!room.same(many), "and from further off they are others");
    }

    private static void crownsMadeFromTheSameAreKnownForTheSame() {
        Crowns crowns = chunkOfThree(0.6f);
        check(crowns.same(chunkOfThree(0.6f)), "what a second build of an unchanged chunk writes down is the same crowns");
        check(!crowns.same(chunkOfThree(0.61f)), "a tree in another light is another crown");
        Crowns fewer = new Crowns();
        check(!crowns.same(fewer) && !fewer.same(crowns) && fewer.isEmpty() && !crowns.isEmpty(), "and a chunk with its trees felled has other crowns");
        crowns.clear();
        check(crowns.isEmpty() && crowns.same(fewer), "written down again from nothing, it holds nothing");
    }

    private static void crownsMadeOnTheirThreadAreTheSameCrowns() {
        SpriteGeometry.MeshSet here = new SpriteGeometry.MeshSet(), empty = new SpriteGeometry.MeshSet();
        chunkOfThree(0.6f).mesh(here, PLAIN, new SpriteGeometry.Frame());
        WorldScene.ChunkMeshes made = new WorldScene.ChunkMeshes(), there = new WorldScene.ChunkMeshes(), spared = new WorldScene.ChunkMeshes();
        made.fill(here, empty);
        Crowns.Work wanted = new Crowns.Work(chunkOfThree(0.6f), there, 5, PLAIN, false), unwanted = new Crowns.Work(chunkOfThree(0.6f), spared, 6, PLAIN, false);
        unwanted.unwanted = true;
        Crowns.begin(wanted);
        Crowns.begin(unwanted);
        check(Crowns.awaited() == wanted && Crowns.awaited() == unwanted && Crowns.finished() == null, "every piece of work comes back, in the order it went, and no more");
        check(spared.vertices == null && spared.floats == 0, "crowns nobody wants any more are not made");
        check(there.floats == made.floats && there.runs == made.runs && there.floats > 0, "the build made on the other thread is as long");
        for (int at = 0; at < made.floats; at++) {
            check(Float.floatToRawIntBits(there.vertices.get(at)) == Float.floatToRawIntBits(made.vertices.get(at)), "and the same to the last bit");
        }
        check(there.minX == made.minX && there.maxUp == made.maxUp, "with the same box round it");
        made.scrap();
        there.scrap();
        // One dot of paint is no crown, and is never written down as one: made all the same, the making fails.
        Crowns none = new Crowns();
        none.add(LEAVES, frame(), Flora.leafPlaces((u, v) -> u == 3 && v == 3, frame()), 0.5f, 0.5f, 0f, FOOT_U, FOOT_V, 12f, 0f, 0f, Flora.BARK_OAK, Flora.BARK_OAK, Flora.LINDEN, 7);
        Crowns.Work failing = new Crowns.Work(none, spared, 7, PLAIN, false);
        Crowns.begin(failing);
        check(Crowns.awaited() == failing && failing.failed instanceof IllegalStateException, "work that fails comes back too, with what it threw");
    }

    private static void spriteMeasuredAsNoCrownMakesNone() {
        SpriteGeometry.Frame frame = frame();
        float[] reachLeft = new float[Flora.CROWN_BANDS], reachRight = new float[Flora.CROWN_BANDS], sizes = new float[Flora.SIZES];
        check(!Flora.measure(frame, Flora.leafPlaces((u, v) -> u == 3 && v == 3, frame), FOOT_U, FOOT_V, reachLeft, reachRight, sizes), "one dot of paint measures as no crown");
        // A stroke one pixel wide up the middle: places enough, and no width.
        float[] stick = Flora.leafPlaces((u, v) -> u == (int) FOOT_U, frame);
        check(Flora.measure(frame, stick, FOOT_U, FOOT_V, reachLeft, reachRight, sizes)
                == Flora.addCrown(new SpriteGeometry.Mesh(), new SpriteGeometry.Mesh(), frame, stick, 0.5f, 0.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.LINDEN, 7),
                "what measures as a crown is made, and what does not is not");
        check(Flora.measure(frame, Flora.leafPlaces(CrownTests::leaf, frame), FOOT_U, FOOT_V, reachLeft, reachRight, sizes) && sizes[Flora.WIDEST] > 1f && sizes[Flora.TALL] > 1f,
                "a painted crown measures as one, tiles wide and tall");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}

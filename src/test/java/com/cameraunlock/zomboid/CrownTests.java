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
        check(vertices >= 6 * 20 && vertices % 6 == 0, "the crown is whole sprigs of two triangles, a good many of them: " + vertices / 6);
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
            check(Math.abs(partU - 0.25f) < 1e-3f || Math.abs(partU - 0.75f) < 1e-3f, "a corner is a quarter in from the whole number across");
            check(Math.abs(partV - 0.25f) < 1e-3f || Math.abs(partV - 0.75f) < 1e-3f, "and down");
            // The whole number is a place in the sprite's own part of its texture, which the crown is coloured from.
            float spriteU = (float) Math.floor(u) / Flora.CROWN_STEPS, spriteV = (float) Math.floor(v) / Flora.CROWN_STEPS;
            check(spriteU >= 0.25f - 1e-3f && spriteU <= 0.5f + 1e-3f && spriteV >= 0.5f - 1e-3f && spriteV <= 1f + 1e-3f,
                    "the colour is taken from inside the sprite's frame");
            float material = (float) Math.floor((mesh.vertices[at + 8] + 0.5f) / 32f);
            check(material == Flora.CROWN + Flora.MAPLE, "every vertex is marked as a sprig of the asked kind");
        }
    }

    private static void crownFurtherOffIsFewerSprigs() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces(CrownTests::leaf, frame);
        SpriteGeometry.Mesh near = new SpriteGeometry.Mesh(), far = new SpriteGeometry.Mesh();
        Flora.addCrown(near, new SpriteGeometry.Mesh(), frame, places, 0.5f, 0.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.LINDEN, 7);
        Flora.addCrown(far, new SpriteGeometry.Mesh(), frame, places, 0.5f, 0.5f, 0f, FOOT_U, FOOT_V, 0.25f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.LINDEN, 7);
        check(far.floats > 0 && far.floats * 2 < near.floats, "a crown at a quarter of the detail has under half the sprigs: " + far.floats + " of " + near.floats);
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

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}

package com.cameraunlock.zomboid;

/** Tests of the window the overhead picture opens through trees: see CanopyWindow. */
final class CanopyTests {
    private CanopyTests() {}

    static void run() {
        Checks.run("theWindowThroughTreesIsTheGamesSizeOnTheScreenAtEveryZoom", CanopyTests::theWindowThroughTreesIsTheGamesSizeOnTheScreenAtEveryZoom);
        Checks.run("theWorldShaderHasTheWindowsEdges", CanopyTests::theWorldShaderHasTheWindowsEdges);
        Checks.run("aTreeInTheWindowShowsAsMuchOfItselfAsTheGameDrawsByItsOwnSquare", CanopyTests::aTreeInTheWindowShowsAsMuchOfItselfAsTheGameDrawsByItsOwnSquare);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /**
     * The game draws its mask's texels Core.tileScale pixels each into a picture the zoom times
     * the screen's size, so on the screen half the player's window is 373 texels across and 187
     * down, at tileScale over zoom pixels a texel. The overhead camera takes in the screen's half
     * height in tiles, a tile being 64 tileScale pixels of the zoomed picture along its diagonal
     * (FirstPerson.aimOverhead), and the picture may be drawn smaller than the screen.
     */
    private static void theWindowThroughTreesIsTheGamesSizeOnTheScreenAtEveryZoom() {
        float away = 6000f;
        float[] size = new float[4];
        for (int tileScale = 1; tileScale <= 2; tileScale++) {
            for (int screenHigh : new int[] {720, 1080, 1440}) {
                for (float drawn : new float[] {0.5f, 0.75f, 1f}) {
                    for (float zoom = 0.25f; zoom <= 2.5f; zoom += 0.25f) {
                        float tileOnScreen = 64f * tileScale / zoom / (float) Math.sqrt(2.0);
                        float halfHighTiles = screenHigh / 2f / tileOnScreen;
                        float pictureHigh = screenHigh * drawn;
                        CanopyWindow.inPixels(pictureHigh, halfHighTiles / away, away, size);
                        float texel = tileScale / zoom * drawn;
                        String at = " at zoom " + zoom + ", tile scale " + tileScale + ", " + screenHigh + " high drawn at " + drawn;
                        check(Math.abs(size[0] - 373f * texel) < 0.01f * texel, "half the window is the game's 373 texels across" + at + ": " + size[0] / texel);
                        check(Math.abs(size[1] - 187f * texel) < 0.01f * texel, "and its 187 down" + at + ": " + size[1] / texel);
                        check(Math.abs(size[2] - 182f * texel) < 0.01f * texel, "the pointer's is the game's 182 texels round" + at + ": " + size[2] / texel);
                        check(Math.abs(size[3] - texel) < 0.0001f * texel, "and the dither is laid in the game's texels" + at + ": " + size[3] / texel);
                    }
                }
            }
        }
    }

    /**
     * The game eases a tree it opens to 0.05 alpha and back (IsoTree.fadeAlpha) and draws it
     * blended; the picture has no blending for the world, so it leaves out the share of a
     * tree's pixels the game does not draw, picked by a fixed pattern, and takes the share
     * by the tree's square, which every vertex of a tree carries, and not the pixel's own.
     */
    private static void aTreeInTheWindowShowsAsMuchOfItselfAsTheGameDrawsByItsOwnSquare() {
        String shader = PipelineShaders.WORLD_FRAGMENT_PARTS + PipelineShaders.WORLD_FRAGMENT_MAIN;
        check(shader.contains("uniform vec3 uFaded[CANOPY_TREES];") && CanopyWindow.GLSL.contains("#define CANOPY_TREES " + CanopyWindow.TREES + "\n"),
                "the shader is told of as many trees as the picture gathers");
        check(shader.contains("bool tree = vRoot.x >= 0.0;"), "a tree is what carries a tree's square");
        check(shader.contains("if (all(lessThan(abs(uFaded[i].xy - vRoot), vec2(0.25)))) {"), "a tree is looked up by its own square");
        check(shader.contains("if (grain(gl_FragCoord.xy + vec2(613.0, 211.0)) >= shown) {"), "and that share of its pixels is drawn");
        check(PipelineShaders.WORLD_VERTEX.contains("vRoot = uModel > 0.5 ? vec2(-1.0) : gl_MultiTexCoord2.xy;"), "the square comes from the vertex, and a model has none");
        // The pattern lets through the share asked for: GLSL's grain, here in Java, over a stretch of the screen.
        for (float share : new float[] {0.05f, 0.3f, 0.7f}) {
            int kept = 0, all = 0;
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < 200; x++) {
                    double p = (x + 0.5 + 613.0) % 4096.0, q = (y + 0.5 + 211.0) % 4096.0;
                    double s = Math.sin(p * 12.9898 + q * 78.233) * 43758.5453;
                    kept += s - Math.floor(s) < share ? 1 : 0;
                    all++;
                }
            }
            check(Math.abs((float) kept / all - share) < 0.02f, "a tree the game draws at " + share + " keeps that share of its pixels: " + (float) kept / all);
        }
    }

    private static void theWorldShaderHasTheWindowsEdges() {
        String shader = PipelineShaders.WORLD_FRAGMENT_PARTS;
        check(shader.contains(CanopyWindow.GLSL), "the world shader is handed the window's edges");
        check(CanopyWindow.GLSL.contains("#define CANOPY_EDGE_FROM 0.85\n") && CanopyWindow.GLSL.contains("#define CANOPY_AIMED_TO 1.29\n"),
                "each edge is a number GLSL reads: " + CanopyWindow.GLSL);
    }
}

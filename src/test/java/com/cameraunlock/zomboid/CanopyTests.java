package com.cameraunlock.zomboid;

/** Tests of the window the overhead picture opens through trees: see CanopyWindow. */
final class CanopyTests {
    private CanopyTests() {}

    static void run() {
        Checks.run("theWindowThroughTreesIsTheGamesSizeOnTheScreenAtEveryZoom", CanopyTests::theWindowThroughTreesIsTheGamesSizeOnTheScreenAtEveryZoom);
        Checks.run("theWorldShaderHasTheWindowsEdges", CanopyTests::theWorldShaderHasTheWindowsEdges);
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

    private static void theWorldShaderHasTheWindowsEdges() {
        String shader = PipelineShaders.WORLD_FRAGMENT_PARTS;
        check(shader.contains(CanopyWindow.GLSL), "the world shader is handed the window's edges");
        check(CanopyWindow.GLSL.contains("#define CANOPY_EDGE_FROM 0.85\n") && CanopyWindow.GLSL.contains("#define CANOPY_AIMED_TO 1.29\n"),
                "each edge is a number GLSL reads: " + CanopyWindow.GLSL);
    }
}

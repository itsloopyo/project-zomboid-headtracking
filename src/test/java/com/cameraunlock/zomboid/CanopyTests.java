package com.cameraunlock.zomboid;

/** Tests of the window the overhead picture opens through trees: see CanopyWindow. */
final class CanopyTests {
    private CanopyTests() {}

    static void run() {
        Checks.run("theWindowThroughTreesIsTheGamesSizeOnTheScreenAtEveryZoom", CanopyTests::theWindowThroughTreesIsTheGamesSizeOnTheScreenAtEveryZoom);
        Checks.run("theWorldShaderHasTheWindowsEdges", CanopyTests::theWorldShaderHasTheWindowsEdges);
        Checks.run("theWindowAndThePointersLandOnTheFloorUnderThem", CanopyTests::theWindowAndThePointersLandOnTheFloorUnderThem);
        Checks.run("theGamesMaskIsWhereTheGameDrewItWithTheCameraLed", CanopyTests::theGamesMaskIsWhereTheGameDrewItWithTheCameraLed);
        Checks.run("theTreesTheGameFadedAimingFarOffAreTheTreesUnderItsMasks", CanopyTests::theTreesTheGameFadedAimingFarOffAreTheTreesUnderItsMasks);
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
        check(shader.contains("bool tree = vRoot.x >= 0.0 && !(vMaterial > 84.5 && vMaterial < 85.5);"), "a tree is what carries a tree's square, and blood, which carries its colour there, is none");
        check(shader.contains("if (all(lessThan(abs(uFaded[i].xy - vRoot), vec2(0.25)))) {"), "a tree is looked up by its own square");
        check(shader.contains("if (grain(gl_FragCoord.xy + vec2(613.0, 211.0)) >= shown) {"), "and that share of its pixels is drawn");
        check(PipelineShaders.WORLD_VERTEX.contains("vRoot = uModel > 0.5 ? vec2(-1.0, piece) : gl_MultiTexCoord2.xy;"),
                "the square comes from the vertex, and a model has none: it carries the piece of its surface in the place of one");
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

    /** Where the game draws a place on the floor at tile scale 2 and that zoom, with its camera moved off by (offX, offY) pixels of its zoomed picture. */
    private static float[] drawn(float x, float y, float zoom, float offX, float offY) {
        return new float[] {((x - y) * 64f - offX) / zoom, ((x + y) * 32f - offY) / zoom};
    }

    /**
     * FirstPerson.aimOverhead puts the middle of the screen less the camera's lead, and the
     * pointer, on the player's floor from where the player is drawn and how a step east and a
     * step south move on the screen, as the game draws its view at zoom 2.
     */
    private static void theWindowAndThePointersLandOnTheFloorUnderThem() {
        float zoom = 2f, offX = 1395000f, offY = 621000f, x = 10900.3f, y = 8500.7f;
        float[] at = drawn(x, y, zoom, offX, offY), east = drawn(x + 1f, y, zoom, offX, offY), south = drawn(x, y + 1f, zoom, offX, offY);
        float[] onFloor = new float[2];
        // The pointer over the middle of a square three east and two north of the player's.
        float[] pointer = drawn(10903.5f, 8498.5f, zoom, offX, offY);
        ViewCamera.underScreen(x, y, at[0], at[1], east[0] - at[0], east[1] - at[1], south[0] - at[0], south[1] - at[1], pointer[0], pointer[1], onFloor);
        check(Math.abs(onFloor[0] - 10903.5f) < 0.01f && Math.abs(onFloor[1] - 8498.5f) < 0.01f,
                "the pointer lands on the floor under it: " + onFloor[0] + ", " + onFloor[1]);
        // The game's window: the middle of a 1280 by 720 screen less a lead of (120, -80) pixels of the zoomed picture.
        float middleX = 640f - 120f / zoom, middleY = 360f + 80f / zoom;
        ViewCamera.underScreen(x, y, at[0], at[1], east[0] - at[0], east[1] - at[1], south[0] - at[0], south[1] - at[1], middleX, middleY, onFloor);
        float[] back = drawn(onFloor[0], onFloor[1], zoom, offX, offY);
        check(Math.abs(back[0] - middleX) < 0.05f && Math.abs(back[1] - middleY) < 0.05f,
                "the place it puts the window on is drawn where the game draws its mask: " + back[0] + ", " + back[1]);
    }

    /**
     * Recorded in the game (rig, 1280x720, zoom 2, panning while aiming on, pointer at the top
     * right): the camera led by (1117, -1092) pixels of the zoomed picture, and the game's
     * stencil area for the player's mask was (-861, 788) to (1187, 2836) of that picture, so
     * its middle was at (163, 1812), on the screen half that.
     */
    private static void theGamesMaskIsWhereTheGameDrewItWithTheCameraLed() {
        float[] at = new float[2];
        CanopyWindow.playerMaskAt(1280f, 720f, 1117f, -1092f, 2f, at);
        check(Math.abs(at[0] - 81.5f) < 0.01f && Math.abs(at[1] - 906f) < 0.01f, "the player's mask is where the game drew it: " + at[0] + ", " + at[1]);
    }

    private static final String FADED = "0,-40 -6,-39 -8,-38 -3,-38 -6,-35 -1,-35 -9,-25 -13,-22 -9,-22 -14,-19 -11,-19 -6,-18 -17,-17 -9,-17 -4,-17"
            + " -2,-15 -18,-14 -23,-12 -11,-12 -6,-12 3,-12 -19,-11 -16,-11 -13,-10 -4,-10 5,-10 -7,-9 -2,-9 3,-8 7,-8 -18,-7"
            + " -15,-6 1,-6 0,-4 3,-4 -20,-3 -9,-3 -12,-2 -2,-2 0,-2 7,-2 -18,-1 -7,-1 -10,0 -3,0 6,0 14,0 3,1 10,1 -1,2 12,2"
            + " 17,2 -11,3 5,3 -9,4 9,4 14,4 18,4 3,5 -7,6 20,6 5,7 9,7 17,7 -3,8 1,8 15,8 13,9 -7,10 -3,10 1,10 3,10 5,10"
            + " 10,10 17,10 20,10 -3,12 14,12 16,12 20,12 7,13 9,13 -1,14 18,14 22,14 3,15 6,15 9,15 12,16 2,17 5,17 14,18"
            + " 9,19 13,21 5,22 9,23 12,23 8,25";
    private static final String KEPT = "-35,-40 -32,-40 -19,-40 -16,-40 -13,-40 30,-40 36,-40 -24,-39 -22,-39 7,-39 12,-39 40,-39 -30,-38 -27,-38"
            + " -18,-38 22,-38 25,-38 -28,-36 -26,-36 -19,-36 -15,-36 -10,-36 7,-36 14,-36 18,-36 -33,-35 -30,-35 -8,-35 5,-35"
            + " 10,-35 -40,-34 -24,-34 -17,-34 -13,-34 -10,-34 20,-34 29,-34 38,-34 -31,-33 -2,-33 3,-33 24,-33 -8,-32 8,-32"
            + " 17,-32 22,-32 -36,-31 -26,-31 -17,-31 5,-31 14,-31 -30,-30 -13,-30 19,-30 24,-30 -34,-29 -19,-29 1,-29 9,-29"
            + " -28,-28 -24,-28 -14,-28 -9,-28 -3,-28 26,-28 -31,-27 -17,-27 -12,-27 4,-27 10,-27 12,-27 30,-27 34,-27 -26,-26"
            + " -3,-26 2,-26 6,-26 20,-26 -22,-25 -20,-25 -12,-25 4,-25 13,-25 16,-25 18,-25 36,-25 -34,-24 -7,-24 1,-24 8,-24"
            + " -38,-23 -31,-23 -24,-23 -2,-23 4,-23 15,-23 -35,-22 -28,-22 -26,-22 -16,-22 0,-22 2,-22 13,-22 34,-22 40,-22"
            + " -18,-20 9,-20 19,-20 -36,-19 -34,-19 -29,-19 -22,-19 -1,-19 3,-19 7,-19 15,-19 38,-19 -31,-18 5,-18 21,-18"
            + " -39,-17 -34,-17 -25,-17 10,-17 17,-17 33,-17 -20,-16 4,-16 7,-16 23,-16 -22,-15 14,-15 -38,-14 -36,-14 -30,-13"
            + " 22,-13 36,-13 -36,-12 6,-12 -40,-11 19,-11 24,-11 38,-11 -34,-10 -31,-10 -39,-9 18,-9 21,-7 32,-7 -38,-6"
            + " -27,-6 12,-6 -27,-4 -38,-3 38,-3 -35,-2 -31,-2 -25,-2 17,-2 31,-2 35,-2 -40,-1 21,-1 24,-1 -28,0 28,0 33,0"
            + " 37,0 -31,1 -34,2 -25,2 30,2 -23,3 -19,3 22,3 -40,4 -35,4 -29,4 -15,4 37,4 40,4 -26,5 -20,5 24,5 -34,6 -31,6"
            + " -14,6 39,6 -40,7 -27,7 -24,7 -18,7 -35,8 -32,8 -12,8 35,8 -9,9 30,9 33,9 -38,10 -34,10 -29,10 -27,10 -24,10"
            + " -15,10 -30,12 -8,12 34,12 39,12 -36,13 -26,13 -22,13 -10,13 -40,14 -33,14 -18,14 -15,14 -13,14 26,14 36,14"
            + " -30,15 -6,15 -4,15 -36,16 -22,16 -16,16 38,16 -38,17 -10,17 -1,17 21,17 30,17 -34,18 -25,18 -22,18 -18,18"
            + " -14,18 -4,18 -8,19 23,19 28,19 -38,20 -11,20 -2,20 18,20 -40,21 -35,21 -32,21 -28,21 -18,21 -16,21 -14,21"
            + " 25,21 30,21 40,21 -38,22 -22,22 -9,22 -6,22 2,22 16,22 21,22 -34,23 -24,23 -12,23 -4,23 -39,24 -36,24 -26,24"
            + " -18,24 -15,24 -6,24 4,24 18,24 23,24 26,24 -32,25 -2,25 6,25 14,25 -16,26 -9,26 -7,26 39,26 -31,28 -18,28 1,28"
            + " 25,28 -34,29 -22,29 -15,29 -11,29 -8,29 -3,29 6,29 14,29 -29,30 -24,30 -6,30 3,30 23,30 -26,31 -18,31 -13,31"
            + " -9,31 8,31 17,31 20,31 -38,32 5,32 13,32 15,32 31,32 -35,33 -32,33 -24,33 -16,33 -7,33 1,33 3,33 -30,34 -27,34"
            + " 17,34 22,34 26,34 30,34 -34,35 -26,35 -19,35 -14,35 -8,35 5,35 9,35 13,35 38,35 0,36 21,36 23,36 32,36 35,36"
            + " -40,37 -32,37 -17,37 -3,37 15,37 38,37 -26,38 -11,38 -7,38 9,38 25,38 -22,39 -13,39 -9,39 -1,39 3,39 13,39"
            + " 22,39 29,39 40,39 -34,40 -27,40 -20,40 -15,40 6,40 9,40 16,40 18,40 34,40";

    /**
     * Recorded in the same run, in a wood at (10900.5, 8500.5): with the firearm aimed at the top
     * right, every tree within 40 squares of the player the game was drawing see-through
     * (FADED, east and south of the player's square) and every one it drew whole (KEPT). The
     * pointer's mask was 46 squares north of the player, beyond any fixed reach round them. With
     * the player's mask's middle on the floor at (-1.25, -0.625) from the player's square,
     * which is where its 98 and 369 trees put it, and the pointer's from the recorded stencil
     * areas, a tree is under a mask exactly when the game faded it.
     */
    private static void theTreesTheGameFadedAimingFarOffAreTheTreesUnderItsMasks() {
        float playerX = -1.25f, playerY = -0.625f;
        // The pointer's mask's middle from the player's, in pixels of the zoomed picture at tile scale 2: (2520, 38) less (163, 1812).
        float across = 2357f / 64f, down = -1774f / 32f;
        float pointerX = playerX + (across + down) / 2f, pointerY = playerY + (down - across) / 2f;
        int far = 0;
        for (String[] list : new String[][] {FADED.split(" "), KEPT.split(" ")}) {
            boolean faded = list[0].equals(FADED.split(" ")[0]);
            for (String tree : list) {
                int x = Integer.parseInt(tree.split(",")[0]), y = Integer.parseInt(tree.split(",")[1]);
                boolean under = CanopyWindow.underMask(x, y, playerX, playerY, CanopyWindow.PLAYER_HALF)
                        || CanopyWindow.underMask(x, y, pointerX, pointerY, CanopyWindow.POINTER_HALF);
                check(under == faded, "the tree at " + x + ", " + y + " from the player is " + (faded ? "" : "not ") + "under a mask, as the game " + (faded ? "faded" : "kept") + " it");
                far += faded && Math.max(Math.abs(x), Math.abs(y)) > 20 ? 1 : 0;
                int reach = CanopyWindow.reach(under && CanopyWindow.underMask(x, y, pointerX, pointerY, CanopyWindow.POINTER_HALF) ? CanopyWindow.POINTER_HALF : CanopyWindow.PLAYER_HALF);
                if (under) {
                    float fromX = CanopyWindow.underMask(x, y, pointerX, pointerY, CanopyWindow.POINTER_HALF) ? pointerX : playerX;
                    float fromY = fromX == pointerX ? pointerY : playerY;
                    check(Math.max(Math.abs(x - Math.floor(fromX)), Math.abs(y - Math.floor(fromY))) <= reach, "and within the reach looked through from that mask's middle");
                }
            }
        }
        check(far >= 6, "the game faded trees more than 20 squares from the player: " + far);
    }

    private static void theWorldShaderHasTheWindowsEdges() {
        String shader = PipelineShaders.WORLD_FRAGMENT_PARTS;
        check(shader.contains(CanopyWindow.GLSL), "the world shader is handed the window's edges");
        check(CanopyWindow.GLSL.contains("#define CANOPY_EDGE_FROM 0.85\n") && CanopyWindow.GLSL.contains("#define CANOPY_AIMED_TO 1.29\n"),
                "each edge is a number GLSL reads: " + CanopyWindow.GLSL);
    }
}

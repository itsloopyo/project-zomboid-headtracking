package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of how a loose item's picture is laid flat where the item lies. */
final class GroundItemTests {
    private GroundItemTests() {}

    static void run() {
        Checks.run("pictureIsCentredOnTheItemAndAsLargeAsTheGameDrawsIt", GroundItemTests::pictureIsCentredOnTheItemAndAsLargeAsTheGameDrawsIt);
        Checks.run("pictureTurnsWithTheItemAndKeepsItsSize", GroundItemTests::pictureTurnsWithTheItemAndKeepsItsSize);
        Checks.run("trimmedPictureKeepsItsPlaceInTheWhole", GroundItemTests::trimmedPictureKeepsItsPlaceInTheWhole);
        Checks.run("itemsOfOneSquareTakeLayersOfTheirOwn", GroundItemTests::itemsOfOneSquareTakeLayersOfTheirOwn);
        Checks.run("modelStandsUpOnTheGroundAtTheGamesSize", GroundItemTests::modelStandsUpOnTheGroundAtTheGamesSize);
        Checks.run("modelTurnsOnTheGroundAndKeepsItsSize", GroundItemTests::modelTurnsOnTheGroundAndKeepsItsSize);
        Checks.run("modelKeepsItsTransformsScaleAndPlace", GroundItemTests::modelKeepsItsTransformsScaleAndPlace);
        Checks.run("modelBroughtToLifeSizeKeepsItsFootOnTheGround", GroundItemTests::modelBroughtToLifeSizeKeepsItsFootOnTheGround);
        Checks.run("ballsAreBroughtToTheirRealSizes", GroundItemTests::ballsAreBroughtToTheirRealSizes);
    }

    private static final float[] IDENTITY = {1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};
    private static final float[] placing = new float[12];

    /** Where the placing takes a point of the model: x, y and up from the item's place. */
    private static float[] placed(float px, float py, float pz) {
        float[] at = new float[3];
        for (int row = 0; row < 3; row++) {
            at[row] = placing[row * 4] * px + placing[row * 4 + 1] * py + placing[row * 4 + 2] * pz + placing[row * 4 + 3];
        }
        return at;
    }

    private static void modelStandsUpOnTheGroundAtTheGamesSize() {
        GroundItems.placing(IDENTITY, 0f, 0f, 0f, placing);
        float[] up = placed(0f, 1f, 0f), across = placed(1f, 0f, 0f), along = placed(0f, 0f, 1f);
        float s = GroundItems.MODEL_SCALE;
        check(near(up[0], 0f) && near(up[1], 0f) && near(up[2], s), "the model's y is up: " + up[0] + ", " + up[1] + ", " + up[2]);
        check(near(across[0], s) && near(across[1], 0f) && near(across[2], 0f), "its x is the world's x");
        check(near(along[0], 0f) && near(along[1], -s) && near(along[2], 0f), "its z is the world's minus y");
        float[] origin = placed(0f, 0f, 0f);
        check(near(origin[0], 0f) && near(origin[1], 0f) && near(origin[2], 0f), "its origin is the item's place");
    }

    /** The game turns an item lying on the ground about the model's y by its worldZRotation. */
    private static void modelTurnsOnTheGroundAndKeepsItsSize() {
        float s = GroundItems.MODEL_SCALE;
        GroundItems.placing(IDENTITY, 0f, 90f, 0f, placing);
        float[] across = placed(1f, 0f, 0f), up = placed(0f, 1f, 0f);
        check(near(across[0], 0f) && near(across[1], s) && near(across[2], 0f), "a quarter turn takes x along y: " + across[0] + ", " + across[1] + ", " + across[2]);
        check(near(up[2], s), "and leaves up up");
        GroundItems.placing(IDENTITY, 0f, 37f, 0f, placing);
        across = placed(1f, 0f, 0f);
        check(near((float) Math.hypot(across[0], across[1]), s) && near(across[2], 0f), "turned any way, it lies the same size on the ground");
    }

    private static void modelKeepsItsTransformsScaleAndPlace() {
        float[] transform = {0.01f, 0f, 0f, 0f, 0f, 0.01f, 0f, 0f, 0f, 0f, 0.01f, 0f, 0.1f, 0.2f, 0.3f, 1f};
        GroundItems.placing(transform, 0f, 0f, 0f, placing);
        float[] tip = placed(10f, 0f, 0f);
        float s = GroundItems.MODEL_SCALE;
        check(near(tip[0], (0.1f + 0.1f) * s) && near(tip[1], -0.3f * s) && near(tip[2], 0.2f * s),
                "the transform's scale and place come first: " + tip[0] + ", " + tip[1] + ", " + tip[2]);
    }

    /** A ball 5.66 across and 5.56 high standing on its origin, as the game's pool ball mesh is, at 0.01 and turned. */
    private static void modelBroughtToLifeSizeKeepsItsFootOnTheGround() {
        float[] places = {-2.83f, 0f, -2.83f, 2.83f, 5.56f, 2.83f, 0f, 0f, 0f};
        float[] transform = {0.01f, 0f, 0f, 0f, 0f, 0.01f, 0f, 0f, 0f, 0f, 0.01f, 0f, 0f, 0f, 0f, 1f};
        GroundItems.placing(transform, 0f, 37f, 0f, placing);
        GroundItems.toLength(placing, places, 0.05715f);
        float[] left = placed(-2.83f, 0f, 0f), right = placed(2.83f, 0f, 0f), top = placed(0f, 5.56f, 0f), foot = placed(0f, 0f, 0f);
        check(near((float) Math.hypot(right[0] - left[0], right[1] - left[1]), 0.05715f), "it is 57.15 mm across: "
                + Math.hypot(right[0] - left[0], right[1] - left[1]));
        check(near(top[2], 0.05715f * 5.56f / 5.66f), "and as much shorter as the mesh is: " + top[2]);
        check(near(foot[0], 0f) && near(foot[1], 0f) && near(foot[2], 0f), "its foot stays on the item's place");
    }

    /** Each ball's length in metres against the sizes its rules allow, so a slip of units shows. */
    private static void ballsAreBroughtToTheirRealSizes() {
        Object[][] allowed = {{"Base.PoolBall", 0.0566f, 0.0577f}, {"Base.Baseball", 0.0729f, 0.0748f}, {"Base.TennisBall", 0.0654f, 0.0686f},
            {"Base.GolfBall", 0.04267f, 0.0430f}, {"Base.Basketball", 0.238f, 0.243f}, {"Base.Football", 0.279f, 0.286f}, {"Base.Poolcue", 1.4f, 1.5f}};
        for (Object[] ball : allowed) {
            Float size = ItemModels.LIFE_SIZE.get((String) ball[0]);
            check(size != null && size >= (Float) ball[1] && size <= (Float) ball[2], ball[0] + " is its real size: " + size);
        }
        check(ItemModels.LIFE_SIZE.size() == allowed.length, "and nothing else is resized: " + ItemModels.LIFE_SIZE.keySet());
    }

    private static final float[] corners = new float[4 * GroundItems.CORNER_FLOATS];

    private static float x(int corner) {
        return corners[corner * GroundItems.CORNER_FLOATS];
    }

    private static float y(int corner) {
        return corners[corner * GroundItems.CORNER_FLOATS + 1];
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    /** A 32 pixel picture drawn at 0.6 of its size at tile scale 2 is 19.2 pixels of a 128 pixel diagonal. */
    private static void pictureIsCentredOnTheItemAndAsLargeAsTheGameDrawsIt() {
        GroundItems.corners(32f, 32f, 0f, 0f, 32f, 32f, 0.6f, 0.6f, 2f, 0f, 0.1f, 0.2f, 0.3f, 0.4f, corners);
        float side = 19.2f / 128f * (float) Math.sqrt(2.0);
        check(near(x(0), -side / 2f) && near(y(0), -side / 2f) && near(x(2), side / 2f) && near(y(2), side / 2f),
                "the picture is " + side + " tiles square about the item: " + x(0) + ", " + y(0) + " to " + x(2) + ", " + y(2));
        check(near(x(0) + x(1) + x(2) + x(3), 0f) && near(y(0) + y(1) + y(2) + y(3), 0f), "centred on the item");
        float[] u = {0.1f, 0.3f, 0.3f, 0.1f}, v = {0.2f, 0.2f, 0.4f, 0.4f};
        for (int corner = 0; corner < 4; corner++) {
            int at = corner * GroundItems.CORNER_FLOATS;
            check(corners[at + 2] == u[corner] && corners[at + 3] == v[corner], "corner " + corner + " takes its own texel");
        }
    }

    private static void pictureTurnsWithTheItemAndKeepsItsSize() {
        GroundItems.corners(40f, 20f, 0f, 0f, 40f, 20f, 1f, 1f, 2f, 0f, 0f, 0f, 1f, 1f, corners);
        float wide = x(1) - x(0), high = y(3) - y(0);
        GroundItems.corners(40f, 20f, 0f, 0f, 40f, 20f, 1f, 1f, 2f, 90f, 0f, 0f, 1f, 1f, corners);
        check(near(x(1) - x(0), 0f) && near(y(1) - y(0), wide), "turned a quarter, the picture's right runs along y: " + (x(1) - x(0)) + ", " + (y(1) - y(0)));
        check(near(x(3) - x(0), -high) && near(y(3) - y(0), 0f), "and its down along minus x");
        GroundItems.corners(40f, 20f, 0f, 0f, 40f, 20f, 1f, 1f, 2f, 37f, 0f, 0f, 1f, 1f, corners);
        float across = (float) Math.hypot(x(1) - x(0), y(1) - y(0)), down = (float) Math.hypot(x(3) - x(0), y(3) - y(0));
        check(near(across, wide) && near(down, high), "turned any way it is the same size");
        check(near((x(1) - x(0)) * (x(3) - x(0)) + (y(1) - y(0)) * (y(3) - y(0)), 0f), "and still square at the corners");
    }

    /** A texture holds the picture's non-empty rectangle only, and that rectangle lies where it is in the whole picture. */
    private static void trimmedPictureKeepsItsPlaceInTheWhole() {
        GroundItems.corners(32f, 32f, 8f, 4f, 16f, 24f, 1f, 1f, 1f, 0f, 0f, 0f, 1f, 1f, corners);
        float perPixel = SpriteGeometry.TILES_PER_PIXEL_ACROSS;
        check(near(x(0), -8f * perPixel) && near(y(0), -12f * perPixel) && near(x(2), 8f * perPixel) && near(y(2), 12f * perPixel),
                "the rectangle from (8, 4) to (24, 28) of a 32 pixel picture centred on the item");
    }

    private static void itemsOfOneSquareTakeLayersOfTheirOwn() {
        check(GroundItems.LAYERS >= 2, "two items that overlap lie a layer apart");
        check(GroundItems.LAYERS * WorldScene.STAND_OFF <= 0.04f, "and a pile lies no more than 0.04 tiles off what it is on near the eye: " + GroundItems.LAYERS * WorldScene.STAND_OFF);
    }
}

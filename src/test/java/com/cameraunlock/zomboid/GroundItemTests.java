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

package com.cameraunlock.zomboid;

/** Tests of how the mod's own picture is laid in a frame or over a sheet on a wall. */
final class DecorTests {
    private DecorTests() {}

    static void run() {
        pictureOfTheFramesShapeFillsItCornerToCorner();
        tallPictureInASquareFrameKeepsItsWidthAndTheUpperPartOfItsHeight();
        widePictureInATallFrameShowsItsMiddle();
        squaresOfALongPictureShowItsPartsInOrder();
        paintedFrameLeavesARailRoundThePictureThatLooksOutsideIt();
        everyFrameHasAPaneInsideItsMoulding();
    }

    private static float u(float[] laid, float x) {
        return laid[0] + x * laid[2];
    }

    private static float v(float[] laid, float z) {
        return laid[1] + z * laid[5];
    }

    private static void pictureOfTheFramesShapeFillsItCornerToCorner() {
        float[] laid = new float[6];
        Decor.lay(-0.3f, 0.3f, 0.1f, 1.0f, 0.6f, 0.9f, 0f, 2f / 3f, laid);
        check(near(u(laid, -0.3f), 0f) && near(u(laid, 0.3f), 1f), "the picture's left edge is at the rectangle's left and its right at the right");
        check(near(v(laid, 1.0f), 0f) && near(v(laid, 0.1f), 1f), "the picture's top is at the rectangle's top and its foot at the foot");
        check(laid[3] == 0f && laid[4] == 0f, "a picture hangs square: across it is across the model and up it is up");
    }

    private static void tallPictureInASquareFrameKeepsItsWidthAndTheUpperPartOfItsHeight() {
        float[] laid = new float[6];
        Decor.lay(0f, 1f, 0f, 1f, 0.5f, 0.5f, 0f, 0.5f, laid);
        check(near(u(laid, 0f), 0f) && near(u(laid, 1f), 1f), "all of its width is shown");
        float top = v(laid, 1f), foot = v(laid, 0f);
        check(near(foot - top, 0.5f), "half of its height is shown, so it is not squashed: " + (foot - top));
        check(top > 0f && top < 0.25f, "and the half shown is above the middle, where a sitter's head is: from " + top);
    }

    private static void widePictureInATallFrameShowsItsMiddle() {
        float[] laid = new float[6];
        Decor.lay(-1f, 1f, 0f, 4f, 0.4f, 0.8f, 0f, 2f, laid);
        check(near(v(laid, 4f), 0f) && near(v(laid, 0f), 1f), "all of its height is shown");
        check(near(u(laid, -1f), 0.375f) && near(u(laid, 1f), 0.625f), "and the middle quarter of its width");
    }

    private static void squaresOfALongPictureShowItsPartsInOrder() {
        // Two squares' lengths of one picture, each a model a metre wide about its own middle, the picture twice as wide as high.
        float[] first = new float[6], second = new float[6];
        Decor.lay(-0.5f, 1.5f, 0f, 1f, 2f, 1f, 0f, 2f, first);
        Decor.lay(-1.5f, 0.5f, 0f, 1f, 2f, 1f, 0f, 2f, second);
        check(near(u(first, -0.5f), 0f) && near(u(first, 0.5f), 0.5f), "the left square shows the left half");
        check(near(u(second, -0.5f), 0.5f) && near(u(second, 0.5f), 1f), "and the right square the right half, starting where the left one stops");
    }

    private static void paintedFrameLeavesARailRoundThePictureThatLooksOutsideIt() {
        // A board two tiles by one, each a metre of the model, a rail of a tenth of a tile, the picture a little squarer than what is left.
        float[] laid = new float[6];
        Decor.lay(0f, 2f, 0f, 1f, 2f, 1f, 0.1f, 2f, laid);
        check(near(u(laid, 0.1f), 0f) && near(u(laid, 1.9f), 1f), "the picture starts and stops a rail inside the board's ends");
        check(near(v(laid, 0.9f), 0f) && near(v(laid, 0.1f), 1f), "and a rail inside its top and its foot");
        check(u(laid, 0f) < 0f && u(laid, 2f) > 1f && v(laid, 1f) < 0f && v(laid, 0f) > 1f, "the rail itself looks outside the picture on every side");
    }

    private static void everyFrameHasAPaneInsideItsMoulding() {
        for (String name : new String[] {"frame_tall", "frame_square", "frame_wide", "canvas"}) {
            Models.Model model = Models.get(name);
            float[] pane = Decor.pane(model);
            check(pane[0] >= model.minX && pane[1] <= model.maxX && pane[2] >= model.minZ && pane[3] <= model.maxZ, name + "'s pane is within it");
            check(pane[1] - pane[0] > 0.8f * model.width() && pane[3] - pane[2] > 0.8f * model.height(), name + "'s pane is most of its face");
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-5f;
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}

package com.cameraunlock.zomboid;

/**
 * A loose item lying in the world: a bottle on a floor, a packet on a table. Unless it draws
 * the item's model, the game draws a small picture of it upright on its screen over the point
 * where the item lies. Stood up as a card turned to the eye at the middle of its square, every
 * item on a square was a card in the one place, and two of them fought for depth. Here the
 * picture is laid flat on whatever the item lies on, at the item's own place, as large as the
 * game draws it, and turned the way the game turns the item's model on the ground.
 */
final class GroundItems {
    private GroundItems() {}

    /** Floats per corner in what corners writes: x and y off the item's place, in tiles, then the texel u and v. */
    static final int CORNER_FLOATS = 4;
    /** The two triangles, of the corners at the picture's top left, top right, bottom right and bottom left. */
    static final int[] TRIANGLES = {0, 1, 2, 0, 2, 3};
    /**
     * How many layers off what they lie on the items of one square are spread over. Each lies a
     * layer clear of the one before, so two that overlap do not fight. Past this many they take
     * the layers again from the first: a layer near the eye is 4 mm, and a pile laid each a layer
     * up would float off the floor.
     */
    static final int LAYERS = 8;

    /**
     * The four corners of an item's picture laid flat, centred on the item's place. The picture
     * is widthOrig by heightOrig pixels, of which the texture holds the trimmed rectangle at
     * (left, top), wide by high, between the texels (uStart, vStart) and (uEnd, vEnd). The game
     * draws a pixel scaleX across and scaleY down; a tile's diagonal across its screen is
     * 64 times the tile scale in pixels. The picture's right runs at degrees from x towards y.
     */
    static void corners(float widthOrig, float heightOrig, float left, float top, float wide, float high, float scaleX, float scaleY,
            float tileScale, float degrees, float uStart, float vStart, float uEnd, float vEnd, float[] out) {
        float perPixel = SpriteGeometry.TILES_PER_PIXEL_ACROSS / tileScale;
        double turn = Math.toRadians(degrees);
        float cos = (float) Math.cos(turn), sin = (float) Math.sin(turn);
        for (int corner = 0; corner < 4; corner++) {
            boolean right = corner == 1 || corner == 2, low = corner >= 2;
            float across = (left + (right ? wide : 0f) - widthOrig / 2f) * scaleX * perPixel;
            float down = (top + (low ? high : 0f) - heightOrig / 2f) * scaleY * perPixel;
            int at = corner * CORNER_FLOATS;
            out[at] = across * cos - down * sin;
            out[at + 1] = across * sin + down * cos;
            out[at + 2] = right ? uEnd : uStart;
            out[at + 3] = low ? vEnd : vStart;
        }
    }

    /**
     * What the game multiplies an item's model by to draw it on the ground: its item camera
     * stands the model in the game's view half as large again, as it does a person.
     */
    static final float MODEL_SCALE = 1.5f;

    /**
     * Scales a placing from placing about the item's place so that the model, whose points are
     * places (three floats each), is length tiles long along its longest own axis.
     */
    static void toLength(float[] placing, float[] places, float length) {
        float longest = 0f;
        for (int axis = 0; axis < 3; axis++) {
            float low = Float.MAX_VALUE, high = -Float.MAX_VALUE;
            for (int i = axis; i < places.length; i += 3) {
                low = Math.min(low, places[i]);
                high = Math.max(high, places[i]);
            }
            longest = Math.max(longest, high - low);
        }
        float scale = (float) Math.sqrt(placing[0] * placing[0] + placing[4] * placing[4] + placing[8] * placing[8]);
        float by = length / (longest * scale);
        for (int i = 0; i < 12; i++) {
            placing[i] *= by;
        }
    }

    /** The world's x, y and up, by the turned model's x, its -z and its y. */
    private static final int[] PLACED_FROM = {0, 2, 1};
    private static final float[] PLACED_SIGN = {MODEL_SCALE, -MODEL_SCALE, MODEL_SCALE};

    /**
     * The matrix that takes a point of an item's model to the world, as three rows of four:
     * tiles along x, along y and up, from the place the item lies. The game draws the model
     * through its transform (kept by columns), turned about the model's x, y and z by the
     * angles, in degrees, in that order, and the game's view takes the model's x to the
     * world's x, its y up, and its z to the world's -y.
     */
    static void placing(float[] transform, float angleX, float angleY, float angleZ, float[] out) {
        double x = Math.toRadians(angleX), y = Math.toRadians(angleY), z = Math.toRadians(angleZ);
        float cx = (float) Math.cos(x), sx = (float) Math.sin(x), cy = (float) Math.cos(y), sy = (float) Math.sin(y);
        float cz = (float) Math.cos(z), sz = (float) Math.sin(z);
        // Rx * Ry * Rz, by rows.
        float[] turn = {
            cy * cz, -cy * sz, sy,
            sx * sy * cz + cx * sz, -sx * sy * sz + cx * cz, -sx * cy,
            -cx * sy * cz + sx * sz, cx * sy * sz + sx * cz, cx * cy};
        for (int row = 0; row < 3; row++) {
            int r = PLACED_FROM[row] * 3;
            for (int column = 0; column < 4; column++) {
                float sum = 0f;
                for (int k = 0; k < 3; k++) {
                    sum += turn[r + k] * transform[column * 4 + k];
                }
                out[row * 4 + column] = PLACED_SIGN[row] * sum;
            }
        }
    }
}

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
}

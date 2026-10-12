package com.cameraunlock.zomboid;

/** What lies flat and is no floor: which sprites are laid on the ground or stood as a low slab, and not as a card facing the eye. */
final class Flat {
    /** How far past its square, in tiles, something lying on the ground may reach: a sheet of litter is painted across the corner of the next. */
    static final float REACH = 0.5f;
    /** How many layers a block of squares two by two takes for what reaches past its squares: see layer. */
    static final int BLOCK_LAYERS = 4;
    /** How high the dark edge under a fur rug's pile is painted, in pixels at tile scale 1: see pile. */
    private static final float FUR_EDGE_PIXELS = 1.5f;
    /** The most a slab stands, in levels, and the least and most it is across, as half its side in tiles. */
    private static final float SLAB_HIGHEST = 0.24f, SLAB_NARROWEST = 0.2f, SLAB_WIDEST = 0.56f;

    private Flat() {}

    /** Whether the game draws a sprite with the floor, by the layer it gives it or by what it lets the player move it as. */
    static boolean lies(String renderLayer, String moveType) {
        return "Floor".equals(renderLayer) || "FloorRug".equals(moveType);
    }

    /**
     * The layer something that reaches past its square lies on, over a square whose floor has
     * taken the layers under floorLayer. Its piece and the next square's overlap along the seam
     * between them, and on one layer the two fight for depth there, so each square of a block two
     * by two has a layer of its own and no two squares that touch, at a side or a corner, share
     * one. A block's layers start at a multiple of BLOCK_LAYERS, and at least there, over the
     * floors round it while those take fewer: a piece over a floor that took more starts a block
     * higher, never level with a piece beside it.
     */
    static int layer(int floorLayer, int x, int y) {
        int block = Math.max(BLOCK_LAYERS, (floorLayer + BLOCK_LAYERS - 1) / BLOCK_LAYERS * BLOCK_LAYERS);
        return block + (x & 1) + 2 * (y & 1);
    }

    /**
     * How high the top of a rug stands over the floor, in levels. The game paints its fur rugs
     * (floors_rugs_02) as a block: the pile's top, and under it a dark edge 1.5 pixels high at
     * tile scale 1. Laid at the floor, a square takes that edge of the picture along two of its
     * sides, a dark line at every seam of the rug; laid at the pile's top it takes the pile.
     */
    static float pile(String name) {
        return name != null && name.startsWith("floors_rugs_02") ? FUR_EDGE_PIXELS / SpriteGeometry.PIXELS_PER_LEVEL : 0f;
    }

    /** Whether a box read off a sprite's outline, half its side in tiles and its height in levels, is a slab: low, and wide for its height. */
    static boolean isSlab(float half, float high) {
        return high <= SLAB_HIGHEST && half >= SLAB_NARROWEST && half <= SLAB_WIDEST && 2f * half >= 2.5f * high * ViewCamera.LEVEL_HEIGHT;
    }
}

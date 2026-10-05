package com.cameraunlock.zomboid;

/** What lies flat and is no floor: which sprites are laid on the ground or stood as a low slab, and not as a card facing the eye. */
final class Flat {
    /** How far past its square, in tiles, something lying on the ground may reach: a sheet of litter is painted across the corner of the next. */
    static final float REACH = 0.5f;
    /** The most a slab stands, in levels, and the least and most it is across, as half its side in tiles. */
    private static final float SLAB_HIGHEST = 0.24f, SLAB_NARROWEST = 0.2f, SLAB_WIDEST = 0.56f;

    private Flat() {}

    /** Whether the game draws a sprite with the floor, by the layer it gives it or by what it lets the player move it as. */
    static boolean lies(String renderLayer, String moveType) {
        return "Floor".equals(renderLayer) || "FloorRug".equals(moveType);
    }

    /** Whether a box read off a sprite's outline, half its side in tiles and its height in levels, is a slab: low, and wide for its height. */
    static boolean isSlab(float half, float high) {
        return high <= SLAB_HIGHEST && half >= SLAB_NARROWEST && half <= SLAB_WIDEST && 2f * half >= 2.5f * high * ViewCamera.LEVEL_HEIGHT;
    }
}

package com.cameraunlock.zomboid;

/**
 * The window the game's own view opens through trees round the player, which the overhead
 * picture opens in the same place, at the same size and with the same edge.
 *
 * The game draws media/mask_transparency_player.png into the stencil at the middle of its
 * screen less the camera's lead while aiming (IsoCell.drawStencilMask), each of its texels
 * Core.tileScale pixels of the game's zoomed picture, and a tree it marks (FBORenderCell
 * .isTranslucentTree) is drawn at 0.05 alpha where the stencil is set. The mask is an ellipse
 * twice as wide as high, solid to its middle and dithered to nothing at its edge. A texel is
 * the same share of a tile at every zoom and both tile scales, so the window is a fixed size
 * on the map and grows and shrinks on the screen as the view zooms. While a firearm is aimed
 * media/mask_transparency_cursor.png, a dithered circle, is drawn at the pointer as well.
 *
 * Which trees, and how much of each, is the game's own: every frame it eases a tree's
 * IsoTree.fadeAlpha down to 0.05, by 0.045 a thirtieth of a second, while it marks the tree
 * (on the camera's side of the player, or any tree while aiming or in a vehicle), and back up
 * to 1 when it stops, and it works that out in Medium and High as in Low. The picture reads
 * those numbers and draws that share of each tree's pixels in the window, the tree whole by
 * its own square.
 *
 * Sizes are in tiles along the screen: the length of a tile's diagonal there over the square
 * root of two, which is what FirstPerson.aimOverhead takes a tile on the screen to be.
 */
final class CanopyWindow {
    private CanopyWindow() {}

    /** Texels of a mask to a tile along the screen: a tile's diagonal is 64 tile-scaled pixels across. */
    static final float TEXELS_PER_TILE = (float) (32.0 * Math.sqrt(2.0));
    /** The player's mask: where half its texels let the stencil through, half across and half down. Measured off the game's picture. */
    static final float ACROSS = 373f / TEXELS_PER_TILE, DOWN = 187f / TEXELS_PER_TILE;
    /** The share of that ellipse at which its dither starts, and where it has thinned to nothing. */
    static final float EDGE_FROM = 0.85f, EDGE_TO = 1.135f;
    /** The pointer's mask while aiming: a circle, and its dither. */
    static final float AIMED = 182f / TEXELS_PER_TILE, AIMED_EDGE_FROM = 0.705f, AIMED_EDGE_TO = 1.29f;

    /** The most trees the game draws see-through that the picture is told of, nearest the player first, and how far from them it looks for them, in squares. */
    static final int TREES = 96, TREE_REACH = 20;

    /** The edges and the number of trees as the world shader has them. */
    static final String GLSL = "#define CANOPY_EDGE_FROM " + EDGE_FROM + "\n#define CANOPY_EDGE_TO " + EDGE_TO
            + "\n#define CANOPY_AIMED_FROM " + AIMED_EDGE_FROM + "\n#define CANOPY_AIMED_TO " + AIMED_EDGE_TO
            + "\n#define CANOPY_TREES " + TREES + "\n";

    /**
     * The window's half width, half height, the pointer's radius and the size of a texel of the
     * masks, in pixels of a picture that many pixels high, drawn by a camera with that half field
     * of view that far from the place it looks at.
     */
    static void inPixels(float pictureHigh, float tanHalfFieldOfView, float away, float[] out) {
        float perTile = pictureHigh / 2f / (tanHalfFieldOfView * away);
        out[0] = ACROSS * perTile;
        out[1] = DOWN * perTile;
        out[2] = AIMED * perTile;
        out[3] = perTile / TEXELS_PER_TILE;
    }
}

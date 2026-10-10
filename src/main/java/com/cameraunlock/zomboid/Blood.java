package com.cameraunlock.zomboid;

/**
 * The game's blood, as its own view draws it: where the picture of a splat on a floor lies, and
 * the colour and the cover a splat on a floor or a wall is drawn with. The numbers are the
 * game's (FBORenderCell.renderOneLevel_Blood and IsoSprite.renderBloodSplat for a floor,
 * IsoWallBloodSplat.render for a wall). The light the game multiplies in is the picture's own.
 */
final class Blood {
    /** Game hours blood takes to dry: from then on it is as dark and as faint as it gets. */
    static final float DRIES_IN_HOURS = 72f;
    /** How much of what is under it fresh blood covers, on a floor and on a wall, and the least a wall's ever does. */
    static final float FLOOR_COVER = 0.27f, WALL_COVER = 0.4f, WALL_COVER_LEAST = 0.15f;
    /** The colour blood on a wall is drawn in, red and then green and blue, before it dries. */
    static final float WALL_RED = 0.7f, WALL_GREEN_BLUE = 0.9f;
    /** Floats a corner of a splat laid on a floor takes: x and y on the map, in tiles, then its texel across and down. */
    static final int CORNER = 4;
    /** The most corners a piece of a splat cut to one square has: four of its own and one for each side of the square. */
    static final int MOST_CORNERS = 8;

    private Blood() {}

    /**
     * How fresh blood is, 1 just spilt and 0 dried, from how many game hours ago it was spilt.
     * The game dries it continuously; here it dries by the whole hour, so a chunk is built again
     * once an hour while its blood dries and not on every frame.
     */
    static float freshness(double hoursOld) {
        return hoursOld < 0.0 || hoursOld >= DRIES_IN_HOURS ? 0f : 1f - (float) Math.floor(hoursOld) / DRIES_IN_HOURS;
    }

    /** How much blood of this freshness keeps of its colour: it darkens as it dries. */
    static float darkness(float freshness) {
        return 0.2f + 0.8f * freshness;
    }

    static float floorCover(float freshness) {
        return FLOOR_COVER * (0.25f + 0.75f * freshness);
    }

    static float wallCover(float freshness) {
        return Math.max(WALL_COVER * (0.25f + 0.75f * freshness), WALL_COVER_LEAST);
    }

    /**
     * The colour the game draws a splat on a floor in, red into into[0] and green and blue,
     * which are always alike, into into[1]. Each splat is its own shade, worked out from where
     * on its chunk it is and of which kind, and darker the drier it is. Red can come out above 1:
     * the game multiplies its light in before it clamps. A shade that comes out no number, as it
     * does for a splat on the chunk's edge at x = 0, is packed by the game as none at all.
     *
     * @param x where on its chunk the splat is, along x, in tiles, as the game keeps it
     * @param y the same along y
     */
    static void floorTint(float x, float y, int type, float freshness, float[] into) {
        float kind = type + 1;
        float first = (x + y / x) * kind;
        float second = first * x / y * kind / (first + y);
        float third = second * first * second * x / (y + 2f);
        first = first * 42367.543f % 1000f / 1000f;
        second = second * 6367.123f % 1000f / 1000f;
        third = third * 23367.133f % 1000f / 1000f;
        if (first > 0.25f) {
            first = 0.25f;
        }
        float red = 1f - 2f * first + second / 3f, greenBlue = 1f - 2f * first - third / 3f;
        float dark = darkness(freshness);
        into[0] = Float.isNaN(red) ? 0f : red * dark;
        into[1] = Float.isNaN(greenBlue) ? 0f : greenBlue * dark;
    }

    /**
     * Puts the four corners of a splat's picture as the game lays it on its floor into corners,
     * round the picture from its top left as the game's view shows it. The game draws the
     * picture as a sprite on its screen with its top left half the picture's width and height,
     * at the tile scale, up and to the left of the splat's point, which it first rounds to a
     * whole pixel. Every point of that rectangle is the point of the floor the game's view shows
     * there, so the picture lies on the floor as a rectangle turned through 45 degrees. The
     * floor is taken at the splat's own height, which the game's view moves the point by and
     * the floor alike: it counts only in where the point is rounded to.
     *
     * @param x where on its chunk the splat is, along x, in tiles
     * @param y the same along y
     * @param z the splat's height, in levels
     * @param scale the game's tile scale
     * @param width the texture's width in pixels, the trimmed rectangle's, as the game draws it
     * @param offsetX where the trimmed rectangle starts in the sprite's frame, as the game adds it
     */
    static void laid(float x, float y, float z, int scale, float width, float height, float offsetX, float offsetY,
            float uStart, float vStart, float uEnd, float vEnd, float[] corners) {
        float across = SpriteGeometry.PIXELS_ACROSS_PER_TILE * scale, down = SpriteGeometry.PIXELS_DOWN_PER_TILE * scale;
        float up = SpriteGeometry.PIXELS_PER_LEVEL * scale;
        // IsoUtils.XToScreen and YToScreen, sum for sum, before the game rounds them.
        float left = (int) (0f + x * across - y * across) - width / 2f * scale + offsetX;
        float top = (int) (0f + y * down + x * down + (0f - z) * up) - height / 2f * scale + offsetY;
        float raised = z * up;
        corner(corners, 0, left, top + raised, across, down, uStart, vStart);
        corner(corners, 1, left + width, top + raised, across, down, uEnd, vStart);
        corner(corners, 2, left + width, top + height + raised, across, down, uEnd, vEnd);
        corner(corners, 3, left, top + height + raised, across, down, uStart, vEnd);
    }

    /** A corner from the pixel of the game's view that shows it, with the floor's height already taken off its row. */
    private static void corner(float[] corners, int index, float pixelU, float pixelV, float across, float down, float u, float v) {
        float minus = pixelU / across, plus = pixelV / down;
        int at = index * CORNER;
        corners[at] = (plus + minus) / 2f;
        corners[at + 1] = (plus - minus) / 2f;
        corners[at + 2] = u;
        corners[at + 3] = v;
    }

    /**
     * Cuts the picture of a splat laid as laid puts it to the square (squareX, squareY) and puts
     * what is left into piece, each corner's place given from the square's own corner, and says
     * how many corners that is: fewer than three where the picture misses the square. The map
     * from the floor to the picture's texels is a straight one, so a corner made by the cut takes
     * its texel by how far along the picture's edge it is, and the pieces on two squares meet
     * along the edge between them texel for texel.
     *
     * @param spare as long as piece, written through on the way
     */
    static int cut(float[] laid, float squareX, float squareY, float[] piece, float[] spare) {
        System.arraycopy(laid, 0, piece, 0, 4 * CORNER);
        int count = 4;
        count = clip(piece, count, spare, 0, squareX, true);
        count = clip(spare, count, piece, 0, squareX + 1f, false);
        count = clip(piece, count, spare, 1, squareY, true);
        count = clip(spare, count, piece, 1, squareY + 1f, false);
        for (int i = 0; i < count; i++) {
            piece[i * CORNER] -= squareX;
            piece[i * CORNER + 1] -= squareY;
        }
        return count;
    }

    /** Sutherland-Hodgman against one side of a square: what of the polygon in from is on the kept side of axis = limit goes into to. */
    private static int clip(float[] from, int count, float[] to, int axis, float limit, boolean keepAbove) {
        int out = 0;
        for (int i = 0; i < count; i++) {
            int at = i * CORNER, next = (i + 1) % count * CORNER;
            float here = from[at + axis] - limit, there = from[next + axis] - limit;
            boolean hereIn = keepAbove ? here >= 0f : here <= 0f, thereIn = keepAbove ? there >= 0f : there <= 0f;
            if (hereIn) {
                System.arraycopy(from, at, to, out++ * CORNER, CORNER);
            }
            if (hereIn != thereIn) {
                float share = here / (here - there);
                int into = out++ * CORNER;
                for (int k = 0; k < CORNER; k++) {
                    to[into + k] = from[at + k] + (from[next + k] - from[at + k]) * share;
                }
                to[into + axis] = limit;
            }
        }
        return out;
    }
}

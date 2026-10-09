package com.cameraunlock.zomboid;

/**
 * Cloth the map hangs out: a banner strung across a street, a banner on a lamp post's arm,
 * bunting and flags on a wall.
 *
 * The game has a banner across a street as one picture of it cut into a sprite for each
 * square it crosses, hung on the squares of air a floor up. Nothing says where any of them
 * stands, and where the banner rises to its fixings a length has no outline SpriteShape knows.
 *
 * The picture itself says where the banner is. Each sprite fills the half of its frame that a
 * sheet standing on its square's own edge would be painted in, the cable runs out at both ends
 * on the top of a wall a storey up, and one square's picture goes on where the last one's
 * stopped. So a run is one sheet in one plane, every square's length of it showing what its
 * sprite paints there, and it hangs from where its two ends are made fast: the land under the
 * squares between has no say, as it has none in a wire's span (Spans).
 *
 * Bunting and a flag two or three squares long are painted the same way, as a sheet on one
 * edge of their square, and the game marks them for no wall: they are laid on the edge they
 * are painted on.
 */
final class Banners {
    /** What a sprite is: none of these, a length of a banner strung across a street, a banner hung from a post's arm, or cloth on a wall of its square. */
    static final int NONE = 0, STRUNG = 1, POSTED = 2, ON_WALL = 3;

    /** The sheet the strung banner and the lamp posts' banners are on, and where on it they are: the cable's lengths past the cloth are part of the run. */
    private static final String SHEET = "signs_one-off_09_";
    private static final int STRUNG_FIRST = 312, STRUNG_LAST = 335, POSTED_FIRST = 336, POSTED_LAST = 337;

    /** A banner on a post's arm hangs in the plane through the post's axis, which stands on the middle of its square. */
    static final float POST_AXIS = 0.5f;
    /**
     * How much of the wind a strung banner takes for each tile it hangs below the line between
     * its two fixings, and the most: the cable swings a little and the cloth's foot more, and
     * nothing moves where it is made fast.
     */
    static final float SWAY_PER_TILE = 0.25f, MOST_SWAY = 0.7f;
    /** How much of the wind the foot of a banner on a post's arm takes: its head is on the arm. */
    static final float POSTED_SWAY = 0.3f;
    /** How many columns either side of an end's own the top of the cable is looked for in: the last dot of a line may be too faint to count. */
    private static final int END_COLUMNS = 3;

    /** The numbers of a run: the square it starts on and the one past its end along the way it runs, how high its cable is at each, in levels, and whether an end is not known for want of map. */
    static final int FROM = 0, TO = 1, TOP_FROM = 2, TOP_TO = 3, WANTING = 4, RUN = 5;

    /** The map a run is found on, on the one level it hangs on. */
    interface Hung {
        /** Whether a square holds a length of a strung banner that runs the way asked: 1, 0, or -1 for a square the map has not loaded or whose picture is not there to read. */
        int strung(int x, int y, boolean alongX);

        /** How high the top of what that length paints is at one end of its square, in levels over its floor: at the end towards the run's start, or the other. */
        float top(int x, int y, boolean alongX, boolean atStart);
    }

    private Banners() {}

    /** What a sprite is, by its name and what the game says of it: its CustomName and whether it is marked as fixed to a wall already. */
    static int kind(String name, String customName, boolean attached) {
        int which = name == null ? -1 : Sheets.place(name, SHEET);
        if (which >= STRUNG_FIRST && which <= STRUNG_LAST) {
            return STRUNG;
        }
        if (which >= POSTED_FIRST && which <= POSTED_LAST) {
            return POSTED;
        }
        return !attached && ("Bunting".equals(customName) || "Flag".equals(customName)) ? ON_WALL : NONE;
    }

    /**
     * Whether the sheet a sprite paints runs along x: it is then painted in the half of its
     * frame east of its square's north corner, on the square's north edge, and one that runs
     * along y in the other half, on the west edge.
     */
    static boolean alongX(SpriteGeometry.Frame frame) {
        return frame.left + frame.width / 2f > frame.originU;
    }

    /**
     * Where the frame's dot (u, v) is on a sheet that stands off of a tile from its square's
     * edge: how far along the sheet from the square's corner, in tiles, and how high over the
     * square's floor, in levels, into out.
     */
    static void place(SpriteGeometry.Frame frame, boolean alongX, float off, float u, float v, float[] out) {
        float across = (u - frame.originU) / (SpriteGeometry.PIXELS_ACROSS_PER_TILE * frame.scale);
        float along = alongX ? off + across : off - across;
        out[0] = along;
        out[1] = ((off + along) * SpriteGeometry.PIXELS_DOWN_PER_TILE * frame.scale + frame.originV - v) / (SpriteGeometry.PIXELS_PER_LEVEL * frame.scale);
    }

    /**
     * How high the topmost dot a sprite paints at one end of its square is, in levels over the
     * floor, on a sheet on the square's edge: where the cable leaves the square. NaN for a
     * sprite that paints nothing there.
     */
    static float top(SpriteShape.Painted painted, SpriteGeometry.Frame frame, boolean alongX, boolean atStart) {
        float tile = SpriteGeometry.PIXELS_ACROSS_PER_TILE * frame.scale;
        // Along x the square's start is the frame's middle and its end a tile to the right; along y the start is the middle and the end a tile to the left.
        int column = Math.round(frame.originU + (atStart ? 0f : alongX ? tile : -tile));
        int inward = atStart == alongX ? 1 : -1;
        if (atStart == alongX) {
            column = Math.max(column, (int) frame.left);
        } else {
            column = Math.min(column - 1, (int) (frame.left + frame.width) - 1);
        }
        float[] at = new float[2];
        for (int step = 0; step < END_COLUMNS; step++) {
            int u = column + inward * step;
            for (int v = (int) frame.top; v < frame.top + frame.height; v++) {
                if (painted.at(u, v)) {
                    place(frame, alongX, 0f, u + 0.5f, v, at);
                    return at[1];
                }
            }
        }
        return Float.NaN;
    }

    /**
     * The run the length on a square is part of, followed both ways along the way it runs to
     * the last square that holds a length that runs the same way. An end the map is not loaded
     * to is left where the map stops, and the run says so.
     */
    static float[] find(Hung map, int x, int y, boolean alongX) {
        int stepX = alongX ? 1 : 0, stepY = alongX ? 0 : 1, back = 0, on = 0, next;
        while ((next = map.strung(x - stepX * (back + 1), y - stepY * (back + 1), alongX)) == 1) {
            back++;
        }
        boolean wanting = next < 0;
        while ((next = map.strung(x + stepX * (on + 1), y + stepY * (on + 1), alongX)) == 1) {
            on++;
        }
        wanting |= next < 0;
        int firstX = x - stepX * back, firstY = y - stepY * back, lastX = x + stepX * on, lastY = y + stepY * on;
        float[] run = new float[RUN];
        run[FROM] = alongX ? firstX : firstY;
        run[TO] = (alongX ? lastX : lastY) + 1;
        run[TOP_FROM] = map.top(firstX, firstY, alongX, true);
        run[TOP_TO] = map.top(lastX, lastY, alongX, false);
        run[WANTING] = wanting ? 1f : 0f;
        return run;
    }

    static boolean wanting(float[] run) {
        return run[WANTING] != 0f;
    }

    /** How far along its run a place is, as a share: 0 where it starts and 1 at its end. */
    static float along(float[] run, float at) {
        return Math.clamp((at - run[FROM]) / (run[TO] - run[FROM]), 0f, 1f);
    }

    /**
     * How much of the wind a point of a strung banner takes: by how far it hangs below the
     * line between the two ends of its cable, in tiles. Nothing at the fixings, where the
     * cable is on that line, and most at the cloth's foot in the middle of the run.
     */
    static float sway(float[] run, float along, float levels) {
        float line = run[TOP_FROM] + (run[TOP_TO] - run[TOP_FROM]) * along;
        return Math.clamp((line - levels) * ViewCamera.LEVEL_HEIGHT * SWAY_PER_TILE, 0f, MOST_SWAY);
    }
}

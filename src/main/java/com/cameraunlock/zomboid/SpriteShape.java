package com.cameraunlock.zomboid;

/**
 * What a sprite the game says nothing about is a picture of, read off its outline.
 *
 * The game marks walls, floors, furniture and what hangs on a wall, and says where each
 * stands. A good many sprites carry no such mark: the slab over a shop front, a sill along a
 * wall, a rafter, a pipe run, the sheets of a billboard, a shop's sign. Stood up to face the
 * eye, as a thing of unknown shape has to be, each of those is drawn on the slant the game's
 * view saw it at, and a row of them is a row of tilted pieces that meet nowhere.
 *
 * Two shapes can be told from the outline alone, because the game's view shows every level
 * line running two pixels across for one down:
 *
 * A panel is a flat sheet standing on a line of the map's grid. Its outline is a parallelogram
 * with upright sides, its top and its foot both on that slant.
 *
 * A beam is a long box lying level along the grid. Its outline is the six-sided one of a box,
 * much longer one way than the other, and no deeper than it is wide.
 *
 * The view looks down a diagonal, so an outline does not say how far along that diagonal the
 * thing is: a sheet further back and higher up paints the same pixels. What settles it is
 * that these pieces are made to meet the next square's: one that is a square long starts at
 * its square's edge.
 */
final class SpriteShape {
    /** Whether the frame pixel (u, v) of a sprite is painted. */
    interface Painted {
        boolean at(int u, int v);
    }

    static final int CARD = 0, PANEL = 1, BEAM = 2;

    /** The share of a panel's columns whose top and foot must lie on its two slanted lines. */
    private static final float PANEL_ON_LINE = 0.86f;
    /** How far off its line a column's end may be, in frame pixels at tile scale 1: the outline is a staircase, and a frame adds a pixel. */
    private static final float PANEL_SLACK = 2.5f;
    /**
     * The least a panel stands, in levels, to be taken for one before a beam is looked for, and
     * the least a strip stands that is no beam: a row of letters along a wall. A beam's long
     * side is such a strip too, with the beam's end beside it.
     */
    private static final float PANEL_LOWEST = 0.25f, STRIP_LOWEST = 0.06f;
    /** The least a panel is long, in tiles. */
    private static final float PANEL_SHORTEST = 0.3f;
    /** From this long, in tiles, a panel is taken to run from the edge of its square. */
    static final float PANEL_WHOLE = 0.9f;
    /** The longest a panel in one square's sprite is: a frame is two tiles across. */
    private static final float PANEL_LONGEST = 1.2f;

    /** A beam's length, in tiles: about a square, so that it starts at the square's edge. */
    private static final float BEAM_SHORTEST = 0.85f, BEAM_LONGEST = 1.3f;
    /** The most a beam is across, in tiles, and deep, in levels, and the least. */
    private static final float BEAM_WIDEST = 0.5f, BEAM_DEEPEST = 0.3f, BEAM_NARROWEST = 0.08f, BEAM_SHALLOWEST = 0.015f;
    /** A beam is at least this many times as long as it is across. */
    private static final float BEAM_SLENDER = 2f;
    /** The least and most of a box's outline a beam's sprite paints: a round pipe paints less than the box round it. */
    private static final float BEAM_LEAST_PAINTED = 0.62f, BEAM_MOST_PAINTED = 1.12f;
    /** The most of the two corners a box leaves empty that a beam's sprite may paint. */
    private static final float BEAM_CORNER_PAINTED = 0.12f;
    /** The rows at the top of a sprite over which a box's topmost corner is looked for, in frame pixels at tile scale 1. */
    private static final float TOP_ROWS = 1.5f;

    static final SpriteShape UNKNOWN = new SpriteShape(CARD, false, false, 0f, 0f, 0f, 0f, 0f, 0f);

    final int kind;
    /** Whether a panel or a beam runs along the map's x, and stands at one y: otherwise along y. */
    final boolean alongX;
    /** For a panel: whether it is long enough to know where it starts. One that is not takes its place from the panel beside it. */
    final boolean whole;
    /**
     * For a panel that runs along x: (a0, b0) is where its uphill end stands on its square,
     * a1 where its other end is, h0 and h1 its foot and its top in levels. One that runs
     * along y has b0 to b1 the same way and stands at a0.
     *
     * For a beam: the box from (a0, b0, h0) to (a1, b1, h1).
     */
    final float a0, b0, h0, a1, b1, h1;

    private SpriteShape(int kind, boolean alongX, boolean whole, float a0, float b0, float h0, float a1, float b1, float h1) {
        this.kind = kind;
        this.alongX = alongX;
        this.whole = whole;
        this.a0 = a0;
        this.b0 = b0;
        this.h0 = h0;
        this.a1 = a1;
        this.b1 = b1;
        this.h1 = h1;
    }

    /** How many rows up from a sprite's lowest painted one its foot is looked for, in frame pixels at tile scale 1. */
    private static final int FOOT_ROWS = 2;

    /**
     * Where a sprite's foot is: into out the point (a, b) of the square's floor that the
     * middle of its lowest painted rows is drawn at, then how wide those rows are, in tiles
     * along a line of the grid. The game's view looks down from the south-east, so the lowest
     * thing a standing object paints is the corner of its foot nearest that view. False for a
     * sprite with nothing painted.
     */
    static boolean foot(Painted painted, SpriteGeometry.Frame frame, float[] out) {
        int left = Math.round(frame.left), top = Math.round(frame.top), width = Math.round(frame.width), height = Math.round(frame.height);
        int rows = Math.round(FOOT_ROWS * frame.scale);
        for (int row = height - 1; row >= 0; row--) {
            int first = -1, last = -1;
            for (int up = 0; up < rows && row - up >= 0; up++) {
                for (int column = 0; column < width; column++) {
                    if (painted.at(left + column, top + row - up)) {
                        first = first < 0 ? column : Math.min(first, column);
                        last = Math.max(last, column);
                    }
                }
                if (up == 0 && first < 0) {
                    break;
                }
            }
            if (first < 0) {
                continue;
            }
            float across = 32f * frame.scale, down = 16f * frame.scale;
            float difference = (left + (first + last + 1) / 2f - frame.originU) / across;
            float sum = (top + row + 1 - frame.originV) / down;
            out[0] = (sum + difference) / 2f;
            out[1] = (sum - difference) / 2f;
            out[2] = (last - first + 1) / across;
            return true;
        }
        return false;
    }

    /**
     * Reads the shape of the sprite whose painted pixels fill the rectangle of the frame
     * from (left, top), width across and height down, in whole pixels.
     */
    static SpriteShape read(Painted painted, SpriteGeometry.Frame frame) {
        int left = Math.round(frame.left), top = Math.round(frame.top), width = Math.round(frame.width), height = Math.round(frame.height);
        if (width < 4 || height < 4) {
            return UNKNOWN;
        }
        int[] first = new int[width], last = new int[width];
        int filled = 0, count = 0;
        for (int column = 0; column < width; column++) {
            first[column] = -1;
            for (int row = 0; row < height; row++) {
                if (painted.at(left + column, top + row)) {
                    if (first[column] < 0) {
                        first[column] = row;
                    }
                    last[column] = row;
                    count++;
                }
            }
            if (first[column] >= 0) {
                filled++;
            }
        }
        if (filled < width * PANEL_ON_LINE) {
            return UNKNOWN;
        }
        SpriteShape panel = panel(frame, left, top, width, height, first, last, PANEL_LOWEST);
        if (panel != null) {
            return panel;
        }
        SpriteShape beam = beam(painted, frame, left, top, width, height, count);
        if (beam != null) {
            return beam;
        }
        SpriteShape strip = panel(frame, left, top, width, height, first, last, STRIP_LOWEST);
        return strip != null ? strip : UNKNOWN;
    }

    private static SpriteShape panel(SpriteGeometry.Frame frame, int left, int top, int width, int height, int[] first, int[] last, float lowest) {
        float across = acrossPerTile(frame), level = perLevel(frame);
        float slack = PANEL_SLACK * frame.scale;
        // The slant takes this much of the sprite's height. What is left is how high the sheet stands.
        float standing = height - width / 2f;
        float length = width / across;
        if (standing < lowest * level || length < PANEL_SHORTEST || length > PANEL_LONGEST) {
            return null;
        }
        for (int way = 0; way < 2; way++) {
            // Down to the right for a sheet that runs along x, down to the left for one along y.
            boolean alongX = way == 0;
            int onLine = 0;
            for (int column = 0; column < width; column++) {
                if (first[column] < 0) {
                    continue;
                }
                float drop = (alongX ? column : width - 1 - column) / 2f;
                if (Math.abs(first[column] - drop) <= slack && Math.abs(last[column] - (drop + standing)) <= slack) {
                    onLine++;
                }
            }
            if (onLine < width * PANEL_ON_LINE) {
                continue;
            }
            boolean whole = length >= PANEL_WHOLE;
            if (alongX) {
                // u = (a - b0) * across + originU. Its uphill end is its left, at the square's edge.
                float b0 = (frame.originU - left) / across;
                float h1 = SpriteGeometry.heightDrawnAt(frame, 0f, b0, top + 0.5f);
                float h0 = SpriteGeometry.heightDrawnAt(frame, length, b0, top + height - 0.5f);
                return new SpriteShape(PANEL, true, whole, 0f, b0, h0, length, b0, h1);
            }
            // u = (a0 - b) * across + originU. Its uphill end is its right, at the square's edge.
            float a0 = (left + width - frame.originU) / across;
            float h1 = SpriteGeometry.heightDrawnAt(frame, a0, 0f, top + 0.5f);
            float h0 = SpriteGeometry.heightDrawnAt(frame, a0, length, top + height - 0.5f);
            return new SpriteShape(PANEL, false, whole, a0, 0f, h0, a0, length, h1);
        }
        return null;
    }

    /**
     * The same panel moved along the view's diagonal so that it stands at depth, the b of one
     * that runs along x and the a of one along y: for a piece too short to say where it
     * starts, which stands in the plane of the whole one beside it.
     */
    SpriteShape at(float depth) {
        float moved = depth - (alongX ? b0 : a0);
        // A step along the diagonal is a step along x, a step along y and a third of a level up.
        return new SpriteShape(kind, alongX, true, a0 + moved, b0 + moved, h0 + moved / 3f, a1 + moved, b1 + moved, h1 + moved / 3f);
    }

    private static SpriteShape beam(Painted painted, SpriteGeometry.Frame frame, int left, int top, int width, int height, int count) {
        float across = acrossPerTile(frame), down = across / 2f, level = perLevel(frame);
        // A box da by db by dh is drawn (da + db) tiles wide and that much slant plus dh deep.
        float both = width / across;
        float deep = (height - both * down) / level;
        if (deep < BEAM_SHALLOWEST || deep > BEAM_DEEPEST) {
            return null;
        }
        // The topmost corner of a box is over the far end of its edge along y.
        int rows = Math.max(1, Math.round(TOP_ROWS * frame.scale));
        float sum = 0f;
        int found = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < width; column++) {
                if (painted.at(left + column, top + row)) {
                    sum += column + 0.5f;
                    found++;
                }
            }
        }
        if (found == 0) {
            return null;
        }
        float db = sum / found / across, da = both - db;
        float longer = Math.max(da, db), shorter = Math.min(da, db);
        if (longer < BEAM_SHORTEST || longer > BEAM_LONGEST || shorter < BEAM_NARROWEST || shorter > BEAM_WIDEST || longer < BEAM_SLENDER * shorter) {
            return null;
        }
        // A box leaves the corners of its sprite empty: by its long side, two big ones.
        boolean alongX = da > db;
        float box = (float) width * height - (da * da + db * db) * across * down;
        if (count < box * BEAM_LEAST_PAINTED || count > box * BEAM_MOST_PAINTED) {
            return null;
        }
        float legAcross = longer * across, legDown = longer * down;
        int inCorners = 0, corners = 0;
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                // Along x the long edges run down to the right: the empty corners are the top
                // right and the bottom left. Along y they are the other two.
                float fromSide = alongX ? width - column - 0.5f : column + 0.5f;
                float fromTop = row + 0.5f, fromFoot = height - row - 0.5f;
                boolean topCorner = fromSide / legAcross + fromTop / legDown < 0.8f;
                float fromOther = alongX ? column + 0.5f : width - column - 0.5f;
                boolean footCorner = fromOther / legAcross + fromFoot / legDown < 0.8f;
                if (topCorner || footCorner) {
                    corners++;
                    if (painted.at(left + column, top + row)) {
                        inCorners++;
                    }
                }
            }
        }
        if (corners == 0 || inCorners > corners * BEAM_CORNER_PAINTED) {
            return null;
        }
        // About a square long, it lies from one edge of its square to the other.
        float a0, b0;
        float right = (left + width - frame.originU) / across;
        if (alongX) {
            a0 = (1f - da) / 2f;
            b0 = a0 + da - right;
        } else {
            b0 = (1f - db) / 2f;
            a0 = right + b0 - da;
        }
        float h1 = SpriteGeometry.heightDrawnAt(frame, a0, b0, top);
        return new SpriteShape(BEAM, alongX, true, a0, b0, h1 - deep, a0 + da, b0 + db, h1);
    }

    private static float acrossPerTile(SpriteGeometry.Frame frame) {
        return SpriteGeometry.frameU(frame, 1f, 0f) - SpriteGeometry.frameU(frame, 0f, 0f);
    }

    private static float perLevel(SpriteGeometry.Frame frame) {
        return SpriteGeometry.frameV(frame, 0f, 0f, 0f) - SpriteGeometry.frameV(frame, 0f, 0f, 1f);
    }
}

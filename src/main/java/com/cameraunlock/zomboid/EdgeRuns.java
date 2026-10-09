package com.cameraunlock.zomboid;

/**
 * A thing hung on a wall that is several squares long, a garage's sign across a gable: one
 * sprite a square, each painted where it meets the next in the game's picture, which is not
 * always its own square's stretch of the wall. The sign over a Muldraugh garage paints each
 * square's piece from 0.28 of a square before its square to 0.75 into it. Laid on its square's
 * stretch alone, every piece lost its first quarter and left the last quarter bare, and the
 * sign came out as strips with the wall between them and the letters stepping from strip to
 * strip. Such a piece is laid on the wall over all it paints, so the pieces meet again.
 *
 * Only a sign, and only where the piece on the next square along the same wall carries on
 * from it: a single thing that stands out from its wall is painted to one side of its fixing
 * for that, and laid flat over all it paints it would move along the wall.
 */
final class EdgeRuns {
    private EdgeRuns() {}

    /** How far apart, in tiles along the wall, two pieces may end and start and still meet: the sprites are trimmed to the pixel. */
    static final float MEET = 0.06f;
    /** How far past its square's stretch of wall a piece paints, in tiles, for it to be cut by it. */
    static final float PAST = 0.06f;

    /**
     * Into out, where along the edge from the square's corner (0, 0) to (da, db) the frame's
     * sprite paints when it is on that edge, in tiles from the corner: the nearer end, then the
     * further. 0 to 1 is the square's own stretch of wall.
     */
    static void along(SpriteGeometry.Frame frame, float da, float db, float[] out) {
        float start = SpriteGeometry.frameU(frame, 0f, 0f), per = SpriteGeometry.frameU(frame, da, db) - start;
        float one = (frame.left - start) / per, other = (frame.left + frame.width - start) / per;
        out[0] = Math.min(one, other);
        out[1] = Math.max(one, other);
    }

    /**
     * Into out, as WallHung.extent leaves it, how high the face a sign is laid on stands: from
     * its square's floor to the top of its level, and further down or up where the sprite paints
     * further. The board over a shop front is on the square of the level above and paints
     * below that level's floor: Mandy's Eatery from 0.10 of a level below it to 0.18 above,
     * resting on its awning. Cut at the floor, the board lost the lower half of its letters.
     */
    static void upright(SpriteGeometry.Frame frame, float da, float db, float[] out) {
        WallHung.extent(frame, da != 0f ? WallHung.NORTH : WallHung.WEST, out);
        out[WallHung.BOTTOM] = Math.min(0f, out[WallHung.BOTTOM]);
        out[WallHung.TOP] = Math.max(1f, out[WallHung.TOP]);
    }

    /**
     * How far up a sign whose foot is that far below its floor, in levels, is hung so that it
     * rests on the cloth of the awning on the level below, which meets the wall cloth levels up
     * that level (0 for no awning). The game draws the board over the awning, so its whole face
     * shows; the awning's cloth runs up to the wall above the board's foot, so where they are
     * stood together the cloth hides the foot of the lettering from the street. Mandy's
     * Eatery's foot is 0.10 below its floor and the cloth 0.03: hung as painted, the cloth hid
     * the lower quarter of the letters.
     */
    static float onAwning(float foot, float cloth) {
        return Math.max(0f, cloth - 1f - foot);
    }

    /** The sheets lettering and signs are on. A pipe, a gutter or a roof's edge carries on from square to square too, but is no sheet flat on the wall. */
    private static final String[] SIGN_SHEETS = {"signs_", "advertising_", "location_", "walls_decoration_"};

    /**
     * Whether a sprite is a sign or lettering hung on a wall: on a sheet of them, and named a
     * sign or nothing. A toilet, a shelf or a curtain on those sheets has a name of its own.
     */
    static boolean sign(String name, String customName) {
        if (name == null || customName != null && !customName.equals("Sign")) {
            return false;
        }
        for (String sheet : SIGN_SHEETS) {
            if (name.startsWith(sheet)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a piece painted from to to along its wall is cut by its square's stretch of it. */
    static boolean overhangs(float from, float to) {
        return from < -PAST || to > 1f + PAST;
    }

    /**
     * Whether a piece painted from to to along its wall, and one painted nextFrom to nextTo on
     * the next square the way the wall runs, are one thing: the second starts where the first
     * stops, and the square cuts one of them.
     */
    static boolean meets(float from, float to, float nextFrom, float nextTo) {
        return Math.abs(nextFrom + 1f - to) <= MEET && (overhangs(from, to) || overhangs(nextFrom, nextTo));
    }
}

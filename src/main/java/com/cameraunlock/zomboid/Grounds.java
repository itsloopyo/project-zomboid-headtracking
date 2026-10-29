package com.cameraunlock.zomboid;

/**
 * Ground that is laid level on purpose: a court's surface and a running track, and the ground
 * a game is played on, which the map marks only by what stands on it: a pitch by its goals, a
 * diamond by its bases, a court by its hoops, an arena by its jumps. What stands on such ground
 * stands level with it: a goal does not lean with the slope of its pitch, and none of its
 * squares stands higher than the next.
 *
 * Footings makes each into one made thing of its own (Footings.LEVEL), which Terrain lays
 * level at the height the land has under its middle, over the box round all of it, and eases
 * the land back in round the box (Terrain.GROUND_EASE). The things a game is played between
 * are joined across the open ground between them: a goal to the goal it faces down the pitch,
 * a base to the other bases, a hoop to the hoop at the other end of its court, a jump to the
 * others in its arena. The ground between them is no part of any thing, so a pitch is known
 * by the box round its goals, as wide as a pitch is for its length.
 *
 * Pure: what the sprites are, and how far each kind reaches for its partners.
 */
final class Grounds {
    private Grounds() {}

    private static final String SPORTS = "recreational_sports_01_", MILITARY = "location_military_generic_01_";

    /**
     * Nothing; a court's surface or a track; something stood on level ground that is joined only
     * to what touches it; a football goal whose crossbar runs along x, and one along y; a base, a
     * home plate or a pitcher's rubber; a basketball's pole or board; a show jump. The goalposts
     * of a gridiron are pieces: their field is a hundred squares long, too long a box to lay
     * level (Footings.boxed), so they stand level each on their own ground.
     */
    static final byte NONE = 0, SURFACE = 1, PIECE = 2, GOAL_ALONG_X = 3, GOAL_ALONG_Y = 4, BASE = 5, HOOP = 6, JUMP = 7;

    /**
     * How far each kind reaches across open ground for another of its kind, in squares: a goal
     * down the longest pitch on the map (74 between the goals), a base across a diamond, a hoop
     * down its court, a jump across its arena. From one in the loaded map a partner within reach
     * is within what MapAhead reads past it (MapAhead.BEYOND). A search that goes on from partner
     * to partner, along a row of pitches say, or that starts beyond the loaded map, may reach
     * chunks not yet read: it stops there, and is worked out again once they are.
     */
    private static final int[] REACH = {0, 0, 0, 80, 80, 30, 32, 24};

    /** What a sprite is to level ground. */
    static byte of(String name) {
        int sports = Sheets.place(name, SPORTS);
        if (sports >= 0) {
            return switch (sports) {
                case 22, 23 -> SURFACE;
                case 19, 20, 21 -> GOAL_ALONG_X;
                case 16, 17, 18 -> GOAL_ALONG_Y;
                case 0, 1, 2, 3, 4, 5, 6 -> BASE;
                case 24, 25, 26, 27, 32, 33 -> HOOP;
                case 120, 121, 122, 123, 124, 125 -> JUMP;
                // A gridiron's goalposts, a golf hole's flag, a tennis net, a boxing ring, a net across a court.
                case 7, 8, 9, 10, 11, 12, 13, 14, 15, 48, 49, 50, 51, 52, 53, 54, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93 -> PIECE;
                default -> NONE;
            };
        }
        // The army gym's climbing frames.
        int military = Sheets.place(name, MILITARY);
        return military >= 48 && military <= 63 ? PIECE : NONE;
    }

    /** How far a kind reaches for another of its kind, in squares. */
    static int reach(byte kind) {
        return REACH[kind];
    }

    /**
     * Whether a kind looks for its partners only straight along x or along y, and which: a goal
     * faces its partner square on, across the line of its crossbar; the others look over a
     * square round them.
     */
    static boolean looksAlongX(byte kind) {
        return kind == GOAL_ALONG_Y || kind == HOOP;
    }

    static boolean looksAlongY(byte kind) {
        return kind == GOAL_ALONG_X || kind == HOOP;
    }

    static boolean looksAround(byte kind) {
        return kind == BASE || kind == JUMP;
    }

    /** Whether ground marked by this kind is a pitch or a court whose width the box round it does not have: see Footings. */
    static boolean widened(byte kind) {
        return kind == GOAL_ALONG_X || kind == GOAL_ALONG_Y || kind == HOOP;
    }

    /** What a ground is in the list weight and order take: its box's first corner and last, and its footing. */
    static final int STRIDE = 5;

    /**
     * Puts grounds, count of them, in one order whatever order they were met in, so that what
     * weight sums comes out the same to the last bit from every chunk. Grounds whose boxes
     * overlap have one footing already, their ruler's (Footings.ruler).
     */
    static void order(float[] grounds, int count) {
        float[][] each = new float[count][];
        for (int at = 0; at < count; at++) {
            each[at] = java.util.Arrays.copyOfRange(grounds, at * STRIDE, at * STRIDE + STRIDE);
        }
        java.util.Arrays.sort(each, (a, b) -> {
            for (int k = 0; k < STRIDE; k++) {
                int order = Float.compare(a[k], b[k]);
                if (order != 0) {
                    return order;
                }
            }
            return 0;
        });
        for (int at = 0; at < count; at++) {
            System.arraycopy(each[at], 0, grounds, at * STRIDE, STRIDE);
        }
    }

    /**
     * How far the land at (x, y) keeps its own height by the grounds laid level round it, from
     * 0 to 1, with the footing it is drawn to left in pad[0]. In a box, 0 and that ground's
     * footing. Outside, each ground within ease draws it the more the nearer it is, 1 less
     * smooth(away / ease), and the footing is theirs each by its draw over the square of how far
     * off it is, so that it comes to a ground's own footing at the edge of its box with no
     * step, also between two grounds at different heights. 1 and 0 with none near.
     */
    static float weight(float[] grounds, int count, float x, float y, float ease, float[] pad) {
        float keeps = 1f, sum = 0f, weights = 0f;
        for (int at = 0; at < count * STRIDE; at += STRIDE) {
            float dx = Math.max(Math.max(grounds[at] - x, x - grounds[at + 2]), 0f), dy = Math.max(Math.max(grounds[at + 1] - y, y - grounds[at + 3]), 0f);
            float squared = dx * dx + dy * dy;
            if (squared == 0f) {
                pad[0] = grounds[at + 4];
                return 0f;
            }
            float away = (float) Math.sqrt(squared);
            if (away >= ease) {
                continue;
            }
            float draws = 1f - Terrain.smooth(away / ease);
            keeps *= 1f - draws;
            sum += grounds[at + 4] * draws / squared;
            weights += draws / squared;
        }
        pad[0] = weights == 0f ? 0f : sum / weights;
        return keeps;
    }

    /** How wide a pitch or a court is for its length. */
    static final float WIDTH_FOR_LENGTH = 0.6f;
    /** How far past what marks it level ground reaches, in squares: the run-off round a pitch, the apron round a court. */
    static final int MARGIN = 2;
}

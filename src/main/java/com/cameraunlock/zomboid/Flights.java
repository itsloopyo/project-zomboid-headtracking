package com.cameraunlock.zomboid;

/**
 * The parts of a flight of stairs that are not its treads, and the rails of a landing, built
 * as the things they are: strings down both sides of an open flight, the posts and the beam
 * that carry its head, a handrail that follows the flight on posts or balusters that stand on
 * the treads, and a level rail along the edge of a floor.
 *
 * Everything is given in a frame of its own and the caller says where that frame is on the
 * map. For a flight: along it in squares from its foot, 0 to 3, across it from 0 to 1, and up
 * in levels above the floor it stands on. For a level rail: along the edge of its square from
 * 0 to 1, across the edge with the square on the positive side, and up in levels. Widths are
 * tiles, and a thickness that stands upright is tiles too, turned into levels here.
 */
final class Flights {
    /** Takes one face: four corners, each along, across, up. */
    interface Sink {
        void quad(float[] corners);
    }

    /** How high a thing a tile thick stands, in levels. */
    private static final float UP = 1f / ViewCamera.LEVEL_HEIGHT;

    /** How thick an open tread is, in levels: the lip its sprite paints along its front. */
    static final float TREAD = 0.35f * Stairs.RISE;
    /**
     * A string of an open flight: how far in from the side of the flight it is thick, and how
     * far under and over the line of the nosings it reaches, in levels. Thicker than half a
     * wall by enough that the string of a flight along a wall stands clear of the wall's skin.
     */
    static final float STRING_THICK = 0.06f, STRING_OVER = -0.15f * Stairs.RISE, STRING_UNDER = 1.7f * Stairs.RISE;
    /** The posts under an open flight, the beam across them and the brace up to the string: how thick, in tiles. */
    static final float POST = 0.07f, BEAM = 0.06f, BRACE = 0.045f;
    /** How far below the head of its post a brace starts, in levels, and how far down the flight it meets the string, in squares. */
    private static final float BRACE_DROP = 0.3f, BRACE_REACH = 0.6f;

    /** How high over the line of the nosings the top of a handrail runs, in levels, by Rails style. */
    private static final float[] RAIL_OVER = {0.41f, 0.34f, 0.32f};
    /** A handrail: how wide and how deep, in tiles, by style. */
    private static final float[] RAIL_WIDE = {0.07f, 0.055f, 0.07f}, RAIL_DEEP = {0.07f, 0.04f, 0.05f};
    /** What a handrail stands on: how thick a post is and how thick a baluster, in tiles, by style. */
    private static final float[] RAIL_POST = {0.06f, 0.045f, 0.08f}, RAIL_BALUSTER = {0f, 0.024f, 0.035f};
    /** Balusters to a square of level rail, by style. */
    private static final int[] BALUSTERS = {0, 4, 5};
    /** A balustrade's bottom rail: how high its top is, in levels, and how deep it is, in tiles. */
    private static final float SHOE_UP = 0.07f, SHOE_DEEP = 0.04f;
    /** How far up the flight from its foot the axis of the first post is, in squares. It stands on the floor, through the front of the first tread. */
    private static final float FOOT = 0.04f;
    /** How far into what it stands on the foot of an upright goes, in levels: level with it, the two are drawn through each other. */
    private static final float SUNK = 0.004f;
    /** How much of an eight sided section's width a square inside it has. */
    private static final float INSIDE = 0.7f;

    private final float[] q = new float[12];
    private final Sink sink;

    Flights(Sink sink) {
        this.sink = sink;
    }

    /** The height of the line through the nosings of a flight, in levels. */
    static float nosings(float along) {
        return along / 3f + Stairs.RISE;
    }

    /** How high the top of a level rail of this style is over its floor: where the rail of a flight of the same style arrives at the head of the flight. */
    static float levelTop(int style) {
        return Stairs.RISE + RAIL_OVER[style];
    }

    private void corner(int corner, float along, float across, float up) {
        q[corner * 3] = along;
        q[corner * 3 + 1] = across;
        q[corner * 3 + 2] = up;
    }

    private void quad(float s0, float c0, float h0, float s1, float c1, float h1, float s2, float c2, float h2, float s3, float c3, float h3) {
        corner(0, s0, c0, h0);
        corner(1, s1, c1, h1);
        corner(2, s2, c2, h2);
        corner(3, s3, c3, h3);
        sink.quad(q);
    }

    /** A block with level and upright faces. */
    private void box(float s0, float s1, float c0, float c1, float h0, float h1) {
        quad(s0, c0, h1, s1, c0, h1, s1, c1, h1, s0, c1, h1);
        quad(s0, c0, h0, s1, c0, h0, s1, c1, h0, s0, c1, h0);
        quad(s0, c0, h0, s1, c0, h0, s1, c0, h1, s0, c0, h1);
        quad(s0, c1, h0, s1, c1, h0, s1, c1, h1, s0, c1, h1);
        quad(s0, c0, h0, s0, c1, h0, s0, c1, h1, s0, c0, h1);
        quad(s1, c0, h0, s1, c1, h0, s1, c1, h1, s1, c0, h1);
    }

    /**
     * A bar that slopes along the flight: its top runs from top0 at s0 to top1 at s1, it is
     * deep0 and deep1 levels deep at its ends, and its ends are cut upright.
     */
    private void bar(float s0, float top0, float deep0, float s1, float top1, float deep1, float c0, float c1) {
        quad(s0, c0, top0, s1, c0, top1, s1, c1, top1, s0, c1, top0);
        quad(s0, c0, top0 - deep0, s1, c0, top1 - deep1, s1, c1, top1 - deep1, s0, c1, top0 - deep0);
        quad(s0, c0, top0 - deep0, s1, c0, top1 - deep1, s1, c0, top1, s0, c0, top0);
        quad(s0, c1, top0 - deep0, s1, c1, top1 - deep1, s1, c1, top1, s0, c1, top0);
        quad(s0, c0, top0 - deep0, s0, c1, top0 - deep0, s0, c1, top0, s0, c0, top0);
        quad(s1, c0, top1 - deep1, s1, c1, top1 - deep1, s1, c1, top1, s1, c0, top1);
    }

    /** The top of an open flight's string, and its underside, which stops at the floor. */
    static float stringTop(float along) {
        return nosings(along) + STRING_OVER;
    }

    static float stringUnder(float along) {
        return Math.max(0f, nosings(along) - STRING_UNDER);
    }

    /**
     * One square of an open flight, less the tops and the front lips of its treads, which the
     * caller lays with what the sprite paints on them: the undersides and backs of the four
     * treads, and a string down each side that the treads sit between. Where the square has a
     * floor under it and is the middle or the top one of its flight, two posts stand under its
     * upper end with a beam across them, and under the middle square a brace runs from each
     * post back up to the string. A side that another flight is joined to, two flights side by
     * side being one stair twice as wide, has no string and no post: the treads run on.
     */
    void open(int third, boolean stands, boolean joinedLow, boolean joinedHigh) {
        float low = joinedLow ? 0f : STRING_THICK, high = joinedHigh ? 1f : 1f - STRING_THICK;
        for (int step = 0; step < Stairs.STEPS; step++) {
            float front = third + step * Stairs.GOING, back = front + Stairs.GOING, top = Stairs.tread(third, step), under = top - TREAD;
            quad(front, low, under, back, low, under, back, high, under, front, high, under);
            quad(back, low, under, back, high, under, back, high, top, back, low, top);
        }
        for (int side = 0; side <= 1; side++) {
            if (side == 0 ? joinedLow : joinedHigh) {
                continue;
            }
            float c0 = side == 0 ? 0f : 1f - STRING_THICK, c1 = c0 + STRING_THICK;
            for (int step = 0; step < Stairs.STEPS; step++) {
                float s0 = third + step * Stairs.GOING, s1 = s0 + Stairs.GOING;
                bar(s0, stringTop(s0), stringTop(s0) - stringUnder(s0), s1, stringTop(s1), stringTop(s1) - stringUnder(s1), c0, c1);
            }
        }
        if (!stands || third == 0) {
            return;
        }
        float head = third + 1f, carried = stringUnder(head);
        for (int side = 0; side <= 1; side++) {
            if (side == 0 ? joinedLow : joinedHigh) {
                continue;
            }
            float c0 = side == 0 ? 0f : 1f - POST, c1 = c0 + POST;
            box(head - POST, head, c0, c1, 0f, carried);
            if (third == 1) {
                float from = carried - BRACE_DROP, to = head - BRACE_REACH, deep = BRACE * UP * 1.5f;
                float b0 = side == 0 ? (POST - BRACE) / 2f : 1f - (POST + BRACE) / 2f;
                bar(to, stringUnder(to), deep, head - POST, from, deep, b0, b0 + BRACE);
            }
        }
        box(head - POST, head, joinedLow ? 0f : POST, joinedHigh ? 1f : 1f - POST, carried - BEAM * UP, carried);
    }

    /**
     * One square of a flight's handrail, centred on the line across the flight at `across`:
     * the rail itself over that square, and what it stands on. A rail of posts has one at the
     * foot of the flight, one at its head, and one on the middle of every second tread between. A rail of
     * balusters has a newel at the foot and at the head and a baluster on every tread. The
     * upright at the foot stands on the floor and every other on the tread under it.
     */
    void slope(int third, int style, float across) {
        float over = RAIL_OVER[style], deep = RAIL_DEEP[style] * UP, half = RAIL_WIDE[style] / 2f;
        float from = third == 0 ? FOOT - RAIL_POST[style] / 2f : third, to = third + 1f;
        float post = RAIL_POST[style] / 2f, into = over - deep / 2f;
        if (style == Rails.PIPE) {
            // A tube. A post's head is cut level at the height the middle of the rail has on
            // the post's axis, and from there to the post's edge the rail rises or falls less
            // than half its depth, so the whole head is inside the rail.
            tube(from, nosings(from) + over, to, nosings(to) + over, across, half);
            if (third == 0) {
                round(FOOT, post, across, 0f, nosings(FOOT) + into);
            }
            if (third == 2) {
                round(3f - post, post, across, 1f, nosings(3f - post) + into);
            }
            for (int step = 1; step < Stairs.STEPS; step += 2) {
                float at = third + (step + 0.5f) * Stairs.GOING;
                if (at < 3f - Stairs.GOING) {
                    round(at, post, across, Stairs.tread(third, step), nosings(at) + into);
                }
            }
            return;
        }
        bar(from, nosings(from) + over, deep, to, nosings(to) + over, deep, across - half, across + half);
        if (third == 0) {
            stand(FOOT, post, across, 0f, nosings(FOOT) + into);
        }
        if (third == 2) {
            stand(3f - post, post, across, 1f, nosings(3f - post) + into);
        }
        float thin = RAIL_BALUSTER[style] / 2f;
        for (int step = 0; step < Stairs.STEPS; step++) {
            float at = third + (step + 0.5f) * Stairs.GOING;
            if (at > FOOT + post + thin && at < 3f - 2f * post - thin) {
                stand(at, thin, across, Stairs.tread(third, step), nosings(at) + into);
            }
        }
    }

    /** The corners of an eight sided section with its flats level and upright: how far across and how far up each is, for a flat a distance 1 from the middle. */
    private static final float[] ROUND_ACROSS = new float[8], ROUND_UP = new float[8];

    static {
        double corner = 1.0 / Math.cos(Math.PI / 8.0);
        for (int side = 0; side < 8; side++) {
            double angle = Math.PI / 8.0 + side * Math.PI / 4.0;
            ROUND_ACROSS[side] = (float) (Math.cos(angle) * corner);
            ROUND_UP[side] = (float) (Math.sin(angle) * corner);
        }
    }

    /** A tube of eight sides that runs along the flight, its top from top0 at s0 to top1 at s1, its ends cut upright. */
    private void tube(float s0, float top0, float s1, float top1, float across, float radius) {
        float middle0 = top0 - radius * UP, middle1 = top1 - radius * UP;
        for (int side = 0; side < 8; side++) {
            int next = (side + 1) % 8;
            float c0 = across + ROUND_ACROSS[side] * radius, c1 = across + ROUND_ACROSS[next] * radius;
            float up0 = ROUND_UP[side] * radius * UP, up1 = ROUND_UP[next] * radius * UP;
            quad(s0, c0, middle0 + up0, s1, c0, middle1 + up0, s1, c1, middle1 + up1, s0, c1, middle0 + up1);
        }
        float in = radius * INSIDE, inUp = in * UP;
        quad(s0, across - in, middle0 - inUp, s0, across + in, middle0 - inUp, s0, across + in, middle0 + inUp, s0, across - in, middle0 + inUp);
        quad(s1, across - in, middle1 - inUp, s1, across + in, middle1 - inUp, s1, across + in, middle1 + inUp, s1, across - in, middle1 + inUp);
    }

    /** An upright tube of eight sides. Its foot and its head are each one level face inside the tube: the foot is sunk in what it stands on and the head is in what it carries. */
    private void round(float along, float radius, float across, float foot, float head) {
        foot -= SUNK;
        for (int side = 0; side < 8; side++) {
            int next = (side + 1) % 8;
            float s0 = along + ROUND_ACROSS[side] * radius, s1 = along + ROUND_ACROSS[next] * radius;
            float c0 = across + ROUND_UP[side] * radius, c1 = across + ROUND_UP[next] * radius;
            quad(s0, c0, foot, s1, c1, foot, s1, c1, head, s0, c0, head);
        }
        float in = radius * INSIDE;
        quad(along - in, across - in, foot, along + in, across - in, foot, along + in, across + in, foot, along - in, across + in, foot);
        quad(along - in, across - in, head, along + in, across - in, head, along + in, across + in, head, along - in, across + in, head);
    }

    /** An upright of square section, half thick each way from its axis, its foot sunk in what it stands on. */
    private void stand(float along, float half, float across, float foot, float head) {
        box(along - half, along + half, across - half, across + half, foot - SUNK, head);
    }

    /**
     * A level rail along the whole of one edge of a square, centred `across` from the edge. A rail of posts stands on one in the middle of the edge, as its sprite
     * has it, and its ends meet the rails of the edges beside it. A rail of balusters stands
     * on those, and a balustrade has a bottom rail they pass through.
     */
    void level(int style, float across) {
        float top = levelTop(style), deep = RAIL_DEEP[style] * UP, half = RAIL_WIDE[style] / 2f;
        if (style == Rails.PIPE) {
            tube(0f, top, 1f, top, across, half);
            round(0.5f, RAIL_POST[style] / 2f, across, 0f, top - deep / 2f);
            return;
        }
        box(0f, 1f, across - half, across + half, top - deep, top);
        if (style == Rails.BALUSTRADE) {
            // The balusters go through the bottom rail to the floor and carry it.
            float shoe = RAIL_BALUSTER[style] / 2f + 0.008f;
            box(0f, 1f, across - shoe, across + shoe, SHOE_UP - SHOE_DEEP * UP, SHOE_UP);
        }
        float thin = RAIL_BALUSTER[style] / 2f;
        for (int baluster = 0; baluster < BALUSTERS[style]; baluster++) {
            stand((baluster + 0.5f) / BALUSTERS[style], thin, across, 0f, top - deep);
        }
    }

    /** Half the width of a porch's post, in tiles: fixtures_railings_01_34 and 74 paint it 12 dots across. */
    private static final float PORCH_POST = 0.047f;

    /** The post of a porch's roof on the corner of two level rails, from the floor to the level over it. */
    void porchPost() {
        box(-PORCH_POST, PORCH_POST, -PORCH_POST, PORCH_POST, 0f, 1f);
    }

    /** The post at the corner of two level rails, on the corner itself. A post of pipe ends in the rails, and a timber one stands a little over them, as a newel does. */
    void cornerPost(int style) {
        float half = RAIL_POST[style] / 2f + 0.01f;
        if (style == Rails.PIPE) {
            round(0f, half, 0f, 0f, levelTop(style));
            return;
        }
        box(-half, half, -half, half, 0f, levelTop(style) + 0.03f);
    }
}

package com.cameraunlock.zomboid;

/**
 * Where on the edge of the first person picture a hit or a close zombie is marked, and how
 * strongly (HitDirection). Apart, so the tests use it without the game's classes.
 *
 * A bearing is the way to something from the eye, seen from above, against the way the view
 * faces: nought ahead, a quarter turn to the right, half a turn behind, a quarter turn back to
 * the left. It is marked on an ellipse just inside the edge of the picture, ahead at the top,
 * the right at the right edge, behind at the bottom.
 */
final class HitArcs {
    private HitArcs() {}

    /** How long the mark of a hit takes to fade out, in seconds. */
    static final float FADE_SECONDS = 1.5f;
    /** How near a zombie out of view is marked, in tiles. */
    static final float NEAR_TILES = 2f;
    /** Of a close zombie's mark, the share it has just out of the side of the view; behind, it has all of it. */
    static final float NEAR_AT_THE_SIDE = 0.35f;

    static final int TOP = 0, RIGHT = 1, BOTTOM = 2, LEFT = 3;
    static final String[] EDGE_NAMES = {"top", "right", "bottom", "left"};

    /**
     * From this far above or below level the view faces, in radians, the top of the picture
     * comes in to say which way is ahead, all the way by straight up or down.
     */
    static final double STEEP_FROM = Math.toRadians(60);

    /**
     * The bearing of a place from the eye, in radians from minus to plus a half turn. Ahead is
     * the way the view faces laid on the map, and the right a quarter turn clockwise from it on
     * the map, so a roll or a pitch of the head does not skew it. Past STEEP_FROM the facing laid
     * on the map shrinks to nothing, and the top of the picture laid on the map is eased in: as
     * it points when looking down, and turned round when looking up, where it points back. So
     * ahead is the way the view would face if it were tipped back level about its right, which
     * changes smoothly however far the view pitches; lying on the back looking up, the feet are
     * ahead. Near straight up or down, a roll of the head turns the view on the map as a turn
     * would, and so the bearings with it.
     *
     * @param forwardX the way the view faces, on the map and upward
     * @param upX      the way the top of the picture points, on the map
     * @param toX      the place less the eye, on the map
     */
    static float bearing(float forwardX, float forwardY, float forwardUp, float upX, float upY, float toX, float toY) {
        float top = topShare(forwardUp), aheadX = forwardX + top * upX, aheadY = forwardY + top * upY;
        return (float) Math.atan2(-toX * aheadY + toY * aheadX, toX * aheadX + toY * aheadY);
    }

    /** How much of the top of the picture is eased into ahead, turned round when looking up: see bearing. */
    private static float topShare(float forwardUp) {
        // A unit vector's part read back as a float can be a hair over one.
        double steep = Math.asin(Math.min(1f, Math.abs(forwardUp)));
        double t = Math.max(0.0, (steep - STEEP_FROM) / (Math.PI / 2 - STEEP_FROM));
        return (float) (t * t * (3 - 2 * t)) * (forwardUp > 0f ? -1f : 1f);
    }

    /**
     * While the character lies on the ground and the view follows its head, the arcs follow the
     * picture: where the top of the picture points back against ahead (on the back, looking up),
     * what is past the head is marked at the top and the feet at the bottom, the right staying the
     * right. The picture there is the world seen from below, the mirror of it seen from above, so
     * no frame turns smoothly from the one to the other: the top and the bottom change places at
     * once, and only once the top of the picture has turned this far back on the map, or as far
     * forward again to change them back, so a small movement of the head about the turn does not
     * swap them to and fro.
     */
    static final float TOP_TURNS = 0.35f;

    /**
     * How far the top of the picture points along ahead, laid on the map: 1 the same way, -1 back,
     * 0 across it or straight up, as on a level view. Ahead as bearing takes it.
     */
    static float topAlong(float forwardX, float forwardY, float forwardUp, float upX, float upY) {
        float top = topShare(forwardUp), aheadX = forwardX + top * upX, aheadY = forwardY + top * upY;
        float length = (float) Math.hypot(aheadX, aheadY);
        // Only a view upside down and straight up or down has no ahead: it holds what it had.
        return length == 0f ? 0f : (upX * aheadX + upY * aheadY) / length;
    }

    /** Whether the top of the picture now points back, from whether it did and topAlong: see TOP_TURNS. */
    static boolean topPointsBack(boolean did, float along) {
        return did ? along < TOP_TURNS : along < -TOP_TURNS;
    }

    /** A bearing with the top and the bottom changed places, the right and the left kept. */
    static float turnedOver(float bearing) {
        return (bearing >= 0f ? (float) Math.PI : (float) -Math.PI) - bearing;
    }

    /**
     * The squares a line of sight on one floor passes into after its first, as the game's
     * LosUtil.lineClear walks it: from the middle of the first square, one square at a time along
     * the axis it runs further on, y where both are as far, with the other axis taken from where
     * the line has got to, so a step can go diagonally across a corner. Written to xs and ys.
     *
     * @return how many
     */
    static int lineSquares(int fromX, int fromY, int toX, int toY, int[] xs, int[] ys) {
        int alongX = toX - fromX, alongY = toY - fromY, count = 0;
        if (Math.abs(alongX) > Math.abs(alongY)) {
            int step = alongX > 0 ? 1 : -1;
            float slope = (float) alongY / alongX * step, y = 0.5f + fromY;
            for (int x = fromX; x != toX; count++) {
                x += step;
                y += slope;
                xs[count] = x;
                ys[count] = (int) Math.floor(y);
            }
        } else if (alongY != 0) {
            int step = alongY > 0 ? 1 : -1;
            float slope = (float) alongX / alongY * step, x = 0.5f + fromX;
            for (int y = fromY; y != toY; count++) {
                y += step;
                x += slope;
                xs[count] = (int) Math.floor(x);
                ys[count] = y;
            }
        }
        return count;
    }

    /** Across and up the picture, -1 to 1, the way a bearing points from its middle. */
    static float across(float bearing) {
        return (float) Math.sin(bearing);
    }

    static float up(float bearing) {
        return (float) Math.cos(bearing);
    }

    /**
     * Which edge of the picture a bearing's mark is drawn nearest: it is drawn at across and up
     * the picture, each a share of half the picture's width and height, so the side wins where
     * it is the further across of the two.
     */
    static int edge(float bearing) {
        float x = across(bearing), y = up(bearing);
        if (Math.abs(x) >= Math.abs(y)) {
            return x >= 0f ? RIGHT : LEFT;
        }
        return y >= 0f ? TOP : BOTTOM;
    }

    /** How much of a hit's mark shows this long after the hit: all of it at the hit, easing out to none at FADE_SECONDS. */
    static float fade(float seconds) {
        if (seconds < 0f || seconds >= FADE_SECONDS) {
            return 0f;
        }
        float left = 1f - seconds / FADE_SECONDS;
        return left * left * (3f - 2f * left);
    }

    /** Whether a bearing is out of a picture whose half width is this tangent across from the view. */
    static boolean outOfView(float bearing, float tanHalfWide) {
        return Math.abs(bearing) > Math.atan(tanHalfWide);
    }

    /**
     * How strongly a zombie this far off at this bearing is marked: nought in view or further
     * than NEAR_TILES, more the nearer it is, most of all inside a tile, and the further round behind.
     */
    static float near(float tiles, float bearing, float tanHalfWide) {
        if (tiles > NEAR_TILES || !outOfView(bearing, tanHalfWide)) {
            return 0f;
        }
        float half = (float) Math.atan(tanHalfWide);
        float behind = (Math.abs(bearing) - half) / ((float) Math.PI - half);
        float off = tiles / NEAR_TILES;
        return (1f - off * off) * (NEAR_AT_THE_SIDE + (1f - NEAR_AT_THE_SIDE) * behind);
    }
}

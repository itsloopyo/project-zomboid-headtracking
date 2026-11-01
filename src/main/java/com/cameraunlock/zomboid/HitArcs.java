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
        // A unit vector's part read back as a float can be a hair over one.
        double steep = Math.asin(Math.min(1f, Math.abs(forwardUp)));
        double t = Math.max(0.0, (steep - STEEP_FROM) / (Math.PI / 2 - STEEP_FROM));
        float top = (float) (t * t * (3 - 2 * t)) * (forwardUp > 0f ? -1f : 1f);
        float aheadX = forwardX + top * upX, aheadY = forwardY + top * upY;
        return (float) Math.atan2(-toX * aheadY + toY * aheadX, toX * aheadX + toY * aheadY);
    }

    /**
     * The bearing of a place from the eye while the character lies on the ground and the view
     * follows its head, taken from the picture rather than from a facing: the place's way on the
     * map against the way the top of the picture and its right point on the map, so a place past
     * the head of a character on its back is marked at the top, as the picture shows it. A view
     * rolled onto its side, whose right points up, has its facing added in, so that what is
     * ahead is marked at the top and what is behind at the bottom.
     *
     * @param forwardX the way the view faces, the top of the picture points and its right points, on the map
     * @param toX      the place less the eye, on the map
     */
    static float bearingLying(float forwardX, float forwardY, float upX, float upY, float rightX, float rightY, float toX, float toY) {
        return (float) Math.atan2(toX * rightX + toY * rightY, toX * (upX + forwardX) + toY * (upY + forwardY));
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

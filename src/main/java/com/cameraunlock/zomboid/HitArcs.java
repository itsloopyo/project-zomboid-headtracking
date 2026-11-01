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

    /** Below this squared length of the view's facing laid on the map, the view looks too near straight up or down to face a way by it. */
    static final float STEEP = 0.04f;

    /**
     * The bearing of a place from the eye, in radians from minus to plus a half turn. Ahead is
     * the way the view faces laid on the map, and the right a quarter turn clockwise from it on
     * the map, so a roll or a pitch of the head does not skew it. A view that looks nearly
     * straight up or down, lying on the back or face down, faces no way on the map: ahead is
     * then the way the top of the picture points and the right the way the picture's right does,
     * which with the facing that steep both lie along the map. Facing, up and right are at right
     * angles, so where the facing is too steep to use, the other two are long enough.
     *
     * @param forwardX the way the view faces, the top of the picture points and the picture's right points, on the map
     * @param toX      the place less the eye, on the map
     */
    static float bearing(float forwardX, float forwardY, float upX, float upY, float rightX, float rightY, float toX, float toY) {
        boolean steep = forwardX * forwardX + forwardY * forwardY < STEEP;
        float aheadX = steep ? upX : forwardX, aheadY = steep ? upY : forwardY;
        float acrossX = steep ? rightX : -forwardY, acrossY = steep ? rightY : forwardX;
        return (float) Math.atan2(toX * acrossX + toY * acrossY, toX * aheadX + toY * aheadY);
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

package com.cameraunlock.zomboid;

/**
 * Whether a model can show in a picture, or shade anything that does: told from the ball the
 * model cannot reach out of and the five planes that bound the picture. Render thread only.
 */
final class PictureEdges {
    private PictureEdges() {}

    private static final float[] past = new float[5], pastBeyond = new float[5];

    /**
     * How far a point lies outside each of the five planes that bound a picture, in tiles, and
     * less than nought inside: behind the eye, then past the right, left, top and bottom edges.
     * The view's rows are the picture's own right, up and backward, so a rolled head and a view
     * turned up or down are in them.
     */
    static void outsideOf(float[] view, float wide, float high, float x, float y, float up, float[] out) {
        float right = view[0] * x + view[4] * y + view[8] * up + view[12];
        float above = view[1] * x + view[5] * y + view[9] * up + view[13];
        float ahead = -(view[2] * x + view[6] * y + view[10] * up + view[14]);
        float slantWide = (float) Math.sqrt(1f + wide * wide), slantHigh = (float) Math.sqrt(1f + high * high);
        out[0] = -ahead;
        out[1] = (right - ahead * wide) / slantWide;
        out[2] = (-right - ahead * wide) / slantWide;
        out[3] = (above - ahead * high) / slantHigh;
        out[4] = (-above - ahead * high) / slantHigh;
    }

    /** How long a shadow is still followed, in tiles: with the sun on the horizon it has no end. */
    private static final float SHADOW_FOLLOWED = 200f;

    /**
     * Whether nothing a model shades can be in a picture. Everything it shades lies
     * between the ball it cannot reach out of and where that ball's shadow meets the lowest
     * ground, along the light: left out when both ends of that are outside the same edge of
     * the picture.
     *
     * @param view the picture's view, with the tangents of half its width and half its height
     * @param map  the sun's map being cast, whose third row is the way to the light over the map's depth
     */
    static boolean shadesNothingSeen(float[] view, float wide, float high, float[] map, float x, float y, float up, float round) {
        float depth = (float) Math.sqrt(map[2] * map[2] + map[6] * map[6] + map[10] * map[10]);
        float lightX = -map[2] / depth, lightY = -map[6] / depth, lightUp = -map[10] / depth;
        // Down to the ground floor from above it, and to its own floor from below.
        float falls = Math.max(up, 0f) + round;
        float length = lightUp * SHADOW_FOLLOWED > falls ? falls / lightUp : SHADOW_FOLLOWED;
        outsideOf(view, wide, high, x, y, up, past);
        outsideOf(view, wide, high, x - lightX * length, y - lightY * length, up - lightUp * length, pastBeyond);
        for (int edge = 0; edge < 5; edge++) {
            if (past[edge] > round && pastBeyond[edge] > round) {
                return true;
            }
        }
        return false;
    }
}

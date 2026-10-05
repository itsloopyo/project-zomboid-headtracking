package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * The light of the open ground, found from the squares out of doors that have a light.
 *
 * A square the character has not seen has no light of its own, and is lit as the open ground
 * is. The mean of every seen square out of doors is not that light: a pool of lamplight is
 * out of doors too, and pulls the mean off what most squares have. Where seen squares and
 * unseen ones lie side by side, any difference between the two is a pattern of squares on
 * the ground. The light most seen squares agree on is the same as theirs.
 */
final class OpenLight {
    private static final int BANDS = 64;
    private final int[] count = new int[BANDS];
    private final float[] red = new float[BANDS], green = new float[BANDS], blue = new float[BANDS];
    private int counted;

    void clear() {
        Arrays.fill(count, 0);
        Arrays.fill(red, 0f);
        Arrays.fill(green, 0f);
        Arrays.fill(blue, 0f);
        counted = 0;
    }

    /** Counts one square's light, each part from 0 to 1. */
    void add(float r, float g, float b) {
        int band = Math.clamp((int) ((r + g + b) / 3f * BANDS), 0, BANDS - 1);
        count[band]++;
        red[band] += r;
        green[band] += g;
        blue[band] += b;
        counted++;
    }

    /** Makes another tally the same as this one. */
    void copyTo(OpenLight other) {
        System.arraycopy(count, 0, other.count, 0, BANDS);
        System.arraycopy(red, 0, other.red, 0, BANDS);
        System.arraycopy(green, 0, other.green, 0, BANDS);
        System.arraycopy(blue, 0, other.blue, 0, BANDS);
        other.counted = counted;
    }

    boolean isEmpty() {
        return counted == 0;
    }

    /**
     * The mean light of the squares in the band of brightness that holds most of them, into
     * out as red, green and blue. Of two bands as full as each other, the darker.
     */
    void commonest(float[] out) {
        int fullest = 0;
        for (int band = 1; band < BANDS; band++) {
            if (count[band] > count[fullest]) {
                fullest = band;
            }
        }
        if (count[fullest] == 0) {
            throw new IllegalStateException("no square's light was counted");
        }
        out[0] = red[fullest] / count[fullest];
        out[1] = green[fullest] / count[fullest];
        out[2] = blue[fullest] / count[fullest];
    }

    /** A band has to hold this share of the squares for its light to be the open ground's: fewer may be anything. */
    private static final float FLOOR_SHARE = 0.1f;

    /**
     * The light every square out of doors has, lamp or no lamp, into out: the mean of the
     * darkest band that holds a fair share of the squares. A lamp only adds to a square's
     * light, so the squares nothing shines on are the darkest, however many a carried torch
     * is lighting round the character. Where the squares are spread so thin that no band
     * holds that share, it is the light most of them have.
     */
    void floor(float[] out) {
        for (int band = 0; band < BANDS; band++) {
            if (count[band] >= counted * FLOOR_SHARE) {
                out[0] = red[band] / count[band];
                out[1] = green[band] / count[band];
                out[2] = blue[band] / count[band];
                return;
            }
        }
        commonest(out);
    }
}

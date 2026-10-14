package com.cameraunlock.zomboid;

/**
 * What the last pass makes of each frame's own light, worked out on the render thread from the
 * light meter's reading (Pipeline.meter): where the frame's dark and bright lie, and so how far
 * its blacks are taken down and its whole range stretched or held in, and the tint of its shade
 * and its lit parts by the light it is lit by. A painter sets the dark and the bright of every
 * picture again; a fixed curve sets them once, and a flat noon or an overcast day came out grey
 * from end to end while a sunset came out as it should.
 *
 * Every figure moves towards what the frame asks for over SETTLE_SECONDS, so a frame that sees a
 * window come into view does not jump.
 */
final class Develop {
    /** The meter's lights in log2, from LOWEST to LOWEST + SPAN, in BINS steps. */
    static final int BINS = 160;
    static final float LOWEST = -16f, SPAN = 24f;
    /** How much of the frame lies under the dark it is set by, and over the bright. */
    static final float DARK_SHARE = 0.02f, BRIGHT_SHARE = 0.99f;
    /** Where the dark and the bright come out, out of the film and before the style's curve: a near black, and short of white. */
    static final float DARK_SHOWN = 0.055f, BRIGHT_SHOWN = 0.9f;
    /** The most of the frame's dark taken away: the rest of it is kept, so a frame that is dark all over keeps its dark. */
    static final float MOST_TAKEN = 0.85f;
    /** How far the range is stretched, and how far held in where the bright would be lost. */
    static final float MOST_GAIN = 1.3f, LEAST_GAIN = 0.8f;
    /** At night the range is stretched by no more than this, so night stays dark, and never held in: a lit window is the brightest thing in a night. */
    static final float MOST_GAIN_AT_NIGHT = 1.12f;
    static final float SETTLE_SECONDS = 0.6f;
    /**
     * How fast less light is taken away. A frame's dark falls by stops at a doorway, and light
     * taken for the street outside, easing down over SETTLE_SECONDS, flattened a dark room for a
     * second and more; and a dark coat or doorway going in and out of the frame's darkest part
     * would make its blacks flicker if more were taken as fast. So less is taken at once, and
     * more only slowly.
     */
    static final float RELEASE_SECONDS = 0.03f;
    /** The film's white, as the develop shader's WHITE. */
    static final float WHITE = 3.2f;

    private final int[] counts = new int[BINS];
    /** Where the frame's dark and bright lie and its middle, log2 of the light before exposure. NaN before the first reading. */
    private float dark = Float.NaN, bright = Float.NaN, middle = Float.NaN;
    /** The light taken from every pixel's, before exposure, and the gain: what the shader is given. */
    private float taken, gain = 1f;
    private long settledAt;
    /** The cast of the frame's brightest light, red and blue against green in stops, and what red and blue are multiplied by against it. */
    private float castRed = Float.NaN, castBlue = Float.NaN, balanceRed = 1f, balanceBlue = 1f;
    /**
     * The part of the frame whose colour is taken as the light's, as shares of it from the dark
     * up: what the light falls on, brighter than most of the frame and short of the lamps
     * themselves, whose own colour is not the colour of what they light.
     */
    static final float CAST_FROM = 0.55f, CAST_TO = 0.95f;
    /** How much of a green or purple cast and of a warm or cool one is taken out indoors, and the most of either, in stops. */
    static final float GREEN_TAKEN = 0.8f, WARM_TAKEN = 0.25f, MOST_CAST = 0.4f;

    /**
     * Reads one frame of the meter: lights[i] is the log2 of a cell's light before exposure,
     * weights[i] how much it counts towards the eye's reading. Returns that reading.
     */
    float read(float[] lights, float[] weights, int cells) {
        java.util.Arrays.fill(counts, 0);
        double weighted = 0, weightSum = 0;
        for (int i = 0; i < cells; i++) {
            weighted += (double) lights[i] * weights[i];
            weightSum += weights[i];
            counts[bin(lights[i])]++;
        }
        dark = share(DARK_SHARE, cells);
        bright = share(BRIGHT_SHARE, cells);
        middle = share(0.5f, cells);
        return (float) (weighted / weightSum);
    }

    /**
     * Reads the colour of the frame's light: the mean colour of the frame between CAST_FROM and
     * CAST_TO, which in a room is the walls, the ceiling and the table the room's lamps light.
     * reds[i] and blues[i] are a cell's red and blue against its green, log2. Call after read.
     */
    void readCast(float[] lights, float[] reds, float[] blues, int cells) {
        // By bins, as the shares were read: a band of the frame all of one light, a plain wall
        // filling the view, lies in one bin and is counted whole.
        int from = bin(share(CAST_FROM, cells)), to = bin(share(CAST_TO, cells));
        double red = 0, blue = 0;
        int counted = 0;
        for (int i = 0; i < cells; i++) {
            int at = bin(lights[i]);
            if (at >= from && at <= to) {
                red += reds[i];
                blue += blues[i];
                counted++;
            }
        }
        castRed = (float) (red / counted);
        castBlue = (float) (blue / counted);
    }

    private static int bin(float light) {
        return Math.clamp((int) ((light - LOWEST) / SPAN * BINS), 0, BINS - 1);
    }

    /** The light, log2, under which this share of the cells lies, read off the counts between bins. */
    private float share(float of, int cells) {
        float wanted = of * cells;
        int sum = 0;
        for (int bin = 0; bin < BINS; bin++) {
            int next = sum + counts[bin];
            if (next >= wanted) {
                float within = counts[bin] == 0 ? 0f : (wanted - sum) / counts[bin];
                return LOWEST + (bin + within) * SPAN / BINS;
            }
            sum = next;
        }
        return LOWEST + SPAN;
    }

    /** The film's answer to light, as the develop shader's film() gives it for a grey. */
    static float film(float light) {
        return light * (1f + light / (WHITE * WHITE)) / (1f + light);
    }

    /** The light the film shows as this, out of the film and before gamma. */
    static float filmInverse(float shown) {
        float low = 0f, high = 64f;
        for (int i = 0; i < 40; i++) {
            float mid = 0.5f * (low + high);
            if (film(mid) < shown) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return 0.5f * (low + high);
    }

    /**
     * Moves the dark taken away and the gain towards what this frame asks for at this exposure.
     * strength is the style's (Style.levels), night 0 by day to 1 by night, fog how thick the air is:
     * fog is meant to be pale, and a night is meant to be dark, so both are let be more.
     */
    void settle(float exposure, float strength, float night, float fog, float indoors, long now) {
        float elapsed = settledAt == 0L ? 1f : Math.min((now - settledAt) / 1e9f, 1f);
        settledAt = now;
        float wantTaken = 0f, wantGain = 1f;
        if (!Float.isNaN(dark) && strength > 0f) {
            float darkLight = exposure * (float) Math.pow(2.0, dark);
            float brightLight = exposure * (float) Math.pow(2.0, bright);
            float darkWanted = (float) Math.pow(DARK_SHOWN, 2.2);
            float taking = Math.clamp(1f - darkWanted / Math.max(darkLight, 1e-6f), 0f, MOST_TAKEN);
            taking *= strength * (1f - 0.6f * Math.clamp(fog, 0f, 1f));
            wantTaken = taking;
            float rest = brightLight - taking * darkLight;
            float dark = Math.clamp(night, 0f, 1f);
            float most = MOST_GAIN + (MOST_GAIN_AT_NIGHT - MOST_GAIN) * dark;
            float least = LEAST_GAIN + (1f - LEAST_GAIN) * dark;
            float fits = filmInverse((float) Math.pow(BRIGHT_SHOWN, 2.2)) / Math.max(rest, 1e-6f);
            wantGain = 1f + (Math.clamp(fits, least, most) - 1f) * strength;
            wantTaken = taking * darkLight / exposure;
        }
        float step = 1f - (float) Math.exp(-elapsed / SETTLE_SECONDS);
        taken += (wantTaken - taken) * (wantTaken < taken ? 1f - (float) Math.exp(-elapsed / RELEASE_SECONDS) : step);
        gain += (wantGain - gain) * step;
        // Out of doors the light is the sky's and the sun's, and its colour is the hour's: it is left
        // as it is. Under a room's lamps the eye takes out most of a green or purple cast, which no
        // painter would keep, and little of a warm or cool one, which is the lamp's and the room's mood.
        float wantRed = 1f, wantBlue = 1f;
        if (!Float.isNaN(castRed) && indoors > 0f) {
            float there = Math.clamp(indoors, 0f, 1f);
            float green = -0.5f * (castRed + castBlue), warm = 0.5f * (castRed - castBlue);
            float greenTaken = Math.clamp(green * GREEN_TAKEN * there, -MOST_CAST, MOST_CAST);
            float warmTaken = Math.clamp(warm * WARM_TAKEN * there, -MOST_CAST, MOST_CAST);
            wantRed = (float) Math.pow(2.0, greenTaken - warmTaken);
            wantBlue = (float) Math.pow(2.0, greenTaken + warmTaken);
        }
        balanceRed += (wantRed - balanceRed) * step;
        balanceBlue += (wantBlue - balanceBlue) * step;
    }

    float balanceRed() {
        return balanceRed;
    }

    float balanceBlue() {
        return balanceBlue;
    }

    /** The light taken from every pixel's before the film, in exposed light: see settle. */
    float takenAt(float exposure) {
        return taken * exposure;
    }

    float gain() {
        return gain;
    }

    /** log2 of the frame's middle light before exposure, for the shader's local tone: NaN before the first reading. */
    float middle() {
        return middle;
    }

    float dark() {
        return dark;
    }

    float bright() {
        return bright;
    }

    /** Back to no reading, as when the pipeline's pictures are made again. */
    void forget() {
        dark = bright = middle = Float.NaN;
        castRed = castBlue = Float.NaN;
        balanceRed = balanceBlue = 1f;
        taken = 0f;
        gain = 1f;
        settledAt = 0L;
    }

    /**
     * The tints the shade and the lit parts take from the light: a clear day's sun warm and its
     * shade cool, by as much as the sun is high and the sky clear; under cloud neither; at night the
     * dark the blue of moonlight and what a lamp lights warm. A low sun colours the picture itself
     * and is let be. Into shade and lit, red, green and blue, each 1 where nothing is tinted.
     */
    static void tints(float sunUp, float cover, float night, float[] shade, float[] lit) {
        float day = 1f - Math.clamp(night, 0f, 1f);
        float high = smoothstep(0.12f, 0.45f, sunUp);
        float clear = 1f - Math.clamp(cover, 0f, 1f);
        float sunny = day * high * clear;
        float dark = 1f - day;
        shade[0] = 1f - 0.03f * sunny - 0.08f * dark;
        shade[1] = 1f - 0.01f * sunny - 0.02f * dark;
        shade[2] = 1f + 0.04f * sunny + 0.1f * dark;
        lit[0] = 1f + 0.035f * sunny + 0.05f * dark;
        lit[1] = 1f;
        lit[2] = 1f - 0.045f * sunny - 0.09f * dark;
    }

    /** How much more of an S an overcast day's picture takes, by day only: a grey sky lights everything alike. */
    static float overcastCurve(float cover, float night) {
        return 0.14f * Math.clamp(cover, 0f, 1f) * (1f - Math.clamp(night, 0f, 1f));
    }

    private static float smoothstep(float from, float to, float x) {
        float t = Math.clamp((x - from) / (to - from), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}

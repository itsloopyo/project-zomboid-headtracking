package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * How the mod's picture is developed, for each player at the screen: the Style row of
 * CameraUnlock.ini for the first player, SecondPlayerStyle and so on for the others. The light that reaches
 * the eye is the same in all of them, and so is what the world is built of. A style is what
 * the last pass makes of that light: how hard the contrast, how full the colours, what tint
 * the shade and the sunlit take, how dark the corners, and how much the lens adds of its own.
 *
 * The rows are read again while the game runs. The thread that watches the file sets them, the
 * logic thread goes to a player's next on the hotkey, and the render thread reads a view's as it draws its frame.
 */
enum Style {
    /** Strong contrast and full colours, cool in the shade and warm in the sun. */
    VIVID("Vivid", 0.34f, 1.08f, 0.32f, 0.1f, 1f, new float[] {0.96f, 0.99f, 1.05f}, new float[] {1.05f, 1.01f, 0.95f}, 1f, 0.3f, 0.18f, 0.6f, 0.35f),
    /** The colours as they are lit, with no tint, and less of the lens. */
    NATURAL("Natural", 0.18f, 1f, 0.16f, 0.07f, 0.5f, new float[] {1f, 1f, 1f}, new float[] {1f, 1f, 1f}, 0.9f, 0.3f, 0.14f, 0.5f, 0.2f),
    /** Washed out and flat under a faint drab cast, nearer the colours of the game's own view. */
    BLEAK("Bleak", 0.08f, 0.72f, 0.24f, 0.05f, 0f, new float[] {0.97f, 1f, 0.96f}, new float[] {1.03f, 1f, 0.94f}, 0.85f, 0.3f, 0.14f, 0.5f, 0.1f),
    /** Golden light and amber shade, as late on a summer afternoon, with a little more glow. */
    WARM("Warm", 0.22f, 1.04f, 0.22f, 0.09f, 0.8f, new float[] {1.04f, 0.99f, 0.9f}, new float[] {1.1f, 1.01f, 0.84f}, 0.9f, 0.3f, 0.15f, 0.5f, 0.25f),
    /** Steel blue shade and pale light, as on a winter morning, with the colours held back. */
    COLD("Cold", 0.24f, 0.88f, 0.22f, 0.06f, 0.4f, new float[] {0.9f, 0.98f, 1.1f}, new float[] {0.95f, 1f, 1.07f}, 0.9f, 0.3f, 0.16f, 0.5f, 0.15f);

    /** The row's word. Its number is its place here: Config describes the row with words(). */
    final String label;
    /** How far the picture is bent towards an S, dark made darker and bright brighter: 0 leaves it as the film gave it. */
    final float curve;
    /** How full the colours are: 1 as they came, under it towards grey. */
    final float saturation;
    /** How much darker the corners are than the middle. */
    final float vignette;
    /** How much of the glow round what is brighter than white is laid over the picture. */
    final float bloom;
    /** How much of the glare the sun leaves in the lens is shown. */
    final float flare;
    /** What the dark of the picture and the bright of it are multiplied by, red, green and blue. */
    final float[] shade, sunlit;
    /**
     * How far the frame's own dark and bright set its range (see Develop), 0 to 1; how far a part
     * of the picture is brought towards the frame's middle light; how far each pixel is set apart
     * from the light round it; how far edges are sharpened again after the steadying; and how much
     * more the weak colours are filled than the full. See the develop shader.
     */
    final float levels, compress, clarity, sharpen, vibrance;

    /** By player. Replaced whole when one changes, so a thread reading it sees one or the other. */
    private static volatile Style[] rows = new Style[View.COUNT];

    static {
        Arrays.fill(rows, BLEAK);
    }

    Style(String label, float curve, float saturation, float vignette, float bloom, float flare, float[] shade, float[] sunlit,
            float levels, float compress, float clarity, float sharpen, float vibrance) {
        this.label = label;
        this.curve = curve;
        this.saturation = saturation;
        this.vignette = vignette;
        this.bloom = bloom;
        this.flare = flare;
        this.shade = shade;
        this.sunlit = sunlit;
        this.levels = levels;
        this.compress = compress;
        this.clarity = clarity;
        this.sharpen = sharpen;
        this.vibrance = vibrance;
    }

    static synchronized void set(int player, int asked) {
        Style next = values()[asked];
        if (next == rows[player]) {
            return;
        }
        Style[] changed = rows.clone();
        changed[player] = next;
        rows = changed;
        Log.info("player " + (player + 1) + "'s picture style: " + next.label);
    }

    static Style of(int player) {
        return rows[player];
    }

    /** The words CameraUnlock.ini holds for the row, in the order of the styles, with commas between. */
    static String words() {
        StringBuilder words = new StringBuilder();
        for (Style each : values()) {
            words.append(words.isEmpty() ? "" : ",").append(each.label);
        }
        return words.toString();
    }

    /** The style the hotkey goes to from this one. */
    Style next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /**
     * A view's style as it is drawn, carried from the one it had to the one asked for over FADE_NANOS
     * so a change of style eases in rather than cutting. The render thread's alone, one per view.
     */
    static final class Fade {
        private static final long FADE_NANOS = 800_000_000L;
        float curve, saturation, vignette, bloom, flare, levels, compress, clarity, sharpen, vibrance;
        final float[] shade = new float[3], sunlit = new float[3];
        private final float[] from = new float[16], now = new float[16], to = new float[16];
        private Style toward;
        private long since;

        Fade toward(Style asked, long nanos) {
            if (toward == null) {
                pack(asked, from);
                since = nanos - FADE_NANOS;
            } else if (asked != toward) {
                System.arraycopy(now, 0, from, 0, now.length);
                since = nanos;
            }
            if (asked != toward) {
                toward = asked;
                pack(asked, to);
            }
            float t = Math.min(1f, Math.max(0f, (nanos - since) / (float) FADE_NANOS));
            float eased = t * t * (3f - 2f * t);
            for (int i = 0; i < now.length; i++) {
                now[i] = from[i] + (to[i] - from[i]) * eased;
            }
            curve = now[0];
            saturation = now[1];
            vignette = now[2];
            bloom = now[3];
            flare = now[4];
            levels = now[5];
            compress = now[6];
            clarity = now[7];
            sharpen = now[8];
            vibrance = now[9];
            System.arraycopy(now, 10, shade, 0, 3);
            System.arraycopy(now, 13, sunlit, 0, 3);
            return this;
        }

        private static void pack(Style style, float[] into) {
            into[0] = style.curve;
            into[1] = style.saturation;
            into[2] = style.vignette;
            into[3] = style.bloom;
            into[4] = style.flare;
            into[5] = style.levels;
            into[6] = style.compress;
            into[7] = style.clarity;
            into[8] = style.sharpen;
            into[9] = style.vibrance;
            System.arraycopy(style.shade, 0, into, 10, 3);
            System.arraycopy(style.sunlit, 0, into, 13, 3);
        }
    }
}

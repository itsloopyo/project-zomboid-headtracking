package com.cameraunlock.zomboid;

/**
 * How the mod's picture is developed: the Style row of CameraUnlock.ini. The light that reaches
 * the eye is the same in all of them, and so is what the world is built of. A style is what
 * the last pass makes of that light: how hard the contrast, how full the colours, what tint
 * the shade and the sunlit take, how dark the corners, and how much the lens adds of its own.
 *
 * The row is read again while the game runs. The thread that watches the file sets it, the
 * logic thread goes to the next on its hotkey, and the render thread reads it as it draws a frame.
 */
enum Style {
    /** Strong contrast and full colours, cool in the shade and warm in the sun. */
    VIVID("Vivid", 0.34f, 1.08f, 0.32f, 0.1f, 1f, new float[] {0.96f, 0.99f, 1.05f}, new float[] {1.05f, 1.01f, 0.95f}),
    /** The colours as they are lit, with no tint, and less of the lens. */
    NATURAL("Natural", 0.18f, 1f, 0.16f, 0.07f, 0.5f, new float[] {1f, 1f, 1f}, new float[] {1f, 1f, 1f}),
    /** Washed out and flat under a faint drab cast, nearer the colours of the game's own view. */
    BLEAK("Bleak", 0.08f, 0.72f, 0.24f, 0.05f, 0f, new float[] {0.97f, 1f, 0.96f}, new float[] {1.03f, 1f, 0.94f});

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

    private static volatile Style row = VIVID;

    Style(String label, float curve, float saturation, float vignette, float bloom, float flare, float[] shade, float[] sunlit) {
        this.label = label;
        this.curve = curve;
        this.saturation = saturation;
        this.vignette = vignette;
        this.bloom = bloom;
        this.flare = flare;
        this.shade = shade;
        this.sunlit = sunlit;
    }

    static void set(int asked) {
        Style next = values()[asked];
        if (next == row) {
            return;
        }
        row = next;
        Log.info("picture style: " + next.label);
    }

    static Style current() {
        return row;
    }

    /** The words CameraUnlock.ini holds for the row, in the order of the styles, with commas between. */
    static String words() {
        StringBuilder words = new StringBuilder();
        for (Style each : values()) {
            words.append(words.isEmpty() ? "" : ",").append(each.label);
        }
        return words.toString();
    }

    /** The style the hotkey goes to from the one the session is in. */
    Style next() {
        return values()[(ordinal() + 1) % values().length];
    }
}

package com.cameraunlock.zomboid;

/**
 * The colour a vehicle's interior is trimmed in: the dashboard, the doors, the seats and the
 * carpet take it, each in its own shade. A fleet's vehicles are trimmed alike; any other body's
 * colour is one of the colours cars were trimmed in in the eighties, chosen by its script so that
 * one model is always the same.
 */
final class CabinTrim {
    private CabinTrim() {}

    /** Navy, burgundy, tan, charcoal, dove grey, saddle brown, slate blue, as a screen shows them. */
    private static final float[][] COLOURS = {
        {0.26f, 0.30f, 0.42f}, {0.44f, 0.17f, 0.18f}, {0.66f, 0.56f, 0.42f}, {0.25f, 0.25f, 0.26f}, {0.56f, 0.56f, 0.57f}, {0.48f, 0.32f, 0.20f},
        {0.36f, 0.42f, 0.52f},
    };
    private static final float[] POLICE = {0.24f, 0.24f, 0.26f}, TAXI = {0.60f, 0.51f, 0.38f};

    /** The trim's colour for a vehicle of that script, linear, into out. */
    static void of(String script, float[] out) {
        float[] colour = script.contains("Police") || script.contains("Lights") ? POLICE
                : script.startsWith("Base.CarTaxi") ? TAXI
                : COLOURS[Math.floorMod(script.hashCode(), COLOURS.length)];
        for (int channel = 0; channel < 3; channel++) {
            out[channel] = (float) Math.pow(colour[channel], 2.2);
        }
    }
}

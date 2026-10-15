package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * The shape of a mouth from the voice coming into a microphone, a block of sound at a time.
 *
 * How loud the voice is against the room's own noise says how far the mouth is open at all: the
 * quietest the microphone has been lately is the room, and only sound well above it is a voice, so
 * a fan or a hum keeps the mouth shut. What is said shapes it. A vowel is the sound of the mouth's
 * own shape, and its two lowest resonances (formants) say what that shape is: the first is higher
 * the further the jaw is dropped (about 750 Hz for "ah", 300 for "ee" and "oo"), and the second is
 * higher the further forward the tongue is and the more spread the lips (about 2300 Hz for "ee"),
 * and lower when the lips are rounded (about 850 Hz for "oo" and "o"). They are read off the
 * spectrum's outline as linear prediction finds it. A hiss ("s", "f", "sh") has its sound in its
 * highest notes and none of a vowel's: the teeth are nearly together and the lips a little apart.
 *
 * The mouth opens quickly on a syllable and closes a little more slowly, as a jaw does, and the
 * lips take a little longer than the jaw to move between shapes.
 */
final class Voice {
    static final int RATE = 16000;
    /** Below this, in decibels of full scale, nothing is a voice however quiet the room is. */
    static final float QUIETEST = -55f;
    /** How far over the room's noise a sound has to be to open the mouth at all, and how much louder again opens it wide, in decibels. */
    static final float ABOVE_ROOM = 10f, WIDE_OVER = 25f;
    /**
     * How fast the room's noise is taken to be rising while nothing quieter is heard, in
     * decibels a second. Speech falls quiet between words far more often than the time this
     * takes to reach it.
     */
    static final float ROOM_RISES = 6f;
    /** How long the jaw takes to open and to close, and the lips to change shape, as time constants in seconds. */
    static final float OPENS = 0.02f, CLOSES = 0.07f, SHAPES = 0.05f;

    /** Samples the spectrum is read over: 32 ms, long enough for three periods of a low voice. */
    static final int WINDOW = 512;
    /** The order of the prediction: two coefficients for each resonance up to 8 kHz, and a few for the voice's own slope. */
    private static final int ORDER = 18;
    /** The spectrum's outline is looked at in steps of this many hertz, up to the highest second formant. */
    private static final float STEP_HZ = 20f, HIGHEST_HZ = 3200f;
    /** How far the jaw drops from the first formant: hardly at all at this frequency, and all the way at that one. */
    private static final float JAW_SHUT_HZ = 250f, JAW_WIDE_HZ = 750f, JAW_LEAST = 0.15f;
    /** The second formant over which the lips spread, and under which they round, if the jaw is up as it is for "o" and "oo". */
    private static final float SPREAD_FROM_HZ = 1800f, SPREAD_HZ = 2200f, ROUND_FROM_HZ = 1750f, ROUND_HZ = 1300f;
    private static final float ROUND_JAW_HZ = 420f, OPEN_JAW_HZ = 600f;
    /** How far the jaw drops for a hiss, and how far the lips spread, of all the way. */
    private static final float HISS_JAW = 0.12f, HISS_SPREAD = 0.35f;

    private final float[] heard = new float[WINDOW], shaded = new float[WINDOW];
    private final double[] r = new double[ORDER + 1], a = new double[ORDER + 1], before = new double[ORDER + 1];
    private final float[] outline = new float[(int) (HIGHEST_HZ / STEP_HZ) + 1];
    /** The room's noise in decibels, which the first block sets. */
    private float room = Float.NaN, open, lips;
    /** How far the jaw drops and how the lips are shaped for the last vowel read: before the first, half way and neither. */
    private float jawRead = 0.5f, shapeRead;

    /** How far the mouth is open now, 0 to 1. */
    float open() {
        return open;
    }

    /** How the lips are shaped now: spread towards 1, rounded and pushed out towards -1. */
    float lips() {
        return lips;
    }

    /** The loudness of a block of 16 bit samples, little end first, as their root mean square against full scale. */
    static float loudness(byte[] block, int bytes) {
        double sum = 0;
        int samples = bytes / 2;
        for (int i = 0; i < samples; i++) {
            int sample = (short) ((block[2 * i] & 0xff) | (block[2 * i + 1] << 8));
            sum += (double) sample * sample;
        }
        return samples == 0 ? 0f : (float) (Math.sqrt(sum / samples) / 32768.0);
    }

    /** The mouth after a block of 16 bit samples, little end first, which took that many seconds. */
    void step(byte[] block, int bytes, float seconds) {
        int samples = Math.min(bytes / 2, WINDOW);
        System.arraycopy(heard, samples, heard, 0, WINDOW - samples);
        for (int i = 0; i < samples; i++) {
            heard[WINDOW - samples + i] = (short) ((block[2 * i] & 0xff) | (block[2 * i + 1] << 8)) / 32768f;
        }
        float decibels = 20f * (float) Math.log10(Math.max(loudness(block, bytes), 1e-6f));
        room = Float.isNaN(room) || decibels < room ? decibels : room + ROOM_RISES * seconds;
        float gate = Math.max(QUIETEST, room + ABOVE_ROOM);
        float voice = Math.clamp((decibels - gate) / WIDE_OVER, 0f, 1f);
        float jaw = 0f, shape = 0f;
        if (voice > 0f) {
            float hiss = hiss();
            float[] formants = formants();
            // A block between two vowels often has no clear pair of formants: the mouth keeps the shape the last one gave it.
            if (formants != null) {
                float f1 = formants[0], f2 = formants[1];
                jawRead = JAW_LEAST + (1f - JAW_LEAST) * smooth(JAW_SHUT_HZ, JAW_WIDE_HZ, f1);
                float spread = smooth(SPREAD_FROM_HZ, SPREAD_HZ, f2);
                float round = smooth(ROUND_FROM_HZ, ROUND_HZ, f2) * smooth(OPEN_JAW_HZ, ROUND_JAW_HZ, f1);
                shapeRead = spread - round;
            }
            jaw = jawRead + (HISS_JAW - jawRead) * hiss;
            shape = shapeRead + (HISS_SPREAD - shapeRead) * hiss;
        }
        float wanted = voice * jaw;
        open += (wanted - open) * (1f - (float) Math.exp(-seconds / (wanted > open ? OPENS : CLOSES)));
        // The lips keep their shape through the quiet between syllables, and only lose it as the jaw closes.
        float lipsWanted = voice > 0f ? shape : lips * Math.min(open * 4f, 1f);
        lips += (lipsWanted - lips) * (1f - (float) Math.exp(-seconds / SHAPES));
    }

    /**
     * How much of the window is a hiss, 0 to 1: by how little each sample follows the one before.
     * A vowel's sound is mostly its low notes, so neighbouring samples are nearly alike; a hiss is
     * all high notes, and they are not.
     */
    private float hiss() {
        double same = 0, all = 0;
        for (int i = 1; i < WINDOW; i++) {
            same += heard[i] * heard[i - 1];
            all += heard[i] * heard[i];
        }
        return all <= 0 ? 0f : smooth(0.55f, 0.15f, (float) (same / all));
    }

    /**
     * The first two formants of the window in hertz, or null when its outline does not have two
     * peaks where a voice's are (a hiss, a breath, a sound that is not a vowel).
     */
    private float[] formants() {
        // Leaned towards the high notes first, which a voice is quieter in by about 6 dB an octave, and shaded at the ends.
        for (int i = 0; i < WINDOW; i++) {
            float leaned = i == 0 ? heard[0] : heard[i] - 0.97f * heard[i - 1];
            shaded[i] = leaned * (0.54f - 0.46f * (float) Math.cos(2 * Math.PI * i / (WINDOW - 1)));
        }
        for (int lag = 0; lag <= ORDER; lag++) {
            double sum = 0;
            for (int i = lag; i < WINDOW; i++) {
                sum += shaded[i] * shaded[i - lag];
            }
            r[lag] = sum;
        }
        if (r[0] <= 0) {
            return null;
        }
        r[0] *= 1.0 + 1e-6;
        // Levinson and Durbin: the prediction's coefficients a[1..ORDER], with a[0] = 1.
        Arrays.fill(a, 0);
        a[0] = 1;
        double error = r[0];
        for (int i = 1; i <= ORDER; i++) {
            double sum = r[i];
            for (int j = 1; j < i; j++) {
                sum += a[j] * r[i - j];
            }
            double k = -sum / error;
            System.arraycopy(a, 0, before, 0, i);
            for (int j = 1; j < i; j++) {
                a[j] = before[j] + k * before[i - j];
            }
            a[i] = k;
            error *= 1 - k * k;
            if (error <= 0) {
                return null;
            }
        }
        for (int at = 0; at < outline.length; at++) {
            double w = 2 * Math.PI * at * STEP_HZ / RATE, re = 0, im = 0;
            for (int k = 0; k <= ORDER; k++) {
                re += a[k] * Math.cos(w * k);
                im -= a[k] * Math.sin(w * k);
            }
            outline[at] = (float) (1 / (re * re + im * im));
        }
        float f1 = Float.NaN;
        for (int at = 1; at < outline.length - 1; at++) {
            if (outline[at] > outline[at - 1] && outline[at] >= outline[at + 1]) {
                float hz = at * STEP_HZ;
                if (Float.isNaN(f1)) {
                    if (hz >= 150f && hz <= 1100f) {
                        f1 = hz;
                    }
                } else if (hz >= f1 + 250f) {
                    return new float[] {f1, hz};
                }
            }
        }
        return null;
    }

    /** 0 at from, 1 at to, smoothly between, either way round. */
    private static float smooth(float from, float to, float x) {
        float t = Math.clamp((x - from) / (to - from), 0f, 1f);
        return t * t * (3 - 2 * t);
    }
}

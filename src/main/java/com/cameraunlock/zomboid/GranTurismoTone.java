package com.cameraunlock.zomboid;

import java.util.Locale;

/**
 * Polyphony Digital's GT7 tone mapping operator for an SDR screen, as their sample implementation
 * of 2025 has it (MIT, see THIRD-PARTY-NOTICES.md), with its ICtCp colour space. Light is taken per
 * colour through the GT curve, then its brightness is kept and its colour taken from the light as
 * it came in, faded out towards white near the top of the screen's range, and the two are blended.
 *
 * The operator works in linear Rec.2020 where 1 is 100 cd/m2 and white on the screen is 250 cd/m2.
 * The develop pass hands it linear Rec.709 light at the film's exposure, which is turned into
 * Rec.2020 and scaled by SCALE first, and what comes out is turned back into Rec.709.
 *
 * The develop shader's granTurismo() is written from the numbers here, so the tests that hold
 * map709 to the sample's output hold the shader to it too.
 */
final class GranTurismoTone {
    private GranTurismoTone() {}

    /** What the operator takes as 1, and white on an SDR screen, both in cd/m2. */
    private static final float REFERENCE_LUMINANCE = 100f, SDR_PAPER_WHITE = 250f;
    /** White on the screen in the operator's units. */
    static final float PEAK = SDR_PAPER_WHITE / REFERENCE_LUMINANCE;
    private static final float ALPHA = 0.25f, MID_POINT = 0.538f, LINEAR_SECTION = 0.444f, TOE_STRENGTH = 1.28f;
    private static final float BLEND_RATIO = 0.6f, FADE_START = 0.98f, FADE_END = 1.16f;

    private static final float K = (LINEAR_SECTION - 1f) / (ALPHA - 1f);
    private static final float KA = PEAK * LINEAR_SECTION + PEAK * K;
    private static final float KB = -PEAK * K * (float) Math.exp(LINEAR_SECTION / K);
    private static final float KC = -1f / (K * PEAK);

    private static final float M1 = 0.1593017578125f, M2 = 78.84375f, C1 = 0.8359375f, C2 = 18.8515625f, C3 = 18.6875f, PQ_TOP = 10000f;

    /** ITU-T T.302: Rec.2020 to LMS, PQ-encoded LMS to ICtCp, ICtCp back to PQ-encoded LMS, and LMS back to Rec.2020, as rows. */
    private static final float[][] TO_LMS = {{1688f / 4096f, 2146f / 4096f, 262f / 4096f}, {683f / 4096f, 2951f / 4096f, 462f / 4096f}, {99f / 4096f, 309f / 4096f, 3688f / 4096f}};
    private static final float[][] TO_ICTCP = {{0.5f, 0.5f, 0f}, {6610f / 4096f, -13613f / 4096f, 7003f / 4096f}, {17933f / 4096f, -17390f / 4096f, -543f / 4096f}};
    private static final float[][] FROM_ICTCP = {{1f, 0.00860904f, 0.11103f}, {1f, -0.00860904f, -0.11103f}, {1f, 0.560031f, -0.320627f}};
    private static final float[][] FROM_LMS = {{3.43661f, -2.50645f, 0.0698454f}, {-0.79133f, 1.9836f, -0.192271f}, {-0.0259499f, -0.0989137f, 1.12486f}};
    /** ITU-R BT.2087: linear Rec.709 to Rec.2020 and back, as rows. */
    private static final float[][] FROM_709 = {{0.6274040f, 0.3292820f, 0.0433136f}, {0.0690970f, 0.9195400f, 0.0113612f}, {0.0163916f, 0.0880132f, 0.8955950f}};
    private static final float[][] TO_709 = {{1.6604910f, -0.5876411f, -0.0728499f}, {-0.1245505f, 1.1328999f, -0.0083494f}, {-0.0181508f, -0.1005789f, 1.1187297f}};

    /** The brightness (I of ICtCp) of a grey at white on the screen. */
    static final float PEAK_UCS = ucs(new float[] {PEAK, PEAK, PEAK})[0];

    /**
     * How much brighter the film's light is made before the operator takes it: enough that a grey
     * of 0.18 is shown as the develop pass's own film shows it, so the exposure the eye settles on
     * means what it did. The operator shows white for less light than the film does, so the
     * develop pass's levels (Develop.settle) aim at grey and greyInverse while it is on.
     */
    static final float SCALE = bisect(x -> curve(0.18f * x) / PEAK, Develop.film(0.18f), 0f, 64f);

    /** The light the develop pass shows as Develop.DARK_SHOWN and as Develop.BRIGHT_SHOWN while the operator is on: Develop.settle's aims. */
    static final float DARK_LIGHT = greyInverse((float) Math.pow(Develop.DARK_SHOWN, 2.2)), BRIGHT_LIGHT = greyInverse((float) Math.pow(Develop.BRIGHT_SHOWN, 2.2));

    static float curve(float x) {
        if (x < 0f) {
            return 0f;
        }
        if (x < LINEAR_SECTION * PEAK) {
            float linear = smoothStep(x, 0f, MID_POINT);
            float toe = MID_POINT * (float) Math.pow(x / MID_POINT, TOE_STRENGTH);
            return (1f - linear) * toe + linear * x;
        }
        return KA + KB * (float) Math.exp(x * KC);
    }

    /** What the develop pass shows a grey of this light as, out of the operator and before gamma. */
    static float grey(float light) {
        return Math.min(curve(SCALE * light), PEAK) / PEAK;
    }

    /** The light the develop pass shows as this grey, under 1. */
    static float greyInverse(float shown) {
        return bisect(GranTurismoTone::grey, shown, 0f, 64f);
    }

    /** Linear Rec.2020 in the operator's units to what an SDR screen shows, 0 to 1 before its gamma. */
    static float[] map(float[] rgb) {
        float[] ucs = ucs(rgb);
        float[] skewed = {curve(rgb[0]), curve(rgb[1]), curve(rgb[2])};
        float[] skewedUcs = ucs(skewed);
        float chroma = 1f - smoothStep(ucs[0] / PEAK_UCS, FADE_START, FADE_END);
        float[] scaled = rgb(new float[] {skewedUcs[0], ucs[1] * chroma, ucs[2] * chroma});
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            float blended = (1f - BLEND_RATIO) * skewed[i] + BLEND_RATIO * scaled[i];
            out[i] = Math.min(blended, PEAK) / PEAK;
        }
        return out;
    }

    /** As the develop shader's granTurismo(): linear Rec.709 at the film's exposure in, Rec.709 on the screen out, before gamma. */
    static float[] map709(float[] c709) {
        float[] c = times(FROM_709, c709);
        for (int i = 0; i < 3; i++) {
            c[i] *= SCALE;
        }
        float[] out = times(TO_709, map(c));
        for (int i = 0; i < 3; i++) {
            out[i] = Math.clamp(out[i], 0f, 1f);
        }
        return out;
    }

    private interface Curve {
        float at(float x);
    }

    /** The x between low and high a rising curve takes to want. */
    private static float bisect(Curve curve, float want, float low, float high) {
        for (int i = 0; i < 48; i++) {
            float mid = 0.5f * (low + high);
            if (curve.at(mid) < want) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return 0.5f * (low + high);
    }

    private static float smoothStep(float x, float edge0, float edge1) {
        if (x < edge0) {
            return 0f;
        }
        if (x > edge1) {
            return 1f;
        }
        float t = (x - edge0) / (edge1 - edge0);
        return t * t * (3f - 2f * t);
    }

    private static float[] times(float[][] rows, float[] v) {
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            out[i] = rows[i][0] * v[0] + rows[i][1] * v[1] + rows[i][2] * v[2];
        }
        return out;
    }

    /** SMPTE ST 2084 (PQ): light in the operator's units to the encoded signal. */
    private static float pq(float v) {
        float ym = (float) Math.pow(v * REFERENCE_LUMINANCE / PQ_TOP, M1);
        return (float) Math.pow((C1 + C2 * ym) / (1f + C3 * ym), M2);
    }

    /** The encoded signal back to light in the operator's units. */
    private static float unPq(float n) {
        float np = (float) Math.pow(Math.clamp(n, 0f, 1f), 1f / M2);
        float l = Math.max(np - C1, 0f) / (C2 - C3 * np);
        return (float) Math.pow(l, 1f / M1) * PQ_TOP / REFERENCE_LUMINANCE;
    }

    static float[] ucs(float[] rgb) {
        float[] lms = times(TO_LMS, rgb);
        return times(TO_ICTCP, new float[] {pq(lms[0]), pq(lms[1]), pq(lms[2])});
    }

    private static float[] rgb(float[] ictcp) {
        float[] lms = times(FROM_ICTCP, ictcp);
        float[] out = times(FROM_LMS, new float[] {unPq(lms[0]), unPq(lms[1]), unPq(lms[2])});
        for (int i = 0; i < 3; i++) {
            out[i] = Math.max(out[i], 0f);
        }
        return out;
    }

    private static String f(float v) {
        return String.format(Locale.ROOT, "%.9g", v);
    }

    /** rows as a GLSL vec3 dotted with v for each row. */
    private static String times(float[][] rows, String v) {
        StringBuilder s = new StringBuilder("vec3(");
        for (int i = 0; i < 3; i++) {
            s.append(i == 0 ? "" : ", ").append("dot(").append(v).append(", vec3(")
                    .append(f(rows[i][0])).append(", ").append(f(rows[i][1])).append(", ").append(f(rows[i][2])).append("))");
        }
        return s.append(")").toString();
    }

    static final String GLSL = """
            float gtCurve(float x) {
                if (x < 0.0) {
                    return 0.0;
                }
                if (x < LINEAR_END) {
                    return mix(MID_POINT * pow(x / MID_POINT, TOE_STRENGTH), x, smoothstep(0.0, MID_POINT, x));
                }
                return KA + KB * exp(x * KC);
            }
            vec3 gtPq(vec3 v) {
                vec3 ym = pow(max(v * TO_PQ, 0.0), vec3(M1));
                return pow((C1 + C2 * ym) / (1.0 + C3 * ym), vec3(M2));
            }
            vec3 gtUnPq(vec3 n) {
                vec3 np = pow(clamp(n, 0.0, 1.0), vec3(1.0 / M2));
                return pow(max(np - C1, 0.0) / (C2 - C3 * np), vec3(1.0 / M1)) / TO_PQ;
            }
            vec3 gtUcs(vec3 c) {
                vec3 lms = gtPq(TO_LMS_c);
                return TO_ICTCP_lms;
            }
            vec3 gtRgb(vec3 i) {
                vec3 lms = gtUnPq(FROM_ICTCP_i);
                return max(FROM_LMS_lms, 0.0);
            }
            // Polyphony's GT7 operator (GranTurismoTone): linear Rec.709 light at the film's exposure
            // in, Rec.709 on the screen out, 0 to 1 before gamma.
            vec3 granTurismo(vec3 c709) {
                vec3 c = SCALE * FROM_709_c709;
                vec3 ucs = gtUcs(c);
                vec3 skewed = vec3(gtCurve(c.r), gtCurve(c.g), gtCurve(c.b));
                float chroma = 1.0 - smoothstep(FADE_START, FADE_END, ucs.x / PEAK_UCS);
                vec3 scaled = gtRgb(vec3(gtUcs(skewed).x, ucs.yz * chroma));
                vec3 shown = min(mix(skewed, scaled, BLEND_RATIO), PEAK) / PEAK;
                return clamp(TO_709_shown, 0.0, 1.0);
            }
            """
            .replace("LINEAR_END", f(LINEAR_SECTION * PEAK)).replace("MID_POINT", f(MID_POINT)).replace("TOE_STRENGTH", f(TOE_STRENGTH))
            .replace("KA", f(KA)).replace("KB", f(KB)).replace("KC", f(KC))
            .replace("TO_PQ", f(REFERENCE_LUMINANCE / PQ_TOP)).replace("M1", f(M1)).replace("M2", f(M2))
            .replace("C1", f(C1)).replace("C2", f(C2)).replace("C3", f(C3))
            .replace("TO_LMS_c", times(TO_LMS, "c")).replace("TO_ICTCP_lms", times(TO_ICTCP, "lms"))
            .replace("FROM_ICTCP_i", times(FROM_ICTCP, "i")).replace("FROM_LMS_lms", times(FROM_LMS, "lms"))
            .replace("FROM_709_c709", times(FROM_709, "c709")).replace("TO_709_shown", times(TO_709, "shown"))
            .replace("FADE_START", f(FADE_START)).replace("FADE_END", f(FADE_END)).replace("BLEND_RATIO", f(BLEND_RATIO))
            .replace("PEAK_UCS", f(PEAK_UCS)).replace("SCALE", f(SCALE)).replace("PEAK", f(PEAK));
}

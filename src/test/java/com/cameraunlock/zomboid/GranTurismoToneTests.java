package com.cameraunlock.zomboid;

/** Tests of Polyphony's GT7 tone mapping as the develop pass runs it: GranTurismoTone against the sample's own output, and the shader against it. */
final class GranTurismoToneTests {
    private GranTurismoToneTests() {}

    static void run() {
        Checks.run("theOperatorGivesWhatPolyphonysSampleGives", GranTurismoToneTests::theOperatorGivesWhatPolyphonysSampleGives);
        Checks.run("aGreyOfPointOneEightIsShownAsTheFilmShowedIt", GranTurismoToneTests::aGreyOfPointOneEightIsShownAsTheFilmShowedIt);
        Checks.run("moreLightIsNeverShownDarker", GranTurismoToneTests::moreLightIsNeverShownDarker);
        Checks.run("theShaderIsWrittenFromTheseConstantsAndDevelopsThroughIt", GranTurismoToneTests::theShaderIsWrittenFromTheseConstantsAndDevelopsThroughIt);
    }

    /**
     * Linear Rec.2020 in and what the SDR screen shows out, printed by gt7_tone_mapping.cpp 1.0
     * (initializeAsSDR, ICtCp) built with g++ 14. The first three are the sample's own inputs.
     */
    private static final float[][] SAMPLE = {
            {0.5f, 1.23000002f, 0.75f, 0.199622497f, 0.490702957f, 0.299567759f},
            {12.3000002f, 34.2999992f, 56.9000015f, 1f, 1f, 1f},
            {1504.69995f, 64.5100021f, 0.5f, 1f, 1f, 0.738751173f},
            {0.180000007f, 0.180000007f, 0.180000007f, 0.0579512306f, 0.0579510108f, 0.0579509139f},
            {0.0199999996f, 0.00999999978f, 0.00499999989f, 0.00319812819f, 0.0013189757f, 0.000512000173f},
            {0.400000006f, 0.100000001f, 0.0500000007f, 0.146074489f, 0.0306801889f, 0.0140847033f},
            {2f, 0.5f, 0.100000001f, 0.742973268f, 0.192309245f, 0.0326252542f},
            {0.0500000007f, 0.300000012f, 0.899999976f, 0.014997541f, 0.112092592f, 0.346679896f},
            {3f, 3f, 3f, 0.91795218f, 0.917948544f, 0.917947114f},
            {0f, 0f, 0f, 0f, 0f, 0f},
            {0.899999976f, 0.0199999996f, 0.600000024f, 0.354818493f, 0.00577048445f, 0.236497387f}};

    private static void theOperatorGivesWhatPolyphonysSampleGives() {
        Checks.check(Math.abs(GranTurismoTone.PEAK_UCS - 0.602560759f) < 1e-5f, "white's brightness in ICtCp is the sample's, not " + GranTurismoTone.PEAK_UCS);
        for (float[] row : SAMPLE) {
            float[] out = GranTurismoTone.map(new float[] {row[0], row[1], row[2]});
            for (int i = 0; i < 3; i++) {
                float want = row[3 + i];
                Checks.check(Math.abs(out[i] - want) <= 2e-4f + 1e-3f * want,
                        "(" + row[0] + ", " + row[1] + ", " + row[2] + ") channel " + i + " is " + want + " in the sample, not " + out[i]);
            }
        }
    }

    private static void aGreyOfPointOneEightIsShownAsTheFilmShowedIt() {
        float shown = GranTurismoTone.map709(new float[] {0.18f, 0.18f, 0.18f})[1];
        Checks.check(Math.abs(shown - Develop.film(0.18f)) < 1e-3f, "a grey of 0.18 is shown at " + Develop.film(0.18f) + " as the film has it, not " + shown);
        Checks.check(Math.abs(GranTurismoTone.grey(0.18f) - shown) < 1e-3f, "grey() is what the operator shows a grey as");
        for (float wanted : new float[] {0.0017f, 0.05f, 0.4f, 0.79f}) {
            float light = GranTurismoTone.greyInverse(wanted);
            Checks.check(Math.abs(GranTurismoTone.grey(light) - wanted) < 1e-4f, "greyInverse gives back " + wanted + ", not " + GranTurismoTone.grey(light));
        }
    }

    private static void moreLightIsNeverShownDarker() {
        float[][] hues = {{1f, 1f, 1f}, {1f, 0.2f, 0.05f}, {0.1f, 0.4f, 1f}, {0.3f, 1f, 0.2f}};
        for (float[] hue : hues) {
            float last = -1f;
            for (float light = 0.0005f; light < 200f; light *= 1.07f) {
                float[] out = GranTurismoTone.map709(new float[] {hue[0] * light, hue[1] * light, hue[2] * light});
                float shown = 0.2126f * out[0] + 0.7152f * out[1] + 0.0722f * out[2];
                if (shown < last - 1e-4f) {
                    Checks.check(false, "light of hue (" + hue[0] + ", " + hue[1] + ", " + hue[2] + ") at " + light + " is shown at " + shown + ", darker than " + last + " just below it");
                    break;
                }
                last = shown;
            }
            Checks.check(last > 0.99f, "light of hue (" + hue[0] + ", " + hue[1] + ", " + hue[2] + ") reaches white, not " + last);
        }
    }

    private static void theShaderIsWrittenFromTheseConstantsAndDevelopsThroughIt() {
        String glsl = GranTurismoTone.GLSL;
        java.util.regex.Matcher left = java.util.regex.Pattern.compile("\\b[A-Z][A-Z0-9_]+\\b").matcher(glsl.replaceAll("//[^\\n]*", ""));
        boolean found = left.find();
        Checks.check(!found, "every constant of the shader is filled in, not " + (found ? left.group() : ""));
        Checks.check(glsl.contains(String.format(java.util.Locale.ROOT, "%.9g * vec3(", GranTurismoTone.SCALE)), "the shader scales the light as the Java does");
        Checks.check(glsl.contains(String.format(java.util.Locale.ROOT, "/ %.9g)", GranTurismoTone.PEAK_UCS)), "the shader fades colour towards the Java's white");
        Checks.check(PipelineShaders.DEVELOP.contains("vec3 granTurismo(vec3 c709)"), "the develop pass has the operator");
        Checks.check(PipelineShaders.DEVELOP.contains("uGranTurismo > 0.5 ? granTurismo(c * uLevels.y) : film(c * uLevels.y)"), "the develop pass develops through it when its switch is on");
    }
}

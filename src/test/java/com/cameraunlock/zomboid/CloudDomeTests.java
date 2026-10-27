package com.cameraunlock.zomboid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tests of the sun through the dome's clouds: see SkyState.shine, SkyState.sunVeiledNow, SkyState.cloudEased and CloudDome. */
final class CloudDomeTests {
    private CloudDomeTests() {}

    static void run() {
        Checks.run("theSunShinesWholeInAGapAndDimsUnderACloudOrInRain", CloudDomeTests::theSunShinesWholeInAGapAndDimsUnderACloudOrInRain);
        Checks.run("anOvercastSunsetIsNoBrighterThanTheDaysGrey", CloudDomeTests::anOvercastSunsetIsNoBrighterThanTheDaysGrey);
        Checks.run("aLowSunInAGapGildsAndBehindACloudDoesNot", CloudDomeTests::aLowSunInAGapGildsAndBehindACloudDoesNot);
        Checks.run("theSunEasesAcrossACloudsEdgeAndStartsAgainAfterAGap", CloudDomeTests::theSunEasesAcrossACloudsEdgeAndStartsAgainAfterAGap);
        Checks.run("theDomeTakesTheSunAsItsCloudsLie", CloudDomeTests::theDomeTakesTheSunAsItsCloudsLie);
        Checks.run("theDomeIsWorkedOutAsTheSkyShaderHasIt", CloudDomeTests::theDomeIsWorkedOutAsTheSkyShaderHasIt);
        Checks.run("theDomesCloudsLieAsTheyDidWhereTheSkysDriftWraps", CloudDomeTests::theDomesCloudsLieAsTheyDidWhereTheSkysDriftWraps);
    }

    private static SkyState sky(float sunUp, float cover, float deck, float overcast, float falling) {
        SkyState sky = new SkyState();
        sky.sunUp = sunUp;
        sky.sunX = (float) Math.sqrt(1f - sunUp * sunUp);
        sky.sunWhite = SkyState.white(sunUp);
        sky.cover = cover;
        sky.deck = deck;
        sky.overcast = overcast;
        sky.falling = falling;
        return sky;
    }

    private static void keepNoise() {
        if (!CloudDome.kept()) {
            CloudDome.keep(CloudNoise.build(64), 64);
        }
    }

    private static void theSunShinesWholeInAGapAndDimsUnderACloudOrInRain() {
        SkyState gap = sky(0.9f, 0.49f, 0f, 0.35f, 0f);
        gap.shine(0f);
        SkyState shadow = sky(0.9f, 0.49f, 0f, 0.35f, 0f);
        shadow.shine(1f);
        SkyState rain = sky(0.9f, 0.49f, 0f, 0.6f, 0.6f);
        rain.shine(0f);
        Checks.check(gap.sunRed == 1f && gap.rayRed == 0.55f, "through a gap in the dome's clouds the sun is whole: " + gap.sunRed + ", shafts " + gap.rayRed);
        Checks.check(Math.abs(shadow.sunRed - 0.1f) < 1e-6f && Math.abs(shadow.rayRed - 0.0825f) < 1e-6f, "behind a cloud it is dim: " + shadow.sunRed + ", shafts " + shadow.rayRed);
        Checks.check(Math.abs(rain.sunRed - 0.46f) < 1e-6f, "in rain it is dimmed by the rain even in a gap: " + rain.sunRed);
    }

    /**
     * A grey evening, dry and in rain, the sun from below the horizon to well up and the clouds
     * drifted every way: the sun's disc, its shafts and the gold it gives a room are never more
     * than they were with the day's overcast alone.
     */
    private static void anOvercastSunsetIsNoBrighterThanTheDaysGrey() {
        keepNoise();
        float[][] days = {{0.7475f, 0f, 0.7f, 0f}, {1.1f, 1f, 1f, 1f}};
        int brighter = 0, count = 0;
        String worst = "";
        for (float[] day : days) {
            for (int step = 0; step <= 34; step++) {
                float sunUp = -0.02f + 0.005f * step;
                for (int i = 0; i < 60; i++) {
                    SkyState sky = sky(sunUp, day[0], day[1], day[2], day[3]);
                    sky.driftX = i * 0.0137f;
                    sky.driftY = i * 0.0091f;
                    sky.shine(sky.sunVeiledNow());
                    float disc = smooth(-0.06f, 0.02f, sunUp) * (1f - 0.9f * day[2]);
                    float shafts = SkyState.through(sunUp, day[2]) * 0.55f;
                    float gold = SkyState.lowSun(sunUp, day[2], 0f);
                    count++;
                    if (sky.sunRed > disc + 1e-6f || sky.rayRed > shafts + 1e-6f || sky.lowSun > gold + 1e-6f) {
                        brighter++;
                        worst = "sun up " + sunUp + " disc " + sky.sunRed + " (" + disc + ") shafts " + sky.rayRed + " (" + shafts + ") gold " + sky.lowSun + " (" + gold + ")";
                    }
                }
            }
        }
        Checks.check(brighter == 0, "an overcast sunset is as grey as the day's overcast makes it: " + brighter + " of " + count + " brighter, " + worst);
    }

    private static void aLowSunInAGapGildsAndBehindACloudDoesNot() {
        SkyState gap = sky(0.06f, 0.49f, 0f, 0.35f, 0f);
        gap.shine(0f);
        SkyState behind = sky(0.06f, 0.49f, 0f, 0.35f, 0f);
        behind.shine(1f);
        Checks.check(Math.abs(gap.lowSun - SkyState.lowSun(0.06f, 0f, 0f)) < 1e-6f, "a low sun in a gap gilds a room as a clear evening's: " + gap.lowSun);
        Checks.check(behind.lowSun < gap.lowSun * 0.2f, "behind a cloud hardly at all: " + behind.lowSun + " against " + gap.lowSun);
    }

    /**
     * Under broken cloud drifting across the sun at the windiest drift, the sun as drawn moves
     * little in any second where the cloud over it changes much; a hitch moves it no further than
     * a quarter second does; after a long gap it starts from the clouds as they are.
     */
    private static void theSunEasesAcrossACloudsEdgeAndStartsAgainAfterAGap() {
        keepNoise();
        SkyState sky = sky(0.5f, 0.6f, 0f, 0.42f, 0f);
        float drift = SkyState.DRIFT_STILL + SkyState.DRIFT_PER_WIND;
        int frames = 600 * 60;
        float[] now = new float[frames], eased = new float[frames];
        float shown = 0f;
        for (int frame = 0; frame < frames; frame++) {
            sky.driftX = drift * frame / 60f;
            now[frame] = sky.sunVeiledNow();
            shown = SkyState.cloudEased(shown, now[frame], frame == 0 ? Float.POSITIVE_INFINITY : 1f / 60f);
            eased[frame] = shown;
        }
        float rawMost = mostInASecond(now), easedMost = mostInASecond(eased);
        Checks.check(rawMost > 0.3f, "the cloud over the sun changes fast at its edges: " + rawMost + " in a second");
        Checks.check(easedMost < 0.25f, "the sun as drawn eases across them: " + easedMost + " in a second");
        float hitch = SkyState.cloudEased(0f, 1f, 2f);
        Checks.check(hitch == SkyState.cloudEased(0f, 1f, SkyState.CLOUDS_STEP_MOST) && hitch < 0.1f, "a hitch of two seconds, an autosave, moves it as a quarter second does: " + hitch);
        Checks.check(SkyState.cloudEased(0f, 0.8f, SkyState.CLOUDS_RESTART + 1f) == 0.8f, "after first person was off, or another save loaded, it starts from the clouds as they are");
        Checks.check(SkyState.cloudEased(0.3f, 0.8f, Float.POSITIVE_INFINITY) == 0.8f, "and the first picture takes them as they are");
    }

    private static float mostInASecond(float[] shown) {
        float most = 0f;
        for (int frame = 60; frame < shown.length; frame++) {
            most = Math.max(most, Math.abs(shown[frame] - shown[frame - 60]));
        }
        return most;
    }

    private static void theDomeTakesTheSunAsItsCloudsLie() {
        keepNoise();
        float high = 0.954f, across = 0.3f;
        Checks.check(CloudDome.taken(across, 0f, high, 0f, 0f, 0.2f, 0.5f) == 0f, "with no cover the dome takes none of the sun");
        float closed = CloudDome.taken(across, 0f, high, 1.1f, 1f, 0.2f, 0.5f);
        Checks.check(closed > 0.95f, "under a closed deck it takes nearly all of it: " + closed);
        Checks.check(CloudDome.shown(0.02f) == 0f && CloudDome.shown(0.9f) == 1f, "a sun too low for the dome draws no cloud over it, and a high one is drawn whole");
        int covered = 0, open = 0, count = 0;
        for (int i = 0; i < 400; i++) {
            float taken = CloudDome.taken(across, 0f, high, 0.6f, 0f, i * 0.0137f, i * 0.0091f);
            covered += taken > 0.7f ? 1 : 0;
            open += taken < 0.05f ? 1 : 0;
            count++;
        }
        Checks.check(covered > count / 20 && open > count / 20, "under broken cloud the sun is behind a cloud at times and in a gap at others: " + covered + " behind, " + open + " clear of " + count);
    }

    /**
     * CloudDome copies the sky's cloud shader line for line: each pair is the shader's line and
     * CloudDome's copy of it, so a change to either shows here, as does a change to what the
     * clouds' pass is fed or to what the sun is worked out with.
     */
    private static void theDomeIsWorkedOutAsTheSkyShaderHasIt() {
        String dome = source("CloudDome.java"), pipeline = source("Pipeline.java"), sky = source("SkyState.java");
        String[][] copies = {
                {"const float BOTTOM = 1.0;", "static final float BOTTOM = 1f, TOP = 2.3f;"},
                {"const float TOP = 2.3;", "static final float BOTTOM = 1f, TOP = 2.3f;"},
                {"const int STEPS = 48;", "static final int STEPS = 48;"},
                {"if (d.z < 0.03 ||", "static final float LOWEST = 0.03f;"},
                {"float shape = smoothstep(0.0, 0.08, h) * (1.0 - smoothstep(0.25, 1.0, h));", "float shape = smooth(0f, 0.08f, h) * (1f - smooth(0.25f, 1f, h));"},
                {"float deck = clamp(uDeck * shape * 0.6, 0.0, 1.0);", "float decked = Math.clamp(deck * shape * 0.6f, 0f, 1f);"},
                {"if (most <= -0.171) {", "if (most <= -0.171f) {"},
                {"vec3 q = vec3(p.xy * 0.17 + uDrift, p.z * 0.21);", "float qx = px * 0.17f + driftX, qy = py * 0.17f + driftY, qz = pz * 0.21f;"},
                {"float bank = texture3D(uNoise, vec3(p.xy * 0.021 + uDrift * 0.4, 0.37)).g;", "float bank = noise(px * 0.021f + driftX * 0.4f, py * 0.021f + driftY * 0.4f, 0.37f, 1);"},
                {"if (most - (0.5 - bank) * 0.34 <= -0.001) {", "if (most - (0.5f - bank) * 0.34f <= -0.001f) {"},
                {"vec3 pulled = q + (texture3D(uNoise, q * 0.9 + 0.31).rgr - 0.5) * 0.22;", "float pulledZ = qz + (noise(qx * 0.9f + 0.31f, qy * 0.9f + 0.31f, qz * 0.9f + 0.31f, 0) - 0.5f) * 0.22f;"},
                {"float body = texture3D(uNoise, pulled).r * 0.68 + texture3D(uNoise, pulled * 2.3 + 0.17).g * 0.32;",
                        "float body = noise(pulledX, pulledY, pulledZ, 0) * 0.68f + noise(pulledX * 2.3f + 0.17f, pulledY * 2.3f + 0.17f, pulledZ * 2.3f + 0.17f, 1) * 0.32f;"},
                {"float d = body * shape - (1.0 - uCover) - (0.5 - bank) * 0.34;", "float d = body * shape - (1f - cover) - (0.5f - bank) * 0.34f;"},
                {"float wear = texture3D(uNoise, q * 3.7 + vec3(uDrift * 0.3, 0.0)).g * 0.62", "float wear = noise(qx * 3.7f + driftX * 0.3f, qy * 3.7f + driftY * 0.3f, qz * 3.7f, 1) * 0.62f"},
                {"+ texture3D(uNoise, q * 9.1 + vec3(uDrift * 0.6, 0.4)).r * 0.38;", "+ noise(qx * 9.1f + driftX * 0.6f, qy * 9.1f + driftY * 0.6f, qz * 9.1f + 0.4f, 0) * 0.38f;"},
                {"d -= wear * 0.34 * (1.0 - min(d * 3.2, 1.0));", "d -= wear * 0.34f * (1f - Math.min(d * 3.2f, 1f));"},
                {"return max(clamp(d * 7.0, 0.0, 1.0), deck);", "return Math.max(Math.clamp(d * 7f, 0f, 1f), decked);"},
                {"float near = BOTTOM / d.z;", "float near = BOTTOM / sunUp;"},
                {"float fade = 1.0 - smoothstep(9.0, 30.0, near);", "1f - smooth(9f, 30f, BOTTOM / sunUp);"},
                {"float stride = (TOP - BOTTOM) / d.z / float(STEPS);", "float stride = (TOP - BOTTOM) / sunUp / STEPS;"},
                {"float taken = 1.0 - exp(-dens * stride * 9.0);", "left *= (float) Math.exp(-dense * stride * 9f);"},
                {"if (through < 0.02) {", "if (left < 0.02f) {"},
                {"gl_FragColor = vec4(light * fade, 1.0 - (1.0 - through) * fade);", "return shown * taken + (1f - shown) * overcast;"}};
        for (String[] copy : copies) {
            Checks.check(Sky.CLOUDS.contains(copy[0]), "the sky's cloud shader still has: " + copy[0]);
            Checks.check(dome.contains(copy[1]) || sky.contains(copy[1]), "and CloudDome its copy: " + copy[1]);
        }
        for (String fed : new String[] {"clouds.set(\"uCover\", state.cover);", "clouds.set(\"uDeck\", state.deck);", "clouds.set(\"uDrift\", state.driftX, state.driftY);"}) {
            Checks.check(pipeline.contains(fed), "the clouds' pass is still fed as CloudDome is: " + fed);
        }
        Checks.check(sky.contains("CloudDome.taken(sunX, sunY, sunUp, cover, deck, driftX, driftY)"), "and the sun is worked out from the same cover, deck and drift");
    }

    /** Every rate the sky's shader reads the drift at comes round at SKY_REPEATS, so the clouds lie where they did as the drift wraps. */
    private static void theDomesCloudsLieAsTheyDidWhereTheSkysDriftWraps() {
        keepNoise();
        java.util.regex.Matcher rate = java.util.regex.Pattern.compile("uDrift \\* ([0-9.]+)").matcher(Sky.CLOUDS);
        int rates = 0;
        while (rate.find()) {
            double turns = Double.parseDouble(rate.group(1)) * SkyState.SKY_REPEATS;
            Checks.check(Math.abs(turns - Math.rint(turns)) < 1e-9, "the sky reads the drift at " + rate.group(1) + ", which comes round at " + SkyState.SKY_REPEATS);
            rates++;
        }
        Checks.check(rates >= 3, "the sky's shader reads the drift at its rates: " + rates + " found");
        Checks.check(SkyState.skyDrifted(SkyState.SKY_REPEATS - 0.001, 0.002) < 0.0011, "the sky's drift comes round to the start");
        float before = (float) (SkyState.SKY_REPEATS - 0.003), after = -0.003f, most = 0f;
        for (int i = 0; i < 200; i++) {
            float sunUp = 0.2f + 0.004f * i, across = (float) Math.sqrt(1f - sunUp * sunUp);
            float x = across * (float) Math.cos(i * 0.7), y = across * (float) Math.sin(i * 0.7);
            most = Math.max(most, Math.abs(CloudDome.taken(x, y, sunUp, 0.6f, 0f, before, 0.3f) - CloudDome.taken(x, y, sunUp, 0.6f, 0f, after, 0.3f)));
        }
        Checks.check(most < 0.02f, "the dome's clouds lie where they did as the sky's drift wraps: " + most);
    }

    private static String source(String file) {
        try {
            return Files.readString(Path.of("src/main/java/com/cameraunlock/zomboid", file));
        } catch (IOException unread) {
            throw new IllegalStateException("the source the dome's copy is checked against is not there: " + file, unread);
        }
    }

    private static float smooth(float from, float to, float value) {
        float t = Math.clamp((value - from) / (to - from), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}

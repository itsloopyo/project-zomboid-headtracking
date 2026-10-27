package com.cameraunlock.zomboid;

/** Tests of the clouds' shadows and the grade by them: see SkyState.cloudShadow, SkyState.shadeLooked and SkyState.coverSeen. */
final class CloudShadowTests {
    private CloudShadowTests() {}

    static void run() {
        Checks.run("aCellsValueIsTheWholeNumberPermutation", CloudShadowTests::aCellsValueIsTheWholeNumberPermutation);
        Checks.run("moreCloudShadesMoreOfTheGroundAndAClosedDeckAllOfIt", CloudShadowTests::moreCloudShadesMoreOfTheGroundAndAClosedDeckAllOfIt);
        Checks.run("sunlitGroundIsGradedAsAClearDayAndShadeAsTheDaysCover", CloudShadowTests::sunlitGroundIsGradedAsAClearDayAndShadeAsTheDaysCover);
        Checks.run("walkingUnderBrokenCloudTheGradeDoesNotPulse", CloudShadowTests::walkingUnderBrokenCloudTheGradeDoesNotPulse);
        Checks.run("theShadeLookedAtGoesByWhereTheEyeLooks", CloudShadowTests::theShadeLookedAtGoesByWhereTheEyeLooks);
    }

    /** The shader works the permutation out in floats as well: every step of it must be exact here. */
    private static void aCellsValueIsTheWholeNumberPermutation() {
        int wrong = 0;
        for (int x = -300; x < 1200; x += 7) {
            for (int y = -300; y < 1200; y += 3) {
                long first = Math.floorMod((34L * Math.floorMod(x, 289) + 1L) * Math.floorMod(x, 289), 289L);
                long inner = first + Math.floorMod(y, 289);
                long second = Math.floorMod((34L * inner + 1L) * inner, 289L);
                if (SkyState.cloudGrain(x, y) != second / 289f) {
                    wrong++;
                }
            }
        }
        Checks.check(wrong == 0, "a cell's value is the whole-number permutation, exactly: " + wrong + " cells differ");
    }

    private static SkyState broken(float cover) {
        SkyState sky = new SkyState();
        sky.lightX = 0.3f;
        sky.lightY = 0.2f;
        sky.lightUp = 0.93f;
        sky.driftX = 0.4f;
        sky.driftY = 0.7f;
        sky.cover = cover;
        return sky;
    }

    private static float shadedShare(float cover) {
        SkyState sky = broken(cover);
        float shaded = 0f;
        int count = 0;
        for (int x = 9000; x < 11000; x += 5) {
            for (int y = 8000; y < 10000; y += 5) {
                shaded += sky.cloudShadow(x, y);
                count++;
            }
        }
        return shaded / count;
    }

    private static void moreCloudShadesMoreOfTheGroundAndAClosedDeckAllOfIt() {
        float clear = shadedShare(0.29f), half = shadedShare(0.49f), closed = shadedShare(1.1f);
        Checks.check(clear < 0.05f, "under a clear sky hardly any ground is in a cloud's shadow: " + clear);
        Checks.check(half > 0.1f && half < 0.5f, "under broken cloud some of the ground is shaded and most is not: " + half);
        Checks.check(closed > 0.99f, "under a closed deck all of it is: " + closed);
    }

    private static void sunlitGroundIsGradedAsAClearDayAndShadeAsTheDaysCover() {
        SkyState sky = new SkyState();
        sky.cover = 0.49f;
        sky.shadeSeen = 0f;
        float sunlit = sky.coverSeen();
        sky.shadeSeen = 1f;
        float shade = sky.coverSeen();
        Checks.check(Math.abs(sunlit - 0.29f) < 1e-6f, "ground in the sun is graded as under a clear sky: " + sunlit);
        Checks.check(Math.abs(shade - 0.49f) < 1e-6f, "ground in the clouds' shadows as under the day's cover: " + shade);
    }

    /**
     * Walking (or driving) across broken cloud, the most the grade moves in any second, by the
     * shade over the round ahead, against what it moved by the eye's own shadow alone eased over
     * a second and a half, as it once was.
     */
    private static void walkingUnderBrokenCloudTheGradeDoesNotPulse() {
        SkyState sky = broken(0.6f);
        for (float speed : new float[] {4f, 16f}) {
            int frames = (int) (400f / speed * 60f);
            float[] looked = new float[frames], eyeOnly = new float[frames];
            float shown = 0f, eye = -1f;
            for (int frame = 0; frame < frames; frame++) {
                float x = 9000f + speed * frame / 60f, y = 8000f;
                shown = SkyState.cloudEased(shown, sky.shadeLooked(x, y, 1f, 0f), frame == 0 ? Float.POSITIVE_INFINITY : 1f / 60f);
                eye = eye < 0f ? sky.cloudShadow(x, y) : eye + (sky.cloudShadow(x, y) - eye) * (1f - (float) Math.exp(-1f / 60f / 1.5f));
                looked[frame] = shown;
                eyeOnly[frame] = eye;
            }
            float most = mostInASecond(looked), eyeMost = mostInASecond(eyeOnly);
            Checks.check(most < 0.12f, "at " + speed + " tiles a second the grade moves little in any second: " + most + " (by the eye's own shadow " + eyeMost + ")");
            Checks.check(most < eyeMost * 0.33f, "and under a third of what the eye's own shadow moved it: " + most + " against " + eyeMost);
        }
    }

    private static float mostInASecond(float[] shown) {
        float most = 0f;
        for (int frame = 60; frame < shown.length; frame++) {
            most = Math.max(most, Math.abs(shown[frame] - shown[frame - 60]));
        }
        return most;
    }

    /** From a place in the sun the round ahead takes in what the eye looks at: turned the other way it can be all shade. */
    private static void theShadeLookedAtGoesByWhereTheEyeLooks() {
        SkyState sky = broken(0.6f);
        float widest = 0f;
        for (int x = 9000; x < 11000; x += 7) {
            if (sky.cloudShadow(x, 8000f) > 0.01f) {
                continue;
            }
            widest = Math.max(widest, Math.abs(sky.shadeLooked(x, 8000f, 1f, 0f) - sky.shadeLooked(x, 8000f, -1f, 0f)));
        }
        Checks.check(widest > 0.3f, "standing in the sun, shade ahead and sun behind grade differently: " + widest);
    }
}

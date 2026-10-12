package com.cameraunlock.zomboid;

/** Tests of the snow a blizzard drives onto the windward side of trunks: see SkyState.plastered. */
final class PlasterTests {
    private PlasterTests() {}

    static void run() {
        Checks.run("aBlizzardPlastersTheWindwardSideAndItThawsWithTheSnow", PlasterTests::aBlizzardPlastersTheWindwardSideAndItThawsWithTheSnow);
    }

    private static void aBlizzardPlastersTheWindwardSideAndItThawsWithTheSnow() {
        float[] at = {0f, 0f};
        for (int second = 0; second < 30; second++) {
            at = SkyState.plastered(at[0], at[1], 0f, -1f, 0f, 0.8f, 1f);
        }
        Checks.check(at[0] == 0f && at[1] == 0f, "snow falling with no wind to drive it sticks to no side");
        for (int second = 0; second < 120; second++) {
            at = SkyState.plastered(at[0], at[1], 0f, -1f, 1f, 0.8f, 1f);
        }
        Checks.check(at[1] < -0.7f && Math.abs(at[0]) < 1e-4f, "two minutes of blizzard from the south stick snow on south faces: " + at[0] + ", " + at[1]);
        float[] still = SkyState.plastered(at[0], at[1], 1f, 0f, 0f, 0.8f, 30f);
        Checks.check(still[0] == at[0] && still[1] == at[1], "it stays when the wind drops");
        float[] thawing = SkyState.plastered(at[0], at[1], 0f, -1f, 0f, 0.3f, 1f);
        Checks.check(Math.hypot(thawing[0], thawing[1]) <= 0.3f + 1e-6f, "there is never more stuck than lies");
        float[] thawed = SkyState.plastered(at[0], at[1], 0f, -1f, 0f, 0f, 1f);
        Checks.check(thawed[0] == 0f && thawed[1] == 0f, "it goes when the snow lying has thawed");
    }
}

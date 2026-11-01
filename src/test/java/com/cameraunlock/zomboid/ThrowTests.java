package com.cameraunlock.zomboid;

/**
 * A throw from the first person view lands where the aim is. The flight here is
 * the game's, step by step (IsoGameCharacter.Throw and IsoPhysicsObject.update,
 * as IsoMolotovCocktail sets it up), without the random push the game adds to
 * every throw.
 */
final class ThrowTests {
    private ThrowTests() {}

    static void run() {
        Checks.run("throwLandsOnTheAimAtEveryFrameRate", ThrowTests::throwLandsOnTheAimAtEveryFrameRate);
        Checks.run("throwBeyondReachIsHeldAtTheGamesRange", ThrowTests::throwBeyondReachIsHeldAtTheGamesRange);
        Checks.run("throwAtTheSquareUnderTheAimOvershoots", ThrowTests::throwAtTheSquareUnderTheAimOvershoots);
    }

    /** Where a throw from from at the corner square comes down, along one axis, flown the way the game flies it. */
    private static float landing(float from, int square, float range, int lockFps) {
        float step = 30f / lockFps;
        float along = Math.max(-range, Math.min(range, square - from));
        float speed = along * 0.4f, x = from, z = 0.6f, fall = 0f;
        while (z > 0f) {
            speed *= 1f - 0.1f * step;
            fall = Math.max(-0.02f, fall - 0.005f * step);
            x += speed * 0.3f * step;
            z += fall * 0.4f * step;
        }
        return x;
    }

    private static void throwLandsOnTheAimAtEveryFrameRate() {
        float range = 8f;
        for (int lockFps : new int[] {30, 60, 120, 144}) {
            float carry = Throws.carry(lockFps);
            float worst = 0f;
            for (float from : new float[] {10806.5f, 10806.44f, 10800.03f, 10812.97f}) {
                float reach = (range - 1f) * carry;
                for (float d = -reach; d <= reach; d += 0.25f) {
                    float aimed = from + d;
                    float landed = landing(from, Throws.square(from, aimed, range, carry), range, lockFps);
                    worst = Math.max(worst, Math.abs(landed - aimed));
                }
            }
            Checks.check(worst <= 0.5f * carry + 0.02f, "at " + lockFps + " frames a second every throw within reach lands within half a square's carry of the aim, worst "
                    + worst + " tiles");
        }
    }

    private static void throwBeyondReachIsHeldAtTheGamesRange() {
        float carry = Throws.carry(60), from = 10806.5f;
        int square = Throws.square(from, from + 40f, 8f, carry);
        Checks.check(Math.abs(square - from) <= 8.5f, "an aim far beyond reach throws at a square no further than the game's range, got " + (square - from));
        Checks.check(landing(from, square, 8f, 60) > from + 8f, "and it still flies as far as the game lets it, not short");
    }

    private static void throwAtTheSquareUnderTheAimOvershoots() {
        float from = 10806.5f, aimed = from - 5f;
        float landed = landing(from, (int) Math.floor(aimed), 8f, 60);
        Checks.check(Math.abs(landed - aimed) > 0.6f, "the game's own throw at the square under the aim lands well past it, which is what Throws.square corrects: "
                + (landed - aimed));
    }
}

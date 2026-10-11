package com.cameraunlock.zomboid;

/** Tests of the push a shot gives the body it drops. */
final class RagdollTests {
    private RagdollTests() {}

    static void run() {
        Checks.run("thePushGoesAlongTheShotInBulletsAxes", RagdollTests::thePushGoesAlongTheShotInBulletsAxes);
        Checks.run("thePushIsGivenWhereTheShotWentIn", RagdollTests::thePushIsGivenWhereTheShotWentIn);
        Checks.run("theShinsAreKickedLevelTowardsTheShooter", RagdollTests::theShinsAreKickedLevelTowardsTheShooter);
        Checks.run("aShotStraightDownKicksNoShin", RagdollTests::aShotStraightDownKicksNoShin);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    /** A shot level along +x and a little down, the game's push being 60 level and 15 lift. */
    private static HitImpulse.Shot shot() {
        float down = 0.2f, level = (float) Math.sqrt(1 - down * down);
        return new HitImpulse.Shot(level, 0f, -down, -0.07f, 0.02f, 0.03f, 2, 0L);
    }

    private static void thePushGoesAlongTheShotInBulletsAxes() {
        HitImpulse.Shot shot = shot();
        float[] buffer = {60f, 15f, 0f, 0f, 0f, 0f};
        float game = (float) Math.sqrt(60 * 60 + 15 * 15);
        HitImpulse.along(shot, buffer);
        float strength = (float) Math.sqrt(buffer[0] * buffer[0] + buffer[1] * buffer[1] + buffer[2] * buffer[2]);
        Checks.check(near(strength, game * HitImpulse.STRENGTH), "the push is the game's for that part and round, times STRENGTH: " + strength);
        // Bullet's axes are x, up, y.
        Checks.check(near(buffer[0] / strength, shot.forwardX()) && near(buffer[1] / strength, shot.forwardUp())
                && near(buffer[2] / strength, shot.forwardY()), "the push is along the shot, x, up, y");
    }

    private static void thePushIsGivenWhereTheShotWentIn() {
        HitImpulse.Shot shot = shot();
        float[] buffer = {60f, 15f, 0f, 0f, 0f, 0f};
        HitImpulse.along(shot, buffer);
        Checks.check(near(buffer[3], shot.leverX()) && near(buffer[4], shot.leverUp()) && near(buffer[5], shot.leverY()),
                "the push is given at the entry point from the part's middle, x, up, y");
    }

    private static void theShinsAreKickedLevelTowardsTheShooter() {
        HitImpulse.Shot shot = new HitImpulse.Shot(0.6f, 0.6f, -0.53f, 0f, 0f, 0f, 1, 0L);
        float[] kick = HitImpulse.shinKick(shot);
        float level = (float) Math.sqrt(0.72f);
        Checks.check(near(kick[0], -0.6f / level * HitImpulse.SHIN_KICK) && near(kick[2], -0.6f / level * HitImpulse.SHIN_KICK),
                "the kick is back along the shot's level line, x and y: " + kick[0] + ", " + kick[2]);
        Checks.check(kick[1] == 0f, "the kick is level");
        Checks.check(kick[3] == 0f && kick[4] == 0f && kick[5] == 0f, "the kick is at the shin's middle");
    }

    private static void aShotStraightDownKicksNoShin() {
        Checks.check(HitImpulse.shinKick(new HitImpulse.Shot(0f, 0f, -1f, 0f, 0f, 0f, 1, 0L)) == null, "a shot with no level line kicks nothing");
    }
}

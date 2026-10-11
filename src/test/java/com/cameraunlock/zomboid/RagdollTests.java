package com.cameraunlock.zomboid;

/** Tests of the push a shot gives the body it drops and the knock it gives one that stays up. */
final class RagdollTests {
    private RagdollTests() {}

    static void run() {
        Checks.run("thePushGoesAlongTheShotInBulletsAxes", RagdollTests::thePushGoesAlongTheShotInBulletsAxes);
        Checks.run("thePushIsGivenWhereTheShotWentIn", RagdollTests::thePushIsGivenWhereTheShotWentIn);
        Checks.run("theShinsAreKickedLevelTowardsTheShooter", RagdollTests::theShinsAreKickedLevelTowardsTheShooter);
        Checks.run("aShotStraightDownKicksNoShin", RagdollTests::aShotStraightDownKicksNoShin);
        Checks.run("theKnockIsWholeAtItsRiseAndGoneByItsEnd", RagdollTests::theKnockIsWholeAtItsRiseAndGoneByItsEnd);
        Checks.run("hitsOfOneRoundKnockOnce", RagdollTests::hitsOfOneRoundKnockOnce);
        Checks.run("theModelIsTheWorldTurnedBackTheWayTheEyeIsPlaced", RagdollTests::theModelIsTheWorldTurnedBackTheWayTheEyeIsPlaced);
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
        HitImpulse.Shot shot = new HitImpulse.Shot(0.48f, 0.64f, -0.6f, 0f, 0f, 0f, 1, 0L);
        float[] kick = HitImpulse.shinKick(shot);
        Checks.check(near(kick[0], -0.6f * HitImpulse.SHIN_KICK) && near(kick[2], -0.8f * HitImpulse.SHIN_KICK),
                "the kick is back along the shot's level line, x then y in Bullet's x and z: " + kick[0] + ", " + kick[2]);
        Checks.check(kick[1] == 0f, "the kick is level");
        Checks.check(kick[3] == 0f && kick[4] == 0f && kick[5] == 0f, "the kick is at the shin's middle");
    }

    private static void aShotStraightDownKicksNoShin() {
        Checks.check(HitImpulse.shinKick(new HitImpulse.Shot(0f, 0f, -1f, 0f, 0f, 0f, 1, 0L)) == null, "a shot with no level line kicks nothing");
    }

    private static void theKnockIsWholeAtItsRiseAndGoneByItsEnd() {
        Checks.check(HitFlinch.envelope(-0.01f) == 0f, "nothing before the hit");
        Checks.check(HitFlinch.envelope(0f) == 0f, "nothing at the hit itself");
        Checks.check(near(HitFlinch.envelope(HitFlinch.RISE), 1f), "all of it at the rise");
        Checks.check(HitFlinch.envelope(HitFlinch.RISE / 2f) < 1f && HitFlinch.envelope(HitFlinch.RISE * 2f) < 1f, "less either side of the rise");
        float last = 1f;
        for (float t = HitFlinch.RISE; t < HitFlinch.LASTS; t += 0.01f) {
            float now = HitFlinch.envelope(t);
            Checks.check(now <= last + 1e-6f, "it only lets go after the rise, at " + t);
            last = now;
        }
        Checks.check(HitFlinch.envelope(HitFlinch.LASTS - 0.001f) < 0.01f, "next to nothing is left as it ends: " + HitFlinch.envelope(HitFlinch.LASTS - 0.001f));
        Checks.check(HitFlinch.envelope(HitFlinch.LASTS) == 0f, "nothing after it ends");
    }

    private static void hitsOfOneRoundKnockOnce() {
        long shot = 1_000_000_000L;
        Checks.check(HitFlinch.sameShot(shot, shot), "the second working out of a hit, at once, is the same round");
        Checks.check(HitFlinch.sameShot(shot, shot + 16_000_000L), "a pellet of the same shell a frame later is the same round");
        Checks.check(!HitFlinch.sameShot(shot, shot + 100_000_000L), "a round a tenth of a second later knocks again");
    }

    private static void theModelIsTheWorldTurnedBackTheWayTheEyeIsPlaced() {
        float[] a = new float[3], b = new float[3], back = new float[3];
        for (float angle : new float[] {0f, 0.7f, 2f, -2.5f}) {
            // Two points of the model placed in the world as the eye is; their difference leaves out the eye's own offsets.
            ViewCamera.eyeOffset(0.1f, 0.9f, -0.2f, angle, 0.3f, a);
            ViewCamera.eyeOffset(0.4f, 1.3f, 0.25f, angle, 0.3f, b);
            HitFlinch.toModel(angle, b[0] - a[0], b[1] - a[1], b[2] - a[2], Characters.CHARACTER_SCALE, back);
            Checks.check(near(back[0], 0.3f) && near(back[1], 0.4f) && near(back[2], 0.45f),
                    "the world back into the model at " + angle + ": " + back[0] + ", " + back[1] + ", " + back[2]);
        }
    }
}

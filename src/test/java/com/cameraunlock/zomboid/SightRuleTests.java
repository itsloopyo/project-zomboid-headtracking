package com.cameraunlock.zomboid;

/**
 * The game's test for whether its character sees a zombie, asked with the line read off the map
 * (see LiveSight and SightRule). The cones are the game's own: 144 degrees across on foot by day, and less at
 * night, measured in town as zombies a clear line from the character showed or stayed hidden
 * (rig runs iso1 and night1): by day shown 70 degrees round and hidden 80 round, at night shown
 * 65 round and hidden 71 round.
 */
final class SightRuleTests {
    private SightRuleTests() {}

    private static final float SIGHT = 40f, DETECTION = 3.5f;
    private static final float DAY_CONE = (float) Math.cos(Math.toRadians(72.0)), NIGHT_CONE = (float) Math.cos(Math.toRadians(68.0));

    static void run() {
        Checks.run("theConeComesBackInTheDegreesTheGameGaveIt", SightRuleTests::theConeComesBackInTheDegreesTheGameGaveIt);
        Checks.run("aLitZombieInTheConeIsSeenAsFarAsTheGamesSight", SightRuleTests::aLitZombieInTheConeIsSeenAsFarAsTheGamesSight);
        Checks.run("outOfTheConeOnlyTheDetectionRangeIsSeen", SightRuleTests::outOfTheConeOnlyTheDetectionRangeIsSeen);
        Checks.run("theMeasuredZombiesShowOrHideAsTheGameHadThem", SightRuleTests::theMeasuredZombiesShowOrHideAsTheGameHadThem);
        Checks.run("aSquareWithNoLightWorkedOutHidesItsZombie", SightRuleTests::aSquareWithNoLightWorkedOutHidesItsZombie);
    }

    private static void theConeComesBackInTheDegreesTheGameGaveIt() {
        // LightingJNI.degreesToCone hands 144 degrees on as (144 - 180) / 180.
        Checks.check(Math.abs(SightRule.coneToDegrees((144f - 180f) / 180f) - 144f) < 1e-3f, "the game's -0.2 is 144 degrees across");
        Checks.check(Math.abs(SightRule.coneToDegrees((324f - 180f) / 180f) - 324f) < 1e-3f, "and a driver's 0.8 by day 324");
    }

    private static void aLitZombieInTheConeIsSeenAsFarAsTheGamesSight() {
        float ahead = 1f;
        Checks.check(SightRule.seen(39.9f, ahead, DAY_CONE, SIGHT, DETECTION, 0.8f, 0.8f), "straight ahead in daylight just inside the game's sight");
        Checks.check(!SightRule.seen(40.1f, ahead, DAY_CONE, SIGHT, DETECTION, 0.8f, 0.8f), "and not past it");
        Checks.check(SightRule.seen(20f, ahead, DAY_CONE, SIGHT, DETECTION, 0.3f, 0.3f), "a street at night, 0.3 light, is seen 20 tiles off");
    }

    private static void outOfTheConeOnlyTheDetectionRangeIsSeen() {
        float behind = -1f;
        Checks.check(SightRule.seen(3f, behind, DAY_CONE, SIGHT, DETECTION, 0.5f, 0.5f), "behind, within the detection range, the character notices it");
        Checks.check(!SightRule.seen(4f, behind, DAY_CONE, SIGHT, DETECTION, 0.5f, 0.5f), "behind and past the detection range it does not");
    }

    private static void theMeasuredZombiesShowOrHideAsTheGameHadThem() {
        Checks.check(SightRule.seen(6f, cos(70), DAY_CONE, SIGHT, DETECTION, 0.33f, 0.33f), "by day, 6 tiles off and 70 degrees round: shown in the game");
        Checks.check(!SightRule.seen(9.3f, cos(84), DAY_CONE, SIGHT, DETECTION, 0.33f, 0.33f), "by day, 9.3 tiles off and 84 degrees round: hidden in the game");
        Checks.check(SightRule.seen(14.3f, cos(65), NIGHT_CONE, SIGHT, DETECTION, 0.32f, 0.32f), "at night, 14.3 tiles off and 65 degrees round: shown in the game");
        Checks.check(!SightRule.seen(11.1f, cos(71), NIGHT_CONE, SIGHT, DETECTION, 0.16f, 0.31f), "at night, 11.1 tiles off and 71 degrees round: hidden in the game");
    }

    private static void aSquareWithNoLightWorkedOutHidesItsZombie() {
        Checks.check(!SightRule.seen(2f, 1f, DAY_CONE, SIGHT, DETECTION, -1f, 0.5f), "TestIfSeen sees nothing on a square whose light is not worked out");
        Checks.check(!SightRule.seen(8f, 1f, DAY_CONE, SIGHT, DETECTION, 0f, 0.5f), "nor on one in no light at all past the detection range");
    }

    private static float cos(double degrees) {
        return (float) Math.cos(Math.toRadians(degrees));
    }
}

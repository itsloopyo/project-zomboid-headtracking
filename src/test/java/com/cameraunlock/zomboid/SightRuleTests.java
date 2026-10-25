package com.cameraunlock.zomboid;

/** The cone of sight LiveSight reads back from the game (see SightRule). */
final class SightRuleTests {
    private SightRuleTests() {}

    static void run() {
        Checks.run("theConeComesBackInTheDegreesTheGameGaveIt", SightRuleTests::theConeComesBackInTheDegreesTheGameGaveIt);
    }

    private static void theConeComesBackInTheDegreesTheGameGaveIt() {
        // LightingJNI.degreesToCone hands 144 degrees on as (144 - 180) / 180.
        Checks.check(Math.abs(SightRule.coneToDegrees((144f - 180f) / 180f) - 144f) < 1e-3f, "the game's -0.2 is 144 degrees across");
        Checks.check(Math.abs(SightRule.coneToDegrees((324f - 180f) / 180f) - 324f) < 1e-3f, "and a driver's 0.8 by day 324");
    }
}

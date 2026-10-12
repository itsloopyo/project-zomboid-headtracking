package com.cameraunlock.zomboid;

/** Tests of what the season does to a deciduous tree's crown and to the leaf on the ground. */
final class SeasonsTests {
    private SeasonsTests() {}

    static void run() {
        Checks.run("treesAreInFullLeafThroughSummer", SeasonsTests::treesAreInFullLeafThroughSummer);
        Checks.run("everyTreeIsBareByTheEndOfAutumnAndThroughWinter", SeasonsTests::everyTreeIsBareByTheEndOfAutumnAndThroughWinter);
        Checks.run("treesDropOnDaysOfTheirOwn", SeasonsTests::treesDropOnDaysOfTheirOwn);
        Checks.run("leafOnlyFallsThroughAutumnAndOnlyGrowsThroughSpring", SeasonsTests::leafOnlyFallsThroughAutumnAndOnlyGrowsThroughSpring);
        Checks.run("litterDeepensThroughAutumnAndRotsThroughSpring", SeasonsTests::litterDeepensThroughAutumnAndRotsThroughSpring);
    }

    private static void treesAreInFullLeafThroughSummer() {
        for (int hash = 0; hash < 1000; hash += 37) {
            Checks.check(Seasons.leafOn(Seasons.SUMMER, 0.5f, hash) == 1f, "summer crown is full");
            Checks.check(Seasons.leafOn(Seasons.SUMMER2, 0.9f, hash) == 1f, "late summer crown is full");
            Checks.check(Seasons.leafOn(Seasons.SPRING, 1f, hash) == 1f, "crown is full at the end of spring");
        }
    }

    private static void everyTreeIsBareByTheEndOfAutumnAndThroughWinter() {
        for (int i = 0; i < 1000; i++) {
            int hash = Flora.mix(i, 7 * i, 3);
            Checks.check(Seasons.leafOn(Seasons.AUTUMN, 1f, hash) == 0f, "bare at the end of autumn");
            Checks.check(Seasons.leafOn(Seasons.WINTER, 0.3f, hash) == 0f, "bare in winter");
            Checks.check(Seasons.leafOn(Seasons.AUTUMN, 0f, hash) == 1f, "full at the start of autumn");
        }
    }

    private static void treesDropOnDaysOfTheirOwn() {
        float least = 1f, most = 0f;
        for (int i = 0; i < 1000; i++) {
            float on = Seasons.leafOn(Seasons.AUTUMN, 0.4f, Flora.mix(i, 3 * i, 9));
            least = Math.min(least, on);
            most = Math.max(most, on);
        }
        Checks.check(most - least > 0.5f, "trees differ by " + (most - least) + " partway through autumn");
    }

    private static void leafOnlyFallsThroughAutumnAndOnlyGrowsThroughSpring() {
        for (int i = 0; i < 200; i++) {
            int hash = Flora.mix(i, i, 5);
            float autumn = 1f, spring = 0f;
            for (int day = 0; day <= 100; day++) {
                float a = Seasons.leafOn(Seasons.AUTUMN, day / 100f, hash), s = Seasons.leafOn(Seasons.SPRING, day / 100f, hash);
                Checks.check(a <= autumn, "autumn leaf rose on day " + day);
                Checks.check(s >= spring, "spring leaf fell on day " + day);
                autumn = a;
                spring = s;
            }
        }
    }

    private static void litterDeepensThroughAutumnAndRotsThroughSpring() {
        Checks.check(Seasons.litter(Seasons.SUMMER, 0.5f) < 0.2f, "summer litter is thin");
        Checks.check(Seasons.litter(Seasons.AUTUMN, 0f) == Seasons.litter(Seasons.SUMMER2, 1f), "autumn starts from summer's litter");
        Checks.check(Seasons.litter(Seasons.AUTUMN, 1f) == 1f, "litter is deepest at the end of autumn");
        Checks.check(Seasons.litter(Seasons.WINTER, 0.5f) == 1f, "winter keeps the litter");
        Checks.check(Seasons.litter(Seasons.SPRING, 1f) == Seasons.litter(Seasons.SUMMER, 0f), "spring ends on summer's litter");
    }
}

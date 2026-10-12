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
        Checks.run("leavesTurnBeforeTheyFallAndAreBrownWhenTheLastFalls", SeasonsTests::leavesTurnBeforeTheyFallAndAreBrownWhenTheLastFalls);
        Checks.run("theStageMovesOnlyWhileATreeChanges", SeasonsTests::theStageMovesOnlyWhileATreeChanges);
        Checks.run("theGamesScheduleIsTheGames", SeasonsTests::theGamesScheduleIsTheGames);
        Checks.run("theShaderWorksOutWhatTheJavaDoes", SeasonsTests::theShaderWorksOutWhatTheJavaDoes);
    }

    private static void leavesTurnBeforeTheyFallAndAreBrownWhenTheLastFalls() {
        for (int i = 0; i < 300; i++) {
            int hash = Seasons.treeHash(i, 11 * i);
            float last = 0f;
            boolean turnedInFullLeaf = false;
            for (int day = 0; day <= 100; day++) {
                float p = day / 100f, turned = Seasons.turned(Seasons.AUTUMN, p, hash), on = Seasons.leafOn(Seasons.AUTUMN, p, hash);
                Checks.check(turned >= last, "autumn leaf turned back on day " + day);
                Checks.check(on > 0f || turned == 1f, "a tree is bare only once its leaves are brown, day " + day);
                turnedInFullLeaf |= on == 1f && turned > 0f;
                last = turned;
            }
            Checks.check(turnedInFullLeaf, "a tree's leaves turn while it still has them all");
            Checks.check(Seasons.turned(Seasons.SPRING, 0.5f, hash) == 0f && Seasons.turned(Seasons.SUMMER2, 0.9f, hash) == 0f, "spring and summer leaves are green");
        }
    }

    /**
     * Every build that holds a tree the season shapes is made again when the stage moves on, so
     * it moves on through autumn and spring at each step at which some tree is otherwise, and not
     * once every tree has come to how it stays: bare and brown in autumn, in full leaf in spring.
     */
    private static void theStageMovesOnlyWhileATreeChanges() {
        Checks.check(Seasons.stage(Seasons.SUMMER, 0.1f) == Seasons.stage(Seasons.SUMMER2, 0.9f), "summer is one stage");
        Checks.check(Seasons.stage(Seasons.WINTER, 0.1f) == Seasons.stage(Seasons.WINTER, 0.9f), "winter is one stage");
        Checks.check(Seasons.stage(Seasons.WINTER, 0.5f) != Seasons.stage(Seasons.SUMMER, 0.5f), "winter is not summer");
        for (int season : new int[] {Seasons.AUTUMN, Seasons.SPRING}) {
            int moves = 0;
            for (int step = 1; step <= 4 * Seasons.STEPS; step++) {
                float before = (step - 1f) / (4 * Seasons.STEPS), now = (float) step / (4 * Seasons.STEPS);
                boolean changes = false;
                for (int hash = 0; hash < 256; hash++) {
                    changes |= Seasons.leafOn(season, Seasons.stepped(season, now), hash) != Seasons.leafOn(season, Seasons.stepped(season, before), hash)
                            || Seasons.turned(season, Seasons.stepped(season, now), hash) != Seasons.turned(season, Seasons.stepped(season, before), hash);
                }
                boolean moved = Seasons.stage(season, now) != Seasons.stage(season, before);
                Checks.check(moved == changes, "season " + season + " at " + now + ": the stage " + (moved ? "moved" : "stood") + " where trees " + (changes ? "changed" : "stood"));
                moves += moved ? 1 : 0;
            }
            Checks.check(moves > Seasons.STEPS / 2 && moves < Seasons.STEPS, "season " + season + " moves on in steps until its trees are settled: " + moves);
        }
        for (int hash = 0; hash < 256; hash++) {
            Checks.check(Seasons.leafOn(Seasons.AUTUMN, Seasons.stepped(Seasons.AUTUMN, 0.95f), hash) == 0f
                    && Seasons.turned(Seasons.AUTUMN, Seasons.stepped(Seasons.AUTUMN, 0.95f), hash) == 1f, "every tree is bare and brown once autumn is settled");
            Checks.check(Seasons.leafOn(Seasons.SPRING, Seasons.stepped(Seasons.SPRING, 0.75f), hash) == 1f, "every tree is in full leaf once spring is settled");
        }
    }

    /** ErosionCategory.currentSeason, as read off the game: a tree's own number is its square's magicNum. */
    private static void theGamesScheduleIsTheGames() {
        for (float own = 0f; own <= 1f; own += 0.125f) {
            Checks.check(own == 1f || Seasons.gameLeafOn(Seasons.AUTUMN, 0.5f + 0.5f * own - 0.01f, own) == 1f && Seasons.gameLeafOn(Seasons.AUTUMN, 0.5f + 0.5f * own + 0.01f, own) == 0f,
                    "in autumn a tree goes bare at half way and its own share of the rest");
            Checks.check(Seasons.gameLeafOn(Seasons.WINTER, 0.5f, own) == 0f && Seasons.gameLeafOn(Seasons.SUMMER, 0.5f, own) == 1f, "bare in winter, in leaf in summer");
            Checks.check(Seasons.gameLeafOn(Seasons.SPRING, own + 0.01f, own) == 1f && (own == 0f || Seasons.gameLeafOn(Seasons.SPRING, own - 0.01f, own) == 0f),
                    "in spring a tree comes into leaf at its own share of it");
            Checks.check(Seasons.gameTurned(Seasons.AUTUMN, 0.5f * own + 0.01f, own) == 0.5f, "its autumn picture from half its own share of autumn");
        }
    }

    /** Seasons.GLSL, evaluated over every season, the season in hundredths and every tree's own number, works out what the Java does. */
    private static void theShaderWorksOutWhatTheJavaDoes() {
        GlslFunctions shader = new GlslFunctions(Seasons.GLSL);
        float most = 0f;
        for (int season = Seasons.DEFAULT; season <= Seasons.WINTER; season++) {
            for (int day = 0; day <= 100; day++) {
                float progression = day / 100f;
                for (int hash = 0; hash < 256; hash++) {
                    float own = Flora.unit(hash, 0);
                    most = Math.max(most, Math.abs(shader.call("leafOn", season, progression, own) - Seasons.leafOn(season, progression, hash)));
                    most = Math.max(most, Math.abs(shader.call("leafTurned", season, progression, own) - Seasons.turned(season, progression, hash)));
                    most = Math.max(most, Math.abs(shader.call("gameLeafOn", season, progression, own) - Seasons.gameLeafOn(season, progression, own)));
                    most = Math.max(most, Math.abs(shader.call("gameTurned", season, progression, own) - Seasons.gameTurned(season, progression, own)));
                }
            }
        }
        Checks.check(most < 1e-4f, "the shader's leaf and turn are the Java's to within " + most);
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

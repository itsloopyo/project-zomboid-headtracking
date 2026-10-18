package com.cameraunlock.zomboid;

/** How far off people, zombies, vehicles and props are drawn: the draw distance row of [Graphics]. */
final class DrawDistanceTests {
    private DrawDistanceTests() {}

    static void run() {
        Checks.run("theDrawDistanceNeverReachesPastTheViewDistance", DrawDistanceTests::theDrawDistanceNeverReachesPastTheViewDistance);
        Checks.run("onFootTheDrawDistanceIsAllRound", DrawDistanceTests::onFootTheDrawDistanceIsAllRound);
        Checks.run("aheadOfARideTheViewDistanceCounts", DrawDistanceTests::aheadOfARideTheViewDistanceCounts);
        Checks.run("whereThePlayersCanFightNothingIsLeftOut", DrawDistanceTests::whereThePlayersCanFightNothingIsLeftOut);
        Checks.run("theGamesOwnViewLeavesNothingOut", DrawDistanceTests::theGamesOwnViewLeavesNothingOut);
        Quality.custom(Quality.HIGH_PICTURE);
        Quality.set(0, Quality.HIGH);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** Custom, drawn with High's picture but for the view distance and the draw distance. */
    private static void custom(int viewDistance, int drawDistance) {
        double[] values = Config.values(Quality.HIGH_PICTURE);
        values[Config.graphicsPart("ViewDistance")] = viewDistance;
        values[Config.graphicsPart("ZombiesAndPropsDrawDistance")] = drawDistance;
        Quality.custom(Config.picture(values));
        Quality.set(0, Quality.CUSTOM);
    }

    private static void theDrawDistanceNeverReachesPastTheViewDistance() {
        check(DrawDistance.reach(30, 48, false) == 30f, "under the view distance the row is how far things are drawn");
        check(DrawDistance.reach(48, 48, false) == DrawDistance.WHOLE && DrawDistance.reach(80, 48, false) == DrawDistance.WHOLE,
                "at the view distance or over it nothing is left out but what the view distance leaves out");
        for (int mode = Quality.LOW; mode <= Quality.HIGH; mode++) {
            Quality.Picture preset = Quality.preset(mode);
            check(preset.drawDistance() == preset.viewDistance(),
                    "each preset draws people and props as far as its view goes, as it always has: " + preset);
        }
        custom(48, 80);
        DrawDistance reach = new DrawDistance();
        reach.take(0, 0f, 0f, 1f, 0f, false);
        check(reach.draws(0f, 79f) && reach.draws(79f, 0f), "rows over a short view distance leave nothing out, which the view distance ends anyway");
    }

    private static void onFootTheDrawDistanceIsAllRound() {
        custom(80, 20);
        DrawDistance reach = new DrawDistance();
        reach.take(0, 100f, 200f, 0f, 0f, false);
        check(reach.draws(120f, 200f) && reach.draws(100f, 180f) && reach.draws(86f, 214f), "on foot what is within the draw distance any way round is drawn");
        check(!reach.draws(121f, 200f) && !reach.draws(100f, 179f) && !reach.draws(85f, 215f), "and nothing further off");
    }

    private static void aheadOfARideTheViewDistanceCounts() {
        custom(80, 20);
        DrawDistance reach = new DrawDistance();
        // A vehicle facing along +y, south on the map.
        reach.take(0, 100f, 200f, 0f, 1f, false);
        check(reach.draws(100f, 279f) && reach.draws(150f, 260f), "on the side a ridden vehicle faces, nothing is left out that the view distance keeps");
        check(reach.draws(100f, 180f) && !reach.draws(100f, 179f), "behind it the draw distance");
        check(reach.draws(120f, 200f) && !reach.draws(121f, 200f), "and square beside it too");
    }

    private static void whereThePlayersCanFightNothingIsLeftOut() {
        custom(80, 20);
        DrawDistance reach = new DrawDistance();
        Servers.in(Servers.Where.FRIENDLY, () -> {
            reach.take(0, 0f, 0f, 0f, 0f, Online.contested());
            check(!reach.draws(30f, 0f), "among friends on a server the draw distance holds");
        });
        Servers.in(Servers.Where.CONTESTED, () -> {
            reach.take(0, 0f, 0f, 1f, 0f, Online.contested());
            check(reach.draws(79f, 0f) && reach.draws(-79f, 0f) && reach.draws(0f, 79f),
                    "where players can hurt each other nothing is left out, so no car or wardrobe is ever a wall another player is seen through");
        });
        check(DrawDistance.reach(16, 80, true) == DrawDistance.WHOLE, "however low the row is set");
    }

    private static void theGamesOwnViewLeavesNothingOut() {
        custom(80, 16);
        DrawDistance reach = new DrawDistance();
        reach.take(0, 0f, 0f, 0f, 0f, false);
        DrawDistance frame = new DrawDistance();
        frame.copy(reach);
        check(!frame.draws(17f, 0f), "a frame takes the eye's draw distance");
        frame.whole();
        check(frame.draws(500f, 500f), "and the game's own view, drawn by the mod, leaves nothing out");
    }
}

package com.cameraunlock.zomboid;

/** Tests of the rows of [Rules]: which of the game's rules the mod changes alone, and which a server keeps the game's. */
final class RulesTests {
    private RulesTests() {}

    static void run() {
        int before = Rules.rows();
        try {
            Checks.run("aloneEveryRuleIsTheModsUntilARowSaysOtherwise", RulesTests::aloneEveryRuleIsTheModsUntilARowSaysOtherwise);
            Checks.run("eachRowOffGivesBackThatRuleAndNoOther", RulesTests::eachRowOffGivesBackThatRuleAndNoOther);
            Checks.run("onAServerTheRulesItKeepsAreTheGames", RulesTests::onAServerTheRulesItKeepsAreTheGames);
        } finally {
            Rules.set(before);
        }
    }

    private static void aloneEveryRuleIsTheModsUntilARowSaysOtherwise() {
        Rules.set(Rules.ALL);
        Servers.in(Servers.Where.ALONE, () -> check(Rules.throughWalls() && Rules.breaksLamps() && Rules.breaksGlass() && Rules.wounds() && !Rules.vehiclesStock()
                        && Rules.facesTheView() && Rules.pastTrees() && Rules.seenAtOnce(),
                "alone with every row on, every rule is the mod's"));
        Servers.in(Servers.Where.ALONE, () -> check(Rules.rewind(), "and a death can be rewound"));
        Servers.in(Servers.Where.ALONE, () -> check(Rules.dressesHiddenWalls(), "and rooms' hidden walls are hung"));
    }

    private static void eachRowOffGivesBackThatRuleAndNoOther() {
        Servers.in(Servers.Where.ALONE, () -> {
            Rules.set(Rules.ALL & ~Rules.THROUGH_WALLS);
            check(!Rules.throughWalls() && Rules.breaksLamps() && Rules.breaksGlass() && Rules.wounds() && !Rules.vehiclesStock() && Rules.facesTheView(),
                    "ShotsThroughWalls off has walls stop shots and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.BREAKS_LAMPS);
            check(Rules.throughWalls() && !Rules.breaksLamps() && Rules.breaksGlass() && Rules.wounds() && !Rules.vehiclesStock() && Rules.facesTheView(),
                    "ShotsBreakLamps off leaves lamps lit and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.BREAKS_GLASS);
            check(Rules.throughWalls() && Rules.breaksLamps() && !Rules.breaksGlass() && Rules.wounds() && !Rules.vehiclesStock() && Rules.facesTheView(),
                    "ShotsBreakGlass off leaves windows whole and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.WOUNDS);
            check(Rules.throughWalls() && Rules.breaksLamps() && Rules.breaksGlass() && !Rules.wounds() && !Rules.vehiclesStock() && Rules.facesTheView(),
                    "ShotsWoundLimbs off leaves limbs to the game and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.SMASHES_THROUGH);
            check(Rules.throughWalls() && Rules.breaksLamps() && Rules.breaksGlass() && Rules.wounds() && Rules.vehiclesStock() && Rules.facesTheView(),
                    "VehiclesSmashThrough off gives vehicles the game's rules and changes nothing else");
            check(!Knockdown.smashing(Rules.vehiclesStock(), true, 50f) && !Knockdown.brushedThrough(Rules.vehiclesStock(), true, false)
                    && Knockdown.slowFactor(Rules.vehiclesStock(), 28f) == 28f && Knockdown.engineHeldOffRoad(Rules.vehiclesStock(), true),
                    "and with it off nothing is knocked down, bushes slow a car and the engine gives less off the road");
            Rules.set(Rules.ALL & ~Rules.FACES_THE_VIEW);
            check(Rules.throughWalls() && Rules.breaksLamps() && Rules.breaksGlass() && Rules.wounds() && !Rules.vehiclesStock() && !Rules.facesTheView() && Rules.pastTrees(),
                    "FacesTheView off has the character turn to the way it walks and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.PAST_TREES);
            check(Rules.throughWalls() && Rules.breaksLamps() && Rules.breaksGlass() && Rules.wounds() && !Rules.vehiclesStock() && Rules.facesTheView() && !Rules.pastTrees(),
                    "ShotsPastTrees off has a tree stop shots across its square and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.SEEN_AT_ONCE);
            check(Rules.throughWalls() && Rules.breaksLamps() && Rules.breaksGlass() && Rules.wounds() && !Rules.vehiclesStock() && Rules.facesTheView() && Rules.pastTrees()
                    && !Rules.seenAtOnce() && Rules.rewind(), "ZombiesSeenAtOnce off leaves zombies to the game's sight and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.REWIND);
            check(Rules.throughWalls() && Rules.breaksLamps() && Rules.breaksGlass() && Rules.wounds() && !Rules.vehiclesStock() && Rules.facesTheView() && Rules.pastTrees()
                    && Rules.seenAtOnce() && !Rules.rewind() && Rules.dressesHiddenWalls(), "Rewind30s off leaves a death final and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.HIDDEN_WALLS);
            check(Rules.throughWalls() && Rules.breaksLamps() && Rules.breaksGlass() && Rules.wounds() && !Rules.vehiclesStock() && Rules.facesTheView() && Rules.pastTrees()
                    && Rules.seenAtOnce() && Rules.rewind() && !Rules.dressesHiddenWalls(), "HiddenWallDecor off hangs nothing and changes nothing else");
        });
    }

    private static void onAServerTheRulesItKeepsAreTheGames() {
        Rules.set(Rules.ALL);
        for (Servers.Where where : new Servers.Where[] {Servers.Where.FRIENDLY, Servers.Where.CONTESTED}) {
            Servers.in(where, () -> check(!Rules.breaksLamps() && !Rules.breaksGlass() && !Rules.wounds() && Rules.vehiclesStock()
                    && !Rules.rewind() && !Rules.dressesHiddenWalls(),
                    "on a server (" + where + ") every rule the server keeps is the game's with every row on"));
            Servers.in(where, () -> check(Rules.drawsHiddenWallPictures(), "and on a server (" + where + ") hidden walls still carry drawn pictures"));
        }
        Servers.in(Servers.Where.FRIENDLY, () -> check(Rules.facesTheView() && Rules.seenAtOnce() && Rules.throughWalls() && Rules.pastTrees(),
                "where players cannot fight, the character faces the view, zombies are seen at once and shots go through walls and past trees"));
        Servers.in(Servers.Where.CONTESTED, () -> check(!Rules.facesTheView() && !Rules.seenAtOnce() && !Rules.throughWalls() && !Rules.pastTrees(),
                "where players can fight, the character turns, zombies keep the game's sight and walls and trees stop shots, as the game has it"));
        Rules.set(Rules.ALL & ~Rules.FACES_THE_VIEW & ~Rules.SEEN_AT_ONCE & ~Rules.HIDDEN_WALLS & ~Rules.THROUGH_WALLS & ~Rules.PAST_TREES);
        Servers.in(Servers.Where.FRIENDLY, () -> check(!Rules.facesTheView() && !Rules.seenAtOnce() && !Rules.drawsHiddenWallPictures()
                        && !Rules.throughWalls() && !Rules.pastTrees(),
                "and a row turned off is off on a server too"));
        Rules.set(Rules.ALL);
        zombie.network.GameServer.server = true;
        try {
            Servers.in(Servers.Where.ALONE, () -> check(!Rules.dressesHiddenWalls() && !Rules.drawsHiddenWallPictures(), "and a server's own process hangs and draws nothing on hidden walls"));
        } finally {
            zombie.network.GameServer.server = false;
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}

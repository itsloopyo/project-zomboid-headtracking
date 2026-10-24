package com.cameraunlock.zomboid;

/** Tests of the rows of [Rules]: which of the game's rules the mod changes alone, and that a server keeps them all the game's. */
final class RulesTests {
    private RulesTests() {}

    static void run() {
        int before = Rules.rows();
        try {
            aloneEveryRuleIsTheModsUntilARowSaysOtherwise();
            eachRowOffGivesBackThatRuleAndNoOther();
            onAServerEveryRuleIsTheGamesWhateverTheRows();
        } finally {
            Rules.set(before);
        }
    }

    private static void aloneEveryRuleIsTheModsUntilARowSaysOtherwise() {
        Rules.set(Rules.ALL);
        Servers.in(Servers.Where.ALONE, () -> check(Rules.throughWalls() && Rules.breaksLamps() && Rules.wounds() && !Rules.vehiclesStock()
                        && Rules.facesTheView() && Rules.pastTrees() && Rules.seenAtOnce(),
                "alone with every row on, every rule is the mod's"));
        Servers.in(Servers.Where.ALONE, () -> check(Rules.rewind(), "and a death can be rewound"));
    }

    private static void eachRowOffGivesBackThatRuleAndNoOther() {
        Servers.in(Servers.Where.ALONE, () -> {
            Rules.set(Rules.ALL & ~Rules.THROUGH_WALLS);
            check(!Rules.throughWalls() && Rules.breaksLamps() && Rules.wounds() && !Rules.vehiclesStock() && Rules.facesTheView(),
                    "ShotsThroughWalls off has walls stop shots and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.BREAKS_LAMPS);
            check(Rules.throughWalls() && !Rules.breaksLamps() && Rules.wounds() && !Rules.vehiclesStock() && Rules.facesTheView(),
                    "ShotsBreakLamps off leaves lamps lit and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.WOUNDS);
            check(Rules.throughWalls() && Rules.breaksLamps() && !Rules.wounds() && !Rules.vehiclesStock() && Rules.facesTheView(),
                    "ShotsWoundLimbs off leaves limbs to the game and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.SMASHES_THROUGH);
            check(Rules.throughWalls() && Rules.breaksLamps() && Rules.wounds() && Rules.vehiclesStock() && Rules.facesTheView(),
                    "VehiclesSmashThrough off gives vehicles the game's rules and changes nothing else");
            check(!Knockdown.smashing(Rules.vehiclesStock(), true, 50f) && !Knockdown.brushedThrough(Rules.vehiclesStock(), true, false)
                    && Knockdown.slowFactor(Rules.vehiclesStock(), 28f) == 28f && Knockdown.engineHeldOffRoad(Rules.vehiclesStock(), true),
                    "and with it off nothing is knocked down, bushes slow a car and the engine gives less off the road");
            Rules.set(Rules.ALL & ~Rules.FACES_THE_VIEW);
            check(Rules.throughWalls() && Rules.breaksLamps() && Rules.wounds() && !Rules.vehiclesStock() && !Rules.facesTheView() && Rules.pastTrees(),
                    "FacesTheView off has the character turn to the way it walks and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.PAST_TREES);
            check(Rules.throughWalls() && Rules.breaksLamps() && Rules.wounds() && !Rules.vehiclesStock() && Rules.facesTheView() && !Rules.pastTrees(),
                    "ShotsPastTrees off has a tree stop shots across its square and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.SEEN_AT_ONCE);
            check(Rules.throughWalls() && Rules.breaksLamps() && Rules.wounds() && !Rules.vehiclesStock() && Rules.facesTheView() && Rules.pastTrees()
                    && !Rules.seenAtOnce() && Rules.rewind(), "ZombiesSeenAtOnce off leaves zombies to the game's sight and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.REWIND);
            check(Rules.throughWalls() && Rules.breaksLamps() && Rules.wounds() && !Rules.vehiclesStock() && Rules.facesTheView() && Rules.pastTrees()
                    && Rules.seenAtOnce() && !Rules.rewind(), "Rewind30s off leaves a death final and changes nothing else");
        });
    }

    private static void onAServerEveryRuleIsTheGamesWhateverTheRows() {
        Rules.set(Rules.ALL);
        for (Servers.Where where : new Servers.Where[] {Servers.Where.FRIENDLY, Servers.Where.CONTESTED}) {
            Servers.in(where, () -> check(!Rules.throughWalls() && !Rules.breaksLamps() && !Rules.wounds() && Rules.vehiclesStock()
                    && !Rules.facesTheView() && !Rules.pastTrees() && !Rules.seenAtOnce() && !Rules.rewind(),
                    "on a server (" + where + ") every rule is the game's with every row on"));
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}

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
                && Rules.rolledBurn(), "alone with every row on, every rule is the mod's"));
    }

    private static void eachRowOffGivesBackThatRuleAndNoOther() {
        Servers.in(Servers.Where.ALONE, () -> {
            Rules.set(Rules.ALL & ~Rules.THROUGH_WALLS);
            check(!Rules.throughWalls() && Rules.breaksLamps() && Rules.wounds() && !Rules.vehiclesStock() && Rules.rolledBurn(),
                    "ShotsThroughWalls off has walls stop shots and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.BREAKS_LAMPS);
            check(Rules.throughWalls() && !Rules.breaksLamps() && Rules.wounds() && !Rules.vehiclesStock() && Rules.rolledBurn(),
                    "ShotsBreakLamps off leaves lamps lit and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.WOUNDS);
            check(Rules.throughWalls() && Rules.breaksLamps() && !Rules.wounds() && !Rules.vehiclesStock() && Rules.rolledBurn(),
                    "ShotsWoundLimbs off leaves limbs to the game and changes nothing else");
            Rules.set(Rules.ALL & ~Rules.SMASHES_THROUGH);
            check(Rules.throughWalls() && Rules.breaksLamps() && Rules.wounds() && Rules.vehiclesStock() && Rules.rolledBurn(),
                    "VehiclesSmashThrough off gives vehicles the game's rules and changes nothing else");
            check(!Knockdown.smashing(Rules.vehiclesStock(), true, 50f) && !Knockdown.brushedThrough(Rules.vehiclesStock(), true, false)
                    && Knockdown.slowFactor(Rules.vehiclesStock(), 28f) == 28f && Knockdown.engineHeldOffRoad(Rules.vehiclesStock(), true),
                    "and with it off nothing is knocked down, bushes slow a car and the engine gives less off the road");
            Rules.set(Rules.ALL & ~Rules.ROLLED_BURN);
            check(Rules.throughWalls() && Rules.breaksLamps() && Rules.wounds() && !Rules.vehiclesStock() && !Rules.rolledBurn(),
                    "RolledVehiclesBurn off has no vehicle catch fire on its roof and changes nothing else");
        });
    }

    private static void onAServerEveryRuleIsTheGamesWhateverTheRows() {
        Rules.set(Rules.ALL);
        for (Servers.Where where : new Servers.Where[] {Servers.Where.FRIENDLY, Servers.Where.CONTESTED}) {
            Servers.in(where, () -> check(!Rules.throughWalls() && !Rules.breaksLamps() && !Rules.wounds() && Rules.vehiclesStock()
                    && !Rules.rolledBurn(), "on a server (" + where + ") every rule is the game's with every row on"));
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}

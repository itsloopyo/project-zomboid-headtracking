package com.cameraunlock.zomboid;

/**
 * What a round goes through: see Penetration. A stud wall of plasterboard lets any round but a
 * shotgun's pellets through a few times over, a wall of boards and a door spend more, and masonry,
 * metal plate and whatever the game names no material for stop every round.
 */
final class PenetrationTests {
    private PenetrationTests() {}

    static void run() {
        Checks.run("masonryMetalAndWhatHasNoMaterialStopEveryRound", PenetrationTests::masonryMetalAndWhatHasNoMaterialStopEveryRound);
        Checks.run("plasterboardCostsLeastAndASolidDoorMost", PenetrationTests::plasterboardCostsLeastAndASolidDoorMost);
        Checks.run("aRifleRoundGoesFurtherThanAHandgunsAndAHandgunsThanAPellet", PenetrationTests::aRifleRoundGoesFurtherThanAHandgunsAndAHandgunsThanAPellet);
        Checks.run("whatIsLeftOfTheBudgetIsTheShareOfTheDamage", PenetrationTests::whatIsLeftOfTheBudgetIsTheShareOfTheDamage);
        Checks.run("whereTheRoundsGoAsTheOwnerAsked", PenetrationTests::whereTheRoundsGoAsTheOwnerAsked);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean through(Penetration.Cartridge cartridge, String... walls) {
        float spent = 0f;
        for (String wall : walls) {
            spent += Penetration.cost(wall);
        }
        return Penetration.left(cartridge.budget, spent) > 0f;
    }

    private static void masonryMetalAndWhatHasNoMaterialStopEveryRound() {
        for (String material : new String[] {"Brick", "Cinderblock", "Concrete", "Stone", "Ceramic", "Metal", "Metal_Large", "Sand", "Rubber"}) {
            check(Penetration.cost(material) == Penetration.STOPS, material + " stops a round");
            check(!through(Penetration.Cartridge.RIFLE_308, material), "a hunting rifle's round too, through " + material);
        }
        check(Penetration.cost(null) == Penetration.STOPS, "a wall the game names no material for stops it, as the game has it");
        check(Penetration.cost("Unheard") == Penetration.STOPS, "and so does a material the table does not know");
    }

    private static void plasterboardCostsLeastAndASolidDoorMost() {
        check(Penetration.cost("Fabric") == 0f, "a curtain costs a round nothing");
        check(Penetration.cost("Glass") < Penetration.cost("Plaster"), "glass costs less than a stud wall");
        check(Penetration.cost("Plaster") < Penetration.cost("Wood"), "a stud wall less than a wall of boards");
        check(Penetration.cost("Wood") < Penetration.cost("Wood_Solid"), "a wall of boards less than a solid door");
        check(Penetration.cost("Plaster") == 1f, "a stud wall is the unit the budgets are counted in");
    }

    private static void aRifleRoundGoesFurtherThanAHandgunsAndAHandgunsThanAPellet() {
        Penetration.Cartridge[] order = Penetration.Cartridge.values();
        for (int i = 1; i < order.length; i++) {
            check(order[i].budget >= order[i - 1].budget, order[i] + " goes at least as far as " + order[i - 1]);
        }
        check(Penetration.Cartridge.RIFLE_308.budget > Penetration.Cartridge.PISTOL_9MM.budget, "a hunting rifle's round goes further than a 9mm's");
        check(Penetration.Cartridge.PISTOL_9MM.budget > Penetration.Cartridge.SHOT.budget, "and a 9mm's further than a shotgun's pellets");
    }

    private static void whatIsLeftOfTheBudgetIsTheShareOfTheDamage() {
        check(Penetration.left(4f, 0f) == 1f, "through nothing a round does all its damage");
        check(Math.abs(Penetration.left(4f, 1f) - 0.75f) < 1e-6f, "a quarter of the budget spent leaves three quarters of the damage");
        check(Penetration.left(4f, 4f) == 0f, "a budget spent to the end does nothing");
        check(Penetration.left(4f, Penetration.STOPS) == 0f, "nor does a round a wall stopped");
    }

    /** The owner's own cases: brick stops it, plasterboard, glass and a wooden door do not. */
    private static void whereTheRoundsGoAsTheOwnerAsked() {
        for (Penetration.Cartridge cartridge : Penetration.Cartridge.values()) {
            check(!through(cartridge, "Brick"), cartridge + " stops on brick");
            check(through(cartridge, "Plaster"), cartridge + " goes through plasterboard");
            check(through(cartridge, "Glass"), cartridge + " goes through a pane of glass");
        }
        check(through(Penetration.Cartridge.PISTOL_9MM, "Wood"), "a 9mm round goes through a hollow wooden door");
        check(through(Penetration.Cartridge.PISTOL_9MM, "Wood_Solid"), "and through a solid one");
        check(!through(Penetration.Cartridge.SHOT, "Wood"), "a shotgun's pellets stop in a wooden door");
        check(through(Penetration.Cartridge.RIFLE_308, "Wood", "Plaster", "Plaster", "Wood_Solid"),
                "a hunting rifle's round goes through a house's outer wall, two inner walls and a solid door");
    }
}

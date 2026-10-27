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
        Checks.run("aDoorWithNoMaterialIsToldByItsSound", PenetrationTests::aDoorWithNoMaterialIsToldByItsSound);
        Checks.run("aShotThroughADoorsLightIsTold", PenetrationTests::aShotThroughADoorsLightIsTold);
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

    /**
     * Placed in the shipped map with a DoorSound and no MaterialType: WoodDoor 7,336, FarmGate 1,951,
     * MetalGate 1,928, GarageDoor 1,324, WoodGateSmall 1,009, SlidingGlassDoor 335, the metal pole gates
     * 332 (census of the map's placements).
     */
    private static void aDoorWithNoMaterialIsToldByItsSound() {
        check("Wood".equals(Penetration.doorMaterialBySound("WoodDoor")), "a door that sounds wooden is a wooden door");
        check("Wood".equals(Penetration.doorMaterialBySound("FarmGate")) && "Wood".equals(Penetration.doorMaterialBySound("WoodGateSmall")), "and so are the wooden gates");
        check("Metal_Light".equals(Penetration.doorMaterialBySound("GarageDoor")), "a garage door is sheet metal");
        check("Glass_Solid".equals(Penetration.doorMaterialBySound("SlidingGlassDoor")), "a sliding door is glass");
        check(Penetration.BARS.equals(Penetration.doorMaterialBySound("MetalGate")) && Penetration.cost(Penetration.BARS) < Penetration.cost("Plaster"),
                "a metal gate is bars, which cost a round less than a stud wall");
        check(Penetration.doorMaterialBySound("Unheard") == null, "a sound the table does not know names nothing, and the door stops a round");
        check(Penetration.doorMaterialBySound(null) == null, "nor does a door with no sound");
        check(Penetration.cost("Glass_Solid") < Penetration.cost("Plaster"), "a glass door costs less than a stud wall");
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

    /** A door with one light high in it: the glass lets a shot by, the wood round it and under it does not. */
    private static void aShotThroughADoorsLightIsTold() {
        float[] panes = {0.3f, 0.7f, 0.55f, 0.8f, 1f};
        check(Penetration.inALight(panes, 0.5f, 0.65f), "a shot through the light's middle is in it");
        check(!Penetration.inALight(panes, 0.5f, 0.4f), "one through the leaf under it is not");
        check(!Penetration.inALight(panes, 0.2f, 0.65f), "nor one through the stile beside it");
        check(!Penetration.inALight(new float[0], 0.5f, 0.65f), "and a leaf with no light has no glass to go through");
    }
}

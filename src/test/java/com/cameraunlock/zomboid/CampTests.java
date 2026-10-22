package com.cameraunlock.zomboid;

import zombie.iso.sprite.IsoSprite;

/** Tests of which model stands for a sprite of a camp, an army post or a survivor's workshop, and which way it is turned. */
final class CampTests {
    private CampTests() {}

    static void run() {
        Checks.run("everyCampPieceNamesAModelTheJarHolds", CampTests::everyCampPieceNamesAModelTheJarHolds);
        Checks.run("aBigTentHasItsDoorWhereItsSpritePaintsIt", CampTests::aBigTentHasItsDoorWhereItsSpritePaintsIt);
        Checks.run("aBigTentIsStoodByTheSquareItsColourIsTakenOn", CampTests::aBigTentIsStoodByTheSquareItsColourIsTakenOn);
        Checks.run("aSleepingBagHasItsHeadWhereItsSpritePaintsItAndIsColouredAtItsFoot", CampTests::aSleepingBagHasItsHeadWhereItsSpritePaintsItAndIsColouredAtItsFoot);
        Checks.run("aCrateOnACrateIsRaisedByTheOneUnderIt", CampTests::aCrateOnACrateIsRaisedByTheOneUnderIt);
        Checks.run("aTargetStandsWhereItsSpritePaintsItsPlate", CampTests::aTargetStandsWhereItsSpritePaintsItsPlate);
        Checks.run("aCageTrapIsLongTheWayItsSpriteIs", CampTests::aCageTrapIsLongTheWayItsSpriteIs);
        Checks.run("aLitFireAndWhatIsNotOfTheseSheetsIsNoPiece", CampTests::aLitFireAndWhatIsNotOfTheseSheetsIsNoPiece);
    }

    private static void holds(Outdoors.Piece piece, String what) {
        Checks.check(piece != null, what);
        if (piece != null) {
            Models.get(piece.model);
        }
    }

    private static Outdoors.Piece unnamed(String name) {
        return Camp.of(name, null, null, Outdoors.NONE, 1, 1, 0);
    }

    private static float[] onMap(Outdoors.Piece piece, float x, float y) {
        float[] out = new float[2];
        Models.turn(piece.turns, x, y, out);
        return out;
    }

    private static void everyCampPieceNamesAModelTheJarHolds() {
        holds(Camp.of("camping_04_68", "Tent", "Brown", Outdoors.EAST, 2, 4, 0), "a big tent");
        holds(Camp.of("camping_01_0", "Tent", "Small", Outdoors.EAST, 1, 2, 0), "a small tent");
        holds(Camp.of("camping_02_0", "Plaid Sleeping Bag", "Red", Outdoors.NORTH, 2, 1, 0), "a sleeping bag");
        holds(Camp.of("camping_03_8", "Shelter", "Tarp", Outdoors.NORTH, 2, 1, 0), "a tarp on poles");
        holds(Camp.of("carpentry_02_76", "Mattress", "Makeshift", Outdoors.EAST, 2, 1, 5), "a mattress");
        holds(Camp.of("carpentry_02_72", "Bed", "Crafted", Outdoors.EAST, 2, 1, 21), "a bed of planks");
        holds(Camp.of("crafted_01_68", "Table", "Pottery", Outdoors.EAST, 1, 2, 32), "a long table under a cloth");
        holds(Camp.of("crafted_05_80", "Drying Rack", "Large", Outdoors.SOUTH, 2, 2, 0), "a drying rack");
        for (String kind : new String[] {"Exceptionally crafted", "Poor quality", "Makeshift"}) {
            holds(Camp.of("carpentry_01_44", "Chair", kind, Outdoors.SOUTH, 1, 1, 17), "a chair " + kind);
        }
        for (String table : new String[] {"Exceptional Table", "Poor Quality Table", "Shoddy Table"}) {
            holds(Camp.of("carpentry_01_62", table, null, Outdoors.NONE, 1, 1, 34), "an " + table);
        }
        holds(Camp.of("carpentry_02_8", "Table", "Quality", Outdoors.EAST, 1, 1, 15), "a low table with a drawer");
        holds(Camp.of("carpentry_02_64", "Shelves", "Crafted Wooden", Outdoors.SOUTH, 1, 1, 80), "shelves");
        holds(Camp.of("camping_01_6", "Campfire", null, Outdoors.NONE, 1, 1, 0), "a cold campfire");
        holds(Camp.of("camping_03_19", "Cooking Pit", null, Outdoors.NONE, 1, 1, 19), "a cooking pit");
        holds(Camp.of("camping_03_16", "Cooking Pit", "Simple", Outdoors.NONE, 1, 1, 0), "a fire with a bar over it");
        holds(Camp.of("crafted_05_56", "Burn Barrel", null, Outdoors.NONE, 1, 1, 0), "a burn barrel");
        holds(Camp.of("constructedobjects_01_0", "Candle", null, Outdoors.NONE, 1, 1, 0), "a candle");
        for (String sprite : new String[] {"carpentry_02_94", "carpentry_02_95", "crafted_05_8", "crafted_05_54", "crafted_01_38", "camping_01_16", "location_military_generic_01_65",
                "constructedobjects_01_8", "constructedobjects_01_9", "constructedobjects_01_10", "constructedobjects_01_11"}) {
            holds(unnamed(sprite), sprite + " has a model");
        }
    }

    private static void aBigTentHasItsDoorWhereItsSpritePaintsIt() {
        // The door is the model's front, -y. A tent the game says faces east has its door to the south, and so round.
        int[][] doors = {{Outdoors.NORTH, -1, 0}, {Outdoors.EAST, 0, 1}, {Outdoors.SOUTH, 1, 0}, {Outdoors.WEST, 0, -1}};
        for (int[] tent : doors) {
            boolean alongY = tent[0] == Outdoors.EAST || tent[0] == Outdoors.WEST;
            Outdoors.Piece piece = Camp.of("camping_04_0", "Tent", "Yellow", tent[0], alongY ? 2 : 4, alongY ? 4 : 2, 0);
            float[] door = onMap(piece, 0f, -1f);
            Checks.check(door[0] == tent[1] && door[1] == tent[2], "a tent facing " + tent[0] + " has its door to " + tent[1] + ", " + tent[2] + ", not " + door[0] + ", " + door[1]);
            Checks.check(piece.dyeX == 1 && piece.dyeY == 1 && piece.fits, "and is coloured from a square its fly is painted on");
        }
    }

    private static void aBigTentIsStoodByTheSquareItsColourIsTakenOn() {
        // A green tent facing south, 112 to 119: its first square paints nothing, and the world never asks it to stand.
        IsoSprite[] squares = new IsoSprite[8];
        for (int i = 0; i < 8; i++) {
            squares[i] = TileRows.sprite("camping_04_" + (112 + i) + "|CustomName|Tent|GroupName|Green|Facing|S|SpriteGridPos|" + i % 4 + "," + i / 4);
        }
        TileRows.piece(4, 2, squares);
        int standing = 0;
        for (IsoSprite square : squares) {
            Furniture piece = Furniture.known(square);
            Checks.check(piece != null && piece.model.name.equals("ridge_tent"), square.getName() + " is a square of the ridge tent");
            boolean stands = FurnishingBuilder.stands(piece, false, new int[] {-piece.gridX, -piece.gridY});
            Checks.check(!stands || piece.gridX == piece.dyeX && piece.gridY == piece.dyeY, square.getName() + " stands it only where it gives its colour");
            Checks.check(FurnishingBuilder.stands(piece, true, new int[] {-piece.gridX, -piece.gridY}) == (square == squares[0]),
                    "where the first square paints, it stands the piece as every piece's does");
            standing += stands ? 1 : 0;
        }
        Checks.check(standing == 1, "one square stands the tent, not " + standing);
    }

    private static void aSleepingBagHasItsHeadWhereItsSpritePaintsItAndIsColouredAtItsFoot() {
        // The head is the model's back, +y. {facing, head x, head y, foot square x, y}
        int[][] bags = {{Outdoors.NORTH, -1, 0, 1, 0}, {Outdoors.EAST, 0, 1, 0, 0}, {Outdoors.SOUTH, 1, 0, 0, 0}, {Outdoors.WEST, 0, -1, 0, 1}};
        for (int[] bag : bags) {
            boolean alongX = bag[0] == Outdoors.NORTH || bag[0] == Outdoors.SOUTH;
            Outdoors.Piece piece = Camp.of("camping_02_32", "Sleeping Bag", "Camo", bag[0], alongX ? 2 : 1, alongX ? 1 : 2, 0);
            float[] head = onMap(piece, 0f, 1f);
            Checks.check(head[0] == bag[1] && head[1] == bag[2], "a bag facing " + bag[0] + " has its head to " + bag[1] + ", " + bag[2]);
            Checks.check(piece.dyeX == bag[3] && piece.dyeY == bag[4], "and takes its colour on the square at its foot");
        }
    }

    private static void aCrateOnACrateIsRaisedByTheOneUnderIt() {
        Outdoors.Piece second = Camp.of("location_military_generic_01_2", "Crate", "Military", Outdoors.NONE, 1, 1, 64);
        Outdoors.Piece third = Camp.of("location_military_generic_01_4", "Crate", "Military", Outdoors.NONE, 1, 1, 96);
        float crate = ViewCamera.LEVEL_HEIGHT / 3f;
        Checks.check(Math.abs(second.raised - crate) < 1e-4f && Math.abs(third.raised - 2f * crate) < 1e-4f, "one crate up and two: " + second.raised + ", " + third.raised);
        Checks.check(Math.abs(second.top - crate) < 1e-4f && second.fits, "each is a crate high");
        Checks.check(Math.abs(third.dyeHigh - second.dyeHigh - 1f / 3f) < 1e-5f, "and takes its colour as much higher on its sprite");
        Outdoors.Piece metal = Camp.of("constructedobjects_01_44", "Crate", "Metal", Outdoors.NORTH, 1, 1, 32);
        Checks.check(metal != null && metal.raised == 0f, "a crate of boards and steel on the floor stands on it");
    }

    private static void aTargetStandsWhereItsSpritePaintsItsPlate() {
        Outdoors.Piece south = unnamed("location_military_generic_01_66"), west = unnamed("location_military_generic_01_64");
        Checks.check(south.b > 0.9f && onMap(south, 0f, -1f)[1] == 1f, "a target facing south stands at the south edge, its face to the south");
        Checks.check(west.a < 0.1f && onMap(west, 0f, -1f)[0] == -1f, "one facing west at the west edge, its face to the west");
    }

    private static void aCageTrapIsLongTheWayItsSpriteIs() {
        Checks.check(onMap(unnamed("constructedobjects_01_9"), 0f, 1f)[1] == 0f && onMap(unnamed("constructedobjects_01_10"), 0f, 1f)[0] == 0f,
                "one cage trap lies along x and the other along y");
        Checks.check(onMap(unnamed("constructedobjects_01_8"), 0f, -1f)[0] == -1f && onMap(unnamed("constructedobjects_01_11"), 0f, -1f)[1] == -1f,
                "a set trap has its door up at its west end on one sprite and its north end on the other");
    }

    private static void aLitFireAndWhatIsNotOfTheseSheetsIsNoPiece() {
        Checks.check(Camp.of("camping_01_4", "Campfire", null, Outdoors.NONE, 1, 1, 0) == null && Camp.of("camping_03_20", "Cooking Pit", null, Outdoors.NONE, 1, 1, 19) == null,
                "a fire that burns keeps the game's sprite with its flames");
        Checks.check(Camp.of("constructedobjects_01_1", "Candle", null, Outdoors.NONE, 1, 1, 0) == null, "and so does a lit candle");
        Checks.check(Camp.of("furniture_tents_01_0", "Tent", "Brown", Outdoors.EAST, 2, 4, 0) == null, "a tent of another sheet");
        Checks.check(Camp.of("camping_04_0", "Tent", "Yellow", Outdoors.NONE, 2, 4, 0) == null, "a tent with no facing");
        Checks.check(unnamed("carpentry_02_103") == null && unnamed("carpentry_02_102") == null, "a lone post, and a wall's frame, which is a wall");
        Checks.check(Camp.of("crafted_05_62", "Burn Barrel", null, Outdoors.NONE, 1, 1, 0) == null, "a small barrel the drum is not");
        Checks.check(Camp.of("location_military_generic_01_0", "Crate", "Military", Outdoors.NONE, 1, 1, 32) == null, "an army crate on the floor, which is Furniture's");
    }
}

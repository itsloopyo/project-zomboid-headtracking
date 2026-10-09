package com.cameraunlock.zomboid;

/** Tests of which model stands for a sprite out of doors, and which way it is turned. */
final class OutdoorTests {
    private OutdoorTests() {}

    static void run() {
        Checks.run("everyPieceNamesAModelTheJarHolds", OutdoorTests::everyPieceNamesAModelTheJarHolds);
        Checks.run("aDuctRunsTheWayItsSpriteDoes", OutdoorTests::aDuctRunsTheWayItsSpriteDoes);
        Checks.run("anElbowsEndsAreWhereItsSpritePaintsThem", OutdoorTests::anElbowsEndsAreWhereItsSpritePaintsThem);
        Checks.run("aPipeLiesAlongTheEdgeItsSpritePaintsItOn", OutdoorTests::aPipeLiesAlongTheEdgeItsSpritePaintsItOn);
        Checks.run("whatStandsOnACaseIsRaisedAndTakesItsColourUpThere", OutdoorTests::whatStandsOnACaseIsRaisedAndTakesItsColourUpThere);
        Checks.run("aDeckChairIsTwoSquaresLongTheWayItFaces", OutdoorTests::aDeckChairIsTwoSquaresLongTheWayItFaces);
        Checks.run("aGravestoneStandsTowardsTheSideItFaces", OutdoorTests::aGravestoneStandsTowardsTheSideItFaces);
        Checks.run("aSpriteOfAnotherSheetOrShapeIsNoPiece", OutdoorTests::aSpriteOfAnotherSheetOrShapeIsNoPiece);
    }

    /** A piece, and its model one the jar has: Models.get throws for a name it has not. */
    private static void holds(Outdoors.Piece piece, String what) {
        Checks.check(piece != null, what);
        if (piece != null) {
            Models.get(piece.model);
        }
    }

    private static Outdoors.Piece unnamed(String name) {
        return Outdoors.of(name, null, null, Outdoors.NONE, 1, 1, 0);
    }

    private static void everyPieceNamesAModelTheJarHolds() {
        int[] works = {32, 33, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 224, 225, 226, 227, 228, 229, 230, 231};
        for (int place : works) {
            Outdoors.Piece piece = unnamed("industry_02_" + place);
            holds(piece, "industry_02_" + place + " has a model");
        }
        for (int place : new int[] {0, 1, 14, 15, 16, 22, 23, 24, 25}) {
            Outdoors.Piece piece = unnamed("rooftop_furniture_" + place);
            holds(piece, "rooftop_furniture_" + place + " has a model");
        }
        for (String sprite : new String[] {"industry_01_40", "industry_01_41", "industry_01_42", "industry_01_43", "industry_01_44", "industry_01_45",
                "construction_01_42", "construction_01_43"}) {
            Outdoors.Piece piece = unnamed(sprite);
            holds(piece, sprite + " has a model");
        }
        Checks.check(Outdoors.washingLine("appliances_laundry_01_30") == Outdoors.LINE_EAST && Outdoors.washingLine("appliances_laundry_01_31") == Outdoors.LINE_SOUTH,
                "a washing line runs to the east on one sprite and to the south on the next");
        Checks.check(Outdoors.washingLine("appliances_laundry_01_26") == Outdoors.NO_LINE && Outdoors.washingLine("appliances_com_01_30") == Outdoors.NO_LINE
                && unnamed("appliances_laundry_01_30") == null, "its pole is no line, nor is a wire, and a line is no model");
        Outdoors.Piece sign = Outdoors.of("street_decoration_01_1", "Sign", "Stop", Outdoors.EAST, 1, 1, 0);
        Models.get(sign.model);
        Checks.check(sign.turns == 1 && sign.a < 0.5f && sign.b > 0.5f, "a stop sign that faces east has a model, by the corner its sprite paints it at");
        Outdoors.Piece truck = Outdoors.of("industry_03_72", "Hand Truck", "Empty", Outdoors.EAST, 1, 1, 0);
        Models.get(truck.model);
        Checks.check(truck.turns == 1 && truck.a == 0.25f && truck.b == 0.5f, "a stock trolley that faces east stands in the west half of its square");
        Checks.check(Outdoors.of("industry_03_64", "Hand Truck", "Oxygen Tank in", Outdoors.EAST, 1, 1, 0) == null, "one with a load on it stays as it is");
        Models.get("twin_head");
        String[][] named = {{"Recycle Bin", ""}, {"Standpipe", ""}, {"Empty Pallet", ""},
                {"Pallet With Bricks", ""}};
        for (String[] thing : named) {
            Outdoors.Piece piece = Outdoors.of("sheet_01_0", thing[0], thing[1], Outdoors.NONE, 1, 1, 0);
            holds(piece, thing[0] + " has a model");
        }
        for (String kind : new String[] {"Round", "Arched", "Square"}) {
            Outdoors.Piece piece = Outdoors.of("location_community_cemetary_01_0", "Gravestone", kind, Outdoors.SOUTH, 1, 1, 0);
            holds(piece, "a " + kind + " gravestone has a model");
        }
        holds(Outdoors.of("street_decoration_01_26", "Road Cone", null, Outdoors.NONE, 1, 1, 0), "a cone has a model");
        holds(Outdoors.of("street_decoration_01_27", "Road Cone", null, Outdoors.NONE, 1, 1, 0), "a striped post has a model");
        holds(Outdoors.of("fencing_01_96", "Military Barrier", "Barricade", Outdoors.NONE, 1, 1, 0), "a barrier has a model");
        holds(Outdoors.of("furniture_seating_outdoor_01_32", "Bench", "Birchwood", Outdoors.SOUTH, 1, 1, 14), "a street bench has a model");
        Checks.check(Outdoors.of("furniture_seating_outdoor_01_33", "Bench", "Birchwood", Outdoors.SOUTH, 2, 1, 14).each, "and one of two laid end to end is a bench of its own");
        Checks.check(Outdoors.of("furniture_seating_outdoor_01_20", "Bench", "Oakwood", Outdoors.SOUTH, 2, 1, 12) == null, "a bench of oak planks two squares long is left to the benches indoors");
        holds(Outdoors.of("camping_01_24", "Stack", "6 Log", Outdoors.EAST, 2, 1, 0), "a stack of six logs has a model");
        holds(Outdoors.of("camping_01_35", "Stack", "5 Log", Outdoors.SOUTH, 1, 2, 0), "a stack of five logs has a model");
        Checks.check(Outdoors.of("camping_01_26", "Stack", "6 Log", Outdoors.EAST, 2, 1, 17) == null, "a stack painted up on a load stays as it is");
    }

    /** Where a way along the floor of a piece's model points on the map. */
    private static float[] onMap(Outdoors.Piece piece, float x, float y) {
        float[] out = new float[2];
        Models.turn(piece.turns, x, y, out);
        return out;
    }

    private static void aDuctRunsTheWayItsSpriteDoes() {
        // A duct's model is long along its y.
        float[] along = onMap(unnamed("industry_02_148"), 0f, 1f);
        Checks.check(along[0] == 0f && Math.abs(along[1]) == 1f, "industry_02_148 runs along the map's y");
        along = onMap(unnamed("industry_02_149"), 0f, 1f);
        Checks.check(Math.abs(along[0]) == 1f && along[1] == 0f, "industry_02_149 runs along the map's x");
        // The end that turns down is the model's back, +y.
        float[] down = onMap(unnamed("industry_02_142"), 0f, 1f);
        Checks.check(down[0] == 0f && down[1] == 1f, "industry_02_142 turns down at its south end");
        down = onMap(unnamed("industry_02_143"), 0f, 1f);
        Checks.check(down[0] == 1f && down[1] == 0f, "industry_02_143 turns down at its east end");
        down = onMap(unnamed("industry_02_140"), 0f, 1f);
        Checks.check(down[0] == 0f && down[1] == -1f, "industry_02_140 turns down at its north end");
        down = onMap(unnamed("industry_02_141"), 0f, 1f);
        Checks.check(down[0] == -1f && down[1] == 0f, "industry_02_141 turns down at its west end");
    }

    private static void anElbowsEndsAreWhereItsSpritePaintsThem() {
        // An elbow's ends are the model's front, -y, and its +x. Each sprite's two ends as the map's x and y.
        int[][] ends = {{144, 1, 0, 0, -1}, {145, 0, 1, 1, 0}, {146, -1, 0, 0, 1}, {147, 0, -1, -1, 0}};
        for (int[] elbow : ends) {
            Outdoors.Piece piece = unnamed("industry_02_" + elbow[0]);
            float[] front = onMap(piece, 0f, -1f), side = onMap(piece, 1f, 0f);
            Checks.check(front[0] == elbow[1] && front[1] == elbow[2] && side[0] == elbow[3] && side[1] == elbow[4],
                    "industry_02_" + elbow[0] + " has its ends to " + elbow[1] + ", " + elbow[2] + " and " + elbow[3] + ", " + elbow[4]
                            + ", not " + front[0] + ", " + front[1] + " and " + side[0] + ", " + side[1]);
        }
    }

    private static void aPipeLiesAlongTheEdgeItsSpritePaintsItOn() {
        Outdoors.Piece west = unnamed("industry_02_224"), north = unnamed("industry_02_226"), south = unnamed("industry_02_230"), east = unnamed("industry_02_231");
        Checks.check(west.a < 0.2f && west.b == 0.5f && onMap(west, 0f, 1f)[0] == 0f, "industry_02_224 lies along y by the west edge");
        Checks.check(east.a > 0.8f && east.b == 0.5f && onMap(east, 0f, 1f)[0] == 0f, "industry_02_231 lies along y by the east edge");
        Checks.check(north.b < 0.2f && north.a == 0.5f && onMap(north, 0f, 1f)[1] == 0f, "industry_02_226 lies along x by the north edge");
        Checks.check(south.b > 0.8f && south.a == 0.5f && onMap(south, 0f, 1f)[1] == 0f, "industry_02_230 lies along x by the south edge");
        // A corner's two pipes are on the model's +x side and at its front.
        int[][] sides = {{225, -1, 0, 0, -1}, {229, 0, -1, 1, 0}, {228, 1, 0, 0, 1}, {227, 0, 1, -1, 0}};
        for (int[] corner : sides) {
            Outdoors.Piece piece = unnamed("industry_02_" + corner[0]);
            float[] side = onMap(piece, 1f, 0f), front = onMap(piece, 0f, -1f);
            Checks.check(side[0] == corner[1] && side[1] == corner[2] && front[0] == corner[3] && front[1] == corner[4],
                    "industry_02_" + corner[0] + " has its pipes on the sides " + corner[1] + ", " + corner[2] + " and " + corner[3] + ", " + corner[4]);
        }
    }

    private static void whatStandsOnACaseIsRaisedAndTakesItsColourUpThere() {
        Outdoors.Piece onRoof = unnamed("rooftop_furniture_22"), onLow = unnamed("rooftop_furniture_23"), onTall = unnamed("rooftop_furniture_16");
        Checks.check(onRoof.raised == 0f, "the ventilator on the roof stands on it");
        Checks.check(onLow.raised > 1.1f && onLow.raised < 1.25f, "the one on a low case is raised by the case: " + onLow.raised);
        Checks.check(onTall.raised > 2.3f && onTall.raised < 2.5f, "the one on a tall case by a level: " + onTall.raised);
        Checks.check(Math.abs(onLow.dyeHigh - onRoof.dyeHigh - onLow.raised / ViewCamera.LEVEL_HEIGHT) < 1e-5f, "and its colour is taken as much higher on its sprite");
        float low = Models.get(unnamed("rooftop_furniture_1").model).height(), tall = Models.get(unnamed("rooftop_furniture_0").model).height();
        Checks.check(Math.abs(low - onLow.raised) < 0.01f && Math.abs(tall - onTall.raised) < 0.01f, "each case is as high as what stands on it is raised: " + low + ", " + tall);
    }

    private static void aDeckChairIsTwoSquaresLongTheWayItFaces() {
        Checks.check(Outdoors.of("furniture_seating_outdoor_01_25", "Chair", "Beach", Outdoors.SOUTH, 1, 2, 17).turns == 2, "a chair that faces south is two squares along y");
        Checks.check(Outdoors.of("furniture_seating_outdoor_01_26", "Chair", "Beach", Outdoors.EAST, 2, 1, 17).turns == 1, "one that faces east two along x");
        Checks.check(Outdoors.of("furniture_seating_outdoor_01_26", "Chair", "Beach", Outdoors.EAST, 1, 2, 17) == null, "and one laid out across the way it faces is none of the mod's");
    }

    private static void aGravestoneStandsTowardsTheSideItFaces() {
        Outdoors.Piece south = Outdoors.of("location_community_cemetary_01_0", "Gravestone", "Round", Outdoors.SOUTH, 1, 1, 0);
        Outdoors.Piece east = Outdoors.of("location_community_cemetary_01_1", "Gravestone", "Round", Outdoors.EAST, 1, 1, 0);
        Checks.check(south.a == 0.5f && south.b > 0.6f && south.dyeB == south.b, "a stone that faces south stands south of its square's middle and is coloured from there");
        Checks.check(east.b == 0.5f && east.a > 0.6f && east.dyeA == east.a, "and one that faces east stands east of it");
    }

    private static void aSpriteOfAnotherSheetOrShapeIsNoPiece() {
        Checks.check(unnamed("industry_02_1480") == null && unnamed("industry_02_") == null && unnamed("industry_02_14x") == null, "a name that is not a place on the sheet");
        Checks.check(unnamed("industry_03_148") == null && unnamed("industry_02_151") == null, "another sheet, and a place with no model");
        Checks.check(unnamed("industry_01_46") == null && unnamed("construction_01_44") == null, "what else is on the sheets of felt and of girders");
        Checks.check(Outdoors.of("industry_02_148", null, null, Outdoors.NONE, 2, 1, 0) == null, "a duct of two squares");
        Checks.check(Outdoors.of("fencing_01_97", "Military Barrier", "Barricade", Outdoors.NONE, 1, 1, 0) == null, "another piece of a barrier");
        Checks.check(Outdoors.of("trashcontainers_01_17", "Green Garbage Bin", null, Outdoors.NONE, 1, 1, 0) == null
                && Outdoors.of("trashcontainers_01_20", "Round Bin", null, Outdoors.NONE, 1, 1, 0) == null, "a bin that is as often in a room is left to the pieces indoors");
        Checks.check(Outdoors.of("x_01_0", "Gravestone", "Battered", Outdoors.SOUTH, 1, 1, 0) == null, "a broken gravestone");
    }
}

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
        Checks.run("aStackOfTyresIsAsManyTyresAsItsSpritePaintsAndStaysOnItsSquare", OutdoorTests::aStackOfTyresIsAsManyTyresAsItsSpritePaintsAndStaysOnItsSquare);
        Checks.run("aRainBarrelIsABarrelWhateverIsOverItsMouth", OutdoorTests::aRainBarrelIsABarrelWhateverIsOverItsMouth);
        Checks.run("aPottedPlantIsItsKindInItsState", OutdoorTests::aPottedPlantIsItsKindInItsState);
        Checks.run("aFencePostStandsWhereItsSpritePaintsItsFoot", OutdoorTests::aFencePostStandsWhereItsSpritePaintsItsFoot);
        Checks.run("aPublicLitterBinIsTheSlattedBin", OutdoorTests::aPublicLitterBinIsTheSlattedBin);
        Checks.run("aConcreteBarrierLiesAlongTheEdgeBehindTheWayItFaces", OutdoorTests::aConcreteBarrierLiesAlongTheEdgeBehindTheWayItFaces);
        Checks.run("whatStandsAlongAStreetHasAModelAsBigAsItsSprite", OutdoorTests::whatStandsAlongAStreetHasAModelAsBigAsItsSprite);
        Checks.run("aPetrolPumpIsStoodByOneOfItsTwoSpritesWhileTheOtherIsThere", OutdoorTests::aPetrolPumpIsStoodByOneOfItsTwoSpritesWhileTheOtherIsThere);
        Checks.run("aHeapOfSandIsOneModelOverItsFourSquares", OutdoorTests::aHeapOfSandIsOneModelOverItsFourSquares);
        Checks.run("aWarningSignsPlateShowsItsSpriteFacingTheWayItIsPainted", OutdoorTests::aWarningSignsPlateShowsItsSpriteFacingTheWayItIsPainted);
        Checks.run("aBurntFencesPostStandsWhereItsSpritePaintsItsFoot", OutdoorTests::aBurntFencesPostStandsWhereItsSpritePaintsItsFoot);
        Checks.run("aCrossingSignalFacesTheWayItsSpriteSaysAndStandsOnItsPost", OutdoorTests::aCrossingSignalFacesTheWayItsSpriteSaysAndStandsOnItsPost);
        Checks.run("aFlagPolesTopStandsOnItsLowerLength", OutdoorTests::aFlagPolesTopStandsOnItsLowerLength);
        Checks.run("aCarParkBarrierIsColouredFromItsBooth", OutdoorTests::aCarParkBarrierIsColouredFromItsBooth);
        Checks.run("aParkingMeterStandsWhereItsSpritePaintsItsPost", OutdoorTests::aParkingMeterStandsWhereItsSpritePaintsItsPost);
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
        Checks.check(Outdoors.of("x_01_0", "Gravestone", "Cracked", Outdoors.SOUTH, 1, 1, 0) == null, "a kind of stone the mod has no model of");
    }

    private static void aConcreteBarrierLiesAlongTheEdgeBehindTheWayItFaces() {
        Outdoors.Piece south = Outdoors.of("street_decoration_01_28", "Block", "Concrete Road", Outdoors.SOUTH, 1, 1, 0);
        Outdoors.Piece east = Outdoors.of("street_decoration_01_29", "Block", "Concrete Road", Outdoors.EAST, 1, 1, 0);
        holds(south, "a concrete barrier that faces south has a model");
        holds(east, "and one that faces east");
        Checks.check(south.a == 0.5f && south.b < 0.25f && !south.fits && onMap(south, 1f, 0f)[1] == 0f, "facing south it runs along x by the north edge, a whole square long");
        Checks.check(east.b == 0.5f && east.a < 0.25f && !east.fits && onMap(east, 1f, 0f)[0] == 0f, "facing east along y by the west edge");
        Models.Model barrier = Models.get(south.model);
        Checks.check(Math.abs(barrier.width() - 1f) < 0.005f, "a run of them meets end to end: " + barrier.width());
        Checks.check(Math.abs(barrier.depth() - 2f * south.b) < 0.02f, "its foot reaches from the edge of its square as far as its sprite paints it: " + barrier.depth());
        Checks.check(Outdoors.of("street_decoration_01_28", "Block", "Concrete Road", Outdoors.NORTH, 1, 1, 0) == null, "the game has none that faces north");
    }

    private static void whatStandsAlongAStreetHasAModelAsBigAsItsSprite() {
        // How high each is, read off its sprite: the top of its paint over where it stands, a level being 2.45 tiles.
        Object[][] things = {{"location_community_cemetary_01_8", "Gravestone", "Battered", Outdoors.SOUTH, 0.7f},
                {"construction_01_8", "Block", "Road", Outdoors.SOUTH, 0.95f}, {"lighting_outdoor_01_48", "Lights", "Flood", Outdoors.EAST, 2.5f},
                {"construction_01_6", "Grinder", "Mortar", Outdoors.SOUTH, 1.18f}};
        for (Object[] thing : things) {
            Outdoors.Piece piece = Outdoors.of((String) thing[0], (String) thing[1], (String) thing[2], (int) thing[3], 1, 1, 0);
            holds(piece, thing[0] + " has a model");
            if (piece == null) {
                continue;
            }
            Models.Model model = Models.get(piece.model);
            Checks.check(Math.abs(model.height() - (float) thing[4]) < 0.08f, thing[0] + " is as high as its sprite: " + model.height());
            Checks.check(piece.turns == (int) thing[3] && piece.dyed != 0, thing[0] + " faces the way its sprite does and takes its colour from it");
        }
        Outdoors.Piece stone = Outdoors.of("location_community_cemetary_01_9", "Gravestone", "Battered", Outdoors.EAST, 1, 1, 0);
        Checks.check(stone.a > 0.55f && stone.a < 0.7f && stone.b == 0.5f, "an old stone that faces east stands a little east of the middle, its plinth being deep");
        Checks.check(Outdoors.of("lighting_outdoor_01_48", "Lights", "Flood", Outdoors.NONE, 1, 1, 0) == null, "a work light the game gives no facing is none");
    }

    private static void aPetrolPumpIsStoodByOneOfItsTwoSpritesWhileTheOtherIsThere() {
        for (String sheet : new String[] {"location_shop_gas2go_01_", "location_shop_fossoil_01_"}) {
            Outdoors.Piece east = unnamed(sheet + "12"), north = unnamed(sheet + "13"), south = unnamed(sheet + "14"), west = unnamed(sheet + "15");
            holds(east, sheet + "12 has a model");
            holds(south, sheet + "14 has a model");
            Checks.check(north != null && north.follows && north.model == null && west != null && west.follows && west.model == null,
                    sheet + "13 and 15 stand nothing of their own");
            Checks.check(east != null && (sheet + "13").equals(east.partner) && east.partnerX == 0 && east.partnerY == -1 && north != null
                    && (sheet + "12").equals(north.partner) && north.partnerX == 0 && north.partnerY == 1, "facing east the other sprite is on the square to the north, each naming the other");
            Checks.check(south != null && (sheet + "15").equals(south.partner) && south.partnerX == -1 && south.partnerY == 0 && west != null
                    && (sheet + "14").equals(west.partner) && west.partnerX == 1, "facing south it is on the square to the west");
            Checks.check(east != null && east.turns == Outdoors.EAST && east.b < 0.5f && south != null && south.turns == Outdoors.SOUTH && south.a < 0.5f,
                    "the pump leans to the edge between its two squares");
            Checks.check(east != null && east.dyed == 1 << Models.PAINT && east.alsoDyed == 1 << Models.VINYL && east.alsoHigh > east.dyeHigh,
                    "its low panel and its head each take their colour from the sprite");
        }
        Models.Model pump = Models.get("fuel_pump");
        Checks.check(pump.width() > pump.depth() && pump.height() > 2.2f && pump.height() < 2.4f, "the pump is long across its front and as high as its sprite: " + pump.height());
        Checks.check(unnamed("location_shop_gas2go_01_16") == null && unnamed("location_shop_fossoil_01_11") == null, "the rest of the stations' sheets are no pump");
    }

    private static void aHeapOfSandIsOneModelOverItsFourSquares() {
        Outdoors.Piece large = Outdoors.of("construction_01_67", "Sand Pile", "Large", Outdoors.NORTH, 2, 2, 0);
        Outdoors.Piece small = Outdoors.of("construction_01_75", "Sand Pile", "Small", Outdoors.NORTH, 2, 2, 0);
        holds(large, "a large heap of sand has a model");
        holds(small, "and a small one");
        Checks.check(large != null && small != null && !large.each && Models.get(large.model).height() > Models.get(small.model).height(), "the large one is the higher");
        Checks.check(Models.get("sand_pile").width() > 1.8f && Models.get("sand_pile").width() < 2.25f, "a heap spreads over its four squares");
        Checks.check(Outdoors.of("construction_01_75", "Sand Pile", "Small", Outdoors.NORTH, 1, 1, 0) == null, "a square of one alone is none");
    }

    private static void aWarningSignsPlateShowsItsSpriteFacingTheWayItIsPainted() {
        int[][] signs = {{4, Outdoors.NORTH}, {5, Outdoors.WEST}, {8, Outdoors.SOUTH}, {12, Outdoors.SOUTH}, {16, Outdoors.EAST}, {20, Outdoors.EAST}};
        for (int[] sign : signs) {
            Outdoors.Piece piece = unnamed("street_roadsigns_01_" + sign[0]);
            holds(piece, "street_roadsigns_01_" + sign[0] + " has a model");
            if (piece == null) {
                continue;
            }
            Checks.check(piece.turns == sign[1] && piece.pictured && piece.dyed == 1 << Models.PANE && piece.paneY == Outdoors.WARNING_PLATE_Y,
                    "street_roadsigns_01_" + sign[0] + " faces " + sign[1] + " and its plate shows its sprite");
        }
        // The plate's face, a hand in front of the post, is where the sprite paints the plate: read off the sprite, across 26 to 94 of
        // its 128 columns and with its middle 81 rows from the top, for one that faces south.
        Outdoors.Piece south = unnamed("street_roadsigns_01_8");
        Models.Model sign = Models.get(south.model);
        float plateB = south.b - Outdoors.WARNING_PLATE_Y;
        float left = (south.a - sign.width() / 2f - plateB) * 64f + 64f, right = (south.a + sign.width() / 2f - plateB) * 64f + 64f;
        Checks.check(Math.abs(left - 26f) < 5f && Math.abs(right - 94f) < 5f, "its plate is as wide as the sprite paints it: " + left + " to " + right);
        Checks.check(unnamed("street_roadsigns_01_0") == null && unnamed("street_roadsigns_01_32") == null, "the boards on two posts and the rest area's signs are not warning signs");
    }

    private static void aParkingMeterStandsWhereItsSpritePaintsItsPost() {
        for (int faces = Outdoors.NORTH; faces <= Outdoors.WEST; faces++) {
            Outdoors.Piece meter = Outdoors.of("street_decoration_01_4", "Meter", "Parking", faces, 1, 1, 0);
            holds(meter, "a parking meter facing " + faces + " has a model");
            if (meter == null) {
                continue;
            }
            Checks.check(meter.turns == faces && meter.a == Outdoors.PARKING_METER_AT[2 * faces] && meter.b == Outdoors.PARKING_METER_AT[2 * faces + 1],
                    "a parking meter facing " + faces + " is turned that way and stands on its post");
            // The post stands back from the way the meter faces: its face looks over the middle of the square.
            float awayA = faces == Outdoors.EAST ? -1f : faces == Outdoors.WEST ? 1f : 0f, awayB = faces == Outdoors.SOUTH ? -1f : faces == Outdoors.NORTH ? 1f : 0f;
            Checks.check((meter.a - 0.5f) * awayA + (meter.b - 0.5f) * awayB > 0.15f, "a parking meter facing " + faces + " stands back from its face");
        }
    }

        private static void aCarParkBarrierIsColouredFromItsBooth() {
        // The booth's square of each facing, as the sheet lays the four: north, east, south, west.
        int[][] booths = {{0, 3}, {0, 0}, {0, 0}, {3, 0}};
        for (int faces = Outdoors.NORTH; faces <= Outdoors.WEST; faces++) {
            boolean alongY = faces == Outdoors.NORTH || faces == Outdoors.SOUTH;
            Outdoors.Piece barrier = Outdoors.of("street_decoration_01_56", "Post", "Barrier", faces, alongY ? 1 : 4, alongY ? 4 : 1, 0);
            holds(barrier, "a car park barrier facing " + faces + " has a model");
            if (barrier == null) {
                continue;
            }
            Checks.check(barrier.model.equals("parking_barrier") && barrier.turns == faces, "a barrier is turned the way it faces");
            Checks.check(barrier.dyeX == booths[faces][0] && barrier.dyeY == booths[faces][1], "a barrier facing " + faces + " is coloured from its booth's square");
        }
        Checks.check(Outdoors.of("street_decoration_01_56", "Post", "Barrier", Outdoors.NORTH, 1, 3, 0) == null, "a barrier the game lays shorter is left to it");
    }

        private static void aFlagPolesTopStandsOnItsLowerLength() {
        Outdoors.Piece pole = Outdoors.of("street_decoration_01_24", "Pole", "American Flag", Outdoors.NONE, 1, 1, 0);
        Outdoors.Piece top = Outdoors.of("street_decoration_01_16", "Pole", "American Flag", Outdoors.NONE, 1, 1, 0);
        holds(pole, "a flag pole's lower length has a model");
        holds(top, "a flag pole's top has a model");
        if (pole == null || top == null) {
            return;
        }
        float poleTop = pole.raised + Models.get(pole.model).height(), topFoot = ViewCamera.LEVEL_HEIGHT + top.raised;
        Checks.check(topFoot < poleTop && poleTop - topFoot < 0.3f, "the top length reaches down a little into the lower one: " + topFoot + " under " + poleTop);
        Checks.check(top.a == pole.a && top.b == pole.b, "the top stands over the lower length");
    }

        private static void aCrossingSignalFacesTheWayItsSpriteSaysAndStandsOnItsPost() {
        for (int faces = Outdoors.NORTH; faces <= Outdoors.WEST; faces++) {
            Outdoors.Piece signal = Outdoors.of("industry_railroad_05_" + (48 + (faces + 3) % 4), "Signal", "Train", faces, 1, 1, 0);
            holds(signal, "a crossing signal facing " + faces + " has a model");
            if (signal == null) {
                continue;
            }
            boolean eastOrWest = faces == Outdoors.EAST || faces == Outdoors.WEST;
            Checks.check(signal.model.equals("crossing_signal") && signal.turns == faces, "a crossing signal is turned the way it faces");
            Checks.check(signal.a == (eastOrWest ? Outdoors.CROSSING_SIGNAL_EW_A : Outdoors.CROSSING_SIGNAL_NS)
                    && signal.b == (eastOrWest ? Outdoors.CROSSING_SIGNAL_EW_B : Outdoors.CROSSING_SIGNAL_NS), "its post is where its sprite paints it");
            Checks.check(signal.dyeA == signal.a && signal.dyeB == signal.b, "it is coloured from its own post");
        }
        Checks.check(Outdoors.of("industry_railroad_05_48", "Signal", "Train", Outdoors.NONE, 1, 1, 0) == null, "a signal facing no way is left to the game");
    }

        private static void aBurntFencesPostStandsWhereItsSpritePaintsItsFoot() {
        Object[][] posts = {{3, "burnt_picket_post", 0.96f}, {13, "burnt_tall_post", 2.33f}, {45, "burnt_rail_post", 1.35f}, {53, "fence_pole", 2.17f},
                {61, "fence_pole_wide", 2.19f}, {69, "fence_pole_capped", 1.28f}};
        for (Object[] post : posts) {
            String name = "fencing_burnt_01_" + post[0];
            Outdoors.Piece piece = Outdoors.of(name, null, null, Outdoors.NONE, 1, 1, 0);
            Outdoors.Piece named = Outdoors.of(name, "Fence Post", null, Outdoors.NONE, 1, 1, 0);
            holds(piece, name + " has a model");
            if (piece == null) {
                continue;
            }
            Checks.check(piece.model.equals(post[1]) && named != null && named.model.equals(post[1]), name + " is " + post[1] + " whether or not the game names it");
            Checks.check(piece.a < 0.15f && piece.b < 0.15f && piece.dyeA == piece.a && piece.dyeB == piece.b, name + " stands by its square's north-west corner and is coloured from itself there");
            Checks.check(Math.abs(Models.get(piece.model).height() - (float) post[2]) < 0.06f, name + " is as high as its sprite: " + Models.get(piece.model).height());
        }
        Checks.check(Outdoors.of("fencing_burnt_01_4", null, null, Outdoors.NONE, 1, 1, 0) == null, "another sprite of the sheet is none of them");
    }

    private static void aStackOfTyresIsAsManyTyresAsItsSpritePaintsAndStaysOnItsSquare() {
        // The sprites of the sheet and how many tyres each paints, from the game's GroupName of them ("2 Stacked", "Stacked") and its pictures.
        int[][] counts = {{40, 2}, {41, 2}, {42, 3}, {43, 3}, {44, 4}, {45, 4}, {48, 1}, {49, 1}, {50, 2}, {51, 3}, {52, 4}};
        float one = Models.get("tyre").height();
        Checks.check(Math.abs(one - 0.2f) < 0.005f && Math.abs(Models.get("tyre").width() - 0.64f) < 0.01f, "a tyre is 0.64 across and 0.2 high: " + one);
        for (int[] sprite : counts) {
            String name = "location_business_machinery_01_" + sprite[0];
            Outdoors.Piece piece = Outdoors.of(name, sprite[1] == 1 ? "Tire" : "Tires", null, Outdoors.NORTH, 1, 1, 0);
            holds(piece, name + " has a model");
            if (piece == null) {
                continue;
            }
            Models.Model model = Models.get(piece.model);
            Checks.check(Math.abs(model.height() - sprite[1] * one) < 0.005f, name + " is " + sprite[1] + " tyres high: " + model.height());
            // The model's origin is the middle of its lowest tyre, and its x is the map's -x.
            Checks.check(piece.turns == 0 && piece.a - model.maxX >= 0f && piece.a - model.minX <= 1f && piece.b + model.minY >= 0f && piece.b + model.maxY <= 1f,
                    name + " lies inside its square");
        }
        Checks.check(Outdoors.of("location_business_machinery_01_46", "Tires", null, Outdoors.NONE, 1, 1, 0) == null
                && Outdoors.of("location_business_machinery_01_32", "Tool Cabinet", "Red Mobile", Outdoors.SOUTH, 1, 1, 34) == null,
                "nothing else on the sheet of tyres");
        // Thrown on less squarely: the upper tyre of a pair painted to the left of the lower lies off it towards the map's -x and +y.
        Models.Model pair = Models.get(Outdoors.of("location_business_machinery_01_40", "Tires", "2 Stacked", Outdoors.NORTH, 1, 1, 0).model);
        Checks.check(pair.maxX > 0.36f && pair.maxY > 0.36f && pair.minX > -0.33f, "the pair facing north has its upper tyre off the lower one: " + pair.maxX + ", " + pair.maxY);
    }

    private static Outdoors.Piece rainBarrel(int place) {
        return Outdoors.of("carpentry_02_" + place, "Rain Collector Barrel", null, Outdoors.NONE, 1, 1, 0);
    }

    private static void aRainBarrelIsABarrelWhateverIsOverItsMouth() {
        String[] models = {"rain_barrel", "rain_barrel_water", "rain_barrel_tarp", "rain_barrel_tarp", "rain_barrel_tarp", "rain_barrel_tarp", "rain_barrel_lid", "rain_barrel_lid"};
        int[] places = {52, 53, 122, 123, 124, 125, 126, 127};
        for (int i = 0; i < places.length; i++) {
            Outdoors.Piece piece = rainBarrel(places[i]);
            holds(piece, "carpentry_02_" + places[i] + " has a model");
            Checks.check(piece != null && piece.model.equals(models[i]) && piece.fits, "carpentry_02_" + places[i] + " is " + models[i] + " inside its square");
            Models.Model model = Models.get(models[i]);
            Checks.check(model.width() < 0.96f && model.depth() < 0.96f && model.height() > 0.8f && model.height() < 0.95f,
                    models[i] + " is a barrel's size: " + model.width() + " by " + model.height());
        }
        Checks.check(rainBarrel(53).dyed == 1 << Models.ENAMEL && rainBarrel(52).dyed == 0, "only the rain in a barrel takes its colour from the sprite");
        Checks.check(rainBarrel(122).dyed == 1 << Models.CLOTH && rainBarrel(127).dyed == 1 << Models.CLOTH, "and the sheet over one");
        Checks.check(Outdoors.of("carpentry_02_52", "Rain Collector Barrel", null, Outdoors.NONE, 2, 1, 0) == null, "a barrel of two squares is none");
    }

    private static void aPottedPlantIsItsKindInItsState() {
        String[][] kinds = {{"evergreen", "Chinese Evergreen", "7", "21", "29", "37"}, {"dragon_tree", "Dragon Tree", "9", "17", "25", "33"},
                {"snake_plant", "Snake Plant", "10", "18", "26", "34"}, {"cast_iron", "Cast Iron Plant", "11", "19", "27", "35"},
                {"ficus", "Ficus", "12", "20", "28", "36"}};
        for (String[] kind : kinds) {
            String[] models = {kind[0], kind[0] + "_dry", kind[0] + "_dry", "pot_" + kind[0]};
            for (int state = 0; state < 4; state++) {
                String name = "vegetation_indoor_01_" + kind[2 + state];
                Potted plant = Potted.of(name, kind[1]);
                Checks.check(plant != null && plant.model.equals(models[state]), name + " is " + models[state]);
                if (plant == null) {
                    continue;
                }
                Models.Model model = Models.get(plant.model);
                Checks.check((plant.leaves > 0f) == (state < 3), name + " takes the colour of leaves only where it has them");
                // A plant stood in a corner reaches no leaf through either wall of it.
                Checks.check(model.width() <= 0.9f + 1e-4f && model.depth() <= 0.9f + 1e-4f, name + " stays inside its square: " + model.width() + " by " + model.depth());
            }
        }
        Checks.check(Potted.of("vegetation_indoor_01_12", "Cast Iron Plant") == null, "a sprite whose name says another plant is none");
        Checks.check(Potted.of("vegetation_indoor_01_8", "Raised Plantbed") == null && Potted.of("vegetation_indoor_01_0", "Venus Fly Trap") == null,
                "a raised bed and what stands on a table are not plants in pots on the floor");
        Checks.check(Potted.of("vegetation_indoor_01_", "Ficus") == null && Potted.of("vegetation_indoor_01_1x", "Ficus") == null
                && Potted.of("vegetation_indoor_02_12", "Ficus") == null, "a name that is not a place on the sheet");
        Checks.check(Models.get("dragon_tree").height() > 2f && Models.get("ficus").height() > 1.3f && Models.get("pot_ficus").height() < 0.5f,
                "a dragon tree is the tallest, a ficus as high as a shoulder, and an empty pot only a pot");
    }

    private static void aFencePostStandsWhereItsSpritePaintsItsFoot() {
        // Each post's place and height were read off its sprite: the middle of its foot, and from there to the middle of its top.
        Object[][] posts = {{"fencing_01_7", null, "picket_post", 0.031f, 0.039f, 0.925f}, {"fencing_01_19", "Fence Post", "rail_post", 0.133f, 0.102f, 0.995f},
                {"fencing_01_29", "Fence Post", "pipe_post", 0.062f, 0.094f, 1.26f}, {"fencing_01_61", null, "pipe_post_tall", 0.062f, 0.094f, 2.16f},
                {"fencing_01_53", null, "block_post", 0.168f, 0.168f, 2.52f}};
        for (Object[] post : posts) {
            String name = (String) post[0];
            Outdoors.Piece piece = Outdoors.of(name, (String) post[1], null, Outdoors.NONE, 1, 1, 0);
            holds(piece, name + " has a model");
            if (piece == null) {
                continue;
            }
            Checks.check(piece.model.equals(post[2]), name + " is " + post[2] + ", not " + piece.model);
            Checks.check(piece.a == (float) post[3] && piece.b == (float) post[4] && piece.dyeA == piece.a && piece.dyeB == piece.b && piece.fits,
                    name + " stands where its sprite paints its foot and takes its colour from itself there");
            float high = Models.get(piece.model).height();
            Checks.check(Math.abs(high - (float) post[5]) < 0.01f, name + " is as high as its sprite: " + high);
        }
        Checks.check(Outdoors.of("fencing_01_37", "Fence Post", null, Outdoors.NONE, 1, 1, 0) == null
                && Outdoors.of("fencing_01_123", "Fence Post", null, Outdoors.NONE, 1, 1, 0) == null, "a post Posts reads whole off its sprite is left to it");
        Checks.check(unnamed("fencing_01_4") == null && unnamed("fencing_01_8") == null, "a length of fence is no post");
    }

    private static void aPublicLitterBinIsTheSlattedBin() {
        Outdoors.Piece bin = Outdoors.of("trashcontainers_01_21", "Public Garbage Bin", null, Outdoors.NONE, 1, 1, 0);
        holds(bin, "a public litter bin has a model");
        Checks.check(bin != null && bin.model.equals("slatted_bin") && bin.fits && bin.dyed == 1 << Models.PAINT, "it is the slatted bin, fitted to its square and coloured from its sprite");
        Models.Model model = Models.get("slatted_bin");
        Checks.check(model.width() < 0.8f && model.height() > 0.85f && model.height() < 0.95f, "it is about as wide and as high as the game paints it: " + model.width() + ", " + model.height());
        Checks.check(Outdoors.of("trashcontainers_01_21", "Public Garbage Bin", null, Outdoors.NONE, 2, 1, 0) == null, "one laid on two squares is none");
    }
}

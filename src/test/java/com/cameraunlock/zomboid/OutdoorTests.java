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
        for (int crate : new int[] {54, 55, 120, 121}) {
            Checks.check(rainBarrel(crate) == null, "carpentry_02_" + crate + ", a crate lined with a sheet, stays a box");
        }
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

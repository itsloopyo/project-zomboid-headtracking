package com.cameraunlock.zomboid;

/** Tests of which model stands for a farmyard's or a garden's sprite, which way it lies and how high it stands. */
final class FarmTests {
    private FarmTests() {}

    static void run() {
        Checks.run("aBaleOfHayLiesTheWayItsTwineRuns", FarmTests::aBaleOfHayLiesTheWayItsTwineRuns);
        Checks.run("aBalePaintedOnOthersStandsOnThem", FarmTests::aBalePaintedOnOthersStandsOnThem);
        Checks.run("aLayerOfLogsLiesOnTheLayerUnderIt", FarmTests::aLayerOfLogsLiesOnTheLayerUnderIt);
        Checks.run("aLoadOfLogsFillsItsSixSquares", FarmTests::aLoadOfLogsFillsItsSixSquares);
        Checks.run("aTroughLiesAcrossTheWayItFaces", FarmTests::aTroughLiesAcrossTheWayItFaces);
        Checks.run("whatStandsInAYardHasItsModel", FarmTests::whatStandsInAYardHasItsModel);
        Checks.run("aMallPlanterIsStoneWithLeavesGrowingOverItsRim", FarmTests::aMallPlanterIsStoneWithLeavesGrowingOverItsRim);
        Checks.run("aPlantOnATableStandsOnItWhereItsSpritePaintsIt", FarmTests::aPlantOnATableStandsOnItWhereItsSpritePaintsIt);
        Checks.run("whatIsNoneOfTheseIsLeftAlone", FarmTests::whatIsNoneOfTheseIsLeftAlone);
    }

    private static Outdoors.Piece hay(int place, String called) {
        return Outdoors.of("vegetation_farm_01_" + place, called, null, Outdoors.NONE, 1, 1, 0);
    }

    private static Models.Model model(Outdoors.Piece piece, String what) {
        Checks.check(piece != null, what + " has a model");
        return piece == null ? null : Models.get(piece.model);
    }

    /** Which way a piece's model's y runs on the map: 0 along y, 1 along x. */
    private static int lengthAlong(Outdoors.Piece piece) {
        float[] out = new float[2];
        Models.turn(piece.turns, 0f, 1f, out);
        return out[0] == 0f ? 0 : 1;
    }

    private static void aBaleOfHayLiesTheWayItsTwineRuns() {
        int[][] bales = {{8, 0}, {9, 0}, {16, 1}, {17, 1}};
        for (int[] bale : bales) {
            Outdoors.Piece piece = hay(bale[0], "Single Stacked Hay");
            Models.Model one = model(piece, "vegetation_farm_01_" + bale[0]);
            Checks.check(piece != null && piece.model.equals("hay_bale") && lengthAlong(piece) == bale[1] && piece.dyed == 1 << Models.CLOTH,
                    "vegetation_farm_01_" + bale[0] + " is one bale of hay lying along the map's " + (bale[1] == 0 ? "y" : "x"));
            Checks.check(one != null && one.depth() > 0.9f && one.depth() < 0.96f && one.width() < 0.52f
                    && Math.abs(one.height() - Farm.BALE * ViewCamera.LEVEL_HEIGHT) < 0.01f, "a bale is a small square bale: " + (one == null ? 0f : one.height()));
        }
        int[][] stacks = {{10, 0}, {11, 0}, {18, 1}, {19, 1}};
        for (int[] stack : stacks) {
            Outdoors.Piece piece = hay(stack[0], "Double Stacked Hay");
            Models.Model two = model(piece, "vegetation_farm_01_" + stack[0]);
            Checks.check(piece != null && piece.model.equals("hay_bales_2") && lengthAlong(piece) == stack[1],
                    "vegetation_farm_01_" + stack[0] + " is two bales lying along the map's " + (stack[1] == 0 ? "y" : "x"));
            Checks.check(two != null && Math.abs(two.height() - 2f * Farm.BALE * ViewCamera.LEVEL_HEIGHT) < 0.02f, "and two bales are twice as high");
        }
    }

    private static void aBalePaintedOnOthersStandsOnThem() {
        float bale = Farm.BALE * ViewCamera.LEVEL_HEIGHT;
        Checks.check(hay(8, "Single Stacked Hay").raised == 0f && hay(10, "Double Stacked Hay").raised == 0f, "a bale on the ground stands on it");
        Checks.check(Math.abs(hay(9, "Single Stacked Hay").raised - 2f * bale) < 1e-4f, "one painted on two bales stands on two");
        Checks.check(Math.abs(hay(17, "Single Stacked Hay").raised - bale) < 1e-4f, "one painted on one bale on one");
        Checks.check(Math.abs(hay(11, "Double Stacked Hay").raised - 2f * bale) < 1e-4f && Math.abs(hay(19, "Double Stacked Hay").raised - 2f * bale) < 1e-4f,
                "two painted on two stand on two");
        Checks.check(Math.abs(hay(9, "Single Stacked Hay").dyeHigh - hay(8, "Single Stacked Hay").dyeHigh - 2f * Farm.BALE) < 1e-5f,
                "and the colour is taken as much higher on the sprite");
    }

    private static void aLayerOfLogsLiesOnTheLayerUnderIt() {
        Checks.check(Outdoors.of("camping_01_24", "Stack", "6 Log", Outdoors.EAST, 2, 1, 0).raised == 0f, "a stack on the ground stands on it");
        Outdoors.Piece onOne = Outdoors.of("camping_01_26", "Stack", "6 Log", Outdoors.EAST, 2, 1, 17);
        Outdoors.Piece onTwo = Outdoors.of("camping_01_40", "Stack", "5 Log", Outdoors.EAST, 2, 1, 33);
        model(onOne, "a layer of six logs on another");
        model(onTwo, "a layer of five logs on two");
        float layer = Models.get("log_stack_6").height();
        Checks.check(Math.abs(onOne.raised - layer) < 0.03f && Math.abs(onTwo.raised - 2f * layer) < 0.05f,
                "a layer is raised by as many layers as the game paints under it: " + onOne.raised + ", " + onTwo.raised + " for a layer of " + layer);
        Checks.check(Math.abs(Models.get("log_stack_5").height() - layer) < 0.02f, "five logs make a layer as high as six");
    }

    private static void aLoadOfLogsFillsItsSixSquares() {
        Outdoors.Piece east = Outdoors.of("camping_01_51", "Stack", "Large Log", Outdoors.EAST, 3, 2, 0);
        Outdoors.Piece south = Outdoors.of("camping_01_58", "Stack", "Large Log", Outdoors.SOUTH, 2, 3, 0);
        Models.Model bundle = model(east, "a load of long logs");
        model(south, "and one lying the other way");
        Checks.check(lengthAlong(east) == 1 && lengthAlong(south) == 0, "the logs lie along the three squares");
        Checks.check(bundle != null && bundle.depth() > 2.7f && bundle.depth() < 2.96f && bundle.width() < 1.96f, "and fit inside them");
        Checks.check(Outdoors.of("camping_01_51", "Stack", "Large Log", Outdoors.EAST, 2, 3, 0) == null, "a load laid across the way it faces is none");
    }

    private static void aTroughLiesAcrossTheWayItFaces() {
        String sheet = "location_farm_accesories_01_";
        Object[][] troughs = {{"Animal", Outdoors.EAST, 1, 2, "feed_trough"}, {"Animal", Outdoors.SOUTH, 2, 1, "feed_trough"},
                {"Small Animal", Outdoors.EAST, 1, 1, "feed_trough_small"}, {"Small Metal", Outdoors.NORTH, 2, 1, "stock_tank"},
                {"Small Metal", Outdoors.WEST, 1, 2, "stock_tank"}, {"Large Metal", Outdoors.NORTH, 3, 1, "stock_tank_long"},
                {"Large Metal", Outdoors.WEST, 1, 3, "stock_tank_long"}};
        for (Object[] trough : troughs) {
            int faces = (Integer) trough[1], width = (Integer) trough[2], height = (Integer) trough[3];
            Outdoors.Piece piece = Outdoors.of(sheet + "4", "Trough", (String) trough[0], faces, width, height, 0);
            Models.Model shape = model(piece, trough[0] + " trough facing " + faces);
            if (piece == null) {
                continue;
            }
            // The model is long along its x: on the map that runs the way the piece's squares do.
            float[] out = new float[2];
            Models.turn(piece.turns, 1f, 0f, out);
            Checks.check(piece.model.equals(trough[4]) && (Math.abs(out[0]) == 1f) == (width > height || width == height && (faces == Outdoors.NORTH || faces == Outdoors.SOUTH)),
                    trough[0] + " trough facing " + faces + " lies along its squares");
            Checks.check(shape.width() < Math.max(width, height) - 0.04f + 1e-4f && shape.depth() < 0.96f, "and inside them");
        }
        Outdoors.Piece feeder = Outdoors.of(sheet + "18", "Trough", "Huge Metal", Outdoors.SOUTH, 2, 2, 0);
        Models.Model rack = model(feeder, "a feeder with a hay rack");
        Checks.check(feeder != null && feeder.model.equals("hay_feeder") && feeder.dyed == 1 << Models.PAINT && rack.width() < 1.96f && rack.depth() < 1.96f
                && rack.height() > 1.6f, "a feeder of four squares is the feeder, inside them and taller than a man's chest");
        Checks.check(Outdoors.of(sheet + "21", "Trough", "Huge Metal", Outdoors.EAST, 2, 2, 0).turns == Outdoors.EAST
                && Outdoors.of(sheet + "21", "Trough", "Huge Metal", Outdoors.NORTH, 2, 2, 0) == null, "one facing east too, and a way the game has no sprite of is none");
        Checks.check(Outdoors.of(sheet + "4", "Trough", "Animal", Outdoors.EAST, 2, 1, 0) == null, "a trough laid along the way it faces is none");
    }

    private static void whatStandsInAYardHasItsModel() {
        Object[][] things = {{"location_farm_accesories_01_8", "House", "Dog", Outdoors.EAST, "dog_house"},
                {"carpentry_02_116", "Composter", null, Outdoors.NONE, "composter"}, {"carpentry_02_117", "Composter", null, Outdoors.NONE, "composter_full"},
                {"carpentry_02_54", "Rain Collector Barrel", null, Outdoors.NONE, "rain_crate"}, {"carpentry_02_120", "Rain Collector Barrel", null, Outdoors.NONE, "rain_crate"},
                {"carpentry_02_55", "Rain Collector Barrel", null, Outdoors.NONE, "rain_crate_water"},
                {"carpentry_02_121", "Rain Collector Barrel", null, Outdoors.NONE, "rain_crate_water"},
                {"vegetation_indoor_01_8", "Raised Plantbed", null, Outdoors.NONE, "raised_bed"}, {"vegetation_indoor_01_16", "Raised Plantbed", null, Outdoors.NONE, "raised_bed_dry"},
                {"vegetation_indoor_01_24", "Raised Plantbed", null, Outdoors.NONE, "raised_bed_dry"},
                {"vegetation_indoor_01_32", "Raised Plantbed", null, Outdoors.NONE, "raised_bed_bare"},
                {"appliances_laundry_01_24", "Bin", "Washing", Outdoors.NORTH, "washing_bin"}, {"vegetation_ornamental_01_50", "Bird Bath", null, Outdoors.NONE, "bird_bath"},
                {"vegetation_ornamental_01_51", "Bird Bath", null, Outdoors.NONE, "bird_bath_round"},
                {"location_farm_accesories_01_12", "Salt Lick", null, Outdoors.NONE, "salt_lick"}, {"crafted_02_86", "Stump", null, Outdoors.NONE, "stump"},
                {"crafted_02_85", "Block", "Chopping", Outdoors.NONE, "chopping_block"}, {"vegetation_ornamental_01_48", "The Gnome", "David", Outdoors.SOUTH, "gnome"}};
        for (Object[] thing : things) {
            Outdoors.Piece piece = Outdoors.of((String) thing[0], (String) thing[1], (String) thing[2], (Integer) thing[3], 1, 1, 0);
            Models.Model shape = model(piece, (String) thing[0]);
            Checks.check(piece != null && piece.model.equals(thing[4]) && piece.fits, thing[0] + " is " + thing[4] + " inside its square");
            Checks.check(shape != null && shape.width() < 0.96f && shape.depth() < 0.96f, thing[4] + " fits its square: " + (shape == null ? 0f : shape.width()));
        }
        Outdoors.Piece post = Outdoors.of("location_farm_accesories_01_13", null, null, Outdoors.NONE, 1, 1, 0);
        Models.Model straw = model(post, "the post bound with straw");
        Checks.check(straw != null && straw.height() > 1.5f && post.dyed == 1 << Models.CLOTH, "is a post as high as a shoulder, its straw coloured from the sprite");
        Checks.check(Outdoors.of("location_farm_accesories_01_14", null, null, Outdoors.NONE, 1, 1, 0) == null, "and the sheet's other unnamed places are none of it");
        Outdoors.Piece fallen = Outdoors.of("d_generic_1_31", "Log", null, Outdoors.NONE, 1, 1, 0);
        Models.Model trunk = model(fallen, "a fallen trunk");
        Checks.check(trunk != null && trunk.height() < 0.4f && Math.max(trunk.width(), trunk.depth()) > 0.7f && fallen.turns == 0,
                "lies on the ground, aslant as its model is");
        Checks.check(Outdoors.of("d_generic_1_12", "TreeBranch2", null, Outdoors.NONE, 1, 1, 0) == null, "branches and stones lying about are left as they are");
        Checks.check(Outdoors.of("crafted_02_56", "Bench", "Log", Outdoors.EAST, 1, 2, 16).model.equals("log_bench"), "a log bench of two squares");
        Checks.check(Math.abs(Models.get("log_bench").height() - 16f / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT) < 0.03f,
                "is as high as the game's seat");
        Checks.check(Outdoors.of("carpentry_02_55", "Rain Collector Barrel", null, Outdoors.NONE, 1, 1, 0).alsoDyed == 1 << Models.ENAMEL
                && Outdoors.of("carpentry_02_54", "Rain Collector Barrel", null, Outdoors.NONE, 1, 1, 0).alsoDyed == 0, "only a crate with rain in it takes the rain's colour");
        Outdoors.Piece kennel = Outdoors.of("location_farm_accesories_01_10", "House", "Dog", Outdoors.WEST, 1, 1, 0);
        Checks.check(kennel.turns == Outdoors.WEST, "a kennel's door is on the side it faces");
    }

    private static void aMallPlanterIsStoneWithLeavesGrowingOverItsRim() {
        Outdoors.Piece planter = Outdoors.of("location_shop_mall_01_47", "Mall Decoration", null, Outdoors.NONE, 1, 1, 0);
        Models.Model shape = model(planter, "the mall's planter");
        if (planter == null) {
            return;
        }
        Checks.check(planter.model.equals("mall_planter") && planter.fits && shape.width() < 0.96f && shape.depth() < 0.96f,
                "is the stone planter, inside its square: " + planter.model + " " + shape.width());
        // The blocks are 0.74 m high; only leaves reach higher.
        Checks.check(shape.height() > 0.9f && shape.height() < 1.15f, "with leaves standing a hand or two over its rim, as the sprite paints them: " + shape.height());
        Checks.check(planter.dyed == 1 << Models.PAINT && planter.alsoDyed == 1 << Models.PLASTIC && planter.alsoHigh > planter.dyeHigh,
                "its stone and its leaves each coloured from the sprite, the leaves from higher up it");
        Checks.check(Outdoors.of("location_shop_mall_01_44", "Empty Mall Decoration", null, Outdoors.NONE, 1, 1, 0) == null
                && Outdoors.of("location_shop_mall_01_46", "Mall Decoration", null, Outdoors.NONE, 1, 1, 0) == null
                && Outdoors.of("location_shop_mall_01_47", "Mall Decoration", null, Outdoors.NONE, 2, 1, 0) == null,
                "the stone bin beside it, another place on the sheet and a piece of two squares are none of it");
    }

    private static void aPlantOnATableStandsOnItWhereItsSpritePaintsIt() {
        String[] called = {"Venus Fly Trap", "Bonsai Tree", "Voilet Flowers", "Cactus", "Roses", "Roses", "Orange Plant"};
        String[] models = {"pot_flowers", "bonsai", "pot_violets", "cactus_pot", "rose_bouquet", "rose_bouquet", "vase_flowers"};
        int[] faces = {Outdoors.NONE, Outdoors.NONE, Outdoors.NONE, Outdoors.NONE, Outdoors.EAST, Outdoors.SOUTH, Outdoors.NONE};
        float table = 34f / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
        for (int place = 0; place < called.length; place++) {
            Outdoors.Piece plant = Outdoors.of("vegetation_indoor_01_" + place, called[place], place >= 4 && place <= 5 ? "Bouquet of" : null, faces[place], 1, 1, 34);
            Models.Model shape = model(plant, "vegetation_indoor_01_" + place);
            if (plant == null) {
                continue;
            }
            Checks.check(plant.model.equals(models[place]) && Math.abs(plant.raised - table) < 1e-4f, "vegetation_indoor_01_" + place + " is " + models[place] + " on its table");
            float reach = Math.max(shape.width(), shape.depth()) / 2f;
            Checks.check(plant.a - reach > 0f && plant.a + reach < 1f && plant.b - reach > 0f && plant.b + reach < 1f, models[place] + " stays on its square");
            Checks.check(plant.dyeHigh > 34f / WorldScene.SURFACE_PIXELS_PER_LEVEL && (plant.alsoDyed == 0 || plant.alsoHigh > 0.3f),
                    "and takes its colour from up on the table in its sprite");
        }
        Checks.check(Outdoors.of("vegetation_indoor_01_2", "Cactus", null, Outdoors.NONE, 1, 1, 34) == null, "a sprite whose name says another plant is none");
    }

    private static void whatIsNoneOfTheseIsLeftAlone() {
        Checks.check(hay(12, "Hay Floor") == null && hay(8, "Hay") == null, "hay spread on a floor, and a name the bales do not have");
        Checks.check(Outdoors.of("vegetation_farm_01_8", "Single Stacked Hay", null, Outdoors.NONE, 2, 1, 0) == null, "a bale of two squares");
        Checks.check(Outdoors.of("carpentry_02_52", "Rain Collector Barrel", null, Outdoors.NONE, 1, 1, 0) == null
                || !Outdoors.of("carpentry_02_52", "Rain Collector Barrel", null, Outdoors.NONE, 1, 1, 0).model.startsWith("rain_crate"), "a barrel is no crate");
        Checks.check(Outdoors.of("carpentry_02_118", "Composter", null, Outdoors.NONE, 1, 1, 0) == null, "a place on the sheet that is no composter");
        Checks.check(Outdoors.of("vegetation_indoor_01_7", "Chinese Evergreen", null, Outdoors.NONE, 1, 1, 0) == null
                || !Outdoors.of("vegetation_indoor_01_7", "Chinese Evergreen", null, Outdoors.NONE, 1, 1, 0).model.startsWith("pot_f"), "a plant on the floor is not one on a table");
        Checks.check(Outdoors.of("vegetation_indoor_01_4", "Roses", "Bouquet of", Outdoors.NONE, 1, 1, 34) == null, "a bouquet with no way it lies");
    }
}

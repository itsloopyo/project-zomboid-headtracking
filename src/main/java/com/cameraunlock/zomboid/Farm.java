package com.cameraunlock.zomboid;

/**
 * What stands in a farmyard or a garden that the mod has a model of: hay bales, log stacks and
 * loads of long logs, troughs and a hay feeder, a kennel, a post bound with straw, a compost bin,
 * a rain crate, a mall's planter, a raised bed, a laundry cart, a bird bath, a salt lick, a log bench, stumps, a
 * fallen trunk, a gnome, lawn flamingos, and the small plants that stand on a table. Asked by
 * Outdoors before its own rules, so a sprite here is told nowhere else.
 *
 * How high each thing is, which way it lies and where its sprite gives it its colour were read
 * off the game's sprites of it.
 */
final class Farm {
    private Farm() {}

    private static final String HAY = "vegetation_farm_01_", CARPENTRY = "carpentry_02_", POTS = "vegetation_indoor_01_", ORNAMENTS = "vegetation_ornamental_01_",
            CRAFTED = "crafted_02_", FARMYARD = "location_farm_accesories_01_", DEBRIS = "d_generic_1_", MALL = "location_shop_mall_01_";
    private static final int CLOTH = 1 << Models.CLOTH, STRAW = 1 << Models.STRAW, WOOD = 1 << Models.WOOD, DARK_WOOD = 1 << Models.DARK_WOOD, PAINT = 1 << Models.PAINT,
            PLASTIC = 1 << Models.PLASTIC, VINYL = 1 << Models.VINYL, CERAMIC = 1 << Models.CERAMIC, ENAMEL = 1 << Models.ENAMEL,
            STEEL = 1 << Models.STEEL;
    /**
     * Whether a sprite is a square of standing wheat the game gives no property at all, which it
     * paints as it paints the wheat it marks as growth (0 to 2 of the sheet) and lays out as fields.
     */
    static boolean wheat(String name) {
        int place = name == null ? -1 : Sheets.place(name, HAY);
        return place >= 3 && place <= 5;
    }

    /** How high a bale of hay is, in levels: the model's 0.41 m. A bale the game paints on others is raised by as many. */
    static final float BALE = 0.41f / ViewCamera.LEVEL_HEIGHT;
    /** Where on its sprite a bale gives the colour of its hay, in levels: one bale, and two. */
    private static final float HAY_DYE = 0.1f, HAY_DYE_TWO = 0.2f;
    /** How high on its sprite a stack of logs gives its bark's colour, in levels, over the layers under it; and a load of long logs. */
    private static final float LOG_DYE = 0.18f, BUNDLE_DYE = 0.75f;
    /** Where on its sprite each thing gives its colour, in levels over its foot. */
    private static final float TROUGH_DYE = 0.12f, ROOF_DYE = 0.32f, BENCH_DYE = 0.15f, BENCH_BARK_DYE = 0.1f, COMPOST_DYE = 0.12f, CART_DYE = 0.25f, BATH_DYE = 0.15f,
            BATH_WATER = 0.322f, LICK_DYE = 0.05f, COAT_DYE = 0.06f, HAT_DYE = 0.34f, BED_DYE = 0.12f, FRONDS_DYE = 0.32f, FLAMINGO_DYE = 0.27f;
    /**
     * Where a mall's planter gives the colour of its stone, low on the face its sprite paints over the square's middle, and
     * over the same place the colour of its leaves, in levels.
     */
    private static final float PLANTER_DYE = 0.1f, PLANTER_LEAVES_DYE = 0.34f;
    /** Where a rain crate gives the colour of the sheet folded over its near corner, and over the same place the rain in it. */
    private static final float CRATE_CORNER = 0.84f, SHEET_DYE = 0.245f, RAIN_DYE = 0.311f;
    /** Where the middle of the fallen trunk is painted on its square, and how high on it its bark gives its colour, in levels. */
    private static final float LOG_AT_A = 0.69f, LOG_AT_B = 0.7f, FALLEN_DYE = 0.1f;
    /** Where on its square the straw post's foot is painted, and how high its straw gives its colour, in levels. */
    private static final float STRAW_POST_A = 0.58f, STRAW_POST_B = 0.6f, STRAW_DYE = 0.5f;
    /**
     * Where on the first square of a hay feeder its frame gives its colour: on the post its sprite paints there, for one that faces
     * south and one that faces east, at the same height, in levels.
     */
    private static final float[] FEEDER_POST = {0.0156f, 0.484f, 0.445f, 0.055f};
    private static final float FEEDER_DYE = 0.51f;
    /** How far along the side a feed trough shows from the game's view its colour is taken, from its middle across. */
    private static final float TROUGH_SIDE = 0.23f;
    /** How high a stock tank's floor and a wooden trough's are inside them (tools/models/furniture.py), in metres: Troughs stands what they hold there. */
    static final float TANK_FLOOR = 0.025f, TROUGH_FLOOR = 0.23f;

    static Outdoors.Piece of(String name, String called, String group, int faces, int width, int height, int surface) {
        if (called == null) {
            // A post with straw bound round it, which the game names nothing.
            return name.equals(FARMYARD + "13") && width == 1 && height == 1 ? new Outdoors.Piece("straw_post", 0).at(STRAW_POST_A, STRAW_POST_B).dye(STRAW, STRAW_DYE) : null;
        }
        String kind = group == null ? "" : group;
        boolean single = width == 1 && height == 1;
        boolean alongY = faces == Outdoors.NORTH || faces == Outdoors.SOUTH;
        int along = alongY ? height : width, across = alongY ? width : height;
        float surfaceHigh = surface / WorldScene.SURFACE_PIXELS_PER_LEVEL;
        if ((called.equals("Single Stacked Hay") || called.equals("Double Stacked Hay")) && single) {
            return hay(Sheets.place(name, HAY), called.startsWith("Double"));
        }
        if (called.equals("Stack") && faces != Outdoors.NONE) {
            // A stack the game paints over another is the next layer of it, as high up as the game says.
            if ((kind.equals("6 Log") || kind.equals("5 Log")) && along == 2 && across == 1 && surface > 0) {
                return new Outdoors.Piece(kind.equals("6 Log") ? "log_stack_6" : "log_stack_5", faces).dye(DARK_WOOD, LOG_DYE).fit().raise(surfaceHigh);
            }
            if (kind.equals("Large Log") && along == 3 && across == 2) {
                return new Outdoors.Piece("log_bundle", faces).dye(DARK_WOOD, BUNDLE_DYE).fit();
            }
            return null;
        }
        if (called.equals("Trough") && faces != Outdoors.NONE) {
            return trough(kind, faces, along, across);
        }
        if (called.equals("House") && kind.equals("Dog") && single && faces != Outdoors.NONE) {
            return new Outdoors.Piece("dog_house", faces).dye(PAINT, ROOF_DYE).fit();
        }
        if (called.equals("Composter") && single) {
            int at = Sheets.place(name, CARPENTRY);
            return at == 116 || at == 117 ? new Outdoors.Piece(at == 117 ? "composter_full" : "composter", 0).dye(WOOD, COMPOST_DYE).fit() : null;
        }
        // The crate the game calls a rain barrel: lined with a sheet whose edge is folded over its boards, with rain in it or none.
        if (called.equals("Rain Collector Barrel") && single) {
            return switch (Sheets.place(name, CARPENTRY)) {
                case 54, 120 -> new Outdoors.Piece("rain_crate", 0).dye(CLOTH, CRATE_CORNER, CRATE_CORNER, SHEET_DYE).fit();
                case 55, 121 -> new Outdoors.Piece("rain_crate_water", 0).dye(CLOTH, CRATE_CORNER, CRATE_CORNER, SHEET_DYE).also(ENAMEL, RAIN_DYE).fit();
                default -> null;
            };
        }
        if (called.equals("Raised Plantbed") && single) {
            return switch (Sheets.place(name, POTS)) {
                case 8 -> new Outdoors.Piece("raised_bed", 0).dye(WOOD, BED_DYE).also(PLASTIC, FRONDS_DYE).fit();
                case 16, 24 -> new Outdoors.Piece("raised_bed_dry", 0).dye(WOOD, BED_DYE).also(PLASTIC, FRONDS_DYE).fit();
                case 32 -> new Outdoors.Piece("raised_bed_bare", 0).dye(WOOD, BED_DYE).fit();
                default -> null;
            };
        }
        if (called.equals("Mall Decoration") && single && Sheets.place(name, MALL) == 47) {
            return new Outdoors.Piece("mall_planter", 0).dye(PAINT, PLANTER_DYE).also(PLASTIC, PLANTER_LEAVES_DYE).fit();
        }
        if (called.equals("Bin") && kind.equals("Washing") && single && faces != Outdoors.NONE) {
            return new Outdoors.Piece("washing_bin", faces).dye(CLOTH, CART_DYE).fit();
        }
        if (called.equals("Bird Bath") && single) {
            int at = Sheets.place(name, ORNAMENTS);
            return at == 50 || at == 51 ? new Outdoors.Piece(at == 50 ? "bird_bath" : "bird_bath_round", 0).dye(PAINT, BATH_DYE).also(ENAMEL, BATH_WATER).fit() : null;
        }
        if (called.equals("Salt Lick") && single) {
            return new Outdoors.Piece("salt_lick", 0).dye(PAINT, LICK_DYE).fit();
        }
        if (called.equals("Bench") && kind.equals("Log") && along == 1 && across == 2 && faces != Outdoors.NONE) {
            return new Outdoors.Piece("log_bench", faces).dye(WOOD, BENCH_DYE).also(DARK_WOOD, BENCH_BARK_DYE).fit();
        }
        if (called.equals("Stump") && single && name.startsWith(CRAFTED)) {
            return new Outdoors.Piece("stump", 0).fit();
        }
        if (called.equals("Block") && kind.equals("Chopping") && single) {
            return new Outdoors.Piece("chopping_block", 0).fit();
        }
        // A fallen trunk lies aslant across its square, as its model does.
        if (called.equals("Log") && single && name.equals(DEBRIS + "31")) {
            return new Outdoors.Piece("fallen_log", 0).at(LOG_AT_A, LOG_AT_B).dye(DARK_WOOD, LOG_AT_A, LOG_AT_B, FALLEN_DYE);
        }
        if (called.equals("The Gnome") && single && faces != Outdoors.NONE) {
            return new Outdoors.Piece("gnome", faces).dye(PAINT, COAT_DYE).also(VINYL, HAT_DYE).fit();
        }
        if (called.equals("Flamingo") && single && faces != Outdoors.NONE) {
            return new Outdoors.Piece(kind.startsWith("Pecking") ? "flamingo_pecking" : "flamingo", faces).dye(PAINT, FLAMINGO_DYE).fit();
        }
        return single ? onTable(Sheets.place(name, POTS), called, faces, surfaceHigh) : null;
    }

    /**
     * A bale or two of hay. The sheet paints one bale and two one on another, each lying along
     * the map's y and then along its x, and the same again painted up on two bales, or on one.
     */
    private static Outdoors.Piece hay(int at, boolean two) {
        String model = two ? "hay_bales_2" : "hay_bale";
        float dye = two ? HAY_DYE_TWO : HAY_DYE;
        return switch (at) {
            case 8, 10 -> new Outdoors.Piece(model, 0).dye(STRAW, dye).fit();
            case 16, 18 -> new Outdoors.Piece(model, 1).dye(STRAW, dye).fit();
            case 9, 11 -> new Outdoors.Piece(model, 0).dye(STRAW, dye).fit().raise(2f * BALE);
            case 17 -> new Outdoors.Piece(model, 1).dye(STRAW, dye).fit().raise(BALE);
            case 19 -> new Outdoors.Piece(model, 1).dye(STRAW, dye).fit().raise(2f * BALE);
            default -> null;
        };
    }

    /** Whether the game's name and group for a sprite make it a galvanised stock tank, which has a floor and stands on the ground. */
    static boolean tank(String called, String group) {
        return "Trough".equals(called) && ("Small Metal".equals(group) || "Large Metal".equals(group));
    }

    /** A trough lies across the way it faces: a wooden one on legs, the galvanised tanks with round ends, and a feeder with a rack for hay. */
    private static Outdoors.Piece trough(String kind, int faces, int along, int across) {
        if (kind.equals("Huge Metal") && along == 2 && across == 2 && (faces == Outdoors.SOUTH || faces == Outdoors.EAST)) {
            int at = faces == Outdoors.SOUTH ? 0 : 2;
            return new Outdoors.Piece("hay_feeder", faces).dye(PAINT, FEEDER_POST[at], FEEDER_POST[at + 1], FEEDER_DYE).fit();
        }
        if (along != 1) {
            return null;
        }
        // Its colour is taken from its side towards the game's view, on its first square.
        float sideA = 0.5f + (faces == Outdoors.EAST || faces == Outdoors.WEST ? TROUGH_SIDE : 0f);
        float sideB = 0.5f + (faces == Outdoors.EAST || faces == Outdoors.WEST ? 0f : TROUGH_SIDE);
        return switch (kind) {
            case "Animal" -> across == 2 ? new Outdoors.Piece("feed_trough", faces).dye(WOOD, sideA, sideB, TROUGH_DYE).fit().trough() : null;
            case "Small Animal" -> across == 1 ? new Outdoors.Piece("feed_trough_small", faces).dye(WOOD, sideA, sideB, TROUGH_DYE).fit().trough() : null;
            case "Small Metal" -> across == 2 ? new Outdoors.Piece("stock_tank", faces).dye(STEEL, sideA, sideB, TROUGH_DYE).fit().trough() : null;
            case "Large Metal" -> across == 3 ? new Outdoors.Piece("stock_tank_long", faces).dye(STEEL, sideA, sideB, TROUGH_DYE).fit().trough() : null;
            default -> null;
        };
    }

    /**
     * The plants and flowers that stand on a table, painted on one as high as the game says
     * its surface is, each where on the square its sprite paints it. A pot gives the colour of
     * its flowers and, over the same place, of its leaves.
     */
    private static Outdoors.Piece onTable(int at, String called, int faces, float table) {
        Outdoors.Piece plant = switch (at) {
            case 0 -> called.equals("Venus Fly Trap") ? new Outdoors.Piece("pot_flowers", 0).at(0.535f, 0.59f).dye(VINYL, 0.57f, 0.555f, 0.12f).also(PLASTIC, 0.073f) : null;
            case 1 -> called.equals("Bonsai Tree") ? new Outdoors.Piece("bonsai", 0).at(0.39f, 0.39f).dye(CERAMIC, 0.39f, 0.39f, 0.016f).also(PLASTIC, 0.146f) : null;
            case 2 -> called.equals("Voilet Flowers") ? new Outdoors.Piece("pot_violets", 0).at(0.195f, 0.865f).dye(VINYL, 0.257f, 0.803f, 0.166f).also(PLASTIC, 0.083f) : null;
            case 3 -> called.equals("Cactus") ? new Outdoors.Piece("cactus_pot", 0).at(0.195f, 0.895f).dye(PLASTIC, 0.195f, 0.895f, 0.096f) : null;
            case 4, 5 -> called.equals("Roses") && faces != Outdoors.NONE
                    ? new Outdoors.Piece("rose_bouquet", faces).at(at == 4 ? 0.36f : 0.59f, at == 4 ? 0.61f : 0.39f)
                            .dye(VINYL, at == 4 ? 0.305f : 0.641f, at == 4 ? 0.665f : 0.329f, 0.042f).also(PLASTIC, -0.015f) : null;
            case 6 -> called.equals("Orange Plant") ? new Outdoors.Piece("vase_flowers", 0).at(0.23f, 0.86f).dye(VINYL, 0.17f, 0.92f, 0.171f).also(PLASTIC, 0.119f) : null;
            default -> null;
        };
        return plant == null ? null : plant.raise(table);
    }
}

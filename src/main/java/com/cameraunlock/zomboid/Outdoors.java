package com.cameraunlock.zomboid;

/**
 * What stands out of doors that the mod has a model of: on a flat roof its ducts, pipes,
 * ventilators and fans, in a yard its deck chairs and bins, along a street its benches, cones
 * and hydrants, in a graveyard its stones. Which model a sprite is, and how the model stands on
 * its square, by what the game says of the sprite and, for the many the game says nothing of,
 * by its place on its sheet.
 *
 * Where a model lies on its square, which way it is turned and where its sprite gives it its
 * colour were each read off the game's sprite of it: the sprite's own picture says which way a
 * duct runs and which end of it turns down.
 */
final class Outdoors {
    /** How a model stands on its square. */
    static final class Piece {
        final String model;
        /** Quarter turns from facing north, as Models.turn takes them. */
        final int turns;
        /** Where the model's origin is on its square, for a piece on one square. */
        float a = 0.5f, b = 0.5f;
        /** How far above the floor its foot is, in tiles. */
        float raised;
        /** The surfaces the sprite gives their colour, and where on the square and how high, in levels, it gives it. */
        int dyed;
        float dyeA = 0.5f, dyeB = 0.5f, dyeHigh;
        /** The surfaces that take their colour from another height over the same place, and that height, in levels. */
        int alsoDyed;
        float alsoHigh;
        /** Whether it is made no bigger than fits inside its squares, and not a metre to the tile so as to meet the next. */
        boolean fits;
        /** Whether each square of a piece the game lays on several is a piece of its own. */
        boolean each;
        /**
         * For a piece the game paints in two sprites on two squares side by side, with no grid to tie them: the name of the
         * other one and how far its square is from this one's. Neither stands its model unless the other is there.
         */
        String partner;
        int partnerX, partnerY;
        /** Whether this is the one of such a pair that stands nothing: the other stands the whole piece. */
        boolean follows;
        /**
         * Whether the model's pane shows the sprite's own painting, each point of it looking at where the sprite paints that
         * point, and how far along the model's y the pane's face is: a road sign's plate.
         */
        boolean pictured;
        float paneY;

        Piece(String model, int turns) {
            this.model = model;
            this.turns = turns;
        }

        Piece at(float a, float b) {
            this.a = a;
            this.b = b;
            return this;
        }

        Piece dye(int surfaces, float a, float b, float high) {
            dyed = surfaces;
            dyeA = a;
            dyeB = b;
            dyeHigh = high;
            return this;
        }

        Piece dye(int surfaces, float high) {
            return dye(surfaces, 0.5f, 0.5f, high);
        }

        Piece also(int surfaces, float high) {
            alsoDyed = surfaces;
            alsoHigh = high;
            return this;
        }

        Piece raise(float levels) {
            raised = levels * ViewCamera.LEVEL_HEIGHT;
            dyeHigh += levels;
            alsoHigh += levels;
            return this;
        }

        Piece each() {
            each = true;
            return this;
        }


        Piece pictured(float y) {
            pictured = true;
            paneY = y;
            return this;
        }

        Piece pairedWith(String name, int x, int y) {
            partner = name;
            partnerX = x;
            partnerY = y;
            return this;
        }

        Piece fit() {
            fits = true;
            return this;
        }

        /** Which square of a piece on several gives the colour, where the first has nothing painted where it is looked for. */
        int dyeX, dyeY;
        /** How high the game says the piece's top is, in tiles, for one made as high as that, or 0. */
        float top;

        Piece dyeOn(int x, int y) {
            dyeX = x;
            dyeY = y;
            return this;
        }

        /** As fit, and made as high as top tiles where that is less than it fits. */
        Piece fitTo(float top) {
            this.top = top;
            return fit();
        }

    }

    /** The game's facings as numbers: the quarter turns of a model that faces that way. */
    static final int NONE = -1, NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;

    private static final String WORKS = "industry_02_", PLANT = "industry_01_", ROOFTOP = "rooftop_furniture_", BUILDING_SITE = "construction_01_", LAUNDRY = "appliances_laundry_01_",
            MILITARY_BARRIER = "fencing_01_96", CARPENTRY = "carpentry_02_", FENCES = "fencing_01_",
            GAS2GO = "location_shop_gas2go_01_", FOSSOIL = "location_shop_fossoil_01_",
            ROAD_SIGNS = "street_roadsigns_01_", BURNT_FENCES = "fencing_burnt_01_", PYLONS = "electricity_pylon_", MASTS = "radio_tower_";
    private static final int PAINT = 1 << Models.PAINT;
    /** How far from the edge of its square the middle of a pipe along the foot of a wall is. */
    private static final float PIPE_EDGE = 0.12f;
    /** How high on its sprite a duct gives its colour, a pipe, a wide pipe on the ground, the case of a fan and a girder, in levels. */
    private static final float DUCT_DYE = 0.12f, PIPE_DYE = 0.053f, CULVERT_DYE = 0.06f, UNIT_DYE = 0.12f, GIRDER_DYE = 0.11f;
    /**
     * How high the game's sprites paint what stands on top of a roof's machine, in levels: on the
     * low case and on the tall one. The sprites of a ventilator come three times, on the roof
     * and at each of these heights.
     */
    private static final float ON_LOW_CASE = 92f / 192f, ON_TALL_CASE = 190f / 192f;
    /** How far under its top a case gives its colour, in levels. */
    private static final float CASE_DYE = 0.02f;
    /** How far from the middle of its square a gravestone stands, towards the side it faces. */
    private static final float STONE_FORWARD = 0.2f;
    /** How far from the middle of its square an old worn headstone stands, its plinth being deeper than a newer one's. */
    private static final float BATTERED_FORWARD = 0.12f;
    /** How high on its sprite a stack of logs gives the colour of its bark, in levels. */
    private static final float LOG_DYE = 0.18f;
    /**
     * How high on its sprite a rain barrel's rain gives its colour, in levels, over the middle of the square; and where
     * the sheet tied over one hangs over its rim, on the side of it nearest the game's view.
     */
    private static final float RAIN_DYE = 0.31f, SHEET_AT = 0.75f, SHEET_DYE = 0.33f;
    /** Where on its square a stop sign's post is painted, for one that faces north, east, south and west. */
    private static final float[] STOP_SIGN_AT = {0.64f, 0.61f, 0.35f, 0.78f, 0.35f, 0.37f, 0.41f, 0.37f};

    /** How high on its sprite the slats of a public litter bin give their colour, in levels. */
    private static final float BIN_DYE = 0.2f;

    private Outdoors() {}

    /**
     * The model of a sprite and how it stands, or null for a sprite that is none of these.
     *
     * @param called  the game's CustomName for it, or null
     * @param group   its GroupName, or null
     * @param faces   NORTH to WEST, or NONE where the game gives it no facing
     * @param width   how many squares the piece lies on along x, and height along y
     * @param surface how high the game says its top is, in the pixels of its unscaled sprites, or 0
     */
    static Piece of(String name, String called, String group, int faces, int width, int height, int surface) {
        Piece farm = Farm.of(name, called, group, faces, width, height, surface);
        if (farm != null) {
            return farm;
        }
        Piece play = Leisure.of(name, called, group, faces, width, height);
        if (play != null) {
            return play;
        }
        boolean single = width == 1 && height == 1;
        Piece burnt = single ? burntPost(name) : null;
        if (burnt != null) {
            return burnt;
        }
        if (called == null) {
            if (!single) {
                return null;
            }
            Piece overhead = Overhead.of(name);
            if (overhead != null) {
                return overhead;
            }
            int works = Sheets.place(name, WORKS);
            if (works >= 0) {
                return works(works);
            }
            int rooftop = Sheets.place(name, ROOFTOP);
            if (rooftop >= 0) {
                return rooftop(rooftop);
            }
            Piece post = fencePost(name);
            if (post != null) {
                return post;
            }
            Piece pump = fuelPump(name);
            if (pump != null) {
                return pump;
            }
            Piece sign = warningSign(name);
            if (sign != null) {
                return sign;
            }
            // A storey of a substation gantry's narrow lattice column, the lowest on its pad, and a radio mast's in white.
            int pylon = Sheets.place(name, PYLONS);
            if (pylon == 176) {
                return new Piece("lattice_post", 0);
            }
            if (pylon == 181) {
                return new Piece("lattice_post_foot", 0);
            }
            if (Sheets.place(name, MASTS) == 81) {
                return new Piece("lattice_post_white", 0);
            }
            // Roofing felt in rolls: two long rolls side by side and a banded bundle of short ones, each along y and then along x.
            return switch (Sheets.place(name, PLANT)) {
                case 40, 42 -> new Piece("felt_bundle", 0);
                case 41 -> new Piece("felt_roll", 0);
                case 43, 45 -> new Piece("felt_bundle", 1);
                case 44 -> new Piece("felt_roll", 1);
                default -> switch (Sheets.place(name, BUILDING_SITE)) {
                    // A girder on the ground, painted along x from either end.
                    case 42, 43 -> new Piece("girder", 1).dye(PAINT, GIRDER_DYE);
                    default -> null;
                };
            };
        }
        String kind = group == null ? "" : group;
        float surfaceHigh = surface / WorldScene.SURFACE_PIXELS_PER_LEVEL;
        boolean alongY = faces == NORTH || faces == SOUTH;
        int along = alongY ? height : width, across = alongY ? width : height;
        if (called.equals("Chair") && kind.equals("Beach") && faces != NONE && along == 2 && across == 1) {
            return new Piece("deck_chair", faces).fit();
        }
        // A street bench is a square long, and the game lays two end to end as one piece as often as it stands one alone:
        // each square of it is a bench of its own, and two of them meet.
        if (called.equals("Bench") && kind.equals("Birchwood") && faces != NONE && along == 1 && across <= 2 && surface > 0) {
            return new Piece("slab_bench", faces).dye(PAINT, surfaceHigh).each();
        }
        // A stack of logs on the ground. The game has the same stacks painted on a load as well, with a height, and those stay as they are.
        if (called.equals("Stack") && (kind.equals("6 Log") || kind.equals("5 Log")) && faces != NONE && along == 2 && across == 1 && surface == 0) {
            return new Piece(kind.equals("6 Log") ? "log_stack_6" : "log_stack_5", faces).dye(1 << Models.DARK_WOOD, LOG_DYE).fit();
        }
        // A heap of sand on a square of four, its top over the middle of them. The first square paints only the back of its foot.
        if (called.equals("Sand Pile") && width == 2 && height == 2) {
            return kind.equals("Large") ? new Piece("sand_pile_large", 0).dye(PAINT, 0.9f, 0.9f, 0.23f)
                    : kind.equals("Medium") || kind.equals("Small") ? new Piece("sand_pile", 0).dye(PAINT, 0.98f, 0.98f, 0.2f) : null;
        }
        if (!single) {
            return null;
        }
        if (called.equals("Tire") || called.equals("Tires")) {
            return tyres(Sheets.place(name, TYRES));
        }
        // A barrel set out to catch the rain: open, with rain in it, with a sheet tied over its mouth, and with a lid laid on
        // that sheet. The others of the name are crates lined with a sheet, which Farm stands before this is asked.
        if (called.equals("Rain Collector Barrel")) {
            return switch (Sheets.place(name, CARPENTRY)) {
                case 52 -> new Piece("rain_barrel", 0).fit();
                case 53 -> new Piece("rain_barrel_water", 0).dye(1 << Models.ENAMEL, RAIN_DYE).fit();
                case 122, 123, 124, 125 -> new Piece("rain_barrel_tarp", 0).dye(1 << Models.CLOTH, SHEET_AT, SHEET_AT, SHEET_DYE).fit();
                case 126, 127 -> new Piece("rain_barrel_lid", 0).dye(1 << Models.CLOTH, SHEET_AT, SHEET_AT, SHEET_DYE).fit();
                default -> null;
            };
        }
        if (called.equals("Fence Post")) {
            return fencePost(name);
        }
        if (called.equals("Public Garbage Bin")) {
            return new Piece("slatted_bin", 0).dye(PAINT, BIN_DYE).fit();
        }
        Piece street = street(called, kind, faces);
        if (street != null) {
            return street;
        }
        if (called.equals("Recycle Bin")) {
            return new Piece("dustbin", 0).fit();
        }
        if (called.equals("Road Cone")) {
            return name.endsWith("_26") ? new Piece("road_cone", 0).dye(PAINT, 0.1f).fit()
                    : name.endsWith("_27") ? new Piece("traffic_drum", 0).dye(PAINT, 0.13f).fit() : null;
        }
        if (called.equals("Standpipe")) {
            return new Piece("hydrant", 0).dye(PAINT, 0.18f).fit();
        }
        if (called.equals("Empty Pallet")) {
            return new Piece("pallet", 0).fit();
        }
        if (called.equals("Pallet With Bricks")) {
            return new Piece("brick_pallet", 0).dye(PAINT, 0.28f).fit();
        }
        if (called.equals("Military Barrier") && name.equals(MILITARY_BARRIER)) {
            return new Piece("sack_barrier", 0).dye(PAINT, 0.3f).fit();
        }
        // A stop sign's post stands near a corner of its square, a different one for each way the sign faces.
        if (called.equals("Sign") && kind.equals("Stop") && faces != NONE) {
            return new Piece("stop_sign", faces).at(STOP_SIGN_AT[2 * faces], STOP_SIGN_AT[2 * faces + 1]);
        }
        // A stock trolley stands in the back half of its square, its frame at the edge behind it.
        if (called.equals("Hand Truck") && kind.equals("Empty") && faces != NONE) {
            return new Piece("hand_truck", faces).at(0.5f - 0.25f * (faces == EAST ? 1f : faces == WEST ? -1f : 0f),
                    0.5f - 0.25f * (faces == SOUTH ? 1f : faces == NORTH ? -1f : 0f));
        }
        if (called.equals("Gravestone") && faces != NONE) {
            String stone = kind.equals("Round") ? "gravestone_round" : kind.equals("Arched") ? "gravestone_gabled"
                    : kind.equals("Square") ? "gravestone_square" : kind.equals("Battered") ? "gravestone_battered" : null;
            if (stone == null) {
                return null;
            }
            float forward = kind.equals("Battered") ? BATTERED_FORWARD : STONE_FORWARD;
            float a = 0.5f + (faces == EAST ? forward : faces == WEST ? -forward : 0f);
            float b = 0.5f + (faces == SOUTH ? forward : faces == NORTH ? -forward : 0f);
            return new Piece(stone, faces).at(a, b).dye(PAINT, a, b, 0.16f);
        }
        return null;
    }

    /** The sheet the game's loose tyres are on. */
    private static final String TYRES = "location_business_machinery_01_";
    /**
     * Where on its square the middle of the lowest tyre is, for the sprites of tyres from 40 to 52
     * of their sheet, as a and b: read off each sprite as the middle of the lowest tyre's outline.
     * A tyre is 0.64 across, so its middle is kept 0.34 in from the square's edges.
     */
    private static final float[] TYRE_AT = {0.41f, 0.50f, 0.45f, 0.49f, 0.46f, 0.51f, 0.47f, 0.53f, 0.43f, 0.51f, 0.42f, 0.52f,
            0f, 0f, 0f, 0f, 0.40f, 0.44f, 0.34f, 0.49f, 0.41f, 0.43f, 0.48f, 0.52f, 0.48f, 0.55f};

    /**
     * The tyres of their sheet: one alone, straight stacks of two to four, and stacks of two to
     * four thrown on less squarely, which the game paints once for a piece facing north and once,
     * thrown another way, for one facing west. Each of those has a model of its own, its tyres
     * lying where the sprite paints them, so none is turned.
     */
    private static Piece tyres(int place) {
        String model = switch (place) {
            case 40 -> "tyre_pile_2n";
            case 41 -> "tyre_pile_2w";
            case 42 -> "tyre_pile_3n";
            case 43 -> "tyre_pile_3w";
            case 44 -> "tyre_pile_4n";
            case 45 -> "tyre_pile_4w";
            case 48, 49 -> "tyre";
            case 50 -> "tyre_stack_2";
            case 51 -> "tyre_stack_3";
            case 52 -> "tyre_stack_4";
            default -> null;
        };
        return model == null ? null : new Piece(model, 0).at(TYRE_AT[2 * (place - 40)], TYRE_AT[2 * (place - 40) + 1]);
    }

    /** Which way a length of washing line runs from over the middle of its square to over the middle of the next. */
    static final int NO_LINE = 0, LINE_EAST = 1, LINE_SOUTH = 2;
    /** How high over the land a washing line's two lines are, in tiles: it runs round a pulley at each end, one line over the other. */
    static final float[] LINES_UP = {1.93f, 2.26f};

    /** Whether a sprite is a square's length of washing line, and which way it runs: it is no model, the picture strings it as it does a wire. */
    static int washingLine(String name) {
        return switch (Sheets.place(name, LAUNDRY)) {
            case 30 -> LINE_EAST;
            case 31 -> LINE_SOUTH;
            default -> NO_LINE;
        };
    }

    /**
     * The sheet of what a works has. A length of duct runs along y on one sprite and along x on
     * the next. The end of a duct that turns down is the model's back, and an elbow's two ends
     * are the model's front and its +x side, which facing north is the west: so an elbow whose
     * ends are to the north and the west is not turned, and each quarter turn takes both ends
     * on the way north goes to east. A pipe lies along one edge of its square, and two that meet
     * lie along the two edges of one of its corners.
     */
    private static Piece works(int place) {
        float far = 1f - PIPE_EDGE;
        return switch (place) {
            case 32 -> new Piece("culvert_pipe", 0).dye(PAINT, CULVERT_DYE);
            case 33 -> new Piece("culvert_pipe", 1).dye(PAINT, CULVERT_DYE);
            case 140 -> new Piece("duct_down", 2).dye(PAINT, DUCT_DYE);
            case 141 -> new Piece("duct_down", 1).dye(PAINT, DUCT_DYE);
            case 142 -> new Piece("duct_down", 0).dye(PAINT, DUCT_DYE);
            case 143 -> new Piece("duct_down", 3).dye(PAINT, DUCT_DYE);
            case 144 -> new Piece("duct_elbow", 1).dye(PAINT, DUCT_DYE);
            case 145 -> new Piece("duct_elbow", 2).dye(PAINT, DUCT_DYE);
            case 146 -> new Piece("duct_elbow", 3).dye(PAINT, DUCT_DYE);
            case 147 -> new Piece("duct_elbow", 0).dye(PAINT, DUCT_DYE);
            case 148 -> new Piece("duct_run", 0).dye(PAINT, DUCT_DYE);
            case 149 -> new Piece("duct_run", 1).dye(PAINT, DUCT_DYE);
            case 150 -> new Piece("fan_unit", 0).dye(PAINT, 0.5f, 0.9f, UNIT_DYE);
            case 224 -> new Piece("pipe_run", 0).at(PIPE_EDGE, 0.5f).dye(PAINT, PIPE_EDGE, 0.5f, PIPE_DYE);
            case 226 -> new Piece("pipe_run", 1).at(0.5f, PIPE_EDGE).dye(PAINT, 0.5f, PIPE_EDGE, PIPE_DYE);
            case 230 -> new Piece("pipe_run", 1).at(0.5f, far).dye(PAINT, 0.5f, far, PIPE_DYE);
            case 231 -> new Piece("pipe_run", 0).at(far, 0.5f).dye(PAINT, far, 0.5f, PIPE_DYE);
            case 225 -> new Piece("pipe_corner", 0).dye(PAINT, PIPE_EDGE, 0.6f, PIPE_DYE);
            case 229 -> new Piece("pipe_corner", 1).dye(PAINT, 0.5f, PIPE_EDGE, PIPE_DYE);
            case 228 -> new Piece("pipe_corner", 2).dye(PAINT, 0.5f, far, PIPE_DYE);
            case 227 -> new Piece("pipe_corner", 3).dye(PAINT, PIPE_EDGE, 0.4f, PIPE_DYE);
            default -> null;
        };
    }

    /**
     * A fence's post that stands alone on its square, by its place on the sheet of fences, or null for any other sprite of
     * it. Each stands where its sprite paints the middle of its foot, near the north-west corner where the fence's two edges
     * meet, and takes its colour from its sprite there. Stood up as a card it turned to the eye, and its picture slid across
     * it as the eye went by.
     */
    private static Piece fencePost(String name) {
        return switch (Sheets.place(name, FENCES)) {
            case 7 -> new Piece("picket_post", 0).at(0.031f, 0.039f).dye(PAINT, 0.031f, 0.039f, 0.2f).fit();
            case 19 -> new Piece("rail_post", 0).at(0.133f, 0.102f).dye(1 << Models.WOOD, 0.133f, 0.102f, 0.2f).fit();
            case 29 -> new Piece("pipe_post", 0).at(0.062f, 0.094f).dye(PAINT, 0.062f, 0.094f, 0.2f).fit();
            case 61 -> new Piece("pipe_post_tall", 0).at(0.062f, 0.094f).dye(PAINT, 0.062f, 0.094f, 0.3f).fit();
            case 53 -> new Piece("block_post", 0).at(0.168f, 0.168f).dye(PAINT, 0.168f, 0.168f, 0.5f).fit();
            default -> null;
        };
    }

    /**
     * What stands along a street or on a building site and has a name of its own, by that name and which way it faces, or
     * null. Where each stands on its square was read off its sprite: a concrete barrier lies along the edge of its square
     * behind the way it faces, as the game lets it be hopped over there, and the game has it facing south and east only.
     */
    private static Piece street(String called, String kind, int faces) {
        boolean south = faces == SOUTH, east = faces == EAST;
        if (called.equals("Block") && kind.equals("Concrete Road") && (south || east)) {
            return south ? new Piece("jersey_barrier", faces).at(0.5f, BARRIER_IN).dye(PAINT, 0.5f, 0.33f, 0.06f)
                    : new Piece("jersey_barrier", faces).at(BARRIER_IN, 0.5f).dye(PAINT, 0.33f, 0.5f, 0.06f);
        }
        // A road works barricade, dyed from the middle of a stripe of its bright paint.
        if (called.equals("Block") && kind.equals("Road") && (south || east)) {
            return south ? new Piece("road_barricade", faces).at(0.47f, 0.44f).dye(PAINT, 0.2f, 0.62f, 0.12f)
                    : new Piece("road_barricade", faces).at(0.4f, 0.5f).dye(PAINT, 0.62f, 0.5f, 0.14f);
        }
        if (called.equals("Lights") && kind.equals("Flood") && faces != NONE) {
            return new Piece("flood_light", faces).at(0.49f, 0.51f).dye(PAINT, 0.49f, 0.51f, 0.6f);
        }
        // A cement mixer stands a little to the front of its square's middle, and its drum gives the colour.
        if (called.equals("Grinder") && kind.equals("Mortar") && faces != NONE) {
            float a = 0.5f + (east ? MIXER_FORWARD : faces == WEST ? -MIXER_FORWARD : 0f), b = 0.5f + (south ? MIXER_FORWARD : faces == NORTH ? -MIXER_FORWARD : 0f);
            return new Piece("cement_mixer", faces).at(a, b).dye(PAINT, a, b, 0.33f).fit();
        }
        return null;
    }

    /** How far the middle of a concrete barrier is from the edge of its square it lies along, and a cement mixer from its square's middle. */
    private static final float BARRIER_IN = 0.19f, MIXER_FORWARD = 0.05f;
    /** Where on its square a petrol pump's middle is, along and across its long way, for the sprite that stands it, and where on that square its colour is taken. */
    private static final float PUMP_ALONG = 0.25f, PUMP_ACROSS = 0.39f, PUMP_FRONT = 0.64f;
    /** How high on its sprite a pump gives the colour of its low panel and of its head, in levels. */
    private static final float PUMP_PANEL_DYE = 0.12f, PUMP_HEAD_DYE = 0.88f;

    /**
     * A petrol pump, which the game paints in two sprites on two squares side by side, the pump straddling the edge between
     * them though most of it is on the square of the first: facing east the first and the second to its north, facing south
     * the first and the second to its west. The first stands the whole pump and the second nothing, each only while the other
     * is there. Two brands of station have the same pump in their own colours.
     */
    private static Piece fuelPump(String name) {
        String sheet = name.startsWith(GAS2GO) ? GAS2GO : name.startsWith(FOSSOIL) ? FOSSOIL : null;
        if (sheet == null) {
            return null;
        }
        Piece pump = switch (Sheets.place(name, sheet)) {
            case 12 -> new Piece("fuel_pump", EAST).at(PUMP_ACROSS, PUMP_ALONG).pairedWith(sheet + "13", 0, -1)
                    .dye(PAINT, PUMP_FRONT, PUMP_ALONG, PUMP_PANEL_DYE);
            case 13 -> new Piece(null, EAST).pairedWith(sheet + "12", 0, 1);
            case 14 -> new Piece("fuel_pump", SOUTH).at(PUMP_ALONG, PUMP_ACROSS).pairedWith(sheet + "15", -1, 0)
                    .dye(PAINT, PUMP_ALONG, PUMP_FRONT, PUMP_PANEL_DYE);
            case 15 -> new Piece(null, SOUTH).pairedWith(sheet + "14", 1, 0);
            default -> null;
        };
        if (pump == null) {
            return null;
        }
        if (pump.model == null) {
            pump.follows = true;
            return pump;
        }
        return pump.also(1 << Models.VINYL, PUMP_HEAD_DYE);
    }

    /** Where the face of a warning sign's plate is along its model's y: WARNING_PLATE_Y in furniture.py. */
    static final float WARNING_PLATE_Y = -0.035f;
    /** How high over its foot a warning sign's plate is painted, in levels: where its colour is looked up, to be sure the sprite is there. */
    private static final float SIGN_PLATE_HIGH = 0.743f;
    /**
     * Where on its square a warning sign's post is painted, for one whose plate faces north, east, south and west. The game
     * gives these sprites no facing: which way a plate faces was read off its picture, the face shown on those that face
     * south or east, the bare back on those that face north or west.
     */
    private static final float[] SIGN_POST_AT = {0.47f, 0.48f, 0.47f, 0.53f, 0.5f, 0.5f, 0.44f, 0.5f};

    /**
     * A diamond warning sign on a post, by its place on the sheet of road signs, or null. Its plate shows the sprite's own
     * painting, so a bend's arrow is the arrow the game paints. Stood up as the lathe of its outline it was a spinning top.
     */
    private static Piece warningSign(String name) {
        int faces = switch (Sheets.place(name, ROAD_SIGNS)) {
            case 4 -> NORTH;
            case 5 -> WEST;
            case 8, 9, 10, 11, 12 -> SOUTH;
            case 16, 17, 18, 19, 20 -> EAST;
            default -> NONE;
        };
        if (faces == NONE) {
            return null;
        }
        float a = SIGN_POST_AT[2 * faces], b = SIGN_POST_AT[2 * faces + 1];
        return new Piece("warning_sign", faces).at(a, b).dye(1 << Models.PANE, a, b, SIGN_PLATE_HIGH).pictured(WARNING_PLATE_Y);
    }

    /**
     * A burnt fence's post that stands alone on its square, by its place on the sheet of burnt fences, or null. Each stands
     * where its sprite paints the middle of its foot, by the north-west corner where the fence's two edges meet, and takes
     * its colour from its sprite there. Stood up as a card it turned to the eye.
     */
    private static Piece burntPost(String name) {
        return switch (Sheets.place(name, BURNT_FENCES)) {
            case 3 -> new Piece("burnt_picket_post", 0).at(0.06f, 0.06f).dye(1 << Models.DARK_WOOD, 0.06f, 0.06f, 0.2f);
            case 13 -> new Piece("burnt_tall_post", 0).at(0.1f, 0.1f).dye(1 << Models.DARK_WOOD, 0.1f, 0.1f, 0.4f);
            case 45 -> new Piece("burnt_rail_post", 0).at(0.07f, 0.07f).dye(1 << Models.DARK_WOOD, 0.07f, 0.07f, 0.25f);
            case 53 -> new Piece("fence_pole", 0).at(0.03f, 0.06f).dye(PAINT, 0.03f, 0.06f, 0.4f);
            case 61 -> new Piece("fence_pole_wide", 0).at(0.08f, 0.06f).dye(PAINT, 0.08f, 0.06f, 0.4f);
            case 69 -> new Piece("fence_pole_capped", 0).at(0.03f, 0.06f).dye(PAINT, 0.03f, 0.06f, 0.25f);
            default -> null;
        };
    }

    /**
     * The sheet of what stands on a flat roof and has no name: the cases of its machines, and the ventilators, fans and vent pipes.
     * A case wrapped on the box of its sprite's outline had the roof's gravel for a top where a fan stood on it.
     */
    private static Piece rooftop(int place) {
        return switch (place) {
            case 0 -> new Piece("roof_case_tall", 0).dye(PAINT, ON_TALL_CASE - CASE_DYE);
            case 1 -> new Piece("roof_case_low", 0).dye(PAINT, ON_LOW_CASE - CASE_DYE);
            case 22 -> new Piece("roof_dome", 0).dye(PAINT, 0.1f);
            case 23 -> new Piece("roof_dome", 0).dye(PAINT, 0.1f).raise(ON_LOW_CASE);
            case 16 -> new Piece("roof_dome", 0).dye(PAINT, 0.1f).raise(ON_TALL_CASE);
            case 15 -> new Piece("roof_fan", 0).dye(PAINT, 0.5f, 0.92f, 0.02f).raise(ON_LOW_CASE);
            case 14 -> new Piece("roof_fan", 0).dye(PAINT, 0.5f, 0.92f, 0.02f).raise(ON_TALL_CASE);
            case 24 -> new Piece("roof_stacks", 0).dye(PAINT, 0.5f, 0.75f, 0.01f);
            case 25 -> new Piece("roof_stacks", 1).dye(PAINT, 0.5f, 0.75f, 0.01f);
            default -> null;
        };
    }
}

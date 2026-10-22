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
         * For a piece the game paints in sprites on three squares in a row, with no grid to tie them: the other two's
         * names and how far each one's square is from this one's, and which of them gives the piece its colour, or -1
         * for this one. Every square of the row reaches the same answer: each stands its share of the piece, or stands
         * nothing and hides its sprite, only where all three are there and the one that gives the colour paints it.
         */
        String[] row;
        int[] rowX, rowY;
        int rowDyer = -1;
        /**
         * Whether the model's pane shows the sprite's own painting, each point of it looking at where the sprite paints that
         * point, and how far along the model's y the pane's face is: a road sign's plate.
         */
        boolean pictured;
        float paneY;
        /**
         * Whether it stands on the roof of its square where there is one: the game paints it over the floor of the
         * roof's level, and the mod's roof is over that floor. See RoofFit.standOn.
         */
        boolean onRoof;

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

        Piece onRoof() {
            onRoof = true;
            return this;
        }

        Piece pairedWith(String name, int x, int y) {
            partner = name;
            partnerX = x;
            partnerY = y;
            return this;
        }

        Piece inRow(String first, int firstX, int firstY, String second, int secondX, int secondY, int dyer) {
            row = new String[] {first, second};
            rowX = new int[] {firstX, secondX};
            rowY = new int[] {firstY, secondY};
            rowDyer = dyer;
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
            ROAD_SIGNS = "street_roadsigns_01_", BURNT_FENCES = "fencing_burnt_01_", PYLONS = "electricity_pylon_", MASTS = "radio_tower_",
            STREET = "street_decoration_01_", FIREPLACES = "fixtures_fireplaces_01_", MACHINERY = "location_business_machinery_01_",
            SEWER = "location_sewer_01_", BANK = "location_business_bank_01_", FARM = "location_farm_accesories_01_",
            GREENES = "location_shop_greenes_01_";
    private static final int PAINT = 1 << Models.PAINT, DARK_WOOD = 1 << Models.DARK_WOOD;
    /** Where a forked porch post stands on its square: how far in from the edge it stands on, and along it; and how high its wood gives its colour, in levels. */
    private static final float FORKED_POST_IN = 0.14f, FORKED_POST_ALONG = 0.53f, FORKED_POST_DYE = 0.4f;
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
    /** How high a street name blade on a signal's post is over its storey's floor, in metres: the model is lowered onto the
     * floor when it is made. */
    private static final float BLADE_UP = 1.0f;

    /** A name blade by its place on the street sheet, its model turned so that its +X runs the way it reaches from the post. */
    private static Piece streetBlade(int place) {
        float up = BLADE_UP / ViewCamera.LEVEL_HEIGHT;
        return switch (place) {
            case 48 -> new Piece("street_blade", SOUTH).at(Furniture.SIGNAL_POST_A, Furniture.SIGNAL_POST_B).dye(PAINT, 0.74f, 0.58f, 0.47f - up).raise(up);
            case 49 -> new Piece("street_blade", EAST).at(Furniture.SIGNAL_POST_A, Furniture.SIGNAL_POST_B).dye(PAINT, 0.54f, 0.38f, 0.45f - up).raise(up);
            case 50 -> new Piece("street_blade", NORTH).at(Furniture.SIGNAL_POST_A, Furniture.SIGNAL_POST_B).dye(PAINT, 0.34f, 0.58f, 0.45f - up).raise(up);
            case 51 -> new Piece("street_blade", WEST).at(Furniture.SIGNAL_POST_A, Furniture.SIGNAL_POST_B).dye(PAINT, 0.54f, 0.78f, 0.47f - up).raise(up);
            default -> null;
        };
    }

    /** How high a coop's front wall and its roof over it are painted, in levels, over a place a little in from its square's south edge. */
    private static final float COOP_DYE = 0.25f, COOP_DYE_B = 0.9f, COOP_ROOF_DYE = 0.65f;

    /** A coop's piece by its sprite: the south-east quarter stands it all while the north-east one is there, the rest follow it. */
    private static Piece coop(String name) {
        String stander = FARM + 42;
        return switch (Sheets.place(name, FARM)) {
            case 42 -> new Piece("coop", SOUTH).at(0f, 0f).pairedWith(FARM + 43, 0, -1).dye(PAINT, 0.5f, COOP_DYE_B, COOP_DYE)
                    .also(1 << Models.METAL, COOP_ROOF_DYE);
            case 43 -> following(new Piece(null, SOUTH).pairedWith(stander, 0, 1));
            case 41, 49 -> following(new Piece(null, SOUTH).pairedWith(stander, 1, 1));
            case 48, 50, 44, 46 -> following(new Piece(null, SOUTH).pairedWith(stander, 1, 0));
            case 45, 47 -> following(new Piece(null, SOUTH).pairedWith(stander, 0, 0));
            default -> null;
        };
    }

    /** Where on its square a cash machine's middle is, out from its wall and along it, and how high its front is painted, in levels. */
    private static final float ATM_BACK = 0.31f, ATM_ALONG = 0.44f, ATM_DYE = 0.25f;
    /** How high a fireplace surround's pillar and its mantel are painted in their stone and on their top, in levels, and
     * how high the mantel's foot is, in metres: the model is lowered onto the floor when it is made. */
    private static final float MANTEL_STONE_DYE = 0.25f, MANTEL_TOP_DYE = 0.54f, MANTEL_BODY_DYE = 0.33f, MANTEL_SHELF_DYE = 0.45f,
            MANTEL_FOOT = 0.62f;
    /** How far from the wall a surround's pieces stand, and where along their wall its two pillars do on their squares. */
    private static final float MANTEL_OFF_WALL = 0.02f, MANTEL_NEAR = 0.17f, MANTEL_FAR = 0.9f;

    /**
     * A piece of a fireplace's surround by its place on the sheet: along the west wall (48 to 50) and along the north wall
     * (51 to 53), and the same eight places on. The first pillar along x stands at the far end of its square and the last at
     * the near end, against the mantel's square between them; along y the same, the sheet having them the other way about.
     */
    private static Piece mantel(int place) {
        int piece = place >= 56 ? place - 8 : place;
        if (piece < 48 || piece > 53) {
            return null;
        }
        float foot = MANTEL_FOOT / ViewCamera.LEVEL_HEIGHT;
        return switch (piece) {
            case 48 -> new Piece("mantel_pillar", EAST).at(MANTEL_OFF_WALL, MANTEL_NEAR).dye(PAINT, 0.2f, MANTEL_NEAR, MANTEL_STONE_DYE)
                    .also(1 << Models.WORKTOP, MANTEL_TOP_DYE);
            case 49 -> new Piece("mantel_lintel", EAST).at(MANTEL_OFF_WALL, 0.5f).dye(PAINT, 0.03f, 0.5f, MANTEL_BODY_DYE - foot)
                    .also(1 << Models.WORKTOP, MANTEL_SHELF_DYE - foot).raise(foot);
            case 50 -> new Piece("mantel_pillar", EAST).at(MANTEL_OFF_WALL, MANTEL_FAR).dye(PAINT, 0.2f, MANTEL_FAR, MANTEL_STONE_DYE)
                    .also(1 << Models.WORKTOP, MANTEL_TOP_DYE);
            case 51 -> new Piece("mantel_pillar", SOUTH).at(MANTEL_FAR, MANTEL_OFF_WALL).dye(PAINT, MANTEL_FAR, 0.2f, MANTEL_STONE_DYE)
                    .also(1 << Models.WORKTOP, MANTEL_TOP_DYE);
            case 52 -> new Piece("mantel_lintel", SOUTH).at(0.5f, MANTEL_OFF_WALL).dye(PAINT, 0.5f, 0.03f, MANTEL_BODY_DYE - foot)
                    .also(1 << Models.WORKTOP, MANTEL_SHELF_DYE - foot).raise(foot);
            default -> new Piece("mantel_pillar", SOUTH).at(MANTEL_NEAR, MANTEL_OFF_WALL).dye(PAINT, MANTEL_NEAR, 0.2f, MANTEL_STONE_DYE)
                    .also(1 << Models.WORKTOP, MANTEL_TOP_DYE);
        };
    }

    /** How high over the middle of its square a generator's sprite paints the top of its body, in levels. */
    private static final float GENERATOR_DYE = 0.18f;
    /** How high a sewer's pipes are painted where their colour is taken, in levels. */
    private static final float SEWER_PIPE_DYE = 0.3f;
    /** Where a car wash's brush's shaft stands on its square, how far in from the north or west edge its curtain hangs, and how
     * high both are painted in their colour, in levels. */
    static final float CAR_WASH_BRUSH_A = 0.45f, CAR_WASH_BRUSH_B = 0.5f, CAR_WASH_CURTAIN = 0.435f;
    private static final float CAR_WASH_DYE = 0.5f;
    /** How high over the floor the foot of a car wash's curtain is, in metres: the model is lowered onto the floor when it is made. */
    private static final float CAR_WASH_STRIPS_FOOT = 0.25f;
    /** Where on its square a chimney's cap is, how high its top is over the floor of its level and where a pot is painted, in levels. */
    static final float CHIMNEY_A = 0.485f, CHIMNEY_B = 0.515f, CHIMNEY_CAP = 0.898f;
    private static final float CHIMNEY_POT_DYE = 0.07f;
    /** Where on its square a parking meter's post is painted, for one that faces north, east, south and west. */
    static final float[] PARKING_METER_AT = {0.53f, 0.84f, 0.25f, 0.53f, 0.54f, 0.3f, 0.83f, 0.52f};
    /** How high on a car park barrier's booth its paint is taken, in levels. */
    private static final float BARRIER_DYE = 0.25f;
    /** How far a flag pole's top length reaches down into the storey under it, how high the sprites paint the pole and the blue
     * by the top of the flag, in levels, and how far along x that blue is. The game lays the pole as a piece over two storeys, so
     * it stands in the middle of its square. */
    static final float FLAG_TOP_SUNK = 0.1f;
    private static final float FLAG_POLE_DYE = 0.5f, FLAG_BLUE_DYE = 0.3f, FLAG_BLUE_A = 0.54f;
    /** Where on its square a crossing signal's post is painted, and how high its sprite paints the post white and a band yellow, in levels. */
    static final float CROSSING_SIGNAL_EW_A = 0.44f, CROSSING_SIGNAL_EW_B = 0.5f, CROSSING_SIGNAL_NS = 0.47f;
    private static final float CROSSING_SIGNAL_WHITE = 0.45f, CROSSING_SIGNAL_YELLOW = 0.23f;
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
        Piece aerial = aerial(name);
        if (aerial != null) {
            return aerial;
        }
        boolean single = width == 1 && height == 1;
        Piece burnt = single ? burntPost(name) : null;
        if (burnt != null) {
            return burnt;
        }
        Piece forked = single ? forkedPost(name) : null;
        if (forked != null) {
            return forked;
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
            // A car wash's upright brush on its floor rail, and its curtains of cloth strips hung along x and along y.
            switch (Sheets.place(name, MACHINERY)) {
                case 14 -> {
                    return new Piece("car_wash_brush", 0).at(CAR_WASH_BRUSH_A, CAR_WASH_BRUSH_B).dye(1 << Models.CLOTH, CAR_WASH_BRUSH_A,
                            CAR_WASH_BRUSH_B, CAR_WASH_DYE);
                }
                case 36 -> {
                    return new Piece("car_wash_curtain", 0).at(0.5f, CAR_WASH_CURTAIN).dye(1 << Models.CLOTH, 0.5f, CAR_WASH_CURTAIN, CAR_WASH_DYE)
                            .raise(CAR_WASH_STRIPS_FOOT / ViewCamera.LEVEL_HEIGHT);
                }
                case 37 -> {
                    return new Piece("car_wash_curtain", 1).at(CAR_WASH_CURTAIN, 0.5f).dye(1 << Models.CLOTH, CAR_WASH_CURTAIN, 0.5f, CAR_WASH_DYE)
                            .raise(CAR_WASH_STRIPS_FOOT / ViewCamera.LEVEL_HEIGHT);
                }
                default -> { }
            }
            // A sewer's two pipes from floor to ceiling along y and then along x, the big one at the far end: a small one that turns
            // into the big one at a little over a metre, or two side by side. Coloured from the big pipe.
            switch (Sheets.place(name, SEWER)) {
                case 34 -> {
                    return new Piece("sewer_pipes_bend", 0).at(0.3f, 0.54f).dye(1 << Models.METAL, 0.28f, 0.77f, SEWER_PIPE_DYE);
                }
                case 37 -> {
                    return new Piece("sewer_pipes_bend", 3).at(0.515f, 0.32f).dye(1 << Models.METAL, 0.74f, 0.31f, SEWER_PIPE_DYE);
                }
                case 35 -> {
                    return new Piece("sewer_pipes", 0).at(0.22f, 0.515f).dye(1 << Models.METAL, 0.22f, 0.64f, SEWER_PIPE_DYE);
                }
                case 36 -> {
                    return new Piece("sewer_pipes", 3).at(0.48f, 0.25f).dye(1 << Models.METAL, 0.6f, 0.25f, SEWER_PIPE_DYE);
                }
                default -> { }
            }
            // A street's name blade on a traffic signal's post, on the storey over its foot: out east, north, west and south.
            Piece blade = streetBlade(Sheets.place(name, STREET));
            if (blade != null) {
                return blade;
            }
            // A chicken coop on four squares: the south-east one stands the whole coop, its middle at that square's north-west
            // corner, and the other three and the doors the game lays over the south two stand nothing.
            Piece coop = coop(name);
            if (coop != null) {
                return coop;
            }
            // A bank's cash machine against the wall behind it, its screen to the east and to the south; coloured from its front.
            switch (Sheets.place(name, BANK)) {
                case 64 -> {
                    return new Piece("atm", EAST).at(ATM_BACK, ATM_ALONG).dye(PAINT, 0.6f, ATM_ALONG, ATM_DYE);
                }
                case 65 -> {
                    return new Piece("atm", SOUTH).at(ATM_ALONG, ATM_BACK).dye(PAINT, ATM_ALONG, 0.6f, ATM_DYE);
                }
                default -> { }
            }
            // A fireplace's surround, a run of three sprites along its wall: a pillar, the mantel over the fire and a pillar. The
            // black marble set and the white one have the same pieces eight places apart on the sheet.
            Piece surround = mantel(Sheets.place(name, FIREPLACES));
            if (surround != null) {
                return surround;
            }
            // Two pots on the cap of a chimney stack, the stack's own top a little under the next level's floor: in a row along
            // y and then along x.
            int fireplace = Sheets.place(name, FIREPLACES);
            if (fireplace == 72 || fireplace == 73) {
                boolean alongY = fireplace == 72;
                return new Piece("chimney_pots", alongY ? 1 : 0).at(CHIMNEY_A, CHIMNEY_B)
                        .dye(PAINT, alongY ? 0.485f : 0.63f, alongY ? 0.36f : 0.515f, CHIMNEY_POT_DYE).raise(CHIMNEY_CAP);
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
        // A car park's barrier on four squares in a row: its booth on the last square behind the way it faces, the arm out the
        // way it faces, the booth's paint taken from its south face, which every facing's sprite paints in the light.
        if (called.equals("Post") && kind.equals("Barrier") && faces != NONE && along == 4 && across == 1) {
            Piece barrier = new Piece("parking_barrier", faces);
            return switch (faces) {
                case NORTH -> barrier.dyeOn(0, 3).dye(PAINT, 0.47f, 0.68f, BARRIER_DYE);
                case EAST -> barrier.dyeOn(0, 0).dye(PAINT, 0.48f, 0.63f, BARRIER_DYE);
                case SOUTH -> barrier.dyeOn(0, 0).dye(PAINT, 0.53f, 0.64f, BARRIER_DYE);
                default -> barrier.dyeOn(3, 0).dye(PAINT, 0.52f, 0.69f, BARRIER_DYE);
            };
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
        // A portable generator, the map's and one a player sets down. The game's sprites have its panel of sockets a quarter turn
        // the other way from the way it faces: east shows it to the south, south to the east.
        if (called.equals("Generator") && kind.equals("Red") && faces != NONE) {
            return new Piece("generator", 3 - faces).dye(PAINT, GENERATOR_DYE);
        }
        // A railway crossing's signal: its post a little west of the square's middle where it faces east or west, and at the
        // middle where it faces north or south. Its post is white and its bands yellow on every facing's sprite.
        if (called.equals("Signal") && kind.equals("Train") && faces != NONE) {
            boolean eastOrWest = faces == EAST || faces == WEST;
            float a = eastOrWest ? CROSSING_SIGNAL_EW_A : CROSSING_SIGNAL_NS, b = eastOrWest ? CROSSING_SIGNAL_EW_B : CROSSING_SIGNAL_NS;
            return new Piece("crossing_signal", faces).at(a, b).dye(PAINT, a, b, CROSSING_SIGNAL_WHITE)
                    .also(1 << Models.PLASTIC, CROSSING_SIGNAL_YELLOW);
        }
        // A street name sign: its post and two blades crossed on top, the upper along y as its sprite paints it.
        if (called.equals("Streetname Sign")) {
            return new Piece("street_name_sign", EAST).at(0.5f, 0.53f).dye(PAINT, 0.5f, 0.25f, 0.77f);
        }
        // A parking meter's post stands back from the middle of its square, away from the way its face is turned.
        if (called.equals("Meter") && kind.equals("Parking") && faces != NONE) {
            return new Piece("parking_meter", faces).at(PARKING_METER_AT[2 * faces], PARKING_METER_AT[2 * faces + 1]);
        }
        // A flag pole is painted a storey at a time: its lower length on the ground, and its top with the flag hung limp from it on
        // the storey over, where the top length reaches down a little into the lower one.
        if (called.equals("Pole") && kind.equals("American Flag")) {
            return switch (Sheets.place(name, STREET)) {
                case 24 -> new Piece("flag_pole", 0).dye(1 << Models.METAL, FLAG_POLE_DYE);
                case 16 -> new Piece("flag_top", 0).dye(1 << Models.CLOTH, FLAG_BLUE_A, 0.5f, FLAG_BLUE_DYE + FLAG_TOP_SUNK).raise(-FLAG_TOP_SUNK);
                default -> null;
            };
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

    /** How far in from its square's edges a standing pipe is, and how high it is painted where its colour is taken, in levels. */
    static final float UPRIGHT_IN = 0.15f;
    private static final float UPRIGHT_DYE = 0.5f;
    /**
     * Per place on the sheet from 236 to 263: the corner of the square the pipe stands in (north, east, south and west, as
     * the corners of the game's view are), which model, and the quarter turns of its run along the floor; -1 for none.
     */
    private static final int[][] UPRIGHTS = {
        {236, NORTH, 1, SOUTH}, {237, NORTH, 1, EAST}, {238, NORTH, 0, 0}, {239, SOUTH, 0, 0}, {240, SOUTH, 1, NORTH}, {241, WEST, 1, NORTH},
        {242, SOUTH, 1, WEST}, {243, EAST, 0, 0}, {244, EAST, 1, WEST}, {245, EAST, 1, SOUTH}, {246, WEST, 1, EAST}, {247, WEST, 0, 0},
        {260, NORTH, 2, SOUTH}, {261, NORTH, 2, EAST}, {262, EAST, 2, SOUTH}, {263, WEST, 2, EAST},
    };

    /**
     * A pipe from the floor to the ceiling in a corner of its square: straight, or bent at its foot and running along the
     * floor to the square's edge, or bent and ending there. Its run turns the way the model's -Y is turned.
     */
    private static Piece pipeUpright(int place) {
        for (int[] upright : UPRIGHTS) {
            if (upright[0] != place) {
                continue;
            }
            int corner = upright[1];
            float a = corner == EAST || corner == SOUTH ? 1f - UPRIGHT_IN : UPRIGHT_IN, b = corner == SOUTH || corner == WEST ? 1f - UPRIGHT_IN : UPRIGHT_IN;
            String model = upright[2] == 0 ? "pipe_upright" : upright[2] == 1 ? "pipe_upright_run" : "pipe_upright_stub";
            return new Piece(model, upright[3]).at(a, b).dye(1 << Models.METAL, a, b, UPRIGHT_DYE);
        }
        return null;
    }

    /**
     * The sheet of what a works has. A length of duct runs along y on one sprite and along x on
     * the next. The end of a duct that turns down is the model's back, and an elbow's two ends
     * are the model's front and its +x side, which facing north is the west: so an elbow whose
     * ends are to the north and the west is not turned, and each quarter turn takes both ends
     * on the way north goes to east. A pipe lies along one edge of its square, and two that meet
     * lie along the two edges of one of its corners. A pipe that stands to the ceiling is in a
     * corner of its square: see pipeUpright.
     */
    private static Piece works(int place) {
        float far = 1f - PIPE_EDGE;
        Piece upright = pipeUpright(place);
        if (upright != null) {
            return upright;
        }
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
     * meet, and takes its colour from its sprite there. A post of chain link stands on the corner itself, where the fence's
     * lengths and their rails end (WallBuilder.addChainLink). Stood up as a card it turned to the eye, and its picture slid across
     * it as the eye went by.
     */
    private static Piece fencePost(String name) {
        return switch (Sheets.place(name, FENCES)) {
            case 7 -> new Piece("picket_post", 0).at(0.031f, 0.039f).dye(PAINT, 0.031f, 0.039f, 0.2f).fit();
            case 19 -> new Piece("rail_post", 0).at(0.133f, 0.102f).dye(1 << Models.WOOD, 0.133f, 0.102f, 0.2f).fit();
            case 29 -> new Piece("pipe_post", 0).at(0f, 0f).dye(PAINT, 0.062f, 0.094f, 0.2f).fit();
            case 61 -> new Piece("pipe_post_tall", 0).at(0f, 0f).dye(PAINT, 0.062f, 0.094f, 0.3f).fit();
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

    /** The sheet the game's television aerials are on, with its poles and wires. */
    private static final String AERIALS = "appliances_com_01_";
    /**
     * Where a television aerial's mast stands, across its pair of squares and along them, on the square of the pair at
     * the far end from the game's view: read off the two sprites drawn together. The aerial facing east is the one
     * facing south mirrored across the diagonal, as the game's view is.
     */
    private static final float AERIAL_ACROSS = 0.03f, AERIAL_ALONG = 0.66f;

    /**
     * A television aerial: two sprites on two squares, the mast and its yagis on one and the foot of its stay on the
     * other, south of the first for one that faces south (80 and 81) and east of it for one that faces east (82 and
     * 83). The square at the far end from the game's view stands the whole aerial, on the roof it is painted on.
     */
    private static Piece aerial(String name) {
        return switch (Sheets.place(name, AERIALS)) {
            case 81 -> new Piece("tv_aerial", SOUTH).at(AERIAL_ACROSS, AERIAL_ALONG).pairedWith(AERIALS + "80", 0, 1).each().onRoof();
            case 82 -> new Piece("tv_aerial", EAST).at(AERIAL_ALONG, AERIAL_ACROSS).pairedWith(AERIALS + "83", 1, 0).each().onRoof();
            case 80 -> following(new Piece(null, SOUTH).pairedWith(AERIALS + "81", 0, -1));
            case 83 -> following(new Piece(null, EAST).pairedWith(AERIALS + "82", -1, 0));
            default -> null;
        };
    }

    static Piece following(Piece piece) {
        piece.follows = true;
        return piece.each();
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
     * A porch post with a brace out to each side along the edge it stands on, which the game names nothing: 4 on the square's
     * west edge, 5 on its north, and 6 and 7 the same with a hanging scale out over the square. Their place and height were
     * read off the sprites: the post stands a storey high, braced up to the beam over it.
     */
    private static Piece forkedPost(String name) {
        return switch (Sheets.place(name, GREENES)) {
            case 4 -> new Piece("forked_post", EAST).at(FORKED_POST_IN, FORKED_POST_ALONG).dye(DARK_WOOD, FORKED_POST_IN, FORKED_POST_ALONG, FORKED_POST_DYE);
            case 5 -> new Piece("forked_post", SOUTH).at(FORKED_POST_ALONG, FORKED_POST_IN).dye(DARK_WOOD, FORKED_POST_ALONG, FORKED_POST_IN, FORKED_POST_DYE);
            case 6 -> new Piece("forked_post_scale", EAST).at(FORKED_POST_IN, FORKED_POST_ALONG).dye(DARK_WOOD, FORKED_POST_IN, FORKED_POST_ALONG, FORKED_POST_DYE);
            case 7 -> new Piece("forked_post_scale", SOUTH).at(FORKED_POST_ALONG, FORKED_POST_IN).dye(DARK_WOOD, FORKED_POST_ALONG, FORKED_POST_IN, FORKED_POST_DYE);
            default -> null;
        };
    }

    /**
     * A burnt fence's post that stands alone on its square, by its place on the sheet of burnt fences, or null. Each stands
     * where its sprite paints the middle of its foot, by the north-west corner where the fence's two edges meet, and takes
     * its colour from its sprite there. A pole of chain link stands on the corner itself, as the whole fence's do. Stood up as a card it turned to the eye.
     */
    private static Piece burntPost(String name) {
        return switch (Sheets.place(name, BURNT_FENCES)) {
            case 3 -> new Piece("burnt_picket_post", 0).at(0.06f, 0.06f).dye(1 << Models.DARK_WOOD, 0.06f, 0.06f, 0.2f);
            case 13 -> new Piece("burnt_tall_post", 0).at(0.1f, 0.1f).dye(1 << Models.DARK_WOOD, 0.1f, 0.1f, 0.4f);
            case 45 -> new Piece("burnt_rail_post", 0).at(0.07f, 0.07f).dye(1 << Models.DARK_WOOD, 0.07f, 0.07f, 0.25f);
            case 53 -> new Piece("fence_pole", 0).at(0f, 0f).dye(PAINT, 0.03f, 0.06f, 0.4f);
            case 61 -> new Piece("fence_pole_wide", 0).at(0.08f, 0.06f).dye(PAINT, 0.08f, 0.06f, 0.4f);
            case 69 -> new Piece("fence_pole_capped", 0).at(0f, 0f).dye(PAINT, 0.03f, 0.06f, 0.25f);
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

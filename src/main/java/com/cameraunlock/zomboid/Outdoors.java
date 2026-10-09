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
        /** Whether it is made no bigger than fits inside its squares, and not a metre to the tile so as to meet the next. */
        boolean fits;
        /** Whether each square of a piece the game lays on several is a piece of its own. */
        boolean each;

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

        Piece raise(float levels) {
            raised = levels * ViewCamera.LEVEL_HEIGHT;
            dyeHigh += levels;
            return this;
        }

        Piece each() {
            each = true;
            return this;
        }

        Piece fit() {
            fits = true;
            return this;
        }

    }

    /** The game's facings as numbers: the quarter turns of a model that faces that way. */
    static final int NONE = -1, NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;

    private static final String WORKS = "industry_02_", PLANT = "industry_01_", ROOFTOP = "rooftop_furniture_", BUILDING_SITE = "construction_01_", LAUNDRY = "appliances_laundry_01_",
            MILITARY_BARRIER = "fencing_01_96";
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
    /** How high on its sprite a stack of logs gives the colour of its bark, in levels. */
    private static final float LOG_DYE = 0.18f;
    /** Where on its square a stop sign's post is painted, for one that faces north, east, south and west. */
    private static final float[] STOP_SIGN_AT = {0.64f, 0.61f, 0.35f, 0.78f, 0.35f, 0.37f, 0.41f, 0.37f};

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
        boolean single = width == 1 && height == 1;
        if (called == null) {
            if (!single) {
                return null;
            }
            int works = place(name, WORKS);
            if (works >= 0) {
                return works(works);
            }
            int rooftop = place(name, ROOFTOP);
            if (rooftop >= 0) {
                return rooftop(rooftop);
            }
            // Roofing felt in rolls: two long rolls side by side and a banded bundle of short ones, each along y and then along x.
            return switch (place(name, PLANT)) {
                case 40, 42 -> new Piece("felt_bundle", 0);
                case 41 -> new Piece("felt_roll", 0);
                case 43, 45 -> new Piece("felt_bundle", 1);
                case 44 -> new Piece("felt_roll", 1);
                default -> switch (place(name, BUILDING_SITE)) {
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
        if (!single) {
            return null;
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
                    : kind.equals("Square") ? "gravestone_square" : null;
            if (stone == null) {
                return null;
            }
            float a = 0.5f + (faces == EAST ? STONE_FORWARD : faces == WEST ? -STONE_FORWARD : 0f);
            float b = 0.5f + (faces == SOUTH ? STONE_FORWARD : faces == NORTH ? -STONE_FORWARD : 0f);
            return new Piece(stone, faces).at(a, b).dye(PAINT, a, b, 0.16f);
        }
        return null;
    }

    /** Which way a length of washing line runs from over the middle of its square to over the middle of the next. */
    static final int NO_LINE = 0, LINE_EAST = 1, LINE_SOUTH = 2;
    /** How high over the land a washing line's two lines are, in tiles: it runs round a pulley at each end, one line over the other. */
    static final float[] LINES_UP = {1.93f, 2.26f};

    /** Whether a sprite is a square's length of washing line, and which way it runs: it is no model, the picture strings it as it does a wire. */
    static int washingLine(String name) {
        return switch (place(name, LAUNDRY)) {
            case 30 -> LINE_EAST;
            case 31 -> LINE_SOUTH;
            default -> NO_LINE;
        };
    }

    /** Which sprite of a sheet a name is, or -1 for a name that is not of the sheet. */
    private static int place(String name, String sheet) {
        if (!name.startsWith(sheet) || name.length() == sheet.length() || name.length() > sheet.length() + 3) {
            return -1;
        }
        int place = 0;
        for (int i = sheet.length(); i < name.length(); i++) {
            char digit = name.charAt(i);
            if (digit < '0' || digit > '9') {
                return -1;
            }
            place = place * 10 + digit - '0';
        }
        return place;
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

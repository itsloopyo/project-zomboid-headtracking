package com.cameraunlock.zomboid;

/**
 * What runs along a building's frame or hangs under its ceiling and has no name: the steel
 * columns and girders of a building going up, ducts and a fan hung from a works' ceiling,
 * the ducts that climb its walls, the wide pipes and their fittings laid along its floor, a
 * church's columns. Which model a sprite is, and where on its square it stands, by its place
 * on its sheet; the places were read off each sprite's solid paint (a member that runs a whole
 * square's length, or an upright standing on the floor, paints one outline only).
 *
 * And the sprites the game lays on a wall it does not name: a length of girder or a cell's
 * barred frame painted on one edge of its square while the game marks it for the other, or for
 * none, or for one where it is painted on both. Laid on the edge it is marked for, its picture
 * is outside the face and nothing of it shows; stood up as a card it turns with the eye.
 */
final class Overhead {
    private static final String BUILDING_SITE = "construction_01_", WORKS = "industry_02_", PLANT = "industry_01_",
            CHURCH = "location_community_church_small_01_", POLICE = "location_community_police_01_", TIMBER_FRAME = "carpentry_03_";
    private static final int PAINT = 1 << Models.PAINT, WOOD = 1 << Models.WOOD;

    /** How deep a girder of a building's frame is, in levels: the model's 0.245 metres. */
    private static final float GIRDER_DEEP = 0.245f / ViewCamera.LEVEL_HEIGHT;
    /** How far in from its square's edge a girder of the frame runs, and a column stands, in tiles. */
    private static final float GIRDER_IN = 0.11f, COLUMN_IN = 0.13f;
    /** How far in from its square's edge a frame of scaffolding stands, on the north and west edges and on the south and east, in tiles. */
    private static final float SCAFFOLD_NEAR = 0.06f, SCAFFOLD_FAR = 0.98f;
    /** How high a duct and a fan hung from a ceiling are, in levels: duct_run's 0.34 metres, fan_unit's 0.36. */
    private static final float DUCT_DEEP = 0.34f / ViewCamera.LEVEL_HEIGHT, FAN_DEEP = 0.36f / ViewCamera.LEVEL_HEIGHT;
    /** How high on its sprite a duct, a column and a wide pipe give their colour, in levels. */
    private static final float DUCT_DYE = 0.06f, COLUMN_DYE = 0.5f, PIPE_DYE = 0.06f;
    /** How deep a timber of a frame of sawn wood is, in levels: timber_corner's 0.12 metres. */
    private static final float TIMBER_DEEP = 0.12f / ViewCamera.LEVEL_HEIGHT;
    /** How far in from its square's corner a block of that timber stands, in tiles. */
    private static final float BLOCK_IN = 0.1f;
    /** How far in from each edge of its corner a timber post's middle is, half timber_post's 0.15 metres, and how high its wood gives its colour, in levels. */
    private static final float POST_IN = 0.075f, POST_DYE = 0.4f;

    /** Which edges of its square a sprite on a wall is laid on, where its picture and not the game's mark says. */
    static final int ANY_EDGE = 0, WEST_EDGE = 1, NORTH_EDGE = 2, BOTH_EDGES = 3;

    private Overhead() {}

    /** The model of a sprite with no name on a square of its own and how it stands, or null for one that is none of these. */
    static Outdoors.Piece of(String name) {
        int place = Sheets.place(name, BUILDING_SITE);
        if (place >= 0) {
            return buildingSite(place);
        }
        place = Sheets.place(name, WORKS);
        if (place >= 0) {
            return works(place);
        }
        place = Sheets.place(name, TIMBER_FRAME);
        if (place >= 0) {
            return timberFrame(place);
        }
        return switch (Sheets.place(name, CHURCH)) {
            case 79 -> new Outdoors.Piece("church_column", 0).dye(PAINT, COLUMN_DYE);
            case 87 -> new Outdoors.Piece("church_column_base", 0).dye(PAINT, COLUMN_DYE);
            default -> null;
        };
    }

    /**
     * A building's steel frame. A column stands in from each corner of its square, the same
     * column painted at the four. A girder runs a square's length along one edge, in from it:
     * up under the ceiling, its top a level up, or on the floor. Sprites 92, 93 and 106 to 108
     * are girders that stop short of their square's end and stay as they are.
     *
     * A frame of scaffolding stands along one edge of its square: both standards and three
     * ledgers on 16, 21, 29 and 24 (the north, south, west and east edges), one standard and a
     * zigzag brace on the rest, the standard at the south (17, 19) or north (18, 20) end of a
     * frame on the west edge, at the west (25, 27) or east (26, 28) end of one on the north.
     * The model's standard is at its back. 19, 20, 25 and 26 also paint a tube lying across the
     * floor from the frame, which the model leaves out. 22 and 23 are plank floors.
     */
    private static Outdoors.Piece buildingSite(int place) {
        float near = GIRDER_IN, far = 1f - GIRDER_IN;
        return switch (place) {
            // A girder lying on the ground, along y: 42 and 43 are the same along x.
            case 40, 41 -> new Outdoors.Piece("girder", 0).dye(PAINT, 0.11f);
            case 16 -> new Outdoors.Piece("scaffold_frame", Outdoors.EAST).at(0.5f, SCAFFOLD_NEAR);
            case 21 -> new Outdoors.Piece("scaffold_frame", Outdoors.EAST).at(0.5f, SCAFFOLD_FAR);
            case 29 -> new Outdoors.Piece("scaffold_frame", Outdoors.NORTH).at(SCAFFOLD_NEAR, 0.5f);
            case 24 -> new Outdoors.Piece("scaffold_frame", Outdoors.NORTH).at(SCAFFOLD_FAR, 0.5f);
            case 17, 19 -> new Outdoors.Piece("scaffold_braced", Outdoors.NORTH).at(SCAFFOLD_NEAR, 0.5f);
            case 18, 20 -> new Outdoors.Piece("scaffold_braced", Outdoors.SOUTH).at(SCAFFOLD_NEAR, 0.5f);
            case 25, 27 -> new Outdoors.Piece("scaffold_braced", Outdoors.EAST).at(0.5f, SCAFFOLD_NEAR);
            case 26, 28 -> new Outdoors.Piece("scaffold_braced", Outdoors.WEST).at(0.5f, SCAFFOLD_NEAR);
            case 48, 52, 82 -> column(1f - COLUMN_IN, 1f - COLUMN_IN);
            case 80, 84 -> column(COLUMN_IN, 1f - COLUMN_IN);
            case 81 -> column(1f - COLUMN_IN, COLUMN_IN);
            case 83, 87 -> column(COLUMN_IN, COLUMN_IN);
            case 104 -> girder(true, 0.5f, near, true);
            case 49, 50, 51, 105 -> girder(true, 0.5f, far, true);
            case 113 -> girder(false, near, 0.5f, true);
            case 53, 54, 55, 112 -> girder(false, far, 0.5f, true);
            case 88 -> girder(true, 0.5f, near, false);
            case 89 -> girder(true, 0.5f, far, false);
            case 97 -> girder(false, near, 0.5f, false);
            case 96 -> girder(false, far, 0.5f, false);
            default -> null;
        };
    }

    /**
     * A frame of sawn timber. Where two of its timbers meet at a corner of a square, the map
     * has one sprite of the two, along the square's two edges at that corner: up under the
     * ceiling (32 to 35) or on the floor (56 to 59), at the north-west, south-east, north-east
     * and south-west corners in the order of each four. A short block on end (52 to 55) stands
     * in from a corner on the floor, the north-west, south-east, north-east and south-west.
     * The straight timbers (16 to 19, 40 to 43) are drawn as the beams their outlines are,
     * which is what a sawn timber is; the posts as posts.
     *
     * A post a storey high (0 to 15) stands in a corner of its square, four to a corner: the
     * north-west, south-east, south-west and north-east in that order. The first of each four
     * is bare, the next two have a brace up to the beam along one of the corner's two edges,
     * and the last has one along each. Stood up as a card, the two braces were a V of boards
     * in one plane over a porch's corner (carpentry_03_7 at 10700, 9613).
     */
    private static Outdoors.Piece timberFrame(int place) {
        float near = BLOCK_IN, far = 1f - BLOCK_IN, in = POST_IN, out = 1f - POST_IN;
        return switch (place) {
            case 0 -> post("timber_post", Outdoors.NORTH, in, in);
            case 1 -> post("timber_post_brace", Outdoors.SOUTH, in, in);
            case 2 -> post("timber_post_brace", Outdoors.WEST, in, in);
            case 3 -> post("timber_post_braces", Outdoors.WEST, in, in);
            case 4 -> post("timber_post", Outdoors.NORTH, out, out);
            case 5 -> post("timber_post_brace", Outdoors.EAST, out, out);
            case 6 -> post("timber_post_brace", Outdoors.NORTH, out, out);
            case 7 -> post("timber_post_braces", Outdoors.EAST, out, out);
            case 8 -> post("timber_post", Outdoors.NORTH, in, out);
            case 9 -> post("timber_post_brace", Outdoors.SOUTH, in, out);
            case 10 -> post("timber_post_brace", Outdoors.EAST, in, out);
            case 11 -> post("timber_post_braces", Outdoors.SOUTH, in, out);
            case 12 -> post("timber_post", Outdoors.NORTH, out, in);
            case 13 -> post("timber_post_brace", Outdoors.WEST, out, in);
            case 14 -> post("timber_post_brace", Outdoors.NORTH, out, in);
            case 15 -> post("timber_post_braces", Outdoors.NORTH, out, in);
            case 32, 56 -> corner(Outdoors.NORTH, place < 40);
            case 34, 58 -> corner(Outdoors.EAST, place < 40);
            case 33, 57 -> corner(Outdoors.SOUTH, place < 40);
            case 35, 59 -> corner(Outdoors.WEST, place < 40);
            case 52 -> block(near, near);
            case 53 -> block(far, far);
            case 54 -> block(far, near);
            case 55 -> block(near, far);
            default -> null;
        };
    }

    /** Where on its square a corner of timbers gives its colour, halfway along one of them, for each of its turns. */
    private static final float[] CORNER_DYE = {0.5f, 0.1f, 0.9f, 0.5f, 0.5f, 0.9f, 0.1f, 0.5f};

    /** turns NORTH for the corner at the north-west, each quarter turn the next corner round to the east. */
    private static Outdoors.Piece corner(int turns, boolean hung) {
        Outdoors.Piece corner = new Outdoors.Piece("timber_corner", turns).dye(WOOD, CORNER_DYE[2 * turns], CORNER_DYE[2 * turns + 1], TIMBER_DEEP / 2f);
        return hung ? corner.raise(1f - TIMBER_DEEP) : corner;
    }

    /** A post with its model's braces along the model's x and y, turned to run along its corner's edges. */
    private static Outdoors.Piece post(String model, int turns, float a, float b) {
        return new Outdoors.Piece(model, turns).at(a, b).dye(WOOD, a, b, POST_DYE);
    }

    private static Outdoors.Piece block(float a, float b) {
        return new Outdoors.Piece("timber_block", 0).at(a, b).dye(WOOD, a, b, TIMBER_DEEP / 2f);
    }

    private static Outdoors.Piece column(float a, float b) {
        return new Outdoors.Piece("steel_column", 0).at(a, b).dye(PAINT, a, b, COLUMN_DYE);
    }

    /** A square's length of girder along x or along y through (a, b), under the ceiling or on the floor. */
    private static Outdoors.Piece girder(boolean alongX, float a, float b, boolean hung) {
        Outdoors.Piece girder = new Outdoors.Piece("steel_beam", alongX ? Outdoors.EAST : Outdoors.NORTH).at(a, b).dye(PAINT, a, b, GIRDER_DEEP / 2f);
        return hung ? girder.raise(1f - GIRDER_DEEP) : girder;
    }

    /**
     * A works' ducts and wide pipes. A duct hung from the ceiling runs through the middle of
     * its square, its top at the ceiling. A duct that climbs a wall stands a little out from
     * it, west, north, east and south in that order on the sheet, and 8 to 11 stop short of
     * the ceiling where 16 to 19 reach it. A wide pipe on the floor (32 and 33 are its straight
     * lengths) turns, branches or stops in the middle of its square, its open ends at the
     * square's edges: the elbow's ends are the model's front and +x side, the tee's run is
     * along x with its branch to the front, the end runs from the front to a cap.
     */
    private static Outdoors.Piece works(int place) {
        return switch (place) {
            case 4 -> hungDuct("duct_run", Outdoors.NORTH).at(0.43f, 0.5f);
            case 5 -> hungDuct("duct_run", Outdoors.EAST).at(0.5f, 0.46f);
            case 6 -> new Outdoors.Piece("fan_unit", 0).dye(PAINT, 0.5f, 0.5f, FAN_DEEP / 2f).raise(1f - FAN_DEEP);
            case 16 -> climbing("duct_up", 0.33f, 0.57f);
            case 17 -> climbing("duct_up", 0.53f, 0.34f);
            case 18 -> climbing("duct_up", 0.80f, 0.54f);
            case 19 -> climbing("duct_up", 0.50f, 0.81f);
            case 8 -> climbing("duct_up_short", 0.33f, 0.57f);
            case 9 -> climbing("duct_up_short", 0.55f, 0.36f);
            case 10 -> climbing("duct_up_short", 0.80f, 0.54f);
            case 11 -> climbing("duct_up_short", 0.50f, 0.81f);
            case 24 -> pipe("culvert_elbow", 1);
            case 25 -> pipe("culvert_elbow", 2);
            case 26 -> pipe("culvert_elbow", 3);
            case 27 -> pipe("culvert_elbow", 0);
            case 28 -> pipe("culvert_tee", 3);
            case 29 -> pipe("culvert_tee", 2);
            case 30 -> pipe("culvert_tee", 1);
            case 31 -> pipe("culvert_tee", 0);
            case 39 -> pipe("culvert_cross", 0);
            case 35 -> pipe("culvert_end", 0);
            case 37 -> pipe("culvert_end", 1);
            case 38 -> pipe("culvert_end", 2);
            case 36 -> pipe("culvert_end", 3);
            default -> null;
        };
    }

    private static Outdoors.Piece hungDuct(String model, int turns) {
        return new Outdoors.Piece(model, turns).dye(PAINT, DUCT_DEEP / 2f).raise(1f - DUCT_DEEP);
    }

    private static Outdoors.Piece climbing(String model, float a, float b) {
        return new Outdoors.Piece(model, 0).at(a, b).dye(PAINT, a, b, DUCT_DYE);
    }

    private static Outdoors.Piece pipe(String model, int turns) {
        return new Outdoors.Piece(model, turns).dye(PAINT, PIPE_DYE);
    }

    /**
     * Which edges a sprite the game lays on a wall goes on, where its picture says otherwise
     * than its marks, or ANY_EDGE to go by the marks. industry_01_30 is a girder painted along
     * the west edge and marked for the north. 27 and 28 are the two girders that meet at a
     * corner, marked for the north; 26, 31 and 36 are where two meet, marked for neither.
     * A cell's barred door frame (10 on the west edge, 11 on the north) and its corner of bars
     * (2) are marked for none.
     */
    static int edgeOf(String name) {
        int place = Sheets.place(name, PLANT);
        if (place >= 0) {
            return switch (place) {
                case 30 -> WEST_EDGE;
                case 26, 27, 28, 31, 36 -> BOTH_EDGES;
                default -> ANY_EDGE;
            };
        }
        return switch (Sheets.place(name, POLICE)) {
            case 10 -> WEST_EDGE;
            case 11 -> NORTH_EDGE;
            case 2 -> BOTH_EDGES;
            default -> ANY_EDGE;
        };
    }
}

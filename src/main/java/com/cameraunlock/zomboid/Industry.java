package com.cameraunlock.zomboid;

import com.cameraunlock.zomboid.Outdoors.Piece;

/**
 * What a works, a warehouse, a garage, a workshop and a distillery have that the mod has a model
 * of: belt conveyors and the machines a line of them runs through, fuel tanks lying on saddles,
 * pipes down from the ceiling, crates stacked on crates, racks of casks and the casks, workbenches,
 * tool chests, security desks, gas bottles, lathes, presses, drill presses and band saws. Most
 * sprites of a works and a workshop have no name and no facing: which piece each is, which way it
 * runs or faces and where on its square it stands go by its place on its sheet.
 */
final class Industry {
    private Industry() {}

    private static final String WORKS = "industry_02_", CRATES = "carpentry_01_", DISTILLERY = "location_business_distillery_01_", WORKSHOP = "industry_03_";
    private static final int PAINT = 1 << Models.PAINT;
    /** How high the game says the crate at the foot of a stack is, in the pixels of its sprite: the crates on it stand from there. */
    private static final int CRATE_FOOT = 32;
    /** How high up its own crate, in levels, a crate on another gives its colour. */
    private static final float CRATE_DYE = 0.25f;
    /** Where on its sprite a machine's painted case gives its colour, in levels. */
    private static final float CASE_DYE = 0.45f, SHELL_DYE = 0.3f, OUTLET_DYE = 0.5f, DRUM_DYE = 0.06f, COLUMN_DYE = 0.5f;
    /** Where on its sprite a tool chest and an extinguisher give their colour, in levels. */
    private static final float CHEST_DYE = 0.2f, BOTTLE_DYE = 0.1f;
    /**
     * How far over the floor a tank's underside is, in levels. A length of tank has nothing under
     * it, and a model stands on its lowest point: the length is raised to meet the ends, which
     * stand on their saddles.
     */
    private static final float TANK_FOOT = 0.2f / ViewCamera.LEVEL_HEIGHT;
    /**
     * Where on its square the game paints each of the four extinguishers that stand on the floor,
     * in the order 22, 23, 30, 31 of their sheet: the middle of the bottle's foot.
     */
    private static final float[] EXTINGUISHER_AT = {0.10f, 0.77f, 0.31f, 0.21f, 0.65f, 0.90f, 0.80f, 0.31f};
    /**
     * The lathes of a workshop's sheet, each three sprites in a row: the headstock's square, the
     * bed's and the tailstock's, which way the lathe faces, and the step along the row from the
     * tailstock's square to the headstock's. The headstock is at the operator's left. The same
     * twelve sprites painted blue follow them on the sheet, BLUE_LATHES on.
     */
    private static final int[][] LATHES = {
            {0, 1, 2, Outdoors.EAST, 0, 1},
            {3, 4, 5, Outdoors.SOUTH, -1, 0},
            {10, 9, 8, Outdoors.NORTH, 1, 0},
            {13, 12, 11, Outdoors.WEST, 0, -1}};
    private static final int BLUE_LATHES = 32;
    /** Where on its sprite a lathe's headstock gives the whole lathe its colour, in levels: the bed's sprite shows little paint. */
    private static final float LATHE_DYE = 0.6f;
    /**
     * The presses on the sheet: four sprites, one a way it faces, green and then blue, and the
     * same eight again with a die on the table. Where on each of the four its case gives the
     * colour, as (a, b, high in levels).
     */
    private static final int PRESSES = 48;
    private static final int[] PRESS_FACES = {Outdoors.WEST, Outdoors.NORTH, Outdoors.EAST, Outdoors.SOUTH};
    private static final float[] PRESS_DYE = {0.5f, 0.35f, 0.65f, 0.5f, 0.5f, 0.7f, 0.5f, 0.5f, 0.725f, 0.45f, 0.6f, 0.7f};

    /**
     * The model of a sprite and how it stands, or null for a sprite that is none of these. The
     * arguments are Outdoors.of's.
     */
    static Piece of(String name, String called, String group, int faces, int width, int height, int surface) {
        boolean single = width == 1 && height == 1;
        if (called == null) {
            if (!single) {
                return null;
            }
            int works = Sheets.place(name, WORKS);
            if (works >= 0) {
                return works(works);
            }
            int workshop = Sheets.place(name, WORKSHOP);
            if (workshop >= 0) {
                return workshop(workshop);
            }
            int distillery = Sheets.place(name, DISTILLERY);
            if (distillery >= 0) {
                return distillery(distillery);
            }
            // A crate on a crate, or a third on two: the game paints each where the one under it leaves off.
            int crate = Sheets.place(name, CRATES);
            if ((crate == 17 || crate == 18 || crate == 20 || crate == 21) && surface > CRATE_FOOT) {
                return new Piece("crate", 0).dye(1 << Models.WOOD, CRATE_DYE).fit().raise((surface - CRATE_FOOT) / WorldScene.SURFACE_PIXELS_PER_LEVEL);
            }
            return null;
        }
        String kind = group == null ? "" : group;
        boolean alongY = faces == Outdoors.NORTH || faces == Outdoors.SOUTH;
        int along = alongY ? height : width, across = alongY ? width : height;
        if (called.equals("Workbench") && kind.equals("Wood Top") && faces != Outdoors.NONE && along == 1 && across == 2) {
            return new Piece("workbench", faces);
        }
        if (!single) {
            return null;
        }
        if (called.equals("Tool Cabinet") && kind.equals("Red Mobile") && faces != Outdoors.NONE) {
            return new Piece("tool_chest", faces).dye(PAINT, CHEST_DYE).fit();
        }
        if (called.equals("Terminal") && kind.equals("Security") && faces != Outdoors.NONE) {
            return new Piece("security_desk", faces).fit();
        }
        if (called.equals("Propane Tank")) {
            return new Piece("propane_tank", 0).fit();
        }
        if (called.equals("Extinguisher")) {
            int which = switch (Sheets.place(name, WORKSHOP)) {
                case 22 -> 0;
                case 23 -> 1;
                case 30 -> 2;
                case 31 -> 3;
                default -> -1;
            };
            if (which < 0) {
                return null;
            }
            float a = EXTINGUISHER_AT[2 * which], b = EXTINGUISHER_AT[2 * which + 1];
            return new Piece("extinguisher_bottle", 0).at(a, b).dye(PAINT, a, b, BOTTLE_DYE).fit();
        }
        return null;
    }

    /**
     * The sheet of a works. A conveyor runs along y on the sprites where its legs are at the
     * south or north end and along x on those where they are at the east or west end, and the
     * model's legs are at its back. A tank lies on two rows of squares, the near row painting its
     * side and the far one its top, and each square stands its own half of a length of it: the
     * model's half is to its +Y and its head, on an end square, to +X or, mirrored, to -X.
     */
    private static Piece works(int place) {
        return switch (place) {
            case 40 -> new Piece("conveyor", Outdoors.WEST);
            case 41 -> new Piece("conveyor", Outdoors.NORTH);
            case 42 -> new Piece("conveyor", Outdoors.EAST);
            case 43 -> new Piece("conveyor", Outdoors.SOUTH);
            case 44 -> new Piece("conveyor_boxed", Outdoors.NORTH);
            case 45 -> new Piece("conveyor_boxed", Outdoors.EAST);
            case 54 -> new Piece("machine_case", Outdoors.EAST).dye(PAINT, CASE_DYE);
            case 55 -> new Piece("machine_case", Outdoors.NORTH).dye(PAINT, CASE_DYE);
            // A machine two squares long: the closed half, and the half the belt comes out of under its hood.
            case 48 -> new Piece("machine_shell", Outdoors.EAST).dye(PAINT, 0.5f, 0.75f, SHELL_DYE);
            case 49 -> new Piece("machine_outlet", Outdoors.EAST).dye(PAINT, 0.25f, 0.5f, OUTLET_DYE);
            case 51 -> new Piece("machine_shell", Outdoors.SOUTH).dye(PAINT, 0.75f, 0.5f, SHELL_DYE);
            case 50 -> new Piece("machine_outlet", Outdoors.SOUTH).dye(PAINT, 0.5f, 0.25f, OUTLET_DYE);
            case 52 -> new Piece("machine_drum", Outdoors.NORTH).dye(PAINT, DRUM_DYE);
            case 53 -> new Piece("machine_drum", Outdoors.EAST).dye(PAINT, DRUM_DYE);
            case 62 -> new Piece("machine_column", Outdoors.EAST).dye(PAINT, COLUMN_DYE);
            case 63 -> new Piece("machine_column", Outdoors.SOUTH).dye(PAINT, COLUMN_DYE);
            // A press over a line's belt, which runs along y on 46 and along x on 47.
            case 46 -> new Piece("line_press", Outdoors.NORTH).dye(PAINT, 0.5f, 0.45f, 0.75f);
            case 47 -> new Piece("line_press", Outdoors.EAST).dye(PAINT, 0.25f, 0.5f, 0.675f);
            // Band saws, their throat to the operator's right, and drill presses.
            case 264 -> new Piece("band_saw", Outdoors.SOUTH).dye(PAINT, 0.6f);
            case 265 -> new Piece("band_saw", Outdoors.WEST).dye(PAINT, 0.55f);
            case 266 -> new Piece("band_saw", Outdoors.NORTH).dye(PAINT, 0.25f);
            case 267 -> new Piece("band_saw", Outdoors.EAST).dye(PAINT, 0.475f);
            case 268 -> new Piece("drill_press", Outdoors.EAST).dye(PAINT, 0.525f, 0.5f, 0.625f);
            case 269 -> new Piece("drill_press", Outdoors.SOUTH).dye(PAINT, 0.55f, 0.45f, 0.635f);
            case 270 -> new Piece("drill_press", Outdoors.NORTH).dye(PAINT, 0.525f, 0.25f, 0.625f);
            case 271 -> new Piece("drill_press", Outdoors.WEST).dye(PAINT, 0.45f, 0.525f, 0.6375f);
            case 76 -> new Piece("downpipe", Outdoors.EAST);
            case 77 -> new Piece("downpipe", Outdoors.SOUTH);
            case 78 -> new Piece("downpipe", Outdoors.NORTH);
            case 79 -> new Piece("downpipe", Outdoors.WEST);
            // Tanks lying along x, the near row (64..67, 280..283) and the far one (68..71, 284..287), west to east.
            case 64, 280 -> new Piece("tank_end", 0).at(0.5f, 0f);
            case 65, 66, 281, 282 -> new Piece("tank_length", 0).at(0.5f, 0f).raise(TANK_FOOT);
            case 67, 283 -> new Piece("tank_end_mirrored", 0).at(0.5f, 0f);
            case 68, 284 -> new Piece("tank_end_mirrored", 2).at(0.5f, 1f);
            case 69, 70, 285, 286 -> new Piece("tank_length", 2).at(0.5f, 1f).raise(TANK_FOOT);
            case 71, 287 -> new Piece("tank_end", 2).at(0.5f, 1f);
            // A tank lying along y: the far column (296..299) and the near one (300..303), south to north.
            case 296 -> new Piece("tank_end_mirrored", 1).at(1f, 0.5f);
            case 297, 298 -> new Piece("tank_length", 1).at(1f, 0.5f).raise(TANK_FOOT);
            case 299 -> new Piece("tank_end", 1).at(1f, 0.5f);
            case 300 -> new Piece("tank_end", 3).at(0f, 0.5f);
            case 301, 302 -> new Piece("tank_length", 3).at(0f, 0.5f).raise(TANK_FOOT);
            case 303 -> new Piece("tank_end_mirrored", 3).at(0f, 0.5f);
            default -> null;
        };
    }

    /**
     * The sheet of a workshop: its lathes and presses. The bed's square of a lathe stands the lathe
     * but for the headstock and the pedestal under it, which the headstock's square stands, and
     * the tailstock's square stands nothing of its own. The three are a row, which stands only
     * whole and coloured from the headstock's sprite: the bed's paints too little of the paint.
     */
    private static Piece workshop(int place) {
        if (place >= PRESSES && place < PRESSES + 16) {
            int way = (place - PRESSES) % 4;
            return new Piece(place - PRESSES >= 8 ? "press_loaded" : "press", PRESS_FACES[way])
                    .dye(PAINT, PRESS_DYE[3 * way], PRESS_DYE[3 * way + 1], PRESS_DYE[3 * way + 2]);
        }
        int colour = place >= BLUE_LATHES ? BLUE_LATHES : 0;
        for (int[] lathe : LATHES) {
            String head = WORKSHOP + (colour + lathe[0]), bed = WORKSHOP + (colour + lathe[1]), tail = WORKSHOP + (colour + lathe[2]);
            int turns = lathe[3], x = lathe[4], y = lathe[5];
            if (place - colour == lathe[0]) {
                return new Piece("lathe_head", turns).inRow(bed, -x, -y, tail, -2 * x, -2 * y, -1).dye(PAINT, LATHE_DYE);
            }
            if (place - colour == lathe[1]) {
                return new Piece("lathe", turns).inRow(head, x, y, tail, -x, -y, 0).dye(PAINT, LATHE_DYE);
            }
            if (place - colour == lathe[2]) {
                return Outdoors.following(new Piece(null, turns).inRow(head, 2 * x, 2 * y, bed, x, y, 0).dye(PAINT, LATHE_DYE));
            }
        }
        return null;
    }

    /**
     * The racks and casks of a distillery. A rack's casks lie along x on the sprites that show
     * their heads on the east side and along y on those that show them on the south.
     */
    private static Piece distillery(int place) {
        return switch (place) {
            case 0, 5 -> new Piece("barrel_rack", 0).fit();
            case 1 -> new Piece("barrel_rack_full", 0).fit();
            case 4 -> new Piece("barrel_rack_full", 1).fit();
            case 2 -> new Piece("barrel_rack_low", 0).fit();
            case 3 -> new Piece("barrel_rack_low", 1).fit();
            case 8 -> new Piece("cask", 0).fit();
            case 9 -> new Piece("cask_lying", 0).fit();
            case 10 -> new Piece("cask_lying", 1).fit();
            default -> null;
        };
    }
}

package com.cameraunlock.zomboid;

import com.cameraunlock.zomboid.Outdoors.Piece;

/**
 * What a works, a warehouse, a garage and a distillery have that the mod has a model of: belt
 * conveyors and the machines a line of them runs through, fuel tanks lying on saddles, pipes down
 * from the ceiling, crates stacked on crates, racks of casks and the casks, workbenches, tool chests,
 * security desks and gas bottles. Most of the sheet of a works has no names, and which piece a
 * sprite is, which way it runs and where on its square it stands were read off its picture and off
 * the squares the map puts it on beside the others of its line.
 */
final class Industry {
    private Industry() {}

    private static final String WORKS = "industry_02_", CRATES = "carpentry_01_", DISTILLERY = "location_business_distillery_01_", EXTINGUISHERS = "industry_03_";
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
            int which = switch (Sheets.place(name, EXTINGUISHERS)) {
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

package com.cameraunlock.zomboid;

/**
 * What a camp, a survivor's workshop and an army post have that the mod has a model for: tents,
 * sleeping bags, a tarp on poles, beds and chairs knocked together from planks, fires in rings
 * of stones, buckets, barrels, crates, drying racks, a shot-up steel target.
 *
 * Which way each stands was read off the game's sprites of it. The sheets of tents and
 * sleeping bags give a facing at a right angle to the way their sprites have the door and the
 * head: a big tent the game says faces east has its door to the south, a sleeping bag that
 * faces north has its head to the west.
 */
final class Camp {
    private Camp() {}

    private static final int CLOTH = 1 << Models.CLOTH, WOOD = 1 << Models.WOOD, PAINT = 1 << Models.PAINT;
    private static final String BIG_TENTS = "camping_04_", CAMPING = "camping_01_", BAGS = "camping_02_", SHELTERS = "camping_03_", CARPENTRY = "carpentry_01_",
            BUILT = "carpentry_02_", CRAFTED = "crafted_01_", WORKSHOP = "crafted_05_", ARMY = "location_military_generic_01_", MADE = "constructedobjects_01_";
    /** How high a crate is, in levels: the game stacks them at this pitch, and a crate that is not on the floor is painted that much higher for each under it. */
    private static final float CRATE_HIGH = 32f / WorldScene.SURFACE_PIXELS_PER_LEVEL;
    /** Where on its sprite a crate gives the colour of its boards, a share of its height up. */
    private static final float CRATE_DYE = 0.75f;
    /** How high on their sprites a tent's fly, a sleeping bag's cover, a tarp, a drum, a ring of stones and a bucket under its handle give their colour, in levels. */
    private static final float FLY_DYE = 0.2f, BAG_DYE = 0.02f, TARP_DYE = 0.25f, DRUM_DYE = 0.15f, STONE_DYE = 0.02f, BUCKET_DYE = 0.06f;
    /** Where on its square a stone in the near side of a ring of them is. */
    private static final float NEAR_STONE = 0.72f;

    /** The piece, or null for a sprite that is none of these. The arguments are Outdoors.of's. */
    static Outdoors.Piece of(String name, String called, String group, int faces, int width, int height, int surface) {
        boolean single = width == 1 && height == 1;
        float surfaceHigh = surface / WorldScene.SURFACE_PIXELS_PER_LEVEL;
        if (called == null) {
            return single ? unnamed(name, surfaceHigh) : null;
        }
        String kind = group == null ? "" : group;
        boolean faced = faces != Outdoors.NONE;
        if (called.equals("Tent") && faced && width * height == 8 && Sheets.place(name, BIG_TENTS) >= 0) {
            return new Outdoors.Piece("ridge_tent", 3 - faces).dyeOn(1, 1).dye(CLOTH, FLY_DYE).fit();
        }
        if (called.equals("Tent") && kind.equals("Small") && faced && width * height == 2 && Sheets.place(name, CAMPING) >= 0) {
            return new Outdoors.Piece("pup_tent", width == 1 ? 0 : 1).dye(CLOTH, FLY_DYE).fit();
        }
        // A sleeping bag gives its colour on the square of its foot: the square of its head shows its lining.
        if (called.endsWith("Sleeping Bag") && faced && width * height == 2 && Sheets.place(name, BAGS) >= 0) {
            int foot = faces == Outdoors.NORTH ? 1 : 0;
            return new Outdoors.Piece("sleeping_bag", 1 - faces & 3).dyeOn(foot, faces == Outdoors.WEST ? 1 : 0).dye(CLOTH, BAG_DYE).fit();
        }
        if (called.equals("Shelter") && kind.equals("Tarp") && faced && width * height == 2 && Sheets.place(name, SHELTERS) >= 0) {
            return new Outdoors.Piece("tarp_shelter", faces).dye(CLOTH, TARP_DYE).fit();
        }
        if (called.equals("Mattress") && kind.equals("Makeshift") && faced && width * height == 2 && Sheets.place(name, BUILT) >= 0) {
            return new Outdoors.Piece("camp_mattress", faces).fit();
        }
        if (called.equals("Bed") && kind.equals("Crafted") && faced && width * height == 2 && Sheets.place(name, BUILT) >= 0) {
            return new Outdoors.Piece("plank_bed", faces).fit();
        }
        if (called.equals("Table") && kind.equals("Pottery") && faced && width * height == 2 && Sheets.place(name, CRAFTED) >= 0) {
            return new Outdoors.Piece("cloth_table", width == 1 ? 0 : 1).fit();
        }
        if (called.equals("Drying Rack") && kind.equals("Large") && faced && width == 2 && height == 2 && Sheets.place(name, WORKSHOP) >= 0) {
            return new Outdoors.Piece("drying_rack", faces & 1).fit();
        }
        if (!single) {
            return null;
        }
        if (called.equals("Chair") && faced && Sheets.place(name, CARPENTRY) >= 0 && surface > 0) {
            String chair = kind.equals("Exceptionally crafted") ? "slat_chair" : kind.equals("Poor quality") ? "plank_chair" : kind.equals("Makeshift") ? "box_chair" : null;
            return chair == null ? null : new Outdoors.Piece(chair, faces).dye(WOOD, surfaceHigh).fit();
        }
        if ((called.equals("Exceptional Table") || called.equals("Poor Quality Table") || called.equals("Shoddy Table")) && Sheets.place(name, CARPENTRY) >= 0) {
            return new Outdoors.Piece("plank_table", 0).dye(WOOD, surfaceHigh).fit();
        }
        if (called.equals("Table") && faced && (kind.equals("Shoddy") || kind.equals("Medium") || kind.equals("Quality")) && Sheets.place(name, BUILT) >= 0) {
            return new Outdoors.Piece("plank_side_table", faces).dye(WOOD, surfaceHigh).fit();
        }
        if (called.equals("Shelves") && kind.equals("Crafted Wooden") && faced && Sheets.place(name, BUILT) >= 0) {
            // Against the edge of its square behind it.
            float back = 0.24f;
            float a = faces == Outdoors.EAST ? back : faces == Outdoors.WEST ? 1f - back : 0.5f;
            float b = faces == Outdoors.SOUTH ? back : faces == Outdoors.NORTH ? 1f - back : 0.5f;
            return new Outdoors.Piece("plank_shelves", faces).at(a, b).dye(WOOD, a, b, surfaceHigh * 0.5f);
        }
        // A lit fire is the game's sprite with its flames; the cold one is the model.
        if (called.equals("Campfire") && name.equals(CAMPING + "6")) {
            return new Outdoors.Piece("campfire", 0).dye(PAINT, NEAR_STONE, NEAR_STONE, STONE_DYE);
        }
        if (called.equals("Cooking Pit") && name.equals(SHELTERS + "19")) {
            return new Outdoors.Piece("cooking_pit", 0).dye(PAINT, 0.5f, 0.5f, surfaceHigh * 0.5f).fit();
        }
        if (called.equals("Cooking Pit") && kind.equals("Simple") && name.equals(SHELTERS + "16")) {
            return new Outdoors.Piece("cooking_pit_simple", 0).dye(PAINT, NEAR_STONE, NEAR_STONE, STONE_DYE);
        }
        if (called.equals("Burn Barrel") && Sheets.place(name, WORKSHOP) >= 0 && Sheets.place(name, WORKSHOP) != 62 && Sheets.place(name, WORKSHOP) != 63) {
            return new Outdoors.Piece("burn_barrel", 0).dye(PAINT, DRUM_DYE);
        }
        // An army crate on the floor is Furniture's, and is the model this stands on it.
        if (called.equals("Crate") && (kind.equals("Military") && Sheets.place(name, ARMY) >= 0 && surfaceHigh > CRATE_HIGH
                || (kind.equals("Metal") && Sheets.place(name, MADE) >= 0 || kind.equals("Fancy Metal") && Sheets.place(name, WORKSHOP) >= 0) && surface > 0)) {
            return crate(surfaceHigh);
        }
        if (called.equals("Candle") && name.equals(MADE + "0")) {
            return new Outdoors.Piece("candle", 0);
        }
        return null;
    }

    /** A crate as the game stacks them, as high as the one under it says, the model Furniture stands under it. */
    private static Outdoors.Piece crate(float topHigh) {
        float below = topHigh - CRATE_HIGH;
        if (below < -0.01f) {
            return null;
        }
        return new Outdoors.Piece("crate", 0).dye(WOOD, CRATE_HIGH * CRATE_DYE).fitTo(CRATE_HIGH * ViewCamera.LEVEL_HEIGHT).raise(Math.max(below, 0f));
    }

    /** What the game gives no name: by its place on its sheet. */
    private static Outdoors.Piece unnamed(String name, float surfaceHigh) {
        int built = Sheets.place(name, BUILT);
        // Two posts of a wall's frame, a storey high, along the north edge of the square or along its west edge.
        if (built == 95) {
            return new Outdoors.Piece("frame_posts", 0).at(0.51f, 0.12f);
        }
        if (built == 94) {
            return new Outdoors.Piece("frame_posts", 1).at(0.12f, 0.51f);
        }
        if (built == 105 || built == 106) {
            return crate(surfaceHigh);
        }
        int workshop = Sheets.place(name, WORKSHOP);
        if (workshop == 45 || workshop == 46) {
            return crate(surfaceHigh);
        }
        if (workshop == 8) {
            return new Outdoors.Piece("wooden_barrel", 0).dye(WOOD, 0.2f);
        }
        int crafted = Sheets.place(name, CRAFTED);
        // The game names the buckets to quench hot iron in only by their group.
        if (workshop == 54 || workshop == 55 || crafted == 38 || crafted == 39 || crafted == 54 || crafted == 55) {
            return new Outdoors.Piece("wooden_bucket", 0).dye(WOOD, BUCKET_DYE);
        }
        if (Sheets.place(name, CAMPING) == 16) {
            return new Outdoors.Piece("stone_well", 0).dye(PAINT, 0.75f, 0.75f, 0.15f);
        }
        // A cage trap, long along x or along y, shut or set with its door up: the door is at its west end, and its north end.
        switch (Sheets.place(name, MADE)) {
            case 8:
                return new Outdoors.Piece("live_trap_set", Outdoors.WEST);
            case 9:
                return new Outdoors.Piece("live_trap", Outdoors.EAST);
            case 10:
                return new Outdoors.Piece("live_trap", Outdoors.NORTH);
            case 11:
                return new Outdoors.Piece("live_trap_set", Outdoors.NORTH);
            default:
                break;
        }
        // A steel plate to shoot at on a frame of timber, its legs behind it. The sprites have
        // the plate in the middle of the square facing north, at its south edge facing south,
        // at its west edge facing west and its east edge facing east.
        return switch (Sheets.place(name, ARMY)) {
            case 64 -> new Outdoors.Piece("steel_target", Outdoors.WEST).at(0.04f, 0.5f);
            case 65 -> new Outdoors.Piece("steel_target", Outdoors.NORTH).at(0.5f, 0.52f);
            case 66 -> new Outdoors.Piece("steel_target", Outdoors.SOUTH).at(0.5f, 0.95f);
            case 67 -> new Outdoors.Piece("steel_target", Outdoors.EAST).at(0.93f, 0.5f);
            default -> null;
        };
    }
}

package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoDirections;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;

/**
 * What stands in rooms and hangs on their walls that Furniture has a model for besides the
 * pieces it tells itself: a kitchen's hood, bins, vents and steel counters, a bathroom's
 * fittings, an office's cabinets and machines, and the furniture the map has most of that
 * was still a sprite wrapped on a box or stood up as a card. Furniture.recognise asks here
 * first, so nothing here may be a piece it tells by the same signs.
 */
final class Indoors {
    private Indoors() {}

    /** How high over the floor the lower edge of a hood over a cooker is, in tiles: a forearm over the back of the hob. */
    private static final float HOOD_FOOT = 1.58f;
    /** How high the shelf of a coat rack is, in tiles, and where on its sprite, in levels, the shelf gives its colour. */
    private static final float COAT_SHELF = 1.45f, COAT_DYE = 0.58f;
    /** The highest the top of a cupboard with a mirror for a door is hung, in tiles: the game gives some a height over a head. */
    private static final float MIRROR_TOP = 1.95f;
    /** How high, in metres, the part of a model is that the game gives the height of. */
    private static final float BASIN_RIM = 0.87f, CHEST_TOP = 0.88f, BAR_SEAT = 0.75f, DRYER_TOP = 0.92f, BENCH_SEAT = 0.45f, PEW_SEAT = 0.44f, DESK_TOP = 0.74f;
    /** Where on its sprite, in levels, a thing gives its colour: the side of a box, a can or a cabinet, the foot of a lamp, a bottle of water. */
    private static final float BOX_DYE = 0.08f, CAN_DYE = 0.2f, FOOT_DYE = 0.01f, BOTTLE_DYE = 0.45f;
    private static final String VENTS = "rooftop_furniture_", FLOOR_LAMPS = "lighting_indoor_01_", BEDDING = "furniture_bedding";

    /** The number a sprite's name ends in, its place on its sheet, or -1. */
    private static int place(String name) {
        String last = name.substring(name.lastIndexOf('_') + 1);
        return !last.isEmpty() && last.length() <= 4 && last.chars().allMatch(Character::isDigit) ? Integer.parseInt(last) : -1;
    }

    /** A piece told with no need of the way it faces, or one that hangs on the wall behind it. Null for anything else. */
    static Furniture anyWay(IsoSprite sprite, String name, String called, String group, String holds, PropertyContainer properties,
            IsoDirections facing, boolean blocks) {
        boolean single = sprite.getSpriteGrid() == null;
        int surface = properties.getSurface();
        boolean onTable = properties.has("IsTableTop");
        if (called.equals("Cartbox") && holds.equals("cardboardbox") && single) {
            // The game gives a box no front. Its four sprites are the same box turned, and so is the model.
            return new Furniture(sprite, "carton", place(name) & 3).dye(1 << Models.PAINT, BOX_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Green Garbage Bin") && holds.equals("bin") && single) {
            return new Furniture(sprite, "garbage_can", 0).dye(1 << Models.PAINT, CAN_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Round Bin") && holds.equals("bin") && single) {
            return new Furniture(sprite, "waste_basket", 0).stand(0f, 0f, false);
        }
        if (called.equals("Lights") && group.startsWith("Lamp") && single && facing == null && name.startsWith(FLOOR_LAMPS)) {
            // Three lamps under one name: two uplighters, then the one with a shade.
            return new Furniture(sprite, place(name) == 58 ? "standard_lamp" : "torchiere", 0).dye(1 << Models.PAINT, FOOT_DYE).stand(0f, 0f, false);
        }
        if (facing == null || blocks || !holds.isEmpty() || onTable || surface != 0 || !properties.has("IsHigh") && !properties.has("IsLow")) {
            return null;
        }
        // The game gives a facing as one of the four ways along the map's axes.
        int faces = facing == IsoDirections.N ? 0 : facing == IsoDirections.E ? 1 : facing == IsoDirections.S ? 2 : 3;
        int wall = WallHung.behind(facing);
        if (called.equals("Conditioner") && group.equals("Air") && single && name.startsWith(VENTS)) {
            // The grilles of the sheet, by their place on it: dark louvres, white slotted plates high and low, round ones.
            int which = place(name);
            String vent = which == 17 || which == 18 ? "wall_vent" : which >= 32 && which <= 35 ? "wall_grille" : which == 36 || which == 37 ? "round_vent" : null;
            return vent == null ? null : new Furniture(sprite, vent, faces).hang(wall, false);
        }
        if (called.equals("Sign") && group.equals("Exit") && single) {
            return new Furniture(sprite, "exit_sign", faces).hang(wall, false);
        }
        if (called.equals("Wall Light") && group.endsWith("Fluorescent") || called.equals("Wall Lamp") && group.equals("Bar") && single) {
            return new Furniture(sprite, "strip_light", faces).hang(wall, false);
        }
        if (called.equals("Wall Lamp") && !group.endsWith("Half Sphere") && single) {
            return new Furniture(sprite, "sconce", faces).hang(wall, false).ownSize();
        }
        if (called.equals("Blower") && group.equals("Air") && single) {
            return new Furniture(sprite, "hand_dryer", faces).hang(wall, false).ownSize();
        }
        return null;
    }

    /**
     * A piece that stands the way it faces.
     *
     * @param along   how many squares the piece is long the way it faces
     * @param across  and across that
     */
    static Furniture facing(IsoSprite sprite, String name, String called, String group, String holds, PropertyContainer properties,
            int turns, int along, int across) {
        boolean single = along == 1 && across == 1, pair = along == 1 && across == 2;
        int surface = properties.getSurface();
        float surfaceHigh = surface / WorldScene.SURFACE_PIXELS_PER_LEVEL, surfaceUp = surfaceHigh * ViewCamera.LEVEL_HEIGHT;
        boolean low = properties.has("IsLow"), high = properties.has("IsHigh"), onTable = properties.has("IsTableTop");
        boolean counterHigh = surface >= 30 && surface <= 40;
        boolean rests = properties.has(IsoFlagType.bed);
        if (called.equals("Hood") && single && high) {
            return new Furniture(sprite, "range_hood", turns).stand(0f, 0f, true).raise(HOOD_FOOT);
        }
        if (called.equals("Counter") && single && group.contains("Steel") && low && properties.isTable() && holds.equals("counter") && counterHigh) {
            // Steel all over: the wooden counter's model dyed grey showed wood's grain through the grey.
            boolean corner = group.contains("Corner");
            Furniture counter = new Furniture(sprite, corner ? "steel_counter_corner" : "steel_counter", turns);
            if (!corner) {
                counter.withBasin = Models.get("steel_counter_sink");
            }
            return counter.meet(surfaceUp, counter.model.height(), !corner);
        }
        // A bath two squares long the way it faces: its taps are at its back, which is the model's end.
        if (called.equals("Bath") && along == 2 && across == 1) {
            return new Furniture(sprite, "bath", turns + 3 & 3).stand(0f, 0f, false);
        }
        if (called.equals("Sink") && !onTable && single && group.endsWith("Standing") && surface > 0) {
            return new Furniture(sprite, "pedestal_sink", turns).stand(surfaceUp, BASIN_RIM, true);
        }
        if (called.equals("Cabinet") && holds.equals("medicine") && single && high && surface > 0) {
            Furniture cabinet = new Furniture(sprite, "medicine_cabinet", turns).stand(0f, 0f, true);
            return cabinet.raise(Math.min(surfaceUp, MIRROR_TOP) - cabinet.model.height());
        }
        if (called.equals("Shelves") && holds.equals("metal_shelves") && single && high && surface >= 50) {
            Furniture shelf = new Furniture(sprite, surface >= 60 ? "wire_wall_shelves" : "wire_wall_shelf", turns).stand(0f, 0f, true);
            return shelf.raise(surfaceUp - shelf.model.height());
        }
        if (called.equals("Rack") && group.equals("Coat") && single && high) {
            Furniture rack = new Furniture(sprite, "coat_rack", turns).dyeAtBack(1 << Models.WOOD, COAT_DYE).stand(0f, 0f, true);
            return rack.raise(COAT_SHELF - rack.model.height());
        }
        if (called.equals("Rack") && holds.equals("clothingrack") && pair) {
            return new Furniture(sprite, "clothes_rail", turns).stand(0f, 0f, false);
        }
        if (called.equals("Cabinet") && holds.equals("filingcabinet") && single && low) {
            return new Furniture(sprite, "filing_cabinet", turns).dye(1 << Models.PAINT, CAN_DYE).stand(0f, 0f, true);
        }
        if (called.equals("Box") && holds.equals("smallbox") && single) {
            return new Furniture(sprite, "carton_small", turns).dye(1 << Models.PAINT, BOX_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Computer") && onTable && single && surface > 0) {
            return new Furniture(sprite, "computer", turns).stand(0f, 0f, false).raise(surfaceUp);
        }
        if (called.equals("Dispenser") && group.equals("Water") && single) {
            return new Furniture(sprite, "water_cooler", turns).dye(1 << Models.PAINT, BOTTLE_DYE).stand(0f, 0f, true);
        }
        if (called.equals("Drawers") && holds.equals("dresser") && group.contains("Mirror") && single && surface > 0) {
            return new Furniture(sprite, "dresser_mirror", turns).dye(1 << Models.WOOD, surfaceHigh).stand(surfaceUp, CHEST_TOP, true);
        }
        if (holds.equals("sidetable") && single && properties.isTable() && surface > 0 && surface <= 22 && (called.equals("Table") || called.equals("Low Table"))) {
            Furniture table = new Furniture(sprite, called.equals("Table") ? "side_table" : "media_stand", turns).dye(1 << Models.WOOD, surfaceHigh);
            return table.stand(surfaceUp, table.model.height(), false);
        }
        if (called.equals("Bar Stool") && single && rests && surface > 0) {
            return new Furniture(sprite, "bar_stool", turns).dye(1 << Models.WOOD, surfaceHigh).stand(surfaceUp, BAR_SEAT, false);
        }
        if (holds.equals("clothingdryer") && single && counterHigh) {
            return new Furniture(sprite, "clothes_dryer", turns).dye(1 << Models.ENAMEL, surfaceHigh * 0.5f).stand(surfaceUp, DRYER_TOP, true);
        }
        // The benches of the seating sheets are planks on two legs. A mall's brick bench and one of logs are not.
        if (called.equals("Bench") && pair && rests && surface > 0 && name.startsWith("furniture_seating") && !group.equals("Park")
                && !group.equals("Fancy Outdoors")) {
            return new Furniture(sprite, "plank_bench", turns).dye(1 << Models.WOOD, surfaceHigh).stand(surfaceUp, BENCH_SEAT, false);
        }
        if (called.equals("Pew") && along == 1 && across == 3 && surface > 0) {
            return new Furniture(sprite, "pew", turns).dye(1 << Models.WOOD, surfaceHigh).stand(surfaceUp, PEW_SEAT, false);
        }
        if (name.startsWith(BEDDING) && called.equals("Beds") && along == 2 && across == 1) {
            return new Furniture(sprite, group.equals("Bunk") ? "steel_bunk_bed" : "bunk_bed", turns).stand(0f, 0f, false);
        }
        if (called.equals("School Bench") && holds.equals("desk") && single && properties.isTable() && surface > 0 && surface <= WorldScene.TABLE_HIGHEST) {
            return new Furniture(sprite, "school_desk", turns).dye(1 << Models.WOOD, surfaceHigh).stand(surfaceUp, DESK_TOP, false);
        }
        return null;
    }
}

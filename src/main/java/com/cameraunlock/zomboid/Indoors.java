package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoDirections;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;

/**
 * What stands in rooms and hangs on their walls that Furniture has a model for besides the
 * pieces it tells itself: a kitchen's hood, bins, vents and steel counters, a bathroom's
 * fittings, an office's cabinets and machines, and the furniture the map has most of. These
 * rules are tried before Furniture's own, so nothing here may be a piece it tells by the same signs.
 */
final class Indoors {
    private Indoors() {}

    /** How high over the floor the lower edge of a hood over a cooker is, in tiles: a forearm over the back of the hob. */
    private static final float HOOD_FOOT = 1.58f;
    /** How high the shelf of a coat rack is, in tiles, and where on its sprite, in levels, the shelf gives its colour. */
    private static final float COAT_SHELF = 1.45f, COAT_DYE = 0.58f;
    /** How high the top of a cupboard with a mirror for a door is hung, in tiles: the game gives every one a height over a head. */
    private static final float MIRROR_TOP = 1.95f;
    /** How high, in metres, the part of a model is that the game gives the height of. */
    private static final float BASIN_RIM = 0.87f, CHEST_TOP = 0.88f, BAR_SEAT = 0.75f, DRYER_TOP = 0.92f, BENCH_SEAT = 0.45f, PEW_SEAT = 0.44f, DESK_TOP = 0.74f;
    /** Where on its sprite, in levels, a thing gives its colour: the side of a box, a can or a cabinet, the foot of a lamp, a bottle of water. */
    private static final float BOX_DYE = 0.08f, CAN_DYE = 0.2f, FOOT_DYE = 0.01f, BOTTLE_DYE = 0.45f;
    /** How high the cloth of the pool table model is, in metres, and where on its sprite, in levels, the cloth gives its colour: under the height the game gives its rails. */
    private static final float POOL_CLOTH = 0.8f, POOL_DYE = 0.33f;
    private static final String VENTS = "rooftop_furniture_", FLOOR_LAMPS = "lighting_indoor_01_", BEDDING = "furniture_bedding";

    /** A piece told with no need of the way it faces, or one that hangs on the wall behind it. Null for anything else. */
    static Furniture anyWay(IsoSprite sprite, String name, String called, String group, String holds, PropertyContainer properties,
            IsoDirections facing, boolean blocks) {
        boolean single = sprite.getSpriteGrid() == null;
        int surface = properties.getSurface();
        boolean onTable = properties.has("IsTableTop");
        Furniture clinic = Clinic.anyWay(sprite, name, called);
        if (clinic != null) {
            return clinic;
        }
        if (called.equals("Cartbox") && holds.equals("cardboardbox") && single) {
            // The game gives a box no front. Its four sprites are the same box turned, and so is the model.
            // A box on another is the same box, as high up as the one under it is high.
            Furniture carton = new Furniture(sprite, "carton", Sheets.place(name) & 3).dye(1 << Models.PAINT, BOX_DYE);
            float top = surface / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
            return carton.stand(top, carton.model.height(), false).raise(onTable ? top : 0f);
        }
        Potted plant = Potted.of(name, called);
        if (plant != null && single) {
            Furniture pot = new Furniture(sprite, plant.model, 0).dye(1 << Models.CERAMIC, Potted.POT_DYE);
            return plant.leaves > 0f ? pot.also(1 << Models.PLASTIC, plant.leaves) : pot;
        }
        if (called.equals("Green Garbage Bin") && holds.equals("bin") && single) {
            return new Furniture(sprite, "garbage_can", 0).dye(1 << Models.PAINT, CAN_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Round Bin") && holds.equals("bin") && single) {
            return new Furniture(sprite, "waste_basket", 0).stand(0f, 0f, false);
        }
        if (called.equals("Lights") && group.startsWith("Lamp") && single && facing == null && name.startsWith(FLOOR_LAMPS)) {
            // Three lamps under one name: two uplighters, then the one with a shade.
            return new Furniture(sprite, Sheets.place(name) == 58 ? "standard_lamp" : "torchiere", 0).dye(1 << Models.PAINT, FOOT_DYE).stand(0f, 0f, false);
        }
        if (facing == null || blocks || !holds.isEmpty() || onTable || surface != 0 || !properties.has("IsHigh") && !properties.has("IsLow")) {
            return null;
        }
        int faces = Furniture.turnsOf(facing);
        int wall = WallHung.behind(facing);
        if (called.equals("Conditioner") && group.equals("Air") && single && name.startsWith(VENTS)) {
            // The grilles of the sheet, by their place on it: dark louvres, white slotted plates high and low, round ones.
            int which = Sheets.place(name);
            String vent = which == 17 || which == 18 ? "wall_vent" : which >= 32 && which <= 35 ? "wall_grille" : which == 36 || which == 37 ? "round_vent" : null;
            return vent == null ? null : new Furniture(sprite, vent, faces).hang(wall, false);
        }
        if (called.equals("Sign") && group.equals("Exit") && single) {
            return new Furniture(sprite, "exit_sign", faces).hang(wall, false);
        }
        // A bar of a lamp is a tube along its wall and no shade on an arm: it stays the beam its sprite makes.
        if (called.equals("Wall Lamp") && !group.endsWith("Half Sphere") && !group.equals("Bar") && single) {
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
            // Steel all over: wood dyed grey keeps its grain.
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
        // A first aid box holds the same things and is no cupboard.
        if (called.equals("Cabinet") && holds.equals("medicine") && single && high && !group.equals("First Aid")) {
            Furniture cabinet = new Furniture(sprite, "medicine_cabinet", turns).stand(0f, 0f, true).mirror();
            return cabinet.raise(MIRROR_TOP - cabinet.model.height());
        }
        if (called.equals("Shelves") && holds.equals("metal_shelves") && single && high) {
            Furniture shelf = new Furniture(sprite, "wire_wall_shelves", turns).stand(0f, 0f, true);
            return shelf.raise(surfaceUp - shelf.model.height());
        }
        if (called.equals("Rack") && group.equals("Coat") && single && high) {
            Furniture rack = new Furniture(sprite, "coat_rack", turns).dyeAtBack(1 << Models.WOOD, COAT_DYE).stand(0f, 0f, true);
            return rack.raise(COAT_SHELF - rack.model.height());
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
            return new Furniture(sprite, "dresser_mirror", turns).dye(1 << Models.WOOD, surfaceHigh).stand(surfaceUp, CHEST_TOP, true).mirror();
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
            return new Furniture(sprite, "plank_bench", turns).dye(1 << Models.WOOD, surfaceHigh).stand(0f, 0f, false).seat(surfaceUp, BENCH_SEAT);
        }
        if (called.equals("Pew") && along == 1 && across == 3 && surface > 0) {
            return new Furniture(sprite, "pew", turns).dye(1 << Models.WOOD, surfaceHigh).stand(0f, 0f, false).seat(surfaceUp, PEW_SEAT);
        }
        if (name.startsWith(BEDDING) && called.equals("Beds") && along == 2 && across == 1) {
            return new Furniture(sprite, group.equals("Bunk") ? "steel_bunk_bed" : "bunk_bed", turns).stand(0f, 0f, false);
        }
        // Two squares across the way it faces: each square's sprite is half the table, and stood up
        // as half a top over a card it was a cloth folded over its rail on flat legs.
        if (called.equals("Table") && group.equals("Pool") && along == 1 && across == 2 && properties.isTable() && surface > 0) {
            return new Furniture(sprite, "pool_table", turns).dye(1 << Models.CLOTH, POOL_DYE).stand(0f, 0f, false).seat(surfaceUp, POOL_CLOTH);
        }
        if (called.equals("School Bench") && holds.equals("desk") && single && properties.isTable() && surface > 0 && surface <= WorldScene.TABLE_HIGHEST) {
            return new Furniture(sprite, "school_desk", turns).dye(1 << Models.WOOD, surfaceHigh).stand(surfaceUp, DESK_TOP, false);
        }
        Furniture restaurant = restaurant(sprite, called, group, properties, turns, along, across);
        if (restaurant != null) {
            return restaurant;
        }
        Furniture seat = upholstered(sprite, called, group, pair && rests && surface == 0, turns);
        if (seat != null) {
            return seat;
        }
        return Clinic.facing(sprite, name, called, group, holds, properties, turns, along, across);
    }

    /** Where on its sprite, in levels, a diner chair's seat gives its colour, and how far over its laminate a diner table's wooden band does. */
    private static final float SWIVEL_SEAT_DYE = 0.229f, BAND_OVER_LAMINATE = 0.1167f;
    /** Where a cafe chair's seat gives its colour: how far in front of the middle of its square, and how high, in levels. */
    private static final float CAFE_SEAT_FORWARD = 0.15f, CAFE_SEAT_DYE = 0.18f;

    /** How far from the square's edge a hospital's curtain rail hangs, how high the foot of a bare rail and the hem of a
     * curtain are, in metres, and how high the sprites paint the curtain, in levels. */
    private static final float CURTAIN_RAIL_ACROSS = 0.47f, CURTAIN_RAIL_FOOT = 2.25f, CURTAIN_HEM = 0.08f, CURTAIN_DYE = 0.5f;
    /** How far along its model from the middle of the rail a gathered curtain's bunch is at its middle, in metres. */
    private static final float CURTAIN_BUNCH = 0.305f;
    /** How high the sprite of a bar's back shelving paints the front of its cabinet over the middle of its square, in levels. */
    private static final float BAR_BACK_DYE = 0.2f;

    /**
     * A restaurant's tables and chairs: a diner's tables on one column, a square one or two
     * side by side or one long one, which lie along the way they face, and its chairs that
     * turn on a column; a fifties diner's long table on one column, which lies across the
     * way it faces; a cafe's square table on a column and its wooden chairs; and the
     * shelving behind a bar.
     */
    private static Furniture restaurant(IsoSprite sprite, String called, String group, PropertyContainer properties, int turns, int along, int across) {
        int surface = properties.getSurface();
        float surfaceHigh = surface / WorldScene.SURFACE_PIXELS_PER_LEVEL, surfaceUp = surfaceHigh * ViewCamera.LEVEL_HEIGHT;
        boolean single = along == 1 && across == 1;
        boolean tableHigh = properties.isTable() && surface > 0 && surface <= WorldScene.TABLE_HIGHEST;
        // A hospital's curtain rail hung along x or along y, and its curtain drawn along it or bunched at one end: at the west or
        // the east end of a rail along x, at the south or the north end of one along y.
        if (single && group.equals("Blue Hospital") && called.startsWith("Curtain ")) {
            boolean alongX = turns == 2;
            String model = called.equals("Curtain A") ? "curtain_rail" : called.equals("Curtain B") ? "curtain_drawn" : "curtain_gathered";
            int turned = !model.equals("curtain_gathered") ? (alongX ? 0 : 1)
                    : called.equals("Curtain C") ? (alongX ? 0 : 3) : (alongX ? 2 : 1);
            Furniture curtain = new Furniture(sprite, model, turned);
            curtain.centreX = alongX ? 0.5f : CURTAIN_RAIL_ACROSS;
            curtain.centreY = alongX ? CURTAIN_RAIL_ACROSS : 0.5f;
            // A bare rail has no cloth to colour. A gathered curtain's colour is taken from its bunch: the middle of the rail is bare.
            if (!model.equals("curtain_rail")) {
                float[] bunch = new float[2];
                Models.turn(turned, model.equals("curtain_gathered") ? CURTAIN_BUNCH : 0f, 0f, bunch);
                curtain.dye(1 << Models.CLOTH, curtain.centreX + bunch[0], curtain.centreY + bunch[1], CURTAIN_DYE);
            }
            return curtain.raise(model.equals("curtain_rail") ? CURTAIN_RAIL_FOOT : CURTAIN_HEM);
        }
        // The shelving behind a bar, its back to the wall behind its facing: brown or dark, and the end pieces of a run the same.
        // The bottles the game paints on it while it holds something stand on it as ShopFittings stands a shop's goods.
        if (called.equals("Bar") && group.endsWith("Bar Wall") && single) {
            return ShopFittings.stocked(new Furniture(sprite, "bar_back", turns).dye(1 << Models.WOOD, BAR_BACK_DYE).stand(0f, 0f, true));
        }
        if (called.equals("Chair") && group.equals("Spiffos Dinner") && single) {
            return new Furniture(sprite, "swivel_chair", turns).dye(1 << Models.VINYL, SWIVEL_SEAT_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Chair") && group.equals("Pile o' Crepe") && single) {
            float[] front = new float[2];
            Models.turn(turns, 0f, -1f, front);
            return new Furniture(sprite, "cafe_chair", turns)
                    .dye(1 << Models.WOOD, 0.5f + front[0] * CAFE_SEAT_FORWARD, 0.5f + front[1] * CAFE_SEAT_FORWARD, CAFE_SEAT_DYE).stand(0f, 0f, false);
        }
        if (!called.equals("Table") || !tableHigh) {
            return null;
        }
        String model = group.equals("Spiffos Type B") && along == 2 && across == 1 ? "diner_tables"
                : group.equals("Spiffos Type A") && along == 2 && across == 1 ? "diner_table_long"
                : group.equals("50s Diner") && along == 1 && across == 2 ? "fifties_table"
                : group.equals("Light Wood Restaurant") && single ? "cafe_table" : null;
        if (model == null) {
            return null;
        }
        Furniture table = new Furniture(sprite, model, turns);
        if (model.startsWith("diner")) {
            table.dye(1 << Models.WORKTOP, surfaceHigh).also(1 << Models.WOOD, surfaceHigh + BAND_OVER_LAMINATE);
        } else {
            table.dye(1 << (model.equals("cafe_table") ? Models.WOOD : Models.WORKTOP), surfaceHigh);
        }
        return table.stand(surfaceUp, table.model.height(), false);
    }

    /** Where on its sprite, in levels, over the back of its first square, a burger bar's booth shows its shell, the lip along the top of its back, and a bar's booth its leather. */
    private static final float SHELL_DYE = 0.5f, LEATHER_DYE = 0.25f;

    /**
     * Seats two squares long in restaurants, bars and waiting rooms, which the game marks as somewhere to rest and
     * gives no height. The pleats of a burger bar's booth are on the side of it the game's view sees only from two of
     * its four ways, so they are one colour whichever way it faces.
     */
    private static Furniture upholstered(IsoSprite sprite, String called, String group, boolean restingPair, int turns) {
        if (!restingPair) {
            return null;
        }
        if (called.equals("Seat") && (group.equals("Spiffos Lounge") || group.equals("Whirled"))) {
            return new Furniture(sprite, "lounge_booth", turns).dyeAtBack(1 << Models.PAINT, SHELL_DYE).stand(0f, 0f, true);
        }
        if (called.equals("Seating") && group.equals("Leather Bar")) {
            return new Furniture(sprite, "leather_booth", turns).dyeAtBack(1 << Models.VINYL, LEATHER_DYE).stand(0f, 0f, true);
        }
        if (called.equals("Chairs") && group.equals("Waiting")) {
            return new Furniture(sprite, "waiting_chairs", turns).stand(0f, 0f, false);
        }
        return null;
    }

    /** The sheet of a bar's fittings. Its wooden panelling has no name but the placeholder "CustomName" and no facing. */
    private static final String BAR = "location_restaurant_bar_01_";
    /** How high over the back of its square, in levels, a length of panelling shows its wood, and where on its square the post that ends a run stands. */
    private static final float PANELLING_DYE = 0.2f, PANELLING_POST = 0.08f;
    /**
     * Where the tall run's sprites paint the brass of its foot rail, which its metal takes its colour from: how far along
     * its edge, how far out from it, and how high, in levels. The line is two to four texels thick, so the place was found
     * on the sprites themselves (84 and 85 of Tiles2x, the only pack that has them): every point within 0.3 of a texel of
     * it is painted, and the shader's nine taps there, sampled nearest or bilinear, take the brass and none of the wood.
     */
    private static final float FOOT_RAIL_ALONG = 0.7f, FOOT_RAIL_OUT = 0.25f, FOOT_RAIL_DYE = 0.0725f;

    /**
     * A bar's panelling, by its place on the sheet: a run of low boards under a rail stands along the north edge of its
     * square (77) or the west edge (76), its corner where the two meet (78), the post that ends a run at the north corner
     * of the square past its end (79), and a run of tall framed panels with a foot rail before it, north (85) and west
     * (84). The game's picture of each is seen from the room, so each faces into its square. Null for the rest of the sheet.
     */
    static Furniture barPanelling(IsoSprite sprite, String name) {
        if (sprite.getSpriteGrid() != null) {
            return null;
        }
        return switch (Sheets.place(name, BAR)) {
            case 77 -> panelling(sprite, "wainscot_north", 2);
            case 76 -> panelling(sprite, "wainscot_west", 1);
            case 85 -> panelling(sprite, "bar_panelling", 2).alsoAt(1 << Models.METAL, FOOT_RAIL_ALONG, FOOT_RAIL_OUT, FOOT_RAIL_DYE);
            case 84 -> panelling(sprite, "bar_panelling_west", 1).alsoAt(1 << Models.METAL, FOOT_RAIL_OUT, FOOT_RAIL_ALONG, FOOT_RAIL_DYE);
            case 78 -> new Furniture(sprite, "wainscot_north_west", 2).dyeAtBack(1 << Models.WOOD, PANELLING_DYE).meet(0f, 0f, false);
            case 79 -> {
                Furniture post = new Furniture(sprite, "wainscot_post", 0).dye(1 << Models.WOOD, PANELLING_POST, PANELLING_POST, PANELLING_DYE).meet(0f, 0f, false);
                post.centreX = PANELLING_POST;
                post.centreY = PANELLING_POST;
                yield post;
            }
            default -> null;
        };
    }

    private static Furniture panelling(IsoSprite sprite, String model, int turns) {
        return new Furniture(sprite, model, turns).dyeAtBack(1 << Models.WOOD, PANELLING_DYE).meet(0f, 0f, true);
    }
}

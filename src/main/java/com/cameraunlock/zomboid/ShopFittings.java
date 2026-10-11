package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.inventory.ItemContainer;
import zombie.iso.IsoDirections;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;

/**
 * What a shop, a mall, a bar or a salon has standing in it that Furniture had no model for: the runs of shelving a
 * shop sells from, the rails and stands its clothes hang on, its open chiller, its glass counter and magazine stands,
 * a mall's high table, and the seats of malls, diners and hairdressers.
 *
 * The game paints what a shelf or a rail holds over each square of it while the square's container holds something,
 * and that painting has no place on a model. While it paints it, goods or clothes of the mod's own stand on that
 * square of the model instead, many or few as the game paints them, and none while it paints none.
 *
 * Tried after Indoors, before Furniture's own rules, so nothing here may be a piece either tells by the same signs.
 */
final class ShopFittings {
    private ShopFittings() {}

    /**
     * Where the sprite of a run of shelving gives its boards' colour: over the middle of its first square, between its
     * two shelves, in levels.
     */
    private static final float BOARD_DYE = 0.65f / ViewCamera.LEVEL_HEIGHT;
    /** And an open cupboard's, from its top; a mall chair's, from its seat; a diner stool's, from its seat. */
    private static final float WARDROBE_DYE = 1.43f / ViewCamera.LEVEL_HEIGHT, MALL_SEAT_DYE = 0.45f / ViewCamera.LEVEL_HEIGHT,
            STOOL_SEAT_DYE = 0.62f / ViewCamera.LEVEL_HEIGHT;
    /** And a magazine stand's, from low on its front; a mall table's, from its top. */
    private static final float STAND_DYE = 0.4f / ViewCamera.LEVEL_HEIGHT, TABLE_DYE = 0.93f / ViewCamera.LEVEL_HEIGHT;
    /** How high, in metres, the seat of the bar stool's model is, which the game gives the height of. */
    private static final float BAR_SEAT = 0.75f;

    /**
     * What tools/models/furniture.py builds the goods and the clothes on: in metres over the fitting's floor, the foot
     * of the tickets on the edge of a run's deck (GONDOLA_SHELVES[0] - TICKETS) and of a chiller's (CHILLER_SHELVES[0]
     * - TICKETS), the top of the packets on a pegboard's higher hooks (HOOKS_Z[1] + 0.02), the top of a long run's deck,
     * and the tops of the rails clothes hang from: a rail's, a rack's, a stand's higher arms and an open cupboard's.
     */
    static final float SHELF_FOOT = 0.10f, CHILLER_FOOT = 0.28f, HOOKS_TOP = 0.90f, FOLDED_FOOT = 0.14f, RAIL_TOP = 1.3f, RACK_TOP = 1.65f,
            STAND_TOP = 1.42f, WARDROBE_RAIL_TOP = 1.27f;
    /** And the foot of the tickets on a magazine stand's lowest rail (MAGAZINE_TIERS[0][0] + 0.05 - TICKETS). */
    static final float MAGAZINE_FOOT = 0.23f;

    /**
     * The glass counter's model in metres from the middle of its footprint, its back to +Y, as tools/models/furniture.py
     * builds glass_counter: half its width, the foot and the top of the glass round its case, the top of the case, how
     * thick its corner posts are and where the black board at its back stands. The glass is laid by these numbers.
     */
    static final float COUNTER_HALF = 0.49f, COUNTER_GLASS_FOOT = 0.44f, COUNTER_GLASS_TOP = 0.85f, COUNTER_TOP = 0.87f, COUNTER_POST = 0.025f,
            COUNTER_BACK = 0.47f;

    /** What the fitting holds, made: the model of many of it and of few, and how high it stands, by its foot or by its top. */
    record Stock(String many, String few, float at, boolean hung) {}

    static final Stock SHELF = new Stock("shelf_goods_many", "shelf_goods_few", SHELF_FOOT, false),
            HOOKS = new Stock("hook_goods_many", "hook_goods_few", HOOKS_TOP, true),
            FOLDED = new Stock("folded_many", "folded_few", FOLDED_FOOT, false),
            CHILLED = new Stock("chiller_goods_many", "chiller_goods_few", CHILLER_FOOT, false),
            RAIL = new Stock("rail_garments_many", "rail_garments_few", RAIL_TOP, true),
            RACK = new Stock("rack_garments_many", "rack_garments_few", RACK_TOP, true),
            STAND = new Stock("stand_garments_many", "stand_garments_few", STAND_TOP, true),
            COATS = new Stock("coat_garments_many", "coat_garments_few", WARDROBE_RAIL_TOP, true),
            MAGAZINES = new Stock("magazines_many", "magazines_few", MAGAZINE_FOOT, false);

    /**
     * ContainerOverlays.updateContainerOverlaySprite paints a container's square with its fewer things while it holds
     * less than this many.
     */
    static final int MANY = 7;

    /** The sheet a mall's high table is on, and the first of its four quarters there: see unnamed. */
    private static final String MALL = "location_shop_mall_01_";
    private static final int MALL_TABLE = 48;

    /**
     * A fitting the game gives no name: a quarter of a mall's high table four squares big, which is a sprite of its own
     * on each square, numbered across the table's north half and then its south half. Each is the one model, turned so
     * its corner at the middle of the table is the square's corner the table's middle is at. Null for anything else.
     */
    static Furniture unnamed(IsoSprite sprite, String name) {
        if (sprite.getSpriteGrid() != null) {
            return null;
        }
        // The model's middle of the table is its square's corner at -X, +Y: the map's south east turned none, south
        // west turned once, north west twice and north east three times.
        int turns = switch (Sheets.place(name, MALL) - MALL_TABLE) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 2;
            default -> -1;
        };
        return turns < 0 ? null : new Furniture(sprite, "mall_table_quarter", turns).dye(1 << Models.WORKTOP, 0.5f, 0.5f, TABLE_DYE);
    }

    /** A fitting told with no need of the way it faces: a clothes stand, which has none. Null for anything else. */
    static Furniture anyWay(IsoSprite sprite, String called, String holds, IsoDirections facing) {
        if (called.equals("Clothes Stand") && holds.equals("clothingrack") && facing == null && sprite.getSpriteGrid() == null) {
            return stocked(new Furniture(sprite, "clothes_stand", 0).stand(0f, 0f, false));
        }
        return null;
    }

    /**
     * A fitting that stands the way it faces. Null for anything else.
     *
     * @param along   how many squares the piece is long the way it faces
     * @param across  and across that
     */
    static Furniture facing(IsoSprite sprite, String called, String group, String holds, PropertyContainer properties, int turns, int along,
            int across) {
        boolean single = along == 1 && across == 1;
        // Runs of shelving the game has facing south and east only. Their SpriteGridPos counts from the end where the
        // map's x or y is least, and the square at grid place 3 is the pegboard: at the model's +X facing south, and at
        // its -X facing east, where the model's +X is the map's north.
        boolean run = along == 1 && across == 4 && (turns == 1 || turns == 2);
        if (run && holds.equals("shelves") && (called.equals("Shelves") && (group.equals("Beige Rack Shop") || group.equals("Greene's Grocery"))
                || called.equals("Rack") && group.equals("Zippee Shelves"))) {
            String model = (group.startsWith("Zippee") ? "shop_gondola_red" : "shop_gondola") + (turns == 1 ? "_m" : "");
            return stocked(new Furniture(sprite, model, turns).dye(1 << Models.PAINT, 0.5f, 0.5f, BOARD_DYE).stand(0f, 0f, false));
        }
        // A clothes shop's wooden run fills three of its squares and the end of the fourth, at grid place 3.
        if (run && called.equals("Shelves") && group.equals("Long")) {
            Furniture shelves = stocked(new Furniture(sprite, "long_shelves", turns).dye(1 << Models.WOOD, 0.5f, 0.5f, BOARD_DYE).stand(0f, 0f, false));
            float start = shelves.model.width() * shelves.scale / 2f;
            if (turns == 2) {
                shelves.centreX = start;
            } else {
                shelves.centreY = start;
            }
            return shelves;
        }
        if (called.equals("Rack") && holds.equals("clothingrack") && along == 1 && across == 2 && (group.equals("Small Clothes") || group.equals("Large Clothes"))) {
            return stocked(new Furniture(sprite, group.startsWith("Small") ? "clothes_rail" : "clothes_rack", turns).stand(0f, 0f, false));
        }
        if (called.equals("Wardrobe") && group.equals("Open") && holds.equals("clothingrack") && along == 1 && across == 3) {
            return stocked(new Furniture(sprite, "open_wardrobe", turns).dye(1 << Models.WOOD, 0.5f, 0.5f, WARDROBE_DYE).stand(0f, 0f, true));
        }
        int surface = properties.getSurface();
        // A shop's glass counter. The corner of a run of them is another shape, which this is not.
        if (called.equals("Counter") && group.equals("Glass Display") && holds.equals("displaycase") && single && surface > 0) {
            float top = surface / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
            Furniture counter = new Furniture(sprite, "glass_counter", turns).stand(top, COUNTER_TOP, false);
            // Shrunk along the ground to fit its square, it is still as high as the game's top, which what stands on it stands on.
            counter.scaleUp = top / COUNTER_TOP;
            counter.glazed = true;
            return counter;
        }
        // A magazine stand one square long, and Zippee's two.
        if (called.equals("Magazine Stand") && holds.equals("shelvesmag") && single
                || called.equals("Magazine Shelf") && group.equals("Red") && holds.equals("shelvesmag") && along == 1 && across == 2) {
            return stocked(new Furniture(sprite, single ? "magazine_stand" : "magazine_stand_red", turns).dye(1 << Models.PAINT, 0.5f, 0.5f, STAND_DYE)
                    .stand(0f, 0f, true));
        }
        if (called.equals("Shelves") && group.equals("Generic Cooled") && holds.equals("fridge") && single) {
            return stocked(new Furniture(sprite, "open_chiller", turns).stand(0f, 0f, true));
        }
        if (called.equals("Chair") && group.equals("Mall") && single) {
            return new Furniture(sprite, "mall_chair", turns).dye(1 << Models.VINYL, 0.5f, 0.5f, MALL_SEAT_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Chair") && group.equals("Salon") && single) {
            return new Furniture(sprite, "salon_chair", turns).stand(0f, 0f, false);
        }
        if (called.equals("Hair Dryer") && group.equals("Salon") && single) {
            return new Furniture(sprite, "hood_dryer", turns).stand(0f, 0f, false);
        }
        // A bar's stool on four legs, which the game calls by the colour of its upholstery.
        if (called.equals("Blue Bar Stool") && single && surface > 0) {
            float seat = surface / WorldScene.SURFACE_PIXELS_PER_LEVEL;
            return new Furniture(sprite, "bar_stool", turns).dye(1 << Models.WOOD, seat).stand(seat * ViewCamera.LEVEL_HEIGHT, BAR_SEAT, false);
        }
        if (called.equals("50s Barstool") && single) {
            return new Furniture(sprite, "pedestal_stool", turns).dye(1 << Models.VINYL, 0.5f, 0.5f, STOOL_SEAT_DYE).stand(0f, 0f, false);
        }
        return null;
    }

    private static Furniture stocked(Furniture piece) {
        piece.stocked = true;
        return piece;
    }

    /** What stands on a square of a stocked fitting while the game paints what the square holds. */
    static Stock stockOf(Furniture piece) {
        return switch (piece.model.name) {
            case "shop_gondola", "shop_gondola_m", "shop_gondola_red", "shop_gondola_red_m" -> piece.gridX + piece.gridY == 3 ? HOOKS : SHELF;
            case "long_shelves" -> FOLDED;
            case "open_chiller" -> CHILLED;
            case "clothes_rail" -> RAIL;
            case "clothes_rack" -> RACK;
            case "clothes_stand" -> STAND;
            case "open_wardrobe" -> COATS;
            case "magazine_stand", "magazine_stand_red" -> MAGAZINES;
            default -> throw new IllegalStateException(piece.model.name + " is stocked and ShopFittings has nothing to stand on it");
        };
    }

    /** Whether the game paints a container's square with its many things, and not its fewer. */
    static boolean many(IsoObject object) {
        ItemContainer held = object.getContainer();
        return held != null && held.getItems().size() >= MANY;
    }

    /**
     * Stands goods or clothes on one square of a fitting while the game paints what the square holds over it, many or
     * few as the game paints them. x and y are the square, foot the floor under it.
     */
    static void addStock(WorldScene.Frame build, IsoObject object, Furniture piece, int x, int y, float foot) {
        if (object.getOverlaySprite() == null) {
            return;
        }
        Stock stock = stockOf(piece);
        Models.Model goods = Models.get(many(object) ? stock.many : stock.few);
        float up = (stock.hung ? stock.at - goods.height() : stock.at) * piece.scaleUp;
        float[] middle = new float[2];
        middleOf(piece, x, y, middle);
        build.placed.add(goods, build.frame, x, y, middle[0], middle[1], foot + up, piece.turns, piece.scale, piece.scaleUp, WorldScene.PLAIN, 0, 0f, 0f,
                0, 0f, 0f);
    }

    /**
     * Lays the glass of a glass counter's case: its top, its front and its two ends, each a sheet inside the steel
     * frame round it. The back is the board whoever serves stands at. x and y are the counter's square, foot the floor
     * under it.
     */
    static void addGlass(WorldScene.Frame build, Furniture piece, int x, int y, float foot) {
        Furniture.Glass glass = new Furniture.Glass(build, piece, x, y, foot);
        float in = COUNTER_HALF - COUNTER_POST / 2f, low = COUNTER_GLASS_FOOT, high = COUNTER_GLASS_TOP, top = high + 0.01f;
        float side = COUNTER_HALF - COUNTER_POST, back = COUNTER_BACK;
        glass.face(0f, 0f, 1f);
        pane(glass, -side, -side, top, side, -side, top, side, back, top, -side, back, top);
        glass.face(0f, -1f, 0f);
        pane(glass, -side, -in, low, side, -in, low, side, -in, high, -side, -in, high);
        for (float way = -1f; way <= 1f; way += 2f) {
            float end = way * in;
            glass.face(way, 0f, 0f);
            pane(glass, end, -side, low, end, back, low, end, back, high, end, -side, high);
        }
    }

    /** A sheet of glass through four corners given in the model as x, y and height, round its edge. */
    private static void pane(Furniture.Glass glass, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3) {
        glass.corner(x0, y0, z0, 0f, 0f);
        glass.corner(x1, y1, z1, 1f, 0f);
        glass.corner(x2, y2, z2, 1f, 1f);
        glass.corner(x0, y0, z0, 0f, 0f);
        glass.corner(x2, y2, z2, 1f, 1f);
        glass.corner(x3, y3, z3, 0f, 1f);
    }

    /**
     * Into out, where what a square of a fitting holds stands: the middle of the square (x, y) along the fitting, and
     * where the fitting is across it. The model's x runs along the map's x when it is turned an even number of times.
     */
    static void middleOf(Furniture piece, int x, int y, float[] out) {
        boolean alongX = (piece.turns & 1) == 0;
        out[0] = x + (alongX ? 0.5f : piece.centreX - piece.gridX);
        out[1] = y + (alongX ? piece.centreY - piece.gridY : 0.5f);
    }
}

package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.Arrays;
import zombie.iso.sprite.IsoSprite;

/**
 * A shop's fittings: which sprites are which fitting and every square of a piece the same one, how each stands on its
 * squares, and that what ShopFittings stands on a square while the game paints its goods is where the fitting has room
 * for it. The rows are the tiles' own, sounds left out.
 */
final class ShopFittingTests {
    private ShopFittingTests() {}

    static void run() {
        Checks.run("eachSquareOfARunOfShelvingIsTheRunWithItsPegboardOnItsLastGridSquare",
                ShopFittingTests::eachSquareOfARunOfShelvingIsTheRunWithItsPegboardOnItsLastGridSquare);
        Checks.run("aClothesShopsLongRunStartsAtItsFirstSquare", ShopFittingTests::aClothesShopsLongRunStartsAtItsFirstSquare);
        Checks.run("railsStandsAndCupboardsClothesHangOnAreTheirModels", ShopFittingTests::railsStandsAndCupboardsClothesHangOnAreTheirModels);
        Checks.run("seatsAreTheirModelsAndTakeTheirSeatsColour", ShopFittingTests::seatsAreTheirModelsAndTakeTheirSeatsColour);
        Checks.run("whatAFittingHoldsStandsWhereItsModelHasRoomForIt", ShopFittingTests::whatAFittingHoldsStandsWhereItsModelHasRoomForIt);
        Checks.run("eachSquaresGoodsStandOnThatSquare", ShopFittingTests::eachSquaresGoodsStandOnThatSquare);
        Checks.run("aGlassCounterIsItsModelAndItsGlassIsInsideItsFrame", ShopFittingTests::aGlassCounterIsItsModelAndItsGlassIsInsideItsFrame);
        Checks.run("magazineStandsAreTheStandWithMagazinesOnItsTiers", ShopFittingTests::magazineStandsAreTheStandWithMagazinesOnItsTiers);
        Checks.run("aMallTablesFourQuartersMeetAtItsMiddle", ShopFittingTests::aMallTablesFourQuartersMeetAtItsMiddle);
    }

    private static final String BEIGE = "|BlocksPlacement||CanBreak||CanScrap||CustomName|Shelves|GroupName|Beige Rack Shop|IsLow||IsMoveAble||ItemHeight|32|"
            + "Material|Wood|PickUpLevel|1|PickUpTool|Hammer|PickUpWeight|75|PlaceTool|Hammer|container|shelves|Facing|";
    private static final String ZIPPEE = "|BlocksPlacement||CanBreak||CanScrap||CustomName|Rack|GroupName|Zippee Shelves|IsLow||IsMoveAble||ItemHeight|34|"
            + "Material|Wood|PickUpLevel|1|PickUpTool|Hammer|PickUpWeight|100|PlaceTool|Hammer|container|shelves|Facing|";
    private static final String GREENES = "|BlocksPlacement||CanBreak||CanScrap||CustomName|Shelves|GroupName|Greene's Grocery|IsLow||IsMoveAble||"
            + "Material|Wood|Material2|Nails|PickUpLevel|1|PickUpTool|Hammer|PickUpWeight|100|PlaceTool|Hammer|container|shelves|solidtrans||Facing|";
    private static final String LONG = "|CanBreak||CanScrap||CustomName|Shelves|GroupName|Long|IsMoveAble||ItemHeight|32|Material|Wood|Material2|Nails|"
            + "MaterialType|Wood|PickUpLevel|1|PickUpTool|Hammer|PickUpWeight|100|PlaceTool|Hammer|Facing|";

    /**
     * A piece of the squares count long, one sprite a square: the sheet and the sprite numbers from SpriteGridPos 0 up,
     * its facing, and what each row adds after its facing and SpriteGridPos (the squares the game gives no container
     * have none).
     */
    private static Furniture[] laid(String sheet, String row, int[] numbers, String facing, String[] extra) {
        boolean alongX = facing.equals("S") || facing.equals("N");
        IsoSprite[] squares = new IsoSprite[numbers.length];
        for (int i = 0; i < numbers.length; i++) {
            squares[i] = TileRows.sprite(sheet + numbers[i] + row + facing + "|SpriteGridPos|" + (alongX ? i + ",0" : "0," + i) + extra[i]);
        }
        TileRows.piece(alongX ? numbers.length : 1, alongX ? 1 : numbers.length, squares);
        Furniture[] pieces = new Furniture[numbers.length];
        for (int i = 0; i < numbers.length; i++) {
            pieces[i] = Furniture.known(squares[i]);
        }
        return pieces;
    }

    private static String[] same(int count, String extra) {
        String[] all = new String[count];
        Arrays.fill(all, extra);
        return all;
    }

    private static String model(Furniture piece) {
        return piece == null ? "nothing" : piece.model == null ? "no model" : piece.model.name;
    }

    private static void eachSquareOfARunOfShelvingIsTheRunWithItsPegboardOnItsLastGridSquare() {
        Object[][] runs = {
            {"location_shop_generic_01_", BEIGE, new int[] {8, 9, 10, 11}, "S", "shop_gondola", 2},
            {"location_shop_generic_01_", BEIGE, new int[] {15, 14, 13, 12}, "E", "shop_gondola_m", 1},
            {"location_shop_greenes_01_", GREENES, new int[] {56, 57, 58, 59}, "S", "shop_gondola", 2},
            {"location_shop_greenes_01_", GREENES, new int[] {63, 62, 61, 60}, "E", "shop_gondola_m", 1},
            {"location_shop_zippee_01_", ZIPPEE, new int[] {8, 9, 10, 11}, "S", "shop_gondola_red", 2},
            {"location_shop_zippee_01_", ZIPPEE, new int[] {15, 14, 13, 12}, "E", "shop_gondola_red_m", 1},
        };
        for (Object[] one : runs) {
            String which = one[0] + "" + ((int[]) one[2])[0] + " facing " + one[3];
            // The pegboard's square of the beige run and of Zippee's lets a body through; Greene's does not.
            String[] extra = one[1] == GREENES ? same(4, "") : new String[] {"|solidtrans|", "|solidtrans|", "|solidtrans|", ""};
            Furniture[] squares = laid((String) one[0], (String) one[1], (int[]) one[2], (String) one[3], extra);
            for (int i = 0; i < squares.length; i++) {
                Furniture square = squares[i];
                check(model(square).equals(one[4]) && square.stocked, "square " + i + " of " + which + " is the run: " + model(square));
                if (square == null || square.model == null) {
                    continue;
                }
                check(square.turns == (int) one[5], which + " faces the way its sprite does: " + square.turns);
                check(square.scale == Furniture.TILES_PER_METRE, which + " is as big as its model, which fits its four squares");
                check(Math.abs(square.centreX - square.width / 2f) < 1e-4f && Math.abs(square.centreY - square.height / 2f) < 1e-4f,
                        which + " stands in the middle of its squares");
                check(ShopFittings.stockOf(square) == (i == 3 ? ShopFittings.HOOKS : ShopFittings.SHELF),
                        which + "'s square " + i + " holds " + (i == 3 ? "what hangs on the pegboard" : "goods on its shelves"));
            }
            // The pegboard is on the square numbered last: at the model's +X turned to face south, which is the map's +x,
            // and at its -X turned to face east, where the model's -X is the map's +y.
            float[] way = new float[2];
            Models.turn((int) one[5], ((String) one[4]).endsWith("_m") ? -1f : 1f, 0f, way);
            check(way[0] + way[1] > 0.99f, which + " has its pegboard towards the end where the map's x or y is most: " + way[0] + ", " + way[1]);
        }
    }

    private static void aClothesShopsLongRunStartsAtItsFirstSquare() {
        String[] extra = {"|container|clothingrack|solidtrans|", "|container|clothingrack|solidtrans|", "|container|clothingrack|solidtrans|", ""};
        for (String facing : new String[] {"S", "E"}) {
            Furniture[] squares = laid("location_shop_generic_01_", LONG, facing.equals("S") ? new int[] {40, 41, 42, 43} : new int[] {47, 46, 45, 44},
                    facing, extra);
            for (Furniture square : squares) {
                check(model(square).equals("long_shelves"), "every square of the long run facing " + facing + " is it, the end board's too: " + model(square));
                if (square == null || square.model == null) {
                    continue;
                }
                float along = facing.equals("S") ? square.centreX : square.centreY, across = facing.equals("S") ? square.centreY : square.centreX;
                check(Math.abs(along - square.model.width() / 2f) < 1e-4f, "it starts at the edge of its first square facing " + facing + ": middle at " + along);
                check(Math.abs(across - 0.5f) < 1e-4f, "and stands in the middle of its squares across: " + across);
                check(along + square.model.width() / 2f <= 4f, "and ends inside its last square");
            }
        }
    }

    private static void railsStandsAndCupboardsClothesHangOnAreTheirModels() {
        String rack = "|BlocksPlacement||CanBreak||CanScrap||CustomName|Rack|IsMoveAble||Material|MetalBars|Material2|MetalScrap|PickUpLevel|1|PickUpTool|Hammer|"
                + "PickUpWeight|50|PlaceTool|Hammer|container|clothingrack|GroupName|";
        Furniture[] small = laid("location_shop_generic_01_", rack + "Small Clothes|Facing|", new int[] {50, 51}, "S", same(2, ""));
        Furniture[] large = laid("location_shop_generic_01_", rack + "Large Clothes|Facing|", new int[] {37, 36}, "E", same(2, ""));
        for (Furniture square : small) {
            check(model(square).equals("clothes_rail") && square.stocked && ShopFittings.stockOf(square) == ShopFittings.RAIL,
                    "each square of a small clothes rail is the rail, and holds clothes on its rail: " + model(square));
        }
        for (Furniture square : large) {
            check(model(square).equals("clothes_rack") && square.stocked && ShopFittings.stockOf(square) == ShopFittings.RACK,
                    "each square of a large rack is the rack: " + model(square));
        }
        Furniture stand = Furniture.known(TileRows.sprite("location_shop_generic_01_52|BlocksPlacement||CanScrap||CustomName|Clothes Stand|IsMoveAble||"
                + "Material|MetalBars|Material2|MetalScrap|PickUpLevel|1|PickUpWeight|50|ScrapSize|Small|container|clothingrack|"));
        check(model(stand).equals("clothes_stand") && stand.stocked && ShopFittings.stockOf(stand) == ShopFittings.STAND,
                "a clothes stand, which has no facing, is the stand: " + model(stand));
        String open = "|BlocksPlacement||CanBreak||CanScrap||CustomName|Wardrobe|GroupName|Open|IsMoveAble||ItemHeight|55|Material|Wood|Material2|Nails|"
                + "MaterialType|Wood|PickUpLevel|1|PickUpTool|Hammer|PickUpWeight|100|PlaceTool|Hammer|container|clothingrack|solidtrans||Facing|";
        for (Furniture square : laid("location_shop_generic_01_", open, new int[] {59, 60, 61}, "S", same(3, ""))) {
            check(model(square).equals("open_wardrobe") && ShopFittings.stockOf(square) == ShopFittings.COATS,
                    "each square of a clothes shop's open cupboard is the cupboard, with coats on its rail: " + model(square));
            check(square != null && square.dyed == 1 << Models.WOOD, "made of the wood its sprite shows");
        }
        Furniture chiller = Furniture.known(TileRows.sprite("location_shop_generic_01_67|BlocksPlacement||CanBreak||CanScrap||CustomName|Shelves|Facing|E|"
                + "GroupName|Generic Cooled|IsMoveAble||ItemHeight|72|Material|Wood|Material2|Nails|Material3|Electric|MaterialType|Plastic|PickUpLevel|1|"
                + "PickUpTool|Electrician|PickUpWeight|100|PlaceTool|Electrician|container|fridge|solidtrans|"));
        check(model(chiller).equals("open_chiller") && ShopFittings.stockOf(chiller) == ShopFittings.CHILLED && chiller.turns == 1,
                "a square of a shop's open chiller is the chiller, facing the way its sprite does: " + model(chiller));
    }

    private static void seatsAreTheirModelsAndTakeTheirSeatsColour() {
        Furniture mall = Furniture.known(TileRows.sprite("location_shop_mall_01_41|BedType|badBed|BlocksPlacement||CanScrap||CustomName|Chair|Facing|E|"
                + "GroupName|Mall|IsLow||IsMoveAble||Material|MetalBars|PickUpWeight|50|bed||chairE||SeatMaterial|Plastic"));
        check(model(mall).equals("mall_chair") && mall.turns == 1 && mall.dyed == 1 << Models.VINYL, "a mall's chair is its model, its seat the sprite's colour");
        Furniture salon = Furniture.known(TileRows.sprite("location_services_beauty_01_2|BedType|badBed|BlocksPlacement||CanScrap||CustomName|Chair|Facing|S|"
                + "GroupName|Salon|IsLow||IsMoveAble||Material|MetalScrap|Material2|Leather|MaterialType|Metal|PickUpWeight|50|ScrapSize|Small|bed||chairS||"
                + "solidtrans|"));
        check(model(salon).equals("salon_chair") && salon.turns == 2, "a hairdresser's chair is its model: " + model(salon));
        Furniture blue = Furniture.known(TileRows.sprite("location_restaurant_bar_01_25|BedType|badBed|BlocksPlacement||CanScrap||CustomName|Blue Bar Stool|"
                + "Facing|S|IsLow||IsMoveAble||Material|Wood|PickUpWeight|50|ScrapSize|Small|Surface|28|bed||chairS||SeatMaterial|Leather"));
        check(model(blue).equals("bar_stool"), "a bar's stool on four legs is the bar stool: " + model(blue));
        if (blue != null && blue.model != null) {
            float seat = 28 / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
            check(Math.abs(0.75f * blue.scaleUp - seat) < 1e-4f, "with its seat as high as the game says: " + 0.75f * blue.scaleUp + " against " + seat);
        }
        Furniture diner = Furniture.known(TileRows.sprite("location_restaurant_diner_01_42|BedType|badBed|BlocksPlacement||CanScrap||CustomName|50s Barstool|"
                + "Facing|S|IsLow||IsMoveAble||Material|MetalScrap|Material2|Leather|PickUpWeight|50|ScrapSize|Small|bed||chairS||SeatMaterial|Leather"));
        check(model(diner).equals("pedestal_stool") && diner.dyed == 1 << Models.VINYL, "a diner's stool on one column is its model: " + model(diner));
    }

    private static void whatAFittingHoldsStandsWhereItsModelHasRoomForIt() {
        Models.Model run = Models.get("shop_gondola"), goods = Models.get(ShopFittings.SHELF.many());
        check(goods.minX >= -0.5f && goods.maxX <= 0.5f, "a square's goods stay on that square of the run: " + goods.minX + " to " + goods.maxX);
        // The run's plain end stands in its first square, its inside a little past the run's end.
        check(-2f + goods.minX + 0.5f > run.minX + 0.03f, "and clear of the run's end: " + (-1.5f + goods.minX) + " against " + run.minX);
        check(goods.minY >= run.minY - 0.005f && goods.maxY < run.maxY, "and within its depth: " + goods.minY + " to " + goods.maxY);
        check(ShopFittings.SHELF_FOOT + goods.height() < 1.02f + 0.3f, "and no higher than a hand over its back");
        Models.Model hooks = Models.get(ShopFittings.HOOKS.many());
        check(ShopFittings.HOOKS_TOP - hooks.height() > 0.14f, "what hangs on the pegboard's hooks is over its foot: " + (ShopFittings.HOOKS_TOP - hooks.height()));
        Models.Model shelves = Models.get("long_shelves"), folded = Models.get(ShopFittings.FOLDED.many());
        check(-shelves.width() / 2f + 0.5f + folded.minX > -shelves.width() / 2f + 0.04f, "folded clothes on the long run's first square are clear of its end");
        Models.Model chiller = Models.get("open_chiller"), chilled = Models.get(ShopFittings.CHILLED.many());
        check(chilled.minX > chiller.minX + 0.03f && chilled.maxX < chiller.maxX - 0.03f, "a chiller's goods are between its sides");
        check(ShopFittings.CHILLER_FOOT + chilled.height() < chiller.height() - 0.2f, "and under its canopy");
        Object[][] hung = {{"clothes_rail", ShopFittings.RAIL}, {"clothes_rack", ShopFittings.RACK}, {"clothes_stand", ShopFittings.STAND},
            {"open_wardrobe", ShopFittings.COATS}};
        for (Object[] one : hung) {
            Models.Model fitting = Models.get((String) one[0]);
            ShopFittings.Stock stock = (ShopFittings.Stock) one[1];
            for (String name : new String[] {stock.many(), stock.few()}) {
                Models.Model clothes = Models.get(name);
                check(stock.at() - clothes.height() > 0.2f, name + " hangs clear of the floor: hem at " + (stock.at() - clothes.height()));
                check(stock.at() <= fitting.height() + 1e-3f, name + " hangs from under the top of " + one[0] + ": " + stock.at() + " against " + fitting.height());
            }
        }
        Models.Model wardrobe = Models.get("open_wardrobe"), coats = Models.get(ShopFittings.COATS.many());
        check(coats.minY > wardrobe.minY && coats.maxY < wardrobe.maxY - 0.01f, "coats hang inside the open cupboard, in front of its back");
    }

    private static void aGlassCounterIsItsModelAndItsGlassIsInsideItsFrame() {
        String row = "|BlocksPlacement||CanBreak||CanScrap||CustomName|Counter|GenericCraftingSurface|true|IsLow||IsMoveAble||IsTable||Material|Wood|"
                + "Material2|Nails|Material3|Glass|MaterialType|Glass_Light|PickUpLevel|1|PickUpTool|Hammer|PickUpWeight|100|PlaceTool|Hammer|Surface|34|"
                + "container|displaycase|solidtrans||GroupName|";
        Furniture counter = Furniture.known(TileRows.sprite("location_shop_generic_01_97" + row + "Glass Display|Facing|S"));
        check(model(counter).equals("glass_counter") && counter.glazed && counter.turns == 2, "a shop's glass counter is its model, glazed: " + model(counter));
        if (counter != null && counter.model != null) {
            float top = 34 / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
            check(Math.abs(counter.model.height() * counter.scaleUp - top) < 1e-4f, "as high as the game says its top is: " + counter.model.height() * counter.scaleUp);
        }
        Furniture corner = Furniture.known(TileRows.sprite("location_shop_generic_01_96" + row + "Glass Corner Display|Facing|S"));
        check(!model(corner).equals("glass_counter"), "the corner of a run of them is another shape and is not it: " + model(corner));
        Models.Model glazed = Models.get("glass_counter");
        check(Math.abs(glazed.maxX - ShopFittings.COUNTER_HALF) < 0.002f && Math.abs(glazed.minY + ShopFittings.COUNTER_HALF) < 0.002f
                && Math.abs(glazed.maxY - ShopFittings.COUNTER_HALF) < 0.002f, "the model is as wide as ShopFittings lays the glass for: " + glazed.minX + " to "
                + glazed.maxX + ", " + glazed.minY + " to " + glazed.maxY);
        check(Math.abs(glazed.height() - ShopFittings.COUNTER_TOP) < 0.002f, "and as high: " + glazed.height());
    }

    private static void magazineStandsAreTheStandWithMagazinesOnItsTiers() {
        String stand = "|BlocksPlacement||CanBreak||CanScrap||CustomName|Magazine Stand|IsMoveAble||Material|Wood|Material2|Nails|PickUpLevel|1|"
                + "PickUpTool|Hammer|PickUpWeight|100|PlaceTool|Hammer|container|shelvesmag|solidtrans||Facing|";
        Furniture one = Furniture.known(TileRows.sprite("location_shop_generic_01_26" + stand + "E"));
        check(model(one).equals("magazine_stand") && one.turns == 1 && ShopFittings.stockOf(one) == ShopFittings.MAGAZINES,
                "a magazine stand is the stand, facing the way its sprite does, with magazines on it: " + model(one));
        String red = "|BlocksPlacement||CanBreak||CanScrap||CustomName|Magazine Shelf|GroupName|Red|IsMoveAble||Material|Wood|PickUpLevel|1|"
                + "PickUpTool|Hammer|PickUpWeight|100|PlaceTool|Hammer|container|shelvesmag|solidtrans||Facing|";
        for (Furniture square : laid("location_shop_zippee_01_", red, new int[] {25, 24}, "S", same(2, ""))) {
            check(model(square).equals("magazine_stand_red") && ShopFittings.stockOf(square) == ShopFittings.MAGAZINES,
                    "each square of Zippee's magazine shelf is its stand: " + model(square));
        }
        Models.Model rack = Models.get("magazine_stand"), magazines = Models.get(ShopFittings.MAGAZINES.many());
        check(magazines.minX > rack.minX && magazines.maxX < rack.maxX, "the magazines are between the stand's ends");
        check(magazines.minY >= rack.minY - 0.005f && magazines.maxY < rack.maxY, "and in front of its back");
        check(ShopFittings.MAGAZINE_FOOT + magazines.height() < rack.height(), "and under its top");
    }

    private static void aMallTablesFourQuartersMeetAtItsMiddle() {
        String row = "|BlocksPlacement||CanScrap||IsLow||IsTable||Surface|34|solidtrans|";
        // The four sprites are the table's quarters in the map's north west, north east, south west and south east.
        int[][] middles = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        float[] way = new float[2];
        for (int i = 0; i < 4; i++) {
            Furniture quarter = Furniture.known(TileRows.sprite("location_shop_mall_01_" + (48 + i) + row));
            check(model(quarter).equals("mall_table_quarter"), "sprite " + (48 + i) + " is a quarter of the mall's table: " + model(quarter));
            if (quarter == null || quarter.model == null) {
                continue;
            }
            Models.turn(quarter.turns, -1f, 1f, way);
            check(way[0] == middles[i][0] && way[1] == middles[i][1], "with its corner at the table's middle towards the middle: " + way[0] + ", " + way[1]);
        }
        Furniture planter = Furniture.known(TileRows.sprite("location_shop_mall_01_47|BlocksPlacement||CanScrap||CustomName|Mall Decoration|IsLow||solidtrans|"));
        check(!model(planter).equals("mall_table_quarter"), "a planter on the same sheet is no table: " + model(planter));
        Furniture unnamed = Furniture.known(TileRows.sprite("location_shop_mall_01_52" + row));
        check(!model(unnamed).equals("mall_table_quarter"), "nor is a sprite of the sheet past the table's four: " + model(unnamed));
    }

    private static void eachSquaresGoodsStandOnThatSquare() {
        Furniture[] squares = laid("location_shop_generic_01_", BEIGE, new int[] {15, 14, 13, 12}, "E", new String[] {"|solidtrans|", "|solidtrans|", "|solidtrans|", ""});
        float[] at = new float[2];
        for (int i = 0; i < squares.length; i++) {
            Furniture square = squares[i];
            if (square == null) {
                continue;
            }
            ShopFittings.middleOf(square, 10, 20 + i, at);
            check(Math.abs(at[1] - (20 + i + 0.5f)) < 1e-4f, "square " + i + "'s goods stand in its middle along the run: " + at[1]);
            check(Math.abs(at[0] - (10 + square.centreX)) < 1e-4f, "and where the run is across it: " + at[0]);
        }
        Furniture[] run = laid("location_shop_generic_01_", LONG, new int[] {40, 41, 42, 43}, "S", new String[] {"|container|clothingrack|solidtrans|",
            "|container|clothingrack|solidtrans|", "|container|clothingrack|solidtrans|", ""});
        ShopFittings.middleOf(run[2], 32, 7, at);
        check(Math.abs(at[0] - 32.5f) < 1e-4f && Math.abs(at[1] - 7.5f) < 1e-4f, "a long run's third square's goods are on that square, wherever the run starts: "
                + at[0] + ", " + at[1]);
    }
}

package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import zombie.iso.sprite.IsoSprite;

/**
 * A cake shop's glass case: which sprites are one, how it stands on its two squares, where its colours are taken, where
 * its cakes stand, and that the glass DisplayCases lays is where the model has its frame. The rows are the tiles' own,
 * sounds left out.
 */
final class DisplayCaseTests {
    private DisplayCaseTests() {}

    static void run() {
        Checks.run("eachSquareOfACakeCaseIsTheCaseWhicheverWayItFaces", DisplayCaseTests::eachSquareOfACakeCaseIsTheCaseWhicheverWayItFaces);
        Checks.run("theCasesBesideItAreNotIt", DisplayCaseTests::theCasesBesideItAreNotIt);
        Checks.run("theGlassAndTheCakesAreWhereTheModelHasRoomForThem", DisplayCaseTests::theGlassAndTheCakesAreWhereTheModelHasRoomForThem);
        Checks.run("eachSquaresCakesStandOnThatSquare", DisplayCaseTests::eachSquaresCakesStandOnThatSquare);
    }

    private static final String ROUNDED = "|BlocksPlacement||CanBreak||CanScrap||ContainerCapacity|20|CustomName|Counter|GroupName|Rounded Glass Display|IsMoveAble||"
            + "ItemHeight|38|Material|Wood|Material2|Nails|Material3|Glass|MaterialType|Glass_Light|PickUpLevel|1|PickUpTool|Hammer|PickUpWeight|100|"
            + "PlaceTool|Hammer|container|displaycasebakery|solidtrans||Facing|";
    private static final String PIE = "|BlocksPlacement||CanBreak||CanScrap||ContainerCapacity|20|CustomName|Stand|GroupName|Glass Display|IsLow||IsMoveAble||"
            + "ItemHeight|38|Material|Wood|Material2|Nails|Material3|Glass|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|100|PlaceTool|Hammer|"
            + "container|displaycasebakery|solidtrans||Facing|";
    private static final String SEAHORSE = "|BlocksPlacement||CanBreak||CanScrap||ContainerCapacity|20|CustomName|Display|GroupName|Seahorse Coffee Glass|IsLow||"
            + "IsMoveAble||ItemHeight|40|Material|Glass|Material2|Wood|Material3|Nails|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|150|PlaceTool|Hammer|"
            + "container|restaurantdisplay|solidtrans||Facing|";

    /** One case: its sheet's row, the sprite numbers of its square at SpriteGridPos 0,0 and of its other square, and its facing. */
    private static Furniture[] laid(String sheet, String row, int first, int second, String facing) {
        boolean alongY = facing.equals("N") || facing.equals("S");
        IsoSprite one = TileRows.sprite(sheet + first + row + facing + "|SpriteGridPos|0,0");
        IsoSprite other = TileRows.sprite(sheet + second + row + facing + "|SpriteGridPos|" + (alongY ? "1,0" : "0,1"));
        TileRows.piece(alongY ? 2 : 1, alongY ? 1 : 2, one, other);
        return new Furniture[] {Furniture.known(one), Furniture.known(other)};
    }

    private static String model(Furniture piece) {
        return piece == null ? "nothing" : piece.model == null ? "no model" : piece.model.name;
    }

    private static void eachSquareOfACakeCaseIsTheCaseWhicheverWayItFaces() {
        Object[][] cases = {
            {"location_shop_generic_01_", ROUNDED, 34, 35, "S", 2}, {"location_shop_generic_01_", ROUNDED, 33, 32, "E", 1},
            {"location_shop_generic_01_", ROUNDED, 114, 115, "N", 0}, {"location_shop_generic_01_", ROUNDED, 113, 112, "W", 3},
            {"location_restaurant_pie_01_", PIE, 50, 51, "S", 2}, {"location_restaurant_pie_01_", PIE, 49, 48, "E", 1},
            {"location_restaurant_seahorse_01_", SEAHORSE, 56, 57, "S", 2}, {"location_restaurant_seahorse_01_", SEAHORSE, 59, 58, "E", 1},
            {"location_restaurant_seahorse_01_", SEAHORSE, 62, 63, "N", 0}, {"location_restaurant_seahorse_01_", SEAHORSE, 61, 60, "W", 3},
        };
        for (Object[] one : cases) {
            String which = one[0] + "" + one[2] + " facing " + one[4];
            Furniture[] squares = laid((String) one[0], (String) one[1], (int) one[2], (int) one[3], (String) one[4]);
            for (Furniture square : squares) {
                check(model(square).equals("bakery_case") && square.displayCase, "each square of " + which + " is the cake case: " + model(square));
                if (square == null || square.model == null) {
                    continue;
                }
                check(square.turns == (int) one[5], which + " faces the way its sprite does: " + square.turns);
                check(square.scale == Furniture.TILES_PER_METRE && square.scaleUp == Furniture.TILES_PER_METRE, which + " is as big as its model, which fills its squares");
                boolean alongX = square.width == 2;
                check(square.dyeX == (alongX ? 1 : 0) && square.dyeY == (alongX ? 0 : 1),
                        which + " takes its colours from its far square, whose end the game's view sees");
                check(square.dyed == 1 << Models.WOOD && square.alsoDyed == 1 << Models.WORKTOP && !Float.isNaN(square.alsoA),
                        which + " takes its body's colour and its top's from two places on that square");
                check(square.dyeA >= 0f && square.dyeA <= 1f && square.dyeB >= 0f && square.dyeB <= 1f && square.alsoA >= 0f && square.alsoA <= 1f
                        && square.alsoB >= 0f && square.alsoB <= 1f, which + " takes them from places on that square: " + square.dyeA + ", " + square.dyeB
                        + " and " + square.alsoA + ", " + square.alsoB);
                // The far end is the square's edge where the map's x or y grows.
                check((alongX ? square.dyeA : square.dyeB) > 0.95f, which + " takes its body's colour from its end, at the square's far edge");
            }
        }
    }

    private static void theCasesBesideItAreNotIt() {
        String butcher = "|BlocksPlacement||CanBreak||CanScrap||CustomName|Counter|Facing|S|GroupName|White Display|IsFridge||IsMoveAble||ItemHeight|34|"
                + "Material|Wood|Material2|Nails|Material3|Glass|MaterialType|Glass_Light|PickUpLevel|1|PickUpTool|Hammer|PickUpWeight|100|PlaceTool|Hammer|"
                + "container|displaycasebutcher|solidtrans||SpriteGridPos|";
        IsoSprite[] three = {TileRows.sprite("location_shop_generic_01_80" + butcher + "0,0"), TileRows.sprite("location_shop_generic_01_81" + butcher + "1,0"),
            TileRows.sprite("location_shop_generic_01_82" + butcher + "2,0")};
        TileRows.piece(3, 1, three);
        check(!model(Furniture.known(three[0])).equals("bakery_case"), "a butcher's case three squares long is not the cake case");
        Furniture trapezoid = Furniture.known(TileRows.sprite("location_shop_greenes_01_28|BlocksPlacement||CanBreak||CanScrap||ContainerCapacity|20|"
                + "CustomName|Counter|Facing|S|GroupName|Grocery Trapezoid|IsLow||IsMoveAble||ItemHeight|34|Material|Wood|PickUpLevel|2|PickUpTool|Hammer|"
                + "PickUpWeight|100|PlaceTool|Hammer|ScrapSize|Small|container|displaycasebakery|solidtrans|"));
        check(!model(trapezoid).equals("bakery_case"), "nor is a grocer's open stand for cakes on one square: " + model(trapezoid));
        Furniture cantina = Furniture.known(TileRows.sprite("location_restaurant_generic_01_16|BlocksPlacement||CanScrap||CustomName|Counter|Facing|E|"
                + "GroupName|Cantina Food|IsLow||IsMoveAble||Material|MetalPlates|Material2|MetalScrap|Material3|Electric|MaterialType|Glass_Light|"
                + "PickUpLevel|1|PickUpTool|Electrician|PickUpWeight|40|PlaceTool|Electrician|container|restaurantdisplay|solidtrans|"));
        check(model(cantina).equals("serving_counter"), "and a canteen's counter is still its serving counter: " + model(cantina));
        Furniture jewellers = Furniture.known(TileRows.sprite("location_shop_generic_01_97|BlocksPlacement||CanBreak||CanScrap||ContainerCapacity|20|"
                + "CustomName|Counter|Facing|S|GenericCraftingSurface|true|GroupName|Glass Display|IsLow||IsMoveAble||IsTable||Material|MetalScrap|"
                + "Material2|Glass|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|100|PlaceTool|Hammer|ScrapSize|Small|Surface|34|container|displaycase|solidtrans|"));
        check(!model(jewellers).equals("bakery_case"), "nor a shop's glass counter on one square: " + model(jewellers));
    }

    private static void theGlassAndTheCakesAreWhereTheModelHasRoomForThem() {
        Models.Model glassCase = Models.get("bakery_case");
        float near = 1e-3f;
        check(Math.abs(glassCase.minX + DisplayCases.HALF) < near && Math.abs(glassCase.maxX - DisplayCases.HALF) < near
                && Math.abs(glassCase.minY - DisplayCases.FRONT) < near && Math.abs(glassCase.maxY - DisplayCases.BACK) < near
                && Math.abs(glassCase.minZ) < near && Math.abs(glassCase.maxZ - DisplayCases.TOP) < near,
                "the case's model is as long, as deep and as high as the numbers its glass is laid by: x " + glassCase.minX + " to " + glassCase.maxX
                        + ", y " + glassCase.minY + " to " + glassCase.maxY + ", z " + glassCase.minZ + " to " + glassCase.maxZ);
        for (String name : new String[] {"pastries_many_a", "pastries_many_b", "pastries_few_a", "pastries_few_b"}) {
            Models.Model cakes = Models.get(name);
            check(Math.abs(cakes.minZ) < near, name + " stands on its foot, which is put on the case's deck");
            check(cakes.minX >= -0.5f && cakes.maxX <= 0.5f, name + " keeps to its own square along the case: x " + cakes.minX + " to " + cakes.maxX);
            // Inside the glass at the deck, behind the shelf's front edge above it, and under the top board.
            check(cakes.minY > DisplayCases.FRONT + 0.03f && cakes.maxY < DisplayCases.BACK - 0.04f,
                    name + " stands inside the case's glass and its back doors: y " + cakes.minY + " to " + cakes.maxY);
            check(DisplayCases.DECK + cakes.maxZ < DisplayCases.UNDER, name + " is under the case's top: " + (DisplayCases.DECK + cakes.maxZ));
        }
    }

    private static void eachSquaresCakesStandOnThatSquare() {
        Furniture[] along = laid("location_shop_generic_01_", ROUNDED, 34, 35, "S");
        float[] middle = new float[2];
        for (int i = 0; i < 2; i++) {
            DisplayCases.middleOf(along[i], 10 + i, 20, middle);
            check(middle[0] > 10 + i + 0.4f && middle[0] < 10 + i + 0.6f, "a square of a case along x has its cakes over itself: " + middle[0]);
            check(Math.abs(middle[1] - (20 + along[i].centreY)) < 1e-5f, "and as far back as the case is: " + middle[1]);
        }
        Furniture[] down = laid("location_restaurant_pie_01_", PIE, 49, 48, "E");
        for (int i = 0; i < 2; i++) {
            DisplayCases.middleOf(down[i], 10, 20 + i, middle);
            check(middle[1] > 20 + i + 0.4f && middle[1] < 20 + i + 0.6f, "a square of a case along y has its cakes over itself: " + middle[1]);
            check(Math.abs(middle[0] - (10 + down[i].centreX)) < 1e-5f, "and as far back as the case is: " + middle[0]);
        }
    }
}

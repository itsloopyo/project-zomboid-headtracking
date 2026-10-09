package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.HashMap;
import java.util.Map;
import zombie.core.properties.PropertyContainer;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteGrid;

/**
 * What a sprite of a room is made, asked as the build asks it: Furniture.known, with the
 * sprite's properties as the game's tile definitions give them (sounds left out). The rows are
 * the tiles' own, so a rule that reads a property the game spells otherwise fails here; one
 * changed to reach a rule's other side says what it changed.
 */
final class FurnitureTests {
    private FurnitureTests() {}

    static void run() {
        Checks.run("aHoodHangsOverTheCookerOnItsWall", FurnitureTests::aHoodHangsOverTheCookerOnItsWall);
        Checks.run("aBathTwoSquaresLongTheWayItFacesIsABathWithItsTapsAtItsBack", FurnitureTests::aBathTwoSquaresLongTheWayItFacesIsABathWithItsTapsAtItsBack);
        Checks.run("aBoxOnABoxStandsOnTheOneUnderIt", FurnitureTests::aBoxOnABoxStandsOnTheOneUnderIt);
        Checks.run("aFirstAidBoxIsNoMirrorCupboard", FurnitureTests::aFirstAidBoxIsNoMirrorCupboard);
        Checks.run("aStripLightIsLeftToItsSprite", FurnitureTests::aStripLightIsLeftToItsSprite);
        Checks.run("aBenchIsAsHighAsItsSeatAndAsLongAsItsSquares", FurnitureTests::aBenchIsAsHighAsItsSeatAndAsLongAsItsSquares);
        Checks.run("ventsAreToldByTheirPlaceOnTheSheet", FurnitureTests::ventsAreToldByTheirPlaceOnTheSheet);
        Checks.run("binsGoByWhatTheyHoldWhereverTheyStand", FurnitureTests::binsGoByWhatTheyHoldWhereverTheyStand);
        Checks.run("aSteelCounterIsSteelAndAWoodenOneIsNot", FurnitureTests::aSteelCounterIsSteelAndAWoodenOneIsNot);
        Checks.run("floorLampsAreToldByTheirPlace", FurnitureTests::floorLampsAreToldByTheirPlace);
        Checks.run("aBunkBedIsSteelOrWoodByItsGroup", FurnitureTests::aBunkBedIsSteelOrWoodByItsGroup);
        Checks.run("aLetteredSignHangsTheModsPictureOnlyWhereItHasOne", FurnitureTests::aLetteredSignHangsTheModsPictureOnlyWhereItHasOne);
        Checks.run("aDeckChairFacesTheWayItsSpriteDoesOnTheSquaresTheGameLaysItOn", FurnitureTests::aDeckChairFacesTheWayItsSpriteDoesOnTheSquaresTheGameLaysItOn);
        Checks.run("eachSquareOfAStreetBenchIsABenchOfItsOwn", FurnitureTests::eachSquareOfAStreetBenchIsABenchOfItsOwn);
        Checks.run("whatStandsOnARoofCaseIsRaisedToItsTop", FurnitureTests::whatStandsOnARoofCaseIsRaisedToItsTop);
    }

    private static IsoSprite sprite(String row) {
        return TileRows.sprite(row);
    }

    private static void piece(int width, int height, IsoSprite... squares) {
        TileRows.piece(width, height, squares);
    }

    private static String model(Furniture piece) {
        return piece == null ? "nothing" : piece.model == null ? "no model" : piece.model.name;
    }

    private static final float SURFACE_TILES = ViewCamera.LEVEL_HEIGHT / WorldScene.SURFACE_PIXELS_PER_LEVEL;

    private static void aHoodHangsOverTheCookerOnItsWall() {
        Furniture hood = Furniture.known(sprite("appliances_cooking_01_68|BlocksPlacement||CanScrap||CustomName|Hood|Facing|E|GroupName|Extractor|IsHigh||IsMoveAble||"
                + "Material|Electric|Material2|SmallMetalPlates|MaterialType|Metal|MoveType|WallObject|PickUpTool|Electrician|PlaceTool|Electrician|"
                + "WallObjectAllowDoorframe||attachedCeiling|"));
        check(model(hood).equals("range_hood"), "a kitchen's hood is the hood's model: " + model(hood));
        if (hood == null || hood.model == null) {
            return;
        }
        check(hood.raised > 1.2f && hood.raised + hood.model.height() * hood.scaleUp <= ViewCamera.LEVEL_HEIGHT,
                "it hangs over a cooker's back with its duct under the ceiling, not on the floor: foot at " + hood.raised);
        check(hood.turns == 1, "and faces the way its sprite does: " + hood.turns + " quarter turns");
        float[] front = new float[2];
        Models.turn(hood.turns, 0f, -1f, front);
        check((hood.centreX - hood.width / 2f) * front[0] + (hood.centreY - hood.height / 2f) * front[1] < -0.1f,
                "with its back to the wall behind its facing, not in the middle of the square");
    }

    private static void aBathTwoSquaresLongTheWayItFacesIsABathWithItsTapsAtItsBack() {
        String row = "|BlocksPlacement||CanScrap||CustomName|Bath|Facing|S|GroupName|Large Deluxe|IsMoveAble||ItemHeight|22|Material|Plumbing|Material2|Pipes|"
                + "PickUpTool|Wrench|PickUpWeight|100|PlaceTool|Wrench|solidtrans||SpriteGridPos|";
        IsoSprite head = sprite("fixtures_bathroom_01_25" + row + "0,0"), foot = sprite("fixtures_bathroom_01_24" + row + "0,1|IsGridExtensionTile|");
        piece(1, 2, head, foot);
        for (IsoSprite square : new IsoSprite[] {head, foot}) {
            Furniture bath = Furniture.known(square);
            check(model(bath).equals("bath"), "each square of a deluxe bath two squares long the way it faces is the bath: " + model(bath));
            check(bath != null && bath.turns == (2 + 3 & 3), "turned so its taps are at its back: " + (bath == null ? "-" : bath.turns));
        }
        IsoSprite left = sprite("fixtures_bathroom_01_25" + row + "0,0"), right = sprite("fixtures_bathroom_01_24" + row + "1,0");
        piece(2, 1, left, right);
        Furniture across = Furniture.known(left);
        check(model(across).equals("bath") && across.turns == 2, "a bath across the way it faces is the bath as it faces, as before");
    }

    private static void aBoxOnABoxStandsOnTheOneUnderIt() {
        String row = "|BlocksPlacement||CanScrap||ContainerCapacity|25|ContainerPosition|Low|CustomName|Cartbox|IsMoveAble||IsSurfaceOffset||"
                + "ItemHeight|64|Material|Paper|PickUpWeight|20|StackReplaceTileOffset|-8|Surface|32|container|cardboardbox|solidtrans|";
        Furniture onTheFloor = Furniture.known(sprite("furniture_storage_02_25" + row));
        Furniture onABox = Furniture.known(sprite("furniture_storage_02_24" + row + "|IsTableTop|"));
        check(model(onTheFloor).equals("carton") && model(onABox).equals("carton"), "a carton is the carton: " + model(onTheFloor) + ", " + model(onABox));
        if (onTheFloor == null || onABox == null || onTheFloor.model == null) {
            return;
        }
        float top = 32 * SURFACE_TILES;
        check(onTheFloor.raised == 0f, "one on the floor stands on the floor");
        check(Math.abs(onTheFloor.model.height() * onTheFloor.scaleUp - top) < 1e-4f,
                "as high as the game says its top is: " + onTheFloor.model.height() * onTheFloor.scaleUp + " against " + top);
        check(Math.abs(onABox.raised - top) < 1e-4f, "and one the game puts on a box stands on that box's top, not inside it: " + onABox.raised);
        check(onTheFloor.turns != Furniture.known(sprite("furniture_storage_02_26" + row)).turns, "the sheet's four boxes are the box turned");
    }

    private static void aFirstAidBoxIsNoMirrorCupboard() {
        String row = "|CanBreak||CanScrap||ContainerCapacity|10|ContainerPosition|High|CustomName|Cabinet|Facing|S|IsHigh||IsMoveAble||Material|Wood|"
                + "Material2|Mirror|MoveType|WallObject|PickUpWeight|100|ScrapSize|Small|Surface|80|attachedN||container|medicine|GroupName|";
        check(!model(Furniture.known(sprite("location_community_medical_01_88" + row + "First Aid"))).equals("medicine_cabinet"),
                "a first aid box holds medicine and is not the cupboard with a mirror for a door");
        Furniture cupboard = Furniture.known(sprite("fixtures_bathroom_01_28|CanBreak||CanScrap||ContainerCapacity|10|ContainerPosition|High|CustomName|Cabinet|"
                + "Facing|S|GroupName|Medicine|IsHigh||IsMirror||IsMoveAble||Material|Wood|Material2|Mirror|MoveType|WallObject|PickUpLevel|2|PickUpTool|Hammer|"
                + "PickUpWeight|100|PlaceTool|Hammer|ScrapSize|Small|Surface|80|attachedN||container|medicine"));
        check(model(cupboard).equals("medicine_cabinet"), "a medicine cupboard is: " + model(cupboard));
        check(cupboard != null && cupboard.raised > 1f, "hung high on its wall: " + (cupboard == null ? "-" : cupboard.raised));
    }

    private static void aStripLightIsLeftToItsSprite() {
        Furniture strip = Furniture.known(sprite("lighting_indoor_02_44|BlocksPlacement||CanScrap||CustomName|Wall Light|Facing|E|GenericCraftingSurface|false|"
                + "GroupName|Fluorescent|HasLightOnSprite||IsHigh||IsMoveAble||LightRadius|6|Material|Electric|MoveType|WallObject|PickUpWeight|40|"
                + "ScrapSize|Small|WallObjectAllowDoorframe||attachedW||lightB|130|lightG|130|lightR|115|lightswitch|"));
        check(strip == null, "a strip light on a wall is no model: its sprite is drawn, " + model(strip));
        String lamp = "|BlocksPlacement||CanScrap||CustomName|Wall Lamp|Facing|E|HasLightOnSprite||IsHigh||IsMoveAble||LightRadius|6|Material|Electric|"
                + "MoveType|WallObject|PickUpLevel|1|PickUpTool|Hammer|PickUpWeight|20|PlaceTool|Hammer|ScrapSize|Small|attachedW||lightswitch||GroupName|";
        Furniture bar = Furniture.known(sprite("lighting_indoor_02_80" + lamp + "Bar"));
        check(!model(bar).equals("sconce"), "nor is a bar of a lamp a sconce: " + model(bar));
        Furniture sconce = Furniture.known(sprite("lighting_indoor_01_16" + lamp + "Pink"));
        check(model(sconce).equals("sconce") && sconce.wall != WallHung.NONE, "where a lamp on a wall with a shade is a sconce hung on its wall: " + model(sconce));
    }

    private static void aBenchIsAsHighAsItsSeatAndAsLongAsItsSquares() {
        String row = "|BedType|badBed|BlocksPlacement||CanBreak||CanScrap||CustomName|Bench|Facing|E|GroupName|Sturdy|IsLow||IsMoveAble||Material|Wood|"
                + "Material2|Nails|Material3|Fabric|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|150|PlaceTool|Hammer|ScrapSize|Small|Surface|17|bed||chairE||SpriteGridPos|";
        IsoSprite first = sprite("furniture_seating_indoor_02_17" + row + "0,0"), second = sprite("furniture_seating_indoor_02_16" + row + "0,1");
        piece(1, 2, first, second);
        Furniture bench = Furniture.known(first);
        check(model(bench).equals("plank_bench"), "a bench of planks two squares long is the bench: " + model(bench));
        if (bench == null || bench.model == null) {
            return;
        }
        check(bench.scale == Furniture.TILES_PER_METRE, "as long along the ground as the model is, not shrunk to reach its seat: " + bench.scale);
        // The model's seat is 0.45 m over the floor: only its height is made as high as the game's seat, 17 pixels of a level.
        check(Math.abs(0.45f * bench.scaleUp - 17 * SURFACE_TILES) < 1e-4f, "only its height is made what the game says the seat is: " + 0.45f * bench.scaleUp);
        IsoSprite stone = sprite("furniture_seating_indoor_02_17" + row.replace("Sturdy", "Park") + "0,0"), other = sprite("furniture_seating_indoor_02_16" + row + "0,1");
        piece(1, 2, stone, other);
        check(!model(Furniture.known(stone)).equals("plank_bench"), "the same bench grouped as a park's is not planks on two legs");
    }

    private static void ventsAreToldByTheirPlaceOnTheSheet() {
        String row = "|CanScrap||CustomName|Conditioner|Facing|S|GroupName|Air|IsMoveAble||Material|MetalScrap|MoveType|WallObject|PickUpTool|Hammer|"
                + "PickUpWeight|25|PlaceTool|Hammer|ScrapSize|Small|attachedN||IsHigh|";
        String[][] places = {{"17", "wall_vent"}, {"18", "wall_vent"}, {"32", "wall_grille"}, {"35", "wall_grille"}, {"36", "round_vent"}, {"37", "round_vent"}};
        for (String[] place : places) {
            Furniture vent = Furniture.known(sprite("rooftop_furniture_" + place[0] + row));
            check(model(vent).equals(place[1]) && vent.wall != WallHung.NONE, "rooftop_furniture_" + place[0] + " is a " + place[1] + " on its wall: " + model(vent));
        }
        check(Furniture.known(sprite("rooftop_furniture_20" + row)) == null, "and another of the sheet's conditioners is none of them");
        Furniture unit = Furniture.known(sprite("industry_01_4|CanScrap||CustomName|Conditioner|Facing|S|GroupName|Air|IsHigh||IsMoveAble||Material|MetalScrap|"
                + "Material2|Electric|MoveType|WallObject|PickUpTool|Hammer|PickUpWeight|25|PlaceTool|Hammer|ScrapSize|Small|attachedN|"));
        check(model(unit).equals("air_conditioner"), "a works' air conditioner of the same name is the case let through its wall: " + model(unit));
    }

    private static void binsGoByWhatTheyHoldWhereverTheyStand() {
        Furniture bin = Furniture.known(sprite("trashcontainers_01_17|BlocksPlacement||CanScrap||ContainerCapacity|20|CustomItem|Base.Mov_GreenGarbageBin|"
                + "CustomName|Green Garbage Bin|IsLow||IsMoveAble||IsTrashCan||Material|MetalScrap|Material2|PlasticBag|PickUpWeight|100|container|bin|solidtrans|"));
        check(model(bin).equals("garbage_can"), "a green garbage bin is the can it is, indoors or out: " + model(bin));
        Furniture basket = Furniture.known(sprite("trashcontainers_01_20|BlocksPlacement||CanScrap||ContainerCapacity|10|CustomName|Round Bin|"
                + "FitsBeneathCountertop||IsLow||IsMoveAble||IsTrashCan||Material|MetalScrap|Material2|PlasticBag|PickUpWeight|50|container|bin"));
        check(model(basket).equals("waste_basket"), "a round bin is the waste basket: " + model(basket));
    }

    private static void aSteelCounterIsSteelAndAWoodenOneIsNot() {
        String row = "|BlocksPlacement||CanBreak||CanScrap||ContainerPosition|Low|CountertopAttach||CustomName|Counter|Facing|N|GenericCraftingSurface|true|"
                + "IsLow||IsMoveAble||IsTable||Material|MetalPlates|Material2|Screws|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|200|PlaceTool|Hammer|"
                + "Surface|35|container|counter|solidtrans||GroupName|";
        Furniture steel = Furniture.known(sprite("fixtures_counters_01_33" + row + "Steel"));
        check(model(steel).equals("steel_counter"), "a counter the game groups as steel is steel all over: " + model(steel));
        check(steel != null && steel.withBasin != null && steel.withBasin.name.equals("steel_counter_sink"), "and takes a steel sink in its top");
        Furniture corner = Furniture.known(sprite("fixtures_counters_01_32" + row + "Steel Corner"));
        check(model(corner).equals("steel_counter_corner") && corner.withBasin == null, "its corner is the steel corner, with no sink: " + model(corner));
        Furniture wood = Furniture.known(sprite("fixtures_counters_01_1" + row.replace("MetalPlates|Material2|Screws", "Wood|Material2|Nails") + "Modern"));
        check(!model(wood).startsWith("steel"), "a counter of another group is not steel: " + model(wood));
    }

    private static void floorLampsAreToldByTheirPlace() {
        String row = "|BlocksPlacement||CanScrap||CustomName|Lights|GroupName|Lamp on|HasLightOnSprite||IsMoveAble||LightRadius|8|Material|MetalPipe|"
                + "Material2|Electric|PickUpWeight|100|lightswitch|";
        check(model(Furniture.known(sprite("lighting_indoor_01_58" + row))).equals("standard_lamp"), "the lamp with a shade is the standard lamp");
        check(model(Furniture.known(sprite("lighting_indoor_01_56" + row))).equals("torchiere"), "and the others under that name are uplighters");
    }

    private static void aBunkBedIsSteelOrWoodByItsGroup() {
        String row = "|BedType|goodBed|BlocksPlacement||CanBreak||CanScrap||CustomName|Beds|Facing|S|IsMoveAble||Material|Nails|Material2|Wood|"
                + "Material3|Fabric|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|200|PlaceTool|Hammer|Surface|26|bed||solidtrans||SpriteGridPos|";
        IsoSprite steelHead = sprite("furniture_bedding_01_85" + row + "0,0|GroupName|Bunk"), steelFoot = sprite("furniture_bedding_01_84" + row + "0,1|GroupName|Bunk");
        piece(1, 2, steelHead, steelFoot);
        check(model(Furniture.known(steelFoot)).equals("steel_bunk_bed"), "a bunk the game groups as Bunk is the steel one: " + model(Furniture.known(steelFoot)));
        IsoSprite woodHead = sprite("furniture_bedding_01_39" + row + "0,0|GroupName|Twin"), woodFoot = sprite("furniture_bedding_01_38" + row + "0,1|GroupName|Twin");
        piece(1, 2, woodHead, woodFoot);
        check(model(Furniture.known(woodHead)).equals("bunk_bed"), "any other bunk the wooden one: " + model(Furniture.known(woodHead)));
    }

    private static void aDeckChairFacesTheWayItsSpriteDoesOnTheSquaresTheGameLaysItOn() {
        String row = "|BedType|badBed|BlocksPlacement||CanScrap||CustomName|Chair|ForceSingleItem||GroupName|Beach|Surface|17|Facing|";
        // Each facing's two sprites, the first at SpriteGridPos 0,0, and how many quarter turns from north the chair is.
        Object[][] facings = {{"N", 29, 28, 0}, {"E", 26, 27, 1}, {"S", 25, 24, 2}, {"W", 30, 31, 3}};
        for (Object[] facing : facings) {
            boolean alongY = facing[0].equals("N") || facing[0].equals("S");
            IsoSprite first = sprite("furniture_seating_outdoor_01_" + facing[1] + row + facing[0] + "|SpriteGridPos|0,0");
            IsoSprite second = sprite("furniture_seating_outdoor_01_" + facing[2] + row + facing[0] + "|SpriteGridPos|" + (alongY ? "0,1" : "1,0"));
            piece(alongY ? 1 : 2, alongY ? 2 : 1, first, second);
            Furniture chair = Furniture.known(second);
            check(model(chair).equals("deck_chair") && chair.turns == (int) facing[3],
                    "a deck chair facing " + facing[0] + " is the chair turned " + facing[3] + " quarters from north: " + model(chair) + " " + (chair == null ? "" : chair.turns));
        }
        IsoSprite across = sprite("furniture_seating_outdoor_01_26" + row + "E|SpriteGridPos|0,0"), other = sprite("furniture_seating_outdoor_01_27" + row + "E|SpriteGridPos|0,1");
        piece(1, 2, across, other);
        check(Furniture.known(across) == null, "one the game lays across the way it faces is no deck chair");
    }

    private static void eachSquareOfAStreetBenchIsABenchOfItsOwn() {
        String row = "|BlocksPlacement||CanScrap||CustomName|Bench|Facing|S|ForceSingleItem||GroupName|Birchwood|IsLow||IsMoveAble||Material|Nails|Material2|Wood|"
                + "PickUpWeight|150|ScrapSize|Small|Surface|14|bed||SpriteGridPos|";
        IsoSprite left = sprite("furniture_seating_outdoor_01_32" + row + "0,0"), right = sprite("furniture_seating_outdoor_01_33" + row + "1,0");
        piece(2, 1, left, right);
        for (IsoSprite square : new IsoSprite[] {left, right}) {
            Furniture bench = Furniture.known(square);
            check(model(bench).equals("slab_bench") && bench.grid == null && bench.width == 1 && bench.turns == 2,
                    "each square of a street bench two long is a bench a square long, facing south");
        }
        Furniture alone = Furniture.known(sprite("furniture_seating_outdoor_01_32" + row + "0,0"));
        check(model(alone).equals("slab_bench") && alone.grid == null, "and one standing alone is the same bench");
    }

    private static void whatStandsOnARoofCaseIsRaisedToItsTop() {
        Furniture fan = Furniture.known(sprite("rooftop_furniture_23|attachedCeiling|"));
        Furniture lowCase = Furniture.known(sprite("rooftop_furniture_1|BlocksPlacement||CanScrap||ItemHeight|45|Material|Mechanical|Material2|MetalScrap|"
                + "MaterialType|Metal_Light|ScrapSize|Large|solid|"));
        check(lowCase != null && lowCase.raised == 0f && lowCase.model != null, "a case on a roof stands on the roof: " + model(lowCase));
        check(fan != null && fan.model != null && lowCase != null && Math.abs(fan.raised - lowCase.model.height() * lowCase.scaleUp) < 0.05f,
                "what the map stands on the low case is raised to the case's top: " + (fan == null ? "nothing" : fan.raised));
    }

    private static void aLetteredSignHangsTheModsPictureOnlyWhereItHasOne() {
        String row = "|CanScrap||CustomName|Sign|Facing|S|ForceSingleItem||IsHigh||IsMoveAble||Material|Plastic|MoveType|WallObject|PickUpWeight|60|attachedN||";
        IsoSprite[] squares = new IsoSprite[4];
        for (int i = 0; i < 4; i++) {
            squares[i] = sprite("appliances_laundry_01_" + (16 + i) + row + "GroupName|Laundromat|SpriteGridPos|" + i + ",0");
        }
        piece(4, 1, squares);
        for (IsoSprite square : squares) {
            Furniture sign = Furniture.known(square);
            check(sign != null && sign.pinned && sign.lettered && sign.picture != Decor.NONE && sign.wall != WallHung.NONE,
                    "each square of the laundromat's sign hangs the mod's lettered picture on its wall");
        }
        check(Furniture.known(squares[1]).picture == Furniture.known(squares[3]).picture, "one picture for the whole sign");
        IsoSprite unlettered = sprite("construction_01_56|CustomName|Sign|Facing|S|ForceSingleItem||GroupName|TIS Construction|IsMoveAble||MoveType|WallObject|"
                + "PickUpWeight|20|SpriteGridPos|0,0|attachedN|");
        check(Furniture.known(unlettered) == null, "a sign the mod has no picture of is left to its sprite");
        IsoSprite facingAway = sprite("appliances_laundry_01_20" + row.replace("Facing|S", "Facing|N") + "GroupName|Laundromat");
        check(Furniture.known(facingAway) == null, "nor is a sign hung whose face the game's view never shows");
    }
}

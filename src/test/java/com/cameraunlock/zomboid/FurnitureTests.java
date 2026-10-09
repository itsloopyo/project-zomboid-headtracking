package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import zombie.iso.sprite.IsoSprite;

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
        Checks.run("eachDumpsterSpriteIsItsDumpsterOneModelOverBothSquaresFacingItsWay", FurnitureTests::eachDumpsterSpriteIsItsDumpsterOneModelOverBothSquaresFacingItsWay);
        Checks.run("aDinersTablesStandOnColumnsAlongTheWayTheyFace", FurnitureTests::aDinersTablesStandOnColumnsAlongTheWayTheyFace);
        Checks.run("aRestaurantsChairsFaceTheWayTheirSpritesDo", FurnitureTests::aRestaurantsChairsFaceTheWayTheirSpritesDo);
        Checks.run("restaurantBoothsAndWaitingChairsAreTheirOwnSeats", FurnitureTests::restaurantBoothsAndWaitingChairsAreTheirOwnSeats);
        Checks.run("aPoolTableIsOneModelOverItsTwoSquares", FurnitureTests::aPoolTableIsOneModelOverItsTwoSquares);
        Checks.run("homeFittingsStandAsTheirSpritesDo", FurnitureTests::homeFittingsStandAsTheirSpritesDo);
        Checks.run("aCountersTopIsABoardOverTheCountersOwnTop", FurnitureTests::aCountersTopIsABoardOverTheCountersOwnTop);
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

    private static void eachDumpsterSpriteIsItsDumpsterOneModelOverBothSquaresFacingItsWay() {
        // Sheet place, facing, colour, SpriteGridPos, and the place of the other square of the pair.
        String[][] sprites = {{"8", "E", "Blue", "0,1", "9"}, {"9", "E", "Blue", "0,0", "8"}, {"10", "S", "Blue", "0,0", "11"}, {"11", "S", "Blue", "1,0", "10"},
            {"12", "E", "Orange", "0,1", "13"}, {"13", "E", "Orange", "0,0", "12"}, {"14", "S", "Orange", "0,0", "15"}, {"15", "S", "Orange", "1,0", "14"},
            {"28", "W", "Blue", "0,1", "29"}, {"29", "W", "Blue", "0,0", "28"}, {"30", "N", "Blue", "0,0", "31"}, {"31", "N", "Blue", "1,0", "30"}};
        java.util.Map<String, IsoSprite> made = new java.util.HashMap<>();
        for (String[] place : sprites) {
            made.put(place[0], sprite("trashcontainers_01_" + place[0] + "|BlocksPlacement||CanScrap||ContainerCapacity|30|CustomName|Dumpster|Facing|" + place[1]
                    + "|GroupName|" + place[2] + "|IsMoveAble||IsTrashCan||Material|MetalPlates|Material2|MetalPlates|Material3|MetalScrap|MaterialType|Metal_Large|"
                    + "PickUpLevel|2|PickUpTool|Crowbar|PickUpWeight|300|SpriteGridPos|" + place[3] + "|" + (place[2].equals("Orange") ? "Surface|46|" : "")
                    + "container|dumpster|solid|"));
        }
        for (String[] place : sprites) {
            if (place[3].equals("0,0")) {
                boolean alongX = place[1].equals("S") || place[1].equals("N");
                piece(alongX ? 2 : 1, alongX ? 1 : 2, made.get(place[0]), made.get(place[4]));
            }
        }
        for (String[] place : sprites) {
            Furniture dumpster = Furniture.known(made.get(place[0]));
            String wanted = place[2].equals("Orange") ? "dumpster_domed" : "dumpster_sloped";
            String name = "trashcontainers_01_" + place[0];
            check(model(dumpster).equals(wanted), name + " is " + wanted + ": " + model(dumpster));
            if (dumpster == null || dumpster.model == null) {
                continue;
            }
            int turns = Furniture.turnsOf(zombie.iso.IsoDirections.valueOf(place[1]));
            check(dumpster.turns == turns, name + " faces " + place[1] + " as its sprite does: " + dumpster.turns + " quarter turns");
            Furniture other = Furniture.known(made.get(place[4]));
            check(other != null && other.model == dumpster.model && other.gridX + other.gridY != dumpster.gridX + dumpster.gridY && other.turns == turns,
                    name + " and its other square are the one model, stood once");
            check(dumpster.width * dumpster.height == 2 && (dumpster.width == 2) == ((turns & 1) == 0), name + "'s length lies along its two squares");
            float length = dumpster.model.width() * dumpster.scale, deep = dumpster.model.depth() * dumpster.scale;
            check(length > 1.6f && length <= 2f && deep > 0.8f && deep <= 1f, name + " is a dumpster's length and depth inside its squares: " + length + " by " + deep);
            check(dumpster.dyed == 1 << Models.PAINT, name + "'s paint is the colour its sprite paints it");
            // Measured on the game's sprites: low in the middle of the first square, trashcontainers_01_14 paints nothing, so
            // ready() found no colour and the orange dumpster facing south was left two wrapped boxes and a card of a lid.
            // Low on the south side every dumpster sprite paints its body.
            check(dumpster.dyeY == dumpster.height - 1 && dumpster.dyeX == 0 && dumpster.dyeA == 0.5f && dumpster.dyeB > 0.9f && dumpster.dyeB < 1f,
                    name + " takes its colour low on the south side the game's view sees: square " + dumpster.dyeX + "," + dumpster.dyeY
                    + " at " + dumpster.dyeA + "," + dumpster.dyeB);
            float high = dumpster.model.height() * dumpster.scaleUp;
            if (place[2].equals("Orange")) {
                check(Math.abs(high - 46 * SURFACE_TILES) < 0.03f, name + "'s lids crown as high as the game stands things on it: " + high + " against " + 46 * SURFACE_TILES);
            } else {
                check(high > 1.25f && high < 1.45f, name + " is as high as its sprite paints it: " + high);
            }
        }
    }

    private static void aPoolTableIsOneModelOverItsTwoSquares() {
        String row = "|BlocksPlacement||CanScrap||CustomName|Table|ForceSingleItem||GroupName|Pool|IsLow||IsMoveAble||IsTable||Material|Wood|Material2|Nails|"
                + "PickUpLevel|1|PickUpTool|Hammer|PickUpWeight|400|PlaceTool|Hammer|Surface|34|solidtrans||Facing|";
        IsoSprite south0 = sprite("recreational_01_2" + row + "S|SpriteGridPos|0,0"), south1 = sprite("recreational_01_3" + row + "S|SpriteGridPos|1,0");
        piece(2, 1, south0, south1);
        IsoSprite east0 = sprite("recreational_01_7" + row + "E|SpriteGridPos|0,0"), east1 = sprite("recreational_01_6" + row + "E|SpriteGridPos|0,1");
        piece(1, 2, east0, east1);
        for (IsoSprite square : new IsoSprite[] {south0, south1, east0, east1}) {
            Furniture table = Furniture.known(square);
            check(model(table).equals("pool_table"), "each half of a pool table is the pool table, not half a top over a card: " + model(table));
            if (table == null || table.model == null) {
                continue;
            }
            boolean alongX = table.width == 2;
            check((table.turns & 1) == (alongX ? 0 : 1), "its length lies along the squares it is laid on: " + table.turns + " quarter turns over " + table.width + " by " + table.height);
            check(table.model.width() * table.scale <= 2f && table.model.depth() * table.scale <= 1f,
                    "inside its two squares: " + table.model.width() * table.scale + " by " + table.model.depth() * table.scale);
            check(Math.abs(0.8f * table.scaleUp - 34 * SURFACE_TILES) < 1e-4f, "its cloth as high as the game says the table's top is: " + 0.8f * table.scaleUp);
            check(table.dyed == 1 << Models.CLOTH, "and its cloth the colour its sprite's cloth is");
        }
        check(Furniture.known(south0).gridX == 0 && Furniture.known(south1).gridX == 1, "one model, stood by the first square");
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

    private static void aDinersTablesStandOnColumnsAlongTheWayTheyFace() {
        String row = "|BlocksPlacement||CanScrap||CustomName|Table|Facing|E|GroupName|Spiffos Type B|IsLow||IsMoveAble||IsTable||Material|MetalPipe|Material2|Plastic|"
                + "PickUpTool|Crowbar|PickUpWeight|100|Surface|36|solidtrans||SpriteGridPos|";
        IsoSprite first = sprite("location_restaurant_spiffos_02_32" + row + "0,0"), second = sprite("location_restaurant_spiffos_02_33" + row + "1,0");
        piece(2, 1, first, second);
        float top = 36 * SURFACE_TILES;
        for (IsoSprite square : new IsoSprite[] {first, second}) {
            Furniture tables = Furniture.known(square);
            check(model(tables).equals("diner_tables"), "each square of a diner's pair of tables is the pair, two along the way it faces: " + model(tables));
        }
        Furniture pair = Furniture.known(first);
        if (pair == null || pair.model == null) {
            return;
        }
        check(pair.turns == 1, "facing east as its sprite does: " + pair.turns);
        check(Math.abs(pair.model.height() * pair.scaleUp - top) < 1e-3f, "its tops as high as the game says: " + pair.model.height() * pair.scaleUp + " against " + top);
        check(pair.dyed == 1 << Models.WORKTOP && pair.alsoDyed == 1 << Models.WOOD && pair.alsoHigh > pair.dyeHigh,
                "its laminate and the wooden band round it each take their colour from the sprite");
        String rowA = row.replace("Type B", "Type A").replace("Facing|E", "Facing|S");
        IsoSprite near = sprite("location_restaurant_spiffos_02_36" + rowA + "0,1"), far = sprite("location_restaurant_spiffos_02_37" + rowA + "0,0");
        piece(1, 2, near, far);
        Furniture longOne = Furniture.known(far);
        check(model(longOne).equals("diner_table_long") && longOne.turns == 2, "a long one lies along y facing south: " + model(longOne));
        String fifties = "|BlocksPlacement||CanBreak||CanScrap||CustomName|Table|Facing|S|GenericCraftingSurface|true|GroupName|50s Diner|IsLow||IsMoveAble||IsTable||"
                + "Material|Wood|Material2|Steel|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|150|PlaceTool|Hammer|Surface|36|bed||solidtrans||SpriteGridPos|";
        IsoSprite left = sprite("location_restaurant_diner_01_6" + fifties + "0,0"), right = sprite("location_restaurant_diner_01_7" + fifties + "1,0");
        piece(2, 1, left, right);
        check(model(Furniture.known(left)).equals("fifties_table"), "a fifties diner's table on one column is not the table on four legs: " + model(Furniture.known(left)));
        Furniture cafe = Furniture.known(sprite("location_restaurant_pileocrepe_01_36|BlocksPlacement||CanBreak||CanScrap||CustomName|Table|Facing|S|"
                + "GroupName|Light Wood Restaurant|IsLow||IsMoveAble||IsTable||Material|Wood|PickUpTool|Hammer|PickUpWeight|50|PlaceTool|Hammer|Surface|32|solidtrans|"));
        check(model(cafe).equals("cafe_table"), "a cafe's square table on a column is: " + model(cafe));
        check(cafe != null && cafe.model != null && Math.abs(cafe.model.height() * cafe.scaleUp - 32 * SURFACE_TILES) < 1e-3f, "as high as the game says its top is");
    }

    private static void aRestaurantsChairsFaceTheWayTheirSpritesDo() {
        String swivel = "|BedType|badBed|BlocksPlacement||CanScrap||CustomName|Chair|GroupName|Spiffos Dinner|IsLow||IsMoveAble||Material|MetalBars|PickUpWeight|50|"
                + "ScrapSize|Small|bed||SeatMaterial|Leather|Facing|";
        Furniture south = Furniture.known(sprite("location_restaurant_spiffos_02_24" + swivel + "S|chairS|"));
        Furniture north = Furniture.known(sprite("location_restaurant_spiffos_02_26" + swivel + "N|chairN|"));
        check(model(south).equals("swivel_chair") && model(north).equals("swivel_chair"), "a diner's chair is the chair that turns on its column: " + model(south));
        check(south != null && north != null && south.turns == 2 && north.turns == 0, "facing the way its sprite does");
        check(south != null && south.dyed == 1 << Models.VINYL, "its seat takes its colour from the sprite");
        String cafe = "|BlocksPlacement||CanScrap||CustomName|Chair|GroupName|Pile o' Crepe|IsLow||IsMoveAble||Material|Wood|PickUpWeight|50|ScrapSize|Small|bed||"
                + "SeatMaterial|Wooden|Facing|";
        Furniture east = Furniture.known(sprite("location_restaurant_pileocrepe_01_40" + cafe + "E"));
        Furniture west = Furniture.known(sprite("location_restaurant_pileocrepe_01_42" + cafe + "W"));
        check(model(east).equals("cafe_chair") && model(west).equals("cafe_chair"), "a cafe's wooden chair is the cafe chair: " + model(east));
        check(east != null && west != null && east.turns == 1 && west.turns == 3, "facing the way its sprite does");
        check(east != null && west != null && east.dyeA > 0.5f && west.dyeA < 0.5f,
                "its colour is taken from the front of its seat, clear of its back, whichever way it faces: " + (east == null ? "-" : east.dyeA));
    }

    private static void restaurantBoothsAndWaitingChairsAreTheirOwnSeats() {
        String spiffos = "|BedType|badBed|BlocksPlacement||CanBreak||CanScrap||CustomName|Seat|Facing|S|GroupName|Spiffos Lounge|IsLow||IsMoveAble||ItemHeight|28|"
                + "Material|Wood|Material2|Wood|Material3|Leather|MaterialType|Wood|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|100|PlaceTool|Hammer|"
                + "bed||chairS||solidtrans||SeatMaterial|Leather|SpriteGridPos|";
        String whirled = "|BedType|averageBed|BlocksPlacement||CanBreak||CanScrap||CustomName|Seat|Facing|S|GroupName|Whirled|IsLow||IsMoveAble||ItemHeight|18|"
                + "Material|Wood|Material2|Nails|Material3|Leather|MaterialType|Wood|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|150|PlaceTool|Hammer|"
                + "bed||solidtrans||SeatMaterial|Leather|SpriteGridPos|";
        String leather = "|BedType|averageBed|BlocksPlacement||CanBreak||CanScrap||CustomName|Seating|Facing|E|GroupName|Leather Bar|IsLow||IsMoveAble||ItemHeight|22|"
                + "Material|Leather|Material2|Wood|MaterialType|Wood|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|150|PlaceTool|Hammer|"
                + "bed||solidtrans||SeatMaterial|Leather|SpriteGridPos|";
        String waiting = "|BedType|badBed|BlocksPlacement||CanScrap||CustomName|Chairs|Facing|S|GroupName|Waiting|HoppableN||IsLow||IsMoveAble||Material|MetalPipe|"
                + "Material2|Foam|PickUpWeight|150|bed||chairS||collideN||solidtrans||SpriteGridPos|";
        String diner = "|BedType|averageBed|BlocksPlacement||CanBreak||CanScrap||CustomName|Seat|Facing|S|GroupName|50s|IsLow||IsMoveAble||ItemHeight|20|"
                + "Material|Leather|Material2|Wood|MaterialType|Wood|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|150|PlaceTool|Hammer|"
                + "bed||solidtrans||SeatMaterial|Leather|SpriteGridPos|";
        Object[][] pieces = {
            {"location_restaurant_spiffos_02_20", "location_restaurant_spiffos_02_21", spiffos, 2, 1, "lounge_booth", 2},
            {"location_restaurant_pizzawhirled_01_44", "location_restaurant_pizzawhirled_01_45", whirled, 2, 1, "lounge_booth", 2},
            {"location_restaurant_bar_01_13", "location_restaurant_bar_01_12", leather, 1, 2, "leather_booth", 1},
            {"location_community_medical_01_58", "location_community_medical_01_59", waiting, 2, 1, "waiting_chairs", 2},
            {"location_restaurant_diner_01_32", "location_restaurant_diner_01_33", diner, 2, 1, "booth", 2}};
        for (Object[] piece : pieces) {
            int width = (int) piece[3], height = (int) piece[4];
            IsoSprite first = sprite(piece[0] + (String) piece[2] + "0,0"), second = sprite(piece[1] + (String) piece[2] + (width == 2 ? "1,0" : "0,1"));
            piece(width, height, first, second);
            Furniture seat = Furniture.known(first);
            check(model(seat).equals(piece[5]) && seat.turns == (int) piece[6] && model(Furniture.known(second)).equals(piece[5]),
                    piece[0] + " and the square beside it are a " + piece[5] + " turned " + piece[6] + " quarters from north: " + model(seat));
            if (seat == null || seat.model == null) {
                continue;
            }
            check(seat.model.width() * seat.scale <= 2f && seat.model.depth() * seat.scale <= 1f, piece[0] + " is no bigger than its two squares");
        }
    }

    private static void homeFittingsStandAsTheirSpritesDo() {
        String china = "|BlocksPlacement||CanBreak||CanScrap||ContainerCapacity|25|CustomName|Cabinet|Facing|S|GroupName|China|IsMoveAble||Material|Nails|Material2|Wood|"
                + "MaterialType|Glass|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|200|PlaceTool|Hammer|Surface|76|attachedN||container|dishescabinet|solid||SpriteGridPos|";
        IsoSprite left = sprite("furniture_storage_02_34" + china + "0,0"), right = sprite("furniture_storage_02_35" + china + "1,0");
        piece(2, 1, left, right);
        Furniture cabinet = Furniture.known(left);
        check(model(cabinet).equals("china_cabinet") && model(Furniture.known(right)).equals("china_cabinet"),
                "each square of a china cabinet on its wall is the cabinet, not a picture of it laid on the wall: " + model(cabinet));
        check(cabinet != null && cabinet.wall == WallHung.NONE && cabinet.turns == 2, "standing on the floor, facing south as its sprite does");
        check(cabinet != null && cabinet.model != null && Math.abs(HomeFittings.CHINA_TOP * cabinet.scaleUp - 76 * SURFACE_TILES) < 1e-3f,
                "its cornice as high as the game says its top is");

        String fire = "|ContainerCapacity|20|CustomName|Fireplace|WallOverlay||container|fireplace|";
        Furniture north = Furniture.known(sprite("fixtures_fireplaces_01_0" + fire + "collideN|"));
        Furniture west = Furniture.known(sprite("fixtures_fireplaces_01_5" + fire + "collideW|"));
        check(model(north).equals("fireplace") && model(west).equals("fireplace"), "a fire in a wall is the fireplace, which the game gives no facing: " + model(north));
        check(north != null && west != null && north.turns == 2 && west.turns == 1, "facing out of the wall it is let into, south of a north wall and east of a west one");
        check(north != null && north.dyeB < 0.5f && west != null && west.dyeA < 0.5f, "its hearth's colour taken before its wall, where the sprite paints the hearth");

        String cell = "|BlocksPlacement||CanScrap||CustomName|Toilet|GroupName|Chemical|IsLow||IsMoveAble||Material|Plumbing|Material2|Screws|PickUpTool|Wrench|"
                + "PickUpWeight|100|PlaceTool|Wrench|ScrapSize|Small|SeatMaterial|Metal|Facing|";
        String[][] toilets = {{"4", "S", "steel_toilet_left"}, {"5", "E", "steel_toilet"}, {"14", "W", "steel_toilet_left"}, {"15", "N", "steel_toilet"}};
        for (String[] toilet : toilets) {
            Furniture one = Furniture.known(sprite("fixtures_bathroom_02_" + toilet[0] + cell + toilet[1] + "|"));
            check(model(one).equals(toilet[2]), "a cell's toilet facing " + toilet[1] + " has its bowl on the side its sprite paints it: " + model(one));
            if (one != null && one.model != null) {
                // Where the bowl is on the map: the side of the model it is built on, turned as the piece is.
                float[] side = new float[2];
                Models.turn(one.turns, toilet[2].endsWith("left") ? -1f : 1f, 0f, side);
                boolean alongX = toilet[1].equals("S") || toilet[1].equals("N");
                check(alongX ? side[0] < -0.5f : side[1] < -0.5f, "the bowl is to the west along a north or south wall and to the north along a west or east one: " + toilet[1]);
            }
        }

        String trailer = "|BlocksPlacement||CanBreak||CanScrap||CustomName|Table|Facing|E|GroupName|Lightwood Trailer|IsLow||IsMoveAble||IsTable||Material|Wood|"
                + "Material2|Nails|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|100|PlaceTool|Hammer|Surface|27|solidtrans||SpriteGridPos|";
        IsoSprite near = sprite("location_trailer_02_12" + trailer + "0,0"), far = sprite("location_trailer_02_13" + trailer + "1,0");
        piece(2, 1, near, far);
        Furniture table = Furniture.known(near);
        check(model(table).equals("trailer_table"), "a trailer's table is the table on one column: " + model(table));
        if (table != null && table.model != null) {
            float[] length = new float[2];
            Models.turn(table.turns, 1f, 0f, length);
            check(Math.abs(length[0]) > 0.5f, "lying along x, the way its two squares do");
            check(Math.abs(table.model.height() * table.scaleUp - 27 * SURFACE_TILES) < 1e-3f, "its top as high as the game says");
        }

        String shelves = "|BlocksPlacement||CanBreak||CanScrap||ContainerCapacity|30|CustomName|Shelves|Facing|S|IsMoveAble||ItemHeight|64|Material|Wood|"
                + "Material2|Screws|MaterialType|Wood|PickUpTool|Hammer|PickUpWeight|75|PlaceTool|Hammer|ScrapSize|Large|container|shelves|solid||GroupName|";
        check(model(Furniture.known(sprite("furniture_shelving_01_9" + shelves + "Big Wall|"))).equals("wall_shelving"), "tall shelves on a wall are the shelving");
        check(model(Furniture.known(sprite("furniture_shelving_01_8" + shelves + "Big Wall Corner|"))).equals("wall_shelving_corner"), "and in a corner, the corner's");

        Furniture rocker = Furniture.known(sprite("furniture_seating_indoor_03_148|BedType|averageBed|BlocksPlacement||CanScrap||CustomName|Chair|Facing|E|"
                + "GroupName|Rocking 2|IsLow||IsMoveAble||Material|Wood|Material2|Nails|PickUpWeight|75|ScrapSize|Small|Surface|15|bed||solidtrans||SeatMaterial|Wooden|"));
        check(model(rocker).equals("rocking_chair_black"), "the black rocking chair is not the armchair: " + model(rocker));
        check(rocker != null && rocker.model != null && Math.abs(HomeFittings.ROCKER_SEAT * rocker.scaleUp - 15 * SURFACE_TILES) < 1e-3f, "its seat as high as the game says");

        Furniture fryer = Furniture.known(sprite("appliances_cooking_01_52|BlocksPlacement||CanScrap||ContainerCapacity|5|CustomName|Industrial 2000|Facing|E|"
                + "GroupName|Fryers Club|IsLow||IsMoveAble||IsoType|IsoStove|ItemHeight|34|Material|Electric|Material2|MetalPlates|Material3|MetalScrap|MaterialType|Metal|"
                + "PickUpLevel|2|PickUpTool|Electrician|PickUpWeight|300|PlaceTool|Electrician|container|stove|solidtrans||"));
        check(model(fryer).equals("deep_fryer"), "a kitchen's fryer is the fryer: " + model(fryer));
        Furniture coffee = Furniture.known(sprite("appliances_cooking_01_56|BlocksPlacement||CanScrap||CustomName|X-press|Facing|S|GroupName|Coffee|IsMoveAble||"
                + "IsSurfaceOffset||IsTableTop||IsoType|IsoStove|Material|Electric|Material2|MetalScrap|MaterialType|Plastic|PickUpWeight|40|ScrapSize|Small|Surface|34|"));
        check(model(coffee).equals("coffee_maker") && coffee.raised > 0.8f, "a coffee maker stands on the counter it was painted on: " + model(coffee));
    }

    private static void aCountersTopIsABoardOverTheCountersOwnTop() {
        Furniture board = Furniture.known(sprite("fixtures_counters_01_89|BlocksPlacement||CanScrap||Countertop||GenericCraftingSurface|true|IsTable||Material|Wood|"
                + "ScrapSize|Small|Surface|35|"));
        check(board != null && board.countertop && board.model == null, "a counter's top is no model of its own, but the board over the counter under it");
        for (String counter : new String[] {"counter", "counter_sink", "counter_corner"}) {
            String worktop = HomeFittings.worktopOver(counter);
            check(worktop != null, "each counter that takes a top has a board: " + counter);
            if (worktop == null) {
                continue;
            }
            Models.Model under = Models.get(counter), over = Models.get(worktop);
            check(Math.abs(Models.get("counter").height() - HomeFittings.WORKTOP_HIGH) < 1e-3f, "the counter's top is where the board is laid over it: " + counter + " " + under.height());
            check(Math.abs(over.height() - HomeFittings.WORKTOP_THICK) < 1e-3f, "the board is as thick as it is stood: " + over.height());
            check(over.width() >= under.width() - 0.05f && over.depth() >= 0.6f, "and covers the counter's own top: " + worktop);
        }
        check(HomeFittings.worktopOver("shop_counter") == null, "a shop's counter keeps its own top");
    }
}

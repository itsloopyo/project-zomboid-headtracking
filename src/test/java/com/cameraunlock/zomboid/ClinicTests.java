package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import zombie.iso.sprite.IsoSprite;

/**
 * What the mod makes of the furniture of wards, surgeries and offices, asked as the build asks
 * it: Furniture.known, with each sprite's properties as the game's tile definitions give them
 * (sounds left out); and DeskRuns, which turns each square of a desk laid in an L.
 */
final class ClinicTests {
    private ClinicTests() {}

    static void run() {
        Checks.run("aWardBedIsTheHospitalBedWithItsHeadAtItsBack", ClinicTests::aWardBedIsTheHospitalBedWithItsHeadAtItsBack);
        Checks.run("theTwoPatientCouchesAreToldByTheirDrawers", ClinicTests::theTwoPatientCouchesAreToldByTheirDrawers);
        Checks.run("whatStandsAloneInAWardIsItsModel", ClinicTests::whatStandsAloneInAWardIsItsModel);
        Checks.run("aTelephoneStandsOnTheTableItWasPaintedFor", ClinicTests::aTelephoneStandsOnTheTableItWasPaintedFor);
        Checks.run("theWideCabinetIsTwoHalvesThatMeet", ClinicTests::theWideCabinetIsTwoHalvesThatMeet);
        Checks.run("aFirstAidCaseHangsOnItsWallShowingItsSprite", ClinicTests::aFirstAidCaseHangsOnItsWallShowingItsSprite);
        Checks.run("eachSquareOfAnLDeskIsItsOwnPieceTurnedAwayFromTheOtherArm", ClinicTests::eachSquareOfAnLDeskIsItsOwnPieceTurnedAwayFromTheOtherArm);
        Checks.run("theSheetsStraightDesksAreLaidSquareBySquare", ClinicTests::theSheetsStraightDesksAreLaidSquareBySquare);
        Checks.run("deskRunsTurnEveryArmOfAnL", ClinicTests::deskRunsTurnEveryArmOfAnL);
        Checks.run("theOfficeSheetsDeskSquaresWithNoNameAreDesks", ClinicTests::theOfficeSheetsDeskSquaresWithNoNameAreDesks);
        Checks.run("aDentistsChairAndItsLampAreTheirModels", ClinicTests::aDentistsChairAndItsLampAreTheirModels);
    }

    private static String model(Furniture piece) {
        return piece == null ? "nothing" : piece.model == null ? "no model" : piece.model.name;
    }

    private static final String MEDICAL = "location_community_medical_01_";
    private static final float SURFACE_TILES = ViewCamera.LEVEL_HEIGHT / WorldScene.SURFACE_PIXELS_PER_LEVEL;

    private static void aWardBedIsTheHospitalBedWithItsHeadAtItsBack() {
        String row = "|BedType|goodBed|BlocksPlacement||CustomName|Bed|Facing|S|GroupName|Large Medical|IsLow||IsMoveAble||ItemHeight|29|"
                + "PickUpWeight|150|bed||solidtrans||SpriteGridPos|";
        IsoSprite head = TileRows.sprite(MEDICAL + "17" + row + "0,0"), foot = TileRows.sprite(MEDICAL + "16" + row + "0,1");
        TileRows.piece(1, 2, head, foot);
        Furniture bed = Furniture.known(head);
        check(model(bed).equals("hospital_bed") && model(Furniture.known(foot)).equals("hospital_bed"), "both squares of a ward's bed are the hospital bed: " + model(bed));
        check(bed.turns == 2, "facing south, so its head is at the north, the square the sprite paints the pillow on: " + bed.turns);
        check(bed.dyed == 1 << Models.CLOTH && bed.dyeY == 1, "its sheet takes its colour from the foot's square: " + bed.dyeX + ", " + bed.dyeY);
        check(bed.model.depth() * bed.scale <= 2f && bed.model.depth() * bed.scale > 1.9f, "and it is as long as its two squares: " + bed.model.depth() * bed.scale);
        String gurney = "|BedType|averageBed|BlocksPlacement||CustomItem|Base.Mov_Gurney|CustomName|Bed|Facing|S|ForceSingleItem||GroupName|Hospital|"
                + "IsLow||IsMoveAble||ItemHeight|32|PickUpWeight|150|bed||solidtrans||SpriteGridPos|";
        IsoSprite one = TileRows.sprite(MEDICAL + "73" + gurney + "0,0"), two = TileRows.sprite(MEDICAL + "72" + gurney + "0,1");
        TileRows.piece(1, 2, one, two);
        check(model(Furniture.known(one)).equals("gurney"), "the bed the game groups as a hospital's is the stretcher on wheels: " + model(Furniture.known(one)));
        String morgue = "|BedType|badBed|BlocksPlacement||CanScrap||CustomName|Table|Facing|E|GroupName|Morgue|IsLow||IsMoveAble||ItemHeight|32|"
                + "Material|MetalPlates|Material2|MetalScrap|PickUpWeight|150|bed||solidtrans||SpriteGridPos|";
        IsoSprite tap = TileRows.sprite(MEDICAL + "76" + morgue + "0,0"), drain = TileRows.sprite(MEDICAL + "77" + morgue + "1,0");
        TileRows.piece(2, 1, tap, drain);
        Furniture table = Furniture.known(tap);
        check(model(table).equals("morgue_table") && table.turns == 1, "a morgue's table faces east, its tap at the west: " + model(table) + " " + table.turns);
    }

    private static void theTwoPatientCouchesAreToldByTheirDrawers() {
        String wooden = "|BedType|averageBed|BlocksPlacement||CanScrap||ContainerCapacity|20|ContainerPosition|Low|CustomName|Chair|Facing|S|"
                + "GroupName|Patient|IsLow||IsMoveAble||ItemHeight|30|Material|Wood|Material2|Nails|Material3|Fabric|PickUpWeight|150|bed||"
                + "container|counter|solidtrans||SpriteGridPos|";
        IsoSprite head = TileRows.sprite(MEDICAL + "1" + wooden + "0,0"), foot = TileRows.sprite(MEDICAL + "0" + wooden + "0,1");
        TileRows.piece(1, 2, head, foot);
        Furniture couch = Furniture.known(head);
        check(model(couch).equals("exam_table") && couch.turns == 2, "the couch with drawers under it is the doctor's couch, its raised head north: "
                + model(couch) + " " + couch.turns);
        String steel = "|BedType|averageBed|BlocksPlacement||CanScrap||CustomName|Chair|Facing|S|GroupName|Patient|IsLow||IsMoveAble||ItemHeight|34|"
                + "Material|MetalPlates|PickUpWeight|150|bed||solidtrans||SpriteGridPos|";
        IsoSprite one = TileRows.sprite(MEDICAL + "33" + steel + "0,0"), two = TileRows.sprite(MEDICAL + "32" + steel + "0,1");
        TileRows.piece(1, 2, one, two);
        check(model(Furniture.known(one)).equals("treatment_couch"), "the one with none is the pad on a steel column: " + model(Furniture.known(one)));
    }

    private static void whatStandsAloneInAWardIsItsModel() {
        String[][] pieces = {
            {"10|BedType|badBed|BlocksPlacement||CanScrap||CustomName|Medical Stool|IsLow||IsMoveAble||Material|MetalScrap|PickUpWeight|50|ScrapSize|Small|bed||",
                "medical_stool"},
            {"48|BlocksPlacement||CanScrap||CustomName|Counter|Facing|W|GenericCraftingSurface|true|GroupName|Mobile Tool|IsLow||IsMoveAble||ItemHeight|30|"
                + "Material|MetalBars|Material2|SmallMetalPlates|PickUpWeight|50|ScrapSize|Small|", "mayo_stand"},
            {"24|BlocksPlacement||CanScrap||CustomName|Bloodbag|Facing|E|GroupName|Mobile|IsMoveAble||PickUpWeight|75|ScrapSize|Small|", "iv_stand"},
            {"52|BlocksPlacement||CanScrap||CustomName|Bench|Facing|W|GroupName|Foot|IsLow||IsMoveAble||IsTable||Material|MetalBars|"
                + "Material2|SmallMetalPlates|PickUpWeight|50|ScrapSize|Small|Surface|15|", "foot_bench"},
            {"64|BlocksPlacement||CanScrap||CustomName|Bench|Facing|N|GroupName|Tool|IsLow||IsMoveAble||IsTable||Material|MetalBars|"
                + "Material2|SmallMetalPlates|PickUpWeight|75|ScrapSize|Small|Surface|34|solidtrans||", "bassinet"},
            {"8|BlocksPlacement||CanScrap||CustomName|Scale|Facing|E|GroupName|Weighing|IsLow||IsMoveAble||Material|MetalBars|PickUpWeight|50|ScrapSize|Small|",
                "weighing_scale"},
            {"102|BlocksPlacement||CanScrap||CustomName|Bank|Facing|S|GroupName|Blood|IsMoveAble||ItemHeight|80|Material|MetalPlates|Material2|MetalScrap|"
                + "Material3|Electric|MaterialType|Metal|PickUpLevel|3|PickUpTool|Electrician|PickUpWeight|250|PlaceTool|Electrician|", "blood_bank"},
            {"136|BlocksPlacement||CanScrap||CustomName|Microscope|Facing|S|GroupName|White|IsMoveAble||IsSurfaceOffset||IsTableTop||Material|Electric|"
                + "Material2|SmallMetalPlates|Material3|MetalScrap|PickUpWeight|200|Surface|34|", "microscope"},
        };
        for (String[] piece : pieces) {
            Furniture made = Furniture.known(TileRows.sprite(MEDICAL + piece[0]));
            check(model(made).equals(piece[1]), MEDICAL + piece[0].substring(0, piece[0].indexOf('|')) + " is the " + piece[1] + ": " + model(made));
            check(made != null && made.model.width() * made.scale <= 1f && made.model.depth() * made.scale <= 1f, "and fits its square");
        }
        Furniture step = Furniture.known(TileRows.sprite(MEDICAL + pieces[3][0]));
        check(Math.abs(step.model.height() * step.scaleUp - 15 * SURFACE_TILES) < 1e-4f, "a step's top is as high as the game says: " + step.model.height() * step.scaleUp);
        Furniture scope = Furniture.known(TileRows.sprite(MEDICAL + pieces[7][0]));
        check(Math.abs(scope.raised - 34 * SURFACE_TILES) < 1e-4f, "a microscope stands on the bench it was painted for: " + scope.raised);
    }

    private static void aTelephoneStandsOnTheTableItWasPaintedFor() {
        String rotary = "location_business_office_generic_02_0|BlocksPlacement||CanScrap||CustomName|Phone|Facing|E|GroupName|Beige Rotary|IsMoveAble||"
                + "IsSurfaceOffset||IsTableTop||Material|Electric|Material2|MetalScrap|PickUpWeight|20|Surface|34|";
        Furniture dial = Furniture.known(TileRows.sprite(rotary));
        check(model(dial).equals("rotary_phone") && dial.turns == 1, "a rotary phone facing east is the dial phone turned east: " + model(dial));
        check(dial.dyed == 1 << Models.PLASTIC, "its body is the colour of its sprite's");
        check(Math.abs(dial.raised - 34 * SURFACE_TILES) < 1e-4f && dial.scale == Furniture.TILES_PER_METRE, "on its table, its own size: " + dial.raised);
        Furniture keys = Furniture.known(TileRows.sprite(rotary.replace("_02_0|", "_02_16|").replace("Facing|E", "Facing|S").replace("Beige Rotary", "White Modern")));
        check(model(keys).equals("desk_phone") && keys.turns == 2, "a modern one is the phone with keys: " + model(keys));
    }

    private static void theWideCabinetIsTwoHalvesThatMeet() {
        String row = "|BlocksPlacement||ContainerCapacity|30|CustomName|Cabinet|Facing|E|GroupName|Wide Medical|MaterialType|Metal|container|medicine|solid||";
        Furniture left = Furniture.known(TileRows.sprite(MEDICAL + "152" + row)), right = Furniture.known(TileRows.sprite(MEDICAL + "153" + row));
        check(model(left).equals("fume_hood_left") && model(right).equals("fume_hood_right"), "the two sprites of a facing are the two halves: "
                + model(left) + ", " + model(right));
        check(left.scale == Furniture.TILES_PER_METRE && Math.abs(left.model.width() - 1f) < 1e-4f, "each a square wide, so the halves meet: " + left.model.width());
    }

    private static void aFirstAidCaseHangsOnItsWallShowingItsSprite() {
        Furniture box = Furniture.known(TileRows.sprite(MEDICAL + "88|CanBreak||CanScrap||ContainerCapacity|10|ContainerPosition|High|CustomName|Cabinet|"
                + "Facing|S|GroupName|First Aid|IsHigh||IsMoveAble||Material|Wood|Material2|Mirror|MoveType|WallObject|PickUpWeight|100|ScrapSize|Small|"
                + "Surface|80|attachedN||container|medicine|"));
        check(model(box).equals("first_aid_case") && box.wall == WallHung.NORTH && box.pictured && box.ownSize,
                "a first aid case hangs on the wall behind it, its front the sprite's painting: " + model(box));
    }

    private static final String DESK = "|BlocksPlacement||CanScrap||CustomName|Desk|Facing|E|GroupName|Office L|IsLow||IsMoveAble||IsTable||Material|Nails|"
            + "Material2|Wood|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|100|PlaceTool|Hammer|Surface|33|solidtrans||SpriteGridPos|";

    private static void eachSquareOfAnLDeskIsItsOwnPieceTurnedAwayFromTheOtherArm() {
        String office = "location_business_office_generic_01_";
        IsoSprite drawers = TileRows.sprite(office + "104" + DESK + "0,2|container|desk|"), middle = TileRows.sprite(office + "105" + DESK + "0,1"),
                corner = TileRows.sprite(office + "106" + DESK + "0,0"), next = TileRows.sprite(office + "107" + DESK + "1,0"),
                end = TileRows.sprite(office + "108" + DESK + "2,0");
        String[] models = {"desk_pedestal_left", "desk_run", "desk_corner", "desk_run", "desk_end_right"};
        IsoSprite[] squares = {drawers, middle, corner, next, end};
        for (int i = 0; i < squares.length; i++) {
            Furniture square = Furniture.known(squares[i]);
            check(model(square).equals(models[i]), "square " + i + " of the L is " + models[i] + ": " + model(square));
            check(square.grid == null && square.centreX == 0.5f && square.centreY == 0.5f, "a piece of its own, in the middle of its square");
            check(Math.abs(square.raised + square.model.height() * square.scaleUp - 33 * SURFACE_TILES) < 1e-4f,
                    "its top as high as the game says: " + (square.raised + square.model.height() * square.scaleUp));
            check(square.scale == Furniture.TILES_PER_METRE, "and a metre of it a tile, so the tops meet");
        }
        float[] back = new float[2];
        Models.turn(Furniture.known(middle).turns, 0f, 1f, back);
        check(back[0] == -1f && back[1] == 0f, "the arm along y has its back to the west, away from the arm along x: " + back[0] + ", " + back[1]);
        Models.turn(Furniture.known(next).turns, 0f, 1f, back);
        check(back[0] == 0f && back[1] == -1f, "and the arm along x its back to the north: " + back[0] + ", " + back[1]);
        Models.turn(Furniture.known(drawers).turns, 0f, -1f, back);
        check(back[0] == 1f && back[1] == 0f, "the drawers open to the east, inside the L: " + back[0] + ", " + back[1]);
        // Facing north the tile definitions lay the squares on one another; where each lies is the map's.
        String north = DESK.replace("Facing|E", "Facing|N");
        String[] northModels = {"desk_end_right", "desk_run", "desk_corner", "desk_run", "desk_end_left"};
        for (int i = 0; i < northModels.length; i++) {
            Furniture square = Furniture.known(TileRows.sprite(office + (112 + i) + north + (i % 2) + ",0"));
            check(model(square).equals(northModels[i]), "square " + i + " of the L facing north is " + northModels[i] + ": " + model(square));
        }
    }

    private static void theSheetsStraightDesksAreLaidSquareBySquare() {
        String office = "location_business_office_generic_01_";
        // They reach the build with no grid: the game drops the grids of their group.
        String straight = DESK.replace("Office L", "Office").replace("Facing|E", "Facing|S");
        Furniture withDrawers = Furniture.known(TileRows.sprite(office + "42" + straight + "0,0|container|desk|")),
                open = Furniture.known(TileRows.sprite(office + "43" + straight + "1,0"));
        check(model(withDrawers).equals("desk_pedestal_left") && model(open).equals("desk_end_right"),
                "a desk two squares long with no grid is its drawers at one end and a panel leg at the other: " + model(withDrawers) + ", " + model(open));
        check(withDrawers.turns == 2 && open.turns == 2, "both facing south, as the game says");
        check(Math.abs(open.raised + open.model.height() * open.scaleUp - 33 * SURFACE_TILES) < 1e-4f, "its top as high as the game says");
        Furniture middle3 = Furniture.known(TileRows.sprite(office + "73" + DESK.replace("Office L", "Office") + "0,1"));
        check(model(middle3).equals("desk_run"), "the middle of one three squares long is a length of desk: " + model(middle3));
        check(open.alsoDyed == 1 << Models.METAL && Math.abs(open.alsoA - 0.94f) < 1e-4f && Math.abs(open.alsoB - 0.8f) < 1e-4f,
                "its body takes the colour the sprite paints its free end's panel, seen from inside: " + open.alsoA + ", " + open.alsoB);
        check(withDrawers.alsoDyed == 1 << Models.METAL && withDrawers.alsoB > 0.9f, "and with its back board away, its drawers' front: " + withDrawers.alsoB);
        Furniture facingAway = Furniture.known(TileRows.sprite(office + "76" + straight + "1,0"));
        check(model(facingAway).equals("desk_run") && facingAway.alsoDyed == 0, "a length whose sprite paints nothing under its top keeps the model's colour");
    }

    private static void deskRunsTurnEveryArmOfAnL() {
        // The four Ls of the sheet's "Office 3", one a facing, as SpriteGridPos lays them on four squares each way.
        int[][][] ls = {
            {{0, 3}, {0, 2}, {0, 1}, {0, 0}, {1, 0}, {2, 0}, {3, 0}},
            {{0, 3}, {1, 3}, {2, 3}, {3, 3}, {3, 2}, {3, 1}, {3, 0}},
            {{0, 0}, {1, 0}, {2, 0}, {3, 0}, {3, 1}, {3, 2}, {3, 3}},
            {{3, 3}, {2, 3}, {1, 3}, {0, 3}, {0, 2}, {0, 1}, {0, 0}},
        };
        for (int[][] l : ls) {
            boolean[][] filled = new boolean[4][4];
            for (int[] square : l) {
                filled[square[0]][square[1]] = true;
            }
            int cornerX = l[3][0], cornerY = l[3][1];
            for (int[] square : l) {
                DeskRuns.Square desk = DeskRuns.of(filled, square[0], square[1], false, 0);
                float[] back = new float[2], left = new float[2];
                Models.turn(desk.turns(), 0f, 1f, back);
                if (square[0] == cornerX && square[1] == cornerY) {
                    check(desk.model().equals("desk_corner"), "the corner is the column: " + desk.model());
                    Models.turn(desk.turns(), -1f, 0f, left);
                    check(filled[cornerX + Math.round(left[0])][cornerY + Math.round(left[1])], "turned so its full sides meet the arms");
                    continue;
                }
                // The back points away from the square across the corner: away from where whoever sits at it sits.
                int insideX = l[0][0] == cornerX ? l[6][0] - cornerX : l[0][0] - cornerX, insideY = l[0][1] == cornerY ? l[6][1] - cornerY : l[0][1] - cornerY;
                check(back[0] * insideX <= 0f && back[1] * insideY <= 0f && (back[0] != 0f) != (back[1] != 0f),
                        "every square's back is on the outside of the L: " + square[0] + "," + square[1] + " back " + back[0] + ", " + back[1]);
                boolean free = square == l[0] || square == l[6];
                check(desk.model().startsWith(free ? "desk_end" : "desk_run"), square[0] + "," + square[1] + " is " + desk.model());
            }
        }
    }

    private static void theOfficeSheetsDeskSquaresWithNoNameAreDesks() {
        String office = "location_business_office_generic_01_";
        Furniture run = Furniture.known(TileRows.sprite(office + "2|")), corner = Furniture.known(TileRows.sprite(office + "3|"));
        check(model(run).equals("desk_run") && run.turns == 3, "the square with its board on its east edge is a length of desk, its back east: " + model(run));
        check(model(corner).equals("desk_corner"), "the one on a column is the corner: " + model(corner));
        check(Math.abs(run.raised + run.model.height() * run.scaleUp - 33 * SURFACE_TILES) < 1e-4f, "as high as the sheet's desks");
        check(Furniture.known(TileRows.sprite(office + "9|")) == null, "and a square of the sheet with no model is left to its sprite");
    }

    private static void aDentistsChairAndItsLampAreTheirModels() {
        String row = "|BlocksPlacement||CustomName|Chair|Facing|S|GroupName|Dentist Patient|solidtrans||SpriteGridPos|";
        IsoSprite head = TileRows.sprite(MEDICAL + "113" + row + "0,0"), foot = TileRows.sprite(MEDICAL + "112" + row + "0,1|bed||BedType|averageBed|");
        TileRows.piece(1, 2, head, foot);
        Furniture chair = Furniture.known(head);
        check(model(chair).equals("dentist_chair") && chair.turns == 2, "a dentist's chair facing south is the chair, its head north: " + model(chair));
        String north = row.replace("Facing|S", "Facing|N");
        IsoSprite back = TileRows.sprite(MEDICAL + "173" + north + "0,0"), front = TileRows.sprite(MEDICAL + "172" + north + "0,1");
        TileRows.piece(1, 2, back, front);
        check(Furniture.known(back) == null, "one facing north paints no upholstery where the colour is taken and is left to its sprite");
        String unit = "|CustomName|Workstation|Facing|E|GroupName|Dentist|SpriteGridPos|";
        IsoSprite cart = TileRows.sprite(MEDICAL + "116" + unit + "0,0"), lamp = TileRows.sprite(MEDICAL + "117" + unit + "1,0");
        TileRows.piece(2, 1, cart, lamp);
        Furniture station = Furniture.known(cart);
        check(model(station).equals("dentist_unit") && station.turns == 1, "the cart and lamp facing east reach the lamp over the east square: " + model(station));
    }
}

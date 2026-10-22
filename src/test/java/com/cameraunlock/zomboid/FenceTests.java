package com.cameraunlock.zomboid;

import java.util.HashMap;
import java.util.Map;

/** Tests of what the game's fences are built as, and that no two of the world's materials share a number. */
final class FenceTests {
    private FenceTests() {}

    static void run() {
        Checks.run("chainLinkLengthsAreKnownByTheirPlaceOnTheSheet", FenceTests::chainLinkLengthsAreKnownByTheirPlaceOnTheSheet);
        Checks.run("tornAndBurntChainLinkIsWireOnAnEdgeOfTheirSheets", FenceTests::tornAndBurntChainLinkIsWireOnAnEdgeOfTheirSheets);
        Checks.run("chainLinkIsLaidAsASheetWhereverItStandsOnAnEdge", FenceTests::chainLinkIsLaidAsASheetWhereverItStandsOnAnEdge);
        Checks.run("boardFencesAreWoodOnAnEdgeAndNoPickets", FenceTests::boardFencesAreWoodOnAnEdgeAndNoPickets);
        Checks.run("ironBarFencesAreTheirOwnIron", FenceTests::ironBarFencesAreTheirOwnIron);
        Checks.run("theGamesFloorMarkByNameStaysOffEdgesAndWalls", FenceTests::theGamesFloorMarkByNameStaysOffEdgesAndWalls);
        Checks.run("noTwoWorldMaterialsShareANumber", FenceTests::noTwoWorldMaterialsShareANumber);
    }

    private static void chainLinkLengthsAreKnownByTheirPlaceOnTheSheet() {
        // Rows of the game's tile definitions: which edges each length is on, and the post its sprite paints at the north-west corner.
        Object[][] rows = {{"fencing_01_24", true, false, true}, {"fencing_01_25", true, false, false}, {"fencing_01_26", false, true, false},
                {"fencing_01_27", false, true, true}, {"fencing_01_28", true, true, true}, {"fencing_01_56", true, false, true},
                {"fencing_01_57", true, false, false}, {"fencing_01_58", false, true, false}, {"fencing_01_59", false, true, true},
                {"fencing_01_60", true, true, true}};
        for (Object[] row : rows) {
            ChainLink.Run run = ChainLink.of((String) row[0]);
            Checks.check(run != null && run.north() == (boolean) row[1] && run.west() == (boolean) row[2] && run.hasPost() == (boolean) row[3]
                    && run.post() > run.rail(), row[0] + " is a length of chain link on the edges and with the post its sprite has, the post over its rail");
        }
        Checks.check(ChainLink.of("fencing_01_57").rail() > ChainLink.of("fencing_01_25").rail(), "the tall fence is the taller");
        for (String other : new String[] {"fencing_01_29", "fencing_01_61", "fencing_01_48", "fencing_01_80", "fencing_01_88", "fencing_damaged_02_0", "walls_exterior_house_01_16"}) {
            Checks.check(ChainLink.of(other) == null, other + " is no whole length of chain link");
        }
        Checks.check(ChainLink.lonePost("fencing_01_29") && ChainLink.lonePost("fencing_01_61") && !ChainLink.lonePost("fencing_01_56"), "lone posts are told");
    }

    private static void tornAndBurntChainLinkIsWireOnAnEdgeOfTheirSheets() {
        Checks.check(ChainLink.broken("fencing_damaged_02_0", "MetalWire", true), "a torn tall length");
        Checks.check(ChainLink.broken("fencing_damaged_01_32", "MetalWire", true), "a torn low length");
        Checks.check(ChainLink.broken("fencing_burnt_01_48", "MetalWire", true) && ChainLink.broken("fencing_burnt_01_64", "MetalWire", true), "burnt lengths");
        Checks.check(!ChainLink.broken("fencing_damaged_01_140", "MetalWire", false), "wire lying on the ground is no length");
        Checks.check(!ChainLink.broken("fencing_damaged_05_0", "MetalBars", true), "a bent welded panel is no chain link");
        Checks.check(!ChainLink.broken("fencing_01_80", "MetalWire", true), "a whole welded panel is no torn chain link");
        Checks.check(!ChainLink.broken("fencing_damaged_02_112", null, false), "a piece with no material is none");
        Checks.check(ChainLink.brokenMaterial("fencing_damaged_02_0", "MetalWire", true) == ChainLink.BROKEN
                && ChainLink.brokenMaterial("fencing_burnt_01_52", "MetalWire", true) == ChainLink.BURNT
                && ChainLink.brokenMaterial("fencing_damaged_05_0", "MetalBars", true) == 0f, "a burnt length is drawn darker than a torn one, and others not at all");
        Checks.check(ChainLink.gate("fixtures_doors_fences_01_64", "MetalWire") && ChainLink.gate("fixtures_doors_fences_01_68", "MetalWire")
                && !ChainLink.gate("fixtures_doors_fences_01_0", null) && !ChainLink.gate("fencing_01_56", "MetalWire"), "gates hung with wire are chain link gates");
    }

    /**
     * Rows of the game's tile definitions that no flag of a wall seen through marks: the corner lengths, hopped over on both
     * edges, and a torn length hopped over on one. Each is laid as one sheet of chain link.
     */
    private static void chainLinkIsLaidAsASheetWhereverItStandsOnAnEdge() {
        String[] rows = {"fencing_01_28|CanScrap||HitByCar||HoppableN||HoppableW||Material|MetalWire|WallNWTrans||wall||FenceTypeLow|Metal",
                "fencing_01_60|CanScrap||HitByCar||Material|MetalWire|Material2|MetalWire|TallHoppableN||TallHoppableW||WallNWTrans||wall||FenceTypeHigh|Metal",
                "fencing_damaged_02_43|HitByCar||HoppableW||Material|MetalWire|FenceTypeHigh|Metal|attachedFloor|true"};
        for (String row : rows) {
            zombie.iso.sprite.IsoSprite sprite = TileRows.sprite(row);
            Checks.check(ChainLink.sheet(sprite.getName(), sprite.getProperties(), zombie.iso.SpriteDetails.IsoObjectType.wall), sprite.getName() + " is laid as a sheet of chain link");
        }
        zombie.iso.sprite.IsoSprite corner = TileRows.sprite(rows[0]);
        Checks.check(Edges.north(corner.getProperties(), zombie.iso.SpriteDetails.IsoObjectType.wall) && Edges.west(corner.getProperties(), zombie.iso.SpriteDetails.IsoObjectType.wall),
                "a corner length stands on both its edges");
        zombie.iso.sprite.IsoSprite lying = TileRows.sprite("fencing_damaged_01_140|Material|MetalWire|attachedFloor|true");
        Checks.check(!ChainLink.sheet(lying.getName(), lying.getProperties(), zombie.iso.SpriteDetails.IsoObjectType.normal), "wire lying on the ground is no sheet");
        zombie.iso.sprite.IsoSprite panel = TileRows.sprite("fencing_01_80|HoppableN||Material|MetalWire|WallNTrans|");
        Checks.check(!ChainLink.sheet(panel.getName(), panel.getProperties(), zombie.iso.SpriteDetails.IsoObjectType.wall), "a welded panel is no sheet of chain link");
    }

    private static void boardFencesAreWoodOnAnEdgeAndNoPickets() {
        // name, Material, Material2, on an edge, a picket by its StopCar or TieSheetRope, and whether it is boards.
        Object[][] rows = {{"fencing_01_8", "Wood", "Nails", true, false, true}, {"fencing_01_12", "Wood", "Nails", true, false, true},
                {"fencing_01_32", "Wood", "Nails", true, false, true}, {"fencing_01_72", "Wood", "Nails", true, false, true},
                {"fencing_01_4", "Wood", "Nails", true, true, false}, {"fencing_01_13", "Wood", "Nails", false, false, false},
                {"fencing_01_16", "Wood", "MetalWire", true, false, false}, {"fencing_01_19", "Wood", null, false, false, false},
                {"fencing_damaged_01_16", "Wood", null, true, false, true}, {"fencing_damaged_01_48", "Wood", null, true, false, true},
                {"fencing_damaged_01_8", "Wood", null, true, true, false}, {"fencing_damaged_01_17", "Wood", null, false, false, false},
                {"fencing_damaged_03_8", "Wood", null, true, false, true}, {"fencing_damaged_03_0", "Wood", null, true, true, false},
                {"fencing_burnt_01_40", "Wood", null, true, false, true}, {"fencing_burnt_01_45", "Wood", null, false, false, false},
                {"fencing_01_56", "MetalWire", "MetalWire", true, false, false}, {"carpentry_02_0", "Wood", null, true, false, false}};
        for (Object[] row : rows) {
            Checks.check(Fences.boards((String) row[0], (String) row[1], null, (String) row[2], (boolean) row[3], (boolean) row[4]) == (boolean) row[5],
                    row[0] + (((boolean) row[5]) ? " is" : " is not") + " a board fence");
        }
        // The burnt tall fence has a MaterialType and no Material. The burnt pickets, what fencing_01_4..6 burn to, have neither.
        Checks.check(Fences.boards("fencing_burnt_01_8", null, "Wood", null, true, false) && Fences.boards("fencing_burnt_01_12", null, "Wood", null, true, false),
                "the burnt tall board fence is boards by its MaterialType");
        Checks.check(!Fences.boards("fencing_burnt_01_0", null, null, null, true, false), "a burnt picket, of no material the game names, is not");
        Checks.check(!Fences.boards("fencing_01_56", null, "Metal", null, true, false), "a MaterialType of another stuff is not");
    }

    private static void ironBarFencesAreTheirOwnIron() {
        Checks.check(Fences.ironBars("fencing_01_2", "MetalBars") && Fences.ironBars("fencing_01_64", "MetalBars"), "iron railings");
        Checks.check(Fences.ironBars("fencing_damaged_05_0", "MetalBars"), "the welded panels the game makes of bars");
        Checks.check(!Fences.ironBars("fencing_01_56", "MetalWire") && !Fences.ironBars("walls_exterior_house_01_16", "MetalBars"), "wire, and no fence");
    }

    /** Rows of the game's tile definitions, with the attachedFloor the game's tile loading sets on every name holding "damaged" or "trash_". */
    private static void theGamesFloorMarkByNameStaysOffEdgesAndWalls() {
        String marked = "|attachedFloor|true";
        String[] standing = {"fencing_damaged_01_16|CanScrap||Material|Wood|Material2|Nails|MaterialType|Wood_Solid|TallHoppableN||WallN||wall|" + marked,
                "fencing_damaged_02_0|CanScrap||Material|MetalWire|TallHoppableN||WallNTrans||wall|" + marked,
                "fencing_damaged_02_43|HitByCar||HoppableW||Material|MetalWire|FenceTypeHigh|Metal" + marked,
                "trash_walls_01_0|CanScrap||CustomName|Notices|Facing|E|GroupName|Weathered|IsMoveAble||Material|Paper|MoveType|WallObject|WallOverlay|" + marked};
        for (String row : standing) {
            zombie.iso.sprite.IsoSprite sprite = TileRows.sprite(row);
            Checks.check(!FloorMarks.onFloor(sprite.getName(), sprite.getProperties()), sprite.getName() + " stands on its edge or hangs on its wall, not on the floor");
        }
        zombie.iso.sprite.IsoSprite litter = TileRows.sprite("trash_01_0|Material|Paper" + marked);
        Checks.check(FloorMarks.onFloor(litter.getName(), litter.getProperties()), "litter the game marks so lies on the floor");
        zombie.iso.sprite.IsoSprite rug = TileRows.sprite("floors_rugs_01_0|attachedFloor|");
        Checks.check(FloorMarks.onFloor(rug.getName(), rug.getProperties()), "a tile the definitions mark so lies on the floor");
        Checks.check(WallObjects.flatOn("trash_walls_01_0", TileRows.sprite(standing[3]).getProperties(), zombie.iso.IsoDirections.E) == WallHung.WEST,
                "a notice the game marks so still hangs on the wall behind the way it faces");
    }

    /** Every named material of the world's faces. Each is a constant the compiler writes in here, so no class is loaded to read it. */
    private static void noTwoWorldMaterialsShareANumber() {
        Object[] named = {"WorldScene.SHINGLES", WorldScene.SHINGLES, "WorldScene.PAINT", WorldScene.PAINT, "WorldScene.WOOD", WorldScene.WOOD,
                "WorldScene.EARTH", WorldScene.EARTH, "WorldScene.LEAVES", WorldScene.LEAVES, "WorldScene.BARK", WorldScene.BARK,
                "WorldScene.PLANKS", WorldScene.PLANKS, "WorldScene.CARPET", WorldScene.CARPET, "WorldScene.CONCRETE", WorldScene.CONCRETE,
                "WorldScene.QUAY", WorldScene.QUAY, "WorldScene.TILE", WorldScene.TILE, "WorldScene.WIRE", WorldScene.WIRE,
                "WorldScene.GLASS", WorldScene.GLASS, "WorldScene.LAMP", WorldScene.LAMP, "WorldScene.MESH", WorldScene.MESH,
                "WorldScene.BOARDS", WorldScene.BOARDS, "RoofBuilder.RIDGE", RoofBuilder.RIDGE, "Ground.GRAVEL", Ground.GRAVEL,
                "Ground.PAVING", Ground.PAVING, "Flora.LITTER", Flora.LITTER, "Flora.TRUNK", Flora.TRUNK, "Flora.IVY", Flora.IVY,
                "Flora.ROCK", Flora.ROCK, "Flora.FROND", Flora.FROND, "Flora.LEAF", Flora.LEAF, "Flora.FLOWER", Flora.FLOWER,
                "Flora.BUSH", Flora.BUSH, "KerbBuilder.KERB_STONE", KerbBuilder.KERB_STONE, "WallBuilder.LIT_GLASS", WallBuilder.LIT_GLASS,
                "SpriteGeometry.TIMBER", SpriteGeometry.TIMBER, "WorldScene.PANE", WorldScene.PANE, "WorldScene.LIT_PANE", WorldScene.LIT_PANE,
                "OpeningBuilder.DRAPE", OpeningBuilder.DRAPE, "Flora.SHADE", Flora.SHADE,
                "Flora.CORE", Flora.CORE, "WorldScene.HUNG", WorldScene.HUNG, "RoadPaint.MATERIAL", RoadPaint.MATERIAL,
                "RoadPaint.STRIP", RoadPaint.STRIP, "WorldScene.BLOOD", WorldScene.BLOOD, "OpeningBuilder.JOINERY", OpeningBuilder.JOINERY,
                "OpeningBuilder.HARDWARE", OpeningBuilder.HARDWARE, "OpeningBuilder.JOINERY_LEVEL", OpeningBuilder.JOINERY_LEVEL,
                "WorldScene.BODY", WorldScene.BODY, "WorldScene.PRINT", WorldScene.PRINT, "ChainLink.FABRIC", ChainLink.FABRIC,
                "ChainLink.BROKEN", ChainLink.BROKEN, "ChainLink.BURNT", ChainLink.BURNT, "WorldScene.INNER_PANE", WorldScene.INNER_PANE,
                "WallBuilder.INNER_GLASS", WallBuilder.INNER_GLASS};
        Map<Float, String> taken = new HashMap<>();
        // The floors of boards take a run of numbers, and a crown one for each of its kinds. A model's surfaces take
        // a run from 16 that the floors of boards share: the picture tells a model's faces by uModel, not by number.
        for (float boards = WorldScene.FIRST_BOARDS; boards <= WorldScene.LAST_BOARDS; boards++) {
            taken.put(boards, "WorldScene.FIRST_BOARDS..LAST_BOARDS");
        }
        for (float surface = Models.FIRST_MATERIAL; surface < Models.FIRST_DYED_MATERIAL + Models.SURFACES; surface++) {
            taken.merge(surface, "Models.FIRST_MATERIAL and its surfaces", (boards, model) -> boards + " and " + model);
        }
        for (float crown = Flora.CROWN; crown < Flora.SHADE; crown++) {
            taken.put(crown, "Flora.CROWN and its kinds");
        }
        for (int i = 0; i < named.length; i += 2) {
            float number = (Float) named[i + 1];
            String before = taken.put(number, (String) named[i]);
            Checks.check(before == null, named[i] + " (" + number + ") is a material number no other has" + (before == null ? "" : ", but " + before + " has it"));
        }
    }
}

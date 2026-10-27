package com.cameraunlock.zomboid;

import java.util.Arrays;
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
        Checks.run("theWhitePicketFenceIsItsModelOnEachEdge", FenceTests::theWhitePicketFenceIsItsModelOnEachEdge);
        Checks.run("aLogWallIsItsLogs", FenceTests::aLogWallIsItsLogs);
        Checks.run("theSecurityFenceStartsWhereItsPostIsBeforeTheCorner", FenceTests::theSecurityFenceStartsWhereItsPostIsBeforeTheCorner);
        Checks.run("aMeshPanelStandsItsPostInAConcreteFoot", FenceTests::aMeshPanelStandsItsPostInAConcreteFoot);
    }

    private static void aMeshPanelStandsItsPostInAConcreteFoot() {
        // Each panel's post is at one end of its run, and the foot under it is a little way in from the corner the next panel's post stands by.
        Checks.check(Fences.footAlong("fencing_01_88") < 0f && Fences.footAlong("fencing_01_89") > 0.7f && Fences.footAlong("fencing_01_89") < 1f,
                "a north panel's post stands just before its corner or before the far one");
        Checks.check(Fences.footAlong("fencing_01_91") > 0f && Fences.footAlong("fencing_01_91") < 0.1f && Fences.footAlong("fencing_01_90") > 0.7f
                && Fences.footAlong("fencing_01_90") < 1f, "and a west panel's by its corner or by the far one");
        for (String other : new String[] {"fencing_01_48", "fencing_01_87", "fencing_01_92", "fencing_02_88", "walls_exterior_house_01_88"}) {
            Checks.check(Float.isNaN(Fences.footAlong(other)), other + " stands in no foot");
        }
        for (String panel : new String[] {"fencing_01_88", "fencing_01_89", "fencing_01_90", "fencing_01_91"}) {
            float a = Fences.footPainted(panel, 0), b = Fences.footPainted(panel, 1);
            Checks.check(a > 0f && a < 1f && b > 0f && b < 1f, panel + " paints its foot on its tile: " + a + ", " + b);
        }
        Models.Model foot = Models.get("fence_foot");
        Checks.check(Math.abs(foot.minY + 0.105f) < 0.002f && Math.abs(foot.maxY - 0.105f) < 0.002f && Math.abs(foot.height() - 0.11f) < 0.002f,
                "the foot is a block astride the edge's line: " + foot.minY + " to " + foot.maxY + ", " + foot.height() + " high");
    }

    private static void theSecurityFenceStartsWhereItsPostIsBeforeTheCorner() {
        // Read off the sprites: fencing_01_48's post stands 12 to 16 dots of 64 before the corner, its arm reaches 34.
        Checks.check(Fences.overhang("fencing_01_48") >= 34f / 64f && Fences.overhang("fencing_01_51") == Fences.overhang("fencing_01_48"),
                "a length with a post starts where its arm reaches");
        Checks.check(Fences.overhang("fencing_01_49") > 0f && Fences.overhang("fencing_01_49") < Fences.overhang("fencing_01_48")
                && Fences.overhangLayers("fencing_01_49") < Fences.overhangLayers("fencing_01_48"), "one without starts nearer, and under the one with");
        Checks.check(Fences.overhangLayers("fencing_01_48") == Fences.POSTED_LAYERS && Fences.overhangLayers("fencing_01_49") != Fences.POSTED_LAYERS,
                "a length with a post stands a pier under it, one without does not");
        Checks.check(Fences.POST_BEFORE > 12f / 64f && Fences.POST_BEFORE < 16f / 64f + 0.01f && Fences.POST_BEFORE < Fences.overhang("fencing_01_48"),
                "the pier stands under the post, inside what the sheet paints");
        for (String other : new String[] {"fencing_01_52", "fencing_01_53", "fencing_01_56", "fencing_01_4", "fencing_02_48", "walls_exterior_house_01_48"}) {
            Checks.check(Fences.overhang(other) == 0f, other + " starts at its corner");
        }
    }

    private static void aLogWallIsItsLogs() {
        for (String length : new String[] {"walls_logs_0", "walls_logs_1", "walls_logs_2", "walls_logs_4", "walls_logs_5", "walls_logs_8", "walls_logs_11"}) {
            Checks.check(LogWalls.is(length), length + " is a length of log wall");
        }
        for (String other : new String[] {"walls_logs_3", "walls_logs_12", "walls_logs_16", "walls_logs_48", "walls_logs_52", "walls_logs_96", "walls_exterior_wooden_01_0"}) {
            Checks.check(!LogWalls.is(other), other + " is none: a corner post, a gable, a palisade or stone");
        }
        for (String gable : new String[] {"walls_logs_16", "walls_logs_19", "walls_logs_27", "walls_logs_33"}) {
            Checks.check(LogWalls.gable(gable) && !LogWalls.is(gable), gable + " is a piece of a log gable");
        }
        for (String other : new String[] {"walls_logs_1", "walls_logs_13", "walls_logs_34", "walls_logs_48", "walls_exterior_roofs_02_16"}) {
            Checks.check(!LogWalls.gable(other), other + " is no log gable");
        }
        Checks.check(Math.abs(WallBuilder.roofOver(0.2f, 0.6f, 0f, 0.5f) - 0.4f) < 1e-6f && Math.abs(WallBuilder.roofOver(0f, 0f, 0.5f, 0.5f) - 0.5f) < 1e-6f
                && Math.abs(WallBuilder.roofOver(0f, 0f, 0.5f, 0f)) < 1e-6f, "a made gable's roof runs straight from end to end, and up to its peak halfway");
        boolean[] run = {true, false, true, true, true, false, true, true};
        WallBuilder.longestRun(run);
        Checks.check(Arrays.equals(run, new boolean[] {false, false, true, true, true, false, false, false}),
                "a gable's course keeps its longest run, not a log's end painted apart from it");
        Models.Model end = Models.get("log_end"), course = Models.get("log_course"), piece = Models.get("log_piece");
        Checks.check(Math.abs(end.height() - ViewCamera.LEVEL_HEIGHT) < 0.002f && Math.abs(course.height() - 2f * LogWalls.RADIUS) < 0.002f,
                "ten logs stack to a level: " + end.height() + ", one is " + course.height());
        Checks.check(Math.abs(course.maxY - LogWalls.RADIUS) < 0.002f && course.maxY > WorldScene.WALL_HALF, "the logs stand proud of the wall's skins");
        Checks.check(Math.abs(course.minX + 0.5f) < 0.002f && Math.abs(course.maxX - 0.5f) < 0.002f, "a course runs the square's edge");
        Checks.check(Math.abs(end.minX) < 0.002f && end.maxX > LogWalls.RADIUS + 0.1f, "an end runs on from the corner a hand past the other wall's logs: " + end.maxX);
        Checks.check(Math.abs(piece.maxX - piece.minX - LogWalls.PIECE) < 0.002f, "a piece is its share of the edge: " + (piece.maxX - piece.minX));
    }

    private static void theWhitePicketFenceIsItsModelOnEachEdge() {
        for (String length : new String[] {"fencing_01_4", "fencing_01_5", "fencing_01_6"}) {
            Checks.check(Pickets.is(length), length + " is a length of the white picket fence");
        }
        for (String other : new String[] {Pickets.POST, "fencing_01_32", "fencing_01_40", "fencing_01_46", "fencing_damaged_01_8", "fencing_burnt_01_0", "fencing_02_4"}) {
            Checks.check(!Pickets.is(other), other + " is no whole length of it");
        }
        Models.Model length = Models.get("picket_length"), post = Models.get("picket_post");
        Checks.check(Math.abs(length.minX + 0.5f) < 0.002f && Math.abs(length.maxX - 0.5f) < 0.002f, "a length runs the square's edge, end to end: " + length.minX
                + " to " + length.maxX);
        Checks.check(Math.abs(length.minY + 0.01f) < 0.002f, "its pickets' faces are a picket's half thickness off the edge's line: " + length.minY);
        Checks.check(length.height() < post.height(), "the post stands over the pickets");
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
                "WorldScene.OUTER_PANE", WorldScene.OUTER_PANE, "WallBuilder.INNER_GLASS", WallBuilder.INNER_GLASS, "WorldScene.STAINED", WorldScene.STAINED,
                "WorldScene.OWN_STAINED", WorldScene.OWN_STAINED, "WorldScene.STRAW", WorldScene.STRAW, "WorldScene.TERRAZZO", WorldScene.TERRAZZO,
                "WorldScene.CANVAS", WorldScene.CANVAS, "WorldScene.FLOOR_CARPET", WorldScene.FLOOR_CARPET};
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
        for (float parquet = WorldScene.FIRST_PARQUET; parquet <= WorldScene.LAST_PARQUET; parquet++) {
            String before = taken.put(parquet, "WorldScene.FIRST_PARQUET..LAST_PARQUET");
            Checks.check(before == null, "parquet (" + parquet + ") is a material number no other has" + (before == null ? "" : ", but " + before + " has it"));
        }
        for (int i = 0; i < named.length; i += 2) {
            float number = (Float) named[i + 1];
            String before = taken.put(number, (String) named[i]);
            Checks.check(before == null, named[i] + " (" + number + ") is a material number no other has" + (before == null ? "" : ", but " + before + " has it"));
        }
    }
}

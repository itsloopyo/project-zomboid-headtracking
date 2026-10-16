package com.cameraunlock.zomboid;

/** Tests of which model stands for a sprite of a works, a garage or a distillery, which way it is turned and where it stands. */
final class IndustryTests {
    private IndustryTests() {}

    static void run() {
        Checks.run("everyIndustrialPieceNamesAModelTheJarHolds", IndustryTests::everyIndustrialPieceNamesAModelTheJarHolds);
        Checks.run("aConveyorsLegsAreAtTheEndItsSpritePaintsThem", IndustryTests::aConveyorsLegsAreAtTheEndItsSpritePaintsThem);
        Checks.run("aBeltRunsThroughAMachineTheWayItsLineDoes", IndustryTests::aBeltRunsThroughAMachineTheWayItsLineDoes);
        Checks.run("aTanksHalvesMeetOnItsAxisAndItsHeadsAreAtItsEnds", IndustryTests::aTanksHalvesMeetOnItsAxisAndItsHeadsAreAtItsEnds);
        Checks.run("aLengthOfTankStandsAsHighAsItsEnds", IndustryTests::aLengthOfTankStandsAsHighAsItsEnds);
        Checks.run("aCrateOnACrateStandsWhereTheOneUnderItLeavesOff", IndustryTests::aCrateOnACrateStandsWhereTheOneUnderItLeavesOff);
        Checks.run("aModelIsAsHighAsTheGameSaysItsTopIs", IndustryTests::aModelIsAsHighAsTheGameSaysItsTopIs);
        Checks.run("aSpriteOfAnotherPlaceOrShapeIsNoIndustrialPiece", IndustryTests::aSpriteOfAnotherPlaceOrShapeIsNoIndustrialPiece);
        Checks.run("aLathesThreeSquaresStandItWithItsHeadstockAtItsEnd", IndustryTests::aLathesThreeSquaresStandItWithItsHeadstockAtItsEnd);
        Checks.run("aLathesThreeSquaresStandOrNotAlike", IndustryTests::aLathesThreeSquaresStandOrNotAlike);
        Checks.run("aWorkshopsMachinesFaceTheWayTheirSpritesDo", IndustryTests::aWorkshopsMachinesFaceTheWayTheirSpritesDo);
        Checks.run("aBenchGrinderStandsOnItsBench", IndustryTests::aBenchGrinderStandsOnItsBench);
    }

    private static Outdoors.Piece unnamed(String name, int surface) {
        return Industry.of(name, null, null, Outdoors.NONE, 1, 1, surface);
    }

    private static Outdoors.Piece works(int place) {
        return unnamed("industry_02_" + place, 0);
    }

    private static void holds(Outdoors.Piece piece, String what) {
        Checks.check(piece != null, what);
        if (piece != null) {
            Models.get(piece.model);
        }
    }

    /** Which way on the map, as (x, y), a model's (x, y) points once the piece is turned. */
    private static float[] way(Outdoors.Piece piece, float x, float y) {
        float[] out = new float[2];
        Models.turn(piece.turns, x, y, out);
        return out;
    }

    private static void everyIndustrialPieceNamesAModelTheJarHolds() {
        int[] places = {40, 41, 42, 43, 44, 45, 48, 49, 50, 51, 52, 53, 54, 55, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 76, 77, 78, 79,
                280, 281, 282, 283, 284, 285, 286, 287, 296, 297, 298, 299, 300, 301, 302, 303};
        for (int place : places) {
            holds(works(place), "industry_02_" + place + " has a model");
        }
        for (int place : new int[] {0, 1, 2, 3, 4, 5, 8, 9, 10}) {
            holds(unnamed("location_business_distillery_01_" + place, 0), "location_business_distillery_01_" + place + " has a model");
        }
        for (int place : new int[] {17, 20}) {
            holds(unnamed("carpentry_01_" + place, 64), "carpentry_01_" + place + " has a model");
        }
        for (int place : new int[] {18, 21}) {
            holds(unnamed("carpentry_01_" + place, 96), "carpentry_01_" + place + " has a model");
        }
        holds(Industry.of("location_business_machinery_01_16", "Workbench", "Wood Top", Outdoors.SOUTH, 2, 1, 24), "a workbench has a model");
        holds(Industry.of("location_business_machinery_01_18", "Workbench", "Wood Top", Outdoors.EAST, 1, 2, 24), "and one along y");
        holds(Industry.of("location_business_machinery_01_32", "Tool Cabinet", "Red Mobile", Outdoors.SOUTH, 1, 1, 34), "a tool chest has a model");
        holds(Industry.of("security_01_0", "Terminal", "Security", Outdoors.SOUTH, 1, 1, 0), "a security desk has a model");
        for (int place : new int[] {105, 107, 109, 110, 112, 113, 114, 115}) {
            holds(Industry.of("industry_03_" + place, "Propane Tank", null, Outdoors.NONE, 1, 1, 0), "industry_03_" + place + " has a model");
        }
        for (int place = 48; place < 64; place++) {
            holds(workshop(place), "industry_03_" + place + " has a model");
        }
        for (int place : new int[] {0, 1, 3, 4, 9, 10, 12, 13, 32, 33, 35, 36, 41, 42, 44, 45}) {
            holds(workshop(place), "industry_03_" + place + " has a model");
        }
        for (int place : new int[] {46, 47, 264, 265, 266, 267, 268, 269, 270, 271}) {
            holds(works(place), "industry_02_" + place + " has a model");
        }
        for (int place = 20; place < 24; place++) {
            holds(Industry.of("industry_04_" + place, "Grinder", "Bench", Outdoors.SOUTH, 1, 1, 31), "industry_04_" + place + " has a model");
        }
        for (int place : new int[] {22, 23, 30, 31}) {
            Outdoors.Piece bottle = Industry.of("industry_03_" + place, "Extinguisher", null, Outdoors.NONE, 1, 1, 0);
            holds(bottle, "industry_03_" + place + " has a model");
            Checks.check(bottle != null && bottle.dyeA == bottle.a && bottle.dyeB == bottle.b, "an extinguisher takes its colour where it stands");
        }
    }

    private static void aConveyorsLegsAreAtTheEndItsSpritePaintsThem() {
        // The legs are at the model's back, +Y: east on 40, south on 41, west on 42, north on 43.
        float[][] legs = {{1f, 0f}, {0f, 1f}, {-1f, 0f}, {0f, -1f}};
        for (int i = 0; i < 4; i++) {
            float[] back = way(works(40 + i), 0f, 1f);
            Checks.check(Math.abs(back[0] - legs[i][0]) < 1e-6f && Math.abs(back[1] - legs[i][1]) < 1e-6f,
                    "industry_02_" + (40 + i) + "'s legs are at the end its sprite paints them");
        }
    }

    private static void aBeltRunsThroughAMachineTheWayItsLineDoes() {
        // The map lays 45, 54 and 49 in lines along x, and 44, 55 and 50 in lines along y.
        for (int place : new int[] {45, 54, 49}) {
            Checks.check(Math.abs(way(works(place), 0f, 1f)[0]) == 1f, "industry_02_" + place + "'s belt runs along x");
        }
        for (int place : new int[] {44, 55, 50}) {
            Checks.check(Math.abs(way(works(place), 0f, 1f)[1]) == 1f, "industry_02_" + place + "'s belt runs along y");
        }
        // The two halves of the machine meet: the closed half's front is the edge the outlet's back is at.
        Checks.check(works(48).turns == works(49).turns && works(51).turns == works(50).turns, "a machine's two halves face the same way");
        float[] front = way(works(48), 0f, -1f);
        Checks.check(front[0] == 1f && front[1] == 0f, "the closed half of 48 and 49 is to the west, its open front to the east, where 49 is");
        front = way(works(51), 0f, -1f);
        Checks.check(front[0] == 0f && front[1] == 1f, "the closed half of 51 and 50 is to the north, its open front to the south, where 50 is");
    }

    private static void aTanksHalvesMeetOnItsAxisAndItsHeadsAreAtItsEnds() {
        // A tank along x: the near row (y + 1) and the far row, west to east. Each half lies away from the axis on the
        // row's side of it, the axis is on the edge the rows share, and a head is to the end of the row it closes.
        int[][] rows = {{64, 65, 66, 67}, {68, 69, 70, 71}, {280, 281, 282, 283}, {284, 285, 286, 287}};
        for (int r = 0; r < rows.length; r++) {
            boolean near = r % 2 == 0;
            for (int i = 0; i < 4; i++) {
                Outdoors.Piece piece = works(rows[r][i]);
                float[] half = way(piece, 0f, 1f);
                Checks.check(half[0] == 0f && half[1] == (near ? 1f : -1f) && piece.b == (near ? 0f : 1f) && piece.a == 0.5f,
                        "industry_02_" + rows[r][i] + " is the half of its row, from the axis on the edge the rows share");
                boolean end = i == 0 || i == 3;
                Checks.check(piece.model.startsWith("tank_end") == end, "industry_02_" + rows[r][i] + " is " + (end ? "an end" : "a length"));
                if (end) {
                    float[] head = way(piece, piece.model.equals("tank_end") ? 1f : -1f, 0f);
                    Checks.check(head[1] == 0f && head[0] == (i == 0 ? -1f : 1f), "industry_02_" + rows[r][i] + "'s head is at its end of the tank");
                }
            }
        }
        // A tank along y: the far column (x - 1) and the near one, south to north.
        int[][] columns = {{296, 297, 298, 299}, {300, 301, 302, 303}};
        for (int c = 0; c < 2; c++) {
            boolean near = c == 1;
            for (int i = 0; i < 4; i++) {
                Outdoors.Piece piece = works(columns[c][i]);
                float[] half = way(piece, 0f, 1f);
                Checks.check(half[1] == 0f && half[0] == (near ? 1f : -1f) && piece.a == (near ? 0f : 1f) && piece.b == 0.5f,
                        "industry_02_" + columns[c][i] + " is the half of its column, from the axis on the edge the columns share");
                boolean end = i == 0 || i == 3;
                Checks.check(piece.model.startsWith("tank_end") == end, "industry_02_" + columns[c][i] + " is " + (end ? "an end" : "a length"));
                if (end) {
                    float[] head = way(piece, piece.model.equals("tank_end") ? 1f : -1f, 0f);
                    Checks.check(head[0] == 0f && head[1] == (i == 0 ? 1f : -1f), "industry_02_" + columns[c][i] + "'s head is at its end of the tank");
                }
            }
        }
    }

    private static void aLengthOfTankStandsAsHighAsItsEnds() {
        Models.Model length = Models.get("tank_length"), end = Models.get("tank_end");
        float lengthTop = works(65).raised + length.maxZ, endTop = works(64).raised + end.maxZ;
        Checks.check(Math.abs(lengthTop - endTop) < 0.005f, "a length of tank is as high as the end beside it, " + lengthTop + " against " + endTop);
        Checks.check(Math.abs(length.maxY - end.maxY) < 0.005f, "and as wide");
    }

    private static void aCrateOnACrateStandsWhereTheOneUnderItLeavesOff() {
        float level = ViewCamera.LEVEL_HEIGHT;
        Checks.check(Math.abs(unnamed("carpentry_01_17", 64).raised - level / 3f) < 1e-5f, "the second crate stands on the first, a third of a level up");
        Checks.check(Math.abs(unnamed("carpentry_01_18", 96).raised - 2f * level / 3f) < 1e-5f, "the third on the second");
        Checks.check(unnamed("carpentry_01_17", 64).fits, "and each is no bigger than its square");
    }

    private static void aModelIsAsHighAsTheGameSaysItsTopIs() {
        float belt = 34f / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
        for (String model : new String[] {"conveyor", "conveyor_boxed", "tool_chest"}) {
            Checks.check(Math.abs(Models.get(model).height() - belt) < 0.005f, model + "'s top is where the game puts what is on it");
        }
        float bench = 24f / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
        Checks.check(Math.abs(Models.get("workbench").height() - bench) < 0.005f, "and the workbench's");
        Checks.check(Math.abs(Models.get("downpipe").height() - ViewCamera.LEVEL_HEIGHT) < 0.005f, "a pipe from the ceiling reaches it");
        // What is set on a band saw's or a drill press's table stands where the game says, 30 and 32 pixels up: on the table.
        float pixel = ViewCamera.LEVEL_HEIGHT / WorldScene.SURFACE_PIXELS_PER_LEVEL;
        Checks.check(topAcross(Models.get("band_saw"), 30f * pixel) > 0.5f, "a band saw's table is at the height the game gives what is on it");
        Checks.check(topAcross(Models.get("drill_press"), 32f * pixel) > 0.25f, "and a drill press's");
    }

    private static void aSpriteOfAnotherPlaceOrShapeIsNoIndustrialPiece() {
        Checks.check(works(88) == null && works(160) == null, "a tank's roof and a cabinet are not these");
        Checks.check(workshop(6) == null && workshop(24) == null && workshop(46) == null, "a gas bottle, a trolley of them and a gap in the sheet are no machine");
        Checks.check(Industry.of("industry_04_16", "Duplicator", "Key", Outdoors.SOUTH, 1, 1, 31) == null, "a key cutter is no grinder");
        Checks.check(unnamed("carpentry_01_16", 32) == null && unnamed("carpentry_01_17", 32) == null, "a crate on the floor is Furniture's");
        Checks.check(unnamed("location_business_distillery_01_11", 0) == null, "a still is not a rack");
        Checks.check(Industry.of("industry_02_40", null, null, Outdoors.NONE, 2, 1, 0) == null, "a piece on two squares is no conveyor");
        Checks.check(Industry.of("location_business_machinery_01_16", "Workbench", "Wood Top", Outdoors.SOUTH, 1, 1, 24) == null,
                "a workbench on one square is not the model");
        Checks.check(Industry.of("industry_03_24", "Extinguisher", null, Outdoors.NONE, 1, 1, 0) == null, "nor an extinguisher of another place");
        Checks.check(Outdoors.of("industry_02_40", null, null, Outdoors.NONE, 1, 1, 0) == null, "Outdoors leaves a conveyor to Industry");
    }

    private static Outdoors.Piece workshop(int place) {
        return unnamed("industry_03_" + place, 0);
    }

    private static void aLathesThreeSquaresStandItWithItsHeadstockAtItsEnd() {
        // Head, bed and tail as the map lays them, and the step from the tail's square to the head's.
        int[][] rows = {{0, 1, 2, 0, 1}, {3, 4, 5, -1, 0}, {10, 9, 8, 1, 0}, {13, 12, 11, 0, -1}};
        for (int colour : new int[] {0, 32}) {
            for (int[] row : rows) {
                Outdoors.Piece head = workshop(colour + row[0]), bed = workshop(colour + row[1]), tail = workshop(colour + row[2]);
                String bedName = "industry_03_" + (colour + row[1]), name = "the lathe of " + bedName;
                Checks.check("lathe_head".equals(head.model) && "lathe".equals(bed.model) && tail.model == null && tail.follows,
                        name + " is a head, a bed and a square that stands nothing");
                String headName = "industry_03_" + (colour + row[0]), tailName = "industry_03_" + (colour + row[2]);
                // Each square names the other two and where they are: the head at one end, a step from the bed, the tail at the other.
                int[][] at = {{0, 0}, {-row[3], -row[4]}, {-2 * row[3], -2 * row[4]}};
                String[] names = {headName, bedName, tailName};
                Outdoors.Piece[] pieces = {head, bed, tail};
                for (int i = 0; i < 3; i++) {
                    Outdoors.Piece piece = pieces[i];
                    boolean right = piece.row != null && piece.row.length == 2 && piece.partner == null;
                    for (int k = 0; right && k < 2; k++) {
                        int other = -1;
                        for (int j = 0; j < 3; j++) {
                            if (names[j].equals(piece.row[k])) {
                                other = j;
                            }
                        }
                        right = other >= 0 && other != i && piece.rowX[k] == at[other][0] - at[i][0] && piece.rowY[k] == at[other][1] - at[i][1];
                    }
                    Checks.check(right, name + ": " + names[i] + " names the other two squares of its row and where each is");
                }
                // The models' headstock is at their -X, towards the head's square; their front is across the row.
                float[] headward = way(bed, -1f, 0f), front = way(bed, 0f, -1f);
                Checks.check(Math.round(headward[0]) == row[3] && Math.round(headward[1]) == row[4], name + "'s headstock is at the head's square");
                Checks.check(Math.round(front[0] * row[3] + front[1] * row[4]) == 0, name + " faces across its row");
                // Looking at its front, the headstock is on the left: the head is the front turned a quarter clockwise on the map.
                Checks.check(Math.round(-front[1]) == row[3] && Math.round(front[0]) == row[4], name + "'s headstock is at the operator's left");
                Checks.check(head.turns == bed.turns && head.a == 0.5f && head.b == 0.5f && bed.a == 0.5f && bed.b == 0.5f,
                        name + "'s two models turn together about their squares' middles");
                for (Outdoors.Piece piece : pieces) {
                    String dyer = piece.rowDyer < 0 ? headName : piece.row[piece.rowDyer];
                    Checks.check(dyer.equals(headName) && piece.dyed == head.dyed && piece.dyeA == head.dyeA && piece.dyeB == head.dyeB && piece.dyeHigh == head.dyeHigh,
                            name + ": every square takes the colour from the headstock's sprite, where the headstock does");
                }
            }
        }
        Models.Model lathe = Models.get("lathe");
        Checks.check(lathe.maxX > 1.4f && lathe.minX < -1.4f, "a lathe is three squares long");
    }

    private static void aWorkshopsMachinesFaceTheWayTheirSpritesDo() {
        // A press has its back to the wall the map stands it against: 50 and 54 a west wall, 51 and 55 a north one.
        int[] faces = {Outdoors.WEST, Outdoors.NORTH, Outdoors.EAST, Outdoors.SOUTH};
        for (int place = 48; place < 64; place++) {
            Outdoors.Piece press = workshop(place);
            Checks.check(press.turns == faces[(place - 48) % 4], "industry_03_" + place + " faces the way its sprite paints its table");
            Checks.check(press.model.equals(place < 56 ? "press" : "press_loaded"), "industry_03_" + place + (place < 56 ? " is empty" : " has a die"));
        }
        int[] saws = {Outdoors.SOUTH, Outdoors.WEST, Outdoors.NORTH, Outdoors.EAST}, drills = {Outdoors.EAST, Outdoors.SOUTH, Outdoors.NORTH, Outdoors.WEST};
        for (int i = 0; i < 4; i++) {
            Checks.check(works(264 + i).turns == saws[i] && works(268 + i).turns == drills[i], "industry_02_" + (264 + i) + " and " + (268 + i) + " face the way their sprites do");
        }
        Checks.check(Math.abs(way(works(46), 0f, 1f)[1]) == 1f && Math.abs(way(works(47), 0f, 1f)[0]) == 1f, "a line press's belt runs along its line");
    }

    private static void aBenchGrinderStandsOnItsBench() {
        int[] faces = {Outdoors.SOUTH, Outdoors.EAST, Outdoors.NORTH, Outdoors.WEST};
        float bench = 31f / WorldScene.SURFACE_PIXELS_PER_LEVEL;
        for (int i = 0; i < 4; i++) {
            Outdoors.Piece grinder = Industry.of("industry_04_" + (20 + i), "Grinder", "Bench", faces[i], 1, 1, 31);
            Checks.check(grinder.turns == faces[i], "industry_04_" + (20 + i) + " faces the way the game says");
            Checks.check(Math.abs(grinder.raised - bench * ViewCamera.LEVEL_HEIGHT) < 1e-5f, "industry_04_" + (20 + i) + " stands on its bench's top");
            Checks.check(grinder.dyeHigh > bench, "industry_04_" + (20 + i) + " takes its colour above the bench, from its case");
        }
    }

    /**
     * Every square of a lathe reaches one answer. With all three sprites there each stands its share, the tail nothing; with
     * the head gone none does, so the bed and tail are the game's sprites again; with the head's square not loaded yet the
     * bed and tail wait for it; and where the headstock's sprite paints nothing where the colour is taken none stands.
     */
    private static void aLathesThreeSquaresStandOrNotAlike() {
        String head = "industry_03_35", bed = "industry_03_36", tail = "industry_03_37";
        int[][] at = {{7382, 8349}, {7383, 8349}, {7384, 8349}};
        String[] names = {head, bed, tail};
        Furniture[] pieces = new Furniture[3];
        for (int i = 0; i < 3; i++) {
            pieces[i] = Furniture.known(TileRows.sprite(names[i] + "|BlocksPlacement||MaterialType|Metal|solidtrans||"));
            Checks.check(pieces[i] != null, names[i] + " is a square of a lathe");
            if (pieces[i] == null) {
                return;
            }
        }
        Checks.check(pieces[2].follows && !pieces[0].follows && !pieces[1].follows, "the tail's square stands nothing of its own");
        java.util.function.IntFunction<Towers.Squares> map = gone -> (x, y, z, sprite) -> {
            for (int j = 0; j < 3; j++) {
                if (x == at[j][0] && y == at[j][1]) {
                    return gone == j ? Boolean.FALSE : gone == j + 3 ? null : sprite.equals(names[j]);
                }
            }
            return false;
        };
        for (int i = 0; i < 3; i++) {
            Checks.check(FurnishingBuilder.othersThere(pieces[i], map.apply(-1), at[i][0], at[i][1], 0) == Boolean.TRUE, names[i] + " stands with the whole lathe there");
            for (int j = 0; j < 3; j++) {
                if (j != i) {
                    Checks.check(FurnishingBuilder.othersThere(pieces[i], map.apply(j), at[i][0], at[i][1], 0) == Boolean.FALSE, names[i] + " is its sprite with " + names[j] + " gone");
                    Checks.check(FurnishingBuilder.othersThere(pieces[i], map.apply(j + 3), at[i][0], at[i][1], 0) == null, names[i] + " waits for " + names[j] + "'s square to load");
                }
            }
        }
        // The colour: every square reads it at the one place of the headstock's sprite the head reads it at, so where that is unpainted none stands.
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.width = 128f;
        frame.height = 256f;
        frame.uEnd = 1f;
        frame.vEnd = 1f;
        FurnishingBuilder builder = new FurnishingBuilder(null);
        int paintU = Math.round(SpriteGeometry.frameU(frame, pieces[0].dyeA, pieces[0].dyeB)), paintV = Math.round(SpriteGeometry.frameV(frame, pieces[0].dyeA, pieces[0].dyeB, pieces[0].dyeHigh));
        Checks.check(builder.dyes((u, v) -> u == paintU && v == paintV, frame, pieces[0], head), "the headstock takes its colour at its own place");
        float headU = builder.dyeU, headV = builder.dyeV;
        for (int i = 0; i < 3; i++) {
            String dyer = pieces[i].rowDyer < 0 ? names[i] : pieces[i].row[pieces[i].rowDyer];
            Checks.check(dyer.equals(head), names[i] + " takes its colour from the headstock's sprite");
            Checks.check(builder.dyes((u, v) -> u == paintU && v == paintV, frame, pieces[i], head) && builder.dyeU == headU && builder.dyeV == headV,
                    names[i] + " reads the colour at the headstock's place on its sprite");
            Checks.check(!builder.dyes((u, v) -> false, frame, pieces[i], head), names[i] + " does not stand where the headstock's sprite paints nothing");
        }
    }

    /** How wide, along the model's x, the faces turned up within 3 mm of high are: a table's top at that height, or 0. */
    private static float topAcross(Models.Model model, float high) {
        float least = Float.MAX_VALUE, most = -Float.MAX_VALUE;
        float[] v = model.vertices;
        for (int at = 0; at < v.length; at += Models.VERTEX_FLOATS) {
            if (v[at + 5] > 0.99f && Math.abs(v[at + 2] - high) < 0.003f) {
                least = Math.min(least, v[at]);
                most = Math.max(most, v[at]);
            }
        }
        return Math.max(most - least, 0f);
    }
}

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
    }

    private static void aSpriteOfAnotherPlaceOrShapeIsNoIndustrialPiece() {
        Checks.check(works(46) == null && works(47) == null && works(88) == null && works(160) == null, "a press, a tank's roof and a cabinet are not these");
        Checks.check(unnamed("carpentry_01_16", 32) == null && unnamed("carpentry_01_17", 32) == null, "a crate on the floor is Furniture's");
        Checks.check(unnamed("location_business_distillery_01_11", 0) == null, "a still is not a rack");
        Checks.check(Industry.of("industry_02_40", null, null, Outdoors.NONE, 2, 1, 0) == null, "a piece on two squares is no conveyor");
        Checks.check(Industry.of("location_business_machinery_01_16", "Workbench", "Wood Top", Outdoors.SOUTH, 1, 1, 24) == null,
                "a workbench on one square is not the model");
        Checks.check(Industry.of("industry_03_24", "Extinguisher", null, Outdoors.NONE, 1, 1, 0) == null, "nor an extinguisher of another place");
        Checks.check(Outdoors.of("industry_02_40", null, null, Outdoors.NONE, 1, 1, 0) == null, "Outdoors leaves a conveyor to Industry");
    }
}

package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import zombie.iso.SpriteDetails.IsoObjectType;

/** Tests of which sprites are a shop's awning or a hood over a lone wall, and of the models that hang in their place. */
final class AwningTests {
    private AwningTests() {}

    static void run() {
        Checks.run("everyStraightCanopyTheMapPlacesHasAModel", AwningTests::everyStraightCanopyTheMapPlacesHasAModel);
        Checks.run("aQuarterOfAParasolOrAnUnknownCanopyIsNoAwning", AwningTests::aQuarterOfAParasolOrAnUnknownCanopyIsNoAwning);
        Checks.run("anAwningHangsOnTheWallBehindTheWayItFaces", AwningTests::anAwningHangsOnTheWallBehindTheWayItFaces);
        Checks.run("anAwningsLengthsMeetAtTheEdgesOfTheirSquares", AwningTests::anAwningsLengthsMeetAtTheEdgesOfTheirSquares);
        Checks.run("anAwningsColoursAreReadWhereItsModelHasCloth", AwningTests::anAwningsColoursAreReadWhereItsModelHasCloth);
        Checks.run("anEaveIsTheEaveRowOfARoofSheet", AwningTests::anEaveIsTheEaveRowOfARoofSheet);
        Checks.run("aKioskHasAHoodHippedAtBothEndsAndItsEndPiecesGo", AwningTests::aKioskHasAHoodHippedAtBothEndsAndItsEndPiecesGo);
        Checks.run("theCornerPastARoofsEaveRowGoesWithTheRow", AwningTests::theCornerPastARoofsEaveRowGoesWithTheRow);
        Checks.run("anEaveWithARoofBehindItIsNoHood", AwningTests::anEaveWithARoofBehindItIsNoHood);
        Checks.run("aHoodHangsFromTheTopOfItsWallAndItsHipsReachPastIt", AwningTests::aHoodHangsFromTheTopOfItsWallAndItsHipsReachPastIt);
    }

    /** Every straight canopy the shipped map places, as the inventory of 2026-10-09 counted them, and the way each faces. */
    private static final String[][] PLACED = {
        {"walls_decoration_01_84", "S"}, {"walls_decoration_01_85", "E"}, {"walls_decoration_01_92", "N"}, {"walls_decoration_01_93", "W"},
        {"walls_decoration_01_100", "S"}, {"walls_decoration_01_101", "E"}, {"walls_decoration_01_108", "N"}, {"walls_decoration_01_109", "W"},
        {"walls_decoration_01_116", "S"}, {"walls_decoration_01_117", "E"}, {"walls_decoration_01_124", "N"}, {"walls_decoration_01_125", "W"},
        {"walls_decoration_01_128", "S"}, {"walls_decoration_01_129", "E"}, {"walls_decoration_01_136", "N"},
        {"walls_decoration_01_132", "S"}, {"walls_decoration_01_133", "E"}, {"walls_decoration_01_140", "N"}, {"walls_decoration_01_141", "W"},
        {"walls_decoration_01_68", "S"}, {"walls_decoration_01_69", "E"}, {"walls_decoration_01_70", "S"}, {"walls_decoration_01_71", "E"},
        {"walls_decoration_01_112", "S"}, {"walls_decoration_01_113", "E"}, {"walls_decoration_01_114", "S"}, {"walls_decoration_01_115", "E"},
        {"walls_decoration_01_120", "S"}, {"walls_decoration_01_121", "E"}, {"walls_decoration_01_122", "S"}, {"walls_decoration_01_123", "E"},
        {"walls_decoration_01_145", "E"}, {"walls_decoration_01_148", "W"}, {"walls_decoration_01_149", "N"}, {"walls_decoration_01_152", "S"},
        {"walls_decoration_01_157", "N"},
        {"location_restaurant_pie_01_4", "S"}, {"location_restaurant_pie_01_5", "E"}, {"location_restaurant_pileocrepe_01_4", "S"},
        {"location_restaurant_pileocrepe_01_5", "E"}, {"location_restaurant_bar_smutz_12", "S"}, {"location_restaurant_bar_smutz_13", "E"},
        {"location_restaurant_bar_smutz_14", "E"}, {"location_restaurant_bar_smutz_15", "E"},
    };

    private static void everyStraightCanopyTheMapPlacesHasAModel() {
        HashSet<String> models = new HashSet<>();
        for (String[] placed : PLACED) {
            Awnings.Hung hung = Awnings.of(placed[0], "Canopy", "Red", placed[1]);
            check(hung != null, placed[0] + " is an awning");
            if (hung != null) {
                Models.get(hung.model);
                models.add(hung.model);
            }
        }
        check(models.size() == 5, "five shapes of awning: " + models);
        check(Awnings.of("walls_decoration_01_100", "Canopy", "Red and white striped", "S").model.equals("awning"), "the striped one in the owner's picture");
    }

    private static void aQuarterOfAParasolOrAnUnknownCanopyIsNoAwning() {
        check(Awnings.of("walls_decoration_01_86", "Canopy", "Curved blue and white striped", "S") == null, "a quarter of a parasol is Parasols'");
        check(Awnings.of("walls_decoration_01_100", "Canopy", "Red and white striped", null) == null, "a canopy that faces no way");
        check(Awnings.of("walls_decoration_01_100", "Lights", "Red and white striped", "S") == null, "something else on the sheet");
        check(Awnings.of("walls_decoration_01_999", "Canopy", "Red", "S") == null, "a canopy no one has looked at");
        check(Awnings.of("location_restaurant_pie_01_6", "Canopy", "Pink", "S") == null, "another sprite of a shop's sheet");
    }

    private static void anAwningHangsOnTheWallBehindTheWayItFaces() {
        // Facing south an awning hangs on the north wall of its square and runs out south, and so on round.
        String[] facing = {"N", "E", "S", "W"};
        float[][] backTo = {{0f, 1f}, {-1f, 0f}, {0f, -1f}, {1f, 0f}};
        float[] centre = new float[2];
        for (int way = 0; way < 4; way++) {
            Awnings.Hung hung = Awnings.of("walls_decoration_01_100", "Canopy", "Red and white striped", facing[way]);
            Models.Model model = Models.get(hung.model);
            hung.centre(model.depth(), centre);
            float backX = centre[0] + backTo[way][0] * model.depth() / 2f, backY = centre[1] + backTo[way][1] * model.depth() / 2f;
            float wallX = backTo[way][0] > 0f ? 1f : backTo[way][0] < 0f ? 0f : backX, wallY = backTo[way][1] > 0f ? 1f : backTo[way][1] < 0f ? 0f : backY;
            float gap = Math.abs(backX - wallX) + Math.abs(backY - wallY);
            check(Math.abs(gap - Awnings.BACK) < 0.001f, "facing " + facing[way] + " its back is clear of the wall behind it by " + gap);
            check(Math.abs(centre[0] - 0.5f) < 0.001f || Math.abs(centre[1] - 0.5f) < 0.001f, "facing " + facing[way] + " it is in the middle of its square along the wall");
            float frontX = centre[0] - backTo[way][0] * model.depth() / 2f, frontY = centre[1] - backTo[way][1] * model.depth() / 2f;
            check(frontX >= -0.005f && frontX <= 1.005f && frontY >= -0.005f && frontY <= 1.005f, "facing " + facing[way] + " it reaches no further than its square");
        }
    }

    private static void anAwningsLengthsMeetAtTheEdgesOfTheirSquares() {
        for (String name : new String[] {"awning", "awning_hemmed", "awning_pointed", "awning_short", "awning_dome"}) {
            Models.Model model = Models.get(name);
            check(Math.abs(model.minX + 0.5f) < 0.001f && Math.abs(model.maxX - 0.5f) < 0.001f, name + " is a square long, end to end: " + model.minX + " to " + model.maxX);
            float lowest = Float.MAX_VALUE;
            for (int vertex = model.first[Models.CLOTH]; vertex < model.first[Models.LINEN + 1]; vertex++) {
                lowest = Math.min(lowest, model.vertices[vertex * Models.VERTEX_FLOATS + 2]);
            }
            check(lowest == 0f, name + "'s valance is its lowest point, which the foot it is hung by is: " + lowest);
            for (int surface : new int[] {Models.CLOTH, Models.LINEN}) {
                check(model.first[surface + 1] > model.first[surface], name + " has cloth of both colours");
            }
        }
    }

    private static void anAwningsColoursAreReadWhereItsModelHasCloth() {
        String[][] kinds = {{"walls_decoration_01_100", "S"}, {"walls_decoration_01_109", "W"}, {"walls_decoration_01_70", "S"}, {"walls_decoration_01_152", "S"}};
        float[] centre = new float[2];
        for (String[] kind : kinds) {
            Awnings.Hung hung = Awnings.of(kind[0], "Canopy", "Red", kind[1]);
            Models.Model model = Models.get(hung.model);
            hung.centre(model.depth(), centre);
            for (float[] at : new float[][] {{hung.firstA, hung.firstB}, {hung.secondA, hung.secondB}}) {
                check(at[0] > 0f && at[0] < 1f && at[1] > 0f && at[1] < 1f, kind[0] + " is read on its own square");
                float highest = clothOver(model, hung.turns, at[0] - centre[0], at[1] - centre[1]);
                float clothAt = hung.foot + highest / ViewCamera.LEVEL_HEIGHT;
                check(highest >= 0f && Math.abs(clothAt - hung.dyeHigh) < 0.03f,
                        kind[0] + " is read at the height its model's cloth is at there: " + clothAt + " levels, read at " + hung.dyeHigh);
            }
        }
    }

    /** How high the upper face of a model's cloth is over a place, from the model's origin on the map, turned as it stands: -1 where it has none. */
    private static float clothOver(Models.Model model, int turns, float x, float y) {
        float[] p = new float[6];
        float highest = -1f;
        for (int corner = 0; corner < model.corners.length; corner += 3) {
            boolean cloth = true;
            for (int k = 0; k < 3; k++) {
                int vertex = model.corners[corner + k];
                cloth &= vertex >= model.first[Models.CLOTH] && vertex < model.first[Models.LINEN + 1];
                float[] turned = new float[2];
                Models.turn(turns, model.vertices[vertex * Models.VERTEX_FLOATS], model.vertices[vertex * Models.VERTEX_FLOATS + 1], turned);
                p[2 * k] = turned[0];
                p[2 * k + 1] = turned[1];
            }
            float d = (p[3] - p[5]) * (p[0] - p[4]) + (p[4] - p[2]) * (p[1] - p[5]);
            if (!cloth || Math.abs(d) < 1e-9f) {
                continue;
            }
            float u = ((p[3] - p[5]) * (x - p[4]) + (p[4] - p[2]) * (y - p[5])) / d, v = ((p[5] - p[1]) * (x - p[4]) + (p[0] - p[4]) * (y - p[5])) / d;
            if (u >= 0f && v >= 0f && u + v <= 1f) {
                int a = model.corners[corner], b = model.corners[corner + 1], c = model.corners[corner + 2];
                float z = u * model.vertices[a * Models.VERTEX_FLOATS + 2] + v * model.vertices[b * Models.VERTEX_FLOATS + 2]
                        + (1f - u - v) * model.vertices[c * Models.VERTEX_FLOATS + 2];
                highest = Math.max(highest, z);
            }
        }
        return highest;
    }

    private static void anEaveIsTheEaveRowOfARoofSheet() {
        check(Hoods.eaveOf("roofs_04_7", false, true, true) == Outdoors.WEST, "the kiosk's eave, on its west wall");
        check(Hoods.eaveOf("roofs_04_6", true, false, true) == Outdoors.NORTH, "the other kiosk's, on its north wall");
        check(Hoods.eaveOf("roofs_burnt_01_6", true, false, true) == Outdoors.NORTH, "a burnt roof's");
        check(Hoods.eaveOf("roofs_05_39", false, true, true) == Outdoors.WEST, "the eave of a sheet's second finish, on its west wall");
        check(Hoods.eaveOf("roofs_01_38", true, false, true) == Outdoors.NORTH, "the other, on its north wall");
        check(Hoods.eaveOf("roofs_01_63", false, true, true) == Hoods.NONE, "a hip's end in the second finish");
        check(Hoods.eaveOf("roofs_04_21", false, false, true) == Hoods.NONE, "a hip's end");
        check(Hoods.eaveOf("roofs_04_31", false, true, true) == Hoods.NONE, "the other hip's end");
        check(Hoods.eaveOf("roofs_04_7", false, true, false) == Hoods.NONE, "a tile the game calls no eave");
        check(Hoods.eaveOf("roofs_04_6", false, true, true) == Hoods.NONE, "an eave fixed to an edge it is not drawn on");
        check(Hoods.eaveOf("walls_decoration_01_7", false, true, true) == Hoods.NONE, "another sheet");
        check(Hoods.eaveOf("roofs_accents_01_7", false, true, true) == Hoods.NONE, "the accents of a roof");
    }

    /** A level of a made-up map. */
    private static final class Pier implements Hoods.Squares {
        final HashMap<String, Integer> eaves = new HashMap<>();
        final HashMap<String, Integer> walls = new HashMap<>();
        final HashSet<String> filledAbove = new HashSet<>(), ends = new HashSet<>();

        @Override
        public int eave(int x, int y) {
            return eaves.getOrDefault(x + "," + y, Hoods.NONE);
        }

        @Override
        public boolean walled(int x, int y, int wall) {
            return walls.getOrDefault(x + "," + y, Hoods.NONE) == wall;
        }

        @Override
        public boolean emptyAbove(int x, int y) {
            return !filledAbove.contains(x + "," + y);
        }

        @Override
        public boolean endPiece(int x, int y) {
            return ends.contains(x + "," + y);
        }
    }

    /** The kiosk at (6433, 5186..5189): a wall on the west edge of two squares with the eave on them, and an end piece on the square past each end. */
    private static Pier kiosk() {
        Pier pier = new Pier();
        for (int y = 1; y <= 2; y++) {
            pier.eaves.put("0," + y, Outdoors.WEST);
            pier.walls.put("0," + y, Outdoors.WEST);
        }
        pier.ends.add("0,0");
        pier.ends.add("0,3");
        return pier;
    }

    /**
     * The one-storey house at (6409..6416, 5358): the eave row of its roof on the north wall of
     * six squares of the ground level, under the roof on the squares north of them, and the
     * corner piece past its west end. Under a roof it is no hood, and its corner is the row's.
     */
    private static void theCornerPastARoofsEaveRowGoesWithTheRow() {
        // As the map has it at the one-storey house at (6409, 5358): a roof on level 1 whose slope
        // falls south to its floor over x 1 to 6, the row of eave it hangs on the north wall of
        // the squares under its foot on level 0, and the corner piece past the row's west end.
        TestMap map = new TestMap();
        ArrayList<RoofFit.Tile> slope = new ArrayList<>();
        for (int x = 1; x <= 6; x++) {
            slope.add(new RoofFit.Tile(x, -1, 1, RoofFit.ALONG_Y, true, 1f + RoofFit.PITCH, 1f));
            map.roofSprite(x, 0, 0, "roofs_02_6", "attachedN", "isEave");
            map.wall(x, 0, 0, true);
        }
        RoofFit.fit(slope);
        for (RoofFit.Tile tile : slope) {
            map.roofTile(tile, map.painted);
        }
        map.roofSprite(0, 0, 0, "roofs_02_30", "attachedN", "isEave");
        map.roofSprite(3, 1, 0, "roofs_02_30", "attachedN", "isEave");
        RoofBuilder roofs = map.build.roofBuilder;
        check(roofs.eaveRow(1, 0, 0) && roofs.eaveRow(6, 0, 0), "the row under the slope's foot is that slope's eave row");
        check(roofs.eaveRowEnd(0, 0, 0), "the corner piece past the west end of the row goes with the row");
        check(!roofs.eaveRowEnd(3, 1, 0), "an end piece in front of the row, across its wall, is none of its");
        check(!roofs.eaveRowEnd(7, 0, 0), "nor is a square with no end piece");
        TestMap bare = new TestMap();
        bare.roofSprite(1, 0, 0, "roofs_02_6", "attachedN", "isEave");
        bare.wall(1, 0, 0, true);
        bare.roofSprite(0, 0, 0, "roofs_02_30", "attachedN", "isEave");
        check(!bare.build.roofBuilder.eaveRow(1, 0, 0) && !bare.build.roofBuilder.eaveRowEnd(0, 0, 0),
                "a row with no slope over it is no roof's eave row, nor the corner past it a row's");
        check(!roofs.eaveRowOfItsOwnEnd(0, 0, 0), "the corner past a slope's eave row is no row of its own's");
        check(bare.build.roofBuilder.addEaveRow(bare.painted, 1, 0, 0) && TestMap.plain().triangles() > 0,
                "a row with no slope over it is made an eave of its own: its soffit, board and gutter");
        check(bare.build.roofBuilder.eaveRowOfItsOwnEnd(0, 0, 0), "and the corner past it goes with that eave");
        TestMap loose = new TestMap();
        loose.roofSprite(1, 0, 0, "roofs_02_6", "attachedN", "isEave");
        check(!loose.build.roofBuilder.addEaveRow(loose.painted, 1, 0, 0) && TestMap.plain().triangles() == 0,
                "a row with no wall under it to hang from is made nothing");
        TestMap glazed = new TestMap();
        glazed.roofSprite(1, 0, 0, "roofs_02_6", "attachedN", "isEave");
        glazed.cell.square(1, 0, 0).add(glazed.sprite("walls_exterior_house_02_41", IsoObjectType.wall, "WindowN"));
        check(glazed.build.roofBuilder.addEaveRow(glazed.painted, 1, 0, 0), "a row over a wall a window is in hangs from that wall");
    }

    private static void aKioskHasAHoodHippedAtBothEndsAndItsEndPiecesGo() {
        Pier pier = kiosk();
        check(Hoods.hood(pier, 0, 1) && Hoods.hood(pier, 0, 2), "both squares of the wall hold a hood");
        // Hung on a west wall the model faces east, and its +x end is to the north.
        check(Hoods.modelOf(pier, 0, 1).equals("hood_end_plus"), "the north length is hipped at its north end: " + Hoods.modelOf(pier, 0, 1));
        check(Hoods.modelOf(pier, 0, 2).equals("hood_end_minus"), "the south length at its south end: " + Hoods.modelOf(pier, 0, 2));
        check(Hoods.endOfAHood(pier, 0, 0) && Hoods.endOfAHood(pier, 0, 3), "the end pieces past both ends are the hips'");
        pier.eaves.remove("0,2");
        pier.walls.remove("0,2");
        check(Hoods.modelOf(pier, 0, 1).equals("hood_alone"), "a hood one square long is hipped at both ends");
        check(!Hoods.endOfAHood(pier, 0, 3), "an end piece two squares from the hood is none of its");
        pier.ends.add("1,1");
        check(!Hoods.endOfAHood(pier, 1, 1), "an end piece in front of the hood, across its wall, is none of its");
        Pier north = new Pier();
        north.eaves.put("1,0", Outdoors.NORTH);
        north.walls.put("1,0", Outdoors.NORTH);
        north.ends.add("0,0");
        check(Hoods.hood(north, 1, 0) && Hoods.modelOf(north, 1, 0).equals("hood_alone") && Hoods.endOfAHood(north, 0, 0), "a hood on a north wall runs along x");
    }

    private static void anEaveWithARoofBehindItIsNoHood() {
        Pier pier = kiosk();
        pier.filledAbove.add("-1,1");
        check(!Hoods.hood(pier, 0, 1), "the eave of a roof, which is on the level above the square behind it");
        check(Hoods.hood(pier, 0, 2), "the next square's eave has nothing behind it");
        Pier unwalled = kiosk();
        unwalled.walls.clear();
        check(!Hoods.hood(unwalled, 0, 1), "an eave on a square with no wall on its edge");
        check(!Hoods.endOfAHood(unwalled, 0, 0), "then the end piece is the roof's too");
    }

    private static void aHoodHangsFromTheTopOfItsWallAndItsHipsReachPastIt() {
        float[] origin = new float[2];
        Hoods.origin(Outdoors.WEST, origin);
        check(origin[0] == 0f && origin[1] == 0.5f, "on its west wall a hood hangs from the middle of that edge");
        Hoods.origin(Outdoors.NORTH, origin);
        check(origin[0] == 0.5f && origin[1] == 0f, "on its north wall from the middle of that one");
        check(Hoods.turns(Outdoors.WEST) == Outdoors.EAST && Hoods.turns(Outdoors.NORTH) == Outdoors.SOUTH, "it faces away from its wall");
        for (String name : new String[] {"hood", "hood_end_plus", "hood_end_minus", "hood_alone"}) {
            Models.Model model = Models.get(name);
            float top = Hoods.FOOT + model.maxZ / ViewCamera.LEVEL_HEIGHT;
            check(Math.abs(top - 1f) < 0.01f, name + "'s top is the top of its wall: " + top + " levels");
            check(model.maxY < -WorldScene.WALL_HALF && model.minY > -0.45f, name + " hangs clear of its wall's skin and reaches out as far as the game's eave");
        }
        check(Models.get("hood_end_plus").maxX > 0.8f && Models.get("hood_end_plus").minX == -0.5f, "a hip reaches past the end of the wall");
        check(Models.get("hood").maxX == 0.5f && Models.get("hood").minX == -0.5f, "a length with no end meets the next at the square's edge");
        float[] dye = Hoods.dyedAt(Outdoors.WEST);
        check(dye[0] < 0.4f && dye[1] > 0f && dye[1] < 1f, "its colour is read on the eave's own square, where the eave is");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}

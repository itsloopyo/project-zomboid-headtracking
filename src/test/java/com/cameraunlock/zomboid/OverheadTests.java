package com.cameraunlock.zomboid;

/** Tests of the steel frame, ducts, wide pipes and columns Overhead stands, and of the edges it lays wall sprites on. */
final class OverheadTests {
    private OverheadTests() {}

    static void run() {
        Checks.run("everyFramePieceNamesAModelTheJarHolds", OverheadTests::everyFramePieceNamesAModelTheJarHolds);
        Checks.run("aGirderRunsAlongTheEdgeItsSpriteIsPaintedBy", OverheadTests::aGirderRunsAlongTheEdgeItsSpriteIsPaintedBy);
        Checks.run("aHungGirderAndDuctHaveTheirTopsAtTheCeiling", OverheadTests::aHungGirderAndDuctHaveTheirTopsAtTheCeiling);
        Checks.run("aColumnStandsInTheCornerItsSpriteIsPaintedIn", OverheadTests::aColumnStandsInTheCornerItsSpriteIsPaintedIn);
        Checks.run("aWidePipesFittingHasItsEndsWhereItsSpritePaintsThem", OverheadTests::aWidePipesFittingHasItsEndsWhereItsSpritePaintsThem);
        Checks.run("aDuctClimbsTheWallItIsFixedTo", OverheadTests::aDuctClimbsTheWallItIsFixedTo);
        Checks.run("wallSpritesGoOnTheEdgeTheirPictureIsOn", OverheadTests::wallSpritesGoOnTheEdgeTheirPictureIsOn);
        Checks.run("aCornerOfTimbersLiesAlongTheTwoEdgesOfItsCorner", OverheadTests::aCornerOfTimbersLiesAlongTheTwoEdgesOfItsCorner);
        Checks.run("aFrameOfScaffoldingStandsOnItsEdgeWithItsStandardWhereTheSpritePaintsIt", OverheadTests::aFrameOfScaffoldingStandsOnItsEdgeWithItsStandardWhereTheSpritePaintsIt);
    }

    private static Outdoors.Piece unnamed(String name) {
        return Outdoors.of(name, null, null, Outdoors.NONE, 1, 1, 0);
    }

    private static float[] onMap(Outdoors.Piece piece, float x, float y) {
        float[] out = new float[2];
        Models.turn(piece.turns, x, y, out);
        return out;
    }

    private static void everyFramePieceNamesAModelTheJarHolds() {
        int[] site = {40, 41, 48, 49, 50, 51, 52, 53, 54, 55, 80, 81, 82, 83, 84, 87, 88, 89, 96, 97, 104, 105, 112, 113};
        for (int place : site) {
            Outdoors.Piece piece = unnamed("construction_01_" + place);
            Checks.check(piece != null, "construction_01_" + place + " has a model");
            if (piece != null) {
                Models.get(piece.model);
            }
        }
        int[] works = {4, 5, 6, 8, 9, 10, 11, 16, 17, 18, 19, 24, 25, 26, 27, 28, 29, 30, 31, 35, 36, 37, 38, 39};
        for (int place : works) {
            Outdoors.Piece piece = unnamed("industry_02_" + place);
            Checks.check(piece != null, "industry_02_" + place + " has a model");
            if (piece != null) {
                Models.get(piece.model);
            }
        }
        for (int place : new int[] {79, 87}) {
            Outdoors.Piece piece = unnamed("location_community_church_small_01_" + place);
            Checks.check(piece != null, "the church's column " + place + " has a model");
            if (piece != null) {
                Models.get(piece.model);
            }
        }
        Checks.check(unnamed("construction_01_92") == null && unnamed("construction_01_107") == null && unnamed("industry_02_34") == null,
                "a girder that stops short of its square's end, and the upright pipe, stay as they are");
        Checks.check(Outdoors.of("construction_01_50", null, null, Outdoors.NONE, 2, 1, 0) == null, "a girder of two squares is none of these");
    }

    private static void aGirderRunsAlongTheEdgeItsSpriteIsPaintedBy() {
        Outdoors.Piece north = unnamed("construction_01_104"), south = unnamed("construction_01_105"), west = unnamed("construction_01_113"),
                east = unnamed("construction_01_112");
        Checks.check(Math.abs(onMap(north, 0f, 1f)[0]) == 1f && north.b < 0.2f && north.a == 0.5f, "construction_01_104 runs along x by the north edge");
        Checks.check(Math.abs(onMap(south, 0f, 1f)[0]) == 1f && south.b > 0.8f, "construction_01_105 runs along x by the south edge");
        Checks.check(Math.abs(onMap(west, 0f, 1f)[1]) == 1f && west.a < 0.2f && west.b == 0.5f, "construction_01_113 runs along y by the west edge");
        Checks.check(Math.abs(onMap(east, 0f, 1f)[1]) == 1f && east.a > 0.8f, "construction_01_112 runs along y by the east edge");
        Checks.check(unnamed("construction_01_88").raised == 0f && unnamed("construction_01_97").raised == 0f, "the girders painted on the floor lie on it");
        Checks.check(north.dyeA == north.a && north.dyeB == north.b, "a girder takes its colour from where it runs");
    }

    private static void aHungGirderAndDuctHaveTheirTopsAtTheCeiling() {
        float ceiling = ViewCamera.LEVEL_HEIGHT;
        for (String name : new String[] {"construction_01_50", "construction_01_54", "industry_02_4", "industry_02_5", "industry_02_6"}) {
            Outdoors.Piece piece = unnamed(name);
            float top = piece.raised + Models.get(piece.model).height();
            Checks.check(Math.abs(top - ceiling) < 0.02f, name + " has its top at the ceiling, not " + top);
            Checks.check(piece.dyeHigh > 0.8f && piece.dyeHigh < 1f, name + " takes its colour up where its sprite paints it: " + piece.dyeHigh);
        }
    }

    private static void aColumnStandsInTheCornerItsSpriteIsPaintedIn() {
        Outdoors.Piece southEast = unnamed("construction_01_48"), southWest = unnamed("construction_01_80"), northEast = unnamed("construction_01_81"),
                northWest = unnamed("construction_01_83");
        Checks.check(southEast.a > 0.8f && southEast.b > 0.8f, "48 stands in the south-east corner");
        Checks.check(southWest.a < 0.2f && southWest.b > 0.8f, "80 in the south-west");
        Checks.check(northEast.a > 0.8f && northEast.b < 0.2f, "81 in the north-east");
        Checks.check(northWest.a < 0.2f && northWest.b < 0.2f, "83 in the north-west");
        Checks.check(Math.abs(Models.get(southEast.model).height() - ViewCamera.LEVEL_HEIGHT) < 0.01f && southEast.raised == 0f, "a column is a storey high from the floor");
    }

    private static void aWidePipesFittingHasItsEndsWhereItsSpritePaintsThem() {
        // An elbow's ends are the model's front, -y, and its +x; a tee's run is along the model's x and its branch to the front.
        int[][] elbows = {{24, 0, -1, 1, 0}, {25, 0, 1, 1, 0}, {26, 0, 1, -1, 0}, {27, 0, -1, -1, 0}};
        for (int[] elbow : elbows) {
            Outdoors.Piece piece = unnamed("industry_02_" + elbow[0]);
            float[] front = onMap(piece, 0f, -1f), side = onMap(piece, 1f, 0f);
            float[] one = {elbow[1], elbow[2]}, other = {elbow[3], elbow[4]};
            Checks.check(same(front, one) && same(side, other) || same(front, other) && same(side, one),
                    "industry_02_" + elbow[0] + " has its ends to " + elbow[1] + ", " + elbow[2] + " and " + elbow[3] + ", " + elbow[4]);
        }
        // A tee's branch: 28 to the west, 29 to the south, 30 to the east, 31 to the north.
        int[][] tees = {{28, -1, 0}, {29, 0, 1}, {30, 1, 0}, {31, 0, -1}};
        for (int[] tee : tees) {
            float[] branch = onMap(unnamed("industry_02_" + tee[0]), 0f, -1f);
            Checks.check(same(branch, new float[] {tee[1], tee[2]}), "industry_02_" + tee[0] + " branches to " + tee[1] + ", " + tee[2]);
        }
        // An end runs from the edge its sprite reaches: 35 north, 36 west, 37 east, 38 south.
        int[][] ends = {{35, 0, -1}, {36, -1, 0}, {37, 1, 0}, {38, 0, 1}};
        for (int[] end : ends) {
            float[] front = onMap(unnamed("industry_02_" + end[0]), 0f, -1f);
            Checks.check(same(front, new float[] {end[1], end[2]}), "industry_02_" + end[0] + " reaches the edge " + end[1] + ", " + end[2]);
        }
    }

    private static boolean same(float[] one, float[] other) {
        return one[0] == other[0] && one[1] == other[1];
    }

    private static void aDuctClimbsTheWallItIsFixedTo() {
        Checks.check(unnamed("industry_02_16").a < 0.4f && unnamed("industry_02_17").b < 0.4f && unnamed("industry_02_18").a > 0.6f
                && unnamed("industry_02_19").b > 0.6f, "16 to 19 stand by the west, north, east and south walls");
        Checks.check(Models.get(unnamed("industry_02_8").model).height() < Models.get(unnamed("industry_02_16").model).height(),
                "8 stops short of the ceiling where 16 reaches it");
    }

    private static void wallSpritesGoOnTheEdgeTheirPictureIsOn() {
        Checks.check(Overhead.edgeOf("industry_01_30") == Overhead.WEST_EDGE, "the girder painted along the west edge goes on it");
        Checks.check(Overhead.edgeOf("industry_01_28") == Overhead.BOTH_EDGES && Overhead.edgeOf("industry_01_27") == Overhead.BOTH_EDGES,
                "a corner of girders goes on both edges");
        Checks.check(Overhead.edgeOf("industry_01_29") == Overhead.ANY_EDGE && Overhead.edgeOf("industry_01_24") == Overhead.ANY_EDGE,
                "a girder marked for the edge it is painted on goes by its mark");
        Checks.check(Overhead.edgeOf("location_community_police_01_10") == Overhead.WEST_EDGE
                && Overhead.edgeOf("location_community_police_01_11") == Overhead.NORTH_EDGE, "a cell's barred frame goes on its edge");
        Checks.check(Overhead.edgeOf("location_community_police_01_4") == Overhead.ANY_EDGE && Overhead.edgeOf("industry_01_300") == Overhead.ANY_EDGE,
                "a cell's door, and a place not on the sheet, go by their marks");
    }

    private static void aCornerOfTimbersLiesAlongTheTwoEdgesOfItsCorner() {
        // The model's two timbers are along its front and its +x side. Each sprite's corner as the map's x and y.
        int[][] corners = {{32, -1, -1}, {34, 1, -1}, {33, 1, 1}, {35, -1, 1}, {56, -1, -1}, {58, 1, -1}, {57, 1, 1}, {59, -1, 1}};
        for (int[] corner : corners) {
            Outdoors.Piece piece = unnamed("carpentry_03_" + corner[0]);
            Models.get(piece.model);
            float[] front = onMap(piece, 0f, -1f), side = onMap(piece, 1f, 0f);
            Checks.check(front[0] + side[0] == corner[1] && front[1] + side[1] == corner[2], "carpentry_03_" + corner[0] + " is at the corner " + corner[1] + ", " + corner[2]);
            // Its colour is taken halfway along one of its timbers, a tenth of a square in from that edge.
            boolean onTimber = Math.abs(piece.dyeA - 0.5f) < 1e-6f && Math.abs(Math.abs(piece.dyeB - 0.5f) - 0.4f) < 1e-6f && Math.signum(piece.dyeB - 0.5f) == corner[2]
                    || Math.abs(piece.dyeB - 0.5f) < 1e-6f && Math.abs(Math.abs(piece.dyeA - 0.5f) - 0.4f) < 1e-6f && Math.signum(piece.dyeA - 0.5f) == corner[1];
            Checks.check(onTimber, "carpentry_03_" + corner[0] + " takes its colour from one of its timbers");
        }
        Checks.check(unnamed("carpentry_03_32").raised > 2f && unnamed("carpentry_03_56").raised == 0f, "32 is under the ceiling and 56 on the floor");
        for (int place : new int[] {52, 53, 54, 55}) {
            Models.get(unnamed("carpentry_03_" + place).model);
        }
        Checks.check(unnamed("carpentry_03_53").a > 0.8f && unnamed("carpentry_03_53").b > 0.8f, "53 stands in the south-east corner");
        Checks.check(unnamed("carpentry_03_16") == null, "a straight timber stays as it is");
    }

    private static void aFrameOfScaffoldingStandsOnItsEdgeWithItsStandardWhereTheSpritePaintsIt() {
        // name, the edge's side as x and y, and for a braced frame the way along the edge to its standard.
        int[][] frames = {{16, 0, -1, 0, 0}, {21, 0, 1, 0, 0}, {29, -1, 0, 0, 0}, {24, 1, 0, 0, 0},
                {17, -1, 0, 0, 1}, {18, -1, 0, 0, -1}, {27, 0, -1, -1, 0}, {28, 0, -1, 1, 0}};
        for (int[] frame : frames) {
            Outdoors.Piece piece = unnamed("construction_01_" + frame[0]);
            Models.get(piece.model);
            float[] along = onMap(piece, 0f, 1f);
            boolean onEdge = frame[1] != 0 ? along[0] == 0f && (frame[1] < 0 ? piece.a < 0.1f : piece.a > 0.9f) && piece.b == 0.5f
                    : along[1] == 0f && (frame[2] < 0 ? piece.b < 0.1f : piece.b > 0.9f) && piece.a == 0.5f;
            Checks.check(onEdge, "construction_01_" + frame[0] + " runs along its edge by it");
            if (frame[3] != 0 || frame[4] != 0) {
                Checks.check(piece.model.equals("scaffold_braced") && along[0] == frame[3] && along[1] == frame[4],
                        "construction_01_" + frame[0] + " has its standard at the end its sprite paints it");
            }
        }
        Checks.check(unnamed("construction_01_22") == null, "a tube on the floor alone stays as it is");
    }
}

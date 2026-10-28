package com.cameraunlock.zomboid;

import java.util.Arrays;

/** Tests of where the lanes of a road run: what Lanes reads off the game's lines and the road's edges, and how a face carries it. */
final class LanesTests {
    private static final int TILES = Terrain.CHUNK_TILES;

    private LanesTests() {}

    private interface Squares {
        int at(int x, int y);
    }

    static void run() {
        Checks.run("linesOfTheRoadSheetAreReadAsTheEdgeTheyLieAlong", LanesTests::linesOfTheRoadSheetAreReadAsTheEdgeTheyLieAlong);
        Checks.run("twoLaneRoadIsSplitAtItsDoubleYellowLine", LanesTests::twoLaneRoadIsSplitAtItsDoubleYellowLine);
        Checks.run("roadAlongXIsCrossedAlongY", LanesTests::roadAlongXIsCrossedAlongY);
        Checks.run("whiteLineSplitsASideIntoTwoLanes", LanesTests::whiteLineSplitsASideIntoTwoLanes);
        Checks.run("eachLaneOfAWideRoadIsItsOwnDashedLinesAndAll", LanesTests::eachLaneOfAWideRoadIsItsOwnDashedLinesAndAll);
        Checks.run("lotBesideARoadIsNoPartOfItsLanes", LanesTests::lotBesideARoadIsNoPartOfItsLanes);
        Checks.run("carParkHasNoLanes", LanesTests::carParkHasNoLanes);
        Checks.run("pavementIsNoPartOfALane", LanesTests::pavementIsNoPartOfALane);
        Checks.run("laneFadesOutWhereItMeetsAJunction", LanesTests::laneFadesOutWhereItMeetsAJunction);
        Checks.run("chunksAgreeOnASquareTheyBothSee", LanesTests::chunksAgreeOnASquareTheyBothSee);
        Checks.run("oneCarriagewayOfADividedRoadHasNoLanes", LanesTests::oneCarriagewayOfADividedRoadHasNoLanes);
        Checks.run("onlyTheGroundOfALaneCarriesIt", LanesTests::onlyTheGroundOfALaneCarriesIt);
        Checks.run("kerbsAndLanesAreWorkedOutAgainWhenALineChanges", LanesTests::kerbsAndLanesAreWorkedOutAgainWhenALineChanges);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static final int MADE = 0, TARMAC = 1, PAVEMENT = 2;

    /** The lanes of the chunk whose corner is at (originX, originY): ground says what each square is, lines what Lanes.mark bits are painted on it. */
    private static float[] lanes(Squares ground, Squares lines, int originX, int originY) {
        byte[] kinds = new byte[Kerbs.SPAN * Kerbs.SPAN];
        boolean[] footway = new boolean[kinds.length];
        byte[] marks = new byte[kinds.length];
        for (int i = 0; i < Kerbs.SPAN; i++) {
            for (int j = 0; j < Kerbs.SPAN; j++) {
                int x = originX - Kerbs.MARGIN + i, y = originY - Kerbs.MARGIN + j, at = i * Kerbs.SPAN + j;
                int what = ground.at(x, y);
                kinds[at] = what == MADE ? Terrain.MADE : Terrain.STREET;
                footway[at] = what != TARMAC;
                marks[at] = (byte) lines.at(x, y);
            }
        }
        return Lanes.of(kinds, footway, marks, originX, originY);
    }

    private static int bit(String name) {
        return Lanes.mark(name);
    }

    private static final String SHEET = "street_trafficlines_01_";

    /** A road along y from x 0 to x 7 with a double yellow line down x 4, as the game paints one: 20 on the square west of it, 16 on the square east. */
    private static int twoLane(int x, int y) {
        return x == 3 ? bit(SHEET + 20) : x == 4 ? bit(SHEET + 16) : 0;
    }

    private static int at(int i, int j) {
        return (i * TILES + j) * Lanes.FLOATS;
    }

    private static boolean near(float value, float to) {
        return Math.abs(value - to) < 0.01f;
    }

    private static String lane(float[] lanes, int at) {
        return Arrays.toString(Arrays.copyOfRange(lanes, at, at + Lanes.FLOATS));
    }

    private static void linesOfTheRoadSheetAreReadAsTheEdgeTheyLieAlong() {
        check(bit(SHEET + 16) == 1 << Lanes.YELLOW, "faded yellow along y near the low x edge");
        check(bit(SHEET + 28) == 2 << Lanes.YELLOW, "yellow along y near the high x edge");
        check(bit(SHEET + 18) == 4 << Lanes.YELLOW, "faded yellow along x near the low y edge");
        check(bit(SHEET + 22) == 8 << Lanes.YELLOW, "faded yellow along x near the high y edge");
        check(bit(SHEET + 0) == 1 << Lanes.WHITE, "faded white along y near the low x edge");
        check(bit(SHEET + 12) == 2 << Lanes.WHITE, "white along y near the high x edge");
        check(bit(SHEET + 17) == 0 && bit(SHEET + 32) == 0 && bit(SHEET + 48) == 0, "corners, bars and arrows are no lane's line");
        check(bit("street_trafficlines_curb_yellow_faded_108") == 0 && bit("blends_street_01_80") == 0 && bit(null) == 0, "other sheets are no lane's line");
    }

    private static void twoLaneRoadIsSplitAtItsDoubleYellowLine() {
        float[] lanes = lanes((x, y) -> x >= 0 && x < 8 ? TARMAC : MADE, LanesTests::twoLane, 0, 0);
        check(lanes != null, "a road with a centre line has lanes");
        for (int i = 0; i < TILES; i++) {
            for (int j = 0; j < TILES; j++) {
                int at = at(i, j);
                check(lanes[at + Lanes.AXIS] == Lanes.ACROSS_X, "the road is crossed along x at " + i + ", " + j);
                check(near(lanes[at + Lanes.CENTRE], 4f), "its centre is the double line's middle, " + lanes[at + Lanes.CENTRE]);
                check(lanes[at + Lanes.INNER] == 0f && near(lanes[at + Lanes.WIDTH], 4f), "a lane runs from the centre to the kerb, " + lane(lanes, at));
                check(near(lanes[at + Lanes.LOW], 0f) && near(lanes[at + Lanes.HIGH], 8f), "the road runs kerb to kerb, " + lane(lanes, at));
                for (int corner = 0; corner < 4; corner++) {
                    check(lanes[at + Lanes.CORNER + corner] == 1f, "the lane is drawn whole along a straight road");
                }
            }
        }
    }

    private static void roadAlongXIsCrossedAlongY() {
        float[] lanes = lanes((x, y) -> y >= 1 && y < 7 ? TARMAC : MADE, (x, y) -> y == 3 ? bit(SHEET + 30) : y == 4 ? bit(SHEET + 26) : 0, 0, 0);
        int at = at(5, 2);
        check(lanes[at + Lanes.AXIS] == Lanes.ACROSS_Y, "a road along x is crossed along y");
        check(near(lanes[at + Lanes.CENTRE], 4f) && near(lanes[at + Lanes.WIDTH], 3f), "centre 4, a lane 3 wide, " + lane(lanes, at));
        check(lanes[at(5, 0) + Lanes.AXIS] == Lanes.NONE, "the land beside it is in no lane");
    }

    private static void whiteLineSplitsASideIntoTwoLanes() {
        // A white line along y near the low edge of x 6 marks off a strip for parking on the east side.
        float[] lanes = lanes((x, y) -> x >= 0 && x < 8 ? TARMAC : MADE, (x, y) -> twoLane(x, y) | (x == 6 ? bit(SHEET + 8) : 0), 0, 0);
        int lane = at(5, 3), strip = at(7, 3);
        check(lanes[lane + Lanes.INNER] == 0f && Math.abs(lanes[lane + Lanes.WIDTH] - 2.0781f) < 0.001f, "the east lane ends at the white line, " + lane(lanes, lane));
        check(Math.abs(lanes[strip + Lanes.INNER] - 2.0781f) < 0.001f && Math.abs(lanes[strip + Lanes.WIDTH] - 1.9297f) < 0.001f,
                "the strip beyond it is a lane of its own, out to the kerb, " + lane(lanes, strip));
        check(lanes[strip + Lanes.CENTRE] == lanes[lane + Lanes.CENTRE] && lanes[strip + Lanes.HIGH] == lanes[lane + Lanes.HIGH], "of the same road");
        check(lanes[strip + Lanes.CORNER] == 1f && lanes[lane + Lanes.CORNER + 1] == 1f, "and neither fades where they meet");
        check(near(lanes[at(1, 3) + Lanes.WIDTH], 4f), "the west lane still ends at the kerb");
    }

    private static void eachLaneOfAWideRoadIsItsOwnDashedLinesAndAll() {
        // Four lanes from x 0 to x 15, the centre down x 8, and a white line two squares painted and two left down x 4 and x 12.
        Squares ground = (x, y) -> x >= 0 && x < 16 ? TARMAC : MADE;
        Squares lines = (x, y) -> (x == 7 ? bit(SHEET + 20) : x == 8 ? bit(SHEET + 16) : 0) | ((x == 4 || x == 12) && Math.floorMod(y, 4) < 2 ? bit(SHEET + 0) : 0);
        float[] west = lanes(ground, lines, 0, 0), east = lanes(ground, lines, 8, 0);
        for (int j = 0; j < TILES; j++) {
            int outer = at(1, j), inner = at(6, j), nearer = at(1, j), further = at(6, j);
            check(near(west[outer + Lanes.INNER], 3.92f) && near(west[outer + Lanes.WIDTH], 4.07f), "the outer west lane, row " + j + ", " + lane(west, outer));
            check(west[inner + Lanes.INNER] == 0f && near(west[inner + Lanes.WIDTH], 3.92f), "the inner west lane, row " + j + ", " + lane(west, inner));
            check(east[nearer + Lanes.INNER] == 0f && near(east[nearer + Lanes.WIDTH], 4.08f), "the inner east lane, row " + j + ", " + lane(east, nearer));
            check(near(east[further + Lanes.INNER], 4.08f) && near(east[further + Lanes.WIDTH], 3.93f), "the outer east lane, row " + j + ", " + lane(east, further));
            for (int corner = 0; corner < 4; corner++) {
                check(west[outer + Lanes.CORNER + corner] == 1f && east[further + Lanes.CORNER + corner] == 1f, "a gap in the dashes fades nothing");
            }
        }
    }

    private static void lotBesideARoadIsNoPartOfItsLanes() {
        // A kerb west of the road, and on the east a lot of tarmac with no kerb between: wider than the window is looked across, and narrower.
        for (int lotEnd : new int[] {40, 15}) {
            float[] lanes = lanes((x, y) -> x >= 0 && x < lotEnd ? TARMAC : MADE, LanesTests::twoLane, 0, 0);
            float[] lot = lanes((x, y) -> x >= 0 && x < lotEnd ? TARMAC : MADE, LanesTests::twoLane, 8, 0);
            int east = at(7, 3);
            check(lanes != null && lanes[east + Lanes.AXIS] == Lanes.ACROSS_X, "a road beside a lot to " + lotEnd + " keeps its lanes");
            check(near(lanes[east + Lanes.WIDTH], 3.99f) && near(lanes[east + Lanes.HIGH], 7.98f), "the east side ends as far out as the west, " + lane(lanes, east));
            for (int corner = 0; corner < 4; corner++) {
                check(lanes[east + Lanes.CORNER + corner] == 1f, "and does not fade beside the lot");
            }
            check(lot == null, "the lot is in no lane");
        }
        // A white line along the road's edge ends it there.
        float[] edged = lanes((x, y) -> x >= 0 && x < 40 ? TARMAC : MADE, (x, y) -> twoLane(x, y) | (x == 7 ? bit(SHEET + 0) : 0), 0, 0);
        check(near(edged[at(5, 3) + Lanes.WIDTH], 3.08f) && edged[at(7, 3) + Lanes.AXIS] == Lanes.NONE, "past the edge line is the lot, " + lane(edged, at(5, 3)));
    }

    private static void carParkHasNoLanes() {
        check(lanes((x, y) -> x >= -6 && x < 14 && y >= -6 && y < 14 ? TARMAC : MADE, (x, y) -> 0, 0, 0) == null, "tarmac with no centre line has no lanes");
        // Parking bays are white lines, which are no centre.
        check(lanes((x, y) -> x >= 0 && x < 8 ? TARMAC : MADE, (x, y) -> x == 2 || x == 5 ? bit(SHEET + 0) : 0, 0, 0) == null, "white lines alone make no lanes");
    }

    private static void pavementIsNoPartOfALane() {
        float[] lanes = lanes((x, y) -> x >= 0 && x < 8 ? TARMAC : x == 8 || x == -1 ? PAVEMENT : MADE, LanesTests::twoLane, -4, 0);
        // The chunk from x -4: its square 3 is x -1, the pavement, and its square 4 the road's first.
        check(lanes[at(3, 2) + Lanes.AXIS] == Lanes.NONE, "pavement is in no lane");
        check(lanes[at(4, 2) + Lanes.AXIS] == Lanes.ACROSS_X, "the road beside it is");
        float[] road = lanes((x, y) -> x >= 0 && x < 8 ? TARMAC : x == 8 || x == -1 ? PAVEMENT : MADE, LanesTests::twoLane, 0, 0);
        check(near(road[at(0, 2) + Lanes.WIDTH], 4f), "the lane ends at the pavement's edge as at a kerb");
    }

    private static void laneFadesOutWhereItMeetsAJunction() {
        // The road along y meets a road along x at y 8..15, which has no centre line: a junction.
        Squares ground = (x, y) -> x >= 0 && x < 8 || y >= 8 && y < 16 ? TARMAC : MADE;
        float[] lanes = lanes(ground, (x, y) -> y < 8 ? twoLane(x, y) : 0, 0, 0);
        int last = at(1, 7);
        check(lanes[last + Lanes.AXIS] == Lanes.ACROSS_X, "the road's last square before the junction is in its lane");
        check(lanes[last + Lanes.CORNER] == 1f && lanes[last + Lanes.CORNER + 1] == 1f, "whole on its side away from the junction");
        check(lanes[last + Lanes.CORNER + 2] == 0f && lanes[last + Lanes.CORNER + 3] == 0f, "faded out on the junction's side");
        check(Lanes.drawn(lanes, last, 0.5f, 0.5f) == 0.5f, "half drawn in its middle");
        float[] junction = lanes(ground, (x, y) -> y < 8 ? twoLane(x, y) : 0, 0, 8);
        check(junction == null, "the junction itself is in no lane");
    }

    private static void chunksAgreeOnASquareTheyBothSee() {
        Squares ground = (x, y) -> x >= 9000 && x < 9008 ? TARMAC : MADE;
        Squares lines = (x, y) -> x == 9003 ? bit(SHEET + 20) : x == 9004 ? bit(SHEET + 16) : 0;
        float[] one = lanes(ground, lines, 9000, 5200), other = lanes(ground, lines, 9000, 5208);
        for (int k = 0; k < Lanes.FLOATS; k++) {
            check(one[at(2, 7) + k] == other[at(2, 0) + k], "the same road gives the same lane further along");
        }
        // A road from x 8996 to 9007 with its centre line down x 9002: x 8999 and 9000 are both of its west lane, either side of a chunk's edge.
        Squares wide = (x, y) -> x >= 8996 && x < 9008 ? TARMAC : MADE;
        Squares centred = (x, y) -> x == 9001 ? bit(SHEET + 20) : x == 9002 ? bit(SHEET + 16) : 0;
        float[] side = lanes(wide, centred, 8992, 5200), beside = lanes(wide, centred, 9000, 5200);
        for (int k = 0; k < Lanes.CORNER; k++) {
            check(side[at(7, 3) + k] == beside[at(0, 3) + k], "a road across a chunk's edge has one lane, " + lane(side, at(7, 3)) + " and " + lane(beside, at(0, 3)));
        }
        check(near(side[at(7, 3) + Lanes.CENTRE], 9002f), "where its line is, far across the map");
        check(side[at(7, 3) + Lanes.CORNER + 1] == 1f && beside[at(0, 3) + Lanes.CORNER] == 1f, "and is drawn whole across the chunks' edge");
    }

    private static void oneCarriagewayOfADividedRoadHasNoLanes() {
        // Two lanes each way, a yellow line down the inner edge of each carriageway, grass between.
        Squares ground = (x, y) -> x >= 0 && x < 4 || x >= 6 && x < 10 ? TARMAC : MADE;
        Squares lines = (x, y) -> x == 3 ? bit(SHEET + 20) : x == 6 ? bit(SHEET + 16) : 0;
        check(lanes(ground, lines, 0, 0) == null, "a yellow line along a carriageway's edge is no centre");
    }

    private static void onlyTheGroundOfALaneCarriesIt() {
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 1f;
        frame.originU = SpriteGeometry.PIXELS_ACROSS_PER_TILE;
        frame.width = 2f * SpriteGeometry.PIXELS_ACROSS_PER_TILE;
        // Tall enough to show the crate's top as well as the ground.
        frame.originV = SpriteGeometry.PIXELS_PER_LEVEL;
        frame.height = 2f * SpriteGeometry.PIXELS_DOWN_PER_TILE + SpriteGeometry.PIXELS_PER_LEVEL;
        frame.uEnd = 1f;
        frame.vEnd = 1f;
        frame.light(1f, 1f, 1f);
        geometry.lanes = lanes((x, y) -> x >= 0 && x < 8 ? TARMAC : MADE, (x, y) -> twoLane(x, y) | (x == 6 ? bit(SHEET + 8) : 0), 0, 0);
        geometry.laneAt = at(7, 3);
        SpriteGeometry.Mesh ground = new SpriteGeometry.Mesh(), top = new SpriteGeometry.Mesh();
        geometry.addFace(ground, frame, 7, 3, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        // The top of a crate on the road.
        geometry.addFace(top, frame, 7, 3, 0, 0f, 0f, 0.4f, 1f, 0f, 0f, 0f, 1f, 0f);
        check(ground.floats > 0 && top.floats > 0, "both faces are built");
        for (int at = 0; at < ground.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float x = ground.vertices[at], rootX = ground.vertices[at + SpriteGeometry.ROOT_AT], rootY = ground.vertices[at + SpriteGeometry.ROOT_AT + 1];
            // As the picture reads it back: see PipelineShaders.WORLD_FRAGMENT_MAIN_GROUND.
            float quarters = (float) Math.floor(rootY / SpriteGeometry.LANE_STEP);
            float width = quarters / SpriteGeometry.LANE_QUARTERS;
            float past = rootY - quarters * SpriteGeometry.LANE_STEP - SpriteGeometry.LANE_BIAS;
            check(rootX < SpriteGeometry.LANE + 0.5f && rootX > SpriteGeometry.LANE - 1.5f, "the ground of a lane is told it is in one, across x, " + rootX);
            check(Math.abs(width - 2f) < 1e-6f, "and the lane's width in quarters of a tile, " + width);
            check(Math.abs(past - (Math.abs(x - geometry.lanes[geometry.laneAt + Lanes.CENTRE]) - geometry.lanes[geometry.laneAt + Lanes.INNER])) < 1e-3f,
                    "and how far it is past the lane's inner edge, " + past);
        }
        for (int at = 0; at < top.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            check(top.vertices[at + SpriteGeometry.ROOT_AT] == SpriteGeometry.NO_ROOT, "the top of a crate on the road is no part of its lane");
        }
    }

    private static void kerbsAndLanesAreWorkedOutAgainWhenALineChanges() {
        int squares = Kerbs.SPAN * Kerbs.SPAN;
        byte[] kinds = new byte[squares], marks = new byte[squares];
        boolean[] hard = new boolean[squares], footway = new boolean[squares];
        Arrays.fill(kinds, Terrain.STREET);
        long before = Terrain.kerbFrom(kinds, hard, footway, marks);
        marks[squares / 2] = (byte) bit(SHEET + 16);
        check(Terrain.kerbFrom(kinds, hard, footway, marks) != before, "a line painted on a square changes what the kerbs and lanes are worked out from");
        float[] kerb = new float[4], lanes = new float[Lanes.FLOATS];
        long kerbOnly = Terrain.kerbSum(kerb, null), both = Terrain.kerbSum(kerb, lanes);
        lanes[Lanes.WIDTH] = 3f;
        check(both != kerbOnly && Terrain.kerbSum(kerb, lanes) != both, "a chunk's lanes are part of its kerbs' number");
        check(Terrain.kerbSum(null, lanes) != 0L && Terrain.kerbSum(null, null) == 0L, "lanes with no kerb still have a number, and neither none");
    }
}

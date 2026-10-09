package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/** Tests of a roof's shape where its slopes meet: hips, valleys, ridges and their caps. */
final class RoofTests {
    private static final float THIRD = RoofFit.PITCH, EAVE = 1f + THIRD;

    private RoofTests() {}

    static void run() {
        Checks.run("planeAndFoldAreToldApartByTheMiddle", RoofTests::planeAndFoldAreToldApartByTheMiddle);
        Checks.run("longestRunLeavesOutAStrayRow", RoofTests::longestRunLeavesOutAStrayRow);
        Checks.run("roofOverAnLShapedBuildingIsClosedAllRound", RoofTests::roofOverAnLShapedBuildingIsClosedAllRound);
        Checks.run("hipsAndValleyOfTheLShapeAreFoldedTheRightWay", RoofTests::hipsAndValleyOfTheLShapeAreFoldedTheRightWay);
        Checks.run("mapTilesAtAHipKeepTheirOwnHalf", RoofTests::mapTilesAtAHipKeepTheirOwnHalf);
        Checks.run("ridgeAndHipsAreCappedAndAValleyIsNot", RoofTests::ridgeAndHipsAreCappedAndAValleyIsNot);
        Checks.run("gableOneTileWiderThanItsSlopesIsLevelAtTheRidge", RoofTests::gableOneTileWiderThanItsSlopesIsLevelAtTheRidge);
        Checks.run("chimneySquareInASlopeIsMended", RoofTests::chimneySquareInASlopeIsMended);
        Checks.run("roofWithBothSidesAndMoreBuildingBeyondGetsNoMore", RoofTests::roofWithBothSidesAndMoreBuildingBeyondGetsNoMore);
        Checks.run("hipTileOfTheMapIsBothItsSlopes", RoofTests::hipTileOfTheMapIsBothItsSlopes);
        Checks.run("wingRunsIntoTheMainRoofInAStraightValley", RoofTests::wingRunsIntoTheMainRoofInAStraightValley);
        Checks.run("slopeIsNotCarriedOverAHip", RoofTests::slopeIsNotCarriedOverAHip);
        Checks.run("valleyTileThatReadsLevelIsFolded", RoofTests::valleyTileThatReadsLevelIsFolded);
        Checks.run("lowestRowThatReadsLevelIsTheSlopeGoingOnDown", RoofTests::lowestRowThatReadsLevelIsTheSlopeGoingOnDown);
        Checks.run("meanColourOfATileCountsTheDarkBetweenItsShingles", RoofTests::meanColourOfATileCountsTheDarkBetweenItsShingles);
        Checks.run("farSlopesOverTheLoftCeilingAreMadeWhole", RoofTests::farSlopesOverTheLoftCeilingAreMadeWhole);
        Checks.run("rowCutShortByAWingStillComesDownToItsEave", RoofTests::rowCutShortByAWingStillComesDownToItsEave);
        Checks.run("bothRidgesOfACrossGableArePeakedAndCappedWhole", RoofTests::bothRidgesOfACrossGableArePeakedAndCappedWhole);
        Checks.run("deepWingIsCarriedIntoTheMainRoofUpToItsRidge", RoofTests::deepWingIsCarriedIntoTheMainRoofUpToItsRidge);
        Checks.run("eaveRowHangsUnderTheFootOfASlopeOnly", RoofTests::eaveRowHangsUnderTheFootOfASlopeOnly);
    }

    private static void eaveRowHangsUnderTheFootOfASlopeOnly() {
        // The wing of the cross gable: its east slope comes down to the eave on level 2 over the east edge of column 8.
        HashMap<Long, RoofFit.Tile> at = new HashMap<>();
        for (RoofFit.Tile tile : crossGable()) {
            at.putIfAbsent(RoofFit.key(tile.x, tile.y, tile.z), tile);
        }
        check(RoofFit.fallsToItsFloor(at.get(RoofFit.key(8, 12, 2)), RoofFit.ALONG_X),
                "the row of eave the map hangs from the wall east of the wing's lowest tile is that tile's eave, which the picture makes");
        check(!RoofFit.fallsToItsFloor(at.get(RoofFit.key(7, 12, 2)), RoofFit.ALONG_X), "a tile higher up the slope has no eave under it");
        check(!RoofFit.fallsToItsFloor(at.get(RoofFit.key(8, 12, 2)), RoofFit.ALONG_Y), "nor has a slope across the wall the row hangs from");
        check(!RoofFit.fallsToItsFloor(at.get(RoofFit.key(3, 8, 2)), RoofFit.ALONG_X) && RoofFit.fallsToItsFloor(at.get(RoofFit.key(3, 8, 2)), RoofFit.ALONG_Y),
                "the main roof's lowest row comes down to its eave along y");
        RoofFit.Tile rising = new RoofFit.Tile(0, 0, 2, RoofFit.ALONG_X, true, 2f, 2f + THIRD);
        RoofFit.fit(new ArrayList<>(List.of(rising)));
        check(!RoofFit.fallsToItsFloor(rising, RoofFit.ALONG_X), "a slope that climbs to the wall the row hangs from is no eave over it");
    }

    /**
     * Cally's Gifts in Riverside (6481..6489, 5295..5312), as the map holds its roof, from x and
     * y 0. A main roof over rows 0 to 8 with its ridge along the middle of row 4, and a wing as
     * wide over rows 9 to 17 with its ridge along the middle of column 4, both from eaves on
     * level 2 up to 3.5. The map has the main roof's south slope and the wing's east slope, the
     * half tiles that top them, and the valley where the two meet on the east; the main roof's
     * north slope and the wing's west slope are left out. A tile that would stand more than a
     * level over its square's floor is on level 3. Squares (5, 5), (6, 6) and (8, 8) hold the
     * valley's tiles and (7, 7) a sprite that reads neither way.
     */
    private static List<RoofFit.Tile> crossGable() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        int[][] southSlope = {{0, 1, 2, 3, 6, 7, 8}, {0, 1, 2, 3, 4, 5, 7, 8}, {0, 1, 2, 3, 4, 8}, {0, 1, 2, 3}};
        for (int row = 0; row < southSlope.length; row++) {
            int y = 5 + row;
            float north = 2f + (9 - y) * THIRD;
            for (int x : southSlope[row]) {
                roof.add(new RoofFit.Tile(x, y, (int) Math.floor(north - THIRD + 0.05f), RoofFit.ALONG_Y, true, north, north - THIRD));
            }
        }
        for (int x = 5; x <= 8; x++) {
            float west = 2f + (9 - x) * THIRD;
            for (int y = x + 1; y <= 17; y++) {
                roof.add(new RoofFit.Tile(x, y, (int) Math.floor(west - THIRD + 0.05f), RoofFit.ALONG_X, true, west, west - THIRD));
            }
        }
        roof.add(new RoofFit.Tile(5, 5, 3, RoofFit.SOUTH_EAST_ODD, true, 3f + THIRD, 3f));
        roof.add(new RoofFit.Tile(6, 6, 2, RoofFit.SOUTH_EAST_ODD, true, 3f, 3f - THIRD));
        roof.add(new RoofFit.Tile(8, 8, 2, RoofFit.SOUTH_EAST_ODD, true, 2f + THIRD, 2f));
        roof.add(new RoofFit.Tile(7, 7, 2, RoofFit.ALONG_Y, false, 2.82f, 2f + THIRD));
        for (int x = 0; x <= 8; x++) {
            roof.add(half(x, 4, RoofFit.SOUTH_HALF));
        }
        for (int y = 5; y <= 17; y++) {
            roof.add(half(4, y, RoofFit.EAST_HALF));
        }
        // In the order of their places, as Roofs.read gives them.
        roof.sort(Comparator.comparingLong(tile -> RoofFit.key(tile.x, tile.y, tile.z)));
        RoofFit.fit(roof);
        return roof;
    }

    private static RoofFit.Tile half(int x, int y, int which) {
        RoofFit.Tile half = new RoofFit.Tile(x, y, 3, RoofFit.LEVEL, true, 3f + THIRD, 3f + THIRD);
        half.half = which;
        half.halfEdge = 3f + THIRD;
        return half;
    }

    /**
     * The building under that roof: two storeys and the loft. The map lays a floor on level 3,
     * the loft's ceiling, where the roof stands over level 3 from edge to edge: rows 3 to 5 of
     * the main roof and columns 3 to 5 of the wing. No room is on it. Roofs.site leaves out such
     * a floor as this does, one that is no room, above the level asked about and no higher than
     * the roof made there.
     */
    private static final RoofFit.Site CROSS_GABLE = new RoofFit.Site() {
        @Override
        public boolean stands(int x, int y, int z) {
            return x >= 0 && x <= 8 && y >= 0 && y <= 17;
        }

        @Override
        public boolean floored(int x, int y, int z) {
            return z == 3 && stands(x, y, z) && (y >= 3 && y <= 5 || x >= 3 && x <= 5 && y >= 3);
        }

        @Override
        public boolean taken(int x, int y, int z, float roof) {
            if (floored(x, y, z)) {
                return true;
            }
            for (int level = z + 1; level <= z + 3; level++) {
                if (floored(x, y, level) && level > roof + RoofFit.SAME) {
                    return true;
                }
            }
            return false;
        }
    };

    private static HashMap<Long, RoofFit.Tile> madeOfCrossGable(List<RoofFit.Tile> tiles) {
        HashMap<Long, RoofFit.Tile> over = new HashMap<>();
        for (RoofFit.Tile tile : RoofFit.backs(tiles, CROSS_GABLE)) {
            over.put(RoofFit.key(tile.x, tile.y, 0), tile);
        }
        return over;
    }

    private static String said(RoofFit.Tile tile) {
        return tile == null ? "nothing" : (tile.shaped ? "folded " : "axis " + tile.fitAxis + " ") + tile.fitLow + ".." + tile.fitHigh;
    }

    private static void farSlopesOverTheLoftCeilingAreMadeWhole() {
        HashMap<Long, RoofFit.Tile> over = madeOfCrossGable(crossGable());
        for (int x = 0; x <= 5; x++) {
            for (int y = 0; y <= 3; y++) {
                RoofFit.Tile tile = over.get(RoofFit.key(x, y, 0));
                check(tile != null && !tile.shaped && tile.fitAxis == RoofFit.ALONG_Y && near(tile.fitLow, 2f + y * THIRD) && near(tile.fitHigh, 2f + (y + 1) * THIRD),
                        "the main roof's north slope is made at " + x + ", " + y + ", from the eave up to the ridge, its top row over the loft's ceiling too: " + said(tile));
            }
        }
        for (int y = 9; y <= 17; y++) {
            for (int x = 0; x <= 3; x++) {
                RoofFit.Tile tile = over.get(RoofFit.key(x, y, 0));
                check(tile != null && !tile.shaped && tile.fitAxis == RoofFit.ALONG_X && near(tile.fitLow, 2f + x * THIRD) && near(tile.fitHigh, 2f + (x + 1) * THIRD),
                        "the wing's west slope is made at " + x + ", " + y + ": " + said(tile));
            }
        }
    }

    private static void rowCutShortByAWingStillComesDownToItsEave() {
        HashMap<Long, RoofFit.Tile> over = madeOfCrossGable(crossGable());
        // East of the valley the main roof's rows run into the wing a row or two over the eave.
        for (int x = 6; x <= 8; x++) {
            for (int y = 0; y <= 3; y++) {
                RoofFit.Tile tile = over.get(RoofFit.key(x, y, 0));
                check(tile != null && !tile.shaped && tile.fitAxis == RoofFit.ALONG_Y && near(tile.fitLow, 2f + y * THIRD) && near(tile.fitHigh, 2f + (y + 1) * THIRD),
                        "the north slope over column " + x + " stands on the eave the rest of it does, and is no row short of the ridge, at " + y + ": " + said(tile));
            }
        }
    }

    private static void bothRidgesOfACrossGableArePeakedAndCappedWhole() {
        List<RoofFit.Tile> tiles = crossGable();
        List<RoofFit.Tile> made = RoofFit.backs(tiles, CROSS_GABLE);
        RoofFit.peaks(tiles, made);
        HashMap<Long, RoofFit.Tile> ridges = new HashMap<>();
        for (RoofFit.Tile tile : made) {
            if (tile.peak > 0f) {
                check(ridges.put(RoofFit.key(tile.x, tile.y, 0), tile) == null, "one ridge to a square: " + tile.x + ", " + tile.y);
            }
        }
        for (int x = 0; x <= 8; x++) {
            RoofFit.Tile ridge = ridges.get(RoofFit.key(x, 4, 0));
            check(ridge != null && ridge.peakAxis == RoofFit.ALONG_Y && near(ridge.fitLow + ridge.peak, 3.5f),
                    "the main roof's ridge runs the whole length of the roof, over the wing's end and the valley's top too: " + x);
        }
        for (int y = 5; y <= 17; y++) {
            RoofFit.Tile ridge = ridges.get(RoofFit.key(4, y, 0));
            check(ridge != null && ridge.peakAxis == RoofFit.ALONG_X && near(ridge.fitLow + ridge.peak, 3.5f),
                    "the wing's ridge runs from the main roof's to its own end, over the main roof's slope going on under it too: " + y);
        }
        boolean[] capped = new boolean[18];
        for (RoofFit.Cap cap : RoofFit.caps(tiles, made)) {
            if (cap.x0 == 4.5f && cap.x1 == 4.5f && near(cap.high0, 3.5f) && near(cap.high1, 3.5f)) {
                for (int y = (int) Math.min(cap.y0, cap.y1); y < Math.max(cap.y0, cap.y1); y++) {
                    capped[y] = true;
                }
            }
        }
        for (int y = 5; y <= 17; y++) {
            check(capped[y], "and the wing's ridge is capped all along it: " + y);
        }
    }

    private static void deepWingIsCarriedIntoTheMainRoofUpToItsRidge() {
        List<RoofFit.Tile> tiles = crossGable();
        List<RoofFit.Tile> made = RoofFit.backs(tiles, CROSS_GABLE);
        RoofFit.peaks(tiles, made);
        HashMap<Long, RoofFit.Tile> carried = new HashMap<>();
        for (RoofFit.Tile tile : RoofFit.valleys(tiles, made)) {
            carried.put(RoofFit.key(tile.x, tile.y, 0), tile);
        }
        // The wing's west slope runs into the main roof's south slope along the line from (0, 9) to (4.5, 4.5).
        for (int x = 0; x <= 3; x++) {
            for (int y = 8 - x; y <= 8; y++) {
                RoofFit.Tile tile = carried.get(RoofFit.key(x, y, 0));
                check(tile != null && tile.fitAxis == RoofFit.ALONG_X && near(tile.fitLow, 2f + x * THIRD),
                        "the wing's west slope is carried over the main roof's as far as the valley, its top row too, a whole tile's climb and more over the main roof's eave: "
                                + x + ", " + y + ": " + said(tile));
            }
        }
    }

    private static void meanColourOfATileCountsTheDarkBetweenItsShingles() {
        // Three pixels of a shingle's face, one of the dark beside it, and one the sprite does not paint.
        byte[] picture = {60, 90, 120, (byte) 255, 60, 90, 120, (byte) 255, 60, 90, 120, (byte) 255, 0, 10, 20, (byte) 255, (byte) 255, (byte) 255, (byte) 255, 0};
        ByteBuffer pixels = ByteBuffer.wrap(picture);
        float[] colour = new float[WallSides.MEAN + 3];
        check(WallSides.usualColour(pixels, 5, 0, 0, 5, 1, colour) && near(colour[0], 60f / 255f), "the usual colour of a tile is the face of its shingles");
        WallSides.meanColour(pixels, 5, 0, 0, 5, 1, colour);
        check(near(colour[WallSides.MEAN], 45f / 255f) && near(colour[WallSides.MEAN + 1], 70f / 255f) && near(colour[WallSides.MEAN + 2], 95f / 255f),
                "and its mean is darker by the dark between them, with what it does not paint left out");
    }

    /** The east slope of a main roof three tiles deep over rows 0 to 6, and the south slope of a wing two tiles deep that ends at its east edge. */
    private static List<RoofFit.Tile> mainRoofAndWing() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int y = 0; y <= 6; y++) {
            for (int x = 0; x < 3; x++) {
                roof.add(new RoofFit.Tile(x, y, 1, RoofFit.ALONG_X, true, 2f - x * THIRD, 2f - (x + 1) * THIRD));
            }
        }
        roof.add(new RoofFit.Tile(3, 4, 1, RoofFit.ALONG_Y, true, 1f + 2f * THIRD, 1f + THIRD));
        roof.add(new RoofFit.Tile(3, 5, 1, RoofFit.ALONG_Y, true, 1f + THIRD, 1f));
        RoofFit.fit(roof);
        return roof;
    }

    private static void wingRunsIntoTheMainRoofInAStraightValley() {
        List<RoofFit.Tile> carried = RoofFit.valleys(mainRoofAndWing(), new ArrayList<>());
        check(carried.size() == 3, "the wing's slope is carried over the three squares of the main roof it stands above, not " + carried.size());
        HashMap<Long, RoofFit.Tile> over = new HashMap<>();
        for (RoofFit.Tile tile : carried) {
            check(tile.carried && tile.fitAxis == RoofFit.ALONG_Y && !tile.shaped, "what is carried is the wing's own plane, whole");
            over.put(RoofFit.key(tile.x, tile.y, 0), tile);
        }
        RoofFit.Tile upper = over.get(RoofFit.key(1, 4, 0)), lower = over.get(RoofFit.key(2, 5, 0));
        check(upper != null && near(upper.fitLow, 1f + 2f * THIRD) && near(upper.fitHigh, 1f + THIRD), "the upper row goes on two squares, at the heights it had");
        check(lower != null && over.containsKey(RoofFit.key(2, 4, 0)) && !over.containsKey(RoofFit.key(1, 5, 0)), "and the lower row one: each stops where the main roof is over it everywhere");
    }

    private static void slopeIsNotCarriedOverAHip() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        roof.add(new RoofFit.Tile(0, 0, 1, RoofFit.ALONG_Y, true, 1f + THIRD, 1f));
        roof.add(new RoofFit.Tile(1, 0, 1, RoofFit.ALONG_X, true, 1f + THIRD, 1f));
        RoofFit.fit(roof);
        check(RoofFit.valleys(roof, new ArrayList<>()).isEmpty(), "a slope that falls the way another would be carried over it meets it in a hip, and nothing is carried");
    }

    private static void valleyTileThatReadsLevelIsFolded() {
        float sixth = THIRD / 2f;
        List<RoofFit.Tile> roof = new ArrayList<>();
        // The main roof comes down from the north to the valley's square, and the wing's west slope comes up to it from the south.
        roof.add(new RoofFit.Tile(0, 0, 1, RoofFit.ALONG_Y, true, 1f + 2f * sixth, 1f + sixth));
        RoofFit.Tile valley = new RoofFit.Tile(0, 1, 1, RoofFit.LEVEL, true, 1f + sixth, 1f + sixth);
        roof.add(valley);
        roof.add(new RoofFit.Tile(0, 2, 1, RoofFit.ALONG_X, true, 1f, 1f + sixth));
        RoofFit.fit(roof);
        check(RoofFit.valleys(roof, new ArrayList<>()).isEmpty() && valley.shaped && valley.crease == RoofFit.NE_SW, "the tile between two slopes that meet over it is folded along the line they meet on");
        check(near(valley.corners[0], 1f + sixth) && near(valley.corners[1], 1f + sixth) && near(valley.corners[2], 1f + sixth) && near(valley.corners[3], 1f),
                "with its one low corner at the foot of the valley");
    }

    private static void lowestRowThatReadsLevelIsTheSlopeGoingOnDown() {
        float sixth = THIRD / 2f;
        List<RoofFit.Tile> roof = new ArrayList<>();
        RoofFit.Tile foot = new RoofFit.Tile(0, 0, 1, RoofFit.LEVEL, true, 1f, 1f);
        roof.add(foot);
        roof.add(new RoofFit.Tile(0, 1, 1, RoofFit.ALONG_Y, true, 1f + sixth, 1f + 2f * sixth));
        roof.add(new RoofFit.Tile(0, 2, 1, RoofFit.ALONG_Y, true, 1f + 2f * sixth, 1f + 3f * sixth));
        RoofFit.fit(roof);
        check(foot.fitAxis == RoofFit.ALONG_Y && near(foot.fitLow, 1f) && near(foot.fitHigh, 1f + sixth), "a level tile a tile's climb under the low edge of a slope is the slope's lowest row");
    }

    /**
     * The diner's shape: a block 8 by 6 with a wing 5 by 4 on the north of its east end, x 0..7
     * and y 0..9, under a roof that climbs two tiles from every wall to a level top.
     */
    private static boolean inL(int x, int y) {
        return x >= 0 && x <= 7 && y >= 4 && y <= 9 || x >= 3 && x <= 7 && y >= 0 && y <= 3;
    }

    private static boolean topOfL(int x, int y) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (!inL(x + dx, y + dy)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static final RoofFit.Site L_SHAPED = new RoofFit.Site() {
        @Override
        public boolean stands(int x, int y, int z) {
            return inL(x, y);
        }

        @Override
        public boolean floored(int x, int y, int z) {
            return z == 2 && topOfL(x, y);
        }

        @Override
        public boolean taken(int x, int y, int z, float roof) {
            return floored(x, y, z) || floored(x, y, z + 1);
        }
    };

    /** What the map holds of that roof: the slopes that fall south and east, cut to triangles at the corners they share with a slope it leaves out. */
    private static List<RoofFit.Tile> mapOfL() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int y = 0; y <= 7; y++) {
            if (y >= 1) {
                roof.add(new RoofFit.Tile(6, y, 1, RoofFit.ALONG_X, true, 2f, 2f - THIRD));
            }
            roof.add(new RoofFit.Tile(7, y, 1, RoofFit.ALONG_X, true, 2f - THIRD, EAVE));
        }
        for (int x = 0; x <= 5; x++) {
            if (x >= 1) {
                roof.add(new RoofFit.Tile(x, 8, 1, RoofFit.ALONG_Y, true, 2f, 2f - THIRD));
            }
            roof.add(new RoofFit.Tile(x, 9, 1, RoofFit.ALONG_Y, true, 2f - THIRD, EAVE));
        }
        roof.add(new RoofFit.Tile(7, 8, 1, RoofFit.ALONG_X, true, 2f - THIRD, EAVE));
        roof.add(new RoofFit.Tile(6, 9, 1, RoofFit.ALONG_Y, true, 2f - THIRD, EAVE));
        roof.add(new RoofFit.Tile(6, 8, 1, RoofFit.NORTH_WEST_ODD, true, 2f, 2f - THIRD));
        roof.add(new RoofFit.Tile(7, 9, 1, RoofFit.NORTH_WEST_ODD, true, 2f - THIRD, EAVE));
        RoofFit.fit(roof);
        return roof;
    }

    private static float[] cornersOf(RoofFit.Tile tile) {
        float[] corners = new float[4];
        if (tile.shaped) {
            System.arraycopy(tile.corners, 0, corners, 0, 4);
        } else {
            RoofFit.cornersOf(tile.fitAxis, tile.fitLow, tile.fitHigh, corners);
        }
        return corners;
    }

    private static HashMap<Long, RoofFit.Tile> bySquare(List<RoofFit.Tile> tiles, List<RoofFit.Tile> made) {
        HashMap<Long, RoofFit.Tile> over = new HashMap<>();
        for (RoofFit.Tile tile : tiles) {
            over.put(RoofFit.key(tile.x, tile.y, 0), tile);
        }
        for (RoofFit.Tile tile : made) {
            check(over.put(RoofFit.key(tile.x, tile.y, 0), tile) == null, "no made tile lies on a square that has a tile: " + tile.x + ", " + tile.y);
        }
        return over;
    }

    private static void planeAndFoldAreToldApartByTheMiddle() {
        float[] plane = new float[4];
        RoofFit.cornersOf(RoofFit.ALONG_X, 1f, 2f, plane);
        check(plane[0] == 1f && plane[1] == 2f && plane[2] == 2f && plane[3] == 1f, "a slope along x is as high at its two west corners and at its two east ones");
        RoofFit.cornersOf(RoofFit.ALONG_Y, 1f, 2f, plane);
        check(plane[0] == 1f && plane[1] == 1f && plane[2] == 2f && plane[3] == 2f, "and one along y at its two north and its two south");
        check(RoofFit.creaseOf(plane, 1.5f) == RoofFit.UNCREASED, "a plane has no fold");
        float[] hip = {1f, 1f, 2f, 1f};
        check(RoofFit.creaseOf(hip, 1.5f) == RoofFit.NW_SE, "three corners low and the middle halfway: folded through the high corner");
        float[] dished = {1f, 2f, 1f, 1f};
        check(RoofFit.creaseOf(dished, 1.5f) == RoofFit.NE_SW, "and through the other two when it is one of them that stands up");
        check(RoofFit.creaseOf(hip, 1f) == RoofFit.NE_SW, "the same corners over a middle that is low are folded the other way");
    }

    private static void longestRunLeavesOutAStrayRow() {
        boolean[] rows = new boolean[100];
        for (int row = 5; row <= 40; row++) {
            rows[row] = true;
        }
        rows[20] = false;
        rows[99] = true;
        int[] run = RoofFit.longestRun(100, 3, row -> rows[row]);
        check(run[0] == 5 && run[1] == 40, "a row or two with nothing on them does not end a run, and a stray row far off is not of it: " + run[0] + ".." + run[1]);
        run = RoofFit.longestRun(100, 3, row -> row >= 90);
        check(run[0] == 90 && run[1] == 99, "a run that goes on to the last row ends there");
    }

    private static void roofOverAnLShapedBuildingIsClosedAllRound() {
        List<RoofFit.Tile> tiles = mapOfL();
        List<RoofFit.Tile> made = RoofFit.backs(tiles, L_SHAPED);
        HashMap<Long, RoofFit.Tile> over = bySquare(tiles, made);
        for (int x = -1; x <= 8; x++) {
            for (int y = -1; y <= 10; y++) {
                RoofFit.Tile tile = over.get(RoofFit.key(x, y, 0));
                if (!inL(x, y)) {
                    check(tile == null, "no roof is laid past the walls: " + x + ", " + y);
                    continue;
                }
                check(tile != null || topOfL(x, y), "every square under the roof has a tile or the level top over it: " + x + ", " + y);
                if (tile == null) {
                    continue;
                }
                float[] here = cornersOf(tile);
                // The roof is as high over a point as the nearest wall is far, along whichever axis is further, up to the top.
                for (int corner = 0; corner < 4; corner++) {
                    float px = x + RoofFit.cornerX(corner), py = y + RoofFit.cornerY(corner), away = 2f;
                    for (int ox = -3; ox <= 10; ox++) {
                        for (int oy = -3; oy <= 12; oy++) {
                            if (!inL(ox, oy)) {
                                away = Math.min(away, Math.max(Math.max(0f, Math.max(ox - px, px - ox - 1)), Math.max(0f, Math.max(oy - py, py - oy - 1))));
                            }
                        }
                    }
                    check(near(here[corner], EAVE + THIRD * away), "the roof at corner " + corner + " of " + x + ", " + y + " is " + here[corner] + ", not " + (EAVE + THIRD * away));
                }
            }
        }
    }

    private static void hipsAndValleyOfTheLShapeAreFoldedTheRightWay() {
        List<RoofFit.Tile> tiles = mapOfL();
        List<RoofFit.Tile> made = RoofFit.backs(tiles, L_SHAPED);
        HashMap<Long, RoofFit.Tile> over = bySquare(tiles, made);
        // The block's north-west corner, the wing's, and the corner where the wing leaves the block.
        int[][] folds = {{0, 4, 1}, {1, 5, 1}, {3, 0, 1}, {4, 1, 1}, {3, 4, -1}, {4, 5, -1}};
        for (int[] fold : folds) {
            RoofFit.Tile tile = over.get(RoofFit.key(fold[0], fold[1], 0));
            check(tile.shaped && tile.crease == RoofFit.NW_SE, "the made slopes meet in a fold from the corner inwards at " + fold[0] + ", " + fold[1]);
            float along = tile.corners[0] + tile.corners[2], off = tile.corners[1] + tile.corners[3];
            check(fold[2] > 0 ? off < along - 0.1f : off > along + 0.1f, (fold[2] > 0 ? "a hip stands over" : "a valley lies under") + " the roof either side of it at " + fold[0] + ", " + fold[1]);
        }
        RoofFit.Tile beside = over.get(RoofFit.key(1, 4, 0));
        check(!beside.shaped && beside.fitAxis == RoofFit.ALONG_Y && near(beside.fitLow, EAVE), "beside a hip the lower of the two slopes is the roof");
        beside = over.get(RoofFit.key(4, 4, 0));
        check(!beside.shaped && beside.fitAxis == RoofFit.ALONG_X && near(beside.fitLow, 2f - THIRD), "beside a valley the higher of the two is");
    }

    private static void mapTilesAtAHipKeepTheirOwnHalf() {
        List<RoofFit.Tile> tiles = mapOfL();
        RoofFit.backs(tiles, L_SHAPED);
        // The wing's north-east corner: the map's slope falls east and the made one north.
        for (RoofFit.Tile tile : tiles) {
            boolean northEast = tile.x == 7 && tile.y == 0 || tile.x == 6 && tile.y == 1, southWest = tile.x == 0 && tile.y == 9 || tile.x == 1 && tile.y == 8;
            if (!northEast && !southWest) {
                check(!tile.shaped || tile.axis == RoofFit.NORTH_WEST_ODD, "a tile away from a hip is left one plane: " + tile.x + ", " + tile.y);
                continue;
            }
            check(tile.shaped && tile.crease == RoofFit.NE_SW && tile.own == 1, "the map's tile at a hip is folded, and its sprite keeps the south-east half: " + tile.x + ", " + tile.y);
            // The fold runs up from the building's corner: its inner end is the one high corner.
            int inner = northEast ? 3 : 1;
            float low = tile.corners[2];
            for (int corner = 0; corner < 4; corner++) {
                check(near(tile.corners[corner], corner == inner ? low + THIRD : low), "the made half comes down to the height the tile's own half does: corner " + corner + " of " + tile.x + ", " + tile.y);
            }
        }
    }

    private static void ridgeAndHipsAreCappedAndAValleyIsNot() {
        List<RoofFit.Tile> tiles = mapOfL();
        List<RoofFit.Tile> made = RoofFit.backs(tiles, L_SHAPED);
        List<RoofFit.Cap> caps = RoofFit.caps(tiles, made);
        // Five corners that point out, two tiles of hip to each. The roof's top is level: it has no ridge.
        check(caps.size() == 10, "each tile of each hip has a cap: " + caps.size());
        for (RoofFit.Cap cap : caps) {
            check(Math.abs(cap.x1 - cap.x0) == 1f && Math.abs(cap.y1 - cap.y0) == 1f && near(Math.abs(cap.high1 - cap.high0), THIRD), "a hip's cap runs corner to corner and climbs a tile's rise");
            boolean valley = Math.min(cap.x0, cap.x1) >= 3f && Math.min(cap.x0, cap.x1) <= 4f && Math.min(cap.y0, cap.y1) >= 4f && Math.min(cap.y0, cap.y1) <= 5f;
            check(!valley, "the valley has no cap");
            check(near(cap.fall, THIRD / 2f / (float) Math.sqrt(0.5)), "the roof falls away from a hip at the slope's pitch across the diagonal: " + cap.fall);
        }
        // A gable: its ridge is capped tile by tile.
        List<RoofFit.Tile> gable = gable(3, 3);
        caps = RoofFit.caps(gable, RoofFit.backs(gable, strip(0, 5)));
        check(caps.size() == 2, "a ridge has a cap along each tile of it: " + caps.size());
        for (RoofFit.Cap cap : caps) {
            check(cap.x0 == 3f && cap.x1 == 3f && near(cap.high0, 2f) && near(cap.high1, 2f) && near(cap.fall, THIRD), "along the line the two slopes meet on, at its height");
        }
    }

    /** The map's half of a gable over rows 0 and 1: deep tiles falling east from a ridge at 2 along the west edge of square x. */
    private static List<RoofFit.Tile> gable(int x, int deep) {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int y = 0; y < 2; y++) {
            for (int i = 0; i < deep; i++) {
                float high = 2f - i * THIRD;
                roof.add(new RoofFit.Tile(x + i, y, 1, RoofFit.ALONG_X, true, high, high - THIRD));
            }
        }
        RoofFit.fit(roof);
        return roof;
    }

    /** A building under rows 0 and 1 from one x to another, with nothing over its ground floor. */
    private static RoofFit.Site strip(int from, int to) {
        return new RoofFit.Site() {
            @Override
            public boolean stands(int x, int y, int z) {
                return x >= from && x <= to && y >= 0 && y <= 1;
            }

            @Override
            public boolean floored(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean taken(int x, int y, int z, float roof) {
                return false;
            }
        };
    }

    private static void gableOneTileWiderThanItsSlopesIsLevelAtTheRidge() {
        // Seven squares wide: three of slope either side, and the ridge over the middle of the fourth.
        List<RoofFit.Tile> tiles = gable(4, 3);
        List<RoofFit.Tile> made = RoofFit.backs(tiles, strip(0, 6));
        check(made.size() == 8, "every square of the far side is roofed: " + made.size());
        HashMap<Long, RoofFit.Tile> over = bySquare(tiles, made);
        RoofFit.Tile foot = over.get(RoofFit.key(0, 0, 0)), top = over.get(RoofFit.key(2, 1, 0)), middle = over.get(RoofFit.key(3, 0, 0));
        check(foot.fitAxis == RoofFit.ALONG_X && near(foot.fitLow, 1f) && near(top.fitHigh, 2f), "the far slope stands on the far wall and climbs to the ridge's height");
        check(middle.fitAxis == RoofFit.LEVEL && near(middle.fitLow, 2f), "and the square between the two slopes is level at it");
        List<RoofFit.Cap> caps = RoofFit.caps(tiles, made);
        check(caps.size() == 2 && caps.get(0).x0 == 3.5f && caps.get(0).fall == 0f, "with the cap along its middle: " + caps.size());
    }

    private static void chimneySquareInASlopeIsMended() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int y = 0; y < 5; y++) {
            if (y != 2) {
                roof.add(new RoofFit.Tile(3, y, 1, RoofFit.ALONG_X, true, 2f, 2f - THIRD));
            }
            roof.add(new RoofFit.Tile(2, y, 1, RoofFit.ALONG_X, true, 2f - THIRD, 2f));
        }
        RoofFit.fit(roof);
        RoofFit.Site site = new RoofFit.Site() {
            @Override
            public boolean stands(int x, int y, int z) {
                return x >= 2 && x <= 3 && y >= 0 && y < 5;
            }

            @Override
            public boolean floored(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean taken(int x, int y, int z, float roof) {
                return false;
            }
        };
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        check(made.size() == 1, "the one square of the slope the map leaves out is made: " + made.size());
        RoofFit.Tile mended = made.get(0);
        check(mended.x == 3 && mended.y == 2 && mended.fitAxis == RoofFit.ALONG_X && near(mended.fitLow, 2f) && near(mended.fitHigh, 2f - THIRD), "in the plane of the tiles either side of it");
    }

    private static void roofWithBothSidesAndMoreBuildingBeyondGetsNoMore() {
        List<RoofFit.Tile> roof = gable(3, 3);
        for (int y = 0; y < 2; y++) {
            for (int i = 0; i < 3; i++) {
                roof.add(new RoofFit.Tile(i, y, 1, RoofFit.ALONG_X, true, 1f + i * THIRD, 1f + (i + 1) * THIRD));
            }
        }
        RoofFit.fit(roof);
        check(RoofFit.backs(roof, strip(-6, 5)).isEmpty(), "a roof with both its slopes is not carried on over a lower part of the building beside it");
    }

    private static void hipTileOfTheMapIsBothItsSlopes() {
        List<RoofFit.Tile> tiles = mapOfL();
        for (RoofFit.Tile tile : tiles) {
            if (tile.axis != RoofFit.NORTH_WEST_ODD) {
                continue;
            }
            float high = tile.x == 6 ? 2f : 2f - THIRD;
            check(tile.shaped && tile.crease == RoofFit.NW_SE && tile.own == 3, "the map's own hip tile is folded and its sprite lies on both halves");
            check(near(tile.corners[0], high) && near(tile.corners[1], high - THIRD) && near(tile.corners[2], high - THIRD) && near(tile.corners[3], high - THIRD),
                    "its north-west corner is the high one, at the height of the slopes it ends");
        }
        RoofFit.Tile valley = new RoofFit.Tile(0, 0, 1, RoofFit.SOUTH_EAST_ODD, true, 1.4f, 1.1f);
        List<RoofFit.Tile> alone = new ArrayList<>(List.of(valley));
        RoofFit.fit(alone);
        check(valley.shaped && valley.corners[0] == 1.4f && valley.corners[1] == 1.4f && valley.corners[3] == 1.4f && valley.corners[2] == 1.1f,
                "a valley tile has its south-east corner low, and with no slope beside it keeps the heights its sprite gave");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-3f;
    }
}

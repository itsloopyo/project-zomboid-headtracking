package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import zombie.core.textures.Texture;
import zombie.iso.SpriteDetails.IsoObjectType;

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
        Checks.run("rowThatEndsAtItsOwnEaveKeepsItWhereItsPlaneRunsOnDown", RoofTests::rowThatEndsAtItsOwnEaveKeepsItWhereItsPlaneRunsOnDown);
        Checks.run("bothRidgesOfACrossGableArePeakedAndCappedWhole", RoofTests::bothRidgesOfACrossGableArePeakedAndCappedWhole);
        Checks.run("deepWingIsCarriedIntoTheMainRoofUpToItsRidge", RoofTests::deepWingIsCarriedIntoTheMainRoofUpToItsRidge);
        Checks.run("eaveRowHangsUnderTheFootOfASlopeOnly", RoofTests::eaveRowHangsUnderTheFootOfASlopeOnly);
        Checks.run("wingsRidgeRunsOnToTheMiddleOfTheMainRidge", RoofTests::wingsRidgeRunsOnToTheMiddleOfTheMainRidge);
        Checks.run("secondSpriteOnAValleyTileLiesOnItsFold", RoofTests::secondSpriteOnAValleyTileLiesOnItsFold);
        Checks.run("unclearTileOnAValleysLineIsFoldedAsTheValleyIs", RoofTests::unclearTileOnAValleysLineIsFoldedAsTheValleyIs);
        Checks.run("hippedCornerAtTheEaveCarriesTheEaveRowOn", RoofTests::hippedCornerAtTheEaveCarriesTheEaveRowOn);
        Checks.run("hipCutFlatGetsItsFarSlopeAcrossTheCrown", RoofTests::hipCutFlatGetsItsFarSlopeAcrossTheCrown);
        Checks.run("levelSheetPaintedOverItsFloorIsAFlatTop", RoofTests::levelSheetPaintedOverItsFloorIsAFlatTop);
        Checks.run("sheetsBehindARidgeAreNoPartOfTheRoof", RoofTests::sheetsBehindARidgeAreNoPartOfTheRoof);
        Checks.run("canopyIsNotCarriedToTheFarWall", RoofTests::canopyIsNotCarriedToTheFarWall);
        Checks.run("eaveOverALeanToThatStopsShortOfItIsMade", RoofTests::eaveOverALeanToThatStopsShortOfItIsMade);
        Checks.run("trimPastAGableIsNoRoofGoingOn", RoofTests::trimPastAGableIsNoRoofGoingOn);
        Checks.run("trimPastAHipsCornerLeavesTheCornersEave", RoofTests::trimPastAHipsCornerLeavesTheCornersEave);
    }

    private static void hippedCornerAtTheEaveCarriesTheEaveRowOn() {
        // The one-storey house at (6404..6416, 5344..5358): the hip's corner over the west end of
        // its south eave, level 1 at (0, 0), reads level at the eave; the slope east of it falls
        // south to the eave, and the roof rises from the corner to the north.
        TestMap map = hippedCorner(1f + THIRD / 2f, false);
        RoofFit.Tile corner = Roofs.at(map.cell, 0, 0, 1, 0, 2f).tile;
        check(RoofFit.levelOnItsFloor(corner), "the corner's tile lies level at the floor of its level");
        check(map.build.roofBuilder.eaveRow(0, 1, 0), "the eave row the map hangs under the corner goes on with the slope's");
        map.build.roofBuilder.addRoof(new SpriteGeometry.Mesh(), map.painted, 0, 0, 1);
        float[] box = boundsOf(TestMap.plain());
        check(TestMap.plain().triangles() > 0 && box[0] < 0f && box[1] >= 1f - 1e-4f && box[3] > 1f,
                "the eave's board, soffit and gutter run along the corner's south edge to past its west end, closed there: x " + box[0] + " to " + box[1]);
        // The same corner as the map has it now, a slope cut to a triangle that the fit folds with
        // the mod's own sheet, its south and west edges both at the eave: the row under it is the fold's.
        TestMap cut = new TestMap();
        RoofFit.Tile cutCorner = new RoofFit.Tile(0, 0, 1, RoofFit.ALONG_Y, true, 1.172f, 1f);
        cutCorner.cutCorner = 0;
        List<RoofFit.Tile> hip = new ArrayList<>(List.of(cutCorner, new RoofFit.Tile(0, -1, 1, RoofFit.ALONG_Y, true, 1.339f, 1.172f),
                new RoofFit.Tile(1, 0, 1, RoofFit.ALONG_Y, true, 1.172f, 1f)));
        RoofFit.fit(hip);
        for (RoofFit.Tile tile : hip) {
            cut.roofTile(tile, cut.painted);
        }
        cut.roofSprite(0, 1, 0, "roofs_02_6", "attachedN", "isEave");
        cut.roofSprite(1, 1, 0, "roofs_02_6", "attachedN", "isEave");
        cut.roofSprite(-1, 1, 0, "roofs_02_30", "attachedN", "isEave");
        check(cutCorner.shaped && cut.build.roofBuilder.eaveRow(0, 1, 0) && cut.build.roofBuilder.eaveRowEnd(-1, 1, 0),
                "under a hip's corner the map cuts to a triangle the eave row goes on, and the corner piece past it goes with it");
        map = hippedCorner(1f, false);
        map.build.roofBuilder.addRoof(new SpriteGeometry.Mesh(), map.painted, 0, 0, 1);
        check(!map.build.roofBuilder.eaveRow(0, 1, 0) && TestMap.plain().triangles() == 0,
                "a flat roof beside a slope, level where a hip's corner rises into the roof, is no corner: no eave row under it, no eave made");
        map = hippedCorner(1f + THIRD / 2f, true);
        map.build.roofBuilder.addRoof(new SpriteGeometry.Mesh(), map.painted, 0, 0, 1);
        check(!map.build.roofBuilder.eaveRow(0, 1, 0) && TestMap.plain().triangles() == 0,
                "nor is a level tile with more roof past the far end of the eave's run");
    }

    /**
     * A roof on level 1 over x 0 to 3: the tile at (0, 0) level at the floor, the slope east of
     * it falling south to its eave on y 1, the row of eave the map hangs under that on level 0,
     * and across from the eave at (0, -1) a level tile at height across. Westward more level
     * roof at (-1, 0) where flatBeyond.
     */
    private static TestMap hippedCorner(float across, boolean flatBeyond) {
        TestMap map = new TestMap();
        List<RoofFit.Tile> tiles = new ArrayList<>(List.of(new RoofFit.Tile(0, 0, 1, RoofFit.LEVEL, true, 1.0052f, 1f),
                new RoofFit.Tile(0, -1, 1, RoofFit.LEVEL, true, across, across)));
        for (int x = 1; x <= 3; x++) {
            // As the fit lays that house's slope: its foot 0.017 under the floor.
            tiles.add(new RoofFit.Tile(x, 0, 1, RoofFit.ALONG_Y, true, 1.105f, 0.983f));
        }
        if (flatBeyond) {
            tiles.add(new RoofFit.Tile(-1, 0, 1, RoofFit.LEVEL, true, 1f, 1f));
        }
        RoofFit.fit(tiles);
        for (RoofFit.Tile tile : tiles) {
            map.roofTile(tile, map.painted);
        }
        for (int x = 0; x <= 3; x++) {
            map.roofSprite(x, 1, 0, "roofs_02_6", "attachedN", "isEave");
            map.wall(x, 1, 0, true);
        }
        // Set as RoofBuilder.addRoof is given the tile's own sprite: 64 pixels high, twice the size, so level.
        map.build.frame.originU = 2f;
        map.build.frame.originV = 192f;
        map.build.frame.takeTexels(map.painted);
        return map;
    }

    /** The least and most x, then the least and most y, of a mesh's vertices. */
    private static float[] boundsOf(SpriteGeometry.Mesh mesh) {
        float[] box = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            box[0] = Math.min(box[0], mesh.vertices[at]);
            box[1] = Math.max(box[1], mesh.vertices[at]);
            box[2] = Math.min(box[2], mesh.vertices[at + 1]);
            box[3] = Math.max(box[3], mesh.vertices[at + 1]);
        }
        return box;
    }

    private static void unclearTileOnAValleysLineIsFoldedAsTheValleyIs() {
        RoofFit.Tile unclear = null;
        for (RoofFit.Tile tile : crossGable()) {
            if (tile.x == 7 && tile.y == 7) {
                unclear = tile;
            }
        }
        float high = 2f + 2f * THIRD, low = 2f + THIRD;
        check(unclear.shaped && unclear.fitted && unclear.crease == RoofFit.NW_SE,
                "the sprite between the valley's tiles that reads neither way is folded along the valley, where it was laid as read and left a hole by the gable");
        check(near(unclear.corners[0], high) && near(unclear.corners[1], high) && near(unclear.corners[2], low) && near(unclear.corners[3], high),
                "with the higher of the two slopes at each corner, its south-east corner the valley's foot: " + unclear.corners[0] + " " + unclear.corners[1] + " " + unclear.corners[2] + " " + unclear.corners[3]);
        RoofFit.Tile alone = new RoofFit.Tile(1, 1, 2, RoofFit.ALONG_Y, false, 2.82f, 2f + THIRD);
        List<RoofFit.Tile> plain = new ArrayList<>(List.of(alone, new RoofFit.Tile(1, 2, 2, RoofFit.ALONG_X, true, 2f + 2f * THIRD, 2f + THIRD),
                new RoofFit.Tile(2, 1, 2, RoofFit.ALONG_Y, true, 2f + 2f * THIRD, 2f + THIRD)));
        RoofFit.fit(plain);
        check(!alone.shaped && !alone.fitted, "with no valley or hip tile of the map along the diagonal it is left as read");
    }

    private static void wingsRidgeRunsOnToTheMiddleOfTheMainRidge() {
        List<RoofFit.Tile> tiles = crossGable();
        List<RoofFit.Tile> made = RoofFit.backs(tiles, CROSS_GABLE);
        RoofFit.peaks(tiles, made);
        RoofFit.Tile end = null, foot = null, main = null;
        for (RoofFit.Tile tile : made) {
            if (tile.peak > 0f && tile.x == 4 && tile.y == 5) {
                end = tile;
            } else if (tile.peak > 0f && tile.x == 4 && tile.y == 17) {
                foot = tile;
            } else if (tile.peak > 0f && tile.x == 4 && tile.y == 4) {
                main = tile;
            }
        }
        check(end != null && end.peakBefore == 0.5f && end.peakAfter == 0f,
                "the wing's ridge runs on from its last square to the middle of the main roof's ridge, where the two meet: " + (end == null ? "none" : end.peakBefore));
        check(foot != null && foot.peakBefore == 0f && foot.peakAfter == 0f, "and stops at its own gable at the other end");
        check(main != null && main.peakBefore == 0f && main.peakAfter == 0f, "the main roof's ridge goes on along its own squares, past the wing's");
        float from = Float.MAX_VALUE;
        for (RoofFit.Cap cap : RoofFit.caps(tiles, made)) {
            if (cap.x0 == 4.5f && cap.x1 == 4.5f) {
                from = Math.min(from, Math.min(cap.y0, cap.y1));
            }
        }
        check(from == 4.5f, "and its cap with it, to the main ridge's line: " + from);
    }

    private static void secondSpriteOnAValleyTileLiesOnItsFold() {
        RoofFit.Tile valley = null;
        for (RoofFit.Tile tile : crossGable()) {
            if (tile.x == 5 && tile.y == 5) {
                valley = tile;
            }
        }
        RoofFit.Tile hip = new RoofFit.Tile(1, 1, 1, RoofFit.NORTH_WEST_ODD, true, 1f + THIRD, 1f);
        RoofFit.fit(new ArrayList<>(List.of(hip)));
        check(valley.shaped && RoofFit.valley(valley) && hip.shaped && !RoofFit.valley(hip), "the valley's tile is folded into a valley, the hip's end over a hip");
        // A second sprite on the square, as RoofBuilder.addRoof lays it: a piece narrower than a
        // tile, and a ridge's half, three quarters of a tile wide.
        SpriteGeometry.Mesh piece = laidSecond(valley, 60);
        check(piece.triangles() > 0 && onTheFold(piece, valley),
                "the piece the map paints over the top of the valley, on the valley tile's square, lies on the valley's fold and not as it reads");
        check(laidSecond(valley, 100).triangles() == 0, "a ridge's half on the valley's square is not laid there, on the fold or as it reads: the ridge is made");
        piece = laidSecond(hip, 60);
        check(piece.triangles() == 0 || !onTheFold(piece, hip),
                "a second sprite on a hip's fold lies as it reads, not in the plane of the hip's own sprite or over the mod's sheet");
    }

    /**
     * What RoofBuilder.addRoof lays of a second sprite, frame pixels wide, on the square of a
     * tile the fit left as given, whose own sprite is another.
     */
    private static SpriteGeometry.Mesh laidSecond(RoofFit.Tile fitted, int wide) {
        TestMap map = new TestMap();
        map.roofTile(fitted, new Texture(1, 128, 128));
        // 64 pixels high at twice the size is level: it reads half a level over its floor.
        Texture second = new Texture(2, wide, 64);
        second.paint(0, 0, wide, 64);
        map.build.frame.originU = wide / 2f;
        map.build.frame.originV = 96f;
        map.build.frame.takeTexels(second);
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        map.build.roofBuilder.addRoof(mesh, second, fitted.x, fitted.y, fitted.z);
        return mesh;
    }

    /** Whether every corner of a mesh lies on a folded tile's two triangles. */
    private static boolean onTheFold(SpriteGeometry.Mesh mesh, RoofFit.Tile tile) {
        int[] order = RoofFit.TRIANGLES[tile.crease];
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float a = mesh.vertices[at] - tile.x, b = mesh.vertices[at + 1] - tile.y, up = mesh.vertices[at + 2] / ViewCamera.LEVEL_HEIGHT;
            boolean held = false;
            for (int triangle = 0; triangle < 2 && !held; triangle++) {
                // The height of the triangle's plane at (a, b), from its three corners.
                float[] p = new float[9];
                for (int i = 0; i < 3; i++) {
                    int corner = order[triangle * 3 + i];
                    p[i * 3] = RoofFit.cornerX(corner);
                    p[i * 3 + 1] = RoofFit.cornerY(corner);
                    p[i * 3 + 2] = tile.corners[corner];
                }
                float ux = p[3] - p[0], uy = p[4] - p[1], uz = p[5] - p[2], vx = p[6] - p[0], vy = p[7] - p[1], vz = p[8] - p[2];
                float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                held = Math.abs(p[2] - (nx * (a - p[0]) + ny * (b - p[1])) / nz - up) < 0.01f;
            }
            if (!held) {
                return false;
            }
        }
        return true;
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

    /**
     * A house over x 0 to 7 and y 1 to 6, rooms on levels 0 to 2 and the loft over them, its
     * ridge along x on the line y 4 at 4, its south slope down to its eave at 3 on y 7; over
     * columns 0 to 3 the same slope runs on down over a lean-to, y 7 to 9, to an eave at 2 on
     * y 10. The map has the south slope only.
     */
    private static void rowThatEndsAtItsOwnEaveKeepsItWhereItsPlaneRunsOnDown() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 7; x++) {
            for (int y = 4; y <= (x <= 3 ? 9 : 6); y++) {
                float north = 4f - (y - 4) * THIRD;
                roof.add(new RoofFit.Tile(x, y, (int) Math.floor(north - THIRD + 0.05f), RoofFit.ALONG_Y, true, north, north - THIRD));
            }
        }
        roof.sort(Comparator.comparingLong(tile -> RoofFit.key(tile.x, tile.y, tile.z)));
        RoofFit.fit(roof);
        RoofFit.Site house = new RoofFit.Site() {
            @Override
            public boolean stands(int x, int y, int z) {
                return x >= 0 && x <= 7 && y >= 1 && y <= 6 || x >= 0 && x <= 3 && y >= 7 && y <= 9;
            }

            @Override
            public boolean floored(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean taken(int x, int y, int z, float over) {
                return stands(x, y, z) && z <= (y >= 7 ? 1 : 2);
            }
        };
        HashMap<Long, RoofFit.Tile> over = new HashMap<>();
        for (RoofFit.Tile tile : RoofFit.backs(roof, house)) {
            over.put(RoofFit.key(tile.x, tile.y, 0), tile);
        }
        for (int x = 4; x <= 7; x++) {
            for (int y = 1; y <= 3; y++) {
                RoofFit.Tile tile = over.get(RoofFit.key(x, y, 0));
                check(tile != null && !tile.shaped && tile.fitAxis == RoofFit.ALONG_Y && near(tile.fitLow, 3f + (y - 1) * THIRD) && near(tile.fitHigh, 3f + y * THIRD),
                        "where the slope stops at the house's own eave the far slope is made from the far eave at 3 up to the ridge, though the slope runs on down to 2 beside it, at "
                                + x + ", " + y + ": " + said(tile));
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

    /** A building over x 1..3 and rows 0..4, with nothing over its ground floor. */
    private static final RoofFit.Site BLOCK = new RoofFit.Site() {
        @Override
        public boolean stands(int x, int y, int z) {
            return x >= 1 && x <= 3 && y >= 0 && y <= 4;
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

    private static void hipCutFlatGetsItsFarSlopeAcrossTheCrown() {
        // The map's south slope on row 4, climbing north to the crown; the crown level over rows 1..3, as Roofs reads a flat top.
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 1; x <= 3; x++) {
            roof.add(new RoofFit.Tile(x, 4, 1, RoofFit.ALONG_Y, true, EAVE, 1f));
            for (int y = 1; y <= 3; y++) {
                RoofFit.Tile crown = new RoofFit.Tile(x, y, 1, RoofFit.LEVEL, true, EAVE, EAVE);
                crown.sheet = true;
                roof.add(crown);
            }
        }
        check(RoofFit.behindRidges(roof).isEmpty(), "a crown is the top of its roof and no sheet behind a ridge");
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, BLOCK);
        HashMap<Long, RoofFit.Tile> over = bySquare(roof, made);
        for (int x = 1; x <= 3; x++) {
            RoofFit.Tile far = over.get(RoofFit.key(x, 0, 0));
            check(far != null && far.fitAxis == RoofFit.ALONG_Y && near(far.fitLow, 1f) && near(far.fitHigh, EAVE),
                    "the north slope stands on the north wall and climbs to the crown at x " + x);
        }
    }

    private static void sheetsBehindARidgeAreNoPartOfTheRoof() {
        // The map's slope on row 3 falls south from a ridge at 1 + 2/3 along its north edge; the sheets north of it, a third
        // of a level up, lie behind that ridge where the game's view never looks.
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 1; x <= 3; x++) {
            roof.add(new RoofFit.Tile(x, 3, 1, RoofFit.ALONG_Y, true, EAVE + THIRD, EAVE));
            for (int y = 0; y <= 2; y++) {
                RoofFit.Tile sheet = new RoofFit.Tile(x, y, 1, RoofFit.LEVEL, true, EAVE, EAVE);
                sheet.sheet = true;
                roof.add(sheet);
            }
        }
        List<RoofFit.Tile> behind = RoofFit.behindRidges(roof);
        check(behind.size() == 9 && behind.stream().allMatch(tile -> tile.sheet), "every sheet north of the ridge is behind it, joined ones too: " + behind.size());
        // A crown that the south slope climbs to stays, though a ridge beside it on the east climbs higher.
        List<RoofFit.Tile> crowned = new ArrayList<>();
        for (int x = 1; x <= 3; x++) {
            crowned.add(new RoofFit.Tile(x, 4, 1, RoofFit.ALONG_Y, true, EAVE, 1f));
            for (int y = 1; y <= 3; y++) {
                RoofFit.Tile sheet = new RoofFit.Tile(x, y, 1, RoofFit.LEVEL, true, EAVE, EAVE);
                sheet.sheet = true;
                crowned.add(sheet);
            }
        }
        for (int y = 1; y <= 3; y++) {
            crowned.add(new RoofFit.Tile(4, y, 1, RoofFit.ALONG_X, true, EAVE + THIRD, EAVE));
        }
        check(RoofFit.behindRidges(crowned).isEmpty(), "a crown its slope climbs to is no sheet behind a ridge");
        // A long gable: its ridge climbs above the sheets along their whole south side, and only the slopes at the two
        // ends of the house meet them at their height.
        List<RoofFit.Tile> gable = new ArrayList<>();
        for (int x = 1; x <= 8; x++) {
            gable.add(new RoofFit.Tile(x, 3, 1, RoofFit.ALONG_Y, true, EAVE + THIRD, EAVE));
            for (int y = 1; y <= 2; y++) {
                RoofFit.Tile sheet = new RoofFit.Tile(x, y, 1, RoofFit.LEVEL, true, EAVE, EAVE);
                sheet.sheet = true;
                gable.add(sheet);
            }
        }
        gable.add(new RoofFit.Tile(0, 1, 1, RoofFit.ALONG_X, true, 1f, EAVE));
        gable.add(new RoofFit.Tile(9, 1, 1, RoofFit.ALONG_X, true, EAVE, 1f));
        check(RoofFit.behindRidges(gable).size() == 16, "sheets a long ridge climbs above are behind it though the ends meet them");
    }

    private static void levelSheetPaintedOverItsFloorIsAFlatTop() {
        check(Roofs.flatTop(new RoofFit.Tile(0, 0, 2, RoofFit.LEVEL, true, 2f + THIRD, 2f + THIRD)), "a level sheet a third of a level over its floor is a crown");
        check(Roofs.flatTop(new RoofFit.Tile(0, 0, 2, RoofFit.LEVEL, true, 2f + 2f * THIRD, 2f + 2f * THIRD)), "and so is one two thirds over it");
        check(!Roofs.flatTop(new RoofFit.Tile(0, 0, 2, RoofFit.LEVEL, true, 2f, 2f)), "a sheet on its floor lies under a slope");
        check(!Roofs.flatTop(new RoofFit.Tile(0, 0, 2, RoofFit.ALONG_X, true, 2f, 2f + THIRD)), "a slope is no flat top");
        RoofFit.Tile trim = new RoofFit.Tile(0, 0, 2, RoofFit.LEVEL, true, 2f + THIRD, 2f + THIRD);
        trim.fitting = true;
        check(!Roofs.flatTop(trim), "nor is a trim narrower than a tile");
        check(Roofs.ceiling("ceilings_01_0") && !Roofs.ceiling("floors_exterior_tilesandstone_01_0") && !Roofs.ceiling(null),
                "the map's ceilings are told from a terrace's floor by their sheet");
    }

    private static void canopyIsNotCarriedToTheFarWall() {
        // A shallow canopy along the south edge, a tenth of a level a tile: no roof has a far side at that pitch.
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 1; x <= 3; x++) {
            roof.add(new RoofFit.Tile(x, 4, 1, RoofFit.ALONG_Y, true, 1.27f, 1.16f));
        }
        RoofFit.fit(roof);
        check(RoofFit.backs(roof, BLOCK).isEmpty(), "nothing is made over the house from a canopy");
    }

    /**
     * A map of one slope along y on (0, 0, z), from low at its north edge to high at its south,
     * fitted, with a frame for RoofBuilder.addRoof whose sprite covers all of the tile's face. A
     * climb of exactly a third is laid as a picture the stand-in sprite cannot give, so tests
     * climb a little less, as the map's tiles read.
     */
    private static TestMap slopeAlongY(int z, float low, float high) {
        TestMap map = new TestMap();
        RoofFit.Tile slope = new RoofFit.Tile(0, 0, z, RoofFit.ALONG_Y, true, low, high);
        RoofFit.fit(new ArrayList<>(List.of(slope)));
        map.roofTile(slope, map.painted);
        map.build.frame.takeTexels(map.painted);
        map.build.frame.left = map.build.frame.top = -4096f;
        map.build.frame.width = map.build.frame.height = 8192f;
        return map;
    }

    /** A trim from a roof's accents on the square, as Roofs reads one: a tile of a roof narrower than a tile, so fitting. */
    private static void trim(TestMap map, int x, int y, int z, String name, String... flags) {
        map.cell.square(x, y, z).add(map.sprite(name, IsoObjectType.WestRoofB, flags));
        RoofFit.Tile tile = new RoofFit.Tile(x, y, z, RoofFit.LEVEL, true, z + 0.9f, z + 0.9f);
        tile.fitting = true;
        Roofs.Roof roof = new Roofs.Roof();
        TestMap.write(roof, "kept", Integer.MAX_VALUE / 2);
        HashMap<Long, Roofs.Laid> laid = TestMap.read(null, Roofs.class, "laid");
        laid.put(RoofFit.key(x, y, z), new Roofs.Laid(tile, map.painted, roof));
    }

    private static void eaveOverALeanToThatStopsShortOfItIsMade() {
        // The upper storey's slope falls north to its eave at the floor of level 2; a level down past the eave
        // lies a lean-to whose top is at 1.2, well short of 2.
        TestMap map = slopeAlongY(2, 2f, 2.32f);
        map.roofTile(fitted(new RoofFit.Tile(0, -1, 1, RoofFit.ALONG_Y, true, 1.1f, 1.2f)), map.painted);
        map.build.roofBuilder.addRoof(new SpriteGeometry.Mesh(), map.painted, 0, 0, 2);
        check(boundsOf(TestMap.plain())[2] < -0.15f, "the slope over a lean-to that stops short of its eave has its eave out past the wall: y from "
                + boundsOf(TestMap.plain())[2]);
        // The same slope with the roof below coming up to its foot: the slope goes on, and has no eave there.
        map = slopeAlongY(2, 2f, 2.32f);
        map.roofTile(fitted(new RoofFit.Tile(0, -1, 1, RoofFit.ALONG_Y, true, 2f - THIRD, 2f)), map.painted);
        map.build.roofBuilder.addRoof(new SpriteGeometry.Mesh(), map.painted, 0, 0, 2);
        check(boundsOf(TestMap.plain())[2] > -0.05f, "a slope that goes on down into the level below has no eave at its foot: y from "
                + boundsOf(TestMap.plain())[2]);
    }

    private static RoofFit.Tile fitted(RoofFit.Tile tile) {
        RoofFit.fit(new ArrayList<>(List.of(tile)));
        return tile;
    }

    private static void trimPastAGableIsNoRoofGoingOn() {
        TestMap map = slopeAlongY(1, 1f, 1.32f);
        trim(map, 1, 0, 1, "roofs_accents_01_11", "attachedW");
        map.build.roofBuilder.addRoof(new SpriteGeometry.Mesh(), map.painted, 0, 0, 1);
        check(boundsOf(TestMap.plain())[1] > 1.1f, "a slope with only a trim past its east end has its rake out over the gable: x to " + boundsOf(TestMap.plain())[1]);
        map = slopeAlongY(1, 1f, 1.32f);
        map.roofTile(fitted(new RoofFit.Tile(1, 0, 1, RoofFit.ALONG_Y, true, 1f, 1.32f)), map.painted);
        map.build.roofBuilder.addRoof(new SpriteGeometry.Mesh(), map.painted, 0, 0, 1);
        check(boundsOf(TestMap.plain())[1] < 1.05f, "a slope the roof goes on past has no rake there: x to " + boundsOf(TestMap.plain())[1]);
    }

    private static void trimPastAHipsCornerLeavesTheCornersEave() {
        TestMap map = hippedCorner(1f + THIRD / 2f, false);
        trim(map, -1, 0, 1, "roofs_accents_01_16");
        map.build.roofBuilder.addRoof(new SpriteGeometry.Mesh(), map.painted, 0, 0, 1);
        float[] box = boundsOf(TestMap.plain());
        check(TestMap.plain().triangles() > 0 && box[0] < 0f, "a hip's corner with a trim on the square past its west end still has its eave, run on past that end: x from " + box[0]);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-3f;
    }
}

package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import zombie.core.textures.Texture;
import zombie.iso.SpriteDetails.IsoObjectType;

/** Tests of a roof's shape where its slopes meet: hips, valleys, ridges and their caps. */
final class RoofTests {
    private static final float THIRD = RoofFit.PITCH, EAVE = 1f + THIRD;

    private RoofTests() {}

    static void run() {
        Checks.run("planeAndFoldAreToldApartByTheMiddle", RoofTests::planeAndFoldAreToldApartByTheMiddle);
        Checks.run("gambrelsTopPaintedForwardStandsOverItsLoft", RoofTests::gambrelsTopPaintedForwardStandsOverItsLoft);
        Checks.run("canopyPaintedForwardStandsOverABaysFlatRoof", RoofTests::canopyPaintedForwardStandsOverABaysFlatRoof);
        Checks.run("slopePaintedTwiceIsDroppedNotLaidOut", RoofTests::slopePaintedTwiceIsDroppedNotLaidOut);
        Checks.run("slopeKeepsItsPitchAboveTheKickAtItsEave", RoofTests::slopeKeepsItsPitchAboveTheKickAtItsEave);
        Checks.run("farSideOfAWingMeetsTheMainRoofInAValley", RoofTests::farSideOfAWingMeetsTheMainRoofInAValley);
        Checks.run("madeFarSidesOfAWingAndItsMainRoofMeetInAValley", RoofTests::madeFarSidesOfAWingAndItsMainRoofMeetInAValley);
        Checks.run("gablesFarSideComesDownToTheCrownOfALowerWingBesideIt", RoofTests::gablesFarSideComesDownToTheCrownOfALowerWingBesideIt);
        Checks.run("wingPastAGableFromTheRidgesSquareIsNoNotch", RoofTests::wingPastAGableFromTheRidgesSquareIsNoNotch);
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
        Checks.run("farSlopeComesDownOverALowerCrown", RoofTests::farSlopeComesDownOverALowerCrown);
        Checks.run("farSlopeStopsAtALowerPartsFlatRoof", RoofTests::farSlopeStopsAtALowerPartsFlatRoof);
        Checks.run("porchRoofIsNoPartOfTheSlopeAboveIt", RoofTests::porchRoofIsNoPartOfTheSlopeAboveIt);
        Checks.run("gableWiderThanACrownKeepsItsFarSideToTheWall", RoofTests::gableWiderThanACrownKeepsItsFarSideToTheWall);
        Checks.run("crownWiderThanAGableGetsNoWedge", RoofTests::crownWiderThanAGableGetsNoWedge);
        Checks.run("levelSheetPaintedOverItsFloorIsAFlatTop", RoofTests::levelSheetPaintedOverItsFloorIsAFlatTop);
        Checks.run("sheetsBehindARidgeAreNoPartOfTheRoof", RoofTests::sheetsBehindARidgeAreNoPartOfTheRoof);
        Checks.run("canopyIsNotCarriedToTheFarWall", RoofTests::canopyIsNotCarriedToTheFarWall);
        Checks.run("gambrelsFarLowerSlopeComesDownFromItsUpperSlope", RoofTests::gambrelsFarLowerSlopeComesDownFromItsUpperSlope);
        Checks.run("farSlopeMeetsAStepInItsWallInAValley", RoofTests::farSlopeMeetsAStepInItsWallInAValley);
        Checks.run("farSlopeRunsOnDownOverAWingPastAShallowerPart", RoofTests::farSlopeRunsOnDownOverAWingPastAShallowerPart);
        Checks.run("farSideOfRowsOverALongCrownStandsOnTheWall", RoofTests::farSideOfRowsOverALongCrownStandsOnTheWall);
        Checks.run("rowAtTheEndOfAHipHasNoFarSide", RoofTests::rowAtTheEndOfAHipHasNoFarSide);
        Checks.run("hipRoofWithCutHipsGetsItsFarFacesAsAHip", RoofTests::hipRoofWithCutHipsGetsItsFarFacesAsAHip);
        Checks.run("roofHippedAtOneEndAndGabledAtTheOtherGetsItsFarFaces", RoofTests::roofHippedAtOneEndAndGabledAtTheOtherGetsItsFarFaces);
        Checks.run("wingsSideBySideUnderACrossRidgeLeaveNoPit", RoofTests::wingsSideBySideUnderACrossRidgeLeaveNoPit);
        Checks.run("dormerOnASlopeGetsItsRidgeAndItsFarFace", RoofTests::dormerOnASlopeGetsItsRidgeAndItsFarFace);
        Checks.run("roomSquareFlooredWithARoofSheetHasNoCeiling", RoofTests::roomSquareFlooredWithARoofSheetHasNoCeiling);
        Checks.run("wingsFarSideStopsAtAMainRoofDeeperThanItsNearSide", RoofTests::wingsFarSideStopsAtAMainRoofDeeperThanItsNearSide);
        Checks.run("slopeThatClimbsPastItsCrownLiesLevel", RoofTests::slopeThatClimbsPastItsCrownLiesLevel);
        Checks.run("gablesSlopeThatClimbsPastACrownStaysASlope", RoofTests::gablesSlopeThatClimbsPastACrownStaysASlope);
        Checks.run("farSideOfARowOverADormerStandsOnTheWall", RoofTests::farSideOfARowOverADormerStandsOnTheWall);
        Checks.run("farSideOfARowOverABayStandsOnTheWall", RoofTests::farSideOfARowOverABayStandsOnTheWall);
        Checks.run("farSideClimbsFromAShallowerSlopeToTheWallOnItsRidge", RoofTests::farSideClimbsFromAShallowerSlopeToTheWallOnItsRidge);
        Checks.run("leanToAgainstAWallHasNoFarSide", RoofTests::leanToAgainstAWallHasNoFarSide);
        Checks.run("roomWithNoFloorUnderTheRoofIsNoStorey", RoofTests::roomWithNoFloorUnderTheRoofIsNoStorey);
        Checks.run("roomTheSlopePassesWhollyOverIsNoStoreyItStopsAt", RoofTests::roomTheSlopePassesWhollyOverIsNoStoreyItStopsAt);
        Checks.run("farSideStandsOnTheStoreyItCoversThoughItsNearSideRunsOnDownOverAPorch", RoofTests::farSideStandsOnTheStoreyItCoversThoughItsNearSideRunsOnDownOverAPorch);
        Checks.run("farSideComesOnDownOverAnOutshutPastTheStoreyItClears", RoofTests::farSideComesOnDownOverAnOutshutPastTheStoreyItClears);
        Checks.run("farSideStopsAtAFlatRoofItComesDownTo", RoofTests::farSideStopsAtAFlatRoofItComesDownTo);
        Checks.run("endRowOfAWingFallsAsDeepAsTheRowStoppedBesideIt", RoofTests::endRowOfAWingFallsAsDeepAsTheRowStoppedBesideIt);
        Checks.run("farSideStopsAtItsEaveBesideAWingsFlatRoof", RoofTests::farSideStopsAtItsEaveBesideAWingsFlatRoof);
        Checks.run("eaveOverALeanToThatStopsShortOfItIsMade", RoofTests::eaveOverALeanToThatStopsShortOfItIsMade);
        Checks.run("trimPastAGableIsNoRoofGoingOn", RoofTests::trimPastAGableIsNoRoofGoingOn);
        Checks.run("trimPastAHipsCornerLeavesTheCornersEave", RoofTests::trimPastAHipsCornerLeavesTheCornersEave);
        Checks.run("gutterTrimGoesOnlyUnderAnEaveMadeOnItsOwnEdge", RoofTests::gutterTrimGoesOnlyUnderAnEaveMadeOnItsOwnEdge);
        Checks.run("anEavesEndCapIsACornerPiece", RoofTests::anEavesEndCapIsACornerPiece);
        Checks.run("roofSheetLaidAsAFloorAtTheFootOfAFarSlopeIsNoTerrace", RoofTests::roofSheetLaidAsAFloorAtTheFootOfAFarSlopeIsNoTerrace);
        Checks.run("lowerWingPastAGableIsNoNotchInTheMainEave", RoofTests::lowerWingPastAGableIsNoNotchInTheMainEave);
        Checks.run("dormerOnAMadeFaceMeetsItInValleys", RoofTests::dormerOnAMadeFaceMeetsItInValleys);
        Checks.run("dormerAtTheEaveMeetsAMadeFaceInAValley", RoofTests::dormerAtTheEaveMeetsAMadeFaceInAValley);
        Checks.run("oneRowWingIsNotCarriedOverItsRidgeSquare", RoofTests::oneRowWingIsNotCarriedOverItsRidgeSquare);
        Checks.run("gabledCanopyHasItsFarSideOverOpenGround", RoofTests::gabledCanopyHasItsFarSideOverOpenGround);
        Checks.run("endRowMeetsAWingAtItsFootInAValley", RoofTests::endRowMeetsAWingAtItsFootInAValley);
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

    private static void slopeKeepsItsPitchAboveTheKickAtItsEave() {
        // As the map has a porch roof's tiles read: roofs_05_4 falling east, then 97 and 96 at a shallower pitch.
        RoofFit.Tile steep = new RoofFit.Tile(0, 0, 1, RoofFit.ALONG_X, true, 1.677f, 1.328f);
        RoofFit.Tile kick = new RoofFit.Tile(1, 0, 1, RoofFit.ALONG_X, true, 1.349f, 1.240f);
        RoofFit.Tile eave = new RoofFit.Tile(2, 0, 1, RoofFit.ALONG_X, true, 1.266f, 1.156f);
        RoofFit.fit(new ArrayList<>(List.of(steep, kick, eave)));
        check(Math.abs(steep.fitLow - steep.fitHigh - THIRD) < 0.05f, "the steep tile keeps a whole pitch: " + steep.fitLow + ".." + steep.fitHigh);
        check(kick.fitted && eave.fitted && near(kick.fitHigh, eave.fitLow), "the kick's two tiles are one plane: " + kick.fitHigh + " " + eave.fitLow);
    }

    private static void farSideOfAWingMeetsTheMainRoofInAValley() {
        // As the map has the junction of a farmhouse's gambrel (1468.., 9850..): the main roof's level top
        // on column 0 and its east slope on 1..3, the wing's south slope on rows 3..5, a square at 3, 2
        // with no tile and a stray slope at 4, 1..2 where the wing's far side meets the main roof.
        List<RoofFit.Tile> tiles = new ArrayList<>();
        for (int y = 0; y <= 6; y++) {
            tiles.add(new RoofFit.Tile(0, y, 3, RoofFit.LEVEL, true, 3f, 3f));
            tiles.add(new RoofFit.Tile(1, y, 2, y == 3 ? RoofFit.SOUTH_EAST_ODD : RoofFit.ALONG_X, true, 3f, 3f - THIRD));
            if (y != 3) {
                tiles.add(new RoofFit.Tile(2, y, 2, y == 4 ? RoofFit.SOUTH_EAST_ODD : RoofFit.ALONG_X, true, 3f - THIRD, 2f + THIRD));
            }
            if (y <= 1 || y == 6) {
                tiles.add(new RoofFit.Tile(3, y, 2, RoofFit.ALONG_X, true, 2f + THIRD, 2f));
            }
        }
        tiles.add(new RoofFit.Tile(2, 3, 2, RoofFit.ALONG_Y, true, 3f, 3f - THIRD));
        tiles.add(new RoofFit.Tile(3, 3, 2, RoofFit.ALONG_Y, true, 3f, 3f - THIRD));
        tiles.add(new RoofFit.Tile(3, 4, 2, RoofFit.ALONG_Y, true, 3f - THIRD, 2f + THIRD));
        tiles.add(new RoofFit.Tile(3, 5, 2, RoofFit.SOUTH_EAST_ODD, true, 2f + THIRD, 2f));
        tiles.add(new RoofFit.Tile(4, 1, 2, RoofFit.ALONG_X, true, 3f - THIRD, 2f + THIRD));
        tiles.add(new RoofFit.Tile(4, 2, 2, RoofFit.ALONG_X, true, 3f - THIRD, 2f + THIRD));
        for (int x = 4; x <= 8; x++) {
            for (int y = 3; y <= 5; y++) {
                tiles.add(new RoofFit.Tile(x, y, 2, RoofFit.ALONG_Y, true, 3f - (y - 3) * THIRD, 3f - (y - 2) * THIRD));
            }
        }
        RoofFit.fit(tiles);
        List<RoofFit.Tile> made = RoofFit.backs(tiles, standing((x, y) -> x >= -3 && x <= 3 && y >= 0 && y <= 6 || x >= 4 && x <= 8 && y >= 0 && y <= 5));
        RoofFit.peaks(tiles, made);
        List<RoofFit.Tile> carried = RoofFit.valleys(tiles, made);
        List<RoofFit.Tile> farSide = new ArrayList<>(made);
        farSide.addAll(carried);
        for (RoofFit.Tile tile : farSide) {
            if (tile.y == 0 && tile.x >= 3) {
                check(near(Math.min(tile.fitLow, tile.fitHigh), 2f), "the wing's far side comes down to its eave on the north wall, not a level over it: " + tile.x + " at " + tile.fitLow + ".." + tile.fitHigh);
            }
        }
        // The valley runs from the wing's eave at the main roof's foot, 4, 0, to its ridge at the main roof's top, 1, 3.
        for (int x = 1; x <= 4; x++) {
            for (int y = 0; y <= 2; y++) {
                if (x + y < 3) {
                    continue;
                }
                boolean found = false;
                for (RoofFit.Tile tile : farSide) {
                    found |= tile.x == x && tile.y == y && tile.fitAxis == RoofFit.ALONG_Y && near(tile.fitLow, 2f + y * THIRD) && near(tile.fitHigh, 2f + (y + 1) * THIRD);
                }
                RoofFit.Tile stray = null;
                for (RoofFit.Tile tile : tiles) {
                    stray = tile.x == x && tile.y == y && tile.z == 2 && tile.shaped && tile.own == 0 ? tile : stray;
                }
                found |= stray != null && near(stray.corners[0], 2f + y * THIRD) && near(stray.corners[1], 2f + y * THIRD) && near(stray.corners[2], 2f + (y + 1) * THIRD) && near(stray.corners[3], 2f + (y + 1) * THIRD);
                check(found, "the wing's far side covers " + x + ", " + y + " on its side of the valley, with no pit at the square the map left out or over the stray slope");
            }
        }
        for (RoofFit.Tile tile : tiles) {
            boolean stray = tile.x == 4 && tile.y == 1;
            if (tile.z == 2 && tile.x <= 4 && tile.y <= 2 && tile.axis == RoofFit.ALONG_X) {
                check(stray == (tile.shaped && tile.own == 0), stray ? "the stray slope whose head stands out of the valley is folded onto the wing's far side, none of its sprite kept"
                        : "a slope of the main roof, whose head meets the slope or the level top beyond it, keeps its sprite: " + tile.x + ", " + tile.y);
            }
        }
    }

    private static void madeFarSidesOfAWingAndItsMainRoofMeetInAValley() {
        // As the map has the church at 3532..3549, 11199..11211: the main roof's south slope on rows
        // 8..11 under a ridge at 2 1/3, and a wing to the north on 4..9 whose east slope, on 7..9, runs
        // into the main roof's far side. Neither the main roof's north slope nor the wing's west one is
        // on the map, and they meet over 4..6, 4..7.
        List<RoofFit.Tile> tiles = new ArrayList<>();
        for (int x = 0; x <= 9; x++) {
            tiles.add(new RoofFit.Tile(x, 8, 2, RoofFit.ALONG_Y, true, 2f + THIRD, 2f));
            tiles.add(new RoofFit.Tile(x, 9, 1, RoofFit.ALONG_Y, true, 2f, 2f - THIRD));
            tiles.add(new RoofFit.Tile(x, 10, 1, RoofFit.ALONG_Y, true, 2f - THIRD, EAVE));
            tiles.add(new RoofFit.Tile(x, 11, 1, RoofFit.ALONG_Y, true, EAVE, 1f));
        }
        for (int y = 0; y <= 6; y++) {
            for (int x = 7; x <= 9 - Math.max(0, y - 4); x++) {
                tiles.add(new RoofFit.Tile(x, y, 1, RoofFit.ALONG_X, true, 2f - (x - 7) * THIRD, 2f - (x - 6) * THIRD));
            }
        }
        RoofFit.fit(tiles);
        List<RoofFit.Tile> made = RoofFit.backs(tiles, standing((x, y) -> x >= 0 && x <= 9 && y >= 4 && y <= 11 || x >= 4 && x <= 9 && y >= 0 && y <= 3));
        RoofFit.peaks(tiles, made);
        made.addAll(RoofFit.valleys(tiles, made));
        boolean[][] covered = new boolean[7][8];
        for (RoofFit.Tile tile : made) {
            if (tile.x > 6 || tile.y > 7) {
                continue;
            }
            check(!covered[tile.x][tile.y], "one made tile over " + tile.x + ", " + tile.y);
            covered[tile.x][tile.y] = true;
            for (int corner = 0; corner < 4; corner++) {
                float x = tile.x + RoofFit.cornerX(corner), y = tile.y + RoofFit.cornerY(corner);
                // The higher of the main roof's far side and the wing's: each falls from its ridge to the eave, and they meet in a valley.
                float roof = Math.max(1f + Math.max(0f, y - 4f) * THIRD, 1f + Math.clamp(x - 4f, 0f, 3f) * THIRD);
                check(near(tile.corners[corner], roof), "the far sides meet in a valley at " + tile.x + ", " + tile.y + " corner " + corner + ": " + tile.corners[corner] + ", not " + roof
                        + ", where each was carried to the other's end wall and stood a level top over it");
            }
        }
        for (int x = 0; x <= 6; x++) {
            for (int y = 0; y <= 7; y++) {
                check(covered[x][y] == (y >= 4 || x >= 4), "the far sides cover the building and nothing else at " + x + ", " + y);
            }
        }
    }

    private static void gablesFarSideComesDownToTheCrownOfALowerWingBesideIt() {
        // As the map has the house at 2447..2456, 6500..6508, read as one roof across the gap: a
        // gable on 4..8 whose east slope, on 7..8, climbs to a cap at 1 2/3 on 6, and a lower wing on
        // 0..5 whose flat crown at 1 1/3 on 1..4 has its south eave on row 7. Column 5 has no tile.
        List<RoofFit.Tile> tiles = new ArrayList<>();
        for (int x = 1; x <= 4; x++) {
            for (int y = 3; y <= 6; y++) {
                RoofFit.Tile crown = new RoofFit.Tile(x, y, 1, RoofFit.LEVEL, true, EAVE, EAVE);
                crown.sheet = true;
                tiles.add(crown);
            }
            tiles.add(new RoofFit.Tile(x, 7, 1, RoofFit.ALONG_Y, true, EAVE, 1f));
        }
        for (int y = 2; y <= 7; y++) {
            RoofFit.Tile cap = new RoofFit.Tile(6, y, 1, RoofFit.LEVEL, true, EAVE + THIRD, EAVE + THIRD);
            cap.fitting = true;
            tiles.add(cap);
            tiles.add(new RoofFit.Tile(7, y, 1, RoofFit.ALONG_X, true, EAVE + THIRD, EAVE));
            tiles.add(new RoofFit.Tile(8, y, 1, RoofFit.ALONG_X, true, EAVE, 1f));
        }
        RoofFit.fit(tiles);
        List<RoofFit.Tile> made = RoofFit.backs(tiles, standing((x, y) -> x >= 0 && x <= 8 && y >= 2 && y <= 7));
        RoofFit.peaks(tiles, made);
        made.addAll(RoofFit.valleys(tiles, made));
        for (int y = 3; y <= 7; y++) {
            RoofFit.Tile gap = madeAt(made, 5, y);
            check(gap != null && gap.fitAxis == RoofFit.ALONG_X && near(gap.fitLow, EAVE) && near(gap.fitHigh, EAVE + THIRD),
                    "the gable's far side climbs from the wing's crown to the cap over the gap on row " + y + ", where the wing made an eave under it");
        }
        for (RoofFit.Tile tile : made) {
            check(tile.x <= 5, "nothing is made on the gable's own slopes or past its east wall: " + tile.x + ", " + tile.y);
            check(tile.x != 0 || tile.fitAxis == RoofFit.ALONG_X || tile.shaped, "the wing's west side is an eave: " + tile.x + ", " + tile.y);
        }
    }

    private static void wingPastAGableFromTheRidgesSquareIsNoNotch() {
        // As the map has the farmhouse at 2863..2870, 10395..10404: the east slope on 4..7 from a ridge
        // at 3 1/3 down to the eave at 2, rows 0..8, and past the south gable a wing on 4..5 of row 9
        // with a low roof of its own. The far side is 0..3; row 9 west of the wing stands on no storey.
        List<RoofFit.Tile> tiles = new ArrayList<>();
        for (int y = 0; y <= 8; y++) {
            tiles.add(new RoofFit.Tile(4, y, 3, RoofFit.ALONG_X, true, 3f + THIRD, 3f));
            for (int x = 5; x <= 7; x++) {
                tiles.add(new RoofFit.Tile(x, y, 2, RoofFit.ALONG_X, true, 3f - (x - 5) * THIRD, 3f - (x - 4) * THIRD));
            }
        }
        tiles.add(new RoofFit.Tile(5, 9, 2, RoofFit.ALONG_X, true, 2f + THIRD, 2f));
        RoofFit.fit(tiles);
        List<RoofFit.Tile> made = RoofFit.backs(tiles, standing((x, y) -> x >= 0 && x <= 7 && y >= 0 && y <= 8 || x >= 4 && x <= 5 && y == 9));
        RoofFit.peaks(tiles, made);
        for (int y = 0; y <= 8; y++) {
            for (int x = 0; x <= 3; x++) {
                RoofFit.Tile tile = madeAt(made, x, y);
                float low = 2f + x * THIRD, high = low + THIRD;
                check(tile != null && near(tile.corners[0], low) && near(tile.corners[1], high) && near(tile.corners[2], high) && near(tile.corners[3], low),
                        "the far side climbs straight to the ridge at " + x + ", " + y + ", where the wing past the gable brought it down to the gable as a hip: "
                                + (tile == null ? "none" : tile.corners[0] + " " + tile.corners[1] + " " + tile.corners[2] + " " + tile.corners[3]));
            }
        }
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

    /**
     * The house at 869..883 x 12514..12526: its south slope, ridged at 3 on the north edge of row 4 here, runs on down in one plane
     * over a porch on level 1 to an eave at 1, while the rooms of level 1 go on north of the ridge to y 0. The map has the south slope only.
     */
    private static void farSideStandsOnTheStoreyItCoversThoughItsNearSideRunsOnDownOverAPorch() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 3; x++) {
            for (int y = 4; y <= 9; y++) {
                float north = 3f - (y - 4) * THIRD;
                roof.add(new RoofFit.Tile(x, y, (int) Math.floor(north - THIRD + 0.05f), RoofFit.ALONG_Y, true, north, north - THIRD));
            }
        }
        roof.sort(Comparator.comparingLong(tile -> RoofFit.key(tile.x, tile.y, tile.z)));
        RoofFit.fit(roof);
        RoofFit.Site house = new RoofFit.Site() {
            @Override
            public boolean stands(int x, int y, int z) {
                return x >= 0 && x <= 3 && y >= 0 && y <= (z <= 1 ? 9 : 6);
            }

            @Override
            public boolean floored(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean storey(int x, int y, int z) {
                return x >= 0 && x <= 3 && y >= 0 && y <= 6 && z == 1;
            }

            @Override
            public boolean taken(int x, int y, int z, float over) {
                return storey(x, y, z) && over < z + 1 - 0.01f;
            }
        };
        HashMap<Long, RoofFit.Tile> over = new HashMap<>();
        for (RoofFit.Tile tile : RoofFit.backs(roof, house)) {
            over.put(RoofFit.key(tile.x, tile.y, 0), tile);
        }
        for (int x = 0; x <= 3; x++) {
            for (int y = 0; y <= 2; y++) {
                RoofFit.Tile tile = over.get(RoofFit.key(x, y, 0));
                check(tile != null && !tile.shaped && tile.fitAxis == RoofFit.ALONG_Y && near(tile.fitLow, 2f + y * THIRD) && near(tile.fitHigh, 2f + (y + 1) * THIRD),
                        "the far side climbs from the north wall at the top of the storey, 2, to the ridge, at " + x + ", " + y + ": " + said(tile));
            }
            RoofFit.Tile top = over.get(RoofFit.key(x, 3, 0));
            check(top != null && near(top.fitLow, 3f) && near(top.fitHigh, 3f), "and lies level at the ridge's height beside it, at " + x + ", 3: " + said(top));
        }
    }

    /**
     * The stone house at 1657..1678 x 9052..9062: its south slope climbs from 2 to a ridge at 3 + 2/3 on the north edge of row 5
     * here, over an upper storey on level 2 that ends at a wall on the north edge of row 3, past which a single storey outshut
     * goes on to y 0. The far side clears the upper storey, a level over it where its wall stands, and comes on down over the
     * outshut to its wall at 2. Stood on the upper storey, it lay level at 3 over the outshut. An upper storey a square deeper
     * is under the plane there, and the far side stands on it as before; so it does where a storey over one it clears reaches
     * further out.
     */
    private static void farSideComesOnDownOverAnOutshutPastTheStoreyItClears() {
        HashMap<Long, RoofFit.Tile> over = farSideOverStoreys(5, (y, z) -> z == 1 || z == 2 && y >= 3);
        for (int x = 0; x <= 3; x++) {
            for (int y = 0; y <= 4; y++) {
                RoofFit.Tile tile = over.get(RoofFit.key(x, y, 0));
                check(tile != null && !tile.shaped && tile.fitAxis == RoofFit.ALONG_Y && near(tile.fitLow, 2f + y * THIRD) && near(tile.fitHigh, 2f + (y + 1) * THIRD),
                        "the far side climbs from the outshut's wall at 2 over the upper storey's wall to the ridge, at " + x + ", " + y + ": " + said(tile));
            }
        }
        over = farSideOverStoreys(5, (y, z) -> z == 1 || z == 2 && y >= 2);
        farSideStandsOnTheStorey(over, 3f, 5, "a storey a square deeper, under the plane at its wall");
        over = farSideOverStoreys(8, (y, z) -> z == 1 || z == 2 && y >= 3 || z == 3);
        farSideStandsOnTheStorey(over, 4f, 8, "a storey reaching further out over one cleared");
    }

    /**
     * The house at 6088..6106 x 8050..8061: its slope climbs three rows from 1 to a ridge at 2 on the north edge of row 4 here,
     * and past where its far side comes down to 1, at the north edge of row 1, the map lays a flat roof of sheets as floors over
     * y -4..0, further than the slope climbs. Read as the slope's foot, the flat had the far side carried on over it to the far
     * wall, level at the ridge. Sheets over y -2..0, no deeper than the climb, are the far side's own foot, as the porch house's
     * at 869..876, 12515..12517, and the far side comes on down over them to the wall.
     */
    private static void farSideStopsAtAFlatRoofItComesDownTo() {
        HashMap<Long, RoofFit.Tile> over = farSideBesideSheets(5);
        for (int x = 0; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                RoofFit.Tile tile = over.get(RoofFit.key(x, y, 0));
                check(tile != null && !tile.shaped && tile.fitAxis == RoofFit.ALONG_Y && near(tile.fitLow, 1f + (y - 1) * THIRD) && near(tile.fitHigh, 1f + y * THIRD),
                        "the far side falls from the ridge to the flat roof at 1, at " + x + ", " + y + ": " + said(tile));
            }
            for (int y = -4; y <= 0; y++) {
                RoofFit.Tile tile = over.get(RoofFit.key(x, y, 0));
                check(tile == null, "and is not carried on over the flat, at " + x + ", " + y + ": " + said(tile));
            }
        }
        over = farSideBesideSheets(3);
        for (int x = 0; x <= 3; x++) {
            RoofFit.Tile tile = over.get(RoofFit.key(x, -2, 0));
            check(tile != null && near(tile.fitLow, 1f), "over sheets no deeper than the climb the far side comes on down to the wall, at " + x + ", -2: " + said(tile));
        }
    }

    /** The far sides of that house standing over y (1 - deep)..6, with sheets laid as floors over y (1 - deep)..0. */
    private static HashMap<Long, RoofFit.Tile> farSideBesideSheets(int deep) {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 3; x++) {
            for (int y = 4; y <= 6; y++) {
                float north = 2f - (y - 4) * THIRD;
                roof.add(new RoofFit.Tile(x, y, (int) Math.floor(north - THIRD + 0.05f), RoofFit.ALONG_Y, true, north, north - THIRD));
            }
        }
        roof.sort(Comparator.comparingLong(tile -> RoofFit.key(tile.x, tile.y, tile.z)));
        RoofFit.fit(roof);
        RoofFit.Site house = new RoofFit.Site() {
            @Override
            public boolean stands(int x, int y, int z) {
                return x >= 0 && x <= 3 && y >= 1 - deep && y <= 6;
            }

            @Override
            public boolean floored(int x, int y, int z) {
                return false;
            }

            @Override
            public int sheets(int x, int y, int dx, int dy, int z) {
                int run = 0;
                while (z == 1 && stands(x + run * dx, y + run * dy, z) && y + run * dy <= 0) {
                    run++;
                }
                return run;
            }

            @Override
            public boolean taken(int x, int y, int z, float over) {
                return false;
            }
        };
        HashMap<Long, RoofFit.Tile> over = new HashMap<>();
        for (RoofFit.Tile tile : RoofFit.backs(roof, house)) {
            over.put(RoofFit.key(tile.x, tile.y, 0), tile);
        }
        return over;
    }

    /**
     * The house at 12352..12368 x 3201..3214: a wing's slope climbs three squares west from 1 to a ridge at 2 on the west edge of
     * x 8 here, over y 0..4, and its far side comes down over a lower part of the house at x 0..7, whose roof the rows y 1..4
     * meet at x 5. The end row y 0 has none of that part's roof on the map, the part's own far side comes out over it, and carried
     * on to the far wall at x 0 it lay at the ridge's height along the whole north edge. Where the building goes on north of the
     * row, it is no end of the ridge, and is left to run on to the far wall.
     */
    private static void endRowOfAWingFallsAsDeepAsTheRowStoppedBesideIt() {
        HashMap<Long, RoofFit.Tile> over = wingOverALowerPart(0);
        for (int x = 5; x <= 7; x++) {
            RoofFit.Tile tile = over.get(RoofFit.key(x, 0, 0));
            check(tile != null && !tile.shaped && tile.fitAxis == RoofFit.ALONG_X && near(tile.fitLow, 1f + (x - 5) * THIRD) && near(tile.fitHigh, 1f + (x - 4) * THIRD),
                    "the end row falls from the ridge as deep as the near side, to 1, at " + x + ", 0: " + said(tile));
        }
        for (int x = 0; x <= 4; x++) {
            RoofFit.Tile tile = over.get(RoofFit.key(x, 0, 0));
            check(tile == null, "and is not carried on past it to the far wall, at " + x + ", 0: " + said(tile));
        }
        for (int y = 1; y <= 4; y++) {
            RoofFit.Tile tile = over.get(RoofFit.key(6, y, 0));
            check(tile != null && near(tile.fitLow, 4f / 3f) && near(tile.fitHigh, 5f / 3f), "the rows beside it meet the lower part as before, at 6, " + y + ": " + said(tile));
        }
        over = wingOverALowerPart(-1);
        RoofFit.Tile wall = over.get(RoofFit.key(0, 0, 0));
        check(wall != null && near(wall.fitLow, 1f), "with the building going on north of it the row runs on to the far wall at 0, 0: " + said(wall));
    }

    /**
     * The house at 2763..2771 x 14534..14541: a slope climbs three squares west from 1 to a ridge at 2 on the west edge of x 6
     * here, over y 0..7, and its far side falls over x 3..5 to the west wall of y 0..3. The rows y 4..7 have a wing on to x 0,
     * roofed flat with sheets at x 1..2 on the floor of the slope's level. Carried on over those to the wing's wall, the far
     * side of y 4..7 lay at its ridge's height over x 3..5, and the gable stood bare in front of it. Where the house goes on as
     * far at y 0..3, the far side is carried on over the sheets as the map has it there (the porch house, 869..876, 12515..12517).
     */
    private static void farSideStopsAtItsEaveBesideAWingsFlatRoof() {
        HashMap<Long, RoofFit.Tile> over = farSideBesideAWing(3);
        for (int y = 0; y <= 7; y++) {
            for (int x = 3; x <= 5; x++) {
                RoofFit.Tile tile = over.get(RoofFit.key(x, y, 0));
                check(tile != null && !tile.shaped && tile.fitAxis == RoofFit.ALONG_X && near(tile.fitLow, 1f + (x - 3) * THIRD) && near(tile.fitHigh, 1f + (x - 2) * THIRD),
                        "the far side falls from the ridge to 1 at the edge of y 0..3, at " + x + ", " + y + ": " + said(tile));
            }
            for (int x = 0; x <= 2; x++) {
                RoofFit.Tile tile = over.get(RoofFit.key(x, y, 0));
                check(tile == null, "and is not carried on over the wing, at " + x + ", " + y + ": " + said(tile));
            }
        }
        over = farSideBesideAWing(0);
        for (int y = 4; y <= 7; y++) {
            RoofFit.Tile tile = over.get(RoofFit.key(0, y, 0));
            check(tile != null && near(tile.fitLow, 1f), "beside rows that go on to the far wall too, it comes on down to the wing's wall, at 0, " + y + ": " + said(tile));
        }
    }

    /** The far sides of that house, whose rows y 0..3 stand from x west on. */
    private static HashMap<Long, RoofFit.Tile> farSideBesideAWing(int west) {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 6; x <= 8; x++) {
            for (int y = 0; y <= 7; y++) {
                float high = 2f - (x - 6) * THIRD;
                roof.add(new RoofFit.Tile(x, y, (int) Math.floor(high - THIRD + 0.05f), RoofFit.ALONG_X, true, high, high - THIRD));
            }
        }
        roof.sort(Comparator.comparingLong(tile -> RoofFit.key(tile.x, tile.y, tile.z)));
        RoofFit.fit(roof);
        RoofFit.Site house = new RoofFit.Site() {
            @Override
            public boolean stands(int x, int y, int z) {
                return x >= (y <= 3 ? west : 0) && x <= 8 && y >= 0 && y <= 7;
            }

            @Override
            public boolean floored(int x, int y, int z) {
                return false;
            }

            @Override
            public int sheets(int x, int y, int dx, int dy, int z) {
                int run = 0;
                while (z == 1 && y >= 4 && x + run * dx >= 1 && x + run * dx <= 2) {
                    run++;
                }
                return run;
            }

            @Override
            public boolean taken(int x, int y, int z, float over) {
                return false;
            }
        };
        HashMap<Long, RoofFit.Tile> over = new HashMap<>();
        for (RoofFit.Tile tile : RoofFit.backs(roof, house)) {
            over.put(RoofFit.key(tile.x, tile.y, 0), tile);
        }
        return over;
    }

    /** The wing over y 0..4 of a house standing over x 0..10 and y north..4, with the far sides made. */
    private static HashMap<Long, RoofFit.Tile> wingOverALowerPart(int north) {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 8; x <= 10; x++) {
            for (int y = 0; y <= 4; y++) {
                float west = 2f - (x - 8) * THIRD;
                roof.add(new RoofFit.Tile(x, y, (int) Math.floor(west - THIRD + 0.05f), RoofFit.ALONG_X, true, west, west - THIRD));
            }
        }
        roof.sort(Comparator.comparingLong(tile -> RoofFit.key(tile.x, tile.y, tile.z)));
        RoofFit.fit(roof);
        RoofFit.Site house = new RoofFit.Site() {
            @Override
            public boolean stands(int x, int y, int z) {
                return x >= 0 && x <= 10 && y >= north && y <= 4;
            }

            @Override
            public boolean floored(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean taken(int x, int y, int z, float over) {
                return x <= 5 && y >= 1 && y <= 4;
            }
        };
        HashMap<Long, RoofFit.Tile> over = new HashMap<>();
        for (RoofFit.Tile tile : RoofFit.backs(roof, house)) {
            over.put(RoofFit.key(tile.x, tile.y, 0), tile);
        }
        return over;
    }

    /**
     * A house of x 0..3 and y 0..(rows + 4) whose south slope climbs rows deep from 2 to its ridge on the north edge of row 5, with
     * storeys where rooms holds of a row and level, and the far sides made over y 0..4.
     */
    private static HashMap<Long, RoofFit.Tile> farSideOverStoreys(int rows, java.util.function.BiPredicate<Integer, Integer> rooms) {
        int south = rows + 4;
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 3; x++) {
            for (int y = 5; y <= south; y++) {
                float north = 2f + rows * THIRD - (y - 5) * THIRD;
                roof.add(new RoofFit.Tile(x, y, (int) Math.floor(north - THIRD + 0.05f), RoofFit.ALONG_Y, true, north, north - THIRD));
            }
        }
        roof.sort(Comparator.comparingLong(tile -> RoofFit.key(tile.x, tile.y, tile.z)));
        RoofFit.fit(roof);
        RoofFit.Site house = new RoofFit.Site() {
            @Override
            public boolean stands(int x, int y, int z) {
                return x >= 0 && x <= 3 && y >= 0 && y <= south;
            }

            @Override
            public boolean floored(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean storey(int x, int y, int z) {
                return stands(x, y, z) && rooms.test(y, z);
            }

            @Override
            public boolean taken(int x, int y, int z, float over) {
                return storey(x, y, z) && over < z + 1 - 0.01f;
            }
        };
        HashMap<Long, RoofFit.Tile> over = new HashMap<>();
        for (RoofFit.Tile tile : RoofFit.backs(roof, house)) {
            over.put(RoofFit.key(tile.x, tile.y, 0), tile);
        }
        return over;
    }

    /** That the far side over y 0..4 climbs from the north wall at top, the top of the storey it stands on, to the ridge and lies level there. */
    private static void farSideStandsOnTheStorey(HashMap<Long, RoofFit.Tile> over, float top, int rows, String what) {
        float ridge = 2f + rows * THIRD;
        for (int x = 0; x <= 3; x++) {
            for (int y = 0; y <= 4; y++) {
                RoofFit.Tile tile = over.get(RoofFit.key(x, y, 0));
                float low = Math.min(top + y * THIRD, ridge), high = Math.min(top + (y + 1) * THIRD, ridge);
                check(tile != null && !tile.shaped && near(tile.fitLow, low) && near(tile.fitHigh, high),
                        "with " + what + " the far side stands on its top at " + top + ", at " + x + ", " + y + ": " + said(tile));
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
        // A canopy's eave row by its hip: the slope's tile over the first level tile reads unclearly and takes its plane from
        // the next along, which comes after the level tile under it (8276, 11287).
        List<RoofFit.Tile> canopy = new ArrayList<>();
        List<RoofFit.Tile> eave = new ArrayList<>();
        for (int x = 0; x <= 3; x++) {
            RoofFit.Tile level = new RoofFit.Tile(x, 0, 1, RoofFit.LEVEL, true, 1f, 1f);
            eave.add(level);
            canopy.add(level);
            canopy.add(new RoofFit.Tile(x, 1, 1, RoofFit.ALONG_Y, x > 0, 1f + sixth, 1f + 2f * sixth));
        }
        canopy.sort(Comparator.comparingLong(tile -> RoofFit.key(tile.x, tile.y, tile.z)));
        RoofFit.fit(canopy);
        check(eave.stream().allMatch(tile -> tile.fitAxis == RoofFit.ALONG_Y && near(tile.fitLow, 1f) && near(tile.fitHigh, 1f + sixth)),
                "the eave under a slope's tile that takes its plane from beside it is the slope's lowest row too");
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
        check(middle.z == 2 && RoofFit.levelOnItsFloor(middle), "on the floor of the level over the slopes, where its gable is a level down: z " + middle.z);
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

    private static void porchRoofIsNoPartOfTheSlopeAboveIt() {
        // A gable over rows 4..6 on the upper storey climbs north to a ridge at 2 along row 4's north edge, its eave at 1.
        // A porch roof a level down on row 7 lies under that eave and falls on south from two thirds of a level: no
        // continuation of the slope above. Taken as one, the far side was looked for on the ground floor's rooms, and
        // the house stood open behind its ridge.
        RoofFit.Site site = new RoofFit.Site() {
            @Override
            public boolean stands(int x, int y, int z) {
                return x >= 1 && x <= 3 && y >= 1 && y <= 7;
            }

            @Override
            public boolean floored(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean taken(int x, int y, int z, float roof) {
                return z == 0;
            }
        };
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 1; x <= 3; x++) {
            for (int row = 0; row < 3; row++) {
                roof.add(new RoofFit.Tile(x, 6 - row, 1, RoofFit.ALONG_Y, true, EAVE + row * THIRD, 1f + row * THIRD));
            }
            roof.add(new RoofFit.Tile(x, 7, 0, RoofFit.ALONG_Y, true, 2f * THIRD, THIRD));
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int x = 1; x <= 3; x++) {
            RoofFit.Tile wall = madeAt(made, x, 1);
            check(madeAt(made, x, 3) != null && madeAt(made, x, 2) != null && wall != null && wall.axis == RoofFit.ALONG_Y
                    && near(wall.low, 1f) && near(wall.high, EAVE), "the far side comes down to the north wall at x " + x);
        }
    }

    private static void farSlopeStopsAtALowerPartsFlatRoof() {
        // A gable over x 0..3, rows 3..5, climbs north to a ridge at 2. The building runs on north to row -3, but rows
        // -1..-3 are a lower part under a flat roof of the map's sheets, over another room than row 0's. The far side
        // comes down to the lower part's wall at row 0's north edge and nothing is made over its roof.
        RoofFit.Site site = new RoofFit.Site() {
            @Override
            public boolean stands(int x, int y, int z) {
                return x >= 0 && x <= 3 && y >= -3 && y <= 5;
            }

            @Override
            public boolean floored(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean taken(int x, int y, int z, float roof) {
                return false;
            }

            @Override
            public boolean annex(int fromX, int fromY, int x, int y, int z) {
                return y <= -1 && fromY >= 0;
            }
        };
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 3; x++) {
            for (int row = 0; row < 3; row++) {
                roof.add(new RoofFit.Tile(x, 5 - row, 1, RoofFit.ALONG_Y, true, EAVE + row * THIRD, 1f + row * THIRD));
            }
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int x = 0; x <= 3; x++) {
            RoofFit.Tile wall = madeAt(made, x, 0);
            check(wall != null && near(wall.corners[0], 1f) && near(wall.corners[1], 1f) && near(wall.corners[2], EAVE) && near(wall.corners[3], EAVE),
                    "the far side comes down to the lower part's wall at x " + x + ": " + (wall == null ? "none" : Arrays.toString(wall.corners)));
            for (int y = -3; y <= -1; y++) {
                check(madeAt(made, x, y) == null, "nothing is made over the lower part's roof at " + x + ", " + y);
            }
        }
    }

    private static void farSlopeComesDownOverALowerCrown() {
        farSlopeMeetsALowerCrown(1);
        farSlopeMeetsALowerCrown(2);
    }

    private static void farSlopeMeetsALowerCrown(int gap) {
        // A gable over x 0..3, rows 3 + gap .. 5 + gap, climbs north to a ridge at 2; north of it, past gap open rows,
        // the crown of a hip cut short lies a third of a level up over x 1..3, rows 0..2. The far side falls two rows to
        // the crown's height, over the crown where one row is open. The map has no slope on the crown's north and west
        // sides, and the building runs on a row north of it and a column west of it.
        int ridgeRow = 3 + gap;
        RoofFit.Site site = standing((x, y) -> x >= 0 && x <= 3 && y >= -1 && y <= ridgeRow + 2);
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 3; x++) {
            for (int row = 0; row < 3; row++) {
                roof.add(new RoofFit.Tile(x, ridgeRow + 2 - row, 1, RoofFit.ALONG_Y, true, EAVE + row * THIRD, 1f + row * THIRD));
            }
        }
        addCrown(roof, 1, 3, 0, 2);
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int x = 0; x <= 3; x++) {
            RoofFit.Tile first = madeAt(made, x, ridgeRow - 1), second = madeAt(made, x, ridgeRow - 2);
            check(first != null && first.axis == RoofFit.ALONG_Y && near(first.low, 1f + 2f * THIRD) && near(first.high, 2f),
                    "the far slope falls north from the ridge at x " + x + ", gap " + gap);
            check(second != null && second.axis == RoofFit.ALONG_Y && near(second.low, EAVE) && near(second.high, 1f + 2f * THIRD),
                    "and on down to the crown's height at x " + x + ", past the crown's end as beside it, gap " + gap);
        }
        for (int x = 1; x <= 3; x++) {
            RoofFit.Tile north = madeAt(made, x, -1);
            check(north != null && north.axis == RoofFit.ALONG_Y && near(north.low, 1f) && near(north.high, EAVE),
                    "the crown's north side climbs from the north wall to it at x " + x);
            check(madeAt(made, x, 0) == null && madeAt(made, x, 1) == null, "and nothing is made over the crown at x " + x + ", gap " + gap);
        }
        for (int y = 0; y <= 1; y++) {
            RoofFit.Tile west = madeAt(made, 0, y);
            check(west != null && west.axis == RoofFit.ALONG_X && near(west.low, 1f) && near(west.high, EAVE),
                    "the crown's west side climbs from the west wall to it in row " + y + ", gap " + gap);
        }
    }

    private static void gableWiderThanACrownKeepsItsFarSideToTheWall() {
        // The gable over x -5..3, rows 4..6, the crown over x 1..3 only, one open row between: past the crown's end the
        // building runs on north to row -1, and only the column next to the crown follows the crown's far side.
        RoofFit.Site site = standing((x, y) -> x >= -5 && x <= 3 && y >= -1 && y <= 6);
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = -5; x <= 3; x++) {
            for (int row = 0; row < 3; row++) {
                roof.add(new RoofFit.Tile(x, 6 - row, 1, RoofFit.ALONG_Y, true, EAVE + row * THIRD, 1f + row * THIRD));
            }
        }
        addCrown(roof, 1, 3, 0, 2);
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int x = -5; x <= 0; x++) {
            for (int y = -1; y <= 3; y++) {
                check(madeAt(made, x, y) != null, "the roof is whole past the crown's end at " + x + ", " + y);
            }
        }
        RoofFit.Tile far = madeAt(made, -2, 0);
        check(far != null && far.axis == RoofFit.ALONG_Y && near(far.low, EAVE) && near(far.high, 1f + 2f * THIRD),
                "two columns past the crown the far side climbs from the north wall: " + (far == null ? "none" : far.low + ".." + far.high));
    }

    private static void crownWiderThanAGableGetsNoWedge() {
        // The gable over x 0..3, rows 4..6; the crown over x 1..6, rows 0..2, with the map's south hip under it at x 4..6
        // on row 3. The gable's far side comes down over row 3 to the crown and stops there: nothing is carried along it
        // over the crown at x 4..6.
        RoofFit.Site site = standing((x, y) -> x >= 0 && (x <= 3 && y >= -1 && y <= 6 || x <= 6 && y >= -1 && y <= 3));
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 3; x++) {
            for (int row = 0; row < 3; row++) {
                roof.add(new RoofFit.Tile(x, 6 - row, 1, RoofFit.ALONG_Y, true, EAVE + row * THIRD, 1f + row * THIRD));
            }
        }
        for (int x = 4; x <= 6; x++) {
            roof.add(new RoofFit.Tile(x, 3, 1, RoofFit.ALONG_Y, true, EAVE, 1f));
        }
        addCrown(roof, 1, 6, 0, 2);
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int x = 4; x <= 6; x++) {
            check(madeAt(made, x, 2) == null, "nothing is made over the crown beside the gable at x " + x);
        }
    }

    private static void addCrown(List<RoofFit.Tile> roof, int x0, int x1, int y0, int y1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                RoofFit.Tile crown = new RoofFit.Tile(x, y, 1, RoofFit.LEVEL, true, EAVE, EAVE);
                crown.sheet = true;
                roof.add(crown);
            }
        }
    }

    /**
     * A gable whose ridge runs north and south between x 5 and 6, as at 12716..12725 x 3669..3681: the map draws the east face
     * over x 6..8 and the mod makes the west face over x 3..5. A dormer stands out of the west face, its ridge trim over x 3..4
     * on row 5 and its south face at 3, 6, under a roof falling south to 1 over row 3 west of the gable. The dormer's north
     * face is made over row 4. Each made face, and the main face carried on past them, meets the other in a valley: the
     * dormer's north face was carried east under the main face to its ridge, a pit was cut at 3, 4, and the main face stopped
     * at the dormer's ridge at 4, 5, and each left a square open to the face's underside, black against the sky.
     */
    private static void dormerOnAMadeFaceMeetsItInValleys() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int y = 0; y <= 9; y++) {
            for (int i = 0; i < 3; i++) {
                roof.add(new RoofFit.Tile(6 + i, y, 1, RoofFit.ALONG_X, true, 2f - i * THIRD, 2f - (i + 1) * THIRD));
            }
        }
        for (int x = 3; x <= 4; x++) {
            RoofFit.Tile ridge = new RoofFit.Tile(x, 5, 1, RoofFit.LEVEL, true, 1f + THIRD, 1f + THIRD);
            ridge.fitting = true;
            ridge.half = RoofFit.SOUTH_HALF;
            ridge.halfEdge = 1f + THIRD;
            roof.add(ridge);
        }
        roof.add(new RoofFit.Tile(3, 6, 1, RoofFit.ALONG_Y, true, 1f + THIRD, 1f));
        for (int x = 0; x <= 3; x++) {
            roof.add(new RoofFit.Tile(x, 3, 1, RoofFit.ALONG_Y, true, 1f + THIRD, 1f));
        }
        for (int y = 1; y <= 2; y++) {
            for (int x = 1; x <= 4; x++) {
                roof.add(new RoofFit.Tile(x, y, 1, RoofFit.LEVEL, true, 1f + THIRD, 1f + THIRD));
            }
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, standing((x, y) -> x >= 3 && x <= 8 && y >= 0 && y <= 9 || x >= 0 && x <= 2 && y >= 0 && y <= 3));
        RoofFit.peaks(roof, made);
        made.addAll(RoofFit.valleys(roof, made));
        for (int x = 4; x <= 5; x++) {
            boolean main = false, dormer = false;
            for (RoofFit.Tile tile : made) {
                main |= tile.x == x && tile.y == 4 && tile.fitAxis == RoofFit.ALONG_X && near(tile.fitLow, 1f + (x - 3) * THIRD);
                dormer |= tile.x == x && tile.y == 4 && tile.fitAxis == RoofFit.ALONG_Y;
            }
            check(main && !dormer, "the main face goes on over the dormer's north face at " + x + ", 4");
        }
        RoofFit.Tile valley = null;
        boolean overRidge = false;
        for (RoofFit.Tile tile : made) {
            valley = tile.x == 3 && tile.y == 4 ? tile : valley;
            overRidge |= tile.x == 4 && tile.y == 5 && tile.carried && tile.fitAxis == RoofFit.ALONG_X && near(tile.fitLow, 1f + THIRD) && near(tile.fitHigh, 1f + 2 * THIRD);
        }
        float[] folded = {1f, 1f + THIRD, 1f + THIRD, 1f + THIRD};
        boolean asFolded = valley != null && valley.shaped;
        for (int corner = 0; asFolded && corner < 4; corner++) {
            asFolded &= near(valley.corners[corner], folded[corner]);
        }
        check(asFolded, "the dormer's north face meets the main face in a valley at 3, 4: " + said(valley));
        check(overRidge, "the main face is carried over the dormer's ridge at 4, 5");
    }

    /**
     * As the house at 12716..12724, 3674..3680 has it: the main roof's east face on x 6..8 falls from 2 to its eave, and a dormer
     * at the south-west corner, its ridge running east along y 5 from the west wall, has its south face at 3, 6 coming down to the
     * south eave. The made west face comes down to the dormer's tile a square short of its eave, and goes on over it in a valley.
     */
    private static void dormerAtTheEaveMeetsAMadeFaceInAValley() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int y = 0; y <= 6; y++) {
            for (int i = 0; i < 3; i++) {
                roof.add(new RoofFit.Tile(6 + i, y, 1, RoofFit.ALONG_X, true, 2f - i * THIRD, 2f - (i + 1) * THIRD));
            }
        }
        for (int x = 3; x <= 4; x++) {
            RoofFit.Tile ridge = new RoofFit.Tile(x, 5, 1, RoofFit.LEVEL, true, 1f + THIRD, 1f + THIRD);
            ridge.fitting = true;
            ridge.half = RoofFit.SOUTH_HALF;
            ridge.halfEdge = 1f + THIRD;
            roof.add(ridge);
        }
        roof.add(new RoofFit.Tile(3, 6, 1, RoofFit.ALONG_Y, true, 1f + THIRD, 1f));
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, standing((x, y) -> x >= 3 && x <= 8 && y >= 0 && y <= 6));
        RoofFit.Tile valley = null;
        for (RoofFit.Tile tile : made) {
            valley = tile.x == 3 && tile.y == 6 ? tile : valley;
        }
        float[] face = {1f, 1f + THIRD, 1f + THIRD, 1f};
        boolean onFace = valley != null && valley.carried && valley.fitAxis == RoofFit.ALONG_X;
        for (int corner = 0; onFace && corner < 4; corner++) {
            onFace &= near(valley.corners[corner], face[corner]);
        }
        check(onFace, "the made west face goes on over the dormer's south face at 3, 6 down to its eave: " + said(valley));
    }

    /**
     * As the house at 12699..12705, 3708..3714 has it: the main roof falls south from its ridge on y 12 to its eave, and a wing at
     * its west end runs north past the main roof's north eave, its ridge along x 1 and its east face on x 2 on the map. The made north
     * face of the main roof's west row falls from the ridge as deep as the south face, and meets the wing's made west face in a
     * valley at 0, 9: a hip's fold there left a step under the wing's ridge.
     */
    private static void endRowMeetsAWingAtItsFootInAValley() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 6; x++) {
            for (int i = 0; i < 3; i++) {
                roof.add(new RoofFit.Tile(x, 12 + i, 1, RoofFit.ALONG_Y, true, 2f - i * THIRD, 2f - (i + 1) * THIRD));
            }
        }
        for (int y = 8; y <= 10; y++) {
            RoofFit.Tile ridge = new RoofFit.Tile(1, y, 1, RoofFit.LEVEL, true, 1f + THIRD, 1f + THIRD);
            ridge.fitting = true;
            ridge.half = RoofFit.EAST_HALF;
            ridge.halfEdge = 1f + THIRD;
            roof.add(ridge);
        }
        for (int y = 8; y <= 9; y++) {
            roof.add(new RoofFit.Tile(2, y, 1, RoofFit.ALONG_X, true, 1f + THIRD, 1f));
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, standing((x, y) -> x >= 0 && x <= 6 && y >= 9 && y <= 14 || x >= 0 && x <= 2 && y == 8));
        RoofFit.Tile foot = madeAt(made, 0, 9);
        float[] valley = {1f, 1f + THIRD, 1f + THIRD, 1f + THIRD};
        boolean asValley = foot != null && foot.shaped;
        for (int corner = 0; asValley && corner < 4; corner++) {
            asValley &= near(foot.corners[corner], valley[corner]);
        }
        check(asValley, "the north face's foot meets the wing's west face in a valley at 0, 9: " + said(foot)
                + (foot == null ? "" : " corners " + java.util.Arrays.toString(foot.corners)));
    }

    /**
     * A porch gable one row deep each side: its ridge on x 5 and its east face on x 6, falling to the eave at 1, with the main roof's
     * south face under the ridge's last square at 5, 6, falling to the same eave. The made west face stops on the ridge square the
     * map holds, and is not carried over the main roof's face there: a whole plane falling west laid over that square stood up out
     * of the main face on its south-east half. With the main roof's face a square further west, past the ridge, the made west face
     * meets it in a valley there as a dormer's face.
     */
    private static void oneRowWingIsNotCarriedOverItsRidgeSquare() {
        for (int faceX = 5; faceX >= 4; faceX--) {
            List<RoofFit.Tile> roof = new ArrayList<>();
            for (int y = 0; y <= 6; y++) {
                roof.add(new RoofFit.Tile(6, y, 1, RoofFit.ALONG_X, true, 1f + THIRD, 1f));
                RoofFit.Tile ridge = new RoofFit.Tile(5, y, 1, RoofFit.LEVEL, true, 1f + THIRD, 1f + THIRD);
                ridge.fitting = true;
                ridge.half = RoofFit.EAST_HALF;
                ridge.halfEdge = 1f + THIRD;
                roof.add(ridge);
            }
            roof.add(new RoofFit.Tile(faceX, 6, 1, RoofFit.ALONG_Y, true, 1f + THIRD, 1f));
            RoofFit.fit(roof);
            List<RoofFit.Tile> made = RoofFit.backs(roof, standing((x, y) -> x >= 4 && x <= 6 && y >= 0 && y <= 6));
            RoofFit.Tile carried = null;
            for (RoofFit.Tile tile : made) {
                carried = tile.x == faceX && tile.y == 6 && tile.carried ? tile : carried;
            }
            if (faceX == 5) {
                check(carried == null, "no made west face is carried over the main roof's face at 5, 6: " + said(carried));
            } else {
                float[] face = {1f, 1f + THIRD, 1f + THIRD, 1f};
                boolean onFace = carried != null && carried.fitAxis == RoofFit.ALONG_X;
                for (int corner = 0; onFace && corner < 4; corner++) {
                    onFace &= near(carried.corners[corner], face[corner]);
                }
                check(onFace, "past the ridge the made west face goes on over the main roof's face at 4, 6: " + said(carried));
            }
        }
    }


    /**
     * As the pancake house at 13601..13606, 5767..5768 has its porch: a gable canopy on posts out front of the building, its east
     * face on x 3..5 falling from 2 to its eave, over open ground, with the map's gable past its south end on x 0..5. Its west face
     * is made over open ground as deep as the east one. A lean-to canopy has no far side: one with no gable, and one whose
     * triangle at its end stands under its own slope alone, on x 3..5.
     */
    private static void gabledCanopyHasItsFarSideOverOpenGround() {
        for (int lowestSheet : new int[] {0, 3, -1}) {
            List<RoofFit.Tile> roof = new ArrayList<>();
            for (int y = 5; y <= 6; y++) {
                for (int i = 0; i < 3; i++) {
                    RoofFit.Tile tile = new RoofFit.Tile(3 + i, y, 1, RoofFit.ALONG_X, true, 2f - i * THIRD, 2f - (i + 1) * THIRD);
                    tile.gabled = lowestSheet >= 0 && y == 6;
                    roof.add(tile);
                }
            }
            RoofFit.fit(roof);
            List<RoofFit.Tile> made = RoofFit.backs(roof, new RoofFit.Site() {
                @Override
                public boolean stands(int x, int y, int z) {
                    return false;
                }

                @Override
                public boolean floored(int x, int y, int z) {
                    return false;
                }

                @Override
                public boolean taken(int x, int y, int z, float roof) {
                    return false;
                }

                @Override
                public boolean anyWall(int x, int y, int z, boolean north) {
                    return lowestSheet >= 0 && north && y == 7 && z == 1 && x >= lowestSheet && x <= 5;
                }
            });
            for (int y = 5; y <= 6; y++) {
                for (int x = 0; x <= 2; x++) {
                    RoofFit.Tile tile = madeAt(made, x, y);
                    if (lowestSheet == 0) {
                        check(tile != null && tile.fitAxis == RoofFit.ALONG_X && near(tile.fitLow, 1f + x * THIRD) && near(tile.fitHigh, 1f + (x + 1) * THIRD),
                                "the gabled canopy's west face is made at " + x + ", " + y + ": " + said(tile));
                    } else {
                        check(tile == null, "a lean-to canopy has no far side at " + x + ", " + y + " (sheets from x " + lowestSheet + "): " + said(tile));
                    }
                }
            }
        }
    }


    private static RoofFit.Site standing(java.util.function.BiPredicate<Integer, Integer> stands) {
        return new RoofFit.Site() {
            @Override
            public boolean stands(int x, int y, int z) {
                return stands.test(x, y);
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

    private static RoofFit.Tile madeAt(List<RoofFit.Tile> made, int x, int y) {
        RoofFit.Tile found = null;
        for (RoofFit.Tile tile : made) {
            if (tile.x == x && tile.y == y) {
                check(found == null, "one made tile a square at " + x + ", " + y);
                found = tile;
            }
        }
        return found;
    }

    /** A gambrel's lower slope on level 1, rows 3..5 of x 0..5, falling south from 2 at its north edge to 1. */
    private static List<RoofFit.Tile> gambrelBelow() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 5; x++) {
            for (int row = 0; row < 3; row++) {
                roof.add(new RoofFit.Tile(x, 3 + row, 1, RoofFit.ALONG_Y, true, 2f - row * THIRD, 2f - (row + 1) * THIRD));
            }
        }
        return roof;
    }

    /** The gambrel's top on level 2, painted where the map has it: rows 2..4 of x 2..7, two thirds of a level up. */
    private static List<RoofFit.Tile> gambrelTop() {
        List<RoofFit.Tile> top = new ArrayList<>();
        float eave = 2f + 2f * THIRD, crown = eave + THIRD / 2f;
        for (int x = 2; x <= 7; x++) {
            top.add(new RoofFit.Tile(x, 2, 2, RoofFit.ALONG_Y, true, eave, crown));
            top.add(new RoofFit.Tile(x, 3, 2, RoofFit.LEVEL, true, crown, crown));
            top.add(new RoofFit.Tile(x, 4, 2, RoofFit.ALONG_Y, true, crown, eave));
        }
        return top;
    }

    private static void gambrelsTopPaintedForwardStandsOverItsLoft() {
        List<RoofFit.Tile> roof = gambrelBelow();
        List<RoofFit.Tile> top = gambrelTop();
        roof.addAll(top);
        Map<RoofFit.Tile, RoofFit.Tile> back = RoofFit.paintedForward(roof, strip(1, 0));
        check(back.size() == top.size() && back.keySet().containsAll(top), "every tile of the top is taken back, and nothing else: " + back.size());
        for (Map.Entry<RoofFit.Tile, RoofFit.Tile> one : back.entrySet()) {
            RoofFit.Tile painted = one.getKey(), stands = one.getValue();
            check(stands.x == painted.x - 2 && stands.y == painted.y - 2 && stands.z == painted.z && stands.axis == painted.axis
                    && Math.abs(stands.low - (painted.low - 2f * THIRD)) < 1e-4f && Math.abs(stands.high - (painted.high - 2f * THIRD)) < 1e-4f,
                    "the top stands two squares back and two thirds of a level down: " + painted.x + "," + painted.y + " to " + stands.x + "," + stands.y + " " + stands.low);
        }
        check(back.values().stream().filter(tile -> tile.y == 2).allMatch(tile -> Math.abs(tile.high - 2f) < 1e-4f),
                "its south eave is the lower slope's top: 2 along y 3");
        // With nothing under it a level down, a piece up in the air is no top painted forward.
        check(RoofFit.paintedForward(gambrelTop(), strip(1, 0)).isEmpty(), "a piece with no slope topping under where it stands stays");
        // Nor where a tile of its own level holds a square it would stand on.
        List<RoofFit.Tile> held = gambrelBelow();
        held.addAll(gambrelTop());
        held.add(new RoofFit.Tile(1, 1, 2, RoofFit.ALONG_Y, true, 2f, 2f + THIRD));
        check(RoofFit.paintedForward(held, strip(1, 0)).isEmpty(), "a piece whose squares back are another's stays");
        // Nor a piece with a trim in it: the trims on a wall under a porch read a level up.
        List<RoofFit.Tile> trimmed = gambrelBelow();
        trimmed.addAll(gambrelTop());
        RoofFit.Tile trim = new RoofFit.Tile(8, 3, 2, RoofFit.LEVEL, true, 2f + 2f * THIRD, 2f + 2f * THIRD);
        trim.fitting = true;
        trimmed.add(trim);
        check(RoofFit.paintedForward(trimmed, strip(1, 0)).isEmpty(), "a piece with a trim in it stays");
        // A hip's crown cut flat two thirds up is climbed to by slopes of its own level, which start on its floor.
        List<RoofFit.Tile> hip = gambrelBelow();
        for (int x = 0; x <= 5; x++) {
            hip.add(new RoofFit.Tile(x, 0, 2, RoofFit.ALONG_Y, true, 2f, 2f + THIRD));
            hip.add(new RoofFit.Tile(x, 1, 2, RoofFit.ALONG_Y, true, 2f + THIRD, 2f + 2f * THIRD));
            hip.add(new RoofFit.Tile(x, 2, 2, RoofFit.LEVEL, true, 2f + 2f * THIRD, 2f + 2f * THIRD));
        }
        check(RoofFit.paintedForward(hip, strip(1, 0)).isEmpty(), "a crown its own level's slopes climb to stays");
        // A shallow gable climbing a sixth of a level a square on level 1, from 1 + 1/6 to 2 either side, whose ridge
        // the map has on level 2 a third over that line, over the ridge's own squares.
        List<RoofFit.Tile> shallow = new ArrayList<>();
        float sixth = THIRD / 2f;
        for (int y = 0; y <= 9; y++) {
            for (int x = 0; x <= 4; x++) {
                shallow.add(new RoofFit.Tile(x, y, 1, RoofFit.ALONG_X, true, 1f + (x + 1) * sixth, 1f + (x + 2) * sixth));
                shallow.add(new RoofFit.Tile(8 + x, y, 1, RoofFit.ALONG_X, true, 2f - x * sixth, 2f - (x + 1) * sixth));
            }
            shallow.add(new RoofFit.Tile(5, y, 2, RoofFit.ALONG_X, true, 2f + THIRD, 2f + THIRD + sixth));
            shallow.add(new RoofFit.Tile(6, y, 2, RoofFit.LEVEL, true, 2f + THIRD + sixth, 2f + THIRD + sixth));
            shallow.add(new RoofFit.Tile(7, y, 2, RoofFit.ALONG_X, true, 2f + THIRD + sixth, 2f + THIRD));
        }
        check(RoofFit.paintedForward(shallow, strip(1, 0)).isEmpty(), "a ridge whose eave one square back would lie on the slope under it stays");
    }

    private static void canopyPaintedForwardStandsOverABaysFlatRoof() {
        // A terrace's upper wall on x = 5 over a bay one storey high on x = 5 and 6, whose flat roof is level 1's floor there in the
        // open air, and the upper storey's floors on x < 5. The map paints a canopy, the top third of a slope falling east, on level 1
        // two squares out, at x = 7.
        List<RoofFit.Tile> canopy = new ArrayList<>();
        for (int y = 2; y <= 6; y++) {
            canopy.add(new RoofFit.Tile(7, y, 1, RoofFit.ALONG_X, true, 2f, 2f - THIRD));
        }
        RoofFit.Site bay = new RoofFit.Site() {
            @Override
            public boolean stands(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean floored(int x, int y, int z) {
                return z == 1 && x <= 6 && y >= 0 && y <= 8;
            }

            @Override
            public boolean openFloor(int x, int y, int z) {
                return floored(x, y, z) && x >= 5;
            }

            @Override
            public boolean taken(int x, int y, int z, float roof) {
                return false;
            }
        };
        Map<RoofFit.Tile, RoofFit.Tile> back = RoofFit.paintedForward(canopy, bay);
        check(back.size() == canopy.size(), "every tile of the canopy is taken back: " + back.size());
        for (RoofFit.Tile stands : back.values()) {
            check(stands.x == 5 && stands.z == 1 && near(stands.low, 1f + THIRD) && near(stands.high, 1f),
                    "the canopy runs from the wall down to the bay's roof: " + stands.x + "," + stands.y + " " + stands.low + " to " + stands.high);
        }
        check(RoofFit.paintedForward(canopy, strip(1, 0)).isEmpty(), "with no floor where it would stand it stays");
        // Painted a square nearer and a third higher, three squares back it would stand on the upper storey's floors, in a room, and stays.
        List<RoofFit.Tile> further = new ArrayList<>();
        for (RoofFit.Tile tile : canopy) {
            further.add(new RoofFit.Tile(tile.x - 1, tile.y, 1, RoofFit.ALONG_X, true, tile.low + THIRD, tile.high + THIRD));
        }
        check(RoofFit.paintedForward(further, bay).isEmpty(), "a piece over a storey's floors stays");
        // The bay's eave trims, on its roof against the wall and under it a level down, are no roof where the canopy stands.
        List<RoofFit.Tile> trimmed = new ArrayList<>(canopy);
        for (int y = 0; y <= 8; y++) {
            for (RoofFit.Tile trim : List.of(new RoofFit.Tile(5, y, 1, RoofFit.LEVEL, true, 1f, 1f), new RoofFit.Tile(5, y, 0, RoofFit.LEVEL, true, 1f, 1f))) {
                trim.fitting = true;
                trimmed.add(trim);
            }
        }
        check(RoofFit.paintedForward(trimmed, bay).size() == canopy.size(), "a canopy over the bay's trims is taken back");
        // Nor is a trim under it the slope it would meet: with no floor in the open air there it stays.
        check(RoofFit.paintedForward(trimmed, strip(1, 0)).isEmpty(), "a trim under where a piece would stand does not take it back");
        // A slope of the bay's own on a square the canopy would stand on keeps it out.
        List<RoofFit.Tile> held = new ArrayList<>(canopy);
        held.add(new RoofFit.Tile(5, 3, 1, RoofFit.ALONG_X, true, 1f + THIRD, 1f));
        check(RoofFit.paintedForward(held, bay).isEmpty(), "a canopy whose squares back hold a slope stays");
    }

    private static void slopePaintedTwiceIsDroppedNotLaidOut() {
        // A garage's slope on level 1, row 2 of x 0..4, falling south from 1 + 1/3 to 1; the map paints it again on row 3
        // of x 1..5 a third up, a square out past its eave.
        List<RoofFit.Tile> roof = new ArrayList<>();
        List<RoofFit.Tile> copy = new ArrayList<>();
        for (int x = 0; x <= 4; x++) {
            roof.add(new RoofFit.Tile(x, 2, 1, RoofFit.ALONG_Y, true, 1f + THIRD, 1f));
            copy.add(new RoofFit.Tile(x + 1, 3, 1, RoofFit.ALONG_Y, true, 1f + 2f * THIRD, 1f + THIRD));
        }
        roof.addAll(copy);
        Set<RoofFit.Tile> twice = RoofFit.paintedTwice(roof);
        check(twice.size() == copy.size() && twice.containsAll(copy), "every tile of the copy is found, and nothing else: " + twice.size());
        // A slope falling south over two rows is no copy: the row back along the line of sight is higher.
        List<RoofFit.Tile> slope = new ArrayList<>();
        for (int x = 0; x <= 4; x++) {
            slope.add(new RoofFit.Tile(x, 2, 1, RoofFit.ALONG_Y, true, 1f + 2f * THIRD, 1f + THIRD));
            slope.add(new RoofFit.Tile(x, 3, 1, RoofFit.ALONG_Y, true, 1f + THIRD, 1f));
        }
        check(RoofFit.paintedTwice(slope).isEmpty(), "a slope's lower row stays");
        // Nor where the tile back along the line falls the other way.
        List<RoofFit.Tile> crossed = new ArrayList<>(copy);
        for (int x = 0; x <= 4; x++) {
            crossed.add(new RoofFit.Tile(x, 2, 1, RoofFit.ALONG_X, true, 1f + THIRD, 1f));
        }
        check(RoofFit.paintedTwice(crossed).isEmpty(), "a tile over a slope of another axis stays");
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
        // One row deep, the slope at each end meets the whole of its side.
        List<RoofFit.Tile> shallow = new ArrayList<>();
        for (RoofFit.Tile tile : gable) {
            if (!tile.sheet || tile.y == 2) {
                shallow.add(tile);
            }
        }
        shallow.removeIf(tile -> tile.x == 0 || tile.x == 9);
        shallow.add(new RoofFit.Tile(0, 2, 1, RoofFit.ALONG_X, true, 1f, EAVE));
        shallow.add(new RoofFit.Tile(9, 2, 1, RoofFit.ALONG_X, true, EAVE, 1f));
        check(RoofFit.behindRidges(shallow).size() == 8, "a row of sheets a long ridge climbs above is behind it though an end slope lines its east side: " + RoofFit.behindRidges(shallow).size());
        // The crown of the house at 12848..12857 x 3169..3176: six by six, its east part's slope a row deeper climbing above it
        // along the whole east side, and the south slope climbing to it along five squares of the south side.
        List<RoofFit.Tile> deeper = new ArrayList<>();
        for (int x = 1; x <= 6; x++) {
            if (x <= 5) {
                deeper.add(new RoofFit.Tile(x, 7, 1, RoofFit.ALONG_Y, true, EAVE, 1f));
            }
            for (int y = 1; y <= 6; y++) {
                RoofFit.Tile sheet = new RoofFit.Tile(x, y, 1, RoofFit.LEVEL, true, EAVE, EAVE);
                sheet.sheet = true;
                deeper.add(sheet);
            }
        }
        for (int y = 1; y <= 6; y++) {
            deeper.add(new RoofFit.Tile(7, y, 1, RoofFit.ALONG_X, true, EAVE + THIRD, EAVE));
        }
        check(RoofFit.behindRidges(deeper).isEmpty(), "a crown its south slope climbs to along most of that side stays, though a ridge on the east climbs higher along all of its");
    }

    /**
     * The barn at 3955..3965 x 11459..11466: the map has the south side of its gambrel only, and
     * on its north row, level 1, a sheet of the roof's own laid as a floor where the game's view
     * never looks. The far slope made down the north side comes down to that sheet's height there.
     */
    private static void roofSheetLaidAsAFloorAtTheFootOfAFarSlopeIsNoTerrace() throws Exception {
        TestMap map = new TestMap();
        zombie.iso.areas.IsoBuilding barn = new zombie.iso.areas.IsoBuilding(null);
        map.cell.square(0, 0, 0).building(barn);
        map.cell.square(0, 0, 1).floor(map.sprite("roofs_02_22", IsoObjectType.normal, "solidfloor"));
        map.cell.square(1, 0, 0).building(barn);
        map.cell.square(1, 0, 1).floor(map.sprite("floors_exterior_tilesandstone_01_0", IsoObjectType.normal, "solidfloor"));
        map.cell.square(2, 0, 0).building(barn);
        map.cell.square(2, 0, 1).floor(map.sprite("roofs_02_22", IsoObjectType.normal, "solidfloor"));
        map.roofSprite(2, 0, 1, "roofs_02_0");
        java.lang.reflect.Method made = Roofs.class.getDeclaredMethod("site", zombie.iso.IsoCell.class, int.class, java.util.HashSet.class);
        made.setAccessible(true);
        RoofFit.Site site = (RoofFit.Site) made.invoke(null, map.cell, 1, new java.util.HashSet<Long>());
        check(!site.taken(0, 0, 1, 1f), "a roof's sheet laid as a floor where the far slope comes down to it is the slope's foot, and no terrace");
        check(site.taken(0, 0, 1, 1f + THIRD), "the same sheet under a slope that would stand a third of a level over it is a flat roof, which the slope does not cover");
        check(site.taken(1, 0, 1, 1f), "a terrace of the floors' own sheets stays one");
        check(site.taken(2, 0, 1, 1f), "nor is a sheet with a tile of the roof on its square left out of the roofing");
    }

    /**
     * The tool shed at 7948..7956 x 11741..11752: its loft on level 1, over x 0..1 and rows 0..2 here, is a room with no
     * floor, and only the squares at its two gable ends, where its walls stand, are on the map. The far slope made over the
     * loft lies on them too. A room with floors is a storey, and the one square of it without a floor, a stairwell, is
     * still taken.
     */
    private static void roomWithNoFloorUnderTheRoofIsNoStorey() throws Exception {
        TestMap map = new TestMap();
        zombie.iso.RoomDef loftDef = new zombie.iso.RoomDef(1);
        loftDef.rects.add(new zombie.iso.RoomDef.RoomRect(0, 0, 2, 3));
        zombie.iso.areas.IsoRoom loft = new zombie.iso.areas.IsoRoom(loftDef);
        for (int y = 0; y <= 2; y += 2) {
            map.cell.square(0, y, 1).room(loft, 7L);
            map.wall(0, y, 1, true);
        }
        zombie.iso.RoomDef storeyDef = new zombie.iso.RoomDef(1);
        storeyDef.rects.add(new zombie.iso.RoomDef.RoomRect(4, 0, 2, 1));
        zombie.iso.areas.IsoRoom storey = new zombie.iso.areas.IsoRoom(storeyDef);
        map.cell.square(4, 0, 1).room(storey, 8L);
        map.cell.square(4, 0, 1).floor(map.sprite("floors_interior_tilesandwood_01_0", IsoObjectType.normal, "solidfloor"));
        map.cell.square(5, 0, 1).room(storey, 8L);
        java.lang.reflect.Method made = Roofs.class.getDeclaredMethod("site", zombie.iso.IsoCell.class, int.class, java.util.HashSet.class);
        made.setAccessible(true);
        RoofFit.Site site = (RoofFit.Site) made.invoke(null, map.cell, 1, new java.util.HashSet<Long>());
        check(!site.taken(0, 0, 1, 1f + 2 * THIRD) && !site.taken(0, 2, 1, 1f + 2 * THIRD), "the loft's gable end squares are under the made slope, no storey");
        check(site.taken(4, 0, 1, 1f + 2 * THIRD), "a room with a floor is a storey the slope stops at");
        check(site.taken(5, 0, 1, 1f + 2 * THIRD), "and so is its stairwell, the one square of it with no floor");
    }

    /**
     * The farmhouse at 2863..2870 x 10395..10403: its attic, a room with floors on level 1 here, lies under the ridge, and
     * the far slope made from the eave on level 1 passes a level over it there. The attic is no storey that slope stops at.
     */
    private static void roomTheSlopePassesWhollyOverIsNoStoreyItStopsAt() throws Exception {
        TestMap map = new TestMap();
        zombie.iso.RoomDef atticDef = new zombie.iso.RoomDef(1);
        atticDef.rects.add(new zombie.iso.RoomDef.RoomRect(0, 0, 2, 1));
        zombie.iso.areas.IsoRoom attic = new zombie.iso.areas.IsoRoom(atticDef);
        for (int x = 0; x <= 1; x++) {
            map.cell.square(x, 0, 1).room(attic, 9L);
            map.cell.square(x, 0, 1).floor(map.sprite("floors_interior_tilesandwood_01_0", IsoObjectType.normal, "solidfloor"));
        }
        map.cell.square(1, 0, 2).floor(map.sprite("floors_interior_tilesandwood_01_0", IsoObjectType.normal, "solidfloor"));
        java.lang.reflect.Method made = Roofs.class.getDeclaredMethod("site", zombie.iso.IsoCell.class, int.class, java.util.HashSet.class);
        made.setAccessible(true);
        RoofFit.Site site = (RoofFit.Site) made.invoke(null, map.cell, 1, new java.util.HashSet<Long>());
        check(!site.taken(0, 0, 1, 2f + THIRD), "a slope a level over the attic passes over it");
        check(site.taken(0, 0, 1, 1f + 2 * THIRD), "a slope that comes down into the attic stops at it");
        check(site.taken(1, 0, 1, 2f + THIRD), "a slope over the attic still stops at a floor on the level above");
    }

    private static void levelSheetPaintedOverItsFloorIsAFlatTop() {
        check(Roofs.flatTop(new RoofFit.Tile(0, 0, 2, RoofFit.LEVEL, true, 2f + THIRD, 2f + THIRD), false), "a level sheet a third of a level over its floor is a crown");
        check(Roofs.flatTop(new RoofFit.Tile(0, 0, 2, RoofFit.LEVEL, true, 2f + 2f * THIRD, 2f + 2f * THIRD), false), "and so is one two thirds over it");
        check(!Roofs.flatTop(new RoofFit.Tile(0, 0, 2, RoofFit.LEVEL, true, 2f, 2f), false), "a sheet on its floor lies under a slope");
        check(Roofs.flatTop(new RoofFit.Tile(0, 0, 2, RoofFit.LEVEL, true, 2f, 2f), true), "but over a room, with no slope on its square, it is the room's roof");
        check(!Roofs.flatTop(new RoofFit.Tile(0, 0, 2, RoofFit.ALONG_X, true, 2f, 2f + THIRD), false), "a slope is no flat top");
        RoofFit.Tile trim = new RoofFit.Tile(0, 0, 2, RoofFit.LEVEL, true, 2f + THIRD, 2f + THIRD);
        trim.fitting = true;
        check(!Roofs.flatTop(trim, false), "nor is a trim narrower than a tile");
        check(Roofs.ceiling("ceilings_01_0") && !Roofs.ceiling("floors_exterior_tilesandstone_01_0") && !Roofs.ceiling(null),
                "the map's ceilings are told from a terrace's floor by their sheet");
    }

    /**
     * A gambrel barn over x 0..3, rows 0..11, as 7621..7642 x 14348..14359 is laid: the south lower slope climbs a third of
     * a level a row over rows 11..9 to the crease at level 2, the upper slopes climb a sixth a row to the ridge between rows
     * 6 and 5, and the map gives the north upper slope (rows 5..3) but nothing under it. The north lower slope is made from
     * the foot of the north upper slope down to the north wall, at the south lower slope's pitch.
     */
    private static void gambrelsFarLowerSlopeComesDownFromItsUpperSlope() {
        RoofFit.Site site = standing((x, y) -> x >= 0 && x <= 3 && y >= 0 && y <= 11);
        float sixth = THIRD / 2f;
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 3; x++) {
            for (int row = 0; row < 3; row++) {
                roof.add(new RoofFit.Tile(x, 11 - row, 1, RoofFit.ALONG_Y, true, 1f + (row + 1) * THIRD, 1f + row * THIRD));
                roof.add(new RoofFit.Tile(x, 8 - row, 2, RoofFit.ALONG_Y, true, 2f + (row + 1) * sixth, 2f + row * sixth));
                roof.add(new RoofFit.Tile(x, 3 + row, 2, RoofFit.ALONG_Y, true, 2f + row * sixth, 2f + (row + 1) * sixth));
            }
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int x = 0; x <= 3; x++) {
            for (int row = 0; row < 3; row++) {
                RoofFit.Tile tile = madeAt(made, x, row);
                check(tile != null && !tile.shaped && tile.fitAxis == RoofFit.ALONG_Y && near(tile.fitLow, 1f + row * THIRD) && near(tile.fitHigh, 1f + (row + 1) * THIRD),
                        "the north lower slope climbs from the north wall to the upper slope's foot at " + x + ", " + row + ": "
                                + said(tile));
            }
            for (int y = 3; y <= 11; y++) {
                check(madeAt(made, x, y) == null, "nothing is made over the map's own slopes at " + x + ", " + y);
            }
        }
    }

    /**
     * A house over x 0..7 and y 0..7 whose west wall steps in a tile for rows 4..7, as at 8673..8674 x 8415..8424: the map
     * has its east slope only, two rows from the eave at 1 on x 8 to a flat top at 1 + 2/3. The made west slope climbs from
     * the wall on both sides of the step, and where the wall steps in the two meet in a valley: no row stands a third of a
     * level over the row beside it.
     */
    private static void farSlopeMeetsAStepInItsWallInAValley() {
        RoofFit.Site site = standing((x, y) -> y >= 0 && y <= 7 && x <= 7 && x >= (y >= 4 ? 1 : 0));
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int y = 0; y <= 7; y++) {
            roof.add(new RoofFit.Tile(6, y, 1, RoofFit.ALONG_X, true, 1f + 2 * THIRD, 1f + THIRD));
            roof.add(new RoofFit.Tile(7, y, 1, RoofFit.ALONG_X, true, 1f + THIRD, 1f));
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        RoofFit.Tile corner = madeAt(made, 0, 3);
        check(corner != null && near(corner.corners[2], 1f) && near(corner.corners[3], 1f),
                "the corner of the wall that steps out is at the eave on its south edge: " + (corner == null ? "nothing" : Arrays.toString(corner.corners)));
        for (int x = 1; x <= 5; x++) {
            RoofFit.Tile north = madeAt(made, x, 3), south = madeAt(made, x, 4);
            check(north != null && south != null && near(north.corners[3], south.corners[0]) && near(north.corners[2], south.corners[1]),
                    "the roof each side of the step meets along y 4 at " + x + ": " + (north == null ? "nothing" : Arrays.toString(north.corners))
                            + " over " + (south == null ? "nothing" : Arrays.toString(south.corners)));
        }
        RoofFit.Tile wall = madeAt(made, 0, 1);
        check(wall != null && near(wall.corners[0], 1f) && near(wall.corners[1], 1f + THIRD), "the slope away from the step climbs from its wall: " + said(wall));
    }

    /**
     * A house over x 0..9 and y 0..5, as at 13511..13521 x 2265..2273: the map's south slope is two rows deep from a ridge
     * at 1 + 2/3 on y 2 to the eave at 1 on y 4, but over x 3..8 the lower row is the level crown of a lower wing's hip at
     * 1 + 1/3. The north side made from every row comes down to the wall at 1: the rows over the middle of the crown read
     * the crown as their eave and made their far side a row short, a flat at the ridge's height over the north wall.
     */
    private static void farSideOfRowsOverALongCrownStandsOnTheWall() {
        RoofFit.Site site = standing((x, y) -> x >= 0 && x <= 9 && y >= 0 && y <= 5);
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 9; x++) {
            roof.add(new RoofFit.Tile(x, 2, 1, RoofFit.ALONG_Y, true, 1f + 2 * THIRD, 1f + THIRD));
            if (x >= 3 && x <= 8) {
                RoofFit.Tile crown = new RoofFit.Tile(x, 3, 1, RoofFit.LEVEL, true, 1f + THIRD, 1f + THIRD);
                crown.sheet = true;
                roof.add(crown);
            } else {
                roof.add(new RoofFit.Tile(x, 3, 1, RoofFit.ALONG_Y, true, 1f + THIRD, 1f));
            }
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int x = 0; x <= 9; x++) {
            RoofFit.Tile foot = madeAt(made, x, 0), upper = madeAt(made, x, 1);
            check(foot != null && foot.fitAxis == RoofFit.ALONG_Y && near(foot.fitLow, 1f) && near(foot.fitHigh, 1f + THIRD)
                    && upper != null && upper.fitAxis == RoofFit.ALONG_Y && near(upper.fitLow, 1f + THIRD) && near(upper.fitHigh, 1f + 2 * THIRD),
                    "the north side comes down to the wall at " + x + ": " + said(upper) + " over " + said(foot));
        }
    }

    /**
     * A cabin over x 2..7 and y 0..8 with a wing over x 0..1 and y 3..8, as at 11062..11069 x 10637..10646: the map has
     * the east slope only, four rows from a ridge at 2 + 1/3 on x 4 to the eave at 1 on x 8. Rows 3..8 of the made west
     * slope come down over the wing to the eave at 1 on x 0; rows 0..2 stop on the wall at x 2, two rows short. The wing's
     * north wall is no eave for the rows over it: they go on down in one plane, and meet rows 0..2 along y 3.
     */
    private static void farSlopeRunsOnDownOverAWingPastAShallowerPart() {
        RoofFit.Site site = standing((x, y) -> y >= 0 && y <= 8 && x <= 7 && x >= (y >= 3 ? 0 : 2));
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int y = 0; y <= 8; y++) {
            roof.add(new RoofFit.Tile(4, y, 2, RoofFit.ALONG_X, true, 2f + THIRD, 2f));
            roof.add(new RoofFit.Tile(5, y, 1, RoofFit.ALONG_X, true, 2f, 1f + 2 * THIRD));
            roof.add(new RoofFit.Tile(6, y, 1, RoofFit.ALONG_X, true, 1f + 2 * THIRD, 1f + THIRD));
            roof.add(new RoofFit.Tile(7, y, 1, RoofFit.ALONG_X, true, 1f + THIRD, 1f));
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int x = 0; x <= 3; x++) {
            RoofFit.Tile over = madeAt(made, x, 3);
            check(over != null && !over.shaped && over.fitAxis == RoofFit.ALONG_X && near(over.fitLow, 1f + x * THIRD) && near(over.fitHigh, 1f + (x + 1) * THIRD),
                    "the west slope runs on down over the wing's north end at " + x + ", 3: " + said(over));
        }
        for (int x = 2; x <= 3; x++) {
            RoofFit.Tile north = madeAt(made, x, 2), south = madeAt(made, x, 3);
            check(north != null && south != null && near(north.corners[3], south.corners[0]) && near(north.corners[2], south.corners[1]),
                    "the roof each side of the wing's end meets along y 3 at " + x + ": " + said(north) + " over " + said(south));
        }
    }

    /**
     * A hip cut short over x 0..7 and y 0..5, as at 8674..8683 x 8415..8425: its crown at 1 + 2/3 over x 2..5 and y 1..3,
     * the map's east slope climbing to it from the eave at 1 on x 8 and its south slope from the eave on y 6, the south
     * slope's end tile at x 1 on row 4 under the hip. The east slope is carried over the crown and down the west hip's face.
     * Across the end tile's top lies no crown, only the crown's corner beside it: the roof there is that face, and no far
     * side of the south slope is made over it to meet the face in a valley.
     */
    private static void rowAtTheEndOfAHipHasNoFarSide() {
        RoofFit.Site site = standing((x, y) -> x >= 0 && x <= 7 && y >= 0 && y <= 5);
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 7; x++) {
            roof.add(new RoofFit.Tile(x, 5, 1, RoofFit.ALONG_Y, true, 1f + THIRD, 1f));
            if (x >= 1 && x <= 5) {
                roof.add(new RoofFit.Tile(x, 4, 1, RoofFit.ALONG_Y, true, 1f + 2 * THIRD, 1f + THIRD));
            }
        }
        for (int y = 1; y <= 3; y++) {
            roof.add(new RoofFit.Tile(6, y, 1, RoofFit.ALONG_X, true, 1f + 2 * THIRD, 1f + THIRD));
            roof.add(new RoofFit.Tile(7, y, 1, RoofFit.ALONG_X, true, 1f + THIRD, 1f));
            for (int x = 2; x <= 5; x++) {
                RoofFit.Tile crown = new RoofFit.Tile(x, y, 1, RoofFit.LEVEL, true, 1f + 2 * THIRD, 1f + 2 * THIRD);
                crown.sheet = true;
                roof.add(crown);
            }
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int y = 2; y <= 3; y++) {
            RoofFit.Tile face = madeAt(made, 1, y);
            check(face != null && !face.shaped && face.fitAxis == RoofFit.ALONG_X && near(face.fitLow, 1f + THIRD) && near(face.fitHigh, 1f + 2 * THIRD),
                    "the west hip's face climbs to the crown at 1, " + y + ": " + said(face));
        }
    }

    /**
     * A house over x 0..9 and y 0..9, as at 3961..3976 x 9853..9867: the map's south slope is two rows deep, from the eave
     * at 1 on y 9 to a crown at 1 + 2/3, but over x 4 a dormer's ridge trim at 1 + 1/3 takes the lower row's place. The
     * north side made from that row comes down to the wall at 1 with the rest: it read the trim as the eave and stood on the
     * wall at 1 + 1/3, with a level step at the crown's height behind it.
     */
    private static void farSideOfARowOverADormerStandsOnTheWall() {
        RoofFit.Site site = standing((x, y) -> x >= 0 && x <= 9 && y >= 0 && y <= 9);
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 9; x++) {
            roof.add(new RoofFit.Tile(x, 8, 1, RoofFit.ALONG_Y, true, 1f + 2 * THIRD, 1f + THIRD));
            if (x == 4) {
                RoofFit.Tile trim = new RoofFit.Tile(x, 9, 1, RoofFit.LEVEL, true, 1f + THIRD, 1f + THIRD);
                trim.fitting = true;
                roof.add(trim);
            } else {
                roof.add(new RoofFit.Tile(x, 9, 1, RoofFit.ALONG_Y, true, 1f + THIRD, 1f));
            }
            for (int y = 2; y <= 7; y++) {
                RoofFit.Tile crown = new RoofFit.Tile(x, y, 1, RoofFit.LEVEL, true, 1f + 2 * THIRD, 1f + 2 * THIRD);
                crown.sheet = true;
                roof.add(crown);
            }
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int y = 0; y <= 1; y++) {
            RoofFit.Tile face = madeAt(made, 4, y);
            check(face != null && !face.shaped && face.fitAxis == RoofFit.ALONG_Y && near(face.fitLow, 1f + y * THIRD) && near(face.fitHigh, 1f + (y + 1) * THIRD),
                    "the north side over the dormer's row climbs from the wall at 4, " + y + ": " + said(face));
        }
    }

    /**
     * The same house with a small gable or bay standing out of the south slope over x 3..5, as at 10400..10408 x
     * 12720..12731 and 3125..3130 x 5754..5760: the map leaves the lower row at x 4 with no tile at a gable's peak, or with
     * only the level sheet a bay's hip is cut short at, at the row's foot. The north side made from that row comes down
     * to the wall at 1 with the rest: it read the upper row's foot as the eave, ran level at the crown's height and stood
     * on the wall at 1 + 1/3. A third of a level lower, the lower row tops on the floor of level 1 and is held on level 0.
     */
    private static void farSideOfARowOverABayStandsOnTheWall() {
        for (boolean bay : new boolean[] {false, true}) {
            farSideOfARowOverABayStandsOnTheWall(bay, 0f);
            farSideOfARowOverABayStandsOnTheWall(bay, THIRD);
        }
    }

    private static void farSideOfARowOverABayStandsOnTheWall(boolean bay, float lower) {
        RoofFit.Site site = standing((x, y) -> x >= 0 && x <= 9 && y >= 0 && y <= 9);
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 9; x++) {
            roof.add(new RoofFit.Tile(x, 8, 1, RoofFit.ALONG_Y, true, 1f + 2 * THIRD - lower, 1f + THIRD - lower));
            if (x != 4) {
                roof.add(new RoofFit.Tile(x, 9, lower > 0f ? 0 : 1, RoofFit.ALONG_Y, true, 1f + THIRD - lower, 1f - lower));
            } else if (bay) {
                RoofFit.Tile sheet = new RoofFit.Tile(x, 9, 1, RoofFit.LEVEL, true, 1f + THIRD - lower, 1f + THIRD - lower);
                sheet.sheet = true;
                roof.add(sheet);
            }
            for (int y = 2; y <= 7; y++) {
                RoofFit.Tile crown = new RoofFit.Tile(x, y, 1, RoofFit.LEVEL, true, 1f + 2 * THIRD - lower, 1f + 2 * THIRD - lower);
                crown.sheet = true;
                roof.add(crown);
            }
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int y = 0; y <= 1; y++) {
            RoofFit.Tile face = madeAt(made, 4, y);
            check(face != null && !face.shaped && face.fitAxis == RoofFit.ALONG_Y && near(face.fitLow, 1f - lower + y * THIRD)
                    && near(face.fitHigh, 1f - lower + (y + 1) * THIRD), (bay ? "the north side over the bay" : "the north side over the gable's peak")
                    + " climbs from the wall at 4, " + y + ", " + lower + " lower: " + said(face));
        }
    }

    /**
     * A barn as at 8000..8011 x 11010..11015 and 4166..4178 x 11056..11061: the map's near slope is two rows deep up to
     * 1 + 2/3 at the ridge, where a wall a whole level high stands along it, and its far side is two shallow rows climbing
     * back towards the ridge from the far wall, with a gap between. The far side over the gap climbs on from the shallow
     * rows' top at the standard pitch to the wall, and runs level at the wall's top where the gap is deeper than that
     * climb. Mirrored from the near side, it started at the near side's eave, a third of a level under the shallow rows'
     * top, and the wall stood bare over it. With no wall there, the far side is the near side's mirror as before, hung from
     * the ridge down to the near side's eave. Laid along both axes, with gaps of two and three squares.
     */
    private static void farSideClimbsFromAShallowerSlopeToTheWallOnItsRidge() {
        for (boolean alongX : new boolean[] {false, true}) {
            for (int gap = 2; gap <= 3; gap++) {
                for (boolean wall : new boolean[] {true, false}) {
                    farSideClimbsFromAShallowerSlopeToTheWallOnItsRidge(alongX, gap, wall);
                }
            }
        }
    }

    private static void farSideClimbsFromAShallowerSlopeToTheWallOnItsRidge(boolean alongX, int gap, boolean wall) {
        int ridgeRow = 2 + gap;
        RoofFit.Site stands = standing((x, y) -> (alongX ? x : y) >= 0 && (alongX ? x : y) <= ridgeRow + 1 && (alongX ? y : x) >= 0 && (alongX ? y : x) <= 9);
        RoofFit.Site site = new RoofFit.Site() {
            @Override
            public boolean stands(int x, int y, int z) {
                return stands.stands(x, y, z);
            }

            @Override
            public boolean floored(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean taken(int x, int y, int z, float roof) {
                return false;
            }

            @Override
            public boolean walled(int x, int y, int z, boolean north) {
                return wall && north != alongX && (alongX ? x : y) == ridgeRow && z == 1;
            }
        };
        int axis = alongX ? RoofFit.ALONG_X : RoofFit.ALONG_Y;
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int b = 0; b <= 9; b++) {
            float[][] rows = {{0, 1.094f, 1.203f}, {1, 1.203f, 1.313f}, {ridgeRow, 1f + 2 * THIRD, 1f + THIRD}, {ridgeRow + 1, 1f + THIRD, 1f}};
            for (float[] row : rows) {
                int a = (int) row[0];
                roof.add(new RoofFit.Tile(alongX ? a : b, alongX ? b : a, 1, axis, true, row[1], row[2]));
            }
        }
        RoofFit.fit(roof);
        float foot = 0f;
        for (RoofFit.Tile tile : roof) {
            if ((alongX ? tile.x : tile.y) == 1) {
                foot = Math.max(tile.fitLow, tile.fitHigh);
            }
        }
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int a = 2; a < ridgeRow; a++) {
            RoofFit.Tile face = alongX ? madeAt(made, a, 4) : madeAt(made, 4, a);
            float low = wall ? Math.min(foot + (a - 2) * THIRD, 2f) : 1f + (a - ridgeRow + 2) * THIRD;
            float high = wall ? Math.min(foot + (a - 1) * THIRD, 2f) : low + THIRD;
            boolean right = low < 1f - 0.01f ? face == null
                    : face != null && face.fitAxis == axis && near(face.fitLow, low) && near(face.fitHigh, high);
            check(right,
                    "the far side " + (alongX ? "along x" : "along y") + " over a gap of " + gap + " at " + a
                    + (wall ? " climbs from the shallow rows to the wall" : " mirrors the near slope with no wall") + ": " + said(face));
        }
    }

    /**
     * A porch as at 2084..2089, 14277..14279 against the south end of a gambrel house: a lean-to on level 1 over y 8, climbing
     * north at the standard pitch from 1 to 1 + 1/3, and a level strip at its top over y 7, against the house's end wall on the
     * north edge of y 7. The house stands over y 0..6. A far side carried north from the porch's top went through the wall and
     * came down inside the house, where it creased the house's own slope. With no wall there it is still made.
     */
    private static void leanToAgainstAWallHasNoFarSide() {
        for (boolean wall : new boolean[] {true, false}) {
            RoofFit.Site stands = standing((x, y) -> x >= 0 && x <= 5 && y >= 0 && y <= 8);
            RoofFit.Site site = new RoofFit.Site() {
                @Override
                public boolean stands(int x, int y, int z) {
                    return stands.stands(x, y, z);
                }

                @Override
                public boolean floored(int x, int y, int z) {
                    return false;
                }

                @Override
                public boolean taken(int x, int y, int z, float roof) {
                    return false;
                }

                @Override
                public boolean anyWall(int x, int y, int z, boolean north) {
                    return wall && north && y == 7 && z == 1;
                }
            };
            List<RoofFit.Tile> roof = new ArrayList<>();
            for (int x = 1; x <= 4; x++) {
                roof.add(new RoofFit.Tile(x, 7, 1, RoofFit.LEVEL, true, EAVE, EAVE));
                roof.add(new RoofFit.Tile(x, 8, 1, RoofFit.ALONG_Y, true, EAVE, 1f));
            }
            RoofFit.fit(roof);
            List<RoofFit.Tile> made = RoofFit.backs(roof, site);
            boolean inside = false;
            for (RoofFit.Tile tile : made) {
                inside |= tile.y <= 6;
            }
            check(inside != wall, wall ? "a lean-to against a wall has no far side through it: " + made.size() + " made"
                    : "a lean-to with no wall at its top has its far side made");
        }
    }

    /**
     * A house over x 0..9 and y 0..7, as at 12798..12807 x 3240..3247: the map's east slope is two rows deep, up to 1 + 2/3
     * on x 8, its south slope one row, and the crown between them lies at 1 + 1/3. The east slope's top row climbs past the
     * crown and lies level at its height, and the west side, made from the east slope across the crown, comes down from
     * the crown to the wall: it stood a third of a level over the crown, from 1 + 1/3 to 1 + 2/3.
     */
    private static void slopeThatClimbsPastItsCrownLiesLevel() {
        RoofFit.Site site = standing((x, y) -> x >= 0 && x <= 9 && y >= 0 && y <= 7);
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int y = 0; y <= 7; y++) {
            roof.add(new RoofFit.Tile(8, y, 1, RoofFit.ALONG_X, true, 1f + 2 * THIRD, 1f + THIRD));
            roof.add(new RoofFit.Tile(9, y, 1, RoofFit.ALONG_X, true, 1f + THIRD, 1f));
        }
        for (int x = 0; x <= 6; x++) {
            roof.add(new RoofFit.Tile(x, 7, 1, RoofFit.ALONG_Y, true, 1f + THIRD, 1f));
        }
        for (int y = 1; y <= 6; y++) {
            for (int x = 1; x <= 7; x++) {
                RoofFit.Tile crown = new RoofFit.Tile(x, y, 1, RoofFit.LEVEL, true, 1f + THIRD, 1f + THIRD);
                crown.sheet = true;
                roof.add(crown);
            }
        }
        RoofFit.fit(roof);
        for (RoofFit.Tile tile : roof) {
            if (tile.x == 8) {
                check(tile.fitAxis == RoofFit.LEVEL && near(tile.fitLow, 1f + THIRD), "the east slope's top row lies level at the crown at 8, " + tile.y + ": " + said(tile));
            }
        }
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int y = 2; y <= 5; y++) {
            RoofFit.Tile face = madeAt(made, 0, y);
            check(face != null && !face.shaped && face.fitAxis == RoofFit.ALONG_X && near(face.fitLow, 1f) && near(face.fitHigh, 1f + THIRD),
                    "the west side climbs from the wall to the crown at 0, " + y + ": " + said(face));
        }
    }

    /**
     * The same house with a gable two rows wider than the crown's end on the south wall under the east slope's rows, its
     * peak on the line x 8, as at 12853..12856 x 3177: the top row is the gable's slope up to its ridge, and stays one, and
     * the far side of that ridge comes down over x 7 onto the crown.
     */
    private static void gablesSlopeThatClimbsPastACrownStaysASlope() {
        RoofFit.Site site = standing((x, y) -> x >= 0 && x <= 9 && y >= 0 && y <= 7);
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int y = 0; y <= 7; y++) {
            RoofFit.Tile top = new RoofFit.Tile(8, y, 1, RoofFit.ALONG_X, true, 1f + 2 * THIRD, 1f + THIRD);
            top.gabled = y == 7;
            roof.add(top);
            roof.add(new RoofFit.Tile(9, y, 1, RoofFit.ALONG_X, true, 1f + THIRD, 1f));
        }
        for (int x = 0; x <= 6; x++) {
            roof.add(new RoofFit.Tile(x, 7, 1, RoofFit.ALONG_Y, true, 1f + THIRD, 1f));
        }
        for (int y = 1; y <= 6; y++) {
            for (int x = 1; x <= 7; x++) {
                RoofFit.Tile crown = new RoofFit.Tile(x, y, 1, RoofFit.LEVEL, true, 1f + THIRD, 1f + THIRD);
                crown.sheet = true;
                roof.add(crown);
            }
        }
        RoofFit.fit(roof);
        for (RoofFit.Tile tile : roof) {
            if (tile.x == 8) {
                check(tile.fitAxis == RoofFit.ALONG_X && near(tile.fitLow, 1f + 2 * THIRD) && near(tile.fitHigh, 1f + THIRD), "the gable's slope climbs to its ridge at 8, " + tile.y + ": " + said(tile));
            }
        }
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int y = 1; y <= 6; y++) {
            RoofFit.Tile face = madeAt(made, 7, y);
            check(face != null && face.fitAxis == RoofFit.ALONG_X && near(face.fitLow, 1f + THIRD) && near(face.fitHigh, 1f + 2 * THIRD),
                    "the ridge's far side comes down onto the crown at 7, " + y + ": " + said(face));
            check(madeAt(made, 6, y) == null, "nothing is made over the crown past it at 6, " + y + ": " + said(madeAt(made, 6, y)));
        }
    }

    /**
     * A house over x 0..9 and y 1..7, as at 6277..6286 x 5410..5416, its ridge along x at 2 on the line y 5 and the map's
     * south slope three rows down to the eave at 1 on y 8; the north side is four rows deep, so its far side climbs three
     * rows from the north wall and runs level along the ridge. A low wing over x 4..6 runs out north to y 0, its ridge at
     * 1 + 1/3 along the middle of x 5, and the map has its east slope. The wing's far side is a row deep and meets the main
     * roof in a valley: carried west to the house's west wall, it lay over the main roof's north side as a flat at the
     * wing's ridge height, walled in by the main roof round it.
     */
    private static void wingsFarSideStopsAtAMainRoofDeeperThanItsNearSide() {
        RoofFit.Site site = standing((x, y) -> x >= 0 && x <= 9 && y >= 1 && y <= 7 || x >= 4 && x <= 6 && y == 0);
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 9; x++) {
            for (int row = 0; row < 3; row++) {
                roof.add(new RoofFit.Tile(x, 7 - row, 1, RoofFit.ALONG_Y, true, 1f + (row + 1) * THIRD, 1f + row * THIRD));
            }
        }
        for (int y = 0; y <= 2; y++) {
            roof.add(new RoofFit.Tile(6, y, 1, RoofFit.ALONG_X, true, 1f + THIRD, 1f));
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int x = 0; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                RoofFit.Tile tile = madeAt(made, x, y);
                check(tile != null && !tile.shaped && tile.fitAxis == RoofFit.ALONG_Y && near(tile.fitLow, 1f + (y - 1) * THIRD) && near(tile.fitHigh, 1f + y * THIRD),
                        "west of the wing the main roof's north side climbs from the north wall at " + x + ", " + y + ": " + said(tile));
            }
        }
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

    private static void anEavesEndCapIsACornerPiece() {
        TestMap map = new TestMap();
        check(RoofBuilder.cornerPiece(map.sprite("roofs_accents_01_16", IsoObjectType.WestRoofB).getProperties()), "a trim hung on no edge is a corner piece");
        for (String cap : new String[] {"roofs_accents_01_43", "roofs_accents_01_47", "roofs_accents_01_53"}) {
            check(RoofBuilder.cornerPiece(map.sprite(cap, IsoObjectType.WestRoofB, "attachedSE").getProperties()),
                    "the eave's end cap " + cap + ", marked attachedSE and painted at its square's north-west corner, is a corner piece");
        }
        check(!RoofBuilder.cornerPiece(map.sprite("roofs_accents_01_8", IsoObjectType.WestRoofB, "attachedW").getProperties()), "a trim on the west edge is not");
        check(!RoofBuilder.cornerPiece(map.sprite("roofs_accents_01_0", IsoObjectType.WestRoofB, "attachedN").getProperties()), "nor one on the north edge");
        check(!RoofBuilder.cornerPiece(map.sprite("roofs_accents_01_15", IsoObjectType.WestRoofB, "attachedNW").getProperties()), "nor one on both");
    }

    private static void gutterTrimGoesOnlyUnderAnEaveMadeOnItsOwnEdge() {
        // A slope on (0, 0, 1) falling east to its eave at the floor of level 1.
        TestMap map = new TestMap();
        map.roofTile(fitted(new RoofFit.Tile(0, 0, 1, RoofFit.ALONG_X, true, 1f + THIRD, 1f)), map.painted);
        RoofBuilder roofs = map.build.roofBuilder;
        check(roofs.trimUnderAMadeEave(1, 0, 1, true, false, false), "a trim hung on the west edge past the slope's eave, on its level, is the eave's gutter");
        check(roofs.trimUnderAMadeEave(1, 0, 0, true, false, false), "so is one on that edge a level down, at the top of the wall under the eave");
        check(!roofs.trimUnderAMadeEave(1, 0, 1, false, true, false), "a trim on the square's north edge is on no edge the eave is made on");
        check(roofs.trimUnderAMadeEave(1, 1, 1, false, false, true), "a corner piece past the south end of the eave goes with it");
        check(!roofs.trimUnderAMadeEave(1, 1, 1, false, false, false), "a trim hung on no edge that is no corner piece stays");
        // A flat roof past the slope's foot: the slope goes on into it, no eave is made, and its trims stay.
        TestMap flat = new TestMap();
        flat.roofTile(fitted(new RoofFit.Tile(0, 0, 1, RoofFit.ALONG_X, true, 1f + THIRD, 1f)), flat.painted);
        flat.roofTile(fitted(new RoofFit.Tile(1, 0, 1, RoofFit.LEVEL, true, 1f, 1f)), flat.painted);
        check(!flat.build.roofBuilder.trimUnderAMadeEave(1, 0, 1, true, false, false), "with a flat roof past the slope's foot there is no eave, and the trim stays");
        check(!flat.build.roofBuilder.trimUnderAMadeEave(2, 0, 1, true, false, false), "nor is a trim past the flat roof the slope's");
    }

    /**
     * A house over x 0..4 and y 0..5, as at 13616..13620 x 3673..3678, its ridge along x at 2 on the line y 3 and the map's
     * south slope three rows down to the eave at 1. Past its east gable a lower wing over x 5..6 and y 1..4 has its ridge at
     * 1 + 2/3 on the same line. The square north of the wing, beside the main roof's north wall, is no notch in that wall:
     * the main roof's north side was hipped down to it, and the main gable stood bare over the hip.
     */
    private static void lowerWingPastAGableIsNoNotchInTheMainEave() {
        RoofFit.Site site = standing((x, y) -> x >= 0 && x <= 4 && y >= 0 && y <= 5 || x >= 5 && x <= 6 && y >= 1 && y <= 4);
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int x = 0; x <= 6; x++) {
            float ridge = x <= 4 ? 2f : 1f + 2 * THIRD;
            for (int row = 0; row < (x <= 4 ? 3 : 2); row++) {
                roof.add(new RoofFit.Tile(x, 3 + row, 1, RoofFit.ALONG_Y, true, ridge - row * THIRD, ridge - (row + 1) * THIRD));
            }
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, site);
        for (int y = 0; y <= 2; y++) {
            RoofFit.Tile face = madeAt(made, 4, y);
            check(face != null && !face.shaped && face.fitAxis == RoofFit.ALONG_Y && near(face.fitLow, 1f + y * THIRD) && near(face.fitHigh, 1f + (y + 1) * THIRD),
                    "the main roof's north side climbs from its wall up to the gable at 4, " + y + ": " + said(face));
        }
        RoofFit.Tile wing = madeAt(made, 5, 2);
        check(wing != null && !wing.shaped && near(wing.fitHigh, 1f + 2 * THIRD), "the wing's north side keeps to its own ridge at 5, 2: " + said(wing));
    }

    /**
     * A hip roof over x 0..5 and y 0..8, as the picnic shelter at 12015..12020, 7364..7372 has it: the map lays the east face
     * from a ridge at x 3 and the south face from its end at y 6, and along both hips the tiles are cut to triangles (roofs_01_56..61).
     * The made north and west faces are the hip's: every made corner lies at a third of a level a square in from the nearest wall,
     * up to the ridge at 2, and every square the map leaves open is roofed. The east face's cut rows made a flat along the north
     * eave, and the south face's cut row a steep face standing over the ridge.
     */
    private static void hipRoofWithCutHipsGetsItsFarFacesAsAHip() {
        String[] map = {
            "0 8 2 1.333 1 0", "1 7 2 1.667 1.333 0", "1 8 2 1.333 1 -1", "2 6 2 2 1.667 0", "2 7 2 1.667 1.333 -1", "2 8 2 1.333 1 -1",
            "3 2 1 2 1.667 0", "3 3 1 2 1.667 -1", "3 4 1 2 1.667 -1", "3 5 1 2 1.667 -1", "3 6 -2 2 1.5 -1", "3 7 2 1.667 1.333 -1", "3 8 2 1.333 1 -1",
            "4 1 1 1.51 1 -1", "4 2 1 1.667 1.333 -1", "4 3 1 1.667 1.333 -1", "4 4 1 1.667 1.333 -1", "4 5 1 1.667 1.333 -1", "4 6 1 1.667 1.333 -1",
            "4 7 3 1.667 1.333 -1", "4 8 2 1.333 1 -1", "5 0 1 1.333 1 0", "5 1 1 1.333 1 -1", "5 2 1 1.333 1 -1", "5 3 1 1.333 1 -1", "5 4 1 1.333 1 -1",
            "5 5 1 1.333 1 -1", "5 6 1 1.333 1 -1", "5 7 1 1.333 1 -1", "5 8 3 1.333 1 -1"};
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (String one : map) {
            String[] f = one.split(" ");
            int axis = Integer.parseInt(f[2]);
            RoofFit.Tile tile = new RoofFit.Tile(Integer.parseInt(f[0]), Integer.parseInt(f[1]), 1, Math.abs(axis), axis > 0, Float.parseFloat(f[3]), Float.parseFloat(f[4]));
            tile.cutCorner = Integer.parseInt(f[5]);
            roof.add(tile);
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, standing((x, y) -> x >= 0 && x <= 5 && y >= 0 && y <= 8));
        for (RoofFit.Tile tile : made) {
            for (int corner = 0; corner < 4; corner++) {
                int x = tile.x + (int) RoofFit.cornerX(corner), y = tile.y + (int) RoofFit.cornerY(corner);
                float hip = Math.min(2f, 1f + Math.min(Math.min(x, 6 - x), Math.min(y, 9 - y)) * THIRD);
                check(near(tile.corners[corner], hip), "the made far side lies on the hip at " + x + ", " + y + ": " + said(tile));
            }
        }
        HashMap<Long, RoofFit.Tile> over = bySquare(roof, made);
        for (int x = 0; x <= 5; x++) {
            for (int y = 0; y <= 8; y++) {
                check(over.containsKey(RoofFit.key(x, y, 0)), "the hip roof is closed over " + x + ", " + y);
            }
        }
    }

    private static void roofHippedAtOneEndAndGabledAtTheOtherGetsItsFarFaces() {
        String[] map = {
            "0 8 2 1.333 1 0", "1 7 2 1.667 1.333 0", "1 8 2 1.333 1 -1", "2 6 2 2 1.667 0", "2 7 2 1.667 1.333 -1", "2 8 2 1.333 1 -1",
            "3 0 1 2 1.667 -1", "3 1 1 2 1.667 -1", "3 2 1 2 1.667 -1", "3 3 1 2 1.667 -1", "3 4 1 2 1.667 -1", "3 5 1 2 1.667 -1", "3 6 -2 2 1.5 -1",
            "3 7 2 1.667 1.333 -1", "3 8 2 1.333 1 -1",
            "4 0 1 1.667 1.333 -1", "4 1 1 1.667 1.333 -1", "4 2 1 1.667 1.333 -1", "4 3 1 1.667 1.333 -1", "4 4 1 1.667 1.333 -1", "4 5 1 1.667 1.333 -1",
            "4 6 1 1.667 1.333 -1", "4 7 3 1.667 1.333 -1", "4 8 2 1.333 1 -1", "5 0 1 1.333 1 -1", "5 1 1 1.333 1 -1", "5 2 1 1.333 1 -1", "5 3 1 1.333 1 -1",
            "5 4 1 1.333 1 -1", "5 5 1 1.333 1 -1", "5 6 1 1.333 1 -1", "5 7 1 1.333 1 -1", "5 8 3 1.333 1 -1"};
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (String one : map) {
            String[] f = one.split(" ");
            int axis = Integer.parseInt(f[2]);
            RoofFit.Tile tile = new RoofFit.Tile(Integer.parseInt(f[0]), Integer.parseInt(f[1]), 1, Math.abs(axis), axis > 0, Float.parseFloat(f[3]), Float.parseFloat(f[4]));
            tile.cutCorner = Integer.parseInt(f[5]);
            roof.add(tile);
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, standing((x, y) -> x >= 0 && x <= 5 && y >= 0 && y <= 8));
        for (RoofFit.Tile tile : made) {
            for (int corner = 0; corner < 4; corner++) {
                int x = tile.x + (int) RoofFit.cornerX(corner), y = tile.y + (int) RoofFit.cornerY(corner);
                float hip = Math.min(2f, 1f + Math.min(Math.min(x, 6 - x), 9 - y) * THIRD);
                check(near(tile.corners[corner], hip), "the made far side lies on the roof at " + x + ", " + y + ": " + said(tile));
            }
        }
        HashMap<Long, RoofFit.Tile> over = bySquare(roof, made);
        for (int x = 0; x <= 5; x++) {
            for (int y = 0; y <= 8; y++) {
                check(over.containsKey(RoofFit.key(x, y, 0)), "the roof is closed over " + x + ", " + y);
            }
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /**
     * The shop at 7716..7722, 14708..14714: a west wing ridged along x 2, an east wing ridged along x 5 and hipped at its north
     * end, and a cross ridge along y 3 between them, all falling a third of a level a square to an eave at 1. Where they meet
     * the roof is whole, the highest of the three at each corner, with no pit between the wings.
     */
    private static void wingsSideBySideUnderACrossRidgeLeaveNoPit() {
        String[] map = {
            "2 0 1 1.667 1.333 -1", "2 1 1 1.667 1.333 -1", "2 2 1 1.667 1.333 -1", "2 3 -2 1.823 1.333 -1", "2 4 1 1.667 1.333 -1",
            "2 5 1 1.667 1.333 -1", "2 6 1 1.667 1.333 -1", "3 0 1 1.333 1 -1", "3 1 1 1.333 1 -1", "3 2 1 1.333 1 -1", "3 3 2 1.667 1.333 -1",
            "3 4 4 1.333 1 -1", "3 5 1 1.333 1 -1", "3 6 1 1.333 1 -1", "4 3 2 1.667 1.333 -1", "4 4 2 1.333 1 -1", "5 1 1 1.667 1.333 0",
            "5 2 1 1.667 1.333 -1", "5 3 3 1.667 1.333 -1", "5 4 2 1.333 1 -1", "6 0 1 1.333 1 0", "6 1 1 1.333 1 -1", "6 2 1 1.333 1 -1",
            "6 3 1 1.333 1 -1", "6 4 3 1.333 1 -1"};
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (String one : map) {
            String[] f = one.split(" ");
            int axis = Integer.parseInt(f[2]);
            RoofFit.Tile tile = new RoofFit.Tile(Integer.parseInt(f[0]), Integer.parseInt(f[1]), 1, Math.abs(axis), axis > 0, Float.parseFloat(f[3]), Float.parseFloat(f[4]));
            tile.cutCorner = Integer.parseInt(f[5]);
            tile.gabled = tile.y == 6;
            roof.add(tile);
        }
        RoofFit.fit(roof);
        List<RoofFit.Tile> all = new ArrayList<>(roof);
        List<RoofFit.Tile> made = RoofFit.backs(roof, standing((x, y) -> x >= 0 && y >= 0 && (x <= 6 && y <= 4 || x <= 3 && y <= 6)));
        RoofFit.peaks(roof, made);
        all.addAll(made);
        all.addAll(RoofFit.valleys(roof, made));
        // The roof over the middle, where the wings and the cross ridge meet: the highest of the three roofs at each corner.
        float[][] expected = {{3, 1, 4, 4, 4, 4}, {3, 2, 4, 4, 5, 5}, {4, 2, 4, 5, 5, 5}, {4, 3, 5, 5, 4, 4}};
        float[] corners = new float[4];
        for (float[] square : expected) {
            float[] top = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            for (RoofFit.Tile tile : all) {
                if (tile.x != (int) square[0] || tile.y != (int) square[1] || tile.fitting) {
                    continue;
                }
                if (tile.shaped || tile.carried) {
                    System.arraycopy(tile.corners, 0, corners, 0, 4);
                } else {
                    RoofFit.cornersOf(tile.fitAxis, tile.fitLow, tile.fitHigh, corners);
                }
                for (int corner = 0; corner < 4; corner++) {
                    top[corner] = Math.max(top[corner], corners[corner]);
                }
            }
            for (int corner = 0; corner < 4; corner++) {
                check(near(top[corner], square[2 + corner] * THIRD), "the roof over " + (int) square[0] + ", " + (int) square[1] + " corner " + corner + " is at " + top[corner]);
            }
        }
        // The valley between the wings dips to 1 + 1/6 along x 3.5 and the cross ridge's far side comes down into it, so
        // the roof at the middle of 3, 1's south edge is as high as the far side there.
        float middle = Float.NEGATIVE_INFINITY;
        for (RoofFit.Tile tile : all) {
            if (tile.x == 3 && tile.y == 1 && !tile.fitting && !tile.shaped) {
                if (!tile.carried) {
                    RoofFit.cornersOf(tile.fitAxis, tile.fitLow, tile.fitHigh, corners);
                } else {
                    System.arraycopy(tile.corners, 0, corners, 0, 4);
                }
                middle = Math.max(middle, corners[0] + (corners[1] - corners[0]) * 0.5f + (corners[3] - corners[0]));
            }
        }
        check(near(middle, 1f + THIRD), "the roof at the middle of 3, 1's south edge is at " + middle);
        // Nothing is carried onto a square twice.
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                RoofFit.Tile a = all.get(i), b = all.get(j);
                boolean same = true;
                for (int c = 0; c < 4; c++) same &= Math.abs(a.corners[c] - b.corners[c]) <= RoofFit.SAME;
                check(!a.carried || !b.carried || a.x != b.x || a.y != b.y || !same,
                        "one carried tile with the same corners on " + a.x + ", " + a.y);
            }
        }
    }

    /**
     * A house's east slope over x 0..1 and y 0..4, as at 4091..4092 x 14529..14533, with a dormer standing out of its
     * lower row over y 1..3: the map has the dormer's ridge as a half tile at 1, 2 and its south face as the valley where
     * it comes down onto the slope at 1, 3, and leaves the slope going on under the dormer at 1, 1. The dormer's ridge is
     * made, and the slope at 1, 1 is folded to the dormer's north face where that stands higher. The same roof turned onto a
     * south slope, with the ridge an east half, does the same.
     */
    private static void dormerOnASlopeGetsItsRidgeAndItsFarFace() {
        for (boolean turned : new boolean[] {false, true}) {
            List<RoofFit.Tile> roof = new ArrayList<>();
            int along = turned ? RoofFit.ALONG_Y : RoofFit.ALONG_X;
            for (int y = 0; y <= 4; y++) {
                roof.add(new RoofFit.Tile(turned ? y : 0, turned ? 0 : y, 1, along, true, 1f + 2 * THIRD, 1f + THIRD));
                if (y == 2) {
                    RoofFit.Tile ridge = new RoofFit.Tile(turned ? y : 1, turned ? 1 : y, 1, RoofFit.LEVEL, true, 1f + THIRD, 1f + THIRD);
                    ridge.fitting = true;
                    ridge.half = turned ? RoofFit.EAST_HALF : RoofFit.SOUTH_HALF;
                    ridge.halfEdge = 1f + THIRD;
                    roof.add(ridge);
                } else if (y == 3) {
                    RoofFit.Tile valley = new RoofFit.Tile(turned ? y : 1, turned ? 1 : y, 1, RoofFit.SOUTH_EAST_ODD, true, 1f + THIRD, 1f);
                    valley.corners[0] = valley.corners[1] = valley.corners[3] = 1f + THIRD;
                    valley.corners[2] = 1f;
                    valley.crease = RoofFit.NW_SE;
                    valley.shaped = true;
                    valley.own = 3;
                    roof.add(valley);
                } else {
                    roof.add(new RoofFit.Tile(turned ? y : 1, turned ? 1 : y, 1, along, true, 1f + THIRD, 1f));
                }
            }
            List<RoofFit.Tile> made = new ArrayList<>();
            RoofFit.peaks(roof, made);
            RoofFit.Tile ridge = turned ? madeAt(made, 2, 1) : madeAt(made, 1, 2);
            check(ridge != null && ridge.fitAxis == RoofFit.LEVEL && near(ridge.fitLow, 1f + THIRD) && near(ridge.peak, THIRD / 2f)
                    && ridge.peakAxis == (turned ? RoofFit.ALONG_X : RoofFit.ALONG_Y), "the dormer's ridge is made, turned " + turned + ": " + said(ridge));
            RoofFit.Tile behind = null;
            for (RoofFit.Tile tile : roof) {
                behind = tile.x == 1 && tile.y == 1 ? tile : behind;
            }
            float[] folded = turned ? new float[] {1f + THIRD, 1f + THIRD, 1f + THIRD, 1f} : new float[] {1f + THIRD, 1f, 1f + THIRD, 1f + THIRD};
            boolean asFolded = behind.shaped && behind.crease == RoofFit.NE_SW && behind.own == 2;
            for (int corner = 0; corner < 4; corner++) {
                asFolded &= near(behind.corners[corner], folded[corner]);
            }
            check(asFolded, "the slope behind the dormer is folded up to its face, turned " + turned + ": " + Arrays.toString(behind.corners) + " crease "
                    + behind.crease + " own " + behind.own);
        }
    }

    /**
     * A room on level 1 whose floor on a square is a roof's sheet laid as a floor, as at 2062..2070 x 7741: the foot of the
     * house's roof, which the map puts in the room upstairs. It is out under the sky and gets no plaster ceiling. A square of
     * the room with a room's floor gets its ceiling.
     */
    private static void roomSquareFlooredWithARoofSheetHasNoCeiling() {
        for (String floor : new String[] {"roofs_02_22", "floors_interior_tilesandwood_01_47"}) {
            TestMap map = new TestMap();
            map.cell.square(0, 0, 1).floor(map.sprite(floor, IsoObjectType.normal));
            map.cell.square(0, 0, 1).setRoomID(1000L);
            map.build.ceilingBuilder.addRoomCeiling(0, 0, 1, 0, 0, false);
            int triangles = TestMap.plain().triangles();
            check(floor.startsWith("roofs_") ? triangles == 0 : triangles == 4, "a room's square floored with " + floor + " has " + triangles + " triangles of ceiling");
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-3f;
    }
}

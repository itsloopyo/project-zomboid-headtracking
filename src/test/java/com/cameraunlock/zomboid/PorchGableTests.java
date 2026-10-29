package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import zombie.core.properties.PropertyContainer;
import zombie.iso.BuildingDef;
import zombie.iso.areas.IsoBuilding;
import zombie.iso.sprite.IsoSprite;

/** Tests of the gables made under a roof's west and north ends: over a porch, and below a slope laid a level up. */
final class PorchGableTests {
    private PorchGableTests() {}

    static void run() {
        Checks.run("aRoofOverAPorchIsClosedAtItsEnd", PorchGableTests::aRoofOverAPorchIsClosedAtItsEnd);
        Checks.run("aPorchRoofIsClosedOnlyWhereItEnds", PorchGableTests::aPorchRoofIsClosedOnlyWhereItEnds);
        Checks.run("aSlopeLaidALevelUpIsClosedDownToTheWalls", PorchGableTests::aSlopeLaidALevelUpIsClosedDownToTheWalls);
    }

    /**
     * A slope on level 1 falling east over (0, 0) and (0, 1), the house under (0, 1) only and the
     * porch under (0, 0): as at the house at 12279..12292, 3811..3820, whose roof runs on north
     * over its porch on row 3810.
     */
    private static TestMap porch(boolean house, boolean roofOverTheGap, int houseAt) {
        TestMap map = new TestMap();
        for (int y = 0; y <= houseAt; y++) {
            if (y == 0 || roofOverTheGap) {
                slope(map, 0, y);
            }
        }
        if (house) {
            inHouse(map, 0, houseAt);
        }
        return map;
    }

    private static final IsoSprite DECK = new IsoSprite("floors_interior_tilesandwood_01_41", new PropertyContainer(Map.of()));
    private static final IsoSprite GRASS = new IsoSprite("blends_natural_01_16", new PropertyContainer(Map.of()));

    private static void slope(TestMap map, int x, int y) {
        RoofFit.Tile tile = new RoofFit.Tile(x, y, 1, RoofFit.ALONG_X, true, 1f + RoofFit.PITCH, 1f);
        RoofFit.fit(new ArrayList<>(List.of(tile)));
        map.roofTile(tile, map.painted);
        map.cell.square(x, y, 0).floor(DECK);
    }

    private static void inHouse(TestMap map, int x, int y) {
        map.cell.square(x, y, 0).setRoomID(1L);
        map.cell.square(x, y, 0).building(new IsoBuilding(new BuildingDef(1L, 0, 0, 2, 5)));
    }

    private static int gables(TestMap map) {
        return gables(map, 0);
    }

    private static int gables(TestMap map, int x) {
        map.build.roofBuilder.addRoof(new SpriteGeometry.Mesh(), map.painted, x, 0, 1);
        return map.build.gablesBare + map.build.gablesPainted + map.build.gablesFaced;
    }

    /**
     * A porch roof two squares wide, the house under the row south of it: the slope on (1, 0)
     * stands high along its west edge, and with the roof going on west of it there is no end
     * there. Closed, every edge of a porch roof between its tiles was a wall across the porch.
     */
    private static void aPorchRoofIsClosedOnlyWhereItEnds() {
        TestMap alone = new TestMap();
        slope(alone, 1, 0);
        slope(alone, 1, 1);
        inHouse(alone, 1, 1);
        check(gables(alone, 1) == 2, "a porch roof one square wide is closed at its north end and along its high west edge");
        TestMap wide = new TestMap();
        for (int x = 0; x <= 1; x++) {
            slope(wide, x, 0);
            slope(wide, x, 1);
            inHouse(wide, x, 1);
        }
        check(gables(wide, 1) == 1, "with the roof going on west of it, only its north end is closed");
    }

    private static void aRoofOverAPorchIsClosedAtItsEnd() {
        check(gables(porch(true, true, 1)) > 0, "the end of a roof over a porch, the house beside it under the same roof, is closed");
        check(gables(porch(true, true, 3)) > 0, "so is one three squares out from the house");
        check(gables(porch(false, true, 1)) == 0, "a roof with no building under it nearby stands on its own, its end open");
        check(gables(porch(true, false, 2)) == 0, "as does one whose roof stops short of the house");
        check(gables(porch(true, true, 4)) == 0, "or that runs further out from it than a porch");
        TestMap overhang = porch(true, true, 1);
        overhang.cell.square(0, 0, 0).floor(GRASS);
        check(gables(overhang) == 0, "a roof run on a square past its wall over the grass is an eave, its gable the wall's");
        // IsoGridSquare.addFloor lays a floor by the name it is given, and for a name no tile has the sprite it makes has none.
        TestMap unnamed = porch(true, true, 1);
        unnamed.cell.square(0, 0, 0).floor(new IsoSprite(null, new PropertyContainer(Map.of())));
        check(gables(unnamed) == 0, "so is one over a floor whose sprite has no name, which is no porch's deck");
    }

    /**
     * A slope on level 3 over a house whose walls stand on levels 0 and 1, as the barn's top rows
     * at 7648, 9352..9353: the loft on level 2 has no wall at the end, and was open there.
     */
    private static TestMap raised(int wallsUpTo, boolean roofBelow) {
        TestMap map = new TestMap();
        RoofFit.Tile tile = new RoofFit.Tile(0, 0, 3, RoofFit.ALONG_X, true, 3f + RoofFit.PITCH, 3f);
        RoofFit.fit(new ArrayList<>(List.of(tile)));
        map.roofTile(tile, map.painted);
        inHouse(map, 0, 0);
        for (int level = 0; level <= wallsUpTo; level++) {
            map.wall(0, 0, level, false);
            map.wall(0, 0, level, true);
        }
        map.cell.square(0, 0, 2);
        if (roofBelow) {
            RoofFit.Tile lower = new RoofFit.Tile(0, 0, 2, RoofFit.ALONG_X, true, 2f + RoofFit.PITCH, 2f);
            RoofFit.fit(new ArrayList<>(List.of(lower)));
            map.roofTile(lower, map.painted);
        }
        return map;
    }

    private static int raisedGables(TestMap map) {
        map.build.roofBuilder.addRoof(new SpriteGeometry.Mesh(), map.painted, 0, 0, 3);
        return map.build.gablesBare + map.build.gablesPainted + map.build.gablesFaced;
    }

    private static void aSlopeLaidALevelUpIsClosedDownToTheWalls() {
        check(raisedGables(raised(1, false)) == 4, "the west and the north end are closed on the roof's level and on the loft's below it");
        check(raisedGables(raised(2, false)) == 2, "with the loft walled at its end only the roof's own level is closed");
        check(raisedGables(raised(1, true)) == 2, "a roof of its own on the loft has its own gable");
    }
}

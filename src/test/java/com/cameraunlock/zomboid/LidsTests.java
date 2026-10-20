package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.Map;
import zombie.core.properties.PropertyContainer;
import zombie.iso.BuildingDef;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.areas.IsoBuilding;
import zombie.iso.sprite.IsoSprite;

/** Which storeys of a tower of six floors on a 4 by 4 box the eye is held from: Lids. */
final class LidsTests {
    private LidsTests() {}

    static void run() {
        Checks.run("everyFloorFlooredOverHoldsFromTwoUpAndThreeDown", LidsTests::everyFloorFlooredOverHoldsFromTwoUpAndThreeDown);
        Checks.run("aStairwellIsLetThrough", LidsTests::aStairwellIsLetThrough);
        Checks.run("aHallThreeFloorsHighHoldsOverItsTop", LidsTests::aHallThreeFloorsHighHoldsOverItsTop);
        Checks.run("aLedgeOverTheHallSeesDownItsWell", LidsTests::aLedgeOverTheHallSeesDownItsWell);
    }

    private static final IsoSprite FLOOR = new IsoSprite("floors_interior_tilesandwood_01_47", new PropertyContainer(Map.of()));
    private static final IsoSprite STAIRS = new IsoSprite("fixtures_stairs_01_0", new PropertyContainer(Map.of()), IsoObjectType.stairsBW, null);
    private static final int TOP = 5;

    /** Six storeys of rooms, each floored, the roof's floor on the level over the top one. */
    private static IsoCell tower(BuildingDef def) {
        IsoCell cell = new IsoCell();
        IsoBuilding building = new IsoBuilding(def);
        for (int z = 0; z <= TOP + 1; z++) {
            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    IsoGridSquare square = cell.square(x, y, z);
                    square.floor(FLOOR);
                    if (z <= TOP) {
                        square.setRoomID(1000L + z);
                        square.building(building);
                    }
                }
            }
        }
        return cell;
    }

    private static void everyFloorFlooredOverHoldsFromTwoUpAndThreeDown() {
        BuildingDef def = new BuildingDef(7L, 0, 0, 4, 4);
        Lids.Held held = Lids.of(tower(def), def, 3);
        check(held.from() == 5 && held.under() == 0 && held.whole(), "from the fourth floor the sixth up and the first down are held: " + held);
    }

    private static void aStairwellIsLetThrough() {
        BuildingDef def = new BuildingDef(7L, 0, 0, 4, 4);
        IsoCell cell = tower(def);
        for (int z = 0; z < TOP; z++) {
            cell.getGridSquare(1, 1, z).add(STAIRS);
            cell.square(1, 1, z + 1).unfloor();
        }
        Lids.Held held = Lids.of(cell, def, 3);
        check(held.from() == 5 && held.under() == 0, "a flight of stairs up through every floor holds what a floored tower holds: " + held);
    }

    /** Floors 1 to 3 have no floor over a 2 by 2 hall, which is open from the ground floor's up to the fourth's. */
    private static IsoCell hall(BuildingDef def) {
        IsoCell cell = tower(def);
        for (int z = 1; z <= 3; z++) {
            for (int x = 1; x <= 2; x++) {
                for (int y = 1; y <= 2; y++) {
                    cell.getGridSquare(x, y, z).unfloor();
                }
            }
        }
        return cell;
    }

    private static void aHallThreeFloorsHighHoldsOverItsTop() {
        BuildingDef def = new BuildingDef(7L, 0, 0, 4, 4);
        Lids.Held held = Lids.of(hall(def), def, 0);
        check(held.from() == 5, "from the floor of the hall the storeys up to the one that roofs it, and one more, are drawn: " + held);
    }

    private static void aLedgeOverTheHallSeesDownItsWell() {
        BuildingDef def = new BuildingDef(7L, 0, 0, 4, 4);
        Lids.Held held = Lids.of(hall(def), def, 2);
        check(held.from() == 5 && held.under() == -3, "from a ledge two floors up, nothing between the hall's floor and its roof is held: " + held);
    }
}

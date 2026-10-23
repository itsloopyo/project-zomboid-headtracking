package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.Map;
import zombie.core.properties.PropertyContainer;
import zombie.iso.BuildingDef;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.areas.IsoBuilding;
import zombie.iso.sprite.IsoSprite;

/** Which squares in no room are rows left out of a tall hall's upper room, and are the hall's: TallHalls. */
final class TallHallsTests {
    private TallHallsTests() {}

    static void run() {
        Checks.run("aRowLeftOutOverTheHallIsTheHalls", TallHallsTests::aRowLeftOutOverTheHallIsTheHalls);
        Checks.run("aRowLeftOutTwoFloorsUpIsTheUpperRooms", TallHallsTests::aRowLeftOutTwoFloorsUpIsTheUpperRooms);
        Checks.run("aRowLeftOutOverAnotherIsTheHalls", TallHallsTests::aRowLeftOutOverAnotherIsTheHalls);
        Checks.run("theAirBesideTheHallIsNot", TallHallsTests::theAirBesideTheHallIsNot);
        Checks.run("aSquareOfTheUpperRoomIsNot", TallHallsTests::aSquareOfTheUpperRoomIsNot);
        Checks.run("aFlooredSquareIsNot", TallHallsTests::aFlooredSquareIsNot);
    }

    private static final IsoSprite FLOOR = new IsoSprite("floors_interior_tilesandwood_01_47", new PropertyContainer(Map.of()));
    private static final long HALL = 1000L, UPPER = 1001L, TOP = 1002L, BUILDING = 7L;

    /**
     * A hall 6 by 6 on the ground floor. On the floor over it its upper room, floorless, with
     * row y = 2 and column x = 4 left out. On the floor over that a floorless room in rows 2 to
     * 3, with row 4 and column x = 4 left out, and the rest of that level floored, as the map
     * floors the level over a hall's upper room. Floored over all of it on the level above.
     * Open ground in no room round the hall on the ground floor, and air over it.
     */
    private static IsoCell hall() {
        IsoCell cell = new IsoCell();
        IsoBuilding building = new IsoBuilding(new BuildingDef(BUILDING, 0, 0, 6, 6));
        for (int x = -1; x <= 6; x++) {
            for (int y = -1; y <= 6; y++) {
                boolean inside = x >= 0 && x < 6 && y >= 0 && y < 6;
                IsoGridSquare ground = cell.square(x, y, 0);
                ground.floor(FLOOR);
                if (inside) {
                    ground.setRoomID(HALL);
                    ground.building(building);
                }
                IsoGridSquare upper = cell.square(x, y, 1);
                if (inside && y != 2 && x != 4) {
                    upper.setRoomID(UPPER);
                    upper.building(building);
                }
                IsoGridSquare top = cell.square(x, y, 2);
                if (inside && (y == 2 || y == 3) && x != 4) {
                    top.setRoomID(TOP);
                    top.building(building);
                } else if (inside && y != 4 && x != 4) {
                    top.floor(FLOOR);
                }
                cell.square(x, y, 3).floor(FLOOR);
            }
        }
        return cell;
    }

    private static long roomUnder(IsoCell cell, int x, int y, int z) {
        IsoGridSquare hall = TallHalls.under(cell, x, y, z);
        return hall == null ? RoomSight.OUTSIDE : hall.getRoomID();
    }

    private static void aRowLeftOutOverTheHallIsTheHalls() {
        long room = roomUnder(hall(), 3, 2, 1);
        check(room == HALL, "the row left out of the upper room is the hall's: " + room);
    }

    private static void aRowLeftOutTwoFloorsUpIsTheUpperRooms() {
        long room = roomUnder(hall(), 3, 4, 2);
        check(room == UPPER, "the row left out on the floor over that, over the upper room, is the upper room's: " + room);
    }

    private static void aRowLeftOutOverAnotherIsTheHalls() {
        long room = roomUnder(hall(), 4, 3, 2);
        check(room == HALL, "a column left out on both floors over the hall is the hall's all the way up: " + room);
    }

    private static void theAirBesideTheHallIsNot() {
        long room = roomUnder(hall(), -1, 3, 1);
        check(room == RoomSight.OUTSIDE, "the air beside the hall's wall, over open ground, is no row of the hall: " + room);
    }

    private static void aSquareOfTheUpperRoomIsNot() {
        long room = roomUnder(hall(), 3, 4, 1);
        check(room == RoomSight.OUTSIDE, "a square of the upper room itself is no row left out: " + room);
    }

    private static void aFlooredSquareIsNot() {
        long room = roomUnder(hall(), 3, 0, 2);
        check(room == RoomSight.OUTSIDE, "a square the map floors over the upper room is no row left out: " + room);
    }
}

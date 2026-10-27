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

/** Which squares in no room are rows left out of a tall hall's upper room, and are the hall's: TallHalls, and RoomMap reading them. */
final class TallHallsTests {
    private TallHallsTests() {}

    static void run() {
        Checks.run("aRowLeftOutOverTheHallIsTheHalls", TallHallsTests::aRowLeftOutOverTheHallIsTheHalls);
        Checks.run("aRowLeftOutTwoFloorsUpIsTheUpperRooms", TallHallsTests::aRowLeftOutTwoFloorsUpIsTheUpperRooms);
        Checks.run("aRowLeftOutOverAnotherIsTheHalls", TallHallsTests::aRowLeftOutOverAnotherIsTheHalls);
        Checks.run("theAirBesideTheHallIsNot", TallHallsTests::theAirBesideTheHallIsNot);
        Checks.run("aSquareOfTheUpperRoomIsNot", TallHallsTests::aSquareOfTheUpperRoomIsNot);
        Checks.run("aFlooredSquareIsNot", TallHallsTests::aFlooredSquareIsNot);
        Checks.run("theSquaresBesideAStairwellAreNoHall", TallHallsTests::theSquaresBesideAStairwellAreNoHall);
        Checks.run("aStairwellIsTheRoomUpstairs", TallHallsTests::aStairwellIsTheRoomUpstairs);
        Checks.run("stairsInATallHallAreTheHalls", TallHallsTests::stairsInATallHallAreTheHalls);
        Checks.run("theRoomMapPutsARowLeftOutInTheHall",TallHallsTests::theRoomMapPutsARowLeftOutInTheHall);
        Checks.run("theRoomMapPutsTheUpperPartOfAHallInTheHall", TallHallsTests::theRoomMapPutsTheUpperPartOfAHallInTheHall);
        Checks.run("eachSquareIsDrawnAsTheRoomSightWalksItAs", TallHallsTests::eachSquareIsDrawnAsTheRoomSightWalksItAs);
        Checks.run("theUpperRoomIsLitAsTheHallsFloor", TallHallsTests::theUpperRoomIsLitAsTheHallsFloor);
        Checks.run("aRowLeftOutIsLitAsTheHallsFloor", TallHallsTests::aRowLeftOutIsLitAsTheHallsFloor);
        Checks.run("noOtherSquareIsLitAsAHall", TallHallsTests::noOtherSquareIsLitAsAHall);
        Checks.run("aRowLeftOutIsClosedWithPlasterToTheOpenAir", TallHallsTests::aRowLeftOutIsClosedWithPlasterToTheOpenAir);
        Checks.run("aRowLeftOutUnderARoofIsLeftToItsGables", TallHallsTests::aRowLeftOutUnderARoofIsLeftToItsGables);
        Checks.run("wallGrimeGathersOnlyAtAFloorAndUnderACeiling", TallHallsTests::wallGrimeGathersOnlyAtAFloorAndUnderACeiling);
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

    private static void wallGrimeGathersOnlyAtAFloorAndUnderACeiling() {
        IsoCell cell = hall();
        check(WallBuilder.grimeFloored(cell, 1, 1, 0) && !WallBuilder.grimeCeiled(cell, 1, 1, 0, false),
                "grime in a tall hall gathers along its floor, and dusts nothing under the floor of its upper part, where there is none");
        check(!WallBuilder.grimeFloored(cell, 1, 1, 1) && WallBuilder.grimeCeiled(cell, 1, 1, 1, false),
                "in the hall's upper part it gathers along no floor, and dusts under the ceiling over it");
        check(!WallBuilder.grimeFloored(cell, 1, 2, 2), "nor in a floorless room on the floor over that");
        check(WallBuilder.grimeFloored(cell, -1, -1, 0) && !WallBuilder.grimeCeiled(cell, -1, -1, 0, true),
                "out of doors it gathers along the ground, under no ceiling");
        check(!WallBuilder.grimeFloored(cell, -1, -1, 1) && !WallBuilder.grimeFloored(cell, 9, 9, 0),
                "and along no floor over a lower storey, or where there is no square: there it was a stripe at every floor");
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

    /**
     * A house 5 by 4, stairs going up along x = 1 from y = 2 to y = 0, and over it a room on rows 0 to 2,
     * floored but where the stairs come up through it. Row 3 over the house has no floor and is in no room, as
     * over a hall under a lean-to roof. It is beside the stairwell, which is in a room and floorless, and no hall.
     */
    private static void theSquaresBesideAStairwellAreNoHall() {
        IsoCell cell = new IsoCell();
        IsoBuilding building = new IsoBuilding(new BuildingDef(BUILDING, 0, 0, 5, 4));
        IsoSprite stairs = new IsoSprite("fixtures_stairs_01_56", new PropertyContainer(Map.of()), IsoObjectType.stairsBN, null);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 4; y++) {
                IsoGridSquare ground = cell.square(x, y, 0);
                ground.floor(FLOOR);
                ground.setRoomID(HALL);
                ground.building(building);
                if (x == 1 && y < 3) {
                    ground.add(stairs);
                }
                IsoGridSquare upper = cell.square(x, y, 1);
                if (y < 3) {
                    upper.setRoomID(UPPER);
                    upper.building(building);
                    if (x != 1 || y == 0) {
                        upper.floor(FLOOR);
                    }
                }
            }
        }
        check(!TallHalls.besideHallOver(cell, 1, 3, 1) && !TallHalls.besideHallOver(cell, 2, 3, 1),
                "the row beside the stairwell is no row left out of a hall, so the room under it has its ceiling");
        check(roomUnder(cell, 2, 3, 1) == RoomSight.OUTSIDE, "and is no hall's: " + roomUnder(cell, 2, 3, 1));
        cell.getGridSquare(3, 2, 1).unfloor();
        check(TallHalls.besideHallOver(cell, 3, 3, 1), "beside a floorless square of the room with no stairs under it, it is the hall's still");
    }

    /**
     * The house of theSquaresBesideAStairwellAreNoHall, with the top of its stairwell (1, 2) left
     * in no room, as the map leaves it at a ranger station (4671,8600, level 1).
     */
    private static IsoCell stairwell() {
        IsoCell cell = new IsoCell();
        IsoBuilding building = new IsoBuilding(new BuildingDef(BUILDING, 0, 0, 5, 4));
        IsoSprite stairs = new IsoSprite("fixtures_stairs_01_56", new PropertyContainer(Map.of()), IsoObjectType.stairsBN, null);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 4; y++) {
                IsoGridSquare ground = cell.square(x, y, 0);
                ground.floor(FLOOR);
                ground.setRoomID(HALL);
                ground.building(building);
                if (x == 1 && y < 3) {
                    ground.add(stairs);
                }
                IsoGridSquare upper = cell.square(x, y, 1);
                if (y < 3 && !(x == 1 && y == 2)) {
                    upper.setRoomID(UPPER);
                    upper.building(building);
                }
                if (y < 3 && (x != 1 || y == 0)) {
                    upper.floor(FLOOR);
                }
            }
        }
        return cell;
    }

    private static void aStairwellIsTheRoomUpstairs() {
        IsoCell cell = stairwell();
        check(TallHalls.roomOf(cell, 1, 1, 1) == UPPER, "the stairwell in the room upstairs is that room's, not the hall's under it: " + TallHalls.roomOf(cell, 1, 1, 1));
        IsoGridSquare floor = TallHalls.floorOf(cell, 1, 2, 1);
        check(floor != null && floor.getRoomID() == UPPER && floor.getZ() == 1,
                "the top of the stairwell the map left in no room is drawn and lit as the room beside it upstairs: " + floor);
        RoomMap map = new RoomMap();
        map.begin(cell, null);
        for (int x = -1; x <= 5; x++) {
            for (int y = -1; y <= 4; y++) {
                check(TallHalls.roomOf(cell, x, y, 1) == map.region(x, y, 1),
                        "square " + x + "," + y + ",1 is drawn as " + TallHalls.roomOf(cell, x, y, 1) + " and walked as " + map.region(x, y, 1));
            }
        }
    }

    private static void stairsInATallHallAreTheHalls() {
        IsoCell cell = hall();
        cell.getGridSquare(1, 4, 0).add(new IsoSprite("fixtures_stairs_01_56", new PropertyContainer(Map.of()), IsoObjectType.stairsBN, null));
        IsoGridSquare floor = TallHalls.floorOf(cell, 1, 4, 1);
        check(floor == cell.getGridSquare(1, 4, 0), "the upper part of a tall hall over stairs in it is the hall's still: " + floor);
    }

    private static void theRoomMapPutsARowLeftOutInTheHall() {
        RoomMap map = new RoomMap();
        map.begin(hall(), null);
        check(map.region(3, 2, 1) == HALL && map.building(3, 2, 1) == BUILDING,
                "sight reads the row left out as the hall, in the hall's building: " + map.region(3, 2, 1) + " " + map.building(3, 2, 1));
        check(map.region(-1, 3, 1) == RoomSight.OUTSIDE && map.building(-1, 3, 1) == RoomSight.OUTSIDE, "and the air beside the hall as the open air");
    }

    /** The hall's ceiling hangs at the top of its upper part and is drawn with that part's room: sight has to reach it whenever it reaches the hall. */
    private static void theRoomMapPutsTheUpperPartOfAHallInTheHall() {
        IsoCell cell = hall();
        cell.getGridSquare(0, 0, 1).floor(FLOOR);
        RoomMap map = new RoomMap();
        map.begin(cell, null);
        check(map.region(3, 4, 1) == HALL && map.building(3, 4, 1) == BUILDING, "the upper room over the hall is the hall: " + map.region(3, 4, 1));
        check(map.region(3, 3, 2) == HALL, "and so is the room over that, two floors up: " + map.region(3, 3, 2));
        check(map.region(0, 0, 1) == UPPER, "a floored square of the upper room, a balcony over the hall, is the upper room: " + map.region(0, 0, 1));
        check(map.region(3, 0, 2) == RoomSight.OUTSIDE, "a floored square in no room over it is no room's: " + map.region(3, 0, 2));
    }

    /** What a square's walls, ceiling and contents are drawn with (TallHalls.roomOf) is the region the walk reaches it as, or they go missing while the hall is seen. */
    private static void eachSquareIsDrawnAsTheRoomSightWalksItAs() {
        IsoCell cell = hall();
        cell.getGridSquare(0, 0, 1).floor(FLOOR);
        RoomMap map = new RoomMap();
        map.begin(cell, null);
        for (int z = 0; z <= 3; z++) {
            for (int x = -2; x <= 7; x++) {
                for (int y = -2; y <= 7; y++) {
                    check(TallHalls.roomOf(cell, x, y, z) == map.region(x, y, z),
                            "square " + x + "," + y + "," + z + " is drawn as " + TallHalls.roomOf(cell, x, y, z) + " and walked as " + map.region(x, y, z));
                }
            }
        }
    }

    private static void theUpperRoomIsLitAsTheHallsFloor() {
        IsoCell cell = hall();
        IsoGridSquare floor = TallHalls.floorOf(cell, 3, 4, 1);
        check(floor == cell.getGridSquare(3, 4, 0), "a square of the upper room is lit as the hall's floor under it: " + floor);
        floor = TallHalls.floorOf(cell, 3, 3, 2);
        check(floor == cell.getGridSquare(3, 3, 0), "and one of the room two floors up, over the upper room, as the same floor: " + floor);
    }

    private static void aRowLeftOutIsLitAsTheHallsFloor() {
        IsoCell cell = hall();
        IsoGridSquare floor = TallHalls.floorOf(cell, 3, 2, 1);
        check(floor == cell.getGridSquare(3, 2, 0), "the row left out of the upper room is lit as the hall's floor: " + floor);
        floor = TallHalls.floorOf(cell, 3, 4, 2);
        check(floor == cell.getGridSquare(3, 4, 0), "and the row left out over the upper room, two floors up, as the same floor: " + floor);
        floor = TallHalls.floorOf(cell, 4, 3, 2);
        check(floor == cell.getGridSquare(4, 3, 0), "and the column left out on both floors: " + floor);
    }

    private static void noOtherSquareIsLitAsAHall() {
        IsoCell cell = hall();
        check(TallHalls.floorOf(cell, 3, 3, 0) == null, "the hall's floor is itself");
        check(TallHalls.floorOf(cell, 3, 0, 2) == null, "a floored square over the upper room is its own");
        check(TallHalls.floorOf(cell, -1, 3, 1) == null, "the air beside the hall is the open air");
        check(TallHalls.floorOf(cell, 3, 3, 3) == null, "the floor over everything is its own");
    }

    /**
     * A shop 4 by 4 on the ground floor, its upper room floorless over rows 1 to 3 and row 0
     * left out of it, the street north of it.
     */
    private static TestMap shopFront() {
        TestMap map = new TestMap();
        IsoBuilding building = new IsoBuilding(new BuildingDef(BUILDING, 0, 0, 4, 4));
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                IsoGridSquare ground = map.cell.square(x, y, 0);
                ground.floor(FLOOR);
                ground.setRoomID(HALL);
                ground.building(building);
                IsoGridSquare upper = map.cell.square(x, y, 1);
                if (y > 0) {
                    upper.setRoomID(UPPER);
                    upper.building(building);
                }
            }
        }
        return map;
    }

    private static void aRowLeftOutIsClosedWithPlasterToTheOpenAir() {
        TestMap map = shopFront();
        map.build.ceilingBuilder.addHallSides(1, 0, 1, 1, 0);
        check(TestMap.plain().triangles() == 2, "a row left out of the upper room with the open air north of it is closed with plaster: " + TestMap.plain().triangles() + " triangles");
    }

    private static void aRowLeftOutUnderARoofIsLeftToItsGables() {
        TestMap map = shopFront();
        map.roofSprite(1, 0, 1, "roofs_01_4");
        map.build.ceilingBuilder.addHallSides(1, 0, 1, 1, 0);
        check(TestMap.plain().triangles() == 0, "a row left out under a roof gets no plaster in its gables' plane: " + TestMap.plain().triangles() + " triangles");
    }
}

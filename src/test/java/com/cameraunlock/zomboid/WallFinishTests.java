package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import zombie.core.textures.Texture;
import zombie.iso.BuildingDef;
import zombie.iso.IsoObject;
import zombie.iso.areas.IsoBuilding;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteInstance;
import zombie.iso.SpriteDetails.IsoObjectType;

/** Tests of what a room's own walls say about the walls the game never shows it: its south and east. */
final class WallFinishTests {
    private WallFinishTests() {}

    static void run() {
        Checks.run("aRoomShowsItsOwnWallOnEachEdge", WallFinishTests::aRoomShowsItsOwnWallOnEachEdge);
        Checks.run("aRoomIsGrimyWhereMostOfItsWallsAre", WallFinishTests::aRoomIsGrimyWhereMostOfItsWallsAre);
    }

    private static final String NORTH = "walls_interior_house_01_1", WEST = "walls_interior_house_01_0";
    private static final Texture NORTH_PAPER = new Texture(41, 128, 256), WEST_PAPER = new Texture(42, 128, 256);

    /** A room of two by two squares with its paper on its north and west walls, grime hung on the first grimed of the four. */
    private static IsoBuilding room(TestMap map, int grimed) {
        IsoBuilding building = new IsoBuilding(new BuildingDef(1L, 0, 0, 2, 2));
        int hung = 0;
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                map.cell.square(x, y, 0).setRoomID(1L);
                map.cell.square(x, y, 0).building(building);
                for (boolean north : new boolean[] {true, false}) {
                    if (north ? y != 0 : x != 0) {
                        continue;
                    }
                    IsoObject wall = map.cell.square(x, y, 0).add(north ? map.spriteWith(NORTH, IsoObjectType.wall, NORTH_PAPER, "WallN", "")
                            : map.spriteWith(WEST, IsoObjectType.wall, WEST_PAPER, "WallW", ""));
                    // The map hangs grime over a wall or among what it draws with it.
                    IsoSprite grime = map.sprite("overlay_grime_wall_01_16", IsoObjectType.normal);
                    if (hung < grimed && hung % 2 == 0) {
                        wall.overlaySprite = grime;
                    } else if (hung < grimed) {
                        wall.attached.add(new IsoSpriteInstance(grime));
                    }
                    hung++;
                }
            }
        }
        return building;
    }

    private static void aRoomShowsItsOwnWallOnEachEdge() {
        TestMap map = new TestMap();
        IsoBuilding building = room(map, 0);
        WallFinish.Facing north = WallFinish.roomWall(map.cell, building, 0, 1L, true, 0), west = WallFinish.roomWall(map.cell, building, 0, 1L, false, 0);
        check(north != null && NORTH.equals(north.sprite.getName()), "the room's north edges show its north wall");
        check(west != null && WEST.equals(west.sprite.getName()), "and its west edges its west wall");
        check(WallFinish.roomWall(map.cell, building, 0, 2L, true, 0) == null, "a room the level does not have shows none");
    }

    private static void aRoomIsGrimyWhereMostOfItsWallsAre() {
        TestMap clean = new TestMap();
        check(!WallFinish.grimy(clean.cell, room(clean, 0), 0, 1L, 0), "a room with no grime on its walls is clean");
        TestMap one = new TestMap();
        check(!WallFinish.grimy(one.cell, room(one, 1), 0, 1L, 0), "one grimy wall of four leaves it clean");
        TestMap half = new TestMap();
        check(WallFinish.grimy(half.cell, room(half, 2), 0, 1L, 0), "half its walls grimy makes it grimy");
        TestMap all = new TestMap();
        check(WallFinish.grimy(all.cell, room(all, 4), 0, 1L, 0), "as do all of them");
    }
}

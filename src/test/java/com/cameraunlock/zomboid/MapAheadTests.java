package com.cameraunlock.zomboid;

import java.util.HashMap;
import java.util.Map;
import zombie.core.properties.PropertyContainer;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;

/** Tests of reading the map's files ahead of the game (MapAhead): the lotpack, the tiles, and what a square is made of. */
final class MapAheadTests {
    private MapAheadTests() {}

    static void run() {
        Checks.run("aTileIsWhatTheHeightsTakeOfItsSprite", MapAheadTests::aTileIsWhatTheHeightsTakeOfItsSprite);
        Checks.run("theGroundsFirstFloorAndWhatIsAboveAreRead", MapAheadTests::theGroundsFirstFloorAndWhatIsAboveAreRead);
        Checks.run("aSquareIsMadeOfWhatTheGameWouldMakeOfIt", MapAheadTests::aSquareIsMadeOfWhatTheGameWouldMakeOfIt);
        Checks.run("whatStandsOnASquareAndWhatIsOverItAreRead", MapAheadTests::whatStandsOnASquareAndWhatIsOverItAreRead);
        Checks.run("theWindowIsAskedRingByRingInTheOrderOfTheWholeBox", MapAheadTests::theWindowIsAskedRingByRingInTheOrderOfTheWholeBox);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static IsoSprite sprite(String name, IsoObjectType type, String... namesAndValues) {
        Map<String, String> properties = new HashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            properties.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return new IsoSprite(name, new PropertyContainer(properties), type, null);
    }

    private static void aTileIsWhatTheHeightsTakeOfItsSprite() {
        int water = MapAhead.traits(sprite("blends_natural_02_0", IsoObjectType.normal, "solidfloor", "", "water", ""));
        check(water == (MapAhead.FLOOR | MapAhead.BLOCKS_RAIN | MapAhead.WATER | MapAhead.NATURAL_NAME), "water is a floor of water, named as the land's: " + water);
        int footway = MapAhead.traits(sprite("blends_street_01_48", IsoObjectType.normal, "solidfloor", "", "FloorMaterial", "Road_04"));
        check(footway == (MapAhead.FLOOR | MapAhead.BLOCKS_RAIN | MapAhead.STREET_NAME | MapAhead.ROAD_04), "a pavement is a street floor of Road_04: " + footway);
        int stair = MapAhead.traits(sprite("fixtures_stairs_01_0", IsoObjectType.stairsBW));
        check(stair == (MapAhead.STAIR | MapAhead.BUILT_UP), "a stair is built: " + stair);
        int roof = MapAhead.traits(sprite("roofs_01_0", IsoObjectType.WestRoofB, "BlockRain", ""));
        check(roof == (MapAhead.ROOF | MapAhead.BUILT_UP | MapAhead.BLOCKS_RAIN), "a roof tile is built and keeps the rain off: " + roof);
        int canopy = MapAhead.traits(sprite("walls_decoration_01_86", IsoObjectType.WestRoofT, "CustomName", "Canopy", "GroupName", "Curved blue and white striped", "Facing", "S"));
        check(canopy == (MapAhead.PARASOL | MapAhead.BUILT_UP), "a parasol's canopy is a roof tile of its own kind: " + canopy);
        int wall = MapAhead.traits(sprite("walls_exterior_house_01_2", IsoObjectType.wall, "WallNW", ""));
        check(wall == (MapAhead.WALL_N | MapAhead.WALL_W | MapAhead.BUILT_UP), "a corner wall is on the north and the west: " + wall);
        int door = MapAhead.traits(sprite("walls_exterior_house_01_3", IsoObjectType.wall, "DoorWallW", ""));
        check(door == (MapAhead.WALL_W | MapAhead.BUILT_UP), "a door frame on the west is a wall there: " + door);
        int lines = MapAhead.traits(sprite(Footings.OVERHEAD_LINES + "_01_20", IsoObjectType.normal));
        check(lines == MapAhead.OVERHEAD, "the overhead wires are overhead lines and nothing else: " + lines);
        int goal = MapAhead.traits(sprite("recreational_sports_01_20", IsoObjectType.normal));
        check(goal == Grounds.GOAL_ALONG_X << MapAhead.LEVEL_SHIFT, "a goal's crossbar marks level ground and is nothing else: " + goal);
    }

    private static void theGroundsFirstFloorAndWhatIsAboveAreRead() {
        int grass = MapAhead.FLOOR | MapAhead.NATURAL_NAME, sign = 0, wires = MapAhead.OVERHEAD, floor = MapAhead.FLOOR | MapAhead.BLOCKS_RAIN, water = MapAhead.FLOOR | MapAhead.WATER;
        int bar = Grounds.GOAL_ALONG_X << MapAhead.LEVEL_SHIFT, base = Grounds.BASE << MapAhead.LEVEL_SHIFT;
        int[] traits = {grass, sign, wires, floor, water, bar, base};
        var pack = FarTreeTests.pack(true, -1, 2,
                new FarTreeTests.Square(2, 1, -1, 3, 4, 4),
                new FarTreeTests.Square(2, 1, 0, 3, 4, 1, 0, 4),
                new FarTreeTests.Square(2, 1, 1, 3, 4, 2),
                new FarTreeTests.Square(2, 1, 2, 3, 4, 3),
                new FarTreeTests.Square(2, 1, 0, 7, 7, 2),
                new FarTreeTests.Square(2, 1, 1, 7, 7, 1),
                new FarTreeTests.Square(2, 1, 0, 5, 5, 0, 6),
                new FarTreeTests.Square(2, 1, 1, 5, 6, 5),
                new FarTreeTests.Square(2, 1, 2, 5, 7, 5));
        MapAhead.Raw raw = MapAhead.read(0, pack, traits, -1, 2, 66, 33, 2, 1);
        int at = 3 * 8 + 4;
        check(raw.floor()[at] == grass, "the floor is the first floor tile on the ground, after a sign and before water: " + raw.floor()[at]);
        check(raw.ground()[at] == (grass | water), "the ground has every tile on it: " + raw.ground()[at]);
        check(raw.above()[at] == (floor | MapAhead.ANY), "above is the floor two levels up, and not the wires: " + raw.above()[at]);
        check(raw.levels()[at] == (1 << 1 | 1 << 2), "the wires' level has a tile on it, and so has the floor's: " + raw.levels()[at]);
        int corner = 7 * 8 + 7;
        check(raw.floor()[corner] == 0 && raw.ground()[corner] == wires, "a square with only wires on the ground has no floor: " + raw.floor()[corner]);
        check(raw.above()[corner] == MapAhead.ANY && raw.levels()[corner] == 1 << 1, "a sign hung above is something over the square: " + raw.above()[corner]);
        check(raw.floor()[0] == 0 && raw.ground()[0] == 0 && raw.above()[0] == 0 && raw.levels()[0] == 0 && raw.level()[0] == Grounds.NONE, "an empty square has nothing");
        check(raw.level()[5 * 8 + 5] == Grounds.BASE && raw.level()[5 * 8 + 6] == Grounds.GOAL_ALONG_X, "a base on the ground and a goal's crossbar on the level over it mark level ground");
        check(raw.level()[5 * 8 + 7] == Grounds.NONE, "and nothing two levels up does");
    }

    /** One square's worth of what the lotpack has: see MapAhead.Raw. */
    private static MapAhead.Raw raw(int floor, int ground, int above, int levels) {
        int[] floors = new int[64], grounds = new int[64], aboves = new int[64], levelses = new int[64];
        byte[] level = new byte[64];
        level[0] = Grounds.GOAL_ALONG_Y;
        floors[0] = floor;
        grounds[0] = ground | floor;
        aboves[0] = above;
        levelses[0] = levels;
        return new MapAhead.Raw(0, 10, 20, floors, grounds, aboves, levelses, level);
    }

    private static final MapAhead.Rooms NO_ROOMS = (x, y, z) -> Footings.NONE;

    private static void aSquareIsMadeOfWhatTheGameWouldMakeOfIt() {
        int grass = MapAhead.FLOOR | MapAhead.NATURAL_NAME, street = MapAhead.FLOOR | MapAhead.STREET_NAME;
        check(MapAhead.guess(raw(MapAhead.FLOOR | MapAhead.WATER | MapAhead.NATURAL_NAME, 0, 0, 0), NO_ROOMS).kind[0] == Terrain.WATER, "water is water");
        MapAhead.Guess open = MapAhead.guess(raw(grass, 0, 0, 0), NO_ROOMS);
        check(open.kind[0] == Terrain.NATURAL && !open.footway[0], "grass out of doors is the land's own");
        check(open.level[0] == Grounds.GOAL_ALONG_Y, "and what it is to level ground is as read");
        MapAhead.Guess pavement = MapAhead.guess(raw(street | MapAhead.ROAD_04, 0, 0, 0), NO_ROOMS);
        check(pavement.kind[0] == Terrain.STREET && pavement.footway[0], "a pavement is street and footway");
        MapAhead.Guess road = MapAhead.guess(raw(street, 0, 0, 0), NO_ROOMS);
        check(road.kind[0] == Terrain.STREET && !road.footway[0], "a road is street and no footway");
        MapAhead.Guess roofed = MapAhead.guess(raw(street, 0, MapAhead.BLOCKS_RAIN | MapAhead.ANY, 1 << 1), NO_ROOMS);
        check(roofed.kind[0] == Terrain.MADE && roofed.footway[0], "a street under a roof is not out of doors, and is made ground");
        check(MapAhead.guess(raw(0, 0, 0, 0), NO_ROOMS).kind[0] == Terrain.MADE, "a square with no floor is made ground, as Terrain.kind has it");
        long middle = Footings.key(84f, 164f);
        MapAhead.Rooms house = (x, y, z) -> x == 80 && y == 160 && z == 0 ? middle : Footings.NONE;
        MapAhead.Guess inside = MapAhead.guess(raw(grass, 0, 0, 0), house);
        check(inside.kind[0] == Terrain.MADE && inside.house[0] == middle, "grass in a room is made ground in the building");
        check(open.house[0] == Footings.NONE, "a square in no room is in no building");
        MapAhead.Rooms basement = (x, y, z) -> x == 80 && y == 160 && z == -1 ? middle : Footings.NONE;
        MapAhead.Guess yard = MapAhead.guess(raw(grass, 0, 0, 0), basement);
        check(yard.house[0] == middle && yard.kind[0] == Terrain.NATURAL, "grass over a basement is on its building's pad and is still grass");
    }

    private static void whatStandsOnASquareAndWhatIsOverItAreRead() {
        int grass = MapAhead.FLOOR | MapAhead.NATURAL_NAME;
        check(MapAhead.guess(raw(grass, MapAhead.STAIR, 0, 0), NO_ROOMS).stands[0] == MapAhead.STAIR_OR_ROOF, "a stair stands on the ground");
        check(MapAhead.guess(raw(grass, MapAhead.ROOF, 0, 0), NO_ROOMS).stands[0] == MapAhead.STAIR_OR_ROOF, "a roof tile stands on the ground");
        check(MapAhead.guess(raw(grass, MapAhead.PARASOL, 0, 0), NO_ROOMS).stands[0] == MapAhead.CANOPY, "a parasol's canopy is told apart");
        MapAhead.Rooms north = (x, y, z) -> x == 80 && y == 159 && z == 0 ? 1L : Footings.NONE;
        check(MapAhead.guess(raw(grass, MapAhead.WALL_N, 0, 0), north).stands[0] == MapAhead.WALLED, "a wall on the north of a building to the north is the building's");
        check(MapAhead.guess(raw(grass, MapAhead.WALL_W, 0, 0), north).stands[0] == 0, "a wall on the west with no building to the west is a garden wall");
        MapAhead.Rooms west = (x, y, z) -> x == 79 && y == 160 && z == 0 ? 1L : Footings.NONE;
        check(MapAhead.guess(raw(grass, MapAhead.WALL_W, 0, 0), west).stands[0] == MapAhead.WALLED, "a wall on the west of a building to the west is the building's");
        check(MapAhead.guess(raw(grass, 0, MapAhead.FLOOR | MapAhead.ANY, 1 << 1), NO_ROOMS).above[0] == Footings.BUILT, "a floor above is built over the square");
        check(MapAhead.guess(raw(grass, 0, MapAhead.BUILT_UP | MapAhead.ANY, 1 << 3), NO_ROOMS).above[0] == Footings.BUILT, "a wall above is built over the square");
        check(MapAhead.guess(raw(grass, 0, MapAhead.ANY, 1 << 1), NO_ROOMS).above[0] == Footings.HUNG, "a sign above hangs over the square");
        check(MapAhead.guess(raw(grass, 0, 0, 1 << 1), NO_ROOMS).above[0] == Footings.OPEN, "wires above are nothing over the square");
        MapAhead.Rooms upstairs = (x, y, z) -> x == 80 && y == 160 && z == 2 ? 1L : Footings.NONE;
        check(MapAhead.guess(raw(grass, 0, MapAhead.ANY, 1 << 2), upstairs).above[0] == Footings.BUILT, "a room above is built over the square");
        check(MapAhead.guess(raw(grass, 0, 0, 0), upstairs).above[0] == Footings.OPEN, "a room above on no square the map has is nothing over it");
    }

    private static void theWindowIsAskedRingByRingInTheOrderOfTheWholeBox() {
        int midX = -3, midY = 1250, rings = 9;
        java.util.ArrayList<Long> walked = new java.util.ArrayList<>(), boxed = new java.util.ArrayList<>();
        MapAhead.ringsOut(midX, midY, rings, (wx, wy) -> walked.add((long) wx << 32 | wy & 0xFFFFFFFFL));
        // Each ring's whole box walked, its edge kept.
        for (int ring = 0; ring <= rings; ring++) {
            for (int wx = midX - ring; wx <= midX + ring; wx++) {
                for (int wy = midY - ring; wy <= midY + ring; wy++) {
                    if (Math.max(Math.abs(wx - midX), Math.abs(wy - midY)) == ring) {
                        boxed.add((long) wx << 32 | wy & 0xFFFFFFFFL);
                    }
                }
            }
        }
        check(walked.equals(boxed) && walked.size() == (2 * rings + 1) * (2 * rings + 1), "every chunk of the window once, nearest first, in the order walking each ring's box gave: "
                + walked.size());
    }
}

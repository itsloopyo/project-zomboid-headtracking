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
        Checks.run("theQuartersOfACanopyOnTheGroundAreRead", MapAheadTests::theQuartersOfACanopyOnTheGroundAreRead);
        Checks.run("aFloorLaidOnADiamondFloorTakesItsPlace", MapAheadTests::aFloorLaidOnADiamondFloorTakesItsPlace);
        Checks.run("aSquareIsMadeOfWhatTheGameWouldMakeOfIt", MapAheadTests::aSquareIsMadeOfWhatTheGameWouldMakeOfIt);
        Checks.run("whatStandsOnASquareAndWhatIsOverItAreRead", MapAheadTests::whatStandsOnASquareAndWhatIsOverItAreRead);
        Checks.run("theWindowHoldsTheChunksRoundTheLoadedMapAndTheWaterRoundThem", MapAheadTests::theWindowHoldsTheChunksRoundTheLoadedMapAndTheWaterRoundThem);
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
        check(canopy == (MapAhead.PARASOL | MapAhead.BUILT_UP | MapAhead.QUARTER | Parasols.SOUTH_EAST << MapAhead.QUARTER_SHIFT), "a parasol's canopy is a roof tile of its own kind, and the quarter it is: " + canopy);
        int wall = MapAhead.traits(sprite("walls_exterior_house_01_2", IsoObjectType.wall, "WallNW", ""));
        check(wall == (MapAhead.WALL_N | MapAhead.WALL_W | MapAhead.BUILT_UP), "a corner wall is on the north and the west: " + wall);
        int door = MapAhead.traits(sprite("walls_exterior_house_01_3", IsoObjectType.wall, "DoorWallW", ""));
        check(door == (MapAhead.WALL_W | MapAhead.BUILT_UP), "a door frame on the west is a wall there: " + door);
        int lines = MapAhead.traits(sprite(Footings.OVERHEAD_LINES + "_01_20", IsoObjectType.normal));
        check(lines == MapAhead.OVERHEAD, "the overhead wires are overhead lines and nothing else: " + lines);
        int goal = MapAhead.traits(sprite("recreational_sports_01_20", IsoObjectType.normal));
        check(goal == Grounds.GOAL_ALONG_X << MapAhead.LEVEL_SHIFT, "a goal's crossbar marks level ground and is nothing else: " + goal);
        int centre = MapAhead.traits(sprite("street_trafficlines_01_20", IsoObjectType.normal));
        check(centre == Lanes.mark("street_trafficlines_01_20") << MapAhead.MARK_SHIFT, "a road's line is the line it paints and nothing else: " + centre);
        int stone = MapAhead.traits(sprite("floors_exterior_tilesandstone_01_5", IsoObjectType.normal, "solidfloor", "", "diamondFloor", ""));
        check(stone == (MapAhead.FLOOR | MapAhead.BLOCKS_RAIN | MapAhead.DIAMOND), "a paving stone is a diamond floor: " + stone);
        int boulder = MapAhead.traits(sprite("boulders_40", IsoObjectType.normal, "solidfloor", "", "diamondFloor", "", "transparentFloor", ""));
        check(boulder == (MapAhead.FLOOR | MapAhead.BLOCKS_RAIN | MapAhead.DIAMOND | MapAhead.SEE_THROUGH), "a boulder's top is a diamond floor that is see-through: " + boulder);
        int line = MapAhead.traits(sprite("street_trafficlines_01_2", IsoObjectType.normal, "FloorOverlay", ""));
        check(line == (MapAhead.OVERLAY | Lanes.mark("street_trafficlines_01_2") << MapAhead.MARK_SHIFT), "a line on the road is laid over the floor: " + line);
    }

    private static void aFloorLaidOnADiamondFloorTakesItsPlace() {
        int grass = MapAhead.FLOOR | MapAhead.NATURAL_NAME | MapAhead.DIAMOND, stone = MapAhead.FLOOR | MapAhead.DIAMOND;
        int boulder = stone | MapAhead.SEE_THROUGH, plain = MapAhead.FLOOR | MapAhead.STREET_NAME;
        int yellow = MapAhead.OVERLAY | 1 << MapAhead.MARK_SHIFT, white = MapAhead.OVERLAY | 2 << MapAhead.MARK_SHIFT;
        int court = stone | Grounds.SURFACE << MapAhead.LEVEL_SHIFT, edging = MapAhead.FLOOR | MapAhead.OVERLAY | MapAhead.NATURAL_NAME;
        int[] traits = {grass, stone, boulder, plain, yellow, white, court, edging};
        int ofGrass = 0, ofStone = 1, ofBoulder = 2, ofPlain = 3, ofYellow = 4, ofWhite = 5, ofCourt = 6, ofEdging = 7;
        var pack = FarTreeTests.pack(true, 0, 1,
                new FarTreeTests.Square(0, 0, 0, 0, 0, ofGrass, ofYellow, ofStone, ofWhite),
                new FarTreeTests.Square(0, 0, 0, 0, 1, ofGrass, ofBoulder),
                new FarTreeTests.Square(0, 0, 0, 0, 2, ofPlain, ofStone),
                new FarTreeTests.Square(0, 0, 0, 0, 3, ofWhite),
                new FarTreeTests.Square(0, 0, 1, 0, 4, ofWhite),
                new FarTreeTests.Square(0, 0, 0, 0, 5, ofCourt, ofStone),
                new FarTreeTests.Square(0, 0, 0, 0, 6, ofStone, ofCourt),
                new FarTreeTests.Square(0, 0, 0, 0, 7, ofEdging, ofGrass),
                new FarTreeTests.Square(0, 0, 0, 1, 0, ofGrass, ofEdging, ofYellow));
        MapAhead.Raw raw = MapAhead.read(0, pack, traits, 0, 1, 40, 50, 0, 0);
        check(raw.floor()[0] == stone && raw.ground()[0] == (stone | 2 << MapAhead.MARK_SHIFT),
                "a diamond floor laid on a diamond floor takes its place, and the line laid before it goes with the floor it was on: " + raw.floor()[0] + " " + raw.ground()[0]);
        check(raw.floor()[1] == grass && raw.ground()[1] == (grass | boulder), "a see-through one is laid beside the floor, not in its place: " + raw.floor()[1]);
        check(raw.floor()[2] == plain && raw.ground()[2] == (plain | stone), "and so is one laid on a floor that is no diamond floor: " + raw.floor()[2]);
        check(raw.floor()[3] == 0 && raw.ground()[3] == 0, "a line where no floor is is not laid: " + raw.ground()[3]);
        check(raw.above()[4] == 0 && raw.levels()[4] == 1 << 1, "nor on a level above, where it is nothing over the square, though the level has a tile: " + raw.above()[4]);
        check(raw.level()[5] == Grounds.NONE && raw.level()[6] == Grounds.SURFACE, "a court's surface floored over is no court, and floored over a floor is one");
        check(raw.floor()[7] == grass && raw.ground()[7] == grass, "an edging laid as an overlay where no floor is yet is not laid, and the floor after it is the floor: " + raw.floor()[7]);
        check(raw.floor()[8] == grass && raw.ground()[8] == (grass | 1 << MapAhead.MARK_SHIFT), "laid on a floor, an overlay is no floor and only its lines are read: " + raw.ground()[8]);
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

    private static void theQuartersOfACanopyOnTheGroundAreRead() {
        int grass = MapAhead.FLOOR | MapAhead.NATURAL_NAME;
        int roofQuarter = MapAhead.PARASOL | MapAhead.BUILT_UP | MapAhead.QUARTER | Parasols.SOUTH_EAST << MapAhead.QUARTER_SHIFT;
        int heldQuarter = MapAhead.QUARTER | Parasols.NORTH_WEST << MapAhead.QUARTER_SHIFT;
        int[] traits = {grass, roofQuarter, heldQuarter};
        var pack = FarTreeTests.pack(true, 0, 1,
                new FarTreeTests.Square(2, 1, 0, 1, 1, 0, 1),
                new FarTreeTests.Square(2, 1, 0, 2, 2, 0, 2),
                new FarTreeTests.Square(2, 1, 1, 3, 3, 1));
        MapAhead.Raw raw = MapAhead.read(0, pack, traits, 0, 1, 66, 33, 2, 1);
        int roofed = raw.quarters()[1 * 8 + 1], held = raw.quarters()[2 * 8 + 2];
        check(roofed == (1 << Terrain.ROOF_QUARTERS + Parasols.SOUTH_EAST | 1 << Terrain.HELD_QUARTERS + Parasols.SOUTH_EAST), "a canopy's quarter on the ground is held there, and is a roof tile: " + roofed);
        check(held == 1 << Terrain.HELD_QUARTERS + Parasols.NORTH_WEST, "a quarter that is no roof tile is held there and is no more: " + held);
        check(raw.quarters()[3 * 8 + 3] == 0, "and a quarter on a level above is none on the ground");
    }

    /** One square's worth of what the lotpack has: see MapAhead.Raw. */
    private static MapAhead.Raw raw(int floor, int ground, int above, int levels) {
        return raw(floor, ground, above, levels, (short) 0);
    }

    /** The same, with the canopy quarters on the ground, as MapAhead.Raw keeps them. */
    private static MapAhead.Raw raw(int floor, int ground, int above, int levels, short quarter) {
        int[] floors = new int[64], grounds = new int[64], aboves = new int[64], levelses = new int[64];
        byte[] level = new byte[64];
        short[] quarters = new short[64];
        level[0] = Grounds.GOAL_ALONG_Y;
        floors[0] = floor;
        grounds[0] = ground | floor;
        aboves[0] = above;
        levelses[0] = levels;
        quarters[0] = quarter;
        return new MapAhead.Raw(0, 10, 20, floors, grounds, aboves, levelses, level, quarters);
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
        int yellow = Lanes.mark("street_trafficlines_01_20"), white = Lanes.mark("street_trafficlines_01_8");
        MapAhead.Guess lined = MapAhead.guess(raw(street, (yellow | white) << MapAhead.MARK_SHIFT, 0, 0), NO_ROOMS);
        check(lined.marks[0] == (byte) (yellow | white), "a road keeps every line painted on it, as Terrain.Kinds does: " + lined.marks[0]);
        check(MapAhead.guess(raw(grass, yellow << MapAhead.MARK_SHIFT, 0, 0), NO_ROOMS).marks[0] == 0, "and what is not a street keeps none");
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
        check(MapAhead.guess(raw(grass, MapAhead.STAIR, 0, 0), NO_ROOMS).stands[0] == Terrain.STAIR_OR_ROOF, "a stair stands on the ground");
        check(MapAhead.guess(raw(grass, MapAhead.ROOF, 0, 0), NO_ROOMS).stands[0] == Terrain.STAIR_OR_ROOF, "a roof tile stands on the ground");
        short canopy = (short) (1 << Terrain.ROOF_QUARTERS + Parasols.SOUTH_EAST | 1 << Terrain.HELD_QUARTERS + Parasols.SOUTH_EAST);
        check(MapAhead.guess(raw(grass, MapAhead.PARASOL, 0, 0, canopy), NO_ROOMS).stands[0] == canopy, "a parasol's canopy is kept as the quarter it is, for Terrain to tell whether the parasol is whole");
        check(MapAhead.guess(raw(grass, MapAhead.WALL_N, 0, 0), NO_ROOMS).stands[0] == Terrain.NORTH_WALL, "a wall on the north is kept, for Terrain to tell whether a building is to the north of it");
        check(MapAhead.guess(raw(grass, MapAhead.WALL_W, 0, 0), NO_ROOMS).stands[0] == Terrain.WEST_WALL, "and one on the west");
        MapAhead.Rooms room = (x, y, z) -> x == 80 && y == 160 && z == 0 ? 1L : Footings.NONE;
        check(MapAhead.guess(raw(grass, 0, 0, 0), room).stands[0] == Terrain.ROOMED, "a square with a room on it is kept as one");
        MapAhead.Rooms basement = (x, y, z) -> x == 80 && y == 160 && z == -1 ? 1L : Footings.NONE;
        check(MapAhead.guess(raw(grass, 0, 0, 0), basement).stands[0] == 0, "a square over a basement has no room on it");
        check(MapAhead.guess(raw(grass, 0, MapAhead.FLOOR | MapAhead.ANY, 1 << 1), NO_ROOMS).above[0] == Footings.BUILT, "a floor above is built over the square");
        check(MapAhead.guess(raw(grass, 0, MapAhead.BUILT_UP | MapAhead.ANY, 1 << 3), NO_ROOMS).above[0] == Footings.BUILT, "a wall above is built over the square");
        check(MapAhead.guess(raw(grass, 0, MapAhead.ANY, 1 << 1), NO_ROOMS).above[0] == Footings.HUNG, "a sign above hangs over the square");
        check(MapAhead.guess(raw(grass, 0, 0, 1 << 1), NO_ROOMS).above[0] == Footings.OPEN, "wires above are nothing over the square");
        MapAhead.Rooms upstairs = (x, y, z) -> x == 80 && y == 160 && z == 2 ? 1L : Footings.NONE;
        check(MapAhead.guess(raw(grass, 0, MapAhead.ANY, 1 << 2), upstairs).above[0] == Footings.BUILT, "a room above is built over the square");
        check(MapAhead.guess(raw(grass, 0, 0, 0), upstairs).above[0] == Footings.OPEN, "a room above on no square the map has is nothing over it");
    }

    /**
     * With the game's loaded map 19 chunks a side, the window holds the chunks round each chunk
     * of it to Terrain.AROUND, the water round each of those, and what a goal, a base, a hoop or
     * a jump on the loaded map's last square reaches for a partner. The middle of a thing, round
     * which its water is looked for, and a chain of partners can lie further, and are not held.
     */
    private static void theWindowHoldsTheChunksRoundTheLoadedMapAndTheWaterRoundThem() {
        int loaded = 19 / 2, window = (19 + 2 * MapAhead.BEYOND) / 2;
        int water = TestMap.<Integer>read(null, Terrain.class, "WATER_CHUNKS");
        int round = loaded + Terrain.AROUND / Terrain.CHUNK_TILES + water;
        check(window >= round, "the window holds the water round the farthest chunk a chunk of the loaded map goes by: " + window + " chunks each way, the water " + round);
        int reach = 0;
        for (byte kind = Grounds.NONE; kind <= Grounds.JUMP; kind++) {
            reach = Math.max(reach, Grounds.reach(kind));
        }
        int partners = (loaded * Terrain.CHUNK_TILES + Terrain.CHUNK_TILES - 1 + reach) / Terrain.CHUNK_TILES;
        check(window >= partners, "and what a goal on the loaded map's last square reaches for its partner: " + window + " chunks each way, the goal " + partners);
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

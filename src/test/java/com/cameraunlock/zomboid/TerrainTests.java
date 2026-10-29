package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import zombie.iso.BuildingDef;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.areas.IsoBuilding;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/** Tests of the height Terrain gives a street and the ground beside it, the hills of the country, and how high the player has them stand. */
final class TerrainTests {
    private static final int SPAN = Terrain.SPAN;
    /** The heights the hills at height 1 gave the land of shown() before HillHeight, with no farmland or woods zoned, as their bits. */
    private static final String AS_IT_WAS = "40139075,400d4fa9,3ff616de,3fdda745,3fdda745,3fdda745,3fe3b897,3fdd20fa,3fd1aa48,3fbcc0e5,3f74639a,3fa0f93a,3fb24599,3fa9ffe7,"
            + "3fa5c257,3fa75e2c,3fa97a4d,3fa7e9f9,3fa44090,3fa38002,3fa7a797,3facbe6e,3face94a,3fa6e388,3f9f628f,3f9bca71,3f9c55aa,3f9c6a00,3f989b1a,3f933c4c,3f9214f0,"
            + "3f97d368,3fa0dc25,3fa73561,3fa911a2,\n403940a1,3f8a65ea,3e9dbe2d,bf4c249a,bee71071,bf3567f0,be002be0,3df3eb0a,be26740e,3fcd89dc,4004a744,3fec1037,3e1ed93c,"
            + "3d6bbc4f,be9c8bd1,";
    private static final float[] HEIGHTS = {0f, 0.35f, 1f, HillHeight.MOST};
    /** Woods from (0, 0) to (6000, 6000) with a town in the middle of them, and farmland drawn as a triangle off to the east. */
    private static final List<Country.Shape> COUNTRY = List.of(
            Country.Shape.rectangle("DeepForest", 0, 0, 6000, 6000),
            Country.Shape.rectangle("TownZone", 2400, 2400, 1200, 1200),
            new Country.Shape("FarmLand", new int[] {8000, 0, 12000, 0, 10000, 4000}));

    /** The lie of the land the test in hand asks. */
    private static Terrain terrain;

    private TerrainTests() {}

    static void run() {
        byte[] around = Terrain.around.clone();
        float[] pads = Terrain.padsAround.clone();
        boolean[] footway = Terrain.footwayAround.clone();
        Terrain.Rooms rooms = Terrain.rooms;
        try {
            Terrain.rooms = TerrainTests::roomOf;
            Arrays.fill(Terrain.footwayAround, false);
            terrain = Terrain.HILLS;
            Checks.run("streetAlongAHillsideIsLevelFromSideToSide", TerrainTests::streetAlongAHillsideIsLevelFromSideToSide);
            Checks.run("swellsAreSeenFromAStreetAndNoSteeperThanOneInTwenty", TerrainTests::swellsAreSeenFromAStreetAndNoSteeperThanOneInTwenty);
            terrain = Terrain.LEVEL;
            Checks.run("streetWithItsPavementAgainstABuildingIsAtTheBuildingsFooting", TerrainTests::streetWithItsPavementAgainstABuildingIsAtTheBuildingsFooting);
            Checks.run("theHillsAtHeightOneAreTheLandAsItWas", TerrainTests::theHillsAtHeightOneAreTheLandAsItWas);
            Checks.run("theHillsAtNoHeightAreTheLevelLand", TerrainTests::theHillsAtNoHeightAreTheLevelLand);
            Checks.run("kerbsBanksAndStreetsStandAsTheyDoAtEveryHeight", TerrainTests::kerbsBanksAndStreetsStandAsTheyDoAtEveryHeight);
            Checks.run("theFarLandAndTheBuiltLandAgreeAtEveryHeight", TerrainTests::theFarLandAndTheBuiltLandAgreeAtEveryHeight);
            Checks.run("hillsRiseInFarmlandAndWoodsAndNotInTown", TerrainTests::hillsRiseInFarmlandAndWoodsAndNotInTown);
            Checks.run("everyRiseOfTheLandIsTheHillsHeightTimesItsOwn", TerrainTests::everyRiseOfTheLandIsTheHillsHeightTimesItsOwn);
            Checks.run("noHillRisesFromWater", TerrainTests::noHillRisesFromWater);
            Checks.run("theHillsAreAtTheirDefaultWherePlayersCanFight", TerrainTests::theHillsAreAtTheirDefaultWherePlayersCanFight);
            Checks.run("aChangeOfHeightReworksTheLandRoundTheEyeAtOnce", TerrainTests::aChangeOfHeightReworksTheLandRoundTheEyeAtOnce);
            Checks.run("streamedChunksShareTheirBorders", TerrainTests::streamedChunksShareTheirBorders);
            Checks.run("aBuildKeepsTheHeightsItWasMadeOn", TerrainTests::aBuildKeepsTheHeightsItWasMadeOn);
            Checks.run("groundReadAheadGivesAChunkTheHeightsItHasOnceLoaded", TerrainTests::groundReadAheadGivesAChunkTheHeightsItHasOnceLoaded);
            Checks.run("groundLoadedOtherwiseThanReadAheadHasItsOwnHeights", TerrainTests::groundLoadedOtherwiseThanReadAheadHasItsOwnHeights);
            Checks.run("theLevelLandHasNoKerbs", TerrainTests::theLevelLandHasNoKerbs);
            Checks.run("aChunkStillFillingIsReadAgainOnceLoaded", TerrainTests::aChunkStillFillingIsReadAgainOnceLoaded);
            Checks.run("aChunkWhoseGroundIsAsItWasKeepsItsHeightsUntilAnyOfItChanges", TerrainTests::aChunkWhoseGroundIsAsItWasKeepsItsHeightsUntilAnyOfItChanges);
            Checks.run("aPitchOnAHillsideIsLaidLevelAndTheHillsEaseBackRoundIt", TerrainTests::aPitchOnAHillsideIsLaidLevelAndTheHillsEaseBackRoundIt);
            Checks.run("aTownStreamedARowAtATimeHasTheHeightsOfOneLoadedWhole", () -> TestMap.withEverything(TerrainTests::aTownStreamedARowAtATimeHasTheHeightsOfOneLoadedWhole));
            Checks.run("recordsLetGoFarFromEveryEyeAreReadAgainAsTheyWere", () -> TestMap.withEverything(TerrainTests::recordsLetGoFarFromEveryEyeAreReadAgainAsTheyWere));
            Checks.run("recordsPutOutForRoomAreReadAgainAsTheyWere", TerrainTests::recordsPutOutForRoomAreReadAgainAsTheyWere);
            Checks.run("aRowOfContentSwitchedGivesTheHeightsWorkedOutAfresh", TerrainTests::aRowOfContentSwitchedGivesTheHeightsWorkedOutAfresh);
            Checks.run("groundReadAheadAndLeftBehindHasTheHeightsOfGroundReadAfresh", TerrainTests::groundReadAheadAndLeftBehindHasTheHeightsOfGroundReadAfresh);
            Checks.run("aSquareTheGameMakesOnAChunksEdgeForTheChunkBesideItIsNothingOverTheGround", TerrainTests::aSquareTheGameMakesOnAChunksEdgeForTheChunkBesideItIsNothingOverTheGround);
            Checks.run("aChunkHasTheHeightsOnItsFirstLoadThatItHasFromTheSave", TerrainTests::aChunkHasTheHeightsOnItsFirstLoadThatItHasFromTheSave);
            Checks.run("theMapIsAskedOnceAChunkWhetherItIsLoadedAndOnlyWhenItLoadsOtherChunks", TerrainTests::theMapIsAskedOnceAChunkWhetherItIsLoadedAndOnlyWhenItLoadsOtherChunks);
            Checks.run("aLoadedChunkWithASquareOfItsGroundNotMadeHasThatSquareAsNotLoaded", TerrainTests::aLoadedChunkWithASquareOfItsGroundNotMadeHasThatSquareAsNotLoaded);
            Checks.run("fourEyesFarApartEachKeepTheRecordsRoundThem", TerrainTests::fourEyesFarApartEachKeepTheRecordsRoundThem);
            Checks.run("aBuildingsWallAndALoneCanopyAreBuiltOverTheGroundAndAGardenWallAndAParasolAreNot",
                    TerrainTests::aBuildingsWallAndALoneCanopyAreBuiltOverTheGroundAndAGardenWallAndAParasolAreNot);
            Checks.run("postsOnALevelAboveTheRoadLeaveTheKerbAndWhatIsBuiltOnAsTheyAre", TerrainTests::postsOnALevelAboveTheRoadLeaveTheKerbAndWhatIsBuiltOnAsTheyAre);
        } finally {
            Terrain.rooms = rooms;
            System.arraycopy(around, 0, Terrain.around, 0, around.length);
            System.arraycopy(pads, 0, Terrain.padsAround, 0, pads.length);
            System.arraycopy(footway, 0, Terrain.footwayAround, 0, footway.length);
            Terrain.HILLS.heighten(1f);
            Country.set(List.of());
        }
    }

    /** The rooms of a test's map, as Terrain.rooms asks them: those of the buildings the test put its squares in. */
    private static long roomOf(IsoCell cell, int x, int y, int z) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        IsoBuilding building = square == null ? null : square.getBuilding();
        BuildingDef def = building == null ? null : building.getDef();
        return def == null ? Footings.NONE : Footings.key(def.getX() + def.getW() / 2f, def.getY() + def.getH() / 2f);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /**
     * A far chunk's build is kept while the heights it was made on have moved by less than the
     * picture shows (WorldScene.Frame.heightsHeld). That holds only while Terrain never writes an
     * array it has handed out: the build keeps those arrays as they were.
     */
    private static void aBuildKeepsTheHeightsItWasMadeOn() {
        boolean live = ChunkMarks.live;
        Terrain.forget();
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            TestMap map = new TestMap();
            var grass = map.sprite("blends_natural_01_0", zombie.iso.SpriteDetails.IsoObjectType.normal);
            var water = map.sprite("blends_natural_02_0", zombie.iso.SpriteDetails.IsoObjectType.normal, "water");
            for (int x = -8; x < 16; x++) {
                for (int y = -8; y < 16; y++) {
                    map.cell.square(x, y, 0).floor(grass);
                }
            }
            Terrain.frame(2000);
            Terrain.loaded(1);
            WorldScene.ChunkMeshes built = new WorldScene.ChunkMeshes(0);
            map.build.heightsSeen(built, 1, 0);
            check(map.build.heightsHeld(built, 0f), "with nothing loaded since, a build's heights are the heights");
            for (int x = -48; x < 56; x++) {
                for (int y = -48; y < 56; y++) {
                    if (x < -8 || x >= 16 || y < -8 || y >= 16) {
                        map.cell.square(x, y, 0).floor(x >= 16 ? water : grass);
                    }
                }
            }
            Terrain.frame(2001);
            Terrain.loaded(2);
            Terrain.HILLS.fromMap(map.cell, 1, 0);
            float moved = 0f;
            float[] now = Terrain.HILLS.corners(map.cell, 1, 0);
            float[] then = built.heightsSeenForTests();
            for (int i = 0; i < now.length; i++) {
                moved = Math.max(moved, Math.abs(now[i] - then[i]));
            }
            check(moved > 0f, "water loaded beside the chunk moves its heights: " + moved);
            check(!map.build.heightsHeld(built, moved * 0.5f), "heights that moved by more than allowed are not the build's");
            check(map.build.heightsHeld(built, moved), "heights that moved by no more than allowed are still the build's");
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
        }
    }

    /** The map the read-ahead tests load: grass, and water from x = 16 east. */
    private static boolean wet(int x) {
        return x >= 16;
    }

    /**
     * The heights of the chunk (1, 0) with the map loaded from -from to to-1 each way, and the
     * chunks round it not loaded read ahead as the map has them, with water from x = 16 east
     * where waterAhead and none where not, or not read ahead at all where readAhead is false.
     */
    private static float[] heightsReadAhead(int from, int to, boolean readAhead, boolean waterAhead) {
        TestMap map = new TestMap();
        var grass = map.sprite("blends_natural_01_0", zombie.iso.SpriteDetails.IsoObjectType.normal);
        var water = map.sprite("blends_natural_02_0", zombie.iso.SpriteDetails.IsoObjectType.normal, "water");
        Terrain.forget();
        MapAhead.forget();
        for (int x = -from; x < to; x++) {
            for (int y = -from; y < to; y++) {
                map.cell.square(x, y, 0).floor(wet(x) ? water : grass);
            }
        }
        int fromChunk = Math.floorDiv(-from, Terrain.CHUNK_TILES), toChunk = Math.floorDiv(to - 1, Terrain.CHUNK_TILES);
        for (int wx = -12; readAhead && wx <= 13; wx++) {
            for (int wy = -12; wy <= 13; wy++) {
                if (wx >= fromChunk && wx <= toChunk && wy >= fromChunk && wy <= toChunk) {
                    continue;
                }
                MapAhead.Guess guess = new MapAhead.Guess(wx, wy);
                for (int i = 0; i < Terrain.CHUNK_TILES; i++) {
                    for (int j = 0; j < Terrain.CHUNK_TILES; j++) {
                        int at = i * Terrain.CHUNK_TILES + j;
                        guess.kind[at] = waterAhead && wet(wx * Terrain.CHUNK_TILES + i) ? Terrain.WATER : Terrain.NATURAL;
                        guess.house[at] = Footings.NONE;
                        guess.above[at] = Footings.OPEN;
                    }
                }
                MapAhead.put(guess);
            }
        }
        Terrain.frame(3000);
        Terrain.loaded(1);
        Terrain.HILLS.fromMap(map.cell, 1, 0);
        return Terrain.HILLS.corners(map.cell, 1, 0).clone();
    }

    /**
     * A chunk's heights go by the ground up to Terrain.AROUND round it. Read ahead of the game
     * (MapAhead), the ground not yet loaded gives a chunk at the edge of the loaded map the
     * heights it has once all of it is loaded, where without it the chunk steps to them as
     * the map loads.
     */
    private static void groundReadAheadGivesAChunkTheHeightsItHasOnceLoaded() {
        boolean live = ChunkMarks.live;
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            float[] loaded = heightsReadAhead(48, 64, false, false);
            float[] unread = heightsReadAhead(8, 16, false, false);
            float[] readAhead = heightsReadAhead(8, 16, true, true);
            check(!Arrays.equals(unread, loaded), "with nothing read ahead, the chunk at the edge has other heights than once the map round it is loaded");
            check(Arrays.equals(readAhead, loaded), "with the map round it read ahead, the chunk at the edge has the heights it has once it is loaded: "
                    + Arrays.toString(readAhead) + " against " + Arrays.toString(loaded));
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
            MapAhead.forget();
        }
    }

    /** What is read ahead is the map as shipped, and a save can have it otherwise: what loads is what the heights then go by. */
    private static void groundLoadedOtherwiseThanReadAheadHasItsOwnHeights() {
        boolean live = ChunkMarks.live;
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            float[] loaded = heightsReadAhead(48, 64, false, false);
            float[] misread = heightsReadAhead(8, 16, true, false);
            float[] loadedOverMisread = heightsReadAhead(48, 64, true, false);
            check(!Arrays.equals(misread, loaded), "read ahead without the water the save has, the chunk at the edge has other heights");
            check(Arrays.equals(loadedOverMisread, loaded), "once the ground is loaded, its heights are the loaded ground's and not what was read ahead");
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
            MapAhead.forget();
        }
    }

    /**
     * The overhead view draws on the level land and the game draws a street at the height of
     * the ground beside it, so a street cut a kerb down there puts what lies on it below the
     * game's own picture of it.
     */
    private static void theLevelLandHasNoKerbs() {
        boolean live = ChunkMarks.live;
        Terrain.forget();
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            TestMap map = new TestMap();
            var grass = map.sprite("blends_natural_01_0", zombie.iso.SpriteDetails.IsoObjectType.normal);
            var road = map.sprite("blends_street_01_0", zombie.iso.SpriteDetails.IsoObjectType.normal);
            for (int x = -24; x < 32; x++) {
                for (int y = -24; y < 32; y++) {
                    map.cell.square(x, y, 0).floor(y >= 2 && y < 6 ? road : grass);
                }
            }
            Terrain.frame(3000);
            Terrain.loaded(1);
            Terrain.HILLS.corners(map.cell, 0, 0);
            Terrain.LEVEL.corners(map.cell, 0, 0);
            float cut = Terrain.HILLS.land(4.5f, 3.5f) - Terrain.HILLS.height(4.5f, 3.5f);
            check(Math.abs(cut - Terrain.KERB) < 1e-4f, "on the hills a street lies a kerb below the land: " + cut);
            check(Terrain.LEVEL.kerb(0, 0) == null, "the level land has no kerbs");
            check(Terrain.LEVEL.height(4.5f, 3.5f) == 0f, "on the level land a street is at the game's height: " + Terrain.LEVEL.height(4.5f, 3.5f));
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
        }
    }

    /** A change to the map the fold test makes once its chunk has heights. */
    private interface Change {
        void make(TestMap map);
    }

    /** The chunks the fold test's map is still filling: one with water near the shed, one the far shed reaches into, and one with water in a valley of the chunk at (0, 0). */
    private static final int[][] FILLING = {{-7, 1}, {-7, 2}, {-4, -3}};

    /**
     * The map the fold test lays: grass from -64 to 39 each way, which leaves ground within
     * Terrain.AROUND of the chunk at (0, 0) not loaded, a street across it from y = 2 to 5, a
     * pond west of the chunk, a shed with no building in it west of that, another that stands
     * across the edge of what the chunk's heights go by, and the chunks of FILLING laid but
     * still filling, with water where the map will have it.
     */
    private static TestMap foldMap() {
        TestMap map = new TestMap();
        var grass = map.sprite("blends_natural_01_0", zombie.iso.SpriteDetails.IsoObjectType.normal);
        var road = map.sprite("blends_street_01_0", zombie.iso.SpriteDetails.IsoObjectType.normal);
        var water = map.sprite("blends_natural_02_0", zombie.iso.SpriteDetails.IsoObjectType.normal, "water");
        for (int x = -64; x < 40; x++) {
            for (int y = -64; y < 40; y++) {
                boolean wet = x >= -32 && x < -28 && y >= 8 && y < 12 || x == -30 && y == -20 || x >= -56 && x < -48 && y >= 8 && y < 16;
                map.cell.square(x, y, 0).floor(y >= 2 && y < 6 ? road : wet ? water : grass);
            }
        }
        for (int x = -42; x < -38; x++) {
            for (int y = 10; y < 13; y++) {
                map.roofSprite(x, y, 0, "roofs_02_0");
            }
        }
        for (int x = -51; x < -46; x++) {
            for (int y = 20; y < 23; y++) {
                map.roofSprite(x, y, 0, "roofs_02_0");
            }
        }
        for (int[] chunk : FILLING) {
            filling(map, chunk[0], chunk[1], true);
        }
        return map;
    }

    private static void filling(TestMap map, int wx, int wy, boolean filling) {
        for (int i = 0; i < Terrain.CHUNK_TILES; i++) {
            for (int j = 0; j < Terrain.CHUNK_TILES; j++) {
                map.cell.square(wx * Terrain.CHUNK_TILES + i, wy * Terrain.CHUNK_TILES + j, 0).filling(filling);
            }
        }
    }

    private static long folded() {
        return TestMap.<Long>read(null, Terrain.class, "tollFolded");
    }

    /** How many times heights that were kept have been worked out again. */
    private static long again() {
        return TestMap.<Long>read(null, Terrain.class, "tollAgain");
    }

    /** The heights, water and kerbs of the chunk at (0, 0), as one string. */
    private static String chunkAtNought(TestMap map) {
        float[] heights = Terrain.HILLS.corners(map.cell, 0, 0);
        return Arrays.toString(heights) + Arrays.toString(Terrain.HILLS.water(0, 0)) + Arrays.toString(Terrain.HILLS.kerb(0, 0));
    }

    /** The same worked out with nothing kept from before, on the map as it is now. */
    private static String chunkAtNoughtAfresh(TestMap map, int epoch) {
        Terrain.forget();
        Terrain.frame(4000 + epoch);
        Terrain.loaded(epoch);
        return chunkAtNought(map);
    }

    /**
     * The map loads more, or more of it is read ahead, whenever the eye only turns round. Where
     * none of the chunks a chunk's heights went by comes out otherwise, they are kept without
     * being worked out again, and they are what working them out afresh gives. Where one does,
     * the fold tells whether anything the heights go by changed with it, a square, a footing,
     * water or a pad worked out from ground beyond what the heights go by, and where it did
     * they are worked out again.
     */
    private static void aChunkWhoseGroundIsAsItWasKeepsItsHeightsUntilAnyOfItChanges() {
        boolean live = ChunkMarks.live;
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            TestMap map = foldMap();
            var grass = map.sprite("blends_natural_01_0", zombie.iso.SpriteDetails.IsoObjectType.normal);
            Terrain.forget();
            MapAhead.forget();
            Terrain.frame(4000);
            Terrain.loaded(1);
            String first = chunkAtNought(map);
            int version = Terrain.HILLS.version(0, 0);
            check(Terrain.HILLS.kerb(0, 0) != null, "the fold test's chunk has kerbs");
            Map<String, Change> unchanged = new LinkedHashMap<>();
            unchanged.put("ground loaded far off", far -> {
                for (int x = -240; x < -232; x++) {
                    for (int y = -240; y < -232; y++) {
                        far.cell.square(x, y, 0).floor(grass);
                    }
                }
            });
            unchanged.put("ground read ahead far off", far -> {
                MapAhead.Guess guess = new MapAhead.Guess(30, 30);
                Arrays.fill(guess.kind, Terrain.NATURAL);
                Arrays.fill(guess.house, Footings.NONE);
                Arrays.fill(guess.above, Footings.OPEN);
                MapAhead.put(guess);
            });
            int epoch = 2;
            for (Map.Entry<String, Change> change : unchanged.entrySet()) {
                change.getValue().make(map);
                long before = again();
                Terrain.frame(4000 + epoch);
                Terrain.loaded(epoch++);
                Terrain.HILLS.fromMap(map.cell, 0, 0);
                check(again() == before, "with " + change.getKey() + " and none of the chunks the heights went by changed, they are kept without being worked out again");
                String kept = chunkAtNought(map);
                check(kept.equals(first) && Terrain.HILLS.version(0, 0) == version, "with " + change.getKey() + ", the heights kept are the heights and the version they were");
            }
            check(chunkAtNought(map).equals(chunkAtNoughtAfresh(map, epoch++)), "heights kept by the fold are what working them out afresh gives");

            var road = map.sprite("blends_street_01_0", zombie.iso.SpriteDetails.IsoObjectType.normal);
            Map<String, Change> changes = new LinkedHashMap<>();
            changes.put("ground loaded at the far edge of what the heights go by", changed -> {
                for (int x = 40; x < 48; x++) {
                    for (int y = 0; y < 8; y++) {
                        changed.cell.square(x, y, 0).floor(y >= 2 && y < 6 ? road : grass);
                    }
                }
            });
            changes.put("the far shed loaded beyond what the heights go by, its footing moved", changed -> filling(changed, -7, 2, false));
            changes.put("water loaded in a valley of the chunk", changed -> filling(changed, -4, -3, false));
            changes.put("water loaded beyond what the heights go by near the shed, its pad moved", changed -> filling(changed, -7, 1, false));
            for (Map.Entry<String, Change> change : changes.entrySet()) {
                TestMap changed = foldMap();
                Terrain.forget();
                Terrain.frame(4000 + epoch);
                Terrain.loaded(epoch++);
                chunkAtNought(changed);
                change.getValue().make(changed);
                long was = folded();
                Terrain.frame(4000 + epoch);
                Terrain.loaded(epoch++);
                Terrain.HILLS.fromMap(changed.cell, 0, 0);
                check(folded() == was, "the fold sees " + change.getKey());
                String worked = chunkAtNought(changed);
                check(worked.equals(chunkAtNoughtAfresh(changed, epoch++)), "with " + change.getKey() + ", the heights are what working them out afresh gives");
            }
            // On the level land every pad is 0, so a footing that moves leaves every pad as it was.
            TestMap moved = foldMap();
            Terrain.forget();
            Terrain.frame(4000 + epoch);
            Terrain.loaded(epoch++);
            Terrain.LEVEL.corners(moved.cell, 0, 0);
            filling(moved, -7, 2, false);
            long was = folded();
            Terrain.frame(4000 + epoch);
            Terrain.loaded(epoch++);
            Terrain.LEVEL.fromMap(moved.cell, 0, 0);
            check(folded() == was, "on the level land, the fold sees the far shed's footing move with no pad moved");
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
            MapAhead.forget();
        }
    }

    /**
     * A chunk the map is still filling has squares with no floor yet, which read as made
     * ground. Kept as read, its street would lose its kerb for the rest of the session.
     */
    private static void aChunkStillFillingIsReadAgainOnceLoaded() {
        boolean live = ChunkMarks.live;
        Terrain.forget();
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            TestMap map = new TestMap();
            var grass = map.sprite("blends_natural_01_0", zombie.iso.SpriteDetails.IsoObjectType.normal);
            var road = map.sprite("blends_street_01_0", zombie.iso.SpriteDetails.IsoObjectType.normal);
            for (int x = -24; x < 32; x++) {
                for (int y = -24; y < 32; y++) {
                    var square = map.cell.square(x, y, 0);
                    if (x >= 0 && x < 8 && y >= 0 && y < 8) {
                        square.filling(true);
                    } else {
                        square.floor(grass);
                    }
                }
            }
            Terrain.frame(3000);
            Terrain.loaded(1);
            Terrain.HILLS.corners(map.cell, 0, 0);
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    var square = map.cell.square(x, y, 0);
                    square.floor(y >= 2 && y < 6 ? road : grass);
                    square.filling(false);
                }
            }
            Terrain.frame(3001);
            Terrain.loaded(2);
            Terrain.HILLS.fromMap(map.cell, 0, 0);
            float cut = Terrain.HILLS.land(4.5f, 3.5f) - Terrain.HILLS.height(4.5f, 3.5f);
            check(Math.abs(cut - Terrain.KERB) < 1e-4f, "a street read while its chunk was still filling lies a kerb below the land once the chunk is loaded: " + cut);
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
        }
    }

    private static void streamedChunksShareTheirBorders() {
        boolean live = ChunkMarks.live;
        Terrain.forget();
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            TestMap map = new TestMap();
            var grass = map.sprite("blends_natural_01_0", zombie.iso.SpriteDetails.IsoObjectType.normal);
            var water = map.sprite("blends_natural_02_0", zombie.iso.SpriteDetails.IsoObjectType.normal, "water");
            for (int x = -8; x < 16; x++) {
                for (int y = -8; y < 16; y++) {
                    map.cell.square(x, y, 0).floor(grass);
                }
            }
            Terrain.frame(1000);
            Terrain.loaded(1);
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    Terrain.HILLS.corners(map.cell, x, y);
                }
            }
            float[] published = Terrain.HILLS.corners(map.cell, 1, 0);
            float[] saved = published.clone();
            for (int x = -48; x < 56; x++) {
                for (int y = -48; y < 56; y++) {
                    if (x < -8 || x >= 16 || y < -8 || y >= 16) {
                        map.cell.square(x, y, 0).floor(x >= 16 ? water : grass);
                    }
                }
            }
            Terrain.frame(1001);
            Terrain.loaded(2);
            Terrain.HILLS.fromMap(map.cell, 0, 0);
            check(Arrays.equals(published, saved), "streaming does not mutate published terrain arrays");
            check(!Arrays.equals(saved, Terrain.HILLS.corners(map.cell, 1, 0)), "a neighbouring border follows the refreshed chunk");
            for (int x = -1; x <= 0; x++) {
                for (int y = -1; y <= 0; y++) {
                    float[] here = Terrain.HILLS.corners(map.cell, x, y);
                    float[] east = Terrain.HILLS.corners(map.cell, x + 1, y);
                    float[] south = Terrain.HILLS.corners(map.cell, x, y + 1);
                    for (int i = 0; i < 9; i++) {
                        check(here[72 + i] == east[i] && here[i * 9 + 8] == south[i * 9],
                                "shared terrain borders agree while adjacent chunks wait for refresh");
                    }
                }
            }
            check(Arrays.equals(Terrain.HILLS.water(0, 0), new float[81]), "dry chunks do not track unused water heights");
            Terrain.HILLS.corners(map.cell, 2, 0);
            check(Math.abs(Terrain.HILLS.water(2, 0)[0] - Terrain.waterLevel(1f, 16f, 0f)) < 1e-5f,
                    "wet chunks retain their water height");
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
        }
    }

    /** The test town's chunks, TOWN_X by TOWN_Y from (0, 0), and how far round it the map's files have grass. */
    private static final int TOWN_X = 8, TOWN_Y = 12, TOWN_MARGIN = 7;
    /** The town's houses: their first corner, their last, and their storeys. The south walls of the first, second and last stand on a chunk's northern edge. */
    private static final int[][] HOUSES = {{2, 4, 12, 16, 1}, {20, 28, 32, 40, 1}, {52, 30, 60, 48, 2}, {24, 76, 40, 88, 1}};

    /**
     * A town of houses, streets with their pavements and lines, a court with its hoops, a shed, a
     * garden wall, a parasol and a pond, with walls, the shed, the court and the parasol across
     * the edges between rows of chunks. As the save has it, or as the map's files have it, which
     * have no last house and a second shed the save does not.
     */
    private static final class Town {
        final TestMap map = new TestMap();
        final boolean asShipped;
        final IsoSprite grass, water, road, pavement, line, inside, roof, northWall, westWall, surface, hoop, sign;
        final IsoSprite[] parasol = new IsoSprite[4];
        final IsoBuilding[] houses = new IsoBuilding[HOUSES.length];

        Town(boolean asShipped) {
            this.asShipped = asShipped;
            grass = map.spriteWith("blends_natural_01_0", IsoObjectType.normal, map.painted, "solidfloor", "");
            water = map.spriteWith("blends_natural_02_0", IsoObjectType.normal, map.painted, "solidfloor", "", "water", "");
            road = map.spriteWith("blends_street_01_0", IsoObjectType.normal, map.painted, "solidfloor", "");
            pavement = map.spriteWith("blends_street_01_48", IsoObjectType.normal, map.painted, "solidfloor", "", "FloorMaterial", "Road_04");
            line = map.sprite("street_trafficlines_01_2", IsoObjectType.normal);
            inside = map.spriteWith("floors_interior_tilesandwood_01_0", IsoObjectType.normal, map.painted, "solidfloor", "");
            roof = map.sprite("roofs_02_0", IsoObjectType.WestRoofB, "BlockRain");
            northWall = map.sprite("walls_exterior_house_01_1", IsoObjectType.wall, "WallN");
            westWall = map.sprite("walls_exterior_house_01_0", IsoObjectType.wall, "WallW");
            surface = map.sprite("recreational_sports_01_22", IsoObjectType.normal);
            hoop = map.sprite("recreational_sports_01_24", IsoObjectType.normal);
            sign = map.sprite("signs_one-off_01_0", IsoObjectType.normal);
            String[] facing = {"N", "E", "W", "S"};
            for (int quarter = 0; quarter < 4; quarter++) {
                parasol[quarter] = map.spriteWith("walls_decoration_01_8" + quarter, IsoObjectType.WestRoofT, map.painted, "CustomName", "Canopy", "GroupName", "Curved red", "Facing", facing[quarter]);
            }
            for (int h = 0; h < HOUSES.length; h++) {
                int[] house = HOUSES[h];
                houses[h] = new IsoBuilding(new BuildingDef(h + 1, house[0], house[1], house[2] - house[0], house[3] - house[1]));
            }
        }

        /** Every sprite of the town. */
        List<IsoSprite> sprites() {
            List<IsoSprite> all = new ArrayList<>(List.of(grass, water, road, pavement, line, inside, roof, northWall, westWall, surface, hoop, sign));
            all.addAll(List.of(parasol));
            return all;
        }

        private static boolean street(int x, int y) {
            return y >= 22 && y < 26 || y >= 70 && y < 74 || x >= 14 && x < 18 || x >= 46 && x < 50;
        }

        private static boolean nearStreet(int x, int y) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (street(x + dx, y + dy)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** The house a square is in, or -1. */
        private int house(int x, int y) {
            for (int h = 0; h < HOUSES.length; h++) {
                int[] house = HOUSES[h];
                if ((h != 3 || !asShipped) && x >= house[0] && x < house[2] && y >= house[1] && y < house[3]) {
                    return h;
                }
            }
            return -1;
        }

        private boolean shed(int x, int y) {
            return x >= 4 && x < 9 && y >= 62 && y < 66 || asShipped && x >= 52 && x < 56 && y >= 58 && y < 61;
        }

        /** Puts the squares of a chunk on the map: the town's, or grass beyond it. */
        void lay(int wx, int wy) {
            boolean inTown = wx >= 0 && wx < TOWN_X && wy >= 0 && wy < TOWN_Y;
            for (int i = 0; i < Terrain.CHUNK_TILES; i++) {
                for (int j = 0; j < Terrain.CHUNK_TILES; j++) {
                    int x = wx * Terrain.CHUNK_TILES + i, y = wy * Terrain.CHUNK_TILES + j;
                    if (inTown) {
                        layTown(x, y);
                    } else {
                        map.cell.square(x, y, 0).floor(grass);
                    }
                }
            }
        }

        private void layTown(int x, int y) {
            IsoGridSquare square = map.cell.square(x, y, 0);
            int house = house(x, y);
            boolean pond = x >= 56 && y >= 80;
            square.floor(pond ? water : street(x, y) ? road : house >= 0 ? inside : nearStreet(x, y) ? pavement : grass);
            if (street(x, y) && y == 24 && !(x >= 14 && x < 18 || x >= 46 && x < 50)) {
                square.add(line);
            }
            if (x >= 2 && x < 8 && y >= 34 && y < 50) {
                square.add(x == 4 && (y == 35 || y == 48) ? hoop : surface);
            }
            if (shed(x, y)) {
                square.add(roof);
            }
            for (int h = 0; h < HOUSES.length; h++) {
                int[] box = HOUSES[h];
                if (h == 3 && asShipped) {
                    continue;
                }
                if (x >= box[0] && x < box[2] && (y == box[1] || y == box[3])) {
                    square.add(northWall);
                }
                if (y >= box[1] && y < box[3] && (x == box[0] || x == box[2])) {
                    square.add(westWall);
                }
            }
            if (y == 56 && x >= 20 && x < 30) {
                square.add(northWall);
            }
            for (int quarter = 0; quarter < 4; quarter++) {
                if (x == 36 + Parasols.ALONG_X[quarter] && y == 55 + Parasols.ALONG_Y[quarter]) {
                    square.add(parasol[quarter]);
                }
            }
            if (house >= 0) {
                square.building(houses[house]);
                square.setRoomID(house + 1);
                if (HOUSES[house][4] > 1) {
                    IsoGridSquare upstairs = map.cell.square(x, y, 1);
                    upstairs.floor(inside);
                    upstairs.building(houses[house]);
                    upstairs.setRoomID(house + 101);
                }
            }
            if (x == 20 && y == 21) {
                map.cell.square(x, y, 1).add(sign);
            }
        }
    }

    /** A lotpack of one chunk, the chunk at (0, 0) of its cell and every other the same: the tiles of each square, by level from 0 to 1, x and y. */
    private static ByteBuffer packOf(int[][] tiles) {
        ByteBuffer out = ByteBuffer.allocate(1 << 16).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(1024);
        int table = out.position(), data = table + 1024 * 8;
        for (int chunk = 0; chunk < 1024; chunk++) {
            out.putLong(table + chunk * 8, data);
        }
        out.position(data);
        int empty = 0;
        for (int[] held : tiles) {
            if (held.length == 0) {
                empty++;
                continue;
            }
            if (empty > 0) {
                out.putInt(-1).putInt(empty);
                empty = 0;
            }
            out.putInt(held.length + 1).putInt(-1);
            for (int tile : held) {
                out.putInt(tile);
            }
        }
        if (empty > 0) {
            out.putInt(-1).putInt(empty);
        }
        out.flip();
        return out;
    }

    /**
     * Has MapAhead read the town and the grass round it from the map's files, the chunks from
     * fromX east: as the save has the town, or with the map's own differences.
     */
    private static void readTownAhead(boolean asShipped, int fromX) {
        Town shipped = new Town(asShipped);
        List<IsoSprite> sprites = shipped.sprites();
        int[] traits = new int[sprites.size()];
        IdentityHashMap<IsoSprite, Integer> tileOf = new IdentityHashMap<>();
        for (int tile = 0; tile < traits.length; tile++) {
            traits[tile] = MapAhead.traits(sprites.get(tile));
            tileOf.put(sprites.get(tile), tile);
        }
        MapAhead.Rooms rooms = (x, y, z) -> roomOf(shipped.map.cell, x, y, z);
        for (int wx = -TOWN_MARGIN; wx < TOWN_X + TOWN_MARGIN; wx++) {
            for (int wy = -TOWN_MARGIN; wy < TOWN_Y + TOWN_MARGIN; wy++) {
                shipped.lay(wx, wy);
            }
        }
        for (int wx = Math.max(fromX, -TOWN_MARGIN); wx < TOWN_X + TOWN_MARGIN; wx++) {
            for (int wy = -TOWN_MARGIN; wy < TOWN_Y + TOWN_MARGIN; wy++) {
                int[][] tiles = new int[2 * Terrain.CHUNK_TILES * Terrain.CHUNK_TILES][];
                for (int z = 0; z <= 1; z++) {
                    for (int i = 0; i < Terrain.CHUNK_TILES; i++) {
                        for (int j = 0; j < Terrain.CHUNK_TILES; j++) {
                            IsoGridSquare square = shipped.map.cell.getGridSquare(wx * Terrain.CHUNK_TILES + i, wy * Terrain.CHUNK_TILES + j, z);
                            PZArrayList<IsoObject> objects = square == null ? new PZArrayList<>() : square.getObjects();
                            int[] held = new int[objects.size()];
                            for (int k = 0; k < held.length; k++) {
                                held[k] = tileOf.get(objects.get(k).getSprite());
                            }
                            tiles[(z * Terrain.CHUNK_TILES + i) * Terrain.CHUNK_TILES + j] = held;
                        }
                    }
                }
                MapAhead.put(MapAhead.guess(MapAhead.read(0, packOf(tiles), traits, 0, 1, wx, wy, 0, 0), rooms));
            }
        }
    }

    /** The heights, water and kerbs of the town's chunks and the ring round them, by chunk, worked out with the frame's budget no object. */
    private static Map<String, String> townHeights(Town town, int frame, int epoch) {
        return heightsIn(town.map.cell, -1, -1, TOWN_X, TOWN_Y, frame, epoch);
    }

    /** The same of the chunks from (fromX, fromY) to (toX, toY). */
    private static Map<String, String> heightsIn(IsoCell cell, int fromX, int fromY, int toX, int toY, int frame, int epoch) {
        Terrain.frame(frame);
        Terrain.loaded(epoch);
        for (int wx = fromX; wx <= toX; wx++) {
            for (int wy = fromY; wy <= toY; wy++) {
                Terrain.HILLS.fromMap(cell, wx, wy);
            }
        }
        Map<String, String> heights = new LinkedHashMap<>();
        for (int wx = fromX; wx <= toX; wx++) {
            for (int wy = fromY; wy <= toY; wy++) {
                heights.put(wx + "," + wy, Arrays.toString(Terrain.HILLS.corners(cell, wx, wy)) + Arrays.toString(Terrain.HILLS.water(wx, wy))
                        + Arrays.toString(Terrain.HILLS.kerb(wx, wy)));
            }
        }
        return heights;
    }

    /** The chunks whose heights two of heightsIn's differ in. */
    private static List<String> differing(Map<String, String> one, Map<String, String> other) {
        List<String> differing = new ArrayList<>();
        for (Map.Entry<String, String> chunk : one.entrySet()) {
            if (!chunk.getValue().equals(other.get(chunk.getKey()))) {
                differing.add(chunk.getKey());
            }
        }
        return differing;
    }

    /** What is over a square, as the record of its chunk has it worked out (Terrain.Kinds.over). */
    private static byte overAt(int x, int y) {
        Object kinds = derivedRecord(Math.floorDiv(x, Terrain.CHUNK_TILES), Math.floorDiv(y, Terrain.CHUNK_TILES));
        return TestMap.<byte[]>read(kinds, "over")[Math.floorMod(x, Terrain.CHUNK_TILES) * Terrain.CHUNK_TILES + Math.floorMod(y, Terrain.CHUNK_TILES)];
    }

    /** The building a square is part of the pad of, as the record of its chunk has it (Terrain.Kinds.house). */
    private static long houseAt(int x, int y) {
        Object kinds = derivedRecord(Math.floorDiv(x, Terrain.CHUNK_TILES), Math.floorDiv(y, Terrain.CHUNK_TILES));
        return TestMap.<long[]>read(kinds, "house")[Math.floorMod(x, Terrain.CHUNK_TILES) * Terrain.CHUNK_TILES + Math.floorMod(y, Terrain.CHUNK_TILES)];
    }

    /** What stands on the ground of a square, as the record of its chunk has it (Terrain.Kinds.stands). */
    private static short standsAt(int x, int y) {
        Object kinds = derivedRecord(Math.floorDiv(x, Terrain.CHUNK_TILES), Math.floorDiv(y, Terrain.CHUNK_TILES));
        return TestMap.<short[]>read(kinds, "stands")[Math.floorMod(x, Terrain.CHUNK_TILES) * Terrain.CHUNK_TILES + Math.floorMod(y, Terrain.CHUNK_TILES)];
    }

    /** The record of a chunk (Terrain.Kinds), which must have had what is over its squares worked out. */
    private static Object derivedRecord(int wx, int wy) {
        for (Object kinds : TestMap.<Object[]>read(null, Terrain.class, "records")) {
            if (kinds != null && TestMap.<Integer>read(kinds, "wx") == wx && TestMap.<Integer>read(kinds, "wy") == wy) {
                if (!TestMap.<Boolean>read(kinds, "derived")) {
                    throw new IllegalStateException("the record of the chunk at " + wx + ", " + wy + " has not had what is over its squares worked out");
                }
                return kinds;
            }
        }
        throw new IllegalStateException("no record of the chunk at " + wx + ", " + wy);
    }

    /**
     * A chunk's heights go by the ground round it, and the map loads a row of chunks at a time
     * as the player goes: for a given map and save, they come out the same whatever order the
     * chunks loaded in. Streamed a row at a time south and north, with the ground beyond read
     * ahead as the save has it, not at all, and as the map's files have it, the town ends with
     * every chunk at the heights it has loaded whole and worked out from nothing.
     */
    private static void aTownStreamedARowAtATimeHasTheHeightsOfOneLoadedWhole() throws Throwable {
        boolean live = ChunkMarks.live;
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            int epoch = 0;
            for (String ahead : new String[] {"as the save has it", "not at all", "as the map's files have it"}) {
                for (boolean southward : new boolean[] {true, false}) {
                    MapAhead.forget();
                    if (!ahead.equals("not at all")) {
                        readTownAhead(ahead.equals("as the map's files have it"), -TOWN_MARGIN);
                    }
                    Terrain.forget();
                    Town town = new Town(false);
                    for (int row = 0; row < TOWN_Y; row++) {
                        int wy = southward ? row : TOWN_Y - 1 - row;
                        for (int wx = 0; wx < TOWN_X; wx++) {
                            town.lay(wx, wy);
                        }
                        epoch++;
                        Terrain.frame(7000 + epoch);
                        Terrain.loaded(epoch);
                        int first = southward ? 0 : wy, last = southward ? wy : TOWN_Y - 1;
                        for (int y = first - 1; y <= last + 1; y++) {
                            for (int x = -1; x <= TOWN_X; x++) {
                                Terrain.HILLS.corners(town.map.cell, x, y);
                            }
                        }
                    }
                    epoch++;
                    Map<String, String> streamed = townHeights(town, 7000 + epoch, epoch);
                    Terrain.forget();
                    epoch++;
                    Map<String, String> whole = townHeights(town, 7000 + epoch, epoch);
                    List<String> differing = new ArrayList<>();
                    for (Map.Entry<String, String> chunk : whole.entrySet()) {
                        if (!chunk.getValue().equals(streamed.get(chunk.getKey()))) {
                            differing.add(chunk.getKey());
                        }
                    }
                    check(differing.isEmpty(), "streamed " + (southward ? "south" : "north") + " with the ground beyond read ahead " + ahead + ", every chunk has the heights it has loaded whole: "
                            + differing.size() + " of " + whole.size() + " differ, " + differing.subList(0, Math.min(12, differing.size())));
                }
            }
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
            MapAhead.forget();
        }
    }

    /** The test town's chunks put on the map, with nothing round them, and Terrain and MapAhead holding nothing of any map. */
    private static Town loadedTown() {
        MapAhead.forget();
        Terrain.forget();
        Town town = new Town(false);
        for (int wx = 0; wx < TOWN_X; wx++) {
            for (int wy = 0; wy < TOWN_Y; wy++) {
                town.lay(wx, wy);
            }
        }
        return town;
    }

    private static long toll(String name) {
        return TestMap.<Long>read(null, Terrain.class, name);
    }

    /**
     * The records of chunks far from every eye are let go, and whatever went by them is worked
     * out again from the map as it is when next asked for: the town's heights, with every
     * record round it let go while the eye was away, are as they were once the eye is back.
     */
    private static void recordsLetGoFarFromEveryEyeAreReadAgainAsTheyWere() {
        boolean live = ChunkMarks.live;
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            Town town = loadedTown();
            Terrain.Rework<IsoCell> none = (cell, wx, wy) -> {};
            float townX = TOWN_X * Terrain.CHUNK_TILES / 2f, townY = TOWN_Y * Terrain.CHUNK_TILES / 2f;
            Terrain.HILLS.heighten(1f, 0, townX, townY, town.map.cell, none);
            Map<String, String> before = townHeights(town, 9001, 9001);
            long dropped = toll("tollDropped"), read = toll("tollRead");
            Terrain.HILLS.heighten(1f, 0, 8000.5f, 8000.5f, town.map.cell, none);
            Terrain.frame(9002);
            Terrain.loaded(9002);
            Terrain.HILLS.corners(town.map.cell, 1000, 1000);
            check(toll("tollDropped") - dropped >= (TOWN_X + 2) * (TOWN_Y + 2), "the records round the town are let go with the eye a thousand chunks off: " + (toll("tollDropped") - dropped));
            Terrain.HILLS.heighten(1f, 0, townX, townY, town.map.cell, none);
            long readAway = toll("tollRead") - read;
            Map<String, String> after = townHeights(town, 9003, 9003);
            check(toll("tollRead") - read - readAway >= TOWN_X * TOWN_Y, "once the eye is back the town's chunks are read again: " + (toll("tollRead") - read - readAway));
            List<String> differing = new ArrayList<>();
            for (Map.Entry<String, String> chunk : before.entrySet()) {
                if (!chunk.getValue().equals(after.get(chunk.getKey()))) {
                    differing.add(chunk.getKey());
                }
            }
            check(differing.isEmpty(), "and every chunk has the heights it had before the eye went: " + differing.size() + " of " + before.size() + " differ, " + differing.subList(0, Math.min(12, differing.size())));
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
            MapAhead.forget();
        }
    }

    /**
     * A place in the records holds as many chunks a table's side apart as it has ways, and one
     * more puts out the record asked for longest ago: what went by the records put out is worked
     * out again from the map when next asked for, and comes out as it was.
     */
    private static void recordsPutOutForRoomAreReadAgainAsTheyWere() {
        boolean live = ChunkMarks.live;
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            MapAhead.forget();
            Terrain.forget();
            TestMap map = new TestMap();
            IsoSprite grass = map.spriteWith("blends_natural_01_0", IsoObjectType.normal, map.painted, "solidfloor", "");
            IsoSprite water = map.spriteWith("blends_natural_02_0", IsoObjectType.normal, map.painted, "solidfloor", "", "water", "");
            int ways = TestMap.<Integer>read(null, Terrain.class, "RECORD_WAYS"), apart = TestMap.<Integer>read(null, Terrain.class, "RECORD_SIDE");
            for (int place = 0; place <= ways; place++) {
                int from = place * apart * Terrain.CHUNK_TILES;
                for (int x = from - 24; x < from + 32; x++) {
                    for (int y = -24; y < 32; y++) {
                        boolean pond = x >= from + 2 + place && x < from + 7 + place && y >= 2 && y < 6;
                        map.cell.square(x, y, 0).floor(pond ? water : grass);
                    }
                }
            }
            long dropped = toll("tollDropped");
            List<String> heights = new ArrayList<>();
            for (int place = 0; place <= ways; place++) {
                Terrain.frame(9101 + place);
                Terrain.loaded(9101 + place);
                heights.add(Arrays.toString(Terrain.HILLS.corners(map.cell, place * apart, 0)) + Arrays.toString(Terrain.HILLS.water(place * apart, 0)));
                check(place < ways || toll("tollDropped") > dropped, "the place after the last way puts out records for room: " + (toll("tollDropped") - dropped));
            }
            check(!heights.get(0).equals(heights.get(1)), "the places' ponds are a tile apart, and so are their heights: " + heights.get(0));
            Terrain.frame(9200);
            Terrain.loaded(9200);
            String again = Arrays.toString(Terrain.HILLS.corners(map.cell, 0, 0)) + Arrays.toString(Terrain.HILLS.water(0, 0));
            check(again.equals(heights.get(0)), "and the first place's heights, asked for again, are as they were: " + heights.get(0) + " then " + again);
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
            MapAhead.forget();
        }
    }

    /**
     * A row of [Content] switched tells what is over a parasol's squares otherwise
     * (Terrain.reread): the town's heights then are those it has worked out afresh with the
     * row as it is now.
     */
    private static void aRowOfContentSwitchedGivesTheHeightsWorkedOutAfresh() {
        boolean live = ChunkMarks.live;
        int rows = Own.rows();
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            Town town = loadedTown();
            Own.set(Own.ALL);
            Map<String, String> furnished = townHeights(town, 9301, 9301);
            Own.set(Own.ALL & ~Own.FURNITURE);
            Terrain.reread();
            Map<String, String> switched = townHeights(town, 9302, 9302);
            Terrain.forget();
            Map<String, String> afresh = townHeights(town, 9303, 9303);
            List<String> differing = new ArrayList<>(), changed = new ArrayList<>();
            for (Map.Entry<String, String> chunk : afresh.entrySet()) {
                if (!chunk.getValue().equals(switched.get(chunk.getKey()))) {
                    differing.add(chunk.getKey());
                }
                if (!chunk.getValue().equals(furnished.get(chunk.getKey()))) {
                    changed.add(chunk.getKey());
                }
            }
            check(!changed.isEmpty(), "the parasol's canopy, the game's own, is built over its squares, and the heights round it are others: " + changed);
            check(differing.isEmpty(), "and with the row switched the town has the heights worked out afresh: " + differing.size() + " of " + afresh.size() + " differ, "
                    + differing.subList(0, Math.min(12, differing.size())));
        } finally {
            Own.set(rows);
            ChunkMarks.live = live;
            Terrain.forget();
            MapAhead.forget();
        }
    }

    /**
     * What MapAhead read of a chunk is let go of once no view's window holds it, so the heights
     * round it go by where the eyes are, not where they have been. Driven past a town read
     * ahead, east till the town is out of the window, the ground beside it has the heights of
     * the same ground read afresh there; and come back, those it has read afresh with the town.
     */
    private static void groundReadAheadAndLeftBehindHasTheHeightsOfGroundReadAfresh() {
        boolean live = ChunkMarks.live;
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            int side = 47, fromY = -18, westX = -20, eastX = TOWN_X, toX = TOWN_X + 5;
            IsoCell nothingLoaded = new TestMap().cell;
            MapAhead.forget();
            Terrain.forget();
            MapAhead.window(0, westX, fromY, side);
            readTownAhead(false, -TOWN_MARGIN);
            Map<String, String> beside = heightsIn(nothingLoaded, eastX, 0, toX, TOWN_Y - 1, 9401, 9401);
            MapAhead.window(0, eastX, fromY, side);
            Map<String, String> passed = heightsIn(nothingLoaded, eastX, 0, toX, TOWN_Y - 1, 9402, 9401);
            MapAhead.window(0, westX, fromY, side);
            readTownAhead(false, -TOWN_MARGIN);
            Map<String, String> back = heightsIn(nothingLoaded, eastX, 0, toX, TOWN_Y - 1, 9403, 9401);

            MapAhead.forget();
            Terrain.forget();
            MapAhead.window(0, eastX, fromY, side);
            readTownAhead(false, eastX);
            Map<String, String> afresh = heightsIn(nothingLoaded, eastX, 0, toX, TOWN_Y - 1, 9404, 9404);
            check(!differing(beside, afresh).isEmpty(), "the town read ahead beside the ground has the ground at heights of its own");
            List<String> left = differing(afresh, passed);
            check(left.isEmpty(), "the town left out of the window, the ground beside it has the heights it has read afresh without it: " + left.size() + " of " + afresh.size()
                    + " differ, " + left.subList(0, Math.min(12, left.size())));
            MapAhead.forget();
            Terrain.forget();
            MapAhead.window(0, westX, fromY, side);
            readTownAhead(false, -TOWN_MARGIN);
            Map<String, String> withTown = heightsIn(nothingLoaded, eastX, 0, toX, TOWN_Y - 1, 9405, 9405);
            List<String> returned = differing(withTown, back);
            check(returned.isEmpty(), "and come back, the heights it has read afresh with the town: " + returned.size() + " of " + withTown.size() + " differ, "
                    + returned.subList(0, Math.min(12, returned.size())));
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
            MapAhead.forget();
        }
    }

    /**
     * On every load of a chunk the game makes a square above the ground where something is on
     * it, and in the 3 x 3 round that in the chunk (Terrain.above). A square on the chunk's edge
     * whose only neighbour with something on it is in the next chunk it makes only when that
     * chunk loads after its own (IsoChunk.loadInMainThread), with the room the map has there, so
     * the room is nothing over the ground: under a room upstairs over the edge between two
     * chunks, with signs up there all along the far side of the edge, the near chunk's edge is
     * open and the far chunk's squares by the signs are built over, whichever chunk loads first,
     * and the heights are those of both loaded at once.
     */
    private static void aSquareTheGameMakesOnAChunksEdgeForTheChunkBesideItIsNothingOverTheGround() {
        boolean live = ChunkMarks.live;
        Terrain.Rooms rooms = Terrain.rooms;
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        int besideX = 3, besideY = 1, edgeX = besideX * Terrain.CHUNK_TILES - 1, fromY = besideY * Terrain.CHUNK_TILES;
        IsoBuilding upstairs = new IsoBuilding(new BuildingDef(9, edgeX - 3, fromY, 8, Terrain.CHUNK_TILES));
        long middle = Footings.key(edgeX + 1f, fromY + Terrain.CHUNK_TILES / 2f);
        try {
            ChunkMarks.live = true;
            // The map's room upstairs, over the four squares either side of the edge, all along it.
            Terrain.rooms = (cell, x, y, z) -> z == 1 && x >= edgeX - 3 && x <= edgeX + 4 && y >= fromY && y < fromY + Terrain.CHUNK_TILES ? middle : roomOf(cell, x, y, z);
            Map<String, String> nearFirst = null;
            for (boolean near : new boolean[] {true, false}) {
                MapAhead.forget();
                Terrain.forget();
                Town town = new Town(false);
                IsoCell cell = town.map.cell;
                Runnable rest = () -> {
                    for (int wx = -2; wx <= 6; wx++) {
                        for (int wy = -2; wy <= 4; wy++) {
                            if (wx != besideX || wy != besideY) {
                                town.lay(wx, wy);
                            }
                        }
                    }
                };
                Runnable far = () -> {
                    town.lay(besideX, besideY);
                    for (int y = fromY; y < fromY + Terrain.CHUNK_TILES; y++) {
                        cell.square(edgeX + 1, y, 1).add(town.sign);
                    }
                };
                int frame = near ? 9601 : 9604;
                if (near) {
                    rest.run();
                    heightsIn(cell, 0, -1, 5, 3, frame, frame);
                    check(overAt(edgeX, fromY) == Footings.OPEN, "the near chunk's edge, with nothing over it, is open");
                    far.run();
                    for (int y = fromY; y < fromY + Terrain.CHUNK_TILES; y++) {
                        IsoGridSquare made = cell.square(edgeX, y, 1);
                        made.building(upstairs);
                        made.setRoomID(9);
                    }
                } else {
                    far.run();
                    heightsIn(cell, 0, -1, 5, 3, frame, frame);
                    rest.run();
                }
                String order = near ? " with the near chunk loaded first" : " with the far chunk loaded first";
                Map<String, String> streamed = heightsIn(cell, 0, -1, 5, 3, frame + 1, frame + 1);
                check(overAt(edgeX, fromY) == Footings.OPEN && overAt(edgeX, fromY + 7) == Footings.OPEN,
                        "the near chunk's edge under the room is open" + order + ": " + overAt(edgeX, fromY) + " " + overAt(edgeX, fromY + 7));
                check(overAt(edgeX + 1, fromY) == Footings.BUILT && overAt(edgeX + 2, fromY + 7) == Footings.BUILT && overAt(edgeX + 3, fromY + 3) == Footings.OPEN,
                        "and the far chunk's squares under the signs and round them in it are built over, and none further off," + order);
                Terrain.forget();
                Map<String, String> whole = heightsIn(cell, 0, -1, 5, 3, frame + 2, frame + 2);
                List<String> differ = differing(whole, streamed);
                check(differ.isEmpty(), "every chunk has the heights it has with both loaded at once" + order + ": " + differ.size() + " of " + whole.size() + " differ, "
                        + differ.subList(0, Math.min(12, differ.size())));
                if (nearFirst == null) {
                    nearFirst = streamed;
                } else {
                    differ = differing(nearFirst, streamed);
                    check(differ.isEmpty(), "and the heights are the same whichever chunk loads first: " + differ.size() + " of " + streamed.size() + " differ, "
                            + differ.subList(0, Math.min(12, differ.size())));
                }
            }
        } finally {
            Terrain.rooms = rooms;
            ChunkMarks.live = live;
            Terrain.forget();
            MapAhead.forget();
        }
    }

    /**
     * A loaded chunk's rooms are the map's (Terrain.rooms), as the game gives them when the chunk
     * loads from the save (IsoChunk.LoadFromDiskOrBufferInternal). On the chunk's first load it
     * gives no room to a square the lotpack has no tile on, on the ground or beside a tile above
     * it (IsoCell.PlaceLot), and it makes a square above the ground for a tile it then lays
     * nothing for, which the save does not keep (IsoChunk.Save). A house whose room upstairs runs
     * out over the yard, with a square of its ground and one of its upstairs that have no tile,
     * and a basement out under the yard, has the same heights on its first load as from the save.
     */
    private static void aChunkHasTheHeightsOnItsFirstLoadThatItHasFromTheSave() {
        boolean live = ChunkMarks.live;
        Terrain.Rooms rooms = Terrain.rooms;
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        long middle = Footings.key(12f, 12f);
        try {
            ChunkMarks.live = true;
            // The map's house: on the ground over x 9 to 14 and y 9 to 11, with a basement out to y 12 and a room upstairs out to y 15.
            Terrain.rooms = (cell, x, y, z) -> x >= 9 && x < 15 && y >= 9 && y < (z == 0 ? 12 : z == -1 ? 13 : z == 1 ? 16 : 9) ? middle : Footings.NONE;
            Map<String, String> first = null;
            for (boolean fromSave : new boolean[] {false, true}) {
                MapAhead.forget();
                Terrain.forget();
                Town town = new Town(false);
                IsoCell cell = town.map.cell;
                IsoBuilding house = new IsoBuilding(new BuildingDef(9, 9, 9, 6, 6));
                for (int x = -16; x < 40; x++) {
                    for (int y = -16; y < 40; y++) {
                        IsoGridSquare ground = cell.square(x, y, 0);
                        boolean inside = x >= 9 && x < 15 && y >= 9 && y < 12, tiled = x != 10 || y != 10;
                        if (tiled) {
                            ground.floor(inside ? town.inside : town.grass);
                        }
                        if (inside && (tiled || fromSave)) {
                            ground.building(house);
                        }
                    }
                }
                // Upstairs: the floor, and the squares the game makes round it on every load, which on the first load have no room where they have no tile.
                for (int x = 8; x < 16; x++) {
                    for (int y = 8; y < 15; y++) {
                        IsoGridSquare upstairs = cell.square(x, y, 1);
                        boolean floored = x >= 9 && x < 15 && y >= 9 && y < 14 && (x != 11 || y != 13);
                        if (floored) {
                            upstairs.floor(town.inside);
                        }
                        if (floored || fromSave && x >= 9 && x < 15 && y >= 9) {
                            upstairs.building(house);
                        }
                    }
                }
                if (!fromSave) {
                    // A tile the game drops, two squares past the floor: its square, in the room, and the squares round it.
                    for (int x = 11; x < 14; x++) {
                        for (int y = 14; y < 17; y++) {
                            cell.square(x, y, 1);
                        }
                    }
                    cell.square(12, 15, 1).building(house);
                }
                String load = fromSave ? " from the save" : " on the first load";
                int frame = fromSave ? 9612 : 9611;
                Map<String, String> heights = heightsIn(cell, -1, -1, 3, 3, frame, frame);
                check(houseAt(10, 10) == middle && (standsAt(10, 10) & Terrain.ROOMED) != 0, "a square of the ground in a room, with no tile, is the house's, with the room on it," + load);
                check(houseAt(12, 12) == middle, "and so is a square of the yard over the basement, with no square under it," + load);
                check(overAt(11, 13) == Footings.BUILT && overAt(12, 14) == Footings.BUILT,
                        "the room upstairs is over the yard on the squares with no tile the game makes beside its floor" + load + ": " + overAt(11, 13) + " " + overAt(12, 14));
                check(overAt(12, 15) == Footings.OPEN, "and over none of it where the game makes a square only for a tile it drops" + load + ": " + overAt(12, 15));
                if (first == null) {
                    first = heights;
                } else {
                    List<String> differ = differing(first, heights);
                    check(differ.isEmpty(), "and the chunks have the same heights on the first load as from the save: " + differ.size() + " of " + heights.size() + " differ, "
                            + differ.subList(0, Math.min(12, differ.size())));
                }
            }
        } finally {
            Terrain.rooms = rooms;
            ChunkMarks.live = live;
            Terrain.forget();
            MapAhead.forget();
        }
    }

    /**
     * The map is asked whether a chunk is loaded, by its middle square, once for each record
     * when it has other chunks loaded, and once for each record read. When only what MapAhead
     * read is another, the records are not checked: the record of the chunk read is read again,
     * and the map is asked about that chunk alone. A chunk that loads beside a loaded one has
     * its own record read again and no other.
     */
    private static void theMapIsAskedOnceAChunkWhetherItIsLoadedAndOnlyWhenItLoadsOtherChunks() {
        boolean live = ChunkMarks.live;
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            MapAhead.forget();
            Terrain.forget();
            Town town = new Town(false);
            for (int wx = -1; wx <= 1; wx++) {
                for (int wy = -1; wy <= 1; wy++) {
                    town.lay(wx + 20, wy);
                }
            }
            heightsIn(town.map.cell, 20, 0, 20, 0, 9701, 9701);
            int records = TestMap.<List<?>>read(null, Terrain.class, "kept").size();
            check(records > 9 * 9, "the heights of the loaded chunk go by records of chunks loaded and not: " + records);
            int[] floors = new int[64], grounds = new int[64];
            Arrays.fill(floors, MapAhead.FLOOR | MapAhead.NATURAL_NAME);
            Arrays.fill(grounds, MapAhead.FLOOR | MapAhead.NATURAL_NAME);
            MapAhead.put(MapAhead.guess(new MapAhead.Raw(0, 23, 2, floors, grounds, new int[64], new int[64], new byte[64], new short[64]),
                    (x, y, z) -> Footings.NONE));
            long asked = toll("tollAsked"), readAgain = toll("tollReadAgain");
            heightsIn(town.map.cell, 20, 0, 20, 0, 9702, 9701);
            check(toll("tollAsked") - asked == 1 && toll("tollReadAgain") - readAgain == 1,
                    "with only a chunk read ahead, that chunk's record alone is read again, and the map is asked about it alone: " + (toll("tollAsked") - asked) + ", " + (toll("tollReadAgain") - readAgain));
            records = TestMap.<List<?>>read(null, Terrain.class, "kept").size();
            asked = toll("tollAsked");
            heightsIn(town.map.cell, 20, 0, 20, 0, 9703, 9703);
            check(toll("tollAsked") - asked == records, "with other chunks loaded, the map is asked once for each record: " + (toll("tollAsked") - asked) + " for " + records);
            town.lay(22, 0);
            records = TestMap.<List<?>>read(null, Terrain.class, "kept").size();
            asked = toll("tollAsked");
            readAgain = toll("tollReadAgain");
            long read = toll("tollRead");
            heightsIn(town.map.cell, 20, 0, 20, 0, 9704, 9704);
            long reads = toll("tollReadAgain") - readAgain + toll("tollRead") - read;
            check(toll("tollReadAgain") - readAgain == 1, "with a chunk loaded beside the loaded ones, its record alone is read again: " + (toll("tollReadAgain") - readAgain));
            check(toll("tollAsked") - asked == records + reads,
                    "and the map is asked once for each record and once for each read: " + (toll("tollAsked") - asked) + " for " + records + " and " + reads);
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
            MapAhead.forget();
        }
    }

    /**
     * Where the game's pass over a chunk it loads throws partway, it counts the chunk loaded
     * with some squares of its ground not made (Terrain.isLoaded): the chunk's heights are
     * worked out with those squares as not loaded, and the rest as they are.
     */
    private static void aLoadedChunkWithASquareOfItsGroundNotMadeHasThatSquareAsNotLoaded() {
        boolean live = ChunkMarks.live;
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            MapAhead.forget();
            Terrain.forget();
            Town town = new Town(false);
            IsoCell cell = town.map.cell;
            for (int x = -16; x < 32; x++) {
                for (int y = -16; y < 32; y++) {
                    if (x != 9 || y != 10) {
                        cell.square(x, y, 0).floor(town.grass);
                    }
                }
            }
            heightsIn(cell, 0, 0, 2, 2, 9921, 9921);
            check(overAt(9, 10) == Footings.UNLOADED, "the square not made is not loaded: " + overAt(9, 10));
            check(overAt(10, 10) == Footings.OPEN && overAt(12, 12) == Footings.OPEN, "and the rest of its chunk is as it is: " + overAt(10, 10) + " " + overAt(12, 12));
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
            MapAhead.forget();
        }
    }

    /**
     * Each view's eye keeps the records round it: four eyes as far apart as the places of the
     * records go round, so that each one's records take the same places as the others', with
     * heights asked for out to the corners of what each keeps. None is put out for room, and
     * asked for again with the map moved on, none is read again.
     */
    private static void fourEyesFarApartEachKeepTheRecordsRoundThem() {
        boolean live = ChunkMarks.live;
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            MapAhead.forget();
            Terrain.forget();
            IsoCell nothingLoaded = new TestMap().cell;
            int apart = TestMap.<Integer>read(null, Terrain.class, "RECORD_SIDE");
            int out = TestMap.<Integer>read(null, Terrain.class, "KEPT_AROUND") - Terrain.AROUND / Terrain.CHUNK_TILES - 1;
            Terrain.Rework<IsoCell> nothing = (cell, wx, wy) -> {};
            for (int view = 0; view < View.COUNT; view++) {
                Terrain.HILLS.heighten(1f, view, view * apart * Terrain.CHUNK_TILES + 4.5f, 4.5f, nothingLoaded, nothing);
            }
            long dropped = toll("tollDropped");
            for (int pass = 0; pass < 2; pass++) {
                long read = toll("tollRead");
                Terrain.frame(9801 + pass);
                Terrain.loaded(9801 + pass);
                for (int view = 0; view < View.COUNT; view++) {
                    for (int corner = 0; corner < 4; corner++) {
                        Terrain.HILLS.corners(nothingLoaded, view * apart + (corner < 2 ? -out : out), corner % 2 == 0 ? -out : out);
                    }
                }
                check(pass == 0 || toll("tollRead") == read, "asked for again, none of the four eyes' records is read again: " + (toll("tollRead") - read));
            }
            check(toll("tollDropped") == dropped, "four eyes far apart keep every record round them, and none is put out for room: " + (toll("tollDropped") - dropped));
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
            MapAhead.forget();
        }
    }

    /**
     * A building's own wall, which the game stands on the square outside it along its south and
     * east sides, is built over that square, and a garden wall is not. A round canopy's lone
     * quarter is a roof over its square, and a whole parasol stands on the ground as furniture
     * does where the mod stands it, and is the game's roof where it does not (Terrain.standsOn).
     */
    private static void aBuildingsWallAndALoneCanopyAreBuiltOverTheGroundAndAGardenWallAndAParasolAreNot() {
        boolean live = ChunkMarks.live;
        int rows = Own.rows();
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            Own.set(Own.ALL);
            MapAhead.forget();
            Terrain.forget();
            Town town = new Town(false);
            IsoCell cell = town.map.cell;
            IsoBuilding house = new IsoBuilding(new BuildingDef(9, 4, 4, 12, 4));
            for (int x = -16; x < 48; x++) {
                for (int y = -16; y < 48; y++) {
                    IsoGridSquare square = cell.square(x, y, 0);
                    boolean in = x >= 4 && x < 16 && y >= 4 && y < 8;
                    square.floor(in ? town.inside : town.grass);
                    if (in) {
                        square.building(house);
                        square.setRoomID(9);
                    }
                    if (x >= 4 && x < 16 && y == 8 || x >= 20 && x < 28 && y == 28) {
                        square.add(town.northWall);
                    }
                    if (x == 16 && y >= 4 && y < 8 || x == 36 && y >= 20 && y < 28) {
                        square.add(town.westWall);
                    }
                }
            }
            for (int quarter = Parasols.NORTH_WEST; quarter <= Parasols.SOUTH_EAST; quarter++) {
                cell.square(8 + Parasols.ALONG_X[quarter], 24 + Parasols.ALONG_Y[quarter], 0).add(town.parasol[quarter]);
            }
            cell.square(28, 12, 0).add(town.parasol[Parasols.SOUTH_EAST]);
            heightsIn(cell, 0, 0, 4, 4, 9901, 9901);
            check(overAt(4, 8) == Footings.BUILT && overAt(15, 8) == Footings.BUILT, "a building's wall on the square south of it, across a chunk's edge, is built over that square: " + overAt(4, 8));
            check(overAt(16, 4) == Footings.BUILT && overAt(16, 7) == Footings.BUILT, "and one on the square east of it: " + overAt(16, 4));
            check(overAt(24, 28) == Footings.OPEN && overAt(36, 24) == Footings.OPEN, "a garden wall is not: " + overAt(24, 28) + " " + overAt(36, 24));
            check(overAt(28, 12) == Footings.BUILT, "a canopy's lone quarter is a roof over its square: " + overAt(28, 12));
            boolean open = true;
            for (int quarter = Parasols.NORTH_WEST; quarter <= Parasols.SOUTH_EAST; quarter++) {
                open &= overAt(8 + Parasols.ALONG_X[quarter], 24 + Parasols.ALONG_Y[quarter]) == Footings.OPEN;
            }
            check(open, "a whole parasol, the mod's furniture, stands on the ground as furniture does");
            Own.set(Own.ALL & ~Own.FURNITURE);
            Terrain.reread();
            boolean built = true;
            for (int quarter = Parasols.NORTH_WEST; quarter <= Parasols.SOUTH_EAST; quarter++) {
                built &= overAt(8 + Parasols.ALONG_X[quarter], 24 + Parasols.ALONG_Y[quarter]) == Footings.BUILT;
            }
            check(built, "and with the game's own furniture, it is the game's roof over its squares");
        } finally {
            Own.set(rows);
            ChunkMarks.live = live;
            Terrain.forget();
            MapAhead.forget();
        }
    }

    /**
     * Posts on the level above a road along a shop front, which the game stands on the road's
     * squares, are built over those squares, and nothing stands on their ground: the kerb runs
     * where it runs without them, and their chunks count no more of their squares built on
     * (Terrain.Kinds.hard).
     */
    private static void postsOnALevelAboveTheRoadLeaveTheKerbAndWhatIsBuiltOnAsTheyAre() {
        boolean live = ChunkMarks.live;
        Terrain.HILLS.heighten(1f);
        Country.set(List.of());
        try {
            ChunkMarks.live = true;
            String[] kerbs = new String[2];
            int[][] builtOn = new int[2][];
            for (int posts = 0; posts < 2; posts++) {
                MapAhead.forget();
                Terrain.forget();
                Town town = new Town(false);
                IsoCell cell = town.map.cell;
                IsoBuilding shop = new IsoBuilding(new BuildingDef(9, 4, 4, 12, 4));
                for (int x = -16; x < 48; x++) {
                    for (int y = -16; y < 48; y++) {
                        IsoGridSquare square = cell.square(x, y, 0);
                        boolean in = x >= 4 && x < 16 && y >= 4 && y < 8;
                        square.floor(in ? town.inside : y >= 8 && y < 24 ? town.road : town.grass);
                        if (in) {
                            square.building(shop);
                            square.setRoomID(9);
                        }
                    }
                }
                if (posts == 1) {
                    IsoSprite post = town.map.sprite("walls_commercial_03_35", IsoObjectType.wall, "WallSE");
                    for (int x = 6; x < 14; x += 2) {
                        cell.square(x, 8, 1).add(post);
                    }
                }
                heightsIn(cell, 0, 0, 2, 2, 9911 + posts, 9911 + posts);
                if (posts == 1) {
                    check(overAt(6, 8) == Footings.BUILT && overAt(12, 8) == Footings.BUILT, "a post on the level above is built over the road's square: " + overAt(6, 8) + " " + overAt(12, 8));
                }
                kerbs[posts] = Arrays.toString(Terrain.HILLS.kerb(0, 1)) + Arrays.toString(Terrain.HILLS.kerb(1, 1));
                builtOn[posts] = new int[] {TestMap.<Integer>read(derivedRecord(0, 1), "builtOn"), TestMap.<Integer>read(derivedRecord(1, 1), "builtOn")};
            }
            check(!kerbs[0].equals("nullnull"), "the road has a kerb along the shop front");
            check(kerbs[1].equals(kerbs[0]), "the kerb runs where it runs without the posts");
            check(Arrays.equals(builtOn[1], builtOn[0]), "the chunks count no more of their squares built on: " + Arrays.toString(builtOn[0]) + " without the posts, " + Arrays.toString(builtOn[1]) + " with");
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
            MapAhead.forget();
        }
    }

    /** Fills the map of the test: made ground up to the street, the street, then open ground, the same in every row. */
    private static void lay(int street, int verge, byte before) {
        Arrays.fill(Terrain.padsAround, Float.NaN);
        for (int i = 0; i < SPAN; i++) {
            for (int j = 0; j < SPAN; j++) {
                Terrain.around[i * SPAN + j] = j < street ? before : j < verge ? Terrain.STREET : Terrain.NATURAL;
            }
        }
    }

    private static void streetAlongAHillsideIsLevelFromSideToSide() {
        // Where the land falls a quarter of a tile across the width of a street that runs east.
        int x = 10724, y = 9725, street = 9, verge = 15, row = SPAN / 2;
        lay(street, verge, Terrain.NATURAL);
        terrain.streets(x, y);
        float kerb = terrain.corner(x + row, y + street, row, street);
        for (int j = street; j <= verge; j++) {
            check(Math.abs(terrain.corner(x + row, y + j, row, j) - kerb) < 1e-5f, "a street along a hillside is as high at one kerb as at the other");
        }
        float along = terrain.corner(x + row + 1, y + street, row + 1, street);
        check(along != kerb && Math.abs(along - kerb) < 0.15f, "a street rises and falls along its length, no steeper than the land");
        float beside = terrain.corner(x + row, y + street - 1, row, street - 1);
        check(Math.abs(beside - kerb) < 0.25f, "the ground beside a street meets it without a step");
    }

    private static void swellsAreSeenFromAStreetAndNoSteeperThanOneInTwenty() {
        float steepest = 0f, lowest = 0f, highest = 0f;
        for (int x = 10000; x < 10600; x++) {
            for (int y = 9000; y < 9600; y++) {
                float here = terrain.swells(x, y);
                steepest = Math.max(steepest, Math.max(Math.abs(terrain.swells(x + 1, y) - here), Math.abs(terrain.swells(x, y + 1) - here)));
                lowest = Math.min(lowest, here);
                highest = Math.max(highest, here);
            }
        }
        check(steepest <= 0.05f, "the land swells no steeper than one in twenty");
        check(steepest > 0.035f && highest > 0.8f && lowest < -0.8f, "the land swells and dips most of a tile either way");
        float crests = 0;
        for (int x = 10000; x < 11000; x++) {
            float before = terrain.swells(x - 1, 9300), here = terrain.swells(x, 9300), after = terrain.swells(x + 1, 9300);
            crests += here > before && here >= after ? 1 : 0;
        }
        check(crests >= 6 && crests <= 10, "a street crosses a crest every hundred paces or so");
    }

    private static void streetWithItsPavementAgainstABuildingIsAtTheBuildingsFooting() {
        // A building, two squares of pavement, a street four squares wide, open ground.
        int pavement = 8, street = 10, verge = 14, row = SPAN / 2;
        lay(street, verge, Terrain.MADE);
        for (int i = 0; i < SPAN; i++) {
            Arrays.fill(Terrain.padsAround, i * SPAN, i * SPAN + pavement, 1f);
        }
        terrain.streets(0, 0);
        check(terrain.corner(0f, 0f, row, pavement) == 1f, "a building's own corner is at the height of its pad");
        // The pavement runs up to the wall, so the street is laid to the building's footing (Paving) and not to the land.
        for (int j = pavement; j <= verge; j++) {
            check(Math.abs(terrain.corner(0f, 0f, row, j) - 1f) < 1e-5f, "a street past a building with its pavement against the wall is at the building's footing from the wall to the far kerb");
        }
        for (int j = street + 1; j <= verge; j++) {
            check(terrain.corner(0f, 0f, row, j) == terrain.corner(0f, 0f, row, street), "and its road is as high at one kerb as at the other");
        }
    }

    /** Open ground, a street, open ground with a line of water across it, about (10724, 9725), as the heights recorded in AS_IT_WAS were laid. */
    private static void layShown() {
        Arrays.fill(Terrain.padsAround, Float.NaN);
        for (int i = 0; i < SPAN; i++) {
            for (int j = 0; j < SPAN; j++) {
                Terrain.around[i * SPAN + j] = j < 9 ? Terrain.NATURAL : j < 15 ? Terrain.STREET : j == 30 ? Terrain.WATER : Terrain.NATURAL;
            }
        }
    }

    /** The corners of a row across layShown's land, every third, then the far land, at a valley's width from water and at 9 tiles, and the water's level, at five places. */
    private static String shown(Terrain on) {
        StringBuilder out = new StringBuilder();
        layShown();
        int x = 10724, y = 9725, row = SPAN / 2;
        on.streets(x, y);
        for (int j = 0; j < SPAN; j += 3) {
            out.append(Integer.toHexString(Float.floatToRawIntBits(on.corner(x + row, y + j, row, j)))).append(',');
        }
        out.append('\n');
        float[][] places = {{10724, 9725}, {6500, 5300}, {12000, 1500}, {8000, 11000}, {3000, 9000}};
        for (float[] place : places) {
            out.append(Integer.toHexString(Float.floatToRawIntBits(on.far(place[0], place[1], Terrain.VALLEY)))).append(',');
            out.append(Integer.toHexString(Float.floatToRawIntBits(on.far(place[0], place[1], 9f)))).append(',');
            out.append(Integer.toHexString(Float.floatToRawIntBits(on.waterLevel(place[0], place[1])))).append(',');
        }
        return out.toString();
    }

    private static void theHillsAtHeightOneAreTheLandAsItWas() {
        Country.set(List.of());
        Terrain.HILLS.heighten(1f);
        check(shown(Terrain.HILLS).equals(AS_IT_WAS), "with the hills at 1 and no farmland or woods, the land is the land as it was to the bit");
    }

    private static void theHillsAtNoHeightAreTheLevelLand() {
        Country.set(COUNTRY);
        Terrain.HILLS.heighten(0f);
        boolean same = true;
        String hills = shown(Terrain.HILLS), level = shown(Terrain.LEVEL);
        String[] a = hills.split("[,\n]+"), b = level.split("[,\n]+");
        for (int i = 0; i < a.length; i++) {
            // Compared as numbers: no land, 0, is no land whichever its sign.
            same &= Float.intBitsToFloat(Integer.parseUnsignedInt(a[i], 16)) == Float.intBitsToFloat(Integer.parseUnsignedInt(b[i], 16));
        }
        check(a.length == b.length && same, "with the hills at 0 the land, the far land and the water are the level land's:\n" + hills + "\n" + level);
        for (int x = 0; x < 6000; x += 97) {
            check(Terrain.HILLS.far(x, 1000f + x / 3f, Terrain.VALLEY) == 0f && Terrain.HILLS.swells(x, 1000f) == 0f,
                    "and the woods have no hill and no swell at " + x);
        }
    }

    private static void kerbsBanksAndStreetsStandAsTheyDoAtEveryHeight() {
        Country.set(COUNTRY);
        int x = 1200, y = 1300, row = SPAN / 2;
        for (float gain : HEIGHTS) {
            Terrain.HILLS.heighten(gain);
            // Open ground falling to water that fills the map from the twentieth row on.
            Arrays.fill(Terrain.padsAround, Float.NaN);
            for (int i = 0; i < SPAN; i++) {
                for (int j = 0; j < SPAN; j++) {
                    Terrain.around[i * SPAN + j] = j < 20 ? Terrain.NATURAL : Terrain.WATER;
                }
            }
            Terrain.HILLS.streets(x, y);
            float edge = Terrain.HILLS.corner(x + row, y + 20, row, 20), water = Terrain.HILLS.base(x + row, y + 20) - Terrain.WATER_BELOW;
            check(Math.abs(edge - water) < 1e-5f, "at " + gain + " the bank comes down to the water's level at the water's edge: " + edge + " and " + water);
            // A street along a hillside, at the land's height down its middle and level across.
            lay(9, 15, Terrain.NATURAL);
            Terrain.HILLS.streets(x, y);
            float kerb = Terrain.HILLS.corner(x + row, y + 9, row, 9);
            boolean level = true;
            for (int j = 9; j <= 15; j++) {
                level &= Math.abs(Terrain.HILLS.corner(x + row, y + j, row, j) - kerb) < 1e-5f;
            }
            check(level, "at " + gain + " a street is as high at one kerb as at the other");
        }
    }

    private static void theFarLandAndTheBuiltLandAgreeAtEveryHeight() {
        Country.set(COUNTRY);
        for (float gain : HEIGHTS) {
            Terrain.HILLS.heighten(gain);
            boolean agree = true, water = true;
            for (int x = 100; x < 12000; x += 211) {
                float y = 500f + x * 0.37f;
                // With no water about and nothing built round it, the land the built world stands on is the far land's.
                agree &= Terrain.HILLS.far(x, y, Terrain.VALLEY) == Terrain.HILLS.base(x, y);
                water &= Math.abs(Terrain.HILLS.waterLevel(x, y) - (Terrain.HILLS.far(x, y, 0f) - Terrain.WATER_BELOW)) < 1e-5f;
            }
            check(agree, "at " + gain + " the far land lies where the built world's land does");
            check(water, "at " + gain + " the water the built world draws lies where the far land's water does, below its land");
        }
    }

    private static void hillsRiseInFarmlandAndWoodsAndNotInTown() {
        Country.set(COUNTRY);
        Terrain.HILLS.heighten(1f);
        check(Country.at(1000f, 1000f) > 0.999f && Country.at(3000f, 3000f) == 0f && Country.at(10000f, 1300f) > 0.999f && Country.at(14000f, 1000f) == 0f
                        && Country.at(-500f, 1000f) == 0f,
                "deep in the woods and in farmland a place is all country, in the middle of a town and where the map zones nothing none: "
                        + Country.at(1000f, 1000f) + " " + Country.at(3000f, 3000f) + " " + Country.at(10000f, 1300f) + " " + Country.at(14000f, 1000f));
        float[] woods = land(200, 2000), town = land(2800, 3200);
        Country.set(List.of());
        float[] bareWoods = land(200, 2000), bareTown = land(2800, 3200);
        Country.set(COUNTRY);
        int hilly = 0;
        float highest = 0f, inTown = 0f;
        for (int at = 0; at < woods.length; at++) {
            float up = woods[at] - bareWoods[at];
            hilly += up > 1f ? 1 : 0;
            highest = Math.max(highest, up);
        }
        for (int at = 0; at < town.length; at++) {
            inTown = Math.max(inTown, Math.abs(town[at] - bareTown[at]));
        }
        float share = hilly / (float) woods.length;
        check(share > 0.3f && highest > 6f, "in the woods a third of the land and more stands a tile and more higher than the same land unzoned, and the highest six tiles and more: "
                + share + ", " + highest);
        check(inTown == 0f, "in the middle of the town the land is as it is unzoned: " + inTown);
        float steepest = 0f;
        for (int x = 200; x < 2000; x += 2) {
            for (int y = 200; y < 2000; y += 2) {
                float here = Terrain.HILLS.base(x, y);
                steepest = Math.max(steepest, (float) Math.hypot(Terrain.HILLS.base(x + 1, y) - here, Terrain.HILLS.base(x, y + 1) - here));
            }
        }
        check(steepest < 0.17f, "and the land of the woods is nowhere steeper than one in six: " + steepest);
    }

    /** The built world's land, before what is uneven on open ground, every fourth tile from (from, from) to (to, to). */
    private static float[] land(int from, int to) {
        int side = (to - from) / 4;
        float[] heights = new float[side * side];
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                heights[i * side + j] = Terrain.HILLS.base(from + i * 4, from + j * 4);
            }
        }
        return heights;
    }

    /** Open ground and a street, with nothing a hand high: every height is gain times its height at 1. */
    private static void everyRiseOfTheLandIsTheHillsHeightTimesItsOwn() {
        Country.set(COUNTRY);
        int x = 1300, y = 1700, row = SPAN / 2;
        lay(9, 15, Terrain.NATURAL);
        Terrain.HILLS.heighten(1f);
        Terrain.HILLS.streets(x, y);
        float[] atOne = new float[SPAN];
        for (int j = 0; j < SPAN; j++) {
            atOne[j] = Terrain.HILLS.corner(x + row, y + j, row, j);
        }
        for (float gain : HEIGHTS) {
            Terrain.HILLS.heighten(gain);
            Terrain.HILLS.streets(x, y);
            float worst = 0f;
            for (int j = 0; j < SPAN; j++) {
                worst = Math.max(worst, Math.abs(Terrain.HILLS.corner(x + row, y + j, row, j) - gain * atOne[j]));
            }
            check(worst < 1e-5f, "at " + gain + " open ground, its knolls and what is uneven underfoot, and the street through it stand " + gain + " times as high: " + worst);
        }
    }

    private static void noHillRisesFromWater() {
        Country.set(COUNTRY);
        for (float gain : HEIGHTS) {
            Terrain.HILLS.heighten(gain);
            boolean low = true;
            for (int x = 200; x < 2000; x += 37) {
                for (int y = 200; y < 2000; y += 41) {
                    // At water the land is the country's rise and fall alone, the hills of the woods as much as any.
                    low &= Math.abs(Terrain.HILLS.far(x, y, 0f) - (Terrain.HILLS.waterLevel(x, y) + Terrain.WATER_BELOW)) < 1e-5f;
                }
            }
            check(low, "at " + gain + " no hill of the woods rises from water");
        }
    }

    private static void theHillsAreAtTheirDefaultWherePlayersCanFight() {
        float asked = HillHeight.asked();
        HillHeight.set(0.3f);
        check(HillHeight.drawn(false) == 0.3f, "where players cannot fight the hills stand as the player set them");
        check(HillHeight.drawn(true) == HillHeight.CONTESTED, "where they can, at one height for everyone");
        HillHeight.set(asked);
    }

    /**
     * A change of the height of the hills has the heights of the eye's chunk and of the eight
     * round it worked out again at once, at the new height, and no others: the rest wait to be
     * asked for, nearest first. Until a chunk's are, the water there is drawn from the land as it
     * was, so the plane the water mirrors in is worked out at the height its chunk was.
     */
    private static void aChangeOfHeightReworksTheLandRoundTheEyeAtOnce() {
        Terrain.HILLS.heighten(1f);
        List<String> worked = new ArrayList<>();
        float eyeX = 1300.5f, eyeY = -12.25f;
        Terrain.Rework<List<String>> record = (list, wx, wy) -> list.add(wx + "," + wy + " at " + Terrain.HILLS.gain());
        check(Terrain.HILLS.heighten(1.5f, 0, eyeX, eyeY, worked, record), "the first view built after the change changes the gain");
        List<String> round = new ArrayList<>();
        for (int wx = 161; wx <= 163; wx++) {
            for (int wy = -3; wy <= -1; wy++) {
                round.add(wx + "," + wy + " at 1.5");
            }
        }
        check(worked.size() == 9 && worked.containsAll(round), "the eye's chunk and the eight round it, and no other, are worked out at once at the new height: " + worked);
        worked.clear();
        check(!Terrain.HILLS.heighten(1.5f, 1, eyeX + 800f, eyeY, worked, record), "the second view, built after it, finds the gain changed already");
        round.replaceAll(chunk -> (Integer.parseInt(chunk.substring(0, chunk.indexOf(','))) + 100) + chunk.substring(chunk.indexOf(',')));
        check(worked.size() == 9 && worked.containsAll(round), "and has the chunks round its own eye worked out at once: " + worked);
        worked.clear();
        Terrain.HILLS.heighten(1.5f, 0, eyeX, eyeY, worked, record);
        Terrain.HILLS.heighten(1.5f, 1, eyeX + 800f, eyeY, worked, record);
        check(worked.isEmpty(), "and neither view's are worked out again until the gain changes again: " + worked);
        Terrain.HILLS.heighten(1f, 0, eyeX, eyeY, worked, record);
        Terrain.HILLS.heighten(1.5f, 0, eyeX, eyeY, worked, record);
        worked.clear();
        Terrain.HILLS.heighten(1.5f, 1, eyeX + 800f, eyeY, worked, record);
        check(worked.size() == 9 && worked.containsAll(round),
                "the gain taken to 1 and back to 1.5 while the second view was not built has its eye's chunks worked out at once when it is: " + worked);
        check(Terrain.HILLS.gainAt(eyeX + 400f, eyeY) == 1.5f, "a chunk never worked out will be at the new height");
        check(Terrain.waterLevel(1.5f, eyeX, eyeY) == 1.5f * (Terrain.waterLevel(1f, eyeX, eyeY) + Terrain.WATER_BELOW) - Terrain.WATER_BELOW,
                "water lies at the height of the hills times the country's rise and fall");
        Terrain.HILLS.heighten(1f);
        // The views' eyes stay where they were told of until the map is let go, and the records of the tests after this one would be let go as far from them.
        Terrain.forget();
    }

    /** Where the test pitch lies: the hills rise and fall across it there. */
    private static final int PITCH_X = 10640, PITCH_Y = 9944;

    /**
     * The heights of the chunks from (fromX, fromY) to (toX, toY), as the hills lay a field of
     * grass about (PITCH_X, PITCH_Y), with a goal at either end of a pitch 30 squares long, or none.
     */
    private static Map<Long, float[]> field(boolean goals, int fromX, int fromY, int toX, int toY) {
        Terrain.forget();
        TestMap map = new TestMap();
        var grass = map.sprite("blends_natural_01_0", zombie.iso.SpriteDetails.IsoObjectType.normal);
        String[] goal = {"recreational_sports_01_19", "recreational_sports_01_20", "recreational_sports_01_21"};
        var pieces = new zombie.iso.sprite.IsoSprite[3];
        for (int i = 0; i < 3; i++) {
            pieces[i] = map.sprite(goal[i], zombie.iso.SpriteDetails.IsoObjectType.normal);
        }
        for (int x = PITCH_X - 64; x < PITCH_X + 96; x++) {
            for (int y = PITCH_Y - 64; y < PITCH_Y + 96; y++) {
                map.cell.square(x, y, 0).floor(grass);
            }
        }
        if (goals) {
            for (int i = 0; i < 3; i++) {
                map.cell.square(PITCH_X + 10 + i, PITCH_Y + 2, 0).add(pieces[i]);
                map.cell.square(PITCH_X + 10 + i, PITCH_Y + 32, 0).add(pieces[i]);
            }
        }
        Terrain.frame(5000);
        Terrain.loaded(1);
        Map<Long, float[]> chunks = new LinkedHashMap<>();
        for (int wx = fromX; wx <= toX; wx++) {
            for (int wy = fromY; wy <= toY; wy++) {
                chunks.put(((long) wx << 32) | (wy & 0xffffffffL), Terrain.HILLS.corners(map.cell, wx, wy).clone());
            }
        }
        return chunks;
    }

    private static float at(Map<Long, float[]> chunks, int x, int y) {
        // A corner on the far edge of the last chunk is that chunk's own last corner.
        int wx = Math.floorDiv(x - 1, Terrain.CHUNK_TILES), wy = Math.floorDiv(y - 1, Terrain.CHUNK_TILES);
        return chunks.get(((long) wx << 32) | (wy & 0xffffffffL))[(x - wx * Terrain.CHUNK_TILES) * 9 + y - wy * Terrain.CHUNK_TILES];
    }

    private static void aPitchOnAHillsideIsLaidLevelAndTheHillsEaseBackRoundIt() {
        boolean live = ChunkMarks.live;
        Terrain.HILLS.heighten(1f);
        Country.set(COUNTRY_OF_HILLS);
        try {
            ChunkMarks.live = true;
            int ease = (int) Terrain.GROUND_EASE, eight = Terrain.CHUNK_TILES;
            int fromX = Math.floorDiv(PITCH_X - ease, eight), fromY = Math.floorDiv(PITCH_Y - ease, eight);
            int toX = Math.floorDiv(PITCH_X + 24 + ease + 4, eight), toY = Math.floorDiv(PITCH_Y + 36 + ease + 4, eight);
            Map<Long, float[]> bare = field(false, fromX, fromY, toX, toY), pitch = field(true, fromX, fromY, toX, toY);
            // The goals' box: their squares and the run-off past them, as wide as a pitch 30 long.
            int lowY = PITCH_Y + 2 - Grounds.MARGIN, highY = PITCH_Y + 33 + Grounds.MARGIN;
            int wide = Math.round((highY - lowY) * Grounds.WIDTH_FOR_LENGTH), lowX = PITCH_X + 10 + 3 / 2 - wide / 2;
            float lowest = Float.MAX_VALUE, highest = -Float.MAX_VALUE;
            for (int x = lowX; x <= lowX + wide - 1; x++) {
                for (int y = lowY; y <= highY; y++) {
                    lowest = Math.min(lowest, at(bare, x, y));
                    highest = Math.max(highest, at(bare, x, y));
                }
            }
            check(highest - lowest > 0.5f, "the hills rise and fall over the ground the pitch is on, by " + (highest - lowest));
            float level = at(pitch, PITCH_X + 11, PITCH_Y + 17);
            for (int x = lowX; x <= lowX + wide - 1; x++) {
                for (int y = lowY; y <= highY; y++) {
                    check(at(pitch, x, y) == level, "the pitch is level from end to end and side to side, at " + x + ", " + y + ": " + at(pitch, x, y) + " for " + level);
                }
            }
            check(level > lowest && level < highest, "at a height the hills had under it, " + level + " in " + lowest + ".." + highest);
            for (int x = fromX * eight + 1; x <= toX * eight + eight; x++) {
                for (int y = fromY * eight + 1; y <= toY * eight + eight; y++) {
                    int outX = Math.max(Math.max(lowX - x, x - (lowX + wide)), 0), outY = Math.max(Math.max(lowY - y, y - (highY + 1)), 0);
                    if (outX * outX + outY * outY >= ease * ease) {
                        check(at(pitch, x, y) == at(bare, x, y), "past the ease the hills are as they were, at " + x + ", " + y);
                    }
                }
            }
            for (long key : pitch.keySet()) {
                int wx = (int) (key >> 32), wy = (int) key;
                float[] here = pitch.get(key), east = pitch.get(((long) (wx + 1) << 32) | (wy & 0xffffffffL)), south = pitch.get(((long) wx << 32) | ((wy + 1) & 0xffffffffL));
                for (int i = 0; i < 9; i++) {
                    check(east == null || here[72 + i] == east[i], "chunks either side of the pitch's edge agree along it");
                    check(south == null || here[i * 9 + 8] == south[i * 9], "and down it");
                }
            }
            float steepest = 0f;
            for (int x = fromX * eight + 1; x < toX * eight + eight; x++) {
                for (int y = fromY * eight + 1; y < toY * eight + eight; y++) {
                    steepest = Math.max(steepest, Math.max(Math.abs(at(pitch, x + 1, y) - at(pitch, x, y)), Math.abs(at(pitch, x, y + 1) - at(pitch, x, y))));
                }
            }
            check(steepest < 0.35f, "the hills come back in round it with no step or cliff, steepest " + steepest + " a square");
        } finally {
            ChunkMarks.live = live;
            Terrain.forget();
            Country.set(List.of());
        }
    }

    private static final List<Country.Shape> COUNTRY_OF_HILLS = List.of(Country.Shape.rectangle("FarmLand", 10000, 9000, 2000, 2000));
}

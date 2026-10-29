package com.cameraunlock.zomboid;

import java.util.Arrays;

/** A build made on a frame beside the walking frame, which took the walking frame's state first: WorldScene.Frame.takeFrom. */
final class BesideTests {
    private BesideTests() {}

    static void run() {
        Checks.run("aFrameBesideTakesWhatABuildReads", BesideTests::aFrameBesideTakesWhatABuildReads);
        Checks.run("aFrameBesideKeepsWhatItBuildsWithAndInto", BesideTests::aFrameBesideKeepsWhatItBuildsWithAndInto);
        Checks.run("aBuildBesideComesOutAsTheWalkingFramesBuild", BesideTests::aBuildBesideComesOutAsTheWalkingFramesBuild);
    }

    private static void aFrameBesideTakesWhatABuildReads() {
        TestMap map = new TestMap();
        WorldScene.Frame walking = map.build, beside = map.world.beside;
        walking.lowestLevel = -3;
        walking.bandAway = 7.5f;
        walking.mainsOn = true;
        walking.lightRed[5] = 0.25f;
        walking.groundObject[3] = new zombie.iso.IsoObject(map.sprite("floors_exterior_natural_01_0", zombie.iso.SpriteDetails.IsoObjectType.normal));
        java.util.ArrayList<zombie.iso.sprite.IsoSprite> paintLater = TestMap.read(walking, "paintLater");
        paintLater.add(map.sprite("walls_exterior_house_01_0", zombie.iso.SpriteDetails.IsoObjectType.normal));
        beside.takeFrom(walking);
        check(beside.lowestLevel == -3 && beside.bandAway == 7.5f && beside.mainsOn, "it takes the walking frame's numbers and switches");
        check(beside.cell == walking.cell, "and the map it reads");
        check(beside.lightRed != walking.lightRed && Arrays.equals(beside.lightRed, walking.lightRed), "into its own arrays, what the walking frame's hold");
        check(beside.groundObject != walking.groundObject && beside.groundObject[3] == walking.groundObject[3], "the things in them as they are");
        java.util.ArrayList<zombie.iso.sprite.IsoSprite> besidePaints = TestMap.read(beside, "paintLater");
        check(besidePaints != paintLater && besidePaints.equals(paintLater), "and into its own lists, what the walking frame's hold");
        check(TestMap.read(beside, "sky") == TestMap.read(walking, "sky"), "it reads the walking frame's sky, which a build does not change");
        check(beside.camera != walking.camera && beside.camera.eyeX == walking.camera.eyeX, "and has the walking frame's camera in its own");
        walking.lowestLevel = 0;
        walking.lightRed[5] = 0.5f;
        check(beside.lowestLevel == -3 && beside.lightRed[5] == 0.25f, "what it took stays as it was taken");
    }

    private static void aFrameBesideKeepsWhatItBuildsWithAndInto() {
        TestMap map = new TestMap();
        WorldScene.Frame walking = map.build, beside = map.world.beside;
        beside.takeFrom(walking);
        check(beside.solid != walking.solid && beside.laid != walking.laid && beside.layers != walking.layers && beside.sites != walking.sites,
                "a frame beside builds into a pair and light sites of its own");
        check(beside.solid.sites == beside.sites && beside.laid.sites == beside.sites, "and its pair counts its sites in its own table");
        check(beside.crownsRead != walking.crownsRead, "it writes its crowns down for itself");
        check(beside.roofBuilder != walking.roofBuilder && beside.frame != walking.frame && beside.geometry != walking.geometry, "with builders and scratch of its own");
        check(beside.building != walking.building || beside.building == null, "and not into the walking frame's meshes");
    }

    private static void aBuildBesideComesOutAsTheWalkingFramesBuild() {
        TestMap map = new TestMap();
        map.roofSprite(1, 0, 0, "roofs_02_6", "attachedN", "isEave");
        map.wall(1, 0, 0, true);
        map.roofSprite(0, 0, 0, "roofs_02_30", "attachedN", "isEave");
        WorldScene.Frame walking = map.build, beside = map.world.beside;
        WorldScene.sharedSolid.clear();
        WorldScene.sharedLaid.clear();
        beside.takeFrom(walking);
        beside.building = walking.building;
        beside.placed = walking.placed;
        beside.roofBuilder.newBuild();
        beside.solid.clear();
        beside.laid.clear();
        LightSiteTests.sited(walking.frame);
        sitedBeside(beside);
        check(walking.roofBuilder.addEaveRow(map.painted, 1, 0, 0) && TestMap.plain().triangles() > 0, "the walking frame builds the eave row");
        check(beside.roofBuilder.addEaveRow(map.painted, 1, 0, 0) && beside.solid.get(WorldScene.PLAIN).triangles() > 0, "and so does the frame beside, into its own pair");
        check(WorldScene.floatsOf(walking.solid) == WorldScene.floatsOf(beside.solid) && WorldScene.floatsOf(walking.laid) == WorldScene.floatsOf(beside.laid),
                "to every float of the walking frame's");
        check(WorldScene.sharedSites.count == beside.sites.count, "on as many light sites");
    }

    /** As LightSiteTests.sited, on the frame beside's own table. */
    private static void sitedBeside(WorldScene.Frame beside) {
        float[] record = new float[LightSites.RECORD];
        LightSites.take(beside.frame, record, 0, 0f, false);
        LightSites.Evaluator evaluator = beside.sites.evaluator;
        beside.sites.evaluator = null;
        try {
            beside.sites.put(LightSites.SQUARE, 1L << 40 | Arrays.hashCode(record) & 0xFFFFFFFFL, beside.frame, 0f, false);
        } finally {
            beside.sites.evaluator = evaluator;
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}

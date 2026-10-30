package com.cameraunlock.zomboid;

import java.util.Arrays;

/** A build made on a frame beside the walking frame, which took the walking frame's state first: WorldScene.Frame.takeFrom. */
final class BesideTests {
    private BesideTests() {}

    static void run() {
        Checks.run("aFrameBesideTakesWhatABuildReads", BesideTests::aFrameBesideTakesWhatABuildReads);
        Checks.run("aFrameBesideKeepsWhatItBuildsWithAndInto", BesideTests::aFrameBesideKeepsWhatItBuildsWithAndInto);
        Checks.run("aBuildBesideComesOutAsTheWalkingFramesBuild", BesideTests::aBuildBesideComesOutAsTheWalkingFramesBuild);
        Checks.run("aBuildThreadIsKnownAndWhatItThrowsIsThrownAgain", BesideTests::aBuildThreadIsKnownAndWhatItThrowsIsThrownAgain);
        Checks.run("whatBuildThreadsAskOfTheLogicThreadIsDoneThereAsItWaits", BesideTests::whatBuildThreadsAskOfTheLogicThreadIsDoneThereAsItWaits);
        Checks.run("workHandedIsMadeAsTheLogicThreadGoesOnInTheOrderHanded", BesideTests::workHandedIsMadeAsTheLogicThreadGoesOnInTheOrderHanded);
        Checks.run("whatABuildLeavesToTheLogicThreadIsKeptInOrderUntilItLands", BesideTests::whatABuildLeavesToTheLogicThreadIsKeptInOrderUntilItLands);
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
        boolean[] built = new boolean[1];
        java.util.concurrent.ExecutorService thread = BuildThreads.one("tests' build thread");
        try {
            BuildThreads.runAndWait(thread, () -> built[0] = beside.roofBuilder.addEaveRow(map.painted, 1, 0, 0));
        } finally {
            thread.shutdown();
        }
        check(built[0] && beside.solid.get(WorldScene.PLAIN).triangles() > 0, "and so does the frame beside on a build thread, into its own pair");
        check(WorldScene.floatsOf(walking.solid) == WorldScene.floatsOf(beside.solid) && WorldScene.floatsOf(walking.laid) == WorldScene.floatsOf(beside.laid),
                "to every float of the walking frame's");
        check(WorldScene.sharedSites.count == beside.sites.count, "on as many light sites");
    }

    private static void aBuildThreadIsKnownAndWhatItThrowsIsThrownAgain() {
        java.util.concurrent.ExecutorService thread = BuildThreads.one("tests' build thread");
        try {
            boolean[] here = new boolean[1];
            BuildThreads.runAndWait(thread, () -> here[0] = BuildThreads.here());
            check(here[0] && !BuildThreads.here(), "a build thread knows itself for one, and the thread that waits on it is not one");
            IllegalArgumentException thrown = new IllegalArgumentException("thrown on the build thread");
            Throwable caught = null;
            try {
                BuildThreads.runAndWait(thread, () -> {
                    throw thrown;
                });
            } catch (IllegalStateException e) {
                caught = e.getCause();
            }
            check(caught == thrown, "what a build throws is thrown again on the thread that waits on it");
        } finally {
            thread.shutdown();
        }
    }

    private static void whatBuildThreadsAskOfTheLogicThreadIsDoneThereAsItWaits() {
        java.util.concurrent.ExecutorService pool = BuildThreads.pool(3, "tests' build thread");
        try {
            Thread logic = Thread.currentThread();
            Thread[] askedOn = new Thread[3];
            boolean[] locked = new boolean[3];
            int[] numbers = new int[3];
            BuildThreads.Handing<Integer> handing = new BuildThreads.Handing<>();
            for (int piece = 0; piece < 3; piece++) {
                handing.hand(piece, pool, 3, thread -> {
                    for (Integer each = handing.next(); each != null; each = handing.next()) {
                        int number = each;
                        numbers[number] = BuildThreads.onLogicThread(() -> {
                            askedOn[number] = Thread.currentThread();
                            locked[number] = Thread.holdsLock(BuildThreads.SHARED);
                            return number + 10;
                        });
                    }
                });
            }
            check(askedOn[0] == null && askedOn[1] == null && askedOn[2] == null, "nothing asked is done before the logic thread waits on the builds");
            handing.finish();
            check(askedOn[0] == logic && askedOn[1] == logic && askedOn[2] == logic, "what each build thread asks is done on the thread that waits on them");
            check(locked[0] && locked[1] && locked[2], "under the builds' lock");
            check(numbers[0] == 10 && numbers[1] == 11 && numbers[2] == 12, "and each is handed back its own answer");
            check(BuildThreads.onLogicThread(() -> Thread.currentThread()) == logic, "on the logic thread itself it is done at once");
            check(BuildThreads.onLogicThread(() -> Thread.holdsLock(BuildThreads.SHARED)), "under the builds' lock as well, as builds may be being made");
            IllegalArgumentException thrown = new IllegalArgumentException("thrown on the logic thread");
            Throwable caught = null;
            BuildThreads.Handing<Integer> throwing = new BuildThreads.Handing<>();
            try {
                throwing.hand(0, pool, 2, thread -> {
                    for (Integer each = throwing.next(); each != null; each = throwing.next()) {
                        BuildThreads.onLogicThread(() -> {
                            throw thrown;
                        });
                    }
                });
                throwing.finish();
            } catch (IllegalStateException e) {
                caught = e.getCause() == null ? null : e.getCause().getCause();
            }
            check(caught == thrown, "what the asking throws is thrown on the build thread, and again on the thread that waits on it");
            check(!BuildThreads.handing(), "and the builds are not taken to be being made after it");
        } finally {
            pool.shutdown();
        }
    }

    private static void workHandedIsMadeAsTheLogicThreadGoesOnInTheOrderHanded() {
        java.util.concurrent.ExecutorService pool = BuildThreads.pool(2, "tests' build thread");
        try {
            BuildThreads.Handing<Integer> handing = new BuildThreads.Handing<>();
            handing.finish();
            check(!BuildThreads.handing(), "nothing handed, nothing waited on");
            java.util.List<Integer> taken = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            for (int round = 0; round < 2; round++) {
                taken.clear();
                java.util.concurrent.CountDownLatch first = new java.util.concurrent.CountDownLatch(1);
                // One thread takes the pieces in the order handed, and two between them take each once.
                int threads = round + 1;
                for (int piece = 0; piece < 50; piece++) {
                    handing.hand(piece, pool, threads, thread -> {
                        for (Integer each = handing.next(); each != null; each = handing.next()) {
                            taken.add(each);
                            first.countDown();
                        }
                    });
                    check(BuildThreads.handing(), "while pieces are handed the builds are taken to be being made");
                    if (piece == 0) {
                        boolean started;
                        try {
                            started = first.await(10, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            throw new IllegalStateException(e);
                        }
                        check(started, "the first piece is made while the logic thread goes on, before it waits");
                    }
                }
                handing.finish();
                check(!BuildThreads.handing(), "and not once waited on");
                check(taken.size() == 50, "every piece is made once, on " + threads + " threads: " + taken.size());
                int inOrder = 0, found = 0;
                for (int i = 0; i < taken.size(); i++) {
                    inOrder += taken.get(i) == i ? 1 : 0;
                    found += taken.contains(i) ? 1 : 0;
                }
                check(found == 50 && (threads > 1 || inOrder == 50), "each of them, and on one thread in the order handed");
            }
        } finally {
            pool.shutdown();
        }
    }

    private static void whatABuildLeavesToTheLogicThreadIsKeptInOrderUntilItLands() {
        java.util.ArrayList<Integer> done = new java.util.ArrayList<>();
        BuildThreads.later(() -> done.add(0));
        check(done.size() == 1, "on the logic thread it is done at once");
        java.util.concurrent.ExecutorService thread = BuildThreads.one("tests' build thread");
        try {
            BuildThreads.Landing landing = new BuildThreads.Landing();
            BuildThreads.runAndWait(thread, () -> {
                BuildThreads.landInto(landing);
                BuildThreads.later(() -> done.add(1));
                BuildThreads.later(() -> done.add(2));
                BuildThreads.landInto(null);
            });
            check(done.size() == 1, "on a build thread it is kept");
            landing.land();
            check(done.equals(java.util.List.of(0, 1, 2)), "and done in the order it was left as it lands");
            landing.land();
            check(done.size() == 3, "once");
            Throwable caught = null;
            try {
                BuildThreads.runAndWait(thread, () -> BuildThreads.later(() -> done.add(3)));
            } catch (IllegalStateException e) {
                caught = e.getCause();
            }
            check(caught instanceof IllegalStateException && done.size() == 3, "a build thread with nowhere to keep it throws");
        } finally {
            thread.shutdown();
        }
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

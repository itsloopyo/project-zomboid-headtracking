package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The levels found relit and waiting to be taken: WorldScene's walk and what it leaves, played through PendingLights as a frame does. */
final class PendingLightsTests {
    private PendingLightsTests() {}

    static void run() {
        Checks.run("aLevelHasTheBitOfItsPlaceAmongTheMapsLevels", PendingLightsTests::aLevelHasTheBitOfItsPlaceAmongTheMapsLevels);
        Checks.run("aShareIsTheLastPassesMeanAndAQuarter", PendingLightsTests::aShareIsTheLastPassesMeanAndAQuarter);
        Checks.run("everythingIsTakenAtOnceUntilAPassHasBeenSeen", PendingLightsTests::everythingIsTakenAtOnceUntilAPassHasBeenSeen);
        Checks.run("aFrameTakesItsShareNearestFirst", PendingLightsTests::aFrameTakesItsShareNearestFirst);
        Checks.run("whatWaitsIsTakenInTheFrameThatSeesTheNextPass", PendingLightsTests::whatWaitsIsTakenInTheFrameThatSeesTheNextPass);
        Checks.run("aLevelFoundAgainWhileItWaitsIsTakenOnce", PendingLightsTests::aLevelFoundAgainWhileItWaitsIsTakenOnce);
        Checks.run("aChunkTheMapLetsGoOfTakesItsLevelsWithIt", PendingLightsTests::aChunkTheMapLetsGoOfTakesItsLevelsWithIt);
        Checks.run("aSteadyDriveTakesEachPassOverItsFrames", PendingLightsTests::aSteadyDriveTakesEachPassOverItsFrames);
        Checks.run("aBigFindIsTakenOverWhatIsLeftOfItsPass", PendingLightsTests::aBigFindIsTakenOverWhatIsLeftOfItsPass);
        Checks.run("theCheckFindsWhatWaitsFromAnEarlierPass", PendingLightsTests::theCheckFindsWhatWaitsFromAnEarlierPass);
        Checks.run("withTheSpreadOffEveryLevelIsTakenInTheFrameItIsFound", PendingLightsTests::withTheSpreadOffEveryLevelIsTakenInTheFrameItIsFound);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static final int SQUARES = WorldScene.CHUNK_TILES * WorldScene.CHUNK_TILES;

    private record Chunk(int wx, int wy) {
        int place() {
            return ChunkMarks.slot(wx, wy);
        }
    }

    private record Took(Chunk chunk, int z) {}

    /** The levels taken, in the order they were taken. */
    private static final List<Took> taken = new ArrayList<>();

    private static long levels(int from, int to) {
        long bits = 0L;
        for (int z = from; z <= to; z++) {
            bits |= PendingLights.bit(z);
        }
        return bits;
    }

    /** As WorldScene's takeLights, with every level a whole chunk's squares: the squares taken. */
    private static int take(PendingLights lights, Chunk chunk) {
        int squares = 0;
        for (int z = lights.next(chunk.place()); z != PendingLights.NONE; z = lights.next(chunk.place())) {
            taken.add(new Took(chunk, z));
            lights.took(chunk.place(), z, SQUARES);
            squares += SQUARES;
        }
        return squares;
    }

    /** As pullLightsLeft: what waits is taken where its chunk is on the map and let go where it is not, and the frame ends. */
    private static int leave(PendingLights lights, Set<Chunk> onTheMap) {
        int squares = 0;
        for (int i = lights.waiting() - 1; i >= 0; i--) {
            int place = lights.waitingAt(i);
            Chunk chunk = new Chunk(lights.wx(place), lights.wy(place));
            if (onTheMap.contains(chunk)) {
                squares += take(lights, chunk);
            } else {
                lights.letGo(place);
            }
        }
        lights.end(0L);
        lights.check();
        return squares;
    }

    /** A set whose last pass took nothing, so that a frame takes the least share there is, begun in the pass and frame given. */
    private static PendingLights leastShared(int pass, int frame) {
        PendingLights lights = new PendingLights();
        lights.begin(pass - 1, frame - 2);
        lights.end(0L);
        lights.begin(pass - 1, frame - 1);
        lights.end(0L);
        lights.begin(pass, frame);
        return lights;
    }

    private static void aLevelHasTheBitOfItsPlaceAmongTheMapsLevels() {
        check(PendingLights.bit(-32) == 1L && PendingLights.bit(31) == Long.MIN_VALUE && PendingLights.bit(0) == 1L << 32, "the map's 64 levels, -32 to 31, are the 64 bits of a place");
        try {
            PendingLights.bit(32);
            check(false, "a level over the map's highest is refused");
        } catch (IllegalArgumentException refused) {
            check(refused.getMessage().contains("32"), "and the refusal names the level");
        }
    }

    private static void aShareIsTheLastPassesMeanAndAQuarter() {
        check(PendingLights.shareOf(3200, 4) == 1000, "a pass of 3,200 squares over 4 frames gives 1,000 a frame");
        check(PendingLights.shareOf(4500, 3) == 1875, "and one of 4,500 over 3 frames 1,875");
        check(PendingLights.shareOf(0, 3) == PendingLights.LEAST_SHARE && PendingLights.shareOf(100, 1) == PendingLights.LEAST_SHARE,
                "a pass that took next to nothing leaves a frame the least share");
    }

    private static void everythingIsTakenAtOnceUntilAPassHasBeenSeen() {
        taken.clear();
        PendingLights lights = new PendingLights();
        Chunk far = new Chunk(410, 120);
        lights.begin(7, 1);
        lights.found(far.place(), far.wx, far.wy, levels(-8, 31));
        int squares = take(lights, far);
        leave(lights, Set.of(far));
        check(squares == 40 * SQUARES && lights.waiting() == 0, "with no pass seen whole there is no share yet, and all 40 levels are taken in the frame they are found");
    }

    private static void aFrameTakesItsShareNearestFirst() {
        taken.clear();
        PendingLights lights = new PendingLights();
        Chunk tower = new Chunk(300, 300), near = new Chunk(301, 300), middle = new Chunk(303, 300), far = new Chunk(306, 300);
        Set<Chunk> map = Set.of(tower, near, middle, far);
        lights.begin(1, 1);
        lights.found(tower.place(), tower.wx, tower.wy, levels(-8, 31));
        take(lights, tower);
        leave(lights, map);
        for (int frame = 2; frame <= 4; frame++) {
            lights.begin(1, frame);
            leave(lights, map);
        }
        // The last pass took 40 levels over four frames: 2,560 squares, a share of 800.
        lights.begin(2, 5);
        int squares = 0;
        for (Chunk chunk : List.of(near, middle, far)) {
            lights.found(chunk.place(), chunk.wx, chunk.wy, levels(0, 5));
            squares += take(lights, chunk);
        }
        squares += leave(lights, map);
        check(taken.containsAll(List.of(new Took(near, 0), new Took(near, 5), new Took(middle, 0), new Took(middle, 5))), "the nearer chunks are taken whole in the frame their light is found");
        check(taken.contains(new Took(far, 0)) && !taken.contains(new Took(far, 1)) && lights.levelsWaiting() == 5,
                "the far chunk's first level takes the share past its end, and its other five wait");
        check(squares <= 800 + SQUARES, "the frame takes its share and at most a level more: " + squares);
        lights.begin(2, 6);
        int next = 0;
        for (Chunk chunk : List.of(near, middle, far)) {
            next += take(lights, chunk);
        }
        next += leave(lights, map);
        check(next == 5 * SQUARES && lights.waiting() == 0 && taken.contains(new Took(far, 5)), "the next frame of the pass takes the five");
    }

    private static void whatWaitsIsTakenInTheFrameThatSeesTheNextPass() {
        taken.clear();
        PendingLights lights = leastShared(2, 3);
        Chunk near = new Chunk(50, 50), far = new Chunk(58, 50);
        Set<Chunk> map = Set.of(near, far);
        // The near chunk takes the frame's whole share, and the pass ends with the frame.
        lights.found(near.place(), near.wx, near.wy, levels(0, 7));
        take(lights, near);
        lights.found(far.place(), far.wx, far.wy, levels(0, 9));
        take(lights, far);
        leave(lights, map);
        check(lights.levelsWaiting() == 10 && !taken.contains(new Took(far, 0)), "the far chunk's ten levels wait while the nearer one takes the frame's share");
        // The far chunk is out of view now and the walk does not come to it: what is left takes it.
        lights.begin(3, 4);
        lights.found(near.place(), near.wx, near.wy, levels(0, 7));
        int squares = take(lights, near);
        squares += leave(lights, map);
        check(lights.waiting() == 0 && taken.containsAll(List.of(new Took(far, 0), new Took(far, 9))), "the frame that sees the next pass takes all ten");
        check(squares == 18 * SQUARES, "whatever its share, here 640 squares: " + squares);
    }

    private static void aLevelFoundAgainWhileItWaitsIsTakenOnce() {
        taken.clear();
        PendingLights lights = leastShared(2, 3);
        Chunk near = new Chunk(-4, 9), far = new Chunk(2, 9);
        Set<Chunk> map = Set.of(near, far);
        lights.found(near.place(), near.wx, near.wy, levels(0, 7));
        take(lights, near);
        lights.found(far.place(), far.wx, far.wy, levels(0, 9));
        take(lights, far);
        leave(lights, map);
        check(lights.levelsWaiting() == 10, "ten levels of the far chunk wait as the pass ends");
        // The next pass finds five of them relit again, and five more, and the frame has 128 squares of its share left when the walk comes to the chunk.
        lights.begin(3, 4);
        lights.found(near.place(), near.wx, near.wy, levels(0, 7));
        take(lights, near);
        lights.found(far.place(), far.wx, far.wy, levels(5, 14));
        take(lights, far);
        leave(lights, map);
        check(taken.containsAll(List.of(new Took(far, 0), new Took(far, 9))) && !taken.contains(new Took(far, 10)) && lights.levelsWaiting() == 5,
                "the ten from the pass before are taken when the walk comes to them, and the five new ones wait for the share");
        lights.begin(3, 5);
        take(lights, far);
        leave(lights, map);
        Map<Took, Integer> times = new HashMap<>();
        for (Took each : taken) {
            times.merge(each, 1, Integer::sum);
        }
        boolean once = true;
        for (int z = 0; z <= 14; z++) {
            once &= times.getOrDefault(new Took(far, z), 0) == 1;
        }
        check(once, "every level the two passes found is taken, and the five found twice only once");
        check(lights.waiting() == 0 && lights.levelsWaiting() == 0, "and nothing is left waiting");
    }

    private static void aChunkTheMapLetsGoOfTakesItsLevelsWithIt() {
        taken.clear();
        PendingLights lights = leastShared(2, 3);
        Chunk near = new Chunk(700, 40), far = new Chunk(706, 40), along = new Chunk(706 + ChunkMarks.SIDE, 40);
        lights.found(near.place(), near.wx, near.wy, levels(0, 7));
        take(lights, near);
        lights.found(far.place(), far.wx, far.wy, levels(0, 5));
        take(lights, far);
        leave(lights, Set.of(near, far));
        check(lights.levelsWaiting() == 6, "six levels of the far chunk wait");
        lights.begin(2, 4);
        lights.found(near.place(), near.wx, near.wy, levels(0, 7));
        take(lights, near);
        leave(lights, Set.of(near));
        check(lights.waiting() == 0 && lights.levelsWaiting() == 0 && !taken.contains(new Took(far, 0)), "the map lets go of the far chunk, and its six go with it untaken");
        check(far.place() == along.place(), "the chunk a table's width on has the same place");
        lights.found(far.place(), far.wx, far.wy, levels(0, 5));
        take(lights, far);
        lights.found(along.place(), along.wx, along.wy, levels(20, 22));
        check(lights.levelsWaiting() == 3, "a chunk found in the place of one that left the map waits with its own three levels alone");
        lights.begin(3, 5);
        take(lights, along);
        leave(lights, Set.of(near, along));
        check(taken.contains(new Took(along, 20)) && !taken.contains(new Took(along, 0)) && lights.levelsWaiting() == 0, "and is taken with them alone");
    }

    private static void aSteadyDriveTakesEachPassOverItsFrames() {
        taken.clear();
        PendingLights lights = new PendingLights();
        List<Chunk> walk = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            walk.add(new Chunk(1000 + i % 6, 1000 + i / 6));
        }
        Set<Chunk> map = new HashSet<>(walk);
        // Three or four frames a pass, as on the drive, and two levels of every chunk found relit at the start of each: 3,840 squares a pass.
        int[] lengths = {4, 3, 4, 4, 3, 4, 3, 4, 4, 3, 4, 4, 3, 4, 3, 4};
        // For each level of each chunk in the walk, the pass and the frame it has waited from, the pass 0 while it does not wait.
        int[][] owedPass = new int[walk.size()][2], owedFrame = new int[walk.size()][2];
        int frame = 0, most = 0, longest = 0;
        boolean foundFirst = true, withinAPass = true, nearestFirst = true;
        for (int pass = 1; pass <= lengths.length; pass++) {
            for (int f = 0; f < lengths[pass - 1]; f++) {
                frame++;
                lights.begin(pass, frame);
                int squares = 0, before = taken.size();
                for (int i = 0; i < walk.size(); i++) {
                    Chunk chunk = walk.get(i);
                    if (f == 0) {
                        lights.found(chunk.place(), chunk.wx, chunk.wy, levels(0, 1));
                        for (int z = 0; z <= 1; z++) {
                            if (owedPass[i][z] == 0) {
                                owedPass[i][z] = pass;
                                owedFrame[i][z] = frame;
                            }
                        }
                    }
                    squares += take(lights, chunk);
                }
                squares += leave(lights, map);
                int furthest = -1;
                for (int t = before; t < taken.size(); t++) {
                    int i = walk.indexOf(taken.get(t).chunk), z = taken.get(t).z;
                    foundFirst &= owedPass[i][z] != 0;
                    withinAPass &= owedPass[i][z] == pass || owedPass[i][z] == pass - 1 && f == 0;
                    longest = Math.max(longest, frame - owedFrame[i][z]);
                    if (owedPass[i][z] == pass) {
                        furthest = Math.max(furthest, i);
                    }
                    owedPass[i][z] = 0;
                }
                // Of this pass's light, none waits nearer than the furthest chunk the frame took it from.
                for (int i = 0; i < furthest; i++) {
                    nearestFirst &= owedPass[i][0] != pass && owedPass[i][1] != pass;
                }
                if (pass > 2) {
                    most = Math.max(most, squares);
                }
            }
        }
        long owed = 0;
        boolean held = true;
        for (int i = 0; i < walk.size(); i++) {
            for (int z = 0; z <= 1; z++) {
                if (owedPass[i][z] != 0) {
                    owed++;
                    held &= lights.waits(walk.get(i).place());
                }
            }
        }
        check(most > 0 && most <= 3840 / 2, "once two passes have been seen, no frame takes as much as half a pass: at most " + most + " of 3,840 squares");
        check(foundFirst, "nothing is taken that was not found and waiting");
        check(withinAPass && longest <= 4, "every level is taken in the pass it was found in or the first frame of the next, at most " + longest + " frames after it was found");
        check(nearestFirst, "and the light a pass found is taken nearest first");
        check(held && owed == lights.levelsWaiting(), "every level found and not taken waits, and no other: " + owed + " against " + lights.levelsWaiting());
    }

    private static void aBigFindIsTakenOverWhatIsLeftOfItsPass() {
        taken.clear();
        PendingLights lights = new PendingLights();
        List<Chunk> walk = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            walk.add(new Chunk(2000 + i, 2000));
        }
        Set<Chunk> map = new HashSet<>(walk);
        int frame = 0;
        // Passes of two frames and three, 2.86 on the mean by the fifth, that find a level of each of the nearest 20 chunks,
        // 1,280 squares: a share of 534 after the fourth.
        int[] lengths = {3, 2, 3, 3};
        for (int pass = 1; pass <= lengths.length; pass++) {
            for (int f = 0; f < lengths[pass - 1]; f++) {
                lights.begin(pass, ++frame);
                if (f == 0) {
                    for (Chunk chunk : walk.subList(0, 20)) {
                        lights.found(chunk.place(), chunk.wx, chunk.wy, levels(0, 0));
                    }
                }
                for (Chunk chunk : walk) {
                    take(lights, chunk);
                }
                leave(lights, map);
            }
        }
        // Then a pass finds four levels of all 40, 10,240 squares.
        int[] squares = new int[3];
        for (int f = 0; f < 3; f++) {
            lights.begin(5, ++frame);
            for (Chunk chunk : walk) {
                if (f == 0) {
                    lights.found(chunk.place(), chunk.wx, chunk.wy, levels(0, 3));
                }
                squares[f] += take(lights, chunk);
            }
            squares[f] += leave(lights, map);
        }
        check(squares[0] <= 534 + SQUARES && taken.contains(new Took(walk.get(0), 3)), "the frame that finds it takes the share, nearest first: " + squares[0]);
        check(lights.levelsWaiting() == 0 && squares[0] + squares[1] + squares[2] == 160 * SQUARES, "the two frames after it take the rest within the pass, so none of it falls due");
        int after = 160 * SQUARES - squares[0];
        check(squares[1] <= after * 2 / 3 && squares[2] >= after / 3, "and the next takes what waits over the 1.86 frames the pass is expected to have left, not all of it: "
                + squares[1] + " and " + squares[2] + " of " + after);
    }

    private static void withTheSpreadOffEveryLevelIsTakenInTheFrameItIsFound() {
        taken.clear();
        Chunk tower = new Chunk(300, 300), near = new Chunk(301, 300), middle = new Chunk(303, 300), far = new Chunk(306, 300);
        Set<Chunk> map = Set.of(tower, near, middle, far);
        // As aFrameTakesItsShareNearestFirst: a share of 800 squares, which the far chunk's light would wait past.
        PendingLights lights = new PendingLights();
        lights.begin(1, 1);
        lights.found(tower.place(), tower.wx, tower.wy, levels(-8, 31));
        take(lights, tower);
        leave(lights, map);
        for (int frame = 2; frame <= 4; frame++) {
            lights.begin(1, frame);
            leave(lights, map);
        }
        PendingLights.set(false);
        try {
            lights.begin(2, 5);
            int before = taken.size();
            for (Chunk chunk : List.of(near, middle, far)) {
                lights.found(chunk.place(), chunk.wx, chunk.wy, levels(0, 5));
                take(lights, chunk);
            }
            List<Took> walk = new ArrayList<>();
            for (Chunk chunk : List.of(near, middle, far)) {
                for (int z = 0; z <= 5; z++) {
                    walk.add(new Took(chunk, z));
                }
            }
            check(taken.subList(before, taken.size()).equals(walk) && lights.levelsWaiting() == 0,
                    "every level found is taken in the walk of the frame it is found in, in the walk's order and from the lowest level up: " + taken.subList(before, taken.size()));
            leave(lights, map);
            // Turned on, a pass's light is spread again: the last pass's 1,152 squares in one frame give a share of 1,440, and 6,144 are found.
            PendingLights.set(true);
            lights.begin(3, 6);
            for (Chunk chunk : List.of(near, middle, far)) {
                lights.found(chunk.place(), chunk.wx, chunk.wy, levels(0, 31));
                take(lights, chunk);
            }
            leave(lights, map);
            check(lights.levelsWaiting() > 0, "turned on again, the frame takes its share and the rest waits: " + lights.levelsWaiting());
            // Turned off with light waiting, the next frame takes all of it.
            PendingLights.set(false);
            lights.begin(3, 7);
            leave(lights, map);
            check(lights.levelsWaiting() == 0, "turned off with light waiting, the next frame takes what waits: " + lights.levelsWaiting());
        } finally {
            PendingLights.set(true);
        }
    }

    private static void theCheckFindsWhatWaitsFromAnEarlierPass() {
        taken.clear();
        PendingLights lights = leastShared(2, 3);
        Chunk near = new Chunk(80, 81), far = new Chunk(88, 81);
        lights.found(near.place(), near.wx, near.wy, levels(0, 7));
        take(lights, near);
        lights.found(far.place(), far.wx, far.wy, levels(0, 3));
        leave(lights, Set.of(near, far));
        lights.begin(3, 4);
        try {
            lights.check();
            check(false, "a level waiting from the pass before is caught before the frame has taken it");
        } catch (IllegalStateException caught) {
            check(caught.getMessage().contains("88, 81"), "the check names the chunk that still waits");
        }
        leave(lights, Set.of(near, far));
        check(lights.waiting() == 0, "and once the frame has taken it, it holds");
    }
}

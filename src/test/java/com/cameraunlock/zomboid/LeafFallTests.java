package com.cameraunlock.zomboid;

import java.util.Arrays;

/** Tests of the leaves that fall from broad-leaved trees in autumn: see LeafFall. */
final class LeafFallTests {
    private LeafFallTests() {}

    static void run() {
        Checks.run("onlyBroadLeavedTreesShed", LeafFallTests::onlyBroadLeavedTreesShed);
        Checks.run("nothingFallsOutsideAutumn", LeafFallTests::nothingFallsOutsideAutumn);
        Checks.run("treesShedAsTheyTurnMostAsTheyThinAndNoneBare", LeafFallTests::treesShedAsTheyTurnMostAsTheyThinAndNoneBare);
        Checks.run("aBareCrownShedsNothingAndAFullAutumnOneDoes", LeafFallTests::aBareCrownShedsNothingAndAFullAutumnOneDoes);
        Checks.run("onlyTreesInLeafWithACrownWithinReachShed", LeafFallTests::onlyTreesInLeafWithACrownWithinReachShed);
        Checks.run("theNearestTreesAreKeptWhateverTheOrderTheyCome", LeafFallTests::theNearestTreesAreKeptWhateverTheOrderTheyCome);
        Checks.run("aNearTreeShedsMoreThanAFarOne", LeafFallTests::aNearTreeShedsMoreThanAFarOne);
        Checks.run("theWindTakesMoreLeavesAndCarriesThemFurther", LeafFallTests::theWindTakesMoreLeavesAndCarriesThemFurther);
        Checks.run("leavesLetGoFromTheCrownTheColourItTurned", LeafFallTests::leavesLetGoFromTheCrownTheColourItTurned);
        Checks.run("aLeafMovesTheSameAtAnyFrameRate", LeafFallTests::aLeafMovesTheSameAtAnyFrameRate);
        Checks.run("asManyLeavesLetGoAtAnyFrameRate", LeafFallTests::asManyLeavesLetGoAtAnyFrameRate);
        Checks.run("aHitchIsTakenAsAQuarterSecond", LeafFallTests::aHitchIsTakenAsAQuarterSecond);
        Checks.run("leavesLandLieAndFade", LeafFallTests::leavesLandLieAndFade);
        Checks.run("whenThereIsNoRoomLyingLeavesFadeToMakeIt", LeafFallTests::whenThereIsNoRoomLyingLeavesFadeToMakeIt);
        Checks.run("aLeafCarriedOffIsLetGoPastKept", LeafFallTests::aLeafCarriedOffIsLetGoPastKept);
        Checks.run("aJumpOfTheEyeLetsEveryLeafGo", LeafFallTests::aJumpOfTheEyeLetsEveryLeafGo);
        Checks.run("aLeafBlownAtABuildingIsCaughtAndFadesOrGoesOver", LeafFallTests::aLeafBlownAtABuildingIsCaughtAndFadesOrGoesOver);
        Checks.run("aGroundFloorOnAHillCatchesALeaf", LeafFallTests::aGroundFloorOnAHillCatchesALeaf);
        Checks.run("noLeafLetsGoInsideAHouseACrownReachesOver", LeafFallTests::noLeafLetsGoInsideAHouseACrownReachesOver);
        Checks.run("aLeafFallingOntoARoofIsCaughtAtIt", LeafFallTests::aLeafFallingOntoARoofIsCaughtAtIt);
        Checks.run("aLeafFallingOntoARoofDownhillIsCaughtAtIt", LeafFallTests::aLeafFallingOntoARoofDownhillIsCaughtAtIt);
        Checks.run("treesAreReadRoundTheEyeWhereverItIs", LeafFallTests::treesAreReadRoundTheEyeWhereverItIs);
        Checks.run("nothingMovesWhileThePictureWaits", LeafFallTests::nothingMovesWhileThePictureWaits);
    }

    private static final float PEAK_AUTUMN = 0.45f;
    /** A test tree's crown: lowest and highest leaves over the ground and how far out, in tiles. */
    private static final float LOW = 1.8f, HIGH = 5f, OUT = 1.6f;
    private static final String MAPLE = "e_redmaple_1_3";

    /**
     * Level ground at height lift with a building on every square from x = wallFrom on, its
     * rooms the storeys under storeys, asked as the game's map is: by the storey LeafFall.storey gives.
     */
    private record Flat(int wallFrom, int storeys, float lift) implements LeafFall.Ground {
        Flat(int wallFrom, int storeys) {
            this(wallFrom, storeys, 0f);
        }

        @Override
        public float height(float x, float y) {
            return lift;
        }

        @Override
        public boolean walled(int x, int y, float up) {
            return x >= wallFrom && LeafFall.storey(up, lift) < storeys;
        }
    }

    private static final Flat OPEN = new Flat(Integer.MAX_VALUE, 0);

    /** A slope: a building on every square from x = wallFrom on, on ground at houseLift, with the ground outside it at outsideLift. */
    private record Terraced(int wallFrom, int storeys, float houseLift, float outsideLift) implements LeafFall.Ground {
        @Override
        public float height(float x, float y) {
            return x >= wallFrom ? houseLift : outsideLift;
        }

        @Override
        public boolean walled(int x, int y, float up) {
            return x >= wallFrom && LeafFall.storey(up, height(x + 0.5f, y + 0.5f)) < storeys;
        }
    }

    /** A sim whose trees stand on a grid round (0, 0), count of them, all red maples in leaf. */
    private static LeafFall grove(int count) {
        LeafFall fall = new LeafFall();
        fall.beginTrees(0f, 0f);
        int side = (int) Math.ceil(Math.sqrt(count));
        for (int i = 0; i < count; i++) {
            int x = (i % side) * 3 - side, y = (i / side) * 3 - side;
            fall.offer(x + 0.5f, y + 0.5f, 0f, MAPLE, Seasons.treeHash(x, y), true, LOW, HIGH, OUT);
        }
        fall.endTrees();
        return fall;
    }

    /** A hash whose tree sheds at the height of autumn at least that many leaves a second in still air. */
    private static int shedding(float least) {
        for (int hash = 0; ; hash++) {
            if (LeafFall.shedding(true, Seasons.AUTUMN, PEAK_AUTUMN, hash) >= least) {
                return hash;
            }
        }
    }

    private static void run(LeafFall fall, float seconds, float step, float airX, float airY, float intensity, int season, float progression) {
        run(fall, seconds, step, airX, airY, intensity, season, progression, OPEN);
    }

    private static void run(LeafFall fall, float seconds, float step, float airX, float airY, float intensity, int season, float progression, LeafFall.Ground on) {
        for (float t = 0f; t < seconds - 1e-4f; t += step) {
            fall.step(step, airX, airY, intensity, season, progression, 0f, 0f, on);
        }
    }

    private static void onlyBroadLeavedTreesShed() {
        Checks.check(LeafFall.sheds(MAPLE) && LeafFall.sheds("e_americanlindenJUMBO_1_0") && LeafFall.sheds("e_riverbirch_1_2"), "maple, linden and birch shed");
        Checks.check(!LeafFall.sheds("e_americanholly_1_3") && !LeafFall.sheds("e_easternwhitepine_1_2") && !LeafFall.sheds("e_canadianhemlockJUMBO_1_1"),
                "holly, pine and hemlock keep theirs");
        Checks.check(!LeafFall.sheds("fencing_burnt_01_17") && !LeafFall.sheds(null), "a dead tree and an unread one shed nothing");
    }

    private static void nothingFallsOutsideAutumn() {
        for (int season : new int[] {Seasons.SPRING, Seasons.SUMMER, Seasons.SUMMER2, Seasons.WINTER}) {
            for (int day = 0; day <= 20; day++) {
                for (int hash = 0; hash < 256; hash += 7) {
                    Checks.check(LeafFall.shedding(true, season, day / 20f, hash) == 0f, "season " + season + " sheds nothing");
                }
            }
            LeafFall fall = grove(30);
            run(fall, 30f, 1f / 60f, 3f, 0f, 0.6f, season, 0.5f);
            Checks.check(fall.spawned == 0 && fall.count() == 0, "a gale in season " + season + " takes no leaf: " + fall.spawned);
        }
    }

    private static void treesShedAsTheyTurnMostAsTheyThinAndNoneBare() {
        for (int hash = 0; hash < 4096; hash += 13) {
            float most = 0f, mostAt = -1f, first = -1f;
            for (int day = 0; day <= 400; day++) {
                float p = day / 400f;
                float shed = LeafFall.shedding(true, Seasons.AUTUMN, p, hash), on = LeafFall.crownLeaf(true, Seasons.AUTUMN, p, hash);
                float turned = Seasons.turned(Seasons.AUTUMN, Seasons.stepped(Seasons.AUTUMN, p), hash);
                Checks.check(shed >= 0f, "shedding is never less than none");
                if (on == 0f || turned == 0f) {
                    Checks.check(shed == 0f, "a bare tree or one still green sheds nothing, day " + day);
                } else {
                    Checks.check(shed > 0f, "a turning tree in leaf sheds, day " + day);
                }
                if (shed > 0f && first < 0f) {
                    first = on;
                }
                if (shed > most) {
                    most = shed;
                    mostAt = on;
                }
            }
            Checks.check(first == 1f, "a tree starts to shed while it still has all its leaves");
            Checks.check(mostAt > 0.3f && mostAt < 0.7f, "a tree sheds most as its crown is half gone: " + mostAt);
            Checks.check(most > 3f * LeafFall.PEAK * LeafFall.TRICKLE, "the thinning sheds a few times what the turning does");
        }
    }

    /** What falls goes by the crown as it is drawn: the mod's leaf at the crowns' step, and none where the game has the tree bare. */
    private static void aBareCrownShedsNothingAndAFullAutumnOneDoes() {
        int bare = 0, full = 0;
        for (int hash = 0; hash < 4096; hash += 3) {
            for (int day = 0; day <= 200; day++) {
                float p = day / 200f, crown = LeafFall.crownLeaf(true, Seasons.AUTUMN, p, hash);
                float turned = Seasons.turned(Seasons.AUTUMN, Seasons.stepped(Seasons.AUTUMN, p), hash);
                Checks.check(LeafFall.shedding(false, Seasons.AUTUMN, p, hash) == 0f, "a tree the game has bare sheds nothing, whatever the season says");
                if (crown == 0f) {
                    bare++;
                    Checks.check(LeafFall.shedding(true, Seasons.AUTUMN, p, hash) == 0f, "a bare crown sheds nothing, day " + day);
                }
                if (crown == 1f && turned >= 0.3f) {
                    full++;
                    Checks.check(LeafFall.shedding(true, Seasons.AUTUMN, p, hash) > 0.5f * LeafFall.PEAK * LeafFall.TRICKLE, "a crown still full of autumn colour sheds, day " + day);
                }
            }
        }
        Checks.check(bare > 1000 && full > 1000, "both kinds of crown were met: " + bare + " bare, " + full + " full and turned");
    }

    /** A tree with the game's leaves off it, one no crown of which has been built, an evergreen, and one past REACH let nothing go; one in leaf beside them does. */
    private static void onlyTreesInLeafWithACrownWithinReachShed() {
        int hash = shedding(1f);
        LeafFall none = new LeafFall();
        none.beginTrees(0f, 0f);
        none.offer(1.5f, 0.5f, 0f, MAPLE, hash, false, LOW, HIGH, OUT);
        none.offer(-1.5f, 0.5f, 0f, MAPLE, hash, true, 0f, 0f, 0f);
        none.offer(0.5f, 2.5f, 0f, "e_americanholly_1_3", hash, true, LOW, HIGH, OUT);
        none.offer(LeafFall.REACH + 0.5f, 0.5f, 0f, MAPLE, hash, true, LOW, HIGH, OUT);
        none.endTrees();
        run(none, 30f, 1f / 60f, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN);
        Checks.check(none.spawned == 0, "none of them lets a leaf go: " + none.spawned);
        LeafFall one = new LeafFall();
        one.beginTrees(0f, 0f);
        one.offer(1.5f, 0.5f, 0f, MAPLE, hash, true, LOW, HIGH, OUT);
        one.endTrees();
        run(one, 30f, 1f / 60f, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN);
        Checks.check(one.spawned > 30, "a tree in leaf with a crown lets them go: " + one.spawned);
    }

    /** A wood with more trees than are kept: the one beside the eye sheds wherever in the read it comes. */
    private static void theNearestTreesAreKeptWhateverTheOrderTheyCome() {
        int hash = shedding(1f);
        LeafFall fall = new LeafFall();
        fall.beginTrees(0f, 0f);
        int offered = 0;
        for (int y = -30; y <= 30 && offered < 2 * LeafFall.MOST_TREES; y += 2) {
            for (int x = -30; x <= 30 && offered < 2 * LeafFall.MOST_TREES; x += 2) {
                if (x * x + y * y > 9) {
                    fall.offer(x + 0.5f, y + 0.5f, 0f, MAPLE, Seasons.treeHash(x, y), true, LOW, HIGH, OUT);
                    offered++;
                }
            }
        }
        fall.offer(0.5f, 0.5f, 0f, MAPLE, hash, true, LOW, HIGH, OUT);
        fall.endTrees();
        Checks.check(offered > LeafFall.MOST_TREES, "more trees offered than are kept: " + offered);
        run(fall, 10f, 1f / 60f, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN);
        Checks.check(fall.released(0) > 20, "the tree beside the eye, offered last, is the first kept and sheds: " + fall.released(0));
    }

    private static void aNearTreeShedsMoreThanAFarOne() {
        Checks.check(LeafFall.nearness(0f) == 1f && LeafFall.nearness(LeafFall.NEAR) == 1f && LeafFall.nearness(20f) < 1f
                && LeafFall.nearness(LeafFall.REACH) > 0.25f && LeafFall.nearness(LeafFall.REACH) < LeafFall.nearness(20f), "a tree near the eye sheds all it would, one further off a share");
        int hash = shedding(1f);
        LeafFall fall = new LeafFall();
        fall.beginTrees(0f, 0f);
        fall.offer(30.5f, 0.5f, 0f, MAPLE, hash, true, LOW, HIGH, OUT);
        fall.offer(2.5f, 0.5f, 0f, MAPLE, hash, true, LOW, HIGH, OUT);
        fall.endTrees();
        run(fall, 120f, 1f / 30f, 0f, 0f, 0.1f, Seasons.AUTUMN, PEAK_AUTUMN);
        Checks.check(fall.released(0) > 2 * fall.released(1) && fall.released(1) > 0, "the near tree lets go more than twice what the same tree far off does: "
                + fall.released(0) + " against " + fall.released(1));
    }

    private static void theWindTakesMoreLeavesAndCarriesThemFurther() {
        Checks.check(LeafFall.letGo(0f) < LeafFall.letGo(0.1f) && LeafFall.letGo(0.1f) < LeafFall.letGo(0.4f) && LeafFall.letGo(0.6f) >= 3f, "the wind lets more go");
        Checks.check(LeafFall.carried(0.02f) < LeafFall.carried(0.3f), "a strong wind carries a leaf more than still air");
        LeafFall calm = grove(20), windy = grove(20);
        run(calm, 20f, 1f / 60f, 0.1f, 0f, 0.02f, Seasons.AUTUMN, PEAK_AUTUMN);
        run(windy, 20f, 1f / 60f, 15f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN);
        Checks.check(windy.spawned > 3 * calm.spawned && calm.spawned > 20, "a gale takes many times the leaves still air does: " + windy.spawned + " against " + calm.spawned);
        float[] leaves = new float[LeafFall.MOST * LeafFall.FLOATS];
        int calmCount = calm.write(leaves, 0f, 0f);
        float calmEast = meanX(leaves, calmCount);
        int windyCount = windy.write(leaves, 0f, 0f);
        float windyEast = meanX(leaves, windyCount);
        Checks.check(Math.abs(calmEast) < 3f, "on a still day leaves drift little: " + calmEast);
        Checks.check(windyEast > calmEast + 8f, "in a gale they are carried downwind: " + windyEast);
    }

    private static float meanX(float[] leaves, int count) {
        float sum = 0f;
        for (int i = 0; i < count; i++) {
            sum += leaves[i * LeafFall.FLOATS + LeafFall.X];
        }
        return count == 0 ? 0f : sum / count;
    }

    private static void leavesLetGoFromTheCrownTheColourItTurned() {
        int hash = shedding(1f);
        LeafFall fall = new LeafFall();
        fall.beginTrees(0f, 0f);
        fall.offer(-0.5f, -0.5f, 0f, MAPLE, hash, true, 3f, 4f, 1f);
        fall.endTrees();
        float[] leaves = new float[LeafFall.MOST * LeafFall.FLOATS];
        int seen = 0;
        for (int round = 0; round < 400; round++) {
            fall.step(0.25f, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, OPEN);
            // A leaf fades in over its first moments: a little later it shows. In that time it falls a little and swings.
            fall.step(0.3f, 0f, 0f, 0.5f, Seasons.SUMMER, 0.5f, 0f, 0f, OPEN);
            int count = fall.write(leaves, 0f, 0f);
            for (int i = 0; i < count; i++) {
                int at = i * LeafFall.FLOATS;
                seen++;
                float out = (float) Math.hypot(leaves[at + LeafFall.X] + 0.5f, leaves[at + LeafFall.Y] + 0.5f);
                Checks.check(out < 1.3f, "a leaf just let go is within its crown's reach: " + out);
                Checks.check(leaves[at + LeafFall.UP] > 3f - 0.6f && leaves[at + LeafFall.UP] < 4.2f, "and between its crown's lowest and highest leaves: " + leaves[at + LeafFall.UP]);
                Checks.check(leaves[at + LeafFall.HUE] == Flora.autumnHue(MAPLE, hash), "a leaf is its tree's colour");
            }
            fall.clear();
        }
        Checks.check(seen > 50, "leaves were let go: " + seen);
    }

    /** The same leaves stepped at 30 and at 144 frames a second, through falling, landing and lying, are in the same places. */
    private static void aLeafMovesTheSameAtAnyFrameRate() {
        LeafFall slow = grove(40), fast = grove(40);
        // One step lets the same leaves go in both; the season then stops any more.
        slow.step(LeafFall.STEP_MOST, 1.5f, 0.5f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, OPEN);
        fast.step(LeafFall.STEP_MOST, 1.5f, 0.5f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, OPEN);
        Checks.check(slow.count() > 20 && slow.count() == fast.count(), "both let the same leaves go: " + slow.count());
        float done = 0f;
        for (float at : new float[] {1f, 2.5f, 6f}) {
            run(slow, at - done, 1f / 30f, 1.5f, 0.5f, 0.5f, Seasons.SUMMER, 0.5f);
            run(fast, at - done, 1f / 144f, 1.5f, 0.5f, 0.5f, Seasons.SUMMER, 0.5f);
            done = at;
            float[][] a = sorted(slow), b = sorted(fast);
            Checks.check(a.length == b.length, "the same leaves are left after " + at + " s: " + a.length + " and " + b.length);
            float worst = 0f;
            for (int i = 0; i < Math.min(a.length, b.length); i++) {
                for (int k = 0; k < LeafFall.FLOATS; k++) {
                    worst = Math.max(worst, Math.abs(a[i][k] - b[i][k]));
                }
            }
            Checks.check(worst < 2e-3f, "every leaf is where it is, turned as it is, after " + at + " s at either rate: off by " + worst);
        }
    }

    private static void asManyLeavesLetGoAtAnyFrameRate() {
        LeafFall slow = grove(20), fast = grove(20);
        run(slow, 20f, 1f / 30f, 0.5f, 0f, 0.2f, Seasons.AUTUMN, PEAK_AUTUMN);
        run(fast, 20f, 1f / 144f, 0.5f, 0f, 0.2f, Seasons.AUTUMN, PEAK_AUTUMN);
        Checks.check(slow.spawned > 200 && Math.abs(slow.spawned - fast.spawned) <= 3, "as many leaves let go at 30 and 144 frames a second: " + slow.spawned + " and " + fast.spawned);
    }

    private static void aHitchIsTakenAsAQuarterSecond() {
        LeafFall hitched = grove(20), steady = grove(20);
        hitched.step(LeafFall.STEP_MOST, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, OPEN);
        steady.step(LeafFall.STEP_MOST, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, OPEN);
        hitched.step(5f, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, OPEN);
        steady.step(LeafFall.STEP_MOST, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, OPEN);
        Checks.check(Arrays.deepEquals(sorted(hitched), sorted(steady)) && hitched.spawned == steady.spawned,
                "a five second hitch moves the leaves and lets them go as a quarter second does: " + hitched.spawned + " and " + steady.spawned);
    }

    private static float[][] sorted(LeafFall fall) {
        float[] leaves = new float[LeafFall.MOST * LeafFall.FLOATS];
        int count = fall.write(leaves, 0f, 0f);
        float[][] each = new float[count][];
        for (int i = 0; i < count; i++) {
            each[i] = Arrays.copyOfRange(leaves, i * LeafFall.FLOATS, (i + 1) * LeafFall.FLOATS);
        }
        Arrays.sort(each, (p, q) -> Float.compare(p[LeafFall.X] + 7.1f * p[LeafFall.Y], q[LeafFall.X] + 7.1f * q[LeafFall.Y]));
        return each;
    }

    private static void leavesLandLieAndFade() {
        LeafFall fall = grove(10);
        fall.step(LeafFall.STEP_MOST, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, OPEN);
        int let = fall.count();
        Checks.check(let > 0, "leaves let go");
        run(fall, 9.75f, 1f / 60f, 0f, 0f, 0.5f, Seasons.SUMMER, 0.5f);
        Checks.check(fall.landings == let, "every leaf has landed: " + fall.landings + " of " + let);
        float[] leaves = new float[LeafFall.MOST * LeafFall.FLOATS];
        int count = fall.write(leaves, 0f, 0f);
        for (int i = 0; i < count; i++) {
            int at = i * LeafFall.FLOATS;
            Checks.check(leaves[at + LeafFall.UP] < 0.01f, "a landed leaf lies on the ground: " + leaves[at + LeafFall.UP]);
            Checks.check(Math.abs(leaves[at + LeafFall.ALONG_UP]) < 0.01f && Math.abs(leaves[at + LeafFall.ACROSS_UP]) < 0.01f, "and lies near flat");
        }
        run(fall, LeafFall.LIES_MOST + LeafFall.FADES + 0.1f, 1f / 60f, 0f, 0f, 0.5f, Seasons.SUMMER, 0.5f);
        Checks.check(fall.count() == 0 && fall.vanished == 0, "every landed leaf has faded, none vanished: " + fall.count() + " left, " + fall.vanished + " vanished");
    }

    /**
     * A wood where more leaves let go than there is room for: the room is made by lying leaves
     * fading sooner, so leaves keep coming at a rate their lying out their time would not allow,
     * the nearest tree keeps shedding, and none vanishes while it shows.
     */
    private static void whenThereIsNoRoomLyingLeavesFadeToMakeIt() {
        int hash = shedding(1f);
        LeafFall fall = new LeafFall();
        fall.beginTrees(0f, 0f);
        for (int y = -30; y <= 30; y += 2) {
            for (int x = -30; x <= 30; x += 2) {
                fall.offer(x + 1.5f, y + 1.5f, 0f, MAPLE, Seasons.treeHash(x, y), true, LOW, HIGH, OUT);
            }
        }
        fall.offer(0.5f, 0.5f, 0f, MAPLE, hash, true, LOW, HIGH, OUT);
        fall.endTrees();
        run(fall, 15f, 1f / 30f, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN);
        Checks.check(fall.count() > LeafFall.MOST - 50, "the wood fills the room: " + fall.count());
        long before = fall.spawned;
        int nearBefore = fall.released(0), carpetLeast = Integer.MAX_VALUE;
        for (int second = 0; second < 20; second++) {
            run(fall, 1f, 1f / 30f, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN);
            carpetLeast = Math.min(carpetLeast, fall.carpet());
        }
        Checks.check(carpetLeast >= LeafFall.CARPET - 40, "a carpet of leaves still lies out its time: at least " + carpetLeast + " of " + LeafFall.CARPET);
        float perSecond = (fall.spawned - before) / 20f;
        // Lying out its time, a leaf would hold its place for (LIES_LEAST + LIES_MOST) / 2 + FADES seconds after its fall.
        float layingOut = LeafFall.MOST / ((LeafFall.LIES_LEAST + LeafFall.LIES_MOST) / 2f + LeafFall.FADES);
        Checks.check(perSecond > layingOut, "leaves keep coming faster than lying out their time would let them: " + perSecond + " a second against " + layingOut);
        float nearRate = LeafFall.letGo(0.5f) * LeafFall.shedding(true, Seasons.AUTUMN, PEAK_AUTUMN, hash);
        Checks.check(fall.released(0) - nearBefore >= 0.8f * nearRate * 20f, "the nearest tree sheds all it would: " + (fall.released(0) - nearBefore) + " of " + nearRate * 20f);
        Checks.check(fall.vanished == 0, "no leaf vanished while it showed: " + fall.vanished);
    }

    private static void aLeafCarriedOffIsLetGoPastKept() {
        LeafFall fall = grove(4);
        run(fall, 2f, 1f / 60f, 20f, 0f, 0.6f, Seasons.AUTUMN, PEAK_AUTUMN);
        run(fall, 3f, 1f / 60f, 20f, 0f, 0.6f, Seasons.SUMMER, 0.5f);
        float[] leaves = new float[LeafFall.MOST * LeafFall.FLOATS];
        Checks.check(fall.spawned > 20 && fall.count() == fall.write(leaves, 0f, 0f), "every leaf left is within KEPT of the eye: " + fall.count() + " kept of " + fall.spawned);
        Checks.check(fall.vanished == 0, "and none vanished while it showed: " + fall.vanished);
    }

    private static void aJumpOfTheEyeLetsEveryLeafGo() {
        LeafFall fall = grove(4);
        run(fall, 3f, 1f / 60f, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN);
        run(fall, 3f, 1f / 60f, 0f, 0f, 0.5f, Seasons.SUMMER, 0.5f);
        Checks.check(fall.count() > 10, "leaves lie round the trees: " + fall.count());
        float[] leaves = new float[LeafFall.MOST * LeafFall.FLOATS];
        int written = fall.write(leaves, 0f, 0f), showing = 0;
        for (int i = 0; i < written; i++) {
            showing += leaves[i * LeafFall.FLOATS + LeafFall.SHOWN] > 0.5f ? 1 : 0;
        }
        fall.step(1f / 60f, 0f, 0f, 0.5f, Seasons.SUMMER, 0.5f, LeafFall.KEPT + 1f, 0f, OPEN);
        Checks.check(fall.count() == 0 && fall.vanished == showing && showing > 10, "an eye that jumps away lets every leaf go, and those that showed are counted as vanished: "
                + fall.count() + " left, " + fall.vanished + " of " + showing + " vanished");
    }

    /** A bungalow downwind: a leaf that meets its walls is caught there and fades, one over its roof goes on, and none vanishes. */
    private static void aLeafBlownAtABuildingIsCaughtAndFadesOrGoesOver() {
        LeafFall fall = new LeafFall();
        fall.beginTrees(0f, 0f);
        fall.offer(-0.5f, 0.5f, 0f, MAPLE, shedding(1f), true, LOW, HIGH, OUT);
        fall.endTrees();
        Flat bungalow = new Flat(3, 1);
        float[] leaves = new float[LeafFall.MOST * LeafFall.FLOATS];
        int over = 0;
        for (int round = 0; round < 1200; round++) {
            fall.step(1f / 30f, 12f, 0f, 0.6f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, bungalow);
            int count = fall.write(leaves, 0f, 0f);
            for (int i = 0; i < count; i++) {
                int at = i * LeafFall.FLOATS;
                                Checks.check(leaves[at + LeafFall.X] < 3f || leaves[at + LeafFall.UP] >= ViewCamera.LEVEL_HEIGHT,
                        "no leaf goes on inside the building: " + leaves[at + LeafFall.X] + " at " + leaves[at + LeafFall.UP]);
                over += leaves[at + LeafFall.X] > 6f ? 1 : 0;
            }
        }
        Checks.check(fall.spawned > 100 && over > 0, "leaves were let go towards the building and some went over it: " + fall.spawned + ", " + over);
        Checks.check(fall.vanished == 0, "none vanished in the air: " + fall.vanished);
    }

    /** On a hill a ground floor stands as high over the flat map as the hill: a leaf blown at it is caught, not let in as if it were upstairs. */
    private static void aGroundFloorOnAHillCatchesALeaf() {
        float hill = 1.5f * ViewCamera.LEVEL_HEIGHT;
        Checks.check(LeafFall.storey(hill + 0.5f, hill) == 0 && LeafFall.storey(hill + ViewCamera.LEVEL_HEIGHT + 0.1f, hill) == 1 && LeafFall.storey(hill - 0.1f, hill) == 0,
                "a storey is counted from the ground under it");
        LeafFall fall = new LeafFall();
        fall.beginTrees(0f, 0f);
        fall.offer(-0.5f, 0.5f, hill, MAPLE, shedding(1f), true, LOW, HIGH, OUT);
        fall.endTrees();
        Flat house = new Flat(3, 1, hill);
        float[] leaves = new float[LeafFall.MOST * LeafFall.FLOATS];
        for (int round = 0; round < 900; round++) {
            fall.step(1f / 30f, 12f, 0f, 0.6f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, house);
            int count = fall.write(leaves, 0f, 0f);
            for (int i = 0; i < count; i++) {
                int at = i * LeafFall.FLOATS;
                Checks.check(leaves[at + LeafFall.X] < 3f || leaves[at + LeafFall.UP] >= hill + ViewCamera.LEVEL_HEIGHT,
                        "no leaf goes on into the ground floor on the hill: " + leaves[at + LeafFall.X] + " at " + leaves[at + LeafFall.UP]);
            }
        }
        Checks.check(fall.spawned > 100, "leaves were let go towards the house: " + fall.spawned);
    }

    /**
     * A tree beside a two-storey house whose crown reaches over it: in still air no leaf lets
     * go inside its rooms, so none is ever seen within its walls, and some fall outside.
     */
    private static void noLeafLetsGoInsideAHouseACrownReachesOver() {
        LeafFall fall = new LeafFall();
        fall.beginTrees(0f, 0f);
        fall.offer(-0.5f, 0.5f, 0f, MAPLE, shedding(1f), true, LOW, HIGH, OUT);
        fall.endTrees();
        Flat house = new Flat(0, 3);
        float[] leaves = new float[LeafFall.MOST * LeafFall.FLOATS];
        for (int round = 0; round < 600; round++) {
            fall.step(1f / 30f, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, house);
            int count = fall.write(leaves, 0f, 0f);
            for (int i = 0; i < count; i++) {
                Checks.check(leaves[i * LeafFall.FLOATS + LeafFall.X] < 0f, "no leaf is in the house: " + leaves[i * LeafFall.FLOATS + LeafFall.X]);
            }
        }
        Checks.check(fall.spawned > 30, "leaves let go outside it: " + fall.spawned);
    }

    /**
     * In still air a leaf let go from a crown over a bungalow's roof falls straight down without
     * leaving its square: it is caught where it comes down into the storey under the roof.
     */
    private static void aLeafFallingOntoARoofIsCaughtAtIt() {
        LeafFall fall = new LeafFall();
        fall.beginTrees(0f, 0f);
        fall.offer(-0.5f, 0.5f, 0f, MAPLE, shedding(1f), true, ViewCamera.LEVEL_HEIGHT + 0.3f, HIGH, OUT);
        fall.endTrees();
        Flat bungalow = new Flat(0, 1);
        float[] leaves = new float[LeafFall.MOST * LeafFall.FLOATS];
        int overRoof = 0;
        for (int round = 0; round < 600; round++) {
            fall.step(1f / 30f, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, bungalow);
            int count = fall.write(leaves, 0f, 0f);
            for (int i = 0; i < count; i++) {
                int at = i * LeafFall.FLOATS;
                if (leaves[at + LeafFall.X] > 0f) {
                    overRoof++;
                    Checks.check(leaves[at + LeafFall.UP] >= ViewCamera.LEVEL_HEIGHT, "a leaf over the roof stays on top of it: " + leaves[at + LeafFall.UP]);
                }
            }
        }
        Checks.check(overRoof > 100, "leaves came down over the roof: " + overRoof);
    }

    /**
     * A tree uphill of a bungalow, its crown over the roof: a leaf let go over the roof is counted
     * in the storeys of the house's ground, not of the tree's, and is caught on top of the roof.
     */
    private static void aLeafFallingOntoARoofDownhillIsCaughtAtIt() {
        float uphill = 0.8f;
        LeafFall fall = new LeafFall();
        fall.beginTrees(0f, 0f);
        fall.offer(-0.5f, 0.5f, uphill, MAPLE, shedding(1f), true, ViewCamera.LEVEL_HEIGHT - uphill + 0.2f, HIGH, OUT);
        fall.endTrees();
        Terraced slope = new Terraced(0, 1, 0f, uphill);
        float[] leaves = new float[LeafFall.MOST * LeafFall.FLOATS];
        int overRoof = 0;
        for (int round = 0; round < 600; round++) {
            fall.step(1f / 30f, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, slope);
            int count = fall.write(leaves, 0f, 0f);
            for (int i = 0; i < count; i++) {
                int at = i * LeafFall.FLOATS;
                if (leaves[at + LeafFall.X] > 0f) {
                    overRoof++;
                    Checks.check(leaves[at + LeafFall.UP] >= ViewCamera.LEVEL_HEIGHT, "a leaf over the roof downhill stays on top of it: " + leaves[at + LeafFall.UP]);
                }
            }
        }
        Checks.check(overRoof > 100, "leaves came down over the roof: " + overRoof);
    }

    /** Far from the map's corner the trees are read, kept and weighed by how far they are from the eye, not from the corner. */
    private static void treesAreReadRoundTheEyeWhereverItIs() {
        float eyeX = 5000.5f, eyeY = 3000.5f;
        int hash = shedding(1f);
        LeafFall fall = new LeafFall();
        fall.beginTrees(eyeX, eyeY);
        fall.offer(eyeX - 30f, eyeY, 0f, MAPLE, hash, true, LOW, HIGH, OUT);
        fall.offer(eyeX + 2f, eyeY, 0f, MAPLE, hash, true, LOW, HIGH, OUT);
        fall.endTrees();
        for (float t = 0f; t < 120f; t += 1f / 30f) {
            fall.step(1f / 30f, 0f, 0f, 0.1f, Seasons.AUTUMN, PEAK_AUTUMN, eyeX, eyeY, OPEN);
        }
        Checks.check(fall.released(0) > 2 * fall.released(1) && fall.released(1) > 0, "the tree beside the eye is kept first and sheds more than the far one: "
                + fall.released(0) + " against " + fall.released(1));
    }

    private static void nothingMovesWhileThePictureWaits() {
        LeafFall fall = grove(10);
        fall.step(LeafFall.STEP_MOST, 1f, 0f, 0.3f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, OPEN);
        float[][] before = sorted(fall);
        long let = fall.spawned;
        for (int i = 0; i < 100; i++) {
            fall.step(0f, 1f, 0f, 0.3f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, OPEN);
        }
        Checks.check(Arrays.deepEquals(before, sorted(fall)) && fall.spawned == let, "paused, nothing moves and nothing more falls");
    }
}

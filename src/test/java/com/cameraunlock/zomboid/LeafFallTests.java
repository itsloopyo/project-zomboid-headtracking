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
        Checks.run("theWindTakesMoreLeavesAndCarriesThemFurther", LeafFallTests::theWindTakesMoreLeavesAndCarriesThemFurther);
        Checks.run("leavesLetGoFromTheCrownTheColourItTurned", LeafFallTests::leavesLetGoFromTheCrownTheColourItTurned);
        Checks.run("aLeafMovesTheSameAtAnyFrameRate", LeafFallTests::aLeafMovesTheSameAtAnyFrameRate);
        Checks.run("leavesLandLieAndFade", LeafFallTests::leavesLandLieAndFade);
        Checks.run("noMoreLeavesThanThereIsRoomFor", LeafFallTests::noMoreLeavesThanThereIsRoomFor);
        Checks.run("aLeafBlownIntoABuildingIsGone", LeafFallTests::aLeafBlownIntoABuildingIsGone);
        Checks.run("nothingMovesWhileThePictureWaits", LeafFallTests::nothingMovesWhileThePictureWaits);
    }

    private static final float PEAK_AUTUMN = 0.45f;

    /** Flat ground at height 0 with walls on every square from x = wallFrom on. */
    private record Flat(int wallFrom) implements LeafFall.Ground {
        @Override
        public float height(float x, float y) {
            return 0f;
        }

        @Override
        public boolean walled(int x, int y) {
            return x >= wallFrom;
        }
    }

    private static final Flat OPEN = new Flat(Integer.MAX_VALUE);

    /** A sim whose trees stand on a grid round (0, 0), count of them, all red maples. */
    private static LeafFall grove(int count) {
        LeafFall fall = new LeafFall();
        float[] xs = new float[count], ys = new float[count], grounds = new float[count];
        int[] hashes = new int[count];
        byte[] hues = new byte[count];
        boolean[] jumbos = new boolean[count], inLeaf = new boolean[count];
        int side = (int) Math.ceil(Math.sqrt(count));
        for (int i = 0; i < count; i++) {
            int x = (i % side) * 3 - side, y = (i / side) * 3 - side;
            xs[i] = x + 0.5f;
            ys[i] = y + 0.5f;
            hashes[i] = Seasons.treeHash(x, y);
            hues[i] = (byte) Flora.autumnHue("e_redmaple_1_3", hashes[i]);
            inLeaf[i] = true;
        }
        fall.trees(count, xs, ys, grounds, hashes, hues, jumbos, inLeaf);
        return fall;
    }

    private static void run(LeafFall fall, float seconds, float step, float airX, float airY, float intensity, int season, float progression) {
        for (float t = 0f; t < seconds - 1e-4f; t += step) {
            fall.step(step, airX, airY, intensity, season, progression, 0f, 0f, OPEN);
        }
    }

    private static void onlyBroadLeavedTreesShed() {
        Checks.check(LeafFall.sheds("e_redmaple_1_3") && LeafFall.sheds("e_americanlindenJUMBO_1_0") && LeafFall.sheds("e_riverbirch_1_2"), "maple, linden and birch shed");
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
                Checks.check(crown == Seasons.leafOn(Seasons.AUTUMN, Seasons.stepped(Seasons.AUTUMN, p), hash), "the crown's leaf is the one PlantBuilder builds it with");
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
        LeafFall fall = new LeafFall();
        fall.trees(1, new float[] {0.5f}, new float[] {0.5f}, new float[1], new int[] {Seasons.treeHash(0, 0)}, new byte[1], new boolean[1], new boolean[] {false});
        run(fall, 30f, 1f / 60f, 1f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN);
        Checks.check(fall.spawned == 0, "a tree the game has bare lets no leaf go in a gale at the height of autumn: " + fall.spawned);
    }

    private static void theWindTakesMoreLeavesAndCarriesThemFurther() {
        Checks.check(LeafFall.letGo(0f) < LeafFall.letGo(0.1f) && LeafFall.letGo(0.1f) < LeafFall.letGo(0.4f) && LeafFall.letGo(0.6f) >= 3f, "the wind lets more go");
        Checks.check(LeafFall.carried(0.02f) < LeafFall.carried(0.3f), "a strong wind carries a leaf more than still air");
        Checks.check(LeafFall.nearness(0f) == 1f && LeafFall.nearness(LeafFall.NEAR) == 1f && LeafFall.nearness(20f) < 1f
                && LeafFall.nearness(LeafFall.REACH) > 0.25f && LeafFall.nearness(LeafFall.REACH) < LeafFall.nearness(20f), "a tree near the eye sheds all it would, one further off a share");
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
        LeafFall fall = grove(1);
        float[] leaves = new float[LeafFall.MOST * LeafFall.FLOATS];
        int hash = Seasons.treeHash(-1, -1);
        int seen = 0;
        for (int round = 0; round < 400; round++) {
            fall.step(0.25f, 0f, 0f, 0.5f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, OPEN);
            // A leaf fades in over its first moments: a little later it shows.
            fall.step(0.3f, 0f, 0f, 0.5f, Seasons.SUMMER, 0.5f, 0f, 0f, OPEN);
            int count = fall.write(leaves, 0f, 0f);
            for (int i = 0; i < count; i++) {
                int at = i * LeafFall.FLOATS;
                if (leaves[at + LeafFall.UP] < 1.2f) {
                    continue;
                }
                seen++;
                float out = (float) Math.hypot(leaves[at + LeafFall.X] + 0.5f, leaves[at + LeafFall.Y] + 0.5f);
                Checks.check(out < 2f, "a leaf in the air near its crown is within reach of its tree: " + out);
                Checks.check(leaves[at + LeafFall.UP] < 5.5f, "and no higher than the crown");
                Checks.check(leaves[at + LeafFall.HUE] == Flora.autumnHue("e_redmaple_1_3", hash), "a leaf is its tree's colour");
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
        Checks.check(fall.count() == 0, "every landed leaf has faded: " + fall.count());
    }

    private static void noMoreLeavesThanThereIsRoomFor() {
        LeafFall fall = grove(LeafFall.MOST_TREES);
        for (int round = 0; round < 600; round++) {
            fall.step(1f / 30f, 20f, 5f, 0.9f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, OPEN);
            Checks.check(fall.count() <= LeafFall.MOST, "no more leaves than there is room for");
        }
        Checks.check(fall.count() > LeafFall.MOST / 2, "a wood in a gale fills it: " + fall.count());
    }

    private static void aLeafBlownIntoABuildingIsGone() {
        LeafFall fall = grove(1);
        Flat walled = new Flat(3);
        float[] leaves = new float[LeafFall.MOST * LeafFall.FLOATS];
        for (int round = 0; round < 1200; round++) {
            fall.step(1f / 30f, 12f, 0f, 0.6f, Seasons.AUTUMN, PEAK_AUTUMN, 0f, 0f, walled);
            int count = fall.write(leaves, 0f, 0f);
            for (int i = 0; i < count; i++) {
                int at = i * LeafFall.FLOATS;
                // Its middle's drift is out of the walls; its swing may take its drawn middle a little past.
                Checks.check(leaves[at + LeafFall.X] < 3.5f, "no leaf goes on inside a building: " + leaves[at + LeafFall.X]);
            }
        }
        Checks.check(fall.spawned > 100, "leaves were let go towards the building: " + fall.spawned);
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

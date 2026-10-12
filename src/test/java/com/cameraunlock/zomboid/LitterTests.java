package com.cameraunlock.zomboid;

/** Tests of the litter under trees: what each tree drops, how far it lies, and what the season shows of it. */
final class LitterTests {
    private static final int TILES = Terrain.CHUNK_TILES, SPAN = TILES + 2 * Litter.RING, ACROSS = TILES + 1;

    private LitterTests() {}

    static void run() {
        Checks.run("evergreensDropNeedlesAndTheRestLeaf", LitterTests::evergreensDropNeedlesAndTheRestLeaf);
        Checks.run("chunksThatShareACornerLayTheSameLitterThere", LitterTests::chunksThatShareACornerLayTheSameLitterThere);
        Checks.run("leafLiesDownwindAndNeedlesUnderTheTree", LitterTests::leafLiesDownwindAndNeedlesUnderTheTree);
        Checks.run("theLeafOnASquareIsOfTheTreeThatDropsMostOnIt", LitterTests::theLeafOnASquareIsOfTheTreeThatDropsMostOnIt);
        Checks.run("leafSpreadsOutThroughAutumnAndShrinksBackBySummer", LitterTests::leafSpreadsOutThroughAutumnAndShrinksBackBySummer);
        Checks.run("freshLeafLiesInAutumnAndMatsDarkInWinter", LitterTests::freshLeafLiesInAutumnAndMatsDarkInWinter);
        Checks.run("gutterKeepsLeafAndTheMiddleOfTheRoadLittle", LitterTests::gutterKeepsLeafAndTheMiddleOfTheRoadLittle);
        Checks.run("fallenLeavesShowInTheSameOrderAndMoreWhereMoreLies", LitterTests::fallenLeavesShowInTheSameOrderAndMoreWhereMoreLies);
        Checks.run("fallenLeavesAreTheColourOfTheirTree", LitterTests::fallenLeavesAreTheColourOfTheirTree);
        Checks.run("fieldsOfADenseWoodAreQuick", LitterTests::fieldsOfADenseWoodAreQuick);
    }

    private static void evergreensDropNeedlesAndTheRestLeaf() {
        Checks.check(Litter.kindOf("e_americanholly_1_4") == Litter.HOLLY, "holly drops holly leaves");
        Checks.check(Litter.kindOf("e_canadianhemlock_1_2") == Litter.PINE && Litter.kindOf("e_virginiapine_1_0") == Litter.PINE, "hemlock and pine drop needles");
        Checks.check(Litter.kindOf("e_redmaple_1_3") == Litter.RED && Litter.kindOf("e_dogwood_1_1") == Litter.RED, "a maple and a dogwood turn red");
        Checks.check(Litter.kindOf("e_riverbirch_1_0") == Litter.YELLOW && Litter.kindOf("e_americanlinden_1_9") == Litter.YELLOW, "a birch and a linden turn yellow");
        Checks.check(Litter.kindOf("jumbo_tree_01_5") == Litter.OAK && Litter.kindOf(null) == Litter.OAK, "any other tree drops brown leaf");
        for (byte kind = Litter.OAK; kind <= Litter.HOLLY; kind++) {
            Checks.check(Litter.evergreen(kind) == (kind == Litter.PINE || kind == Litter.HOLLY), "only holly, hemlock and pine are evergreen");
        }
    }

    private static void chunksThatShareACornerLayTheSameLitterThere() {
        float[] westLeaves = new float[ACROSS * ACROSS], westNeedles = new float[ACROSS * ACROSS], eastLeaves = new float[ACROSS * ACROSS], eastNeedles = new float[ACROSS * ACROSS];
        float[] southLeaves = new float[ACROSS * ACROSS], southNeedles = new float[ACROSS * ACROSS];
        fields(Tests.woodAround(0, 0, SPAN, 4), westLeaves, westNeedles);
        fields(Tests.woodAround(TILES, 0, SPAN, 4), eastLeaves, eastNeedles);
        fields(Tests.woodAround(0, TILES, SPAN, 4), southLeaves, southNeedles);
        boolean some = false;
        for (int j = 0; j < ACROSS; j++) {
            Checks.check(westLeaves[TILES * ACROSS + j] == eastLeaves[j] && westNeedles[TILES * ACROSS + j] == eastNeedles[j], "a corner on the edge two chunks share has the same litter from either");
        }
        for (int i = 0; i < ACROSS; i++) {
            Checks.check(westLeaves[i * ACROSS + TILES] == southLeaves[i * ACROSS] && westNeedles[i * ACROSS + TILES] == southNeedles[i * ACROSS], "and on the edge to the south");
        }
        for (int at = 0; at < westLeaves.length; at++) {
            Checks.check(westLeaves[at] >= 0f && westLeaves[at] <= 1f && westNeedles[at] >= 0f && westNeedles[at] <= 1f, "litter is no deeper than all the way");
            some |= westLeaves[at] > 0.05f && westNeedles[at] > 0.05f;
        }
        Checks.check(some, "a mixed wood has leaf and needles together");
        fields(new byte[SPAN * SPAN], westLeaves, westNeedles);
        for (int at = 0; at < westLeaves.length; at++) {
            Checks.check(westLeaves[at] == 0f && westNeedles[at] == 0f, "no tree, no litter");
        }
    }

    private static void leafLiesDownwindAndNeedlesUnderTheTree() {
        float[] leaves = new float[ACROSS * ACROSS], needles = new float[ACROSS * ACROSS];
        // One tree on the chunk's square (4, 4): its foot is the corner (4.5, 4.5) of no square.
        fields(lone(Litter.OAK), leaves, needles);
        float downwind = leaves[6 * ACROSS + 3], upwind = leaves[3 * ACROSS + 6];
        Checks.check(downwind > 2f * upwind + 0.05f, "a tree's leaf lies further the way the wind blows, to +x and -y: " + downwind + " against " + upwind);
        float sum = 0f;
        for (float needle : needles) {
            sum += needle;
        }
        Checks.check(sum == 0f, "a broad-leaved tree drops no needles");
        fields(lone(Litter.PINE), leaves, needles);
        Checks.check(needles[4 * ACROSS + 4] > 0.5f && needles[4 * ACROSS + 4] == needles[5 * ACROSS + 5], "a pine's needles lie under it, the same all round");
        Checks.check(needles[0] == 0f && needles[8 * ACROSS + 8] < 0.01f, "and not far from it");
        for (float leaf : leaves) {
            Checks.check(leaf == 0f, "a pine drops no leaf");
        }
    }

    private static void theLeafOnASquareIsOfTheTreeThatDropsMostOnIt() {
        byte[] trees = new byte[SPAN * SPAN];
        trees[(1 + Litter.RING) * SPAN + 1 + Litter.RING] = Litter.RED;
        trees[(6 + Litter.RING) * SPAN + 6 + Litter.RING] = Litter.YELLOW;
        trees[(6 + Litter.RING) * SPAN + 1 + Litter.RING] = Litter.HOLLY;
        float[] leaves = new float[ACROSS * ACROSS], needles = new float[ACROSS * ACROSS];
        byte[] leafKinds = new byte[TILES * TILES], needleKinds = new byte[TILES * TILES];
        Litter.fields(trees, TILES, leaves, needles, leafKinds, needleKinds);
        Checks.check(leafKinds[1 * TILES + 1] == Litter.RED && leafKinds[6 * TILES + 6] == Litter.YELLOW, "the leaf under a tree is its own");
        Checks.check(needleKinds[6 * TILES + 1] == Litter.HOLLY, "and so are the needles, or a holly's leaves");
    }

    private static void leafSpreadsOutThroughAutumnAndShrinksBackBySummer() {
        float summer = Litter.spread(Seasons.litter(Seasons.SUMMER, 0.5f));
        Checks.check(near(summer, Litter.SPREAD_SUMMER), "summer spreads the litter as SPREAD_SUMMER says, " + summer);
        Checks.check(near(Litter.spread(Seasons.litter(Seasons.AUTUMN, 1f)), Litter.SPREAD_MOST), "the end of autumn spreads it furthest");
        float last = 0f;
        for (int day = 0; day <= 100; day++) {
            float spread = Litter.spread(Seasons.litter(Seasons.AUTUMN, day / 100f));
            Checks.check(spread >= last - 1e-6f, "through autumn the leaf only spreads further, day " + day);
            last = spread;
        }
        Checks.check(Litter.spread(Seasons.litter(Seasons.WINTER, 0.5f)) == Litter.SPREAD_MOST, "and lies as far through winter");
        Checks.check(Litter.spread(Seasons.litter(Seasons.SPRING, 1f)) < Litter.SPREAD_SUMMER + 0.01f, "and has rotted back by summer");
        // How many corners of one tree's leaf show, in summer and at the end of autumn.
        float[] leaves = new float[ACROSS * ACROSS], needles = new float[ACROSS * ACROSS];
        fields(lone(Litter.OAK), leaves, needles);
        int inSummer = 0, inAutumn = 0;
        for (float leaf : leaves) {
            inSummer += leaf * summer > 0.5f ? 1 : 0;
            inAutumn += leaf * Litter.SPREAD_MOST > 0.5f ? 1 : 0;
        }
        Checks.check(inSummer <= 2 && inAutumn >= 12, "one tree's leaf is a patch at its foot in summer and spreads well out in autumn: " + inSummer + " and " + inAutumn + " corners");
        // Where the leaf of a wood lies all the way, it shows all year.
        fields(Tests.woodAround(0, 0, SPAN, 1), leaves, needles);
        int full = 0;
        for (float leaf : leaves) {
            full += leaf * summer > 0.62f || leaf == 0f ? 1 : 0;
        }
        Checks.check(full > ACROSS * ACROSS * 0.9f, "a wood's floor is leaf in summer too");
    }

    private static void freshLeafLiesInAutumnAndMatsDarkInWinter() {
        for (int season : new int[] {Seasons.SPRING, Seasons.SUMMER, Seasons.SUMMER2, Seasons.WINTER}) {
            Checks.check(Litter.fresh(season, 0.5f) == 0f, "no fresh leaf outside autumn, season " + season);
        }
        Checks.check(Litter.fresh(Seasons.AUTUMN, 0f) < 0.01f && Litter.fresh(Seasons.AUTUMN, 0.5f) > 0.9f, "fresh leaf comes down as autumn goes on");
        Checks.check(Litter.fresh(Seasons.AUTUMN, 1f) > 0.4f && Litter.fresh(Seasons.AUTUMN, 1f) < 0.6f, "and has half faded by its end");
        Checks.check(Litter.dark(Seasons.WINTER, 1f) == 1f && Litter.dark(Seasons.WINTER, 0f) == 0f, "litter mats dark through the start of winter");
        Checks.check(Litter.dark(Seasons.AUTUMN, 0.8f) == 0f && Litter.dark(Seasons.AUTUMN, 0f) == Litter.dark(Seasons.SUMMER2, 0.5f), "autumn's leaf covers summer's darker litter");
        Checks.check(near(Litter.dark(Seasons.SPRING, 1f), Litter.dark(Seasons.SUMMER, 0.5f)), "spring rots winter's back to summer's");
    }

    private static void gutterKeepsLeafAndTheMiddleOfTheRoadLittle() {
        Checks.check(Litter.gutter(-0.5f) == 1f && Litter.gutter(0f) == 1f && Litter.gutter(0.1f) == 1f, "the land and the gutter keep all of it");
        Checks.check(near(Litter.gutter(Kerbs.NEAR), Litter.ROAD_MIDDLE), "the middle of the road keeps a little");
        float last = 1f;
        for (int i = 0; i <= 100; i++) {
            float kept = Litter.gutter(i / 100f);
            Checks.check(kept <= last + 1e-6f, "the further from the kerb the less");
            last = kept;
        }
        Checks.check(Litter.keeps(Terrain.NATURAL, true) == 1f && Litter.keeps(Terrain.STREET, true) == 1f && Litter.keeps(Terrain.MADE, true) == Litter.MADE_KEEPS,
                "open ground, a street and a path keep it");
        Checks.check(Litter.keeps(Terrain.NATURAL, false) == 0f && Litter.keeps(Terrain.WATER, true) == 0f && Litter.keeps(Terrain.UNKNOWN, true) == 0f,
                "a roof, water and what is not known keep none");
    }

    private static void fallenLeavesShowInTheSameOrderAndMoreWhereMoreLies() {
        int[] shownSummer = new int[2], shownAutumn = new int[2];
        float[] covers = {0.25f, 1f};
        for (int x = 0; x < 60; x++) {
            for (int y = 0; y < 60; y++) {
                for (int card = 0; card < Litter.CARDS_MOST; card++) {
                    Checks.check(Litter.cardStep(x, y, card, 0f) == 0, "no leaf where none lies");
                    int last = Integer.MAX_VALUE;
                    for (int c = 0; c < covers.length; c++) {
                        int step = Litter.cardStep(x, y, card, covers[c]);
                        Checks.check(step == Litter.cardStep(x, y, card, covers[c]), "a leaf shows from the same step every time");
                        Checks.check(step >= 0 && step <= Litter.STEPS, "and from one of the steps");
                        if (last != 0 && last != Integer.MAX_VALUE) {
                            Checks.check(step != 0 && step <= last, "where more lies a leaf shows no later");
                        }
                        last = step;
                        shownSummer[c] += step != 0 && step <= Litter.stepOf(Litter.cardSpread(Seasons.SUMMER, 0.5f)) ? 1 : 0;
                        shownAutumn[c] += step != 0 && step <= Litter.stepOf(Litter.cardSpread(Seasons.AUTUMN, 1f)) ? 1 : 0;
                    }
                }
            }
        }
        int squares = 60 * 60;
        Checks.check(shownAutumn[1] == squares * Litter.CARDS_MOST, "every leaf of a wood shows at the end of autumn");
        Checks.check(shownSummer[1] > squares && shownSummer[1] < squares * 3, "a wood shows a leaf or two a square in summer: " + shownSummer[1]);
        Checks.check(shownSummer[0] == 0 && shownAutumn[0] > squares, "thin leaf shows none in summer and some in autumn: " + shownSummer[0] + ", " + shownAutumn[0]);
        Checks.check(Litter.cardSpread(Seasons.WINTER, 1f) < Litter.cardSpread(Seasons.AUTUMN, 1f), "fewer stand proud once winter has matted them");
    }

    private static void fallenLeavesAreTheColourOfTheirTree() {
        int red = 0, yellow = 0, brown = 0;
        for (int i = 0; i < 1000; i++) {
            float chance = i / 1000f;
            red += Litter.leafPick(Litter.RED, chance) == Flora.FALLEN_RED ? 1 : 0;
            yellow += Litter.leafPick(Litter.YELLOW, chance) == Flora.FALLEN_YELLOW ? 1 : 0;
            brown += Litter.leafPick(Litter.OAK, chance) == Flora.FALLEN_BROWN ? 1 : 0;
            Checks.check(Litter.leafPick(Litter.OAK, chance) != Flora.FALLEN_RED, "an oak's leaf is never red");
        }
        Checks.check(red > 500 && yellow > 500 && brown > 400, "most of a tree's leaves are its own colour");
    }

    private static void fieldsOfADenseWoodAreQuick() {
        byte[] trees = Tests.woodAround(0, 0, SPAN, 2);
        float[] leaves = new float[ACROSS * ACROSS], needles = new float[ACROSS * ACROSS];
        for (int i = 0; i < 200; i++) {
            fields(trees, leaves, needles);
        }
        long began = System.nanoTime();
        int times = 500;
        for (int i = 0; i < times; i++) {
            fields(trees, leaves, needles);
        }
        double each = (System.nanoTime() - began) / 1e3 / times;
        float[] wooded = new float[ACROSS * ACROSS];
        for (int i = 0; i < 200; i++) {
            Flora.wooded(trees, TILES, wooded);
        }
        began = System.nanoTime();
        for (int i = 0; i < times; i++) {
            Flora.wooded(trees, TILES, wooded);
        }
        double woods = (System.nanoTime() - began) / 1e3 / times;
        System.out.printf(java.util.Locale.ROOT, "  litter fields of a wood with a tree on every other square: %.1f us a chunk (how wooded it is: %.1f us)%n", each, woods);
        Checks.check(each < 400.0, "the litter of a dense wood is worked out in well under a millisecond a chunk: " + each + " us");
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static byte[] lone(byte kind) {
        byte[] trees = new byte[SPAN * SPAN];
        trees[(4 + Litter.RING) * SPAN + 4 + Litter.RING] = kind;
        return trees;
    }

    private static void fields(byte[] trees, float[] leaves, float[] needles) {
        Litter.fields(trees, TILES, leaves, needles, new byte[TILES * TILES], new byte[TILES * TILES]);
    }
}

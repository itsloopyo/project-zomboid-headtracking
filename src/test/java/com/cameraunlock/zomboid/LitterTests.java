package com.cameraunlock.zomboid;

/** Tests of the litter under trees: what each tree drops, how far it lies, and what the season shows of it. */
final class LitterTests {
    private static final int TILES = Terrain.CHUNK_TILES, SPAN = TILES + 2 * Litter.RING, ACROSS = TILES + 1;

    private LitterTests() {}

    static void run() {
        Checks.run("theLeafOnTheGroundIsTheLeafOfItsCrown", LitterTests::theLeafOnTheGroundIsTheLeafOfItsCrown);
        Checks.run("chunksThatShareACornerLayTheSameLitterThere", LitterTests::chunksThatShareACornerLayTheSameLitterThere);
        Checks.run("leafLiesDownwindAndNeedlesUnderTheTree", LitterTests::leafLiesDownwindAndNeedlesUnderTheTree);
        Checks.run("theLeafOnASquareIsOfTheTreeThatDropsMostOnIt", LitterTests::theLeafOnASquareIsOfTheTreeThatDropsMostOnIt);
        Checks.run("leafSpreadsOutThroughAutumnAndShrinksBackBySummer", LitterTests::leafSpreadsOutThroughAutumnAndShrinksBackBySummer);
        Checks.run("freshLeafLiesInAutumnAndMatsDarkInWinter", LitterTests::freshLeafLiesInAutumnAndMatsDarkInWinter);
        Checks.run("nothingTheSeasonShowsJumpsBetweenSeasons", LitterTests::nothingTheSeasonShowsJumpsBetweenSeasons);
        Checks.run("theShaderCountsLitterAsTheJavaDoes", LitterTests::theShaderCountsLitterAsTheJavaDoes);
        Checks.run("aCornerKeepsWhatTheLeastOfItsSquaresKeeps", LitterTests::aCornerKeepsWhatTheLeastOfItsSquaresKeeps);
        Checks.run("litterAKerbCrossesIsLaidOnBothSidesAndIsAllThere", LitterTests::litterAKerbCrossesIsLaidOnBothSidesAndIsAllThere);
        Checks.run("gutterKeepsLeafAndTheMiddleOfTheRoadLittle", LitterTests::gutterKeepsLeafAndTheMiddleOfTheRoadLittle);
        Checks.run("fallenLeavesShowInTheSameOrderAndMoreWhereMoreLies", LitterTests::fallenLeavesShowInTheSameOrderAndMoreWhereMoreLies);
        Checks.run("fallenLeavesAreTheColourOfTheirTree", LitterTests::fallenLeavesAreTheColourOfTheirTree);
        Checks.run("fieldsOfADenseWoodAreQuick", LitterTests::fieldsOfADenseWoodAreQuick);
    }

    private static void theLeafOnTheGroundIsTheLeafOfItsCrown() {
        String[] names = {"e_americanholly_1_4", "e_canadianhemlock_1_2", "e_virginiapine_1_0", "e_redmaple_1_3", "e_redmapleJUMBOXL_1_2", "e_dogwood_1_1",
            "e_cockspurhawthorn_1_0", "e_riverbirch_1_0", "e_americanlinden_1_9", "e_carolinasilverbellJUMBOXL_1_0", "e_yellowwood_1_2", "e_easternredbud_1_1",
            "jumbo_tree_01_5", "vegetation_trees_01_8"};
        for (String name : names) {
            for (int hash = 0; hash < 4096; hash += 97) {
                byte kind = Litter.kindOf(name, hash);
                Checks.check(kind == Litter.kindOf(name, Flora.otherHue(hash)), name + " drops what a build keeps for its sprite and its square's colour");
                Checks.check(Litter.evergreen(kind) == Flora.evergreen(name), name + " drops needles exactly when Flora makes it evergreen");
                if (!Flora.evergreen(name)) {
                    Checks.check(kind - Litter.LEAF == Flora.autumnHue(name, hash), name + " drops leaf of the colour its crown turns");
                }
            }
        }
        Checks.check(Litter.kindOf("e_americanholly_1_4", 0) == Litter.HOLLY && Litter.kindOf("e_virginiapine_1_0", 0) == Litter.PINE, "holly drops its leaves, a pine its needles");
        Checks.check(Litter.kindOf(null, 0) == Litter.LEAF + Flora.autumnHue("", 0), "a tree with no name drops leaf as an unnamed crown turns");
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
        fields(lone(Litter.LEAF), leaves, needles);
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
        byte red = (byte) (Litter.LEAF + Flora.SCARLET_LEAF), yellow = (byte) (Litter.LEAF + Flora.YELLOW_LEAF);
        trees[(1 + Litter.RING) * SPAN + 1 + Litter.RING] = red;
        trees[(6 + Litter.RING) * SPAN + 6 + Litter.RING] = yellow;
        trees[(6 + Litter.RING) * SPAN + 1 + Litter.RING] = Litter.HOLLY;
        float[] leaves = new float[ACROSS * ACROSS], needles = new float[ACROSS * ACROSS];
        byte[] leafKinds = new byte[TILES * TILES], needleKinds = new byte[TILES * TILES];
        Litter.fields(trees, TILES, leaves, needles, leafKinds, needleKinds);
        Checks.check(leafKinds[1 * TILES + 1] == red && leafKinds[6 * TILES + 6] == yellow, "the leaf under a tree is its own");
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
        fields(lone(Litter.LEAF), leaves, needles);
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
        Checks.check(Litter.fresh(Seasons.AUTUMN, 0.85f) > 0.3f && Litter.fresh(Seasons.AUTUMN, 1f) == 0f, "and has faded by its end");
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
        for (int hue = 0; hue < Flora.HUES; hue++) {
            byte kind = (byte) (Litter.LEAF + hue);
            int own = 0;
            for (int i = 0; i < 1000; i++) {
                int pick = Litter.leafPick(kind, i / 1000f);
                Checks.check(pick == Flora.FALLEN_HUE + hue || pick == Flora.FALLEN_BROWN, "a fallen leaf is its tree's autumn colour or brown");
                own += pick == Flora.FALLEN_HUE + hue ? 1 : 0;
            }
            Checks.check(Math.abs(own - 1000 * Litter.OWN_SHARE) <= 1, "OWN_SHARE of them are its own colour: " + own);
        }
        Checks.check(Flora.FALLEN_HUE + Flora.HUES - 1 < 2 * 8, "every fallen leaf's pick fits the card's coordinate");
    }

    private static void nothingTheSeasonShowsJumpsBetweenSeasons() {
        int[] year = {Seasons.SPRING, Seasons.SUMMER, Seasons.SUMMER2, Seasons.AUTUMN, Seasons.WINTER};
        for (int i = 0; i < year.length; i++) {
            int from = year[i], to = year[(i + 1) % year.length];
            float[] end = channels(from, 1f), start = channels(to, 0f);
            for (int c = 0; c < end.length; c++) {
                Checks.check(Math.abs(end[c] - start[c]) < 1e-4f, "channel " + c + " runs on from season " + from + " into season " + to + ": " + end[c] + " then " + start[c]);
            }
            float[] last = channels(from, 0f);
            for (int day = 1; day <= 200; day++) {
                float[] here = channels(from, day / 200f);
                for (int c = 0; c < here.length; c++) {
                    Checks.check(Math.abs(here[c] - last[c]) < 0.05f, "channel " + c + " moves smoothly through season " + from + ", day " + day);
                }
                last = here;
            }
        }
    }

    /** What the world shader is told of the season, as Pipeline tells it. */
    private static float[] channels(int season, float progression) {
        return new float[] {Litter.spread(Seasons.litter(season, progression)), Litter.fresh(season, progression), Litter.dark(season, progression),
            Litter.cardSpread(season, progression)};
    }

    private static void theShaderCountsLitterAsTheJavaDoes() {
        String glsl = Litter.GLSL;
        Checks.check(constant(glsl, "LITTER_SPREAD_MOST") == Litter.SPREAD_MOST && constant(glsl, "LITTER_STEPS") == Litter.STEPS, "the shader's spread is Litter's");
        Checks.check(constant(glsl, "LITTER_KIND_STEP") == Flora.LITTER_KIND_STEP && constant(glsl, "LITTER_LEAF") == Litter.LEAF, "it reads a litter vertex's kind as Flora lays it");
        Checks.check(constant(glsl, "LITTER_OWN_SHARE") == Litter.OWN_SHARE && constant(glsl, "LITTER_FALLEN_DIM") == Litter.FALLEN_DIM, "it colours fallen leaf as leafPick picks it");
        Checks.check(constant(glsl, "LEAF_BROWN") == Flora.FALLEN_BROWN && constant(glsl, "LEAF_TWIG") == Flora.TWIG && constant(glsl, "LEAF_CONE") == Flora.CONE
                && constant(glsl, "LEAF_HUE") == Flora.FALLEN_HUE, "and knows a card's leaf by Flora's numbers");
        String main = PipelineShaders.WORLD_FRAGMENT_MAIN;
        Checks.check(main.contains("floor(vUv.x / LITTER_KIND_STEP)") && main.contains("abs(pick - LEAF_TWIG)") && main.contains("abs(pick - LEAF_CONE)")
                && main.contains("autumnHue(pick - LEAF_HUE)"), "the world's main takes those numbers by name");
    }

    /** The value a shader gives a float constant of that name, from its line "const float NAME = value;". */
    private static float constant(String glsl, String name) {
        String line = "const float " + name + " = ";
        int at = glsl.indexOf(line);
        Checks.check(at >= 0, "the shader has " + name);
        return at < 0 ? Float.NaN : Float.parseFloat(glsl.substring(at + line.length(), glsl.indexOf(';', at)));
    }

    private static void aCornerKeepsWhatTheLeastOfItsSquaresKeeps() {
        int span = WorldScene.LIGHT_SPAN;
        byte[] kinds = new byte[span * span], open = new byte[span * span];
        java.util.Arrays.fill(kinds, Terrain.NATURAL);
        java.util.Arrays.fill(open, (byte) 1);
        Checks.check(PlantBuilder.cornerKeeps(kinds, open, 3, 3) == 1f, "a corner in the open keeps all of it");
        kinds[4 * span + 4] = Terrain.MADE;
        Checks.check(PlantBuilder.cornerKeeps(kinds, open, 3, 3) == Litter.MADE_KEEPS && PlantBuilder.cornerKeeps(kinds, open, 4, 4) == Litter.MADE_KEEPS
                && PlantBuilder.cornerKeeps(kinds, open, 3, 4) == Litter.MADE_KEEPS && PlantBuilder.cornerKeeps(kinds, open, 4, 3) == Litter.MADE_KEEPS,
                "each of the four corners round a path keeps as much as the path does");
        Checks.check(PlantBuilder.cornerKeeps(kinds, open, 5, 5) == 1f, "and a corner clear of it all");
        open[6 * span + 6] = 0;
        Checks.check(PlantBuilder.cornerKeeps(kinds, open, 5, 5) == 0f, "a corner touching a roofed square keeps none");
        kinds[2 * span + 2] = Terrain.WATER;
        Checks.check(PlantBuilder.cornerKeeps(kinds, open, 1, 1) == 0f, "nor one touching water");
    }

    private static void litterAKerbCrossesIsLaidOnBothSidesAndIsAllThere() {
        // A kerb slanting across the chunk: the field is the street's distance from the line, the street to its north east.
        float[] kerbs = new float[Kerbs.POINTS * Kerbs.POINTS];
        for (int p = 0; p < Kerbs.POINTS; p++) {
            for (int q = 0; q < Kerbs.POINTS; q++) {
                float x = (float) p / Kerbs.FINE, y = (float) q / Kerbs.FINE;
                kerbs[p * Kerbs.POINTS + q] = Math.clamp((x - 0.6f * y - 2.3f) / (float) Math.hypot(1, 0.6), -Kerbs.NEAR, Kerbs.NEAR);
            }
        }
        Checks.check(PlantBuilder.nearKerb(kerbs, 2, 0) && PlantBuilder.nearKerb(kerbs, 3, 2), "the squares the kerb runs through are near it");
        Checks.check(!PlantBuilder.nearKerb(kerbs, 0, 4), "land well back from the kerb is not");
        Checks.check(!PlantBuilder.nearKerb(kerbs, 7, 0), "nor street well out from it");
        int pieces = PlantBuilder.LITTER_PIECES, crossed = 0;
        float[] a = new float[4], b = new float[4], kerb = new float[4], outA = new float[8], outB = new float[8], outKerb = new float[8];
        for (int p = 0; p < pieces; p++) {
            for (int q = 0; q < pieces; q++) {
                float size = 1f / pieces;
                for (int corner = 0; corner < 4; corner++) {
                    a[corner] = (float) p / pieces + (corner == 1 || corner == 2 ? size : 0f);
                    b[corner] = (float) q / pieces + (corner >= 2 ? size : 0f);
                    kerb[corner] = Kerbs.at(kerbs, 2 + a[corner], b[corner]);
                }
                float whole = 0f;
                boolean both = true;
                for (int side = 0; side < 2; side++) {
                    int count = Litter.side(4, a, b, kerb, side == 0, outA, outB, outKerb);
                    both &= count >= 3;
                    if (count < 3) {
                        continue;
                    }
                    whole += area(count, outA, outB);
                    for (int corner = 0; corner < count; corner++) {
                        float field = Kerbs.at(kerbs, 2 + outA[corner], outB[corner]);
                        Checks.check(side == 0 ? field > -0.02f : field < 0.02f, "each part of a piece is on its own side of the kerb: " + field);
                    }
                }
                crossed += both ? 1 : 0;
                Checks.check(Math.abs(whole - size * size) < 1e-5f, "the two sides of a piece are the whole of it: " + whole);
            }
        }
        Checks.check(crossed >= 3, "the kerb crosses pieces of the square, and they are laid on both sides: " + crossed);
    }

    private static float area(int count, float[] a, float[] b) {
        float twice = 0f;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            twice += a[i] * b[j] - a[j] * b[i];
        }
        return Math.abs(twice) / 2f;
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

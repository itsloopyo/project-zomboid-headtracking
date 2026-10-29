package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

/** Tests of the crown of sprigs a tree or a bush is given in place of its sprite's painted leaves. */
final class CrownTests {
    private static final int WIDE = 128, HIGH = 256;
    private static final float FOOT_U = 64f, FOOT_V = 240f;

    private CrownTests() {}

    static void run() {
        Checks.run("leavesAreFoundOnlyWhereTheSpritePaintsThem", CrownTests::leavesAreFoundOnlyWhereTheSpritePaintsThem);
        Checks.run("crownStandsWhereTheSpriteShowsItAndEverySprigSaysWhereItsColourIs", CrownTests::crownStandsWhereTheSpriteShowsItAndEverySprigSaysWhereItsColourIs);
        Checks.run("crownFurtherOffIsFewerSprigs", CrownTests::crownFurtherOffIsFewerSprigs);
        Checks.run("spriteWithNextToNothingPaintedHasNoCrown", CrownTests::spriteWithNextToNothingPaintedHasNoCrown);
        Checks.run("sprigIsChosenByTheSpeciesInTheName", CrownTests::sprigIsChosenByTheSpeciesInTheName);
        Checks.run("crownsWrittenDownAreMadeAsTheBuildWouldHaveMadeThem", CrownTests::crownsWrittenDownAreMadeAsTheBuildWouldHaveMadeThem);
        Checks.run("crownsMadeFromTheSameAreKnownForTheSame", CrownTests::crownsMadeFromTheSameAreKnownForTheSame);
        Checks.run("crownsOfAChunkInAnotherBandAreMadeFromWhatWasWrittenDown", CrownTests::crownsOfAChunkInAnotherBandAreMadeFromWhatWasWrittenDown);
        Checks.run("crownsMadeOnTheirThreadAreTheSameCrowns", CrownTests::crownsMadeOnTheirThreadAreTheSameCrowns);
        Checks.run("spriteMeasuredAsNoCrownMakesNone", CrownTests::spriteMeasuredAsNoCrownMakesNone);
        Checks.run("aGreatTreesPaintedTrunkAndLimbsAreNoPartOfItsCrown", CrownTests::aGreatTreesPaintedTrunkAndLimbsAreNoPartOfItsCrown);
        Checks.run("onlyATreesSparseFootIsCut", CrownTests::onlyATreesSparseFootIsCut);
        Checks.run("aTreesCrownHasNoPlacesOnTheWoodItsSpritePaints", CrownTests::aTreesCrownHasNoPlacesOnTheWoodItsSpritePaints);
        Checks.run("aSprigThatMayBeLeftOutFromUnderTheTreeHasNoTwig", CrownTests::aSprigThatMayBeLeftOutFromUnderTheTreeHasNoTwig);
        Checks.run("crownBesideAFenceKeepsToItsOwnSideInAnyWind", CrownTests::crownBesideAFenceKeepsToItsOwnSideInAnyWind);
        Checks.run("aRootIsHeldInFromAFenceByAsFarAsItReaches", CrownTests::aRootIsHeldInFromAFenceByAsFarAsItReaches);
        Checks.run("everyPartOfATreesCrownIsItsTreesAndABushIsNoTree", CrownTests::everyPartOfATreesCrownIsItsTreesAndABushIsNoTree);
        Checks.run("aCrownLosingItsLeavesLosesWholeSprigsAndKeepsTheRest", CrownTests::aCrownLosingItsLeavesLosesWholeSprigsAndKeepsTheRest);
        Checks.run("everySprigSaysTheColourItsLeavesTurnAndHowFar", CrownTests::everySprigSaysTheColourItsLeavesTurnAndHowFar);
        Checks.run("autumnColourIsChosenByTheSpecies", CrownTests::autumnColourIsChosenByTheSpecies);
        Checks.run("aShadowOnlyFanSaysHowMuchShadowItThrows", CrownTests::aShadowOnlyFanSaysHowMuchShadowItThrows);
        Checks.run("woodAndRoundThingsAreLitByAWayOfLengthOne", CrownTests::woodAndRoundThingsAreLitByAWayOfLengthOne);
        Checks.run("aSprigIsLitByHowTheBoughsItIsOnSlope", CrownTests::aSprigIsLitByHowTheBoughsItIsOnSlope);
        Checks.run("aBushIsPaintedAsTheGameDrawsItOnlyInTheGamesOwnView", CrownTests::aBushIsPaintedAsTheGameDrawsItOnlyInTheGamesOwnView);
        Checks.run("aBushsBloomIsTheOutsideOfItsCrownAndFacesTheGamesViewOverhead", CrownTests::aBushsBloomIsTheOutsideOfItsCrownAndFacesTheGamesViewOverhead);
        Checks.run("aPaintedSprigOverheadKeepsToItsSpritesFrame", CrownTests::aPaintedSprigOverheadKeepsToItsSpritesFrame);
        Checks.run("aLayerReachesAsFarAsTheRectangleRoundItsPlaces", CrownTests::aLayerReachesAsFarAsTheRectangleRoundItsPlaces);
    }

    /** The sprigs of a crown, each by where its four corners are, to the thousandth of a tile. */
    private static java.util.Set<String> sprigsOf(SpriteGeometry.Mesh mesh) {
        java.util.Set<String> sprigs = new java.util.HashSet<>();
        float[] lit = LightSiteTests.lit(mesh);
        for (int at = 0; at < mesh.floats; at += 6 * SpriteGeometry.VERTEX_FLOATS) {
            if ((float) Math.floor((lit[at + 8] + 0.5f) / 32f) == Flora.CORE) {
                continue;
            }
            StringBuilder corners = new StringBuilder();
            for (int corner = 0; corner < 6; corner++) {
                int of = at + corner * SpriteGeometry.VERTEX_FLOATS;
                corners.append(Math.round(mesh.vertices[of] * 1000f)).append(',').append(Math.round(mesh.vertices[of + 1] * 1000f)).append(',')
                        .append(Math.round(mesh.vertices[of + 2] * 1000f)).append(' ');
            }
            sprigs.add(corners.toString());
        }
        return sprigs;
    }

    private static int coreVertices(SpriteGeometry.Mesh mesh) {
        return mesh.floats / SpriteGeometry.VERTEX_FLOATS - sprigVertices(mesh);
    }

    /**
     * Through autumn a crown loses sprigs whole, about as many as the leaves it has lost, and
     * every sprig a thinner crown has is one the fuller crown had, where it had it: nothing comes
     * and goes as the crown thins. Its inside draws in and is gone before the crown is half bare,
     * so the sky shows through. No leaves is no crown, and is never asked for.
     */
    private static void aCrownLosingItsLeavesLosesWholeSprigsAndKeepsTheRest() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces(CrownTests::leaf, frame);
        float[] leaves = {1f, 0.75f, 0.5f, 0.2f};
        SpriteGeometry.Mesh[] crowns = new SpriteGeometry.Mesh[leaves.length], woods = new SpriteGeometry.Mesh[leaves.length];
        for (int i = 0; i < leaves.length; i++) {
            crowns[i] = new SpriteGeometry.Mesh();
            woods[i] = new SpriteGeometry.Mesh();
            check(Flora.addCrown(crowns[i], woods[i], frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0.12f, 1.2f, Flora.BARK_OAK, Flora.MAPLE, 7,
                    Flora.OPEN, leaves[i], Flora.SCARLET_LEAF, 0.4f), "a crown with " + leaves[i] + " of its leaves is a crown");
        }
        // The leaves left are on wood: a crown that has lost some has a bare tree's limbs, branches and twigs in it.
        check(woods[2].floats > 2 * woods[0].floats, "half bare, a crown has a bare tree's wood: " + woods[2].floats + " floats of wood against " + woods[0].floats + " in full leaf");
        java.util.Set<String> full = sprigsOf(crowns[0]);
        for (int i = 1; i < leaves.length; i++) {
            java.util.Set<String> thinner = sprigsOf(crowns[i]), fuller = sprigsOf(crowns[i - 1]);
            float share = (float) thinner.size() / full.size();
            check(Math.abs(share - leaves[i]) < 0.1f, "a crown with " + leaves[i] + " of its leaves has as much of its sprigs: " + share);
            check(fuller.containsAll(thinner), "every sprig of the crown with " + leaves[i] + " of its leaves is one the fuller crown had, where it had it");
        }
        check(coreVertices(crowns[0]) > 0 && coreVertices(crowns[1]) == coreVertices(crowns[0]), "in full leaf and a quarter bare a crown has its inside");
        check(coreVertices(crowns[2]) == 0 && coreVertices(crowns[3]) == 0, "half bare it has none");
        boolean refused = false;
        try {
            Flora.addCrown(new SpriteGeometry.Mesh(), new SpriteGeometry.Mesh(), frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 1f, 0f, 0f, Flora.BARK_OAK, Flora.MAPLE, 7,
                    Flora.OPEN, 0f, Flora.SCARLET_LEAF, 1f);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a bare tree's crown is never made");
    }

    /**
     * A crown's sprigs and inside carry the colour its leaves turn and how far in their tree's
     * square: its x whole, its y within a quarter of a square of its own, and both read back as
     * the world shader reads them (PipelineShaders.AUTUMN's autumnOf, in float arithmetic) at
     * squares as far out as the map goes. The wood carries the square whole, and where on the
     * sprite a sprig takes its colour is left as it was, short of CROWN_STEPS.
     */
    private static void everySprigSaysTheColourItsLeavesTurnAndHowFar() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces(CrownTests::leaf, frame);
        int[][] squares = {{10, 20}, {10797, 9769}, {19999, 19998}};
        for (int[] square : squares) {
            for (int hue = 0; hue < Flora.HUES; hue++) {
                for (int level = 0; level < Flora.TURNS; level++) {
                    float turned = level / (Flora.TURNS - 1f);
                    SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh(), wood = new SpriteGeometry.Mesh();
                    check(Flora.addCrown(mesh, wood, frame, places, square[0] + 0.5f, square[1] + 0.5f, 0f, FOOT_U, FOOT_V, 1f, 1f, 0.12f, 1.2f, Flora.BARK_OAK, Flora.MAPLE, 7,
                            Flora.OPEN, 1f, hue, turned), "a crown of hue " + hue + " turned " + turned);
                    for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                        float rootX = mesh.vertices[at + SpriteGeometry.ROOT_AT], rootY = mesh.vertices[at + SpriteGeometry.ROOT_AT + 1];
                        check(rootX == square[0] && Math.abs(rootY - square[1]) < 0.25f, "a leaf carries its tree's square, " + rootX + ", " + rootY);
                        float code = (float) Math.floor((rootY - (float) Math.floor(rootY + 0.5f)) * Flora.AUTUMN_SPACING + 0.5f) + Flora.AUTUMN_CODES / 2;
                        float readHue = (float) Math.floor((code + 0.5f) / Flora.TURNS);
                        check(readHue == hue && code - readHue * Flora.TURNS == level, "and reads back as hue " + hue + " turned " + level + ", not code " + code);
                        check(mesh.vertices[at + 3] < Flora.CROWN_STEPS && mesh.vertices[at + 4] < Flora.CROWN_STEPS, "where it takes its colour is under CROWN_STEPS");
                    }
                    for (int at = 0; at < wood.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                        check(wood.vertices[at + SpriteGeometry.ROOT_AT + 1] == square[1], "its wood carries its tree's square whole");
                    }
                }
            }
        }
    }

    /**
     * What stands only to throw a shadow says in its red how much of a mass of leaves throws it,
     * which the sun's pictures read (PipelineShaders.SHADOW_VERTEX): a crop's or a full crown's
     * is 1 whatever the square's light, where a square unexplored, its light 0, once threw a solid
     * shadow. Nothing in the mod makes a shadow-only fan any other way than Flora.shadeOnly.
     */
    private static void aShadowOnlyFanSaysHowMuchShadowItThrows() throws java.io.IOException {
        SpriteGeometry.Frame frame = frame();
        frame.light(0f, 0f, 0f);
        Flora.shadeOnly(frame, 1f);
        check(frame.material == Flora.SHADE && frame.red == 1f, "a full mass of leaves throws its whole shadow in the dark");
        Flora.shadeOnly(frame, 0.3f);
        check(frame.red == 0.3f, "a crown with 0.3 of its leaves says so");
        try (java.util.stream.Stream<java.nio.file.Path> sources = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java/com/cameraunlock/zomboid")).filter(java.nio.file.Files::isRegularFile)) {
            for (java.nio.file.Path source : sources.toList()) {
                String text = java.nio.file.Files.readString(source);
                check(source.getFileName().toString().equals("Flora.java") || !text.contains("Flora.SHADE"),
                        source.getFileName() + " makes a shadow-only fan without saying how much shadow it throws");
            }
        }
    }

    private static void autumnColourIsChosenByTheSpecies() {
        for (int hash = 0; hash < 4096; hash += 97) {
            int maple = Flora.autumnHue("e_redmapleJUMBO_1_10", hash);
            check(maple == Flora.SCARLET_LEAF || maple == Flora.ORANGE_LEAF, "a red maple turns scarlet or orange");
            int linden = Flora.autumnHue("e_americanlinden_1_10", hash);
            check(linden == Flora.YELLOW_LEAF || linden == Flora.GOLD_LEAF, "a linden turns yellow or gold");
            int dogwood = Flora.autumnHue("e_dogwood_1_10", hash);
            check(dogwood == Flora.CLARET_LEAF || dogwood == Flora.SCARLET_LEAF, "a dogwood turns claret or scarlet");
        }
        check(Flora.autumnHue("e_redmaple_1_10", 0) != Flora.autumnHue("e_redmaple_1_10", 1 << 8), "not every tree of a kind turns one colour");
    }

    /**
     * The game opens or leaves a tree whole in its own view (CanopyWindow), so every vertex of a
     * crown, its sprigs, its core and the limbs and twigs in wood, carries its tree's square,
     * however far a sprig reaches past that square. A bush is no tree. What the meshes carried
     * before is theirs again after.
     */
    private static void everyPartOfATreesCrownIsItsTreesAndABushIsNoTree() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces(CrownTests::leaf, frame);
        SpriteGeometry.Mesh leaves = new SpriteGeometry.Mesh(), wood = new SpriteGeometry.Mesh();
        leaves.rooted(3, 4);
        check(Flora.addCrown(leaves, wood, frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 1f, 0.12f, 1.2f, Flora.BARK_OAK, Flora.MAPLE, 7, Flora.OPEN, 1f, 0, 0f),
                "a painted crown makes a crown");
        check(wood.floats > 0, "with limbs and twigs in wood");
        float leastX = Float.MAX_VALUE, mostX = -Float.MAX_VALUE;
        for (SpriteGeometry.Mesh mesh : new SpriteGeometry.Mesh[] {leaves, wood}) {
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                check(mesh.vertices[at + SpriteGeometry.ROOT_AT] == 10f && Math.round(mesh.vertices[at + SpriteGeometry.ROOT_AT + 1]) == 20,
                        "a vertex of the crown at " + mesh.vertices[at] + ", " + mesh.vertices[at + 1] + " is its tree's, on square 10, 20");
                leastX = Math.min(leastX, mesh.vertices[at]);
                mostX = Math.max(mostX, mesh.vertices[at]);
            }
        }
        check(leastX < 10f && mostX >= 11f, "and the crown reaches over the squares either side of its own: " + leastX + " to " + mostX);
        check(leaves.rootX == 3f && leaves.rootY == 4f && wood.rootX == SpriteGeometry.NO_ROOT, "the meshes carry on with what they carried before");
        // Its core and the twigs inside it as well: a bush in the window was hollowed when they went by their stuff.
        SpriteGeometry.Mesh bush = new SpriteGeometry.Mesh(), twigs = new SpriteGeometry.Mesh();
        check(Flora.addCrown(bush, twigs, frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 1f, 0f, 0f, Flora.BARK_OAK, Flora.SHRUB, 7, Flora.OPEN, 1f, 0, 0f),
                "a bush makes a crown");
        check(twigs.floats > 0, "with twigs");
        for (SpriteGeometry.Mesh mesh : new SpriteGeometry.Mesh[] {bush, twigs}) {
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                check(mesh.vertices[at + SpriteGeometry.ROOT_AT] == SpriteGeometry.NO_ROOT, "a bush's vertex is no tree's, its core and twigs included");
            }
        }
        // A bush's leaves and a young tree's are painted with their sprite from overhead, and a grown tree's are not.
        SpriteGeometry.Mesh young = new SpriteGeometry.Mesh();
        check(Flora.addCrown(young, new SpriteGeometry.Mesh(), frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 1f, 0f, 0f, Flora.BARK_OAK, Flora.PINE, 7, Flora.OPEN, 1f, 0, 0f,
                TreeForm.NONE, null, Flora.TWIGGED_AT_1080, true), "a young tree makes a crown");
        for (SpriteGeometry.Mesh mesh : new SpriteGeometry.Mesh[] {bush, young, leaves}) {
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                check(mesh.vertices[at + 3] >= Flora.PAINTED == (mesh != leaves), "a vertex of a painted crown says so in where it takes its colour, and only one of a painted crown");
            }
        }
    }

    /** How far on average a mesh's sprig corners stand from the upright through (10.5, 20.5). */
    private static float meanReach(SpriteGeometry.Mesh mesh) {
        double sum = 0.0;
        int count = 0;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            sum += Math.hypot(mesh.vertices[at] - 10.5f, mesh.vertices[at + 1] - 20.5f);
            count++;
        }
        return (float) (sum / count);
    }

    /**
     * The flowers the game paints over a bush stand on the outside of the bush's own crown, beyond
     * its leaves, with no inside or twigs of their own; overhead each faces the game's view, so
     * that none is seen edge on and left out.
     */
    private static void aBushsBloomIsTheOutsideOfItsCrownAndFacesTheGamesViewOverhead() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces(CrownTests::leaf, frame);
        SpriteGeometry.Mesh leaves = new SpriteGeometry.Mesh(), bloom = new SpriteGeometry.Mesh(), twigs = new SpriteGeometry.Mesh();
        check(Flora.addCrown(leaves, new SpriteGeometry.Mesh(), frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 1f, 0f, 0f, Flora.BARK_OAK, Flora.SHRUB, 7, Flora.OPEN,
                1f, 0, 0f, TreeForm.NONE, null, Flora.TWIGGED_AT_1080, true, false, false), "a bush makes a crown");
        check(Flora.addCrown(bloom, twigs, frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 1f, 0f, 0f, Flora.BARK_OAK, Flora.SHRUB, 7, Flora.OPEN,
                1f, 0, 0f, TreeForm.NONE, null, Flora.TWIGGED_AT_1080, true, false, true), "its bloom makes one");
        check(sprigVertices(bloom) >= 6 * 20 && coreVertices(bloom) == 0 && twigs.floats == 0, "of a good many sprigs, with no inside and no twigs: "
                + sprigVertices(bloom) / 6 + " sprigs, " + coreVertices(bloom) + " vertices inside, " + twigs.floats + " floats of twig");
        check(meanReach(bloom) > meanReach(leaves) + 0.05f, "standing out past the leaves: " + meanReach(bloom) + " from the middle against " + meanReach(leaves));
        boolean marked = true, unmarked = true;
        for (int at = 0; at < bloom.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            marked &= bloom.vertices[at + 4] >= Flora.BLOOMS;
        }
        for (int at = 0; at < leaves.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            unmarked &= leaves.vertices[at + 4] < Flora.BLOOMS;
        }
        check(marked && unmarked, "every vertex of the bloom says it is one in where it takes its colour, and none of the leaves");
        SpriteGeometry.Mesh overhead = new SpriteGeometry.Mesh();
        check(Flora.addCrown(overhead, new SpriteGeometry.Mesh(), frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 1f, 0f, 0f, Flora.BARK_OAK, Flora.SHRUB, 7, Flora.OPEN,
                1f, 0, 0f, TreeForm.NONE, null, Flora.TWIGGED_AT_1080, true, true, true), "built for overhead it makes one too");
        float least = 1f;
        for (int at = 0; at < overhead.floats; at += 6 * SpriteGeometry.VERTEX_FLOATS) {
            int b = at + SpriteGeometry.VERTEX_FLOATS, c = at + 2 * SpriteGeometry.VERTEX_FLOATS;
            float ax = overhead.vertices[b] - overhead.vertices[at], ay = overhead.vertices[b + 1] - overhead.vertices[at + 1], az = overhead.vertices[b + 2] - overhead.vertices[at + 2];
            float bx = overhead.vertices[c] - overhead.vertices[at], by = overhead.vertices[c + 1] - overhead.vertices[at + 1], bz = overhead.vertices[c + 2] - overhead.vertices[at + 2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            least = Math.min(least, Math.abs(nx * 0.61237f + ny * 0.61237f + nz * 0.5f) / length);
        }
        check(least > 0.999f, "and every sprig of it faces the game's view, the least square on to it " + least);
    }

    /**
     * Overhead a painted sprig is painted with what its sprite paints round it, and past the
     * sprite's frame that is another sprite's: every corner of every sprig keeps within the frame
     * seen along the game's view, where from the eye they reach past it.
     */
    private static void aPaintedSprigOverheadKeepsToItsSpritesFrame() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces(CrownTests::leaf, frame);
        // The disc is 100 pixels across: a frame trimmed to 80 of them cuts its sides.
        frame.left = FOOT_U - 40f;
        frame.width = 80f;
        float up = ViewCamera.LEVEL_HEIGHT / SpriteGeometry.PIXELS_PER_LEVEL;
        for (boolean overhead : new boolean[] {false, true}) {
            SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
            check(Flora.addCrown(mesh, new SpriteGeometry.Mesh(), frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.SHRUB, 7, Flora.OPEN,
                    1f, 0, 0f, TreeForm.NONE, null, Flora.TWIGGED_AT_1080, true, overhead, false), "a bush makes a crown");
            int outside = 0, sprigs = 0;
            float[] lit = LightSiteTests.lit(mesh);
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                if ((float) Math.floor((lit[at + 8] + 0.5f) / 32f) == Flora.CORE) {
                    continue;
                }
                sprigs++;
                float dx = mesh.vertices[at] - 10.5f, dy = mesh.vertices[at + 1] - 20.5f;
                float u = FOOT_U + (dx - dy) * SpriteGeometry.PIXELS_ACROSS_PER_TILE, v = FOOT_V + (dx + dy) * SpriteGeometry.PIXELS_DOWN_PER_TILE - mesh.vertices[at + 2] / up;
                if (u < frame.left - 0.01f || u > frame.left + frame.width + 0.01f || v < frame.top - 0.01f || v > frame.top + frame.height + 0.01f) {
                    outside++;
                }
            }
            check(sprigs >= 6 * 20, "of a good many sprigs: " + sprigs / 6);
            check(overhead ? outside == 0 : outside > 0, (overhead ? "overhead none" : "from the eye some") + " of its corners lie past the frame: " + outside);
        }
        check(Flora.fitsFrame(frame, FOOT_U, FOOT_V, 0f, 0f, 160f * up, up, 0.1f, 0f, 0f, 0f, 0.05f, 0f) == 1f, "a small sprig in the frame's middle fits whole");
        check(Flora.fitsFrame(frame, FOOT_U, FOOT_V, 3f, -3f, 160f * up, up, 0.1f, 0f, 0f, 0f, 0.05f, 0f) == 0f, "one whose middle is past the frame not at all");
        float half = Flora.fitsFrame(frame, FOOT_U, FOOT_V, 0.4f, -0.4f, 160f * up, up, 1f, -1f, 0f, 0f, 0f, 0f);
        check(Math.abs(half - 0.45f) < 1e-4f, "and one too long for where it is shrinks to what fits (14.4 pixels of room, 32 of reach): " + half);
    }

    private static void aLayerReachesAsFarAsTheRectangleRoundItsPlaces() {
        check(Flora.spread(new float[] {10f, 20f, 40f, 25f, 30f, 60f}) == 30f * 40f, "the rectangle round three places");
        check(Flora.spread(new float[0]) == 0f, "and none round no places");
    }

    /** How a crown's boughs slope under a sprig, round the crown and up it, is how fast where it is on them changes going either way. */
    private static void aSprigIsLitByHowTheBoughsItIsOnSlope() {
        float[] at = new float[3], ahead = new float[3], behind = new float[3];
        boolean round = true, up = true;
        for (int i = 0; i < 400; i++) {
            float turn = i * 0.0157f, share = (i % 23) / 22f;
            Flora.boughSlopes(turn, share, 3 + i % 3, 1.5f + (i % 5) * 0.6f, 0.3f, 1.1f, 2.2f, at);
            Flora.boughSlopes(turn + 1e-3f, share, 3 + i % 3, 1.5f + (i % 5) * 0.6f, 0.3f, 1.1f, 2.2f, ahead);
            Flora.boughSlopes(turn - 1e-3f, share, 3 + i % 3, 1.5f + (i % 5) * 0.6f, 0.3f, 1.1f, 2.2f, behind);
            round &= Math.abs((ahead[0] - behind[0]) / 2e-3f - at[1]) < 0.01f;
            Flora.boughSlopes(turn, share + 1e-3f, 3 + i % 3, 1.5f + (i % 5) * 0.6f, 0.3f, 1.1f, 2.2f, ahead);
            Flora.boughSlopes(turn, share - 1e-3f, 3 + i % 3, 1.5f + (i % 5) * 0.6f, 0.3f, 1.1f, 2.2f, behind);
            up &= Math.abs((ahead[0] - behind[0]) / 2e-3f - at[2]) < 0.02f;
        }
        check(round && up, "the slope a sprig is lit by is the boughs' own, round the crown and up it");
    }

    /**
     * Wood and a round thing are lit by the way out of them, given as it is with no length taken
     * off it: it has to be of length 1 already, with only the wind's share (at most 0.3) on
     * it, or the picture takes the wrong sway off it. A round thing stands still, and faces
     * straight out from its axis.
     */
    private static void woodAndRoundThingsAreLitByAWayOfLengthOne() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces(CrownTests::leaf, frame);
        for (int sprig : new int[] {Flora.MAPLE, Flora.BARE}) {
            for (float detail : new float[] {1f, 0.8f}) {
                SpriteGeometry.Mesh leaves = new SpriteGeometry.Mesh(), wood = new SpriteGeometry.Mesh();
                check(Flora.addCrown(leaves, wood, frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, detail, 1f, 0.12f, 1.2f, Flora.BARK_OAK, sprig, 7, Flora.OPEN, 1f, 0, 0f)
                        && wood.floats > 0, "a crown with wood in it");
                boolean unit = true;
                for (int at = 0; at < wood.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                    float length = (float) Math.sqrt(wood.vertices[at + 9] * wood.vertices[at + 9] + wood.vertices[at + 10] * wood.vertices[at + 10]
                            + wood.vertices[at + 11] * wood.vertices[at + 11]);
                    unit &= length > 1f - 1e-5f && length < 1.3f + 1e-5f;
                }
                check(unit, "every vertex of its wood is lit by a way of length 1 and the wind's share on it");
            }
        }
        SpriteGeometry.Mesh round = new SpriteGeometry.Mesh();
        for (int sides : new int[] {6, 8, 12}) {
            round.clear();
            LightSiteTests.constant(round);
            SpriteGeometry.addRound(round, 1.5f, 2.5f, 0.1f, 0.9f, 0.2f, 0.1f, sides, 0f, 0f, 1f, 1f, 1f);
            boolean outward = true;
            for (int at = 0; at < sides * 6 * SpriteGeometry.VERTEX_FLOATS; at += SpriteGeometry.VERTEX_FLOATS) {
                float nx = round.vertices[at + 9], ny = round.vertices[at + 10], nz = round.vertices[at + 11];
                float ox = round.vertices[at] - 1.5f, oy = round.vertices[at + 1] - 2.5f;
                outward &= Math.abs(nx * nx + ny * ny + nz * nz - 1f) < 1e-5f && Math.abs(nx * oy - ny * ox) < 1e-5f && nx * ox + ny * oy > 0f;
            }
            check(outward, "a round thing of " + sides + " sides faces straight out of its axis, by a way of length 1");
        }
    }

    /** Where a fern's root or a tuft of long grass stands in its room, as the build places it with Flora.within. */
    private static void aRootIsHeldInFromAFenceByAsFarAsItReaches() {
        float fence = 0.06f, open = Float.MAX_VALUE;
        check(Flora.within(0.5f, 0.3f, fence, open) == 0.5f, "a root that reaches no fence is where it grows");
        check(Math.abs(Flora.within(0.2f, 0.3f, fence, open) - (fence + 0.3f)) < 1e-6f, "one that would reach through is moved in until it reaches the fence and no further");
        check(Math.abs(Flora.within(0.9f, 0.3f, -open, 1f - fence) - (1f - fence - 0.3f)) < 1e-6f, "from a fence on the far side as well");
        check(Math.abs(Flora.within(0.1f, 0.6f, fence, 1f - fence) - 0.5f) < 1e-6f, "and one too big for a square fenced both sides stands in its middle");
    }

    /** A round crown painted in the upper half of the sprite, over a bare stick of trunk that is no part of it. */
    private static boolean leaf(int u, int v) {
        float du = u - FOOT_U, dv = v - 80f;
        return du * du + dv * dv < 50f * 50f;
    }

    private static SpriteGeometry.Frame frame() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 1f;
        frame.left = 0f;
        frame.top = 0f;
        frame.width = WIDE;
        frame.height = HIGH;
        frame.uStart = 0.25f;
        frame.uEnd = 0.5f;
        frame.vStart = 0.5f;
        frame.vEnd = 1f;
        frame.open = 1f;
        frame.light(0.8f, 0.8f, 0.8f);
        return LightSiteTests.sited(frame);
    }

    private static void leavesAreFoundOnlyWhereTheSpritePaintsThem() {
        float[] places = Flora.leafPlaces(CrownTests::leaf, frame());
        check(places.length >= 2 * 40, "a crown that fills a quarter of its sprite is found at dozens of places, not " + places.length / 2);
        for (int i = 0; i < places.length; i += 2) {
            check(leaf((int) places[i], (int) places[i + 1]), "every place found is one the sprite paints");
        }
    }

    private static void crownStandsWhereTheSpriteShowsItAndEverySprigSaysWhereItsColourIs() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces(CrownTests::leaf, frame);
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        check(Flora.addCrown(mesh, new SpriteGeometry.Mesh(), frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.MAPLE, 7, Flora.OPEN, 1f, 0, 0f), "a painted crown makes a crown");
        int vertices = mesh.floats / SpriteGeometry.VERTEX_FLOATS;
        int sprigs = sprigVertices(mesh), inside = 0;
        check(sprigs >= 6 * 20 && sprigs % 6 == 0, "the crown is whole sprigs of two triangles, a good many of them: " + sprigs / 6);
        float across = SpriteGeometry.TILES_PER_PIXEL_ACROSS, up = ViewCamera.LEVEL_HEIGHT / SpriteGeometry.PIXELS_PER_LEVEL;
        // The painted disc is 50 pixels round a middle 160 above the foot, and a sprig reaches at most its own length past where it grows.
        float reach = 50f * across + 1f, low = 110f * up - 1f, high = 210f * up + 1f;
        float[] lit = LightSiteTests.lit(mesh);
        for (int vertex = 0; vertex < vertices; vertex++) {
            int at = vertex * SpriteGeometry.VERTEX_FLOATS;
            float dx = mesh.vertices[at] - 10.5f, dy = mesh.vertices[at + 1] - 20.5f, z = mesh.vertices[at + 2];
            check(Math.sqrt(dx * dx + dy * dy) <= reach, "no sprig stands further from the trunk than the sprite's crown is wide");
            check(z >= low && z <= high, "every sprig is at the height the sprite paints leaves at, not " + z);
            float u = mesh.vertices[at + 3], v = mesh.vertices[at + 4];
            float partU = u - (float) Math.floor(u), partV = v - (float) Math.floor(v);
            float material = (float) Math.floor((lit[at + 8] + 0.5f) / 32f);
            boolean core = material == Flora.CORE;
            if (core) {
                // The inside of the crown: within the sprigs, in shade, and saying which kind of leaf it is made of.
                inside++;
                check(Math.sqrt(dx * dx + dy * dy) <= 50f * across * 0.8f, "the inside of a crown keeps inside its sprigs");
                check(Math.abs(partU - (Flora.MAPLE + 0.5f) / Flora.CORE_KINDS) < 1e-3f, "the inside says which sprig its leaves are");
                check(lit[at + 5] < 0.8f * 0.7f, "the inside of a crown is in the shade of its outside");
            } else {
                check(Math.abs(partU - 0.25f) < 1e-3f || Math.abs(partU - 0.75f) < 1e-3f, "a corner is a quarter in from the whole number across");
                check(Math.abs(partV - 0.25f) < 1e-3f || Math.abs(partV - 0.75f) < 1e-3f, "and down");
                check(material == Flora.CROWN + Flora.MAPLE, "every vertex is marked as a sprig of the asked kind");
            }
            // The whole number is a place in the sprite's own part of its texture, which the crown is coloured from.
            float spriteU = (float) Math.floor(u) / Flora.CROWN_STEPS, spriteV = (float) Math.floor(v) / Flora.CROWN_STEPS;
            check(spriteU >= 0.25f - 1e-3f && spriteU <= 0.5f + 1e-3f && spriteV >= 0.5f - 1e-3f && spriteV <= 1f + 1e-3f,
                    "the colour is taken from inside the sprite's frame");
        }
        check(inside >= 3 * 40 && inside % 3 == 0, "a crown has an inside, of whole triangles all the way round it: " + inside / 3);
    }

    /** How many of a crown's vertices are its sprigs', and not its inside's. */
    private static int sprigVertices(SpriteGeometry.Mesh mesh) {
        int count = 0;
        float[] lit = LightSiteTests.lit(mesh);
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            if ((float) Math.floor((lit[at + 8] + 0.5f) / 32f) != Flora.CORE) {
                count++;
            }
        }
        return count;
    }

    private static void crownFurtherOffIsFewerSprigs() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces(CrownTests::leaf, frame);
        SpriteGeometry.Mesh near = new SpriteGeometry.Mesh(), far = new SpriteGeometry.Mesh();
        Flora.addCrown(near, new SpriteGeometry.Mesh(), frame, places, 0.5f, 0.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.LINDEN, 7, Flora.OPEN, 1f, 0, 0f);
        Flora.addCrown(far, new SpriteGeometry.Mesh(), frame, places, 0.5f, 0.5f, 0f, FOOT_U, FOOT_V, 0.25f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.LINDEN, 7, Flora.OPEN, 1f, 0, 0f);
        int farSprigs = sprigVertices(far), nearSprigs = sprigVertices(near);
        check(farSprigs > 0 && farSprigs * 2 < nearSprigs, "a crown at a quarter of the detail has under half the sprigs: " + farSprigs + " of " + nearSprigs);
        check(coreVertices(far) > 0 && coreVertices(far) * 3 < coreVertices(near), "and an inside of under a third as many faces: " + coreVertices(far) + " of " + coreVertices(near));
        // A bare tree's twigs reach as far as a twig is half a pixel across: further in a taller picture, and less far in a wider view.
        float set = (float) Math.tan(Math.toRadians(FieldOfView.DEFAULT_DEGREES) / 2.0), wide = (float) Math.tan(Math.toRadians(90.0) / 2.0);
        check(Flora.twiggedFrom(2160, set) < Flora.TWIGGED_AT_1080 && Flora.twiggedFrom(1080, wide) > Flora.TWIGGED_AT_1080 && Flora.TWIGGED_AT_1080 == Flora.twiggedFrom(1080, set),
                "twigs reach further in a taller picture and less far in a wider view");
        check(twigged(frame, places, 20f, Flora.TWIGGED_AT_1080) && !twigged(frame, places, 30f, Flora.TWIGGED_AT_1080) && twigged(frame, places, 30f, Flora.twiggedFrom(2160, set)),
                "at 1080 pixels high a bare crown 20 tiles off has twigs and one 30 off none, and at 2160 that one has them too");
    }

    /** Whether a bare crown that far off has twigs, which are wood too fine to throw a shadow: see Flora.FINE_MARK. */
    private static boolean twigged(SpriteGeometry.Frame frame, float[] places, float away, float twiggedFrom) {
        SpriteGeometry.Mesh wood = new SpriteGeometry.Mesh();
        Flora.addCrown(new SpriteGeometry.Mesh(), wood, frame, places, 0.5f, 0.5f, 0f, FOOT_U, FOOT_V, Crowns.detail(away), 0f, 0.12f, 1.2f, Flora.BARK_OAK, Flora.BARE, 7,
                Flora.OPEN, 1f, 0, 0f, TreeForm.NONE, null, twiggedFrom, false);
        for (int at = 0; at < wood.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            if (wood.vertices[at + 3] >= Flora.FINE_MARK) {
                return true;
            }
        }
        return false;
    }

    private static void spriteWithNextToNothingPaintedHasNoCrown() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces((u, v) -> u == 3 && v == 3, frame);
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        check(!Flora.addCrown(mesh, new SpriteGeometry.Mesh(), frame, places, 0.5f, 0.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.LINDEN, 7, Flora.OPEN, 1f, 0, 0f) && mesh.floats == 0,
                "a sprite with one dot painted makes no crown and adds nothing");
    }

    private static void sprigIsChosenByTheSpeciesInTheName() {
        check(Flora.sprigOf("e_virginiapine_1_3", false) == Flora.PINE, "a pine bears pine sprigs");
        check(Flora.sprigOf("e_redmaple_1_0", false) == Flora.MAPLE, "a maple bears maple sprigs");
        check(Flora.sprigOf("e_americanholly_1_2", false) == Flora.HOLLY, "a holly bears holly sprigs");
        check(Flora.sprigOf("e_canadianhemlock_1_2", false) == Flora.HEMLOCK, "a hemlock bears hemlock sprigs");
        check(Flora.sprigOf("e_riverbirch_1_2", false) == Flora.BIRCH, "a birch bears birch sprigs");
        check(Flora.sprigOf("e_americanlinden_1_2", false) == Flora.LINDEN, "a linden bears linden sprigs");
        check(Flora.sprigOf("f_bushes_1_4", true) == Flora.SHRUB, "a bush bears a shrub's, whatever it is called");
    }

    private static final int LEAVES = 41, PLAIN = 0;
    /** One sprite's leaves, found once as the mod finds them once a texture: two crowns are the same only on the same places. */
    private static final float[] PLACES = Flora.leafPlaces(CrownTests::leaf, frame());

    /** Two trees and a bush of one chunk as from that far off, the second tree in another light and with a bole to grow limbs from. */
    private static Crowns chunkOfThree(float secondRed, float away) {
        SpriteGeometry.Frame frame = frame();
        float[] places = PLACES;
        Crowns crowns = new Crowns();
        crowns.add(LEAVES, frame, places, 10.5f, 20.5f, 0.1f, FOOT_U, FOOT_V, away, 0f, 0f, Flora.BARK_OAK, Flora.BARK_OAK, Flora.MAPLE, 7, Flora.OPEN, 1f, 0, 0f, TreeForm.NONE, null, Flora.TWIGGED_AT_1080, false, false, false);
        frame.light(secondRed, 0.5f, 0.4f);
        LightSiteTests.sited(frame);
        crowns.add(LEAVES + 1, frame, places, 12.5f, 21.5f, 0f, FOOT_U, FOOT_V, away, 0.12f, 0.9f, Flora.BARK_OAK + 1, Flora.BARK_OAK, Flora.LINDEN, 8, Flora.OPEN, 1f, 0, 0f, TreeForm.NONE, null, Flora.TWIGGED_AT_1080, false, false, false);
        frame.open = 0f;
        LightSiteTests.sited(frame);
        crowns.add(LEAVES, frame, places, 14.5f, 22.5f, 0f, FOOT_U, FOOT_V, away, 0f, 0f, Flora.BARK_OAK, Flora.BARK_OAK, Flora.SHRUB, 9, Flora.OPEN, 1f, 0, 0f, TreeForm.NONE, null, Flora.TWIGGED_AT_1080, true, false, false);
        return crowns;
    }

    private static Crowns chunkOfThree(float secondRed) {
        return chunkOfThree(secondRed, 6f);
    }

    /** The same three as a build made them before crowns were written down: each from its own numbers. */
    private static void threeInTheBuild(SpriteGeometry.MeshSet inBuild, float away) {
        SpriteGeometry.Frame frame = frame();
        float[] places = PLACES;
        float detail = Crowns.detail(away), close = Crowns.close(away);
        boolean limbed = Crowns.limbed(away, Flora.LINDEN, 1f);
        Flora.addCrown(inBuild.get(LEAVES), inBuild.get(PLAIN), frame, places, 10.5f, 20.5f, 0.1f, FOOT_U, FOOT_V, detail, close, 0f, 0f, Flora.BARK_OAK, Flora.MAPLE, 7, Flora.OPEN, 1f, 0, 0f);
        frame.light(0.6f, 0.5f, 0.4f);
        LightSiteTests.sited(frame);
        Flora.addCrown(inBuild.get(LEAVES + 1), inBuild.get(PLAIN), frame, places, 12.5f, 21.5f, 0f, FOOT_U, FOOT_V, detail, close, limbed ? 0.12f : 0f, limbed ? 0.9f : 0f,
                limbed ? Flora.BARK_OAK + 1 : Flora.BARK_OAK, Flora.LINDEN, 8, Flora.OPEN, 1f, 0, 0f);
        frame.open = 0f;
        LightSiteTests.sited(frame);
        Flora.addCrown(inBuild.get(LEAVES), inBuild.get(PLAIN), frame, places, 14.5f, 22.5f, 0f, FOOT_U, FOOT_V, detail, close, 0f, 0f, Flora.BARK_OAK, Flora.SHRUB, 9, Flora.OPEN, 1f, 0, 0f);
    }

    private static void same(SpriteGeometry.MeshSet apart, SpriteGeometry.MeshSet inBuild) {
        check(apart.used.size() == 3 && inBuild.used.size() == 3, "the leaves of two textures and the wood are three meshes");
        for (int i = 0; i < 3; i++) {
            SpriteGeometry.Mesh one = apart.used.get(i), other = inBuild.used.get(i);
            check(one.texture == other.texture && one.floats == other.floats && (one.floats > 0 || one.texture == PLAIN), "each mesh is of the same texture and as long");
            float[] oneLit = LightSiteTests.lit(one), otherLit = LightSiteTests.lit(other);
            for (int at = 0; at < one.floats; at++) {
                // Each counts its records in a table of its own: what is the same is the light the two records give.
                check(at % SpriteGeometry.VERTEX_FLOATS == SpriteGeometry.RECORD_AT || Float.floatToRawIntBits(one.vertices[at]) == Float.floatToRawIntBits(other.vertices[at]),
                        "and the same to the last bit of every float");
                check(Float.floatToRawIntBits(oneLit[at]) == Float.floatToRawIntBits(otherLit[at]), "and lit the same to the last bit");
            }
        }
    }

    private static void crownsWrittenDownAreMadeAsTheBuildWouldHaveMadeThem() {
        SpriteGeometry.MeshSet apart = new SpriteGeometry.MeshSet(), inBuild = new SpriteGeometry.MeshSet(new SpriteGeometry.Layers(), WorldScene.sharedSites);
        // Near, with limbs and stood under; in between; and far off, coarse and in its own bark.
        for (float away : new float[] {6f, 20f, 30f}) {
            apart.clear();
            inBuild.clear();
            chunkOfThree(0.6f, away).mesh(apart, PLAIN, new SpriteGeometry.Frame());
            threeInTheBuild(inBuild, away);
            same(apart, inBuild);
        }
    }

    private static void crownsOfAChunkInAnotherBandAreMadeFromWhatWasWrittenDown() {
        Crowns near = chunkOfThree(0.6f, 6f), moved = new Crowns();
        near.copyTo(moved, 30f);
        check(!moved.same(near) && moved.same(chunkOfThree(0.6f, 30f)), "written down again as from further off, they are what a build from there writes down");
        SpriteGeometry.MeshSet apart = new SpriteGeometry.MeshSet(), inBuild = new SpriteGeometry.MeshSet(new SpriteGeometry.Layers(), WorldScene.sharedSites);
        moved.mesh(apart, PLAIN, new SpriteGeometry.Frame());
        threeInTheBuild(inBuild, 30f);
        same(apart, inBuild);
        // Into one that held more.
        Crowns one = new Crowns();
        one.add(LEAVES, frame(), PLACES, 1.5f, 2.5f, 0f, FOOT_U, FOOT_V, 6f, 0f, 0f, Flora.BARK_OAK, Flora.BARK_OAK, Flora.MAPLE, 3, Flora.OPEN, 1f, 0, 0f, TreeForm.NONE, null, Flora.TWIGGED_AT_1080, false, false, false);
        one.copyTo(moved, 6f);
        check(moved.same(one) && !moved.same(near), "and take the place of whatever was written there before");
        // And more than a new one has room for: nine, where it starts with room for eight.
        Crowns many = new Crowns(), room = new Crowns();
        for (int i = 0; i < 9; i++) {
            many.add(LEAVES, frame(), PLACES, i + 0.5f, 2.5f, 0f, FOOT_U, FOOT_V, 6f, 0f, 0f, Flora.BARK_OAK, Flora.BARK_OAK, Flora.MAPLE, i, Flora.OPEN, 1f, 0, 0f, TreeForm.NONE, null, Flora.TWIGGED_AT_1080, false, false, false);
        }
        many.copyTo(room, 6f);
        check(room.same(many), "nine crowns written into one that has held none are the same nine");
        many.copyTo(room, 20f);
        check(!room.same(many), "and from further off they are others");
    }

    private static void crownsMadeFromTheSameAreKnownForTheSame() {
        Crowns crowns = chunkOfThree(0.6f);
        check(crowns.same(chunkOfThree(0.6f)), "what a second build of an unchanged chunk writes down is the same crowns");
        check(!crowns.same(chunkOfThree(0.61f)), "a tree in another light is another crown");
        Crowns fewer = new Crowns();
        check(!crowns.same(fewer) && !fewer.same(crowns) && fewer.isEmpty() && !crowns.isEmpty(), "and a chunk with its trees felled has other crowns");
        crowns.clear();
        check(crowns.isEmpty() && crowns.same(fewer), "written down again from nothing, it holds nothing");
    }

    private static void crownsMadeOnTheirThreadAreTheSameCrowns() {
        SpriteGeometry.MeshSet here = new SpriteGeometry.MeshSet(), empty = new SpriteGeometry.MeshSet();
        chunkOfThree(0.6f).mesh(here, PLAIN, new SpriteGeometry.Frame());
        WorldScene.ChunkMeshes made = new WorldScene.ChunkMeshes(0), there = new WorldScene.ChunkMeshes(0), spared = new WorldScene.ChunkMeshes(0);
        made.fill(here, empty);
        LinkedBlockingQueue<Crowns.Work> back = new LinkedBlockingQueue<>(), otherViews = new LinkedBlockingQueue<>();
        Crowns.Work wanted = new Crowns.Work(chunkOfThree(0.6f), there, 5, PLAIN, false, back), unwanted = new Crowns.Work(chunkOfThree(0.6f), spared, 6, PLAIN, false, back);
        Crowns.Work others = new Crowns.Work(chunkOfThree(0.6f), new WorldScene.ChunkMeshes(1), 5, PLAIN, false, otherViews);
        unwanted.unwanted = true;
        Crowns.begin(wanted);
        Crowns.begin(others);
        Crowns.begin(unwanted);
        check(Crowns.awaited(back) == wanted && Crowns.awaited(back) == unwanted && back.poll() == null, "every piece of work comes back, in the order it went, and no more");
        check(Crowns.awaited(otherViews) == others && otherViews.poll() == null, "and to the view that handed it over, not to another");
        others.into.scrap();
        check(spared.vertices == null && spared.floats == 0, "crowns nobody wants any more are not made");
        check(there.floats == made.floats && there.runs == made.runs && there.floats > 0, "the build made on the other thread is as long");
        for (int at = 0; at < made.floats; at++) {
            check(Float.floatToRawIntBits(there.vertices.get(at)) == Float.floatToRawIntBits(made.vertices.get(at)), "and the same to the last bit");
        }
        check(there.minX == made.minX && there.maxUp == made.maxUp, "with the same box round it");
        made.scrap();
        there.scrap();
        // One dot of paint is no crown, and is never written down as one: made all the same, the making fails.
        Crowns none = new Crowns();
        none.add(LEAVES, frame(), Flora.leafPlaces((u, v) -> u == 3 && v == 3, frame()), 0.5f, 0.5f, 0f, FOOT_U, FOOT_V, 12f, 0f, 0f, Flora.BARK_OAK, Flora.BARK_OAK, Flora.LINDEN, 7, Flora.OPEN, 1f, 0, 0f, TreeForm.NONE, null, Flora.TWIGGED_AT_1080, false, false, false);
        Crowns.Work failing = new Crowns.Work(none, spared, 7, PLAIN, false, back);
        Crowns.begin(failing);
        check(Crowns.awaited(back) == failing && failing.failed instanceof IllegalStateException, "work that fails comes back too, with what it threw");
    }

    private static void spriteMeasuredAsNoCrownMakesNone() {
        SpriteGeometry.Frame frame = frame();
        float[] reachLeft = new float[Flora.CROWN_BANDS], reachRight = new float[Flora.CROWN_BANDS], sizes = new float[Flora.SIZES];
        check(!Flora.measure(frame, Flora.leafPlaces((u, v) -> u == 3 && v == 3, frame), FOOT_U, FOOT_V, true, reachLeft, reachRight, new int[Flora.CROWN_BANDS], sizes), "one dot of paint measures as no crown");
        // A stroke one pixel wide up the middle: places enough, and no width.
        float[] stick = Flora.leafPlaces((u, v) -> u == (int) FOOT_U, frame);
        check(Flora.measure(frame, stick, FOOT_U, FOOT_V, true, reachLeft, reachRight, new int[Flora.CROWN_BANDS], sizes)
                == Flora.addCrown(new SpriteGeometry.Mesh(), new SpriteGeometry.Mesh(), frame, stick, 0.5f, 0.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.LINDEN, 7, Flora.OPEN, 1f, 0, 0f),
                "what measures as a crown is made, and what does not is not");
        check(Flora.measure(frame, Flora.leafPlaces(CrownTests::leaf, frame), FOOT_U, FOOT_V, true, reachLeft, reachRight, new int[Flora.CROWN_BANDS], sizes) && sizes[Flora.WIDEST] > 1f && sizes[Flora.TALL] > 1f,
                "a painted crown measures as one, tiles wide and tall");
    }

    /**
     * A great tree's leaf sprite paints its whole trunk under the crown: limbs forking out as far
     * as the crown's foot is wide, and roots splayed at the ground. Each height of that is wide
     * and holds few places, and the crown, which ends where its leaves do, has none of it. Made
     * of it, the crown stood brown sprigs round the trunk at the eye's height.
     */
    private static void aGreatTreesPaintedTrunkAndLimbsAreNoPartOfItsCrown() {
        SpriteGeometry.Frame frame = frame();
        float[] left = new float[Flora.CROWN_BANDS], right = new float[Flora.CROWN_BANDS], sizes = new float[Flora.SIZES];
        int[] counts = new int[Flora.CROWN_BANDS];
        check(Flora.measure(frame, Flora.leafPlaces(CrownTests::leaf, frame), FOOT_U, FOOT_V, true, left, right, counts, sizes), "the leaves alone measure as a crown");
        float leavesLowest = sizes[Flora.LOWEST];
        float[] places = Flora.leafPlaces((u, v) -> leaf(u, v) || greatTrunk(u, v), frame);
        check(Flora.measure(frame, places, FOOT_U, FOOT_V, true, left, right, counts, sizes), "with the trunk painted under them as well");
        float band = sizes[Flora.TALL] / Flora.CROWN_BANDS;
        check(sizes[Flora.LOWEST] > leavesLowest - band, "the crown ends where its leaves do, " + leavesLowest + " over the foot, give or take a height of it: "
                + sizes[Flora.LOWEST]);
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        check(Flora.addCrown(mesh, new SpriteGeometry.Mesh(), frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 1f, 0f, 0f, Flora.BARK_OAK, Flora.MAPLE, 7, Flora.OPEN, 1f, 0, 0f),
                "and is made");
        float lowestSprig = Float.MAX_VALUE;
        float[] lit = LightSiteTests.lit(mesh);
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            if ((float) Math.floor((lit[at + 8] + 0.5f) / 32f) == Flora.CROWN + Flora.MAPLE) {
                lowestSprig = Math.min(lowestSprig, mesh.vertices[at + 2]);
            }
        }
        check(lowestSprig > leavesLowest - band - 0.5f, "with no sprig down the trunk: the lowest is at " + lowestSprig + ", the leaves at " + leavesLowest);
    }

    /**
     * A tree's sprig turned down may be left out while the eye is under the tree (the world
     * shader decides it as it draws), and a twig it was on was left bare. Near the eye, where
     * every crown has twigs, each twig ends in the middle of a sprig turned up enough to stay.
     */
    private static void aSprigThatMayBeLeftOutFromUnderTheTreeHasNoTwig() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces(CrownTests::leaf, frame);
        SpriteGeometry.Mesh crown = new SpriteGeometry.Mesh(), wood = new SpriteGeometry.Mesh();
        check(Flora.addCrown(crown, wood, frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 1f, 0f, 0f, Flora.BARK_OAK, Flora.MAPLE, 7,
                Flora.OPEN, 1f, Flora.SCARLET_LEAF, 0f), "a crown near the eye is a crown");
        java.util.List<float[]> sprigs = new java.util.ArrayList<>();
        int turnedDown = 0;
        float[] crownLight = LightSiteTests.lit(crown);
        for (int at = 0; at < crown.floats; at += 6 * SpriteGeometry.VERTEX_FLOATS) {
            if ((float) Math.floor((crownLight[at + 8] + 0.5f) / 32f) == Flora.CORE) {
                continue;
            }
            float x = 0f, y = 0f, up = 0f;
            for (int corner : new int[] {0, 1, 2, 5}) {
                x += crown.vertices[at + corner * SpriteGeometry.VERTEX_FLOATS] / 4f;
                y += crown.vertices[at + corner * SpriteGeometry.VERTEX_FLOATS + 1] / 4f;
                up += crown.vertices[at + corner * SpriteGeometry.VERTEX_FLOATS + 2] / 4f;
            }
            float nx = crown.vertices[at + 9], ny = crown.vertices[at + 10], nz = crown.vertices[at + 11];
            float lit = nz / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            sprigs.add(new float[] {x, y, up, lit});
            turnedDown += lit < Flora.TURNED_DOWN ? 1 : 0;
        }
        check(turnedDown > 0 && turnedDown < sprigs.size(), "some of the crown's " + sprigs.size() + " sprigs are turned down: " + turnedDown);
        int block = 3 * 6 * SpriteGeometry.VERTEX_FLOATS, twigs = 0, bare = 0;
        check(wood.floats > 0 && wood.floats % block == 0, "its wood is twigs of three sides: " + wood.floats + " floats");
        for (int at = 0; at < wood.floats; at += block) {
            float least = Float.MAX_VALUE;
            for (int v = at; v < at + block; v += SpriteGeometry.VERTEX_FLOATS) {
                least = Math.min(least, wood.vertices[v + 4]);
            }
            float x = 0f, y = 0f, up = 0f;
            int far = 0;
            for (int v = at; v < at + block; v += SpriteGeometry.VERTEX_FLOATS) {
                if (wood.vertices[v + 4] > least + 1e-6f) {
                    x += wood.vertices[v];
                    y += wood.vertices[v + 1];
                    up += wood.vertices[v + 2];
                    far++;
                }
            }
            float nearest = Float.MAX_VALUE, lit = 0f;
            for (float[] sprig : sprigs) {
                float away = Math.abs(sprig[0] - x / far) + Math.abs(sprig[1] - y / far) + Math.abs(sprig[2] - up / far);
                if (away < nearest) {
                    nearest = away;
                    lit = sprig[3];
                }
            }
            twigs++;
            bare += nearest > 1e-3f || lit < Flora.TURNED_DOWN - 1e-4f ? 1 : 0;
        }
        check(twigs > 0 && bare == 0, "of its " + twigs + " twigs, " + bare + " end on no sprig, or on one turned down");
    }

    /**
     * A sprite with green leaves over a bark-brown trunk, a bunch of red berries, a mass of
     * autumn orange and one of dark holly green in it, each painted in colours read off the
     * game's own tree sprites, four dots of the sheet to a frame pixel each way.
     */
    private static void aTreesCrownHasNoPlacesOnTheWoodItsSpritePaints() {
        check(CrownWood.wood(0.28f, 0.22f, 0.20f) && CrownWood.wood(0.15f, 0.104f, 0.082f), "a trunk's bark and a limb's shade are wood");
        check(!CrownWood.wood(0.38f, 0.20f, 0.15f) && !CrownWood.wood(0.6f, 0.15f, 0.12f), "a hawthorn's berries are not");
        check(!CrownWood.wood(0.8f, 0.5f, 0.2f) && !CrownWood.wood(0.55f, 0.35f, 0.12f), "nor is an autumn leaf, orange or brown");
        check(!CrownWood.wood(0.35f, 0.5f, 0.2f) && !CrownWood.wood(0.1f, 0.2f, 0.1f), "nor a green one");
        int wide = 64, high = 96;
        java.nio.ByteBuffer sheet = java.nio.ByteBuffer.allocate(wide * high * 4);
        for (int y = 0; y < high; y++) {
            for (int x = 0; x < wide; x++) {
                float[] colour = y >= 60 ? (x >= 26 && x < 38 ? new float[] {0.28f, 0.22f, 0.20f} : null)
                        : x >= 8 && x < 20 && y >= 8 && y < 20 ? new float[] {0.6f, 0.15f, 0.12f}
                        : x >= 40 && x < 56 && y >= 8 && y < 24 ? new float[] {0.8f, 0.5f, 0.2f}
                        : x >= 8 && x < 24 && y >= 36 && y < 52 ? new float[] {0.1f, 0.2f, 0.1f}
                        : x >= 26 && x < 38 && y >= 36 ? new float[] {0.15f, 0.104f, 0.082f}
                        : x >= 44 && x < 48 && y >= 30 && y < 56 ? new float[] {0.28f, 0.22f, 0.20f}
                        : new float[] {0.35f, 0.5f, 0.2f};
                int at = (y * wide + x) * 4;
                if (colour != null) {
                    sheet.put(at, (byte) Math.round(colour[0] * 255f)).put(at + 1, (byte) Math.round(colour[1] * 255f)).put(at + 2, (byte) Math.round(colour[2] * 255f)).put(at + 3, (byte) 255);
                }
            }
        }
        CrownWood.Grid grid = CrownWood.measure(sheet, wide, 0, 0, wide, high);
        check(grid.width() == wide && grid.height() == high, "the sprite is measured dot by dot: " + grid.width() + " by " + grid.height());
        float[] places = {8f, 75f / 4f, 32f / 4f, 44f / 4f, 45f / 4f, 10f, 14f / 4f, 14f / 4f, 48f / 4f, 16f / 4f, 16f / 4f, 44f / 4f, 32f / 4f, 4f / 4f, 1f, 1f};
        float[] kept = CrownWood.leaves(grid, 0f, 0f, wide / 4f, high / 4f, places);
        check(Arrays.equals(kept, new float[] {14f / 4f, 14f / 4f, 48f / 4f, 16f / 4f, 16f / 4f, 44f / 4f, 32f / 4f, 4f / 4f, 1f, 1f}),
                "the places on the trunk, on a limb among the leaves and on a twig too thin to colour a sprig of its own go, and those on the berries, the autumn leaves, the holly and the green stay: " + Arrays.toString(kept));
    }

    /**
     * The sparse heights a great tree's trunk paints are cut only from the foot up. A bush's
     * stems under its leaves are the bush, a crown in two masses joined by a few limbs keeps the
     * lower one, and a crown painted thinly all through is not cut at all.
     */
    private static void onlyATreesSparseFootIsCut() {
        SpriteGeometry.Frame frame = frame();
        float[] left = new float[Flora.CROWN_BANDS], right = new float[Flora.CROWN_BANDS], sizes = new float[Flora.SIZES];
        int[] counts = new int[Flora.CROWN_BANDS];
        float[] bush = Flora.leafPlaces(CrownTests::stemmedBush, frame);
        check(Flora.measure(frame, bush, FOOT_U, FOOT_V, false, left, right, counts, sizes), "a bush on bare stems measures");
        float bushLowest = sizes[Flora.LOWEST], bushBand = sizes[Flora.TALL] / Flora.CROWN_BANDS;
        check(bushLowest < bushBand, "a bush keeps its stems down to its foot: its lowest is " + bushLowest);
        check(Flora.measure(frame, bush, FOOT_U, FOOT_V, true, left, right, counts, sizes) && sizes[Flora.LOWEST] > bushLowest + bushBand,
                "where a tree's sprite of that shape ends over them");
        check(Flora.measure(frame, Flora.leafPlaces(CrownTests::lowerMass, frame), FOOT_U, FOOT_V, true, left, right, counts, sizes), "a crown's lower mass alone measures");
        float lowerLowest = sizes[Flora.LOWEST];
        check(Flora.measure(frame, Flora.leafPlaces((u, v) -> twoTiers(u, v) || greatTrunk(u, v), frame), FOOT_U, FOOT_V, true, left, right, counts, sizes),
                "a crown in two masses joined by limbs measures");
        check(sizes[Flora.LOWEST] < lowerLowest + sizes[Flora.TALL] / Flora.CROWN_BANDS, "and keeps its lower mass: lowest " + sizes[Flora.LOWEST] + ", the mass's " + lowerLowest);
        check(Flora.measure(frame, Flora.leafPlaces(CrownTests::leaf, frame), FOOT_U, FOOT_V, true, left, right, counts, sizes), "a crown painted solid measures");
        float solidLowest = sizes[Flora.LOWEST];
        check(Flora.measure(frame, Flora.leafPlaces((u, v) -> leaf(u, v) && Flora.mix(u, v, 5) % 4 == 0, frame), FOOT_U, FOOT_V, true, left, right, counts, sizes),
                "a crown painted thinly all through measures");
        check(sizes[Flora.LOWEST] < solidLowest + sizes[Flora.TALL] / Flora.CROWN_BANDS, "and is not cut: lowest " + sizes[Flora.LOWEST] + ", painted solid " + solidLowest);
    }

    /** A bush's leaves 90 pixels across, on three bare stems a pixel thick under them, the outer two leaning out to the ground. */
    private static boolean stemmedBush(int u, int v) {
        float du = u - FOOT_U, dv = v - 150f;
        boolean leaves = du * du + dv * dv < 45f * 45f;
        boolean stem = v >= 190 && v <= FOOT_V && (Math.abs(du) < 1f || Math.abs(Math.abs(du) - 20f - (v - 190) * 0.4f) < 1f);
        return leaves || stem;
    }

    /** The lower of two masses of leaves, under the crown's broadest. */
    private static boolean lowerMass(int u, int v) {
        float du = (u - FOOT_U) / 38f, dv = (v - 175f) / 30f;
        return du * du + dv * dv < 1f;
    }

    /** A crown in two masses of leaves, the upper the broader, joined by two limbs a pixel thick. */
    private static boolean twoTiers(int u, int v) {
        float du = u - FOOT_U, dv = v - 75f;
        boolean upper = du * du + dv * dv < 45f * 45f;
        boolean limb = v >= 118 && v <= 148 && Math.abs(Math.abs(du) - (v - 118) * 1.2f) < 1f;
        return upper || limb || lowerMass(u, v);
    }

    /** A trunk 8 pixels thick from the crown's foot to the ground, two limbs 2 pixels thick forking out from it under the crown, and two roots splayed at its foot. */
    private static boolean greatTrunk(int u, int v) {
        float du = u - FOOT_U;
        boolean trunk = Math.abs(du) < 4f && v >= 125 && v <= FOOT_V;
        boolean limb = v >= 125 && v <= 200 && Math.abs(Math.abs(du) - (200 - v) * 0.6f) < 1f;
        boolean root = v >= 225 && v <= FOOT_V && Math.abs(Math.abs(du) - (v - 225) * 2.5f) < 1f;
        return trunk || limb || root;
    }

    /** A bush painted from its foot to two tiles up and nearly a tile out each side of it, as the game's are. */
    private static boolean lowBush(int u, int v) {
        float du = u - FOOT_U, dv = v - 200f;
        return du * du + dv * dv < 40f * 40f;
    }

    /** A crown painted from a third of a wall's height over its foot to half as high again over the wall's top, more than a tile out each side. */
    private static boolean acrossTheTop(int u, int v) {
        float du = u - FOOT_U, dv = v - 144f;
        return du * du + dv * dv < 50f * 50f;
    }

    /**
     * How far past x a mesh's vertices lower than the room's top reach, swaying as far as the
     * strongest wind carries each: the length of a vertex's normal is one and what it sways.
     */
    private static float pastX(SpriteGeometry.Mesh mesh, float x, float base) {
        float most = -Float.MAX_VALUE;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            if (mesh.vertices[at + 2] - base >= Flora.ROOM_UNDER) {
                continue;
            }
            float nx = mesh.vertices[at + 9], ny = mesh.vertices[at + 10], nz = mesh.vertices[at + 11];
            float sway = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) - 1f;
            most = Math.max(most, mesh.vertices[at] + SpriteGeometry.SWAY_REACH * sway - x);
        }
        return most;
    }

    private static void crownBesideAFenceKeepsToItsOwnSideInAnyWind() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces(CrownTests::lowBush, frame);
        float fence = 10.94f;
        float[] room = {-Float.MAX_VALUE, fence, -Float.MAX_VALUE, Float.MAX_VALUE};
        SpriteGeometry.Mesh open = new SpriteGeometry.Mesh(), kept = new SpriteGeometry.Mesh(), wood = new SpriteGeometry.Mesh();
        check(Flora.addCrown(open, new SpriteGeometry.Mesh(), frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 1f, 0f, 0f, Flora.BARK_OAK, Flora.SHRUB, 9, Flora.OPEN, 1f, 0, 0f)
                && Flora.addCrown(kept, new SpriteGeometry.Mesh(), frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 1f, 0f, 0f, Flora.BARK_OAK, Flora.SHRUB, 9, room, 1f, 0, 0f),
                "a bush beside a fence has a crown");
        check(pastX(open, fence, 0f) > 0.3f, "with nothing beside it a bush reaches a good way past where the fence would be: " + pastX(open, fence, 0f));
        check(kept.floats == open.floats, "beside one it has every sprig it had");
        check(pastX(kept, fence, 0f) <= 1e-4f, "and none of them, nor its inside, passes the fence in the strongest wind: " + pastX(kept, fence, 0f));
        float westmost = Float.MAX_VALUE, keptWestmost = Float.MAX_VALUE;
        for (int at = 0; at < open.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            westmost = Math.min(westmost, open.vertices[at]);
            keptWestmost = Math.min(keptWestmost, kept.vertices[at]);
        }
        check(keptWestmost == westmost && westmost < 10.5f - 0.6f, "on its open side it reaches as far as it did: " + keptWestmost + ", " + westmost);
        // A tree low enough to meet the fence top, with limbs and stood under so it has twigs: its wood keeps to its side as well.
        check(Flora.addCrown(new SpriteGeometry.Mesh(), wood, frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 1f, 0.06f, 0.3f, Flora.BARK_OAK, Flora.MAPLE, 9, room, 1f, 0, 0f)
                && wood.floats > 0, "a low tree has wood in its crown");
        // A twig's round is a hundredth of a tile about its line, and its line keeps to the room.
        check(pastX(wood, fence + 0.011f, 0f) <= 1e-4f, "and none of it passes the fence: " + pastX(wood, fence + 0.011f, 0f));
        // A crown from a third of a wall's height to half as high again over its top: what of it is under the top keeps to its side, sprigs that reach down to it whole.
        SpriteGeometry.Mesh spanning = new SpriteGeometry.Mesh(), spanningOpen = new SpriteGeometry.Mesh();
        float[] across = Flora.leafPlaces(CrownTests::acrossTheTop, frame);
        Flora.addCrown(spanning, new SpriteGeometry.Mesh(), frame, across, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.MAPLE, 9, room, 1f, 0, 0f);
        Flora.addCrown(spanningOpen, new SpriteGeometry.Mesh(), frame, across, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.MAPLE, 9, Flora.OPEN, 1f, 0, 0f);
        check(pastX(spanningOpen, fence, 0f) > 0.3f && pastX(spanning, fence, 0f) <= 1e-4f, "a crown across a wall's top keeps to its side under the top: "
                + pastX(spanningOpen, fence, 0f) + " then " + pastX(spanning, fence, 0f));
        float overTop = -Float.MAX_VALUE;
        for (int at = 0; at < spanning.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            overTop = Math.max(overTop, spanning.vertices[at]);
        }
        check(overTop > fence + 0.3f, "and over it reaches across: " + overTop);
        // A crown that rises over a wall's top reaches across it.
        SpriteGeometry.Mesh high = new SpriteGeometry.Mesh(), highOpen = new SpriteGeometry.Mesh();
        Flora.addCrown(high, new SpriteGeometry.Mesh(), frame, PLACES, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.MAPLE, 9, room, 1f, 0, 0f);
        Flora.addCrown(highOpen, new SpriteGeometry.Mesh(), frame, PLACES, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, 1f, 0f, 0f, 0f, Flora.BARK_OAK, Flora.MAPLE, 9, Flora.OPEN, 1f, 0, 0f);
        check(high.floats > 0 && Arrays.equals(high.vertices, 0, high.floats, highOpen.vertices, 0, highOpen.floats),
                "a crown wholly over a wall's top is made as if nothing stood beside it");
    }

    /**
     * A bush's core and sprigs give way to the sprite's paint where the pixel is seen along the
     * game's view. From the eye, looking down into a bush that way, the same test cut a round
     * hole in it, so the test holds only while the picture is the game's own (uGameView).
     */
    private static void aBushIsPaintedAsTheGameDrawsItOnlyInTheGamesOwnView() {
        String shader = PipelineShaders.WORLD_FRAGMENT_MAIN;
        String along = "dot(normalize(uEye - vWorld), GAME_VIEW) > 0.95";
        int seen = 0;
        for (int at = shader.indexOf(along); at >= 0; at = shader.indexOf(along, at + 1)) {
            seen++;
            String line = shader.substring(shader.lastIndexOf('\n', at) + 1, at);
            check(line.contains("uGameView > 0.5 &&"), "painting along the game's view is held to the game's own view: " + line + along);
        }
        check(seen == 2, "the crown's core and its sprigs are painted along the game's view, " + seen + " places");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}

package com.cameraunlock.zomboid;

/**
 * Tests of the plants the game paints that the mod gives a shape of its own in place of the
 * game's cards.
 */
final class PlantKindTests {
    private static final int WIDE = 128, HIGH = 256;
    private static final float FOOT_U = 64f, FOOT_V = 240f;
    private static final int F = SpriteGeometry.VERTEX_FLOATS;

    private PlantKindTests() {}

    static void run() {
        Checks.run("evergreensAreToldByName", PlantKindTests::evergreensAreToldByName);
        Checks.run("anEvergreensPaintedShadowIsNoNeedles", PlantKindTests::anEvergreensPaintedShadowIsNoNeedles);
        Checks.run("deadTreesAreToldByName", PlantKindTests::deadTreesAreToldByName);
        Checks.run("aBareCrownIsWoodAloneOutToTheOutline", PlantKindTests::aBareCrownIsWoodAloneOutToTheOutline);
        Checks.run("aBareCrownNeedsABole", PlantKindTests::aBareCrownNeedsABole);
        Checks.run("aBareCrownFarOffHasNoTwigs", PlantKindTests::aBareCrownFarOffHasNoTwigs);
        Checks.run("aBareCrownHasLimbsWhateverItsDistance", PlantKindTests::aBareCrownHasLimbsWhateverItsDistance);
        Checks.run("leafyWeedsAndFernsAreToldByName", PlantKindTests::leafyWeedsAndFernsAreToldByName);
        Checks.run("gardenPlantsAreToldByName", PlantKindTests::gardenPlantsAreToldByName);
        Checks.run("fallenSticksAreToldByName", PlantKindTests::fallenSticksAreToldByName);
        Checks.run("fallenSticksLieOnTheirSquaresGround", PlantKindTests::fallenSticksLieOnTheirSquaresGround);
        Checks.run("ivyOnTheGroundLiesRoundTheBoleAndKeepsToItsBounds", PlantKindTests::ivyOnTheGroundLiesRoundTheBoleAndKeepsToItsBounds);
        Checks.run("cropsAreToldByName", PlantKindTests::cropsAreToldByName);
        Checks.run("cropStalksStandOnTheirSquareAsTallAsTheCropAndTakeTheSpritesColour", PlantKindTests::cropStalksStandOnTheirSquareAsTallAsTheCropAndTakeTheSpritesColour);
        Checks.run("wheatThinsWithDistanceAndCornDoesNot", PlantKindTests::wheatThinsWithDistanceAndCornDoesNot);
        Checks.run("aCropIsAsTallAsItsSpritePaintsIt", PlantKindTests::aCropIsAsTallAsItsSpritePaintsIt);
        Checks.run("aCropsColourIsTakenOffItsOutline", PlantKindTests::aCropsColourIsTakenOffItsOutline);
        Checks.run("aCropsCardStandsAsTallAsTheCrop", PlantKindTests::aCropsCardStandsAsTallAsTheCrop);
        Checks.run("wheatsCardsRiseAmongItsThinnedStalks", PlantKindTests::wheatsCardsRiseAmongItsThinnedStalks);
    }

    private static void evergreensAreToldByName() {
        for (String name : new String[] {"e_virginiapine_1_3", "e_virginiapineJUMBO_1_1", "e_canadianhemlock_1_2", "e_americanholly_1_0", "e_americanhollyJUMBOXL_1_0"}) {
            check(Flora.evergreen(name), name + " keeps its needles all year");
        }
        for (String name : new String[] {"e_redmapleJUMBO_1_1", "e_dogwood_1_2", "e_riverbirchJUMBOXL_1_0", "f_bushes_1_4", null}) {
            check(!Flora.evergreen(name), name + " is no evergreen");
        }
    }

    private static SpriteGeometry.Frame frame() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 1f;
        frame.width = WIDE;
        frame.height = HIGH;
        frame.uStart = 0.25f;
        frame.uEnd = 0.5f;
        frame.vStart = 0.5f;
        frame.vEnd = 1f;
        frame.open = 1f;
        frame.light(0.8f, 0.8f, 0.8f);
        return frame;
    }

    private static boolean conifer(int u, int v) {
        float du = Math.abs(u - FOOT_U), up = FOOT_V - v;
        boolean shadow = Math.abs(up) < 14f && du < 50f;
        boolean needles = up > 20f && up < 200f && du < (200f - up) * 0.25f;
        return shadow || needles;
    }

    private static void anEvergreensPaintedShadowIsNoNeedles() {
        SpriteGeometry.Frame frame = frame();
        float[] all = Flora.leafPlaces(PlantKindTests::conifer, frame);
        float[] kept = Flora.above(all, frame, FOOT_V, 0.3f);
        float up = ViewCamera.LEVEL_HEIGHT / SpriteGeometry.PIXELS_PER_LEVEL;
        check(kept.length > 0 && kept.length < all.length, "some places are left, and not all");
        for (int i = 0; i < kept.length; i += 2) {
            check((FOOT_V - kept[i + 1]) * up >= 0.3f, "every place left is 0.3 tiles over the foot or more");
        }
        int needles = 0;
        for (int i = 0; i < all.length; i += 2) {
            needles += (FOOT_V - all[i + 1]) * up >= 0.3f ? 1 : 0;
        }
        check(kept.length / 2 == needles, "and every such place is kept: " + kept.length / 2 + " of " + needles);
    }

    private static void deadTreesAreToldByName() {
        for (int place = 0; place < 24; place++) {
            check(Flora.deadTree("fencing_burnt_01_" + place) == (place >= 16 && place <= 19), "fencing_burnt_01_" + place + (place >= 16 && place <= 19 ? " is" : " is not") + " a dead tree");
        }
        check(!Flora.deadTree(null) && !Flora.deadTree("fencing_burnt_02_16"), "no other sheet holds the dead trees");
    }

    private static boolean branches(int u, int v) {
        float du = Math.abs(u - FOOT_U), up = FOOT_V - v;
        if (up < 0f || up > 220f) {
            return false;
        }
        return du < 4f || up > 90f && du < (up - 90f) * 0.45f && (int) (du + up * 0.3f) % 9 < 2;
    }

    private static float[] bare(SpriteGeometry.Mesh leaves, SpriteGeometry.Mesh wood, float detail, float boleRadius) {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces(PlantKindTests::branches, frame);
        boolean made = Flora.addCrown(leaves, wood, frame, places, 10.5f, 20.5f, 0f, FOOT_U, FOOT_V, detail, 0f, boleRadius, 1.2f, Flora.BARK_OAK, Flora.BARE, 7, Flora.OPEN, 1f, 0, 0f);
        return made ? places : null;
    }

    private static void aBareCrownIsWoodAloneOutToTheOutline() {
        SpriteGeometry.Mesh leaves = new SpriteGeometry.Mesh(), wood = new SpriteGeometry.Mesh();
        float[] places = bare(leaves, wood, 1f, 0.1f);
        check(places != null, "a dead tree with a bole has a crown");
        check(leaves.floats == 0, "of no leaves at all, not " + leaves.floats / F + " vertices of them");
        check(wood.floats > 0, "but wood");
        float[] reachLeft = new float[Flora.CROWN_BANDS], reachRight = new float[Flora.CROWN_BANDS], sizes = new float[Flora.SIZES];
        check(Flora.measure(frame(), places, FOOT_U, FOOT_V, reachLeft, reachRight, sizes), "its outline measures");
        float widest = 0f, highest = 0f;
        for (int at = 0; at < wood.floats; at += F) {
            float dx = wood.vertices[at] - 10.5f, dy = wood.vertices[at + 1] - 20.5f;
            widest = Math.max(widest, (float) Math.sqrt(dx * dx + dy * dy));
            highest = Math.max(highest, wood.vertices[at + 2]);
        }
        check(widest > 0.7f * sizes[Flora.WIDEST], "its wood reaches out most of the way to where the sprite paints branches: " + widest + " of " + sizes[Flora.WIDEST]);
        check(widest < 1.15f * sizes[Flora.WIDEST], "and no further than a little past it: " + widest + " of " + sizes[Flora.WIDEST]);
        check(highest > 0.75f * sizes[Flora.HIGHEST] && highest < 1.2f * sizes[Flora.HIGHEST], "and up to its top: " + highest + " of " + sizes[Flora.HIGHEST]);
    }

    private static void aBareCrownNeedsABole() {
        SpriteGeometry.Mesh leaves = new SpriteGeometry.Mesh(), wood = new SpriteGeometry.Mesh();
        check(bare(leaves, wood, 1f, 0f) == null, "with no bole to grow from there is no bare crown");
        check(leaves.floats == 0 && wood.floats == 0, "and nothing is made");
    }

    private static void aBareCrownFarOffHasNoTwigs() {
        SpriteGeometry.Mesh nearWood = new SpriteGeometry.Mesh(), farWood = new SpriteGeometry.Mesh();
        bare(new SpriteGeometry.Mesh(), nearWood, 1f, 0.1f);
        bare(new SpriteGeometry.Mesh(), farWood, Crowns.detail(Crowns.REACH), 0.1f);
        check(farWood.floats > 0, "far off it still has its limbs and branches");
        check(farWood.floats * 2 < nearWood.floats, "but not its twigs, which are most of it near: " + farWood.floats / F + " against " + nearWood.floats / F);
    }

    private static void aBareCrownHasLimbsWhateverItsDistance() {
        check(Crowns.limbed(Crowns.REACH * 4f, Flora.BARE, 1f), "a bare crown is its limbs: it has them at any distance");
        check(!Crowns.limbed(Crowns.REACH * 4f, Flora.MAPLE, 1f) && Crowns.limbed(4f, Flora.MAPLE, 1f) && Crowns.limbed(Crowns.REACH * 4f, Flora.MAPLE, 0.5f), "a crown in leaf has them only near, and one losing its leaves at any distance");
    }

    private static void gardenPlantsAreToldByName() {
        for (String name : new String[] {"vegetation_gardening_01_19", "vegetation_gardening_trampled_01_0", "vegetation_farming_01_20", "vegetation_farming_dead_01b_3"}) {
            check(Flora.gardenPlant(name), name + " grows in a bed");
        }
        for (String name : new String[] {"vegetation_farm_01_33", "vegetation_ornamental_01_16", "vegetation_foliage_01_0", null}) {
            check(!Flora.gardenPlant(name), name + " is no garden's plant");
        }
    }

    private static void leafyWeedsAndFernsAreToldByName() {
        int[] leafy = {16, 17, 20, 23, 32, 39, 49, 55, 64};
        int[] other = {0, 7, 8, 15, 18, 19, 24, 31, 36, 40, 47, 48, 56, 63, 65};
        for (int place : leafy) {
            check(Flora.leafyWeed("d_plants_1_" + place), "d_plants_1_" + place + " is a leafy wild plant");
        }
        for (int place : other) {
            check(!Flora.leafyWeed("d_plants_1_" + place), "d_plants_1_" + place + " is a seedling, a dried plant, a flower, a reed or a thin sapling, and no bush");
        }
        check(!Flora.leafyWeed(null) && !Flora.leafyWeed("d_plants_2_16"), "no other sheet holds them");
        for (int place = 40; place < 92; place++) {
            boolean fern = place >= 48 && place <= 55 || place >= 80 && place <= 87;
            check(Flora.fernSprite("d_generic_1_" + place) == fern, "d_generic_1_" + place + (fern ? " is" : " is not") + " a fern");
        }
    }

    private static void fallenSticksAreToldByName() {
        for (int place = 0; place < 48; place++) {
            boolean fallen = place == 8 || place >= 11 && place <= 15 || place == 44 || place == 45;
            check(Flora.fallenWood("d_generic_1_" + place) == fallen, "d_generic_1_" + place + (fallen ? " is" : " is not") + " fallen sticks");
        }
        check(!Flora.fallenWood(null) && !Flora.fallenWood("d_generic_2_8"), "no other sheet holds fallen sticks");
    }

    private static void fallenSticksLieOnTheirSquaresGround() {
        // A square on a slope, two levels up: its ground rises 0.3 from its west side to its east.
        float base = 2f * ViewCamera.LEVEL_HEIGHT;
        float[] lift = {0.1f, 0.4f, 0.4f, 0.1f};
        for (int seed = 0; seed < 40; seed++) {
            SpriteGeometry.Mesh wood = new SpriteGeometry.Mesh();
            int sticks = Flora.addFallenWood(wood, 7, 9, base, lift, Flora.mix(seed, 3, 5), frame());
            check(sticks >= 2 && sticks <= 5 && wood.floats > 0, "a square of fallen wood is a few sticks");
            for (int at = 0; at < wood.floats; at += F) {
                float x = wood.vertices[at], y = wood.vertices[at + 1], up = wood.vertices[at + 2];
                check(x > 6.97f && x < 8.03f && y > 8.97f && y < 10.03f, "every stick lies on its own square, not at " + x + ", " + y);
                float ground = base + 0.1f + 0.3f * Math.clamp(x - 7f, 0f, 1f);
                check(up > ground - 0.05f && up < ground + 0.12f, "and on its ground, not " + (up - ground) + " off it");
                check(wood.vertices[at + SpriteGeometry.WAY_AT] == SpriteGeometry.WOOD && wood.vertices[at + SpriteGeometry.ALONG_AT] < -SnowShader.UNSNOWED,
                        "a stick lying in the snow holds no snow of its own");
            }
        }
    }

    private static void ivyOnTheGroundLiesRoundTheBoleAndKeepsToItsBounds() {
        float base = 0f, radius = 0.12f, reach = 0.9f;
        float[] lift = {0.1f, 0.3f, 0.3f, 0.1f};
        for (int seed = 0; seed < 30; seed++) {
            SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
            // Free to spread east and south onto open ground, held inside its square west and north.
            int triangles = Flora.addGroundIvy(mesh, 20.5f, 30.5f, 20, 30, base, lift, radius, reach, 20.03f, 21.6f, 30.03f, 31.6f, Flora.mix(seed, 1, 2), frame());
            check(triangles > 0 && mesh.floats == triangles * 3 * F, "ground ivy is " + triangles + " triangles, all of them made");
            float farthest = 0f;
            for (int at = 0; at < mesh.floats; at += F) {
                float x = mesh.vertices[at], y = mesh.vertices[at + 1], up = mesh.vertices[at + 2];
                check(x >= 20.03f && x <= 21.6f && y >= 30.03f && y <= 31.6f, "every corner keeps to its bounds, not at " + x + ", " + y);
                float dx = x - 20.5f, dy = y - 30.5f, out = (float) Math.sqrt(dx * dx + dy * dy);
                check(out >= radius, "and is clear of the bark, not " + out + " from the bole's middle");
                float ground = 0.1f + 0.2f * Math.clamp(x - 20f, 0f, 1f);
                check(up > ground && up < ground + 0.08f, "and lies on the ground, not " + (up - ground) + " over it");
                farthest = Math.max(farthest, out);
            }
            check(farthest > 0.4f && farthest <= radius + 0.01f + reach * 1.15f, "it spreads out from the bole, as far as " + farthest);
        }
        // It thins towards its rim: the share of the way out is what thins it, carried as the normal's length.
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        Flora.addGroundIvy(mesh, 20.5f, 30.5f, 20, 30, base, lift, radius, reach, 0f, 100f, 0f, 100f, 3, frame());
        float nearTake = Float.MAX_VALUE, farTake = 0f;
        for (int at = 0; at < mesh.floats; at += F) {
            float dx = mesh.vertices[at] - 20.5f, dy = mesh.vertices[at + 1] - 30.5f, out = (float) Math.sqrt(dx * dx + dy * dy);
            float take = (float) Math.sqrt(mesh.vertices[at + 9] * mesh.vertices[at + 9] + mesh.vertices[at + 10] * mesh.vertices[at + 10] + mesh.vertices[at + 11] * mesh.vertices[at + 11]) - 1f;
            if (out < radius + 0.02f) {
                nearTake = Math.min(nearTake, take);
            }
            farTake = Math.max(farTake, take);
        }
        check(nearTake < 0.01f && farTake > 0.5f && farTake < 0.75f, "thick at the bole and thinning to its rim, without the whole wind a plant's top takes: " + nearTake + " to " + farTake);
    }

    private static void cropsAreToldByName() {
        check(Crops.of("vegetation_farm_01_0") == Crops.WHEAT && Crops.of("vegetation_farm_01_5") == Crops.WHEAT, "the first six of the farm's sheet are wheat");
        check(Crops.of("vegetation_farm_01_32") == Crops.CORN && Crops.of("vegetation_farm_01_39") == Crops.CORN, "32 to 39 are dry corn");
        check(Crops.of("vegetation_farm_01_40") == Crops.LEAFY && Crops.of("vegetation_farm_01_47") == Crops.LEAFY, "40 to 47 are a dead leafy crop");
        for (String name : new String[] {"vegetation_farm_01_6", "vegetation_farm_01_8", "vegetation_farm_01_16", "vegetation_farm_01_31", "vegetation_farm_01_48",
                "vegetation_farming_01_3", null}) {
            check(Crops.of(name) == Crops.NONE, name + " is no standing crop: hay, a bale, another sheet");
        }
    }

    private static boolean stalks(int u, int v) {
        return v < FOOT_V && v > FOOT_V - 180f && (Math.abs(u - 40) < 3 || Math.abs(u - 64) < 3 || Math.abs(u - 88) < 3);
    }

    private static SpriteGeometry.Mesh crop(int kind, float away, float tall, float[] places) {
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        Crops.grow(geometry, mesh, frame(), places, kind, 30, 40, tall, away, 0.04f, 0.96f, 0.04f, 0.96f);
        return mesh;
    }

    private static void cropStalksStandOnTheirSquareAsTallAsTheCropAndTakeTheSpritesColour() {
        SpriteGeometry.Frame frame = frame();
        float[] places = Flora.leafPlaces(PlantKindTests::stalks, frame);
        for (int kind : new int[] {Crops.WHEAT, Crops.CORN, Crops.LEAFY}) {
            float tall = kind == Crops.CORN ? 2f : 0.9f;
            SpriteGeometry.Mesh mesh = crop(kind, 3f, tall, places);
            check(mesh.floats > 0, "crop " + kind + " is grown");
            float highest = 0f;
            for (int at = 0; at < mesh.floats; at += F) {
                float[] v = mesh.vertices;
                float rootX = v[at] + v[at + 12], rootY = v[at + 1] + v[at + 13];
                check(rootX > 30f && rootX < 31f && rootY > 40f && rootY < 41f, "crop " + kind + ": every plant stands on its own square, not at " + rootX + ", " + rootY);
                highest = Math.max(highest, v[at + 2]);
                // The colour is read at a place the sprite paints, never where it is empty.
                int u = Math.round((v[at + 3] - frame.uStart) / (frame.uEnd - frame.uStart) * frame.width - 0.5f);
                int down = Math.round((v[at + 4] - frame.vStart) / (frame.vEnd - frame.vStart) * frame.height - 0.5f);
                boolean painted = false;
                for (int du = -1; du <= 1 && !painted; du++) {
                    for (int dv = -1; dv <= 1 && !painted; dv++) {
                        painted = stalks(u + du, down + dv);
                    }
                }
                check(painted, "crop " + kind + ": a stalk takes its colour from where the sprite paints the crop, not from " + u + ", " + down);
            }
            check(highest > 0.75f * tall && highest < 1.45f * tall, "crop " + kind + " stands about as tall as it should: " + highest + " for " + tall);
        }
    }

    private static void wheatThinsWithDistanceAndCornDoesNot() {
        float[] places = Flora.leafPlaces(PlantKindTests::stalks, frame());
        SpriteGeometry.Mesh nearWheat = crop(Crops.WHEAT, 3f, 0.9f, places), farWheat = crop(Crops.WHEAT, 25f, 0.9f, places);
        check(farWheat.floats > 0 && farWheat.floats * 3 < nearWheat.floats, "wheat far off is fewer and simpler stalks: " + farWheat.floats / F + " against " + nearWheat.floats / F);
        SpriteGeometry.Mesh nearCorn = crop(Crops.CORN, 3f, 2f, places), farCorn = crop(Crops.CORN, 25f, 2f, places);
        check(farCorn.floats > 0 && farCorn.floats < nearCorn.floats, "corn far off is simpler stalks");
        check(stems(farCorn) == stems(nearCorn), "but as many of them: a square of corn is only four stalks");
    }

    private static int stems(SpriteGeometry.Mesh mesh) {
        int feet = 0;
        for (int at = 0; at < mesh.floats; at += F) {
            if (mesh.vertices[at + 10] == 2f * Grass.STEM) {
                feet++;
            }
        }
        return feet / 3;
    }

    private static void aCropIsAsTallAsItsSpritePaintsIt() {
        check(Math.abs(Crops.tall(0.5f) - 0.5f * ViewCamera.LEVEL_HEIGHT) < 1e-5f, "half a level high in the sprite is half a level high in the world");
        check(Crops.tall(0.01f) >= 0.3f && Crops.tall(5f) <= 2.6f, "but never next to nothing high, nor higher than corn grows");
    }

    /** The game's field wheat as its sheet holds it: a 2x frame, the crop painted from row 105 down over the whole square. */
    private static SpriteGeometry.Frame wheatFrame() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.left = 0f;
        frame.top = 105f;
        frame.width = 126f;
        frame.height = 149f;
        return frame;
    }

    private static void aCropsCardStandsAsTallAsTheCrop() {
        SpriteGeometry.Frame frame = wheatFrame();
        float[] box = new float[4];
        float share = Crops.cardShare(frame, box);
        float card = (SpriteGeometry.frameV(frame, 0.5f, 0.5f, 0f) - frame.top) * SpriteGeometry.TILES_PER_PIXEL_UP / frame.scale;
        check(card > 1.4f, "stood through its square's middle as painted, the wheat card is " + card + " high, higher than the crop");
        check(share > 0.6f && share < 0.85f, "so it stands lower: " + share + " of that");
        check(Math.abs(card * share - Crops.tall(box[3])) < 1e-4f, "as tall as the crop: " + card * share + " for " + Crops.tall(box[3]));
        check(Crops.tall(box[3]) > 0.9f && Crops.tall(box[3]) < 1.3f, "which is about a metre: " + Crops.tall(box[3]));
        frame.top = 188f;
        frame.height = 4f;
        check(Crops.cardShare(frame, box) == 1f, "a sprite whose outline says too little is stood as painted");
    }

    private static void wheatsCardsRiseAmongItsThinnedStalks() {
        check(Crops.amongStalks(Crops.WHEAT, Crops.WHEAT_CARDS_FROM) == 0f && Crops.amongStalks(Crops.WHEAT, 1f) == 0f, "near the eye wheat is its stalks alone");
        check(Crops.amongStalks(Crops.WHEAT, 12f) > 0.2f && Crops.amongStalks(Crops.WHEAT, 12f) < Crops.amongStalks(Crops.WHEAT, 20f), "further off its cards rise among them");
        check(Crops.amongStalks(Crops.WHEAT, Crops.WHEAT_CARDS_FULL) == 1f && Crops.amongStalks(Crops.WHEAT, Crops.REACH) == 1f, "to the crop's height before the stalks end");
        check(Crops.amongStalks(Crops.CORN, 20f) == 0f && Crops.amongStalks(Crops.LEAFY, 20f) == 0f, "corn and the leafy crop keep every stalk, and no cards among them");
    }

    private static void aCropsColourIsTakenOffItsOutline() {
        SpriteGeometry.Frame frame = frame();
        float[] all = Flora.leafPlaces(PlantKindTests::stalks, frame), kept = Flora.inside(all, PlantKindTests::stalks);
        check(kept.length > 0 && kept.length < all.length, "the inside of the stalks is kept and their edges are not: " + kept.length / 2 + " of " + all.length / 2);
        for (int i = 0; i < kept.length; i += 2) {
            int u = (int) kept[i], v = (int) kept[i + 1];
            check(stalks(u - 1, v) && stalks(u + 1, v) && stalks(u, v - 1) && stalks(u, v + 1), "every place kept is painted on all four sides");
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}

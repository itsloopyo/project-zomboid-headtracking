package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import zombie.core.properties.PropertyContainer;
import zombie.iso.sprite.IsoSprite;

/**
 * Tests of where shots strike: what each surface is read as being made of, where on a wall
 * the impact is put, what flies from each kind of surface and where it comes down, and that
 * the game's own look shows none of it.
 */
final class ImpactTests {
    private ImpactTests() {}

    static void run() {
        Checks.run("everyWordTheGameHasForWhatAThingIsMadeOfIsRead", ImpactTests::everyWordTheGameHasForWhatAThingIsMadeOfIsRead);
        Checks.run("groundIsReadByTheGamesWordForItsFloor", ImpactTests::groundIsReadByTheGamesWordForItsFloor);
        Checks.run("aSpriteThatSaysNothingIsReadAsThePictureDrawsIt", ImpactTests::aSpriteThatSaysNothingIsReadAsThePictureDrawsIt);
        Checks.run("aShotIntoOpenGroundUnderSnowThrowsUpSnow", ImpactTests::aShotIntoOpenGroundUnderSnowThrowsUpSnow);
        Checks.run("onlyWhatKeepsAMarkIsHoled", ImpactTests::onlyWhatKeepsAMarkIsHoled);
        Checks.run("aShotStrikesTheNearFaceOfAWall", ImpactTests::aShotStrikesTheNearFaceOfAWall);
        Checks.run("everyKindHasWhatFliesFromItWithinItsVertices", ImpactTests::everyKindHasWhatFliesFromItWithinItsVertices);
        Checks.run("piecesFallToTheFloorUnderAWallAndStayNearIt", ImpactTests::piecesFallToTheFloorUnderAWallAndStayNearIt);
        Checks.run("theShaderMovesWhatFliesAsTheseTestsDo", ImpactTests::theShaderMovesWhatFliesAsTheseTestsDo);
        Checks.run("eachImpactStartsWhenItStrikesAndHolesAreKept", ImpactTests::eachImpactStartsWhenItStrikesAndHolesAreKept);
        Checks.run("leavingTheSaveForgetsItsHoles", ImpactTests::leavingTheSaveForgetsItsHoles);
        Checks.run("theGamesOwnLookShowsNoImpacts", ImpactTests::theGamesOwnLookShowsNoImpacts);
    }

    /** The game's MaterialType words, every one of zombie.iso.enums.MaterialType. */
    private static final String[] WORDS = {"Default", "Flesh", "Flesh_Hollow", "Concrete", "Plaster", "Stone", "Wood", "Wood_Solid", "Brick", "Metal",
            "Metal_Large", "Metal_Light", "Metal_Solid", "Glass", "Glass_Light", "Glass_Solid", "Cinderblock", "Plastic", "Ceramic", "Rubber", "Fabric",
            "Carpet", "Dirt", "Grass", "Gravel", "Sand", "Snow"};

    private static void everyWordTheGameHasForWhatAThingIsMadeOfIsRead() {
        for (String word : WORDS) {
            check(word.equals("Default") == (Impacts.ofMaterialType(word) == Impacts.NONE), word + " is read as something, and only Default as nothing");
        }
        check(Impacts.ofMaterialType("Brick") == Impacts.MASONRY && Impacts.ofMaterialType("Plaster") == Impacts.MASONRY, "brick and plaster are masonry");
        check(Impacts.ofMaterialType("Wood_Solid") == Impacts.WOOD, "solid wood is wood");
        check(Impacts.ofMaterialType("Metal_Light") == Impacts.METAL, "sheet metal is metal");
        check(Impacts.ofMaterialType("Flesh_Hollow") == Impacts.FLESH, "flesh is flesh");
        check(Impacts.ofMaterialType("Gravel") == Impacts.SOIL, "gravel is thrown up as earth is");
        check(Impacts.ofMaterialType("Carpet") == Impacts.SOFT, "carpet is soft");
        check(Impacts.ofMaterialType(null) == Impacts.NONE && Impacts.ofMaterialType("Unobtainium") == Impacts.NONE, "nothing, and a word the game has not got, is nothing");
    }

    private static void groundIsReadByTheGamesWordForItsFloor() {
        for (int road = 1; road <= 7; road++) {
            check(Impacts.ofFloorMaterial("Road_0" + road) == Impacts.MASONRY, "Road_0" + road + ", a road or a pavement, is struck as masonry");
        }
        for (String earth : new String[] {"Grass_Dark", "Grass_Medium", "Grass_Light", "Dirt", "Dirt_Grass", "Clay", "Sand", "Burnt"}) {
            check(Impacts.ofFloorMaterial(earth) == Impacts.SOIL, earth + " is earth");
        }
        check(Impacts.ofFloorMaterial("Water") == Impacts.WATER, "water is water");
        check(Impacts.ofFloorMaterial(null) == Impacts.NONE, "a floor with no word says nothing");
    }

    private static IsoSprite sprite(String name, String... properties) {
        Map<String, String> all = new HashMap<>();
        for (int i = 0; i < properties.length; i += 2) {
            all.put(properties[i], properties[i + 1]);
        }
        return new IsoSprite(name, new PropertyContainer(all));
    }

    private static void aSpriteThatSaysNothingIsReadAsThePictureDrawsIt() {
        check(Impacts.of(sprite("walls_interior_house_01_4", "MaterialType", "Plaster"), false) == Impacts.MASONRY, "an inside wall the game calls plaster");
        check(Impacts.of(sprite("walls_exterior_wooden_01_5"), false) == Impacts.WOOD, "a wall of boards that says nothing is wood, as the picture boards it");
        check(Impacts.of(sprite("walls_exterior_wooden_01_5", "MaterialType", "Brick"), false) == Impacts.MASONRY, "the game's own word comes first");
        check(Impacts.of(sprite("walls_exterior_house_07_3"), false) == Impacts.MASONRY, "a painted wall that says nothing is plaster");
        check(Impacts.of(sprite("floors_interior_carpet_01_3"), true) == Impacts.SOFT, "a carpet");
        check(Impacts.of(sprite("floors_interior_tilesandwood_01_45"), true) == Impacts.WOOD, "a floor of boards from the sheet of tiles and wood");
        check(Impacts.of(sprite("floors_interior_tilesandwood_01_10"), true) == Impacts.MASONRY, "and a tiled floor from it");
        check(Impacts.of(sprite("blends_natural_01_16", "FloorMaterial", "Grass_Light"), true) == Impacts.SOIL, "grass");
        check(Impacts.of(sprite("blends_street_01_0", "FloorMaterial", "Road_01"), true) == Impacts.MASONRY, "a road");
        check(Impacts.of(sprite("blends_natural_02_0", "water", ""), true) == Impacts.WATER, "water by its flag alone");
        check(Impacts.of(sprite("someone_elses_sheet_0"), true) == Impacts.MASONRY, "a floor that says nothing at all is concrete");
        check(Impacts.of(null, false) == Impacts.MASONRY, "and so is a wall with no sprite");
    }

    private static void aShotIntoOpenGroundUnderSnowThrowsUpSnow() {
        check(Impacts.underSnow(Impacts.SOIL, Impacts.SNOW_SHOWS) == Impacts.SNOW, "earth under snow");
        check(Impacts.underSnow(Impacts.MASONRY, 1f) == Impacts.SNOW, "a road under snow");
        check(Impacts.underSnow(Impacts.SOIL, Impacts.SNOW_SHOWS - 0.01f) == Impacts.SOIL, "a dusting leaves the earth showing");
        check(Impacts.underSnow(Impacts.WATER, 1f) == Impacts.WATER, "snow does not lie on water");
    }

    private static void onlyWhatKeepsAMarkIsHoled() {
        for (int kind = 0; kind < Impacts.KINDS; kind++) {
            boolean keeps = kind == Impacts.MASONRY || kind == Impacts.WOOD || kind == Impacts.METAL || kind == Impacts.SOFT;
            check(Impacts.holed(kind) == keeps, "kind " + kind + (keeps ? " keeps a hole" : " keeps none"));
        }
    }

    private static void aShotStrikesTheNearFaceOfAWall() {
        float edge = 3f;
        check(Math.abs(Impacts.toSkin(edge, 1f) - (edge - WorldScene.WALL_HALF)) < 1e-6f, "straight on, the face is a skin's width short of the edge");
        check(Math.abs(Impacts.toSkin(edge, -0.5f) - (edge - 2f * WorldScene.WALL_HALF)) < 1e-6f, "at a slant, further along the line, whichever way it crosses");
        check(Impacts.toSkin(0.01f, 1f) == 0f, "a muzzle against the wall strikes it where it is");
    }

    private static void everyKindHasWhatFliesFromItWithinItsVertices() {
        int first = 0;
        for (int group = 0; group < ImpactDraw.MOST.length; group++) {
            first += ImpactDraw.MOST[group];
        }
        check(first == ImpactDraw.PARTICLES, "the groups fill the vertices");
        for (int kind = 0; kind < Impacts.KINDS; kind++) {
            ImpactDraw.Spray[] sprays = ImpactDraw.SPRAYS[kind];
            check(sprays != null && sprays.length == ImpactDraw.MOST.length, "kind " + kind + " has pieces, puffs and sparks");
            int shown = 0;
            for (int group = 0; group < sprays.length; group++) {
                ImpactDraw.Spray spray = sprays[group];
                shown += spray.count();
                check(spray.count() <= ImpactDraw.MOST[group], "kind " + kind + " group " + group + " fits its vertices");
                check(spray.count() == 0 || spray.lifeMost() + 0.03f <= Impacts.LONGEST, "kind " + kind + " group " + group + " is gone before its impact is let go");
                check(spray.count() == 0 || spray.sizeLeast() > 0f && spray.speedMost() >= spray.speedLeast(), "kind " + kind + " group " + group + " has a size and a speed");
                check((spray.glowRed() > 0f) == (kind == Impacts.METAL && group == 2 && spray.count() > 0), "only metal's sparks give light of their own");
                check(spray.blood() == (kind == Impacts.FLESH && spray.count() > 0), "only flesh bleeds");
            }
            check(shown > 0, "something flies from kind " + kind);
        }
    }

    /** Where the vertex shader puts what left uAt at v0 after t seconds: see place() in its text. */
    private static float[] place(float[] at, float[] v0, float k, float g, float t) {
        float s = (float) ((1.0 - Math.exp(-k * t)) / k);
        return new float[] {at[0] + v0[0] * s, at[1] + v0[1] * s, at[2] + v0[2] * s + g / k * (s - t)};
    }

    private static void piecesFallToTheFloorUnderAWallAndStayNearIt() {
        // A wall facing +x, struck 1.2 tiles over its floor by a rifle's round coming straight at it.
        float[] at = {10f, 20f, 1.2f};
        float floor = 0f, scale = ImpactFinder.RIFLE;
        for (int kind = 0; kind < Impacts.KINDS; kind++) {
            for (ImpactDraw.Spray spray : ImpactDraw.SPRAYS[kind]) {
                if (spray.count() == 0 || !spray.rests()) {
                    continue;
                }
                float k = Math.max(spray.drag(), 0.05f), g = 9.8f * spray.fall();
                // Fastest straight out along the normal, slightly up: as far as anything of the kind goes.
                float speed = spray.speedMost() * (float) Math.sqrt(scale);
                float[] v0 = {speed * 0.97f, 0f, speed * 0.24f};
                float[] end = place(at, v0, k, g, spray.lifeMost());
                check(end[2] < floor, "kind " + kind + ": the fastest piece comes down to the floor before it is gone: " + end[2]);
                // Where it came down: halved towards the crossing, as the shader does it.
                float low = 0f, high = spray.lifeMost();
                for (int j = 0; j < 8; j++) {
                    float middle = 0.5f * (low + high);
                    if (place(at, v0, k, g, middle)[2] < floor) {
                        high = middle;
                    } else {
                        low = middle;
                    }
                }
                float out = place(at, v0, k, g, high)[0] - at[0];
                check(out > 0.1f && out < 3f, "kind " + kind + ": it lands between a tenth of a tile and three tiles out from the wall: " + out);
            }
        }
    }

    private static void theShaderMovesWhatFliesAsTheseTestsDo() throws IOException {
        String source;
        try (InputStream in = ImpactDraw.class.getResourceAsStream("ImpactDraw.class")) {
            source = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
        for (String line : new String[] {"float s = (1.0 - exp(-k * t)) / k;", "p.z += g / k * (s - t);", "vec3 v0 = dir * mix(uSpeed.x, uSpeed.y, rnd(i, 4.0)) * sqrt(uScale);",
                "if (place(v0, k, g, middle).z < uFloor) {"}) {
            check(source.contains(line), "the vertex shader has, word for word: " + line);
        }
    }

    private static Impacts.Struck struck(float x, boolean hole) {
        return new Impacts.Struck(x, 0f, 1f, -1f, 0f, 0f, 1f, 0f, 0f, 0f, Impacts.MASONRY, 1f, hole, 0f, 1f, 0f);
    }

    private static void eachImpactStartsWhenItStrikesAndHolesAreKept() {
        Impacts.forget();
        Impacts.take();
        long before = System.nanoTime();
        Impacts.add(struck(1f, true));
        Impacts.add(struck(2f, false));
        Impacts.add(struck(3f, true));
        long after = System.nanoTime();
        Impacts.take();
        int taken = 0;
        for (int slot = 0; slot < Impacts.MOST; slot++) {
            if (Impacts.held[slot] != null) {
                taken++;
                check(Impacts.born[slot] >= before && Impacts.born[slot] <= after, "an impact starts when the shot strikes, not when a frame takes it");
            }
        }
        check(taken == 3, "all three are taken: " + taken);
        check(Impacts.holeCount == 2, "two left holes: " + Impacts.holeCount);
        Impacts.take();
        check(Impacts.holeCount == 2, "a frame with no new shot takes nothing more");
        // Shot while no picture takes them, in the game's own view: no more are held than there are places for holes.
        for (int i = 0; i < Impacts.MOST_HOLES + 50; i++) {
            Impacts.add(struck(i, true));
        }
        long shot = System.nanoTime();
        // The first frame that takes them comes later: first person comes on.
        try {
            Thread.sleep(5);
        } catch (InterruptedException interrupted) {
            throw new IllegalStateException(interrupted);
        }
        Impacts.take();
        check(Impacts.holeCount == Impacts.MOST_HOLES, "the holes are kept up to their most: " + Impacts.holeCount);
        float least = Float.MAX_VALUE;
        for (int i = 0; i < Impacts.holeCount; i++) {
            least = Math.min(least, Impacts.holes[i].x());
        }
        check(least == 50f, "and the oldest are the ones given up: " + least);
        // What flies from them, as ImpactDraw.debris ages it, is done LONGEST after the last shot, however late a frame took them.
        long done = shot + (long) (Impacts.LONGEST * 1e9) + 1_000_000L;
        for (int slot = 0; slot < Impacts.MOST; slot++) {
            if (Impacts.held[slot] != null) {
                float age = (done - Impacts.born[slot]) / 1e9f;
                check(age > Impacts.LONGEST, "what a shot threw up is done LONGEST after it struck: " + age + " s old");
            }
        }
        Impacts.forget();
        Impacts.take();
    }

    private static void leavingTheSaveForgetsItsHoles() {
        Impacts.add(struck(1f, true));
        Impacts.take();
        check(Impacts.holeCount > 0, "a hole is kept");
        Impacts.forget();
        Impacts.add(struck(2f, true));
        Impacts.take();
        check(Impacts.holeCount == 0, "a save left takes its holes and what was shot as it was left: " + Impacts.holeCount);
        for (int slot = 0; slot < Impacts.MOST; slot++) {
            check(Impacts.held[slot] == null, "and nothing flies");
        }
    }

    private static void theGamesOwnLookShowsNoImpacts() {
        int mode = Quality.row(0);
        Quality.Picture custom = Quality.custom();
        try {
            Quality.set(0, Quality.LOW);
            check(!ImpactDraw.shown(0), "Low, the game's own look, shows no impacts");
            Quality.set(0, Quality.MEDIUM);
            check(ImpactDraw.shown(0), "Medium shows them");
            Quality.set(0, Quality.HIGH);
            check(ImpactDraw.shown(0), "and High");
        } finally {
            Quality.custom(custom);
            Quality.set(0, mode);
        }
    }
}

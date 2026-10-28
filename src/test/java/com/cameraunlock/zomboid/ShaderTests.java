package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Puts every shader the mod hands a graphics driver through glslangValidator, the Khronos
 * reference compiler, which holds a source to the version it declares where a driver may let
 * more by.
 *
 * The programs are found by reading the mod's own source for the places that link one, and each
 * stage's text is then taken from the fields that place names, so a program added later is
 * checked without being listed here, and one put together in a way this cannot read fails the run.
 *
 * A pass says each stage is legal GLSL of its version, extensions and fixed-function names
 * included, and that the stages of a program agree on what passes between them. It does not say
 * a driver will take it: glslangValidator knows no driver's limits or bugs, lets by anything the
 * language merely leaves undefined, and does not check a count of texture units or uniforms.
 */
final class ShaderTests {
    private static final Path SOURCE = Path.of("src/main/java/com/cameraunlock/zomboid"), OUT = Path.of("build/shaders");
    private static final String VALIDATOR = "glslangValidator";

    /** Every way a source reaches glShaderSource, by the file the call is in. A call anywhere else is one this cannot follow. */
    private static final List<String> HANDS_OVER = List.of("Program.java", "SmallProgram.java", "CharacterDraw.java", "OwnArms.java");

    private static final Pattern LINKED = Pattern.compile("Program\\.link\\(\\s*\"([^\"]+)\"\\s*,([^,()]+),([^,()]+),");
    private static final Pattern SMALL = Pattern.compile("new SmallProgram\\(\\s*\"([^\"]+)\"\\s*,(?:\\s*\"[^\"]*\"\\s*,)?([^,()]+),([^,()]+)\\)");
    private static final Pattern STAGED = Pattern.compile("\\bprogram\\(\\s*\"([^\"]+)\"\\s*,([^()]+)\\)");
    private static final Pattern ARMS = Pattern.compile("String\\[\\] sources = \\{([^,{}]+),([^,{}]+)\\};");
    private static final Map<String, String> STAGE = Map.of("GL_VERTEX_SHADER", "vert", "GL_TESS_CONTROL_SHADER", "tesc",
            "GL_TESS_EVALUATION_SHADER", "tese", "GL_FRAGMENT_SHADER", "frag");

    /**
     * The mod's classes loaded again beside the stubs of the game's. A class that holds a shader has fields of the
     * game's types, and the tests' own class path has no game on it, which other tests rely on.
     */
    private static final ClassLoader WITH_STUBS;

    static {
        try {
            WITH_STUBS = new URLClassLoader(new URL[] {Path.of("build/classes").toUri().toURL(), Path.of("build/stubs").toUri().toURL()},
                    ClassLoader.getPlatformClassLoader());
        } catch (MalformedURLException e) {
            throw new AssertionError(e);
        }
    }

    private ShaderTests() {}

    /** A program's stages, each a file extension glslangValidator tells the stage by and the text. */
    private record Linked(String name, String from, Map<String, String> stages) {}

    static void run() {
        List<Linked> programs = programs();
        Checks.run("depthProgramIsTheWorldsToldToDrawDepthAlone", () -> depthProgramIsTheWorldsToldToDrawDepthAlone(programs));
        Checks.run("calmProgramIsTheWorldsWithItsTestsAnswered", () -> calmProgramIsTheWorldsWithItsTestsAnswered(programs));
        Checks.run("untintedProgramIsTheCalmToldNothingIsPainted", () -> untintedProgramIsTheCalmToldNothingIsPainted(programs));
        Checks.run("aGroundPictureReadOnlyWhereItIsTakesItsDerivativesFromOutside", ShaderTests::aGroundPictureReadOnlyWhereItIsTakesItsDerivativesFromOutside);
        Checks.run("wiresAreOutOfTheMapFromAbove", () -> wiresAreOutOfTheMapFromAbove(programs));
        Checks.run("wallsAskTheSunFromUnderTheirRoof", () -> wallsAskTheSunFromUnderTheirRoof(programs));
        Checks.run("noSunThroughARoomsCorner", ShaderTests::noSunThroughARoomsCorner);
        Checks.run("whatHangsOnACordSwingsInTheViewAndInItsShadowAlike", () -> whatHangsOnACordSwingsInTheViewAndInItsShadowAlike(programs));
        Checks.run("whatTheAirHidesOfASurfaceItHidesOfItsShade", () -> whatTheAirHidesOfASurfaceItHidesOfItsShade(programs));
        Checks.run("theLastFrameIsFoundFromTheEyeNotTheMap", () -> theLastFrameIsFoundFromTheEyeNotTheMap(programs));
        Checks.run("whatIsDrawnThroughTheTurnTakesTheEyeOffItself", () -> whatIsDrawnThroughTheTurnTakesTheEyeOffItself(programs));
        Checks.run("figuresAndTerrainShareSunsetHaze", ShaderTests::figuresAndTerrainShareSunsetHaze);
        Checks.run("aThinSpriteSampleTakesItsColourFromTheSamplesItsCutReads", ShaderTests::aThinSpriteSampleTakesItsColourFromTheSamplesItsCutReads);
        Checks.run("aCrownOpensOverTheEyeASprigAtATime", ShaderTests::aCrownOpensOverTheEyeASprigAtATime);
        Checks.run("aRollerDoorIsLaidAsSlatsAndNotAsMasonry", () -> aRollerDoorIsLaidAsSlatsAndNotAsMasonry(programs));
        Checks.run("stonesOnMortarAreToldApartFromTilesAndTakeItsWidth", ShaderTests::stonesOnMortarAreToldApartFromTilesAndTakeItsWidth);
        Checks.run("spreadGroundThatDoesNotShowHidesNothing", () -> spreadGroundThatDoesNotShowHidesNothing(programs));
        Checks.run("grimeIsSeenOnlyFromItsSide", () -> grimeIsSeenOnlyFromItsSide(programs));
        Checks.run("aWallSkinHasNoBack", () -> aWallSkinHasNoBack(programs));
        Checks.run("everyProgramPassesTheReferenceCompiler", () -> everyProgramPassesTheReferenceCompiler(programs));
        Checks.run("theFloorAsksForWhatTheFragmentStagesRead", () -> theFloorAsksForWhatTheFragmentStagesRead(programs));
    }

    private static final Pattern REFLECTED = Pattern.compile("^(\\S+): offset -?\\d+, type ([0-9a-f]+), size (\\d+), .*?stages (\\d+)", Pattern.MULTILINE);
    /** glslang's bit for the fragment stage in a reflected uniform's stages. */
    private static final int FRAGMENT_STAGE = 16;

    /**
     * GlFloor turns first person off on a card that reads fewer textures, or holds fewer uniform
     * components, in a fragment shader than the most any program asks for: so its numbers are
     * that most, as glslangValidator's reflection of the linked programs counts them, a vec4 to
     * each uniform and each column of a matrix, the way a driver lays them out.
     */
    private static void theFloorAsksForWhatTheFragmentStagesRead(List<Linked> programs) throws IOException, InterruptedException {
        Path out = OUT.resolve("reflected");
        int samplers = 0, components = 0;
        String mostSamplers = null, mostComponents = null;
        for (Linked program : programs) {
            List<String> command = new ArrayList<>(List.of(VALIDATOR, "-l", "-q"));
            Files.createDirectories(out);
            for (Map.Entry<String, String> stage : program.stages.entrySet()) {
                Path file = out.resolve(program.name.replace(' ', '-') + "." + stage.getKey());
                Files.writeString(file, stage.getValue(), StandardCharsets.UTF_8);
                command.add(file.toString());
            }
            Process validator = new ProcessBuilder(command).redirectErrorStream(true).start();
            String said = new String(validator.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Checks.check(validator.waitFor() == 0, "glslangValidator could not reflect the " + program.name + " program:\n" + said);
            int start = said.indexOf("Uniform reflection:"), end = said.indexOf("Uniform block reflection:");
            Checks.check(start >= 0 && end > start, "glslangValidator's reflection of the " + program.name + " program has no uniforms section:\n" + said);
            int readSamplers = 0, held = 0;
            Matcher uniform = REFLECTED.matcher(said.substring(start, end));
            while (uniform.find()) {
                if (uniform.group(1).startsWith("gl_") || (Integer.parseInt(uniform.group(4)) & FRAGMENT_STAGE) == 0) {
                    continue;
                }
                int type = Integer.parseInt(uniform.group(2), 16), size = Integer.parseInt(uniform.group(3));
                if (type >= 0x8B5D && type <= 0x8B64 || type >= 0x8DC0 && type <= 0x8DD8) {
                    readSamplers += size;
                } else {
                    held += 4 * size * (type == 0x8B5C ? 4 : type == 0x8B5B ? 3 : type == 0x8B5A ? 2 : 1);
                }
            }
            if (readSamplers > samplers) {
                samplers = readSamplers;
                mostSamplers = program.name;
            }
            if (held > components) {
                components = held;
                mostComponents = program.name;
            }
        }
        Checks.check(samplers == GlFloor.FRAGMENT_SAMPLERS, "the " + mostSamplers + " fragment stage reads " + samplers
                + " textures, the most of any, and GlFloor.FRAGMENT_SAMPLERS says " + GlFloor.FRAGMENT_SAMPLERS + RAISING);
        Checks.check(components == GlFloor.FRAGMENT_UNIFORM_COMPONENTS, "the " + mostComponents + " fragment stage holds " + components
                + " uniform components, the most of any, and GlFloor.FRAGMENT_UNIFORM_COMPONENTS says " + GlFloor.FRAGMENT_UNIFORM_COMPONENTS + RAISING);
    }

    /** Said when the shaders and GlFloor disagree, since the fix that reads easiest is the one players pay for. */
    private static final String RAISING = ". Raising the constant to match raises the floor for players: a card with fewer is then refused first person,"
            + " so first look for a way to read fewer";

    private static void figuresAndTerrainShareSunsetHaze() {
        Checks.check(PipelineShaders.WORLD_FRAGMENT_MAIN.contains("distanceHaze(-view, range)"),
                "near terrain must use the shared directional haze");
        Checks.check(PipelineShaders.FAR_FRAGMENT.contains("distanceHaze(-view, away)"),
                "distant terrain must use the shared directional haze");
        Checks.check(PipelineShaders.FIGURE_LIGHT_MAIN.contains("distanceHaze(normalize(ray), range)"),
                "characters must receive the same sunset haze as terrain");
    }

    /**
     * Under a tree its crown's lower face opens: a downturned sprig is left out or kept whole,
     * by what is the same over all of it, and only for the tree the eye stands under. Keyed on
     * the way to each pixel it was cut along a line, and a tree on a rise ten tiles off thinned.
     */
    private static void aCrownOpensOverTheEyeASprigAtATime() {
        String main = PipelineShaders.WORLD_FRAGMENT_MAIN;
        Checks.check(main.contains("if (floor(vMaterial - 71.5) != SHRUB && each < CROWN_UNDER_THINS * (1.0 - smoothstep(CROWN_UNDER_IN, CROWN_UNDER_OUT, length(uEye.xy - vRoot)))"),
                "a sprig under a crown is not left out whole, or not only under the tree the eye stands under, or a bush's is too");
        Checks.check(main.contains("float under = which == SHRUB ? 0.0 : (1.0 - smoothstep(CROWN_UNDER_IN, CROWN_UNDER_OUT, length(uEye.xy - vRoot)))"),
                "a crown's inside opens for a tree the eye is not under, or for a bush");
        Checks.check(main.contains("* (1.0 - smoothstep(-0.3, " + Flora.TURNED_DOWN + ", normalize(vNormal).z))) {"),
                "a sprig is left out from under its tree when turned down less than Flora.TURNED_DOWN, which says which sprigs have no twig");
        Checks.check(main.contains("if (max(sprig.a, farSide ? 0.0 : behind.a * (1.0 - under)) < 0.5 * under) {"), "a crown's far side is cut at more than half its leaves' alpha");
        // GLSL leaves smoothstep with its first edge past its second undefined.
        Checks.check(!main.contains("smoothstep(CROWN_UNDER_OUT, CROWN_UNDER_IN") && !main.contains("smoothstep(0.2, -0.3"), "a crown's underside opens by a smoothstep with its edges the wrong way round");
    }

    /**
     * A sprite's pixel is cut by the cover of two samples half a texel either side of it, so its
     * colour, where its own sample is all but clear, is taken from those two samples: divided by
     * the centre's sliver of cover it was a bright speck, and Low's plants glittered.
     */
    private static void aThinSpriteSampleTakesItsColourFromTheSamplesItsCutReads() {
        String main = PipelineShaders.WORLD_FRAGMENT_MAIN;
        Checks.check(main.contains("vec2 halfTexel = vec2(0.5 * uTexel.x, 0.0);"), "the cut no longer reads samples half a texel either side across");
        Checks.check(main.contains("vec2 across = vec2(0.5 * uTexel.x, 0.0);"), "a thin sample's colour is not read from the cut's samples");
        Checks.check(main.contains("texel.rgb = min(texel.rgb, vec3(1.0));"), "a sprite's colour can come out lighter than white");
    }

    private static void aRollerDoorIsLaidAsSlatsAndNotAsMasonry(List<Linked> programs) {
        float[] tint = new float[4];
        WallSides.tint(WallSides.SLATS, tint);
        String main = PipelineShaders.WORLD_FRAGMENT_MAIN;
        Checks.check(main.contains("bool slatTint = abs(uTint.a - " + (int) tint[3] + ".0) < 0.5;"), "the world is not told a roller door's slats as WallSides tells them");
        Checks.check(main.contains("if (uTint.a > 3.5 && !boarded && !fineTint && !tiledTint && !stainTint && !slatTint) {"), "a roller door would be laid as masonry too");
        Checks.check(fragment(programs, "world").contains("vec3 facSlats("), "the world's program has no slats to lay");
    }

    private static void stonesOnMortarAreToldApartFromTilesAndTakeItsWidth() {
        String main = PipelineShaders.WORLD_FRAGMENT_MAIN;
        Checks.check(main.contains("bool mortared = abs(uTint.a - MORTARED) < 0.125;") && Math.abs(WallSides.MORTARED - WallSides.TILED) < 0.5f,
                "stones on mortar are not told apart from tiles, or are not laid as tiles");
        Checks.check(main.contains("float jointHalf = floored ? (mortared ? FLOOR_MORTAR : FLOOR_JOINT) : TILE_JOINT;"), "stones on mortar do not take its width");
    }

    private static void depthProgramIsTheWorldsToldToDrawDepthAlone(List<Linked> programs) {
        String whole = fragment(programs, "world"), depth = fragment(programs, "world depth");
        Checks.check(!whole.equals(depth) && depth.contains("const bool depthOnly = true;"), "the world depth program is not the world's program told to draw depth alone");
    }

    private static void calmProgramIsTheWorldsWithItsTestsAnswered(List<Linked> programs) {
        String whole = fragment(programs, "world"), calm = fragment(programs, "world calm");
        String[] tests = {"uWet > 0.0", "uRaining > 0.0", "uSnowLying > 0.0", "uPlantSnow > 0.0", "uTorchAt.w > 0.0", "uFlashAt.w <= 0.0", "uLantern.w > 0.0", "uBeamAt[i].w > 0.0"};
        for (String test : tests) {
            Checks.check(whole.contains(test) && !calm.contains(test), "the calm world program is not told the answer to " + test);
        }
        Checks.check(count(whole, "if (at.w <= 0.0)") - count(calm, "if (at.w <= 0.0)") == count(whole, "vec4 at = uFireAt[i];"), "the calm world program is not told that no fire gives light");
    }

    /**
     * The untinted program draws the ranges of tint key 0 in place of the calm one, so it is the calm
     * text with uTint held at what WallSides.tint tells a key of 0, and nothing else changed.
     */
    private static void untintedProgramIsTheCalmToldNothingIsPainted(List<Linked> programs) {
        String calm = fragment(programs, "world calm"), untinted = fragment(programs, "world untinted");
        Checks.check(count(calm, "uniform vec4 uTint;") == 1 && calm.replace("uniform vec4 uTint;", "const vec4 uTint = vec4(0.0);").equals(untinted),
                "the untinted world program is not the calm one with only uTint held at nothing");
        float[] tint = {1f, 1f, 1f, 1f};
        WallSides.tint(0, tint);
        Checks.check(tint[0] == 0f && tint[1] == 0f && tint[2] == 0f && tint[3] == 0f, "a range of tint key 0 is told a tint, which the untinted program cannot take");
    }

    /**
     * A ground picture is read only where the ground is made of some of it, a test that differs
     * between the pixels of one quad, so each read is told its derivatives, taken before the tests.
     */
    private static void aGroundPictureReadOnlyWhereItIsTakesItsDerivativesFromOutside() {
        String main = PipelineShaders.WORLD_FRAGMENT_MAIN;
        int taken = main.indexOf("vec2 oneAcross = dFdx(one), oneDown = dFdy(one), twoAcross = dFdx(two), twoDown = dFdy(two);");
        for (String[] read : new String[][] {{"dark", "uAsphalt"}, {"loose", "uGravel"}, {"earth", "uSoil"}, {"green", "uLawn"}}) {
            int test = main.indexOf("if (" + read[0] + " != 0.0) {");
            int end = test < 0 ? -1 : main.indexOf("}", test);
            String body = end < 0 ? "" : main.substring(test, end);
            Checks.check(taken >= 0 && test > taken && body.contains("texture2DGradARB(" + read[1] + ", one, oneAcross, oneDown)")
                    && body.contains("texture2DGradARB(" + read[1] + ", two, twoAcross, twoDown)") && !body.contains("texture2D(") && !body.contains("dFd"),
                    "the " + read[1] + " picture read where there is " + read[0] + " is not told derivatives taken before the test");
        }
        // The ground's bump leans by reads of its pictures, told the same derivatives: a dFdx of what was read sparkles under motion.
        String rise = PipelineShaders.WORLD_FRAGMENT_PARTS.substring(Math.max(0, PipelineShaders.WORLD_FRAGMENT_PARTS.indexOf("vec2 groundRise(")));
        rise = rise.substring(0, Math.max(0, rise.indexOf("return mix(")));
        Checks.check(rise.startsWith("vec2 groundRise(") && rise.contains("texture2DGradARB(picture, one + vec2(oneStep, 0.0), oneX, oneY)")
                && !rise.contains("texture2D(") && !rise.contains("dFd") && !PipelineShaders.WORLD_FRAGMENT_MAIN_GROUND.contains("dFdx(high)"),
                "the ground's bump does not lean by reads of its pictures told derivatives taken before the tests");
    }

    private static void everyProgramPassesTheReferenceCompiler(List<Linked> programs) throws IOException, InterruptedException {
        // The folder holds theFloorAsksForWhatTheFragmentStagesRead's reflected programs as well, in one of its own.
        if (Files.exists(OUT)) {
            try (Stream<Path> old = Files.walk(OUT)) {
                for (Path file : old.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
        Files.createDirectories(OUT);
        int passed = 0;
        for (Linked program : programs) {
            String refused = refusal(OUT, program.name, program.stages);
            Checks.check(refused == null, "glslangValidator refuses the " + program.name + " shader (" + program.from + "), which the mod compiles in the game."
                    + " Its source is in " + OUT + ":\n" + refused);
            passed += refused == null ? 1 : 0;
        }
        System.out.println("shader tests: " + passed + " of " + programs.size() + " programs pass " + VALIDATOR);
    }

    /**
     * What glslangValidator says against a program's stages, linked, each a file extension it
     * tells the stage by and the text, written into a folder to be read; null if it takes them
     * with no error and no warning.
     */
    static String refusal(Path out, String name, Map<String, String> stages) throws IOException, InterruptedException {
        Files.createDirectories(out);
        List<String> command = new ArrayList<>(List.of(VALIDATOR, "-l", "--validate-io"));
        for (Map.Entry<String, String> stage : stages.entrySet()) {
            Path file = out.resolve(name.replace(' ', '-') + "." + stage.getKey());
            Files.writeString(file, stage.getValue(), StandardCharsets.UTF_8);
            command.add(file.toString());
        }
        Process validator;
        try {
            validator = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException missing) {
            throw new AssertionError(VALIDATOR + " did not start (" + missing.getMessage()
                    + "): it comes with the pixi environment, so run the tests with pixi run test");
        }
        String said = new String(validator.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return validator.waitFor() == 0 && !said.contains("WARNING") && !said.contains("ERROR") ? null : said.strip();
    }

    /**
     * An overhead wire is thrown out of the map read as what keeps the sky off the ground, and out of no other map
     * the shadow program draws. Read from the sources, since nothing here can run a shader: that the wire's branch
     * of the vertex stage puts the vertex outside the clip volume, before it is placed by the matrix, when
     * uFromAbove is set, and that the one draw that sets it is the map from above, which also sets it back.
     */
    private static String vertex(List<Linked> programs, String name) {
        for (Linked program : programs) {
            if (program.name.equals(name)) {
                return program.stages.get("vert");
            }
        }
        throw new AssertionError("no program is linked as \"" + name + "\"");
    }

    private static final Pattern HANGS_OVER = Pattern.compile("dot\\(gl_Normal, gl_Normal\\) > ([0-9.]+)");

    /** The length, squared, of a facing over which a vertex of the stuff of a lamp is one that hangs, as a vertex stage has it written, once. */
    private static String hangsOver(String stage, String name) {
        Matcher line = HANGS_OVER.matcher(stage);
        if (!line.find()) {
            throw new AssertionError("the " + name + " vertex stage does not tell a lamp that hangs by the length of its facing");
        }
        String over = line.group(1);
        if (line.find()) {
            throw new AssertionError("the " + name + " vertex stage tells a lamp that hangs in more than one place");
        }
        return over;
    }

    private static String stuff(String material, float number) {
        return material + " > " + (number - 0.5f) + " && " + material + " < " + (number + 0.5f);
    }

    /** What a vertex stage has to ask to know a vertex for one that hangs on a cord: WorldScene's stuff of what hangs unlit, or a lamp's with a facing longer than the line. */
    private static String hung(String material, String over) {
        return stuff(material, WorldScene.HUNG) + " || " + stuff(material, WorldScene.LAMP) + " && dot(gl_Normal, gl_Normal) > " + over;
    }

    private static void once(String stage, String name, String line) {
        int at = stage.indexOf(line);
        Checks.check(!(at < 0 || stage.indexOf(line, at + 1) >= 0), "the " + name + " vertex stage has not exactly one line \"" + line + "\"");
    }

    /**
     * A bulb on a string of lights swings with its cord in the view and in the sun's map, each
     * of which finds out for itself that a vertex hangs. Both have to ask the same, of the
     * numbers WorldScene gives what hangs, and every vertex Lights.round makes has to pass,
     * the one right under a support too, which hangs by HUNG_LEAST alone. A lamp that stands
     * has a facing one long and must not pass.
     */
    private static void whatHangsOnACordSwingsInTheViewAndInItsShadowAlike(List<Linked> programs) {
        String world = vertex(programs, "world"), shadow = vertex(programs, "shadow");
        String over = hangsOver(world, "world");
        Checks.check(over.equals(hangsOver(shadow, "shadow")), "the view tells a lamp that hangs by a facing over " + over + " and its shadow by one over " + hangsOver(shadow, "shadow"));
        once(world, "world", "bool hung = uModel < 0.5 && !still && (" + hung("vMaterial", over) + ");");
        once(world, "world", "bool wire = uModel < 0.5 && " + stuff("vMaterial", WorldScene.WIRE) + " || hung;");
        once(world, "world", "at.xyz = swung(at.xyz, length(gl_Normal) - 1.0, across, swing);");
        once(shadow, "shadow", "if (" + stuff("material", WorldScene.WIRE) + " || " + hung("material", over) + ") {");
        once(shadow, "shadow", "vAt = swung(stands.xyz, length(gl_Normal) - 1.0, across, swing);");
        float line = Float.parseFloat(over);
        for (int kind = Lights.LARGE; kind <= Lights.RAINBOW; kind++) {
            // Holder and glass, near the eye and far from it.
            for (int shape = 0; shape < 4; shape++) {
                boolean far = shape >= 2;
                SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
                Lights.round(mesh, 0f, 0f, 0f, 0f, 1f, 0f, shape % 2 == 0 ? Lights.holder(far) : Lights.glass(far), Lights.size(kind), Lights.sides(kind, far), 1f, 1f, 1f);
                int still = 0;
                float least = Float.MAX_VALUE;
                for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                    float[] v = mesh.vertices;
                    float squared = v[at + 9] * v[at + 9] + v[at + 10] * v[at + 10] + v[at + 11] * v[at + 11];
                    still += squared > line ? 0 : 1;
                    least = Math.min(least, squared);
                }
                Checks.check(still == 0, still + " vertices of a bulb of kind " + kind + ", shape " + shape + " have a facing the vertex stages do not take for hanging,"
                        + " the least " + least + " squared (over " + over + "): they would stand still while the cord swings");
            }
        }
        SpriteGeometry.Mesh stands = new SpriteGeometry.Mesh();
        for (float[] facing : new float[][] {{1f, 0f, 0f}, {0.3f, -0.7f, 0.2f}, {-0.577f, 0.577f, 0.577f}}) {
            stands.face(facing[0], facing[1], facing[2]);
            stands.add(0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);
        }
        for (int at = 0; at < stands.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float[] v = stands.vertices;
            Checks.check(!(v[at + 9] * v[at + 9] + v[at + 10] * v[at + 10] + v[at + 11] * v[at + 11] > line), "a lamp that stands, with a facing one long, is taken for one that hangs (over " + over + ")");
        }
    }

    /**
     * An overhead wire in the map from above combs lines into lying snow beside it. Read, not
     * run: the wire is thrown out by the shadow vertex stage, which only a graphics card runs,
     * and the map is drawn by Pipeline, which only loads in the game.
     */
    private static void wiresAreOutOfTheMapFromAbove(List<Linked> programs) {
        String vertex = vertex(programs, "shadow");
        String wire = "if (" + stuff("material", WorldScene.WIRE) + " || " + hung("material", hangsOver(vertex, "shadow")) + ") {";
        int at = vertex.indexOf(wire);
        Checks.check(!(at < 0 || vertex.indexOf(wire, at + 1) >= 0), "the shadow vertex stage has not exactly one branch for an overhead wire");
        String branch = vertex.substring(at + wire.length()).replaceAll("//[^\n]*", "").replaceAll("\\s+", " ").strip();
        Checks.check(branch.startsWith("if (uFromAbove > 0.5) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); return; }"), "the shadow vertex stage does not throw an overhead wire out of the map from above before it places it");
        String pipeline;
        try {
            pipeline = Files.readString(SOURCE.resolve("Pipeline.java")).replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int roof = pipeline.indexOf("private void castRoof("), set = pipeline.indexOf("shadow.set(\"uFromAbove\", 1f); try {");
        int back = pipeline.indexOf("} finally { shadow.set(\"uFromAbove\", 0f); } }", set), next = pipeline.indexOf(" private ", roof + 1);
        Checks.check(!(roof < 0 || set < roof || back < set || back > next || count(pipeline, "\"uFromAbove\", 1f") != 1), "uFromAbove is not set for the map from above alone, and set back whatever the draw does");
    }

    /**
     * A sun map holds a level sheet behind itself by Pipeline.SLOPE_OFFSET texels of its slope, so the top of a wall
     * under a roof of one sheet reads as in front of the sheet. Reads the sources: both lookups of the world's fragment
     * stage into a sun map are made, for what is roofed, from where sunAsked puts the point, and a point in a room lit
     * there is asked SUN_BAND lower too, a ceiling's sheet being held behind itself as a roof's is; sunAsked lowers a point
     * only where something is over it, by the offset, a texel and the lookup's own spread; a point lowered out of the
     * near map is asked of the far one; the program is told the offset the maps are drawn with; and the shadow maps
     * keep a sheet's clear pixels and no other face's.
     */
    private static void wallsAskTheSunFromUnderTheirRoof(List<Linked> programs) {
        String world = fragment(programs, "world").replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        Checks.check(world.contains("bool roofed = (open < 0.5 || abs(normalize(vNormal).z) < 0.5 && !card) && !water && uCutMarked.z <= 0.0;"), "the world's fragment stage does not say what is roofed before it asks the sun maps");
        Checks.check(world.contains("float beneath = open < 0.5 ? 0.16 * uRoofPitch : 0.0;"), "a point in a room is not asked from under the ceiling of a pitched roof, or one out of doors is");
        Checks.check(!(!world.contains("if (roofed) { c = (uNearMatrix * vec4(sunAsked(from.xyz, soft, uSunTexelUp.x, beneath), 1.0)).xyz * 0.5 + 0.5; inNear = inside(c); }"
                + " if (inNear) { reached = shadowAt(uShadowNear, c, soft); if (open < 0.5 && reached > 0.0) {"
                + " vec3 lower = (uNearMatrix * vec4(sunAsked(from.xyz, soft, uSunTexelUp.x, beneath) - vec3(0.0, 0.0, SUN_BAND), 1.0)).xyz * 0.5 + 0.5;"
                + " reached = min(reached, shadowAt(uShadowNear, lower, soft)); } } }")
                || !world.contains("if (!inNear) { if (roofed) { from.xyz = sunAsked(from.xyz, 1.6, uSunTexelUp.y, beneath); }"
                        + " c = (uFarMatrix * from).xyz * 0.5 + 0.5; reached = inside(c) ? shadowAt(uShadowFar, c, 1.6) : 1.0; }")
                || count(world, "shadowAt(uShadow") != 3), "the world's fragment stage asks a sun map about what is roofed from where the map cannot tell it from its roof,"
                    + " or a point in a room only at itself and not SUN_BAND lower too");
        Checks.check(world.contains("float top = from.z + (c.z - over) * uRoofDeep; if (top <= vWorld.z + 0.001) { return from; } "
                + "return vec3(from.xy, min(from.z, top - (uSlopeOffset + 1.0 + texels) * texelUp - under));"), "sunAsked does not lower a point under what is over it by the map's offset and the lookup's spread,"
                    + " or lowers one with nothing over it");
        String caster = fragment(programs, "shadow").replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        Checks.check(caster.contains("if (vSolid < 0.5 && texture2D(uTexture, vUv).a < 0.5) { discard; }"), "the shadow maps let the sun through the clear pixels of a floor or a ceiling's sprite");
        String casterVertex = vertex(programs, "shadow").replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        String pipeline;
        try {
            pipeline = Files.readString(SOURCE.resolve("Pipeline.java")).replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Checks.check(!(!casterVertex.contains("vSolid = gl_MultiTexCoord1.z > 5.5 && gl_MultiTexCoord1.z < 6.5 ? 1.0 : 0.0;") || SpriteGeometry.SHEET != 6f
                || SpriteGeometry.LAYERS_UP >= 5.5f), "the shadow maps keep the clear pixels of something that is not a sheet, or of no sheet");
        Checks.check(casterVertex.contains("(kind < 3.5 ? vec3(1.0, 0.0, 0.0) : kind < 4.5 ? vec3(0.0, 1.0, 0.0) : vec3(0.0, 0.0, 1.0))"), "a sheet does not lie up as what lies in layers up does");
        String scene = LowTests.scene();
        Checks.check(!(count(scene, "layers.sheet(") != 3 || count(scene, "layers.sheet(-1f); ") != 2
                || !scene.contains("private void layFloor(boolean floor) { if (floor && build.floorLayer == 0) { WorldScene.layers.sheet(build.floorLayer++); }"
                        + " else { WorldScene.layers.lie(SpriteGeometry.LAYERS_UP, build.floorLayer++); } }")
                || !scene.contains("layFloor(isFloor && build.frame.kind == 0f);")), "the scene does not mark the first layer of a floor and both ceilings a sheet, and only those");
        Checks.check(!(count(pipeline, "glPolygonOffset(") != 2 || count(pipeline, "GL11.glPolygonOffset(SLOPE_OFFSET, 6f);") != 2
                || count(pipeline, "glPolygonOffsetClamp(") != 1 || count(pipeline, "glPolygonOffsetClampEXT(") != 1
                || count(pipeline, "(SLOPE_OFFSET, 6f, most);") != 2
                || !pipeline.contains("float across = (float) Math.hypot(state.lightX, state.lightY); "
                        + "to.set(\"uSunTexelUp\", 2f * NEAR_EXTENT / shadowSize * across, 2f * FAR_EXTENT / shadowSize * across); "
                        + "to.set(\"uSlopeOffset\", SLOPE_OFFSET); to.set(\"uRoofDeep\", 2f * ROOF_RANGE);"
                        + " to.set(\"uRoofPitch\", RoofFit.PITCH * ViewCamera.LEVEL_HEIGHT);")), "the world's program is not told the offset the sun maps are drawn with,"
                    + " a texel of each map as the light lies, or the depth of the map from above");
    }

    /**
     * A wall the sun runs almost along is so steep in a sun map that the slope's offset held it a tile and more behind
     * itself, and the sun went through it in a strip down the wall it meets at a room's corner. Reads the source: the
     * near and the far map are each cast with the offset held to Pipeline.MOST_HELD_BEHIND along the light, less than a
     * wall is thick, and the unheld offset is put back before anything else is cast.
     */
    private static void noSunThroughARoomsCorner() {
        String pipeline;
        try {
            pipeline = Files.readString(SOURCE.resolve("Pipeline.java")).replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Checks.check(pipeline.contains("offsetHeldTo(map == 0 ? NEAR_RANGE : FAR_RANGE); castHeld(map == 0 ? nearMatrix : farMatrix,")
                && pipeline.contains("float most = MOST_HELD_BEHIND / (2f * range);")
                && pipeline.contains("map == 0 ? NEAR_MAP : FAR_MAP); } GL11.glPolygonOffset(SLOPE_OFFSET, 6f); if (figures) {"),
                "the sun maps are not each cast with the slope's offset held to MOST_HELD_BEHIND along the light, or the unheld offset is not put back after them");
        Checks.check(Pipeline.MOST_HELD_BEHIND > 0f && Pipeline.MOST_HELD_BEHIND < 2f * WorldScene.WALL_HALF,
                "the sun maps may hold a wall further behind itself than a wall is thick");
    }

    /**
     * The world's shader veils a surface by the fog, rain or snow between it and the eye, and the develop pass then lays
     * the shade, the light lent by what stands near and what a surface mirrors over that colour. Each has to be veiled by
     * as much, by the world's own count from the same distance, or the shade of a building lost in thick fog draws its
     * window frames and roof edges through the fog as dark lines. Read, not run: only a graphics card runs the pass.
     */
    private static void whatTheAirHidesOfASurfaceItHidesOfItsShade(List<Linked> programs) {
        String raw = fragment(programs, "develop");
        Checks.check(raw.contains(Sky.ATMOSPHERE), "the develop pass does not count what the air hides with the world's own veiling");
        String develop = raw.replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        Checks.check(develop.contains("float range = max(away - uRangeFrom, 0.0);") && develop.contains("float away = length(p);"),
                "the develop pass does not count the pixel's distance as the world counts it");
        // The world veils by max(veiling(range), ends * skyVeiling(-view)): where it has ended in the hills, what the air hides of the sky.
        String world = fragment(programs, "world").replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        Checks.check(world.contains("max(veiling(range), ends * skyVeiling(-view))")
                && world.contains("float ends = uBeyond > 0.5 ? 0.0 : max(smoothstep(uFogStart, uFogEnd, range), smoothstep(uLoaded.z - (uFogEnd - uFogStart), uLoaded.z, length(toEye.xy + uLoaded.xy)));"),
                "the world's own veil is not what this test holds the develop pass to");
        Checks.check(develop.contains("vec3 fromEye = uAcross * p.x + uUpward * p.y + uAhead * p.z;")
                && develop.contains("float ends = uBeyond > 0.5 ? 0.0 : max(smoothstep(uFogStart, uFogEnd, range), smoothstep(uLoaded.z - (uFogEnd - uFogStart), uLoaded.z, length(fromEye.xy - uLoaded.xy)));")
                && develop.contains("float seen = 1.0 - max(veiling(range), ends * skyVeiling(fromEye / away));"),
                "the develop pass does not leave a pixel seen by as much as the world's veil does, the sky's share where the world ends included");
        Checks.check(develop.contains("vec3 c = mix(here.rgb, mirrored(uv, here, p), seen) * mix(1.0, around.a, 0.62 * seen);"),
                "the shade, or what a surface mirrors, is laid over a surface whole however much of it the air hides");
        Checks.check(develop.contains("c += lent * (here.rgb / max(own, 0.02)) * (0.22 * seen);"), "the light lent by what stands near is laid over a surface whole however much of it the air hides");
        Checks.check(count(develop, "around.a") == 1 && count(develop, "lent *") == 1 && count(develop, "mirrored(uv") == 1, "the develop pass lays the shade, the lent light or the mirror over the picture somewhere else too");
        String pipeline;
        try {
            pipeline = Files.readString(SOURCE.resolve("Pipeline.java")).replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int from = pipeline.indexOf("private void drawDeveloped("), to = pipeline.indexOf(" private ", from + 1);
        String developed = from < 0 || to < 0 ? "" : pipeline.substring(from, to);
        Checks.check(developed.contains("atmosphere(develop, state);") && developed.contains("develop.set(\"uRangeFrom\", rangeFrom);"),
                "the develop pass is not told the air's thickness and where its count of distance starts, as the world's is");
        Checks.check(developed.contains("develop.set(\"uFogStart\", fogStart);") && developed.contains("develop.set(\"uFogEnd\", fogEnd);")
                && developed.contains("develop.set(\"uLoaded\", loadedX - camera.eyeX, loadedY - camera.eyeY, loadedWithin);") && developed.contains("develop.set(\"uBeyond\", beyond);"),
                "the develop pass is not told where the world ends, as the world's is");
    }

    /**
     * Out on the map a float holds a place to about a millimetre, a third of a pixel at the feet. The steadied picture and
     * the lit air find where a pixel was in the last frame from the eye, with the eye's move taken on the CPU, so no
     * place on the map is made in the shader and no two of them are taken from each other there. Read, not run.
     */
    private static void theLastFrameIsFoundFromTheEyeNotTheMap(List<Linked> programs) {
        String steady = fragment(programs, "steady").replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        Checks.check(!steady.contains("uEye ") && !steady.contains("uEye;") && !steady.contains("uEyeBefore"), "the steadied picture is told where the eyes are on the map");
        Checks.check(steady.contains("vec3 from = seen + uEyeMoved;") && steady.contains("float far = distance(seen.xy, uMover[i].xy);")
                && steady.contains("vec3 limb = seen - uMover[i].xyz;"), "the steadied picture does not find the last frame from the eye");
        String air = fragment(programs, "lit air").replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        Checks.check(!air.contains("uEyeBefore") && air.contains("vec3 seen = ray * ahead + uEyeMoved;"), "the lit air does not find the last frame from the eye");
        String pipeline;
        try {
            pipeline = Files.readString(SOURCE.resolve("Pipeline.java")).replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Checks.check(pipeline.contains("steady.set(\"uEyeMoved\", (float) (camera.exactX() - before.exactX()), (float) (camera.exactY() - before.exactY()), (float) (camera.exactUp() - before.exactUp()));")
                && pipeline.contains("steady.set(MOVER_AT[i], movers.now[i * 4] - camera.eyeX - camera.eyeFineX, movers.now[i * 4 + 1] - camera.eyeY - camera.eyeFineY, movers.now[i * 4 + 2] - camera.eyeUp - camera.eyeFineUp,")
                && pipeline.contains("steady.set(MOVER_BEFORE[i], movers.before[i * 4] - before.eyeX - before.eyeFineX, movers.before[i * 4 + 1] - before.eyeY - before.eyeFineY, movers.before[i * 4 + 2] - before.eyeUp - before.eyeFineUp,"),
                "the steadied picture is not told the eye's move, or where those who move are from the eye, now and a frame ago");
        Checks.check(pipeline.contains("volume.set(\"uEyeMoved\", (float) (camera.exactX() - airBefore.exactX()), (float) (camera.exactY() - airBefore.exactY()), (float) (camera.exactUp() - airBefore.exactUp()));"),
                "the lit air is not told the eye's move since its last frame");
    }

    private static final Pattern PLACED_BY_THE_MATRIX = Pattern.compile("gl_ModelViewProjectionMatrix \\* vec4\\(([^;]*?), 1\\.0\\)");

    /**
     * A picture drawn aside from the world's (Pipeline.lookFrom) is given only the turn of the view, with the eye's fine part
     * (ViewCamera.turned), so each program drawn through it takes the eye's float place off a vertex itself, as the world's
     * does: one that did not would draw its drops or its debris as far again from the eye as the eye is from the map's corner.
     * The bolt is a mile off and is drawn through the whole view. Read, not run.
     */
    private static void whatIsDrawnThroughTheTurnTakesTheEyeOffItself(List<Linked> programs) {
        for (String name : List.of("rain", "snow", "dust", "falling leaves", "debris of shots", "bullet holes")) {
            String vertex = vertex(programs, name).replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
            Matcher placed = PLACED_BY_THE_MATRIX.matcher(vertex);
            int found = 0;
            while (placed.find()) {
                found++;
                Checks.check(placed.group(1).contains("- uEye"), "the " + name + " program puts a place on the map through the turn alone: " + placed.group(0));
            }
            Checks.check(found > 0, "the " + name + " program places nothing by the matrix, which this cannot read");
        }
        String bolt = vertex(programs, "lightning").replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        Checks.check(!bolt.contains("uEye"), "the bolt takes the eye off itself, and is drawn through the whole view");
        String pipeline;
        try {
            pipeline = Files.readString(SOURCE.resolve("Pipeline.java")).replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Checks.check(pipeline.contains("GL11.glMatrixMode(GL_MODELVIEW); ViewCamera.turned(camera, viewed); GL11.glLoadMatrixf(viewed); }"),
                "a picture drawn aside is not given the turn of the view alone");
        Checks.check(pipeline.contains("lookFrom(camera, tanHalfFieldOfView, aspect, near, far, asideX, asideY); ViewCamera.view(camera, boltView); GL11.glLoadMatrixf(boltView);"),
                "the bolt is not drawn through the whole view");
    }

    /** A wall's skin is seen from the side it stands out to, as a wall's two faces are, and left out from behind. Read, not run. */
    private static void aWallSkinHasNoBack(List<Linked> programs) {
        Checks.check(SpriteGeometry.SHEET == 6f && SpriteGeometry.SEEN_ALONG == 7f && SpriteGeometry.SEEN_AGAINST == 8f,
                "the one-sided codes are the two above every code that lies a face off its plane");
        String vertex = PipelineShaders.WORLD_VERTEX.replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        Checks.check(vertex.contains("vSeen = uModel < 0.5 && gl_MultiTexCoord1.z > 6.5 ? (gl_MultiTexCoord1.z < 7.5 ? 1.0 : -1.0) : 0.0;"),
                "the world's vertices read which side of a skin is seen");
        Checks.check(vertex.contains("if (kind > 2.5 && kind < 6.5) {") && vertex.contains("gl_MultiTexCoord1.z > 4.5 && gl_MultiTexCoord1.z < 6.5"),
                "and lie a one-sided skin on its own plane");
        String world = fragment(programs, "world").replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        int discarded = world.indexOf("if (vSeen * dot(uEye - vWorld, vNormal) < 0.0) { discard; }");
        Checks.check(discarded >= 0 && discarded < world.indexOf("if (depthOnly)"), "a skin seen from behind is left out, depth and all");
    }

    /**
     * A wall's grime lies a layer out from the wall's skin. An eye in the wall's thickness, at its end, has the skin cut by
     * the near plane and the grime a layer further out just past it: a sheet 5 cm from the eye over half the picture that wrote
     * its depth and kept the sky out of it, so half the sky came out black (13770,1874, looking east). Read, not run.
     */
    private static void grimeIsSeenOnlyFromItsSide(List<Linked> programs) {
        String world = fragment(programs, "world").replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        Checks.check(world.contains("pictureScreen = vec2(dFdx(vUv.y), dFdy(vUv.y)); }"
                + " if (stainTint && dot(uEye - vWorld, vLies) <= 0.0) { discard; }"),
                "a wall's grime is drawn for an eye behind the layer it lies out to");
    }

    /**
     * What is laid over the ground writes depth. Ground spreading over its neighbour fades out by `shown`, and a pixel
     * of it that shows nothing, left in, still wrote its depth: where nothing of the built world was drawn beneath it,
     * the sky and the far land were then kept out of that pixel, which stayed black (offline, at the end of the loaded
     * map and along a far kerb, in every mode). And it lies a layer over its square's ground, lifted along the line it
     * is seen on, so its ground is back along that line by what it was lifted (vLies): where that ground had thinned out
     * into the far land, or past the loaded map, a pixel of it that showed in part was mixed over nothing. So is all that
     * is laid (some other layer left the last specks in knob-fog High). Walked back as if lifted straight up, the ground
     * was found past the true one, and the cut on its square took a band off the far edge of spread ground. Read, not
     * run: what is laid is thinned out and cut off under the water where its ground is, and spread ground is left out
     * where it shows next to nothing.
     */
    private static void spreadGroundThatDoesNotShowHidesNothing(List<Linked> programs) {
        String world = fragment(programs, "world").replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        String spread = "float shown = 1.0; if (uLaid > 0.5 && !grass && !stainTint && vMaterial < 56.5 && laidCover < 0.999) {"
                + " float ragged = patches(vWorld.xy * 1.9) * 0.6 + patches(vWorld.xy * 6.1 + 17.0) * 0.4 - 0.5;"
                + " float crumbs = (patches(vWorld.xy * 17.0 + 5.0) * 0.6 + patches(vWorld.xy * 43.0 + 29.0) * 0.4 - 0.5)"
                + " * (1.0 - smoothstep(0.012, 0.04, length(fwidth(vWorld.xy))));"
                + " float proud = laidCover + (ragged * 3.0 + crumbs * 2.0) * laidCover * (1.0 - laidCover);"
                + " float blur = max(fwidth(proud), 0.012); shown = smoothstep(0.5 - blur, 0.5 + blur, proud);";
        int at = world.indexOf(spread);
        Checks.check(at >= 0 && world.indexOf(spread, at + 1) < 0, "the world's fragment stage does not work out once how much spread ground shows");
        Checks.check(world.startsWith(" if (shown < 0.03) { discard; } }", at + spread.length()),
                "spread ground is drawn where it shows next to nothing");
        Checks.check(world.contains("vec3 under = uLaid > 0.5 && vLies.z > 0.0 ? vWorld - vLies : vWorld; if (thinnedOut(under.xy - uEye.xy)) { discard; }"
                + " if (uUnder.z > 0.0 && (vKind > 3.5 && !grass || dot(uUnder.xyz, under) < uUnder.w)) { discard; }"),
                "what is laid is thinned out into the far land, or cut off under the water, where it was lifted to, not where its ground is");
        String vertex = vertex(programs, "world").replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        Checks.check(vertex.contains("vWorld = at.xyz + lies; vLies = lies;"), "the fragment stage is not told how far a vertex lies over its surface");
        // Lifted straight up, a layer seen from above slid up the picture off its square and bared a line of the
        // ground under it along each near edge: the overhead view showed pale lines between squares of spread ground,
        // and at a slant, bare ground showed in lines where grass spread over it met the grass's own square.
        Checks.check(vertex.contains("if (uModel < 0.5 && !strip && gl_MultiTexCoord1.z > 4.5 && gl_MultiTexCoord1.z < 6.5) { vec3 toEye = normalize(uEye - at.xyz);"
                + " lies = toEye.z > 0.0 && lies.z > 0.0 ? toEye * (lies.z / max(toEye.z, 0.2)) : lies; } vWorld = at.xyz + lies;"),
                "what is laid and looked down on is not lifted along the line it is seen on, and slides off its square");
    }

    private static String fragment(List<Linked> programs, String name) {
        for (Linked program : programs) {
            if (program.name.equals(name)) {
                return program.stages.get("frag");
            }
        }
        throw new AssertionError("no program is linked as \"" + name + "\"");
    }

    private static List<Linked> programs() {
        List<Linked> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE).filter(Files::isRegularFile)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString(), text = Files.readString(file);
                String owner = name.substring(0, name.length() - ".java".length());
                int handsOver = count(text, "glShaderSource("), known = HANDS_OVER.contains(name) ? 1 : 0;
                if (handsOver != known) {
                    throw new AssertionError(name + " calls glShaderSource in " + handsOver + " places and ShaderTests knows how the source is put together in "
                            + known + ": teach it the new place, or take the file off its list");
                }
                int before = found.size();
                for (Matcher linked = LINKED.matcher(text); linked.find(); ) {
                    found.add(new Linked(linked.group(1), name, stages("vert", text(owner, linked.group(2)), "frag", text(owner, linked.group(3)))));
                }
                expect(name, "Program.link(", count(text, "Program.link("), found.size() - before);
                before = found.size();
                for (Matcher small = SMALL.matcher(text); small.find(); ) {
                    found.add(new Linked(small.group(1), name, stages("vert", text(owner, small.group(2)), "frag", text(owner, small.group(3)))));
                }
                expect(name, "new SmallProgram(", count(text, "new SmallProgram("), found.size() - before);
                if (name.equals("CharacterDraw.java")) {
                    before = found.size();
                    for (Matcher staged = STAGED.matcher(text); staged.find(); ) {
                        // The first argument after the name is where a refusal is written.
                        String[] arguments = staged.group(2).split(",");
                        Map<String, String> stages = new LinkedHashMap<>();
                        for (int at = 1; at < arguments.length; at += 2) {
                            String stage = STAGE.get(arguments[at].strip());
                            if (stage == null || stages.put(stage, text(owner, arguments[at + 1])) != null) {
                                throw new AssertionError("cannot read the stages of the " + staged.group(1) + " program in " + name + ": " + staged.group(2));
                            }
                        }
                        found.add(new Linked(staged.group(1), name, stages));
                    }
                    expect(name, "program(\"", count(text, " program(\""), found.size() - before);
                }
                if (name.equals("OwnArms.java")) {
                    Matcher arms = ARMS.matcher(text);
                    if (!arms.find()) {
                        throw new AssertionError("cannot find the sources OwnArms.java compiles");
                    }
                    found.add(new Linked("arms", name, stages("vert", text(owner, arms.group(1)), "frag", text(owner, arms.group(2)))));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    private static void expect(String file, String call, int written, int read) {
        if (written != read) {
            throw new AssertionError(file + " has " + written + " of " + call + " and ShaderTests could read the sources of " + read);
        }
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }

    private static Map<String, String> stages(String first, String firstText, String second, String secondText) {
        Map<String, String> stages = new LinkedHashMap<>();
        stages.put(first, firstText);
        stages.put(second, secondText);
        return stages;
    }

    /** The text an expression at a linking place stands for: fields, of the class it is written in or of a class it names, joined by +. */
    private static String text(String owner, String expression) {
        StringBuilder text = new StringBuilder();
        for (String term : expression.split("\\+")) {
            String[] parts = term.strip().split("\\.");
            if (parts.length > 2 || !parts[parts.length - 1].matches("[A-Z][A-Z_0-9]*")) {
                throw new AssertionError("cannot read the shader source \"" + expression.strip() + "\" in " + owner + ".java");
            }
            try {
                Field field = Class.forName(ShaderTests.class.getPackageName() + "." + (parts.length == 2 ? parts[0] : owner), true, WITH_STUBS)
                        .getDeclaredField(parts[parts.length - 1]);
                field.setAccessible(true);
                text.append((String) field.get(null));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("cannot read the shader source \"" + expression.strip() + "\" in " + owner + ".java", e);
            }
        }
        return text.toString();
    }
}

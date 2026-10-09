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
    private static final Pattern SMALL = Pattern.compile("new SmallProgram\\(\\s*\"([^\"]+)\"\\s*,([^,()]+),([^,()]+)\\)");
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
        Checks.run("wiresAreOutOfTheMapFromAbove", () -> wiresAreOutOfTheMapFromAbove(programs));
        Checks.run("wallsAskTheSunFromUnderTheirRoof", () -> wallsAskTheSunFromUnderTheirRoof(programs));
        Checks.run("whatHangsOnACordSwingsInTheViewAndInItsShadowAlike", () -> whatHangsOnACordSwingsInTheViewAndInItsShadowAlike(programs));
        Checks.run("everyProgramPassesTheReferenceCompiler", () -> everyProgramPassesTheReferenceCompiler(programs));
    }

    /**
     * The depth program is the world's text with one line put in place of another. Were the line reworded in the
     * shader and not where it is looked for, it would be the world's whole program: the same picture, slower.
     */
    private static void depthProgramIsTheWorldsToldToDrawDepthAlone(List<Linked> programs) {
        String whole = fragment(programs, "world"), depth = fragment(programs, "world depth");
        Checks.check(!whole.equals(depth) && depth.contains("const bool depthOnly = true;"), "the world depth program is not the world's program told to draw depth alone");
    }

    private static void everyProgramPassesTheReferenceCompiler(List<Linked> programs) throws IOException, InterruptedException {
        if (Files.exists(OUT)) {
            try (Stream<Path> old = Files.list(OUT)) {
                for (Path file : old.toList()) {
                    Files.delete(file);
                }
            }
        }
        Files.createDirectories(OUT);
        int passed = 0;
        for (Linked program : programs) {
            List<String> command = new ArrayList<>(List.of(VALIDATOR, "-l", "--validate-io"));
            for (Map.Entry<String, String> stage : program.stages.entrySet()) {
                Path file = OUT.resolve(program.name.replace(' ', '-') + "." + stage.getKey());
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
            boolean taken = validator.waitFor() == 0 && !said.contains("WARNING") && !said.contains("ERROR");
            Checks.check(taken, "glslangValidator refuses the " + program.name + " shader (" + program.from + "), which the mod compiles in the game."
                    + " Its source is in " + OUT + ":\n" + said.strip());
            passed += taken ? 1 : 0;
        }
        System.out.println("shader tests: " + passed + " of " + programs.size() + " programs pass " + VALIDATOR);
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
                for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                    float[] v = mesh.vertices;
                    float squared = v[at + 9] * v[at + 9] + v[at + 10] * v[at + 10] + v[at + 11] * v[at + 11];
                    Checks.check(squared > line, "a vertex of a bulb hung right at a support has a facing of " + squared + " squared, which the vertex stages do not take for hanging (over "
                                + over + "): it would stand still while its cord swings");
                }
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
     * stage into a sun map are made, for what is roofed, from where sunAsked puts the point; sunAsked lowers a point
     * only where something is over it, by the offset, a texel and the lookup's own spread; a point lowered out of the
     * near map is asked of the far one; the program is told the offset the maps are drawn with; and the shadow maps
     * keep a sheet's clear pixels and no other face's.
     */
    private static void wallsAskTheSunFromUnderTheirRoof(List<Linked> programs) {
        String world = fragment(programs, "world").replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        Checks.check(world.contains("bool roofed = (open < 0.5 || abs(normalize(vNormal).z) < 0.5 && !card) && !water && uCutMarked.z <= 0.0;"), "the world's fragment stage does not say what is roofed before it asks the sun maps");
        Checks.check(world.contains("float beneath = open < 0.5 ? 0.16 * uRoofPitch : 0.0;"), "a point in a room is not asked from under the ceiling of a pitched roof, or one out of doors is");
        Checks.check(!(!world.contains("if (roofed) { c = (uNearMatrix * vec4(sunAsked(from.xyz, soft, uSunTexelUp.x, beneath), 1.0)).xyz * 0.5 + 0.5; inNear = inside(c); }"
                + " if (inNear) { reached = shadowAt(uShadowNear, c, soft); } }")
                || !world.contains("if (!inNear) { if (roofed) { from.xyz = sunAsked(from.xyz, 1.6, uSunTexelUp.y, beneath); }"
                        + " c = (uFarMatrix * from).xyz * 0.5 + 0.5; reached = inside(c) ? shadowAt(uShadowFar, c, 1.6) : 1.0; }")
                || count(world, "shadowAt(uShadow") != 2), "the world's fragment stage asks a sun map about what is roofed from where the map cannot tell it from its roof");
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
        Checks.check(!(!casterVertex.contains("vSolid = gl_MultiTexCoord1.z > 5.5 ? 1.0 : 0.0;") || SpriteGeometry.SHEET != 6f
                || SpriteGeometry.LAYERS_UP >= 5.5f), "the shadow maps keep the clear pixels of something that is not a sheet, or of no sheet");
        Checks.check(casterVertex.contains("(kind < 3.5 ? vec3(1.0, 0.0, 0.0) : kind < 4.5 ? vec3(0.0, 1.0, 0.0) : vec3(0.0, 0.0, 1.0))"), "a sheet does not lie up as what lies in layers up does");
        String scene = LowTests.scene();
        Checks.check(!(count(scene, "layers.sheet(") != 3 || count(scene, "layers.sheet(-1f); ") != 2
                || !scene.contains("private void layFloor(boolean floor) { if (floor && build.floorLayer == 0) { WorldScene.layers.sheet(build.floorLayer++); }"
                        + " else { WorldScene.layers.lie(SpriteGeometry.LAYERS_UP, build.floorLayer++); } }")
                || !scene.contains("layFloor(isFloor && build.frame.kind == 0f);")), "the scene does not mark the first layer of a floor and both ceilings a sheet, and only those");
        Checks.check(!(count(pipeline, "glPolygonOffset(") != 1 || !pipeline.contains("GL11.glPolygonOffset(SLOPE_OFFSET, 6f);")
                || !pipeline.contains("float across = (float) Math.hypot(state.lightX, state.lightY); "
                        + "to.set(\"uSunTexelUp\", 2f * NEAR_EXTENT / shadowSize * across, 2f * FAR_EXTENT / shadowSize * across); "
                        + "to.set(\"uSlopeOffset\", SLOPE_OFFSET); to.set(\"uRoofDeep\", 2f * ROOF_RANGE);"
                        + " to.set(\"uRoofPitch\", RoofFit.PITCH * ViewCamera.LEVEL_HEIGHT);")), "the world's program is not told the offset the sun maps are drawn with,"
                    + " a texel of each map as the light lies, or the depth of the map from above");
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
        try (Stream<Path> files = Files.list(SOURCE)) {
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

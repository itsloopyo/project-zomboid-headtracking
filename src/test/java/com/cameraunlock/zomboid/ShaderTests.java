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
        try {
            if (Files.exists(OUT)) {
                try (Stream<Path> old = Files.list(OUT)) {
                    for (Path file : old.toList()) {
                        Files.delete(file);
                    }
                }
            }
            Files.createDirectories(OUT);
            StringBuilder refused = new StringBuilder();
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
                if (validator.waitFor() != 0 || said.contains("WARNING") || said.contains("ERROR")) {
                    refused.append("the ").append(program.name).append(" shader (").append(program.from).append("):\n").append(said.strip()).append("\n\n");
                }
            }
            if (refused.length() > 0) {
                throw new AssertionError("glslangValidator refuses shaders the mod compiles in the game. Each source is in " + OUT + ".\n\n" + refused);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        System.out.println("shader tests: " + programs.size() + " programs pass " + VALIDATOR);
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

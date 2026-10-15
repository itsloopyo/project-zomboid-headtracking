package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tests of the game's basicEffect shaders as GameMouths rewrites them. The game's own files are
 * not in this repository: the stand-ins here are written for these tests, in the shape the
 * game's have where the rewrite reads them, and what the rewrite makes of them is put through
 * glslangValidator, each vertex stage linked with the fragment stage it is compiled beside.
 */
final class GameMouthsTests {
    private GameMouthsTests() {}

    private static final Path OUT = Path.of("build/shaders-game");
    private static final String FOLDER = "C:/Games/ProjectZomboid/media/shaders/";

    private static final String SKINNED = """
            #version 330

            layout (location = 0) in vec4 vertex;
            layout (location = 1) in vec4 normal;
            layout (location = 2) in vec4 boneWeights;
            layout (location = 3) in vec4 boneIndices;
            layout (location = 4) in vec2 uv;

            out vec3 vertColour;
            out vec3 vertNormal;
            out vec2 texCoords;

            uniform mat4 ModelViewProjection;
            uniform mat4 MatrixPalette[60];

            void main()
            {
            \tvec4 position = vec4(vertex.xyz, 1.0);
            \tvec4 normal = vec4(normal.xyz, 0.0);

            \ttexCoords = uv;
            \tmat4 bones = MatrixPalette[int(boneIndices.x)] * boneWeights.x + MatrixPalette[int(boneIndices.y)] * boneWeights.y;
            \tvertNormal = (bones * normal).xyz;
            \tvertColour = vec3(1.0);
            \tgl_Position = ModelViewProjection * (bones * position);
            }
            """;

    private static final String HUNG = """
            #version 330

            layout (location = 0) in vec4 vertex;
            layout (location = 1) in vec4 normal;
            layout (location = 2) in vec2 uv;

            out vec3 vertColour;
            out vec3 vertNormal;
            out vec2 texCoords;

            uniform mat4 ModelViewProjection;
            uniform mat4 transform;

            void main()
            {
            \tvec4 position = vec4(vertex.xyz, 1);
            \tvec4 normal = vec4(normal.xyz, 0);
            \ttexCoords = uv;
            \tvertNormal = (transform * normal).xyz;
            \tvertColour = vec3(1,1,1);
            \tgl_Position = ModelViewProjection * (transform * position);
            }
            """;

    private static final String FRAGMENT = """
            #version 110

            varying vec3 vertColour;
            varying vec3 vertNormal;
            varying vec2 texCoords;

            uniform sampler2D Texture;
            uniform vec3 AmbientColour;
            uniform vec3 Light0Direction;
            uniform vec3 Light0Colour;

            void main()
            {
            \tvec4 texSample = texture2D(Texture, texCoords);
            \tvec3 col = texSample.xyz;
            \tvec3 lighting = vec3(0.0);
            \tlighting += Light0Colour * max(dot(normalize(vertNormal), Light0Direction), 0.0);
            \tlighting += AmbientColour;
            \tgl_FragColor = vec4(col * min(lighting, vec3(1.0)) * vertColour, texSample.w);
            }
            """;

    static void run() {
        Checks.run("theSkinnedProgramTurnsTheJawAndDrawsTheMouthsInside", GameMouthsTests::theSkinnedProgramTurnsTheJawAndDrawsTheMouthsInside);
        Checks.run("aPartHungOnABoneWritesNoMouthForTheSameFragmentStage", GameMouthsTests::aPartHungOnABoneWritesNoMouthForTheSameFragmentStage);
        Checks.run("aVertexStageNotAsExpectedLeavesItsProgramAsItIs", GameMouthsTests::aVertexStageNotAsExpectedLeavesItsProgramAsItIs);
        Checks.run("aFragmentStageNotAsExpectedLeavesTheMouthsShut", GameMouthsTests::aFragmentStageNotAsExpectedLeavesTheMouthsShut);
        Checks.run("otherShadersAreLeftAsTheyAre", GameMouthsTests::otherShadersAreLeftAsTheyAre);
    }

    private static void theSkinnedProgramTurnsTheJawAndDrawsTheMouthsInside() throws Exception {
        String vertex = GameMouths.source(SKINNED, FOLDER + "basicEffect.vert");
        String fragment = GameMouths.source(FRAGMENT, FOLDER + "basicEffect.frag");
        check(vertex.contains("position.xyz = jawedPlace(vertex.xyz, mouthTurn, mouthHead);") && vertex.contains("normal.xyz = jawed(normal.xyz, mouthTurn);")
                && vertex.indexOf("mouthTurn") > vertex.indexOf("vec4 normal = vec4(normal.xyz, 0.0);")
                && vertex.indexOf("mouthTurn") < vertex.indexOf("vertNormal = (bones * normal).xyz;"),
                "the corner and its normal are turned with the jaw as made, before the bones place them");
        check(vertex.contains("uniform float uHeadBone = -1.0;"), "a part drawn with nothing set has no head bone, and so no mouth");
        check(fragment.contains("float gap = mouthGap(inside);") && fragment.indexOf("mouthGap(inside)") < fragment.indexOf("vec3 lighting = vec3(0.0);"),
                "the inside of the mouth is drawn over the texture's colour before it is lit");
        check(GameMouths.taken(), "and the game's people open their mouths");
        String refused = ShaderTests.refusal(OUT, "basicEffect", stages(vertex, fragment));
        check(refused == null, "glslangValidator takes the rewritten skinned program:\n" + refused);
    }

    private static void aPartHungOnABoneWritesNoMouthForTheSameFragmentStage() throws Exception {
        GameMouths.source(SKINNED, FOLDER + "basicEffect.vert");
        GameMouths.source(FRAGMENT, FOLDER + "basicEffect.frag");
        String vertex = GameMouths.source(HUNG, FOLDER + "basicEffect_static.vert");
        String fragment = GameMouths.source(FRAGMENT, FOLDER + "basicEffect.frag");
        check(vertex.contains("vRest = vec3(0.0);") && !vertex.contains("jawed") && fragment.contains("mouthGap(inside)"),
                "the hung part's vertex stage writes what the rewritten fragment stage reads, and turns nothing");
        check(GameMouths.taken(), "and the skinned program still opens mouths");
        String refused = ShaderTests.refusal(OUT, "basicEffect static", stages(vertex, fragment));
        check(refused == null, "glslangValidator takes the rewritten static program:\n" + refused);
    }

    private static void aVertexStageNotAsExpectedLeavesItsProgramAsItIs() {
        String other = SKINNED.replace("vec4 normal = vec4(normal.xyz, 0.0);", "vec4 normal = normal;");
        check(GameMouths.source(other, FOLDER + "basicEffect.vert").equals(other), "a skinned vertex stage that does not have its corner as made is left as it is");
        check(GameMouths.source(FRAGMENT, FOLDER + "basicEffect.frag").equals(FRAGMENT), "and so is the fragment stage after it, which nothing would write the mouth for");
        check(!GameMouths.taken(), "and the game's people keep their mouths shut");
    }

    private static void aFragmentStageNotAsExpectedLeavesTheMouthsShut() {
        String other = FRAGMENT.replace("vec3 lighting = vec3(0.0);", "vec3 lighting = AmbientColour;");
        check(!GameMouths.source(SKINNED, FOLDER + "basicEffect.vert").equals(SKINNED), "the vertex stage is rewritten");
        check(GameMouths.source(other, FOLDER + "basicEffect.frag").equals(other), "a fragment stage that does not light as expected is left as it is");
        check(!GameMouths.taken(), "and the game's people keep their mouths shut");
    }

    private static void otherShadersAreLeftAsTheyAre() {
        check(GameMouths.source(SKINNED, FOLDER + "basicEffect.vert") != null, "the skinned vertex stage is read");
        check(GameMouths.source(HUNG, FOLDER + "vehicle_static.vert").equals(HUNG), "another vertex stage is left as it is");
        check(GameMouths.source(FRAGMENT, FOLDER + "basicEffect.frag").equals(FRAGMENT), "and the fragment stage read after it too");
        check(GameMouths.source(FRAGMENT, FOLDER + "vehicle.frag").equals(FRAGMENT) && GameMouths.source(null, FOLDER + "basicEffect.vert") == null,
                "another fragment stage, and a file the game could not read, are left as they are");
    }

    private static Map<String, String> stages(String vertex, String fragment) {
        Map<String, String> stages = new LinkedHashMap<>();
        stages.put("vert", vertex);
        stages.put("frag", fragment);
        return stages;
    }
}

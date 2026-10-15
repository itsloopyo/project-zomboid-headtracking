package com.cameraunlock.zomboid;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import zombie.core.opengl.ShaderProgram;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.core.skinnedmodel.shader.Shader;
import zombie.core.textures.Texture;

/**
 * Talking mouths on the people the game draws itself: the isometric view in its Low, where the
 * mod draws nothing, and any part the picture leaves to the game's draw. The game draws a person's
 * skinned parts with its basicEffect program, from basicEffect.vert and basicEffect.frag, and a
 * part hung whole on a bone from basicEffect_static.vert and the same basicEffect.frag. Those are
 * rewritten as they are read (Hooks.shaderSource): the skinned vertex stage turns the jaw and
 * shapes the lips as CharacterDraw.JAW does, and the fragment stage draws the inside of the mouth
 * between them as CharacterDraw.MOUTH does. A part of a person whose mouth is moving is drawn with
 * the mouth's shape set on the program, which is put back to a shut mouth after it (draw).
 *
 * The game compiles a program's vertex stage before its fragment stage, so basicEffect.frag is
 * rewritten only straight after a vertex stage that was, which writes what the rewritten fragment
 * stage reads. A shader of the game's that is not as expected is left as it is and said in the
 * log, and the people the game draws keep their mouths shut. Shaders are compiled on the render
 * thread, which is the thread that draws.
 */
final class GameMouths {
    private GameMouths() {}

    private static final String PROGRAM = "basicEffect";
    private static final String SKINNED = "basicEffect.vert", HUNG = "basicEffect_static.vert", FRAGMENT = "basicEffect.frag";

    private static final Pattern VERSION = Pattern.compile("#version\\s+(\\d+)");
    private static final Pattern MAIN = Pattern.compile("void\\s+main\\s*\\(\\s*\\)\\s*\\{");
    /** Where the skinned vertex stage has the corner and its normal as made, before the bones place them. */
    private static final Pattern AS_MADE = Pattern.compile("vec4\\s+position\\s*=\\s*vec4\\(\\s*vertex\\.xyz\\s*,\\s*1(\\.0)?\\s*\\)\\s*;\\s*"
            + "vec4\\s+normal\\s*=\\s*vec4\\(\\s*normal\\.xyz\\s*,\\s*0(\\.0)?\\s*\\)\\s*;");
    private static final Pattern BONES = Pattern.compile("\\bin\\s+vec4\\s+boneWeights\\s*;[\\s\\S]*\\bin\\s+vec4\\s+boneIndices\\s*;");
    /** Where the fragment stage begins lighting the texture's colour, after its hue is turned. */
    private static final Pattern LIGHTING = Pattern.compile("vec3\\s+lighting\\s*=\\s*vec3\\(\\s*0\\.0\\s*\\)\\s*;");

    /** The vertex stage read last, rewritten (SKINNED or HUNG) or not (null). */
    private static String vertexBefore;
    /** Whether the skinned program, both its stages, opens mouths. */
    private static volatile boolean taken;

    /** The source of a shader of the game's as it was read from fileName, rewritten if it is one of basicEffect's. */
    static String source(String code, String fileName) {
        if (code == null) {
            return null;
        }
        String name = fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1);
        if (name.endsWith(".vert")) {
            String rewritten = name.equals(SKINNED) ? skinned(code) : name.equals(HUNG) ? hung(code) : null;
            vertexBefore = rewritten == null ? null : name;
            if (name.equals(SKINNED)) {
                taken = false;
            }
            return rewritten == null ? code : rewritten;
        }
        if (!name.equals(FRAGMENT)) {
            return code;
        }
        String from = vertexBefore;
        vertexBefore = null;
        if (from == null) {
            Log.info("the game's " + FRAGMENT + " is left as it is: the vertex stage read before it was not a rewritten " + SKINNED + " or " + HUNG);
            return code;
        }
        String rewritten = fragment(code);
        if (rewritten == null) {
            Log.info("people the game draws itself keep their mouths shut: the game's " + FRAGMENT + " does not light its colour as expected");
            return code;
        }
        if (from.equals(SKINNED)) {
            taken = true;
            Log.info("people the game draws itself open their mouths: its " + SKINNED + " and " + FRAGMENT + " are rewritten");
        }
        return rewritten;
    }

    private static String skinned(String code) {
        Matcher main = MAIN.matcher(code), asMade = AS_MADE.matcher(code);
        int version = version(code);
        if (version < 130 || !BONES.matcher(code).find() || !main.find() || !asMade.find(main.end())) {
            Log.info("people the game draws itself keep their mouths shut: the game's " + SKINNED + " does not place its corners as expected");
            return null;
        }
        String declared = """
                uniform float uMouth;
                uniform float uLips;
                uniform float uHeadBone = -1.0;
                out vec3 vRest;
                out vec3 vMouth;
                """ + CharacterDraw.JAW;
        String turned = """

                    float mouthHead = headShare(boneWeights, boneIndices), mouthTurn = jawTurnAt(vertex.xyz, mouthHead);
                    position.xyz = jawedPlace(vertex.xyz, mouthTurn, mouthHead);
                    normal.xyz = jawed(normal.xyz, mouthTurn);
                    vRest = vertex.xyz;
                    vMouth = mouthHeights(vertex.xyz, mouthTurn, mouthHead);
                """;
        return code.substring(0, main.start()) + declared + "\n" + code.substring(main.start(), asMade.end()) + turned + code.substring(asMade.end());
    }

    /** A part hung whole on a bone has no jaw: it writes what the fragment stage reads, as a point of no mouth. */
    private static String hung(String code) {
        Matcher main = MAIN.matcher(code);
        if (version(code) < 130 || !main.find()) {
            Log.info("the game's " + HUNG + " is left as it is: it has no main as expected");
            return null;
        }
        return code.substring(0, main.start()) + "out vec3 vRest;\nout vec3 vMouth;\n\n" + code.substring(main.start(), main.end())
                + "\n    vRest = vec3(0.0);\n    vMouth = vec3(0.0);" + code.substring(main.end());
    }

    private static String fragment(String code) {
        Matcher main = MAIN.matcher(code), lighting = LIGHTING.matcher(code);
        if (!main.find() || !lighting.find(main.end())) {
            return null;
        }
        String from = version(code) >= 130 ? "in" : "varying";
        String declared = from + " vec3 vRest;\n" + from + " vec3 vMouth;\nuniform float uMouthGap;\nuniform float uLips;\n" + CharacterDraw.MOUTH;
        String inside = "if (uMouthGap > 0.0) {\n        vec3 inside;\n        float gap = mouthGap(inside);\n        col = mix(col, inside, gap);\n    }\n    ";
        return code.substring(0, main.start()) + declared + "\n" + code.substring(main.start(), lighting.start()) + inside + code.substring(lighting.start());
    }

    /** Whether the people the game draws with its skinned program open their mouths: both of its stages are rewritten. */
    static boolean taken() {
        return taken;
    }

    private static int version(String code) {
        Matcher version = VERSION.matcher(code);
        return version.find() ? Integer.parseInt(version.group(1)) : 110;
    }

    /**
     * Draws a part of a model by the game's own draw, in place of its draw of the part. A part of
     * a person whose mouth is moving, drawn by the skinned basicEffect program, is drawn with the
     * mouth's shape set on the program: the jaw's drop and the lips' shape on every part carried
     * by the head's bone, and the inside of the mouth on the body alone, as CharacterDraw draws it.
     */
    static void draw(Model model, ModelSlotRenderData slot, ModelInstanceRenderData part) {
        Shader effect = model.effect;
        Mouths.Shape mouth = taken && !model.isStatic && effect != null && PROGRAM.equals(effect.getName()) && !effect.isInstanced()
                ? Mouths.of(slot.character) : Mouths.Shape.SHUT;
        float head = mouth.moving() ? CharacterDraw.headBone(model) : -1f;
        if (head < 0f) {
            model.DrawChar(slot, part);
            return;
        }
        Texture texture = part.tex;
        boolean body = texture != null && CharacterDraw.wearOf(model.name, texture.getName(), texture.getClass().getSimpleName().equals("CharacterSmartTexture")) == CharacterDraw.BODY;
        ShaderProgram program = effect.getShaderProgram();
        program.Start();
        program.setValue("uMouth", mouth.open());
        program.setValue("uLips", mouth.lips());
        program.setValue("uHeadBone", head);
        program.setValue("uMouthGap", body ? mouth.parted() : 0f);
        try {
            model.DrawChar(slot, part);
        } finally {
            program.Start();
            program.setValue("uMouth", 0f);
            program.setValue("uLips", 0f);
            program.setValue("uHeadBone", -1f);
            program.setValue("uMouthGap", 0f);
            program.End();
        }
    }
}

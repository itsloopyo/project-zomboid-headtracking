package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL40;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelMesh;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.core.skinnedmodel.model.SkinningData;
import zombie.core.skinnedmodel.model.VertexBufferObject;
import zombie.core.skinnedmodel.shader.Shader;
import zombie.core.textures.Texture;
import zombie.iso.Vector2;
import zombie.scripting.objects.ModelScript;

/**
 * The picture's own draw of a person standing in the world. The game's draw of a person is a
 * few hundred flat-sided triangles, which from the game's own distance is all that is wanted
 * and from an arm's length is a figure cut from card. Here each of a person's parts is drawn
 * from a copy of its mesh that also knows, at every corner, which way the surface faces once
 * the faces round it are run together, and with that the triangles near the eye are divided
 * and bowed outwards into the round shape they stand for.
 *
 * The bones, the textures and the colours are the game's, read from what it posed this frame,
 * and what is written is what the game's draw writes: the same colour and the same alpha.
 *
 * Render thread only.
 */
final class CharacterDraw {
    private CharacterDraw() {}

    /** The game's program for a person's skinned parts. A part drawn with another is left to the game. */
    private static final String GAME_PROGRAM = "basicEffect";
    /** How many bones the game poses for one part. */
    private static final int BONES = 60;
    /** How many lights the game lays on one person. */
    private static final int LIGHTS = 5;
    /** How much of the light where a person stands the game gives every side of them. */
    private static final float AMBIENT_SHARE = 0.45f;
    /** The game draws nothing of a person, or of a texel, fainter than this. */
    private static final float FAINTEST = 0.01f;
    /** Within this many tiles of the eye a triangle is divided most finely, and beyond the second not at all. */
    private static final float FINE_WITHIN = 3f, WHOLE_BEYOND = 12f;

    /** Place, normal, bone weights, bone numbers, texture place, run-together normal. */
    private static final int FLOATS = 19, STRIDE = FLOATS * 4;
    private static final int PLACE = 0, NORMAL = 1, WEIGHTS = 2, BONE_NUMBERS = 3, UV = 4, SMOOTH = 5, ATTRIBUTES = 6;
    private static final int[] SIZE = {3, 3, 4, 4, 2, 3};
    private static final int[] OFFSET = {0, 12, 24, 40, 56, 64};
    private static final String[] ATTRIBUTE_NAMES = {"aPlace", "aNormal", "aWeights", "aBones", "aUv", "aSmooth"};

    private static final int GL_VERSION = 0x1F02;
    private static final int GL_TRIANGLES = 0x0004, GL_PATCHES = 0x000E, GL_PATCH_VERTICES = 0x8E72;
    private static final int GL_FLOAT = 0x1406, GL_UNSIGNED_INT = 0x1405;
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_ELEMENT_ARRAY_BUFFER = 0x8893, GL_BUFFER_SIZE = 0x8764, GL_STATIC_DRAW = 0x88E4;
    private static final int GL_VERTEX_SHADER = 0x8B31, GL_FRAGMENT_SHADER = 0x8B30, GL_TESS_CONTROL_SHADER = 0x8E88, GL_TESS_EVALUATION_SHADER = 0x8E87;
    private static final int GL_COMPILE_STATUS = 0x8B81, GL_LINK_STATUS = 0x8B82;
    private static final int GL_CULL_FACE = 0x0B44, GL_FRONT = 0x0404, GL_DEPTH_TEST = 0x0B71, GL_LESS = 0x0201, GL_LEQUAL = 0x0203, GL_BLEND = 0x0BE2;
    private static final int GL_TEXTURE0 = 0x84C0;

    private static final String OLD = """
            #version 120
            #define FROM_MESH attribute
            #define TO_NEXT varying
            #define FROM_LAST varying
            #define TEXEL texture2D
            """;
    private static final String NEW = """
            #version 400 compatibility
            #define FROM_MESH in
            #define TO_NEXT out
            #define FROM_LAST in
            #define TEXEL texture
            """;

    /**
     * A corner goes where its bones take it: each bone's matrix by how much of the corner is
     * that bone's. A bone with none of it is not read, since its number then says nothing.
     */
    private static final String POSED = """
            FROM_MESH vec3 aPlace;
            FROM_MESH vec3 aNormal;
            FROM_MESH vec4 aWeights;
            FROM_MESH vec4 aBones;
            FROM_MESH vec2 aUv;
            uniform mat4 uPalette[60];
            uniform float uScale;
            uniform vec2 uUvScale;

            mat4 posed() {
                mat4 blend = mat4(0.0);
                if (aWeights.x > 0.0) blend += uPalette[int(aBones.x)] * aWeights.x;
                if (aWeights.y > 0.0) blend += uPalette[int(aBones.y)] * aWeights.y;
                if (aWeights.z > 0.0) blend += uPalette[int(aBones.z)] * aWeights.z;
                if (aWeights.w > 0.0) blend += uPalette[int(aBones.w)] * aWeights.w;
                return blend;
            }
            """;

    private static final String WHOLE_VERTEX = POSED + """
            uniform mat4 uModelView;
            uniform mat4 uProjection;
            TO_NEXT vec3 vNormal;
            TO_NEXT vec2 vUv;

            void main() {
                mat4 blend = posed();
                vec4 place = blend * vec4(aPlace, 1.0);
                place.xyz *= uScale;
                vNormal = (blend * vec4(aNormal, 0.0)).xyz;
                vUv = aUv * uUvScale;
                gl_Position = uProjection * (uModelView * place);
            }
            """;

    private static final String DIVIDED_VERTEX = POSED + """
            FROM_MESH vec3 aSmooth;
            TO_NEXT vec3 cPlace;
            TO_NEXT vec3 cNormal;
            TO_NEXT vec3 cSmooth;
            TO_NEXT vec2 cUv;

            void main() {
                mat4 blend = posed();
                vec4 place = blend * vec4(aPlace, 1.0);
                cPlace = place.xyz * uScale;
                cNormal = (blend * vec4(aNormal, 0.0)).xyz;
                cSmooth = (blend * vec4(aSmooth, 0.0)).xyz;
                cUv = aUv * uUvScale;
            }
            """;

    /**
     * Turns a colour round the colour wheel by a part of a whole turn, keeping how bright and
     * how strong it is.
     */
    private static final String HUE = """
            vec3 turned(vec3 color, float by) {
                float most = max(color.r, max(color.g, color.b));
                float least = min(color.r, min(color.g, color.b));
                float spread = most - least;
                if (spread <= 0.0) {
                    return color;
                }
                float hue;
                if (most == color.r) {
                    hue = (color.g - color.b) / spread;
                } else if (most == color.g) {
                    hue = 2.0 + (color.b - color.r) / spread;
                } else {
                    hue = 4.0 + (color.r - color.g) / spread;
                }
                float sixth = fract(hue / 6.0 + by) * 6.0;
                vec3 pure = clamp(vec3(abs(sixth - 3.0) - 1.0, 2.0 - abs(sixth - 2.0), 2.0 - abs(sixth - 4.0)), 0.0, 1.0);
                return most * mix(vec3(1.0), pure, spread / most);
            }
            """;

    /**
     * An arm of the picture's own, from a wrist by way of an elbow to a shoulder, all three
     * given as the eye sees them. Each corner of its mesh says where it is round the arm, as
     * a cosine and a sine, and how far along: 0 at the wrist, 1 at the elbow, 2 at the
     * shoulder. Below 0 the end is closed over the wrist as half a ball, and past 2 the arm
     * runs straight on, so that its open end is never in the picture.
     *
     * The three joints lie in one plane, and the line square to it is square to both bones:
     * round the arm is measured from that line, so the skin does not twist at the elbow.
     */
    private static final String SLEEVE_VERTEX = """
            FROM_MESH vec3 aPlace;
            uniform vec3 uJoint[3];
            uniform vec3 uBend;
            uniform vec3 uWide;
            uniform mat4 uProjection;
            TO_NEXT vec3 vOut;
            TO_NEXT vec3 vWay;
            TO_NEXT vec3 vRound;
            const float ROUNDED = 0.3;

            void main() {
                float along = clamp(aPlace.z, 0.0, 2.0);
                vec3 fore = uJoint[1] - uJoint[0], upper = uJoint[2] - uJoint[1];
                vec3 middle;
                vec3 way;
                if (along < 1.0 - ROUNDED) {
                    middle = uJoint[0] + fore * along;
                    way = fore;
                } else if (along > 1.0 + ROUNDED) {
                    middle = uJoint[1] + upper * (along - 1.0);
                    way = upper;
                } else {
                    // Round the elbow, not through its corner.
                    float turn = (along - 1.0 + ROUNDED) / (2.0 * ROUNDED);
                    vec3 from = uJoint[1] - fore * ROUNDED, to = uJoint[1] + upper * ROUNDED;
                    middle = from * ((1.0 - turn) * (1.0 - turn)) + uJoint[1] * (2.0 * turn * (1.0 - turn)) + to * (turn * turn);
                    way = mix(normalize(fore), normalize(upper), turn);
                }
                way = normalize(way);
                middle += normalize(upper) * (max(aPlace.z - 2.0, 0.0) * length(upper));
                float wide = along < 1.0 ? mix(uWide.x, uWide.y, along) : mix(uWide.y, uWide.z, along - 1.0);
                vec3 ring = aPlace.x * uBend + aPlace.y * cross(way, uBend);
                float closing = (1.0 - clamp(-aPlace.z, 0.0, 1.0)) * 1.5708;
                vOut = ring * sin(closing) - way * cos(closing);
                vWay = way;
                vRound = aPlace;
                gl_Position = uProjection * vec4(middle + vOut * wide, 1.0);
            }
            """;

    /**
     * The viewer's own body is drawn in pieces: the hands on a weapon, moved to where a first
     * person view holds it, and the body without the arms those hands are on, left where the
     * game posed it. Each corner says how much of it belongs to the bones the piece is cut
     * along, and is drawn or left out by that.
     */
    private static final String CUT_VERTEX = POSED + """
            uniform mat4 uModelView;
            uniform mat4 uProjection;
            uniform float uArm[60];
            TO_NEXT vec3 vNormal;
            TO_NEXT vec2 vUv;
            TO_NEXT float vArm;

            void main() {
                mat4 blend = posed();
                vec4 place = blend * vec4(aPlace, 1.0);
                place.xyz *= uScale;
                vNormal = (blend * vec4(aNormal, 0.0)).xyz;
                vUv = aUv * uUvScale;
                float arm = 0.0;
                if (aWeights.x > 0.0) arm += uArm[int(aBones.x)] * aWeights.x;
                if (aWeights.y > 0.0) arm += uArm[int(aBones.y)] * aWeights.y;
                if (aWeights.z > 0.0) arm += uArm[int(aBones.z)] * aWeights.z;
                if (aWeights.w > 0.0) arm += uArm[int(aBones.w)] * aWeights.w;
                vArm = arm;
                gl_Position = uProjection * (uModelView * place);
            }
            """;

    /**
     * How finely an edge is divided depends on the edge alone, its two ends and nothing of the
     * triangle it belongs to, so the two triangles that share it divide it the same and no gap
     * opens between them.
     */
    private static final String DIVIDE = """
            #version 400 compatibility
            layout(vertices = 3) out;
            in vec3 cPlace[];
            in vec3 cNormal[];
            in vec3 cSmooth[];
            in vec2 cUv[];
            out vec3 ePlace[];
            out vec3 eNormal[];
            out vec3 eSmooth[];
            out vec2 eUv[];
            uniform mat4 uModelView;
            uniform vec2 uFine;
            const float FINEST = 4.0;

            float pieces(vec3 a, vec3 b) {
                float away = length((uModelView * vec4((a + b) * 0.5, 1.0)).xyz);
                return mix(FINEST, 1.0, clamp((away - uFine.x) / (uFine.y - uFine.x), 0.0, 1.0));
            }

            void main() {
                ePlace[gl_InvocationID] = cPlace[gl_InvocationID];
                eNormal[gl_InvocationID] = cNormal[gl_InvocationID];
                eSmooth[gl_InvocationID] = cSmooth[gl_InvocationID];
                eUv[gl_InvocationID] = cUv[gl_InvocationID];
                if (gl_InvocationID == 0) {
                    float first = pieces(cPlace[1], cPlace[2]);
                    float second = pieces(cPlace[2], cPlace[0]);
                    float third = pieces(cPlace[0], cPlace[1]);
                    gl_TessLevelOuter[0] = first;
                    gl_TessLevelOuter[1] = second;
                    gl_TessLevelOuter[2] = third;
                    gl_TessLevelInner[0] = max(first, max(second, third));
                }
            }
            """;

    /**
     * Each new point is moved towards the three planes that touch the surface at the
     * triangle's corners, by how near it is to each corner. The planes are the run-together
     * ones, the same for every copy of a corner the mesh holds, so where two triangles meet at
     * a seam in the texture their shared edge bows the same way from both sides. A plane that
     * leans steeply against its triangle moves a point no further than STEEPEST of the way to
     * its corner: a finger or a foot is a box of a few faces and would otherwise swell into a
     * ball. The limit is by the distance to the corner alone, so it too is the same from both
     * sides of an edge.
     */
    private static final String BOW = """
            #version 400 compatibility
            layout(triangles, fractional_odd_spacing, ccw) in;
            in vec3 ePlace[];
            in vec3 eNormal[];
            in vec3 eSmooth[];
            in vec2 eUv[];
            out vec3 vNormal;
            out vec2 vUv;
            uniform mat4 uModelView;
            uniform mat4 uProjection;
            const float ROUNDING = 0.75;
            const float STEEPEST = 0.35;

            vec3 onto(vec3 point, vec3 corner, vec3 facing) {
                vec3 across = point - corner;
                vec3 n = normalize(facing);
                float most = STEEPEST * length(across);
                return point - clamp(dot(across, n), -most, most) * n;
            }

            void main() {
                vec3 share = gl_TessCoord;
                vec3 level = share.x * ePlace[0] + share.y * ePlace[1] + share.z * ePlace[2];
                vec3 rounded = share.x * onto(level, ePlace[0], eSmooth[0]) + share.y * onto(level, ePlace[1], eSmooth[1])
                        + share.z * onto(level, ePlace[2], eSmooth[2]);
                vNormal = share.x * eSmooth[0] + share.y * eSmooth[1] + share.z * eSmooth[2];
                vUv = share.x * eUv[0] + share.y * eUv[1] + share.z * eUv[2];
                gl_Position = uProjection * (uModelView * vec4(mix(level, rounded, ROUNDING), 1.0));
            }
            """;

    /**
     * A texel the texture leaves clear is not drawn at all: that is how the game hides the
     * body under what is worn over it. The colour is the texture's, turned round the colour
     * wheel by the part's hue, lit by the game's lights for this person and tinted.
     */
    private static final String FRAGMENT = """
            uniform sampler2D uTexture;
            uniform vec2 uTexSize;
            uniform float uAlpha;
            uniform float uHue;
            uniform vec3 uTint;
            uniform vec3 uAmbient;
            uniform vec3 uLightWay[5];
            uniform vec3 uLightColor[5];
            uniform mat4 uModelView;
            uniform float uBare;
            FROM_LAST vec3 vNormal;
            FROM_LAST vec2 vUv;

            """ + HUE + """
            void main() {
                // The game's skins are a few hundred dots across a whole person. Read through
                // a curve over the four nearest dots each way, and not a straight blend of
                // two, the edge of a sleeve or a wound is a clean line and not a blur; the
                // colour is then drawn a little away from the mean of what is round it.
                vec2 size = uTexSize;
                vec2 at = vUv * size - 0.5;
                vec2 whole = floor(at), part = at - whole;
                vec2 w0 = part * (-0.5 + part * (1.0 - 0.5 * part));
                vec2 w1 = 1.0 + part * part * (-2.5 + 1.5 * part);
                vec2 w2 = part * (0.5 + part * (2.0 - 1.5 * part));
                vec2 w3 = part * part * (-0.5 + 0.5 * part);
                vec2 w12 = w1 + w2;
                vec2 t0 = (whole - 0.5) / size, t3 = (whole + 2.5) / size, t12 = (whole + 0.5 + w2 / w12) / size;
                vec4 texel = TEXEL(uTexture, vec2(t12.x, t0.y)) * (w12.x * w0.y) + TEXEL(uTexture, vec2(t0.x, t12.y)) * (w0.x * w12.y)
                        + TEXEL(uTexture, t12) * (w12.x * w12.y) + TEXEL(uTexture, vec2(t3.x, t12.y)) * (w3.x * w12.y)
                        + TEXEL(uTexture, vec2(t12.x, t3.y)) * (w12.x * w3.y);
                texel /= w12.x * w0.y + w0.x * w12.y + w12.x * w12.y + w3.x * w12.y + w12.x * w3.y;
                vec4 plain = TEXEL(uTexture, vUv);
                // Where the skin is cut away the plain reading decides, so that nothing of
                // the body shows through what is worn over it that did not before.
                texel.a = plain.a;
                texel.rgb = clamp(texel.rgb + (texel.rgb - plain.rgb) * 0.6, 0.0, 1.0);
                float alpha = uAlpha * texel.a;
                if (texel.a < 0.01 || alpha <= 0.01) {
                    discard;
                }
                vec3 color = texel.rgb;
                if (uHue != 0.0) {
                    color = turned(color, uHue);
                }
                vec3 n = normalize(vNormal);
                vec3 light = uAmbient;
                for (int i = 0; i < 5; i++) {
                    light += uLightColor[i] * max(dot(n, uLightWay[i]), 0.0);
                }
                if (uBare > 0.5) {
                    // For the picture's own lighting: the colour as it is, and beside it the
                    // way the surface faces as the eye sees it.
                    gl_FragData[0] = vec4(uAlpha * color * uTint, alpha);
                    gl_FragData[1] = vec4(normalize(mat3(uModelView) * n) * 0.5 + 0.5, 1.0);
                } else {
                    gl_FragData[0] = vec4(uAlpha * color * uTint * min(light, vec3(1.0)), alpha);
                }
            }
            """;

    private static final String SLEEVE_FRAGMENT = """
            uniform sampler2D uTexture;
            uniform vec2 uForeUv[3];
            uniform vec2 uUpperUv[3];
            uniform vec2 uHas;
            uniform float uAlpha;
            uniform float uHue;
            uniform vec3 uTint;
            FROM_LAST vec3 vOut;
            FROM_LAST vec3 vWay;
            FROM_LAST vec3 vRound;
            """ + HUE + """
            // What is worn at three places on one length of the arm: the mean of those the
            // texture has anything at, or nothing where it is clear at all three.
            vec4 worn(vec2 a, vec2 b, vec2 c) {
                vec4 sum = vec4(0.0);
                vec4 texel = TEXEL(uTexture, a);
                sum += texel.a >= 0.5 ? vec4(texel.rgb, 1.0) : vec4(0.0);
                texel = TEXEL(uTexture, b);
                sum += texel.a >= 0.5 ? vec4(texel.rgb, 1.0) : vec4(0.0);
                texel = TEXEL(uTexture, c);
                sum += texel.a >= 0.5 ? vec4(texel.rgb, 1.0) : vec4(0.0);
                return sum.a > 0.0 ? vec4(sum.rgb / sum.a, 1.0) : vec4(0.0);
            }

            void main() {
                float along = vRound.z;
                vec4 fore = uHas.x > 0.5 ? worn(uForeUv[0], uForeUv[1], uForeUv[2]) : vec4(0.0);
                vec4 upper = uHas.y > 0.5 ? worn(uUpperUv[0], uUpperUv[1], uUpperUv[2]) : vec4(0.0);
                vec4 here = along < 1.0 ? fore : upper;
                // A part that has nothing on this length of the arm leaves it to the part under it.
                if (here.a < 0.5 || uAlpha <= 0.01) {
                    discard;
                }
                vec3 color = fore.a > 0.5 && upper.a > 0.5 ? mix(fore.rgb, upper.rgb, smoothstep(0.85, 1.15, along)) : here.rgb;
                if (uHue != 0.0) {
                    color = turned(color, uHue);
                }
                color *= uTint;
                // Skin, living or dead, is redder than it is blue and not strongly coloured.
                // A bare arm is smooth; a sleeve gathers in folds round it, most at the elbow.
                float most = max(color.r, max(color.g, color.b)), least = min(color.r, min(color.g, color.b));
                float flesh = step(color.b, color.g + 0.02) * step(color.g, color.r + 0.02) * smoothstep(0.25, 0.4, most)
                        * (1.0 - smoothstep(0.35, 0.6, (most - least) / max(most, 0.001)));
                float gathered = (0.3 + 0.7 * exp(-(along - 1.0) * (along - 1.0) * 9.0)) * (1.0 - flesh);
                float fold = along * 21.0 + vRound.x * 2.2 + 1.6 * sin(along * 7.0 + vRound.y * 2.0);
                vec3 n = normalize(normalize(vOut) + normalize(vWay) * (cos(fold) * gathered * 0.2));
                color *= 1.0 - 0.08 * (0.5 - 0.5 * sin(fold)) * gathered;
                // Unlit, with the way the surface faces as the eye sees it: the picture lights it.
                gl_FragData[0] = vec4(uAlpha * color, uAlpha);
                gl_FragData[1] = vec4(n * 0.5 + 0.5, 1.0);
            }
            """;

    private static final String CUT_FRAGMENT = """
            FROM_LAST float vArm;
            uniform float uKeepArms;
            """ + FRAGMENT.replace("void main() {", """
            void main() {
                if ((vArm > 0.5) != (uKeepArms > 0.5)) {
                    discard;
                }""");

    /** The bones of each arm, right then left, by the names the game's skeleton gives them: the hand's own first. */
    private static final String[][] HAND_BONES = {{"Bip01_R_Hand", "Bip01_R_Finger0", "Bip01_R_Finger1"}, {"Bip01_L_Hand", "Bip01_L_Finger0", "Bip01_L_Finger1"}};
    private static final String[] FOREARM_BONE = {"Bip01_R_Forearm", "Bip01_L_Forearm"}, UPPER_ARM_BONE = {"Bip01_R_UpperArm", "Bip01_L_UpperArm"};
    /** What a bone is to an arm. The left arm's are these and KINDS more. */
    static final int HAND = 1, FORE = 2, UPPER = 3, KINDS = 3;
    /** How many places on each length of an arm its sleeve's colour is read at. */
    static final int SAMPLES = 3;
    /** How many sides the picture's arm has, and how far along it each ring of them is. */
    private static final int SIDES = 12;
    private static final float[] RINGS = {-1f, -0.75f, -0.5f, -0.25f, 0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f, 1.125f, 1.25f,
        1.375f, 1.5f, 1.625f, 1.75f, 1.875f, 2f, 2.5f, 3f};

    /** What a mesh has of one arm, in the mesh's own places as it was made. */
    static final class Limb {
        /** The middles of the wrist and of the elbow, or null in a mesh that does not reach across one, and how far its skin stands off each. */
        float[] wrist, elbow;
        float wristWide, elbowWide;
        /** Texture places well inside the forearm's skin and the upper arm's, SAMPLES pairs each, or null in a mesh that covers none of it. */
        float[] foreUv, upperUv;
    }

    /** One program and where its settings live. */
    private static final class Skin {
        final int id;
        final int palette, modelView, projection, scale, uvScale, alpha, hue, tint, ambient, fine;
        final int[] lightWay = new int[LIGHTS], lightColor = new int[LIGHTS];

        Skin(int id) {
            this.id = id;
            palette = GL20.glGetUniformLocation(id, "uPalette");
            modelView = GL20.glGetUniformLocation(id, "uModelView");
            projection = GL20.glGetUniformLocation(id, "uProjection");
            scale = GL20.glGetUniformLocation(id, "uScale");
            uvScale = GL20.glGetUniformLocation(id, "uUvScale");
            alpha = GL20.glGetUniformLocation(id, "uAlpha");
            hue = GL20.glGetUniformLocation(id, "uHue");
            tint = GL20.glGetUniformLocation(id, "uTint");
            ambient = GL20.glGetUniformLocation(id, "uAmbient");
            fine = GL20.glGetUniformLocation(id, "uFine");
            for (int light = 0; light < LIGHTS; light++) {
                lightWay[light] = GL20.glGetUniformLocation(id, "uLightWay[" + light + "]");
                lightColor[light] = GL20.glGetUniformLocation(id, "uLightColor[" + light + "]");
            }
            GL20.glUseProgram(id);
            GL20.glUniform1i(GL20.glGetUniformLocation(id, "uTexture"), 0);
        }
    }

    /** The picture's copy of one of the game's meshes. */
    private static final class Copy {
        final VertexBufferObject from;
        final int modification;
        /** Zero for a mesh the picture cannot draw, which is left to the game. */
        final int vertices, indices, count;
        /** The right arm and the left, and the bone of each hand: null for a mesh that is not drawn. */
        final Limb[] limbs;
        final int[] handBone;

        Copy(VertexBufferObject from, int modification, int vertices, int indices, int count, Limb[] limbs, int[] handBone) {
            this.from = from;
            this.modification = modification;
            this.vertices = vertices;
            this.indices = indices;
            this.count = count;
            this.limbs = limbs;
            this.handBone = handBone;
        }
    }

    private record Spot(int x, int y, int z) {}

    private static boolean prepared;
    /** Set once something of the game's is not as this build knows it: every person is then the game's to draw. */
    private static boolean off;
    private static Skin whole, divided;
    /** The program that draws the viewer's body in its two pieces, or null if the driver refused it. */
    private static Skin cut;
    private static int cutArm, cutKeep;
    /** While set, the part is drawn by the cut program: which bones it is cut along, and which side of the cut is kept. */
    private static float[] cutBones;
    private static boolean cutKeepsThem;
    /**
     * For each skeleton, 1 at each bone of the right arm, the same with the left arm's too,
     * and then the same two for the hands alone.
     */
    private static final IdentityHashMap<SkinningData, float[][]> arms = new IdentityHashMap<>();
    /** The program that draws the picture's own arms, and their mesh. */
    private static int sleeve, sleeveVertices, sleeveIndices, sleeveCount;
    private static Field handleField, elementsField, formatField;
    private static final IdentityHashMap<ModelMesh, Copy> copies = new IdentityHashMap<>();

    private static ModelSlotRenderData person;
    private static float[] modelView, projection;
    private static boolean shadow;
    /** How far from the eye the person begun stands, in tiles. */
    private static float away;
    /**
     * Set while people are drawn into a target that has a second picture for the way their
     * surfaces face: they are then drawn unlit, for the picture to light.
     */
    static boolean bare;
    private static final int[] FIRST = {0x8CE0}, BOTH = {0x8CE0, 0x8CE1};
    private static final float[] lightWay = new float[LIGHTS * 3], lightColor = new float[LIGHTS * 3];
    private static Vector2 uvScale;

    /**
     * Before the parts of one person: the matrix that stands them before the eye, the eye's
     * projection, and whether this is a shadow map, which is drawn undivided since nothing of
     * a shadow's edge shows the difference.
     */
    static void begin(ModelSlotRenderData data, float[] standing, float[] seen, boolean casting) {
        person = data;
        modelView = standing;
        projection = seen;
        shadow = casting;
        away = (float) Math.sqrt(standing[12] * standing[12] + standing[13] * standing[13] + standing[14] * standing[14]);
        if (!off) {
            lights(data);
        }
    }

    /** Whether the viewer's body can be drawn in two pieces on this machine. */
    static boolean cuts() {
        if (!prepared) {
            prepared = true;
            prepare();
        }
        return !off && cut != null;
    }

    /**
     * Draws one piece of one part of the viewer begun: the hands on the weapon, or the body
     * without the arms those hands are on. False for a part that is the game's to draw, whole.
     *
     * @param bothArms whether the left hand is on the weapon too
     * @param held     whether the hands are the piece to draw
     */
    static boolean piece(ModelInstanceRenderData part, boolean bothArms, boolean held) {
        if (part.model == null || !(part.model.tag instanceof SkinningData skeleton)) {
            return false;
        }
        cutBones = armBones(skeleton)[(held ? 2 : 0) + (bothArms ? 1 : 0)];
        cutKeepsThem = held;
        boolean drawn = part(part);
        cutBones = null;
        return drawn;
    }

    /** Draws the left hand of one part of the viewer begun, and nothing else of it. */
    static boolean leftHand(ModelInstanceRenderData part) {
        if (part.model == null || !(part.model.tag instanceof SkinningData skeleton)) {
            return false;
        }
        cutBones = armBones(skeleton)[4];
        cutKeepsThem = true;
        boolean drawn = part(part);
        cutBones = null;
        return drawn;
    }

    private static float[][] armBones(SkinningData skeleton) {
        float[][] found = arms.get(skeleton);
        if (found == null) {
            found = new float[5][BONES];
            int[] kinds = kinds(skeleton);
            for (int bone = 0; bone < BONES; bone++) {
                if (kinds[bone] != 0) {
                    boolean right = kinds[bone] <= KINDS, hand = (kinds[bone] - 1) % KINDS + 1 == HAND;
                    found[1][bone] = 1f;
                    found[0][bone] = right ? 1f : 0f;
                    found[3][bone] = hand ? 1f : 0f;
                    found[2][bone] = hand && right ? 1f : 0f;
                    found[4][bone] = hand && !right ? 1f : 0f;
                }
            }
            arms.put(skeleton, found);
        }
        return found;
    }

    /** What each bone of a skeleton is to an arm: nought for a bone of neither. */
    private static int[] kinds(SkinningData skeleton) {
        int[] kinds = new int[BONES];
        for (int side = 0; side < 2; side++) {
            for (String name : HAND_BONES[side]) {
                mark(skeleton, name, kinds, side * KINDS + HAND);
            }
            mark(skeleton, FOREARM_BONE[side], kinds, side * KINDS + FORE);
            mark(skeleton, UPPER_ARM_BONE[side], kinds, side * KINDS + UPPER);
        }
        return kinds;
    }

    private static void mark(SkinningData skeleton, String name, int[] kinds, int kind) {
        Integer bone = skeleton.boneIndices.get(name);
        if (bone != null && bone >= 0 && bone < BONES) {
            kinds[bone] = kind;
        }
    }

    /**
     * Where a hand joins its arm, from a part of a person whose mesh has the hand and
     * the forearm both: the middle of the wrist, and where the elbow would be were the forearm
     * rigid with the hand, in the axes the person is stood from, at the scale given; then how far the
     * skin stands off the wrist and off the elbow. False for a part with no such hand.
     */
    static boolean hand(ModelInstanceRenderData part, boolean left, float finalScale, float[] out) {
        Model model = part.model;
        if (off || model == null || model.isStatic || model.mesh == null || part.matrixPalette == null) {
            return false;
        }
        Copy copy = copy(model);
        int side = left ? 1 : 0;
        if (copy == null || copy.count == 0 || copy.handBone[side] < 0) {
            return false;
        }
        Limb limb = copy.limbs[side];
        if (limb.wrist == null || limb.elbow == null) {
            return false;
        }
        // The hand's bone carries both points: its matrix is kept by rows.
        int at = copy.handBone[side] * 16;
        FloatBuffer palette = part.matrixPalette;
        for (int point = 0; point < 2; point++) {
            float[] from = point == 0 ? limb.wrist : limb.elbow;
            for (int axis = 0; axis < 3; axis++) {
                int row = at + axis * 4;
                out[point * 3 + axis] = (palette.get(row) * from[0] + palette.get(row + 1) * from[1] + palette.get(row + 2) * from[2] + palette.get(row + 3))
                        * finalScale;
            }
        }
        out[6] = limb.wristWide * finalScale;
        out[7] = limb.elbowWide * finalScale;
        return true;
    }

    /**
     * Draws what one part of the viewer begun wears on one arm, as the picture's own arm:
     * nothing for a part that has none of that arm. The parts are to be given in the order the
     * game draws them, so that what is worn over the skin is drawn over it here.
     *
     * @param joints the wrist, the elbow and the shoulder as the eye sees them
     * @param bend   the line square to both bones, of length one
     * @param wide   how far the skin stands off each joint
     */
    static void sleeve(ModelInstanceRenderData part, boolean left, float[] joints, float[] bend, float[] wide) {
        Model model = part.model;
        if (off || model == null || model.isStatic || model.mesh == null) {
            return;
        }
        Shader effect = model.effect;
        if (effect != null && !GAME_PROGRAM.equals(effect.getName())) {
            return;
        }
        Copy copy = copy(model);
        if (copy == null || copy.count == 0 || person.alpha < FAINTEST) {
            return;
        }
        Limb limb = copy.limbs[left ? 1 : 0];
        if (limb.foreUv == null && limb.upperUv == null) {
            return;
        }
        ModelInstance instance = part.modelInstance;
        Texture texture = instance.tex != null ? instance.tex : model.tex;
        if (texture == null) {
            return;
        }
        // As for a part: the finer skin is asked for before any state is set.
        Texture worn = FineSkins.of(texture);

        // The arm is seen from outside only, and each part's is the same mesh at the same
        // depth: one drawn later has to be let through where it is as near.
        GL11.glDisable(GL_CULL_FACE);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_BLEND);
        GL20.glUseProgram(sleeve);
        for (int joint = 0; joint < 3; joint++) {
            GL20.glUniform3f(GL20.glGetUniformLocation(sleeve, "uJoint[" + joint + "]"), joints[joint * 3], joints[joint * 3 + 1], joints[joint * 3 + 2]);
        }
        GL20.glUniform3f(GL20.glGetUniformLocation(sleeve, "uBend"), bend[0], bend[1], bend[2]);
        GL20.glUniform3f(GL20.glGetUniformLocation(sleeve, "uWide"), wide[0], wide[1], wide[2]);
        GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(sleeve, "uProjection"), false, projection);
        GL20.glUniform1f(GL20.glGetUniformLocation(sleeve, "uAlpha"), person.alpha);
        GL20.glUniform1f(GL20.glGetUniformLocation(sleeve, "uHue"), part.hue);
        GL20.glUniform3f(GL20.glGetUniformLocation(sleeve, "uTint"), instance.tintR, instance.tintG, instance.tintB);
        GL13.glActiveTexture(GL_TEXTURE0);
        Texture.lastTextureID = -1;
        worn.bind();
        worn.getUVScale(uvScale);
        GL20.glUniform2f(GL20.glGetUniformLocation(sleeve, "uHas"), limb.foreUv != null ? 1f : 0f, limb.upperUv != null ? 1f : 0f);
        for (int sample = 0; sample < SAMPLES; sample++) {
            if (limb.foreUv != null) {
                GL20.glUniform2f(GL20.glGetUniformLocation(sleeve, "uForeUv[" + sample + "]"), limb.foreUv[sample * 2] * uvScale.x, limb.foreUv[sample * 2 + 1] * uvScale.y);
            }
            if (limb.upperUv != null) {
                GL20.glUniform2f(GL20.glGetUniformLocation(sleeve, "uUpperUv[" + sample + "]"), limb.upperUv[sample * 2] * uvScale.x, limb.upperUv[sample * 2 + 1] * uvScale.y);
            }
        }
        if (bare) {
            GL20.glDrawBuffers(BOTH);
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, sleeveVertices);
        GL20.glVertexAttribPointer(PLACE, 3, GL_FLOAT, false, 12, 0L);
        GL20.glEnableVertexAttribArray(PLACE);
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sleeveIndices);
        GL11.glDrawElements(GL_TRIANGLES, sleeveCount, GL_UNSIGNED_INT, 0L);
        if (bare) {
            GL20.glDrawBuffers(FIRST);
        }
        GL20.glDisableVertexAttribArray(PLACE);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /** The picture's arm before it is bent to its joints: rings of corners, each saying where it is round the arm and how far along. */
    private static void makeSleeve() {
        FloatBuffer corners = ByteBuffer.allocateDirect(RINGS.length * SIDES * 12).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (float ring : RINGS) {
            for (int side = 0; side < SIDES; side++) {
                double round = 2 * Math.PI * side / SIDES;
                corners.put((float) Math.cos(round)).put((float) Math.sin(round)).put(ring);
            }
        }
        corners.flip();
        int[] triangles = new int[(RINGS.length - 1) * SIDES * 6];
        int at = 0;
        for (int ring = 0; ring + 1 < RINGS.length; ring++) {
            for (int side = 0; side < SIDES; side++) {
                int here = ring * SIDES + side, next = ring * SIDES + (side + 1) % SIDES;
                triangles[at++] = here;
                triangles[at++] = next;
                triangles[at++] = here + SIDES;
                triangles[at++] = next;
                triangles[at++] = next + SIDES;
                triangles[at++] = here + SIDES;
            }
        }
        sleeveVertices = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ARRAY_BUFFER, sleeveVertices);
        GL15.glBufferData(GL_ARRAY_BUFFER, corners, GL_STATIC_DRAW);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        sleeveIndices = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sleeveIndices);
        GL15.glBufferData(GL_ELEMENT_ARRAY_BUFFER, triangles, GL_STATIC_DRAW);
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        sleeveCount = triangles.length;
    }

    /** Draws one part of the person begun. False for a part that is the game's to draw. */
    static boolean part(ModelInstanceRenderData part) {
        if (off) {
            return false;
        }
        Model model = part.model;
        if (model == null || model.isStatic || model.mesh == null || part.matrixPalette == null) {
            return false;
        }
        Shader effect = model.effect;
        if (effect != null && !GAME_PROGRAM.equals(effect.getName())) {
            return false;
        }
        if (!prepared) {
            prepared = true;
            prepare();
            if (off) {
                return false;
            }
        }
        Copy copy = copy(model);
        if (copy == null || copy.count == 0) {
            return false;
        }
        // What the game's draw leaves out is left out here.
        if (person.alpha < FAINTEST || person.animPlayer == null) {
            return true;
        }
        ModelInstance instance = part.modelInstance;
        Texture texture = instance.tex != null ? instance.tex : model.tex;
        if (texture == null) {
            return true;
        }
        // Making a finer skin runs the game's combiner, so it is asked for before any of
        // this part's state is set. Further off than triangles are divided a person is too
        // small on the screen for a finer skin to show, and a shadow has no skin at all.
        Texture worn = shadow || away > WHOLE_BEYOND ? texture : FineSkins.of(texture);

        ModelScript script = instance.modelScript;
        if (script != null && script.cullFace == 0) {
            GL11.glDisable(GL_CULL_FACE);
        } else {
            GL11.glEnable(GL_CULL_FACE);
            // The matrix that stands a model in the world mirrors it, which turns every
            // triangle's front to its back.
            GL11.glCullFace(script != null && script.cullFace != -1 ? script.cullFace : GL_FRONT);
        }
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LESS);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_BLEND);

        Skin skin = cutBones != null ? cut : divided != null && !shadow ? divided : whole;
        GL20.glUseProgram(skin.id);
        if (cutBones != null) {
            GL20.glUniform1fv(cutArm, cutBones);
            GL20.glUniform1f(cutKeep, cutKeepsThem ? 1f : 0f);
        }
        // The game keeps each bone's matrix with its rows where OpenGL wants columns.
        GL20.glUniformMatrix4fv(skin.palette, true, part.matrixPalette);
        GL20.glUniformMatrix4fv(skin.modelView, false, modelView);
        GL20.glUniformMatrix4fv(skin.projection, false, projection);
        GL20.glUniform1f(skin.scale, person.finalScale);
        GL20.glUniform1f(GL20.glGetUniformLocation(skin.id, "uBare"), bare && !shadow ? 1f : 0f);
        if (bare && !shadow) {
            GL20.glDrawBuffers(BOTH);
        }
        GL20.glUniform1f(skin.alpha, person.alpha);
        GL20.glUniform1f(skin.hue, part.hue);
        GL20.glUniform3f(skin.tint, instance.tintR, instance.tintG, instance.tintB);
        if (part.ignoreLighting) {
            GL20.glUniform3f(skin.ambient, 1f, 1f, 1f);
        } else {
            GL20.glUniform3f(skin.ambient, person.ambientR * AMBIENT_SHARE, person.ambientG * AMBIENT_SHARE, person.ambientB * AMBIENT_SHARE);
        }
        for (int light = 0; light < LIGHTS; light++) {
            int at = light * 3;
            GL20.glUniform3f(skin.lightWay[light], lightWay[at], lightWay[at + 1], lightWay[at + 2]);
            GL20.glUniform3f(skin.lightColor[light], lightColor[at], lightColor[at + 1], lightColor[at + 2]);
        }
        // The game remembers which texture it bound last and binds none twice running. The
        // picture binds textures of its own between one pass over the people and the next, so
        // what the game remembers may no longer be what is bound: it is made to forget, and
        // the bind then goes through it, so what it remembers afterwards is true again for the
        // parts it draws itself.
        GL13.glActiveTexture(GL_TEXTURE0);
        Texture.lastTextureID = -1;
        worn.bind();
        worn.getUVScale(uvScale);
        GL20.glUniform2f(skin.uvScale, uvScale.x, uvScale.y);
        GL20.glUniform2f(GL20.glGetUniformLocation(skin.id, "uTexSize"), worn.getWidth() / uvScale.x, worn.getHeight() / uvScale.y);

        GL15.glBindBuffer(GL_ARRAY_BUFFER, copy.vertices);
        for (int attribute = 0; attribute < ATTRIBUTES; attribute++) {
            GL20.glVertexAttribPointer(attribute, SIZE[attribute], GL_FLOAT, false, STRIDE, OFFSET[attribute]);
            GL20.glEnableVertexAttribArray(attribute);
        }
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, copy.indices);
        if (skin == divided) {
            GL20.glUniform2f(skin.fine, FINE_WITHIN, WHOLE_BEYOND);
            GL40.glPatchParameteri(GL_PATCH_VERTICES, 3);
            GL11.glDrawElements(GL_PATCHES, copy.count, GL_UNSIGNED_INT, 0L);
        } else {
            GL11.glDrawElements(GL_TRIANGLES, copy.count, GL_UNSIGNED_INT, 0L);
        }
        if (bare && !shadow) {
            GL20.glDrawBuffers(FIRST);
        }
        // The game's draw of the next part turns on only the arrays its own mesh has. One left
        // on from here would be read past the end of this mesh's buffer.
        for (int attribute = 0; attribute < ATTRIBUTES; attribute++) {
            GL20.glDisableVertexAttribArray(attribute);
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        return true;
    }

    private static void refuse(String why) {
        off = true;
        Log.info("characters are drawn by the game's own draw, as flat-sided as it makes them: " + why);
    }

    private static void prepare() {
        try {
            handleField = field("handle");
            elementsField = field("elements");
            formatField = field("vertexFormat");
        } catch (NoSuchFieldException e) {
            refuse("the game's vertex buffer has no field " + e.getMessage());
            return;
        }
        String version = GL11.glGetString(GL_VERSION);
        Matcher numbers = Pattern.compile("^(\\d+)\\.(\\d+)").matcher(version == null ? "" : version);
        if (!numbers.find()) {
            refuse("the graphics driver gives its OpenGL version as " + version);
            return;
        }
        StringBuilder problem = new StringBuilder();
        int plain = program("character", problem, GL_VERTEX_SHADER, OLD + WHOLE_VERTEX, GL_FRAGMENT_SHADER, OLD + FRAGMENT);
        boolean divides = Integer.parseInt(numbers.group(1)) >= 4;
        int bowed = divides ? program("rounded character", problem, GL_VERTEX_SHADER, NEW + DIVIDED_VERTEX, GL_TESS_CONTROL_SHADER, DIVIDE,
                GL_TESS_EVALUATION_SHADER, BOW, GL_FRAGMENT_SHADER, NEW + FRAGMENT) : 0;
        if (problem.length() > 0) {
            refuse(problem.toString());
            return;
        }
        uvScale = new Vector2();
        whole = new Skin(plain);
        // Apart from the others: without it people are drawn as before, and the viewer's own
        // body and weapon stay where the game holds them.
        StringBuilder cutProblem = new StringBuilder();
        int pieces = program("held weapon", cutProblem, GL_VERTEX_SHADER, OLD + CUT_VERTEX, GL_FRAGMENT_SHADER, OLD + CUT_FRAGMENT);
        int arm = cutProblem.length() > 0 ? 0 : program("held arm", cutProblem, GL_VERTEX_SHADER, OLD + SLEEVE_VERTEX, GL_FRAGMENT_SHADER, OLD + SLEEVE_FRAGMENT);
        if (cutProblem.length() > 0) {
            Log.info("the weapon in hand is drawn where the game holds it: " + cutProblem);
        } else {
            cut = new Skin(pieces);
            cutArm = GL20.glGetUniformLocation(pieces, "uArm");
            cutKeep = GL20.glGetUniformLocation(pieces, "uKeepArms");
            sleeve = arm;
            GL20.glUseProgram(arm);
            GL20.glUniform1i(GL20.glGetUniformLocation(arm, "uTexture"), 0);
            makeSleeve();
        }
        if (divides) {
            divided = new Skin(bowed);
            Log.info("characters are drawn by the picture, rounded near the eye (OpenGL " + version + ")");
        } else {
            Log.info("characters are drawn by the picture, with the game's triangles as they are: rounding them needs OpenGL 4.0 and this is " + version);
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = VertexBufferObject.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** A linked program from kinds and sources in turn, or zero with what the driver refused added to problem. */
    private static int program(String name, StringBuilder problem, Object... stages) {
        int id = GL20.glCreateProgram();
        for (int stage = 0; stage < stages.length; stage += 2) {
            int shader = GL20.glCreateShader((Integer) stages[stage]);
            GL20.glShaderSource(shader, (String) stages[stage + 1]);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
                problem.append("stage ").append(stage / 2 + 1).append(" of the ").append(name).append(" shader did not compile: ")
                        .append(GL20.glGetShaderInfoLog(shader)).append(' ');
                return 0;
            }
            GL20.glAttachShader(id, shader);
        }
        for (int attribute = 0; attribute < ATTRIBUTES; attribute++) {
            GL20.glBindAttribLocation(id, attribute, ATTRIBUTE_NAMES[attribute]);
        }
        GL20.glLinkProgram(id);
        if (GL20.glGetProgrami(id, GL_LINK_STATUS) == 0) {
            problem.append("the ").append(name).append(" shader did not link: ").append(GL20.glGetProgramInfoLog(id)).append(' ');
            return 0;
        }
        return id;
    }

    /** The picture's copy of a model's mesh, made the first time it is drawn and again when the game loads the mesh anew. Null while the game has not loaded it. */
    private static Copy copy(Model model) {
        ModelMesh mesh = model.mesh;
        VertexBufferObject buffer = mesh.vb;
        Copy copy = copies.get(mesh);
        if (copy != null && copy.from == buffer && copy.modification == mesh.modificationCount) {
            return copy;
        }
        if (buffer == null) {
            return null;
        }
        VertexBufferObject.Vbo handle;
        int[] elements;
        VertexBufferObject.VertexFormat format;
        try {
            handle = (VertexBufferObject.Vbo) handleField.get(buffer);
            elements = (int[]) elementsField.get(buffer);
            format = (VertexBufferObject.VertexFormat) formatField.get(buffer);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("the game's vertex buffer cannot be read: " + e, e);
        }
        if (handle == null) {
            return null;
        }
        if (copy != null && copy.count != 0) {
            GL15.glDeleteBuffers(copy.vertices);
            GL15.glDeleteBuffers(copy.indices);
        }
        copy = read(buffer, mesh.modificationCount, handle, elements, format, model.name, model.tag instanceof SkinningData skeleton ? skeleton : null);
        copies.put(mesh, copy);
        return copy;
    }

    private static Copy read(VertexBufferObject buffer, int modification, VertexBufferObject.Vbo handle, int[] elements,
            VertexBufferObject.VertexFormat format, String name, SkinningData skeleton) {
        int[] from = {-1, -1, -1, -1, -1};
        for (int element = 0; format != null && element < format.getNumElements(); element++) {
            VertexBufferObject.VertexElement at = format.getElement(element);
            int which = switch (at.type) {
                case VertexArray -> PLACE;
                case NormalArray -> NORMAL;
                case BlendWeightArray -> WEIGHTS;
                case BlendIndexArray -> BONE_NUMBERS;
                case TextureCoordArray -> UV;
                default -> -1;
            };
            // A mesh with more than one set of texture places is drawn with its first.
            if (which >= 0 && from[which] < 0) {
                from[which] = at.byteOffset;
            }
        }
        int count = handle.numElements, stride = handle.vertexStride;
        String lacks = handle.faceDataOnly ? "its buffer holds no vertices"
                : from[PLACE] < 0 ? "it has no places" : from[NORMAL] < 0 ? "it has no normals" : from[WEIGHTS] < 0 ? "it has no bone weights"
                : from[BONE_NUMBERS] < 0 ? "it has no bone numbers" : from[UV] < 0 ? "it has no texture places"
                : elements == null || elements.length < count || count % 3 != 0 ? "its triangles are not kept as a list of " + count + " corners" : null;
        if (lacks != null) {
            Log.info("the model " + name + " is left to the game's own draw: " + lacks);
            return new Copy(buffer, modification, 0, 0, 0, null, null);
        }

        GL15.glBindBuffer(GL_ARRAY_BUFFER, handle.vboId);
        int bytes = GL15.glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE);
        ByteBuffer raw = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        GL15.glGetBufferSubData(GL_ARRAY_BUFFER, 0L, raw);
        int vertices = bytes / stride;
        for (int corner = 0; corner < count; corner++) {
            if (elements[corner] < 0 || elements[corner] >= vertices) {
                GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
                Log.info("the model " + name + " is left to the game's own draw: a triangle names vertex " + elements[corner] + " of " + vertices);
                return new Copy(buffer, modification, 0, 0, 0, null, null);
            }
        }

        float[] places = new float[vertices * 3], normals = new float[vertices * 3];
        for (int vertex = 0; vertex < vertices; vertex++) {
            for (int axis = 0; axis < 3; axis++) {
                places[vertex * 3 + axis] = raw.getFloat(vertex * stride + from[PLACE] + axis * 4);
                normals[vertex * 3 + axis] = raw.getFloat(vertex * stride + from[NORMAL] + axis * 4);
            }
        }
        float[] smooth = smoothNormals(places, normals, elements, count);
        Limb[] limbs = {new Limb(), new Limb()};
        int[] handBone = {-1, -1};
        if (skeleton != null) {
            int[] boneKinds = kinds(skeleton);
            int[] kinds = new int[vertices];
            float[] uvs = new float[vertices * 2];
            for (int vertex = 0; vertex < vertices; vertex++) {
                float most = 0f;
                for (int share = 0; share < 4; share++) {
                    float weight = raw.getFloat(vertex * stride + from[WEIGHTS] + share * 4);
                    int bone = (int) raw.getFloat(vertex * stride + from[BONE_NUMBERS] + share * 4);
                    if (weight > most && bone >= 0 && bone < BONES) {
                        most = weight;
                        kinds[vertex] = boneKinds[bone];
                    }
                }
                uvs[vertex * 2] = raw.getFloat(vertex * stride + from[UV]);
                uvs[vertex * 2 + 1] = raw.getFloat(vertex * stride + from[UV] + 4);
            }
            for (int side = 0; side < 2; side++) {
                limbs[side] = limb(places, uvs, kinds, elements, count, side);
                Integer bone = skeleton.boneIndices.get(HAND_BONES[side][0]);
                handBone[side] = bone != null && bone >= 0 && bone < BONES ? bone : -1;
            }
        }
        FloatBuffer made = ByteBuffer.allocateDirect(vertices * STRIDE).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int vertex = 0; vertex < vertices; vertex++) {
            for (int attribute = 0; attribute < SMOOTH; attribute++) {
                for (int part = 0; part < SIZE[attribute]; part++) {
                    made.put(raw.getFloat(vertex * stride + from[attribute] + part * 4));
                }
            }
            made.put(smooth, vertex * 3, 3);
        }
        made.flip();
        int ours = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ARRAY_BUFFER, ours);
        GL15.glBufferData(GL_ARRAY_BUFFER, made, GL_STATIC_DRAW);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        int indices = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indices);
        GL15.glBufferData(GL_ELEMENT_ARRAY_BUFFER, elements.length == count ? elements : java.util.Arrays.copyOf(elements, count), GL_STATIC_DRAW);
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        return new Copy(buffer, modification, ours, indices, count, limbs, handBone);
    }

    /**
     * What a mesh has of one arm. Each vertex is taken to belong to the bone that has most of
     * it. The wrist is where triangles reach from the hand's vertices to the forearm's, and
     * the elbow where they reach from the forearm's to the upper arm's: the middle of each is
     * the mean of those triangles' corners. The colour of what is worn on a length of the arm
     * is read at the middles of the triangles nearest half way along it, which are inside the
     * texture's piece for that length and not on its edge.
     *
     * @param kinds what each vertex's bone is to an arm
     * @param side  0 for the right arm, 1 for the left
     */
    static Limb limb(float[] places, float[] uvs, int[] kinds, int[] elements, int count, int side) {
        int hand = side * KINDS + HAND, fore = side * KINDS + FORE, upper = side * KINDS + UPPER;
        Limb limb = new Limb();
        boolean[] atWrist = new boolean[kinds.length], atElbow = new boolean[kinds.length];
        // How many of each triangle's corners are the forearm's, and the upper arm's.
        int[] ofFore = new int[count / 3], ofUpper = new int[count / 3];
        for (int corner = 0; corner + 2 < count; corner += 3) {
            int hands = 0, fores = 0, uppers = 0;
            for (int of = 0; of < 3; of++) {
                int kind = kinds[elements[corner + of]];
                hands += kind == hand ? 1 : 0;
                fores += kind == fore ? 1 : 0;
                uppers += kind == upper ? 1 : 0;
            }
            for (int of = 0; of < 3; of++) {
                atWrist[elements[corner + of]] |= hands > 0 && fores > 0;
                atElbow[elements[corner + of]] |= fores > 0 && uppers > 0;
            }
            ofFore[corner / 3] = fores;
            ofUpper[corner / 3] = uppers;
        }
        float[] wrist = middle(places, atWrist), elbow = middle(places, atElbow);
        if (wrist != null) {
            limb.wrist = new float[] {wrist[0], wrist[1], wrist[2]};
            limb.wristWide = wrist[3];
        }
        if (elbow != null) {
            limb.elbow = new float[] {elbow[0], elbow[1], elbow[2]};
            limb.elbowWide = elbow[3];
        }
        limb.foreUv = worn(places, uvs, elements, ofFore, wrist != null && elbow != null
                ? new float[] {(wrist[0] + elbow[0]) / 2f, (wrist[1] + elbow[1]) / 2f, (wrist[2] + elbow[2]) / 2f} : null);
        limb.upperUv = worn(places, uvs, elements, ofUpper, null);
        return limb;
    }

    /** The mean of the marked vertices and their mean distance from it, or null with none marked. */
    private static float[] middle(float[] places, boolean[] marked) {
        float[] middle = new float[4];
        int of = 0;
        for (int vertex = 0; vertex < marked.length; vertex++) {
            if (marked[vertex]) {
                middle[0] += places[vertex * 3];
                middle[1] += places[vertex * 3 + 1];
                middle[2] += places[vertex * 3 + 2];
                of++;
            }
        }
        if (of == 0) {
            return null;
        }
        middle[0] /= of;
        middle[1] /= of;
        middle[2] /= of;
        for (int vertex = 0; vertex < marked.length; vertex++) {
            if (marked[vertex]) {
                float x = places[vertex * 3] - middle[0], y = places[vertex * 3 + 1] - middle[1], z = places[vertex * 3 + 2] - middle[2];
                middle[3] += (float) Math.sqrt(x * x + y * y + z * z) / of;
            }
        }
        return middle;
    }

    /**
     * The texture places at the middles of the SAMPLES triangles nearest a point, among those
     * with the most corners on one length of an arm: all three where the mesh has such
     * triangles, which a mesh of few faces may not. Null if no triangle has a corner there.
     *
     * @param corners how many of each triangle's corners are on that length
     * @param near    the point, or null for the middle of all such triangles
     */
    private static float[] worn(float[] places, float[] uvs, int[] elements, int[] corners, float[] near) {
        int most = 0;
        for (int has : corners) {
            most = Math.max(most, has);
        }
        if (most == 0) {
            return null;
        }
        float[] middles = new float[corners.length * 3];
        float[] mean = new float[3];
        int of = 0;
        for (int triangle = 0; triangle < corners.length; triangle++) {
            if (corners[triangle] != most) {
                continue;
            }
            for (int axis = 0; axis < 3; axis++) {
                middles[triangle * 3 + axis] = (places[elements[triangle * 3] * 3 + axis] + places[elements[triangle * 3 + 1] * 3 + axis]
                        + places[elements[triangle * 3 + 2] * 3 + axis]) / 3f;
                mean[axis] += middles[triangle * 3 + axis];
            }
            of++;
        }
        float[] to = near != null ? near : new float[] {mean[0] / of, mean[1] / of, mean[2] / of};
        float[] found = new float[SAMPLES * 2];
        boolean[] taken = new boolean[corners.length];
        int last = -1;
        for (int sample = 0; sample < SAMPLES; sample++) {
            int nearest = -1;
            float least = Float.MAX_VALUE;
            for (int triangle = 0; triangle < corners.length; triangle++) {
                if (corners[triangle] != most || taken[triangle]) {
                    continue;
                }
                float x = middles[triangle * 3] - to[0], y = middles[triangle * 3 + 1] - to[1], z = middles[triangle * 3 + 2] - to[2];
                float away = x * x + y * y + z * z;
                if (away < least) {
                    least = away;
                    nearest = triangle;
                }
            }
            // A length of fewer triangles than samples is read at its last one again.
            if (nearest < 0) {
                nearest = last;
            }
            taken[nearest] = true;
            last = nearest;
            for (int axis = 0; axis < 2; axis++) {
                found[sample * 2 + axis] = (uvs[elements[nearest * 3] * 2 + axis] + uvs[elements[nearest * 3 + 1] * 2 + axis]
                        + uvs[elements[nearest * 3 + 2] * 2 + axis]) / 3f;
            }
        }
        return found;
    }

    /**
     * Which way the surface faces at each vertex once the faces round it are run together. A
     * mesh holds a corner several times over where its texture has a seam there, each copy
     * with its own normal; here every copy at one place gets the one answer, the faces round
     * that place added up by their area. A vertex no triangle uses keeps its own normal.
     */
    static float[] smoothNormals(float[] places, float[] normals, int[] elements, int count) {
        int vertices = places.length / 3;
        HashMap<Spot, Integer> firstAt = new HashMap<>();
        int[] one = new int[vertices];
        for (int vertex = 0; vertex < vertices; vertex++) {
            // Adding zero makes the two zeros one.
            Spot spot = new Spot(Float.floatToIntBits(places[vertex * 3] + 0f), Float.floatToIntBits(places[vertex * 3 + 1] + 0f),
                    Float.floatToIntBits(places[vertex * 3 + 2] + 0f));
            Integer first = firstAt.putIfAbsent(spot, vertex);
            one[vertex] = first == null ? vertex : first;
        }
        float[] sum = new float[vertices * 3];
        for (int corner = 0; corner + 2 < count; corner += 3) {
            int a = elements[corner] * 3, b = elements[corner + 1] * 3, c = elements[corner + 2] * 3;
            float ux = places[b] - places[a], uy = places[b + 1] - places[a + 1], uz = places[b + 2] - places[a + 2];
            float vx = places[c] - places[a], vy = places[c + 1] - places[a + 1], vz = places[c + 2] - places[a + 2];
            // As long as twice the triangle's area, which is what weighs it.
            float x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
            // Which way round a mesh lists its corners is not known, and its own normals say
            // which side is out.
            float out = x * (normals[a] + normals[b] + normals[c]) + y * (normals[a + 1] + normals[b + 1] + normals[c + 1])
                    + z * (normals[a + 2] + normals[b + 2] + normals[c + 2]);
            float sign = out < 0f ? -1f : 1f;
            for (int of = 0; of < 3; of++) {
                int to = one[elements[corner + of]] * 3;
                sum[to] += x * sign;
                sum[to + 1] += y * sign;
                sum[to + 2] += z * sign;
            }
        }
        float[] smooth = new float[vertices * 3];
        for (int vertex = 0; vertex < vertices; vertex++) {
            int at = vertex * 3, of = one[vertex] * 3;
            float length = (float) Math.sqrt(sum[of] * sum[of] + sum[of + 1] * sum[of + 1] + sum[of + 2] * sum[of + 2]);
            if (length > 0f) {
                smooth[at] = sum[of] / length;
                smooth[at + 1] = sum[of + 1] / length;
                smooth[at + 2] = sum[of + 2] / length;
            } else {
                smooth[at] = normals[at];
                smooth[at + 1] = normals[at + 1];
                smooth[at + 2] = normals[at + 2];
            }
        }
        return smooth;
    }

    /**
     * The game's lights for one person as its own draw gives them to its program: each
     * light's direction from where the person stands, turned into the model's own axes, and
     * its colour faded by how far off it is against how far it reaches.
     */
    private static void lights(ModelSlotRenderData data) {
        float angle = data.animPlayerAngle;
        boolean stands = !Float.isNaN(angle);
        for (int light = 0; light < LIGHTS; light++) {
            ModelInstance.EffectLight lamp = data.effectLights[light];
            int at = light * 3;
            if (lamp.r + lamp.g + lamp.b == 0f || lamp.radius <= 0) {
                lightWay[at] = 0f;
                lightWay[at + 1] = 1f;
                lightWay[at + 2] = 0f;
                lightColor[at] = 0f;
                lightColor[at + 1] = 0f;
                lightColor[at + 2] = 0f;
                continue;
            }
            float x = lamp.x, y = lamp.y, z = lamp.z;
            if (stands) {
                x -= data.x;
                y -= data.y;
                z -= data.z;
            }
            float away = (float) Math.sqrt(x * x + y * y + z * z);
            if (away < 1e-4f) {
                x = 0f;
                y = 0f;
                z = 1f;
            } else {
                x /= away;
                y /= away;
                z /= away;
            }
            if (stands) {
                float cos = (float) Math.cos(-angle), sin = (float) Math.sin(-angle);
                float turnedX = x * cos - y * sin, turnedY = x * sin + y * cos;
                x = turnedX;
                y = turnedY;
            }
            float reach = Math.clamp(1f - away / lamp.radius, 0f, 1f);
            // The world's up is the model's y, and the model is mirrored across x.
            lightWay[at] = -x;
            lightWay[at + 1] = z;
            lightWay[at + 2] = y;
            lightColor[at] = Math.clamp(lamp.r * reach, 0f, 1f);
            lightColor[at + 1] = Math.clamp(lamp.g * reach, 0f, 1f);
            lightColor[at + 2] = Math.clamp(lamp.b * reach, 0f, 1f);
        }
    }
}

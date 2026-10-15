package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelMesh;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.core.skinnedmodel.model.SkinningBone;
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
 * The bones, the textures and the colours are the game's, read from what it posed this frame.
 * While bare is set what is written is the unlit colour and what KEPT describes, for the
 * picture to light; otherwise it is what the game's draw writes, the same colour and the same
 * alpha.
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
    static final float FINE_WITHIN = 3f, WHOLE_BEYOND = 7f;
    /**
     * A person whose feet are this much further off than WHOLE_BEYOND has no edge within it, and is
     * drawn by the undivided program: divided into itself a triangle is the same triangle.
     */
    private static final float PERSON_REACHES = 2.5f;
    /** How far off a head of hair its outermost fringe stands, in the model's own measure, and how many layers of fringe there are near the eye and further off. */
    private static final float FRINGE = 0.0045f, FRINGE_NEAR = 2.5f, FRINGE_FAR = 5f;
    private static final int FRINGE_LAYERS_NEAR = 4, FRINGE_LAYERS_FAR = 2;

    /**
     * Place, normal, bone weights, bone numbers, texture place, run-together normal, and the
     * way the texture runs down the surface with how near the corner is to the mesh's open edge.
     */
    private static final int FLOATS = 23, STRIDE = FLOATS * 4;
    private static final int PLACE = 0, NORMAL = 1, WEIGHTS = 2, BONE_NUMBERS = 3, UV = 4, SMOOTH = 5, ALONG = 6, ATTRIBUTES = 7;
    private static final int[] SIZE = {3, 3, 4, 4, 2, 3, 4};
    private static final int[] OFFSET = {0, 12, 24, 40, 56, 64, 76};
    private static final String[] ATTRIBUTE_NAMES = {"aPlace", "aNormal", "aWeights", "aBones", "aUv", "aSmooth", "aAlong"};

    private static final int GL_VERSION = 0x1F02;
    private static final int GL_TRIANGLES = 0x0004, GL_PATCHES = 0x000E, GL_PATCH_VERTICES = 0x8E72;
    private static final int GL_FLOAT = 0x1406, GL_UNSIGNED_INT = 0x1405;
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_ELEMENT_ARRAY_BUFFER = 0x8893, GL_BUFFER_SIZE = 0x8764, GL_STATIC_DRAW = 0x88E4, GL_VERTEX_ARRAY_BINDING = 0x85B5;
    private static final int GL_VERTEX_SHADER = 0x8B31, GL_FRAGMENT_SHADER = 0x8B30, GL_TESS_CONTROL_SHADER = 0x8E88, GL_TESS_EVALUATION_SHADER = 0x8E87;
    private static final int GL_COMPILE_STATUS = 0x8B81, GL_LINK_STATUS = 0x8B82;
    private static final int GL_CULL_FACE = 0x0B44, GL_FRONT = 0x0404, GL_DEPTH_TEST = 0x0B71, GL_LESS = 0x0201, GL_LEQUAL = 0x0203, GL_BLEND = 0x0BE2, GL_BACK = 0x0405;
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
     * The two rows of corners the mouth opens between, in the model as made, by how far from the
     * middle of the mouth: the upper lip's front, which stays with the head, and the line where
     * the lips meet, which goes down with the jaw. Both rows dip towards the corners.
     */
    static final String LIP_ROWS = """
            float lipTop(float x) {
                return 0.8565 - 0.0013 * (x / 0.02) * (x / 0.02);
            }

            float lipLine(float x) {
                return 0.852 - 0.0034 * (x / 0.0195) * (x / 0.0195) * (x / 0.0195) * (x / 0.0195);
            }
            """;

    /**
     * A corner goes where its bones take it: each bone's matrix by how much of the corner is
     * that bone's. A bone with none of it is not read, since its number then says nothing.
     */
    private static final String POSED = LIP_ROWS + """
            FROM_MESH vec3 aPlace;
            FROM_MESH vec3 aNormal;
            FROM_MESH vec4 aWeights;
            FROM_MESH vec4 aBones;
            FROM_MESH vec2 aUv;
            FROM_MESH vec3 aSmooth;
            FROM_MESH vec4 aAlong;
            uniform mat4 uPalette[60];
            uniform float uScale;
            uniform vec2 uUvScale;
            uniform vec2 uThread;
            uniform float uLift;
            uniform float uMouth;
            uniform float uLips;
            uniform float uHeadBone;
            // The jaw, in the model as made (the face looks along -z): the hinge in front of the ear,
            // and how far it turns the chin down with the mouth wide open, in radians.
            const vec3 JAW_HINGE = vec3(0.0, 0.857, 0.028);
            const float JAW_WIDEST = 0.17;

            // How far the jaw turns a corner: by its share of the head's bone, if it is on the line
            // where the lips meet or below it and ahead of the ear. The upper lip's front row stays, so
            // the mouth opens under the upper lip. The male and female heads have their lips within half a
            // thousandth of each other, so one line between them serves both, and a beard is made
            // over the same head and goes with the chin.
            float headShare() {
                float head = 0.0;
                if (aWeights.x > 0.0 && abs(aBones.x - uHeadBone) < 0.5) head += aWeights.x;
                if (aWeights.y > 0.0 && abs(aBones.y - uHeadBone) < 0.5) head += aWeights.y;
                if (aWeights.z > 0.0 && abs(aBones.z - uHeadBone) < 0.5) head += aWeights.z;
                if (aWeights.w > 0.0 && abs(aBones.w - uHeadBone) < 0.5) head += aWeights.w;
                return head;
            }

            float jawTurnAt(vec3 at) {
                if (uMouth <= 0.0 || uHeadBone < 0.0) {
                    return 0.0;
                }
                float below = 1.0 - smoothstep(0.8525, 0.855, at.y);
                float ahead = 1.0 - smoothstep(-0.002, 0.03, at.z);
                return -JAW_WIDEST * uMouth * headShare() * below * ahead;
            }

            float jawTurn() {
                return jawTurnAt(aPlace);
            }

            // The lips spread (uLips towards 1) or rounded and pushed out (towards -1), in the model as
            // made. The mouth is a dozen corners: its two corners at x +-0.02 go out and back a little
            // for "ee", and in for "oo" while the middle of both lips comes forward and parts. The
            // face round the lips follows them less the further it is from the mouth's middle.
            const vec3 MOUTH_MIDDLE = vec3(0.0, 0.851, -0.035);
            vec3 lipped(vec3 at) {
                if (uLips == 0.0 || uHeadBone < 0.0) {
                    return at;
                }
                vec2 off = at.xy - MOUTH_MIDDLE.xy;
                float near = (1.0 - smoothstep(0.022, 0.042, length(off))) * (1.0 - smoothstep(-0.018, -0.004, at.z)) * headShare();
                float spread = max(uLips, 0.0) * near, rounded = max(-uLips, 0.0) * near;
                float side = min(abs(at.x) / 0.02, 1.0);
                // Spread or rounded, the middle of the lower lip drops while the corners stay
                // together: rounded, the strip between the lips opens to an O, and spread, to a slit
                // that shows the teeth.
                float lower = 1.0 - smoothstep(0.8525, 0.855, at.y);
                at.x *= 1.0 + 0.3 * spread - 0.4 * rounded;
                at.y += 0.0012 * spread * side * side - (0.004 * rounded + 0.0015 * spread) * lower * (1.0 - side * side);
                at.z += 0.004 * spread * side * side - 0.008 * rounded * (1.0 - 0.6 * side);
                return at;
            }

            // A way in the model as made, turned with the jaw about the line across the head.
            vec3 jawed(vec3 way, float turn) {
                float c = cos(turn), s = sin(turn);
                return vec3(way.x, way.y * c - way.z * s, way.y * s + way.z * c);
            }

            vec3 jawedPlace(float turn) {
                return JAW_HINGE + jawed(lipped(aPlace) - JAW_HINGE, turn);
            }

            // How high the corner is with the mouth as it is, and the two rows of the lips straight
            // above and below it (LIP_ROWS). The mouth's inside is drawn by how far a point is from
            // each (TEETH), so the teeth keep their size however far the jaw stretches the strip
            // between the rows.
            vec3 mouthHeights(float turn) {
                if (uHeadBone < 0.0) {
                    return vec3(0.0);
                }
                vec3 upper = vec3(aPlace.x, lipTop(aPlace.x), aPlace.z), lower = vec3(aPlace.x, lipLine(aPlace.x), aPlace.z);
                return vec3(jawedPlace(turn).y, (JAW_HINGE + jawed(lipped(upper) - JAW_HINGE, jawTurnAt(upper))).y,
                        (JAW_HINGE + jawed(lipped(lower) - JAW_HINGE, jawTurnAt(lower))).y);
            }

            // How much of a bone's matrix is pressed flat (Stumps): its volume against a whole one's
            // the size of its longest axis. A bone that turns and moves the corner keeps all of it.
            float pressed(mat4 bone) {
                vec3 a = bone[0].xyz, b = bone[1].xyz, c = bone[2].xyz;
                float most = max(length(a), max(length(b), length(c)));
                if (most < 1e-6) {
                    return 1.0;
                }
                return 1.0 - smoothstep(0.08, 0.3, abs(dot(a, cross(b, c))) / (most * most * most));
            }

            // How much of a corner is carried by pressed bones: the cut end of a limb, which is drawn as meat.
            float stumped() {
                float share = 0.0;
                if (aWeights.x > 0.0) share += pressed(uPalette[int(aBones.x)]) * aWeights.x;
                if (aWeights.y > 0.0) share += pressed(uPalette[int(aBones.y)]) * aWeights.y;
                if (aWeights.z > 0.0) share += pressed(uPalette[int(aBones.z)]) * aWeights.z;
                if (aWeights.w > 0.0) share += pressed(uPalette[int(aBones.w)]) * aWeights.w;
                return share;
            }

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
            TO_NEXT vec3 vRest;
            TO_NEXT vec3 vLean;
            TO_NEXT vec4 vAlong;
            TO_NEXT vec2 vLay;
            TO_NEXT vec3 vEye;
            TO_NEXT float vStump;
            TO_NEXT vec3 vMouth;

            void main() {
                mat4 blend = posed();
                vStump = stumped();
                float turn = jawTurn();
                vec4 place = blend * vec4(jawedPlace(turn), 1.0);
                vMouth = mouthHeights(turn);
                // The run-together normal: a seam in the texture is then no seam in the light.
                vNormal = (blend * vec4(jawed(aSmooth, turn), 0.0)).xyz;
                place.xyz = place.xyz * uScale + vNormal * (uLift / max(length(vNormal), 1e-6));
                vUv = aUv * uUvScale;
                vRest = aPlace;
                vLean = aSmooth;
                vAlong = vec4((blend * vec4(jawed(aAlong.xyz, turn), 0.0)).xyz, aAlong.w);
                vLay = aUv * uThread;
                vec4 eye = uModelView * place;
                vEye = eye.xyz;
                gl_Position = uProjection * eye;
            }
            """;

    /** Into a shadow map: where the corner is and where on the texture, and nothing the picture lights it by. */
    private static final String DEPTH_VERTEX = POSED + """
            uniform mat4 uModelView;
            uniform mat4 uProjection;
            TO_NEXT vec2 vUv;

            void main() {
                vec4 place = posed() * vec4(jawedPlace(jawTurn()), 1.0);
                place.xyz *= uScale;
                vUv = aUv * uUvScale;
                gl_Position = uProjection * (uModelView * place);
            }
            """;

    /** A shadow map keeps no colour: all it asks is whether the texel is there. */
    private static final String DEPTH_FRAGMENT = """
            uniform sampler2D uTexture;
            uniform float uAlpha;
            FROM_LAST vec2 vUv;

            void main() {
                float there = TEXEL(uTexture, vUv).a;
                if (there < 0.01 || uAlpha * there <= 0.01) {
                    discard;
                }
            }
            """;

    private static final String DIVIDED_VERTEX = POSED + """
            TO_NEXT vec3 cPlace;
            TO_NEXT vec3 cSmooth;
            TO_NEXT vec2 cUv;
            TO_NEXT vec3 cRest;
            TO_NEXT vec3 cLean;
            TO_NEXT vec4 cAlong;
            TO_NEXT vec2 cLay;
            TO_NEXT float cStump;
            TO_NEXT vec3 cMouth;

            void main() {
                mat4 blend = posed();
                cStump = stumped();
                float turn = jawTurn();
                vec4 place = blend * vec4(jawedPlace(turn), 1.0);
                cMouth = mouthHeights(turn);
                cPlace = place.xyz * uScale;
                cSmooth = (blend * vec4(jawed(aSmooth, turn), 0.0)).xyz;
                cUv = aUv * uUvScale;
                cRest = aPlace;
                cLean = aSmooth;
                cAlong = vec4((blend * vec4(jawed(aAlong.xyz, turn), 0.0)).xyz, aAlong.w);
                cLay = aUv * uThread;
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
     * At the wrist the arm is an oval, wide along the line given across the cuff, and the
     * ball that closes it is as deep as the oval is wide: the cut end of the hand is inside it
     * whichever way the wrist is bent. uWide is half the cuff's width across and through, then
     * how far the skin stands off the elbow and the shoulder.
     *
     * The three joints lie in one plane, and the line square to it is square to both bones:
     * round the arm is measured from that line, so the skin does not twist at the elbow.
     */
    private static final String SLEEVE_VERTEX = """
            FROM_MESH vec3 aPlace;
            uniform vec3 uJoint[3];
            uniform vec3 uBend;
            uniform vec3 uCuff;
            uniform vec4 uWide;
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
                vec3 ring = aPlace.x * uBend + aPlace.y * cross(way, uBend);
                vec3 skin = ring * mix(uWide.z, uWide.w, along - 1.0);
                vec3 facing = ring;
                if (along < 1.0) {
                    // An oval at the wrist, the hand's own way round, drawn in to a circle by the elbow.
                    vec3 across = normalize(uCuff - way * dot(uCuff, way));
                    vec3 through = cross(way, across);
                    float wide = mix(uWide.x, uWide.z, along), deep = mix(uWide.y, uWide.z, along);
                    float a = dot(ring, across), b = dot(ring, through);
                    skin = across * (a * wide) + through * (b * deep);
                    facing = normalize(across * (a * deep) + through * (b * wide));
                }
                float closing = (1.0 - clamp(-aPlace.z, 0.0, 1.0)) * 1.5708;
                vOut = facing * sin(closing) - way * cos(closing);
                vWay = way;
                vRound = aPlace;
                gl_Position = uProjection * vec4(middle + skin * sin(closing) - way * (cos(closing) * max(uWide.x, uWide.y)), 1.0);
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
            TO_NEXT vec3 vRest;
            TO_NEXT vec3 vLean;
            TO_NEXT vec4 vAlong;
            TO_NEXT vec2 vLay;
            TO_NEXT vec3 vEye;
            TO_NEXT float vArm;
            TO_NEXT float vStump;
            TO_NEXT vec3 vMouth;

            void main() {
                mat4 blend = posed();
                vStump = stumped();
                float turn = jawTurn();
                vec4 place = blend * vec4(jawedPlace(turn), 1.0);
                vMouth = mouthHeights(turn);
                place.xyz *= uScale;
                vNormal = (blend * vec4(jawed(aNormal, turn), 0.0)).xyz;
                vUv = aUv * uUvScale;
                vRest = aPlace;
                vLean = aSmooth;
                vAlong = vec4((blend * vec4(jawed(aAlong.xyz, turn), 0.0)).xyz, aAlong.w);
                vLay = aUv * uThread;
                vEye = (uModelView * place).xyz;
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
            in vec3 cSmooth[];
            in vec2 cUv[];
            in vec3 cRest[];
            in vec3 cLean[];
            in vec4 cAlong[];
            in vec2 cLay[];
            in float cStump[];
            in vec3 cMouth[];
            out vec3 ePlace[];
            out vec3 eSmooth[];
            out vec2 eUv[];
            out vec3 eRest[];
            out vec3 eLean[];
            out vec4 eAlong[];
            out vec2 eLay[];
            out float eStump[];
            out vec3 eMouth[];
            uniform mat4 uModelView;
            uniform vec2 uFine;
            const float FINEST = 4.0;

            float pieces(vec3 a, vec3 b) {
                float away = length((uModelView * vec4((a + b) * 0.5, 1.0)).xyz);
                return mix(FINEST, 1.0, clamp((away - uFine.x) / (uFine.y - uFine.x), 0.0, 1.0));
            }

            void main() {
                ePlace[gl_InvocationID] = cPlace[gl_InvocationID];
                eSmooth[gl_InvocationID] = cSmooth[gl_InvocationID];
                eUv[gl_InvocationID] = cUv[gl_InvocationID];
                eRest[gl_InvocationID] = cRest[gl_InvocationID];
                eLean[gl_InvocationID] = cLean[gl_InvocationID];
                eAlong[gl_InvocationID] = cAlong[gl_InvocationID];
                eLay[gl_InvocationID] = cLay[gl_InvocationID];
                eStump[gl_InvocationID] = cStump[gl_InvocationID];
                eMouth[gl_InvocationID] = cMouth[gl_InvocationID];
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
            in vec3 eSmooth[];
            in vec2 eUv[];
            in vec3 eRest[];
            in vec3 eLean[];
            in vec4 eAlong[];
            in vec2 eLay[];
            in float eStump[];
            in vec3 eMouth[];
            out vec3 vNormal;
            out vec2 vUv;
            out vec3 vRest;
            out vec3 vLean;
            out vec4 vAlong;
            out vec2 vLay;
            out vec3 vEye;
            out float vStump;
            out vec3 vMouth;
            uniform mat4 uModelView;
            uniform mat4 uProjection;
            uniform float uLift;
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
                vRest = share.x * eRest[0] + share.y * eRest[1] + share.z * eRest[2];
                vLean = share.x * eLean[0] + share.y * eLean[1] + share.z * eLean[2];
                vAlong = share.x * eAlong[0] + share.y * eAlong[1] + share.z * eAlong[2];
                vLay = share.x * eLay[0] + share.y * eLay[1] + share.z * eLay[2];
                vStump = share.x * eStump[0] + share.y * eStump[1] + share.z * eStump[2];
                vMouth = share.x * eMouth[0] + share.y * eMouth[1] + share.z * eMouth[2];
                vec3 stood = mix(level, rounded, ROUNDING) + vNormal * (uLift / max(length(vNormal), 1e-6));
                vec4 eye = uModelView * vec4(stood, 1.0);
                vEye = eye.xyz;
                gl_Position = uProjection * eye;
            }
            """;

    /**
     * How a person's surface is written down for the picture's lighting, in the four numbers
     * of a pixel, and read back. The way it faces is folded flat onto a square, which keeps
     * the same fineness whichever way it faces and leaves two numbers free. The third is how
     * glossy it is, or for hair which way the strands run across it. The fourth says what it
     * is made of, whether the person stands out of doors, and one thing more by what it is:
     * how thin skin is there, or how much nap cloth has.
     *
     * The strands' way is kept as an angle in a pair of lines that lie in the surface and are
     * found from the way it faces alone, so that whoever reads the pixel finds the same pair.
     * They are found from the folded and unfolded facing, not the true one, for that reason.
     *
     * The folding is the octahedral encoding of Cigolle and others, as Krzysztof Narkowicz wrote
     * it out (THIRD-PARTY-NOTICES.md, Acknowledgements).
     */
    static final String KEPT = """
            vec2 folded(vec3 n) {
                n /= abs(n.x) + abs(n.y) + abs(n.z);
                vec2 flatways = n.z >= 0.0 ? n.xy : (1.0 - abs(n.yx)) * vec2(n.x >= 0.0 ? 1.0 : -1.0, n.y >= 0.0 ? 1.0 : -1.0);
                return flatways * 0.5 + 0.5;
            }

            vec3 unfolded(vec2 kept) {
                vec2 f = kept * 2.0 - 1.0;
                vec3 n = vec3(f, 1.0 - abs(f.x) - abs(f.y));
                float under = clamp(-n.z, 0.0, 1.0);
                n.xy += vec2(n.x >= 0.0 ? -under : under, n.y >= 0.0 ? -under : under);
                return normalize(n);
            }

            void lying(vec3 n, out vec3 first, out vec3 second) {
                float k = 1.0 / (1.0 + max(n.z, -0.999));
                float both = -n.x * n.y * k;
                first = vec3(1.0 - n.x * n.x * k, both, -n.x);
                second = vec3(both, 1.0 - n.y * n.y * k, -n.y);
            }
            """;

    /** What a part is made of, as its shader is told. A body is skin with whatever the game painted on it. */
    static final int BODY = 0, HAIR = 1, WEAVE = 2, TWILL = 3, KNIT = 4, LEATHER = 5, SLEEK = 6;

    /**
     * The fine shape of what people are made of, worked out from where a point is on the body
     * as it was made, before any bone moved it: it is fixed to the surface, moves as the
     * surface moves and never swims or sparkles. Each is left out once it is finer than the
     * pixels it would be drawn with.
     *
     * chance and chanceAt are hash13 and hash11 of David Hoskins's Hash without Sine, used
     * under its MIT licence: the notice is in THIRD-PARTY-NOTICES.md.
     */
    private static final String GRAIN = """
            float chance(vec3 p) {
                p = fract(p * 0.1031);
                p += dot(p, p.zyx + 31.32);
                return fract((p.x + p.y) * p.z);
            }

            float lumps(vec3 p) {
                vec3 cell = floor(p), f = p - cell;
                f = f * f * (3.0 - 2.0 * f);
                return mix(mix(mix(chance(cell), chance(cell + vec3(1.0, 0.0, 0.0)), f.x),
                        mix(chance(cell + vec3(0.0, 1.0, 0.0)), chance(cell + vec3(1.0, 1.0, 0.0)), f.x), f.y),
                        mix(mix(chance(cell + vec3(0.0, 0.0, 1.0)), chance(cell + vec3(1.0, 0.0, 1.0)), f.x),
                        mix(chance(cell + vec3(0.0, 1.0, 1.0)), chance(cell + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
            }

            float chanceAt(float p) {
                p = fract(p * 0.1031);
                p *= p + 33.33;
                p *= p + p;
                return fract(p);
            }

            float wander(float x) {
                float cell = floor(x), f = x - cell;
                f = f * f * (3.0 - 2.0 * f);
                return mix(chanceAt(cell), chanceAt(cell + 1.0), f);
            }

            // Strands lying side by side, that many to the unit: lay.x is across them and
            // lay.y along, and each wanders a little as it runs.
            float strands(vec2 lay, float count) {
                float across = lay.x * count + 1.4 * wander(lay.y * count * 0.06 + 3.1 * wander(lay.x * count * 0.21));
                return wander(across) * (0.7 + 0.3 * wander(lay.y * count * 0.09 + across * 0.37));
            }

            // Threads, one pitch to the unit: over and under for a plain weave, the slanting
            // ribs of twill, the upright rows of loops of a knit.
            float threads(vec2 q, float wear) {
                if (wear < 2.5) {
                    return 0.5 + 0.5 * sin(6.2832 * q.x) * sin(6.2832 * q.y);
                }
                if (wear < 3.5) {
                    return (0.5 + 0.5 * sin(6.2832 * (q.x + q.y))) * (0.75 + 0.25 * sin(12.5664 * (q.x - q.y)));
                }
                float row = fract(q.x) - 0.5;
                return (1.0 - 4.0 * row * row) * (0.6 + 0.4 * sin(6.2832 * (q.y * 1.5 + abs(row) * 1.4)));
            }

            // How high the surface stands at a place on the body as made. lean is how much
            // the surface there faces along each axis; shown is how much of the cloth's grain
            // and of the skin's the pixels can carry.
            float standing(vec3 p, vec3 lean, float wear, float flesh, vec2 shown) {
                float high = 0.0;
                if (flesh < 0.999 && shown.x > 0.0) {
                    float made = 0.0;
                    if (wear > 5.5) {
                        made = 0.0;
                    } else if (wear > 4.5) {
                        // Leather: a pebbled grain, and the creases it is worn into.
                        made = 0.45 * lumps(p * 430.0) + 0.3 * lumps(p * 1020.0) + 0.25 * smoothstep(0.35, 0.65, lumps(p * 95.0));
                    } else {
                        vec3 q = p * (wear < 2.5 ? 520.0 : wear < 3.5 ? 430.0 : 330.0);
                        // An arm is made held straight out, and what is worn on it runs along it.
                        vec2 front = abs(p.x) > 0.17 ? q.yx : q.xy;
                        made = threads(front, wear) * lean.z + threads(q.zy, wear) * lean.x + threads(q.zx, wear) * lean.y;
                    }
                    high += made * (1.0 - flesh) * shown.x;
                }
                if (flesh > 0.001 && shown.y > 0.0) {
                    // Skin: pores set into it, and the unevenness of what lies under it.
                    high += (0.3 * lumps(p * 170.0) - 0.5 * smoothstep(0.55, 0.85, lumps(p * 640.0))) * 0.6 * flesh * shown.y;
                }
                return high;
            }
            """;

    /**
     * The inside of an open mouth, by how far a point is below the upper lip's edge and above the
     * lower lip's, both with the jaw where it is, in the model as made: the upper teeth show
     * UPPER_TEETH below the one, the lower teeth LOWER_TEETH above the other, and the tongue rises
     * TONGUE_RISE over the lower teeth in the middle of a mouth open wide enough to see it.
     */
    static final float UPPER_TEETH = 0.0032f, LOWER_TEETH = 0.0019f, TONGUE_RISE = 0.0022f;
    static final String TEETH = String.format(Locale.ROOT, """
            float mouthStep(float from, float to, float t) {
                return clamp((t - from) / (to - from), 0.0, 1.0) * clamp((t - from) / (to - from), 0.0, 1.0) * (3.0 - 2.0 * clamp((t - from) / (to - from), 0.0, 1.0));
            }

            float upperTeeth(float below) {
                return 1.0 - mouthStep(%1$f - 0.0003, %1$f, below);
            }

            float lowerTeeth(float above) {
                return 1.0 - mouthStep(%2$f - 0.0003, %2$f, above);
            }

            float tongueTop(float across) {
                return %2$f + %3$f * max(1.0 - 1.6 * across * across, 0.0);
            }

            float tongue(float above, float open, float across) {
                return (1.0 - mouthStep(tongueTop(across) - 0.0004, tongueTop(across), above)) * mouthStep(0.008, 0.013, open);
            }
            """, UPPER_TEETH, LOWER_TEETH, TONGUE_RISE);

    /**
     * A texel the texture leaves clear is not drawn at all: that is how the game hides the
     * body under what is worn over it. The colour is the texture's, turned round the colour
     * wheel by the part's hue and tinted.
     *
     * For the picture's own lighting nothing is lit here. What is written is the colour, with
     * the game's light where the person stands beside it, and what KEPT describes: the way the
     * surface faces once its grain is on it, and what it is. What a part is made of comes with
     * the part, and on a body is told texel by texel: skin, the blood on it, the wet of an
     * eye, and the clothes the game paints straight onto the skin.
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
            uniform float uShadow;
            uniform float uNearCut;
            uniform float uInside;
            uniform float uWear;
            uniform float uShell;
            uniform vec2 uStands;
            uniform float uMouthGap;
            uniform float uLips;
            const vec3 MOUTH_INSIDE = vec3(0.14, 0.05, 0.05), TOOTH = vec3(0.84, 0.8, 0.7), TONGUE = vec3(0.38, 0.13, 0.13);
            const float INSIDE_REACH = 0.6, INSIDE_SHADE = 0.15;
            FROM_LAST vec3 vNormal;
            FROM_LAST vec2 vUv;
            FROM_LAST vec3 vRest;
            FROM_LAST vec3 vLean;
            FROM_LAST vec4 vAlong;
            FROM_LAST vec2 vLay;
            FROM_LAST vec3 vEye;
            FROM_LAST float vStump;
            FROM_LAST vec3 vMouth;

            """ + HUE + KEPT + GRAIN + SnowShader.ON_PEOPLE + LIP_ROWS + TEETH + """
            float grey(vec3 color) {
                return dot(color, vec3(0.299, 0.587, 0.114));
            }

            // The cut end of a limb, by where the point was on the body as made: dark wet muscle in
            // strands, lighter where it is torn, and flecks of bone and fat in it.
            vec3 meat(vec3 rest) {
                float coarse = lumps(rest * 60.0), fine = lumps(rest * 220.0 + 7.0);
                vec3 muscle = mix(vec3(0.30, 0.03, 0.03), vec3(0.55, 0.10, 0.08), coarse * 0.7 + fine * 0.3);
                float fleck = smoothstep(0.78, 0.86, lumps(rest * 140.0 + 31.0));
                return mix(muscle, mix(vec3(0.80, 0.72, 0.60), vec3(0.85, 0.80, 0.70), fine), fleck);
            }

            // Hair as high as it stands at a place across and along its strands: locks, the
            // strands in them, and the single hairs, each as far as the pixels carry it.
            float combed(vec2 lay, vec3 shown) {
                return 0.5 * strands(lay, 150.0) * shown.x + 0.35 * strands(lay, 520.0) * shown.y + 0.15 * strands(lay, 1300.0) * shown.z;
            }

            void main() {
                // A person nearer the eye than this is cut away there, round the eye, and not drawn
                // swollen across the picture and torn at its near plane.
                if (dot(vEye, vEye) < uNearCut * uNearCut) {
                    discard;
                }
                // A shadow map keeps no colour: all it asks is whether the texel is there.
                if (uShadow > 0.5) {
                    float there = TEXEL(uTexture, vUv).a;
                    if (there < 0.01 || uAlpha * there <= 0.01) {
                        discard;
                    }
                    return;
                }
                bool hair = uWear > 0.5 && uWear < 1.5;
                // The game's skins are a few hundred dots across a whole person. Read through
                // a curve over the four nearest dots each way, and not a straight blend of
                // two, the edge of a sleeve or a wound is a clean line and not a blur; the
                // colour is then drawn a little away from the mean of what is round it.
                // The weights of the curve, and the pairs of dots the card is left to mix, are MJP's
                // SampleTextureCatmullRom, used under its MIT licence: THIRD-PARTY-NOTICES.md.
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
                // Where the cut round the eye opens a person, the inside of what it went through
                // shows: drawn as a solid cut end in that part's own colour, in shade, and not as
                // a hole onto what is behind. Further off no inside is seen through a cut.
                if (uInside != 0.0 && gl_FrontFacing == (uInside > 0.0)) {
                    if (dot(vEye, vEye) > INSIDE_REACH * INSIDE_REACH) {
                        discard;
                    }
                    vec3 inner = clamp(color * uTint * INSIDE_SHADE, 0.0, 1.0);
                    if (uBare < 0.5) {
                        gl_FragData[0] = vec4(uAlpha * inner, alpha);
                        return;
                    }
                    if (alpha < 0.5) {
                        discard;
                    }
                    vec2 toEye = floor(folded(normalize(-vEye)) * 255.0 + 0.5) / 255.0;
                    float stood = uStands.x < 0.0 ? 255.0 : 128.0 + floor(clamp(uStands.x, 0.0, 1.0) * 126.0 + 0.5);
                    gl_FragData[0] = vec4(inner, stood / 255.0);
                    gl_FragData[1] = vec4(toEye, 0.0, (128.0 + 64.0 + (uStands.y > 0.5 ? 16.0 : 0.0)) / 255.0);
                    return;
                }
                // Most of a cut end's corners are only partly on the pressed bones: the meat reaches up the
                // stump from them, as far as the skin the bone above pulls in over the end.
                if (vStump > 0.02) {
                    color = mix(color, meat(vRest), smoothstep(0.03, 0.35, vStump));
                }
                // How bright the texture is round a texel, a few dots off each way, how much
                // brighter the texel is than that, and which way over the texture it gets brighter.
                float stands = 0.0, about = 0.0;
                vec2 brighter = vec2(0.0);
                if (uBare > 0.5 && uWear < 1.5) {
                    float reach = hair ? 0.06 : 0.0137;
                    float east = grey(TEXEL(uTexture, vUv + vec2(reach, 0.0)).rgb), west = grey(TEXEL(uTexture, vUv - vec2(reach, 0.0)).rgb);
                    float north = grey(TEXEL(uTexture, vUv + vec2(0.0, reach)).rgb), south = grey(TEXEL(uTexture, vUv - vec2(0.0, reach)).rgb);
                    about = 0.25 * (east + west + north + south);
                    brighter = vec2(east - west, north - south) / (2.0 * reach);
                    float here = grey(color);
                    stands = here - about;
                    if (hair) {
                        // The game paints hair as a few broad strokes of light and dark. They
                        // are kept as the lie of the locks and drawn in towards the tone round
                        // them, or the strands laid over them would be lost in the stripes.
                        color *= clamp(sqrt(max(about, 0.02) / max(here, 0.02)), 0.7, 1.5);
                    }
                }
                if (hair && uBare > 0.5) {
                    // The fringe: layers stood a little off the hair, each with fewer strands
                    // in it than the one under, leaning the way the hair runs.
                    if (uShell > 0.0 && strands(vLay + vec2(0.0, -0.006 * uShell), 430.0) < mix(0.5, 0.86, uShell)) {
                        discard;
                    }
                    // Where the mesh stops the hair does not stop in a straight line: some
                    // strands end sooner than others.
                    if (vAlong.w > 0.3 && vAlong.w > 0.3 + 0.45 * strands(vLay, 300.0) + 0.25 * strands(vLay, 900.0)) {
                        discard;
                    }
                }
                if (uHue != 0.0) {
                    color = turned(color, uHue);
                }
                float gap = 0.0;
                if (uMouthGap > 0.0) {
                    // Between the lips, which the jaw pulls open: the strip of the head as made from
                    // the line where the lips meet up to the upper lip's front (LIP_ROWS), inside the
                    // corners. It narrows towards both lips, so the mouth opens to an oval and not a box.
                    float top = lipTop(vRest.x), line = lipLine(vRest.x);
                    float across = clamp((2.0 * vRest.y - top - line) / (top - line), -1.0, 1.0);
                    float reach = 0.0195 * sqrt(1.0 - 0.75 * across * across);
                    gap = smoothstep(line - 0.0004, line + 0.0002, vRest.y) * (1.0 - smoothstep(top - 0.0006, top, vRest.y))
                            * (1.0 - smoothstep(reach - 0.006, reach, abs(vRest.x))) * (1.0 - smoothstep(-0.027, -0.022, vRest.z))
                            * min(uMouthGap * 3.0, 1.0);
                    // Behind the lips: the upper teeth hang from the upper lip, the lower teeth and the
                    // tongue go down with the lower lip, and towards the corners all of it is in the
                    // cheeks' shadow. Rounded lips are pushed out, so less of the teeth shows past them.
                    float below = vMouth.y - vMouth.x, above = vMouth.x - vMouth.z;
                    float cheeks = smoothstep(0.45, 0.9, abs(vRest.x) / 0.0195);
                    float shown = (1.0 - 0.7 * max(-uLips, 0.0)) * (1.0 - cheeks);
                    vec3 inside = mix(MOUTH_INSIDE, TONGUE, tongue(above, vMouth.y - vMouth.z, vRest.x / 0.0195) * (1.0 - 0.6 * cheeks));
                    inside = mix(inside, 0.8 * TOOTH, lowerTeeth(above) * shown);
                    inside = mix(inside, TOOTH, upperTeeth(below) * shown);
                    color = mix(color, inside, gap);
                }
                vec3 n = normalize(vNormal);
                if (uMouthGap > 0.0) {
                    // The strip the jaw opens is the underside of the upper lip, which faces down and
                    // away from the light. What is seen through it faces out of the mouth, as the
                    // stretched strip itself does.
                    vec3 outward = normalize(cross(dFdx(vEye), dFdy(vEye)));
                    outward *= sign(dot(outward, -vEye));
                    n = normalize(mix(n, normalize(transpose(mat3(uModelView)) * outward), gap));
                }
                if (uBare < 0.5) {
                    vec3 light = uAmbient;
                    for (int i = 0; i < 5; i++) {
                        light += uLightColor[i] * max(dot(n, uLightWay[i]), 0.0);
                    }
                    gl_FragData[0] = vec4(uAlpha * color * uTint * min(light, vec3(1.0)), alpha);
                    return;
                }
                // The picture draws a person whole or not at all.
                if (alpha < 0.5) {
                    discard;
                }
                vec3 paint = color * uTint;
                vec3 facing = normalize(mat3(uModelView) * n);
                float scale = length(uModelView[1].xyz);
                float kind = 2.0, gloss = 0.0, low = 0.0, third = 0.0;
                vec3 flow = vec3(0.0);
                if (hair) {
                    kind = 0.0;
                    flow = mat3(uModelView) * vAlong.xyz;
                    flow -= facing * dot(flow, facing);
                    // A mesh whose texture says nothing of which way it runs: its hair hangs.
                    if (dot(flow, flow) < 1e-10) {
                        flow = mat3(uModelView) * vec3(0.0, -1.0, 0.0);
                        flow -= facing * dot(flow, facing);
                    }
                    flow = normalize(flow);
                    vec3 beside = cross(facing, flow);
                    float fine = fwidth(vLay.x);
                    vec3 shown = 1.0 - smoothstep(0.3, 0.7, fine * vec3(150.0, 520.0, 1300.0));
                    float aside = 0.0006;
                    float here = 0.0, slope = 0.0, tilt = 0.0;
                    // Too far off for the pixels to carry any of it, all three come to nought.
                    if (any(greaterThan(shown, vec3(0.0)))) {
                        here = combed(vLay, shown);
                        slope = (combed(vLay + vec2(aside, 0.0), shown) - combed(vLay - vec2(aside, 0.0), shown)) / (2.0 * aside);
                        // Each lock lies a little steeper or flatter than the next, which is what
                        // breaks the light along a head of hair into streaks and not one ring.
                        tilt = (strands(vLay, 240.0) - 0.42) * shown.x;
                    }
                    facing = normalize(facing - beside * (slope * 0.0011) + flow * (tilt * 0.5));
                    flow = normalize(flow - facing * dot(flow, facing));
                    paint *= 0.74 + 0.5 * here;
                } else {
                    bool body = uWear < 0.5;
                    float wear = uWear;
                    float most = max(paint.r, max(paint.g, paint.b)), least = min(paint.r, min(paint.g, paint.b));
                    float strong = (most - least) / max(most, 0.001);
                    // Blood is a dark red, redder by half again than it is green or blue.
                    // The inside of an open mouth is as red as that, and is no blood.
                    float blood = smoothstep(1.4, 1.8, paint.r / max(max(paint.g, paint.b), 0.02)) * step(0.12, paint.r) * (1.0 - smoothstep(0.62, 0.8, paint.r))
                            * (1.0 - gap);
                    float flesh = 0.0, wet = 0.0;
                    vec2 ball = vec2(0.0);
                    if (body) {
                        // A body is skin, living or dead, bruised, pale or dark, except where
                        // the game painted clothes on it, and those are told by what skin
                        // never is: strongly coloured away from skin's own orange, bluer than
                        // it is red, clean white, or black. A head is skin whatever its
                        // colour: the white of an eye is no shirt. What is painted on is
                        // shoes on the feet, trousers on the legs, denim where they are
                        // blue, and a shirt above.
                        float warm = (paint.g - paint.b) / max(paint.r - paint.b, 0.001);
                        float tanned = step(paint.g, paint.r) * step(paint.b, paint.g) * step(0.15, warm) * step(warm, 0.8);
                        float clothed = max(smoothstep(0.4, 0.55, strong) * (1.0 - tanned), step(paint.r + 0.04, paint.b) * step(paint.g, paint.b + 0.02));
                        clothed = max(clothed, smoothstep(0.78, 0.86, most) * (1.0 - smoothstep(0.04, 0.08, strong)));
                        clothed = max(clothed, 1.0 - smoothstep(0.08, 0.14, most));
                        flesh = max(max(1.0 - clothed, smoothstep(0.8, 0.83, vRest.y)), blood);
                        wear = vRest.y < 0.07 ? 5.0 : vRest.y < 0.52 ? (paint.b > paint.r + 0.03 ? 3.0 : 2.0) : 4.0;
                        // On the front of the head, what is nearly white and much brighter
                        // than the skin round it is the white of an eye or a tooth, and what
                        // is nearly black and much darker is the dark of an eye: both are wet.
                        float face = smoothstep(0.835, 0.85, vRest.y) * (1.0 - smoothstep(0.925, 0.94, vRest.y)) * (1.0 - smoothstep(-0.008, 0.0, vRest.z));
                        float tone = grey(color);
                        float white = smoothstep(0.1, 0.2, stands) * smoothstep(0.78, 0.88, tone) * (1.0 - smoothstep(0.1, 0.2, strong));
                        float dark = smoothstep(0.22, 0.36, -stands) * (1.0 - smoothstep(0.1, 0.2, tone));
                        // The inside of an open mouth is darker than the face round it too, and is in shadow: it does not shine as an eye does.
                        wet = max(white, dark) * face * (1.0 - gap);
                        // An eye is a ball set in the face, and its wet shows as a point of
                        // light only if it is round. The middle of a white has white all
                        // round it and its rim has not, so how white it is round a texel
                        // rises to the middle of the eye as a ball does, and the dark of
                        // an eye the other way about. ball is which way over the texture
                        // the eye rises, and how steeply.
                        ball = brighter * (wet * (white > dark ? 0.05 : -0.05) * scale);
                        // Fingers, ears and the end of the nose are thin enough for light to come through.
                        float head = smoothstep(0.85, 0.87, vRest.y) * (1.0 - smoothstep(0.92, 0.94, vRest.y));
                        low = flesh * max(smoothstep(0.415, 0.46, abs(vRest.x)), head * max(smoothstep(0.047, 0.058, abs(vRest.x)), 0.5 * (1.0 - smoothstep(-0.046, -0.036, vRest.z))));
                    }
                    float fine = max(length(dFdx(vRest)), length(dFdy(vRest)));
                    float pitch = wear > 5.5 ? 1.0 : wear > 4.5 ? 430.0 : wear < 2.5 ? 520.0 : wear < 3.5 ? 430.0 : 330.0;
                    vec2 shown = 1.0 - smoothstep(0.3, 0.7, fine * vec2(pitch, 640.0));
                    vec3 lean = pow(abs(normalize(vLean)), vec3(4.0));
                    lean /= lean.x + lean.y + lean.z;
                    float here = standing(vRest, lean, wear, flesh, shown);
                    vec3 across = dFdx(vEye), down = dFdy(vEye);
                    vec3 first = cross(down, facing), second = cross(facing, across);
                    float room = dot(across, first);
                    float deep = mix(mix(0.00028, 0.0002, step(4.5, wear)), 0.00016, flesh) * scale;
                    float byAcross = (standing(vRest + dFdx(vRest), lean, wear, flesh, shown) - here) * deep + dot(ball, dFdx(vUv));
                    float byDown = (standing(vRest + dFdy(vRest), lean, wear, flesh, shown) - here) * deep + dot(ball, dFdy(vUv));
                    if (abs(room) > 1e-12) {
                        facing = normalize(abs(room) * facing - sign(room) * (byAcross * first + byDown * second));
                    }
                    paint *= 1.0 + mix(0.24, 0.1, flesh) * (here - 0.45 * (1.0 - flesh) * shown.x * step(wear, 5.5));
                    // A wet white is not as white as paint, or no light could show on it.
                    paint *= 1.0 - 0.2 * wet;
                    // Blood is darker than what it is on, and shines.
                    paint *= 1.0 - 0.3 * blood;
                    if (flesh > 0.5) {
                        kind = 1.0;
                        gloss = max(0.7 * blood, wet);
                    } else {
                        // How glossy what is worn is, and how much nap it has.
                        vec2 finish = wear < 2.5 ? vec2(0.05, 0.5) : wear < 3.5 ? vec2(0.07, 0.3) : wear < 4.5 ? vec2(0.02, 0.9) : wear < 5.5 ? vec2(0.6, 0.0) : vec2(0.45, 0.0);
                        gloss = max(finish.x, 0.7 * blood);
                        low = finish.y * (1.0 - blood);
                    }
                }
                // Snow settled on the person, by how far the surface faces up as it is made, not as
                // its grain tilts it: where it lies thick it is what the surface is, and faces up.
                float snowLies = snowOnPerson(dot(normalize(mat3(uModelView) * n), uUpSeen), vRest.xz);
                if (snowLies > 0.0) {
                    paint = mix(paint, SNOW_WHITE, snowLies);
                    facing = normalize(facing + uUpSeen * snowLies);
                    if (snowLies > 0.5) {
                        hair = false;
                        kind = 2.0;
                        gloss = 0.0;
                        low = 0.9;
                    }
                }
                vec2 kept = floor(folded(facing) * 255.0 + 0.5) / 255.0;
                if (hair) {
                    vec3 one, other;
                    lying(unfolded(kept), one, other);
                    third = fract(atan(dot(flow, other), dot(flow, one)) / 3.14159265 + 1.0);
                } else {
                    third = gloss;
                }
                float where = uStands.x < 0.0 ? 255.0 : 128.0 + floor(clamp(uStands.x, 0.0, 1.0) * 126.0 + 0.5);
                float what = 128.0 + kind * 32.0 + (uStands.y > 0.5 ? 16.0 : 0.0) + floor(clamp(low, 0.0, 1.0) * 15.0 + 0.5);
                gl_FragData[0] = vec4(clamp(paint, 0.0, 1.0), where / 255.0);
                gl_FragData[1] = vec4(kept, third, what / 255.0);
            }
            """;

    /**
     * For the pass that lights the picture's people: reading back what FRAGMENT wrote of a
     * surface, and the light each kind of surface throws back.
     *
     * Hair is lit along its strands: a narrow light the colour of the sun towards their tips
     * and a broad one the colour of the hair towards their roots, both lying across the
     * strands as a band, and a glow through its edge with the sun behind it. Skin takes the
     * light a little way in: warm where it turns from the light, red through an ear or a
     * finger, with a broad faint shine, a sharper one at a glance, and a hard point of light
     * where it is wet. What is worn is dull, its nap bright at the outline, with a highlight
     * that is tighter the glossier it is: leather and rubber against cloth.
     */
    static final String LIT = KEPT + """
            // The way a kept surface faces, as the eye sees it, and what else was kept of it:
            // worn is 0 for hair, 1 for skin, 2 for what is worn, 3 for a surface that says
            // nothing more than its facing.
            vec3 surfaceOf(vec4 kept, out float worn, out float gloss, out float low, out float outdoors, out vec3 strand) {
                float what = floor(kept.a * 255.0 + 0.5) - 128.0;
                worn = floor(what / 32.0);
                what -= worn * 32.0;
                outdoors = step(15.5, what);
                low = (what - outdoors * 16.0) / 15.0;
                gloss = kept.b;
                strand = vec3(0.0);
                if (worn > 2.5) {
                    return kept.xyz * 2.0 - 1.0;
                }
                vec3 n = unfolded(kept.xy);
                if (worn < 0.5) {
                    vec3 one, other;
                    lying(n, one, other);
                    float turn = kept.b * 3.14159265;
                    strand = one * cos(turn) + other * sin(turn);
                    gloss = 0.0;
                }
                return n;
            }

            // How much skin and what is worn mirror looked at square on: water and oil, and cloth, leather and rubber.
            const float SKIN_MIRRORS = 0.028, WORN_MIRRORS = 0.04;
            // 1 where skin and what is worn mirror as the world's surfaces do (glossed, mirroredShare),
            // 0 in Low, which keeps the look it has always had.
            uniform float uFigureMirror;

            // paint is the surface's own colour, way the way to the sun, sunLit the sun's
            // light where it reaches, around the light from all round, lamps what lamps
            // give, skyBack the sky the surface would mirror.
            vec3 dressed(vec3 paint, vec3 n, vec3 view, vec3 strand, float worn, float gloss, float low, vec3 way, vec3 sunLit, vec3 around,
                    vec3 lamps, vec3 skyBack) {
                float towards = dot(n, way);
                float seen = clamp(dot(n, view), 0.0, 1.0);
                vec3 between = normalize(way + view);
                float rim = pow(1.0 - seen, 4.0);
                float mirror = 0.03 + 0.97 * pow(1.0 - seen, 5.0);
                float high = clamp(dot(n, between), 0.0, 1.0);
                if (worn < 0.5) {
                    float alongSun = dot(strand, way);
                    float lit = clamp((towards + 0.45) / 1.45, 0.0, 1.0) * mix(1.0, sqrt(max(1.0 - alongSun * alongSun, 0.0)), 0.5);
                    vec3 color = paint * (around + lamps + sunLit * (1.5 * lit));
                    float most = max(paint.r, max(paint.g, paint.b));
                    vec3 tone = paint / max(most, 0.02);
                    float front = smoothstep(-0.2, 0.3, towards);
                    float first = dot(normalize(strand + n * 0.14), between), second = dot(normalize(strand - n * 0.24), between);
                    float glint = pow(max(1.0 - first * first, 0.0), 32.0);
                    float glow = pow(max(1.0 - second * second, 0.0), 8.0);
                    color += sunLit * (front * (0.14 * glint * mix(vec3(1.0), tone, vec3(0.3)) + 0.4 * glow * sqrt(most) * tone));
                    // The sky lies along it as a faint band of its own.
                    float third = dot(strand, normalize(vec3(0.0, 0.0, 1.0) + view));
                    color += around * tone * (0.12 * sqrt(most) * pow(max(1.0 - third * third, 0.0), 12.0));
                    color += sunLit * tone * (0.6 * sqrt(most) * rim * pow(max(dot(-view, way), 0.0), 3.0));
                    return color;
                }
                if (worn < 1.5) {
                    float lit = clamp((towards + 0.25) / 1.25, 0.0, 1.0);
                    vec3 color = paint * (around + lamps + sunLit * (1.5 * lit));
                    float edge = smoothstep(0.0, 0.25, lit) * (1.0 - smoothstep(0.25, 0.6, lit));
                    color += paint * sunLit * vec3(0.55, 0.16, 0.08) * edge;
                    color += paint * sunLit * vec3(0.95, 0.28, 0.14) * (0.9 * low * clamp(0.35 - towards, 0.0, 1.0));
                    float dry = 1.0 - gloss;
                    if (uFigureMirror > 0.5) {
                        // Skin's oil mirrors as the world's surfaces do, in two lobes, a broad one
                        // and a tighter, fainter one, and sweat or blood on it tighter still.
                        float broad = mix(0.48, 0.2, gloss), tight = mix(0.3, 0.18, gloss);
                        color += sunLit * (1.5 * mix(glossed(n, view, way, broad, SKIN_MIRRORS), glossed(n, view, way, tight, SKIN_MIRRORS), 0.15));
                        color += skyBack * mirroredShare(SKIN_MIRRORS, broad, max(seen, 0.001));
                    } else {
                        float glance = 0.028 + 0.972 * pow(1.0 - clamp(dot(view, between), 0.0, 1.0), 5.0);
                        float shine = dry * (0.05 * pow(high, 10.0) + (0.02 + 0.3 * glance) * pow(high, 48.0))
                                + gloss * mix(0.5, 6.0, gloss) * pow(high, mix(90.0, 380.0, gloss * gloss));
                        color += sunLit * (shine * smoothstep(-0.05, 0.15, towards));
                        color += skyBack * (mirror * (0.22 * dry + 0.35 * gloss) + 0.05 * gloss * gloss);
                    }
                    color += paint * around * (0.35 * rim * dry);
                    return color;
                }
                float lit = clamp((towards + 0.12) / 1.12, 0.0, 1.0);
                vec3 color = paint * (around + lamps + sunLit * (1.5 * lit));
                color += paint * (around * (0.9 * rim * low) + sunLit * (0.5 * rim * low * lit));
                if (uFigureMirror > 0.5) {
                    // What is worn mirrors as the world's surfaces do, from felt to wet rubber by its gloss.
                    float rough = mix(0.9, 0.28, gloss);
                    color += sunLit * (1.5 * glossed(n, view, way, rough, WORN_MIRRORS));
                    color += skyBack * mirroredShare(WORN_MIRRORS, rough, max(seen, 0.001));
                } else {
                    color += sunLit * (max(towards, 0.0) * mix(0.035, 0.75, gloss) * pow(high, mix(7.0, 110.0, gloss)));
                    color += skyBack * mirror * (0.1 + 0.7 * gloss);
                }
                return color;
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
    private static final float[] NOWHERE = new float[3];
    /** What a bone is to an arm. The left arm's are these and KINDS more. */
    static final int HAND = 1, FORE = 2, UPPER = 3, KINDS = 3;
    /** How many sides the picture's arm has, and how far along it each ring of them is. */
    private static final int SIDES = 12;
    private static final float[] RINGS = {-1f, -0.75f, -0.5f, -0.25f, 0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f, 1.125f, 1.25f,
        1.375f, 1.5f, 1.625f, 1.75f, 1.875f, 2f, 2.5f, 3f};

    /** One program and where its settings live. */
    private static final class Skin {
        final int id;
        final int palette, modelView, projection, scale, uvScale, alpha, hue, tint, ambient, fine, bare, shadow, nearCut, inside, texSize, mouth,
                headBone, mouthGap, lips;
        final int[] lightWay = new int[LIGHTS], lightColor = new int[LIGHTS];
        /** The person whose settings the program holds (CharacterDraw.peopleBegun): a part of the same person sets only its own. */
        int heldFor = -1;

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
            bare = GL20.glGetUniformLocation(id, "uBare");
            shadow = GL20.glGetUniformLocation(id, "uShadow");
            nearCut = GL20.glGetUniformLocation(id, "uNearCut");
            inside = GL20.glGetUniformLocation(id, "uInside");
            texSize = GL20.glGetUniformLocation(id, "uTexSize");
            mouth = GL20.glGetUniformLocation(id, "uMouth");
            lips = GL20.glGetUniformLocation(id, "uLips");
            headBone = GL20.glGetUniformLocation(id, "uHeadBone");
            mouthGap = GL20.glGetUniformLocation(id, "uMouthGap");
            for (int light = 0; light < LIGHTS; light++) {
                lightWay[light] = GL20.glGetUniformLocation(id, "uLightWay[" + light + "]");
                lightColor[light] = GL20.glGetUniformLocation(id, "uLightColor[" + light + "]");
            }
            Program.useOther(id);
            GL20.glUniform1i(GL20.glGetUniformLocation(id, "uTexture"), 0);
        }
    }

    /** The picture's copy of one of the game's meshes. */
    private static final class Copy {
        final VertexBufferObject from;
        final int modification;
        /** Zero for a mesh the picture cannot draw, which is left to the game. The vertex array holds the other two and how the vertices are read. */
        final int vertices, indices, array, count;
        /** The right arm and the left, and the bone of each hand: null for a mesh that is not drawn. */
        final Limb[] limbs;
        final int[] handBone;
        /** How far over the surface one whole width of the texture goes, and one whole height: null for a mesh that is not drawn. */
        final float[] lay;
        /** What this mesh was found to be made of in each texture it has been drawn in. */
        final HashMap<String, Integer> wears = new HashMap<>();

        Copy(VertexBufferObject from, int modification, int vertices, int indices, int array, int count, Limb[] limbs, int[] handBone, float[] lay) {
            this.from = from;
            this.modification = modification;
            this.vertices = vertices;
            this.indices = indices;
            this.array = array;
            this.count = count;
            this.limbs = limbs;
            this.handBone = handBone;
            this.lay = lay;
        }
    }

    private record Spot(int x, int y, int z) {}

    private static boolean prepared;
    /** Set once something of the game's is not as this build knows it: every person is then the game's to draw. */
    private static boolean off;
    private static Skin whole, divided, depth;
    /** The program that draws the viewer's body in its two pieces, or null if the driver refused it. */
    private static Skin cut;
    private static int cutArm, cutKeep;
    /** While set, the part is drawn by the cut program: which bones it is cut along, and which side of the cut is kept. */
    private static float[] cutBones;
    private static boolean cutKeepsThem;
    /**
     * For each skeleton, 1 at each bone of the right arm, the same with the left arm's too,
     * then the same two for what is carried with a held weapon, and last the left's of that
     * alone. What is carried is the hands alone with OWN_ARMS set, and the whole arms without.
     */
    private static final IdentityHashMap<SkinningData, float[][]> arms = new IdentityHashMap<>();
    /**
     * Set while the viewer's own body is drawn from their own eye. The eye is inside the head,
     * and the head is then drawn with every corner of it at the eye: seen from there it has
     * no size, and a triangle that runs from the neck to it lies along the line of sight and
     * has none either. However the head is turned, none of it is in the view.
     */
    static boolean fromTheEye;
    /**
     * While the eye's picture of other people is drawn, how near the eye, in tiles, they are cut
     * away: a zombie shoved or reeling brings its head to within a few centimetres of the eye,
     * where a face fills the picture skewed and its hair is torn by the near plane. Nought
     * otherwise: the viewer's own body, shadows, mirrors and scopes are drawn whole.
     */
    static float nearCut;
    /** That distance. A face as near as this still reads as a face in the picture. */
    static final float NEAR_CUT = 0.15f;
    /** A person whose middle is further than this from the eye, in tiles, has nothing within the cut. */
    private static final float INSIDE_WITHIN = 3f;
    /**
     * Set while a person is drawn as a mirror shows them. A mirror turns every triangle's
     * front to its back once more.
     */
    static boolean mirrored;
    /**
     * Set while people are drawn into the small picture of a vehicle's mirror, where nobody is
     * big enough to show divided triangles, a finer skin or a fringe of hair.
     */
    static boolean plainly;
    /** For each skeleton, the bones of its head. */
    private static final IdentityHashMap<SkinningData, int[]> heads = new IdentityHashMap<>();
    private static final String HEAD_BONE = "Bip01_Head";
    private static final FloatBuffer headless = ByteBuffer.allocateDirect(BONES * 16 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
    /** The program that draws the picture's own arms, and their mesh. */
    private static int sleeve, sleeveVertices, sleeveIndices, sleeveCount;
    /** Where the sleeve program's uniforms live, asked of the driver once, as the program is made. */
    private static int sleeveBend, sleeveCuff, sleeveWide, sleeveProjection, sleeveAlpha, sleeveHue, sleeveTint, sleeveHas;
    private static final int[] sleeveJoint = new int[3], sleeveForeUv = new int[Limb.SAMPLES], sleeveUpperUv = new int[Limb.SAMPLES];
    private static Field handleField, elementsField, formatField;
    private static final IdentityHashMap<ModelMesh, Copy> copies = new IdentityHashMap<>();

    /**
     * The person begun and how they are drawn, set by begin before every person. There is one
     * of each for every view: the views' pictures are drawn one after another on one thread,
     * and nothing here is read before begin has set it again.
     */
    private static ModelSlotRenderData person;
    private static float[] modelView, projection;
    private static boolean shadow;
    /** How far from the eye the person begun stands, in tiles. */
    private static float away;
    /** How far the mouth of the person begun is open, 0 to 1: see Mouths. */
    private static float mouthOpen;
    /** How the lips of the person begun are shaped, spread towards 1 and rounded towards -1: see Mouths. */
    private static float mouthLips;
    /** How far from the viewer's eye the person begun stands, in tiles, in every pass alike: see Characters.fromViewer. */
    private static float fromEye;
    /** Whether the person begun wears finer skins: see FineSkinRange. */
    private static boolean finelySkinned;
    private static final FineSkinRange FINE_SKINS = new FineSkinRange();
    /**
     * Set while people are drawn into a target that has a second picture for the way their
     * surfaces face: they are then drawn unlit, for the picture to light.
     */
    static boolean bare;
    /** The game's light on the square the person begun stands on, or less than nought when nobody has said, and whether that square is out of doors. */
    private static float standsIn = -1f;
    private static boolean standsOut;
    /** How much snow has settled on the person begun, and the world's up as the view they are drawn through sees it. */
    private static float snowOn;
    private static final float[] upSeen = {0f, 1f, 0f};
    private static final boolean TRACE = Boolean.getBoolean("cameraunlock.zomboid.trace");
    /** For each program, by its number, where its settings for what a part is made of live. */
    private static int[][] dressing = new int[0][];
    private static final int[] FIRST = {0x8CE0}, BOTH = {0x8CE0, 0x8CE1};
    private static final float[] lightWay = new float[LIGHTS * 3], lightColor = new float[LIGHTS * 3];
    private static Vector2 uvScale;

    static void setDown() {
        person = null;
    }

    /**
     * Before the parts of one person: the matrix that stands them before the eye, the eye's
     * projection, whether this is a shadow map, which is drawn undivided since nothing of
     * a shadow's edge shows the difference, and how far from the viewer's eye the person
     * stands, in tiles (Characters.fromViewer), which a shadow map's own matrix does not say.
     */
    static void begin(ModelSlotRenderData data, float[] standing, float[] seen, boolean casting, float fromTheViewer) {
        person = data;
        fromEye = fromTheViewer;
        peopleBegun++;
        modelView = standing;
        projection = seen;
        shadow = casting;
        away = (float) Math.sqrt(standing[12] * standing[12] + standing[13] * standing[13] + standing[14] * standing[14]);
        finelySkinned = !casting && !plainly && FINE_SKINS.wears(data.object != null ? data.object : data, fromEye, Pipeline.frame());
        standsIn = -1f;
        snowOn = 0f;
        Mouths.Shape mouth = Mouths.of(data.character);
        mouthOpen = mouth.open();
        mouthLips = mouth.lips();
        if (!off && !bare && !casting) {
            lights(data);
        }
    }

    /** Whether the person begun wears finer skins in this pass: never in a shadow map or the vehicle mirror, which then leave FineSkinRange alone. */
    static boolean finelySkinned() {
        return finelySkinned;
    }

    /**
     * The game's light on the square the person begun stands on, as the world round them is
     * lit by it, and whether that square is out of doors. Said after begin, or not at all: a
     * person nobody has said it of is lit as one standing under the open sky.
     */
    static void stands(float light, boolean outside) {
        standsIn = Math.clamp(light, 0f, 1f);
        standsOut = outside;
    }

    /** How much snow has settled on the person begun, 0 to 1: see SnowCarried. Said after begin, or none is drawn on them. */
    static void snowed(float dust) {
        snowOn = dust;
    }

    /** The view the people are drawn through from here on, by columns: the world's up as it sees it is what snow settles on. */
    static void looking(float[] view) {
        upSeen[0] = view[8];
        upSeen[1] = view[9];
        upSeen[2] = view[10];
    }

    /** The world's up as the eye of the view people are drawn through sees it: see looking. */
    static float[] upSeen() {
        return upSeen;
    }

    /**
     * What a part is made of, by the names of its model and its texture, which the game gives
     * as paths or as bare names, in either case of letter. A body is skin and whatever the
     * game painted on it, and its texture is one the game composed for a character.
     *
     * @param composedForBody whether the texture is the game's composition of a whole character's skin
     */
    static int wearOf(String model, String texture, boolean composedForBody) {
        String named = (within(model) + " " + within(texture)).toLowerCase(Locale.ROOT);
        if (has(named, "hair", "beard")) {
            return HAIR;
        }
        if (composedForBody || has(named, "malebody", "zedbody", "skeleton")) {
            return BODY;
        }
        if (has(named, "padded")) {
            return WEAVE;
        }
        if (has(named, "leather", "boot", "shoe", "belt", "holster", "glove", "hide", "biker")) {
            return LEATHER;
        }
        if (has(named, "hazmat", "rubber", "vinyl", "plastic", "helmet", "hardhat", "goggle", "glasses", "mask", "pad", "armor", "armour", "cuirass", "scba",
                "tire", "metal", "medal", "necklace", "choker", "garbage", "tarp")) {
            return SLEEK;
        }
        if (has(named, "jumper", "sweater", "hoodie", "tshirt", "t-shirt", "sock", "beanie", "wool", "scarf", "tanktop", "legging", "stocking",
                "balaclava", "longjohn")) {
            return KNIT;
        }
        if (has(named, "denim", "jean", "dungaree", "trouser", "camo", "army", "boilersuit", "boliersuit", "shorts")) {
            return TWILL;
        }
        return WEAVE;
    }

    /** A name without the folders the game is installed in, which say nothing of what the thing is. */
    private static String within(String name) {
        if (name == null) {
            return "";
        }
        String path = name.replace('\\', '/');
        int media = path.toLowerCase(Locale.ROOT).lastIndexOf("media/");
        return media < 0 ? path : path.substring(media + 6);
    }

    private static boolean has(String named, String... words) {
        for (String word : words) {
            if (named.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** How many layers of fringe a part made of that gets at that many tiles from the eye. */
    static int fringe(int wear, float tilesAway) {
        return wear != HAIR || tilesAway >= FRINGE_FAR ? 0 : tilesAway < FRINGE_NEAR ? FRINGE_LAYERS_NEAR : FRINGE_LAYERS_FAR;
    }

    private static int[] dressingOf(int program) {
        if (program >= dressing.length) {
            dressing = Arrays.copyOf(dressing, program + 1);
        }
        if (dressing[program] == null) {
            dressing[program] = new int[] {GL20.glGetUniformLocation(program, "uWear"), GL20.glGetUniformLocation(program, "uThread"),
                GL20.glGetUniformLocation(program, "uStands"), GL20.glGetUniformLocation(program, "uShell"), GL20.glGetUniformLocation(program, "uLift"),
                GL20.glGetUniformLocation(program, "uSnowOn"), GL20.glGetUniformLocation(program, "uUpSeen")};
        }
        return dressing[program];
    }

    /** Tells the program in use what the part about to be drawn is made of, how its texture lies on it, and where its wearer stands. */
    static void dress(int program, int wear, float[] lay) {
        int[] at = dressingOf(program);
        GL20.glUniform1f(at[0], wear);
        GL20.glUniform2f(at[1], lay[0], lay[1]);
        GL20.glUniform2f(at[2], standsIn, standsOut ? 1f : 0f);
        GL20.glUniform1f(at[5], snowOn);
        GL20.glUniform3f(at[6], upSeen[0], upSeen[1], upSeen[2]);
    }

    /** Tells the program in use which layer of a part is about to be drawn: nought is the part itself, the rest its fringe, outermost last. */
    static void shell(int program, int layer, int layers, float scale) {
        int[] at = dressingOf(program);
        float out = layers == 0 ? 0f : (float) layer / layers;
        GL20.glUniform1f(at[3], out);
        GL20.glUniform1f(at[4], out * FRINGE * scale);
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
        try {
            return part(part);
        } finally {
            // A throw turns one view off, and the other's people are not to be drawn cut.
            cutBones = null;
        }
    }

    /** Draws the left hand of one part of the viewer begun, and nothing else of it. */
    static boolean leftHand(ModelInstanceRenderData part) {
        if (part.model == null || !(part.model.tag instanceof SkinningData skeleton)) {
            return false;
        }
        cutBones = armBones(skeleton)[4];
        cutKeepsThem = true;
        try {
            return part(part);
        } finally {
            cutBones = null;
        }
    }

    /**
     * A part's bones with the head's taking every corner to the eye.
     *
     * @param modelView the matrix the part is drawn with, by columns: the eye is where it takes to the origin
     * @param scale     what a corner is multiplied by after its bones have placed it and before that matrix
     */
    private static FloatBuffer headAtTheEye(SkinningData skeleton, FloatBuffer bones, float[] modelView, float scale) {
        int[] head = heads.get(skeleton);
        if (head == null) {
            head = new int[BONES];
            int found = headBones(skeleton.getBone(HEAD_BONE), head, 0);
            head = Arrays.copyOf(head, found);
            heads.put(skeleton, head);
        }
        float a = modelView[0], b = modelView[4], c = modelView[8];
        float d = modelView[1], e = modelView[5], f = modelView[9];
        float g = modelView[2], h = modelView[6], i = modelView[10];
        float x = -modelView[12], y = -modelView[13], z = -modelView[14];
        float det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
        float eyeX = ((e * i - f * h) * x + (c * h - b * i) * y + (b * f - c * e) * z) / (det * scale);
        float eyeY = ((f * g - d * i) * x + (a * i - c * g) * y + (c * d - a * f) * z) / (det * scale);
        float eyeZ = ((d * h - e * g) * x + (b * g - a * h) * y + (a * e - b * d) * z) / (det * scale);
        int floats = bones.remaining();
        headless.clear();
        headless.put(0, bones, bones.position(), floats);
        headless.limit(floats);
        for (int bone : head) {
            int at = bone * 16;
            if (at + 16 > floats) {
                continue;
            }
            for (int part = 0; part < 12; part++) {
                headless.put(at + part, 0f);
            }
            headless.put(at + 3, eyeX);
            headless.put(at + 7, eyeY);
            headless.put(at + 11, eyeZ);
        }
        return headless;
    }

    /** The number of a part's head bone, or -1 for a part that has none: a part with no skeleton of its own, or one whose head is not among the bones posed for it. */
    private static float headBone(Model model) {
        if (!(model.tag instanceof SkinningData skeleton)) {
            return -1f;
        }
        SkinningBone head = skeleton.getBone(HEAD_BONE);
        return head != null && head.index < BONES ? head.index : -1f;
    }

    /** Writes the numbers of a bone and of every bone that hangs from it, and returns how many are written. */
    private static int headBones(SkinningBone bone, int[] into, int found) {
        if (bone == null || bone.index >= BONES) {
            return found;
        }
        into[found++] = bone.index;
        if (bone.children != null) {
            for (SkinningBone child : bone.children) {
                found = headBones(child, into, found);
            }
        }
        return found;
    }

    /**
     * Whether the arms on a held weapon are the picture's own sleeves, from shoulders out of
     * sight to the game's hands on the weapon. Off, and the sleeves kept dormant: the game's
     * own arms are drawn whole with their hands, carried where the weapon is held.
     */
    static final boolean OWN_ARMS = false;

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
                    boolean carried = hand || !OWN_ARMS;
                    found[3][bone] = carried ? 1f : 0f;
                    found[2][bone] = carried && right ? 1f : 0f;
                    found[4][bone] = carried && !right ? 1f : 0f;
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

    /** How many floats `hand` says, and where among them the wrist's two half widths and the elbow's are. */
    static final int HAND_SAYS = 15, WRIST_ACROSS = 12, WRIST_THROUGH = 13, ELBOW_WIDE = 14;

    /**
     * Where a hand joins its arm, from a part of a person whose mesh has the hand and
     * the forearm both: the middle of the wrist, where the elbow would be were the forearm
     * rigid with the hand, the middle of the hand, and a point off the wrist's middle along
     * the line across the wrist, all in the axes the person is stood from, at the scale given;
     * then half the wrist's width across and through, and how far the skin stands off the
     * elbow. False for a part with no such hand.
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
        if (limb.across == null) {
            return false;
        }
        // The hand's bone carries every point: its matrix is kept by rows.
        int at = copy.handBone[side] * 16;
        FloatBuffer palette = part.matrixPalette;
        for (int point = 0; point < 4; point++) {
            float[] from = point == 0 || point == 3 ? limb.wrist : point == 1 ? limb.elbow : limb.palm;
            float[] aside = point == 3 ? limb.across : NOWHERE;
            float x = from[0] + aside[0], y = from[1] + aside[1], z = from[2] + aside[2];
            for (int axis = 0; axis < 3; axis++) {
                int row = at + axis * 4;
                out[point * 3 + axis] = (palette.get(row) * x + palette.get(row + 1) * y + palette.get(row + 2) * z + palette.get(row + 3)) * finalScale;
            }
        }
        out[WRIST_ACROSS] = limb.wristAcross * finalScale;
        out[WRIST_THROUGH] = limb.wristThrough * finalScale;
        out[ELBOW_WIDE] = limb.elbowWide * finalScale;
        return true;
    }

    /**
     * Draws what one part of the viewer begun wears on one arm, as the picture's own arm:
     * nothing for a part that has none of that arm. The parts are to be given in the order the
     * game draws them, so that what is worn over the skin is drawn over it here.
     *
     * @param joints the wrist, the elbow and the shoulder as the eye sees them
     * @param bend   the line square to both bones, of length one
     * @param cuff   the line across the cuff, square to the forearm, of length one
     * @param wide   half the cuff's width across and through, then how far the skin stands off the elbow and the shoulder
     */
    static void sleeve(ModelInstanceRenderData part, boolean left, float[] joints, float[] bend, float[] cuff, float[] wide) {
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
        Texture worn = FineSkins.of(texture, 0f);

        // The arm is seen from outside only, and each part's is the same mesh at the same
        // depth: one drawn later has to be let through where it is as near.
        GL11.glDisable(GL_CULL_FACE);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_BLEND);
        Program.useOther(sleeve);
        for (int joint = 0; joint < 3; joint++) {
            GL20.glUniform3f(sleeveJoint[joint], joints[joint * 3], joints[joint * 3 + 1], joints[joint * 3 + 2]);
        }
        GL20.glUniform3f(sleeveBend, bend[0], bend[1], bend[2]);
        GL20.glUniform3f(sleeveCuff, cuff[0], cuff[1], cuff[2]);
        GL20.glUniform4f(sleeveWide, wide[0], wide[1], wide[2], wide[3]);
        GL20.glUniformMatrix4fv(sleeveProjection, false, projection);
        GL20.glUniform1f(sleeveAlpha, person.alpha);
        GL20.glUniform1f(sleeveHue, part.hue);
        GL20.glUniform3f(sleeveTint, instance.tintR, instance.tintG, instance.tintB);
        GL13.glActiveTexture(GL_TEXTURE0);
        Texture.lastTextureID = -1;
        worn.bind();
        worn.getUVScale(uvScale);
        GL20.glUniform2f(sleeveHas, limb.foreUv != null ? 1f : 0f, limb.upperUv != null ? 1f : 0f);
        for (int sample = 0; sample < Limb.SAMPLES; sample++) {
            if (limb.foreUv != null) {
                GL20.glUniform2f(sleeveForeUv[sample], limb.foreUv[sample * 2] * uvScale.x, limb.foreUv[sample * 2 + 1] * uvScale.y);
            }
            if (limb.upperUv != null) {
                GL20.glUniform2f(sleeveUpperUv[sample], limb.upperUv[sample * 2] * uvScale.x, limb.upperUv[sample * 2 + 1] * uvScale.y);
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
        if (model == null || model.mesh == null || (model.isStatic ? part.xfrm == null : part.matrixPalette == null)) {
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
        if (shadow && fromEye > SHADOW_WHOLE_WITHIN && !Quality.wholeFigureShadows() && wearOf(copy, model, texture) != BODY) {
            return true;
        }
        // Making a finer skin runs the game's combiner, so it is asked for before any of
        // this part's state is set. A shadow has no skin at all.
        Texture worn = finelySkinned ? FineSkins.of(texture, fromEye) : texture;

        if (!drawing) {
            GL11.glEnable(GL_DEPTH_TEST);
            GL11.glDepthFunc(GL_LESS);
            GL11.glDepthMask(true);
            GL11.glDisable(GL_BLEND);
            drawing = true;
            culled = UNKNOWN;
            programInUse = 0;
        }
        ModelScript script = instance.modelScript;
        // The matrix that stands a model in the world mirrors it, which turns every triangle's front to its back.
        int face = script != null && script.cullFace != -1 ? script.cullFace : GL_FRONT;
        int cull = script != null && script.cullFace == 0 ? 0 : mirrored ? face == GL_FRONT ? GL_BACK : GL_FRONT : face;
        // Near enough for the cut round the eye to open it, a person is drawn from both sides, and what was culled is its inside.
        float inside = 0f;
        if (cull != 0 && nearCut > 0f && !shadow && away < INSIDE_WITHIN) {
            inside = cull == GL_FRONT ? 1f : -1f;
            cull = 0;
        }
        if (cull != culled) {
            if (cull == 0) {
                GL11.glDisable(GL_CULL_FACE);
            } else {
                if (culled == 0 || culled == UNKNOWN) {
                    GL11.glEnable(GL_CULL_FACE);
                }
                GL11.glCullFace(cull);
            }
            culled = cull;
        }

        Skin skin = cutBones != null ? cut : shadow ? depth : divided != null && !plainly && away < WHOLE_BEYOND + PERSON_REACHES ? divided : whole;
        if (skin.id != programInUse) {
            Program.useOther(skin.id);
            programInUse = skin.id;
        }
        boolean unlit = bare && !shadow;
        int wear = WEAVE;
        if (unlit) {
            wear = wearOf(copy, model, texture);
        }
        if (skin != depth) {
            GL20.glUniform1f(skin.inside, inside);
            dress(skin.id, wear, copy.lay);
        }
        if (cutBones != null) {
            GL20.glUniform1fv(cutArm, cutBones);
            GL20.glUniform1f(cutKeep, cutKeepsThem ? 1f : 0f);
        }
        // The game keeps each bone's matrix with its rows where OpenGL wants columns. A part the
        // game hangs on the body whole, a hat or a pair of glasses, has one: where the game
        // places it among the body's bones (xfrm), which its copy's corners are all carried by.
        boolean eyed = fromTheEye && !shadow && model.tag instanceof SkinningData;
        FloatBuffer bones = model.isStatic ? hungAt(part) : eyed ? headAtTheEye((SkinningData) model.tag, part.matrixPalette, modelView, person.finalScale) : part.matrixPalette;
        GL20.glUniformMatrix4fv(skin.palette, true, bones);
        if (skin.heldFor != peopleBegun) {
            GL20.glUniformMatrix4fv(skin.modelView, false, modelView);
            GL20.glUniformMatrix4fv(skin.projection, false, projection);
            GL20.glUniform1f(skin.scale, person.finalScale);
            GL20.glUniform1f(skin.bare, unlit ? 1f : 0f);
            GL20.glUniform1f(skin.shadow, shadow ? 1f : 0f);
            GL20.glUniform1f(skin.nearCut, shadow ? 0f : nearCut);
            GL20.glUniform1f(skin.alpha, person.alpha);
            GL20.glUniform1f(skin.mouth, mouthOpen);
            GL20.glUniform1f(skin.lips, mouthLips);
            // The game's lights are read by a lit draw alone: a bare one is lit by the picture, and a shadow map keeps no colour.
            if (!bare && !shadow) {
                for (int light = 0; light < LIGHTS; light++) {
                    int at = light * 3;
                    GL20.glUniform3f(skin.lightWay[light], lightWay[at], lightWay[at + 1], lightWay[at + 2]);
                    GL20.glUniform3f(skin.lightColor[light], lightColor[at], lightColor[at + 1], lightColor[at + 2]);
                }
            }
            skin.heldFor = peopleBegun;
        }
        if (unlit != bothBuffers) {
            GL20.glDrawBuffers(unlit ? BOTH : FIRST);
            bothBuffers = unlit;
        }
        if (skin != depth) {
            GL20.glUniform1f(skin.hue, part.hue);
            GL20.glUniform3f(skin.tint, instance.tintR, instance.tintG, instance.tintB);
            // The lips part on the body alone: whatever else is worn over the mouth moves with the jaw and keeps its own colour.
            // Spread or rounded lips part in the middle however little the jaw drops (lipped in POSED), and the gap is as dark as an open mouth's.
            float parted = Math.max(mouthOpen, Math.abs(mouthLips) / 3f);
            GL20.glUniform1f(skin.mouthGap, parted > 0f && wearOf(copy, model, texture) == BODY ? parted : 0f);
        }
        GL20.glUniform1f(skin.headBone, !model.isStatic && (mouthOpen > 0f || mouthLips != 0f) ? headBone(model) : -1f);
        if (!bare && !shadow) {
            if (part.ignoreLighting) {
                GL20.glUniform3f(skin.ambient, 1f, 1f, 1f);
            } else {
                GL20.glUniform3f(skin.ambient, person.ambientR * AMBIENT_SHARE, person.ambientG * AMBIENT_SHARE, person.ambientB * AMBIENT_SHARE);
            }
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
        if (skin != depth) {
            GL20.glUniform2f(skin.texSize, worn.getWidth() / uvScale.x, worn.getHeight() / uvScale.y);
        }

        if (skin == divided) {
            GL20.glUniform2f(skin.fine, FINE_WITHIN, WHOLE_BEYOND);
            GL40.glPatchParameteri(GL_PATCH_VERTICES, 3);
        }
        // Hair is drawn again in a few layers stood off it, each thinner than the last: its
        // outline is then strands and not the edge of a helmet. The viewer's own is not seen.
        int layers = unlit && !fromTheEye && !plainly && cutBones == null ? fringe(wear, away) : 0;
        // Bound for the draw alone: the game's combiner, run for the next part's skin, pushes and
        // pops client state, and what it pops goes into whichever vertex array is bound.
        GL30.glBindVertexArray(copy.array);
        try {
            for (int layer = 0; layer <= layers; layer++) {
                if (skin != depth) {
                    shell(skin.id, layer, layers, person.finalScale);
                }
                GL11.glDrawElements(skin == divided ? GL_PATCHES : GL_TRIANGLES, copy.count, GL_UNSIGNED_INT, 0L);
            }
        } finally {
            GL30.glBindVertexArray(0);
        }
        return true;
    }

    private static final float[] hung = new float[16];
    private static final FloatBuffer HUNG = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();

    /** The one matrix of a part the game hangs on the body whole, by rows as the game keeps a bone's. */
    private static FloatBuffer hungAt(ModelInstanceRenderData part) {
        part.xfrm.get(hung);
        HUNG.clear();
        HUNG.put(hung).flip();
        return HUNG;
    }

    /**
     * Whether parts are being drawn one after another (part): what every part sets alike,
     * the depth test and no blending, is then set already, and the culling, the
     * program and the draw buffers are as the last part left them. peopleBegun counts the people
     * begun, for what a program is told once a person (Skin.heldFor).
     */
    private static boolean drawing, bothBuffers;
    private static int culled, programInUse, peopleBegun;
    private static final int UNKNOWN = -1;

    /**
     * Before anything else draws, and at the end of a pass over the people: the buffers are let
     * go and the second draw buffer closed. What the programs were told is told again, since
     * whatever drew in between may have used them.
     */
    static void release() {
        peopleBegun++;
        if (!drawing) {
            return;
        }
        drawing = false;
        // On the default vertex array: one the game left bound is not the picture's to change.
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        if (bothBuffers) {
            GL20.glDrawBuffers(FIRST);
            bothBuffers = false;
        }
    }

    /** What a part of this model in this texture is made of, as wearOf tells it, kept with the model's copy. */
    private static int wearOf(Copy copy, Model model, Texture texture) {
        String wornName = texture.getName();
        Integer known = copy.wears.get(wornName);
        if (known == null) {
            known = wearOf(model.name, wornName, texture.getClass().getSimpleName().equals("CharacterSmartTexture"));
            copy.wears.put(wornName, known);
            if (TRACE) {
                Log.info("the model " + model.name + " in " + wornName + " is drawn as "
                        + new String[] {"a body", "hair", "woven cloth", "twill", "knit", "leather", "something sleek"}[known]);
            }
        }
        return known;
    }

    /** How far off, in tiles, a person throws the shadow of all they wear and carry and not of their body alone: see Quality.wholeFigureShadows. */
    static final float SHADOW_WHOLE_WITHIN = 4f;

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
        int cast = program("character's shadow", problem, GL_VERTEX_SHADER, OLD + DEPTH_VERTEX, GL_FRAGMENT_SHADER, OLD + DEPTH_FRAGMENT);
        boolean divides = Integer.parseInt(numbers.group(1)) >= 4;
        int bowed = divides ? program("rounded character", problem, GL_VERTEX_SHADER, NEW + DIVIDED_VERTEX, GL_TESS_CONTROL_SHADER, DIVIDE,
                GL_TESS_EVALUATION_SHADER, BOW, GL_FRAGMENT_SHADER, NEW + FRAGMENT) : 0;
        if (problem.length() > 0) {
            refuse(problem.toString());
            return;
        }
        uvScale = new Vector2();
        whole = new Skin(plain);
        depth = new Skin(cast);
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
            Program.useOther(arm);
            GL20.glUniform1i(GL20.glGetUniformLocation(arm, "uTexture"), 0);
            sleeveBend = GL20.glGetUniformLocation(arm, "uBend");
            sleeveCuff = GL20.glGetUniformLocation(arm, "uCuff");
            sleeveWide = GL20.glGetUniformLocation(arm, "uWide");
            sleeveProjection = GL20.glGetUniformLocation(arm, "uProjection");
            sleeveAlpha = GL20.glGetUniformLocation(arm, "uAlpha");
            sleeveHue = GL20.glGetUniformLocation(arm, "uHue");
            sleeveTint = GL20.glGetUniformLocation(arm, "uTint");
            sleeveHas = GL20.glGetUniformLocation(arm, "uHas");
            for (int joint = 0; joint < sleeveJoint.length; joint++) {
                sleeveJoint[joint] = GL20.glGetUniformLocation(arm, "uJoint[" + joint + "]");
            }
            for (int sample = 0; sample < Limb.SAMPLES; sample++) {
                sleeveForeUv[sample] = GL20.glGetUniformLocation(arm, "uForeUv[" + sample + "]");
                sleeveUpperUv[sample] = GL20.glGetUniformLocation(arm, "uUpperUv[" + sample + "]");
            }
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

    /**
     * Gives the copies of the game's meshes back to the graphics card, and lets go of what was
     * read of the game's skeletons, which it loads anew between saves. Each is made again the
     * next time it is drawn, as it was the first time. Render thread.
     *
     * @return how many copies there were
     */
    static int letGo() {
        int had = copies.size();
        for (Copy copy : copies.values()) {
            if (copy.count != 0) {
                GL30.glDeleteVertexArrays(copy.array);
                GL15.glDeleteBuffers(copy.vertices);
                GL15.glDeleteBuffers(copy.indices);
            }
        }
        copies.clear();
        arms.clear();
        heads.clear();
        return had;
    }

    /** A mesh's vertices, three floats each, its triangles, three vertices each, and the buffer they were read from. */
    record Shape(float[] places, int[] corners, VertexBufferObject from) {}

    /**
     * The places and triangles of a model's mesh as the graphics card has them, read back once
     * for whoever asks: a weapon's, to find where hands go on it. Render thread. Null while the
     * game has not loaded the mesh, with nothing said, and null with `why` saying so for a mesh
     * whose buffer is not laid out as the game's own are. The triangles are read as the copy
     * the picture draws people from reads them.
     */
    static Shape shapeOf(Model model, StringBuilder why) {
        ModelMesh mesh = model.mesh;
        VertexBufferObject buffer = mesh == null ? null : mesh.vb;
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
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the game's vertex buffer cannot be read: " + e, e);
        }
        int place = -1;
        for (int element = 0; format != null && element < format.getNumElements(); element++) {
            VertexBufferObject.VertexElement at = format.getElement(element);
            if (at.type == VertexBufferObject.VertexType.VertexArray && place < 0) {
                place = at.byteOffset;
            }
        }
        if (handle == null) {
            return null;
        }
        int count = handle.numElements, stride = handle.vertexStride;
        if (handle.faceDataOnly || place < 0 || stride <= 0 || elements == null || elements.length < count || count % 3 != 0 || count == 0) {
            why.append("its vertex buffer holds no places and triangles the way the game's own do");
            return null;
        }
        int bound = GL11.glGetInteger(0x8894);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, handle.vboId);
        int bytes = GL15.glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE);
        ByteBuffer raw = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        GL15.glGetBufferSubData(GL_ARRAY_BUFFER, 0L, raw);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, bound);
        int vertices = bytes / stride;
        float[] places = new float[vertices * 3];
        for (int vertex = 0; vertex < vertices; vertex++) {
            for (int axis = 0; axis < 3; axis++) {
                places[vertex * 3 + axis] = raw.getFloat(vertex * stride + place + axis * 4);
                if (!Float.isFinite(places[vertex * 3 + axis])) {
                    why.append("vertex ").append(vertex).append(" has a place that is not a number");
                    return null;
                }
            }
        }
        int[] corners = Arrays.copyOf(elements, count);
        for (int corner : corners) {
            if (corner < 0 || corner >= vertices) {
                why.append("a triangle names vertex ").append(corner).append(" of ").append(vertices);
                return null;
            }
        }
        return new Shape(places, corners, buffer);
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
            GL30.glDeleteVertexArrays(copy.array);
            GL15.glDeleteBuffers(copy.vertices);
            GL15.glDeleteBuffers(copy.indices);
        }
        copy = read(buffer, mesh.modificationCount, handle, elements, format, model.name, model.tag instanceof SkinningData skeleton ? skeleton : null, model.isStatic);
        copies.put(mesh, copy);
        return copy;
    }

    private static Copy read(VertexBufferObject buffer, int modification, VertexBufferObject.Vbo handle, int[] elements,
            VertexBufferObject.VertexFormat format, String name, SkinningData skeleton, boolean whole) {
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
                : from[PLACE] < 0 ? "it has no places" : from[NORMAL] < 0 ? "it has no normals" : !whole && from[WEIGHTS] < 0 ? "it has no bone weights"
                : !whole && from[BONE_NUMBERS] < 0 ? "it has no bone numbers" : from[UV] < 0 ? "it has no texture places"
                : elements == null || elements.length < count || count % 3 != 0 ? "its triangles are not kept as a list of " + count + " corners"
                : count == 0 ? "it has no triangles" : null;
        if (lacks != null) {
            Log.info("the model " + name + " is left to the game's own draw: " + lacks);
            return new Copy(buffer, modification, 0, 0, 0, 0, null, null, null);
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
                return new Copy(buffer, modification, 0, 0, 0, 0, null, null, null);
            }
        }

        float[] places = new float[vertices * 3], normals = new float[vertices * 3], uvs = new float[vertices * 2];
        for (int vertex = 0; vertex < vertices; vertex++) {
            for (int axis = 0; axis < 3; axis++) {
                places[vertex * 3 + axis] = raw.getFloat(vertex * stride + from[PLACE] + axis * 4);
                normals[vertex * 3 + axis] = raw.getFloat(vertex * stride + from[NORMAL] + axis * 4);
            }
            uvs[vertex * 2] = raw.getFloat(vertex * stride + from[UV]);
            uvs[vertex * 2 + 1] = raw.getFloat(vertex * stride + from[UV] + 4);
            // A model is the game's or a mod's to supply. With a place that is not a number the
            // nearest of an arm's triangles to their middle is none of them.
            if (!Float.isFinite(places[vertex * 3]) || !Float.isFinite(places[vertex * 3 + 1]) || !Float.isFinite(places[vertex * 3 + 2])) {
                GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
                Log.info("the model " + name + " is left to the game's own draw: vertex " + vertex + " has a place that is not a number");
                return new Copy(buffer, modification, 0, 0, 0, 0, null, null, null);
            }
        }
        int[] one = firstAt(places);
        float[] smooth = smoothNormals(places, normals, elements, count, one);
        float[] along = along(places, uvs, elements, count, one);
        Limb[] limbs = {new Limb(), new Limb()};
        int[] handBone = {-1, -1};
        if (skeleton != null) {
            int[] boneKinds = kinds(skeleton);
            int[] kinds = new int[vertices];
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
            }
            for (int side = 0; side < 2; side++) {
                limbs[side] = Limb.read(places, uvs, kinds, elements, count, side);
                Integer bone = skeleton.boneIndices.get(HAND_BONES[side][0]);
                handBone[side] = bone != null && bone >= 0 && bone < BONES ? bone : -1;
            }
        }
        FloatBuffer made = ByteBuffer.allocateDirect(vertices * STRIDE).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int vertex = 0; vertex < vertices; vertex++) {
            for (int attribute = 0; attribute < SMOOTH; attribute++) {
                for (int part = 0; part < SIZE[attribute]; part++) {
                    // A model hung whole is carried by one matrix, given as bone 0.
                    made.put(whole && attribute == WEIGHTS ? (part == 0 ? 1f : 0f) : whole && attribute == BONE_NUMBERS ? 0f
                            : raw.getFloat(vertex * stride + from[attribute] + part * 4));
                }
            }
            made.put(smooth, vertex * 3, 3);
            made.put(along, vertex * 4, 4);
        }
        made.flip();
        int bound = GL11.glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int array = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(array);
        int ours = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ARRAY_BUFFER, ours);
        GL15.glBufferData(GL_ARRAY_BUFFER, made, GL_STATIC_DRAW);
        for (int attribute = 0; attribute < ATTRIBUTES; attribute++) {
            GL20.glVertexAttribPointer(attribute, SIZE[attribute], GL_FLOAT, false, STRIDE, OFFSET[attribute]);
            GL20.glEnableVertexAttribArray(attribute);
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        int indices = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indices);
        GL15.glBufferData(GL_ELEMENT_ARRAY_BUFFER, elements.length == count ? elements : Arrays.copyOf(elements, count), GL_STATIC_DRAW);
        GL30.glBindVertexArray(bound);
        return new Copy(buffer, modification, ours, indices, array, count, limbs, handBone, laid(places, uvs, elements, count));
    }

    /** Which vertex is the first at each place a mesh has a vertex: a mesh holds a corner several times over where its texture has a seam there. */
    private static int[] firstAt(float[] places) {
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
        return one;
    }

    /**
     * For each vertex, four numbers. The first three are the way over the surface that the
     * texture runs downwards there, of length one: the game's hair is painted combed down its
     * textures, so on hair it is the way the strands run. Every copy of a corner gets the one
     * answer, the triangles round it added up by their area; a vertex whose triangles say
     * nothing of it gets nought. The fourth is one for a vertex on an open edge of the mesh,
     * where a triangle's side is no other triangle's, and nought for the rest.
     */
    static float[] along(float[] places, float[] uvs, int[] elements, int count) {
        return along(places, uvs, elements, count, firstAt(places));
    }

    /** @param one firstAt of the places */
    private static float[] along(float[] places, float[] uvs, int[] elements, int count, int[] one) {
        int vertices = places.length / 3;
        float[] sum = new float[vertices * 3];
        HashMap<Long, Integer> sides = new HashMap<>();
        for (int corner = 0; corner + 2 < count; corner += 3) {
            int a = elements[corner], b = elements[corner + 1], c = elements[corner + 2];
            for (int side = 0; side < 3; side++) {
                int from = one[elements[corner + side]], to = one[elements[corner + (side + 1) % 3]];
                if (from != to) {
                    sides.merge((long) Math.min(from, to) << 32 | Math.max(from, to), 1, Integer::sum);
                }
            }
            float ux = places[b * 3] - places[a * 3], uy = places[b * 3 + 1] - places[a * 3 + 1], uz = places[b * 3 + 2] - places[a * 3 + 2];
            float vx = places[c * 3] - places[a * 3], vy = places[c * 3 + 1] - places[a * 3 + 1], vz = places[c * 3 + 2] - places[a * 3 + 2];
            float s1 = uvs[b * 2] - uvs[a * 2], t1 = uvs[b * 2 + 1] - uvs[a * 2 + 1], s2 = uvs[c * 2] - uvs[a * 2], t2 = uvs[c * 2 + 1] - uvs[a * 2 + 1];
            float turned = s1 * t2 - s2 * t1;
            if (turned == 0f) {
                continue;
            }
            // The way the place moves as the texture is gone down, across staying put.
            float x = (vx * s1 - ux * s2) / turned, y = (vy * s1 - uy * s2) / turned, z = (vz * s1 - uz * s2) / turned;
            float length = (float) Math.sqrt(x * x + y * y + z * z);
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float area = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (!(length > 0f) || !Float.isFinite(length)) {
                continue;
            }
            for (int of = 0; of < 3; of++) {
                int to = one[elements[corner + of]] * 3;
                sum[to] += x / length * area;
                sum[to + 1] += y / length * area;
                sum[to + 2] += z / length * area;
            }
        }
        boolean[] open = new boolean[vertices];
        for (var side : sides.entrySet()) {
            if (side.getValue() == 1) {
                open[(int) (side.getKey() >>> 32)] = true;
                open[(int) (long) side.getKey()] = true;
            }
        }
        float[] along = new float[vertices * 4];
        for (int vertex = 0; vertex < vertices; vertex++) {
            int of = one[vertex] * 3;
            float length = (float) Math.sqrt(sum[of] * sum[of] + sum[of + 1] * sum[of + 1] + sum[of + 2] * sum[of + 2]);
            if (length > 0f) {
                along[vertex * 4] = sum[of] / length;
                along[vertex * 4 + 1] = sum[of + 1] / length;
                along[vertex * 4 + 2] = sum[of + 2] / length;
            }
            along[vertex * 4 + 3] = open[one[vertex]] ? 1f : 0f;
        }
        return along;
    }

    /**
     * How far over a mesh's surface one whole width of its texture goes, and one whole height,
     * in the mesh's own measure: the mean over its triangles, by their area. With these a
     * pattern laid in the texture's own lines is as wide on one mesh as on another. Nought and
     * nought for a mesh whose texture is laid nowhere.
     */
    static float[] laid(float[] places, float[] uvs, int[] elements, int count) {
        double across = 0, down = 0, whole = 0;
        for (int corner = 0; corner + 2 < count; corner += 3) {
            int a = elements[corner], b = elements[corner + 1], c = elements[corner + 2];
            float ux = places[b * 3] - places[a * 3], uy = places[b * 3 + 1] - places[a * 3 + 1], uz = places[b * 3 + 2] - places[a * 3 + 2];
            float vx = places[c * 3] - places[a * 3], vy = places[c * 3 + 1] - places[a * 3 + 1], vz = places[c * 3 + 2] - places[a * 3 + 2];
            float s1 = uvs[b * 2] - uvs[a * 2], t1 = uvs[b * 2 + 1] - uvs[a * 2 + 1], s2 = uvs[c * 2] - uvs[a * 2], t2 = uvs[c * 2 + 1] - uvs[a * 2 + 1];
            float turned = s1 * t2 - s2 * t1;
            if (turned == 0f) {
                continue;
            }
            double sx = (ux * t2 - vx * t1) / turned, sy = (uy * t2 - vy * t1) / turned, sz = (uz * t2 - vz * t1) / turned;
            double tx = (vx * s1 - ux * s2) / turned, ty = (vy * s1 - uy * s2) / turned, tz = (vz * s1 - uz * s2) / turned;
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            double area = Math.sqrt(nx * nx + ny * ny + nz * nz);
            double wide = Math.sqrt(sx * sx + sy * sy + sz * sz), high = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (!Double.isFinite(wide) || !Double.isFinite(high)) {
                continue;
            }
            across += wide * area;
            down += high * area;
            whole += area;
        }
        return whole > 0 ? new float[] {(float) (across / whole), (float) (down / whole)} : new float[2];
    }

    /**
     * Which way the surface faces at each vertex once the faces round it are run together. A
     * mesh holds a corner several times over where its texture has a seam there, each copy
     * with its own normal; here every copy at one place gets the one answer, the faces round
     * that place added up by their area. A vertex no triangle uses keeps its own normal.
     */
    static float[] smoothNormals(float[] places, float[] normals, int[] elements, int count) {
        return smoothNormals(places, normals, elements, count, firstAt(places));
    }

    /** @param one firstAt of the places */
    private static float[] smoothNormals(float[] places, float[] normals, int[] elements, int count, int[] one) {
        int vertices = places.length / 3;
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
        float cos = (float) Math.cos(-angle), sin = (float) Math.sin(-angle);
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

package com.cameraunlock.zomboid;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * The mod's own model of the hunting rifle, drawn in the viewer's hands in place of the
 * game's, which is made to be seen from a rooftop: a mesh, its colour and the shape and
 * finish of its surface, made by tools/models/rifle.py and kept in the jar, in the layout
 * the shotgun's files have (OwnWeapon).
 *
 * It is held exactly where the game's own would be: the grip in the right hand and the
 * muzzle where the game's muzzle is, so that the hands, the aim, the flash and a scope are
 * as they were. After a shot its bolt is worked: the handle turned up, the bolt drawn back,
 * the fired case thrown out of the port, and the bolt shut again.
 *
 * It is drawn into the depth of what the mod draws in the viewer's hands (HeldDepth), so
 * that its parts near the eye sort against one another and against a scope on it.
 *
 * All of it is the render thread's.
 */
final class OwnRifle {
    final View view;

    OwnRifle(View view) {
        this.view = view;
    }


    /** The one item of the game's that the model stands for. */
    private static final String ITEM = "Base.HuntingRifle";

    /** The model, read out of the jar and on the graphics card. */
    static final OwnModel MODEL = new OwnModel("the rifle's model", "rifle");
    /** The parts the file's layout names. The rifle has nothing in the second, which is a pump's, or the last, which is a stock left out. */
    private static final int FIXED = 0, BOLT = 2, CASE = 3;

    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_UNSIGNED_INT = 0x1405, GL_TRIANGLES = 0x0004;
    private static final int GL_FRAMEBUFFER = 0x8D40, GL_TEXTURE0 = 0x84C0, GL_DEPTH_TEST = 0x0B71, GL_CULL_FACE = 0x0B44, GL_BLEND = 0x0BE2, GL_LEQUAL = 0x0203;
    /** Units nothing else in the picture keeps a texture on between passes: the shotgun's, which is never in the hands with the rifle. */
    private static final int COLOUR_UNIT = 30, SURFACE_UNIT = 31, HANDS_UNIT = 3;

    private static final String VERTEX = """
            #version 120
            attribute vec3 aPlace;
            attribute vec3 aNormal;
            attribute vec3 aTangent;
            attribute float aWay;
            attribute vec2 aUv;
            // From the model's axes to the view's: x to the right, y up, z towards the viewer.
            uniform mat4 uModel;
            uniform mat4 uProjection;
            // The bolt: the cosine and sine of how far its handle is turned up about the bore,
            // how far it is drawn back along it, and how high the bore is over the model's origin.
            uniform vec2 uTurn;
            uniform float uSlide;
            uniform float uBore;
            // -1 where uModel is a mirror, which turns a surface's own axes inside out.
            uniform float uHanded;
            uniform vec3 uRight;
            uniform vec3 uUp;
            uniform vec3 uForward;
            varying vec3 vSeen;
            varying vec3 vNormal;
            varying vec3 vTangent;
            varying vec3 vOther;
            varying vec2 vUv;

            vec3 inWorld(vec3 v) {
                return uRight * v.x + uUp * v.y - uForward * v.z;
            }

            // Turned about a line along the barrel that is this high over the origin.
            vec3 turned(vec3 v, float high) {
                float over = v.z - high;
                return vec3(v.x * uTurn.x - over * uTurn.y, v.y, high + v.x * uTurn.y + over * uTurn.x);
            }

            void main() {
                vec3 place = turned(aPlace, uBore);
                vec4 seen = uModel * vec4(place.x, place.y - uSlide, place.z, 1.0);
                vec3 n = normalize(mat3(uModel) * turned(aNormal, 0.0));
                vec3 t = normalize(mat3(uModel) * turned(aTangent, 0.0));
                vSeen = seen.xyz;
                vNormal = inWorld(n);
                vTangent = inWorld(t);
                vOther = inWorld(cross(n, t)) * (aWay * uHanded);
                vUv = aUv;
                gl_Position = uProjection * seen;
            }
            """;

    private static final String FRAGMENT = "#version 120\n" + Sky.ATMOSPHERE + MuzzleFlash.GLSL + """
            uniform sampler2D uColour;
            uniform sampler2D uSurface;
            uniform sampler2DShadow uShadowNear;
            uniform sampler2DShadow uShadowFar;
            uniform mat4 uNearMatrix;
            uniform mat4 uFarMatrix;
            uniform vec3 uEye;
            uniform vec3 uRight;
            uniform vec3 uUp;
            uniform vec3 uForward;
            uniform vec3 uLight;
            uniform vec3 uLightColor;
            // The game's light where the viewer stands, and whether that is out of doors.
            uniform float uStood;
            uniform float uOutdoors;
            // The depth is written in the measure of the target drawn into, whose near plane this is.
            uniform float uWriteNear;
            uniform float uFar;
            // The depth the viewer's hands were drawn with, the size of the picture and the near
            // plane of that depth, and whether what they cover is left out: it is, of the
            // rifle's colour, and is not of its depth, which is tested against the picture's own.
            uniform sampler2D uHands;
            uniform vec2 uSize;
            uniform float uHandsNear;
            uniform float uBehindHands;
            // Set for the pass that keeps only where the rifle is, and none of its colour.
            uniform float uDepthOnly;
            // Tiles to a metre at the size the rifle is drawn.
            uniform float uMetre;
            varying vec3 vSeen;
            varying vec3 vNormal;
            varying vec3 vTangent;
            varying vec3 vOther;
            varying vec2 vUv;

            const float PI = 3.14159265;

            bool inside(vec3 c) {
                return c.x > 0.01 && c.x < 0.99 && c.y > 0.01 && c.y < 0.99 && c.z < 1.0;
            }

            // The light that comes from all round, from the way d: the sky and the ground under
            // it out of doors, and within a room the ceiling's light over the floor's.
            vec3 around(vec3 d, float skyGives) {
                vec3 open = atmosphere(vec3(d.xy, max(d.z, 0.05)));
                vec3 ground = vec3(0.24, 0.26, 0.16) * (skyGives * (0.35 + 0.9 * max(uLight.z, 0.0)));
                vec3 out3 = mix(ground, open, smoothstep(-0.05, 0.30, d.z));
                vec3 room = vec3(1.0, 0.97, 0.92) * (0.16 + 0.84 * smoothstep(-0.5, 0.9, d.z));
                return mix(room, out3 * 2.0, uOutdoors) * pow(uStood, 2.2);
            }

            // How much of the light from the way d the viewer's own head keeps off a point of
            // the rifle: with the sights up the head is over the comb and fills the sky that
            // way, so what faces the eye from close by is in its shade and mirrors only it.
            float headHides(vec3 d, vec3 view) {
                float close = 1.0 - smoothstep(0.10, 0.28, length(vSeen) / uMetre);
                return close * smoothstep(0.05, 0.7, dot(d, view));
            }

            void main() {
                float ahead = -vSeen.z;
                if (uBehindHands > 0.5) {
                    float z = texture2D(uHands, gl_FragCoord.xy / uSize).r;
                    if (z < 1.0 && 2.0 * uHandsNear * uFar / (uFar + uHandsNear - (z * 2.0 - 1.0) * (uFar - uHandsNear)) < ahead) {
                        discard;
                    }
                }
                if (uDepthOnly > 0.5) {
                    gl_FragDepth = clamp(0.5 + 0.5 * (uFar + uWriteNear - 2.0 * uWriteNear * uFar / ahead) / (uFar - uWriteNear), 0.0, 1.0);
                    return;
                }
                vec4 tint = texture2D(uColour, vUv);
                vec4 surface = texture2D(uSurface, vUv);
                vec2 bend = surface.xy * 2.0 - 1.0;
                vec3 n = normalize(vTangent * bend.x + vOther * bend.y + normalize(vNormal) * sqrt(max(1.0 - dot(bend, bend), 0.0)));
                vec3 at = uEye + uRight * vSeen.x + uUp * vSeen.y - uForward * vSeen.z;
                vec3 view = normalize(uEye - at);
                // A surface that the bend of its normal turns away from the eye is seen edge on.
                float nv = max(dot(n, view), 0.02);
                float rough = clamp(surface.b, 0.07, 1.0);
                float metal = surface.a;
                float reached = tint.a;
                vec3 dull = tint.rgb * (1.0 - metal);
                vec3 mirror = mix(vec3(0.04), tint.rgb, metal);

                vec4 from = vec4(at + uLight * 0.12, 1.0);
                vec3 c = (uNearMatrix * from).xyz * 0.5 + 0.5;
                float sun = 1.0;
                if (inside(c)) {
                    float e = 2.0 / 4096.0;
                    sun = 0.2 * (shadow2D(uShadowNear, c).r + shadow2D(uShadowNear, c + vec3(e, e, 0.0)).r + shadow2D(uShadowNear, c + vec3(-e, e, 0.0)).r
                            + shadow2D(uShadowNear, c + vec3(e, -e, 0.0)).r + shadow2D(uShadowNear, c + vec3(-e, -e, 0.0)).r);
                } else {
                    c = (uFarMatrix * from).xyz * 0.5 + 0.5;
                    if (inside(c)) {
                        sun = shadow2D(uShadowFar, c).r;
                    }
                }
                vec3 sunLight = pow(uLightColor, vec3(2.2)) * (1.5 * sun);

                // The sun: what a rough surface scatters of it, and its glint in a smooth one.
                float nl = max(dot(n, uLight), 0.0);
                vec3 halfway = normalize(uLight + view);
                float nh = max(dot(n, halfway), 0.0);
                float a = rough * rough;
                float a2 = a * a;
                float spread = nh * nh * (a2 - 1.0) + 1.0;
                float glint = a2 / (PI * spread * spread);
                float seenBoth = 0.5 / (nl * sqrt(nv * nv * (1.0 - a2) + a2) + nv * sqrt(nl * nl * (1.0 - a2) + a2) + 0.0001);
                vec3 thrown = mirror + (1.0 - mirror) * pow(1.0 - clamp(dot(halfway, view), 0.0, 1.0), 5.0);
                vec3 color = (dull + thrown * min(glint * seenBoth * PI, 60.0)) * sunLight * nl;

                // The light from all round: scattered by the way the surface faces, mirrored
                // by the way it throws the eye's line, the mirror blurred as the surface is rough.
                float skyGives = clamp(dot(atmosphere(vec3(0.0, 0.0, 1.0)), vec3(1.4)) + 0.05, 0.0, 1.0);
                vec3 soft = pow(uStood, 2.2) * mix(vec3(0.66 + 0.34 * n.z + 0.1 * dot(n.xy, vec2(0.6, -0.8))), vec3(0.72, 0.80, 0.95) * (0.72 + 0.28 * n.z), uOutdoors);
                soft += pow(uLightColor, vec3(2.2)) * vec3(0.9, 0.86, 0.74) * (0.3 * max(uLight.z, 0.0) * (1.0 - max(n.z, 0.0)) * uOutdoors);
                soft *= 1.0 - 0.6 * headHides(n, view);
                vec3 back = reflect(-view, n);
                vec3 shown = mix(around(back, skyGives), 0.5 * (around(normalize(back + n), skyGives) + soft), clamp(rough * 1.5, 0.0, 1.0));
                shown *= 1.0 - 0.9 * headHides(back, view);
                // What a surface this rough throws back of its surroundings at this angle, by the fit Brian
                // Karis published for it (THIRD-PARTY-NOTICES.md, Acknowledgements).
                vec4 fit = rough * vec4(-1.0, -0.0275, -0.572, 0.022) + vec4(1.0, 0.0425, 1.04, -0.04);
                // Oiled wood seen along its length does not turn into a mirror of the sky as a
                // polished thing would: its grain throws most of that light back the way it came.
                float glance = mix(max(nv, 0.30), nv, metal);
                float edge = min(fit.x * fit.x, exp2(-9.28 * glance)) * fit.x + fit.y;
                vec2 share = vec2(-1.04, 1.04) * edge + fit.zw;
                // What stands close round a point keeps the light off it, and out of its mirror more as it is rough.
                float mirrored = mix(1.0, reached, 0.4 + 0.6 * rough);
                color += dull * soft * reached + (mirror * share.x + share.y) * shown * mirrored;
                color += (dull + mirror * 0.6) * flashLight(at, n);

                gl_FragColor = vec4(color, 1.0);
                gl_FragDepth = clamp(0.5 + 0.5 * (uFar + uWriteNear - 2.0 * uWriteNear * uFar / ahead) / (uFar - uWriteNear), 0.0, 1.0);
            }
            """;

    /** Linked the first time the model is made and kept: the model's buffers and pictures are what is given back. */
    private static Program program;
    private final float[] model = new float[16], projection = new float[16];
    private boolean placed;
    private float handed, stood, outdoors;

    /**
     * How far the handle is turned up to open the bolt, as tools/models/rifle.py makes it, and
     * how long each part of working it takes, in seconds: the handle up, the bolt back, a
     * moment there, the bolt forward, the handle down.
     */
    private static final float LIFT = (float) Math.toRadians(55);
    private static final float UP = 0.13f, BACK = 0.17f, HELD_BACK = 0.05f, FORWARD = 0.17f, DOWN = 0.13f;
    /** How long working the bolt takes from the handle's first turn to its last. */
    static final float WORKED_FOR = UP + BACK + HELD_BACK + FORWARD + DOWN;

    private static final float NOT_WORKING = -1f;
    private boolean caseThrown;
    private long workedAsOf;

    /** Whether the model is what is drawn for an item held. */
    static boolean standsFor(String item) {
        return MODEL.onCard && Own.weapons() && ITEM.equals(item);
    }

    /** Reads the model out of the jar and gives it to the graphics card. Null, or what went wrong. */
    static String prepare() {
        if (program == null) {
            StringBuilder problem = new StringBuilder();
            program = Program.link("own rifle", VERTEX, FRAGMENT, problem);
            if (program == null) {
                return problem.toString();
            }
        }
        return MODEL.prepare(program);
    }

    /**
     * Notes where the rifle is this frame, to be drawn once the hands on it have been.
     *
     * @param heldView    ViewModel's matrix for the weapon held up: from the character's axes to the view's
     * @param weapon      where the game has the weapon in its character's axes, read by rows
     * @param finalScale  the scale the game draws the character's parts at
     * @param gameMuzzle  how far the muzzle of the game's model is from its grip, in that model's units
     * @param light       the game's light where the viewer stands
     * @param outside     whether the viewer stands out of doors
     */
    void place(float[] heldView, float[] weapon, float finalScale, float gameMuzzle, float light, boolean outside) {
        handed = MODEL.hold(model, heldView, weapon, finalScale, gameMuzzle);
        stood = light;
        outdoors = outside ? 1f : 0f;
        placed = true;
    }

    /** Whether the rifle was placed this frame and is still to be drawn. */
    boolean waiting() {
        return placed;
    }

    /**
     * Begins the drawing: the program is in use, for the picture to give it the light of the
     * frame under the names its own programs know it by.
     */
    Program begin() {
        program.use();
        return program;
    }

    /** How far through working the bolt a time after it began is: the handle's turn, 0 to 1, and the bolt's draw. */
    static void worked(float seconds, float[] out) {
        out[0] = ViewModelMath.smooth(seconds / UP) - ViewModelMath.smooth((seconds - UP - BACK - HELD_BACK - FORWARD) / DOWN);
        out[1] = ViewModelMath.smooth((seconds - UP) / BACK) - ViewModelMath.smooth((seconds - UP - BACK - HELD_BACK) / FORWARD);
    }

    /**
     * Draws the rifle placed over the picture, into the depth of what the mod draws in the
     * viewer's hands (HeldDepth, made ready for the frame before this), behind whatever of the
     * viewer's hands is nearer than it, and then into the picture's own depth.
     *
     * @param camera     where the frame is drawn from
     * @param scene      the picture's target, wide by high
     * @param handsDepth the depth the viewer's hands were drawn with, whose near plane is near
     * @param sceneNear  the near plane the picture's own depth is measured from
     * @param asideX     the fraction of a dot the world is drawn aside this frame, as a part of
     *                   the picture's width, and asideY of its height
     */
    void draw(ViewCamera.Pose camera, int scene, int wide, int high, int handsDepth, float tanHalfFieldOfView, float aspect, float near, float far,
            float sceneNear, float asideX, float asideY) {
        placed = false;
        long now = System.nanoTime();
        float seconds = Math.min((now - workedAsOf) / 1e9f, 0.05f);
        workedAsOf = now;
        // The bolt is worked a moment after the shot, by the clock the hand that works it goes by:
        // the clock here counts no more than a twentieth of a second a frame, so on long frames it
        // runs slow, and by it the hand would leave an open bolt.
        ViewModel.Held held = view.viewModel.held;
        float working = held == null || held.cycled() < 0f ? NOT_WORKING : held.cycled() - ArmActs.BOLT_AFTER;
        if (working < 0f || working > WORKED_FOR) {
            bolt[0] = 0f;
            bolt[1] = 0f;
        } else {
            worked(working, bolt);
        }
        if (working < 0f) {
            caseThrown = false;
        }
        // Once each time it is worked, as the bolt comes right back, however few frames show it there.
        boolean throwing = !caseThrown && working >= 0f && (bolt[1] >= THROWN_AT || working > UP + BACK);
        caseThrown |= throwing;
        throwCases(camera, seconds, throwing);

        float metre = (float) Math.sqrt(model[4] * model[4] + model[5] * model[5] + model[6] * model[6]);
        ViewCamera.projection(tanHalfFieldOfView, aspect, Scope.NEAREST, far, projection);
        projection[8] += asideX;
        projection[9] += asideY;
        program.set("uProjection", projection);
        program.set("uHanded", handed);
        program.set("uStood", stood);
        program.set("uOutdoors", outdoors);
        program.set("uFar", far);
        program.set("uMetre", metre);
        program.set("uBore", MODEL.muzzle[2]);
        program.set("uColour", COLOUR_UNIT);
        program.set("uSurface", SURFACE_UNIT);
        program.set("uShadowNear", OwnWeapon.SHADOW_NEAR_UNIT);
        program.set("uShadowFar", OwnWeapon.SHADOW_FAR_UNIT);
        program.set("uHands", HANDS_UNIT);
        program.set("uSize", (float) wide, (float) high);
        program.set("uHandsNear", near);
        GL13.glActiveTexture(GL_TEXTURE0 + COLOUR_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, MODEL.colour);
        GL13.glActiveTexture(GL_TEXTURE0 + SURFACE_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, MODEL.surface);
        GL13.glActiveTexture(GL_TEXTURE0 + HANDS_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, handsDepth);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL30.glBindVertexArray(MODEL.array);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(true);
        // Held up, the model is a mirror image of itself as it was made, and its faces wind the other way.
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);

        view.heldDepth.bind();
        program.set("uWriteNear", Scope.NEAREST);
        program.set("uBehindHands", 1f);
        program.set("uDepthOnly", 0f);
        parts();
        // And where it is, for what the picture works out from depth afterwards.
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, scene);
        GL11.glColorMask(false, false, false, false);
        program.set("uWriteNear", sceneNear);
        program.set("uBehindHands", 0f);
        program.set("uDepthOnly", 1f);
        parts();
        GL11.glColorMask(true, true, true, true);

        GL11.glDisable(GL_DEPTH_TEST);
        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);
    }

    private final float[] bolt = new float[2];

    private void parts() {
        program.set("uModel", model);
        program.set("uTurn", 1f, 0f);
        program.set("uSlide", 0f);
        GL11.glDrawElements(GL_TRIANGLES, MODEL.indexCount[FIXED], GL_UNSIGNED_INT, (long) MODEL.firstIndex[FIXED] * Integer.BYTES);
        // The handle is on the model's left, which held up is the viewer's right, and rises as it is turned this way.
        float turn = -LIFT * bolt[0];
        program.set("uTurn", (float) Math.cos(turn), (float) Math.sin(turn));
        program.set("uSlide", bolt[1] * MODEL.travel);
        GL11.glDrawElements(GL_TRIANGLES, MODEL.indexCount[BOLT], GL_UNSIGNED_INT, (long) MODEL.firstIndex[BOLT] * Integer.BYTES);
        program.set("uTurn", 1f, 0f);
        program.set("uSlide", 0f);
        for (int thrown = 0; thrown < CASES; thrown++) {
            if (caseAge[thrown] < CASE_LASTS) {
                program.set("uModel", caseModel[thrown]);
                GL11.glDrawElements(GL_TRIANGLES, MODEL.indexCount[CASE], GL_UNSIGNED_INT, (long) MODEL.firstIndex[CASE] * Integer.BYTES);
            }
        }
    }

    /** As many fired cases as can be in the air at once, and how long one is drawn for, in seconds. */
    private static final int CASES = 2;
    private static final float CASE_LASTS = 0.9f;
    /** The bolt throws the case out once it is this far back, on its way back. */
    private static final float THROWN_AT = 0.85f;
    /**
     * How a case leaves the port, in tiles a second in the view's axes: out to the right, up
     * and a little back. Then how fast it falls, in tiles a second each second, and tumbles, in
     * radians a second.
     */
    private static final float THROWN_RIGHT = 1.3f, THROWN_UP = 0.9f, THROWN_BACK = 0.3f, FALLS = 7.5f, TUMBLES = 21f;
    private final float[] caseAge = {CASE_LASTS, CASE_LASTS};
    /** Each case in the world: where it is, how it moves, and the rifle's own axes as they were when it left. */
    private final float[][] caseAt = new float[CASES][3], caseGoes = new float[CASES][3], caseAxes = new float[CASES][9];
    private final float[][] caseModel = new float[CASES][16];
    private final float[] seenAt = new float[3], tumbled = new float[3];
    private int nextCase;

    /** Throws a case as the bolt comes back, moves those in the air, and works out where the eye sees each. */
    private void throwCases(ViewCamera.Pose camera, float seconds, boolean thrown) {
        if (thrown) {
            int flying = nextCase;
            nextCase = (nextCase + 1) % CASES;
            caseAge[flying] = 0f;
            ViewModelMath.place(model, MODEL.port[0], MODEL.port[1], MODEL.port[2], seenAt);
            OwnWeapon.inWorld(camera, seenAt[0], seenAt[1], seenAt[2], caseAt[flying]);
            caseAt[flying][0] += camera.eyeX;
            caseAt[flying][1] += camera.eyeY;
            caseAt[flying][2] += camera.eyeUp;
            OwnWeapon.inWorld(camera, THROWN_RIGHT, THROWN_UP, THROWN_BACK, caseGoes[flying]);
            for (int column = 0; column < 3; column++) {
                OwnWeapon.inWorld(camera, model[column * 4], model[column * 4 + 1], model[column * 4 + 2], seenAt);
                System.arraycopy(seenAt, 0, caseAxes[flying], column * 3, 3);
            }
        }
        for (int flying = 0; flying < CASES; flying++) {
            if (caseAge[flying] >= CASE_LASTS) {
                continue;
            }
            caseAge[flying] += seconds;
            float[] at = caseAt[flying], goes = caseGoes[flying], axes = caseAxes[flying], seen = caseModel[flying];
            goes[2] -= FALLS * seconds;
            for (int axis = 0; axis < 3; axis++) {
                at[axis] += goes[axis] * seconds;
            }
            // It tumbles end over end about the rifle's upright as it was when the case left.
            float turn = TUMBLES * caseAge[flying];
            float c = (float) Math.cos(turn), s = (float) Math.sin(turn);
            for (int axis = 0; axis < 3; axis++) {
                float x = axes[axis], y = axes[3 + axis];
                seenAt[axis] = x * c + y * s;
                tumbled[axis] = -x * s + y * c;
            }
            OwnWeapon.seenFrom(camera, seenAt[0], seenAt[1], seenAt[2], seen, 0);
            OwnWeapon.seenFrom(camera, tumbled[0], tumbled[1], tumbled[2], seen, 4);
            OwnWeapon.seenFrom(camera, axes[6], axes[7], axes[8], seen, 8);
            OwnWeapon.seenFrom(camera, at[0] - camera.eyeX, at[1] - camera.eyeY, at[2] - camera.eyeUp, seen, 12);
            seen[3] = 0f;
            seen[7] = 0f;
            seen[11] = 0f;
            seen[15] = 1f;
        }
    }
}

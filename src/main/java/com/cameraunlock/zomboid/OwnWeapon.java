package com.cameraunlock.zomboid;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * The mod's own model of the pump action shotgun, drawn in the viewer's hands in place of
 * the game's, which is made to be seen from a rooftop: a mesh, its colour and the shape and
 * finish of its surface, made by tools/models/shotgun.py and kept in the jar.
 *
 * It is held exactly where the game's own would be: the grip in the right hand and the
 * muzzle where the game's muzzle is, so that the hands, the aim and the flash are as they
 * were. It is lit here and not by the pass that lights people: steel is a mirror, and what
 * it shows is the sky, the sun and the room. Its fore-end slides back with the hand that
 * works it, the bolt goes with it, and as the bolt comes back the spent shell is thrown out
 * of the port and falls away.
 *
 * All of it is the render thread's.
 */
final class OwnWeapon {
    final View view;

    OwnWeapon(View view) {
        this.view = view;
    }


    /** The one item of the game's that the model stands for. */
    private static final String ITEM = "Base.Shotgun";

    /** The model, read out of the jar and on the graphics card. */
    static final OwnModel MODEL = new OwnModel("the shotgun's model", "shotgun");
    private static final int FIXED = 0, SHELL = 3, STOCK = 4;

    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_UNSIGNED_INT = 0x1405, GL_TRIANGLES = 0x0004;
    private static final int GL_FRAMEBUFFER = 0x8D40, GL_COLOR_ATTACHMENT0 = 0x8CE0, GL_DEPTH_ATTACHMENT = 0x8D00, GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_TEXTURE0 = 0x84C0, GL_DEPTH_TEST = 0x0B71, GL_CULL_FACE = 0x0B44, GL_BLEND = 0x0BE2, GL_LEQUAL = 0x0203;
    /** How near the eye the model is still drawn, in tiles. */
    private static final float NEAREST = 0.012f;
    /** Units nothing else in the picture keeps a texture on between passes. */
    private static final int COLOUR_UNIT = 30, SURFACE_UNIT = 31;
    /** The units the pass that lights people leaves the sun's two shadow maps on. */
    static final int SHADOW_NEAR_UNIT = 1, SHADOW_FAR_UNIT = 2;

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
            // How far back along the barrel the part being drawn has slid, in the model's metres.
            uniform float uSlide;
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

            void main() {
                vec4 seen = uModel * vec4(aPlace.x, aPlace.y - uSlide, aPlace.z, 1.0);
                vec3 n = normalize(mat3(uModel) * aNormal);
                vec3 t = normalize(mat3(uModel) * aTangent);
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
            // Nearer the eye than this the part is not drawn, and -1 where uModel is a mirror.
            uniform float uCut;
            uniform float uHanded;
            // Set for the pass that keeps only where the model is, and none of its colour.
            uniform float uDepthOnly;
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
                // Up to a hand's breadth over the horizon what stands round the viewer is in the
                // way of the sky: trees, houses, the ground itself.
                vec3 ground = vec3(0.24, 0.26, 0.16) * (skyGives * (0.35 + 0.9 * max(uLight.z, 0.0)));
                vec3 out3 = mix(ground, open, smoothstep(-0.05, 0.30, d.z));
                vec3 room = vec3(1.0, 0.97, 0.92) * (0.16 + 0.84 * smoothstep(-0.5, 0.9, d.z));
                return mix(room, out3 * 2.0, uOutdoors) * pow(uStood, 2.2);
            }

            void main() {
                float ahead = -vSeen.z;
                if (ahead < uCut) {
                    discard;
                }
                if (uDepthOnly > 0.5) {
                    gl_FragDepth = clamp(0.5 + 0.5 * (uFar + uWriteNear - 2.0 * uWriteNear * uFar / ahead) / (uFar - uWriteNear), 0.0, 1.0);
                    return;
                }
                // Where the cut goes through a part its inside shows, which is drawn as the cut end
                // of what the part is made of, in shade, and not as a lit surface seen from behind.
                if (gl_FrontFacing != (uHanded < 0.0)) {
                    gl_FragColor = vec4(texture2D(uColour, vUv).rgb * (0.45 * pow(uStood, 2.2)), 1.0);
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
                // What a dull surface scatters is the light the picture gives the hands that hold it:
                // the game's light where the viewer stands, from the sky and the sunlit ground out
                // of doors and mostly from above within.
                vec3 soft = pow(uStood, 2.2) * mix(vec3(0.66 + 0.34 * n.z + 0.1 * dot(n.xy, vec2(0.6, -0.8))), vec3(0.72, 0.80, 0.95) * (0.72 + 0.28 * n.z), uOutdoors);
                soft += pow(uLightColor, vec3(2.2)) * vec3(0.9, 0.86, 0.74) * (0.3 * max(uLight.z, 0.0) * (1.0 - max(n.z, 0.0)) * uOutdoors);
                vec3 back = reflect(-view, n);
                vec3 shown = mix(around(back, skyGives), 0.5 * (around(normalize(back + n), skyGives) + soft), clamp(rough * 1.5, 0.0, 1.0));
                // What a surface this rough throws back of its surroundings at this angle, by the fit Brian
                // Karis published for it (THIRD-PARTY-NOTICES.md, Acknowledgements).
                vec4 fit = rough * vec4(-1.0, -0.0275, -0.572, 0.022) + vec4(1.0, 0.0425, 1.04, -0.04);
                float edge = min(fit.x * fit.x, exp2(-9.28 * nv)) * fit.x + fit.y;
                vec2 share = vec2(-1.04, 1.04) * edge + fit.zw;
                // What stands close round a point keeps the light off it, and out of its mirror more as it is rough.
                float mirrored = mix(1.0, reached, 0.4 + 0.6 * rough);
                // Varnish and rubber mirror the sky far less cleanly than the sums for a perfect coat say: taken whole, walnut under a blue sky was purple.
                color += dull * soft * reached + (mirror * share.x + share.y) * shown * (mirrored * mix(0.45, 1.0, metal));
                color += (dull + mirror * 0.6) * flashLight(at, n);

                gl_FragColor = vec4(color, 1.0);
                gl_FragDepth = clamp(0.5 + 0.5 * (uFar + uWriteNear - 2.0 * uWriteNear * uFar / ahead) / (uFar - uWriteNear), 0.0, 1.0);
            }
            """;

    /** Linked the first time the model is made and kept: the model's buffers and pictures are what is given back. */
    private static Program program;
    /** The picture's colour with the depth of the viewer's own hands, which the weapon is drawn against. */
    private int target, targetColor, targetDepth;

    private final float[] model = new float[16], projection = new float[16];
    private boolean placed;
    private float handed, stood, outdoors, cut;
    /** How far from the eye the stock is first drawn with the sights up, in the model's metres. */
    private static final float NOT_DRAWN_WITHIN = 0.13f;

    /** Whether the model is what is drawn for an item held. */
    static boolean standsFor(String item) {
        return MODEL.onCard && Own.weapons() && ITEM.equals(item);
    }

    /** Reads the model out of the jar and gives it to the graphics card. Null, or what went wrong. */
    static String prepare() {
        if (program == null) {
            StringBuilder problem = new StringBuilder();
            program = Program.link("own weapon", VERTEX, FRAGMENT, problem);
            if (program == null) {
                return problem.toString();
            }
        }
        return MODEL.prepare(program);
    }

    /**
     * Notes where the weapon is this frame, to be drawn once the hands on it have been.
     *
     * @param heldView    ViewModel's matrix for the weapon held up: from the character's axes to the view's
     * @param weapon      where the game has the weapon in its character's axes, read by rows
     * @param finalScale  the scale the game draws the character's parts at
     * @param gameMuzzle  how far the muzzle of the game's model is from its grip, in that model's units
     * @param light       the game's light where the viewer stands
     * @param outside     whether the viewer stands out of doors
     * @param aim         0 at the hip, 1 with the sights up
     */
    void place(float[] heldView, float[] weapon, float finalScale, float gameMuzzle, float light, boolean outside, float aim) {
        // The model is sized so that its muzzle is where the game's is.
        float size = finalScale * gameMuzzle / MODEL.muzzleFar;
        for (int column = 0; column < 3; column++) {
            model[column * 4] = weapon[column] * size;
            model[column * 4 + 1] = weapon[4 + column] * size;
            model[column * 4 + 2] = weapon[8 + column] * size;
            model[column * 4 + 3] = 0f;
        }
        model[12] = weapon[3] * finalScale;
        model[13] = weapon[7] * finalScale;
        model[14] = weapon[11] * finalScale;
        model[15] = 1f;
        ViewModelMath.onto(heldView, model);
        float turned = model[0] * (model[5] * model[10] - model[9] * model[6]) - model[4] * (model[1] * model[10] - model[9] * model[2])
                + model[8] * (model[1] * model[6] - model[5] * model[2]);
        handed = turned < 0f ? -1f : 1f;
        stood = light;
        outdoors = outside ? 1f : 0f;
        // With the sights up the butt of the stock is at the eye, where the shoulder it is held
        // against would be, and seen from there its end would fill the foot of the picture. As
        // it comes up it is left out for a hand's length from the eye, and the stock is seen
        // from there on: its comb, the wrist the right hand is on, the back of the receiver.
        float tilesToMetre = (float) Math.sqrt(model[4] * model[4] + model[5] * model[5] + model[6] * model[6]);
        cut = ViewModelMath.smooth(aim) * NOT_DRAWN_WITHIN * tilesToMetre;
        placed = true;
    }

    /** Whether the weapon was placed this frame and is still to be drawn. */
    boolean waiting() {
        return placed;
    }

    /**
     * The picture's colour and the hands' depth were given back, and the target is to take the
     * next ones. The target goes with them: a texture given back while a target still holds it
     * keeps its memory on the graphics card until that target lets go of it.
     */
    void forgetTarget() {
        GL30.glDeleteFramebuffers(target);
        target = 0;
        targetColor = 0;
        targetDepth = 0;
    }

    /**
     * Begins the drawing: the program is in use, for the picture to give it the light of the
     * frame under the names its own programs know it by (the sky, the sun and its two shadow
     * maps, the eye and its axes, the flash of a shot).
     */
    Program begin() {
        program.use();
        return program;
    }

    /**
     * Draws the weapon placed over the picture, behind whatever of the viewer's hands is
     * nearer than it, and then into the picture's own depth.
     *
     * @param camera     where the frame is drawn from
     * @param scene      the picture's target, and sceneColor its colour
     * @param handsDepth the depth the viewer's hands were drawn with, whose near plane is near
     * @param sceneNear  the near plane the picture's own depth is measured from
     */
    void draw(ViewCamera.Pose camera, int scene, int sceneColor, int handsDepth, float tanHalfFieldOfView, float aspect, float near, float far,
            float sceneNear) {
        placed = false;
        throwShells(camera);
        if (sceneColor != targetColor || handsDepth != targetDepth) {
            if (target == 0) {
                target = GL30.glGenFramebuffers();
            }
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
            GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sceneColor, 0);
            GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, handsDepth, 0);
            int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("the driver would not make the shotgun's target (status 0x" + Integer.toHexString(status) + ")");
            }
            targetColor = sceneColor;
            targetDepth = handsDepth;
        }
        // With the sights up the stock runs back under the eye, nearer than the plane the hands
        // are cut off at. It is drawn right up to the eye, and says of each point how deep
        // it is in the hands' own measure, where anything nearer than their plane is in front.
        ViewCamera.projection(tanHalfFieldOfView, aspect, NEAREST, far, projection);
        program.set("uProjection", projection);
        program.set("uHanded", handed);
        program.set("uStood", stood);
        program.set("uOutdoors", outdoors);
        program.set("uFar", far);
        program.set("uColour", COLOUR_UNIT);
        program.set("uSurface", SURFACE_UNIT);
        program.set("uShadowNear", SHADOW_NEAR_UNIT);
        program.set("uShadowFar", SHADOW_FAR_UNIT);
        GL13.glActiveTexture(GL_TEXTURE0 + COLOUR_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, MODEL.colour);
        GL13.glActiveTexture(GL_TEXTURE0 + SURFACE_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, MODEL.surface);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL30.glBindVertexArray(MODEL.array);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(true);
        // Held up, the model is a mirror image of itself as it was made, and its faces wind the other way.
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);

        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        program.set("uWriteNear", near);
        program.set("uDepthOnly", 0f);
        parts();
        // And where it is, for what the picture works out from depth afterwards.
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, scene);
        GL11.glColorMask(false, false, false, false);
        program.set("uWriteNear", sceneNear);
        program.set("uDepthOnly", 1f);
        parts();
        GL11.glColorMask(true, true, true, true);

        GL11.glDisable(GL_DEPTH_TEST);
        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);
    }

    private void parts() {
        program.set("uModel", model);
        program.set("uCut", 0f);
        for (int part = 0; part < SHELL; part++) {
            program.set("uSlide", part == FIXED ? 0f : view.viewModel.pumped * MODEL.travel);
            GL11.glDrawElements(GL_TRIANGLES, MODEL.indexCount[part], GL_UNSIGNED_INT, (long) MODEL.firstIndex[part] * Integer.BYTES);
        }
        program.set("uSlide", 0f);
        program.set("uCut", cut);
        GL11.glDrawElements(GL_TRIANGLES, MODEL.indexCount[STOCK], GL_UNSIGNED_INT, (long) MODEL.firstIndex[STOCK] * Integer.BYTES);
        program.set("uCut", 0f);
        for (int shell = 0; shell < SHELLS; shell++) {
            if (shellAge[shell] < SHELL_LASTS) {
                program.set("uModel", shellModel[shell]);
                GL11.glDrawElements(GL_TRIANGLES, MODEL.indexCount[SHELL], GL_UNSIGNED_INT, (long) MODEL.firstIndex[SHELL] * Integer.BYTES);
            }
        }
    }

    /** As many spent shells as can be in the air at once, and how long one is drawn for, in seconds. */
    private static final int SHELLS = 3;
    private static final float SHELL_LASTS = 0.9f;
    /** The bolt throws the shell out once the fore-end is this far back, on its way back. */
    private static final float THROWN_AT = 0.7f;
    /**
     * How a shell leaves the port, in tiles a second in the view's axes: out to the right, up
     * and a little back. Then how fast it falls, in tiles a second each second, and tumbles, in
     * radians a second.
     */
    private static final float THROWN_RIGHT = 1.5f, THROWN_UP = 1.1f, THROWN_BACK = 0.35f, FALLS = 7.5f, TUMBLES = 17f;
    private final float[] shellAge = {SHELL_LASTS, SHELL_LASTS, SHELL_LASTS};
    /** Each shell in the world: where it is, how it moves, and the gun's own axes as they were when it left. */
    private final float[][] shellAt = new float[SHELLS][3], shellGoes = new float[SHELLS][3], shellAxes = new float[SHELLS][9];
    private final float[][] shellModel = new float[SHELLS][16];
    private final float[] portSeen = new float[3];
    private float pumpedBefore;
    private long shellsAsOf;
    private int nextShell;

    /** Throws a shell as the bolt comes back, moves those in the air, and works out where the eye sees each. */
    private void throwShells(ViewCamera.Pose camera) {
        long now = System.nanoTime();
        float seconds = Math.min((now - shellsAsOf) / 1e9f, 0.05f);
        shellsAsOf = now;
        float pumped = view.viewModel.pumped;
        if (pumped >= THROWN_AT && pumpedBefore < THROWN_AT) {
            int shell = nextShell;
            nextShell = (nextShell + 1) % SHELLS;
            shellAge[shell] = 0f;
            ViewModelMath.place(model, MODEL.port[0], MODEL.port[1] - pumped * MODEL.travel * 0.5f, MODEL.port[2], portSeen);
            inWorld(camera, portSeen[0], portSeen[1], portSeen[2], shellAt[shell]);
            shellAt[shell][0] += camera.eyeX;
            shellAt[shell][1] += camera.eyeY;
            shellAt[shell][2] += camera.eyeUp;
            inWorld(camera, THROWN_RIGHT, THROWN_UP, THROWN_BACK, shellGoes[shell]);
            for (int column = 0; column < 3; column++) {
                inWorld(camera, model[column * 4], model[column * 4 + 1], model[column * 4 + 2], portSeen);
                System.arraycopy(portSeen, 0, shellAxes[shell], column * 3, 3);
            }
        }
        pumpedBefore = pumped;
        for (int shell = 0; shell < SHELLS; shell++) {
            if (shellAge[shell] >= SHELL_LASTS) {
                continue;
            }
            shellAge[shell] += seconds;
            float[] at = shellAt[shell], goes = shellGoes[shell], axes = shellAxes[shell], seen = shellModel[shell];
            goes[2] -= FALLS * seconds;
            for (int axis = 0; axis < 3; axis++) {
                at[axis] += goes[axis] * seconds;
            }
            // It tumbles end over end about the gun's upright as it was when the shell left.
            float turned = TUMBLES * shellAge[shell];
            float c = (float) Math.cos(turned), s = (float) Math.sin(turned);
            for (int axis = 0; axis < 3; axis++) {
                float x = axes[axis], y = axes[3 + axis];
                portSeen[axis] = x * c + y * s;
                tumbled[axis] = -x * s + y * c;
            }
            seenFrom(camera, portSeen[0], portSeen[1], portSeen[2], seen, 0);
            seenFrom(camera, tumbled[0], tumbled[1], tumbled[2], seen, 4);
            seenFrom(camera, axes[6], axes[7], axes[8], seen, 8);
            seenFrom(camera, at[0] - camera.eyeX, at[1] - camera.eyeY, at[2] - camera.eyeUp, seen, 12);
            seen[3] = 0f;
            seen[7] = 0f;
            seen[11] = 0f;
            seen[15] = 1f;
        }
    }

    private final float[] tumbled = new float[3];

    /** A direction of the view's, x to the right, y up and z towards the viewer, in the world's axes. */
    static void inWorld(ViewCamera.Pose camera, float x, float y, float z, float[] out) {
        out[0] = camera.rightX * x + camera.upX * y - camera.forwardX * z;
        out[1] = camera.rightY * x + camera.upY * y - camera.forwardY * z;
        out[2] = camera.rightUp * x + camera.upUp * y - camera.forwardUp * z;
    }

    /** A direction of the world's in the view's axes, written into a matrix at a column's first place. */
    static void seenFrom(ViewCamera.Pose camera, float x, float y, float z, float[] out, int at) {
        out[at] = camera.rightX * x + camera.rightY * y + camera.rightUp * z;
        out[at + 1] = camera.upX * x + camera.upY * y + camera.upUp * z;
        out[at + 2] = -(camera.forwardX * x + camera.forwardY * y + camera.forwardUp * z);
    }
}

package com.cameraunlock.zomboid;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * What the mod's own firearm models share, each drawn in the viewer's hands in place of the
 * game's, which is made to be seen from a rooftop (OwnShotgun, OwnRifle): the program that
 * lights them, where one is held, how it is drawn over the hands that hold it, and the spent
 * shells and cases its action throws out.
 *
 * A model is held exactly where the game's own would be: the grip in the right hand and the
 * muzzle where the game's muzzle is, so that the hands, the aim, the flash and a scope are as
 * they were. It is lit here and not by the pass that lights people: steel is a mirror, and what
 * it shows is the sky, the sun and the room. It is drawn into the depth of what the mod draws
 * in the viewer's hands (HeldDepth), so that its parts near the eye sort against one another
 * and against a scope on it.
 *
 * All of it is the render thread's.
 */
abstract class OwnGun {
    final View view;

    /** The units the pass that lights people leaves the sun's two shadow maps on. */
    static final int SHADOW_NEAR_UNIT = 1, SHADOW_FAR_UNIT = 2;
    /**
     * The units the model's colour and surface are bound to for the draw, and the one the hands'
     * depth is read from. 30 is also Pipeline's LAMP_UNIT: that is safe only because every
     * figureLight.use() binds the lamps again, so nothing may read unit 30 after this draw
     * without binding it first.
     */
    private static final int COLOUR_UNIT = 30, SURFACE_UNIT = 31, HANDS_UNIT = 3;
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_UNSIGNED_INT = 0x1405, GL_TRIANGLES = 0x0004;
    private static final int GL_FRAMEBUFFER = 0x8D40, GL_TEXTURE0 = 0x84C0, GL_DEPTH_TEST = 0x0B71, GL_CULL_FACE = 0x0B44, GL_BLEND = 0x0BE2, GL_LEQUAL = 0x0203;

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
            // A part that moves: the cosine and sine of how far it is turned about the bore, how
            // far back along it it has slid, in the model's metres, and how high the bore is over
            // the model's origin.
            uniform vec2 uTurn;
            uniform float uSlide;
            uniform float uBore;
            // A part that turns about a pin or hinge of its own, in the model's axes: rigid, and
            // the same for all but such a part.
            uniform mat4 uPart;
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
                vec4 seen = uModel * (uPart * vec4(place.x, place.y - uSlide, place.z, 1.0));
                vec3 n = normalize(mat3(uModel) * (mat3(uPart) * turned(aNormal, 0.0)));
                vec3 t = normalize(mat3(uModel) * (mat3(uPart) * turned(aTangent, 0.0)));
                vSeen = seen.xyz;
                vNormal = inWorld(n);
                vTangent = inWorld(t);
                vOther = inWorld(cross(n, t)) * (aWay * uHanded);
                vUv = aUv;
                gl_Position = uProjection * seen;
            }
            """;

    private static final String FRAGMENT = "#version 120\n" + Sky.ATMOSPHERE + MuzzleFlash.GLSL + Fires.GLSL + """
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
            // Nearer the eye than this the part is not drawn.
            uniform float uCut;
            // -1 where uModel is a mirror.
            uniform float uHanded;
            // The depth the viewer's hands were drawn with, the size of the picture and the near
            // plane of that depth, and whether what they cover is left out: it is, of the
            // gun's colour, and is not of its depth, which is tested against the picture's own.
            uniform sampler2D uHands;
            uniform vec2 uSize;
            uniform float uHandsNear;
            uniform float uBehindHands;
            // Set for the pass that keeps only where the gun is, and none of its colour.
            uniform float uDepthOnly;
            // Tiles to a metre at the size the gun is drawn.
            uniform float uMetre;
            // The gun's finish, as each was tuned: how much of what is round it varnish and rubber
            // mirror against a perfect coat, how near square-on oiled wood is taken to be seen
            // however it is seen, and how much of the light the viewer's head keeps off it.
            uniform float uVarnish;
            uniform float uGrain;
            uniform float uHeadShade;
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

            // How much of the light from the way d the viewer's own head keeps off a point of
            // the gun: with the sights up the head is over the comb and fills the sky that
            // way, so what faces the eye from close by is in its shade and mirrors only it.
            float headHides(vec3 d, vec3 view) {
                float close = 1.0 - smoothstep(0.10, 0.28, length(vSeen) / uMetre);
                return uHeadShade * close * smoothstep(0.05, 0.7, dot(d, view));
            }

            float depthWritten(float ahead) {
                return clamp(0.5 + 0.5 * (uFar + uWriteNear - 2.0 * uWriteNear * uFar / ahead) / (uFar - uWriteNear), 0.0, 1.0);
            }

            void main() {
                float ahead = -vSeen.z;
                if (ahead < uCut) {
                    discard;
                }
                if (uBehindHands > 0.5) {
                    float z = texture2D(uHands, gl_FragCoord.xy / uSize).r;
                    if (z < 1.0 && 2.0 * uHandsNear * uFar / (uFar + uHandsNear - (z * 2.0 - 1.0) * (uFar - uHandsNear)) < ahead) {
                        discard;
                    }
                }
                if (uDepthOnly > 0.5) {
                    gl_FragDepth = depthWritten(ahead);
                    return;
                }
                // Where the cut goes through a part its inside shows, which is drawn as the cut end
                // of what the part is made of, in shade, and not as a lit surface seen from behind.
                if (gl_FrontFacing != (uHanded < 0.0)) {
                    gl_FragColor = vec4(texture2D(uColour, vUv).rgb * (0.45 * pow(uStood, 2.2)), 1.0);
                    gl_FragDepth = depthWritten(ahead);
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
                soft *= 1.0 - 0.6 * headHides(n, view);
                vec3 back = reflect(-view, n);
                vec3 shown = mix(around(back, skyGives), 0.5 * (around(normalize(back + n), skyGives) + soft), clamp(rough * 1.5, 0.0, 1.0));
                shown *= 1.0 - 0.9 * headHides(back, view);
                // What a surface this rough throws back of its surroundings at this angle, by the fit Brian
                // Karis published for it (THIRD-PARTY-NOTICES.md, Acknowledgements).
                vec4 fit = rough * vec4(-1.0, -0.0275, -0.572, 0.022) + vec4(1.0, 0.0425, 1.04, -0.04);
                // Oiled wood seen along its length does not turn into a mirror of the sky as a
                // polished thing would: its grain throws most of that light back the way it came.
                float glance = mix(max(nv, uGrain), nv, metal);
                float edge = min(fit.x * fit.x, exp2(-9.28 * glance)) * fit.x + fit.y;
                vec2 share = vec2(-1.04, 1.04) * edge + fit.zw;
                // What stands close round a point keeps the light off it, and out of its mirror more as it is rough.
                float mirrored = mix(1.0, reached, 0.4 + 0.6 * rough);
                color += dull * soft * reached + (mirror * share.x + share.y) * shown * (mirrored * mix(uVarnish, 1.0, metal));
                color += (dull + mirror * 0.6) * (flashLight(at, n) + fireLight(at, n));

                gl_FragColor = vec4(color, 1.0);
                gl_FragDepth = depthWritten(ahead);
            }
            """;

    /** Linked the first time a model is made and kept: the models' buffers and pictures are what is given back. */
    private static volatile Program program;
    /** Set once the program has been refused, which is said once: the game's own guns are drawn from then on. */
    private static boolean refused;

    /** The finish, as the fragment shader's uVarnish, uGrain and uHeadShade. */
    private final float varnish, grain, headShade;

    /** The model placed this frame, and its matrix from the model's axes to the view's. */
    OwnModel drawn;
    final float[] model = new float[16];
    private final float[] projection = new float[16];
    private boolean placed;
    private float handed, stood, outdoors;
    private long workedAsOf;

    OwnGun(View view, float varnish, float grain, float headShade) {
        this.view = view;
        this.varnish = varnish;
        this.grain = grain;
        this.headShade = headShade;
    }

    /** Reads a model out of the jar and gives it to the graphics card, laid out for the program. Null, or what went wrong. */
    static String prepare(OwnModel model) {
        return model.prepare(program);
    }

    /** Whether the game was paused in the frame the gun was last placed for, as the logic thread had it. */
    private boolean stillFrame;

    /**
     * Links the program the models are drawn with, as a piece of the core: linked as a gun is
     * first held, it held that frame. Always null: a refusal leaves the game's own guns drawn,
     * and first person goes on, in every graphics mode.
     */
    static String link() {
        if (program != null || refused) {
            return null;
        }
        StringBuilder problem = new StringBuilder();
        program = Program.link("own gun", VERTEX, FRAGMENT, problem);
        if (program == null) {
            // Only the mod's own guns are drawn with it: first person goes on with the game's.
            refused = true;
            Log.info("the mod's own guns cannot be drawn on this machine, and the game's own are drawn: " + problem);
        }
        return null;
    }

    /** Whether the mod's own guns can be drawn: read by the logic thread too. */
    static boolean linked() {
        return program != null;
    }

    /**
     * Notes where the gun is this frame, to be drawn once the hands on it have been.
     *
     * @param what        the model to draw
     * @param heldView    ViewModel's matrix for the weapon held up: from the character's axes to the view's
     * @param weapon      where the game has the weapon in its character's axes, read by rows
     * @param finalScale  the scale the game draws the character's parts at
     * @param gameMuzzle  how far the muzzle of the game's model is from its grip, in that model's units
     * @param light       the game's light where the viewer stands
     * @param outside     whether the viewer stands out of doors
     * @param aim         0 at the hip, 1 with the sights up
     * @param paused      whether the game is paused in that frame
     */
    final void place(OwnModel what, float[] heldView, float[] weapon, float finalScale, float gameMuzzle, float light, boolean outside, float aim, boolean paused) {
        drawn = what;
        stillFrame = paused;
        handed = what.hold(model, heldView, weapon, finalScale, gameMuzzle);
        stood = light;
        outdoors = outside ? 1f : 0f;
        placed(aim);
        placed = true;
    }

    /** What the gun makes of where it is held this frame, once its matrix is filled. */
    void placed(float aim) {
    }

    /** Whether the gun was placed this frame and is still to be drawn. */
    final boolean waiting() {
        return placed;
    }

    /**
     * Begins the drawing: the program is in use, for the picture to give it the light of the
     * frame under the names its own programs know it by (the sky, the sun and its two shadow
     * maps, the eye and its axes, the flash of a shot).
     */
    final Program begin() {
        program.use();
        return program;
    }

    /** Tiles to a metre of the model, at the size it is held this frame. */
    final float metre() {
        return (float) Math.sqrt(model[4] * model[4] + model[5] * model[5] + model[6] * model[6]);
    }

    /**
     * Moves the action on by a frame and throws out what it throws, before the parts are drawn.
     *
     * @param seconds how long the frame was, counted no longer than a twentieth of a second
     */
    abstract void work(ViewCamera.Pose camera, float seconds);

    /** Draws the model's parts as they are this frame, and what is in the air of what the action threw. */
    abstract void parts(Program drawing);

    /** Draws one of the model's parts as the program is set. */
    final void part(int which) {
        GL11.glDrawElements(GL_TRIANGLES, drawn.indexCount[which], GL_UNSIGNED_INT, (long) drawn.firstIndex[which] * Integer.BYTES);
    }

    /**
     * Draws the gun placed over the picture, into the depth of what the mod draws in the
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
    final void draw(ViewCamera.Pose camera, int scene, int wide, int high, int handsDepth, float tanHalfFieldOfView, float aspect, float near, float far,
            float sceneNear, float asideX, float asideY) {
        placed = false;
        long now = System.nanoTime();
        // What the action threw hangs in the air while the game is paused.
        float seconds = stillFrame ? 0f : Math.min((now - workedAsOf) / 1e9f, 0.05f);
        workedAsOf = now;
        work(camera, seconds);

        ViewCamera.projection(tanHalfFieldOfView, aspect, Scope.NEAREST, far, projection);
        projection[8] += asideX;
        projection[9] += asideY;
        program.set("uProjection", projection);
        program.set("uHanded", handed);
        program.set("uStood", stood);
        program.set("uOutdoors", outdoors);
        program.set("uFar", far);
        program.set("uMetre", metre());
        program.set("uBore", drawn.muzzle[2]);
        program.set("uVarnish", varnish);
        program.set("uGrain", grain);
        program.set("uHeadShade", headShade);
        program.set("uColour", COLOUR_UNIT);
        program.set("uSurface", SURFACE_UNIT);
        program.set("uShadowNear", SHADOW_NEAR_UNIT);
        program.set("uShadowFar", SHADOW_FAR_UNIT);
        program.set("uHands", HANDS_UNIT);
        program.set("uSize", (float) wide, (float) high);
        program.set("uHandsNear", near);
        GL13.glActiveTexture(GL_TEXTURE0 + COLOUR_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, drawn.colour);
        GL13.glActiveTexture(GL_TEXTURE0 + SURFACE_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, drawn.surface);
        GL13.glActiveTexture(GL_TEXTURE0 + HANDS_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, handsDepth);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL30.glBindVertexArray(drawn.array);
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
        drawParts();
        // And where it is, for what the picture works out from depth afterwards.
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, scene);
        GL11.glColorMask(false, false, false, false);
        program.set("uWriteNear", sceneNear);
        program.set("uBehindHands", 0f);
        program.set("uDepthOnly", 1f);
        drawParts();
        GL11.glColorMask(true, true, true, true);

        GL11.glDisable(GL_DEPTH_TEST);
        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);
    }

    private void drawParts() {
        program.set("uModel", model);
        program.set("uTurn", 1f, 0f);
        program.set("uSlide", 0f);
        program.set("uCut", 0f);
        program.set("uPart", Characters.IDENTITY);
        parts(program);
    }

    /**
     * Fills out, by columns, with a part's turn by angle radians about a line through the
     * model's point (x, y, z) along the axis (ax, ay, az), as uPart takes it: a turn that is
     * positive by the right hand about the axis, in the model's own axes.
     */
    static void turnAbout(float[] out, float x, float y, float z, float ax, float ay, float az, float angle) {
        float length = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= length;
        ay /= length;
        az /= length;
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle), k = 1f - c;
        out[0] = c + ax * ax * k;
        out[1] = ay * ax * k + az * s;
        out[2] = az * ax * k - ay * s;
        out[3] = 0f;
        out[4] = ax * ay * k - az * s;
        out[5] = c + ay * ay * k;
        out[6] = az * ay * k + ax * s;
        out[7] = 0f;
        out[8] = ax * az * k + ay * s;
        out[9] = ay * az * k - ax * s;
        out[10] = c + az * az * k;
        out[11] = 0f;
        // The pivot stays where it is: p - R p.
        out[12] = x - (out[0] * x + out[4] * y + out[8] * z);
        out[13] = y - (out[1] * x + out[5] * y + out[9] * z);
        out[14] = z - (out[2] * x + out[6] * y + out[10] * z);
        out[15] = 1f;
    }

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

    /**
     * What an action throws out of its port, spent shells or fired cases: each flies out in the
     * world's axes, falls, and tumbles end over end about the gun's upright as it was when it
     * left, for as long as it is drawn.
     */
    static final class Thrown {
        private final float lasts, right, up, back, falls, tumbles;
        private final float[] age;
        /** Each one in the world: where it is, how it moves, the gun's own axes as they were when it left, and where the eye sees it. */
        private final float[][] at, goes, axes, seen;
        private final float[] scratch = new float[3], tumbled = new float[3], along = new float[3];
        private int next;

        /**
         * @param count   as many as can be in the air at once
         * @param lasts   how long one is drawn for, in seconds
         * @param right   how it leaves the port, in tiles a second in the view's axes: out to the
         *                right, up and back
         * @param falls   how fast it falls, in tiles a second each second
         * @param tumbles how fast it tumbles, in radians a second
         */
        Thrown(int count, float lasts, float right, float up, float back, float falls, float tumbles) {
            this.lasts = lasts;
            this.right = right;
            this.up = up;
            this.back = back;
            this.falls = falls;
            this.tumbles = tumbles;
            age = new float[count];
            java.util.Arrays.fill(age, lasts);
            at = new float[count][3];
            goes = new float[count][3];
            axes = new float[count][9];
            seen = new float[count][16];
        }

        /** Throws one out of the port, which is at x, y, z in the model's axes, the model held as `model` says. */
        void out(ViewCamera.Pose camera, float[] model, float x, float y, float z) {
            outAlong(camera, model, x, y, z, 0f, 0f, 0f);
        }

        /**
         * Throws one out from x, y, z in the model's axes as `out` does, moving besides along
         * (alongX, alongY, alongZ) of the model's own, in its metres a second.
         */
        void outAlong(ViewCamera.Pose camera, float[] model, float x, float y, float z, float alongX, float alongY, float alongZ) {
            int one = next;
            next = (next + 1) % age.length;
            age[one] = 0f;
            ViewModelMath.place(model, x, y, z, scratch);
            inWorld(camera, scratch[0], scratch[1], scratch[2], at[one]);
            at[one][0] += camera.eyeX;
            at[one][1] += camera.eyeY;
            at[one][2] += camera.eyeUp;
            along[0] = alongX;
            along[1] = alongY;
            along[2] = alongZ;
            ViewModelMath.turned(model, along, scratch);
            inWorld(camera, right + scratch[0], up + scratch[1], back + scratch[2], goes[one]);
            for (int column = 0; column < 3; column++) {
                inWorld(camera, model[column * 4], model[column * 4 + 1], model[column * 4 + 2], scratch);
                System.arraycopy(scratch, 0, axes[one], column * 3, 3);
            }
        }

        /** Moves those in the air on by a frame, and works out where the eye sees each. */
        void fly(ViewCamera.Pose camera, float seconds) {
            for (int one = 0; one < age.length; one++) {
                if (age[one] >= lasts) {
                    continue;
                }
                age[one] += seconds;
                float[] where = at[one], going = goes[one], turned = axes[one], matrix = seen[one];
                going[2] -= falls * seconds;
                for (int axis = 0; axis < 3; axis++) {
                    where[axis] += going[axis] * seconds;
                }
                float turn = tumbles * age[one];
                float c = (float) Math.cos(turn), s = (float) Math.sin(turn);
                for (int axis = 0; axis < 3; axis++) {
                    float x = turned[axis], y = turned[3 + axis];
                    scratch[axis] = x * c + y * s;
                    tumbled[axis] = -x * s + y * c;
                }
                seenFrom(camera, scratch[0], scratch[1], scratch[2], matrix, 0);
                seenFrom(camera, tumbled[0], tumbled[1], tumbled[2], matrix, 4);
                seenFrom(camera, turned[6], turned[7], turned[8], matrix, 8);
                seenFrom(camera, where[0] - camera.eyeX, where[1] - camera.eyeY, where[2] - camera.eyeUp, matrix, 12);
                matrix[3] = 0f;
                matrix[7] = 0f;
                matrix[11] = 0f;
                matrix[15] = 1f;
            }
        }

        /** Draws those in the air, each as the gun's part `which`. */
        void draw(OwnGun gun, Program drawing, int which) {
            for (int one = 0; one < age.length; one++) {
                if (age[one] < lasts) {
                    drawing.set("uModel", seen[one]);
                    gun.part(which);
                }
            }
        }
    }
}

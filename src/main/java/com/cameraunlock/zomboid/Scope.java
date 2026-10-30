package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import zombie.inventory.types.HandWeapon;
import zombie.inventory.types.WeaponPart;

/**
 * A rifle's telescopic sight in first person: a scope of the mod's own in place of the
 * game's, and in its eyepiece a second picture of the world, drawn from the objective lens
 * along the scope's own axis at the scope's magnification.
 *
 * The eyepiece is not a screen with that picture stuck on it. Each point of its glass shows
 * what a real scope would show the eye along that line: the part of the world at that angle
 * to the axis, if the line passes through the scope's exit pupil, and the black inside of the
 * tube if it does not (ScopeOptics). So the picture is whole only from behind the scope at
 * the scope: a shadow comes in from the side as the eye drifts off the axis, and the field
 * closes down as the eye falls too far back. Nearer than the eye relief the picture is whole
 * and grows with the glass. The reticle is in the same plane as the picture and stays on the
 * same point of the world wherever the eye is.
 *
 * The game's logic thread says which scope is fitted. Everything else is the render thread's.
 */
final class Scope {
    final View view;

    Scope(View view) {
        this.view = view;
    }


    /** The part of a firearm the game keeps its sight under. */
    private static final String PART = "Scope";
    /**
     * The attachments of a weapon's model that a scope is fixed to: most are fixed to the first,
     * and the game fixes some to the second on some rifles (the x4 on the MSR700 Rifle).
     */
    static final java.util.Set<String> ATTACHMENTS = java.util.Set.of("scope", "scope2");
    /**
     * The game's model units to a metre: the measure the mod's own shotgun is sized by, whose
     * muzzle is 0.9015 m from its grip where the game's is 0.4469 units.
     */
    static final float UNITS_PER_METRE = 0.4469f / 0.9015f;

    /**
     * The picture in the eyepiece, in dots each way. From the eye relief the eyepiece is under
     * half the picture's height across on the screen. Leaned in to, it is wider than the
     * screen is high, and the picture is drawn larger than it was made.
     */
    static final int PICTURE = 1024;
    /** Nearer the objective than this, in tiles, is the rifle the scope is on, and is left out of its picture. */
    static final float PICTURE_NEAR = 0.6f;
    private static final int PICTURE_UNIT = 29;
    /** How near the eye what the mod draws in the viewer's hands is still drawn, in tiles. */
    static final float NEAREST = 0.012f;
    /** How near the eyepiece's glass a lean may bring the eye, in tiles. */
    private static final float EYE_CLEAR = 0.02f;

    private static final int GL_TEXTURE_2D = 0x0DE1, GL_RGBA = 0x1908, GL_RGBA8 = 0x8058, GL_RGBA16F = 0x881A, GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_DEPTH_COMPONENT = 0x1902, GL_DEPTH_COMPONENT24 = 0x81A6;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_NEAREST = 0x2600, GL_LINEAR = 0x2601, GL_LINEAR_MIPMAP_LINEAR = 0x2703, GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_STATIC_DRAW = 0x88E4, GL_FLOAT = 0x1406, GL_TRIANGLES = 0x0004;
    private static final int GL_FRAMEBUFFER = 0x8D40, GL_COLOR_ATTACHMENT0 = 0x8CE0, GL_DEPTH_ATTACHMENT = 0x8D00, GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_TEXTURE0 = 0x84C0, GL_DEPTH_TEST = 0x0B71, GL_CULL_FACE = 0x0B44, GL_BLEND = 0x0BE2, GL_LEQUAL = 0x0203;

    private static final String VERTEX = """
            #version 120
            attribute vec3 aPlace;
            attribute vec3 aNormal;
            attribute vec4 aFinish;
            attribute vec2 aGrip;
            // From the scope's axes to the view's: x to the right, y up, z towards the viewer.
            uniform mat4 uModel;
            uniform mat4 uProjection;
            uniform vec3 uRight;
            uniform vec3 uUp;
            uniform vec3 uForward;
            varying vec3 vSeen;
            varying vec3 vNormal;
            varying vec4 vFinish;
            varying vec2 vGrip;

            void main() {
                vec4 seen = uModel * vec4(aPlace, 1.0);
                vec3 n = normalize(mat3(uModel) * aNormal);
                vSeen = seen.xyz;
                vNormal = uRight * n.x + uUp * n.y - uForward * n.z;
                vFinish = aFinish;
                vGrip = aGrip;
                gl_Position = uProjection * seen;
            }
            """;

    private static final String FRAGMENT = "#version 120\n#extension GL_ARB_shader_texture_lod : require\n" + Sky.ATMOSPHERE + MuzzleFlash.GLSL + Fires.GLSL + PipelineShaders.BEAMS + String.format(Locale.ROOT, """
            const float RELIEF = %f;
            const float EYE_LENS = %f;
            const float FIELD = %f;
            const float PUPIL = %f;
            const float NEAREST_EYE = %f;
            """, ScopeOptics.RELIEF, ScopeOptics.EYE_LENS, ScopeOptics.FIELD, ScopeOptics.PUPIL, ScopeOptics.NEAREST_EYE) + """
            uniform sampler2D uPicture;
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
            // The light what the viewer holds is in, red, green and blue (HeldLight.STOOD), and whether that is out of doors.
            uniform vec3 uStood;
            uniform float uOutdoors;
            // The depth is written in the measure of the target drawn into, whose near plane this is.
            uniform float uWriteNear;
            uniform float uFar;
            // The depth the viewer's hands and rifle were drawn with, the size of the picture and the
            // near plane of that depth, and whether what they cover is left out: it is, of
            // the scope's colour, and is not of its depth, which is tested against the picture's own.
            uniform sampler2D uHands;
            uniform vec2 uSize;
            uniform float uHandsNear;
            uniform float uBehindHands;
            // Whether only the depth is wanted: the target drawn into keeps no colour.
            uniform float uDepthOnly;
            // 0 for the scope's body, 1 for the glass of its eyepiece, 2 for the glass of its objective.
            uniform float uGlass;
            // The middle of the glass being drawn and the scope's axes, in the view's: the axis
            // runs from the eyepiece to the objective.
            uniform vec3 uLens;
            uniform vec3 uAxis;
            uniform vec3 uSide;
            uniform vec3 uTop;
            // Tiles to a metre at the size the scope is drawn.
            uniform float uMetre;
            // How many times larger than the picture round it the scope shows the world, the radius
            // of its exit pupil in metres, and the tangent of half the angle its picture was drawn with.
            uniform float uPower;
            uniform float uExit;
            uniform float uPictureTan;
            // Whether the frame drew a picture for the eyepiece, and whether the reticle has ranging dots.
            uniform float uPictured;
            uniform float uDots;
            varying vec3 vSeen;
            varying vec3 vNormal;
            varying vec4 vFinish;
            varying vec2 vGrip;

            const float PI = 3.14159265;

            vec3 inWorld(vec3 v) {
                return uRight * v.x + uUp * v.y - uForward * v.z;
            }

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
                return mix(room, out3 * 2.0, uOutdoors) * pow(uStood, vec3(2.2));
            }

            float sunReaches(vec3 at) {
                vec4 from = vec4(at + uLight * 0.12, 1.0);
                vec3 c = (uNearMatrix * from).xyz * 0.5 + 0.5;
                if (inside(c)) {
                    float e = 2.0 / 4096.0;
                    return 0.2 * (shadow2D(uShadowNear, c).r + shadow2D(uShadowNear, c + vec3(e, e, 0.0)).r + shadow2D(uShadowNear, c + vec3(-e, e, 0.0)).r
                            + shadow2D(uShadowNear, c + vec3(e, -e, 0.0)).r + shadow2D(uShadowNear, c + vec3(-e, -e, 0.0)).r);
                }
                c = (uFarMatrix * from).xyz * 0.5 + 0.5;
                return inside(c) ? shadow2D(uShadowFar, c).r : 1.0;
            }

            // How much of the light from the way d the viewer's own head keeps off a point of
            // the scope: the head is a hand's breadth behind the eyepiece and fills the sky that
            // way, so what faces the eye from close by is in its shade and mirrors only it.
            float headHides(vec3 d, vec3 view) {
                float close = 1.0 - smoothstep(0.10, 0.28, length(vSeen) / uMetre);
                return close * smoothstep(0.05, 0.7, dot(d, view));
            }

            // The sun's glint in a surface as smooth as rough says, seen along view.
            float glint(vec3 n, vec3 view, float rough, out vec3 halfway) {
                halfway = normalize(uLight + view);
                float nl = max(dot(n, uLight), 0.0), nv = max(dot(n, view), 0.02), nh = max(dot(n, halfway), 0.0);
                float a2 = rough * rough * rough * rough;
                float spread = nh * nh * (a2 - 1.0) + 1.0;
                float seenBoth = 0.5 / (nl * sqrt(nv * nv * (1.0 - a2) + a2) + nv * sqrt(nl * nl * (1.0 - a2) + a2) + 0.0001);
                return min(a2 / (PI * spread * spread) * seenBoth * PI, 60.0) * nl;
            }

            // Anodised aluminium, rubber and steel.
            vec3 body(vec3 at, vec3 view) {
                vec3 n = normalize(vNormal);
                float rough = vFinish.x;
                if (vFinish.w > 0.5) {
                    // Knurling: two sets of grooves cut across each other, 2.4 mm apart. The
                    // slope of the cut surface is had from how its height changes across the
                    // picture, and where a groove is finer than a dot of the picture the cut
                    // only roughens what it is cut in.
                    float pitch = 2618.0;
                    float fine = max(fwidth(vGrip.x), fwidth(vGrip.y)) * pitch;
                    float kept = 1.0 - smoothstep(0.5, 1.6, fine);
                    float high = sin((vGrip.x + vGrip.y) * pitch) * sin((vGrip.x - vGrip.y) * pitch);
                    vec3 dx = dFdx(at), dy = dFdy(at);
                    vec3 r1 = cross(dy, n), r2 = cross(n, dx);
                    float det = dot(dx, r1);
                    if (abs(det) > 1e-12) {
                        vec3 slope = (r1 * dFdx(high) + r2 * dFdy(high)) / det;
                        n = normalize(n - slope * (0.00022 * uMetre * kept));
                    }
                    rough = mix(0.62, rough, kept);
                }
                float nv = max(dot(n, view), 0.02);
                float tone = vFinish.y;
                if (vFinish.w > 1.5) {
                    // The turrets' graduations: a white line filled into the metal at every click,
                    // a longer one at every fourth.
                    float click = vGrip.x / 0.00275;
                    float nth = floor(click + 0.5);
                    float line = 1.0 - smoothstep(0.05, 0.05 + fwidth(click) + 1e-6, abs(click - nth));
                    float longer = mod(nth, 4.0) < 0.5 ? 0.0155 : 0.0178;
                    line *= smoothstep(longer, longer + 0.0002, vGrip.y) * (1.0 - smoothstep(0.0205, 0.0207, vGrip.y));
                    tone = mix(tone, 0.62, line);
                    rough = mix(rough, 0.7, line);
                }
                vec3 dull = vec3(tone) * (1.0 - vFinish.z);
                vec3 mirror = mix(vec3(0.035), vec3(tone), vFinish.z);
                vec3 sunLight = pow(uLightColor, vec3(2.2)) * (1.5 * sunReaches(at));
                vec3 halfway;
                float shine = glint(n, view, rough, halfway);
                vec3 thrown = mirror + (1.0 - mirror) * pow(1.0 - clamp(dot(halfway, view), 0.0, 1.0), 5.0);
                vec3 color = (dull * max(dot(n, uLight), 0.0) + thrown * shine) * sunLight;

                float skyGives = clamp(dot(atmosphere(vec3(0.0, 0.0, 1.0)), vec3(1.4)) + 0.05, 0.0, 1.0);
                vec3 soft = pow(uStood, vec3(2.2)) * mix(vec3(0.66 + 0.34 * n.z + 0.1 * dot(n.xy, vec2(0.6, -0.8))), vec3(0.72, 0.80, 0.95) * (0.72 + 0.28 * n.z), uOutdoors);
                soft += pow(uLightColor, vec3(2.2)) * vec3(0.9, 0.86, 0.74) * (0.3 * max(uLight.z, 0.0) * (1.0 - max(n.z, 0.0)) * uOutdoors);
                soft *= 1.0 - 0.8 * headHides(n, view);
                vec3 back = reflect(-view, n);
                vec3 shown = mix(around(back, skyGives), 0.5 * (around(normalize(back + n), skyGives) + soft), clamp(rough * 1.5, 0.0, 1.0));
                // A satin finish is pits and peaks too small to see, and the pits throw back less than a flat face would.
                shown *= (1.0 - 0.94 * headHides(back, view)) * mix(1.0 - 0.5 * rough, 1.0, vFinish.z);
                // What a surface this rough throws back of its surroundings at this angle, by the fit Brian
                // Karis published for it (THIRD-PARTY-NOTICES.md, Acknowledgements).
                vec4 fit = rough * vec4(-1.0, -0.0275, -0.572, 0.022) + vec4(1.0, 0.0425, 1.04, -0.04);
                float edge = min(fit.x * fit.x, exp2(-9.28 * nv)) * fit.x + fit.y;
                vec2 share = vec2(-1.04, 1.04) * edge + fit.zw;
                color += dull * soft + (mirror * share.x + share.y) * shown;
                color += (dull + mirror * 0.6) * (flashLight(at, n) + fireLight(at, n) + beams(at, n, 0.0));
                return color;
            }

            // What a coated lens throws back of the light that falls on it: little, and coloured
            // by the coating, purple seen square on and green seen aslant. The glass bulges
            // by curve, towards the way it faces.
            vec3 thrownBack(vec3 at, vec3 view, float facing, float curve, float base) {
                vec3 off = vSeen - uLens;
                vec3 out3 = (off - uAxis * dot(off, uAxis)) / (EYE_LENS * uMetre);
                vec3 n = normalize(inWorld(uAxis * facing + out3 * curve));
                float nv = clamp(dot(n, view), 0.0, 1.0);
                float thrown = base + (1.0 - base) * pow(1.0 - nv, 5.0);
                vec3 coat = mix(vec3(0.74, 0.30, 0.95), vec3(0.30, 0.95, 0.52), smoothstep(0.05, 0.55, 1.0 - nv));
                float skyGives = clamp(dot(atmosphere(vec3(0.0, 0.0, 1.0)), vec3(1.4)) + 0.05, 0.0, 1.0);
                vec3 halfway;
                float shine = glint(n, view, 0.09, halfway);
                vec3 back = reflect(-view, n);
                vec3 color = around(back, skyGives) * coat * (thrown * (1.0 - 0.94 * headHides(back, view)));
                // Behind the first glass there are more, and each throws back a little of its own:
                // the next one in is hollow towards the eye, so its picture of the sky is the other
                // way up and slides the other way as the scope turns.
                vec3 inner = normalize(inWorld(uAxis * facing - out3 * (curve * 1.6)));
                vec3 innerBack = reflect(-view, inner);
                color += around(innerBack, skyGives) * coat.gbr * (0.5 * base * (1.0 - 0.94 * headHides(innerBack, view)));
                color += pow(uLightColor, vec3(2.2)) * (1.5 * sunReaches(at) * shine * thrown);
                return color + coat * 0.02 * (flashLight(at, n) + fireLight(at, n) + beams(at, n, 0.0));
            }

            // The eyepiece. A line of sight at an angle to the axis shows the world at that
            // angle over the scope's power, whatever part of the glass it passes through, and
            // only if it also passes through the exit pupil, which stands the eye relief
            // behind the glass. An eye nearer than that loses nothing for it, and the picture
            // fills as much of the glass as it does from the eye relief (ScopeOptics.field).
            vec3 eyepiece(vec3 at, vec3 view) {
                vec3 ray = normalize(vSeen);
                vec2 slope = vec2(dot(ray, uSide), dot(ray, uTop)) / max(dot(ray, uAxis), 0.05);
                float behind = dot(uLens, uAxis) / uMetre;
                vec2 across = -vec2(dot(uLens, uSide), dot(uLens, uTop)) / uMetre;
                // How far off the exit pupil the eye may be is counted in the world's measure, which is the head's.
                float off = length(across + slope * max(behind - RELIEF, 0.0)) * uMetre;
                float lit = 1.0 - smoothstep(abs(uExit - PUPIL), uExit + PUPIL, off);

                // Across the field: the middle at nought, the field stop's edge at one.
                float field = FIELD * max(RELIEF / max(behind, NEAREST_EYE), 1.0);
                vec2 g = slope / field;
                float out2 = dot(g, g);
                float dot1 = fwidth(g.x) + fwidth(g.y) + 1e-5;
                float stop = 1.0 - smoothstep(1.0 - dot1, 1.0, sqrt(out2));

                // The picture, as the glass passes it: drawn a little larger towards the edge
                // of the field, its colours a hair apart there and its edge softer than its middle.
                vec2 world = g * FIELD / uPower * (1.0 + 0.035 * out2);
                vec2 uv = world / uPictureTan * 0.5;
                float soft = 1.2 * out2 * out2;
                vec3 seen = vec3(texture2D(uPicture, 0.5 + uv * (1.0 - 0.006 * out2), soft).r, texture2D(uPicture, 0.5 + uv, soft).g,
                        texture2D(uPicture, 0.5 + uv * (1.0 + 0.006 * out2), soft).b);
                // Light scattered inside the tube lies over all of it as a thin veil. A bias does not
                // reach a smaller copy here: the place read is the same for every dot, so its
                // derivatives are nought and the bias is added to minus infinity.
                vec3 veil = texture2DLod(uPicture, vec2(0.5), 8.0).rgb;
                seen = seen * (1.0 - 0.22 * out2) * vec3(0.97, 0.98, 0.94) + veil * 0.03;

                // The reticle: four heavy posts that stop short of the middle and a fine wire
                // through it, and on the strongest scope three dots along each wire to range by.
                vec2 a = abs(g);
                float wire = min(a.x, a.y), along = max(a.x, a.y);
                float drawn = 1.0 - smoothstep(0.0035 - dot1, 0.0035 + dot1, wire);
                drawn = max(drawn, (1.0 - smoothstep(0.02 - dot1, 0.02 + dot1, wire)) * smoothstep(0.40 - dot1, 0.40 + dot1, along));
                float nearest = 0.1 * clamp(floor(along / 0.1 + 0.5), 1.0, 3.0);
                drawn = max(drawn, uDots * (1.0 - smoothstep(0.011 - dot1, 0.011 + dot1, length(vec2(along - nearest, wire)))));
                seen *= 1.0 - 0.97 * drawn;

                // The rim of the eyepiece is a hand's breadth from an eye that is looking at the
                // far distance, so it is a blur: its shade reaches a little way in over the glass.
                vec3 from = vSeen - uLens;
                float out1 = length(from - uAxis * dot(from, uAxis)) / (EYE_LENS * uMetre);
                float rim = 1.0 - 0.9 * smoothstep(0.86, 1.0, out1);
                vec3 color = seen * (lit * stop * uPictured * rim);
                // Past the field stop is the inside of the tube: black, but for what the lit
                // picture scatters onto the stop's own edge.
                float past = max(sqrt(out2) - 1.0, 0.0);
                color += veil * (0.05 * lit * uPictured * rim * (1.0 - stop) * exp(-past * 14.0));
                return color + thrownBack(at, view, -1.0, 0.38, 0.006);
            }

            void main() {
                float ahead = -vSeen.z;
                if (uBehindHands > 0.5) {
                    float z = texture2D(uHands, gl_FragCoord.xy / uSize).r;
                    if (z < 1.0 && 2.0 * uHandsNear * uFar / (uFar + uHandsNear - (z * 2.0 - 1.0) * (uFar - uHandsNear)) < ahead) {
                        discard;
                    }
                }
                gl_FragDepth = clamp(0.5 + 0.5 * (uFar + uWriteNear - 2.0 * uWriteNear * uFar / ahead) / (uFar - uWriteNear), 0.0, 1.0);
                if (uDepthOnly > 0.5) {
                    gl_FragColor = vec4(0.0);
                    return;
                }
                vec3 at = uEye + inWorld(vSeen);
                vec3 view = normalize(uEye - at);
                vec3 color = uGlass < 0.5 ? body(at, view)
                        : uGlass < 1.5 ? eyepiece(at, view)
                        : vec3(0.002, 0.004, 0.003) + thrownBack(at, view, 1.0, 0.3, 0.05);
                gl_FragColor = vec4(color, 1.0);
            }
            """;

    /** The scope on the firearm in the viewer's hands, or null. Written by the game's logic thread. */
    private volatile ScopeOptics.Glass fitted;
    /** How far a lean along the aim may take the eye towards the scope, in tiles: read by the logic thread. */
    private volatile float forwardStop = Float.POSITIVE_INFINITY;
    /** How many times larger the eyepiece's picture was in the frame last drawn for the eye's being near: read by the logic thread. */
    private volatile float grown = 1f;

    private static Program program;
    private static final int[] arrays = new int[ScopeOptics.KNOWN.length];
    private static final ScopeModel[] models = new ScopeModel[ScopeOptics.KNOWN.length];

    /**
     * The picture for the eyepiece: its colour, its depth, and the people in it before they are
     * lit. Four textures 1024 square, made by the first frame that draws a picture for an
     * eyepiece and given back when none has for a while: zero while they are not made.
     */
    int pictureTarget, pictureColor, pictureDepth, figureTarget, figureColor, figureNormal, figureLamps;
    /** The picture's colour with nothing beside it, for the holes shots leave, which read its depth as they are laid. */
    int decalTarget;
    private long picturedNanos;
    private static final int HANDS_UNIT = 3;

    private final float[] model = new float[16], projection = new float[16];
    /** From the scope's axes, in metres, to those of the weapon's model: as the game fixed the one to the other in the frame last drawn. */
    private final float[] mount = new float[16];
    private ScopeOptics.Glass placed, mounted;
    private float outdoors;
    private final float[] stood = new float[3];
    /**
     * Where the last frame drawn left the scope, against the aim it was held in: the
     * objective's place and the scope's side, top and axis, each as its parts to the right of
     * the aim, above it and along it.
     */
    private final float[] held = new float[12];
    /** Where the eye was from the eyepiece then: across the axis and behind the glass, in the scope's metres, and tiles to one of those. */
    private float eyeAcrossX, eyeAcrossY, eyeBehind, drawnAt;
    private boolean leftHeld, pictured;
    private float power, pictureTan;
    /** Where this frame's picture for the eyepiece is drawn from. */
    final ViewCamera.Pose sees = new ViewCamera.Pose();
    private final float[] a = new float[3], b = new float[3], c = new float[3], d = new float[3];
    private String said;

    /** Logic thread: the firearm in the viewer's hands, or null with none. */
    void fitted(HandWeapon weapon) {
        WeaponPart part = weapon == null ? null : weapon.getWeaponPart(PART);
        fitted = part == null || !Own.weapons(view.index) ? null : ScopeOptics.of(part.getFullType());
        redDot = part != null && RED_DOT.equals(part.getFullType());
    }

    /** The game's red dot sight, which it fits where a scope goes. */
    private static final String RED_DOT = "Base.RedDot";
    private volatile boolean redDot;

    /** Whether the firearm in hand has a red dot sight fitted. */
    boolean redDot() {
        return redDot;
    }

    /** The scope the firearm in hand is fitted with, or null. */
    ScopeOptics.Glass fitted() {
        return fitted;
    }

    /** Logic thread: how much of a turn of the mouse reaches the aim this frame. See ScopeOptics.turn. */
    float turn() {
        return ScopeOptics.turn(fitted, view.fieldOfView.zoom(), FieldOfView.AIMED_ZOOM, grown);
    }

    /**
     * Whether the game's own reticle is left out. With the sights more than half way up
     * behind a scope that the aim mode keeps the eye behind, sights locked and stock sights,
     * the scope's reticle is on the point the rounds go to and the game's would only lie over
     * it. At the hip the eye is beside the scope, which shows nothing, and an attack held
     * there aims too, so the game's reticle is the only mark of where the rounds go. In free
     * look the scope's is true only from behind the scope, and the game's stays.
     */
    boolean hidesReticle() {
        ViewModel.Held held = view.viewModel.held;
        return fitted != null && !Tracking.freeLook() && held != null && held.aim() > 0.5f;
    }

    /** How far a lean along the aim may take the eye towards the eyepiece with the sights up, in tiles. */
    float forwardStop() {
        return forwardStop;
    }

    /** Builds the program. Null, or what the driver refused. */
    static String prepare() {
        StringBuilder problem = new StringBuilder();
        program = Program.link("scope", VERTEX, FRAGMENT, problem);
        return program == null ? problem.toString() : null;
    }

    /**
     * Has the picture's targets there for the frame about to draw into them, making them if
     * they are not. Thrown, as a frame's fault, when the driver will not make them.
     *
     * @param now when the frame began
     */
    void lendPicture(long now) {
        picturedNanos = now;
        if (pictureTarget != 0) {
            return;
        }
        long start = System.nanoTime();
        pictureColor = texture(GL_RGBA16F, GL_RGBA, GL_FLOAT, GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR);
        GL30.glGenerateMipmap(GL_TEXTURE_2D);
        pictureDepth = texture(GL_DEPTH_COMPONENT24, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST, GL_NEAREST);
        pictureTarget = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, pictureTarget);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, pictureColor, 0);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, pictureDepth, 0);
        int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        decalTarget = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, decalTarget);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, pictureColor, 0);
        int decalStatus = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        figureColor = texture(GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST, GL_NEAREST);
        figureNormal = texture(GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST, GL_NEAREST);
        figureLamps = texture(GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST, GL_NEAREST);
        figureTarget = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, figureTarget);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, figureColor, 0);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + 1, GL_TEXTURE_2D, figureNormal, 0);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + 2, GL_TEXTURE_2D, figureLamps, 0);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, pictureDepth, 0);
        int figureStatus = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE || figureStatus != GL_FRAMEBUFFER_COMPLETE || decalStatus != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("the driver would not make the scope's picture (status 0x" + Integer.toHexString(status) + ", 0x"
                    + Integer.toHexString(figureStatus) + ", 0x" + Integer.toHexString(decalStatus) + ")");
        }
        if (DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "first person picture: the scope's picture made in %.1f ms", (System.nanoTime() - start) / 1e6));
        }
    }

    /** What the picture's five textures take on the graphics card, the colour's smaller copies of itself among them. */
    long pictureBytes() {
        return pictureTarget == 0 ? 0L : (long) PICTURE * PICTURE * 8 * 4 / 3 + (long) PICTURE * PICTURE * 4 * 4;
    }

    /** Gives the picture's targets back if no frame has drawn into them since before. */
    void takeBackPicture(long before) {
        if (pictureTarget != 0 && picturedNanos < before) {
            giveBackPicture();
            if (DevBuild.ON) {
                Log.info("first person picture: the scope's picture given back, unused");
            }
        }
    }

    /** Gives the picture's targets back to the graphics card. The next frame to draw a picture for an eyepiece makes them again. */
    void giveBackPicture() {
        GL30.glDeleteFramebuffers(pictureTarget);
        GL30.glDeleteFramebuffers(decalTarget);
        GL30.glDeleteFramebuffers(figureTarget);
        GL11.glDeleteTextures(pictureColor);
        GL11.glDeleteTextures(pictureDepth);
        GL11.glDeleteTextures(figureColor);
        GL11.glDeleteTextures(figureNormal);
        GL11.glDeleteTextures(figureLamps);
        pictureTarget = 0;
        decalTarget = 0;
        figureTarget = 0;
        pictureColor = 0;
        pictureDepth = 0;
        figureColor = 0;
        figureNormal = 0;
        figureLamps = 0;
    }

    private static int texture(int kept, int format, int type, int smaller, int larger) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, kept, PICTURE, PICTURE, 0, format, type, (ByteBuffer) null);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, smaller);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, larger);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return texture;
    }

    private static int arrayOf(ScopeOptics.Glass glass) {
        int which = 0;
        while (ScopeOptics.KNOWN[which] != glass) {
            which++;
        }
        if (arrays[which] == 0) {
            ScopeModel made = new ScopeModel(glass);
            models[which] = made;
            arrays[which] = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(arrays[which]);
            FloatBuffer data = ByteBuffer.allocateDirect(made.vertices.length * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
            data.put(made.vertices).flip();
            GL15.glBindBuffer(GL_ARRAY_BUFFER, GL15.glGenBuffers());
            GL15.glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
            attribute("aPlace", 3, 0);
            attribute("aNormal", 3, 3);
            attribute("aFinish", 4, 6);
            attribute("aGrip", 2, 10);
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
        return which;
    }

    private static void attribute(String name, int size, int offset) {
        int at = GL20.glGetAttribLocation(program.id, name);
        GL20.glEnableVertexAttribArray(at);
        GL20.glVertexAttribPointer(at, size, GL_FLOAT, false, ScopeModel.FLOATS * Float.BYTES, (long) offset * Float.BYTES);
    }

    /**
     * Notes where the scope is this frame, to be drawn once the hands and the rifle have been.
     *
     * @param heldView   ViewModel's matrix for the weapon held up: from the character's axes to the view's
     * @param part       where the game has its own scope in its character's axes, read by rows
     * @param weapon     the same for the rifle the scope is on
     * @param finalScale the scale the game draws the character's parts at
     * @param light      the light what the viewer holds is in, red, green and blue (HeldLight.STOOD)
     * @param outside    whether the viewer stands out of doors
     */
    void place(float[] heldView, float[] part, float[] weapon, float finalScale, float[] light, boolean outside) {
        ScopeOptics.Glass glass = fitted;
        if (glass == null || program == null) {
            return;
        }
        if (!(finalScale * finalScale * (part[1] * part[1] + part[5] * part[5] + part[9] * part[9]) > 0f)) {
            // The frame a scope is fitted to a weapon the game has not yet placed, the part's matrix is all noughts:
            // there is nothing to draw, and nothing measured from it is kept.
            return;
        }
        float size = finalScale * UNITS_PER_METRE;
        for (int column = 0; column < 3; column++) {
            model[column * 4] = part[column] * size;
            model[column * 4 + 1] = part[4 + column] * size;
            model[column * 4 + 2] = part[8 + column] * size;
            model[column * 4 + 3] = 0f;
        }
        model[12] = part[3] * finalScale;
        model[13] = part[7] * finalScale;
        model[14] = part[11] * finalScale;
        model[15] = 1f;
        ViewModelMath.onto(heldView, model);
        ScopeOptics.mount(part, weapon, UNITS_PER_METRE, mount);
        mounted = glass;
        System.arraycopy(light, 0, stood, 0, 3);
        outdoors = outside ? 1f : 0f;
        placed = glass;
    }

    /**
     * Where a rifle is held with the sights up so that the eye is on its scope's axis, the eye
     * relief behind the eyepiece: ScopeOptics.sighted, from where the game had the scope on
     * the rifle in the frame last drawn. The first frame a scoped rifle is held has no answer.
     */
    boolean sighted(float[] weapon, float finalScale, float[] origin, float[] forward, float[] top, float[] end, float scale, float[] out) {
        if (fitted == null || mounted != fitted) {
            return false;
        }
        ScopeOptics.sighted(mount, weapon, finalScale, origin, forward, top, end, scale, out);
        return true;
    }

    /** Whether the scope was placed this frame and is still to be drawn. */
    boolean waiting() {
        return placed != null;
    }

    /**
     * Whether this frame is to draw a picture for the eyepiece, and from where: only with the
     * eye where it can see some of it. Asked once a frame, before the world is drawn, and
     * answered from where the last frame left the scope against the aim, which is where this
     * frame will hold it but for the sway of a turn.
     *
     * @param aim  the aim this frame's weapon is held in
     * @param zoom the tangent the frame is drawn with over the tangent the player set
     */
    boolean wanted(ViewCamera.Pose aim, float zoom) {
        boolean left = leftHeld;
        leftHeld = false;
        pictured = false;
        ScopeOptics.Glass glass = fitted;
        if (!left || glass == null || glass != mounted || !ScopeOptics.shows(glass, eyeAcrossX, eyeAcrossY, eyeBehind, drawnAt)) {
            return false;
        }
        power = ScopeOptics.magnification(glass, zoom);
        pictureTan = ScopeOptics.pictureTan(glass, zoom);
        objective(aim, held, sees);
        pictured = true;
        return true;
    }

    /**
     * The pose the eyepiece's picture is drawn from: the objective, held against the aim as
     * against() measured it, rebuilt whole from the aim every frame.
     */
    static void objective(ViewCamera.Pose aim, float[] held, ViewCamera.Pose into) {
        into.eyeAt(aim.exactX() + aim.rightX * held[0] + aim.upX * held[1] + aim.forwardX * held[2],
                aim.exactY() + aim.rightY * held[0] + aim.upY * held[1] + aim.forwardY * held[2],
                aim.exactUp() + aim.rightUp * held[0] + aim.upUp * held[1] + aim.forwardUp * held[2]);
        into.rightX = aim.rightX * held[3] + aim.upX * held[4] + aim.forwardX * held[5];
        into.rightY = aim.rightY * held[3] + aim.upY * held[4] + aim.forwardY * held[5];
        into.rightUp = aim.rightUp * held[3] + aim.upUp * held[4] + aim.forwardUp * held[5];
        into.upX = aim.rightX * held[6] + aim.upX * held[7] + aim.forwardX * held[8];
        into.upY = aim.rightY * held[6] + aim.upY * held[7] + aim.forwardY * held[8];
        into.upUp = aim.rightUp * held[6] + aim.upUp * held[7] + aim.forwardUp * held[8];
        into.forwardX = aim.rightX * held[9] + aim.upX * held[10] + aim.forwardX * held[11];
        into.forwardY = aim.rightY * held[9] + aim.upY * held[10] + aim.forwardY * held[11];
        into.forwardUp = aim.rightUp * held[9] + aim.upUp * held[10] + aim.forwardUp * held[11];
    }

    /** The tangent of half the angle this frame's picture for the eyepiece is drawn with. */
    float pictureTan() {
        return pictureTan;
    }

    /** The picture for the eyepiece is drawn: it is read smaller than it is towards the edge of the field. */
    void developed() {
        GL13.glActiveTexture(GL_TEXTURE0 + PICTURE_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, pictureColor);
        GL30.glGenerateMipmap(GL_TEXTURE_2D);
        GL13.glActiveTexture(GL_TEXTURE0);
    }

    /**
     * Begins the drawing: the program is in use, for the picture to give it the light of the
     * frame under the names its own programs know it by.
     */
    Program begin() {
        program.use();
        return program;
    }

    /**
     * Draws the scope placed over the picture, into the depth of what the mod draws in the
     * viewer's hands (HeldDepth, which the caller has made ready for this frame), behind whatever of the
     * viewer's hands is nearer than it, and then into the picture's own depth.
     *
     * @param camera     where the frame is drawn from
     * @param aim        the aim the weapon is held in
     * @param scene      the picture's target, wide by high
     * @param handsDepth the depth the viewer's hands were drawn with, whose near plane is near
     * @param sceneNear  the near plane the picture's own depth is measured from
     * @param asideX     the fraction of a dot the world is drawn aside this frame, as a part of
     *                   the picture's width, and asideY of its height: the scope is drawn the same
     *                   way aside, so that its edges are smoothed over the frames as the world's are
     */
    void draw(ViewCamera.Pose camera, ViewCamera.Pose aim, int scene, int wide, int high, int handsDepth, float tanHalfFieldOfView,
            float aspect, float near, float far, float sceneNear, float asideX, float asideY) {
        ScopeOptics.Glass glass = placed;
        placed = null;
        int which = arrayOf(glass);
        ScopeModel shape = models[which];
        // The scope's axes in the view's: its axis, its top squared to that, and the side that
        // is to the right of someone looking along it.
        ViewModelMath.turned(model, AXIS, a);
        float metre = unit(a);
        if (!(metre > 0f)) {
            // The view the weapon is held in has no size: there is nothing to draw, and nothing measured from it is kept.
            Program.useOther(0);
            return;
        }
        ViewModelMath.turned(model, TOP, b);
        float along = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        for (int axis = 0; axis < 3; axis++) {
            b[axis] -= along * a[axis];
        }
        unit(b);
        c[0] = a[1] * b[2] - a[2] * b[1];
        c[1] = a[2] * b[0] - a[0] * b[2];
        c[2] = a[0] * b[1] - a[1] * b[0];

        ViewModelMath.place(model, 0f, shape.eyeGlassY, ScopeModel.AXIS_UP, d);
        eyeBehind = (d[0] * a[0] + d[1] * a[1] + d[2] * a[2]) / metre;
        eyeAcrossX = -(d[0] * c[0] + d[1] * c[1] + d[2] * c[2]) / metre;
        eyeAcrossY = -(d[0] * b[0] + d[1] * b[1] + d[2] * b[2]) / metre;
        drawnAt = metre;
        forwardStop = ScopeOptics.forwardStop(metre, EYE_CLEAR);
        grown = ScopeOptics.grown(eyeBehind);
        if (!glass.part().equals(said)) {
            said = glass.part();
            if (DevBuild.ON) {
                Log.info(String.format(Locale.ROOT, "scope %s: %.0f times, drawn at %.4f tiles to the metre, the eye %.1f mm behind its eyepiece and %.1f mm off its axis",
                        said, glass.power(), metre, eyeBehind * 1000f, Math.hypot(eyeAcrossX, eyeAcrossY) * 1000f));
            }
        }

        program.set("uModel", model);
        ViewCamera.projection(tanHalfFieldOfView, aspect, NEAREST, far, projection);
        projection[8] += asideX;
        projection[9] += asideY;
        program.set("uProjection", projection);
        program.set("uStood", stood[0], stood[1], stood[2]);
        program.set("uOutdoors", outdoors);
        program.set("uFar", far);
        program.set("uShadowNear", OwnGun.SHADOW_NEAR_UNIT);
        program.set("uShadowFar", OwnGun.SHADOW_FAR_UNIT);
        program.set("uPicture", PICTURE_UNIT);
        program.set("uHands", HANDS_UNIT);
        program.set("uSize", (float) wide, (float) high);
        program.set("uHandsNear", near);
        program.set("uAxis", a[0], a[1], a[2]);
        program.set("uSide", c[0], c[1], c[2]);
        program.set("uTop", b[0], b[1], b[2]);
        program.set("uMetre", metre);
        program.set("uPower", pictured ? power : 1f);
        program.set("uExit", glass.exitPupil());
        program.set("uPictureTan", pictured ? pictureTan : 1f);
        program.set("uPictured", pictured ? 1f : 0f);
        program.set("uDots", glass.power() >= 8f ? 1f : 0f);
        // A scope the eye is not behind has no picture, and the program multiplies what it reads of one by nought:
        // with none made it reads a colour of nothing, where no texture at all is the driver's to answer for.
        GL13.glActiveTexture(GL_TEXTURE0 + PICTURE_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, pictureColor != 0 ? pictureColor : Pipeline.none());
        GL13.glActiveTexture(GL_TEXTURE0 + HANDS_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, handsDepth);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL30.glBindVertexArray(arrays[which]);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(true);
        // Held up, the model is a mirror image of itself as it was made, and its faces wind the other way.
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);

        view.heldDepth.bind();
        program.set("uWriteNear", NEAREST);
        program.set("uBehindHands", 1f);
        program.set("uDepthOnly", 0f);
        parts(shape, glass);
        // And where it is, for what the picture works out from depth afterwards.
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, scene);
        GL11.glColorMask(false, false, false, false);
        program.set("uWriteNear", sceneNear);
        program.set("uBehindHands", 0f);
        program.set("uDepthOnly", 1f);
        parts(shape, glass);
        GL11.glColorMask(true, true, true, true);

        GL11.glDisable(GL_DEPTH_TEST);
        GL30.glBindVertexArray(0);
        Program.useOther(0);

        // Where the next frame's picture is drawn from: the objective, as it stands against the aim.
        ViewModelMath.place(model, 0f, glass.length() - ScopeModel.FRONT_GLASS_IN, ScopeModel.AXIS_UP, d);
        against(camera, aim, d, true, 0);
        against(camera, aim, c, false, 3);
        against(camera, aim, b, false, 6);
        against(camera, aim, a, false, 9);
        leftHeld = true;
    }

    private static final float[] AXIS = {0f, 1f, 0f}, TOP = {0f, 0f, 1f};

    private void parts(ScopeModel shape, ScopeOptics.Glass glass) {
        for (int part = 0; part < ScopeModel.PARTS; part++) {
            program.set("uGlass", (float) part);
            if (part != ScopeModel.BODY) {
                ViewModelMath.place(model, 0f, part == ScopeModel.EYE_GLASS ? shape.eyeGlassY : glass.length() - ScopeModel.FRONT_GLASS_IN, ScopeModel.AXIS_UP, d);
                program.set("uLens", d[0], d[1], d[2]);
            }
            GL11.glDrawArrays(GL_TRIANGLES, shape.first[part], shape.count[part]);
        }
    }

    private static float unit(float[] v) {
        float length = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        v[0] /= length;
        v[1] /= length;
        v[2] /= length;
        return length;
    }

    /** A point or a direction of the drawn view's, as its parts to the right of the aim, above it and along it. */
    private void against(ViewCamera.Pose camera, ViewCamera.Pose aim, float[] seen, boolean point, int at) {
        float x = camera.rightX * seen[0] + camera.upX * seen[1] - camera.forwardX * seen[2];
        float y = camera.rightY * seen[0] + camera.upY * seen[1] - camera.forwardY * seen[2];
        float up = camera.rightUp * seen[0] + camera.upUp * seen[1] - camera.forwardUp * seen[2];
        if (point) {
            float[] eyeFromAim = view.characters.eyeFromAim;
            x += eyeFromAim[0];
            y += eyeFromAim[1];
            up += eyeFromAim[2];
        }
        held[at] = aim.rightX * x + aim.rightY * y + aim.rightUp * up;
        held[at + 1] = aim.upX * x + aim.upY * y + aim.upUp * up;
        held[at + 2] = aim.forwardX * x + aim.forwardY * y + aim.forwardUp * up;
    }
}

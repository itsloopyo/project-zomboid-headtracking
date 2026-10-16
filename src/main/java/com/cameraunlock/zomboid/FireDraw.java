package com.cameraunlock.zomboid;

import java.util.ArrayList;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import zombie.iso.sprite.IsoAnim;

/**
 * Draws the fires a picture holds (Fires) into its light, after its world, its people, its sky
 * and its rain, and before its window panes: flames that give light and smoke that takes it,
 * marched through along each pixel's ray as far as the first surface the picture has there, so a
 * wall, a hearth's sides or a barrel's rim stands in front of what is behind it. Each fire is
 * drawn over the part of the picture its box covers, the furthest first.
 *
 * Render thread only.
 */
final class FireDraw {
    /** How many steps a ray takes through a fire, near the eye and at the furthest a fire is drawn. */
    static final int STEPS_NEAR = 40, STEPS_FAR = 14;
    /** How far off a fire's steps are fewest, in tiles. */
    static final float FEWEST_AT = 45f;
    /** Up to this many fires in sight each is marched in all its steps. */
    static final float CROWD = 12f;
    /** How far smoke is carried along the ground for each tile it rises, for each tile a second the air moves. */
    static final float DRIFT = 0.25f;
    /** The most it is carried for each tile it rises, however hard the wind: smoke leans over to about 55 degrees and no lower. */
    static final float MOST_DRIFT = 1.4f;
    /**
     * How many steps a ray takes through a fire's flames at most, near the eye and at the furthest,
     * and how many of the loop's cells it goes between two, nearer.
     */
    static final int FLAME_STEPS_NEAR = 72, FLAME_STEPS_FAR = 20;
    static final float FLAME_STRIDE = 1.5f;
    /**
     * The flames' light, in the picture's light for a tile of the thickest flame at its hottest,
     * and how much light a tile of it takes: flame is mostly glow, and hides a little of what is
     * behind it.
     */
    static final float GLOW = 50f, TAKES = 3f;
    /** The colour of the gas from cold to hottest, as a black body from this to this, in kelvin, and how its light grows with its heat. */
    static final float COLDEST = 900f, HOTTEST = 2800f, GLOW_POWER = 2f;
    /**
     * How far the noise stirs the flame, in the loop's cells, how many of its repeats a cell is,
     * and between which amounts of flame its edge is drawn: the loop is soft for want of cells, and
     * flame's edge is sharp.
     */
    static final float STIR = 5f, STIR_REPEATS_A_CELL = 0.0225f, EDGE_FROM = 0.2f, EDGE_TO = 0.45f;
    /** How much wider or narrower than the loop's own build a fire's flames may be drawn, to fill its radius. */
    static final float WIDEST = 1.6f, NARROWEST = 0.8f;

    private static final String FRAGMENT = """
            #version 120
            uniform vec2 uSize;
            uniform vec2 uTan;
            uniform vec2 uAside;
            uniform float uNear;
            uniform float uFar;
            uniform sampler2D uDepth;
            uniform vec3 uRight;
            uniform vec3 uUp;
            uniform vec3 uForward;
            // The fire's foot from the eye, in world axes (x, y, up).
            uniform vec3 uFoot;
            uniform float uRadius;
            uniform float uTall;
            uniform float uSmoke;
            uniform float uFlame;
            uniform float uThick;
            uniform float uSeed;
            uniform float uKind;
            // A window: the way along it and out of it on the map, its half width and its height.
            uniform vec4 uWindow;
            uniform vec2 uWindowSize;
            // How far the smoke is carried along the ground for each tile it rises.
            uniform vec2 uDrift;
            // The light the smoke is lit by where the fire is, besides the fire's.
            uniform vec3 uAmbient;
            uniform float uTime;
            uniform float uSteps;
            // How much of their light the flames' own glows are drawn with: FireCards.stoppedDown.
            uniform float uStopped;
            // How far over the fire's foot a ceiling stops its smoke, or 0.
            uniform float uCeiling;
            // The way to whichever of the sun and the moon lights the sky, and its light where the fire is, or none indoors.
            uniform vec3 uLightWay;
            uniform vec3 uLightColour;
            // The flames' loop (FlameVolume): how much flame and how hot, its frames laid side by side; a frame's
            // cells across, deep and high, and how many frames a row and a column of them hold.
            uniform sampler3D uFlames;
            uniform sampler3D uHeats;
            uniform vec3 uCells;
            uniform vec2 uLaid;
            // How big a cell is here, in tiles across and up; the way the loop is turned about the up, and 1 or -1 for
            // whether it is mirrored; where in the loop the fire is, in frames; how far its flame rises a frame, in cells.
            uniform vec2 uCellSize;
            uniform vec3 uTurn;
            uniform float uFrame;
            uniform float uRise;
            // The light of burning gas as hot as each sixteenth of the way from cold to the hottest, in the picture's
            // light for a tile of flame.
            uniform vec3 uGlow[16];
            uniform float uTakes;
            uniform float uFlameSteps;
            // How far a ray goes through the flames between two looks at them, in cells.
            uniform float uFlameStride;
            // The flames' box in tiles: how far it reaches each way from the fire's middle, and how high.
            uniform vec2 uFlameBox;
            // The clouds' noise (Pipeline.noise), which stirs the flame finer than the loop's cells: how far, in cells,
            // how many of the noise's repeats a cell is, and how far it has risen with the flame, in repeats.
            uniform sampler3D uNoise;
            uniform vec3 uStir;
            // Below the first, flame gives no light, and from the second all of it: the loop's soft edges drawn sharp.
            uniform vec2 uEdge;
            // 1 while the flames are written into the picture's depth alone, where they are more than a glow.
            uniform float uDepthOnly;

            // The steadying's mark for what moves within itself and goes nowhere, between a crowd's and a figure's.
            const float MOVES = 0.375;

            float hash(vec3 p) {
                p = fract(p * vec3(0.1031, 0.1030, 0.0973));
                p += dot(p, p.yxz + 33.33);
                return fract((p.x + p.y) * p.z);
            }

            float noise(vec3 p) {
                vec3 i = floor(p);
                vec3 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(mix(mix(hash(i), hash(i + vec3(1.0, 0.0, 0.0)), f.x), mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
                        mix(mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), f.x), mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
            }

            float fbm(vec3 p) {
                float sum = 0.5 * noise(p);
                p = p * 2.03 + vec3(1.7, 9.2, 3.1);
                sum += 0.25 * noise(p);
                p = p * 2.03 + vec3(1.7, 9.2, 3.1);
                sum += 0.125 * noise(p);
                p = p * 2.03 + vec3(1.7, 9.2, 3.1);
                return sum + 0.0625 * noise(p);
            }

            // The colour of burning gas by how hot it is, 0 to 1: deep red at its edge, orange,
            // yellow, and nearly white at the root of the flames.
            vec3 flameColour(float heat) {
                vec3 c = mix(vec3(0.5, 0.04, 0.0), vec3(1.0, 0.32, 0.03), smoothstep(0.0, 0.45, heat));
                c = mix(c, vec3(1.0, 0.72, 0.28), smoothstep(0.4, 0.8, heat));
                return mix(c, vec3(1.0, 0.95, 0.8), smoothstep(0.8, 1.1, heat));
            }

            // A frame's cell c of a loop laid as the flames' is in a texture, its frame's own cells `cells`: inset half a
            // cell, so no frame reads its neighbour's.
            float cellOf(sampler3D cells, vec3 c, vec3 size, float frame) {
                float f = mod(frame, uLaid.x * uLaid.y);
                vec2 tile = vec2(mod(f, uLaid.x), floor(f / uLaid.x));
                c = clamp(c, vec3(0.5), size - 0.5);
                return texture3D(cells, vec3((tile * size.xy + c.xy) / (size.xy * uLaid), c.z / size.z)).r;
            }

            // How much flame there is at q, from the fire's foot in world axes, and how hot it is, between two frames of
            // the loop: each carried along the way the flame rises, this frame up by the part of the way gone and the
            // next down by the part to go, so it flows rather than fades from one frame into the next.
            vec2 flameAt(vec3 q) {
                vec2 l = vec2(uTurn.x * q.x + uTurn.y * q.y, (uTurn.x * q.y - uTurn.y * q.x) * uTurn.z);
                vec3 c = vec3(l / uCellSize.x + uCells.xy * 0.5, q.z / uCellSize.y);
                if (any(lessThan(c, vec3(0.0))) || any(greaterThan(c, uCells))) {
                    return vec2(0.0);
                }
                vec3 n = c * uStir.y - vec3(0.0, 0.0, uStir.z);
                c += (vec3(texture3D(uNoise, n).rg, texture3D(uNoise, n * 1.9 + vec3(0.37, 0.71, 0.13)).r) - 0.5) * uStir.x;
                float f = floor(uFrame), t = uFrame - f;
                vec3 gone = vec3(0.0, 0.0, uRise * t), toGo = vec3(0.0, 0.0, uRise * (1.0 - t));
                float flame = mix(cellOf(uFlames, c - gone, uCells, f), cellOf(uFlames, c + toGo, uCells, f + 1.0), t);
                flame *= smoothstep(uEdge.x, uEdge.y, flame);
                if (flame < 0.004) {
                    return vec2(0.0);
                }
                vec3 halved = uCells * 0.5;
                float heat = mix(cellOf(uHeats, (c - gone) * 0.5, halved, f), cellOf(uHeats, (c + toGo) * 0.5, halved, f + 1.0), t);
                return vec2(flame, heat);
            }

            vec3 glowOf(float heat) {
                float at = clamp(heat, 0.0, 1.0) * 15.0;
                int i = int(min(floor(at), 14.0));
                return mix(uGlow[i], uGlow[i + 1], at - float(i));
            }

            // How thick the smoke is at q, from the fire's foot in world axes: it rolls up off the flames in
            // billows, noise turned about by noise of its own, spreading as it rises and carried by the air.
            float smokeAt(vec3 q) {
                float h = q.z - uTall * 0.45;
                if (h <= 0.0) {
                    return 0.0;
                }
                if (uCeiling > 0.0 && q.z > uCeiling) {
                    return 0.0;
                }
                float rise = h / uSmoke;
                vec2 level = q.xy - uDrift * h;
                // Under a ceiling it rolls out along it.
                float spread = uCeiling > 0.0 ? 1.0 + 1.6 * smoothstep(uCeiling - 0.7, uCeiling, q.z) : 1.0;
                float wide = uRadius * (0.8 + 2.2 * sqrt(rise)) * spread;
                if (rise >= 1.0 || dot(level, level) >= wide * wide * 1.44) {
                    return 0.0;
                }
                vec3 p = vec3(level / wide * 1.2, rise * 3.0 - uTime * 0.3) + vec3(uSeed * 1.3, uSeed * 0.4, 0.0);
                p += (vec3(noise(p * 0.6), noise(p * 0.6 + 7.3), noise(p * 0.6 + 13.1)) - 0.5) * 1.6;
                float puff = fbm(p);
                float r = length(level / wide);
                return clamp((puff - 0.32 - r * 0.42) * 3.2, 0.0, 1.0) * smoothstep(0.0, 0.1, rise) * (1.0 - smoothstep(0.5, 1.0, rise));
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                // Writing the flames into the picture's depth, its depth is not read: the depth test stands before them.
                float z = uDepthOnly > 0.5 ? 1.0 : texture2D(uDepth, uv).r * 2.0 - 1.0;
                float sceneAhead = uDepthOnly > 0.5 ? uFar : 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
                vec2 ndc = uv * 2.0 - 1.0 + uAside;
                // The ray in world axes, a tile of it a tile further ahead of the eye.
                vec3 ray = uForward + uRight * (ndc.x * uTan.x) + uUp * (ndc.y * uTan.y);
                if (uKind > 0.5) {
                    // A stove's door: the glow of the fire through its grille, a flat window that
                    // breathes and stirs. Bars nearer the eye than it hide it as any surface does.
                    vec3 out3 = vec3(uWindow.zw, 0.0);
                    float facing = dot(ray, out3);
                    if (facing >= 0.0) {
                        discard;
                    }
                    float ahead = dot(uFoot, out3) / facing;
                    // The door's face is behind the glow and the grille's bars before it: the glow shows
                    // where nothing nearer stands than the depth's own steps allow, which grow with the
                    // square of the distance. An allowance that grew as the door is seen slant let the
                    // glow through the stove's own corner.
                    if (ahead <= uNear || ahead > sceneAhead + 0.0005 + ahead * ahead * 0.00002) {
                        discard;
                    }
                    vec3 q = ray * ahead - uFoot;
                    vec2 on = vec2(dot(q.xy, uWindow.xy) / uWindowSize.x, q.z / uWindowSize.y);
                    if (abs(on.x) > 1.0 || on.y < 0.0 || on.y > 1.0) {
                        discard;
                    }
                    float stir = fbm(vec3(on * vec2(3.0, 2.5) + uSeed, uTime * 1.3 - on.y * 1.5));
                    float heat = clamp(stir * 1.4 - on.y * 0.45 + 0.05, 0.0, 1.0);
                    // Seen slant the glow is the grille's gaps seen edge on, and fades.
                    float edge = smoothstep(1.0, 0.8, abs(on.x)) * smoothstep(0.0, 0.08, on.y) * smoothstep(1.0, 0.9, on.y)
                            * smoothstep(0.1, 0.45, -facing / length(ray));
                    gl_FragData[0] = vec4(flameColour(heat) * (heat * 4.0 * edge * uFlame * uStopped), 0.0);
                    gl_FragData[1] = edge > 0.3 ? vec4(vec3(MOVES), 1.0) : vec4(0.0);
                    return;
                }
                // The box the fire is in, its flames' box in it: see FireDraw.box, which is the same.
                float flameHalf = uFlame > 0.0 ? uFlameBox.x : 0.0;
                float flameTop = uFlame > 0.0 ? uFlameBox.y : 0.0;
                float reach = max(uRadius * (uSmoke > 0.0 ? uCeiling > 0.0 ? 8.0 : 4.4 : 2.0), flameHalf);
                vec2 drift = uDrift * uSmoke;
                vec3 lo = vec3(min(vec2(-reach), vec2(-reach) + drift), 0.0);
                float top = max(uTall * 1.3 + uSmoke, flameTop);
                vec3 hi = vec3(max(vec2(reach), vec2(reach) + drift), uCeiling > 0.0 ? min(top, uCeiling) : top);
                vec3 from = -uFoot;
                vec3 inv = 1.0 / (ray + vec3(1e-6));
                vec3 t0 = (lo - from) * inv;
                vec3 t1 = (hi - from) * inv;
                vec3 tn = min(t0, t1);
                vec3 tf = max(t0, t1);
                float enter = max(max(tn.x, tn.y), max(tn.z, uNear));
                float leave = min(min(tf.x, tf.y), min(tf.z, sceneAhead));
                if (leave <= enter) {
                    discard;
                }
                // The flames' box: as wide as the loop's turned box can reach, as high as it is.
                vec3 ft0 = (vec3(-flameHalf, -flameHalf, 0.0) - from) * inv;
                vec3 ft1 = (vec3(flameHalf, flameHalf, flameTop) - from) * inv;
                vec3 ftn = min(ft0, ft1), ftf = max(ft0, ft1);
                float flameIn = max(max(max(ftn.x, ftn.y), ftn.z), enter);
                float flameOut = min(min(min(ftf.x, ftf.y), ftf.z), leave);
                if (uFlame <= 0.0 || flameOut <= flameIn) {
                    flameIn = flameOut = leave;
                }
                float jitter = hash(vec3(gl_FragCoord.xy, fract(uTime * 7.31) * 61.0));
                // A look every uFlameStride cells of the way through the flames, but never more than uFlameSteps: a ray
                // through a corner of the box takes few.
                float flameSteps = min(uFlameSteps, max(ceil((flameOut - flameIn) * length(ray) / (min(uCellSize.x, uCellSize.y) * uFlameStride)), 1.0));
                if (uDepthOnly > 0.5) {
                    // The first place along the ray where the flame is more than a glow, as the picture's depth there.
                    float df = (flameOut - flameIn) / flameSteps;
                    float seenSoFar = 0.0;
                    for (int i = 0; i < 96; i++) {
                        if (float(i) >= flameSteps) {
                            break;
                        }
                        float along = flameIn + df * (float(i) + jitter);
                        vec2 fh = flameAt(from + ray * along);
                        seenSoFar += fh.x * uTakes * length(ray) * df;
                        if (seenSoFar > 0.6) {
                            float ndc = (uFar + uNear - 2.0 * uFar * uNear / along) / (uFar - uNear);
                            gl_FragDepth = ndc * 0.5 + 0.5;
                            gl_FragData[0] = vec4(0.0);
                            return;
                        }
                    }
                    discard;
                }
                // How much light smoke throws on along the way it came, by the angle it turns through: much more
                // ahead than aside or back (Henyey and Greenstein's, g 0.45), so smoke before the sun glows at its edges.
                float turn = dot(normalize(ray), uLightWay);
                float ahead = 0.1592 * (1.0 - 0.2025) / pow(1.2025 - 0.9 * turn, 1.5);
                vec3 light = vec3(0.0);
                float through = 1.0;
                // Front to back: the smoke before the flames' box, then the flames and the smoke among them in finer
                // steps, then the smoke behind.
                float dt = (leave - enter) / uSteps;
                for (int part = 0; part < 3; part++) {
                    float from0 = part == 0 ? enter : part == 1 ? flameIn : flameOut;
                    float to0 = part == 0 ? flameIn : part == 1 ? flameOut : leave;
                    if (to0 <= from0 || part != 1 && uSmoke <= 0.0) {
                        continue;
                    }
                    float steps = part == 1 ? flameSteps : max(ceil((to0 - from0) / dt), 1.0);
                    float d = (to0 - from0) / steps;
                    float span = length(ray) * d;
                    for (int i = 0; i < 96; i++) {
                        if (float(i) >= steps || through < 0.02) {
                            break;
                        }
                        vec3 q = from + ray * (from0 + d * (float(i) + jitter));
                        if (part == 1) {
                            vec2 fh = flameAt(q);
                            if (fh.x > 0.0) {
                                light += through * glowOf(fh.y) * (fh.x * span * uFlame * uStopped);
                                through *= exp(-fh.x * uTakes * span);
                            }
                        }
                        float dense = uSmoke > 0.0 ? smokeAt(q) * uThick * span : 0.0;
                        if (dense > 0.0) {
                            // Sooty and dark where it leaves the flames, paler as it thins; lit by the light round
                            // it, and from below by the fire, only near the flames.
                            float soot = mix(0.16, 0.42, smoothstep(0.0, 0.5, (q.z - uTall * 0.45) / uSmoke));
                            float glowUp = exp(-max(q.z - uTall * 0.6, 0.0) * 2.5 / max(uTall, 0.3));
                            // The sun or the moon, as much as the smoke between this and it lets through: two looks
                            // towards it, a radius and two and a half away, stand for the column's own shadow.
                            float toLight = (smokeAt(q + uLightWay * uRadius) + smokeAt(q + uLightWay * uRadius * 2.5)) * uThick * uRadius * 1.25;
                            vec3 sunlit = uLightColour * (soot * exp(-toLight) * ahead * 4.0);
                            vec3 lit = uAmbient * soot + sunlit + vec3(1.0, 0.4, 0.1) * (uFlame * 0.5 * glowUp * uStopped);
                            light += through * lit * (1.0 - exp(-dense));
                            through *= exp(-dense);
                        }
                    }
                }
                if (through > 0.995 && dot(light, light) < 1e-6) {
                    discard;
                }
                gl_FragData[0] = vec4(light, 1.0 - through);
                // Where the fire is more than a haze it moves by itself, and the steadying keeps little of a
                // frame before it there. Its alpha of 1 lays the mark over what was there, and of 0 leaves that.
                float seen = max(1.0 - through, dot(light, vec3(0.3, 0.5, 0.2)));
                gl_FragData[1] = seen > 0.15 ? vec4(vec3(MOVES), 1.0) : vec4(0.0);
            }
            """;

    private static final int GL_FRAMEBUFFER = 0x8D40, GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE0 = 0x84C0;
    private static final int GL_BLEND = 0x0BE2, GL_ONE = 1, GL_ZERO = 0, GL_SRC_ALPHA = 0x0302, GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_DEPTH_TEST = 0x0B71, GL_CULL_FACE = 0x0B44, GL_TRIANGLE_STRIP = 0x0005, GL_LEQUAL = 0x0203, GL_TEXTURE_3D = 0x806F;

    private static final SmallProgram program = new SmallProgram("fire", SmallProgram.FLAT_VERTEX, FRAGMENT);

    /** This picture's fires: the squares that burn, then the hearths in sight, Fires.FLOATS each. */
    private float[] fires = new float[Fires.MOST * Fires.FLOATS * 2];
    private int count;
    private float[] far = new float[Fires.MOST * 2];
    private int[] order = new int[Fires.MOST * 2];
    /** For each of this picture's fires, the game's own animation of its flames and of its smoke, or null. */
    private Object[] flameLooks = new Object[Fires.MOST * 2], smokeLooks = new Object[Fires.MOST * 2];
    /** The lights for uFireAt, four floats each. */
    final float[] lights = new float[Fires.LIGHTS * 4];
    /** How far each of them reaches, in tiles. */
    final float[] reach = new float[Fires.LIGHTS];
    private final float[] weights = new float[Fires.LIGHTS];
    /** Which of them throw shadows, and the slots of their shadow maps. */
    final FireShadows shadows = new FireShadows();
    private final boolean[] flaming = new boolean[Fires.LIGHTS];
    private int frame;
    private final float[] box = new float[4];
    private final FireCards cards = new FireCards();
    /** Whether the log last said there were fires in sight. */
    private boolean inSight;
    private static final String[] FIRE_AT = new String[Fires.LIGHTS], FIRE_FROM = new String[FireShadows.MOST];

    static {
        for (int i = 0; i < Fires.LIGHTS; i++) {
            FIRE_AT[i] = "uFireAt[" + i + "]";
        }
        for (int i = 0; i < FireShadows.MOST; i++) {
            FIRE_FROM[i] = "uFireFrom[" + i + "]";
        }
    }

    /**
     * Takes this picture's fires, the squares the scene gathered and the hearths of the chunks
     * drawn, those in a room the eye's own picture leaves out left out too, and picks their lights.
     */
    void take(SkyState state, ArrayList<WorldScene.ChunkMeshes> drawn, RoomSight.Seen rooms, ViewCamera.Pose camera, long nanos) {
        count = 0;
        System.arraycopy(state.fires, 0, fires, 0, state.fireCount * Fires.FLOATS);
        System.arraycopy(state.flameLook, 0, flameLooks, 0, state.fireCount);
        System.arraycopy(state.smokeLook, 0, smokeLooks, 0, state.fireCount);
        count = state.fireCount;
        for (int chunk = 0; chunk < drawn.size(); chunk++) {
            Models.Instances placed = drawn.get(chunk).placed;
            for (int i = 0; i < placed.fireCount; i++) {
                if (!ChunkBuffers.listed(placed.fireRooms[i], rooms)) {
                    continue;
                }
                if ((count + 1) * Fires.FLOATS > fires.length) {
                    fires = java.util.Arrays.copyOf(fires, fires.length * 2);
                    far = java.util.Arrays.copyOf(far, far.length * 2);
                    order = java.util.Arrays.copyOf(order, order.length * 2);
                    flameLooks = java.util.Arrays.copyOf(flameLooks, flameLooks.length * 2);
                    smokeLooks = java.util.Arrays.copyOf(smokeLooks, smokeLooks.length * 2);
                }
                System.arraycopy(placed.fires, i * Fires.FLOATS, fires, count * Fires.FLOATS, Fires.FLOATS);
                flameLooks[count] = placed.fireLooks[i];
                smokeLooks[count] = placed.smokeLooks[i];
                count++;
            }
        }
        // The game lights round its own flames itself: the picture's light is for its own fire.
        Fires.lights(fires, Fires.own() ? count : 0, camera.eyeX, camera.eyeY, camera.eyeUp, seconds(nanos), lights, reach, flaming, weights);
        shadows.assign(lights, reach, weights, flaming, drawn, ++frame, nanos, Fires.own() ? FireShadows.slots() : 0, FireShadows.faceSize());
        if (DevBuild.ON && (count > 0) != inSight) {
            inSight = count > 0;
            Log.info(inSight ? "first person fires in sight: " + state.fireCount + " squares that burn or smoke, " + (count - state.fireCount) + " hearths"
                    : "first person fires: none in sight");
        }
    }

    /** The clock the flames move by, which starts again each hour so it keeps its fine steps. */
    static float seconds(long nanos) {
        return (nanos % 3_600_000_000_000L) / 1e9f;
    }

    /** Gives a program whose source has Fires.GLSL in it this picture's firelight. */
    void light(Program program) {
        for (int i = 0; i < Fires.LIGHTS; i++) {
            program.set(FIRE_AT[i], lights[i * 4], lights[i * 4 + 1], lights[i * 4 + 2], lights[i * 4 + 3]);
        }
        program.setEach("uFireReach", reach);
        program.set("uFireColor", Fires.RED, Fires.GREEN, Fires.BLUE);
    }

    /** Gives the world's program, whose source has FireShadows.GLSL in it, the fires' shadow maps on unit, which the picture has bound. */
    void lightWorld(Program program, int unit) {
        program.set("uFireShadow", unit);
        program.setEach("uFireSlot", shadows.slotOf);
        for (int slot = 0; slot < FireShadows.MOST; slot++) {
            program.set(FIRE_FROM[slot], shadows.from[slot * 4], shadows.from[slot * 4 + 1], shadows.from[slot * 4 + 2], shadows.from[slot * 4 + 3]);
        }
        program.set("uFireFace", shadows.face, Math.max(shadows.slotCount, 1));
        program.set("uFireNear", FireShadows.NEAR);
    }

    /**
     * The part of the picture a fire's box can cover, its flames' box of half width flameHalf and
     * height flameTop in it (0 for none), in the picture's own measure, into out as
     * least x, least y, most x, most y: the whole picture when some of its corners are at or
     * behind the near plane and some before it. False when the box is wholly behind the eye or off
     * the picture.
     */
    static boolean box(float[] fire, int at, float driftX, float driftY, float flameHalf, float flameTop, ViewCamera.Pose camera, float tanX, float tanY,
            float asideX, float asideY, float near, float[] out) {
        boolean window = fire[at + Fires.KIND] == Fires.WINDOW;
        float radius = fire[at + Fires.RADIUS], smoke = window ? 0f : fire[at + Fires.SMOKE];
        float ceiling = fire[at + Fires.CEILING];
        float reach = window ? fire[at + Fires.WIDE] : Math.max(radius * (smoke > 0f ? ceiling > 0f ? 8f : 4.4f : 2f), flameHalf);
        float top = window ? fire[at + Fires.HIGH] : Math.max(fire[at + Fires.TALL] * 1.3f + smoke, flameTop);
        if (ceiling > 0f) {
            top = Math.min(top, ceiling);
        }
        float loX = Math.min(-reach, -reach + driftX * smoke), hiX = Math.max(reach, reach + driftX * smoke);
        float loY = Math.min(-reach, -reach + driftY * smoke), hiY = Math.max(reach, reach + driftY * smoke);
        out[0] = out[1] = Float.MAX_VALUE;
        out[2] = out[3] = -Float.MAX_VALUE;
        int behind = 0;
        for (int corner = 0; corner < 8; corner++) {
            float x = fire[at + Fires.X] + ((corner & 1) == 0 ? loX : hiX) - camera.eyeX;
            float y = fire[at + Fires.Y] + ((corner & 2) == 0 ? loY : hiY) - camera.eyeY;
            float up = fire[at + Fires.UP] + ((corner & 4) == 0 ? 0f : top) - camera.eyeUp;
            float ahead = x * camera.forwardX + y * camera.forwardY + up * camera.forwardUp;
            if (ahead <= near) {
                behind++;
                continue;
            }
            float px = (x * camera.rightX + y * camera.rightY + up * camera.rightUp) / (ahead * tanX) - asideX;
            float py = (x * camera.upX + y * camera.upY + up * camera.upUp) / (ahead * tanY) - asideY;
            out[0] = Math.min(out[0], px);
            out[1] = Math.min(out[1], py);
            out[2] = Math.max(out[2], px);
            out[3] = Math.max(out[3], py);
        }
        if (behind == 8) {
            return false;
        }
        if (behind > 0) {
            // The box reaches round the eye: what of it is before the eye may be anywhere in the picture.
            out[0] = out[1] = -1f;
            out[2] = out[3] = 1f;
            return true;
        }
        out[0] = Math.max(out[0], -1f);
        out[1] = Math.max(out[1], -1f);
        out[2] = Math.min(out[2], 1f);
        out[3] = Math.min(out[3], 1f);
        return out[0] < out[2] && out[1] < out[3];
    }

    /** The light of whichever of the sun and the moon lights the sky, as this frame's fires are drawn in it. */
    private final float[] lightColour = new float[3];

    /**
     * Draws the fires into target, the picture's light with no depth of its own, behind what is
     * nearer the eye in depth, the picture's depth.
     */
    void draw(int target, int depth, SkyState state, ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far,
            int width, int height, float asideX, float asideY, long nanos, float exposure) {
        flamesShown = false;
        if (count == 0 || !program.use()) {
            return;
        }
        float tanX = tanHalfFieldOfView * aspect, tanY = tanHalfFieldOfView;
        // Many fires in sight, a block burning, are each marched in fewer steps, down to half.
        float crowd = Math.clamp((float) Math.sqrt(CROWD / (float) count), 0.5f, 1f);
        for (int i = 0; i < count; i++) {
            int at = i * Fires.FLOATS;
            float dx = fires[at + Fires.X] - camera.eyeX, dy = fires[at + Fires.Y] - camera.eyeY, du = fires[at + Fires.UP] + fires[at + Fires.TALL] * 0.5f - camera.eyeUp;
            this.far[i] = dx * dx + dy * dy + du * du;
            order[i] = i;
        }
        // The furthest first, so each nearer one is laid over it.
        for (int i = 1; i < count; i++) {
            int fire = order[i];
            int k = i - 1;
            while (k >= 0 && this.far[order[k]] < this.far[fire]) {
                order[k + 1] = order[k];
                k--;
            }
            order[k + 1] = fire;
        }
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL11.glViewport(0, 0, width, height);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glEnable(GL_BLEND);
        // Light given over what the smoke and flames leave of what is behind. The picture's alpha says how much a
        // surface mirrors, and what stands before it hides its mirroring as much: a wet road behind a fire shows the fire.
        // Into the second buffer, with frame blending on, a mark that what is there moves by itself.
        GL14.glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE_MINUS_SRC_ALPHA);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, depth);
        GL20.glUniform1i(program.at("uDepth"), 0);
        // Each sampler has a unit of its own while the loop is still being read too: two kinds of sampler on one unit
        // stop the draw (GL_INVALID_OPERATION), whether or not the march reaches them.
        GL20.glUniform1i(program.at("uFlames"), 1);
        GL20.glUniform1i(program.at("uHeats"), 2);
        GL20.glUniform1i(program.at("uNoise"), 3);
        GL20.glUniform2f(program.at("uSize"), width, height);
        GL20.glUniform2f(program.at("uTan"), tanX, tanY);
        GL20.glUniform2f(program.at("uAside"), asideX, asideY);
        GL20.glUniform1f(program.at("uNear"), near);
        GL20.glUniform1f(program.at("uFar"), far);
        GL20.glUniform3f(program.at("uRight"), camera.rightX, camera.rightY, camera.rightUp);
        GL20.glUniform3f(program.at("uUp"), camera.upX, camera.upY, camera.upUp);
        GL20.glUniform3f(program.at("uForward"), camera.forwardX, camera.forwardY, camera.forwardUp);
        GL20.glUniform1f(program.at("uTime"), seconds(nanos));
        GL20.glUniform1f(program.at("uStopped"), FireCards.stoppedDown(exposure));
        // Smoke drifts with the air: in a breeze of a tile a second, a tile across for each four it rises.
        float air = (float) Math.hypot(state.airX, state.airY);
        float carried = air * DRIFT > MOST_DRIFT ? MOST_DRIFT / air : DRIFT;
        float driftX = state.airX * carried, driftY = state.airY * carried;
        GL20.glUniform2f(program.at("uDrift"), driftX, driftY);
        drawnDriftX = driftX;
        drawnDriftY = driftY;
        drawnCrowd = crowd;
        float kept = state.daylightKept;
        GL20.glUniform3f(program.at("uAmbient"), (float) Math.pow(state.openRed, 2.2) * kept, (float) Math.pow(state.openGreen, 2.2) * kept,
                (float) Math.pow(state.openBlue, 2.2) * kept);
        GL20.glUniform3f(program.at("uLightWay"), state.lightX, state.lightY, state.lightUp);
        lightColour[0] = state.lightRed * kept;
        lightColour[1] = state.lightGreen * kept;
        lightColour[2] = state.lightBlue * kept;
        float seconds = seconds(nanos);
        boolean own = Fires.own();
        boolean drawn = cards.begin(depth, camera, tanHalfFieldOfView, aspect, near, far, width, height, asideX, asideY,
                (float) Math.pow(state.openRed, 2.2) * kept, (float) Math.pow(state.openGreen, 2.2) * kept, (float) Math.pow(state.openBlue, 2.2) * kept,
                exposure);
        FlameVolume.Loop loop = own ? flames() : null;
        boolean flames = loop != null;
        // Fire by fire, the furthest first, so a nearer fire's smoke stands before the flames of one behind it: the
        // picture's own flames and smoke, or a stove's glow, marched here, then its embers; or the game's own smoke
        // and flames, its frames stood up where they burn.
        for (int k = 0; k < count; k++) {
            int at = order[k] * Fires.FLOATS;
            if (own) {
                march(at, k, driftX, driftY, camera, tanX, tanY, asideX, asideY, near, crowd, loop, seconds, false);
                if (drawn && flames && fires[at + Fires.KIND] == Fires.VOLUME && fires[at + Fires.FLAME] > 0f) {
                    cards.embers(fires, at, camera, seconds);
                }
            } else if (drawn) {
                if (smokeLooks[order[k]] != null) {
                    cards.look(fires, at, (IsoAnim) smokeLooks[order[k]], true, camera, seconds);
                }
                // A shut hearth shows no flame: what the game attaches to it is behind its door.
                if (flameLooks[order[k]] != null && fires[at + Fires.FLAME] > 0f) {
                    cards.look(fires, at, (IsoAnim) flameLooks[order[k]], false, camera, seconds);
                }
            }
        }
        flamesShown = own ? flames : drawn;
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
    }

    /** How far the last draw's smoke was carried for each tile it rose, and how few steps its crowd of fires took: depth marches the same. */
    private float drawnDriftX, drawnDriftY, drawnCrowd = 1f;

    /** Whether the last draw had flames in the picture, which glass drawn after them must not cover where they stand before it. */
    private boolean flamesShown;

    boolean flamesShown() {
        return flamesShown;
    }

    /**
     * Writes the flames of the last draw into the depth of target, where they are more than a
     * glow, and nothing into its light: the window panes drawn next then stay behind a fire that
     * stands before them. The picture puts its own depth back after the panes, so nothing drawn
     * by depth later takes a flame for a surface.
     */
    void depth(int target, ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far, int width, int height, float asideX,
            float asideY, long nanos) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL11.glViewport(0, 0, width, height);
        GL11.glDisable(GL_BLEND);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glColorMask(false, false, false, false);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(true);
        float seconds = seconds(nanos);
        if (Fires.own()) {
            // The march's view is the one the draw left in the program, this same frame.
            FlameVolume.Loop loop = program.use() ? flames() : null;
            if (loop != null) {
                // Nor is the depth being written bound to be read.
                GL13.glActiveTexture(GL_TEXTURE0);
                GL11.glBindTexture(GL_TEXTURE_2D, Pipeline.none());
                float tanX = tanHalfFieldOfView * aspect, tanY = tanHalfFieldOfView;
                for (int k = 0; k < count; k++) {
                    march(order[k] * Fires.FLOATS, k, drawnDriftX, drawnDriftY, camera, tanX, tanY, asideX, asideY, near, drawnCrowd, loop, seconds, true);
                }
                GL20.glUniform1f(program.at("uDepthOnly"), 0f);
            }
        } else if (cards.begin(Pipeline.none(), camera, tanHalfFieldOfView, aspect, near, far, width, height, asideX, asideY, 0f, 0f, 0f, 1f)) {
            // The depth being written is not read: the fade at surfaces is the depth test's.
            cards.depthOnly(true);
            for (int k = 0; k < count; k++) {
                int at = order[k] * Fires.FLOATS;
                if (flameLooks[order[k]] != null && fires[at + Fires.FLAME] > 0f) {
                    cards.look(fires, at, (IsoAnim) flameLooks[order[k]], false, camera, seconds);
                }
            }
            cards.depthOnly(false);
        }
        GL11.glColorMask(true, true, true, true);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Marches the fire at `at`, k-th in order, where it has flames of the loop, or smoke, or is a
     * stove's glow; or, depthOnly, writes its flames into the picture's depth and nothing else.
     */
    private void march(int at, int k, float driftX, float driftY, ViewCamera.Pose camera, float tanX, float tanY, float asideX, float asideY,
            float near, float crowd, FlameVolume.Loop loop, float seconds, boolean depthOnly) {
        boolean volume = fires[at + Fires.KIND] == Fires.VOLUME;
        boolean flames = volume && loop != null && fires[at + Fires.FLAME] > 0f;
        if (flames) {
            cellSize(fires, at, loop, cell);
        } else {
            cell[0] = cell[1] = 0f;
        }
        float flameHalf = flames ? loop.filled() * cell[1] : 0f, flameTop = flames ? loop.filledHigh() * cell[0] : 0f;
        if (depthOnly && !flames || volume && !flames && fires[at + Fires.SMOKE] <= 0f
                || !box(fires, at, driftX, driftY, flameHalf, flameTop, camera, tanX, tanY, asideX, asideY, near, box)) {
            return;
        }
        program.use();
        if (!depthOnly) {
            GL14.glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE_MINUS_SRC_ALPHA);
        }
        float distance = (float) Math.sqrt(this.far[order[k]]);
        float steps = (STEPS_NEAR + (STEPS_FAR - STEPS_NEAR) * Math.min(distance / FEWEST_AT, 1f)) * crowd;
        float flameSteps = (FLAME_STEPS_NEAR + (FLAME_STEPS_FAR - FLAME_STEPS_NEAR) * Math.min(distance / FEWEST_AT, 1f)) * crowd;
        float seed = fires[at + Fires.SEED];
        GL20.glUniform1f(program.at("uDepthOnly"), depthOnly ? 1f : 0f);
        GL20.glUniform2f(program.at("uCellSize"), cell[1], cell[0]);
        float turn = FireCards.hash(seed * 3.7f) * 6.2832f;
        GL20.glUniform3f(program.at("uTurn"), (float) Math.cos(turn), (float) Math.sin(turn), FireCards.hash(seed * 9.1f) < 0.5f ? 1f : -1f);
        float played = flames ? played(loop, cell[0], seconds, seed) : 0f;
        GL20.glUniform1f(program.at("uFrame"), played % (flames ? loop.frames() : 1));
        // The stirring rises with the flame, and is the same after a whole repeat of the noise.
        float stirred = flames ? played * loop.rise() * STIR_REPEATS_A_CELL : 0f;
        GL20.glUniform3f(program.at("uStir"), STIR, STIR_REPEATS_A_CELL, stirred - (float) Math.floor(stirred));
        GL20.glUniform1f(program.at("uFlameSteps"), Math.round(flameSteps));
        GL20.glUniform2f(program.at("uFlameBox"), flameHalf, flameTop);
        GL20.glUniform3f(program.at("uFoot"), fires[at + Fires.X] - camera.eyeX, fires[at + Fires.Y] - camera.eyeY, fires[at + Fires.UP] - camera.eyeUp);
        GL20.glUniform1f(program.at("uRadius"), fires[at + Fires.RADIUS]);
        GL20.glUniform1f(program.at("uTall"), fires[at + Fires.TALL]);
        GL20.glUniform1f(program.at("uSmoke"), fires[at + Fires.SMOKE]);
        GL20.glUniform1f(program.at("uFlame"), volume && !flames ? 0f : fires[at + Fires.FLAME]);
        GL20.glUniform1f(program.at("uThick"), fires[at + Fires.THICK]);
        GL20.glUniform1f(program.at("uSeed"), fires[at + Fires.SEED]);
        GL20.glUniform1f(program.at("uKind"), fires[at + Fires.KIND]);
        GL20.glUniform4f(program.at("uWindow"), fires[at + Fires.ALONG_X], fires[at + Fires.ALONG_Y], fires[at + Fires.OUT_X], fires[at + Fires.OUT_Y]);
        GL20.glUniform2f(program.at("uWindowSize"), fires[at + Fires.WIDE], fires[at + Fires.HIGH]);
        GL20.glUniform1f(program.at("uSteps"), Math.round(steps));
        GL20.glUniform1f(program.at("uCeiling"), fires[at + Fires.CEILING]);
        // Under a ceiling no sun or moon reaches the smoke.
        float sunlit = fires[at + Fires.CEILING] > 0f ? 0f : 1f;
        GL20.glUniform3f(program.at("uLightColour"), lightColour[0] * sunlit, lightColour[1] * sunlit, lightColour[2] * sunlit);
        GL11.glBegin(GL_TRIANGLE_STRIP);
        GL11.glVertex2f(box[0], box[1]);
        GL11.glVertex2f(box[2], box[1]);
        GL11.glVertex2f(box[0], box[3]);
        GL11.glVertex2f(box[2], box[3]);
        GL11.glEnd();
    }

    private final float[] cell = new float[2];

    /**
     * How big a cell of the flames' loop is drawn for the fire at `at`, in tiles, into out as up
     * and then across: as high as makes the loop's flames reach as high as the fire's, on the
     * mean, and as wide as makes their root as wide as the fire, but never stretched or squeezed
     * by more than WIDEST or NARROWEST.
     */
    static void cellSize(float[] fire, int at, FlameVolume.Loop loop, float[] out) {
        float up = fire[at + Fires.TALL] / (loop.reach() * loop.high());
        out[0] = up;
        out[1] = Math.clamp(2f * fire[at + Fires.RADIUS] / (loop.root() * loop.wide()), up * NARROWEST, up * WIDEST);
    }

    /**
     * How many frames of the loop a fire has played at seconds: a bigger fire's flames move
     * slower, by the square root of how much bigger than the simulation's it is drawn, as a fire's
     * do; and each fire starts at a place in the loop of its own. The picture's tiles are taken as
     * metres, as the eye's height is (FirstPerson.EYE_HEIGHT_STANDING). The frame it shows is this
     * round the loop.
     */
    static float played(FlameVolume.Loop loop, float cellUp, float seconds, float seed) {
        float bigger = cellUp / loop.cell();
        return seconds * loop.framesASecond() / (float) Math.sqrt(bigger) + FireCards.hash(seed * 5.9f) * loop.frames();
    }

    /**
     * The glow of the flames' gas from cold to hottest in sixteen steps, three floats each: a black
     * body's colour from COLDEST to HOTTEST kelvin, at the picture's light by GLOW and its heat to
     * the power GLOW_POWER.
     */
    static float[] glowRamp() {
        float[] ramp = new float[16 * 3];
        for (int i = 0; i < 16; i++) {
            float heat = i / 15f;
            blackbody(COLDEST + (HOTTEST - COLDEST) * heat, ramp, i * 3);
            float light = GLOW * (float) Math.pow(heat, GLOW_POWER);
            for (int c = 0; c < 3; c++) {
                ramp[i * 3 + c] *= light;
            }
        }
        return ramp;
    }

    /**
     * The colour a black body glows at kelvin, its brightest channel 1, in linear light: Tanner
     * Helland's fit to the Planckian locus in sRGB, which holds from 1000 K up and is near enough below.
     */
    static void blackbody(float kelvin, float[] out, int at) {
        double t = kelvin / 100.0;
        double r = t <= 66 ? 255 : 329.698727446 * Math.pow(t - 60, -0.1332047592);
        double g = t <= 66 ? 99.4708025861 * Math.log(t) - 161.1195681661 : 288.1221695283 * Math.pow(t - 60, -0.0755148492);
        double b = t >= 66 ? 255 : t <= 19 ? 0 : 138.5177312231 * Math.log(t - 10) - 305.0447927307;
        double[] c = {r, g, b};
        double most = 0;
        for (int i = 0; i < 3; i++) {
            c[i] = Math.pow(Math.clamp(c[i] / 255.0, 0.0, 1.0), 2.2);
            most = Math.max(most, c[i]);
        }
        for (int i = 0; i < 3; i++) {
            out[at + i] = (float) (c[i] / most);
        }
    }

    /** The flames' loop where the picture draws its own fire, its textures bound for the fire program; null until it has been read. */
    private FlameVolume.Loop flames() {
        FlameVolume.Loop loop = FlameVolume.ready();
        if (loop == null) {
            return null;
        }
        program.use();
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_3D, FlameVolume.flameTexture());
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_3D, FlameVolume.heatTexture());
        GL20.glUniform3f(program.at("uCells"), loop.wide(), loop.deep(), loop.high());
        GL20.glUniform2f(program.at("uLaid"), loop.across(), loop.down());
        GL20.glUniform1f(program.at("uRise"), loop.rise());
        GL20.glUniform1f(program.at("uTakes"), TAKES);
        GL20.glUniform1f(program.at("uFlameStride"), FLAME_STRIDE);
        GL20.glUniform2f(program.at("uEdge"), EDGE_FROM, EDGE_TO);
        GL13.glActiveTexture(GL_TEXTURE0 + 3);
        GL11.glBindTexture(GL_TEXTURE_3D, Pipeline.noise());
        GL13.glActiveTexture(GL_TEXTURE0);
        for (int i = 0; i < 16; i++) {
            GL20.glUniform3f(program.at(GLOW_AT[i]), GLOW_RAMP[i * 3], GLOW_RAMP[i * 3 + 1], GLOW_RAMP[i * 3 + 2]);
        }
        return loop;
    }

    private static final float[] GLOW_RAMP = glowRamp();
    private static final String[] GLOW_AT = new String[16];

    static {
        for (int i = 0; i < 16; i++) {
            GLOW_AT[i] = "uGlow[" + i + "]";
        }
    }
}

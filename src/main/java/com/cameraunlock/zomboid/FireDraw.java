package com.cameraunlock.zomboid;

import java.util.ArrayList;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

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

            // At q, from the fire's foot in world axes: x how much flame there is, y how thick the smoke.
            vec2 fireAt(vec3 q) {
                float h = q.z;
                vec2 there = vec2(0.0);
                // Noise is asked only within the reach of the flames and of the smoke: most of a fire's box is clear air.
                if (uFlame > 0.0 && h < uTall * 1.3 && dot(q.xy, q.xy) < uRadius * uRadius * 1.96) {
                    float hn = clamp(h / uTall, 0.0, 1.0);
                    vec3 p = vec3(q.xy / uRadius * 1.6, hn * 4.0) + vec3(uSeed, uSeed * 0.7, -uTime * 2.6);
                    // The flames sway more the higher they reach, and narrow to tongues.
                    vec2 sway = (vec2(noise(p * 0.7), noise(p * 0.7 + 17.0)) - 0.5) * (1.1 * hn);
                    float wide = uRadius * (1.0 - hn * hn * 0.85) * (0.75 + 0.25 * smoothstep(0.0, 0.15, hn));
                    float r = length(q.xy / wide - sway);
                    float lick = fbm(p);
                    float body = clamp(1.0 - r, 0.0, 1.0) * clamp(lick * 1.7 - hn * 0.95 + 0.15, 0.0, 1.0);
                    there.x = body * (1.0 - smoothstep(0.85, 1.3, h / uTall));
                }
                if (uSmoke > 0.0 && h > uTall * 0.45) {
                    float rise = (h - uTall * 0.45) / uSmoke;
                    vec2 level = q.xy - uDrift * (h - uTall * 0.45);
                    float wide = uRadius * (0.8 + 2.2 * sqrt(max(rise, 0.0)));
                    if (rise < 1.0 && dot(level, level) < wide * wide * 1.21) {
                        vec3 p = vec3(level / wide * 1.3, rise * 4.0) + vec3(uSeed * 1.3, uSeed * 0.4, -uTime * 0.45);
                        float puff = fbm(p);
                        float r = length(level / wide);
                        there.y = clamp(puff * 1.6 - r - 0.25, 0.0, 1.0) * smoothstep(0.0, 0.12, rise) * (1.0 - smoothstep(0.55, 1.0, rise));
                    }
                }
                return there;
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                float z = texture2D(uDepth, uv).r * 2.0 - 1.0;
                float sceneAhead = 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
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
                    // The door's face is 1.5 mm behind the glow and the grille's bars 2.5 mm before it,
                    // along the way out: the glow shows where what the picture has there is no nearer
                    // than a millimetre behind it, however slant it is seen, and the depth's own
                    // steps, which grow with the square of the distance, are allowed for.
                    if (ahead <= uNear || ahead > sceneAhead + 0.001 / -facing + ahead * ahead * 0.00002) {
                        discard;
                    }
                    vec3 q = ray * ahead - uFoot;
                    vec2 on = vec2(dot(q.xy, uWindow.xy) / uWindowSize.x, q.z / uWindowSize.y);
                    if (abs(on.x) > 1.0 || on.y < 0.0 || on.y > 1.0) {
                        discard;
                    }
                    float stir = fbm(vec3(on * vec2(3.0, 2.5) + uSeed, uTime * 1.3 - on.y * 1.5));
                    float heat = clamp(stir * 1.4 - on.y * 0.45 + 0.05, 0.0, 1.0);
                    float edge = smoothstep(1.0, 0.8, abs(on.x)) * smoothstep(0.0, 0.08, on.y) * smoothstep(1.0, 0.9, on.y);
                    gl_FragData[0] = vec4(flameColour(heat) * (heat * 4.0 * edge * uFlame), 0.0);
                    gl_FragData[1] = vec4(1.0);
                    return;
                }
                // The box the fire is in: see FireDraw.box, which is the same.
                float reach = uRadius * (uSmoke > 0.0 ? 4.4 : 1.4);
                vec2 drift = uDrift * uSmoke;
                vec3 lo = vec3(min(vec2(-reach), vec2(-reach) + drift), 0.0);
                vec3 hi = vec3(max(vec2(reach), vec2(reach) + drift), uTall * 1.3 + uSmoke);
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
                float dt = (leave - enter) / uSteps;
                float t = enter + dt * hash(vec3(gl_FragCoord.xy, fract(uTime * 7.31) * 61.0));
                float span = length(ray) * dt;
                vec3 light = vec3(0.0);
                float through = 1.0;
                for (int i = 0; i < 48; i++) {
                    if (float(i) >= uSteps || through < 0.02) {
                        break;
                    }
                    vec3 q = from + ray * t;
                    vec2 here = fireAt(q);
                    if (here.x > 0.0) {
                        float heat = here.x * (1.25 - clamp(q.z / uTall, 0.0, 1.0) * 0.5);
                        light += through * flameColour(heat) * (here.x * uFlame * 9.0 * span / uRadius);
                        through *= exp(-here.x * 1.2 * span / uRadius);
                    }
                    if (here.y > 0.0) {
                        float dense = here.y * uThick * span;
                        // Lit from below by the fire, less the higher it has risen, and by the light round it.
                        float glowUp = exp(-max(q.z - uTall, 0.0) * 1.2 / uTall);
                        vec3 lit = uAmbient * 0.35 + vec3(1.0, 0.42, 0.12) * (uFlame * 1.6 * glowUp);
                        light += through * lit * (1.0 - exp(-dense));
                        through *= exp(-dense);
                    }
                    t += dt;
                }
                if (through > 0.995 && dot(light, light) < 1e-6) {
                    discard;
                }
                gl_FragData[0] = vec4(light, 1.0 - through);
                // It moves by itself: the steadying keeps little of a frame before it (PipelineShaders.STEADY).
                gl_FragData[1] = vec4(1.0);
            }
            """;

    private static final int GL_FRAMEBUFFER = 0x8D40, GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE0 = 0x84C0;
    private static final int GL_BLEND = 0x0BE2, GL_ONE = 1, GL_ZERO = 0, GL_SRC_ALPHA = 0x0302, GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_DEPTH_TEST = 0x0B71, GL_CULL_FACE = 0x0B44, GL_TRIANGLE_STRIP = 0x0005;

    private static final SmallProgram program = new SmallProgram("fire", SmallProgram.FLAT_VERTEX, FRAGMENT);

    /** This picture's fires: the squares that burn, then the hearths in sight, Fires.FLOATS each. */
    private float[] fires = new float[Fires.MOST * Fires.FLOATS * 2];
    private int count;
    private float[] far = new float[Fires.MOST * 2];
    private int[] order = new int[Fires.MOST * 2];
    /** The lights for uFireAt, four floats each. */
    final float[] lights = new float[Fires.LIGHTS * 4];
    private final float[] weights = new float[Fires.LIGHTS];
    private final float[] box = new float[4];
    /** Whether the log last said there were fires in sight. */
    private boolean inSight;
    private static final String[] FIRE_AT = new String[Fires.LIGHTS];

    static {
        for (int i = 0; i < Fires.LIGHTS; i++) {
            FIRE_AT[i] = "uFireAt[" + i + "]";
        }
    }

    /**
     * Takes this picture's fires, the squares the scene gathered and the hearths of the chunks
     * drawn, those in a room the eye's own picture leaves out left out too, and picks their lights.
     */
    void take(SkyState state, ArrayList<WorldScene.ChunkMeshes> drawn, RoomSight.Seen rooms, ViewCamera.Pose camera, long nanos) {
        count = 0;
        if (!Fires.own()) {
            Fires.lights(fires, 0, 0f, 0f, 0f, 0f, lights, weights);
            return;
        }
        System.arraycopy(state.fires, 0, fires, 0, state.fireCount * Fires.FLOATS);
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
                }
                System.arraycopy(placed.fires, i * Fires.FLOATS, fires, count * Fires.FLOATS, Fires.FLOATS);
                count++;
            }
        }
        Fires.lights(fires, count, camera.eyeX, camera.eyeY, camera.eyeUp, seconds(nanos), lights, weights);
        if ((count > 0) != inSight) {
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
        program.set("uFireColor", Fires.RED, Fires.GREEN, Fires.BLUE);
    }

    /**
     * The part of the picture a fire's box can cover, in the picture's own measure, into out as
     * least x, least y, most x, most y: the whole picture when some of its corners are at or
     * behind the near plane and some before it. False when the box is wholly behind the eye or off
     * the picture.
     */
    static boolean box(float[] fire, int at, float driftX, float driftY, ViewCamera.Pose camera, float tanX, float tanY, float asideX, float asideY,
            float near, float[] out) {
        boolean window = fire[at + Fires.KIND] == Fires.WINDOW;
        float radius = fire[at + Fires.RADIUS], smoke = window ? 0f : fire[at + Fires.SMOKE];
        float reach = window ? fire[at + Fires.WIDE] : radius * (smoke > 0f ? 4.4f : 1.4f);
        float top = window ? fire[at + Fires.HIGH] : fire[at + Fires.TALL] * 1.3f + smoke;
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

    /**
     * Draws the fires into target, the picture's light with no depth of its own, behind what is
     * nearer the eye in depth, the picture's depth.
     */
    void draw(int target, int depth, SkyState state, ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far,
            int width, int height, float asideX, float asideY, long nanos) {
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
        GL20.glUniform2f(program.at("uSize"), width, height);
        GL20.glUniform2f(program.at("uTan"), tanX, tanY);
        GL20.glUniform2f(program.at("uAside"), asideX, asideY);
        GL20.glUniform1f(program.at("uNear"), near);
        GL20.glUniform1f(program.at("uFar"), far);
        GL20.glUniform3f(program.at("uRight"), camera.rightX, camera.rightY, camera.rightUp);
        GL20.glUniform3f(program.at("uUp"), camera.upX, camera.upY, camera.upUp);
        GL20.glUniform3f(program.at("uForward"), camera.forwardX, camera.forwardY, camera.forwardUp);
        GL20.glUniform1f(program.at("uTime"), seconds(nanos));
        // Smoke drifts with the air: in a breeze of a tile a second, a tile across for each four it rises.
        float air = (float) Math.hypot(state.airX, state.airY);
        float carried = air * DRIFT > MOST_DRIFT ? MOST_DRIFT / air : DRIFT;
        float driftX = state.airX * carried, driftY = state.airY * carried;
        GL20.glUniform2f(program.at("uDrift"), driftX, driftY);
        float kept = state.daylightKept;
        GL20.glUniform3f(program.at("uAmbient"), (float) Math.pow(state.openRed, 2.2) * kept, (float) Math.pow(state.openGreen, 2.2) * kept,
                (float) Math.pow(state.openBlue, 2.2) * kept);
        for (int k = 0; k < count; k++) {
            int at = order[k] * Fires.FLOATS;
            if (!box(fires, at, driftX, driftY, camera, tanX, tanY, asideX, asideY, near, box)) {
                continue;
            }
            float distance = (float) Math.sqrt(this.far[order[k]]);
            float steps = (STEPS_NEAR + (STEPS_FAR - STEPS_NEAR) * Math.min(distance / FEWEST_AT, 1f)) * crowd;
            GL20.glUniform3f(program.at("uFoot"), fires[at + Fires.X] - camera.eyeX, fires[at + Fires.Y] - camera.eyeY, fires[at + Fires.UP] - camera.eyeUp);
            GL20.glUniform1f(program.at("uRadius"), fires[at + Fires.RADIUS]);
            GL20.glUniform1f(program.at("uTall"), fires[at + Fires.TALL]);
            GL20.glUniform1f(program.at("uSmoke"), fires[at + Fires.SMOKE]);
            GL20.glUniform1f(program.at("uFlame"), fires[at + Fires.FLAME]);
            GL20.glUniform1f(program.at("uThick"), fires[at + Fires.THICK]);
            GL20.glUniform1f(program.at("uSeed"), fires[at + Fires.SEED]);
            GL20.glUniform1f(program.at("uKind"), fires[at + Fires.KIND]);
            GL20.glUniform4f(program.at("uWindow"), fires[at + Fires.ALONG_X], fires[at + Fires.ALONG_Y], fires[at + Fires.OUT_X], fires[at + Fires.OUT_Y]);
            GL20.glUniform2f(program.at("uWindowSize"), fires[at + Fires.WIDE], fires[at + Fires.HIGH]);
            GL20.glUniform1f(program.at("uSteps"), Math.round(steps));
            GL11.glBegin(GL_TRIANGLE_STRIP);
            GL11.glVertex2f(box[0], box[1]);
            GL11.glVertex2f(box[2], box[1]);
            GL11.glVertex2f(box[0], box[3]);
            GL11.glVertex2f(box[2], box[3]);
            GL11.glEnd();
        }
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
    }
}

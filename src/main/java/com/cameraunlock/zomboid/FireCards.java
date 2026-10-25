package com.cameraunlock.zomboid;

import java.util.ArrayList;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import zombie.core.textures.Texture;
import zombie.iso.IsoDirections;
import zombie.iso.sprite.IsoAnim;
import zombie.iso.sprite.IsoDirectionFrame;

/**
 * What of a fire is drawn on cards rather than marched (FireDraw): the embers that fly up from
 * its flames, and, where the picture draws the game's fire and not its own, the game's frames of
 * its flames and smoke stood up where it burns. Where a card passes into the ground or a wall it
 * fades over a short distance instead of being cut.
 *
 * Render thread only.
 */
final class FireCards {
    /** Over how many of a fire's radii a card fades where it meets a surface. */
    static final float SOFT = 0.35f;
    /** How bright the game's frames of flames are at their brightest, in the picture's light: the sun lights a white wall to about 2. */
    static final float BRIGHTEST = 8f;

    /**
     * The game's frames of flames and smoke: the pixel of a frame the fire stands on, the foot of
     * its flames (every frame's run down to row 254 of 256), and how far a pixel is in tiles across
     * and up: a frame 128 pixels wide spans a square's diagonal, and 192 pixels up is a level. The
     * game shows them at about 15 frames a second, at 0.7 of whole (IsoFire's AttachAnim).
     */
    static final float LOOK_FOOT_X = 64f, LOOK_FOOT_Y = 250f, LOOK_WIDE_PER_PIXEL = 1.4142f / 128f, LOOK_HIGH_PER_PIXEL = ViewCamera.LEVEL_HEIGHT / 192f;
    static final float LOOK_FRAMES_A_SECOND = 15f, LOOK_ALPHA = 0.7f;
    /** How much of the angle the eye looks down at a fire its frames lean back from the eye. */
    static final float LEAN = 0.6f;

    /** How many embers a fire throws up at once at its height, and how long each flies, in seconds. */
    static final int EMBERS = 18;
    static final float EMBER_LIFE = 2.2f;
    /** How fast an ember rises, in tiles a second, how wide it is, and how bright at its hottest. */
    static final float EMBER_RISE = 1.6f, EMBER_SIZE = 0.012f, EMBER_BRIGHT = 14f;
    /** How long a streak an ember draws, in seconds of its flight: the eye's own blur of a bright moving point. */
    static final float EMBER_STREAK = 0.035f;

    private static final String VERTEX = """
            #version 120
            uniform vec3 uForward;
            varying vec2 vPlace;
            varying float vAhead;
            varying vec4 vColor;
            void main() {
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                vPlace = gl_MultiTexCoord0.xy;
                vAhead = dot(gl_Vertex.xyz, uForward);
                vColor = gl_Color;
            }
            """;

    private static final String FRAGMENT = """
            #version 120
            uniform sampler2D uDepth;
            uniform vec2 uSize;
            uniform float uNear;
            uniform float uFar;
            // How strongly a frame is drawn and the fade's reach in tiles.
            uniform float uStrength;
            uniform float uSoft;
            uniform float uBrightest;
            // 1 an ember, 2 a frame of the game's own flames, 3 of its smoke, and how bright an ember at its hottest is.
            uniform float uKind;
            uniform float uEmberBright;
            // The game's frame, and the light its smoke is lit by.
            uniform sampler2D uSprite;
            uniform vec3 uAmbient;
            // 1 while the flames are put in the picture's depth and nothing in its light.
            uniform float uDepthOnly;
            varying vec2 vPlace;
            varying float vAhead;
            varying vec4 vColor;

            void main() {
                // The depth pass has the picture's depth bound to write, not to read: the depth test stands for the fade.
                float before = 1e6;
                if (uDepthOnly < 0.5) {
                    float sceneZ = texture2D(uDepth, gl_FragCoord.xy / uSize).r * 2.0 - 1.0;
                    float sceneAhead = 2.0 * uNear * uFar / (uFar + uNear - sceneZ * (uFar - uNear));
                    before = sceneAhead - vAhead;
                    if (before <= 0.0) {
                        discard;
                    }
                }
                float fade = smoothstep(0.0, uSoft, before) * smoothstep(uNear, uNear + 0.25, vAhead);
                if (uKind > 1.5) {
                    vec4 texel = texture2D(uSprite, vPlace);
                    float a = texel.a * uStrength * fade;
                    if (a < 0.01 || uDepthOnly > 0.5 && a < 0.5) {
                        discard;
                    }
                    // The game's flames are light, and where they are bright they hide what is behind them: drawn as
                    // light alone the grass shows through their dimmer parts. Their dark fringes stay light alone, as
                    // the game draws them, or they would be black. Its smoke is its sprite's grey, lit by the air round
                    // it and never quite black.
                    vec3 light;
                    float cover;
                    if (uKind < 2.5) {
                        // The game's frames are near enough grey, pale yellow at most: coloured here by how bright
                        // each part is, the dim fringes red, then orange, the core yellow white.
                        float l = dot(texel.rgb, vec3(0.3, 0.5, 0.2));
                        vec3 c = mix(vec3(0.8, 0.07, 0.0), vec3(1.0, 0.32, 0.03), smoothstep(0.1, 0.5, l));
                        c = mix(c, vec3(1.0, 0.6, 0.16), smoothstep(0.5, 0.95, l));
                        light = c * (pow(l, 1.5) * a * uBrightest);
                        cover = a * smoothstep(0.3, 0.75, l) * 0.9;
                    } else {
                        light = pow(texel.rgb, vec3(2.2)) * 8.0 * (uAmbient + vec3(0.05)) * a;
                        cover = clamp(a * 1.5, 0.0, 1.0);
                    }
                    gl_FragData[0] = vec4(light, cover);
                    gl_FragData[1] = vec4(vec3(0.375), 1.0);
                    return;
                }
                float r = length(vPlace);
                float spot = 1.0 - smoothstep(0.2, 1.0, r);
                if (spot <= 0.0) {
                    discard;
                }
                gl_FragData[0] = vec4(vColor.rgb * (spot * uEmberBright), 0.0);
                gl_FragData[1] = vec4(vec3(0.375), 1.0);
            }
            """;

    private static final int GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE0 = 0x84C0;
    private static final int GL_QUADS = 0x0007, GL_PROJECTION = 0x1701, GL_MODELVIEW = 0x1700;
    private static final int GL_ONE = 1, GL_ONE_MINUS_SRC_ALPHA = 0x0303, GL_BLEND = 0x0BE2;

    static final SmallProgram program = new SmallProgram("fire cards", VERTEX, FRAGMENT);

    private final float[] projection = new float[16], turned = new float[16];
    private final ViewCamera.Pose atEye = new ViewCamera.Pose();
    private final float[] at = new float[4], before = new float[4];
    /** A pixel of the picture begun, in tiles for each tile ahead of the eye: the least an ember is drawn across. */
    private float pixelPerTile;

    /**
     * Readies the program for drawing into the picture bound, behind what is nearer the eye in
     * depth, the picture's depth, the game's smoke lit by ambient: false where the driver refused
     * the program. The camera's matrices are loaded, turned but not moved: every point is given
     * from the eye.
     */
    boolean begin(int depth, ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far, int width, int height,
            float asideX, float asideY, float ambientRed, float ambientGreen, float ambientBlue, float exposure) {
        if (!program.use()) {
            return false;
        }
        GL11.glMatrixMode(GL_PROJECTION);
        ViewCamera.projection(tanHalfFieldOfView, aspect, near, far, projection);
        projection[8] += asideX;
        projection[9] += asideY;
        GL11.glLoadMatrixf(projection);
        GL11.glMatrixMode(GL_MODELVIEW);
        atEye.set(camera);
        atEye.eyeX = atEye.eyeY = atEye.eyeUp = 0f;
        ViewCamera.view(atEye, turned);
        GL11.glLoadMatrixf(turned);
        pixelPerTile = pixelPerTile(tanHalfFieldOfView, height);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, depth);
        GL20.glUniform1i(program.at("uDepth"), 0);
        GL20.glUniform1i(program.at("uSprite"), 2);
        GL20.glUniform2f(program.at("uSize"), width, height);
        GL20.glUniform1f(program.at("uNear"), near);
        GL20.glUniform1f(program.at("uFar"), far);
        float stopped = stoppedDown(exposure);
        GL20.glUniform1f(program.at("uBrightest"), BRIGHTEST * stopped);
        GL20.glUniform1f(program.at("uEmberBright"), EMBER_BRIGHT * stopped);
        GL20.glUniform3f(program.at("uForward"), camera.forwardX, camera.forwardY, camera.forwardUp);
        GL20.glUniform3f(program.at("uAmbient"), ambientRed, ambientGreen, ambientBlue);
        GL20.glUniform1f(program.at("uDepthOnly"), 0f);
        return true;
    }

    /**
     * How much of their light fire is drawn with at a picture's exposure (WorldScene.adaptEye).
     * The eye opens up at night by several times, and flames as bright as they are by day went
     * past white into flat blobs: the eye stops down again for a fire in view. At the exposure of
     * a lit day, DAY_EXPOSURE, and below, all of it; above, less, by the power STOPS_DOWN.
     */
    static float stoppedDown(float exposure) {
        return exposure <= DAY_EXPOSURE ? 1f : (float) Math.pow(DAY_EXPOSURE / exposure, STOPS_DOWN);
    }

    static final float DAY_EXPOSURE = 0.5f, STOPS_DOWN = 0.85f;

    /** While on, what is drawn goes into the picture's depth alone, where the flames are more than a glow. */
    void depthOnly(boolean on) {
        program.use();
        GL20.glUniform1f(program.at("uDepthOnly"), on ? 1f : 0f);
    }

    /**
     * A card of the game's own frames of a fire's flames, or of its smoke, as the game paints
     * them for its view from above, stood up where the fire burns, facing the eye about the up:
     * the frame's foot at the fire's foot, at the scale the game draws them (Fires.LOOK). Its
     * frames go round at the game's pace.
     */
    void look(float[] fires, int f, IsoAnim anim, boolean smoke, ViewCamera.Pose camera, float seconds) {
        ArrayList<IsoDirectionFrame> frames = anim.frames;
        if (frames.isEmpty()) {
            return;
        }
        float seed = fires[f + Fires.SEED];
        int shown = (int) Math.floor(seconds * LOOK_FRAMES_A_SECOND + seed * 7f) % frames.size();
        Texture frame = frames.get(shown).getTexture(IsoDirections.N);
        if (frame == null || frame.getID() <= 0) {
            return;
        }
        program.use();
        GL11.glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        GL30.glDisablei(GL_BLEND, 1);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, frame.getID());
        GL13.glActiveTexture(GL_TEXTURE0);
        float x = fires[f + Fires.X] - camera.eyeX, y = fires[f + Fires.Y] - camera.eyeY, up = fires[f + Fires.UP] - camera.eyeUp;
        float level = (float) Math.hypot(x, y);
        // The card faces the eye about the up: across it is level and square to the line from the eye. Over the
        // fire's very middle that line is straight down, and across is the view's own right.
        float acrossX = level > 1e-3f ? -y / level : camera.rightX, acrossY = level > 1e-3f ? x / level : camera.rightY;
        float acrossLength = (float) Math.hypot(acrossX, acrossY);
        acrossX /= acrossLength;
        acrossY /= acrossLength;
        // Seen from above, it leans back from the eye about its foot by LEAN of the angle the eye looks down at
        // it, as the game's own frames face its view from above: stood straight it is a sheet seen edge on.
        float lean = LEAN * (float) Math.atan2(Math.max(-up, 0f), level);
        float awayX = level > 1e-3f ? x / level : camera.forwardX, awayY = level > 1e-3f ? y / level : camera.forwardY;
        float awayLength = Math.max((float) Math.hypot(awayX, awayY), 1e-6f), sinLean = (float) Math.sin(lean);
        float tipX = awayX / awayLength * sinLean, tipY = awayY / awayLength * sinLean;
        float tipUp = (float) Math.cos(lean);
        float left = frame.getOffsetX(), top = frame.getOffsetY(), wide = frame.getWidth(), high = frame.getHeight();
        float scale = fires[f + Fires.LOOK];
        float from = (left - LOOK_FOOT_X) * LOOK_WIDE_PER_PIXEL * scale, to = (left + wide - LOOK_FOOT_X) * LOOK_WIDE_PER_PIXEL * scale;
        float upper = (LOOK_FOOT_Y - top) * LOOK_HIGH_PER_PIXEL * scale, lower = (LOOK_FOOT_Y - top - high) * LOOK_HIGH_PER_PIXEL * scale;
        GL20.glUniform1f(program.at("uKind"), smoke ? 3f : 2f);
        GL20.glUniform1f(program.at("uStrength"), LOOK_ALPHA);
        GL20.glUniform1f(program.at("uSoft"), Math.max(fires[f + Fires.RADIUS], 0.15f) * SOFT);
        float u0 = frame.getXStart(), u1 = frame.getXEnd(), v0 = frame.getYStart(), v1 = frame.getYEnd();
        GL11.glBegin(GL_QUADS);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex3f(x + acrossX * from + tipX * lower, y + acrossY * from + tipY * lower, up + tipUp * lower);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex3f(x + acrossX * to + tipX * lower, y + acrossY * to + tipY * lower, up + tipUp * lower);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex3f(x + acrossX * to + tipX * upper, y + acrossY * to + tipY * upper, up + tipUp * upper);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex3f(x + acrossX * from + tipX * upper, y + acrossY * from + tipY * upper, up + tipUp * upper);
        GL11.glEnd();
        GL30.glEnablei(GL_BLEND, 1);
    }

    /**
     * Draws the embers of the Fires record at f, once begin has readied the program: light added
     * to the picture, and the second buffer, with frame blending on, given over whatever it held
     * the mark that what is there moves by itself.
     */
    void embers(float[] fires, int f, ViewCamera.Pose camera, float seconds) {
        float radius = fires[f + Fires.RADIUS], tall = fires[f + Fires.TALL], seed = fires[f + Fires.SEED];
        int embers = Math.round(EMBERS * Math.min(tall / 2.2f, 1f) * fires[f + Fires.FLAME]);
        if (embers == 0) {
            return;
        }
        program.use();
        GL14.glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        GL30.glDisablei(GL_BLEND, 1);
        float x = fires[f + Fires.X] - camera.eyeX, y = fires[f + Fires.Y] - camera.eyeY, up = fires[f + Fires.UP] - camera.eyeUp;
        GL20.glUniform1f(program.at("uKind"), 1f);
        GL11.glBegin(GL_QUADS);
        for (int i = 0; i < embers; i++) {
            ember(i, seed, seconds, radius, tall, at);
            if (at[3] <= 0.01f) {
                continue;
            }
            ember(i, seed, seconds - EMBER_STREAK, radius, tall, before);
            float hx = x + at[1], hy = y + at[2], hu = up + at[0];
            // Streaked from where it was a moment ago, and as wide as it is across the line of sight; one just thrown up is a point.
            float sx = at[1] - before[1], sy = at[2] - before[2], su = at[0] - before[0];
            if (before[3] <= 0.01f) {
                sx = sy = 0f;
                su = 0.001f;
            }
            float wx = sy * hu - su * hy, wy = su * hx - sx * hu, wu = sx * hy - sy * hx;
            float wl = (float) Math.sqrt(wx * wx + wy * wy + wu * wu);
            if (wl < 1e-9f) {
                continue;
            }
            // No thinner than about a pixel, as far off as it is, or a far ember twinkles in and out between pixels.
            float distance = (float) Math.sqrt(hx * hx + hy * hy + hu * hu);
            float size = Math.max(EMBER_SIZE * (0.6f + 0.8f * hash(seed + i * 0.91f)), distance * pixelPerTile);
            wx *= size / wl;
            wy *= size / wl;
            wu *= size / wl;
            float heat = at[3];
            GL11.glColor3f(heat, heat * (0.35f + 0.4f * heat), heat * 0.08f * heat);
            GL11.glTexCoord2f(-1f, -1f);
            GL11.glVertex3f(hx - sx - wx, hy - sy - wy, hu - su - wu);
            GL11.glTexCoord2f(1f, -1f);
            GL11.glVertex3f(hx - sx + wx, hy - sy + wy, hu - su + wu);
            GL11.glTexCoord2f(1f, 1f);
            GL11.glVertex3f(hx + wx, hy + wy, hu + wu);
            GL11.glTexCoord2f(-1f, 1f);
            GL11.glVertex3f(hx - wx, hy - wy, hu - wu);
        }
        GL11.glEnd();
        GL11.glColor3f(1f, 1f, 1f);
        GL30.glEnablei(GL_BLEND, 1);
    }

    /**
     * Where an ember is, into out as how far it has risen from the fire's foot, how far it is off
     * the fire's middle along x and y, all in tiles, and how hot it still is, 0 to 1 (0: not
     * flying). Each of a fire's embers rises from a place of its own among the flames, wanders
     * as the hot air turns, and cools to nothing.
     */
    static void ember(int index, float seed, float seconds, float radius, float tall, float[] out) {
        float own = hash(seed * 7.1f + index * 1.37f);
        float life = EMBER_LIFE * (0.6f + 0.8f * own);
        float age = (seconds + own * 97f) / life;
        float cycle = (float) Math.floor(age);
        float t = (age - cycle) * life;
        // A new place each time it is thrown up.
        float a = hash(own * 31f + cycle * 0.77f) * 6.2832f, r = (float) Math.sqrt(hash(own * 53f + cycle * 1.31f)) * radius * 0.8f;
        float rise = EMBER_RISE * (0.7f + 0.6f * hash(own * 11f + cycle)) * t;
        float wander = 0.18f * t * t;
        out[0] = tall * 0.25f + rise;
        out[1] = (float) Math.cos(a) * r + wander * (float) Math.sin(3.1f * t + own * 40f);
        out[2] = (float) Math.sin(a) * r + wander * (float) Math.cos(2.3f * t + own * 17f);
        float heat = 1f - t / life;
        out[3] = heat * heat * Math.min(t * 8f, 1f);
    }

    /**
     * How wide a pixel of a picture height pixels high is, in tiles for each tile ahead of the eye.
     * The overhead view's eye is thousands of tiles off with a field of view to match, so a
     * pixel there is a far smaller share of the distance than in first person.
     */
    static float pixelPerTile(float tanHalfFieldOfView, int height) {
        return 2f * tanHalfFieldOfView / height;
    }

    static float hash(float x) {
        double s = Math.sin(x * 12.9898) * 43758.5453;
        return (float) (s - Math.floor(s));
    }
}

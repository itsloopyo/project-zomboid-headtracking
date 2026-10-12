package com.cameraunlock.zomboid;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
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
 * The flames of a fire and the embers that fly up from it. The flames are a fire simulated and
 * rendered as a loop of frames (tools/fire/flames.py), each fire drawn as a few upright sheets
 * that turn about the up to face the eye, standing one behind another through the fire, each
 * playing the loop at a time of its own: seen from anywhere, a body of flame that never repeats
 * along its width. Where a sheet passes into the ground, a wall or a hearth's sides it fades
 * over a short distance instead of being cut. The embers are sparks that rise and wander off
 * the flames and cool as they go.
 *
 * Render thread only.
 */
final class FlameSheets {
    /** The loop: its frames, how many a row of the picture holds, and how many it plays a second. */
    static final int FRAMES = 64, ACROSS = 8;
    static final float FRAMES_A_SECOND = 24f;
    /**
     * How a frame of the loop is laid: the simulation's fuel at its foot, twice as high as it is
     * wide, the flames, where the program draws any, reaching REACH of its height on the mean and
     * their root ROOT of its width across (measured off the loop tools/fire/flames.py makes). A
     * sheet is stretched so that its flames reach as high as the fire's and are as wide at their root.
     */
    static final float FRAME_HIGH_FOR_WIDE = 2f, REACH = 0.65f, ROOT = 0.3f;
    /** How many sheets a fire is drawn as, and how far apart through it they stand, in its radius. */
    static final int SHEETS = 3;
    static final float SHEETS_APART = 0.45f;
    /** How much of a fire's light each of its sheets gives: they stand through each other, and where they cross they add. */
    static final float SHEET_SHARE = 0.55f;
    /** How bright the flames are at their brightest, in the picture's light: the sun lights a white wall to about 2. */
    static final float BRIGHTEST = 4f;
    /** Over how many of a fire's radii a sheet fades where it meets a surface. */
    static final float SOFT = 0.35f;

    /**
     * The game's frames of flames and smoke: the pixel of a frame that is the middle of its square
     * on the floor, and how far a pixel is in tiles across and up: a frame 128 pixels wide spans a
     * square's diagonal, and 192 pixels up is a level. The game shows them at about 15 frames a
     * second, and its smoke at 0.7 of whole.
     */
    static final float LOOK_FOOT_X = 64f, LOOK_FOOT_Y = 224f, LOOK_WIDE_PER_PIXEL = 1.4142f / 128f, LOOK_HIGH_PER_PIXEL = ViewCamera.LEVEL_HEIGHT / 192f;
    static final float LOOK_FRAMES_A_SECOND = 15f, LOOK_SMOKE = 0.7f;

    /** How many embers a fire throws up at once at its height, and how long each flies, in seconds. */
    static final int EMBERS = 18;
    static final float EMBER_LIFE = 2.2f;
    /** How fast an ember rises, in tiles a second, how wide it is, and how bright at its hottest. */
    static final float EMBER_RISE = 1.6f, EMBER_SIZE = 0.012f, EMBER_BRIGHT = 14f;
    /** How long a streak an ember draws, in seconds of its flight: the eye's own blur of a bright moving point. */
    static final float EMBER_STREAK = 0.035f;
    /** The least an ember is drawn across, in tiles for each tile it is from the eye: about a pixel of the picture. */
    static final float EMBER_LEAST_PER_TILE = 0.0012f;

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
            uniform sampler2D uFlames;
            uniform sampler2D uDepth;
            uniform vec2 uSize;
            uniform float uNear;
            uniform float uFar;
            // For flames: the frame of the loop, which loop (0 or 1), how strongly they burn and the fade's reach in tiles.
            uniform float uFrame;
            uniform float uLoop;
            uniform float uStrength;
            uniform float uSoft;
            // The loop's frames, how many a row of the picture holds, and how bright its flames are at their brightest.
            uniform float uFrames;
            uniform float uAcross;
            uniform float uBrightest;
            // 0 a sheet of flame, 1 an ember, 2 a frame of the game's own flames, 3 of its smoke, and how bright an ember at its hottest is.
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


            // The light of burning gas by how much of it there is, as the loop holds it: a deep red where
            // it is thin, through orange to a yellow white at its root.
            vec3 glowOf(float amount) {
                vec3 c = mix(vec3(0.55, 0.06, 0.01), vec3(1.0, 0.33, 0.05), smoothstep(0.02, 0.3, amount));
                c = mix(c, vec3(1.0, 0.6, 0.17), smoothstep(0.25, 0.6, amount));
                return mix(c, vec3(1.0, 0.86, 0.55), smoothstep(0.6, 1.0, amount));
            }

            float amountIn(float frame, vec2 uv) {
                float i = mod(frame, uFrames);
                vec2 cell = vec2(mod(i, uAcross), floor(i / uAcross));
                // The picture's first row is its top, and a frame's flames stand on its foot.
                vec2 at = (cell + vec2(uv.x, 1.0 - uv.y)) / vec2(uAcross, uFrames / uAcross);
                vec2 both = texture2D(uFlames, at).rg;
                float stored = mix(both.r, both.g, uLoop);
                return stored * stored;
            }

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
                    // The game's flames are light; its smoke is dark, lit as the air round it is.
                    vec3 light = uKind < 2.5 ? pow(texel.rgb, vec3(2.2)) * (a * uBrightest * 0.6) : uAmbient * (0.3 * a);
                    gl_FragData[0] = vec4(light, clamp(a * 1.5, 0.0, 1.0));
                    gl_FragData[1] = vec4(vec3(0.375), 1.0);
                    return;
                }
                if (uKind > 0.5) {
                    float r = length(vPlace);
                    float spot = 1.0 - smoothstep(0.2, 1.0, r);
                    if (spot <= 0.0) {
                        discard;
                    }
                    gl_FragData[0] = vec4(vColor.rgb * (spot * uEmberBright), 0.0);
                    gl_FragData[1] = vec4(vec3(0.375), 1.0);
                    return;
                }
                // Inset half a texel so no frame reads its neighbour's edge.
                vec2 uv = clamp(vPlace, vec2(0.004), vec2(0.996));
                float f = floor(uFrame);
                // The simulation's flame field is a soft cloud: its thin quarter is let go and its top fifth made whole, which
                // leaves the flames' own edges and the tongues that break off them.
                float amount = smoothstep(0.25, 0.8, mix(amountIn(f, uv), amountIn(f + 1.0, uv), uFrame - f));
                float shown = amount * fade * uStrength;
                if (shown < 0.003 || uDepthOnly > 0.5 && shown < 0.3) {
                    discard;
                }
                // Coloured by how much flame there is, and as bright as the fire burns and the fade leaves it.
                vec3 light = glowOf(amount) * (shown * uBrightest);
                // Flame hides what a wet road behind it mirrors, as much as it is there. It moves by itself (FireDraw.MOVES).
                gl_FragData[0] = vec4(light, clamp(shown * 3.0, 0.0, 1.0));
                gl_FragData[1] = vec4(vec3(0.375), 1.0);
            }
            """;

    private static final int GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE0 = 0x84C0, GL_RG = 0x8227, GL_RG8 = 0x822B, GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_LINEAR = 0x2601, GL_LINEAR_MIPMAP_LINEAR = 0x2703;
    private static final int GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803, GL_CLAMP_TO_EDGE = 0x812F, GL_UNPACK_ALIGNMENT = 0x0CF5;
    private static final int GL_QUADS = 0x0007, GL_PROJECTION = 0x1701, GL_MODELVIEW = 0x1700;
    private static final int GL_ONE = 1, GL_ZERO = 0, GL_ONE_MINUS_SRC_ALPHA = 0x0303, GL_BLEND = 0x0BE2;

    static final String PICTURE = "/fire/flames.png";

    private static final SmallProgram program = new SmallProgram("flames", VERTEX, FRAGMENT);
    /** The loop as it is read off the jar, on a thread of its own the first time a fire is seen. */
    private static CompletableFuture<Loop> reading;
    private static int texture;

    private record Loop(ByteBuffer texels, int wide, int high) {}

    private final float[] projection = new float[16], turned = new float[16];
    private final ViewCamera.Pose atEye = new ViewCamera.Pose();

    /** The loop's two flames, red and green, as eight bits each, read off the jar. */
    static Loop read() {
        BufferedImage image;
        try (InputStream in = FlameSheets.class.getResourceAsStream(PICTURE)) {
            if (in == null) {
                throw new IOException("the jar has no " + PICTURE);
            }
            image = ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(PICTURE + " could not be read", e);
        }
        int wide = image.getWidth(), high = image.getHeight(), frameWide = wide / ACROSS;
        if (wide != ACROSS * frameWide || high != FRAMES / ACROSS * frameWide * (int) FRAME_HIGH_FOR_WIDE) {
            throw new IllegalStateException(PICTURE + " is " + wide + " by " + high + ", not " + ACROSS + " frames across and " + FRAMES / ACROSS
                    + " down, each twice as high as wide");
        }
        ByteBuffer texels = ByteBuffer.allocateDirect(wide * high * 2);
        int[] row = new int[wide];
        for (int y = 0; y < high; y++) {
            image.getRGB(0, y, wide, 1, row, 0, wide);
            for (int rgb : row) {
                texels.put((byte) (rgb >> 16)).put((byte) (rgb >> 8));
            }
        }
        texels.flip();
        return new Loop(texels, wide, high);
    }

    /** The loop's texture, or 0 while it is still being read. */
    private static int texture() {
        if (texture != 0) {
            return texture;
        }
        if (reading == null) {
            reading = CompletableFuture.supplyAsync(FlameSheets::read);
            return 0;
        }
        if (!reading.isDone()) {
            return 0;
        }
        Loop loop = reading.join();
        texture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, texture);
        GL11.glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RG8, loop.wide, loop.high, 0, GL_RG, GL_UNSIGNED_BYTE, loop.texels);
        GL11.glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        GL30.glGenerateMipmap(GL_TEXTURE_2D);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        Log.info("first person fire: the flames' loop is on the graphics card, " + loop.wide + " by " + loop.high);
        return texture;
    }

    /**
     * The frame of the loop a sheet shows at seconds, for a fire with its own number and the
     * sheet's place among the fire's: each sheet is at a time of its own, so no two show the same
     * flames. 0 up to FRAMES.
     */
    static float frame(float seconds, float seed, int sheet) {
        float at = seconds * FRAMES_A_SECOND + seed * 13.7f + sheet * 21.3f;
        return at - (float) Math.floor(at / FRAMES) * FRAMES;
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

    static float hash(float x) {
        double s = Math.sin(x * 12.9898) * 43758.5453;
        return (float) (s - Math.floor(s));
    }

    private final float[] at = new float[4], before = new float[4];

    /**
     * Readies the program for drawing fires into the picture bound, behind what is nearer the eye
     * in depth, the picture's depth: false while the loop is still being read. The camera's
     * matrices are loaded, turned but not moved: every point is given from the eye.
     */
    boolean begin(int depth, ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far, int width, int height,
            float asideX, float asideY) {
        int loop = texture();
        if (loop == 0 || !program.use()) {
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
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, loop);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, depth);
        GL20.glUniform1i(program.at("uDepth"), 0);
        GL20.glUniform1i(program.at("uFlames"), 1);
        GL20.glUniform1i(program.at("uSprite"), 2);
        GL20.glUniform2f(program.at("uSize"), width, height);
        GL20.glUniform1f(program.at("uNear"), near);
        GL20.glUniform1f(program.at("uFar"), far);
        GL20.glUniform1f(program.at("uFrames"), FRAMES);
        GL20.glUniform1f(program.at("uAcross"), ACROSS);
        GL20.glUniform1f(program.at("uBrightest"), BRIGHTEST);
        GL20.glUniform1f(program.at("uEmberBright"), EMBER_BRIGHT);
        GL20.glUniform3f(program.at("uForward"), camera.forwardX, camera.forwardY, camera.forwardUp);
        return true;
    }

    /**
     * Draws one fire's flames and embers, the Fires record at f, once begin has readied the
     * program. Light is added to the picture and a surface behind mirrors as little as the flame
     * hides it; with frame blending on, the second buffer is given, over whatever it held, the
     * mark that what is there moves by itself.
     */
    void fire(float[] fires, int f, ViewCamera.Pose camera, float seconds) {
        program.use();
        GL14.glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ZERO, GL_ONE_MINUS_SRC_ALPHA);
        GL30.glDisablei(GL_BLEND, 1);
        float x = fires[f + Fires.X] - camera.eyeX, y = fires[f + Fires.Y] - camera.eyeY, up = fires[f + Fires.UP] - camera.eyeUp;
        float radius = fires[f + Fires.RADIUS], tall = fires[f + Fires.TALL], seed = fires[f + Fires.SEED];
        // The sheets face the eye about the up: across them is level and square to the line from the eye. Over the
        // fire's very middle that line is straight down, and across is the view's own right.
        float level = (float) Math.hypot(x, y);
        float acrossX = level > 1e-3f ? -y / level : camera.rightX, acrossY = level > 1e-3f ? x / level : camera.rightY;
        float acrossLength = (float) Math.hypot(acrossX, acrossY);
        acrossX /= acrossLength;
        acrossY /= acrossLength;
        float toX = acrossY, toY = -acrossX;
        float high = tall / REACH, halfWide = radius / ROOT;
        GL20.glUniform1f(program.at("uKind"), 0f);
        GL20.glUniform1f(program.at("uStrength"), fires[f + Fires.FLAME] * SHEET_SHARE);
        GL20.glUniform1f(program.at("uSoft"), radius * SOFT);
        for (int sheet = 0; sheet < SHEETS; sheet++) {
            float through = (sheet - (SHEETS - 1) / 2f) * SHEETS_APART * radius;
            float aside = (hash(seed + sheet * 3.3f) - 0.5f) * 0.3f * radius;
            float cx = x + toX * through + acrossX * aside, cy = y + toY * through + acrossY * aside;
            GL20.glUniform1f(program.at("uFrame"), frame(seconds, seed, sheet));
            GL20.glUniform1f(program.at("uLoop"), sheet % 2);
            // The middle sheet is the loop seen from behind.
            float flip = sheet == 1 ? -1f : 1f;
            float sx = acrossX * halfWide * flip, sy = acrossY * halfWide * flip;
            GL11.glBegin(GL_QUADS);
            GL11.glTexCoord2f(0f, 0f);
            GL11.glVertex3f(cx - sx, cy - sy, up);
            GL11.glTexCoord2f(1f, 0f);
            GL11.glVertex3f(cx + sx, cy + sy, up);
            GL11.glTexCoord2f(1f, 1f);
            GL11.glVertex3f(cx + sx, cy + sy, up + high);
            GL11.glTexCoord2f(0f, 1f);
            GL11.glVertex3f(cx - sx, cy - sy, up + high);
            GL11.glEnd();
        }
        if (!depthOnly) {
            embers(fires, f, x, y, up, seconds);
        }
        GL30.glEnablei(GL_BLEND, 1);
    }

    /** Where a uniform of the program is, for what FireDraw tells it. */
    int uniform(String name) {
        return program.at(name);
    }

    private boolean depthOnly;

    /** While on, what is drawn goes into the picture's depth alone, where the flames are more than a glow. */
    void depthOnly(boolean on) {
        depthOnly = on;
        program.use();
        GL20.glUniform1f(program.at("uDepthOnly"), on ? 1f : 0f);
    }

    /**
     * A sheet of the game's own frames of a fire's flames, or of its smoke, as the game paints
     * them for its view from above, stood up where the fire burns, facing the eye about the up:
     * the frame's foot (pixel 64, 224 of 128 by 256, the middle of the square) at the fire's
     * foot, at the game's own scale of a pixel, made smaller for a hearth whose flames are less.
     * Its frames go round at the game's pace.
     */
    void look(float[] fires, int f, IsoAnim anim, boolean smoke, ViewCamera.Pose camera, float seconds) {
        ArrayList<IsoDirectionFrame> frames = anim.frames;
        if (frames == null || frames.isEmpty()) {
            return;
        }
        float seed = fires[f + Fires.SEED];
        int at = (int) Math.floor(seconds * LOOK_FRAMES_A_SECOND + seed * 7f) % frames.size();
        Texture frame = frames.get(at).getTexture(IsoDirections.N);
        if (frame == null || frame.getID() <= 0) {
            return;
        }
        program.use();
        if (smoke) {
            GL14.glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GL14.glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ZERO, GL_ONE_MINUS_SRC_ALPHA);
        }
        GL30.glDisablei(GL_BLEND, 1);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, frame.getID());
        GL13.glActiveTexture(GL_TEXTURE0);
        float x = fires[f + Fires.X] - camera.eyeX, y = fires[f + Fires.Y] - camera.eyeY, up = fires[f + Fires.UP] - camera.eyeUp;
        float level = (float) Math.hypot(x, y);
        float acrossX = level > 1e-3f ? -y / level : camera.rightX, acrossY = level > 1e-3f ? x / level : camera.rightY;
        float acrossLength = (float) Math.hypot(acrossX, acrossY);
        acrossX /= acrossLength;
        acrossY /= acrossLength;
        float left = frame.getOffsetX(), top = frame.getOffsetY(), wide = frame.getWidth(), high = frame.getHeight();
        // Smaller for a hearth: the frame's flames reach no higher than half as high again as the hearth's.
        float tall = fires[f + Fires.TALL], reaches = (LOOK_FOOT_Y - top) * LOOK_HIGH_PER_PIXEL;
        float scale = smoke || tall <= 0f || reaches <= 0f ? 1f : Math.min(1f, tall * 1.5f / reaches);
        float from = (left - LOOK_FOOT_X) * LOOK_WIDE_PER_PIXEL * scale, to = (left + wide - LOOK_FOOT_X) * LOOK_WIDE_PER_PIXEL * scale;
        float upper = (LOOK_FOOT_Y - top) * LOOK_HIGH_PER_PIXEL * scale, lower = (LOOK_FOOT_Y - top - high) * LOOK_HIGH_PER_PIXEL * scale;
        GL20.glUniform1f(program.at("uKind"), smoke ? 3f : 2f);
        GL20.glUniform1f(program.at("uStrength"), smoke ? LOOK_SMOKE : 1f);
        GL20.glUniform1f(program.at("uSoft"), Math.max(fires[f + Fires.RADIUS], 0.15f) * SOFT);
        float u0 = frame.getXStart(), u1 = frame.getXEnd(), v0 = frame.getYStart(), v1 = frame.getYEnd();
        GL11.glBegin(GL_QUADS);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex3f(x + acrossX * from, y + acrossY * from, up + lower);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex3f(x + acrossX * to, y + acrossY * to, up + lower);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex3f(x + acrossX * to, y + acrossY * to, up + upper);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex3f(x + acrossX * from, y + acrossY * from, up + upper);
        GL11.glEnd();
        GL30.glEnablei(GL_BLEND, 1);
    }

    /** The embers of one fire whose foot is at (x, y, up) from the eye. */
    private void embers(float[] fires, int f, float x, float y, float up, float seconds) {
        float radius = fires[f + Fires.RADIUS], tall = fires[f + Fires.TALL], seed = fires[f + Fires.SEED];
        int embers = Math.round(EMBERS * Math.min(tall / 2.2f, 1f) * fires[f + Fires.FLAME]);
        if (embers == 0) {
            return;
        }
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
            float size = Math.max(EMBER_SIZE * (0.6f + 0.8f * hash(seed + i * 0.91f)), distance * EMBER_LEAST_PER_TILE);
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
    }
}

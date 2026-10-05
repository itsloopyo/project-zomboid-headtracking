package com.cameraunlock.zomboid;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import zombie.core.textures.TextureDraw;

/**
 * How one frame of the first-person picture is drawn.
 *
 * The world's triangles are drawn twice from the sun's side into two shadow
 * maps, a near one and a far one, then once from the eye into a picture that
 * holds light as it is rather than as a screen shows it: each pixel is the
 * sprite's own colour under the game's light for its square, the sun where
 * the shadow maps say the sun reaches, and the sky's light from above, fading
 * into the sky with distance. The sky is drawn into the pixels the world left
 * empty. That picture is then turned into what a camera would have recorded:
 * shafts of light past whatever stands before the sun, a glow around what is
 * brighter than the screen can show, an exposure, a film curve, and smoothed
 * edges, written over the game's own picture of the world.
 *
 * Nothing here runs unless the first-person view is on, so the game's own
 * view is drawn by the game alone. Render thread only. The game's OpenGL state
 * is saved before the frame and put back after it.
 */
final class Pipeline {
    private static final int SHADOW_SIZE = 4096;
    /** Half the width of the ground each shadow map covers, in tiles, and how far along the light it reaches. */
    private static final float NEAR_EXTENT = 24f, FAR_EXTENT = 96f;
    private static final float NEAR_RANGE = 120f, FAR_RANGE = 220f;
    private static final int BLOOM_LEVELS = 5;
    private static final int NOISE_SIZE = 64;

    private static final int GL_TRIANGLES = 0x0004;
    private static final int GL_TRIANGLE_STRIP = 0x0005;
    private static final int GL_FLOAT = 0x1406;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_RGB = 0x1907;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_RGB8 = 0x8051;
    private static final int GL_RGBA8 = 0x8058;
    private static final int GL_RGBA16F = 0x881A;
    private static final int GL_DEPTH_COMPONENT = 0x1902;
    private static final int GL_DEPTH_COMPONENT24 = 0x81A6;
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_TEXTURE_WIDTH = 0x1000;
    private static final int GL_TEXTURE_HEIGHT = 0x1001;
    /**
     * How covered a pixel of a solid sprite has to be to be drawn. A shade under a half: the
     * cover is a mean over two texels, and a bar one texel wide reaches a half and no more.
     */
    private static final float SOLID_CUT = 0.48f;
    private static final int GL_TEXTURE_3D = 0x806F;
    private static final int GL_TEXTURE0 = 0x84C0;
    private static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    private static final int GL_TEXTURE_WRAP_S = 0x2802;
    private static final int GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_TEXTURE_WRAP_R = 0x8072;
    private static final int GL_TEXTURE_COMPARE_MODE = 0x884C;
    private static final int GL_TEXTURE_COMPARE_FUNC = 0x884D;
    private static final int GL_COMPARE_REF_TO_TEXTURE = 0x884E;
    private static final int GL_NEAREST = 0x2600;
    private static final int GL_LINEAR = 0x2601;
    private static final int GL_REPEAT = 0x2901;
    private static final int GL_LINEAR_MIPMAP_LINEAR = 0x2703;
    private static final int GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_LEQUAL = 0x0203;
    private static final int GL_SRC_ALPHA = 0x0302;
    private static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_NONE = 0;
    private static final int GL_DEPTH_TEST = 0x0B71;
    private static final int GL_CULL_FACE = 0x0B44;
    private static final int GL_ZERO = 0;
    private static final int GL_ALWAYS = 0x0207;
    private static final int GL_ONE = 1;
    private static final int GL_BLEND = 0x0BE2;
    private static final int GL_DST_ALPHA = 0x0304, GL_ONE_MINUS_DST_ALPHA = 0x0305;
    private static final int GL_CONSTANT_ALPHA = 0x8003, GL_ONE_MINUS_CONSTANT_ALPHA = 0x8004;
    private static final int GL_ALPHA_TEST = 0x0BC0;
    private static final int GL_SCISSOR_TEST = 0x0C11;
    private static final int GL_STENCIL_TEST = 0x0B90;
    private static final int GL_LIGHTING = 0x0B50;
    private static final int GL_FOG = 0x0B60;
    private static final int GL_POLYGON_OFFSET_FILL = 0x8037;
    private static final int GL_DEPTH_BUFFER_BIT = 0x0100;
    private static final int GL_COLOR_BUFFER_BIT = 0x4000;
    private static final int GL_MODELVIEW = 0x1700;
    private static final int GL_PROJECTION = 0x1701;
    private static final int GL_ALL_ATTRIB_BITS = 0x000FFFFF;
    private static final int GL_CLIENT_ALL_ATTRIB_BITS = 0xFFFFFFFF;
    private static final int GL_VERTEX_ARRAY = 0x8074;
    private static final int GL_NORMAL_ARRAY = 0x8075;
    private static final int GL_COLOR_ARRAY = 0x8076;
    private static final int GL_TEXTURE_COORD_ARRAY = 0x8078;
    /** Index, edge flag, fog coordinate and secondary colour: the arrays this frame does not use. */
    private static final int[] OTHER_ARRAYS = {0x8077, 0x8079, 0x8457, 0x845E};
    /** Every OpenGL has at least this many generic vertex attributes and old-style texture coordinate sets. */
    private static final int VERTEX_ATTRIBUTES = 16;
    private static final int TEXTURE_COORD_UNITS = 8;
    private static final int GL_CURRENT_PROGRAM = 0x8B8D;
    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6;
    private static final int GL_READ_FRAMEBUFFER_BINDING = 0x8CAA;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    private static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_VERTEX_ARRAY_BINDING = 0x85B5;
    private static final int GL_ARRAY_BUFFER = 0x8892;
    private static final int GL_ARRAY_BUFFER_BINDING = 0x8894;
    private static final int GL_STATIC_DRAW = 0x88E4;
    private static final int GL_PIXEL_UNPACK_BUFFER = 0x88EC;
    private static final int GL_PIXEL_UNPACK_BUFFER_BINDING = 0x88EF;
    private static final int GL_SAMPLER_BINDING = 0x8919;
    private static final int GL_UNPACK_ALIGNMENT = 0x0CF5;
    private static final int GL_UNPACK_ROW_LENGTH = 0x0CF2;
    private static final int GL_UNPACK_SKIP_ROWS = 0x0CF3;
    private static final int GL_UNPACK_SKIP_PIXELS = 0x0CF4;
    private static final int GL_ERROR_FLAGS = 8;
    /** The texture units the frame uses, each of which the game may have left a sampler object on. */
    private static final int UNITS = 32;
    /** The texture unit the headlights' shadow map stays bound to through the frame. */
    private static final int BEAM_UNIT = 20;

    /**
     * The shaders the off-line harnesses read from this class by name. The two longest are
     * joined here from their parts.
     */
    private static final String WORLD_VERTEX = PipelineShaders.WORLD_VERTEX;
    private static final String SHADOW_VERTEX = PipelineShaders.SHADOW_VERTEX;
    private static final String SHADOW_FRAGMENT = PipelineShaders.SHADOW_FRAGMENT;
    private static final String SCREEN_VERTEX = PipelineShaders.SCREEN_VERTEX;
    private static final String WORLD_FRAGMENT = PipelineShaders.WORLD_FRAGMENT_PARTS.concat(PipelineShaders.WORLD_FRAGMENT_MAIN);
    private static final String FIGURE_LIGHT = PipelineShaders.FIGURE_LIGHT_PARTS.concat(CabinShader.SOLIDS).concat(CabinShader.LIGHT).concat(CabinShader.GLASS)
            .concat(CharacterDraw.LIT).concat(PipelineShaders.FIGURE_LIGHT_MAIN);

    /** Why the picture cannot be drawn on this machine, or null. Set once, on the render thread. */
    private static volatile String failure;
    private static boolean prepared;
    private static Program rain;
    private static Program steady, copy;
    /** The steadied picture of the last frame and of this one, taking turns. */
    private static final int[] keptColor = new int[2], keptTarget = new int[2];
    private static int keptNow;
    private static boolean keptAny;
    private static final ViewCamera.Pose before = new ViewCamera.Pose();
    /** How much of the last frame's picture goes into this one. */
    private static final float KEPT = 0.93f;
    /** Where in its pixel each frame of the cycle is drawn, from the middle, in pixels times two: the Halton points of bases 2 and 3. */
    private static final float[] JITTER_X = {0f, -0.5f, 0.5f, -0.75f, 0.25f, -0.25f, 0.75f, -0.875f};
    private static final float[] JITTER_Y = {-0.333f, 0.333f, -0.778f, -0.111f, 0.556f, -0.556f, 0.111f, 0.778f};
    private static int jitterAt;
    private static Program world, shadow, figureLight, sky, clouds, occlusion, soften, volume, bloomDown, bloomUp, develop, smooth;
    private static int whiteTexture, noiseTexture;
    private static int nearMap, farMap, nearTarget, farTarget;
    /** What is overhead near the eye, seen from straight above: where rain cannot fall. */
    private static int roofMap, roofTarget;
    private static final float[] roofMatrix = new float[16];
    private static final float ROOF_EXTENT = 20f, ROOF_RANGE = 40f;
    private static final int RAIN_DROPS = 9000;
    /** The drops, on the graphics card: they are the same every frame. */
    private static int rainDrops;
    /**
     * What open ground is made of, as pictures of the stuff itself that join up with themselves
     * at their edges: tarmac, gravel, earth, grass. They are laid over the ground by where it
     * is on the map, so the game's square of ground repeated a thousand times becomes one
     * unbroken surface. Made for this mod and shipped in its jar.
     */
    private static final String[] MATERIALS = {"asphalt", "gravel", "soil", "grass", "shingles", "paint", "wood", "bark", "planks", "carpet", "leaves", "vinyl", "cloth", "jersey"};
    /** Which of the materials is planed wood. */
    private static final int WOOD = 6, CARPET = 9;
    /** The materials from here on are cut-outs: four bytes a pixel, the last how solid it is. */
    private static final int FIRST_CUTOUT = 10;
    private static final int FIRST_MATERIAL_UNIT = 3;
    private static final int[] materialTexture = new int[MATERIALS.length];
    /** What each material's picture is multiplied by to be 1 on average: it changes the ground's texture, not how light it is. */
    private static final float[] materialGain = new float[MATERIALS.length];
    /** What Flora's ground and plants are made of: fallen leaves, rock, a fern's frond and ivy, the last two cut out. */
    private static final String[] NATURE = {"litter", "rock", "frond", "ivy"};
    private static final int FIRST_NATURE_CUTOUT = 2;
    private static final int FIRST_NATURE_UNIT = 24;
    private static final int[] natureTexture = new int[NATURE.length];
    private static final float[] natureGain = new float[NATURE.length];
    /** The sprigs a crown is made of, all in one picture: see Flora.addCrown. */
    private static final int SPRIG_UNIT = 28;
    private static int sprigs;
    private static int width, height;
    private static int sceneTarget, sceneColor, sceneDepth, shownTarget, shownColor, figureTarget, figureColor;
    /** The viewer's own body, drawn apart from everything else: see Characters.drawViewer. */
    private static int ownTarget, ownColor, ownDepth;
    /** Beside the viewer's own body, which way each point of it faces, where the picture drew it itself. */
    private static int ownNormal;
    private static int repaintedTarget, repaintedColor, repaintedDepth;
    private static final float OWN_NEAR = 0.3f;
    private static int occlusionTarget, occlusionColor, softTarget, softColor;
    private static int volumeTarget, volumeColor, softVolumeTarget, softVolumeColor;
    private static int spriteSampler;
    private static int skinSampler;
    private static boolean glazed;
    private static final int SKIN_UNITS = 8;
    private static int depthSampler;
    /** How wide the sun is, as the width of a shadow's soft edge for each tile between caster and ground. Wider than the real sun's: the real one's edge is too fine to read at this scale. */
    private static final float SUN_WIDTH = 0.06f;
    private static int cloudTarget, cloudColor, softCloudTarget, softCloudColor;

    private static final boolean TIMES = Boolean.getBoolean("cameraunlock.zomboid.gpuTimes");
    /**
     * The picture seen from far overhead, as the game's own view is. The eye is then thousands
     * of tiles off and everything in sight is much the same distance from it, so what the
     * picture does by how far a thing is from the eye (detail, haze, the reach of shade) goes
     * by its distance less rangeFrom, and what it centres on the eye (shadow maps, who is
     * near enough to draw) is centred on the place looked at.
     */
    static boolean overhead;
    /** The picture is drawn by a camera outside the viewer, who is then one of the people in it. */
    static boolean outside;
    /** How much of the game's own picture, which is under this one, this one covers. */
    static float opacity = 1f;
    /** The glass the viewer is shown in this frame, or null: see OwnReflection. */
    static OwnReflection.Glass reflection;
    private static int selfTarget, selfColor;
    private static Program selfShown;
    private static final ViewCamera.Pose inGlass = new ViewCamera.Pose();
    private static final float[] selfView = new float[16], selfProjection = new float[16];
    private static final FloatBuffer selfCorners = buffer(OwnReflection.MOST * 18);
    /**
     * Whether the viewer sat in a vehicle when the frame was built. Every pass of the frame
     * goes by this one answer: the logic thread seats the viewer and stands them up between
     * two passes of a frame as readily as between two frames.
     */
    static boolean seated;
    static float rangeFrom;
    static float focusX, focusY, focusUp;
    /** The height above which walls are left out, for a viewer indoors seen from overhead: see the world shader. */
    static float cutAbove = Float.MAX_VALUE;
    /** How much of CUT_BEHIND the cut still reaches behind the viewer: all of it from overhead, none as the camera reaches the eye. */
    static float cutBehind = 1f;
    /** The height above which everything in the viewer's building is left out, while the camera is above it. */
    static float lid = Float.MAX_VALUE;
    static final float[] cutBox = new float[4];
    private static final float[] holeAt = new float[2];
    /** The window opened about the viewer: its radius as a share of the picture's height, how far before them it starts, and how far under their eye its middle is, in tiles. */
    private static final float HOLE_OF_HEIGHT = 0.11f, HOLE_BEFORE = 1.6f, HOLE_BELOW_EYE = 0.5f, HOLE_FLOOR_BELOW_EYE = 1.2f;
    /** How far behind the viewer, away from the camera, the cut begins, in tiles of x and y together along the map's diagonal. */
    private static final float CUT_BEHIND = 1.5f;
    /**
     * How many of the picture's pixels there are to each of the window's, each way. Leaves,
     * blades of grass, fence wire and roof edges are finer than a pixel, and drawn one sample
     * to a pixel each is there or not there as the head moves by a hair: the whole picture
     * crawls. Drawn finer and brought down to the window, each pixel is the mean of several
     * and changes by degrees.
     */
    private static final float SCALE = Float.parseFloat(System.getProperty("cameraunlock.zomboid.renderScale", "1"));
    /** Dev only: passes left out of the picture, to find which of them a fault is in. Any of steady, occlusion, air, glow. */
    private static final String WITHOUT = System.getProperty("cameraunlock.zomboid.without", "");
    private static final String[] STAGE_NAMES = {"load", "shadows", "world", "clouds", "sky", "occlusion", "air", "glow", "develop", "figures", "smooth"};
    private static final int STAGE_LOAD = 0, STAGE_SHADOWS = 1, STAGE_WORLD = 2, STAGE_CLOUDS = 3, STAGE_SKY = 4, STAGE_OCCLUSION = 5,
            STAGE_AIR = 6, STAGE_GLOW = 7, STAGE_DEVELOP = 8, STAGE_FIGURES = 9, STAGE_SMOOTH = 10;
    private static final long[] stageNanos = new long[STAGE_NAMES.length];
    private static long stageStart;
    private static int stageFrames;
    private static final int[] downTarget = new int[BLOOM_LEVELS], downColor = new int[BLOOM_LEVELS];
    private static final int[] upTarget = new int[BLOOM_LEVELS - 1], upColor = new int[BLOOM_LEVELS - 1];
    private static final int[] levelWidth = new int[BLOOM_LEVELS], levelHeight = new int[BLOOM_LEVELS];

    private static final float[] tint = new float[4];
    /** The chunk builds the frame draws, for the furniture standing on each. */
    private static ArrayList<WorldScene.ChunkMeshes> drawn;
    /** Every chunk of the frame as draws. */
    private static final ChunkBuffers.Listing all = new ChunkBuffers.Listing();
    /** For each shadow map, the chunks it can hold as draws, when those are not all of them. */
    private static final ChunkBuffers.Listing nearListed = new ChunkBuffers.Listing(), farListed = new ChunkBuffers.Listing(),
            beamListed = new ChunkBuffers.Listing(), roofListed = new ChunkBuffers.Listing();
    private static boolean[] leftOut = new boolean[256];
    /** The width and height of each texture a frame has drawn with, by its OpenGL name, and the frame each was last asked of the driver: see tellTexel. */
    private static int[] texelOf = new int[1024], texelWide = new int[1024], texelHigh = new int[1024], texelRead = new int[1024];
    private static int texelsKnown;
    /** A texture's size is asked again after this many frames and up to as many more, by its name, so they are not all asked in one frame. A power of two. */
    private static final int TEXEL_KEPT_FRAMES = 256;
    private static int frameNumber;
    /** How many chunks the last frame drew into the near, far, headlight and roof maps, and how many it had, for the log. */
    private static final int NEAR_MAP = 0, FAR_MAP = 1, BEAM_MAP = 2, ROOF_MAP = 3;
    private static final int[] heldChunks = new int[4];
    /** Written after heldChunks, and read before it by the logic thread. */
    private static volatile int heldOf;
    /** What the shadow map being drawn holds. */
    private static final MapBounds mapBounds = new MapBounds();
    /**
     * The furthest the wind moves a vertex, in tiles for each unit of the wind's strength: the
     * most the shaders' blown() leans a plant, 0.05 + 0.045 + 0.02, times the most of the wind
     * a vertex takes, which is 1, and as much again to be sure.
     */
    private static final float BLOWN_MOST = 0.115f * 2f;
    /** Every model the mod has, on the graphics card as Models.buffer lays them out. */
    private static int modelBuffer;
    private static final int MODEL_STRIDE = Models.BUFFER_FLOATS * Float.BYTES;
    private static final float[] nearMatrix = new float[16], farMatrix = new float[16];
    private static final float[] beamMatrix = new float[16];
    private static int beamMap, beamTarget;
    private static int paneColor, paneTarget;
    private static Program paneMask;
    private static final int PANE_UNIT = 23;
    /**
     * What part of the cabin each pixel of the vehicle sat in is, and how far off: the body's
     * own triangles, its glass left out, each drawn in the number of its kind.
     */
    private static int partsColor, partsDepth, partsTarget;
    private static Program partKind;
    private static final int PARTS_UNIT = 18, PARTS_DEPTH_UNIT = 19;
    /** The game's vehicle draw binds its own textures over the low units, the wood's among them: the cabin reads the wood from here. */
    private static final int WOOD_UNIT = 17;
    private static int figureNormal;
    private static final int NORMAL_UNIT = 22;
    private static final int[] FIRST_BUFFER = {GL_COLOR_ATTACHMENT0}, BOTH_BUFFERS = {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT0 + 1};
    private static int mirrorColor, mirrorTarget, mirrorDepth;
    /** The people and vehicles the mirror shows, as the game or the picture draws them, before they are lit into the mirror's picture. */
    private static int mirrorFigureTarget, mirrorFigureColor, mirrorFigureNormal;
    /** Nearer the mirror than this, in tiles, is the vehicle it hangs in, and is left out of its picture. */
    private static final float MIRROR_NEAR = 2.6f;
    private static boolean inMirror;
    private static final int MIRROR_WIDTH = 640, MIRROR_HEIGHT = 180, MIRROR_UNIT = 21;
    /** Half of how much of the world behind the mirror takes in, top to bottom, as a tangent. */
    private static final float MIRROR_TAN = 0.1f;
    private static final ViewCamera.Pose behind = new ViewCamera.Pose();
    private static final ViewCamera.Pose lamp = new ViewCamera.Pose();
    /** Half the angle the headlights' shadow map takes in, as a tangent, and how near and far it sees. */
    private static final float BEAM_TAN = 0.84f, BEAM_NEAR = 0.35f, BEAM_FAR = 32f;
    private static final float[] sunNdc = new float[2];
    private static final int[] samplers = new int[UNITS];
    /** Where the driver puts an answer: see bound. As long as the longest answer OpenGL gives to one question. */
    private static final int[] asked = new int[16];
    private static final float[] projection = new float[16], viewed = new float[16], flash = new float[7];
    private static final String[] BEAM_AT = new String[Headlights.BEAMS], BEAM_WAY = new String[Headlights.BEAMS];
    private static final String[] ROOT_AT = new String[Characters.ROOTS];

    static {
        for (int i = 0; i < Headlights.BEAMS; i++) {
            BEAM_AT[i] = "uBeamAt[" + i + "]";
            BEAM_WAY[i] = "uBeamWay[" + i + "]";
        }
        for (int i = 0; i < ROOT_AT.length; i++) {
            ROOT_AT[i] = "uRoot[" + i + "]";
        }
    }
    private static volatile float lastMillis;

    private Pipeline() {}

    /** Why first person cannot be drawn here, for the log and to keep the view off, or null. */
    static String failure() {
        return failure;
    }

    static float lastMillis() {
        return lastMillis;
    }

    /** How many of the last frame's chunks each shadow map was drawn from. */
    static String describeShadows() {
        int of = heldOf;
        return "of " + of + " chunks the shadow maps hold near " + heldChunks[NEAR_MAP] + " far " + heldChunks[FAR_MAP] + " headlights " + heldChunks[BEAM_MAP]
                + " roof " + heldChunks[ROOF_MAP];
    }

    private static FloatBuffer buffer(int floats) {
        return ByteBuffer.allocateDirect(floats * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    /**
     * @param viewport   the part of the game's picture the frame is written to: x, y, width, height
     * @param exposure   how much light the eye is letting in: lower in the open at noon than in a room
     * @param fogEnd     the distance, in tiles, at which the world has faded into the sky
     */
    static void draw(ArrayList<WorldScene.ChunkMeshes> chunks, ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView,
            float near, float far, float fogStart, float fogEnd, float exposure, int[] viewport) {
        if (failure != null) {
            return;
        }
        long start = System.nanoTime();
        clearErrors();
        frameNumber++;

        int program = bound(GL_CURRENT_PROGRAM);
        int drawTarget = bound(GL_DRAW_FRAMEBUFFER_BINDING);
        int readTarget = bound(GL_READ_FRAMEBUFFER_BINDING);
        int vertexArray = bound(GL_VERTEX_ARRAY_BINDING);
        int arrayBuffer = bound(GL_ARRAY_BUFFER_BINDING);
        int unpackBuffer = bound(GL_PIXEL_UNPACK_BUFFER_BINDING);
        GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL_CLIENT_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glPushMatrix();
        // The game's own buffers and samplers would change what this frame's pointers and
        // textures mean. They are set aside for the frame.
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        for (int unit = 0; unit < UNITS; unit++) {
            GL13.glActiveTexture(GL_TEXTURE0 + unit);
            samplers[unit] = bound(GL_SAMPLER_BINDING);
            GL33.glBindSampler(unit, 0);
        }
        GL13.glActiveTexture(GL_TEXTURE0);

        // Whatever the frame throws, the game gets its own state back: a frame that left its
        // pushes behind would leave more on every frame the fault came again.
        try {
            picture(chunks, camera, state, tanHalfFieldOfView, near, far, fogStart, fogEnd, exposure, viewport, drawTarget);
        } catch (RuntimeException | LinkageError thrown) {
            // Thrown on, it ends the game's whole frame, its own panels with it, and comes again
            // on the next. The view goes off instead, and the log has why.
            failure = "the frame's draw threw " + thrown;
            Log.error("first person cannot be drawn", thrown);
            Characters.unwind();
        } finally {
            for (int unit = 0; unit < UNITS; unit++) {
                GL33.glBindSampler(unit, samplers[unit]);
            }
            GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawTarget);
            GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, readTarget);
            GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
            GL30.glBindVertexArray(vertexArray);
            GL11.glMatrixMode(GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            GL20.glUseProgram(program);
        }
        lastMillis = (System.nanoTime() - start) / 1e6f;
    }

    private static void picture(ArrayList<WorldScene.ChunkMeshes> chunks, ViewCamera.Pose camera, SkyState state,
            float tanHalfFieldOfView, float near, float far, float fogStart, float fogEnd, float exposure, int[] viewport,
            int drawTarget) {
        if (!prepared) {
            prepared = true;
            failure = prepare();
        }
        if (failure == null && nearMap == 0) {
            failure = makeShadowMaps();
        }
        int wide = Math.round(viewport[2] * SCALE), high = Math.round(viewport[3] * SCALE);
        if (failure == null && (wide != width || high != height)) {
            failure = resize(wide, high);
        }
        if (failure == null) {
            float aspect = (float) viewport[2] / viewport[3];
            if (TIMES) {
                GL11.glFinish();
                stageStart = System.nanoTime();
            }
            SpriteColours.read();
            drawn = chunks;
            ChunkBuffers.load(chunks);
            all.of(chunks, null, true);
            arrays();
            stage(STAGE_LOAD);
            boolean figures = Characters.ready();
            glazed = false;
            drawShadows(state, figures);
            boolean inTheGlass = figures && !outside && !seated && reflection != null
                    && drawReflection(camera, state, tanHalfFieldOfView, aspect, near, far, fogStart, fogEnd);
            stage(STAGE_SHADOWS);
            float[] cabin = seated ? Cabins.wheel(Ride.seatedIn) : null;
            if (cabin != null) {
                drawBehind(camera, state, cabin, far, fogStart, fogEnd, figures);
            }
            drawWorld(camera, state, tanHalfFieldOfView, aspect, near, far, fogStart, fogEnd);
            stage(STAGE_WORLD);
            if (figures) {
                drawFigures(camera, state, tanHalfFieldOfView, aspect, near, far, fogStart, fogEnd);
            }
            stage(STAGE_FIGURES);
            drawSky(camera, state, tanHalfFieldOfView, aspect, near, far);
            if (state.rain > 0f && !overhead) {
                drawRain(camera, state, tanHalfFieldOfView, aspect, near, far);
            }
            // After the sky and the rain, which are both beyond any glass: the sky is painted
            // wherever nothing else is, over whatever was there.
            if (inTheGlass) {
                showReflection(camera, tanHalfFieldOfView, aspect, near, far);
            }
            if (!overhead) {
                drawPanes(camera, state, tanHalfFieldOfView, aspect, fogStart, fogEnd);
            }
            if (glazed) {
                drawGlass();
            }
            drawSteady(camera, tanHalfFieldOfView, aspect, near, far);
            stage(STAGE_SKY);
            drawOcclusion(camera, state, tanHalfFieldOfView, aspect, near, far);
            stage(STAGE_OCCLUSION);
            // Seen from overhead the picture is developed without the lit air.
            if (!overhead) {
                drawVolume(camera, state, tanHalfFieldOfView, aspect, near, far);
            }
            stage(STAGE_AIR);
            drawBloom(exposure);
            stage(STAGE_GLOW);
            drawDeveloped(camera, state, tanHalfFieldOfView, aspect, exposure, near, far);
            stage(STAGE_DEVELOP);
            showSmoothed(drawTarget, viewport);
            Crosshair.draw(viewport[2], viewport[3]);
            stage(STAGE_SMOOTH);
            reportStages();
            int error = GL11.glGetError();
            if (error != 0) {
                failure = "OpenGL error 0x" + Integer.toHexString(error) + " drawing the picture";
            }
        }
        if (failure != null) {
            clearErrors();
            Log.info("first person cannot be drawn on this machine: " + failure);
        }
    }

    private static void drawBehind(ViewCamera.Pose camera, SkyState state, float[] cabin, float far, float fogStart, float fogEnd,
            boolean figures) {
        // What the mirror on the screen shows: the world behind, from where the mirror
        // hangs, drawn small.
        float aheadX = Ride.aheadX, aheadY = Ride.aheadY;
        behind.eyeX = camera.eyeX + (aheadX * cabin[7] - aheadY * cabin[8]) * Cabins.SCALE;
        behind.eyeY = camera.eyeY + (aheadY * cabin[7] + aheadX * cabin[8]) * Cabins.SCALE;
        behind.eyeUp = camera.eyeUp + cabin[9] * Cabins.SCALE;
        // Where the vehicle's wheel was when it was last drawn says where the mirror
        // is, whichever seat the eye is in and however the head leans.
        float[] wheelAt = VehicleMeshes.seatedWheel;
        VehicleMeshes.Parts body = VehicleMeshes.seatedParts;
        if (wheelAt != null) {
            Cabins.mirrorAt(wheelAt, Cabins.house(Ride.seatedIn, body != null ? body.measures : Cabins.STANDARD_MEASURES), aheadX, aheadY, mirrorPlace);
            behind.eyeX = mirrorPlace[0];
            behind.eyeY = mirrorPlace[1];
            behind.eyeUp = mirrorPlace[2];
        }
        behind.forwardX = -aheadX;
        behind.forwardY = -aheadY;
        behind.forwardUp = 0f;
        behind.rightX = -aheadY;
        behind.rightY = aheadX;
        behind.rightUp = 0f;
        behind.upX = 0f;
        behind.upY = 0f;
        behind.upUp = 1f;
        inMirror = true;
        drawWorld(behind, state, MIRROR_TAN, (float) MIRROR_WIDTH / MIRROR_HEIGHT, MIRROR_NEAR, far, fogStart, fogEnd);
        inMirror = false;
        if (figures) {
            drawMirrorFigures(state, far, fogStart, fogEnd);
        }
    }

    /** The developed picture, its edges smoothed, into the part of the game's own picture the frame is written to. */
    private static void showSmoothed(int drawTarget, int[] viewport) {
        GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawTarget);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        if (opacity < 1f) {
            GL11.glEnable(GL_BLEND);
            GL14.glBlendColor(0f, 0f, 0f, opacity);
            GL11.glBlendFunc(GL_CONSTANT_ALPHA, GL_ONE_MINUS_CONSTANT_ALPHA);
        }
        smooth.use();
        GL11.glBindTexture(GL_TEXTURE_2D, shownColor);
        smooth.set("uImage", 0);
        smooth.set("uSize", (float) width, (float) height);
        smooth.set("uOrigin", (float) viewport[0], (float) viewport[1]);
        smooth.set("uShown", (float) viewport[2], (float) viewport[3]);
        quad();
        GL11.glDisable(GL_BLEND);
    }

    /**
     * What the game has bound of one kind, to be put back when the frame is drawn. Asked into
     * an array the frame keeps: the call that hands the answer back makes a buffer for it
     * every time, forty times a frame.
     */
    private static int bound(int kind) {
        GL11.glGetIntegerv(kind, asked);
        return asked[0];
    }

    /** Points OpenGL at the frame's vertices. Done again after the game has drawn anything, which leaves its own arrays set. */
    private static void arrays() {
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        // Any other array the game left switched on would be read for every vertex drawn here,
        // from wherever the game last pointed it and for as many vertices as this frame has.
        for (int attribute = 0; attribute < VERTEX_ATTRIBUTES; attribute++) {
            GL20.glDisableVertexAttribArray(attribute);
        }
        for (int array : OTHER_ARRAYS) {
            GL11.glDisableClientState(array);
        }
        for (int unit = TEXTURE_COORD_UNITS - 1; unit >= 0; unit--) {
            GL13.glClientActiveTexture(GL_TEXTURE0 + unit);
            GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        }
        GL11.glEnableClientState(GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glEnableClientState(GL_COLOR_ARRAY);
        GL11.glEnableClientState(GL_NORMAL_ARRAY);
        ChunkBuffers.point();
    }

    /**
     * Draws the ranges telling the program the size of a texel of each one's texture and the
     * colour it is painted over in, and leaves it told that nothing is painted over.
     */
    private static void drawRanges(ChunkBuffers.Listing listed, int from, int to, Program program) {
        int texel = program.at("uTexel"), painted = program.at("uTint");
        boolean told = false;
        int toldKey = 0;
        for (int i = from; i < to; i++) {
            int texture = listed.texture(i) == 0 ? whiteTexture : listed.texture(i);
            GL11.glBindTexture(GL_TEXTURE_2D, texture);
            tellTexel(texel, texture);
            int key = listed.tint(i);
            if (!told || key != toldKey) {
                WallSides.tint(key, tint);
                GL20.glUniform4f(painted, tint[0], tint[1], tint[2], tint[3]);
                told = true;
                toldKey = key;
            }
            listed.draw(i);
        }
        GL20.glUniform4f(painted, 0f, 0f, 0f, 0f);
    }

    /**
     * Tells the program in use, at a uniform's place, the size of a texel of the texture bound,
     * whose name is given. The driver is asked a texture's size the first time a frame draws
     * with it and again every few seconds, not at every draw: an answer has to wait for
     * everything the driver has been handed so far, and there is a draw between each two.
     * Asked again because the game may delete a texture and give its name to another.
     */
    private static void tellTexel(int at, int texture) {
        int mask = texelOf.length - 1;
        int slot = texture * 0x9E3779B1 >>> 16 & mask;
        while (texelOf[slot] != 0 && texelOf[slot] != texture) {
            slot = slot + 1 & mask;
        }
        if (texelOf[slot] == 0 || frameNumber - texelRead[slot] >= TEXEL_KEPT_FRAMES + (texture & TEXEL_KEPT_FRAMES - 1)) {
            if (texelOf[slot] == 0 && ++texelsKnown * 2 > texelOf.length) {
                int[] names = texelOf, wide = texelWide, high = texelHigh, read = texelRead;
                texelOf = new int[names.length * 2];
                texelWide = new int[names.length * 2];
                texelHigh = new int[names.length * 2];
                texelRead = new int[names.length * 2];
                mask = texelOf.length - 1;
                for (int old = 0; old < names.length; old++) {
                    if (names[old] != 0) {
                        int moved = names[old] * 0x9E3779B1 >>> 16 & mask;
                        while (texelOf[moved] != 0) {
                            moved = moved + 1 & mask;
                        }
                        texelOf[moved] = names[old];
                        texelWide[moved] = wide[old];
                        texelHigh[moved] = high[old];
                        texelRead[moved] = read[old];
                    }
                }
                slot = texture * 0x9E3779B1 >>> 16 & mask;
                while (texelOf[slot] != 0) {
                    slot = slot + 1 & mask;
                }
            }
            texelOf[slot] = texture;
            texelWide[slot] = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
            texelHigh[slot] = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
            texelRead[slot] = frameNumber;
        }
        GL20.glUniform2f(at, 1f / texelWide[slot], 1f / texelHigh[slot]);
    }

    /** Draws the ranges but the panes of windows, which are drawn last of all: see drawPanes. */
    private static void drawRanges(ChunkBuffers.Listing listed, int from, int to) {
        for (int i = from; i < to; i++) {
            if (listed.tint(i) == WallSides.PANES) {
                continue;
            }
            GL11.glBindTexture(GL_TEXTURE_2D, listed.texture(i) == 0 ? whiteTexture : listed.texture(i));
            listed.draw(i);
        }
    }

    /**
     * Points OpenGL at the models, handing them to the graphics card the first time.
     *
     * A pointer is read as a place in whichever buffer is bound when it is set, and as memory
     * of this process when none is. So the buffer is bound while these three are set and no
     * longer: the rain and the vehicle's glass set pointers to memory of their own with none
     * bound, and one of them set with this still bound would be taken for a place in it, far
     * past its end. The models carry no colour, and the colour array would be read from the
     * chunks' buffer at a model's vertex numbers, so that array is off until arrays() puts it
     * back.
     */
    private static void modelArrays() {
        boolean made = modelBuffer != 0;
        if (!made) {
            modelBuffer = GL15.glGenBuffers();
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, modelBuffer);
        if (!made) {
            float[] models = Models.buffer();
            GL15.glBufferData(GL_ARRAY_BUFFER, buffer(models.length).put(models).flip(), GL_STATIC_DRAW);
        }
        GL13.glClientActiveTexture(GL_TEXTURE0);
        GL11.glVertexPointer(3, GL_FLOAT, MODEL_STRIDE, 0L);
        GL11.glNormalPointer(GL_FLOAT, MODEL_STRIDE, 3L * Float.BYTES);
        GL11.glTexCoordPointer(2, GL_FLOAT, MODEL_STRIDE, 6L * Float.BYTES);
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Draws the furniture standing on the chunks the frame shows, each piece from the one copy
     * of its model the graphics card holds, and leaves the frame's own arrays set as it found
     * them.
     *
     * @param lit     whether the program lights and colours what it draws, or only casts it into the map heldBy last took the bounds of
     * @param within  the eye of a picture that pieces wholly behind it or beyond far are left out of, or null to draw them all
     */
    private static void drawModels(Program program, boolean lit, ViewCamera.Pose within, float far) {
        boolean any = false;
        int bound = 0;
        int placeAt = 0, footAt = 0, turnAt = 0, litAt = 0, alongAt = 0, downAt = 0, acrossAt = 0, surfacesAt = 0, dyeAt = 0, pictureAt = 0, texelAt = 0;
        // The piece whose turn, surfaces, dye and picture the program was last told, none yet: a
        // row of one kind of shelf shares all four, and telling the program what it already
        // holds is a call into the driver for nothing.
        int told = -1;
        float[] toldOf = null;
        for (int chunk = 0; chunk < drawn.size(); chunk++) {
            Models.Instances placed = drawn.get(chunk).placed;
            float[] f = placed.floats;
            int[] whole = placed.ints;
            for (int piece = 0; piece < placed.count; piece++) {
                int at = piece * Models.Instances.FLOATS, ints = piece * Models.Instances.INTS;
                int ball = at + Models.Instances.BALL;
                if (within != null) {
                    float ahead = (f[ball] - within.eyeX) * within.forwardX + (f[ball + 1] - within.eyeY) * within.forwardY
                            + (f[ball + 2] - within.eyeUp) * within.forwardUp;
                    if (ahead < -f[ball + 3] || ahead > far + f[ball + 3]) {
                        continue;
                    }
                } else if (!lit && mapBounds.outside(f[ball], f[ball + 1], f[ball + 2], f[ball + 3], f[ball + 3], f[ball + 3])) {
                    // A piece wholly outside the map being drawn puts nothing in it.
                    continue;
                }
                if (!any) {
                    any = true;
                    modelArrays();
                    program.set("uModel", 1f);
                    placeAt = program.at("uPlace");
                    footAt = program.at("uFoot");
                    turnAt = program.at("uTurn");
                    if (lit) {
                        litAt = program.at("uLit");
                        alongAt = program.at("uLitAlong");
                        downAt = program.at("uLitDown");
                        acrossAt = program.at("uLitAcross");
                        surfacesAt = program.at("uSurfaces");
                        dyeAt = program.at("uDye");
                        pictureAt = program.at("uPicture");
                        texelAt = program.at("uTexel");
                    } else {
                        GL11.glBindTexture(GL_TEXTURE_2D, whiteTexture);
                    }
                }
                int place = at + Models.Instances.PLACE, foot = at + Models.Instances.FOOT, turn = at + Models.Instances.TURN;
                GL20.glUniform4f(placeAt, f[place], f[place + 1], f[place + 2], f[place + 3]);
                GL20.glUniform3f(footAt, f[foot], f[foot + 1], f[foot + 2]);
                if (toldOf == null || differ(f, turn, toldOf, told + Models.Instances.TURN, 4)) {
                    GL20.glUniform4f(turnAt, f[turn], f[turn + 1], f[turn + 2], f[turn + 3]);
                }
                if (lit) {
                    int light = at + Models.Instances.LIGHT, surfaces = at + Models.Instances.SURFACES, dye = at + Models.Instances.DYE;
                    GL20.glUniform3f(litAt, f[light], f[light + 1], f[light + 2]);
                    GL20.glUniform3f(alongAt, f[light + 3], f[light + 4], f[light + 5]);
                    GL20.glUniform3f(downAt, f[light + 6], f[light + 7], f[light + 8]);
                    GL20.glUniform3f(acrossAt, f[light + 9], f[light + 10], f[light + 11]);
                    if (toldOf == null || differ(f, surfaces, toldOf, told + Models.Instances.SURFACES, 3)) {
                        GL20.glUniform3f(surfacesAt, f[surfaces], f[surfaces + 1], f[surfaces + 2]);
                    }
                    if (toldOf == null || differ(f, dye, toldOf, told + Models.Instances.DYE, 4)) {
                        GL20.glUniform4f(dyeAt, f[dye], f[dye + 1], f[dye + 2], f[dye + 3]);
                    }
                    int picture = at + Models.Instances.PICTURE;
                    if (toldOf == null || differ(f, picture, toldOf, told + Models.Instances.PICTURE, 4)) {
                        GL20.glUniform4f(pictureAt, f[picture], f[picture + 1], f[picture + 2], f[picture + 3]);
                    }
                    int texture = whole[ints + Models.Instances.TEXTURE] == 0 ? whiteTexture : whole[ints + Models.Instances.TEXTURE];
                    if (texture != bound) {
                        bound = texture;
                        GL11.glBindTexture(GL_TEXTURE_2D, texture);
                        tellTexel(texelAt, texture);
                    }
                }
                toldOf = f;
                told = at;
                GL11.glDrawArrays(GL_TRIANGLES, whole[ints + Models.Instances.FIRST], whole[ints + Models.Instances.COUNT]);
            }
        }
        if (any) {
            program.set("uModel", 0f);
            arrays();
        }
    }

    /** Whether two runs of floats differ anywhere, bit for bit: a number that is not one differs from itself here, which only costs a call. */
    private static boolean differ(float[] a, int from, float[] b, int against, int count) {
        for (int i = 0; i < count; i++) {
            if (a[from + i] != b[against + i]) {
                return true;
            }
        }
        return false;
    }

    private static void drawShadows(SkyState state, boolean figures) {
        lightMatrix(nearMatrix, state, NEAR_EXTENT, NEAR_RANGE);
        lightMatrix(farMatrix, state, FAR_EXTENT, FAR_RANGE);
        GL11.glDisable(GL_SCISSOR_TEST);
        GL11.glDisable(GL_STENCIL_TEST);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);
        GL11.glDisable(GL_ALPHA_TEST);
        GL11.glDisable(GL_LIGHTING);
        GL11.glDisable(GL_FOG);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glColorMask(false, false, false, false);
        // A surface would otherwise shade itself where the map's depth is a hair nearer the
        // light than the surface it was drawn from.
        GL11.glEnable(GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(2.5f, 6f);
        GL11.glViewport(0, 0, SHADOW_SIZE, SHADOW_SIZE);
        shadow.use();
        shadow.set("uTexture", 0);
        shadow.set("uWind", state.windX, state.windY, state.seconds);
        shadow.set("uSunWay", state.lightX, state.lightY, state.lightUp);
        // The game magnifies its sprites pixel by pixel, which up close is a wall of squares.
        // This frame reads them through a sampler of its own that blends between pixels, and
        // leaves the textures' own settings as the game set them.
        GL33.glBindSampler(0, spriteSampler);
        castSun(state, figures);
        castBeams(state, figures);
        GL13.glActiveTexture(GL_TEXTURE0 + BEAM_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, beamMap);
        GL13.glActiveTexture(GL_TEXTURE0);
        castRoof(state);
        heldOf = drawn.size();
        GL11.glDisable(GL_POLYGON_OFFSET_FILL);
        GL11.glColorMask(true, true, true, true);
    }

    /** The near and the far shadow map, along the light. */
    private static void castSun(SkyState state, boolean figures) {
        for (int map = 0; map < 2; map++) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, map == 0 ? nearTarget : farTarget);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL_DEPTH_BUFFER_BIT);
            shadow.set("uMatrix", map == 0 ? nearMatrix : farMatrix);
            ChunkBuffers.Listing held = heldBy(map == 0 ? nearMatrix : farMatrix, state, map == 0 ? nearListed : farListed, map == 0 ? NEAR_MAP : FAR_MAP);
            drawRanges(held, 0, held.solidRanges());
            drawModels(shadow, false, null, 0f);
        }
        if (figures) {
            // The game's own draw of each character, with the light's matrix where its camera
            // would be. It sets its own program, buffers and culling, so the frame's are put back.
            for (int map = 0; map < 2; map++) {
                GL30.glBindFramebuffer(GL_FRAMEBUFFER, map == 0 ? nearTarget : farTarget);
                Characters.cast(map == 0 ? nearMatrix : farMatrix);
            }
            resumeCasting();
        }
    }

    private static void castBeams(SkyState state, boolean figures) {
        // The scene as the nearest lit headlights see it, so that what stands in their beam
        // throws its shadow down the road.
        float[] beams = Headlights.beams;
        heldChunks[BEAM_MAP] = 0;
        if (beams[6] > 0f) {
            lamp.eyeX = beams[0];
            lamp.eyeY = beams[1];
            lamp.eyeUp = beams[2];
            lamp.forwardX = beams[3];
            lamp.forwardY = beams[4];
            lamp.forwardUp = beams[5];
            float flat = (float) Math.hypot(beams[3], beams[4]);
            lamp.rightX = beams[4] / flat;
            lamp.rightY = -beams[3] / flat;
            lamp.rightUp = 0f;
            lamp.upX = lamp.rightY * lamp.forwardUp - lamp.rightUp * lamp.forwardY;
            lamp.upY = lamp.rightUp * lamp.forwardX - lamp.rightX * lamp.forwardUp;
            lamp.upUp = lamp.rightX * lamp.forwardY - lamp.rightY * lamp.forwardX;
            float[] seen = viewed, cast = projection;
            ViewCamera.view(lamp, seen);
            ViewCamera.projection(BEAM_TAN, 1f, BEAM_NEAR, BEAM_FAR, cast);
            for (int column = 0; column < 4; column++) {
                for (int row = 0; row < 4; row++) {
                    float sum = 0f;
                    for (int k = 0; k < 4; k++) {
                        sum += cast[k * 4 + row] * seen[column * 4 + k];
                    }
                    beamMatrix[column * 4 + row] = sum;
                }
            }
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, beamTarget);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL_DEPTH_BUFFER_BIT);
            shadow.use();
            shadow.set("uMatrix", beamMatrix);
            shadow.set("uSunWay", 0f, 0f, 1f);
            GL13.glActiveTexture(GL_TEXTURE0);
            ChunkBuffers.Listing held = heldBy(beamMatrix, state, beamListed, BEAM_MAP);
            drawRanges(held, 0, held.solidRanges());
            drawModels(shadow, false, null, 0f);
            if (figures) {
                Characters.castCharacters(beamMatrix);
                resumeCasting();
            }
        }
    }

    /** Drawn whatever the weather: the rain is kept off by it, and the light of the sky is traced against it. */
    private static void castRoof(SkyState state) {
        float texel = 2f * ROOF_EXTENT / SHADOW_SIZE;
        Arrays.fill(roofMatrix, 0f);
        roofMatrix[0] = 1f / ROOF_EXTENT;
        roofMatrix[12] = -(float) Math.floor(focusX / texel) * texel / ROOF_EXTENT;
        roofMatrix[5] = 1f / ROOF_EXTENT;
        roofMatrix[13] = -(float) Math.floor(focusY / texel) * texel / ROOF_EXTENT;
        roofMatrix[10] = -1f / ROOF_RANGE;
        roofMatrix[14] = focusUp / ROOF_RANGE;
        roofMatrix[15] = 1f;
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, roofTarget);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL_DEPTH_BUFFER_BIT);
        shadow.use();
        shadow.set("uMatrix", roofMatrix);
        shadow.set("uSunWay", 0f, 0f, 1f);
        ChunkBuffers.Listing held = heldBy(roofMatrix, state, roofListed, ROOF_MAP);
        drawRanges(held, 0, held.solidRanges());
        drawModels(shadow, false, null, 0f);
    }

    /**
     * The frame's chunks that a map drawn with this matrix can hold, as draws, and the map's
     * bounds left in mapBounds for the furniture. A chunk whose every vertex, wherever the wind has moved it,
     * is outside the map puts nothing in it, drawn or not, so the map is the same texels with
     * it left out. Into own when any is left out, and all of them otherwise.
     */
    private static ChunkBuffers.Listing heldBy(float[] matrix, SkyState state, ChunkBuffers.Listing own, int map) {
        mapBounds.of(matrix);
        if (leftOut.length < drawn.size()) {
            leftOut = new boolean[drawn.size() * 2];
        }
        float blown = BLOWN_MOST * (float) Math.hypot(state.windX, state.windY);
        int left = 0;
        for (int i = 0; i < drawn.size(); i++) {
            WorldScene.ChunkMeshes build = drawn.get(i);
            leftOut[i] = build.solidRuns == 0 || mapBounds.outside((build.minX + build.maxX) / 2f, (build.minY + build.maxY) / 2f, (build.minUp + build.maxUp) / 2f,
                    (build.maxX - build.minX) / 2f + blown, (build.maxY - build.minY) / 2f + blown, (build.maxUp - build.minUp) / 2f);
            if (leftOut[i]) {
                left++;
            }
        }
        heldChunks[map] = drawn.size() - left;
        if (left == 0) {
            return all;
        }
        own.of(drawn, leftOut, false);
        return own;
    }

    private static void drawSteady(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far) {
        int next = 1 - keptNow;
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisable(GL_BLEND);
        GL11.glDepthMask(false);
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, keptTarget[next]);
        GL11.glViewport(0, 0, width, height);
        steady.use();
        steady.set("uScene", 0);
        steady.set("uBefore", 1);
        steady.set("uDepth", 2);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneDepth);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, keptColor[keptNow]);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneColor);
        steady.set("uSize", (float) width, (float) height);
        steady.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        steady.set("uNear", near);
        steady.set("uFar", far);
        steady.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
        setBasis(steady, camera);
        steady.set("uEyeBefore", before.eyeX, before.eyeY, before.eyeUp);
        steady.set("uRightBefore", before.rightX, before.rightY, before.rightUp);
        steady.set("uUpBefore", before.upX, before.upY, before.upUp);
        steady.set("uForwardBefore", before.forwardX, before.forwardY, before.forwardUp);
        steady.set("uKept", keptAny && !WITHOUT.contains("steady") ? KEPT : 0f);
        steady.set("uOwnWithin", seated ? 3.2f : 0f);
        quad();
        // The rest of the frame is made from the steadied picture.
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        copy.use();
        GL11.glBindTexture(GL_TEXTURE_2D, keptColor[next]);
        copy.set("uSource", 0);
        copy.set("uSize", (float) width, (float) height);
        quad();
        keptNow = next;
        keptAny = true;
        before.set(camera);
        GL11.glDepthMask(true);
    }

    /** The rain, into the picture after the world and the sky and before anything is made of the picture. */
    private static void drawRain(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        GL11.glViewport(0, 0, width, height);
        GL11.glMatrixMode(GL_PROJECTION);
        ViewCamera.projection(tanHalfFieldOfView, aspect, near, far, projection);
        GL11.glLoadMatrixf(projection);
        GL11.glMatrixMode(GL_MODELVIEW);
        ViewCamera.view(camera, viewed);
        GL11.glLoadMatrixf(viewed);
        arrays();
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL_NORMAL_ARRAY);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, rainDrops);
        GL11.glVertexPointer(4, GL_FLOAT, 0, 0L);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glEnable(GL_BLEND);
        // Added to what is there. The picture's alpha is left alone: it says what mirrors.
        GL14.glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_ZERO, GL_ONE);
        rain.use();
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, roofMap);
        rain.set("uRoof", 0);
        rain.set("uRoofMatrix", roofMatrix);
        rain.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
        rain.set("uTime", state.seconds);
        rain.set("uWind", state.windX, state.windY);
        rain.set("uRain", state.rain);
        rain.set("uSeated", seated ? 1f : 0f);
        setBeams(rain);
        // A drop shows the sky it falls through.
        rain.set("uTint", 1.6f * (float) Math.pow(state.horizonRed, 2.2), 1.6f * (float) Math.pow(state.horizonGreen, 2.2),
                1.6f * (float) Math.pow(state.horizonBlue, 2.2));
        GL11.glDrawArrays(GL_TRIANGLES, 0, RAIN_DROPS * 6);
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisableClientState(GL_VERTEX_ARRAY);
    }

    /**
     * The matrix that looks along the light at a square of ground centred on the focus. The
     * centre moves a whole texel of the map at a time, or the shadows' edges would crawl as
     * the eye moves.
     */
    private static void lightMatrix(float[] m, SkyState state, float extent, float range) {
        float lx = state.lightX, ly = state.lightY, lu = state.lightUp;
        float rx = -ly, ry = lx;
        float flat = (float) Math.sqrt(rx * rx + ry * ry);
        if (flat < 1e-4f) {
            rx = 1f;
            ry = 0f;
        } else {
            rx /= flat;
            ry /= flat;
        }
        float ux = -lu * ry, uy = lu * rx, uu = lx * ry - ly * rx;
        float texel = 2f * extent / SHADOW_SIZE;
        float centreRight = (float) Math.floor((rx * focusX + ry * focusY) / texel) * texel;
        float centreUp = (float) Math.floor((ux * focusX + uy * focusY + uu * focusUp) / texel) * texel;
        float centreAlong = lx * focusX + ly * focusY + lu * focusUp;
        m[0] = rx / extent; m[4] = ry / extent; m[8] = 0f; m[12] = -centreRight / extent;
        m[1] = ux / extent; m[5] = uy / extent; m[9] = uu / extent; m[13] = -centreUp / extent;
        m[2] = -lx / range; m[6] = -ly / range; m[10] = -lu / range; m[14] = centreAlong / range;
        m[3] = 0f; m[7] = 0f; m[11] = 0f; m[15] = 1f;
    }

    private static void drawWorld(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect,
            float near, float far, float fogStart, float fogEnd) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, inMirror ? mirrorTarget : sceneTarget);
        GL11.glViewport(0, 0, inMirror ? MIRROR_WIDTH : width, inMirror ? MIRROR_HEIGHT : height);
        // In the mirror, where nothing is drawn is marked, and the sky is put there when the
        // glass is drawn.
        // The pass leaves the arrays and the sprite sampler unbound when it ends, and it may
        // run twice in a frame: once for the mirror, once for the eye.
        arrays();
        GL33.glBindSampler(0, spriteSampler);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDisable(GL_BLEND);
        // A clear only reaches what the masks let it write.
        GL11.glDepthMask(true);
        GL11.glColorMask(true, true, true, true);
        GL11.glClearColor(inMirror ? -1f : 0f, 0f, 0f, 1f);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        // Each frame the world is drawn a fraction of a pixel to one side of the last, a
        // different way each time round a short cycle. The steadying pass mixes the frames,
        // and an edge that falls between pixels comes out as the mean of where it fell: smooth,
        // and with more detail than one frame's pixels hold.
        ViewCamera.projection(tanHalfFieldOfView, aspect, near, far, projection);
        if (!inMirror) {
            jitterAt = (jitterAt + 1) % JITTER_X.length;
            projection[8] += JITTER_X[jitterAt] / width;
            projection[9] += JITTER_Y[jitterAt] / height;
            Characters.aside(JITTER_X[jitterAt] / width, JITTER_Y[jitterAt] / height);
        }
        tellWorld(camera, state, tanHalfFieldOfView, aspect, fogStart, fogEnd);
        world.set("uLaid", 0f);
        drawRanges(all, 0, all.solidRanges(), world);
        // The mirror's eye is not the eye the chunks were chosen for, so nothing is left out of it.
        drawModels(world, true, inMirror ? null : camera, far);
        // What is laid over the floors, mixed in by how see-through each pixel is, and the grass
        // that stands on them.
        GL11.glEnable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        world.set("uCut", 0.02f);
        world.set("uLaid", 1f);
        world.set("uTexel", 0f, 0f);
        drawRanges(all, all.solidRanges(), all.ranges());
        GL11.glDisable(GL_BLEND);
        GL11.glDepthMask(true);
        leaveWorld();
    }

    /**
     * The panes of the windows the picture builds, mixed in over everything seen through them:
     * the room or the street, the people in it, the sky and the rain, which is why they are
     * drawn after all of those. A pane hides nothing and so leaves the picture's depth alone.
     */
    private static void drawPanes(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float fogStart, float fogEnd) {
        int first = all.solidRanges();
        while (first < all.ranges() && all.tint(first) != WallSides.PANES) {
            first++;
        }
        if (first == all.ranges()) {
            return;
        }
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        GL11.glViewport(0, 0, width, height);
        arrays();
        GL33.glBindSampler(0, spriteSampler);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glEnable(GL_BLEND);
        // The picture's alpha says how much of a mirror each pixel is, and stays what lies behind the pane.
        GL14.glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
        tellWorld(camera, state, tanHalfFieldOfView, aspect, fogStart, fogEnd);
        world.set("uLaid", 0f);
        world.set("uTexel", 0f, 0f);
        GL11.glBindTexture(GL_TEXTURE_2D, whiteTexture);
        for (int i = first; i < all.ranges(); i++) {
            if (all.tint(i) == WallSides.PANES) {
                all.draw(i);
            }
        }
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
        leaveWorld();
    }

    /**
     * Makes the world's program the one in use and tells it the frame: the eye, the sun, the
     * shadow maps, the weather. The projection is the one drawWorld last made, so whatever is
     * drawn later in the frame lies on what was drawn first to the fraction of a pixel.
     */
    private static void tellWorld(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float fogStart, float fogEnd) {
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadMatrixf(projection);
        GL11.glMatrixMode(GL_MODELVIEW);
        // Only the turn: the world's vertex program takes the eye's place off each vertex itself.
        ViewCamera.turned(camera, viewed);
        GL11.glLoadMatrixf(viewed);

        world.use();
        world.set("uTexture", 0);
        world.set("uShadowNear", 1);
        world.set("uShadowFar", 2);
        bindMaterials();
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, farMap);
        GL13.glActiveTexture(GL_TEXTURE0);
        world.set("uNearMatrix", nearMatrix);
        world.set("uFarMatrix", farMatrix);
        world.set("uShadowTexel", 1f / SHADOW_SIZE);
        GL13.glActiveTexture(GL_TEXTURE0 + UNITS - 2);
        GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
        GL33.glBindSampler(UNITS - 2, depthSampler);
        GL13.glActiveTexture(GL_TEXTURE0);
        world.set("uShadowDepth", UNITS - 2);
        GL13.glActiveTexture(GL_TEXTURE0 + UNITS - 1);
        GL11.glBindTexture(GL_TEXTURE_2D, roofMap);
        GL33.glBindSampler(UNITS - 1, depthSampler);
        GL13.glActiveTexture(GL_TEXTURE0);
        world.set("uHeights", UNITS - 1);
        world.set("uRoofMatrix", roofMatrix);
        world.set("uCover", state.cover);
        world.set("uDrift", state.driftX, state.driftY);
        // Texels of soft edge for each unit of the map's depth between caster and ground.
        world.set("uSoftPerDepth", 2f * NEAR_RANGE * SUN_WIDTH * SHADOW_SIZE / (2f * NEAR_EXTENT));
        world.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
        world.set("uRangeFrom", rangeFrom);
        world.set("uCutAbove", cutAbove);
        world.set("uCutBox", cutBox[0], cutBox[1], cutBox[2], cutBox[3]);
        if (overhead && ViewCamera.project(camera, tanHalfFieldOfView, aspect, focusX, focusY, focusUp - HOLE_BELOW_EYE, holeAt)) {
            world.set("uHole", (holeAt[0] * 0.5f + 0.5f) * width, (holeAt[1] * 0.5f + 0.5f) * height, HOLE_OF_HEIGHT * height);
            float along = camera.forwardX * focusX + camera.forwardY * focusY + camera.forwardUp * focusUp;
            // The ground they stand on is not in the way.
            world.set("uHoleUp", focusUp - HOLE_FLOOR_BELOW_EYE);
            world.set("uHoleFrom", camera.forwardX, camera.forwardY, camera.forwardUp, along - HOLE_BEFORE);
        } else {
            world.set("uHole", 0f, 0f, 0f);
        }
        // Nearer the camera, along the ground. The overhead eye is to the south-east, where
        // that is a greater x and y together, and the lengths here are in those units.
        float flat = (float) Math.sqrt((camera.forwardX * camera.forwardX + camera.forwardY * camera.forwardY) / 2f);
        float nearerX = -camera.forwardX / flat, nearerY = -camera.forwardY / flat;
        world.set("uCutNear", nearerX, nearerY, nearerX * focusX + nearerY * focusY - CUT_BEHIND * cutBehind);
        world.set("uLid", lid);
        world.set("uLight", state.lightX, state.lightY, state.lightUp);
        setBeams(world);
        setFlash(world);
        world.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
        world.set("uFogStart", fogStart);
        world.set("uFogEnd", fogEnd);
        world.set("uWind", state.windX, state.windY, state.seconds);
        world.set("uWet", state.wet);
        world.set("uSeconds", state.seconds);
        world.set("uRaining", state.rain);
        world.set("uDark", state.night);
        atmosphere(world, state);
        world.set("uCut", SOLID_CUT);
    }

    /** Leaves the arrays and the samplers of the world's program unbound, as every pass that uses it must. */
    private static void leaveWorld() {
        GL33.glBindSampler(0, 0);
        GL33.glBindSampler(UNITS - 2, 0);
        GL33.glBindSampler(UNITS - 1, 0);

        GL11.glDisableClientState(GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL_NORMAL_ARRAY);
    }

    private static void bindMaterials() {
        for (int material = 0; material < MATERIALS.length; material++) {
            GL13.glActiveTexture(GL_TEXTURE0 + FIRST_MATERIAL_UNIT + material);
            GL11.glBindTexture(GL_TEXTURE_2D, materialTexture[material]);
        }
        world.set("uAsphalt", FIRST_MATERIAL_UNIT);
        world.set("uGravel", FIRST_MATERIAL_UNIT + 1);
        world.set("uSoil", FIRST_MATERIAL_UNIT + 2);
        world.set("uLawn", FIRST_MATERIAL_UNIT + 3);
        world.set("uMaterialGain", materialGain[0], materialGain[1], materialGain[2], materialGain[3]);
        world.set("uShingles", FIRST_MATERIAL_UNIT + 4);
        world.set("uPaint", FIRST_MATERIAL_UNIT + 5);
        world.set("uWood", FIRST_MATERIAL_UNIT + 6);
        world.set("uSurfaceGain", materialGain[4], materialGain[5], materialGain[6]);
        world.set("uBark", FIRST_MATERIAL_UNIT + 7);
        world.set("uPlanks", FIRST_MATERIAL_UNIT + 8);
        world.set("uCarpet", FIRST_MATERIAL_UNIT + 9);
        world.set("uTimberGain", materialGain[7], materialGain[8], materialGain[9]);
        world.set("uLeaves", FIRST_MATERIAL_UNIT + 10);
        world.set("uLeafGain", materialGain[10]);
        world.set("uCloth", FIRST_MATERIAL_UNIT + 12);
        world.set("uClothGain", materialGain[12]);
        for (int nature = 0; nature < NATURE.length; nature++) {
            GL13.glActiveTexture(GL_TEXTURE0 + FIRST_NATURE_UNIT + nature);
            GL11.glBindTexture(GL_TEXTURE_2D, natureTexture[nature]);
        }
        world.set("uLitter", FIRST_NATURE_UNIT);
        world.set("uRock", FIRST_NATURE_UNIT + 1);
        world.set("uFrond", FIRST_NATURE_UNIT + 2);
        world.set("uIvy", FIRST_NATURE_UNIT + 3);
        world.set("uNatureGain", natureGain[0], natureGain[1]);
        GL13.glActiveTexture(GL_TEXTURE0 + SPRIG_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, sprigs);
        world.set("uSprigs", SPRIG_UNIT);
    }

    private static void setBasis(Program program, ViewCamera.Pose pose) {
        program.set("uRight", pose.rightX, pose.rightY, pose.rightUp);
        program.set("uUp", pose.upX, pose.upY, pose.upUp);
        program.set("uForward", pose.forwardX, pose.forwardY, pose.forwardUp);
    }

    private static void atmosphere(Program program, SkyState state) {
        program.set("uSun", state.sunX, state.sunY, state.sunUp);
        program.set("uSunColor", state.sunRed, state.sunGreen, state.sunBlue);
        program.set("uZenith", state.zenithRed, state.zenithGreen, state.zenithBlue);
        program.set("uHorizon", state.horizonRed, state.horizonGreen, state.horizonBlue);
        program.set("uGlow", state.glowRed, state.glowGreen, state.glowBlue);
    }

    private static void drawSky(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        GL11.glDepthMask(false);
        GL11.glDisable(GL_DEPTH_TEST);
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, cloudTarget);
        GL11.glViewport(0, 0, levelWidth[0], levelHeight[0]);
        clouds.use();
        GL11.glBindTexture(GL_TEXTURE_3D, noiseTexture);
        clouds.set("uNoise", 0);
        setBasis(clouds, camera);
        clouds.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        clouds.set("uSize", (float) levelWidth[0], (float) levelHeight[0]);
        clouds.set("uLight", state.lightX, state.lightY, state.lightUp);
        clouds.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
        clouds.set("uZenith", state.zenithRed, state.zenithGreen, state.zenithBlue);
        clouds.set("uHorizon", state.horizonRed, state.horizonGreen, state.horizonBlue);
        clouds.set("uCover", state.cover);
        clouds.set("uDrift", state.driftX, state.driftY);
        quad();
        softenInto(softCloudTarget, cloudColor, near, far);
        stage(STAGE_CLOUDS);

        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        GL11.glViewport(0, 0, width, height);
        GL11.glEnable(GL_DEPTH_TEST);
        // Said again here: the game's own draw of the characters leaves its depth test behind.
        GL11.glDepthFunc(GL_LEQUAL);
        sky.use();
        GL11.glBindTexture(GL_TEXTURE_2D, softCloudColor);
        sky.set("uClouds", 0);
        setBasis(sky, camera);
        sky.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        sky.set("uSize", (float) width, (float) height);
        sky.set("uMoon", state.moonX, state.moonY, state.moonUp);
        sky.set("uNight", state.night);
        sky.set("uTime", state.seconds);
        atmosphere(sky, state);
        quad();
        GL11.glDisable(GL_DEPTH_TEST);
    }

    /**
     * With -Dcameraunlock.zomboid.gpuTimes=true, waits for the graphics card to finish what it
     * has been given and charges the time since the last stage to this one. The wait itself
     * slows the frame, so the figures are for comparing stages, not for the frame rate.
     */
    private static void stage(int stage) {
        if (!TIMES) {
            return;
        }
        GL11.glFinish();
        long now = System.nanoTime();
        stageNanos[stage] += now - stageStart;
        stageStart = now;
    }

    private static void reportStages() {
        if (!TIMES || ++stageFrames < 240) {
            return;
        }
        StringBuilder line = new StringBuilder("first person frame stages, ms:");
        for (int i = 0; i < STAGE_NAMES.length; i++) {
            line.append(String.format(Locale.ROOT, " %s %.2f", STAGE_NAMES[i], stageNanos[i] / 1e6 / stageFrames));
            stageNanos[i] = 0;
        }
        stageFrames = 0;
        Log.info(line.toString());
    }

    private static void drawOcclusion(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, occlusionTarget);
        GL11.glViewport(0, 0, levelWidth[0], levelHeight[0]);
        occlusion.use();
        occlusion.set("uFull", (float) width, (float) height);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneColor);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneDepth);
        occlusion.set("uDepth", 0);
        occlusion.set("uScene", 1);
        // The way to the light as the eye sees it: across, up and ahead. With no light above
        // the horizon there is nothing to trace towards.
        if (state.lightUp > 0.02f) {
            occlusion.set("uSunEye", state.lightX * camera.rightX + state.lightY * camera.rightY + state.lightUp * camera.rightUp,
                    state.lightX * camera.upX + state.lightY * camera.upY + state.lightUp * camera.upUp,
                    state.lightX * camera.forwardX + state.lightY * camera.forwardY + state.lightUp * camera.forwardUp);
        } else {
            occlusion.set("uSunEye", 0f, 0f, -3f);
        }
        occlusion.set("uSize", (float) levelWidth[0], (float) levelHeight[0]);
        occlusion.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        occlusion.set("uNear", near);
        occlusion.set("uFar", far);
        quad();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, softTarget);
        soften.use();
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, occlusionColor);
        GL13.glActiveTexture(GL_TEXTURE0);
        soften.set("uSource", 1);
        soften.set("uDepth", 0);
        soften.set("uNear", near);
        soften.set("uFar", far);
        soften.set("uSize", (float) levelWidth[0], (float) levelHeight[0]);
        quad();
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneColor);
        GL13.glActiveTexture(GL_TEXTURE0);
    }

    private static void drawVolume(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, volumeTarget);
        GL11.glViewport(0, 0, levelWidth[0], levelHeight[0]);
        volume.use();
        volume.set("uDepth", 0);
        volume.set("uShadowNear", 1);
        volume.set("uShadowFar", 2);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, farMap);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneDepth);
        volume.set("uNearMatrix", nearMatrix);
        volume.set("uFarMatrix", farMatrix);
        volume.set("uSize", (float) levelWidth[0], (float) levelHeight[0]);
        volume.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        volume.set("uNear", near);
        volume.set("uFar", far);
        volume.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
        setBasis(volume, camera);
        volume.set("uLight", state.lightX, state.lightY, state.lightUp);
        setBeams(volume);
        volume.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
        volume.set("uMist", state.mist);
        quad();
        softenInto(softVolumeTarget, volumeColor, near, far);
    }

    /**
     * The soften pass weighs each pixel by how near its distance is to the middle one's, so it
     * is given the scene's depth and the planes it was drawn between every time: what an
     * earlier pass left in its uniforms is another texture and, on a session's first frame, nought.
     */
    private static void softenInto(int target, int source, float near, float far) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        soften.use();
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneDepth);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, source);
        soften.set("uSource", 0);
        soften.set("uDepth", 1);
        soften.set("uNear", near);
        soften.set("uFar", far);
        soften.set("uSize", (float) levelWidth[0], (float) levelHeight[0]);
        quad();
    }

    /** Halves the picture five times, keeping only what is too bright to show, then adds the halves back up. */
    private static void drawBloom(float exposure) {
        bloomDown.use();
        bloomDown.set("uSource", 0);
        for (int level = 0; level < BLOOM_LEVELS; level++) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, downTarget[level]);
            GL11.glViewport(0, 0, levelWidth[level], levelHeight[level]);
            GL11.glBindTexture(GL_TEXTURE_2D, level == 0 ? sceneColor : downColor[level - 1]);
            int sourceWidth = level == 0 ? width : levelWidth[level - 1];
            int sourceHeight = level == 0 ? height : levelHeight[level - 1];
            bloomDown.set("uSize", (float) levelWidth[level], (float) levelHeight[level]);
            bloomDown.set("uSourceTexel", 1f / sourceWidth, 1f / sourceHeight);
            bloomDown.set("uThreshold", level == 0 ? 1.1f / exposure : 0f);
            quad();
        }
        bloomUp.use();
        bloomUp.set("uLow", 0);
        bloomUp.set("uHigh", 1);
        for (int level = BLOOM_LEVELS - 2; level >= 0; level--) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, upTarget[level]);
            GL11.glViewport(0, 0, levelWidth[level], levelHeight[level]);
            GL13.glActiveTexture(GL_TEXTURE0 + 1);
            GL11.glBindTexture(GL_TEXTURE_2D, downColor[level]);
            GL13.glActiveTexture(GL_TEXTURE0);
            GL11.glBindTexture(GL_TEXTURE_2D, level == BLOOM_LEVELS - 2 ? downColor[level + 1] : upColor[level + 1]);
            bloomUp.set("uSize", (float) levelWidth[level], (float) levelHeight[level]);
            bloomUp.set("uLowTexel", 1f / levelWidth[level + 1], 1f / levelHeight[level + 1]);
            quad();
        }
    }

    private static void drawDeveloped(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float exposure,
            float near, float far) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, shownTarget);
        GL11.glViewport(0, 0, width, height);
        develop.use();
        develop.set("uScene", 0);
        develop.set("uBloom", 1);
        develop.set("uDepth", 2);
        develop.set("uOcclusion", 3);
        develop.set("uWithout", WITHOUT.contains("occlusion") ? 1f : 0f, WITHOUT.contains("air") || overhead ? 1f : 0f, WITHOUT.contains("glow") ? 1f : 0f);
        develop.set("uVolume", 4);
        GL13.glActiveTexture(GL_TEXTURE0 + 4);
        GL11.glBindTexture(GL_TEXTURE_2D, softVolumeColor);
        GL13.glActiveTexture(GL_TEXTURE0 + 3);
        GL11.glBindTexture(GL_TEXTURE_2D, softColor);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneDepth);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, upColor[0]);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneColor);
        develop.set("uSize", (float) width, (float) height);
        develop.set("uExposure", exposure);
        develop.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        develop.set("uNear", near);
        develop.set("uFar", far);
        // The way up in the world, as the eye sees it: across, up and ahead.
        develop.set("uUp", camera.rightUp, camera.upUp, camera.forwardUp);
        develop.set("uAspect", aspect);
        develop.set("uTime", state.seconds);
        boolean sunAhead = ViewCamera.project(camera, tanHalfFieldOfView, aspect, camera.eyeX + state.sunX, camera.eyeY + state.sunY,
                camera.eyeUp + state.sunUp, sunNdc);
        if (sunAhead) {
            develop.set("uSunAt", sunNdc[0] * 0.5f + 0.5f, sunNdc[1] * 0.5f + 0.5f);
            develop.set("uRayColor", state.rayRed, state.rayGreen, state.rayBlue);
        } else {
            develop.set("uRayColor", 0f, 0f, 0f);
        }
        quad();
    }

    private static void drawFigures(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect,
            float near, float far, float fogStart, float fogEnd) {
        // Flesh and cloth first, then painted metal, which is lit as lacquer.
        for (int pass = 0; pass < 2; pass++) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, figureTarget);
            GL11.glViewport(0, 0, width, height);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL20.glDrawBuffers(BOTH_BUFFERS);
            GL11.glClear(GL_COLOR_BUFFER_BIT);
            GL20.glDrawBuffers(FIRST_BUFFER);
            GL11.glEnable(GL_DEPTH_TEST);
            GL11.glDepthFunc(GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glDisable(GL_BLEND);
            smoothSkins(true);
            CharacterDraw.bare = true;
            Characters.draw(camera, tanHalfFieldOfView, aspect, near, far, pass == 1);
            CharacterDraw.bare = false;
            smoothSkins(false);
            // The game's draw leaves its own program, buffers and arrays bound.
            resumeFlat();

            GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
            figureLight.use();
            figureLight.set("uFigure", 0);
            figureLight.set("uDepth", 3);
            figureLight.set("uShadowNear", 1);
            figureLight.set("uShadowFar", 2);
            GL13.glActiveTexture(GL_TEXTURE0 + 3);
            GL11.glBindTexture(GL_TEXTURE_2D, sceneDepth);
            GL13.glActiveTexture(GL_TEXTURE0 + 2);
            GL11.glBindTexture(GL_TEXTURE_2D, farMap);
            GL13.glActiveTexture(GL_TEXTURE0 + 1);
            GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
            GL13.glActiveTexture(GL_TEXTURE0);
            GL11.glBindTexture(GL_TEXTURE_2D, figureColor);
            figureLight.set("uNearMatrix", nearMatrix);
            figureLight.set("uFarMatrix", farMatrix);
            figureLight.set("uSize", (float) width, (float) height);
            figureLight.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
            figureLight.set("uNear", near);
            figureLight.set("uFar", far);
            figureLight.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
            figureLight.set("uRangeFrom", rangeFrom);
            setBasis(figureLight, camera);
            figureLight.set("uLight", state.lightX, state.lightY, state.lightUp);
            setBeams(figureLight);
            setFlash(figureLight);
            figureLight.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
            figureLight.set("uFogStart", fogStart);
            figureLight.set("uFogEnd", fogEnd);
            figureLight.set("uWriteNear", 0f);
            // Only the viewer's own vehicle is cut away from inside, not who stands beside it.
            figureLight.set("uInside", 0f);
            figureLight.set("uOwnAmbient", 1f, 1f, 1f);
            figureLight.set("uFigureAmbient", Characters.ambient);
            GL13.glActiveTexture(GL_TEXTURE0 + NORMAL_UNIT);
            GL11.glBindTexture(GL_TEXTURE_2D, figureNormal);
            GL13.glActiveTexture(GL_TEXTURE0);
            figureLight.set("uFacing", NORMAL_UNIT);
            figureLight.set("uHaveFacing", pass == 0 ? 1f : 0f);
            figureLight.set("uJersey", FIRST_MATERIAL_UNIT + 13);
            figureLight.set("uVinyl", FIRST_MATERIAL_UNIT + 11);
            figureLight.set("uWearGain", materialGain[13], materialGain[11]);
            figureLight.set("uRoots", pass == 0 ? (float) Characters.rootsFound : 0f);
            for (int root = 0; root < Characters.rootsFound && pass == 0; root++) {
                figureLight.set(ROOT_AT[root], Characters.roots[root * 4], Characters.roots[root * 4 + 1], Characters.roots[root * 4 + 2], Characters.roots[root * 4 + 3]);
            }
            atmosphere(figureLight, state);
            figureLight.set("uPainted", pass == 1 ? 1f : 0f);
            quad();
        }

        if (seated) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, repaintedTarget);
            GL11.glDepthMask(true);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL_DEPTH_TEST);
            GL11.glDepthFunc(GL_LEQUAL);
            GL11.glDisable(GL_BLEND);
            Characters.repainted = true;
            smoothSkins(true);
            Characters.drawViewer(camera, tanHalfFieldOfView, aspect, OWN_NEAR, far);
            smoothSkins(false);
            Characters.repainted = false;
        }
        // The viewer's own body, the same way: drawn by the game, lit here.
        clearOwnTarget();
        smoothSkins(seated);
        // What of the body the picture draws itself is drawn unlit, as everyone else is.
        CharacterDraw.bare = !seated;
        boolean own = !outside && Characters.drawViewer(camera, tanHalfFieldOfView, aspect, OWN_NEAR, far);
        CharacterDraw.bare = false;
        smoothSkins(false);
        FloatBuffer glass = own && seated ? VehicleMeshes.seatedPanes : null;
        if (glass != null) {
            drawPaneMask(glass);
        }
        VehicleMeshes.Parts parts = own && seated ? VehicleMeshes.seatedParts : null;
        if (parts != null) {
            drawPartKinds(parts);
        }
        GL13.glActiveTexture(GL_TEXTURE0 + PARTS_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, partsColor);
        GL13.glActiveTexture(GL_TEXTURE0 + PARTS_DEPTH_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, partsDepth);
        GL13.glActiveTexture(GL_TEXTURE0 + WOOD_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, materialTexture[WOOD]);
        // The carpet's own unit is among those the game's vehicle draw may have bound over.
        GL13.glActiveTexture(GL_TEXTURE0 + FIRST_MATERIAL_UNIT + CARPET);
        GL11.glBindTexture(GL_TEXTURE_2D, materialTexture[CARPET]);
        GL13.glActiveTexture(GL_TEXTURE0 + PANE_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, paneColor);
        GL13.glActiveTexture(GL_TEXTURE0);
        figureLight.use();
        figureLight.set("uPanes", PANE_UNIT);
        figureLight.set("uHavePanes", glass != null ? 1f : 0f);
        figureLight.set("uParts", PARTS_UNIT);
        figureLight.set("uPartsDepth", PARTS_DEPTH_UNIT);
        figureLight.set("uParted", parts != null ? 1f : 0f, parts != null && parts.seats ? 1f : 0f);
        figureLight.set("uWood", WOOD_UNIT);
        figureLight.set("uWoodGain", materialGain[WOOD]);
        figureLight.set("uCarpet", FIRST_MATERIAL_UNIT + CARPET);
        figureLight.set("uCarpetGain", materialGain[CARPET]);
        resumeFlat();
        if (own) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
            figureLight.use();
            GL13.glActiveTexture(GL_TEXTURE0 + 4);
            GL11.glBindTexture(GL_TEXTURE_2D, repaintedColor);
            figureLight.set("uRepainted", 4);
            GL13.glActiveTexture(GL_TEXTURE0 + 3);
            GL11.glBindTexture(GL_TEXTURE_2D, ownDepth);
            GL13.glActiveTexture(GL_TEXTURE0);
            GL11.glBindTexture(GL_TEXTURE_2D, ownColor);
            figureLight.set("uNear", OWN_NEAR);
            figureLight.set("uWriteNear", near);
            figureLight.set("uInside", seated ? 1f : 0f);
            // The pass before this was over painted metal. A vehicle sat in is that; a body is not.
            figureLight.set("uPainted", seated ? 1f : 0f);
            GL13.glActiveTexture(GL_TEXTURE0 + NORMAL_UNIT);
            GL11.glBindTexture(GL_TEXTURE_2D, ownNormal);
            GL13.glActiveTexture(GL_TEXTURE0);
            figureLight.set("uFacing", NORMAL_UNIT);
            figureLight.set("uHaveFacing", seated ? 0f : 1f);
            if (!seated) {
                figureLight.set("uFigureAmbient", Characters.ownLight);
            }
            figureLight.set("uVinyl", FIRST_MATERIAL_UNIT + 11);
            figureLight.set("uCloth", FIRST_MATERIAL_UNIT + 12);
            figureLight.set("uCabinGain", materialGain[11], materialGain[12]);
            figureLight.set("uOwnAmbient", Characters.ownAmbient[0], Characters.ownAmbient[1], Characters.ownAmbient[2]);
            GL11.glEnable(GL_DEPTH_TEST);
            GL11.glDepthFunc(GL_ALWAYS);
            GL11.glDepthMask(true);
            figureLight.set("uGlassPass", 0f);
            figureLight.set("uInstruments", Ride.instrumentsLit ? 0.9f * state.night : 0f);
            float[] wheel = seated ? Cabins.wheel(Ride.seatedIn) : null;
            if (wheel == null) {
                figureLight.set("uWheel", 0f, 0f, 0f, 0f);
                figureLight.set("uSeat", 0f, 0f, 0f);
            } else {
                setCabin(camera, wheel, parts);
            }
            figureLight.set("uRaining", state.rain);
            figureLight.set("uGauges", Ride.roadSpeed, Ride.engineSpeed, Ride.fuel);
            figureLight.set("uTime", state.seconds);
            figureLight.set("uAhead", Ride.aheadX, Ride.aheadY);
            quad();
            glazed = seated;
            GL11.glDepthFunc(GL_LEQUAL);
            GL11.glDisable(GL_DEPTH_TEST);
            if (Characters.holding()) {
                drawHeld(camera, state, tanHalfFieldOfView, aspect, near, far);
            }
        }
        Characters.done();
        GL11.glDepthMask(true);
    }

    private static void drawPaneMask(FloatBuffer glass) {
        // Which pixels are the vehicle's glass, drawn from the triangles of it that are:
        // a window's edge is then the straight edge of a triangle.
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, paneTarget);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);
        arrays();
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL_NORMAL_ARRAY);
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glLoadMatrixf(VehicleMeshes.paneMatrix);
        GL11.glVertexPointer(3, GL_FLOAT, 0, glass);
        paneMask.use();
        // The panes of the front of the glasshouse are drawn at a half: the picture
        // builds its own there.
        int rear = VehicleMeshes.seatedRearPanes;
        paneMask.set("uPane", 1f);
        GL11.glDrawArrays(GL_TRIANGLES, 0, rear);
        paneMask.set("uPane", 0.5f);
        GL11.glDrawArrays(GL_TRIANGLES, rear, glass.limit() / 3 - rear);
        GL11.glDisableClientState(GL_VERTEX_ARRAY);
        GL11.glLoadIdentity();
    }

    private static void drawPartKinds(VehicleMeshes.Parts parts) {
        // What part of the cabin each pixel is, from the body's triangles that are not
        // glass, each kind in its own number and the nearest winning.
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, partsTarget);
        GL11.glDepthMask(true);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);
        arrays();
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL_NORMAL_ARRAY);
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glLoadMatrixf(VehicleMeshes.paneMatrix);
        GL11.glVertexPointer(3, GL_FLOAT, 0, parts.triangles);
        partKind.use();
        GL11.glColorMask(true, false, true, true);
        for (int kind = 0; kind < CabinParts.UNSEEN; kind++) {
            if (parts.count[kind] > 0) {
                partKind.set("uKind", (float) kind);
                GL11.glDrawArrays(GL_TRIANGLES, parts.first[kind], parts.count[kind]);
            }
        }
        // What the picture builds its own in place of is marked where it stands before
        // the rest, and hides none of it.
        GL11.glColorMask(false, true, false, false);
        GL11.glDepthMask(false);
        GL11.glDrawArrays(GL_TRIANGLES, parts.first[CabinParts.UNSEEN], parts.count[CabinParts.UNSEEN]);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glDisableClientState(GL_VERTEX_ARRAY);
        GL11.glLoadIdentity();
        GL11.glDisable(GL_DEPTH_TEST);
    }

    /** What the cabin's shader is told of the vehicle sat in. */
    private static void setCabin(ViewCamera.Pose camera, float[] wheel, VehicleMeshes.Parts parts) {
        figureLight.set("uWheel", wheel[0], wheel[1], wheel[2], wheel[3]);
        figureLight.set("uWheelAxis", wheel[4], wheel[5], wheel[6]);
        float[] kit = Cabins.kit(Ride.seatedIn);
        figureLight.set("uKit", kit[0], kit[1], kit[2], kit[3]);
        figureLight.set("uHub", kit[4], kit[5], kit[6]);
        figureLight.set("uCluster", kit[7], kit[8], kit[9]);
        figureLight.set("uHood", kit[10], kit[11]);
        figureLight.set("uDashColor", kit[12], kit[13], kit[14]);
        figureLight.set("uSeatColor", kit[15], kit[16], kit[17]);
        figureLight.set("uGlowColor", kit[18], kit[19], kit[20]);
        figureLight.set("uShift", kit[21]);
        figureLight.set("uTrim", kit[22], kit[23], kit[24], kit[25]);
        figureLight.set("uFittings", kit[26]);
        // The driver's place is laid out round the wheel. Where the body's own wheel
        // is this frame says where the eye is in it, the head leaning or not.
        float[] at = VehicleMeshes.seatedWheel;
        if (at == null) {
            figureLight.set("uSeat", 0f, 0f, 0f);
        } else {
            float toX = camera.eyeX - at[0], toY = camera.eyeY - at[1];
            figureLight.set("uSeat", wheel[0] + (toX * Ride.aheadX + toY * Ride.aheadY) / Cabins.SCALE,
                    wheel[1] + (toY * Ride.aheadX - toX * Ride.aheadY) / Cabins.SCALE, wheel[2] + (camera.eyeUp - at[2]) / Cabins.SCALE);
        }
        float[] measures = parts != null ? parts.measures : Cabins.STANDARD_MEASURES;
        float[] house = Cabins.house(Ride.seatedIn, measures);
        figureLight.set("uHouse", house[0], house[1], house[2]);
        figureLight.set("uMirror", house[3], house[4], house[5]);
        figureLight.set("uPad", wheel[0] + measures[9] / Cabins.SCALE, Cabins.PILLAR_BEHIND);
        figureLight.set("uTrade", Cabins.trade(Ride.seatedIn));
        float[] skin = Cabins.skin(Ride.skin);
        Cabins.Wear wear = Cabins.worn;
        figureLight.set("uArms", Ride.driving ? 1f : 0f);
        figureLight.set("uBare", skin[0], skin[1], skin[2]);
        if (wear.glove() == null) {
            figureLight.set("uSkin", skin[0], skin[1], skin[2]);
        } else {
            Cabins.colour(wear.glove(), worn);
            figureLight.set("uSkin", worn[0], worn[1], worn[2]);
        }
        if (wear.sleeve() == null) {
            figureLight.set("uSleeve", skin[0], skin[1], skin[2], 0f);
        } else {
            Cabins.colour(wear.sleeve(), worn);
            figureLight.set("uSleeve", worn[0], worn[1], worn[2], wear.toTheWrist() ? 1f : 0.5f);
        }
        float sill = wheel[2] + measures[2] / Cabins.SCALE, glassOut = measures[3] / Cabins.SCALE;
        figureLight.set("uScreen", wheel[0] + measures[0] / Cabins.SCALE, wheel[2] + measures[1] / Cabins.SCALE, sill, glassOut);
        figureLight.set("uDash", measures[4] / Cabins.SCALE, wheel[2] + measures[5] / Cabins.SCALE);
        figureLight.set("uDoorMirror", wheel[0] + measures[6] / Cabins.SCALE, wheel[1] - Math.signum(measures[4]) * measures[7] / Cabins.SCALE,
                wheel[2] + measures[8] / Cabins.SCALE);
        figureLight.set("uPaint", VehicleMeshes.seatedPaint[0], VehicleMeshes.seatedPaint[1], VehicleMeshes.seatedPaint[2]);
        figureLight.set("uSteer", Cabins.wheelTurn(Ride.steering));
        figureLight.set("uLamps", Ride.instrumentsLit ? 1f : 0f, Ride.lampsOn ? 1f : 0f);
        figureLight.set("uDome", Ride.doorOpen ? 1f : 0f);
        GL13.glActiveTexture(GL_TEXTURE0 + MIRROR_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, mirrorColor);
        GL13.glActiveTexture(GL_TEXTURE0);
        figureLight.set("uBehind", MIRROR_UNIT);
    }

    private static final float[] worn = new float[3], mirrorPlace = new float[3];

    /**
     * The people and then the vehicles behind, into the mirror's picture of the road: drawn
     * as they are for the eye, against the depth of the world the mirror shows, and lit the
     * same way. Only what Characters.drawBehind finds near enough is drawn at all, and a pass
     * that finds nothing costs nothing more.
     */
    private static void drawMirrorFigures(SkyState state, float far, float fogStart, float fogEnd) {
        float aspect = (float) MIRROR_WIDTH / MIRROR_HEIGHT;
        for (int pass = 0; pass < 2; pass++) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, mirrorFigureTarget);
            GL11.glViewport(0, 0, MIRROR_WIDTH, MIRROR_HEIGHT);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glColorMask(true, true, true, true);
            GL20.glDrawBuffers(BOTH_BUFFERS);
            GL11.glClear(GL_COLOR_BUFFER_BIT);
            GL20.glDrawBuffers(FIRST_BUFFER);
            GL11.glEnable(GL_DEPTH_TEST);
            GL11.glDepthFunc(GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glDisable(GL_BLEND);
            smoothSkins(true);
            CharacterDraw.bare = true;
            int drawn = Characters.drawBehind(behind, MIRROR_TAN, aspect, MIRROR_NEAR, far, pass == 1);
            CharacterDraw.bare = false;
            smoothSkins(false);
            // The game's draw leaves its own program, buffers and arrays bound.
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
            GL13.glActiveTexture(GL_TEXTURE0);
            GL11.glDisable(GL_CULL_FACE);
            GL11.glDisable(GL_BLEND);
            GL11.glDisable(GL_ALPHA_TEST);
            if (drawn == 0) {
                continue;
            }
            GL11.glDisable(GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, mirrorTarget);
            figureLight.use();
            figureLight.set("uFigure", 0);
            figureLight.set("uDepth", 3);
            figureLight.set("uShadowNear", 1);
            figureLight.set("uShadowFar", 2);
            GL13.glActiveTexture(GL_TEXTURE0 + 3);
            GL11.glBindTexture(GL_TEXTURE_2D, mirrorDepth);
            GL13.glActiveTexture(GL_TEXTURE0 + 2);
            GL11.glBindTexture(GL_TEXTURE_2D, farMap);
            GL13.glActiveTexture(GL_TEXTURE0 + 1);
            GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
            GL13.glActiveTexture(GL_TEXTURE0 + NORMAL_UNIT);
            GL11.glBindTexture(GL_TEXTURE_2D, mirrorFigureNormal);
            GL13.glActiveTexture(GL_TEXTURE0);
            GL11.glBindTexture(GL_TEXTURE_2D, mirrorFigureColor);
            figureLight.set("uNearMatrix", nearMatrix);
            figureLight.set("uFarMatrix", farMatrix);
            figureLight.set("uSize", (float) MIRROR_WIDTH, (float) MIRROR_HEIGHT);
            figureLight.set("uTan", MIRROR_TAN * aspect, MIRROR_TAN);
            figureLight.set("uNear", MIRROR_NEAR);
            figureLight.set("uFar", far);
            figureLight.set("uEye", behind.eyeX, behind.eyeY, behind.eyeUp);
            setBasis(figureLight, behind);
            figureLight.set("uLight", state.lightX, state.lightY, state.lightUp);
            setBeams(figureLight);
            setFlash(figureLight);
            figureLight.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
            figureLight.set("uFogStart", fogStart);
            figureLight.set("uFogEnd", fogEnd);
            figureLight.set("uWriteNear", 0f);
            figureLight.set("uInside", 0f);
            figureLight.set("uGlassPass", 0f);
            figureLight.set("uOwnAmbient", 1f, 1f, 1f);
            figureLight.set("uFigureAmbient", Characters.ambient);
            figureLight.set("uFacing", NORMAL_UNIT);
            figureLight.set("uHaveFacing", pass == 0 ? 1f : 0f);
            // In a picture this small the weave of a shirt is finer than a dot of it.
            figureLight.set("uRoots", 0f);
            atmosphere(figureLight, state);
            figureLight.set("uPainted", pass == 1 ? 1f : 0f);
            quad();
        }
        GL11.glDepthMask(true);
    }

    /**
     * The viewer as the glass of the frame's window shows them, into a picture of their own:
     * drawn as anyone is, from an eye on the far side of the glass, and lit by the light
     * where they really stand. False if there was nothing of them to draw.
     */
    private static boolean drawReflection(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect,
            float near, float far, float fogStart, float fogEnd) {
        clearOwnTarget();
        GL11.glViewport(0, 0, width, height);
        GL11.glDepthMask(true);
        smoothSkins(true);
        CharacterDraw.bare = true;
        boolean drawn = Characters.drawReflected(camera, reflection, tanHalfFieldOfView, aspect, near, far);
        CharacterDraw.bare = false;
        smoothSkins(false);
        resumeFlat();
        if (!drawn) {
            return false;
        }
        // The eye that sees the real viewer where this one sees them in the glass: with it
        // the light finds where they stand, in the room and not out in the street.
        OwnReflection.mirror(camera, reflection, inGlass);
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, selfTarget);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL_COLOR_BUFFER_BIT);
        figureLight.use();
        figureLight.set("uFigure", 0);
        figureLight.set("uDepth", 3);
        figureLight.set("uShadowNear", 1);
        figureLight.set("uShadowFar", 2);
        GL13.glActiveTexture(GL_TEXTURE0 + 3);
        GL11.glBindTexture(GL_TEXTURE_2D, ownDepth);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, farMap);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
        GL13.glActiveTexture(GL_TEXTURE0 + NORMAL_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, ownNormal);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, ownColor);
        figureLight.set("uNearMatrix", nearMatrix);
        figureLight.set("uFarMatrix", farMatrix);
        figureLight.set("uSize", (float) width, (float) height);
        figureLight.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        figureLight.set("uNear", near);
        figureLight.set("uFar", far);
        figureLight.set("uEye", inGlass.eyeX, inGlass.eyeY, inGlass.eyeUp);
        figureLight.set("uRangeFrom", 0f);
        setBasis(figureLight, inGlass);
        figureLight.set("uLight", state.lightX, state.lightY, state.lightUp);
        setBeams(figureLight);
        setFlash(figureLight);
        figureLight.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
        figureLight.set("uFogStart", fogStart);
        figureLight.set("uFogEnd", fogEnd);
        figureLight.set("uWriteNear", 0f);
        figureLight.set("uInside", 0f);
        figureLight.set("uGlassPass", 0f);
        figureLight.set("uHavePanes", 0f);
        figureLight.set("uParted", 0f, 0f);
        figureLight.set("uOwnAmbient", 1f, 1f, 1f);
        figureLight.set("uFigureAmbient", Characters.reflectedLight);
        figureLight.set("uFacing", NORMAL_UNIT);
        figureLight.set("uHaveFacing", 1f);
        figureLight.set("uJersey", FIRST_MATERIAL_UNIT + 13);
        figureLight.set("uVinyl", FIRST_MATERIAL_UNIT + 11);
        figureLight.set("uWearGain", materialGain[13], materialGain[11]);
        figureLight.set("uRoots", 0f);
        atmosphere(figureLight, state);
        figureLight.set("uPainted", 0f);
        quad();
        return true;
    }

    /**
     * The picture of the viewer in the glass, added to the scene over the panes of the
     * glass's wall. It is laid before them, so whatever stands before a pane hides it as it
     * hides the pane: a curtain, a chair, the viewer's own hands.
     */
    private static void showReflection(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        GL11.glViewport(0, 0, width, height);
        arrays();
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL_NORMAL_ARRAY);
        ViewCamera.projection(tanHalfFieldOfView, aspect, near, far, selfProjection);
        Characters.aside(selfProjection);
        ViewCamera.turned(camera, selfView);
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadMatrixf(selfProjection);
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glLoadMatrixf(selfView);
        selfShown.use();
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, selfColor);
        selfShown.set("uSelf", 0);
        selfShown.set("uSize", (float) width, (float) height);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glEnable(GL_BLEND);
        // Glass set in the wall: added to what is seen through it, and the picture's mark of
        // what is a mirror left as it is.
        int corners = OwnReflection.corners(reflection, camera, false, selfCorners);
        GL11.glVertexPointer(3, GL_FLOAT, 0, selfCorners);
        selfShown.set("uThrown", OwnReflection.THROWN);
        selfShown.set("uOnly", 0f);
        GL11.glBlendFunc(GL_ONE, GL_ONE);
        GL11.glColorMask(true, true, true, false);
        GL11.glDrawArrays(GL_TRIANGLES, 0, corners);
        // What hangs on the wall, where the viewer covers it, in three steps, since the
        // picture's alpha is the only place to keep a number for each pixel between them.
        // First the alpha is made how much of the pixel the viewer is to be: how much of a
        // mirror the surface was marked as, times what a mirror shows for that.
        corners = OwnReflection.corners(reflection, camera, true, selfCorners);
        GL11.glVertexPointer(3, GL_FLOAT, 0, selfCorners);
        selfShown.set("uOnly", 1f);
        selfShown.set("uMark", OwnReflection.HUNG_THROWN);
        GL11.glColorMask(false, false, false, true);
        GL14.glBlendFuncSeparate(GL_ZERO, GL_ONE, GL_ONE_MINUS_DST_ALPHA, GL_ZERO);
        GL11.glDrawArrays(GL_TRIANGLES, 0, corners);
        // Then the viewer is mixed into the colour by it,
        selfShown.set("uThrown", 1f);
        GL11.glColorMask(true, true, true, false);
        GL11.glBlendFunc(GL_DST_ALPHA, GL_ONE_MINUS_DST_ALPHA);
        GL11.glDrawArrays(GL_TRIANGLES, 0, corners);
        // and last the alpha is set to no mirror at all, so the room traced in the mirror
        // afterwards is not laid over them.
        selfShown.set("uMark", 1f);
        GL11.glColorMask(false, false, false, true);
        GL11.glDisable(GL_BLEND);
        GL11.glDrawArrays(GL_TRIANGLES, 0, corners);
        GL11.glColorMask(true, true, true, true);
        GL11.glDisable(GL_BLEND);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisableClientState(GL_VERTEX_ARRAY);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL_MODELVIEW);
    }

    /** The frame's arrays and depth test again, after the game's own draw of a character into a shadow map. */
    private static void resumeCasting() {
        arrays();
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);
        GL11.glDisable(GL_ALPHA_TEST);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(true);
    }

    /** Nothing of the game's bound and no depth test, for a pass over the whole picture after the game's own draw of a character. */
    private static void resumeFlat() {
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);
        GL11.glDisable(GL_ALPHA_TEST);
        GL11.glDepthMask(false);
    }

    private static void clearOwnTarget() {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, ownTarget);
        GL11.glDepthMask(true);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClearDepth(1.0);
        GL20.glDrawBuffers(BOTH_BUFFERS);
        GL11.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GL20.glDrawBuffers(FIRST_BUFFER);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDisable(GL_BLEND);
    }

    /**
     * The game draws a vehicle's skin with each of its dots a hard square, which is right from
     * its own distance. From the seat a dot is a hand across and the edge of a painted window
     * is a staircase, so there the skin is read with its dots run together.
     */
    private static void smoothSkins(boolean smooth) {
        for (int unit = 0; unit < SKIN_UNITS; unit++) {
            GL33.glBindSampler(unit, smooth ? skinSampler : 0);
        }
    }

    /**
     * The panes of the vehicle the viewer sits in, over everything seen through them: the
     * world, the sky and the rain, which are all outside the glass.
     */
    private static void drawGlass() {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        GL11.glViewport(0, 0, width, height);
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glLoadIdentity();
        figureLight.use();
        GL13.glActiveTexture(GL_TEXTURE0 + 4);
        GL11.glBindTexture(GL_TEXTURE_2D, repaintedColor);
        GL13.glActiveTexture(GL_TEXTURE0 + 3);
        GL11.glBindTexture(GL_TEXTURE_2D, ownDepth);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, ownColor);
        // The cabin's own parts are in the picture's depth, and each pane says how far off
        // it is: a pane is hidden by whatever of the cabin stands before it.
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDepthMask(false);
        GL11.glEnable(GL_BLEND);
        // The picture's alpha is left alone: it says what mirrors.
        GL14.glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
        figureLight.set("uGlassPass", 1f);
        quad();
        figureLight.set("uGlassPass", 0f);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
    }

    /** How bright a headlight is beside the sun at 2.1. */
    private static final float BEAM_STRENGTH = 9f;

    private static void setBeams(Program program) {
        float[] beams = Headlights.beams;
        for (int i = 0; i < Headlights.BEAMS; i++) {
            int at = i * Headlights.STRIDE;
            program.set(BEAM_AT[i], beams[at], beams[at + 1], beams[at + 2], beams[at + 6] * BEAM_STRENGTH);
            program.set(BEAM_WAY[i], beams[at + 3], beams[at + 4], beams[at + 5]);
        }
        program.set("uBeamShadow", BEAM_UNIT);
        program.set("uBeamMatrix", beamMatrix);
    }

    /** The light of a shot's flash, for a program whose source has MuzzleFlash.GLSL in it. */
    private static void setFlash(Program program) {
        MuzzleFlash.light(flash);
        program.set("uFlashAt", flash[0], flash[1], flash[2], flash[3]);
        program.set("uFlashColor", flash[4], flash[5], flash[6]);
    }

    /**
     * The weapon the viewer holds and the arms on it, drawn after the rest of the body into
     * the same picture of their own, cleared between, and lit the same way. Their near plane
     * is their own: the body's cuts away the head and chest, and would cut the weapon's stock
     * off in the middle of the picture.
     */
    private static void drawHeld(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        clearOwnTarget();
        CharacterDraw.bare = true;
        Characters.drawHeld(tanHalfFieldOfView, aspect, HELD_NEAR, far);
        CharacterDraw.bare = false;
        // The game's draw leaves its own program, buffers and arrays bound.
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);
        GL11.glDisable(GL_ALPHA_TEST);
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        figureLight.use();
        GL13.glActiveTexture(GL_TEXTURE0 + 3);
        GL11.glBindTexture(GL_TEXTURE_2D, ownDepth);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, ownColor);
        figureLight.set("uNear", HELD_NEAR);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_ALWAYS);
        GL11.glDepthMask(true);
        quad();
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDisable(GL_DEPTH_TEST);
        if (OwnWeapon.waiting()) {
            drawOwnWeapon(camera, state, tanHalfFieldOfView, aspect, near, far);
        }
        if (ViewModel.hasMuzzle) {
            MuzzleFlash.draw(camera, ViewModel.muzzle[0], ViewModel.muzzle[1], ViewModel.muzzle[2], tanHalfFieldOfView, aspect, width, height);
        } else {
            MuzzleFlash.none();
        }
    }

    /** The weapon the mod has a model of its own for, over the hands that hold it, in the light of the frame. */
    private static void drawOwnWeapon(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        Program program = OwnWeapon.begin();
        atmosphere(program, state);
        setBasis(program, camera);
        setFlash(program);
        program.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
        program.set("uLight", state.lightX, state.lightY, state.lightUp);
        program.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
        program.set("uNearMatrix", nearMatrix);
        program.set("uFarMatrix", farMatrix);
        GL13.glActiveTexture(GL_TEXTURE0 + OwnWeapon.SHADOW_FAR_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, farMap);
        GL13.glActiveTexture(GL_TEXTURE0 + OwnWeapon.SHADOW_NEAR_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
        GL13.glActiveTexture(GL_TEXTURE0);
        OwnWeapon.draw(camera, sceneTarget, sceneColor, ownDepth, tanHalfFieldOfView, aspect, HELD_NEAR, far, near);
    }

    /** How near the eye the held weapon is still drawn, in tiles. */
    private static final float HELD_NEAR = 0.08f;

    private static void quad() {
        GL11.glBegin(GL_TRIANGLE_STRIP);
        GL11.glVertex2f(-1f, -1f);
        GL11.glVertex2f(1f, -1f);
        GL11.glVertex2f(-1f, 1f);
        GL11.glVertex2f(1f, 1f);
        GL11.glEnd();
    }

    /** Builds the programs and the textures that do not depend on the picture's size. Null, or what the driver refused. */
    private static String prepare() {
        String problem = linkPrograms();
        if (problem != null) {
            return problem;
        }
        long start = System.nanoTime();
        GL11.glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        makeNoise();
        makeWhite();
        problem = loadMaterials();
        if (problem == null) {
            problem = OwnWeapon.prepare();
        }
        if (problem != null) {
            return problem;
        }
        makeSamplers();
        problem = makeShadowMaps();
        if (problem == null) {
            problem = makeMirror();
        }
        if (problem != null) {
            return problem;
        }
        makeRainDrops();
        int error = GL11.glGetError();
        if (error != 0) {
            return "OpenGL error 0x" + Integer.toHexString(error) + " making the textures";
        }
        Log.info(String.format(Locale.ROOT, "first person picture ready: %d shaders, four %d shadow maps, cloud noise %d cubed, in %.0f ms",
                17, SHADOW_SIZE, NOISE_SIZE, (System.nanoTime() - start) / 1e6));
        return null;
    }

    /** Null, or what the driver refused. */
    private static String linkPrograms() {
        StringBuilder problem = new StringBuilder();
        world = Program.link("world", WORLD_VERTEX, WORLD_FRAGMENT, problem);
        paneMask = Program.link("pane mask", PipelineShaders.PANE_VERTEX, PipelineShaders.PANE_FRAGMENT, problem);
        selfShown = Program.link("viewer in the glass", PipelineShaders.PANE_VERTEX, OwnReflection.SHOWN, problem);
        partKind = Program.link("cabin parts", PipelineShaders.PANE_VERTEX, PipelineShaders.PART_FRAGMENT, problem);
        shadow = Program.link("shadow", SHADOW_VERTEX, SHADOW_FRAGMENT, problem);
        rain = Program.link("rain", PipelineShaders.RAIN_VERTEX, PipelineShaders.RAIN_FRAGMENT, problem);
        sky = Program.link("sky", SCREEN_VERTEX, Sky.FRAGMENT, problem);
        clouds = Program.link("clouds", SCREEN_VERTEX, Sky.CLOUDS, problem);
        figureLight = Program.link("character light", SCREEN_VERTEX, FIGURE_LIGHT, problem);
        occlusion = Program.link("occlusion", SCREEN_VERTEX, PipelineShaders.OCCLUSION, problem);
        soften = Program.link("occlusion soften", SCREEN_VERTEX, PipelineShaders.SOFTEN, problem);
        volume = Program.link("lit air", SCREEN_VERTEX, PipelineShaders.VOLUME, problem);
        bloomDown = Program.link("glow down", SCREEN_VERTEX, PipelineShaders.BLOOM_DOWN, problem);
        bloomUp = Program.link("glow up", SCREEN_VERTEX, PipelineShaders.BLOOM_UP, problem);
        develop = Program.link("develop", SCREEN_VERTEX, PipelineShaders.DEVELOP, problem);
        smooth = Program.link("smooth", SCREEN_VERTEX, PipelineShaders.SMOOTH, problem);
        steady = Program.link("steady", SCREEN_VERTEX, PipelineShaders.STEADY, problem);
        copy = Program.link("copy", SCREEN_VERTEX, PipelineShaders.COPY, problem);
        return problem.length() > 0 ? problem.toString() : null;
    }

    private static void makeNoise() {
        noiseTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_3D, noiseTexture);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_REPEAT);
        GL12.glTexImage3D(GL_TEXTURE_3D, 0, GL_RGB8, NOISE_SIZE, NOISE_SIZE, NOISE_SIZE, 0, GL_RGB, GL_UNSIGNED_BYTE,
                CloudNoise.build(NOISE_SIZE));

    }

    private static void makeWhite() {
        // What a thing with no sprite is drawn with: the vertex colour alone.
        whiteTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, whiteTexture);
        ByteBuffer white = ByteBuffer.allocateDirect(4);
        white.put((byte) -1).put((byte) -1).put((byte) -1).put((byte) -1).flip();
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, white);
        filter(GL_NEAREST);

    }

    /** Null, or the picture that could not be loaded. */
    private static String loadMaterials() {
        for (int material = 0; material < MATERIALS.length; material++) {
            String missing = loadPicture(MATERIALS[material], material == FIRST_CUTOUT, materialTexture, materialGain, material);
            if (missing != null) {
                return missing;
            }
        }
        for (int nature = 0; nature < NATURE.length; nature++) {
            String missing = loadPicture(NATURE[nature], nature >= FIRST_NATURE_CUTOUT, natureTexture, natureGain, nature);
            if (missing != null) {
                return missing;
            }
        }
        try {
            sprigs = Pictures.load("/materials/sprigs.png", false);
        } catch (IOException e) {
            return "the sprigs could not be read: " + e.getMessage();
        }

        return null;
    }

    private static void makeSamplers() {
        // Reads a shadow map's depth as a number, where the map's own settings compare it.
        depthSampler = sampler(GL_NEAREST, GL_CLAMP_TO_EDGE);
        GL33.glSamplerParameteri(depthSampler, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        skinSampler = sampler(GL_LINEAR, GL_REPEAT);
        spriteSampler = sampler(GL_LINEAR, GL_CLAMP_TO_EDGE);
    }

    private static int sampler(int filter, int wrap) {
        int sampler = GL33.glGenSamplers();
        GL33.glSamplerParameteri(sampler, GL_TEXTURE_MIN_FILTER, filter);
        GL33.glSamplerParameteri(sampler, GL_TEXTURE_MAG_FILTER, filter);
        GL33.glSamplerParameteri(sampler, GL_TEXTURE_WRAP_S, wrap);
        GL33.glSamplerParameteri(sampler, GL_TEXTURE_WRAP_T, wrap);
        return sampler;
    }

    /** The four shadow maps, the mirror's picture and the picture of who stands behind. Null, or what the driver refused. */
    private static String makeShadowMaps() {
        int[] made = new int[2];
        String near = shadowMap(made);
        nearMap = made[0];
        nearTarget = made[1];
        String far = shadowMap(made);
        farMap = made[0];
        farTarget = made[1];
        String roof = shadowMap(made);
        roofMap = made[0];
        roofTarget = made[1];
        String beam = shadowMap(made);
        beamMap = made[0];
        beamTarget = made[1];
        return near != null ? near : far != null ? far : roof != null ? roof : beam;
    }

    private static String makeMirror() {
        String beam = null;
        mirrorColor = texture(GL_RGBA16F, MIRROR_WIDTH, MIRROR_HEIGHT, GL_RGBA, GL_FLOAT, GL_LINEAR);
        mirrorDepth = texture(GL_DEPTH_COMPONENT24, MIRROR_WIDTH, MIRROR_HEIGHT, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
        mirrorTarget = target(mirrorColor);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, mirrorDepth, 0);
        if (beam == null) {
            beam = complete("mirror");
        }
        // Who and what stands behind is drawn against the depth of the world the mirror shows.
        mirrorFigureColor = texture(GL_RGBA8, MIRROR_WIDTH, MIRROR_HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        mirrorFigureTarget = target(mirrorFigureColor);
        mirrorFigureNormal = texture(GL_RGBA8, MIRROR_WIDTH, MIRROR_HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + 1, GL_TEXTURE_2D, mirrorFigureNormal, 0);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, mirrorDepth, 0);
        if (beam == null) {
            beam = complete("mirror's people");
        }
        return beam;
    }

    private static void makeRainDrops() {
        // Where each drop starts in its box of air, and which corner of its streak each of its
        // six vertices is. The same every run: rain needs no surprises.
        FloatBuffer drops = buffer(RAIN_DROPS * 6 * 4);
        Random random = new Random(1987);
        float[] corners = {0f, 1f, 2f, 0f, 2f, 3f};
        for (int drop = 0; drop < RAIN_DROPS; drop++) {
            float x = random.nextFloat(), y = random.nextFloat(), z = random.nextFloat();
            for (float corner : corners) {
                drops.put(x).put(y).put(z).put(corner);
            }
        }
        rainDrops = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ARRAY_BUFFER, rainDrops);
        GL15.glBufferData(GL_ARRAY_BUFFER, drops.flip(), GL_STATIC_DRAW);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private static String shadowMap(int[] made) {
        int map = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, map);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, SHADOW_SIZE, SHADOW_SIZE, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
        filter(GL_LINEAR);
        // The texture answers "is this point nearer the light than what the map holds" itself,
        // and blends the four answers around a sample, which is what softens the shadow's edge.
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
        int target = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, map, 0);
        GL11.glDrawBuffer(GL_NONE);
        GL11.glReadBuffer(GL_NONE);
        made[0] = map;
        made[1] = target;
        return complete("shadow map");
    }

    /**
     * Queued once as the picture stops being drawn: gives the graphics card back the pictures
     * the frame is drawn through and the shadow maps, which at 1920 by 1080 are over 400 MB
     * that the game's own view would otherwise carry for the rest of the session. The next
     * frame drawn makes them again.
     */
    static final TextureDraw.GenericDrawer RELEASE = new TextureDraw.GenericDrawer() {
        @Override
        public void render() {
            Characters.setDown();
            if (!prepared || failure != null || width == 0) {
                return;
            }
            for (int old : sizedTargets) {
                GL30.glDeleteFramebuffers(old);
            }
            for (int old : sizedTextures) {
                GL11.glDeleteTextures(old);
            }
            sizedTargets.clear();
            sizedTextures.clear();
            width = 0;
            height = 0;
            for (int map : new int[] {nearMap, farMap, roofMap, beamMap}) {
                GL11.glDeleteTextures(map);
            }
            for (int target : new int[] {nearTarget, farTarget, roofTarget, beamTarget}) {
                GL30.glDeleteFramebuffers(target);
            }
            nearMap = 0;
            Log.info("first person picture set down: its targets and shadow maps are given back to the graphics card");
        }
    };

    /** Makes the pictures the frame is drawn through, at the size of the game's own. Null, or what the driver refused. */
    private static String resize(int newWidth, int newHeight) {
        // The last size's are given back first. Left alone they are held until the game
        // ends, a set for every size the picture has been.
        for (int old : sizedTargets) {
            GL30.glDeleteFramebuffers(old);
        }
        for (int old : sizedTextures) {
            GL11.glDeleteTextures(old);
        }
        sizedTargets.clear();
        sizedTextures.clear();
        sizing = true;
        try {
            return makeSized(newWidth, newHeight);
        } finally {
            sizing = false;
        }
    }

    /** The textures and targets the last resize made, and whether one is being made now. */
    private static final ArrayList<Integer> sizedTextures = new ArrayList<>(), sizedTargets = new ArrayList<>();
    private static boolean sizing;

    private static String makeSized(int newWidth, int newHeight) {
        width = newWidth;
        height = newHeight;
        sceneColor = texture(GL_RGBA16F, width, height, GL_RGBA, GL_FLOAT, GL_LINEAR);
        for (int i = 0; i < 2; i++) {
            keptColor[i] = texture(GL_RGBA16F, width, height, GL_RGBA, GL_FLOAT, GL_LINEAR);
            keptTarget[i] = target(keptColor[i]);
        }
        keptAny = false;
        sceneDepth = texture(GL_DEPTH_COMPONENT24, width, height, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
        sceneTarget = target(sceneColor);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, sceneDepth, 0);
        String problem = complete("scene");
        if (problem != null) {
            return problem;
        }
        shownColor = texture(GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, GL_LINEAR);
        shownTarget = target(shownColor);
        problem = complete("developed picture");
        figureColor = texture(GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        figureTarget = target(figureColor);
        figureNormal = texture(GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + 1, GL_TEXTURE_2D, figureNormal, 0);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, sceneDepth, 0);
        if (problem == null) {
            problem = complete("character");
        }
        repaintedColor = texture(GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        repaintedDepth = texture(GL_DEPTH_COMPONENT24, width, height, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
        repaintedTarget = target(repaintedColor);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, repaintedDepth, 0);
        if (problem == null) {
            problem = complete("repainted vehicle");
        }
        ownColor = texture(GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        ownDepth = texture(GL_DEPTH_COMPONENT24, width, height, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
        ownTarget = target(ownColor);
        ownNormal = texture(GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + 1, GL_TEXTURE_2D, ownNormal, 0);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, ownDepth, 0);
        if (problem == null) {
            problem = complete("viewer");
        }
        selfColor = texture(GL_RGBA16F, width, height, GL_RGBA, GL_FLOAT, GL_NEAREST);
        selfTarget = target(selfColor);
        if (problem == null) {
            problem = complete("viewer in the glass");
        }
        paneColor = texture(GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        paneTarget = target(paneColor);
        if (problem == null) {
            problem = complete("window glass");
        }
        partsColor = texture(GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        partsDepth = texture(GL_DEPTH_COMPONENT24, width, height, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
        partsTarget = target(partsColor);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, partsDepth, 0);
        if (problem == null) {
            problem = complete("cabin parts");
        }
        int levelW = width, levelH = height;
        for (int level = 0; level < BLOOM_LEVELS && problem == null; level++) {
            levelW = Math.max(1, levelW / 2);
            levelH = Math.max(1, levelH / 2);
            levelWidth[level] = levelW;
            levelHeight[level] = levelH;
            downColor[level] = texture(GL_RGBA16F, levelW, levelH, GL_RGBA, GL_FLOAT, GL_LINEAR);
            downTarget[level] = target(downColor[level]);
            problem = complete("glow");
            if (level < BLOOM_LEVELS - 1 && problem == null) {
                upColor[level] = texture(GL_RGBA16F, levelW, levelH, GL_RGBA, GL_FLOAT, GL_LINEAR);
                upTarget[level] = target(upColor[level]);
                problem = complete("glow");
            }
        }
        if (problem == null) {
            occlusionColor = texture(GL_RGBA8, levelWidth[0], levelHeight[0], GL_RGBA, GL_UNSIGNED_BYTE, GL_LINEAR);
            occlusionTarget = target(occlusionColor);
            softColor = texture(GL_RGBA8, levelWidth[0], levelHeight[0], GL_RGBA, GL_UNSIGNED_BYTE, GL_LINEAR);
            softTarget = target(softColor);
            volumeColor = texture(GL_RGBA16F, levelWidth[0], levelHeight[0], GL_RGBA, GL_FLOAT, GL_LINEAR);
            volumeTarget = target(volumeColor);
            softVolumeColor = texture(GL_RGBA16F, levelWidth[0], levelHeight[0], GL_RGBA, GL_FLOAT, GL_LINEAR);
            softVolumeTarget = target(softVolumeColor);
            cloudColor = texture(GL_RGBA16F, levelWidth[0], levelHeight[0], GL_RGBA, GL_FLOAT, GL_LINEAR);
            cloudTarget = target(cloudColor);
            softCloudColor = texture(GL_RGBA16F, levelWidth[0], levelHeight[0], GL_RGBA, GL_FLOAT, GL_LINEAR);
            softCloudTarget = target(softCloudColor);
            problem = complete("occlusion");
        }
        if (problem == null) {
            Log.info("first person picture sized " + width + "x" + height);
        }
        return problem;
    }

    private static int texture(int internal, int w, int h, int format, int type, int filter) {
        int texture = GL11.glGenTextures();
        if (sizing) {
            sizedTextures.add(texture);
        }
        GL11.glBindTexture(GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, internal, w, h, 0, format, type, (ByteBuffer) null);
        filter(filter);
        return texture;
    }

    /**
     * Reads a material's picture out of the jar and gives it to the graphics card, noting its
     * texture and its gain at index. Null, or what went wrong.
     */
    private static String loadPicture(String name, boolean cutout, int[] textures, float[] gains, int index) {
        try {
            textures[index] = Pictures.load("/materials/" + name + (cutout ? ".png" : ".jpg"), true);
        } catch (IOException e) {
            return e.getMessage();
        }
        gains[index] = Pictures.gain;
        return null;
    }

    private static void filter(int filter) {
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    private static int target(int color) {
        int target = GL30.glGenFramebuffers();
        if (sizing) {
            sizedTargets.add(target);
        }
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
        return target;
    }

    private static String complete(String what) {
        int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        return status == GL_FRAMEBUFFER_COMPLETE ? null : "the driver would not make the " + what + " target (status 0x" + Integer.toHexString(status) + ")";
    }

    /** Null, with the driver's message added to problem, when a shader does not compile or the program does not link. */
    private static void clearErrors() {
        for (int flag = 0; flag < GL_ERROR_FLAGS && GL11.glGetError() != 0; flag++) {
        }
    }
}

package com.cameraunlock.zomboid;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
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
 * Nothing here runs unless the picture is being drawn: in first person, or in
 * a graphics mode in which it draws the game's own view. Render thread only.
 * The game's OpenGL state is saved before the frame and put back after it.
 *
 * There is one of these for each view, and it holds what is that view's alone: the pictures
 * at the size of its part of the screen, its shadow maps, which are centred on its eye, the
 * frame before this one, and what the scene says of the frame being drawn. What a picture is
 * drawn with whoever looks is static and made once: the programs, the mod's own pictures and
 * models, and the making of them a piece a frame.
 */
final class Pipeline {
    final View view;

    Pipeline(View view) {
        this.view = view;
    }

    /** How many texels across the shadow maps are: see Quality.shadowSize. */
    private int shadowSize;
    /** Half the width of the ground each shadow map covers, in tiles, and how far along the light it reaches. */
    private static final float NEAR_EXTENT = 24f, FAR_EXTENT = 96f;
    /**
     * How far behind itself every shadow map holds a surface, in texels of the surface's slope in the map: see
     * drawShadows, and sunAsked in the world's program.
     */
    private static final float SLOPE_OFFSET = 2.5f;
    private static final float NEAR_RANGE = 120f, FAR_RANGE = 220f;
    private static final int BLOOM_LEVELS = 5;
    private static final int NOISE_SIZE = 64;

    private static final int GL_TRIANGLES = 0x0004;
    private static final int GL_TRIANGLE_STRIP = 0x0005;
    private static final int GL_FLOAT = 0x1406;
    private static final int GL_QUADS = 0x0007, GL_MAX = 0x8008, GL_FUNC_ADD = 0x8006;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_RG = 0x8227;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_RG8 = 0x822B;
    private static final int GL_RGBA8 = 0x8058;
    private static final int GL_RGBA16F = 0x881A;
    private static final int GL_R16F = 0x822D;
    private static final int GL_R8 = 0x8229;
    private static final int GL_RED = 0x1903;
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
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_LINEAR_MIPMAP_LINEAR = 0x2703;
    private static final int GL_TEXTURE_WRAP_S = 0x2802;
    private static final int GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_TEXTURE_WRAP_R = 0x8072;
    private static final int GL_TEXTURE_COMPARE_MODE = 0x884C;
    private static final int GL_TEXTURE_COMPARE_FUNC = 0x884D;
    private static final int GL_COMPARE_REF_TO_TEXTURE = 0x884E;
    private static final int GL_NEAREST = 0x2600;
    private static final int GL_LINEAR = 0x2601;
    private static final int GL_REPEAT = 0x2901;
    private static final int GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_EQUAL = 0x0202;
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
    private static final int GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    private static final int GL_UNSIGNED_INT = 0x1405;
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
     * The units the torch's shadow map is bound to: one for the world's program, which has a
     * texture of its own on every unit but the few the people's pass uses, and one for the
     * other programs the torch lights, which leave the world's last units alone. Each pass
     * that has its own texture on either binds it there again before it draws.
     */
    private static final int TORCH_UNIT_IN_WORLD = 21, TORCH_UNIT = UNITS - 1;

    /**
     * The shaders the off-line harnesses read from this class by name. The two longest are
     * joined here from their parts.
     */
    private static final String WORLD_VERTEX = PipelineShaders.WORLD_VERTEX;
    private static final String SHADOW_VERTEX = PipelineShaders.SHADOW_VERTEX;
    private static final String SHADOW_FRAGMENT = PipelineShaders.SHADOW_FRAGMENT;
    private static final String SCREEN_VERTEX = PipelineShaders.SCREEN_VERTEX;
    private static final String WORLD_FRAGMENT = PipelineShaders.WORLD_FRAGMENT_PARTS.concat(PipelineShaders.WORLD_FRAGMENT_RELIEF).concat(SnowShader.GLSL).concat(RoadPaint.GLSL).concat(PipelineShaders.WORLD_FRAGMENT_MAIN);
    /**
     * The same program told at compile time that only depth is wanted, so the driver leaves out
     * every sum that only colours a pixel: see drawWorld.
     */
    private static final String WORLD_FRAGMENT_DEPTH = WORLD_FRAGMENT.replace(PipelineShaders.DEPTH_ONLY_ASKED, PipelineShaders.DEPTH_ONLY_ALWAYS);
    private static final String FIGURE_LIGHT = PipelineShaders.FIGURE_LIGHT_PARTS.concat(PipelineShaders.FIGURE_LIGHT_PANES).concat(VehicleLamps.GLSL).concat(CabinShader.SOLIDS).concat(CabinShader.LIGHT).concat(CabinShader.GLASS)
            .concat(CharacterDraw.LIT).concat(PipelineShaders.FIGURE_LIGHT_MAIN);

    /**
     * Why no view's picture can be drawn on this machine, or null: a piece of what they are all
     * drawn with could not be made. Set once, on the render thread.
     */
    private static volatile String broken;
    /**
     * Why this view's picture cannot be drawn, or null: its own pictures could not be made, or
     * a frame of it threw. The other view's goes on being drawn. A fault in what they share
     * that only shows as a frame is drawn is met by each view in its own frame.
     */
    private volatile String failure;
    /**
     * Whether this view has asked for a piece or drawn a frame since it last let go. What every
     * view is drawn with is given back once none has.
     */
    private boolean holds;
    /** The number of the game's frame being drawn, the same for every view drawn in it. */
    private static int gameFrame;
    /** The game's frame this view last had its turn in, and the one the last piece was made in. */
    private int turnIn;
    private static int pieceIn = -1;
    private static volatile boolean prepared;
    private static Program rain;
    private static Program snow;
    private static Program dust;
    private static Program steady;
    /** The steadied picture of the last frame and of this one, taking turns. */
    private final int[] keptColor = new int[2], keptAhead = new int[2], keptTarget = new int[2];
    /**
     * Beside the scene, where what it shows moves by itself: 1 a person, a half a vehicle, nought
     * anything else. The people's light writes it through movesTarget, which is the scene's
     * target with this beside its colour. Only with FrameBlending on.
     */
    private int sceneMoves, movesTarget, movesAlone;
    /** The scene's colour with nothing else beside it, for the holes shots leave, which read the scene's depth as they are laid. */
    private int decalTarget;
    private final ImpactDraw impacts = new ImpactDraw();
    /** Whether this frame's picture shows where shots struck. */
    private boolean struck;
    private static final String[] MOVER_AT = new String[Movers.MOST], MOVER_BEFORE = new String[Movers.MOST];
    private int keptNow;
    private boolean keptAny;
    private final ViewCamera.Pose before = new ViewCamera.Pose(), aimBefore = new ViewCamera.Pose();
    /** How much of the last frame's picture goes into this one at the most: see STEADY for where it is less. */
    private static final float KEPT = 0.93f;
    /** Where in its pixel each frame of the cycle is drawn, from the middle, in pixels times two: the Halton points of bases 2 and 3. */
    private static final float[] JITTER_X = {0f, -0.5f, 0.5f, -0.75f, 0.25f, -0.25f, 0.75f, -0.875f};
    private static final float[] JITTER_Y = {-0.333f, 0.333f, -0.778f, -0.111f, 0.556f, -0.556f, 0.111f, 0.778f};
    private int jitterAt;
    /** How far to one side this frame is drawn, as the projection takes it: nothing with the frames not mixed. */
    private float asideX, asideY;
    private static Program world, worldDepth, shadow, figureLight, sky, clouds, bolt, occlusion, soften, volume, bloomDown, bloomUp, develop, smooth;
    private static Program far, farTree;
    /** What the shaders are told as uBeyond this frame: see PipelineShaders.ENDS. Not 0 where the far land is drawn behind the built world. */
    private float beyond;
    /** How many frames go by before the pixels the built world is thinned out by come round again. */
    private static final int SIEVES = 64;
    private static int whiteTexture, noiseTexture;
    /**
     * What stands in for a picture that is not made, where a program has a sampler for it: a
     * colour of nothing at all, which is what such a picture is cleared to before it is drawn
     * into, and for a shadow map a depth as far off as depth goes, which shades nothing.
     */
    private static int noneTexture, farDepth;
    private int nearMap, farMap, nearTarget, farTarget;
    /** Whether the far map and the map of what is overhead have been drawn since they were made, and whether this frame draws the far one: see drawShadows. */
    private boolean farDrawn, roofDrawn, castFar;
    /** What is overhead near the eye, seen from straight above: where rain cannot fall. */
    private int roofMap, roofTarget;
    private final float[] roofMatrix = new float[16];
    private static final float ROOF_EXTENT = 20f, ROOF_RANGE = 40f;
    private static final int RAIN_DROPS = 9000;
    /** The unit the rain and the snow read the block of sheltered squares on. */
    private static final int SHELTER_UNIT = 1;
    /** The unit the develop pass reads it on, and how bright fog is against the sky it hangs under. */
    private static final int DEVELOP_SHELTER_UNIT = 5, DEVELOP_OUTLINES_UNIT = 6;
    private static final float FOG_BRIGHT = 1.1f;
    /** The drops, on the graphics card: they are the same every frame. */
    private static int rainDrops;
    /**
     * What open ground is made of, as pictures of the stuff itself that join up with themselves
     * at their edges: tarmac, gravel, earth, grass. They are laid over the ground by where it
     * is on the map, so the game's square of ground repeated a thousand times becomes one
     * unbroken surface. Made for this mod and shipped in its jar.
     */
    private static final String[] MATERIALS = {"asphalt", "gravel", "soil", "grass", "shingles", "paint", "wood", "bark", "planks", "carpet", "leaves", "vinyl", "cloth", "jersey"};
    /** Which of the materials are planed wood and carpet. */
    private static final int WOOD = 6, CARPET = 9;
    /** Bark is several pictures in one texture, one behind another in the order Flora counts them. */
    private static final int BARK = 7;
    private static final int FIRST_MATERIAL_UNIT = 3;
    private static final int[] materialTexture = new int[MATERIALS.length];
    /** What each material's picture is multiplied by to be 1 on average: it changes the ground's texture, not how light it is. */
    private static final float[] materialGain = ones(MATERIALS.length);
    /** What Flora's ground and plants are made of: fallen leaves, rock, a fern's frond and ivy, the last two cut out. */
    private static final String[] NATURE = {"litter", "rock", "frond", "ivy"};
    private static final int FIRST_NATURE_UNIT = 24;
    private static final int[] natureTexture = new int[NATURE.length];
    private static final float[] natureGain = ones(NATURE.length);
    /** The sprigs a crown is made of, all in one picture: see Flora.addCrown. */
    private static final int SPRIG_UNIT = 28;
    private static int sprigs, sprigForm;
    private static final int SPRIG_FORM_UNIT = 29;
    private int width, height;
    private int sceneTarget, sceneColor, sceneDepth, shownTarget, shownColor, figureTarget, figureColor;
    /** What is outlined, each thing in the colour of its line where it is the nearest thing, over the scene's depth: see drawOutlined. */
    private int outlineTarget, outlineColor;
    /** The picture's colour as the passes after the steadying read it: the scene's, or with the frames blended the steadied one. */
    private int frameColor;
    /**
     * Quality.shadedAndLit, Quality.surfacesMirror and Quality.reflections as the frame began: the
     * logic thread may change the mode between two passes of a frame.
     */
    private boolean shadedAndLit, surfacesMirror, reflections;
    /** Quality.frameBlending as the frame began: the row is read again while the game runs, and what one pass of a frame draws into the next reads. */
    private boolean steadying;

    /**
     * Pictures that only some frames draw into. Each set is made by the first frame that draws
     * into it and given back once no frame has for LENT_FOR_NANOS: a player on foot does not
     * carry a driver's, nor one in Low what only High draws.
     */
    private static final class Lent {
        final String what;
        /** Makes its pictures: null, or what the driver refused. Null for the whole picture, which resize makes. */
        final Supplier<String> make;
        final ArrayList<Integer> textures = new ArrayList<>(), targets = new ArrayList<>();
        long usedNanos;
        /** What its textures take on the graphics card. */
        long bytes;

        Lent(String what, Supplier<String> make) {
            this.what = what;
            this.make = make;
        }

        boolean made() {
            return !textures.isEmpty();
        }
    }

    /** What every frame draws into, at the picture's size. */
    private final Lent whole = new Lent("picture", null);
    /** The steadied pair (FrameBlending), the cabin's three (seated), the viewer in a window's glass, and the shade and lit air (High). */
    private final Lent steadied = new Lent("steadied pictures", this::makeSteadied), cabin = new Lent("cabin's pictures", this::makeCabin),
            glassed = new Lent("picture of the viewer in the glass", this::makeGlassed), shaded = new Lent("pictures of shade and lit air", this::makeShaded);
    /** The headlights' shadow map, at the shadow maps' size. */
    private final Lent beamed = new Lent("headlights' shadow map", this::makeBeamed);
    /** The carried torch's shadow map, at TORCH_MAP_MOST across or the shadow maps' size if that is smaller. */
    private final Lent torched = new Lent("torch's shadow map", this::makeTorched);
    /** What a vehicle's mirror shows and the people in it, at the mirror's size. */
    private final Lent mirrored = new Lent("mirror's pictures", this::makeMirror);
    /** What water shows, at a third of the picture's size: only in High, in first person, with water in sight. */
    private final Lent watered = new Lent("picture of what water shows", this::makeWatered);
    private final Lent[] SIZED = {whole, steadied, cabin, glassed, shaded, watered};
    private final Lent[] UNSIZED = {beamed, torched, mirrored};
    private static final long LENT_FOR_NANOS = 10_000_000_000L;
    /** The set whose pictures are being made, which texture and target note them in, or null. */
    private Lent making;
    /** When the frame being drawn began. */
    private long frameNanos;
    /** The viewer's own body, drawn apart from everything else: see Characters.drawViewer. */
    private int ownTarget, ownColor, ownDepth;
    /** Beside the viewer's own body, which way each point of it faces, where the picture drew it itself. */
    private int ownNormal;
    private int repaintedTarget, repaintedColor, repaintedDepth;
    private static final float OWN_NEAR = 0.05f;
    private int occlusionTarget, occlusionColor, softTarget, softColor;
    private int volumeTarget, volumeColor, softVolumeTarget, softVolumeColor;
    private static int spriteSampler;
    private static int skinSampler;
    private boolean glazed;
    private static final int SKIN_UNITS = 8;
    private static int depthSampler;
    /** How wide the sun is, as the width of a shadow's soft edge for each tile between caster and ground. Wider than the real sun's: the real one's edge is too fine to read at this scale. */
    private static final float SUN_WIDTH = 0.06f;
    private int cloudTarget, cloudColor, softCloudTarget, softCloudColor;

    private static final boolean TIMES = Boolean.getBoolean("cameraunlock.zomboid.gpuTimes");
    /**
     * The picture seen from far overhead, as the game's own view is. The eye is then thousands
     * of tiles off and everything in sight is much the same distance from it, so what the
     * picture does by how far a thing is from the eye (detail, haze, the reach of shade) goes
     * by its distance less rangeFrom, and what it centres on the eye (shadow maps, who is
     * near enough to draw) is centred on the place looked at.
     */
    boolean overhead;
    /** The picture is drawn by a camera outside the viewer, who is then one of the people in it. */
    boolean outside;
    /** How much of the way from the game's own view to the eye the camera has come: 0 at rest overhead, 1 at the eye. */
    float landing;
    /** How much of the game's own picture, which is under this one, this one covers. */
    float opacity = 1f;
    /** The glass the viewer is shown in this frame, or null: see OwnReflection. */
    OwnReflection.Glass reflection;
    private int selfTarget, selfColor;
    private static Program selfShown;
    private final ViewCamera.Pose inGlass = new ViewCamera.Pose();
    private final float[] selfView = new float[16], selfProjection = new float[16];
    private final FloatBuffer selfCorners = buffer(OwnReflection.MOST * 18);
    /**
     * Whether the viewer sat in a vehicle when the frame was built. Every pass of the frame
     * goes by this one answer: the logic thread seats the viewer and stands them up between
     * two passes of a frame as readily as between two frames.
     */
    boolean seated;
    float seatedX, seatedY, seatedGroundUp;
    /** How the vehicle the viewer sits in lies on the land, as the logic thread had it with seatedGroundUp: see VehicleSlope.turn. */
    final float[] seatedTurn = new float[9];
    /** The way the vehicle faces, its left and its up, as the logic thread had them when it seated the eye in it: see Ride.stands. */
    final float[] seatedAxes = new float[9];
    /**
     * The plane the water round the eye lies in, where the frame shows any: the unit vector
     * square to it and how far along it the plane is, see WaterMirror. Null with no water.
     */
    float[] water;
    /**
     * The headlights as the frame's first read found them. The logic thread gathers them anew
     * while a frame is drawn, and the beam's shadow map and its cone are to be of one gathering.
     */
    private float[] beams = new float[Headlights.BEAMS * Headlights.STRIDE];
    /** Builds the frame does not draw, of chunks the view does not face, which it hands to the graphics card with the ones it draws: see ChunkBuffers.loadAhead. */
    ArrayList<WorldScene.ChunkMeshes> ahead;
    float rangeFrom;
    float focusX, focusY, focusUp;

    /** Where the game's own view opens its window through trees on the player's floor, and the pointer's while aiming, and the trees it draws see-through: see CanopyWindow. */
    void canopy(float x, float y, float up, boolean aimed, float aimX, float aimY, float[] faded, int fadedCount) {
        canopyX = x;
        canopyY = y;
        canopyUp = up;
        canopyAimed = aimed;
        canopyAimX = aimX;
        canopyAimY = aimY;
        System.arraycopy(faded, 0, canopyFaded, 0, 3 * fadedCount);
        canopyFadedCount = fadedCount;
    }
    /** How far from the eye the frame draws people, zombies, vehicles and furniture, which every pass of it goes by. */
    final DrawDistance drawDistance = new DrawDistance();
    /**
     * The middle of the map the game has loaded and how far from it the map reaches every way,
     * in tiles: the world fades into the sky by there as well as by the picture's own sight.
     */
    float loadedX, loadedY, loadedWithin = Float.MAX_VALUE / 2f;
    /** The height above which walls are left out, for a viewer seen from overhead: see the world shader. */
    float cutAbove = Float.MAX_VALUE;
    /**
     * For the picture drawn as the game's own view, the walls the game cuts away: see Cutaways.
     * The block of squares' marks, its first square, and the height the walls of the viewer's
     * floor reach, which is 0 on the camera's way to the eye, when the walls left out are the
     * near ones of the viewer's building.
     */
    float[] cutRows;
    float cutRowsX, cutRowsY, cutBelow;
    /** How much of CUT_BEHIND the cut still reaches behind the viewer: all of it from overhead, none as the camera reaches the eye. */
    float cutBehind = 1f;
    /** Where rain and snow are kept out with no roof drawn, or null: see Shelter. */
    Shelter sheltered;
    /** The height of the floor the viewer stands on, which the picture from far overhead is seen against. */
    float floorUp;
    /** How far the camera outside the viewer is from the place it looks at, in tiles. */
    float away;
    private int shelterTexture;
    private Shelter shelterHeld;
    /** Which air round the eye is in a room, or null before the first build: see RoomAir. */
    RoomAir roomAir;
    private int roomAirTexture;
    private RoomAir roomAirHeld;
    /** The height above which everything in the viewer's building is left out, while the camera is above it. */
    float lid = Float.MAX_VALUE;
    final float[] cutBox = new float[4];
    private final float[] holeAt = new float[2];
    /** The window opened about the viewer: its radius as a share of the picture's height, how far before them it starts, and how far under their eye its middle is, in tiles. */
    private static final float HOLE_OF_HEIGHT = 0.11f, HOLE_BEFORE = 1.6f, HOLE_BELOW_EYE = 0.5f, HOLE_FLOOR_BELOW_EYE = 1.2f;
    /** The game's window through trees: see CanopyWindow and canopy. */
    private float canopyX, canopyY, canopyUp, canopyAimX, canopyAimY;
    private boolean canopyAimed;
    private final float[] canopyAt = new float[2], canopySize = new float[4], canopyFaded = new float[3 * CanopyWindow.TREES];
    private int canopyFadedCount;
    /** How far behind the viewer, away from the camera, the cut begins, in tiles of x and y together along the map's diagonal. */
    private static final float CUT_BEHIND = 1.5f;
    /**
     * Dev only, on top of Quality.scale:
     * how many of the picture's pixels there are to each of the window's, each way. Leaves,
     * blades of grass, fence wire and roof edges are finer than a pixel, and drawn one sample
     * to a pixel each is there or not there as the head moves by a hair: the whole picture
     * crawls. Drawn finer and brought down to the window, each pixel is the mean of several
     * and changes by degrees.
     */
    private static final float SCALE = Float.parseFloat(System.getProperty("cameraunlock.zomboid.renderScale", "1"));
    /** Dev only: passes left out of the picture, to find which of them a fault is in. Any of steady, occlusion, air, glow, relief, and depth for the solid world and what grows drawn once with no pass for their depth first. */
    private static final String WITHOUT = System.getProperty("cameraunlock.zomboid.without", "");
    private static final String[] STAGE_NAMES = {"load", "shadows", "world", "clouds", "sky", "occlusion", "air", "glow", "develop", "figures", "smooth", "depth", "solid", "laid", "mirror"};
    private static final int STAGE_LOAD = 0, STAGE_SHADOWS = 1, STAGE_WORLD = 2, STAGE_CLOUDS = 3, STAGE_SKY = 4, STAGE_OCCLUSION = 5,
            STAGE_AIR = 6, STAGE_GLOW = 7, STAGE_DEVELOP = 8, STAGE_FIGURES = 9, STAGE_SMOOTH = 10, STAGE_DEPTH = 11, STAGE_SOLID = 12, STAGE_LAID = 13, STAGE_MIRROR = 14;
    private final long[] stageNanos = new long[STAGE_NAMES.length];
    private int stageFrames;
    private final int[] downTarget = new int[BLOOM_LEVELS], downColor = new int[BLOOM_LEVELS];
    private final int[] upTarget = new int[BLOOM_LEVELS - 1], upColor = new int[BLOOM_LEVELS - 1];
    private final int[] levelWidth = new int[BLOOM_LEVELS], levelHeight = new int[BLOOM_LEVELS];

    private final float[] tint = new float[4];
    /** The chunk builds the frame draws, for the furniture standing on each. */
    private ArrayList<WorldScene.ChunkMeshes> drawn;
    /** Every chunk of the frame as draws. */
    private final ChunkBuffers.Listing all = new ChunkBuffers.Listing();
    /** For each shadow map, the chunks it can hold as draws, when those are not all of them. */
    private final ChunkBuffers.Listing nearListed = new ChunkBuffers.Listing(), farListed = new ChunkBuffers.Listing(),
            beamListed = new ChunkBuffers.Listing(), roofListed = new ChunkBuffers.Listing(), torchListed = new ChunkBuffers.Listing();
    /** The chunks a vehicle's mirror can show as draws, and the listing its picture is drawn from: every chunk's when none is out of it. */
    private final ChunkBuffers.Listing behindListed = new ChunkBuffers.Listing();
    private ChunkBuffers.Listing shownBehind = all;
    /** The chunks the eye's own picture, or a scope's, can show as draws, when those are not all of them: see seenBy. */
    private final ChunkBuffers.Listing seenListed = new ChunkBuffers.Listing();
    /**
     * The rooms the eye's own picture sees into, whose contents it draws, leaving out every
     * other room's; null to draw every room's. Set by the frame before each draw: see RoomSight.
     * Only the eye's own picture goes by it: what a scope, a mirror or water shows, and the
     * shadows, are drawn with every room.
     */
    RoomSight.Seen roomsSeen;
    /** Who the eye's own picture and a scope's leave out by the room they stand in, or null to leave out no one by room. Set by the frame before each draw: see FigureRooms. */
    FigureRooms figureRooms;
    /** What the world's program is told in uLaid past 1, for all that is laid: only its grass is drawn, or all of it but its grass. */
    private static final float LAID_GRASS = 2f, LAID_REST = 3f;
    /**
     * How much further than the wind's reach a chunk's bounds are taken to go, in tiles, when it
     * is asked whether a picture can show the chunk: the program presses down the grass round
     * the viewer's feet and thickens a far wire, each by far less than this.
     */
    private static final float SEEN_MARGIN = 0.5f;
    private boolean[] leftOut = new boolean[256];
    /** The width and height of each texture a frame has drawn with, by its OpenGL name, and the frame each was last asked of the driver: see tellTexel. */
    private static int[] texelOf = new int[1024], texelWide = new int[1024], texelHigh = new int[1024], texelRead = new int[1024];
    private static int texelsKnown;
    /** A texture's size is asked again after this many frames and up to as many more, by its name, so they are not all asked in one frame. A power of two. */
    private static final int TEXEL_KEPT_FRAMES = 256;
    private int frameNumber;
    /** How many chunks the last frame drew into the near, far, headlight, roof and torch maps, and how many it had, for the log. */
    private static final int NEAR_MAP = 0, FAR_MAP = 1, BEAM_MAP = 2, ROOF_MAP = 3, TORCH_MAP = 4;
    private final int[] heldChunks = new int[5];
    /** Written after heldChunks, and read before it by the logic thread. */
    private volatile int heldOf;
    /** What this view's own pictures and shadow maps take on the graphics card, as its last frame left them. Read by the logic thread. */
    private volatile long heldBytes;
    /** What the shadow map being drawn holds. */
    private final MapBounds mapBounds = new MapBounds();
    /**
     * The furthest the wind moves a vertex, in tiles for each unit of the wind's strength: the
     * most the shaders' blown() leans a plant, 0.05 + 0.045 + 0.02, times the most of the wind
     * a vertex takes, which is 1, and as much again to be sure.
     */
    private static final float BLOWN_MOST = 0.115f * 2f;
    /** Every model the mod has, on the graphics card as Models.buffer lays them out. */
    private static int modelBuffer;
    /** Every model's triangles, as Models.corners lists them. */
    private static int modelCorners;
    private static final int MODEL_STRIDE = Models.BUFFER_FLOATS * Float.BYTES;
    private final float[] nearMatrix = new float[16], farMatrix = new float[16];
    private final float[] beamMatrix = new float[16];
    private int beamMap, beamTarget;
    private int paneColor, paneTarget;
    private static Program paneMask;
    private static final int PANE_UNIT = 23;
    /**
     * What part of the cabin each pixel of the vehicle sat in is, and how far off: the body's
     * own triangles, its glass left out, each drawn in the number of its kind.
     */
    private int partsColor, partsDepth, partsTarget;
    private static Program partKind;
    private static final int PARTS_UNIT = 18, PARTS_DEPTH_UNIT = 19;
    /** The game's vehicle draw binds its own textures over the low units, the wood's among them: the cabin reads the wood from here. */
    private static final int WOOD_UNIT = 17;
    private int figureNormal;
    /** The lamps of the vehicles in the picture that are on, as the game's vehicle shaders write them: see VehicleLamps. */
    private int figureLamps;
    private static final int LAMP_UNIT = 30;
    /** Every picture of the people's target, to clear; and the two a vehicle is drawn into, its colour and its lamps. */
    private static final int[] FIGURE_BUFFERS = {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT0 + 1, GL_COLOR_ATTACHMENT0 + 2},
            LAMP_BUFFERS = {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT0 + 2};
    private boolean lampsSaid;
    private static final int NORMAL_UNIT = 22;
    private static final int[] FIRST_BUFFER = {GL_COLOR_ATTACHMENT0}, BOTH_BUFFERS = {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT0 + 1};
    private int mirrorColor, mirrorTarget, mirrorDepth;
    /** The people and vehicles the mirror shows, as the game or the picture draws them, before they are lit into the mirror's picture. */
    private int mirrorFigureTarget, mirrorFigureColor, mirrorFigureNormal, mirrorFigureLamps;
    /** Nearer the mirror than this, in tiles, is the vehicle it hangs in, and is left out of its picture. */
    private static final float MIRROR_NEAR = 2.6f;
    private boolean inMirror;
    /**
     * How long the mirror shows one picture of the road behind before it is drawn again: thirty
     * times a second. It is a second drawing of the world, a seventh of a frame's work on the
     * graphics card, for a picture a hand's breadth across.
     */
    private static final long MIRROR_EVERY_NANOS = 33_000_000L;
    private long mirrorDrawnAt;
    /** While set, the world is drawn into the picture a scope's eyepiece shows. */
    private boolean inScope;
    /**
     * What water shows: the world over it, drawn from the eye's image under it at a third of the
     * picture's size. Only in High, in first person, and only in a frame whose picture can
     * have water in it: see waterInSight and WaterMirror.
     */
    private int waterColor, waterTarget, waterDepth, waterWidth, waterHeight;
    private final ViewCamera.Pose under = new ViewCamera.Pose();
    /** While set, the world is drawn into that picture. */
    private boolean inWater;
    /** Whether this frame drew it, for the eye's own picture to show. */
    private boolean waterDrawn;
    /** How far the nearest water in sight is from the eye, in tiles: see waterInSight. */
    private float waterNearest;
    /**
     * The part of that picture the water in sight can show, as its least and greatest x and y
     * from -1 to 1 (see waterInSight); that and what its coarser levels take in round it, which
     * is drawn; room for WaterMirror.takeIn; and the projection cut down to what is drawn.
     */
    private final float[] waterRect = new float[4], cropRect = new float[4], boxCorners = new float[32], cropped = new float[16];
    /** How many of its pixels the picture of what water shows is drawn past the part of it that is read: what its coarser levels take in. */
    private static final int WATER_RECT_TEXELS = 16;
    private static final int WATER_UNIT = 18;
    static final int MIRROR_WIDTH = 640, MIRROR_HEIGHT = 180;
    private static final int MIRROR_UNIT = 21;
    /** Half of how much of the world behind the mirror takes in, top to bottom, as a tangent. */
    static final float MIRROR_TAN = 0.1f;
    private final ViewCamera.Pose behind = new ViewCamera.Pose();
    private final ViewCamera.Pose lamp = new ViewCamera.Pose();
    /** Half the angle the headlights' shadow map takes in, as a tangent, and how near and far it sees. */
    private static final float BEAM_TAN = 0.84f, BEAM_NEAR = 0.35f, BEAM_FAR = 32f;
    /**
     * The same for the torch's. Its beam lights nothing further off its middle than 57 degrees
     * (torchAt's cone ends at a cosine of 0.55), and the map takes in a little more than that.
     * It sees from nearer than the hand is to what the hand holds up, and past the torch's
     * reach by as far as the beam takes to die away.
     */
    private static final float TORCH_TAN = 1.6f, TORCH_NEAR = 0.08f, TORCH_PAST_REACH = 3.5f;
    /** A torch lights a room, not a road: its map is no bigger than this across, in any mode. */
    private static final int TORCH_MAP_MOST = 2048;
    private final float[] torchMatrix = new float[16], torchAt = new float[4], torchWay = new float[3];
    private int torchMap, torchTarget, torchSize;
    private final float[] sunNdc = new float[2];
    private final int[] samplers = new int[UNITS];
    /** Where the driver puts an answer: see bound. As long as the longest answer OpenGL gives to one question. */
    private final int[] asked = new int[16];
    private final float[] projection = new float[16], viewed = new float[16], flash = new float[7];
    /** This picture's fires and their light. */
    private final FireDraw fires = new FireDraw();
    private static final String[] BEAM_AT = new String[Headlights.BEAMS], BEAM_WAY = new String[Headlights.BEAMS];
    private static final String[] ROOT_AT = new String[Characters.ROOTS];
    private static final String[] PRINT_AT = new String[SnowPrints.PRINTS], RUT_AT = new String[SnowPrints.RUTS], RUT_UP_AT = new String[SnowPrints.RUTS];
    private final float[] snowPrints = new float[SnowPrints.PRINTS * 4], snowRuts = new float[SnowPrints.RUTS * 4], snowRutsUp = new float[SnowPrints.RUTS];
    private final float[] snowTrod = new float[6];

    static {
        for (int i = 0; i < Headlights.BEAMS; i++) {
            BEAM_AT[i] = "uBeamAt[" + i + "]";
            BEAM_WAY[i] = "uBeamWay[" + i + "]";
        }
        for (int i = 0; i < ROOT_AT.length; i++) {
            ROOT_AT[i] = "uRoot[" + i + "]";
        }
        for (int i = 0; i < Movers.MOST; i++) {
            MOVER_AT[i] = "uMover[" + i + "]";
            MOVER_BEFORE[i] = "uMoverBefore[" + i + "]";
        }
        for (int i = 0; i < PRINT_AT.length; i++) {
            PRINT_AT[i] = "uSnowPrint[" + i + "]";
        }
        for (int i = 0; i < RUT_AT.length; i++) {
            RUT_AT[i] = "uSnowRut[" + i + "]";
            RUT_UP_AT[i] = "uSnowRutUp[" + i + "]";
        }
    }
    private volatile float lastMillis;
    /** Whether any texture unit has been found with a sampler of the game's or another mod's bound to it, and how often they are looked at until one has. */
    private static boolean samplersSeen;
    private static final int SAMPLERS_ASKED_EVERY = 256;


    /** Why first person cannot be drawn here, for the log and to keep the view off, or null. */
    String failure() {
        String shared = broken;
        return shared != null ? shared : failure;
    }

    float lastMillis() {
        return lastMillis;
    }

    /** How many of the last frame's chunks each shadow map was drawn from. */
    String describeShadows() {
        int of = heldOf;
        return "of " + of + " chunks the shadow maps hold near " + heldChunks[NEAR_MAP] + " far " + heldChunks[FAR_MAP] + " headlights " + heldChunks[BEAM_MAP]
                + " roof " + heldChunks[ROOF_MAP] + " torch " + heldChunks[TORCH_MAP]
                + ", and this view's pictures and shadow maps take " + (heldBytes >> 20) + " MB on the graphics card";
    }

    /** Every map and picture of this view's own, four bytes to a texel of depth or of a screen's colour and eight to one of light. */
    private long held() {
        long bytes = nearMap == 0 ? 0L : 3L * 4 * shadowSize * shadowSize;
        for (Lent set : SIZED) {
            bytes += set.bytes;
        }
        for (Lent set : UNSIZED) {
            bytes += set.bytes;
        }
        return bytes + view.scope.pictureBytes() + view.heldDepth.bytes();
    }

    /** A gain of one for each: what a surface is multiplied by while its picture is not made, which is plain white. */
    private static float[] ones(int count) {
        float[] gains = new float[count];
        Arrays.fill(gains, 1f);
        return gains;
    }

    /** The number of the game's frame being drawn, for whatever is done once a frame however many views are drawn in it. Render thread. */
    static int frame() {
        return gameFrame;
    }

    /**
     * Counts the game's frames, as a view begins its part of one: a view that has had its turn
     * in the frame counted is beginning the next.
     */
    private void turn() {
        if (turnIn == gameFrame) {
            gameFrame++;
        }
        turnIn = gameFrame;
        holds = true;
    }

    private static FloatBuffer buffer(int floats) {
        return ByteBuffer.allocateDirect(floats * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    /**
     * @param chunks     what to draw, or null to make the next piece of what the picture is drawn with and draw nothing
     * @param viewport   the part of the game's picture the frame is written to: x, y, width, height
     * @param exposure   how much light the eye is letting in: lower in the open at noon than in a room
     * @param fogEnd     the distance, in tiles, at which the world has faded into the sky
     */
    void draw(ArrayList<WorldScene.ChunkMeshes> chunks, ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView,
            float near, float far, float fogStart, float fogEnd, float exposure, int[] viewport) {
        if (failure() != null) {
            return;
        }
        long start = System.nanoTime();
        clearErrors();
        if (chunks != null) {
            turn();
        }
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
        // The game binds no sampler to any unit, so most frames there is nothing to ask: every
        // question put to a driver that runs on a thread of its own waits for that thread, and
        // there are thirty-two units. They are asked on one frame in 256, and on every
        // frame from then on once any unit has held one, which another mod could see to.
        boolean looked = samplersSeen || (frameNumber & SAMPLERS_ASKED_EVERY - 1) == 1;
        for (int unit = 0; unit < UNITS; unit++) {
            if (looked) {
                GL13.glActiveTexture(GL_TEXTURE0 + unit);
                samplers[unit] = bound(GL_SAMPLER_BINDING);
                samplersSeen |= samplers[unit] != 0;
            }
            GL33.glBindSampler(unit, 0);
        }
        GL13.glActiveTexture(GL_TEXTURE0);

        // Whatever the frame throws, the game gets its own state back: a frame that left its
        // pushes behind would leave more on every frame the fault came again.
        try {
            if (chunks == null) {
                broken = prepareNext();
                if (broken != null) {
                    Log.info("first person cannot be drawn on this machine: " + broken);
                }
            } else {
                picture(chunks, camera, state, tanHalfFieldOfView, near, far, fogStart, fogEnd, exposure, viewport, drawTarget);
            }
        } catch (RuntimeException | LinkageError thrown) {
            // Thrown on, it ends the game's whole frame, its own panels with it, and comes again
            // on the next. The view goes off instead, and the log has why.
            if (chunks == null) {
                broken = "the making of a piece threw " + thrown;
            } else {
                failure = "the frame's draw threw " + thrown;
            }
            Log.error("first person cannot be drawn", thrown);
            view.characters.unwind();
            // The other view's frames go on, and are not to find this as the throw left it.
            CharacterDraw.bare = false;
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

    private void picture(ArrayList<WorldScene.ChunkMeshes> chunks, ViewCamera.Pose camera, SkyState state,
            float tanHalfFieldOfView, float near, float far, float fogStart, float fogEnd, float exposure, int[] viewport,
            int drawTarget) {
        beams = view.headlights.beams;
        while (broken == null && !prepared) {
            // Asked to draw before the pieces were made one a frame: they are made here and now, each reading what it needs itself.
            if (!coreBegun) {
                coreBegun = true;
                Pictures.askCard();
                picturesSmall = small();
                Pictures.small = picturesSmall;
            }
            makes = CORE;
            broken = prepareNext();
        }
        // One piece a frame, whichever view's turn comes first in it.
        if (broken == null && pieceIn != gameFrame && pieceWaits()) {
            broken = prepareNext();
        }
        if (broken != null) {
            clearErrors();
            Log.info("first person cannot be drawn on this machine: " + broken);
            return;
        }
        if (failure == null && nearMap != 0 && shadowSize != Quality.shadowSize()) {
            giveBackShadowMaps();
        }
        if (failure == null && nearMap == 0) {
            shadowSize = Quality.shadowSize();
            failure = makeShadowMaps();
            farDrawn = false;
            roofDrawn = false;
        }
        float scale = SCALE * Quality.scale();
        int wide = Math.round(viewport[2] * scale), high = Math.round(viewport[3] * scale);
        if (failure == null && (wide != width || high != height)) {
            failure = resize(wide, high);
        }
        if (failure == null) {
            float aspect = (float) viewport[2] / viewport[3];
            if (TIMES) {
                beginStages();
            }
            frameNanos = System.nanoTime();
            shadedAndLit = Quality.shadedAndLit();
            surfacesMirror = Quality.surfacesMirror();
            reflections = Quality.reflections();
            steadying = Quality.frameBlending();
            frameColor = sceneColor;
            SpriteColours.read(view.index);
            Rugs.putOnCard(camera.eyeX, camera.eyeY, camera.eyeUp);
            FineSkins.tend();
            drawn = chunks;
            ChunkBuffers.load(chunks, ahead);
            Impacts.take(frameNanos);
            struck = !overhead && ImpactDraw.shown();
            // At rest overhead the picture is of the built world alone, as the game's is. On the
            // camera's way to the eye the far land is round the viewer and not the camera: it comes
            // in as the built world's own ends do, which come in from far off as the camera comes down.
            boolean farThere = view.worldScene.farLand.load(focusX, focusY);
            beyond = (!outside || landing > 0f) && farThere ? 1f + (steadying ? frameNumber % SIEVES : 0) : 0f;
            all.of(chunks, null, true, null);
            arrays();
            fires.take(state, chunks, eyesOwn() ? roomsSeen : null, camera, frameNanos);
            stage(STAGE_LOAD);
            boolean figures = view.characters.ready();
            glazed = false;
            // A mirror and a scope look where the eye does not, at ground the same maps shade.
            view.characters.seenBy(camera, tanHalfFieldOfView, aspect, !seated && view.scope.fitted() == null);
            drawShadows(state, figures);
            boolean inTheGlass = figures && !outside && !seated && reflection != null && surfacesMirror
                    && drawReflection(camera, state, tanHalfFieldOfView, aspect, near, far, fogStart, fogEnd);
            stage(STAGE_SHADOWS);
            float[] cabin = seated ? Cabins.wheel(view.ride) : null;
            // The mirror keeps its picture between drawings: see MIRROR_EVERY_NANOS.
            if (cabin != null && reflections && (!mirrored.made() || System.nanoTime() - mirrorDrawnAt >= MIRROR_EVERY_NANOS)) {
                mirrorDrawnAt = System.nanoTime();
                drawBehind(camera, state, cabin, far, fogStart, fogEnd, figures);
                stage(STAGE_MIRROR);
            }
            if (view.scope.wanted(view.characters.aim, tanHalfFieldOfView / FieldOfView.setTanHalfVertical())) {
                drawThroughScope(state, far, fogStart, fogEnd, figures);
            }
            waterDrawn = water != null && surfacesMirror && !outside && waterInSight(camera, state, tanHalfFieldOfView, aspect, near, far);
            if (waterDrawn) {
                drawOverWater(camera, state, tanHalfFieldOfView, aspect, near, far, fogStart, fogEnd);
            }
            stage(STAGE_WORLD);
            drawWorld(camera, state, tanHalfFieldOfView, aspect, near, far, fogStart, fogEnd);
            if (struck) {
                impacts.read(sceneTarget, camera, tanHalfFieldOfView, aspect, width, height);
                lookFrom(camera, tanHalfFieldOfView, aspect, near, far, asideX, asideY);
                impacts.holes(decalTarget, sceneDepth, camera, tanHalfFieldOfView, aspect, near, far, width, height, asideX, asideY);
            }
            stage(STAGE_WORLD);
            if (steadying) {
                // Nothing in the picture moves by itself until the people's light says so.
                lend(steadied);
                GL30.glBindFramebuffer(GL_FRAMEBUFFER, movesAlone);
                GL11.glClearColor(0f, 0f, 0f, 0f);
                GL11.glClear(GL_COLOR_BUFFER_BIT);
            }
            view.characters.movers.begin(steadying);
            if (figures) {
                drawFigures(camera, state, tanHalfFieldOfView, aspect, near, far, fogStart, fogEnd);
            }
            view.characters.movers.end();
            view.kerbSteps.sweep(System.nanoTime());
            stage(STAGE_FIGURES);
            drawSky(camera, state, tanHalfFieldOfView, aspect, near, far);
            eyeMoves(camera);
            if (state.seenCount > 0 && !overhead) {
                drawLightning(camera, state, tanHalfFieldOfView, aspect, near, far);
            }
            if ((state.rain > 0f || state.snow > 0f) && sheltered != Shelter.EVERYWHERE) {
                drawRain(camera, state, tanHalfFieldOfView, aspect, near, far);
            }
            // After the sky, as the rain is: the sky is painted wherever the depth has nothing, and what flies leaves the depth alone.
            if (struck) {
                drawDebris(steadying ? movesTarget : sceneTarget, width, height, camera, tanHalfFieldOfView, aspect, near, far, asideX, asideY,
                        fogStart, fogEnd);
            }
            // After the rain, which falls beyond and before a fire alike, and before any glass, which is before it.
            fires.draw(decalTarget, sceneDepth, state, camera, tanHalfFieldOfView, aspect, near, far, width, height, asideX, asideY, frameNanos);
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
            if (steadying) {
                drawSteady(camera, tanHalfFieldOfView, aspect, near, far);
            } else {
                keptAny = false;
            }
            // Dust is what the lit air is made of, so it is drawn where that is, and after the steadying,
            // which would take a speck that has moved for flicker. A vehicle's cabin is not in the shadow maps.
            if (!overhead && shadedAndLit && !seated) {
                drawDust(camera, state, tanHalfFieldOfView, aspect, near, far);
            }
            stage(STAGE_SKY);
            if (shadedAndLit) {
                drawOcclusion(camera, state, tanHalfFieldOfView, aspect, near, far);
            }
            stage(STAGE_OCCLUSION);
            // Seen from overhead the picture is developed without the lit air.
            if (!overhead && shadedAndLit) {
                drawVolume(camera, state, tanHalfFieldOfView, aspect, near, far);
            }
            stage(STAGE_AIR);
            drawBloom(exposure);
            stage(STAGE_GLOW);
            outlinesDrawn = !overhead && state.outlined != null;
            if (outlinesDrawn) {
                drawOutlined(camera, state, tanHalfFieldOfView, aspect, near, far, fogStart, fogEnd);
            }
            drawDeveloped(camera, state, tanHalfFieldOfView, aspect, exposure, near, far);
            stage(STAGE_DEVELOP);
            showSmoothed(drawTarget, viewport);
            view.crosshair.draw(viewport[2], viewport[3]);
            stage(STAGE_SMOOTH);
            reportStages();
            takeBackLent();
            heldBytes = held();
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

    private void drawBehind(ViewCamera.Pose camera, SkyState state, float[] cabin, float far, float fogStart, float fogEnd,
            boolean figures) {
        // What the mirror on the screen shows: the world behind, from where the mirror
        // hangs, drawn small, and as Low draws it whatever the mode: no further than Low's
        // sight, with no depth in its surfaces and no shine on who is in it.
        float sight = Math.min(fogEnd, Quality.sightBehind());
        fogStart *= sight / fogEnd;
        fogEnd = sight;
        float aheadX = view.ride.aheadX, aheadY = view.ride.aheadY;
        behind.eyeX = camera.eyeX + (aheadX * cabin[7] - aheadY * cabin[8]) * Cabins.SCALE;
        behind.eyeY = camera.eyeY + (aheadY * cabin[7] + aheadX * cabin[8]) * Cabins.SCALE;
        behind.eyeUp = camera.eyeUp + cabin[9] * Cabins.SCALE;
        // Where the vehicle's wheel was when it was last drawn says where the mirror
        // is, whichever seat the eye is in and however the head leans.
        float[] wheelAt = view.characters.cabin.wheel;
        VehicleMeshes.Parts body = view.characters.cabin.parts;
        if (wheelAt != null) {
            Cabins.mirrorAt(wheelAt, Cabins.house(view.ride.seatedIn, body != null ? body.measures : Cabins.STANDARD_MEASURES), aheadX, aheadY, mirrorPlace);
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
        shownBehind = listBehind(behind, state, fogEnd);
        lend(mirrored);
        inMirror = true;
        drawWorld(behind, state, MIRROR_TAN, (float) MIRROR_WIDTH / MIRROR_HEIGHT, MIRROR_NEAR, far, fogStart, fogEnd);
        inMirror = false;
        if (figures) {
            drawFiguresAside(behind, MIRROR_TAN, (float) MIRROR_WIDTH / MIRROR_HEIGHT, MIRROR_NEAR, MIRROR_WIDTH, MIRROR_HEIGHT, mirrorFigureTarget, mirrorFigureColor,
                    mirrorFigureNormal, mirrorFigureLamps, mirrorTarget, mirrorDepth, true, state, far, fogStart, fogEnd);
        }
    }

    /**
     * Lists the chunks the mirror's picture shows. The chunks of a frame are chosen for the
     * eye, which looks the other way, so most of them are none of the mirror's: drawn from
     * every one of them its picture took twice as long as from its own.
     */
    private ChunkBuffers.Listing listBehind(ViewCamera.Pose pose, SkyState state, float sight) {
        if (leftOut.length < drawn.size()) {
            leftOut = new boolean[drawn.size() * 2];
        }
        float blown = BLOWN_MOST * (float) Math.hypot(state.windX, state.windY);
        float tan = MIRROR_TAN * MIRROR_WIDTH / MIRROR_HEIGHT;
        int left = 0;
        for (int i = 0; i < drawn.size(); i++) {
            WorldScene.ChunkMeshes build = drawn.get(i);
            float toX = (build.minX + build.maxX) / 2f - pose.eyeX, toY = (build.minY + build.maxY) / 2f - pose.eyeY;
            float reach = (float) Math.hypot((build.maxX - build.minX) / 2f + blown, (build.maxY - build.minY) / 2f + blown);
            leftOut[i] = Cabins.outOfMirror(toX * pose.forwardX + toY * pose.forwardY, toX * pose.rightX + toY * pose.rightY, reach, tan, MIRROR_NEAR, sight);
            if (leftOut[i]) {
                left++;
            }
        }
        if (left == 0) {
            return all;
        }
        behindListed.of(drawn, leftOut, true, null);
        return behindListed;
    }

    /**
     * Whether a chunk with water on it can put a pixel in the eye's picture. The frame holds
     * the chunks all round the eye, and with a river behind the viewer, or beyond the walls of
     * the room they stand in but off to a side, the picture of what water shows is a second
     * drawing of the world that nothing would show.
     */
    private boolean waterInSight(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        ViewCamera.projection(tanHalfFieldOfView, aspect, near, far, projection);
        ViewCamera.turned(camera, viewed);
        mapBounds.ofTurned(projection, viewed);
        float tan = tanHalfFieldOfView * WaterMirror.MARGIN;
        float roughness = WaterMirror.ruffle((float) Math.hypot(state.windX, state.windY));
        waterNearest = Float.POSITIVE_INFINITY;
        boolean seen = false;
        for (int i = 0; i < drawn.size(); i++) {
            WorldScene.ChunkMeshes build = drawn.get(i);
            // A build with no triangles has no box: its middle is not a number, and is outside nothing.
            if (!build.water || build.runs == 0 || mapBounds.outside((build.minX + build.maxX) / 2f - camera.eyeX, (build.minY + build.maxY) / 2f - camera.eyeY,
                    (build.minUp + build.maxUp) / 2f - camera.eyeUp, (build.maxX - build.minX) / 2f, (build.maxY - build.minY) / 2f, (build.maxUp - build.minUp) / 2f)) {
                continue;
            }
            if (!seen) {
                WaterMirror.nothingSeen(waterRect);
                seen = true;
            }
            WaterMirror.takeIn(camera, water, build.minX, build.minY, build.maxX, build.maxY, tan, aspect, roughness, boxCorners, waterRect);
            waterNearest = Math.min(waterNearest, WaterMirror.nearest(camera, water, build.minX, build.minY, build.maxX, build.maxY));
        }
        if (!seen) {
            return false;
        }
        return WaterMirror.showsAny(waterRect);
    }

    /**
     * What the water of the frame shows of the world over it, sky and all: see WaterMirror.
     * Drawn before the eye's own picture of the world, which shows it wherever it draws water.
     */
    private void drawOverWater(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far,
            float fogStart, float fogEnd) {
        WaterMirror.mirror(camera, water, under);
        lend(watered);
        float tan = tanHalfFieldOfView * WaterMirror.MARGIN;
        // The world only where the water is, and the coarser levels of it, which take in what is
        // round it: the rest of the picture is the sky, which nothing reads.
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, waterTarget);
        GL11.glViewport(0, 0, waterWidth, waterHeight);
        GL11.glDepthMask(true);
        GL11.glColorMask(true, true, true, true);
        GL11.glClearColor(0f, 0f, 0f, 1f);
        GL11.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        int fromX = Math.max(0, (int) Math.floor((waterRect[0] * 0.5f + 0.5f) * waterWidth) - WATER_RECT_TEXELS);
        int fromY = Math.max(0, (int) Math.floor((waterRect[1] * 0.5f + 0.5f) * waterHeight) - WATER_RECT_TEXELS);
        int toX = Math.min(waterWidth, (int) Math.ceil((waterRect[2] * 0.5f + 0.5f) * waterWidth) + WATER_RECT_TEXELS);
        int toY = Math.min(waterHeight, (int) Math.ceil((waterRect[3] * 0.5f + 0.5f) * waterHeight) + WATER_RECT_TEXELS);
        GL11.glScissor(fromX, fromY, toX - fromX, toY - fromY);
        cropRect[0] = Math.max(-1f, waterRect[0] - 2f * WATER_RECT_TEXELS / waterWidth);
        cropRect[1] = Math.max(-1f, waterRect[1] - 2f * WATER_RECT_TEXELS / waterHeight);
        cropRect[2] = Math.min(1f, waterRect[2] + 2f * WATER_RECT_TEXELS / waterWidth);
        cropRect[3] = Math.min(1f, waterRect[3] + 2f * WATER_RECT_TEXELS / waterHeight);
        GL11.glEnable(GL_SCISSOR_TEST);
        inWater = true;
        drawWorld(under, state, tan, aspect, near, far, fogStart, fogEnd);
        inWater = false;
        GL11.glDisable(GL_SCISSOR_TEST);
        drawSkyInto(waterTarget, waterWidth, waterHeight, false, under, state, tan, aspect, near, far);
        // Far water reads it coarser than it was drawn: see overWater in the world's program.
        GL11.glBindTexture(GL_TEXTURE_2D, waterColor);
        GL30.glGenerateMipmap(GL_TEXTURE_2D);
    }

    /**
     * What the eyepiece of the scope on the weapon held shows: the world from its objective
     * lens, along its axis, as narrow as its magnification makes it, with the people in it and
     * the sky behind. It is the same light as the picture round it, and is developed with it.
     */
    private void drawThroughScope(SkyState state, float far, float fogStart, float fogEnd, boolean figures) {
        float tan = view.scope.pictureTan();
        view.scope.lendPicture(frameNanos);
        inScope = true;
        drawWorld(view.scope.sees, state, tan, 1f, Scope.PICTURE_NEAR, far, fogStart, fogEnd);
        inScope = false;
        if (struck) {
            lookFrom(view.scope.sees, tan, 1f, Scope.PICTURE_NEAR, far, 0f, 0f);
            impacts.holes(view.scope.decalTarget, view.scope.pictureDepth, view.scope.sees, tan, 1f, Scope.PICTURE_NEAR, far, Scope.PICTURE, Scope.PICTURE, 0f, 0f);
        }
        if (figures) {
            drawFiguresAside(view.scope.sees, tan, 1f, Scope.PICTURE_NEAR, Scope.PICTURE, Scope.PICTURE, view.scope.figureTarget, view.scope.figureColor, view.scope.figureNormal, view.scope.figureLamps,
                    view.scope.pictureTarget, view.scope.pictureDepth, false, state, far, fogStart, fogEnd);
        }
        drawSkyInto(view.scope.pictureTarget, Scope.PICTURE, Scope.PICTURE, false, view.scope.sees, state, tan, 1f, Scope.PICTURE_NEAR, far);
        if (struck) {
            drawDebris(view.scope.pictureTarget, Scope.PICTURE, Scope.PICTURE, view.scope.sees, tan, 1f, Scope.PICTURE_NEAR, far, 0f, 0f, fogStart, fogEnd);
        }
        view.scope.developed();
    }

    /** The developed picture, its edges smoothed, into the part of the game's own picture the frame is written to. */
    private void showSmoothed(int drawTarget, int[] viewport) {
        GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawTarget);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        // The game's own picture has a depth of its own, with the game's vehicles in it: tested
        // against that, the picture would leave each of them showing through.
        GL11.glDisable(GL_DEPTH_TEST);
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
     * every time.
     */
    private int bound(int kind) {
        GL11.glGetIntegerv(kind, asked);
        return asked[0];
    }

    /** Switches off the arrays of what a vertex carries for a card and its tree's square: only the frame's own vertices have them. */
    private static void cardArrayOff() {
        GL13.glClientActiveTexture(GL_TEXTURE0 + 1);
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL13.glClientActiveTexture(GL_TEXTURE0 + 2);
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL13.glClientActiveTexture(GL_TEXTURE0);
    }

    /** Points OpenGL at the frame's vertices. Done again after the game has drawn anything, which leaves its own arrays set. */
    private void arrays() {
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
        GL13.glClientActiveTexture(GL_TEXTURE0 + 1);
        GL11.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        GL13.glClientActiveTexture(GL_TEXTURE0 + 2);
        GL11.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        GL13.glClientActiveTexture(GL_TEXTURE0);
        ChunkBuffers.point();
    }

    /**
     * Draws the ranges telling the program the size of a texel of each one's texture and the
     * colour it is painted over in, and leaves it told that nothing is painted over.
     */
    private void drawRanges(ChunkBuffers.Listing listed, int from, int to, Program program) {
        int texel = program.at("uTexel"), painted = program.at("uTint"), joints = program.at("uJoint");
        boolean told = false;
        int toldKey = 0;
        for (int i = from; i < to; i++) {
            int texture = listed.texture(i) == 0 ? whiteTexture : listed.texture(i);
            GL11.glBindTexture(GL_TEXTURE_2D, texture);
            tellTexel(texel, texture);
            int key = listed.tint(i);
            if (!told || key != toldKey) {
                WallSides.joint(key, tint);
                GL20.glUniform3f(joints, tint[0], tint[1], tint[2]);
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
        if (texelOf[slot] == 0 || gameFrame - texelRead[slot] >= TEXEL_KEPT_FRAMES + (texture & TEXEL_KEPT_FRAMES - 1)) {
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
            texelRead[slot] = gameFrame;
        }
        GL20.glUniform2f(at, 1f / texelWide[slot], 1f / texelHigh[slot]);
    }

    /** Draws the ranges but the panes of windows, which are drawn last of all (see drawPanes), and the blood, drawn after the rest of what is laid (see drawBlood). */
    private void drawRanges(ChunkBuffers.Listing listed, int from, int to) {
        for (int i = from; i < to; i++) {
            if (listed.tint(i) == WallSides.PANES || listed.tint(i) == WallSides.BLOOD) {
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
     * past its end. The list of the models' triangles stays bound until drawModels has drawn
     * them. The models carry no colour, and the colour array would be read from the
     * chunks' buffer at a model's vertex numbers, so that array is off until arrays() puts it
     * back.
     */
    private static void modelArrays() {
        boolean made = modelBuffer != 0;
        if (!made) {
            modelBuffer = GL15.glGenBuffers();
            modelCorners = GL15.glGenBuffers();
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, modelBuffer);
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, modelCorners);
        if (!made) {
            float[] models = Models.buffer();
            int[] corners = Models.corners();
            OffHeap.Block memory = OffHeap.take(Math.max(models.length * Float.BYTES, corners.length * Integer.BYTES));
            try {
                GL15.glBufferData(GL_ARRAY_BUFFER, memory.bytes.asFloatBuffer().put(0, models), GL_STATIC_DRAW);
                memory.bytes.asIntBuffer().put(0, corners);
                GL15.glBufferData(GL_ELEMENT_ARRAY_BUFFER, memory.bytes.slice(0, corners.length * Integer.BYTES), GL_STATIC_DRAW);
            } finally {
                memory.free();
            }
        }
        GL13.glClientActiveTexture(GL_TEXTURE0);
        GL11.glVertexPointer(3, GL_FLOAT, MODEL_STRIDE, 0L);
        GL11.glNormalPointer(GL_FLOAT, MODEL_STRIDE, 3L * Float.BYTES);
        GL11.glTexCoordPointer(2, GL_FLOAT, MODEL_STRIDE, 6L * Float.BYTES);
        cardArrayOff();
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /** What Screens.now says of the glass of a set showing a programme. */
    private final float[] tube = new float[4];

    /**
     * Draws the furniture standing on the chunks the frame shows, each piece from the one copy
     * of its model the graphics card holds, and leaves the frame's own arrays set as it found
     * them.
     *
     * @param lit     whether the program lights and colours what it draws, or only casts it into the map heldBy last took the bounds of
     * @param shaded  whether a piece's light is told to the program: its depth alone is drawn without it, since the light is only ever colour
     * @param within  the eye of a picture that pieces wholly behind it or beyond far are left out of, or null to draw them all
     * @param pixel   how far apart two things a tile from that eye are when they are a pixel apart in its picture: see
     *                Models.Instances.plain. A map of shadows is always cast by the plain models, and a picture with no eye
     *                given is drawn whole.
     * @param only    the key of the one thing whose pieces are drawn (Outlines.key), or Outlines.NONE for every piece
     */
    private void drawModels(Program program, boolean lit, boolean shaded, ViewCamera.Pose within, float far, float pixel, long only) {
        if (lit) {
            Screens.giveBack();
        }
        boolean any = false;
        int bound = 0;
        int placeAt = 0, footAt = 0, turnAt = 0, litAt = 0, alongAt = 0, downAt = 0, acrossAt = 0, surfacesAt = 0, dyeAt = 0, pictureAt = 0, texelAt = 0;
        // Whether the program was last told of a tube showing part of its picture, which the next piece is not.
        int tubeAt = 0;
        boolean tubed = false;
        // The piece whose turn, surfaces, dye and picture the program was last told, none yet: a
        // row of one kind of shelf shares all four, and telling the program what it already
        // holds is a call into the driver for nothing.
        int told = -1;
        float[] toldOf = null;
        // The eye's own picture draws a room's furniture only while it sees into the room, and
        // it and a scope's only what can be in their pictures: seenBy has just set the bounds of each.
        RoomSight.Seen rooms = within != null && eyesOwn() ? roomsSeen : null;
        boolean bounded = within != null && !inMirror;
        for (int chunk = 0; chunk < drawn.size(); chunk++) {
            if ((inMirror && shownBehind == behindListed || inWater) && leftOut[chunk]) {
                continue;
            }
            Models.Instances placed = drawn.get(chunk).placed;
            float[] f = placed.floats;
            int[] whole = placed.ints;
            for (int piece = 0; piece < placed.count; piece++) {
                if (only != Outlines.NONE && placed.keys[piece] != only) {
                    continue;
                }
                int at = piece * Models.Instances.FLOATS, ints = piece * Models.Instances.INTS;
                int ball = at + Models.Instances.BALL;
                if (whole[ints + Models.Instances.BUILT] == 0 && !drawDistance.draws(f[ball], f[ball + 1])) {
                    continue;
                }
                boolean plain = !lit;
                if (within != null) {
                    float ahead = (f[ball] - within.eyeX) * within.forwardX + (f[ball + 1] - within.eyeY) * within.forwardY
                            + (f[ball + 2] - within.eyeUp) * within.forwardUp;
                    if (ahead < -f[ball + 3] || ahead > far + f[ball + 3]) {
                        continue;
                    }
                    if (!ChunkBuffers.listed(placed.rooms[piece], rooms)) {
                        continue;
                    }
                    float reach = f[ball + 3] + SEEN_MARGIN;
                    if (bounded && mapBounds.outside(f[ball] - within.eyeX, f[ball + 1] - within.eyeY, f[ball + 2] - within.eyeUp, reach, reach, reach)) {
                        continue;
                    }
                    // The picture of what water shows holds nothing nearer than the water: see seenBy.
                    if (inWater && length(f[ball] - within.eyeX, f[ball + 1] - within.eyeY, f[ball + 2] - within.eyeUp) + f[ball + 3] < waterNearest) {
                        continue;
                    }
                    plain = placed.plain(piece, ahead, pixel);
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
                        tubeAt = program.at("uTube");
                        GL20.glUniform4f(tubeAt, 0.5f, 0.5f, 1f, Screens.IN_COLOUR);
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
                    if (shaded) {
                        GL20.glUniform3f(litAt, f[light], f[light + 1], f[light + 2]);
                        GL20.glUniform3f(alongAt, f[light + 3], f[light + 4], f[light + 5]);
                        GL20.glUniform3f(downAt, f[light + 6], f[light + 7], f[light + 8]);
                        GL20.glUniform3f(acrossAt, f[light + 9], f[light + 10], f[light + 11]);
                    }
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
                    // A number below zero is one of the mod's own pictures: see Decor.held.
                    int named = whole[ints + Models.Instances.TEXTURE];
                    boolean live = named < 0 && Screens.live(named);
                    if (live) {
                        // The glass of a set showing a programme: which shot it shows and what part of the shot is
                        // worked out now, and the picture is laid so that the part fills the glass.
                        named = Screens.now(named, tube);
                        float of = tube[2];
                        GL20.glUniform4f(dyeAt, tube[0] + (f[dye] - 0.5f) * of, tube[1] + (f[dye + 1] - 0.5f) * of, f[dye + 2], f[dye + 3]);
                        GL20.glUniform4f(pictureAt, f[picture] * of, f[picture + 1] * of, f[picture + 2] * of, f[picture + 3] * of);
                        GL20.glUniform4f(tubeAt, tube[0], tube[1], of, tube[3] == Screens.SNOW ? Screens.SNOW : f[dye + 2]);
                        tubed = true;
                    } else if (tubed) {
                        GL20.glUniform4f(tubeAt, 0.5f, 0.5f, 1f, Screens.IN_COLOUR);
                        tubed = false;
                    }
                    int texture = named == 0 ? whiteTexture : named < 0 ? Decor.texture(named) : named;
                    if (texture != bound) {
                        bound = texture;
                        GL11.glBindTexture(GL_TEXTURE_2D, texture);
                        tellTexel(texelAt, texture);
                    }
                }
                // What a tube was told of its dye and its picture is not what the piece holds: the next piece is told its own.
                toldOf = tubed ? null : f;
                told = at;
                GL11.glDrawElements(GL_TRIANGLES, whole[ints + (plain ? Models.Instances.PLAIN_COUNT : Models.Instances.COUNT)], GL_UNSIGNED_INT,
                        (long) whole[ints + (plain ? Models.Instances.PLAIN_FIRST : Models.Instances.FIRST)] * Integer.BYTES);
            }
        }
        if (any) {
            GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
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

    /** The light the far shadow map was last drawn along, and how near this frame's has to be to it, as the cosine between, for it to be read as it is. */
    private float farLightX, farLightY, farLightUp = 1f;
    private static final float FAR_LIGHT_SAME = 0.9995f;

    private void drawShadows(SkyState state, boolean figures) {
        lightMatrix(nearMatrix, state, NEAR_EXTENT, NEAR_RANGE);
        // The far map and the map of what is overhead are each drawn every other frame, turn about. Both are
        // of things tens of tiles off or straight overhead, and each is read through the matrix it was last
        // drawn with, so a frame late is not seen.
        // A strike takes the light over for a few frames and gives it back: a far map drawn along
        // another light than this frame's would cast its shadows the wrong way.
        boolean lightMoved = state.lightX * farLightX + state.lightY * farLightY + state.lightUp * farLightUp < FAR_LIGHT_SAME;
        castFar = !farDrawn || (frameNumber & 1) == 0 || lightMoved;
        boolean castRoof = !roofDrawn || (frameNumber & 1) == 1;
        if (castFar) {
            lightMatrix(farMatrix, state, FAR_EXTENT, FAR_RANGE);
            farDrawn = true;
            farLightX = state.lightX;
            farLightY = state.lightY;
            farLightUp = state.lightUp;
        }
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
        GL11.glPolygonOffset(SLOPE_OFFSET, 6f);
        GL11.glViewport(0, 0, shadowSize, shadowSize);
        shadow.use();
        shadow.set("uTexture", 0);
        shadow.set("uWind", state.windX, state.windY, state.seconds);
        shadow.set("uGust", state.gustX, state.gustY, state.flutter);
        shadow.set("uPlantSnow", state.snowOnPlants());
        shadow.set("uFaced", state.facedX, state.facedY);
        shadow.set("uLayerFrom", state.layersFromX, state.layersFromY, state.layersFromUp);
        shadow.set("uLayer", WorldScene.STAND_OFF, WorldScene.STAND_OFF_PER_TILE_SQUARED, WorldScene.CARD_LAYER);
        shadow.set("uAcross", state.acrossX, state.acrossY);
        shadow.set("uSunWay", state.lightX, state.lightY, state.lightUp);
        // The game magnifies its sprites pixel by pixel, which up close is a wall of squares.
        // This frame reads them through a sampler of its own that blends between pixels, and
        // leaves the textures' own settings as the game set them.
        GL33.glBindSampler(0, spriteSampler);
        castSun(state, figures);
        castBeams(state, figures);
        GL13.glActiveTexture(GL_TEXTURE0 + BEAM_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, beamed.made() ? beamMap : farDepth);
        GL13.glActiveTexture(GL_TEXTURE0);
        castTorch(state, figures);
        if (castRoof) {
            castRoof(state);
            roofDrawn = true;
        }
        heldOf = drawn.size();
        GL11.glDisable(GL_POLYGON_OFFSET_FILL);
        GL11.glColorMask(true, true, true, true);
    }

    /** The near and the far shadow map, along the light. */
    private void castSun(SkyState state, boolean figures) {
        for (int map = 0; map < (castFar ? 2 : 1); map++) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, map == 0 ? nearTarget : farTarget);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL_DEPTH_BUFFER_BIT);
            shadow.set("uMatrix", map == 0 ? nearMatrix : farMatrix);
            castHeld(map == 0 ? nearMatrix : farMatrix, state, map == 0 ? nearListed : farListed, map == 0 ? NEAR_MAP : FAR_MAP);
        }
        if (figures) {
            // The game's own draw of each character, with the light's matrix where its camera
            // would be. It sets its own program, buffers and culling, so the frame's are put back.
            for (int map = 0; map < (castFar ? 2 : 1); map++) {
                GL30.glBindFramebuffer(GL_FRAMEBUFFER, map == 0 ? nearTarget : farTarget);
                if (map == 0) {
                    view.characters.cast(nearMatrix);
                } else {
                    view.characters.castBeyond(farMatrix, nearMatrix);
                }
            }
            resumeCasting();
        }
    }

    private void castBeams(SkyState state, boolean figures) {
        // The scene as the nearest lit headlights see it, so that what stands in their beam
        // throws its shadow down the road.
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
            multiply(cast, seen, beamMatrix);
            lend(beamed);
            beginCast(beamTarget, beamMatrix);
            castHeld(beamMatrix, state, beamListed, BEAM_MAP);
            if (figures) {
                view.characters.castCharacters(beamMatrix, seen, BEAM_TAN, BEAM_FAR);
                resumeCasting();
            }
        }
    }

    /**
     * The scene as the torch the viewer carries sees it, so that the beam stops at whatever
     * stands in it: a wall, a banister, the floor of the landing above, a zombie in a doorway.
     * Everyone but the viewer is drawn into it: the torch is in the viewer's own hand, and a
     * map with the viewer's body in it would have the beam light nothing but the viewer.
     */
    private void castTorch(SkyState state, boolean figures) {
        float[] carried = state.carried;
        heldChunks[TORCH_MAP] = 0;
        torchAt[3] = 0f;
        if (carried[CarriedLight.REACH] <= 0f || carried[CarriedLight.CONE] <= 0f) {
            return;
        }
        float wayX = carried[CarriedLight.WAY], wayY = carried[CarriedLight.WAY + 1], wayUp = carried[CarriedLight.WAY + 2];
        System.arraycopy(carried, CarriedLight.HELD, torchAt, 0, 3);
        torchAt[3] = carried[CarriedLight.REACH];
        torchWay[0] = wayX;
        torchWay[1] = wayY;
        torchWay[2] = wayUp;
        ViewCamera.clean(lamp, torchAt[0], torchAt[1], torchAt[2], (float) Math.atan2(wayY, wayX), (float) Math.asin(Math.clamp(wayUp, -1f, 1f)));
        float[] seen = viewed, cast = projection;
        ViewCamera.view(lamp, seen);
        ViewCamera.projection(TORCH_TAN, 1f, TORCH_NEAR, torchAt[3] + TORCH_PAST_REACH, cast);
        multiply(cast, seen, torchMatrix);
        lend(torched);
        GL11.glViewport(0, 0, torchSize, torchSize);
        beginCast(torchTarget, torchMatrix);
        castHeld(torchMatrix, state, torchListed, TORCH_MAP);
        if (figures) {
            view.characters.castOthers(torchMatrix);
            resumeCasting();
        }
        GL11.glViewport(0, 0, shadowSize, shadowSize);
    }

    /** Drawn whatever the weather: the rain is kept off by it, and the light of the sky is traced against it. */
    private void castRoof(SkyState state) {
        float texel = 2f * ROOF_EXTENT / shadowSize;
        Arrays.fill(roofMatrix, 0f);
        roofMatrix[0] = 1f / ROOF_EXTENT;
        roofMatrix[12] = -(float) Math.floor(focusX / texel) * texel / ROOF_EXTENT;
        roofMatrix[5] = 1f / ROOF_EXTENT;
        roofMatrix[13] = -(float) Math.floor(focusY / texel) * texel / ROOF_EXTENT;
        roofMatrix[10] = -1f / ROOF_RANGE;
        roofMatrix[14] = focusUp / ROOF_RANGE;
        roofMatrix[15] = 1f;
        beginCast(roofTarget, roofMatrix);
        shadow.set("uFromAbove", 1f);
        try {
            castHeld(roofMatrix, state, roofListed, ROOF_MAP);
        } finally {
            // Every other map this program draws has its wires in it.
            shadow.set("uFromAbove", 0f);
        }
    }

    /** Binds and clears a map that is not the sun's, and has the shadow program cast into it along the matrix. */
    private void beginCast(int target, float[] matrix) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL_DEPTH_BUFFER_BIT);
        shadow.use();
        shadow.set("uMatrix", matrix);
        shadow.set("uSunWay", 0f, 0f, 1f);
        GL13.glActiveTexture(GL_TEXTURE0);
    }

    /** Casts the frame's solid chunks and furniture that a map drawn with the matrix can hold into the map bound. */
    private void castHeld(float[] matrix, SkyState state, ChunkBuffers.Listing own, int map) {
        ChunkBuffers.Listing held = heldBy(matrix, state, own, map);
        drawRanges(held, 0, held.solidRanges());
        drawModels(shadow, false, false, null, 0f, 0f, Outlines.NONE);
    }

    /**
     * The frame's chunks that a map drawn with this matrix can hold, as draws, and the map's
     * bounds left in mapBounds for the furniture. A chunk whose every vertex, wherever the wind has moved it,
     * is outside the map puts nothing in it, drawn or not, so the map is the same texels with
     * it left out. Into own when any is left out, and all of them otherwise.
     */
    private ChunkBuffers.Listing heldBy(float[] matrix, SkyState state, ChunkBuffers.Listing own, int map) {
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
        own.of(drawn, leftOut, false, null);
        return own;
    }

    /**
     * The frame's chunks that can put a pixel in the picture the projection makes from an eye,
     * as draws. The frame holds every chunk near the eye whichever way it looks, since what is
     * behind it still casts a shadow into the picture, and most of them are behind it or off to
     * a side: listed for the picture too, every vertex of theirs was worked out in each of its
     * passes to be thrown away.
     */
    private ChunkBuffers.Listing seenBy(ViewCamera.Pose camera, SkyState state) {
        ViewCamera.turned(camera, viewed);
        if (inWater) {
            // The picture of what water shows is drawn only where the water is, and what the rest of it would hold is left out.
            System.arraycopy(projection, 0, cropped, 0, 16);
            float halfX = (cropRect[2] - cropRect[0]) / 2f, halfY = (cropRect[3] - cropRect[1]) / 2f;
            float middleX = (cropRect[2] + cropRect[0]) / 2f, middleY = (cropRect[3] + cropRect[1]) / 2f;
            cropped[0] /= halfX;
            cropped[8] = (cropped[8] + middleX) / halfX;
            cropped[5] /= halfY;
            cropped[9] = (cropped[9] + middleY) / halfY;
            mapBounds.ofTurned(cropped, viewed);
        } else {
            mapBounds.ofTurned(projection, viewed);
        }
        if (leftOut.length < drawn.size()) {
            leftOut = new boolean[drawn.size() * 2];
        }
        float blown = BLOWN_MOST * (float) Math.hypot(state.windX, state.windY) + SEEN_MARGIN;
        int left = 0;
        for (int i = 0; i < drawn.size(); i++) {
            WorldScene.ChunkMeshes build = drawn.get(i);
            float midX = (build.minX + build.maxX) / 2f, midY = (build.minY + build.maxY) / 2f, midUp = (build.minUp + build.maxUp) / 2f;
            float halfX = (build.maxX - build.minX) / 2f, halfY = (build.maxY - build.minY) / 2f, halfUp = (build.maxUp - build.minUp) / 2f;
            // A layer of cards as the program works it out for the vertex of the chunk furthest from where layers are counted from, which is the widest any is.
            float furthest = length(midX - state.layersFromX, midY - state.layersFromY, midUp - state.layersFromUp) + length(halfX, halfY, halfUp);
            float card = WorldScene.CARD_LAYER + 4f * (WorldScene.STAND_OFF + WorldScene.STAND_OFF_PER_TILE_SQUARED * furthest * furthest);
            float reach = blown + build.layered * card;
            leftOut[i] = mapBounds.outside(midX - camera.eyeX, midY - camera.eyeY, midUp - camera.eyeUp, halfX + reach + build.turned,
                    halfY + reach + build.turned, halfUp + reach);
            // Seen from under the water the eye's line is under the water until it meets it, and
            // what is all of it nearer than the nearest water in sight is in no reflection.
            if (inWater && !leftOut[i] && length(Math.abs(midX - camera.eyeX) + halfX + reach + build.turned, Math.abs(midY - camera.eyeY) + halfY + reach + build.turned,
                    Math.abs(midUp - camera.eyeUp) + halfUp + reach) < waterNearest) {
                leftOut[i] = true;
            }
            if (leftOut[i]) {
                left++;
            }
        }
        RoomSight.Seen rooms = eyesOwn() ? roomsSeen : null;
        if (left == 0 && rooms == null) {
            return all;
        }
        seenListed.of(drawn, leftOut, true, rooms);
        return seenListed;
    }

    /** Whether the picture being drawn is the eye's own, and not a scope's, a mirror's or the one of what water shows. */
    private boolean eyesOwn() {
        return !inMirror && !inScope && !inWater;
    }

    private static float length(float x, float y, float up) {
        return (float) Math.sqrt(x * x + y * y + up * up);
    }

    private void drawSteady(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far) {
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
        steady.set("uAheadBefore", 3);
        GL13.glActiveTexture(GL_TEXTURE0 + 3);
        GL11.glBindTexture(GL_TEXTURE_2D, keptAhead[keptNow]);
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
        ViewCamera.Pose aim = view.characters.aim;
        steady.set("uAimEye", aim.eyeX, aim.eyeY, aim.eyeUp);
        steady.set("uAimRight", aim.rightX, aim.rightY, aim.rightUp);
        steady.set("uAimUp", aim.upX, aim.upY, aim.upUp);
        steady.set("uAimForward", aim.forwardX, aim.forwardY, aim.forwardUp);
        steady.set("uAimEyeBefore", aimBefore.eyeX, aimBefore.eyeY, aimBefore.eyeUp);
        steady.set("uAimRightBefore", aimBefore.rightX, aimBefore.rightY, aimBefore.rightUp);
        steady.set("uAimUpBefore", aimBefore.upX, aimBefore.upY, aimBefore.upUp);
        steady.set("uAimForwardBefore", aimBefore.forwardX, aimBefore.forwardY, aimBefore.forwardUp);
        steady.set("uMoves", 4);
        GL13.glActiveTexture(GL_TEXTURE0 + 4);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneMoves);
        GL13.glActiveTexture(GL_TEXTURE0);
        Movers movers = view.characters.movers;
        steady.set("uMovers", (float) movers.found);
        for (int i = 0; i < movers.found; i++) {
            steady.set(MOVER_AT[i], movers.now[i * 4], movers.now[i * 4 + 1], movers.now[i * 4 + 2], movers.now[i * 4 + 3]);
            steady.set(MOVER_BEFORE[i], movers.before[i * 4], movers.before[i * 4 + 1], movers.before[i * 4 + 2], movers.before[i * 4 + 3]);
        }
        steady.set("uKept", keptAny && !WITHOUT.contains("steady") ? KEPT : 0f);
        steady.set("uOwnWithin", seated ? 3.2f : 0f);
        steady.set("uHeldWithin", seated || outside || overhead ? 0f : HELD_WITHIN);
        quad();
        // The rest of the frame is made from the steadied picture. Nothing is drawn into the scene after this.
        frameColor = keptColor[next];
        keptNow = next;
        keptAny = true;
        before.set(camera);
        aimBefore.set(aim);
        GL11.glDepthMask(true);
    }

    /** Loads the matrices of a picture drawn from a camera, drawn aside as the world was. */
    private void lookFrom(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far, float asideX, float asideY) {
        GL11.glMatrixMode(GL_PROJECTION);
        ViewCamera.projection(tanHalfFieldOfView, aspect, near, far, projection);
        projection[8] += asideX;
        projection[9] += asideY;
        GL11.glLoadMatrixf(projection);
        GL11.glMatrixMode(GL_MODELVIEW);
        ViewCamera.view(camera, viewed);
        GL11.glLoadMatrixf(viewed);
    }

    /** What flies from where shots struck, into a picture after its world, its people and its sky. */
    private void drawDebris(int target, int wide, int high, ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far,
            float asideX, float asideY, float fogStart, float fogEnd) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL11.glViewport(0, 0, wide, high);
        lookFrom(camera, tanHalfFieldOfView, aspect, near, far, asideX, asideY);
        arrays();
        cardArrayOff();
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL_NORMAL_ARRAY);
        impacts.debris(camera, frameNanos, fogStart, fogEnd);
        GL11.glDisableClientState(GL_VERTEX_ARRAY);
    }

    /** The box of air rain and snow fall in: its width, its height, and how far under the eye it reaches. See the rain shader and Wind. */
    private static final float RAIN_BOX = Wind.CARRIED_WRAP, RAIN_HIGH = 12f, RAIN_UNDER_EYE = 0.35f * RAIN_HIGH;
    /** Half the width of a streak of rain, in tiles, and how long the eye takes a drop in for, in seconds: a streak is as long as the drop goes in that. */
    private static final float RAIN_HALF_WIDE = 0.003f, RAIN_SEEN_FOR = 0.02f;
    /** How much of a drop and of a flake shows, at its middle. */
    private static final float RAIN_SHOWN = 0.55f, SNOW_SHOWN = 0.9f;
    /** How much of lightning's light over the sky a drop of rain shows: a lit drop is a bead of the flash. */
    private static final float RAIN_FLASHED = 1.2f;
    /**
     * From far overhead: how far under the place looked at the box reaches at least, the most
     * boxes drawn along each side of the ground in the picture, and the least a streak or a
     * flake is across, in dots of the picture.
     */
    private static final float RAIN_UNDER_PLACE = 1f;
    private static final int RAIN_BOXES_MOST = 9;
    /**
     * The least the camera far overhead looks down for its lines of sight to be followed to
     * the viewer's floor, as the way it looks goes up for each tile on. On its flight to an
     * eye that looks level or up they never come down on that floor.
     */
    private static final float SEEN_ON_FLOOR_FROM = 0.2f;
    private static final float RAIN_DOTS_LEAST = 1.1f, SNOW_DOTS_LEAST = 1.5f;
    /**
     * How long the eye takes a flake in for, in seconds: a flake is drawn out by what it goes past the eye in that,
     * up to its own width. Standing in still air it is round; in a gale or met at speed it is a little long.
     */
    private static final float SNOW_SEEN_FOR = 0.03f;
    /**
     * How fast the eye has been going, in tiles a second, eased over EYE_MOVES_SECONDS, for the streaks of rain
     * and snow: see eyeMoves. A move faster than EYE_MOVES_MOST is a jump to another place, and nothing streaks.
     */
    private float movesX, movesY, movesUp;
    private float lastEyeX, lastEyeY, lastEyeUp;
    private long lastEyeNanos;
    private static final float EYE_MOVES_SECONDS = 0.08f, EYE_MOVES_MOST = 60f;
    /**
     * From far overhead a line of sight crosses the whole depth of the falling rain, where an
     * eye in it sees the near drops large and few. This share of the drops, and of the flakes,
     * is drawn there, which is as heavy as the game's own view has its rain and its snow.
     */
    private static final float RAIN_SHARE_OVERHEAD = 0.3f, SNOW_SHARE_OVERHEAD = 0.35f;
    /** How long a drop is taken in for from far overhead: the game's own view has its rain as short strokes, a fifth of a tile or so. */
    private static final float RAIN_SEEN_FOR_OVERHEAD = 0.014f;
    private final float[] rainBox = new float[4];

    /** The rain or the snow, into the picture after the world and the sky and before anything is made of the picture. */
    private void drawRain(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        GL11.glViewport(0, 0, width, height);
        GL11.glMatrixMode(GL_PROJECTION);
        ViewCamera.projection(tanHalfFieldOfView, aspect, near, far, projection);
        // Aside as the world was drawn: the panes drawn after this load the same matrix and are tested against the world's depth.
        projection[8] += asideX;
        projection[9] += asideY;
        GL11.glLoadMatrixf(projection);
        GL11.glMatrixMode(GL_MODELVIEW);
        ViewCamera.view(camera, viewed);
        GL11.glLoadMatrixf(viewed);
        arrays();
        cardArrayOff();
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
        holdShelter(SHELTER_UNIT);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, roofMap);
        // Round the eye, the one box the eye is in. From far overhead, the boxes over the ground
        // the picture shows and over as much again towards the camera as the rain stands high,
        // which is where the lines of sight to that ground come down through it.
        int boxesX = 1, boxesY = 1;
        float dot = 0f, aheadX = 1f, aheadY = 0f, reachAlong = 0f, reachAcross = 0f;
        rainBox[0] = camera.eyeX;
        rainBox[1] = camera.eyeY;
        rainBox[2] = camera.eyeUp;
        rainBox[3] = RAIN_UNDER_EYE;
        if (overhead) {
            float halfHigh = tanHalfFieldOfView * away, halfWide = halfHigh * aspect;
            float flat = (float) Math.hypot(camera.forwardX, camera.forwardY);
            float steep = Math.max(-camera.forwardUp, SEEN_ON_FLOOR_FROM);
            if (flat > 1e-4f) {
                aheadX = camera.forwardX / flat;
                aheadY = camera.forwardY / flat;
            }
            float towards = RAIN_HIGH * flat / steep, along = halfHigh / steep;
            float placeUp = camera.eyeUp + camera.forwardUp * away;
            rainBox[0] = camera.eyeX + camera.forwardX * away - aheadX * towards / 2f;
            rainBox[1] = camera.eyeY + camera.forwardY * away - aheadY * towards / 2f;
            rainBox[2] = placeUp;
            rainBox[3] = Math.clamp(placeUp + RAIN_UNDER_PLACE, RAIN_UNDER_PLACE, RAIN_UNDER_EYE);
            reachAlong = along + towards / 2f;
            reachAcross = halfWide;
            float reachX = Math.abs(aheadX) * reachAlong + Math.abs(aheadY) * reachAcross;
            float reachY = Math.abs(aheadY) * reachAlong + Math.abs(aheadX) * reachAcross;
            boxesX = Math.clamp((int) Math.ceil(2f * reachX / RAIN_BOX), 1, RAIN_BOXES_MOST);
            boxesY = Math.clamp((int) Math.ceil(2f * reachY / RAIN_BOX), 1, RAIN_BOXES_MOST);
            // Half a dot of the picture, in tiles, at the place looked at.
            dot = halfHigh / height;
        }
        boolean lidded = overhead && lid < Float.MAX_VALUE / 2f;
        boolean seenOnFloor = overhead && camera.forwardUp < -SEEN_ON_FLOOR_FROM;
        if (state.rain > 0f) {
            // Added to what is there. The picture's alpha is left alone: it says what mirrors.
            GL14.glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_ZERO, GL_ONE);
            rain.use();
            rain.set("uRoof", 0);
            rain.set("uRoofMatrix", roofMatrix);
            rain.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
            rain.set("uFall", state.carriedX, state.carriedY, state.rainClock);
            rain.set("uAir", state.airX, state.airY);
            rain.set("uRain", state.rain * (overhead ? RAIN_SHARE_OVERHEAD : 1f));
            rain.set("uSeated", seated ? 1f : 0f);
            rain.set("uBox", rainBox[0], rainBox[1], rainBox[2], rainBox[3]);
            rain.set("uThin", Math.max(RAIN_HALF_WIDE, RAIN_DOTS_LEAST * dot));
            rain.set("uPerPixel", overhead ? 0f : RAIN_DOTS_LEAST * tanHalfFieldOfView / height);
            rain.set("uLong", overhead ? RAIN_SEEN_FOR_OVERHEAD : RAIN_SEEN_FOR);
            rain.set("uMoves", movesX, movesY, movesUp);
            rain.set("uShown", RAIN_SHOWN);
            setShelter(rain, SHELTER_UNIT, lidded);
            rain.set("uSheltering", outside ? 0f : 1f);
            rain.set("uSeenOn", seenOnFloor ? camera.forwardX : 0f, seenOnFloor ? camera.forwardY : 0f, seenOnFloor ? camera.forwardUp : 0f, floorUp);
            setBeams(rain, TORCH_UNIT);
            // A drop shows the sky it falls through, and a flash of lightning lights every drop in the air.
            rain.set("uTint", 1.6f * (float) Math.pow(state.horizonRed, 2.2) + RAIN_FLASHED * state.flashRed,
                    1.6f * (float) Math.pow(state.horizonGreen, 2.2) + RAIN_FLASHED * state.flashGreen,
                    1.6f * (float) Math.pow(state.horizonBlue, 2.2) + RAIN_FLASHED * state.flashBlue);
            drawBoxes(rain, boxesX, boxesY, aheadX, aheadY, reachAlong, reachAcross);
        }
        if (state.snow > 0f) {
            // A flake is white and hides what is behind it: laid over the picture, not added to it.
            GL14.glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
            snow.use();
            snow.set("uRoof", 0);
            snow.set("uRoofMatrix", roofMatrix);
            snow.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
            snow.set("uFall", state.carriedX, state.carriedY, state.snowClock);
            snow.set("uAir", state.airX, state.airY);
            snow.set("uSnow", state.snow * (overhead ? SNOW_SHARE_OVERHEAD : 1f));
            snow.set("uSeated", seated ? 1f : 0f);
            snow.set("uBox", rainBox[0], rainBox[1], rainBox[2], rainBox[3]);
            snow.set("uThin", SNOW_DOTS_LEAST * dot);
            snow.set("uLong", overhead ? 0f : SNOW_SEEN_FOR);
            snow.set("uMoves", movesX, movesY, movesUp);
            snow.set("uShown", SNOW_SHOWN);
            setShelter(snow, SHELTER_UNIT, lidded);
            snow.set("uSheltering", outside ? 0f : 1f);
            snow.set("uSeenOn", seenOnFloor ? camera.forwardX : 0f, seenOnFloor ? camera.forwardY : 0f, seenOnFloor ? camera.forwardUp : 0f, floorUp);
            setBeams(snow, TORCH_UNIT);
            // A flake is lit by the whole sky, and by the sun or the moon when there is one.
            snow.set("uTint", (float) Math.pow(0.5f * (state.horizonRed + state.zenithRed) + 0.35f * state.lightRed, 2.2),
                    (float) Math.pow(0.5f * (state.horizonGreen + state.zenithGreen) + 0.35f * state.lightGreen, 2.2),
                    (float) Math.pow(0.5f * (state.horizonBlue + state.zenithBlue) + 0.35f * state.lightBlue, 2.2));
            drawBoxes(snow, boxesX, boxesY, aheadX, aheadY, reachAlong, reachAcross);
        }
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisableClientState(GL_VERTEX_ARRAY);
    }

    /** How bright a speck of dust that fills a dot is under the sun, in a room and out of doors. */
    private static final float DUST_SHOWN_INSIDE = 6f, DUST_SHOWN_OUTSIDE = 1.8f;
    /** How many times the rain's vertices are drawn as dust, each a set of specks of its own. */
    private static final int DUST_LAYERS = 6;

    /** The dust in the air round the eye, into the picture after the steadying. */
    private void drawDust(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        GL11.glViewport(0, 0, width, height);
        GL11.glMatrixMode(GL_PROJECTION);
        ViewCamera.projection(tanHalfFieldOfView, aspect, near, far, projection);
        projection[8] += asideX;
        projection[9] += asideY;
        GL11.glLoadMatrixf(projection);
        GL11.glMatrixMode(GL_MODELVIEW);
        ViewCamera.view(camera, viewed);
        GL11.glLoadMatrixf(viewed);
        arrays();
        cardArrayOff();
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
        // Light added to what is there. The picture's alpha says what mirrors and is left alone.
        GL14.glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ZERO, GL_ONE);
        // The maps compare depths themselves: a skin's smoothing sampler on these units would not.
        GL33.glBindSampler(1, 0);
        GL33.glBindSampler(2, 0);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, farMap);
        GL13.glActiveTexture(GL_TEXTURE0);
        dust.use();
        dust.set("uShadowNear", 1);
        dust.set("uShadowFar", 2);
        dust.set("uNearMatrix", nearMatrix);
        dust.set("uFarMatrix", farMatrix);
        dust.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
        dust.set("uClock", state.seconds);
        dust.set("uPerPixel", 1.1f * tanHalfFieldOfView / height);
        dust.set("uLight", state.lightX, state.lightY, state.lightUp);
        dust.set("uGlow", (float) Math.pow(state.lightRed, 2.2), (float) Math.pow(state.lightGreen, 2.2), (float) Math.pow(state.lightBlue, 2.2));
        dust.set("uShown", DUST_SHOWN_INSIDE + (DUST_SHOWN_OUTSIDE - DUST_SHOWN_INSIDE) * state.outdoors);
        setRoofedAir(dust, 3);
        setBeams(dust, TORCH_UNIT);
        for (int layer = 0; layer < DUST_LAYERS; layer++) {
            dust.set("uLayer", (float) layer);
            GL11.glDrawArrays(GL_TRIANGLES, 0, RAIN_DROPS * 6);
        }
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisableClientState(GL_VERTEX_ARRAY);
    }

    /** No lamps for the people's light: a picture drawn with no vehicle's lamps in it. Leaves the first unit active. */
    private void noLamps() {
        GL13.glActiveTexture(GL_TEXTURE0 + LAMP_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, noneTexture);
        GL13.glActiveTexture(GL_TEXTURE0);
        figureLight.set("uLampsLit", LAMP_UNIT);
    }

    /** Notes how fast the eye has gone since the frame before, into movesX, movesY and movesUp. */
    private void eyeMoves(ViewCamera.Pose camera) {
        float seconds = (frameNanos - lastEyeNanos) / 1e9f;
        float dx = camera.eyeX - lastEyeX, dy = camera.eyeY - lastEyeY, du = camera.eyeUp - lastEyeUp;
        boolean jumped = lastEyeNanos == 0 || seconds <= 0f || seconds > 0.25f
                || dx * dx + dy * dy + du * du > EYE_MOVES_MOST * EYE_MOVES_MOST * seconds * seconds;
        lastEyeX = camera.eyeX;
        lastEyeY = camera.eyeY;
        lastEyeUp = camera.eyeUp;
        lastEyeNanos = frameNanos;
        if (jumped) {
            movesX = movesY = movesUp = 0f;
            return;
        }
        float keep = (float) Math.exp(-seconds / EYE_MOVES_SECONDS);
        movesX = movesX * keep + dx / seconds * (1f - keep);
        movesY = movesY * keep + dy / seconds * (1f - keep);
        movesUp = movesUp * keep + du / seconds * (1f - keep);
    }

    /**
     * The bolts of lightning in sight, over the sky and behind whatever of the world stands
     * before them: see Lightning. Each is its brightest where its ribbons cross, not their sum,
     * so a channel's stretches and its branches meet without bright knots where they overlap.
     */
    private void drawLightning(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, steadying ? movesTarget : sceneTarget);
        GL11.glViewport(0, 0, width, height);
        GL11.glMatrixMode(GL_PROJECTION);
        ViewCamera.projection(tanHalfFieldOfView, aspect, near, far, projection);
        projection[8] += asideX;
        projection[9] += asideY;
        GL11.glLoadMatrixf(projection);
        GL11.glMatrixMode(GL_MODELVIEW);
        ViewCamera.view(camera, viewed);
        GL11.glLoadMatrixf(viewed);
        arrays();
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL_NORMAL_ARRAY);
        GL13.glClientActiveTexture(GL_TEXTURE0 + 2);
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glEnable(GL_BLEND);
        GL14.glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ONE, GL_ONE);
        GL14.glBlendEquation(GL_MAX);
        bolt.use();
        atmosphere(bolt, state);
        bolt.set("uPixels", (float) width, (float) height);
        bolt.set("uCore", Lightning.CORE_WIDE, Lightning.CORE_DOTS);
        bolt.set("uHalo", Lightning.GLOW_WIDE, Lightning.GLOW_DOTS);
        bolt.set("uLights", Lightning.CORE_LIGHT, Lightning.GLOW_LIGHT);
        for (int i = 0; i < state.seenCount; i++) {
            Lightning.Seen seen = state.seen[i];
            Lightning.Bolt shape = seen.bolt();
            bolt.set("uGlow", seen.channel(), seen.branches(), seen.reached());
            GL11.glVertexPointer(Lightning.FLOATS, GL_FLOAT, 0, shape.ends);
            GL13.glClientActiveTexture(GL_TEXTURE0);
            GL11.glTexCoordPointer(Lightning.FLOATS, GL_FLOAT, 0, shape.others);
            GL13.glClientActiveTexture(GL_TEXTURE0 + 1);
            GL11.glTexCoordPointer(Lightning.FLOATS, GL_FLOAT, 0, shape.kinds);
            GL11.glDrawArrays(GL_QUADS, 0, shape.vertices);
        }
        GL14.glBlendEquation(GL_FUNC_ADD);
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL13.glClientActiveTexture(GL_TEXTURE0);
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL_VERTEX_ARRAY);
    }

    /**
     * Draws the drops once for each box of a block of them, the block's middle on the place the
     * boxes are counted from. The boxes lie along the map and the picture's ground lies along
     * the way the camera looks, so a box is left out when no part of it comes within the reach
     * of that ground, ahead and to the side.
     */
    private static void drawBoxes(Program falling, int boxesX, int boxesY, float aheadX, float aheadY, float reachAlong, float reachAcross) {
        float corner = RAIN_BOX * 0.7072f;
        for (int boxY = 0; boxY < boxesY; boxY++) {
            for (int boxX = 0; boxX < boxesX; boxX++) {
                float x = (boxX - (boxesX - 1) / 2f) * RAIN_BOX, y = (boxY - (boxesY - 1) / 2f) * RAIN_BOX;
                if (Math.abs(x * aheadX + y * aheadY) > reachAlong + corner || Math.abs(y * aheadX - x * aheadY) > reachAcross + corner) {
                    continue;
                }
                falling.set("uTile", x, y);
                GL11.glDrawArrays(GL_TRIANGLES, 0, RAIN_DROPS * 6);
            }
        }
    }

    /** Has the frame's block of sheltered squares on the graphics card, on a unit. The one small texture goes back with the shadow maps. */
    private void holdShelter(int unit) {
        GL13.glActiveTexture(GL_TEXTURE0 + unit);
        if (shelterTexture == 0) {
            shelterTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL_TEXTURE_2D, shelterTexture);
            GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, Shelter.SIDE, Shelter.SIDE, 0, GL_RED, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            filter(GL_NEAREST);
            shelterHeld = null;
        }
        GL11.glBindTexture(GL_TEXTURE_2D, shelterTexture);
        if (sheltered != null && sheltered != shelterHeld) {
            GL11.glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, Shelter.SIDE, Shelter.SIDE, GL_RED, GL_UNSIGNED_BYTE, sheltered.storeys);
            GL11.glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            shelterHeld = sheltered;
        }
    }

    private void setShelter(Program falling, int unit, boolean lidded) {
        falling.set("uShelter", unit);
        falling.set("uShelterAt", sheltered == null ? 0f : sheltered.x, sheltered == null ? 0f : sheltered.y, sheltered == null ? 0f : 1f / Shelter.SIDE,
                255f * ViewCamera.LEVEL_HEIGHT);
        falling.set("uLidBox", lidded ? cutBox[0] : 0f, lidded ? cutBox[1] : 0f, lidded ? cutBox[2] : 0f, lidded ? cutBox[3] : 0f);
    }

    /**
     * The matrix that looks along the light at a square of ground centred on the focus. The
     * centre moves a whole texel of the map at a time, or the shadows' edges would crawl as
     * the eye moves.
     */
    private void lightMatrix(float[] m, SkyState state, float extent, float range) {
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
        float texel = 2f * extent / shadowSize;
        float centreRight = (float) Math.floor((rx * focusX + ry * focusY) / texel) * texel;
        float centreUp = (float) Math.floor((ux * focusX + uy * focusY + uu * focusUp) / texel) * texel;
        float centreAlong = lx * focusX + ly * focusY + lu * focusUp;
        m[0] = rx / extent; m[4] = ry / extent; m[8] = 0f; m[12] = -centreRight / extent;
        m[1] = ux / extent; m[5] = uy / extent; m[9] = uu / extent; m[13] = -centreUp / extent;
        m[2] = -lx / range; m[6] = -ly / range; m[10] = -lu / range; m[14] = centreAlong / range;
        m[3] = 0f; m[7] = 0f; m[11] = 0f; m[15] = 1f;
    }

    private void drawWorld(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect,
            float near, float far, float fogStart, float fogEnd) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, inScope ? view.scope.pictureTarget : inMirror ? mirrorTarget : inWater ? waterTarget : sceneTarget);
        GL11.glViewport(0, 0, inScope ? Scope.PICTURE : inMirror ? MIRROR_WIDTH : inWater ? waterWidth : width, drawnHigh());
        // In the mirror, where nothing is drawn is marked, and the sky is put there when the
        // glass is drawn.
        // The pass leaves the arrays and the sprite sampler unbound when it ends, and it may
        // run three times in a frame: for the mirror, for a scope and for the eye.
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
        if (!inMirror && !inScope && !inWater) {
            if (steadying) {
                jitterAt = (jitterAt + 1) % JITTER_X.length;
                asideX = JITTER_X[jitterAt] / width;
                asideY = JITTER_Y[jitterAt] / height;
            } else {
                asideX = 0f;
                asideY = 0f;
            }
            projection[8] += asideX;
            projection[9] += asideY;
            view.characters.aside(asideX, asideY);
        }
        ChunkBuffers.Listing listed = inMirror ? shownBehind : seenBy(camera, state);
        // The picture of what water shows is mostly far off, where little hides anything else, and is drawn once.
        boolean depthFirst = !WITHOUT.contains("depth") && !inWater;
        // Read once for both drawings of the world: read for each, a mark the logic thread added between them would be in one and not the other.
        placeSnowPrints(camera);
        tellWorld(world, camera, state, tanHalfFieldOfView, aspect, fogStart, fogEnd);
        world.set("uLaid", 0f);
        // The solid world is drawn twice. The first time only its depth is kept, and the
        // program stops once it knows whether a pixel is left out. The second time a pixel is
        // worked out only where it is the nearest thing there: the program leaves pixels out,
        // so drawn once the graphics card worked every pixel of every surface out in full
        // before finding most of them hidden, which was three quarters of the pass's time.
        // The first is drawn by a program of its own, the same text told as it is compiled
        // that depth is all that is wanted, so the driver leaves out every sum that only
        // colours. A vertex's place is invariant, so the two agree to the bit on how deep a
        // pixel is. Which pixels are left out they agree on as far as one driver compiles the
        // same lines the same way twice: a pixel one kept and the other left out would be a
        // pinhole, and without=depth is the picture to hold a capture against.
        // The same in both drawings of the solid world, which must agree on which model of a piece is there.
        float pixel = 2f * tanHalfFieldOfView / drawnHigh();
        if (depthFirst) {
            tellWorld(worldDepth, camera, state, tanHalfFieldOfView, aspect, fogStart, fogEnd);
            worldDepth.set("uLaid", 0f);
            GL11.glColorMask(false, false, false, false);
            drawRanges(listed, 0, listed.solidRanges(), worldDepth);
            drawModels(worldDepth, true, false, camera, far, pixel, Outlines.NONE);
            GL11.glColorMask(true, true, true, true);
            world.use();
            GL11.glDepthFunc(GL_EQUAL);
            GL11.glDepthMask(false);
        }
        // The three parts of the view's own picture of the world are timed apart: what "world" then has left is the scope and what water shows.
        boolean own = !inMirror && !inScope && !inWater;
        if (own) {
            stage(STAGE_DEPTH);
        }
        drawRanges(listed, 0, listed.solidRanges(), world);
        drawModels(world, true, true, camera, far, pixel, Outlines.NONE);
        if (own) {
            stage(STAGE_SOLID);
        }
        if (depthFirst) {
            GL11.glDepthFunc(GL_LEQUAL);
            GL11.glDepthMask(true);
        }
        // What is laid over the floors, mixed in by how see-through each pixel is, and the grass
        // that stands on them.
        GL11.glEnable(GL_BLEND);
        // The picture's alpha is how much of a mirror a pixel is, 1 for none, and what is laid gives how much of the pixel it covers:
        // mixed in as the colour is, a pixel half covered would be left a quarter of a mirror.
        GL14.glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        world.set("uCut", 0.02f);
        world.set("uLaid", 1f);
        world.set("uTexel", 0f, 0f);
        // Grass is blade behind blade, and each pixel of each was lit in full to be drawn over
        // by the next. It is drawn by itself before the rest of what is laid: its depth first,
        // as the solid world's is, and then a pixel is lit only for the blade nearest the eye
        // there. The rest lies on the ground in layers that are mixed each over the last, and
        // is drawn once, in the order listed.
        if (depthFirst) {
            GL11.glDisable(GL_BLEND);
            worldDepth.use();
            worldDepth.set("uCut", 0.02f);
            worldDepth.set("uLaid", LAID_GRASS);
            worldDepth.set("uTexel", 0f, 0f);
            GL11.glColorMask(false, false, false, false);
            drawRanges(listed, listed.solidRanges(), listed.ranges());
            GL11.glColorMask(true, true, true, true);
            world.use();
            world.set("uLaid", LAID_GRASS);
            GL11.glEnable(GL_BLEND);
            GL11.glDepthFunc(GL_EQUAL);
            GL11.glDepthMask(false);
            drawRanges(listed, listed.solidRanges(), listed.ranges());
            GL11.glDepthFunc(GL_LEQUAL);
            GL11.glDepthMask(true);
            world.set("uLaid", LAID_REST);
        }
        drawRanges(listed, listed.solidRanges(), listed.ranges());
        drawBlood(listed);
        if (own) {
            stage(STAGE_LAID);
        }
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL_BLEND);
        GL11.glDepthMask(true);
        leaveWorld();
        if (beyond > 0f && !inMirror) {
            drawFar(camera, state, fogStart, fogEnd);
        }
    }

    /**
     * The game's blood, over everything else that is laid: hidden by what lies or grows over
     * it, and leaving the picture's depth alone, so each splat is mixed over the floor and over
     * every splat under it, as the game's own view mixes them. Written to the depth, one splat
     * hid another wherever two met and the blood came out a pale pink where the game's is dark.
     */
    private void drawBlood(ChunkBuffers.Listing listed) {
        GL11.glDepthMask(false);
        int texel = world.at("uTexel");
        for (int i = listed.solidRanges(); i < listed.ranges(); i++) {
            if (listed.tint(i) == WallSides.BLOOD) {
                GL11.glBindTexture(GL_TEXTURE_2D, listed.texture(i));
                tellTexel(texel, listed.texture(i));
                listed.draw(i);
            }
        }
        GL20.glUniform2f(texel, 0f, 0f);
        GL11.glDepthMask(true);
    }

    /**
     * The far land, wherever the picture's depth does not already have the built world before
     * it: see FarLand. Drawn after the built world, most of it is hidden and costs nothing.
     */
    private void drawFar(ViewCamera.Pose camera, SkyState state, float fogStart, float fogEnd) {
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadMatrixf(projection);
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glLoadMatrixf(viewed);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_BLEND);
        far.use();
        farUniforms(far, camera, state, fogStart, fogEnd);
        far.set("uPicture", 0);
        far.set("uPictureSide", (float) FarLand.PICTURE);
        far.set("uStorey", ViewCamera.LEVEL_HEIGHT);
        view.worldScene.farLand.draw(focusX, focusY);
        drawFarTrees(camera, state, fogStart, fogEnd);
    }

    /** What the far land's and the far trees' programs are both told: the eye, the light and the air (PipelineShaders.FAR_LIGHT), and where the built world thins out into them (ENDS). */
    private void farUniforms(Program program, ViewCamera.Pose camera, SkyState state, float fogStart, float fogEnd) {
        program.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
        program.set("uLight", state.lightX, state.lightY, state.lightUp);
        program.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
        program.set("uOpenLight", state.openRed, state.openGreen, state.openBlue);
        program.set("uDaylightKept", (float) Math.pow(state.daylightKept, 2.2));
        program.set("uCover", state.cover);
        program.set("uDrift", state.driftX, state.driftY);
        program.set("uSnowLying", state.snowLying);
        program.set("uReach", FarLand.REACH);
        program.set("uFogStart", fogStart);
        program.set("uFogEnd", fogEnd);
        program.set("uLoaded", loadedX - camera.eyeX, loadedY - camera.eyeY, loadedWithin);
        program.set("uBeyond", inScope || inWater ? Math.min(beyond, 1f) : beyond);
        atmosphere(program, state);
    }

    /** The far trees' mesh near the eye and further off (FarTrees.mesh), and how many vertices each has. */
    private static final int[] treeMeshes = new int[2], treeVertices = new int[2];
    /** Each kind of far tree's row (FarTreeLooks), the kinds it was written for and the frame it was. */
    private static int kindsTexture;
    private static FarTreeKinds kindsFor;
    private static int kindsWritten;
    private static ByteBuffer kindsBytes;
    /** How often the kinds' rows are written again, for the season and the kinds the built world has seen since: frames. */
    private static final int KINDS_EVERY = 120;
    private static final int GL_RGBA32F = 0x8814;

    /** The trees beyond the loaded map, after the far land, to Quality.farTreeDistance: see FarTrees. */
    private void drawFarTrees(ViewCamera.Pose camera, SkyState state, float fogStart, float fogEnd) {
        FarTreeKinds kinds = FarLand.treeKinds;
        int reach = Quality.farTreeDistance();
        if (kinds == null || reach <= 0) {
            return;
        }
        if (treeMeshes[0] == 0) {
            float[][] meshes = {FarTrees.mesh(5, 7, 4), FarTrees.mesh(2, 4, 0)};
            for (int lod = 0; lod < 2; lod++) {
                treeMeshes[lod] = GL15.glGenBuffers();
                treeVertices[lod] = meshes[lod].length / 3;
                GL15.glBindBuffer(GL_ARRAY_BUFFER, treeMeshes[lod]);
                GL15.glBufferData(GL_ARRAY_BUFFER, meshes[lod], GL_STATIC_DRAW);
            }
            GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
        int rows = kinds.species.size() * FarTreeKinds.SIZES;
        GL13.glActiveTexture(GL_TEXTURE0);
        if (kindsTexture == 0) {
            kindsTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL_TEXTURE_2D, kindsTexture);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        } else {
            GL11.glBindTexture(GL_TEXTURE_2D, kindsTexture);
        }
        if (kinds != kindsFor || frameNumber - kindsWritten >= KINDS_EVERY) {
            int bytes = rows * FarTreeLooks.ROW * 4 * Float.BYTES;
            if (kindsBytes == null || kindsBytes.capacity() < bytes) {
                kindsBytes = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
            }
            kindsBytes.clear();
            FarTreeLooks.write(kinds, kindsBytes);
            kindsBytes.flip();
            GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, FarTreeLooks.ROW, rows, 0, GL_RGBA, GL_FLOAT, kindsBytes);
            kindsFor = kinds;
            kindsWritten = frameNumber;
        }
        boolean culling = GL11.glIsEnabled(GL_CULL_FACE);
        GL11.glDisable(GL_CULL_FACE);
        farTree.use();
        farUniforms(farTree, camera, state, fogStart, fogEnd);
        farTree.set("uKinds", 0);
        farTree.set("uKindRows", (float) rows);
        farTree.set("uTreeReach", (float) reach);
        // Where the near trees are the game's sprites the far ones follow the game's schedule, and otherwise the mod's, in the steps its builds take.
        long now = Seasons.now();
        int season = Seasons.seasonOf(now);
        boolean games = !Own.plants();
        farTree.set("uSeason", (float) season, games ? Seasons.progressionOf(now) : Seasons.stepped(season, Seasons.progressionOf(now)));
        farTree.set("uGameSchedule", games ? 1f : 0f);
        view.worldScene.farLand.drawTrees(focusX, focusY, reach, farTree.attribute("aTree"), treeMeshes, treeVertices);
        GL11.glBindTexture(GL_TEXTURE_2D, 0);
        if (culling) {
            GL11.glEnable(GL_CULL_FACE);
        }
    }

    /**
     * The panes of the windows the picture builds, mixed in over everything seen through them:
     * the room or the street, the people in it, the sky and the rain, which is why they are
     * drawn after all of those. A pane hides nothing and so leaves the picture's depth alone.
     */
    private void drawPanes(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float fogStart, float fogEnd) {
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
        placeSnowPrints(camera);
        tellWorld(world, camera, state, tanHalfFieldOfView, aspect, fogStart, fogEnd);
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
        GL11.glDisable(GL_DEPTH_TEST);
        leaveWorld();
    }

    /** Where the viewer has trodden the snow and driven over it, counted from this picture's own eye, for tellWorld. */
    private void placeSnowPrints(ViewCamera.Pose camera) {
        SnowPrints.placed(view.worldScene.terrain, camera.eyeX, camera.eyeY, snowPrints, snowRuts, snowRutsUp, snowTrod);
        // The marks are the mod's own: the game's snow, which Low draws, keeps none.
        if (!Own.textures()) {
            snowTrod[0] = 0f;
            snowTrod[1] = 0f;
        }
    }

    /**
     * Makes one of the world's two programs the one in use and tells it the frame: the eye, the sun, the
     * shadow maps, the weather, and the snow's marks placeSnowPrints last placed. The projection is the one drawWorld
     * last made, so whatever is drawn later in the frame lies on what was drawn first to the fraction of a pixel.
     */
    private void tellWorld(Program to, ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float fogStart, float fogEnd) {
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadMatrixf(projection);
        GL11.glMatrixMode(GL_MODELVIEW);
        // Only the turn: the world's vertex program takes the eye's place off each vertex itself.
        ViewCamera.turned(camera, viewed);
        GL11.glLoadMatrixf(viewed);

        to.use();
        to.set("uTexture", 0);
        to.set("uShadowNear", 1);
        to.set("uShadowFar", 2);
        bindMaterials(to);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, farMap);
        GL13.glActiveTexture(GL_TEXTURE0);
        to.set("uNearMatrix", nearMatrix);
        to.set("uFarMatrix", farMatrix);
        to.set("uShadowTexel", 1f / shadowSize);
        GL13.glActiveTexture(GL_TEXTURE0 + UNITS - 2);
        GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
        GL33.glBindSampler(UNITS - 2, depthSampler);
        GL13.glActiveTexture(GL_TEXTURE0);
        to.set("uShadowDepth", UNITS - 2);
        GL13.glActiveTexture(GL_TEXTURE0 + UNITS - 1);
        GL11.glBindTexture(GL_TEXTURE_2D, roofMap);
        GL33.glBindSampler(UNITS - 1, depthSampler);
        GL13.glActiveTexture(GL_TEXTURE0);
        to.set("uHeights", UNITS - 1);
        to.set("uRoofMatrix", roofMatrix);
        to.set("uCover", state.cover);
        to.set("uDrift", state.driftX, state.driftY);
        // Texels of soft edge for each unit of the map's depth between caster and ground.
        to.set("uSoftPerDepth", 2f * NEAR_RANGE * SUN_WIDTH * shadowSize / (2f * NEAR_EXTENT));
        // A texel of a sun map is 2 * extent / size tiles across, and under a level sheet spans that times the
        // cosine of the sun's elevation in height.
        float across = (float) Math.hypot(state.lightX, state.lightY);
        to.set("uSunTexelUp", 2f * NEAR_EXTENT / shadowSize * across, 2f * FAR_EXTENT / shadowSize * across);
        to.set("uSlopeOffset", SLOPE_OFFSET);
        to.set("uRoofDeep", 2f * ROOF_RANGE);
        to.set("uRoofPitch", RoofFit.PITCH * ViewCamera.LEVEL_HEIGHT);
        to.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
        // How much of its distance from the eye a pixel of this picture takes in, up and down.
        to.set("uPixel", 2f * tanHalfFieldOfView / drawnHigh());
        to.set("uRangeFrom", rangeFrom);
        to.set("uCutAbove", cutAbove);
        to.set("uCutBox", cutBox[0], cutBox[1], cutBox[2], cutBox[3]);
        if (overhead && ViewCamera.project(camera, tanHalfFieldOfView, aspect, focusX, focusY, focusUp - HOLE_BELOW_EYE, holeAt)) {
            to.set("uHole", (holeAt[0] * 0.5f + 0.5f) * width, (holeAt[1] * 0.5f + 0.5f) * height, HOLE_OF_HEIGHT * height);
            float along = camera.forwardX * focusX + camera.forwardY * focusY + camera.forwardUp * focusUp;
            // The ground they stand on is not in the way.
            to.set("uHoleUp", focusUp - HOLE_FLOOR_BELOW_EYE);
            to.set("uHoleFrom", camera.forwardX, camera.forwardY, camera.forwardUp, along - HOLE_BEFORE);
        } else {
            to.set("uHole", 0f, 0f, 0f);
        }
        if (overhead && ViewCamera.project(camera, tanHalfFieldOfView, aspect, canopyX, canopyY, canopyUp, canopyAt)) {
            CanopyWindow.inPixels(height, tanHalfFieldOfView, away, canopySize);
            to.set("uCanopy", (canopyAt[0] * 0.5f + 0.5f) * width, (canopyAt[1] * 0.5f + 0.5f) * height, canopySize[0], canopySize[1]);
            to.set("uCanopyTexel", canopySize[3]);
            to.setEach3("uFaded", canopyFaded);
            to.set("uFadedCount", canopyFadedCount);
            if (canopyAimed && ViewCamera.project(camera, tanHalfFieldOfView, aspect, canopyAimX, canopyAimY, canopyUp, canopyAt)) {
                to.set("uCanopyAim", (canopyAt[0] * 0.5f + 0.5f) * width, (canopyAt[1] * 0.5f + 0.5f) * height, canopySize[2]);
            } else {
                to.set("uCanopyAim", 0f, 0f, 0f);
            }
        } else {
            to.set("uCanopy", 0f, 0f, 0f, 0f);
            to.set("uCanopyAim", 0f, 0f, 0f);
        }
        // Nearer the camera, along the ground. The overhead eye is to the south-east, where
        // that is a greater x and y together, and the lengths here are in those units.
        float flat = (float) Math.sqrt((camera.forwardX * camera.forwardX + camera.forwardY * camera.forwardY) / 2f);
        float nearerX = -camera.forwardX / flat, nearerY = -camera.forwardY / flat;
        to.set("uCutNear", nearerX, nearerY, nearerX * focusX + nearerY * focusY - CUT_BEHIND * cutBehind);
        to.set("uLid", lid);
        if (cutBelow > 0f) {
            to.setEach("uCutRows", cutRows);
        }
        to.set("uCutMarked", cutRowsX, cutRowsY, cutBelow);
        to.set("uLight", state.lightX, state.lightY, state.lightUp);
        setBeams(to, TORCH_UNIT_IN_WORLD);
        float[] carried = state.carried;
        // A carried light that throws a beam is the torch of setBeams: what is said here is one that throws none.
        to.set("uLantern", carried[CarriedLight.HELD], carried[CarriedLight.HELD + 1], carried[CarriedLight.HELD + 2],
                carried[CarriedLight.CONE] > 0f ? 0f : carried[CarriedLight.REACH]);
        to.set("uOpenLight", state.openRed, state.openGreen, state.openBlue);
        to.set("uDaylightKept", (float) Math.pow(state.daylightKept, 2.2));
        to.set("uRoomKept", (float) Math.pow(state.daylightKept * state.roomKept, 2.2));
        setFlash(to);
        to.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
        to.set("uFogStart", fogStart);
        to.set("uFogEnd", fogEnd);
        to.set("uLoaded", loadedX - camera.eyeX, loadedY - camera.eyeY, loadedWithin);
        // A scope's picture is not mixed with the one before it: there the built world is thinned out by the same pixels every frame.
        to.set("uBeyond", inMirror ? 0f : inScope || inWater ? Math.min(beyond, 1f) : beyond);
        // The picture drawn from under the water has nothing in it that lies under the water's plane, nor the water.
        if (inWater) {
            to.set("uUnder", water[0], water[1], water[2], water[3]);
        } else {
            to.set("uUnder", 0f, 0f, 0f, 0f);
        }
        // The eye's own picture shows it where it draws water. A mirror's and a scope's look
        // from somewhere else, and their water shows the sky alone.
        boolean shown = waterDrawn && !inWater && !inMirror && !inScope;
        GL13.glActiveTexture(GL_TEXTURE0 + WATER_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, shown ? waterColor : whiteTexture);
        GL13.glActiveTexture(GL_TEXTURE0);
        to.set("uWaterPicture", WATER_UNIT);
        if (shown) {
            to.set("uWater", water[0], water[1], water[2], WaterMirror.above(water, camera.eyeX, camera.eyeY, camera.eyeUp));
        } else {
            to.set("uWater", 0f, 0f, 0f, 0f);
        }
        to.set("uWaterShare", 1f / WaterMirror.MARGIN);
        to.set("uWaterRect", waterRect[0], waterRect[1], waterRect[2], waterRect[3]);
        to.set("uWaterDensity", (float) waterHeight / height);
        to.set("uWaterThrow", WaterMirror.THROW);
        to.set("uRuffle", WaterMirror.ruffleDrawn((float) Math.hypot(state.windX, state.windY)));
        to.set("uPlainWater", Quality.plain() ? 1f : 0f);
        to.set("uWind", state.windX, state.windY, state.seconds);
        to.set("uGust", state.gustX, state.gustY, state.flutter);
        to.set("uFaced", state.facedX, state.facedY);
        to.set("uLayerFrom", state.layersFromX, state.layersFromY, state.layersFromUp);
        to.set("uLayer", WorldScene.STAND_OFF, WorldScene.STAND_OFF_PER_TILE_SQUARED, WorldScene.CARD_LAYER);
        to.set("uAcross", state.acrossX, state.acrossY);
        to.set("uWet", state.wet);
        to.set("uSeconds", state.seconds);
        to.set("uRaining", state.rain);
        to.set("uSnowLying", state.snowLying);
        to.set("uPlantSnow", state.snowOnPlants());
        for (int i = 0; i < snowTrod[0]; i++) {
            to.set(PRINT_AT[i], snowPrints[i * 4], snowPrints[i * 4 + 1], snowPrints[i * 4 + 2], snowPrints[i * 4 + 3]);
        }
        for (int i = 0; i < snowTrod[1]; i++) {
            to.set(RUT_AT[i], snowRuts[i * 4], snowRuts[i * 4 + 1], snowRuts[i * 4 + 2], snowRuts[i * 4 + 3]);
            to.set(RUT_UP_AT[i], snowRutsUp[i]);
        }
        to.set("uSnowTrod", snowTrod[0], snowTrod[1]);
        to.set("uSnowTrodBox", snowTrod[2], snowTrod[3], snowTrod[4], snowTrod[5]);
        to.set("uDark", state.night);
        atmosphere(to, state);
        to.set("uCut", SOLID_CUT);
    }

    /** Leaves the arrays and the samplers of the world's program unbound, as every pass that uses it must. */
    private static void leaveWorld() {
        GL33.glBindSampler(0, 0);
        GL33.glBindSampler(UNITS - 2, 0);
        GL33.glBindSampler(UNITS - 1, 0);

        GL11.glDisableClientState(GL_VERTEX_ARRAY);
        cardArrayOff();
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL_NORMAL_ARRAY);
    }

    private void bindMaterials(Program to) {
        for (int material = 0; material < MATERIALS.length; material++) {
            GL13.glActiveTexture(GL_TEXTURE0 + FIRST_MATERIAL_UNIT + material);
            // Until the mod's own pictures are made a unit holds plain white, which leaves whatever reads it as it was.
            GL11.glBindTexture(material == BARK ? Pictures.GL_TEXTURE_2D_ARRAY : GL_TEXTURE_2D,
                    material == BARK || materialTexture[material] != 0 ? materialTexture[material] : whiteTexture);
        }
        to.set("uAsphalt", FIRST_MATERIAL_UNIT);
        to.set("uGravel", FIRST_MATERIAL_UNIT + 1);
        to.set("uSoil", FIRST_MATERIAL_UNIT + 2);
        to.set("uLawn", FIRST_MATERIAL_UNIT + 3);
        to.set("uMaterialGain", materialGain[0], materialGain[1], materialGain[2], materialGain[3]);
        to.set("uShingles", FIRST_MATERIAL_UNIT + 4);
        to.set("uPaint", FIRST_MATERIAL_UNIT + 5);
        to.set("uWood", FIRST_MATERIAL_UNIT + 6);
        to.set("uSurfaceGain", materialGain[4], materialGain[5], materialGain[6]);
        to.set("uBark", FIRST_MATERIAL_UNIT + 7);
        to.set("uPlanks", FIRST_MATERIAL_UNIT + 8);
        to.set("uCarpet", FIRST_MATERIAL_UNIT + 9);
        to.set("uTimberGain", materialGain[7], materialGain[8], materialGain[9]);
        to.set("uLeaves", FIRST_MATERIAL_UNIT + 10);
        to.set("uLeafGain", materialGain[10]);
        to.set("uCloth", FIRST_MATERIAL_UNIT + 12);
        to.set("uClothGain", materialGain[12]);
        for (int nature = 0; nature < NATURE.length; nature++) {
            GL13.glActiveTexture(GL_TEXTURE0 + FIRST_NATURE_UNIT + nature);
            GL11.glBindTexture(GL_TEXTURE_2D, natureTexture[nature] != 0 ? natureTexture[nature] : whiteTexture);
        }
        to.set("uLitter", FIRST_NATURE_UNIT);
        to.set("uRock", FIRST_NATURE_UNIT + 1);
        to.set("uFrond", FIRST_NATURE_UNIT + 2);
        to.set("uIvy", FIRST_NATURE_UNIT + 3);
        to.set("uNatureGain", natureGain[0], natureGain[1]);
        GL13.glActiveTexture(GL_TEXTURE0 + SPRIG_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, sprigs != 0 ? sprigs : whiteTexture);
        to.set("uSprigs", SPRIG_UNIT);
        GL13.glActiveTexture(GL_TEXTURE0 + SPRIG_FORM_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, sprigForm != 0 ? sprigForm : whiteTexture);
        to.set("uSprigForm", SPRIG_FORM_UNIT);
        to.set("uRelief", WITHOUT.contains("relief") || !Own.textures() || !Quality.relief() || inMirror || inWater ? 0f : 1f);
        to.set("uMirrors", surfacesMirror && !inWater ? 1f : 0f);
        to.set("uOwnTextures", Own.textures() ? 1f : 0f);
    }

    private static void setBasis(Program program, ViewCamera.Pose pose) {
        program.set("uRight", pose.rightX, pose.rightY, pose.rightUp);
        program.set("uUp", pose.upX, pose.upY, pose.upUp);
        program.set("uForward", pose.forwardX, pose.forwardY, pose.forwardUp);
    }

    private void atmosphere(Program program, SkyState state) {
        // From far overhead the fog is the develop pass's sheet, as the game has it over its own view. On the
        // flight between the two the air thins away as the sheet comes in, so neither leaves the picture clear.
        float sheet = overhead ? Math.clamp((away - WorldScene.OVERHEAD_RANGE) / (2f * WorldScene.OVERHEAD_RANGE), 0f, 1f) : 0f;
        program.set("uVeil", state.thick * (1f - sheet), Eyesight.SKY_THROUGH, Eyesight.FOG_CLEAR_NEAR);
        program.set("uVeilLight", (float) Math.pow(state.openRed * state.daylightKept, 2.2), (float) Math.pow(state.openGreen * state.daylightKept, 2.2),
                (float) Math.pow(state.openBlue * state.daylightKept, 2.2));
        program.set("uSun", state.sunX, state.sunY, state.sunUp);
        program.set("uSunColor", state.sunRed, state.sunGreen, state.sunBlue);
        program.set("uZenith", state.zenithRed, state.zenithGreen, state.zenithBlue);
        program.set("uHorizon", state.horizonRed, state.horizonGreen, state.horizonBlue);
        program.set("uGlow", state.glowRed, state.glowGreen, state.glowBlue);
        program.set("uFlashSky", state.flashRed, state.flashGreen, state.flashBlue);
        program.set("uFlashWay", state.burstX, state.burstY, state.burstUp);
    }

    private void drawSky(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        drawSkyInto(sceneTarget, width, height, true, camera, state, tanHalfFieldOfView, aspect, near, far);
    }

    /**
     * The sky, behind whatever a picture's depth says is drawn in it already.
     *
     * @param soft whether the clouds' edges are softened first, against the depth of the eye's own picture
     */
    private void drawSkyInto(int target, int wide, int high, boolean soft, ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView,
            float aspect, float near, float far) {
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
        clouds.set("uDeck", state.deck);
        clouds.set("uDrift", state.driftX, state.driftY);
        clouds.set("uBurstWay", state.burstX, state.burstY, state.burstUp);
        clouds.set("uBurstLight", Lightning.RED * state.burst, Lightning.GREEN * state.burst, Lightning.BLUE * state.burst);
        quad();
        if (soft) {
            softenInto(softCloudTarget, cloudColor, near, far);
            stage(STAGE_CLOUDS);
        }

        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL11.glViewport(0, 0, wide, high);
        GL11.glEnable(GL_DEPTH_TEST);
        // Said again here: the game's own draw of the characters leaves its depth test behind.
        GL11.glDepthFunc(GL_LEQUAL);
        sky.use();
        GL11.glBindTexture(GL_TEXTURE_2D, soft ? softCloudColor : cloudColor);
        sky.set("uClouds", 0);
        setBasis(sky, camera);
        sky.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        sky.set("uSize", (float) wide, (float) high);
        sky.set("uMoon", state.moonX, state.moonY, state.moonUp);
        sky.set("uNight", state.night);
        sky.set("uTime", state.seconds);
        atmosphere(sky, state);
        quad();
        GL11.glDisable(GL_DEPTH_TEST);
    }

    private static final int GL_TIME_ELAPSED = 0x88BF, GL_QUERY_RESULT = 0x8866;
    /** The timer queries of a frame, in the order its stages ended, and the stage each was for. */
    private final int[] stageQuery = new int[32], stageOf = new int[32];
    private int stageQueries;
    private boolean stageOpen;

    /**
     * With -Dcameraunlock.zomboid.gpuTimes=true, reads how long the graphics card took over
     * each stage of the frame before, and begins timing this one. The card is asked for its
     * own time, by timer queries: waiting for it to finish after each stage charged a stage
     * with the time the driver took to be handed it as well, and held the whole frame up.
     */
    private void beginStages() {
        if (stageOpen) {
            GL15.glEndQuery(GL_TIME_ELAPSED);
            stageOpen = false;
        }
        for (int i = 0; i < stageQueries; i++) {
            stageNanos[stageOf[i]] += GL15.glGetQueryObjecti(stageQuery[i], GL_QUERY_RESULT);
        }
        stageQueries = 0;
        openStage();
    }

    private void openStage() {
        if (stageQueries == stageQuery.length) {
            return;
        }
        if (stageQuery[stageQueries] == 0) {
            stageQuery[stageQueries] = GL15.glGenQueries();
        }
        GL15.glBeginQuery(GL_TIME_ELAPSED, stageQuery[stageQueries]);
        stageOpen = true;
    }

    /** Charges what the graphics card has done since the last stage ended to this one. */
    private void stage(int stage) {
        if (!TIMES || !stageOpen) {
            return;
        }
        GL15.glEndQuery(GL_TIME_ELAPSED);
        stageOf[stageQueries++] = stage;
        stageOpen = false;
        openStage();
    }

    private void reportStages() {
        if (TIMES && stageOpen) {
            // What comes after the last stage is charged to none. Left open, it would still be open as another view's frame began its own.
            GL15.glEndQuery(GL_TIME_ELAPSED);
            stageOpen = false;
        }
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

    private void drawOcclusion(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        lend(shaded);
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, occlusionTarget);
        GL11.glViewport(0, 0, levelWidth[0], levelHeight[0]);
        occlusion.use();
        occlusion.set("uFull", (float) width, (float) height);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, frameColor);
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
        GL11.glBindTexture(GL_TEXTURE_2D, frameColor);
        GL13.glActiveTexture(GL_TEXTURE0);
    }

    /** The map from above for PipelineShaders.ROOFED_AIR, on a unit, read as heights. */
    private void setRoofedAir(Program program, int unit) {
        GL13.glActiveTexture(GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL_TEXTURE_2D, roofMap);
        GL33.glBindSampler(unit, depthSampler);
        GL13.glActiveTexture(GL_TEXTURE0);
        program.set("uHeights", unit);
        program.set("uRoofMatrix", roofMatrix);
        program.set("uRoofDeep", 2f * ROOF_RANGE);
    }

    /** How much more a room's air holds for light to catch than the open air on a clear day: see PipelineShaders.VOLUME. */
    private static final float ROOM_AIR = 1f;
    private static final int GL_RG32F = 0x8230;

    /** The frame's RoomAir on the graphics card, on a unit, for PipelineShaders.ROOM_AIR. The one small texture goes back with the shadow maps. */
    private void setRoomAir(Program program, int unit) {
        GL13.glActiveTexture(GL_TEXTURE0 + unit);
        if (roomAirTexture == 0) {
            roomAirTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL_TEXTURE_2D, roomAirTexture);
            GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RG32F, RoomAir.SIDE, RoomAir.SIDE, 0, GL_RG, GL_FLOAT, (ByteBuffer) null);
            filter(GL_NEAREST);
            roomAirHeld = null;
        }
        GL11.glBindTexture(GL_TEXTURE_2D, roomAirTexture);
        if (roomAir != null && roomAir != roomAirHeld) {
            GL11.glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, RoomAir.SIDE, RoomAir.SIDE, GL_RG, GL_FLOAT, roomAir.spans);
            roomAirHeld = roomAir;
        }
        GL33.glBindSampler(unit, 0);
        GL13.glActiveTexture(GL_TEXTURE0);
        program.set("uRoomAirMap", unit);
        program.set("uRoomAirAt", roomAir == null ? 0f : roomAir.x, roomAir == null ? 0f : roomAir.y, roomAir == null ? 0f : 1f / RoomAir.SIDE);
    }

    private void drawVolume(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        lend(shaded);
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
        setBeams(volume, TORCH_UNIT);
        volume.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
        volume.set("uMist", state.mist);
        volume.set("uThick", state.thick);
        setRoofedAir(volume, 3);
        setRoomAir(volume, 4);
        volume.set("uRoomAir", ROOM_AIR);
        quad();
        softenInto(softVolumeTarget, volumeColor, near, far);
    }

    /**
     * The soften pass weighs each pixel by how near its distance is to the middle one's, so it
     * is given the scene's depth and the planes it was drawn between every time: what an
     * earlier pass left in its uniforms is another texture and, on a session's first frame, nought.
     */
    private void softenInto(int target, int source, float near, float far) {
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
    private void drawBloom(float exposure) {
        bloomDown.use();
        bloomDown.set("uSource", 0);
        for (int level = 0; level < BLOOM_LEVELS; level++) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, downTarget[level]);
            GL11.glViewport(0, 0, levelWidth[level], levelHeight[level]);
            GL11.glBindTexture(GL_TEXTURE_2D, level == 0 ? frameColor : downColor[level - 1]);
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

    /** Whether this frame's picture has anything outlined in outlineColor. */
    private boolean outlinesDrawn;

    /**
     * Draws each thing the picture outlines (Outlines) into outlineColor, in the colour of its
     * line: its triangles and its pieces again, by the program that drew the world's depth, over
     * the depth the frame has by now. The same program and the same vertices come to the same
     * depth to the bit, so only what of the thing is the nearest thing there is drawn, and a
     * person or anything else in front of it hides it as it hides the thing.
     */
    private void drawOutlined(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far,
            float fogStart, float fogEnd) {
        Outlines.Frame outlined = state.outlined;
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, outlineTarget);
        GL11.glViewport(0, 0, width, height);
        GL11.glColorMask(true, true, true, true);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL_COLOR_BUFFER_BIT);
        // As the world was drawn, a fraction of a pixel to one side: see drawWorld.
        ViewCamera.projection(tanHalfFieldOfView, aspect, near, far, projection);
        projection[8] += asideX;
        projection[9] += asideY;
        arrays();
        GL33.glBindSampler(0, spriteSampler);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glDisable(GL_BLEND);
        tellWorld(worldDepth, camera, state, tanHalfFieldOfView, aspect, fogStart, fogEnd);
        int outline = worldDepth.at("uOutline"), texel = worldDepth.at("uTexel"), painted = worldDepth.at("uTint"), joints = worldDepth.at("uJoint");
        float pixel = 2f * tanHalfFieldOfView / height;
        long[] keys = outlined.keys();
        float[] colours = outlined.colours();
        for (int k = 0; k < keys.length; k++) {
            GL20.glUniform4f(outline, colours[k * 3], colours[k * 3 + 1], colours[k * 3 + 2], 1f);
            for (int chunk = 0; chunk < drawn.size(); chunk++) {
                WorldScene.ChunkMeshes build = drawn.get(chunk);
                for (int i = 0; i < build.owned; i++) {
                    int run = build.ownedRun[i];
                    if (build.ownedKey[i] != keys[k] || !ChunkBuffers.listed(build.runRoom[run], roomsSeen)) {
                        continue;
                    }
                    boolean laid = run >= build.solidRuns;
                    worldDepth.set("uLaid", laid ? 1f : 0f);
                    worldDepth.set("uCut", laid ? 0.02f : SOLID_CUT);
                    // Window panes are drawn white and untinted: see drawPanes.
                    int key = build.runTint[run];
                    boolean pane = key == WallSides.PANES;
                    int texture = pane || build.runTexture[run] == 0 ? whiteTexture : ChunkBuffers.bound(build.runTexture[run]);
                    GL11.glBindTexture(GL_TEXTURE_2D, texture);
                    tellTexel(texel, texture);
                    // Blood is painted in no colour of a wall's.
                    if (pane || key == WallSides.BLOOD) {
                        GL20.glUniform4f(painted, 0f, 0f, 0f, 0f);
                    } else {
                        WallSides.joint(key, tint);
                        GL20.glUniform3f(joints, tint[0], tint[1], tint[2]);
                        WallSides.tint(key, tint);
                        GL20.glUniform4f(painted, tint[0], tint[1], tint[2], tint[3]);
                    }
                    GL11.glDrawArrays(GL_TRIANGLES, (build.heldAt + build.ownedFrom[i]) / SpriteGeometry.VERTEX_FLOATS,
                            build.ownedFloats[i] / SpriteGeometry.VERTEX_FLOATS);
                }
            }
            GL20.glUniform4f(painted, 0f, 0f, 0f, 0f);
            worldDepth.set("uLaid", 0f);
            worldDepth.set("uCut", SOLID_CUT);
            drawModels(worldDepth, true, false, camera, far, pixel, keys[k]);
        }
        GL20.glUniform4f(outline, 0f, 0f, 0f, 0f);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_DEPTH_TEST);
        leaveWorld();
    }

    private void drawDeveloped(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float exposure,
            float near, float far) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, shownTarget);
        GL11.glViewport(0, 0, width, height);
        develop.use();
        develop.set("uScene", 0);
        develop.set("uBloom", 1);
        develop.set("uDepth", 2);
        develop.set("uOcclusion", 3);
        develop.set("uWithout", WITHOUT.contains("occlusion") || !shadedAndLit ? 1f : 0f,
                WITHOUT.contains("air") || overhead || !shadedAndLit ? 1f : 0f, WITHOUT.contains("glow") ? 1f : 0f);
        develop.set("uVolume", 4);
        GL13.glActiveTexture(GL_TEXTURE0 + 4);
        GL11.glBindTexture(GL_TEXTURE_2D, shaded.made() ? softVolumeColor : noneTexture);
        GL13.glActiveTexture(GL_TEXTURE0 + 3);
        GL11.glBindTexture(GL_TEXTURE_2D, shaded.made() ? softColor : noneTexture);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneDepth);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, upColor[0]);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, frameColor);
        develop.set("uSize", (float) width, (float) height);
        develop.set("uLow", (float) levelWidth[0], (float) levelHeight[0]);
        develop.set("uExposure", exposure);
        Style style = Style.current();
        develop.set("uGrade", style.curve, style.saturation, style.vignette);
        develop.set("uLens", style.bloom, style.flare);
        develop.set("uShade", style.shade[0], style.shade[1], style.shade[2]);
        develop.set("uSunlit", style.sunlit[0], style.sunlit[1], style.sunlit[2]);
        develop.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        develop.set("uNear", near);
        develop.set("uFar", far);
        // The way up in the world, as the eye sees it: across, up and ahead.
        develop.set("uUp", camera.rightUp, camera.upUp, camera.forwardUp);
        develop.set("uAspect", aspect);
        develop.set("uTime", state.seconds);
        atmosphere(develop, state);
        develop.set("uRangeFrom", rangeFrom);
        develop.set("uAcross", camera.rightX, camera.rightY, camera.rightUp);
        develop.set("uUpward", camera.upX, camera.upY, camera.upUp);
        develop.set("uAhead", camera.forwardX, camera.forwardY, camera.forwardUp);
        develop.set("uOutlines", DEVELOP_OUTLINES_UNIT);
        develop.set("uOutlined", outlinesDrawn ? 1f : 0f);
        GL13.glActiveTexture(GL_TEXTURE0 + DEVELOP_OUTLINES_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, outlinesDrawn ? outlineColor : noneTexture);
        GL13.glActiveTexture(GL_TEXTURE0);
        // Seen from far overhead the fog is a sheet over the ground: see the develop shader. It
        // thins away as the camera comes down to the eye, where the lit air is the fog, and is
        // gone before the camera is near enough to stop being overhead or looks level enough
        // for its lines of sight to miss the sheet.
        float fog = 0f;
        if (overhead && sheltered != Shelter.EVERYWHERE) {
            fog = state.fog * cutBehind * Math.clamp((-camera.forwardUp - SEEN_ON_FLOOR_FROM) / SEEN_ON_FLOOR_FROM, 0f, 1f)
                    * Math.clamp((away - WorldScene.OVERHEAD_RANGE) / (2f * WorldScene.OVERHEAD_RANGE), 0f, 1f);
        }
        develop.set("uFog", fog, state.gustX, state.gustY);
        // The character's eyes are the eye's picture's alone: the view from far overhead is left as it was.
        if (overhead) {
            develop.set("uNarrowed", 0f);
        } else {
            develop.set("uNarrowed", state.narrowed);
        }
        if (fog > 0f) {
            holdShelter(DEVELOP_SHELTER_UNIT);
            GL13.glActiveTexture(GL_TEXTURE0);
            setShelter(develop, DEVELOP_SHELTER_UNIT, lid < Float.MAX_VALUE / 2f);
            develop.set("uFogFrom", camera.eyeX + camera.forwardX * away - focusX, camera.eyeY + camera.forwardY * away - focusY,
                    camera.eyeUp + camera.forwardUp * away - floorUp);
            develop.set("uFogViewer", focusX, focusY, floorUp);
            develop.set("uFogSpan", tanHalfFieldOfView * aspect * away, tanHalfFieldOfView * away);
            // Fog is the colour of the sky it hangs under, with most of the blue out of it.
            float red = (float) Math.pow(0.65f * state.horizonRed + 0.35f * state.zenithRed, 2.2);
            float green = (float) Math.pow(0.65f * state.horizonGreen + 0.35f * state.zenithGreen, 2.2);
            float blue = (float) Math.pow(0.65f * state.horizonBlue + 0.35f * state.zenithBlue, 2.2);
            float grey = 0.3f * red + 0.59f * green + 0.11f * blue;
            develop.set("uFogColor", FOG_BRIGHT * (grey + 0.3f * (red - grey)), FOG_BRIGHT * (grey + 0.3f * (green - grey)),
                    FOG_BRIGHT * (grey + 0.3f * (blue - grey)));
        }
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

    private void drawFigures(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect,
            float near, float far, float fogStart, float fogEnd) {
        // Flesh and cloth first, then painted metal, which is lit as lacquer.
        for (int pass = 0; pass < 2; pass++) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, figureTarget);
            GL11.glViewport(0, 0, width, height);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL20.glDrawBuffers(FIGURE_BUFFERS);
            GL11.glClear(GL_COLOR_BUFFER_BIT);
            GL20.glDrawBuffers(FIRST_BUFFER);
            GL11.glEnable(GL_DEPTH_TEST);
            GL11.glDepthFunc(GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glDisable(GL_BLEND);
            smoothSkins(true);
            CharacterDraw.bare = true;
            boolean lamps = VehicleLamps.drawn();
            if (!lamps && !lampsSaid) {
                lampsSaid = true;
                Log.info("vehicle lamps are drawn as paint: " + (Agent.lampsGap() != null ? Agent.lampsGap() : "a vehicle shader of the game's was not as expected"));
            }
            view.characters.lamps = lamps ? LAMP_BUFFERS : null;
            int drawn = view.characters.draw(camera, tanHalfFieldOfView, aspect, near, far, pass == 1, false);
            view.characters.lamps = null;
            CharacterDraw.bare = false;
            smoothSkins(false);
            // The game's draw leaves its own program, buffers and arrays bound.
            resumeFlat();

            GL30.glBindFramebuffer(GL_FRAMEBUFFER, steadying ? movesTarget : sceneTarget);
            figureLight.use();
            figureLight.set("uMoves", pass == 0 ? 1f : 0.5f);
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
            setBeams(figureLight, TORCH_UNIT);
            figureLight.set("uShines", shadedAndLit ? 1f : 0f);
            setFlash(figureLight);
            figureLight.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
            figureLight.set("uFogStart", fogStart);
            figureLight.set("uFogEnd", fogEnd);
            figureLight.set("uLoaded", loadedX - camera.eyeX, loadedY - camera.eyeY, loadedWithin);
            figureLight.set("uBeyond", beyond);
            figureLight.set("uWriteNear", 0f);
            // Only the viewer's own vehicle is cut away from inside, not who stands beside it.
            figureLight.set("uInside", 0f);
            figureLight.set("uOwnAmbient", 1f, 1f, 1f);
            figureLight.set("uFigureAmbient", view.characters.ambient);
            GL13.glActiveTexture(GL_TEXTURE0 + NORMAL_UNIT);
            GL11.glBindTexture(GL_TEXTURE_2D, figureNormal);
            GL13.glActiveTexture(GL_TEXTURE0 + LAMP_UNIT);
            GL11.glBindTexture(GL_TEXTURE_2D, figureLamps);
            GL13.glActiveTexture(GL_TEXTURE0);
            figureLight.set("uLampsLit", LAMP_UNIT);
            figureLight.set("uFacing", NORMAL_UNIT);
            figureLight.set("uHaveFacing", pass == 0 ? 1f : 0f);
            figureLight.set("uJersey", FIRST_MATERIAL_UNIT + 13);
            figureLight.set("uVinyl", FIRST_MATERIAL_UNIT + 11);
            figureLight.set("uWearGain", materialGain[13], materialGain[11]);
            figureLight.set("uWornGrain", Own.textures() ? 1f : 0f);
            figureLight.set("uRoots", pass == 0 ? (float) view.characters.rootsFound : 0f);
            for (int root = 0; root < view.characters.rootsFound && pass == 0; root++) {
                figureLight.set(ROOT_AT[root], view.characters.roots[root * 4], view.characters.roots[root * 4 + 1], view.characters.roots[root * 4 + 2], view.characters.roots[root * 4 + 3]);
            }
            atmosphere(figureLight, state);
            figureLight.set("uPainted", pass == 1 ? 1f : 0f);
            // With nothing drawn there is nothing to light. What the program was told stays told: the viewer's own body is lit by it next.
            if (drawn > 0) {
                quad();
            }
        }

        if (seated) {
            lend(cabin);
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, repaintedTarget);
            GL11.glDepthMask(true);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL_DEPTH_TEST);
            GL11.glDepthFunc(GL_LEQUAL);
            GL11.glDisable(GL_BLEND);
            view.characters.repainted = true;
            smoothSkins(true);
            view.characters.drawViewer(camera, tanHalfFieldOfView, aspect, OWN_NEAR, far);
            smoothSkins(false);
            view.characters.repainted = false;
        }
        // The viewer's own body, the same way: drawn by the game, lit here.
        clearOwnTarget();
        smoothSkins(seated);
        // What of the body the picture draws itself is drawn unlit, as everyone else is.
        CharacterDraw.bare = !seated;
        boolean own = !outside && view.characters.drawViewer(camera, tanHalfFieldOfView, aspect, OWN_NEAR, far);
        CharacterDraw.bare = false;
        smoothSkins(false);
        FloatBuffer glass = own && seated ? view.characters.cabin.panes : null;
        if (glass != null) {
            drawPaneMask(glass);
        }
        VehicleMeshes.Parts parts = own && seated ? view.characters.cabin.parts : null;
        if (parts != null) {
            drawPartKinds(parts);
        }
        // On foot the cabin's pictures are not made: nothing is drawn in each, at any depth.
        GL13.glActiveTexture(GL_TEXTURE0 + PARTS_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, cabin.made() ? partsColor : noneTexture);
        GL13.glActiveTexture(GL_TEXTURE0 + PARTS_DEPTH_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, cabin.made() ? partsDepth : whiteTexture);
        GL13.glActiveTexture(GL_TEXTURE0 + WOOD_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, materialTexture[WOOD]);
        // The carpet's own unit is among those the game's vehicle draw may have bound over.
        GL13.glActiveTexture(GL_TEXTURE0 + FIRST_MATERIAL_UNIT + CARPET);
        GL11.glBindTexture(GL_TEXTURE_2D, materialTexture[CARPET]);
        GL13.glActiveTexture(GL_TEXTURE0 + PANE_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, cabin.made() ? paneColor : noneTexture);
        GL13.glActiveTexture(GL_TEXTURE0);
        figureLight.use();
        noLamps();
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
            // The viewer's own body before a person is not that person: what it covers goes as the body does.
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, steadying ? movesTarget : sceneTarget);
            figureLight.use();
            figureLight.set("uMoves", 0f);
            GL13.glActiveTexture(GL_TEXTURE0 + 4);
            GL11.glBindTexture(GL_TEXTURE_2D, cabin.made() ? repaintedColor : noneTexture);
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
            noLamps();
            figureLight.set("uFacing", NORMAL_UNIT);
            figureLight.set("uHaveFacing", seated ? 0f : 1f);
            if (!seated) {
                figureLight.set("uFigureAmbient", view.characters.ownLight);
            }
            figureLight.set("uVinyl", FIRST_MATERIAL_UNIT + 11);
            figureLight.set("uCloth", FIRST_MATERIAL_UNIT + 12);
            figureLight.set("uCabinGain", materialGain[11], materialGain[12]);
            figureLight.set("uOwnAmbient", view.characters.ownAmbient[0], view.characters.ownAmbient[1], view.characters.ownAmbient[2]);
            GL11.glEnable(GL_DEPTH_TEST);
            GL11.glDepthFunc(GL_ALWAYS);
            GL11.glDepthMask(true);
            figureLight.set("uGlassPass", 0f);
            figureLight.set("uInstruments", view.ride.instrumentsLit ? 0.9f * state.night : 0f);
            float[] wheel = seated ? Cabins.wheel(view.ride) : null;
            if (wheel == null) {
                figureLight.set("uWheel", 0f, 0f, 0f, 0f);
                figureLight.set("uSeat", 0f, 0f, 0f);
            } else {
                setCabin(camera, wheel, parts);
            }
            figureLight.set("uRaining", state.rain);
            figureLight.set("uSnowing", state.snow);
            figureLight.set("uGlassRain", (float) Quality.glassRain());
            view.glassPull.frame(seated, state.seconds, view.ride.goingX, view.ride.goingY, view.ride.aheadX, view.ride.aheadY);
            if (FirstPerson.TRACE && seated) {
                view.glassPull.say();
            }
            figureLight.set("uGlassPull", view.glassPull.leanAhead, view.glassPull.leanAside, view.glassPull.front, view.glassPull.back);
            figureLight.set("uGauges", view.ride.roadSpeed, view.ride.engineSpeed, view.ride.fuel);
            figureLight.set("uTime", state.seconds);
            float[] axes = view.characters.cabin.axes;
            figureLight.set("uBodyAhead", axes[0], axes[1], axes[2]);
            figureLight.set("uBodyAside", axes[3], axes[4], axes[5]);
            figureLight.set("uBodyAbove", axes[6], axes[7], axes[8]);
            quad();
            glazed = seated;
            GL11.glDepthFunc(GL_LEQUAL);
            GL11.glDisable(GL_DEPTH_TEST);
            if (view.characters.holding()) {
                drawHeld(camera, state, tanHalfFieldOfView, aspect, near, far);
            }
        }
        view.characters.done();
        GL11.glDepthMask(true);
    }

    /** Has the next draw take bare triangles of the cabin, already in the cabin's own place on the screen, with nothing culled or blended. */
    private void placeCabinTriangles(FloatBuffer triangles) {
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);
        arrays();
        cardArrayOff();
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL_NORMAL_ARRAY);
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glLoadMatrixf(view.characters.cabin.paneMatrix);
        GL11.glVertexPointer(3, GL_FLOAT, 0, triangles);
    }

    private void drawPaneMask(FloatBuffer glass) {
        // Which pixels are the vehicle's glass, drawn from the triangles of it that are:
        // a window's edge is then the straight edge of a triangle.
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, paneTarget);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL_DEPTH_TEST);
        placeCabinTriangles(glass);
        paneMask.use();
        // The panes of the front of the glasshouse are drawn at a half: the picture
        // builds its own there.
        int rear = view.characters.cabin.rearPanes;
        paneMask.set("uPane", 1f);
        GL11.glDrawArrays(GL_TRIANGLES, 0, rear);
        paneMask.set("uPane", 0.5f);
        GL11.glDrawArrays(GL_TRIANGLES, rear, glass.limit() / 3 - rear);
        GL11.glDisableClientState(GL_VERTEX_ARRAY);
        GL11.glLoadIdentity();
    }

    private void drawPartKinds(VehicleMeshes.Parts parts) {
        // What part of the cabin each pixel is, from the body's triangles that are not
        // glass, each kind in its own number and the nearest winning.
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, partsTarget);
        GL11.glDepthMask(true);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        placeCabinTriangles(parts.triangles);
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
    private void setCabin(ViewCamera.Pose camera, float[] wheel, VehicleMeshes.Parts parts) {
        figureLight.set("uWheel", wheel[0], wheel[1], wheel[2], wheel[3]);
        figureLight.set("uWheelAxis", wheel[4], wheel[5], wheel[6]);
        float[] kit = Cabins.kit(view.ride.seatedIn);
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
        // is this frame, and how the body is tipped, says where the eye is in it, the
        // head leaning or not.
        float[] at = view.characters.cabin.wheel;
        if (at == null) {
            figureLight.set("uSeat", 0f, 0f, 0f);
        } else {
            float[] axes = view.characters.cabin.axes;
            float toX = camera.eyeX - at[0], toY = camera.eyeY - at[1], toUp = camera.eyeUp - at[2];
            figureLight.set("uSeat", wheel[0] + (toX * axes[0] + toY * axes[1] + toUp * axes[2]) / Cabins.SCALE,
                    wheel[1] + (toX * axes[3] + toY * axes[4] + toUp * axes[5]) / Cabins.SCALE,
                    wheel[2] + (toX * axes[6] + toY * axes[7] + toUp * axes[8]) / Cabins.SCALE);
        }
        float[] measures = parts != null ? parts.measures : Cabins.STANDARD_MEASURES;
        float[] house = Cabins.house(view.ride.seatedIn, measures);
        figureLight.set("uHouse", house[0], house[1], house[2]);
        figureLight.set("uMirror", house[3], house[4], house[5]);
        figureLight.set("uPad", wheel[0] + measures[6] / Cabins.SCALE, Cabins.PILLAR_BEHIND);
        figureLight.set("uTrade", Cabins.trade(view.ride.seatedIn));
        float[] skin = Cabins.skin(view.ride.skin);
        Cabins.Wear wear = view.ride.dress.worn;
        figureLight.set("uArms", view.ride.driving ? 1f : 0f);
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
        float[] doorMirror = Cabins.doorMirror(house, measures);
        figureLight.set("uDoorMirror", doorMirror[0], doorMirror[1], doorMirror[2]);
        figureLight.set("uDoorView", Cabins.DOOR_VIEW_MIDDLE, Cabins.DOOR_VIEW_HALF, Cabins.DOOR_VIEW_FLANK);
        figureLight.set("uPaint", view.characters.cabin.paint[0], view.characters.cabin.paint[1], view.characters.cabin.paint[2]);
        figureLight.set("uSteer", Cabins.wheelTurn(view.ride.steering));
        figureLight.set("uLamps", view.ride.instrumentsLit ? 1f : 0f, view.ride.lampsOn ? 1f : 0f);
        figureLight.set("uDome", view.ride.doorOpen ? 1f : 0f);
        GL13.glActiveTexture(GL_TEXTURE0 + MIRROR_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, mirrored.made() ? mirrorColor : noneTexture);
        GL13.glActiveTexture(GL_TEXTURE0);
        figureLight.set("uBehind", MIRROR_UNIT);
        figureLight.set("uBehindShown", reflections ? 1f : 0f);
    }

    private final float[] worn = new float[3], mirrorPlace = new float[3];

    /**
     * The people and then the vehicles, into a second picture of the world drawn beside the
     * eye's own, a mirror's or a scope's: drawn as they are for the eye, against the depth of
     * the world that picture shows, and lit the same way. In a mirror only what
     * Characters.drawBehind finds near enough is drawn at all, and a pass that finds nothing
     * costs nothing more.
     *
     * @param pose          where the second picture is drawn from
     * @param figureTarget  where its people are drawn before they are lit, whose depth is worldDepth
     * @param worldTarget   the picture itself
     * @param mirror        whether it is a vehicle's mirror
     */
    private void drawFiguresAside(ViewCamera.Pose pose, float tan, float aspect, float near, int wide, int high, int figureTarget, int figureColor,
            int figureNormal, int figureLamps, int worldTarget, int worldDepth, boolean mirror, SkyState state, float far, float fogStart, float fogEnd) {
        for (int pass = 0; pass < 2; pass++) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, figureTarget);
            GL11.glViewport(0, 0, wide, high);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glColorMask(true, true, true, true);
            GL20.glDrawBuffers(FIGURE_BUFFERS);
            GL11.glClear(GL_COLOR_BUFFER_BIT);
            GL20.glDrawBuffers(FIRST_BUFFER);
            GL11.glEnable(GL_DEPTH_TEST);
            GL11.glDepthFunc(GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glDisable(GL_BLEND);
            smoothSkins(true);
            CharacterDraw.bare = true;
            view.characters.lamps = VehicleLamps.drawn() ? LAMP_BUFFERS : null;
            int drawn = mirror ? view.characters.drawBehind(pose, tan, aspect, near, far, pass == 1) : view.characters.draw(pose, tan, aspect, near, far, pass == 1, true);
            view.characters.lamps = null;
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
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, worldTarget);
            figureLight.use();
            figureLight.set("uFigure", 0);
            figureLight.set("uDepth", 3);
            figureLight.set("uShadowNear", 1);
            figureLight.set("uShadowFar", 2);
            GL13.glActiveTexture(GL_TEXTURE0 + 3);
            GL11.glBindTexture(GL_TEXTURE_2D, worldDepth);
            GL13.glActiveTexture(GL_TEXTURE0 + 2);
            GL11.glBindTexture(GL_TEXTURE_2D, farMap);
            GL13.glActiveTexture(GL_TEXTURE0 + 1);
            GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
            GL13.glActiveTexture(GL_TEXTURE0 + NORMAL_UNIT);
            GL11.glBindTexture(GL_TEXTURE_2D, figureNormal);
            GL13.glActiveTexture(GL_TEXTURE0);
            GL11.glBindTexture(GL_TEXTURE_2D, figureColor);
            figureLight.set("uNearMatrix", nearMatrix);
            figureLight.set("uFarMatrix", farMatrix);
            figureLight.set("uSize", (float) wide, (float) high);
            figureLight.set("uTan", tan * aspect, tan);
            figureLight.set("uNear", near);
            figureLight.set("uFar", far);
            figureLight.set("uEye", pose.eyeX, pose.eyeY, pose.eyeUp);
            figureLight.set("uRangeFrom", 0f);
            setBasis(figureLight, pose);
            figureLight.set("uLight", state.lightX, state.lightY, state.lightUp);
            setBeams(figureLight, TORCH_UNIT);
            figureLight.set("uShines", shadedAndLit && !mirror ? 1f : 0f);
            setFlash(figureLight);
            figureLight.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
            figureLight.set("uFogStart", fogStart);
            figureLight.set("uFogEnd", fogEnd);
            figureLight.set("uLoaded", loadedX - pose.eyeX, loadedY - pose.eyeY, loadedWithin);
            figureLight.set("uBeyond", mirror ? 0f : Math.min(beyond, 1f));
            figureLight.set("uWriteNear", 0f);
            figureLight.set("uInside", 0f);
            figureLight.set("uGlassPass", 0f);
            figureLight.set("uOwnAmbient", 1f, 1f, 1f);
            figureLight.set("uFigureAmbient", view.characters.ambient);
            GL13.glActiveTexture(GL_TEXTURE0 + LAMP_UNIT);
            GL11.glBindTexture(GL_TEXTURE_2D, figureLamps);
            GL13.glActiveTexture(GL_TEXTURE0);
            figureLight.set("uLampsLit", LAMP_UNIT);
            figureLight.set("uFacing", NORMAL_UNIT);
            figureLight.set("uHaveFacing", pass == 0 ? 1f : 0f);
            // In a mirror the weave of a shirt is finer than a dot of the picture, and a scope shows it from too far off.
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
    private boolean drawReflection(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect,
            float near, float far, float fogStart, float fogEnd) {
        clearOwnTarget();
        GL11.glViewport(0, 0, width, height);
        GL11.glDepthMask(true);
        smoothSkins(true);
        CharacterDraw.bare = true;
        boolean drawn = view.characters.drawReflected(camera, reflection, tanHalfFieldOfView, aspect, near, far);
        CharacterDraw.bare = false;
        smoothSkins(false);
        resumeFlat();
        if (!drawn) {
            return false;
        }
        lend(glassed);
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
        setBeams(figureLight, TORCH_UNIT);
        figureLight.set("uShines", shadedAndLit ? 1f : 0f);
        setFlash(figureLight);
        figureLight.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
        figureLight.set("uFogStart", fogStart);
        figureLight.set("uFogEnd", fogEnd);
        figureLight.set("uLoaded", loadedX - inGlass.eyeX, loadedY - inGlass.eyeY, loadedWithin);
        figureLight.set("uBeyond", 0f);
        figureLight.set("uWriteNear", 0f);
        figureLight.set("uInside", 0f);
        figureLight.set("uGlassPass", 0f);
        figureLight.set("uHavePanes", 0f);
        figureLight.set("uParted", 0f, 0f);
        figureLight.set("uOwnAmbient", 1f, 1f, 1f);
        figureLight.set("uFigureAmbient", view.characters.reflectedLight);
        noLamps();
        figureLight.set("uFacing", NORMAL_UNIT);
        figureLight.set("uHaveFacing", 1f);
        figureLight.set("uJersey", FIRST_MATERIAL_UNIT + 13);
        figureLight.set("uVinyl", FIRST_MATERIAL_UNIT + 11);
        figureLight.set("uWearGain", materialGain[13], materialGain[11]);
        figureLight.set("uWornGrain", Own.textures() ? 1f : 0f);
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
    private void showReflection(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        GL11.glViewport(0, 0, width, height);
        arrays();
        cardArrayOff();
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL_NORMAL_ARRAY);
        ViewCamera.projection(tanHalfFieldOfView, aspect, near, far, selfProjection);
        view.characters.aside(selfProjection);
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
    private void resumeCasting() {
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

    private void clearOwnTarget() {
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
    private void smoothSkins(boolean smooth) {
        for (int unit = 0; unit < SKIN_UNITS; unit++) {
            GL33.glBindSampler(unit, smooth ? skinSampler : 0);
        }
    }

    /**
     * The panes of the vehicle the viewer sits in, over everything seen through them: the
     * world, the sky and the rain, which are all outside the glass.
     */
    private void drawGlass() {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        GL11.glViewport(0, 0, width, height);
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glLoadIdentity();
        figureLight.use();
        noLamps();
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

    private void setBeams(Program program, int torchUnit) {
        for (int i = 0; i < Headlights.BEAMS; i++) {
            int at = i * Headlights.STRIDE;
            program.set(BEAM_AT[i], beams[at], beams[at + 1], beams[at + 2], beams[at + 6] * BEAM_STRENGTH);
            program.set(BEAM_WAY[i], beams[at + 3], beams[at + 4], beams[at + 5]);
        }
        program.set("uBeamShadow", BEAM_UNIT);
        program.set("uBeamMatrix", beamMatrix);
        program.set("uTorchAt", torchAt[0], torchAt[1], torchAt[2], torchAt[3]);
        program.set("uTorchWay", torchWay[0], torchWay[1], torchWay[2]);
        // Bound here and not once a frame: no unit is free in every program the torch lights.
        GL13.glActiveTexture(GL_TEXTURE0 + torchUnit);
        GL33.glBindSampler(torchUnit, 0);
        GL11.glBindTexture(GL_TEXTURE_2D, torched.made() ? torchMap : farDepth);
        GL13.glActiveTexture(GL_TEXTURE0);
        program.set("uTorchShadow", torchUnit);
        program.set("uTorchMatrix", torchMatrix);
        program.set("uTorchTexel", torchSize == 0 ? 0f : 1f / torchSize);
    }

    /** The light of a shot's flash, for a program whose source has MuzzleFlash.GLSL in it. */
    private void setFlash(Program program) {
        view.muzzleFlash.light(flash, frameNanos);
        program.set("uFlashAt", flash[0], flash[1], flash[2], flash[3]);
        program.set("uFlashColor", flash[4], flash[5], flash[6]);
        fires.light(program);
    }

    /**
     * The weapon the viewer holds and the arms on it, drawn after the rest of the body into
     * the same picture of their own, cleared between, and lit the same way. Their near plane
     * is their own: the body's cuts away the head and chest, and would cut the weapon's stock
     * off in the middle of the picture.
     */
    private void drawHeld(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        clearOwnTarget();
        CharacterDraw.bare = true;
        view.characters.drawHeld(tanHalfFieldOfView, aspect, HELD_NEAR, far);
        CharacterDraw.bare = false;
        // The game's draw leaves its own program, buffers and arrays bound.
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);
        GL11.glDisable(GL_ALPHA_TEST);
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        figureLight.use();
        noLamps();
        if (seated) {
            // The cabin's pass before this one cuts away glass, pillars and roof near the eye,
            // which is where a weapon raised out of the window is: none of that is the weapon's.
            figureLight.set("uInside", 0f);
            figureLight.set("uPainted", 0f);
            figureLight.set("uHavePanes", 0f);
            figureLight.set("uParted", 0f, 0f);
            figureLight.set("uWheel", 0f, 0f, 0f, 0f);
            figureLight.set("uSeat", 0f, 0f, 0f);
            figureLight.set("uFigureAmbient", view.characters.ownLight);
            figureLight.set("uHaveFacing", 1f);
        }
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
        if (view.ownShotgun.waiting() || view.ownRifle.waiting() || view.scope.waiting()) {
            view.heldDepth.begin(sceneColor, width, height, frameNanos);
        }
        drawOwnGun(view.ownShotgun, camera, state, tanHalfFieldOfView, aspect, near, far);
        drawOwnGun(view.ownRifle, camera, state, tanHalfFieldOfView, aspect, near, far);
        if (view.scope.waiting()) {
            lightHeld(view.scope.begin(), camera, state);
            view.scope.draw(camera, view.characters.aim, sceneTarget, width, height, ownDepth, tanHalfFieldOfView, aspect, HELD_NEAR, far, near,
                    asideX, asideY);
        }
        if (view.viewModel.hasMuzzle) {
            view.muzzleFlash.place(camera, view.viewModel.muzzle, view.viewModel.pointing);
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
            float[] muzzle = view.viewModel.muzzle, grip = view.viewModel.gripSeen;
            float reach = (float) Math.sqrt((muzzle[0] - grip[0]) * (muzzle[0] - grip[0]) + (muzzle[1] - grip[1]) * (muzzle[1] - grip[1])
                    + (muzzle[2] - grip[2]) * (muzzle[2] - grip[2]));
            view.muzzleFlash.draw(muzzle, view.viewModel.pointing, reach, frameNanos, tanHalfFieldOfView, aspect, width, height, near, far);
        } else {
            view.muzzleFlash.none();
        }
    }

    /** A gun the mod has a model of its own for, if one was placed this frame, over the hands that hold it, in the light of the frame. */
    private void drawOwnGun(OwnGun gun, ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        if (gun.waiting()) {
            lightHeld(gun.begin(), camera, state);
            gun.draw(camera, sceneTarget, width, height, ownDepth, tanHalfFieldOfView, aspect, HELD_NEAR, far, near, asideX, asideY);
        }
    }

    /** A projection times a view, each sixteen floats by columns. */
    private static void multiply(float[] cast, float[] seen, float[] out) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += cast[k * 4 + row] * seen[column * 4 + k];
                }
                out[column * 4 + row] = sum;
            }
        }
    }

    /**
     * Gives the program of something the mod draws in the viewer's hands, a gun or a scope,
     * the light of the frame: the sky, the sun and its two shadow maps, the eye and its axes,
     * the flash of a shot.
     */
    private void lightHeld(Program program, ViewCamera.Pose camera, SkyState state) {
        atmosphere(program, state);
        setBasis(program, camera);
        setFlash(program);
        program.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
        program.set("uLight", state.lightX, state.lightY, state.lightUp);
        program.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
        program.set("uNearMatrix", nearMatrix);
        program.set("uFarMatrix", farMatrix);
        GL13.glActiveTexture(GL_TEXTURE0 + OwnGun.SHADOW_FAR_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, farMap);
        GL13.glActiveTexture(GL_TEXTURE0 + OwnGun.SHADOW_NEAR_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
        GL13.glActiveTexture(GL_TEXTURE0);
    }

    /** Nearer the eye than this, in tiles, what the picture shows on foot is in the viewer's hands: the hands, most of a weapon, a scope. */
    private static final float HELD_WITHIN = 0.6f;

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

    /**
     * The programs and the textures that do not depend on the picture's size are made a piece
     * at a time: made all at once on the frame that first draws the picture, they held that
     * frame for three and a half seconds. FirstPerson has a piece made on each frame from the
     * moment first person, or a graphics mode in which the picture draws the game's own view, is asked for.
     */
    private static int preparing;
    private static long preparingNanos, preparingWorst;
    private static final String NOISE_AHEAD = "cloud noise";

    /**
     * One piece: its making, null from that or what went wrong, and before it what its making
     * reads out of the jar or works out, begun on other threads and done or not.
     */
    private record Piece(Runnable begin, BooleanSupplier begun, Supplier<String> make, boolean ofPictures) {
        Piece(Supplier<String> make) {
            this(() -> { }, () -> true, make, false);
        }

        Piece(Runnable begin, BooleanSupplier begun, Supplier<String> make) {
            this(begin, begun, make, false);
        }

        static Piece ofPictures(Supplier<String> make, String... paths) {
            return new Piece(() -> {
                for (String path : paths) {
                    Pictures.readAhead(path);
                }
            }, () -> {
                for (String path : paths) {
                    if (!Pictures.isRead(path)) {
                        return false;
                    }
                }
                return true;
            }, make, true);
        }
    }

    /**
     * The surfaces a picture reads whatever the graphics mode shows of the mod's own: the bed of
     * shallow water is the soil, and a vehicle's cabin is trimmed in the wood, carpet, vinyl and
     * cloth. They are made with the core.
     */
    private static final Set<String> ALWAYS_READ = Set.of("soil", "wood", "carpet", "vinyl", "cloth");

    /**
     * What every picture is drawn with, in any graphics mode: the programs, the cloud noise,
     * the scope's program, the rain and the surfaces every mode reads. Nothing of
     * it is made until first person is first asked for.
     */
    private static final List<Piece> CORE = corePieces();
    /**
     * The pictures of the mod's own surfaces and plants and its weapon models, which only a
     * graphics mode that shows the mod's own draws from: about 135 MB on the graphics card at
     * full size, that the Low mode never reads.
     */
    private static final List<Piece> CONTENT = contentPieces();

    private static List<Piece> corePieces() {
        List<Piece> pieces = new ArrayList<>();
        pieces.add(new Piece(Pipeline::linkPrograms));
        pieces.add(new Piece(() -> Ahead.begin(NOISE_AHEAD, () -> CloudNoise.build(NOISE_SIZE)), () -> Ahead.done(NOISE_AHEAD), () -> {
            GL11.glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            makeWhite();
            return makeNoise();
        }));
        pieces.add(new Piece(Scope::prepare));
        pieces.add(new Piece(() -> {
            makeSamplers();
            makeRainDrops();
            return null;
        }));
        // Last, one for each of ALWAYS_READ and the arms: LET_GO gives these back, and the making begins again from the first of them.
        for (int material = 0; material < MATERIALS.length; material++) {
            if (ALWAYS_READ.contains(MATERIALS[material])) {
                pieces.add(materialPiece(material));
            }
        }
        // Every mode draws the arms on a held weapon as OwnArms says, Low included: see Own.arms.
        pieces.add(new Piece(OwnArms::readAhead, OwnArms::isRead, OwnArms::prepare));
        return pieces;
    }

    /** How many pieces at the end of CORE LET_GO gives back: the surfaces of ALWAYS_READ and the arms. */
    private static final int CORE_GIVEN_BACK = ALWAYS_READ.size() + 1;

    private static Piece materialPiece(int material) {
        String picture = "/materials/" + MATERIALS[material] + ".bc";
        return material == BARK ? Piece.ofPictures(() -> loadBarks(picture), picture)
                : Piece.ofPictures(() -> loadPicture(picture, materialTexture, materialGain, material), picture);
    }

    private static List<Piece> contentPieces() {
        List<Piece> pieces = new ArrayList<>();
        for (int material = 0; material < MATERIALS.length; material++) {
            if (!ALWAYS_READ.contains(MATERIALS[material])) {
                pieces.add(materialPiece(material));
            }
        }
        for (int each = 0; each < NATURE.length; each++) {
            int nature = each;
            String picture = "/materials/" + NATURE[nature] + ".bc";
            pieces.add(Piece.ofPictures(() -> loadPicture(picture, natureTexture, natureGain, nature), picture));
        }
        pieces.add(Piece.ofPictures(() -> {
            try {
                int newSprigs = Pictures.load(SPRIGS, false), newForm;
                try {
                    newForm = Pictures.load(SPRIG_FORM, false);
                } catch (IOException e) {
                    GL11.glDeleteTextures(newSprigs);
                    throw e;
                }
                GL11.glDeleteTextures(sprigs);
                GL11.glDeleteTextures(sprigForm);
                sprigs = newSprigs;
                sprigForm = newForm;
                return null;
            } catch (IOException e) {
                return "the sprigs could not be read: " + e.getMessage();
            }
        }, SPRIGS, SPRIG_FORM));
        // The weapon models are given back with the pictures and made again with them: see giveBackContent.
        for (OwnModel gun : OwnModel.ALL) {
            pieces.add(new Piece(gun::readAhead, gun::isRead, () -> OwnGun.prepare(gun)));
        }
        return pieces;
    }

    private static final String SPRIGS = "/materials/sprigs.bc", SPRIG_FORM = "/materials/sprigs-form.bc";
    private static boolean coreBegun;
    private static volatile boolean contentBegun;
    /** Which list the next piece is of: set by pieceWaits, read by prepareNext. */
    private static List<Piece> makes;
    /** Whether the pictures on the graphics card are the half size ones, and the pieces being made again at the other size, if any are. */
    private static volatile boolean picturesSmall;
    private static volatile List<Piece> resized;
    /** Whether a making of the core's surfaces at another size was given up part way, so that they are of both sizes. */
    private static volatile boolean coreMixed;
    /**
     * Set for the rest of the session once the graphics card has had no room for the content:
     * it is not asked again, the pictures stay the half size ones, and every mode is drawn
     * with what Low is drawn with. First person needs none of the content.
     */
    private static volatile boolean contentRefused;
    /** Set by whoever is told by the driver, while a piece is made, that it is out of memory: see noted. */
    private static boolean noRoom;
    private static final int GL_OUT_OF_MEMORY = 0x0505;
    /** Dev only: the piece of the content, counted from 1, at whose making the graphics card is taken to have run out of room. */
    private static final int NO_ROOM_AT = Integer.getInteger("cameraunlock.zomboid.noRoomAt", 0);
    private static int resizedNext;
    private static int contentPreparing;
    /** Whether every piece of CONTENT is made. Set on the render thread, read by the logic thread: see Own. */
    private static volatile boolean contentReady;

    /** Whether there is anything left to make before the first picture can be drawn. */
    boolean unprepared() {
        return !prepared && failure() == null;
    }

    /**
     * Whether the pictures of the mod's own are on the graphics card. Until they are, a mode
     * that shows the mod's own is drawn as the one that shows none is: see Own.
     */
    boolean contentReady() {
        return contentReady;
    }

    /** Whether the graphics mode shows the mod's own and its pictures have still to be made. */
    boolean contentWaited() {
        return !Quality.plain() && !contentReady && !contentRefused && failure() == null;
    }

    /**
     * Whether pieces wait to be made, or given back, with no picture being drawn to do it: a
     * making of the content begun and not ended, and pictures that are not the size the mode
     * now reads them at.
     */
    boolean contentHalfMade() {
        return failure() == null && (contentBegun && !contentReady || resized != null || prepared && (coreMixed || picturesSmall != small()));
    }

    /** Whether the pictures are to be the half size ones: in a mode that reads them so, and once the graphics card has had no room for the content. */
    private static boolean small() {
        return Quality.smallerPictures() || contentRefused;
    }

    /** Told each OpenGL error met while a piece is made, by whoever asks the driver for it: out of memory is not a fault of this build. */
    static void noted(int error) {
        if (error == GL_OUT_OF_MEMORY) {
            noRoom = true;
        }
    }

    /** A colour of nothing, for a sampler whose picture is not made. Render thread. */
    static int none() {
        return noneTexture;
    }

    /** Queued on frames that draw no picture while first person is being got ready: hands the graphics card the far land made since the last. */
    final TextureDraw.GenericDrawer HAND_FAR = new TextureDraw.GenericDrawer() {
        @Override
        public void render() {
            view.worldScene.farLand.hand();
        }
    };

    /**
     * Queued on frames that draw no picture while first person is being got ready: makes the
     * next piece, or nothing on a frame where what that piece reads out of the jar is still
     * being read.
     */
    final TextureDraw.GenericDrawer PREPARE = new TextureDraw.GenericDrawer() {
        @Override
        public void render() {
            turn();
            // Two views getting ready in one frame make one piece between them.
            if (failure() == null && pieceIn != gameFrame && pieceWaits()) {
                draw(null, null, null, 0f, 0f, 0f, 0f, 0f, 0f, null);
            }
        }
    };

    /**
     * Whether a piece can be made now. Begins, on other threads, the reading of what the
     * pieces still to be made are made from: the core's the first time, and the content's the
     * first time a mode that shows the mod's own is on.
     */
    private static boolean pieceWaits() {
        if (broken != null) {
            return false;
        }
        if (!coreBegun) {
            coreBegun = true;
            Pictures.askCard();
            picturesSmall = small();
            Pictures.small = picturesSmall;
            // From the next to be made: after LET_GO that is the first of the surfaces, and the pieces before it are made.
            for (int piece = preparing; piece < CORE.size(); piece++) {
                CORE.get(piece).begin.run();
            }
        }
        if (!prepared) {
            makes = CORE;
            return CORE.get(preparing).begun.getAsBoolean();
        }
        if (contentBegun && Quality.plain()) {
            // The mode now shows none of the mod's own: its pictures are given back, made or half made.
            giveBackContent();
            Log.info("the graphics mode shows none of the mod's own: its surfaces, plants and weapons are given back to the graphics card");
        }
        if (contentBegun && !contentReady) {
            makes = CONTENT;
            return CONTENT.get(contentPreparing).begun.getAsBoolean();
        }
        if (resized == null && (coreMixed || picturesSmall != small())) {
            // The mode now shows the pictures at the other size: each is made again in its place, a
            // piece a frame, and the picture goes on being drawn from whichever a surface has.
            coreMixed = false;
            picturesSmall = small();
            Pictures.small = picturesSmall;
            resized = new ArrayList<>();
            for (Piece piece : CORE) {
                if (piece.ofPictures) {
                    resized.add(piece);
                }
            }
            for (Piece piece : CONTENT) {
                if (piece.ofPictures && contentReady) {
                    resized.add(piece);
                }
            }
            for (Piece piece : resized) {
                piece.begin.run();
            }
            resizedNext = 0;
        }
        if (resized != null) {
            makes = resized;
            return resized.get(resizedNext).begun.getAsBoolean();
        }
        if (contentReady || contentRefused || Quality.plain()) {
            return false;
        }
        contentBegun = true;
        for (Piece piece : CONTENT) {
            piece.begin.run();
        }
        makes = CONTENT;
        return CONTENT.get(contentPreparing).begun.getAsBoolean();
    }

    /** Makes the next piece of the core, or of the content once the core is made. Null, or what the driver refused or the jar lacked. */
    private static String prepareNext() {
        long start = System.nanoTime();
        pieceIn = gameFrame;
        noRoom = false;
        String problem;
        boolean lastOfCore = false, lastOfContent = false, lastOfResized = false;
        if (makes == CORE) {
            problem = CORE.get(preparing++).make.get();
            lastOfCore = preparing == CORE.size();
        } else if (makes == CONTENT) {
            problem = CONTENT.get(contentPreparing++).make.get();
            lastOfContent = contentPreparing == CONTENT.size();
        } else {
            problem = resized.get(resizedNext++).make.get();
            lastOfResized = resizedNext == resized.size();
        }
        if (problem == null) {
            int error = GL11.glGetError();
            noted(error);
            if (error != 0) {
                problem = "OpenGL error 0x" + Integer.toHexString(error) + " making the textures";
            }
        }
        if (problem == null && makes == CONTENT && contentPreparing == NO_ROOM_AT) {
            noRoom = true;
            problem = "cameraunlock.zomboid.noRoomAt asks for no room at piece " + NO_ROOM_AT;
        }
        boolean refused = problem != null && noRoom && makes != CORE && !contentRefused;
        noRoom = false;
        if (refused) {
            // A graphics card with no room for the content has room for first person without it: the content
            // goes back, whatever of it was made, and the core's surfaces are made at half size by the pieces after this.
            Log.info("the graphics card has no room for the mod's own surfaces, plants and weapons (" + problem
                    + "): they are given back, and for the rest of this session every graphics mode is drawn without them");
            contentRefused = true;
            giveBackContent();
            clearErrors();
            preparingNanos = 0;
            preparingWorst = 0;
            return null;
        }
        if (problem != null) {
            // No piece is made after this one, so nothing would take what was read ahead for the rest.
            Pictures.letGoOfReadAhead();
        }
        long took = System.nanoTime() - start;
        preparingNanos += took;
        preparingWorst = Math.max(preparingWorst, took);
        if (lastOfCore && problem == null) {
            prepared = true;
            Log.info(String.format(Locale.ROOT, "first person picture ready: its pieces took %.0f ms in all, the longest %.0f ms",
                    preparingNanos / 1e6, preparingWorst / 1e6));
            preparingNanos = 0;
            preparingWorst = 0;
        }
        if (lastOfContent && problem == null) {
            contentReady = true;
            Own.picturesMade(true);
            Log.info(String.format(Locale.ROOT, "the mod's own surfaces, plants and weapons are on the graphics card: %d pieces taking %.0f ms in all, the longest %.0f ms",
                    contentPreparing, preparingNanos / 1e6, preparingWorst / 1e6));
            preparingNanos = 0;
            preparingWorst = 0;
        }
        if (lastOfResized && problem == null) {
            resized = null;
            Log.info(String.format(Locale.ROOT, "the mod's pictures are made again at %s, taking %.0f ms in all, the longest %.0f ms",
                    picturesSmall ? "half size" : "full size", preparingNanos / 1e6, preparingWorst / 1e6));
            preparingNanos = 0;
            preparingWorst = 0;
        }
        return problem;
    }

    /** Null, or what the driver refused. */
    private static String linkPrograms() {
        StringBuilder problem = new StringBuilder();
        world = Program.link("world", WORLD_VERTEX, WORLD_FRAGMENT, problem);
        worldDepth = Program.link("world depth", WORLD_VERTEX, WORLD_FRAGMENT_DEPTH, problem);
        paneMask = Program.link("pane mask", PipelineShaders.PANE_VERTEX, PipelineShaders.PANE_FRAGMENT, problem);
        selfShown = Program.link("viewer in the glass", PipelineShaders.PANE_VERTEX, OwnReflection.SHOWN, problem);
        partKind = Program.link("cabin parts", PipelineShaders.PANE_VERTEX, PipelineShaders.PART_FRAGMENT, problem);
        shadow = Program.link("shadow", SHADOW_VERTEX, SHADOW_FRAGMENT, problem);
        rain = Program.link("rain", PipelineShaders.RAIN_VERTEX, PipelineShaders.RAIN_FRAGMENT, problem);
        snow = Program.link("snow", PipelineShaders.SNOW_VERTEX, PipelineShaders.SNOW_FRAGMENT, problem);
        dust = Program.link("dust", PipelineShaders.DUST_VERTEX, PipelineShaders.DUST_FRAGMENT, problem);
        sky = Program.link("sky", SCREEN_VERTEX, Sky.FRAGMENT, problem);
        clouds = Program.link("clouds", SCREEN_VERTEX, Sky.CLOUDS, problem);
        bolt = Program.link("lightning", Lightning.BOLT_VERTEX, Lightning.BOLT_FRAGMENT, problem);
        far = Program.link("far land", PipelineShaders.FAR_VERTEX, PipelineShaders.FAR_FRAGMENT, problem);
        farTree = Program.link("far trees", PipelineShaders.FAR_TREE_VERTEX, PipelineShaders.FAR_TREE_FRAGMENT, problem);
        figureLight = Program.link("character light", SCREEN_VERTEX, FIGURE_LIGHT, problem);
        occlusion = Program.link("occlusion", SCREEN_VERTEX, PipelineShaders.OCCLUSION, problem);
        soften = Program.link("occlusion soften", SCREEN_VERTEX, PipelineShaders.SOFTEN, problem);
        volume = Program.link("lit air", SCREEN_VERTEX, PipelineShaders.VOLUME, problem);
        bloomDown = Program.link("glow down", SCREEN_VERTEX, PipelineShaders.BLOOM_DOWN, problem);
        bloomUp = Program.link("glow up", SCREEN_VERTEX, PipelineShaders.BLOOM_UP, problem);
        develop = Program.link("develop", SCREEN_VERTEX, PipelineShaders.DEVELOP, problem);
        smooth = Program.link("smooth", SCREEN_VERTEX, PipelineShaders.SMOOTH, problem);
        steady = Program.link("steady", SCREEN_VERTEX, PipelineShaders.STEADY, problem);
        return problem.length() > 0 ? problem.toString() : null;
    }

    private static String makeNoise() {
        ByteBuffer cells;
        try {
            cells = Ahead.take(NOISE_AHEAD, () -> CloudNoise.build(NOISE_SIZE));
        } catch (IOException unread) {
            return unread.getMessage();
        }
        noiseTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_3D, noiseTexture);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_REPEAT);
        GL12.glTexImage3D(GL_TEXTURE_3D, 0, GL_RG8, NOISE_SIZE, NOISE_SIZE, NOISE_SIZE, 0, GL_RG, GL_UNSIGNED_BYTE, cells);
        return null;
    }

    private static void makeWhite() {
        // What a thing with no sprite is drawn with: the vertex colour alone.
        whiteTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, whiteTexture);
        ByteBuffer white = ByteBuffer.allocateDirect(4);
        white.put((byte) -1).put((byte) -1).put((byte) -1).put((byte) -1).flip();
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, white);
        filter(GL_NEAREST);
        noneTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, noneTexture);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, ByteBuffer.allocateDirect(4));
        filter(GL_NEAREST);
        farDepth = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, farDepth);
        ByteBuffer far = ByteBuffer.allocateDirect(Float.BYTES).order(ByteOrder.nativeOrder()).putFloat(0, 1f);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, 1, 1, 0, GL_DEPTH_COMPONENT, GL_FLOAT, far);
        filter(GL_NEAREST);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
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

    private void giveBackShadowMaps() {
        for (int map : new int[] {nearMap, farMap, roofMap}) {
            GL11.glDeleteTextures(map);
        }
        for (int target : new int[] {nearTarget, farTarget, roofTarget}) {
            GL30.glDeleteFramebuffers(target);
        }
        nearMap = 0;
        GL11.glDeleteTextures(shelterTexture);
        shelterTexture = 0;
        GL11.glDeleteTextures(roomAirTexture);
        roomAirTexture = 0;
        giveBack(beamed);
        giveBack(torched);
    }

    /** The three shadow maps every frame draws: near, far and overhead. Null, or what the driver refused. */
    private String makeShadowMaps() {
        int[] made = new int[2];
        String near = shadowMap(made, shadowSize);
        nearMap = made[0];
        nearTarget = made[1];
        String far = shadowMap(made, shadowSize);
        farMap = made[0];
        farTarget = made[1];
        String roof = shadowMap(made, shadowSize);
        roofMap = made[0];
        roofTarget = made[1];
        return near != null ? near : far != null ? far : roof;
    }

    /** The headlights' shadow map, for the frames in which a headlight is lit: at High's size it is 64 MB. */
    private String makeBeamed() {
        int[] made = new int[2];
        String problem = shadowMap(made, shadowSize);
        beamMap = made[0];
        beamTarget = made[1];
        beamed.textures.add(beamMap);
        beamed.targets.add(beamTarget);
        beamed.bytes = 4L * shadowSize * shadowSize;
        return problem;
    }

    /** The torch's shadow map, for the frames in which the viewer's torch throws a beam: 16 MB at its largest. */
    private String makeTorched() {
        int[] made = new int[2];
        torchSize = Math.min(shadowSize, TORCH_MAP_MOST);
        String problem = shadowMap(made, torchSize);
        torchMap = made[0];
        torchTarget = made[1];
        torched.textures.add(torchMap);
        torched.targets.add(torchTarget);
        torched.bytes = 4L * torchSize * torchSize;
        return problem;
    }

    /**
     * Has a set's pictures there for the pass about to draw into them, making them if they are
     * not. A making binds a texture and a target: the pass sets its own after this. Thrown, as
     * a frame's fault, when the driver will not make them.
     */
    private void lend(Lent set) {
        set.usedNanos = frameNanos;
        if (set.made()) {
            return;
        }
        long start = System.nanoTime();
        making = set;
        String problem;
        try {
            problem = set.make.get();
        } finally {
            making = null;
        }
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        Log.info(String.format(Locale.ROOT, "first person picture: the %s made in %.1f ms", set.what, (System.nanoTime() - start) / 1e6));
    }

    private static void giveBack(Lent set) {
        for (int target : set.targets) {
            GL30.glDeleteFramebuffers(target);
        }
        for (int texture : set.textures) {
            GL11.glDeleteTextures(texture);
        }
        set.targets.clear();
        set.textures.clear();
        set.bytes = 0;
    }

    /** At the end of a frame: gives back each set that no frame has drawn into for a while. */
    private void takeBackLent() {
        for (Lent set : SIZED) {
            if (set != whole && set.made() && frameNanos - set.usedNanos > LENT_FOR_NANOS) {
                giveBack(set);
                Log.info("first person picture: the " + set.what + " given back, unused");
            }
        }
        for (Lent set : UNSIZED) {
            if (set.made() && frameNanos - set.usedNanos > LENT_FOR_NANOS) {
                giveBack(set);
                Log.info("first person picture: the " + set.what + " given back, unused");
            }
        }
        view.scope.takeBackPicture(frameNanos - LENT_FOR_NANOS);
        view.heldDepth.takeBack(frameNanos - LENT_FOR_NANOS);
    }

    private String makeMirror() {
        mirrorColor = texture(GL_RGBA16F, MIRROR_WIDTH, MIRROR_HEIGHT, GL_RGBA, GL_FLOAT, GL_LINEAR);
        mirrorDepth = texture(GL_DEPTH_COMPONENT24, MIRROR_WIDTH, MIRROR_HEIGHT, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
        mirrorTarget = target(mirrorColor);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, mirrorDepth, 0);
        String problem = complete("mirror");
        // Who and what stands behind is drawn against the depth of the world the mirror shows.
        mirrorFigureColor = texture(GL_RGBA8, MIRROR_WIDTH, MIRROR_HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        mirrorFigureTarget = target(mirrorFigureColor);
        mirrorFigureNormal = texture(GL_RGBA8, MIRROR_WIDTH, MIRROR_HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + 1, GL_TEXTURE_2D, mirrorFigureNormal, 0);
        mirrorFigureLamps = texture(GL_RGBA8, MIRROR_WIDTH, MIRROR_HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + 2, GL_TEXTURE_2D, mirrorFigureLamps, 0);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, mirrorDepth, 0);
        if (problem == null) {
            problem = complete("mirror's people");
        }
        return problem;
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

    private static String shadowMap(int[] made, int size) {
        int map = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, map);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, size, size, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
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
    final TextureDraw.GenericDrawer RELEASE = new TextureDraw.GenericDrawer() {
        @Override
        public void render() {
            view.characters.setDown();
            if (width == 0) {
                return;
            }
            giveBackSized();
            width = 0;
            height = 0;
            giveBack(mirrored);
            giveBackShadowMaps();
            view.scope.giveBackPicture();
            heldBytes = 0;
            Log.info("first person picture set down: its targets and shadow maps are given back to the graphics card");
        }
    };

    /**
     * Queued once a view's first person has been off for a while and the logic thread has let
     * its every build go. Once no view holds any of it, gives back the graphics card's copy of the world, the pictures and models of
     * the mod's own, the surfaces every mode reads, the arms, the furniture's models and the copies of
     * the game's meshes, and whatever was read ahead for a making that will not now happen.
     * What is left is the programs, the cloud noise and the rain, which are small.
     * Asked for again, the rest is made again, as it was the first time.
     */
    final TextureDraw.GenericDrawer LET_GO = new TextureDraw.GenericDrawer() {
        @Override
        public void render() {
            holds = false;
            ChunkBuffers.letGo(view.index);
            view.worldScene.farLand.catchUp();
            int copies = view.characters.letGo();
            view.characters.movers.forget();
            view.kerbSteps.forget();
            for (int other = 0; other < View.COUNT; other++) {
                if (View.of(other) != null && View.of(other).pipeline.holds) {
                    // Another view is drawn with all of it still: this one gave its own back as it was set down.
                    return;
                }
            }
            Decor.letGo();
            Flats.letGo();
            Rugs.letGo();
            forgetTexels();
            GL15.glDeleteBuffers(modelBuffer);
            GL15.glDeleteBuffers(modelCorners);
            modelBuffer = modelCorners = 0;
            if (contentBegun) {
                giveBackContent();
                Log.info("the mod's own surfaces, plants and weapons are given back to the graphics card");
            }
            Pictures.letGoOfReadAhead();
            Ahead.forget(NOISE_AHEAD);
            resized = null;
            coreMixed = false;
            for (int material = 0; material < MATERIALS.length; material++) {
                if (ALWAYS_READ.contains(MATERIALS[material])) {
                    GL11.glDeleteTextures(materialTexture[material]);
                    materialTexture[material] = 0;
                    materialGain[material] = 1f;
                }
            }
            OwnArms.letGo();
            // The surfaces and the arms are made again by the pieces of the core that made them, and their reading begun again with them.
            preparing = Math.min(preparing, CORE.size() - CORE_GIVEN_BACK);
            prepared = false;
            coreBegun = false;
            Log.info("first person has given back the surfaces every graphics mode reads, the arms, the furniture's models and its copies of " + copies + " of the game's meshes");
        }
    };

    /**
     * Ends a making at another size part way, after what was read ahead for it is let go. Some of the core's
     * surfaces are then at one size and some at the other: all are made again by the next piece asked for.
     */
    private static void giveUpResize() {
        if (resized != null) {
            resized = null;
            coreMixed = true;
        }
    }

    /** Gives back the pictures and models of CONTENT, made or half made, and whatever was read ahead for them. They are made again when a mode next shows them. */
    private static void giveBackContent() {
        Pictures.letGoOfReadAhead();
        // What was read ahead for a making at another size went with the rest, and its next piece would wait for it for good.
        giveUpResize();
        for (int material = 0; material < MATERIALS.length; material++) {
            if (ALWAYS_READ.contains(MATERIALS[material])) {
                continue;
            }
            GL11.glDeleteTextures(materialTexture[material]);
            materialTexture[material] = 0;
            materialGain[material] = 1f;
        }
        for (int nature = 0; nature < NATURE.length; nature++) {
            GL11.glDeleteTextures(natureTexture[nature]);
            natureTexture[nature] = 0;
            natureGain[nature] = 1f;
        }
        GL11.glDeleteTextures(sprigs);
        GL11.glDeleteTextures(sprigForm);
        sprigs = 0;
        sprigForm = 0;
        for (OwnModel gun : OwnModel.ALL) {
            gun.letGo();
        }
        contentPreparing = 0;
        contentBegun = false;
        contentReady = false;
        Own.picturesMade(false);
    }

    /** Forgets the size of every texture drawn with: names given back to the graphics card are handed out again for pictures of other sizes. */
    static void forgetTexels() {
        Arrays.fill(texelOf, 0);
        texelsKnown = 0;
    }

    /**
     * Gives the sized textures and targets back to the graphics card. The driver hands the same
     * numbers out again for the next ones made, so whoever told a texture of these apart by its
     * number is told they are gone: a target of their own still holds the one given back.
     */
    private void giveBackSized() {
        for (Lent set : SIZED) {
            giveBack(set);
        }
        view.heldDepth.forgetTarget();
    }

    /** Makes the pictures the frame is drawn through, at the size of the game's own. Null, or what the driver refused. */
    private String resize(int newWidth, int newHeight) {
        // The last size's are given back first. Left alone they are held until the game
        // ends, a set for every size the picture has been.
        giveBackSized();
        width = newWidth;
        height = newHeight;
        making = whole;
        try {
            return makeWhole();
        } finally {
            making = null;
        }
    }

    /** The steadied picture of the last frame and of this one: only with FrameBlending on. */
    private String makeSteadied() {
        for (int i = 0; i < 2; i++) {
            keptColor[i] = texture(GL_RGBA16F, width, height, GL_RGBA, GL_FLOAT, GL_LINEAR);
            keptAhead[i] = texture(GL_R16F, width, height, GL_RED, GL_FLOAT, GL_NEAREST);
            keptTarget[i] = target(keptColor[i]);
            GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + 1, GL_TEXTURE_2D, keptAhead[i], 0);
            GL20.glDrawBuffers(BOTH_BUFFERS);
        }
        sceneMoves = texture(GL_R8, width, height, GL_RED, GL_UNSIGNED_BYTE, GL_NEAREST);
        movesAlone = target(sceneMoves);
        movesTarget = target(sceneColor);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + 1, GL_TEXTURE_2D, sceneMoves, 0);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, sceneDepth, 0);
        GL20.glDrawBuffers(BOTH_BUFFERS);
        keptAny = false;
        return complete("steadied picture");
    }

    /** What is drawn of the vehicle sat in: only while seated. */
    private String makeCabin() {
        repaintedColor = texture(GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        repaintedDepth = texture(GL_DEPTH_COMPONENT24, width, height, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
        repaintedTarget = target(repaintedColor);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, repaintedDepth, 0);
        String problem = complete("repainted vehicle");
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
        return problem;
    }

    /** The viewer as a window's glass shows them: only in High, with a window near. */
    private String makeGlassed() {
        selfColor = texture(GL_RGBA16F, width, height, GL_RGBA, GL_FLOAT, GL_NEAREST);
        selfTarget = target(selfColor);
        return complete("viewer in the glass");
    }

    private String makeWatered() {
        waterWidth = Math.max(1, width / 3);
        waterHeight = Math.max(1, height / 3);
        waterColor = texture(GL_RGBA16F, waterWidth, waterHeight, GL_RGBA, GL_FLOAT, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        // Its coarser levels come to a third of it again.
        making.bytes += (long) waterWidth * waterHeight * 8 / 3;
        waterDepth = texture(GL_DEPTH_COMPONENT24, waterWidth, waterHeight, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
        waterTarget = target(waterColor);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, waterDepth, 0);
        return complete("what water shows");
    }

    /** How many pixels high the picture being drawn into is. */
    private int drawnHigh() {
        return inScope ? Scope.PICTURE : inMirror ? MIRROR_HEIGHT : inWater ? waterHeight : height;
    }

    /** The shade of what stands near and the lit air, at half the picture's size: only in High. */
    private String makeShaded() {
        occlusionColor = texture(GL_RGBA8, levelWidth[0], levelHeight[0], GL_RGBA, GL_UNSIGNED_BYTE, GL_LINEAR);
        occlusionTarget = target(occlusionColor);
        String problem = complete("occlusion");
        softColor = texture(GL_RGBA8, levelWidth[0], levelHeight[0], GL_RGBA, GL_UNSIGNED_BYTE, GL_LINEAR);
        softTarget = target(softColor);
        volumeColor = texture(GL_RGBA16F, levelWidth[0], levelHeight[0], GL_RGBA, GL_FLOAT, GL_LINEAR);
        volumeTarget = target(volumeColor);
        softVolumeColor = texture(GL_RGBA16F, levelWidth[0], levelHeight[0], GL_RGBA, GL_FLOAT, GL_LINEAR);
        softVolumeTarget = target(softVolumeColor);
        if (problem == null) {
            problem = complete("lit air");
        }
        return problem;
    }

    /** What every frame draws into, at the picture's size. */
    private String makeWhole() {
        sceneColor = texture(GL_RGBA16F, width, height, GL_RGBA, GL_FLOAT, GL_LINEAR);
        sceneDepth = texture(GL_DEPTH_COMPONENT24, width, height, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
        sceneTarget = target(sceneColor);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, sceneDepth, 0);
        String problem = complete("scene");
        if (problem != null) {
            return problem;
        }
        decalTarget = target(sceneColor);
        problem = complete("bullet hole");
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
        figureLamps = texture(GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + 2, GL_TEXTURE_2D, figureLamps, 0);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, sceneDepth, 0);
        if (problem == null) {
            problem = complete("character");
        }
        outlineColor = texture(GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        outlineTarget = target(outlineColor);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, sceneDepth, 0);
        if (problem == null) {
            problem = complete("outlines");
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
            cloudColor = texture(GL_RGBA16F, levelWidth[0], levelHeight[0], GL_RGBA, GL_FLOAT, GL_LINEAR);
            cloudTarget = target(cloudColor);
            softCloudColor = texture(GL_RGBA16F, levelWidth[0], levelHeight[0], GL_RGBA, GL_FLOAT, GL_LINEAR);
            softCloudTarget = target(softCloudColor);
            problem = complete("clouds");
        }
        if (problem == null) {
            Log.info("first person picture sized " + width + "x" + height);
        }
        return problem;
    }

    private int texture(int internal, int w, int h, int format, int type, int filter) {
        int texture = GL11.glGenTextures();
        if (making != null) {
            making.textures.add(texture);
            making.bytes += (long) w * h * (internal == GL_RGBA16F ? 8 : internal == GL_R16F ? 2 : internal == GL_R8 ? 1 : 4);
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
    private static String loadPicture(String picture, int[] textures, float[] gains, int index) {
        try {
            // Made again at another size, the picture takes the place of the one before it, which is given back.
            int old = textures[index];
            textures[index] = Pictures.load(picture, true);
            GL11.glDeleteTextures(old);
        } catch (IOException e) {
            return e.getMessage();
        }
        gains[index] = Pictures.gain;
        return null;
    }

    private static String loadBarks(String path) {
        try {
            int old = materialTexture[BARK];
            materialTexture[BARK] = Pictures.loadLayers(path);
            GL11.glDeleteTextures(old);
        } catch (IOException e) {
            return e.getMessage();
        }
        materialGain[BARK] = Pictures.gain;
        return null;
    }

    private static void filter(int filter) {
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    private int target(int color) {
        int target = GL30.glGenFramebuffers();
        if (making != null) {
            making.targets.add(target);
        }
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
        return target;
    }

    private static String complete(String what) {
        int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        return status == GL_FRAMEBUFFER_COMPLETE ? null : "the driver would not make the " + what + " target (status 0x" + Integer.toHexString(status) + ")";
    }

    /** Reads off the error flags already set, the game's among them, so that an error read afterwards is this frame's own. */
    private static void clearErrors() {
        for (int flag = 0; flag < GL_ERROR_FLAGS && GL11.glGetError() != 0; flag++) {
        }
    }
}

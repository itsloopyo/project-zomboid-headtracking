package com.cameraunlock.zomboid;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Locale;
import org.lwjgl.opengl.GL11;
import zombie.GameTime;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.Core;
import zombie.core.properties.PropertyContainer;
import zombie.core.textures.ColorInfo;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureDraw;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoChunkMap;
import zombie.iso.IsoDirections;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.areas.IsoBuilding;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.fboRenderChunk.FBORenderChunk;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteGrid;
import zombie.iso.sprite.IsoSpriteInstance;
import zombie.util.list.PZArrayList;

/**
 * One frame of the world as the first-person view draws it.
 *
 * build() runs on the game's logic thread and reads the loaded map: every
 * square the game holds in memory that the view can face becomes triangles
 * textured with the sprites the game has loaded for it. What hides a square
 * is what stands in front of it, as in any first-person picture, not whether
 * the character has been there. render() runs later on
 * the render thread and only draws what build() wrote, so it never touches the
 * map. The scenes are reused in turn, and there are more of them than the game
 * keeps frames in flight between its two threads.
 */
final class WorldScene extends TextureDraw.GenericDrawer {
    /**
     * How far the view draws, in tiles. The game keeps at most 19 chunks of 8 tiles a side
     * around the player, so nothing further than this from the player is ever in memory.
     */
    private static final int RADIUS = 80;
    /** How far the aim is traced. */
    static final float REACH = 30f;
    static final int CHUNK_TILES = 8;
    /** Half the diagonal of a chunk, rounded up: how far a chunk reaches from its centre. */
    private static final float CHUNK_REACH = 6f;
    /** A chunk this near the eye is drawn whichever way the view faces: the eye may be inside it. */
    private static final float ALWAYS_DRAWN = 16f;
    /** How much wider than the picture's corners the drawn cone is, so nothing pops in at the edges. */
    private static final float CONE_MARGIN = (float) Math.toRadians(8);
    /** The world fades into the sky from this share of the way to the edge of the loaded map. */
    private static final float FOG_START = 0.78f;
    /**
     * How far the near plane's corners are from the eye. A lean stops LeanClamp.SKIN off a wall,
     * and the wall is only drawn while that is further than this.
     */
    static final float NEAR_CORNER_DISTANCE = 0.08f;
    private static final float FAR = 200f;
    private static final float EXPOSURE_INDOORS = 1.25f;
    private static final float EYE_ADAPT_SECONDS = 0.8f;
    private static final float EXPOSURE_MOST = 3.5f;
    private static final float EXPOSURE_MOST_AT_NIGHT = 1.5f;
    /**
     * How far a sprite hung on a wall or laid on a floor stands off the one under it, so the two
     * do not fight for depth. The depth buffer tells two surfaces apart less well the further
     * off they are, by the square of the distance, so the gap grows the same way: at 80 tiles
     * it is about a pixel wide.
     */
    private static final float STAND_OFF = 0.004f;
    private static final float STAND_OFF_PER_TILE_SQUARED = 1e-5f;

    /** The mesh of things drawn without a texture. No texture has this name. */
    private static final int PLAIN = 0;
    /** A character the game has faded out of the view is not drawn. */
    private static final float CHARACTER_SHOWN_ALPHA = 0.5f;

    private static final int GL_VIEWPORT = 0x0BA2;
    /** The part of the game's picture the frame being drawn is written to. Render thread. */
    private static final int[] viewport = new int[4];

    private static final WorldScene[] scenes = {new WorldScene(), new WorldScene(), new WorldScene(), new WorldScene()};
    private static int nextScene;

    private static volatile float lastDrawMillis;
    /** Width over height of the buffer the last frame was drawn into, 0 until a frame has been drawn. */
    private static volatile float drawnAspect;
    private static float loggedTanHalfFieldOfView, loggedAspect;
    private static float eyeOpen = -1f;
    private static float shaded;
    /** How much further the eye opens deep in a wood than on open ground. */
    private static final float WOODS_OPEN = 1.1f;
    private static long lastAdapt;

    /**
     * One chunk's triangles as they were last built. Most of the world does not change from
     * one frame to the next, so a chunk is only built again when it is due: every frame near
     * the eye, where a door may open or a light come on, and every so many frames further off.
     *
     * A build is never changed once made. The render thread may still be drawing a frame that
     * shows it, so a chunk that is due gets a new one and the old goes back to be used again
     * only when no frame in flight shows it.
     */
    static final class ChunkMeshes {
        /**
         * The build's triangles as runs of vertices that wear one texture in one tint, one run
         * after another: first what is drawn as it stands, a pixel of a sprite being there or
         * not, then what is laid over a floor and mixed into it. For each run its texture, its
         * tint and how many floats long it is.
         */
        int[] runTexture = new int[32], runTint = new int[32], runFloats = new int[32];
        int runs, solidRuns;
        /** How many floats the runs come to. */
        int floats;
        /**
         * The box that holds every vertex of the runs as it stands, before the wind moves any:
         * the least and the greatest x, y and height. Of no meaning for a build with no runs.
         */
        float minX, minY, minUp, maxX, maxY, maxUp;
        /**
         * The vertices themselves, on loan from VertexLoans, until the graphics card has them:
         * null from then on, and for a build with no triangles. The logic thread sets it when
         * it has filled the loan, and the render thread gives the loan back and clears it.
         */
        volatile FloatBuffer vertices;
        /** The furniture the mod has models of, each drawn from its model where it stands. */
        final Models.Instances placed = new Models.Instances();
        private int wx, wy;
        private int builtFrame;
        /** The frame the build was last made in or found still to be what a new one would be. */
        private int checkedFrame;
        /** Where the eye was when the build was made, and which way lay to the right of the view. */
        private float eyeX, eyeY, eyeUp, rightX, rightY;
        /** Whether a square of the build was so near the eye that its cards lie across the view. */
        private boolean acrossView;
        /** Whether what the build read of the map was summed as it was made, and the sum: see signature. */
        private boolean signed;
        private long signature;
        /** The frame from which the chunk's builds are summed again, after one was found changed with the eye still. */
        private int summedFrom;
        /** The frames in flight that show this build, and one more while it is the chunk's latest. */
        private int users;
        /**
         * Raised each time the build has been filled. The render thread reads it before it
         * reads the build's runs and vertices, which is what lets it see all of what the logic
         * thread wrote, and from it tells a build the graphics card holds as it was from one it
         * holds as it is.
         */
        volatile int version;
        /**
         * Whether a block of ChunkBuffers' buffer holds this build's vertices. The render
         * thread alone writes it, and writes it last of what it changes in a build, so the
         * logic thread, reading it to tell whether there is a block to free, sees the rest too.
         */
        volatile boolean held;
        /** The block: which filling of the build it holds, where it is, how long, and the build's place in ChunkBuffers' list of builds with a block. */
        int heldVersion, heldAt, heldFloats, heldIndex;

        /**
         * Makes this build of what two sets hold, the solid and the laid, and leaves the sets
         * as they were. Run on the logic thread, on a build no frame in flight shows.
         */
        void fill(SpriteGeometry.MeshSet solid, SpriteGeometry.MeshSet laid) {
            // The last filling's vertices are still here when no frame that showed it was ever drawn.
            scrap();
            runs = 0;
            floats = 0;
            minX = minY = minUp = Float.POSITIVE_INFINITY;
            maxX = maxY = maxUp = Float.NEGATIVE_INFINITY;
            note(solid);
            solidRuns = runs;
            note(laid);
            if (floats > 0) {
                FloatBuffer loan = VertexLoans.take(floats);
                copy(solid, loan);
                copy(laid, loan);
                loan.flip();
                vertices = loan;
            }
            version++;
        }

        private void note(SpriteGeometry.MeshSet set) {
            for (int i = 0; i < set.used.size(); i++) {
                SpriteGeometry.Mesh mesh = set.used.get(i);
                if (mesh.floats == 0) {
                    continue;
                }
                if (runs == runTexture.length) {
                    runTexture = java.util.Arrays.copyOf(runTexture, runs * 2);
                    runTint = java.util.Arrays.copyOf(runTint, runs * 2);
                    runFloats = java.util.Arrays.copyOf(runFloats, runs * 2);
                }
                runTexture[runs] = mesh.texture;
                runTint[runs] = mesh.tint;
                runFloats[runs] = mesh.floats;
                runs++;
                floats = Math.addExact(floats, mesh.floats);
                float[] from = mesh.vertices;
                float lowX = minX, lowY = minY, lowUp = minUp, highX = maxX, highY = maxY, highUp = maxUp;
                for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                    float x = from[at], y = from[at + 1], up = from[at + 2];
                    lowX = Math.min(lowX, x);
                    highX = Math.max(highX, x);
                    lowY = Math.min(lowY, y);
                    highY = Math.max(highY, y);
                    lowUp = Math.min(lowUp, up);
                    highUp = Math.max(highUp, up);
                }
                minX = lowX;
                minY = lowY;
                minUp = lowUp;
                maxX = highX;
                maxY = highY;
                maxUp = highUp;
            }
        }

        private static void copy(SpriteGeometry.MeshSet set, FloatBuffer into) {
            for (int i = 0; i < set.used.size(); i++) {
                SpriteGeometry.Mesh mesh = set.used.get(i);
                into.put(mesh.vertices, 0, mesh.floats);
            }
        }

        /** Gives back what a build that will not be filled again has on loan. Run on the logic thread, on a build no frame in flight shows. */
        void scrap() {
            FloatBuffer last = vertices;
            if (last != null) {
                VertexLoans.give(last);
                vertices = null;
            }
        }

        int triangles() {
            return floats / (SpriteGeometry.VERTEX_FLOATS * 3);
        }
    }

    /** A chunk nearer the eye than this is built every frame. */
    private static final float BUILT_EVERY_FRAME = 20f;
    private static final float BUILT_OFTEN = 44f;
    /** Chunks within this of the eye are drawn whichever way the view faces: what is behind the eye still casts a shadow into the picture. */
    private static final float CASTS_SHADOWS = 40f;
    /** The latest build of each chunk, by the low bits of its position. The loaded map is at most 19 chunks a side, so no two of its chunks share a place. */
    private static final int TABLE_SIDE = 64;
    private static final ChunkMeshes[] latest = new ChunkMeshes[TABLE_SIDE * TABLE_SIDE];
    private static final java.util.ArrayDeque<ChunkMeshes> spare = new java.util.ArrayDeque<>();
    /** Builds no chunk is using, kept to be filled again: as many as a turn of the head brings into view at once. */
    private static final int MOST_SPARE = 96;
    private static final int FORGET_EVERY_FRAMES = 120;
    /**
     * A build found unchanged is still made again after this many frames. The sum of what a
     * build reads leaves out what it is told by the roofs, the wall finishes and the sprite
     * colours, which are worked out over several frames as the map round a chunk loads, and
     * a far chunk has always shown those up to this late.
     */
    private static final int KEPT_UNCHANGED_FRAMES = 30;
    /** Where the eye was in the frame before this one, and whether it is in the same place now. */
    private static float lastEyeX = Float.NaN, lastEyeY, lastEyeUp;
    private static boolean eyeStill;
    /** The loaded map as it was when the last frame was built: its first chunk each way and how many chunks a side it is. */
    private static int mapWx, mapWy, mapSide;
    private static int frames;

    /** The chunk builds this frame draws. */
    private final ArrayList<ChunkMeshes> shown = new ArrayList<>();
    /**
     * Where the chunk being built puts its triangles: what is drawn as it stands, and what is
     * laid over a floor, the grass that runs out into a path, a stain, a rug's fringe. The game
     * paints the second with soft edges that fade into the floor under them, so they are drawn
     * after everything solid and mixed into it by how see-through each pixel is.
     *
     * One pair serves every build. A build keeps only what it is filled with, and that only
     * until the graphics card has it: a pair kept by each build was two copies of every chunk
     * in sight, each with the room of the fullest chunk it had ever held.
     */
    private static final SpriteGeometry.MeshSet solid = new SpriteGeometry.MeshSet(), laid = new SpriteGeometry.MeshSet();
    private Models.Instances placed;
    private final SpriteGeometry geometry = new SpriteGeometry();
    private final SpriteGeometry.Frame frame = new SpriteGeometry.Frame();

    private final ViewCamera.Pose camera = new ViewCamera.Pose();
    /**
     * Set for a scene that is seen from far overhead: the camera it is drawn from, the tangent
     * of half its field of view and how far off it is. The scene is still built round `camera`,
     * which stands where the player does, so everything that goes by nearness is as it would
     * be for someone standing there.
     */
    private final ViewCamera.Pose overheadCamera = new ViewCamera.Pose();
    private boolean overhead;
    private float overheadTan, overheadAway;
    /** How far in front of and behind the place looked at the picture reaches, in tiles. */
    private static final float OVERHEAD_DEPTH = 160f;
    /** The distance the place looked at is taken to be at, for what the picture does by distance. */
    private static final float OVERHEAD_RANGE = 12f;

    void seenFromOverhead(ViewCamera.Pose from, float tanHalfFieldOfView, float away) {
        overheadCamera.set(from);
        overheadTan = tanHalfFieldOfView;
        overheadAway = away;
        overhead = true;
    }
    /** The aim the held weapon is drawn on: see FirstPerson. */
    private final ViewCamera.Pose aim = new ViewCamera.Pose();
    private float rightX, rightY;
    /** The direction an upright sprite on the square being added runs along. */
    private float cardRightX, cardRightY;
    private float tanHalfFieldOfView;

    /** The characters this frame draws, for the aim to be traced against. */
    float[] characterX = new float[16], characterY = new float[16], characterBase = new float[16];
    int characters;
    private final SkyState sky = new SkyState();
    private float fogEnd;
    /** How much light the eye lets in this frame: see adaptEye. */
    private float exposure;
    /** How much of its opening-up the eye is refused at full night. */
    private static final float NIGHT_HELD_BACK = 0.5f;
    private IsoPlayer viewer;
    private IsoCell cell;
    private int playerIndex;
    private int lowestLevel;
    /** The highest floor built: all of them, but for a viewer indoors seen from overhead. */
    private static int topLevel = Integer.MAX_VALUE;
    /** The building the viewer is in, a square wider all round, in tiles. */
    private static int cutMinX, cutMinY, cutMaxX, cutMaxY;

    /** Whether what is over a square is left out so that the viewer indoors can be seen from overhead. */
    private static boolean cutAway(int x, int y) {
        return topLevel != Integer.MAX_VALUE && x >= cutMinX && x < cutMaxX && y >= cutMinY && y < cutMaxY;
    }
    /** How much of a wall is left standing when the walls are cut down, in tiles. */
    private static final float WALL_LEFT = 0.7f;
    private float cutFrom = Float.MAX_VALUE;
    private int chunksBuilt, chunksKept;
    /** Whether a square of the chunk being built is so near the eye that its cards lie across the view. */
    private boolean acrossView;
    private int boxes;
    private int furnished;
    private int spreadFaces;
    private int lifted;
    private int chunksDrawn, squaresLoaded, squaresSeen, sprites, triangles;
    private int roofsNorthSouth, roofsWestEast, roofsFlat, roofsUnclear, roofsFolded;
    /** The gap between two sprites on one plane of the square being added. */
    private float standOff;
    private int floorLayer, westLayer, northLayer, eastLayer, southLayer;

    /**
     * The light of the squares the character has seen that are out of doors, counted over this
     * frame, and the light most of them had in the last frame that had any: see OpenLight. A
     * square the character has not seen has no light of its own yet: the game holds it black
     * until it is seen.
     */
    private final OpenLight seenOutside = new OpenLight();
    /** The tally as it stood before a chunk was read only to be summed. */
    private final OpenLight seenBefore = new OpenLight();
    private final float[] openLight = new float[3];
    private static float unseenRed = -1f, unseenGreen, unseenBlue;
    private final ColorInfo unseenLight = new ColorInfo();
    private final float[] corner = new float[2];
    private final float[] box = new float[4];
    private int overLayer;
    private int remakes;
    private static final int REMAKES_A_FRAME = 12;
    private boolean wiredHere;
    private final java.util.IdentityHashMap<IsoSprite, Boolean> meshes = new java.util.IdentityHashMap<>();
    private static final int MESH_CHANGES = 12;
    private boolean wallNorth, wallWest;
    /** Whether anything of a wall's kind, a fence or a low wall too, stands on the north and the west edge of the square being added. */
    private boolean edgeNorth, edgeWest;
    private static final float SWITCH_WIDEST = 12f;
    private static final java.util.HashMap<Long, Integer> strung = new java.util.HashMap<>();
    private static final int WIRE_SPAN = 12;
    private static final float WIRE_HEIGHT = 0.5f, WIRE_SAG = 0.03f, WIRE_THICK = 0.035f;
    private float steepest;
    private String steepestSaid = "", roofName = "";
    /** How far a roof runs out past its wall, and how deep the board along its end is, in tiles. */
    private static final float EAVE = 0.22f, FASCIA = 0.12f, RAKE = 0.14f, GUTTER = 0.07f, PIPE = 0.035f;
    private final float[] eave = new float[12];
    /**
     * The cap along a ridge or down a hip, in tiles: how far it reaches either side of the line,
     * half the width of its level crown, how far the crown stands over the line and how far its
     * edges stand off the roof.
     */
    private static final float CAP_HALF = 0.11f, CAP_CROWN = 0.035f, CAP_RISE = 0.03f, CAP_CLEAR = 0.008f;
    private final float[] folded = new float[9];
    private final float[] heldLift = new float[4];
    /** How far under a roof tile its plain sheet lies, in levels. */
    private static final float UNDER_ROOF = 0.03f;
    /** Half the thickness of a wall, in tiles. */
    private static final float WALL_HALF = 0.04f;
    private static final float HUNG_CLEAR = 0.012f;
    /** The game gives the height of a surface in pixels of its unscaled sprites, of which a level is this many. */
    private static final float SURFACE_PIXELS_PER_LEVEL = 96f;
    private static final int TABLE_HIGHEST = 48;
    private static final int TALL_SURFACE = 60;
    private static final float CUPBOARD_DEEP = 0.55f;
    private static final int LONG_TUFTS = 13;
    private final Flora flora = new Flora();
    private static final int WOOD_SPAN = CHUNK_TILES + 2 * Flora.WOOD_RING;
    /** Which squares of the chunk being built, and of Flora.WOOD_RING more on every side, hold a tree. */
    private final boolean[] treed = new boolean[WOOD_SPAN * WOOD_SPAN];
    /** How deep in fallen leaves each corner of the chunk being built is: see Flora.wooded. */
    private final float[] wooded = new float[Terrain.CORNERS * Terrain.CORNERS];
    private final float[] littered = new float[4];
    /** How many of its neighbours' grounds spreadGround last laid over a square. */
    private int spreadLayers;
    /** How far from the eye each thing Flora makes is still made, in tiles. Past TRUNK_NEAR a bole is a plain post, and past ROCK_NEAR a boulder has a quarter of its faces. */
    private static final float FERN_REACH = 22f, FERN_NEAR = 12f, FALLEN_REACH = 10f, FLOWER_REACH = 14f, TRUNK_NEAR = 18f, TRUNK_REACH = 60f,
            ROCK_NEAR = 30f, BUSH_REACH = 40f;
    /** How far in from the edge of a lawn its grass takes to reach its full height, in tiles. */
    private static final float LAWN_EDGE = 0.9f;
    /** How far from a bush's middle its outer cards stand, in tiles. */
    private static final float BUSH_SPREAD = 0.16f;
    /** What was read off each tree's sprite: how thick its bole is and how tall, in tiles. Empty for a sprite with no bole to read. */
    private final java.util.IdentityHashMap<IsoSprite, float[]> boles = new java.util.IdentityHashMap<>();
    private static final float[] NO_BOLE = new float[0];
    /** A bole is no thinner or thicker than this, in tiles, whatever its sprite says. */
    private static final float BOLE_THINNEST = 0.05f, BOLE_THICKEST = 0.4f;
    private static final float GRASS_SPILL = 0.35f;
    /** Whether the square being added has no floor. */
    private boolean hangsInAir;
    /** How far down the wall of a quay goes from its top: past the water's surface. */
    private static final float QUAY_DEPTH = 0.6f;
    private static final float CARD_LAYER = 0.035f;
    /** The height of a bed, in levels. */
    private static final float BED_HEIGHT = 0.2f;
    /** How much of a tree's trunk sprite, from the ground up, is its bole. */
    private static final float BOLE = 0.42f;
    /** How far from the eye grass stands up, in tiles, and how many tufts a square of it has. Chunks this near are built every frame. */
    private static final float GRASS_REACH = 16f;
    private static final int TUFTS = 14;
    private static final float TUFT_HALF_WIDTH = 0.3f;
    /** The ground of the chunk being built and of the ring of squares round it, laid out as the lights are. */
    private final byte[] groundKind = new byte[LIGHT_SPAN * LIGHT_SPAN];
    private final IsoObject[] groundObject = new IsoObject[LIGHT_SPAN * LIGHT_SPAN];
    private final Texture[] groundTexture = new Texture[LIGHT_SPAN * LIGHT_SPAN];
    private final int[] groundRank = new int[LIGHT_SPAN * LIGHT_SPAN];
    private final java.util.IdentityHashMap<IsoSprite, int[]> ranks = new java.util.IdentityHashMap<>();
    private final int[] spreading = new int[8];
    private final float[] cover = new float[4];
    /** The place in its chunk of the square being added. */
    private int waterLocalX, waterLocalY;
    /** How far in from a wall's end its sprite is looked over for its first painted pixel, in unscaled pixels. */
    private static final float MOST_UNPAINTED = 5f;
    /** The height, in levels, a wall's sprite is looked over at: under the sill of a window. */
    private static final float SEAL_HEIGHT = 0.15f;
    /** A chunk's squares and the ring of squares round them. */
    private static final int LIGHT_SPAN = CHUNK_TILES + 2;
    private final float[] lightRed = new float[LIGHT_SPAN * LIGHT_SPAN], lightGreen = new float[LIGHT_SPAN * LIGHT_SPAN],
            lightBlue = new float[LIGHT_SPAN * LIGHT_SPAN];
    private final byte[] lightOpen = new byte[LIGHT_SPAN * LIGHT_SPAN];
    private final float[] heldRed = new float[4], heldGreen = new float[4], heldBlue = new float[4];
    private float buildMillis;

    /** The square being added. */
    private IsoGridSquare here;
    /** The plain outside wall the far skin of the wall last asked about can show, or null: see farTint. */
    private WallFinish.Facing farFacing;
    /** Whether the wall being added has a door or a window in it. */
    private boolean wallOpens;
    /** A wall whose sprite is painted this high up, in levels, is a whole wall and not a low one. */
    private static final float WHOLE_WALL = 0.9f;
    private final SpriteGeometry.Frame held = new SpriteGeometry.Frame();
    /** Where in the chunk's lights the square beyond the wall last asked about is, or -1 when it is the same place as this side. */
    private int farLight;
    /** The roof the tile being added is part of, or null. */
    private Roofs.Roof roof;
    private float roofU, roofV;
    /** How far up a wall's sprite an opening is looked for, in levels: above a sill and below a lintel. */
    private static final float OPENING_ROW = 0.5f;
    /** The narrowest gap in a wall's sprite that is an opening, as a share of the wall's length. */
    private static final float NARROWEST_OPENING = 0.2f;
    /** How far inside an opening's corners its sprite has to be clear for it to be a rectangle, in unscaled pixels. */
    private static final float OPENING_CORNER = 1.5f;
    /** No door is lower than this, in levels. */
    private static final float LOWEST_DOOR = 0.5f;
    /** Where the opening in each wall sprite seen so far is: see openingOf. */
    private static final java.util.IdentityHashMap<Texture, float[]> openings = new java.util.IdentityHashMap<>();
    private final float[] reveal = new float[12];

    private WorldScene() {}

    static WorldScene next() {
        nextScene = (nextScene + 1) % scenes.length;
        return scenes[nextScene];
    }

    String describe() {
        return String.format(Locale.ROOT, "chunks %d built %d kept %d squares %d seen %d sprites %d triangles %d build %.2f ms draw %.2f ms"
                        + " exposure %.2f tan half fov %.4f aspect %.4f fog end %.1f roofs n-s %d w-e %d flat %d unclear %d boxes %d furniture %d spread %d lifted %d models %d | %s",
                chunksDrawn, chunksBuilt, chunksKept, squaresLoaded, squaresSeen, sprites, triangles, buildMillis, lastDrawMillis, exposure,
                tanHalfFieldOfView, drawnAspect, fogEnd, roofsNorthSouth, roofsWestEast, roofsFlat, roofsUnclear, boxes, furnished, spreadFaces, lifted, Characters.drawnLast(), sky.describe()) + " | " + Pipeline.describeShadows() + " | " + ChunkBuffers.describe()
                + String.format(Locale.ROOT, ", builds are filled from %d meshes with room for %.0f MB", solid.kept() + laid.kept(), (solid.room() + laid.room()) * Float.BYTES / 1048576f)
                + " | noted " + Characters.described + " | steepest roof " + steepestSaid + " | roofs folded " + roofsFolded;
    }

    static float drawnAspect() {
        return drawnAspect;
    }

    /** The tangent of half the vertical field of view this frame is drawn with. */
    float tanHalfFieldOfView() {
        return tanHalfFieldOfView;
    }

    /**
     * @param view               the eye and orientation the frame is drawn from, head tracking included
     * @param aimed              the aim, which the weapon in hand is held on
     * @param tanHalfFieldOfView the tangent of half the vertical field of view the frame is drawn with
     */
    void build(IsoPlayer player, ViewCamera.Pose view, ViewCamera.Pose aimed, float tanHalfFieldOfView) {
        long start = System.nanoTime();
        camera.set(view);
        aim.set(aimed);
        overhead = false;
        this.tanHalfFieldOfView = tanHalfFieldOfView;
        float flat = (float) Math.sqrt(view.forwardX * view.forwardX + view.forwardY * view.forwardY);
        if (flat > 1e-3f) {
            rightX = -view.forwardY / flat;
            rightY = view.forwardX / flat;
        }
        characters = 0;
        for (int i = 0; i < shown.size(); i++) {
            release(shown.get(i));
        }
        shown.clear();
        frames++;
        chunksDrawn = 0;
        chunksBuilt = 0;
        chunksKept = 0;
        eyeStill = view.eyeX == lastEyeX && view.eyeY == lastEyeY && view.eyeUp == lastEyeUp;
        lastEyeX = view.eyeX;
        lastEyeY = view.eyeY;
        lastEyeUp = view.eyeUp;
        remakes = 0;
        boxes = 0;
        furnished = 0;
        spreadFaces = 0;
        lifted = 0;
        squaresLoaded = 0;
        squaresSeen = 0;
        sprites = 0;
        triangles = 0;
        roofsNorthSouth = 0;
        roofsWestEast = 0;
        roofsFlat = 0;
        roofsUnclear = 0;
        roofsFolded = 0;

        viewer = player;
        float daylight = GameTime.getInstance().getAmbient();
        sky.capture();
        if (unseenRed < 0f) {
            unseenRed = unseenGreen = unseenBlue = daylight;
        }
        exposure = adaptEye(player);
        playerIndex = player.getIndex();
        cell = IsoWorld.instance.currentCell;
        mainsOn = IsoWorld.instance.isHydroPowerOn();
        IsoChunkMap map = cell.getChunkMap(playerIndex);
        int level = (int) Math.floor(player.getZ());
        // From an upper floor the ground is in the picture; from the ground, only the level under it.
        int lowest = level > 0 ? 0 : level - 1;
        lowestLevel = lowest;
        // Seen from overhead, whoever is indoors is under a roof and the floors above. The
        // game leaves those out and so does the picture, and the walls of the floor are cut
        // low: see Pipeline.cutAbove.
        IsoGridSquare stoodOn = player.getCurrentSquare();
        boolean indoors = FirstPerson.overhead() && stoodOn != null && !stoodOn.isOutside();
        int top = indoors ? level : Integer.MAX_VALUE;
        int minX = 0, minY = 0, maxX = 0, maxY = 0;
        if (indoors) {
            zombie.iso.BuildingDef def = stoodOn.getBuilding() == null ? null : stoodOn.getBuilding().getDef();
            if (def == null) {
                top = Integer.MAX_VALUE;
            } else {
                minX = def.getX() - 1;
                minY = def.getY() - 1;
                maxX = def.getX() + def.getW() + 1;
                maxY = def.getY() + def.getH() + 1;
            }
        }
        indoors = top != Integer.MAX_VALUE;
        if (top != topLevel || minX != cutMinX || minY != cutMinY || maxX != cutMaxX || maxY != cutMaxY) {
            topLevel = top;
            cutMinX = minX;
            cutMinY = minY;
            cutMaxX = maxX;
            cutMaxY = maxY;
            forgetAll();
        }
        cutFrom = indoors ? level * ViewCamera.LEVEL_HEIGHT + Terrain.lift(player.getX(), player.getY(), level) + WALL_LEFT : Float.MAX_VALUE;
        frame.scale = Core.tileScale;
        if (unseenRed < 0f) {
            unseenRed = unseenGreen = unseenBlue = daylight;
        }
        unseenLight.r = unseenRed;
        unseenLight.g = unseenGreen;
        unseenLight.b = unseenBlue;
        seenOutside.clear();

        // The loaded map is a square of chunks around the player, and it ends in nothing. The
        // picture fades into the sky before the nearest of its four edges, so the edge is never seen.
        int tiles = IsoChunkMap.chunkGridWidth * CHUNK_TILES;
        float toEdge = Math.min(Math.min(player.getX() - map.getWorldXMinTiles(), map.getWorldXMinTiles() + tiles - player.getX()),
                Math.min(player.getY() - map.getWorldYMinTiles(), map.getWorldYMinTiles() + tiles - player.getY()));
        fogEnd = Math.min(toEdge, RADIUS);
        float cosCone = cosDrawnCone(view, flat);
        // Looking straight up or down the view faces no way along the ground, and nothing is left out.
        float forwardX = cosCone > -1f ? view.forwardX / flat : 0f, forwardY = cosCone > -1f ? view.forwardY / flat : 0f;

        mapWx = Math.floorDiv(map.getWorldXMinTiles(), CHUNK_TILES);
        mapWy = Math.floorDiv(map.getWorldYMinTiles(), CHUNK_TILES);
        mapSide = IsoChunkMap.chunkGridWidth;
        if (frames % FORGET_EVERY_FRAMES == 0) {
            forgetFar();
        }
        for (int gridX = 0; gridX < IsoChunkMap.chunkGridWidth; gridX++) {
            for (int gridY = 0; gridY < IsoChunkMap.chunkGridWidth; gridY++) {
                IsoChunk chunk = map.getChunk(gridX, gridY);
                if (chunk == null) {
                    continue;
                }
                int chunkX = chunk.wx * CHUNK_TILES, chunkY = chunk.wy * CHUNK_TILES;
                float toChunkX = chunkX + CHUNK_TILES / 2f - view.eyeX, toChunkY = chunkY + CHUNK_TILES / 2f - view.eyeY;
                float distance = (float) Math.sqrt(toChunkX * toChunkX + toChunkY * toChunkY);
                if (distance > fogEnd + CHUNK_REACH) {
                    continue;
                }
                if (distance > CASTS_SHADOWS && toChunkX * forwardX + toChunkY * forwardY < cosCone * distance - CHUNK_REACH) {
                    continue;
                }
                chunksDrawn++;
                int slot = (chunk.wx & (TABLE_SIDE - 1)) * TABLE_SIDE + (chunk.wy & (TABLE_SIDE - 1));
                ChunkMeshes current = latest[slot];
                boolean missing = current == null || current.wx != chunk.wx || current.wy != chunk.wy;
                // A chunk that is only due to be made again can wait a frame when many fall due
                // together: its old build is still good enough to show. Without this the frames
                // on which dozens fall due are several times as long as the rest.
                boolean due = !missing && frames - current.checkedFrame >= buildEvery(distance, slot)
                        && (distance < CHUNK_TILES * 1.5f || remakes++ < REMAKES_A_FRAME);
                // The eye's place is in every build: which way a card faces, how far apart
                // two sprites on one plane stand, how much detail a square is given. While it
                // moves a chunk that is due is made again. While it is still, what the build
                // would read of the map is summed first, and a chunk whose sum is what its
                // build's was is kept: a new build would be the same triangles.
                long read = 0;
                int summedFrom = missing ? frames : current.summedFrom;
                boolean summed = eyeStill && (missing || due) && frames >= summedFrom;
                if (summed) {
                    seenOutside.copyTo(seenBefore);
                    int seen = squaresSeen;
                    read = signature(chunk, chunkX, chunkY, lowest);
                    boolean sameEye = due && current.signed && current.eyeX == view.eyeX && current.eyeY == view.eyeY && current.eyeUp == view.eyeUp
                            && (!current.acrossView || current.rightX == rightX && current.rightY == rightY);
                    if (sameEye && current.signature == read && frames - current.builtFrame < KEPT_UNCHANGED_FRAMES) {
                        // The squares it read count towards this frame's light as a build's would.
                        current.checkedFrame = frames;
                        chunksKept++;
                        due = false;
                    } else {
                        // The build reads them again.
                        seenBefore.copyTo(seenOutside);
                        squaresSeen = seen;
                        if (sameEye && current.signature != read) {
                            // Something on the chunk changes from frame to frame: a light that
                            // flickers, a sprite that moves. Summing it each time it is due
                            // would be work for nothing, so it is left for a while.
                            summedFrom = frames + KEPT_UNCHANGED_FRAMES;
                        }
                    }
                }
                if (missing || due) {
                    ChunkMeshes fresh = spare.isEmpty() ? new ChunkMeshes() : spare.pop();
                    solid.clear();
                    laid.clear();
                    fresh.placed.clear();
                    fresh.wx = chunk.wx;
                    fresh.wy = chunk.wy;
                    fresh.builtFrame = frames;
                    fresh.checkedFrame = frames;
                    fresh.eyeX = view.eyeX;
                    fresh.eyeY = view.eyeY;
                    fresh.eyeUp = view.eyeUp;
                    fresh.rightX = rightX;
                    fresh.rightY = rightY;
                    fresh.signed = summed;
                    fresh.signature = read;
                    fresh.summedFrom = summedFrom;
                    fresh.users = 1;
                    acrossView = false;
                    placed = fresh.placed;
                    chunksBuilt++;
                    for (int z = Math.max(lowest, chunk.getMinLevel()); z <= chunk.getMaxLevel(); z++) {
                        readLights(chunk, chunkX, chunkY, z);
                        if (z == 0) {
                            readWoods(chunk, chunkX, chunkY);
                        }
                        float[] ground = Terrain.corners(cell, chunk.wx, chunk.wy);
                        float[] water = Terrain.water(chunk.wx, chunk.wy);
                        for (int localX = 0; localX < CHUNK_TILES; localX++) {
                            for (int localY = 0; localY < CHUNK_TILES; localY++) {
                                IsoGridSquare square = chunk.getGridSquare(localX, localY, z);
                                if (square == null || z > topLevel && cutAway(chunkX + localX, chunkY + localY)) {
                                    continue;
                                }
                                squaresLoaded++;
                                cornerLights(localX, localY);
                                int corner = localX * Terrain.CORNERS + localY;
                                // Water lies at its own height, and a street below its kerb.
                                byte kind = z == 0 ? groundKind[(localX + 1) * LIGHT_SPAN + localY + 1] : Terrain.MADE;
                                float[] heights = kind == Terrain.WATER ? water : ground;
                                float drop = kind == Terrain.STREET ? Terrain.KERB : 0f;
                                geometry.lift[0] = heights[corner] - drop;
                                geometry.lift[1] = heights[corner + Terrain.CORNERS] - drop;
                                geometry.lift[2] = heights[corner + Terrain.CORNERS + 1] - drop;
                                geometry.lift[3] = heights[corner + 1] - drop;
                                if (geometry.lift[0] != 0f) {
                                    lifted++;
                                }
                                addSquare(square, chunkX + localX, chunkY + localY, z, localX, localY);
                            }
                        }
                    }
                    addBacks(chunk, chunkX, chunkY);
                    fresh.acrossView = acrossView;
                    fresh.fill(solid, laid);
                    if (current != null) {
                        release(current);
                    }
                    latest[slot] = fresh;
                    current = fresh;
                }
                current.users++;
                shown.add(current);
            }
        }
        // Where each standing character is, for the aim to be traced against. Read from the
        // game's own list every frame: characters move, and a chunk's build may be frames old.
        for (IsoMovingObject mover : cell.getObjectList()) {
            if (mover instanceof IsoGameCharacter character && character != viewer
                    && character.getAlpha(playerIndex) > CHARACTER_SHOWN_ALPHA && !character.isInvisible()) {
                float toX = character.getX() - view.eyeX, toY = character.getY() - view.eyeY;
                if (toX * toX + toY * toY <= REACH * REACH) {
                    addCharacter(character);
                }
            }
        }
        if (!seenOutside.isEmpty()) {
            seenOutside.commonest(openLight);
            unseenRed = openLight[0];
            unseenGreen = openLight[1];
            unseenBlue = openLight[2];
        }
        for (int i = 0; i < shown.size(); i++) {
            triangles += shown.get(i).triangles() + shown.get(i).placed.triangles();
        }
        buildMillis = (System.nanoTime() - start) / 1e6f;
    }

    private static void release(ChunkMeshes meshes) {
        if (--meshes.users != 0) {
            return;
        }
        ChunkBuffers.forget(meshes);
        if (spare.size() < MOST_SPARE) {
            spare.push(meshes);
        } else {
            meshes.scrap();
        }
    }

    /** Whether a build is of a chunk the loaded map holds, or of one a step outside it, which the next step back brings in again. */
    private static boolean near(ChunkMeshes meshes) {
        return meshes.wx >= mapWx - 1 && meshes.wx <= mapWx + mapSide && meshes.wy >= mapWy - 1 && meshes.wy <= mapWy + mapSide;
    }

    /** Lets go of every build: for when what all of them were built on has changed. */
    static void forgetAll() {
        for (int slot = 0; slot < latest.length; slot++) {
            if (latest[slot] != null) {
                release(latest[slot]);
                latest[slot] = null;
            }
        }
    }

    /**
     * Lets go of every build of a chunk the player has left behind, and of the room the sets
     * and the loans have that nothing since the last time used. The table is far wider than
     * the loaded map so that no two loaded chunks share a place in it, and a build stayed in
     * its place until another chunk took it: driving across the map, every chunk ever passed
     * was kept, grass and all, until there was no memory left. A build kept is a block of the
     * graphics card's memory kept, so none is kept for a chunk the map no longer holds.
     */
    private static void forgetFar() {
        for (int slot = 0; slot < latest.length; slot++) {
            ChunkMeshes held = latest[slot];
            if (held != null && !near(held)) {
                latest[slot] = null;
                release(held);
            }
        }
        solid.clear();
        laid.clear();
        solid.trim();
        laid.trim();
        VertexLoans.trim();
    }

    /**
     * What a build of a chunk would read of the map, summed: the light, the ground and the
     * trees of each of its squares and of the squares round it, as readLights and readWoods
     * leave them, the land's heights, and on each square every object's sprite, the texture
     * it shows this frame, where it is drawn from, what is laid over it, whether a door is
     * open, and what lies dead there. Two sums that are equal mean, for the same eye, the
     * same triangles.
     *
     * It reads the lights as a build does, so the squares it sees count towards the frame's
     * light as they would have.
     */
    private long signature(IsoChunk chunk, int chunkX, int chunkY, int lowest) {
        int from = Math.max(lowest, chunk.getMinLevel()), to = chunk.getMaxLevel();
        long sum = sum(sum(sum(0x9E3779B97F4A7C15L, Float.floatToRawIntBits(frame.scale)), from), to);
        float[] ground = Terrain.corners(cell, chunk.wx, chunk.wy);
        float[] water = Terrain.water(chunk.wx, chunk.wy);
        for (int i = 0; i < ground.length; i++) {
            sum = sum(sum(sum, Float.floatToRawIntBits(ground[i])), Float.floatToRawIntBits(water[i]));
        }
        for (int z = from; z <= to; z++) {
            readLights(chunk, chunkX, chunkY, z);
            if (z == 0) {
                readWoods(chunk, chunkX, chunkY);
                for (int i = 0; i < treed.length; i++) {
                    sum = sum(sum, treed[i] ? 1 : 0);
                }
            }
            for (int i = 0; i < LIGHT_SPAN; i++) {
                for (int j = 0; j < LIGHT_SPAN; j++) {
                    int at = i * LIGHT_SPAN + j;
                    sum = sum(sum, lightOpen[at]);
                    if (lightOpen[at] < 0) {
                        continue;
                    }
                    sum = sum(sum(sum(sum, Float.floatToRawIntBits(lightRed[at])), Float.floatToRawIntBits(lightGreen[at])), Float.floatToRawIntBits(lightBlue[at]));
                    if (z == 0) {
                        sum = sum(sum(sum(sum, groundKind[at]), groundRank[at]), groundTexture[at] == null ? 0 : groundTexture[at].getID());
                    }
                    boolean inChunk = i >= 1 && i <= CHUNK_TILES && j >= 1 && j <= CHUNK_TILES;
                    IsoGridSquare square = inChunk ? chunk.getGridSquare(i - 1, j - 1, z) : cell.getGridSquare(chunkX + i - 1, chunkY + j - 1, z);
                    sum = sum(sum, square.getRoomID());
                    PZArrayList<IsoObject> objects = square.getObjects();
                    sum = sum(sum, objects.size());
                    for (int k = 0; k < objects.size(); k++) {
                        IsoObject object = objects.get(k);
                        IsoDirections dir = object.getDir();
                        sum = sum(sum(sum(sum(sum, shown(object.getSprite(), dir)), Float.floatToRawIntBits(object.offsetX)), Float.floatToRawIntBits(object.offsetY)),
                                shown(object.getOverlaySprite(), dir));
                        ArrayList<IsoSpriteInstance> attached = object.getAttachedAnimSprite();
                        if (attached != null) {
                            for (int over = 0; over < attached.size(); over++) {
                                sum = sum(sum, shown(attached.get(over).parentSprite, dir));
                            }
                        }
                        if (object instanceof IsoDoor door) {
                            sum = sum(sum, door.IsOpen() ? 2 : 1);
                        } else if (object instanceof IsoThumpable thumpable && thumpable.isDoor()) {
                            sum = sum(sum, thumpable.IsOpen() ? 4 : 3);
                        }
                    }
                    ArrayList<IsoMovingObject> bodies = square.getStaticMovingObjects();
                    for (int k = 0; k < bodies.size(); k++) {
                        IsoMovingObject body = bodies.get(k);
                        sum = sum(sum(sum(sum, Float.floatToRawIntBits(body.getX())), Float.floatToRawIntBits(body.getY())), Float.floatToRawIntBits(body.getZ()));
                    }
                }
            }
        }
        return sum;
    }

    /** A sprite and the texture it shows this frame turned that way, as one number. */
    private static long shown(IsoSprite sprite, IsoDirections dir) {
        if (sprite == null) {
            return 0;
        }
        Texture texture = sprite.getTextureForCurrentFrame(dir);
        return (long) System.identityHashCode(sprite) << 32 | (texture == null ? 0 : texture.getID() & 0xffffffffL);
    }

    private static long sum(long sum, long value) {
        return (sum ^ value) * 0x100000001B3L;
    }

    /**
     * How many frames a chunk's build is kept before it is made again. The chunk's place in
     * the table is mixed in, or every far chunk built on the frame the view came on would fall
     * due together ever after.
     */
    private static int buildEvery(float distance, int slot) {
        if (distance < BUILT_EVERY_FRAME) {
            // The chunk the eye is in and those round it every frame: their cards turn fastest
            // as the eye moves. The rest of the near ones every second or third.
            return distance < CHUNK_TILES * 1.5f ? 1 : 2 + slot % 2;
        }
        return distance < BUILT_OFTEN ? 6 + slot % 4 : 18 + slot % 9;
    }

    /**
     * The cosine of the widest angle, seen from above, between the way the view faces and
     * anything in the picture: the angle to the picture's corners, which a pitched or rolled
     * view swings out sideways, and a margin. -1, which leaves nothing out, when the view
     * looks so far up or down that a corner points behind it or the picture has no shape yet.
     */
    private float cosDrawnCone(ViewCamera.Pose view, float flat) {
        float aspect = drawnAspect;
        if (aspect == 0f || flat <= 1e-3f || FirstPerson.overhead()) {
            return -1f;
        }
        // A driver with a mirror sees behind as well.
        if (FirstPerson.seated && Cabins.wheel(FirstPerson.seatedIn) != null) {
            return -1f;
        }
        float widest = 0f;
        for (int i = 0; i < 4; i++) {
            float across = ((i & 1) == 0 ? -1f : 1f) * tanHalfFieldOfView * aspect;
            float along = (i < 2 ? -1f : 1f) * tanHalfFieldOfView;
            float x = view.forwardX + view.rightX * across + view.upX * along;
            float y = view.forwardY + view.rightY * across + view.upY * along;
            float length = (float) Math.sqrt(x * x + y * y);
            float cos = (x * view.forwardX + y * view.forwardY) / (length * flat);
            if (length < 1e-3f || cos <= 0f) {
                return -1f;
            }
            widest = Math.max(widest, (float) Math.acos(Math.min(1f, cos)));
        }
        widest += CONE_MARGIN;
        return widest >= Math.PI ? -1f : (float) Math.cos(widest);
    }

    /**
     * An eye lets in less light in the open at noon than in a room, and takes a moment to
     * change when its owner walks through a door. What it is adapted to is where the character
     * stands, in or out of doors, and how strong the sun or moon is.
     */
    private float adaptEye(IsoPlayer player) {
        IsoGridSquare under = player.getCurrentSquare();
        // Seen from overhead the picture is of the open air, whatever roof the viewer is under.
        float target = FirstPerson.overhead() || under == null || under.isOutside() ? 1f : 0f;
        long now = System.nanoTime();
        float elapsed = lastAdapt == 0 ? 1f : Math.min((now - lastAdapt) / 1e9f, 1f);
        lastAdapt = now;
        eyeOpen = eyeOpen < 0f ? target : eyeOpen + (target - eyeOpen) * (1f - (float) Math.exp(-elapsed / EYE_ADAPT_SECONDS));
        sky.outdoors = eyeOpen;
        float strongest = Math.max(sky.lightRed, Math.max(sky.lightGreen, sky.lightBlue));
        // Out of doors by day, sunlit ground comes out about as bright as the sprite was painted.
        // As the game's own light for the open ground falls, towards night, the eye opens up to
        // it, though never so far that night stops being dark.
        float ground = (float) Math.pow((unseenRed + unseenGreen + unseenBlue) / 3f, 2.2);
        float outdoors = Math.min(1.4f / (0.8f * ground + 1.83f * (float) Math.pow(strongest, 2.2)), EXPOSURE_MOST);
        // An eye in the dark does not get back what the day had: it sees less, and that is
        // what makes a lit window or a pair of headlights the brightest thing in the night.
        outdoors *= 1f - NIGHT_HELD_BACK * sky.night;
        float here = 1f;
        if (under != null) {
            ColorInfo light = under.lighting[player.getIndex()].lightInfo();
            here = (float) Math.pow((light.r + light.g + light.b) / 3f, 2.2);
        }
        // By day an eye indoors opens up to a dim room. At night it has less to open with: an
        // unlit room stays dark, and one with a lamp on is seen by the lamp.
        float most = EXPOSURE_MOST + (EXPOSURE_MOST_AT_NIGHT - EXPOSURE_MOST) * sky.night;
        // Among trees most of what the eye sees is in shade, and it opens up to that as it
        // does indoors, though less.
        int trees = 0;
        if (under != null) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    IsoGridSquare near = IsoWorld.instance.currentCell.getGridSquare(under.getX() + dx, under.getY() + dy, under.getZ());
                    if (near != null && near.HasTree()) {
                        trees++;
                    }
                }
            }
        }
        float wooded = Math.min(trees / 9f, 1f);
        shaded += (wooded - shaded) * (1f - (float) Math.exp(-elapsed / EYE_ADAPT_SECONDS));
        outdoors *= 1f + WOODS_OPEN * shaded;
        float indoors = Math.min(EXPOSURE_INDOORS / Math.max(here, 1e-3f), most);
        return indoors + (outdoors - indoors) * eyeOpen;
    }

    /**
     * The light of every square of one level of a chunk and of the ring of squares round it,
     * into lightRed, lightGreen, lightBlue and lightOpen, a LIGHT_SPAN wide grid whose first
     * row and column are the squares just outside the chunk. lightOpen is 1 out of doors, 0
     * under a roof and -1 where there is no square.
     */
    private void readLights(IsoChunk chunk, int chunkX, int chunkY, int z) {
        for (int i = 0; i < LIGHT_SPAN; i++) {
            for (int j = 0; j < LIGHT_SPAN; j++) {
                boolean inChunk = i >= 1 && i <= CHUNK_TILES && j >= 1 && j <= CHUNK_TILES;
                IsoGridSquare square = inChunk ? chunk.getGridSquare(i - 1, j - 1, z) : cell.getGridSquare(chunkX + i - 1, chunkY + j - 1, z);
                int at = i * LIGHT_SPAN + j;
                if (z == 0) {
                    groundKind[at] = Terrain.kind(square);
                    groundObject[at] = square == null ? null : square.getFloor();
                    groundTexture[at] = textureOf(groundObject[at]);
                    groundRank[at] = groundKind[at] == Terrain.NATURAL ? rank(groundObject[at].getSprite()) : 0;
                }
                if (square == null) {
                    lightOpen[at] = -1;
                    continue;
                }
                // Read from the lighting engine, which holds every loaded square. getLightInfo is
                // a copy the game's own render takes of it, and only for the squares its camera
                // has on screen.
                ColorInfo light = square.lighting[playerIndex].lightInfo();
                boolean outside = square.isOutside();
                if (!square.isSeen(playerIndex)) {
                    light = unseenLight;
                } else if (inChunk) {
                    squaresSeen++;
                    if (outside) {
                        seenOutside.add(light.r, light.g, light.b);
                    }
                }
                lightRed[at] = light.r;
                lightGreen[at] = light.g;
                lightBlue[at] = light.b;
                lightOpen[at] = (byte) (outside ? 1 : 0);
                // The game has no lamp in a canopy. A lit one lights what is under it.
                if (outside && z >= 0 && underLitCanopy(chunkX + i - 1, chunkY + j - 1, z)) {
                    lightRed[at] = Soffits.under(light.r, Soffits.GLOW_RED);
                    lightGreen[at] = Soffits.under(light.g, Soffits.GLOW_GREEN);
                    lightBlue[at] = Soffits.under(light.b, Soffits.GLOW_BLUE);
                }
            }
        }
    }

    /**
     * Sets the frame's light for the square at (localX, localY) of the chunk readLights last
     * read. Each corner takes the mean of the squares that meet at it, so two squares agree
     * about the corner they share and the light has no step between them. Squares on the other
     * side of a roof's edge are left out of the mean: a room is not lit by the garden through
     * its wall.
     */
    private void cornerLights(int localX, int localY) {
        int own = (localX + 1) * LIGHT_SPAN + localY + 1;
        frame.red = lightRed[own];
        frame.green = lightGreen[own];
        frame.blue = lightBlue[own];
        frame.open = lightOpen[own];
        for (int corner = 0; corner < 4; corner++) {
            int i = localX + (corner == 1 || corner == 2 ? 1 : 0), j = localY + (corner >= 2 ? 1 : 0);
            float red = 0f, green = 0f, blue = 0f;
            int count = 0;
            for (int di = 0; di <= 1; di++) {
                for (int dj = 0; dj <= 1; dj++) {
                    int at = (i + di) * LIGHT_SPAN + j + dj;
                    if (lightOpen[at] == lightOpen[own]) {
                        red += lightRed[at];
                        green += lightGreen[at];
                        blue += lightBlue[at];
                        count++;
                    }
                }
            }
            frame.cornerRed[corner] = red / count;
            frame.cornerGreen[corner] = green / count;
            frame.cornerBlue[corner] = blue / count;
        }
    }

    /** Finds the trees on and round a chunk, and from them how deep in fallen leaves each of its corners is. */
    private void readWoods(IsoChunk chunk, int chunkX, int chunkY) {
        for (int i = 0; i < WOOD_SPAN; i++) {
            for (int j = 0; j < WOOD_SPAN; j++) {
                int localX = i - Flora.WOOD_RING, localY = j - Flora.WOOD_RING;
                boolean inChunk = localX >= 0 && localX < CHUNK_TILES && localY >= 0 && localY < CHUNK_TILES;
                IsoGridSquare square = inChunk ? chunk.getGridSquare(localX, localY, 0) : cell.getGridSquare(chunkX + localX, chunkY + localY, 0);
                treed[i * WOOD_SPAN + j] = square != null && square.HasTree();
            }
        }
        Flora.wooded(treed, CHUNK_TILES, wooded);
    }

    /** How deep in fallen leaves the point (a, b) of a square of the chunk being built is. */
    private float woodedAt(int localX, int localY, float a, float b) {
        int at = localX * Terrain.CORNERS + localY;
        return (wooded[at] * (1f - a) + wooded[at + Terrain.CORNERS] * a) * (1f - b)
                + (wooded[at + 1] * (1f - a) + wooded[at + Terrain.CORNERS + 1] * a) * b;
    }

    private boolean isLawn(int at) {
        return groundKind[at] == Terrain.NATURAL && lightOpen[at] == 1 && groundTexture[at] != null && (groundRank[at] - 1) % 64 >= 33;
    }

    private void addOver(IsoObject object, IsoSprite under, IsoSprite over, boolean isFloor, int x, int y, int z) {
        if (over == null) {
            return;
        }
        Texture texture = over.getTextureForCurrentFrame(object.getDir());
        if (texture == null || texture.getID() <= 0 || texture.getWidth() <= 0 || texture.getHeight() <= 0) {
            return;
        }
        frameOf(object, texture);
        standOn(object);
        frame.material = under.getType() == IsoObjectType.tree ? LEAVES : frame.material;
        sprites++;
        // Leaves stand on the same card as their trunk. Each layer is moved a little towards
        // the eye from the one under it, or the two would be drawn through each other.
        overLayer++;
        float toX = camera.eyeX - (x + 0.5f), toY = camera.eyeY - (y + 0.5f);
        float away = Math.max((float) Math.sqrt(toX * toX + toY * toY), 0.5f);
        float nearer = overLayer * (CARD_LAYER + 4f * standOff);
        geometry.nudgeX = toX / away * nearer;
        geometry.nudgeY = toY / away * nearer;
        if (!isFloor && addHung(over, texture, x, y, z)) {
            return;
        }
        // A poster or a notice the game has pinned to a wall is laid over the wall's own sprite,
        // and stood as that sprite stands it goes onto both walls of a corner piece, on one of
        // them slewed. It says which wall it was painted for, and goes on that one. Only what
        // has a front: a stain or ivy has none and is the wall's own.
        if (!isFloor && over.getProperties().has(IsoFlagType.WallOverlay) && over.getFacing() != null) {
            int wall = wallOf(over);
            if (wall == WallHung.NORTH || wall == WallHung.WEST) {
                SpriteGeometry.Mesh mesh = solid.get(texture.getID());
                if (wall == WallHung.WEST) {
                    addEdge(mesh, texture, false, false, x, y, z, westLayer++ * standOff, 0f, 0f, 1f);
                } else {
                    addEdge(mesh, texture, false, false, x, y, z, 0f, northLayer++ * standOff, 1f, 0f);
                }
                return;
            }
        }
        addSprite(solid.get(texture.getID()), object, under, texture, isFloor, x, y, z);
    }

    /**
     * Which wall of its square a sprite laid over a wall was painted for: the one the game
     * says it is attached to, or failing that the one behind it, facing the way it does.
     * NONE for one that says neither.
     */
    private static int wallOf(IsoSprite over) {
        PropertyContainer properties = over.getProperties();
        boolean north = properties.has(IsoFlagType.attachedN), west = properties.has(IsoFlagType.attachedW);
        if (north != west) {
            return north ? WallHung.NORTH : WallHung.WEST;
        }
        return behind(over.getFacing());
    }

    /** The wall of its square that is behind something facing that way. */
    private static int behind(IsoDirections facing) {
        return facing == IsoDirections.S ? WallHung.NORTH : facing == IsoDirections.E ? WallHung.WEST
                : facing == IsoDirections.N ? WallHung.SOUTH : facing == IsoDirections.W ? WallHung.EAST : WallHung.NONE;
    }

    private void frameOf(IsoObject object, Texture texture) {
        frameOf(frame, object, texture);
    }

    /**
     * Sets how high above its square's floor the object's sprite is to be stood: the game
     * draws a thing higher up the picture than its frame has it by an offset the thing
     * carries, a lamp put on a higher table than the one it was painted for.
     */
    private void standOn(IsoObject object) {
        geometry.raised = object.getRenderYOffset() / SURFACE_PIXELS_PER_LEVEL;
    }

    static void frameOf(SpriteGeometry.Frame frame, IsoObject object, Texture texture) {
        frame.originU = object.offsetX;
        frame.originV = object.offsetY;
        // Where a tree's sprite stands on its square the game works out as it draws the tree,
        // and writes into the tree: a tree its own view has not drawn still has whatever was
        // there before, and its crown comes out lying in the road. The same sum is done here.
        IsoSprite tree = object.getSprite();
        if (tree != null && tree.getType() == IsoObjectType.tree && tree.getName() != null) {
            String name = tree.getName();
            frame.originU = FBORenderChunk.FLOOR_WIDTH / 2f;
            frame.originV = FBORenderChunk.PIXELS_PER_LEVEL;
            if (name.contains("JUMBOXXL")) {
                frame.originU = FBORenderChunk.JUMBO_XXL_WIDTH / 2f;
                frame.originV = FBORenderChunk.JUMBO_XXL_HEIGHT - FBORenderChunk.FLOOR_HEIGHT;
            } else if (name.contains("JUMBOXL")) {
                frame.originU = FBORenderChunk.JUMBO_XL_WIDTH / 2f;
                frame.originV = FBORenderChunk.JUMBO_XL_HEIGHT - FBORenderChunk.FLOOR_HEIGHT;
            } else if (name.contains("JUMBO")) {
                frame.originU = FBORenderChunk.JUMBO_L_WIDTH / 2f;
                frame.originV = FBORenderChunk.JUMBO_L_HEIGHT - FBORenderChunk.FLOOR_HEIGHT;
            }
        }
        frame.left = texture.getOffsetX();
        frame.top = texture.getOffsetY();
        frame.width = texture.getWidth();
        frame.height = texture.getHeight();
        frame.uStart = texture.getXStart();
        frame.uEnd = texture.getXEnd();
        frame.vStart = texture.getYStart();
        frame.vEnd = texture.getYEnd();
    }

    private static Texture textureOf(IsoObject floor) {
        IsoSprite sprite = floor == null ? null : floor.getSprite();
        Texture texture = sprite == null ? null : sprite.getTextureForCurrentFrame(floor.getDir());
        return texture == null || texture.getID() <= 0 || texture.getWidth() <= 0 || texture.getHeight() <= 0 ? null : texture;
    }

    /**
     * The game lays its ground a square at a time, so where grass meets earth the line between
     * them is a staircase of square corners. Ground does not end like that. Here grass, earth
     * and sand spread over the edge of whatever lies next to them: the neighbour's ground is
     * laid over this square too, all of it at the corners the neighbour touches and none at the
     * others, and the picture breaks the fade between into an uneven edge.
     *
     * Of two kinds of open ground the one whose texture has the higher name spreads over the
     * other, which is as good a rule as any and is the same from both sides. Ground spreads
     * over a road or a path and out over the edge of water, never the other way, and nothing
     * spreads indoors.
     */
    private void spreadGround(int x, int y, int localX, int localY) {
        int own = (localX + 1) * LIGHT_SPAN + localY + 1;
        spreadLayers = 0;
        // A street ends at its kerb, and the verge stands behind it: nothing grows out over the tarmac.
        if (groundKind[own] == Terrain.UNKNOWN || groundKind[own] == Terrain.STREET || lightOpen[own] != 1) {
            return;
        }
        int mine = groundRank[own];
        int spread = 0;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                int at = own + di * LIGHT_SPAN + dj;
                if (!spreads(at, own, mine)) {
                    continue;
                }
                int name = groundRank[at];
                boolean done = false;
                for (int k = 0; k < spread; k++) {
                    done |= spreading[k] == name;
                }
                if (done) {
                    continue;
                }
                spreading[spread++] = name;
                for (int corner = 0; corner < 4; corner++) {
                    int i = localX + (corner == 1 || corner == 2 ? 1 : 0), j = localY + (corner >= 2 ? 1 : 0);
                    boolean touches = false;
                    for (int ci = 0; ci <= 1; ci++) {
                        for (int cj = 0; cj <= 1; cj++) {
                            int touching = (i + ci) * LIGHT_SPAN + j + cj;
                            touches |= touching != own && spreads(touching, own, mine) && groundRank[touching] == name;
                        }
                    }
                    cover[corner] = touches ? 1f : 0f;
                }
                frameOf(groundObject[at], groundTexture[at]);
                geometry.cover = cover;
                spreadFaces++;
                geometry.addFace(laid.get(groundTexture[at].getID()), frame, x, y, 0, 0f, 0f, (floorLayer + spread) * standOff / ViewCamera.LEVEL_HEIGHT,
                        1f, 0f, 0f, 0f, 1f, 0f);
                geometry.cover = null;
            }
        }
        spreadLayers = spread;
    }

    /**
     * Which ground a sprite of open ground is, as a number that also says what spreads over
     * what. The game's sheets of ground hold one kind of ground to a row of sixteen sprites,
     * so every sprite of a row is the same ground and gets the same number. Grass, the rows
     * after the first and before the fifth, outranks bare ground: it is grass that grows out
     * over earth and sand, in tufts, and not the earth that creeps over the grass.
     */
    private int rank(IsoSprite sprite) {
        int[] known = ranks.get(sprite);
        if (known == null) {
            String name = sprite.getName();
            int cut = name.lastIndexOf('_');
            int row = Integer.parseInt(name.substring(cut + 1)) / 16;
            known = new int[] {1 + (name.substring(0, cut).hashCode() & 0xffff) * 64 + (row >= 1 && row <= 3 ? 32 + row : row)};
            ranks.put(sprite, known);
        }
        return known[0];
    }

    /**
     * Grass the game paints flat on the ground stands up near the eye: tufts of blades on every
     * square of open grass, each the colour of the ground it stands on. Where each tuft stands
     * and how it is turned comes from the square's own place on the map, so the same tufts are
     * there every time the square is built.
     */
    private void growGrass(int x, int y, int localX, int localY) {
        int own = (localX + 1) * LIGHT_SPAN + localY + 1;
        if (!isLawn(own)) {
            return;
        }
        // A tuft is wider than nothing, and one near the square's edge hangs over it. Over more
        // grass that is as it should be. Over a path it is untidy, and through the wall of a
        // house it is wrong, so on a side where the lawn ends the tufts keep in from the edge.
        boolean west = isLawn(own - LIGHT_SPAN), east = isLawn(own + LIGHT_SPAN), north = isLawn(own - 1), south = isLawn(own + 1);
        float keepIn = TUFT_HALF_WIDTH + 0.06f;
        float lowA = west ? 0f : keepIn, highA = east ? 1f : 1f - keepIn, lowB = north ? 0f : keepIn, highB = south ? 1f : 1f - keepIn;
        frameOf(groundObject[own], groundTexture[own]);
        SpriteGeometry.Mesh mesh = laid.get(groundTexture[own].getID());
        for (int tuft = 0; tuft < TUFTS; tuft++) {
            int hash = x * 73856093 ^ y * 19349663 ^ tuft * 83492791;
            hash = (hash ^ hash >>> 13) * 0x5bd1e995;
            hash ^= hash >>> 15;
            float a = lowA + (highA - lowA) * (hash & 0xff) / 255f, b = lowB + (highB - lowB) * (hash >>> 8 & 0xff) / 255f;
            // A lawn does not stop at a line. Towards a path or a wall, and under trees, its
            // grass is shorter, thinner and browner, and the further in or under the more so.
            float edge = 1f;
            if (!west) {
                edge = Math.min(edge, a / LAWN_EDGE);
            }
            if (!east) {
                edge = Math.min(edge, (1f - a) / LAWN_EDGE);
            }
            if (!north) {
                edge = Math.min(edge, b / LAWN_EDGE);
            }
            if (!south) {
                edge = Math.min(edge, (1f - b) / LAWN_EDGE);
            }
            float vigour = (1f - 0.85f * woodedAt(localX, localY, a, b)) * (0.2f + 0.8f * edge * edge * (3f - 2f * edge));
            if (Flora.unit(Flora.mix(x, y, 31 + tuft), 0) > 0.3f + 0.7f * vigour) {
                continue;
            }
            double turn = (hash >>> 16 & 0xff) / 255.0 * Math.PI;
            float height = (0.16f + 0.26f * (hash >>> 24 & 0xff) / 255f) * (0.55f + 0.45f * vigour);
            geometry.addTuft(mesh, frame, x, y, a, b, (float) Math.cos(turn), (float) Math.sin(turn), TUFT_HALF_WIDTH, height, SpriteGeometry.GRASS, vigour);
        }
    }

    /**
     * What stands in and lies on open ground besides its grass. Under trees the ground is
     * fallen leaves, thick in a wood and a thin scatter round one tree on a lawn, with ferns
     * growing out of them and a leaf or a twig standing proud. A lawn has flowers in drifts,
     * and here and there a weed. The leaves are laid as far as the land is drawn; the rest
     * only near the eye, where it can be told from the ground it stands on.
     */
    private void strew(int x, int y, int localX, int localY, float away2) {
        int own = (localX + 1) * LIGHT_SPAN + localY + 1;
        if (groundKind[own] != Terrain.NATURAL || lightOpen[own] != 1) {
            return;
        }
        float deepest = 0f;
        for (int corner = 0; corner < 4; corner++) {
            int i = localX + (corner == 1 || corner == 2 ? 1 : 0), j = localY + (corner >= 2 ? 1 : 0);
            // Leaves are swept off a road and a path, and none fall indoors: a corner that touches either has none.
            boolean open = true;
            for (int ci = 0; ci <= 1; ci++) {
                for (int cj = 0; cj <= 1; cj++) {
                    int touching = (i + ci) * LIGHT_SPAN + j + cj;
                    open &= groundKind[touching] == Terrain.NATURAL && lightOpen[touching] == 1;
                }
            }
            littered[corner] = open ? wooded[i * Terrain.CORNERS + j] : 0f;
            deepest = Math.max(deepest, littered[corner]);
        }
        SpriteGeometry.Mesh cards = laid.get(PLAIN);
        if (deepest > 0.02f) {
            Flora.addLitter(cards, x, y, geometry.lift, (floorLayer + spreadLayers + 1) * standOff, littered, frame);
        }
        float deep = (littered[0] + littered[1] + littered[2] + littered[3]) / 4f;
        boolean tree = treed[(localX + Flora.WOOD_RING) * WOOD_SPAN + localY + Flora.WOOD_RING];
        if (away2 < FERN_REACH * FERN_REACH && !tree && isLawn(own)) {
            int ferns = Flora.ferns(x, y, deep);
            for (int fern = 0; fern < ferns; fern++) {
                int seed = Flora.mix(x, y, 40 + fern);
                float a = 0.15f + 0.7f * Flora.unit(seed, 0), b = 0.15f + 0.7f * Flora.unit(seed, 8);
                int fronds = away2 < FERN_NEAR * FERN_NEAR ? 5 + (seed >>> 24 & 1) : 4;
                Flora.addFern(cards, x + a, y + b, geometry.liftAt(a, b), 0.4f + 0.3f * Flora.unit(seed, 16), fronds, seed, frame);
            }
        }
        if (away2 < FALLEN_REACH * FALLEN_REACH) {
            int fallen = Flora.fallen(x, y, deep);
            for (int leaf = 0; leaf < fallen; leaf++) {
                int seed = Flora.mix(x, y, 50 + leaf);
                float a = 0.05f + 0.9f * Flora.unit(seed, 0), b = 0.05f + 0.9f * Flora.unit(seed, 8);
                double turn = Flora.unit(seed, 16) * 2.0 * Math.PI;
                float tall = Flora.unit(seed, 24);
                if (leaf == 0 && (seed & 3) == 0) {
                    Flora.addLeaf(cards, x + a, y + b, geometry.liftAt(a, b) + 0.03f, 0.22f + 0.2f * tall, 0.035f, turn, Math.toRadians(3.0 + 5.0 * tall),
                            Flora.TWIG, 0f, frame);
                } else {
                    float length = 0.09f + 0.05f * tall;
                    Flora.addLeaf(cards, x + a, y + b, geometry.liftAt(a, b) + 0.02f, length, length * 0.7f, turn, Math.toRadians(8.0 + 22.0 * tall),
                            Flora.FALLEN_ORANGE + (seed >>> 20) % 3, 0f, frame);
                }
            }
        }
        if (away2 < FLOWER_REACH * FLOWER_REACH && isLawn(own) && deep < 0.35f) {
            addFlowers(cards, x, y, 0.1f, 0.14f, 0.022f);
            if (Flora.weed(x, y)) {
                int seed = Flora.mix(x, y, 70);
                float a = 0.2f + 0.6f * Flora.unit(seed, 0), b = 0.2f + 0.6f * Flora.unit(seed, 8);
                for (int leaf = 0; leaf < Flora.WEED_LEAVES; leaf++) {
                    int each = Flora.mix(seed, leaf, 71);
                    float length = 0.13f + 0.07f * Flora.unit(each, 0);
                    Flora.addLeaf(cards, x + a, y + b, geometry.liftAt(a, b) + 0.01f, length, length * 0.5f,
                            (leaf + 0.6 * Flora.unit(each, 8)) * 2.0 * Math.PI / Flora.WEED_LEAVES, Math.toRadians(15.0 + 25.0 * Flora.unit(each, 16)),
                            Flora.WEED, 0.05f, frame);
                }
            }
        }
    }

    /** The flowers of a square of grass: heads at least lowest above the ground and up to taller more, the smallest of them least across. */
    private void addFlowers(SpriteGeometry.Mesh cards, int x, int y, float lowest, float taller, float least) {
        int flowers = Flora.flowers(x, y);
        for (int flower = 0; flower < flowers; flower++) {
            int seed = Flora.mix(x, y, 60 + flower);
            float a = 0.08f + 0.84f * Flora.unit(seed, 0), b = 0.08f + 0.84f * Flora.unit(seed, 8);
            Flora.addFlower(cards, x + a, y + b, geometry.liftAt(a, b) + lowest + taller * Flora.unit(seed, 16), least * (1f + 0.65f * Flora.unit(seed, 24)),
                    Flora.unit(seed, 4) * 2.0 * Math.PI, Flora.flowerKind(x, y, flower), frame);
        }
    }

    /**
     * Where a square stands above the one beside it there is a face between them: the kerb
     * along a street, and the wall of a quay or a boardwalk over water.
     */
    private void addEdges(int x, int y, int localX, int localY) {
        int own = (localX + 1) * LIGHT_SPAN + localY + 1;
        byte mine = groundKind[own];
        if (mine == Terrain.WATER || mine == Terrain.STREET || mine == Terrain.UNKNOWN) {
            return;
        }
        float[] lift = geometry.lift;
        addEdge(groundKind[own - LIGHT_SPAN], mine, x, y + 0.5f, 0f, 1f, (lift[0] + lift[3]) / 2f);
        addEdge(groundKind[own + LIGHT_SPAN], mine, x + 1f, y + 0.5f, 0f, 1f, (lift[1] + lift[2]) / 2f);
        addEdge(groundKind[own - 1], mine, x + 0.5f, y, 1f, 0f, (lift[0] + lift[1]) / 2f);
        addEdge(groundKind[own + 1], mine, x + 0.5f, y + 1f, 1f, 0f, (lift[3] + lift[2]) / 2f);
    }

    private void addEdge(byte beside, byte mine, float x, float y, float alongX, float alongY, float top) {
        SpriteGeometry.Mesh mesh = solid.get(PLAIN);
        // The colour given is the light on it. What colour the thing is, the picture knows by
        // what it is made of.
        if (beside == Terrain.STREET) {
            mesh.open = frame.open + 32f * KERB_STONE;
            SpriteGeometry.addPlainUpright(mesh, x, y, top, alongX, alongY, 0.5f, -Terrain.KERB - 0.02f, 0f, frame.red, frame.green, frame.blue);
        } else if (beside == Terrain.WATER && mine != Terrain.NATURAL) {
            mesh.open = frame.open + 32f * QUAY;
            SpriteGeometry.addPlainUpright(mesh, x, y, top, alongX, alongY, 0.5f, -QUAY_DEPTH, 0f, frame.red, frame.green, frame.blue);
        }
    }

    private boolean spillsTo(int at) {
        return groundKind[at] == Terrain.NATURAL && lightOpen[at] == 1;
    }

    private boolean growLongGrass(int x, int y) {
        int own = (waterLocalX + 1) * LIGHT_SPAN + waterLocalY + 1;
        if (groundTexture[own] == null) {
            return false;
        }
        float originU = frame.originU, originV = frame.originV, left = frame.left, top = frame.top, width = frame.width, height = frame.height;
        float uStart = frame.uStart, uEnd = frame.uEnd, vStart = frame.vStart, vEnd = frame.vEnd;
        frameOf(groundObject[own], groundTexture[own]);
        float lowA = spillsTo(own - LIGHT_SPAN) ? -GRASS_SPILL : 0.12f, highA = spillsTo(own + LIGHT_SPAN) ? 1f + GRASS_SPILL : 0.88f;
        float lowB = spillsTo(own - 1) ? -GRASS_SPILL : 0.12f, highB = spillsTo(own + 1) ? 1f + GRASS_SPILL : 0.88f;
        SpriteGeometry.Mesh mesh = laid.get(groundTexture[own].getID());
        for (int tuft = 0; tuft < LONG_TUFTS; tuft++) {
            int hash = x * 19349663 ^ y * 83492791 ^ tuft * 73856093;
            hash = (hash ^ hash >>> 13) * 0x5bd1e995;
            hash ^= hash >>> 15;
            // A clump of long grass is not the shape of the square the game put it on. Towards
            // open ground its tufts spill a third of a square past the edge, shorter the
            // further out they stand, so a patch has a ragged outline. Towards a road, a
            // path or a floor it keeps inside.
            float a = lowA + (highA - lowA) * (hash & 0xff) / 255f, b = lowB + (highB - lowB) * (hash >>> 8 & 0xff) / 255f;
            double turn = (hash >>> 16 & 0xff) / 255.0 * Math.PI;
            float out = Math.max(Math.max(-a, a - 1f), Math.max(-b, b - 1f));
            float tall = (0.45f + 0.5f * (hash >>> 24 & 0xff) / 255f) * (out > 0f ? 1f - 1.8f * out : 1f);
            float vigour = 1f - 0.7f * woodedAt(waterLocalX, waterLocalY, Math.clamp(a, 0f, 1f), Math.clamp(b, 0f, 1f));
            geometry.addTuft(mesh, frame, x, y, a, b, (float) Math.cos(turn), (float) Math.sin(turn), 0.34f, tall, SpriteGeometry.LONG_GRASS, vigour);
        }
        float toX = x + 0.5f - camera.eyeX, toY = y + 0.5f - camera.eyeY;
        if (toX * toX + toY * toY < FLOWER_REACH * FLOWER_REACH) {
            // Flowers in long grass stand as tall as it does, on stems lost among the blades.
            addFlowers(laid.get(PLAIN), x, y, 0.4f, 0.35f, 0.03f);
        }
        frame.originU = originU;
        frame.originV = originV;
        frame.left = left;
        frame.top = top;
        frame.width = width;
        frame.height = height;
        frame.uStart = uStart;
        frame.uEnd = uEnd;
        frame.vStart = vStart;
        frame.vEnd = vEnd;
        return true;
    }

    private static final float SHINGLES = 1f, PAINT = 2f, WOOD = 3f, EARTH = 4f, LEAVES = 5f, BARK = 6f, PLANKS = 7f, CARPET = 8f, CONCRETE = 9f, QUAY = 10f, TILE = 11f, WIRE = 12f, GLASS = 13f, LAMP = 14f, MESH = 15f;
    /** The mod's own kerb, of cast concrete. Its number is past those of what grows. */
    private static final float KERB_STONE = 64f;
    /** A window pane with a lamp burning in the room behind it. */
    private static final float LIT_GLASS = 65f;
    /** A wall of upright boards. */
    private static final float BOARDS = 52f;
    /** The cap along a ridge or a hip: the roof's own covering, which the picture draws a shade darker. */
    private static final float RIDGE = 53f;

    /** What a sprite that is no floor is made of: see materialOf. A wall the game says is of wood, on a sheet made for one place, is boarded. */
    static float materialOf(IsoSprite sprite) {
        float material = materialOf(sprite.getName(), false);
        if (material != 0f || sprite.getName() == null || !sprite.getName().startsWith("location_")) {
            return material;
        }
        PropertyContainer properties = sprite.getProperties();
        boolean wooden = "Wood".equals(properties.get("MaterialType")) || "Wood".equals(properties.get("Material"));
        return wooden && isWall(properties, sprite.getType()) ? BOARDS : 0f;
    }

    /** What a sprite's surface is made of, by the sheet it comes from. */
    private static float materialOf(String name, boolean isFloor) {
        if (name == null) {
            return 0f;
        }
        if (name.startsWith("roofs_")) {
            return SHINGLES;
        }
        if (name.startsWith("fixtures_windows") && !name.startsWith("fixtures_windows_curtains")) {
            return GLASS;
        }
        if (name.startsWith("lighting_")) {
            return LAMP;
        }
        if (name.startsWith("walls_exterior_wooden")) {
            return BOARDS;
        }
        if (name.startsWith("floors_interior_carpet") || name.startsWith("floors_rugs")) {
            return CARPET;
        }
        if (name.startsWith("walls_")) {
            return PAINT;
        }
        if (name.startsWith("fencing_") || name.startsWith("carpentry_")) {
            return PLANKS;
        }
        if (name.startsWith("e_") && !name.startsWith("e_newgrass")) {
            return BARK;
        }
        if (isFloor && name.startsWith("blends_natural")) {
            return EARTH;
        }
        // Furniture, by what it is mostly made of: timber, or cloth.
        if (name.startsWith("furniture_tables") || name.startsWith("furniture_storage") || name.startsWith("furniture_shelving")
                || name.startsWith("fixtures_counters")) {
            return WOOD;
        }
        // A panelled door is painted: under coarse grain its panels are lost.
        if (name.startsWith("fixtures_doors")) {
            return PAINT;
        }
        if (name.startsWith("furniture_seating_indoor") || name.startsWith("furniture_bedding") || name.startsWith("fixtures_windows_curtains")) {
            return CARPET;
        }
        if (isFloor && name.contains("tile") && !name.contains("wood")) {
            return TILE;
        }
        return isFloor && name.contains("wood") ? WOOD : 0f;
    }

    /**
     * The height of the sheet under a roof where the roof is at level z plus high: under the
     * roof, and never below the level itself, which is the ceiling of the room beneath. Where
     * the roof comes down to the level the sheet comes up to meet it along that line only. Held
     * a fixed step above the level instead, it lay in the roof's own plane along the eaves and
     * the two were drawn through each other.
     */
    private static float under(int z, float high) {
        return (z + high - Math.min(UNDER_ROOF, Math.max(high, 0f) * 0.5f)) * ViewCamera.LEVEL_HEIGHT;
    }

    /**
     * The game paints a short length of wire on a square here and there along a line of poles.
     * A wire runs the whole way: from each square that holds some, a cable is strung to the
     * next such square along each of the four ways a line can run on the map, sagging a little
     * between them.
     */
    private void stringWires(int x, int y, int z) {
        // Where the cable from this square goes is worked out once and kept: the lines do not
        // move, and working it out looks at a hundred squares.
        long key = ((long) x << 36) ^ ((long) y << 8) ^ z;
        Integer kept = strung.get(key);
        if (kept == null) {
            kept = wireFrom(x, y, z);
            // Kept only once the map is loaded as far as the search reaches: before that the
            // answer may be short of the truth.
            if (cell.getGridSquare(x - WIRE_SPAN, y - WIRE_SPAN, 0) != null && cell.getGridSquare(x + WIRE_SPAN, y + WIRE_SPAN, 0) != null
                    && cell.getGridSquare(x - WIRE_SPAN, y + WIRE_SPAN, 0) != null && cell.getGridSquare(x + WIRE_SPAN, y - WIRE_SPAN, 0) != null) {
                strung.put(key, kept);
            }
        }
        if (kept == 0) {
            return;
        }
        int way = kept >> 8, far = kept & 0xff;
        int stepX = way == 1 ? 0 : 1, stepY = way == 0 ? 0 : way == 3 ? -1 : 1;
        int toX = x + stepX * far, toY = y + stepY * far;
        float fromUp = (z + WIRE_HEIGHT) * ViewCamera.LEVEL_HEIGHT + Terrain.height(x + 0.5f, y + 0.5f);
        float toUp = (z + WIRE_HEIGHT) * ViewCamera.LEVEL_HEIGHT + Terrain.height(toX + 0.5f, toY + 0.5f);
        float midX = x + 0.5f + stepX * far / 2f, midY = y + 0.5f + stepY * far / 2f;
        float midUp = (fromUp + toUp) / 2f - WIRE_SAG * far;
        cable(x + 0.5f, y + 0.5f, fromUp, midX, midY, midUp);
        cable(midX, midY, midUp, toX + 0.5f, toY + 0.5f, toUp);
    }

    /**
     * Which way the cable from a square runs and how many squares on the next wire square is,
     * as way * 256 + far, or 0 for none. Several wires run side by side, so the squares that
     * hold wire are a band a few squares wide: the cable goes the way the band runs, which is
     * the way along which the most of them lie, and not across it from wire to wire.
     */
    private int wireFrom(int x, int y, int z) {
        int most = 0, along = 0;
        for (int way = 0; way < 4; way++) {
            int stepX = way == 1 ? 0 : 1, stepY = way == 0 ? 0 : way == 3 ? -1 : 1;
            int count = 0;
            for (int far = -WIRE_SPAN; far <= WIRE_SPAN; far++) {
                if (far != 0 && holdsWire(x + stepX * far, y + stepY * far, z)) {
                    count++;
                }
            }
            if (count > most) {
                most = count;
                along = way;
            }
        }
        if (most == 0) {
            return 0;
        }
        int stepX = along == 1 ? 0 : 1, stepY = along == 0 ? 0 : along == 3 ? -1 : 1;
        for (int far = 1; far <= WIRE_SPAN; far++) {
            if (holdsWire(x + stepX * far, y + stepY * far, z)) {
                return along << 8 | far;
            }
        }
        return 0;
    }

    private void cable(float x0, float y0, float up0, float x1, float y1, float up1) {
        SpriteGeometry.Mesh plain = solid.get(PLAIN);
        plain.open = frame.open + SpriteGeometry.CARD + 32f * WIRE;
        float[] c = eave;
        set(c, 0, x0, y0, up0);
        set(c, 1, x1, y1, up1);
        set(c, 2, x1, y1, up1 + WIRE_THICK);
        set(c, 3, x0, y0, up0 + WIRE_THICK);
        SpriteGeometry.addPlainQuad(plain, c, frame.red, frame.green, frame.blue);
    }

    private boolean holdsWire(int x, int y, int z) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        if (square == null || square.getFloor() != null) {
            return false;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && sprite.getName() != null && sprite.getName().startsWith("appliances_com")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a fence's sprite is chain link or wire and not boards: along a line across it the
     * sprite goes from painted to clear and back many times, where a board fence does so at
     * its ends and between a few boards. Counted once for each sprite, along three lines.
     */
    private boolean isMesh(IsoSprite sprite, Texture texture) {
        Boolean known = meshes.get(sprite);
        if (known == null) {
            int changes = 0;
            for (int row = 1; row <= 3; row++) {
                int v = (int) (frame.top + frame.height * row / 4f);
                boolean was = false;
                for (int u = (int) frame.left; u < frame.left + frame.width; u++) {
                    boolean is = texture.isMaskSet(u, v);
                    if (is != was) {
                        changes++;
                    }
                    was = is;
                }
            }
            known = changes >= 3 * MESH_CHANGES;
            meshes.put(sprite, known);
        }
        return known;
    }

    private boolean overPole(int x, int y) {
        IsoGridSquare ground = cell.getGridSquare(x, y, 0);
        if (ground == null) {
            return false;
        }
        PZArrayList<IsoObject> objects = ground.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            String name = sprite == null ? null : sprite.getName();
            if (name != null && (name.startsWith("street_decoration") || name.startsWith("lighting_outdoor") || name.startsWith("appliances_com"))) {
                return true;
            }
        }
        return false;
    }

    private boolean spreads(int at, int own, int mine) {
        if (groundKind[at] != Terrain.NATURAL || lightOpen[at] != 1 || groundTexture[at] == null) {
            return false;
        }
        int name = groundRank[at];
        return name != mine && (groundKind[own] != Terrain.NATURAL || name > mine);
    }

    private void addSquare(IsoGridSquare square, int x, int y, int z, int localX, int localY) {
        float toX = x + 0.5f - camera.eyeX, toY = y + 0.5f - camera.eyeY, toUp = z * ViewCamera.LEVEL_HEIGHT - camera.eyeUp;
        standOff = STAND_OFF + STAND_OFF_PER_TILE_SQUARED * (toX * toX + toY * toY + toUp * toUp);
        // An upright sprite faces the eye, not the way the view is turned, so it holds still
        // while the head or the mouse turns. On the eye's own square there is no such
        // direction, and it lies across the view.
        float along = (float) Math.sqrt(toX * toX + toY * toY);
        if (along > 0.5f) {
            cardRightX = -toY / along;
            cardRightY = toX / along;
        } else {
            cardRightX = rightX;
            cardRightY = rightY;
            acrossView = true;
        }
        floorLayer = westLayer = northLayer = eastLayer = southLayer = 0;
        waterLocalX = localX;
        waterLocalY = localY;

        here = square;
        IsoObject floor = square.getFloor();
        hangsInAir = floor == null;
        geometry.floored = !hangsInAir;
        wallNorth = wallWest = edgeNorth = edgeWest = false;
        boolean openAbove = false;
        PZArrayList<IsoObject> here = square.getObjects();
        for (int i = 0; i < here.size(); i++) {
            IsoSprite sprite = here.get(i).getSprite();
            if (sprite != null) {
                PropertyContainer flags = sprite.getProperties();
                IsoObjectType type = sprite.getType();
                openAbove |= Roofs.isTile(type) || isStair(type);
                edgeNorth |= flags.has(IsoFlagType.WallN) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.WallNTrans)
                        || flags.has(IsoFlagType.DoorWallN) || flags.has(IsoFlagType.WindowN) || flags.has(IsoFlagType.HoppableN);
                edgeWest |= flags.has(IsoFlagType.WallW) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.WallWTrans)
                        || flags.has(IsoFlagType.DoorWallW) || flags.has(IsoFlagType.WindowW) || flags.has(IsoFlagType.HoppableW);
                wallNorth |= flags.has(IsoFlagType.WallN) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.DoorWallN) || flags.has(IsoFlagType.WindowN);
                wallWest |= flags.has(IsoFlagType.WallW) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.DoorWallW) || flags.has(IsoFlagType.WindowW);
            }
        }
        wiredHere = false;
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object.getSprite();
            if (sprite == null) {
                continue;
            }
            Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
            if (texture == null || texture.getID() <= 0 || texture.getWidth() <= 0 || texture.getHeight() <= 0) {
                continue;
            }
            frameOf(object, texture);
            standOn(object);
            frame.material = Ground.of(sprite.getProperties().get("FloorMaterial"), sprite.getProperties().get("FootstepMaterial"), object == floor);
            if (frame.material == 0f) {
                frame.material = object == floor ? materialOf(sprite.getName(), true) : materialOf(sprite);
            }
            if (frame.material == PLANKS && isMesh(sprite, texture)) {
                frame.material = MESH;
            }
            sprites++;
            overLayer = 0;
            geometry.nudgeX = geometry.nudgeY = 0f;
            ArrayList<IsoSpriteInstance> leaves = object.getAttachedAnimSprite();
            // A tree in leaf shows its bole and not the branches in its crown: they are
            // inside the leaves. Drawn, they cross from one card of the fan through the leaves
            // of the next. A bare tree is all branch and keeps them.
            boolean inLeaf = sprite.getType() == IsoObjectType.tree && (leaves != null && !leaves.isEmpty() || object.getOverlaySprite() != null);
            geometry.keep = inLeaf ? BOLE : 1f;
            addSprite(solid.get(texture.getID()), object, sprite, texture, object == floor, x, y, z);
            geometry.keep = 1f;
            // What the game draws over an object with the object: the leaves on a tree, the
            // ivy and the stains on a wall. Each lies or stands as the object under it does.
            ArrayList<IsoSpriteInstance> attached = object.getAttachedAnimSprite();
            if (attached != null) {
                for (int k = 0; k < attached.size(); k++) {
                    addOver(object, sprite, attached.get(k).parentSprite, object == floor, x, y, z);
                }
            }
            addOver(object, sprite, object.getOverlaySprite(), object == floor, x, y, z);
            geometry.nudgeX = geometry.nudgeY = 0f;
        }
        geometry.raised = 0f;
        frame.material = 0f;
        if (wiredHere) {
            stringWires(x, y, z);
        }
        if (!openAbove && square.getRoomID() != -1L) {
            addRoomCeiling(x, y, z, localX, localY);
        }
        if (z == 0) {
            addEdges(x, y, localX, localY);
            spreadGround(x, y, localX, localY);
            if (toX * toX + toY * toY < GRASS_REACH * GRASS_REACH) {
                growGrass(x, y, localX, localY);
            }
            strew(x, y, localX, localY, toX * toX + toY * toY);
        }

        ArrayList<IsoMovingObject> bodies = square.getStaticMovingObjects();
        for (int i = 0; i < bodies.size(); i++) {
            IsoMovingObject body = bodies.get(i);
            solid.get(PLAIN).open = frame.open;
            SpriteGeometry.addPlainFlat(solid.get(PLAIN), body.getX(), body.getY(),
                    body.getZ() * ViewCamera.LEVEL_HEIGHT + Terrain.lift(body.getX(), body.getY(), body.getZ()) + 0.08f,
                    0.85f, 0.3f, 0.35f * frame.red, 0.2f * frame.green, 0.18f * frame.blue);
        }
    }

    /**
     * A character has no sprite: the game draws it with a model, and Characters has the game
     * draw that model into this picture. What is kept here is where each standing character
     * is, for the aim to be traced against.
     */
    private void addCharacter(IsoGameCharacter character) {
        if (character.isKnockedDown() || character instanceof IsoZombie crawler && crawler.isCrawling()) {
            return;
        }
        float x = character.getX(), y = character.getY();
        float base = character.getZ() * ViewCamera.LEVEL_HEIGHT + Terrain.lift(x, y, character.getZ());
        if (characters == characterX.length) {
            characterX = java.util.Arrays.copyOf(characterX, characters * 2);
            characterY = java.util.Arrays.copyOf(characterY, characters * 2);
            characterBase = java.util.Arrays.copyOf(characterBase, characters * 2);
        }
        characterX[characters] = x;
        characterY[characters] = y;
        characterBase[characters] = base;
        characters++;
    }

    /**
     * Sprites that share a plane of the square are stacked on it in the order the square holds
     * them, each standOff further into the square than the one before: a rug above its floor, a
     * window and then its curtain inside their wall. Seen from the other side of the plane the
     * later ones are then behind the first, which is where they are.
     */
    private void addSprite(SpriteGeometry.Mesh mesh, IsoObject object, IsoSprite sprite, Texture texture, boolean isFloor,
            int x, int y, int z) {
        PropertyContainer properties = sprite.getProperties();
        IsoObjectType type = sprite.getType();

        if (isFloor || properties.has(IsoFlagType.solidfloor) || properties.has(IsoFlagType.FloorOverlay)
                || properties.has(IsoFlagType.attachedFloor)) {
            // The game's own edging of one ground over another, a square at a time. spreadGround
            // does that here.
            if (!isFloor && z == 0 && sprite.getName() != null && sprite.getName().startsWith("blends_natural")) {
                return;
            }
            if (floorLayer == 0 && z > lowestLevel) {
                addCeiling(mesh, x, y, z);
            }
            SpriteGeometry.Mesh on = floorLayer == 0 ? mesh : laid.get(texture.getID());
            frame.kind = properties.has(IsoFlagType.water) ? SpriteGeometry.WATER : 0f;
            if (frame.kind != 0f && z == 0 && floorLayer == 0) {
                // Water knows where its bank is: each corner says whether land touches it.
                for (int corner = 0; corner < 4; corner++) {
                    int i = waterLocalX + (corner == 1 || corner == 2 ? 1 : 0), j = waterLocalY + (corner >= 2 ? 1 : 0);
                    boolean land = false;
                    for (int ci = 0; ci <= 1; ci++) {
                        for (int cj = 0; cj <= 1; cj++) {
                            // A square the map has not loaded is not land: counted as land,
                            // the last water the map holds has a bank along its far side,
                            // pale shallows and a line of foam where the water goes on.
                            byte kind = groundKind[(i + ci) * LIGHT_SPAN + j + cj];
                            land |= kind != Terrain.WATER && kind != Terrain.UNKNOWN;
                        }
                    }
                    cover[corner] = land ? 0f : 1f;
                }
                geometry.cover = cover;
            }
            geometry.addFace(on, frame, x, y, z, 0f, 0f, floorLayer++ * standOff / ViewCamera.LEVEL_HEIGHT, 1f, 0f, 0f, 0f, 1f, 0f);
            frame.kind = 0f;
            geometry.cover = null;
            return;
        }

        // What the game draws with the floor though it is no floor: railway track, and the
        // litter it calls a rug. Each is painted lying on the ground, and stood up to face the
        // eye a sheet of newspaper is a hoarding and a length of track a fence.
        if (!hangsInAir && Flat.lies(properties.get("RenderLayer"), properties.get("MoveType"))) {
            geometry.addFace(laid.get(texture.getID()), frame, x, y, z, -Flat.REACH, -Flat.REACH, floorLayer++ * standOff / ViewCamera.LEVEL_HEIGHT,
                    1f + 2f * Flat.REACH, 0f, 0f, 0f, 1f + 2f * Flat.REACH, 0f);
            return;
        }

        if (z > 0 && hangsInAir && sprite.getName() != null && sprite.getName().startsWith("appliances_com")) {
            wiredHere = true;
        }
        // Overhead wires. The game hangs them as sprites on squares of air two floors up, each
        // a length of wire painted as its view sees it run along the ground's grid. Laid flat
        // at that height the painted line is a straight wire again, from square to square. Stood
        // up to face the eye, as other loose things are, they were a fan of dashes in the sky.
        // The upper lengths of the pole hang there too: those have the foot of the pole on the
        // ground under them.
        if (z > 0 && hangsInAir && !overPole(x, y) && sprite.getName() != null && sprite.getName().startsWith("appliances_com")) {
            frame.kind = SpriteGeometry.CARD;
            frame.material = WIRE;
            geometry.addFace(mesh, frame, x, y, z, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
            frame.kind = 0f;
            frame.material = 0f;
            return;
        }

        if (addStair(mesh, texture, type, x, y, z)) {
            return;
        }

        if (overLayer == 0 && addHung(sprite, texture, x, y, z)) {
            return;
        }
        if (addFurniture(object, sprite, x, y, z)) {
            return;
        }
        // A strip light on a ceiling, a square of it at a time. The game paints it lying
        // under the ceiling, and stood up to face the eye it hung in the room as a slat.
        if (overLayer == 0 && sprite.getSpriteGrid() != null && properties.has("IsHigh") && "Lamp".equals(properties.get("CustomName"))
                && !properties.has(IsoFlagType.attachedN) && !properties.has(IsoFlagType.attachedW) && !properties.has(IsoFlagType.attachedE)
                && !properties.has(IsoFlagType.attachedS)) {
            SpriteGeometry.Mesh plain = solid.get(PLAIN);
            float lift = (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f;
            Soffits.strip(plain, x, y, (z + 1) * ViewCamera.LEVEL_HEIGHT + lift - 2f * standOff, sprite.getSpriteGrid().getWidth() > 1,
                    frame.red, frame.green, frame.blue, frame.open, frame.open + 32f * LAMP);
            return;
        }

        boolean west;
        boolean north;
        doorSwung = false;
        if (object instanceof IsoDoor door) {
            // A door on hinges stands across its doorway when it is open. A garage door
            // rolls up in its own plane, and what is left of it showing is still in the wall.
            doorSwung = door.IsOpen() && !properties.has("GarageDoor");
            north = door.north != doorSwung;
            west = !north;
        } else if (object instanceof IsoThumpable thumpable && thumpable.isDoor()) {
            doorSwung = thumpable.IsOpen() && !properties.has("GarageDoor");
            north = thumpable.north != doorSwung;
            west = !north;
        } else {
            // The corner length of a skirting board or a cornice is marked for both walls of
            // its corner by a name of its own, which says nothing to a test for either wall:
            // it stood in every corner of every room as a card turned to the eye.
            boolean cornerTrim = (properties.has("attachedNW") || properties.has("attachedSE")) && !Roofs.isTile(type)
                    && !(sprite.getName() != null && sprite.getName().startsWith("roofs_"));
            boolean both = properties.has(IsoFlagType.WallNW) || properties.has(IsoFlagType.WallSE) || cornerTrim;
            west = both || standsOnWestEdge(properties, type);
            north = both || standsOnNorthEdge(properties, type);
        }
        // A light switch says nothing of where it is. It is on a wall of its square, and is
        // laid on whichever the square has; stood up to face the eye it is a grey panel
        // floating in the room.
        if (!west && !north && type == IsoObjectType.lightswitch && frame.width < SWITCH_WIDEST * frame.scale) {
            if (!wallNorth && !wallWest) {
                return;
            }
            north = wallNorth;
            west = !wallNorth;
        }
        boolean door = object instanceof IsoDoor || object instanceof IsoThumpable hinged && hinged.isDoor();
        wallOpens = properties.has(IsoFlagType.DoorWallN) || properties.has(IsoFlagType.DoorWallW)
                || properties.has(IsoFlagType.WindowN) || properties.has(IsoFlagType.WindowW);
        seeThrough = isSheet(properties);
        // A gate in a fence is rails or wire like the fence, and stays the sheet it is.
        doorLeaf = door && !(sprite.getName() != null && sprite.getName().startsWith("fixtures_doors_fences"));
        casing = type == IsoObjectType.doorFrW || type == IsoObjectType.doorFrN;
        if (west) {
            addEdge(mesh, texture, isWall(properties, type), door, x, y, z, westLayer++ * standOff, 0f, 0f, 1f);
        }
        if (north) {
            addEdge(mesh, texture, isWall(properties, type), door, x, y, z, 0f, northLayer++ * standOff, 1f, 0f);
        }
        if (west || north) {
            return;
        }
        // The far edges are the next square's wall, so even the first sprite here stands off it.
        if (properties.has(IsoFlagType.attachedE) || type == IsoObjectType.curtainE) {
            // Clear of the skin of the next square's wall, which stands this side of the edge.
            geometry.shownA = -WALL_HALF - HUNG_CLEAR;
            geometry.addFace(mesh, frame, x, y, z, 1f - ++eastLayer * standOff, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);
            geometry.shownA = 0f;
            return;
        }
        if (properties.has(IsoFlagType.attachedS) || type == IsoObjectType.curtainS) {
            geometry.shownB = -WALL_HALF - HUNG_CLEAR;
            geometry.addFace(mesh, frame, x, y, z, 0f, 1f - ++southLayer * standOff, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
            geometry.shownB = 0f;
            return;
        }
        // The game marks most of a roof's sprites as roof, but not the caps along its ridge
        // and its edges. Their sprites do not say which way they lie, and stood upright like
        // furniture they are boxes floating over the house.
        if (Roofs.isTile(type)) {
            // The game marks a few things that are no roof as one, so that they are cut away
            // with the roof when the player walks under: a church's columns. Laid as a roof
            // tile a column is a slab leaning on the wall.
            if (overLayer == 0 && sprite.getName() != null && !sprite.getName().startsWith("roofs_") && addPost(mesh, object, sprite, texture, x, y, z)) {
                return;
            }
            roofName = sprite.getName();
            addRoof(mesh, texture, x, y, z);
            return;
        }
        if (z > 0 && sprite.getName() != null && sprite.getName().startsWith("roofs_")) {
            // Left out. Laid level they stand proud of a gable as slabs; the sheet under each
            // roof tile is what closes the roof.
            return;
        }
        // A boulder. The game's picture of one stood up as a card is a cut-out of a boulder,
        // and wrapped on a box it is a crate painted as one.
        if (sprite.getName() != null && sprite.getName().startsWith("boulders_") && addRock(x, y, z)) {
            return;
        }
        // What the game itself moves with the wind, and what it lets the player clear as
        // growth, is growth. A tree gets a second card across the first: from the eye it is
        // edge on and unseen, but the sun sees it, so the tree's shadow has a width whichever
        // way the eye looks at the tree.
        boolean grows = properties.has(IsoFlagType.vegitation) || properties.has(IsoFlagType.canBeRemoved) || properties.has("MoveWithWind");
        // What goes on a table top is painted in its frame at the height of a table, not on
        // the floor, so its outline says nothing of where a box of it would stand: read as
        // one, a computer on a desk against a wall was a box a pace away outside the wall.
        boolean onTop = properties.has("IsTableTop");
        // What stands upright on one spot and is no furniture: a column, a porch post, a lamp
        // post, a pole, a timber post with its braces.
        if (!grows && !onTop && overLayer == 0 && !properties.has(IsoFlagType.solid) && !properties.has(IsoFlagType.solidtrans)
                && !properties.has(IsoFlagType.bed) && !properties.isTable() && properties.getSurface() <= 0 && properties.get("container") == null
                && addPost(mesh, object, sprite, texture, x, y, z)) {
            return;
        }
        // What the game says nothing about at all, and whose outline is a sheet on the grid's
        // line or a beam along it: the slab over a shop front, a sill, a pipe, a billboard.
        if (!grows && !onTop && overLayer == 0 && addShaped(mesh, object, sprite, texture, x, y, z)) {
            return;
        }
        // What the game counts as filling its square, or as something to sit or lie on, is
        // furniture, and furniture is a solid thing with sides and a top.
        if (!grows && !onTop && (properties.has(IsoFlagType.solid) || properties.has(IsoFlagType.solidtrans) || properties.has(IsoFlagType.bed))
                && !(sprite.getName() != null && sprite.getName().startsWith("furniture_bedding"))
                && SpriteGeometry.boxOf(frame, box) && fillsBox(texture)) {
            geometry.addBox(mesh, frame, x, y, z, box);
            boxes++;
            return;
        }
        // A tall cupboard two squares wide that addFurniture has no model for is the same
        // case as the bed below: each square's sprite is half of it, and the outline of a half
        // is not a box. The game says how high such a thing stands and that it fills its square.
        if (!grows && properties.has(IsoFlagType.solid) && properties.getSurface() >= TALL_SURFACE) {
            // Half a square deep, as a wardrobe is, against whichever wall the square has.
            box[0] = wallWest && !wallNorth ? CUPBOARD_DEEP / 2f : 0.5f;
            box[1] = wallNorth ? CUPBOARD_DEEP / 2f : 0.5f;
            box[2] = wallWest && !wallNorth ? CUPBOARD_DEEP / 2f : 0.5f;
            box[3] = properties.getSurface() / SURFACE_PIXELS_PER_LEVEL;
            geometry.boxHalfB = wallNorth ? CUPBOARD_DEEP / 2f : 0.5f;
            geometry.addBox(mesh, frame, x, y, z, box);
            geometry.boxHalfB = 0f;
            boxes++;
            return;
        }
        // A bed addFurniture could not lay out, one with a square of it missing, say. It is
        // two squares long and each square's sprite is half of it, so neither half has the
        // outline of a box of its own. Each is the whole square wide and as high as a mattress
        // on its frame.
        // The game marks whatever can be rested on as a bed, chairs too: a bed is told by its sheet.
        if (!grows && sprite.getName() != null && sprite.getName().startsWith("furniture_bedding")) {
            // The sprite of one square of a bed is a part of the bed, and wrapped on a box it
            // leaves gaps and torn edges. The square is a block of bedding instead, the colour
            // the sprite has in the middle of the bed's top, as high as the game says a bed is.
            float high = properties.getSurface() > 0 ? properties.getSurface() / SURFACE_PIXELS_PER_LEVEL : BED_HEIGHT;
            geometry.addBlock(mesh, frame, x, y, z, high, 0.5f, 0.5f, high);
            // Over the block, what the sprite shows of the bed's top: the pillow, the fold of the
            // blanket. Where the sprite stops short the block's own colour carries on.
            geometry.addFace(mesh, frame, x, y, z, 0f, 0f, high + 2f * standOff / ViewCamera.LEVEL_HEIGHT, 1f, 0f, 0f, 0f, 1f, 0f);
            boxes++;
            return;
        }
        // A bin, a barrel, a hydrant: something compact that the game drew from above and to
        // one side. Stood up as a flat picture it leans back at the angle it was drawn from,
        // its lid tipped towards whoever looks. As a body with a top and sides it stands
        // upright. Only what is painted through the middle of its top and both the sides it
        // shows: a chair or a table has air there.
        if (!grows && !onTop && SpriteGeometry.boxOf(frame, box) && box[2] <= 0.5f && box[3] <= 1f && fillsMiddle(texture)) {
            geometry.addBox(mesh, frame, x, y, z, box);
            boxes++;
            return;
        }
        // Something low and wide the game says nothing of: a vent on a flat roof, a shower's
        // tray, a hatch. It is a slab, whatever holes its sprite has in it: a fan's blades
        // show the roof between them, and the test above takes that for a chair's legs.
        if (!grows && !onTop && overLayer == 0 && !properties.isTable() && properties.getSurface() <= 0 && !properties.has(IsoFlagType.bed)
                && SpriteGeometry.boxOf(frame, box) && Flat.isSlab(box[2], box[3])) {
            geometry.addBox(mesh, frame, x, y, z, box);
            boxes++;
            return;
        }
        // A table. The game says how high its top is. The top is laid level at that height,
        // taking from the sprite what the sprite shows there, round or square as it is painted,
        // and what the sprite shows below the top stands under it as the legs.
        // Only what is table height: a tall cupboard the game also calls a table is not one.
        if (!grows && properties.isTable() && properties.getSurface() > 0 && properties.getSurface() <= TABLE_HIGHEST) {
            float top = properties.getSurface() / SURFACE_PIXELS_PER_LEVEL;
            geometry.addFace(mesh, frame, x, y, z, 0f, 0f, top, 1f, 0f, 0f, 0f, 1f, 0f);
            float under = (frame.top + frame.height - SpriteGeometry.frameV(frame, 0.5f, 0.5f, top)) / frame.height;
            if (under > 0.05f) {
                geometry.keep = Math.min(under, 1f);
                geometry.addUpright(mesh, frame, x, y, z, 0.5f, 0.5f, 0f, cardRightX, cardRightY, false);
                geometry.keep = 1f;
            }
            return;
        }
        // Long grass. The game has it as a small picture of a clump, which up close is a
        // handful of coarse painted strokes. Here it is grown instead: tall blades, the
        // colour of the ground they stand in, thick on the square.
        if (z == 0 && sprite.getName() != null && sprite.getName().startsWith("e_newgrass") && growLongGrass(x, y)) {
            return;
        }
        if (grows) {
            // A tree or a bush is not a picture of one that turns to face whoever looks at it.
            // It stands in the world: the sprite is set up several times through the plant's
            // middle, fanned round it, every other one mirrored, so that from any side there
            // are leaves in front of leaves and they pass each other as the eye moves. Which
            // way the fan is turned comes from where the plant stands, so no two are alike.
            int cards = type == IsoObjectType.tree ? 5 : 3;
            // A tree's bole is round. A tree in leaf shows nothing else of its trunk's sprite,
            // so the bole stands in for all its cards; a bare one keeps them for its branches.
            if (type == IsoObjectType.tree && overLayer == 0 && addBole(sprite, texture, x, y, z) && geometry.keep < 1f) {
                return;
            }
            // A bush is leaves all over, as a tree's crown is.
            String grown = sprite.getName();
            // A bush's leaves are smaller than a tree's.
            boolean bush = grown != null && type != IsoObjectType.tree
                    && (grown.startsWith("f_bushes") || grown.startsWith("vegetation_foliage") || grown.startsWith("vegetation_ornamental"));
            // So is every other plant the game paints with a dark line round each leaf: the
            // line is what makes it a drawing. A bare tree's trunk is the one thing left as it is.
            if (grown != null && type != IsoObjectType.tree) {
                frame.material = bush ? Flora.BUSH : LEAVES;
            }
            double turn = ((x * 73856093 ^ y * 19349663) >>> 8 & 0xff) / 255.0 * Math.PI;
            for (int card = 0; card < cards; card++) {
                double angle = turn + card * Math.PI / cards;
                float side = (card & 1) == 0 ? 1f : -1f;
                float alongX = (float) Math.cos(angle), alongY = (float) Math.sin(angle);
                // Leaves lie on the same card as their trunk, and each layer has to stand
                // clear of the one under it on the side the eye is on, square to this card:
                // a move straight towards the eye does nothing for a card seen at a slant.
                float squareX = alongY, squareY = -alongX;
                float eyeSide = Math.signum(squareX * (camera.eyeX - x - 0.5f) + squareY * (camera.eyeY - y - 0.5f));
                float clear = overLayer * (CARD_LAYER + 4f * standOff) * eyeSide;
                geometry.nudgeX = squareX * clear;
                geometry.nudgeY = squareY * clear;
                geometry.addUpright(mesh, frame, x, y, z, 0.5f, 0.5f, 0f, side * alongX, side * alongY, true);
            }
            float toX = x + 0.5f - camera.eyeX, toY = y + 0.5f - camera.eyeY;
            if (bush && toX * toX + toY * toY < BUSH_REACH * BUSH_REACH) {
                // A fan of cards through one line is thin seen along that line, and from
                // above it is a star. A bush is a clump: three more cards stand round its
                // middle, each across the way out to it, so there are leaves on every side.
                for (int outer = 0; outer < 3; outer++) {
                    double out = turn + (outer + 0.5) * 2.0 * Math.PI / 3.0;
                    float outX = (float) Math.cos(out), outY = (float) Math.sin(out);
                    float side = (outer & 1) == 0 ? 1f : -1f;
                    geometry.nudgeX = outX * BUSH_SPREAD;
                    geometry.nudgeY = outY * BUSH_SPREAD;
                    geometry.addUpright(mesh, frame, x, y, z, 0.5f, 0.5f, 0f, -outY * side, outX * side, true);
                }
            }
            geometry.nudgeX = geometry.nudgeY = 0f;
            return;
        }
        if (overLayer == 0 && properties.getSurface() <= 0 && !properties.isTable() && addInWallLine(mesh, sprite, texture, x, y, z)) {
            return;
        }
        geometry.addUpright(mesh, frame, x, y, z, 0.5f, 0.5f, 0f, cardRightX, cardRightY, false);
    }

    /** Where each sprite the game gives no placing to has its foot: see SpriteShape.foot. Empty for a sprite with nothing painted. */
    private static final java.util.IdentityHashMap<IsoSprite, float[]> feet = new java.util.IdentityHashMap<>();
    /** How near the edge of its square a post's foot is for the post to be in the line of the wall there, in tiles. */
    private static final float IN_WALL_LINE = 0.3f;
    /** What stands in a wall's line as a flat piece is at least this tall, in levels, this long, in tiles, and this many times as long as its foot is wide. */
    private static final float BRACED_TALL = 0.9f, BRACED_LONG = 0.6f, BRACED_SPREAD = 3f;
    /** How far past its square and how high a piece in a wall's line may reach: the face is cut to what the sprite paints. */
    private static final float BRACED_REACH = 0.5f, BRACED_HIGH = 1.5f;

    /**
     * A post with braces to a beam, an arch, a trellis: something tall whose foot is against
     * the edge of a square that has a wall or a fence on that edge, and which spreads along
     * it far wider than its foot. It is part of that wall's line and flat in its plane. Stood
     * on the middle of its square to face the eye, as a thing of unknown shape is, it is half
     * a square out from the wall it belongs to and turns as the eye passes. What is narrow all
     * the way up, a pole or a lamp post, stays a card: flat in the wall's line it would be gone
     * seen from along the wall. False, adding nothing, for anything else.
     */
    private boolean addInWallLine(SpriteGeometry.Mesh mesh, IsoSprite sprite, Texture texture, int x, int y, int z) {
        if (!edgeWest && !edgeNorth) {
            return false;
        }
        float[] foot = feet.get(sprite);
        if (foot == null) {
            foot = new float[3];
            if (!SpriteShape.foot(texture::isMaskSet, frame, foot)) {
                foot = NO_BOLE;
            }
            feet.put(sprite, foot);
        }
        float pixelsPerTile = 32f * frame.scale;
        if (foot.length == 0 || frame.height < BRACED_TALL * 96f * frame.scale || frame.width < BRACED_LONG * pixelsPerTile
                || frame.width < BRACED_SPREAD * foot[2] * pixelsPerTile) {
            return false;
        }
        boolean west = edgeWest && foot[0] >= 0f && foot[0] < IN_WALL_LINE, north = edgeNorth && foot[1] >= 0f && foot[1] < IN_WALL_LINE;
        if (!west && !north) {
            return false;
        }
        frame.kind = SpriteGeometry.CARD;
        // The foot found is the corner nearest the game's view of something standing against
        // the edge, so its middle is half way back to the edge.
        if (west && (!north || foot[0] <= foot[1])) {
            geometry.addFace(mesh, frame, x, y, z, foot[0] / 2f, -BRACED_REACH, 0f, 0f, 1f + 2f * BRACED_REACH, 0f, 0f, 0f, BRACED_HIGH);
        } else {
            geometry.addFace(mesh, frame, x, y, z, -BRACED_REACH, foot[1] / 2f, 0f, 1f + 2f * BRACED_REACH, 0f, 0f, 0f, 0f, BRACED_HIGH);
        }
        frame.kind = 0f;
        return true;
    }

    /** What each sprite has been read as by Posts: NO_POST for one that is none. */
    private static final java.util.IdentityHashMap<IsoSprite, Object> posts = new java.util.IdentityHashMap<>();
    private static final Object NO_POST = new Object();
    /** How far apart, in tiles, the axes of two lengths of one post may be read on the squares one above the other. */
    private static final float SAME_POST = 0.2f;

    /** The post an object's sprite is a picture of, read the first time the sprite is met, or null. */
    private Posts postOf(IsoObject object, IsoSprite sprite, Texture texture) {
        Object post = posts.get(sprite);
        if (post == null) {
            shapeFrame.scale = frame.scale;
            frameOf(shapeFrame, object, texture);
            // A pole the game takes for a trunk is round, though its foot is drawn to a point.
            post = Posts.read(texture::isMaskSet, shapeFrame, "Tree".equals(sprite.getProperties().get("PhysicsShape")));
            if (post == null) {
                post = NO_POST;
            }
            posts.put(sprite, post);
        }
        return post == NO_POST ? null : (Posts) post;
    }

    /** The length of post on a square whose axis is within SAME_POST of (a, b), or null. */
    private Posts postAt(int x, int y, int z, float a, float b) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        if (square == null) {
            return null;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object.getSprite();
            if (sprite == null) {
                continue;
            }
            Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
            if (texture == null || texture.getID() <= 0 || texture.getWidth() <= 0 || texture.getHeight() <= 0) {
                continue;
            }
            Posts post = postOf(object, sprite, texture);
            if (post != null && Math.abs(post.a - a) < SAME_POST && Math.abs(post.b - b) < SAME_POST) {
                return post;
            }
        }
        return null;
    }

    /**
     * Stands a sprite as the post its outline is, and says whether the sprite is thereby
     * dealt with: see Posts. A post taller than a storey is a sprite on each level, and every
     * length of it stands on the axis of the lowest, since each sprite's own reading of where
     * it stands is a little different.
     */
    private boolean addPost(SpriteGeometry.Mesh mesh, IsoObject object, IsoSprite sprite, Texture texture, int x, int y, int z) {
        Posts post = postOf(object, sprite, texture);
        // A flat sign seen on the slant has upright sides and rows all of one width, as a
        // shaft has: its outline says it is a sheet, and it stays one.
        if (post == null || shapeOf(object, sprite, texture).kind == SpriteShape.PANEL) {
            return false;
        }
        float a = post.a, b = post.b;
        for (int under = z - 1; under >= lowestLevel; under--) {
            Posts below = postAt(x, y, under, a, b);
            if (below == null) {
                break;
            }
            a = below.a;
            b = below.b;
        }
        PropertyContainer properties = sprite.getProperties();
        String made = properties.get("Material"), kind = properties.get("MaterialType");
        boolean wooden = made != null && (made.startsWith("Wood") || made.equals("Log")) || kind != null && kind.startsWith("Wood")
                || sprite.getName() != null && sprite.getName().startsWith("carpentry_");
        // Braces lie along the wall or the fence their post stands against, where there is one.
        boolean bracesAlongX = edgeWest && a < IN_WALL_LINE && !(edgeNorth && b < a) ? false
                : edgeNorth && b < IN_WALL_LINE ? true : post.armsAlongX();
        geometry.standPost(mesh, frame, x, y, z, post, a, b, wooden && post.kind == Posts.SQUARE, bracesAlongX,
                postAt(x, y, z + 1, a, b) == null, cardRightX, cardRightY);
        boxes++;
        return true;
    }

    /** What each sprite the game gives no placing to has been read as: see SpriteShape. */
    private static final java.util.IdentityHashMap<IsoSprite, SpriteShape> shapes = new java.util.IdentityHashMap<>();
    private final SpriteGeometry.Frame shapeFrame = new SpriteGeometry.Frame();

    /** The shape of an object's sprite, read the first time the sprite is met. */
    private SpriteShape shapeOf(IsoObject object, IsoSprite sprite, Texture texture) {
        SpriteShape shape = shapes.get(sprite);
        if (shape == null) {
            shapeFrame.scale = frame.scale;
            frameOf(shapeFrame, object, texture);
            shape = SpriteShape.read(texture::isMaskSet, shapeFrame);
            shapes.put(sprite, shape);
        }
        return shape;
    }

    /**
     * Stands a sprite as the panel or lays it as the beam its outline is, and says whether
     * the sprite is thereby dealt with. False for any other outline, for a length of panel
     * with no whole one beside it to say where it stands, and for a beam the game calls
     * furniture: a bench is as long and as low as a rafter, and is no box.
     */
    private boolean addShaped(SpriteGeometry.Mesh mesh, IsoObject object, IsoSprite sprite, Texture texture, int x, int y, int z) {
        SpriteShape shape = shapeOf(object, sprite, texture);
        if (shape.kind == SpriteShape.CARD) {
            return false;
        }
        if (shape.kind == SpriteShape.BEAM) {
            PropertyContainer properties = sprite.getProperties();
            if (properties.has(IsoFlagType.solid) || properties.has(IsoFlagType.solidtrans) || properties.has(IsoFlagType.bed)
                    || properties.isTable() || properties.getSurface() > 0) {
                return false;
            }
            geometry.addBeam(mesh, frame, x, y, z, shape);
            boxes++;
            return true;
        }
        if (!shape.whole) {
            shape = besideWhole(shape, x, y, z);
            if (shape == null) {
                return false;
            }
        }
        geometry.addPanel(mesh, frame, x, y, z, shape);
        return true;
    }

    /**
     * A length of panel too short to say where on its square it stands, put in the plane of
     * the whole panel on the next square along its run: the end sheet of a billboard. Null
     * when neither of those squares has one.
     */
    private SpriteShape besideWhole(SpriteShape part, int x, int y, int z) {
        for (int step = -1; step <= 1; step += 2) {
            IsoGridSquare beside = cell.getGridSquare(part.alongX ? x + step : x, part.alongX ? y : y + step, z);
            if (beside == null) {
                continue;
            }
            PZArrayList<IsoObject> objects = beside.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                IsoObject object = objects.get(i);
                IsoSprite sprite = object.getSprite();
                if (sprite == null) {
                    continue;
                }
                Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
                if (texture == null || texture.getID() <= 0 || texture.getWidth() <= 0 || texture.getHeight() <= 0) {
                    continue;
                }
                SpriteShape whole = shapeOf(object, sprite, texture);
                if (whole.kind == SpriteShape.PANEL && whole.whole && whole.alongX == part.alongX) {
                    return part.at(part.alongX ? whole.b0 : whole.a0);
                }
            }
        }
        return null;
    }

    /**
     * Stands a boulder on the square, as big as its sprite's outline says it is. False for a
     * sprite whose outline is too flat to say: a stone lying in the grass, which stays as it is.
     */
    private boolean addRock(int x, int y, int z) {
        if (!SpriteGeometry.boxOf(frame, box)) {
            return false;
        }
        float a = Math.clamp(box[0], 0.2f, 0.8f), b = Math.clamp(box[1], 0.2f, 0.8f);
        float toX = x + a - camera.eyeX, toY = y + b - camera.eyeY;
        flora.addRock(solid.get(PLAIN), x + a, y + b, z * ViewCamera.LEVEL_HEIGHT + geometry.liftAt(a, b), Math.clamp(box[2] * 1.15f, 0.15f, 0.6f),
                Math.clamp(box[3] * ViewCamera.LEVEL_HEIGHT, 0.12f, 1.4f), toX * toX + toY * toY < ROCK_NEAR * ROCK_NEAR, Flora.mix(x, y, 80), frame);
        return true;
    }

    /**
     * Stands a round bole where a tree's sprite shows its trunk, with ivy up some. False when
     * the tree is too far off for one, or its sprite has no bole that can be read.
     */
    private boolean addBole(IsoSprite sprite, Texture texture, int x, int y, int z) {
        float toX = x + 0.5f - camera.eyeX, toY = y + 0.5f - camera.eyeY;
        float away2 = toX * toX + toY * toY;
        if (away2 > TRUNK_REACH * TRUNK_REACH) {
            return false;
        }
        float[] bole = boles.get(sprite);
        if (bole == null) {
            bole = measureBole(texture);
            boles.put(sprite, bole);
        }
        if (bole.length == 0) {
            return false;
        }
        boolean near = away2 < TRUNK_NEAR * TRUNK_NEAR;
        int seed = Flora.mix(x, y, 81);
        float base = z * ViewCamera.LEVEL_HEIGHT + geometry.liftAt(0.5f, 0.5f);
        flora.addTrunk(solid.get(PLAIN), x + 0.5f, y + 0.5f, base, bole[0], bole[1], near, seed, frame);
        if (near && Flora.ivied(x, y)) {
            flora.addIvy(laid.get(PLAIN), x + 0.5f, y + 0.5f, base, bole[0], bole[1], bole[1] * (0.5f + 0.5f * Flora.unit(seed, 16)), seed, frame);
        }
        return true;
    }

    /**
     * Reads a bole off the tree sprite the frame is set to: how tall it is, which is as much of
     * the sprite as a tree in leaf shows, and how thick, which is the narrower of what is
     * painted across the sprite at two heights below where the boughs start. NO_BOLE for a
     * sprite painted right across at those heights, which is no trunk, and for one painted at
     * neither.
     */
    private float[] measureBole(Texture texture) {
        float topRow = frame.top + frame.height * (1f - BOLE);
        float footRow = Math.min(SpriteGeometry.frameV(frame, 0.5f, 0.5f, 0f), frame.top + frame.height - 1f);
        float tall = SpriteGeometry.heightDrawnAt(frame, 0.5f, 0.5f, topRow) * ViewCamera.LEVEL_HEIGHT;
        if (tall < 0.3f) {
            return NO_BOLE;
        }
        int narrowest = Integer.MAX_VALUE;
        for (int row = 0; row < 2; row++) {
            int v = Math.round(footRow + (topRow - footRow) * (row == 0 ? 0.2f : 0.45f));
            int first = -1, last = -1;
            for (int u = (int) frame.left; u < frame.left + frame.width; u++) {
                if (texture.isMaskSet(u, v)) {
                    if (first < 0) {
                        first = u;
                    }
                    last = u;
                }
            }
            if (first >= 0) {
                narrowest = Math.min(narrowest, last - first + 1);
            }
        }
        if (narrowest == Integer.MAX_VALUE || narrowest >= frame.width * 0.6f) {
            return NO_BOLE;
        }
        float radius = narrowest / 2f * SpriteGeometry.TILES_PER_PIXEL_ACROSS / frame.scale;
        return new float[] {Math.clamp(radius, BOLE_THINNEST, BOLE_THICKEST), tall};
    }

    /**
     * A model is in metres, and the view's lengths are in tiles, along the ground and up
     * alike: a level is ViewCamera.LEVEL_HEIGHT tiles up, and a pixel of the height the game
     * gives a surface is LEVEL_HEIGHT / SURFACE_PIXELS_PER_LEVEL of a tile. A tile is taken as
     * a metre. The character's eye stands 1.6 tiles up and a storey is 2.45, which is what
     * they are in metres.
     */
    private static final float TILES_PER_METRE = 1f;
    /** How far inside the edge of its squares a piece of furniture stops, in tiles. */
    private static final float FURNITURE_CLEAR = 0.02f;
    /** How far the back of a piece that stands against a wall is from the edge of its square: clear of the wall's skin. */
    private static final float BACK_CLEAR = WALL_HALF + 0.02f;

    /** How high the foot of a cupboard on the wall is, and where on its sprite its colour is taken, in levels. */
    private static final float WALL_CUPBOARD_FOOT = 0.6f, WALL_CUPBOARD_DYE = 0.76f;
    /** The height, in levels, at which the sprite of a thing with no height of its own gives its colour: a bin, a barrel, a locker. */
    private static final float LOW_DYE = 0.2f, LOCKER_DYE = 0.35f;
    /** How high a picnic table's top is, in levels, and where on its first square the top's colour is taken. */
    private static final float PICNIC_TOP = 0.31f, PICNIC_DYE = 0.75f;
    /** Where the sprite gives a piece's colour when it has none at the first place, as shares of that place's height. */
    private static final float[] DYE_TRIES = {1f, 0.5f, 0.75f};

    /** A sprite the mod has a model for, and how the model stands on the squares the game gives the piece. */
    private static final class Furniture {
        final Models.Model model;
        /** Quarter turns from facing north, as Models.turn takes them. */
        final int turns;
        /** The piece's squares when it has more than one, and how many there are along x and y. */
        final IsoSpriteGrid grid;
        final int width, height;
        /** Which of those squares this sprite is. */
        final int gridX, gridY;
        /** The model in place of model when a basin stands on the piece, or null for a piece that takes none. */
        Models.Model withBasin;
        /** Whether this is a basin: part of the model of the counter it stands on, and nothing of its own. */
        boolean basin;
        /** The surfaces that take their colour from the game's sprite, a bit for each, and those that take it from a second place on it. */
        int dyed, alsoDyed;
        /** Where on its square the sprite gives the colour, and how high, in levels: the first place and the second. */
        float dyeA = 0.5f, dyeB = 0.5f, dyeHigh, alsoHigh;
        /** The square whose sprite gives the colour. */
        int dyeX, dyeY;
        /** Tiles to a metre of the model along the ground and up, and how far above the floor its foot is, in tiles. */
        float scale = TILES_PER_METRE, scaleUp = TILES_PER_METRE, raised;
        /** Where the model's origin is from the corner of the piece's first square. */
        float centreX, centreY;
        /** For what hangs on a wall, which wall of its square: it is stood by addHung, where its sprite paints it. NONE for all else. */
        int wall = WallHung.NONE;
        /** Whether what its frame holds is the picture its sprite paints, and not a mirror's glass. */
        boolean pictured;
        /** See ownSize. */
        boolean ownSize;

        Furniture(IsoSprite sprite, String model, int turns) {
            this.model = model == null ? null : Models.get(model);
            this.turns = turns;
            grid = sprite.getSpriteGrid();
            width = grid == null ? 1 : grid.getWidth();
            height = grid == null ? 1 : grid.getHeight();
            gridX = grid == null ? 0 : grid.getSpriteGridPosX(sprite);
            gridY = grid == null ? 0 : grid.getSpriteGridPosY(sprite);
            centreX = width / 2f;
            centreY = height / 2f;
        }

        /** The surfaces that take the colour the sprite has over the middle of the piece's first square, high levels up. */
        Furniture dye(int surfaces, float high) {
            dyed = surfaces;
            dyeHigh = high;
            return this;
        }

        /** As dye, from (a, b) on the square. */
        Furniture dye(int surfaces, float a, float b, float high) {
            dyeA = a;
            dyeB = b;
            return dye(surfaces, high);
        }

        /** As dye, from the square at the piece's front: a bed's blanket is on the squares of its foot. */
        Furniture dyeAtFront(int surfaces, float high) {
            float[] front = new float[2];
            Models.turn(turns, 0f, -1f, front);
            dyeX = front[0] > 0f ? width - 1 : 0;
            dyeY = front[1] > 0f ? height - 1 : 0;
            return dye(surfaces, high);
        }

        /** The surfaces that take the colour the sprite has at another height over the same place: a counter's top. */
        Furniture also(int surfaces, float high) {
            alsoDyed = surfaces;
            alsoHigh = high;
            return this;
        }

        /**
         * Stands the piece inside its squares, no bigger than fits them.
         *
         * @param topUp        how high the game says the piece's top is, in tiles, for one made as high as that, or 0
         * @param reference    how high the model's own top is, in metres, where topUp is given
         * @param againstBack  whether the piece stands with its back at the edge of its squares and not in the middle of them
         */
        Furniture stand(float topUp, float reference, boolean againstBack) {
            boolean alongY = (turns & 1) == 0;
            float along = alongY ? height : width, across = alongY ? width : height;
            float fits = Math.min((along - 2f * FURNITURE_CLEAR) / model.depth(), (across - 2f * FURNITURE_CLEAR) / model.width());
            scale = scaleUp = Math.min(topUp > 0f ? topUp / reference : TILES_PER_METRE, fits);
            back(againstBack ? Math.max((along - model.depth() * scale) / 2f - BACK_CLEAR, 0f) : 0f);
            return this;
        }

        /**
         * Stands a piece that meets the next one along: a metre of it is a tile along the ground
         * whatever its height, so that a run of counters has no gaps in it.
         *
         * @param centred  whether the model is built round the middle of its own depth, to be stood at the back of its
         *                 square, and not round the middle of the square it fills
         */
        Furniture meet(float topUp, float reference, boolean centred) {
            scaleUp = topUp > 0f ? topUp / reference : TILES_PER_METRE;
            back(centred ? (1f - model.depth()) / 2f - BACK_CLEAR : 0f);
            return this;
        }

        private void back(float slack) {
            float[] front = new float[2];
            Models.turn(turns, 0f, -1f, front);
            centreX = width / 2f - front[0] * slack;
            centreY = height / 2f - front[1] * slack;
        }

        Furniture raise(float up) {
            raised = up;
            return this;
        }

        /** As dye, from over the back of the piece's square: a shelf on a wall is nowhere near the square's middle. */
        Furniture dyeAtBack(int surfaces, float high) {
            float[] back = new float[2];
            Models.turn(turns, 0f, 1f, back);
            return dye(surfaces, 0.5f + back[0] * BACK_DYE, 0.5f + back[1] * BACK_DYE, high);
        }

        Furniture hang(int wall, boolean pictured) {
            this.wall = wall;
            this.pictured = pictured;
            return this;
        }

        /** For what hangs on a wall and stands out from it, a lamp: it is as big as its model, whatever its sprite's outline comes to. */
        Furniture ownSize() {
            ownSize = true;
            return this;
        }
    }

    /** What each sprite seen so far is, null for one that is no furniture the mod has a model of. */
    private static final java.util.IdentityHashMap<IsoSprite, Furniture> furniture = new java.util.IdentityHashMap<>();
    private final SpriteGeometry.Frame dyeFrame = new SpriteGeometry.Frame();
    /** Where the piece last found ready takes its colours, and the texture of the sprite that gives them, PLAIN for a piece that takes none. */
    private float dyeU, dyeV, alsoU, alsoV;
    private int dyeTexture;

    private static Furniture known(IsoSprite sprite) {
        if (!furniture.containsKey(sprite)) {
            furniture.put(sprite, recognise(sprite));
        }
        return furniture.get(sprite);
    }

    /**
     * Which of the mod's models a sprite is a square of, by what the game says the sprite is:
     * the name it gives the piece and the kind of piece, what the piece holds, which way it
     * faces, how high its top is and the squares it lies on. Null for a piece of another shape
     * than the models: a rocking chair is not the armchair, a round table not the square one.
     */
    private static Furniture recognise(IsoSprite sprite) {
        String name = sprite.getName();
        PropertyContainer properties = sprite.getProperties();
        String called = properties.get("CustomName");
        IsoSpriteGrid grid = sprite.getSpriteGrid();
        if (name != null && called == null && grid == null && name.startsWith(STREET_LAMPS)) {
            return streetLamp(sprite, name);
        }
        if (name == null || called == null || grid != null && grid.getLevels() != 1) {
            return null;
        }
        String group = properties.get("GroupName") == null ? "" : properties.get("GroupName");
        String holds = properties.get("container") == null ? "" : properties.get("container");
        int surface = properties.getSurface();
        float surfaceHigh = surface / SURFACE_PIXELS_PER_LEVEL, surfaceUp = surfaceHigh * ViewCamera.LEVEL_HEIGHT;
        boolean low = properties.has("IsLow"), onTable = properties.has("IsTableTop");
        boolean counterHigh = surface >= 30 && surface <= 40;
        IsoDirections facing = sprite.getFacing();
        boolean blocks = properties.has(IsoFlagType.solid) || properties.has(IsoFlagType.solidtrans);
        if (facing != null && !blocks && holds.isEmpty() && !onTable && surface == 0) {
            // The game gives a facing as one of the four ways along the map's axes.
            int faces = facing == IsoDirections.N ? 0 : facing == IsoDirections.E ? 1 : facing == IsoDirections.S ? 2 : 3;
            if (called.equals("Clock") && grid == null) {
                return new Furniture(sprite, "wall_clock", faces).hang(behind(facing), false);
            }
            // A lamp on a wall stands out from it, and laid in the wall's plane as its sprite is
            // painted it is a bowl with nothing holding it up. It faces away from its wall.
            if (called.equals("Wall Lamp") && group.endsWith("Half Sphere") && grid == null) {
                return new Furniture(sprite, "wall_bowl", faces).hang(behind(facing), false).ownSize();
            }
            if (called.equals("Outdoor Lamp") && grid == null) {
                String lamp = group.equals("Fancy") ? "wall_bowl" : group.equals("Modern") || group.equals("Oval") ? "bulkhead_light" : "wall_lantern";
                return new Furniture(sprite, lamp, faces).hang(behind(facing), false).ownSize();
            }
            // One square of a mirror or a picture that is longer than a square has no frame of
            // its own, and each square's length of it meets the next.
            if (called.equals("Mirror") && name.startsWith("walls_decoration")) {
                return new Furniture(sprite, grid == null ? null : "canvas", faces).hang(behind(facing), false);
            }
            // A picture's sprite shows its face only from the two sides the game's view sees.
            if (FRAMED.contains(called) && (facing == IsoDirections.S || facing == IsoDirections.E)) {
                return new Furniture(sprite, grid == null ? null : "canvas", faces).hang(behind(facing), true);
            }
        }
        // A drum is round, and a crate the same from every side: the game gives most of them no front.
        if ((called.equals("Metal Drum") || called.equals("Barrel")) && grid == null) {
            return new Furniture(sprite, "barrel", 0).dye(1 << Models.PAINT, LOW_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Barbecue") && holds.equals("barbecue") && grid == null) {
            return new Furniture(sprite, "kettle_barbecue", 0).dye(1 << Models.PAINT, KETTLE_LID).stand(0f, 0f, false);
        }
        if (called.equals("Stool") && grid == null && properties.has(IsoFlagType.bed) && !group.equals("Organs")) {
            Furniture stool = new Furniture(sprite, "stool", 0).dye(1 << Models.WOOD, surface > 0 ? surfaceHigh : LOW_DYE);
            return stool.stand(surfaceUp, stool.model.height(), false);
        }
        if (called.equals("Crate") && (holds.equals("crate") || holds.equals("militarycrate")) && !group.contains("Metal") && grid == null
                && surface >= 25 && surface <= 40) {
            Furniture crate = new Furniture(sprite, "crate", 0).dye(1 << Models.WOOD, surfaceHigh * 0.75f);
            return crate.stand(surfaceUp, crate.model.height(), false);
        }
        if (facing == null) {
            // A table for four has no front, and the game gives it none.
            if (grid == null && name.startsWith("furniture_tables_high") && properties.isTable() && surface > 0 && surface <= TABLE_HIGHEST) {
                Furniture table = new Furniture(sprite, called.contains("Round") ? "round_table" : "small_table", 0).dye(1 << Models.WOOD, surfaceHigh);
                return table.stand(surfaceUp, table.model.height(), false);
            }
            return null;
        }
        // The game gives a facing as one of the four ways along the map's axes.
        int turns = facing == IsoDirections.N ? 0 : facing == IsoDirections.E ? 1 : facing == IsoDirections.S ? 2 : 3;
        boolean alongY = (turns & 1) == 0;
        int width = grid == null ? 1 : grid.getWidth(), height = grid == null ? 1 : grid.getHeight();
        int along = alongY ? height : width, across = alongY ? width : height;
        boolean single = grid == null, pair = along == 1 && across == 2;
        boolean tableHigh = properties.isTable() && surface > 0 && surface <= TABLE_HIGHEST;
        // A bed faces the way its foot points, so its head is the model's back. Its blanket's
        // colour is on the squares of its foot: the squares of its head show the pillows.
        if (name.startsWith("furniture_bedding") && called.equals("Bed") && along == 2 && across <= 2) {
            return new Furniture(sprite, across == 2 ? "double_bed" : "single_bed", turns)
                    .dyeAtFront(1 << Models.CLOTH, surface > 0 ? surfaceHigh : BED_HEIGHT).stand(0f, 0f, false);
        }
        if (called.equals("Wardrobe") && holds.equals("wardrobe") && surface >= TALL_SURFACE && pair) {
            Furniture wardrobe = new Furniture(sprite, "wardrobe", turns).dye(1 << Models.WOOD, surfaceHigh / 2f);
            return wardrobe.stand(surfaceUp, wardrobe.model.height(), true);
        }
        // A tall cupboard on one square: a steel locker, or shelves that stand on the floor.
        if (called.equals("Locker") && single && !low && (holds.equals("locker") || holds.equals("militarylocker") || holds.equals("metal_shelves"))) {
            Furniture locker = new Furniture(sprite, "locker", turns).dye(1 << Models.PAINT, LOCKER_DYE);
            return locker.stand(surface >= TALL_SURFACE ? surfaceUp : 0f, locker.model.height(), true);
        }
        if (called.equals("Shelves") && holds.equals("shelves") && name.startsWith("furniture_shelving") && properties.has(IsoFlagType.solid)
                && surface >= TALL_SURFACE && single) {
            Furniture bookcase = new Furniture(sprite, "bookcase", turns).dye(1 << Models.WOOD, surfaceHigh / 2f);
            return bookcase.stand(surfaceUp, bookcase.model.height(), true);
        }
        if (name.startsWith("furniture_seating_indoor") && called.equals("Couch") && surface > 0 && pair) {
            return new Furniture(sprite, "sofa", turns).dye(1 << Models.CLOTH, surfaceHigh).stand(0f, 0f, true);
        }
        if (called.equals("Shelves") && single && properties.has("IsHigh") && !blocks && surface >= WALL_SHELF_LOWEST
                && holds.equals("shelves")) {
            // The game gives the height of the top board. Two boards where that leaves room for a second under it.
            Furniture shelf = new Furniture(sprite, surface >= WALL_SHELVES_LOWEST ? "wall_shelves" : "wall_shelf", turns)
                    .dyeAtBack(1 << Models.WOOD, surfaceHigh);
            return shelf.stand(0f, 0f, true).raise(surfaceUp - shelf.model.height());
        }
        // Low open shelves in a run, each square of them meeting the next as counters do.
        if (called.equals("Shelves") && single && low && properties.isTable() && counterHigh && holds.equals("shelves")
                && name.startsWith("furniture_shelving")) {
            Furniture shelves = new Furniture(sprite, "low_shelves", turns).dye(1 << Models.WOOD, surfaceHigh * 0.5f);
            return shelves.meet(surfaceUp, shelves.model.height(), true);
        }
        // Shelving two squares long with no height given: a shop's, or steel racking.
        if (called.equals("Shelves") && pair && surface == 0 && properties.has(IsoFlagType.solidtrans) && !low) {
            if (holds.equals("metal_shelves")) {
                return new Furniture(sprite, "metal_rack", turns).dye(1 << Models.PAINT, LOCKER_DYE).stand(0f, 0f, true);
            }
            if (holds.equals("shelves")) {
                return new Furniture(sprite, "store_shelves", turns).dye(1 << Models.PAINT, LOCKER_DYE).stand(0f, 0f, true);
            }
            return null;
        }
        if (called.equals("Chair") && surface > 0 && single && "badBed".equals(properties.get("BedType"))
                && (group.contains("Plastic") || group.equals("Folding"))) {
            return group.equals("Folding") ? new Furniture(sprite, "folding_chair", turns).dye(1 << Models.VINYL, surfaceHigh).stand(0f, 0f, false)
                    : new Furniture(sprite, "plastic_chair", turns).dye(1 << Models.PLASTIC, surfaceHigh).stand(0f, 0f, false);
        }
        // A diner's bench: the game marks it as something to rest on, two squares long.
        if (called.equals("Seat") && pair && properties.has(IsoFlagType.bed)) {
            return new Furniture(sprite, "booth", turns).dye(1 << Models.VINYL, BOOTH_SEAT).also(1 << Models.WOOD, BOOTH_BASE).stand(0f, 0f, true);
        }
        if (name.startsWith("furniture_seating_indoor") && called.equals("Chair") && surface > 0 && single) {
            // What the game rates as fair to sleep in is an armchair. What it rates as bad is a hard chair.
            if ("averageBed".equals(properties.get("BedType")) && !group.startsWith("Rocking")) {
                return new Furniture(sprite, "armchair", turns).dye(1 << Models.CLOTH, surfaceHigh).stand(0f, 0f, false);
            }
            if ("badBed".equals(properties.get("BedType")) && !group.contains("Plastic") && !group.contains("Folding")) {
                return new Furniture(sprite, group.contains("Office") ? "office_chair" : "dining_chair", turns).dye(1 << Models.CLOTH, surfaceHigh)
                        .stand(0f, 0f, false);
            }
            return null;
        }
        if (called.equals("Drawers") && surface > 0 && single && (holds.equals("sidetable") || holds.equals("dresser") && !group.contains("Mirror"))) {
            Furniture drawers = new Furniture(sprite, holds.equals("dresser") ? "dresser" : "bedside_cabinet", turns).dye(1 << Models.WOOD, surfaceHigh);
            return drawers.stand(surfaceUp, drawers.model.height(), true);
        }
        // A desk faces whoever sits at it: its drawers are on that side.
        if (called.equals("Desk") && surface > 0 && pair) {
            Furniture desk = new Furniture(sprite, "desk", turns).dye(1 << Models.WOOD, surfaceHigh);
            return desk.stand(surfaceUp, desk.model.height(), false);
        }
        if (called.equals("Desk") && tableHigh && along == 1 && across == 3) {
            Furniture desk = new Furniture(sprite, "office_desk", turns).dye(1 << Models.WOOD, surfaceHigh);
            return desk.stand(surfaceUp, desk.model.height(), false);
        }
        // A table lies across the way it faces, as a desk does. A pool table is no table to sit at.
        if ((called.equals("Table") || called.equals("Low Table")) && tableHigh && !group.equals("Pool") && !group.contains("Glass")) {
            boolean lowTable = surface <= LOW_TABLE_HIGHEST;
            String model = pair ? lowTable ? "low_table_2" : "table_2" : along == 1 && across == 3 && !lowTable ? "table_3"
                    : along == 2 && across == 2 && !lowTable ? "table_4" : single && lowTable && holds.isEmpty() ? "low_table"
                    : single && !lowTable && group.startsWith("Long") ? "narrow_table" : null;
            if (model == null) {
                return null;
            }
            Furniture table = new Furniture(sprite, model, turns).dye(1 << Models.WOOD, surfaceHigh);
            return table.stand(surfaceUp, table.model.height(), false);
        }
        if (called.equals("Counter") && single) {
            boolean corner = group.contains("Corner");
            // A shop's counter or a bar: a counter with no doors on the side it is seen from, at
            // whatever height the game gives it.
            if (low && properties.isTable() && surface >= 30 && surface <= TABLE_HIGHEST && (holds.isEmpty() || holds.equals("counter") && !counterHigh)) {
                Furniture counter = new Furniture(sprite, corner ? "counter_corner" : "shop_counter", turns)
                        .dye(1 << Models.WOOD, surfaceHigh * 0.45f).also(1 << Models.WORKTOP, surfaceHigh);
                return counter.meet(surfaceUp, counter.model.height(), !corner);
            }
            if (low && properties.isTable() && holds.equals("counter") && counterHigh) {
                Furniture counter = new Furniture(sprite, corner ? "counter_corner" : "counter", turns)
                        .dye(1 << Models.WOOD, surfaceHigh * 0.45f).also(1 << Models.WORKTOP, surfaceHigh);
                if (!corner) {
                    counter.withBasin = Models.get("counter_sink");
                }
                return counter.meet(surfaceUp, counter.model.height(), !corner);
            }
            // A canteen's serving counter: steel, with pans in its top under a sloping glass. The
            // game gives it no height, and wrapped on the box its outline makes the glass is a
            // tilted blue slab.
            if (low && surface == 0 && holds.equals("restaurantdisplay")) {
                return new Furniture(sprite, "serving_counter", turns).meet(0f, 0f, true);
            }
            if (properties.has("IsHigh") && surface == 0 && (holds.equals("counter") || holds.equals("overhead"))) {
                return new Furniture(sprite, corner ? "wall_cupboard_corner" : "wall_cupboard", turns).dye(1 << Models.WOOD, WALL_CUPBOARD_DYE)
                        .meet(0f, 0f, !corner).raise(WALL_CUPBOARD_FOOT * ViewCamera.LEVEL_HEIGHT);
            }
            return null;
        }
        // A basin on a wall with nothing under it. The game gives the height of its rim.
        if (called.equals("Sink") && !onTable && single && group.endsWith("Hanging") && surface > 0 && !blocks) {
            Furniture sink = new Furniture(sprite, "wall_sink", turns).stand(0f, 0f, true);
            return sink.raise(surfaceUp - SINK_RIM * sink.scaleUp);
        }
        // A basin let into a counter. The counter's model has it.
        if (called.equals("Sink") && onTable && single) {
            Furniture basin = new Furniture(sprite, null, turns);
            basin.basin = true;
            return basin;
        }
        if (called.equals("Fridge") && holds.equals("fridge") && (single || pair) && (surface >= 55 || single && counterHigh)) {
            Furniture fridge = new Furniture(sprite, pair ? "large_fridge" : surface >= 55 ? "fridge" : "mini_fridge", turns)
                    .dye(1 << Models.ENAMEL, surfaceHigh * 0.8f);
            return fridge.stand(surfaceUp, fridge.model.height(), true);
        }
        // A shop's freezer chest under glass, two squares long.
        if (called.equals("Freezer") && pair && low && surface >= 25 && surface <= 40) {
            Furniture freezer = new Furniture(sprite, "chest_freezer", turns).dye(1 << Models.ENAMEL, surfaceHigh * 0.5f);
            return freezer.stand(surfaceUp, freezer.model.height(), false);
        }
        if (holds.equals("dishwasher") && single && counterHigh) {
            Furniture washer = new Furniture(sprite, "dishwasher", turns).dye(1 << Models.ENAMEL, surfaceHigh * 0.5f);
            return washer.stand(surfaceUp, washer.model.height(), true);
        }
        // What stands on a counter says how high the counter is.
        if (onTable && single && surface > 0) {
            if (holds.equals("microwave")) {
                return new Furniture(sprite, "microwave", turns).stand(0f, 0f, false).raise(surfaceUp);
            }
            if (holds.equals("cashregister")) {
                return new Furniture(sprite, "register", turns).stand(0f, 0f, false).raise(surfaceUp);
            }
            if (called.equals("Radio")) {
                return new Furniture(sprite, "radio", turns).stand(0f, 0f, false).raise(surfaceUp);
            }
        }
        if (called.equals("Machine") && single && surface == 0) {
            if (holds.equals("vendingsnack") || holds.equals("vendingpop")) {
                return new Furniture(sprite, "vending_machine", turns).dye(1 << Models.PAINT, LOCKER_DYE).stand(0f, 0f, true);
            }
            if (group.endsWith("Arcade")) {
                return new Furniture(sprite, "arcade_machine", turns).stand(0f, 0f, true);
            }
            return null;
        }
        if (called.equals("Dumpster") && holds.equals("dumpster") && pair) {
            return new Furniture(sprite, "dumpster", turns).dye(1 << Models.PAINT, LOW_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Barbecue") && holds.equals("barbecuepropane") && single) {
            return new Furniture(sprite, "gas_barbecue", turns).stand(0f, 0f, false);
        }
        // A shower is on the wall behind it. The model is built on its square, the wall at its back.
        if (called.equals("Shower") && single && name.startsWith("fixtures_bathroom") && (facing == IsoDirections.S || facing == IsoDirections.E)) {
            return group.equals("Wall") ? new Furniture(sprite, "shower_head", turns).meet(0f, 0f, false).raise(SHOWER_TAPS)
                    : new Furniture(sprite, "shower", turns).meet(0f, 0f, false);
        }
        if (called.equals("Oven") && holds.equals("stove") && single && counterHigh) {
            return new Furniture(sprite, "oven", turns).dye(1 << Models.ENAMEL, surfaceHigh * 0.45f).stand(surfaceUp, OVEN_HOB, true);
        }
        if (holds.equals("clothingwasher") && single && counterHigh) {
            return new Furniture(sprite, "washing_machine", turns).dye(1 << Models.ENAMEL, surfaceHigh * 0.5f).stand(surfaceUp, WASHER_TOP, true);
        }
        if (called.equals("Television") && single) {
            // A set on a table says how high the table is. One in a cabinet of its own stands on the floor.
            if (onTable) {
                return new Furniture(sprite, "tv", turns).stand(0f, 0f, false).raise(surfaceUp);
            }
            if (surface >= 25 && surface <= 40) {
                return new Furniture(sprite, "tv_console", turns).dye(1 << Models.WOOD, surfaceHigh * 0.25f).stand(surfaceUp, TV_CABINET_TOP, true);
            }
            return null;
        }
        if (called.equals("Lamp") && onTable && single && surface > 0) {
            return new Furniture(sprite, "table_lamp", turns).stand(0f, 0f, false).raise(surfaceUp);
        }
        if (called.equals("Toilet") && single && name.startsWith("fixtures_bathroom_01")) {
            return new Furniture(sprite, "toilet", turns).stand(0f, 0f, true);
        }
        if (called.equals("Bath") && pair) {
            return new Furniture(sprite, "bath", turns).stand(0f, 0f, true);
        }
        if (called.equals("Bin") && holds.equals("bin") && single && (group.equals("Wheelie") || group.contains("Garbage"))) {
            return new Furniture(sprite, group.equals("Wheelie") ? "wheelie_bin" : "litter_bin", turns).dye(1 << Models.PLASTIC, LOW_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Box") && holds.equals("postbox") && single) {
            return group.equals("Mail") ? new Furniture(sprite, "mailbox", turns).stand(0f, 0f, false)
                    : new Furniture(sprite, "postbox", turns).dye(1 << Models.PAINT, LOW_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Bench") && (group.equals("Park") || group.equals("Fancy Outdoors")) && surface > 0 && pair) {
            return new Furniture(sprite, "park_bench", turns).dye(1 << Models.WOOD, surfaceHigh * 0.9f).stand(surfaceUp, BENCH_SEAT, false);
        }
        if ((called.equals("Picknic Table") || called.equals("Table") && group.equals("Picknic")) && width == 2 && height == 2) {
            return new Furniture(sprite, "picnic_table", turns).dye(1 << Models.WOOD, PICNIC_DYE, PICNIC_DYE, PICNIC_TOP).stand(0f, 0f, false);
        }
        return null;
    }

    /** The sheet the game's street lamps are on. They carry no name: which lamp a sprite is, is its place on the sheet. */
    private static final String STREET_LAMPS = "lighting_outdoor_01_";
    /** Where on its sprite a lamp's post gives its colour, in levels. */
    private static final float POST_DYE = 0.5f, UPPER_POST_DYE = 0.25f;

    /**
     * The model of a street lamp, by which sprite of the game's sheet of them it is. The first
     * four are whole lamps, a post with its light. A lamp taller than a storey is two sprites,
     * and the upper one, on the level above, is the head: a lantern, or the arm of a road lamp,
     * which reaches out north, east, south and west in that order on the sheet. Null for the
     * rest of the sheet: bare posts, which stand well enough as they are, signals, and heads
     * the mod has no model of.
     */
    private static Furniture streetLamp(IsoSprite sprite, String name) {
        // The sheet has a copy of itself with the lamps lit, the same lamps in the same places.
        String place = name.substring(STREET_LAMPS.length());
        if (place.startsWith("on_")) {
            place = place.substring(3);
        }
        if (place.isEmpty() || place.length() > 3 || !place.chars().allMatch(Character::isDigit)) {
            return null;
        }
        int which = Integer.parseInt(place);
        return switch (which) {
            case 0, 2 -> new Furniture(sprite, "cone_lamp", 0).dye(1 << Models.PAINT, POST_DYE);
            case 1 -> new Furniture(sprite, "globe_lamp", 0).dye(1 << Models.PAINT, POST_DYE);
            case 3 -> new Furniture(sprite, "lantern_lamp", 0).dye(1 << Models.PAINT, POST_DYE);
            case 5 -> new Furniture(sprite, "lantern_head", 0).dye(1 << Models.PAINT, UPPER_POST_DYE);
            case 8, 9, 10, 11 -> new Furniture(sprite, "cobra_head", which - 8).dye(1 << Models.PAINT, UPPER_POST_DYE);
            default -> null;
        };
    }

    /** How high, in metres, the part of a model is that the game gives the height of: an oven's hob under its back, a bench's seat, a basin's rim. */
    private static final float OVEN_HOB = 0.91f, WASHER_TOP = 0.92f, TV_CABINET_TOP = 0.78f, BENCH_SEAT = 0.455f, SINK_RIM = 0.44f;
    /** How far from the middle of its square, towards its back, a piece on the wall gives its colour. */
    private static final float BACK_DYE = 0.36f;
    /** How high above the floor the lowest part of a shower with no tray is, its taps, in tiles: its model starts there. */
    private static final float SHOWER_TAPS = 0.99f;
    /** The height the game gives a shelf on a wall from which it is a shelf and no low cupboard, and from which there is room for a second board under the first. */
    private static final int WALL_SHELF_LOWEST = 50, WALL_SHELVES_LOWEST = 60;
    /** The highest a table's top is, as the game gives it, for the table to be one to sit on a sofa at. */
    private static final int LOW_TABLE_HIGHEST = 22;
    /** Where on its sprite a kettle grill's lid gives its colour, and a diner bench's seat and its base, in levels. */
    private static final float KETTLE_LID = 0.36f, BOOTH_SEAT = 0.2f, BOOTH_BASE = 0.06f;
    /** What the game calls the things that hang in a frame, or as a board, with a picture on their face. */
    private static final java.util.Set<String> FRAMED = java.util.Set.of("Painting", "Paintings", "Certificate", "Whiteboard", "Noteboard", "Dartboard");
    /** The frames a picture can hang in, from the tallest for its width to the widest. */
    private static final String[] FRAMES = {"frame_tall", "frame_square", "frame_wide"};
    /** The least and the most a clock is made of its model's size. */
    private static final float CLOCK_SMALLEST = 0.8f, CLOCK_LARGEST = 1.6f;

    private final float[] hungAt = new float[4], hungShows = new float[6], hungWay = new float[2];

    /**
     * Hangs the mod's model of a picture, a mirror or a clock on the wall the sprite was
     * painted for, where on that wall the sprite paints it and as big, and says whether the
     * sprite is thereby dealt with. A picture's frame holds what the sprite shows.
     *
     * False, which leaves the sprite to be drawn as it is, for a sprite that is no such thing
     * and for one not painted where something on that wall would be.
     */
    private boolean addHung(IsoSprite sprite, Texture texture, int x, int y, int z) {
        Furniture piece = known(sprite);
        if (piece == null || piece.wall == WallHung.NONE || !WallHung.painted(frame, piece.wall, hungAt)) {
            return false;
        }
        float wide = hungAt[WallHung.TO] - hungAt[WallHung.FROM];
        float high = (hungAt[WallHung.TOP] - hungAt[WallHung.BOTTOM]) * ViewCamera.LEVEL_HEIGHT;
        float along = (hungAt[WallHung.FROM] + hungAt[WallHung.TO]) / 2f;
        Models.Model model = piece.model != null ? piece.model : Models.get(FRAMES[WallHung.frameFor(wide / high)]);
        float scale = wide / model.width(), scaleUp = high / model.height(), foot = hungAt[WallHung.BOTTOM] * ViewCamera.LEVEL_HEIGHT;
        if (piece.model != null && !piece.model.name.equals("canvas")) {
            // A clock is round whatever its sprite's outline comes to, and hangs about the middle of it.
            scale = scaleUp = piece.ownSize ? TILES_PER_METRE : Math.clamp(Math.min(scale, scaleUp), CLOCK_SMALLEST, CLOCK_LARGEST);
            foot += (high - model.height() * scale) / 2f;
        }
        if (piece.ownSize) {
            // A lamp stands out into its square, and is painted to one side of its fixing for it.
            float proud = model.depth() * scale / 2f;
            along = Math.clamp(along + WallHung.alongProud(piece.wall, proud), 0f, 1f);
            foot += WallHung.upProud(piece.wall, proud) * ViewCamera.LEVEL_HEIGHT;
        }
        // Its back clear of the wall's skin, which stands that far inside the square's edge.
        Models.turn(piece.turns, 0f, -1f, hungWay);
        float out = WALL_HALF + HUNG_CLEAR + model.depth() * scale / 2f;
        float a = WallHung.a(piece.wall, along), b = WallHung.b(piece.wall, along);
        float[] lift = geometry.lift;
        placed.add(model, frame, x, y, x + a + hungWay[0] * out, y + b + hungWay[1] * out,
                z * ViewCamera.LEVEL_HEIGHT + (lift[0] + lift[1] + lift[2] + lift[3]) / 4f + foot, piece.turns, scale, scaleUp,
                piece.pictured ? texture.getID() : PLAIN, piece.pictured ? 1 << Models.PANE : 0, 0f, 0f, 0, 0f, 0f);
        if (piece.pictured) {
            Models.turn(piece.turns, 1f, 0f, hungWay);
            WallHung.shows(frame, a, b, hungAt[WallHung.BOTTOM], hungWay[0] * scale, hungWay[1] * scale, scaleUp / ViewCamera.LEVEL_HEIGHT, hungShows);
            placed.picture(hungShows);
        }
        furnished++;
        return true;
    }

    /**
     * Whether the piece the object is a square of can stand as its model: every square of it is
     * there, and the sprite that is to give it its colour is painted where the colour is
     * taken. Leaves where the colour is taken in dyeU to alsoV and dyeTexture.
     */
    private boolean ready(IsoObject object, Furniture piece, int x, int y, int z) {
        int originX = x - piece.gridX, originY = y - piece.gridY;
        IsoObject dyer = object;
        if (piece.grid != null) {
            for (int i = 0; i < piece.width; i++) {
                for (int j = 0; j < piece.height; j++) {
                    IsoObject part = holding(cell.getGridSquare(originX + i, originY + j, z), piece.grid.getSprite(i, j));
                    if (part == null) {
                        return false;
                    }
                    if (i == piece.dyeX && j == piece.dyeY) {
                        dyer = part;
                    }
                }
            }
        }
        dyeTexture = PLAIN;
        if (piece.dyed == 0) {
            return true;
        }
        Texture dye = dyer.getSprite().getTextureForCurrentFrame(dyer.getDir());
        if (dye == null || dye.getID() <= 0 || dye.getWidth() <= 0 || dye.getHeight() <= 0) {
            return false;
        }
        dyeFrame.scale = frame.scale;
        frameOf(dyeFrame, dyer, dye);
        dyeTexture = dye.getID();
        for (float share : DYE_TRIES) {
            float high = piece.dyeHigh * share;
            if (dye.isMaskSet(Math.round(SpriteGeometry.frameU(dyeFrame, piece.dyeA, piece.dyeB)),
                    Math.round(SpriteGeometry.frameV(dyeFrame, piece.dyeA, piece.dyeB, high)))) {
                dyeU = SpriteGeometry.textureU(dyeFrame, piece.dyeA, piece.dyeB);
                dyeV = SpriteGeometry.textureV(dyeFrame, piece.dyeA, piece.dyeB, high);
                if (piece.alsoDyed == 0) {
                    return true;
                }
                alsoU = dyeU;
                alsoV = SpriteGeometry.textureV(dyeFrame, piece.dyeA, piece.dyeB, piece.alsoHigh);
                return dye.isMaskSet(Math.round(SpriteGeometry.frameU(dyeFrame, piece.dyeA, piece.dyeB)),
                        Math.round(SpriteGeometry.frameV(dyeFrame, piece.dyeA, piece.dyeB, piece.alsoHigh)));
            }
        }
        return false;
    }

    /** The object on the square that is a counter a basin can stand in, or a basin, or null. */
    private IsoObject onSquare(int x, int y, int z, boolean basin) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        if (square == null) {
            return null;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            Furniture piece = sprite == null ? null : known(sprite);
            if (piece != null && (basin ? piece.basin : piece.withBasin != null)) {
                return objects.get(i);
            }
        }
        return null;
    }

    /**
     * Stands the mod's own model of a piece of furniture where the game has the sprites of
     * one, and says whether this sprite is thereby dealt with. A piece on several squares is
     * one model, added by the first of its squares, and its other squares add nothing.
     *
     * False, which leaves the sprite to be drawn as it is, when there is no model of the
     * piece, when one of its squares is missing, and when the sprite that is to give it its
     * colour has nothing painted where the colour is taken. Every square of a piece reaches
     * the same answer, so none of it is drawn twice or left out.
     */
    private boolean addFurniture(IsoObject object, IsoSprite sprite, int x, int y, int z) {
        Furniture piece = known(sprite);
        // What hangs on a wall is addHung's: one it could not hang is drawn as it was.
        if (piece == null || piece.wall != WallHung.NONE) {
            return false;
        }
        if (piece.basin) {
            // Dealt with when the counter under it is: the counter's model has the basin in it.
            IsoObject counter = onSquare(x, y, z, false);
            return counter != null && ready(counter, known(counter.getSprite()), x, y, z);
        }
        if (!ready(object, piece, x, y, z)) {
            return false;
        }
        // What the game lays over the piece's sprite, a stain, has no place on the model.
        if (piece.gridX != 0 || piece.gridY != 0 || overLayer > 0) {
            return true;
        }
        float[] lift = geometry.lift;
        Models.Model model = piece.withBasin != null && onSquare(x, y, z, true) != null ? piece.withBasin : piece.model;
        placed.add(model, frame, x, y, x + piece.centreX, y + piece.centreY,
                z * ViewCamera.LEVEL_HEIGHT + (lift[0] + lift[1] + lift[2] + lift[3]) / 4f + piece.raised, piece.turns, piece.scale, piece.scaleUp,
                dyeTexture, piece.dyed, dyeU, dyeV, piece.alsoDyed, alsoU, alsoV);
        furnished++;
        return true;
    }

    /** The object on a square that shows the sprite, or null. */
    private static IsoObject holding(IsoGridSquare square, IsoSprite sprite) {
        if (square == null || sprite == null) {
            return null;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i).getSprite() == sprite) {
                return objects.get(i);
            }
        }
        return null;
    }

    /**
     * A room has a ceiling. Where a floor lies over it the floor's underside is that ceiling:
     * see addCeiling. Over the rest of a room the map has only what the game's view from above
     * shows, the roof's near slopes or nothing at all, and from inside the room that is rafters
     * and sky. Such a square is closed at the top of its level with plain plaster, lit by the
     * room. Left open: a square the stairs go up through, one a roof comes down through, and
     * one with a room over it, which is how the map marks a hall two floors high.
     */
    private void addRoomCeiling(int x, int y, int z, int localX, int localY) {
        IsoGridSquare above = cell.getGridSquare(x, y, z + 1);
        if (above != null && (above.getFloor() != null || above.getRoomID() != -1L) || Roofs.madeAt(x, y, z, frames) != null
                || cutAway(x, y)) {
            return;
        }
        cornerLights(localX, localY);
        SpriteGeometry.Mesh plain = solid.get(PLAIN);
        plain.open = 32f * CONCRETE;
        plain.sway = 0f;
        plain.face(0f, 0f, -1f);
        float up = (z + 1) * ViewCamera.LEVEL_HEIGHT - standOff;
        for (int corner : CEILING_CORNERS) {
            plain.add(x + (corner == 1 || corner == 2 ? 1f : 0f), y + (corner >= 2 ? 1f : 0f), up + geometry.lift[corner], 0f, 0f,
                    frame.cornerRed[corner], frame.cornerGreen[corner], frame.cornerBlue[corner]);
        }
    }

    private static final int[] CEILING_CORNERS = {0, 1, 2, 0, 2, 3};

    private static boolean isStair(IsoObjectType type) {
        return type == IsoObjectType.stairsBW || type == IsoObjectType.stairsMW || type == IsoObjectType.stairsTW
                || type == IsoObjectType.stairsBN || type == IsoObjectType.stairsMN || type == IsoObjectType.stairsTN;
    }

    /**
     * Lays the tiles of the slopes the map leaves out that lie in a chunk, and the caps of the
     * ridges and hips in it: see RoofFit.backs and RoofFit.caps. They are of the roof's own
     * colour, and lit as the open air is: no square of the map holds them, so none has a
     * light for them.
     */
    private void addBacks(IsoChunk chunk, int chunkX, int chunkY) {
        ArrayList<Roofs.Laid> backs = Roofs.madeIn(chunk.wx, chunk.wy, frames);
        ArrayList<Roofs.Capped> caps = Roofs.cappedIn(chunk.wx, chunk.wy, frames);
        if (backs == null && caps == null) {
            return;
        }
        float[] ground = Terrain.corners(cell, chunk.wx, chunk.wy);
        frame.light(unseenLight.r, unseenLight.g, unseenLight.b);
        frame.open = 1f;
        frame.kind = 0f;
        float[] lift = geometry.lift;
        for (int i = 0; backs != null && i < backs.size(); i++) {
            RoofFit.Tile tile = backs.get(i).tile;
            if (cutAway(tile.x, tile.y)) {
                continue;
            }
            liftOf(ground, tile.x - chunkX, tile.y - chunkY);
            roof = backs.get(i).roof;
            SpriteGeometry.Mesh sheet = roofSheet();
            if (tile.shaped) {
                addFolded(sheet, tile, 3, false);
                addFoldEaves(sheet, tile);
                continue;
            }
            float[] at = tile.corners;
            SpriteGeometry.addOver(sheet, tile.x, tile.y, at[0] * ViewCamera.LEVEL_HEIGHT + lift[0], at[1] * ViewCamera.LEVEL_HEIGHT + lift[1],
                    at[2] * ViewCamera.LEVEL_HEIGHT + lift[2], at[3] * ViewCamera.LEVEL_HEIGHT + lift[3], roofU, roofV, frame.red, frame.green, frame.blue);
            addEave(sheet, tile.x, tile.y, tile.z, at[0] - tile.z, at[1] - at[0], at[3] - at[0], true);
            addRake(sheet, tile.x, tile.y, tile.z, at[0] - tile.z, at[1] - at[0], at[3] - at[0]);
        }
        for (int i = 0; caps != null && i < caps.size(); i++) {
            RoofFit.Cap cap = caps.get(i).cap;
            if (cutAway((int) Math.floor((cap.x0 + cap.x1) / 2f), (int) Math.floor((cap.y0 + cap.y1) / 2f))) {
                continue;
            }
            liftOf(ground, Math.clamp((int) Math.floor((cap.x0 + cap.x1) / 2f) - chunkX, 0, CHUNK_TILES - 1),
                    Math.clamp((int) Math.floor((cap.y0 + cap.y1) / 2f) - chunkY, 0, CHUNK_TILES - 1));
            roof = caps.get(i).roof;
            addCap(cap);
        }
    }

    /** Sets the ground's lift at the corners of a square of the chunk whose ground is given. */
    private void liftOf(float[] ground, int localX, int localY) {
        int corner = localX * Terrain.CORNERS + localY;
        float[] lift = geometry.lift;
        lift[0] = ground[corner];
        lift[1] = ground[corner + Terrain.CORNERS];
        lift[2] = ground[corner + Terrain.CORNERS + 1];
        lift[3] = ground[corner + 1];
    }

    /**
     * The mod's own sheet over the triangles of a folded roof tile that which names, bit 0 the
     * first and bit 1 the second: in the tile's own surface, or just under it for a triangle
     * the tile's sprite is drawn on.
     */
    private void addFolded(SpriteGeometry.Mesh sheet, RoofFit.Tile tile, int which, boolean under) {
        int[] order = RoofFit.TRIANGLES[tile.crease];
        for (int triangle = 0; triangle < 2; triangle++) {
            if ((which >> triangle & 1) == 0) {
                continue;
            }
            for (int i = 0; i < 3; i++) {
                int corner = order[triangle * 3 + i];
                float high = tile.corners[corner] - tile.z;
                set(folded, i, tile.x + RoofFit.cornerX(corner), tile.y + RoofFit.cornerY(corner),
                        (under ? under(tile.z, high) : (tile.z + high) * ViewCamera.LEVEL_HEIGHT) + geometry.lift[corner]);
            }
            SpriteGeometry.addTriangle(sheet, folded, roofU, roofV, frame.red, frame.green, frame.blue);
        }
    }

    /** The west, east, north and south edge of a square, each as its two corners. */
    private static final int[][] EDGES = {{0, 3}, {1, 2}, {0, 1}, {3, 2}};

    /**
     * The eaves of a folded tile. Each of its triangles is a slope of its own, and where one
     * comes down to a level edge of the square with no roof beyond, that edge is an eave.
     */
    private void addFoldEaves(SpriteGeometry.Mesh sheet, RoofFit.Tile tile) {
        int[] order = RoofFit.TRIANGLES[tile.crease];
        float[] at = tile.corners;
        for (int pass = 0; pass < 2; pass++) {
            for (int edge = 0; edge < 4; edge++) {
                int first = EDGES[edge][0], second = EDGES[edge][1];
                if (pass == 0) {
                    foldEave[edge] = false;
                }
                if (Math.abs(at[first] - at[second]) > RoofFit.SAME) {
                    continue;
                }
                // The triangle against this edge is the one whose corner off the fold is one of the edge's own.
                int off = order[2] == first || order[2] == second ? order[2] : order[5];
                int high = order[0] == first || order[0] == second ? order[1] : order[0];
                float rise = at[high] - at[first];
                if (off != first && off != second || rise < Roofs.FLAT) {
                    continue;
                }
                boolean alongX = edge < 2, lowAtFar = edge == 1 || edge == 3;
                float atOrigin = at[first] - tile.z + (lowAtFar ? rise : 0f), along = lowAtFar ? -rise : rise;
                if (pass == 0) {
                    foldEave[edge] = isEave(tile.x, tile.y, tile.z, atOrigin, alongX ? along : 0f, alongX ? 0f : along);
                    continue;
                }
                // Two eaves of one tile meet at the tile's outer corner, each cut on the slant
                // to the other. One pipe to a corner: the eave along x gives it.
                addEave(sheet, tile.x, tile.y, tile.z, atOrigin, alongX ? along : 0f, alongX ? 0f : along, !alongX,
                        foldEave[alongX ? 2 : 0], foldEave[alongX ? 3 : 1]);
            }
        }
    }

    /** Which of a folded tile's west, east, north and south edges is an eave. */
    private final boolean[] foldEave = new boolean[4];

    /**
     * The cap along a ridge or down a hip: a low strip with a level crown and two sides that
     * come down onto the roof, of the roof's own covering.
     */
    private void addCap(RoofFit.Cap cap) {
        SpriteGeometry.Mesh sheet = roofSheet();
        sheet.open = frame.open + 32f * RIDGE;
        float dx = cap.x1 - cap.x0, dy = cap.y1 - cap.y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float acrossX = -dy / length, acrossY = dx / length;
        float lift = (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f;
        float from = cap.high0 * ViewCamera.LEVEL_HEIGHT + lift, to = cap.high1 * ViewCamera.LEVEL_HEIGHT + lift;
        float edge = CAP_CLEAR - cap.fall * ViewCamera.LEVEL_HEIGHT * CAP_HALF;
        float[] c = eave;
        for (int strip = 0; strip < 3; strip++) {
            float near = strip == 0 ? -CAP_HALF : strip == 1 ? -CAP_CROWN : CAP_CROWN, far = strip == 0 ? -CAP_CROWN : strip == 1 ? CAP_CROWN : CAP_HALF;
            float nearUp = strip == 0 ? edge : CAP_RISE, farUp = strip == 2 ? edge : CAP_RISE;
            set(c, 0, cap.x0 + acrossX * near, cap.y0 + acrossY * near, from + nearUp);
            set(c, 1, cap.x1 + acrossX * near, cap.y1 + acrossY * near, to + nearUp);
            set(c, 2, cap.x1 + acrossX * far, cap.y1 + acrossY * far, to + farUp);
            set(c, 3, cap.x0 + acrossX * far, cap.y0 + acrossY * far, from + farUp);
            SpriteGeometry.addQuad(sheet, c, roofU, roofV, frame.red, frame.green, frame.blue);
        }
    }

    /** Whether the mains are on: read once a frame. */
    private boolean mainsOn;
    /** How deep the canopy over each square of one is, in tiles, by the square's place: 0 for a floor in the air with no band round it. */
    private static final java.util.HashMap<Long, Float> canopies = new java.util.HashMap<>();
    /** How far a canopy is looked across for its edge, in squares, and the least and most deep one is made, in tiles. */
    private static final int CANOPY_SPAN = 24;
    private static final float CANOPY_SHALLOWEST = 0.25f, CANOPY_DEEPEST = 1.2f;
    /** How far past the canopy's edge its lining runs, to meet the band hung just outside the edge. */
    private static final float CANOPY_LIP = 0.1f;
    private final SpriteGeometry.Frame bandFrame = new SpriteGeometry.Frame();
    private final float[] canopyRed = new float[4], canopyGreen = new float[4], canopyBlue = new float[4];
    /** Where fasciaOf last found a band: the square it hangs on. */
    private int bandX, bandY;

    /** Whether a square with a floor is roof over open air and no room's floor: nothing is built on it, nor under it on the level below. */
    private static boolean isCanopy(IsoGridSquare square, IsoGridSquare below) {
        return square != null && square.getFloor() != null && square.getRoomID() == -1L && square.getZ() >= 1
                && (below == null || below.getRoomID() == -1L && below.getFloor() == null);
    }

    /**
     * The band hung along the south edge of the canopy a square is part of, or along its east
     * edge: the sprite the map hangs, a level down, on the first square past the canopy that
     * way. Null where the canopy has none there. Leaves the band's square in bandX and bandY.
     */
    private IsoObject fasciaOf(int x, int y, int z, boolean south) {
        for (int step = 1; step <= CANOPY_SPAN; step++) {
            int atX = south ? x : x + step, atY = south ? y + step : y;
            if (isCanopy(cell.getGridSquare(atX, atY, z), cell.getGridSquare(atX, atY, z - 1))) {
                continue;
            }
            IsoGridSquare under = cell.getGridSquare(atX, atY, z - 1);
            if (under == null) {
                return null;
            }
            PZArrayList<IsoObject> objects = under.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                IsoSprite sprite = objects.get(i).getSprite();
                if (sprite == null) {
                    continue;
                }
                PropertyContainer properties = sprite.getProperties();
                if (properties.has(south ? IsoFlagType.attachedN : IsoFlagType.attachedW) && !isWall(properties, sprite.getType()) && textureOf(objects.get(i)) != null) {
                    bandX = atX;
                    bandY = atY;
                    return objects.get(i);
                }
            }
            return null;
        }
        return null;
    }

    /**
     * How deep the canopy a square is part of is: as deep as the band the map hangs along its
     * south or its east edge is high. 0 for a floor with no such band, which is no canopy
     * but a balcony or a bridge, and keeps the underside it has.
     */
    private float canopyDrop(int x, int y, int z) {
        long key = ((long) x << 36) ^ ((long) y << 8) ^ z;
        Float known = canopies.get(key);
        if (known != null) {
            return known;
        }
        float drop = 0f;
        for (int way = 0; way < 2 && drop == 0f; way++) {
            IsoObject band = fasciaOf(x, y, z, way == 0);
            if (band == null) {
                continue;
            }
            bandFrame.scale = frame.scale;
            frameOf(bandFrame, band, textureOf(band));
            if (WallHung.painted(bandFrame, way == 0 ? WallHung.NORTH : WallHung.WEST, hungAt)) {
                drop = Math.clamp((1f - hungAt[WallHung.BOTTOM]) * ViewCamera.LEVEL_HEIGHT, CANOPY_SHALLOWEST, CANOPY_DEEPEST);
            }
        }
        // Kept only once the map is loaded as far as the search reaches: before that the answer may be short of the truth.
        if (cell.getGridSquare(x + CANOPY_SPAN, y, 0) != null && cell.getGridSquare(x, y + CANOPY_SPAN, 0) != null) {
            canopies.put(key, drop);
        }
        return drop;
    }

    /** Whether a square out of doors has a canopy over it, one or two levels up, whose lights are on. */
    private boolean underLitCanopy(int x, int y, int z) {
        for (int up = z + 1; up <= z + 2; up++) {
            IsoGridSquare over = cell.getGridSquare(x, y, up);
            if (over == null || over.getFloor() == null) {
                continue;
            }
            return isCanopy(over, cell.getGridSquare(x, y, up - 1)) && (mainsOn || over.haveElectricity()) && canopyDrop(x, y, up) > 0f;
        }
        return false;
    }

    /**
     * Lines one square of a canopy and closes its edges: see Soffits. Its underside is as far
     * under the floor as the band round it is deep, lit by the ground beneath. Along its south
     * and east edges the map hangs the band. Its north and west edges, which the game's view
     * never saw, get the band from the far end of the same row or column of the canopy.
     */
    private void addCanopy(int x, int y, int z, float drop) {
        IsoGridSquare ground = null;
        for (int under = z - 1; under >= 0 && ground == null; under--) {
            ground = cell.getGridSquare(x, y, under);
        }
        ColorInfo light = ground != null && ground.isSeen(playerIndex) ? ground.lighting[playerIndex].lightInfo() : unseenLight;
        boolean lit = mainsOn || here.haveElectricity();
        float red = lit ? Soffits.under(light.r, Soffits.GLOW_RED) : light.r, green = lit ? Soffits.under(light.g, Soffits.GLOW_GREEN) : light.g;
        float blue = lit ? Soffits.under(light.b, Soffits.GLOW_BLUE) : light.b;
        java.util.Arrays.fill(canopyRed, red);
        java.util.Arrays.fill(canopyGreen, green);
        java.util.Arrays.fill(canopyBlue, blue);
        float up = z * ViewCamera.LEVEL_HEIGHT - drop;
        SpriteGeometry.Mesh plain = solid.get(PLAIN);
        Soffits.add(plain, x, y, up, canopyRed, canopyGreen, canopyBlue, 32f * CONCRETE, 32f * LAMP, Soffits.fitted(x, y), lit);
        plain.open = 32f * CONCRETE;
        float[] c = eave;
        for (int side = 0; side < 4; side++) {
            int stepX = side == 0 ? -1 : side == 1 ? 1 : 0, stepY = side == 2 ? -1 : side == 3 ? 1 : 0;
            if (isCanopy(cell.getGridSquare(x + stepX, y + stepY, z), cell.getGridSquare(x + stepX, y + stepY, z - 1))) {
                continue;
            }
            // The lining runs a little past the edge, under the foot of the band.
            float x0 = stepX < 0 ? x - CANOPY_LIP : stepX > 0 ? x + 1f : x, x1 = stepX < 0 ? x : stepX > 0 ? x + 1f + CANOPY_LIP : x + 1f;
            float y0 = stepY < 0 ? y - CANOPY_LIP : stepY > 0 ? y + 1f : y, y1 = stepY < 0 ? y : stepY > 0 ? y + 1f + CANOPY_LIP : y + 1f;
            set(c, 0, x0, y0, up);
            set(c, 1, x1, y0, up);
            set(c, 2, x1, y1, up);
            set(c, 3, x0, y1, up);
            SpriteGeometry.addPlainQuad(plain, c, red, green, blue);
            if (stepX > 0 || stepY > 0) {
                continue;
            }
            // The north or the west edge: the band of the edge across the canopy, stood here.
            boolean north = stepY < 0;
            IsoObject band = fasciaOf(x, y, z, north);
            Texture texture = band == null ? null : textureOf(band);
            if (texture == null) {
                // No band to borrow: a plain one, the lining's colour, as deep as the canopy.
                float top = z * ViewCamera.LEVEL_HEIGHT;
                set(c, 0, x, y, up);
                set(c, 1, north ? x + 1f : x, north ? y : y + 1f, up);
                set(c, 2, north ? x + 1f : x, north ? y : y + 1f, top);
                set(c, 3, x, y, top);
                SpriteGeometry.addPlainQuad(plain, c, red, green, blue);
                continue;
            }
            bandFrame.scale = frame.scale;
            frameOf(bandFrame, band, texture);
            bandFrame.light(unseenLight.r, unseenLight.g, unseenLight.b);
            bandFrame.open = 1f;
            bandFrame.material = materialOf(band.getSprite());
            float raised = geometry.raised;
            geometry.raised = 0f;
            if (north) {
                geometry.shownB = y - CANOPY_LIP - bandY;
                geometry.addFace(solid.get(texture.getID()), bandFrame, x, bandY, z - 1, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
                geometry.shownB = 0f;
            } else {
                geometry.shownA = x - CANOPY_LIP - bandX;
                geometry.addFace(solid.get(texture.getID()), bandFrame, bandX, y, z - 1, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);
                geometry.shownA = 0f;
            }
            geometry.raised = raised;
        }
    }

    /**
     * A floor over a room is that room's ceiling, and one that juts out over a pavement or a
     * porch is the underside of a canopy. Seen from below it is lit by the place beneath, not
     * by whatever falls on the floor above, so its underside is a face of its own just under
     * the floor, carrying the light of the square beneath: left to the floor's own back, a
     * canopy's underside is lit by the room upstairs. Over open air with no square in it, it
     * has the light of the open ground.
     */
    private void addCeiling(SpriteGeometry.Mesh mesh, int x, int y, int z) {
        IsoGridSquare below = cell.getGridSquare(x, y, z - 1);
        if (isCanopy(here, below)) {
            float drop = canopyDrop(x, y, z);
            if (drop > 0f) {
                addCanopy(x, y, z, drop);
                return;
            }
        }
        float red = frame.red, green = frame.green, blue = frame.blue, open = frame.open;
        System.arraycopy(frame.cornerRed, 0, heldRed, 0, 4);
        System.arraycopy(frame.cornerGreen, 0, heldGreen, 0, 4);
        System.arraycopy(frame.cornerBlue, 0, heldBlue, 0, 4);
        ColorInfo light = below != null && below.isSeen(playerIndex) ? below.lighting[playerIndex].lightInfo() : unseenLight;
        frame.light(light.r, light.g, light.b);
        frame.open = 0f;
        geometry.addFace(mesh, frame, x, y, z, 0f, 0f, -standOff / ViewCamera.LEVEL_HEIGHT, 1f, 0f, 0f, 0f, 1f, 0f);
        frame.red = red;
        frame.green = green;
        frame.blue = blue;
        frame.open = open;
        System.arraycopy(heldRed, 0, frame.cornerRed, 0, 4);
        System.arraycopy(heldGreen, 0, frame.cornerGreen, 0, 4);
        System.arraycopy(heldBlue, 0, frame.cornerBlue, 0, 4);
    }

    /**
     * Adds a face standing on the square from (a, b) along (da, db), a level high: a wall, or
     * what hangs on one. A wall's sprite is trimmed to everything it shows, its cap and its corner post too, so the wall
     * itself can stop a few pixels inside the sprite's edge with nothing painted beyond. A face
     * that took those texels would be see-through there, a slit of sky between two walls. So
     * the sprite is walked in from each end of the face, below any window, and down from its
     * top, to the first pixel that is painted, and the face takes its texels from there. Only
     * a wall: a window or a curtain that stops short of the square's edge really does stop there.
     */
    private void addEdge(SpriteGeometry.Mesh mesh, Texture texture, boolean wall, boolean door, int x, int y, int z,
            float a, float b, float da, float db) {
        // A wall is as thick as a wall: two skins, one each side of the square's edge, both
        // with the sprite's outline. Through a window or a doorway the far skin is seen
        // behind the near one, which is the depth of the opening. What hangs on the wall
        // hangs on the skin inside the square.
        float inX = db * WALL_HALF, inY = da * WALL_HALF;
        if (wall && seeThrough) {
            // A fence of wire or of rails is one sheet on the line between its squares. As two
            // skins a wall's thickness apart, the eye saw one lattice through the other and
            // the two beat against each other as it moved.
            geometry.addFace(mesh, frame, x, y, z, a, b, 0f, da, db, 0f, 0f, 0f, 1f);
            return;
        }
        if (!wall) {
            // A finger's width clear of the skin: lying in the skin's own plane, the two are
            // drawn through each other.
            // More of it the further off, where the picture's depth is coarser.
            geometry.shownA = inX + db * (HUNG_CLEAR + 3f * standOff);
            geometry.shownB = inY + da * (HUNG_CLEAR + 3f * standOff);
            float material = frame.material;
            if (material == GLASS) {
                faceIntoRoom(x, y, z, da != 0f);
            }
            if (door && doorLeaf && overLayer == 0 && addDoor(mesh, texture, x, y, z, a, b, da, db)) {
                geometry.shownA = geometry.shownB = 0f;
                return;
            }
            // A door's top is a straight line, and its sprite has it as a staircase of pixels
            // on the slant. The face stops at the door's top, so the line is the face's own edge.
            geometry.addFace(mesh, frame, x, y, z, a, b, 0f, da, db, 0f, 0f, 0f, door ? doorTop(texture, a, b, da, db) : 1f);
            if (casing && overLayer == 0) {
                // A door's frame goes round the doorway on both sides of the wall. The game
                // has it on the side its view saw, and from the other the doorway was bare.
                geometry.shownA = -geometry.shownA;
                geometry.shownB = -geometry.shownB;
                geometry.addFace(mesh, frame, x, y, z, a, b, 0f, da, db, 0f, 0f, 0f, 1f);
            }
            geometry.shownA = geometry.shownB = 0f;
            geometry.turned = false;
            frame.material = material;
            return;
        }
        float fromStart = unpainted(texture, a, b, da, db);
        float fromEnd = unpainted(texture, a + da, b + db, -da, -db);
        float fromTop = unpaintedTop(texture, a, b, da, db);
        boolean startIsLow = SpriteGeometry.frameU(frame, a, b) < SpriteGeometry.frameU(frame, a + da, b + db);
        float[] opening = openingOf(texture, a, b, da, db);
        int at = da != 0f ? 0 : OPENING_FLOATS;
        // Where the wall's own sprite is painted to its end, the end is the wall's: a corner
        // post's sprite is a sliver at one end of its square and nothing at the other.
        float pixels = Math.abs(SpriteGeometry.frameU(frame, a + da, b + db) - SpriteGeometry.frameU(frame, a, b));
        boolean startPainted = paintedOnWall(texture, a, b, da, db, (fromStart + 1.5f) / pixels, SEAL_HEIGHT, 0f);
        boolean endPainted = paintedOnWall(texture, a, b, da, db, 1f - (fromEnd + 1.5f) / pixels, SEAL_HEIGHT, 0f);
        int startJoin = 0, endJoin = 0;
        if (overLayer == 0) {
            joinLevel = z;
            int joins = WallJoins.of(standing, x, y, da != 0f);
            startJoin = startPainted ? joins & 7 : 0;
            endJoin = endPainted ? joins >> 3 : 0;
        }
        geometry.shownA = inX;
        geometry.shownB = inY;
        skinStart = (startJoin & WallJoins.NEAR) != 0 ? WALL_HALF : 0f;
        skinEnd = (endJoin & WallJoins.NEAR) != 0 ? WALL_HALF : 0f;
        addSkin(mesh, opening, at, x, y, z, a, b, da, db, fromStart, fromEnd, fromTop, startIsLow);
        skinStart = (startJoin & WallJoins.FAR) != 0 ? WALL_HALF : 0f;
        skinEnd = (endJoin & WallJoins.FAR) != 0 ? WALL_HALF : 0f;
        geometry.shownA = -inX;
        geometry.shownB = -inY;
        // The sprite is painted for the side inside the square. Where the far side is another
        // place, the skin on it is that side's surface. Only the wall itself: ivy or a
        // stain over it is on the side it was painted on, and is no part of the wall's colour.
        float material = frame.material;
        int tint = overLayer == 0 ? farTint(texture, x, y, z, da != 0f) : 0;
        // Out of doors the far skin is the building's own outside wall, the sprite its other
        // faces show, where the pieces round this wall's opening can take it: a wall whose
        // opening is cut out of its sprite alone would be closed by a sprite with none, and
        // a low wall would be made a whole one.
        WallFinish.Facing faced = tint != 0 && farFacing != null && farFacing.texture != texture
                && (!wallOpens || opening[at + OPENING_START] >= 0f) && paintedOnWall(texture, a, b, da, db, 0.5f, WHOLE_WALL, 0f) ? farFacing : null;
        if (faced != null) {
            tint = 0;
            held.left = frame.left;
            held.top = frame.top;
            held.width = frame.width;
            held.height = frame.height;
            held.uStart = frame.uStart;
            held.uEnd = frame.uEnd;
            held.vStart = frame.vStart;
            held.vEnd = frame.vEnd;
            frame.left = faced.texture.getOffsetX();
            frame.top = faced.texture.getOffsetY();
            frame.width = faced.texture.getWidth();
            frame.height = faced.texture.getHeight();
            frame.uStart = faced.texture.getXStart();
            frame.uEnd = faced.texture.getXEnd();
            frame.vStart = faced.texture.getYStart();
            frame.vEnd = faced.texture.getYEnd();
            frame.material = materialOf(faced.sprite);
            fromStart = unpainted(faced.texture, a, b, da, db);
            fromEnd = unpainted(faced.texture, a + da, b + db, -da, -db);
            fromTop = unpaintedTop(faced.texture, a, b, da, db);
        }
        if (tint != 0) {
            frame.material = PAINT;
        }
        // The far skin is in the place beyond the wall, and is lit as that place is: the
        // outside of a house by the day, whatever the room behind it is lit by.
        float red = frame.red, green = frame.green, blue = frame.blue, open = frame.open;
        if (overLayer == 0 && farLight >= 0) {
            System.arraycopy(frame.cornerRed, 0, heldRed, 0, 4);
            System.arraycopy(frame.cornerGreen, 0, heldGreen, 0, 4);
            System.arraycopy(frame.cornerBlue, 0, heldBlue, 0, 4);
            if (lightOpen[farLight] < 0) {
                frame.light(unseenLight.r, unseenLight.g, unseenLight.b);
                frame.open = 1f;
            } else {
                frame.light(lightRed[farLight], lightGreen[farLight], lightBlue[farLight]);
                frame.open = lightOpen[farLight];
            }
        }
        addSkin(faced != null ? solid.get(faced.texture.getID()) : tint != 0 ? solid.get(texture.getID(), tint) : mesh, opening, at, x, y, z, a, b, da, db,
                fromStart, fromEnd, fromTop, startIsLow);
        // Out of doors, on a whole wall in one colour.
        if (tint != 0 && farColour != null && frame.open > 0.5f && opening[at + OPENING_TOP] >= WHOLE_WALL) {
            addCourses(opening, at, x, y, z, a, b, da, db, -Math.signum(inX), -Math.signum(inY));
        }
        if (faced != null) {
            frame.left = held.left;
            frame.top = held.top;
            frame.width = held.width;
            frame.height = held.height;
            frame.uStart = held.uStart;
            frame.uEnd = held.uEnd;
            frame.vStart = held.vStart;
            frame.vEnd = held.vEnd;
        }
        if (overLayer == 0 && farLight >= 0) {
            frame.red = red;
            frame.green = green;
            frame.blue = blue;
            frame.open = open;
            System.arraycopy(heldRed, 0, frame.cornerRed, 0, 4);
            System.arraycopy(heldGreen, 0, frame.cornerGreen, 0, 4);
            System.arraycopy(heldBlue, 0, frame.cornerBlue, 0, 4);
        }
        frame.material = material;
        skinStart = skinEnd = 0f;
        geometry.shownA = geometry.shownB = 0f;
        geometry.sealLow = geometry.sealHigh = geometry.sealTop = 0f;
        if (overLayer == 0 && opening[at + OPENING_START] >= 0f) {
            addReveals(opening, at, x, y, z, a, b, da, db, inX, inY);
        }
        if (overLayer == 0) {
            addCaps(mesh, texture, opening[at + OPENING_TOP], (startJoin & WallJoins.OPEN) != 0, (endJoin & WallJoins.OPEN) != 0,
                    startPainted && endPainted, x, y, z, a, b, da, db, inX, inY);
        }
    }

    /** Half the thickness of a door's leaf, in tiles. */
    private static final float DOOR_HALF = 0.022f;
    /** How high up a door its width is read, in levels, and how far in from its edge the colour of its edges is taken, in frame pixels at tile scale 1. */
    private static final float DOOR_READ_AT = 0.4f, DOOR_STILE = 2.5f;
    /** Whether the door being added stands open across its doorway, and whether it is a leaf with a thickness. */
    private boolean doorSwung, doorLeaf;
    /** Whether the sprite being added is the frame round a doorway. */
    private boolean casing;
    /** For each door sprite on each edge: the frame origin it was read with, then the shares of the edge its leaf starts and ends at. */
    private final java.util.IdentityHashMap<Texture, float[]> doorSpans = new java.util.IdentityHashMap<>();
    private static final int SPAN_FLOATS = 4;

    /**
     * A door as a leaf with a thickness: its sprite on both faces, and its two edges and its
     * top closed in the colour of its stile. Shut, it hangs in the middle of the wall's
     * thickness, behind the doorway's reveals. Open, it stands clear of the wall it has swung
     * back against. False, adding nothing, for a sprite with no door painted across the edge.
     */
    private boolean addDoor(SpriteGeometry.Mesh mesh, Texture texture, int x, int y, int z, float a, float b, float da, float db) {
        int at = da != 0f ? 0 : SPAN_FLOATS;
        float[] span = doorSpans.get(texture);
        if (span == null) {
            span = new float[2 * SPAN_FLOATS];
            java.util.Arrays.fill(span, Float.NaN);
            doorSpans.put(texture, span);
        }
        if (span[at] != frame.originU || span[at + 1] != frame.originV) {
            span[at] = frame.originU;
            span[at + 1] = frame.originV;
            int pixels = Math.round(Math.abs(SpriteGeometry.frameU(frame, a + da, b + db) - SpriteGeometry.frameU(frame, a, b)));
            int first = -1, last = -1;
            for (int pixel = 0; pixel < pixels; pixel++) {
                if (paintedOnWall(texture, a, b, da, db, (pixel + 0.5f) / pixels, DOOR_READ_AT, 0f)) {
                    first = first < 0 ? pixel : first;
                    last = pixel + 1;
                }
            }
            span[at + 2] = first < 0 ? -1f : (float) first / pixels;
            span[at + 3] = (float) last / pixels;
        }
        float from = span[at + 2], to = span[at + 3];
        if (from < 0f) {
            return false;
        }
        float top = doorTop(texture, a, b, da, db);
        float out = doorSwung ? WALL_HALF + HUNG_CLEAR + DOOR_HALF : 0f;
        for (int side = -1; side <= 1; side += 2) {
            geometry.shownA = db * (out + side * DOOR_HALF);
            geometry.shownB = da * (out + side * DOOR_HALF);
            geometry.addFace(mesh, frame, x, y, z, a, b, 0f, da, db, 0f, 0f, 0f, top);
        }
        geometry.shownA = geometry.shownB = 0f;
        // A tile along a wall is 32 frame pixels across at tile scale 1.
        float stile = from + DOOR_STILE / 32f;
        if (!paintedOnWall(texture, a, b, da, db, stile, DOOR_READ_AT, 0f)) {
            return true;
        }
        float u = SpriteGeometry.textureU(frame, a + da * stile, b + db * stile), v = SpriteGeometry.textureV(frame, a + da * stile, b + db * stile, DOOR_READ_AT);
        mesh.open = frame.open + 32f * frame.material;
        mesh.sway = 0f;
        float nearX = db * (out - DOOR_HALF), nearY = da * (out - DOOR_HALF), farX = db * (out + DOOR_HALF), farY = da * (out + DOOR_HALF);
        float x0 = x + a + da * from, y0 = y + b + db * from, x1 = x + a + da * to, y1 = y + b + db * to;
        float foot = z * ViewCamera.LEVEL_HEIGHT + geometry.liftAt(a + da * 0.5f, b + db * 0.5f), head = foot + top * ViewCamera.LEVEL_HEIGHT;
        float[] c = reveal;
        for (int end = 0; end < 2; end++) {
            float ex = end == 0 ? x0 : x1, ey = end == 0 ? y0 : y1;
            set(c, 0, ex + nearX, ey + nearY, foot);
            set(c, 1, ex + farX, ey + farY, foot);
            set(c, 2, ex + farX, ey + farY, head);
            set(c, 3, ex + nearX, ey + nearY, head);
            SpriteGeometry.addQuad(mesh, c, u, v, frame.red, frame.green, frame.blue);
        }
        set(c, 0, x0 + nearX, y0 + nearY, head);
        set(c, 1, x0 + farX, y0 + farY, head);
        set(c, 2, x1 + farX, y1 + farY, head);
        set(c, 3, x1 + nearX, y1 + nearY, head);
        SpriteGeometry.addQuad(mesh, c, u, v, frame.red, frame.green, frame.blue);
        return true;
    }

    /** How bright the game's light in a room has to be for the room's windows to count as lit from inside. */
    private static final float LAMPLIT = 0.55f;

    /**
     * Turns the window on the north or west edge of the square being added to face into its
     * room, which is how the picture tells the street side of a pane from the room side, and
     * marks it as lit from inside when the room's light is a lamp's. A window between two
     * rooms, or between two places out of doors, faces into its own square.
     */
    private void faceIntoRoom(int x, int y, int z, boolean north) {
        IsoGridSquare beyond = cell.getGridSquare(north ? x : x - 1, north ? y - 1 : y, z);
        boolean ownIsRoom = here.getRoomID() != -1L || beyond == null || beyond.getRoomID() == -1L;
        // A face on a north edge looks north, out of its square, and one on a west edge east, into it.
        geometry.turned = north == ownIsRoom;
        IsoGridSquare room = ownIsRoom ? here : beyond;
        ColorInfo light = room.lighting[playerIndex].lightInfo();
        if (room.isSeen(playerIndex) && Math.max(light.r, Math.max(light.g, light.b)) > LAMPLIT) {
            frame.material = LIT_GLASS;
        }
    }

    /** How high a wall has to stand short of its level for its top to be closed: a full wall's top is under the floor or the roof over it. */
    private static final float CAPPED_BELOW = 0.97f;
    /** Where along a wall its colour is looked for, for the faces that close it. */
    private static final float[] CAP_COLOUR_AT = {0.5f, 0.12f, 0.88f};

    /**
     * Closes a wall where the eye could see in between its two skins: across an end that
     * nothing meets, a doorway with no frame or the last panel of a fence; over the top of one
     * that stops short of its level, a fence, a garden wall, a parapet; and under the foot of
     * one that stands on an upper floor, which is seen from below where the floor juts out.
     * Each is the colour the wall's sprite has low down on it.
     */
    private void addCaps(SpriteGeometry.Mesh mesh, Texture texture, float top, boolean openStart, boolean openEnd, boolean whole,
            int x, int y, int z, float a, float b, float da, float db, float inX, float inY) {
        boolean overTop = whole && top > 0f && top < CAPPED_BELOW, underFoot = whole && z > 0;
        if (top <= 0f || !openStart && !openEnd && !overTop && !underFoot) {
            return;
        }
        float u = 0f, v = 0f;
        boolean found = false;
        for (float along : CAP_COLOUR_AT) {
            if (paintedOnWall(texture, a, b, da, db, along, SEAL_HEIGHT, 0f)) {
                u = SpriteGeometry.textureU(frame, a + da * along, b + db * along);
                v = SpriteGeometry.textureV(frame, a + da * along, b + db * along, SEAL_HEIGHT);
                found = true;
                break;
            }
        }
        if (!found) {
            return;
        }
        mesh.open = frame.open + 32f * frame.material;
        mesh.sway = 0f;
        float x0 = x + a, y0 = y + b, x1 = x0 + da, y1 = y0 + db;
        float foot0 = z * ViewCamera.LEVEL_HEIGHT + geometry.liftAt(a, b), foot1 = z * ViewCamera.LEVEL_HEIGHT + geometry.liftAt(a + da, b + db);
        float high = top * ViewCamera.LEVEL_HEIGHT;
        float[] c = reveal;
        for (int end = 0; end < 2; end++) {
            if (end == 0 ? !openStart : !openEnd) {
                continue;
            }
            float ex = end == 0 ? x0 : x1, ey = end == 0 ? y0 : y1, foot = end == 0 ? foot0 : foot1;
            set(c, 0, ex - inX, ey - inY, foot);
            set(c, 1, ex + inX, ey + inY, foot);
            set(c, 2, ex + inX, ey + inY, foot + high);
            set(c, 3, ex - inX, ey - inY, foot + high);
            SpriteGeometry.addQuad(mesh, c, u, v, frame.red, frame.green, frame.blue);
        }
        for (int side = 0; side < 2; side++) {
            if (side == 0 ? !overTop : !underFoot) {
                continue;
            }
            float up = side == 0 ? high : 0f;
            set(c, 0, x0 - inX, y0 - inY, foot0 + up);
            set(c, 1, x0 + inX, y0 + inY, foot0 + up);
            set(c, 2, x1 + inX, y1 + inY, foot1 + up);
            set(c, 3, x1 - inX, y1 - inY, foot1 + up);
            SpriteGeometry.addQuad(mesh, c, u, v, frame.red, frame.green, frame.blue);
        }
    }

    /**
     * One skin of a wall. A wall with a door or a window in it is the wall round the opening,
     * in pieces, and not one face with a hole in its picture: the edges of the opening are then
     * edges of faces, straight, where the sprite has them as staircases of pixels on the slant.
     */
    private void addSkin(SpriteGeometry.Mesh mesh, float[] opening, int at, int x, int y, int z, float a, float b, float da, float db,
            float fromStart, float fromEnd, float fromTop, boolean startIsLow) {
        float start = opening[at + OPENING_START], end = opening[at + OPENING_END];
        // A skin carried on past its wall's end to the outside of a corner takes its texels
        // from as far inside the sprite as it would have, the sprite having nothing out there.
        float perTile = 32f * frame.scale;
        float first = -skinStart, last = 1f + skinEnd;
        fromStart += skinStart * perTile;
        fromEnd += skinEnd * perTile;
        if (start < 0f) {
            addPiece(mesh, x, y, z, a, b, da, db, first, last, 0f, 1f, fromStart, fromEnd, fromTop, startIsLow);
            return;
        }
        float foot = opening[at + OPENING_FOOT], head = opening[at + OPENING_HEAD];
        addPiece(mesh, x, y, z, a, b, da, db, first, start, 0f, 1f, fromStart, 0f, fromTop, startIsLow);
        addPiece(mesh, x, y, z, a, b, da, db, end, last, 0f, 1f, 0f, fromEnd, fromTop, startIsLow);
        if (head < 1f) {
            addPiece(mesh, x, y, z, a, b, da, db, start, end, head, 1f, 0f, 0f, fromTop, startIsLow);
        }
        if (foot > 0f) {
            addPiece(mesh, x, y, z, a, b, da, db, start, end, 0f, foot, 0f, 0f, 0f, startIsLow);
        }
    }

    /** The part of a wall from one share of its length to another and from one height to another, in levels. */
    private void addPiece(SpriteGeometry.Mesh mesh, int x, int y, int z, float a, float b, float da, float db,
            float from, float to, float foot, float head, float sealFrom, float sealTo, float sealTop, boolean startIsLow) {
        geometry.sealLow = startIsLow ? sealFrom : sealTo;
        geometry.sealHigh = startIsLow ? sealTo : sealFrom;
        geometry.sealTop = sealTop;
        geometry.addFace(mesh, frame, x, y, z, a + da * from, b + db * from, foot, da * (to - from), db * (to - from), 0f, 0f, 0f, head - foot);
    }

    /**
     * The sides of an opening through a wall, from one skin to the other: the jambs, the
     * underside of the lintel and the sill. Without them the eye sees in between the two skins.
     */
    private void addReveals(float[] opening, int at, int x, int y, int z, float a, float b, float da, float db, float inX, float inY) {
        SpriteGeometry.Mesh plain = solid.get(PLAIN);
        plain.open = frame.open + 32f * CONCRETE;
        float lift = (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f;
        float foot = (z + opening[at + OPENING_FOOT]) * ViewCamera.LEVEL_HEIGHT + lift, head = (z + opening[at + OPENING_HEAD]) * ViewCamera.LEVEL_HEIGHT + lift;
        float x0 = x + a + da * opening[at + OPENING_START], y0 = y + b + db * opening[at + OPENING_START];
        float x1 = x + a + da * opening[at + OPENING_END], y1 = y + b + db * opening[at + OPENING_END];
        float[] c = reveal;
        for (int side = 0; side < 2; side++) {
            float jx = side == 0 ? x0 : x1, jy = side == 0 ? y0 : y1;
            set(c, 0, jx - inX, jy - inY, foot);
            set(c, 1, jx + inX, jy + inY, foot);
            set(c, 2, jx + inX, jy + inY, head);
            set(c, 3, jx - inX, jy - inY, head);
            SpriteGeometry.addPlainQuad(plain, c, frame.red, frame.green, frame.blue);
        }
        for (int side = 0; side < 2; side++) {
            if (side == 0 ? opening[at + OPENING_HEAD] >= 1f : opening[at + OPENING_FOOT] <= 0f) {
                continue;
            }
            float up = side == 0 ? head : foot;
            set(c, 0, x0 - inX, y0 - inY, up);
            set(c, 1, x0 + inX, y0 + inY, up);
            set(c, 2, x1 + inX, y1 + inY, up);
            set(c, 3, x1 - inX, y1 - inY, up);
            SpriteGeometry.addPlainQuad(plain, c, frame.red, frame.green, frame.blue);
        }
    }

    /** For each of the two edges a wall can stand on: the frame origin the sprite was read with, then the opening. */
    private static final int OPENING_FLOATS = 7, OPENING_ORIGIN = 0, OPENING_START = 2, OPENING_END = 3, OPENING_FOOT = 4, OPENING_HEAD = 5, OPENING_TOP = 6;
    /** Where along a wall its height is read, and the lowest a wall's top is looked for, in levels. */
    private static final float[] TOP_READ_AT = {0.15f, 0.5f, 0.85f};
    private static final float LOWEST_TOP = 0.05f;

    /**
     * Where the door or window through a wall is, read off the wall's sprite: at at + OPENING_START
     * and OPENING_END the shares of the wall's length it starts and ends at, and at OPENING_FOOT
     * and OPENING_HEAD the heights of its sill and its lintel, in levels. A start below 0 is a
     * wall with no opening, or with one that is not a rectangle, an arched window, or with more
     * than one. Read once for each sprite on each edge.
     */
    private float[] openingOf(Texture texture, float a, float b, float da, float db) {
        int at = da != 0f ? 0 : OPENING_FLOATS;
        float[] known = openings.get(texture);
        if (known == null) {
            known = new float[2 * OPENING_FLOATS];
            java.util.Arrays.fill(known, Float.NaN);
            openings.put(texture, known);
        }
        if (known[at + OPENING_ORIGIN] == frame.originU && known[at + OPENING_ORIGIN + 1] == frame.originV) {
            return known;
        }
        known[at + OPENING_ORIGIN] = frame.originU;
        known[at + OPENING_ORIGIN + 1] = frame.originV;
        known[at + OPENING_START] = -1f;
        known[at + OPENING_TOP] = wallTop(texture, a, b, da, db);
        int pixels = Math.round(Math.abs(SpriteGeometry.frameU(frame, a + da, b + db) - SpriteGeometry.frameU(frame, a, b)));
        int first = -1, last = -1, gaps = 0, run = -1;
        for (int pixel = 0; pixel <= pixels; pixel++) {
            boolean clear = pixel < pixels && !paintedOnWall(texture, a, b, da, db, (pixel + 0.5f) / pixels, OPENING_ROW, 0f);
            if (clear && run < 0) {
                run = pixel;
            } else if (!clear && run >= 0) {
                // A gap that reaches the wall's end is the sprite stopping short there, not an opening.
                if (run > 0 && pixel < pixels && pixel - run >= NARROWEST_OPENING * pixels) {
                    gaps++;
                    first = run;
                    last = pixel;
                }
                run = -1;
            }
        }
        if (gaps != 1) {
            return known;
        }
        float start = (float) first / pixels, end = (float) last / pixels, middle = (start + end) / 2f;
        float perPixel = 1f / (96f * frame.scale);
        float head = 1f, foot = 0f;
        for (float h = OPENING_ROW; h < 1f; h += perPixel) {
            if (paintedOnWall(texture, a, b, da, db, middle, h, 0f)) {
                head = h - perPixel / 2f;
                break;
            }
        }
        for (float h = OPENING_ROW; h > 0f; h -= perPixel) {
            if (paintedOnWall(texture, a, b, da, db, middle, h, 0f)) {
                foot = h + perPixel / 2f;
                break;
            }
        }
        // A rectangle is clear right into its corners. An arch is not.
        float inAlong = OPENING_CORNER * frame.scale / pixels, inUp = OPENING_CORNER * frame.scale * perPixel;
        for (int corner = 0; corner < 4; corner++) {
            float along = (corner & 1) == 0 ? start + inAlong : end - inAlong;
            float up = corner < 2 ? foot + inUp : head - inUp;
            if (paintedOnWall(texture, a, b, da, db, along, up, 0f)) {
                return known;
            }
        }
        known[at + OPENING_START] = start;
        known[at + OPENING_END] = end;
        known[at + OPENING_FOOT] = foot;
        known[at + OPENING_HEAD] = head;
        return known;
    }

    /**
     * How high a wall stands, in levels: the lowest its sprite's top is drawn at three places
     * along it, so that the top of a fence whose boards are cut to a curve is closed below
     * the lowest of them. 0 for a sprite with nothing painted at any of the three.
     */
    private float wallTop(Texture texture, float a, float b, float da, float db) {
        float perPixel = 1f / (96f * frame.scale);
        float lowest = 0f;
        for (float along : TOP_READ_AT) {
            for (float h = 1f; h > LOWEST_TOP; h -= perPixel) {
                if (paintedOnWall(texture, a, b, da, db, along, h, 0f)) {
                    float top = Math.min(h + perPixel / 2f, 1f);
                    lowest = lowest == 0f ? top : Math.min(lowest, top);
                    break;
                }
            }
        }
        return lowest;
    }

    /** Whether the sprite is painted where it shows the point a share along the wall and up levels high, down frame pixels lower. */
    private boolean paintedOnWall(Texture texture, float a, float b, float da, float db, float along, float up, float down) {
        float u = SpriteGeometry.frameU(frame, a + da * along, b + db * along);
        float v = SpriteGeometry.frameV(frame, a + da * along, b + db * along, up) + down;
        return u >= frame.left && u < frame.left + frame.width && v >= frame.top && v < frame.top + frame.height
                && texture.isMaskSet((int) u, (int) v);
    }

    /**
     * How many frame pixels down from the wall's top its sprite has nothing painted: the most
     * found a quarter, a half and three quarters of the way along it. 0 when it is painted
     * from the top, and where nothing is within MOST_UNPAINTED: a low wall stops where it stops.
     */
    private float unpaintedTop(Texture texture, float a, float b, float da, float db) {
        int most = Math.round(MOST_UNPAINTED * frame.scale);
        float deepest = 0f;
        for (int place = 1; place <= 3; place++) {
            for (int pixel = 0; pixel <= most; pixel++) {
                if (paintedOnWall(texture, a, b, da, db, place / 4f, 1f, pixel + 0.5f)) {
                    deepest = Math.max(deepest, pixel);
                    break;
                }
            }
        }
        return deepest;
    }

    /** How high the top of a door's sprite is drawn half way along it, in levels. A level for a sprite painted that high. */
    private float doorTop(Texture texture, float a, float b, float da, float db) {
        float perPixel = 1f / (96f * frame.scale);
        for (float h = 1f; h > LOWEST_DOOR; h -= perPixel) {
            if (paintedOnWall(texture, a, b, da, db, 0.5f, h, 0f)) {
                return Math.min(h + perPixel / 2f, 1f);
            }
        }
        return 1f;
    }

    /**
     * What the far skin of the wall on the north or west edge of the square being added is
     * painted over in, as WallSides packs it, and into farLight where the light of the place
     * beyond it is. 0 where the far skin shows the sprite as the near skin does: a wall within
     * one room, a fence or a wall in the open, a wall whose far side shows the same sprite, a
     * building with no wall that shows its finish, and a sprite the picture has not read yet.
     */
    private int farTint(Texture texture, int x, int y, int z, boolean north) {
        farColour = null;
        int beyondX = north ? x : x - 1, beyondY = north ? y - 1 : y;
        IsoGridSquare beyond = cell.getGridSquare(beyondX, beyondY, z);
        long own = here.getRoomID(), far = beyond == null ? -1L : beyond.getRoomID();
        farLight = -1;
        farFacing = null;
        if (own == far) {
            return 0;
        }
        // Above the ground a square of open air is often no square at all. On the ground it is map not loaded yet.
        if (beyond == null && cell.getGridSquare(beyondX, beyondY, 0) == null) {
            return 0;
        }
        int ownLight = (waterLocalX + 1) * LIGHT_SPAN + waterLocalY + 1;
        farLight = north ? ownLight - 1 : ownLight - LIGHT_SPAN;
        IsoBuilding building = far == -1L ? here.getBuilding() : beyond.getBuilding();
        if (building == null) {
            return 0;
        }
        Texture shows = far == -1L ? WallFinish.outside(cell, building, z, frames) : WallFinish.room(cell, building, z, far, frames);
        if (far == -1L) {
            farFacing = WallFinish.facing(cell, building, z, north, frames);
        }
        if (shows == null || shows == texture) {
            return 0;
        }
        float[] colour = SpriteColours.of(shows);
        if (colour == null) {
            return 0;
        }
        farColour = colour;
        return WallSides.key(colour[0], colour[1], colour[2], far == -1L ? WallSides.SIDING : WallSides.FLAT);
    }

    /** The colour farTint last gave a far skin, or null where it gave none. */
    private float[] farColour;

    /**
     * The outside of a wall that is one flat colour has the two lines every storey of a
     * building has: a plinth along its foot and a course under its top, each a shade off the
     * wall and standing a finger proud of it. They are what says, from down the street, where
     * one floor ends and the next begins. The plinth stops either side of a doorway.
     */
    private void addCourses(float[] opening, int at, int x, int y, int z, float a, float b, float da, float db, float outX, float outY) {
        SpriteGeometry.Mesh plain = solid.get(PLAIN);
        plain.open = frame.open;
        plain.sway = 0f;
        float doorFrom = -1f, doorTo = -1f;
        if (opening[at + OPENING_START] >= 0f && opening[at + OPENING_FOOT] < Courses.DOORWAY_FOOT) {
            doorFrom = opening[at + OPENING_START];
            doorTo = opening[at + OPENING_END];
        }
        float x0 = x + a + outX, y0 = y + b + outY, base = z * ViewCamera.LEVEL_HEIGHT;
        float lift0 = geometry.liftAt(a, b), lift1 = geometry.liftAt(a + da, b + db);
        Courses.add(plain, x0, y0, base + lift0, x0 + da, y0 + db, base + lift1, outX, outY, doorFrom, doorTo,
                farColour[0] * frame.red, farColour[1] * frame.green, farColour[2] * frame.blue);
    }

    /** The level the walls that meet the wall being added are looked for on. */
    private int joinLevel;
    /** Whether a wall with a thickness stands on the north or west edge of a square of joinLevel. */
    private final WallJoins.Walls standing = (wallX, wallY, north) -> {
        IsoGridSquare square = cell.getGridSquare(wallX, wallY, joinLevel);
        IsoObject wall = square == null ? null : wallOn(square, north);
        return wall != null && !isSheet(wall.getSprite().getProperties());
    };
    /** Whether the wall being added is a sheet: see isSheet. */
    private boolean seeThrough;
    /** How far the skin being added runs on past its wall's two ends, in tiles: see WallJoins. */
    private float skinStart, skinEnd;

    /** The wall standing on the square's north or west edge that has a sprite to show, or null. */
    static IsoObject wallOn(IsoGridSquare square, boolean north) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object.getSprite();
            if (sprite == null) {
                continue;
            }
            PropertyContainer properties = sprite.getProperties();
            IsoObjectType type = sprite.getType();
            if (!isWall(properties, type) || !(properties.has(IsoFlagType.WallNW)
                    || (north ? standsOnNorthEdge(properties, type) : standsOnWestEdge(properties, type)))) {
                continue;
            }
            Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
            if (texture != null && texture.getID() > 0 && texture.getWidth() > 0 && texture.getHeight() > 0) {
                return object;
            }
        }
        return null;
    }

    /**
     * How many frame pixels along (da, db) from (a, b), at SEAL_HEIGHT, the sprite has nothing
     * painted. 0 when it is painted from the start, and when nothing is within MOST_UNPAINTED:
     * that is a sprite which is not a wall from edge to edge, and it is left as it is.
     */
    private float unpainted(Texture texture, float a, float b, float da, float db) {
        float pixels = Math.abs(SpriteGeometry.frameU(frame, a + da, b + db) - SpriteGeometry.frameU(frame, a, b));
        int most = Math.round(MOST_UNPAINTED * frame.scale);
        for (int pixel = 0; pixel <= most; pixel++) {
            float along = (pixel + 0.5f) / pixels;
            float u = SpriteGeometry.frameU(frame, a + da * along, b + db * along);
            float v = SpriteGeometry.frameV(frame, a + da * along, b + db * along, SEAL_HEIGHT);
            if (u >= frame.left && u < frame.left + frame.width && v >= frame.top && v < frame.top + frame.height
                    && texture.isMaskSet((int) u, (int) v)) {
                return pixel;
            }
        }
        return 0f;
    }

    /**
     * Whether the sprite is painted all over the box its outline describes: a fridge, a counter,
     * a bed. A chair or a table has air where the middle of a side would be, and wrapped onto a
     * box it comes apart, so those stay upright cards.
     */
    private boolean fillsBox(Texture texture) {
        float a = box[0], b = box[1], half = box[2], height = box[3];
        return painted(texture, a, b, height) && painted(texture, a, b + half, height * 0.5f) && painted(texture, a + half, b, height * 0.5f)
                && painted(texture, a, b + half, height * 0.2f) && painted(texture, a + half, b, height * 0.2f)
                && painted(texture, a - half * 0.5f, b + half, height * 0.8f) && painted(texture, a + half, b - half * 0.5f, height * 0.8f);
    }

    private boolean fillsMiddle(Texture texture) {
        float a = box[0], b = box[1], half = box[2], height = box[3];
        return painted(texture, a, b, height) && painted(texture, a, b + half, height * 0.5f) && painted(texture, a + half, b, height * 0.5f)
                && painted(texture, a, b + half, height * 0.15f) && painted(texture, a + half, b, height * 0.15f)
                && painted(texture, a, b + half, height * 0.85f) && painted(texture, a + half, b, height * 0.85f);
    }

    private boolean painted(Texture texture, float a, float b, float h) {
        return texture.isMaskSet(Math.round(SpriteGeometry.frameU(frame, a, b)), Math.round(SpriteGeometry.frameV(frame, a, b, h)));
    }

    /**
     * A roof tile, laid where its whole roof puts it: see Roofs. The sprite is drawn as its
     * own outline reads, so that all of it shows, and stood on the plane the roof's fit gave
     * the tile, so that it meets the tiles beside it.
     */
    private void addRoof(SpriteGeometry.Mesh mesh, Texture texture, int x, int y, int z) {
        RoofFit.Tile own = Roofs.measure(frame, texture, x, y, z);
        Roofs.Laid laid = Roofs.at(cell, x, y, z, frames, frame.scale);
        // A square with two roof sprites has one of them fitted. The other lies as it reads.
        RoofFit.Tile tile = laid != null && laid.texture == texture ? laid.tile : own;
        roof = laid == null ? null : laid.roof;
        float northWest = own.low - z, southEast = own.high - z;
        if (Math.abs(northWest - southEast) > steepest) {
            steepest = Math.abs(northWest - southEast);
            steepestSaid = String.format(Locale.ROOT, "%s at (%d, %d, %d) nw %.2f se %.2f frame %.0fx%.0f at (%.0f, %.0f) origin (%.0f, %.0f) scale %.0f",
                    roofName, x, y, z, northWest, southEast, frame.width, frame.height, frame.left, frame.top, frame.originU, frame.originV, frame.scale);
        }
        if (tile.shaped) {
            // Two slopes on one square: each half of the sprite goes on its own, and where
            // the map's slope is only one of the two the other half is the mod's own sheet.
            int[] order = RoofFit.TRIANGLES[tile.crease];
            for (int triangle = 0; triangle < 2; triangle++) {
                if ((tile.own >> triangle & 1) == 0) {
                    continue;
                }
                for (int i = 0; i < 3; i++) {
                    int corner = order[triangle * 3 + i];
                    set(folded, i, RoofFit.cornerX(corner), RoofFit.cornerY(corner), tile.corners[corner] - z);
                }
                geometry.addTriangle(mesh, frame, x, y, z, folded);
            }
            roofsFolded++;
            SpriteGeometry.Mesh sheet = roofSheet();
            addFolded(sheet, tile, tile.own, true);
            addFolded(sheet, tile, ~tile.own & 3, false);
            addFoldEaves(sheet, tile);
            return;
        }
        if (own.axis == RoofFit.LEVEL) {
            roofsFlat++;
            float level = (northWest + southEast) / 2f;
            // A flat roof that lies on its level has the floor there under it already.
            if (geometry.addFace(mesh, frame, x, y, z, 0f, 0f, level, 1f, 0f, 0f, 0f, 1f, 0f) > 0 && level > 2f * UNDER_ROOF) {
                underRoof(x, y, z, level, 0f, 0f);
            }
            return;
        }
        boolean readAlongX = own.axis == RoofFit.ALONG_X, alongX = tile.fitAxis == RoofFit.ALONG_X;
        float low = tile.fitLow - z, high = tile.fitHigh - z;
        float[] lift = geometry.lift;
        for (int corner = 0; corner < 4; corner++) {
            boolean farA = corner == 1 || corner == 2, farB = corner >= 2;
            float read = (readAlongX ? farA : farB) ? southEast : northWest;
            float fitted = (alongX ? farA : farB) ? high : low;
            heldLift[corner] = lift[corner];
            lift[corner] += (fitted - read) * ViewCamera.LEVEL_HEIGHT;
        }
        int added = readAlongX ? geometry.addFace(mesh, frame, x, y, z, 0f, 0f, northWest, 1f, 0f, southEast - northWest, 0f, 1f, 0f)
                : geometry.addFace(mesh, frame, x, y, z, 0f, 0f, northWest, 1f, 0f, 0f, 0f, 1f, southEast - northWest);
        System.arraycopy(heldLift, 0, lift, 0, 4);
        boolean placed = tile.fitted || own.sure;
        if (alongX) {
            roofsWestEast++;
        } else if (placed) {
            roofsNorthSouth++;
        } else {
            roofsUnclear++;
        }
        if (added == 0) {
            return;
        }
        if (placed) {
            underRoof(x, y, z, low, alongX ? high - low : 0f, alongX ? 0f : high - low);
        } else if (frame.width > Roofs.WHOLE_TILE_WIDE * frame.scale) {
            // Which way this tile slopes is a guess, and a sheet laid to a wrong guess
            // stands out of the roof. A level one at the tile's lowest height cannot: it
            // is under the tile whichever way it slopes, and still closes the roof.
            underRoof(x, y, z, Math.min(low, high), 0f, 0f);
        }
    }

    /**
     * The game drew each roof tile from one side, and a slope that faces away from that side
     * is a sliver of sprite, or none: seen from the other side the roof has holes with the
     * rooms showing through. Under every tile of roof goes a plain sheet of the same slope, in
     * the grey of shingle, which is what shows wherever the sprite has nothing.
     */
    private void underRoof(int x, int y, int z, float atOrigin, float alongX, float alongY) {
        SpriteGeometry.Mesh sheet = roofSheet();
        float[] lift = geometry.lift;
        SpriteGeometry.addOver(sheet, x, y, under(z, atOrigin) + lift[0], under(z, atOrigin + alongX) + lift[1],
                under(z, atOrigin + alongX + alongY) + lift[2], under(z, atOrigin + alongY) + lift[3],
                roofU, roofV, frame.red, frame.green, frame.blue);
        addEave(sheet, x, y, z, atOrigin, alongX, alongY, true);
        addRake(sheet, x, y, z, atOrigin, alongX, alongY);
    }

    /**
     * The mesh the mod's own roofing goes in, and into roofU and roofV the texel all of it
     * takes. It is the colour most of the roof's own sprite is, painted flat over a painted
     * place on that sprite, so a slope the sprite shows nothing of is the colour of the
     * slopes it does show. Until the picture has read that colour, and for a roof with no
     * sprite to read, it is plain shingle grey.
     */
    private SpriteGeometry.Mesh roofSheet() {
        float[] colour = roof == null || roof.finish == null ? null : SpriteColours.of(roof.finish);
        SpriteGeometry.Mesh sheet;
        if (colour == null) {
            sheet = solid.get(PLAIN);
            roofU = roofV = 0f;
        } else {
            sheet = solid.get(roof.finish.getID(), WallSides.key(colour[0], colour[1], colour[2], WallSides.FLAT));
            roofU = roof.u;
            roofV = roof.v;
        }
        sheet.open = frame.open + 32f * SHINGLES;
        return sheet;
    }

    /**
     * At a gable the roof runs a little past the end wall too: where a sloping tile has no roof
     * beside it across its slope, a strip of roof carries its slope out over the wall.
     */
    private void addRake(SpriteGeometry.Mesh sheet, int x, int y, int z, float atOrigin, float alongX, float alongY) {
        if (Math.abs(alongX + alongY) < Roofs.FLAT) {
            return;
        }
        float lift = (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f;
        float low = (z + atOrigin) * ViewCamera.LEVEL_HEIGHT + lift, high = (z + atOrigin + alongX + alongY) * ViewCamera.LEVEL_HEIGHT + lift;
        float[] c = eave;
        for (int side = 0; side < 2; side++) {
            int besideX = x + (alongX != 0f ? 0 : side == 0 ? -1 : 1), besideY = y + (alongX != 0f ? side == 0 ? -1 : 1 : 0);
            if (hasRoof(besideX, besideY, z)) {
                continue;
            }
            float edge = side, out = side == 0 ? -RAKE : RAKE;
            if (alongX != 0f) {
                set(c, 0, x, y + edge, low);
                set(c, 1, x + 1f, y + edge, high);
                set(c, 2, x + 1f, y + edge + out, high);
                set(c, 3, x, y + edge + out, low);
            } else {
                set(c, 0, x + edge, y, low);
                set(c, 1, x + edge, y + 1f, high);
                set(c, 2, x + edge + out, y + 1f, high);
                set(c, 3, x + edge + out, y, low);
            }
            SpriteGeometry.addQuad(sheet, c, roofU, roofV, frame.red, frame.green, frame.blue);
        }
    }

    /**
     * A roof does not stop at the wall. Where a sloping tile is the lowest of its slope, with
     * no roof beyond its low edge, the slope runs on past the wall and a little down: the
     * eave, with a board closing its end. piped is whether the gutter may empty into a pipe here.
     */
    private void addEave(SpriteGeometry.Mesh sheet, int x, int y, int z, float atOrigin, float alongX, float alongY, boolean piped) {
        addEave(sheet, x, y, z, atOrigin, alongX, alongY, piped, false, false);
    }

    /**
     * Whether the low edge of a sloping tile is an eave: the slope has a fall, and no roof
     * goes on beyond it. A slope that comes down to the foot of its level goes on in the top
     * of the level below: the tile beyond is there, and this is the middle of the roof.
     */
    private boolean isEave(int x, int y, int z, float atOrigin, float alongX, float alongY) {
        float along = alongX != 0f ? alongX : alongY;
        if (Math.abs(along) < Roofs.FLAT) {
            return false;
        }
        boolean lowAtFar = along < 0f;
        int beyondX = x + (alongX != 0f ? (lowAtFar ? 1 : -1) : 0), beyondY = y + (alongY != 0f ? (lowAtFar ? 1 : -1) : 0);
        float low = lowAtFar ? atOrigin + along : atOrigin;
        return !hasRoof(beyondX, beyondY, z) && !(low < Roofs.FLAT && hasRoof(beyondX, beyondY, z - 1));
    }

    /**
     * As addEave, for an eave that meets another at one or both ends of its run, the end at
     * the square's north or west and the one at its south or east: see Eaves.
     */
    private void addEave(SpriteGeometry.Mesh sheet, int x, int y, int z, float atOrigin, float alongX, float alongY, boolean piped,
            boolean meetsAtStart, boolean meetsAtEnd) {
        if (!isEave(x, y, z, atOrigin, alongX, alongY)) {
            return;
        }
        float along = alongX != 0f ? alongX : alongY;
        boolean lowAtFar = along < 0f;
        float low = lowAtFar ? atOrigin + along : atOrigin;
        float out = lowAtFar ? EAVE : -EAVE;
        float edge = lowAtFar ? 1f : 0f;
        float drop = -Math.abs(along) * EAVE;
        float lift = (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f;
        float high = (z + low) * ViewCamera.LEVEL_HEIGHT + lift, far = (z + low + drop) * ViewCamera.LEVEL_HEIGHT + lift;
        boolean runsY = alongX != 0f;
        int startEnd = Eaves.end(meetsAtStart, hasRoof(runsY ? x : x - 1, runsY ? y - 1 : y, z));
        int endEnd = Eaves.end(meetsAtEnd, hasRoof(runsY ? x : x + 1, runsY ? y + 1 : y, z));
        // The run's two ends, at the wall and at the eave's edge: where it starts and stops along the run.
        float wallFrom = -Eaves.atWall(startEnd, RAKE), edgeFrom = -Eaves.atEdge(startEnd, EAVE, RAKE);
        float wallTo = 1f + Eaves.atWall(endEnd, RAKE), edgeTo = 1f + Eaves.atEdge(endEnd, EAVE, RAKE);
        float[] c = eave;
        if (runsY) {
            set(c, 0, x + edge, y + wallFrom, high);
            set(c, 1, x + edge + out, y + edgeFrom, far);
            set(c, 2, x + edge + out, y + edgeTo, far);
            set(c, 3, x + edge, y + wallTo, high);
        } else {
            set(c, 0, x + wallFrom, y + edge, high);
            set(c, 1, x + edgeFrom, y + edge + out, far);
            set(c, 2, x + edgeTo, y + edge + out, far);
            set(c, 3, x + wallTo, y + edge, high);
        }
        SpriteGeometry.addQuad(sheet, c, roofU, roofV, frame.red, frame.green, frame.blue);
        // The soffit: the boarded underside of the eave, level from the fascia's foot back to
        // the wall, which is what is seen of a roof from the ground beside the house.
        SpriteGeometry.Mesh plain = solid.get(PLAIN);
        plain.open = frame.open + 32f * CONCRETE;
        float under = far - FASCIA;
        float wx1 = c[0], wy1 = c[1], wx2 = c[9], wy2 = c[10], ex1 = c[3], ey1 = c[4], ex2 = c[6], ey2 = c[7];
        set(c, 0, wx1, wy1, under);
        set(c, 1, ex1, ey1, under);
        set(c, 2, ex2, ey2, under);
        set(c, 3, wx2, wy2, under);
        SpriteGeometry.addPlainQuad(plain, c, frame.red, frame.green, frame.blue);
        // The board along the eave's end.
        plain.open = frame.open;
        float x1 = ex1, y1 = ey1, x2 = ex2, y2 = ey2;
        set(c, 0, x1, y1, far);
        set(c, 1, x2, y2, far);
        set(c, 2, x2, y2, far - FASCIA);
        set(c, 3, x1, y1, far - FASCIA);
        SpriteGeometry.addPlainQuad(plain, c, 0.55f * frame.red, 0.55f * frame.green, 0.53f * frame.blue);
        // Where the roof stops at a gable the eave is a hollow wedge seen end on, and is
        // closed with a board of its own.
        for (int end = 0; end < 2; end++) {
            if ((end == 0 ? startEnd : endEnd) != Eaves.GABLE) {
                continue;
            }
            float wx = end == 0 ? wx1 : wx2, wy = end == 0 ? wy1 : wy2, ex = end == 0 ? ex1 : ex2, ey = end == 0 ? ey1 : ey2;
            set(c, 0, wx, wy, high);
            set(c, 1, ex, ey, far);
            set(c, 2, ex, ey, under);
            set(c, 3, wx, wy, under);
            SpriteGeometry.addPlainQuad(plain, c, 0.55f * frame.red, 0.55f * frame.green, 0.53f * frame.blue);
        }
        // The gutter along the eave: a trough standing out from the board, its outer face and
        // its underside.
        float ox = alongX != 0f ? Math.signum(out) * GUTTER : 0f, oy = alongX != 0f ? 0f : Math.signum(out) * GUTTER;
        plain.open = frame.open + 32f * QUAY;
        set(c, 0, x1 + ox, y1 + oy, far + 0.01f);
        set(c, 1, x2 + ox, y2 + oy, far + 0.01f);
        set(c, 2, x2 + ox, y2 + oy, far - GUTTER);
        set(c, 3, x1 + ox, y1 + oy, far - GUTTER);
        SpriteGeometry.addPlainQuad(plain, c, frame.red, frame.green, frame.blue);
        set(c, 0, x1, y1, far - GUTTER);
        set(c, 1, x2, y2, far - GUTTER);
        set(c, 2, x2 + ox, y2 + oy, far - GUTTER);
        set(c, 3, x1 + ox, y1 + oy, far - GUTTER);
        SpriteGeometry.addPlainQuad(plain, c, frame.red, frame.green, frame.blue);
        // Where the eave ends, at the corner of the building, the gutter empties into a pipe
        // down the wall to the ground.
        // Of the gutter's dark metal: pale, it reads as a post standing at the foot of the gable.
        if (piped && startEnd != Eaves.RUNS_ON) {
            int beyondX = x + (alongX != 0f ? (lowAtFar ? 1 : -1) : 0), beyondY = y + (alongY != 0f ? (lowAtFar ? 1 : -1) : 0);
            float foot = Eaves.pipeFoot(z, level -> under(beyondX, beyondY, level));
            // Against the wall at the building's corner, whatever the eave does past it.
            float px = runsY ? x + edge + ox * 0.5f : x, py = runsY ? y : y + edge + oy * 0.5f;
            float head = far - GUTTER;
            if (!Float.isNaN(foot) && head - foot - lift >= Eaves.SHORTEST_PIPE) {
                foot += lift;
                pipeFrom[0] = pipeTo[0] = px;
                pipeFrom[1] = pipeTo[1] = py;
                pipeFrom[2] = foot + Eaves.SHOE_HIGH;
                pipeTo[2] = head;
                SpriteGeometry.addStrut(plain, pipeFrom, pipeTo, PIPE, 0f, 0f, frame.red, frame.green, frame.blue);
                // The shoe: the pipe's foot turned out from the wall, to throw the water clear of it.
                pipeTo[0] = px + Math.signum(ox) * Eaves.SHOE_OUT;
                pipeTo[1] = py + Math.signum(oy) * Eaves.SHOE_OUT;
                pipeTo[2] = foot + PIPE;
                SpriteGeometry.addStrut(plain, pipeFrom, pipeTo, PIPE, 0f, 0f, frame.red, frame.green, frame.blue);
            }
        }
    }

    private final float[] pipeFrom = new float[3], pipeTo = new float[3];

    /** What is under an eave on the square past it, a level at a time going down: see Eaves.pipeFoot. */
    private int under(int x, int y, int level) {
        IsoGridSquare square = cell.getGridSquare(x, y, level);
        if (square == null) {
            return Eaves.AIR;
        }
        if (square.getRoomID() != -1L || Roofs.roofing(square) || Roofs.madeAt(x, y, level, frames) != null) {
            return Eaves.ROOM;
        }
        return square.getFloor() != null ? Eaves.FLOOR : Eaves.AIR;
    }

    private static void set(float[] c, int corner, float x, float y, float h) {
        c[corner * 3] = x;
        c[corner * 3 + 1] = y;
        c[corner * 3 + 2] = h;
    }

    private boolean hasRoof(int x, int y, int z) {
        if (Roofs.madeAt(x, y, z, frames) != null) {
            return true;
        }
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        return square != null && Roofs.roofing(square);
    }

    /**
     * A flight is three squares, each a third of a level, rising towards -x (W) or -y (N).
     * Returns false, adding nothing, for a sprite that is not a stair.
     */
    private boolean addStair(SpriteGeometry.Mesh mesh, Texture texture, IsoObjectType type, int x, int y, int z) {
        int third = type == IsoObjectType.stairsBW || type == IsoObjectType.stairsBN ? 0
                : type == IsoObjectType.stairsMW || type == IsoObjectType.stairsMN ? 1
                : type == IsoObjectType.stairsTW || type == IsoObjectType.stairsTN ? 2 : -1;
        if (third < 0) {
            return false;
        }
        float top = (third + 1) / 3f;
        boolean west = type == IsoObjectType.stairsBW || type == IsoObjectType.stairsMW || type == IsoObjectType.stairsTW;
        int added = west ? geometry.addFace(mesh, frame, x, y, z, 0f, 0f, top, 1f, 0f, -1f / 3f, 0f, 1f, 0f)
                : geometry.addFace(mesh, frame, x, y, z, 0f, 0f, top, 0f, 1f, -1f / 3f, 1f, 0f, 0f);
        // A flight is solid under its treads: left as the slope alone it is a ramp of paper
        // seen from the side, with the room showing under it. Each square is closed down
        // to the floor along both its sides, and the top one across its back, in the colour
        // the stair has half way up the square. Against a wall the side is inside the wall.
        float middle = top - 1f / 6f;
        if (added == 0 || !painted(texture, 0.5f, 0.5f, middle)) {
            return true;
        }
        float u = SpriteGeometry.textureU(frame, 0.5f, 0.5f), v = SpriteGeometry.textureV(frame, 0.5f, 0.5f, middle);
        mesh.open = frame.open + 32f * frame.material;
        mesh.sway = 0f;
        float floor = z * ViewCamera.LEVEL_HEIGHT;
        float high = top * ViewCamera.LEVEL_HEIGHT, low = (top - 1f / 3f) * ViewCamera.LEVEL_HEIGHT;
        float[] c = reveal;
        for (int side = 0; side <= 1; side++) {
            // The high end of the slope, then the low end, along this side of the square.
            float hx = west ? 0f : side, hy = west ? side : 0f, lx = west ? 1f : side, ly = west ? side : 1f;
            float highFoot = floor + geometry.liftAt(hx, hy), lowFoot = floor + geometry.liftAt(lx, ly);
            set(c, 0, x + hx, y + hy, highFoot + high);
            set(c, 1, x + lx, y + ly, lowFoot + low);
            set(c, 2, x + lx, y + ly, lowFoot);
            set(c, 3, x + hx, y + hy, highFoot);
            SpriteGeometry.addQuad(mesh, c, u, v, frame.red, frame.green, frame.blue);
        }
        if (third == 2) {
            float foot0 = floor + geometry.liftAt(0f, 0f), foot1 = floor + (west ? geometry.liftAt(0f, 1f) : geometry.liftAt(1f, 0f));
            float bx = west ? 0f : 1f, by = west ? 1f : 0f;
            set(c, 0, x, y, foot0 + high);
            set(c, 1, x + bx, y + by, foot1 + high);
            set(c, 2, x + bx, y + by, foot1);
            set(c, 3, x, y, foot0);
            SpriteGeometry.addQuad(mesh, c, u, v, frame.red, frame.green, frame.blue);
        }
        return true;
    }

    private static boolean isWall(PropertyContainer properties, IsoObjectType type) {
        return type == IsoObjectType.wall || properties.has(IsoFlagType.WallW) || properties.has(IsoFlagType.WallN)
                || properties.has(IsoFlagType.WallNW) || properties.has(IsoFlagType.WallSE)
                || properties.has(IsoFlagType.DoorWallW) || properties.has(IsoFlagType.DoorWallN)
                || properties.has(IsoFlagType.WindowW) || properties.has(IsoFlagType.WindowN);
    }

    /**
     * Whether what the game calls a wall is one it can be seen through: a fence of wire, of
     * rails or of pickets, a partition of glass. It is a sheet and no thickness of masonry.
     */
    private static boolean isSheet(PropertyContainer properties) {
        return (properties.has(IsoFlagType.WallWTrans) || properties.has(IsoFlagType.WallNTrans))
                && !properties.has(IsoFlagType.WallW) && !properties.has(IsoFlagType.WallN) && !properties.has(IsoFlagType.WallNW)
                && !properties.has(IsoFlagType.DoorWallW) && !properties.has(IsoFlagType.DoorWallN)
                && !properties.has(IsoFlagType.WindowW) && !properties.has(IsoFlagType.WindowN);
    }

    private static boolean standsOnWestEdge(PropertyContainer properties, IsoObjectType type) {
        return properties.has(IsoFlagType.collideW) || properties.has(IsoFlagType.WallW)
                || properties.has(IsoFlagType.WallWTrans) || properties.has(IsoFlagType.DoorWallW)
                || properties.has(IsoFlagType.WindowW) || properties.has(IsoFlagType.windowW)
                || properties.has(IsoFlagType.doorW) || properties.has(IsoFlagType.cutW)
                || properties.has(IsoFlagType.HoppableW) || properties.has(IsoFlagType.TallHoppableW)
                || properties.has(IsoFlagType.attachedW)
                || type == IsoObjectType.doorFrW || type == IsoObjectType.doorW
                || type == IsoObjectType.windowFW || type == IsoObjectType.curtainW;
    }

    private static boolean standsOnNorthEdge(PropertyContainer properties, IsoObjectType type) {
        return properties.has(IsoFlagType.collideN) || properties.has(IsoFlagType.WallN)
                || properties.has(IsoFlagType.WallNTrans) || properties.has(IsoFlagType.DoorWallN)
                || properties.has(IsoFlagType.WindowN) || properties.has(IsoFlagType.windowN)
                || properties.has(IsoFlagType.doorN) || properties.has(IsoFlagType.cutN)
                || properties.has(IsoFlagType.HoppableN) || properties.has(IsoFlagType.TallHoppableN)
                || properties.has(IsoFlagType.attachedN)
                || type == IsoObjectType.doorFrN || type == IsoObjectType.doorN
                || type == IsoObjectType.windowFN || type == IsoObjectType.curtainN;
    }

    @Override
    public void render() {
        MouseGrab.sync();

        GL11.glGetIntegerv(GL_VIEWPORT, viewport);
        if (viewport[2] <= 0 || viewport[3] <= 0) {
            // Nothing to draw into, and no aspect ratio to draw it with.
            return;
        }
        float aspect = (float) viewport[2] / viewport[3];
        float near = ViewCamera.nearPlane(NEAR_CORNER_DISTANCE, tanHalfFieldOfView, aspect);
        drawnAspect = aspect;
        if (tanHalfFieldOfView != loggedTanHalfFieldOfView || aspect != loggedAspect) {
            loggedTanHalfFieldOfView = tanHalfFieldOfView;
            loggedAspect = aspect;
            // Nothing in this view narrows the field of view (no sights, scope or zoom), so the
            // head pose is drawn at the size the tracker sent it and there is no zoom factor.
            Log.info(String.format(Locale.ROOT, "first person field of view %.2f degrees vertical, %.2f horizontal"
                            + " at aspect %.4f (%dx%d), near plane %.4f",
                    Math.toDegrees(2 * Math.atan(tanHalfFieldOfView)),
                    Math.toDegrees(2 * Math.atan(tanHalfFieldOfView * aspect)), aspect, viewport[2], viewport[3], near));
        }
        Characters.aim.set(aim);
        Pipeline.overhead = overhead;
        Pipeline.cutAbove = overhead ? cutFrom : Float.MAX_VALUE;
        Pipeline.cutBox[0] = cutMinX;
        Pipeline.cutBox[1] = cutMinY;
        Pipeline.cutBox[2] = cutMaxX;
        Pipeline.cutBox[3] = cutMaxY;
        Pipeline.focusX = camera.eyeX;
        Pipeline.focusY = camera.eyeY;
        Pipeline.focusUp = camera.eyeUp;
        if (overhead) {
            Pipeline.rangeFrom = overheadAway - OVERHEAD_RANGE;
            // Nothing fades into the sky: the edge of the loaded map is beyond the picture's.
            Pipeline.draw(shown, overheadCamera, sky, overheadTan, overheadAway - OVERHEAD_DEPTH, overheadAway + OVERHEAD_DEPTH,
                    Float.MAX_VALUE / 4f, Float.MAX_VALUE / 2f, exposure, viewport);
            lastDrawMillis = Pipeline.lastMillis();
            return;
        }
        Pipeline.rangeFrom = 0f;
        Pipeline.draw(shown, camera, sky, tanHalfFieldOfView, near, FAR, fogEnd * FOG_START, fogEnd, exposure, viewport);
        lastDrawMillis = Pipeline.lastMillis();
    }
}

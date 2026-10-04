package com.cameraunlock.zomboid;

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
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.fboRenderChunk.FBORenderChunk;
import zombie.iso.sprite.IsoSprite;
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
    /** Two corner heights of a roof tile nearer than this, in levels, are one height: the tile is flat. */
    private static final float FLAT_ROOF = 0.03f;
    /** How far inside a roof tile's corner its sprite is looked at, in frame pixels at tile scale 1. */
    private static final float ROOF_CORNER_INSET = 4f;

    /** The mesh of things drawn without a texture. No texture has this name. */
    private static final int PLAIN = 0;
    /** A character the game has faded out of the view is not drawn. */
    private static final float CHARACTER_SHOWN_ALPHA = 0.5f;

    private static final int GL_VIEWPORT = 0x0BA2;

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
        /** What is drawn as it stands: a pixel of a sprite is there or it is not. */
        final SpriteGeometry.MeshSet solid = new SpriteGeometry.MeshSet();
        /**
         * What is laid over a floor: the grass that runs out into a path, a stain, a rug's
         * fringe. The game paints these with soft edges that fade into the floor under them,
         * so they are drawn after everything solid and mixed into it by how see-through each
         * pixel is.
         */
        final SpriteGeometry.MeshSet laid = new SpriteGeometry.MeshSet();
        private int wx, wy;
        private int builtFrame;
        /** The frames in flight that show this build, and one more while it is the chunk's latest. */
        private int users;
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
    private static int frames;

    /** The chunk builds this frame draws. */
    private final ArrayList<ChunkMeshes> shown = new ArrayList<>();
    /** Where the chunk being built puts its triangles. */
    private SpriteGeometry.MeshSet solid, laid;
    private final SpriteGeometry geometry = new SpriteGeometry();
    private final SpriteGeometry.Frame frame = new SpriteGeometry.Frame();

    private final ViewCamera.Pose camera = new ViewCamera.Pose();
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
    private static final float NIGHT_HELD_BACK = 0.62f;
    private IsoPlayer viewer;
    private IsoCell cell;
    private int playerIndex;
    private int lowestLevel;
    private int chunksBuilt;
    private int boxes;
    private int spreadFaces;
    private int lifted;
    private int chunksDrawn, squaresLoaded, squaresSeen, sprites, triangles;
    private int roofsNorthSouth, roofsWestEast, roofsFlat, roofsUnclear;
    /** The gap between two sprites on one plane of the square being added. */
    private float standOff;
    private int floorLayer, westLayer, northLayer, eastLayer, southLayer;

    /**
     * The light of the squares the character has seen that are out of doors, summed over this
     * frame, and its mean over the last frame that had any. A square the character has not
     * seen has no light of its own yet: the game holds it black until it is seen.
     */
    private float seenRed, seenGreen, seenBlue;
    private int seenOutside;
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
    private static final float SWITCH_WIDEST = 12f;
    private static final java.util.HashMap<Long, Integer> strung = new java.util.HashMap<>();
    private static final int WIRE_SPAN = 12;
    private static final float WIRE_HEIGHT = 0.5f, WIRE_SAG = 0.03f, WIRE_THICK = 0.035f;
    private float steepest;
    private static final float STEEPEST_ROOF = 0.55f;
    /** A roof sprite narrower than this, in unscaled pixels, is a fitting on the roof and not a tile of it. */
    private static final float WHOLE_TILE_WIDE = 50f;
    private String steepestSaid = "", roofName = "";
    /** How far a roof runs out past its wall, and how deep the board along its end is, in tiles. */
    private static final float EAVE = 0.22f, FASCIA = 0.12f, RAKE = 0.14f, GUTTER = 0.07f, PIPE = 0.035f;
    private final float[] eave = new float[12];
    /** How far under a roof tile its plain sheet lies, in levels. */
    private static final float UNDER_ROOF = 0.03f;
    /** Half the thickness of a wall, in tiles. */
    private static final float WALL_HALF = 0.025f;
    private static final float HUNG_CLEAR = 0.012f;
    /** The game gives the height of a surface in pixels of its unscaled sprites, of which a level is this many. */
    private static final float SURFACE_PIXELS_PER_LEVEL = 96f;
    private static final int TABLE_HIGHEST = 48;
    private static final int TALL_SURFACE = 60;
    private static final float CUPBOARD_DEEP = 0.55f;
    private static final int LONG_TUFTS = 13;
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

    private WorldScene() {}

    static WorldScene next() {
        nextScene = (nextScene + 1) % scenes.length;
        return scenes[nextScene];
    }

    String describe() {
        return String.format(Locale.ROOT, "chunks %d built %d squares %d seen %d sprites %d triangles %d build %.2f ms draw %.2f ms"
                        + " exposure %.2f tan half fov %.4f aspect %.4f fog end %.1f roofs n-s %d w-e %d flat %d unclear %d boxes %d spread %d lifted %d models %d | %s",
                chunksDrawn, chunksBuilt, squaresLoaded, squaresSeen, sprites, triangles, buildMillis, lastDrawMillis, exposure,
                tanHalfFieldOfView, drawnAspect, fogEnd, roofsNorthSouth, roofsWestEast, roofsFlat, roofsUnclear, boxes, spreadFaces, lifted, Characters.drawnLast(), sky.describe()) + " | noted " + Characters.described + " | steepest roof " + steepestSaid;
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
     * @param tanHalfFieldOfView the tangent of half the vertical field of view the frame is drawn with
     */
    void build(IsoPlayer player, ViewCamera.Pose view, float tanHalfFieldOfView) {
        long start = System.nanoTime();
        camera.set(view);
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
        remakes = 0;
        boxes = 0;
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

        viewer = player;
        float daylight = GameTime.getInstance().getAmbient();
        sky.capture();
        if (unseenRed < 0f) {
            unseenRed = unseenGreen = unseenBlue = daylight;
        }
        exposure = adaptEye(player);
        playerIndex = player.getIndex();
        cell = IsoWorld.instance.currentCell;
        IsoChunkMap map = cell.getChunkMap(playerIndex);
        int level = (int) Math.floor(player.getZ());
        // From an upper floor the ground is in the picture; from the ground, only the level under it.
        int lowest = level > 0 ? 0 : level - 1;
        lowestLevel = lowest;
        frame.scale = Core.tileScale;
        if (unseenRed < 0f) {
            unseenRed = unseenGreen = unseenBlue = daylight;
        }
        unseenLight.r = unseenRed;
        unseenLight.g = unseenGreen;
        unseenLight.b = unseenBlue;
        seenRed = seenGreen = seenBlue = 0f;
        seenOutside = 0;

        // The loaded map is a square of chunks around the player, and it ends in nothing. The
        // picture fades into the sky before the nearest of its four edges, so the edge is never seen.
        int tiles = IsoChunkMap.chunkGridWidth * CHUNK_TILES;
        float toEdge = Math.min(Math.min(player.getX() - map.getWorldXMinTiles(), map.getWorldXMinTiles() + tiles - player.getX()),
                Math.min(player.getY() - map.getWorldYMinTiles(), map.getWorldYMinTiles() + tiles - player.getY()));
        fogEnd = Math.min(toEdge, RADIUS);
        float cosCone = cosDrawnCone(view, flat);
        // Looking straight up or down the view faces no way along the ground, and nothing is left out.
        float forwardX = cosCone > -1f ? view.forwardX / flat : 0f, forwardY = cosCone > -1f ? view.forwardY / flat : 0f;

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
                boolean due = !missing && frames - current.builtFrame >= buildEvery(distance, slot)
                        && (distance < CHUNK_TILES * 1.5f || remakes++ < REMAKES_A_FRAME);
                if (missing || due) {
                    ChunkMeshes fresh = spare.isEmpty() ? new ChunkMeshes() : spare.pop();
                    fresh.solid.clear();
                    fresh.laid.clear();
                    fresh.wx = chunk.wx;
                    fresh.wy = chunk.wy;
                    fresh.builtFrame = frames;
                    fresh.users = 1;
                    solid = fresh.solid;
                    laid = fresh.laid;
                    chunksBuilt++;
                    for (int z = Math.max(lowest, chunk.getMinLevel()); z <= chunk.getMaxLevel(); z++) {
                        readLights(chunk, chunkX, chunkY, z);
                        float[] ground = Terrain.corners(cell, chunk.wx, chunk.wy);
                        float[] water = Terrain.water(chunk.wx, chunk.wy);
                        for (int localX = 0; localX < CHUNK_TILES; localX++) {
                            for (int localY = 0; localY < CHUNK_TILES; localY++) {
                                IsoGridSquare square = chunk.getGridSquare(localX, localY, z);
                                if (square == null) {
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
        if (seenOutside > 0) {
            unseenRed = seenRed / seenOutside;
            unseenGreen = seenGreen / seenOutside;
            unseenBlue = seenBlue / seenOutside;
        }
        for (int i = 0; i < shown.size(); i++) {
            triangles += shown.get(i).solid.triangles() + shown.get(i).laid.triangles();
        }
        buildMillis = (System.nanoTime() - start) / 1e6f;
    }

    private static void release(ChunkMeshes meshes) {
        if (--meshes.users == 0) {
            spare.push(meshes);
        }
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
        if (aspect == 0f || flat <= 1e-3f) {
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
        float target = under == null || under.isOutside() ? 1f : 0f;
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
                    IsoGridSquare near = cell.getGridSquare(under.getX() + dx, under.getY() + dy, under.getZ());
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
                        seenRed += light.r;
                        seenGreen += light.g;
                        seenBlue += light.b;
                        seenOutside++;
                    }
                }
                lightRed[at] = light.r;
                lightGreen[at] = light.g;
                lightBlue[at] = light.b;
                lightOpen[at] = (byte) (outside ? 1 : 0);
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

    private void addOver(IsoObject object, IsoSprite under, IsoSprite over, boolean isFloor, int x, int y, int z) {
        if (over == null) {
            return;
        }
        Texture texture = over.getTextureForCurrentFrame(object.getDir());
        if (texture == null || texture.getID() <= 0 || texture.getWidth() <= 0 || texture.getHeight() <= 0) {
            return;
        }
        frameOf(object, texture);
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
        addSprite(solid.get(texture.getID()), object, under, texture, isFloor, x, y, z);
    }

    private void frameOf(IsoObject object, Texture texture) {
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
        if (groundKind[own] != Terrain.NATURAL || lightOpen[own] != 1 || groundTexture[own] == null || (groundRank[own] - 1) % 64 < 33) {
            return;
        }
        // A tuft is wider than nothing, and one near the square's edge hangs over it. Over more
        // grass that is as it should be. Over a path it is untidy, and through the wall of a
        // house it is wrong, so unless every square round this one is open grass too the tufts
        // keep to the middle.
        boolean amongGrass = true;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                int at = own + di * LIGHT_SPAN + dj;
                amongGrass &= groundKind[at] == Terrain.NATURAL && lightOpen[at] == 1 && (groundRank[at] - 1) % 64 >= 33;
            }
        }
        float edge = amongGrass ? 0f : TUFT_HALF_WIDTH + 0.06f;
        frameOf(groundObject[own], groundTexture[own]);
        SpriteGeometry.Mesh mesh = laid.get(groundTexture[own].getID());
        for (int tuft = 0; tuft < TUFTS; tuft++) {
            int hash = x * 73856093 ^ y * 19349663 ^ tuft * 83492791;
            hash = (hash ^ hash >>> 13) * 0x5bd1e995;
            hash ^= hash >>> 15;
            float a = edge + (1f - 2f * edge) * (hash & 0xff) / 255f, b = edge + (1f - 2f * edge) * (hash >>> 8 & 0xff) / 255f;
            double turn = (hash >>> 16 & 0xff) / 255.0 * Math.PI;
            float height = 0.16f + 0.26f * (hash >>> 24 & 0xff) / 255f;
            geometry.addTuft(mesh, frame, x, y, a, b, (float) Math.cos(turn), (float) Math.sin(turn), TUFT_HALF_WIDTH, height, SpriteGeometry.GRASS);
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
            mesh.open = frame.open + 32f * CONCRETE;
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
            geometry.addTuft(mesh, frame, x, y, a, b, (float) Math.cos(turn), (float) Math.sin(turn), 0.34f, tall, SpriteGeometry.LONG_GRASS);
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
            return PLANKS;
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

    /** The middle of what is painted in column u of the sprite, as a frame row. NaN if nothing is. */
    private float paintedAt(Texture texture, int u) {
        int first = -1, last = -1;
        for (int v = (int) frame.top; v < frame.top + frame.height; v++) {
            if (texture.isMaskSet(u, v)) {
                if (first < 0) {
                    first = v;
                }
                last = v;
            }
        }
        return first < 0 ? Float.NaN : (first + last) / 2f;
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
        }
        floorLayer = westLayer = northLayer = eastLayer = southLayer = 0;
        waterLocalX = localX;
        waterLocalY = localY;

        IsoObject floor = square.getFloor();
        hangsInAir = floor == null;
        wallNorth = wallWest = false;
        PZArrayList<IsoObject> here = square.getObjects();
        for (int i = 0; i < here.size(); i++) {
            IsoSprite sprite = here.get(i).getSprite();
            if (sprite != null) {
                PropertyContainer flags = sprite.getProperties();
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
            frame.material = materialOf(sprite.getName(), object == floor);
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
        frame.material = 0f;
        if (wiredHere) {
            stringWires(x, y, z);
        }
        if (z == 0) {
            addEdges(x, y, localX, localY);
            spreadGround(x, y, localX, localY);
            if (toX * toX + toY * toY < GRASS_REACH * GRASS_REACH) {
                growGrass(x, y, localX, localY);
            }
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
                            land |= groundKind[(i + ci) * LIGHT_SPAN + j + cj] != Terrain.WATER;
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

        if (addStair(mesh, type, x, y, z)) {
            return;
        }

        boolean west;
        boolean north;
        if (object instanceof IsoDoor door) {
            north = door.north != door.IsOpen();
            west = !north;
        } else if (object instanceof IsoThumpable thumpable && thumpable.isDoor()) {
            north = thumpable.north != thumpable.IsOpen();
            west = !north;
        } else {
            boolean both = properties.has(IsoFlagType.WallNW) || properties.has(IsoFlagType.WallSE);
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
        if (west) {
            addEdge(mesh, texture, isWall(properties, type), x, y, z, westLayer++ * standOff, 0f, 0f, 1f);
        }
        if (north) {
            addEdge(mesh, texture, isWall(properties, type), x, y, z, 0f, northLayer++ * standOff, 1f, 0f);
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
        if (type == IsoObjectType.WestRoofB || type == IsoObjectType.WestRoofM || type == IsoObjectType.WestRoofT) {
            roofName = sprite.getName();
            addRoof(mesh, texture, x, y, z);
            return;
        }
        if (z > 0 && sprite.getName() != null && sprite.getName().startsWith("roofs_")) {
            // Left out. Laid level they stand proud of a gable as slabs; the sheet under each
            // roof tile is what closes the roof.
            return;
        }
        // What the game itself moves with the wind, and what it lets the player clear as
        // growth, is growth. A tree gets a second card across the first: from the eye it is
        // edge on and unseen, but the sun sees it, so the tree's shadow has a width whichever
        // way the eye looks at the tree.
        boolean grows = properties.has(IsoFlagType.vegitation) || properties.has(IsoFlagType.canBeRemoved) || properties.has("MoveWithWind");
        // What the game counts as filling its square, or as something to sit or lie on, is
        // furniture, and furniture is a solid thing with sides and a top.
        if (!grows && (properties.has(IsoFlagType.solid) || properties.has(IsoFlagType.solidtrans) || properties.has(IsoFlagType.bed))
                && !(sprite.getName() != null && sprite.getName().startsWith("furniture_bedding"))
                && SpriteGeometry.boxOf(frame, box) && fillsBox(texture)) {
            geometry.addBox(mesh, frame, x, y, z, box);
            boxes++;
            return;
        }
        // A wardrobe or a dresser two squares wide is the same case as the bed below: each
        // square's sprite is half of it, and the outline of a half is not a box. The game
        // says how high such a thing stands and that it fills its square.
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
        // A bed is two squares long and each square's sprite is half of it, so neither half has
        // the outline of a box of its own. Each is the whole square wide and as high as a
        // mattress on its frame.
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
        if (!grows && SpriteGeometry.boxOf(frame, box) && box[2] <= 0.5f && box[3] <= 1f && fillsMiddle(texture)) {
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
            // A bush is leaves all over, as a tree's crown is.
            String grown = sprite.getName();
            // So is every other plant the game paints with a dark line round each leaf: the
            // line is what makes it a drawing. A bare tree's trunk is the one thing left as it is.
            if (grown != null && type != IsoObjectType.tree) {
                frame.material = LEAVES;
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
            geometry.nudgeX = geometry.nudgeY = 0f;
            return;
        }
        geometry.addUpright(mesh, frame, x, y, z, 0.5f, 0.5f, 0f, cardRightX, cardRightY, false);
    }

    /**
     * A floor over a room is that room's ceiling. Seen from below it is lit by the room, not
     * by whatever falls on the floor above, so its underside is a face of its own just under
     * the floor, carrying the light of the square beneath.
     */
    private void addCeiling(SpriteGeometry.Mesh mesh, int x, int y, int z) {
        IsoGridSquare below = cell.getGridSquare(x, y, z - 1);
        if (below == null || below.isOutside()) {
            return;
        }
        float red = frame.red, green = frame.green, blue = frame.blue, open = frame.open;
        System.arraycopy(frame.cornerRed, 0, heldRed, 0, 4);
        System.arraycopy(frame.cornerGreen, 0, heldGreen, 0, 4);
        System.arraycopy(frame.cornerBlue, 0, heldBlue, 0, 4);
        ColorInfo light = below.isSeen(playerIndex) ? below.lighting[playerIndex].lightInfo() : unseenLight;
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
     * the sprite is walked in from each end of the face, below any window, to the first pixel that is
     * painted, and the face takes its texels from there. Only a wall: a window or a curtain
     * that stops short of the square's edge really does stop there.
     */
    private void addEdge(SpriteGeometry.Mesh mesh, Texture texture, boolean wall, int x, int y, int z,
            float a, float b, float da, float db) {
        // A wall is as thick as a wall: two skins, one each side of the square's edge, both
        // showing what the sprite shows. Through a window or a doorway the far skin is seen
        // behind the near one, which is the depth of the opening. What hangs on the wall
        // hangs on the skin inside the square.
        float inX = db * WALL_HALF, inY = da * WALL_HALF;
        if (!wall) {
            // A finger's width clear of the skin: lying in the skin's own plane, the two are
            // drawn through each other.
            // More of it the further off, where the picture's depth is coarser.
            geometry.shownA = inX + db * (HUNG_CLEAR + 3f * standOff);
            geometry.shownB = inY + da * (HUNG_CLEAR + 3f * standOff);
            geometry.addFace(mesh, frame, x, y, z, a, b, 0f, da, db, 0f, 0f, 0f, 1f);
            geometry.shownA = geometry.shownB = 0f;
            return;
        }
        float fromStart = unpainted(texture, a, b, da, db);
        float fromEnd = unpainted(texture, a + da, b + db, -da, -db);
        boolean startIsLow = SpriteGeometry.frameU(frame, a, b) < SpriteGeometry.frameU(frame, a + da, b + db);
        geometry.sealLow = startIsLow ? fromStart : fromEnd;
        geometry.sealHigh = startIsLow ? fromEnd : fromStart;
        geometry.shownA = inX;
        geometry.shownB = inY;
        geometry.addFace(mesh, frame, x, y, z, a, b, 0f, da, db, 0f, 0f, 0f, 1f);
        geometry.shownA = -inX;
        geometry.shownB = -inY;
        geometry.addFace(mesh, frame, x, y, z, a, b, 0f, da, db, 0f, 0f, 0f, 1f);
        geometry.shownA = geometry.shownB = 0f;
        geometry.sealLow = 0f;
        geometry.sealHigh = 0f;
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
     * A roof tile is a plane over its square, level along one axis and sloping along the other.
     * Nothing the game holds says which way a tile slopes, but its sprite does. The sprite's
     * top is where the square's north-west corner is drawn and its bottom where the south-east
     * corner is, which gives the roof's height at both. The other two corners each share a
     * height with one of those, and which one is read off the sprite: a slope along y has its
     * south-west corner drawn at the south-east corner's height, a slope along x at the
     * north-west corner's, and only one of the two places has the sprite's pixels in it.
     */
    private void addRoof(SpriteGeometry.Mesh mesh, Texture texture, int x, int y, int z) {
        float northWest = SpriteGeometry.heightDrawnAt(frame, 0f, 0f, frame.top);
        float southEast = SpriteGeometry.heightDrawnAt(frame, 1f, 1f, frame.top + frame.height);
        // Some roof sprites have more painted on them than the slope, the end of the roof or
        // a board up its edge, and their outline then says the tile climbs a whole floor. No
        // roof in the game does: a slope takes three tiles to a floor. Such a tile is taken
        // to rise a third of a floor from its lowest drawn point, which is the roof's own.
        if (Math.abs(northWest - southEast) > STEEPEST_ROOF) {
            if (northWest > southEast) {
                northWest = southEast + 1f / 3f;
            } else {
                southEast = northWest + 1f / 3f;
            }
        }
        if (Math.abs(northWest - southEast) > steepest) {
            steepest = Math.abs(northWest - southEast);
            steepestSaid = String.format(Locale.ROOT, "%s at (%d, %d, %d) nw %.2f se %.2f frame %.0fx%.0f at (%.0f, %.0f) origin (%.0f, %.0f) scale %.0f",
                    roofName, x, y, z, northWest, southEast, frame.width, frame.height, frame.left, frame.top, frame.originU, frame.originV, frame.scale);
        }
        if (Math.abs(northWest - southEast) < FLAT_ROOF) {
            roofsFlat++;
            // A flat roof that lies on its level has the floor there under it already.
            if (geometry.addFace(mesh, frame, x, y, z, 0f, 0f, (northWest + southEast) / 2f, 1f, 0f, 0f, 0f, 1f, 0f) > 0
                    && (northWest + southEast) / 2f > 2f * UNDER_ROOF) {
                underRoof(x, y, z, (northWest + southEast) / 2f, 0f, 0f);
            }
            return;
        }
        int alongY = 0, alongX = 0;
        // South-west corner, then north-east: the two the slope's direction decides.
        for (int i = 0; i < 2; i++) {
            float a = i, b = 1 - i;
            float inset = (i == 0 ? ROOF_CORNER_INSET : -ROOF_CORNER_INSET) * frame.scale;
            int u = Math.round(SpriteGeometry.frameU(frame, a, b) + inset);
            // Along y the height follows b, so this corner is at the height of the corner with its b.
            boolean atY = texture.isMaskSet(u, Math.round(SpriteGeometry.frameV(frame, a, b, i == 0 ? southEast : northWest)));
            boolean atX = texture.isMaskSet(u, Math.round(SpriteGeometry.frameV(frame, a, b, i == 0 ? northWest : southEast)));
            if (atY && !atX) {
                alongY++;
            } else if (atX && !atY) {
                alongX++;
            }
        }
        if (alongX == alongY && frame.width > WHOLE_TILE_WIDE * frame.scale) {
            // The corners did not say. The sprite's two ends do: its left end is the tile's
            // south-west corner and its right end the north-east one, and whichever end is
            // painted higher up is the higher corner.
            float left = paintedAt(texture, Math.round(frame.left + ROOF_CORNER_INSET * frame.scale));
            float right = paintedAt(texture, Math.round(frame.left + frame.width - 1 - ROOF_CORNER_INSET * frame.scale));
            if (!Float.isNaN(left) && !Float.isNaN(right) && Math.abs(left - right) > 2f * frame.scale) {
                boolean southWestHigher = left < right;
                if (southWestHigher == southEast > northWest) {
                    alongY++;
                } else {
                    alongX++;
                }
            }
        }
        if (alongX > alongY) {
            roofsWestEast++;
            if (geometry.addFace(mesh, frame, x, y, z, 0f, 0f, northWest, 1f, 0f, southEast - northWest, 0f, 1f, 0f) > 0) {
                underRoof(x, y, z, northWest, southEast - northWest, 0f);
            }
            return;
        }
        boolean sure = alongY > alongX;
        if (sure) {
            roofsNorthSouth++;
        } else {
            roofsUnclear++;
        }
        // Only where the tile's slope is sure: a sheet under a tile whose slope was guessed
        // stands up out of the roof as a slab.
        if (geometry.addFace(mesh, frame, x, y, z, 0f, 0f, northWest, 1f, 0f, 0f, 0f, 1f, southEast - northWest) > 0) {
            if (sure) {
                underRoof(x, y, z, northWest, 0f, southEast - northWest);
            } else if (frame.width > WHOLE_TILE_WIDE * frame.scale) {
                // Which way this tile slopes is a guess, and a sheet laid to a wrong guess
                // stands out of the roof. A level one at the tile's lowest height cannot: it
                // is under the tile whichever way it slopes, and still closes the roof.
                underRoof(x, y, z, Math.min(northWest, southEast), 0f, 0f);
            }
        }
    }

    /**
     * The game drew each roof tile from one side, and a slope that faces away from that side
     * is a sliver of sprite, or none: seen from the other side the roof has holes with the
     * rooms showing through. Under every tile of roof goes a plain sheet of the same slope, in
     * the grey of shingle, which is what shows wherever the sprite has nothing.
     */
    private void underRoof(int x, int y, int z, float atOrigin, float alongX, float alongY) {
        SpriteGeometry.Mesh plain = solid.get(PLAIN);
        plain.open = frame.open + 32f * SHINGLES;
        float[] lift = geometry.lift;
        SpriteGeometry.addPlainOver(plain, x, y, under(z, atOrigin) + lift[0], under(z, atOrigin + alongX) + lift[1],
                under(z, atOrigin + alongX + alongY) + lift[2], under(z, atOrigin + alongY) + lift[3],
                frame.red, frame.green, frame.blue);
        addEave(plain, x, y, z, atOrigin, alongX, alongY);
        addRake(plain, x, y, z, atOrigin, alongX, alongY);
    }

    /**
     * At a gable the roof runs a little past the end wall too: where a sloping tile has no roof
     * beside it across its slope, a strip of roof carries its slope out over the wall.
     */
    private void addRake(SpriteGeometry.Mesh plain, int x, int y, int z, float atOrigin, float alongX, float alongY) {
        if (Math.abs(alongX + alongY) < FLAT_ROOF) {
            return;
        }
        plain.open = frame.open + 32f * SHINGLES;
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
            SpriteGeometry.addPlainQuad(plain, c, frame.red, frame.green, frame.blue);
        }
    }

    /**
     * A roof does not stop at the wall. Where a sloping tile is the lowest of its slope, with
     * no roof beyond its low edge, the slope runs on past the wall and a little down: the
     * eave, with a board closing its end.
     */
    private void addEave(SpriteGeometry.Mesh plain, int x, int y, int z, float atOrigin, float alongX, float alongY) {
        float along = alongX != 0f ? alongX : alongY;
        if (Math.abs(along) < FLAT_ROOF) {
            return;
        }
        boolean lowAtFar = along < 0f;
        int beyondX = x + (alongX != 0f ? (lowAtFar ? 1 : -1) : 0), beyondY = y + (alongY != 0f ? (lowAtFar ? 1 : -1) : 0);
        if (hasRoof(beyondX, beyondY, z)) {
            return;
        }
        float low = lowAtFar ? atOrigin + along : atOrigin;
        float out = lowAtFar ? EAVE : -EAVE;
        float edge = lowAtFar ? 1f : 0f;
        float drop = -Math.abs(along) * EAVE;
        float lift = (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f;
        float high = (z + low) * ViewCamera.LEVEL_HEIGHT + lift, far = (z + low + drop) * ViewCamera.LEVEL_HEIGHT + lift;
        float[] c = eave;
        if (alongX != 0f) {
            set(c, 0, x + edge, y, high);
            set(c, 1, x + edge + out, y, far);
            set(c, 2, x + edge + out, y + 1f, far);
            set(c, 3, x + edge, y + 1f, high);
        } else {
            set(c, 0, x, y + edge, high);
            set(c, 1, x, y + edge + out, far);
            set(c, 2, x + 1f, y + edge + out, far);
            set(c, 3, x + 1f, y + edge, high);
        }
        SpriteGeometry.addPlainQuad(plain, c, frame.red, frame.green, frame.blue);
        // The soffit: the boarded underside of the eave, level from the fascia's foot back to
        // the wall, which is what is seen of a roof from the ground beside the house.
        plain.open = frame.open + 32f * CONCRETE;
        float under = far - FASCIA;
        float wx1 = c[0], wy1 = c[1], wx2 = c[9], wy2 = c[10], ex1 = c[3], ey1 = c[4], ex2 = c[6], ey2 = c[7];
        set(c, 0, wx1, wy1, under);
        set(c, 1, ex1, ey1, under);
        set(c, 2, ex2, ey2, under);
        set(c, 3, wx2, wy2, under);
        SpriteGeometry.addPlainQuad(plain, c, frame.red, frame.green, frame.blue);
        set(c, 0, wx1, wy1, high);
        set(c, 1, ex1, ey1, far);
        set(c, 2, ex2, ey2, far);
        set(c, 3, wx2, wy2, high);
        // The board along the eave's end.
        plain.open = frame.open;
        set(c, 0, c[3], c[4], far);
        set(c, 3, c[6], c[7], far - FASCIA);
        float x1 = c[3], y1 = c[4], x2 = c[6], y2 = c[7];
        set(c, 1, x2, y2, far);
        set(c, 2, x2, y2, far - FASCIA);
        set(c, 3, x1, y1, far - FASCIA);
        SpriteGeometry.addPlainQuad(plain, c, 0.55f * frame.red, 0.55f * frame.green, 0.53f * frame.blue);
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
        if (!hasRoof(alongX != 0f ? x : x - 1, alongX != 0f ? y - 1 : y, z)) {
            plain.open = frame.open + 32f * CONCRETE;
            float px = x1 + ox * 0.5f - (alongX != 0f ? Math.signum(out) * EAVE : 0f), py = y1 + oy * 0.5f - (alongX != 0f ? 0f : Math.signum(out) * EAVE);
            float foot = lift;
            for (int turn = 0; turn < 2; turn++) {
                float wx = turn == 0 ? PIPE : 0f, wy = turn == 0 ? 0f : PIPE;
                set(c, 0, px - wx, py - wy, foot);
                set(c, 1, px + wx, py + wy, foot);
                set(c, 2, px + wx, py + wy, far - GUTTER);
                set(c, 3, px - wx, py - wy, far - GUTTER);
                SpriteGeometry.addPlainQuad(plain, c, frame.red, frame.green, frame.blue);
            }
        }
    }

    private static void set(float[] c, int corner, float x, float y, float h) {
        c[corner * 3] = x;
        c[corner * 3 + 1] = y;
        c[corner * 3 + 2] = h;
    }

    private boolean hasRoof(int x, int y, int z) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        if (square == null) {
            return false;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && sprite.getName() != null && sprite.getName().startsWith("roofs_")) {
                return true;
            }
        }
        return false;
    }

    /**
     * A flight is three squares, each a third of a level, rising towards -x (W) or -y (N).
     * Returns false, adding nothing, for a sprite that is not a stair.
     */
    private boolean addStair(SpriteGeometry.Mesh mesh, IsoObjectType type, int x, int y, int z) {
        int third = type == IsoObjectType.stairsBW || type == IsoObjectType.stairsBN ? 0
                : type == IsoObjectType.stairsMW || type == IsoObjectType.stairsMN ? 1
                : type == IsoObjectType.stairsTW || type == IsoObjectType.stairsTN ? 2 : -1;
        if (third < 0) {
            return false;
        }
        float top = (third + 1) / 3f;
        if (type == IsoObjectType.stairsBW || type == IsoObjectType.stairsMW || type == IsoObjectType.stairsTW) {
            geometry.addFace(mesh, frame, x, y, z, 0f, 0f, top, 1f, 0f, -1f / 3f, 0f, 1f, 0f);
        } else {
            geometry.addFace(mesh, frame, x, y, z, 0f, 0f, top, 0f, 1f, -1f / 3f, 1f, 0f, 0f);
        }
        return true;
    }

    private static boolean isWall(PropertyContainer properties, IsoObjectType type) {
        return type == IsoObjectType.wall || properties.has(IsoFlagType.WallW) || properties.has(IsoFlagType.WallN)
                || properties.has(IsoFlagType.WallNW) || properties.has(IsoFlagType.WallSE)
                || properties.has(IsoFlagType.DoorWallW) || properties.has(IsoFlagType.DoorWallN)
                || properties.has(IsoFlagType.WindowW) || properties.has(IsoFlagType.WindowN);
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

        int[] viewport = new int[4];
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
        Pipeline.draw(shown, camera, sky, tanHalfFieldOfView, near, FAR, fogEnd * FOG_START, fogEnd, exposure, viewport);
        lastDrawMillis = Pipeline.lastMillis();
    }
}

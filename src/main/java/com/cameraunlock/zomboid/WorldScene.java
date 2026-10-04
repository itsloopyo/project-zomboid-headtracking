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
import zombie.iso.sprite.IsoSprite;
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
    private static final float FOG_START = 0.55f;
    /**
     * How far the near plane's corners are from the eye. A lean stops LeanClamp.SKIN off a wall,
     * and the wall is only drawn while that is further than this.
     */
    static final float NEAR_CORNER_DISTANCE = 0.08f;
    private static final float FAR = 200f;
    private static final float EXPOSURE_INDOORS = 1.25f;
    private static final float EYE_ADAPT_SECONDS = 0.8f;
    private static final float EXPOSURE_MOST = 3.5f;
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
    private IsoPlayer viewer;
    private IsoCell cell;
    private int playerIndex;
    private int lowestLevel;
    private int chunksBuilt;
    private int boxes;
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
                        + " exposure %.2f tan half fov %.4f aspect %.4f fog end %.1f roofs n-s %d w-e %d flat %d unclear %d boxes %d models %d | %s",
                chunksDrawn, chunksBuilt, squaresLoaded, squaresSeen, sprites, triangles, buildMillis, lastDrawMillis, exposure,
                tanHalfFieldOfView, drawnAspect, fogEnd, roofsNorthSouth, roofsWestEast, roofsFlat, roofsUnclear, boxes, Characters.drawnLast(), sky.describe());
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
        boxes = 0;
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
                if (current == null || current.wx != chunk.wx || current.wy != chunk.wy
                        || frames - current.builtFrame >= buildEvery(distance, slot)) {
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
                        for (int localX = 0; localX < CHUNK_TILES; localX++) {
                            for (int localY = 0; localY < CHUNK_TILES; localY++) {
                                IsoGridSquare square = chunk.getGridSquare(localX, localY, z);
                                if (square == null) {
                                    continue;
                                }
                                squaresLoaded++;
                                cornerLights(localX, localY);
                                addSquare(square, chunkX + localX, chunkY + localY, z, playerIndex);
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
            return 1;
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
        float here = 1f;
        if (under != null) {
            ColorInfo light = under.lighting[player.getIndex()].lightInfo();
            here = (float) Math.pow((light.r + light.g + light.b) / 3f, 2.2);
        }
        float indoors = Math.min(EXPOSURE_INDOORS / Math.max(here, 1e-3f), EXPOSURE_MOST);
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

    private void addSquare(IsoGridSquare square, int x, int y, int z, int playerIndex) {
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

        IsoObject floor = square.getFloor();
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
            frame.originU = object.offsetX;
            frame.originV = object.offsetY;
            frame.left = texture.getOffsetX();
            frame.top = texture.getOffsetY();
            frame.width = texture.getWidth();
            frame.height = texture.getHeight();
            frame.uStart = texture.getXStart();
            frame.uEnd = texture.getXEnd();
            frame.vStart = texture.getYStart();
            frame.vEnd = texture.getYEnd();
            sprites++;
            addSprite(solid.get(texture.getID()), object, sprite, texture, object == floor, x, y, z);
        }

        ArrayList<IsoMovingObject> bodies = square.getStaticMovingObjects();
        for (int i = 0; i < bodies.size(); i++) {
            IsoMovingObject body = bodies.get(i);
            solid.get(PLAIN).open = frame.open;
            SpriteGeometry.addPlainFlat(solid.get(PLAIN), body.getX(), body.getY(), body.getZ() * ViewCamera.LEVEL_HEIGHT + 0.08f,
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
        float base = character.getZ() * ViewCamera.LEVEL_HEIGHT;
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
            if (floorLayer == 0 && z > lowestLevel) {
                addCeiling(mesh, x, y, z);
            }
            SpriteGeometry.Mesh on = floorLayer == 0 ? mesh : laid.get(texture.getID());
            frame.kind = properties.has(IsoFlagType.water) ? SpriteGeometry.WATER : 0f;
            geometry.addFace(on, frame, x, y, z, 0f, 0f, floorLayer++ * standOff / ViewCamera.LEVEL_HEIGHT, 1f, 0f, 0f, 0f, 1f, 0f);
            frame.kind = 0f;
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
            geometry.addFace(mesh, frame, x, y, z, 1f - ++eastLayer * standOff, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);
            return;
        }
        if (properties.has(IsoFlagType.attachedS) || type == IsoObjectType.curtainS) {
            geometry.addFace(mesh, frame, x, y, z, 0f, 1f - ++southLayer * standOff, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
            return;
        }
        if (type == IsoObjectType.WestRoofB || type == IsoObjectType.WestRoofM || type == IsoObjectType.WestRoofT) {
            addRoof(mesh, texture, x, y, z);
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
                && SpriteGeometry.boxOf(frame, box) && fillsBox(texture)) {
            geometry.addBox(mesh, frame, x, y, z, box);
            boxes++;
            return;
        }
        geometry.addUpright(mesh, frame, x, y, z, 0.5f, 0.5f, 0f, cardRightX, cardRightY, grows);
        if (type == IsoObjectType.tree) {
            geometry.addUpright(mesh, frame, x, y, z, 0.5f, 0.5f, 0f, -cardRightY, cardRightX, grows);
        }
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
        if (!wall) {
            geometry.addFace(mesh, frame, x, y, z, a, b, 0f, da, db, 0f, 0f, 0f, 1f);
            return;
        }
        float fromStart = unpainted(texture, a, b, da, db);
        float fromEnd = unpainted(texture, a + da, b + db, -da, -db);
        boolean startIsLow = SpriteGeometry.frameU(frame, a, b) < SpriteGeometry.frameU(frame, a + da, b + db);
        geometry.sealLow = startIsLow ? fromStart : fromEnd;
        geometry.sealHigh = startIsLow ? fromEnd : fromStart;
        geometry.addFace(mesh, frame, x, y, z, a, b, 0f, da, db, 0f, 0f, 0f, 1f);
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
        if (Math.abs(northWest - southEast) < FLAT_ROOF) {
            roofsFlat++;
            geometry.addFace(mesh, frame, x, y, z, 0f, 0f, (northWest + southEast) / 2f, 1f, 0f, 0f, 0f, 1f, 0f);
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
        if (alongX > alongY) {
            roofsWestEast++;
            geometry.addFace(mesh, frame, x, y, z, 0f, 0f, northWest, 1f, 0f, southEast - northWest, 0f, 1f, 0f);
            return;
        }
        if (alongY > alongX) {
            roofsNorthSouth++;
        } else {
            roofsUnclear++;
        }
        geometry.addFace(mesh, frame, x, y, z, 0f, 0f, northWest, 1f, 0f, 0f, 0f, 1f, southEast - northWest);
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

package com.cameraunlock.zomboid;

import java.lang.ref.WeakReference;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.opengl.GL11;
import zombie.GameTime;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.Core;
import zombie.core.PerformanceSettings;
import zombie.core.properties.PropertyContainer;
import zombie.core.skinnedmodel.DeadBodyAtlas;
import zombie.core.skinnedmodel.ModelManager;
import zombie.core.textures.ColorInfo;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureDraw;
import zombie.iso.BuildingDef;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoChunkMap;
import zombie.iso.IsoDirections;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoLightSource;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.LightingJNI;
import zombie.iso.RoomDef;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.areas.IsoBuilding;
import zombie.iso.areas.IsoRoom;
import zombie.iso.fboRenderChunk.FBORenderChunk;
import zombie.iso.fboRenderChunk.FBORenderCutaways;
import zombie.iso.fboRenderChunk.FBORenderLevels;
import zombie.iso.objects.IsoBarricade;
import zombie.iso.objects.IsoCurtain;
import zombie.iso.objects.IsoDeadBody;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoBarbecue;
import zombie.iso.objects.IsoFireplace;
import zombie.iso.objects.IsoLightSwitch;
import zombie.iso.objects.IsoTelevision;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWindow;
import zombie.iso.objects.IsoWorldInventoryObject;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteInstance;
import zombie.iso.weather.ClimateManager;
import zombie.util.list.PZArrayList;

/**
 * The world as one player's first-person view builds and keeps it, and inside it, as Frame,
 * one frame of that world as the view draws it.
 *
 * A chunk's build holds one player's view of it: which squares that player has seen and the
 * light the game has on each for them. So the builds, and everything kept round the eye they
 * are made from, are this object's. What was read of a sprite or a texture is the same
 * whoever looks, and so is what a build is put together in: those are static, one for every
 * view, and the logic thread builds one view's world at a time.
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
final class WorldScene {
    final View view;

    WorldScene(View view) {
        this.view = view;
    }

    /** How far the aim is traced with no firearm in hand. */
    static final float REACH = 30f;
    static final int CHUNK_TILES = 8;
    /**
     * The hills' heights round an eye worked out again from the map: made once, where one made
     * at each build would be made every frame. In a class of its own, made at its first use: the
     * tests load WorldScene without the game's classes.
     */
    private static final class HillsFromMap {
        static final Terrain.Rework<IsoCell> REWORK = Terrain.HILLS::fromMap;
    }
    /** Half the diagonal of a chunk, rounded up: how far a chunk reaches from its centre. */
    static final float CHUNK_REACH = 6f;
    /** How much wider than the picture's corners the drawn cone is, so nothing pops in at the edges. */
    private static final float CONE_MARGIN = (float) Math.toRadians(8);
    /** The cosine of the angle either side of straight behind that a driver's mirror takes in, with the same margin. */
    private static final float COS_MIRROR_CONE =
            (float) Math.cos(Math.atan(Pipeline.MIRROR_TAN * Pipeline.MIRROR_WIDTH / Pipeline.MIRROR_HEIGHT) + CONE_MARGIN);
    /** The world fades into the sky from this share of the way to the edge of the loaded map. */
    private static final float FOG_START = 0.78f;
    /**
     * How far the near plane's corners are from the eye. A lean stops the collision margin off a wall,
     * and the wall is only drawn while that is further than this.
     */
    static final float NEAR_CORNER_DISTANCE = 0.08f;
    /** Past where the far land ends, for an eye as high over it as the map's tallest roof. */
    private static final float FAR = FarLand.REACH + 80f;
    private static final float EXPOSURE_INDOORS = 1.25f;
    private static final float EYE_ADAPT_SECONDS = 0.8f;
    /** How much further the eye closes at night over ground all under snow: 2.5 times as far at full snow and full night. */
    private static final float SNOW_STOPS_DOWN = 1.5f;
    private static final float EXPOSURE_MOST = 3.5f;
    private static final float EXPOSURE_MOST_AT_NIGHT = 1.25f;
    /**
     * How far a sprite hung on a wall or laid on a floor stands off the one under it, so the two
     * do not fight for depth: a layer. The depth buffer tells two surfaces apart less well the
     * further off they are, by the square of the distance, so the gap grows the same way: at 80
     * tiles it is about a pixel wide. A build only counts the layers (SpriteGeometry.Layers).
     * The picture makes them this thick as it draws, vertex by vertex, from how far off each
     * is, so a build is the same from any distance.
     */
    static final float STAND_OFF = 0.004f;
    static final float STAND_OFF_PER_TILE_SQUARED = 1e-5f;

    /** The mesh of things drawn without a texture. No texture has this name. */
    static final int PLAIN = 0;
    /** A character the game has faded out of the view is not drawn. */
    private static final float CHARACTER_SHOWN_ALPHA = 0.5f;

    private static final int GL_VIEWPORT = 0x0BA2;
    /** The part of the game's picture the frame being drawn is written to. Render thread. */
    private final int[] viewport = new int[4];

    /** The land beyond the loaded map, round this view's eye. */
    final FarLand farLand = new FarLand();
    /** The game's light of each square the builds read, by its lighting engine's pass. Logic thread. */
    private final SquareLights squareLights = new SquareLights();
    /**
     * The frames handed to the render thread in turn. One is filled again only after the
     * other three were handed on, and by then the render thread has drawn it.
     */
    static final int SCENES = 4;
    private final Frame[] scenes = {new Frame(), new Frame(), new Frame(), new Frame()};
    private int nextScene;

    private volatile float lastDrawMillis;
    /** Width over height of the buffer the last frame was drawn into, 0 until a frame has been drawn. */
    private volatile float drawnAspect;
    private float loggedTanHalfSet, loggedAspect;
    private float eyeOpen = -1f;
    /** How far the eye is out from under any roof: see SkyState.openOver. */
    private float openOver = -1f;
    private float shaded;
    /** The exposure the eye has come to by what it sees: see adaptEye. */
    private float eyeSeen = -1f;
    /**
     * What the light meter reads, as a log2, of a picture the eye is adapted to (Pipeline.meter),
     * in a room and in the open air: a room by day is drawn brighter than the open air at noon.
     * Measured offline at noon, the exposure by place against the meter: a bedroom 10.2 at -4.7,
     * a street 0.53 at -1.97. A view brighter than that stops the eye down and a darker one opens
     * it up, as far as the exposures for a room and for the open air between them reach.
     */
    private static final float METER_KEY_INDOORS = -1.35f, METER_KEY_OUTDOORS = -2.88f;
    /**
     * How much more the meter reads of the street than of a room, in stops, from the same two
     * noon measurements: -1.97 against -4.7. A view from a room that reads this much more than
     * the room would is all daylight, a window into the sun, and the eye takes it at the open
     * air's key.
     */
    private static final float ROOM_TO_STREET = 2.73f;
    /**
     * How far, in stops, the brightest tenth of a view may lie over the eye's centre-weighted
     * reading before it counts for more than that reading. In an evenly lit view it lies about
     * this far over and changes nothing; a window into the sun filling a tenth of the view or
     * more lies further over and stops the eye down for it. Set by eye, not measured.
     */
    private static final float UPPER_OVER_MEAN = 1.5f;
    /** How long the eye takes to stop down to a brighter view, and to open up to a darker one, in seconds: an eye is slower to open. */
    private static final float EYE_CLOSES_SECONDS = 0.5f, EYE_OPENS_SECONDS = 2.5f;
    /** How much further the eye opens deep in a wood than on open ground. */
    private static final float WOODS_OPEN = 1.1f;
    private long lastAdapt;

    /**
     * One chunk's triangles as they were last built. Most of the world does not change from
     * one frame to the next, so a chunk is only looked at again when it is due, every frame near
     * the eye, where a door may open or a light come on, and every so many frames further off,
     * and built again only when what it is built from has changed or it has been kept as long
     * as a build is.
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
        /**
         * For each run, the region it is seen from, the contents of a room drawn only while the
         * eye sees into it, RoomSight.OUTSIDE or RoomSight.ANYWHERE: see SpriteGeometry.MeshSet.room.
         * Of what is seen from either of two (RoomSight.pair), the one and the other in runRoomToo,
         * which is runRoom again for the rest.
         */
        long[] runRoom = new long[32], runRoomToo = new long[32];
        /** For each run, the room whose being seen into leaves it out (RoomSight.blind), or RoomSight.OUTSIDE. */
        long[] runUnless = new long[32];
        /** The building and the level each run is of, or SpriteGeometry.NO_BUILDING: see SpriteGeometry.MeshSet.storey. */
        long[] runBuilding = new long[32];
        int[] runLevel = new int[32];
        int runs, solidRuns;
        /** How many floats the runs come to. */
        int floats;
        /**
         * What each thing that may be outlined drew of the runs: its key (Outlines.key), the
         * run, and the first of its floats and how many, counted from the start of the build.
         */
        long[] ownedKey = new long[0];
        int[] ownedRun = new int[0], ownedFrom = new int[0], ownedFloats = new int[0];
        int owned;
        /**
         * The box that holds every vertex of the runs as it stands, before the wind moves any:
         * the least and the greatest x, y and height. Of no meaning for a build with no runs.
         */
        float minX, minY, minUp, maxX, maxY, maxUp;
        /** Whether the chunk it was built of has water on it, as last asked of Terrain.holdsWater. */
        boolean water;
        /**
         * How far past the box, along the map, the picture may draw a vertex of the runs: a card
         * that turns to the camera is held as its middle line, and each vertex says how far along
         * the card it is drawn. The greatest of those.
         */
        float turned;
        /**
         * The most layers any vertex of the runs is stood clear of its plane or laid off it by,
         * which the picture also moves it by as it draws: how far a layer is grows with how far
         * off the vertex is. See PipelineShaders.FACED.
         */
        float layered;
        /**
         * The vertices themselves, on loan from VertexLoans, until the graphics card has them:
         * null from then on, and for a build with no triangles. The logic thread sets it when
         * it has filled the loan, and the render thread gives the loan back and clears it.
         */
        volatile FloatBuffer vertices;
        /** The furniture the mod has models of, each drawn from its model where it stands. */
        final Models.Instances placed = new Models.Instances();
        /** Dev only: the triangles as they were filled, for DepthFights. Null unless it audits. */
        DepthFights.Copy depthCopy;
        private int wx, wy;
        private int builtFrame;
        /** The frame the build was last made in or found still to be what a new one would be. */
        private int checkedFrame;
        /** When it was last built or found unchanged, when it was built, and when its chunk was first built, on System.nanoTime's clock. */
        private long checkedNanos, builtNanos, bornNanos;
        /** When the chunk was first found changed and its build put off for a frame with time for it, or 0: see CHANGED_WAITS_NANOS. */
        private long putOffNanos;
        /** Where the eye was when the build was made, for a build of what grows on a chunk's ground. */
        private float eyeX, eyeY, eyeUp;
        /**
         * Which band of distance from the eye the build was made for: see BAND_ENDS. A tree, a
         * bush, a rock and long grass are made by it, and nothing else of the build goes by
         * where the eye is.
         */
        int band;
        /** The bands the build would have come out the same for, a bit each: see within. */
        private int alike;
        /** For a build of a chunk's crowns and nothing else, what they were made from: see latestCrowned. Null for any other build. */
        Crowns crowns;
        /** Whether any square of the chunk is open natural ground, where a lawn, ferns, flowers or fallen leaves may be. */
        private boolean grows;
        /** Whether the build is of what grows on a chunk's ground and not of the chunk: see latestGrown. */
        boolean grown;
        /** For each square of the chunk's ground, how many layers its floor has: what grows on it is laid over them. */
        private final byte[] groundLayers = new byte[CHUNK_TILES * CHUNK_TILES];
        /** Whether what the build read of the map was summed as it was made, and the sum: see signature. */
        boolean signed;
        private long signature;
        /** The frame and the time from which the chunk's builds are summed again, after one was found changed with the eye still. */
        private int summedFrom;
        private long summedFromNanos;
        /**
         * Whether the build read nothing but what its sum holds: no room, no wall, no roof, no
         * canopy, nothing hung or raised. Such a build is the same for as long as its sum is.
         */
        boolean plain;
        /** What ChunkMarks had counted round the chunk when the build was made or last found unchanged, and what every build went by then: see epoch. */
        private int marks, told;
        /** Of those marks, the ones for anything but light: see ChunkMarks.thingsAround. */
        private int things;
        /**
         * For a build that was summed, the sum of each of its levels from sumFrom to sumTo, what
         * ChunkMarks had counted for the light of each when it was read, and for light marked
         * with no level. A level whose count is what it was is not read again: see signature.
         * sumTo is under sumFrom for a build with no sums. And the light a square not yet seen
         * was given, which is no square's own and changes with the hour, unmarked.
         */
        private long[] levelSums = new long[0];
        private int[] litSeen = new int[0];
        private int sumFrom, sumTo = -1, litLoose;
        private long unseenRead;
        /**
         * When every level was last read. The engine's light for a square can go on changing
         * with nothing marked, as it does for minutes after the hour is set, so a chunk read
         * by its marked levels is still read whole as often as one nothing is marked on.
         */
        private long wholeNanos;

        /** Takes the sums of the levels as the last reading left them: one made at a time given, for a chunk whose build till now is given, or null. */
        private void keepLevels(Frame scene, long now, ChunkMeshes before) {
            wholeNanos = scene.readByLevel ? before.wholeNanos : now;
            int levels = scene.readTo - scene.readFrom + 1;
            if (levelSums.length < levels) {
                levelSums = new long[levels];
                litSeen = new int[levels];
            }
            System.arraycopy(scene.readSums, 0, levelSums, 0, levels);
            System.arraycopy(scene.readLit, 0, litSeen, 0, levels);
            sumFrom = scene.readFrom;
            sumTo = scene.readTo;
            litLoose = scene.readLoose;
            unseenRead = scene.readUnseen;
        }
        private long epoch;
        /** Whether the build holds a tree the season shapes, and the season's stage it was made at: see Frame.seasonHolds. */
        boolean seasonal;
        int stage;
        /** The heights the build was made on, and which chunks it took any from, those round its own and those further off: see landOf. */
        private int land, terrainVersion, heightsRound;
        private long[] heightsFar = NO_CHUNKS;
        /** Whether the build left the span of a wire unknown at an end because the map was not loaded that far: see landOf. */
        private boolean wantsMap;
        /** The land's part of what the build read, which signature leaves out of its sum, and its own chunk's kerbs: see heightsHeld. */
        private long landSum, kerbs;
        /**
         * The heights the build was made on: its own chunk's first, then those of every chunk it
         * took a height from, as Terrain handed them out (an array Terrain has handed out is never
         * written again), each chunk's x and y in heightsSeenOf, and its own chunk's water.
         */
        private float[][] heightsSeen = new float[0][];
        private int[] heightsSeenOf = new int[0];
        private float[] waterSeen;

        /** Tests only: the build's own chunk's heights as it was made on them. */
        float[] heightsSeenForTests() {
            return heightsSeen[0];
        }
        /** Which of the chunk and the eight round it were loaded when it was last read, a bit each, and which chunks the map had loaded then: see loadedNow. */
        private int ring, loaded;
        /**
         * Whether the chunk holds what changes with nothing said of it: a sprite of several
         * frames, a body the game is about to make its picture of. Such a chunk is read
         * through as it falls due, as every chunk is without the game's marks.
         */
        private boolean restless;
        /** Whether it was made, or last read and kept, for a view drawn as the game's own: a room not yet seen is built otherwise for the eye. */
        private boolean gamesView;
        /** The light of its seen squares out of doors, as its last reading left them: see OpenLight. Null until it has been read. */
        private OpenLight seen;
        /** The light of its room squares in the character's sight, those with no lamp burning in their room and those with one. Null until it has been read. */
        private OpenLight seenRooms, seenLamplit;
        /** Dev only: what the build holds, what of it was stood as the mod's model, and what hangs showing the mod's picture, for SightTrace. Null unless the trace is on. */
        Set<Object> traced, modelled, pictured;
        /** Dev only: the last frame that showed the build. */
        int shownFrame = -1;
        /** Dev only: every float of the build as one number. */
        private long auditFloats;
        /** Dev only, for auditStale: each mesh of the build as its set, texture and tint in one number, then how many floats it had, then their sum. */
        private long[] auditMeshes;
        /** Dev only: what the sum was made of, part by part. See audit. */
        private long auditTerrain, auditTrees;
        private long[] auditLights, auditThings;
        private int auditFrom, auditTo;
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
        /** Which player's view the build is of: a build goes back to be filled again by the view that made it. */
        final int of;

        ChunkMeshes(int of) {
            this.of = of;
        }

        /**
         * Makes this build of what two sets hold, the solid and the laid, and leaves the sets
         * as they were. Run on a build no frame in flight shows and no table holds, by the
         * one thread that has it: the logic thread, or the crowns' thread for the build it
         * was handed.
         */
        void fill(SpriteGeometry.MeshSet solid, SpriteGeometry.MeshSet laid) {
            // The last filling's vertices are still here when no frame that showed it was ever drawn.
            scrap();
            runs = 0;
            floats = 0;
            owned = 0;
            minX = minY = minUp = Float.POSITIVE_INFINITY;
            maxX = maxY = maxUp = Float.NEGATIVE_INFINITY;
            turned = 0f;
            layered = 0f;
            note(solid);
            solidRuns = runs;
            note(laid);
            if (DepthFights.ON) {
                depthCopy = crowns != null ? null : DepthFights.copy(wx, wy, grown, solid, laid);
            }
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
                    runTexture = Arrays.copyOf(runTexture, runs * 2);
                    runTint = Arrays.copyOf(runTint, runs * 2);
                    runFloats = Arrays.copyOf(runFloats, runs * 2);
                    runRoom = Arrays.copyOf(runRoom, runs * 2);
                    runRoomToo = Arrays.copyOf(runRoomToo, runs * 2);
                    runUnless = Arrays.copyOf(runUnless, runs * 2);
                    runBuilding = Arrays.copyOf(runBuilding, runs * 2);
                    runLevel = Arrays.copyOf(runLevel, runs * 2);
                }
                runTexture[runs] = mesh.texture;
                runTint[runs] = mesh.tint;
                long[] two = RoomSight.pairOf(mesh.room);
                boolean blind = two != null && two[0] == RoomSight.BLIND;
                runRoom[runs] = two == null ? mesh.room : blind ? RoomSight.OUTSIDE : two[0];
                runRoomToo[runs] = two == null ? mesh.room : blind ? RoomSight.OUTSIDE : two[1];
                runUnless[runs] = blind ? two[1] : RoomSight.OUTSIDE;
                runBuilding[runs] = mesh.building;
                runLevel[runs] = mesh.level;
                runFloats[runs] = mesh.floats;
                for (int span = 0; span < mesh.spans; span++) {
                    if (owned == ownedKey.length) {
                        int more = Math.max(16, owned * 2);
                        ownedKey = Arrays.copyOf(ownedKey, more);
                        ownedRun = Arrays.copyOf(ownedRun, more);
                        ownedFrom = Arrays.copyOf(ownedFrom, more);
                        ownedFloats = Arrays.copyOf(ownedFloats, more);
                    }
                    ownedKey[owned] = mesh.spanKey[span];
                    ownedRun[owned] = runs;
                    ownedFrom[owned] = floats + mesh.spanFrom[span];
                    ownedFloats[owned] = mesh.spanTo[span] - mesh.spanFrom[span];
                    owned++;
                }
                runs++;
                floats = Math.addExact(floats, mesh.floats);
                float[] from = mesh.vertices;
                float lowX = minX, lowY = minY, lowUp = minUp, highX = maxX, highY = maxY, highUp = maxUp, along = turned, layers = layered;
                for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                    float x = from[at], y = from[at + 1], up = from[at + 2];
                    float moved = from[at + SpriteGeometry.WAY_AT];
                    // Snow may stand on wood as high as its ridge, and as far out to its sides: see SnowShader.RIDGE.
                    float top = up, side = 0f;
                    if (moved == SpriteGeometry.FACES) {
                        along = Math.max(along, Math.abs(from[at + SpriteGeometry.ALONG_AT]));
                    } else if (moved == SpriteGeometry.WOOD) {
                        top += SnowShader.RIDGE_HIGH;
                        side = SnowShader.RIDGE_WIDE * SnowShader.RIDGE_HIGH;
                    } else if (moved != 0f) {
                        layers = Math.max(layers, Math.abs(from[at + SpriteGeometry.ALONG_AT]));
                    }
                    lowX = Math.min(lowX, x - side);
                    highX = Math.max(highX, x + side);
                    lowY = Math.min(lowY, y - side);
                    highY = Math.max(highY, y + side);
                    lowUp = Math.min(lowUp, up);
                    highUp = Math.max(highUp, top);
                }
                minX = lowX;
                minY = lowY;
                minUp = lowUp;
                maxX = highX;
                maxY = highY;
                maxUp = highUp;
                turned = along;
                layered = layers;
            }
        }

        private static void copy(SpriteGeometry.MeshSet set, FloatBuffer into) {
            for (int i = 0; i < set.used.size(); i++) {
                SpriteGeometry.Mesh mesh = set.used.get(i);
                into.put(mesh.vertices, 0, mesh.floats);
            }
        }

        /** Gives back what a build that will not be filled again has on loan. Run as fill is: fill runs it. */
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

    /** A chunk nearer the eye than this falls due every second or third frame, and one within a chunk and a half of the eye every frame: see buildEvery. */
    private static final float BUILT_EVERY_FRAME = 20f;
    private static final float BUILT_OFTEN = 44f;
    /** Chunks within this of the eye are drawn whichever way the view faces: what is behind the eye still casts a shadow into the picture. */
    private static final float CASTS_SHADOWS = 40f;
    /** The latest build of each chunk, by the low bits of its position. The loaded map is at most 19 chunks a side, so no two of its chunks share a place. */
    private static final int TABLE_SIDE = 64;
    private final ChunkMeshes[] latest = new ChunkMeshes[TABLE_SIDE * TABLE_SIDE];
    /**
     * What grows on each chunk's ground, the lawn, ferns, flowers and fallen leaves, as a build
     * of its own beside the chunk's: all of it is made by how far from the eye it is, and it is
     * a tenth of the work of a chunk, so it is what is made again as the eye moves.
     */
    private final ChunkMeshes[] latestGrown = new ChunkMeshes[TABLE_SIDE * TABLE_SIDE];
    /**
     * The crowns of each chunk's trees and bushes, as a build of their own beside the chunk's.
     * A build of the chunk writes down what its crowns are made from (crownsRead) and makes
     * none: the crowns it has stand while they were made from the same, and are otherwise
     * made on a thread of their own and stand until the new ones come back. A chunk with no
     * crowns standing has them made there and then, so that no tree is ever seen bare, and
     * so has one built because the map changed: see crown.
     */
    private final ChunkMeshes[] latestCrowned = new ChunkMeshes[TABLE_SIDE * TABLE_SIDE];
    /** The crowns of each chunk that are out being made. */
    private final Crowns.Work[] crownsWaited = new Crowns.Work[TABLE_SIDE * TABLE_SIDE];
    /** How many pieces of work are out, wanted or not: each comes back, made or not, in crownsBack. */
    private int crownsOut;
    private final LinkedBlockingQueue<Crowns.Work> crownsBack = new LinkedBlockingQueue<>();
    /** What the build being made has written down of its crowns, and what a check's second build has. */
    Crowns crownsRead = new Crowns();
    static final Crowns crownsChecked = new Crowns();
    private static final ArrayDeque<Crowns> spareCrowns = new ArrayDeque<>();
    private static final SpriteGeometry.Frame crownFrame = new SpriteGeometry.Frame();
    private final ArrayDeque<ChunkMeshes> spare = new ArrayDeque<>();
    /** Builds no chunk is using, kept to be filled again: as many as a turn of the head brings into view at once. */
    private static final int MOST_SPARE = 96;
    private static final int FORGET_EVERY_FRAMES = 120;
    /**
     * How long the build of a chunk of open country is kept with its sum unchanged: only a
     * backstop, since nothing such a build reads is left out of its sum. See ChunkMeshes.plain.
     */
    private static final long KEPT_PLAIN_NANOS = 20_000_000_000L;
    /**
     * With the game telling which chunks it changes (ChunkMarks), how long a build is kept
     * unchanged: a backstop for whatever a build goes by that neither the game marks nor
     * the chunk's sum holds nor the mod tells of itself.
     */
    private static final long KEPT_TOLD_NANOS = Long.getLong("cameraunlock.zomboid.keptToldMillis", 60_000L) * 1_000_000L;
    /**
     * With the game telling, how often a chunk nothing has been said of is read through all
     * the same, by how far off it is: the game changes some things without a mark, plants
     * that grow, a sign on a crate that says what is in it, and they show this much late.
     */
    private static final long SWEPT_NEAR_NANOS = 500_000_000L, SWEPT_NANOS = 1_000_000_000L, SWEPT_OFTEN_NANOS = 3_000_000_000L, SWEPT_FAR_NANOS = 6_000_000_000L;
    private static final int SWEPT_A_FRAME = 2;
    /**
     * How many builds a frame are made again only for their age. A head that turns back to
     * a street it looked away from finds every far chunk of it aged on the same frame.
     */
    private static final int AGED_A_FRAME = 2;
    /** Over how many frames builds that hold a tree the season shapes come to its next stage: see Frame.seasonHolds. */
    private static final int STAGE_SPREAD = 120;
    /** How soon a chunk right by the eye that keeps changing is built again: sixty times a second, whatever the frame rate. */
    private static final long CHANGING_BUILT_EVERY_NANOS = 16_000_000L;
    /**
     * A build found unchanged is still made again once it is this old. The sum of what a
     * build reads leaves out what it is told by the roofs, the wall finishes and the sprite
     * colours, which are worked out over several frames as the map round a chunk loads: for
     * its first three seconds a chunk is built again a quarter of a second after it was last,
     * which is how late it has always shown those. After that they have settled, and while
     * the game does not tell of its changes it is built again every two seconds, where every
     * quarter second was a view standing still building five chunks a frame for nothing.
     * While the game tells, it is KEPT_TOLD_NANOS for every chunk.
     */
    private static final long KEPT_UNCHANGED_NANOS = 2_000_000_000L, KEPT_WHILE_SETTLING_NANOS = 240_000_000L, SETTLES_IN_NANOS = 3_000_000_000L;
    /** For this many frames a chunk that was found changed is built as it falls due without being read through first. */
    private static final int KEPT_UNCHANGED_FRAMES = 30;
    /** Where the eye was in the frame before this one, and whether it is in the same place now. */
    float lastEyeX = Float.NaN, lastEyeY, lastEyeUp;
    private boolean eyeStill;
    /** The loaded map as it was when the last frame was built: its first chunk each way and how many chunks a side it is. */
    int mapWx, mapWy, mapSide;
    /** How many times this view's world has been built, and the round of every view's builds that the last was in: see Rounds. */
    int frames, roundNow;
    /**
     * When each view's world was last built, on System.nanoTime's clock, and 0 for one set
     * down since. A view whose world was built within the last second is being built: what
     * the views share is let go once none is, and the frame's time for building is shared
     * between those that are. A view set down may still hold its builds, so only what the
     * next build makes again for itself is let go by this.
     */
    private static final long[] builtAt = new long[View.COUNT];
    private static final long BUILDING_WITHIN_NANOS = 1_000_000_000L;

    private static int building(long now) {
        int building = 0;
        for (long at : builtAt) {
            building += at != 0L && now - at < BUILDING_WITHIN_NANOS ? 1 : 0;
        }
        return building;
    }
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
    static final SpriteGeometry.Layers layers = new SpriteGeometry.Layers();
    static final SpriteGeometry.MeshSet solid = new SpriteGeometry.MeshSet(layers), laid = new SpriteGeometry.MeshSet(layers);
    /** How far in front of the place looked at the picture reaches from far overhead, in tiles. */
    private static final float OVERHEAD_DEPTH = 160f;
    /** The distance the place looked at is taken to be at, for what the picture does by distance. */
    static final float OVERHEAD_RANGE = 12f;
    /** How far past the ground the game's own view shows its darkening is read, in squares: what stands on a square below the screen's edge reaches up into it. */
    private static final float SIGHT_MARGIN = 4f;
    /** How much of its opening-up the eye is refused at full night. */
    private static final float NIGHT_HELD_BACK = 0.6f;
    /**
     * The highest floor built where the game leaves out what is over it: every floor is built,
     * but for a view drawn as the game's own while the game opens a building or the viewer is
     * under the ground.
     */
    private int topLevel = Integer.MAX_VALUE;
    /** The buildings the game opens in its own view, as the builds were last made for them, and as the game has them now. */
    private final ArrayList<BuildingDef> openBuildings = new ArrayList<>(), openBuildingsNow = new ArrayList<>();
    /** The rooms of those buildings, where rain and snow are kept out with no roof drawn: see Shelter. Null while no building is open. */
    private Shelter shelter;
    /** Which air round the eye is in a room, kept from one build to the next: see RoomAir. */
    private RoomAir roomAirKept;

    /** The rooms of the opened buildings round a square, each up to its own ceiling. */
    private Shelter shelterRound(int x, int y) {
        Shelter made = new Shelter(x, y);
        for (int i = 0; i < openBuildings.size(); i++) {
            BuildingDef def = openBuildings.get(i);
            for (int r = 0; r < def.rooms.size(); r++) {
                RoomDef room = def.rooms.get(r);
                for (int k = 0; k < room.rects.size(); k++) {
                    RoomDef.RoomRect rect = room.rects.get(k);
                    made.cover(rect.x, rect.y, rect.w, rect.h, room.level + 1);
                }
            }
        }
        return made;
    }

    /**
     * Whether the builds standing gave the plants of the last band a crown and a bole:
     * Quality.crownsToSight as the logic thread last read it. Without, a tree out there is the
     * game's sprite on its fan of cards, which turns into a crown as the eye comes within
     * Crowns.REACH of it.
     */
    boolean crownedToSight;
    /** The share of its plants grass was grown with by the builds standing: Grass.density as the logic thread last read it. */
    float grassGrown = Float.NaN;
    /** The detail from which the builds standing gave a bare crown twigs, for this view's picture: Flora.twiggedFrom. */
    float twiggedFrom = Float.NaN;

    /**
     * Whether the roof and the ceilings the mod makes over a square are left out so that the
     * viewer can be seen from overhead. They lie over more than their own square, so they go by
     * the box of a building the game opens, a square wider all round, and under the ground
     * by nothing: the game shows nothing over a viewer there.
     */
    boolean cutAway(int x, int y) {
        if (topLevel < 0) {
            return true;
        }
        for (int i = 0; i < openBuildings.size(); i++) {
            BuildingDef def = openBuildings.get(i);
            if (x >= def.getX() - 1 && x < def.getX() + def.getW() + 1 && y >= def.getY() - 1 && y < def.getY() + def.getH() + 1) {
                return true;
            }
        }
        return false;
    }

    /** Lets go of the builds of the chunks a building stands on. */
    private void forgetOver(BuildingDef def) {
        forgetOver(def.getX() - 1, def.getY() - 1, def.getX() + def.getW() + 1, def.getY() + def.getH() + 1);
    }
    /** How much of a wall is left standing when the walls are cut down, in tiles. */
    private static final float WALL_LEFT = 0.7f;
    /** How far under the floor above the lid is, in tiles: the ceiling goes with what is over it. */
    private static final float LID_UNDER = 0.1f;

    /**
     * The light of the squares the character has seen that are out of doors, counted over this
     * frame, and the light most of them had in the last frame that had any: see OpenLight. A
     * square the character has not seen has no light of its own yet: the game holds it black
     * until it is seen.
     */
    private final OpenLight seenOutside = new OpenLight();
    /**
     * The tally is kept over half a second, each chunk counted in it once, and the open
     * ground's light is taken from it when the half second is up. Taken afresh every frame
     * from whichever chunks that frame happened to read, it was never quite the same twice,
     * and what the game has given no light is lit by it (OPEN_LIT): at night, most of what
     * is in view flickered with it. A step of under a five-hundredth in the light is left
     * untaken, for the same reason.
     */
    private static final long OPEN_LIGHT_TAKEN_EVERY_NANOS = 500_000_000L;
    private static final float OPEN_LIGHT_STEP = 1f / 512f;
    private long openLightTakenAt;
    private boolean openLightTaken;
    private float unseenRed = -1f, unseenGreen, unseenBlue;
    /**
     * The light of the rooms the character has in sight, counted with the open ground's, and
     * what a room the game has not shown yet is lit by until it has a light of its own. With
     * no lamp burning in it that is the open ground's light times roomShare, how bright most
     * room squares in sight are beside the open ground: it follows the hour as the open
     * ground's does. With a lamp burning it is lampLevel, how bright most lamplit squares in
     * sight are, which the hour does not change. Each is the readings followed a share at a
     * time (Sightline.followed), and no build holds either: see LIT_STEP. Less than 0 until
     * rooms have been in sight.
     */
    private final OpenLight seenRooms = new OpenLight(), seenLamplit = new OpenLight();
    private float roomShare = -1f, lampLevel = -1f;
    /** When the lights of what the game has given no light were last eased towards their readings: see UNLIT_EASE_SECONDS. */
    private long unlitEasedAt;
    /**
     * Those lights as eased so far, handed to each frame's sky (SkyState.openUnlit, daylight, roomLight
     * and lampLight). Kept here and not in the sky: the frames take turns, four of them, so a sky's own
     * last value is four frames old while unlitEasedAt is one frame's.
     */
    private final float[] openUnlitEased = new float[3], daylightEased = new float[3], roomLightEased = new float[3], lampLightEased = new float[3];
    private long roomLightTakenAt;
    /** How near the eye a chunk is, in tiles, for what stands in its rooms the game has not shown to be built: within the last band but one. See Sightline.emptied. */
    private static final float UNSHOWN_FILLED_WITHIN = 40f;
    /** How bright the open ground has to be for roomShare to be taken: under it, a sixty-fourth of light, which is as fine as the tally counts, is more than a step of the share. */
    private static final float ROOM_SHARE_TAKEN_FROM = 0.25f;
    /** Fewer squares than this in sight say nothing of what most rooms have. */
    private static final int ROOM_LIGHT_FROM = 12;
    /** The light a square out of doors has with no lamp on it: see OpenLight.floor. */
    private float floorRed, floorGreen, floorBlue;
    /** The open ground's light the last time the game had it full night. */
    private float nightOpen = Evening.NIGHT_OPEN;
    /** The open ground's light as the game has it and how much of its daylight the picture keeps, for whoever lights a thing by its square. */
    volatile float openNow, daylightKeptNow = 1f, roomKeptNow = 1f;
    /** Whether the frame last built lit rooms by the day they let in, how much of the day there was, and the open ground's light by as much of it, in its steps: see Frame.daylit. */
    volatile boolean roomsDaylitNow;
    volatile float dayNow, dayRedNow, dayGreenNow, dayBlueNow;
    private static final int REMAKES_A_FRAME = 12;
    private static final IdentityHashMap<IsoSprite, Boolean> meshes = new IdentityHashMap<>();

    /**
     * What a build asks of a sprite that is the same at every asking: its properties that go
     * by name, each of which the game looks up through two maps keyed by the name, and what
     * the sprite's own name begins with. Nothing here depends on the object that shows the sprite.
     */
    static final class Kind {
        /** What the sprite is made of as the floor of its square, and as anything else. */
        final float asFloor, asOther;
        final boolean laidWall, gableSheet, lies, wire, roofAccent, roofSheet, stripLamp, garageDoor, cornerTrim, papered, tiledWall, fenceGate,
                boulder, bedding, longGrass, natural, carpet, tiled, grows, onTop, holds, flightPost, fireEscape, paint, wallTrim, trim;
        /** What length of handrail the sprite is, or null: see Rails. */
        final Rails.Piece rail;
        /** Which cloth the map hangs out the sprite is, if any: see Banners. */
        final int banner;
        /** Which way a length of washing line runs from its square, or Outdoors.NO_LINE. */
        final int line;
        /** Which edges a sprite on a wall is laid on where its picture overrules its marks, or Overhead.ANY_EDGE. */
        final int edge;
        /** The wall of its square the sprite lies flat on when nothing else is made of it: see WallObjects. */
        final int flatWall;
        /** Whether it is a broken length of fence the game marks for no wall: see BrokenFences. */
        final boolean brokenFence;
        /** The length of chain link fencing it is, or null: see ChainLink. */
        final ChainLink.Run chainLink;
        /** As a torn or burnt length of chain link, the material its sprite is drawn with, or 0: see ChainLink.brokenMaterial. */
        final float brokenLink;
        /** Whether it is laid as one sheet of chain link, whole, torn or burnt: see ChainLink.sheet. */
        final boolean linkSheet;
        /** Whether it is a whole length of white picket fence: see Pickets. */
        final boolean pickets;
        /** As a tree, what it drops, for a tree that turns the first of its kind's colours and for one that turns the other: see Litter.kindOf. */
        private final byte falls, fallsOther;
        /** What it is a square of rug made of, Rugs.WOVEN, FUR or HIDE, or -1 for no rug, and how high its top lies over the floor, in levels: see Flat.pile. */
        final int rug;
        final float pile;
        /** What it is of a string of lights, a round canopy, an awning, a tent, a roof's hood and a structure the mod has models of: see each class's own reading. */
        final int lights, parasol, eave;
        final Awnings.Hung awning;
        final Tents.Piece tent, rope;
        final boolean endPiece;
        final Towers.Part[] tower;
        /** Whether it is the letters of a sign: see EdgeRuns.sign. */
        final boolean sign;
        /** Whether it is a stained glass window: see StainedGlass. */
        final boolean stained;
        /** For a sprite of tiled walls, its sheet: see Frame.tiledSheet. */
        final String tiledSheet;
        /** What of partOfTheBuilding and withinItsRoom goes by the sprite alone. */
        final boolean ofTheBuilding, keptInItsRoom;
        /** Whether it is seen only from within the room of its square, or from where the eye sees into it: a floor, what is laid as one, a stair. */
        final boolean seenInItsRoom;

        /** What a tree of the sprite drops, given which of its kind's two colours it turns: see Flora.otherHue. */
        byte drops(boolean otherHue) {
            return otherHue ? fallsOther : falls;
        }

        /**
         * Whether a sprite is a wall the picture may lay as masonry: one of a sheet of outside walls. A garage
         * door is on such a sheet and is no masonry: its sections' grooves read as courses of brick.
         */
        static boolean laidWall(String name, PropertyContainer properties) {
            return Masonry.suits(name) && !properties.has("GarageDoor");
        }

        Kind(IsoSprite sprite) {
            PropertyContainer properties = sprite.getProperties();
            String name = sprite.getName();
            IsoObjectType type = sprite.getType();
            String floorMaterial = properties.get("FloorMaterial"), footstep = properties.get("FootstepMaterial");
            float floor = Ground.of(floorMaterial, footstep, true), other = Ground.of(floorMaterial, footstep, false);
            asFloor = floor != 0f ? floor : materialOf(name, true);
            String madeOf = properties.get("Material");
            boolean onEdge = Edges.west(properties, type) || Edges.north(properties, type);
            boolean picket = properties.has("StopCar") || properties.has("TieSheetRope");
            asOther = printed(properties, name) ? PRINT : other != 0f ? other : Fences.ironBars(name, madeOf) ? 0f
                    : Fences.boards(name, madeOf, properties.get("MaterialType"), properties.get("Material2"), onEdge, picket) ? BOARDS : materialOf(sprite);
            garageDoor = properties.has("GarageDoor");
            laidWall = laidWall(name, properties);
            gableSheet = laidWall && name.startsWith("walls_exterior_roofs");
            lies = Flat.lies(properties.get("RenderLayer"), properties.get("MoveType"));
            wire = name != null && name.startsWith("appliances_com");
            roofAccent = name != null && name.startsWith("roofs_accents");
            roofSheet = name != null && name.startsWith("roofs_");
            stripLamp = sprite.getSpriteGrid() != null && properties.has("IsHigh") && "Lamp".equals(properties.get("CustomName"))
                    && !properties.has(IsoFlagType.attachedN) && !properties.has(IsoFlagType.attachedW) && !properties.has(IsoFlagType.attachedE)
                    && !properties.has(IsoFlagType.attachedS);
            cornerTrim = (properties.has("attachedNW") || properties.has("attachedSE")) && !Roofs.isTile(type) && !roofSheet;
            papered = name != null && name.startsWith("walls_interior_house");
            tiledWall = WallTiles.tiledSheet(name);
            wallTrim = name != null && name.startsWith("walls_detailing_") && !Edges.west(properties, type) && !onNorthEdge(properties)
                    && !properties.has(IsoFlagType.attachedE) && !properties.has(IsoFlagType.attachedS);
            fenceGate = name != null && name.startsWith("fixtures_doors_fences");
            trim = Trims.is(name);
            boulder = name != null && name.startsWith("boulders_");
            bedding = name != null && name.startsWith("furniture_bedding");
            longGrass = name != null && name.startsWith("e_newgrass");
            natural = name != null && name.startsWith("blends_natural");
            carpet = name != null && name.startsWith("floors_interior_carpet");
            rug = Rugs.kindOf(name);
            pile = Flat.pile(name);
            falls = Litter.kindOf(name, false);
            fallsOther = Litter.kindOf(name, true);
            tiled = name != null && name.startsWith("floors_") && name.contains("tile");
            grows = properties.has(IsoFlagType.vegitation) || properties.has(IsoFlagType.canBeRemoved) || properties.has("MoveWithWind")
                    || Farm.wheat(name);
            onTop = properties.has("IsTableTop");
            holds = properties.get("container") != null;
            rail = Rails.of(name);
            flightPost = Rails.flightPost(name);
            fireEscape = Rails.fireEscape(name);
            paint = RoadPaint.marks(name);
            banner = Banners.kind(name, properties.get("CustomName"), properties.has(IsoFlagType.attachedN)
                    || properties.has(IsoFlagType.attachedW) || properties.has(IsoFlagType.attachedE) || properties.has(IsoFlagType.attachedS));
            line = name == null ? Outdoors.NO_LINE : Outdoors.washingLine(name);
            edge = name == null ? Overhead.ANY_EDGE : Overhead.edgeOf(name);
            flatWall = WallObjects.flatOn(name, properties, sprite.getFacing());
            brokenFence = BrokenFences.is(name);
            chainLink = ChainLink.of(name);
            brokenLink = ChainLink.brokenMaterial(name, madeOf, onEdge);
            linkSheet = ChainLink.sheet(name, properties, type);
            pickets = Pickets.is(name);
            lights = Lights.slice(sprite);
            parasol = Parasols.quarter(sprite);
            eave = Hoods.eave(sprite);
            endPiece = Hoods.endPiece(sprite);
            awning = Awnings.of(sprite);
            tent = Tents.of(sprite);
            rope = Tents.ropeOf(sprite);
            tower = Towers.of(sprite);
            sign = EdgeRuns.sign(name, properties.get("CustomName"));
            stained = StainedGlass.of(name);
            tiledSheet = tiledWall ? name.substring(0, name.lastIndexOf('_') + 1) : null;
            ofTheBuilding = ofTheBuilding(sprite);
            keptInItsRoom = keptInItsRoom(sprite);
            seenInItsRoom = !Roofs.isTile(type) && (isStair(type) || properties.has(IsoFlagType.solidfloor) || properties.has(IsoFlagType.FloorOverlay)
                    || FloorMarks.onFloor(name, properties));
        }
    }

    private static final IdentityHashMap<IsoSprite, Kind> kinds = new IdentityHashMap<>();
    private static final int MESH_CHANGES = 12;
    private static final float SWITCH_WIDEST = 12f;
    /** For each sprite hung in the air: the wires it shows, see Wires.read. */
    static final IdentityHashMap<Texture, float[]> wires = new IdentityHashMap<>();
    private static final long[] NO_CHUNKS = new long[0];
    /** The map the kept builds were read from, and the one everything every view keeps by place or by sprite was. */
    private WeakReference<IsoCell> builtIn = new WeakReference<>(null);
    private static WeakReference<IsoCell> readIn = new WeakReference<>(null);
    /** How thick an overhead wire is drawn, in tiles: thicker than one is, or a street away it is finer than a pixel and comes and goes. */
    static final float WIRE_THICK = 0.03f;
    /** What hangs on a cord and gives no light: a bulb that is out, and a bulb's holder. The picture swings it with its cord and lights it as any plain thing. */
    static final float HUNG = 82f;
    /** How far under a roof tile its plain sheet lies, in levels. */
    static final float UNDER_ROOF = 0.03f;
    /** Half the thickness of a wall, in tiles. */
    static final float WALL_HALF = 0.04f;
    /** How far from the line of a wall the middle of a handrail fixed to it runs, in tiles. */
    private static final float RAIL_OFF_WALL = WALL_HALF + 0.09f;
    static final float HUNG_CLEAR = 0.012f;
    /** The game gives the height of a surface in pixels of its unscaled sprites, of which a level is this many. */
    static final float SURFACE_PIXELS_PER_LEVEL = 96f;
    static final int TABLE_HIGHEST = 48;
    static final int TALL_SURFACE = 60;
    private static final float CUPBOARD_DEEP = 0.55f;
    static final int WOOD_SPAN = CHUNK_TILES + 2 * Litter.RING;
    /** How far from the eye each thing Flora makes is still made, in tiles. Past TRUNK_NEAR a bole is a plain post, and past ROCK_NEAR a boulder has a quarter of its faces. */
    static final float FERN_REACH = 22f, FERN_NEAR = 12f, FALLEN_REACH = 10f, FLOWER_REACH = 14f, TRUNK_NEAR = 18f, TRUNK_REACH = 60f,
            ROCK_NEAR = 30f, BUSH_REACH = 40f;
    /** How far in from the edge of a lawn its grass takes to reach its full height, in tiles. */
    private static final float LAWN_EDGE = 0.9f;
    /** What was read off each tree's sprite: how thick its bole is and how tall, in tiles. Empty for a sprite with no bole to read. */
    static final IdentityHashMap<IsoSprite, float[]> boles = new IdentityHashMap<>();
    static final float[] NO_BOLE = new float[0];
    /** The outline of the branches each broad-leaved tree's trunk sprite paints: see Flora.woodShape. */
    static final IdentityHashMap<IsoSprite, float[]> woodShapes = new IdentityHashMap<>();
    /** How far down the wall of a quay goes from its top: past the water's surface. */
    private static final float QUAY_DEPTH = 0.6f;
    /** How far a layer of cards stands from the one under it besides what any layer does, in tiles: leaves on the card of their trunk. */
    static final float CARD_LAYER = 0.035f;
    /**
     * How many layers of cards towards the eye a thing stood on another stands from it. A card
     * turns to the eye through its square's middle, and so does the card of what holds it up,
     * a table's front or a counter's: two cards in one plane, and the thing on top fought the
     * table for each pixel where the two pictures overlap. Two layers, so it is also clear of
     * a layer the game draws over the table, a stain.
     */
    static final int STANDS_NEARER = 2;

    /**
     * How many layers of cards towards the eye an object's card stands: STANDS_NEARER more for
     * each thing stood on something before it on the square, so two things on one table are
     * not in one plane either, and none for anything that stands on the floor.
     */
    static float standingNearer(boolean standsOn, int stoodOnBefore) {
        return standsOn ? STANDS_NEARER * (stoodOnBefore + 1) : 0f;
    }
    /** The height of a bed, in levels. */
    static final float BED_HEIGHT = 0.2f;
    /** How much of a tree's trunk sprite, from the ground up, is its bole. */
    static final float BOLE = 0.42f;
    /** How far from the eye a lawn's grass stands up, in tiles. */
    private static final float GRASS_REACH = 16f;
    private static final IdentityHashMap<IsoSprite, int[]> ranks = new IdentityHashMap<>();
    /** A chunk's squares and the ring of squares round them. */
    static final int LIGHT_SPAN = CHUNK_TILES + 2;
    /**
     * Kinds of square by their light: lit by the game, an unshown room, an unshown room with a
     * lamp burning, and anything else the game has given no light, which is lit as the open
     * ground with no lamp on it is. Those lights move as the eye looks about and the clouds go over:
     * baked into the builds, each step had every chunk in view built again, a tower's thirty
     * storeys at a time. So a build gives such a square the light SHADE_ONLY, which the
     * shades a builder lays on make the shade alone, and says which light it takes in its
     * openness, LIT_STEP times its kind: the world shader multiplies the shade by this
     * frame's uRoomLight, uLampLight or uOpenLight. A corner's mean does not mix kinds.
     */
    static final byte OWN_LIGHT = 0, ROOM_LIT = 1, LAMP_LIT = 2, OPEN_LIT = 3;
    /** What a square's kind of light is counted in, in a face's openness: above the 32 of every material there is. */
    static final float LIT_STEP = 4096f;
    /** The openness of a face out of doors that the game has given no light: see OPEN_LIT. */
    static final float OPEN_UNLIT = 1f + LIT_STEP * OPEN_LIT;
    /** The light a build gives a square the game has given none: read only. */
    static final ColorInfo SHADE_ONLY = shadeOnly();

    private static ColorInfo shadeOnly() {
        ColorInfo light = new ColorInfo();
        light.r = light.g = light.b = 1f;
        return light;
    }

    /** Whether an openness, which may carry a kind of light, is a square's out of doors. */
    static boolean outdoors(float open) {
        return open % LIT_STEP > 0.5f;
    }

    /**
     * Dev only: every sum that finds a chunk changed is set against what ChunkMarks counted,
     * and one the game marked nothing for is logged with what of it changed. See audit.
     */
    private static final boolean AUDIT_MARKS = ChunkMarks.COUNTED;
    private long tollMarkedChanges, tollMissedChanges, tollMarkedSame, tollMarkedByReading, tollPulledAfterGame;
    private int auditLogged;
    private final long[] tollWhy = new long[10];
    /** Which chunks of the map are loaded, as one number. */
    private int loadedNow;
    /** The map the builds read, for whether a chunk of it is loaded. */
    IsoChunkMap chunkMap;
    /**
     * The spans the lengths of overhead wire hang in, as Spans finds them, by the square, the
     * sprite and which of its lines a length is. A span is found by following its wire to both
     * its poles, some chunks either way, once: the map does not move its poles. One left
     * unknown at an end for want of map is found again when the map has other chunks loaded.
     */
    final HashMap<Long, float[]> strung = new HashMap<>();
    private int strungWith;
    /** The run each length of a banner strung across a street is part of, as Banners finds it, by the square it hangs on. Kept and dropped as strung is. */
    private final HashMap<Long, float[]> bannerRuns = new HashMap<>();
    /** The banner sprites the log has said paint nothing at an end of their square. */
    private static final Set<String> bannersUnpainted = ConcurrentHashMap.newKeySet();
    /** The lighting engine's count of its passes when each chunk's lights were last asked after, and the game's own note on the chunk of when it last took them. */
    private final int[] litAt = new int[TABLE_SIDE * TABLE_SIDE], litByGame = new int[TABLE_SIDE * TABLE_SIDE];
    /** The storey of the viewer's building each slot's light was last asked for from, or Integer.MAX_VALUE for every level: see pullLights. */
    private final int[] litFor = new int[TABLE_SIDE * TABLE_SIDE];
    private long tollQuiet, tollPulls, tollPulled, tollAgedDiffered, tollAgedSame, tollAgedSamePlain, tollRestless;
    /** Dev only: the chunks built ahead of the view's turning to them since the last asking, and the time that took. */
    private long tollAhead, tollAheadNanos;
    /** Dev only: how many times the room walk's horizon had been built when tolls last said. */
    private int horizonsTold;
    /** Dev only: builds due for nothing but light that waited, the view not facing their chunk. */
    private long tollLitUnfaced, tollChangedWaited;
    /** Dev only: see SightTrace. */
    private SightTrace sightTrace;
    /** The rooms and their openings round the eye, and the walk through them: see RoomSight. Logic thread. */
    final RoomMap roomMap = new RoomMap();
    /** The daylight that comes into each room through its openings: see RoomDaylight. Worked out on the logic thread, read on any. */
    final RoomDaylight roomDaylight = new RoomDaylight();

    /**
     * Any thread: a square's light with the day its room lets in, as the frame last built lit
     * rooms (Frame.daylit), for what is drawn in a room without being built with it: people,
     * what the viewer holds. Into into, or the light given back as it is.
     */
    ColorInfo daylitNow(IsoGridSquare square, ColorInfo light, ColorInfo into) {
        float day = dayNow;
        if (!roomsDaylitNow || day <= 0f || square.getRoomID() == RoomSight.OUTSIDE) {
            return light;
        }
        float share = roomDaylight.sharedAt(square);
        into.r = Math.max(light.r, share * dayRedNow);
        into.g = Math.max(light.g, share * dayGreenNow);
        into.b = Math.max(light.b, share * dayBlueNow);
        into.a = light.a;
        return into;
    }

    /**
     * Logic thread: the room map, ready to be read, and the openings on the levels from fromZ
     * to toZ of every loaded chunk of a player's map whose middle is within reach of a point,
     * each chunk read again if it may
     * have changed. The openings are gathered into {@code into} only when the chunks or what
     * was read of them differ from the last gathering, whose number is {@code last}: the
     * number of this one is returned.
     */
    long openingsAround(IsoCell cell, int playerIndex, float x, float y, float reach, int fromZ, int toZ, RoomSight.Openings into, long last) {
        roomMap.begin(cell, terrain);
        IsoChunkMap map = cell.getChunkMap(playerIndex);
        int side = IsoChunkMap.chunkGridWidth;
        long now = System.nanoTime(), gathering = (17L * 31 + fromZ) * 31 + toZ;
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) {
                if (gathering == last) {
                    return gathering;
                }
                into.clear();
            }
            for (int gridX = 0; gridX < side; gridX++) {
                for (int gridY = 0; gridY < side; gridY++) {
                    IsoChunk chunk = map.getChunk(gridX, gridY);
                    if (chunk == null || !chunk.loaded || farther(chunk, x, y, reach)) {
                        continue;
                    }
                    if (pass == 0) {
                        IsoChunk north = gridY > 0 ? map.getChunk(gridX, gridY - 1) : null, west = gridX > 0 ? map.getChunk(gridX - 1, gridY) : null;
                        int readings = roomMap.read(chunk, north != null && north.loaded ? north : null, west != null && west.loaded ? west : null, ChunkMarks.live, now);
                        gathering = ((gathering * 31 + chunk.wx) * 31 + chunk.wy) * 31 + readings;
                    } else {
                        roomMap.addOpeningsTo(chunk, into, fromZ, toZ);
                    }
                }
            }
        }
        return gathering;
    }

    /** Whether the middle of a chunk is further than reach from (x, y). */
    static boolean farther(IsoChunk chunk, float x, float y, float reach) {
        float dx = chunk.wx * CHUNK_TILES + CHUNK_TILES / 2f - x, dy = chunk.wy * CHUNK_TILES + CHUNK_TILES / 2f - y;
        return dx * dx + dy * dy > reach * reach;
    }

    private final RoomSight roomWalk = new RoomSight();
    /**
     * The openings and blocks in reach of the eye, and what they were last gathered from: see
     * Frame.seeRooms. Logic thread. Kept here with the walk, not with each frame: four frames
     * took turns handing the walk lists of the same things, and it built its horizon of every
     * block in reach afresh for each, every frame, which by a tall building of 1,745 blocks
     * was most of a millisecond and a half.
     */
    private final RoomSight.Openings openingsInReach = new RoomSight.Openings();
    private final RoomSight.Blocks blocksInReach = new RoomSight.Blocks();
    private long openingsReach;
    /**
     * The rooms whose contents builds hold, and whether builds go by them: they do while the
     * eye's own picture is drawn, and then a room no walk round the eye has found for a few
     * seconds is left out of the next build of its chunk. Otherwise, from elsewhere and as the game's own view, they go by
     * Sightline as before. Logic thread.
     */
    private final RoomSight.Kept roomsBuilt = new RoomSight.Kept();
    private final RoomSight.Seen roomsFound = new RoomSight.Seen(), roomsEntered = new RoomSight.Seen(), roomsLeft = new RoomSight.Seen();
    /** The rooms come in whose chunks are built again this frame, and those come in on a held storey that wait: see where they are filled. Logic thread. */
    private final RoomSight.Seen roomsTold = new RoomSight.Seen();
    private RoomSight.Seen roomsWaiting = new RoomSight.Seen(), roomsWaitingSpare = new RoomSight.Seen();
    /**
     * The rooms a walk round the eye finds with every shut door and drawn curtain in reach
     * counted open, and the picture's own, kept as roomsBuilt keeps its rooms. A door opened
     * further off than SHUT_WITHIN shows its room before that room's contents are built, and
     * the game gives a zombie its model a tick after it is asked for, so the zombies in it are
     * given theirs while it could come into sight: see roomsForModels. Logic thread.
     */
    private final RoomSight.Kept roomsMayShow = new RoomSight.Kept();
    private final RoomSight.Seen roomsMayShowFound = new RoomSight.Seen(), roomsMayShowChanged = new RoomSight.Seen();
    private final FigureRooms modelRooms = new FigureRooms();
    /**
     * The rooms the eye's picture saw into in the last half second, and who stands where the
     * picture leaves them out by them: see figuresLeftOut. Logic thread.
     */
    private final RoomSight.Kept roomsShown = new RoomSight.Kept(SHOWN_KEEP_NANOS);
    private final RoomSight.Seen roomsShownChanged = new RoomSight.Seen();
    private final FigureRooms figuresShown = new FigureRooms();
    private static final long SHOWN_KEEP_NANOS = 500_000_000L;
    /**
     * What the last walk for roomsMayShow was walked from: with every shut door in reach counted
     * open it reads no door, so from the same eye, reach, region and openings it finds the same rooms.
     */
    private float mayShowEyeX, mayShowEyeY, mayShowEyeUp, mayShowReach;
    private long mayShowRegion, mayShowBuilding;
    private int mayShowOpenings = -1, mayShowBlocks = -1;
    private boolean buildingBySight;
    /** Where the eye was at the last walk round it and when, and how fast it has been moving, tiles a second. */
    private float roundEyeX, roundEyeY, eyeSpeed;
    private long roundNanos;
    /** The walks round the eye being walked, a picture of each a frame, and whether roomsMayShow is to take what its walk finds once that is done. */
    private final RoomSight.Round builtRound = new RoomSight.Round(), mayShowRound = new RoomSight.Round();
    private boolean roomsMayShowDue;
    /**
     * How long the rooms a walk round the eye found stand for it, unless the eye has gone half
     * the way an opening is taken to reach past its edges: a door opened far off by someone
     * else has its room built in that time.
     */
    private static final long ROUND_EVERY_NANOS = 250_000_000L;
    /**
     * How often a development build holds the walk to RoomRays while the eye's own picture goes
     * by it, and how far and how finely the lines are followed: a room the lines reach and the
     * walk leaves out stands empty in the picture, and the log says so with what the walk
     * started from.
     */
    private static final long ROOMS_CHECKED_EVERY_NANOS = 500_000_000L;
    private static final int ROOM_LINES_ACROSS = 64, ROOM_LINES_UP = 36;
    private static final float ROOM_LINES_REACH = 40f;
    private final RoomSight.Seen roomsByLines = new RoomSight.Seen();
    private long roomsCheckedNanos, roomChecksNanos;
    private int roomChecks, roomsMissed;
    /** Dev only, for the rig: the time the walks took and how many there were, and what the last one had. */
    private long tollRoomsNanos, tollRoomsWalks;
    private int lastOpenings, lastBlocks, lastSeen, lastHidden;
    /**
     * A step's worth at a walk and what the eye covers in GROW_SECONDS, in tiles: once the eye has
     * gone half of it the walk round it is made again, so each walk finds what any eye within that
     * half could see (RoomSight.walkAllRound).
     */
    private static final float GROW_TILES = 1.5f, GROW_SECONDS = 0.4f;
    /** A shut door or a drawn curtain this near the eye, in tiles, counts as open when what is built is worked out: it may be opened before its room could be built. */
    private static final float SHUT_WITHIN = 10f;

    /** Whether builds hold what stands in a room: always, but while they go by sight, only in a room a walk round the eye has found lately. */
    boolean builtBySight(long room) {
        return !buildingBySight || roomsBuilt.contains(room);
    }

    /**
     * How many layers are laid over the land on the square (x, y) of the ground level, as its
     * chunk's build laid them: its floor, what spreads over it, blood, what was dropped there,
     * and over those, in the build of what grows, the litter. 0 where the chunk has no build,
     * where nothing is drawn either. Logic thread.
     */
    float layersOnTheLand(int x, int y) {
        int wx = Math.floorDiv(x, CHUNK_TILES), wy = Math.floorDiv(y, CHUNK_TILES);
        ChunkMeshes held = latest[(wx & (TABLE_SIDE - 1)) * TABLE_SIDE + (wy & (TABLE_SIDE - 1))];
        if (held == null || held.wx != wx || held.wy != wy) {
            return 0f;
        }
        return held.groundLayers[Math.floorMod(x, CHUNK_TILES) * CHUNK_TILES + Math.floorMod(y, CHUNK_TILES)] + 1f;
    }

    /** Dev only: the build a chunk has, shown or not, or null. */
    ChunkMeshes latestOf(int wx, int wy) {
        ChunkMeshes held = latest[(wx & (TABLE_SIDE - 1)) * TABLE_SIDE + (wy & (TABLE_SIDE - 1))];
        return held == null || held.wx != wx || held.wy != wy ? null : held;
    }
    /** Dev only: the game's marks are left unused when set, for a comparison within one run. */
    private volatile boolean withoutMarks;
    /**
     * Dev only, for the same: a chunk's crowns are made again with every build of it, they
     * are made on the logic thread, and a chunk is built again in every band it passes into.
     */
    private volatile boolean withoutKeptCrowns, withoutCrownThread, withoutAlikeBands;
    /**
     * Dev only, for the same: a room is filled only as the game shows it, wherever the game is
     * played, and no chunk is built before the view turns to it.
     */
    private volatile boolean withoutSightline;
    /** Dev only, for the same: the eye's picture draws every room its chunks were built with, whether it sees into it or not. */
    private volatile boolean withoutCulling;

    /** Dev only, for the rig: names what is turned off ("marks", "kept", "thread", "bands", "sightline", "culling", or several with commas), and everything else is on. */
    void without(String what) {
        List<String> off = Arrays.asList(what.split(","));
        withoutMarks = off.contains("marks");
        withoutKeptCrowns = off.contains("kept");
        withoutCrownThread = off.contains("thread");
        withoutAlikeBands = off.contains("bands");
        withoutSightline = off.contains("sightline");
        withoutCulling = off.contains("culling");
        boolean[] was = {TreeForm.withoutForms, Flora.fineOutOfShadow, Flora.nearTwiglets, Flora.piecesByDistance, Flora.twigsByDistance};
        TreeForm.withoutForms = off.contains("forms");
        Flora.fineOutOfShadow = !off.contains("fineshadow");
        Flora.nearTwiglets = !off.contains("twiglets");
        Flora.piecesByDistance = !off.contains("pieces");
        Flora.twigsByDistance = !off.contains("twigcount");
        if (!java.util.Arrays.equals(was, new boolean[] {TreeForm.withoutForms, Flora.fineOutOfShadow, Flora.nearTwiglets, Flora.piecesByDistance, Flora.twigsByDistance})) {
            forgetAll();
        }
    }

    /** Why the chunks are read through over and over to find what has changed, said once, or null while the game tells. */
    private static String polledBecause;
    private static boolean pollingSaid;

    /** Whether the game tells which chunks it changes: its code took the three patches, and it keeps a texture of each chunk, which is what it marks for. */
    private boolean told() {
        if (!pollingSaid) {
            pollingSaid = true;
            polledBecause = Agent.chunkMarksGap();
            Log.info(polledBecause == null ? "the game says which chunks it changes: chunks in view are read again only when it does"
                    : "the game does not say which chunks it changes (" + polledBecause + "): every chunk in view is read through over and over, as before");
            String windows = Agent.windowMarksGap();
            if (windows != null) {
                Log.info("a window smashed or opened is not told of (" + windows + "): it shows when its chunk is next read through");
            }
        }
        return polledBecause == null && PerformanceSettings.fboRenderChunk && !withoutMarks;
    }

    private static long sweptEvery(float distance, int slot) {
        long every = distance < CHUNK_TILES * 1.5f ? SWEPT_NEAR_NANOS : distance < BUILT_EVERY_FRAME ? SWEPT_NANOS : distance < BUILT_OFTEN ? SWEPT_OFTEN_NANOS : SWEPT_FAR_NANOS;
        // Spread by the chunk's place, or all that were built together are read together ever after.
        return every + every * (slot & 7) / 8;
    }

    /** Which chunks the map has loaded, as one number. */
    private static int loadedOf(IsoChunkMap map, int side) {
        int sum = 17;
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < side; y++) {
                IsoChunk chunk = map.getChunk(x, y);
                sum = sum * 31 + (chunk != null && chunk.loaded ? chunk.wx * 4099 + chunk.wy : 7);
            }
        }
        return sum;
    }
    /** Dev only: every build is made a second time with nothing taken from what is kept, and the two are compared float for float. */
    private static final boolean CHECK_BUILDS = Boolean.getBoolean("cameraunlock.zomboid.checkBuilds");
    /**
     * A far chunk is not built again for heights that moved by less than half a dot of a picture
     * this many dots high, at the field of view it is drawn with: see heightsHeld.
     */
    private static final float HELD_ROWS = 2160f;
    /** Dev only, for the rig: what building has come to since tolls was last asked. */
    private final int[] tollBuilt = new int[7];
    private final long[] tollBuiltNanos = new long[7];
    private long tollMovedBand, tollMovedUnsigned, tollRebanded, tollHeightsHeld;
    private long tollCrownsWaitNanos, tollCrownsLongestNanos;
    private long tollCrownsKept, tollCrownsHere, tollCrownsHereNanos, tollCrownsThere, tollCrownsUnwanted;
    private long tollFrames, tollSums, tollSumNanos, tollBuildNanos, tollGrown, tollGrownNanos, tollChecked, tollDiffered;
    private long tollWoods, tollWoodsNanos;
    private long tollSumsByLevel, tollLevelsRead, tollLevelsKept, tollLevelsHeld, tollSumsChecked, tollSumsDiffered, tollSumsLate;

    /** Dev only: the builds, the sums and the time they took since the last asking, for a rig run's window. */
    String tolls() {
        String told = String.format(Locale.ROOT, "frames %d sums %d in %.1f ms, builds %d in %.1f ms (new %d moved %d changed %d aged %d unsigned %d unread %d), grown %d in %.1f ms, checked %d differed %d",
                tollFrames, tollSums, tollSumNanos / 1e6, tollBuilt[NEW] + tollBuilt[MOVED] + tollBuilt[CHANGED] + tollBuilt[AGED] + tollBuilt[UNSIGNED] + tollBuilt[LEFT_UNREAD],
                tollBuildNanos / 1e6, tollBuilt[NEW], tollBuilt[MOVED], tollBuilt[CHANGED], tollBuilt[AGED], tollBuilt[UNSIGNED], tollBuilt[LEFT_UNREAD],
                tollGrown, tollGrownNanos / 1e6, tollChecked, tollDiffered);
        told += String.format(Locale.ROOT, ", telling %b quiet %d, lights asked %d taken %d", ChunkMarks.live, tollQuiet, tollPulls, tollPulled);
        told += String.format(Locale.ROOT, ", trees read for sums %d in %.2f ms", tollWoods, tollWoodsNanos / 1e6);
        told += ", " + Terrain.tolls();
        tollWoods = tollWoodsNanos = 0;
        told += String.format(Locale.ROOT, ", built ahead of a turn %d in %.1f ms, light left on chunks not faced %d, changed put off %d", tollAhead, tollAheadNanos / 1e6, tollLitUnfaced,
                tollChangedWaited);
        tollLitUnfaced = 0;
        tollChangedWaited = 0;
        told += String.format(Locale.ROOT, ", rooms walked %d times in %.2f ms each (chunks read for openings %d; last: openings %d, blocks %d, rooms seen %d, built %d, openings hidden by blocks %d)",
                tollRoomsWalks, tollRoomsNanos / 1e6 / Math.max(1, tollRoomsWalks), roomMap.chunksRead, lastOpenings, lastBlocks, lastSeen, roomsBuilt.size(), lastHidden);
        told += String.format(Locale.ROOT, ", horizons built %d", roomWalk.horizonBuilds() - horizonsTold);
        horizonsTold = roomWalk.horizonBuilds();
        told += ", " + roomDaylight.tolls();
        tollRoomsWalks = tollRoomsNanos = 0;
        roomMap.chunksRead = 0;
        tollAhead = tollAheadNanos = 0;
        told += String.format(Locale.ROOT, ", sums by level %d (levels read %d, kept %d, held over the viewer %d, checked against whole sums %d, levels marked by that reading %d, levels otherwise with nothing marked %d)",
                tollSumsByLevel, tollLevelsRead, tollLevelsKept, tollLevelsHeld, tollSumsChecked, tollSumsLate, tollSumsDiffered);
        tollSumsByLevel = tollLevelsRead = tollLevelsKept = tollLevelsHeld = tollSumsChecked = tollSumsDiffered = tollSumsLate = 0;
        if (AUDIT_STALE) {
            told += String.format(Locale.ROOT, ", chunks left alone built beside %d and what grows on them %d, found otherwise and due to be read %d, found stale %d", tollStaleAsked, tollStaleGrownAsked,
                    tollStaleDue, tollStale);
            tollStaleAsked = tollStale = tollStaleDue = tollStaleGrownAsked = 0;
        }
        tollQuiet = tollPulls = tollPulled = 0;
        if (AUDIT_MARKS) {
            told += String.format(Locale.ROOT, ", sums changed with a mark %d without %d by a late light %d, marked and the same %d, restless %d, lights taken after the game %d, aged builds the same %d (plain %d) otherwise %d, marks by flag%s",
                    tollMarkedChanges, tollMissedChanges, tollMarkedByReading, tollMarkedSame, tollRestless, tollPulledAfterGame, tollAgedSame, tollAgedSamePlain, tollAgedDiffered, ChunkMarks.counted())
                    + " | sums read because (marked, told, epoch, heights, map, restless, new, swept, unsigned, moved) " + Arrays.toString(tollWhy);
            Arrays.fill(tollWhy, 0);
            tollMarkedChanges = tollMissedChanges = tollMarkedSame = tollRestless = tollAgedSame = tollAgedSamePlain = tollAgedDiffered = tollMarkedByReading = tollPulledAfterGame = 0;
        }
        told += " | ms by cause " + Arrays.toString(Arrays.stream(tollBuiltNanos).map(each -> each / 1_000_000L).toArray()) + " moved for the band " + tollMovedBand + " unsigned " + tollMovedUnsigned
                + " kept across a band " + tollRebanded + " kept with heights moved too little to show " + tollHeightsHeld;
        told += String.format(Locale.ROOT, " | crowns kept %d, made here %d in %.1f ms, on their thread %d, unwanted %d, back after %.1f ms each and at most %.1f", tollCrownsKept, tollCrownsHere,
                tollCrownsHereNanos / 1e6, tollCrownsThere, tollCrownsUnwanted, tollCrownsWaitNanos / 1e6 / Math.max(1, tollCrownsThere), tollCrownsLongestNanos / 1e6);
        tollCrownsKept = tollCrownsHere = tollCrownsHereNanos = tollCrownsThere = tollCrownsUnwanted = tollCrownsWaitNanos = tollCrownsLongestNanos = 0;
        Arrays.fill(tollBuiltNanos, 0);
        tollMovedBand = tollMovedUnsigned = tollRebanded = tollHeightsHeld = 0;
        Arrays.fill(tollBuilt, 0);
        tollFrames = tollSums = tollSumNanos = tollBuildNanos = tollGrown = tollGrownNanos = tollChecked = tollDiffered = 0;
        return told;
    }
    /**
     * Why each chunk built this frame was: it had no build, the eye had moved, something on it
     * had changed, it had been kept as long as one is, its build was made while the eye moved and
     * so was not signed, or it had changed a moment ago and was not read through. KEPT_ON counts
     * those found unchanged, which are not built.
     */
    private static final int NEW = 0, MOVED = 1, CHANGED = 2, AGED = 3, UNSIGNED = 4, LEFT_UNREAD = 5, KEPT_ON = 6;
    /**
     * Set round a build nobody will see, made so that the first build somebody does see is not
     * the first there ever was: then nothing is built whatever it takes. See FirstPerson.rehearse.
     */
    boolean rehearsing;
    /** How many chunks the build before this one left waiting. */
    private int waitedLast;
    /** How far the eye may be, in tiles, from where what grows on a chunk right by it was made from and still count as there. */
    private static final float EYE_HELD_WITHIN = 0.006f;
    /** The tangent of how far round a chunk the eye may have gone since its build, seen from the chunk's nearest point: two degrees. */
    private static final float HELD_ROUND = 0.035f;

    /** How far the eye may be from where what grows on a chunk was made from, in tiles, and that build still do. */
    private static float heldWithin(ViewCamera.Pose view, float distance, float middleX, float middleY) {
        return Math.max(EYE_HELD_WITHIN * Math.max(1f, distance / (CHUNK_TILES * 1.5f)), nearest(view, middleX, middleY) * HELD_ROUND);
    }

    private static boolean stepped(float[] light, float red, float green, float blue) {
        return Math.abs(light[0] - red) > OPEN_LIGHT_STEP || Math.abs(light[1] - green) > OPEN_LIGHT_STEP || Math.abs(light[2] - blue) > OPEN_LIGHT_STEP;
    }

    /** What a frame of a chunk's interval stands for in time: see where a chunk is found due. */
    private static final long NANOS_A_FRAME_COUNTED = 8_000_000L, UNCHANGED_READ_EVERY_NANOS = 33_000_000L;
    /**
     * How long a frame builds chunks for before those further off than {@link #BUILT_WHATEVER_IT_TAKES}
     * wait for the next. It is the frame's time, and so is the time for chunks whose only news is
     * their light: two players' views building in one frame have half of each.
     */
    private static final long BUILD_NANOS_A_FRAME = 5_000_000L;
    /**
     * How long a frame reads and builds chunks for before one whose only news is its light
     * waits for the next, and how long such a chunk waits at most. The game's light for a
     * square changes with where the character stands, on every level of the map at once, so a
     * step indoors marks every chunk round the character together, and a chunk of a tall
     * building is thirty levels to read and to build: all made on the frame of the step they
     * were a frame of 40 to 50 ms at every step, on the tenth floor of a building of 27.
     */
    private static final long LIT_NANOS_A_FRAME = 2_000_000L, LIT_WAITS_NANOS = 150_000_000L;
    /**
     * How long the lights of what the game has given no light take to come most of the way
     * to a new reading, in seconds: the readings are taken twice a second, and a room taken
     * from one to the next at once stepped brighter or darker.
     */
    private static final float UNLIT_EASE_SECONDS = 0.5f;
    /** How long a chunk found changed waits at most for a frame with time to build it: see where one is put off. */
    private static final long CHANGED_WAITS_NANOS = 500_000_000L;
    /** Within this many tiles of the eye a chunk with no build is built on the frame it is wanted. */
    private static final float BUILT_WHATEVER_IT_TAKES = CHUNK_TILES * 2.5f;
    /** Where the opening in each wall sprite seen so far is: see openingOf. */
    static final IdentityHashMap<Texture, float[]> openings = new IdentityHashMap<>();

    /**
     * Logic thread, as the picture stops being built. A scene names the map it was last built
     * from, and with the view off it would go on naming it through another save's loading:
     * the whole of a map the game had let go of.
     */
    void setDown() {
        for (Frame scene : scenes) {
            scene.cell = null;
            scene.here = null;
            Arrays.fill(scene.groundObject, null);
            Arrays.fill(scene.lightSquare, null);
            Arrays.fill(scene.around, null);
            scene.lampsNear.clear();
            scene.lampsBurning.clear();
            scene.lampsSortedFrame = -1;
            scene.roomAsked = null;
            scene.keptFrame = -1;
        }
        squareLights.forget();
        roomDaylight.forget();
        chunkMap = null;
        builtAt[view.index] = 0L;
        roomMap.forget();
        buildingBySight = false;
        roomsBuilt.clear();
        roomsMayShow.clear();
        roomsMayShowFound.clear();
        mayShowOpenings = -1;
        roundNanos = 0L;
        builtRound.drop();
        mayShowRound.drop();
        roomsMayShowDue = false;
        eyeSpeed = 0f;
        if (building(System.nanoTime()) == 0) {
            // Nothing is built or read while every view is off, so nothing would come to let these go.
            VertexLoans.letGoIdle();
            SpriteColours.letGoOfThePage();
        }
    }

    /**
     * Render thread, as the picture is set down, after the frames queued before it are drawn:
     * a frame queued as its player died is drawn after the logic thread has gone on to set the
     * picture down, and draws for the player it was built for.
     */
    void letGoOfViewers() {
        for (Frame scene : scenes) {
            scene.viewer = null;
        }
    }

    Frame next() {
        nextScene = (nextScene + 1) % scenes.length;
        return scenes[nextScene];
    }

    float drawnAspect() {
        return drawnAspect;
    }

    /** Takes in the crowns a piece of work was to make: they stand for their chunk from now, unless the chunk has since been let go of or built otherwise. */
    private void land(Crowns.Work back) {
        crownsOut--;
        long out = System.nanoTime() - back.begun;
        tollCrownsWaitNanos += out;
        tollCrownsLongestNanos = Math.max(tollCrownsLongestNanos, out);
        back.into.crowns = back.from;
        if (back.unwanted || back.failed != null) {
            tollCrownsUnwanted += back.unwanted ? 1 : 0;
            if (crownsWaited[back.slot] == back) {
                crownsWaited[back.slot] = null;
            }
            release(back.into);
            // Thrown only now that the work is counted back in and its build given back: whoever lets go of every build waits for all that is out.
            if (back.failed != null) {
                throw new IllegalStateException("the crowns of the chunk at " + back.into.wx + ", " + back.into.wy + " could not be made", back.failed);
            }
            return;
        }
        if (CHECK_BUILDS) {
            solid.clear();
            back.from.mesh(solid, PLAIN, crownFrame);
            tollChecked++;
            if (floatsOf(solid) != back.sum) {
                tollDiffered++;
                Log.info("build check: the crowns of the chunk at " + back.into.wx + ", " + back.into.wy + " came out otherwise on their own thread");
            }
            solid.clear();
        }
        crownsWaited[back.slot] = null;
        ChunkMeshes standing = latestCrowned[back.slot];
        if (standing != null) {
            release(standing);
        }
        latestCrowned[back.slot] = back.into;
    }

    /** Lets go of the crowns a chunk has standing and of those out being made for it. */
    private void forgetCrowns(int slot) {
        if (latestCrowned[slot] != null) {
            release(latestCrowned[slot]);
            latestCrowned[slot] = null;
        }
        if (crownsWaited[slot] != null) {
            crownsWaited[slot].unwanted = true;
            crownsWaited[slot] = null;
        }
    }

    /**
     * Dev only: every so many frames a chunk that is left alone, because nothing has been said
     * of it, is built again beside its standing build, and a line says so when the two differ
     * in any float: something a build reads became otherwise with no mark, no telling and no
     * change of the chunk's sum, and shows stale until the chunk is next built for another
     * reason. The line says what of the chunk does differ now, to find what it was. A chunk
     * whose sum has changed, or that the build beside got told, is only counted: it is due
     * to be read and built as it is. The crowns the build beside writes down are set against
     * the chunk's own, and what grows on its ground is built beside as well where it stands
     * made from where the eye still is: with a head that moves that is seldom, so a run that
     * is to cover it holds the head still. The standing build is left as it was.
     */
    private static final boolean AUDIT_STALE = Boolean.getBoolean("cameraunlock.zomboid.auditStale");
    private static final int AUDIT_STALE_EVERY = 240;
    private long tollStaleAsked, tollStale, tollStaleDue, tollStaleGrownAsked;

    /** Dev only: see ChunkMeshes.auditMeshes. */
    private long[] meshesOf() {
        long[] of = new long[(solid.used.size() + laid.used.size()) * 3];
        int at = 0;
        for (int set = 0; set < 2; set++) {
            SpriteGeometry.MeshSet from = set == 0 ? solid : laid;
            for (int i = 0; i < from.used.size(); i++) {
                SpriteGeometry.Mesh mesh = from.used.get(i);
                long sum = SUM_FROM;
                for (int k = 0; k < mesh.floats; k++) {
                    sum = sum(sum, Float.floatToRawIntBits(mesh.vertices[k]));
                }
                // A rug's key is below zero: kept to 30 bits, it neither sign-extends over the set nor reaches it.
                of[at++] = (long) set << 62 | (mesh.texture & 0x3fffffffL) << 32 | mesh.tint & 0xffffffffL;
                of[at++] = mesh.floats;
                of[at++] = sum;
            }
        }
        return of;
    }

    /** Dev only: a mesh that differs, and for one of the build beside, what it is made of and where it lies. */
    private String describe(long key, long before, long after) {
        int set = (int) (key >>> 62), texture = (int) (key >>> 32 & 0x3fffffff), tint = (int) key;
        String said = (set == 0 ? "solid" : "laid") + " texture " + texture + " tint " + Integer.toHexString(tint) + " floats " + before + " then " + after;
        SpriteGeometry.Mesh mesh = after == 0 ? null : (set == 0 ? solid : laid).get(texture, tint);
        if (mesh == null) {
            return said;
        }
        TreeSet<Integer> made = new TreeSet<>();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minUp = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxUp = -Float.MAX_VALUE;
        for (int k = 0; k < mesh.floats; k += SpriteGeometry.VERTEX_FLOATS) {
            made.add((int) Math.floor((mesh.vertices[k + 8] % LIT_STEP + 0.5f) / 32f));
            minX = Math.min(minX, mesh.vertices[k]);
            maxX = Math.max(maxX, mesh.vertices[k]);
            minY = Math.min(minY, mesh.vertices[k + 1]);
            maxY = Math.max(maxY, mesh.vertices[k + 1]);
            minUp = Math.min(minUp, mesh.vertices[k + 2]);
            maxUp = Math.max(maxUp, mesh.vertices[k + 2]);
        }
        return said + String.format(Locale.ROOT, ", made of %s, over x %.2f to %.2f, y %.2f to %.2f, up %.2f to %.2f", made, minX, maxX, minY, maxY, minUp, maxUp);
    }

    /** Dev only: every vertex of a set as one number, whatever order its meshes and their vertices are in. */
    private static long looseOf(SpriteGeometry.MeshSet set) {
        long all = 0;
        for (int i = 0; i < set.used.size(); i++) {
            SpriteGeometry.Mesh mesh = set.used.get(i);
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                long vertex = sum(sum(SUM_FROM, mesh.texture), mesh.tint);
                for (int of = 0; of < SpriteGeometry.VERTEX_FLOATS; of++) {
                    vertex = sum(vertex, Float.floatToRawIntBits(mesh.vertices[at + of]));
                }
                all += vertex;
            }
        }
        return all;
    }

    static long floatsOf(SpriteGeometry.MeshSet set) {
        long sum = set.used.size();
        for (int i = 0; i < set.used.size(); i++) {
            SpriteGeometry.Mesh mesh = set.used.get(i);
            sum = sum(sum(sum, mesh.tint), mesh.floats);
            for (int at = 0; at < mesh.floats; at++) {
                sum = sum(sum, Float.floatToRawIntBits(mesh.vertices[at]));
            }
        }
        return sum;
    }

    /** The height, in tiles, of a house of two floors with its roof, or of a tree: what stands on any chunk may be this tall, whatever its floors. */
    private static final float TALL_AS_A_HOUSE = 7f;

    /** Dev only: a build takes this many times as long as it did, to see on a fast PC what a slow one does with the frame's time for building. */
    private static final int SLOW_BUILDS = Integer.getInteger("cameraunlock.zomboid.slowBuilds", 1);

    private static void slowed(long began) {
        if (SLOW_BUILDS > 1) {
            long until = System.nanoTime() + (System.nanoTime() - began) * (SLOW_BUILDS - 1);
            while (System.nanoTime() < until) {
                Thread.onSpinWait();
            }
        }
    }

    private void release(ChunkMeshes meshes) {
        if (--meshes.users != 0) {
            return;
        }
        ChunkBuffers.forget(meshes);
        if (meshes.crowns != null) {
            spareCrowns.push(meshes.crowns);
            meshes.crowns = null;
        }
        if (spare.size() < MOST_SPARE) {
            spare.push(meshes);
        } else {
            meshes.scrap();
        }
    }

    /** How far beyond what is in sight a build is kept, in tiles: a walk or a drive back brings it into the picture again within seconds. */
    static final float KEPT_BEYOND = 8f;
    /** Where the eye was when builds were last let go by distance. */
    private float keptRoundX, keptRoundY;

    /**
     * Whether a build is worth keeping: of a chunk the loaded map holds, or of one a step
     * outside it, which the next step back brings in again, and no further from the eye than
     * the graphics mode shows and a little more. The map is far wider than Low sees, and a
     * build kept is a block of the graphics card's memory kept.
     */
    private boolean near(ChunkMeshes meshes) {
        return ChunkBuffers.kept(meshes.wx, meshes.wy, mapWx, mapWy, mapSide, keptRoundX, keptRoundY, Quality.sight(view.index));
    }

    /** How far the eye may be, in tiles, from where what grows on a chunk was made from before it is made again. */
    private static final float GROWN_HELD_WITHIN = 0.5f;
    /** How near a chunk's middle is to the eye, in tiles, for anything that grows on its ground to be made by how far off it is: the furthest of the rings and a chunk's reach. */
    private static final float GROWN_WITHIN = Math.max(Math.max(FERN_REACH, GRASS_REACH), Math.max(FALLEN_REACH, FLOWER_REACH)) + CHUNK_REACH;

    /**
     * Where each band of distance from the eye ends, in tiles from the eye to the nearest edge
     * of a chunk, and the distance everything in a chunk of the band is made for. A tree's
     * limbs and its crown's fineness, a bole, a bush's cards, a rock's faces and long grass
     * grown plant by plant all go by distance: taken tree by tree from the eye itself, every
     * chunk with a plant in it was a different build at every step. By band, a chunk is the
     * same build until it crosses into the next, about two chunks a frame at a car's speed.
     * The further out a band ends the more chunks cross its end, so the last ends where a crown
     * stops being made below the High mode, and a bole is made only as far as that. In High the
     * last band has both: see crownedToSight.
     */
    private static final float[] BAND_ENDS = {4f, 9f, 16f, 24f, 40f};
    static final float[] BAND_AWAY = {2f, 6f, 12f, 20f, 30f, 70f};
    /** How far past its band's ends a chunk keeps the build made for the band, so that one on the line is not made over and over. */
    private static final float BAND_SLACK = 0.5f;

    private static int band(float nearest) {
        int band = 0;
        while (band < BAND_ENDS.length && nearest >= BAND_ENDS[band]) {
            band++;
        }
        return band;
    }

    private static boolean inBand(int band, float nearest) {
        return (band == 0 || nearest >= BAND_ENDS[band - 1] - BAND_SLACK) && (band == BAND_ENDS.length || nearest < BAND_ENDS[band] + BAND_SLACK);
    }

    /** How far the eye is from the nearest point of the chunk whose middle is given, in tiles along the ground. */
    private static float nearest(ViewCamera.Pose view, float middleX, float middleY) {
        float clearX = Math.max(Math.abs(view.eyeX - middleX) - CHUNK_TILES / 2f, 0f), clearY = Math.max(Math.abs(view.eyeY - middleY) - CHUNK_TILES / 2f, 0f);
        return (float) Math.sqrt(clearX * clearX + clearY * clearY);
    }

    private static final int ALL_BANDS = (1 << BAND_AWAY.length) - 1;

    /**
     * Logic thread, once first person has been off for a while: lets go of every build of this
     * view, the ones the last frames showed among them, so that the graphics card's copy of
     * them can be given back (ChunkBuffers.letGo). The next switch to first person builds it
     * again. What every view builds with is let go once no view's world is being built.
     */
    void letGo() {
        for (Frame scene : scenes) {
            for (int i = 0; i < scene.shown.size(); i++) {
                release(scene.shown.get(i));
            }
            scene.shown.clear();
            for (int i = 0; i < scene.builtAheadOf.size(); i++) {
                release(scene.builtAheadOf.get(i));
            }
            scene.builtAheadOf.clear();
        }
        forgetAll();
        forgetTheOpened();
        // What is out being made comes back first, wanted by nobody now: a build left with the other thread would keep its vertices until the view next came on.
        while (crownsOut > 0) {
            land(Crowns.awaited(crownsBack));
        }
        farLand.forget();
        while (!spare.isEmpty()) {
            spare.pop().scrap();
        }
        setDown();
        if (building(System.nanoTime()) == 0) {
            spareCrowns.clear();
            Crowns.letGo();
            FarLand.forgetTheMap();
            solid.clear();
            laid.clear();
            solid.trim();
            laid.trim();
        }
    }

    /** Lets go of the builds of every chunk that has a square in the box of squares from (minX, minY) up to but not (maxX, maxY), or right beside it. */
    private void forgetOver(int minX, int minY, int maxX, int maxY) {
        for (int slot = 0; slot < latest.length; slot++) {
            ChunkMeshes held = latest[slot] != null ? latest[slot] : latestGrown[slot] != null ? latestGrown[slot]
                    : latestCrowned[slot] != null ? latestCrowned[slot] : crownsWaited[slot] != null ? crownsWaited[slot].into : null;
            if (held == null || !ChunkBuffers.chunkTouches(held.wx, held.wy, minX, minY, maxX, maxY)) {
                continue;
            }
            forget(slot);
        }
    }

    /**
     * The lie of the land this view's world is built and drawn on: the hills, or the country
     * left level while the view is the game's own from overhead. Set by the logic thread.
     */
    volatile Terrain terrain = Terrain.HILLS;
    /** The leaves falling round this view's eye. */
    private final LeafTrees leafTrees = new LeafTrees(this);
    /** The height of the hills the far land was last asked for at: see HillHeight. Logic thread. */
    private float farHills = HillHeight.DEFAULT;

    /**
     * Logic thread, while first person is being got ready and nothing is built from the eye:
     * asks for the far land round it, at the height the hills are drawn, as a build does. The
     * hills stand at 1 until a build first raises them, and the far land would be made at that.
     */
    void tendFar(float eyeX, float eyeY) {
        float hills = HillHeight.drawn(Online.contested());
        if (hills != Terrain.HILLS.gain()) {
            Terrain.HILLS.heighten(hills);
        }
        if (hills != farHills) {
            farHills = hills;
            farLand.forget();
        }
        farLand.tend(eyeX, eyeY);
    }

    /** Logic thread: has the view's country level or not, and lets go of every build made on it the other way. */
    void level(boolean flat) {
        if (terrain.level != flat) {
            terrain = Terrain.of(flat);
            forgetAll();
        }
    }

    /** After every build has been let go of: no building of the map they were made on is held as one the game had open. */
    private void forgetTheOpened() {
        openBuildings.clear();
        openBuildingsNow.clear();
        topLevel = Integer.MAX_VALUE;
    }

    /** Lets go of the builds held in a slot of the table. */
    private void forget(int slot) {
        if (latest[slot] != null) {
            release(latest[slot]);
            latest[slot] = null;
        }
        if (latestGrown[slot] != null) {
            release(latestGrown[slot]);
            latestGrown[slot] = null;
        }
        forgetCrowns(slot);
    }

    /** Lets go of every build: for when what all of them were built on has changed. */
    void forgetAll() {
        for (int slot = 0; slot < latest.length; slot++) {
            forget(slot);
        }
    }

    /**
     * Lets go of every build of a chunk the player has left behind, and of the room the sets
     * have that nothing since the last time used. The loans keep their own watch: see VertexLoans.trim. The table is far wider than
     * the loaded map so that no two loaded chunks share a place in it, and a build stayed in
     * its place until another chunk took it: driving across the map, every chunk ever passed
     * was kept, grass and all, until there was no memory left. A build kept is a block of the
     * graphics card's memory kept, so none is kept for a chunk the map no longer holds.
     */
    private void forgetFar() {
        for (int slot = 0; slot < latest.length; slot++) {
            ChunkMeshes held = latest[slot];
            if (held != null && !near(held)) {
                latest[slot] = null;
                release(held);
            }
            ChunkMeshes grown = latestGrown[slot];
            if (grown != null && !near(grown)) {
                latestGrown[slot] = null;
                release(grown);
            }
            if (latestCrowned[slot] != null && !near(latestCrowned[slot]) || crownsWaited[slot] != null && !near(crownsWaited[slot].into)) {
                forgetCrowns(slot);
            }
        }
        squareLights.forgetOutside(mapWx, mapWy, mapSide);
        roomDaylight.forgetOutside(mapWx, mapWy, mapSide);
        solid.clear();
        laid.clear();
        solid.trim();
        laid.trim();
        VertexLoans.trim(System.nanoTime());
    }

    private static final HashSet<String> movers = new HashSet<>();

    /** Whether a sprite has more than one frame, and so may show another the next time it is asked. */
    private static boolean moves(IsoSprite sprite) {
        boolean moves = sprite != null && sprite.getFrameCount() > 1;
        if (AUDIT_MARKS && moves && movers.size() < 60 && movers.add(String.valueOf(sprite.getName()))) {
            Log.info("chunk marks: a sprite of " + sprite.getFrameCount() + " frames, " + sprite.getName());
        }
        return moves;
    }

    static final long SUM_FROM = 0x9E3779B97F4A7C15L;

    /** The picture the game has of a body and where it draws it, as one number: 0 while it has none. */
    private static long pictured(IsoDeadBody body) {
        DeadBodyAtlas.BodyTexture picture = body.getAtlasTexture();
        Texture texture = picture == null ? null : picture.getTexture();
        if (!usable(texture) || !texture.isReady()) {
            return 0;
        }
        return (long) System.identityHashCode(texture) << 32
                | (Float.floatToRawIntBits(picture.getRenderX(0f)) * 31 + Float.floatToRawIntBits(picture.getRenderY(0f))) & 0xffffffffL;
    }

    private static java.lang.reflect.Field bodyPicture;

    /**
     * Asks the game for the picture of a body that has none, as IsoDeadBody.render does when the
     * body first comes into the game's own view, and returns whether it was asked. That view
     * draws no body under the first person picture, so a body that came to lie while it was
     * covered was never given one, and was not drawn. Logic thread, while a frame is handed to
     * the render thread, where the game's own asking is.
     */
    private static boolean askPicture(IsoDeadBody body) {
        if (body.getAtlasTexture() != null || !ModelManager.instance.debugEnableModels || !ModelManager.instance.isCreated()) {
            return false;
        }
        try {
            if (bodyPicture == null) {
                bodyPicture = IsoDeadBody.class.getDeclaredField(Links.BODY_PICTURE.name());
                bodyPicture.setAccessible(true);
            }
            bodyPicture.set(body, DeadBodyAtlas.instance.getBodyTexture(body));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("the picture of a body could not be asked for", failure);
        }
        DeadBodyAtlas.instance.render();
        return true;
    }

    /** A sprite and the texture it shows this frame turned that way, as one number. */
    private static long shown(IsoSprite sprite, IsoDirections dir) {
        if (sprite == null) {
            return 0;
        }
        Texture texture = sprite.getTextureForCurrentFrame(dir);
        return (long) System.identityHashCode(sprite) << 32 | (texture == null ? 0 : texture.getID() & 0xffffffffL);
    }

    static long sum(long sum, long value) {
        return (sum ^ value) * 0x100000001B3L;
    }

    /**
     * How many frames a chunk's build is kept before it is made again. The chunk's place in
     * the table is mixed in, or every far chunk built on the frame the view came on would fall
     * due together ever after.
     */
    private static int buildEvery(float distance, int slot) {
        if (distance < BUILT_EVERY_FRAME) {
            // The chunk the eye is in and those round it every frame, the rest of the near
            // ones every second or third.
            return distance < CHUNK_TILES * 1.5f ? 1 : 2 + slot % 2;
        }
        return distance < BUILT_OFTEN ? 6 + slot % 4 : 18 + slot % 9;
    }

    /**
     * For development: each sheet of the game's sprites whose own picture the builds have put on
     * triangles, with how many objects and how many vertices. A sprite the picture has built
     * of its own geometry, or drawn as a model, is not in it. The test rig's probe reads it.
     */
    final ConcurrentHashMap<String, int[]> stretched = new ConcurrentHashMap<>();

    private void tally(IsoSprite sprite, int floats) {
        String name = sprite.getName();
        if (floats <= 0 || name == null) {
            return;
        }
        int[] sum = stretched.computeIfAbsent(name, key -> new int[2]);
        sum[0]++;
        sum[1] += floats / SpriteGeometry.VERTEX_FLOATS;
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
        frame.takeTexels(texture);
    }

    static Texture textureOf(IsoObject floor) {
        IsoSprite sprite = floor == null ? null : floor.getSprite();
        Texture texture = sprite == null ? null : sprite.getTextureForCurrentFrame(floor.getDir());
        return usable(texture) ? texture : null;
    }

    /** Whether a texture has an OpenGL name and a size. */
    static boolean usable(Texture texture) {
        return texture != null && texture.getID() > 0 && texture.getWidth() > 0 && texture.getHeight() > 0;
    }

    static final float SHINGLES = 1f, PAINT = 2f, WOOD = 3f, EARTH = 4f, LEAVES = 5f, BARK = 6f, PLANKS = 7f, CARPET = 8f, CONCRETE = 9f, QUAY = 10f, TILE = 11f, WIRE = 12f, GLASS = 13f, LAMP = 14f, MESH = 15f;
    /** A pane the picture has built, one with a lamp burning in the room behind it, and one with a room on both sides (OpeningBuilder.paneMaterial), which is also the glass of every glass case (Furniture.Glass). */
    static final float PANE = 67f, LIT_PANE = 68f, INNER_PANE = 71f;
    /** A wall of upright boards. */
    static final float BOARDS = 52f;
    /** The game's blood on a floor or a wall: its texels take the colour its vertices carry where a tree's carry its square, and it covers as much as they say. See BloodBuilder. */
    static final float BLOOD = 85f;
    /** The picture of a body lying on the ground: it fell on the snow, and settled snow does not cover it. */
    static final float BODY = 89f;
    /**
     * A sheet hung flat on a wall, a poster, a notice, a picture, a board: its light and dark
     * are what is printed on it and say nothing of its shape. See printed.
     */
    static final float PRINT = 90f;
    /** The coloured glass of a stained glass window, lit by the day behind it: see StainedGlass. */
    static final float STAINED = 95f;
    /** What the game calls the sheets and boards it hangs flat on a wall. */
    private static final Set<String> PRINTS = Set.of("Poster", "Torn Poster", "Notices", "Picture", "Painting", "Paintings", "Certificate",
            "Child's Drawing", "Childrens Drawing", "Board", "Whiteboard", "Noteboard", "Pegboard", "USA");

    /**
     * Whether a sprite is a sheet or a board the game hangs flat on a wall. The map's film
     * posters carry no name, and are known by the sheet they are on. What gives light is a
     * lamp, whose glow goes by its own stuff.
     */
    static boolean printed(PropertyContainer properties, String name) {
        if (!"WallObject".equals(properties.get("MoveType")) || properties.has("HasLightOnSprite") || properties.get("LightRadius") != null) {
            return false;
        }
        String called = properties.get("CustomName");
        return called != null ? PRINTS.contains(called) : name != null && name.startsWith("walls_decoration");
    }

    /** What a sprite that is no floor is made of: see materialOf. A wall the game says is of wood, on a sheet made for one place, is boarded. */
    static float materialOf(IsoSprite sprite) {
        float material = materialOf(sprite.getName(), false);
        // A street lamp's bare post is on the sheet of lamps and lit as strongly as the lamp over
        // it: its pale paint glowed from foot to head.
        if (material == LAMP && sprite.getType() != IsoObjectType.lightswitch && Knockdown.streetLamp(sprite.getName(), sprite.getProperties().get("CustomName"))) {
            return 0f;
        }
        if (material != 0f || sprite.getName() == null || !sprite.getName().startsWith("location_")) {
            return material;
        }
        PropertyContainer properties = sprite.getProperties();
        boolean wooden = "Wood".equals(properties.get("MaterialType")) || "Wood".equals(properties.get("Material"));
        return wooden && isWall(properties, sprite.getType()) ? BOARDS : 0f;
    }

    /** The sheet of floors for inside a house: its tiles and lino first, then from FIRST_WOOD its boards and parquet. */
    private static final String TILES_AND_WOOD = "floors_interior_tilesandwood_01_";
    private static final int FIRST_WOOD = 40;

    /** What a sprite's surface is made of, by the sheet it comes from. */
    static float materialOf(String name, boolean isFloor) {
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
        // Another mod's sheet may take the name with something other than a number after it: that is no sprite of this sheet.
        if (isFloor && name.startsWith(TILES_AND_WOOD) && name.length() > TILES_AND_WOOD.length() && name.length() - TILES_AND_WOOD.length() <= 9
                && name.chars().skip(TILES_AND_WOOD.length()).allMatch(Character::isDigit)) {
            return Integer.parseInt(name, TILES_AND_WOOD.length(), name.length(), 10) < FIRST_WOOD ? TILE : WOOD;
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
    static float under(int z, float high) {
        return (z + high - Math.min(UNDER_ROOF, Math.max(high, 0f) * 0.5f)) * ViewCamera.LEVEL_HEIGHT;
    }

    /** How far above the floor a body's picture is laid, in tiles: clear of the blood and the litter laid on the floor under it. */
    private static final float BODY_UP = 0.08f;
    private static final int[] BODY_CORNERS = {0, 1, 2, 0, 2, 3};
    /** How many cells a body's picture is laid in each way. */
    private static final int BODY_CELLS = 8;
    /** How many layers the bodies and pieces of a chunk are laid in turn, one over the next, so that two lying across each other do not fight for depth. */
    private static final int BODY_LAYERS = 8;

    /**
     * Whether everything built of an object that is no part of its building stays inside the
     * room it stands in, so that it can be left out of a picture that does not see into the
     * room. What hangs on a wall may hang on its outer face, and a curtain or a barricade on a
     * window is seen through the glass from outside however the room is shut off.
     */
    static boolean withinItsRoom(IsoObject object, IsoSprite sprite) {
        return withinItsRoom(object, ofTheBuilding(sprite), keptInItsRoom(sprite));
    }

    /** The same, given what of it goes by the sprite alone: see Kind. */
    static boolean withinItsRoom(IsoObject object, boolean ofTheBuilding, boolean keptInItsRoom) {
        return !partOfTheBuilding(object, ofTheBuilding) && !(object instanceof IsoCurtain) && !(object instanceof IsoBarricade) && keptInItsRoom;
    }

    /** What of withinItsRoom goes by the sprite alone: what hangs on a wall, a curtain and an overlay on a wall are not kept in. */
    private static boolean keptInItsRoom(IsoSprite sprite) {
        IsoObjectType type = sprite.getType();
        if (type == IsoObjectType.curtainN || type == IsoObjectType.curtainS || type == IsoObjectType.curtainE || type == IsoObjectType.curtainW) {
            return false;
        }
        PropertyContainer flags = sprite.getProperties();
        return !flags.has(IsoFlagType.attachedN) && !flags.has(IsoFlagType.attachedW) && !flags.has(IsoFlagType.attachedE) && !flags.has(IsoFlagType.attachedS)
                && !flags.has(IsoFlagType.WallOverlay) && !flags.has("attachedNW") && !flags.has("attachedSE");
    }

    /** Whether an object is the house itself and not something in it: a wall, a door, a window, stairs, a roof. */
    static boolean partOfTheBuilding(IsoObject object, IsoSprite sprite) {
        return partOfTheBuilding(object, ofTheBuilding(sprite));
    }

    /** The same, given what of it goes by the sprite alone: see Kind. */
    static boolean partOfTheBuilding(IsoObject object, boolean ofTheBuilding) {
        return ofTheBuilding || object instanceof IsoDoor || object instanceof IsoWindow || object instanceof IsoThumpable thumpable && thumpable.isDoor();
    }

    /** What of partOfTheBuilding goes by the sprite alone. */
    private static boolean ofTheBuilding(IsoSprite sprite) {
        IsoObjectType type = sprite.getType();
        if (Roofs.isTile(type) || isStair(type)) {
            return true;
        }
        PropertyContainer flags = sprite.getProperties();
        return flags.has(IsoFlagType.WallN) || flags.has(IsoFlagType.WallW) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.WallSE)
                || flags.has(IsoFlagType.WallNTrans) || flags.has(IsoFlagType.WallWTrans) || flags.has(IsoFlagType.DoorWallN)
                || flags.has(IsoFlagType.DoorWallW) || flags.has(IsoFlagType.WindowN) || flags.has(IsoFlagType.WindowW)
                || flags.has(IsoFlagType.solidfloor) || flags.has(IsoFlagType.FloorOverlay) || FloorMarks.onFloor(sprite.getName(), flags);
    }

    /** Where each plant's leaves are painted on its texture: see Flora.leafPlaces. */
    static final IdentityHashMap<Texture, float[]> leafPlaces = new IdentityHashMap<>();
    /** Where on each crop's sprite its stalks take their colour from: see Flora.inside. */
    static final IdentityHashMap<Texture, float[]> cropColours = new IdentityHashMap<>();

    /** Where each sprite the game gives no placing to has its foot: see SpriteShape.foot. Empty for a sprite with nothing painted. */
    static final IdentityHashMap<IsoSprite, float[]> feet = new IdentityHashMap<>();

    /** What each sprite has been read as by Posts: NO_POST for one that is none. */
    static final IdentityHashMap<IsoSprite, Object> posts = new IdentityHashMap<>();

    /** What each sprite the game gives no placing to has been read as: see SpriteShape. */
    static final IdentityHashMap<IsoSprite, SpriteShape> shapes = new IdentityHashMap<>();

    /** Which square of a flight a sprite of this type is: 0 the bottom one, 2 the top, -1 for no stair. */
    static int thirdOf(IsoObjectType type) {
        return type == IsoObjectType.stairsBW || type == IsoObjectType.stairsBN ? 0
                : type == IsoObjectType.stairsMW || type == IsoObjectType.stairsMN ? 1
                : type == IsoObjectType.stairsTW || type == IsoObjectType.stairsTN ? 2 : -1;
    }

    static boolean climbsWest(IsoObjectType type) {
        return type == IsoObjectType.stairsBW || type == IsoObjectType.stairsMW || type == IsoObjectType.stairsTW;
    }

    static boolean isStair(IsoObjectType type) {
        return type == IsoObjectType.stairsBW || type == IsoObjectType.stairsMW || type == IsoObjectType.stairsTW
                || type == IsoObjectType.stairsBN || type == IsoObjectType.stairsMN || type == IsoObjectType.stairsTN;
    }

    /** How deep the canopy over each square of one is, in tiles, by the square's place: 0 for a floor in the air with no band round it. */
    static final HashMap<Long, Float> canopies = new HashMap<>();

    /** Whether a square with a floor is roof over open air and no room's floor: nothing is built on it, nor under it on the level below. */
    static boolean isCanopy(IsoGridSquare square, IsoGridSquare below) {
        return square != null && square.getFloor() != null && square.getRoomID() == -1L && square.getZ() >= 1
                && (below == null || below.getRoomID() == -1L && below.getFloor() == null);
    }

    /** For each door sprite on each edge: the frame origin it was read with, then the shares of the edge its leaf starts and ends at. */
    static final IdentityHashMap<Texture, float[]> doorSpans = new IdentityHashMap<>();

    /**
     * Whether a lamp burns in the room a square is in: the room's own light, which the game's
     * lighting sets from the switch and the power, or a lamp stood in the room. A chunk's sum
     * holds it for every lit square of a room: the game gives a room its lights, lists its
     * lamps and switches both with no mark on the room's chunks, and in daylight no square's
     * light changes with them either, so nothing else says that a window's pane is now lit.
     */
    static boolean lampBurns(IsoCell cell, IsoGridSquare square, List<IsoLightSource> lamps) {
        IsoRoom room = square.getRoom();
        if (room == null) {
            return false;
        }
        if (roomLightsOn(room)) {
            return true;
        }
        for (int i = 0; i < lamps.size(); i++) {
            IsoLightSource lamp = lamps.get(i);
            if (!reaches(lamp, square)) {
                continue;
            }
            IsoGridSquare stands = cell.getGridSquare(lamp.x, lamp.y, lamp.z);
            if (stands != null && stands.getRoomID() == square.getRoomID()) {
                return true;
            }
        }
        return false;
    }

    /** Whether any of the lights the game gives a room from its switches burns. */
    private static boolean roomLightsOn(IsoRoom room) {
        for (int i = 0; i < room.roomLights.size(); i++) {
            if (room.roomLights.get(i).active) {
                return true;
            }
        }
        return false;
    }

    /** Whether a lamp burns and reaches the square, on its level. */
    private static boolean reaches(IsoLightSource lamp, IsoGridSquare square) {
        return lamp.active && lamp.z == square.getZ() && Math.abs(lamp.x - square.getX()) <= lamp.radius && Math.abs(lamp.y - square.getY()) <= lamp.radius;
    }

    private static final byte LAMP_UNASKED = 0, LAMP_OUT = 1, LAMP_BURNS = 2;

    /** How bright the game's light on a square has to be for a lamp burning in its room to count as reaching it. */
    private static final float LAMPLIT = 0.55f;
    /** What a room with a lamp burning is lit by before any lamplit room has been in sight: just what counts as lamplit. */
    private static final float LAMP_AT_LEAST = LAMPLIT + 0.05f;
    /** How finely a room's daylight follows the hour: each step builds every room's chunks again. */
    private static final int DAY_STEPS = 32;

    /**
     * Whether a lamp burns in the room a square is in and lights the square. How bright the
     * square is does not say so alone. The game keeps every square at full daylight for an
     * hour after the picture's sky is the night's, and it lights whole squares in the beam of
     * a torch or of a car's headlights. A room the game has not shown has no light to go by:
     * from the eye a lamp burning in it lights it, and shown only as the game shows it, it is dark.
     */
    static boolean lamplit(IsoCell cell, IsoGridSquare square, int playerIndex, boolean asTheGameShows, List<IsoLightSource> lamps) {
        if (square.getRoom() == null) {
            return false;
        }
        if (!square.isSeen(playerIndex)) {
            return !asTheGameShows && lampBurns(cell, square, lamps);
        }
        ColorInfo light = square.lighting[playerIndex].lightInfo();
        return Math.max(light.r, Math.max(light.g, light.b)) > LAMPLIT && lampBurns(cell, square, lamps);
    }

    /**
     * A floor of boards the picture lays: FIRST_BOARDS for the fewest boards to a tile along
     * x, the next for the same along y, and so on up two at a time to LAST_BOARDS. The
     * picture's vertex program reads the two back: see vBoards in PipelineShaders.
     */
    static final float FIRST_BOARDS = 16f, LAST_BOARDS = FIRST_BOARDS + 2 * (Flats.MOST_BOARDS - Flats.FEWEST_BOARDS) + 1;

    /**
     * Parquet the picture lays, wood all the same to everything else: FIRST_PARQUET for the
     * fewest blocks a side and slats to a block, the slats of the even blocks running along x,
     * the next for the same along y, then the next count of slats, and after the most slats
     * the next count of blocks. The picture's vertex program reads them back: see vBlocks.
     * Every one stays under LIT_STEP's 4096 thirty-twos, which the openness counts the light above.
     */
    static final float FIRST_PARQUET = 100f, LAST_PARQUET = FIRST_PARQUET
            + 2 * ((Tiles.MOST_BLOCKS - Tiles.FEWEST_BLOCKS + 1) * (Tiles.MOST_SLATS - Tiles.FEWEST_SLATS + 1)) - 1;

    /** What parquet of so many blocks a side and slats to a block is made of, the slats of its even blocks running along y or along x. */
    static float parquetOf(int blocks, int slats, boolean evenAlongY) {
        return FIRST_PARQUET + 2 * ((blocks - Tiles.FEWEST_BLOCKS) * (Tiles.MOST_SLATS - Tiles.FEWEST_SLATS + 1) + slats - Tiles.FEWEST_SLATS) + (evenAlongY ? 1 : 0);
    }

    /** What a floor of so many boards to a tile, running along y or along x, is made of. */
    static float boardsOf(int count, boolean alongY) {
        return FIRST_BOARDS + 2 * (count - Flats.FEWEST_BOARDS) + (alongY ? 1 : 0);
    }

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
                    || (north ? Edges.north(properties, type) : Edges.west(properties, type)))) {
                continue;
            }
            Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
            if (usable(texture)) {
                return object;
            }
        }
        return null;
    }

    static void set(float[] c, int corner, float x, float y, float h) {
        c[corner * 3] = x;
        c[corner * 3 + 1] = y;
        c[corner * 3 + 2] = h;
    }

    static boolean isWall(PropertyContainer properties, IsoObjectType type) {
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


    /** Whether what has these flags is a wall, a fence, a door's frame or a window on the north edge of its square. */
    static boolean onNorthEdge(PropertyContainer flags) {
        return flags.has(IsoFlagType.WallN) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.WallNTrans)
                || flags.has(IsoFlagType.DoorWallN) || flags.has(IsoFlagType.WindowN) || flags.has(IsoFlagType.HoppableN);
    }

    static boolean onWestEdge(PropertyContainer flags) {
        return flags.has(IsoFlagType.WallW) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.WallWTrans)
                || flags.has(IsoFlagType.DoorWallW) || flags.has(IsoFlagType.WindowW) || flags.has(IsoFlagType.HoppableW);
    }


    /** With tracing on, the memory the render thread took for the picture since it was last asked. */
    private final AtomicLong drawnBytes = new AtomicLong();

    long takeDrawnBytes() {
        return drawnBytes.getAndSet(0);
    }

    /**
     * Logic thread: who stands in a room the eye could not see into lately, nor through any shut
     * door in reach once opened, or null for none. The game's choice of which zombies are given
     * a model goes by it: see FarModels and roomsMayShow.
     */
    FigureRooms roomsForModels;
    /**
     * Logic thread: who the eye's picture leaves out by the room they stand in, as the last
     * frame worked it out, or null for none: see FigureRooms. The game does not draw them
     * either: see FarModels.onScreen.
     */
    FigureRooms figuresLeftOut;

    /** One frame of the picture: what of the world this view shows in it, built on the logic thread and drawn on the picture's. */
    final class Frame extends TextureDraw.GenericDrawer implements Grass.Ground {
        /** The share of its plants grass grows in this build: see Grass.density. */
        float grassDensity() {
            return grassGrown;
        }

        final FloorBuilder floorBuilder = new FloorBuilder(this);
        private final KerbBuilder kerbBuilder = new KerbBuilder(this);
        private final PlantBuilder plantBuilder = new PlantBuilder(WorldScene.this, this);
        final WallBuilder wallBuilder = new WallBuilder(WorldScene.this, this);
        final OpeningBuilder openingBuilder = new OpeningBuilder(this);
        final PostBuilder postBuilder = new PostBuilder(this);
        private final StairBuilder stairBuilder = new StairBuilder(this);
        private final FurnishingBuilder furnishingBuilder = new FurnishingBuilder(this);
        private final BloodBuilder bloodBuilder = new BloodBuilder(this);
        final CeilingBuilder ceilingBuilder = new CeilingBuilder(WorldScene.this, this);
        final RoofBuilder roofBuilder = new RoofBuilder(WorldScene.this, this);
        private final WireBuilder wireBuilder = new WireBuilder(WorldScene.this, this);
        /** Dev only: set round a check's third build, which makes its crowns in the chunk's own sets as every build once did. */
        boolean crownsInline;

        /** The chunk builds this frame draws. */
        private final ArrayList<ChunkMeshes> shown = new ArrayList<>();
        /** The builds this frame made of chunks the view does not face, which the frame hands to the graphics card and does not draw. */
        private final ArrayList<ChunkMeshes> builtAheadOf = new ArrayList<>();
        /** Whether any of them has water on it, and the plane that water lies in round the eye: see WaterMirror. */
        private boolean waterShown;
        private final float[] waterPlane = new float[4];
        Models.Instances placed;
        final SpriteGeometry geometry = new SpriteGeometry();
        final SpriteGeometry.Frame frame = new SpriteGeometry.Frame();

        final ViewCamera.Pose camera = new ViewCamera.Pose();
        /**
         * Set for a scene that is seen from outside the viewer, from far overhead or on the way
         * between there and the eye: the camera it is drawn from, the tangent of half its field of
         * view and how far it is from the place it looks at. The scene is still built round
         * `camera`, which stands where the player does, so everything that goes by nearness is as
         * it would be for someone standing there.
         */
        private final ViewCamera.Pose outsideCamera = new ViewCamera.Pose();
        private boolean outside;
        private boolean seated;
        private float seatedX, seatedY, seatedGroundUp;
        private final float[] seatedTurn = new float[9];
        private final float[] seatedAxes = new float[9];
        private float outsideTan, outsideAway;
        /** The windows near the viewer whose glass may throw the room back. */
        private final WindowGlass.Panes panes = new WindowGlass.Panes();
        /** How much of the way from overhead to the eye the outside camera has come. */
        private float landing;
        /** How much of the game's own picture this one covers: less than all of it while one is mixed into the other. */
        private float opacity;

        /**
         * For a build of the game's own view: where on the map the middle of the screen is, at the
         * player's floor, which way along the ground the view looks, how far from the middle the
         * screen reaches across that and along it, and how far a floor above is drawn up the
         * screen from the one below, all in tiles. Good for the one build that follows.
         */
        private boolean seenOnly;
        private float seenRoundX, seenRoundY, seenAlongX, seenAlongY, seenHalfAcross, seenHalfAlong, seenLevelShift;

        void seenOnly(float x, float y, float alongX, float alongY, float halfAcross, float halfAlong, float levelShift) {
            seenOnly = true;
            seenRoundX = x;
            seenRoundY = y;
            seenAlongX = alongX;
            seenAlongY = alongY;
            seenHalfAcross = halfAcross;
            seenHalfAlong = halfAlong;
            seenLevelShift = levelShift;
        }

        /**
         * @param landing how much of the way to the eye the camera has come: 0 from where the game's own view is drawn
         */
        void seenFrom(ViewCamera.Pose from, float tanHalfFieldOfView, float away, float landing) {
            outsideCamera.set(from);
            outsideTan = tanHalfFieldOfView;
            outsideAway = away;
            this.landing = landing;
            outside = true;
        }

        private float canopyX, canopyY, canopyUp, canopyAimX, canopyAimY;
        private boolean canopyAimed;
        private final float[] canopyFaded = new float[3 * CanopyWindow.TREES];
        private int canopyFadedCount;

        /** Where the game's own view opens its window through trees, on the player's floor, and the trees it draws see-through: see CanopyWindow and FirstPerson.aimOverhead. */
        void canopyWindow(float x, float y, float up, boolean aimed, float aimX, float aimY, float[] faded, int fadedCount) {
            canopyX = x;
            canopyY = y;
            canopyUp = up;
            canopyAimed = aimed;
            canopyAimX = aimX;
            canopyAimY = aimY;
            System.arraycopy(faded, 0, canopyFaded, 0, 3 * fadedCount);
            canopyFadedCount = fadedCount;
        }

        /** Has the picture mixed into the game's own, which is under it, and not laid over it whole. */
        void mixed(float opacity) {
            this.opacity = opacity;
        }
        /** The aim the held weapon is drawn on: see FirstPerson. */
        private final ViewCamera.Pose aim = new ViewCamera.Pose();
        /** Where the eye the frame is drawn from is from the aim's: see ViewCamera.between. */
        private final float[] eyeFromAim = new float[3];
        private float rightX, rightY;
        /**
         * Where the camera the frame is drawn from is, which is what an upright sprite is turned to
         * face. Seen from outside that is not the eye the scene is built round: turned to the
         * viewer, every card in the game's own view swung round as the viewer walked past it.
         */
        private float facedX, facedY;
        private float tanHalfFieldOfView;
        private float tanHalfSet;

        /** The characters this frame draws, for the aim to be traced against. */
        float[] characterX = new float[16], characterY = new float[16], characterBase = new float[16];
        IsoGameCharacter[] characterOf = new IsoGameCharacter[16];
        int characters;
        private final SkyState sky = new SkyState();
        private final RoomLamps roomLamps = new RoomLamps();
        private float fogEnd;
        /** The middle of the map the game has loaded, and how far from it the map reaches every way, in tiles. */
        private float loadedX, loadedY, loadedWithin;
        /** How far from the eye people, zombies, vehicles and furniture are drawn, from the eye's frame: see DrawDistance. */
        private final DrawDistance drawDistance = new DrawDistance();
        /** How much light the eye lets in this frame: see adaptEye. */
        float exposure;
        /** What the eye would let in by where the viewer stands alone, and what the light meter read: see adaptEye. */
        float located, metered;
        private IsoPlayer viewer;
        IsoCell cell;
        int playerIndex;
        int lowestLevel;
        private float cutFrom = Float.MAX_VALUE;
        /** Whether the frame is built as the game's own view, which leaves out and cuts away what the game does. */
        boolean gamesView;
        /** For such a frame, the walls the game cuts away: see Cutaways and Pipeline.cutRows. */
        private final float[] cutRows = new float[Cutaways.FLOATS];
        /** For such a frame, where rain and snow are kept out with no roof drawn, or null: see Shelter. */
        private Shelter sheltered;
        /** Which air round the eye is in a room: see RoomAir. */
        private RoomAir roomAir;
        /** The height of the floor the viewer stands on. */
        private float stoodUp;
        private int cutRowsX, cutRowsY;
        private float cutBelow;
        /** For a frame seen from outside the viewer, how the game's own view would darken each square: see Sight. */
        private final Sight sight = new Sight();
        private boolean sightRead;
        /**
         * The height at which the viewer's storey ends, for a viewer indoors whose building is
         * whole in the scene: what is above it is left out of the picture while the camera is too.
         */
        private float lidFrom = Float.MAX_VALUE;
        /** The cut box as this frame was built with it. The statics are the next build's by the time the frame is drawn. */
        private int boxMinX, boxMinY, boxMaxX, boxMaxY;
        private int chunksBuilt, chunksKept;
        /** How many chunks had what grows on their ground made this frame. */
        private int chunksGrown;
        /**
         * How far from the eye everything of the chunk being built is taken to be, in tiles, by
         * what is made finer or left out with distance: the middle of the chunk's band, one number
         * for the whole chunk, so that the build is the same wherever in the band the eye is.
         */
        float bandAway;
        /** Whether only what grows on the ground is being added: see latestGrown. */
        private boolean growing;
        int boxes;
        int furnished;
        int spreadFaces;
        private int lifted;
        private int chunksDrawn, squaresLoaded, squaresSeen, sprites, triangles;
        int roofsNorthSouth, roofsWestEast, roofsFlat, roofsUnclear, roofsFolded;
        /** The gable walls made in the last build: in the sprite of the building's face, in its colour alone, and those left open for want of either. */
        int gablesFaced, gablesPainted, gablesBare;
        int floorLayer, westLayer, northLayer, eastLayer, southLayer;
        private final float[] openLight = new float[3];
        /** Whether a room square is lit at least by the daylight its room lets in (RoomDaylight): not in Low, which keeps the game's light, nor as the game's own view. */
        private boolean roomsDaylit;
        /** When this frame was begun: RoomDaylight works a room out again by it. */
        private long daylitAt;
        /** How much of the day there is (Evening.day), in steps of 1 / DAY_STEPS. A room has the day it lets in by as much. */
        private float day;
        /**
         * The open ground's light by as much of the day as there is, in steps of 1 / DAY_STEPS: a
         * room the game has lit is built lit at least by its share of it (daylit). In steps, so
         * the builds go by it only as it moves a step; the rooms it has not lit take it in the
         * picture (SkyState.daylight).
         */
        private final float[] dayLight = new float[3];
        /** Where TorchLess writes a square's light when the viewer's torch is on the square. */
        final ColorInfo torchless = new ColorInfo();
        final float[] box = new float[4];
        int overLayer;
        /** How many layers of cards towards the eye the object being added stands, before its overlays: see standingNearer. */
        float standsNearer;
        /** How many things stood on something the square being built has had added so far. */
        int stoodOn;
        /** How many plants the square being built has had fanned so far, and which crops it has had grown (a bit for each). */
        int plantsHere, cropsGrownHere;
        private int remakes;

        private Kind kindOf(IsoSprite sprite) {
            if (afresh) {
                return new Kind(sprite);
            }
            Kind kind = kinds.get(sprite);
            if (kind == null) {
                kind = new Kind(sprite);
                kinds.put(sprite, kind);
            }
            return kind;
        }
        private boolean wallNorth, wallWest;
        /** Whether the square being built holds a square of a tent's canopy: see Tents.isCanvas. */
        private boolean canvas;
        /** Whether anything of a wall's kind, a fence or a low wall too, stands on the north and the west edge of the square being added. */
        boolean edgeNorth, edgeWest;
        String steepestSaid = "", roofName = "";
        final float[] eave = new float[12];
        /** What tree, as Litter.kindOf names it, stands on each square of the chunk being built and of Litter.RING more on every side. */
        final byte[] woods = new byte[WOOD_SPAN * WOOD_SPAN];
        /** How far into a wood each corner of the chunk being built is: see Flora.wooded. */
        final float[] wooded = new float[Terrain.CORNERS * Terrain.CORNERS];
        /** For what grows on the chunk being built: how deep each corner is in leaf and in needles, and which leaf and which needles lie on each square. See Litter.fields. */
        final float[] leaves = new float[Terrain.CORNERS * Terrain.CORNERS], needles = new float[Terrain.CORNERS * Terrain.CORNERS];
        final byte[] leafKinds = new byte[CHUNK_TILES * CHUNK_TILES], needleKinds = new byte[CHUNK_TILES * CHUNK_TILES];
        /** Whether any tree stands on the chunk being built or near enough to drop litter on it. */
        private boolean treesRound;
        /** How many of its neighbours' grounds spreadGround last laid over a square. */
        int spreadLayers;
        /** Whether the square being added has no floor. */
        boolean hangsInAir;
        /** The room of the square being added, or RoomSight.OUTSIDE between squares: see contentsOf. */
        private long squareRoom = RoomSight.OUTSIDE;
        /** The square whose grass is being grown: where in the chunk it is, which of its sides run on into more lawn, and how much standing under trees takes from its grass. */
        int grownLocalX, grownLocalY;
        boolean lawnWest = true, lawnEast = true, lawnNorth = true, lawnSouth = true;
        float grownUnder;
        /** The ground of the chunk being built and of the ring of squares round it, laid out as the lights are. */
        final byte[] groundKind = new byte[LIGHT_SPAN * LIGHT_SPAN];
        final IsoObject[] groundObject = new IsoObject[LIGHT_SPAN * LIGHT_SPAN];
        final Texture[] groundTexture = new Texture[LIGHT_SPAN * LIGHT_SPAN];
        final int[] groundRank = new int[LIGHT_SPAN * LIGHT_SPAN];
        /** Whether a square of open ground has none beside it: a tree's pit in a pavement, which keeps to its square. */
        final boolean[] groundLone = new boolean[LIGHT_SPAN * LIGHT_SPAN];
        final float[] cover = new float[4];
        /** The place in its chunk of the square being added. */
        int waterLocalX, waterLocalY;
        final float[] lightRed = new float[LIGHT_SPAN * LIGHT_SPAN], lightGreen = new float[LIGHT_SPAN * LIGHT_SPAN],
                lightBlue = new float[LIGHT_SPAN * LIGHT_SPAN];
        final byte[] lightOpen = new byte[LIGHT_SPAN * LIGHT_SPAN];
        /** Each square's kind of light: OWN_LIGHT, ROOM_LIT, LAMP_LIT or OPEN_LIT. */
        final byte[] lightUnseen = new byte[LIGHT_SPAN * LIGHT_SPAN];
        /** For a square lit by ROOM_LIT or LAMP_LIT, the share of the open ground's light its room lets in by day (dayShare), or 0. */
        final float[] lightDay = new float[LIGHT_SPAN * LIGHT_SPAN];
        final float[] heldRed = new float[4], heldGreen = new float[4], heldBlue = new float[4], heldUnlit = new float[SpriteGeometry.Frame.UNLIT_FLOATS];
        /**
         * The lights of every level of the chunk last summed, as readLights left each: a chunk
         * found changed is built in the same frame, and its build takes them from here in place
         * of asking the game for every square a second time. The ground and the woods are of
         * level 0 alone and stay where they were read into. A level the reading took its sum of
         * from the build has nothing kept, and keptLevels has a bit for each level that has.
         */
        private float[] keptRed = new float[0], keptGreen = new float[0], keptBlue = new float[0];
        private float[] keptDay = new float[0];
        private byte[] keptOpen = new byte[0], keptUnseen = new byte[0];
        private int keptWx, keptWy, keptFrom, keptTo, keptFrame = -1;
        /**
         * The lowest level of the viewer's own building whose light is held as its build was made,
         * and the box of the building a square wider all round: see where it is set. MAX_VALUE
         * while the viewer is in no building.
         */
        private int heldFrom = Integer.MAX_VALUE, heldMinX, heldMinY, heldMaxX, heldMaxY;
        /** The highest level of the viewer's own building under it that is held as heldFrom's are, MIN_VALUE while the viewer is in no building. */
        private int heldUnder = Integer.MIN_VALUE;
        /** The viewer's own building while heldFrom is set: its storeys from there up are left out of the eye's picture too. */
        private long heldBuilding = SpriteGeometry.NO_BUILDING;
        private long keptLevels;
        /** Whether any square of the chunk last read through, on any level, has a canopy's floor over it. */
        boolean canopied;
        /** The squares readLights last read, laid out as the lights are. */
        private final IsoGridSquare[] lightSquare = new IsoGridSquare[LIGHT_SPAN * LIGHT_SPAN];
        /** The chunk being read and the eight round it, by column then row, null where the map has none loaded. */
        final IsoChunk[] around = new IsoChunk[9];
        private long auditTerrain, auditTrees;
        private long[] auditLights = new long[0], auditThings = new long[0];
        /** What every build goes by that is not of its chunk, as one number: see build. */
        private long epoch;
        /**
         * The season's stage (Seasons.stage) and the one before it, and the frame it moved on.
         * Only a build that holds a tree the season shapes goes by it, and those come to the new
         * stage over STAGE_SPREAD frames, each in a frame of its own by its slot: at once, every
         * near chunk with a tree in it would be made again in one frame.
         */
        private int stage = Integer.MIN_VALUE, stageBefore;
        private long stageMoved;
        /** Set by the last reading of a chunk: see ChunkMeshes.restless. */
        private boolean restless;
        /** Which of the chunk last looked round and the eight round it are loaded, a bit each: see surround. */
        private int ring;
        /** The light of the seen squares out of doors of the chunk being read. */
        private final OpenLight reading = new OpenLight();
        /** And of its room squares in the character's sight: see seenRooms. */
        private final OpenLight readingRooms = new OpenLight(), readingLamplit = new OpenLight();
        /** Whether what stands in a room the game has not shown is left out of this frame's builds: see Sightline. */
        boolean asTheGameShows;
        private boolean tallying;
        private int chunksQuiet;

        /** The building and the viewer's level heldFrom and heldUnder were last found for, whether every column of it was loaded then, and the frame. */
        private long lidsOf = SpriteGeometry.NO_BUILDING;
        private int lidsAt;
        private boolean lidsWhole;
        private int lidsFrame;
        /** How many frames go by before a building some of whose columns were not loaded is looked through again. */
        private static final int LIDS_AGAIN = 30;

        /**
         * Reads the game's darkening of the squares round the viewer into sight: only those the
         * game's own view has on screen when the frame says which those are. Off the viewer's
         * floor, where nothing stands at their level, a square is read from the first floor
         * down that has one, as the ground seen from a balcony.
         *
         * The shader looks a square up by where the surface it draws stands, and a floor below
         * the viewer's is on screen further along the view than the viewer's own, one above it
         * nearer, by the level shift a floor: so the squares read reach as far as the lowest and
         * the highest floors drawn put the screen's edges.
         */
        private void readSight(IsoCell cell, IsoChunkMap map, int level, int lowest, int stoodX, int stoodY, boolean screenOnly) {
            if (!screenOnly) {
                sight.begin(stoodX, stoodY);
                readSight(cell, level, sight.x, sight.y, sight.x + Sight.SIDE - 1, sight.y + Sight.SIDE - 1);
                return;
            }
            int highest = level;
            for (int gridY = 0; gridY < IsoChunkMap.chunkGridWidth; gridY++) {
                for (int gridX = 0; gridX < IsoChunkMap.chunkGridWidth; gridX++) {
                    IsoChunk chunk = map.getChunk(gridX, gridY);
                    if (chunk != null && chunk.loaded) {
                        highest = Math.max(highest, chunk.getMaxLevel());
                    }
                }
            }
            // Along the view, from the middle of the screen at the viewer's floor: the nearest and the furthest square on it.
            float nearest = -seenHalfAlong - (highest - level) * seenLevelShift - SIGHT_MARGIN;
            float furthest = seenHalfAlong + (level - lowest) * seenLevelShift + SIGHT_MARGIN;
            float across = seenHalfAcross + SIGHT_MARGIN, middle = (nearest + furthest) / 2f, halfAlong = (furthest - nearest) / 2f;
            float middleX = seenRoundX + seenAlongX * middle, middleY = seenRoundY + seenAlongY * middle;
            float reachX = Math.abs(seenAlongX) * halfAlong + Math.abs(seenAlongY) * across;
            float reachY = Math.abs(seenAlongY) * halfAlong + Math.abs(seenAlongX) * across;
            sight.begin((int) Math.floor(middleX), (int) Math.floor(middleY));
            readSight(cell, level, Math.max(sight.x, (int) Math.floor(middleX - reachX)), Math.max(sight.y, (int) Math.floor(middleY - reachY)),
                    Math.min(sight.x + Sight.SIDE - 1, (int) Math.floor(middleX + reachX)), Math.min(sight.y + Sight.SIDE - 1, (int) Math.floor(middleY + reachY)));
        }

        private void readSight(IsoCell cell, int level, int fromX, int fromY, int toX, int toY) {
            for (int squareY = fromY; squareY <= toY; squareY++) {
                for (int squareX = fromX; squareX <= toX; squareX++) {
                    IsoGridSquare square = cell.getGridSquare(squareX, squareY, level);
                    for (int below = level - 1; below >= 0 && (square == null || square.getFloor() == null); below--) {
                        IsoGridSquare under = cell.getGridSquare(squareX, squareY, below);
                        if (under != null) {
                            square = under;
                        }
                    }
                    if (square == null) {
                        sight.put(squareX, squareY, 0);
                        continue;
                    }
                    BuildingDef building = square.getRoomID() == -1L || square.getBuilding() == null ? null : square.getBuilding().getDef();
                    if (building != null && !openBuildings.contains(building)) {
                        sight.put(squareX, squareY, Sight.CLOSED);
                        continue;
                    }
                    // isCanSee brings the square's figures up to date from the lighting engine; the dark multiplier alone does not.
                    square.isCanSee(playerIndex);
                    sight.put(squareX, squareY, Sight.kept(square.getDarkMulti(playerIndex)));
                }
            }
            sight.settle();
        }

        /** Sets heldFrom and heldUnder for the viewer on that level of the building: see Lids. */
        private void lids(BuildingDef def, int level) {
            Lids.Held held = Lids.of(cell, def, level);
            heldFrom = held.from();
            heldUnder = held.under();
            lidsOf = def.id;
            lidsAt = level;
            lidsWhole = held.whole();
            lidsFrame = frames;
        }

        /**
         * Asks the lighting engine, once for each of its passes, whether a chunk's lights have
         * changed, and has the game take them where they have. The game holds a copy of each
         * square's light that is only as new as the last asking of that square, and it marks the
         * chunk where the copy changes (ChunkMarks), so without this a chunk that is not read
         * would never be marked for its light. The engine's flag for a level of a chunk is put
         * down by the asking, and the game asks itself for the levels its own view has on screen,
         * though it then takes only the squares that view shows: for those the game's note of its
         * own asking is gone by, and every square is taken. Under the first person picture the
         * game asks for none (Agent.coveredPatches leaves that out with the rest of what it does
         * for a texture it never draws), and every level is asked for here.
         */
        private void pullLights(IsoChunk chunk, int slot, int lowest) {
            int pass = LightingJNI.getUpdateCounter(playerIndex);
            // In the viewer's building only the storeys round the eye's are asked for: those over
            // it are held (heldFrom), and those further under are seen, if at all, down a
            // stairwell. Each asking for a storey marks it for its light, and every mark built
            // the whole tower's column again. Moved to another storey, every level now asked for
            // is taken once, as what was left unasked may have changed.
            int chunkX = chunk.wx * CHUNK_TILES, chunkY = chunk.wy * CHUNK_TILES;
            int storey = heldFrom != Integer.MAX_VALUE && chunkX < heldMaxX && chunkX + CHUNK_TILES > heldMinX && chunkY < heldMaxY && chunkY + CHUNK_TILES > heldMinY
                    ? heldFrom : Integer.MAX_VALUE;
            boolean taken = chunk.lightingUpdateCounter != litByGame[slot] || storey != litFor[slot];
            if (pass == litAt[slot] && !taken) {
                return;
            }
            litAt[slot] = pass;
            litByGame[slot] = chunk.lightingUpdateCounter;
            litFor[slot] = storey;
            FBORenderLevels levels = chunk.getRenderLevels(playerIndex);
            boolean gameAsks = !view.firstPerson.coversTheGamesView();
            int top = storey == Integer.MAX_VALUE ? chunk.getMaxLevel() : Math.min(chunk.getMaxLevel(), storey - 1);
            for (int z = Math.max(storey == Integer.MAX_VALUE ? lowest : Math.max(lowest, heldUnder + 1), chunk.getMinLevel()); z <= top; z++) {
                tollPulls++;
                if (!taken && (gameAsks && levels.isOnScreen(z) || !LightingJNI.getChunkDirty(playerIndex, chunk.wx, chunk.wy, z + 32))) {
                    continue;
                }
                tollPulled++;
                if (taken) {
                    tollPulledAfterGame++;
                }
                for (int x = 0; x < CHUNK_TILES; x++) {
                    for (int y = 0; y < CHUNK_TILES; y++) {
                        IsoGridSquare square = chunk.getGridSquare(x, y, z);
                        if (square != null) {
                            square.cacheLightInfo();
                        }
                    }
                }
            }
        }
        /** Set round the second build of a check: nothing kept is taken. */
        boolean afresh;
        private final Models.Instances checkPlaced = new Models.Instances();
        private float buildMillis;
        private final int[] builtBecause = new int[7];
        /** Chunks in view that had no build yet when the frame had spent its time on building. */
        private int chunksWaiting;

        /** How many chunks in view the last build left for the next frames. */
        int waiting() {
            return chunksWaiting;
        }

        /** The square being added. */
        IsoGridSquare here;
        /** Whether the wall being added has a door or a window in it. */
        boolean wallOpens;
        final SpriteGeometry.Frame held = new SpriteGeometry.Frame();
        final float[] reveal = new float[12];

        private Frame() {}

        /** What the frame cost: said when a frame was long enough to feel. */
        String timing() {
            return String.format(Locale.ROOT, "%d chunks built in %.1f ms and %d left for the next frames, the picture drawn in %.1f ms"
                    + " (new %d moved %d changed %d aged %d unsigned %d unread %d, grown %d)",
                    chunksBuilt, buildMillis, chunksWaiting, view.pipeline.lastMillis(), builtBecause[NEW], builtBecause[MOVED], builtBecause[CHANGED],
                    builtBecause[AGED], builtBecause[UNSIGNED], builtBecause[LEFT_UNREAD], chunksGrown);
        }

        String describe() {
            return String.format(Locale.ROOT, "chunks %d waiting %d built %d (new %d moved %d changed %d aged %d unsigned %d unread %d) grown %d kept %d quiet %d squares %d seen %d sprites %d triangles %d build %.2f ms draw %.2f ms"
                            + " exposure %.2f (by place %.2f, metered %.2f) tan half fov %.4f aspect %.4f fog end %.1f roofs n-s %d w-e %d flat %d unclear %d boxes %d furniture %d spread %d lifted %d models %d | %s",
                    chunksDrawn, chunksWaiting, chunksBuilt, builtBecause[NEW], builtBecause[MOVED], builtBecause[CHANGED], builtBecause[AGED],
                    builtBecause[UNSIGNED], builtBecause[LEFT_UNREAD], chunksGrown, chunksKept, chunksQuiet, squaresLoaded, squaresSeen, sprites, triangles, buildMillis, lastDrawMillis, exposure, located, metered,
                    tanHalfFieldOfView, drawnAspect, fogEnd, roofsNorthSouth, roofsWestEast, roofsFlat, roofsUnclear, boxes, furnished, spreadFaces, lifted, view.characters.drawnLast(), sky.describe()) + " | " + view.pipeline.describeShadows() + " | " + ChunkBuffers.describe()
                    + String.format(Locale.ROOT, ", builds are filled from %d meshes with room for %.0f MB", solid.kept() + laid.kept(), (solid.room() + laid.room()) * Float.BYTES / 1048576f)
                    + String.format(Locale.ROOT, " | rooms held to lines %d times in %.2f ms each, %d left out", roomChecks, roomChecks == 0 ? 0f : roomChecksNanos / 1e6f / roomChecks, roomsMissed)
                    + " | noted " + view.characters.described + " | steepest roof " + steepestSaid + " | roofs folded " + roofsFolded
                    + " | gables faced " + gablesFaced + " painted " + gablesPainted + " bare " + gablesBare
                    + " | " + view.carriedLight.describe() + String.format(Locale.ROOT, " open light (%.2f, %.2f, %.2f)", floorRed, floorGreen, floorBlue);
        }

        /** The tangent of half the vertical field of view this frame is drawn with. */
        float tanHalfFieldOfView() {
            return tanHalfFieldOfView;
        }

        /**
         * @param pose               the eye and orientation the frame is drawn from, head tracking included
         * @param aimed              the aim, which the weapon in hand is held on
         * @param eyeFromAim         where pose's eye is from aimed's, in the world's axes: see ViewCamera.between
         * @param tanHalfFieldOfView the tangent of half the vertical field of view the frame is drawn with
         * @param tanHalfSet         the tangent of half the vertical field of view the player set, which
         *                           the first is narrower than while a firearm is aimed
         * @param from               the camera the frame is drawn by when that is somewhere else, or null: see seenFrom
         * @param opened             the frame is the game's own view: what the game leaves out of that view is not built, and the walls it cuts away are cut
         */
        void build(IsoPlayer player, ViewCamera.Pose pose, ViewCamera.Pose aimed, float[] eyeFromAim, float tanHalfFieldOfView, float tanHalfSet,
                ViewCamera.Pose from, boolean opened) {
            long start = System.nanoTime();
            builtAt[view.index] = start;
            int sharing = building(start);
            // What every view shares is tended once a round of their builds, not once for each: see Rounds.
            boolean roundBegins = Rounds.BUILDS.begin(view.index);
            roundNow = Rounds.BUILDS.now();
            if (roundBegins) {
                Screens.tick();
            }
            boolean fromOutside = from != null;
            boolean screenOnly = seenOnly;
            seenOnly = false;
            camera.set(pose);
            facedX = fromOutside ? from.eyeX : pose.eyeX;
            facedY = fromOutside ? from.eyeY : pose.eyeY;
            aim.set(aimed);
            System.arraycopy(eyeFromAim, 0, this.eyeFromAim, 0, 3);
            outside = false;
            opacity = 1f;
            seated = view.ride.seated;
            seatedX = view.ride.atX;
            seatedY = view.ride.atY;
            seatedGroundUp = view.ride.groundUp;
            System.arraycopy(view.ride.turn, 0, seatedTurn, 0, seatedTurn.length);
            System.arraycopy(view.ride.stands, 0, seatedAxes, 0, seatedAxes.length);
            this.tanHalfFieldOfView = tanHalfFieldOfView;
            this.tanHalfSet = tanHalfSet;
            float flat = (float) Math.sqrt(pose.forwardX * pose.forwardX + pose.forwardY * pose.forwardY);
            if (flat > 1e-3f) {
                rightX = -pose.forwardY / flat;
                rightY = pose.forwardX / flat;
            }
            beginTallies(pose);
            int agedBuilt = 0;

            viewer = player;
            float daylight = GameTime.getInstance().getAmbient();
            sky.capture();
            sky.sight(player);
            if (unseenRed < 0f) {
                unseenRed = unseenGreen = unseenBlue = daylight;
                floorRed = floorGreen = floorBlue = daylight;
            }
            sky.openRed = floorRed;
            sky.openGreen = floorGreen;
            sky.openBlue = floorBlue;
            float gameOpen = (floorRed + floorGreen + floorBlue) / 3f;
            if (ClimateManager.getInstance().getNightStrength() >= 1f) {
                nightOpen = gameOpen;
            }
            sky.daylightKept = Evening.kept(sky.sunUp, gameOpen, nightOpen);
            // By day: at night a room is seen by its lamps, and the eye is held back from opening up to it.
            // Low lights a room as the game does.
            sky.roomKept = Quality.plain(view.index) ? 1f : Evening.ROOM_DAYLIGHT + (1f - Evening.ROOM_DAYLIGHT) * sky.night;
            openNow = gameOpen;
            daylightKeptNow = sky.daylightKept;
            roomKeptNow = sky.roomKept;
            // The beam of this picture's own view: the one being gathered now may be a turn of the head later.
            sky.carried = view.carriedLight.light;
            HeldLight.light(view.heldLight.now, floorRed, floorGreen, floorBlue, sky.daylightKept, sky.roomKept, sky.held);
            sky.outlined = Outlines.gather(player.getIndex(), view.centreUse.marked);
            sky.facedX = facedX;
            sky.facedY = facedY;
            sky.layersFromX = pose.eyeX;
            sky.layersFromY = pose.eyeY;
            sky.layersFromUp = pose.eyeUp;
            sky.acrossX = rightX;
            sky.acrossY = rightY;
            exposure = adaptEye(player, fromOutside);
            Lightning.see(view.index, sky, pose.eyeX, pose.eyeY, pose.eyeUp, System.nanoTime());
            Fires.gather(sky, terrain, player.getIndex(), pose.eyeX, pose.eyeY);
            playerIndex = player.getIndex();
            cell = IsoWorld.instance.currentCell;
            Lamps.gather(sky, cell, terrain, pose.eyeX, pose.eyeY, Quality.lightDistance(view.index));
            roomLamps.gather(sky, cell, terrain, view.torchLess, playerIndex, pose.eyeX, pose.eyeY, (int) Math.floor(player.getZ()), Quality.lightDistance(view.index));
            leafTrees.gather(sky, cell, terrain, roundNow, Core.tileScale, pose.eyeX, pose.eyeY, start);
            roofBuilder.newBuild();
            if (Own.taken(view.index)) {
                // A row of [Content] was switched: every build holds what the rows were, and so does every roof read and every square's ground, which leave a parasol out by its row.
                forgetAll();
                Roofs.forget();
                Terrain.reread();
            }
            float grass = Grass.density(Quality.grassDensity(view.index), Online.contested());
            if (grass != grassGrown) {
                // Every build holds the grass it grew.
                grassGrown = grass;
                forgetAll();
            }
            if (crownedToSight != Quality.crownsToSight(view.index)) {
                // Every build in the last band holds whether its trees were given crowns and boles.
                crownedToSight = Quality.crownsToSight(view.index);
                forgetAll();
            }
            float twigged = Flora.twiggedFrom(zombie.iso.IsoCamera.getScreenHeight(view.index), tanHalfSet);
            if (twigged != twiggedFrom) {
                // As are their bare crowns' twigs, which reach as far as the picture is high and narrow.
                twiggedFrom = twigged;
                forgetAll();
            }
            if (cell != builtIn.get()) {
                // Another save, loaded without leaving the game: every build is of the world before.
                builtIn = new WeakReference<>(cell);
                squareLights.forget();
                roomDaylight.forget();
                for (Frame scene : scenes) {
                    scene.lampsBurning.clear();
                    scene.lampsSortedFrame = -1;
                    scene.lampsNear.clear();
                    scene.roomAsked = null;
                    Arrays.fill(scene.groundObject, null);
                    Arrays.fill(scene.around, null);
                }
                roomShare = lampLevel = -1f;
                strung.clear();
                bannerRuns.clear();
                forgetAll();
                forgetTheOpened();
            }
            if (cell != readIn.get()) {
                // And so is what is kept by place on the map or by one of the game's sprites, once for every view.
                readIn = new WeakReference<>(cell);
                canopies.clear();
                openings.clear();
                feet.clear();
                posts.clear();
                shapes.clear();
                meshes.clear();
                kinds.clear();
                boles.clear();
                Trunks.forget();
                woodShapes.clear();
                ranks.clear();
                leafPlaces.clear();
                cropColours.clear();
                doorSpans.clear();
                wires.clear();
                Furniture.forget();
                PlantBuilder.forget();
                Parasols.forget();
                Awnings.forget();
                Tents.forget();
                Hoods.forget();
                Towers.forget();
                Leisure.forgetGoals();
                Lights.forget();
                Roofs.forget();
                WallFinish.forget();
                MapAhead.forget();
                Terrain.forget();
            }
            float hills = HillHeight.drawn(Online.contested());
            if (Terrain.HILLS.heighten(hills, view.index, pose.eyeX, pose.eyeY, cell, HillsFromMap.REWORK)) {
                Log.info("hill height " + hills + ": the heights round the eye are worked out again, and every other chunk's as it is next asked for");
            }
            if (hills != farHills) {
                // The far land is made at the height the land was when it was asked for.
                farHills = hills;
                farLand.forget();
            }
            mainsOn = IsoWorld.instance.isHydroPowerOn();
            long goesBy = sum(sum(SUM_FROM, mainsOn ? 1 : 0), Float.floatToRawIntBits(Core.tileScale));
            int seasonNow = Seasons.stage();
            if (seasonNow != stage) {
                stageBefore = stage == Integer.MIN_VALUE ? seasonNow : stage;
                stage = seasonNow;
                stageMoved = frames;
            }
            boolean showing = Sightline.asTheGameShows(withoutSightline);
            if (showing != asTheGameShows) {
                // A build made by the other rule, seen before it falls due, would fill or empty its rooms a frame or more late.
                asTheGameShows = showing;
                forgetAll();
            }
            goesBy = sum(goesBy, asTheGameShows ? 1 : 0);
            panes.count = 0;
            if (!fromOutside && !seated && Quality.surfacesMirror(view.index)) {
                WindowGlass.find(cell, player, terrain, pose, panes);
            }
            IsoChunkMap map = cell.getChunkMap(playerIndex);
            int level = (int) Math.floor(player.getZ());
            // From an upper floor the ground is in the picture; from the ground, only the level under it.
            int lowest = level > 0 ? 0 : level - 1;
            lowestLevel = lowest;
            epoch = sum(sum(goesBy, lowest), playerIndex);
            boolean telling = told();
            ChunkMarks.live = telling;
            loadedNow = loadedOf(map, IsoChunkMap.chunkGridWidth);
            chunkMap = map;
            if (loadedNow != strungWith) {
                // A span left unknown at an end for want of map is found again now that the map has other chunks loaded.
                strung.values().removeIf(Spans::wanting);
                bannerRuns.values().removeIf(Banners::wanting);
                strungWith = loadedNow;
            }
            int loadedForAll = Rounds.loaded(view.index, loadedNow);
            MapAhead.tend(view.index, (int) Math.floor(player.getX()), (int) Math.floor(player.getY()));
            Terrain.loaded(loadedForAll);
            if (telling && roundBegins) {
                // The game's own scale: a scene's frame has none until its first build has got as far as setting it.
                Roofs.tend(cell, roundNow, Core.tileScale, loadedForAll);
                WallFinish.tend(cell, roundNow, loadedForAll);
            }
            // On the camera's way between the game's own view and the eye, whoever is indoors is
            // under a roof and the floors above. Those are left out while the camera is over
            // them, and the near walls of the floor are cut low: see Pipeline.cutAbove and lid.
            IsoGridSquare stoodOn = player.getCurrentSquare();
            // Under an arcade or a canopy the viewer is out of doors and still under a floor.
            IsoGridSquare roofed = fromOutside && stoodOn != null && !stoodOn.isOutside() ? stoodOn : null;
            for (int over = level + 1; fromOutside && roofed == null && stoodOn != null && over <= level + 8; over++) {
                IsoGridSquare above = cell.getGridSquare(stoodOn.getX(), stoodOn.getY(), over);
                if (above != null && above.getFloor() != null) {
                    roofed = above;
                }
            }
            BuildingDef def = roofed == null || roofed.getBuilding() == null ? null : roofed.getBuilding().getDef();
            boolean indoors = def != null;
            // From the eye in a building, its floors from two over the first that closes over the
            // viewer's storey are seen only up a stairwell, and from no window: they stand for
            // their shape and their shadow, and what the game's light does on them is not read
            // while the viewer is under them. So with its floors from three under the first under
            // the viewer, seen only down a stairwell. A hall several floors high puts that first
            // floor further off: see Lids.
            BuildingDef within = !fromOutside && stoodOn != null && !stoodOn.isOutside() && stoodOn.getBuilding() != null ? stoodOn.getBuilding().getDef() : null;
            if (within == null) {
                heldFrom = Integer.MAX_VALUE;
                heldUnder = Integer.MIN_VALUE;
                lidsOf = SpriteGeometry.NO_BUILDING;
            } else if (within.id != lidsOf || level != lidsAt || !lidsWhole && frames - lidsFrame >= LIDS_AGAIN) {
                lids(within, level);
            }
            heldBuilding = within == null ? SpriteGeometry.NO_BUILDING : within.id;
            if (within != null) {
                heldMinX = within.getX() - 1;
                heldMinY = within.getY() - 1;
                heldMaxX = within.getX() + within.getW() + 1;
                heldMaxY = within.getY() + within.getH() + 1;
            }
            int minX = 0, minY = 0, maxX = 0, maxY = 0;
            if (indoors) {
                minX = def.getX() - 1;
                minY = def.getY() - 1;
                maxX = def.getX() + def.getW() + 1;
                maxY = def.getY() + def.getH() + 1;
            }
            // As the game's own view, the picture leaves out what the game does, and the game is
            // asked what that is: the floors over the viewer's in the buildings it opens, which
            // are the viewer's own and those between its camera and the viewer or what the
            // viewer aims at, and everything over the viewer's level under the ground. This
            // runs as the game has just drawn this player's world, so what it holds is this frame's.
            gamesView = fromOutside && opened;
            openBuildingsNow.clear();
            if (gamesView) {
                openBuildingsNow.addAll(FBORenderCutaways.getInstance().getCollapsedBuildings());
            }
            // What every chunk's build goes by. Nothing of it is set while every building is built whole.
            int top = gamesView && (level < 0 || !openBuildingsNow.isEmpty()) ? level : Integer.MAX_VALUE;
            if (top != topLevel && (top < 0 || topLevel < 0)) {
                // Under the ground what is left out is left out of every chunk.
                forgetAll();
            } else {
                // Only the chunks an opened building stands on are built by it: one that was opened and one that is now.
                for (int i = 0; i < openBuildings.size(); i++) {
                    if (top != topLevel || !openBuildingsNow.contains(openBuildings.get(i))) {
                        forgetOver(openBuildings.get(i));
                    }
                }
                for (int i = 0; i < openBuildingsNow.size(); i++) {
                    if (top != topLevel || !openBuildings.contains(openBuildingsNow.get(i))) {
                        forgetOver(openBuildingsNow.get(i));
                    }
                }
            }
            topLevel = top;
            boolean sameOpen = openBuildings.equals(openBuildingsNow);
            openBuildings.clear();
            if (!openBuildingsNow.isEmpty()) {
                openBuildings.addAll(openBuildingsNow);
            }
            int stoodX = (int) Math.floor(player.getX()), stoodY = (int) Math.floor(player.getY());
            if (openBuildings.isEmpty()) {
                shelter = null;
            } else if (!sameOpen || shelter == null || shelter.strayedFrom(stoodX, stoodY)) {
                shelter = shelterRound(stoodX, stoodY);
            }
            sheltered = gamesView && level < 0 ? Shelter.EVERYWHERE : shelter;
            int eyeSquareX = (int) Math.floor(camera.eyeX), eyeSquareY = (int) Math.floor(camera.eyeY);
            if (roomAirKept == null || roomAirKept.staleFor(terrain, eyeSquareX, eyeSquareY)) {
                roomAirKept = RoomAir.round(IsoWorld.instance.getMetaGrid(), terrain, eyeSquareX, eyeSquareY);
            }
            roomAir = roomAirKept;
            float floorUp = level * ViewCamera.LEVEL_HEIGHT + terrain.lift(player.getX(), player.getY(), level);
            stoodUp = floorUp;
            cutFrom = gamesView || indoors ? floorUp + WALL_LEFT : Float.MAX_VALUE;
            cutBelow = 0f;
            if (gamesView) {
                // The walls of the viewer's floor that the game cuts away, for the squares round the viewer.
                cutBelow = floorUp + ViewCamera.LEVEL_HEIGHT;
                cutRowsX = (int) Math.floor(player.getX()) - Cutaways.SIDE / 2;
                cutRowsY = (int) Math.floor(player.getY()) - Cutaways.SIDE / 2;
                Cutaways.clear(cutRows);
                long now = System.currentTimeMillis();
                IsoGridSquare viewerSquare = player.getCurrentSquare();
                IsoRoom viewerRoom = viewerSquare == null ? null : viewerSquare.getRoom();
                for (int row = 0; row < Cutaways.SIDE; row++) {
                    for (int column = 0; column < Cutaways.SIDE; column++) {
                        IsoGridSquare square = cell.getGridSquare(cutRowsX + column, cutRowsY + row, level);
                        int marks = square == null ? 0 : square.getPlayerCutawayFlag(playerIndex, now);
                        if (marks != 0) {
                            Cutaways.put(cutRows, column, row, marks, Cutaways.kept(marks, viewerSquare != null && square.getRoom() == viewerRoom,
                                    square.has(IsoFlagType.cutN), square.has(IsoFlagType.cutW)));
                        }
                    }
                }
            }
            sightRead = fromOutside && !Quality.overheadPlain(view.index);
            if (sightRead) {
                readSight(cell, map, level, lowest, stoodX, stoodY, screenOnly);
            }
            lidFrom = indoors && !opened ? floorUp + ViewCamera.LEVEL_HEIGHT - LID_UNDER : Float.MAX_VALUE;
            boxMinX = minX;
            boxMinY = minY;
            boxMaxX = maxX;
            boxMaxY = maxY;
            frame.scale = Core.tileScale;
            roomsDaylit = !gamesView && !Quality.plain(view.index);
            day = Math.round(Evening.day(sky.sunUp) * DAY_STEPS) / (float) DAY_STEPS;
            roomsDaylitNow = roomsDaylit;
            dayNow = day;
            for (int i = 0; i < 3; i++) {
                float open = i == 0 ? unseenRed : i == 1 ? unseenGreen : unseenBlue;
                dayLight[i] = roomsDaylit ? Math.round(day * open * DAY_STEPS) / (float) DAY_STEPS : 0f;
            }
            dayRedNow = dayLight[0];
            dayGreenNow = dayLight[1];
            dayBlueNow = dayLight[2];
            roomMap.begin(cell, terrain);
            daylitAt = start;
            // Until a room has been in sight, a room is lit as the open ground is, and one with a lamp burning at least as a lamp lights.
            float share = roomShare < 0f ? 1f : roomShare;
            float lamp = lampLevel < 0f ? LAMP_AT_LEAST : lampLevel;
            float eased = unlitEasedAt == 0L ? 1f : 1f - (float) Math.exp(-(start - unlitEasedAt) / 1e9 / UNLIT_EASE_SECONDS);
            unlitEasedAt = start;
            float dayNowFull = roomsDaylit ? Evening.day(sky.sunUp) : 0f;
            for (int i = 0; i < 3; i++) {
                float open = i == 0 ? unseenRed : i == 1 ? unseenGreen : unseenBlue, room = Math.min(open * share, 1f);
                // The open ground's light with no lamp on it, which the world shader reads the daylight against: lit as
                // the commonest, brighter where lamps light much of the street, an eave or a roof the game gives no light
                // had that much over the daylight, which the shader took for a lamp's, warm and never held back at night.
                float bare = i == 0 ? floorRed : i == 1 ? floorGreen : floorBlue;
                openUnlitEased[i] += (bare - openUnlitEased[i]) * eased;
                daylightEased[i] += (dayNowFull * open - daylightEased[i]) * eased;
                roomLightEased[i] += (room - roomLightEased[i]) * eased;
                lampLightEased[i] += (Math.max(room, lamp) - lampLightEased[i]) * eased;
            }
            System.arraycopy(openUnlitEased, 0, sky.openUnlit, 0, 3);
            System.arraycopy(daylightEased, 0, sky.daylight, 0, 3);
            System.arraycopy(roomLightEased, 0, sky.roomLight, 0, 3);
            System.arraycopy(lampLightEased, 0, sky.lampLight, 0, 3);

            // The loaded map is a square of chunks, and it ends in nothing. The picture fades into the
            // sky by the round that just fits inside it, so the edge is never seen. The square is not
            // always about the player: the game keeps it ahead of a vehicle, a tile for every 5 km/h,
            // and the road ahead of a car at speed is there to be seen for further than what is behind.
            loadedWithin = IsoChunkMap.chunkGridWidth * CHUNK_TILES / 2f;
            loadedX = map.getWorldXMinTiles() + loadedWithin;
            loadedY = map.getWorldYMinTiles() + loadedWithin;
            fogEnd = Quality.sight(view.index);
            drawDistance.take(view.index, pose.eyeX, pose.eyeY, seated ? view.ride.aheadX : 0f, seated ? view.ride.aheadY : 0f, Online.contested());
            // On the camera's way between the views as well, so the far land is there when it comes into sight.
            if (!fromOutside || !view.flight.home()) {
                farLand.tend(pose.eyeX, pose.eyeY);
            }
            float cosCone = fromOutside ? -1f : cosDrawnCone(pose, flat);
            // A driver's mirror shows a narrow cone straight behind the vehicle, and what is in it is drawn as well.
            boolean mirrored = !fromOutside && seated && Quality.reflections(view.index) && Cabins.wheel(view.ride) != null;
            float behindX = -view.ride.aheadX, behindY = -view.ride.aheadY;
            boolean glassShown = !fromOutside && !seated && view.pipeline.lookingGlassShown();
            // Looking straight up or down the view faces no way along the ground, and nothing is left out.
            float forwardX = cosCone > -1f ? pose.forwardX / flat : 0f, forwardY = cosCone > -1f ? pose.forwardY / flat : 0f;

            mapWx = Math.floorDiv(map.getWorldXMinTiles(), CHUNK_TILES);
            mapWy = Math.floorDiv(map.getWorldYMinTiles(), CHUNK_TILES);
            mapSide = IsoChunkMap.chunkGridWidth;
            // Before the chunks are built, so that a room the eye has just come to see is built in this frame.
            seeRooms(map, level, fromOutside, start);
            if (frames % FORGET_EVERY_FRAMES == 0) {
                keptRoundX = pose.eyeX;
                keptRoundY = pose.eyeY;
                forgetFar();
            }
            // Nearest first. When the frame has spent its time on building, what waits is then what is
            // furthest off, all round, where walked a row at a time it was one side of the picture.
            int side = IsoChunkMap.chunkGridWidth;
            int[] walk = ChunkBuffers.nearestFirst(side);
            int eyeGridX = Math.floorDiv((int) Math.floor(pose.eyeX) - map.getWorldXMinTiles(), CHUNK_TILES);
            int eyeGridY = Math.floorDiv((int) Math.floor(pose.eyeY) - map.getWorldYMinTiles(), CHUNK_TILES);
            int builtLate = 0, changedLate = 0;
            float nearestWaiting = Float.MAX_VALUE;
            long aheadBegan = 0L;
            int swept = 0;
            for (Crowns.Work back = crownsBack.poll(); back != null; back = crownsBack.poll()) {
                land(back);
            }
            // The chunks in view, and after them the nearest one the view does not face that has no build: see where it is found aside.
            int aheadStep = -1;
            for (int step = 0; step <= walk.length; step += 2) {
                boolean ahead = step == walk.length;
                if (ahead && (aheadStep < 0 || !Sightline.buildsAhead(view.index, withoutSightline) || rehearsing || chunksWaiting > 0 || waitedLast > 0
                        || System.nanoTime() - start > BUILD_NANOS_A_FRAME / sharing)) {
                    break;
                }
                {
                    int walked = ahead ? aheadStep : step;
                    int gridX = eyeGridX + walk[walked], gridY = eyeGridY + walk[walked + 1];
                    if (gridX < 0 || gridY < 0 || gridX >= side || gridY >= side) {
                        continue;
                    }
                    IsoChunk chunk = map.getChunk(gridX, gridY);
                    if (chunk == null || !chunk.loaded) {
                        continue;
                    }
                    int chunkX = chunk.wx * CHUNK_TILES, chunkY = chunk.wy * CHUNK_TILES;
                    float toChunkX = chunkX + CHUNK_TILES / 2f - pose.eyeX, toChunkY = chunkY + CHUNK_TILES / 2f - pose.eyeY;
                    float distance = (float) Math.sqrt(toChunkX * toChunkX + toChunkY * toChunkY);
                    if (distance > ChunkBuffers.drawnWithin(fogEnd)
                            || Math.hypot(chunkX + CHUNK_TILES / 2f - loadedX, chunkY + CHUNK_TILES / 2f - loadedY) > ChunkBuffers.drawnWithin(loadedWithin)) {
                        continue;
                    }
                    // Drawn as the game's own view, the picture shows a patch of the map round the middle of the screen and no more.
                    if (screenOnly && !ChunkBuffers.onScreen(chunkX + CHUNK_TILES / 2f - seenRoundX, chunkY + CHUNK_TILES / 2f - seenRoundY,
                            chunk.getMinLevel() - level, chunk.getMaxLevel() - level, seenAlongX, seenAlongY, seenHalfAcross, seenHalfAlong, seenLevelShift,
                            ChunkBuffers.shadowReach(sky.lightUp, Math.max(TALL_AS_A_HOUSE, (chunk.getMaxLevel() + 1) * ViewCamera.LEVEL_HEIGHT)))) {
                        continue;
                    }
                    boolean aside = distance > CASTS_SHADOWS && toChunkX * forwardX + toChunkY * forwardY < cosCone * distance - CHUNK_REACH
                            && !(mirrored && toChunkX * behindX + toChunkY * behindY >= COS_MIRROR_CONE * distance - CHUNK_REACH);
                    int slot = (chunk.wx & (TABLE_SIDE - 1)) * TABLE_SIDE + (chunk.wy & (TABLE_SIDE - 1));
                    ChunkMeshes current = latest[slot];
                    boolean missing = current == null || current.wx != chunk.wx || current.wy != chunk.wy;
                    if (aside != ahead) {
                        if (aside && missing && aheadStep < 0) {
                            aheadStep = step;
                        }
                        continue;
                    }
                    if (aside) {
                        // What the view does not face is not drawn, and a head turns to it faster than its chunks can be
                        // built. So one that has no build is given it ahead of the turn: one a frame, after everything in
                        // view, in a frame that left nothing in view waiting and has not spent its time for building.
                        // It is handed to the graphics card with the frame and kept as any build is.
                        aheadBegan = System.nanoTime();
                    } else {
                        chunksDrawn++;
                    }
                    // A chunk that is only due to be made again can wait a frame when many fall due
                    // together: its old build is still good enough to show. Without this the frames
                    // on which dozens fall due are several times as long as the rest.
                    int every = missing ? 0 : buildEvery(distance, slot);
                    // On the frame the view comes on, after a jump across the map or as the head turns
                    // fast, hundreds of chunks want building at once, and built in one frame they held it
                    // for most of a second. What is near is built whatever it takes. The rest is built
                    // until the frame has spent its time on building, and the others come on the next frames.
                    boolean late = !aside && (rehearsing || distance >= BUILT_WHATEVER_IT_TAKES) && System.nanoTime() - start > BUILD_NANOS_A_FRAME / sharing;
                    // One chunk a frame is built however late the frame is: on a PC where what is near takes all
                    // of the frame's time, nothing further off would otherwise ever be built.
                    if (missing && late && builtLate++ > 0) {
                        chunksWaiting++;
                        nearestWaiting = Math.min(nearestWaiting, distance);
                        continue;
                    }
                    // While chunks wait for their first build, the time goes to them: one built already
                    // and not right by the eye is shown as it is, or those at the end of the walk would
                    // wait for as long as the ones before them kept falling due.
                    boolean hurried = late || waitedLast > 0;
                    // The numbers of frames were set at a hundred frames a second. On a faster PC a
                    // chunk waits out the time they stood for as well, or the faster the picture is
                    // drawn the more of every frame goes on building it. And one right by an eye
                    // that is still, found unchanged, is not read through again for a thirtieth of
                    // a second, where it was read through on every frame.
                    long since = start - (missing ? 0L : current.checkedNanos);
                    // A head that is tracked is never where it was to the last bit, and neither is a
                    // player standing in a crowd. Of where the eye is a build holds only its band, so
                    // it still stands while the eye is in the band, or in another the build would
                    // have come out the same for: then only its crowns are made again.
                    // A build with no sum cannot be found unchanged: while the eye moves it is made again
                    // in its turn, as one the eye has left behind is, and not at once.
                    float nearest = nearest(pose, chunkX + CHUNK_TILES / 2f, chunkY + CHUNK_TILES / 2f);
                    if (!missing && !inBand(current.band, nearest) && (current.alike >> band(nearest) & 1) != 0 && !withoutAlikeBands) {
                        reband(current, slot, band(nearest));
                    }
                    boolean stands = !missing && inBand(current.band, nearest);
                    boolean still = missing ? eyeStill : stands && (eyeStill || current.signed);
                    // A build that is held is only read through to see whether its chunk has changed: half as often will do,
                    // and a change twenty tiles off shows a tenth of a second later.
                    boolean held = !missing && still && current.signed;
                    boolean waited = missing || (every > 1 ? since >= every * NANOS_A_FRAME_COUNTED * (held ? 2 : 1)
                            : held ? since >= UNCHANGED_READ_EVERY_NANOS : !stands || since >= CHANGING_BUILT_EVERY_NANOS);
                    // With the game telling of its changes, a chunk nothing has been said of is not read:
                    // not marked by the game, nor told of by the mod, nor waiting on the map to load
                    // round it, with its heights and what every build goes by as they were. It is still
                    // read every so often (sweptEvery), and for its first seconds as it always was,
                    // while what loads late comes in.
                    int land = 0;
                    if (telling && !missing) {
                        pullLights(chunk, slot, lowest);
                    }
                    if (!missing) {
                        terrain.corners(cell, chunk.wx, chunk.wy);
                        land = landOf(chunk.wx, chunk.wy, current.heightsRound, current.heightsFar, current.heightsFar.length, current.wantsMap);
                    }
                    // Read before the chunk is: a mark made from another thread while it is read is then one more than the build remembers.
                    int marks = ChunkMarks.around(chunk.wx, chunk.wy);
                    int things = ChunkMarks.thingsAround(chunk.wx, chunk.wy);
                    int toldSoFar = ChunkMarks.told(chunk.wx, chunk.wy);
                    // The map has loaded or let go of a chunk since: whether one beside this chunk, the same asking says.
                    boolean ringAsItWas = missing || current.loaded == loadedNow;
                    if (telling && !ringAsItWas) {
                        surround(map, chunk, gridX, gridY, side);
                        ringAsItWas = ring == current.ring;
                        if (ringAsItWas) {
                            current.loaded = loadedNow;
                        }
                    }
                    // A chunk that has only just come onto the map is built again for what loads late a quarter of a second after it was
                    // last, and later at twice its age: between those it is left alone as any other is.
                    boolean settled = !missing && (start - current.bornNanos >= SETTLES_IN_NANOS
                            || start - current.builtNanos < (distance < BUILT_EVERY_FRAME ? KEPT_WHILE_SETTLING_NANOS : Math.max(KEPT_WHILE_SETTLING_NANOS, (start - current.bornNanos) / 2)));
                    boolean landChanged = !missing && terrain.version(chunk.wx, chunk.wy) != current.terrainVersion;
                    boolean quiet = telling && !missing && !landChanged && stands && current.signed && !current.restless && marks == current.marks && toldSoFar == current.told
                            && current.epoch == epoch && seasonHolds(current, slot) && current.gamesView == gamesView && land == current.land && ringAsItWas && settled;
                    // Only so many a frame are read for the time that has passed: a head that turns back to a street finds every chunk of it overdue at once.
                    if (quiet && since >= sweptEvery(distance, slot) && swept++ < SWEPT_A_FRAME) {
                        quiet = false;
                    }
                    if (quiet) {
                        chunksQuiet++;
                        if (AUDIT_STALE && (frames + slot) % AUDIT_STALE_EVERY == 0) {
                            surround(map, chunk, gridX, gridY, side);
                            auditStale(chunk, chunkX, chunkY, lowest, slot, current, distance, start, toldSoFar, land);
                        }
                    }
                    // Dev only: what has a chunk read that would have been left alone, for the audit's line.
                    int why = !AUDIT_MARKS || !telling || missing ? 0 : (marks != current.marks ? 1 : 0) | (toldSoFar != current.told ? 2 : 0) | (current.epoch != epoch ? 4 : 0)
                            | (land != current.land ? 8 : 0) | (!ringAsItWas ? 16 : 0) | (current.restless ? 32 : 0)
                            | (!settled ? 64 : 0) | (!quiet && since >= sweptEvery(distance, slot) ? 128 : 0) | (!current.signed ? 256 : 0) | (!stands ? 512 : 0);
                    // One told of a room come into the rooms built or gone (ChunkMarks.tell) is due at once: the room may be
                    // coming into the picture, and waiting out its turn left the room empty there for up to 54 frames.
                    boolean toldOfRoom = !missing && toldSoFar != current.told;
                    // One that has waited twice its time waits no longer: the chunks are walked nearest first,
                    // and the last of them would wait as long as those before it kept falling due.
                    boolean due = landChanged && distance < BUILT_WHATEVER_IT_TAKES
                            || !missing && !quiet && !rehearsing && (toldOfRoom || waited && frames - current.checkedFrame >= every
                            && (distance < CHUNK_TILES * 1.5f || frames - current.checkedFrame >= 2 * every && !hurried
                                    || frames - current.checkedFrame >= 4 * every && !late || !hurried && remakes++ < REMAKES_A_FRAME));
                    // On arriving somewhere the map round the eye is still loading, and each chunk is found changed again
                    // and again. One found changed is made again as it falls due without being read through, and right by
                    // the eye that is every frame: the nine chunks round it were all made again on every frame for the
                    // first seconds, which with builds slowed fourfold was seven frames a second for five seconds. While
                    // a chunk is new, a quarter of a second between two builds of it is soon enough, and
                    // the build it has stands for as long as the eye is in its band, whether or not the eye moves.
                    if (due && !landChanged && stands && (frames < current.summedFrom || start < current.summedFromNanos)
                            && start - current.bornNanos < SETTLES_IN_NANOS
                            && start - current.builtNanos < KEPT_WHILE_SETTLING_NANOS) {
                        due = false;
                    }
                    // Nothing but light has been marked on it since it was last read, and the frame has
                    // spent its time: the build it has is right but for that light, and is shown a frame or a few longer.
                    // Not one told of a room come into the rooms built or gone (ChunkMarks.tell): its contents would wait.
                    if (due && !landChanged && telling && stands && current.signed && current.things == things && current.told == toldSoFar && since < LIT_WAITS_NANOS
                            && System.nanoTime() - start > LIT_NANOS_A_FRAME / sharing) {
                        due = false;
                    }
                    // Nothing but light has been marked on it and the view does not face it: its light is in no picture,
                    // and its shadows are of its shape. The game's sight goes round with the mouse and darkens what it
                    // leaves, so a mouse turn marked a third of the chunks in reach and they were built again every
                    // few frames; with the view back on one, the game's light is mostly what its build was made with,
                    // and its sum finds it unchanged.
                    // A mirror in a room shows what is behind the eye: while one shows, every chunk built here counts as faced.
                    boolean faced = cosCone <= -1f || toChunkX * forwardX + toChunkY * forwardY >= cosCone * distance - CHUNK_REACH
                            || mirrored && toChunkX * behindX + toChunkY * behindY >= COS_MIRROR_CONE * distance - CHUNK_REACH
                            || glassShown;
                    boolean onlyLit = telling && !missing && !landChanged && stands && current.signed && !current.restless && settled && ringAsItWas
                            && marks != current.marks && current.things == things && current.told == toldSoFar && current.epoch == epoch
                            && seasonHolds(current, slot) && current.gamesView == gamesView && current.land == land
                            && current.litLoose == ChunkMarks.litLooseAround(chunk.wx, chunk.wy);
                    if (due && !faced && onlyLit) {
                        due = false;
                        tollLitUnfaced++;
                    }
                    // What the build of a chunk that is due would read of the map is summed first,
                    // and a chunk whose sum is what its build's was, with the eye still in the
                    // build's band, is kept until it is as old as a build is kept: a new build
                    // would be the same triangles.
                    long read = 0;
                    int cause = NEW;
                    // Whether the chunk was summed and found to be what its build was made from.
                    boolean unchanged = false;
                    int summedFrom = missing ? frames : current.summedFrom;
                    long summedFromNanos = missing ? start : current.summedFromNanos;
                    // Every build is summed, whether or not the eye moves, but for one of a chunk
                    // found changed a moment ago: see summedFrom.
                    float middleX = chunkX + CHUNK_TILES / 2f, middleY = chunkY + CHUNK_TILES / 2f;
                    // One put off for want of time is not read again while the frame still has none: see where one is put off.
                    if (due && !missing && current.putOffNanos != 0L && start - current.putOffNanos < CHANGED_WAITS_NANOS && changedLate > 0
                            && (distance >= CHUNK_TILES * 1.5f || current.things == things) && System.nanoTime() - start > BUILD_NANOS_A_FRAME / sharing) {
                        due = false;
                        tollChangedWaited++;
                    }
                    boolean summed = (missing || due) && frames >= summedFrom && start >= summedFromNanos;
                    if (missing || due) {
                        surround(map, chunk, gridX, gridY, side);
                    }
                    if (summed) {
                        long summing = System.nanoTime();
                        // Read for nothing but light the game marked, which it marks by its level: the other levels are as they were summed.
                        boolean lightAlone = telling && !missing && !AUDIT_MARKS && stands && current.signed && !current.restless && settled && ringAsItWas
                                && marks != current.marks && current.things == things && current.told == toldSoFar && current.epoch == epoch && seasonHolds(current, slot) && current.land == land
                                && current.litLoose == ChunkMarks.litLooseAround(chunk.wx, chunk.wy) && start - current.wholeNanos < sweptEvery(distance, slot);
                        read = signature(chunk, chunkX, chunkY, lowest, lightAlone ? current : null, current);
                        if (CHECK_BUILDS && readByLevel) {
                            long[] byLevel = Arrays.copyOf(readSums, readTo - readFrom + 1);
                            read = signature(chunk, chunkX, chunkY, lowest, null, null);
                            tollSumsChecked++;
                            for (int z = readFrom; z <= readTo; z++) {
                                if (byLevel[z - readFrom] == readSums[z - readFrom]) {
                                    continue;
                                }
                                // A light the whole reading was the first to ask for is marked by that asking, and the level is read the next time.
                                boolean markedSince = ChunkMarks.litAround(chunk.wx, chunk.wy, z) != readLit[z - readFrom];
                                if (markedSince) {
                                    tollSumsLate++;
                                } else {
                                    tollSumsDiffered++;
                                }
                                Log.info(String.format(Locale.ROOT, "chunk sums: level %d of the chunk at %d, %d, kept as it was summed, is another sum read now, %s", z, chunk.wx, chunk.wy,
                                        markedSince ? "and that reading marked its light" : "WITH NOTHING MARKED"));
                            }
                        }
                        slowed(summing);
                        tollSums++;
                        tollSumNanos += System.nanoTime() - summing;
                        boolean sameEye = still && due && current.signed;
                        if (AUDIT_MARKS) {
                            for (int bit = 0; bit < tollWhy.length; bit++) {
                                tollWhy[bit] += why >> bit & 1;
                            }
                        }
                        if (AUDIT_MARKS && sameEye) {
                            if (current.signature != read) {
                                audit(chunk, current, distance, start, why);
                                if (distance < 40f && auditLogged++ < 4000) {
                                    Log.info(String.format(Locale.ROOT, "chunk marks: changed at %d, %d, %.0f tiles off, read because %d, last read %.0f ms ago", chunk.wx, chunk.wy, distance, why,
                                            since / 1e6));
                                }
                            } else if (marks != current.marks) {
                                tollMarkedSame++;
                            }
                        }
                        // The same triangles: the same sum, and nothing a build is told that its sum does not hold is otherwise.
                        // The sum holds the chunk's own heights. Those of the chunks round it that its build went by are in land alone.
                        boolean same = !missing && current.signature == read && current.landSum == readLand && current.told == toldSoFar && current.epoch == epoch && seasonHolds(current, slot) && current.land == land;
                        // Driving, the map loads a row of chunks every few frames, and the heights of every chunk
                        // within a valley's width of it are worked out again: most move by a few hundredths of a
                        // tile, and each passes its edges on to the chunks beside it. Far off, a build whose heights
                        // have moved by less than the picture can show is that build still.
                        if (!same && !missing && current.signed && distance >= BUILT_WHATEVER_IT_TAKES && current.signature == read && current.told == toldSoFar
                                && current.epoch == epoch && seasonHolds(current, slot) && (!current.wantsMap || current.loaded == loadedNow)
                                && heightsHeld(current, distance * tanHalfFieldOfView / HELD_ROWS)) {
                            same = true;
                            tollHeightsHeld++;
                        }
                        unchanged = same;
                        // A chunk that has only just come onto the map far off is made again for what loads late
                        // after a quarter of a second, then a half, then one: at a car's speed a dozen builds each
                        // of every chunk coming over the horizon were half of all the building there was.
                        long settling = distance < BUILT_EVERY_FRAME || missing ? KEPT_WHILE_SETTLING_NANOS
                                : Math.max(KEPT_WHILE_SETTLING_NANOS, (start - current.bornNanos) / 2);
                        long kept = telling ? KEPT_TOLD_NANOS : current == null || !current.plain ? KEPT_UNCHANGED_NANOS : KEPT_PLAIN_NANOS;
                        boolean young = !missing && start - current.builtNanos < (start - current.bornNanos < SETTLES_IN_NANOS ? settling : kept);
                        // One aged with others waits for a frame with fewer: its build is as good as it was a frame
                        // ago. Not one that is new, which is built again for what loads late, and not one that has
                        // waited its time over again: the nearer ones age as fast as they are built, and would always be first.
                        if (!young && due && sameEye && same && start - current.bornNanos >= SETTLES_IN_NANOS
                                && start - current.builtNanos < 2 * kept && agedBuilt++ >= AGED_A_FRAME) {
                            young = true;
                        }
                        if (due) {
                            cause = !still ? MOVED : !current.signed ? UNSIGNED : !same ? CHANGED : young ? KEPT_ON : AGED;
                            // One found changed that is not right by the eye is shown as it was built while the
                            // frame has spent its time: a turn has the game show a tower's rooms and finds a dozen
                            // of its chunks changed at once, each of thirty storeys, and built together they held
                            // the frame for a tenth of a second. So is one right by the eye that nothing but its
                            // light was marked on: the game lights again the chunks round where the character
                            // stands as it turns, and eight of a tower's built together held a frame for 70 ms.
                            // One a frame is made however late the frame is.
                            if (cause == CHANGED && (distance >= CHUNK_TILES * 1.5f || current.things == things)
                                    && (current.putOffNanos == 0L || start - current.putOffNanos < CHANGED_WAITS_NANOS)
                                    && System.nanoTime() - start > BUILD_NANOS_A_FRAME / sharing && changedLate++ > 0) {
                                due = false;
                                if (current.putOffNanos == 0L) {
                                    current.putOffNanos = start;
                                }
                                tollChangedWaited++;
                            } else {
                                builtBecause[cause]++;
                            }
                        }
                        if (sameEye && same && young) {
                            current.putOffNanos = 0L;
                            current.checkedFrame = frames;
                            current.checkedNanos = start;
                            current.marks = marks;
                            current.things = things;
                            current.keepLevels(this, start, current);
                            current.land = land;
                            current.landSum = readLand;
                            current.terrainVersion = terrain.version(chunk.wx, chunk.wy);
                            current.ring = ring;
                            current.loaded = loadedNow;
                            current.restless = restless;
                            current.gamesView = gamesView;
                            chunksKept++;
                            due = false;
                        } else {
                            // With the game telling, a chunk found changed is read again when it is next marked, and one that is restless is read as it was without.
                            if (sameEye && current.signature != read && (!telling || restless)) {
                                // Something on the chunk changes from frame to frame: a light that
                                // flickers, a sprite that moves. Summing it each time it is due
                                // would be work for nothing, so it is left for a while.
                                summedFrom = frames + KEPT_UNCHANGED_FRAMES;
                                summedFromNanos = start + KEPT_UNCHANGED_FRAMES * NANOS_A_FRAME_COUNTED;
                            }
                        }
                    }
                    if (missing) {
                        cause = NEW;
                        builtBecause[NEW]++;
                    } else if (due && !summed) {
                        cause = still ? LEFT_UNREAD : MOVED;
                        builtBecause[cause]++;
                    }
                    if (missing || due) {
                        ChunkMeshes fresh = spare.isEmpty() ? new ChunkMeshes(view.index) : spare.pop();
                        solid.clear();
                        laid.clear();
                        // Shared by every view: a build that threw half way through a layer turned
                        // its own view off, and the other's builds are not to inherit its count.
                        layers.none();
                        fresh.placed.clear();
                        if (SightTrace.ON) {
                            if (fresh.traced == null) {
                                fresh.traced = Collections.newSetFromMap(new IdentityHashMap<>());
                                fresh.modelled = Collections.newSetFromMap(new IdentityHashMap<>());
                                fresh.pictured = Collections.newSetFromMap(new IdentityHashMap<>());
                            }
                            fresh.traced.clear();
                            fresh.modelled.clear();
                            fresh.pictured.clear();
                        }
                        fresh.wx = chunk.wx;
                        fresh.wy = chunk.wy;
                        fresh.builtFrame = frames;
                        fresh.checkedFrame = frames;
                        fresh.checkedNanos = start;
                        fresh.builtNanos = start;
                        fresh.bornNanos = missing ? start : current.bornNanos;
                        fresh.putOffNanos = 0L;
                        fresh.signed = summed;
                        fresh.signature = read;
                        fresh.landSum = readLand;
                        fresh.summedFrom = summedFrom;
                        fresh.summedFromNanos = summedFromNanos;
                        fresh.plain = true;
                        fresh.marks = marks;
                        fresh.things = things;
                        if (summed) {
                            fresh.keepLevels(this, start, current);
                        } else {
                            fresh.sumTo = fresh.sumFrom - 1;
                        }
                        fresh.epoch = epoch;
                        fresh.ring = ring;
                        fresh.loaded = loadedNow;
                        fresh.restless = summed && restless;
                        fresh.gamesView = gamesView;
                        if (AUDIT_MARKS) {
                            auditKeep(fresh, summed);
                        }
                        fresh.users = 1;
                        fresh.band = band(nearest);
                        crownsRead.clear();
                        fresh.grows = false;
                        fresh.seasonal = false;
                        fresh.stage = stage;
                        bandAway = BAND_AWAY[fresh.band];
                        alike = ALL_BANDS;
                        heightsRound = 0;
                        heightsFarCount = 0;
                        wantsMap = false;
                        placed = fresh.placed;
                        building = fresh;
                        chunksBuilt++;
                        growing = false;
                        long began = System.nanoTime();
                        addSquares(chunk, chunkX, chunkY, lowest);
                        kerbBuilder.noKerb();
                        roofBuilder.addBacks(chunk, chunkX, chunkY);
                        fresh.plain &= !canopied;
                        // As it was before the build. A build may itself tell its own chunk, part way through: the roof first
                        // read from one of its squares lays slopes over squares the build has already passed, which asked
                        // while there was none. The chunk is then told still, and is built once more.
                        fresh.told = toldSoFar;
                        fresh.heightsRound = heightsRound;
                        fresh.heightsFar = heightsFarCount == 0 ? NO_CHUNKS : Arrays.copyOf(heightsFar, heightsFarCount);
                        fresh.wantsMap = wantsMap;
                        fresh.land = landOf(chunk.wx, chunk.wy, heightsRound, heightsFar, heightsFarCount, wantsMap);
                        heightsSeen(fresh, chunk.wx, chunk.wy);
                        if (fresh.seen == null) {
                            fresh.seen = new OpenLight();
                            fresh.seenRooms = new OpenLight();
                            fresh.seenLamplit = new OpenLight();
                        }
                        reading.copyTo(fresh.seen);
                        readingRooms.copyTo(fresh.seenRooms);
                        readingLamplit.copyTo(fresh.seenLamplit);
                        if (AUDIT_STALE) {
                            fresh.auditFloats = floatsOf(solid) * 31 + floatsOf(laid);
                            fresh.auditMeshes = meshesOf();
                        }
                        if (AUDIT_MARKS) {
                            fresh.auditFloats = floatsOf(solid) * 31 + floatsOf(laid);
                            boolean aged = current != null && summed && current.signed && current.signature == read && current.band == fresh.band && fresh.signed
                                    && current.epoch == epoch && current.told == toldSoFar && ChunkMarks.told(chunk.wx, chunk.wy) == toldSoFar;
                            if (aged && current.auditFloats != fresh.auditFloats) {
                                tollAgedDiffered++;
                                if (auditLogged++ < 4000) {
                                    Log.info(String.format(Locale.ROOT, "chunk marks: a build of %d, %d with the same sum and the same eye came out otherwise, %.1f s old, last built %.2f s ago, told %d then %d now, plain %b",
                                            chunk.wx, chunk.wy, (start - current.bornNanos) / 1e9, (start - current.builtNanos) / 1e9, current.told, fresh.told, current.plain));
                                }
                            } else if (aged) {
                                tollAgedSame++;
                                tollAgedSamePlain += current.plain ? 1 : 0;
                            }
                        }
                        fresh.grown = false;
                        fresh.alike = alike;
                        if (layers.lying()) {
                            throw new IllegalStateException("the build of the chunk at " + chunk.wx + ", " + chunk.wy + " ended with its layers still counted");
                        }
                        if (CHECK_BUILDS) {
                            check(chunk, chunkX, chunkY, lowest, fresh);
                        }
                        fresh.fill(solid, laid);
                        slowed(began);
                        long took = System.nanoTime() - began;
                        tollBuildNanos += took;
                        tollBuiltNanos[cause] += took;
                        if (cause == MOVED) {
                            if (inBand(current.band, nearest)) {
                                tollMovedUnsigned++;
                            } else {
                                tollMovedBand++;
                            }
                        }
                        if (current != null) {
                            release(current);
                        }
                        latest[slot] = fresh;
                        current = fresh;
                        crown(slot, chunk.wx, chunk.wy, unchanged && (cause == MOVED || cause == AGED));
                    }
                    current.users++;
                    current.water = Terrain.holdsWater(chunk.wx, chunk.wy);
                    if (aside) {
                        builtAheadOf.add(current);
                    } else {
                        shown.add(current);
                        if (SightTrace.ON) {
                            current.shownFrame = frames;
                        }
                        waterShown |= current.water;
                    }
                    ChunkMeshes crowned = latestCrowned[slot];
                    if (crowned != null && crowned.wx == chunk.wx && crowned.wy == chunk.wy) {
                        crowned.users++;
                        (aside ? builtAheadOf : shown).add(crowned);
                    }
                    // What grows on the chunk's ground is made again with the chunk, and by itself
                    // as the eye moves while any of it is near enough to be made by how far off it is.
                    ChunkMeshes grown = latestGrown[slot];
                    boolean ungrown = grown == null || grown.wx != chunk.wx || grown.wy != chunk.wy || grown.builtFrame < current.builtFrame;
                    // A lawn's blades shorten over seven tiles and a half: made from half a tile off they are a fifteenth out.
                    float grownFrom = grown == null ? 0f
                            : Math.max(Math.max(Math.abs(grown.eyeX - pose.eyeX), Math.abs(grown.eyeY - pose.eyeY)), Math.abs(grown.eyeUp - pose.eyeUp));
                    boolean regrown = !ungrown && !rehearsing && distance < GROWN_WITHIN && frames - grown.checkedFrame >= every
                            && grownFrom > Math.max(GROWN_HELD_WITHIN, heldWithin(pose, distance, middleX, middleY));
                    if (!current.grows && grown != null && grown.wx == chunk.wx && grown.wy == chunk.wy) {
                        release(grown);
                        latestGrown[slot] = null;
                        grown = null;
                    }
                    // What grows is made with its chunk. Made again only because the eye has moved, it waits for a frame with the time.
                    if (Own.plants(view.index) && current.grows && (ungrown || regrown && !late)) {
                        if (!missing && !due) {
                            surround(map, chunk, gridX, gridY, side);
                        }
                        ChunkMeshes fresh = spare.isEmpty() ? new ChunkMeshes(view.index) : spare.pop();
                        solid.clear();
                        laid.clear();
                        fresh.placed.clear();
                        fresh.wx = chunk.wx;
                        fresh.wy = chunk.wy;
                        fresh.builtFrame = frames;
                        fresh.checkedFrame = frames;
                        fresh.eyeX = pose.eyeX;
                        fresh.eyeY = pose.eyeY;
                        fresh.eyeUp = pose.eyeUp;
                        fresh.grown = true;
                        fresh.users = 1;
                        placed = fresh.placed;
                        building = current;
                        growing = true;
                        chunksGrown++;
                        long began = System.nanoTime();
                        addSquares(chunk, chunkX, chunkY, 0);
                        growing = false;
                        kerbBuilder.noKerb();
                        if (AUDIT_STALE) {
                            fresh.auditFloats = floatsOf(solid) * 31 + floatsOf(laid);
                        }
                        fresh.fill(solid, laid);
                        slowed(began);
                        tollGrown++;
                        tollGrownNanos += System.nanoTime() - began;
                        if (grown != null) {
                            release(grown);
                        }
                        latestGrown[slot] = fresh;
                        grown = fresh;
                    }
                    if (grown != null && grown.wx == chunk.wx && grown.wy == chunk.wy) {
                        grown.users++;
                        (aside ? builtAheadOf : shown).add(grown);
                    }
                    if (aside) {
                        tollAhead++;
                        tollAheadNanos += System.nanoTime() - aheadBegan;
                    }
                }
            }
            addCharacters(player, pose);
            takeLight(start);
            if (DepthFights.ON && !fromOutside) {
                DepthFights.audit(shown, pose.eyeX, pose.eyeY, pose.eyeUp, cell);
            }
            for (int i = 0; i < shown.size(); i++) {
                triangles += shown.get(i).triangles() + shown.get(i).placed.triangles();
            }
            waitedLast = chunksWaiting;
            if (SightTrace.ON && !fromOutside) {
                if (sightTrace == null) {
                    sightTrace = new SightTrace(WorldScene.this);
                }
                sightTrace.frame(cell, player, pose, forwardX, forwardY, cosCone, tanHalfFieldOfView * drawnAspect, tanHalfFieldOfView, frames, chunksWaiting, nearestWaiting,
                        asTheGameShows, roomsSeen);
            }
            buildMillis = (System.nanoTime() - start) / 1e6f;
            tollFrames++;
            tollQuiet += chunksQuiet;
            for (int i = 0; i < tollBuilt.length; i++) {
                tollBuilt[i] += builtBecause[i];
            }
        }

        /**
         * The rooms the eye's picture sees into, or null for a frame whose pictures draw every
         * room its chunks were built with: one drawn from elsewhere, the game's own view, and
         * the first frame, whose picture's shape is not known yet. And the shape it was worked
         * out for: a picture of another shape draws every room.
         */
        RoomSight.Seen roomsSeen;
        float roomsAspect;
        /** Who the eye's picture and a scope's may leave out by the room they stand in, or null for none: see FigureRooms. */
        FigureRooms figureRooms;
        private final RoomSight.Seen roomsSeenKept = new RoomSight.Seen();

        /** Logs each room a line through the picture reaches that the walk left out of roomsSeenKept: see ROOMS_CHECKED_EVERY_NANOS. */
        private void checkRoomsAgainstLines(int level, int eyeX, int eyeY, float aspect) {
            long began = System.nanoTime();
            roomsByLines.clear();
            RoomRays.trace(roomMap, camera, tanHalfFieldOfView * aspect, tanHalfFieldOfView, ROOM_LINES_ACROSS, ROOM_LINES_UP, ROOM_LINES_REACH, level, roomsByLines);
            for (int slot = 0; slot < roomsByLines.slots(); slot++) {
                long room = roomsByLines.at(slot);
                if (room != RoomSight.OUTSIDE && !roomsSeenKept.contains(room)) {
                    roomsMissed++;
                    Log.info(String.format(Locale.ROOT, "rooms: room %d is reached by a line through the picture and left out by the walk: eye (%.3f, %.3f, %.3f) looking (%.3f, %.3f, %.3f)"
                                    + " on level %d in region %d of building %d, %d openings, %d blocks, %d hidden by blocks, %d rooms walked",
                            room, camera.eyeX, camera.eyeY, camera.eyeUp, camera.forwardX, camera.forwardY, camera.forwardUp, level, roomMap.region(eyeX, eyeY, level),
                            roomMap.building(eyeX, eyeY, level), openingsInReach.count, blocksInReach.count, roomWalk.hiddenByBlocks, roomsSeenKept.size()));
                    StringBuilder walked = new StringBuilder();
                    Log.info("rooms: the line to room " + room + " goes " + RoomRays.pathTo(roomMap, camera, tanHalfFieldOfView * aspect, tanHalfFieldOfView, ROOM_LINES_ACROSS, ROOM_LINES_UP,
                            ROOM_LINES_REACH, level, room, (x, y, z, kind, from, to, along) -> walked.append(' ').append(roomWalk.explain(roomMap, openingsInReach, x, y, z, kind, from)))
                            + "; the walk made of its openings:" + walked + "; openings up within 2 squares:" + openingsUpNear());
                }
            }
            roomChecks++;
            roomChecksNanos += System.nanoTime() - began;
        }

        /** Dev only: the openings up to the level above within two squares of the eye, each with its regions, its heights and its state. */
        private String openingsUpNear() {
            StringBuilder list = new StringBuilder();
            for (int i = 0; i < openingsInReach.count; i++) {
                if (openingsInReach.kind[i] != RoomSight.UP || Math.abs(openingsInReach.x[i] + 0.5f - camera.eyeX) > 2.5f || Math.abs(openingsInReach.y[i] + 0.5f - camera.eyeY) > 2.5f) {
                    continue;
                }
                float[] h = openingsInReach.heights;
                list.append(String.format(Locale.ROOT, " [%d,%d,%d %d->%d at %.2f %.2f %.2f %.2f state %d]", openingsInReach.x[i], openingsInReach.y[i], openingsInReach.z[i],
                        openingsInReach.here[i], openingsInReach.there[i], h[i * 4], h[i * 4 + 1], h[i * 4 + 2], h[i * 4 + 3], RoomSight.state(roomMap, openingsInReach, i)));
            }
            return list.length() == 0 ? " none" : list.toString();
        }

        /**
         * Works out which rooms the eye sees into, from the openings of every chunk the picture
         * can reach, and which rooms it could see into soon, whose contents are built: see
         * RoomSight. A build of a chunk with a room that came into those or went out of them is
         * made again.
         */
        private void seeRooms(IsoChunkMap map, int level, boolean fromOutside, long now) {
            roomsSeen = null;
            figureRooms = null;
            roomsForModels = null;
            figuresLeftOut = null;
            float aspect = drawnAspect;
            if (fromOutside || gamesView || aspect <= 0f || withoutCulling) {
                if (buildingBySight) {
                    buildingBySight = false;
                    roomsBuilt.clear();
                    roomsMayShow.clear();
                    roomsMayShowFound.clear();
                    mayShowOpenings = -1;
                    tellEveryChunk(map);
                }
                return;
            }
            long began = System.nanoTime();
            roomMap.begin(cell, terrain);
            int side = IsoChunkMap.chunkGridWidth;
            float reach = ChunkBuffers.drawnWithin(fogEnd) + CHUNK_REACH;
            // The chunks in reach, each read again if it may have changed, as one number: the same as the last frame's, the openings are.
            long inReach = 17L;
            for (int gridX = 0; gridX < side; gridX++) {
                for (int gridY = 0; gridY < side; gridY++) {
                    IsoChunk chunk = reachable(map, gridX, gridY, reach);
                    if (chunk == null) {
                        continue;
                    }
                    IsoChunk north = gridY > 0 ? map.getChunk(gridX, gridY - 1) : null, west = gridX > 0 ? map.getChunk(gridX - 1, gridY) : null;
                    int readings = roomMap.read(chunk, north != null && north.loaded ? north : null, west != null && west.loaded ? west : null, ChunkMarks.live, now);
                    inReach = (inReach * 31 + chunk.wx) * 31 + chunk.wy;
                    inReach = inReach * 31 + readings;
                }
            }
            if (inReach != openingsReach) {
                openingsReach = inReach;
                openingsInReach.clear();
                blocksInReach.clear();
                for (int gridX = 0; gridX < side; gridX++) {
                    for (int gridY = 0; gridY < side; gridY++) {
                        IsoChunk chunk = reachable(map, gridX, gridY, reach);
                        if (chunk != null) {
                            roomMap.addTo(chunk, openingsInReach, blocksInReach);
                        }
                    }
                }
            }
            int eyeX = (int) Math.floor(camera.eyeX), eyeY = (int) Math.floor(camera.eyeY);
            // On a stair the character's level is the one it set out from while its eye is already over the next floor.
            int eyeLevel = RoomSight.levelOf(roomMap, eyeX, eyeY, level, camera.eyeUp);
            roomsSeenKept.clear();
            roomWalk.walk(roomMap, openingsInReach, blocksInReach, camera, tanHalfFieldOfView * aspect, tanHalfFieldOfView, roomMap.region(eyeX, eyeY, eyeLevel),
                    roomMap.building(eyeX, eyeY, eyeLevel), roomsSeenKept);
            roomsSeen = roomsSeenKept;
            roomsAspect = aspect;
            lastHidden = roomWalk.hiddenByBlocks;
            if (DevBuild.ON && now - roomsCheckedNanos >= ROOMS_CHECKED_EVERY_NANOS) {
                roomsCheckedNanos = now;
                checkRoomsAgainstLines(eyeLevel, eyeX, eyeY, aspect);
            }
            float seconds = (now - roundNanos) / 1e9f;
            float moved = (float) Math.hypot(camera.eyeX - roundEyeX, camera.eyeY - roundEyeY);
            float grow = GROW_TILES + eyeSpeed * GROW_SECONDS;
            roomsEntered.clear();
            roomsLeft.clear();
            boolean whole = roundNanos == 0L || !buildingBySight;
            if (whole) {
                if (mayShowRound.walking()) {
                    // What it found so far is not what it was set out from.
                    mayShowOpenings = -1;
                }
                builtRound.drop();
                mayShowRound.drop();
            }
            if (!builtRound.walking() && (whole || now - roundNanos >= ROUND_EVERY_NANOS || moved > grow / 2f)) {
                if (roundNanos != 0L && seconds > 0f) {
                    // A jump across the map is no speed: the walk round the new place starts it again.
                    float speed = moved / seconds;
                    eyeSpeed = speed > 100f ? 0f : eyeSpeed + (speed - eyeSpeed) * Math.min(1f, seconds * 4f);
                }
                roundEyeX = camera.eyeX;
                roundEyeY = camera.eyeY;
                roundNanos = now;
                roomsFound.clear();
                long region = roomMap.region(eyeX, eyeY, eyeLevel), building = roomMap.building(eyeX, eyeY, eyeLevel);
                roomWalk.begin(builtRound, camera.eyeX, camera.eyeY, camera.eyeUp, region, building, (GROW_TILES + eyeSpeed * GROW_SECONDS) / 2f, SHUT_WITHIN, roomsFound);
                float movedSince = (float) Math.sqrt((camera.eyeX - mayShowEyeX) * (camera.eyeX - mayShowEyeX) + (camera.eyeY - mayShowEyeY) * (camera.eyeY - mayShowEyeY)
                        + (camera.eyeUp - mayShowEyeUp) * (camera.eyeUp - mayShowEyeUp));
                if (!mayShowRound.walking() && (movedSince > grow / 2f || reach != mayShowReach || region != mayShowRegion || building != mayShowBuilding
                        || openingsInReach.filling != mayShowOpenings || blocksInReach.filling != mayShowBlocks)) {
                    mayShowEyeX = camera.eyeX;
                    mayShowEyeY = camera.eyeY;
                    mayShowEyeUp = camera.eyeUp;
                    mayShowReach = reach;
                    mayShowRegion = region;
                    mayShowBuilding = building;
                    mayShowOpenings = openingsInReach.filling;
                    mayShowBlocks = blocksInReach.filling;
                    roomsMayShowFound.clear();
                    roomWalk.begin(mayShowRound, camera.eyeX, camera.eyeY, camera.eyeUp, region, building, (GROW_TILES + eyeSpeed * GROW_SECONDS) / 2f, reach, roomsMayShowFound);
                }
                roomsMayShowDue = true;
            }
            // Each round's pictures are walked one a frame: six of them walked in one frame were a
            // hitch of a frame's worth every quarter second on a street.
            do {
                if (builtRound.walking() && roomWalk.walkFace(roomMap, openingsInReach, blocksInReach, builtRound)) {
                    roomsBuilt.found(roomsFound, now, roomsEntered);
                    roomsBuilt.expire(now, roomsLeft);
                }
                if (mayShowRound.walking()) {
                    roomWalk.walkFace(roomMap, openingsInReach, blocksInReach, mayShowRound);
                }
            } while (whole && (builtRound.walking() || mayShowRound.walking()));
            if (roomsMayShowDue && !mayShowRound.walking()) {
                roomsMayShowDue = false;
                // The rooms the last walk found stand for it until it is walked again.
                roomsMayShowChanged.clear();
                roomsMayShow.found(roomsMayShowFound, now, roomsMayShowChanged);
                roomsMayShow.expire(now, roomsMayShowChanged);
            }
            // What the picture draws is always built, whatever the walk round found.
            roomsBuilt.found(roomsSeenKept, now, roomsEntered);
            roomsMayShowChanged.clear();
            roomsMayShow.found(roomsSeenKept, now, roomsMayShowChanged);
            roomsShownChanged.clear();
            roomsShown.found(roomsSeenKept, now, roomsShownChanged);
            roomsShown.expire(now, roomsShownChanged);
            figuresShown.fill(roomsShown, openingsInReach);
            figureRooms = figuresShown;
            figuresLeftOut = figuresShown;
            modelRooms.fill(roomsMayShow, openingsInReach);
            roomsForModels = modelRooms;
            tollRoomsNanos += System.nanoTime() - began;
            tollRoomsWalks++;
            lastOpenings = openingsInReach.count;
            lastBlocks = blocksInReach.count;
            lastSeen = roomsSeenKept.size();
            // Only a room come in has its chunks built again. A build holds the contents of a room
            // gone out until its chunk is built again for something else, and the picture draws
            // nothing of a room it does not see into: building a tower's column of thirty storeys
            // again to leave a room out was most of what was built on a climb, a floor's rooms
            // coming in up the stairwell and going out four seconds later. So with the builds made
            // before the eye's picture went by sight, which hold every room.
            // A room come in on a storey of the viewer's building that is held waits until the
            // picture sees into it or the viewer comes within reach of its storey: the walk round
            // finds the rooms up and down a stairwell long before any of them shows.
            roomsTold.clear();
            for (int slot = 0; slot < roomsEntered.slots(); slot++) {
                long room = roomsEntered.at(slot);
                if (room != RoomSight.OUTSIDE) {
                    (soonShown(room) ? roomsTold : roomsWaiting).add(room);
                }
            }
            roomsWaitingSpare.clear();
            for (int slot = 0; slot < roomsWaiting.slots(); slot++) {
                long room = roomsWaiting.at(slot);
                if (room == RoomSight.OUTSIDE || !roomsBuilt.contains(room) || roomsTold.contains(room)) {
                    continue;
                }
                (soonShown(room) ? roomsTold : roomsWaitingSpare).add(room);
            }
            RoomSight.Seen waiting = roomsWaiting;
            roomsWaiting = roomsWaitingSpare;
            roomsWaitingSpare = waiting;
            if (!buildingBySight) {
                buildingBySight = true;
            } else if (roomsTold.size() > 0) {
                for (int gridX = 0; gridX < side; gridX++) {
                    for (int gridY = 0; gridY < side; gridY++) {
                        IsoChunk chunk = map.getChunk(gridX, gridY);
                        if (chunk != null && roomMap.holdsAny(chunk, roomsTold)) {
                            ChunkMarks.tell(chunk.wx, chunk.wy);
                        }
                    }
                }
            }
        }

        /** Whether a room the builds hold may show soon: the picture sees into it, or it is on a storey of the viewer's building that is not held. */
        private boolean soonShown(long room) {
            if (roomsSeenKept.contains(room)) {
                return true;
            }
            RoomDef def = IsoWorld.instance.getMetaGrid().getRoomDefByID(room);
            BuildingDef building = def == null ? null : def.getBuilding();
            return building == null || building.id != heldBuilding || def.level < heldFrom && def.level > heldUnder;
        }

        /** A loaded chunk of the map whose middle is within reach of the eye, or null. */
        private IsoChunk reachable(IsoChunkMap map, int gridX, int gridY, float reach) {
            IsoChunk chunk = map.getChunk(gridX, gridY);
            return chunk == null || !chunk.loaded || farther(chunk, camera.eyeX, camera.eyeY, reach) ? null : chunk;
        }

        /** Has every chunk of the loaded map built again, as the rule its rooms are built by has changed. */
        private void tellEveryChunk(IsoChunkMap map) {
            int side = IsoChunkMap.chunkGridWidth;
            for (int gridX = 0; gridX < side; gridX++) {
                for (int gridY = 0; gridY < side; gridY++) {
                    IsoChunk chunk = map.getChunk(gridX, gridY);
                    if (chunk != null) {
                        ChunkMarks.tell(chunk.wx, chunk.wy);
                    }
                }
            }
        }

        /** Begins again what a frame counts and what it showed, and notes whether the eye has moved since the last. */
        private void beginTallies(ViewCamera.Pose pose) {
            Arrays.fill(characterOf, 0, characters, null);
            characters = 0;
            for (int i = 0; i < shown.size(); i++) {
                release(shown.get(i));
            }
            shown.clear();
            for (int i = 0; i < builtAheadOf.size(); i++) {
                release(builtAheadOf.get(i));
            }
            builtAheadOf.clear();
            waterShown = false;
            frames++;
            Terrain.frame(roundNow);
            chunksDrawn = 0;
            chunksBuilt = 0;
            chunksGrown = 0;
            chunksKept = 0;
            chunksQuiet = 0;
            chunksWaiting = 0;
            Arrays.fill(builtBecause, 0);
            eyeStill = pose.eyeX == lastEyeX && pose.eyeY == lastEyeY && pose.eyeUp == lastEyeUp;
            lastEyeX = pose.eyeX;
            lastEyeY = pose.eyeY;
            lastEyeUp = pose.eyeUp;
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
            gablesFaced = gablesPainted = gablesBare = 0;
        }

        /**
         * Where each standing character is, for the aim to be traced against. Read from the
         * game's own list every frame: characters move, and a chunk's build may be frames old.
         * The player comes in as handed to build and is not read off viewer: setting the picture
         * down lets go of viewer on the render thread, which may fall while a rehearsal builds.
         */
        private void addCharacters(IsoPlayer player, ViewCamera.Pose pose) {
            float reach = Controls.aimReach(player);
            for (IsoMovingObject mover : cell.getObjectList()) {
                if (mover instanceof IsoGameCharacter character && character != player
                        && character.getAlpha(playerIndex) > CHARACTER_SHOWN_ALPHA && !character.isInvisible()) {
                    float toX = character.getX() - pose.eyeX, toY = character.getY() - pose.eyeY;
                    if (toX * toX + toY * toY <= reach * reach) {
                        addCharacter(character);
                    }
                }
            }
        }

        /** Takes the light of the open ground and of the rooms from the squares in sight, as often as each is taken. */
        private void takeLight(long start) {
            if (!openLightTaken || start - openLightTakenAt >= OPEN_LIGHT_TAKEN_EVERY_NANOS) {
                seenOutside.clear();
                for (int i = 0; i < shown.size(); i++) {
                    ChunkMeshes each = shown.get(i);
                    if (!each.grown && each.crowns == null && each.seen != null) {
                        each.seen.addTo(seenOutside);
                    }
                }
            }
            // As often as the open ground's, and kept while too few room squares are in sight to say.
            if (!asTheGameShows && !gamesView && start - roomLightTakenAt >= OPEN_LIGHT_TAKEN_EVERY_NANOS) {
                roomLightTakenAt = start;
                for (int i = 0; i < shown.size(); i++) {
                    ChunkMeshes each = shown.get(i);
                    if (!each.grown && each.crowns == null && each.seen != null) {
                        each.seenRooms.addTo(seenRooms);
                        each.seenLamplit.addTo(seenLamplit);
                    }
                }
                // Taken by day alone. In the dark a room's light beside the open ground's is one small number over
                // another, which no step holds still, and what it comes to is not what it is by day.
                float open = (unseenRed + unseenGreen + unseenBlue) / 3f;
                if (seenRooms.counted() >= ROOM_LIGHT_FROM && open >= ROOM_SHARE_TAKEN_FROM) {
                    seenRooms.commonest(openLight);
                    roomShare = Sightline.followed(Math.min((openLight[0] + openLight[1] + openLight[2]) / 3f / open, 1f), roomShare);
                }
                if (seenLamplit.counted() >= ROOM_LIGHT_FROM) {
                    seenLamplit.commonest(openLight);
                    lampLevel = Sightline.followed(Math.max(openLight[0], Math.max(openLight[1], openLight[2])), lampLevel);
                }
                seenRooms.clear();
                seenLamplit.clear();
            }
            if (!seenOutside.isEmpty() && (!openLightTaken || start - openLightTakenAt >= OPEN_LIGHT_TAKEN_EVERY_NANOS)) {
                seenOutside.commonest(openLight);
                if (!openLightTaken || stepped(openLight, unseenRed, unseenGreen, unseenBlue)) {
                    unseenRed = openLight[0];
                    unseenGreen = openLight[1];
                    unseenBlue = openLight[2];
                }
                seenOutside.floor(openLight);
                if (!openLightTaken || stepped(openLight, floorRed, floorGreen, floorBlue)) {
                    floorRed = openLight[0];
                    floorGreen = openLight[1];
                    floorBlue = openLight[2];
                }
                seenOutside.clear();
                openLightTaken = true;
                openLightTakenAt = start;
            }
        }

        /**
         * After a build of a chunk: sees that the chunk has the crowns the build wrote down. See
         * latestCrowned. They may be made on their own thread only for a build of a chunk found
         * unchanged, made because the eye had moved or the build was old: what has changed on the
         * map, a light switched on by a tree, a tree felled, shows whole in the frame it is read
         * in, as it always did, whatever else that build was made for.
         */
        private void crown(int slot, int wx, int wy, boolean mayWait) {
            ChunkMeshes standing = latestCrowned[slot];
            Crowns.Work waited = crownsWaited[slot];
            if (crownsRead.isEmpty() || standing != null && (standing.wx != wx || standing.wy != wy) || waited != null && (waited.into.wx != wx || waited.into.wy != wy)) {
                forgetCrowns(slot);
                standing = null;
                waited = null;
            }
            if (crownsRead.isEmpty()) {
                return;
            }
            if (waited != null && waited.from.same(crownsRead)) {
                tollCrownsKept++;
                return;
            }
            // The crowns that stand may be the ones wanted even with others out being made: an eye that crossed a band and came straight back.
            if (standing != null && !withoutKeptCrowns && standing.crowns.same(crownsRead)) {
                if (waited != null) {
                    waited.unwanted = true;
                    crownsWaited[slot] = null;
                }
                tollCrownsKept++;
                return;
            }
            if (waited != null) {
                waited.unwanted = true;
                crownsWaited[slot] = null;
            }
            ChunkMeshes fresh = spare.isEmpty() ? new ChunkMeshes(view.index) : spare.pop();
            fresh.placed.clear();
            fresh.wx = wx;
            fresh.wy = wy;
            fresh.builtFrame = frames;
            fresh.grown = false;
            fresh.users = 1;
            Crowns from = crownsRead;
            crownsRead = spareCrowns.isEmpty() ? new Crowns() : spareCrowns.pop();
            if (standing == null || !mayWait || withoutCrownThread) {
                long began = System.nanoTime();
                solid.clear();
                laid.clear();
                from.mesh(solid, PLAIN, crownFrame);
                fresh.crowns = from;
                fresh.fill(solid, laid);
                if (standing != null) {
                    release(standing);
                }
                latestCrowned[slot] = fresh;
                tollCrownsHere++;
                tollCrownsHereNanos += System.nanoTime() - began;
            } else {
                Crowns.Work work = new Crowns.Work(from, fresh, slot, PLAIN, CHECK_BUILDS, crownsBack);
                Crowns.begin(work);
                crownsWaited[slot] = work;
                crownsOut++;
                tollCrownsThere++;
            }
        }

        /** Whether a slope the map leaves out was made over the square, which a build that asks is then no plain one. */
        boolean madeAt(int x, int y, int z) {
            boolean made = Roofs.madeAt(x, y, z, roundNow) != null;
            building.plain &= !made;
            return made;
        }

        /** Finds the chunk being read and the eight round it, for squareAt. */
        private void surround(IsoChunkMap map, IsoChunk chunk, int gridX, int gridY, int side) {
            for (int i = 0; i < 9; i++) {
                int x = gridX + i / 3 - 1, y = gridY + i % 3 - 1;
                IsoChunk beside = x < 0 || y < 0 || x >= side || y >= side ? null : map.getChunk(x, y);
                around[i] = beside != null && beside.loaded ? beside : null;
            }
            around[4] = chunk;
            ring = 0;
            for (int i = 0; i < 9; i++) {
                ring |= around[i] == null ? 0 : 1 << i;
            }
        }

        /** Whether something lies on the square that its lawn does not grow up through: see Flat.grassThrough. */
        private boolean flattened(IsoGridSquare square) {
            PZArrayList<IsoObject> objects = square.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                IsoSprite sprite = objects.get(i).getSprite();
                if (sprite != null && kindOf(sprite).lies && !Flat.grassThrough(sprite.getName())) {
                    return true;
                }
            }
            return false;
        }

        /** How far something lying on the square (x, y) reaches into the square (toX, toY) beside it: none where something lies there too. */
        private float reachInto(int x, int y, int toX, int toY, int z) {
            IsoGridSquare next = squareAt(Math.floorDiv(x, CHUNK_TILES) * CHUNK_TILES, Math.floorDiv(y, CHUNK_TILES) * CHUNK_TILES, toX, toY, z);
            if (next == null) {
                return Flat.REACH;
            }
            PZArrayList<IsoObject> objects = next.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                IsoSprite sprite = objects.get(i).getSprite();
                if (sprite != null && kindOf(sprite).lies) {
                    return 0f;
                }
            }
            return Flat.REACH;
        }

        /**
         * The square at (x, y, z), which is on the chunk at (chunkX, chunkY) or within a chunk of
         * it: taken from the chunk it is on, which the cell would have to find among the map's
         * chunks at every asking.
         */
        boolean naturalBeside(int chunkX, int chunkY, int x, int y) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if ((dx != 0 || dy != 0) && Terrain.kind(squareAt(chunkX, chunkY, x + dx, y + dy, 0)) == Terrain.NATURAL) {
                        return true;
                    }
                }
            }
            return false;
        }

        IsoGridSquare squareAt(int chunkX, int chunkY, int x, int y, int z) {
            int offX = x - chunkX, offY = y - chunkY;
            IsoChunk on = around[(offX < 0 ? 0 : offX < CHUNK_TILES ? 1 : 2) * 3 + (offY < 0 ? 0 : offY < CHUNK_TILES ? 1 : 2)];
            return on == null ? cell.getGridSquare(x, y, z) : on.getGridSquare(offX & CHUNK_TILES - 1, offY & CHUNK_TILES - 1, z);
        }
        private final byte[] auditLayers = new byte[CHUNK_TILES * CHUNK_TILES];

        private void auditStale(IsoChunk chunk, int chunkX, int chunkY, int lowest, int slot, ChunkMeshes standing, float distance, long now, int toldNow, int landNow) {
            boolean signed = standing.signed, plain = standing.plain, grows = standing.grows;
            int seen = squaresSeen, loaded = squaresLoaded, counted = sprites;
            System.arraycopy(standing.groundLayers, 0, auditLayers, 0, auditLayers.length);
            long sum = signature(chunk, chunkX, chunkY, lowest, null, null);
            solid.clear();
            laid.clear();
            checkPlaced.clear();
            placed = checkPlaced;
            building = standing;
            bandAway = BAND_AWAY[standing.band];
            afresh = true;
            geometry.afresh = true;
            crownsChecked.clear();
            addSquares(chunk, chunkX, chunkY, lowest);
            kerbBuilder.noKerb();
            roofBuilder.addBacks(chunk, chunkX, chunkY);
            afresh = false;
            geometry.afresh = false;
            long floats = floatsOf(solid) * 31 + floatsOf(laid);
            boolean asked = !standing.signed;
            // The crowns the build beside writes down against those the chunk has standing or out being made, and what
            // grows on its ground, built beside too where it stands made from where the eye still is.
            Crowns.Work waited = crownsWaited[slot];
            ChunkMeshes crowned = latestCrowned[slot];
            Crowns written = waited != null && waited.into.wx == chunk.wx && waited.into.wy == chunk.wy ? waited.from
                    : crowned != null && crowned.wx == chunk.wx && crowned.wy == chunk.wy ? crowned.crowns : null;
            boolean crownsStale = written == null ? !crownsChecked.isEmpty() : !written.same(crownsChecked);
            ChunkMeshes grown = latestGrown[slot];
            boolean grownStale = false;
            if (grown != null && grown.wx == chunk.wx && grown.wy == chunk.wy && grown.builtFrame >= standing.builtFrame && grown.eyeX == camera.eyeX
                    && grown.eyeY == camera.eyeY && grown.eyeUp == camera.eyeUp && floats == standing.auditFloats) {
                // The sets are not read again below: the chunk's own build beside came out as its standing one.
                solid.clear();
                laid.clear();
                growing = true;
                addSquares(chunk, chunkX, chunkY, 0);
                growing = false;
                kerbBuilder.noKerb();
                grownStale = floatsOf(solid) * 31 + floatsOf(laid) != grown.auditFloats;
                tollStaleGrownAsked++;
                if (grownStale) {
                    grown.auditFloats = floatsOf(solid) * 31 + floatsOf(laid);
                }
            }
            System.arraycopy(auditLayers, 0, standing.groundLayers, 0, auditLayers.length);
            standing.signed = signed;
            standing.plain = plain;
            standing.grows = grows;
            squaresSeen = seen;
            squaresLoaded = loaded;
            sprites = counted;
            tollStaleAsked++;
            // A build beside may itself be the asking that reads a roof again, which then tells the chunk: that is the signal working.
            boolean due = ChunkMarks.told(chunk.wx, chunk.wy) != toldNow || signed && sum != standing.signature;
            if ((crownsStale || grownStale) && !due) {
                tollStale++;
                Log.info(String.format(Locale.ROOT, "stale build: the chunk at %d, %d (%d, %d), %.0f tiles off, band %d, built %.1f s ago, would have other %s now, with its sum, what it was told and its heights the same",
                        chunk.wx, chunk.wy, chunkX, chunkY, distance, standing.band, (now - standing.builtNanos) / 1e9, crownsStale ? "crowns written down" : "things growing on its ground"));
            }
            if (floats != standing.auditFloats && due) {
                tollStaleDue++;
                standing.auditFloats = floats;
                standing.auditMeshes = meshesOf();
            } else if (floats != standing.auditFloats) {
                tollStale++;
                Log.info(String.format(Locale.ROOT, "stale build: the chunk at %d, %d (%d, %d), %.0f tiles off, band %d, built %.1f s ago and %.1f s old, would be built otherwise now: its sum %s, told %s, heights %s, map round it %s, plain %b, this build asked for what is not read yet %b",
                        chunk.wx, chunk.wy, chunkX, chunkY, distance, standing.band, (now - standing.builtNanos) / 1e9, (now - standing.bornNanos) / 1e9,
                        !signed ? "was not taken" : sum == standing.signature ? "the same" : "another", toldNow == standing.told ? "the same" : "another",
                        landNow == standing.land ? "the same" : "another", ring == standing.ring ? "the same" : "another", plain, asked));
                long[] now2 = meshesOf();
                int said = 0;
                for (int i = 0; i < now2.length && said < 8; i += 3) {
                    int at = -1;
                    for (int j = 0; j < standing.auditMeshes.length; j += 3) {
                        at = standing.auditMeshes[j] == now2[i] ? j : at;
                    }
                    if (at < 0 || standing.auditMeshes[at + 2] != now2[i + 2]) {
                        said++;
                        Log.info("stale build:   " + WorldScene.this.describe(now2[i], at < 0 ? 0 : standing.auditMeshes[at + 1], now2[i + 1]));
                    }
                }
                for (int j = 0; j < standing.auditMeshes.length && said < 8; j += 3) {
                    boolean still = false;
                    for (int i = 0; i < now2.length; i += 3) {
                        still |= standing.auditMeshes[j] == now2[i];
                    }
                    if (!still) {
                        said++;
                        Log.info("stale build:   " + WorldScene.this.describe(standing.auditMeshes[j], standing.auditMeshes[j + 1], 0));
                    }
                }
                standing.auditFloats = floats;
                standing.auditMeshes = now2;
            }
            solid.clear();
            laid.clear();
        }

        /** Dev only: builds the chunk again with nothing taken from what is kept, and says so when the two builds differ in any float. */
        private void check(IsoChunk chunk, int chunkX, int chunkY, int lowest, ChunkMeshes fresh) {
            long first = floatsOf(solid) * 31 + floatsOf(laid);
            long loose = looseOf(solid) + looseOf(laid);
            boolean signed = fresh.signed;
            int seen = squaresSeen, loaded = squaresLoaded, counted = sprites;
            solid.clear();
            laid.clear();
            checkPlaced.clear();
            placed = checkPlaced;
            afresh = true;
            geometry.afresh = true;
            // First with the crowns made in the chunk's own sets, as they were before they were a build of their own:
            // every vertex of that build is one of this build's or of its crowns', whatever the order.
            crownsInline = true;
            addSquares(chunk, chunkX, chunkY, lowest);
            kerbBuilder.noKerb();
            roofBuilder.addBacks(chunk, chunkX, chunkY);
            crownsInline = false;
            long whole = looseOf(solid) + looseOf(laid);
            solid.clear();
            laid.clear();
            crownsRead.mesh(solid, PLAIN, crownFrame);
            long apart = loose + looseOf(solid);
            // A build said to come out the same for the band on either side is made as for that band, and has to.
            boolean alikeHeld = true;
            int band = fresh.band;
            for (int other = band - 1; other <= band + 1; other += 2) {
                if (other < 0 || other >= BAND_AWAY.length || (fresh.alike >> other & 1) == 0) {
                    continue;
                }
                solid.clear();
                laid.clear();
                checkPlaced.clear();
                crownsChecked.clear();
                fresh.band = other;
                bandAway = BAND_AWAY[other];
                addSquares(chunk, chunkX, chunkY, lowest);
                kerbBuilder.noKerb();
                roofBuilder.addBacks(chunk, chunkX, chunkY);
                alikeHeld &= floatsOf(solid) * 31 + floatsOf(laid) == first;
                tollChecked++;
            }
            fresh.band = band;
            bandAway = BAND_AWAY[band];
            solid.clear();
            laid.clear();
            checkPlaced.clear();
            crownsChecked.clear();
            addSquares(chunk, chunkX, chunkY, lowest);
            kerbBuilder.noKerb();
            roofBuilder.addBacks(chunk, chunkX, chunkY);
            long second = floatsOf(solid) * 31 + floatsOf(laid);
            boolean sameCrowns = crownsRead.same(crownsChecked);
            afresh = false;
            geometry.afresh = false;
            placed = fresh.placed;
            squaresSeen = seen;
            squaresLoaded = loaded;
            sprites = counted;
            tollChecked++;
            // A build that asked for a sprite's colours is not signed, and its second build may have been answered.
            if (signed && fresh.signed && (first != second || !sameCrowns || whole != apart || !alikeHeld)) {
                tollDiffered++;
                Log.info("build check: the chunk at " + chunk.wx + ", " + chunk.wy + " came out as two different builds" + (first != second ? "" : whole != apart
                        ? ", with its crowns made apart from it and in it" : !sameCrowns ? " in its crowns" : " for a band it was said to be the same for"));
            }
            fresh.signed = signed && fresh.signed;
        }

        /**
         * Goes through the squares of a chunk from the level lowest up, adding each to the sets:
         * all that stands on it, or, while growing is set, only what grows on its ground.
         */
        private void addSquares(IsoChunk chunk, int chunkX, int chunkY, int lowest) {
            bodiesLaid = 0;
            // Once for the chunk and not once a level: until the ground round a chunk is all
            // loaded its corners are worked out afresh at every asking.
            float[] ground = terrain.corners(cell, chunk.wx, chunk.wy);
            if (!growing) {
                building.terrainVersion = terrain.version(chunk.wx, chunk.wy);
            }
            float[] water = terrain.water(chunk.wx, chunk.wy);
            kerbs = terrain.kerb(chunk.wx, chunk.wy);
            boolean kept = !afresh && keptFrame == frames && keptWx == chunk.wx && keptWy == chunk.wy;
            if (!kept && !growing) {
                canopied = false;
            }
            // What grows on a chunk is another build of the same chunk, and a check is a second build: neither is the reading the chunk's light is taken from.
            // A level the reading left unread is read here, and its squares are counted with the reading's.
            if (!kept && !growing && !afresh) {
                reading.clear();
                readingRooms.clear();
                readingLamplit.clear();
            }
            if (!kept) {
                lampsRound(chunkX, chunkY);
            }
            for (int z = Math.max(lowest, chunk.getMinLevel()); z <= (growing ? 0 : chunk.getMaxLevel()); z++) {
                boolean inKept = kept && z >= keptFrom && z <= keptTo;
                int at = (z - keptFrom) * lightRed.length;
                if (inKept && (keptLevels >> (z - keptFrom) & 1L) != 0L) {
                    System.arraycopy(keptRed, at, lightRed, 0, lightRed.length);
                    System.arraycopy(keptGreen, at, lightGreen, 0, lightRed.length);
                    System.arraycopy(keptBlue, at, lightBlue, 0, lightRed.length);
                    System.arraycopy(keptOpen, at, lightOpen, 0, lightRed.length);
                    System.arraycopy(keptUnseen, at, lightUnseen, 0, lightRed.length);
                    System.arraycopy(keptDay, at, lightDay, 0, lightRed.length);
                } else {
                    tallying = !growing && !afresh;
                    readLights(chunkX, chunkY, z);
                    if (z == 0) {
                        readWoods(chunkX, chunkY);
                    }
                    // Kept for the build of what grows on the chunk, which follows in the same frame.
                    if (inKept) {
                        System.arraycopy(lightRed, 0, keptRed, at, lightRed.length);
                        System.arraycopy(lightGreen, 0, keptGreen, at, lightRed.length);
                        System.arraycopy(lightBlue, 0, keptBlue, at, lightRed.length);
                        System.arraycopy(lightOpen, 0, keptOpen, at, lightRed.length);
                        System.arraycopy(lightUnseen, 0, keptUnseen, at, lightRed.length);
                        System.arraycopy(lightDay, 0, keptDay, at, lightRed.length);
                        keptLevels |= 1L << (z - keptFrom);
                    }
                }
                if (z == 0) {
                    Flora.wooded(woods, CHUNK_TILES, wooded);
                    treesRound = false;
                    for (byte tree : woods) {
                        treesRound |= tree != Litter.NONE;
                    }
                    if (growing) {
                        Litter.fields(woods, CHUNK_TILES, leaves, needles, leafKinds, needleKinds);
                    }
                }
                if (!growing) {
                    bloodBuilder.read(chunkX, chunkY, z);
                }
                for (int localX = 0; localX < CHUNK_TILES; localX++) {
                    for (int localY = 0; localY < CHUNK_TILES; localY++) {
                        IsoGridSquare square = chunk.getGridSquare(localX, localY, z);
                        if (square == null || z > topLevel && !FBORenderCutaways.getInstance().shouldRenderBuildingSquare(playerIndex, square)) {
                            continue;
                        }
                        if (!growing) {
                            squaresLoaded++;
                        }
                        cornerLights(localX, localY);
                        int corner = localX * Terrain.CORNERS + localY;
                        // Water lies at its own height, and a street below its kerb.
                        byte kind = z == 0 ? groundKind[(localX + 1) * LIGHT_SPAN + localY + 1] : Terrain.MADE;
                        float[] heights = kind == Terrain.WATER ? water : ground;
                        float drop = z == 0 && kind != Terrain.WATER && kerbs != null ? kerbBuilder.kerbOver(kind, localX, localY) : kerbBuilder.noKerb();
                        geometry.lift[0] = heights[corner] - drop;
                        geometry.lift[1] = heights[corner + Terrain.CORNERS] - drop;
                        geometry.lift[2] = heights[corner + Terrain.CORNERS + 1] - drop;
                        geometry.lift[3] = heights[corner + 1] - drop;
                        if (geometry.lift[0] != 0f) {
                            lifted++;
                        }
                        if (growing) {
                            grow(square, chunkX + localX, chunkY + localY, localX, localY);
                        } else {
                            IsoBuilding of = square.getBuilding();
                            long storeyOf = of == null || of.getDef() == null ? SpriteGeometry.NO_BUILDING : of.getDef().id;
                            solid.storey(storeyOf, z);
                            laid.storey(storeyOf, z);
                            addSquare(square, chunkX + localX, chunkY + localY, z, localX, localY);
                        }
                    }
                }
            }
            solid.storey(SpriteGeometry.NO_BUILDING, 0);
            laid.storey(SpriteGeometry.NO_BUILDING, 0);
        }
        /** The bands the chunk being built would come out the same for, a bit each. */
        int alike;

        /**
         * Whether a build is still what the season has it: it holds no tree the season shapes, or
         * was made at the stage now, or at the one before while its slot's frame to come to the
         * new one has not come yet.
         */
        private boolean seasonHolds(ChunkMeshes built, int slot) {
            return !built.seasonal || built.stage == stage || built.stage == stageBefore && frames - stageMoved < slot % STAGE_SPREAD;
        }

        /**
         * Whether what is being added is within a reach of the eye, or right at it when that
         * counts as within, by the band the chunk is built for. Every question a build asks of
         * its band comes through here or through alikeOnly, and each takes out of alike the bands
         * that would have answered otherwise: a street's chunk asks none, and is one build from
         * any distance.
         */
        boolean within(float reach, boolean atCounts) {
            boolean within = bandAway < reach || atCounts && bandAway == reach;
            for (int band = 0; band < BAND_AWAY.length; band++) {
                if ((BAND_AWAY[band] < reach || atCounts && BAND_AWAY[band] == reach) != within) {
                    alike &= ~(1 << band);
                }
            }
            return within;
        }

        /**
         * Takes a build into another band it would have come out the same for. Its crowns are
         * another build and go by the distance whatever the band: they are made again, from what
         * the chunk's last build read, with the old ones standing until they are ready.
         */
        private void reband(ChunkMeshes built, int slot, int band) {
            built.band = band;
            tollRebanded++;
            Crowns.Work waited = crownsWaited[slot];
            ChunkMeshes standing = latestCrowned[slot];
            Crowns read = waited != null && waited.into.wx == built.wx && waited.into.wy == built.wy ? waited.from
                    : standing != null && standing.wx == built.wx && standing.wy == built.wy ? standing.crowns : null;
            if (read != null) {
                read.copyTo(crownsRead, BAND_AWAY[band]);
                crown(slot, built.wx, built.wy, true);
            }
        }

        /** What grows on the ground of the square (x, y): the lawn, and what is strewn and springs up on it. */
        private void grow(IsoGridSquare square, int x, int y, int localX, int localY) {
            float toX = x + 0.5f - camera.eyeX, toY = y + 0.5f - camera.eyeY;
            floorLayer = building.groundLayers[localX * CHUNK_TILES + localY];
            spreadLayers = 0;
            waterLocalX = localX;
            waterLocalY = localY;
            geometry.floored = true;
            int own = (localX + 1) * LIGHT_SPAN + localY + 1;
            if (groundTexture[own] != null) {
                frameOf(groundObject[own], groundTexture[own]);
            }
            frame.material = 0f;
            if (toX * toX + toY * toY < GRASS_REACH * GRASS_REACH && !flattened(square)) {
                plantBuilder.growGrass(x, y, localX, localY);
            }
            plantBuilder.strew(x, y, localX, localY, toX * toX + toY * toY);
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
         *
         * Each level is summed apart and the sums summed. Given a build whose chunk has had
         * nothing but light marked since it was read, a level whose light no mark names takes the
         * sum the build has for it and is not read. A build that follows reads the light of those
         * levels itself, and takes the others' from the reading.
         *
         * @param readFor a build of the chunk to take the sums of its unmarked levels from, or null to read every level
         */
        private long signature(IsoChunk chunk, int chunkX, int chunkY, int lowest, ChunkMeshes readFor, ChunkMeshes held) {
            int from = Math.max(lowest, chunk.getMinLevel()), to = chunk.getMaxLevel();
            // The levels of the viewer's building that are held (heldFrom, heldUnder) are summed as the build there is was.
            boolean holds = held != null && heldFrom <= to && held.signed && held.sumFrom == from && held.sumTo == to
                    && chunkX < heldMaxX && chunkX + CHUNK_TILES > heldMinX && chunkY < heldMaxY && chunkY + CHUNK_TILES > heldMinY;
            readUnseen = sum(SUM_FROM, gamesView ? 1 : 0);
            readUnseen = sum(readUnseen, asTheGameShows ? 1 : 0);
            // A lit room's daylight goes by the hour and the open ground's light as well (daylit): read again at each step of it.
            readUnseen = sum(sum(sum(sum(readUnseen, roomsDaylit ? 1 : 0), Math.round(dayLight[0] * DAY_STEPS)), Math.round(dayLight[1] * DAY_STEPS)), Math.round(dayLight[2] * DAY_STEPS));
            readByLevel = readFor != null && readFor.sumFrom == from && readFor.sumTo == to && readFor.unseenRead == readUnseen;
            readFrom = from;
            readTo = to;
            if (readSums.length < to - from + 1) {
                readSums = new long[to - from + 1];
                readLit = new int[to - from + 1];
            }
            // Before the levels are read: a mark made while one is read is then one more than is remembered for it.
            readLoose = ChunkMarks.litLooseAround(chunk.wx, chunk.wy);
            for (int z = from; z <= to; z++) {
                readLit[z - from] = ChunkMarks.litAround(chunk.wx, chunk.wy, z);
            }
            if (readByLevel) {
                tollSumsByLevel++;
            }
            long sum = sum(sum(sum(SUM_FROM, Float.floatToRawIntBits(frame.scale)), from), to);
            float[] ground = terrain.corners(cell, chunk.wx, chunk.wy);
            float[] water = terrain.water(chunk.wx, chunk.wy);
            long land = SUM_FROM;
            for (int i = 0; i < ground.length; i++) {
                land = sum(sum(land, Float.floatToRawIntBits(ground[i])), Float.floatToRawIntBits(water[i]));
            }
            land = sum(land, terrain.kerbSum(chunk.wx, chunk.wy));
            readLand = land;
            int span = lightRed.length;
            if (AUDIT_MARKS) {
                auditTerrain = land;
                if (auditLights.length < (to - from + 1) * span) {
                    auditLights = new long[(to - from + 1) * span];
                    auditThings = new long[auditLights.length];
                }
            }
            if (keptRed.length < (to - from + 1) * span) {
                keptRed = new float[(to - from + 1) * span];
                keptGreen = new float[keptRed.length];
                keptBlue = new float[keptRed.length];
                keptOpen = new byte[keptRed.length];
                keptUnseen = new byte[keptRed.length];
                keptDay = new float[keptRed.length];
            }
            keptWx = chunk.wx;
            keptWy = chunk.wy;
            keptFrom = from;
            keptTo = to;
            keptFrame = frames;
            keptLevels = 0L;
            canopied = false;
            restless = false;
            tallying = true;
            reading.clear();
            readingRooms.clear();
            readingLamplit.clear();
            lampsRound(chunkX, chunkY);
            for (int z = from; z <= to; z++) {
                if (holds && (z >= heldFrom || z <= heldUnder)) {
                    readSums[z - from] = held.levelSums[z - from];
                    readLit[z - from] = held.litSeen[z - from];
                    tollLevelsHeld++;
                    continue;
                }
                if (readByLevel && readLit[z - from] == readFor.litSeen[z - from]) {
                    readSums[z - from] = readFor.levelSums[z - from];
                    tollLevelsKept++;
                    continue;
                }
                tollLevelsRead++;
                long level = bloodBuilder.read(chunkX, chunkY, z);
                readLights(chunkX, chunkY, z);
                System.arraycopy(lightRed, 0, keptRed, (z - from) * span, span);
                System.arraycopy(lightGreen, 0, keptGreen, (z - from) * span, span);
                System.arraycopy(lightBlue, 0, keptBlue, (z - from) * span, span);
                System.arraycopy(lightOpen, 0, keptOpen, (z - from) * span, span);
                System.arraycopy(lightUnseen, 0, keptUnseen, (z - from) * span, span);
                System.arraycopy(lightDay, 0, keptDay, (z - from) * span, span);
                keptLevels |= 1L << (z - from);
                if (z == 0) {
                    long woodsBegan = System.nanoTime();
                    readWoods(chunkX, chunkY);
                    tollWoods++;
                    tollWoodsNanos += System.nanoTime() - woodsBegan;
                    long trees = SUM_FROM;
                    for (int i = 0; i < woods.length; i++) {
                        trees = sum(trees, woods[i]);
                    }
                    level = sum(level, trees);
                    auditTrees = trees;
                    // Where a path turns, FloorBuilder.around reads the ground two squares out, a ring past the light grid.
                    for (int i = -2; i <= CHUNK_TILES + 1; i++) {
                        for (int j = -2; j <= CHUNK_TILES + 1; j++) {
                            if (i >= -1 && i <= CHUNK_TILES && j >= -1 && j <= CHUNK_TILES) {
                                continue;
                            }
                            IsoGridSquare square = squareAt(chunkX, chunkY, chunkX + i, chunkY + j, 0);
                            IsoObject floor = square == null ? null : square.getFloor();
                            byte kind = square == null ? Terrain.UNKNOWN : Terrain.kind(square, floor);
                            level = sum(sum(level, kind), kind == Terrain.NATURAL ? rank(floor.getSprite()) : 0);
                        }
                    }
                }
                for (int i = 0; i < LIGHT_SPAN; i++) {
                    for (int j = 0; j < LIGHT_SPAN; j++) {
                        int at = i * LIGHT_SPAN + j;
                        if (lightOpen[at] < 0) {
                            level = sum(level, -1);
                            if (AUDIT_MARKS) {
                                auditLights[(z - from) * span + at] = auditThings[(z - from) * span + at] = -1;
                            }
                            continue;
                        }
                        long lit = sum(sum(sum(sum(sum(sum(SUM_FROM, lightOpen[at]), lightUnseen[at]), Float.floatToRawIntBits(lightRed[at])), Float.floatToRawIntBits(lightGreen[at])),
                                Float.floatToRawIntBits(lightBlue[at])), Float.floatToRawIntBits(lightDay[at]));
                        long thing = SUM_FROM;
                        if (z == 0) {
                            thing = sum(sum(sum(thing, groundKind[at]), groundRank[at]), groundTexture[at] == null ? 0 : groundTexture[at].getID());
                        }
                        IsoGridSquare square = lightSquare[at];
                        thing = sum(sum(sum(thing, square.getRoomID()), unexplored(square) ? 1 : 0), lightUnseen[at] == OWN_LIGHT && Math.max(lightRed[at], Math.max(lightGreen[at], lightBlue[at])) > LAMPLIT && lampBurnsAt(at, square) ? 1 : 0);
                        PZArrayList<IsoObject> objects = square.getObjects();
                        thing = sum(thing, objects.size());
                        for (int k = 0; k < objects.size(); k++) {
                            IsoObject object = objects.get(k);
                            IsoDirections dir = object.getDir();
                            thing = sum(sum(sum(sum(thing, shown(object.getSprite(), dir)), Float.floatToRawIntBits(object.offsetX)), Float.floatToRawIntBits(object.offsetY)),
                                    shown(object.getOverlaySprite(), dir));
                            restless |= moves(object.getSprite()) || moves(object.getOverlaySprite());
                            ArrayList<IsoSpriteInstance> attached = object.getAttachedAnimSprite();
                            // The game's flames and smoke are nothing of the build's: the picture draws them each picture (Fires).
                            // Whether a hearth burns is, and the game marks nothing when one is lit or put out.
                            if (object instanceof IsoFireplace hearth) {
                                thing = sum(thing, hearth.isLit() ? 1 : 2);
                            } else if (object instanceof IsoBarbecue barbecue) {
                                thing = sum(thing, barbecue.isLit() ? 1 : 2);
                            }
                            if (attached != null) {
                                for (int over = 0; over < attached.size(); over++) {
                                    IsoSprite on = attached.get(over).parentSprite;
                                    if (on != null && FireLooks.isLook(on)) {
                                        continue;
                                    }
                                    thing = sum(thing, shown(on, dir));
                                    restless |= moves(on);
                                }
                            }
                            // The game marks nothing when an item's model it was loading is ready, or its mesh is read, or when
                            // its option to draw items as models is changed.
                            if (object instanceof IsoWorldInventoryObject loose) {
                                thing = sum(thing, ItemModels.shown() ? 2 : 3);
                                if (ItemModels.stillWaiting(loose)) {
                                    thing = sum(thing, 1);
                                    restless = true;
                                }
                            }
                            if (object instanceof IsoTelevision set) {
                                // The game marks the chunk when a set goes on or off or between the bars and a programme, each of
                                // which is another overlay sprite (IsoTelevision.updateTvScreen). It marks nothing for another channel or a tape.
                                int showing = Screens.showing(set);
                                level = sum(level, showing == Screens.PROGRAMME ? Screens.PROGRAMME + Screens.from(set) : showing);
                            }
                            if (object instanceof IsoLightSwitch string && object.getSprite() != null && Lights.slice(object.getSprite()) != Lights.NONE) {
                                // A string of lights that goes on or off: its bulbs are built burning or out.
                                thing = sum(thing, burns(string) ? 6 : 5);
                            }
                            thing = BloodBuilder.onWall(thing, object);
                            if (object instanceof IsoDoor door) {
                                thing = sum(thing, door.IsOpen() ? 2 : 1);
                            } else if (object instanceof IsoThumpable thumpable && thumpable.isDoor()) {
                                thing = sum(thing, thumpable.IsOpen() ? 4 : 3);
                            }
                        }
                        ArrayList<IsoMovingObject> bodies = square.getStaticMovingObjects();
                        for (int k = 0; k < bodies.size(); k++) {
                            IsoMovingObject body = bodies.get(k);
                            thing = sum(sum(sum(thing, Float.floatToRawIntBits(body.getX())), Float.floatToRawIntBits(body.getY())), Float.floatToRawIntBits(body.getZ()));
                            if (body instanceof IsoDeadBody dead) {
                                long picture = pictured(dead);
                                thing = sum(thing, picture);
                                // The game marks nothing when a picture it was asked for is made.
                                restless |= picture == 0 && (askPicture(dead) || dead.getAtlasTexture() != null);
                            }
                        }
                        thing = sum(thing, LimbDecals.sumAt(square.getX(), square.getY(), z));
                        level = sum(sum(level, lit), thing);
                        if (AUDIT_MARKS) {
                            auditLights[(z - from) * span + at] = lit;
                            auditThings[(z - from) * span + at] = thing;
                        }
                    }
                }
                readSums[z - from] = level;
            }
            for (int z = from; z <= to; z++) {
                sum = sum(sum, readSums[z - from]);
            }
            if (restless) {
                tollRestless++;
            }
            return sum;
        }
        /** What the last reading of a chunk came to level by level, from readFrom to readTo, with ChunkMarks' counts for the light of each as they were before it, and whether it took any level's sum from a build. */
        private long[] readSums = new long[8];
        /** The land's part of the last reading, which signature leaves out of its sum: see ChunkMeshes.landSum. */
        private long readLand;
        private int[] readLit = new int[8];
        private int readFrom, readTo, readLoose;
        private long readUnseen;
        private boolean readByLevel;

        /** Dev only: keeps with a build what its sum was made of. */
        private void auditKeep(ChunkMeshes fresh, boolean summed) {
            if (!summed) {
                fresh.auditLights = null;
                return;
            }
            int length = (keptTo - keptFrom + 1) * lightRed.length;
            if (fresh.auditLights == null || fresh.auditLights.length < length) {
                fresh.auditLights = new long[length];
                fresh.auditThings = new long[length];
            }
            System.arraycopy(auditLights, 0, fresh.auditLights, 0, length);
            System.arraycopy(auditThings, 0, fresh.auditThings, 0, length);
            fresh.auditTerrain = auditTerrain;
            fresh.auditTrees = auditTrees;
            fresh.auditFrom = keptFrom;
            fresh.auditTo = keptTo;
        }

        /**
         * Dev only: a chunk's sum has come out other than its build's. Says whether the game had
         * marked the chunk or one beside it since, and for one it had not, what of the sum changed.
         */
        private void audit(IsoChunk chunk, ChunkMeshes current, float distance, long start, int why) {
            int now = ChunkMarks.around(chunk.wx, chunk.wy);
            if ((why & 31) != 0) {
                tollMarkedChanges++;
                return;
            }
            // Marked by the reading itself: a light this reading was the first to ask for, which pullLights should have asked for before.
            boolean byReading = now != current.marks;
            if (byReading) {
                tollMarkedByReading++;
            } else {
                tollMissedChanges++;
            }
            if (auditLogged++ > 4000) {
                return;
            }
            StringBuilder what = new StringBuilder();
            what.append(String.format(Locale.ROOT, "chunk marks: %s at %d, %d, %.0f tiles off, %.1f s old, built %.2f s ago, plain %b, read because %d, on the game's screen %b:",
                    byReading ? "LATE LIGHT" : "MISSED", chunk.wx, chunk.wy, distance,
                    (start - current.bornNanos) / 1e9, (start - current.builtNanos) / 1e9, current.plain, why, chunk.getRenderLevels(playerIndex).isOnScreen(0)));
            if (current.auditLights == null || current.auditFrom != keptFrom || current.auditTo != keptTo) {
                what.append(" levels ").append(current.auditFrom).append("..").append(current.auditTo).append(" now ").append(keptFrom).append("..").append(keptTo);
                Log.info(what.toString());
                return;
            }
            if (current.auditTerrain != auditTerrain) {
                what.append(" terrain");
            }
            if (current.auditTrees != auditTrees) {
                what.append(" trees");
            }
            int span = lightRed.length, litOwn = 0, litRing = 0, thingOwn = 0, thingRing = 0, named = 0;
            for (int z = keptFrom; z <= keptTo; z++) {
                for (int at = 0; at < span; at++) {
                    int i = at / LIGHT_SPAN, j = at % LIGHT_SPAN;
                    boolean own = i >= 1 && i <= CHUNK_TILES && j >= 1 && j <= CHUNK_TILES;
                    int index = (z - keptFrom) * span + at;
                    if (current.auditLights[index] != auditLights[index]) {
                        if (own) {
                            litOwn++;
                        } else {
                            litRing++;
                        }
                    }
                    if (current.auditThings[index] != auditThings[index]) {
                        if (own) {
                            thingOwn++;
                        } else {
                            thingRing++;
                        }
                        if (named++ < 3) {
                            IsoGridSquare square = squareAt(chunk.wx * CHUNK_TILES, chunk.wy * CHUNK_TILES, chunk.wx * CHUNK_TILES + i - 1, chunk.wy * CHUNK_TILES + j - 1, z);
                            what.append(" [").append(chunk.wx * CHUNK_TILES + i - 1).append(',').append(chunk.wy * CHUNK_TILES + j - 1).append(',').append(z);
                            if (square == null) {
                                what.append(" gone");
                            } else {
                                what.append(current.auditThings[index] == -1 ? " new" : "").append(" room ").append(square.getRoomID()).append(unexplored(square) ? " unexplored" : "");
                                PZArrayList<IsoObject> objects = square.getObjects();
                                for (int k = 0; k < objects.size(); k++) {
                                    IsoSprite sprite = objects.get(k).getSprite();
                                    Texture texture = sprite == null ? null : sprite.getTextureForCurrentFrame(objects.get(k).getDir());
                                    what.append(' ').append(objects.get(k).getClass().getSimpleName()).append(':').append(sprite == null ? null : sprite.getName())
                                            .append('/').append(texture == null ? null : texture.getName());
                                }
                                what.append(" bodies ").append(square.getStaticMovingObjects().size());
                            }
                            what.append(']');
                        }
                    }
                }
            }
            what.append(String.format(Locale.ROOT, " lights own %d ring %d, things own %d ring %d", litOwn, litRing, thingOwn, thingRing));
            Log.info(what.toString());
        }

        /**
         * The cosine of the widest angle, seen from above, between the way the view faces and
         * anything in the picture: the angle to the picture's corners, which a pitched or rolled
         * view swings out sideways, and a margin. -1, which leaves nothing out, when the view
         * looks so far up or down that a corner points behind it or the picture has no shape yet.
         */
        private float cosDrawnCone(ViewCamera.Pose pose, float flat) {
            float aspect = drawnAspect;
            if (aspect == 0f || flat <= 1e-3f) {
                return -1f;
            }
            float widest = 0f;
            for (int i = 0; i < 4; i++) {
                float across = ((i & 1) == 0 ? -1f : 1f) * tanHalfFieldOfView * aspect;
                float along = (i < 2 ? -1f : 1f) * tanHalfFieldOfView;
                float x = pose.forwardX + pose.rightX * across + pose.upX * along;
                float y = pose.forwardY + pose.rightY * across + pose.upY * along;
                float length = (float) Math.sqrt(x * x + y * y);
                float cos = (x * pose.forwardX + y * pose.forwardY) / (length * flat);
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
         * change when its owner walks through a door. Where the character stands, in a room or
         * out of doors, and how strong the sun or moon is, give the exposure for each. A roof that
         * is no room's, a gazebo's, a stall's or a porch's, has the open air round it.
         *
         * Between those two the eye goes by what it sees: the light meter's reading of the picture
         * (Pipeline.meter). At a window looking out into the sun it stops down towards the open
         * air's exposure, and turned back to the room it opens up again, faster to close than to
         * open.
         */
        private float adaptEye(IsoPlayer player, boolean fromOutside) {
            IsoGridSquare under = player.getCurrentSquare();
            // Seen from outside the picture is of the open air, whatever roof the viewer is under.
            float target = fromOutside || under == null || under.isOutside() || under.getRoom() == null ? 1f : 0f;
            long now = System.nanoTime();
            float elapsed = lastAdapt == 0 ? 1f : Math.min((now - lastAdapt) / 1e9f, 1f);
            lastAdapt = now;
            eyeOpen = eyeOpen < 0f ? target : eyeOpen + (target - eyeOpen) * (1f - (float) Math.exp(-elapsed / EYE_ADAPT_SECONDS));
            sky.outdoors = eyeOpen;
            float open = under == null || under.isOutside() ? 1f : 0f;
            openOver = openOver < 0f ? open : openOver + (open - openOver) * (1f - (float) Math.exp(-elapsed / EYE_ADAPT_SECONDS));
            sky.openOver = openOver;
            float strongest = Math.max(sky.lightRed, Math.max(sky.lightGreen, sky.lightBlue));
            // Out of doors by day, sunlit ground comes out about as bright as the sprite was painted.
            // As the game's own light for the open ground falls, towards night, the eye opens up to
            // it, though never so far that night stops being dark.
            float gameOpen = (floorRed + floorGreen + floorBlue) / 3f;
            float ground = (float) Math.pow(Evening.dimmed((unseenRed + unseenGreen + unseenBlue) / 3f, gameOpen, sky.daylightKept), 2.2);
            float outdoors = Math.min(1.4f / (0.8f * ground + 1.83f * (float) Math.pow(strongest, 2.2)), EXPOSURE_MOST);
            // An eye in the dark does not get back what the day had: it sees less, and that is
            // what makes a lit window or a pair of headlights the brightest thing in the night.
            outdoors *= 1f - NIGHT_HELD_BACK * sky.night;
            // Snow throws back most of the little light a night has, nine times what a road does: the eye
            // stops down to a white land, or a night of snow comes out as bright as a day.
            outdoors /= 1f + SNOW_STOPS_DOWN * sky.snowLying * sky.night;
            float here = 1f, roomHere = 1f;
            if (under != null) {
                // Without the viewer's own torch. The game has the torch on the square the character
                // has just stepped onto and takes it off again half a second later, so an eye that
                // went by the square's whole light shut and opened once for every tile walked.
                // As the frame lights the square, with the day its room lets in (set up as the frame before was built).
                ColorInfo light = daylit(under, view.torchLess.light(under, player.getIndex(), torchless));
                here = (float) Math.pow(Evening.dimmed((light.r + light.g + light.b) / 3f, gameOpen, sky.daylightKept), 2.2);
                roomHere = (float) Math.pow(Evening.dimmed((light.r + light.g + light.b) / 3f, gameOpen, sky.daylightKept * sky.roomKept), 2.2);
            }
            // By day an eye indoors opens up to a dim room. At night it has less to open with: an
            // unlit room stays dark, and one with a lamp on is seen by the lamp.
            float most = EXPOSURE_MOST + (EXPOSURE_MOST_AT_NIGHT - EXPOSURE_MOST) * sky.night;
            // Among trees most of what the eye sees is in shade, and it opens up to that as it
            // does indoors, though less.
            int trees = 0;
            if (under != null) {
                IsoCell round = IsoWorld.instance.currentCell;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        IsoGridSquare near = round.getGridSquare(under.getX() + dx, under.getY() + dy, under.getZ());
                        if (near != null && near.HasTree()) {
                            trees++;
                        }
                    }
                }
            }
            float wooded = Math.min(trees / 9f, 1f);
            shaded += (wooded - shaded) * (1f - (float) Math.exp(-elapsed / EYE_ADAPT_SECONDS));
            outdoors *= 1f + WOODS_OPEN * shaded;
            // Opened up as it was to the game's light, and further by as much as the room's daylight is taken down.
            float indoors = Math.min(EXPOSURE_INDOORS / Math.max(here, 1e-3f), most) * Math.max(here, 1e-3f) / Math.max(roomHere, 1e-3f);
            located = indoors + (outdoors - indoors) * eyeOpen;
            metered = view.pipeline.meteredLight();
            // A mean of the view's light in stops barely moves for a bright window in a dim
            // room: the eye is also stopped down for the brightest tenth of what it sees.
            float upper = view.pipeline.meteredUpper();
            if (!Float.isNaN(upper)) {
                metered = Math.max(metered, upper - UPPER_OVER_MEAN);
            }
            // Until the meter's first reading has come back from the card.
            if (Float.isNaN(metered)) {
                eyeSeen = located;
                return located;
            }
            float key = METER_KEY_INDOORS + (METER_KEY_OUTDOORS - METER_KEY_INDOORS) * eyeOpen;
            // The room's key is for a view of the room. As much of the view as is brighter than
            // the room, a window with the day through it, is taken at the open air's key, or the
            // eye stays open for the room with the day blown out to white in front of it.
            float daylit = Math.clamp((metered - (METER_KEY_INDOORS - (float) (Math.log(indoors) / Math.log(2.0)))) / ROOM_TO_STREET, 0f, 1f);
            key += (METER_KEY_OUTDOORS - key) * daylit;
            float seen = Math.clamp((float) Math.pow(2.0, key - metered), Math.min(indoors, outdoors), Math.max(indoors, outdoors));
            if (eyeSeen < 0f) {
                eyeSeen = seen;
            }
            float seconds = seen < eyeSeen ? EYE_CLOSES_SECONDS : EYE_OPENS_SECONDS;
            eyeSeen *= (float) Math.pow(seen / eyeSeen, 1f - Math.exp(-elapsed / seconds));
            return eyeSeen;
        }

        /**
         * The lamps that burn near enough to reach a square of a chunk or of the ring of squares
         * round it, into lampsNear, and the room each stands in into lampRooms. The map's lamps
         * that burn are sorted by x once a frame, and only those within the farthest reach of
         * any of them along x are looked at.
         */
        private void lampsRound(int chunkX, int chunkY) {
            if (lampsSortedFrame != frames) {
                lampsSortedFrame = frames;
                lampsBurning.clear();
                farthestLamp = 0;
                Stack<IsoLightSource> lamps = cell.getLamppostPositions();
                for (int i = 0; i < lamps.size(); i++) {
                    IsoLightSource lamp = lamps.get(i);
                    if (lamp.active) {
                        lampsBurning.add(lamp);
                        farthestLamp = Math.max(farthestLamp, lamp.radius);
                    }
                }
                lampsBurning.sort((one, other) -> Integer.compare(one.x, other.x));
            }
            lampsNear.clear();
            int low = 0, high = lampsBurning.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (lampsBurning.get(middle).x < chunkX - 1 - farthestLamp) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            for (int i = low; i < lampsBurning.size() && lampsBurning.get(i).x <= chunkX + CHUNK_TILES + farthestLamp; i++) {
                IsoLightSource lamp = lampsBurning.get(i);
                if (lamp.x + lamp.radius >= chunkX - 1 && lamp.x - lamp.radius <= chunkX + CHUNK_TILES && lamp.y + lamp.radius >= chunkY - 1
                        && lamp.y - lamp.radius <= chunkY + CHUNK_TILES) {
                    lampsNear.add(lamp);
                }
            }
            if (lampRooms.length < lampsNear.size()) {
                lampRooms = new long[lampsNear.size() * 2];
            }
            for (int i = 0; i < lampsNear.size(); i++) {
                IsoLightSource lamp = lampsNear.get(i);
                IsoGridSquare stands = cell.getGridSquare(lamp.x, lamp.y, lamp.z);
                lampRooms[i] = stands == null ? TorchLess.NOT_THERE : stands.getRoomID();
            }
        }
        private final ArrayList<IsoLightSource> lampsBurning = new ArrayList<>();
        private int lampsSortedFrame = -1, farthestLamp;
        private long[] lampRooms = new long[16];
        /** For each square readLights last read, whether a lamp burns in its room and reaches it, once asked. */
        private final byte[] lampAt = new byte[LIGHT_SPAN * LIGHT_SPAN];
        /** The room last asked in the reading readLights last made whether its lights are on, and the answer. */
        private IsoRoom roomAsked;
        private boolean roomAskedLit;

        /**
         * The lighting engine's pass the lights of the chunk being built were read in. Where the
         * build takes a level's lights from the reading made before it (kept), that is the
         * reading's pass, which may be one the engine has gone past: a square asked again in it
         * gives what the build's own lights were read as, and a floor and its square agree.
         */
        private int lightPass;

        /**
         * A square's light for the picture as the build takes it: the game's, asked once in the
         * lighting pass the chunk's lights were read in, without the viewer's torch. Written into
         * torchless or one of SquareLights' own, and good until the next asking.
         */
        ColorInfo lightOf(IsoGridSquare square) {
            return view.torchLess.light(square, squareLights.of(square, playerIndex, lightPass), torchless);
        }

        /** Whether each room met in a reading of lights is seen: see roomSeen. */
        private final HashMap<Long, Boolean> roomsSeenInReading = new HashMap<>();

        /** Whether the player has seen a room, as RoomsSeen takes it from its squares on its own level. */
        private boolean roomSeen(long room) {
            Boolean known = roomsSeenInReading.get(room);
            if (known == null) {
                RoomDef def = IsoWorld.instance.getMetaGrid().getRoomDefByID(room);
                int[] rects = new int[def.rects.size() * 4];
                for (int i = 0; i < def.rects.size(); i++) {
                    RoomDef.RoomRect rect = def.rects.get(i);
                    rects[i * 4] = rect.x;
                    rects[i * 4 + 1] = rect.y;
                    rects[i * 4 + 2] = rect.w;
                    rects[i * 4 + 3] = rect.h;
                }
                int level = def.level;
                known = RoomsSeen.most(rects, (x, y) -> {
                    IsoGridSquare square = cell.getGridSquare(x, y, level);
                    return square == null ? RoomsSeen.NONE : square.isSeen(playerIndex) ? RoomsSeen.SEEN : RoomsSeen.UNSEEN;
                });
                roomsSeenInReading.put(room, known);
            }
            return known;
        }

        private final ColorInfo hallLight = new ColorInfo();

        /**
         * The light of a square in the upper part of a tall hall: the most of each colour the
         * game gives it or any square under it down to the hall's floor. The game lights each
         * level by itself, where the hall is one room from its floor to its ceiling. Written
         * into hallLight and good until the next asking.
         */
        ColorInfo hallLightOf(IsoGridSquare square, IsoGridSquare hallFloor) {
            ColorInfo light = lightOf(square);
            float red = light.r, green = light.g, blue = light.b, cover = light.a;
            for (int z = square.getZ() - 1; z >= hallFloor.getZ(); z--) {
                light = lightOf(cell.getGridSquare(square.getX(), square.getY(), z));
                red = Math.max(red, light.r);
                green = Math.max(green, light.g);
                blue = Math.max(blue, light.b);
            }
            hallLight.r = red;
            hallLight.g = green;
            hallLight.b = blue;
            hallLight.a = cover;
            return hallLight;
        }

        private final ColorInfo daylit = new ColorInfo();

        /**
         * A square's light as the frame lights it: in a room, where the frame lights rooms by
         * the day, at least the share of the open ground's light its room lets in (RoomDaylight).
         * Written into daylit and good until the next asking, or the light given back as it is.
         */
        ColorInfo daylit(IsoGridSquare square, ColorInfo light) {
            if (!roomsDaylit || day <= 0f || square.getRoomID() == -1L) {
                return light;
            }
            float share = dayShare(square);
            daylit.r = Math.max(light.r, share * dayLight[0]);
            daylit.g = Math.max(light.g, share * dayLight[1]);
            daylit.b = Math.max(light.b, share * dayLight[2]);
            daylit.a = light.a;
            return daylit;
        }

        /** The share of the open ground's light a square's room lets in by day (RoomDaylight), whatever the hour: 0 out of a room, or where rooms are not lit by the day. */
        float dayShare(IsoGridSquare square) {
            return !roomsDaylit || square.getRoomID() == -1L ? 0f
                    : roomDaylight.share(roomMap, square, mapWx * CHUNK_TILES, mapWy * CHUNK_TILES, mapSide * CHUNK_TILES, daylitAt);
        }

        /** lampBurns for the square readLights last read at a place in its grid, worked out once a reading. */
        private boolean lampBurnsAt(int at, IsoGridSquare square) {
            if (lampAt[at] == LAMP_UNASKED) {
                lampAt[at] = burnsNear(square) ? LAMP_BURNS : LAMP_OUT;
            }
            return lampAt[at] == LAMP_BURNS;
        }

        /** lampBurns, with the lamps lampsRound found and the rooms they stand in. */
        private boolean burnsNear(IsoGridSquare square) {
            IsoRoom room = square.getRoom();
            if (room == null) {
                return false;
            }
            if (room != roomAsked) {
                roomAsked = room;
                roomAskedLit = roomLightsOn(room);
            }
            if (roomAskedLit) {
                return true;
            }
            for (int i = 0; i < lampsNear.size(); i++) {
                if (reaches(lampsNear.get(i), square) && lampRooms[i] == square.getRoomID()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * The light of every square of one level of a chunk and of the ring of squares round it,
         * into lightRed, lightGreen, lightBlue and lightOpen, a LIGHT_SPAN wide grid whose first
         * row and column are the squares just outside the chunk. lightOpen is 1 out of doors, 0
         * under a roof and -1 where there is no square.
         */
        private void readLights(int chunkX, int chunkY, int z) {
            lightPass = LightingJNI.getUpdateCounter(playerIndex);
            Arrays.fill(lampAt, LAMP_UNASKED);
            roomAsked = null;
            roomsSeenInReading.clear();
            for (int i = 0; i < LIGHT_SPAN; i++) {
                for (int j = 0; j < LIGHT_SPAN; j++) {
                    boolean inChunk = i >= 1 && i <= CHUNK_TILES && j >= 1 && j <= CHUNK_TILES;
                    IsoGridSquare square = squareAt(chunkX, chunkY, chunkX + i - 1, chunkY + j - 1, z);
                    int at = i * LIGHT_SPAN + j;
                    lightSquare[at] = square;
                    if (z == 0) {
                        groundObject[at] = square == null ? null : square.getFloor();
                        groundKind[at] = square == null ? Terrain.UNKNOWN : Terrain.kind(square, groundObject[at]);
                        groundTexture[at] = textureOf(groundObject[at]);
                        groundRank[at] = groundKind[at] == Terrain.NATURAL ? rank(groundObject[at].getSprite()) : 0;
                        groundLone[at] = groundKind[at] == Terrain.NATURAL && !naturalBeside(chunkX, chunkY, chunkX + i - 1, chunkY + j - 1);
                    }
                    if (square == null) {
                        lightOpen[at] = -1;
                        lightDay[at] = 0f;
                        continue;
                    }
                    // Read from the lighting engine, which holds every loaded square. getLightInfo is
                    // a copy the game's own render takes of it, and only for the squares its camera
                    // has on screen.
                    // The upper part of a tall hall is the hall, and is lit as its floor is: by
                    // whether the player has seen it, by the day the hall lets in and by its lamps.
                    // A row left out of the upper room is in no room at all.
                    IsoGridSquare hallFloor = TallHalls.floorOf(cell, square.getX(), square.getY(), z);
                    IsoGridSquare lighting = hallFloor != null ? hallFloor : square;
                    ColorInfo light = hallFloor != null ? hallLightOf(square, hallFloor) : lightOf(square);
                    boolean outside = square.isOutside();
                    // As the game's own view a room keeps the engine's light whether or not it has
                    // been seen: none in a room the player has not seen into, and less in one
                    // they have seen and cannot see now, which is how the game shows them.
                    // From the eye a room is seen or not as a whole (RoomsSeen).
                    boolean inRoom = lighting.getRoomID() != -1L;
                    boolean seen = inRoom && !asTheGameShows && !gamesView ? roomSeen(lighting.getRoomID()) : lighting.isSeen(playerIndex);
                    byte lit = OWN_LIGHT;
                    // Under the ground a square in no room is the earth round a basement, which the game marks out of doors: it keeps the game's light.
                    if (!seen && !(gamesView && inRoom) && (inRoom || z >= 0)) {
                        // From the eye a room the game has not shown is a room like the ones in sight: see seenRooms.
                        // Shown only as the game shows it, it stands empty and lit as the open ground is.
                        lit = !inRoom || asTheGameShows ? OPEN_LIT : lampBurnsAt(at, lighting) ? LAMP_LIT : ROOM_LIT;
                        light = SHADE_ONLY;
                    } else if (seen && inChunk) {
                        squaresSeen++;
                        if (outside && tallying) {
                            reading.add(light.r, light.g, light.b);
                        } else if (inRoom && tallying && !gamesView && lighting.isCanSee(playerIndex)) {
                            // In sight now: one seen and out of sight has the game's darkening on it.
                            if (Math.max(light.r, Math.max(light.g, light.b)) > LAMPLIT && lampBurnsAt(at, lighting)) {
                                readingLamplit.add(light.r, light.g, light.b);
                            } else {
                                readingRooms.add(light.r, light.g, light.b);
                            }
                        }
                    }
                    if (lit == OWN_LIGHT) {
                        light = daylit(lighting, light);
                    }
                    lightRed[at] = light.r;
                    lightGreen[at] = light.g;
                    lightBlue[at] = light.b;
                    lightOpen[at] = (byte) (outside ? 1 : 0);
                    lightUnseen[at] = lit;
                    // A room the game has not lit takes its day in the picture, which goes by the hour without a build.
                    lightDay[at] = lit == ROOM_LIT || lit == LAMP_LIT ? dayShare(lighting) : 0f;
                    // The game has no lamp in a canopy. A lit one lights what is under it, and what the game has given no light by itself.
                    if (outside && z >= 0 && ceilingBuilder.underLitCanopy(chunkX, chunkY, chunkX + i - 1, chunkY + j - 1, z)) {
                        boolean unlit = lit != OWN_LIGHT;
                        lightRed[at] = unlit ? Soffits.GLOW_RED : Soffits.under(light.r, Soffits.GLOW_RED);
                        lightGreen[at] = unlit ? Soffits.GLOW_GREEN : Soffits.under(light.g, Soffits.GLOW_GREEN);
                        lightBlue[at] = unlit ? Soffits.GLOW_BLUE : Soffits.under(light.b, Soffits.GLOW_BLUE);
                        lightUnseen[at] = OWN_LIGHT;
                        lightDay[at] = 0f;
                    }
                }
            }
        }

        /** The openness a face lit as the square at the given place of the light grid is takes: see LIT_STEP. */
        float openOf(int at) {
            return lightOpen[at] + LIT_STEP * lightUnseen[at];
        }

        /**
         * Sets the frame's light for the square at (localX, localY) of the chunk readLights last
         * read. Each corner takes the mean of the squares that meet at it, so two squares agree
         * about the corner they share and the light has no step between them. Squares on the other
         * side of a roof's edge are left out of the mean: a room is not lit by the garden through
         * its wall. A square the game has given no light counts as 1, and the corner keeps how
         * much of its light such squares are (cornerUnlit), of one kind of them: the square's
         * own, or for one the game has lit the first kind round it (unlitKind), and the mean of
         * what their rooms let in of the day (cornerDay). A square of another kind is left out.
         */
        void cornerLights(int localX, int localY) {
            int own = (localX + 1) * LIGHT_SPAN + localY + 1;
            frame.red = lightRed[own];
            frame.green = lightGreen[own];
            frame.blue = lightBlue[own];
            frame.open = openOf(own);
            byte kind = unlitKindRound(own);
            frame.unlitKind = kind;
            for (int corner = 0; corner < 4; corner++) {
                int i = localX + (corner == 1 || corner == 2 ? 1 : 0), j = localY + (corner >= 2 ? 1 : 0);
                float red = 0f, green = 0f, blue = 0f, given = 0f, day = 0f;
                int count = 0, unlit = 0;
                for (int di = 0; di <= 1; di++) {
                    for (int dj = 0; dj <= 1; dj++) {
                        int at = (i + di) * LIGHT_SPAN + j + dj;
                        if (lightOpen[at] == lightOpen[own] && (lightUnseen[at] == OWN_LIGHT || lightUnseen[at] == kind)) {
                            red += lightRed[at];
                            green += lightGreen[at];
                            blue += lightBlue[at];
                            count++;
                            if (lightUnseen[at] == OWN_LIGHT) {
                                given += (lightRed[at] + lightGreen[at] + lightBlue[at]) / 3f;
                            } else {
                                unlit++;
                                day += lightDay[at];
                            }
                        }
                    }
                }
                frame.cornerRed[corner] = red / count;
                frame.cornerGreen[corner] = green / count;
                frame.cornerBlue[corner] = blue / count;
                frame.cornerUnlit[corner] = unlitShare(given, unlit);
                frame.cornerDay[corner] = unlit == 0 ? 0f : day / unlit;
            }
        }

        /** How much of a mean of squares the ones the game has not lit are, each counted as 1, beside the light of the ones it has lit, summed as their grey. */
        static float unlitShare(float given, int unlit) {
            return unlit == 0 ? 0f : unlit / (given + unlit);
        }

        /** The kind of light the game has not given that the square at the given place of the light grid is lit by, or for one it has lit the first such kind of a square round it with the same openness, or OWN_LIGHT. */
        byte unlitKindRound(int own) {
            if (lightUnseen[own] != OWN_LIGHT) {
                return lightUnseen[own];
            }
            int ownI = own / LIGHT_SPAN, ownJ = own % LIGHT_SPAN;
            for (int i = Math.max(ownI - 1, 0); i <= Math.min(ownI + 1, LIGHT_SPAN - 1); i++) {
                for (int j = Math.max(ownJ - 1, 0); j <= Math.min(ownJ + 1, LIGHT_SPAN - 1); j++) {
                    int at = i * LIGHT_SPAN + j;
                    if (lightOpen[at] == lightOpen[own] && lightUnseen[at] != OWN_LIGHT) {
                        return lightUnseen[at];
                    }
                }
            }
            return OWN_LIGHT;
        }

        /** Finds the trees on and round a chunk, and what each drops. */
        private void readWoods(int chunkX, int chunkY) {
            for (int i = 0; i < WOOD_SPAN; i++) {
                for (int j = 0; j < WOOD_SPAN; j++) {
                    int localX = i - Litter.RING, localY = j - Litter.RING;
                    IsoGridSquare square = squareAt(chunkX, chunkY, chunkX + localX, chunkY + localY, 0);
                    woods[i * WOOD_SPAN + j] = square != null && square.HasTree() ? treeDrops(square, Flora.otherHue(Seasons.treeHash(chunkX + localX, chunkY + localY))) : Litter.NONE;
                }
            }
        }

        /**
         * What the tree on a square that has one drops (Litter.kindOf), by its sprite: the game
         * marks a square as having a tree for any object of the tree's type on it.
         */
        private byte treeDrops(IsoGridSquare square, boolean otherHue) {
            PZArrayList<IsoObject> objects = square.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                IsoObject object = objects.get(i);
                if (object.getType() == IsoObjectType.tree) {
                    if (object.getSprite() == null) {
                        break;
                    }
                    Kind kind = kindOf(object.getSprite());
                    return kind.drops(otherHue);
                }
            }
            return Litter.kindOf(null, otherHue);
        }

        /**
         * Adds what the game draws over an object, as of the object's stuff: ivy and stains over a
         * wall are the wall's. A sheet pinned over it is print of its own, and the next overlay
         * of the object is of the object's stuff again.
         */
        private void addOver(IsoObject object, IsoSprite under, IsoSprite over, boolean isFloor, int x, int y, int z) {
            float material = frame.material;
            addOverOf(object, under, over, isFloor, x, y, z);
            frame.material = material;
        }

        private void addOverOf(IsoObject object, IsoSprite under, IsoSprite over, boolean isFloor, int x, int y, int z) {
            // The game's flames and smoke are drawn by the picture as fire, never as a card: see Fires.
            if (over == null || FireLooks.isLook(over)) {
                return;
            }
            Texture texture = over.getTextureForCurrentFrame(object.getDir());
            if (!usable(texture)) {
                return;
            }
            frameOf(object, texture);
            standOn(object);
            frame.material = under.getType() == IsoObjectType.tree && Own.plants(view.index) ? LEAVES : kindOf(over).asOther == PRINT ? PRINT : frame.material;
            sprites++;
            // Leaves stand on the same card as their trunk. Each layer is moved a little towards
            // the eye from the one under it, or the two would be drawn through each other.
            overLayer++;
            geometry.nearer = standsNearer + overLayer;
            if (!isFloor && furnishingBuilder.addHung(object, over, texture, x, y, z)) {
                if (SightTrace.ON && building.modelled != null) {
                    building.modelled.add(object);
                }
                return;
            }
            if (!isFloor && furnishingBuilder.addCountertop(object, under, over, texture, x, y, z)) {
                return;
            }
            // A poster or a notice the game has pinned to a wall is laid over the wall's own sprite,
            // and stood as that sprite stands it goes onto both walls of a corner piece, on one of
            // them slewed. It says which wall it was painted for, and goes on that one. Only what
            // has a front: a stain or ivy has none and is the wall's own.
            if (!isFloor && over.getProperties().has(IsoFlagType.WallOverlay) && over.getFacing() != null) {
                int wall = WallHung.wallOf(over);
                if (wall == WallHung.NORTH || wall == WallHung.WEST) {
                    SpriteGeometry.Mesh mesh = solid.get(texture.getID());
                    overSheet = kindOf(over).tiledSheet;
                    if (wall == WallHung.WEST) {
                        wallBuilder.addEdge(mesh, texture, false, false, x, y, z, westLayer++, 0f, 1f);
                    } else {
                        wallBuilder.addEdge(mesh, texture, false, false, x, y, z, northLayer++, 1f, 0f);
                    }
                    overSheet = null;
                    return;
                }
            }
            // A skirting, a rail or a cornice is a moulding along its wall: see Trims.
            if (!isFloor && kindOf(over).trim) {
                if (Own.buildings() && addTrim(over, texture, x, y, z)) {
                    return;
                }
                // A panel the sprite keeps, a plinth of cut stone, is a surface of its own and
                // not the wall's: on a wall of upright boards it was grooved as boards.
                frame.material = kindOf(over).asOther;
            }
            // Where a street steps along a verge the map paints grass over half of the street's
            // square, to slant the edge, from the ground's own sheet or from the sheet of painted
            // kerbs that have grass behind them. That grass is the verge's, and the verge ends at the kerb:
            // it lies on the land's side of a kerb that crosses the square and nowhere on a street.
            // Land left level (the overhead view) has no kerbs, and there the grass is the edge, as the game paints it.
            vergeOver = isFloor && z == 0 && groundKind[(waterLocalX + 1) * LIGHT_SPAN + waterLocalY + 1] == Terrain.STREET
                    && over.getName() != null && (over.getName().startsWith("blends_natural") || over.getName().startsWith("street_curbs") && over.getName().contains("grass"));
            if (vergeOver && geometry.kerb == null && kerbs != null) {
                vergeOver = false;
                return;
            }
            // The map edges one open ground with another by painting half of the next ground's
            // picture over the square, and spreadGround lays that edge already: painted as well,
            // the half lay over it as a flat triangle. Not a half of a lower ground whose square
            // this one's buries whole: spreading, the higher ground covers the lower to where it
            // touches it, and the half is all a trail of it a square wide keeps. Nor of a ground
            // no square round has, a patch the map's own.
            int ground = (waterLocalX + 1) * LIGHT_SPAN + waterLocalY + 1;
            if (isFloor && z == 0 && groundKind[ground] == Terrain.NATURAL && lightOpen[ground] == 1
                    && over.getName() != null && over.getName().startsWith("blends_natural")) {
                int edged = rank(over);
                for (int di = -1; di <= 1; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        int at = ground + di * LIGHT_SPAN + dj;
                        if (at != ground && naturalOpen(at) && groundRank[at] == edged
                                && (edged > groundRank[ground] || !buried(at, groundRank[ground]))) {
                            return;
                        }
                    }
                }
            }
            int before = FirstPerson.TRACE ? worn(texture) : 0;
            // The map hangs a road's lines on the road's own floor, and what is added next is added as that floor's: see addFloor.
            paintOver = kindOf(over).paint;
            // Tiles the map lays over a wall, a splashback or a panel, hang on the wall and are added as its: see WallBuilder.addTiledOverlay.
            overSheet = kindOf(over).tiledWall ? over.getName().substring(0, over.getName().lastIndexOf('_') + 1) : null;
            addSprite(solid.get(texture.getID()), object, under, texture, isFloor, x, y, z);
            overSheet = null;
            paintOver = false;
            vergeOver = false;
            if (FirstPerson.TRACE) {
                tally(under, worn(texture) - before);
            }
        }

        /**
         * Builds the trim sprite over a wall as mouldings on the edges it is laid on. False
         * where it is drawn as its sprite: on no north or west edge, panelling, or not read yet,
         * in which case the build is made again once it is.
         */
        private boolean addTrim(IsoSprite over, Texture texture, int x, int y, int z) {
            PropertyContainer flags = over.getProperties();
            boolean corner = flags.has("attachedNW");
            boolean north = corner || flags.has(IsoFlagType.attachedN), west = corner || flags.has(IsoFlagType.attachedW);
            Trims.Band[] onNorth = north ? Trims.of(texture, frame, true) : null, onWest = west ? Trims.of(texture, frame, false) : null;
            if (north && onNorth == null || west && onWest == null) {
                building.signed = false;
                return false;
            }
            if (north && onNorth.length == 0 || west && onWest.length == 0 || !north && !west) {
                return false;
            }
            float base = z * ViewCamera.LEVEL_HEIGHT;
            Trims.Light light = (atX, atY, into) -> {
                float a = Math.clamp(atX - x, 0f, 1f), b = Math.clamp(atY - y, 0f, 1f);
                into[0] = (frame.cornerRed[0] * (1f - a) + frame.cornerRed[1] * a) * (1f - b) + (frame.cornerRed[3] * (1f - a) + frame.cornerRed[2] * a) * b;
                into[1] = (frame.cornerGreen[0] * (1f - a) + frame.cornerGreen[1] * a) * (1f - b) + (frame.cornerGreen[3] * (1f - a) + frame.cornerGreen[2] * a) * b;
                into[2] = (frame.cornerBlue[0] * (1f - a) + frame.cornerBlue[1] * a) * (1f - b) + (frame.cornerBlue[3] * (1f - a) + frame.cornerBlue[2] * a) * b;
                into[3] = frame.unlitAt((1f - a) * (1f - b), a * (1f - b), a * b, (1f - a) * b);
                into[4] = frame.dayAt((1f - a) * (1f - b), a * (1f - b), a * b, (1f - a) * b);
            };
            for (int edge = 0; edge < 2; edge++) {
                Trims.Band[] bands = edge == 0 ? onNorth : onWest;
                if (bands == null) {
                    continue;
                }
                float da = edge == 0 ? 1f : 0f, db = 1f - da, rise = kerbTopRise(z, edge == 0);
                for (Trims.Band band : bands) {
                    SpriteGeometry.Mesh plain = solid.get(PLAIN, Trims.paint(band));
                    plain.open = frame.open;
                    plain.sway = 0f;
                    Trims.add(plain, band, x, y, da, db, db, da, base + geometry.liftAt(0f, 0f) + rise, base + geometry.liftAt(da, db) + rise, light);
                }
            }
            return true;
        }

        private int worn(Texture texture) {
            return solid.get(texture.getID()).floats + laid.get(texture.getID()).floats;
        }

        void frameOf(IsoObject object, Texture texture) {
            WorldScene.frameOf(frame, object, texture);
        }

        /**
         * Sets how high above its square's floor the object's sprite is to be stood: the game
         * draws a thing higher up the picture than its frame has it by an offset the thing
         * carries, a lamp put on a higher table than the one it was painted for.
         */
        private void standOn(IsoObject object) {
            geometry.raised = object.getRenderYOffset() / SURFACE_PIXELS_PER_LEVEL;
            building.plain &= geometry.raised == 0f;
        }

        /**
         * Which ground a sprite of open ground is, as a number that also says what spreads over
         * what. The game's sheets of ground hold one kind of ground to a row of sixteen sprites,
         * so every sprite of a row is the same ground and gets the same number. Grass, the rows
         * after the first and before the fifth, outranks bare ground: it is grass that grows out
         * over earth and sand, in tufts, and not the earth that creeps over the grass.
         */
        private boolean naturalOpen(int at) {
            return groundKind[at] == Terrain.NATURAL && lightOpen[at] == 1 && groundTexture[at] != null;
        }

        /**
         * Whether open ground of that rank touches every corner of square at, the four squares
         * round a corner counted, as FloorBuilder.spreadGround covers a square whole. A square on
         * the ring of the span has corners past it, which count as untouched.
         */
        private boolean buried(int at, int name) {
            int i = at / LIGHT_SPAN, j = at % LIGHT_SPAN;
            for (int corner = 0; corner < 4; corner++) {
                int ci0 = i + (corner == 1 || corner == 2 ? 0 : -1), cj0 = j + (corner >= 2 ? 0 : -1);
                boolean touches = false;
                for (int ci = ci0; ci <= ci0 + 1; ci++) {
                    for (int cj = cj0; cj <= cj0 + 1; cj++) {
                        int touching = ci * LIGHT_SPAN + cj;
                        touches |= ci >= 0 && cj >= 0 && ci < LIGHT_SPAN && cj < LIGHT_SPAN && touching != at
                                && naturalOpen(touching) && groundRank[touching] == name;
                    }
                }
                if (!touches) {
                    return false;
                }
            }
            return true;
        }

        int rank(IsoSprite sprite) {
            int[] known = ranks.get(sprite);
            if (known == null) {
                String name = sprite.getName();
                int cut = name.lastIndexOf('_');
                // The game's sheets all end in a number. A map's own tile need not, and is then of its sheet's first row.
                String number = name.substring(cut + 1);
                int row = !number.isEmpty() && number.length() <= 6 && number.chars().allMatch(Character::isDigit) ? Integer.parseInt(number) / 16 : 0;
                known = new int[] {1 + (name.substring(0, Math.max(cut, 0)).hashCode() & 0xffff) * 64 + (row >= 1 && row <= 3 ? 32 + row : row)};
                ranks.put(sprite, known);
            }
            return known[0];
        }

        /**
         * How well grass does at a point of the square being grown. A lawn does not stop at a
         * line: towards a path or a wall, and under trees, its grass is shorter, thinner and
         * browner, and the further in or under the more so.
         */
        @Override
        public float vigour(float a, float b) {
            float edge = 1f;
            if (!lawnWest) {
                edge = Math.min(edge, a / LAWN_EDGE);
            }
            if (!lawnEast) {
                edge = Math.min(edge, (1f - a) / LAWN_EDGE);
            }
            if (!lawnNorth) {
                edge = Math.min(edge, b / LAWN_EDGE);
            }
            if (!lawnSouth) {
                edge = Math.min(edge, (1f - b) / LAWN_EDGE);
            }
            return (1f - grownUnder * plantBuilder.woodedAt(grownLocalX, grownLocalY, a, b)) * (0.2f + 0.8f * edge * edge * (3f - 2f * edge));
        }

        @Override
        public boolean grows(float a, float b) {
            return geometry.kerb == null || !geometry.onStreet(a, b);
        }

        /** Where the kerbs of the chunk being built run, as Kerbs keeps it. Null where it has no street. */
        float[] kerbs;
        /** Whether the square being added has a kerb on it or along its edge. */
        boolean kerbed;
        /** Whether the square being added has street on it and a kerb along some stretch of its north edge, or of its west edge. */
        boolean kerbNorth, kerbWest;
        /** The square's ground and kerb as they were before standOnKerbTop lifted them, while it has. */
        private final float[] groundUnderKerb = new float[4];
        private float[] kerbUnderKerb;
        private boolean onKerbTop;

        /**
         * A kerb along the north or west edge of a level-0 square is the street's edge, and the
         * square's ground lies a kerb below the land on the far side of it. Whatever stands or
         * hangs on that edge (a wall and what is laid on it, its door, its window and the
         * window's curtain, an awning, a hood, a picture) stands on the kerb's top, at the land's
         * height, so that a run of wall is one height from end to end whether or not the kerb
         * comes up to it. How far that is above the square's own ground: a kerb where the
         * square is the street's, and 0 for any other edge and where a kerb crosses land.
         */
        float kerbTopRise(int z, boolean north) {
            return z == 0 && (north ? kerbNorth : kerbWest) && (geometry.kerb == null || geometry.kerbStreet) ? Terrain.KERB : 0f;
        }

        /**
         * Lifts the square's ground by kerbTopRise and clears its kerb field, for the one thing
         * on that edge being added, and says whether it did; leaveKerbTop, in a finally, puts
         * them back.
         */
        boolean standOnKerbTop(int z, boolean north) {
            if (z != 0 || !(north ? kerbNorth : kerbWest)) {
                return false;
            }
            if (onKerbTop) {
                throw new IllegalStateException("the square's ground is already on the kerb's top");
            }
            float rise = kerbTopRise(z, north);
            float[] lift = geometry.lift;
            System.arraycopy(lift, 0, groundUnderKerb, 0, lift.length);
            kerbUnderKerb = geometry.kerb;
            for (int corner = 0; corner < lift.length; corner++) {
                lift[corner] += rise;
            }
            geometry.kerb = null;
            onKerbTop = true;
            return true;
        }

        /** Puts back the square's ground as it was before standOnKerbTop. */
        void leaveKerbTop() {
            System.arraycopy(groundUnderKerb, 0, geometry.lift, 0, groundUnderKerb.length);
            geometry.kerb = kerbUnderKerb;
            kerbUnderKerb = null;
            onKerbTop = false;
        }

        /** The square beside the one being added whose ground lies beyond the kerb that crosses it, as a place in groundKind, or -1. */
        int beyondKerb;

        /** Whether what is being laid is grass the map paints over the edge of a street's square. */
        boolean vergeOver;
        /** Whether the sprite being added over an object's own is a marking painted on the ground: see RoadPaint. */
        boolean paintOver;

        /**
         * Where a square stands above water beside it there is a face between them: the wall of
         * a quay or a boardwalk.
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
            if (beside == Terrain.WATER && mine != Terrain.NATURAL) {
                mesh.open = frame.open + 32f * QUAY;
                SpriteGeometry.addPlainUpright(mesh, x, y, top, alongX, alongY, 0.5f, -QUAY_DEPTH, 0f, frame.red, frame.green, frame.blue);
            }
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

        /** How many strips a square's length of a banner is cut into along the banner, and up it: enough for it to belly in the wind. */
        private static final int BANNER_ALONG = 4, BANNER_UP = 3, BANNER_FLOATS = 11;
        /** How far inside its sprite's rectangle a banner's sheet takes its outermost texels, in frame dots: the middle of the outermost dot. */
        private static final float BANNER_INSET = 0.5f;
        /** The corners of one piece of a banner's sheet in the order of its two triangles: a bit for the further one along, a bit for the lower one. */
        private static final int[] BANNER_CORNERS = {0, 1, 3, 0, 3, 2};
        private final float[] bannerAt = new float[2];
        private final float[] bannerCorners = new float[(BANNER_ALONG + 1) * (BANNER_UP + 1) * BANNER_FLOATS];

        /**
         * A square's length of a banner strung across a street, or a banner on a lamp post's
         * arm: the whole of what the sprite paints, as a sheet in the plane the banner hangs
         * in, every dot of it where the game's view has it on that plane. See Banners.
         *
         * A strung banner is on the edge of its squares, and as high as its run has it: over
         * the line between the land at the run's two ends, where its cable is made fast, and
         * not over the land under the square, which steps at a kerb and rises with the road.
         * One on a post's arm is in the plane through the post. Both sides show the sprite, so
         * from behind the lettering reads backwards through the cloth.
         */
        private void addBanner(SpriteGeometry.Mesh mesh, boolean isStrung, int x, int y, int z) {
            boolean alongX = Banners.alongX(frame);
            float off = isStrung ? 0f : Banners.POST_AXIS, level = ViewCamera.LEVEL_HEIGHT;
            float[] run = null;
            float landFrom = 0f, landTo = 0f;
            if (isStrung) {
                long key = (long) x << 40 | (y & 0xFFFFFFL) << 16 | z & 0xFFFFL;
                run = bannerRuns.get(key);
                if (run == null) {
                    hungOn = z;
                    run = Banners.find(bannerMap, x, y, alongX);
                    bannerRuns.put(key, run);
                    if (FirstPerson.TRACE) {
                        Log.info("banner run: " + x + "," + y + "," + z + " " + Arrays.toString(run));
                    }
                }
                wantsMap |= Banners.wanting(run);
                float line = alongX ? y : x;
                landFrom = alongX ? landAt(run[Banners.FROM], line) : landAt(line, run[Banners.FROM]);
                landTo = alongX ? landAt(run[Banners.TO], line) : landAt(line, run[Banners.TO]);
            }
            // The sheet reaches the edge of the sprite's last dots, where the next square's first ones
            // begin. Its texels are taken from the middle of a dot at the nearest: further out
            // the sheet's next sprite over is mixed in, or nothing, and shows as a line down the join.
            float lastU = frame.left + frame.width - BANNER_INSET, lastV = frame.top + frame.height - BANNER_INSET;
            int row = (BANNER_ALONG + 1) * BANNER_FLOATS;
            for (int j = 0; j <= BANNER_UP; j++) {
                for (int i = 0; i <= BANNER_ALONG; i++) {
                    float u = frame.left + frame.width * i / BANNER_ALONG, v = frame.top + frame.height * j / BANNER_UP;
                    Banners.place(frame, alongX, off, u, v, bannerAt);
                    float along = bannerAt[0], levels = bannerAt[1];
                    float a = alongX ? along : off, b = alongX ? off : along;
                    float up, sway;
                    if (isStrung) {
                        float share = Banners.along(run, (alongX ? x : y) + along);
                        up = (z + levels) * level + landFrom + (landTo - landFrom) * share;
                        sway = Banners.sway(run, share, levels);
                    } else {
                        up = (z + levels) * level + geometry.liftAt(Banners.POST_AXIS, Banners.POST_AXIS);
                        sway = Banners.POSTED_SWAY * j / BANNER_UP;
                    }
                    float lightA = Math.clamp(a, 0f, 1f), lightB = Math.clamp(b, 0f, 1f);
                    float w0 = (1f - lightA) * (1f - lightB), w1 = lightA * (1f - lightB), w2 = lightA * lightB, w3 = (1f - lightA) * lightB;
                    int at = j * row + i * BANNER_FLOATS;
                    bannerCorners[at] = x + a;
                    bannerCorners[at + 1] = y + b;
                    bannerCorners[at + 2] = up;
                    float texelU = Math.clamp(u, frame.left + BANNER_INSET, lastU), texelV = Math.clamp(v, frame.top + BANNER_INSET, lastV);
                    bannerCorners[at + 3] = frame.uStart + (texelU - frame.left) / frame.width * (frame.uEnd - frame.uStart);
                    bannerCorners[at + 4] = frame.vStart + (texelV - frame.top) / frame.height * (frame.vEnd - frame.vStart);
                    bannerCorners[at + 5] = frame.cornerRed[0] * w0 + frame.cornerRed[1] * w1 + frame.cornerRed[2] * w2 + frame.cornerRed[3] * w3;
                    bannerCorners[at + 6] = frame.cornerGreen[0] * w0 + frame.cornerGreen[1] * w1 + frame.cornerGreen[2] * w2 + frame.cornerGreen[3] * w3;
                    bannerCorners[at + 7] = frame.cornerBlue[0] * w0 + frame.cornerBlue[1] * w1 + frame.cornerBlue[2] * w2 + frame.cornerBlue[3] * w3;
                    bannerCorners[at + 8] = sway;
                    bannerCorners[at + 9] = frame.unlitAt(w0, w1, w2, w3);
                    bannerCorners[at + 10] = frame.dayAt(w0, w1, w2, w3);
                }
            }
            mesh.open = frame.open + 32f * frame.material;
            mesh.face(alongX ? 0f : 1f, alongX ? 1f : 0f, 0f);
            for (int j = 0; j < BANNER_UP; j++) {
                for (int i = 0; i < BANNER_ALONG; i++) {
                    for (int corner : BANNER_CORNERS) {
                        int at = (j + (corner >> 1)) * row + (i + (corner & 1)) * BANNER_FLOATS;
                        mesh.sway = bannerCorners[at + 8];
                        mesh.unlit = bannerCorners[at + 9];
                        mesh.day = bannerCorners[at + 10];
                        mesh.add(bannerCorners[at], bannerCorners[at + 1], bannerCorners[at + 2], bannerCorners[at + 3], bannerCorners[at + 4],
                                bannerCorners[at + 5], bannerCorners[at + 6], bannerCorners[at + 7]);
                    }
                }
            }
            mesh.sway = 0f;
            mesh.litAsFace();
        }

        /** The map the run of a strung banner is found on, on the level hungOn. */
        private final Banners.Hung bannerMap = new Banners.Hung() {
            /** The picture of the length found last, and whether a length was passed over because its picture is not there to read yet. */
            private Texture picture;
            private boolean unread;

            @Override
            public int strung(int x, int y, boolean alongX) {
                int gridX = Math.floorDiv(x, CHUNK_TILES) - mapWx, gridY = Math.floorDiv(y, CHUNK_TILES) - mapWy;
                if (gridX < 0 || gridY < 0 || gridX >= mapSide || gridY >= mapSide) {
                    return -1;
                }
                IsoChunk hungIn = chunkMap.getChunk(gridX, gridY);
                if (hungIn == null || !hungIn.loaded) {
                    return -1;
                }
                // With a length unread, where the run goes from here is not known.
                return length(x, y, alongX) != null ? 1 : unread ? -1 : 0;
            }

            @Override
            public float top(int x, int y, boolean alongX, boolean atStart) {
                IsoObject length = length(x, y, alongX);
                float top = Banners.top(picture::isMaskSet, hungFrame, alongX, atStart);
                if (Float.isNaN(top)) {
                    // A retextured sheet can leave the cable short of the square's end: the
                    // length is still drawn, swaying from the top of its picture there.
                    String name = length.getSprite().getName();
                    if (bannersUnpainted.add(name)) {
                        if (DevBuild.ON) {
                            Log.info("the banner's length " + name + " at " + x + "," + y + "," + hungOn
                                    + " paints nothing at the end of its square, so its cable is taken to be at the top of its picture there");
                        }
                    }
                    top = Banners.frameTop(hungFrame, alongX, atStart);
                }
                return top;
            }

            /** The length on a square that runs the way asked, with hungFrame and picture set for it. */
            private IsoObject length(int x, int y, boolean alongX) {
                unread = false;
                IsoGridSquare square = cell.getGridSquare(x, y, hungOn);
                if (square == null) {
                    return null;
                }
                PZArrayList<IsoObject> objects = square.getObjects();
                for (int i = 0; i < objects.size(); i++) {
                    IsoObject object = objects.get(i);
                    IsoSprite sprite = object.getSprite();
                    if (sprite == null || kindOf(sprite).banner != Banners.STRUNG) {
                        continue;
                    }
                    Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
                    if (!usable(texture)) {
                        unread = true;
                        continue;
                    }
                    hungFrame.scale = frame.scale;
                    WorldScene.frameOf(hungFrame, object, texture);
                    if (Banners.alongX(hungFrame) == alongX) {
                        picture = texture;
                        return object;
                    }
                }
                return null;
            }
        };

        /** The level the wire hangs on whose span is being found, and the map the spans are found on. */
        int hungOn;
        final SpriteGeometry.Frame hungFrame = new SpriteGeometry.Frame();

        /** Which of the chunks round the one being built the build has taken the land's height from, a bit each: see landAt. */
        private int heightsRound;
        /** The chunks further off it has taken one from, each as its two numbers in one: the span of a wire ends at a pole some chunks away. */
        private long[] heightsFar = new long[8];
        private int heightsFarCount;
        /**
         * Whether the build has left something unknown because the map is not loaded that far: the span of a wire at an
         * end, a square of a piece of furniture painted on several.
         */
        boolean wantsMap;

        /**
         * Before the build takes the ground's height at a point that may be over another chunk: the
         * corner of a body's picture over the next one, the pole a wire's span ends at some chunks
         * away. A chunk's heights read as none until they are first worked out, so that chunk's
         * are worked out here if they are not yet, and the build remembers that it goes by them
         * (landOf): when they come out otherwise this chunk is built again.
         */
        private void heightsAt(float x, float y) {
            int wx = Math.floorDiv((int) Math.floor(x), CHUNK_TILES), wy = Math.floorDiv((int) Math.floor(y), CHUNK_TILES);
            if (wx == building.wx && wy == building.wy) {
                return;
            }
            terrain.corners(cell, wx, wy);
            if (Math.abs(wx - building.wx) <= 1 && Math.abs(wy - building.wy) <= 1) {
                heightsRound |= 1 << (wx - building.wx + 1) * 3 + wy - building.wy + 1;
                return;
            }
            long far = (long) wx << 32 | wy & 0xFFFFFFFFL;
            for (int known = 0; known < heightsFarCount; known++) {
                if (heightsFar[known] == far) {
                    return;
                }
            }
            if (heightsFarCount == heightsFar.length) {
                heightsFar = Arrays.copyOf(heightsFar, heightsFarCount * 2);
            }
            heightsFar[heightsFarCount++] = far;
        }

        /** The height of the land at a point the build hangs something over: see heightsAt. */
        float landAt(float x, float y) {
            heightsAt(x, y);
            return terrain.land(x, y);
        }

        /** Notes the heights a build was made on, after landOf: its own chunk's and those of the chunks it took heights from. */
        void heightsSeen(ChunkMeshes fresh, int wx, int wy) {
            int count = 1 + Integer.bitCount(heightsRound) + heightsFarCount;
            if (fresh.heightsSeen.length != count) {
                fresh.heightsSeen = new float[count][];
                fresh.heightsSeenOf = new int[count * 2];
            }
            int at = 0;
            fresh.heightsSeenOf[0] = wx;
            fresh.heightsSeenOf[1] = wy;
            fresh.heightsSeen[at++] = terrain.corners(cell, wx, wy);
            for (int bit = 0; bit < 9; bit++) {
                if ((heightsRound >> bit & 1) != 0) {
                    fresh.heightsSeenOf[at * 2] = wx + bit / 3 - 1;
                    fresh.heightsSeenOf[at * 2 + 1] = wy + bit % 3 - 1;
                    fresh.heightsSeen[at++] = terrain.corners(cell, wx + bit / 3 - 1, wy + bit % 3 - 1);
                }
            }
            for (int known = 0; known < heightsFarCount; known++) {
                fresh.heightsSeenOf[at * 2] = (int) (heightsFar[known] >> 32);
                fresh.heightsSeenOf[at * 2 + 1] = (int) heightsFar[known];
                fresh.heightsSeen[at] = terrain.corners(cell, fresh.heightsSeenOf[at * 2], fresh.heightsSeenOf[at * 2 + 1]);
                at++;
            }
            fresh.waterSeen = terrain.water(wx, wy);
            fresh.kerbs = terrain.kerbSum(wx, wy);
        }

        /**
         * Whether every height a build was made on is within within of what it is now, and its own
         * chunk's kerbs run where they did: its triangles would then come out the same but for
         * that much up or down.
         */
        boolean heightsHeld(ChunkMeshes build, float within) {
            int wx = build.heightsSeenOf[0], wy = build.heightsSeenOf[1];
            if (terrain.kerbSum(wx, wy) != build.kerbs || !within(build.waterSeen, terrain.water(wx, wy), within)) {
                return false;
            }
            for (int at = 0; at < build.heightsSeen.length; at++) {
                if (!within(build.heightsSeen[at], terrain.corners(cell, build.heightsSeenOf[at * 2], build.heightsSeenOf[at * 2 + 1]), within)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean within(float[] then, float[] now, float within) {
            if (then == now) {
                return true;
            }
            for (int i = 0; i < then.length; i++) {
                if (Math.abs(then[i] - now[i]) > within) {
                    return false;
                }
            }
            return true;
        }

        /**
         * The heights a build of a chunk goes by, as one number: the version of its own chunk's
         * and of those the build took a height from, round it (round) and further off (the
         * first count of far). A build that left a wire's span unknown for want of map goes by
         * which chunks the map has loaded as well: it is made again when the map has others.
         * After corners.
         */
        private int landOf(int wx, int wy, int round, long[] far, int count, boolean wantsMap) {
            int land = terrain.version(wx, wy);
            for (int bit = 0; bit < 9; bit++) {
                if ((round >> bit & 1) != 0) {
                    terrain.corners(cell, wx + bit / 3 - 1, wy + bit % 3 - 1);
                    land = land * 31 + terrain.version(wx + bit / 3 - 1, wy + bit % 3 - 1);
                }
            }
            for (int known = 0; known < count; known++) {
                int farX = (int) (far[known] >> 32), farY = (int) far[known];
                terrain.corners(cell, farX, farY);
                land = land * 31 + terrain.version(farX, farY);
            }
            return wantsMap ? land * 31 + loadedNow : land;
        }

        final float[] wireEnds = new float[6], wireHangs = new float[2], insulatorAt = new float[2];

        private final float[] lightSpan = new float[Spans.PIECE], lightAt = new float[Lights.NUMBERS];

        /**
         * Whether a string of lights burns: it is switched on and has power. The game's own test
         * for laying the lit sprite over the dark one, IsoLightSwitch.shouldShowOnOverlay, also
         * asks that the viewer has seen the string's square, and a string behind a fence it hangs
         * on has not been seen from the far side of that fence.
         */
        private boolean burns(IsoLightSwitch string) {
            return string.isActivated() && (mainsOn || string.getSquare().haveElectricity());
        }

        /**
         * Hangs the half of a swag of lights a sprite is, and says whether the sprite is thereby
         * dealt with: see Lights. The cord is hung as an overhead wire is, in lengths that lie on
         * the curve of its span, and swings as one; a bulb hangs under it wherever the sprites
         * show one, and swings with the cord it hangs on.
         *
         * The bulbs burn when the string does: see burns. Burning, a bulb is the colour the lit
         * sprite has it and fully lit, which in the dark is how the picture tells a lamp that is on.
         * How finely they are turned, and whether they are there, goes by the chunk's band: see
         * Lights.FINE_WITHIN.
         */
        private boolean addLights(int slice, IsoObject object, IsoSprite sprite, int x, int y, int z) {
            if (slice == Lights.NONE) {
                return false;
            }
            // What the game lays over the sprite, a stain, has no place on the cord.
            if (overLayer > 0) {
                return true;
            }
            building.plain = false;
            Lights.span(slice, x, y, lightSpan);
            int from = Spans.FROM, to = Spans.TO, kind = Lights.kind(slice);
            float held = (z + Lights.HELD) * ViewCamera.LEVEL_HEIGHT, sag = Lights.SAG * ViewCamera.LEVEL_HEIGHT;
            float fromUp = held + landAt(lightSpan[from], lightSpan[from + 1]), toUp = held + landAt(lightSpan[to], lightSpan[to + 1]);
            float swing = Spans.swing(lightSpan, 0);
            for (int length = 0; length < Lights.LENGTHS; length++) {
                for (int end = 0; end < 2; end++) {
                    Lights.cordEnd(lightSpan, length + end, sag, lightAt);
                    wireHangs[end] = lightAt[Lights.HANGS];
                    set(wireEnds, end, lightAt[Lights.X], lightAt[Lights.Y], fromUp + (toUp - fromUp) * lightAt[Lights.ALONG] - wireHangs[end]);
                }
                wireBuilder.cable(wireEnds[0], wireEnds[1], wireEnds[2], wireHangs[0], wireEnds[3], wireEnds[4], wireEnds[5], wireHangs[1], swing);
            }
            boolean burning = object instanceof IsoLightSwitch string && burns(string);
            if (FirstPerson.TRACE) {
                Log.info("lights: " + x + "," + y + "," + z + " " + sprite.getName() + " " + object.getClass().getSimpleName() + (burning ? " burning" : " out"));
            }
            float spanX = lightSpan[to] - lightSpan[from], spanY = lightSpan[to + 1] - lightSpan[from + 1];
            float turned = Spans.acrossSign(-spanY, spanX) / Lights.SPAN, tells = 1.1f + 0.8f * swing;
            float acrossX = -spanY * turned * tells, acrossY = spanX * turned * tells, size = Lights.size(kind);
            float red = burning ? 1f : frame.red, green = burning ? 1f : frame.green, blue = burning ? 1f : frame.blue;
            // Both are asked, whatever the first says: each takes out of alike the bands that would answer it otherwise.
            boolean fine = within(Lights.FINE_WITHIN, false), near = within(Lights.BULBS_WITHIN, false);
            int made = Lights.made(fine, near, burning);
            if (made == Lights.BARE) {
                return true;
            }
            boolean far = made == Lights.FAR;
            int sides = Lights.sides(kind, far);
            SpriteGeometry.Mesh holder = solid.get(PLAIN, WallSides.key(Lights.HOLDER_RED, Lights.HOLDER_GREEN, Lights.HOLDER_BLUE, WallSides.FLAT));
            for (int bulb = 0; bulb < Lights.bulbs(kind); bulb++) {
                if (!Lights.on(slice, bulb)) {
                    continue;
                }
                Lights.bulb(lightSpan, kind, bulb, sag, lightAt);
                float bulbX = lightAt[Lights.X], bulbY = lightAt[Lights.Y], hangs = lightAt[Lights.HANGS], up = fromUp + (toUp - fromUp) * lightAt[Lights.ALONG] - hangs;
                SpriteGeometry.Mesh glass = solid.get(PLAIN, WallSides.key(Lights.colour(kind, bulb, burning, 0), Lights.colour(kind, bulb, burning, 1),
                        Lights.colour(kind, bulb, burning, 2), WallSides.FLAT));
                holder.open = frame.open + 32f * HUNG;
                Lights.round(holder, bulbX, bulbY, up, hangs, acrossX, acrossY, Lights.holder(far), size, sides, frame.red, frame.green, frame.blue);
                // Glass that burns is of the stuff of a lamp, and a lamp's vertex that hangs below a line is what the picture swings.
                glass.open = frame.open + 32f * (burning ? LAMP : HUNG);
                Lights.round(glass, bulbX, bulbY, up, hangs, acrossX, acrossY, Lights.glass(far), size, sides, red, green, blue);
            }
            return true;
        }

        /** Whether a string of lights is made fast at the north-west corner of a square, where its corner post stands. */
        private boolean holdsLights(int x, int y, int z) {
            for (int beside = 0; beside < Lights.HELD_FROM_X.length; beside++) {
                int alongX = Lights.HELD_FROM_X[beside], alongY = Lights.HELD_FROM_Y[beside];
                IsoGridSquare square = cell.getGridSquare(x + alongX, y + alongY, z);
                if (square == null) {
                    continue;
                }
                PZArrayList<IsoObject> objects = square.getObjects();
                for (int i = 0; i < objects.size(); i++) {
                    IsoSprite sprite = objects.get(i).getSprite();
                    if (sprite != null) {
                        int slice = Lights.slice(sprite);
                        if (slice != Lights.NONE && Lights.fastAt(slice, alongX, alongY)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private void addSquare(IsoGridSquare square, int x, int y, int z, int localX, int localY) {
            floorLayer = westLayer = northLayer = eastLayer = southLayer = 0;
            items.clear();
            waterLocalX = localX;
            waterLocalY = localY;

            here = square;
            building.plain &= square.getRoomID() == -1L;
            IsoObject floor = square.getFloor();
            hangsInAir = floor == null;
            geometry.floored = !hangsInAir;
            wallNorth = wallWest = edgeNorth = edgeWest = canvas = false;
            boolean openAbove = false;
            PZArrayList<IsoObject> objects = square.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                IsoSprite sprite = objects.get(i).getSprite();
                if (sprite != null) {
                    PropertyContainer flags = sprite.getProperties();
                    IsoObjectType type = sprite.getType();
                    openAbove |= Roofs.isTile(type) || isStair(type);
                    canvas |= Tents.isCanvas(sprite);
                    edgeNorth |= onNorthEdge(flags);
                    edgeWest |= onWestEdge(flags);
                    wallNorth |= flags.has(IsoFlagType.WallN) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.DoorWallN) || flags.has(IsoFlagType.WindowN);
                    wallWest |= flags.has(IsoFlagType.WallW) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.DoorWallW) || flags.has(IsoFlagType.WindowW);
                }
            }
            // A room the game has not shown the player yet is black in the game's own view. Where
            // the viewer is shown no more than the game shows, the house stands with the room
            // left empty: see Sightline. From the eye, a room is built while a walk round the eye
            // finds it can be seen soon, and stands empty otherwise, near or far: see RoomSight.
            // What hangs on its walls may be seen from outside it, and is built whatever the walk found.
            long room = square.getRoomID();
            squareRoom = room;
            int emptied = Sightline.emptied(asTheGameShows, gamesView, room != RoomSight.OUTSIDE, square.isSeen(playerIndex));
            boolean unexplored = emptied == Sightline.LEFT_OUT;
            // The band is asked only where its answer changes the build: asked of every square, a chunk would be another build in every band.
            boolean empty = unexplored || !buildingBySight && emptied == Sightline.LEFT_OUT_FAR && !within(UNSHOWN_FILLED_WITHIN, false);
            boolean unsought = buildingBySight && room != RoomSight.OUTSIDE && !builtBySight(room);
            // A room's floor, its stairs and its ceiling are drawn only while the eye sees into
            // it, as what stands in it is. Not a floor over open air: its underside is seen from
            // out of doors (FloorBuilder.addCeiling).
            IsoGridSquare under = z > lowestLevel ? cell.getGridSquare(x, y, z - 1) : null;
            boolean shellSeenInside = room != RoomSight.OUTSIDE && (z == lowestLevel || under != null && under.getRoomID() != -1L);
            stoodOn = 0;
            plantsHere = cropsGrownHere = 0;
            for (int i = 0; i < objects.size(); i++) {
                IsoObject object = objects.get(i);
                IsoSprite sprite = object.getSprite();
                if (sprite == null) {
                    continue;
                }
                Kind kind = kindOf(sprite);
                boolean inItsRoom = object != floor && withinItsRoom(object, kind.ofTheBuilding, kind.keptInItsRoom);
                if (empty && object != floor && !partOfTheBuilding(object, kind.ofTheBuilding) || unsought && inItsRoom) {
                    continue;
                }
                contentsOf(inItsRoom || shellSeenInside && (object == floor || kind.seenInItsRoom) ? room : hungFrom(object, sprite, kind, room, x, y, z));
                Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
                if (!usable(texture)) {
                    continue;
                }
                if (SightTrace.ON && building.traced != null) {
                    building.traced.add(object);
                }
                // Laid once the square's floor and what spreads over it are: see layItems.
                if (object instanceof IsoWorldInventoryObject item) {
                    items.add(item);
                    continue;
                }
                owning(Outlines.key(object));
                frameOf(object, texture);
                standOn(object);
                frame.material = object == floor ? kind.asFloor : kind.stained && Own.buildings() ? STAINED : kind.asOther;
                laidWall = kind.laidWall;
                garageDoor = kind.garageDoor;
                gableSheet = kind.gableSheet;
                if (frame.material == PLANKS && isMesh(sprite, texture)) {
                    frame.material = MESH;
                }
                sprites++;
                overLayer = 0;
                geometry.nudgeX = geometry.nudgeY = 0f;
                boolean standsOn = kind.onTop || geometry.raised != 0f;
                standsNearer = standingNearer(standsOn, standsOn ? stoodOn++ : 0);
                geometry.nearer = standsNearer;
                ArrayList<IsoSpriteInstance> leaves = object.getAttachedAnimSprite();
                // A tree in leaf shows its bole and not the branches in its crown: they are
                // inside the leaves. Drawn, they cross from one card of the fan through the leaves
                // of the next. A bare tree is all branch and keeps them.
                boolean inLeaf = sprite.getType() == IsoObjectType.tree && LeafTrees.inLeaf(object);
                // The game's leaves stay on a tree until a day of its own; the mod's may have fallen sooner.
                boolean bare = inLeaf && plantBuilder.bareBySeason(sprite.getName(), x, y);
                geometry.keep = inLeaf && !bare ? BOLE : 1f;
                if (unexplored && object == floor) {
                    frame.light(0f, 0f, 0f);
                }
                int before = FirstPerson.TRACE ? worn(texture) : 0;
                int firesBefore = placed.fireCount;
                addSprite(solid.get(texture.getID()), object, sprite, texture, object == floor, x, y, z);
                if (FirstPerson.TRACE) {
                    tally(sprite, worn(texture) - before);
                }
                boolean lit = object instanceof IsoFireplace hearth && hearth.isLit() || object instanceof IsoBarbecue barbecue && barbecue.isLit();
                if (lit && placed.fireCount == firesBefore) {
                    furnishingBuilder.addFireOverSprite(object, sprite, x, y, z);
                }
                if (unexplored && object == floor) {
                    cornerLights(localX, localY);
                }
                geometry.keep = 1f;
                // What the game draws over an object with the object: the leaves on a tree, the
                // ivy and the stains on a wall. Each lies or stands as the object under it does.
                if (leaves != null && !bare) {
                    for (int k = 0; k < leaves.size(); k++) {
                        addOver(object, sprite, leaves.get(k).parentSprite, object == floor, x, y, z);
                    }
                }
                if (!bare) {
                    addOver(object, sprite, object.getOverlaySprite(), object == floor, x, y, z);
                }
                geometry.nudgeX = geometry.nudgeY = geometry.nearer = 0f;
                if (object.wallBloodSplats != null && !object.wallBloodSplats.isEmpty()) {
                    PropertyContainer flags = sprite.getProperties();
                    bloodBuilder.layOnWall(object, onNorthEdge(flags), onWestEdge(flags), x, y, z);
                }
                owning(Outlines.NONE);
            }
            contentsOf(RoomSight.OUTSIDE);
            geometry.raised = 0f;
            frame.material = 0f;
            if (room != RoomSight.OUTSIDE) {
                contentsOf(room);
                ceilingBuilder.addRoomCeiling(x, y, z, localX, localY, openAbove);
                contentsOf(RoomSight.OUTSIDE);
            } else if (hangsInAir) {
                IsoGridSquare hall = TallHalls.under(cell, x, y, z);
                if (hall != null) {
                    contentsOf(hall.getRoomID());
                    ceilingBuilder.addRoomCeiling(x, y, z, localX, localY, openAbove);
                    contentsOf(RoomSight.OUTSIDE);
                }
            }
            if (z == 0) {
                if (geometry.kerb != null && beyondKerb >= 0) {
                    kerbBuilder.addBeyondKerb(x, y);
                }
                if (kerbed) {
                    kerbBuilder.addKerb(x, y, localX, localY);
                }
                addEdges(x, y, localX, localY);
                floorBuilder.spreadGround(x, y, localX, localY);
                contentsOf(room);
                // Blood is spilt on a floor, and runs over onto the next square's: not onto water.
                boolean wet = groundKind[(localX + 1) * LIGHT_SPAN + localY + 1] == Terrain.WATER;
                int blood = empty || unsought || hangsInAir || wet ? 0 : bloodBuilder.layFloor(x, y, z, localX, localY, floorLayer + spreadLayers);
                int laidItems = layItems(z, floorLayer + spreadLayers + blood);
                contentsOf(RoomSight.OUTSIDE);
                // What grows here is a build of its own: see grow. It lies over these layers.
                building.groundLayers[localX * CHUNK_TILES + localY] = (byte) (floorLayer + spreadLayers + blood + laidItems);
                int own = (localX + 1) * LIGHT_SPAN + localY + 1;
                building.grows |= lightOpen[own] == 1 && (groundKind[own] == Terrain.NATURAL || treesRound && Litter.keeps(groundKind[own], true) > 0f);
            }

            contentsOf(room);
            if (z != 0) {
                int blood = empty || unsought || hangsInAir ? 0 : bloodBuilder.layFloor(x, y, z, localX, localY, floorLayer);
                layItems(z, floorLayer + blood);
            }

            ArrayList<IsoMovingObject> bodies = square.getStaticMovingObjects();
            for (int i = 0; i < bodies.size() && !empty && !unsought; i++) {
                if (bodies.get(i) instanceof IsoDeadBody body) {
                    owning(Outlines.key(body));
                    addBody(body);
                    owning(Outlines.NONE);
                }
            }
            if (!empty && !unsought) {
                for (LimbDecals.Laid laid : LimbDecals.at(x, y, z)) {
                    addLimb(laid);
                }
            }
            squareRoom = RoomSight.OUTSIDE;
            contentsOf(RoomSight.OUTSIDE);
        }

        /** Has what is built next be what the thing with this key draws, for its outline, or no thing's for Outlines.NONE: see Outlines. */
        private void owning(long key) {
            solid.own(key);
            laid.own(key);
            placed.key = key;
        }

        /**
         * Where what hangs on one edge of its square is seen from: the room or the open air on
         * either side of that edge (RoomSight.pair). RoomSight.OUTSIDE, drawn whenever its chunk
         * is, for anything else that is no room's contents.
         */
        private long hungFrom(IsoObject object, IsoSprite sprite, Kind kind, long room, int x, int y, int z) {
            if (kind.keptInItsRoom || partOfTheBuilding(object, kind.ofTheBuilding) || object instanceof IsoCurtain || object instanceof IsoBarricade) {
                return RoomSight.OUTSIDE;
            }
            PropertyContainer flags = sprite.getProperties();
            int acrossX = flags.has(IsoFlagType.attachedE) ? 1 : flags.has(IsoFlagType.attachedW) ? -1 : 0;
            int acrossY = flags.has(IsoFlagType.attachedS) ? 1 : flags.has(IsoFlagType.attachedN) ? -1 : 0;
            if ((acrossX != 0) == (acrossY != 0)) {
                return RoomSight.OUTSIDE;
            }
            IsoGridSquare beyond = cell.getGridSquare(x + acrossX, y + acrossY, z);
            return RoomSight.pair(room, beyond == null ? RoomSight.OUTSIDE : beyond.getRoomID());
        }

        /**
         * Has what is built next be the contents of a room, seen from the places RoomSight.pair
         * gives, or with RoomSight.OUTSIDE in the open air. What is no room's on a room's square
         * is drawn whenever its chunk is: its place is not known well enough to leave it out.
         */
        void contentsOf(long room) {
            if (room == RoomSight.OUTSIDE && squareRoom != RoomSight.OUTSIDE) {
                room = RoomSight.ANYWHERE;
            }
            solid.room = room;
            laid.room = room;
            placed.room = room;
        }

        /** The loose items of the square being added, as addSquare finds them. */
        private final ArrayList<IsoWorldInventoryObject> items = new ArrayList<>();
        private final float[] itemCorners = new float[4 * GroundItems.CORNER_FLOATS];

        /**
         * Lays the loose items of the square being added, the first a layer over the given
         * number lying under them, and says how many layers they take: see GroundItems. An item
         * the game does not draw in its own view is not laid.
         */
        private int layItems(int z, int under) {
            int laid = 0;
            boolean models = ItemModels.shown() && !items.isEmpty() && within(ItemModels.REACH, false);
            // Read as it is and not through within: loading ahead changes nothing the build makes.
            boolean ahead = !models && ItemModels.shown() && bandAway < ItemModels.AHEAD;
            for (int i = 0; i < items.size(); i++) {
                IsoWorldInventoryObject item = items.get(i);
                if (!item.getItem().getScriptItem().isWorldRender()) {
                    continue;
                }
                if (ahead) {
                    ItemModels.ahead(item);
                }
                if (models && ItemModels.place(item, itemPlaced)) {
                    buildItem(item, z);
                    continue;
                }
                IsoSprite sprite = item.getSprite();
                Texture texture = sprite.getTextureForCurrentFrame(item.getDir());
                GroundItems.corners(texture.getWidthOrig(), texture.getHeightOrig(), texture.getOffsetX(), texture.getOffsetY(),
                        texture.getWidth(), texture.getHeight(), sprite.def.getScaleX(), sprite.def.getScaleY(), frame.scale,
                        item.getItem().getWorldZRotation(), texture.getXStart(), texture.getYStart(), texture.getXEnd(), texture.getYEnd(), itemCorners);
                ColorInfo tint = sprite.getTintMod();
                float itemX = item.getWorldPosX(), itemY = item.getWorldPosY(), up = item.getWorldPosZ() * ViewCamera.LEVEL_HEIGHT;
                owning(Outlines.key(item));
                SpriteGeometry.Mesh mesh = solid.get(texture.getID(), WallSides.multiplied(tint.r, tint.g, tint.b));
                mesh.open = frame.open;
                mesh.face(0f, 0f, 1f);
                layers.lie(SpriteGeometry.LAYERS_UP, under + 1 + laid % GroundItems.LAYERS);
                for (int corner : GroundItems.TRIANGLES) {
                    int at = corner * GroundItems.CORNER_FLOATS;
                    float atX = itemX + itemCorners[at], atY = itemY + itemCorners[at + 1];
                    heightsAt(atX, atY);
                    mesh.add(atX, atY, up + terrain.lift(atX, atY, z), itemCorners[at + 2], itemCorners[at + 3],
                            frame.red, frame.green, frame.blue);
                }
                layers.none();
                owning(Outlines.NONE);
                laid++;
            }
            return Math.min(laid, GroundItems.LAYERS);
        }

        private final ItemModels.Placed itemPlaced = new ItemModels.Placed();

        /**
         * Builds a loose item as the game's own model of it, placed as ItemModels found it, its
         * every corner lit by the light of the square as the item's picture would be.
         */
        private void buildItem(IsoWorldInventoryObject item, int z) {
            ItemModels.Placed placed = itemPlaced;
            ItemModels.Shape shape = placed.shape;
            float[] m = placed.placing, places = shape.places(), normals = shape.normals(), uvs = shape.uvs();
            Texture texture = placed.texture;
            float uStart = texture.getXStart(), vStart = texture.getYStart(), uSpan = texture.getXEnd() - uStart, vSpan = texture.getYEnd() - vStart;
            heightsAt(placed.x, placed.y);
            float up = placed.up + terrain.lift(placed.x, placed.y, z);
            owning(Outlines.key(item));
            SpriteGeometry.Mesh mesh = solid.get(texture.getID(), WallSides.multiplied(placed.red, placed.green, placed.blue));
            mesh.open = frame.open;
            for (int corner : shape.corners()) {
                int p = corner * 3;
                float px = places[p], py = places[p + 1], pz = places[p + 2];
                float nx = normals[p], ny = normals[p + 1], nz = normals[p + 2];
                mesh.face(m[0] * nx + m[1] * ny + m[2] * nz, m[4] * nx + m[5] * ny + m[6] * nz, m[8] * nx + m[9] * ny + m[10] * nz);
                mesh.add(placed.x + m[0] * px + m[1] * py + m[2] * pz + m[3], placed.y + m[4] * px + m[5] * py + m[6] * pz + m[7],
                        up + m[8] * px + m[9] * py + m[10] * pz + m[11], uStart + uvs[corner * 2] * uSpan, vStart + uvs[corner * 2 + 1] * vSpan,
                        frame.red, frame.green, frame.blue);
            }
            owning(Outlines.NONE);
        }

        /**
         * A body lying on the ground. The game has no model standing there to draw: it keeps a
         * picture of the body as its own view sees it, made once, and draws that where the body
         * lies. The same picture is laid on the ground here, every texel of it where the game's
         * view has it, so it is the body the game shows, lying as the game shows it. The picture
         * is asked for when the chunk is read (askPicture), and until the game has made it there
         * is none to lay.
         */
        private void addBody(IsoDeadBody body) {
            DeadBodyAtlas.BodyTexture picture = body.getAtlasTexture();
            Texture texture = picture == null ? null : picture.getTexture();
            if (!usable(texture) || !texture.isReady()) {
                return;
            }
            if (SightTrace.ON && building.traced != null) {
                building.traced.add(body);
            }
            SpriteGeometry.Mesh mesh = solid.get(texture.getID());
            mesh.open = frame.open + 32f * BODY;
            mesh.face(0f, 0f, 1f);
            float x = body.getX(), y = body.getY(), z = body.getZ();
            // Where the game draws the picture, in its own pixels from where it draws the body's place on the floor.
            float left = picture.getRenderX(0f), top = picture.getRenderY(0f), wide = picture.getRenderWidth(), high = picture.getRenderHeight();
            float across = SpriteGeometry.PIXELS_ACROSS_PER_TILE * frame.scale, down = SpriteGeometry.PIXELS_DOWN_PER_TILE * frame.scale;
            // What is above the floor is drawn further up the screen, so the picture lies this much nearer the game's view than it would on the floor.
            float nearer = BODY_UP / ViewCamera.LEVEL_HEIGHT * SpriteGeometry.PIXELS_PER_LEVEL / (2f * SpriteGeometry.PIXELS_DOWN_PER_TILE);
            // Laid as a grid on the ground, each point at the highest of the ground round it, so it
            // lies over a slope and is not cut into by the ground between its corners, nor by a kerb.
            float base = z * ViewCamera.LEVEL_HEIGHT + BODY_UP;
            for (int i = 0; i <= BODY_CELLS; i++) {
                for (int j = 0; j <= BODY_CELLS; j++) {
                    float u = (left + wide * i / BODY_CELLS) / across, v = (top + high * j / BODY_CELLS) / down;
                    float atX = x + (v + u) / 2f + nearer, atY = y + (v - u) / 2f + nearer;
                    int at = (i * (BODY_CELLS + 1) + j) * 3;
                    bodyGrid[at] = atX;
                    bodyGrid[at + 1] = atY;
                    bodyGrid[at + 2] = base + groundUnder(atX, atY, z, Math.max(wide / across, high / down) / (2f * BODY_CELLS));
                }
            }
            float u0 = texture.getXStart(), u1 = texture.getXEnd(), v0 = texture.getYStart(), v1 = texture.getYEnd();
            layers.lie(SpriteGeometry.LAYERS_UP, 1 + bodiesLaid++ % BODY_LAYERS);
            for (int i = 0; i < BODY_CELLS; i++) {
                for (int j = 0; j < BODY_CELLS; j++) {
                    for (int corner : BODY_CORNERS) {
                        int ci = i + (corner == 1 || corner == 2 ? 1 : 0), cj = j + (corner >= 2 ? 1 : 0);
                        int at = (ci * (BODY_CELLS + 1) + cj) * 3;
                        mesh.add(bodyGrid[at], bodyGrid[at + 1], bodyGrid[at + 2], u0 + (u1 - u0) * ci / BODY_CELLS, v0 + (v1 - v0) * cj / BODY_CELLS,
                                frame.red, frame.green, frame.blue);
                    }
                }
            }
            layers.none();
        }

        /** How many bodies and pieces have been laid in the chunk being added: see BODY_LAYERS. */
        private int bodiesLaid;
        private final float[] bodyGrid = new float[(BODY_CELLS + 1) * (BODY_CELLS + 1) * 3];

        /**
         * A piece cut off a zombie, lying on the ground: a picture of it from straight above,
         * laid as a body's is, over the ground round it. See LimbDecals.
         */
        private void addLimb(LimbDecals.Laid laid) {
            SpriteGeometry.Mesh mesh = solid.get(LimbDecals.texture());
            mesh.open = frame.open + 32f * BODY;
            mesh.face(0f, 0f, 1f);
            float base = laid.z() * ViewCamera.LEVEL_HEIGHT + BODY_UP, side = 2f * laid.half();
            float share = (float) LimbDecals.SLOT / LimbDecals.SIDE, u0 = laid.u0(), v0 = laid.v0();
            for (int i = 0; i <= BODY_CELLS; i++) {
                for (int j = 0; j <= BODY_CELLS; j++) {
                    float atX = laid.x() - laid.half() + side * i / BODY_CELLS, atY = laid.y() - laid.half() + side * j / BODY_CELLS;
                    int at = (i * (BODY_CELLS + 1) + j) * 3;
                    bodyGrid[at] = atX;
                    bodyGrid[at + 1] = atY;
                    bodyGrid[at + 2] = base + groundUnder(atX, atY, laid.z(), laid.half() / BODY_CELLS);
                }
            }
            layers.lie(SpriteGeometry.LAYERS_UP, 1 + bodiesLaid++ % BODY_LAYERS);
            for (int i = 0; i < BODY_CELLS; i++) {
                for (int j = 0; j < BODY_CELLS; j++) {
                    for (int corner : BODY_CORNERS) {
                        int ci = i + (corner == 1 || corner == 2 ? 1 : 0), cj = j + (corner >= 2 ? 1 : 0);
                        int at = (ci * (BODY_CELLS + 1) + cj) * 3;
                        // The picture's foot is the south side of the square it shows.
                        mesh.add(bodyGrid[at], bodyGrid[at + 1], bodyGrid[at + 2], u0 + share * ci / BODY_CELLS, v0 + share * (1f - (float) cj / BODY_CELLS),
                                frame.red, frame.green, frame.blue);
                    }
                }
            }
            layers.none();
        }

        /** The highest the ground is at (x, y) and reach along the map either way from it. */
        private float groundUnder(float x, float y, float z, float reach) {
            float highest = Float.NEGATIVE_INFINITY;
            for (int k = 0; k < 5; k++) {
                float atX = x + (k == 1 ? reach : k == 2 ? -reach : 0f), atY = y + (k == 3 ? reach : k == 4 ? -reach : 0f);
                heightsAt(atX, atY);
                highest = Math.max(highest, terrain.lift(atX, atY, z));
            }
            return highest;
        }

        /**
         * Whether a square is in a room the game has never had in the player's sight, in a frame
         * that leaves out what is in such a room: see Sightline.leftOut. As the game's own view
         * the frame leaves nothing out of it: the room has the engine's light, which is what hides it.
         */
        private boolean unexplored(IsoGridSquare square) {
            return Sightline.leftOut(asTheGameShows, gamesView, square.getRoomID() != -1L, square.isSeen(playerIndex));
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
            float base = view.kerbSteps.up(terrain, character, x, y, character.getZ());
            if (characters == characterX.length) {
                characterX = Arrays.copyOf(characterX, characters * 2);
                characterY = Arrays.copyOf(characterY, characters * 2);
                characterBase = Arrays.copyOf(characterBase, characters * 2);
                characterOf = Arrays.copyOf(characterOf, characters * 2);
            }
            characterOf[characters] = character;
            characterX[characters] = x;
            characterY[characters] = y;
            characterBase[characters] = base;
            characters++;
        }

        /**
         * Adds the box boxOf read off the sprite, cut back to any wall on an edge of its square.
         * The outline is of the sprite, and a cabinet against a wall is painted reaching a little
         * past the wall's line: stood where its outline says, it shows through the wall outside.
         */
        private void addBoxWithinWalls(SpriteGeometry.Mesh mesh, int x, int y, int z) {
            float west = box[0] - box[2], east = box[0] + box[2], north = box[1] - box[2], south = box[1] + box[2];
            if (Hoods.walled(here, false)) {
                west = Math.max(west, 0f);
            }
            if (Hoods.walled(cell.getGridSquare(x + 1, y, z), false)) {
                east = Math.min(east, 1f);
            }
            if (Hoods.walled(here, true)) {
                north = Math.max(north, 0f);
            }
            if (Hoods.walled(cell.getGridSquare(x, y + 1, z), true)) {
                south = Math.min(south, 1f);
            }
            box[0] = (west + east) / 2f;
            box[1] = (north + south) / 2f;
            box[2] = (east - west) / 2f;
            geometry.boxHalfB = (south - north) / 2f;
            geometry.addBox(mesh, frame, x, y, z, box);
            geometry.boxHalfB = 0f;
        }

        private boolean floatsFree(int x, int y, int z) {
            IsoGridSquare square = cell.getGridSquare(x, y, z), under = cell.getGridSquare(x, y, z - 1);
            PZArrayList<IsoObject> objects = square.getObjects();
            for (int n = 0; n < objects.size(); n++) {
                IsoSprite each = objects.get(n).getSprite();
                if (each == null || !Roofs.accent(each.getName())) {
                    return false;
                }
            }
            if (under != null && !under.getObjects().isEmpty()) {
                return false;
            }
            // A trim beside a roof edges it, wherever the map's view puts it.
            for (int level = z - 1; level <= z; level++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        IsoGridSquare beside = cell.getGridSquare(x + dx, y + dy, level);
                        if (beside != null && Roofs.covered(cell, beside)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        /**
         * Sprites that share a plane of the square are stacked on it in the order the square holds
         * them, each a layer further into the square than the one before: a rug above its floor, a
         * window and then its curtain inside their wall. Seen from the other side of the plane the
         * later ones are then behind the first, which is where they are.
         */
        private void addSprite(SpriteGeometry.Mesh mesh, IsoObject object, IsoSprite sprite, Texture texture, boolean isFloor,
                int x, int y, int z) {
            PropertyContainer properties = sprite.getProperties();
            IsoObjectType type = sprite.getType();
            Kind kind = kindOf(sprite);

            // The game marks whatever is named damaged or trash_ as fixed to the floor, a torn fence and a notice on a wall
            // among them: see FloorMarks. It marks some things that stand so too, a lawn flamingo and a small mirror on a
            // wall among them, and what the mod has a model of stands as that.
            boolean modelled = !isFloor && properties.has(IsoFlagType.attachedFloor) && Own.furniture() && Furniture.known(sprite) != null;
            if (isFloor || properties.has(IsoFlagType.solidfloor) || properties.has(IsoFlagType.FloorOverlay)
                    || !modelled && FloorMarks.onFloor(sprite.getName(), properties)) {
                // The map closes a tent at the level its canopy is on with a ceiling laid as a
                // floor. Inside the tent the canvas is the ceiling: the flat one is left out.
                if (canvas && z > 0) {
                    return;
                }
                if (z > 0 && kind.roofSheet) {
                    if (roofBuilder.underAMadeSlope(x, y, z)) {
                        return;
                    }
                    // A roof's crown the map lays as a floor a level up closes a loft, whose west and north ends it leaves open.
                    roofBuilder.addCrownGable(x, y, z);
                }
                floorBuilder.addFloor(mesh, kind, properties, texture, isFloor, x, y, z);
                return;
            }

            // What the game draws with the floor though it is no floor: railway track, and the
            // litter it calls a rug. Each is painted lying on the ground, and stood up to face the
            // eye a sheet of newspaper is a hoarding and a length of track a fence. It reaches
            // past its square only where the next square has nothing lying on it: a rug of many
            // squares meets itself edge to edge, where each piece reaching into the next was two
            // pieces over one another along every seam. A square of rug is its picture drawn
            // again, which reaches as far past its square as it was read.
            if (!hangsInAir && kind.lies) {
                int layer = Flat.layer(floorLayer, x, y);
                layers.lie(SpriteGeometry.LAYERS_UP, layer);
                floorLayer = layer + 1;
                float pile = kind.pile;
                int rug = Own.textures(view.index) ? rugOf(texture, kind.rug, pile, x, y, z) : 0;
                float most = rug != 0 ? Rugs.REACH : Flat.REACH;
                float west = Math.min(most, reachInto(x, y, x - 1, y, z)), north = Math.min(most, reachInto(x, y, x, y - 1, z));
                float east = Math.min(most, reachInto(x, y, x + 1, y, z)), south = Math.min(most, reachInto(x, y, x, y + 1, z));
                int flat = frame.flat;
                float material = frame.material;
                if (rug != 0) {
                    frame.flat = SpriteGeometry.FLAT_RUG;
                    frame.material = Rugs.material(kind.rug, material);
                }
                geometry.addFace(laid.get(rug != 0 ? rug : texture.getID()), frame, x, y, z, -west, -north, pile, 1f + west + east, 0f, 0f, 0f,
                        1f + north + south, 0f);
                frame.flat = flat;
                frame.material = material;
                layers.none();
                return;
            }

            if (kind.wire && Poles.length(sprite.getName()) && wireBuilder.addPole(mesh, object, sprite, texture, x, y, z)) {
                return;
            }

            // A big pylon's wire across its square, as high over the land at each end as the wire is over its level.
            float[] span = Own.furniture() ? PylonWires.of(sprite.getName()) : null;
            if (span != null) {
                float x0 = x + span[0], y0 = y + span[1], x1 = x + span[2], y1 = y + span[3], up = (z + span[4]) * ViewCamera.LEVEL_HEIGHT;
                wireBuilder.cable(x0, y0, landAt(x0, y0) + up, 0f, x1, y1, landAt(x1, y1) + up, 0f, 0f);
                float along = PylonWires.ball(sprite.getName());
                if (!Float.isNaN(along)) {
                    // The ball's model is built along its X, round the wire through its middle.
                    float ballX = x0 + (x1 - x0) * along, ballY = y0 + (y1 - y0) * along;
                    building.plain = false;
                    placed.add(Models.get("marker_ball"), frame, x, y, ballX, ballY, landAt(ballX, ballY) + up - MARKER_BALL_HALF,
                            Math.abs(x1 - x0) > Math.abs(y1 - y0) ? 0 : 1, Furniture.TILES_PER_METRE, Furniture.TILES_PER_METRE, 0, 0, 0f, 0f, 0, 0f, 0f);
                    placed.built();
                    furnished++;
                }
                return;
            }

            // Overhead wires. The game hangs them as sprites on squares of air two floors up, each
            // the lengths of wire that cross its square, painted as its view sees them.
            if (z > 0 && hangsInAir && kind.wire && wireBuilder.addWires(texture, sprite.getName(), x, y, z)) {
                return;
            }

            // A washing line. The game paints a square's length of it as it paints an overhead wire,
            // and stood up as a card a run of them was a row of dashes that turned with the eye.
            // Each length is strung from over the middle of its square to over the middle of the
            // next, as high over the land at each end as the line is, so a run goes with the land.
            if (z == 0 && !hangsInAir && overLayer == 0 && kind.line != Outdoors.NO_LINE && Own.furniture()) {
                float fromX = x + 0.5f, fromY = y + 0.5f;
                float toX = fromX + (kind.line == Outdoors.LINE_EAST ? 1f : 0f), toY = fromY + (kind.line == Outdoors.LINE_SOUTH ? 1f : 0f);
                float from = landAt(fromX, fromY), to = landAt(toX, toY);
                for (float up : Outdoors.LINES_UP) {
                    wireBuilder.cable(fromX, fromY, from + up, 0f, toX, toY, to + up, 0f, 0f);
                }
                return;
            }
            if ((kind.banner == Banners.STRUNG || kind.banner == Banners.POSTED) && Own.furniture()) {
                addBanner(mesh, kind.banner == Banners.STRUNG, x, y, z);
                return;
            }

            if (stairBuilder.addStair(mesh, texture, type, x, y, z)) {
                return;
            }
            if (overLayer == 0 && Own.buildings() && (kind.rail != null && addRail(kind.rail, x, y, z)
                    || kind.flightPost && underAFlightsHead(x, y, z))) {
                return;
            }

            if (Own.furniture() && addParasol(kind.parasol, object, sprite, properties, x, y, z)) {
                return;
            }

            if (Own.furniture() && (addAwning(kind.awning, texture, x, y, z) || addHood(kind, texture, x, y, z))) {
                return;
            }

            if (Own.furniture() && addTower(kind.tower, sprite, x, y, z)) {
                return;
            }

            if (addTent(mesh, kind.tent, kind.rope, x, y, z)) {
                return;
            }

            if (Own.furniture() && addLights(kind.lights, object, sprite, x, y, z)) {
                return;
            }

            // A fence's corner post that a string of lights is made fast to. Stood as a wall's
            // corner piece it is its picture on both edges of its square, two boards crossed,
            // each twice as wide as the post and cut to the point the game's view draws its top as.
            if (Own.furniture() && overLayer == 0 && properties.has(IsoFlagType.WallSE) && sprite.getName() != null && sprite.getName().startsWith("fencing_")
                    && holdsLights(x, y, z) && postBuilder.addPost(mesh, object, sprite, texture, x, y, z)) {
                return;
            }

            if (kind.roofAccent && (z > 0 && roofBuilder.underARake(x, y, z) || roofBuilder.trimUnderAMadeEave(x, y, z, properties.has(IsoFlagType.attachedW),
                    properties.has(IsoFlagType.attachedN), !properties.has(IsoFlagType.attachedW) && !properties.has(IsoFlagType.attachedN)
                    && !properties.has(IsoFlagType.attachedE) && !properties.has(IsoFlagType.attachedS) && !properties.has(IsoFlagType.attachedNW)
                    && !properties.has(IsoFlagType.attachedSE)))) {
                return;
            }
            // The eave the map hangs on the wall under a slope's foot, the trims painted on it and
            // the corner piece past each end of it: the slope's lowest tile makes that eave.
            if ((kind.roofAccent || kind.eave != Hoods.NONE) && roofBuilder.eaveRow(x, y, z) || kind.endPiece && roofBuilder.eaveRowEnd(x, y, z)) {
                return;
            }
            // A trim with nothing on its square but trims, nothing under it and no roof beside it hangs
            // in the air: the map puts flashing on a square levels over the roof it edges, where the
            // game's view draws it in place (the porch roof's top edge at 10752..10760, 9619, laid out
            // on 9622 two levels up).
            if (kind.roofAccent && z > 0 && floatsFree(x, y, z)) {
                return;
            }
            // Where no slope makes it, the row is an eave of its own, and the corner past it goes with it.
            if (kind.eave != Hoods.NONE && roofBuilder.addEaveRow(texture, x, y, z) || kind.endPiece && roofBuilder.eaveRowOfItsOwnEnd(x, y, z)) {
                return;
            }

            if (overLayer == 0 && furnishingBuilder.addHung(object, sprite, texture, x, y, z) || furnishingBuilder.addFurniture(object, sprite, x, y, z)) {
                if (SightTrace.ON && building.modelled != null) {
                    building.modelled.add(object);
                }
                return;
            }
            // A strip light on a ceiling, a square of it at a time. The game paints it lying
            // under the ceiling, and stood up to face the eye it hung in the room as a slat.
            if (overLayer == 0 && kind.stripLamp) {
                SpriteGeometry.Mesh plain = solid.get(PLAIN);
                float lift = (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f;
                layers.lie(SpriteGeometry.LAYERS_UP, -2f);
                Soffits.strip(plain, x, y, (z + 1) * ViewCamera.LEVEL_HEIGHT + lift, sprite.getSpriteGrid().getWidth() > 1,
                        frame.red, frame.green, frame.blue, frame.open, frame.open + 32f * LAMP);
                layers.none();
                return;
            }

            if (overLayer == 0 && (type == IsoObjectType.curtainN || type == IsoObjectType.curtainS || type == IsoObjectType.curtainW || type == IsoObjectType.curtainE)
                    && Own.buildings() && openingBuilder.addCurtain(texture, type, x, y, z)) {
                return;
            }
            boolean west;
            boolean north;
            doorSwung = false;
            if (object instanceof IsoDoor door) {
                // A door on hinges stands across its doorway when it is open. A garage door
                // rolls up in its own plane, and what is left of it showing is still in the wall.
                doorSwung = door.IsOpen() && !kind.garageDoor;
                north = door.north != doorSwung;
                west = !north;
            } else if (object instanceof IsoThumpable thumpable && thumpable.isDoor()) {
                doorSwung = thumpable.IsOpen() && !kind.garageDoor;
                north = thumpable.north != doorSwung;
                west = !north;
            } else if (object instanceof IsoWindow window) {
                // A window knows which wall it is in. Its sprites do not always: the game marks the
                // open sash of a window in a north wall as fixed to the west one.
                north = window.getNorth();
                west = !north;
            } else {
                // The corner length of a skirting board or a cornice is marked for both walls of
                // its corner by a name of its own, which says nothing to a test for either wall:
                // it stood in every corner of every room as a card turned to the eye.
                boolean both = properties.has(IsoFlagType.WallNW) || properties.has(IsoFlagType.WallSE) || kind.cornerTrim;
                // Bunting and a flag some squares long are marked for no wall. Which edge each is on is in its picture.
                // With the mod's furniture off it is a card turned to the eye, as a strung banner is the game's sprite.
                boolean cloth = kind.banner == Banners.ON_WALL && Own.furniture();
                west = both || Edges.west(properties, type) || cloth && !Banners.alongX(frame);
                north = both || Edges.north(properties, type) || cloth && Banners.alongX(frame);
                if (kind.edge != Overhead.ANY_EDGE) {
                    west = kind.edge != Overhead.NORTH_EDGE;
                    north = kind.edge != Overhead.WEST_EDGE;
                }
                if (kind.brokenFence && !west && !north) {
                    west = BrokenFences.onWest(frame);
                    north = BrokenFences.onNorth(frame);
                }
            }
            // Trim the map paints round a window or along a wall, a window's head or its sill, or
            // the arch between two pillars of a belfry, and marks for no wall. Stood up to face the
            // eye each was a slab slewed off the wall in the air, an arch a box floating squares
            // away. It is on the wall its square has, and where the square has both or neither,
            // on the edge its picture runs along. A pillar of the belfry, far taller than it is
            // wide, stands on its corner as before where there is no wall to hang it on.
            if (!west && !north && kind.wallTrim && (wallNorth || wallWest || texture.getHeight() < 2 * texture.getWidth())) {
                north = wallNorth != wallWest ? wallNorth : Banners.alongX(frame);
                west = !north;
            }
            // A light switch marked for no wall (the game's own, lighting_indoor_01_0 to 7, are each
            // marked attachedN, W, E or S and do not come here) is on a wall of its square, and is
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
            glazedWall = object instanceof IsoWindow;
            paperedWall = kind.papered;
            tiledSheet = kind.tiledSheet;
            wallOpens = properties.has(IsoFlagType.DoorWallN) || properties.has(IsoFlagType.DoorWallW)
                    || properties.has(IsoFlagType.WindowN) || properties.has(IsoFlagType.WindowW);
            doorWall = properties.has(IsoFlagType.DoorWallN) || properties.has(IsoFlagType.DoorWallW);
            seeThrough = isSheet(properties) || kind.linkSheet;
            linkSheet = kind.linkSheet;
            chainLink = seeThrough && overLayer == 0 && Own.buildings() ? kind.chainLink : null;
            linkPostDue = chainLink != null && chainLink.hasPost();
            picket = seeThrough && overLayer == 0 && Own.buildings() && kind.pickets;
            picketPostDue = picket;
            if (seeThrough && overLayer == 0 && Own.buildings() && kind.brokenLink != 0f) {
                frame.material = kind.brokenLink;
            }
            // A gate in a fence is rails or wire like the fence, and stays the sheet it is.
            doorLeaf = door && !kind.fenceGate;
            casing = type == IsoObjectType.doorFrW || type == IsoObjectType.doorFrN;
            wallBuilder.sign = overLayer == 0 && !door && west != north && kind.sign;
            // An open garage door's sprite is the head of its opening and the jamb at an end: wall, cut to the sprite, with the
            // door raised under the ceiling behind it. Read as a door, the band was a leaf hanging out of the head.
            boolean raised = door && kind.garageDoor && !isWall(properties, type) && overLayer == 0 && Own.buildings();
            if (west) {
                wallBuilder.addEdge(mesh, texture, isWall(properties, type) || raised, door, x, y, z, westLayer++, 0f, 1f);
                if (raised) {
                    wallBuilder.addRaisedGarageDoor(texture, x, y, z, 0f, 1f);
                }
            }
            if (north) {
                wallBuilder.addEdge(mesh, texture, isWall(properties, type) || raised, door, x, y, z, northLayer++, 1f, 0f);
                if (raised) {
                    wallBuilder.addRaisedGarageDoor(texture, x, y, z, 1f, 0f);
                }
            }
            wallBuilder.sign = false;
            if (west || north) {
                return;
            }
            // The far edges are the next square's wall, so even the first sprite here stands off it.
            if (properties.has(IsoFlagType.attachedE) || type == IsoObjectType.curtainE) {
                // Clear of the skin of the next square's wall, which stands this side of the edge.
                geometry.shownA = -WALL_HALF - HUNG_CLEAR;
                layers.lie(SpriteGeometry.LAYERS_X, -++eastLayer);
                geometry.addFace(mesh, frame, x, y, z, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);
                layers.none();
                geometry.shownA = 0f;
                return;
            }
            if (properties.has(IsoFlagType.attachedS) || type == IsoObjectType.curtainS) {
                geometry.shownB = -WALL_HALF - HUNG_CLEAR;
                layers.lie(SpriteGeometry.LAYERS_Y, -++southLayer);
                geometry.addFace(mesh, frame, x, y, z, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
                layers.none();
                geometry.shownB = 0f;
                return;
            }
            // The game marks most of a roof's sprites as roof, but not the caps along its ridge
            // and its edges. Their sprites do not say which way they lie, and stood upright like
            // furniture they are boxes floating over the house.
            if (Roofs.isTile(type)) {
                // A church tower's spire, which the game marks as a roof, is one model of the whole spire.
                if (sprite.getName() != null && Leisure.spirePiece(sprite.getName()) && furnishingBuilder.addFurniture(object, sprite, x, y, z)) {
                    return;
                }
                // The game marks a few things that are no roof as one, so that they are cut away
                // with the roof when the player walks under: a church's columns. Laid as a roof
                // tile a column is a slab leaning on the wall.
                if (overLayer == 0 && sprite.getName() != null && !kind.roofSheet && postBuilder.addPost(mesh, object, sprite, texture, x, y, z)) {
                    return;
                }
                // A building's own sheet marks the boards along its roof's edges as roof too: a
                // trailer's fascia and barge boards. Narrower than a tile, laid as one each is a
                // square of board beyond the roof. The picture makes the eave and the rake there
                // itself, as it does over the boards from the sheet of a roof's accents. The boards
                // under a rake are the gable's siding too, the lowest a tile wide and of no clear
                // slope, and the gable is made in their place.
                if (!kind.roofSheet && z > 0) {
                    RoofFit.Tile board = Roofs.measure(frame, texture, x, y, z);
                    if ((board.fitting || !board.sure) && board.half == 0 && roofBuilder.underARake(x, y, z)) {
                        roofBuilder.addGableBeyond(x, y, z);
                        return;
                    }
                    if (board.fitting && board.half == 0 && roofBuilder.trimUnderAMadeEave(x, y, z, true, true, true)) {
                        return;
                    }
                }
                roofName = sprite.getName();
                roofBuilder.addRoof(mesh, texture, x, y, z);
                return;
            }
            if (z > 0 && kind.roofSheet) {
                // Left out, but for a flat top its roof took as a tile (Roofs.flatTop). Laid level the
                // others stand proud of a gable as slabs; the sheet under each roof tile is what closes the roof.
                roofBuilder.addFlatTop(mesh, texture, x, y, z);
                return;
            }
            // A chalkboard, a notice or a map the game hangs on a wall by the way it faces and marks for no wall. Stood
            // up to face the eye it hung in the middle of its square. With the mod's furniture off it is as it was.
            if (kind.flatWall != WallHung.NONE && Own.furniture() && layFlat(mesh, kind.flatWall, x, y, z)) {
                return;
            }
            // A boulder. The game's picture of one stood up as a card is a cut-out of a boulder,
            // and wrapped on a box it is a crate painted as one.
            if (kind.boulder && Own.plants(view.index) && plantBuilder.addRock(x, y, z)) {
                return;
            }
            // Sticks the game paints lying on the ground. Stood up as a card they float upright.
            if (Flora.fallenWood(sprite.getName()) && Own.plants(view.index)) {
                Flora.addFallenWood(solid.get(PLAIN), x, y, (z + geometry.raised) * ViewCamera.LEVEL_HEIGHT, geometry.lift, Flora.mix(x, y, 86), frame);
                return;
            }
            // What the game itself moves with the wind, and what it lets the player clear as
            // growth, is growth. A tree gets a second card across the first: from the eye it is
            // edge on and unseen, but the sun sees it, so the tree's shadow has a width whichever
            // way the eye looks at the tree.
            boolean grows = kind.grows;
            // What goes on a table top is painted in its frame at the height of a table, not on
            // the floor, so its outline says nothing of where a box of it would stand: read as
            // one, a computer on a desk against a wall was a box a pace away outside the wall.
            boolean onTop = kind.onTop;
            // What stands upright on one spot and is no furniture: a column, a porch post, a lamp
            // post, a pole, a timber post with its braces.
            if (!grows && !onTop && overLayer == 0 && !properties.has(IsoFlagType.solid) && !properties.has(IsoFlagType.solidtrans)
                    && !properties.has(IsoFlagType.bed) && !properties.isTable() && properties.getSurface() <= 0 && !kind.holds
                    && postBuilder.addPost(mesh, object, sprite, texture, x, y, z)) {
                return;
            }
            // What the game says nothing about at all, and whose outline is a sheet on the grid's
            // line or a beam along it: the slab over a shop front, a sill, a pipe, a billboard.
            if (!grows && !onTop && overLayer == 0 && postBuilder.addShaped(mesh, object, sprite, texture, x, y, z)) {
                return;
            }
            // What the game counts as filling its square, or as something to sit or lie on, is
            // furniture, and furniture is a solid thing with sides and a top.
            if (!grows && !onTop && (properties.has(IsoFlagType.solid) || properties.has(IsoFlagType.solidtrans) || properties.has(IsoFlagType.bed))
                    && !kind.bedding
                    && SpriteGeometry.boxOf(frame, box) && fillsBox(texture)) {
                addBoxWithinWalls(mesh, x, y, z);
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
            if (!grows && kind.bedding) {
                // The sprite of one square of a bed is a part of the bed, and wrapped on a box it
                // leaves gaps and torn edges. The square is a block of bedding instead, the colour
                // the sprite has in the middle of the bed's top, as high as the game says a bed is.
                float high = properties.getSurface() > 0 ? properties.getSurface() / SURFACE_PIXELS_PER_LEVEL : BED_HEIGHT;
                geometry.addBlock(mesh, frame, x, y, z, high, 0.5f, 0.5f, high);
                // Over the block, what the sprite shows of the bed's top: the pillow, the fold of the
                // blanket. Where the sprite stops short the block's own colour carries on.
                layers.lie(SpriteGeometry.LAYERS_UP, 2f);
                geometry.addFace(mesh, frame, x, y, z, 0f, 0f, high, 1f, 0f, 0f, 0f, 1f, 0f);
                layers.none();
                boxes++;
                return;
            }
            // A bin, a barrel, a hydrant: something compact that the game drew from above and to
            // one side. Stood up as a flat picture it leans back at the angle it was drawn from,
            // its lid tipped towards whoever looks. As a body with a top and sides it stands
            // upright. Only what is painted through the middle of its top and both the sides it
            // shows: a chair or a table has air there.
            if (!grows && !onTop && SpriteGeometry.boxOf(frame, box) && box[2] <= 0.5f && box[3] <= 1f && fillsMiddle(texture)) {
                addBoxWithinWalls(mesh, x, y, z);
                boxes++;
                return;
            }
            // Something low and wide the game says nothing of: a vent on a flat roof, a shower's
            // tray, a hatch. It is a slab, whatever holes its sprite has in it: a fan's blades
            // show the roof between them, and the test above takes that for a chair's legs.
            if (!grows && !onTop && overLayer == 0 && !properties.isTable() && properties.getSurface() <= 0 && !properties.has(IsoFlagType.bed)
                    && SpriteGeometry.boxOf(frame, box) && Flat.isSlab(box[2], box[3])) {
                addBoxWithinWalls(mesh, x, y, z);
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
                    geometry.addUpright(mesh, frame, x, y, z, 0.5f, 0.5f, 0f, 1f, 0f, true, false);
                    geometry.keep = 1f;
                }
                return;
            }
            // Long grass. The game has it as a small picture of a clump, which up close is a
            // handful of coarse painted strokes. Here it is grown instead: tall blades, the
            // colour of the ground they stand in, thick on the square.
            if (z == 0 && kind.longGrass && Own.plants(view.index) && plantBuilder.growLongGrass(x, y)) {
                return;
            }
            if (grows) {
                plantBuilder.addGrown(mesh, sprite, type, texture, x, y, z);
                return;
            }
            if (overLayer == 0 && properties.getSurface() <= 0 && !properties.isTable() && postBuilder.addInWallLine(mesh, sprite, texture, x, y, z)) {
                return;
            }
            geometry.addUpright(mesh, frame, x, y, z, 0.5f, 0.5f, 0f, 1f, 0f, true, false);
        }

        /**
         * Lays the sprite flat on a wall of its square, as a picture the game has hung there is
         * laid, over as much of the wall's plane as it paints, which may run onto the next
         * square's length of the wall, and says whether it did. False for a sprite not painted in
         * that wall's plane, which is left as it was.
         */
        private boolean layFlat(SpriteGeometry.Mesh mesh, int wall, int x, int y, int z) {
            WallHung.extent(frame, wall, hungAt);
            if (!WallHung.paintedAlong(hungAt)) {
                return false;
            }
            float from = Math.min(hungAt[WallHung.FROM], 0f), along = Math.max(hungAt[WallHung.TO], 1f) - from;
            float foot = Math.min(hungAt[WallHung.BOTTOM], 0f), up = Math.max(hungAt[WallHung.TOP], 1f) - foot;
            // On the near walls clear of the wall's skin as a poster is, layered as addEdge layers what is not a
            // wall; on the far ones clear of the next square's wall, as what the game marks attached to them is.
            float clear = WALL_HALF + HUNG_CLEAR;
            switch (wall) {
                case WallHung.NORTH -> {
                    geometry.shownB = clear;
                    layers.lie(SpriteGeometry.LAYERS_Y, northLayer++ + 3f);
                    geometry.addFace(mesh, frame, x, y, z, from, 0f, foot, along, 0f, 0f, 0f, 0f, up);
                }
                case WallHung.WEST -> {
                    geometry.shownA = clear;
                    layers.lie(SpriteGeometry.LAYERS_X, westLayer++ + 3f);
                    geometry.addFace(mesh, frame, x, y, z, 0f, from, foot, 0f, along, 0f, 0f, 0f, up);
                }
                case WallHung.SOUTH -> {
                    geometry.shownB = -clear;
                    layers.lie(SpriteGeometry.LAYERS_Y, -++southLayer);
                    geometry.addFace(mesh, frame, x, y, z, from, 1f, foot, along, 0f, 0f, 0f, 0f, up);
                }
                default -> {
                    geometry.shownA = -clear;
                    layers.lie(SpriteGeometry.LAYERS_X, -++eastLayer);
                    geometry.addFace(mesh, frame, x, y, z, 1f, from, foot, 0f, along, 0f, 0f, 0f, up);
                }
            }
            layers.none();
            geometry.shownA = geometry.shownB = 0f;
            return true;
        }

        /** The room of the plant being added: see roomFor. */
        final float[] room = new float[4];

        final SpriteGeometry.Frame dyeFrame = new SpriteGeometry.Frame();

        final float[] hungAt = new float[4], hungShows = new float[6], hungWay = new float[2];

        /**
         * Stands the mod's model of a parasol where the map has the four quarters of a round
         * canopy, and says whether this sprite is thereby dealt with: see Parasols. The south-east
         * quarter stands the model, with its pole at the corner the four squares meet at, and the
         * other three add nothing. Nor does the wall's corner post the map stands on the
         * south-east square for a pole: the model has its own.
         *
         * False, which leaves the sprite to be drawn as it is, for a canopy with a quarter
         * missing and for one whose south-east sprite has nothing painted where the cloth's
         * colours are taken. Every quarter reaches the same answer.
         */
        private boolean addParasol(int quarter, IsoObject object, IsoSprite sprite, PropertyContainer properties, int x, int y, int z) {
            // The pole is a post of a building's wall. A fence's corner post on the south-east square is the fence's.
            boolean pole = properties.has(IsoFlagType.WallSE) && sprite.getName() != null && sprite.getName().startsWith("walls_");
            if (quarter == Parasols.NONE && !pole) {
                return false;
            }
            IsoObject front = Parasols.front(Parasols.on(cell, z), quarter, pole, x, y);
            if (front == null) {
                return false;
            }
            Texture cloth = front.getSprite().getTextureForCurrentFrame(front.getDir());
            if (!usable(cloth)) {
                return false;
            }
            dyeFrame.scale = frame.scale;
            WorldScene.frameOf(dyeFrame, front, cloth);
            if (!cloth.isMaskSet(Math.round(SpriteGeometry.frameU(dyeFrame, Parasols.FIRST_A, Parasols.FIRST_B)),
                    Math.round(SpriteGeometry.frameV(dyeFrame, Parasols.FIRST_A, Parasols.FIRST_B, Parasols.CLOTH_HIGH)))
                    || !cloth.isMaskSet(Math.round(SpriteGeometry.frameU(dyeFrame, Parasols.SECOND_A, Parasols.SECOND_B)),
                            Math.round(SpriteGeometry.frameV(dyeFrame, Parasols.SECOND_A, Parasols.SECOND_B, Parasols.CLOTH_HIGH)))) {
                return false;
            }
            if (object != front || overLayer > 0) {
                return true;
            }
            building.plain = false;
            // The corner the pole stands at is this square's north-west one.
            float foot = z * ViewCamera.LEVEL_HEIGHT + geometry.lift[0];
            placed.add(Models.get(Parasols.modelOf(properties.get("GroupName"))), frame, x, y, x, y, foot, 0, Furniture.TILES_PER_METRE, Furniture.TILES_PER_METRE,
                    cloth.getID(), 1 << Models.CLOTH, SpriteGeometry.textureU(dyeFrame, Parasols.FIRST_A, Parasols.FIRST_B),
                    SpriteGeometry.textureV(dyeFrame, Parasols.FIRST_A, Parasols.FIRST_B, Parasols.CLOTH_HIGH),
                    1 << Models.LINEN, SpriteGeometry.textureU(dyeFrame, Parasols.SECOND_A, Parasols.SECOND_B),
                    SpriteGeometry.textureV(dyeFrame, Parasols.SECOND_A, Parasols.SECOND_B, Parasols.CLOTH_HIGH));
            furnished++;
            return true;
        }

        final float[] awningAt = new float[2];

        /**
         * Hangs the mod's model of a length of a shop's awning on a square the game lays one on,
         * and says whether the sprite is thereby dealt with: see Awnings. Its cloth takes the
         * sprite's two colours, from two places on the slope a stripe apart. False, which leaves
         * the sprite to be drawn as it was, for a sprite with nothing painted at either place.
         */
        private boolean addAwning(Awnings.Hung hung, Texture texture, int x, int y, int z) {
            if (hung == null) {
                return false;
            }
            if (!texture.isMaskSet(Math.round(SpriteGeometry.frameU(frame, hung.firstA, hung.firstB)), Math.round(SpriteGeometry.frameV(frame, hung.firstA, hung.firstB, hung.dyeHigh)))
                    || !texture.isMaskSet(Math.round(SpriteGeometry.frameU(frame, hung.secondA, hung.secondB)),
                            Math.round(SpriteGeometry.frameV(frame, hung.secondA, hung.secondB, hung.dyeHigh)))) {
                return false;
            }
            if (overLayer > 0) {
                return true;
            }
            building.plain = false;
            Models.Model model = Models.get(hung.model);
            // The wall it is fixed to is behind its back, the way its front faces turned about.
            Models.turn(hung.turns, 0f, -1f, awningAt);
            float rise = awningAt[1] > 0.5f ? kerbTopRise(z, true) : awningAt[0] > 0.5f ? kerbTopRise(z, false) : 0f;
            hung.centre(model.depth(), awningAt);
            float foot = z * ViewCamera.LEVEL_HEIGHT + (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f + rise
                    + hung.foot * ViewCamera.LEVEL_HEIGHT;
            placed.add(model, frame, x, y, x + awningAt[0], y + awningAt[1], foot, hung.turns, Furniture.TILES_PER_METRE, Furniture.TILES_PER_METRE,
                    texture.getID(), 1 << Models.CLOTH, SpriteGeometry.textureU(frame, hung.firstA, hung.firstB), SpriteGeometry.textureV(frame, hung.firstA, hung.firstB, hung.dyeHigh),
                    1 << Models.LINEN, SpriteGeometry.textureU(frame, hung.secondA, hung.secondB), SpriteGeometry.textureV(frame, hung.secondA, hung.secondB, hung.dyeHigh));
            placed.built();
            furnished++;
            return true;
        }

        /**
         * Lays a piece of a tent or of a market stall's canopy on the plane its sprite paints,
         * and strings its rope from under the eaves to the ground: see Tents. The rope is the
         * mod's own line, and with the mod's furniture off its sprite is left to be drawn as it
         * was. False for any other sprite.
         */
        private final float[] gablePoints = new float[9], liningAt = new float[12], liningPicture = new float[12], liningU = new float[4], liningV = new float[4];
        private static final int[] LINING_ORDER = {0, 1, 2, 0, 2, 3};

        /**
         * Lines a tent's gable inside with its own picture up to the canvas: see
         * Tents.LINING_IN. Each corner takes the texel of the gable straight out from it, drawn
         * in towards the lining's middle (Tents.LINING_DRAWN_IN), so that no texel of it is one
         * the sprite leaves clear or outlines along its top or its ends.
         */
        private void addGableLining(SpriteGeometry.Mesh mesh, Tents.Piece gable, int x, int y, int z) {
            Tents.lining(gable, canvas, liningAt);
            Tents.liningPicture(gable, liningAt, liningPicture);
            float middleU = 0f, middleV = 0f;
            for (int corner = 0; corner < 4; corner++) {
                float a = liningPicture[corner * 3], b = liningPicture[corner * 3 + 1], h = liningPicture[corner * 3 + 2];
                liningU[corner] = SpriteGeometry.frameU(frame, a, b);
                liningV[corner] = SpriteGeometry.frameV(frame, a, b, h);
                middleU += liningU[corner] / 4f;
                middleV += liningV[corner] / 4f;
            }
            float inset = SpriteGeometry.insetPixels(Tents.LINING_DRAWN_IN) * frame.scale;
            boolean north = gable.alongX();
            mesh.open = frame.open + frame.kind + 32f * frame.material;
            mesh.face(north ? 0f : 1f, north ? 1f : 0f, 0f);
            for (int corner : LINING_ORDER) {
                float a = liningAt[corner * 3], b = liningAt[corner * 3 + 1];
                float u = liningU[corner] + Math.clamp(middleU - liningU[corner], -inset, inset);
                float v = liningV[corner] + Math.clamp(middleV - liningV[corner], -inset, inset);
                float wa = Math.clamp(a, 0f, 1f), wb = Math.clamp(b, 0f, 1f);
                float w0 = (1f - wa) * (1f - wb), w1 = wa * (1f - wb), w2 = wa * wb, w3 = (1f - wa) * wb;
                mesh.litAt(frame, w0, w1, w2, w3);
                mesh.add(x + a, y + b, (z + liningAt[corner * 3 + 2]) * ViewCamera.LEVEL_HEIGHT + geometry.liftAt(a, b),
                        SpriteGeometry.textureAtU(frame, u), SpriteGeometry.textureAtV(frame, v),
                        frame.cornerRed[0] * w0 + frame.cornerRed[1] * w1 + frame.cornerRed[2] * w2 + frame.cornerRed[3] * w3,
                        frame.cornerGreen[0] * w0 + frame.cornerGreen[1] * w1 + frame.cornerGreen[2] * w2 + frame.cornerGreen[3] * w3,
                        frame.cornerBlue[0] * w0 + frame.cornerBlue[1] * w1 + frame.cornerBlue[2] * w2 + frame.cornerBlue[3] * w3);
            }
            mesh.litAsFace();
        }

        private boolean addTent(SpriteGeometry.Mesh mesh, Tents.Piece piece, Tents.Piece rope, int x, int y, int z) {
            if (!Tents.laid(piece, rope, Own.furniture())) {
                return false;
            }
            building.plain = false;
            if (piece != null) {
                boolean alongX = piece.alongX();
                geometry.drawnIn = piece.kind() == Tents.CANOPY || piece.kind() == Tents.GABLE ? Tents.DRAWN_IN : 0f;
                switch (piece.kind()) {
                    case Tents.CANOPY -> {
                        float rise = piece.to() - piece.from();
                        geometry.addFace(mesh, frame, x, y, z, 0f, 0f, piece.from(), 1f, 0f, alongX ? rise : 0f, 0f, 1f, alongX ? 0f : rise);
                    }
                    case Tents.GABLE -> {
                        // Up to the canopy's line, as two triangles of the wall under it, and lined inside.
                        float endA = alongX ? 1f : 0f, endB = alongX ? 0f : 1f;
                        set(gablePoints, 0, 0f, 0f, 0f);
                        set(gablePoints, 1, endA, endB, 0f);
                        set(gablePoints, 2, endA, endB, piece.to());
                        geometry.addTriangle(mesh, frame, x, y, z, gablePoints);
                        set(gablePoints, 1, endA, endB, piece.to());
                        set(gablePoints, 2, 0f, 0f, piece.from());
                        geometry.addTriangle(mesh, frame, x, y, z, gablePoints);
                        addGableLining(mesh, piece, x, y, z);
                    }
                    case Tents.FLAP -> geometry.addFace(mesh, frame, x, y, z, 0f, 0f, 1f, alongX ? 1f : 0f, alongX ? 0f : 1f, 0f,
                            alongX ? 0f : Tents.FLAP_OUT, alongX ? Tents.FLAP_OUT : 0f, -Tents.STEP * Tents.FLAP_OUT);
                    default -> throw new IllegalStateException("a tent's piece of kind " + piece.kind());
                }
                geometry.drawnIn = 0f;
            }
            if (rope != null && overLayer == 0) {
                float topX = x + rope.topA(), topY = y + rope.topB(), footX = x + rope.footA(), footY = y + rope.footB();
                float floor = z * ViewCamera.LEVEL_HEIGHT;
                wireBuilder.cable(topX, topY, floor + landAt(topX, topY) + rope.topH() * ViewCamera.LEVEL_HEIGHT, 0f, footX, footY, floor + landAt(footX, footY), 0f, 0f);
            }
            return true;
        }

        /**
         * Hangs the mod's model of a length of a hood of roofing over a wall that stands on its
         * own, and draws nothing for the pieces of eave at the ends of one, which its model's
         * hips stand in for: see Hoods. Its roofing takes the sprite's colour. False for any
         * other sprite, and for a hood's sprite with nothing painted where the colour is taken.
         */
        private boolean addHood(Kind kind, Texture texture, int x, int y, int z) {
            int wall = kind.eave;
            if (wall == Hoods.NONE) {
                return kind.endPiece && Hoods.endOfAHood(Hoods.on(cell, z), x, y);
            }
            Hoods.Squares squares = Hoods.on(cell, z);
            if (!Hoods.hood(squares, x, y)) {
                return false;
            }
            float[] dye = Hoods.dyedAt(wall);
            if (!texture.isMaskSet(Math.round(SpriteGeometry.frameU(frame, dye[0], dye[1])), Math.round(SpriteGeometry.frameV(frame, dye[0], dye[1], Hoods.DYE_HIGH)))) {
                return false;
            }
            if (overLayer > 0) {
                return true;
            }
            building.plain = false;
            Hoods.origin(wall, awningAt);
            float foot = z * ViewCamera.LEVEL_HEIGHT + (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f
                    + kerbTopRise(z, wall == Outdoors.NORTH) + Hoods.FOOT * ViewCamera.LEVEL_HEIGHT;
            placed.add(Models.get(Hoods.modelOf(squares, x, y)), frame, x, y, x + awningAt[0], y + awningAt[1], foot, Hoods.turns(wall),
                    Furniture.TILES_PER_METRE, Furniture.TILES_PER_METRE, texture.getID(), 1 << Models.PAINT,
                    SpriteGeometry.textureU(frame, dye[0], dye[1]), SpriteGeometry.textureV(frame, dye[0], dye[1], Hoods.DYE_HIGH), 0, 0f, 0f);
            placed.built();
            furnished++;
            return true;
        }

        /**
         * Stands the mod's model of a section of a pylon, a mast or a tank where the map has
         * every sprite of it that tells it, and says whether this sprite is thereby dealt with:
         * see Towers. The anchor's sprite stands the model and the section's other sprites add
         * nothing. A section with a square not loaded yet adds nothing either, and the build is
         * made again when the map has more.
         */
        private boolean addTower(Towers.Part[] parts, IsoSprite sprite, int x, int y, int z) {
            boolean dealt = false;
            String stood = null;
            for (Towers.Part part : parts) {
                Towers.Section section = part.section();
                int at = Towers.whole(Towers.on(cell), section, x - part.dx(), y - part.dy(), z - part.dz());
                if (at == Towers.NOT_LOADED) {
                    wantsMap = true;
                    dealt = true;
                    continue;
                }
                if (at != Towers.WHOLE) {
                    continue;
                }
                dealt = true;
                if (part.dx() != 0 || part.dy() != 0 || part.dz() != 0 || !section.anchor().equals(sprite.getName()) || overLayer > 0
                        || section.model().equals(stood)) {
                    continue;
                }
                stood = section.model();
                building.plain = false;
                float centreX = x + section.a(), centreY = y + section.b();
                heightsAt(centreX, centreY);
                float foot = z * ViewCamera.LEVEL_HEIGHT + terrain.lift(centreX, centreY, z) + section.raised();
                // A section reaches some squares from its anchor's, and the light of that square
                // drawn on across them by its corners runs far off what it is at their corners.
                // The whole section is lit as the middle of that square is.
                for (int corner = 0; corner < 4; corner++) {
                    towerLight.cornerRed[corner] = (frame.cornerRed[0] + frame.cornerRed[1] + frame.cornerRed[2] + frame.cornerRed[3]) / 4f;
                    towerLight.cornerGreen[corner] = (frame.cornerGreen[0] + frame.cornerGreen[1] + frame.cornerGreen[2] + frame.cornerGreen[3]) / 4f;
                    towerLight.cornerBlue[corner] = (frame.cornerBlue[0] + frame.cornerBlue[1] + frame.cornerBlue[2] + frame.cornerBlue[3]) / 4f;
                    towerLight.cornerUnlit[corner] = frame.cornerUnlit[0] < 0f ? SpriteGeometry.AS_FACE
                            : (frame.cornerUnlit[0] + frame.cornerUnlit[1] + frame.cornerUnlit[2] + frame.cornerUnlit[3]) / 4f;
                    towerLight.cornerDay[corner] = (frame.cornerDay[0] + frame.cornerDay[1] + frame.cornerDay[2] + frame.cornerDay[3]) / 4f;
                }
                towerLight.unlitKind = frame.unlitKind;
                towerLight.open = frame.open;
                placed.add(Models.get(section.model()), towerLight, x, y, centreX, centreY, foot, section.turns(), Furniture.TILES_PER_METRE,
                        Furniture.TILES_PER_METRE, 0, 0, 0f, 0f, 0, 0f, 0f);
                placed.built();
                furnished++;
            }
            return dealt;
        }

        final SpriteGeometry.Frame towerLight = new SpriteGeometry.Frame();
        /** How far a pylon wire's marker ball reaches under the wire through its middle, in metres. */
        private static final float MARKER_BALL_HALF = 0.13f;

        /** Whether the mains are on: read once a frame. */
        boolean mainsOn;
        final SpriteGeometry.Frame bandFrame = new SpriteGeometry.Frame();
        /** Where fasciaOf last found a band: the square it hangs on. */
        int bandX, bandY;

        /** The number of the square of rug the sprite paints, laid at (x, y, z), as Rugs.of: 0 for no rug, and for one whose picture is not on the card yet, with the build left to be made again. */
        int rugOf(Texture texture, int kind, float lifted, int x, int y, int z) {
            int rug = Rugs.of(texture, frame, kind, lifted, x, y, z);
            if (rug == Rugs.PENDING) {
                building.signed = false;
                return 0;
            }
            return rug;
        }

        /**
         * The name of the picture of the sprite the frame holds, read as a floor, a wall or a
         * roof on the plane given: see Flats. 0 for a sprite with none, and for one not yet
         * read, which is drawn from its sprite in a build that is made again.
         */
        int flatOf(Texture texture, int as, float high, float climbA, float climbB) {
            int flat = Flats.of(texture, frame, as, high, climbA, climbB);
            if (flat == Flats.PENDING) {
                building.signed = false;
                return 0;
            }
            return flat;
        }

        /** Whether the sprite being added is of a sheet of outside walls, and of one of the walls under a roof's end: see laid. */
        boolean laidWall, gableSheet;

        /** Whether the door being added stands open across its doorway, and whether it is a leaf with a thickness. */
        boolean doorSwung, doorLeaf;
        /** Whether the sprite being added is a garage door's, which has no hinges and no knob and is the same door seen from either side. */
        boolean garageDoor;
        /** Whether the sprite being added is the frame round a doorway. */
        boolean casing;
        /** Whether the sprite being added is a wall a doorway is in. */
        boolean doorWall;

        /** Whether the sprite being added is a window's that is also a wall. */
        boolean glazedWall;
        /** The build being filled. */
        ChunkMeshes building;

        /** The lamps that burn within reach of the chunk being summed, found once a sum: a square of a room asks only these. */
        final ArrayList<IsoLightSource> lampsNear = new ArrayList<>();

        boolean paperedWall;
        /** The sheet the sprite being added is of, its name up to the number, where it is a sheet of tiled walls (see WallTiles); null otherwise. */
        String tiledSheet;
        /** The same of the sprite hung over the one being added, while it is: tiles laid over a wall. */
        String overSheet;
        /** The colour the papered wall's mesh is painted over in, as the picture is told it, and the light of the piece being added while its stripes are. */
        final float[] paperTint = new float[4], paperRed = new float[4], paperGreen = new float[4], paperBlue = new float[4], paperUnlit = new float[SpriteGeometry.Frame.UNLIT_FLOATS];

        /** The level the walls that meet the wall being added are looked for on. */
        int joinLevel;
        /** Whether a wall with a thickness stands on the north or west edge of a square of joinLevel. */
        final WallJoins.Walls standing = (wallX, wallY, north) -> {
            IsoGridSquare square = cell.getGridSquare(wallX, wallY, joinLevel);
            IsoObject wall = square == null ? null : wallOn(square, north);
            return wall != null && !isSheet(wall.getSprite().getProperties());
        };
        /** Whether the wall being added is a sheet: see isSheet. */
        boolean seeThrough;
        /** Whether the sprite being added is a sheet of chain link, which stands on its edge whether or not the game calls it a wall: see ChainLink.sheet. */
        boolean linkSheet;
        /** The chain link fencing the sheet being added is built as, or null for its sprite: see ChainLink. */
        ChainLink.Run chainLink;
        /** Whether the post of the chain link being added is still to be built: a corner piece is on two edges and has one post. */
        boolean linkPostDue;
        /** Whether the sheet being added is a length of white picket fence, built as its model: see Pickets. */
        boolean picket;
        /** Whether the post of the picket fence being added is still to be stood: as linkPostDue. */
        boolean picketPostDue;

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

        boolean painted(Texture texture, float a, float b, float h) {
            return texture.isMaskSet(Math.round(SpriteGeometry.frameU(frame, a, b)), Math.round(SpriteGeometry.frameV(frame, a, b, h)));
        }

        final Flights flights = new Flights(this::flightQuad);
        private final float[] flightCorners = new float[12];
        private SpriteGeometry.Mesh flightMesh;
        /** Where on its square a point of Flights' frame is: a = flightA + along * alongA + across * acrossA, and b likewise. */
        private float flightA, flightB, alongA, alongB, acrossA, acrossB;
        private int flightX, flightY, flightZ;

        /** Has what Flights builds next go into this mesh, as the given square of a flight on the square at x, y, z. */
        void flightOn(SpriteGeometry.Mesh mesh, int x, int y, int z, boolean west, int third) {
            frameOn(mesh, x, y, z, west ? 1f + third : 0f, west ? 0f : 1f + third, west ? -1f : 0f, west ? 0f : -1f, west ? 0f : 1f, west ? 1f : 0f);
        }

        private void frameOn(SpriteGeometry.Mesh mesh, int x, int y, int z, float a, float b, float sA, float sB, float cA, float cB) {
            flightMesh = mesh;
            flightX = x;
            flightY = y;
            flightZ = z;
            flightA = a;
            flightB = b;
            alongA = sA;
            alongB = sB;
            acrossA = cA;
            acrossB = cB;
        }

        private void flightQuad(float[] q) {
            float[] c = flightCorners;
            for (int corner = 0; corner < 4; corner++) {
                float along = q[corner * 3], across = q[corner * 3 + 1];
                float a = flightA + along * alongA + across * acrossA, b = flightB + along * alongB + across * acrossB;
                set(c, corner, flightX + a, flightY + b, (flightZ + q[corner * 3 + 2]) * ViewCamera.LEVEL_HEIGHT + geometry.liftAt(a, b));
            }
            SpriteGeometry.addQuad(flightMesh, c, 0f, 0f, frame.red, frame.green, frame.blue);
        }

        /** Which square of a flight that climbs the given way the map has at a place: 0 the bottom one, 2 the top, -1 for none. */
        int flightThirdAt(int x, int y, int z, boolean west) {
            IsoGridSquare square = cell.getGridSquare(x, y, z);
            if (square == null) {
                return -1;
            }
            PZArrayList<IsoObject> objects = square.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                IsoSprite sprite = objects.get(i).getSprite();
                if (sprite != null && thirdOf(sprite.getType()) >= 0 && climbsWest(sprite.getType()) == west) {
                    return thirdOf(sprite.getType());
                }
            }
            return -1;
        }

        /**
         * A length of handrail, built as a rail: see Rails and Flights. A length of a flight's
         * rail is built where the map has the square of the flight it is the rail of, on its
         * own square or on the one before it across the flight, and the lengths the map hangs
         * a level up over a flight are that same rail, built already, but for the level rail
         * one of them has joined to it. A rail along a wall
         * stands clear of the wall on the flight's side. False for a length of a flight's rail
         * with no flight to be the rail of, which is left the sprite it is.
         */
        private boolean addRail(Rails.Piece piece, int x, int y, int z) {
            boolean slope = piece.part == Rails.SLOPE || piece.part == Rails.ABOVE || piece.part == Rails.ABOVE_AND_LEVEL;
            boolean own = false;
            // What is built depends on a square that may be another chunk's.
            building.plain = false;
            if (slope) {
                int level = piece.part == Rails.SLOPE ? z : z - 1;
                own = flightThirdAt(x, y, level, piece.west) == piece.third;
                if (!own && flightThirdAt(piece.west ? x : x - 1, piece.west ? y - 1 : y, level, piece.west) != piece.third) {
                    return false;
                }
                if (piece.part == Rails.ABOVE) {
                    return true;
                }
            }
            SpriteGeometry.Mesh mesh = solid.get(PLAIN, WallSides.key(piece.red, piece.green, piece.blue, WallSides.FLAT));
            mesh.open = frame.open;
            mesh.sway = 0f;
            if (piece.part == Rails.ABOVE_AND_LEVEL) {
                if (piece.west) {
                    frameOn(mesh, x, y, z, 0f, 0f, 1f, 0f, 0f, 1f);
                } else {
                    frameOn(mesh, x, y, z, 0f, 0f, 0f, 1f, 1f, 0f);
                }
                flights.level(piece.style, 0f);
                return true;
            }
            if (slope) {
                boolean walled = piece.west ? wallNorth : wallWest;
                flightOn(mesh, x, y, z, piece.west, piece.third);
                flights.slope(piece.third, piece.style, walled ? (own ? RAIL_OFF_WALL : -RAIL_OFF_WALL) : 0f);
                return true;
            }
            if (piece.part == Rails.LEVEL_WEST || piece.part == Rails.LEVEL_BOTH) {
                frameOn(mesh, x, y, z, 0f, 0f, 0f, 1f, 1f, 0f);
                flights.level(piece.style, 0f);
            }
            if (piece.part == Rails.LEVEL_NORTH || piece.part == Rails.LEVEL_BOTH) {
                frameOn(mesh, x, y, z, 0f, 0f, 1f, 0f, 0f, 1f);
                flights.level(piece.style, 0f);
            }
            if (piece.part == Rails.CORNER_POST || piece.part == Rails.LEVEL_BOTH && piece.style == Rails.BALUSTRADE) {
                frameOn(mesh, x, y, z, 0f, 0f, 1f, 0f, 0f, 1f);
                flights.cornerPost(piece.style);
            }
            return true;
        }

        /**
         * Whether a post the map stands under the head of a flight is the one Flights stands
         * there: it is on the square a fire escape arrives over, the flight's top square has a
         * floor for the built posts to stand on, and the flight's sprite has been read for the
         * colour they are built in. Until it has, the flight has no posts and the map's stays.
         */
        private boolean underAFlightsHead(int x, int y, int z) {
            building.plain = false;
            for (int way = 0; way <= 1; way++) {
                boolean west = way == 0;
                IsoGridSquare top = cell.getGridSquare(west ? x + 1 : x, west ? y : y + 1, z);
                if (top == null || top.getFloor() == null) {
                    continue;
                }
                PZArrayList<IsoObject> objects = top.getObjects();
                for (int i = 0; i < objects.size(); i++) {
                    IsoObject object = objects.get(i);
                    IsoSprite sprite = object.getSprite();
                    if (sprite != null && thirdOf(sprite.getType()) == 2 && climbsWest(sprite.getType()) == west && kindOf(sprite).fireEscape) {
                        Texture stair = sprite.getTextureForCurrentFrame(object.getDir());
                        if (usable(stair) && SpriteColours.of(stair) != null) {
                            return true;
                        }
                        building.signed &= !usable(stair) || !SpriteColours.unread(stair);
                    }
                }
            }
            return false;
        }

        @Override
        public void render() {
            if (!FirstPerson.TRACE) {
                draw();
                return;
            }
            long before = Allocation.byThisThread();
            draw();
            drawnBytes.addAndGet(Allocation.byThisThread() - before);
        }

        private void draw() {
            MouseGrab.sync();
            view.characters.viewer = viewer;

            GL11.glGetIntegerv(GL_VIEWPORT, viewport);
            if (viewport[2] <= 0 || viewport[3] <= 0) {
                // Nothing to draw into, and no aspect ratio to draw it with.
                return;
            }
            float aspect = (float) viewport[2] / viewport[3];
            float near = ViewCamera.nearPlane(NEAR_CORNER_DISTANCE, tanHalfFieldOfView, aspect);
            drawnAspect = aspect;
            if (tanHalfSet != loggedTanHalfSet || aspect != loggedAspect) {
                loggedTanHalfSet = tanHalfSet;
                loggedAspect = aspect;
                // Every term of the zoom factor, on the first frame drawn and whenever the field of
                // view that is set or the picture's shape changes. Both angles are vertical, so the
                // aspect is no term of the factor, and the factor is 1 whenever no firearm is aimed.
                Log.info(String.format(Locale.ROOT, "first person field of view: drawn at %.2f degrees vertical"
                                + " (%.2f horizontal at aspect %.4f, %dx%d), set to %.2f degrees vertical,"
                                + " head tracking zoom factor %.4f, near plane %.4f",
                        Math.toDegrees(2 * Math.atan(tanHalfFieldOfView)),
                        Math.toDegrees(2 * Math.atan(tanHalfFieldOfView * aspect)), aspect, viewport[2], viewport[3],
                        Math.toDegrees(2 * Math.atan(tanHalfSet)),
                        view.headTracking.zoomFactor(), near));
            }
            view.characters.aim.set(aim);
            System.arraycopy(eyeFromAim, 0, view.characters.eyeFromAim, 0, 3);
            // What the picture does without for an eye thousands of tiles off, it has again once
            // the eye is as near as the place looked at is taken to be.
            view.pipeline.outside = outside;
            view.pipeline.landing = outside ? landing : 1f;
            view.pipeline.overhead = outside && outsideAway > OVERHEAD_RANGE;
            view.pipeline.seated = seated;
            view.pipeline.seatedX = seatedX;
            view.pipeline.seatedY = seatedY;
            view.pipeline.seatedGroundUp = seatedGroundUp;
            System.arraycopy(seatedTurn, 0, view.pipeline.seatedTurn, 0, seatedTurn.length);
            System.arraycopy(seatedAxes, 0, view.pipeline.seatedAxes, 0, seatedAxes.length);
            view.pipeline.panes = panes.count > 0 ? panes : null;
            view.pipeline.opacity = opacity;
            view.pipeline.cutAbove = outside ? cutFrom : Float.MAX_VALUE;
            view.pipeline.cutBehind = 1f - landing;
            view.pipeline.lid = outside && outsideCamera.eyeUp > lidFrom ? lidFrom : Float.MAX_VALUE;
            view.pipeline.cutRows = cutRows;
            view.pipeline.sight = sightRead ? sight : null;
            view.pipeline.cutRowsX = cutRowsX;
            view.pipeline.cutRowsY = cutRowsY;
            view.pipeline.cutBelow = outside ? cutBelow : 0f;
            view.pipeline.sheltered = sheltered;
            view.pipeline.roomAir = roomAir;
            view.pipeline.floorUp = stoodUp;
            view.pipeline.away = outside ? outsideAway : 0f;
            view.pipeline.cutBox[0] = boxMinX;
            view.pipeline.cutBox[1] = boxMinY;
            view.pipeline.cutBox[2] = boxMaxX;
            view.pipeline.cutBox[3] = boxMaxY;
            view.pipeline.loadedX = loadedX;
            view.pipeline.loadedY = loadedY;
            view.pipeline.focusX = camera.eyeX;
            view.pipeline.focusY = camera.eyeY;
            view.pipeline.focusUp = camera.eyeUp;
            view.pipeline.canopy(canopyX, canopyY, canopyUp, canopyAimed, canopyAimX, canopyAimY, canopyFaded, canopyFadedCount);
            if (outside) {
                view.pipeline.drawDistance.whole();
            } else {
                view.pipeline.drawDistance.copy(drawDistance);
            }
            if (waterShown) {
                WaterMirror.plane(terrain, camera.eyeX, camera.eyeY, waterPlane);
            }
            view.pipeline.water = waterShown ? waterPlane : null;
            view.pipeline.ahead = builtAheadOf;
            if (outside) {
                view.pipeline.rangeFrom = Math.max(outsideAway - OVERHEAD_RANGE, 0f);
                // From far overhead nothing fades into the sky: the edge of the loaded map is beyond
                // the picture's. The fade comes in from infinity as the camera comes down.
                float fogAt = landing == 0f ? Float.MAX_VALUE / 2f : fogEnd / landing;
                view.pipeline.loadedWithin = landing == 0f ? Float.MAX_VALUE / 2f : loadedWithin / landing;
                view.pipeline.roomsSeen = null;
                view.pipeline.storeysFrom = Integer.MAX_VALUE;
                view.pipeline.figureRooms = null;
                float nearest = Math.max(outsideAway - OVERHEAD_DEPTH, ViewCamera.nearPlane(NEAR_CORNER_DISTANCE, outsideTan, aspect));
                view.pipeline.draw(shown, outsideCamera, sky, outsideTan, nearest, outsideAway + FAR, fogAt * FOG_START, fogAt, exposure, viewport);
                lastDrawMillis = view.pipeline.lastMillis();
                return;
            }
            view.pipeline.rangeFrom = 0f;
            view.pipeline.loadedWithin = loadedWithin;
            view.pipeline.roomsSeen = aspect == roomsAspect ? roomsSeen : null;
            view.pipeline.storeysFrom = heldFrom;
            view.pipeline.storeysOf = heldBuilding;
            view.pipeline.figureRooms = figureRooms;
            view.pipeline.draw(shown, camera, sky, tanHalfFieldOfView, near, FAR, fogEnd * FOG_START, fogEnd, exposure, viewport);
            lastDrawMillis = view.pipeline.lastMillis();
        }
    }
}

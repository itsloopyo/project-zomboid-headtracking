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
import zombie.iso.areas.IsoRoom;
import zombie.iso.fboRenderChunk.FBORenderChunk;
import zombie.iso.fboRenderChunk.FBORenderCutaways;
import zombie.iso.fboRenderChunk.FBORenderLevels;
import zombie.iso.objects.IsoBarricade;
import zombie.iso.objects.IsoCurtain;
import zombie.iso.objects.IsoDeadBody;
import zombie.iso.objects.IsoDoor;
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

    /** How far the aim is traced. */
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
    private static final float EXPOSURE_MOST = 3.5f;
    private static final float EXPOSURE_MOST_AT_NIGHT = 1.5f;
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
    private float shaded;
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
        /** For each run, the room it is the contents of, drawn only while the eye sees into it, or RoomSight.OUTSIDE: see SpriteGeometry.MeshSet.room. */
        long[] runRoom = new long[32];
        int runs, solidRuns;
        /** How many floats the runs come to. */
        int floats;
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
        private int wx, wy;
        private int builtFrame;
        /** The frame the build was last made in or found still to be what a new one would be. */
        private int checkedFrame;
        /** When it was last built or found unchanged, when it was built, and when its chunk was first built, on System.nanoTime's clock. */
        private long checkedNanos, builtNanos, bornNanos;
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
        /** The heights the build was made on, and which chunks it took any from, those round its own and those further off: see landOf. */
        private int land, terrainVersion, heightsRound;
        private long[] heightsFar = NO_CHUNKS;
        /** Whether the build left the span of a wire unknown at an end because the map was not loaded that far: see landOf. */
        private boolean wantsMap;
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
        /** Dev only: what the build holds, for SightTrace. Null unless the trace is on. */
        Set<Object> traced;
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
            minX = minY = minUp = Float.POSITIVE_INFINITY;
            maxX = maxY = maxUp = Float.NEGATIVE_INFINITY;
            turned = 0f;
            layered = 0f;
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
                    runTexture = Arrays.copyOf(runTexture, runs * 2);
                    runTint = Arrays.copyOf(runTint, runs * 2);
                    runFloats = Arrays.copyOf(runFloats, runs * 2);
                    runRoom = Arrays.copyOf(runRoom, runs * 2);
                }
                runTexture[runs] = mesh.texture;
                runTint[runs] = mesh.tint;
                runRoom[runs] = mesh.room;
                runFloats[runs] = mesh.floats;
                runs++;
                floats = Math.addExact(floats, mesh.floats);
                float[] from = mesh.vertices;
                float lowX = minX, lowY = minY, lowUp = minUp, highX = maxX, highY = maxY, highUp = maxUp, along = turned, layers = layered;
                for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                    float x = from[at], y = from[at + 1], up = from[at + 2];
                    float moved = from[at + SpriteGeometry.WAY_AT];
                    if (moved == SpriteGeometry.FACES) {
                        along = Math.max(along, Math.abs(from[at + SpriteGeometry.ALONG_AT]));
                    } else if (moved != 0f) {
                        layers = Math.max(layers, Math.abs(from[at + SpriteGeometry.ALONG_AT]));
                    }
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
    /** How much of its opening-up the eye is refused at full night. */
    private static final float NIGHT_HELD_BACK = 0.5f;
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
     * and it is part of what every chunk with an unseen square beside it is built from: such
     * chunks, which at night are most of them, were built again on every frame for it. A step
     * of under a five-hundredth in the light is left untaken, for the same reason.
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
     * sight are, which the hour does not change. Both are every build's, so they move in
     * steps of Sightline.LIGHT_STEP and no finer. Less than 0 until rooms have been in sight.
     */
    private final OpenLight seenRooms = new OpenLight(), seenLamplit = new OpenLight();
    private float roomShare = -1f, lampLevel = -1f;
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
    volatile float openNow, daylightKeptNow = 1f;
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
        final boolean laidWall, gableSheet, lies, wire, roofAccent, roofSheet, stripLamp, garageDoor, cornerTrim, papered, fenceGate,
                boulder, bedding, longGrass, natural, carpet, tiled, grows, onTop, holds, flightPost, fireEscape, paint;
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

        Kind(IsoSprite sprite) {
            PropertyContainer properties = sprite.getProperties();
            String name = sprite.getName();
            IsoObjectType type = sprite.getType();
            String floorMaterial = properties.get("FloorMaterial"), footstep = properties.get("FootstepMaterial");
            float floor = Ground.of(floorMaterial, footstep, true), other = Ground.of(floorMaterial, footstep, false);
            asFloor = floor != 0f ? floor : materialOf(name, true);
            asOther = other != 0f ? other : materialOf(sprite);
            laidWall = Masonry.suits(name);
            gableSheet = laidWall && name.startsWith("walls_exterior_roofs");
            lies = Flat.lies(properties.get("RenderLayer"), properties.get("MoveType"));
            wire = name != null && name.startsWith("appliances_com");
            roofAccent = name != null && name.startsWith("roofs_accents");
            roofSheet = name != null && name.startsWith("roofs_");
            stripLamp = sprite.getSpriteGrid() != null && properties.has("IsHigh") && "Lamp".equals(properties.get("CustomName"))
                    && !properties.has(IsoFlagType.attachedN) && !properties.has(IsoFlagType.attachedW) && !properties.has(IsoFlagType.attachedE)
                    && !properties.has(IsoFlagType.attachedS);
            garageDoor = properties.has("GarageDoor");
            cornerTrim = (properties.has("attachedNW") || properties.has("attachedSE")) && !Roofs.isTile(type) && !roofSheet;
            papered = name != null && name.startsWith("walls_interior_house");
            fenceGate = name != null && name.startsWith("fixtures_doors_fences");
            boulder = name != null && name.startsWith("boulders_");
            bedding = name != null && name.startsWith("furniture_bedding");
            longGrass = name != null && name.startsWith("e_newgrass");
            natural = name != null && name.startsWith("blends_natural");
            carpet = name != null && name.startsWith("floors_interior_carpet");
            tiled = name != null && name.startsWith("floors_") && name.contains("tile");
            grows = properties.has(IsoFlagType.vegitation) || properties.has(IsoFlagType.canBeRemoved) || properties.has("MoveWithWind");
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
            flatWall = WallObjects.flatOn(properties, sprite.getFacing());
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
    static final int WOOD_SPAN = CHUNK_TILES + 2 * Flora.WOOD_RING;
    /** How far from the eye each thing Flora makes is still made, in tiles. Past TRUNK_NEAR a bole is a plain post, and past ROCK_NEAR a boulder has a quarter of its faces. */
    static final float FERN_REACH = 22f, FERN_NEAR = 12f, FALLEN_REACH = 10f, FLOWER_REACH = 14f, TRUNK_NEAR = 18f, TRUNK_REACH = 60f,
            ROCK_NEAR = 30f, BUSH_REACH = 40f;
    /** How far in from the edge of a lawn its grass takes to reach its full height, in tiles. */
    private static final float LAWN_EDGE = 0.9f;
    /** What was read off each tree's sprite: how thick its bole is and how tall, in tiles. Empty for a sprite with no bole to read. */
    static final IdentityHashMap<IsoSprite, float[]> boles = new IdentityHashMap<>();
    static final float[] NO_BOLE = new float[0];
    /** How far down the wall of a quay goes from its top: past the water's surface. */
    private static final float QUAY_DEPTH = 0.6f;
    /** How far a layer of cards stands from the one under it besides what any layer does, in tiles: leaves on the card of their trunk. */
    static final float CARD_LAYER = 0.035f;
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
    private long tollQuiet, tollPulls, tollPulled, tollAgedDiffered, tollAgedSame, tollAgedSamePlain, tollRestless;
    /** Dev only: the chunks built ahead of the view's turning to them since the last asking, and the time that took. */
    private long tollAhead, tollAheadNanos;
    /** Dev only: see SightTrace. */
    private SightTrace sightTrace;
    /** The rooms and their openings round the eye, and the walk through them: see RoomSight. Logic thread. */
    final RoomMap roomMap = new RoomMap();
    private final RoomSight roomWalk = new RoomSight();
    /**
     * The rooms whose contents builds hold, and whether builds go by them: they do while the
     * eye's own picture is drawn, and then a room no walk round the eye has found for a few
     * seconds stands empty. Otherwise, from elsewhere and as the game's own view, they go by
     * Sightline as before. Logic thread.
     */
    private final RoomSight.Kept roomsBuilt = new RoomSight.Kept();
    private final RoomSight.Seen roomsFound = new RoomSight.Seen(), roomsEntered = new RoomSight.Seen(), roomsLeft = new RoomSight.Seen();
    private boolean buildingBySight;
    /** Where the eye was at the last walk round it and when, and how fast it has been moving, tiles a second. */
    private float roundEyeX, roundEyeY, eyeSpeed;
    private long roundNanos;
    /**
     * How long the rooms a walk round the eye found stand for it, unless the eye has gone half
     * the way an opening is taken to reach past its edges: a door opened far off by someone
     * else has its room built in that time.
     */
    private static final long ROUND_EVERY_NANOS = 250_000_000L;
    /** Dev only, for the rig: the time the walks took and how many there were, and what the last one had. */
    private long tollRoomsNanos, tollRoomsWalks;
    private int lastOpenings, lastBlocks, lastSeen, lastHidden;
    /** How far past its edges an opening is taken to reach when what is built is worked out, in tiles: a step's worth at a walk, and what the eye covers in GROW_SECONDS. */
    private static final float GROW_TILES = 1.5f, GROW_SECONDS = 0.4f;
    /** A shut door or a drawn curtain this near the eye, in tiles, counts as open when what is built is worked out: it may be opened before its room could be built. */
    private static final float SHUT_WITHIN = 10f;

    /** Whether builds hold what stands in a room: always, but while they go by sight, only in a room a walk round the eye has found lately. */
    boolean builtBySight(long room) {
        return !buildingBySight || roomsBuilt.contains(room);
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
    /** Dev only, for the rig: what building has come to since tolls was last asked. */
    private final int[] tollBuilt = new int[7];
    private final long[] tollBuiltNanos = new long[7];
    private long tollMovedBand, tollMovedUnsigned, tollRebanded;
    private long tollCrownsWaitNanos, tollCrownsLongestNanos;
    private long tollCrownsKept, tollCrownsHere, tollCrownsHereNanos, tollCrownsThere, tollCrownsUnwanted;
    private long tollFrames, tollSums, tollSumNanos, tollBuildNanos, tollGrown, tollGrownNanos, tollChecked, tollDiffered;
    private long tollSumsByLevel, tollLevelsRead, tollLevelsKept, tollSumsChecked, tollSumsDiffered, tollSumsLate;

    /** Dev only: the builds, the sums and the time they took since the last asking, for a rig run's window. */
    String tolls() {
        String told = String.format(Locale.ROOT, "frames %d sums %d in %.1f ms, builds %d in %.1f ms (new %d moved %d changed %d aged %d unsigned %d unread %d), grown %d in %.1f ms, checked %d differed %d",
                tollFrames, tollSums, tollSumNanos / 1e6, tollBuilt[NEW] + tollBuilt[MOVED] + tollBuilt[CHANGED] + tollBuilt[AGED] + tollBuilt[UNSIGNED] + tollBuilt[LEFT_UNREAD],
                tollBuildNanos / 1e6, tollBuilt[NEW], tollBuilt[MOVED], tollBuilt[CHANGED], tollBuilt[AGED], tollBuilt[UNSIGNED], tollBuilt[LEFT_UNREAD],
                tollGrown, tollGrownNanos / 1e6, tollChecked, tollDiffered);
        told += String.format(Locale.ROOT, ", telling %b quiet %d, lights asked %d taken %d", ChunkMarks.live, tollQuiet, tollPulls, tollPulled);
        told += String.format(Locale.ROOT, ", built ahead of a turn %d in %.1f ms", tollAhead, tollAheadNanos / 1e6);
        told += String.format(Locale.ROOT, ", rooms walked %d times in %.2f ms each (chunks read for openings %d; last: openings %d, blocks %d, rooms seen %d, built %d, openings hidden by blocks %d)",
                tollRoomsWalks, tollRoomsNanos / 1e6 / Math.max(1, tollRoomsWalks), roomMap.chunksRead, lastOpenings, lastBlocks, lastSeen, roomsBuilt.size(), lastHidden);
        tollRoomsWalks = tollRoomsNanos = 0;
        roomMap.chunksRead = 0;
        tollAhead = tollAheadNanos = 0;
        told += String.format(Locale.ROOT, ", sums by level %d (levels read %d, kept %d, checked against whole sums %d, levels marked by that reading %d, levels otherwise with nothing marked %d)",
                tollSumsByLevel, tollLevelsRead, tollLevelsKept, tollSumsChecked, tollSumsLate, tollSumsDiffered);
        tollSumsByLevel = tollLevelsRead = tollLevelsKept = tollSumsChecked = tollSumsDiffered = tollSumsLate = 0;
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
                + " kept across a band " + tollRebanded;
        told += String.format(Locale.ROOT, " | crowns kept %d, made here %d in %.1f ms, on their thread %d, unwanted %d, back after %.1f ms each and at most %.1f", tollCrownsKept, tollCrownsHere,
                tollCrownsHereNanos / 1e6, tollCrownsThere, tollCrownsUnwanted, tollCrownsWaitNanos / 1e6 / Math.max(1, tollCrownsThere), tollCrownsLongestNanos / 1e6);
        tollCrownsKept = tollCrownsHere = tollCrownsHereNanos = tollCrownsThere = tollCrownsUnwanted = tollCrownsWaitNanos = tollCrownsLongestNanos = 0;
        Arrays.fill(tollBuiltNanos, 0);
        tollMovedBand = tollMovedUnsigned = tollRebanded = 0;
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
            scene.viewer = null;
            scene.cell = null;
            scene.here = null;
            Arrays.fill(scene.groundObject, null);
            Arrays.fill(scene.lightSquare, null);
            Arrays.fill(scene.around, null);
            scene.lampsNear.clear();
            scene.keptFrame = -1;
        }
        chunkMap = null;
        builtAt[view.index] = 0L;
        if (building(System.nanoTime()) == 0) {
            // Nothing is built or read while every view is off, so nothing would come to let these go.
            VertexLoans.letGoIdle();
            SpriteColours.letGoOfThePage();
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
                of[at++] = (long) set << 62 | (long) mesh.texture << 32 | mesh.tint & 0xffffffffL;
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
            made.add((int) Math.floor((mesh.vertices[k + 8] + 0.5f) / 32f));
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
        return ChunkBuffers.kept(meshes.wx, meshes.wy, mapWx, mapWy, mapSide, keptRoundX, keptRoundY, Quality.sight());
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
    /** The height of the hills the far land was last asked for at: see HillHeight. Logic thread. */
    private float farHills = HillHeight.DEFAULT;

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

    private static final long SUM_FROM = 0x9E3779B97F4A7C15L;

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
    /** A pane of a window the picture has built, and one with a lamp burning in the room behind it. */
    static final float PANE = 67f, LIT_PANE = 68f;
    /** A wall of upright boards. */
    static final float BOARDS = 52f;

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

    /**
     * Whether everything built of an object that is no part of its building stays inside the
     * room it stands in, so that it can be left out of a picture that does not see into the
     * room. What hangs on a wall may hang on its outer face, and a curtain or a barricade on a
     * window is seen through the glass from outside however the room is shut off.
     */
    static boolean withinItsRoom(IsoObject object, IsoSprite sprite) {
        if (partOfTheBuilding(object, sprite) || object instanceof IsoCurtain || object instanceof IsoBarricade) {
            return false;
        }
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
        if (object instanceof IsoDoor || object instanceof IsoWindow || object instanceof IsoThumpable thumpable && thumpable.isDoor()) {
            return true;
        }
        IsoObjectType type = sprite.getType();
        if (Roofs.isTile(type) || isStair(type)) {
            return true;
        }
        PropertyContainer flags = sprite.getProperties();
        return flags.has(IsoFlagType.WallN) || flags.has(IsoFlagType.WallW) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.WallSE)
                || flags.has(IsoFlagType.WallNTrans) || flags.has(IsoFlagType.WallWTrans) || flags.has(IsoFlagType.DoorWallN)
                || flags.has(IsoFlagType.DoorWallW) || flags.has(IsoFlagType.WindowN) || flags.has(IsoFlagType.WindowW)
                || flags.has(IsoFlagType.solidfloor) || flags.has(IsoFlagType.FloorOverlay) || flags.has(IsoFlagType.attachedFloor);
    }

    /** Where each plant's leaves are painted on its texture: see Flora.leafPlaces. */
    static final IdentityHashMap<Texture, float[]> leafPlaces = new IdentityHashMap<>();

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

    private static boolean isStair(IsoObjectType type) {
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
        for (int i = 0; i < room.roomLights.size(); i++) {
            if (room.roomLights.get(i).active) {
                return true;
            }
        }
        for (int i = 0; i < lamps.size(); i++) {
            IsoLightSource lamp = lamps.get(i);
            if (!lamp.active || lamp.z != square.getZ() || Math.abs(lamp.x - square.getX()) > lamp.radius || Math.abs(lamp.y - square.getY()) > lamp.radius) {
                continue;
            }
            IsoGridSquare stands = cell.getGridSquare(lamp.x, lamp.y, lamp.z);
            if (stands != null && stands.getRoomID() == square.getRoomID()) {
                return true;
            }
        }
        return false;
    }

    /** How bright the game's light on a square has to be for a lamp burning in its room to count as reaching it. */
    private static final float LAMPLIT = 0.55f;
    /** What a room with a lamp burning is lit by before any lamplit room has been in sight: just what counts as lamplit. */
    private static final float LAMP_AT_LEAST = LAMPLIT + 0.05f;

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
                    || (north ? standsOnNorthEdge(properties, type) : standsOnWestEdge(properties, type)))) {
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

    /** Whether what has these flags is a wall, a fence, a door's frame or a window on the north edge of its square. */
    static boolean onNorthEdge(PropertyContainer flags) {
        return flags.has(IsoFlagType.WallN) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.WallNTrans)
                || flags.has(IsoFlagType.DoorWallN) || flags.has(IsoFlagType.WindowN) || flags.has(IsoFlagType.HoppableN);
    }

    static boolean onWestEdge(PropertyContainer flags) {
        return flags.has(IsoFlagType.WallW) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.WallWTrans)
                || flags.has(IsoFlagType.DoorWallW) || flags.has(IsoFlagType.WindowW) || flags.has(IsoFlagType.HoppableW);
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

    /** With tracing on, the memory the render thread took for the picture since it was last asked. */
    private final AtomicLong drawnBytes = new AtomicLong();

    long takeDrawnBytes() {
        return drawnBytes.getAndSet(0);
    }

    /** One frame of the picture: what of the world this view shows in it, built on the logic thread and drawn on the picture's. */
    final class Frame extends TextureDraw.GenericDrawer implements Grass.Ground {
        final FloorBuilder floorBuilder = new FloorBuilder(WorldScene.this, this);
        private final KerbBuilder kerbBuilder = new KerbBuilder(this);
        private final PlantBuilder plantBuilder = new PlantBuilder(WorldScene.this, this);
        final WallBuilder wallBuilder = new WallBuilder(WorldScene.this, this);
        final OpeningBuilder openingBuilder = new OpeningBuilder(this);
        final PostBuilder postBuilder = new PostBuilder(this);
        private final StairBuilder stairBuilder = new StairBuilder(this);
        private final FurnishingBuilder furnishingBuilder = new FurnishingBuilder(this);
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
        private float outsideTan, outsideAway;
        /** The glass the viewer is shown in, seen from their own eye. */
        private final OwnReflection.Glass glass = new OwnReflection.Glass();
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
        private float fogEnd;
        /** The middle of the map the game has loaded, and how far from it the map reaches every way, in tiles. */
        private float loadedX, loadedY, loadedWithin;
        /** How far from the eye people, zombies, vehicles and furniture are drawn, from the eye's frame: see DrawDistance. */
        private final DrawDistance drawDistance = new DrawDistance();
        /** How much light the eye lets in this frame: see adaptEye. */
        private float exposure;
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
        /** The height of the floor the viewer stands on. */
        private float stoodUp;
        private int cutRowsX, cutRowsY;
        private float cutBelow;
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
        final ColorInfo unseenLight = new ColorInfo();
        /** Where TorchLess writes a square's light when the viewer's torch is on the square. */
        final ColorInfo torchless = new ColorInfo();
        final float[] box = new float[4];
        int overLayer;
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
        /** Which squares of the chunk being built, and of Flora.WOOD_RING more on every side, hold a tree. */
        final boolean[] treed = new boolean[WOOD_SPAN * WOOD_SPAN];
        /** How deep in fallen leaves each corner of the chunk being built is: see Flora.wooded. */
        final float[] wooded = new float[Terrain.CORNERS * Terrain.CORNERS];
        /** How many of its neighbours' grounds spreadGround last laid over a square. */
        int spreadLayers;
        /** Whether the square being added has no floor. */
        boolean hangsInAir;
        /** The square whose grass is being grown: where in the chunk it is, which of its sides run on into more lawn, and how much standing under trees takes from its grass. */
        int grownLocalX, grownLocalY;
        boolean lawnWest = true, lawnEast = true, lawnNorth = true, lawnSouth = true;
        float grownUnder;
        /** The ground of the chunk being built and of the ring of squares round it, laid out as the lights are. */
        final byte[] groundKind = new byte[LIGHT_SPAN * LIGHT_SPAN];
        final IsoObject[] groundObject = new IsoObject[LIGHT_SPAN * LIGHT_SPAN];
        final Texture[] groundTexture = new Texture[LIGHT_SPAN * LIGHT_SPAN];
        final int[] groundRank = new int[LIGHT_SPAN * LIGHT_SPAN];
        final float[] cover = new float[4];
        /** The place in its chunk of the square being added. */
        int waterLocalX, waterLocalY;
        final float[] lightRed = new float[LIGHT_SPAN * LIGHT_SPAN], lightGreen = new float[LIGHT_SPAN * LIGHT_SPAN],
                lightBlue = new float[LIGHT_SPAN * LIGHT_SPAN];
        final byte[] lightOpen = new byte[LIGHT_SPAN * LIGHT_SPAN];
        final float[] heldRed = new float[4], heldGreen = new float[4], heldBlue = new float[4];
        /**
         * The lights of every level of the chunk last summed, as readLights left each: a chunk
         * found changed is built in the same frame, and its build takes them from here in place
         * of asking the game for every square a second time. The ground and the woods are of
         * level 0 alone and stay where they were read into.
         */
        private float[] keptRed = new float[0], keptGreen = new float[0], keptBlue = new float[0];
        private byte[] keptOpen = new byte[0];
        private int keptWx, keptWy, keptFrom, keptTo, keptFrame = -1;
        /** Whether any square of the chunk last read through, on any level, has a canopy's floor over it. */
        boolean canopied;
        /** The squares readLights last read, laid out as the lights are. */
        private final IsoGridSquare[] lightSquare = new IsoGridSquare[LIGHT_SPAN * LIGHT_SPAN];
        /** The chunk being read and the eight round it, by column then row, null where the map has none loaded. */
        private final IsoChunk[] around = new IsoChunk[9];
        private long auditTerrain, auditTrees;
        private long[] auditLights = new long[0], auditThings = new long[0];
        /** What every build goes by that is not of its chunk, as one number: see build. */
        private long epoch;
        /** Set by the last reading of a chunk: see ChunkMeshes.restless. */
        private boolean restless;
        /** Which of the chunk last looked round and the eight round it are loaded, a bit each: see surround. */
        private int ring;
        /** The light of the seen squares out of doors of the chunk being read. */
        private final OpenLight reading = new OpenLight();
        /** And of its room squares in the character's sight: see seenRooms. */
        private final OpenLight readingRooms = new OpenLight(), readingLamplit = new OpenLight();
        final ColorInfo roomLight = new ColorInfo(), lampLight = new ColorInfo();
        /** Whether what stands in a room the game has not shown is left out of this frame's builds: see Sightline. */
        boolean asTheGameShows;
        private boolean tallying;
        private int chunksQuiet;

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
            boolean taken = chunk.lightingUpdateCounter != litByGame[slot];
            if (pass == litAt[slot] && !taken) {
                return;
            }
            litAt[slot] = pass;
            litByGame[slot] = chunk.lightingUpdateCounter;
            FBORenderLevels levels = chunk.getRenderLevels(playerIndex);
            boolean gameAsks = !view.firstPerson.coversTheGamesView();
            for (int z = Math.max(lowest, chunk.getMinLevel()); z <= chunk.getMaxLevel(); z++) {
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
                            + " exposure %.2f tan half fov %.4f aspect %.4f fog end %.1f roofs n-s %d w-e %d flat %d unclear %d boxes %d furniture %d spread %d lifted %d models %d | %s",
                    chunksDrawn, chunksWaiting, chunksBuilt, builtBecause[NEW], builtBecause[MOVED], builtBecause[CHANGED], builtBecause[AGED],
                    builtBecause[UNSIGNED], builtBecause[LEFT_UNREAD], chunksGrown, chunksKept, chunksQuiet, squaresLoaded, squaresSeen, sprites, triangles, buildMillis, lastDrawMillis, exposure,
                    tanHalfFieldOfView, drawnAspect, fogEnd, roofsNorthSouth, roofsWestEast, roofsFlat, roofsUnclear, boxes, furnished, spreadFaces, lifted, view.characters.drawnLast(), sky.describe()) + " | " + view.pipeline.describeShadows() + " | " + ChunkBuffers.describe()
                    + String.format(Locale.ROOT, ", builds are filled from %d meshes with room for %.0f MB", solid.kept() + laid.kept(), (solid.room() + laid.room()) * Float.BYTES / 1048576f)
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
         * @param tanHalfFieldOfView the tangent of half the vertical field of view the frame is drawn with
         * @param tanHalfSet         the tangent of half the vertical field of view the player set, which
         *                           the first is narrower than while a firearm is aimed
         * @param from               the camera the frame is drawn by when that is somewhere else, or null: see seenFrom
         * @param opened             the frame is the game's own view: what the game leaves out of that view is not built, and the walls it cuts away are cut
         */
        void build(IsoPlayer player, ViewCamera.Pose pose, ViewCamera.Pose aimed, float tanHalfFieldOfView, float tanHalfSet,
                ViewCamera.Pose from, boolean opened) {
            long start = System.nanoTime();
            Stall.beat();
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
            outside = false;
            opacity = 1f;
            seated = view.ride.seated;
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
            if (fromOutside) {
                sky.daylightKept = Evening.seenFrom(sky.daylightKept, view.flight.share());
            }
            openNow = gameOpen;
            daylightKeptNow = sky.daylightKept;
            // The beam of this picture's own view: the one being gathered now may be a turn of the head later.
            sky.carried = view.carriedLight.light;
            sky.marked = view.centreUse.marked;
            sky.facedX = facedX;
            sky.facedY = facedY;
            sky.layersFromX = pose.eyeX;
            sky.layersFromY = pose.eyeY;
            sky.layersFromUp = pose.eyeUp;
            sky.acrossX = rightX;
            sky.acrossY = rightY;
            exposure = adaptEye(player, fromOutside);
            playerIndex = player.getIndex();
            cell = IsoWorld.instance.currentCell;
            roofBuilder.newBuild();
            if (Own.taken(view.index)) {
                // A row of [Content] was switched: every build holds what the rows were, and so does every roof read and every square's ground, which leave a parasol out by its row.
                forgetAll();
                Roofs.forget();
                Terrain.reread();
            }
            if (crownedToSight != Quality.crownsToSight()) {
                // Every build in the last band holds whether its trees were given crowns and boles.
                crownedToSight = Quality.crownsToSight();
                forgetAll();
            }
            if (cell != builtIn.get()) {
                // Another save, loaded without leaving the game: every build is of the world before.
                builtIn = new WeakReference<>(cell);
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
                ranks.clear();
                leafPlaces.clear();
                doorSpans.clear();
                wires.clear();
                Furniture.forget();
                Parasols.forget();
                Awnings.forget();
                Tents.forget();
                Hoods.forget();
                Towers.forget();
                Lights.forget();
                Roofs.forget();
                WallFinish.forget();
                Country.read(IsoWorld.instance.getMetaGrid());
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
            long goesBy = sum(sum(sum(SUM_FROM, Float.floatToRawIntBits(unseenRed)), Float.floatToRawIntBits(unseenGreen)), Float.floatToRawIntBits(unseenBlue));
            goesBy = sum(sum(goesBy, mainsOn ? 1 : 0), Float.floatToRawIntBits(Core.tileScale));
            boolean showing = Sightline.asTheGameShows(withoutSightline);
            if (showing != asTheGameShows) {
                // A build made by the other rule, seen before it falls due, would fill or empty its rooms a frame or more late.
                asTheGameShows = showing;
                forgetAll();
            }
            // No build reads the rooms' light where a room is shown only as the game shows it.
            goesBy = sum(sum(sum(goesBy, asTheGameShows ? 1 : 0), asTheGameShows ? 0 : Float.floatToRawIntBits(roomShare)), asTheGameShows ? 0 : Float.floatToRawIntBits(lampLevel));
            glass.found = false;
            if (!fromOutside && !seated && drawnAspect > 0f && Quality.surfacesMirror()) {
                OwnReflection.find(cell, player, terrain, pose, tanHalfFieldOfView * drawnAspect, glass);
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
                for (int row = 0; row < Cutaways.SIDE; row++) {
                    for (int column = 0; column < Cutaways.SIDE; column++) {
                        IsoGridSquare square = cell.getGridSquare(cutRowsX + column, cutRowsY + row, level);
                        int marks = square == null ? 0 : square.getPlayerCutawayFlag(playerIndex, now);
                        if (marks != 0) {
                            Cutaways.put(cutRows, column, row, marks);
                        }
                    }
                }
            }
            lidFrom = indoors && !opened ? floorUp + ViewCamera.LEVEL_HEIGHT - LID_UNDER : Float.MAX_VALUE;
            boxMinX = minX;
            boxMinY = minY;
            boxMaxX = maxX;
            boxMaxY = maxY;
            frame.scale = Core.tileScale;
            unseenLight.r = unseenRed;
            unseenLight.g = unseenGreen;
            unseenLight.b = unseenBlue;
            // Until a room has been in sight, a room is lit as the open ground is, and one with a lamp burning at least as a lamp lights.
            float share = roomShare < 0f ? 1f : roomShare;
            roomLight.r = Math.min(unseenRed * share, 1f);
            roomLight.g = Math.min(unseenGreen * share, 1f);
            roomLight.b = Math.min(unseenBlue * share, 1f);
            float lamp = lampLevel < 0f ? LAMP_AT_LEAST : lampLevel;
            lampLight.r = Math.max(roomLight.r, lamp);
            lampLight.g = Math.max(roomLight.g, lamp);
            lampLight.b = Math.max(roomLight.b, lamp);

            // The loaded map is a square of chunks, and it ends in nothing. The picture fades into the
            // sky by the round that just fits inside it, so the edge is never seen. The square is not
            // always about the player: the game keeps it ahead of a vehicle, a tile for every 5 km/h,
            // and the road ahead of a car at speed is there to be seen for further than what is behind.
            loadedWithin = IsoChunkMap.chunkGridWidth * CHUNK_TILES / 2f;
            loadedX = map.getWorldXMinTiles() + loadedWithin;
            loadedY = map.getWorldYMinTiles() + loadedWithin;
            fogEnd = Quality.sight();
            drawDistance.take(pose.eyeX, pose.eyeY, seated ? view.ride.aheadX : 0f, seated ? view.ride.aheadY : 0f, Online.contested());
            if (!fromOutside) {
                farLand.tend(pose.eyeX, pose.eyeY);
            }
            float cosCone = fromOutside ? -1f : cosDrawnCone(pose, flat);
            // A driver's mirror shows a narrow cone straight behind the vehicle, and what is in it is drawn as well.
            boolean mirrored = !fromOutside && seated && Quality.reflections() && Cabins.wheel(view.ride) != null;
            float behindX = -view.ride.aheadX, behindY = -view.ride.aheadY;
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
            int builtLate = 0;
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
                if (ahead && (aheadStep < 0 || !Sightline.buildsAhead(withoutSightline) || rehearsing || chunksWaiting > 0 || waitedLast > 0
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
                            && current.epoch == epoch && current.gamesView == gamesView && land == current.land && ringAsItWas && settled;
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
                    // One that has waited twice its time waits no longer: the chunks are walked nearest first,
                    // and the last of them would wait as long as those before it kept falling due.
                    boolean due = landChanged && distance < BUILT_WHATEVER_IT_TAKES
                            || !missing && !quiet && !rehearsing && waited && frames - current.checkedFrame >= every
                            && (distance < CHUNK_TILES * 1.5f || frames - current.checkedFrame >= 2 * every && !hurried
                                    || frames - current.checkedFrame >= 4 * every && !late || !hurried && remakes++ < REMAKES_A_FRAME);
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
                    if (due && !landChanged && telling && stands && current.signed && current.things == things && since < LIT_WAITS_NANOS
                            && System.nanoTime() - start > LIT_NANOS_A_FRAME / sharing) {
                        due = false;
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
                    boolean summed = (missing || due) && frames >= summedFrom && start >= summedFromNanos;
                    if (missing || due) {
                        surround(map, chunk, gridX, gridY, side);
                    }
                    if (summed) {
                        long summing = System.nanoTime();
                        // Read for nothing but light the game marked, which it marks by its level: the other levels are as they were summed.
                        boolean lightAlone = telling && !missing && !AUDIT_MARKS && stands && current.signed && !current.restless && settled && ringAsItWas
                                && marks != current.marks && current.things == things && current.told == toldSoFar && current.epoch == epoch && current.land == land
                                && current.litLoose == ChunkMarks.litLooseAround(chunk.wx, chunk.wy) && start - current.wholeNanos < sweptEvery(distance, slot);
                        read = signature(chunk, chunkX, chunkY, lowest, lightAlone ? current : null);
                        if (CHECK_BUILDS && readByLevel) {
                            long[] byLevel = Arrays.copyOf(readSums, readTo - readFrom + 1);
                            read = signature(chunk, chunkX, chunkY, lowest, null);
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
                        boolean same = !missing && current.signature == read && current.told == toldSoFar && current.epoch == epoch && current.land == land;
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
                            builtBecause[cause]++;
                        }
                        if (sameEye && same && young) {
                            current.checkedFrame = frames;
                            current.checkedNanos = start;
                            current.marks = marks;
                            current.things = things;
                            current.keepLevels(this, start, current);
                            current.land = land;
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
                            }
                            fresh.traced.clear();
                        }
                        fresh.wx = chunk.wx;
                        fresh.wy = chunk.wy;
                        fresh.builtFrame = frames;
                        fresh.checkedFrame = frames;
                        fresh.checkedNanos = start;
                        fresh.builtNanos = start;
                        fresh.bornNanos = missing ? start : current.bornNanos;
                        fresh.signed = summed;
                        fresh.signature = read;
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
                    if (Own.plants() && current.grows && (ungrown || regrown && !late)) {
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
            addCharacters(pose);
            takeLight(start);
            for (int i = 0; i < shown.size(); i++) {
                triangles += shown.get(i).triangles() + shown.get(i).placed.triangles();
            }
            waitedLast = chunksWaiting;
            if (SightTrace.ON && !fromOutside) {
                if (sightTrace == null) {
                    sightTrace = new SightTrace(WorldScene.this);
                }
                sightTrace.frame(cell, player, pose, forwardX, forwardY, cosCone, frames, chunksWaiting, nearestWaiting, asTheGameShows, roomsSeen);
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
        private final FigureRooms figureRoomsKept = new FigureRooms();
        private final RoomSight.Seen roomsSeenKept = new RoomSight.Seen();
        private final RoomSight.Openings openingsInReach = new RoomSight.Openings();
        private final RoomSight.Blocks blocksInReach = new RoomSight.Blocks();
        /** What the openings in reach were last gathered from: see seeRooms. */
        private long openingsReach;

        /**
         * Works out which rooms the eye sees into, from the openings of every chunk the picture
         * can reach, and which rooms it could see into soon, whose contents are built: see
         * RoomSight. A build of a chunk with a room that came into those or went out of them is
         * made again.
         */
        private void seeRooms(IsoChunkMap map, int level, boolean fromOutside, long now) {
            roomsSeen = null;
            figureRooms = null;
            float aspect = drawnAspect;
            if (fromOutside || gamesView || aspect <= 0f || withoutCulling) {
                if (buildingBySight) {
                    buildingBySight = false;
                    roomsBuilt.clear();
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
            roomsSeenKept.clear();
            roomWalk.walk(roomMap, openingsInReach, blocksInReach, camera, tanHalfFieldOfView * aspect, tanHalfFieldOfView, roomMap.region(eyeX, eyeY, level),
                    roomMap.building(eyeX, eyeY, level), roomsSeenKept);
            roomsSeen = roomsSeenKept;
            roomsAspect = aspect;
            lastHidden = roomWalk.hiddenByBlocks;
            float seconds = (now - roundNanos) / 1e9f;
            float moved = (float) Math.hypot(camera.eyeX - roundEyeX, camera.eyeY - roundEyeY);
            float grow = GROW_TILES + eyeSpeed * GROW_SECONDS;
            roomsEntered.clear();
            roomsLeft.clear();
            if (roundNanos == 0L || now - roundNanos >= ROUND_EVERY_NANOS || moved > grow / 2f) {
                if (roundNanos != 0L && seconds > 0f) {
                    // A jump across the map is no speed: the walk round the new place starts it again.
                    float speed = moved / seconds;
                    eyeSpeed = speed > 100f ? 0f : eyeSpeed + (speed - eyeSpeed) * Math.min(1f, seconds * 4f);
                }
                roundEyeX = camera.eyeX;
                roundEyeY = camera.eyeY;
                roundNanos = now;
                roomsFound.clear();
                roomWalk.walkAllRound(roomMap, openingsInReach, blocksInReach, camera.eyeX, camera.eyeY, camera.eyeUp, roomMap.region(eyeX, eyeY, level),
                        roomMap.building(eyeX, eyeY, level), GROW_TILES + eyeSpeed * GROW_SECONDS, SHUT_WITHIN, roomsFound);
                roomsBuilt.found(roomsFound, now, roomsEntered);
                roomsBuilt.expire(now, roomsLeft);
            }
            // What the picture draws is always built, whatever the walk round found.
            roomsBuilt.found(roomsSeenKept, now, roomsEntered);
            if (FigureRooms.applies(Online.contested())) {
                figureRoomsKept.fill(roomsBuilt, openingsInReach);
                figureRooms = figureRoomsKept;
            }
            tollRoomsNanos += System.nanoTime() - began;
            tollRoomsWalks++;
            lastOpenings = openingsInReach.count;
            lastBlocks = blocksInReach.count;
            lastSeen = roomsSeenKept.size();
            if (!buildingBySight) {
                buildingBySight = true;
                tellEveryChunk(map);
            } else if (roomsEntered.size() > 0 || roomsLeft.size() > 0) {
                for (int gridX = 0; gridX < side; gridX++) {
                    for (int gridY = 0; gridY < side; gridY++) {
                        IsoChunk chunk = map.getChunk(gridX, gridY);
                        if (chunk != null && (roomMap.holdsAny(chunk, roomsEntered) || roomMap.holdsAny(chunk, roomsLeft))) {
                            ChunkMarks.tell(chunk.wx, chunk.wy);
                        }
                    }
                }
            }
        }

        /** A loaded chunk of the map whose middle is within reach of the eye, or null. */
        private IsoChunk reachable(IsoChunkMap map, int gridX, int gridY, float reach) {
            IsoChunk chunk = map.getChunk(gridX, gridY);
            return chunk == null || !chunk.loaded
                    || Math.hypot(chunk.wx * CHUNK_TILES + CHUNK_TILES / 2f - camera.eyeX, chunk.wy * CHUNK_TILES + CHUNK_TILES / 2f - camera.eyeY) > reach ? null : chunk;
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
         */
        private void addCharacters(ViewCamera.Pose pose) {
            for (IsoMovingObject mover : cell.getObjectList()) {
                if (mover instanceof IsoGameCharacter character && character != viewer
                        && character.getAlpha(playerIndex) > CHARACTER_SHOWN_ALPHA && !character.isInvisible()) {
                    float toX = character.getX() - pose.eyeX, toY = character.getY() - pose.eyeY;
                    if (toX * toX + toY * toY <= REACH * REACH) {
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
                    roomShare = Sightline.stepped(Math.min((openLight[0] + openLight[1] + openLight[2]) / 3f / open, 1f), roomShare);
                }
                if (seenLamplit.counted() >= ROOM_LIGHT_FROM) {
                    seenLamplit.commonest(openLight);
                    lampLevel = Sightline.stepped(Math.max(openLight[0], Math.max(openLight[1], openLight[2])), lampLevel);
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

        /**
         * The square at (x, y, z), which is on the chunk at (chunkX, chunkY) or within a chunk of
         * it: taken from the chunk it is on, which the cell would have to find among the map's
         * chunks at every asking.
         */
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
            long sum = signature(chunk, chunkX, chunkY, lowest, null);
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
            tallying = !kept && !growing && !afresh;
            if (tallying) {
                reading.clear();
                readingRooms.clear();
                readingLamplit.clear();
            }
            if (!kept) {
                lampsRound(chunkX, chunkY);
            }
            for (int z = Math.max(lowest, chunk.getMinLevel()); z <= (growing ? 0 : chunk.getMaxLevel()); z++) {
                if (kept && z >= keptFrom && z <= keptTo) {
                    int at = (z - keptFrom) * lightRed.length;
                    System.arraycopy(keptRed, at, lightRed, 0, lightRed.length);
                    System.arraycopy(keptGreen, at, lightGreen, 0, lightRed.length);
                    System.arraycopy(keptBlue, at, lightBlue, 0, lightRed.length);
                    System.arraycopy(keptOpen, at, lightOpen, 0, lightRed.length);
                } else {
                    readLights(chunkX, chunkY, z);
                    if (z == 0) {
                        readWoods(chunkX, chunkY);
                    }
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
                            grow(chunkX + localX, chunkY + localY, localX, localY);
                        } else {
                            addSquare(square, chunkX + localX, chunkY + localY, z, localX, localY);
                        }
                    }
                }
            }
        }
        /** The bands the chunk being built would come out the same for, a bit each. */
        int alike;

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
        private void grow(int x, int y, int localX, int localY) {
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
            if (toX * toX + toY * toY < GRASS_REACH * GRASS_REACH) {
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
         * sum the build has for it and is not read. What is then kept of the reading is not whole,
         * and a build that follows reads the chunk's light itself.
         *
         * @param readFor a build of the chunk to take the sums of its unmarked levels from, or null to read every level
         */
        private long signature(IsoChunk chunk, int chunkX, int chunkY, int lowest, ChunkMeshes readFor) {
            int from = Math.max(lowest, chunk.getMinLevel()), to = chunk.getMaxLevel();
            readUnseen = sum(sum(sum(sum(SUM_FROM, Float.floatToRawIntBits(unseenLight.r)), Float.floatToRawIntBits(unseenLight.g)), Float.floatToRawIntBits(unseenLight.b)), gamesView ? 1 : 0);
            readUnseen = sum(sum(sum(sum(readUnseen, asTheGameShows ? 1 : 0), Float.floatToRawIntBits(roomLight.r)), Float.floatToRawIntBits(roomLight.g)), Float.floatToRawIntBits(roomLight.b));
            readUnseen = sum(sum(sum(readUnseen, Float.floatToRawIntBits(lampLight.r)), Float.floatToRawIntBits(lampLight.g)), Float.floatToRawIntBits(lampLight.b));
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
            sum = sum(sum, land);
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
            }
            keptWx = chunk.wx;
            keptWy = chunk.wy;
            keptFrom = from;
            keptTo = to;
            keptFrame = frames;
            canopied = false;
            restless = false;
            tallying = true;
            reading.clear();
            readingRooms.clear();
            readingLamplit.clear();
            lampsRound(chunkX, chunkY);
            for (int z = from; z <= to; z++) {
                if (readByLevel && readLit[z - from] == readFor.litSeen[z - from]) {
                    readSums[z - from] = readFor.levelSums[z - from];
                    tollLevelsKept++;
                    continue;
                }
                tollLevelsRead++;
                long level = SUM_FROM;
                readLights(chunkX, chunkY, z);
                System.arraycopy(lightRed, 0, keptRed, (z - from) * span, span);
                System.arraycopy(lightGreen, 0, keptGreen, (z - from) * span, span);
                System.arraycopy(lightBlue, 0, keptBlue, (z - from) * span, span);
                System.arraycopy(lightOpen, 0, keptOpen, (z - from) * span, span);
                if (z == 0) {
                    readWoods(chunkX, chunkY);
                    long trees = SUM_FROM;
                    for (int i = 0; i < treed.length; i++) {
                        trees = sum(trees, treed[i] ? 1 : 0);
                    }
                    level = sum(level, trees);
                    auditTrees = trees;
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
                        long lit = sum(sum(sum(sum(SUM_FROM, lightOpen[at]), Float.floatToRawIntBits(lightRed[at])), Float.floatToRawIntBits(lightGreen[at])),
                                Float.floatToRawIntBits(lightBlue[at]));
                        long thing = SUM_FROM;
                        if (z == 0) {
                            thing = sum(sum(sum(thing, groundKind[at]), groundRank[at]), groundTexture[at] == null ? 0 : groundTexture[at].getID());
                        }
                        IsoGridSquare square = lightSquare[at];
                        thing = sum(sum(sum(thing, square.getRoomID()), unexplored(square) ? 1 : 0), Math.max(lightRed[at], Math.max(lightGreen[at], lightBlue[at])) > LAMPLIT && lampBurns(cell, square, lampsNear) ? 1 : 0);
                        PZArrayList<IsoObject> objects = square.getObjects();
                        thing = sum(thing, objects.size());
                        for (int k = 0; k < objects.size(); k++) {
                            IsoObject object = objects.get(k);
                            IsoDirections dir = object.getDir();
                            thing = sum(sum(sum(sum(thing, shown(object.getSprite(), dir)), Float.floatToRawIntBits(object.offsetX)), Float.floatToRawIntBits(object.offsetY)),
                                    shown(object.getOverlaySprite(), dir));
                            restless |= moves(object.getSprite()) || moves(object.getOverlaySprite());
                            ArrayList<IsoSpriteInstance> attached = object.getAttachedAnimSprite();
                            if (attached != null) {
                                for (int over = 0; over < attached.size(); over++) {
                                    thing = sum(thing, shown(attached.get(over).parentSprite, dir));
                                    restless |= moves(attached.get(over).parentSprite);
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
                                // The game makes a body's picture when its own view has the body on screen, and marks nothing when it has.
                                restless |= picture == 0 && square.getChunk().getRenderLevels(playerIndex).isOnScreen(z);
                            }
                        }
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
            if (readByLevel) {
                keptFrame = -1;
            }
            if (restless) {
                tollRestless++;
            }
            return sum;
        }
        /** What the last reading of a chunk came to level by level, from readFrom to readTo, with ChunkMarks' counts for the light of each as they were before it, and whether it took any level's sum from a build. */
        private long[] readSums = new long[8];
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
         * change when its owner walks through a door. What it is adapted to is where the character
         * stands, in or out of doors, and how strong the sun or moon is.
         */
        private float adaptEye(IsoPlayer player, boolean fromOutside) {
            IsoGridSquare under = player.getCurrentSquare();
            // Seen from outside the picture is of the open air, whatever roof the viewer is under.
            float target = fromOutside || under == null || under.isOutside() ? 1f : 0f;
            long now = System.nanoTime();
            float elapsed = lastAdapt == 0 ? 1f : Math.min((now - lastAdapt) / 1e9f, 1f);
            lastAdapt = now;
            eyeOpen = eyeOpen < 0f ? target : eyeOpen + (target - eyeOpen) * (1f - (float) Math.exp(-elapsed / EYE_ADAPT_SECONDS));
            sky.outdoors = eyeOpen;
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
            float here = 1f;
            if (under != null) {
                // Without the viewer's own torch. The game has the torch on the square the character
                // has just stepped onto and takes it off again half a second later, so an eye that
                // went by the square's whole light shut and opened once for every tile walked.
                ColorInfo light = view.torchLess.light(under, player.getIndex(), torchless);
                here = (float) Math.pow(Evening.dimmed((light.r + light.g + light.b) / 3f, gameOpen, sky.daylightKept), 2.2);
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
            float indoors = Math.min(EXPOSURE_INDOORS / Math.max(here, 1e-3f), most);
            return indoors + (outdoors - indoors) * eyeOpen;
        }

        /** The lamps that burn near enough to reach a square of a chunk or of the ring of squares round it, into lampsNear. */
        private void lampsRound(int chunkX, int chunkY) {
            lampsNear.clear();
            Stack<IsoLightSource> lamps = cell.getLamppostPositions();
            for (int i = 0; i < lamps.size(); i++) {
                IsoLightSource lamp = lamps.get(i);
                if (lamp.active && lamp.x + lamp.radius >= chunkX - 1 && lamp.x - lamp.radius <= chunkX + CHUNK_TILES && lamp.y + lamp.radius >= chunkY - 1
                        && lamp.y - lamp.radius <= chunkY + CHUNK_TILES) {
                    lampsNear.add(lamp);
                }
            }
        }

        /**
         * The light of every square of one level of a chunk and of the ring of squares round it,
         * into lightRed, lightGreen, lightBlue and lightOpen, a LIGHT_SPAN wide grid whose first
         * row and column are the squares just outside the chunk. lightOpen is 1 out of doors, 0
         * under a roof and -1 where there is no square.
         */
        private void readLights(int chunkX, int chunkY, int z) {
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
                    }
                    if (square == null) {
                        lightOpen[at] = -1;
                        continue;
                    }
                    // Read from the lighting engine, which holds every loaded square. getLightInfo is
                    // a copy the game's own render takes of it, and only for the squares its camera
                    // has on screen.
                    ColorInfo light = view.torchLess.light(square, playerIndex, torchless);
                    boolean outside = square.isOutside();
                    // As the game's own view a room keeps the engine's light whether or not it has
                    // been seen: none in a room the player has not seen into, and less in one
                    // they have seen and cannot see now, which is how the game shows them.
                    boolean seen = square.isSeen(playerIndex);
                    boolean inRoom = square.getRoomID() != -1L;
                    if (!seen && !(gamesView && inRoom)) {
                        // From the eye a room the game has not shown is a room like the ones in sight: see seenRooms.
                        // Shown only as the game shows it, it stands empty and lit as the open ground is.
                        light = !inRoom || asTheGameShows ? unseenLight : lampBurns(cell, square, lampsNear) ? lampLight : roomLight;
                    } else if (seen && inChunk) {
                        squaresSeen++;
                        if (outside && tallying) {
                            reading.add(light.r, light.g, light.b);
                        } else if (inRoom && tallying && !gamesView && square.isCanSee(playerIndex)) {
                            // In sight now: one seen and out of sight has the game's darkening on it.
                            if (Math.max(light.r, Math.max(light.g, light.b)) > LAMPLIT && lampBurns(cell, square, lampsNear)) {
                                readingLamplit.add(light.r, light.g, light.b);
                            } else {
                                readingRooms.add(light.r, light.g, light.b);
                            }
                        }
                    }
                    lightRed[at] = light.r;
                    lightGreen[at] = light.g;
                    lightBlue[at] = light.b;
                    lightOpen[at] = (byte) (outside ? 1 : 0);
                    // The game has no lamp in a canopy. A lit one lights what is under it.
                    if (outside && z >= 0 && ceilingBuilder.underLitCanopy(chunkX, chunkY, chunkX + i - 1, chunkY + j - 1, z)) {
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
        void cornerLights(int localX, int localY) {
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
        private void readWoods(int chunkX, int chunkY) {
            for (int i = 0; i < WOOD_SPAN; i++) {
                for (int j = 0; j < WOOD_SPAN; j++) {
                    int localX = i - Flora.WOOD_RING, localY = j - Flora.WOOD_RING;
                    IsoGridSquare square = squareAt(chunkX, chunkY, chunkX + localX, chunkY + localY, 0);
                    treed[i * WOOD_SPAN + j] = square != null && square.HasTree();
                }
            }
            Flora.wooded(treed, CHUNK_TILES, wooded);
        }

        private void addOver(IsoObject object, IsoSprite under, IsoSprite over, boolean isFloor, int x, int y, int z) {
            if (over == null) {
                return;
            }
            Texture texture = over.getTextureForCurrentFrame(object.getDir());
            if (!usable(texture)) {
                return;
            }
            frameOf(object, texture);
            standOn(object);
            frame.material = under.getType() == IsoObjectType.tree && Own.plants() ? LEAVES : frame.material;
            sprites++;
            // Leaves stand on the same card as their trunk. Each layer is moved a little towards
            // the eye from the one under it, or the two would be drawn through each other.
            overLayer++;
            geometry.nearer = overLayer;
            if (!isFloor && furnishingBuilder.addHung(object, over, texture, x, y, z)) {
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
                    if (wall == WallHung.WEST) {
                        wallBuilder.addEdge(mesh, texture, false, false, x, y, z, westLayer++, 0f, 1f);
                    } else {
                        wallBuilder.addEdge(mesh, texture, false, false, x, y, z, northLayer++, 1f, 0f);
                    }
                    return;
                }
            }
            // Where a street steps along a verge the map paints grass over half of the street's
            // square, to slant the edge, from the ground's own sheet or from the sheet of painted
            // kerbs that have grass behind them. That grass is the verge's, and the verge ends at the kerb:
            // it lies on the land's side of a kerb that crosses the square and nowhere on a street.
            vergeOver = isFloor && z == 0 && groundKind[(waterLocalX + 1) * LIGHT_SPAN + waterLocalY + 1] == Terrain.STREET
                    && over.getName() != null && (over.getName().startsWith("blends_natural") || over.getName().startsWith("street_curbs") && over.getName().contains("grass"));
            if (vergeOver && geometry.kerb == null) {
                vergeOver = false;
                return;
            }
            int before = FirstPerson.TRACE ? worn(texture) : 0;
            // The map hangs a road's lines on the road's own floor, and what is added next is added as that floor's: see addFloor.
            paintOver = kindOf(over).paint;
            addSprite(solid.get(texture.getID()), object, under, texture, isFloor, x, y, z);
            paintOver = false;
            vergeOver = false;
            if (FirstPerson.TRACE) {
                tally(under, worn(texture) - before);
            }
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
        private int rank(IsoSprite sprite) {
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
        private static final int BANNER_ALONG = 4, BANNER_UP = 3, BANNER_FLOATS = 9;
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
                }
            }
            mesh.open = frame.open + 32f * frame.material;
            mesh.face(alongX ? 0f : 1f, alongX ? 1f : 0f, 0f);
            for (int j = 0; j < BANNER_UP; j++) {
                for (int i = 0; i < BANNER_ALONG; i++) {
                    for (int corner : BANNER_CORNERS) {
                        int at = (j + (corner >> 1)) * row + (i + (corner & 1)) * BANNER_FLOATS;
                        mesh.sway = bannerCorners[at + 8];
                        mesh.add(bannerCorners[at], bannerCorners[at + 1], bannerCorners[at + 2], bannerCorners[at + 3], bannerCorners[at + 4],
                                bannerCorners[at + 5], bannerCorners[at + 6], bannerCorners[at + 7]);
                    }
                }
            }
            mesh.sway = 0f;
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
                        Log.info("the banner's length " + name + " at " + x + "," + y + "," + hungOn
                                + " paints nothing at the end of its square, so its cable is taken to be at the top of its picture there");
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
        /** Whether the build has left the span of a wire unknown at an end because the map is not loaded that far. */
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
        private boolean addLights(IsoObject object, IsoSprite sprite, int x, int y, int z) {
            int slice = Lights.slice(sprite);
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
            int emptied = Sightline.emptied(asTheGameShows, gamesView, room != RoomSight.OUTSIDE, square.isSeen(playerIndex));
            boolean unexplored = emptied == Sightline.LEFT_OUT;
            // The band is asked only where its answer changes the build: asked of every square, a chunk would be another build in every band.
            boolean empty = unexplored || !buildingBySight && emptied == Sightline.LEFT_OUT_FAR && !within(UNSHOWN_FILLED_WITHIN, false);
            boolean unsought = buildingBySight && room != RoomSight.OUTSIDE && !builtBySight(room);
            for (int i = 0; i < objects.size(); i++) {
                IsoObject object = objects.get(i);
                IsoSprite sprite = object.getSprite();
                if (sprite == null || empty && object != floor && !partOfTheBuilding(object, sprite) || unsought && object != floor && withinItsRoom(object, sprite)) {
                    continue;
                }
                contentsOf(object != floor && withinItsRoom(object, sprite) ? room : RoomSight.OUTSIDE);
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
                frameOf(object, texture);
                standOn(object);
                Kind kind = kindOf(sprite);
                frame.material = object == floor ? kind.asFloor : kind.asOther;
                laidWall = kind.laidWall;
                gableSheet = kind.gableSheet;
                if (frame.material == PLANKS && isMesh(sprite, texture)) {
                    frame.material = MESH;
                }
                sprites++;
                overLayer = 0;
                geometry.nudgeX = geometry.nudgeY = geometry.nearer = 0f;
                ArrayList<IsoSpriteInstance> leaves = object.getAttachedAnimSprite();
                // A tree in leaf shows its bole and not the branches in its crown: they are
                // inside the leaves. Drawn, they cross from one card of the fan through the leaves
                // of the next. A bare tree is all branch and keeps them.
                boolean inLeaf = sprite.getType() == IsoObjectType.tree && (leaves != null && !leaves.isEmpty() || object.getOverlaySprite() != null);
                geometry.keep = inLeaf ? BOLE : 1f;
                if (unexplored && object == floor) {
                    frame.light(0f, 0f, 0f);
                }
                int before = FirstPerson.TRACE ? worn(texture) : 0;
                addSprite(solid.get(texture.getID()), object, sprite, texture, object == floor, x, y, z);
                if (FirstPerson.TRACE) {
                    tally(sprite, worn(texture) - before);
                }
                if (unexplored && object == floor) {
                    cornerLights(localX, localY);
                }
                geometry.keep = 1f;
                // What the game draws over an object with the object: the leaves on a tree, the
                // ivy and the stains on a wall. Each lies or stands as the object under it does.
                if (leaves != null) {
                    for (int k = 0; k < leaves.size(); k++) {
                        addOver(object, sprite, leaves.get(k).parentSprite, object == floor, x, y, z);
                    }
                }
                addOver(object, sprite, object.getOverlaySprite(), object == floor, x, y, z);
                geometry.nudgeX = geometry.nudgeY = geometry.nearer = 0f;
            }
            contentsOf(RoomSight.OUTSIDE);
            geometry.raised = 0f;
            frame.material = 0f;
            if (!openAbove && square.getRoomID() != -1L) {
                ceilingBuilder.addRoomCeiling(x, y, z, localX, localY);
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
                int laidItems = layItems(z, floorLayer + spreadLayers);
                contentsOf(RoomSight.OUTSIDE);
                // What grows here is a build of its own: see grow. It lies over these layers.
                building.groundLayers[localX * CHUNK_TILES + localY] = (byte) (floorLayer + spreadLayers + laidItems);
                int own = (localX + 1) * LIGHT_SPAN + localY + 1;
                building.grows |= groundKind[own] == Terrain.NATURAL && lightOpen[own] == 1;
            }

            contentsOf(room);
            if (z != 0) {
                layItems(z, floorLayer);
            }

            ArrayList<IsoMovingObject> bodies = square.getStaticMovingObjects();
            for (int i = 0; i < bodies.size() && !empty && !unsought; i++) {
                if (bodies.get(i) instanceof IsoDeadBody body) {
                    addBody(body);
                }
            }
            contentsOf(RoomSight.OUTSIDE);
        }

        /** Has what is built next be the contents of a room, or with RoomSight.OUTSIDE drawn whenever its chunk is. */
        private void contentsOf(long room) {
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
            for (int i = 0; i < items.size(); i++) {
                IsoWorldInventoryObject item = items.get(i);
                if (!item.getItem().getScriptItem().isWorldRender()) {
                    continue;
                }
                IsoSprite sprite = item.getSprite();
                Texture texture = sprite.getTextureForCurrentFrame(item.getDir());
                GroundItems.corners(texture.getWidthOrig(), texture.getHeightOrig(), texture.getOffsetX(), texture.getOffsetY(),
                        texture.getWidth(), texture.getHeight(), sprite.def.getScaleX(), sprite.def.getScaleY(), frame.scale,
                        item.getItem().getWorldZRotation(), texture.getXStart(), texture.getYStart(), texture.getXEnd(), texture.getYEnd(), itemCorners);
                ColorInfo tint = sprite.getTintMod();
                float itemX = item.getWorldPosX(), itemY = item.getWorldPosY(), up = item.getWorldPosZ() * ViewCamera.LEVEL_HEIGHT;
                SpriteGeometry.Mesh mesh = solid.get(texture.getID());
                mesh.open = frame.open;
                mesh.face(0f, 0f, 1f);
                layers.lie(SpriteGeometry.LAYERS_UP, under + 1 + laid % GroundItems.LAYERS);
                for (int corner : GroundItems.TRIANGLES) {
                    int at = corner * GroundItems.CORNER_FLOATS;
                    float atX = itemX + itemCorners[at], atY = itemY + itemCorners[at + 1];
                    heightsAt(atX, atY);
                    mesh.add(atX, atY, up + terrain.lift(atX, atY, z), itemCorners[at + 2], itemCorners[at + 3],
                            frame.red * tint.r, frame.green * tint.g, frame.blue * tint.b);
                }
                layers.none();
                laid++;
            }
            return Math.min(laid, GroundItems.LAYERS);
        }

        /**
         * A body lying on the ground. The game has no model standing there to draw: it keeps a
         * picture of the body as its own view sees it, made once, and draws that where the body
         * lies. The same picture is laid on the ground here, every texel of it where the game's
         * view has it, so it is the body the game shows, lying as the game shows it. The game
         * makes the picture when the body first comes into its own view, and until then there is
         * none to lay.
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
            mesh.open = frame.open;
            mesh.face(0f, 0f, 1f);
            float x = body.getX(), y = body.getY(), z = body.getZ();
            // Where the game draws the picture, in its own pixels from where it draws the body's place on the floor.
            float left = picture.getRenderX(0f), top = picture.getRenderY(0f), wide = picture.getRenderWidth(), high = picture.getRenderHeight();
            float across = SpriteGeometry.PIXELS_ACROSS_PER_TILE * frame.scale, down = SpriteGeometry.PIXELS_DOWN_PER_TILE * frame.scale;
            // What is above the floor is drawn further up the screen, so the picture lies this much nearer the game's view than it would on the floor.
            float nearer = BODY_UP / ViewCamera.LEVEL_HEIGHT * SpriteGeometry.PIXELS_PER_LEVEL / (2f * SpriteGeometry.PIXELS_DOWN_PER_TILE);
            for (int corner : BODY_CORNERS) {
                boolean right = corner == 1 || corner == 2, low = corner >= 2;
                float u = (left + (right ? wide : 0f)) / across, v = (top + (low ? high : 0f)) / down;
                float atX = x + (v + u) / 2f + nearer, atY = y + (v - u) / 2f + nearer;
                heightsAt(atX, atY);
                mesh.add(atX, atY, z * ViewCamera.LEVEL_HEIGHT + terrain.lift(atX, atY, z) + BODY_UP,
                        right ? texture.getXEnd() : texture.getXStart(), low ? texture.getYEnd() : texture.getYStart(), frame.red, frame.green, frame.blue);
            }
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
            float base = character.getZ() * ViewCamera.LEVEL_HEIGHT + terrain.lift(x, y, character.getZ());
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

            if (isFloor || properties.has(IsoFlagType.solidfloor) || properties.has(IsoFlagType.FloorOverlay)
                    || properties.has(IsoFlagType.attachedFloor)) {
                // The map closes a tent at the level its canopy is on with a ceiling laid as a
                // floor. Inside the tent the canvas is the ceiling: the flat one is left out.
                if (canvas && z > 0) {
                    return;
                }
                floorBuilder.addFloor(mesh, kind, properties, texture, isFloor, x, y, z);
                return;
            }

            // What the game draws with the floor though it is no floor: railway track, and the
            // litter it calls a rug. Each is painted lying on the ground, and stood up to face the
            // eye a sheet of newspaper is a hoarding and a length of track a fence.
            if (!hangsInAir && kind.lies) {
                layers.lie(SpriteGeometry.LAYERS_UP, floorLayer++);
                geometry.addFace(laid.get(texture.getID()), frame, x, y, z, -Flat.REACH, -Flat.REACH, 0f, 1f + 2f * Flat.REACH, 0f, 0f, 0f, 1f + 2f * Flat.REACH, 0f);
                layers.none();
                return;
            }

            if (kind.wire && Poles.length(sprite.getName()) && wireBuilder.addPole(mesh, object, sprite, texture, x, y, z)) {
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

            if (Own.furniture() && addParasol(object, sprite, texture, properties, x, y, z)) {
                return;
            }

            if (Own.furniture() && (addAwning(sprite, texture, x, y, z) || addHood(sprite, texture, x, y, z))) {
                return;
            }

            if (Own.furniture() && addTower(sprite, x, y, z)) {
                return;
            }

            if (addTent(mesh, sprite, x, y, z)) {
                return;
            }

            if (Own.furniture() && addLights(object, sprite, x, y, z)) {
                return;
            }

            // A fence's corner post that a string of lights is made fast to. Stood as a wall's
            // corner piece it is its picture on both edges of its square, two boards crossed,
            // each twice as wide as the post and cut to the point the game's view draws its top as.
            if (Own.furniture() && overLayer == 0 && properties.has(IsoFlagType.WallSE) && sprite.getName() != null && sprite.getName().startsWith("fencing_")
                    && holdsLights(x, y, z) && postBuilder.addPost(mesh, object, sprite, texture, x, y, z)) {
                return;
            }

            if (z > 0 && kind.roofAccent && roofBuilder.underARake(x, y, z)) {
                return;
            }
            // The eave the map hangs on the wall under a slope's foot, the trims painted on it and
            // the corner piece past each end of it: the slope's lowest tile makes that eave.
            if ((kind.roofAccent || Hoods.eave(sprite) != Hoods.NONE) && roofBuilder.eaveRow(x, y, z) || Hoods.endPiece(sprite) && roofBuilder.eaveRowEnd(x, y, z)) {
                return;
            }

            if (overLayer == 0 && furnishingBuilder.addHung(object, sprite, texture, x, y, z)) {
                return;
            }
            if (furnishingBuilder.addFurniture(object, sprite, x, y, z)) {
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
                west = both || standsOnWestEdge(properties, type) || cloth && !Banners.alongX(frame);
                north = both || standsOnNorthEdge(properties, type) || cloth && Banners.alongX(frame);
                if (kind.edge != Overhead.ANY_EDGE) {
                    west = kind.edge != Overhead.NORTH_EDGE;
                    north = kind.edge != Overhead.WEST_EDGE;
                }
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
            glazedWall = object instanceof IsoWindow;
            paperedWall = kind.papered;
            wallOpens = properties.has(IsoFlagType.DoorWallN) || properties.has(IsoFlagType.DoorWallW)
                    || properties.has(IsoFlagType.WindowN) || properties.has(IsoFlagType.WindowW);
            seeThrough = isSheet(properties);
            // A gate in a fence is rails or wire like the fence, and stays the sheet it is.
            doorLeaf = door && !kind.fenceGate;
            casing = type == IsoObjectType.doorFrW || type == IsoObjectType.doorFrN;
            wallBuilder.sign = overLayer == 0 && !door && west != north && EdgeRuns.sign(sprite.getName(), properties.get("CustomName"));
            if (west) {
                wallBuilder.addEdge(mesh, texture, isWall(properties, type), door, x, y, z, westLayer++, 0f, 1f);
            }
            if (north) {
                wallBuilder.addEdge(mesh, texture, isWall(properties, type), door, x, y, z, northLayer++, 1f, 0f);
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
                // The game marks a few things that are no roof as one, so that they are cut away
                // with the roof when the player walks under: a church's columns. Laid as a roof
                // tile a column is a slab leaning on the wall.
                if (overLayer == 0 && sprite.getName() != null && !kind.roofSheet && postBuilder.addPost(mesh, object, sprite, texture, x, y, z)) {
                    return;
                }
                roofName = sprite.getName();
                roofBuilder.addRoof(mesh, texture, x, y, z);
                return;
            }
            if (z > 0 && kind.roofSheet) {
                // Left out. Laid level they stand proud of a gable as slabs; the sheet under each
                // roof tile is what closes the roof.
                return;
            }
            // A chalkboard, a notice or a map the game hangs on a wall by the way it faces and marks for no wall. Stood
            // up to face the eye it hung in the middle of its square. With the mod's furniture off it is as it was.
            if (kind.flatWall != WallHung.NONE && Own.furniture() && layFlat(mesh, kind.flatWall, x, y, z)) {
                return;
            }
            // A boulder. The game's picture of one stood up as a card is a cut-out of a boulder,
            // and wrapped on a box it is a crate painted as one.
            if (kind.boulder && Own.plants() && plantBuilder.addRock(x, y, z)) {
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
                    geometry.addUpright(mesh, frame, x, y, z, 0.5f, 0.5f, 0f, 1f, 0f, true, false);
                    geometry.keep = 1f;
                }
                return;
            }
            // Long grass. The game has it as a small picture of a clump, which up close is a
            // handful of coarse painted strokes. Here it is grown instead: tall blades, the
            // colour of the ground they stand in, thick on the square.
            if (z == 0 && kind.longGrass && Own.plants() && plantBuilder.growLongGrass(x, y)) {
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
        private boolean addParasol(IsoObject object, IsoSprite sprite, Texture texture, PropertyContainer properties, int x, int y, int z) {
            int quarter = Parasols.quarter(sprite);
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
        private boolean addAwning(IsoSprite sprite, Texture texture, int x, int y, int z) {
            Awnings.Hung hung = Awnings.of(sprite);
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
            hung.centre(model.depth(), awningAt);
            float foot = z * ViewCamera.LEVEL_HEIGHT + (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f + hung.foot * ViewCamera.LEVEL_HEIGHT;
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
                mesh.add(x + a, y + b, (z + liningAt[corner * 3 + 2]) * ViewCamera.LEVEL_HEIGHT + geometry.liftAt(a, b),
                        SpriteGeometry.textureAtU(frame, u), SpriteGeometry.textureAtV(frame, v),
                        frame.cornerRed[0] * w0 + frame.cornerRed[1] * w1 + frame.cornerRed[2] * w2 + frame.cornerRed[3] * w3,
                        frame.cornerGreen[0] * w0 + frame.cornerGreen[1] * w1 + frame.cornerGreen[2] * w2 + frame.cornerGreen[3] * w3,
                        frame.cornerBlue[0] * w0 + frame.cornerBlue[1] * w1 + frame.cornerBlue[2] * w2 + frame.cornerBlue[3] * w3);
            }
        }

        private boolean addTent(SpriteGeometry.Mesh mesh, IsoSprite sprite, int x, int y, int z) {
            Tents.Piece piece = Tents.of(sprite), rope = Tents.ropeOf(sprite);
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
        private boolean addHood(IsoSprite sprite, Texture texture, int x, int y, int z) {
            int wall = Hoods.eave(sprite);
            if (wall == Hoods.NONE) {
                return Hoods.endPiece(sprite) && Hoods.endOfAHood(Hoods.on(cell, z), x, y);
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
            float foot = z * ViewCamera.LEVEL_HEIGHT + (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f + Hoods.FOOT * ViewCamera.LEVEL_HEIGHT;
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
        private boolean addTower(IsoSprite sprite, int x, int y, int z) {
            Towers.Part[] parts = Towers.of(sprite);
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
                }
                towerLight.open = frame.open;
                placed.add(Models.get(section.model()), towerLight, x, y, centreX, centreY, foot, section.turns(), Furniture.TILES_PER_METRE,
                        Furniture.TILES_PER_METRE, 0, 0, 0f, 0f, 0, 0f, 0f);
                placed.built();
                furnished++;
            }
            return dealt;
        }

        final SpriteGeometry.Frame towerLight = new SpriteGeometry.Frame();

        /** Whether the mains are on: read once a frame. */
        boolean mainsOn;
        final SpriteGeometry.Frame bandFrame = new SpriteGeometry.Frame();
        /** Where fasciaOf last found a band: the square it hangs on. */
        int bandX, bandY;

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
        /** Whether the sprite being added is the frame round a doorway. */
        boolean casing;

        /** Whether the sprite being added is a window's that is also a wall. */
        boolean glazedWall;
        /** The build being filled. */
        ChunkMeshes building;

        /** The lamps that burn within reach of the chunk being summed, found once a sum: a square of a room asks only these. */
        final ArrayList<IsoLightSource> lampsNear = new ArrayList<>();

        boolean paperedWall;
        /** The colour the papered wall's mesh is painted over in, as the picture is told it, and the light of the piece being added while its stripes are. */
        final float[] paperTint = new float[4], paperRed = new float[4], paperGreen = new float[4], paperBlue = new float[4];

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
            // What the picture does without for an eye thousands of tiles off, it has again once
            // the eye is as near as the place looked at is taken to be.
            view.pipeline.outside = outside;
            view.pipeline.overhead = outside && outsideAway > OVERHEAD_RANGE;
            view.pipeline.seated = seated;
            view.pipeline.reflection = glass.found ? glass : null;
            view.pipeline.opacity = opacity;
            view.pipeline.cutAbove = outside ? cutFrom : Float.MAX_VALUE;
            view.pipeline.cutBehind = 1f - landing;
            view.pipeline.lid = outside && outsideCamera.eyeUp > lidFrom ? lidFrom : Float.MAX_VALUE;
            view.pipeline.cutRows = cutRows;
            view.pipeline.cutRowsX = cutRowsX;
            view.pipeline.cutRowsY = cutRowsY;
            view.pipeline.cutBelow = outside ? cutBelow : 0f;
            view.pipeline.sheltered = sheltered;
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
                view.pipeline.figureRooms = null;
                float nearest = Math.max(outsideAway - OVERHEAD_DEPTH, ViewCamera.nearPlane(NEAR_CORNER_DISTANCE, outsideTan, aspect));
                view.pipeline.draw(shown, outsideCamera, sky, outsideTan, nearest, outsideAway + FAR, fogAt * FOG_START, fogAt, exposure, viewport);
                lastDrawMillis = view.pipeline.lastMillis();
                return;
            }
            view.pipeline.rangeFrom = 0f;
            view.pipeline.loadedWithin = loadedWithin;
            view.pipeline.roomsSeen = aspect == roomsAspect ? roomsSeen : null;
            view.pipeline.figureRooms = figureRooms;
            view.pipeline.draw(shown, camera, sky, tanHalfFieldOfView, near, FAR, fogEnd * FOG_START, fogEnd, exposure, viewport);
            lastDrawMillis = view.pipeline.lastMillis();
        }
    }
}

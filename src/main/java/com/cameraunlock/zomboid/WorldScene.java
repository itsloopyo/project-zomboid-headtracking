package com.cameraunlock.zomboid;

import java.lang.ref.WeakReference;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.opengl.GL11;
import zombie.GameTime;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.Core;
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
import zombie.iso.LightingJNI;
import zombie.iso.RoomDef;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.areas.IsoBuilding;
import zombie.iso.areas.IsoRoom;
import zombie.iso.fboRenderChunk.FBORenderChunk;
import zombie.iso.fboRenderChunk.FBORenderCutaways;
import zombie.iso.fboRenderChunk.FBORenderLevels;
import zombie.iso.objects.IsoDeadBody;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoLightSwitch;
import zombie.iso.objects.IsoTelevision;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWindow;
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
    private static final int PLAIN = 0;
    /** A character the game has faded out of the view is not drawn. */
    private static final float CHARACTER_SHOWN_ALPHA = 0.5f;

    private static final int GL_VIEWPORT = 0x0BA2;
    /** The part of the game's picture the frame being drawn is written to. Render thread. */
    private final int[] viewport = new int[4];

    /** The land beyond the loaded map, round this view's eye. */
    final FarLand farLand = new FarLand();
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
        private int band;
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
        private boolean signed;
        private long signature;
        /** The frame and the time from which the chunk's builds are summed again, after one was found changed with the eye still. */
        private int summedFrom;
        private long summedFromNanos;
        /**
         * Whether the build read nothing but what its sum holds: no room, no wall, no roof, no
         * canopy, nothing hung or raised. Such a build is the same for as long as its sum is.
         */
        private boolean plain;
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
        private int land, heightsRound;
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
                }
                runTexture[runs] = mesh.texture;
                runTint[runs] = mesh.tint;
                runFloats[runs] = mesh.floats;
                runs++;
                floats = Math.addExact(floats, mesh.floats);
                float[] from = mesh.vertices;
                float lowX = minX, lowY = minY, lowUp = minUp, highX = maxX, highY = maxY, highUp = maxUp, along = turned, layers = layered;
                for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                    float x = from[at], y = from[at + 1], up = from[at + 2];
                    float moved = from[at + SpriteGeometry.VERTEX_FLOATS - 1];
                    if (moved == SpriteGeometry.FACES) {
                        along = Math.max(along, Math.abs(from[at + SpriteGeometry.VERTEX_FLOATS - 3]));
                    } else if (moved != 0f) {
                        layers = Math.max(layers, Math.abs(from[at + SpriteGeometry.VERTEX_FLOATS - 3]));
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
    private final java.util.concurrent.LinkedBlockingQueue<Crowns.Work> crownsBack = new java.util.concurrent.LinkedBlockingQueue<>();
    /** What the build being made has written down of its crowns, and what a check's second build has. */
    private Crowns crownsRead = new Crowns();
    private static final Crowns crownsChecked = new Crowns();
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
    private float lastEyeX = Float.NaN, lastEyeY, lastEyeUp;
    private boolean eyeStill;
    /** The loaded map as it was when the last frame was built: its first chunk each way and how many chunks a side it is. */
    private int mapWx, mapWy, mapSide;
    /** How many times this view's world has been built, and the round of every view's builds that the last was in: see Rounds. */
    private int frames, roundNow;
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
    private static final SpriteGeometry.Layers layers = new SpriteGeometry.Layers();
    private static final SpriteGeometry.MeshSet solid = new SpriteGeometry.MeshSet(layers), laid = new SpriteGeometry.MeshSet(layers);
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
    private boolean crownedToSight;

    /**
     * Whether the roof and the ceilings the mod makes over a square are left out so that the
     * viewer can be seen from overhead. They lie over more than their own square, so they go by
     * the box of a building the game opens, a square wider all round, and under the ground
     * by nothing: the game shows nothing over a viewer there.
     */
    private boolean cutAway(int x, int y) {
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
    private static final class Kind {
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
        }
    }

    private static final IdentityHashMap<IsoSprite, Kind> kinds = new IdentityHashMap<>();
    private static final int MESH_CHANGES = 12;
    private static final float SWITCH_WIDEST = 12f;
    /** For each sprite hung in the air: the wires it shows, see Wires.read. */
    private static final IdentityHashMap<Texture, float[]> wires = new IdentityHashMap<>();
    private static final long[] NO_CHUNKS = new long[0];
    /** The map the kept builds were read from, and the one everything every view keeps by place or by sprite was. */
    private WeakReference<IsoCell> builtIn = new WeakReference<>(null);
    private static WeakReference<IsoCell> readIn = new WeakReference<>(null);
    /** How thick an overhead wire is drawn, in tiles: thicker than one is, or a street away it is finer than a pixel and comes and goes. */
    private static final float WIRE_THICK = 0.03f;
    /** The least a vertex of what hangs on a cord is given as hanging below the line between the cord's supports, in tiles: the picture tells it from a lamp that stands by its hanging at all. */
    private static final float HUNG_LEAST = 0.004f;
    /** What hangs on a cord and gives no light: a bulb that is out, and a bulb's holder. The picture swings it with its cord and lights it as any plain thing. */
    private static final float HUNG = 82f;
    /** How far a roof runs out past its wall, and how deep the board along its end is, in tiles. */
    private static final float EAVE = 0.22f, FASCIA = 0.12f, RAKE = 0.14f, GUTTER = 0.07f, PIPE = 0.035f;
    /**
     * The cap along a ridge or down a hip, in tiles: how far it reaches either side of the line,
     * half the width of its level crown, how far the crown stands over the line and how far its
     * edges stand off the roof.
     */
    private static final float CAP_HALF = 0.11f, CAP_CROWN = 0.035f, CAP_RISE = 0.03f, CAP_CLEAR = 0.008f;
    /** How far under a roof tile its plain sheet lies, in levels. */
    private static final float UNDER_ROOF = 0.03f;
    /** How many layers of a floor a flat roof's covering lies above the floor it is on: more than a floor has of its own. */
    private static final float FLAT_ROOF_LAYERS = 4f;
    /** A flat roof read lower over its floor than this, in levels, lies on the floor. */
    private static final float FLAT_ROOF_ON_FLOOR = 0.06f;
    /** Half the thickness of a wall, in tiles. */
    static final float WALL_HALF = 0.04f;
    /** How far from the line of a wall the middle of a handrail fixed to it runs, in tiles. */
    private static final float RAIL_OFF_WALL = WALL_HALF + 0.09f;
    private static final float HUNG_CLEAR = 0.012f;
    /** The game gives the height of a surface in pixels of its unscaled sprites, of which a level is this many. */
    static final float SURFACE_PIXELS_PER_LEVEL = 96f;
    static final int TABLE_HIGHEST = 48;
    static final int TALL_SURFACE = 60;
    private static final float CUPBOARD_DEEP = 0.55f;
    private static final int LONG_TUFTS = 13;
    private static final int WOOD_SPAN = CHUNK_TILES + 2 * Flora.WOOD_RING;
    /** How far from the eye each thing Flora makes is still made, in tiles. Past TRUNK_NEAR a bole is a plain post, and past ROCK_NEAR a boulder has a quarter of its faces. */
    private static final float FERN_REACH = 22f, FERN_NEAR = 12f, FALLEN_REACH = 10f, FLOWER_REACH = 14f, TRUNK_NEAR = 18f, TRUNK_REACH = 60f,
            ROCK_NEAR = 30f, BUSH_REACH = 40f;
    /** How far in from the edge of a lawn its grass takes to reach its full height, in tiles. */
    private static final float LAWN_EDGE = 0.9f;
    /** How far from a bush's middle its outer cards stand, in tiles. */
    private static final float BUSH_SPREAD = 0.16f;
    /** What was read off each tree's sprite: how thick its bole is and how tall, in tiles. Empty for a sprite with no bole to read. */
    private static final IdentityHashMap<IsoSprite, float[]> boles = new IdentityHashMap<>();
    private static final float[] NO_BOLE = new float[0];
    /** A bole is no thinner or thicker than this, in tiles, whatever its sprite says. */
    private static final float BOLE_THINNEST = 0.05f, BOLE_THICKEST = 0.4f;
    private static final float GRASS_SPILL = 0.35f;
    /** How far down the wall of a quay goes from its top: past the water's surface. */
    private static final float QUAY_DEPTH = 0.6f;
    /** How far a layer of cards stands from the one under it besides what any layer does, in tiles: leaves on the card of their trunk. */
    static final float CARD_LAYER = 0.035f;
    /** The height of a bed, in levels. */
    static final float BED_HEIGHT = 0.2f;
    /** How much of a tree's trunk sprite, from the ground up, is its bole. */
    private static final float BOLE = 0.42f;
    /** How far from the eye a lawn's grass stands up, in tiles. */
    private static final float GRASS_REACH = 16f;
    /** How far in from a side where the lawn ends its plants keep, in tiles. */
    private static final float LAWN_KEEPS_IN = 0.05f;
    private static final IdentityHashMap<IsoSprite, int[]> ranks = new IdentityHashMap<>();
    /** How far in from a wall's end its sprite is looked over for its first painted pixel, in unscaled pixels. */
    private static final float MOST_UNPAINTED = 5f;
    /** The height, in levels, a wall's sprite is looked over at: under the sill of a window. */
    private static final float SEAL_HEIGHT = 0.15f;
    /** A chunk's squares and the ring of squares round them. */
    private static final int LIGHT_SPAN = CHUNK_TILES + 2;
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
    private IsoChunkMap chunkMap;
    /**
     * The spans the lengths of overhead wire hang in, as Spans finds them, by the square, the
     * sprite and which of its lines a length is. A span is found by following its wire to both
     * its poles, some chunks either way, once: the map does not move its poles. One left
     * unknown at an end for want of map is found again when the map has other chunks loaded.
     */
    private final HashMap<Long, float[]> strung = new HashMap<>();
    private int strungWith;
    /** The run each length of a banner strung across a street is part of, as Banners finds it, by the square it hangs on. Kept and dropped as strung is. */
    private final HashMap<Long, float[]> bannerRuns = new HashMap<>();
    /** The lighting engine's count of its passes when each chunk's lights were last asked after, and the game's own note on the chunk of when it last took them. */
    private final int[] litAt = new int[TABLE_SIDE * TABLE_SIDE], litByGame = new int[TABLE_SIDE * TABLE_SIDE];
    private long tollQuiet, tollPulls, tollPulled, tollAgedDiffered, tollAgedSame, tollAgedSamePlain, tollRestless;
    /** Dev only: the game's marks are left unused when set, for a comparison within one run. */
    private volatile boolean withoutMarks;
    /**
     * Dev only, for the same: a chunk's crowns are made again with every build of it, they
     * are made on the logic thread, and a chunk is built again in every band it passes into.
     */
    private volatile boolean withoutKeptCrowns, withoutCrownThread, withoutAlikeBands;

    /** Dev only, for the rig: names what is turned off ("marks", "kept", "thread", "bands", or several with commas), and everything else is on. */
    void without(String what) {
        java.util.List<String> off = Arrays.asList(what.split(","));
        withoutMarks = off.contains("marks");
        withoutKeptCrowns = off.contains("kept");
        withoutCrownThread = off.contains("thread");
        withoutAlikeBands = off.contains("bands");
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
        return polledBecause == null && zombie.core.PerformanceSettings.fboRenderChunk && !withoutMarks;
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
    /** A wall whose sprite is painted this high up, in levels, is a whole wall and not a low one. */
    private static final float WHOLE_WALL = 0.9f;
    /** How far up a wall's sprite an opening is looked for, in levels: above a sill and below a lintel. */
    private static final float OPENING_ROW = 0.5f;
    /** The narrowest gap in a wall's sprite that is an opening, as a share of the wall's length. */
    private static final float NARROWEST_OPENING = 0.2f;
    /** How far inside an opening's corners its sprite has to be clear for it to be a rectangle, in unscaled pixels. */
    private static final float OPENING_CORNER = 1.5f;
    /** No door is lower than this, in levels. */
    private static final float LOWEST_DOOR = 0.5f;
    /** Where the opening in each wall sprite seen so far is: see openingOf. */
    private static final IdentityHashMap<Texture, float[]> openings = new IdentityHashMap<>();

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
        java.util.TreeSet<Integer> made = new java.util.TreeSet<>();
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
    private static final float[] BAND_ENDS = {4f, 9f, 16f, 24f, 40f}, BAND_AWAY = {2f, 6f, 12f, 20f, 30f, 70f};
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
    }

    /**
     * The lie of the land this view's world is built and drawn on: the hills, or the country
     * left level while the view is the game's own from overhead. Set by the logic thread.
     */
    volatile Terrain terrain = Terrain.HILLS;

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

    /** Lets go of every build: for when what all of them were built on has changed. */
    void forgetAll() {
        for (int slot = 0; slot < latest.length; slot++) {
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

    private static final java.util.HashSet<String> movers = new java.util.HashSet<>();

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
    final java.util.concurrent.ConcurrentHashMap<String, int[]> stretched = new java.util.concurrent.ConcurrentHashMap<>();

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
        return usable(texture) ? texture : null;
    }

    /** Whether a texture has an OpenGL name and a size. */
    static boolean usable(Texture texture) {
        return texture != null && texture.getID() > 0 && texture.getWidth() > 0 && texture.getHeight() > 0;
    }

    /** Two triangles of a strip between two ends: each corner is an end, and whether it is the top there. */
    private static final int[] KERB_CORNERS = {0, 2, 3, 0, 3, 1};

    private static final float SHINGLES = 1f, PAINT = 2f, WOOD = 3f, EARTH = 4f, LEAVES = 5f, BARK = 6f, PLANKS = 7f, CARPET = 8f, CONCRETE = 9f, QUAY = 10f, TILE = 11f, WIRE = 12f, GLASS = 13f, LAMP = 14f, MESH = 15f;
    /** The mod's own kerb, of cast concrete. Its number is past those of what grows. */
    private static final float KERB_STONE = 64f;
    /** A window pane with a lamp burning in the room behind it. */
    private static final float LIT_GLASS = 65f;
    /** A pane of a window the picture has built, and one with a lamp burning in the room behind it. */
    private static final float PANE = 67f, LIT_PANE = 68f;
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

    /** The sheet of floors for inside a house: its tiles and lino first, then from FIRST_WOOD its boards and parquet. */
    private static final String TILES_AND_WOOD = "floors_interior_tilesandwood_01_";
    private static final int FIRST_WOOD = 40;

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
        // Another mod's sheet may take the name with something other than a number after it: that is no sprite of this sheet.
        if (isFloor && name.startsWith(TILES_AND_WOOD) && name.length() > TILES_AND_WOOD.length() && name.chars().skip(TILES_AND_WOOD.length()).allMatch(Character::isDigit)) {
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
    private static float under(int z, float high) {
        return (z + high - Math.min(UNDER_ROOF, Math.max(high, 0f) * 0.5f)) * ViewCamera.LEVEL_HEIGHT;
    }

    /** The corners of a strip in the order of its two triangles: which end of the wire each is at, and which side of its middle. */
    private static final int[] STRIP_END = {0, 1, 1, 0, 1, 0}, STRIP_SIDE = {-1, -1, 1, -1, 1, 1};

    /** How far above the floor a body's picture is laid, in tiles: clear of the blood and the litter laid on the floor under it. */
    private static final float BODY_UP = 0.08f;
    private static final int[] BODY_CORNERS = {0, 1, 2, 0, 2, 3};

    /** Whether an object is the house itself and not something in it: a wall, a door, a window, stairs, a roof. */
    private static boolean partOfTheBuilding(IsoObject object, IsoSprite sprite) {
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

    /** Half the width of the joint between two tiles, in tiles of the map, and how much darker than the sprite paints it the joint is drawn. */
    private static final float JOINT_HALF = 0.006f, JOINT_DARK = 0.8f;
    /** A carpet no more uneven than this is one colour all over. A carpet with a figure woven in it is more, and keeps its sprite. */
    private static final float CARPET_UNEVEN = 0.006f;

    /** Where each plant's leaves are painted on its texture: see Flora.leafPlaces. */
    private static final IdentityHashMap<Texture, float[]> leafPlaces = new IdentityHashMap<>();
    private static final int CROWN_LEAST_PLACES = 12;

    /** Where each sprite the game gives no placing to has its foot: see SpriteShape.foot. Empty for a sprite with nothing painted. */
    private static final IdentityHashMap<IsoSprite, float[]> feet = new IdentityHashMap<>();
    /** How near the edge of its square a post's foot is for the post to be in the line of the wall there, in tiles. */
    private static final float IN_WALL_LINE = 0.3f;
    /** What stands in a wall's line as a flat piece is at least this tall, in levels, this long, in tiles, and this many times as long as its foot is wide. */
    private static final float BRACED_TALL = 0.9f, BRACED_LONG = 0.6f, BRACED_SPREAD = 3f;
    /** How far past its square and how high a piece in a wall's line may reach: the face is cut to what the sprite paints. */
    private static final float BRACED_REACH = 0.5f, BRACED_HIGH = 1.5f;

    /** What each sprite has been read as by Posts: NO_POST for one that is none. */
    private static final IdentityHashMap<IsoSprite, Object> posts = new IdentityHashMap<>();
    private static final Object NO_POST = new Object();
    /** How far apart, in tiles, the axes of two lengths of one post may be read on the squares one above the other. */
    private static final float SAME_POST = 0.2f;

    /** What each sprite the game gives no placing to has been read as: see SpriteShape. */
    private static final IdentityHashMap<IsoSprite, SpriteShape> shapes = new IdentityHashMap<>();

    /** Where the sprite gives a piece's colour when it has none at the first place, as shares of that place's height. */
    private static final float[] DYE_TRIES = {1f, 0.5f, 0.75f};
    /** The frames a picture can hang in, from the tallest for its width to the widest. */
    private static final String[] FRAMES = {"frame_tall", "frame_square", "frame_wide"};
    /** The least and the most a clock is made of its model's size. */
    private static final float CLOCK_SMALLEST = 0.8f, CLOCK_LARGEST = 1.6f;

    /** How near the end of its square the sprite of one length of a long picture stops for the length to be stood to the end. */
    private static final float LENGTHS_MEET = 0.1f;

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

    private static final int[] CEILING_CORNERS = {0, 1, 2, 0, 2, 3};

    /** Which square of a flight a sprite of this type is: 0 the bottom one, 2 the top, -1 for no stair. */
    private static int thirdOf(IsoObjectType type) {
        return type == IsoObjectType.stairsBW || type == IsoObjectType.stairsBN ? 0
                : type == IsoObjectType.stairsMW || type == IsoObjectType.stairsMN ? 1
                : type == IsoObjectType.stairsTW || type == IsoObjectType.stairsTN ? 2 : -1;
    }

    private static boolean climbsWest(IsoObjectType type) {
        return type == IsoObjectType.stairsBW || type == IsoObjectType.stairsMW || type == IsoObjectType.stairsTW;
    }

    private static boolean isStair(IsoObjectType type) {
        return type == IsoObjectType.stairsBW || type == IsoObjectType.stairsMW || type == IsoObjectType.stairsTW
                || type == IsoObjectType.stairsBN || type == IsoObjectType.stairsMN || type == IsoObjectType.stairsTN;
    }

    /** A roof's edge lower than this over the floor of its level, in levels, has no wall made under it. */
    private static final float GABLE_LEAST = 0.05f;

    /** The west, east, north and south edge of a square, each as its two corners. */
    private static final int[][] EDGES = {{0, 3}, {1, 2}, {0, 1}, {3, 2}};
    /** How deep the canopy over each square of one is, in tiles, by the square's place: 0 for a floor in the air with no band round it. */
    private static final HashMap<Long, Float> canopies = new HashMap<>();
    /** How far a canopy is looked across for its edge, in squares, and the least and most deep one is made, in tiles. */
    private static final int CANOPY_SPAN = 24;
    private static final float CANOPY_SHALLOWEST = 0.25f, CANOPY_DEEPEST = 1.2f;
    /** How far past the canopy's edge its lining runs, to meet the band hung just outside the edge. */
    private static final float CANOPY_LIP = 0.1f;

    /** Whether a square with a floor is roof over open air and no room's floor: nothing is built on it, nor under it on the level below. */
    private static boolean isCanopy(IsoGridSquare square, IsoGridSquare below) {
        return square != null && square.getFloor() != null && square.getRoomID() == -1L && square.getZ() >= 1
                && (below == null || below.getRoomID() == -1L && below.getFloor() == null);
    }
    /** How far a wall's colour may be from that of its building's walls, summed over red, green and blue, for it to be built as they are. */
    private static final float SAME_FINISH = 0.3f;

    /** Half the thickness of a door's leaf, in tiles. */
    private static final float DOOR_HALF = 0.022f;
    /** How high up a door its width is read, in levels, and how far in from its edge the colour of its edges is taken, in frame pixels at tile scale 1. */
    private static final float DOOR_READ_AT = 0.4f, DOOR_STILE = 2.5f;
    /** For each door sprite on each edge: the frame origin it was read with, then the shares of the edge its leaf starts and ends at. */
    private static final IdentityHashMap<Texture, float[]> doorSpans = new IdentityHashMap<>();
    private static final int SPAN_FLOATS = 4;

    /** Cloth the picture has hung in folds of its own. */
    private static final float DRAPE = 69f;
    /** How far a curtain hangs clear of the wall's skin, how thick its rod is and how far the rod runs past the cloth at each end, in tiles. */
    private static final float DRAPE_CLEAR = 0.04f, ROD_THICK = 0.022f, ROD_PAST = 0.04f;
    /** How wide a fold is in a curtain drawn across and the narrowest one in a curtain pulled back, and how deep each is, in tiles. */
    private static final float FOLD_WIDE = 0.055f, FOLD_NARROW = 0.022f, FOLD_DEEP = 0.016f, FOLD_DEEP_BACK = 0.024f;
    /** How many strips a fold is made of, how much of a curtain's height its hem is and how much darker. */
    private static final int FOLD_STRIPS = 6;
    private static final float HEM = 0.07f, HEM_SHADE = 0.86f;
    /** The colour of a curtain rod. */
    private static final float ROD_RED = 0.2f, ROD_GREEN = 0.16f, ROD_BLUE = 0.13f;

    /** How far a moulding round a door's panel stands out of the leaf and how wide it is, in tiles. */
    private static final float MOULDING_PROUD = 0.011f, MOULDING_WIDE = 0.024f;
    /** A room's light is the same from every side, and shows no relief: a moulding is drawn this much darker than the leaf, as its own shadow makes it, and a panel's field this much. */
    private static final float MOULDING_SHADE = 0.8f, FIELD_SHADE = 0.93f;
    /** Where the panels of a door are, as shares of the part of the leaf they are on: across, then up a whole leaf, then up what is below a light. */
    private static final float[] PANELS_ACROSS = {0.14f, 0.455f, 0.545f, 0.86f};
    private static final float[] PANELS_UP = {0.1f, 0.37f, 0.45f, 0.8f, 0.86f, 0.95f}, PANELS_UP_BELOW = {0.16f, 0.86f};
    /** A boarded door: the gap between two boards, how far its ledges stand out and how high each is, in tiles, and where up the leaf they are. */
    private static final float BOARD_GAP = 0.005f, BOARD_USUAL = 0.16f, LEDGE_PROUD = 0.014f, LEDGE_HIGH = 0.085f;
    private static final float[] LEDGES_AT = {0.12f, 0.5f, 0.88f};
    /** How much darker than the boards the open joint between two is. */
    private static final float JOINT_SHADE = 0.4f;
    /** A door's knob: how high up it is in levels, how far in from the leaf's end as a share of the leaf, how thick and how far out, in tiles. */
    private static final float KNOB_HIGH = 0.4f, KNOB_IN = 0.1f, KNOB_ROUND = 0.017f, KNOB_OUT = 0.034f, KNOB_NECK = 0.4f;
    /** The picture's number for bare metal: see the furniture's surfaces in the world's fragment program. */
    private static final float BARE_METAL = 21f;

    /** How far a window's frame stands out of the wall's skins and laps over the edge of its opening, in tiles. */
    private static final float FRAME_PROUD = 0.006f, FRAME_LAP = 0.012f;
    /** A bar is this much as deep as it is wide, between the least and the most, in tiles. */
    private static final float BAR_DEPTH = 0.55f, BAR_THINNEST = 0.007f, BAR_DEEPEST = 0.022f;
    /** How far the sill stands out of the wall out of doors, how far it runs past the frame at each end and how thick it is, in tiles. */
    private static final float SILL_OUT = 0.035f, SILL_PAST = 0.02f, SILL_THICK = 0.022f;
    /** A frame that comes down to the floor has a threshold and no sill: how high its foot has to be for a sill, in levels. */
    private static final float SILL_LOWEST = 0.08f;
    /** How wide a shard of a broken pane is at the bar that holds it, at least and at most, in tiles. */
    private static final float SHARD_NARROWEST = 0.012f, SHARD_WIDEST = 0.085f;
    /** How far to either side of the middle of its foot a shard's point may stand, in widths of the foot. */
    private static final float SHARD_LEAN = 1.3f;
    /** A frame redder than it is blue by this much is bare wood, and anything else is painted. */
    private static final float WOODEN = 0.12f;

    /** A member of a frame wider than this each way, in tiles, is a piece of the wall the window is in and keeps the wall's own picture on its faces. */
    private static final float WALL_MEMBER = 0.14f;

    /** A number from 0 to 1 that is the same every time for the same seed. */
    private static float chance(int seed) {
        seed = (seed ^ seed >>> 15) * 0x2C1B3C6D;
        seed = (seed ^ seed >>> 12) * 0x297A2D39;
        return ((seed ^ seed >>> 15) >>> 8) / (float) (1 << 24);
    }

    /**
     * Whether a lamp burns in the room a square is in: the room's own light, which the game's
     * lighting sets from the switch and the power, or a lamp stood in the room. A chunk's sum
     * holds it for every lit square of a room: the game gives a room its lights, lists its
     * lamps and switches both with no mark on the room's chunks, and in daylight no square's
     * light changes with them either, so nothing else says that a window's pane is now lit.
     */
    private static boolean lampBurns(IsoCell cell, IsoGridSquare square, java.util.List<IsoLightSource> lamps) {
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

    /**
     * Whether a lamp burns in the room a square is in and lights the square. How bright the
     * square is does not say so alone. The game keeps every square at full daylight for an
     * hour after the picture's sky is the night's, and it lights whole squares in the beam of
     * a torch or of a car's headlights.
     */
    static boolean lamplit(IsoCell cell, IsoGridSquare square, int playerIndex) {
        if (square.getRoom() == null || !square.isSeen(playerIndex)) {
            return false;
        }
        ColorInfo light = square.lighting[playerIndex].lightInfo();
        return Math.max(light.r, Math.max(light.g, light.b)) > LAMPLIT && lampBurns(cell, square, cell.getLamppostPositions());
    }

    /** How high a wall has to stand short of its level for its top to be closed: a full wall's top is under the floor or the roof over it. */
    private static final float CAPPED_BELOW = 0.97f;
    /** Where along a wall its colour is looked for, for the faces that close it. */
    private static final float[] CAP_COLOUR_AT = {0.5f, 0.12f, 0.88f};

    /** Wallpaper with a figure on it, which the picture prints sprig by sprig. */
    private static final float FIGURED_PAPER = 70f;
    /** A floor of boards that run along y: the picture draws it as WOOD with its grain turned. */
    private static final float WOOD_ALONG_Y = 71f;

    /** For each of the two edges a wall can stand on: the frame origin the sprite was read with, then the opening. */
    private static final int OPENING_FLOATS = 7, OPENING_ORIGIN = 0, OPENING_START = 2, OPENING_END = 3, OPENING_FOOT = 4, OPENING_HEAD = 5, OPENING_TOP = 6;
    /** Where along a wall its height is read, and the lowest a wall's top is looked for, in levels. */
    private static final float[] TOP_READ_AT = {0.15f, 0.5f, 0.85f};
    private static final float LOWEST_TOP = 0.05f;

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
    /** How deep the board under a gable's edge is, in tiles: deep enough to cover where the wall's own pieces stop short of the roof. */
    private static final float BARGE = 0.2f;

    private static void set(float[] c, int corner, float x, float y, float h) {
        c[corner * 3] = x;
        c[corner * 3 + 1] = y;
        c[corner * 3 + 2] = h;
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

    /** With tracing on, the memory the render thread took for the picture since it was last asked. */
    private final AtomicLong drawnBytes = new AtomicLong();

    long takeDrawnBytes() {
        return drawnBytes.getAndSet(0);
    }

    /** One frame of the picture: what of the world this view shows in it, built on the logic thread and drawn on the picture's. */
    final class Frame extends TextureDraw.GenericDrawer implements Grass.Ground {
        private final float[] crownLeft = new float[Flora.CROWN_BANDS], crownRight = new float[Flora.CROWN_BANDS], crownSizes = new float[Flora.SIZES];
        /** Dev only: set round a check's third build, which makes its crowns in the chunk's own sets as every build once did. */
        private boolean crownsInline;

        /** The chunk builds this frame draws. */
        private final ArrayList<ChunkMeshes> shown = new ArrayList<>();
        /** Whether any of them has water on it, and the plane that water lies in round the eye: see WaterMirror. */
        private boolean waterShown;
        private final float[] waterPlane = new float[4];
        private Models.Instances placed;
        private final SpriteGeometry geometry = new SpriteGeometry();
        private final SpriteGeometry.Frame frame = new SpriteGeometry.Frame();

        private final ViewCamera.Pose camera = new ViewCamera.Pose();
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
        int characters;
        private final SkyState sky = new SkyState();
        private float fogEnd;
        /** The middle of the map the game has loaded, and how far from it the map reaches every way, in tiles. */
        private float loadedX, loadedY, loadedWithin;
        /** How much light the eye lets in this frame: see adaptEye. */
        private float exposure;
        private IsoPlayer viewer;
        private IsoCell cell;
        private int playerIndex;
        private int lowestLevel;
        private float cutFrom = Float.MAX_VALUE;
        /** Whether the frame is built as the game's own view, which leaves out and cuts away what the game does. */
        private boolean gamesView;
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
        private float bandAway;
        /** Whether only what grows on the ground is being added: see latestGrown. */
        private boolean growing;
        private int boxes;
        private int furnished;
        private int spreadFaces;
        private int lifted;
        private int chunksDrawn, squaresLoaded, squaresSeen, sprites, triangles;
        private int roofsNorthSouth, roofsWestEast, roofsFlat, roofsUnclear, roofsFolded;
        /** The gable walls made in the last build: in the sprite of the building's face, in its colour alone, and those left open for want of either. */
        private int gablesFaced, gablesPainted, gablesBare;
        private int floorLayer, westLayer, northLayer, eastLayer, southLayer;
        private final float[] openLight = new float[3];
        private final ColorInfo unseenLight = new ColorInfo();
        /** Where TorchLess writes a square's light when the viewer's torch is on the square. */
        private final ColorInfo torchless = new ColorInfo();
        private final float[] box = new float[4];
        private int overLayer;
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
        /** Whether anything of a wall's kind, a fence or a low wall too, stands on the north and the west edge of the square being added. */
        private boolean edgeNorth, edgeWest;
        private float steepest;
        private String steepestSaid = "", roofName = "";
        private final float[] eave = new float[12];
        private final float[] folded = new float[9];
        private final float[] heldLift = new float[4];
        private final Flora flora = new Flora();
        /** Which squares of the chunk being built, and of Flora.WOOD_RING more on every side, hold a tree. */
        private final boolean[] treed = new boolean[WOOD_SPAN * WOOD_SPAN];
        /** How deep in fallen leaves each corner of the chunk being built is: see Flora.wooded. */
        private final float[] wooded = new float[Terrain.CORNERS * Terrain.CORNERS];
        private final float[] littered = new float[4];
        /** How many of its neighbours' grounds spreadGround last laid over a square. */
        private int spreadLayers;
        /** Whether the square being added has no floor. */
        private boolean hangsInAir;
        /** The square whose grass is being grown: where in the chunk it is, which of its sides run on into more lawn, and how much standing under trees takes from its grass. */
        private int grownLocalX, grownLocalY;
        private boolean lawnWest = true, lawnEast = true, lawnNorth = true, lawnSouth = true;
        private float grownUnder;
        /** The ground of the chunk being built and of the ring of squares round it, laid out as the lights are. */
        private final byte[] groundKind = new byte[LIGHT_SPAN * LIGHT_SPAN];
        private final IsoObject[] groundObject = new IsoObject[LIGHT_SPAN * LIGHT_SPAN];
        private final Texture[] groundTexture = new Texture[LIGHT_SPAN * LIGHT_SPAN];
        private final int[] groundRank = new int[LIGHT_SPAN * LIGHT_SPAN];
        private final int[] spreading = new int[8];
        private final float[] cover = new float[4];
        /** The place in its chunk of the square being added. */
        private int waterLocalX, waterLocalY;
        private final float[] lightRed = new float[LIGHT_SPAN * LIGHT_SPAN], lightGreen = new float[LIGHT_SPAN * LIGHT_SPAN],
                lightBlue = new float[LIGHT_SPAN * LIGHT_SPAN];
        private final byte[] lightOpen = new byte[LIGHT_SPAN * LIGHT_SPAN];
        private final float[] heldRed = new float[4], heldGreen = new float[4], heldBlue = new float[4];
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
        private boolean canopied;
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
        private boolean afresh;
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
        private IsoGridSquare here;
        /** The plain outside wall the far skin of the wall last asked about can show, or null: see farTint. */
        private WallFinish.Facing farFacing;
        /** Whether the wall being added has a door or a window in it. */
        private boolean wallOpens;
        private final SpriteGeometry.Frame held = new SpriteGeometry.Frame();
        /** Where in the chunk's lights the square beyond the wall last asked about is, or -1 when it is the same place as this side. */
        private int farLight;
        /** The roof the tile being added is part of, or null. */
        private Roofs.Roof roof;
        private float roofU, roofV;
        private final float[] reveal = new float[12];

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
            characters = 0;
            for (int i = 0; i < shown.size(); i++) {
                release(shown.get(i));
            }
            shown.clear();
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
            int agedBuilt = 0;
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

            viewer = player;
            float daylight = GameTime.getInstance().getAmbient();
            sky.capture();
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
            if (Own.taken(view.index)) {
                // A row of [Content] was switched: every build holds what the rows were, and so does every roof read, which leaves a parasol out by its row.
                forgetAll();
                Roofs.forget();
            }
            if (crownedToSight != Quality.crownsToSight()) {
                // Every build in the last band holds whether its trees were given crowns and boles.
                crownedToSight = Quality.crownsToSight();
                forgetAll();
            }
            if (cell != builtIn.get()) {
                // Another save, loaded without leaving the game: every build is of the world before.
                builtIn = new WeakReference<>(cell);
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
                Lights.forget();
                Roofs.forget();
                WallFinish.forget();
                Terrain.forget();
            }
            mainsOn = IsoWorld.instance.isHydroPowerOn();
            long goesBy = sum(sum(sum(SUM_FROM, Float.floatToRawIntBits(unseenRed)), Float.floatToRawIntBits(unseenGreen)), Float.floatToRawIntBits(unseenBlue));
            goesBy = sum(sum(goesBy, mainsOn ? 1 : 0), Float.floatToRawIntBits(Core.tileScale));
            glass.found = false;
            if (!fromOutside && !seated && drawnAspect > 0f && Quality.shadedAndLit()) {
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

            // The loaded map is a square of chunks, and it ends in nothing. The picture fades into the
            // sky by the round that just fits inside it, so the edge is never seen. The square is not
            // always about the player: the game keeps it ahead of a vehicle, a tile for every 5 km/h,
            // and the road ahead of a car at speed is there to be seen for further than what is behind.
            loadedWithin = IsoChunkMap.chunkGridWidth * CHUNK_TILES / 2f;
            loadedX = map.getWorldXMinTiles() + loadedWithin;
            loadedY = map.getWorldYMinTiles() + loadedWithin;
            fogEnd = Quality.sight();
            if (!fromOutside) {
                farLand.tend(pose.eyeX, pose.eyeY);
            }
            float cosCone = fromOutside ? -1f : cosDrawnCone(pose, flat);
            // A driver's mirror shows a narrow cone straight behind the vehicle, and what is in it is drawn as well.
            boolean mirrored = !fromOutside && seated && Cabins.wheel(view.ride) != null;
            float behindX = -view.ride.aheadX, behindY = -view.ride.aheadY;
            // Looking straight up or down the view faces no way along the ground, and nothing is left out.
            float forwardX = cosCone > -1f ? pose.forwardX / flat : 0f, forwardY = cosCone > -1f ? pose.forwardY / flat : 0f;

            mapWx = Math.floorDiv(map.getWorldXMinTiles(), CHUNK_TILES);
            mapWy = Math.floorDiv(map.getWorldYMinTiles(), CHUNK_TILES);
            mapSide = IsoChunkMap.chunkGridWidth;
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
            int swept = 0;
            for (Crowns.Work back = crownsBack.poll(); back != null; back = crownsBack.poll()) {
                land(back);
            }
            for (int step = 0; step < walk.length; step += 2) {
                {
                    int gridX = eyeGridX + walk[step], gridY = eyeGridY + walk[step + 1];
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
                    if (distance > CASTS_SHADOWS && toChunkX * forwardX + toChunkY * forwardY < cosCone * distance - CHUNK_REACH
                            && !(mirrored && toChunkX * behindX + toChunkY * behindY >= COS_MIRROR_CONE * distance - CHUNK_REACH)) {
                        continue;
                    }
                    chunksDrawn++;
                    int slot = (chunk.wx & (TABLE_SIDE - 1)) * TABLE_SIDE + (chunk.wy & (TABLE_SIDE - 1));
                    ChunkMeshes current = latest[slot];
                    boolean missing = current == null || current.wx != chunk.wx || current.wy != chunk.wy;
                    // A chunk that is only due to be made again can wait a frame when many fall due
                    // together: its old build is still good enough to show. Without this the frames
                    // on which dozens fall due are several times as long as the rest.
                    int every = missing ? 0 : buildEvery(distance, slot);
                    // On the frame the view comes on, after a jump across the map or as the head turns
                    // fast, hundreds of chunks want building at once, and built in one frame they held it
                    // for most of a second. What is near is built whatever it takes. The rest is built
                    // until the frame has spent its time on building, and the others come on the next frames.
                    boolean late = (rehearsing || distance >= BUILT_WHATEVER_IT_TAKES) && System.nanoTime() - start > BUILD_NANOS_A_FRAME / sharing;
                    // One chunk a frame is built however late the frame is: on a PC where what is near takes all
                    // of the frame's time, nothing further off would otherwise ever be built.
                    if (missing && late && builtLate++ > 0) {
                        chunksWaiting++;
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
                    boolean quiet = telling && !missing && stands && current.signed && !current.restless && marks == current.marks && toldSoFar == current.told
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
                    boolean due = !missing && !quiet && !rehearsing && waited && frames - current.checkedFrame >= every
                            && (distance < CHUNK_TILES * 1.5f || frames - current.checkedFrame >= 2 * every && !hurried
                                    || frames - current.checkedFrame >= 4 * every && !late || !hurried && remakes++ < REMAKES_A_FRAME);
                    // On arriving somewhere the map round the eye is still loading, and each chunk is found changed again
                    // and again. One found changed is made again as it falls due without being read through, and right by
                    // the eye that is every frame: the nine chunks round it were all made again on every frame for the
                    // first seconds, which with builds slowed fourfold was seven frames a second for five seconds. While
                    // a chunk is new, a quarter of a second between two builds of it is soon enough, and
                    // the build it has stands for as long as the eye is in its band, whether or not the eye moves.
                    if (due && stands && (frames < current.summedFrom || start < current.summedFromNanos)
                            && start - current.bornNanos < SETTLES_IN_NANOS
                            && start - current.builtNanos < KEPT_WHILE_SETTLING_NANOS) {
                        due = false;
                    }
                    // Nothing but light has been marked on it since it was last read, and the frame has
                    // spent its time: the build it has is right but for that light, and is shown a frame or a few longer.
                    if (due && telling && stands && current.signed && current.things == things && since < LIT_WAITS_NANOS
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
                        fresh.placed.clear();
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
                        noKerb();
                        addBacks(chunk, chunkX, chunkY);
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
                        }
                        reading.copyTo(fresh.seen);
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
                    shown.add(current);
                    current.water = Terrain.holdsWater(chunk.wx, chunk.wy);
                    waterShown |= current.water;
                    ChunkMeshes crowned = latestCrowned[slot];
                    if (crowned != null && crowned.wx == chunk.wx && crowned.wy == chunk.wy) {
                        crowned.users++;
                        shown.add(crowned);
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
                        noKerb();
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
                        shown.add(grown);
                    }
                }
            }
            // Where each standing character is, for the aim to be traced against. Read from the
            // game's own list every frame: characters move, and a chunk's build may be frames old.
            for (IsoMovingObject mover : cell.getObjectList()) {
                if (mover instanceof IsoGameCharacter character && character != viewer
                        && character.getAlpha(playerIndex) > CHARACTER_SHOWN_ALPHA && !character.isInvisible()) {
                    float toX = character.getX() - pose.eyeX, toY = character.getY() - pose.eyeY;
                    if (toX * toX + toY * toY <= REACH * REACH) {
                        addCharacter(character);
                    }
                }
            }
            if (!openLightTaken || start - openLightTakenAt >= OPEN_LIGHT_TAKEN_EVERY_NANOS) {
                seenOutside.clear();
                for (int i = 0; i < shown.size(); i++) {
                    ChunkMeshes each = shown.get(i);
                    if (!each.grown && each.crowns == null && each.seen != null) {
                        each.seen.addTo(seenOutside);
                    }
                }
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
            for (int i = 0; i < shown.size(); i++) {
                triangles += shown.get(i).triangles() + shown.get(i).placed.triangles();
            }
            waitedLast = chunksWaiting;
            buildMillis = (System.nanoTime() - start) / 1e6f;
            tollFrames++;
            tollQuiet += chunksQuiet;
            for (int i = 0; i < tollBuilt.length; i++) {
                tollBuilt[i] += builtBecause[i];
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
        private boolean madeAt(int x, int y, int z) {
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
        private IsoGridSquare squareAt(int chunkX, int chunkY, int x, int y, int z) {
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
            noKerb();
            addBacks(chunk, chunkX, chunkY);
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
                noKerb();
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
            noKerb();
            addBacks(chunk, chunkX, chunkY);
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
                noKerb();
                addBacks(chunk, chunkX, chunkY);
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
            noKerb();
            addBacks(chunk, chunkX, chunkY);
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
                        float drop = z == 0 && kind != Terrain.WATER && kerbs != null ? kerbOver(kind, localX, localY) : noKerb();
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
        private int alike;

        /**
         * Whether what is being added is within a reach of the eye, or right at it when that
         * counts as within, by the band the chunk is built for. Every question a build asks of
         * its band comes through here or through alikeOnly, and each takes out of alike the bands
         * that would have answered otherwise: a street's chunk asks none, and is one build from
         * any distance.
         */
        private boolean within(float reach, boolean atCounts) {
            boolean within = bandAway < reach || atCounts && bandAway == reach;
            for (int band = 0; band < BAND_AWAY.length; band++) {
                if ((BAND_AWAY[band] < reach || atCounts && BAND_AWAY[band] == reach) != within) {
                    alike &= ~(1 << band);
                }
            }
            return within;
        }

        /** Says that what is being added is made by the band's own distance: the build is then another in every other band. */
        private void alikeOnly() {
            alike &= 1 << building.band;
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
                growGrass(x, y, localX, localY);
            }
            strew(x, y, localX, localY, toX * toX + toY * toY);
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
            lampsNear.clear();
            Stack<IsoLightSource> lamps = cell.getLamppostPositions();
            for (int i = 0; i < lamps.size(); i++) {
                IsoLightSource lamp = lamps.get(i);
                if (lamp.active && lamp.x + lamp.radius >= chunkX - 1 && lamp.x - lamp.radius <= chunkX + CHUNK_TILES && lamp.y + lamp.radius >= chunkY - 1
                        && lamp.y - lamp.radius <= chunkY + CHUNK_TILES) {
                    lampsNear.add(lamp);
                }
            }
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
                    if (!seen && !(gamesView && square.getRoomID() != -1L)) {
                        light = unseenLight;
                    } else if (seen && inChunk) {
                        squaresSeen++;
                        if (outside && tallying) {
                            reading.add(light.r, light.g, light.b);
                        }
                    }
                    lightRed[at] = light.r;
                    lightGreen[at] = light.g;
                    lightBlue[at] = light.b;
                    lightOpen[at] = (byte) (outside ? 1 : 0);
                    // The game has no lamp in a canopy. A lit one lights what is under it.
                    if (outside && z >= 0 && underLitCanopy(chunkX, chunkY, chunkX + i - 1, chunkY + j - 1, z)) {
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
            if (!isFloor && addHung(object, over, texture, x, y, z)) {
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
                        addEdge(mesh, texture, false, false, x, y, z, westLayer++, 0f, 1f);
                    } else {
                        addEdge(mesh, texture, false, false, x, y, z, northLayer++, 1f, 0f);
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

        private void frameOf(IsoObject object, Texture texture) {
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
                    // Grass does not spread down a kerb onto the street.
                    geometry.kerbKeep = SpriteGeometry.LAND;
                    spreadFaces++;
                    layers.lie(SpriteGeometry.LAYERS_UP, floorLayer + spread);
                    geometry.addFace(laid.get(groundTexture[at].getID()), frame, x, y, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
                    layers.none();
                    geometry.kerbKeep = SpriteGeometry.BOTH;
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
                // The game's sheets all end in a number. A map's own tile need not, and is then of its sheet's first row.
                String number = name.substring(cut + 1);
                int row = !number.isEmpty() && number.length() <= 6 && number.chars().allMatch(Character::isDigit) ? Integer.parseInt(number) / 16 : 0;
                known = new int[] {1 + (name.substring(0, Math.max(cut, 0)).hashCode() & 0xffff) * 64 + (row >= 1 && row <= 3 ? 32 + row : row)};
                ranks.put(sprite, known);
            }
            return known[0];
        }

        /**
         * Grass the game paints flat on the ground stands up near the eye: every square of open
         * grass grows its own plants, each the colour of the ground it stands on. See Grass.
         */
        private void growGrass(int x, int y, int localX, int localY) {
            int own = (localX + 1) * LIGHT_SPAN + localY + 1;
            if (!isLawn(own)) {
                return;
            }
            // Over more grass a blade may hang past its square. Over a path it is untidy, and
            // through the wall of a house it is wrong, so on a side where the lawn ends the
            // plants keep in from the edge.
            lawnWest = isLawn(own - LIGHT_SPAN);
            lawnEast = isLawn(own + LIGHT_SPAN);
            lawnNorth = isLawn(own - 1);
            lawnSouth = isLawn(own + 1);
            grownLocalX = localX;
            grownLocalY = localY;
            grownUnder = 0.85f;
            frameOf(groundObject[own], groundTexture[own]);
            // Looking no way, as long grass is grown: a lawn left out behind the view was bare when the head turned to it.
            Grass.lawn(geometry, laid.get(groundTexture[own].getID()), frame, x, y, camera.eyeX, camera.eyeY, 0f, 0f,
                    lawnWest ? 0f : LAWN_KEEPS_IN, lawnEast ? 1f : 1f - LAWN_KEEPS_IN, lawnNorth ? 0f : LAWN_KEEPS_IN, lawnSouth ? 1f : 1f - LAWN_KEEPS_IN, this);
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
            return (1f - grownUnder * woodedAt(grownLocalX, grownLocalY, a, b)) * (0.2f + 0.8f * edge * edge * (3f - 2f * edge));
        }

        @Override
        public boolean grows(float a, float b) {
            return geometry.kerb == null || !geometry.onStreet(a, b);
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
                layers.lie(SpriteGeometry.LAYERS_UP, floorLayer + spreadLayers + 1);
                Flora.addLitter(cards, x, y, geometry.lift, littered, frame);
                layers.none();
            }
            float deep = (littered[0] + littered[1] + littered[2] + littered[3]) / 4f;
            boolean tree = treed[(localX + Flora.WOOD_RING) * WOOD_SPAN + localY + Flora.WOOD_RING];
            if (away2 < FERN_REACH * FERN_REACH && !tree && isLawn(own)) {
                int ferns = Flora.ferns(x, y, deep);
                for (int fern = 0; fern < ferns; fern++) {
                    int seed = Flora.mix(x, y, 40 + fern);
                    float a = 0.15f + 0.7f * Flora.unit(seed, 0), b = 0.15f + 0.7f * Flora.unit(seed, 8);
                    if (geometry.kerb != null && geometry.onStreet(a, b)) {
                        continue;
                    }
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
                    if (geometry.kerb != null && geometry.onStreet(a, b)) {
                        return;
                    }
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
                if (geometry.kerb != null && geometry.onStreet(a, b)) {
                    continue;
                }
                Flora.addFlower(cards, x + a, y + b, geometry.liftAt(a, b), lowest + taller * Flora.unit(seed, 16), least * (1f + 0.65f * Flora.unit(seed, 24)),
                        Flora.unit(seed, 4) * 2.0 * Math.PI, Flora.flowerKind(x, y, flower), (seed >>> 12) % Flora.FLOWER_THINNINGS, frame);
            }
        }

        /** Where the kerbs of the chunk being built run, as Kerbs keeps it. Null where it has no street. */
        private float[] kerbs;
        /** Whether the square being added has a kerb on it or along its edge. */
        private boolean kerbed;
        /** The square beside the one being added whose ground lies beyond the kerb that crosses it, as a place in groundKind, or -1. */
        private int beyondKerb;

        /** Whether what is being laid is grass the map paints over the edge of a street's square. */
        private boolean vergeOver;
        /** Whether the sprite being added over an object's own is a marking painted on the ground: see RoadPaint. */
        private boolean paintOver;

        /** For a square with no kerb to do with it. */
        private float noKerb() {
            geometry.kerb = null;
            kerbed = false;
            return 0f;
        }

        /**
         * How far a square of the ground lies below its corners, which is by a kerb where it is on
         * the street's side of one. A kerb does not keep to the edges of the squares, so a square
         * it crosses is in two parts, and geometry is told of it: the square then lies at the
         * height of what it is itself, street or land, and the part beyond the kerb a kerb lower
         * or higher.
         */
        private float kerbOver(byte kind, int localX, int localY) {
            boolean street = false, land = false, onLine = false;
            float beyondX = 0f, beyondY = 0f;
            int beyond = 0;
            for (int p = 0; p <= Kerbs.FINE; p++) {
                for (int q = 0; q <= Kerbs.FINE; q++) {
                    float value = kerbs[(localX * Kerbs.FINE + p) * Kerbs.POINTS + localY * Kerbs.FINE + q];
                    street |= value > 0f;
                    land |= value < 0f;
                    onLine |= value == 0f;
                    if (value != 0f && value > 0f != (kind == Terrain.STREET)) {
                        beyondX += p;
                        beyondY += q;
                        beyond++;
                    }
                }
            }
            geometry.kerb = null;
            if (!street || !land) {
                // A kerb along the square's edge stands on the street's square.
                kerbed = street && onLine;
                return street ? Terrain.KERB : 0f;
            }
            kerbed = true;
            geometry.kerb = kerbs;
            geometry.kerbX = localX;
            geometry.kerbY = localY;
            geometry.kerbStreet = kind == Terrain.STREET;
            // The ground beyond the kerb is the ground of the square beside this one that is nearest that part.
            beyondKerb = -1;
            float nearest = Float.MAX_VALUE;
            int own = (localX + 1) * LIGHT_SPAN + localY + 1;
            for (int di = -1; di <= 1; di++) {
                for (int dj = -1; dj <= 1; dj++) {
                    int at = own + di * LIGHT_SPAN + dj;
                    byte beside = groundKind[at];
                    boolean other = geometry.kerbStreet ? beside == Terrain.NATURAL || beside == Terrain.MADE : beside == Terrain.STREET;
                    if (!other || groundTexture[at] == null) {
                        continue;
                    }
                    float awayX = beyondX / beyond / Kerbs.FINE - 0.5f - di, awayY = beyondY / beyond / Kerbs.FINE - 0.5f - dj;
                    if (awayX * awayX + awayY * awayY < nearest) {
                        nearest = awayX * awayX + awayY * awayY;
                        beyondKerb = at;
                    }
                }
            }
            return geometry.kerbStreet ? Terrain.KERB : 0f;
        }

        /**
         * The face of the kerb on a square: a strip standing along the line where the kerbs'
         * field crosses nothing, from the land's height down to the street's. Each block between
         * four points of the field is two triangles, and the line runs straight through each.
         */
        private void addKerb(int x, int y, int localX, int localY) {
            SpriteGeometry.Mesh mesh = solid.get(PLAIN);
            // The colour given is the light on it. What colour the thing is, the picture knows by
            // what it is made of.
            mesh.open = frame.open + 32f * KERB_STONE;
            for (int p = 0; p < Kerbs.FINE; p++) {
                for (int q = 0; q < Kerbs.FINE; q++) {
                    int at = (localX * Kerbs.FINE + p) * Kerbs.POINTS + localY * Kerbs.FINE + q;
                    float here = kerbs[at], across = kerbs[at + Kerbs.POINTS], opposite = kerbs[at + Kerbs.POINTS + 1], along = kerbs[at + 1];
                    boolean street = here > 0f;
                    if (street == across > 0f && street == opposite > 0f && street == along > 0f) {
                        continue;
                    }
                    addKerbLength(mesh, x, y, localX, localY, p, q, here, 1, 0, across, 1, 1, opposite);
                    addKerbLength(mesh, x, y, localX, localY, p, q, here, 1, 1, opposite, 0, 1, along);
                }
            }
        }

        private final float[] kerbEnds = new float[4];

        /** The length of kerb in one triangle of a block of the field: its corner (p, q) and two more, each with the field there. */
        private void addKerbLength(SpriteGeometry.Mesh mesh, int x, int y, int localX, int localY, int p, int q,
                float here, int p1, int q1, float first, int p2, int q2, float second) {
            int ends = 0;
            ends = kerbEnd(ends, 0, 0, here, p1, q1, first);
            ends = kerbEnd(ends, p1, q1, first, p2, q2, second);
            ends = kerbEnd(ends, p2, q2, second, 0, 0, here);
            if (ends != 2 || kerbEnds[0] == kerbEnds[2] && kerbEnds[1] == kerbEnds[3]) {
                return;
            }
            float[] lift = geometry.lift;
            // The square's own height is the street's where it is street, or all on the street's side.
            float raise = geometry.kerb == null || geometry.kerbStreet ? Terrain.KERB : 0f;
            for (int corner : KERB_CORNERS) {
                int end = corner >> 1;
                float a = (p + kerbEnds[2 * end]) / Kerbs.FINE, b = (q + kerbEnds[2 * end + 1]) / Kerbs.FINE;
                float top = (lift[0] * (1f - a) + lift[1] * a) * (1f - b) + (lift[3] * (1f - a) + lift[2] * a) * b + raise;
                // It faces the street, and round a corner turns with the line.
                float step = 1f / Kerbs.FINE;
                float towardsX = Kerbs.at(kerbs, localX + a + step, localY + b) - Kerbs.at(kerbs, localX + a - step, localY + b);
                float towardsY = Kerbs.at(kerbs, localX + a, localY + b + step) - Kerbs.at(kerbs, localX + a, localY + b - step);
                float length = (float) Math.sqrt(towardsX * towardsX + towardsY * towardsY);
                if (length < 1e-4f) {
                    // Hard against a house the field is level on the house's side, and the length's own lie says which way it faces.
                    towardsX = kerbEnds[3] - kerbEnds[1];
                    towardsY = kerbEnds[0] - kerbEnds[2];
                    float streetP = here > 0f ? 0f : first > 0f ? p1 : p2, streetQ = here > 0f ? 0f : first > 0f ? q1 : q2;
                    if ((streetP - kerbEnds[0]) * towardsX + (streetQ - kerbEnds[1]) * towardsY < 0f) {
                        towardsX = -towardsX;
                        towardsY = -towardsY;
                    }
                    length = (float) Math.sqrt(towardsX * towardsX + towardsY * towardsY);
                }
                mesh.face(towardsX / length, towardsY / length, 0.9f);
                mesh.add(x + a, y + b, (corner & 1) == 0 ? top - Terrain.KERB - 0.02f : top, 0f, 0f, frame.red, frame.green, frame.blue);
            }
        }

        /** Notes where the kerb's line crosses one side of a triangle, if it does: between a point on the street's side and one that is not. */
        private int kerbEnd(int ends, int p0, int q0, float from, int p1, int q1, float to) {
            if (from > 0f == to > 0f || ends == 2) {
                return ends;
            }
            float t = from / (from - to);
            kerbEnds[2 * ends] = p0 + (p1 - p0) * t;
            kerbEnds[2 * ends + 1] = q0 + (q1 - q0) * t;
            return ends + 1;
        }

        /**
         * The ground beyond a kerb that crosses a square: the pavement's on the part of a street's
         * square the kerb leaves standing, and the street's on the part of a pavement's square it
         * takes. It is the ground of the square beside, laid here as far as the kerb.
         */
        private void addBeyondKerb(int x, int y) {
            IsoObject floor = groundObject[beyondKerb];
            Texture texture = groundTexture[beyondKerb];
            PropertyContainer properties = floor.getSprite().getProperties();
            frameOf(floor, texture);
            frame.material = Ground.of(properties.get("FloorMaterial"), properties.get("FootstepMaterial"), true);
            if (frame.material == 0f) {
                frame.material = materialOf(floor.getSprite().getName(), true);
            }
            geometry.kerbKeep = geometry.kerbStreet ? SpriteGeometry.LAND : SpriteGeometry.STREET;
            int flat = flatOf(texture, Flats.FLOOR, 0f, 0f, 0f);
            frame.flat = flat != 0 ? SpriteGeometry.FLAT_FLOOR : 0;
            if (frame.material == WOOD && Flats.alongY(flat)) {
                frame.material = WOOD_ALONG_Y;
            }
            geometry.addFace(solid.get(flat != 0 ? flat : texture.getID()), frame, x, y, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
            frame.flat = 0;
            geometry.kerbKeep = SpriteGeometry.BOTH;
            frame.material = 0f;
        }

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
            float away = bandAway;
            // Near the eye it is grown plant by plant. Far off, where a blade is finer than the
            // picture can draw, it is cards with the blades cut out of them, and between the two
            // the cards rise out of the ground as the plants sink into it.
            // Past where the plants end and the flowers too, it is the same cards from any distance.
            if (within(Grass.RANK_ENDS, false) | within(FLOWER_REACH, false)) {
                alikeOnly();
            }
            if (away < Grass.RANK_ENDS) {
                lawnWest = lawnEast = lawnNorth = lawnSouth = true;
                grownLocalX = waterLocalX;
                grownLocalY = waterLocalY;
                grownUnder = 0.7f;
                // Grown as from an eye the band's distance off and looking no way: none of it is left out as behind the view.
                Grass.rank(geometry, mesh, frame, x, y, x + 0.5f - away, y + 0.5f, 0f, 0f, lowA, highA, lowB, highB, this);
            }
            float carded = Math.clamp((away - Grass.RANK_THINS) / (Grass.RANK_ENDS - Grass.RANK_THINS), 0f, 1f);
            for (int tuft = 0; tuft < LONG_TUFTS && carded > 0f; tuft++) {
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
                float tall = (0.45f + 0.5f * (hash >>> 24 & 0xff) / 255f) * (out > 0f ? 1f - 1.8f * out : 1f) * carded;
                float vigour = 1f - 0.7f * woodedAt(waterLocalX, waterLocalY, Math.clamp(a, 0f, 1f), Math.clamp(b, 0f, 1f));
                geometry.addTuft(mesh, frame, x, y, a, b, (float) Math.cos(turn), (float) Math.sin(turn), 0.34f, tall, SpriteGeometry.LONG_GRASS, vigour);
            }
            if (away < FLOWER_REACH) {
                // Flowers in long grass stand as tall as it does.
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

        /**
         * The wires a sprite of overhead wire shows, each hung where it is painted over the map
         * and as high as its span has it there: a wire is held at the insulators of two poles and
         * hangs between them, whatever the land under it does (Spans). The game's view cannot tell
         * how high a line in the air is, only where it is drawn, and at the height of the
         * insulators the lines of a run come out over their poles' arms, each square's meeting
         * the next square's end to end. False, adding nothing, for a sprite that is no wire: a
         * length of the pole.
         */
        private boolean addWires(Texture texture, String name, int x, int y, int z) {
            float[] lines = wiresOn(texture, name, frame);
            long square = (long) x << 38 | (long) y << 16 | (long) z << 12 | (long) Poles.index(name) << 4;
            for (int i = 0; i < lines.length; i += 4) {
                long key = square | i / 4;
                float[] pieces = strung.get(key);
                if (pieces == null) {
                    hungOn = z;
                    pieces = spans.find(x, y, x + lines[i], y + lines[i + 1], x + lines[i + 2], y + lines[i + 3]);
                    strung.put(key, pieces);
                    if (FirstPerson.TRACE) {
                        Log.info("wire span: " + x + "," + y + "," + z + " " + name + " line " + i / 4 + " " + Arrays.toString(pieces) + " eye "
                                + Math.round(Math.hypot(x - lastEyeX, y - lastEyeY)));
                    }
                }
                wantsMap |= Spans.wanting(pieces);
                float held = (z + Wires.UP) * ViewCamera.LEVEL_HEIGHT;
                for (int piece = 0; piece < pieces.length; piece += Spans.PIECE) {
                    int from = piece + Spans.FROM, to = piece + Spans.TO;
                    float fromUp = held + landAt(pieces[from + Spans.END_LAND], pieces[from + Spans.END_LAND + 1]);
                    float toUp = held + landAt(pieces[to + Spans.END_LAND], pieces[to + Spans.END_LAND + 1]);
                    float sag = Spans.sag(Spans.span(pieces, piece));
                    for (int end = 0; end < 2; end++) {
                        float endX = pieces[piece + end * 2], endY = pieces[piece + end * 2 + 1], along = Spans.along(pieces, piece, endX, endY);
                        float straight = fromUp + (toUp - fromUp) * along;
                        float up = Math.max(straight - Spans.hangs(along, sag), landAt(endX, endY) + Spans.CLEAR);
                        set(wireEnds, end, endX, endY, up);
                        wireHangs[end] = Math.max(0f, straight - up);
                    }
                    cable(wireEnds[0], wireEnds[1], wireEnds[2], wireHangs[0], wireEnds[3], wireEnds[4], wireEnds[5], wireHangs[1], Spans.swing(pieces, piece));
                }
            }
            return lines.length > 0;
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
                    throw new IllegalStateException("the banner's length " + length.getSprite().getName() + " at " + x + "," + y + "," + hungOn
                            + " paints nothing at the end of its square, so where its cable is made fast cannot be read");
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

        /** The wires a sprite shows, as Wires reads them off it in a frame set for it. Empty for a sprite that is no wire. */
        private float[] wiresOn(Texture texture, String name, SpriteGeometry.Frame in) {
            float[] lines = wires.get(texture);
            if (lines == null) {
                lines = Wires.hangs(name) ? Wires.read(texture::isMaskSet, in) : Wires.NONE;
                wires.put(texture, lines);
                if (FirstPerson.TRACE) {
                    Log.info("wires read off " + texture.getName() + ": " + Arrays.toString(lines));
                }
            }
            return lines;
        }

        /** The level the wire hangs on whose span is being found, and the map the spans are found on. */
        private int hungOn;
        private final SpriteGeometry.Frame hungFrame = new SpriteGeometry.Frame();
        private final Spans spans = new Spans(new Spans.Hung() {
            @Override
            public int lines(int x, int y, float[] out) {
                int gridX = Math.floorDiv(x, CHUNK_TILES) - mapWx, gridY = Math.floorDiv(y, CHUNK_TILES) - mapWy;
                if (gridX < 0 || gridY < 0 || gridX >= mapSide || gridY >= mapSide) {
                    return -1;
                }
                IsoChunk hungIn = chunkMap.getChunk(gridX, gridY);
                if (hungIn == null || !hungIn.loaded) {
                    return -1;
                }
                IsoGridSquare square = cell.getGridSquare(x, y, hungOn);
                // As a build takes a sprite for wire: one hung on a square of air.
                if (square == null || square.getFloor() != null) {
                    return 0;
                }
                int count = 0;
                PZArrayList<IsoObject> objects = square.getObjects();
                for (int i = 0; i < objects.size(); i++) {
                    IsoObject object = objects.get(i);
                    IsoSprite sprite = object.getSprite();
                    if (sprite == null || !Wires.hangs(sprite.getName())) {
                        continue;
                    }
                    Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
                    if (!usable(texture)) {
                        // Its picture is not there to read yet: what the wire does here is not known.
                        return -1;
                    }
                    hungFrame.scale = frame.scale;
                    WorldScene.frameOf(hungFrame, object, texture);
                    float[] lines = wiresOn(texture, sprite.getName(), hungFrame);
                    for (int line = 0; line < lines.length; line += 2) {
                        out[count++] = x + lines[line];
                        out[count++] = y + lines[line + 1];
                    }
                }
                return count;
            }

            @Override
            public int pins(int x, int y, float[] out, int at) {
                Poles.Arm arm = Poles.arm(poleOn(x, y, hungOn));
                Posts shaft = arm == null ? null : shaftOf(x, y, hungOn);
                if (shaft == null) {
                    return 0;
                }
                float axisX = x + shaft.a, axisY = y + shaft.b;
                for (int which = -1; which <= 1; which++) {
                    Poles.insulator(arm, which, axisX, axisY, shaft.half * Poles.TAPER, insulatorAt);
                    out[at++] = insulatorAt[0];
                    out[at++] = insulatorAt[1];
                    out[at++] = axisX;
                    out[at++] = axisY;
                }
                return Spans.POLE_PINS;
            }
        });

        /** Which of the chunks round the one being built the build has taken the land's height from, a bit each: see landAt. */
        private int heightsRound;
        /** The chunks further off it has taken one from, each as its two numbers in one: the span of a wire ends at a pole some chunks away. */
        private long[] heightsFar = new long[8];
        private int heightsFarCount;
        /** Whether the build has left the span of a wire unknown at an end because the map is not loaded that far. */
        private boolean wantsMap;

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
        private float landAt(float x, float y) {
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

        private final float[] wireEnds = new float[6], wireHangs = new float[2], insulatorAt = new float[2];

        /** The shaft of the pole that has a length on a square, as Posts reads the lowest of its lengths, or null. */
        private Posts shaftOf(int x, int y, int z) {
            while (z > lowestLevel && poleOn(x, y, z - 1) != null) {
                z--;
            }
            PZArrayList<IsoObject> objects = cell.getGridSquare(x, y, z).getObjects();
            for (int i = 0; i < objects.size(); i++) {
                IsoObject object = objects.get(i);
                IsoSprite sprite = object.getSprite();
                if (sprite != null && Poles.length(sprite.getName())) {
                    Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
                    return usable(texture) ? postOf(object, sprite, texture) : null;
                }
            }
            return null;
        }

        /** The sprite of the length of pole that stands on a square, by its name, or null. */
        private String poleOn(int x, int y, int z) {
            IsoGridSquare square = cell.getGridSquare(x, y, z);
            if (square == null) {
                return null;
            }
            PZArrayList<IsoObject> objects = square.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                IsoSprite sprite = objects.get(i).getSprite();
                if (sprite != null && Poles.length(sprite.getName())) {
                    return sprite.getName();
                }
            }
            return null;
        }

        private final float[] poleFrom = new float[3], poleTo = new float[3];

        /**
         * Builds a pole whole from the lowest of its lengths: one shaft from the ground to its
         * top, and on it the arm its highest length shows, with its braces and the insulators its
         * wires lie on. The lengths above the lowest add nothing more. The top is as high over the
         * land as the wires are hung, so that they meet it. See Poles.
         */
        private boolean addPole(SpriteGeometry.Mesh mesh, IsoObject object, IsoSprite sprite, Texture texture, int x, int y, int z) {
            if (z > lowestLevel && poleOn(x, y, z - 1) != null) {
                // Its foot has built it, unless its foot is a sprite Posts cannot read: then each length is left to stand as it did.
                return shaftOf(x, y, z) != null;
            }
            Posts post = postOf(object, sprite, texture);
            if (post == null) {
                return false;
            }
            int top = z;
            String highest = sprite.getName();
            for (String above = poleOn(x, y, top + 1); above != null; above = poleOn(x, y, top + 1)) {
                top++;
                highest = above;
            }
            Poles.Arm arm = Poles.arm(highest);
            float axisX = x + post.a, axisY = y + post.b, level = ViewCamera.LEVEL_HEIGHT;
            float foot = z * level + geometry.liftAt(post.a, post.b), floor = top * level + landAt(axisX, axisY);
            float head = floor + (arm == null ? Poles.LENGTH : Poles.TOP) * level, thin = post.half * Poles.TAPER;
            float u = SpriteGeometry.textureU(frame, post.a, post.b), v = SpriteGeometry.textureV(frame, post.a, post.b, 0.5f);
            mesh.open = frame.open + 32f * SpriteGeometry.TIMBER;
            mesh.sway = 0f;
            SpriteGeometry.addRound(mesh, axisX, axisY, foot, head, post.half, thin, 12, u, v, frame.red, frame.green, frame.blue);
            boxes++;
            if (arm == null) {
                return true;
            }
            float alongX = arm.alongX(), alongY = arm.alongY(), out = thin + Poles.ARM_HALF;
            float armX = axisX - alongY * out, armY = axisY + alongX * out, armUp = floor + Poles.ARM_UP * level;
            set(poleFrom, 0, armX - alongX * arm.half(), armY - alongY * arm.half(), armUp);
            set(poleTo, 0, armX + alongX * arm.half(), armY + alongY * arm.half(), armUp);
            SpriteGeometry.addStrut(mesh, poleFrom, poleTo, Poles.ARM_HALF, u, v, frame.red, frame.green, frame.blue);
            for (int side = -1; side <= 1; side += 2) {
                float at = side * arm.half() * Poles.BRACE_AT;
                set(poleFrom, 0, axisX - alongY * (thin + Poles.BRACE_HALF), axisY + alongX * (thin + Poles.BRACE_HALF), floor + Poles.BRACE_UP * level);
                set(poleTo, 0, armX + alongX * at, armY + alongY * at, armUp - Poles.ARM_HALF);
                SpriteGeometry.addStrut(mesh, poleFrom, poleTo, Poles.BRACE_HALF, u, v, frame.red, frame.green, frame.blue);
            }
            SpriteGeometry.Mesh glazed = solid.get(PLAIN, WallSides.key(Poles.INSULATOR_RED, Poles.INSULATOR_GREEN, Poles.INSULATOR_BLUE, WallSides.FLAT));
            glazed.open = frame.open;
            glazed.sway = 0f;
            float wire = floor + Wires.UP * level;
            for (int which = -1; which <= 1; which++) {
                boolean onShaft = Poles.insulator(arm, which, axisX, axisY, thin, insulatorAt);
                float atX = insulatorAt[0], atY = insulatorAt[1];
                float from = onShaft ? head : armUp + Poles.ARM_HALF, neck = onShaft ? from : from + (wire - from) * Poles.PIN_SHARE;
                if (!onShaft) {
                    SpriteGeometry.addRound(glazed, atX, atY, from, neck, Poles.PIN, Poles.PIN, 6, 0f, 0f, frame.red, frame.green, frame.blue);
                }
                SpriteGeometry.addRound(glazed, atX, atY, neck, wire, Poles.INSULATOR, Poles.INSULATOR * 0.6f, 8, 0f, 0f, frame.red, frame.green, frame.blue);
            }
            return true;
        }

        /**
         * A wire from one point to another: two strips crossed, so that it has a width from below
         * as from the side. A wire has no texel, and where a texel's place is each vertex carries
         * what the picture moves it by as it draws, see wireOff in PipelineShaders: the way
         * across the wire on the map, of a length that says which corner of which strip the
         * vertex is, 1 and 2 for the two sides of the level strip and 3 and 4 for the top and the
         * bottom of the upright one, and after that, between a tenth and nine tenths, where in
         * its swing the wire's span is (swing, 0 to 1). The picture stands a vertex off the
         * middle of the wire where it is far from the eye, so that the wire is never finer than
         * a pixel, and swings it in the wind by how far the wire hangs there below the line
         * between its insulators (hangs0, hangs1), which rides on the length of its facing as
         * a plant's sway does.
         *
         * The way across is the same one for every length of a span, whichever of its ends a
         * length is read from first: lengths that took it for themselves would swing against
         * each other.
         */
        private void cable(float x0, float y0, float up0, float hangs0, float x1, float y1, float up1, float hangs1, float swing) {
            SpriteGeometry.Mesh plain = solid.get(PLAIN);
            plain.open = frame.open + SpriteGeometry.CARD + 32f * WIRE;
            float length = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
            float turned = Spans.acrossSign(-(y1 - y0), x1 - x0) / length;
            float sideX = -(y1 - y0) * turned, sideY = (x1 - x0) * turned, half = WIRE_THICK / 2f, carried = 0.1f + 0.8f * swing;
            for (int strip = 0; strip < 2; strip++) {
                boolean upright = strip == 0;
                float offX = upright ? 0f : sideX, offY = upright ? 0f : sideY, offUp = upright ? 1f : 0f;
                plain.face(upright ? sideX : 0f, upright ? sideY : 0f, upright ? 0f : 1f);
                for (int corner = 0; corner < 6; corner++) {
                    int side = STRIP_SIDE[corner];
                    boolean far = STRIP_END[corner] == 1;
                    float tells = (upright ? 3f : 1f) + (side > 0 ? 0f : 1f) + carried;
                    plain.sway = far ? hangs1 : hangs0;
                    plain.add((far ? x1 : x0) + offX * side * half, (far ? y1 : y0) + offY * side * half, (far ? up1 : up0) + offUp * side * half,
                            sideX * tells, sideY * tells, frame.red, frame.green, frame.blue);
                }
            }
            plain.sway = 0f;
        }

        private final float[] lightSpan = new float[Spans.PIECE];


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
                    float share = (length + end) / (float) Lights.LENGTHS;
                    float endX = lightSpan[0] + (lightSpan[2] - lightSpan[0]) * share, endY = lightSpan[1] + (lightSpan[3] - lightSpan[1]) * share;
                    float along = Spans.along(lightSpan, 0, endX, endY);
                    wireHangs[end] = Spans.hangs(along, sag);
                    set(wireEnds, end, endX, endY, fromUp + (toUp - fromUp) * along - wireHangs[end]);
                }
                cable(wireEnds[0], wireEnds[1], wireEnds[2], wireHangs[0], wireEnds[3], wireEnds[4], wireEnds[5], wireHangs[1], swing);
            }
            boolean burning = object instanceof IsoLightSwitch string && burns(string);
            if (FirstPerson.TRACE) {
                Log.info("lights: " + x + "," + y + "," + z + " " + sprite.getName() + " " + object.getClass().getSimpleName() + (burning ? " burning" : " out"));
            }
            float spanX = lightSpan[to] - lightSpan[from], spanY = lightSpan[to + 1] - lightSpan[from + 1];
            float turned = Spans.acrossSign(-spanY, spanX) / Lights.SPAN, tells = 1.1f + 0.8f * swing;
            float acrossX = -spanY * turned * tells, acrossY = spanX * turned * tells, size = Lights.size(kind);
            float red = burning ? 1f : frame.red, green = burning ? 1f : frame.green, blue = burning ? 1f : frame.blue;
            SpriteGeometry.Mesh holder = solid.get(PLAIN, WallSides.key(Lights.HOLDER_RED, Lights.HOLDER_GREEN, Lights.HOLDER_BLUE, WallSides.FLAT));
            for (int bulb = 0; bulb < Lights.bulbs(kind); bulb++) {
                if (!Lights.on(slice, bulb)) {
                    continue;
                }
                float along = Lights.place(kind, bulb) / Lights.SPAN, hangs = Spans.hangs(along, sag);
                float bulbX = lightSpan[from] + spanX * along, bulbY = lightSpan[from + 1] + spanY * along, up = fromUp + (toUp - fromUp) * along - hangs;
                SpriteGeometry.Mesh glass = solid.get(PLAIN, WallSides.key(Lights.colour(kind, bulb, burning, 0), Lights.colour(kind, bulb, burning, 1),
                        Lights.colour(kind, bulb, burning, 2), WallSides.FLAT));
                hungRound(holder, HUNG, bulbX, bulbY, up, hangs, acrossX, acrossY, Lights.HOLDER, size, Lights.sides(kind), frame.red, frame.green, frame.blue);
                hungRound(glass, burning ? LAMP : HUNG, bulbX, bulbY, up, hangs, acrossX, acrossY, Lights.GLASS, size, Lights.sides(kind), red, green, blue);
            }
            return true;
        }

        /**
         * Adds a round thing that hangs under a cord and swings with it: turned about the plumb
         * line under (x, y, up) to a profile of pairs, how far below the cord and how thick, in
         * units of size. hangs is how far the cord hangs there below the line between its
         * supports, and each vertex is that much and its own depth under the cord below the
         * line, so the whole of it is carried round the line as the cord is: see swung in
         * PipelineShaders. stuff is LAMP for glass that burns, and a lamp's vertex that hangs
         * below a line is what the picture swings, or HUNG. across is what cable gives a cord's
         * vertex where a texel's place would be, for the first corner.
         */
        private void hungRound(SpriteGeometry.Mesh mesh, float stuff, float x, float y, float up, float hangs, float acrossX, float acrossY, float[] profile, float size,
                int sides, float red, float green, float blue) {
            mesh.open = frame.open + 32f * stuff;
            for (int ring = 0; ring + 3 < profile.length; ring += 2) {
                float highDeep = profile[ring] * size, highWide = profile[ring + 1] * size, lowDeep = profile[ring + 2] * size, lowWide = profile[ring + 3] * size;
                float drop = lowDeep - highDeep, swell = lowWide - highWide, slant = (float) Math.sqrt(drop * drop + swell * swell);
                for (int side = 0; side < sides; side++) {
                    double start = side * 2.0 * Math.PI / sides, stop = (side + 1) * 2.0 * Math.PI / sides;
                    for (int corner = 0; corner < 6; corner++) {
                        // Two triangles, as SpriteGeometry.addRound makes them: low from, low to, high to, then low from, high to, high from.
                        boolean far = corner == 1 || corner == 2 || corner == 4, high = corner == 2 || corner >= 4;
                        float cos = (float) Math.cos(far ? stop : start), sin = (float) Math.sin(far ? stop : start);
                        float deep = high ? highDeep : lowDeep, wide = high ? highWide : lowWide;
                        mesh.face(cos * drop / slant, sin * drop / slant, swell / slant);
                        mesh.sway = hangs + deep + HUNG_LEAST;
                        mesh.add(x + cos * wide, y + sin * wide, up - deep, acrossX, acrossY, red, green, blue);
                    }
                }
            }
            mesh.sway = 0f;
        }

        /** Whether a string of lights is made fast at the north-west corner of a square, where its corner post stands. */
        private boolean holdsLights(int x, int y, int z) {
            for (int beside = 0; beside < 3; beside++) {
                int alongX = beside == 1 ? -1 : 0, alongY = beside == 2 ? -1 : 0;
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

        private boolean spreads(int at, int own, int mine) {
            if (groundKind[at] != Terrain.NATURAL || lightOpen[at] != 1 || groundTexture[at] == null) {
                return false;
            }
            int name = groundRank[at];
            return name != mine && (groundKind[own] != Terrain.NATURAL || name > mine);
        }

        private void addSquare(IsoGridSquare square, int x, int y, int z, int localX, int localY) {
            floorLayer = westLayer = northLayer = eastLayer = southLayer = 0;
            waterLocalX = localX;
            waterLocalY = localY;

            here = square;
            building.plain &= square.getRoomID() == -1L;
            IsoObject floor = square.getFloor();
            hangsInAir = floor == null;
            geometry.floored = !hangsInAir;
            wallNorth = wallWest = edgeNorth = edgeWest = false;
            boolean openAbove = false;
            PZArrayList<IsoObject> objects = square.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                IsoSprite sprite = objects.get(i).getSprite();
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
            // A room the game has not shown the player yet is black in the game's own view. Here
            // the house still stands, and what is in the room is left out until the game has it seen.
            boolean unexplored = unexplored(square);
            for (int i = 0; i < objects.size(); i++) {
                IsoObject object = objects.get(i);
                IsoSprite sprite = object.getSprite();
                if (sprite == null || unexplored && object != floor && !partOfTheBuilding(object, sprite)) {
                    continue;
                }
                Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
                if (!usable(texture)) {
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
                ArrayList<IsoSpriteInstance> attached = object.getAttachedAnimSprite();
                if (attached != null) {
                    for (int k = 0; k < attached.size(); k++) {
                        addOver(object, sprite, attached.get(k).parentSprite, object == floor, x, y, z);
                    }
                }
                addOver(object, sprite, object.getOverlaySprite(), object == floor, x, y, z);
                geometry.nudgeX = geometry.nudgeY = geometry.nearer = 0f;
            }
            geometry.raised = 0f;
            frame.material = 0f;
            if (!openAbove && square.getRoomID() != -1L) {
                addRoomCeiling(x, y, z, localX, localY);
            }
            if (z == 0) {
                if (geometry.kerb != null && beyondKerb >= 0) {
                    addBeyondKerb(x, y);
                }
                if (kerbed) {
                    addKerb(x, y, localX, localY);
                }
                addEdges(x, y, localX, localY);
                spreadGround(x, y, localX, localY);
                // What grows here is a build of its own: see grow. It lies over these layers.
                building.groundLayers[localX * CHUNK_TILES + localY] = (byte) (floorLayer + spreadLayers);
                int own = (localX + 1) * LIGHT_SPAN + localY + 1;
                building.grows |= groundKind[own] == Terrain.NATURAL && lightOpen[own] == 1;
            }

            ArrayList<IsoMovingObject> bodies = square.getStaticMovingObjects();
            for (int i = 0; i < bodies.size() && !unexplored; i++) {
                if (bodies.get(i) instanceof IsoDeadBody body) {
                    addBody(body);
                }
            }
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
         * that leaves out what is in such a room. As the game's own view the frame leaves nothing
         * out of it: the room has the engine's light, which is what hides it.
         */
        private boolean unexplored(IsoGridSquare square) {
            return !gamesView && square.getRoomID() != -1L && !square.isSeen(playerIndex);
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
            }
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
                addFloor(mesh, kind, properties, texture, isFloor, x, y, z);
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

            if (kind.wire && Poles.length(sprite.getName()) && addPole(mesh, object, sprite, texture, x, y, z)) {
                return;
            }

            // Overhead wires. The game hangs them as sprites on squares of air two floors up, each
            // the lengths of wire that cross its square, painted as its view sees them.
            if (z > 0 && hangsInAir && kind.wire && addWires(texture, sprite.getName(), x, y, z)) {
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
                    cable(fromX, fromY, from + up, 0f, toX, toY, to + up, 0f, 0f);
                }
                return;
            }
            if (kind.banner == Banners.STRUNG || kind.banner == Banners.POSTED) {
                addBanner(mesh, kind.banner == Banners.STRUNG, x, y, z);
                return;
            }

            if (addStair(mesh, texture, type, x, y, z)) {
                return;
            }
            if (overLayer == 0 && Own.buildings() && (kind.rail != null && addRail(kind.rail, x, y, z)
                    || kind.flightPost && underAFlightsHead(x, y, z))) {
                return;
            }

            if (Own.furniture() && addParasol(object, sprite, texture, properties, x, y, z)) {
                return;
            }

            if (Own.furniture() && addLights(object, sprite, x, y, z)) {
                return;
            }

            // A fence's corner post that a string of lights is made fast to. Stood as a wall's
            // corner piece it is its picture on both edges of its square, two boards crossed,
            // each twice as wide as the post and cut to the point the game's view draws its top as.
            if (Own.furniture() && overLayer == 0 && properties.has(IsoFlagType.WallSE) && sprite.getName() != null && sprite.getName().startsWith("fencing_")
                    && holdsLights(x, y, z) && addPost(mesh, object, sprite, texture, x, y, z)) {
                return;
            }

            if (z > 0 && kind.roofAccent && underARake(x, y, z)) {
                return;
            }

            if (overLayer == 0 && addHung(object, sprite, texture, x, y, z)) {
                return;
            }
            if (addFurniture(object, sprite, x, y, z)) {
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
                    && Own.buildings() && addCurtain(texture, type, x, y, z)) {
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
                boolean cloth = kind.banner == Banners.ON_WALL;
                west = both || standsOnWestEdge(properties, type) || cloth && !Banners.alongX(frame);
                north = both || standsOnNorthEdge(properties, type) || cloth && Banners.alongX(frame);
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
            if (west) {
                addEdge(mesh, texture, isWall(properties, type), door, x, y, z, westLayer++, 0f, 1f);
            }
            if (north) {
                addEdge(mesh, texture, isWall(properties, type), door, x, y, z, northLayer++, 1f, 0f);
            }
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
                if (overLayer == 0 && sprite.getName() != null && !kind.roofSheet && addPost(mesh, object, sprite, texture, x, y, z)) {
                    return;
                }
                roofName = sprite.getName();
                addRoof(mesh, texture, x, y, z);
                return;
            }
            if (z > 0 && kind.roofSheet) {
                // Left out. Laid level they stand proud of a gable as slabs; the sheet under each
                // roof tile is what closes the roof.
                return;
            }
            // A boulder. The game's picture of one stood up as a card is a cut-out of a boulder,
            // and wrapped on a box it is a crate painted as one.
            if (kind.boulder && Own.plants() && addRock(x, y, z)) {
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
            if (z == 0 && kind.longGrass && Own.plants() && growLongGrass(x, y)) {
                return;
            }
            if (grows) {
                addGrown(mesh, sprite, type, texture, x, y, z);
                return;
            }
            if (overLayer == 0 && properties.getSurface() <= 0 && !properties.isTable() && addInWallLine(mesh, sprite, texture, x, y, z)) {
                return;
            }
            geometry.addUpright(mesh, frame, x, y, z, 0.5f, 0.5f, 0f, 1f, 0f, true, false);
        }

        /** A floor, or what the game lays on one, flat on the ground of its square. */
        private void addFloor(SpriteGeometry.Mesh mesh, Kind sprite, PropertyContainer properties, Texture texture, boolean isFloor,
                int x, int y, int z) {
            // The game's own edging of one ground over another, a square at a time. spreadGround
            // does that here.
            if (!isFloor && z == 0 && sprite.natural) {
                return;
            }
            frame.kind = properties.has(IsoFlagType.water) ? SpriteGeometry.WATER : 0f;
            // The floor of a square is its sprite laid flat, a picture that goes on into the
            // squares beside it: see Flats. Not water, which the picture moves, nor what is
            // painted over a floor.
            boolean floor = isFloor && overLayer == 0 && floorLayer == 0 && frame.kind == 0f;
            int flat = floor ? flatOf(texture, Flats.FLOOR, 0f, 0f, 0f) : 0;
            frame.flat = flat != 0 ? SpriteGeometry.FLAT_FLOOR : 0;
            if (flat != 0) {
                mesh = solid.get(flat);
                // The grain the picture lays on a floor of boards runs the way the sprite's boards do.
                if (frame.material == WOOD && Flats.alongY(flat)) {
                    frame.material = WOOD_ALONG_Y;
                }
            }
            if (floorLayer == 0 && z > lowestLevel) {
                addCeiling(mesh, x, y, z);
            }
            // A line painted on the road is laid as paint, not as its sprite: see RoadPaint.
            boolean painted = (overLayer > 0 ? paintOver : sprite.paint && !isFloor) && frame.kind == 0f && Own.paint();
            RoadPaint.Shape paint = painted ? RoadPaint.of(texture, frame) : null;
            if (painted && paint == null && RoadPaint.unread(texture)) {
                building.signed = false;
            }
            SpriteGeometry.Mesh on = paint != null ? laid.get(paint.picture) : floorLayer == 0 ? mesh : laid.get(texture.getID());
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
            // A floor a kerb crosses ends at the kerb, and the ground of the square beside goes on beyond
            // it. What the game paints over the floor, an edging or a line, ends there with it.
            if (z == 0 && geometry.kerb != null && (!isFloor || beyondKerb >= 0)) {
                geometry.kerbKeep = geometry.kerbStreet && !vergeOver ? SpriteGeometry.STREET : SpriteGeometry.LAND;
            }
            if (floorLayer == 0 && isFloor && frame.kind == 0f && geometry.kerb == null && (sprite.carpet || sprite.tiled) && Own.textures()) {
                // Tiles are faces of their own on the sprite's own mesh, painted over.
                frame.flat = 0;
                if (addTiles(texture, sprite.carpet, x, y, z)) {
                    return;
                }
                frame.flat = flat != 0 ? SpriteGeometry.FLAT_FLOOR : 0;
            }
            layers.lie(SpriteGeometry.LAYERS_UP, floorLayer++);
            if (paint != null) {
                RoadPaint.lay(geometry, on, frame, paint, x, y, z);
            } else {
                geometry.addFace(on, frame, x, y, z, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
            }
            layers.none();
            geometry.kerbKeep = SpriteGeometry.BOTH;
            frame.kind = 0f;
            frame.flat = 0;
            geometry.cover = null;
        }
        private final float[] tileTint = new float[4];

        /**
         * A square of tiled floor, laid: a bed the colour of the joints, and on it each tile as a
         * face of its own in its own colour, with a straight edge. How many tiles and which colours
         * is measured off the sprite: see Tiles. False, adding nothing, for a sprite not measured
         * yet or a floor that is no grid of plain tiles.
         */
        private boolean addTiles(Texture texture, boolean carpet, int x, int y, int z) {
            Tiles.Shape tiles = Tiles.of(texture, frame);
            if (tiles == null) {
                if (Tiles.unread(texture)) {
                    building.signed = false;
                }
                return false;
            }
            // A fitted carpet is one colour, with a nap the picture has a picture of: the few dots of
            // lighter and darker the game scatters on it are blots when magnified.
            if (carpet ? tiles.uneven > CARPET_UNEVEN : tiles.count == 0) {
                return false;
            }
            int count = carpet ? 1 : tiles.count;
            int key = WallSides.key(tiles.red, tiles.green, tiles.blue, WallSides.FLAT);
            WallSides.tint(key, tileTint);
            SpriteGeometry.Mesh on = solid.get(texture.getID(), key);
            float made = frame.material, red = frame.red, green = frame.green, blue = frame.blue;
            System.arraycopy(frame.cornerRed, 0, paperRed, 0, 4);
            System.arraycopy(frame.cornerGreen, 0, paperGreen, 0, 4);
            System.arraycopy(frame.cornerBlue, 0, paperBlue, 0, 4);
            frame.material = carpet ? CARPET : TILE;
            float joint = count > 1 ? JOINT_HALF : 0f, each = 1f / count;
            if (count > 1) {
                shade(red, green, blue, JOINT_DARK * tiles.jointRed / Math.max(tileTint[0], 0.02f), JOINT_DARK * tiles.jointGreen / Math.max(tileTint[1], 0.02f),
                        JOINT_DARK * tiles.jointBlue / Math.max(tileTint[2], 0.02f));
                layers.lie(SpriteGeometry.LAYERS_UP, floorLayer++);
                geometry.addFace(on, frame, x, y, z, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
            }
            layers.lie(SpriteGeometry.LAYERS_UP, floorLayer++);
            for (int i = 0; i < count; i++) {
                for (int j = 0; j < count; j++) {
                    int at = (i * count + j) * 3;
                    float r = carpet ? tiles.red : tiles.colours[at], g = carpet ? tiles.green : tiles.colours[at + 1], b = carpet ? tiles.blue : tiles.colours[at + 2];
                    shade(red, green, blue, r / Math.max(tileTint[0], 0.02f), g / Math.max(tileTint[1], 0.02f), b / Math.max(tileTint[2], 0.02f));
                    geometry.addFace(on, frame, x, y, z, i * each + joint, j * each + joint, 0f, each - 2f * joint, 0f, 0f, 0f, each - 2f * joint, 0f);
                }
            }
            layers.none();
            frame.material = made;
            frame.red = red;
            frame.green = green;
            frame.blue = blue;
            System.arraycopy(paperRed, 0, frame.cornerRed, 0, 4);
            System.arraycopy(paperGreen, 0, frame.cornerGreen, 0, 4);
            System.arraycopy(paperBlue, 0, frame.cornerBlue, 0, 4);
            return true;
        }

        /** Lights the frame as it was lit, held in paperRed and its fellows, times a shade for each of red, green and blue. */
        private void shade(float red, float green, float blue, float r, float g, float b) {
            frame.red = red * r;
            frame.green = green * g;
            frame.blue = blue * b;
            for (int corner = 0; corner < 4; corner++) {
                frame.cornerRed[corner] = paperRed[corner] * r;
                frame.cornerGreen[corner] = paperGreen[corner] * g;
                frame.cornerBlue[corner] = paperBlue[corner] * b;
            }
        }

        /**
         * A tree or a bush is not a picture of one that turns to face whoever looks at it.
         * It stands in the world: the sprite is set up several times through the plant's
         * middle, fanned round it, every other one mirrored, so that from any side there
         * are leaves in front of leaves and they pass each other as the eye moves. Which
         * way the fan is turned comes from where the plant stands, so no two are alike.
         */
        private void addGrown(SpriteGeometry.Mesh mesh, IsoSprite sprite, IsoObjectType type, Texture texture, int x, int y, int z) {
            int cards = type == IsoObjectType.tree ? 5 : 3;
            // A tree's bole is round. A tree in leaf shows nothing else of its trunk's sprite,
            // so the bole stands in for all its cards; a bare one keeps them for its branches.
            if (type == IsoObjectType.tree && overLayer == 0 && Own.plants() && addBole(sprite, texture, x, y, z) && geometry.keep < 1f) {
                return;
            }
            // A bush is leaves all over, as a tree's crown is.
            String grown = sprite.getName();
            // A bush's leaves are smaller than a tree's.
            boolean bush = grown != null && type != IsoObjectType.tree
                    && (grown.startsWith("f_bushes") || grown.startsWith("vegetation_foliage") || grown.startsWith("vegetation_ornamental"));
            // So is every other plant the game paints with a dark line round each leaf: the
            // line is what makes it a drawing. A bare tree's trunk is the one thing left as it is.
            if (grown != null && type != IsoObjectType.tree && Own.plants()) {
                frame.material = bush ? Flora.BUSH : LEAVES;
            }
            // The leaves of a tree and a bush, near enough to be seen as leaves, are a crown of
            // sprigs. The fan then stands only in the sun's own pictures, to throw the shadow.
            float fanned = frame.material;
            boolean crowned = grown != null && (bush || type == IsoObjectType.tree && overLayer > 0) && Own.plants() && addCrown(texture, grown, bush, x, y, z);
            if (crowned) {
                frame.material = Flora.SHADE;
            }
            double turn = ((x * 73856093 ^ y * 19349663) >>> 8 & 0xff) / 255.0 * Math.PI;
            for (int card = 0; card < cards; card++) {
                double angle = turn + card * Math.PI / cards;
                float side = (card & 1) == 0 ? 1f : -1f;
                float alongX = (float) Math.cos(angle), alongY = (float) Math.sin(angle);
                // Leaves lie on the same card as their trunk, and each layer has to stand
                // clear of the one under it on the side the eye is on, square to this card:
                // a move straight towards the eye does nothing for a card seen at a slant.
                // Which side that is, the picture finds as it draws: see PipelineShaders.FACED.
                geometry.clear = overLayer;
                geometry.addUpright(mesh, frame, x, y, z, 0.5f, 0.5f, 0f, side * alongX, side * alongY, false, true);
                geometry.clear = 0f;
            }
            frame.material = fanned;
            if (bush && !crowned && Own.plants() && within(BUSH_REACH, false)) {
                // A fan of cards through one line is thin seen along that line, and from
                // above it is a star. A bush is a clump: three more cards stand round its
                // middle, each across the way out to it, so there are leaves on every side.
                for (int outer = 0; outer < 3; outer++) {
                    double out = turn + (outer + 0.5) * 2.0 * Math.PI / 3.0;
                    float outX = (float) Math.cos(out), outY = (float) Math.sin(out);
                    float side = (outer & 1) == 0 ? 1f : -1f;
                    geometry.nudgeX = outX * BUSH_SPREAD;
                    geometry.nudgeY = outY * BUSH_SPREAD;
                    geometry.addUpright(mesh, frame, x, y, z, 0.5f, 0.5f, 0f, -outY * side, outX * side, false, true);
                }
            }
            geometry.nudgeX = geometry.nudgeY = 0f;
        }
        /** The tree whose bole was stood last: its square, how thick and tall the bole is, and its bark. Its crown, which comes after it, grows limbs from it. */
        private int boleX = Integer.MIN_VALUE, boleY, boleBark;
        private float boleThick, boleTall;
        /**
         * Stands a crown of sprigs for the plant whose leaves the frame is set to, in the mesh of
         * the leaves' own texture, which gives the sprigs their colour. False when the plant is
         * too far off for one or its texture shows too little to make one of.
         */
        private boolean addCrown(Texture texture, String name, boolean bush, int x, int y, int z) {
            if (!crownedToSight && !within(Crowns.REACH, true)) {
                return false;
            }
            float away = bandAway;
            float[] places = leafPlaces.get(texture);
            if (places == null) {
                places = Flora.leafPlaces(texture::isMaskSet, frame);
                // A texture that shows nothing yet may only not have its outline read yet.
                if (places.length < CROWN_LEAST_PLACES * 2) {
                    return false;
                }
                leafPlaces.put(texture, places);
            }
            // The bole stood last may be another tree's, in another chunk: only its own tree's crown grows limbs from it.
            boolean boled = !bush && boleX == x && boleY == y;
            int seed = Flora.mix(x, y, 83);
            float base = z * ViewCamera.LEVEL_HEIGHT + geometry.liftAt(0.5f, 0.5f) + geometry.raised * ViewCamera.LEVEL_HEIGHT;
            float footU = SpriteGeometry.frameU(frame, 0.5f, 0.5f), footV = SpriteGeometry.frameV(frame, 0.5f, 0.5f, 0f);
            int bark = Flora.barkOf(name, seed), sprig = Flora.sprigOf(name, bush);
            if (crownsInline) {
                boolean limbed = boled && Crowns.limbed(away);
                return Flora.addCrown(solid.get(texture.getID()), solid.get(PLAIN), frame, places, x + 0.5f, y + 0.5f, base, footU, footV, Crowns.detail(away), Crowns.close(away),
                        limbed ? boleThick : 0f, limbed ? boleTall : 0f, limbed ? boleBark : bark, sprig, seed);
            }
            if (!Flora.measure(frame, places, footU, footV, crownLeft, crownRight, crownSizes)) {
                return false;
            }
            (afresh ? crownsChecked : crownsRead).add(texture.getID(), frame, places, x + 0.5f, y + 0.5f, base, footU, footV, away, boled ? boleThick : 0f, boled ? boleTall : 0f,
                    boled ? boleBark : bark, bark, sprig, seed);
            return true;
        }

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
            float pixelsPerTile = SpriteGeometry.PIXELS_ACROSS_PER_TILE * frame.scale;
            if (foot.length == 0 || frame.height < BRACED_TALL * SpriteGeometry.PIXELS_PER_LEVEL * frame.scale || frame.width < BRACED_LONG * pixelsPerTile
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

        /** The post an object's sprite is a picture of, read the first time the sprite is met, or null. */
        private Posts postOf(IsoObject object, IsoSprite sprite, Texture texture) {
            Object post = posts.get(sprite);
            if (post == null) {
                shapeFrame.scale = frame.scale;
                WorldScene.frameOf(shapeFrame, object, texture);
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
                if (!usable(texture)) {
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
                    postAt(x, y, z + 1, a, b) == null);
            boxes++;
            return true;
        }
        private final SpriteGeometry.Frame shapeFrame = new SpriteGeometry.Frame();

        /** The shape of an object's sprite, read the first time the sprite is met. */
        private SpriteShape shapeOf(IsoObject object, IsoSprite sprite, Texture texture) {
            SpriteShape shape = shapes.get(sprite);
            if (shape == null) {
                shapeFrame.scale = frame.scale;
                WorldScene.frameOf(shapeFrame, object, texture);
                shape = SpriteShape.read(texture::isMaskSet, shapeFrame);
                if (shape.kind == SpriteShape.BEAM) {
                    // Its solid paint is read by the next frame drawn: until then it is the beam its outline makes, and the build is made again.
                    Solids.Outline solid = Solids.of(texture, shapeFrame);
                    if (solid == null) {
                        building.signed = false;
                        return shape;
                    }
                    shape = SpriteShape.withoutShadow(shape, solid, shapeFrame);
                }
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
                    if (!usable(texture)) {
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
            flora.addRock(solid.get(PLAIN), x + a, y + b, z * ViewCamera.LEVEL_HEIGHT + geometry.liftAt(a, b), Math.clamp(box[2] * 1.15f, 0.15f, 0.6f),
                    Math.clamp(box[3] * ViewCamera.LEVEL_HEIGHT, 0.12f, 1.4f), within(ROCK_NEAR, false), Flora.mix(x, y, 80), frame);
            return true;
        }

        /**
         * Stands a round bole where a tree's sprite shows its trunk, with ivy up some. False when
         * the tree is too far off for one, or its sprite has no bole that can be read.
         */
        private boolean addBole(IsoSprite sprite, Texture texture, int x, int y, int z) {
            if (!crownedToSight && !within(TRUNK_REACH, true)) {
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
            boolean near = within(TRUNK_NEAR, false);
            int seed = Flora.mix(x, y, 81);
            float base = z * ViewCamera.LEVEL_HEIGHT + geometry.liftAt(0.5f, 0.5f);
            boleBark = Flora.barkOf(sprite.getName(), seed);
            flora.addTrunk(solid.get(PLAIN), x + 0.5f, y + 0.5f, base, bole[0], bole[1], near, boleBark, seed, frame);
            boleX = x;
            boleY = y;
            boleThick = bole[0];
            boleTall = bole[1];
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

        private final SpriteGeometry.Frame dyeFrame = new SpriteGeometry.Frame();
        /** Where the piece last found ready takes its colours, and the texture of the sprite that gives them, PLAIN for a piece that takes none. */
        private float dyeU, dyeV, alsoU, alsoV;
        private int dyeTexture;

        private final float[] hungAt = new float[4], hungShows = new float[6], hungWay = new float[2];

        /**
         * Hangs the mod's model of a picture, a mirror or a clock on the wall the sprite was
         * painted for, where on that wall the sprite paints it and as big, and says whether the
         * sprite is thereby dealt with. A picture's frame holds the mod's own picture for the
         * sprite where it has one, and failing that what the sprite shows.
         *
         * False, which leaves the sprite to be drawn as it is, for a sprite that is no such thing
         * and for one not painted where something on that wall would be.
         */
        private boolean addHung(IsoObject object, IsoSprite sprite, Texture texture, int x, int y, int z) {
            Furniture piece = Furniture.known(sprite);
            // The mod's picture for the sprite, where its pictures are on. A poster hangs as the
            // mod's sheet only to show one, and a frame, a clock or a lamp only where the mod's
            // furniture is on or it has a picture to show.
            int picture = piece != null && Own.pictures() ? piece.picture : Decor.NONE;
            if (piece == null || piece.wall == WallHung.NONE || picture == Decor.NONE && (piece.pinned || !Own.furniture())
                    || !WallHung.painted(frame, piece.wall, hungAt) && !(piece.ownSize && WallHung.paintedProud(hungAt, piece.model.depth()))) {
                return false;
            }
            if (picture != Decor.NONE && piece.grid != null) {
                // The sprite of one square's length of a picture stops a pixel short of the square's
                // end, and the wall shows through between two lengths. Each is stood to the end
                // of its square where another length follows it.
                if (piece.lengthAt() > 0 && hungAt[WallHung.FROM] < LENGTHS_MEET) {
                    hungAt[WallHung.FROM] = 0f;
                }
                if (piece.lengthAt() < piece.lengths() - 1 && hungAt[WallHung.TO] > 1f - LENGTHS_MEET) {
                    hungAt[WallHung.TO] = 1f;
                }
                // And every length is as high as the whole picture: the sprites of two lengths
                // differ by a pixel at the foot, which is a step in the picture's lower edge.
                if (!wholeOf(object, piece) && piece.lettered) {
                    return false;
                }
                hungAt[WallHung.BOTTOM] = wholeAt[WallHung.BOTTOM];
                hungAt[WallHung.TOP] = wholeAt[WallHung.TOP];
            }
            float wide = hungAt[WallHung.TO] - hungAt[WallHung.FROM];
            float high = (hungAt[WallHung.TOP] - hungAt[WallHung.BOTTOM]) * ViewCamera.LEVEL_HEIGHT;
            float along = (hungAt[WallHung.FROM] + hungAt[WallHung.TO]) / 2f;
            Models.Model model = piece.model != null ? piece.model : Models.get(FRAMES[WallHung.frameFor(wide / high)]);
            float scale = wide / model.width(), scaleUp = high / model.height(), foot = hungAt[WallHung.BOTTOM] * ViewCamera.LEVEL_HEIGHT;
            if (piece.model != null && !piece.model.name.equals("canvas")) {
                // A clock is round whatever its sprite's outline comes to, and hangs about the middle of it.
                scale = scaleUp = piece.ownSize ? Furniture.TILES_PER_METRE : Math.clamp(Math.min(scale, scaleUp), CLOCK_SMALLEST, CLOCK_LARGEST);
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
            building.plain = false;
            placed.add(model, frame, x, y, x + a + hungWay[0] * out, y + b + hungWay[1] * out,
                    z * ViewCamera.LEVEL_HEIGHT + (lift[0] + lift[1] + lift[2] + lift[3]) / 4f + foot, piece.turns, scale, scaleUp,
                    !piece.pictured ? PLAIN : picture != Decor.NONE ? Decor.held(picture) : texture.getID(),
                    piece.pictured ? 1 << Models.PANE : 0, 0f, 0f, 0, 0f, 0f);
            if (picture != Decor.NONE && piece.grid == null) {
                float[] pane = Decor.pane(model);
                Decor.lay(pane[0], pane[1], pane[2], pane[3], (pane[1] - pane[0]) * scale, (pane[3] - pane[2]) * scaleUp, 0f,
                        Decor.wideForHigh(picture), hungShows);
                placed.picture(hungShows);
            } else if (picture != Decor.NONE) {
                // One square's length of a picture longer than a square shows its own part of it.
                // Whoever faces a north wall has the map's x growing to their right, and whoever
                // faces a west one has its y growing to their left.
                boolean north = piece.wall == WallHung.NORTH;
                float bottom = hungAt[WallHung.BOTTOM];
                Decor.lay((north ? wholeAt[WallHung.FROM] - along : along - wholeAt[WallHung.TO]) / scale,
                        (north ? wholeAt[WallHung.TO] - along : along - wholeAt[WallHung.FROM]) / scale,
                        (wholeAt[WallHung.BOTTOM] - bottom) * ViewCamera.LEVEL_HEIGHT / scaleUp, (wholeAt[WallHung.TOP] - bottom) * ViewCamera.LEVEL_HEIGHT / scaleUp,
                        wholeAt[WallHung.TO] - wholeAt[WallHung.FROM], (wholeAt[WallHung.TOP] - wholeAt[WallHung.BOTTOM]) * ViewCamera.LEVEL_HEIGHT,
                        piece.rail, Decor.wideForHigh(picture), hungShows);
                placed.picture(hungShows);
            } else if (piece.pictured) {
                Models.turn(piece.turns, 1f, 0f, hungWay);
                WallHung.shows(frame, a, b, hungAt[WallHung.BOTTOM], hungWay[0] * scale, hungWay[1] * scale, scaleUp / ViewCamera.LEVEL_HEIGHT, hungShows);
                placed.picture(hungShows);
            }
            furnished++;
            return true;
        }

        private final SpriteGeometry.Frame partFrame = new SpriteGeometry.Frame();
        private final float[] partAt = new float[4], wholeAt = new float[4];

        /**
         * Finds the whole of a picture that hangs along several squares of one wall, from the
         * sprites of all its squares: into wholeAt as WallHung.painted leaves one square's, with
         * the places along the wall counted from the corner of the square the object is on. Each
         * square's sprite is taken to stand on its square as the object's does on this one, and
         * the lengths to follow each other along the wall the way the map's x or y grows. False
         * where a square's sprite is not painted as something on that wall: the whole is then
         * of the other squares alone.
         */
        private boolean wholeOf(IsoObject object, Furniture piece) {
            wholeAt[WallHung.FROM] = wholeAt[WallHung.BOTTOM] = Float.MAX_VALUE;
            wholeAt[WallHung.TO] = wholeAt[WallHung.TOP] = -Float.MAX_VALUE;
            partFrame.scale = frame.scale;
            boolean every = true;
            for (int k = 0; k < piece.lengths(); k++) {
                IsoSprite part = piece.width > 1 ? piece.grid.getSprite(k, piece.gridY) : piece.grid.getSprite(piece.gridX, k);
                Texture painting = part == null ? null : part.getTextureForCurrentFrame(object.getDir());
                if (!usable(painting)) {
                    every = false;
                    continue;
                }
                WorldScene.frameOf(partFrame, object, painting);
                if (!WallHung.painted(partFrame, piece.wall, partAt)) {
                    every = false;
                    continue;
                }
                int from = k - piece.lengthAt();
                wholeAt[WallHung.FROM] = Math.min(wholeAt[WallHung.FROM], from + partAt[WallHung.FROM]);
                wholeAt[WallHung.TO] = Math.max(wholeAt[WallHung.TO], from + partAt[WallHung.TO]);
                wholeAt[WallHung.BOTTOM] = Math.min(wholeAt[WallHung.BOTTOM], partAt[WallHung.BOTTOM]);
                wholeAt[WallHung.TOP] = Math.max(wholeAt[WallHung.TOP], partAt[WallHung.TOP]);
            }
            return every;
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
            if (!usable(dye)) {
                return false;
            }
            dyeFrame.scale = frame.scale;
            WorldScene.frameOf(dyeFrame, dyer, dye);
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
                Furniture piece = sprite == null ? null : Furniture.known(sprite);
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
            Furniture piece = Own.furniture() ? Furniture.known(sprite) : null;
            // What hangs on a wall is addHung's: one it could not hang is drawn as it was.
            if (piece == null || piece.wall != WallHung.NONE) {
                return false;
            }
            if (piece.basin) {
                // Dealt with when the counter under it is: the counter's model has the basin in it.
                IsoObject counter = onSquare(x, y, z, false);
                return counter != null && ready(counter, Furniture.known(counter.getSprite()), x, y, z);
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
            building.plain = false;
            // The game draws a thing that stands on a table higher than its sprite has it where the
            // table is higher than the one the sprite was painted on: a set on a chest of drawers.
            float foot = z * ViewCamera.LEVEL_HEIGHT + (lift[0] + lift[1] + lift[2] + lift[3]) / 4f + piece.raised + geometry.raised * ViewCamera.LEVEL_HEIGHT;
            placed.add(model, frame, x, y, x + piece.centreX, y + piece.centreY, foot, piece.turns, piece.scale, piece.scaleUp,
                    dyeTexture, piece.dyed, dyeU, dyeV, piece.alsoDyed, alsoU, alsoV);
            furnished++;
            if (piece.television && object instanceof IsoTelevision set) {
                // The game's sprite of a lit screen is painted for its own view and has no place on
                // the model: it is never drawn over one. With the mod's pictures switched off a set
                // that is on shows the bars, which is what the game's sprite is a picture of.
                int showing = Screens.showing(set);
                if (showing != Screens.DARK) {
                    addLitScreen(piece, model, Screens.held(set, Own.pictures() ? showing : Screens.BARS, piece.blackAndWhite), piece.blackAndWhite, x, y, foot);
                }
            }
            return true;
        }

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
            // The pole is a post of a building's wall, on the south-east square. A fence's corner post there is the fence's.
            if (quarter == Parasols.NONE && !(properties.has(IsoFlagType.WallSE) && sprite.getName() != null && sprite.getName().startsWith("walls_"))) {
                return false;
            }
            int from = quarter == Parasols.NONE ? Parasols.SOUTH_EAST : quarter;
            IsoObject front = Parasols.whole(cell, x - Parasols.ALONG_X[from], y - Parasols.ALONG_Y[from], z);
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

        private final float[] screenAt = new float[2];

        /**
         * Stands a picture just proud of the glass of a set that is on, as wide and as high as the glass. held is what
         * Screens.held gives for it. Whether the tube has colour goes where a piece's second dye is looked up, which a
         * sheet has none of: a programme's picture is in colour in the jar, and the picture's program takes it out.
         */
        private void addLitScreen(Furniture piece, Models.Model set, int held, boolean inGrey, int x, int y, float foot) {
            float[] glass = Screens.glass(set);
            float wide = (glass[1] - glass[0]) * piece.scale, high = (glass[3] - glass[2]) * piece.scaleUp;
            Models.turn(piece.turns, (glass[0] + glass[1]) / 2f * piece.scale, (glass[4] - Screens.PROUD) * piece.scale, screenAt);
            placed.add(Models.get("lit_screen"), frame, x, y, x + piece.centreX + screenAt[0], y + piece.centreY + screenAt[1], foot + glass[2] * piece.scaleUp,
                    piece.turns, wide, high, held, 1 << Models.SCREEN, 0f, 0f, 0, inGrey ? Screens.IN_GREY : Screens.IN_COLOUR, 0f);
            Decor.lay(-0.5f, 0.5f, 0f, 1f, wide, high, 0f, Screens.WIDE_FOR_HIGH, hungShows);
            placed.picture(hungShows);
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
            if (above != null && (above.getFloor() != null || above.getRoomID() != -1L) || madeAt(x, y, z)
                    || cutAway(x, y)) {
                return;
            }
            cornerLights(localX, localY);
            SpriteGeometry.Mesh plain = solid.get(PLAIN);
            plain.open = 32f * CONCRETE;
            plain.sway = 0f;
            plain.face(0f, 0f, -1f);
            float up = (z + 1) * ViewCamera.LEVEL_HEIGHT;
            layers.lie(SpriteGeometry.LAYERS_UP, -1f);
            for (int corner : CEILING_CORNERS) {
                plain.add(x + (corner == 1 || corner == 2 ? 1f : 0f), y + (corner >= 2 ? 1f : 0f), up + geometry.lift[corner], 0f, 0f,
                        frame.cornerRed[corner], frame.cornerGreen[corner], frame.cornerBlue[corner]);
            }
            layers.none();
        }

        /**
         * Lays the tiles of the slopes the map leaves out that lie in a chunk, and the caps of the
         * ridges and hips in it: see RoofFit.backs and RoofFit.caps. They are of the roof's own
         * colour, and lit as the open air is: no square of the map holds them, so none has a
         * light for them.
         */
        private void addBacks(IsoChunk chunk, int chunkX, int chunkY) {
            ArrayList<Roofs.Laid> backs = Roofs.madeIn(cell, chunk.wx, chunk.wy, roundNow, frame.scale);
            ArrayList<Roofs.Capped> caps = Roofs.cappedIn(cell, chunk.wx, chunk.wy, roundNow, frame.scale);
            if (backs == null && caps == null) {
                return;
            }
            building.plain = false;
            float[] ground = terrain.corners(cell, chunk.wx, chunk.wy);
            frame.light(unseenLight.r, unseenLight.g, unseenLight.b);
            frame.open = 1f;
            frame.kind = 0f;
            // A gable under one of these is a plain wall with nothing over it: see laid.
            overLayer = 0;
            gableSheet = false;
            float[] lift = geometry.lift;
            for (int i = 0; backs != null && i < backs.size(); i++) {
                RoofFit.Tile tile = backs.get(i).tile;
                if (cutAway(tile.x, tile.y)) {
                    continue;
                }
                liftOf(ground, tile.x - chunkX, tile.y - chunkY);
                roof = backs.get(i).roof;
                SpriteGeometry.Mesh sheet = roofSheet();
                if (tile.carried) {
                    // A slope carried into a valley is its plane and nothing else: it has no edge of its own on this square.
                    if (tile.peak > 0f) {
                        addPeak(sheet, tile);
                    } else {
                        float[] over = tile.corners;
                        SpriteGeometry.addOver(sheet, tile.x, tile.y, over[0] * ViewCamera.LEVEL_HEIGHT + lift[0], over[1] * ViewCamera.LEVEL_HEIGHT + lift[1],
                                over[2] * ViewCamera.LEVEL_HEIGHT + lift[2], over[3] * ViewCamera.LEVEL_HEIGHT + lift[3], roofU, roofV, frame.red, frame.green, frame.blue);
                    }
                    continue;
                }
                addGable(tile.x, tile.y, tile.z, tile.corners[0] - tile.z, tile.corners[1] - tile.z, tile.corners[3] - tile.z, tile.peak, tile.peakAxis);
                if (tile.shaped) {
                    addFolded(sheet, tile, 3, false);
                    addFoldEaves(sheet, tile);
                    continue;
                }
                if (tile.peak > 0f) {
                    addPeak(sheet, tile);
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

        private final float[] gable = new float[4];
        /** The level buildingUnder last found a building on, and the room it found there. */
        private int foundOn;
        private long foundIn;

        /** The building that stands under a square of a roof on level z, on the lowest level it is found on, or null for a roof over open ground. */
        private IsoBuilding buildingUnder(int x, int y, int z) {
            for (int level = 0; level <= z; level++) {
                IsoGridSquare under = cell.getGridSquare(x, y, level);
                IsoBuilding standing = under == null ? null : under.getBuilding();
                if (standing != null) {
                    foundOn = level;
                    foundIn = under.getRoomID();
                    return standing;
                }
            }
            return null;
        }

        /**
         * The wall under the end of a roof. The game's view sees a house from the south-east, and
         * its maps have the walls that close a roof's ends, the gables, only where that view
         * sees them: at the east and south ends. A roof that ends at the west or the north has
         * nothing under its edge, and from that side one looks in under the slopes. So under the
         * west and the north edge of a roof tile, where the building under it goes no further and
         * the map has no wall, the wall is made: from the floor of the tile's level up to the roof, in the
         * sprite the building's east or south face shows. The heights are the roof's over that
         * floor, in levels, at the tile's north-west, north-east and south-west corners. peak is how
         * far a ridge along the middle of the tile stands over them, and peakAxis the axis the
         * roof climbs to it along: see RoofFit.peaks.
         */
        private void addGable(int x, int y, int z, float northWest, float northEast, float southWest, float peak, int peakAxis) {
            // A roof that runs out past its building's wall has no wall under its own edge: the
            // gable is under the last tile that has the building beneath it.
            IsoBuilding standing = buildingUnder(x, y, z);
            if (standing == null) {
                return;
            }
            int ground = foundOn;
            long room = foundIn;
            IsoGridSquare square = cell.getGridSquare(x, y, z);
            // The wall is out of doors and is lit as the open air is. The roof tile's own square
            // is the loft under it, which is dark.
            float red = frame.red, green = frame.green, blue = frame.blue, open = frame.open;
            System.arraycopy(frame.cornerRed, 0, heldRed, 0, 4);
            System.arraycopy(frame.cornerGreen, 0, heldGreen, 0, 4);
            System.arraycopy(frame.cornerBlue, 0, heldBlue, 0, 4);
            frame.light(unseenLight.r, unseenLight.g, unseenLight.b);
            frame.open = 1f;
            addGableWalls(square, standing, ground, room, x, y, z, northWest, northEast, southWest, peak, peakAxis);
            frame.red = red;
            frame.green = green;
            frame.blue = blue;
            frame.open = open;
            System.arraycopy(heldRed, 0, frame.cornerRed, 0, 4);
            System.arraycopy(heldGreen, 0, frame.cornerGreen, 0, 4);
            System.arraycopy(heldBlue, 0, frame.cornerBlue, 0, 4);
        }

        private void addGableWalls(IsoGridSquare square, IsoBuilding standing, int ground, long room, int x, int y, int z, float northWest, float northEast, float southWest,
                float peak, int peakAxis) {
            for (int edge = 0; edge < 2; edge++) {
                boolean north = edge == 1;
                float second = north ? northEast : southWest;
                if (Math.max(northWest, second) < GABLE_LEAST || buildingUnder(north ? x : x - 1, north ? y - 1 : y, z) != null
                        || square != null && wallOn(square, north) != null) {
                    continue;
                }
                // The building's lowest level, and no other: on the roof's own level its walls are
                // the gables of its other ends, cut to the roof's slope, and a gable's sprite laid
                // on this wall would be mostly air.
                WallFinish.Facing facing = WallFinish.facing(cell, standing, ground, north, room, roundNow);
                Texture finish = WallFinish.outside(cell, standing, ground, room, roundNow);
                float alongA = north ? 1f : 0f, alongB = north ? 0f : 1f;
                if (facing == null) {
                    // That face of the building has no plain wall to take the sprite of: the gable
                    // is built of what the building's outside walls are, as the far skin of a
                    // wall is.
                    float[] colour = finish == null ? null : SpriteColours.of(finish);
                    if (colour == null) {
                        gablesBare++;
                        if (finish != null) {
                            building.signed = false;
                        }
                        continue;
                    }
                    gablesPainted++;
                    int built = Own.textures() ? Masonry.of(finish) : 0;
                    building.signed &= !Own.textures() || !Masonry.unread(finish);
                    SpriteGeometry.Mesh painted = solid.get(PLAIN, built != 0 ? WallSides.over(built) : WallSides.key(colour[0], colour[1], colour[2], WallSides.SIDING));
                    painted.open = frame.open + 32f * PAINT;
                    float foot = z * ViewCamera.LEVEL_HEIGHT + (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f;
                    float farX = x + alongA, farY = y + alongB;
                    float[] c = eave;
                    set(c, 0, x, y, foot);
                    set(c, 1, farX, farY, foot);
                    set(c, 2, farX, farY, foot + second * ViewCamera.LEVEL_HEIGHT);
                    set(c, 3, x, y, foot + northWest * ViewCamera.LEVEL_HEIGHT);
                    SpriteGeometry.addPlainQuad(painted, c, frame.red, frame.green, frame.blue);
                    if (peak > 0f && (peakAxis == RoofFit.ALONG_X) == north) {
                        set(c, 0, x, y, foot + northWest * ViewCamera.LEVEL_HEIGHT);
                        set(c, 1, farX, farY, foot + second * ViewCamera.LEVEL_HEIGHT);
                        set(c, 2, x + alongA / 2f, y + alongB / 2f, foot + ((northWest + second) / 2f + peak) * ViewCamera.LEVEL_HEIGHT);
                        set(c, 3, x, y, foot + northWest * ViewCamera.LEVEL_HEIGHT);
                        SpriteGeometry.addPlainQuad(painted, c, frame.red, frame.green, frame.blue);
                    }
                    continue;
                }
                gablesFaced++;
                held.left = frame.left;
                held.top = frame.top;
                held.width = frame.width;
                held.height = frame.height;
                held.uStart = frame.uStart;
                held.uEnd = frame.uEnd;
                held.vStart = frame.vStart;
                held.vEnd = frame.vEnd;
                float material = frame.material;
                frame.left = facing.texture.getOffsetX();
                frame.top = facing.texture.getOffsetY();
                frame.width = facing.texture.getWidth();
                frame.height = facing.texture.getHeight();
                frame.uStart = facing.texture.getXStart();
                frame.uEnd = facing.texture.getXEnd();
                frame.vStart = facing.texture.getYStart();
                frame.vEnd = facing.texture.getYEnd();
                frame.material = materialOf(facing.sprite);
                SpriteGeometry.Mesh mesh = laid(solid.get(facing.texture.getID()), facing.texture, Masonry.suits(facing.sprite.getName()), x, y, z);
                if (second >= GABLE_LEAST) {
                    set(folded, 0, 0f, 0f, 0f);
                    set(folded, 1, alongA, alongB, 0f);
                    set(folded, 2, alongA, alongB, second);
                    geometry.addTriangle(mesh, frame, x, y, z, folded);
                }
                if (northWest >= GABLE_LEAST) {
                    set(folded, 0, 0f, 0f, 0f);
                    set(folded, 1, alongA, alongB, second);
                    set(folded, 2, 0f, 0f, northWest);
                    geometry.addTriangle(mesh, frame, x, y, z, folded);
                }
                if (peak > 0f && (peakAxis == RoofFit.ALONG_X) == north) {
                    set(folded, 0, 0f, 0f, northWest);
                    set(folded, 1, alongA, alongB, second);
                    set(folded, 2, alongA / 2f, alongB / 2f, (northWest + second) / 2f + peak);
                    geometry.addTriangle(mesh, frame, x, y, z, folded);
                }
                frame.left = held.left;
                frame.top = held.top;
                frame.width = held.width;
                frame.height = held.height;
                frame.uStart = held.uStart;
                frame.uEnd = held.uEnd;
                frame.vStart = held.vStart;
                frame.vEnd = held.vEnd;
                frame.material = material;
            }
        }

        /**
         * A tile under a ridge that runs along its middle: two half slopes from its edges up to
         * the ridge, and at a gable, where no roof lies beside it along the ridge, both carried out
         * over the wall as the slopes below them are.
         */
        private void addPeak(SpriteGeometry.Mesh sheet, RoofFit.Tile tile) {
            boolean alongX = tile.peakAxis == RoofFit.ALONG_X;
            float lift = (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f;
            float edge = tile.fitLow * ViewCamera.LEVEL_HEIGHT + lift, top = (tile.fitLow + tile.peak) * ViewCamera.LEVEL_HEIGHT + lift;
            float from = tile.carried || hasRoof(alongX ? tile.x : tile.x - 1, alongX ? tile.y - 1 : tile.y, tile.z) ? 0f : -RAKE;
            float to = tile.carried || hasRoof(alongX ? tile.x : tile.x + 1, alongX ? tile.y + 1 : tile.y, tile.z) ? 1f : 1f + RAKE;
            float[] c = eave;
            for (int side = 0; side < 2; side++) {
                float low = side, high = 0.5f;
                if (alongX) {
                    set(c, 0, tile.x + low, tile.y + from, edge);
                    set(c, 1, tile.x + high, tile.y + from, top);
                    set(c, 2, tile.x + high, tile.y + to, top);
                    set(c, 3, tile.x + low, tile.y + to, edge);
                } else {
                    set(c, 0, tile.x + from, tile.y + low, edge);
                    set(c, 1, tile.x + from, tile.y + high, top);
                    set(c, 2, tile.x + to, tile.y + high, top);
                    set(c, 3, tile.x + to, tile.y + low, edge);
                }
                SpriteGeometry.addQuad(sheet, c, roofU, roofV, frame.red, frame.green, frame.blue);
                // At a gable the half slope's edge has its board, as a whole slope's has.
                if (from < 0f) {
                    addBarge(sheet, c[0], c[1], c[2], c[3], c[4], c[5]);
                }
                if (to > 1f) {
                    addBarge(sheet, c[9], c[10], c[11], c[6], c[7], c[8]);
                }
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
                            (under ? WorldScene.under(tile.z, high) : (tile.z + high) * ViewCamera.LEVEL_HEIGHT) + geometry.lift[corner]);
                }
                SpriteGeometry.addTriangle(sheet, folded, roofU, roofV, frame.red, frame.green, frame.blue);
            }
        }

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
        private final SpriteGeometry.Frame bandFrame = new SpriteGeometry.Frame();
        private final float[] canopyRed = new float[4], canopyGreen = new float[4], canopyBlue = new float[4];
        /** Where fasciaOf last found a band: the square it hangs on. */
        private int bandX, bandY;

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
                WorldScene.frameOf(bandFrame, band, textureOf(band));
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
        private boolean underLitCanopy(int chunkX, int chunkY, int x, int y, int z) {
            for (int up = z + 1; up <= z + 2; up++) {
                IsoGridSquare over = squareAt(chunkX, chunkY, x, y, up);
                if (over == null || over.getFloor() == null) {
                    continue;
                }
                canopied = true;
                return isCanopy(over, squareAt(chunkX, chunkY, x, y, up - 1)) && (mainsOn || over.haveElectricity()) && canopyDrop(x, y, up) > 0f;
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
            building.plain = false;
            IsoGridSquare ground = null;
            for (int under = z - 1; under >= 0 && ground == null; under--) {
                ground = cell.getGridSquare(x, y, under);
            }
            ColorInfo light = ground != null && ground.isSeen(playerIndex) ? view.torchLess.light(ground, playerIndex, torchless) : unseenLight;
            boolean lit = mainsOn || here.haveElectricity();
            float red = lit ? Soffits.under(light.r, Soffits.GLOW_RED) : light.r, green = lit ? Soffits.under(light.g, Soffits.GLOW_GREEN) : light.g;
            float blue = lit ? Soffits.under(light.b, Soffits.GLOW_BLUE) : light.b;
            Arrays.fill(canopyRed, red);
            Arrays.fill(canopyGreen, green);
            Arrays.fill(canopyBlue, blue);
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
                WorldScene.frameOf(bandFrame, band, texture);
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
            ColorInfo light = below != null && below.isSeen(playerIndex) ? view.torchLess.light(below, playerIndex, torchless) : unseenLight;
            frame.light(light.r, light.g, light.b);
            frame.open = 0f;
            layers.lie(SpriteGeometry.LAYERS_UP, -1f);
            geometry.addFace(mesh, frame, x, y, z, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
            layers.none();
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
                float layer, float da, float db) {
            building.plain = false;
            // A wall is as thick as a wall: two skins, one each side of the square's edge, both
            // with the sprite's outline. Through a window or a doorway the far skin is seen
            // behind the near one, which is the depth of the opening. What hangs on the wall
            // hangs on the skin inside the square.
            // A window that is a whole length of wall, a shop's front or the side of a sun room, is glass in a frame too.
            if (overLayer == 0 && Own.buildings()
                    && (frame.material == GLASS && addWindow(null, texture, x, y, z, da, db) || glazedWall && frame.material != GLASS && addWindow(mesh, texture, x, y, z, da, db))) {
                return;
            }
            // What shares the edge's plane with the wall lies its layers into the square.
            float way = db != 0f ? SpriteGeometry.LAYERS_X : SpriteGeometry.LAYERS_Y;
            layers.lie(way, layer);
            standEdge(mesh, texture, wall, door, x, y, z, way, layer, da, db);
            layers.none();
        }

        private void standEdge(SpriteGeometry.Mesh mesh, Texture texture, boolean wall, boolean door, int x, int y, int z,
                float way, float layer, float da, float db) {
            float inX = db * WALL_HALF, inY = da * WALL_HALF;
            if (wall && seeThrough) {
                // A fence of wire or of rails is one sheet on the line between its squares. As two
                // skins a wall's thickness apart, the eye saw one lattice through the other and
                // the two beat against each other as it moved.
                geometry.addFace(mesh, frame, x, y, z, 0f, 0f, 0f, da, db, 0f, 0f, 0f, 1f);
                return;
            }
            if (!wall) {
                // A finger's width clear of the skin: lying in the skin's own plane, the two are
                // drawn through each other.
                geometry.shownA = inX + db * HUNG_CLEAR;
                geometry.shownB = inY + da * HUNG_CLEAR;
                float material = frame.material;
                if (material == GLASS) {
                    faceIntoRoom(x, y, z, da != 0f);
                }
                if (door && doorLeaf && overLayer == 0) {
                    // A leaf hangs in the middle of the wall's thickness: a built one where the wall is, the sprite's own with the edge's layers.
                    layers.none();
                    boolean built = Own.buildings() && addBuiltDoor(texture, x, y, z, da, db);
                    layers.lie(way, layer);
                    if (built || addDoor(mesh, texture, x, y, z, 0f, 0f, da, db)) {
                        geometry.shownA = geometry.shownB = 0f;
                        return;
                    }
                }
                // And three layers more, which are thicker the further off: there the picture's depth is coarser.
                layers.lie(way, layer + 3f);
                // A door's top is a straight line, and its sprite has it as a staircase of pixels
                // on the slant. The face stops at the door's top, so the line is the face's own edge.
                geometry.addFace(mesh, frame, x, y, z, 0f, 0f, 0f, da, db, 0f, 0f, 0f, door ? doorTop(texture, 0f, 0f, da, db) : 1f);
                if (casing && overLayer == 0) {
                    // A door's frame goes round the doorway on both sides of the wall. The game
                    // has it on the side its view saw, and from the other the doorway was bare.
                    geometry.shownA = -geometry.shownA;
                    geometry.shownB = -geometry.shownB;
                    layers.lie(way, layer - 3f);
                    geometry.addFace(mesh, frame, x, y, z, 0f, 0f, 0f, da, db, 0f, 0f, 0f, 1f);
                }
                geometry.shownA = geometry.shownB = 0f;
                geometry.turned = false;
                frame.material = material;
                return;
            }
            float fromStart = unpainted(texture, 0f, 0f, da, db);
            float fromEnd = unpainted(texture, da, db, -da, -db);
            float fromTop = unpaintedTop(texture, 0f, 0f, da, db);
            boolean startIsLow = SpriteGeometry.frameU(frame, 0f, 0f) < SpriteGeometry.frameU(frame, da, db);
            float[] opening = openingOf(texture, 0f, 0f, da, db);
            int at = da != 0f ? 0 : OPENING_FLOATS;
            // Where the wall's own sprite is painted to its end, the end is the wall's: a corner
            // post's sprite is a sliver at one end of its square and nothing at the other.
            float pixels = Math.abs(SpriteGeometry.frameU(frame, da, db) - SpriteGeometry.frameU(frame, 0f, 0f));
            boolean startPainted = paintedOnWall(texture, 0f, 0f, da, db, (fromStart + 1.5f) / pixels, SEAL_HEIGHT, 0f);
            boolean endPainted = paintedOnWall(texture, 0f, 0f, da, db, 1f - (fromEnd + 1.5f) / pixels, SEAL_HEIGHT, 0f);
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
            // An inside wall is its paper's stripes, each a face of its own: see Papers.
            paper = overLayer == 0 && paperedWall && Own.textures() ? Papers.of(texture, frame, da != 0f) : null;
            if (paper == null && overLayer == 0 && paperedWall && Papers.unread(texture)) {
                building.signed = false;
            }
            SpriteGeometry.Mesh near = laid(mesh, texture, laidWall, x, y, z);
            if (paper != null) {
                int key = WallSides.key(paper.red, paper.green, paper.blue, WallSides.FLAT);
                WallSides.tint(key, paperTint);
                near = solid.get(texture.getID(), key);
            }
            // A wall that shows its own sprite shows it stood square, so its courses run on into the next length: see Flats.
            near = stoodSquare(near, texture, da != 0f);
            int nearFlat = frame.flat;
            addSkin(near, opening, at, x, y, z, 0f, 0f, da, db, fromStart, fromEnd, fromTop, startIsLow);
            frame.flat = 0;
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
                    && (!wallOpens || opening[at + OPENING_START] >= 0f) && paintedOnWall(texture, 0f, 0f, da, db, 0.5f, WHOLE_WALL, 0f) ? farFacing : null;
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
                fromStart = unpainted(faced.texture, 0f, 0f, da, db);
                fromEnd = unpainted(faced.texture, da, db, -da, -db);
                fromTop = unpaintedTop(faced.texture, 0f, 0f, da, db);
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
                    lightAlong(farLight, da != 0f);
                }
            }
            // The far skin is papered as the near one only where it shows the same sprite.
            paper = faced == null && tint == 0 ? paper : null;
            SpriteGeometry.Mesh far = near;
            if (faced != null) {
                far = stoodSquare(laid(solid.get(faced.texture.getID()), faced.texture, Masonry.suits(faced.sprite.getName()), x, y, z), faced.texture, da != 0f);
            } else if (tint != 0) {
                far = solid.get(texture.getID(), tint);
            } else {
                frame.flat = nearFlat;
            }
            addSkin(far, opening, at, x, y, z, 0f, 0f, da, db, fromStart, fromEnd, fromTop, startIsLow);
            frame.flat = 0;
            paper = null;
            // Out of doors, on a whole wall in one colour.
            if (tint != 0 && farColour != null && frame.open > 0.5f && opening[at + OPENING_TOP] >= WHOLE_WALL) {
                addCourses(opening, at, x, y, z, 0f, 0f, da, db, -Math.signum(inX), -Math.signum(inY));
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
                addReveals(opening, at, x, y, z, 0f, 0f, da, db, inX, inY);
            }
            if (overLayer == 0) {
                addCaps(mesh, texture, opening[at + OPENING_TOP], (startJoin & WallJoins.OPEN) != 0, (endJoin & WallJoins.OPEN) != 0,
                        startPainted && endPainted, x, y, z, 0f, 0f, da, db, inX, inY);
            }
        }

        /**
         * Lights the frame as the square a wall's far side stands in is lit, shaded from one end of
         * the wall to the other as the squares along it are: each end is the mean of that square
         * and the next one along the wall on that side, where the two are both indoors or both
         * out. One light for the whole length made every square of a wall its own flat panel. By a
         * lamp, whose light the game gives a colour, a shop's front was a teal square and a pink
         * one in a grey wall.
         */
        private void lightAlong(int at, boolean north) {
            int step = north ? LIGHT_SPAN : 1;
            frame.light(lightRed[at], lightGreen[at], lightBlue[at]);
            frame.open = lightOpen[at];
            for (int end = 0; end < 2; end++) {
                int beside = at + (end == 0 ? -step : step);
                // The lights are read a square past the chunk and no further: a far side that is in that last row has nothing beyond one end.
                boolean alike = beside >= 0 && beside < lightOpen.length && lightOpen[beside] == lightOpen[at];
                float red = alike ? (lightRed[at] + lightRed[beside]) / 2f : lightRed[at];
                float green = alike ? (lightGreen[at] + lightGreen[beside]) / 2f : lightGreen[at];
                float blue = alike ? (lightBlue[at] + lightBlue[beside]) / 2f : lightBlue[at];
                // The square's corners go north-west, north-east, south-east, south-west: a wall on its
                // north edge ends at the first two, one on its west edge at the first and the last.
                int near = end == 0 ? 0 : north ? 1 : 3, far = end == 0 ? (north ? 3 : 1) : 2;
                frame.cornerRed[near] = frame.cornerRed[far] = red;
                frame.cornerGreen[near] = frame.cornerGreen[far] = green;
                frame.cornerBlue[near] = frame.cornerBlue[far] = blue;
            }
        }

        /**
         * The name of the picture of the sprite the frame holds, read as a floor, a wall or a
         * roof on the plane given: see Flats. 0 for a sprite with none, and for one not yet
         * read, which is drawn from its sprite in a build that is made again.
         */
        private int flatOf(Texture texture, int as, float high, float climbA, float climbB) {
            int flat = Flats.of(texture, frame, as, high, climbA, climbB);
            if (flat == Flats.PENDING) {
                building.signed = false;
                return 0;
            }
            return flat;
        }

        /**
         * The mesh a wall's skin goes in when it is to show the given sprite as the frame has
         * it: the mesh of the sprite stood square, with the frame told so, where the skin was
         * to go in the sprite's own mesh and is the wall itself. Any other mesh, one painted
         * over, laid as masonry or papered, is given back as it is.
         */
        private SpriteGeometry.Mesh stoodSquare(SpriteGeometry.Mesh mesh, Texture texture, boolean north) {
            frame.flat = 0;
            if (overLayer != 0 || mesh.tint != 0 || mesh.texture != texture.getID()) {
                return mesh;
            }
            int stood = flatOf(texture, north ? Flats.NORTH : Flats.WEST, 0f, 0f, 0f);
            if (stood == 0) {
                return mesh;
            }
            frame.flat = north ? SpriteGeometry.FLAT_NORTH : SpriteGeometry.FLAT_WEST;
            return solid.get(stood);
        }

        /** Whether the sprite being added is of a sheet of outside walls, and of one of the walls under a roof's end: see laid. */
        private boolean laidWall, gableSheet;
        private final float[] laidTint = new float[4];

        /**
         * The mesh a wall's skin goes in: the sprite's own, or for a wall of brick, lapped boards
         * or stone the one the picture lays as that over the sprite. See Masonry. What is drawn
         * over a wall, ivy or a stain, stays as painted. suits is whether the sprite is of a
         * sheet of outside walls, and x, y, z the square the wall is on.
         */
        private SpriteGeometry.Mesh laid(SpriteGeometry.Mesh mesh, Texture texture, boolean suits, int x, int y, int z) {
            if (overLayer != 0 || !Own.textures()) {
                return mesh;
            }
            // Upright boards are made board by board in the sprite's own colour: see timber.
            if (frame.material == BOARDS) {
                float[] colour = SpriteColours.of(texture);
                if (colour == null) {
                    building.signed &= !SpriteColours.unread(texture);
                    return mesh;
                }
                return solid.get(texture.getID(), WallSides.laid(colour[0], colour[1], colour[2], 0f, 0f, 0f, WallSides.BOARDED));
            }
            if (!suits) {
                return mesh;
            }
            // A building's walls are many sprites, with a window in one, a doorway in the next and
            // a low one along the roof, each read on its own, and a few read wrongly: the piece
            // round a doorway has little wall in it to read. What the plain walls of the building
            // show is read from the most wall. So a wall of the colour of the building's finish is
            // built as that finish is, whatever it reads as itself, and one wall is one thing
            // from end to end. The wall under a roof's end, cut to the slope with a vent in it,
            // is built so whatever its colour.
            // A wall on a building's south or east side stands on the square outside it, so the
            // building is looked for under the square and then under the two the wall may face.
            Texture finish = null;
            for (int side = 0; side < 3 && finish == null; side++) {
                int underX = x - (side == 2 ? 1 : 0), underY = y - (side == 1 ? 1 : 0);
                for (int level = 0; level <= z && finish == null; level++) {
                    IsoGridSquare under = cell.getGridSquare(underX, underY, level);
                    IsoBuilding standing = under == null ? null : under.getBuilding();
                    if (standing != null) {
                        finish = WallFinish.outside(cell, standing, level, under.getRoomID(), roundNow);
                    }
                }
            }
            boolean unread = false;
            int how = 0;
            if (finish != null && finish != texture) {
                int built = Masonry.of(finish);
                unread = Masonry.unread(finish);
                int read = gableSheet ? 0 : Masonry.of(texture);
                unread |= !gableSheet && Masonry.unread(texture);
                if (built != 0 && !gableSheet) {
                    float[] own = SpriteColours.of(texture);
                    WallSides.tint(built, laidTint);
                    unread |= SpriteColours.unread(texture);
                    // The game paints a wall on a north edge lighter than the same wall on a west
                    // edge, as its own light falls. A wall that reads as the finish does, or as
                    // nothing, is the finish if it is the finish's colour made lighter or darker.
                    boolean samePaint = own != null && (read == 0 || (read & 15) == (built & 15)) && WallSides.oneColourInTwoLights(own, laidTint);
                    boolean sameColour = own != null && Math.abs(own[0] - laidTint[0]) + Math.abs(own[1] - laidTint[1]) + Math.abs(own[2] - laidTint[2]) <= SAME_FINISH;
                    built = samePaint || sameColour ? built : 0;
                }
                how = built != 0 ? built : read;
            } else if (!gableSheet) {
                how = Masonry.of(texture);
                unread |= Masonry.unread(texture);
            }
            // A sprite is read by the next frame drawn. A build that went without any it asked
            // for is made again when next due, though nothing it read of the map has changed:
            // what was read may lay the wall one way now and the rest another once it is in.
            building.signed &= !unread;
            if (how == 0) {
                return mesh;
            }
            return solid.get(texture.getID(), how);
        }
        /** Whether the door being added stands open across its doorway, and whether it is a leaf with a thickness. */
        private boolean doorSwung, doorLeaf;
        /** Whether the sprite being added is the frame round a doorway. */
        private boolean casing;

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
                Arrays.fill(span, Float.NaN);
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

        /**
         * A curtain, hung: a rod over the window and the cloth below it in folds, drawn across in
         * one length or pulled back to the two ends as the game's sprite has it, in the sprite's
         * colour. Cloth rolled up over the window is a roll. Where it hangs is measured off the
         * sprite: see Drapes. False, adding nothing, for a sprite not measured yet or with no
         * cloth on its edge.
         */
        private boolean addCurtain(Texture texture, IsoObjectType type, int x, int y, int z) {
            boolean north = type == IsoObjectType.curtainN || type == IsoObjectType.curtainS;
            boolean far = type == IsoObjectType.curtainS || type == IsoObjectType.curtainE;
            Drapes.Shape shape = Drapes.of(texture, frame, north, far);
            if (shape == null) {
                if (Drapes.unread(texture)) {
                    building.signed = false;
                }
                return false;
            }
            windowX = x + (far && !north ? 1 : 0);
            windowY = y + (far && north ? 1 : 0);
            windowAlongX = north ? 1f : 0f;
            windowAlongY = north ? 0f : 1f;
            windowFoot = z * ViewCamera.LEVEL_HEIGHT + (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f;
            float level = ViewCamera.LEVEL_HEIGHT, into = far ? -1f : 1f, out = into * (WALL_HALF + DRAPE_CLEAR);
            float red = frame.red, green = frame.green, blue = frame.blue;
            SpriteGeometry.Mesh cloth = solid.get(PLAIN, WallSides.key(shape.red, shape.green, shape.blue, WallSides.FLAT));
            cloth.sway = 0f;
            cloth.open = frame.open + 32f * DRAPE;
            float[] c = reveal;
            if (shape.hangs.length == 0) {
                // Rolled up and tied: a roll as thick as the sprite paints it, along the window's head.
                float middle = (shape.foot + shape.head) / 2f, round = Math.min((shape.head - shape.foot) * level / 2f, 0.11f);
                for (int side = 0; side < 8; side++) {
                    double a0 = side * Math.PI / 4.0, a1 = (side + 1) * Math.PI / 4.0;
                    float d0 = out + round * (float) Math.cos(a0), h0 = middle + round * (float) Math.sin(a0) / level;
                    float d1 = out + round * (float) Math.cos(a1), h1 = middle + round * (float) Math.sin(a1) / level;
                    windowPoint(c, 0, shape.start, h0, d0);
                    windowPoint(c, 1, shape.end, h0, d0);
                    windowPoint(c, 2, shape.end, h1, d1);
                    windowPoint(c, 3, shape.start, h1, d1);
                    SpriteGeometry.addPlainQuad(cloth, c, red, green, blue);
                }
                return true;
            }
            float whole = shape.end - shape.start;
            int seed = x * 73856093 ^ y * 19349663 ^ z * 83492791;
            for (int hung = 0; hung < shape.hangs.length; hung += 2) {
                float t0 = shape.hangs[hung], t1 = shape.hangs[hung + 1], wide = t1 - t0;
                boolean across = wide > 0.6f * whole;
                // Cloth pulled back is the same cloth in less room: its folds are narrower and deeper.
                float pitch = across ? FOLD_WIDE : Math.max(FOLD_NARROW, wide / 5f), deep = across ? FOLD_DEEP : FOLD_DEEP_BACK;
                int folds = Math.max(2, Math.round(wide / pitch)), strips = folds * FOLD_STRIPS;
                float hem = shape.foot + (shape.head - shape.foot) * HEM;
                float before = 0f, beforeFoot = 0f;
                for (int strip = 0; strip <= strips; strip++) {
                    int fold = strip / FOLD_STRIPS;
                    // No two folds hang quite alike, and cloth spreads a little towards its foot.
                    float much = deep * (0.65f + 0.7f * chance(seed + hung * 31 + fold));
                    float turn = (float) Math.sin(2.0 * Math.PI * strip / FOLD_STRIPS + 6.0 * chance(seed + hung));
                    float here = out + into * much * turn, hereFoot = out + into * much * 1.3f * turn;
                    if (strip > 0) {
                        float a = t0 + wide * (strip - 1) / strips, b = t0 + wide * strip / strips;
                        windowPoint(c, 0, a, hem, beforeFoot);
                        windowPoint(c, 1, b, hem, hereFoot);
                        windowPoint(c, 2, b, shape.head, here);
                        windowPoint(c, 3, a, shape.head, before);
                        SpriteGeometry.addPlainQuad(cloth, c, red, green, blue);
                        windowPoint(c, 0, a, shape.foot, beforeFoot);
                        windowPoint(c, 1, b, shape.foot, hereFoot);
                        windowPoint(c, 2, b, hem, hereFoot);
                        windowPoint(c, 3, a, hem, beforeFoot);
                        SpriteGeometry.addPlainQuad(cloth, c, red * HEM_SHADE, green * HEM_SHADE, blue * HEM_SHADE);
                    }
                    before = here;
                    beforeFoot = hereFoot;
                }
            }
            SpriteGeometry.Mesh rod = solid.get(PLAIN, WallSides.key(ROD_RED, ROD_GREEN, ROD_BLUE, WallSides.FLAT));
            rod.sway = 0f;
            rod.open = frame.open + 32f * WOOD;
            float half = ROD_THICK / 2f;
            for (int side = 0; side < 4; side++) {
                float d0 = out + into * (FOLD_DEEP_BACK + half) + (side == 0 || side == 3 ? -half : half), d1 = out + into * (FOLD_DEEP_BACK + half) + (side < 2 ? half : -half);
                float h0 = shape.head + (side < 2 ? -half : half) / level, h1 = shape.head + (side == 1 || side == 2 ? half : -half) / level;
                windowPoint(c, 0, shape.start - ROD_PAST, h0, d0);
                windowPoint(c, 1, shape.end + ROD_PAST, h0, d0);
                windowPoint(c, 2, shape.end + ROD_PAST, h1, d1);
                windowPoint(c, 3, shape.start - ROD_PAST, h1, d1);
                SpriteGeometry.addPlainQuad(rod, c, red, green, blue);
            }
            return true;
        }

        /**
         * A door, built: a leaf as thick as a door in the colour of the game's sprite, with glass
         * in its lights, mouldings round its panels or boards on ledges where the sprite draws
         * them, and a knob on each face. Its outline, its lights and how it is worked are measured
         * off the sprite: see Doors. False, adding nothing, for a sprite not measured yet or one
         * that is no leaf with four straight sides.
         */
        private boolean addBuiltDoor(Texture texture, int x, int y, int z, float da, float db) {
            boolean north = da != 0f;
            Doors.Shape door = Doors.of(texture, frame, north);
            if (door == null) {
                if (Doors.unread(texture)) {
                    building.signed = false;
                }
                return false;
            }
            Windows.Shape leaf = door.leaf;
            float out = doorSwung ? WALL_HALF + HUNG_CLEAR + DOOR_HALF : 0f;
            windowX = x + db * out;
            windowY = y + da * out;
            windowAlongX = da;
            windowAlongY = db;
            windowFoot = z * ViewCamera.LEVEL_HEIGHT + (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f;
            // A door shut in its doorway has a room or the street on each face. Swung open it stands in its own square.
            int ownLight = (waterLocalX + 1) * LIGHT_SPAN + waterLocalY + 1, beyondLight = north ? ownLight - 1 : ownLight - LIGHT_SPAN;
            boolean same = doorSwung, unseen = !same && lightOpen[beyondLight] < 0;
            float[] red = {frame.red, same ? frame.red : unseen ? unseenLight.r : lightRed[beyondLight]};
            float[] green = {frame.green, same ? frame.green : unseen ? unseenLight.g : lightGreen[beyondLight]};
            float[] blue = {frame.blue, same ? frame.blue : unseen ? unseenLight.b : lightBlue[beyondLight]};
            float[] open = {frame.open, same ? frame.open : unseen ? 1f : lightOpen[beyondLight]};

            boolean wooden = door.kind == Doors.PLANKED || leaf.red - leaf.blue > WOODEN;
            SpriteGeometry.Mesh wood = solid.get(PLAIN, WallSides.key(leaf.red, leaf.green, leaf.blue, WallSides.FLAT));
            SpriteGeometry.Mesh joint = solid.get(PLAIN, WallSides.key(leaf.red * JOINT_SHADE, leaf.green * JOINT_SHADE, leaf.blue * JOINT_SHADE, WallSides.FLAT));
            SpriteGeometry.Mesh metal = solid.get(PLAIN);
            wood.sway = joint.sway = metal.sway = 0f;
            float level = ViewCamera.LEVEL_HEIGHT, wide = leaf.end - leaf.start;
            float inStart = leaf.start + leaf.startWide, inEnd = leaf.end - leaf.endWide;
            // What is below the lowest light, or the whole leaf where there is none, is where panels are.
            float panelled = leaf.head;
            for (int pane = 0; pane < leaf.panes.length; pane += 5) {
                panelled = Math.min(panelled, leaf.panes[pane + 2]);
            }
            float[] up = panelled < leaf.head ? PANELS_UP_BELOW : PANELS_UP;
            float knob = door.handleAtEnd ? leaf.end - KNOB_IN * wide : leaf.start + KNOB_IN * wide;
            for (int side = 0; side < 2; side++) {
                float face = (side == 0 ? 1f : -1f) * DOOR_HALF;
                float r = red[side], g = green[side], b = blue[side];
                wood.open = joint.open = open[side] + 32f * (wooden ? WOOD : PAINT);
                if (door.kind == Doors.PLANKED) {
                    // Boards side by side with an open joint between each two, on a back a little behind them.
                    float board = door.board > 0.08f ? door.board : BOARD_USUAL;
                    int boards = Math.max(2, Math.round(wide / board));
                    addJoinery(joint, leaf.start, leaf.end, leaf.foot, leaf.head, face * 0.7f, r, g, b);
                    for (int i = 0; i < boards; i++) {
                        float from = leaf.start + wide * i / boards + (i == 0 ? 0f : BOARD_GAP / 2f), to = leaf.start + wide * (i + 1) / boards - (i == boards - 1 ? 0f : BOARD_GAP / 2f);
                        addJoinery(wood, from, to, leaf.foot, leaf.head, face, r, g, b);
                    }
                    for (float at : LEDGES_AT) {
                        float middle = leaf.foot + (leaf.head - leaf.foot) * at, half = LEDGE_HIGH / (2f * level);
                        addJoinery(wood, leaf.start + 0.02f, leaf.end - 0.02f, middle - half, middle + half, face + Math.signum(face) * LEDGE_PROUD, r, g, b);
                    }
                } else {
                    if (leaf.startWide > 0f) {
                        addJoinery(wood, leaf.start, inStart, leaf.foot, leaf.head, face, r, g, b);
                    }
                    if (leaf.endWide > 0f) {
                        addJoinery(wood, inEnd, leaf.end, leaf.foot, leaf.head, face, r, g, b);
                    }
                    if (leaf.headHigh > 0f) {
                        addJoinery(wood, inStart, inEnd, leaf.head - leaf.headHigh, leaf.head, face, r, g, b);
                    }
                    if (leaf.footHigh > 0f) {
                        addJoinery(wood, inStart, inEnd, leaf.foot, leaf.foot + leaf.footHigh, face, r, g, b);
                    }
                    for (int bar = 0; bar < leaf.bars.length; bar += 4) {
                        addJoinery(wood, leaf.bars[bar], leaf.bars[bar + 1], leaf.bars[bar + 2], leaf.bars[bar + 3], face, r, g, b);
                    }
                }
                if (door.kind == Doors.PANELLED && panelled - leaf.foot > 0.15f) {
                    float proud = face + Math.signum(face) * MOULDING_PROUD, high = panelled - leaf.foot, thick = MOULDING_WIDE / level;
                    for (int column = 0; column < PANELS_ACROSS.length; column += 2) {
                        float from = leaf.start + wide * PANELS_ACROSS[column], to = leaf.start + wide * PANELS_ACROSS[column + 1];
                        for (int row = 0; row < up.length; row += 2) {
                            float foot = leaf.foot + high * up[row], head = leaf.foot + high * up[row + 1];
                            float mr = r * MOULDING_SHADE, mg = g * MOULDING_SHADE, mb = b * MOULDING_SHADE;
                            addJoinery(wood, from, from + MOULDING_WIDE, foot, head, proud, mr, mg, mb);
                            addJoinery(wood, to - MOULDING_WIDE, to, foot, head, proud, mr, mg, mb);
                            addJoinery(wood, from + MOULDING_WIDE, to - MOULDING_WIDE, foot, foot + thick, proud, mr, mg, mb);
                            addJoinery(wood, from + MOULDING_WIDE, to - MOULDING_WIDE, head - thick, head, proud, mr, mg, mb);
                            // The field of the panel, raised in its middle.
                            addJoinery(wood, from + 2f * MOULDING_WIDE, to - 2f * MOULDING_WIDE, foot + 2f * thick, head - 2f * thick,
                                    face + Math.signum(face) * MOULDING_PROUD * 0.5f, r * FIELD_SHADE, g * FIELD_SHADE, b * FIELD_SHADE);
                        }
                    }
                }
                // The knob: a neck and a head of eight sides.
                metal.open = open[side] + 32f * BARE_METAL;
                float[] c = reveal;
                for (int part = 0; part < 2; part++) {
                    float round = part == 0 ? KNOB_ROUND * KNOB_NECK : KNOB_ROUND;
                    float from = face + Math.signum(face) * (part == 0 ? 0f : KNOB_OUT * 0.45f), to = face + Math.signum(face) * (part == 0 ? KNOB_OUT * 0.45f : KNOB_OUT);
                    for (int turn = 0; turn < 8; turn++) {
                        double a0 = turn * Math.PI / 4.0, a1 = (turn + 1) * Math.PI / 4.0;
                        float t0 = knob + round * (float) Math.cos(a0), h0 = KNOB_HIGH + round * (float) Math.sin(a0) / level;
                        float t1 = knob + round * (float) Math.cos(a1), h1 = KNOB_HIGH + round * (float) Math.sin(a1) / level;
                        windowPoint(c, 0, t0, h0, from);
                        windowPoint(c, 1, t1, h1, from);
                        windowPoint(c, 2, t1, h1, to);
                        windowPoint(c, 3, t0, h0, to);
                        SpriteGeometry.addPlainQuad(metal, c, r, g, b);
                        windowPoint(c, 0, knob, KNOB_HIGH, to);
                        windowPoint(c, 1, t0, h0, to);
                        windowPoint(c, 2, t1, h1, to);
                        windowPoint(c, 3, knob, KNOB_HIGH, to);
                        SpriteGeometry.addPlainQuad(metal, c, r, g, b);
                    }
                }
            }
            SpriteGeometry.Mesh glass = laid.get(PLAIN, WallSides.PANES);
            glass.sway = 0f;
            glass.open = frame.open + 32f * PANE;
            glass.face(db, da, 0f);
            for (int pane = 0; pane < leaf.panes.length; pane += 5) {
                float t0 = leaf.panes[pane], t1 = leaf.panes[pane + 1], h0 = leaf.panes[pane + 2], h1 = leaf.panes[pane + 3];
                if (leaf.panes[pane + 4] >= 1f) {
                    addGlass(glass, t0, h0, 0f, 0f, t1, h0, 1f, 0f, t1, h1, 1f, 1f);
                    addGlass(glass, t0, h0, 0f, 0f, t1, h1, 1f, 1f, t0, h1, 0f, 1f);
                } else {
                    addShards(glass, t0, t1, h0, h1, leaf.panes[pane + 4], x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ pane * 2654435);
                }
            }
            return true;
        }
        /** Whether the sprite being added is a window's that is also a wall. */
        private boolean glazedWall;
        /** The build being filled. */
        private ChunkMeshes building;
        /** Where the window being built is: the corner of its square its edge starts from, the edge's direction, and its foot. */
        private float windowX, windowY, windowAlongX, windowAlongY, windowFoot;

        /**
         * A window on the north or west edge of the square, built: the four sides of its frame
         * through the wall's thickness, a sill outside, its bars, and a sheet of glass in each
         * light that has one, the pieces left round the edge of one that is broken. Where and how
         * thick each is, and the frame's colour, are measured off the sprite: see Windows. Each
         * side of the glass is lit as its own side of the wall is. False, adding nothing, for a
         * sprite not measured yet or one that is no window in a square frame.
         */
        private boolean addWindow(SpriteGeometry.Mesh wall, Texture texture, int x, int y, int z, float da, float db) {
            boolean glazedOnly = wall != null;
            boolean north = da != 0f;
            Windows.Shape shape = Windows.of(texture, frame, north);
            if (shape == null) {
                // The sprite is measured by the next frame drawn. A build that went without is
                // made again when next due, though nothing it read of the map has changed.
                if (Windows.unread(texture)) {
                    building.signed = false;
                }
                return false;
            }
            if (glazedOnly && shape.panes.length == 0) {
                return false;
            }
            IsoGridSquare beyond = cell.getGridSquare(north ? x : x - 1, north ? y - 1 : y, z);
            boolean ownIsRoom = here.getRoomID() != -1L || beyond == null || beyond.getRoomID() == -1L;
            boolean beyondIsRoom = beyond != null && beyond.getRoomID() != -1L;
            boolean lit = lamplit(cell, ownIsRoom ? here : beyond, playerIndex);

            windowX = x;
            windowY = y;
            windowAlongX = da;
            windowAlongY = db;
            windowFoot = z * ViewCamera.LEVEL_HEIGHT + (geometry.lift[0] + geometry.lift[1] + geometry.lift[2] + geometry.lift[3]) / 4f;
            int ownLight = (waterLocalX + 1) * LIGHT_SPAN + waterLocalY + 1, beyondLight = north ? ownLight - 1 : ownLight - LIGHT_SPAN;
            boolean farUnseen = lightOpen[beyondLight] < 0;
            float[] red = {frame.red, farUnseen ? unseenLight.r : lightRed[beyondLight]};
            float[] green = {frame.green, farUnseen ? unseenLight.g : lightGreen[beyondLight]};
            float[] blue = {frame.blue, farUnseen ? unseenLight.b : lightBlue[beyondLight]};
            float[] open = {frame.open, farUnseen ? 1f : lightOpen[beyondLight]};

            SpriteGeometry.Mesh joinery = solid.get(PLAIN, WallSides.key(shape.red, shape.green, shape.blue, WallSides.FLAT));
            joinery.sway = 0f;
            float made = shape.red - shape.blue > WOODEN ? WOOD : PAINT;
            float level = ViewCamera.LEVEL_HEIGHT, lap = FRAME_LAP / level;
            float deep = WALL_HALF + FRAME_PROUD;
            float inStart = shape.start + shape.startWide, inEnd = shape.end - shape.endWide;
            for (int side = 0; side < 2; side++) {
                float out = side == 0 ? 1f : -1f;
                joinery.open = open[side] + 32f * made;
                float r = red[side], g = green[side], b = blue[side];
                // A frame with no stile at one end runs on into the window on the next square.
                windowLit = side;
                if (shape.startWide > 0f) {
                    addMember(joinery, wall, x, y, z, shape.start - FRAME_LAP, inStart, shape.foot - lap, shape.head + lap, out * deep, r, g, b, open[side]);
                }
                if (shape.endWide > 0f) {
                    addMember(joinery, wall, x, y, z, inEnd, shape.end + FRAME_LAP, shape.foot - lap, shape.head + lap, out * deep, r, g, b, open[side]);
                }
                addMember(joinery, wall, x, y, z, inStart, inEnd, shape.head - shape.headHigh, shape.head + lap, out * deep, r, g, b, open[side]);
                addMember(joinery, wall, x, y, z, inStart, inEnd, shape.foot - lap, shape.foot + shape.footHigh, out * deep, r, g, b, open[side]);
                if (shape.arch != null) {
                    addArch(joinery, shape, out * deep, r, g, b);
                }
                // Rain is thrown clear of the wall by a sill, which a window has on the side that is out of doors.
                if (shape.foot > SILL_LOWEST && (side == 0 ? !ownIsRoom : ownIsRoom && !beyondIsRoom)) {
                    float pastStart = shape.startWide > 0f ? SILL_PAST + FRAME_LAP : 0f, pastEnd = shape.endWide > 0f ? SILL_PAST + FRAME_LAP : 0f;
                    addJoinery(joinery, shape.start - pastStart, shape.end + pastEnd, shape.foot - lap - SILL_THICK / level, shape.foot - lap + 0.004f / level,
                            out * (WALL_HALF + SILL_OUT), r, g, b);
                }
                for (int bar = 0; bar < shape.bars.length; bar += 4) {
                    float wide = Math.min(shape.bars[bar + 1] - shape.bars[bar], (shape.bars[bar + 3] - shape.bars[bar + 2]) * level);
                    addJoinery(joinery, shape.bars[bar], shape.bars[bar + 1], shape.bars[bar + 2], shape.bars[bar + 3],
                            out * Math.clamp(wide * BAR_DEPTH, BAR_THINNEST, BAR_DEEPEST), r, g, b);
                }
            }

            SpriteGeometry.Mesh glass = laid.get(PLAIN, WallSides.PANES);
            glass.sway = 0f;
            glass.open = frame.open + 32f * (lit ? LIT_PANE : PANE);
            // A pane faces into its room, which is how the picture tells its street side.
            float faces = ownIsRoom ? 1f : -1f;
            glass.face(db * faces, da * faces, 0f);
            for (int pane = 0; pane < shape.panes.length; pane += 5) {
                float t0 = shape.panes[pane], t1 = shape.panes[pane + 1], h0 = shape.panes[pane + 2], h1 = shape.panes[pane + 3];
                if (shape.panes[pane + 4] >= 1f) {
                    addGlass(glass, t0, h0, 0f, 0f, t1, h0, 1f, 0f, t1, h1, 1f, 1f);
                    addGlass(glass, t0, h0, 0f, 0f, t1, h1, 1f, 1f, t0, h1, 0f, 1f);
                } else {
                    addShards(glass, t0, t1, h0, h1, shape.panes[pane + 4], x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ pane * 2654435);
                }
            }
            if (shape.arch != null) {
                // The light under the arch, from where it springs up to the frame's inside.
                int places = shape.arch.length / 3;
                float rise = Math.max(shape.arch[(places / 2) * 3 + 1] - shape.head, 0.001f);
                for (int place = 0; place + 1 < places; place++) {
                    int at = place * 3;
                    if (shape.arch[at + 2] < 0.5f || shape.arch[at + 5] < 0.5f) {
                        continue;
                    }
                    float t0 = shape.start + (shape.end - shape.start) * place / (places - 1), t1 = shape.start + (shape.end - shape.start) * (place + 1) / (places - 1);
                    float u0 = (float) place / (places - 1), u1 = (float) (place + 1) / (places - 1);
                    float in0 = shape.arch[at + 1], in1 = shape.arch[at + 4];
                    addGlass(glass, t0, shape.head, u0, 0f, t1, shape.head, u1, 0f, t1, in1, u1, (in1 - shape.head) / rise);
                    addGlass(glass, t0, shape.head, u0, 0f, t1, in1, u1, (in1 - shape.head) / rise, t0, in0, u0, (in0 - shape.head) / rise);
                }
            }
            return true;
        }

        /**
         * One side of the arched head of a window's frame, from the glass's plane out to across
         * tiles into the square: its face, a length for each place the arch was measured at, and
         * its rims outside and inside.
         */
        private void addArch(SpriteGeometry.Mesh mesh, Windows.Shape shape, float across, float red, float green, float blue) {
            int places = shape.arch.length / 3;
            float[] c = reveal;
            for (int place = 0; place + 1 < places; place++) {
                int at = place * 3;
                float t0 = shape.start + (shape.end - shape.start) * place / (places - 1), t1 = shape.start + (shape.end - shape.start) * (place + 1) / (places - 1);
                float out0 = Math.max(shape.arch[at], shape.head), in0 = Math.max(shape.arch[at + 1], shape.head);
                float out1 = Math.max(shape.arch[at + 3], shape.head), in1 = Math.max(shape.arch[at + 4], shape.head);
                if (out0 <= in0 && out1 <= in1) {
                    continue;
                }
                windowPoint(c, 0, t0, in0, across);
                windowPoint(c, 1, t1, in1, across);
                windowPoint(c, 2, t1, out1, across);
                windowPoint(c, 3, t0, out0, across);
                SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
                windowPoint(c, 0, t0, out0, 0f);
                windowPoint(c, 1, t1, out1, 0f);
                windowPoint(c, 2, t1, out1, across);
                windowPoint(c, 3, t0, out0, across);
                SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
                windowPoint(c, 0, t0, in0, 0f);
                windowPoint(c, 1, t1, in1, 0f);
                windowPoint(c, 2, t1, in1, across);
                windowPoint(c, 3, t0, in0, across);
                SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
            }
        }
        /** Which side of the window is being added: 0 its own square's, 1 the far one. */
        private int windowLit;

        /**
         * A side of a window's frame. In a window that is a length of wall, a shop's front, the
         * broad parts of the frame are wall, below the glass and over it, and show the wall's own
         * sprite on the skin; their edges, round the glass, are closed in the frame's colour.
         */
        private void addMember(SpriteGeometry.Mesh joinery, SpriteGeometry.Mesh wall, int x, int y, int z, float from, float to, float foot, float head,
                float across, float red, float green, float blue, float open) {
            if (wall == null || Math.min(to - from, (head - foot) * ViewCamera.LEVEL_HEIGHT) <= WALL_MEMBER) {
                addJoinery(joinery, from, to, foot, head, across, red, green, blue);
                return;
            }
            float out = Math.signum(across);
            addJoinery(joinery, from, to, foot, head, out * (WALL_HALF - 0.003f), red, green, blue);
            float start = Math.max(from, 0f), end = Math.min(to, 1f), low = Math.max(foot, 0f), high = Math.min(head, 1f);
            float wasRed = frame.red, wasGreen = frame.green, wasBlue = frame.blue, wasOpen = frame.open;
            System.arraycopy(frame.cornerRed, 0, heldRed, 0, 4);
            System.arraycopy(frame.cornerGreen, 0, heldGreen, 0, 4);
            System.arraycopy(frame.cornerBlue, 0, heldBlue, 0, 4);
            if (windowLit == 1) {
                frame.light(red, green, blue);
                frame.open = open;
            }
            geometry.shownA = windowAlongY * out * WALL_HALF;
            geometry.shownB = windowAlongX * out * WALL_HALF;
            geometry.addFace(wall, frame, x, y, z, windowAlongX * start, windowAlongY * start, low, windowAlongX * (end - start), windowAlongY * (end - start), 0f,
                    0f, 0f, high - low);
            geometry.shownA = geometry.shownB = 0f;
            frame.red = wasRed;
            frame.green = wasGreen;
            frame.blue = wasBlue;
            frame.open = wasOpen;
            System.arraycopy(heldRed, 0, frame.cornerRed, 0, 4);
            System.arraycopy(heldGreen, 0, frame.cornerGreen, 0, 4);
            System.arraycopy(heldBlue, 0, frame.cornerBlue, 0, 4);
        }

        /** Where the point a share along the window's edge, up levels high and across tiles into its square is, into c at i. */
        private void windowPoint(float[] c, int i, float along, float up, float across) {
            set(c, i, windowX + windowAlongX * along + windowAlongY * across, windowY + windowAlongY * along + windowAlongX * across,
                    windowFoot + up * ViewCamera.LEVEL_HEIGHT);
        }

        /**
         * One side of a piece of a window's woodwork: from the glass's plane out to across tiles
         * into the square, its face and its four edges. The other side is added with the other
         * side's light.
         */
        private void addJoinery(SpriteGeometry.Mesh mesh, float from, float to, float foot, float head, float across, float red, float green, float blue) {
            float[] c = reveal;
            windowPoint(c, 0, from, foot, across);
            windowPoint(c, 1, to, foot, across);
            windowPoint(c, 2, to, head, across);
            windowPoint(c, 3, from, head, across);
            SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
            for (int edge = 0; edge < 4; edge++) {
                float along0 = edge == 1 ? to : from, along1 = edge == 0 ? from : to;
                float up0 = edge == 3 ? head : foot, up1 = edge == 2 ? foot : head;
                windowPoint(c, 0, along0, up0, 0f);
                windowPoint(c, 1, along1, up1, 0f);
                windowPoint(c, 2, along1, up1, across);
                windowPoint(c, 3, along0, up0, across);
                SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
            }
        }

        /** A triangle of glass in the window's plane: each corner a share along the edge, a height in levels, and where on its pane it is. */
        private void addGlass(SpriteGeometry.Mesh mesh, float t0, float h0, float u0, float v0, float t1, float h1, float u1, float v1,
                float t2, float h2, float u2, float v2) {
            float[] c = reveal;
            windowPoint(c, 0, t0, h0, 0f);
            windowPoint(c, 1, t1, h1, 0f);
            windowPoint(c, 2, t2, h2, 0f);
            mesh.add(c[0], c[1], c[2], u0, v0, frame.red, frame.green, frame.blue);
            mesh.add(c[3], c[4], c[5], u1, v1, frame.red, frame.green, frame.blue);
            mesh.add(c[6], c[7], c[8], u2, v2, frame.red, frame.green, frame.blue);
        }

        /**
         * What is left of a broken pane: teeth of glass standing in from the bars round it, the
         * longer the more of the pane the game's sprite still shows. Which teeth, and how long, is
         * settled by where the window is, so a window keeps its own break.
         */
        private void addShards(SpriteGeometry.Mesh mesh, float t0, float t1, float h0, float h1, float left, int seed) {
            float level = ViewCamera.LEVEL_HEIGHT, wide = t1 - t0, high = (h1 - h0) * level;
            float reach = Math.min(wide, high) * (0.25f + 0.75f * left);
            for (int side = 0; side < 4; side++) {
                float length = side < 2 ? wide : high;
                for (float at = 0f; at < length; ) {
                    float base = Math.min(SHARD_NARROWEST + (SHARD_WIDEST - SHARD_NARROWEST) * chance(seed++), length - at);
                    // Most of what is left is splinters along the putty, with a long blade here and
                    // there, and a blade leans: its point is seldom over its foot.
                    float long3 = chance(seed++);
                    float tip = Math.clamp(at + base * (SHARD_LEAN * (2f * chance(seed++) - 1f) + 0.5f), 0f, length);
                    float in = reach * (0.06f + 0.94f * long3 * long3 * long3);
                    boolean stands = chance(seed++) < 0.35f + 0.6f * left;
                    if (stands) {
                        // Along the foot, the head, the start and the end of the pane in turn.
                        float a0 = side < 2 ? at / wide : side == 2 ? 0f : 1f, b0 = side < 2 ? (side == 0 ? 0f : 1f) : at / high;
                        float a1 = side < 2 ? (at + base) / wide : a0, b1 = side < 2 ? b0 : (at + base) / high;
                        float a2 = side < 2 ? tip / wide : side == 2 ? in / wide : 1f - in / wide;
                        float b2 = side < 2 ? (side == 0 ? in / high : 1f - in / high) : tip / high;
                        addGlass(mesh, t0 + a0 * wide, h0 + b0 * (h1 - h0), a0, b0, t0 + a1 * wide, h0 + b1 * (h1 - h0), a1, b1,
                                t0 + a2 * wide, h0 + b2 * (h1 - h0), a2, b2);
                    }
                    at += base;
                }
            }
        }

        /** The lamps that burn within reach of the chunk being summed, found once a sum: a square of a room asks only these. */
        private final ArrayList<IsoLightSource> lampsNear = new ArrayList<>();

        /**
         * Turns the window on the north or west edge of the square being added to face into its
         * room, which is how the picture tells the street side of a pane from the room side, and
         * marks it as lit from inside when a lamp burns in the room. A window between two
         * rooms, or between two places out of doors, faces into its own square.
         */
        private void faceIntoRoom(int x, int y, int z, boolean north) {
            IsoGridSquare beyond = cell.getGridSquare(north ? x : x - 1, north ? y - 1 : y, z);
            boolean ownIsRoom = here.getRoomID() != -1L || beyond == null || beyond.getRoomID() == -1L;
            // A face on a north edge looks north, out of its square, and one on a west edge east, into it.
            geometry.turned = north == ownIsRoom;
            if (lamplit(cell, ownIsRoom ? here : beyond, playerIndex)) {
                frame.material = LIT_GLASS;
            }
        }

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
            float perTile = SpriteGeometry.PIXELS_ACROSS_PER_TILE * frame.scale;
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
            geometry.sealTop = sealTop;
            if (paper == null) {
                geometry.sealLow = startIsLow ? sealFrom : sealTo;
                geometry.sealHigh = startIsLow ? sealTo : sealFrom;
                geometry.addFace(mesh, frame, x, y, z, a + da * from, b + db * from, foot, da * (to - from), db * (to - from), 0f, 0f, 0f, head - foot);
                return;
            }
            // The mesh is painted over in the paper's colour as a whole, and each stripe is lit by
            // as much more or less as its own colour is of that.
            float made = frame.material;
            frame.material = paper.figured ? FIGURED_PAPER : made;
            float red = frame.red, green = frame.green, blue = frame.blue;
            System.arraycopy(frame.cornerRed, 0, paperRed, 0, 4);
            System.arraycopy(frame.cornerGreen, 0, paperGreen, 0, 4);
            System.arraycopy(frame.cornerBlue, 0, paperBlue, 0, 4);
            float begin = 0f;
            for (int stripe = 0; stripe < paper.ends.length; stripe++) {
                float end = paper.ends[stripe];
                float low = stripe == 0 ? from : Math.max(from, begin), high = stripe == paper.ends.length - 1 ? to : Math.min(to, end);
                begin = end;
                if (high <= low) {
                    continue;
                }
                float r = paper.colours[stripe * 3] / Math.max(paperTint[0], 0.02f), g = paper.colours[stripe * 3 + 1] / Math.max(paperTint[1], 0.02f),
                        bl = paper.colours[stripe * 3 + 2] / Math.max(paperTint[2], 0.02f);
                shade(red, green, blue, r, g, bl);
                float sealStart = low == from ? sealFrom : 0f, sealEnd = high == to ? sealTo : 0f;
                geometry.sealLow = startIsLow ? sealStart : sealEnd;
                geometry.sealHigh = startIsLow ? sealEnd : sealStart;
                geometry.addFace(mesh, frame, x, y, z, a + da * low, b + db * low, foot, da * (high - low), db * (high - low), 0f, 0f, 0f, head - foot);
            }
            frame.material = made;
            frame.red = red;
            frame.green = green;
            frame.blue = blue;
            System.arraycopy(paperRed, 0, frame.cornerRed, 0, 4);
            System.arraycopy(paperGreen, 0, frame.cornerGreen, 0, 4);
            System.arraycopy(paperBlue, 0, frame.cornerBlue, 0, 4);
        }
        /** The paper of the wall being added, or null for a wall that shows its sprite, and whether the sprite being added is an inside wall of a house. */
        private Papers.Paper paper;
        private boolean paperedWall;
        /** The colour the papered wall's mesh is painted over in, as the picture is told it, and the light of the piece being added while its stripes are. */
        private final float[] paperTint = new float[4], paperRed = new float[4], paperGreen = new float[4], paperBlue = new float[4];

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
                Arrays.fill(known, Float.NaN);
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
            float perPixel = 1f / (SpriteGeometry.PIXELS_PER_LEVEL * frame.scale);
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
            float perPixel = 1f / (SpriteGeometry.PIXELS_PER_LEVEL * frame.scale);
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
            float perPixel = 1f / (SpriteGeometry.PIXELS_PER_LEVEL * frame.scale);
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
            Texture shows = far == -1L ? WallFinish.outside(cell, building, z, own, roundNow) : WallFinish.room(cell, building, z, far, roundNow);
            if (far == -1L) {
                farFacing = WallFinish.facing(cell, building, z, north, own, roundNow);
            }
            if (shows == null || shows == texture) {
                return 0;
            }
            float[] colour = SpriteColours.of(shows);
            // The picture reads a sprite's colour and how it is laid on the next frame drawn, and tells nobody when it has:
            // a build that went without either is made again when next due.
            this.building.signed &= !SpriteColours.unread(shows) && (far != -1L || !Masonry.unread(shows));
            if (colour == null) {
                return 0;
            }
            farColour = colour;
            // Out of doors the skin is built of what the building's outside walls are: brick or
            // stone where their sprite reads so, and boards lapped along it otherwise.
            int laid = far == -1L ? Masonry.of(shows) : 0;
            return laid != 0 ? WallSides.over(laid) : WallSides.key(colour[0], colour[1], colour[2], far == -1L ? WallSides.SIDING : WallSides.FLAT);
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

        /**
         * How many frame pixels along (da, db) from (a, b), at SEAL_HEIGHT, the sprite has nothing
         * painted. 0 when it is painted from the start, and when nothing is within MOST_UNPAINTED:
         * that is a sprite which is not a wall from edge to edge, and it is left as it is.
         */
        private float unpainted(Texture texture, float a, float b, float da, float db) {
            float pixels = Math.abs(SpriteGeometry.frameU(frame, a + da, b + db) - SpriteGeometry.frameU(frame, a, b));
            int most = Math.round(MOST_UNPAINTED * frame.scale);
            for (int pixel = 0; pixel <= most; pixel++) {
                if (paintedOnWall(texture, a, b, da, db, (pixel + 0.5f) / pixels, SEAL_HEIGHT, 0f)) {
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
            building.plain = false;
            Roofs.Laid laid = Roofs.at(cell, x, y, z, roundNow, frame.scale);
            // A square with two roof sprites has one of them fitted. The other lies as it reads.
            // The fitted one holds what its sprite read as, which the fit leaves as it was.
            RoofFit.Tile tile = laid != null && laid.texture == texture ? laid.tile : Roofs.measure(frame, texture, x, y, z);
            // Half a tile at a ridge is not where the map has it: the ridge is made, see RoofFit.peaks.
            if (tile.half != 0) {
                return;
            }
            roof = laid == null ? null : laid.roof;
            if (tile.shaped) {
                addGable(x, y, z, tile.corners[0] - z, tile.corners[1] - z, tile.corners[3] - z, 0f, 0);
            } else if (tile.cutCorner < 0) {
                RoofFit.cornersOf(tile.fitAxis, tile.fitLow - z, tile.fitHigh - z, gable);
                addGable(x, y, z, gable[0], gable[1], gable[3], 0f, 0);
            }
            float northWest = tile.low - z, southEast = tile.high - z;
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
            // A tile that reads level and that the fit found to be the foot of a slope lies on the slope.
            if (tile.axis == RoofFit.LEVEL && tile.fitAxis == RoofFit.LEVEL) {
                roofsFlat++;
                float level = (northWest + southEast) / 2f;
                // A flat roof that lies on its level has the floor there under it already, and lies
                // clear of it: in the floor's own plane the two are drawn through each other.
                layers.lie(SpriteGeometry.LAYERS_UP, level < FLAT_ROOF_ON_FLOOR ? FLAT_ROOF_LAYERS : 0f);
                int made = geometry.addFace(roofLaidFlat(mesh, texture, level, 0f, 0f), frame, x, y, z, 0f, 0f, level, 1f, 0f, 0f, 0f, 1f, 0f);
                frame.flat = 0;
                layers.none();
                if (made > 0 && level > 2f * UNDER_ROOF) {
                    underRoof(x, y, z, level, 0f, 0f);
                }
                return;
            }
            boolean readAlongX = tile.axis == RoofFit.ALONG_X, alongX = tile.fitAxis == RoofFit.ALONG_X;
            float low = tile.fitLow - z, high = tile.fitHigh - z;
            float[] lift = geometry.lift;
            for (int corner = 0; corner < 4; corner++) {
                boolean farA = corner == 1 || corner == 2, farB = corner >= 2;
                float read = (readAlongX ? farA : farB) ? southEast : northWest;
                float fitted = (alongX ? farA : farB) ? high : low;
                heldLift[corner] = lift[corner];
                lift[corner] += (fitted - read) * ViewCamera.LEVEL_HEIGHT;
            }
            SpriteGeometry.Mesh slope = roofLaidFlat(mesh, texture, northWest, readAlongX ? southEast - northWest : 0f, readAlongX ? 0f : southEast - northWest);
            int added = readAlongX ? geometry.addFace(slope, frame, x, y, z, 0f, 0f, northWest, 1f, 0f, southEast - northWest, 0f, 1f, 0f)
                    : geometry.addFace(slope, frame, x, y, z, 0f, 0f, northWest, 1f, 0f, 0f, 0f, 1f, southEast - northWest);
            frame.flat = 0;
            System.arraycopy(heldLift, 0, lift, 0, 4);
            boolean placed = tile.fitted || tile.sure;
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
            if (tile.cutCorner >= 0) {
                underCut(tile, x, y, z);
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
         * The mesh a whole tile of roof goes in on the plane its sprite reads as, given as Flats
         * holds one: the mesh of the sprite laid flat on that plane, with the frame told so,
         * so the tile's courses meet the next tile's. The sprite's own mesh for a sprite that
         * does not paint the whole of the plane.
         */
        private SpriteGeometry.Mesh roofLaidFlat(SpriteGeometry.Mesh mesh, Texture texture, float high, float climbA, float climbB) {
            int flat = flatOf(texture, Flats.ROOF, high, climbA, climbB);
            if (flat == 0) {
                return mesh;
            }
            frame.flat = SpriteGeometry.FLAT_FLOOR;
            return solid.get(flat);
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
            SpriteGeometry.addOver(sheet, x, y, WorldScene.under(z, atOrigin) + lift[0], WorldScene.under(z, atOrigin + alongX) + lift[1],
                    WorldScene.under(z, atOrigin + alongX + alongY) + lift[2], WorldScene.under(z, atOrigin + alongY) + lift[3],
                    roofU, roofV, frame.red, frame.green, frame.blue);
            addEave(sheet, x, y, z, atOrigin, alongX, alongY, true);
            addRake(sheet, x, y, z, atOrigin, alongX, alongY);
        }

        /**
         * What goes with a tile the map cuts to a triangle, where a hip would cross its square and
         * the map has the slope on one side of it only (Roofs.cut): the plain sheet under the half
         * of the square the tile covers, the eave along its low edge where it has the whole of
         * one, and a wall under the cut, from the roof down to the floor it stands over, in the
         * roof's own covering. Given what a whole tile has, the sheet and the strip past a gable
         * stood out beyond the cut, and the end of the roof was open under it.
         */
        private void underCut(RoofFit.Tile tile, int x, int y, int z) {
            SpriteGeometry.Mesh sheet = roofSheet();
            boolean alongX = tile.fitAxis == RoofFit.ALONG_X;
            float low = tile.fitLow - z, high = tile.fitHigh - z;
            RoofFit.cornersOf(tile.fitAxis, low, high, gable);
            float[] lift = geometry.lift;
            int at = 0;
            for (int corner = 0; corner < 4; corner++) {
                if (corner != tile.cutCorner) {
                    set(folded, at++, x + RoofFit.cornerX(corner), y + RoofFit.cornerY(corner), WorldScene.under(z, gable[corner]) + lift[corner]);
                }
            }
            SpriteGeometry.addTriangle(sheet, folded, roofU, roofV, frame.red, frame.green, frame.blue);
            // The north-west corner is on the edge nearer the map's origin whichever way the tile slopes, and the south-east on the far one.
            if (low < high == (tile.cutCorner == 2)) {
                addEave(sheet, x, y, z, low, alongX ? high - low : 0f, alongX ? 0f : high - low, true);
            }
            // Down to the first floor under the cut, and never into a room.
            int foot = z;
            while (foot > 0) {
                IsoGridSquare here = cell.getGridSquare(x, y, foot), below = cell.getGridSquare(x, y, foot - 1);
                if (here != null && here.getFloor() != null || below != null && below.getRoomID() != -1L) {
                    break;
                }
                foot--;
            }
            set(cutWall, 0, x, y + 1f, foot * ViewCamera.LEVEL_HEIGHT + lift[3]);
            set(cutWall, 1, x + 1f, y, foot * ViewCamera.LEVEL_HEIGHT + lift[1]);
            set(cutWall, 2, x + 1f, y, (z + gable[1]) * ViewCamera.LEVEL_HEIGHT + lift[1]);
            set(cutWall, 3, x, y + 1f, (z + gable[3]) * ViewCamera.LEVEL_HEIGHT + lift[3]);
            SpriteGeometry.addQuad(sheet, cutWall, roofU, roofV, frame.red, frame.green, frame.blue);
        }

        private final float[] cutWall = new float[12];

        /**
         * The mesh the mod's own roofing goes in, and into roofU and roofV the texel all of it
         * takes. It is the colour most of the roof's own sprite is, painted flat over a painted
         * place on that sprite, so a slope the sprite shows nothing of is the colour of the
         * slopes it does show. Until the picture has read that colour, and for a roof with no
         * sprite to read, it is plain shingle grey.
         */
        private SpriteGeometry.Mesh roofSheet() {
            float[] colour = roof == null || roof.finish == null ? null : SpriteColours.of(roof.finish);
            building.signed &= roof == null || roof.finish == null || !SpriteColours.unread(roof.finish);
            SpriteGeometry.Mesh sheet;
            if (colour == null) {
                sheet = solid.get(PLAIN);
                roofU = roofV = 0f;
            } else {
                // The mean of the tile's sprite, the dark between its shingles with them: the usual
                // colour is the shingle's own face, and a sheet of that beside the tiles is paler and bluer than they are.
                sheet = solid.get(roof.finish.getID(), WallSides.key(colour[WallSides.MEAN], colour[WallSides.MEAN + 1], colour[WallSides.MEAN + 2], WallSides.FLAT));
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
                addBarge(sheet, c[9], c[10], c[11], c[6], c[7], c[8]);
            }
        }

        /**
         * The board under the edge of a roof where it runs out over a gable, from one end of a
         * length of that edge to the other: it hangs from the roof and follows its slope, as the
         * board along an eave follows the eave.
         */
        private void addBarge(SpriteGeometry.Mesh sheet, float x0, float y0, float up0, float x1, float y1, float up1) {
            SpriteGeometry.Mesh plain = solid.get(PLAIN);
            // The roofing itself goes in this mesh when the roof has no colour of its own yet.
            float roofing = sheet.open;
            plain.open = frame.open;
            float[] c = barge;
            set(c, 0, x0, y0, up0);
            set(c, 1, x1, y1, up1);
            set(c, 2, x1, y1, up1 - BARGE);
            set(c, 3, x0, y0, up0 - BARGE);
            SpriteGeometry.addPlainQuad(plain, c, 0.55f * frame.red, 0.55f * frame.green, 0.53f * frame.blue);
            sheet.open = roofing;
        }

        private final float[] barge = new float[12];

        /**
         * Whether a trim from the sheet of a roof's accents is the board the map paints under the
         * edge of a gable: the roof beside it, to the west or the north, slopes along the edge it
         * is on. Each square's length of that board is painted for the game's view, and stood on
         * its wall the lengths are a row of blocks. The picture hangs a board of its own from the
         * roof's edge, and the map's is left out. A trim along an eave is level, and stays.
         */
        private boolean underARake(int x, int y, int z) {
            return slopesAlong(x - 1, y, z, RoofFit.ALONG_Y) || slopesAlong(x, y - 1, z, RoofFit.ALONG_X);
        }

        private boolean slopesAlong(int x, int y, int z, int axis) {
            building.plain = false;
            Roofs.Laid map = Roofs.at(cell, x, y, z, roundNow, frame.scale), made = Roofs.madeAt(x, y, z, roundNow);
            return map != null && !map.tile.shaped && !map.tile.fitting && map.tile.fitAxis == axis
                    || made != null && !made.tile.shaped && (made.tile.fitAxis == axis || made.tile.peak > 0f && made.tile.peakAxis == axis);
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
            // The roofing itself goes in this mesh when the roof has no colour of its own yet.
            float roofing = sheet.open;
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
            sheet.open = roofing;
        }

        private final float[] pipeFrom = new float[3], pipeTo = new float[3];

        /** What is under an eave on the square past it, a level at a time going down: see Eaves.pipeFoot. */
        private int under(int x, int y, int level) {
            IsoGridSquare square = cell.getGridSquare(x, y, level);
            if (square == null) {
                return Eaves.AIR;
            }
            if (square.getRoomID() != -1L || Roofs.roofing(square) || madeAt(x, y, level)) {
                return Eaves.ROOM;
            }
            return square.getFloor() != null ? Eaves.FLOOR : Eaves.AIR;
        }

        private boolean hasRoof(int x, int y, int z) {
            if (madeAt(x, y, z)) {
                return true;
            }
            IsoGridSquare square = cell.getGridSquare(x, y, z);
            return square != null && Roofs.roofing(square);
        }

        /**
         * A flight is three squares, each a third of a level, rising towards -x (W) or -y (N): see
         * Stairs. Each square is built as the four steps its sprite paints, every tread and riser
         * showing what the sprite has there, and what carries them is built with them: see Flights.
         * Returns false, adding nothing, for a sprite that is not a stair.
         */
        private boolean addStair(SpriteGeometry.Mesh mesh, Texture texture, IsoObjectType type, int x, int y, int z) {
            int third = thirdOf(type);
            if (third < 0) {
                return false;
            }
            boolean west = climbsWest(type);
            // A flight the sprite paints with a side is closed: risers, a string down each side
            // from the steps to the underside, the underside itself, and a back under the top
            // step. A fire escape or a flight of logs has no side painted. Its treads are slabs
            // with air between them, and what the sprite shows through the gaps is not on them.
            // The side the sprite shows is the one towards the game's view, south of a flight
            // that climbs west and east of one that climbs north.
            float middle = third + 0.5f, band = middle / 3f - Stairs.STRING / 2f;
            float sideA = west ? 0.5f : 1f, sideB = west ? 1f : 0.5f;
            boolean closed = painted(texture, sideA, sideB, band);
            float riser = closed ? Stairs.RISE : Flights.TREAD;
            int added = 0;
            for (int step = 0; step < Stairs.STEPS; step++) {
                // The tread runs from its riser at near back to far, the way the flight climbs.
                // A sprite stops short along the low edge of its square, where the game draws the
                // next square's sprite over it, and its lowest step is cut ragged there. That step
                // shows what the sprite has for the one above it: the steps of a flight are alike.
                int shown = Math.max(step, 1);
                float near = 1f - shown * Stairs.GOING, far = near - Stairs.GOING, high = Stairs.tread(third, shown);
                float back = (shown - step) * Stairs.GOING;
                geometry.shownA = west ? back : 0f;
                geometry.shownB = west ? 0f : back;
                geometry.shownUp = (step - shown) * Stairs.RISE;
                added += west ? geometry.addFace(mesh, frame, x, y, z, far, 0f, high, Stairs.GOING, 0f, 0f, 0f, 1f, 0f)
                        : geometry.addFace(mesh, frame, x, y, z, 0f, far, high, 0f, Stairs.GOING, 0f, 1f, 0f, 0f);
                added += west ? geometry.addFace(mesh, frame, x, y, z, near, 0f, high - riser, 0f, 1f, 0f, 0f, 0f, riser)
                        : geometry.addFace(mesh, frame, x, y, z, 0f, near, high - riser, 1f, 0f, 0f, 0f, 0f, riser);
            }
            geometry.shownA = geometry.shownB = geometry.shownUp = 0f;
            if (added == 0) {
                return true;
            }
            if (!closed) {
                // What carries the treads is in the mean of what the sprite paints. The build
                // that first meets the sprite has no colour for it yet and is made again.
                float[] colour = SpriteColours.of(texture);
                if (colour == null) {
                    building.signed &= !SpriteColours.unread(texture);
                    return true;
                }
                SpriteGeometry.Mesh plain = solid.get(PLAIN, WallSides.key(colour[WallSides.MEAN], colour[WallSides.MEAN + 1], colour[WallSides.MEAN + 2], WallSides.FLAT));
                plain.open = frame.open;
                plain.sway = 0f;
                flightOn(plain, x, y, z, west, third);
                // Whether another flight is joined to a side is read from a square that may be another chunk's.
                building.plain = false;
                boolean joinedLow = flightThirdAt(west ? x : x - 1, west ? y - 1 : y, z, west) == third;
                boolean joinedHigh = flightThirdAt(west ? x : x + 1, west ? y + 1 : y, z, west) == third;
                // The posts are the mod's own. Under a fire escape the map stands a post of its own, which is left out where these are built: see underAFlightsHead.
                flights.open(third, !hangsInAir && Own.buildings(), joinedLow, joinedHigh);
                return true;
            }
            mesh.open = frame.open + 32f * frame.material;
            mesh.sway = 0f;
            float u = SpriteGeometry.textureU(frame, sideA, sideB), v = SpriteGeometry.textureV(frame, sideA, sideB, band);
            float[] c = reveal;
            for (int side = 0; side <= 1; side++) {
                for (int step = 0; step < Stairs.STEPS; step++) {
                    float near = 1f - step * Stairs.GOING, far = near - Stairs.GOING, high = Stairs.tread(third, step);
                    float under = Stairs.underside(third + step * Stairs.GOING), underFar = Stairs.underside(third + (step + 1) * Stairs.GOING);
                    stairCorner(c, 0, x, y, z, west, near, side, high);
                    stairCorner(c, 1, x, y, z, west, far, side, high);
                    stairCorner(c, 2, x, y, z, west, far, side, underFar);
                    stairCorner(c, 3, x, y, z, west, near, side, under);
                    SpriteGeometry.addQuad(mesh, c, u, v, frame.red, frame.green, frame.blue);
                }
            }
            // The underside lies on the floor as far as the second riser of the flight.
            float from = Math.max(third, Stairs.GOING), foot = 1f - (from - third);
            stairCorner(c, 0, x, y, z, west, foot, 0f, Stairs.underside(from));
            stairCorner(c, 1, x, y, z, west, 0f, 0f, Stairs.underside(third + 1f));
            stairCorner(c, 2, x, y, z, west, 0f, 1f, Stairs.underside(third + 1f));
            stairCorner(c, 3, x, y, z, west, foot, 1f, Stairs.underside(from));
            SpriteGeometry.addQuad(mesh, c, u, v, frame.red, frame.green, frame.blue);
            if (third == 2) {
                stairCorner(c, 0, x, y, z, west, 0f, 0f, 1f);
                stairCorner(c, 1, x, y, z, west, 0f, 1f, 1f);
                stairCorner(c, 2, x, y, z, west, 0f, 1f, Stairs.underside(3f));
                stairCorner(c, 3, x, y, z, west, 0f, 0f, Stairs.underside(3f));
                SpriteGeometry.addQuad(mesh, c, u, v, frame.red, frame.green, frame.blue);
            }
            return true;
        }

        private final Flights flights = new Flights(this::flightQuad);
        private final float[] flightCorners = new float[12];
        private SpriteGeometry.Mesh flightMesh;
        /** Where on its square a point of Flights' frame is: a = flightA + along * alongA + across * acrossA, and b likewise. */
        private float flightA, flightB, alongA, alongB, acrossA, acrossB;
        private int flightX, flightY, flightZ;

        /** Has what Flights builds next go into this mesh, as the given square of a flight on the square at x, y, z. */
        private void flightOn(SpriteGeometry.Mesh mesh, int x, int y, int z, boolean west, int third) {
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
        private int flightThirdAt(int x, int y, int z, boolean west) {
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

        /** A corner of a flight's own faces: along is how far down the square from its high edge, across how far over it, high in levels above its floor. */
        private void stairCorner(float[] c, int corner, int x, int y, int z, boolean west, float along, float across, float high) {
            float a = west ? along : across, b = west ? across : along;
            set(c, corner, x + a, y + b, (z + high) * ViewCamera.LEVEL_HEIGHT + geometry.liftAt(a, b));
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
            if (waterShown) {
                WaterMirror.plane(terrain, camera.eyeX, camera.eyeY, waterPlane);
            }
            view.pipeline.water = waterShown ? waterPlane : null;
            if (outside) {
                view.pipeline.rangeFrom = Math.max(outsideAway - OVERHEAD_RANGE, 0f);
                // From far overhead nothing fades into the sky: the edge of the loaded map is beyond
                // the picture's. The fade comes in from infinity as the camera comes down.
                float fogAt = landing == 0f ? Float.MAX_VALUE / 2f : fogEnd / landing;
                view.pipeline.loadedWithin = landing == 0f ? Float.MAX_VALUE / 2f : loadedWithin / landing;
                float nearest = Math.max(outsideAway - OVERHEAD_DEPTH, ViewCamera.nearPlane(NEAR_CORNER_DISTANCE, outsideTan, aspect));
                view.pipeline.draw(shown, outsideCamera, sky, outsideTan, nearest, outsideAway + FAR, fogAt * FOG_START, fogAt, exposure, viewport);
                lastDrawMillis = view.pipeline.lastMillis();
                return;
            }
            view.pipeline.rangeFrom = 0f;
            view.pipeline.loadedWithin = loadedWithin;
            view.pipeline.draw(shown, camera, sky, tanHalfFieldOfView, near, FAR, fogEnd * FOG_START, fogEnd, exposure, viewport);
            lastDrawMillis = view.pipeline.lastMillis();
        }
    }
}

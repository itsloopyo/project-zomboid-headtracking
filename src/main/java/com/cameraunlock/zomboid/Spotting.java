package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongFunction;
import zombie.GameTime;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMetaGrid;
import zombie.iso.IsoWorld;
import zombie.iso.RoomDef;
import zombie.meta.Meta;
import zombie.network.GameClient;

/**
 * Which rooms the game spots and which squares it marks seen this hour while the first person
 * picture covers its view. The game does both as it works out a square's light
 * (LightingJNI.JNILighting.updateFBORenderChunk): for a square the player has seen it spots the
 * room (IsoGridSquare.checkRoomSeen, which takes a room on the player's level within 10 tiles, or
 * 50 in the player's own building, and spots every room of that building on the floors the game
 * fills, whose zombies the game puts in at its next update: IsoCell.ProcessSpottedRooms), and a
 * square that has just turned seen it marks seen this hour (Meta.dealWithSquareSeen, which puts
 * the hour on the square and on its zone). In its own view it works out the light of the levels
 * it is about to draw, so of what is on its screen. Under the picture it draws none
 * (Agent.coveredPatches), and the light was worked out only where the mod read it: which rooms
 * filled with zombies, and when, went by how the mod read light.
 *
 * So under the picture the light spots no room (Hooks.roomSeenByLight) and hands each square it
 * finds turned seen to turned (Hooks.squareSeenByLight), and here, at a rhythm of its own, the
 * game's two calls are made for the squares the first person view sees: every square of each
 * room the eye's picture saw into in the last half second, and every square out of doors on the
 * chunks the picture draws where it sees the open air. A room is spotted from those of its
 * squares the game has had in the player's sight (IsoGridSquare.isSeen), and a square is marked
 * where it has turned seen since the last pass, whoever's read of its light found the turn. What
 * comes of each call is the game's to say: the level, the reach, whether the room is explored,
 * whether this machine is a client and whether the square was marked this hour.
 *
 * Asking isSeen is a read of the square's light: the game works it out afresh where its
 * lighting engine has changed it since it was last read, and otherwise asks the engine whether
 * it has, over JNI, on every asking (updateFBORenderChunk 0 to 82). A pass asks every square the
 * view sees that is not marked this hour, so that each turn there is found at the pass's rhythm
 * and not only where the mod reads light, and asks nothing where no call could come of it.
 *
 * Logic thread, where the game's light is read, but for turned.
 */
final class Spotting {
    /** How often the squares the view sees are gone over: four times a second, whatever the frame rate and however the light is read. */
    static final long EVERY_NANOS = 250_000_000L;
    /** Dev only, with this property set: what the light left alone and what the passes did are logged every ten seconds, and SpotCheck says where anything else spots or marks under the picture. */
    static final String CHECKED_BY = "cameraunlock.zomboid.checkSpotting";
    static final boolean CHECK = DevBuild.ON && Boolean.getBoolean(CHECKED_BY);
    private static final long SAID_EVERY_NANOS = 10_000_000_000L;

    /** Dev only, from any thread that reads a square's light: the rooms the light passed over under the picture. */
    static final LongAdder roomsLeftByLight = new LongAdder();
    /** Dev only: the thread a pass is making the game's calls on, or null, which SpotCheck asks. */
    static volatile Thread passer;

    private final int playerIndex;
    private boolean passed;
    private long passedNanos;
    private final RoomSight.Seen rooms = new RoomSight.Seen(), chunkKeys = new RoomSight.Seen(), drawnKeys = new RoomSight.Seen();
    private final List<IsoChunk> chunks = new ArrayList<>();
    /** The squares the light has found turned seen since the last pass, by place (key), and the set a pass empties: swapped under this object's lock. */
    private RoomSight.Seen turned = new RoomSight.Seen(), turnedBefore = new RoomSight.Seen();

    /** Dev only: what the passes came to since they were last said. */
    private long frames, passes, passNanos, longestNanos, squaresRead, seenAsked, sampled, seenOutOfSight, spotted, saidNanos;
    /** Dev only, under this object's lock: the turns found as a pass asked and by other reads of the light, and those marked where the view sees and left elsewhere. */
    private long turnsAsked, turnsAside, turnsMarked, turnsLeft;

    Spotting(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    /** Whether a pass is due at a time on System.nanoTime's clock, which is then the time of the last. */
    boolean due(long now) {
        if (passed && now - passedNanos < EVERY_NANOS) {
            return false;
        }
        passed = true;
        passedNanos = now;
        return true;
    }

    /** The light has found a square of this player's turned seen, under the picture, on whichever thread read it: the next pass marks it if the view sees it. */
    void turned(IsoGridSquare square) {
        synchronized (this) {
            turned.add(key(square.getX(), square.getY(), square.getZ()));
            if (CHECK) {
                turnsAsked += passer == Thread.currentThread() ? 1 : 0;
                turnsAside += passer == Thread.currentThread() ? 0 : 1;
            }
        }
    }

    /** A square's place as one number, never RoomSight.OUTSIDE: the map's x and y are never below 0, and its levels run from -32 to 31. */
    private static long key(int x, int y, int z) {
        return (long) x << 32 | (long) (y & 0xFFFFFF) << 8 | z + 128 & 0xFF;
    }

    /**
     * Called once a frame of the eye's picture is built while the picture covers the game's
     * view: a pass over what it sees, when one is due and the light leaves spotting to it.
     */
    void look(WorldScene scene, WorldScene.Frame frame, long now) {
        frames++;
        if (!due(now) || !leftHere()) {
            sayIfDue(now);
            return;
        }
        long began = System.nanoTime();
        IsoCell cell = IsoWorld.instance.currentCell;
        rooms.clear();
        scene.roomsShownTo(rooms);
        chunkKeys.clear();
        chunks.clear();
        RoomSight.Seen air = frame.roomsSeen;
        List<WorldScene.ChunkMeshes> shown = frame.shownBuilds();
        for (int i = 0; i < shown.size(); i++) {
            WorldScene.ChunkMeshes build = shown.get(i);
            // A build with no runs has no box, and draws nothing of its chunk.
            if (build.runs == 0) {
                continue;
            }
            // Where the picture lists the runs of the open air (ChunkBuffers.airShows): everywhere while it sees into every room.
            float slack = AIR_SLACK + build.turned;
            if (air != null && !air.airSees(build.minX - slack, build.minY - slack, build.minUp - slack, build.maxX + slack, build.maxY + slack, build.maxUp + slack)) {
                continue;
            }
            if (chunkKeys.add(chunkKey(build.wx(), build.wy()))) {
                IsoChunk chunk = cell.getChunkForGridSquare(build.wx() * WorldScene.CHUNK_TILES, build.wy() * WorldScene.CHUNK_TILES, 0);
                if (chunk == null) {
                    throw new IllegalStateException("the picture drew a build of the chunk at " + build.wx() + ", " + build.wy() + ", which is not on the map");
                }
                chunks.add(chunk);
            }
        }
        IsoMetaGrid grid = IsoWorld.instance.getMetaGrid();
        passer = Thread.currentThread();
        try {
            pass(cell, (int) GameTime.getInstance().getWorldAgeHours(), rooms, grid::getRoomDefByID, chunks);
        } finally {
            passer = null;
        }
        long took = System.nanoTime() - began;
        passes++;
        passNanos += took;
        longestNanos = Math.max(longestNanos, took);
        sayIfDue(now);
    }

    private static long chunkKey(int wx, int wy) {
        return (long) wx << 32 | wy & 0xFFFFFFFFL;
    }

    /** How far past a chunk build's box what is built in it may be drawn, in tiles: ChunkBuffers.AIR_SLACK. */
    private static final float AIR_SLACK = 1f;

    /**
     * Whether the game's light leaves spotting to the passes on this build of the game. Where
     * it does not, the light spots and marks under the picture as it did, and a pass would
     * do both a second time. Asked once, and said. Logic thread.
     */
    private static boolean leftHere() {
        if (!gapAsked) {
            gapAsked = true;
            gap = Agent.spottingGap();
            Log.info(gap == null ? "under the first person picture rooms are spotted and squares marked seen by what the view sees, four times a second"
                    : "under the first person picture rooms are spotted and squares marked seen by where the mod reads light (" + gap + ")");
        }
        return gap == null;
    }

    private static boolean gapAsked;
    private static String gap;

    /**
     * One pass at the game's hour given, over the rooms given and the squares out of doors on the
     * chunks given: the game's spotting of each room from its squares the game has had in the
     * player's sight, and its marking of each square there that the light has found turned seen,
     * by the pass's asking or since the last pass. Each asking is left out where the game would
     * make nothing of it: of a room already explored, and on a client or of a square marked in
     * this hour.
     */
    void pass(IsoCell cell, int hour, RoomSight.Seen inSight, LongFunction<RoomDef> defs, List<IsoChunk> drawn) {
        boolean marks = !GameClient.client;
        for (int slot = 0; slot < inSight.slots(); slot++) {
            long room = inSight.at(slot);
            if (room == RoomSight.OUTSIDE) {
                continue;
            }
            RoomDef def = defs.apply(room);
            // The game spots no room it has no def of (IsoGridSquare.checkRoomSeen), and the def is what says where its squares are.
            if (def == null) {
                continue;
            }
            for (int r = 0; r < def.rects.size(); r++) {
                RoomDef.RoomRect rect = def.rects.get(r);
                for (int y = rect.y; y < rect.y + rect.h; y++) {
                    for (int x = rect.x; x < rect.x + rect.w; x++) {
                        IsoGridSquare square = cell.getGridSquare(x, y, def.level);
                        if (square == null) {
                            continue;
                        }
                        squaresRead++;
                        boolean spot = !def.explored;
                        if (!spot && !(marks && square.hourLastSeen != hour)) {
                            continue;
                        }
                        if (asked(square) && spot) {
                            square.checkRoomSeen(playerIndex);
                            spotted += def.explored ? 1 : 0;
                        }
                    }
                }
            }
        }
        drawnKeys.clear();
        for (int i = 0; i < drawn.size(); i++) {
            drawnKeys.add(chunkKey(drawn.get(i).wx, drawn.get(i).wy));
        }
        if (!marks) {
            return;
        }
        for (int i = 0; i < drawn.size(); i++) {
            IsoChunk chunk = drawn.get(i);
            for (int z = chunk.getMinLevel(); z <= chunk.getMaxLevel(); z++) {
                for (int y = 0; y < WorldScene.CHUNK_TILES; y++) {
                    for (int x = 0; x < WorldScene.CHUNK_TILES; x++) {
                        IsoGridSquare square = chunk.getGridSquare(x, y, z);
                        if (square == null || square.getRoomID() != RoomSight.OUTSIDE) {
                            continue;
                        }
                        squaresRead++;
                        if (square.hourLastSeen != hour) {
                            asked(square);
                        }
                    }
                }
            }
        }
        RoomSight.Seen marking;
        synchronized (this) {
            marking = turned;
            turned = turnedBefore;
            turnedBefore = marking;
        }
        long marked = 0L, left = 0L;
        for (int slot = 0; slot < marking.slots(); slot++) {
            long place = marking.at(slot);
            if (place == RoomSight.OUTSIDE) {
                continue;
            }
            int x = (int) (place >>> 32), y = (int) (place >>> 8 & 0xFFFFFF), z = (int) (place & 0xFF) - 128;
            IsoGridSquare square = cell.getGridSquare(x, y, z);
            long room = square == null ? RoomSight.OUTSIDE : square.getRoomID();
            boolean sees = square != null && (room == RoomSight.OUTSIDE
                    ? drawnKeys.contains(chunkKey(Math.floorDiv(x, WorldScene.CHUNK_TILES), Math.floorDiv(y, WorldScene.CHUNK_TILES)))
                    : inSight.contains(room));
            if (sees) {
                Meta.instance.dealWithSquareSeen(square);
            }
            marked += sees ? 1 : 0;
            left += sees ? 0 : 1;
        }
        marking.clear();
        if (CHECK) {
            synchronized (this) {
                turnsMarked += marked;
                turnsLeft += left;
            }
        }
    }

    /**
     * Called as the game leaves a save for its menu: everything is let go and nothing marked, as
     * the world is being let go. A room id and a square's key name a place on the map, so a turn
     * kept into the next save would be marked there, where its character never saw it.
     */
    void leftSave() {
        synchronized (this) {
            turned.clear();
            turnedBefore.clear();
        }
        passed = false;
        rooms.clear();
        chunkKeys.clear();
        drawnKeys.clear();
        chunks.clear();
    }

    /** Whether the game has had a square in the player's sight, asked of its light, which may find it turned seen (turned). */
    private boolean asked(IsoGridSquare square) {
        seenAsked++;
        boolean seen = square.isSeen(playerIndex);
        // Dev only: whether the game's seen stays on out of the player's sight, sampled from one square in sixteen.
        if (CHECK && seen && (seenAsked & 15) == 0) {
            sampled++;
            seenOutOfSight += square.isCouldSee(playerIndex) ? 0 : 1;
        }
        return seen;
    }

    /** Dev only: says what the passes and the light came to, once SAID_EVERY_NANOS has gone by since it last did. */
    private void sayIfDue(long now) {
        if (!CHECK) {
            return;
        }
        if (saidNanos == 0L) {
            saidNanos = now;
            return;
        }
        if (now - saidNanos < SAID_EVERY_NANOS) {
            return;
        }
        long asked, aside, marked, left;
        synchronized (this) {
            asked = turnsAsked;
            aside = turnsAside;
            marked = turnsMarked;
            left = turnsLeft;
            turnsAsked = turnsAside = turnsMarked = turnsLeft = 0;
        }
        Log.info(String.format(Locale.ROOT,
                "spotting: player %d, %.1f s, %d frames, %d passes in %.3f ms each (longest %.3f, %.4f ms a frame); squares read %d, asked whether seen %d"
                        + " (seen and out of sight in %d of %d sampled), rooms spotted %d, rooms the light passed over %d;"
                        + " squares found turned seen as a pass asked %d and by other reads %d, marked where the view sees them %d, left elsewhere %d",
                playerIndex + 1, (now - saidNanos) / 1e9, frames, passes, passNanos / 1e6 / Math.max(1, passes), longestNanos / 1e6,
                passNanos / 1e6 / Math.max(1, frames), squaresRead, seenAsked, seenOutOfSight, sampled, spotted,
                roomsLeftByLight.sumThenReset(), asked, aside, marked, left));
        saidNanos = now;
        frames = passes = passNanos = longestNanos = squaresRead = seenAsked = sampled = seenOutOfSight = spotted = 0;
    }
}

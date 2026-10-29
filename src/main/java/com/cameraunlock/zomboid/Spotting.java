package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;
import zombie.characters.IsoPlayer;
import zombie.iso.BuildingDef;
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
 * finds turned seen to turned (Hooks.squareSeenByLight), and here, in rounds of a rhythm of their
 * own, the game's two calls are made for the squares the first person view sees: every square of
 * each room the eye's picture saw into in the last four seconds, and every square out of doors on
 * the chunks the picture draws that its cone takes in (WorldScene.Frame.faces), where it sees
 * the open air. A room is spotted from those of its
 * squares the game has had in the player's sight (IsoGridSquare.isSeen), and a square the light
 * has found turned seen, whoever's read of it found the turn, is marked once the view sees it.
 * The game marks a square only as it turns, so a turn the view has not seen yet waits for it,
 * until its chunk is let go, or until the picture stops covering the view (stoppedCovering).
 * What comes of each call is the game's to say: the level, the reach, whether the room is
 * explored, whether this machine is a client and whether the square was marked this hour. A
 * round leaves out only the spotting the game would make nothing of, of a room off the player's
 * floor or out of their reach (canSpot).
 *
 * Asking isSeen is a read of the square's light: the game works it out afresh where its
 * lighting engine has changed it since it was last read, and otherwise asks the engine whether
 * it has, over JNI, on every asking (updateFBORenderChunk 0 to 82). A square seen stays seen (the
 * game saves it so: IsoGridSquare.save and load), so a square is asked only until it is found
 * seen, which is the only way a turn there can still come. Where the game could spot its room from
 * it, it is asked in every round; where an asking could only find that it has become seen, every
 * fourth (TURN_ROUNDS). A round's askings are spread over as many frames as they take at half a
 * millisecond a frame (SLICE_NANOS).
 *
 * Logic thread, where the game's light is read, but for turned.
 */
final class Spotting {
    /** How often a round over the squares the view sees begins: up to four times a second, whatever the frame rate and however the light is read. One the frames were too slow to finish in its quarter second holds back the next until it is done. */
    static final long EVERY_NANOS = 250_000_000L;
    /** Dev only, with this property set: what the light left alone and what the rounds did are logged every ten seconds, and SpotCheck says where anything else spots or marks under the picture. */
    static final String CHECKED_BY = "cameraunlock.zomboid.checkSpotting";
    static final boolean CHECK = DevBuild.ON && Boolean.getBoolean(CHECKED_BY);
    private static final long SAID_EVERY_NANOS = 10_000_000_000L;
    /**
     * How long a frame's share of a round may take, from the start of the frame's slice, the
     * beginning of a round included: the time is looked at after each unit, so a share runs over
     * by at most a unit, and a frame with none left does one unit, so a round always moves on.
     */
    static final long SLICE_NANOS = 500_000L;
    /** The levels a chunk can have, from LOWEST up (LightingJNI takes a level as z + 32). */
    private static final int LEVELS = 64, LOWEST = -32;
    /** A unit of a round that is a piece of a room, where the rest is a level of a chunk. */
    private static final long ROOM_UNIT = 1L << 62;
    /** The ints a piece of a room takes in pieces: the room's index in roomDefs, then the piece's least x and y and its greatest x and y, left out. */
    private static final int PIECE = 5;

    /** Dev only, from any thread that reads a square's light: the rooms the light passed over under the picture. */
    static final LongAdder roomsLeftByLight = new LongAdder();
    /**
     * Dev only, counted by SpotCheck on the thread a round makes the game's calls on: the rooms the
     * game spotted, every room of a building on the floors it fills (IsoRoom.onSee), and the chunk
     * levels it marked to be built again, once for each light an asking found changed.
     */
    static final LongAdder roomsSpotted = new LongAdder(), levelsMarked = new LongAdder();
    /** Dev only: the thread a round is making the game's calls on, or null, which SpotCheck asks. */
    static volatile Thread passer;

    private final int playerIndex;
    private boolean passed;
    private long passedNanos;
    private final RoomSight.Seen shownLately = new RoomSight.Seen(), shownRooms = new RoomSight.Seen();
    private final List<IsoChunk> shownChunks = new ArrayList<>();

    /** The round under way: whether there is one, what it goes over, the units it is made of and how far it has got. */
    private boolean open, marks;
    private final RoomSight.Seen inSight = new RoomSight.Seen(), drawnKeys = new RoomSight.Seen();
    private final List<RoomDef> roomDefs = new ArrayList<>();
    private final List<IsoChunk> drawnChunks = new ArrayList<>();
    private long[] drawnAt = new long[64], units = new long[256];
    /** The rooms' rectangles cut at the chunks' edges, PIECE ints each, so that a unit is never more than a chunk's 64 squares. */
    private int[] pieces = new int[PIECE * 64];
    private int unitCount, nextUnit, pieceCount, rounds;
    private long roundNanos;
    private final long sliceLength;
    private ToIntFunction<IsoChunk> lightFrom, lightTop;

    /**
     * What is known of each chunk (Known), by the chunk's place (knownAt, to an index into
     * known). Let go with the chunk: a chunk loaded again at its place is asked about again, and
     * the turns that waited on it are let go.
     */
    private final List<Known> known = new ArrayList<>();
    private RoomSight.Seen knownAt = new RoomSight.Seen(), knownSpare = new RoomSight.Seen();
    private int[] knownFree = new int[16];
    private int knownFreeCount;

    /**
     * Of a chunk: the squares found seen, and those the light found turned seen that wait for the
     * view to see them, a bit a square on each level, and the last round that asked its squares
     * whether they have become seen (asksForTurns).
     */
    private static final class Known {
        IsoChunk chunk;
        final long[] seen = new long[LEVELS], waiting = new long[LEVELS];
        int askedForTurns;
    }

    /** The squares the light has found turned seen since the last round ended, by place (key), and the set a round empties: swapped under this object's lock. */
    private RoomSight.Seen turned = new RoomSight.Seen(), turnedBefore = new RoomSight.Seen();
    /**
     * The rooms the picture saw into in the last four seconds (RoomSight.Kept.KEEP_NANOS): the
     * game may find a square seen a while after the picture has looked away from its room, and a
     * room glimpsed in passing is still spotted when it does, as the game's level and reach say.
     * Kept from every frame, not only those that begin a round: the picture keeps a room it saw
     * into for half a second (WorldScene.roomsShownTo), and a round can take longer.
     */
    private final RoomSight.Kept roomsKept = new RoomSight.Kept();

    /** Dev only: what the rounds came to since they were last said. */
    private long frames, passes, late, passNanos, longestPassNanos, sliceNanos, longestSliceNanos, longestBeginNanos, longestEndNanos, squaresRead, seenAsked,
            sampled, seenOutOfSight, spotted, marked, letGo, readAfresh, readUnchanged, saidNanos;
    /** Dev only, under this object's lock: the turns found as a round asked and by other reads of the light. */
    private long turnsAsked, turnsAside;
    /**
     * Dev only: the game's own counts of the squares whose light it read afresh and of those its
     * engine said were unchanged (LightingJNI.JNILighting.dirty and notDirty), read round each
     * frame's share of a round. Any other thread's reads meanwhile are counted in with it.
     */
    private static Field lightsAfresh, lightsUnchanged;

    Spotting(int playerIndex) {
        this(playerIndex, SLICE_NANOS);
    }

    /** With the time a frame's share of a round may take. */
    Spotting(int playerIndex, long sliceLength) {
        this.playerIndex = playerIndex;
        this.sliceLength = sliceLength;
    }

    /** Whether a round is due at a time on System.nanoTime's clock, which is then the time of the last. */
    boolean due(long now) {
        if (passed && now - passedNanos < EVERY_NANOS) {
            return false;
        }
        passed = true;
        passedNanos = now;
        return true;
    }

    /**
     * Where the player stands, as the game reads them where it spots a room
     * (IsoGridSquare.checkRoomSeen): their place, their floor, and the building they are in, or
     * null. The game spots a room of that building from 50 tiles off and any other from 10
     * (OWN_REACH, REACH), with REACH_SPARE over here.
     */
    private float playerX, playerY;
    private int playerFloor = Integer.MIN_VALUE;
    private BuildingDef playerBuilding;
    private static final float REACH = 10f, OWN_REACH = 50f;
    /** How far past the game's reach a room is still handed to the game, so that its own sums decide at the edge. */
    private static final float REACH_SPARE = 1f;

    /** Where the player stands for the rounds' work from now on, in the building of a def or in none. */
    void stand(float x, float y, float z, BuildingDef building) {
        playerX = x;
        playerY = y;
        playerFloor = (int) Math.floor(z);
        playerBuilding = building;
    }

    /**
     * Whether the game could spot a room from a square of a rectangle of it, x0 to x1 and y0 to
     * y1 with the ends left out, where the player stands now: the room unexplored, on the
     * player's floor, and the square of the rectangle nearest the player within reach. The game
     * asks whether the room's building is the player's (player.getBuilding() == room.building),
     * here asked of their defs.
     */
    private boolean canSpot(RoomDef def, int x0, int y0, int x1, int y1) {
        if (def.explored || def.level != playerFloor) {
            return false;
        }
        float reach = (playerBuilding != null && def.getBuilding() == playerBuilding ? OWN_REACH : REACH) + REACH_SPARE;
        float dx = Math.clamp(playerX, x0 + 0.5f, x1 - 0.5f) - playerX, dy = Math.clamp(playerY, y0 + 0.5f, y1 - 0.5f) - playerY;
        return dx * dx + dy * dy < reach * reach;
    }

    /**
     * Where a turn the light finds goes (turned), under this object's lock: taken here from the
     * moment the picture comes to cover the view (covering), the game's to mark once
     * stoppedCovering has handed the turns back, and let go once leftSave has.
     */
    private Turns turnsGo = Turns.GIVEN_BACK;

    private enum Turns { TAKEN, GIVEN_BACK, LET_GO }

    /** Called as the picture comes to cover the game's view, before anything can find it covering: the turns the light finds are taken here from then on. */
    void covering() {
        synchronized (this) {
            turnsGo = Turns.TAKEN;
        }
    }

    /**
     * The light has found a square of this player's turned seen, under the picture, on whichever
     * thread read it, and is told whether it is the game's to mark. While the picture covers the
     * view it is taken, for a round to mark once the view sees it. A read that found the picture
     * covering can still come here after the turns were handed back: after stoppedCovering it is
     * the game's to mark, as it would have been had the read come a moment later, and after
     * leftSave it is let go with the world.
     */
    boolean turned(IsoGridSquare square) {
        synchronized (this) {
            if (turnsGo == Turns.TAKEN) {
                turned.add(key(square.getX(), square.getY(), square.getZ()));
                if (CHECK) {
                    turnsAsked += passer == Thread.currentThread() ? 1 : 0;
                    turnsAside += passer == Thread.currentThread() ? 0 : 1;
                }
            }
            return turnsGo == Turns.GIVEN_BACK;
        }
    }

    /** A square's place as one number, never RoomSight.OUTSIDE: the map's x and y are never below 0, and its levels run from -32 to 31. */
    private static long key(int x, int y, int z) {
        return (long) x << 32 | (long) (y & 0xFFFFFF) << 8 | z + 128 & 0xFF;
    }

    private static long chunkKey(int wx, int wy) {
        return (long) wx << 32 | wy & 0xFFFFFFFFL;
    }

    /**
     * Called once a frame of the eye's picture is built while the picture covers the game's
     * view: this frame's share of the round under way, and a new round when one is due, where
     * the light leaves spotting to the rounds.
     */
    void look(WorldScene scene, WorldScene.Frame frame, IsoPlayer player, long now) {
        if (!leftHere()) {
            frames++;
            sayIfDue(now);
            return;
        }
        shownLately.clear();
        scene.roomsShownTo(shownLately);
        look(shownLately, frame, IsoWorld.instance.currentCell, Spotting::roomDefOf, player.getX(), player.getY(), player.getZ(),
                player.getBuilding() == null ? null : player.getBuilding().getDef(), now);
    }

    private static RoomDef roomDefOf(long room) {
        return IsoWorld.instance.getMetaGrid().getRoomDefByID(room);
    }

    /**
     * The same, with the rooms the picture saw into in the last half second
     * (WorldScene.roomsShownTo), on a map with the rooms' defs given, for a player standing at a
     * place, in the building of a def or in none.
     */
    void look(RoomSight.Seen lately, WorldScene.Frame frame, IsoCell cell, LongFunction<RoomDef> defs, float x, float y, float z, BuildingDef building, long now) {
        stand(x, y, z, building);
        frames++;
        long began = System.nanoTime(), deadline = began + sliceLength;
        roomsKept.found(lately, now, null);
        int afresh = CHECK ? count(true) : 0, unchanged = CHECK ? count(false) : 0;
        passer = Thread.currentThread();
        boolean finished = false;
        try {
            // A round the frames were too slow to finish in its quarter second goes on over the next, and the next round begins once it is done.
            boolean begun = !open && due(now);
            if (begun) {
                gather(frame, cell, now);
                begin(cell, shownRooms, defs, shownChunks, frame::lightFrom, frame::lightTop);
                if (CHECK) {
                    longestBeginNanos = Math.max(longestBeginNanos, System.nanoTime() - began);
                }
            }
            // The frame that begins a round counts the beginning against its time; any other does at least a unit.
            if (open && (!begun || System.nanoTime() < deadline) && work(cell, deadline)) {
                long ending = CHECK ? System.nanoTime() : 0L;
                finish(cell);
                finished = true;
                if (CHECK) {
                    late += now - passedNanos >= EVERY_NANOS ? 1 : 0;
                    longestEndNanos = Math.max(longestEndNanos, System.nanoTime() - ending);
                }
            }
        } finally {
            passer = null;
        }
        if (CHECK) {
            readAfresh += count(true) - afresh;
            readUnchanged += count(false) - unchanged;
        }
        long ended = System.nanoTime();
        if (open || finished) {
            roundNanos += ended - began;
        }
        if (finished) {
            passes++;
            passNanos += roundNanos;
            longestPassNanos = Math.max(longestPassNanos, roundNanos);
        }
        sliceNanos += ended - began;
        longestSliceNanos = Math.max(longestSliceNanos, ended - began);
        sayIfDue(now);
    }

    /** What the picture sees now: the rooms it saw into in the last four seconds, and the chunks it draws that the view faces, where it sees their open air. */
    private void gather(WorldScene.Frame frame, IsoCell cell, long now) {
        roomsKept.expire(now, null);
        shownRooms.clear();
        roomsKept.copyTo(shownRooms);
        shownChunks.clear();
        RoomSight.Seen air = frame.roomsSeen;
        List<WorldScene.ChunkMeshes> shown = frame.shownBuilds();
        for (int i = 0; i < shown.size(); i++) {
            WorldScene.ChunkMeshes build = shown.get(i);
            // A build with no runs has no box, and draws nothing of its chunk. One near enough to cast its shadow
            // into the picture is drawn wherever it is, and seen only where the view faces it.
            if (build.runs == 0 || !frame.faces(build.wx(), build.wy())) {
                continue;
            }
            // Where the picture lists the runs of the open air (ChunkBuffers.airShows): everywhere while it sees into every room.
            float slack = AIR_SLACK + build.turned;
            if (air != null && !air.airSees(build.minX - slack, build.minY - slack, build.minUp - slack, build.maxX + slack, build.maxY + slack, build.maxUp + slack)) {
                continue;
            }
            IsoChunk chunk = cell.getChunkForGridSquare(build.wx() * WorldScene.CHUNK_TILES, build.wy() * WorldScene.CHUNK_TILES, 0);
            if (chunk == null) {
                throw new IllegalStateException("the picture drew a build of the chunk at " + build.wx() + ", " + build.wy() + ", which is not on the map");
            }
            shownChunks.add(chunk);
        }
    }

    /** How far past a chunk build's box what is built in it may be drawn, in tiles: ChunkBuffers.AIR_SLACK. */
    private static final float AIR_SLACK = 1f;

    /**
     * What the light does where the game spots a square's room as it works out the square's
     * light for a player (Hooks.roomSeenByLight): nothing while the first person picture covers
     * that player's view, which is when their spotting is given, and the game's spotting otherwise.
     */
    static void roomSeenByLight(IsoGridSquare square, int playerIndex, Spotting covered) {
        if (covered == null) {
            square.checkRoomSeen(playerIndex);
        } else if (CHECK) {
            roomsLeftByLight.increment();
        }
    }

    /**
     * The same where the game marks a square seen this hour as it finds it has just turned seen
     * (Hooks.squareSeenByLight): handed to the spotting given, to be marked once the view sees it,
     * unless it has handed its turns back to the game.
     */
    static void squareSeenByLight(Meta meta, IsoGridSquare square, Spotting covered) {
        if (covered == null || covered.turned(square)) {
            meta.dealWithSquareSeen(square);
        }
    }

    /** Dev only: the game's count of squares whose light it read afresh, or of those it found unchanged. */
    private static int count(boolean afresh) {
        try {
            if (lightsAfresh == null) {
                Class<?> light = Class.forName("zombie.iso.LightingJNI$JNILighting");
                lightsAfresh = light.getDeclaredField("dirty");
                lightsUnchanged = light.getDeclaredField("notDirty");
                lightsAfresh.setAccessible(true);
                lightsUnchanged.setAccessible(true);
            }
            return (afresh ? lightsAfresh : lightsUnchanged).getInt(null);
        } catch (ReflectiveOperationException missing) {
            throw new IllegalStateException("the game's counts of the light it reads are not as they were", missing);
        }
    }

    /**
     * Whether the game's light leaves spotting to the rounds on this build of the game. Where
     * it does not, the light spots and marks under the picture as it did, and a round would
     * do both a second time. Asked once, and said. Logic thread.
     */
    private static boolean leftHere() {
        if (!gapAsked) {
            gapAsked = true;
            gap = Agent.spottingGap();
            if (gap == null) {
                Log.info("under the first person picture rooms are spotted and squares marked seen by what the view sees, up to four times a second");
            } else {
                Log.error("under the first person picture rooms are spotted and squares marked seen by where the mod reads light, as the game's light"
                        + " could not be patched to leave them to what the view sees (" + gap + ")");
            }
        }
        return gap == null;
    }

    private static boolean gapAsked;
    private static String gap;

    /** A whole round over the rooms given and the squares out of doors on every level of the chunks given, at once, for a player standing at a place, in the building of a def or in none. */
    void pass(IsoCell cell, RoomSight.Seen rooms, LongFunction<RoomDef> defs, List<IsoChunk> drawn, float x, float y, float z, BuildingDef building) {
        stand(x, y, z, building);
        begin(cell, rooms, defs, drawn, IsoChunk::getMinLevel, IsoChunk::getMaxLevel);
        work(cell, Long.MAX_VALUE);
        finish(cell);
    }

    /**
     * Begins a round over the rooms given and the squares out of doors on the chunks given: a
     * unit for each piece of each room's rectangles inside a chunk, those that could spot their
     * room from where the player stands first, and for each level of each chunk. On a client,
     * which marks nothing, the units are only those that can spot a room. Only the levels of a
     * chunk the picture reads light on are gone over (lightFrom to lightTop,
     * WorldScene.Frame.lightFrom): the storeys of the viewer's building held over the eye are read
     * by nothing, and an asking that found a square's light changed would mark its chunk to be
     * built again.
     */
    void begin(IsoCell cell, RoomSight.Seen rooms, LongFunction<RoomDef> defs, List<IsoChunk> drawn, ToIntFunction<IsoChunk> lightFrom, ToIntFunction<IsoChunk> lightTop) {
        letGoOfChunksGone(cell);
        this.lightFrom = lightFrom;
        this.lightTop = lightTop;
        open = true;
        marks = !GameClient.client;
        unitCount = nextUnit = pieceCount = 0;
        roundNanos = 0L;
        rounds++;
        inSight.clear();
        roomDefs.clear();
        for (int slot = 0; slot < rooms.slots(); slot++) {
            long room = rooms.at(slot);
            if (room == RoomSight.OUTSIDE) {
                continue;
            }
            RoomDef def = defs.apply(room);
            // The game spots no room it has no def of (IsoGridSquare.checkRoomSeen), and the def is what says where its squares are.
            if (def == null) {
                continue;
            }
            inSight.add(room);
            if (!marks && def.explored) {
                continue;
            }
            for (int r = 0; r < def.rects.size(); r++) {
                cut(roomDefs.size(), def.rects.get(r));
            }
            roomDefs.add(def);
        }
        // Those that could spot first, so that a round spread over frames spots as soon as one done in a frame would.
        for (int piece = 0; piece < pieceCount; piece++) {
            if (canSpot(piece)) {
                unit(ROOM_UNIT | piece);
            }
        }
        for (int piece = 0; piece < pieceCount; piece++) {
            if (!canSpot(piece)) {
                unit(ROOM_UNIT | piece);
            }
        }
        drawnKeys.clear();
        drawnChunks.clear();
        for (int i = 0; i < drawn.size(); i++) {
            IsoChunk chunk = drawn.get(i);
            long place = chunkKey(chunk.wx, chunk.wy);
            if (!drawnKeys.add(place)) {
                continue;
            }
            if (drawnChunks.size() == drawnAt.length) {
                drawnAt = Arrays.copyOf(drawnAt, drawnAt.length * 2);
            }
            drawnAt[drawnChunks.size()] = place;
            for (int z = lightFrom.applyAsInt(chunk), top = lightTop.applyAsInt(chunk); marks && z <= top; z++) {
                unit((long) drawnChunks.size() << 8 | z - LOWEST);
            }
            drawnChunks.add(chunk);
        }
    }

    /** Cuts a rectangle of the room at an index of roomDefs into pieces at the chunks' edges. */
    private void cut(int room, RoomDef.RoomRect rect) {
        int x1 = rect.x + rect.w, y1 = rect.y + rect.h;
        for (int y = rect.y; y < y1; y = ((y >> 3) + 1) << 3) {
            for (int x = rect.x; x < x1; x = ((x >> 3) + 1) << 3) {
                if ((pieceCount + 1) * PIECE > pieces.length) {
                    pieces = Arrays.copyOf(pieces, pieces.length * 2);
                }
                int at = pieceCount++ * PIECE;
                pieces[at] = room;
                pieces[at + 1] = x;
                pieces[at + 2] = y;
                pieces[at + 3] = Math.min(x1, ((x >> 3) + 1) << 3);
                pieces[at + 4] = Math.min(y1, ((y >> 3) + 1) << 3);
            }
        }
    }

    private boolean canSpot(int piece) {
        int at = piece * PIECE;
        return canSpot(roomDefs.get(pieces[at]), pieces[at + 1], pieces[at + 2], pieces[at + 3], pieces[at + 4]);
    }

    private void unit(long unit) {
        if (unitCount == units.length) {
            units = Arrays.copyOf(units, units.length * 2);
        }
        units[unitCount++] = unit;
    }

    /**
     * Goes over the round's units until the time given on System.nanoTime's clock has come, at
     * least one of them, and says whether the round's units are all done.
     */
    boolean work(IsoCell cell, long deadline) {
        while (nextUnit < unitCount) {
            long unit = units[nextUnit++];
            if ((unit & ROOM_UNIT) != 0L) {
                roomPiece(cell, (int) unit);
            } else {
                chunkLevel(cell, (int) (unit >>> 8), (int) (unit & 0xFF) + LOWEST);
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
        return nextUnit == unitCount;
    }

    /**
     * One piece of a room in sight, inside one chunk: the room spotted from each square of it the
     * game has had in the player's sight, where the game could spot it from there now (canSpot).
     * Where it could not, the game would make nothing of the call, and the squares are asked only
     * whether they have become seen, and not at all on a client.
     */
    private void roomPiece(IsoCell cell, int piece) {
        int p = piece * PIECE, x0 = pieces[p + 1], y0 = pieces[p + 2], x1 = pieces[p + 3], y1 = pieces[p + 4];
        RoomDef def = roomDefs.get(pieces[p]);
        int z = def.level;
        boolean reachable = canSpot(def, x0, y0, x1, y1);
        if (!reachable && !marks) {
            return;
        }
        Known at = null;
        boolean turns = false;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                IsoGridSquare square = cell.getGridSquare(x, y, z);
                if (square == null) {
                    continue;
                }
                if (at == null) {
                    IsoChunk chunk = cell.getChunkForGridSquare(x, y, z);
                    if (chunk == null) {
                        throw new IllegalStateException("the square at " + x + ", " + y + ", " + z + " is on the map and its chunk is not");
                    }
                    if (z < lightFrom.applyAsInt(chunk) || z > lightTop.applyAsInt(chunk)) {
                        return;
                    }
                    at = knownOf(chunk);
                    turns = marks && asksForTurns(at);
                }
                squaresRead++;
                long bit = 1L << ((y & 7) << 3 | x & 7);
                markIfWaiting(at, z, bit, square);
                boolean spot = reachable && !def.explored;
                if ((at.seen[z - LOWEST] & bit) == 0L) {
                    if (!spot && !turns || !asked(square)) {
                        continue;
                    }
                    at.seen[z - LOWEST] |= bit;
                }
                if (spot) {
                    square.checkRoomSeen(playerIndex);
                    spotted += def.explored ? 1 : 0;
                }
            }
        }
    }

    /** One level of a chunk drawn: each square of it out of doors that has not been found seen is asked about, on the chunk's asking rounds (asksForTurns). */
    private void chunkLevel(IsoCell cell, int index, int z) {
        IsoChunk chunk = drawnChunks.get(index);
        long place = drawnAt[index];
        // A chunk let go of since the round began is let be: what holds it may be another chunk now.
        if (chunk.wx != (int) (place >> 32) || chunk.wy != (int) place
                || cell.getChunkForGridSquare(chunk.wx * WorldScene.CHUNK_TILES, chunk.wy * WorldScene.CHUNK_TILES, 0) != chunk) {
            return;
        }
        Known at = knownOf(chunk);
        boolean turns = asksForTurns(at);
        for (int y = 0; y < WorldScene.CHUNK_TILES; y++) {
            for (int x = 0; x < WorldScene.CHUNK_TILES; x++) {
                IsoGridSquare square = chunk.getGridSquare(x, y, z);
                if (square == null || square.getRoomID() != RoomSight.OUTSIDE) {
                    continue;
                }
                squaresRead++;
                long bit = 1L << (y << 3 | x);
                markIfWaiting(at, z, bit, square);
                if (turns && (at.seen[z - LOWEST] & bit) == 0L && asked(square)) {
                    at.seen[z - LOWEST] |= bit;
                }
            }
        }
    }

    /** What has been found seen of a chunk, begun again where the chunk at its place is not the one it was found of. */
    private Known knownOf(IsoChunk chunk) {
        long place = chunkKey(chunk.wx, chunk.wy);
        int slot = knownAt.slotOf(place);
        Known at;
        if (slot >= 0) {
            at = known.get((int) knownAt.valueAt(slot));
        } else {
            int index;
            if (knownFreeCount > 0) {
                index = knownFree[--knownFreeCount];
                at = known.get(index);
            } else {
                index = known.size();
                at = new Known();
                known.add(at);
            }
            knownAt.put(place, index);
        }
        if (at.chunk != chunk) {
            at.chunk = chunk;
            forget(at);
        }
        return at;
    }

    /**
     * Whether this round asks the squares of a chunk not yet found seen whether they have become
     * seen, where that is all an asking could find, in a room the game could not spot from them
     * or out of doors: the first round to go over the chunk does, and then every TURN_ROUNDS-th.
     * A square of a room the game could spot from it is asked in every round.
     */
    private boolean asksForTurns(Known at) {
        if (at.askedForTurns != rounds && rounds - at.askedForTurns < TURN_ROUNDS) {
            return false;
        }
        at.askedForTurns = rounds;
        return true;
    }

    /**
     * The owner's ruling of 29 October 2026: a square the game has not had in sight is asked
     * whether it has become seen every fourth round, not every round. Such askings were 88% of a
     * round's and found 1.3% of the turns, the rest coming from the picture's own reads of the
     * light. A turn only such an asking finds is marked up to four rounds later: about a second
     * while rounds keep their quarter second, longer where the frames are too slow for them.
     */
    private static final int TURN_ROUNDS = 4;

    /** Forgets what was found of a chunk, the squares that waited for the view to see them included. */
    private void forget(Known at) {
        at.askedForTurns = rounds - TURN_ROUNDS;
        Arrays.fill(at.seen, 0L);
        for (int z = 0; z < LEVELS; z++) {
            letGo += Long.bitCount(at.waiting[z]);
            at.waiting[z] = 0L;
        }
    }

    /** Marks a square that turned seen while the view did not see it, now a round goes over it. */
    private void markIfWaiting(Known at, int z, long bit, IsoGridSquare square) {
        if ((at.waiting[z - LOWEST] & bit) != 0L) {
            at.waiting[z - LOWEST] &= ~bit;
            Meta.instance.dealWithSquareSeen(square);
            marked++;
        }
    }

    /** Lets go of what was found seen of each chunk that is no longer the one at its place on the map. */
    private void letGoOfChunksGone(IsoCell cell) {
        knownSpare.clear();
        for (int slot = 0; slot < knownAt.slots(); slot++) {
            long place = knownAt.at(slot);
            if (place == RoomSight.OUTSIDE) {
                continue;
            }
            int index = (int) knownAt.valueAt(slot);
            Known at = known.get(index);
            if (cell.getChunkForGridSquare((int) (place >> 32) * WorldScene.CHUNK_TILES, (int) place * WorldScene.CHUNK_TILES, 0) == at.chunk) {
                knownSpare.put(place, index);
                continue;
            }
            at.chunk = null;
            forget(at);
            if (knownFreeCount == knownFree.length) {
                knownFree = Arrays.copyOf(knownFree, knownFree.length * 2);
            }
            knownFree[knownFreeCount++] = index;
        }
        RoomSight.Seen was = knownAt;
        knownAt = knownSpare;
        knownSpare = was;
    }

    /**
     * Ends the round: each square the light has found turned seen since the last ended is marked
     * if the view sees it, and otherwise waits for a round that goes over it.
     */
    private void finish(IsoCell cell) {
        open = false;
        if (marks) {
            mark(cell);
        }
    }

    /** The squares the light has found turned seen since this was last asked, to be emptied by the caller. */
    private RoomSight.Seen takeTurns() {
        synchronized (this) {
            RoomSight.Seen taken = turned;
            turned = turnedBefore;
            turnedBefore = taken;
            return taken;
        }
    }

    private void mark(IsoCell cell) {
        RoomSight.Seen marking = takeTurns();
        for (int slot = 0; slot < marking.slots(); slot++) {
            long place = marking.at(slot);
            if (place == RoomSight.OUTSIDE) {
                continue;
            }
            int x = (int) (place >>> 32), y = (int) (place >>> 8 & 0xFFFFFF), z = (int) (place & 0xFF) - 128;
            IsoGridSquare square = cell.getGridSquare(x, y, z);
            if (square == null) {
                letGo++;
                continue;
            }
            IsoChunk chunk = cell.getChunkForGridSquare(x, y, z);
            if (chunk == null) {
                throw new IllegalStateException("the square at " + x + ", " + y + ", " + z + " is on the map and its chunk is not");
            }
            if (goesOver(square, chunk, z)) {
                Meta.instance.dealWithSquareSeen(square);
                marked++;
            } else {
                knownOf(chunk).waiting[z - LOWEST] |= 1L << ((y & 7) << 3 | x & 7);
            }
        }
        marking.clear();
    }

    /**
     * Called on the first frame the picture no longer covers the game's view, however it
     * stopped. From then on the game's own light marks a square as it finds it turned seen, and a
     * square seen stays seen, so nothing would come to mark those that turned under the picture
     * and still wait for the view: each is marked now, the nearest the game's rule comes to when
     * it would have marked them, and any found after this is the game's to mark (turned). The
     * round under way is closed, as what it went over was what the picture saw.
     */
    void stoppedCovering(IsoCell cell) {
        letGoOfChunksGone(cell);
        RoomSight.Seen marking;
        synchronized (this) {
            turnsGo = Turns.GIVEN_BACK;
            marking = takeTurns();
        }
        for (int slot = 0; slot < marking.slots(); slot++) {
            long place = marking.at(slot);
            if (place == RoomSight.OUTSIDE) {
                continue;
            }
            IsoGridSquare square = cell.getGridSquare((int) (place >>> 32), (int) (place >>> 8 & 0xFFFFFF), (int) (place & 0xFF) - 128);
            if (square == null) {
                letGo++;
                continue;
            }
            Meta.instance.dealWithSquareSeen(square);
            marked++;
        }
        marking.clear();
        for (int i = 0; i < known.size(); i++) {
            Known at = known.get(i);
            for (int z = 0; at.chunk != null && z < LEVELS; z++) {
                for (long bits = at.waiting[z]; bits != 0L; bits &= bits - 1) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    IsoGridSquare square = at.chunk.getGridSquare(bit & 7, bit >>> 3, z + LOWEST);
                    if (square == null) {
                        letGo++;
                        continue;
                    }
                    Meta.instance.dealWithSquareSeen(square);
                    marked++;
                }
                at.waiting[z] = 0L;
            }
        }
        open = false;
        unitCount = nextUnit = pieceCount = 0;
        inSight.clear();
        roomDefs.clear();
        drawnKeys.clear();
        drawnChunks.clear();
        shownChunks.clear();
    }

    /**
     * Called as the game leaves a save for its menu: everything is let go and nothing marked, as
     * the world is being let go. A room id and a chunk key name a place on the map, so a turn
     * kept into the next save would be marked there, where its character never saw it, and a turn
     * found after this is let go too (turned), until the picture covers a view again.
     */
    void leftSave() {
        synchronized (this) {
            turnsGo = Turns.LET_GO;
            turned.clear();
            turnedBefore.clear();
        }
        passed = false;
        open = false;
        unitCount = nextUnit = pieceCount = 0;
        inSight.clear();
        roomDefs.clear();
        drawnKeys.clear();
        drawnChunks.clear();
        shownChunks.clear();
        shownRooms.clear();
        shownLately.clear();
        lightFrom = lightTop = null;
        known.clear();
        knownAt.clear();
        knownSpare.clear();
        knownFreeCount = 0;
        roomsKept.clear();
    }

    /** Whether the round goes over a square: in a room in sight, or out of doors on a chunk drawn, on a level the picture reads light on. */
    private boolean goesOver(IsoGridSquare square, IsoChunk chunk, int z) {
        if (z < lightFrom.applyAsInt(chunk) || z > lightTop.applyAsInt(chunk)) {
            return false;
        }
        long room = square.getRoomID();
        return room == RoomSight.OUTSIDE ? drawnKeys.contains(chunkKey(chunk.wx, chunk.wy)) : inSight.contains(room);
    }

    /** Dev only: how many squares wait for the view to see them. */
    private long waiting() {
        long waiting = 0L;
        for (int i = 0; i < known.size(); i++) {
            Known at = known.get(i);
            for (int z = 0; at.chunk != null && z < LEVELS; z++) {
                waiting += Long.bitCount(at.waiting[z]);
            }
        }
        return waiting;
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

    /** Dev only: says what the rounds and the light came to, once SAID_EVERY_NANOS has gone by since it last did. */
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
        long asked, aside;
        synchronized (this) {
            asked = turnsAsked;
            aside = turnsAside;
            turnsAsked = turnsAside = 0;
        }
        Log.info(String.format(Locale.ROOT,
                "spotting: player %d, %.1f s, %d frames, %d rounds (%d over their quarter second) in %.3f ms each (longest %.3f), %.4f ms a frame (longest %.3f,"
                        + " beginning a round up to %.3f, ending one up to %.3f);"
                        + " squares read %d, asked whether seen %d (seen and out of sight in %d of %d sampled), light read afresh %d and found unchanged %d, chunk levels marked for their light %d times,"
                        + " buildings spotted %d (rooms %d), rooms the light passed over %d, rooms in sight %d; squares found turned seen as a round asked %d and by other reads %d,"
                        + " marked where the view sees them %d, waiting for the view %d, let go with their chunk %d; chunks known %d",
                playerIndex + 1, (now - saidNanos) / 1e9, frames, passes, late, passNanos / 1e6 / Math.max(1, passes), longestPassNanos / 1e6,
                sliceNanos / 1e6 / Math.max(1, frames), longestSliceNanos / 1e6, longestBeginNanos / 1e6, longestEndNanos / 1e6, squaresRead, seenAsked, seenOutOfSight, sampled, readAfresh, readUnchanged, levelsMarked.sumThenReset(),
                spotted, roomsSpotted.sumThenReset(), roomsLeftByLight.sumThenReset(), roomsKept.size(), asked, aside, marked, waiting(), letGo, knownAt.size()));
        saidNanos = now;
        frames = passes = late = passNanos = longestPassNanos = sliceNanos = longestSliceNanos = longestBeginNanos = longestEndNanos = squaresRead = seenAsked = sampled = seenOutOfSight = spotted
                = marked = letGo = readAfresh = readUnchanged = 0;
    }
}

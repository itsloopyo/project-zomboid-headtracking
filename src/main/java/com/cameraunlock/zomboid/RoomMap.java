package com.cameraunlock.zomboid;

import java.util.Arrays;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.objects.IsoBarricade;
import zombie.iso.objects.IsoCurtain;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWindow;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * The game's map as RoomSight reads it: which room each square is in, what each edge between
 * two squares does to sight as the picture draws it, and which squares have a floor. Run on
 * the logic thread, which the game changes the map on.
 *
 * An edge stops sight only where the picture draws something that does: a wall with no
 * opening in it, a shut door with no glass in it, a drawn curtain. A doorway, a window, a
 * door with glass in it, a fence, a wall the game calls see-through and a barricade (planks
 * have gaps) let sight through. A floor or a roof over a square stops it between levels.
 *
 * The openings of each chunk are kept, and found again when the game marks anything that may
 * be part of a building on the chunk or round it, when the chunk or the chunks beside it are
 * loaded again, and while the game marks nothing, every second.
 */
final class RoomMap implements Acoustics.Walls, RoomDaylight.Edges {
    private static final int SIDE = ChunkMarks.SIDE;
    private static final long REREAD_UNTOLD_NANOS = 1_000_000_000L;
    private IsoCell cell;
    private Terrain terrain;
    /** Until when, by System.nanoTime, chunks may be read: every reader in a frame spends the one budget. */
    private long readsUntil;
    private final RoomSight.Openings[] kept = new RoomSight.Openings[SIDE * SIDE];
    private final RoomSight.Blocks[] keptBlocks = new RoomSight.Blocks[SIDE * SIDE];
    /** For each chunk kept, the rooms any of its squares is in, each once: the builds of the chunk go by whether those are in sight. */
    private final RoomSight.Seen[] keptRooms = new RoomSight.Seen[SIDE * SIDE];
    private final IsoChunk[] keptChunk = new IsoChunk[SIDE * SIDE], keptNorth = new IsoChunk[SIDE * SIDE], keptWest = new IsoChunk[SIDE * SIDE];
    private final int[] keptMarks = new int[SIDE * SIDE], keptLevels = new int[SIDE * SIDE];
    /**
     * The heights of the land each kept chunk was read at (land): its openings' heights are
     * the floors' heights there. A chunk read before the land under it was worked out kept its
     * doorways a hill's height off the ground they stand on, and they covered none of the picture.
     */
    private final int[] keptLand = new int[SIDE * SIDE];
    /** Each kept chunk's openings level by level, from its lowest, and the marks each level was read at, and those marked with no level. */
    private final RoomSight.Openings[][] keptByLevel = new RoomSight.Openings[SIDE * SIDE][];
    private final int[][] keptLevelMarks = new int[SIDE * SIDE][];
    private final int[] keptLoose = new int[SIDE * SIDE];
    private final long[] keptNanos = new long[SIDE * SIDE];
    /** How many times each chunk has been read: a frame whose chunks were all read before, and are the same chunks, has the same openings. */
    private final int[] keptReadings = new int[SIDE * SIDE];
    /** Whether the last read of each chunk said UNREAD: what is kept there is not of it as it is now. */
    private final boolean[] keptUnread = new boolean[SIDE * SIDE];
    /** Dev only: how many chunks were read for their openings since the last asking. */
    int chunksRead;

    void begin(IsoCell cell, Terrain terrain) {
        this.cell = cell;
        this.terrain = terrain;
    }

    /**
     * Lets go of the map and every chunk read from it, as the picture stops being built: kept,
     * they would hold the whole of a world the game had let go of through another save's
     * loading. The count of readings goes on, so a chunk read again is never taken for the same.
     */
    void forget() {
        cell = null;
        terrain = null;
        Arrays.fill(kept, null);
        Arrays.fill(keptBlocks, null);
        Arrays.fill(keptRooms, null);
        Arrays.fill(keptChunk, null);
        Arrays.fill(keptNorth, null);
        Arrays.fill(keptWest, null);
        Arrays.fill(keptByLevel, null);
        Arrays.fill(keptLevelMarks, null);
    }

    /** What read says of a chunk it was not to read and has no reading of as it is now. */
    static final int UNREAD = -1;

    /** Lets chunks be read until a time by System.nanoTime: see mayRead. */
    void readUntil(long until) {
        readsUntil = until;
    }

    /** Whether the frame's budget for reading chunks has time left. */
    boolean mayRead() {
        return System.nanoTime() < readsUntil;
    }

    /**
     * Reads a chunk's openings and blocks again if what they were read from may have changed,
     * and says how many times it has been read. Told not to read, it keeps what it read before
     * for a chunk that may have changed, and says UNREAD for one it has nothing of as it is now.
     *
     * @param north   the chunk at y - 1 on the loaded map, or null
     * @param west    the chunk at x - 1, or null
     * @param told    the game marks what it changes: see ChunkMarks.live
     * @param mayRead whether it may read the chunk now
     */
    int read(IsoChunk chunk, IsoChunk north, IsoChunk west, boolean told, long now, boolean mayRead) {
        int slot = ChunkMarks.slot(chunk.wx, chunk.wy);
        int marks = ChunkMarks.structuresIn(chunk.wx - 1, chunk.wy - 1, chunk.wx + 1, chunk.wy + 1);
        int levels = chunk.getMinLevel() * 1000 + chunk.getMaxLevel();
        int land = land(chunk.wx, chunk.wy);
        RoomSight.Openings openings = kept[slot];
        RoomSight.Blocks blocks = keptBlocks[slot];
        boolean fits = openings != null && keptChunk[slot] == chunk && keptNorth[slot] == north && keptWest[slot] == west && keptLevels[slot] == levels && keptLand[slot] == land;
        boolean same = fits && (told || now - keptNanos[slot] < REREAD_UNTOLD_NANOS);
        if (!mayRead && (!same || keptMarks[slot] != marks)) {
            keptUnread[slot] = !fits;
            return fits ? keptReadings[slot] : UNREAD;
        }
        keptUnread[slot] = false;
        if (same && keptMarks[slot] != marks && readLevels(chunk, slot)) {
            keptMarks[slot] = marks;
            keptNanos[slot] = now;
            keptReadings[slot]++;
            return keptReadings[slot];
        }
        if (!same || keptMarks[slot] != marks) {
            if (openings == null) {
                openings = kept[slot] = new RoomSight.Openings();
                blocks = keptBlocks[slot] = new RoomSight.Blocks();
                keptRooms[slot] = new RoomSight.Seen();
            }
            openings.clear();
            blocks.clear();
            RoomSight.Seen rooms = keptRooms[slot];
            rooms.clear();
            int fromX = chunk.wx * WorldScene.CHUNK_TILES, fromY = chunk.wy * WorldScene.CHUNK_TILES;
            for (int y = fromY; y < fromY + WorldScene.CHUNK_TILES; y++) {
                blocks.scanRow(this, fromX, fromX + WorldScene.CHUNK_TILES, y);
            }
            // The gap over the highest level is to whatever is above it, which is open air.
            int count = chunk.getMaxLevel() - chunk.getMinLevel() + 1;
            if (keptByLevel[slot] == null || keptByLevel[slot].length < count) {
                keptByLevel[slot] = new RoomSight.Openings[count];
                keptLevelMarks[slot] = new int[count];
            }
            for (int z = chunk.getMinLevel(); z <= chunk.getMaxLevel(); z++) {
                int at = z - chunk.getMinLevel();
                keptLevelMarks[slot][at] = levelMarks(chunk, z);
                scanLevel(chunk, slot, z, rooms);
                openings.addAll(keptByLevel[slot][at]);
            }
            keptLoose[slot] = ChunkMarks.structuresLoose(chunk.wx - 1, chunk.wy - 1, chunk.wx + 1, chunk.wy + 1);
            keptChunk[slot] = chunk;
            keptNorth[slot] = north;
            keptWest[slot] = west;
            keptMarks[slot] = marks;
            keptLevels[slot] = levels;
            keptLand[slot] = land;
            keptNanos[slot] = now;
            keptReadings[slot]++;
            chunksRead++;
        }
        return keptReadings[slot];
    }

    /** One number for the heights of the chunks a chunk's openings and blocks take floors' heights from: its own and, at its far edges, those east and south of it. */
    private int land(int wx, int wy) {
        return ((terrain.versionIfKept(wx, wy) * 31 + terrain.versionIfKept(wx + 1, wy)) * 31 + terrain.versionIfKept(wx, wy + 1)) * 31 + terrain.versionIfKept(wx + 1, wy + 1);
    }

    /** Adds the openings of a chunk read lately to what is heard round a listener. */
    void addOpeningsTo(IsoChunk chunk, RoomSight.Openings into, int fromZ, int toZ) {
        int slot = ChunkMarks.slot(chunk.wx, chunk.wy);
        if (keptUnread[slot]) {
            return;
        }
        into.addAll(kept[slot], fromZ, toZ);
    }

    /**
     * Reads again only the levels of a kept chunk marked since it was read, and those either
     * side of them, whose openings up or down go by them: a tower's chunk is thirty levels, and
     * a door opened on one had every level of the chunk and the eight round it read again.
     * False where it cannot: the chunk's levels have changed in number, or something marked
     * with no level.
     */
    private boolean readLevels(IsoChunk chunk, int slot) {
        int count = chunk.getMaxLevel() - chunk.getMinLevel() + 1;
        int[] marked = keptLevelMarks[slot];
        if (marked == null || keptByLevel[slot].length < count
                || ChunkMarks.structuresLoose(chunk.wx - 1, chunk.wy - 1, chunk.wx + 1, chunk.wy + 1) != keptLoose[slot]) {
            return false;
        }
        long changed = 0L;
        for (int z = chunk.getMinLevel(); z <= chunk.getMaxLevel(); z++) {
            int at = z - chunk.getMinLevel(), now = levelMarks(chunk, z);
            if (now != marked[at]) {
                marked[at] = now;
                changed |= 7L << at >> 1;
            }
        }
        RoomSight.Openings openings = kept[slot];
        openings.clear();
        RoomSight.Blocks blocks = keptBlocks[slot];
        blocks.clear();
        int fromX = chunk.wx * WorldScene.CHUNK_TILES, fromY = chunk.wy * WorldScene.CHUNK_TILES;
        for (int y = fromY; y < fromY + WorldScene.CHUNK_TILES; y++) {
            blocks.scanRow(this, fromX, fromX + WorldScene.CHUNK_TILES, y);
        }
        for (int z = chunk.getMinLevel(); z <= chunk.getMaxLevel(); z++) {
            int at = z - chunk.getMinLevel();
            if ((changed >> at & 1L) != 0L) {
                scanLevel(chunk, slot, z, keptRooms[slot]);
            }
            openings.addAll(keptByLevel[slot][at]);
        }
        return true;
    }

    /** The marks of a level of the chunk and the eight round it, without those marked with no level. */
    private static int levelMarks(IsoChunk chunk, int z) {
        return ChunkMarks.structuresIn(chunk.wx - 1, chunk.wy - 1, chunk.wx + 1, chunk.wy + 1, z, z)
                - ChunkMarks.structuresLoose(chunk.wx - 1, chunk.wy - 1, chunk.wx + 1, chunk.wy + 1);
    }

    /** Reads one level of a chunk's openings into what is kept of that level, and its rooms into a set. */
    private void scanLevel(IsoChunk chunk, int slot, int z, RoomSight.Seen rooms) {
        RoomSight.Openings level = keptByLevel[slot][z - chunk.getMinLevel()];
        if (level == null) {
            level = keptByLevel[slot][z - chunk.getMinLevel()] = new RoomSight.Openings();
        }
        level.clear();
        int fromX = chunk.wx * WorldScene.CHUNK_TILES, fromY = chunk.wy * WorldScene.CHUNK_TILES;
        for (int x = fromX; x < fromX + WorldScene.CHUNK_TILES; x++) {
            for (int y = fromY; y < fromY + WorldScene.CHUNK_TILES; y++) {
                level.scan(this, x, y, z);
                long room = region(x, y, z);
                if (room != RoomSight.OUTSIDE) {
                    rooms.add(room);
                }
            }
        }
    }

    /** Adds the openings and blocks of a chunk read lately to a frame's, and nothing for one it has no reading of as it is now loaded. */
    void addTo(IsoChunk chunk, RoomSight.Openings into, RoomSight.Blocks blocksInto) {
        int slot = ChunkMarks.slot(chunk.wx, chunk.wy);
        if (keptUnread[slot]) {
            return;
        }
        into.addAll(kept[slot]);
        blocksInto.addAll(keptBlocks[slot]);
    }

    /** Whether a chunk was read as it is now loaded and any of the rooms given is on it: a chunk never read was never built by sight. */
    boolean holdsAny(IsoChunk chunk, RoomSight.Seen of) {
        int slot = ChunkMarks.slot(chunk.wx, chunk.wy);
        return keptChunk[slot] == chunk && keptRooms[slot].overlaps(of);
    }

    @Override
    public long region(int x, int y, int z) {
        IsoGridSquare square = hallOrSquare(x, y, z);
        return square == null ? RoomSight.OUTSIDE : square.getRoomID();
    }

    /**
     * The square, or for the upper part of a tall hall and a row left out of it the hall's floored
     * square: that is all one space, and the hall's ceiling hangs at its top (TallHalls).
     */
    private IsoGridSquare hallOrSquare(int x, int y, int z) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        IsoGridSquare hall = square == null ? null : TallHalls.floorOf(cell, x, y, z);
        return hall != null ? hall : square;
    }

    @Override
    public int edge(int x, int y, int z, boolean north) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        if (square == null) {
            // Walls stand on the north and west edges of the square they belong to: with no square there is none.
            return RoomSight.SEE;
        }
        read(square, north);
        if (door || window) {
            IsoGridSquare beyond = north ? cell.getGridSquare(x, y - 1, z) : cell.getGridSquare(x - 1, y, z);
            return doorBlind || curtained(square, beyond, north) ? RoomSight.SHUT : RoomSight.SEE;
        }
        return wall ? RoomSight.SOLID : RoomSight.SEE;
    }

    @Override
    public int heard(int x, int y, int z, boolean north) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        if (square == null) {
            return Acoustics.OPEN;
        }
        read(square, north);
        if (door) {
            return !doorShut ? Acoustics.OPEN : doorGlazed ? Acoustics.GLASS : Acoustics.DOOR;
        }
        if (window) {
            return glassShut ? Acoustics.GLASS : Acoustics.OPEN;
        }
        return wall ? Acoustics.WALL : Acoustics.OPEN;
    }

    /** How much of the day a drawn curtain lets through, round its edges and through the cloth. */
    static final float CURTAIN_PASSES = 0.12f;
    /** How much of the day a shut door with glass in it lets through: the glass is part of the door. */
    static final float GLAZED_DOOR_PASSES = 0.5f;

    @Override
    public float through(int x, int y, int z, boolean north) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        if (square == null) {
            return 1f;
        }
        read(square, north);
        float passed;
        if (door) {
            passed = !doorShut ? 1f : doorGlazed ? GLAZED_DOOR_PASSES : 0f;
        } else {
            passed = window || !wall ? 1f : 0f;
        }
        if (passed > 0f && (door || window) && curtained(square, north ? cell.getGridSquare(x, y - 1, z) : cell.getGridSquare(x - 1, y, z), north)) {
            passed = CURTAIN_PASSES;
        }
        return passed * boarded(square, north);
    }

    /** How much of the day the barricades on a square's door or window on that edge let through, on either side of it. */
    private static float boarded(IsoGridSquare square, boolean north) {
        PZArrayList<IsoObject> objects = square.getObjects();
        float passed = 1f;
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            if (object instanceof IsoWindow each && each.getNorth() == north) {
                passed *= passes(each.getBarricadeOnSameSquare()) * passes(each.getBarricadeOnOppositeSquare());
            } else if (object instanceof IsoDoor each && each.getNorth() == north) {
                passed *= passes(each.getBarricadeOnSameSquare()) * passes(each.getBarricadeOnOppositeSquare());
            } else if (object instanceof IsoThumpable each && (each.isDoor() ? each.getNorth() == north : north ? each.isWindowN() : each.isWindowW())) {
                passed *= passes(each.getBarricadeOnSameSquare()) * passes(each.getBarricadeOnOppositeSquare());
            }
        }
        return passed;
    }

    /** A sheet of metal lets nothing through; bars let most of it; each plank shuts off a slat, and four leave slits. */
    private static float passes(IsoBarricade barricade) {
        if (barricade == null) {
            return 1f;
        }
        if (barricade.isMetal()) {
            return 0f;
        }
        if (barricade.isMetalBar()) {
            return 0.75f;
        }
        return 1f - 0.9f * Math.min(barricade.getNumPlanks(), IsoBarricade.MAX_PLANKS) / IsoBarricade.MAX_PLANKS;
    }

    /** What read last found on an edge. A curtain does not stop sound, and is asked after only for sight. */
    private boolean door, doorShut, doorGlazed, doorBlind, window, glassShut, wall;

    /** Reads what stands on a square's north edge, or with north false its west edge. */
    private void read(IsoGridSquare square, boolean north) {
        door = false;
        doorShut = false;
        doorGlazed = false;
        doorBlind = false;
        window = false;
        glassShut = false;
        wall = false;
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            if (object instanceof IsoDoor each && each.getNorth() == north) {
                door = true;
                boolean glazed = seeThrough(each.getSprite());
                doorShut |= !each.IsOpen();
                doorGlazed |= glazed;
                doorBlind |= !each.IsOpen() && (!glazed || each.HasCurtains() != null && !each.isCurtainOpen());
            } else if (object instanceof IsoThumpable thumpable) {
                if (thumpable.isDoor() && thumpable.getNorth() == north) {
                    door = true;
                    doorShut |= !thumpable.IsOpen();
                    doorBlind |= !thumpable.IsOpen();
                } else if (north ? thumpable.isWindowN() : thumpable.isWindowW()) {
                    window = true;
                    glassShut |= !thumpable.IsOpen();
                }
            } else if (object instanceof IsoWindow each && each.getNorth() == north) {
                window = true;
                glassShut |= !each.IsOpen() && !each.isSmashed() && !each.isGlassRemoved();
            }
            IsoSprite sprite = object.getSprite();
            if (sprite == null) {
                continue;
            }
            var flags = sprite.getProperties();
            if (north ? flags.has(IsoFlagType.WindowN) : flags.has(IsoFlagType.WindowW)) {
                window = true;
            }
            if (north ? flags.has(IsoFlagType.DoorWallN) : flags.has(IsoFlagType.DoorWallW)) {
                // A door frame: what is in the doorway decides.
                continue;
            }
            boolean onEdge = north ? flags.has(IsoFlagType.WallN) || flags.has(IsoFlagType.WallNW) : flags.has(IsoFlagType.WallW) || flags.has(IsoFlagType.WallNW);
            boolean seen = north ? flags.has(IsoFlagType.WallNTrans) || flags.has(IsoFlagType.transparentN) || flags.has(IsoFlagType.HoppableN) || flags.has(IsoFlagType.TallHoppableN)
                    : flags.has(IsoFlagType.WallWTrans) || flags.has(IsoFlagType.transparentW) || flags.has(IsoFlagType.HoppableW) || flags.has(IsoFlagType.TallHoppableW);
            wall |= onEdge && !seen;
        }
    }

    /** Whether a door's picture has glass in it, which the game says of a door it lets the light of a room through. */
    private static boolean seeThrough(IsoSprite sprite) {
        return sprite != null && sprite.getProperties().has("doorTrans");
    }

    /** Whether a curtain is drawn across the edge, hung on the square's side of it or on the side beyond. */
    private static boolean curtained(IsoGridSquare square, IsoGridSquare beyond, boolean north) {
        return drawn(square, north ? IsoObjectType.curtainN : IsoObjectType.curtainW) || beyond != null && drawn(beyond, north ? IsoObjectType.curtainS : IsoObjectType.curtainE);
    }

    private static boolean drawn(IsoGridSquare square, IsoObjectType type) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i) instanceof IsoCurtain curtain && !curtain.IsOpen() && curtain.getType() == type) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean floored(int x, int y, int z) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        if (square == null) {
            return false;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite == null) {
                continue;
            }
            var flags = sprite.getProperties();
            if (flags.has(IsoFlagType.solidfloor) && !flags.has(IsoFlagType.transparentFloor) || Roofs.isTile(sprite.getType())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public long building(int x, int y, int z) {
        IsoGridSquare square = hallOrSquare(x, y, z);
        return square == null || square.getRoomID() == RoomSight.OUTSIDE ? RoomSight.OUTSIDE : square.getBuilding().getDef().id;
    }

    @Override
    public float floorUp(float x, float y, int z) {
        return z * ViewCamera.LEVEL_HEIGHT + terrain.lift(x, y, z);
    }
}
